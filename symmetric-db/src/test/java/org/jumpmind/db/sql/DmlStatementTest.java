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

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.util.BinaryEncoding;
import org.junit.jupiter.api.Test;
import org.jumpmind.db.io.DatabaseXmlUtil;
import org.jumpmind.db.model.*;
import org.apache.commons.lang3.NotImplementedException;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.DmlStatement.DmlType;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DmlStatementTest {
    @Test
    void testInit_withMismatchedNullKeyValuesLength_usesAllKeysAndAllFalseNullKeyValues() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.DELETE, "test_table").keys(keys)
                .nullKeyValues(new boolean[] { true, false });
        DmlStatement dml = new DmlStatement(options);
        assertArrayEquals(keys, dml.getKeys());
        assertEquals("delete from test_table where id = ?", dml.getSql());
    }

    @Test
    void testInit_withNullKeyValuesMarkingSomeKeysNull_weedsOutNullKeysButKeepsThemInGeneratedSql() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER), keyColumn("id2", Types.INTEGER) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.DELETE, "test_table").keys(keys)
                .nullKeyValues(new boolean[] { true, false });
        DmlStatement dml = new DmlStatement(options);
        assertEquals(1, dml.getKeys().length);
        assertEquals("id2", dml.getKeys()[0].getName());
        assertArrayEquals(new int[] { Types.INTEGER }, dml.getTypes());
        assertEquals("delete from test_table where id is NULL and id2 = ?", dml.getSql());
    }

    @Test
    void testInit_withUpsertType_throwsNotImplementedException() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        assertThrows(NotImplementedException.class, () -> buildDml(DmlType.UPSERT, keys, columns));
    }

    @Test
    void testInit_withUnknownType_throwsNotImplementedException() {
        assertThrows(NotImplementedException.class, () -> buildDml(DmlType.UNKNOWN, null, null));
    }

    @Test
    void testInit_withQuotedIdentifiersTrue_usesDelimiterTokenAsQuote() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.DELETE, "test_table").keys(keys).quotedIdentifiers(true);
        DmlStatement dml = new DmlStatement(options);
        assertEquals("delete from \"test_table\" where \"id\" = ?", dml.getSql());
    }

    @Test
    void testInit_withQuotedIdentifiersTrueButNullDelimiterToken_usesEmptyQuote() {
        DatabaseInfo dbInfo = new DatabaseInfo();
        dbInfo.setDelimiterToken(null);
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.DELETE, "test_table").keys(keys).quotedIdentifiers(true)
                .databaseInfo(dbInfo);
        DmlStatement dml = new DmlStatement(options);
        assertEquals("delete from test_table where id = ?", dml.getSql());
    }

    @Test
    void testGetTypes_forInsert_returnsColumnTypesOnly() {
        Column[] columns = new Column[] { column("name", Types.VARCHAR), column("age", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.INSERT, null, columns);
        assertArrayEquals(new int[] { Types.VARCHAR, Types.INTEGER }, dml.getTypes());
    }

    @Test
    void testGetTypes_forUpdate_returnsColumnTypesThenKeyTypes() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.UPDATE, keys, columns);
        assertArrayEquals(new int[] { Types.VARCHAR, Types.INTEGER }, dml.getTypes());
    }

    @Test
    void testGetTypes_forDelete_returnsKeyTypesOnly() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        assertArrayEquals(new int[] { Types.INTEGER }, dml.getTypes());
    }

    @Test
    void testGetTypes_forSelect_returnsKeyTypesOnly() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.SELECT, keys, columns);
        assertArrayEquals(new int[] { Types.INTEGER }, dml.getTypes());
    }

    @Test
    void testGetTypes_forWhere_returnsNull() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.WHERE, keys, null);
        assertNull(dml.getTypes());
    }

    @Test
    void testGetTypeCode_dateWithDateOverrideToTimestamp_mapsToTimestampType() {
        DatabaseInfo dbInfo = new DatabaseInfo();
        dbInfo.setDateOverridesToTimestamp(true);
        Column[] columns = new Column[] { column("created", Types.DATE) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "test_table").columns(columns).databaseInfo(dbInfo);
        DmlStatement dml = new DmlStatement(options);
        assertArrayEquals(new int[] { Types.TIMESTAMP }, dml.getTypes());
    }

    @Test
    void testGetTypeCode_floatDoubleReal_mapToDecimalType() {
        Column[] columns = new Column[] { column("f", Types.FLOAT), column("d", Types.DOUBLE), column("r", Types.REAL) };
        DmlStatement dml = buildDml(DmlType.INSERT, null, columns);
        assertArrayEquals(new int[] { Types.DECIMAL, Types.DECIMAL, Types.DECIMAL }, dml.getTypes());
    }

    @Test
    void testGetSql_forFrom_buildsFromSqlWithWhereClause() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.FROM, keys, null);
        assertEquals(" from test_table where id = ?", dml.getSql());
    }

    @Test
    void testGetSql_forCount_withKeys_buildsCountSqlWithWhereClause() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.COUNT, keys, null);
        assertEquals("select count(*) from test_table where id = ?", dml.getSql());
    }

    @Test
    void testGetSql_forCount_withNoKeys_omitsWhereClause() {
        DmlStatement dml = buildDml(DmlType.COUNT, null, null);
        assertEquals("select count(*) from test_table", dml.getSql());
    }

    @Test
    void testGetSql_forSelectAll_withColumns_selectsSpecificColumns() {
        Column[] columns = new Column[] { column("id", Types.INTEGER), column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.SELECT_ALL, null, columns);
        assertEquals("select id, name from test_table", dml.getSql());
    }

    @Test
    void testGetSql_forSelectAll_withNoColumns_selectsStar() {
        DmlStatement dml = buildDml(DmlType.SELECT_ALL, null, null);
        assertEquals("select * from test_table", dml.getSql());
    }

    @Test
    void testGetSql_withTextColumnExpressionOnKeyColumn_wrapsWhereClauseParameter() {
        Column[] keys = new Column[] { keyColumn("code", Types.VARCHAR) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.DELETE, "test_table").keys(keys)
                .textColumnExpression("$(columnName)::text");
        DmlStatement dml = new DmlStatement(options);
        assertEquals("delete from test_table where code = ?::text", dml.getSql());
    }

    @Test
    void testGetSql_withTextColumnExpressionOnInsertColumn_wrapsColumnParameter() {
        Column[] columns = new Column[] { column("description", Types.VARCHAR) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "test_table").columns(columns)
                .textColumnExpression("$(columnName)::text");
        DmlStatement dml = new DmlStatement(options);
        assertEquals("insert into test_table (description) values (?::text)", dml.getSql());
    }

    @Test
    void testGetSql_withNamedParametersAndTextColumnExpression_substitutesNamedPlaceholderIntoExpression() {
        Column[] columns = new Column[] { column("description", Types.VARCHAR) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "test_table").columns(columns)
                .namedParameters(true).textColumnExpression("$(columnName)::text");
        DmlStatement dml = new DmlStatement(options);
        assertEquals("insert into test_table (description) values (:description::text)", dml.getSql());
    }

    @Test
    void testGetColumnsSql_prependsSelectColumnsBeforeExistingSql() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        Column[] selectColumns = new Column[] { column("name", Types.VARCHAR) };
        assertEquals("select namedelete from test_table where id = ?", dml.getColumnsSql(selectColumns));
    }

    @Test
    void testGetSql_boolOverload_ignoresArgumentAndReturnsSameSql() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        assertEquals(dml.getSql(), dml.getSql(true));
        assertEquals(dml.getSql(), dml.getSql(false));
    }

    @Test
    void testGetColumnKeyMetaData_concatenatesColumnsAndKeys() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.UPDATE, keys, columns);
        assertArrayEquals(new Column[] { columns[0], keys[0] }, dml.getColumnKeyMetaData());
    }

    @Test
    void testGetMetaData_forUpdate_returnsColumnKeyMetaData() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.UPDATE, keys, columns);
        assertArrayEquals(dml.getColumnKeyMetaData(), dml.getMetaData());
    }

    @Test
    void testGetMetaData_forInsert_returnsColumnsOnly() {
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.INSERT, null, columns);
        assertArrayEquals(columns, dml.getMetaData());
    }

    @Test
    void testGetMetaData_forDelete_returnsKeysOnly() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        assertArrayEquals(keys, dml.getMetaData());
    }

    @Test
    void testGetMetaData_forSelect_returnsNull() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.SELECT, keys, null);
        assertNull(dml.getMetaData());
    }

    @Test
    void testGetValueArrayTwoArrays_forUpdate_concatenatesColumnValuesThenKeyValues() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.UPDATE, keys, columns);
        Object[] result = dml.getValueArray(new Object[] { "n1" }, new Object[] { 1 });
        assertArrayEquals(new Object[] { "n1", 1 }, result);
    }

    @Test
    void testGetValueArrayTwoArrays_forInsert_returnsColumnValues() {
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.INSERT, null, columns);
        Object[] result = dml.getValueArray(new Object[] { "n1" }, new Object[] { 1 });
        assertArrayEquals(new Object[] { "n1" }, result);
    }

    @Test
    void testGetValueArrayTwoArrays_forDelete_returnsKeyValues() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        Object[] result = dml.getValueArray(new Object[] { "n1" }, new Object[] { 1 });
        assertArrayEquals(new Object[] { 1 }, result);
    }

    @Test
    void testGetValueArrayTwoArrays_forSelect_returnsNull() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.SELECT, keys, null);
        assertNull(dml.getValueArray(new Object[] { "n1" }, new Object[] { 1 }));
    }

    @Test
    void testGetValueArrayMap_withNullParams_returnsNull() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        assertNull(dml.getValueArray((Map<String, Object>) null));
    }

    @Test
    void testGetValueArrayMap_forInsert_mapsColumnsInOrder() {
        Column[] columns = new Column[] { column("name", Types.VARCHAR), column("age", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.INSERT, null, columns);
        Object[] result = dml.getValueArray(Map.of("name", "bob", "age", 42));
        assertArrayEquals(new Object[] { "bob", 42 }, result);
    }

    @Test
    void testGetValueArrayMap_forUpdate_mapsColumnsThenKeys() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        Column[] columns = new Column[] { column("name", Types.VARCHAR) };
        DmlStatement dml = buildDml(DmlType.UPDATE, keys, columns);
        Object[] result = dml.getValueArray(Map.of("name", "bob", "id", 1));
        assertArrayEquals(new Object[] { "bob", 1 }, result);
    }

    @Test
    void testGetValueArrayMap_forDelete_mapsKeys() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        Object[] result = dml.getValueArray(Map.of("id", 1));
        assertArrayEquals(new Object[] { 1 }, result);
    }

    @Test
    void testGetValueArrayMap_forUnsupportedType_throwsUnsupportedOperationException() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.WHERE, keys, null);
        assertThrows(UnsupportedOperationException.class, () -> dml.getValueArray(Map.of("id", 1)));
    }

    @Test
    void testBuildDynamicDeleteSql_buildsDeleteStatementFromKeysOnly() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        Row row = new Row(1);
        String sql = dml.buildDynamicDeleteSql(BinaryEncoding.HEX, row, false, true);
        assertEquals("delete from test_table where id is null;", sql);
    }

    @Test
    public void testBuildDynamicSqlUpdateNulls() {
        Database database = DatabaseXmlUtil.read(getClass().getResourceAsStream("/testDatabase.xml"));
        Table table = database.getTable(0);
        DmlStatementOptions options = new DmlStatementOptions(DmlType.UPDATE, table);
        DmlStatement dml = new DmlStatement(options);
        Row row = new Row(2);
        String sql = dml.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
        String expectedSql = "update test_simple_table set id = null, string_one_value = null where id is null;";
        assertEquals(expectedSql, sql);
    }

    @Test
    public void testBuildDynamicSqlSelectNulls() {
        Database database = DatabaseXmlUtil.read(getClass().getResourceAsStream("/testDatabase.xml"));
        Table table = database.getTable(0);
        DmlStatementOptions options = new DmlStatementOptions(DmlType.SELECT, table);
        DmlStatement dml = new DmlStatement(options);
        Row row = new Row(2);
        String sql = dml.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
        String expectedSql = "select id, string_one_value from test_simple_table where id is null;";
        assertEquals(expectedSql, sql);
    }

    @Test
    public void testBuildDynamicSqlDeleteNulls() {
        Database database = DatabaseXmlUtil.read(getClass().getResourceAsStream("/testDatabase.xml"));
        Table table = database.getTable(0);
        DmlStatementOptions options = new DmlStatementOptions(DmlType.DELETE, table);
        DmlStatement dml = new DmlStatement(options);
        Row row = new Row(2);
        String sql = dml.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
        String expectedSql = "delete from test_simple_table where id is null;";
        assertEquals(expectedSql, sql);
    }

    @Test
    public void testBuildDynamicSqlInsertNulls() {
        Database database = DatabaseXmlUtil.read(getClass().getResourceAsStream("/testDatabase.xml"));
        Table table = database.getTable(0);
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, table);
        DmlStatement dml = new DmlStatement(options);
        Row row = new Row(2);
        String sql = dml.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
        String expectedSql = "insert into test_simple_table (id, string_one_value) values (null,null);";
        assertEquals(expectedSql, sql);
    }

    @Test
    public void testBuildDynamicSqlWhereNulls() {
        Database database = DatabaseXmlUtil.read(getClass().getResourceAsStream("/testDatabase.xml"));
        Table table = database.getTable(0);
        DmlStatementOptions options = new DmlStatementOptions(DmlType.WHERE, table);
        DmlStatement dml = new DmlStatement(options);
        Row row = new Row(2);
        String sql = dml.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
        String expectedSql = "where id is null;";
        assertEquals(expectedSql, sql);
    }

    @Test
    public void testBuildDynamicSqlUpdateNonNulls() {
        Database database = DatabaseXmlUtil.read(getClass().getResourceAsStream("/testDatabase.xml"));
        Table table = database.getTable(0);
        DmlStatementOptions options = new DmlStatementOptions(DmlType.UPDATE, table);
        DmlStatement dml = new DmlStatement(options);
        Row row = new Row(2);
        row.put("id", 1);
        row.put("string_one_value", "test");
        String sql = dml.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
        String expectedSql = "update test_simple_table set id = 1, string_one_value = 'test' where id = 1;";
        assertEquals(expectedSql, sql);
    }

    @Test
    public void testBuildDynamicSqlSelectNonNulls() {
        Database database = DatabaseXmlUtil.read(getClass().getResourceAsStream("/testDatabase.xml"));
        Table table = database.getTable(0);
        DmlStatementOptions options = new DmlStatementOptions(DmlType.SELECT, table);
        DmlStatement dml = new DmlStatement(options);
        Row row = new Row(2);
        row.put("id", 1);
        row.put("string_one_value", "test");
        String sql = dml.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
        String expectedSql = "select id, string_one_value from test_simple_table where id = 1;";
        assertEquals(expectedSql, sql);
    }

    @Test
    public void testBuildDynamicSqlWithTimestamp() {
        Database database = DatabaseXmlUtil.read(getClass().getResourceAsStream("/testDatabase.xml"));
        Table table = database.getTable(1);
        DmlStatementOptions options = new DmlStatementOptions(DmlType.UPDATE, table);
        DmlStatement dml = new DmlStatement(options);
        Row row = new Row(1);
        row.put("ts", "2020-01-01 15:10:10");
        String sql = dml.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
        String expectedSql = "update testColumnWithTimestamp set ts = {ts '2020-01-01 15:10:10.000'};";
        assertEquals(expectedSql, sql);
    }

    @Test
    void testIsUpsertSupported_returnsFalse() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        assertFalse(dml.isUpsertSupported());
    }

    @Test
    void testIsNamedParameters_reflectsConstructorFlag() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.DELETE, "test_table").keys(keys).namedParameters(true);
        DmlStatement dml = new DmlStatement(options);
        assertTrue(dml.isNamedParameters());
        assertEquals("delete from test_table where id = :id", dml.getSql());
    }

    @Test
    void testGetLookupKeyData_returnsKeyValuesInKeyColumnOrder() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER), keyColumn("id2", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        Map<String, String> lookupData = new HashMap<>();
        lookupData.put("id", "1");
        lookupData.put("id2", "2");
        assertArrayEquals(new String[] { "1", "2" }, dml.getLookupKeyData(lookupData));
    }

    @Test
    void testGetLookupKeyData_withNoKeys_returnsNull() {
        DmlStatement dml = buildDml(DmlType.INSERT, null, new Column[] { column("name", Types.VARCHAR) });
        assertNull(dml.getLookupKeyData(Map.of("name", "x")));
    }

    @Test
    void testGetLookupKeyData_withEmptyLookupDataMap_returnsNull() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        assertNull(dml.getLookupKeyData(new HashMap<>()));
    }

    @Test
    void testUpdateCteExpression_instance_insertsValueAfterPrefixColonInSql() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        dml.sql = "with cte: as (select 1) delete from test_table where id = ?";
        dml.updateCteExpression("42", "cte");
        assertEquals("with cte:42 as (select 1) delete from test_table where id = ?", dml.getSql());
    }

    @Test
    void testUpdateCteExpression_instance_withBlankPrefix_leavesSqlUnchanged() {
        Column[] keys = new Column[] { keyColumn("id", Types.INTEGER) };
        DmlStatement dml = buildDml(DmlType.DELETE, keys, null);
        String before = dml.getSql();
        dml.updateCteExpression("42", " ");
        assertEquals(before, dml.getSql());
    }

    @Test
    void testUpdateCteExpression_static_insertsValueAfterPrefixColonInGivenSql() {
        String result = DmlStatement.updateCteExpression("with cte: as (select 1)", "42", "cte");
        assertEquals("with cte:42 as (select 1)", result);
    }

    @Test
    void testUpdateCteExpression_static_withNullSql_returnsEmptyString() {
        assertEquals("", DmlStatement.updateCteExpression(null, "42", "cte"));
    }

    @Test
    void testUpdateCteExpression_static_withBlankPrefix_returnsSqlUnchanged() {
        String sql = "with cte: as (select 1)";
        assertEquals(sql, DmlStatement.updateCteExpression(sql, "42", " "));
    }

    @Test
    void testGetNullKeyValues_pinsBugAlwaysReturningAllFalseRegardlessOfActualNulls() {
        // BUG: getNullKeyValues() checks `values == null` instead of `values[i] == null`,
        // so it always returns an all-false array even when elements actually are null.
        boolean[] result = DmlStatement.getNullKeyValues(new Object[] { "a", null, "c" });
        assertArrayEquals(new boolean[] { false, false, false }, result);
    }

    private static Column column(String name, int typeCode) {
        return new Column(name, false, typeCode, -1, -1);
    }

    private static Column keyColumn(String name, int typeCode) {
        return new Column(name, true, typeCode, -1, -1);
    }

    private static DmlStatement buildDml(DmlType type, Column[] keys, Column[] columns) {
        DmlStatementOptions options = new DmlStatementOptions(type, "test_table").keys(keys).columns(columns);
        return new DmlStatement(options);
    }
}
