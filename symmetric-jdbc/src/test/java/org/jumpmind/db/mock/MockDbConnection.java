package org.jumpmind.db.mock;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.jumpmind.db.sql.SqlException;

/**
 * Helper class stubs out most methods for Connection. All mocked Resultsets are piped from MockDbDataSource.
 */
public class MockDbConnection implements Connection {
    protected MockDbDataSource parentDataSource;
    // protected ArrayList<ResultSet> metaDataResults;
    private String catalog;
    private String schema;
    private int transactionIsolationLevel;
    private boolean readOnly;
    private boolean autoCommitFlag;

    public MockDbConnection(MockDbDataSource parentDataSource) {
        this.parentDataSource = parentDataSource;
        try {
            this.catalog = "";
            this.schema = "";
            this.transactionIsolationLevel = 0;
            this.readOnly = false;
            this.autoCommitFlag = true;
        } catch (Exception ex) {
            System.out.println("MockDbSqlConnection - FAILED. Ex=" + ex.toString());
            return;
        }
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public void abort(Executor executor) throws SQLException {
    }

    @Override
    public void clearWarnings() throws SQLException {
    }

    @Override
    public void close() throws SQLException {
    }

    @Override
    public void commit() throws SQLException {
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return null;
    }

    @Override
    public Blob createBlob() throws SQLException {
        return null;
    }

    @Override
    public Clob createClob() throws SQLException {
        return null;
    }

    @Override
    public NClob createNClob() throws SQLException {
        return null;
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return null;
    }

    /**
     * Pulls mocked Statement out of the queue.
     */
    @Override
    public Statement createStatement() throws SQLException {
        MockDbStatement statement = this.parentDataSource.dequeueStatement();
        if (statement == null) {
            System.out.println("createStatement - No pre-defined results!");
            throw new SqlException("No more pre-defined results in the preparedStatements queue!");
        }
        System.out.println("createStatement - Dequeued statement OK; SQL=" + statement.getSql());
        return statement;
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return this.createStatement();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return this.createStatement();
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return null;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return this.autoCommitFlag;
    }

    @Override
    public String getCatalog() throws SQLException {
        return this.catalog;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return null;
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return null;
    }

    @Override
    public int getHoldability() throws SQLException {
        return 0;
    }

    @Override
    public DatabaseMetaData getMetaData() {
        return this.parentDataSource.getMetaData();
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return 0;
    }

    @Override
    public String getSchema() throws SQLException {
        return this.schema;
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return transactionIsolationLevel;
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return null;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return this.readOnly;
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return true;
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return sql;
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return null;
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return null;
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return null;
    }

    /**
     * Pulls mocked PreparedStatement with it's pre-staged result set out of the queue.
     */
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        MockDbPreparedStatement statement = this.parentDataSource.dequeuePreparedStatement();
        if (statement == null) {
            System.out.println("prepareStatement - No pre-defined results! query.sql=" + sql);
            throw new SqlException("No more pre-defined results in the preparedStatements queue!");
        }
        if (statement.isSqlMatch(sql)) {
            System.out.println("prepareStatement - Dequeued PreparedStatement OK; query.sql=" + sql);
            return statement;
        }
        throw new SqlException("prepareStatement - Dequeued PreparedStatement SQL does not match! Stored.sql=" + statement.getSql() + "; query.sql=" + sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return this.prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return this.prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return this.prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return this.prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return this.prepareStatement(sql);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    }

    @Override
    public void rollback() throws SQLException {
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.autoCommitFlag = autoCommit;
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        this.catalog = catalog;
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.readOnly = readOnly;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints are not supported in mockDb!");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints are not supported in mockDb!");
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        this.schema = schema;
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        this.transactionIsolationLevel = level;
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    }
}
