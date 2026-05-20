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
package org.jumpmind.symmetric.db.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlUtils;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.db.SequenceIdentifier;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.jumpmind.util.FormatUtils;

public class MySqlSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    private static final String PRE_5_1_23 = "_pre_5_1_23";
    private static final String PRE_5_7_6 = "_pre_5_7_6";
    private static final String POST_5_7_6 = "_post_5_7_6";
    private static final String TRANSACTION_ID = "transaction_id";
    static final String SYNC_TRIGGERS_DISABLED_USER_VARIABLE = "@sync_triggers_disabled";
    static final String SYNC_TRIGGERS_DISABLED_NODE_VARIABLE = "@sync_node_disabled";
    static final String SQL_DROP_FUNCTION = "drop function $(functionName)";
    static final String SQL_FUNCTION_INSTALLED = "select count(*) from information_schema.routines where routine_name='$(functionName)' and routine_schema in (select database())";
    static final String SQL_FUNCTION_EQUALS = "select count(*) from information_schema.routines where routine_name='$(functionName)' and routine_schema in (select database())"
            + " and trim(routine_definition)=trim('$(functionBody)')";
    private String functionTemplateKeySuffix = null;
    private boolean isConvertZeroDateToNull;
    private String characterSet;

    public MySqlSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.parameterService = parameterService;
        String version = getProductVersion();
        if (!Version.isOlderThanVersion(version, "5.1.5") && !platform.getName().equals(DatabaseNamesConstants.SINGLE_STORE)) {
            String defaultEngine = platform.getSqlTemplate().queryForString("select engine from information_schema.engines where support='DEFAULT';");
            if (!Strings.CI.equals(defaultEngine, "innodb")) {
                String message = "Please ensure that the default storage engine is set to InnoDB";
                throw new SymmetricException(message);
            }
        }
        if (parameterService.getString(DataSourceProperties.DB_POOL_URL).contains("zeroDateTimeBehavior=convertToNull")) {
            try {
                String sqlMode = platform.getSqlTemplate().queryForString("select @@sql_mode");
                isConvertZeroDateToNull = sqlMode == null || (!sqlMode.contains("NO_ZERO_DATE") && !sqlMode.contains("NO_ZERO_IN_DATE"));
                if (isConvertZeroDateToNull) {
                    log.info("Zero dates will be converted to null");
                }
            } catch (Exception e) {
                log.warn("Cannot convert zero dates to null because unable to verify sql_mode: {}", e.getMessage());
            }
        }
        if (Version.isOlderThanVersion(version, "5.1.23")) {
            this.functionTemplateKeySuffix = PRE_5_1_23;
        } else if (Version.isOlderThanVersion(version, "5.7.6")) {
            this.functionTemplateKeySuffix = PRE_5_7_6;
        } else {
            this.functionTemplateKeySuffix = POST_5_7_6;
        }
        characterSet = parameterService.getString(ParameterConstants.DB_MASTER_COLLATION, Version.isOlderThanVersion(getProductVersion(), "5.5.3") ? "utf8"
                : "utf8mb4");
        this.triggerTemplate = new MySqlTriggerTemplate(this, isConvertZeroDateToNull, characterSet);
        platform.getDatabaseInfo().setGeneratedColumnsSupported(!Version.isOlderThanVersion(version, "5.7.0"));
        platform.getDatabaseInfo().setExpressionsAsDefaultValuesSupported(!Version.isOlderThanVersion(version, "8.0.13"));
    }

    @Override
    public boolean supportsTransactionId() {
        return true;
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String function = null;
        String functionBody = null;
        String sql = null;
        if (this.functionTemplateKeySuffix.equals(PRE_5_1_23)) {
            function = this.parameterService.getTablePrefix() + "_" + TRANSACTION_ID + this.functionTemplateKeySuffix;
            functionBody = " begin                                                        " +
                    " declare comm_name varchar(50);                                                        " +
                    " declare comm_value varchar(50);                                                        " +
                    " declare comm_cur cursor for show status like 'Com_commit';                                                        " +
                    " if @@autocommit = 0 then                                                        " +
                    " open comm_cur;                                                        " +
                    " fetch comm_cur into comm_name, comm_value;                                                        " +
                    " close comm_cur;                                                        " +
                    " return concat(concat(connection_id(), '.'), comm_value);                                                        " +
                    " else                                                        " +
                    " return null;                                                        " +
                    " end if;                                                          " +
                    " end                                                             ";
            sql = "create function $(functionName)()                                                        " +
                    " returns varchar(50) NOT DETERMINISTIC READS SQL DATA                                                        " +
                    functionBody;
        } else if (this.functionTemplateKeySuffix.equals(PRE_5_7_6)) {
            function = this.parameterService.getTablePrefix() + "_" + TRANSACTION_ID + this.functionTemplateKeySuffix;
            functionBody = " begin                                                                                                                          \n"
                    +
                    "    declare comm_value varchar(50);                                                                                             \n" +
                    "    declare comm_cur cursor for select VARIABLE_VALUE from INFORMATION_SCHEMA.SESSION_STATUS where VARIABLE_NAME='COM_COMMIT';  \n" +
                    "    open comm_cur;                                                                                                              \n" +
                    "    fetch comm_cur into comm_value;                                                                                             \n" +
                    "    close comm_cur;                                                                                                             \n" +
                    "    return concat(concat(connection_id(), '.'), comm_value);                                                                    \n" +
                    " end                                                                                                                            ";
            sql = "create function $(functionName)()                                                                                          \n" +
                    " returns varchar(50) NOT DETERMINISTIC READS SQL DATA                                                                           \n" +
                    functionBody;
        } else {
            function = this.parameterService.getTablePrefix() + "_" + TRANSACTION_ID + this.functionTemplateKeySuffix;
            functionBody = " begin                                                                                                                           \n"
                    +
                    "    declare done int default 0;                                                                                                  \n" +
                    "    declare comm_value varchar(50);                                                                                              \n" +
                    "    declare comm_cur cursor for select TRX_ID from INFORMATION_SCHEMA.INNODB_TRX where TRX_MYSQL_THREAD_ID = CONNECTION_ID();    \n" +
                    "    declare continue handler for not found set done = 1;                                                                         \n" +
                    "    open comm_cur;                                                                                                               \n" +
                    "    fetch comm_cur into comm_value;                                                                                              \n" +
                    "    close comm_cur;                                                                                                              \n" +
                    "    return concat(concat(connection_id(), '.'), comm_value);                                                                     \n" +
                    " end                                                                                                                             ";
            sql = "create function $(functionName)()                                                                                           \n" +
                    " returns varchar(50) NOT DETERMINISTIC READS SQL DATA                                                                            \n" +
                    functionBody;
        }
        if (!functionEquals(SQL_FUNCTION_EQUALS, function, functionBody)) {
            if (installed(SQL_FUNCTION_INSTALLED, function)) {
                uninstall(SQL_DROP_FUNCTION, function, ddl);
            }
            install(sql, function, ddl);
        }
    }

    private boolean functionEquals(String sqlFunctionEquals, String functionName, String functionBody) {
        return platform.getSqlTemplate().queryForInt(replaceTokens(sqlFunctionEquals, functionName, functionBody)) > 0;
    }

    private String replaceTokens(String sql, String objectName, String functionBody) {
        String ddl = super.replaceTokens(sql, objectName);
        ddl = FormatUtils.replace("functionBody", Strings.CS.replace(functionBody, "'", "''"), ddl);
        return ddl;
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String function = this.parameterService.getTablePrefix() + "_" + TRANSACTION_ID + this.functionTemplateKeySuffix;
        if (installed(SQL_FUNCTION_INSTALLED, function)) {
            uninstall(SQL_DROP_FUNCTION, function);
        }
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalog, String schema, String tableName,
            String triggerName) {
        catalog = catalog == null ? (platform.getDefaultCatalog() == null ? null : platform.getDefaultCatalog()) : catalog;
        String checkCatalogSql = (catalog != null && catalog.length() > 0) ? " and trigger_schema='" + SqlUtils.sanitizeIdentifier(catalog) + "'" : "";
        return platform.getSqlTemplate().queryForInt(
                "select count(*) from information_schema.triggers where trigger_name like ? and event_object_table like ?"
                        + checkCatalogSql, new Object[] { triggerName, tableName }) > 0;
    }

    @Override
    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName,
            String triggerName, String tableName, ISqlTransaction transaction) {
        String quote = platform.getDatabaseInfo().getDelimiterToken();
        String catalogPrefix = StringUtils.isBlank(catalogName) ? "" : (quote + SqlUtils.sanitizeIdentifier(catalogName) + quote + ".");
        String sql = "drop trigger if exists " + catalogPrefix + SqlUtils.sanitizeIdentifier(triggerName);
        if (Version.isOlderThanVersion(getProductVersion(), "5.0.32")) {
            sql = "drop trigger " + catalogPrefix + SqlUtils.sanitizeIdentifier(triggerName);
        }
        logSql(sql, sqlBuffer);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
            log.info("Dropping {} trigger for {}", triggerName, Table.getFullyQualifiedTableName(catalogName, schemaName, tableName));
            transaction.execute(sql);
        }
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("set " + SYNC_TRIGGERS_DISABLED_USER_VARIABLE + "=1");
        if (nodeId != null) {
            transaction
                    .prepareAndExecute("set " + SYNC_TRIGGERS_DISABLED_NODE_VARIABLE + "='" + nodeId + "'");
        }
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("set " + SYNC_TRIGGERS_DISABLED_USER_VARIABLE + "=null");
        transaction.prepareAndExecute("set " + SYNC_TRIGGERS_DISABLED_NODE_VARIABLE + "=null");
    }

    public String getSyncTriggersExpression() {
        return SYNC_TRIGGERS_DISABLED_USER_VARIABLE + " is null";
    }

    public String getSyncTriggersOnIncomingExpression() {
        return "coalesce(" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE + ", 0) != 2";
    }

    private final String getTransactionFunctionName() {
        return SymmetricUtils.quote(this, platform.getDefaultCatalog()) + "." + parameterService.getTablePrefix() + "_"
                + TRANSACTION_ID + this.functionTemplateKeySuffix;
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema,
            Trigger trigger) {
        return getTransactionFunctionName() + "()";
    }

    @Override
    public String getTransactionId(ISqlTransaction transaction) {
        String xid = null;
        if (supportsTransactionId()) {
            List<String> list = transaction.query("select @@gtid_executed", new StringMapper(), null, null);
            if (list != null && list.size() > 0) {
                String gtid = list.get(0);
                int index = gtid.indexOf(':');
                if (index != -1) {
                    xid = gtid.substring(index + 1);
                }
            }
        }
        return xid;
    }

    public void cleanDatabase() {
    }

    @Override
    protected String switchCatalogForTriggerInstall(String catalog, ISqlTransaction transaction) {
        if (catalog != null) {
            Connection c = ((JdbcSqlTransaction) transaction).getConnection();
            String previousCatalog;
            try {
                previousCatalog = c.getCatalog();
                c.setCatalog(catalog);
                return previousCatalog;
            } catch (SQLException e) {
                throw new SqlException(e);
            }
        } else {
            return null;
        }
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.HEX;
    }

    @Override
    protected String getDbSpecificDataHasChangedCondition(Trigger trigger) {
        return "var_old_data is null or var_row_data != var_old_data";
    }

    @Override
    public long getCurrentSequenceValue(SequenceIdentifier identifier) {
        return platform.getSqlTemplate().queryForLong("select auto_increment from information_schema.tables where table_schema = ? and table_name = ?",
                platform.getDefaultCatalog(), parameterService.getTablePrefix() + "_" + identifier);
    }

    @Override
    public PermissionType[] getSymTablePermissions() {
        PermissionType[] permissions = { PermissionType.CREATE_TABLE, PermissionType.DROP_TABLE, PermissionType.CREATE_TRIGGER, PermissionType.DROP_TRIGGER,
                PermissionType.CREATE_ROUTINE };
        return permissions;
    }

    @Override
    public Database readSymmetricSchemaFromXml() {
        Database database = super.readSymmetricSchemaFromXml();
        if (Version.isOlderThanVersion(getProductVersion(), "5.5")) {
            String prefix = parameterService.getTablePrefix() + "_";
            reconfigureTableColumn(database, prefix, TableConstants.SYM_FILE_SNAPSHOT, "relative_dir", "55");
            reconfigureTableColumn(database, prefix, TableConstants.SYM_FILE_SNAPSHOT, "file_name", "55");
            reconfigureTableColumn(database, prefix, TableConstants.SYM_FILE_INCOMING, "relative_dir", "55");
            reconfigureTableColumn(database, prefix, TableConstants.SYM_FILE_INCOMING, "file_name", "55");
        }
        return database;
    }

    protected void reconfigureTableColumn(Database database, String prefix, String tableName, String columnName, String size) {
        Table table = database.findTable(prefix + tableName);
        if (table != null) {
            Column column = table.findColumn(columnName);
            if (column != null) {
                column.setSize(size);
            }
        }
    }
}
