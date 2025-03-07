/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.db.platform.mssql;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.jumpmind.db.DdlReaderTestConstants;
import org.jumpmind.db.mock.MockDbDataSource;
import org.jumpmind.db.mock.MockDbUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.CompressionTypes;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.PlatformIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Tests MsSqlDdlReader (universal class) in conjunction with the MsSql2008DatabasePlatform
 */
@DisplayName("MsSqlDdlReader_MsSql2008DatabasePlatform")
class MsSql2008DdlReaderTest {
    protected final String SAMPLE_CATALOG_NAME = "testCatlog";
    protected final String SAMPLE_SCHEMA_NAME = "testSchema";
    protected final String SAMPLE_TRIGGER_NAME = "testTrigger";
    protected final String SAMPLE_TABLE_NAME = "testTableName";
    protected final String SAMPLE_INDEX_NAME = "testIndexName";
    public final int MsSqlDatabasePlatform_VERSION10 = 10; // SQL Server 2008
//    protected MsSql2008DatabasePlatform platform;
    protected IDatabasePlatform platform;
    protected Pattern mssql2008IsoDatePattern;
    /*
     * The regular expression pattern for the mssql2008 conversion of ISO times.
     */
    protected Pattern mssql2008IsoTimePattern;
    /*
     * The regular expression pattern for the mssql2008 conversion of ISO timestamps.
     */
    protected Pattern mssql2008IsoTimestampPattern;
    ISqlTemplate sqlTemplate;
    ISqlTransaction sqlTransaction;
    protected AbstractJdbcDdlReader abstractJdbcDdlReader;
    ThreadLocalRandom rand = ThreadLocalRandom.current();
    // Anticipated queries to mock responses for:
    public final String MSSQL_USER_DEFIND_TYPES_QUERY = "select name from sys.types where is_user_defined = 1";
    public final String MSSQL_INFORMATION_SCHEMA_TABLES_QUERY = "select \"TABLE_NAME\" from \"testCatlog\".\"INFORMATION_SCHEMA\".\"TABLES\" where";
    public final String MSSQL_INFORMATION_SCHEMA_TRIGGER_QUERY = "select TRIG.name, TAB.name as table_name, SC.name as table_schema, TRIG.is_disabled, TRIG.is_ms_shipped, TRIG.is_not_for_replication, TRIG.is_instead_of_trigger, TRIG.create_date, TRIG.modify_date, OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsUpdateTrigger') AS isupdate, OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsDeleteTrigger') AS isdelete, OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsInsertTrigger') AS isinsert, OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsAfterTrigger') AS isafter, OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsInsteadOfTrigger') AS isinsteadof, TRIG.object_id, TRIG.parent_id, TAB.schema_id, OBJECT_DEFINITION(TRIG.OBJECT_ID) as trigger_source from sys.triggers as TRIG inner join sys.tables as TAB on TRIG.parent_id = TAB.object_id inner join sys.schemas as SC on TAB.schema_id = SC.schema_id where TAB.name=? and SC.name=?";

    @BeforeEach
    public void setUp() throws Exception {
        platform = mock(MsSql2008DatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        mssql2008IsoDatePattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD'\\)");
        mssql2008IsoTimePattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'HH24:MI:SS'\\)");
        mssql2008IsoTimestampPattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD HH24:MI:SS'\\)");
    }

    /**
     * Helper builds new instance of the MsSqlDdlReader and mocks results for anticipated query listing user-defined types
     */
    private MsSqlDdlReader createMsSqlDdlReader(MockDbDataSource mockDataSource) {
        mockDataSource.enqueue(MockDbUtils.buildStatementNoResults(
                MSSQL_USER_DEFIND_TYPES_QUERY, 1));
        MsSql2008DatabasePlatform platform2008 = new MsSql2008DatabasePlatform(mockDataSource, mockDataSource.getSqlTemplateSettings());
        mockDataSource.enqueue(MockDbUtils.buildStatementNoResults(
                MSSQL_USER_DEFIND_TYPES_QUERY, 1));
        MsSqlDdlReader testReader = new MsSqlDdlReader(platform2008);
        return testReader;
    }

    @Test
    @DisplayName("Constructed")
    void testConstructor() throws Exception {
        MockDbDataSource mockDataSource = new MockDbDataSource(MsSqlDatabasePlatform_VERSION10);
        MsSqlDdlReader testReader = createMsSqlDdlReader(mockDataSource);
        testReader.setDefaultCatalogPattern(null);
        testReader.setDefaultSchemaPattern(null);
        testReader.setDefaultTablePattern("%");
        assertEquals(null, testReader.getDefaultCatalogPattern());
        assertEquals(null, testReader.getDefaultSchemaPattern());
        assertEquals("%", testReader.getDefaultTablePattern());
        assertEquals(true, mssql2008IsoDatePattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD'\\)"));
        assertEquals(true, mssql2008IsoTimePattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'HH24:MI:SS'\\)"));
        assertEquals(true, mssql2008IsoTimestampPattern.pattern().equals("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD HH24:MI:SS'\\)"));
    }

    @Test
    @DisplayName("getTableNames Single")
    void getTableNames() throws Exception {
        MockDbDataSource mockDataSource = new MockDbDataSource(MsSqlDatabasePlatform_VERSION10);
        mockDataSource.mockAndEnqueueTableLookup1Results(SAMPLE_TABLE_NAME, MSSQL_INFORMATION_SCHEMA_TABLES_QUERY);
        MsSqlDdlReader testReader = createMsSqlDdlReader(mockDataSource);
        List<String> tableNameResults = new ArrayList<String>();
        List<String> tableNames = new ArrayList<String>();
        tableNames.add(SAMPLE_TABLE_NAME);
        tableNameResults = testReader.getTableNames(SAMPLE_CATALOG_NAME, SAMPLE_SCHEMA_NAME, null);
        assertEquals(tableNames, tableNameResults);
    }
    

    @ParameterizedTest
    @Disabled
    @CsvSource({ "INSERT,1,0,0", "UPDATE,0,1,0", "DELETE,0,0,1", })
    void testGetTriggers(String triggerTypeParam, String isInsert, String isUpdate, String isDelete) throws Exception {
        // Mocked components
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        Statement st = mock(Statement.class);
        PreparedStatement ps2 = mock(PreparedStatement.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        Connection connection = mock(Connection.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        // "Real" components
        MsSqlDdlReader testReader = new MsSqlDdlReader(platform);
        List<Trigger> actualTriggers = new ArrayList<Trigger>();
        MsSqlJdbcSqlTemplate testTemplate = new MsSqlJdbcSqlTemplate(dataSource, settings, databaseInfo);
        // Spied components
        MsSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        when(spyTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement(spyTemplate.getSettings().getResultSetType(), ResultSet.CONCUR_READ_ONLY)).thenReturn(st);
        when(connection.prepareStatement(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(ps)
                .thenReturn(ps2);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(10);
        when(rsMetaData.getColumnLabel(1)).thenReturn("name");
        when(rs.getObject(1)).thenReturn("testTrigger");
        when(rsMetaData.getColumnLabel(2)).thenReturn("table_schema");
        when(rs.getObject(2)).thenReturn("testSchema");
        when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        when(rs.getObject(3)).thenReturn("testTableName");
        when(rsMetaData.getColumnLabel(4)).thenReturn("is_disabled");
        when(rs.getObject(4)).thenReturn("FALSE");
        when(rsMetaData.getColumnLabel(5)).thenReturn("trigger_source");
        when(rs.getObject(5)).thenReturn("create ");
        when(rsMetaData.getColumnLabel(6)).thenReturn("isupdate");
        when(rs.getObject(6)).thenReturn(isUpdate);
        when(rsMetaData.getColumnLabel(7)).thenReturn("isdelete");
        when(rs.getObject(7)).thenReturn(isDelete);
        when(rsMetaData.getColumnLabel(8)).thenReturn("isinsert");
        when(rs.getObject(8)).thenReturn(isInsert);
        when(rsMetaData.getColumnLabel(9)).thenReturn("isafter");
        when(rs.getObject(9)).thenReturn("0");
        when(rsMetaData.getColumnLabel(10)).thenReturn("isinsteadof");
        when(rs.getObject(10)).thenReturn("0");
        when(ps2.executeQuery()).thenReturn(rs2);
        when(rs2.next()).thenReturn(true).thenReturn(false);
        when(rs2.getMetaData()).thenReturn(rsMetaData2);
        when(rsMetaData2.getColumnCount()).thenReturn(1);
        when(rsMetaData2.getColumnLabel(1)).thenReturn("TEXT");
        when(rs2.getObject(1)).thenReturn("testText");
        Row triggerMetaData = new Row(5);
        triggerMetaData.put("trigger_name", "testTrigger");
        triggerMetaData.put("trigger_schema", "testSchema");
        triggerMetaData.put(DdlReaderTestConstants.TABLE_NAME, "testTableName");
        triggerMetaData.put("STATUS", "ACTIVE");
        triggerMetaData.put("TRIGGERING_EVENT", triggerTypeParam);
        Trigger trigger = new Trigger();
        trigger.setName("testTrigger");
        trigger.setSchemaName("testSchema");
        trigger.setTableName("testTableName");
        trigger.setEnabled(false);
        trigger.setSource("create ");
        trigger.setMetaData(triggerMetaData);
        String triggerType = triggerTypeParam;
        trigger.setTriggerType(TriggerType.valueOf(triggerType));
        actualTriggers.add(trigger);
        List<Trigger> triggers = testReader.getTriggers("test", "test", "test");
        Trigger testTrigger = triggers.get(0);
        assertEquals("testTrigger", testTrigger.getName());
        assertEquals("testSchema", testTrigger.getSchemaName());
        assertEquals("testTableName", testTrigger.getTableName());
        assertEquals(true, testTrigger.isEnabled());
        assertEquals("create ", testTrigger.getSource());
        assertEquals(true, testTrigger.getTriggerType().toString().equals(triggerTypeParam));
    }

    @ParameterizedTest
    @Disabled
    @CsvSource({ "2008-11-11, DATE, " + Types.DATE + ",," + -1 + "", "2008-11-11 12:10:30, TIMESTAMP, " + Types.TIMESTAMP + ",," + -1 + "",
            "testDef, VARCHAR, " + Types.VARCHAR + ",254," + 254 + "", })
    void testReadTableWithBasicArgs(String defaultValue, String jdbcTypeName, int jdbcTypeCode, String testColumnSize, int platformColumnSize)
            throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        // "Real" Components
        MsSqlJdbcSqlTemplate testTemplate = new MsSqlJdbcSqlTemplate(dataSource, settings, databaseInfo);
        MsSql2008DatabasePlatform platform = new MsSql2008DatabasePlatform(dataSource, settings);
        // Spied Components
        MsSql2008DatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        MsSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        MsSqlDdlReader testReader = new MsSqlDdlReader(spyPlatform);
        MsSqlDdlReader spyReader = Mockito.spy(testReader);
        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet rs5 = mock(ResultSet.class);
        ResultSet rsForCompression = mock(ResultSet.class);
        List<Row> resultsForCompression = new ArrayList<Row>();
        Row compressionRow = new Row(7);
        compressionRow.put("TABLENAME", "testName");
        compressionRow.put("INDEXNAME", "testIndexFilter");
        compressionRow.put("IndexType", "testIndex");
        compressionRow.put("FILTER", true);
        compressionRow.put("HASFILTER", 1);
        compressionRow.put("COMPRESSIONTYPE", 1);
        compressionRow.put("COMPRESSIONDESCRIPTION", "testing");
        resultsForCompression.add(compressionRow);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData5 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);
        PreparedStatement stmtForCompression = mock(PreparedStatement.class);
        ResultSetMetaData stmt1RsMetaData = mock(ResultSetMetaData.class);
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
        // when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
        // .thenReturn(stmt4);
        // THIS SECTION IS NEW. This is because the msSqlDdlReader uses the
        // determineAutoIncrementFromResultSetMetaData
        // method, and this changes several things when testing
        when(connection.createStatement()).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3).thenReturn(stmt4);
        when(connection.prepareStatement(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                .thenReturn(stmtForCompression);
        when(connection.createStatement(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(stmtForCompression);
        when(stmtForCompression.executeQuery(ArgumentMatchers.anyString())).thenReturn(rsForCompression);
        doReturn(resultsForCompression).when(spyTemplate).query(ArgumentMatchers.anyString(), ArgumentMatchers.any());
        // when(spyTemplate.query(ArgumentMatchers.anyString(), ArgumentMatchers.any())).thenReturn(resultsForCompression);
        when(stmt1.executeQuery(ArgumentMatchers.anyString())).thenReturn(stmtrs1);
        when(stmtrs1.getMetaData()).thenReturn(stmt1RsMetaData);
        when(stmt1RsMetaData.isAutoIncrement(ArgumentMatchers.anyInt())).thenReturn(true);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);
        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
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
        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);
        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);
        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
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
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(jdbcTypeCode);// Variable 3
        testColumn.setMappedType(jdbcTypeName);// Variable 2
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(defaultValue);// Variable 1
        platformColumn.setName("mssql2008");
        platformColumn.setType(jdbcTypeName);// Variable 2
        platformColumn.setSize(platformColumnSize);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("mssql2008", platformColumn);
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
    @Disabled
    @CsvSource({ "testDef, VARCHAR, " + Types.VARCHAR + ",254," + 254 + ",0",
            "testDef, VARCHAR, " + Types.VARCHAR + ",254," + 254 + ",1",
            "testDef, VARCHAR, " + Types.VARCHAR + ",254," + 254 + ",2",
            "testDef, VARCHAR, " + Types.VARCHAR + ",254," + 254 + ",3",
            "testDef, VARCHAR, " + Types.VARCHAR + ",254," + 254 + ",4",
            "testDef, VARCHAR, " + Types.VARCHAR + ",254," + 254 + ",5", })
    void testReadTableWithDifferentCompressionTypes(String defaultValue, String jdbcTypeName, int jdbcTypeCode, String testColumnSize, int platformColumnSize,
            int compressionType)
            throws Exception {
        // Mocked Components
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        // "Real" Components
        MsSqlJdbcSqlTemplate testTemplate = new MsSqlJdbcSqlTemplate(dataSource, settings, databaseInfo);
        MsSql2008DatabasePlatform platform = new MsSql2008DatabasePlatform(dataSource, settings);
        // Spied Components
        MsSql2008DatabasePlatform spyPlatform = Mockito.spy(platform);
        testTemplate.setIsolationLevel(1);
        MsSqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        spyTemplate.setIsolationLevel(1);
        MsSqlDdlReader testReader = new MsSqlDdlReader(spyPlatform);
        MsSqlDdlReader spyReader = Mockito.spy(testReader);
        // Result Set Mocks
        ResultSet rs = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        ResultSet rs3 = mock(ResultSet.class);
        ResultSet rs4 = mock(ResultSet.class);
        ResultSet rs5 = mock(ResultSet.class);
        ResultSet rsForCompression = mock(ResultSet.class);
        List<Row> resultsForCompression = new ArrayList<Row>();
        Row compressionRow = new Row(7);
        compressionRow.put("TABLENAME", "testName");
        compressionRow.put("INDEXNAME", "testIndexFilter");
        compressionRow.put("IndexType", "testIndex");
        compressionRow.put("FILTER", true);
        compressionRow.put("HASFILTER", 1);
        compressionRow.put("COMPRESSIONTYPE", compressionType);
        compressionRow.put("COMPRESSIONDESCRIPTION", "testing");
        resultsForCompression.add(compressionRow);
        ResultSet stmtrs1 = mock(ResultSet.class);
        ResultSet stmtrs2 = mock(ResultSet.class);
        ResultSet stmtrs3 = mock(ResultSet.class);
        ResultSet stmtrs4 = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData4 = mock(ResultSetMetaData.class);
        ResultSetMetaData rsMetaData5 = mock(ResultSetMetaData.class);
        PreparedStatement stmt1 = mock(PreparedStatement.class);
        PreparedStatement stmt2 = mock(PreparedStatement.class);
        PreparedStatement stmt3 = mock(PreparedStatement.class);
        PreparedStatement stmt4 = mock(PreparedStatement.class);
        PreparedStatement stmtForCompression = mock(PreparedStatement.class);
        ResultSetMetaData stmt1RsMetaData = mock(ResultSetMetaData.class);
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
        // when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3)
        // .thenReturn(stmt4);
        // THIS SECTION IS NEW. This is because the msSqlDdlReader uses the
        // determineAutoIncrementFromResultSetMetaData
        // method, and this changes several things when testing
        when(connection.createStatement()).thenReturn(stmt1).thenReturn(stmt2).thenReturn(stmt3).thenReturn(stmt4);
        when(connection.prepareStatement(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                .thenReturn(stmtForCompression);
        when(connection.createStatement(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(stmtForCompression);
        when(stmtForCompression.executeQuery(ArgumentMatchers.anyString())).thenReturn(rsForCompression);
        doReturn(resultsForCompression).when(spyTemplate).query(ArgumentMatchers.anyString(), ArgumentMatchers.any());
        // when(spyTemplate.query(ArgumentMatchers.anyString(), ArgumentMatchers.any())).thenReturn(resultsForCompression);
        when(stmt1.executeQuery(ArgumentMatchers.anyString())).thenReturn(stmtrs1);
        when(stmtrs1.getMetaData()).thenReturn(stmt1RsMetaData);
        when(stmt1RsMetaData.isAutoIncrement(ArgumentMatchers.anyInt())).thenReturn(true);
        when(stmtrs1.next()).thenReturn(true).thenReturn(false);
        when(stmtrs1.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAMECAPS);
        when(stmt2.executeQuery()).thenReturn(stmtrs2);
        when(stmtrs2.next()).thenReturn(true).thenReturn(false);
        when(stmtrs2.getString(1)).thenReturn("NOTRIGHTNAME");
        when(stmtrs2.getString(2)).thenReturn("TESTSCHEMA");
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
        when(rs5.getString(1)).thenReturn("testIndexFilter");
        when(rsMetaData5.getColumnLabel(2)).thenReturn("TABLE_NAME");
        when(rsMetaData5.getColumnName(2)).thenReturn("TABLE_NAME");
        when(rs5.getString(2)).thenReturn(DdlReaderTestConstants.TESTNAME);
        when(stmt3.executeQuery()).thenReturn(stmtrs3);
        when(stmtrs3.next()).thenReturn(true);
        when(stmt4.executeQuery()).thenReturn(stmtrs4);
        when(stmtrs4.next()).thenReturn(true);
        Table testTable = spyReader.readTable(DdlReaderTestConstants.CATALOG, DdlReaderTestConstants.SCHEMA, DdlReaderTestConstants.TABLE);
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
        testColumn.setAutoIncrement(true);
        testColumn.setJdbcTypeCode(jdbcTypeCode);// Variable 3
        testColumn.setMappedType(jdbcTypeName);// Variable 2
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        PlatformColumn platformColumn = new PlatformColumn();
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(defaultValue);// Variable 1
        platformColumn.setName("mssql2008");
        platformColumn.setType(jdbcTypeName);// Variable 2
        platformColumn.setSize(platformColumnSize);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("mssql2008", platformColumn);
        PlatformIndex platformIndex = new PlatformIndex();
        if (compressionType == 1) {
            platformIndex.setCompressionType(CompressionTypes.ROW);
        } else if (compressionType == 2) {
            platformIndex.setCompressionType(CompressionTypes.PAGE);
        } else if (compressionType == 3) {
            platformIndex.setCompressionType(CompressionTypes.COLUMNSTORE);
        } else if (compressionType == 4) {
            platformIndex.setCompressionType(CompressionTypes.COLUMNSTORE_ARCHIVE);
        } else {
            platformIndex.setCompressionType(CompressionTypes.NONE);
        }
        platformIndex.setFilterCondition("WHERE true");
        platformIndex.setName("testIndexFilter");
        IndexColumn testIndexColumn = new IndexColumn();
        testIndexColumn.setOrdinalPosition(0);
        NonUniqueIndex testIndex = new NonUniqueIndex();
        testIndex.setName("testIndexFilter");
        testIndex.addColumn(testIndexColumn);
        testColumn.addPlatformColumn(platformColumn);
        expectedTable.addColumn(testColumn);
        testIndex.addPlatformIndex(platformIndex);
        expectedTable.addIndex(testIndex);
        assertEquals(expectedTable, testTable);
    }

    protected String getResultSetSchemaName() {
        return DdlReaderTestConstants.TABLE_SCHEM;
    }

    public IDatabasePlatform getPlatform() {
        return platform;
    }
}