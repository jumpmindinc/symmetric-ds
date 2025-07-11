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
package org.jumpmind.symmetric.db.postgresql;

import java.sql.Types;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.db.SequenceIdentifier;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.util.FormatUtils;

/*
 * Support for PostgreSQL
 */
public class PostgreSqlSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String TRANSACTION_ID_EXPRESSION = "txid_current()";
    static final String SYNC_TRIGGERS_DISABLED_VARIABLE = "symmetric.triggers_disabled";
    static final String SYNC_NODE_DISABLED_VARIABLE = "symmetric.node_disabled";
    static final String SQL_DROP_FUNCTION = "drop function $(functionName)";
    static final String SQL_FUNCTION_INSTALLED = " select count(*) from information_schema.routines " +
            " where routine_name = '$(functionName)' and specific_schema = '$(defaultSchema)'";
    static final String SQL_SELECT_TRANSACTIONS = "select min(a.xact_start) from pg_stat_activity a join pg_catalog.pg_locks l on l.pid = a.pid  where l.mode = 'RowExclusiveLock'";
    private Boolean supportsTransactionId = null;
    protected String sharedTriggersDisabledFunction;
    protected String sharedNodeDisabledFunction;
    protected String sharedReadLargeObjectFunction;
    protected boolean versionSupportsReplaceTriggers;

    public PostgreSqlSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        versionSupportsReplaceTriggers = databaseMajorVersion >= 14;
        this.triggerTemplate = new PostgreSqlTriggerTemplate(this);
        this.supportsDdlTriggers = databaseMajorVersion > 9 || (databaseMajorVersion == 9 && databaseMinorVersion >= 3);
        if (parameterService.is(ParameterConstants.ROUTING_GAPS_USE_TRANSACTION_VIEW)) {
            try {
                getEarliestTransactionStartTime();
                supportsTransactionViews = true;
                log.info("Enabling use of transaction views for data gap detection.");
            } catch (Exception ex) {
                log.warn("Cannot enable use of transaction views for data gap detection.", ex);
            }
        }
        platform.getDatabaseInfo().setGeneratedColumnsSupported(databaseMajorVersion >= 12);
        platform.getDatabaseInfo().setTriggersCreateOrReplaceSupported(versionSupportsReplaceTriggers);
        sharedTriggersDisabledFunction = this.parameterService.getTablePrefix() + "_triggers_disabled";
        sharedNodeDisabledFunction = this.parameterService.getTablePrefix() + "_node_disabled";
        sharedReadLargeObjectFunction = this.parameterService.getTablePrefix() + "_largeobject";
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        ISqlTransaction transaction = null;
        try {
            transaction = platform.getSqlTemplate().startSqlTransaction();
            enableSyncTriggers(transaction);
        } catch (Exception e) {
            String message = "Please add \"custom_variable_classes = 'symmetric'\" to your postgresql.conf file";
            log.error(message);
            throw new SymmetricException(message, e);
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
        if (!isFunctionInstalled(sharedTriggersDisabledFunction)) {
            String sql = "CREATE OR REPLACE FUNCTION $(functionName)() RETURNS INTEGER AS $$ "
                    + "   DECLARE "
                    + "     triggerDisabled INTEGER; "
                    + "   BEGIN "
                    + "     select current_setting('" + SYNC_TRIGGERS_DISABLED_VARIABLE + "') into triggerDisabled; "
                    + "     return triggerDisabled;    "
                    + "   EXCEPTION WHEN OTHERS THEN   "
                    + "     return 0;  "
                    + "   END; "
                    + "   $$ LANGUAGE plpgsql;";
            install(sql, sharedTriggersDisabledFunction, ddl);
            log.debug("Created shared function {} for tracking when triggers are disabled.", sharedTriggersDisabledFunction);
        }
        if (!isFunctionInstalled(sharedNodeDisabledFunction)) {
            String sql = "CREATE OR REPLACE FUNCTION $(functionName)() RETURNS VARCHAR AS $$ "
                    + "   DECLARE "
                    + "     nodeId VARCHAR(50); "
                    + "   BEGIN "
                    + "     select current_setting('" + SYNC_NODE_DISABLED_VARIABLE
                    + "') into nodeId; "
                    + "     return nodeId; "
                    + "   EXCEPTION WHEN OTHERS THEN "
                    + "     return ''; "
                    + "   END; "
                    + "   $$ LANGUAGE plpgsql;";
            install(sql, sharedNodeDisabledFunction, ddl);
            log.debug("Created shared function {} for tracking when node replication is disabled.", sharedNodeDisabledFunction);
        }
        if (!isFunctionInstalled(sharedReadLargeObjectFunction)) {
            String sql = "CREATE OR REPLACE FUNCTION $(functionName)(objectId oid) RETURNS text AS $$  "
                    + "   DECLARE      "
                    + "     encodedBlob text;    "
                    + "     encodedBlobPage text;      "
                    + "   BEGIN        "
                    + "     encodedBlob := '';   "
                    + "     FOR encodedBlobPage IN SELECT pg_catalog.encode(data, 'escape') "
                    + "     FROM pg_largeobject WHERE loid = objectId ORDER BY pageno LOOP       "
                    + "       encodedBlob := encodedBlob || encodedBlobPage;     "
                    + "     END LOOP;  "
                    + "     RETURN pg_catalog.encode(pg_catalog.decode(encodedBlob, 'escape'), 'base64');    "
                    + "   EXCEPTION WHEN OTHERS THEN   "
                    + "     RETURN ''; "
                    + "   END          "
                    + "   $$ LANGUAGE plpgsql;   ";
            install(sql, sharedReadLargeObjectFunction, ddl);
            log.info("Created shared function {} for processing LOBs", sharedReadLargeObjectFunction);
        }
        if (parameterService.is(ParameterConstants.POSTGRES_TRIGGER_CAPTURE_TRUNCATE)) {
            if (supportsReplaceTriggers()) {
                createSharedTruncateCaptureFunctions(ddl);
            } else {
                log.warn("SymmetricDS does not support truncate table event triggers on PostgreSQL older than version 14!");
            }
        } else {
            dropSharedTruncateCaptureFunction();
        }
    }

    public void createSharedTruncateCaptureFunctions(StringBuilder ddl) {
        PostgreSqlTriggerTemplate templatesMap = (PostgreSqlTriggerTemplate) this.triggerTemplate;
        String sharedTruncateEventFunction = templatesMap.getTruncateSharedFunctionName();
        if (isFunctionInstalled(sharedTruncateEventFunction)) {
            log.info("Detected shared function {} for capturing table truncate events.", sharedTruncateEventFunction);
            return;
        }
        String sql = templatesMap.getEntry(sharedTruncateEventFunction);
        String defaultSchema = platform.getDefaultSchema();
        if (!StringUtils.isBlank(defaultSchema)) {
            defaultSchema += ".";
        }
        sql = FormatUtils.replace("prefixName", this.parameterService.getTablePrefix(), sql);
        sql = FormatUtils.replace("replicationEnabledCondition", getSyncTriggersExpression(), sql);
        sql = FormatUtils.replace("defaultSchema", defaultSchema, sql);
        install(sql, sharedReadLargeObjectFunction, ddl);
        log.info("Created shared function {} for capturing table truncate events.", sharedTruncateEventFunction);
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        if (isFunctionInstalled(sharedTriggersDisabledFunction)) {
            uninstall(SQL_DROP_FUNCTION + "() cascade", sharedTriggersDisabledFunction);
        }
        if (isFunctionInstalled(sharedNodeDisabledFunction)) {
            uninstall(SQL_DROP_FUNCTION + "() cascade", sharedNodeDisabledFunction);
        }
        String largeObjects = this.parameterService.getTablePrefix() + "_" + "largeobject";
        if (isFunctionInstalled(largeObjects)) {
            uninstall(SQL_DROP_FUNCTION + "(objectId oid) cascade", largeObjects);
        }
        dropSharedTruncateCaptureFunction();
    }

    public void dropSharedTruncateCaptureFunction() {
        PostgreSqlTriggerTemplate templatesMap = (PostgreSqlTriggerTemplate) this.triggerTemplate;
        String sharedTruncateEventFunction = templatesMap.getTruncateSharedFunctionName();
        if (isFunctionInstalled(sharedTruncateEventFunction)) {
            uninstall(SQL_DROP_FUNCTION + "() cascade", sharedTruncateEventFunction);
            log.info("Removed shared function for capturing table truncate events={}", sharedTruncateEventFunction);
        }
    }

    @Override
    public boolean requiresAutoCommitFalseToSetFetchSize() {
        return true;
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalogName, String schema, String tableName, String triggerName) {
        if (platform.isMetadataIgnoreCase()) {
            return platform.getSqlTemplate().queryForInt(
                    "select count(*) from information_schema.triggers where trigger_name = ? "
                            + "and lower(event_object_table) = lower(?) and trigger_schema = ?",
                    new Object[] { triggerName.toLowerCase(), tableName, schema == null ? platform.getDefaultSchema() : schema }) > 0;
        } else {
            return platform.getSqlTemplate().queryForInt(
                    "select count(*) from information_schema.triggers where trigger_name = ? "
                            + "and event_object_table = ? and trigger_schema = ?",
                    new Object[] { triggerName.toLowerCase(), tableName, schema == null ? platform.getDefaultSchema() : schema }) > 0;
        }
    }

    @Override
    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName,
            String triggerName, String tableName, ISqlTransaction transaction) {
        Table table = platform.getTableFromCache(catalogName, schemaName, tableName, false);
        if (table != null) {
            String quoteChar = platform.getDatabaseInfo().getDelimiterToken();
            String schemaPrefix = table.getSchema() == null ? ""
                    : (quoteChar + table.getSchema()
                            + quoteChar + ".");
            final String dropSql = "drop trigger IF EXISTS " + triggerName + " on " + schemaPrefix + quoteChar
                    + table.getName() + quoteChar;
            logSql(dropSql, sqlBuffer);
            final String dropFunction = "drop function IF EXISTS " + schemaPrefix + "f" + triggerName
                    + "() cascade";
            logSql(dropFunction, sqlBuffer);
            if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
                log.info("Dropping {} trigger for {}", triggerName, Table.getFullyQualifiedTableName(catalogName, schemaName, tableName));
                transaction.execute(dropSql);
                transaction.execute(dropFunction);
            }
        }
    }

    @Override
    public boolean doesDdlTriggerExist(final String catalogName, final String schema, final String triggerName) {
        boolean dropTriggerExists = platform.getSqlTemplate().queryForInt("select count(*) from pg_event_trigger where evtname = ?",
                new Object[] { triggerName.toLowerCase() + "_drop" }) > 0;
        return dropTriggerExists && platform.getSqlTemplate().queryForInt("select count(*) from pg_event_trigger where evtname = ?",
                new Object[] { triggerName.toLowerCase() }) > 0;
    }

    @Override
    public void removeDdlTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName) {
        final String dropSql = "drop event trigger IF EXISTS " + triggerName;
        logSql(dropSql, sqlBuffer);
        logSql(dropSql + "_drop", sqlBuffer);
        final String dropFunction = "drop function IF EXISTS f" + triggerName + "() cascade";
        logSql(dropFunction, sqlBuffer);
        logSql("drop function IF EXISTS f" + triggerName + "_drop() cascade", sqlBuffer);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
            log.info("Removing DDL trigger " + triggerName);
            try {
                platform.getSqlTemplate().update(dropSql);
                platform.getSqlTemplate().update(dropSql + "_drop");
            } catch (Exception e) {
                log.warn("Tried to remove DDL trigger using: {} and failed because: {}", dropSql, e.getMessage());
            }
            try {
                platform.getSqlTemplate().update(dropFunction);
                platform.getSqlTemplate().update("drop function IF EXISTS f" + triggerName + "_drop() cascade");
            } catch (Exception e) {
                log.warn("Tried to remove DDL trigger function using: {} and failed because: {}", dropFunction, e.getMessage());
            }
        }
    }

    @Override
    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("select set_config('" + SYNC_TRIGGERS_DISABLED_VARIABLE + "', '1', false)");
        if (nodeId == null) {
            nodeId = "";
        }
        transaction.prepareAndExecute("select set_config('" + SYNC_NODE_DISABLED_VARIABLE + "', '" + nodeId + "', false)");
    }

    @Override
    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("select set_config('" + SYNC_TRIGGERS_DISABLED_VARIABLE
                + "', '', false)");
        transaction.prepareAndExecute("select set_config('" + SYNC_NODE_DISABLED_VARIABLE
                + "', '', false)");
    }

    @Override
    public String getSyncTriggersExpression() {
        return "$(defaultSchema)" + parameterService.getTablePrefix() + "_triggers_disabled() = 0";
    }

    @Override
    public String getSyncTriggersOnIncomingExpression() {
        return "$(defaultSchema)" + parameterService.getTablePrefix() + "_triggers_disabled() != 2";
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema, Trigger trigger) {
        if (supportsTransactionId()) {
            return TRANSACTION_ID_EXPRESSION;
        } else {
            return "null";
        }
    }

    @Override
    public String getTransactionId(ISqlTransaction transaction) {
        if (supportsTransactionId()) {
            List<String> list = transaction.query("select " + TRANSACTION_ID_EXPRESSION, new StringMapper(), null, null);
            if (list != null && list.size() > 0) {
                return list.get(0);
            }
        }
        return null;
    }

    @Override
    public boolean supportsTransactionId() {
        if (supportsTransactionId == null) {
            if (platform
                    .getSqlTemplate()
                    .queryForInt(
                            "select count(*) from information_schema.routines where routine_name='txid_current'") > 0) {
                supportsTransactionId = true;
            } else {
                supportsTransactionId = false;
            }
        }
        return supportsTransactionId;
    }

    @Override
    public final Date getEarliestTransactionStartTime() {
        Date minStartTime = platform.getSqlTemplate().queryForObject(SQL_SELECT_TRANSACTIONS, Date.class);
        if (minStartTime == null) {
            minStartTime = new Date();
        }
        return minStartTime;
    }

    @Override
    public boolean supportsTransactionViews() {
        return supportsTransactionViews
                && parameterService.is(ParameterConstants.ROUTING_GAPS_USE_TRANSACTION_VIEW);
    }

    @Override
    public void cleanDatabase() {
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.BASE64;
    }

    @Override
    public int getSqlTypeForIds() {
        return Types.BIGINT;
    }

    @Override
    protected String getDbSpecificDataHasChangedCondition(Trigger trigger) {
        return "var_old_data is null or var_row_data != var_old_data";
    }

    @Override
    public long getCurrentSequenceValue(SequenceIdentifier identifier) {
        return platform.getSqlTemplate().queryForLong("select last_value from " + getSequenceName(identifier) + "_seq");
    }

    public boolean isFunctionInstalled(String functionName) {
        return installed(SQL_FUNCTION_INSTALLED, functionName);
    }

    public boolean supportsReplaceTriggers() {
        return versionSupportsReplaceTriggers;
    }
}
