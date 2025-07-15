package org.jumpmind.db.mock;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;

import org.apache.commons.lang3.NotImplementedException;
import org.jumpmind.db.sql.SqlException;

/**
 * Helper class stubs out most methods for DatabaseMetaData. All mocked Resultsets are piped from MockDbDataSource.
 */
public class MockDbMetaData implements DatabaseMetaData {
    protected MockDbDataSource parentDataSource;
    protected int databaseMajorVersion = 1;
    final int maxNameLength = 100;
    final int maxStatementLength = 10000;
    final int maxTablesInSelect = 10;
    final String identifierQuoteString = "\"";
    final String catalogSeparator = ".";

    public MockDbMetaData(MockDbDataSource parentDataSource, int databaseMajorVersion) {
        this.parentDataSource = parentDataSource;
        this.databaseMajorVersion = databaseMajorVersion;
    }

    @Override
    public boolean isWrapperFor(Class<?> arg0) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public <T> T unwrap(Class<T> arg0) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean deletesAreDetected(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getAttributes - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; typeNamePattern="
                    + typeNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getAttributes - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; typeNamePattern="
                + typeNamePattern);
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getBestRowIdentifier - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schema + "; table="
                    + table);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getBestRowIdentifier - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schema + "; table="
                + table);
        return resultSet;
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        return catalogSeparator;
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
        // return catalogTerm;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getCatalogs() throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getCatalogs - No pre-defined results!");
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getCatalogs - Dequeued results OK.");
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getClientInfoProperties - No pre-defined results!");
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getClientInfoProperties - Dequeued results OK.");
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getColumnPrivileges - No pre-defined results! catalog=" + catalog + "; schema=" + schema + "; table="
                    + table + "; columnNamePattern="
                    + columnNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getColumnPrivileges - Dequeued results OK; catalog=" + catalog + "; schema=" + schema + "; table="
                + table + "; columnNamePattern="
                + columnNamePattern);
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getColumns - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; tableNamePattern="
                    + tableNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getColumns - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; tableNamePattern="
                + tableNamePattern);
        return resultSet;
    }

    @Override
    public Connection getConnection() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getCrossReference(String catalog, String schema, String table, String foreignCatalog, String foreignSchema,
            String foreignTable) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getCrossReference - No pre-defined results! catalog=" + catalog + "; schema=" + schema + "; table="
                    + table);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getCrossReference - Dequeued results OK; catalog=" + catalog + "; schema=" + schema + "; table="
                + table);
        return resultSet;
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        return this.databaseMajorVersion;
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        return 0;
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        return String.format("%d", this.databaseMajorVersion);
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getDriverMajorVersion() {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getDriverMinorVersion() {
        return 0;
    }

    @Override
    public String getDriverName() throws SQLException {
        return "MockedDriverInMockDbMetaData";
    }

    @Override
    public String getDriverVersion() throws SQLException {
        return "MockedDriverInMockDbMetaData";
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getExportedKeys - No pre-defined results! catalog=" + catalog + "; schema=" + schema + "; table="
                    + table);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getExportedKeys - Dequeued results OK; catalog=" + catalog + "; schema=" + schema + "; table="
                + table);
        return resultSet;
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getFunctionColumns - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; functionNamePattern="
                    + functionNamePattern + "; columnNamePattern="
                    + columnNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getFunctionColumns - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; functionNamePattern="
                + functionNamePattern + "; columnNamePattern="
                + columnNamePattern);
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getAttributes - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; functionNamePattern="
                    + functionNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getAttributes - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; functionNamePattern="
                + functionNamePattern);
        return resultSet;
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        return identifierQuoteString;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String tableName) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getImportedKeys - No pre-defined results! catalog=" + catalog + "; schema=" + schema + "; tableName="
                    + tableName);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getImportedKeys - Dequeued results OK; catalog=" + catalog + "; schema=" + schema + "; tableName="
                + tableName);
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String tableName, boolean unique, boolean approximate) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getIndexInfo - No pre-defined results! catalog=" + catalog + "; schema=" + schema + "; tableName="
                    + tableName);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getIndexInfo - Dequeued results OK; catalog=" + catalog + "; schema=" + schema + "; tableName="
                + tableName);
        return resultSet;
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        return maxNameLength;
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        return maxNameLength;
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxConnections() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        return maxNameLength;
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        return maxNameLength;
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        return maxNameLength;
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
        // return maxRowSize;
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        return maxNameLength;
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        return maxStatementLength;
    }

    @Override
    public int getMaxStatements() throws SQLException {
        return 1;
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        return maxNameLength;
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        return maxTablesInSelect;
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        return maxNameLength;
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String tableName) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getPrimaryKeys - No pre-defined results! catalog=" + catalog + "; schema=" + schema + "; tableName="
                    + tableName);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getPrimaryKeys - Dequeued results OK; catalog=" + catalog + "; schema=" + schema + "; tableName="
                + tableName);
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getAttributes - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; procedureNamePattern="
                    + procedureNamePattern + "; columnNamePattern="
                    + columnNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getAttributes - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; procedureNamePattern="
                + procedureNamePattern + "; columnNamePattern="
                + columnNamePattern);
        return resultSet;
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getProcedures - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; procedureNamePattern="
                    + procedureNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getProcedures - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; procedureNamePattern="
                + procedureNamePattern);
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getPseudoColumns - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; columnNamePattern="
                    + columnNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getPseudoColumns - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; columnNamePattern="
                + columnNamePattern);
        return resultSet;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public int getSQLStateType() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        return getSchemas("", "");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getSchemas - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getSchemas - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern);
        return resultSet;
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public String getStringFunctions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getSuperTables - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; tableNamePattern="
                    + tableNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getSuperTables - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; tableNamePattern="
                + tableNamePattern);
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getSuperTypes - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; typeNamePattern="
                    + typeNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getSuperTypes - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; typeNamePattern="
                + typeNamePattern);
        return resultSet;
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getTablePrivileges - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; tableNamePattern="
                    + tableNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getTablePrivileges - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; tableNamePattern="
                + tableNamePattern);
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getTableTypes() throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getTableTypes - No pre-defined results!");
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getTableTypes - Dequeued results OK.");
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getTables - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; tableNamePattern="
                    + tableNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getTables - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; tableNamePattern="
                + tableNamePattern);
        return resultSet;
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getTypeInfo() throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getTypeInfo - No pre-defined results!");
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getTypeInfo - Dequeued results OK.");
        return resultSet;
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getUDTs - No pre-defined results! catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; typeNamePattern="
                    + typeNamePattern);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getUDTs - Dequeued results OK; catalog=" + catalog + "; schemaPattern=" + schemaPattern + "; typeNamePattern="
                + typeNamePattern);
        return resultSet;
    }

    @Override
    public String getURL() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public String getUserName() throws SQLException {
        return "MOCKER";
    }

    /**
     * Returns mock ResultSet from the meta data queue (MockDbDataSource).
     */
    @Override
    public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
        ResultSet resultSet = this.parentDataSource.dequeueMetaDataResultSet();
        if (resultSet == null) {
            System.out.println("getVersionColumns - No pre-defined results! catalog=" + catalog + "; schema=" + schema + "; table="
                    + table);
            throw new SqlException("No more pre-defined results in the metaData queue!");
        }
        System.out.println("getVersionColumns - Dequeued results OK; catalog=" + catalog + "; schema=" + schema + "; table="
                + table);
        return resultSet;
    }

    @Override
    public boolean insertsAreDetected(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean othersDeletesAreVisible(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean othersInsertsAreVisible(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean othersUpdatesAreVisible(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean ownDeletesAreVisible(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean ownInsertsAreVisible(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean ownUpdatesAreVisible(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsConvert(int fromType, int toType) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsResultSetHoldability(int holdability) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsResultSetType(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean updatesAreDetected(int type) throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        throw new NotImplementedException("No mock data/results defined for this method!");
    }
}
