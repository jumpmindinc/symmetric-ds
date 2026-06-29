package org.jumpmind.db.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.sql.Types;

class TypeMapTest {

	@Test
	void getJdbcTypeCodeLooksUpByName() {
		assertEquals(Types.VARCHAR, TypeMap.getJdbcTypeCode("VARCHAR"));
	}

	@Test
	void getJdbcTypeCodeIsCaseInsensitive() {
		assertEquals(Types.VARCHAR, TypeMap.getJdbcTypeCode("varchar"));
	}

	@Test
	void getJdbcTypeCodeReturnsNullForUnknown() {
		assertNull(TypeMap.getJdbcTypeCode("NOT_A_REAL_TYPE"));
	}

	@Test
	void getJdbcTypeNameLookUpByCode() {
		assertEquals("VARCHAR", TypeMap.getJdbcTypeName(Types.VARCHAR));
	}

	@Test
	void getJdbcTypeNameFallsBackToCodeStringForUnknown() {
		assertEquals("999", TypeMap.getJdbcTypeName(999));
	}

	@Test
	void isNumericTypeTrueForInteger() {
		assertTrue(TypeMap.isNumericType(Types.INTEGER));
	}

	@Test
	void isNumericTypeFalseForVarChar() {
		assertFalse(TypeMap.isNumericType(Types.VARCHAR));
	}

	@Test
	void isTextTypeTrueForVarChar() {
		assertTrue(TypeMap.isTextType(Types.VARCHAR));
	}

	@Test
	void isTextTypeFalseForVarChar() {
		assertFalse(TypeMap.isTextType(Types.INTEGER));
	}

	@Test
	void isSpecialTypeTrueForArray() {
		assertTrue(TypeMap.isSpecialType(Types.ARRAY));
	}

	@Test
	void isSpecialTypeFalseForBlob() {
		assertFalse(TypeMap.isSpecialType(Types.BLOB));
	}

	@Test
	void isBinaryTypeTrueForBlob() {
		assertTrue(TypeMap.isBinaryType(Types.BLOB));
	}

	@Test
	void isBinaryTypeFalseForArray() {
		assertFalse(TypeMap.isBinaryType(Types.ARRAY));
	}

	@Test
	void isDateTimeTypeTrueForDate() {
		assertTrue(TypeMap.isDateTimeType(Types.DATE));
	}

	@Test
	void isDateTimeTypeFalseForDecimal() {
		assertFalse(TypeMap.isDateTimeType(Types.DECIMAL));
	}

	@Test
	void getJdbcTypeDescriptionsJoinsNamesWithCommaSpace() {
		int[] array = { Types.VARCHAR, Types.INTEGER };
		String result = TypeMap.getJdbcTypeDescriptions(array);
		assertEquals(result, "VARCHAR, INTEGER");
	}

	@Test
	void getJdbcTypeDescriptionsSingleTypeHasNoComma() {
		int[] array = { Types.VARCHAR };
		String result = TypeMap.getJdbcTypeDescriptions(array);
		assertEquals(result, "VARCHAR");
	}

	@Test
	void getJdbcTypeDescriptionsEmptyArrayReturnsEmptyString() {
		int[] array = {};
		String result = TypeMap.getJdbcTypeDescriptions(array);
		assertEquals(result, "");
	}

	@Test
	void getJdbcTypeDescriptionsUsesCodeStringForUnknownTypes() {
		int[] array = { 999, 9999 };
		String result = TypeMap.getJdbcTypeDescriptions(array);
		assertEquals(result, "999, 9999");
	}

}
