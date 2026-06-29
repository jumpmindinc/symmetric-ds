package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

class NamedParameterUtilsTest {

	@Test
	void parsesDistinctNamedParameters() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :bar");

		assertEquals(2, parsed.getNamedParameterCount());

		assertEquals(0, parsed.getUnnamedParameterCount());
		assertEquals(2, parsed.getTotalParameterCount());
		assertEquals(List.of("foo", "bar"), parsed.getParameterNames());
	}

	@Test
	void countsRepeatedParameterOnceButTotalCountsEach() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :foo");

		assertEquals(1, parsed.getNamedParameterCount());
		assertEquals(2, parsed.getTotalParameterCount());
		assertEquals(List.of("foo", "foo"), parsed.getParameterNames());

	}

	@Test
	void skipsPostgresStyleCastOperator() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select a::int from t where b = :foo");

		assertEquals(1, parsed.getNamedParameterCount());
		assertEquals(List.of("foo"), parsed.getParameterNames());
	}

	@Test
	void expandsCollectionIntoCommaSeparatedPlaceholders() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where id in (:ids)");

		Map<String, Object> params = Map.of("ids", List.of(1, 2, 3));

		String sql = NamedParameterUtils.substituteNamedParameters(parsed, params);
		assertEquals("select * from t where id in (?, ?, ?)", sql);
	}

	@Test
	void throwsWhenMixingNamedAndUnnamedParameters() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = ?");

		Map<String, Object> params = Map.of("foo", 1);

		assertThrows(IllegalStateException.class, () -> NamedParameterUtils.buildValueArray(parsed, params));
	}

	@Test
	void substitutesNamedParameterWithPlaceholder() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :bar");

		Map<String, Object> params = Map.of("foo", 1, "bar", 2);

		String sql = NamedParameterUtils.substituteNamedParameters(parsed, params);

		assertEquals("select * from t where a = ? and b = ?", sql);
	}

	@Test
	void expandsCollectionOfArraysIntoGroupedPlaceholders() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where (a, b) in (:tuples)");

		Map<String, Object> params = Map.of("tuples", List.of(new Object[] { 1, 2 }, new Object[] { 3, 4 }));

		String sql = NamedParameterUtils.substituteNamedParameters(parsed, params);

		assertEquals("select * from t where (a, b) in ((?, ?), (?, ?))", sql);

	}

	@Test
	void throwsWhenParameterMissingFromMap() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo");

		Map<String, Object> params = Map.of("bar", 1); // wrong key
		assertThrows(InvalidSqlException.class, () -> NamedParameterUtils.substituteNamedParameters(parsed, params));

	}

	@Test
	void ignoresParameterInsideSingleQuotes() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select ':notparam' from t where a = :foo");

		assertEquals(1, parsed.getNamedParameterCount());
		assertEquals(List.of("foo"), parsed.getParameterNames());
	}

	@Test
	void ignoresParameterInsideDoubleQuotes() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select \":notcolumn\" from t where a = :foo");

		assertEquals(1, parsed.getNamedParameterCount());
		assertEquals(List.of("foo"), parsed.getParameterNames());
	}

	@Test
	void ignoresParameterInsideLineComment() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t -- skip :notparam\n where a = :foo");

		assertEquals(1, parsed.getNamedParameterCount());
		assertEquals(List.of("foo"), parsed.getParameterNames());
	}

	@Test
	void ignoresParameterInsideBlockedComment() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * /* skip :notparam */ from t where a = :foo");

		assertEquals(1, parsed.getNamedParameterCount());
		assertEquals(List.of("foo"), parsed.getParameterNames());
	}

	@Test
	void buildsValueArrayInParameterOrder() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :bar");

		Map<String, Object> params = Map.of("foo", 1, "bar", 2);

		Object[] values = NamedParameterUtils.buildValueArray(parsed, params);
		assertArrayEquals(new Object[] { 1, 2 }, values);
	}

	@Test
	void repeatsValueForRepeatedParameter() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :foo");

		Map<String, Object> params = Map.of("foo", 1);

		Object[] values = NamedParameterUtils.buildValueArray(parsed, params);
		assertArrayEquals(new Object[] { 1, 1 }, values);

	}

	@Test
	void flattensCollectionValueIntoArray() {
		ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where id in (:ids)");

		Map<String, Object> params = Map.of("ids", List.of(1, 2, 3));

		Object[] values = NamedParameterUtils.buildValueArray(parsed, params);
		assertArrayEquals(new Object[] { 1, 2, 3 }, values);

	}

}