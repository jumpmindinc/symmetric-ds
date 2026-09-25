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
package org.jumpmind.db.platform.db2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Db2DdlReaderTest {
    private static final String SQL_WITH_SCHEMA = "SELECT NAME, IDENTITY, GENERATED FROM SYSIBM.SYSCOLUMNS WHERE TBNAME=? AND TBCREATOR=?";
    private static final String SQL_WITHOUT_SCHEMA = "SELECT NAME, IDENTITY, GENERATED FROM SYSIBM.SYSCOLUMNS WHERE TBNAME=?";
    private static final String TABLE_NAME = "TEST_TABLE";
    private static final String SCHEMA_NAME = "DB2INST1";
    private static final String COLUMN_NAME = "ID";
    private Db2DatabasePlatform platform;
    private Db2DdlReader ddlReader;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private DatabaseMetaDataWrapper metaData;

    @BeforeEach
    void setup() throws SQLException {
        platform = mock(Db2DatabasePlatform.class);
        ddlReader = new Db2DdlReader(platform);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        metaData = new DatabaseMetaDataWrapper();
        metaData.setSchemaPattern(SCHEMA_NAME);
    }

    @Test
    void testConstructor_clearsDefaultCatalogAndSchemaPatterns() {
        Db2DdlReader reader = new Db2DdlReader(platform);
        assertNull(reader.getDefaultCatalogPattern());
        assertNull(reader.getDefaultSchemaPattern());
    }

    @Test
    void testReadRelation_knownSystemTable_returnsNullWithoutReadingFurther() throws Exception {
        for (String systemTable : new String[] { "STMG_DBSIZE_INFO", "HMON_ATM_INFO", "HMON_COLLECTION", "POLICY" }) {
            Map<String, Object> values = new HashMap<>();
            values.put("TABLE_NAME", systemTable);
            assertNull(ddlReader.readRelation(connection, metaData, values));
        }
    }

    @Test
    void testEnhanceTableMetaData_identityColumn() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, "Y", "D");
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertTrue(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        verify(connection).prepareStatement(SQL_WITH_SCHEMA);
        verify(statement).setString(1, TABLE_NAME);
        verify(statement).setString(2, SCHEMA_NAME);
    }

    @Test
    void testEnhanceTableMetaData_nonIdentityColumn() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, "N", "");
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_nullIdentityValue() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, null, "");
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_nullGeneratedValue() throws Exception {
        Column column = new Column(COLUMN_NAME);
        column.setGenerated(true);
        stubRow(COLUMN_NAME, "Y", null);
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertTrue(column.isAutoIncrement());
        assertTrue(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_identitySetGeneratedAlways() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, "Y", "A");
        column.setGenerated(true);
        column.setPrimaryKey(true);
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertTrue(column.isAutoIncrement());
        assertTrue(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_columnNotOnTable() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow("MISSING_COLUMN", "Y", "D");
        Table table = mock(Table.class);
        when(table.getColumnWithName("MISSING_COLUMN")).thenReturn(null);
        ddlReader.enhanceTableMetaData(connection, metaData, table);
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_noSchemaPattern() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, "Y", "D");
        ddlReader.enhanceTableMetaData(connection, new DatabaseMetaDataWrapper(), mockTable(column));
        assertTrue(column.isAutoIncrement());
        verify(connection).prepareStatement(SQL_WITHOUT_SCHEMA);
        verify(statement).setString(1, TABLE_NAME);
        verify(statement, never()).setString(2, SCHEMA_NAME);
    }

    @Test
    void testClearGeneratedIfIdentityByDefault_generatedByDefault_clearsGeneratedFlag() {
        Column column = new Column(COLUMN_NAME);
        column.setGenerated(true);
        ddlReader.clearGeneratedIfIdentityByDefault("D", column);
        assertFalse(column.isGenerated());
    }

    @Test
    void testClearGeneratedIfIdentityByDefault_generatedAlways_leavesGeneratedFlagUnchanged() {
        Column column = new Column(COLUMN_NAME);
        column.setGenerated(true);
        ddlReader.clearGeneratedIfIdentityByDefault("A", column);
        assertTrue(column.isGenerated());
    }

    @Test
    void testClearGeneratedIfIdentityByDefault_nullValue_leavesGeneratedFlagUnchanged() {
        Column column = new Column(COLUMN_NAME);
        column.setGenerated(true);
        ddlReader.clearGeneratedIfIdentityByDefault(null, column);
        assertTrue(column.isGenerated());
    }

    @Test
    void testReadColumn_timeDefaultValue_convertsDb2FormatToStandardFormat() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.TIME);
        values.put("TYPE_NAME", "TIME");
        values.put("COLUMN_DEF", "'14.30.00'");
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("'14:30:00'", column.getDefaultValue());
    }

    @Test
    void testReadColumn_timestampDefaultValueWithFraction_convertsDb2FormatToStandardFormat() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.TIMESTAMP);
        values.put("TYPE_NAME", "TIMESTAMP");
        values.put("COLUMN_DEF", "'2024-01-15-14.30.00.123456'");
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("'2024-01-15 14:30:00.123456'", column.getDefaultValue());
    }

    @Test
    void testReadColumn_textDefaultValue_unquotesAndUnescapes() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.VARCHAR);
        values.put("TYPE_NAME", "VARCHAR");
        values.put("COLUMN_DEF", "'it''s'");
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("it's", column.getDefaultValue());
    }

    @Test
    void testReadColumn_timestampType_resetsColumnSizeToScale() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.TIMESTAMP);
        values.put("TYPE_NAME", "TIMESTAMP");
        values.put("DECIMAL_DIGITS", 6);
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("6", column.getSize());
    }

    @Test
    void testReadColumn_dateType_removesColumnSize() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.DATE);
        values.put("TYPE_NAME", "DATE");
        Column column = ddlReader.readColumn(metaData, values);
        assertNull(column.getSize());
    }

    @Test
    void testIsInternalPrimaryKeyIndex_sqlPrefixedNumericSuffix_returnsTrue() throws SQLException {
        IIndex index = new NonUniqueIndex("SQL060205225246220");
        Table table = new Table(TABLE_NAME);
        assertTrue(ddlReader.isInternalPrimaryKeyIndex(connection, metaData, table, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_sqlPrefixedNonNumericSuffix_returnsFalse() throws SQLException {
        IIndex index = new NonUniqueIndex("SQLNOTANUMBER");
        Table table = new Table(TABLE_NAME);
        assertFalse(ddlReader.isInternalPrimaryKeyIndex(connection, metaData, table, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_nonSqlPrefixedNameWithNoMatchingPrimaryKey_returnsFalse() throws Exception {
        IIndex index = new NonUniqueIndex("MY_PK_INDEX");
        Table table = new Table(TABLE_NAME);
        DatabaseMetaData rawMetaData = mock(DatabaseMetaData.class);
        metaData.setMetaData(rawMetaData);
        ResultSet pkResultSet = mock(ResultSet.class);
        when(rawMetaData.getPrimaryKeys(any(), any(), eq(TABLE_NAME))).thenReturn(pkResultSet);
        when(pkResultSet.next()).thenReturn(false);
        assertFalse(ddlReader.isInternalPrimaryKeyIndex(connection, metaData, table, index));
        verify(pkResultSet).close();
    }

    @Test
    void testIsInternalForeignKeyIndex_matchingNameIgnoringCase_returnsTrue() throws SQLException {
        ForeignKey fk = new ForeignKey("fk_test");
        IIndex index = new NonUniqueIndex("FK_TEST");
        Table table = new Table(TABLE_NAME);
        assertTrue(ddlReader.isInternalForeignKeyIndex(connection, metaData, table, fk, index));
    }

    @Test
    void testIsInternalForeignKeyIndex_nonMatchingName_returnsFalse() throws SQLException {
        ForeignKey fk = new ForeignKey("fk_test");
        IIndex index = new NonUniqueIndex("OTHER_INDEX");
        Table table = new Table(TABLE_NAME);
        assertFalse(ddlReader.isInternalForeignKeyIndex(connection, metaData, table, fk, index));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetTriggers_queriesWithTableNameAndSchema() throws Exception {
        JdbcSqlTemplate sqlTemplate = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME)))
                .thenReturn(new ArrayList<Trigger>());
        List<Trigger> triggers = ddlReader.getTriggers("CATALOG1", SCHEMA_NAME, TABLE_NAME);
        assertTrue(triggers.isEmpty());
        verify(sqlTemplate).query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME));
    }

    @Test
    void testGetTriggers_mapRow_insertAfterStatementEnabledTrigger() throws Exception {
        Trigger trigger = mapTriggerRow("I", "A", "S", "Y");
        assertEquals(TriggerType.INSERT, trigger.getTriggerType());
        assertEquals("AFTER", trigger.getMetaData().get("TRIGGER_TIME"));
        assertEquals("ONCE PER STATEMENT", trigger.getMetaData().get("GRANULARITY"));
        assertTrue(trigger.isEnabled());
        assertFalse(trigger.getMetaData().containsKey("TEXT"));
    }

    @Test
    void testGetTriggers_mapRow_updateBeforeRowDisabledTrigger() throws Exception {
        Trigger trigger = mapTriggerRow("U", "B", "R", "N");
        assertEquals(TriggerType.UPDATE, trigger.getTriggerType());
        assertEquals("BEFORE", trigger.getMetaData().get("TRIGGER_TIME"));
        assertEquals("ONCE PER ROW", trigger.getMetaData().get("GRANULARITY"));
        assertFalse(trigger.isEnabled());
    }

    @Test
    void testGetTriggers_mapRow_deleteInsteadOfTrigger() throws Exception {
        Trigger trigger = mapTriggerRow("D", "I", "S", "Y");
        assertEquals(TriggerType.DELETE, trigger.getTriggerType());
        assertEquals("INSTEAD OF", trigger.getMetaData().get("TRIGGER_TIME"));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_rowidTypeName_returnsVarchar() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "ROWID");
        assertEquals(Types.VARCHAR, ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_clobTypeName_returnsLongVarchar() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "DBCLOB");
        assertEquals(Types.LONGVARCHAR, ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_longVarcharTypeName_returnsMappedTypeForLongVarChar() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "LONG VARCHAR");
        assertEquals(Types.LONGVARCHAR, ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_xmlTypeName_returnsSqlXml() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "XML");
        assertEquals(Types.SQLXML, ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_unrecognizedTypeName_fallsBackToSuper() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "INTEGER");
        assertNull(ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testGetMappedTypeForLongVarChar_returnsLongVarchar() {
        assertEquals(Types.LONGVARCHAR, ddlReader.getMappedTypeForLongVarChar());
    }

    @Test
    void testRemoveGeneratedColumns_rowidAndGeneratedLobColumns_areRemoved() throws SQLException {
        Column keepColumn = new Column("NAME", false, Types.VARCHAR, 50, 0);
        Column rowIdColumn = new Column("ROW_ID", false, Types.ROWID, 0, 0);
        Column generatedLobColumn = new Column("DB2_GENERATED_ROWID_FOR_LOBS", false, Types.CHAR, 0, 0);
        Table table = new Table(TABLE_NAME, keepColumn, rowIdColumn, generatedLobColumn);
        ddlReader.removeGeneratedColumns(connection, metaData, table);
        assertEquals(1, table.getColumnCount());
        assertEquals("NAME", table.getColumn(0).getName());
    }

    @Test
    void testRemoveGeneratedColumns_noGeneratedColumns_leavesTableUnchanged() throws SQLException {
        Column keepColumn = new Column("NAME", false, Types.VARCHAR, 50, 0);
        Table table = new Table(TABLE_NAME, keepColumn);
        ddlReader.removeGeneratedColumns(connection, metaData, table);
        assertEquals(1, table.getColumnCount());
        assertEquals("NAME", table.getColumn(0).getName());
    }

    @SuppressWarnings("unchecked")
    private Trigger mapTriggerRow(String triggerType, String triggerTime, String granularity, String enabled) throws Exception {
        JdbcSqlTemplate sqlTemplate = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ArgumentCaptor<ISqlRowMapper<Trigger>> captor = (ArgumentCaptor<ISqlRowMapper<Trigger>>) (ArgumentCaptor<?>) ArgumentCaptor
                .forClass(ISqlRowMapper.class);
        when(sqlTemplate.query(anyString(), captor.capture(), eq(TABLE_NAME), eq(SCHEMA_NAME))).thenReturn(new ArrayList<Trigger>());
        ddlReader.getTriggers("CATALOG1", SCHEMA_NAME, TABLE_NAME);
        Row row = new Row(
                new String[] { "TRIGGER_NAME", "SCHEMA", "TABLE_NAME", "ENABLED", "TEXT", "TRIGGER_TYPE", "TRIGGER_TIME", "GRANULARITY" },
                new Object[] { "TRG1", SCHEMA_NAME, TABLE_NAME, enabled, "BEGIN END", triggerType, triggerTime, granularity });
        return captor.getValue().mapRow(row);
    }

    private Map<String, Object> baseColumnValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("COLUMN_NAME", COLUMN_NAME);
        values.put("NUM_PREC_RADIX", 10);
        values.put("DECIMAL_DIGITS", 0);
        values.put("COLUMN_SIZE", "50");
        return values;
    }

    private void stubRow(String columnName, String identity, String generated) throws SQLException {
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString(1)).thenReturn(columnName);
        when(resultSet.getString(2)).thenReturn(identity);
        when(resultSet.getString(3)).thenReturn(generated);
    }

    private Table mockTable(Column column) {
        Table table = mock(Table.class);
        when(table.getName()).thenReturn(TABLE_NAME);
        when(table.getColumnWithName(column.getName())).thenReturn(column);
        return table;
    }
}
