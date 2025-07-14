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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({ "LastWritten, DEFAULT CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP NOT NULL, timestamp, ", })
    void testDetermineExtraColumnInfo_With_Default_Current_TimeStamp_On_Update(String columnName, String extra, String columnType, String generationExpression)
            throws Exception {
        MockDbDataSource mockDataSource = new MockDbDataSource(MySqlDatabasePlatform_VERSION8);
        MySqlDdlReader testReader = createMySqlDdlReader(mockDataSource);
        Column testColumn = MockDbMySqlUtils.generateMySqlColumn(columnName, extra, columnType,
                Types.TIMESTAMP,
                "0", "TIMESTAMP", 0, columnType, true, false, false);
        Table testTable = MockDbUtils.generateOneColumnTable(testColumn, null);
        ResultSet mockResultSet = MockDbMySqlUtils.buildTableLookup1ColumnInformation(columnName, extra, columnType, generationExpression);
        mockDataSource.enqueuePreparedStatement(MockDbMySqlUtils.QUERY_TABLE_COLUMN_INFO, mockResultSet, 1);
        assertEquals(true, testColumn.isGenerated());
        assertEquals(false, testColumn.isAutoUpdate());
        assertEquals(columnName, mockResultSet.getObject(1));
        testReader.determineExtraColumnInfo(testTable);
        assertEquals(false, testColumn.isGenerated());
        assertEquals(true, testColumn.isAutoUpdate());
        assertEquals(columnName, testColumn.getName());
    }

    @ParameterizedTest
    @CsvSource({ "LastWritten, on update CURRENT_TIMESTAMP NOT NULL, timestamp, ", })
    void testDetermineExtraColumnInfo_On_Update_Current_Time(String columnName, String extra, String columnType, String generationExpression) throws Exception {
        MockDbDataSource mockDataSource = new MockDbDataSource(MySqlDatabasePlatform_VERSION8);
        MySqlDdlReader testReader = createMySqlDdlReader(mockDataSource);
        Column testColumn = MockDbMySqlUtils.generateMySqlColumn(columnName, extra, columnType,
                Types.TIMESTAMP,
                "0", "TIMESTAMP", 0, columnType, true, false, false);
        Table testTable = MockDbUtils.generateOneColumnTable(testColumn, null);
        ResultSet mockResultSet = MockDbMySqlUtils.buildTableLookup1ColumnInformation(columnName, extra, columnType, generationExpression);
        mockDataSource.enqueuePreparedStatement(MockDbMySqlUtils.QUERY_TABLE_COLUMN_INFO, mockResultSet, 1);
        assertEquals(true, testColumn.isGenerated());
        assertEquals(false, testColumn.isAutoUpdate());
        assertEquals(columnName, mockResultSet.getObject(1));
        testReader.determineExtraColumnInfo(testTable);
        assertEquals(false, testColumn.isGenerated());
        assertEquals(true, testColumn.isAutoUpdate());
        assertEquals(columnName, testColumn.getName());
    }

    @ParameterizedTest
    @CsvSource({ "LastWritten, DEFAULT_GENERATED, timestamp, ", })
    void testDetermineExtraColumnInfo_Default_Current_Time(String columnName, String extra, String columnType, String generationExpression) throws Exception {
        MockDbDataSource mockDataSource = new MockDbDataSource(MySqlDatabasePlatform_VERSION8);
        MySqlDdlReader testReader = createMySqlDdlReader(mockDataSource);
        Column testColumn = MockDbMySqlUtils.generateMySqlColumn(columnName, extra, columnType,
                Types.TIMESTAMP,
                "0", "TIMESTAMP", 0, columnType, true, false, false);
        Table testTable = MockDbUtils.generateOneColumnTable(testColumn, null);
        ResultSet mockResultSet = MockDbMySqlUtils.buildTableLookup1ColumnInformation(columnName, extra, columnType, generationExpression);
        mockDataSource.enqueuePreparedStatement(MockDbMySqlUtils.QUERY_TABLE_COLUMN_INFO, mockResultSet, 1);
        assertEquals(true, testColumn.isGenerated());
        assertEquals(false, testColumn.isAutoUpdate());
        assertEquals(columnName, mockResultSet.getObject(1));
        testReader.determineExtraColumnInfo(testTable);
        assertEquals(false, testColumn.isGenerated());
        assertEquals(false, testColumn.isAutoUpdate());
        assertEquals(true, testColumn.isExpressionAsDefaultValue());
        assertEquals(columnName, testColumn.getName());
    }

    @ParameterizedTest
    @CsvSource({ "LastWritten, , timestamp, ", })
    void testDetermineExtraColumnInfo_Time_Not_Null(String columnName, String extra, String columnType, String generationExpression) throws Exception {
        MockDbDataSource mockDataSource = new MockDbDataSource(MySqlDatabasePlatform_VERSION8);
        MySqlDdlReader testReader = createMySqlDdlReader(mockDataSource);
        Column testColumn = MockDbMySqlUtils.generateMySqlColumn(columnName, extra, columnType,
                Types.TIMESTAMP,
                "0", "TIMESTAMP", 0, columnType, false, false, false);
        Table testTable = MockDbUtils.generateOneColumnTable(testColumn, null);
        ResultSet mockResultSet = MockDbMySqlUtils.buildTableLookup1ColumnInformation(columnName, extra, columnType, generationExpression);
        mockDataSource.enqueuePreparedStatement(MockDbMySqlUtils.QUERY_TABLE_COLUMN_INFO, mockResultSet, 1);
        assertEquals(false, testColumn.isGenerated());
        assertEquals(false, testColumn.isAutoUpdate());
        assertEquals(true, testColumn.isRequired());
        assertEquals(columnName, mockResultSet.getObject(1));
        testReader.determineExtraColumnInfo(testTable);
        assertEquals(false, testColumn.isGenerated());
        assertEquals(false, testColumn.isAutoUpdate());
        assertEquals(columnName, testColumn.getName());
        assertEquals(true, testColumn.isRequired());
    }

    @ParameterizedTest
    @CsvSource({ "Id, auto_increment, int, ", })
    void testDetermineExtraColumnInfo_Auto_Increment(String columnName, String extra, String columnType, String generationExpression) throws Exception {
        MockDbDataSource mockDataSource = new MockDbDataSource(MySqlDatabasePlatform_VERSION8);
        MySqlDdlReader testReader = createMySqlDdlReader(mockDataSource);
        Column testColumn = MockDbMySqlUtils.generateMySqlColumn(columnName, extra, columnType,
                Types.INTEGER,
                "0", "integer", 0, columnType, false, false, false);
        Table testTable = MockDbUtils.generateOneColumnTable(testColumn, null);
        ResultSet mockResultSet = MockDbMySqlUtils.buildTableLookup1ColumnInformation(columnName, extra, columnType, generationExpression);
        mockDataSource.enqueuePreparedStatement(MockDbMySqlUtils.QUERY_TABLE_COLUMN_INFO, mockResultSet, 1);
        assertEquals(false, testColumn.isGenerated());
        assertEquals(false, testColumn.isAutoIncrement());
        assertEquals(true, testColumn.isRequired());
        assertEquals(columnName, mockResultSet.getObject(1));
        testReader.determineExtraColumnInfo(testTable);
        assertEquals(false, testColumn.isGenerated());
        assertEquals(true, testColumn.isAutoIncrement());
        assertEquals(columnName, testColumn.getName());
        assertEquals(true, testColumn.isRequired());
    }
}
