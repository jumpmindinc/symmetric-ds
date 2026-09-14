/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NamedParameterUtilsTest {
    @Test
    void testParseSqlStatement_parsesDistinctNamedParameters() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :bar");
        assertEquals(2, parsed.getNamedParameterCount());
        assertEquals(0, parsed.getUnnamedParameterCount());
        assertEquals(2, parsed.getTotalParameterCount());
        assertEquals(List.of("foo", "bar"), parsed.getParameterNames());
    }

    @Test
    void testParseSqlStatement_countsRepeatedParameterOnceButTotalCountsEach() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :foo");
        assertEquals(1, parsed.getNamedParameterCount());
        assertEquals(2, parsed.getTotalParameterCount());
        assertEquals(List.of("foo", "foo"), parsed.getParameterNames());
    }

    @Test
    void testSubstituteNamedParameters_expandsCollectionIntoCommaSeparatedPlaceholders() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where id in (:ids)");
        Map<String, Object> params = Map.of("ids", List.of(1, 2, 3));
        String sql = NamedParameterUtils.substituteNamedParameters(parsed, params);
        assertEquals("select * from t where id in (?, ?, ?)", sql);
    }

    @Test
    void testBuildValueArray_throwsWhenMixingNamedAndUnnamedParameters() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = ?");
        Map<String, Object> params = Map.of("foo", 1);
        assertThrows(IllegalStateException.class, () -> NamedParameterUtils.buildValueArray(parsed, params));
    }

    @Test
    void testSubstituteNamedParameters_substitutesNamedParameterWithPlaceholder() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :bar");
        Map<String, Object> params = Map.of("foo", 1, "bar", 2);
        String sql = NamedParameterUtils.substituteNamedParameters(parsed, params);
        assertEquals("select * from t where a = ? and b = ?", sql);
    }

    @Test
    void testSubstituteNamedParameters_expandsCollectionOfArraysIntoGroupedPlaceholders() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where (a, b) in (:tuples)");
        Map<String, Object> params = Map.of("tuples", List.of(new Object[] { 1, 2 }, new Object[] { 3, 4 }));
        String sql = NamedParameterUtils.substituteNamedParameters(parsed, params);
        assertEquals("select * from t where (a, b) in ((?, ?), (?, ?))", sql);
    }

    @Test
    void testSubstituteNamedParameters_throwsWhenParameterMissingFromMap() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo");
        Map<String, Object> params = Map.of("bar", 1);
        assertThrows(InvalidSqlException.class, () -> NamedParameterUtils.substituteNamedParameters(parsed, params));
    }

    static Stream<Arguments> sqlWithSingleFooParameter() {
        return Stream.of(
                Arguments.of("postgres cast operator", "select a::int from t where b = :foo"),
                Arguments.of("single quotes", "select ':notparam' from t where a = :foo"),
                Arguments.of("double quotes", "select \":notcolumn\" from t where a = :foo"),
                Arguments.of("line comment", "select * from t -- skip :notparam\n where a = :foo"),
                Arguments.of("block comment", "select * /* skip :notparam */ from t where a = :foo"));
    }

    @ParameterizedTest(name = "parses single parameter ignoring {0}")
    @MethodSource("sqlWithSingleFooParameter")
    void testParseSqlStatement_parsesSingleNamedParameterInVariousContexts(String description, String sql) {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement(sql);
        assertEquals(1, parsed.getNamedParameterCount());
        assertEquals(List.of("foo"), parsed.getParameterNames());
    }

    @Test
    void testBuildValueArray_buildsValueArrayInParameterOrder() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :bar");
        Map<String, Object> params = Map.of("foo", 1, "bar", 2);
        Object[] values = NamedParameterUtils.buildValueArray(parsed, params);
        assertArrayEquals(new Object[] { 1, 2 }, values);
    }

    @Test
    void testBuildValueArray_repeatsValueForRepeatedParameter() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where a = :foo and b = :foo");
        Map<String, Object> params = Map.of("foo", 1);
        Object[] values = NamedParameterUtils.buildValueArray(parsed, params);
        assertArrayEquals(new Object[] { 1, 1 }, values);
    }

    @Test
    void testBuildValueArray_flattensCollectionValueIntoArray() {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement("select * from t where id in (:ids)");
        Map<String, Object> params = Map.of("ids", List.of(1, 2, 3));
        Object[] values = NamedParameterUtils.buildValueArray(parsed, params);
        assertArrayEquals(new Object[] { 1, 2, 3 }, values);
    }
}