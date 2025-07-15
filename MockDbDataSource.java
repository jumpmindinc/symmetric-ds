package org.jumpmind.db.mock;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.jumpmind.db.sql.SqlTemplateSettings;

/**
 * Main class in the hierarchy of Mock classes allowing to pre-stage any result sets for test subjects (DatabasePlatform, DdlReader, DdlBuilder, etc.).
 */
public class MockDbDataSource implements DataSource {
    protected MockDbConnection mockConnection;
    protected MockDbMetaData mockDbMetaData;
    protected ArrayList<ResultSet> metaDataSets;
    protected ArrayList<MockDbStatement> statements;
    protected ArrayList<MockDbPreparedStatement> preparedStatements;
    protected SqlTemplateSettings sqlTemplateSettings;

    public MockDbDataSource(int databaseMajorVersion) {
        this.mockConnection = new MockDbConnection(this);
        this.mockDbMetaData = new MockDbMetaData(this, databaseMajorVersion);
        this.metaDataSets = new ArrayList<ResultSet>();
        this.statements = new ArrayList<MockDbStatement>();
        this.preparedStatements = new ArrayList<MockDbPreparedStatement>();
        this.sqlTemplateSettings = new SqlTemplateSettings();
        this.sqlTemplateSettings.setQueryTimeout(0);
    }

    public MockDbDataSource(int databaseMajorVersion, SqlTemplateSettings sqlTemplateSettings) {
        this.mockConnection = new MockDbConnection(this);
        this.mockDbMetaData = new MockDbMetaData(this, databaseMajorVersion);
        this.statements = new ArrayList<MockDbStatement>();
        this.preparedStatements = new ArrayList<MockDbPreparedStatement>();
        this.sqlTemplateSettings = sqlTemplateSettings;
    }

    public int getDatabaseMajorVersion() throws SQLException {
        return this.mockDbMetaData.getDatabaseMajorVersion();
    }

    public SqlTemplateSettings getSqlTemplateSettings() {
        return this.sqlTemplateSettings;
    }

    public DatabaseMetaData getMetaData() {
        return this.mockDbMetaData;
    }

    public void enqueue(MockDbPreparedStatement preparedStatement) {
        this.preparedStatements.add(preparedStatement);
    }

    // Helper method to simplify staging query result:
    public void enqueuePreparedStatement(String sql, ResultSet mockResultSet, int repeatOutput) {
        this.enqueue(MockDbUtils.buildPreparedStatement(sql, mockResultSet, repeatOutput));
    }

    public void enqueue(MockDbStatement statement) {
        this.statements.add(statement);
    }

    // Helper method to simplify staging query result:
    public void enqueueStatement(String sql, ResultSet mockResultSet, int repeatOutput) {
        this.enqueue(MockDbUtils.buildStatement(sql, mockResultSet, repeatOutput));
    }

    public void enqueueMetaData(ResultSet resultSet) {
        this.metaDataSets.add(resultSet);
    }

    public ResultSet dequeueMetaDataResultSet() {
        if (this.metaDataSets.size() < 1) {
            System.out.println("dequeuePreparedStatement - No more pre-defined results in the metaData queue!");
            return null;
        }
        ResultSet resultSet = this.metaDataSets.remove(0);
        // System.out.println("dequeueMetaDataResultSet - Dispensed one resultSet entry from the metaData queue;");
        return resultSet;
    }

    public MockDbPreparedStatement dequeuePreparedStatement() {
        if (this.preparedStatements.size() < 1) {
            System.out.println("dequeuePreparedStatement - No more pre-defined results in the preparedStatements queue!");
            return null;
        }
        MockDbPreparedStatement preparedStatement = this.preparedStatements.remove(0);
        System.out.println("dequeuePreparedStatement - Dispensed one item from the preparedStatements queue;");
        return preparedStatement;
    }

    public MockDbStatement dequeueStatement() {
        if (this.statements.size() < 1) {
            System.out.println("dequeueStatement - No more pre-defined results in the statements queue!");
            return null;
        }
        MockDbStatement statement = this.statements.remove(0);
        // System.out.println("dequeueStatement - Dispensed one item from the statements queue;");
        return statement;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
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
    public Connection getConnection() throws SQLException {
        return mockConnection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return mockConnection;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
    }

    /**
     * Mocks and enqueues 1 result for a table look up query (useful for DdlReader.getTableNames )
     */
    public void mockAndEnqueueTableLookup1Results(String tableName, String anticipatedPlatformTableLookupQuery)
            throws SQLException {
        MockDbPreparedStatement tableLookupStatement = MockDbUtils.mockTableLookup1Statement(tableName, anticipatedPlatformTableLookupQuery);
        this.enqueue(tableLookupStatement);
    }

    /**
     * Mocks and enqueues 1 result for a trigger look up query (useful for DdlReader.getTriggers )
     */
    public void mockAndEnqueueTriggerLookup1Results(String triggerName, String schemaName, String tableName, String triggerSource,
            String isInsert, String isUpdate, String isDelete, String triggerInfoQuery) throws SQLException {
        MockDbPreparedStatement triggerLookupStatement = MockDbUtils.mockTriggerLookup1PreparedStatement(triggerName, schemaName, tableName,
                triggerSource, isInsert, isUpdate, isDelete, triggerInfoQuery);
        this.enqueue(triggerLookupStatement);
    }
}
