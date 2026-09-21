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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractSqlTemplateTest {
    private TestableAbstractSqlTemplate template;

    @BeforeEach
    void setUp() {
        template = new TestableAbstractSqlTemplate();
    }

    @Test
    void testQueryForObject_returnsFirstResult() {
        template.rowsToReturn = rows("a", "b");
        String result = template.queryForObject("select value", row -> row.getString("value"));
        assertEquals("a", result);
    }

    @Test
    void testQueryForObject_returnsNullWhenEmpty() {
        String result = template.queryForObject("select value", row -> row.getString("value"));
        assertNull(result);
    }

    @Test
    void testQueryForString_delegatesToClassBasedQueryForObject() {
        template.objectToReturn = "hello";
        assertEquals("hello", template.queryForString("select value"));
    }

    @Test
    void testQueryForInt_withMapParams_substitutesAndReturnsValue() {
        template.objectToReturn = 5;
        Map<String, Object> params = new HashMap<>();
        params.put("x", 5);
        assertEquals(5, template.queryForInt("select :x", params));
    }

    @Test
    void testQueryForInt_returnsIntValue() {
        template.objectToReturn = 7;
        assertEquals(7, template.queryForInt("select value"));
    }

    @Test
    void testQueryForInt_returnsZeroWhenNull() {
        assertEquals(0, template.queryForInt("select value"));
    }

    @Test
    void testQueryForLong_returnsLongValue() {
        template.objectToReturn = 9L;
        assertEquals(9L, template.queryForLong("select value"));
    }

    @Test
    void testQueryForLong_returnsZeroWhenNull() {
        assertEquals(0L, template.queryForLong("select value"));
    }

    @Test
    void testQueryForMap_withKeyAndValueColumns() {
        template.rowsToReturn = new ArrayList<>();
        template.rowsToReturn.add(new Row(new String[] { "k", "v" }, new Object[] { "a", "1" }));
        template.rowsToReturn.add(new Row(new String[] { "k", "v" }, new Object[] { "b", "2" }));
        Map<String, Object> result = template.queryForMap("select k, v", "k", "v");
        assertEquals("1", result.get("a"));
        assertEquals("2", result.get("b"));
    }

    @Test
    void testQueryForMap_withMapperAndKeyColumn() {
        template.rowsToReturn = new ArrayList<>();
        template.rowsToReturn.add(new Row(new String[] { "k", "v" }, new Object[] { "a", "1" }));
        Map<String, Integer> result = template.queryForMap("select k, v", row -> row.getInt("v"), "k");
        assertEquals(1, result.get("a"));
    }

    @Test
    void testQueryForCursor_twoArg_readsMappedRows() {
        template.rowsToReturn = rows("a", "b");
        ISqlReadCursor<String> cursor = template.queryForCursor("select value", row -> row.getString("value"));
        assertEquals("a", cursor.next());
        assertEquals("b", cursor.next());
        assertNull(cursor.next());
    }

    @Test
    void testQueryForCursor_withHandlerIgnoresHandlerAndReadsMappedRows() {
        template.rowsToReturn = rows("a");
        ISqlReadCursor<String> cursor = template.queryForCursor("select value", row -> row.getString("value"), null, null, null);
        assertEquals("a", cursor.next());
    }

    @Test
    void testQueryForCursor_withReturnLobObjectsIgnoresFlag() {
        template.rowsToReturn = rows("a");
        ISqlReadCursor<String> cursor = template.queryForCursor("select value", row -> row.getString("value"), true);
        assertEquals("a", cursor.next());
    }

    @Test
    void testQueryForCursor_withNamedParams_substitutesAndReadsMappedRows() {
        template.rowsToReturn = rows("a");
        Map<String, Object> params = new HashMap<>();
        params.put("x", 5);
        ISqlReadCursor<String> cursor = template.queryForCursor("select :x", row -> row.getString("value"), params);
        assertEquals("a", cursor.next());
    }

    @Test
    void testQuery_singleSql_returnsRows() {
        Row row = new Row("value", "a");
        template.rowsToReturn = new ArrayList<>(List.of(row));
        assertEquals(List.of(row), template.query("select value"));
    }

    @Test
    void testQuery_withMapperAndArgs_returnsMappedValues() {
        template.rowsToReturn = rows("a", "b");
        List<String> result = template.query("select value", row -> row.getString("value"), "unused");
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void testQueryWithHandler_ignoresHandlerAndReturnsMappedValues() {
        template.rowsToReturn = rows("a");
        List<String> result = template.queryWithHandler("select value", row -> row.getString("value"), null, "unused");
        assertEquals(List.of("a"), result);
    }

    @Test
    void testQueryForRow_returnsFirstRow() {
        Row row = new Row("value", "a");
        template.rowsToReturn = new ArrayList<>(List.of(row));
        assertSame(row, template.queryForRow("select value"));
    }

    @Test
    void testQueryForRow_returnsNullWhenEmpty() {
        assertNull(template.queryForRow("select value"));
    }

    @Test
    void testQuery_keyColValueColBuildsMap() {
        template.rowsToReturn = new ArrayList<>();
        template.rowsToReturn.add(new Row(new String[] { "id", "name" }, new Object[] { 1, "Alice" }));
        Map<Integer, String> result = template.query("select id, name", "id", "name", null, null);
        assertEquals("Alice", result.get(1));
    }

    @Test
    void testQuery_withMaxRowsMapperAndArgs_returnsMappedValues() {
        template.rowsToReturn = rows("a", "b");
        List<String> result = template.query("select value", 10, row -> row.getString("value"), "unused");
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void testQuery_withMaxRowsMapperAndNamedParams_substitutesAndReturnsMappedValues() {
        template.rowsToReturn = rows("a");
        Map<String, Object> params = new HashMap<>();
        params.put("x", 5);
        List<String> result = template.query("select :x", 10, row -> row.getString("value"), params);
        assertEquals(List.of("a"), result);
    }

    @Test
    void testQuery_withMapperAndNamedParamsMap_substitutesAndReturnsMappedValues() {
        template.rowsToReturn = rows("a");
        Map<String, Object> params = new HashMap<>();
        params.put("x", 5);
        List<String> result = template.query("select :x", row -> row.getString("value"), params);
        assertEquals(List.of("a"), result);
    }

    @Test
    void testQuery_withArgsAndTypes_returnsRows() {
        Row row = new Row("value", "a");
        template.rowsToReturn = new ArrayList<>(List.of(row));
        assertEquals(List.of(row), template.query("select value", (Object[]) null, (int[]) null));
    }

    @Test
    void testQuery_withArgsOnly_returnsRows() {
        Row row = new Row("value", "a");
        template.rowsToReturn = new ArrayList<>(List.of(row));
        assertEquals(List.of(row), template.query("select value", (Object[]) null));
    }

    @Test
    void testQuery_withMapperArgsAndTypes_returnsMappedValues() {
        template.rowsToReturn = rows("a");
        List<String> result = template.query("select value", row -> row.getString("value"), (Object[]) null, (int[]) null);
        assertEquals(List.of("a"), result);
    }

    @Test
    void testQuery_withMapperHandlerArgsAndTypes_ignoresHandlerAndReturnsMappedValues() {
        template.rowsToReturn = rows("a");
        List<String> result = template.query("select value", row -> row.getString("value"), null, null, null);
        assertEquals(List.of("a"), result);
    }

    @Test
    void testQuery_withMaxRowsGreaterThanZero_limitsResults() {
        template.rowsToReturn = rows("a", "b", "c");
        List<String> result = template.query("select value", 2, row -> row.getString("value"), null, null);
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void testQuery_withMaxRowsZero_treatedAsUnlimited() {
        template.rowsToReturn = rows("a", "b", "c");
        List<String> result = template.query("select value", 0, row -> row.getString("value"), null, null);
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void testQuery_withHandlerMaxRowsArgsAndTypes_returnsMappedValuesAndClosesCursor() {
        template.rowsToReturn = rows("a", "b");
        List<String> result = template.query("select value", -1, row -> row.getString("value"), null, null, null);
        assertEquals(List.of("a", "b"), result);
        assertTrue(template.closed);
    }

    @Test
    void testQuery_withEmptyRows_returnsEmptyList() {
        List<String> result = template.query("select value", -1, row -> row.getString("value"), null, null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testQuery_whenMapperThrows_closesCursorAndPropagates() {
        template.rowsToReturn = rows("a", "bad");
        assertThrows(RuntimeException.class, () -> template.query("select value", -1, row -> {
            String value = row.getString("value");
            if ("bad".equals(value)) {
                throw new RuntimeException("boom");
            }
            return value;
        }, null, null, null));
        assertTrue(template.closed);
    }

    @Test
    void testUpdate_singleArgArray_delegatesToFullOverload() {
        template.updateResult = 3;
        assertEquals(3, template.update("update foo set bar = ?", "value"));
    }

    @Test
    void testExpandSql_withSqlList_replacesTokenWithPlaceholders() {
        SqlList list = new SqlList(":ids", Arrays.asList("a", "b", "c"));
        String result = template.expandSql("select * from foo where id in (:ids)", new Object[] { list });
        assertEquals("select * from foo where id in (?,?,?)", result);
    }

    @Test
    void testExpandSql_withSqlToken_replacesTokenWithSinglePlaceholder() {
        SqlToken token = new SqlToken(":name", "value");
        String result = template.expandSql("select * from foo where name = :name", new Object[] { token });
        assertEquals("select * from foo where name = ?", result);
    }

    @Test
    void testExpandSql_withPlainArgs_leavesSqlUnchanged() {
        String result = template.expandSql("select * from foo where id = ?", new Object[] { 1 });
        assertEquals("select * from foo where id = ?", result);
    }

    @Test
    void testExpandSql_withNullArgs_leavesSqlUnchanged() {
        assertEquals("select * from foo", template.expandSql("select * from foo", null));
    }

    @Test
    void testExpandArgs_withSqlListPresentInSql_expandsIntoIndividualItems() {
        SqlList list = new SqlList(":ids", Arrays.asList("a", "b"));
        Object[] result = template.expandArgs("select * from foo where id in (:ids)", new Object[] { list });
        assertEquals(List.of("a", "b"), Arrays.asList(result));
    }

    @Test
    void testExpandArgs_withSqlListNotPresentInSql_excludesList() {
        SqlList list = new SqlList(":ids", Arrays.asList("a", "b"));
        Object[] result = template.expandArgs("select * from foo", new Object[] { list });
        assertEquals(0, result.length);
    }

    @Test
    void testExpandArgs_withSqlTokenPresentInSql_addsTokenValue() {
        SqlToken token = new SqlToken(":name", "value");
        Object[] result = template.expandArgs("select * from foo where name = :name", new Object[] { token });
        assertEquals(List.of("value"), Arrays.asList(result));
    }

    @Test
    void testExpandArgs_withMixOfPlainAndSpecialArgs_preservesOrder() {
        SqlToken token = new SqlToken(":name", "value");
        Object[] result = template.expandArgs("select * from foo where a = ? and name = :name and b = ?",
                new Object[] { 1, token, 2 });
        assertEquals(List.of(1, "value", 2), Arrays.asList(result));
    }

    @Test
    void testExpandArgs_withNullArgs_returnsNull() {
        assertNull(template.expandArgs("select * from foo", null));
    }

    @Test
    void testTranslate_singleArg_delegatesToMessageAndCause() {
        RuntimeException cause = new RuntimeException("boom");
        SqlException result = template.translate(cause);
        assertEquals("boom", result.getMessage());
        assertSame(cause, result.getCause());
    }

    @Test
    void testTranslate_withUniqueKeyViolation_wrapsInUniqueKeyException() {
        template.uniqueKeyViolation = true;
        RuntimeException cause = new RuntimeException("boom");
        SqlException result = template.translate("boom", cause);
        assertTrue(result instanceof UniqueKeyException);
        assertSame(cause, result.getCause());
    }

    @Test
    void testTranslate_withUniqueKeyViolationAndAlreadyUniqueKeyException_returnsSameInstance() {
        template.uniqueKeyViolation = true;
        UniqueKeyException ex = new UniqueKeyException("boom");
        SqlException result = template.translate("boom", ex);
        assertSame(ex, result);
    }

    @Test
    void testTranslate_withDataTruncationViolation_wrapsInDataTruncationException() {
        template.dataTruncationViolation = true;
        RuntimeException cause = new RuntimeException("boom");
        SqlException result = template.translate("boom", cause);
        assertTrue(result instanceof DataTruncationException);
        assertSame(cause, result.getCause());
    }

    @Test
    void testTranslate_withSqlException_returnsSameInstance() {
        SqlException ex = new SqlException("boom");
        assertSame(ex, template.translate("boom", ex));
    }

    @Test
    void testTranslate_withGenericException_wrapsInSqlException() {
        RuntimeException cause = new RuntimeException("boom");
        SqlException result = template.translate("wrapped", cause);
        assertEquals("wrapped", result.getMessage());
        assertSame(cause, result.getCause());
    }

    @Test
    void testGetUniqueKeyViolationIndexName_returnsNull() {
        assertNull(template.getUniqueKeyViolationIndexName(new RuntimeException("boom")));
    }

    @Test
    void testIsForeignKeyChildExistsViolation_returnsFalse() {
        assertFalse(template.isForeignKeyChildExistsViolation(new RuntimeException("boom")));
    }

    @Test
    void testIsDeadlock_returnsFalse() {
        assertFalse(template.isDeadlock(new RuntimeException("boom")));
    }

    @Test
    void testDoesObjectAlreadyExist_returnsFalse() {
        assertFalse(template.doesObjectAlreadyExist(new RuntimeException("boom")));
    }

    @Test
    void testDoesObjectNotExist_returnsFalse() {
        assertFalse(template.doesObjectNotExist(new RuntimeException("boom")));
    }

    @Test
    void testSetThreadLocalConnectionHandler_doesNotThrow() {
        assertDoesNotThrow(() -> template.setThreadLocalConnectionHandler(null));
    }

    @Test
    void testClearThreadLocalConnectionHandler_doesNotThrow() {
        assertDoesNotThrow(template::clearThreadLocalConnectionHandler);
    }

    private static List<Row> rows(String... values) {
        List<Row> rows = new ArrayList<>();
        for (String value : values) {
            rows.add(new Row("value", value));
        }
        return rows;
    }

    private static class TestableAbstractSqlTemplate extends AbstractSqlTemplate {
        List<Row> rowsToReturn = new ArrayList<>();
        Object objectToReturn;
        int updateResult;
        boolean uniqueKeyViolation;
        boolean dataTruncationViolation;
        boolean closed;

        @Override
        public <T> ISqlReadCursor<T> queryForCursor(String sql, ISqlRowMapper<T> mapper, Object[] params, int[] types) {
            Iterator<Row> iterator = rowsToReturn.iterator();
            closed = false;
            return new ISqlReadCursor<T>() {
                @Override
                public T next() {
                    while (iterator.hasNext()) {
                        T value = mapper.mapRow(iterator.next());
                        if (value != null) {
                            return value;
                        }
                    }
                    return null;
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> clazz, Object... params) {
            return (T) objectToReturn;
        }

        @Override
        public int update(String sql, Object[] values, int[] types) {
            return updateResult;
        }

        @Override
        public boolean isUniqueKeyViolation(Throwable ex) {
            return uniqueKeyViolation;
        }

        @Override
        public boolean isDataTruncationViolation(Throwable ex) {
            return dataTruncationViolation;
        }

        @Override
        public byte[] queryForBlob(String sql, int jdbcTypeCode, String jdbcTypeName, Object... args) {
            return null;
        }

        @Override
        public String queryForClob(String sql, int jdbcTypeCode, String jdbcTypeName, Object... args) {
            return null;
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... params) {
            return null;
        }

        @Override
        public int update(boolean autoCommit, boolean failOnError, int commitRate, ISqlResultsListener listener, String... sql) {
            return 0;
        }

        @Override
        public int update(boolean autoCommit, boolean failOnError, boolean failOnDrops, boolean failOnSequenceCreate,
                int commitRate, ISqlResultsListener listener, ISqlStatementSource source) {
            return 0;
        }

        @Override
        public int update(boolean autoCommit, boolean failOnError, int commitRate, String... sql) {
            return 0;
        }

        @Override
        public void testConnection() {
        }

        @Override
        public boolean isForeignKeyViolation(Throwable ex) {
            return false;
        }

        @Override
        public ISqlTransaction startSqlTransaction() {
            return null;
        }

        @Override
        public ISqlTransaction startSqlTransaction(boolean autoCommit) {
            return null;
        }

        @Override
        public int getDatabaseMajorVersion() {
            return 0;
        }

        @Override
        public int getDatabaseMinorVersion() {
            return 0;
        }

        @Override
        public String getDatabaseProductName() {
            return null;
        }

        @Override
        public String getDatabaseProductVersion() {
            return null;
        }

        @Override
        public String getDriverName() {
            return null;
        }

        @Override
        public String getDriverVersion() {
            return null;
        }

        @Override
        public Set<String> getSqlKeywords() {
            return null;
        }

        @Override
        public boolean supportsGetGeneratedKeys() {
            return false;
        }

        @Override
        public boolean isStoresUpperCaseIdentifiers() {
            return false;
        }

        @Override
        public boolean isStoresLowerCaseIdentifiers() {
            return false;
        }

        @Override
        public boolean isStoresMixedCaseQuotedIdentifiers() {
            return false;
        }

        @Override
        public long insertWithGeneratedKey(String sql, String column, String sequenceName, Object[] args, int[] types) {
            return 0;
        }
    }
}
