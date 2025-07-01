package org.jumpmind.db.mock;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;

import org.jumpmind.db.DdlReaderTestConstants;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Helper class with static methods for mocking result sets and other MySql-specific objects for use with MockDbDataSource
 */
public final class MockDbMySqlUtils {
    public static final String QUERY_TABLE_COLUMN_INFO = "SELECT column_name, extra, column_type, generation_expression FROM information_schema.columns WHERE table_schema = ? AND table_name = ?";

    /**
     * Creates new Column object for MySQL platform with specified properties
     */
    public static Column generateMySqlColumn(String columnName,
            String columnDefault,
            String jdbcTypeName,
            int jdbcTypeCode,
            String columnSize,
            String testColumnMappedType,
            int platformColumnSize,
            String platformColumnType,
            boolean generated) {
        Column testColumn = new Column();
        testColumn.setDefaultValue(columnDefault);
        testColumn.setName(columnName);
        testColumn.setJdbcTypeName(jdbcTypeName);
        testColumn.setSize(columnSize);
        testColumn.setAutoIncrement(false);
        testColumn.setJdbcTypeCode(jdbcTypeCode);
        testColumn.setMappedType(testColumnMappedType);
        testColumn.setPrecisionRadix(10);
        testColumn.setPrimaryKeySequence(1);
        testColumn.setPrimaryKey(true);
        testColumn.setGenerated(generated);
        PlatformColumn platformColumn = new PlatformColumn();
        testColumn.addPlatformColumn(platformColumn);
        platformColumn.setDecimalDigits(-1);
        platformColumn.setDefaultValue(DdlReaderTestConstants.COLUMN_DEF_TEST_VALUE);
        platformColumn.setName("mysql");
        platformColumn.setSize(platformColumnSize);
        platformColumn.setType(platformColumnType);
        HashMap<String, PlatformColumn> expectedPlatformColumn = new HashMap<String, PlatformColumn>();
        expectedPlatformColumn.put("mysql", platformColumn);
        return testColumn;
    }


    /**
     * Mocks up table column information ResultSet for a table (targeting determineExtraColumnInfo call). Use with MockDbDataSource.enqueuePreparedStatement()
     */
    public static ResultSet buildTableLookup1ColumnInformation(String columnName, String extra, String columnType, String generationExpression)
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(4);
        // Mock column 1==column_name
        when(rsMetaData.getColumnLabel(1)).thenReturn("column_name");
        when(rsMetaData.getColumnName(1)).thenReturn("column_name");
        when(rs.getString(1)).thenReturn(columnName);
        when(rs.getObject(1)).thenReturn(columnName);
        // Mock column 2==extra
        when(rsMetaData.getColumnLabel(2)).thenReturn("extra");
        when(rsMetaData.getColumnName(2)).thenReturn("extra");
        when(rs.getString(2)).thenReturn(extra);
        when(rs.getObject(2)).thenReturn(extra);
        // Mock column 3==column_type
        when(rsMetaData.getColumnLabel(3)).thenReturn("column_type");
        when(rsMetaData.getColumnName(3)).thenReturn("column_type");
        when(rs.getString(3)).thenReturn(columnType);
        when(rs.getObject(3)).thenReturn(columnType);
        // Mock column 4==generation_expression
        when(rsMetaData.getColumnLabel(4)).thenReturn("generation_expression");
        when(rsMetaData.getColumnName(4)).thenReturn("generation_expression");
        when(rs.getString(4)).thenReturn(generationExpression);
        when(rs.getObject(4)).thenReturn(generationExpression);
        return rs;
    }

    /**
     * Mocks up table column information ResultSet for a table (targeting determineExtraColumnInfo call). Use with MockDbDataSource.enqueuePreparedStatement()
     */
    public static ResultSet buildTableColumnLookup(Table table) throws SQLException {
        assertNotNull(table);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData rsMetaData = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(rsMetaData);
        when(rsMetaData.getColumnCount()).thenReturn(4);
        Column[] columns = table.getColumns();
        // Mock number of rows in the resultSet = columns.length :
        when(rs.next()).thenAnswer(new Answer<Boolean>() {
            private int currentInvocations = 0;

            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                if (currentInvocations < columns.length) {
                    currentInvocations++;
                    return true;
                } else {
                    return false;
                }
            }
        });
        for (Column column : columns) {
            // Mock column 1==column_name
            when(rsMetaData.getColumnLabel(1)).thenReturn("column_name");
            when(rsMetaData.getColumnName(1)).thenReturn("column_name");
            when(rsMetaData.getColumnType(1)).thenReturn(Types.VARCHAR);
            when(rsMetaData.getColumnTypeName(1)).thenReturn("varchar");
            when(rs.getObject(1)).thenReturn(column.getName());
            // Mock column 2==extra
            when(rsMetaData.getColumnLabel(2)).thenReturn("extra");
            when(rsMetaData.getColumnName(2)).thenReturn("extra");
            when(rsMetaData.getColumnType(2)).thenReturn(Types.VARCHAR);
            when(rsMetaData.getColumnTypeName(2)).thenReturn("varchar");
            when(rs.getObject(2)).thenReturn(column.getDescription()); // TODO: Fix! extra?
            // Mock column 3==column_type
            when(rsMetaData.getColumnLabel(3)).thenReturn("column_type");
            when(rsMetaData.getColumnName(3)).thenReturn("column_type");
            when(rsMetaData.getColumnType(3)).thenReturn(Types.VARCHAR);
            when(rsMetaData.getColumnTypeName(3)).thenReturn("varchar");
            when(rs.getObject(3)).thenReturn(column.getMappedType());
            // Mock column 4==generation_expression
            when(rsMetaData.getColumnLabel(4)).thenReturn("generation_expression");
            when(rsMetaData.getColumnName(4)).thenReturn("generation_expression");
            when(rsMetaData.getColumnType(4)).thenReturn(Types.VARCHAR);
            when(rsMetaData.getColumnTypeName(4)).thenReturn("varchar");
            when(rs.getObject(4)).thenReturn(column.getDefaultValue());
        }
        return rs;
    }

}
