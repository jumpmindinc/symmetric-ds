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
package org.jumpmind.db.platform.postgresql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.jumpmind.db.DdlReaderTestConstants;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.model.UniqueIndex;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class PostgreSqlDdlReaderTest {
    protected IDatabasePlatform platform;
    protected Pattern postgresIsoDatePattern;
    /*
     * The regular expression pattern for the postgres conversion of ISO times.
     */
    protected Pattern postgresIsoTimePattern;
    /*
     * The regular expression pattern for the postgres conversion of ISO timestamps.
     */
    protected Pattern postgresIsoTimestampPattern;
    ISqlTransaction sqlTransaction;
    protected AbstractJdbcDdlReader abstractJdbcDdlReader;
    ThreadLocalRandom rand = ThreadLocalRandom.current();

    @BeforeEach
    public void setUp() throws Exception {
        platform = mock(PostgreSqlDatabasePlatform.class);
        postgresIsoDatePattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD'\\)");
        postgresIsoTimePattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'HH24:MI:SS'\\)");
        postgresIsoTimestampPattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD HH24:MI:SS'\\)");
    }

    /***
     * Sets up a mock result set for a prepared SQL statement used by the PostgresqlDdlReader.readMetaDataAndPrimaryKeyConstraint()
     * <ul>
     * <li>trait | value
     * <li>PRIMARY_KEY_NAME | primaryKeyConstaintName
     * <li>| loggingEnabled
     * </ul>
     */
    PreparedStatement mockPreparedStatement4TablePrimaryKeyConstraintAndLogging(boolean loggingEnabled, String primaryKeyConstaintName) throws SQLException {
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        ResultSet stmtrs1 = mock(ResultSet.class);
        when(stmt1.executeQuery()).thenReturn(stmtrs1);
        when(stmtrs1.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn("PRIMARY_KEY_NAME").thenReturn("LOGGING_MODE");
        when(stmtrs1.getString(2)).thenReturn(primaryKeyConstaintName).thenReturn(loggingEnabled ? "true" : "false");
        return stmt1;
    }

    /***
     * Sets up a mock result set for a prepared SQL statement used by ???
     */
    PreparedStatement mockPreparedStatement2(String str1, String str2) throws SQLException {
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn(str1);
        when(stmtrs2.getString(2)).thenReturn(str2);
        return stmt2;
    }

    /***
     * Sets up a mock result set for a prepared SQL statement used by ???
     */
    PreparedStatement mockPreparedStatement3(boolean bValue) throws SQLException {
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(bValue);
        return stmt3;
    }

    /***
     * Sets up a mock result set for a prepared SQL statement used by ???
     */
    PreparedStatement mockPreparedStatement4(boolean bValue) throws SQLException {
        PreparedStatement stmt4 = mock(PreparedStatement.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(bValue);
        return stmt4;
    }

    /***
     * Sets up a mock result set for a prepared SQL statement used by PostgreSqlDdlReader.getTriggers()
     */
    PreparedStatement mockPreparedStatementTriggerDetails(String triggerName, boolean enabled, String catalogName, String schemaName,
            String tableName, String triggerTypeParam, String sourceName) throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(7);
        when(rsMetaData.getColumnLabel(1)).thenReturn("trigger_name");
        when(rs.getObject(1)).thenReturn(triggerName);
        when(rsMetaData.getColumnLabel(2)).thenReturn("trigger_catalog");
        when(rs.getObject(2)).thenReturn(catalogName);
        when(rsMetaData.getColumnLabel(3)).thenReturn("trigger_schema");
        when(rs.getObject(3)).thenReturn(schemaName);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getObject(4)).thenReturn(tableName);
        when(rsMetaData.getColumnLabel(5)).thenReturn("STATUS");
        when(rs.getObject(5)).thenReturn(enabled ? "ACTIVE" : "DISABLED");
        when(rsMetaData.getColumnLabel(6)).thenReturn("prosrc");
        when(rs.getObject(6)).thenReturn(sourceName);
        when(rsMetaData.getColumnLabel(7)).thenReturn("trigger_type");
        when(rs.getObject(7)).thenReturn(triggerTypeParam);
        return ps;
    }

    /***
     * Sets up a mock result set for a prepared SQL statement used by PostgreSqlDdlReader.getTriggers()
     */
    PreparedStatement mockPreparedStatementTrigger2() throws SQLException {
        PreparedStatement ps2 = mock(PreparedStatement.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        when(ps2.executeQuery()).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(1);
        when(rsMetaData2.getColumnLabel(1)).thenReturn("TEXT");
        when(rs2.getObject(1)).thenReturn("testText");
        return ps2;
    }

    /**
     * Creates a stand-alone Table object with only one column and index
     */
    Table generateOneColumnTable(Column testColumn, IIndex testIndex) {
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        if (testColumn != null) {
            expectedTable.addColumn(testColumn);
        }
        if (testIndex != null) {
            expectedTable.addIndex(testIndex);
            if (testIndex instanceof UniqueIndex) {
                expectedTable.setPrimaryKeyConstraintName(testIndex.getName());
            }
        }
        return expectedTable;
    }

    /**
     * Creates a stand-alone Column object per with specified properties
     */
    Column generatePostgresColumn(String columnDef,
            String columnDefault,
            String jdbcTypeName,
            int jdbcTypeCode,
            String columnSize,
            String testColumnJdbcTypeName,
            int testColumnJdbcTypeCode,
            String testColumnMappedType,
            int platformColumnSize,
            String platformColumnType) {
        Column testColumn = new Column();
        testColumn.setDefaultValue(columnDef);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName(testColumnJdbcTypeName);
        testColumn.setSize(columnSize);
        testColumn.setAutoIncrement(false);
        testColumn.setJdbcTypeCode(testColumnJdbcTypeCode);
        testColumn.setMappedType(testColumnMappedType);
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        PlatformColumn platformColumn = new PlatformColumn();
        testColumn.addPlatformColumn(platformColumn);
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("postgres");
        platformColumn.setSize(platformColumnSize);
        platformColumn.setType(platformColumnType);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("postgres", platformColumn);
        return testColumn;
    }

    /**
     * Creates a stand-alone NonUniqueIndex object per with specified properties
     */
    NonUniqueIndex generateNonUniqueIndexOneColumn(String indexName, int columnOrdinalPosition) {
        IndexColumn testIndexColumn = new IndexColumn();
        testIndexColumn.setOrdinalPosition(columnOrdinalPosition);
        NonUniqueIndex testIndex = new NonUniqueIndex();
        testIndex.setName(indexName);
        testIndex.addColumn(testIndexColumn);
        return testIndex;
    }

    /**
     * Creates a stand-alone Trigger object per with specified properties
     */
    Trigger generatePostgresTrigger(String triggerName, boolean enabled, String schemaName, String tableName, String triggerTypeParam, String sourceName) {
        Row triggerMetaData = new Row(5);
        triggerMetaData.put("trigger_name", triggerName);
        triggerMetaData.put("trigger_schema", schemaName);
        triggerMetaData.put(DdlReaderTestConstants.TABLE_NAME, tableName);
        triggerMetaData.put("STATUS", "ACTIVE");
        triggerMetaData.put("TRIGGERING_EVENT", triggerTypeParam);
        Trigger trigger = new Trigger();
        trigger.setName(triggerName);
        trigger.setSchemaName(schemaName);
        trigger.setTableName(tableName);
        trigger.setEnabled(enabled);
        trigger.setSource(sourceName);
        trigger.setMetaData(triggerMetaData);
        String triggerType = triggerTypeParam;
        trigger.setTriggerType(TriggerType.valueOf(triggerType));
        return trigger;
    }

    @Test
    void testPostgreSqlDdlReaderConstructor() throws Exception {
        PostgreSqlDdlReader testReader = new PostgreSqlDdlReader(platform);
        testReader.setDefaultCatalogPattern(null);
        testReader.setDefaultSchemaPattern(null);
        testReader.setDefaultTablePattern("%");
        assertEquals(null, testReader.getDefaultCatalogPattern());
        assertEquals(null, testReader.getDefaultSchemaPattern());
        assertEquals("%", testReader.getDefaultTablePattern());
        assertEquals(true, postgresIsoDatePattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD'\\)"));
        assertEquals(true, postgresIsoTimePattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'HH24:MI:SS'\\)"));
        assertEquals(true, postgresIsoTimestampPattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD HH24:MI:SS'\\)"));
    }

    @ParameterizedTest
    @CsvSource({ "INSERT", "UPDATE", "DELETE", })
    void testGetTriggers(String triggerTypeParam) throws Exception {
        // Mocked components
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        Statement st = mock(Statement.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        Connection connection = mock(Connection.class);
        // "Real" components
        PostgreSqlDdlReader testReader = new PostgreSqlDdlReader(platform);
        List<Trigger> actualTriggers = new ArrayList<Trigger>();
        PostgreSqlJdbcSqlTemplate testTemplate = new PostgreSqlJdbcSqlTemplate(dataSource, settings, new SymmetricLobHandler(), databaseInfo);
        // Spied components
        PostgreSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        when(platform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement(spyTemplate.getSettings().getResultSetType(), ResultSet.CONCUR_READ_ONLY)).thenReturn(st);
        PreparedStatement mockPreparedStatement1 = mockPreparedStatementTriggerDetails("testTrigger", true, "testCatalog", "testSchema",
                "testTableName", triggerTypeParam, "create ");
        PreparedStatement mockPreparedStatement2 = mockPreparedStatementTrigger2();
        when(connection.prepareStatement(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(mockPreparedStatement1)
                .thenReturn(mockPreparedStatement2);
        Trigger expectedTrigger = generatePostgresTrigger("testTrigger", true, "testSchema", "testTableName", triggerTypeParam, "create ");
        actualTriggers.add(expectedTrigger);
        List<Trigger> triggers = testReader.getTriggers("test", "test", "test");
        Trigger testTrigger = triggers.get(0);
        assertEquals(expectedTrigger.getName(), testTrigger.getName());
        assertEquals(expectedTrigger.getSchemaName(), testTrigger.getSchemaName());
        assertEquals(expectedTrigger.getTableName(), testTrigger.getTableName());
        assertEquals(expectedTrigger.isEnabled(), testTrigger.isEnabled());
        assertEquals(expectedTrigger.getSource(), testTrigger.getSource());
        assertEquals(true, testTrigger.getTriggerType().toString().equals(triggerTypeParam));
    }

    @ParameterizedTest
    @CsvSource({ "2008-11-11, DATE, " + Types.DATE + ",," + -1 + "",
            "2008-11-11 12:10:30, TIMESTAMP, " + Types.TIMESTAMP + ",," + -1 + "",
            "testDef, VARCHAR, " + Types.VARCHAR + ",254," + 254 + "", })
    void testReadTableWithBasicArgs(String defaultValue, String jdbcTypeName, int jdbcTypeCode, String testColumnSize, int platformColumnSize)
            throws Exception {
        String primaryKeyConstaintName = DdlReaderTestConstants.TESTNAMECAPS + "_PK";
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        // "Real" Components
        TypedProperties properties = mock(TypedProperties.class);
        doReturn(properties).when(settings).getProperties();
        doReturn(true).when(properties).is("postgres.convert.infinity.date.to.null");
        PostgreSqlJdbcSqlTemplate testTemplate = new PostgreSqlJdbcSqlTemplate(dataSource, settings, new SymmetricLobHandler(), databaseInfo);
        PostgreSqlDatabasePlatform platform = new PostgreSqlDatabasePlatform(dataSource, settings);
        // Spied Components
        PostgreSqlDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        PostgreSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        PostgreSqlDdlReader testReader = new PostgreSqlDdlReader(spyPlatform);
        PostgreSqlDdlReader spyReader = Mockito.spy(testReader);
        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet rs5 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData5 = mock(ResultSetMetaData.class);
        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplate()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(7);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(defaultValue);// Variable 1
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn(jdbcTypeName);// Variable2
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(jdbcTypeCode);// Variable3
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);
        PreparedStatement mockPreparedStatementPk = mockPreparedStatement4TablePrimaryKeyConstraintAndLogging(true, primaryKeyConstaintName);
        PreparedStatement mockPreparedStatement2 = mockPreparedStatement2("NOTRIGHTNAME", "TESTSCHEMA");
        PreparedStatement mockPreparedStatement3 = mockPreparedStatement3(true);
        PreparedStatement mockPreparedStatement4 = mockPreparedStatement4(true);
        when(connection.prepareStatement(ArgumentMatchers.anyString()))
                .thenReturn(mockPreparedStatementPk).thenReturn(mockPreparedStatement2).thenReturn(mockPreparedStatement3)
                .thenReturn(mockPreparedStatement4);
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);
        when(metaData.getIndexInfo(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())).thenReturn(rs5);
        when(rs5.next()).thenReturn(true).thenReturn(false);
        when(rs5.getMetaData()).thenReturn(rsMetaData5);
        when(rsMetaData5.getColumnCount()).thenReturn(2);
        when(rsMetaData5.getColumnLabel(1)).thenReturn("INDEX_NAME");
        when(rsMetaData5.getColumnName(1)).thenReturn("INDEX_NAME");
        when(rs5.getString(1)).thenReturn("testIndexName");
        when(rsMetaData5.getColumnLabel(2)).thenReturn("TABLE_NAME");
        when(rsMetaData5.getColumnName(2)).thenReturn("TABLE_NAME");
        when(rs5.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        Table testTable = (Table) spyReader.readRelation(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(defaultValue);// Variable 1
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName(jdbcTypeName);// Variable 2
        testColumn.setSize(testColumnSize);
        testColumn.setAutoIncrement(false);
        testColumn.setJdbcTypeCode(jdbcTypeCode);// Variable 3
        testColumn.setMappedType(jdbcTypeName);// Variable 2
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(defaultValue);// Variable 1
        platformColumn.setName("postgres");
        platformColumn.setType(jdbcTypeName);// Variable 2
        platformColumn.setSize(platformColumnSize);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("postgres", platformColumn);
        IndexColumn testIndexColumn = new IndexColumn();
        testIndexColumn.setOrdinalPosition(0);
        NonUniqueIndex testIndex = new NonUniqueIndex();
        testIndex.setName("testIndexName");
        testIndex.addColumn(testIndexColumn);
        testColumn.addPlatformColumn(platformColumn);
        expectedTable.addColumn(testColumn);
        expectedTable.addIndex(testIndex);
        assertEquals(expectedTable, testTable);
    }

    @ParameterizedTest
    @CsvSource({ "'1001001','1001001',BINARY," + Types.BINARY + ",100,BINARY," + Types.BINARY + ",BINARY," + 63 + ",BINARY",
            "testDef,testDefault,TEXT," + Types.VARCHAR + ",2147483647,TEXT," + Types.VARCHAR + ",LONGVARCHAR," + 2147483647 + ",VARCHAR",
            "testDef,testDefault,BINARY," + Types.BINARY + ",2147483647,BINARY," + Types.BINARY + ",LONGVARBINARY," + 2147483647 + ",VARCHAR",
            "testDef,testDefault,OTHER," + Types.OTHER + ",63,LONGVARCHAR," + Types.LONGVARCHAR + ",LONGVARCHAR," + 63 + ",LONGVARCHAR" })
    void testReadTableWithAdvancedArgs(String columnDef,
            String columnDefault,
            String jdbcTypeName,
            int jdbcTypeCode,
            String columnSize,
            String testColumnJdbcTypeName,
            int testColumnJdbcTypeCode,
            String testColumnMappedType,
            int platformColumnSize,
            String platformColumnType) throws Exception {
        String primaryKeyConstaintName = DdlReaderTestConstants.TESTNAMECAPS + "_PK";
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        TypedProperties properties = mock(TypedProperties.class);
        doReturn(properties).when(settings).getProperties();
        doReturn(true).when(properties).is("postgres.convert.infinity.date.to.null");
        // "Real" Components
        PostgreSqlJdbcSqlTemplate testTemplate = new PostgreSqlJdbcSqlTemplate(dataSource, settings, new SymmetricLobHandler(), databaseInfo);
        PostgreSqlDatabasePlatform platform = new PostgreSqlDatabasePlatform(dataSource, settings);
        // Spied Components
        PostgreSqlDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        PostgreSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        PostgreSqlDdlReader testReader = new PostgreSqlDdlReader(spyPlatform);
        PostgreSqlDdlReader spyReader = Mockito.spy(testReader);
        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet rs5 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData5 = mock(ResultSetMetaData.class);
        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplate()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(8);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(columnDef);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(columnDefault);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn(jdbcTypeName);
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(jdbcTypeCode);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn(columnSize);
        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);
        PreparedStatement mockPreparedStatementPk = mockPreparedStatement4TablePrimaryKeyConstraintAndLogging(true, primaryKeyConstaintName);
        PreparedStatement mockPreparedStatement2 = mockPreparedStatement2("NOTRIGHTNAME", "TESTSCHEMA");
        PreparedStatement mockPreparedStatement3 = mockPreparedStatement3(true);
        PreparedStatement mockPreparedStatement4 = mockPreparedStatement4(true);
        when(connection.prepareStatement(ArgumentMatchers.anyString()))
                .thenReturn(mockPreparedStatementPk).thenReturn(mockPreparedStatement2).thenReturn(mockPreparedStatement3)
                .thenReturn(mockPreparedStatement4);
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);
        when(metaData.getIndexInfo(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())).thenReturn(rs5);
        when(rs5.next()).thenReturn(true).thenReturn(false);
        when(rs5.getMetaData()).thenReturn(rsMetaData5);
        when(rsMetaData5.getColumnCount()).thenReturn(2);
        when(rsMetaData5.getColumnLabel(1)).thenReturn("INDEX_NAME");
        when(rsMetaData5.getColumnName(1)).thenReturn("INDEX_NAME");
        when(rs5.getString(1)).thenReturn(primaryKeyConstaintName);
        when(rsMetaData5.getColumnLabel(2)).thenReturn("TABLE_NAME");
        when(rsMetaData5.getColumnName(2)).thenReturn("TABLE_NAME");
        when(rs5.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        // TODO: arg matching!//doReturn(1).when(spyTemplate).queryForInt(ArgumentMatchers.anyString(), ArgumentMatchers.any());
        Column expectedColumn = generatePostgresColumn(columnDef, columnDefault, jdbcTypeName, jdbcTypeCode, columnSize, testColumnJdbcTypeName,
                testColumnJdbcTypeCode,
                testColumnMappedType, platformColumnSize, platformColumnType);
        NonUniqueIndex testIndex = generateNonUniqueIndexOneColumn(primaryKeyConstaintName, 0);
        Table expectedTable = generateOneColumnTable(expectedColumn, testIndex);
        Table testTable = (Table) spyReader.readRelation(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        assertEquals(expectedTable, testTable);
        // assertEquals(expectedTable.getPrimaryKeyConstraintName(), testTable.getPrimaryKeyConstraintName());
    }

    @ParameterizedTest
    // 1010 is a code size for null size and null scale. If scale is set, it automatically sets the size to 0 instead of null, and for the test purposes
    // we need the size to be null.
    // using 126:288 because it is supposed to set the size to 126,288 but the comma breaks the csv data.
    @CsvSource({ "1.1234321,1.1234321,DECIMAL," + Types.DECIMAL + ",0," + 288 + ",DECIMAL," + Types.DECIMAL + "," + Types.DECIMAL + ",1010," + 0 + ",DECIMAL,"
            + 2147483647 + ",DECIMAL",
            "1.1234321,1.1234321,DECIMAL," + Types.DECIMAL + ",126," + 288 + ",DECIMAL," + Types.DECIMAL + "," + Types.DECIMAL + ",126:288," + 288 + ",DECIMAL,"
                    + 63 + ",DECIMAL",
            "1.1234321,1.1234321,DECIMAL," + Types.DECIMAL + ",126," + 10 + ",DECIMAL," + Types.DECIMAL + "," + Types.DECIMAL + ",126:10," + 10 + ",DECIMAL,"
                    + 126 + ",DECIMAL" })
    void testReadTableWithDecimalArgs(String columnDef,
            String columnDefault,
            String jdbcTypeName,
            int jdbcTypeCode,
            String columnSize,
            int decimalDigits,
            String testColumnJdbcTypeName,
            int testColumnJdbcTypeCode,
            int testColumnMappedTypeCode,
            String testColumnSize,
            int testColumnScale,
            String testColumnMappedTypeName,
            int platformColumnSize,
            String platformColumnType) throws Exception {
        String primaryKeyConstaintName = DdlReaderTestConstants.TESTNAMECAPS + "_PK";
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        // "Real" Components
        TypedProperties properties = mock(TypedProperties.class);
        doReturn(properties).when(settings).getProperties();
        doReturn(true).when(properties).is("postgres.convert.infinity.date.to.null");
        PostgreSqlJdbcSqlTemplate testTemplate = new PostgreSqlJdbcSqlTemplate(dataSource, settings, new SymmetricLobHandler(), databaseInfo);
        PostgreSqlDatabasePlatform platform = new PostgreSqlDatabasePlatform(dataSource, settings);
        // Spied Components
        PostgreSqlDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        PostgreSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        PostgreSqlDdlReader testReader = new PostgreSqlDdlReader(spyPlatform);
        PostgreSqlDdlReader spyReader = Mockito.spy(testReader);
        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet rs5 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData5 = mock(ResultSetMetaData.class);
        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplate()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(9);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(columnDef);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(columnDefault);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn(jdbcTypeName);
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(jdbcTypeCode);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn(columnSize);
        when(rsMetaData2.getColumnLabel(9)).thenReturn("DECIMAL_DIGITS");
        when(rsMetaData2.getColumnName(9)).thenReturn("DECIMAL_DIGITS");
        when(rs2.getInt(9)).thenReturn(decimalDigits);
        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);
        PreparedStatement mockPreparedStatementPk = mockPreparedStatement4TablePrimaryKeyConstraintAndLogging(true, primaryKeyConstaintName);
        PreparedStatement mockPreparedStatement2 = mockPreparedStatement2("NOTRIGHTNAME", "TESTSCHEMA");
        PreparedStatement mockPreparedStatement3 = mockPreparedStatement3(true);
        PreparedStatement mockPreparedStatement4 = mockPreparedStatement4(true);
        when(connection.prepareStatement(ArgumentMatchers.anyString()))
                .thenReturn(mockPreparedStatementPk).thenReturn(mockPreparedStatement2).thenReturn(mockPreparedStatement3)
                .thenReturn(mockPreparedStatement4);
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);
        when(metaData.getIndexInfo(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())).thenReturn(rs5);
        when(rs5.next()).thenReturn(true).thenReturn(false);
        when(rs5.getMetaData()).thenReturn(rsMetaData5);
        when(rsMetaData5.getColumnCount()).thenReturn(2);
        when(rsMetaData5.getColumnLabel(1)).thenReturn("INDEX_NAME");
        when(rsMetaData5.getColumnName(1)).thenReturn("INDEX_NAME");
        when(rs5.getString(1)).thenReturn("testIndexName");
        when(rsMetaData5.getColumnLabel(2)).thenReturn("TABLE_NAME");
        when(rsMetaData5.getColumnName(2)).thenReturn("TABLE_NAME");
        when(rs5.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        // TODO: Arg matching! //doReturn(1).when(spyTemplate).queryForInt(ArgumentMatchers.anyString(), ArgumentMatchers.any());
        Table testTable = (Table) spyReader.readRelation(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(columnDef);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName(jdbcTypeName);
        if (testColumnSize.equals("1010")) {
            testColumn.setSize(null);
        } else if (testColumnSize.contains(":")) {
            String correctColumnSize = testColumnSize.replace(":", ",");
            testColumn.setSize(correctColumnSize);
            testColumn.setScale(testColumnScale);
        } else {
            testColumn.setSize(testColumnSize);
            testColumn.setScale(testColumnScale);
        }
        testColumn.setAutoIncrement(false);
        testColumn.setJdbcTypeCode(testColumnJdbcTypeCode);
        testColumn.setMappedTypeCode(testColumnMappedTypeCode);
        testColumn.setMappedType(testColumnMappedTypeName);
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(columnDef);
        platformColumn.setName("postgres");
        platformColumn.setSize(-1);
        platformColumn.setType(platformColumnType);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("postgres", platformColumn);
        IndexColumn testIndexColumn = new IndexColumn();
        testIndexColumn.setOrdinalPosition(0);
        NonUniqueIndex testIndex = new NonUniqueIndex();
        testIndex.setName("testIndexName");
        testIndex.addColumn(testIndexColumn);
        testColumn.addPlatformColumn(platformColumn);
        expectedTable.addColumn(testColumn);
        expectedTable.addIndex(testIndex);
        assertEquals(testColumn, testTable.getColumn(0));
        assertEquals(expectedTable, testTable);
    }

    @Test
    void testReadTableWithBitType() throws Exception {
        String primaryKeyConstaintName = DdlReaderTestConstants.TESTNAMECAPS + "_PK";
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        // "Real" Components
        TypedProperties properties = mock(TypedProperties.class);
        doReturn(properties).when(settings).getProperties();
        doReturn(true).when(properties).is("postgres.convert.infinity.date.to.null");
        PostgreSqlJdbcSqlTemplate testTemplate = new PostgreSqlJdbcSqlTemplate(dataSource, settings, new SymmetricLobHandler(), databaseInfo);
        PostgreSqlDatabasePlatform platform = new PostgreSqlDatabasePlatform(dataSource, settings);
        // Spied Components
        PostgreSqlDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        PostgreSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        PostgreSqlDdlReader testReader = new PostgreSqlDdlReader(spyPlatform);
        PostgreSqlDdlReader spyReader = Mockito.spy(testReader);
        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet rs5 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData5 = mock(ResultSetMetaData.class);
        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplate()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(8);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("BIT");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.BIT);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn("63");
        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);
        PreparedStatement mockPreparedStatementPk = mockPreparedStatement4TablePrimaryKeyConstraintAndLogging(true, primaryKeyConstaintName);
        PreparedStatement mockPreparedStatement2 = mockPreparedStatement2("NOTRIGHTNAME", "TESTSCHEMA");
        PreparedStatement mockPreparedStatement3 = mockPreparedStatement3(true);
        PreparedStatement mockPreparedStatement4 = mockPreparedStatement4(true);
        when(connection.prepareStatement(ArgumentMatchers.anyString()))
                .thenReturn(mockPreparedStatementPk).thenReturn(mockPreparedStatement2).thenReturn(mockPreparedStatement3)
                .thenReturn(mockPreparedStatement4);
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);
        when(metaData.getIndexInfo(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())).thenReturn(rs5);
        when(rs5.next()).thenReturn(true).thenReturn(false);
        when(rs5.getMetaData()).thenReturn(rsMetaData5);
        when(rsMetaData5.getColumnCount()).thenReturn(2);
        when(rsMetaData5.getColumnLabel(1)).thenReturn("INDEX_NAME");
        when(rsMetaData5.getColumnName(1)).thenReturn("INDEX_NAME");
        when(rs5.getString(1)).thenReturn("testIndexName");
        when(rsMetaData5.getColumnLabel(2)).thenReturn("TABLE_NAME");
        when(rsMetaData5.getColumnName(2)).thenReturn("TABLE_NAME");
        when(rs5.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        Table testTable = (Table) spyReader.readRelation(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("VARCHAR");
        testColumn.setSize("63");
        testColumn.setAutoIncrement(false);
        testColumn.setJdbcTypeCode(Types.VARCHAR);
        testColumn.setMappedType("VARCHAR");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("postgres");
        platformColumn.setSize(63);
        platformColumn.setType("VARCHAR");
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("postgres", platformColumn);
        IndexColumn testIndexColumn = new IndexColumn();
        testIndexColumn.setOrdinalPosition(0);
        NonUniqueIndex testIndex = new NonUniqueIndex();
        testIndex.setName("testIndexName");
        testIndex.addColumn(testIndexColumn);
        testColumn.addPlatformColumn(platformColumn);
        expectedTable.addColumn(testColumn);
        expectedTable.addIndex(testIndex);
        assertEquals(expectedTable, testTable);
    }

    // haven't be parameterized because size and scale have to be set
    @Test
    void testReadTableWithVarcharAndSizeToGetDecimal() throws Exception {
        String primaryKeyConstaintName = DdlReaderTestConstants.TESTNAMECAPS + "_PK";
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        // "Real" Components
        TypedProperties properties = mock(TypedProperties.class);
        doReturn(properties).when(settings).getProperties();
        doReturn(true).when(properties).is("postgres.convert.infinity.date.to.null");
        PostgreSqlJdbcSqlTemplate testTemplate = new PostgreSqlJdbcSqlTemplate(dataSource, settings, new SymmetricLobHandler(), databaseInfo);
        PostgreSqlDatabasePlatform platform = new PostgreSqlDatabasePlatform(dataSource, settings);
        // Spied Components
        PostgreSqlDatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        PostgreSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        PostgreSqlDdlReader testReader = new PostgreSqlDdlReader(spyPlatform);
        PostgreSqlDdlReader spyReader = Mockito.spy(testReader);
        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet rs5 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData5 = mock(ResultSetMetaData.class);
        when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        when(spyPlatform.createSqlTemplate()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                (String[]) ArgumentMatchers.any())).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(5);
        when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(8);
        when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        when(rs2.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        when(rs2.getString(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        when(rs2.getString(5)).thenReturn("NUMERIC");
        when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        when(rs2.getInt(6)).thenReturn(Types.NUMERIC);
        when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        when(rs2.getString(7)).thenReturn("TRUE");
        when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        when(rs2.getString(8)).thenReturn("131089");
        when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs3);
        when(rs3.next()).thenReturn(false);
        when(rs3.getMetaData()).thenReturn(rsMetaData3);
        when(rsMetaData3.getColumnCount()).thenReturn(0);
        PreparedStatement mockPreparedStatementPk = mockPreparedStatement4TablePrimaryKeyConstraintAndLogging(true, primaryKeyConstaintName);
        PreparedStatement mockPreparedStatement2 = mockPreparedStatement2("NOTRIGHTNAME", "TESTSCHEMA");
        PreparedStatement mockPreparedStatement3 = mockPreparedStatement3(true);
        PreparedStatement mockPreparedStatement4 = mockPreparedStatement4(true);
        when(connection.prepareStatement(ArgumentMatchers.anyString()))
                .thenReturn(mockPreparedStatementPk).thenReturn(mockPreparedStatement2).thenReturn(mockPreparedStatement3)
                .thenReturn(mockPreparedStatement4);
        when(metaData.getPrimaryKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(rs4);
        when(rs4.getMetaData()).thenReturn(rsMetaData4);
        when(rsMetaData4.getColumnCount()).thenReturn(3);
        when(rsMetaData4.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rsMetaData4.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        when(rs4.getString(1)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rsMetaData4.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rsMetaData4.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs4.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(rsMetaData4.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rsMetaData4.getColumnName(3)).thenReturn(DdlReaderTestConstants.PK_NAME);
        when(rs4.getString(3)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        when(rs4.next()).thenReturn(true).thenReturn(false);
        when(metaData.getIndexInfo(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())).thenReturn(rs5);
        when(rs5.next()).thenReturn(true).thenReturn(false);
        when(rs5.getMetaData()).thenReturn(rsMetaData5);
        when(rsMetaData5.getColumnCount()).thenReturn(2);
        when(rsMetaData5.getColumnLabel(1)).thenReturn("INDEX_NAME");
        when(rsMetaData5.getColumnName(1)).thenReturn("INDEX_NAME");
        when(rs5.getString(1)).thenReturn("testIndexName");
        when(rsMetaData5.getColumnLabel(2)).thenReturn("TABLE_NAME");
        when(rsMetaData5.getColumnName(2)).thenReturn("TABLE_NAME");
        when(rs5.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        Table testTable = (Table) spyReader.readRelation(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
        Table expectedTable = new Table();
        expectedTable.setName(DdlReaderTestConstants.TESTNAME);
        expectedTable.setType(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        expectedTable.setCatalog(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        expectedTable.setSchema(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        expectedTable.setDescription(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        Column testColumn = new Column();
        testColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        testColumn.setJdbcTypeName("NUMERIC");
        testColumn.setSizeAndScale(0, 0);
        testColumn.setAutoIncrement(false);
        testColumn.setJdbcTypeCode(Types.NUMERIC);
        testColumn.setMappedType("DECIMAL");
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("postgres");
        platformColumn.setSize(2147483647);
        platformColumn.setType("VARCHAR");
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("postgres", platformColumn);
        IndexColumn testIndexColumn = new IndexColumn();
        testIndexColumn.setOrdinalPosition(0);
        NonUniqueIndex testIndex = new NonUniqueIndex();
        testIndex.setName("testIndexName");
        testIndex.addColumn(testIndexColumn);
        testColumn.addPlatformColumn(platformColumn);
        expectedTable.addColumn(testColumn);
        expectedTable.addIndex(testIndex);
        assertEquals(expectedTable, testTable);
    }

    @Test
    void testRemoveExistingIndexColumns() {
        IIndex index = new UniqueIndex("testIndex");
        IndexColumn indexColumn1 = new IndexColumn("indexColumn1");
        IndexColumn indexColumn2 = new IndexColumn("indexColumn2");
        IndexColumn includedIndexColumn = new IndexColumn("includedIndexColumn");
        index.addColumn(indexColumn1);
        index.addColumn(indexColumn2);
        index.addColumn(includedIndexColumn);
        List<IndexColumn> indexColumnList = List.of(indexColumn1, indexColumn2);
        List<IndexColumn> includedIndexColumnList = List.of(includedIndexColumn);
        PostgreSqlDdlReader ddlReader = new PostgreSqlDdlReader(platform);
        assertEquals(indexColumnList.size() + includedIndexColumnList.size(), index.getColumnCount());
        assertEquals(0, index.getIncludedColumnCount());
        ddlReader.removeExistingIndexColumns(index, indexColumnList, includedIndexColumnList);
        assertEquals(indexColumnList.size(), index.getColumnCount());
    }

    @Test
    void testGetIncludeIndexColumnsFromSql() {
        int numOfIndexColumns = 1;
        String[] allIndexColumns = { "2", "1", "3" };
        String[] columnNames = { "id", "createdBy", "createdOn" };
        PostgreSqlDdlReader ddlReader = new PostgreSqlDdlReader(platform);
        List<IndexColumn> result = ddlReader.getIncludeIndexColumnsFromSql(numOfIndexColumns, allIndexColumns, columnNames);
        assertEquals(allIndexColumns.length - numOfIndexColumns, result.size());
        assertEquals(columnNames[0], result.get(0).getName());
        assertEquals(columnNames[2], result.get(1).getName());
        List<IndexColumn> result2 = ddlReader.getIncludeIndexColumnsFromSql(0, new String[] {}, new String[] {});
        assertTrue(result2.isEmpty());
    }

    @Test
    void testGetIndexColumnsFromSql() {
        int numOfIndexColumns = 2;
        String[] allIndexColumns = { "2", "1", "3" };
        String[] columnNames = { "id", "createdBy", "createdOn" };
        PostgreSqlDdlReader ddlReader = new PostgreSqlDdlReader(platform);
        List<IndexColumn> result = ddlReader.getIndexColumnsFromSql(numOfIndexColumns, allIndexColumns, columnNames);
        assertEquals(numOfIndexColumns, result.size());
        assertEquals(columnNames[1], result.get(0).getName());
        assertEquals(columnNames[0], result.get(1).getName());
        List<IndexColumn> result2 = ddlReader.getIndexColumnsFromSql(0, new String[] {}, new String[] {});
        assertTrue(result2.isEmpty());
    }

    @Test
    void testFindIndexColumn() {
        IndexColumn indexColumn1 = new IndexColumn("indexColumn1");
        IndexColumn indexColumn2 = new IndexColumn("indexColumn2");
        IndexColumn indexColumn3 = new IndexColumn("indexColumn3");
        IndexColumn[] indexColumns = new IndexColumn[] { indexColumn1, indexColumn2, indexColumn3 };
        PostgreSqlDdlReader ddlReader = new PostgreSqlDdlReader(platform);
        IndexColumn result = ddlReader.findIndexColumn("indexColumn2", indexColumns);
        assertEquals(indexColumn2, result);
        assertNull(ddlReader.findIndexColumn("dne", indexColumns));
        assertNull(ddlReader.findIndexColumn("indexColumn1", new IndexColumn[] {}));
    }

    @Test
    void testFindIndex() {
        IIndex index1 = new UniqueIndex("index1");
        IIndex index2 = new NonUniqueIndex("index2");
        IIndex index3 = new UniqueIndex("index3");
        List<IIndex> indices = Arrays.asList(index1, index2, index3);
        PostgreSqlDdlReader ddlReader = new PostgreSqlDdlReader(platform);
        IIndex result = ddlReader.findIndex("index2", indices);
        assertEquals(index2, result);
        assertNull(ddlReader.findIndex("dne", indices));
        assertNull(ddlReader.findIndex("index2", Collections.emptyList()));
    }

    protected String getResultSetSchemaName() {
        return DdlReaderTestConstants.TABLE_SCHEM;
    }

    public IDatabasePlatform getPlatform() {
        return platform;
    }
}