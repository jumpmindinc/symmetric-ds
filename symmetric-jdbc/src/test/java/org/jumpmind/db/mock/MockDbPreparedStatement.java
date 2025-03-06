package org.jumpmind.db.mock;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * Helper class stubs out most methods for PreparedStatement. All mocked Resultsets are piped from MockDbDataSource.
 */
public class MockDbPreparedStatement implements PreparedStatement {
    protected String sql;
    protected ResultSet mockResultSet;
    protected int repeatOutput;
    private MockDbConnection parentConnection;
    private int queryTimeout;
    private int fetchSizeRows;
    private int fetchMaxRows;

    public MockDbPreparedStatement(String sql, ResultSet mockResultSet, int repeatOutput) {
        this.sql = sql;
        this.mockResultSet = mockResultSet;
        this.repeatOutput = repeatOutput;
        this.parentConnection = null;
        this.queryTimeout = 0;
        this.fetchSizeRows = -1;
        this.fetchMaxRows = -1;
        System.out.println("MockDbPreparedStatement - init. SQL=" + sql + ";ResultSet.rows=" + mockResultSet);
    }

    public void setParentConnection(MockDbConnection parentConnection) {
        this.parentConnection = parentConnection;
    }

    public String getSql() {
        return this.sql;
    }

    public boolean isSqlMatch(String sqlRequested) {
        return sqlRequested.startsWith(this.sql);
    }

    @Override
    public void addBatch(String arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void cancel() throws SQLException {
    }

    @Override
    public void clearBatch() throws SQLException {
    }

    @Override
    public void clearWarnings() throws SQLException {
    }

    @Override
    public void close() throws SQLException {
        this.sql = "{CLOSED}";
    }

    @Override
    public void closeOnCompletion() throws SQLException {
    }

    @Override
    public boolean execute(String arg0) throws SQLException {
        return true;
    }

    @Override
    public boolean execute(String arg0, int arg1) throws SQLException {
        return true;
    }

    @Override
    public boolean execute(String arg0, int[] arg1) throws SQLException {
        return true;
    }

    @Override
    public boolean execute(String arg0, String[] arg1) throws SQLException {
        return true;
    }

    @Override
    public int[] executeBatch() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        if (repeatOutput-- < 1) {
            return null;
        }
        return this.mockResultSet;
    }

    @Override
    public int executeUpdate(String arg0) throws SQLException {
        throw new SQLException("executeUpdate is not allowed for PreparedStatements!");
    }

    @Override
    public int executeUpdate(String arg0, int arg1) throws SQLException {
        throw new SQLException("executeUpdate is not allowed for PreparedStatements!");
    }

    @Override
    public int executeUpdate(String arg0, int[] arg1) throws SQLException {
        throw new SQLException("executeUpdate is not allowed for PreparedStatements!");
    }

    @Override
    public int executeUpdate(String arg0, String[] arg1) throws SQLException {
        throw new SQLException("executeUpdate is not allowed for PreparedStatements!");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.parentConnection;
    }

    @Override
    public int getFetchDirection() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getFetchSize() throws SQLException {
        return this.fetchSizeRows;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getMaxRows() throws SQLException {
        return this.fetchMaxRows;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean getMoreResults(int arg0) throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return this.queryTimeout;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        // if (repeatOutput-- < 1) {
        // return null;
        // }
        return this.mockResultSet;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getResultSetType() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.sql.equals("{CLOSED}");
    }

    @Override
    public boolean isPoolable() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setCursorName(String arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setEscapeProcessing(boolean arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setFetchDirection(int arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        this.fetchSizeRows = rows;
    }

    @Override
    public void setMaxFieldSize(int arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        this.fetchMaxRows = max;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        this.queryTimeout = seconds;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addBatch() throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void clearParameters() throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean execute() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        if (repeatOutput-- < 1) {
            return null;
        }
        return this.mockResultSet;
    }

    @Override
    public int executeUpdate() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
    }


}
