package org.jumpmind.db.platform.mysql;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.sql.ResultSet;
import java.sql.Types;

import org.jumpmind.db.DbTestUtils;
import org.jumpmind.db.mock.MockDbDataSource;
import org.jumpmind.db.mock.MockDbMySqlUtils;
import org.jumpmind.db.mock.MockDbUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MySqlDdlReaderTest extends MySqlDdlReader {
    public final int MySqlDatabasePlatform_VERSION8 = 8;

    public MySqlDdlReaderTest() throws Exception {
        super(DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT));
    }

    @BeforeEach
    public void setUp() throws Exception {
        platform = mock(MySqlDatabasePlatform.class);
    }

    public void close() throws Exception {
        platform = null;
    }

    private MySqlDdlReader createMySqlDdlReader(MockDbDataSource mockDataSource) {
        MySqlDatabasePlatform platformMySql = new MySqlDatabasePlatform(mockDataSource, mockDataSource.getSqlTemplateSettings());
        MySqlDdlReader testReader = new MySqlDdlReader(platformMySql);
        return testReader;
    }


    @Test
    void testDetermineExtraColumnInfo() throws Exception {
        String columnName = "LastWritten";
        String extra = "DEFAULT CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP NOT NULL";
        String columnType = "timestamp";
        String generationExpression = "";
        MockDbDataSource mockDataSource = new MockDbDataSource(MySqlDatabasePlatform_VERSION8);
        MySqlDdlReader testReader = createMySqlDdlReader(mockDataSource);
        Column testColumn = MockDbMySqlUtils.generateMySqlColumn(columnName, "DEFAULT CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP NOT NULL", columnType,
                Types.TIMESTAMP,
                "0", "TIMESTAMP", 0, columnType, true);
        Table testTable = MockDbUtils.generateOneColumnTable(testColumn, null);
        ResultSet mockResultSet = MockDbMySqlUtils.buildTableLookup1ColumnInformation(columnName, extra, columnType, generationExpression);
        mockDataSource.enqueuePreparedStatement(MockDbMySqlUtils.QUERY_TABLE_COLUMN_INFO, mockResultSet, 1);
        assertEquals(true, testColumn.isGenerated());
        assertEquals(columnName, mockResultSet.getObject(1));
        testReader.determineExtraColumnInfo(testTable);
        assertEquals(false, testColumn.isGenerated());
        // DataSource dataSource = mock(DataSource.class);
        // DatabaseInfo databaseInfo = mock(DatabaseInfo.class);
        // SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        // Connection connection = mock(Connection.class);
        // SymmetricLobHandler lobHandler = mock(SymmetricLobHandler.class);
        // DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        // // "Real" Components
        // MySqlDatabasePlatform platform = new MySqlDatabasePlatform(dataSource, settings);
        // MySqlJdbcSqlTemplate testTemplate = new MySqlJdbcSqlTemplate(dataSource, settings, lobHandler, databaseInfo);
        // // Spied Components
        // MySqlDatabasePlatform spyPlatform = Mockito.spy(platform);
        // // MySqlDdlReader testReader = new MySqlDdlReader(spyPlatform);
        // // MySqlDdlReader spyReader = Mockito.spy(testReader);
        // testTemplate.setIsolationLevel(1);
        // MySqlJdbcSqlTemplate spyTemplate = Mockito.spy(testTemplate);
        // spyTemplate.setIsolationLevel(1);
        // // Mocked result set
        // ResultSet rs = mock(ResultSet.class);
        // ResultSet stmtrs1 = mock(ResultSet.class);
        // ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        // PreparedStatement stmt1 = mock(PreparedStatement.class);
        // ResultSetMetaData stmt1RsMetaData = mock(ResultSetMetaData.class);
        // ResultSet rs2 = mock(ResultSet.class);
        // ResultSet stmtrs2 = mock(ResultSet.class);
        // ResultSetMetaData rsMetaData2 = mock(ResultSetMetaData.class);
        // PreparedStatement stmt2 = mock(PreparedStatement.class);
        // ResultSet rs3 = mock(ResultSet.class);
        // ResultSet stmtrs3 = mock(ResultSet.class);
        // ResultSetMetaData rsMetaData3 = mock(ResultSetMetaData.class);
        // PreparedStatement stmt3 = mock(PreparedStatement.class);
        // when(spyTemplate.getDataSource().getConnection()).thenReturn(connection);
        // doReturn(spyTemplate).when(spyPlatform).createSqlTemplate();
        // doReturn(new ArrayList<Row>()).when(spyTemplate).query(ArgumentMatchers.anyString());
        // when(spyPlatform.createSqlTemplate()).thenReturn(spyTemplate);
        // when(spyPlatform.getSqlTemplateDirty()).thenReturn(spyTemplate);
        // when(spyPlatform.getSqlTemplate()).thenReturn(spyTemplate);
        // when(spyTemplate.getDataSource()).thenReturn(dataSource);
        // when(dataSource.getConnection()).thenReturn(connection);
        // when(connection.getMetaData()).thenReturn(metaData);
        // when(metaData.getTables(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
        // (String[]) ArgumentMatchers.any())).thenReturn(rs);
        // when(rs.next()).thenReturn(true).thenReturn(false);
        // when(rs.getMetaData()).thenReturn(rsMetaData);
        // when(rsMetaData.getColumnCount()).thenReturn(5);
        // when(rsMetaData.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        // when(rsMetaData.getColumnName(1)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        // when(rs.getString(1)).thenReturn(DdlReaderTestConstants.TESTNAME);
        // when(rsMetaData.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        // when(rsMetaData.getColumnName(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE);
        // when(rs.getString(2)).thenReturn(DdlReaderTestConstants.TABLE_TYPE_TEST_VALUE);
        // when(rsMetaData.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        // when(rsMetaData.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT);
        // when(rs.getString(3)).thenReturn(DdlReaderTestConstants.TABLE_CAT_TEST_VALUE);
        // when(rsMetaData.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        // when(rsMetaData.getColumnName(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEM);
        // when(rs.getString(4)).thenReturn(DdlReaderTestConstants.TABLE_SCHEMA_TEST_VALUE);
        // when(rsMetaData.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        // when(rsMetaData.getColumnName(5)).thenReturn(DdlReaderTestConstants.REMARKS);
        // when(rs.getString(5)).thenReturn(DdlReaderTestConstants.REMARKS_TEST_VALUE);
        // when(metaData.getColumns(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
        // ArgumentMatchers.any())).thenReturn(rs2);
        // when(rs2.next()).thenReturn(true).thenReturn(false);
        // when(rs2.getMetaData()).thenReturn(rsMetaData2);
        // when(rsMetaData2.getColumnCount()).thenReturn(8);
        // when(rsMetaData2.getColumnLabel(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        // when(rsMetaData2.getColumnName(1)).thenReturn(DdlReaderTestConstants.COLUMN_DEF);
        // when(rs2.getString(1)).thenReturn(columnDef);
        // when(rsMetaData2.getColumnLabel(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        // when(rsMetaData2.getColumnName(2)).thenReturn(DdlReaderTestConstants.COLUMN_DEFAULT);
        // when(rs2.getString(2)).thenReturn(columnDefault);
        // when(rsMetaData2.getColumnLabel(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        // when(rsMetaData2.getColumnName(3)).thenReturn(DdlReaderTestConstants.TABLE_NAME);
        // when(rs2.getString(3)).thenReturn(DdlReaderTestConstants.TESTNAME);
        // when(rsMetaData2.getColumnLabel(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        // when(rsMetaData2.getColumnName(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME);
        // when(rs2.getString(4)).thenReturn(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        // when(rsMetaData2.getColumnLabel(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        // when(rsMetaData2.getColumnName(5)).thenReturn(DdlReaderTestConstants.TYPE_NAME);
        // when(rs2.getString(5)).thenReturn(jdbcTypeName);
        // when(rsMetaData2.getColumnLabel(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        // when(rsMetaData2.getColumnName(6)).thenReturn(DdlReaderTestConstants.DATA_TYPE);
        // when(rs2.getInt(6)).thenReturn(jdbcTypeCode);
        // when(rsMetaData2.getColumnLabel(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        // when(rsMetaData2.getColumnName(7)).thenReturn(DdlReaderTestConstants.IS_NULLABLE);
        // when(rs2.getString(7)).thenReturn("TRUE");
        // when(rsMetaData2.getColumnLabel(8)).thenReturn("COLUMN_SIZE");
        // when(rsMetaData2.getColumnName(8)).thenReturn("COLUMN_SIZE");
        // when(rs2.getString(8)).thenReturn(columnSize);
        // when(metaData.getImportedKeys(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        // .thenReturn(rs3);
        // when(rs3.next()).thenReturn(false);
        // when(rs3.getMetaData()).thenReturn(rsMetaData3);
        // when(rsMetaData3.getColumnCount()).thenReturn(0);
        // // Expected Column
        // Column testColumn = new Column();
        // testColumn.setDefaultValue(columnDef);
        // testColumn.setName(DdlReaderTestConstants.COLUMN_NAME_TEST_VALUE);
        // testColumn.setJdbcTypeName(testColumnJdbcTypeName);
        // testColumn.setSize(columnSize);
        // testColumn.setAutoIncrement(false);
        // testColumn.setJdbcTypeCode(testColumnJdbcTypeCode);
        // testColumn.setMappedType(testColumnMappedType);
        // testColumn.setPrecisionRadix(10);
        // testColumn.setPrimaryKeySequence(1);
        // testColumn.setPrimaryKey(true);
    }
}
