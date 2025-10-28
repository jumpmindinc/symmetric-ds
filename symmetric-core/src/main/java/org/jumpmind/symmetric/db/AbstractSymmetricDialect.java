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
package org.jumpmind.symmetric.db;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IAlterDatabaseInterceptor;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlResultsListener;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.LogSqlResultsListener;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlScript;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.ext.IDatabaseInstallStatementListener;
import org.jumpmind.symmetric.ext.IDatabaseUpgradeListener;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * The abstract class for database dialects.
 */
abstract public class AbstractSymmetricDialect implements ISymmetricDialect {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static final int MAX_SYMMETRIC_SUPPORTED_TRIGGER_SIZE = 50;
    protected IDatabasePlatform platform;
    protected ISymmetricDialect targetDialect = this;
    protected AbstractTriggerTemplate triggerTemplate;
    protected IParameterService parameterService;
    protected IExtensionService extensionService;
    protected Boolean supportsGetGeneratedKeys;
    protected String databaseName;
    protected String driverVersion;
    protected String driverName;
    protected int databaseMajorVersion;
    protected int databaseMinorVersion;
    protected String databaseProductVersion;
    protected Set<String> sqlKeywords;
    protected boolean supportsTransactionViews = false;
    protected boolean supportsSubselectsInDelete = true;
    protected boolean supportsSubselectsInUpdate = true;
    protected boolean supportsDdlTriggers = false;
    protected Map<String, String> sqlReplacementTokens = new HashMap<String, String>();
    protected String tablePrefixLowerCase;
    protected boolean isSpatialTypesEnabled = true;

    public AbstractSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        this.parameterService = parameterService;
        this.platform = platform;
        log.info("The DbDialect being used is {}", this.getClass().getName());
        buildSqlReplacementTokens();
        ISqlTemplate sqlTemplate = this.platform.getSqlTemplate();
        this.databaseMajorVersion = sqlTemplate.getDatabaseMajorVersion();
        this.databaseMinorVersion = sqlTemplate.getDatabaseMinorVersion();
        this.databaseName = sqlTemplate.getDatabaseProductName();
        this.databaseProductVersion = sqlTemplate.getDatabaseProductVersion();
        this.driverName = sqlTemplate.getDriverName();
        this.driverVersion = sqlTemplate.getDriverVersion();
        tablePrefixLowerCase = parameterService.getTablePrefix().toLowerCase();
        this.isSpatialTypesEnabled = parameterService.is(ParameterConstants.SPATIAL_TYPES_ENABLED);
        DatabaseInfo ddlBuilderDbInfo = this.platform.getDdlBuilder().getDatabaseInfo();
        ddlBuilderDbInfo.setDefaultValuesToLeaveUnquotedSupplier(() -> {
            String defaultValuesToLeaveUnquoted = parameterService.getString(ParameterConstants.DEFAULT_VALUES_TO_LEAVE_UNQUOTED);
            if (StringUtils.isNotBlank(defaultValuesToLeaveUnquoted)) {
                String[] values = CsvUtils.tokenizeCsvData(defaultValuesToLeaveUnquoted);
                if (values != null && values.length > 0) {
                    return Arrays.stream(values).collect(Collectors.toSet());
                }
            }
            return new HashSet<String>();
        });
        ddlBuilderDbInfo.setDefaultValuesToTranslateSupplier(() -> {
            String defaultValuesToTranslate = parameterService.getString(ParameterConstants.DEFAULT_VALUES_TO_TRANSLATE);
            Map<String, String> valueMap = new HashMap<String, String>();
            if (StringUtils.isNotBlank(defaultValuesToTranslate)) {
                String[] pairs = CsvUtils.tokenizeCsvData(defaultValuesToTranslate);
                if (pairs != null && pairs.length > 0) {
                    for (String pair : pairs) {
                        String[] values = pair.split("=");
                        if (values.length == 2) {
                            valueMap.put(values[0], values[1]);
                        }
                    }
                }
            }
            return valueMap;
        });
    }

    @Override
    public String getSymmetricDdlChanges() {
        Database modelFromXml = readSymmetricSchemaFromXml();
        Database modelFromDatabase = readSymmetricSchemaFromDatabase();
        List<IAlterDatabaseInterceptor> alterDatabaseInterceptors = extensionService
                .getExtensionPointList(IAlterDatabaseInterceptor.class);
        IAlterDatabaseInterceptor[] interceptors = alterDatabaseInterceptors
                .toArray(new IAlterDatabaseInterceptor[alterDatabaseInterceptors.size()]);
        IDdlBuilder builder = platform.getDdlBuilder();
        String alterSql = builder.alterDatabase(modelFromDatabase, modelFromXml, interceptors);
        return alterSql;
    }

    public boolean requiresAutoCommitFalseToSetFetchSize() {
        return false;
    }

    protected void buildSqlReplacementTokens() {
        sqlReplacementTokens.put("selectDataUsingGapsSqlHint", "");
        sqlReplacementTokens.put("selectDataUsingStartDataIdHint", "");
    }

    @Override
    public Map<String, String> getSqlReplacementTokens() {
        return sqlReplacementTokens;
    }

    /*
     * Provide a default implementation of this method using DDLUtils, getMaxColumnNameLength()
     */
    @Override
    public int getMaxTriggerNameLength() {
        int max = getPlatform().getDatabaseInfo().getMaxColumnNameLength();
        return max < MAX_SYMMETRIC_SUPPORTED_TRIGGER_SIZE && max > 0 ? max : MAX_SYMMETRIC_SUPPORTED_TRIGGER_SIZE;
    }

    @Override
    public void verifyDatabaseIsCompatible() {
    }

    @Override
    public void initTablesAndDatabaseObjects() {
        createRequiredDatabaseObjects();
        createOrAlterTablesIfNecessary();
        platform.resetCachedTableModel();
    }

    protected String replaceTokens(String sql, String objectName) {
        String ddl = FormatUtils.replace("functionName", objectName, sql);
        ddl = FormatUtils.replace("version", Version.versionWithUnderscores(), ddl);
        ddl = FormatUtils.replace("defaultSchema", platform.getDefaultSchema(), ddl);
        ddl = FormatUtils.replace("prefixName", parameterService.getTablePrefix(), ddl);
        return ddl;
    }

    protected boolean installed(String sql, String objectName) {
        return platform.getSqlTemplate().queryForInt(replaceTokens(sql, objectName)) > 0;
    }

    protected void install(String sql, String objectName, StringBuilder ddl) {
        sql = replaceTokens(sql, objectName);
        logSql(sql, ddl);
        if (ddl == null) {
            log.info("Installing SymmetricDS database object:\n{}", sql);
            platform.getSqlTemplate().update(sql);
            log.info("Just installed {}", objectName);
        }
    }

    protected void uninstall(String sql, String objectName) {
        uninstall(sql, objectName, null);
    }

    protected void uninstall(String sql, String objectName, StringBuilder ddl) {
        sql = replaceTokens(sql, objectName);
        logSql(sql, ddl);
        if (ddl == null) {
            platform.getSqlTemplate().update(sql);
            log.info("Just uninstalled {}", objectName);
        }
    }

    @Override
    public void dropTablesAndDatabaseObjects() {
        Database modelFromDatabase = readSymmetricSchemaFromDatabase();
        platform.dropDatabase(modelFromDatabase, true);
        dropRequiredDatabaseObjects();
    }

    @Override
    final public boolean doesTriggerExist(StringBuilder sqlBuffer, String catalogName, String schema, String tableName, String triggerName) {
        if (StringUtils.isNotBlank(triggerName)) {
            try {
                return doesTriggerExistOnPlatform(sqlBuffer, catalogName, schema, tableName, triggerName);
            } catch (Exception ex) {
                log.warn("Could not figure out if the trigger exists.  Assuming that it does not", ex);
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean doesDdlTriggerExist(String catalogName, String schema, String triggerName) {
        return false;
    }

    @Override
    public abstract void dropRequiredDatabaseObjects();

    @Override
    public void createRequiredDatabaseObjects() {
        createRequiredDatabaseObjectsImpl(null);
    };

    @Override
    public String getCreateRequiredDatabaseObjectsDDL() {
        StringBuilder ddl = new StringBuilder();
        createRequiredDatabaseObjectsImpl(ddl);
        return ddl.toString();
    }

    protected void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
    }

    @Override
    abstract public BinaryEncoding getBinaryEncoding();

    abstract protected boolean doesTriggerExistOnPlatform(StringBuilder seqlBuffer, String catalogName, String schema, String tableName, String triggerName);

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema, Trigger trigger) {
        return "null";
    }

    @Override
    public String getTransactionId(ISqlTransaction transaction) {
        return null;
    }

    @Override
    public String createInitialLoadSqlFor(Node node, TriggerRouter trigger, Table table, TriggerHistory triggerHistory, Channel channel,
            String overrideSelectSql) {
        return triggerTemplate.createInitalLoadSql(node, trigger, table, triggerHistory, channel, overrideSelectSql).trim();
    }

    @Override
    public boolean[] getColumnPositionUsingTemplate(Table originalTable, TriggerHistory triggerHistory) {
        return triggerTemplate.getColumnPositionUsingTemplate(originalTable, triggerHistory);
    }

    @Override
    public String createPurgeSqlFor(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory) {
        return createPurgeSqlFor(node, triggerRouter, triggerHistory, null);
    }

    @Override
    public String createPurgeSqlFor(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            List<TransformTableNodeGroupLink> transforms) {
        return createPurgeSqlFor(node, triggerRouter, triggerHistory, transforms, null);
    }

    @Override
    public String createPurgeSqlFor(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            List<TransformTableNodeGroupLink> transforms, String deleteSql) {
        List<String> sqlStatements = createPurgeSqlForMultipleTables(node, triggerRouter, triggerHistory, transforms, deleteSql);
        return sqlStatements.size() == 1 ? sqlStatements.get(0) : "";
    }

    @Override
    public List<String> createPurgeSqlForMultipleTables(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            List<TransformTableNodeGroupLink> transforms, String deleteSql) {
        List<String> sqlStatements = new ArrayList<String>();
        if (StringUtils.isEmpty(triggerRouter.getInitialLoadDeleteStmt())) {
            Set<String> tableNames = new HashSet<String>();
            if (transforms != null) {
                for (TransformTableNodeGroupLink transform : transforms) {
                    tableNames.add(transform.getFullyQualifiedTargetTableName());
                }
            } else {
                tableNames.add(triggerRouter.qualifiedTargetTableName(triggerHistory));
            }
            for (String tableName : tableNames) {
                if (deleteSql == null) {
                    if (tableName.startsWith(parameterService.getTablePrefix())) {
                        deleteSql = "delete from %s";
                    } else {
                        deleteSql = parameterService.getString(ParameterConstants.INITIAL_LOAD_DELETE_FIRST_SQL);
                    }
                }
                sqlStatements.add(String.format(deleteSql, tableName));
            }
        } else {
            sqlStatements.add(triggerRouter.getInitialLoadDeleteStmt());
        }
        return sqlStatements;
    }

    @Override
    public String createCsvDataSql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause) {
        return createCsvDataSql(trigger, triggerHistory, channel, whereClause, platform.getTableFromCache(trigger.getSourceCatalogName(),
                trigger.getSourceSchemaName(), trigger.getSourceTableName(), false));
    }

    public String createCsvDataSql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause, Table table) {
        return triggerTemplate.createCsvDataSql(trigger, triggerHistory, table, channel, whereClause).trim();
    }

    @Override
    public String createCsvPrimaryKeySql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause) {
        return triggerTemplate.createCsvPrimaryKeySql(trigger, triggerHistory, platform.getTableFromCache(trigger.getSourceCatalogName(),
                trigger.getSourceSchemaName(), trigger.getSourceTableName(), false), channel, whereClause).trim();
    }

    public String createCsvPrimaryKeySql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause, Table table) {
        return triggerTemplate.createCsvPrimaryKeySql(trigger, triggerHistory, table, channel, whereClause).trim();
    }

    @Override
    public Set<String> getSqlKeywords() {
        if (sqlKeywords == null) {
            this.sqlKeywords = this.platform.getSqlTemplate().getSqlKeywords();
        }
        return sqlKeywords;
    }

    protected String getDropTriggerSql(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName, String tableName) {
        schemaName = StringUtils.isBlank(schemaName) ? "" : (schemaName + ".");
        return "drop trigger " + schemaName + triggerName;
    }

    @Override
    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName, String tableName) {
        ISqlTransaction transaction = null;
        try {
            transaction = platform.getSqlTemplate().startSqlTransaction(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
            removeTrigger(sqlBuffer, catalogName, schemaName, triggerName, tableName, transaction);
            transaction.commit();
        } catch (Exception ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName, String tableName,
            ISqlTransaction transaction) {
        String sql = getDropTriggerSql(sqlBuffer, catalogName, schemaName, triggerName, tableName);
        logSql(sql, sqlBuffer);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
            log.info("Dropping {} trigger for {}", triggerName, Table.getFullyQualifiedTableName(catalogName, schemaName, tableName));
            transaction.execute(sql);
        }
    }

    @Override
    public void removeDdlTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName) {
    }

    final protected void logSql(String sql, StringBuilder sqlBuffer) {
        if (sqlBuffer != null && StringUtils.isNotBlank(sql)) {
            sqlBuffer.append(sql);
            sqlBuffer.append(System.getProperty("line.separator"));
            sqlBuffer.append(System.getProperty("line.separator"));
        }
    }

    @Override
    public void createTrigger(final StringBuilder sqlBuffer, final DataEventType dml, final Trigger trigger, final TriggerHistory hist,
            final Channel channel, final String tablePrefix, final Table table) {
        ISqlTransaction transaction = null;
        try {
            transaction = platform.getSqlTemplate().startSqlTransaction(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
            createTrigger(sqlBuffer, dml, trigger, hist, channel, tablePrefix, table, transaction);
            transaction.commit();
        } catch (Exception ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    /*
     * Create the configured trigger. The catalog will be changed to the source schema if the source schema is configured.
     */
    @Override
    public void createTrigger(final StringBuilder sqlBuffer, final DataEventType dml, final Trigger trigger, final TriggerHistory hist,
            final Channel channel, final String tablePrefix, final Table table, ISqlTransaction transaction) {
        String previousCatalog = null;
        String sourceCatalogName = table.getCatalog();
        String defaultCatalog = platform.getDefaultCatalog();
        String defaultSchema = platform.getDefaultSchema();
        String triggerSql = triggerTemplate.createTriggerDDL(dml, trigger, hist, channel, tablePrefix, table, defaultCatalog, defaultSchema);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)) {
            try {
                previousCatalog = switchCatalogForTriggerInstall(sourceCatalogName, transaction);
                try {
                    logSql(triggerSql, sqlBuffer);
                    if (sqlBuffer == null) {
                        log.info("Creating {} trigger for {}", hist.getTriggerNameForDmlType(dml), table.getFullyQualifiedTableName());
                        log.debug("Running: {}", triggerSql);
                        transaction.execute(triggerSql);
                    }
                } catch (SqlException ex) {
                    log.info("Failed to create trigger: {}", triggerSql);
                    throw ex;
                }
                postCreateTrigger(transaction, sqlBuffer, dml, trigger, hist, channel, tablePrefix, table);
            } finally {
                if (sourceCatalogName != null && !sourceCatalogName.equalsIgnoreCase(previousCatalog)) {
                    switchCatalogForTriggerInstall(previousCatalog, transaction);
                }
            }
        }
    }

    protected void postCreateTrigger(ISqlTransaction transaction, StringBuilder sqlBuffer, DataEventType dml, Trigger trigger,
            TriggerHistory hist, Channel channel, String tablePrefix, Table table) {
        String postTriggerDml = createPostTriggerDDL(dml, trigger, hist, channel, tablePrefix, table);
        if (StringUtils.isNotBlank(postTriggerDml)) {
            try {
                logSql(postTriggerDml, sqlBuffer);
                if (sqlBuffer == null) {
                    log.debug("Running: {}", postTriggerDml);
                    transaction.execute(postTriggerDml);
                }
            } catch (SqlException ex) {
                log.info("Failed to create post trigger: {}", postTriggerDml);
                throw ex;
            }
        }
    }

    /*
     * Provide the option switch a connection's schema for trigger installation.
     */
    protected String switchCatalogForTriggerInstall(String catalog, ISqlTransaction transaction) {
        return null;
    }

    protected String createPostTriggerDDL(DataEventType dml, Trigger trigger, TriggerHistory hist, Channel channel, String tablePrefix,
            Table table) {
        return triggerTemplate.createPostTriggerDDL(dml, trigger, hist, channel, tablePrefix, table, platform.getDefaultCatalog(),
                platform.getDefaultSchema());
    }

    @Override
    public void createDdlTrigger(final String tablePrefix, StringBuilder sqlBuffer, String triggerName, String runtimeCatalog, String runtimeSchema) {
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)) {
            String triggerSql = triggerTemplate.createDdlTrigger(tablePrefix, runtimeCatalog, runtimeSchema,
                    triggerName);
            if (triggerSql != null) {
                ISqlTransaction transaction = null;
                try {
                    transaction = this.platform.getSqlTemplate().startSqlTransaction(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
                    try {
                        logSql(triggerSql, sqlBuffer);
                        if (sqlBuffer == null) {
                            log.info("Creating DDL trigger " + triggerName);
                            log.debug("Running: {}", triggerSql);
                            transaction.execute(triggerSql);
                        }
                    } catch (SqlException ex) {
                        log.info("Failed to create DDL trigger: {}", triggerSql);
                        throw ex;
                    }
                    postCreateDdlTrigger(transaction, tablePrefix, sqlBuffer, triggerName);
                    transaction.commit();
                } catch (SqlException ex) {
                    if (transaction != null) {
                        transaction.rollback();
                    }
                    throw ex;
                } finally {
                    if (transaction != null) {
                        transaction.close();
                    }
                }
            }
        }
    }

    protected void postCreateDdlTrigger(ISqlTransaction transaction, String tablePrefix, StringBuilder sqlBuffer, String triggerName) {
        String postTriggerDdl = createPostDdlTriggerDDL(tablePrefix, triggerName);
        if (StringUtils.isNotBlank(postTriggerDdl)) {
            try {
                logSql(postTriggerDdl, sqlBuffer);
                if (sqlBuffer == null) {
                    log.debug("Running: {}", postTriggerDdl);
                    transaction.execute(postTriggerDdl);
                }
            } catch (SqlException ex) {
                log.info("Failed to create post DDL trigger: {}", postTriggerDdl);
                throw ex;
            }
        }
    }

    protected String createPostDdlTriggerDDL(String tablePrefix, String triggerName) {
        return triggerTemplate.createPostDdlTriggerDDL(tablePrefix, triggerName);
    }

    @Override
    public String getCreateSymmetricDDL() {
        Database database = readSymmetricSchemaFromXml();
        IDdlBuilder builder = platform.getDdlBuilder();
        return builder.createTables(database, true);
    }

    protected void prefixConfigDatabase(Database targetTables) {
        platform.prefixDatabase(parameterService.getTablePrefix(), targetTables);
    }

    /*
     * @return true if SQL was executed.
     */
    @Override
    public boolean createOrAlterTablesIfNecessary(String... tableNames) {
        try {
            log.info("Checking if SymmetricDS tables need created or altered");
            Database modelFromXml = readSymmetricSchemaFromXml();
            Database modelFromDatabase = readSymmetricSchemaFromDatabase();
            if (tableNames != null && tableNames.length > 0) {
                tableNames = platform.alterCaseToMatchDatabaseDefaultCase(tableNames);
                modelFromXml.removeAllTablesExcept(tableNames);
                modelFromDatabase.removeAllTablesExcept(tableNames);
            }
            IDdlBuilder builder = platform.getDdlBuilder();
            List<IAlterDatabaseInterceptor> alterDatabaseInterceptors = extensionService
                    .getExtensionPointList(IAlterDatabaseInterceptor.class);
            IAlterDatabaseInterceptor[] interceptors = alterDatabaseInterceptors
                    .toArray(new IAlterDatabaseInterceptor[alterDatabaseInterceptors.size()]);
            if (builder.isAlterDatabase(modelFromDatabase, modelFromXml, interceptors)) {
                String delimiter = platform.getDatabaseInfo().getSqlCommandDelimiter();
                ISqlResultsListener resultsListener = new LogSqlResultsListener();
                List<IDatabaseUpgradeListener> databaseUpgradeListeners = extensionService
                        .getExtensionPointList(IDatabaseUpgradeListener.class);
                for (IDatabaseUpgradeListener listener : databaseUpgradeListeners) {
                    String sql = listener.beforeUpgrade(this, this.parameterService.getTablePrefix(), modelFromDatabase, modelFromXml);
                    SqlScript script = new SqlScript(sql, getPlatform().getSqlTemplate(), true, false, false, delimiter, null);
                    script.setListener(resultsListener);
                    script.execute(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
                }
                // The beforeUpgrade() methods may have made changes to the current database, should read again
                modelFromDatabase = readSymmetricSchemaFromDatabase();
                String alterSql = builder.alterDatabase(modelFromDatabase, modelFromXml, interceptors);
                if (isNotBlank(alterSql)) {
                    log.info("There are SymmetricDS tables that needed altered");
                    log.debug("Alter SQL generated: {}", alterSql);
                    ISqlResultsListener resultsInstallListener = resultsListener;
                    List<IDatabaseInstallStatementListener> installListeners = extensionService.getExtensionPointList(IDatabaseInstallStatementListener.class);
                    boolean triggersContainJava = platform.getDatabaseInfo().isTriggersContainJava();
                    if (installListeners != null && installListeners.size() > 0) {
                        int totalStatements = SqlScript.calculateTotalStatements(alterSql, delimiter, triggersContainJava);
                        resultsInstallListener = new LogSqlResultsInstallListener(parameterService.getEngineName(),
                                totalStatements, extensionService.getExtensionPointList(IDatabaseInstallStatementListener.class));
                    }
                    SqlScript script = new SqlScript(alterSql, getPlatform().getSqlTemplate(), true, false, false, triggersContainJava, delimiter, null);
                    script.setListener(resultsInstallListener);
                    script.execute(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
                    for (IDatabaseUpgradeListener listener : databaseUpgradeListeners) {
                        String sql = listener.afterUpgrade(this, this.parameterService.getTablePrefix(), modelFromXml);
                        script = new SqlScript(sql, getPlatform().getSqlTemplate(), true, false, false, delimiter, null);
                        script.setListener(resultsListener);
                        script.execute(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
                    }
                    log.info("Done with auto update of SymmetricDS tables");
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Database readSymmetricSchemaFromXml() {
        try {
            Database database = merge(readDatabaseFromXml("/symmetric-schema.xml"), readDatabaseFromXml("/console-schema.xml"));
            prefixConfigDatabase(database);
            String extraTablesXml = parameterService.getString(ParameterConstants.AUTO_CONFIGURE_EXTRA_TABLES);
            if (StringUtils.isNotBlank(extraTablesXml)) {
                try {
                    database = merge(database, readDatabaseFromXml(extraTablesXml));
                } catch (Exception ex) {
                    log.error("", ex);
                }
            }
            return database;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Database readSymmetricSchemaFromDatabase() {
        return platform.readFromDatabase(readSymmetricSchemaFromXml().getTables());
    }

    protected Database readDatabaseFromXml(String resourceName) throws IOException {
        try {
            return platform.readDatabaseFromXml(resourceName, true);
        } catch (IoException ex) {
            return new Database();
        }
    }

    protected Database merge(Database... databases) {
        Database database = new Database();
        if (databases != null) {
            for (Database db : databases) {
                Table[] tables = db.getTables();
                for (Table table : tables) {
                    database.addTable(table);
                }
            }
        }
        return database;
    }

    @Override
    public IDatabasePlatform getPlatform() {
        return this.platform;
    }

    @Override
    public IDatabasePlatform getTargetPlatform() {
        return targetDialect.getPlatform();
    }

    @Override
    public IDatabasePlatform getTargetPlatform(String tableName) {
        if (tableName.toLowerCase().startsWith(tablePrefixLowerCase)) {
            return platform;
        }
        return targetDialect.getPlatform();
    }

    @Override
    public String getName() {
        if (!this.equals(targetDialect)) {
            return targetDialect.getName();
        }
        return databaseName;
    }

    @Override
    public String getVersion() {
        if (!this.equals(targetDialect)) {
            return targetDialect.getVersion();
        }
        return databaseMajorVersion + "." + databaseMinorVersion;
    }

    @Override
    public int getMajorVersion() {
        if (!this.equals(targetDialect)) {
            return targetDialect.getMajorVersion();
        }
        return databaseMajorVersion;
    }

    @Override
    public int getMinorVersion() {
        if (!this.equals(targetDialect)) {
            return targetDialect.getMinorVersion();
        }
        return databaseMinorVersion;
    }

    @Override
    public String getProductVersion() {
        if (!this.equals(targetDialect)) {
            return targetDialect.getProductVersion();
        }
        return databaseProductVersion;
    }

    @Override
    public boolean supportsTransactionViews() {
        return supportsTransactionViews;
    }

    /*
     * Indicates if this dialect supports subselects in delete statements.
     */
    @Override
    public boolean supportsSubselectsInDelete() {
        return supportsSubselectsInDelete;
    }

    /*
     * Indicates if this dialect supports subselects in update statements.
     */
    @Override
    public boolean supportsSubselectsInUpdate() {
        return supportsSubselectsInUpdate;
    }

    @Override
    public boolean supportsDdlTriggers() {
        return supportsDdlTriggers;
    }

    @Override
    public long insertWithGeneratedKey(String sql, SequenceIdentifier sequenceId) {
        return insertWithGeneratedKey(sql, sequenceId, null, null);
    }

    @Override
    public long insertWithGeneratedKey(final String sql, final SequenceIdentifier identifier, Object... args) {
        return platform.getSqlTemplate().insertWithGeneratedKey(sql, getSequenceKeyName(identifier), getSequenceKeyName(identifier), args,
                null);
    }

    @Override
    public String getSequenceName(SequenceIdentifier identifier) {
        switch (identifier) {
            case REQUEST:
                return parameterService.getTablePrefix() + "_extract_r_st_request_id";
            case DATA:
                return parameterService.getTablePrefix() + "_data_data_id";
            case TRIGGER_HIST:
                return parameterService.getTablePrefix() + "_trigger_his_ger_hist_id";
        }
        return null;
    }

    @Override
    public String getSequenceKeyName(SequenceIdentifier identifier) {
        switch (identifier) {
            case REQUEST:
                return "request_id";
            case DATA:
                return "data_id";
            case TRIGGER_HIST:
                return "trigger_hist_id";
        }
        return null;
    }

    @Override
    public long getCurrentSequenceValue(SequenceIdentifier identifier) {
        return -1;
    }

    @Override
    @Deprecated
    public Column[] orderColumns(String[] columnNames, Table table) {
        Column[] unorderedColumns = table.getColumns();
        Column[] orderedColumns = new Column[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            String name = columnNames[i];
            for (Column column : unorderedColumns) {
                if (column.getName().equalsIgnoreCase(name)) {
                    orderedColumns[i] = column;
                    break;
                }
            }
        }
        return orderedColumns;
    }

    @Override
    @Deprecated
    public void disableSyncTriggers(ISqlTransaction transaction) {
        disableSyncTriggers(transaction, null);
    }

    @Override
    public boolean supportsTransactionId() {
        return false;
    }

    @Override
    public boolean isBlobSyncSupported() {
        return true;
    }

    @Override
    public boolean isClobSyncSupported() {
        return true;
    }

    @Override
    public boolean isTransactionIdOverrideSupported() {
        return true;
    }

    @Override
    public String getEngineName() {
        return parameterService.getEngineName();
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() {
        return true;
    }

    @Override
    public String getInitialLoadTableAlias() {
        return "t";
    }

    @Override
    public String preProcessTriggerSqlClause(String sqlClause) {
        return sqlClause;
    }

    @Override
    public void truncateTable(String tableName) {
        String quote = platform.getDdlBuilder().isDelimitedIdentifierModeOn() ? platform.getDatabaseInfo().getDelimiterToken() : "";
        boolean success = false;
        int tryCount = 5;
        while (!success && tryCount > 0) {
            try {
                Table table = platform.getTableFromCache(tableName, false);
                if (table != null) {
                    platform.getSqlTemplate().update(String.format("truncate table %s%s%s", quote, table.getName(), quote));
                    success = true;
                } else {
                    throw new RuntimeException(String.format("Could not find %s to trunate", tableName));
                }
            } catch (SqlException ex) {
                log.warn("", ex);
                AppUtils.sleep(5000);
                tryCount--;
            }
        }
    }

    @Override
    public boolean areDatabaseTransactionsPendingSince(long time) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getEarliestTransactionStartTime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDatabaseTimeSQL() {
        return platform.scrubSql("select current_timestamp");
    }

    @Override
    public long getDatabaseTime() {
        try {
            Date dateTime = this.platform.getSqlTemplate().queryForObject(getDatabaseTimeSQL(), java.util.Date.class);
            if (dateTime != null) {
                return dateTime.getTime();
            } else {
                return System.currentTimeMillis();
            }
        } catch (Exception ex) {
            log.error("", ex);
            return System.currentTimeMillis();
        }
    }

    @Override
    public String getSourceNodeExpression() {
        return null;
    }

    @Override
    final public String getDataHasChangedCondition(Trigger trigger) {
        if (trigger.isCaptureChangesOnly() || parameterService.is(ParameterConstants.TRIGGER_UPDATE_CAPTURE_CHANGED_DATA_ONLY)) {
            return getDbSpecificDataHasChangedCondition(trigger);
        } else {
            return Constants.ALWAYS_TRUE_CONDITION;
        }
    }

    protected String getDbSpecificDataHasChangedCondition(Trigger trigger) {
        return Constants.ALWAYS_TRUE_CONDITION;
    }

    @Override
    public boolean needsToSelectLobData() {
        return false;
    }

    @Override
    public boolean canGapsOccurInCapturedDataIds() {
        return true;
    }

    @Override
    public String massageDataExtractionSql(String sql, boolean isContainsBigLob) {
        String textColumnExpression = parameterService.getString(ParameterConstants.DATA_EXTRACTOR_TEXT_COLUMN_EXPRESSION);
        if (isNotBlank(textColumnExpression)) {
            sql = sql.replace("d.old_data", textColumnExpression.replace("$(columnName)", "d.old_data"));
            sql = sql.replace("d.row_data", textColumnExpression.replace("$(columnName)", "d.row_data"));
            sql = sql.replace("d.pk_data", textColumnExpression.replace("$(columnName)", "d.pk_data"));
        }
        return sql;
    }

    @Override
    public String getDriverName() {
        if (targetDialect != this) {
            return targetDialect.getDriverName();
        }
        return driverName;
    }

    @Override
    public String getDriverVersion() {
        if (targetDialect != this) {
            return targetDialect.getDriverVersion();
        }
        return driverVersion;
    }

    @Override
    public String massageForLob(String sql, boolean isContainsBigLob) {
        return sql;
    }

    @Override
    public boolean isInitialLoadTwoPassLob(Table table) {
        return false;
    }

    @Override
    public String getInitialLoadTwoPassLobSql(String sql, Table table, boolean isFirstPass) {
        List<Column> columns = table.getLobColumns(this.platform);
        boolean isFirstColumn = true;
        sql = sql == null ? "" : sql;
        String orderBySql = "";
        int index = sql.toUpperCase().indexOf("ORDER BY");
        if (index != -1) {
            orderBySql = " " + sql.substring(index);
            sql = sql.substring(0, index);
        }
        if (columns.size() > 0) {
            if (!sql.equals("")) {
                sql += " and ";
            }
            sql += "(";
        }
        for (Column column : table.getLobColumns(this.platform)) {
            String columnSql = getInitialLoadTwoPassLobLengthSql(column, isFirstPass);
            if (columnSql != null && !columnSql.trim().equals("")) {
                if (isFirstColumn) {
                    isFirstColumn = false;
                } else {
                    if (isFirstPass) {
                        sql += " and ";
                    } else {
                        sql += " or ";
                    }
                }
                sql += columnSql;
            }
        }
        if (columns.size() > 0) {
            sql += ")";
        }
        sql += orderBySql;
        return sql;
    }

    public String getInitialLoadTwoPassLobLengthSql(Column column, boolean isFirstPass) {
        return null;
    }

    @Override
    public boolean escapesTemplatesForDatabaseInserts() {
        return false;
    }

    @Override
    public String getMasterCollation() {
        return parameterService.getString(ParameterConstants.DB_MASTER_COLLATION, "");
    }

    @Override
    public boolean supportsBatchUpdates() {
        return true;
    }

    @Override
    public void cleanupTriggers() {
    }

    @Override
    public AbstractTriggerTemplate getTriggerTemplate() {
        return triggerTemplate;
    }

    protected void close(ISqlTransaction transaction) {
        if (transaction != null) {
            transaction.close();
        }
    }

    @Override
    public String getTablePrefix() {
        return parameterService.getTablePrefix();
    }

    @Override
    public String getTemplateNumberPrecisionSpec() {
        return null;
    }

    @Override
    public int getSqlTypeForIds() {
        return Types.NUMERIC;
    }

    @Override
    public IParameterService getParameterService() {
        return parameterService;
    }

    @Override
    public void setExtensionService(IExtensionService extensionService) {
        this.extensionService = extensionService;
    }

    @Override
    public PermissionType[] getSymTablePermissions() {
        PermissionType[] permissions = { PermissionType.CREATE_TABLE, PermissionType.DROP_TABLE, PermissionType.CREATE_TRIGGER,
                PermissionType.DROP_TRIGGER };
        return permissions;
    }

    @Override
    public ISymmetricDialect getTargetDialect() {
        return targetDialect;
    }

    @Override
    public ISymmetricDialect getTargetDialect(String tableName) {
        if (tableName.toLowerCase().startsWith(tablePrefixLowerCase)) {
            return this;
        }
        return targetDialect;
    }

    @Override
    public void setTargetDialect(ISymmetricDialect targetDialect) {
        this.targetDialect = targetDialect;
    }

    @Override
    public String getSyncTriggersOnIncomingExpression() {
        return Constants.ALWAYS_TRUE_CONDITION;
    }
}
