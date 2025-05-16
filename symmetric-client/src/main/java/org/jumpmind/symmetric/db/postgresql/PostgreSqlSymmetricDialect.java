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
    static final String SQL_FUNCTION_INSTALLED_FOR_SCHEMA = " select count(*) from information_schema.routines " +
            " where routine_name = '$(functionName)' and specific_schema = ";
    protected static String sharedTriggersDisabledFunction;
    protected static String sharedNodeDisabledFunction;
    protected static String sharedReadLargeObjectFunction;
    protected static String sharedTruncateEventFunction;
    static final String sharedTruncateEventFunctionTemplate = "CREATE OR REPLACE FUNCTION $(functionName)() RETURNS TRIGGER AS $$ "
            + "DECLARE "
            + "    command_text VARCHAR(3000); "
            + "    argumentNo int:=0; "
            + "    tableSchema varchar(255); "
            + "    tableName varchar(255); "
            + "    channelId varchar(128); "
            + "    triggerId varchar(128); "
            + "    histId int:=0; "
            + "BEGIN  "
            // -- TODO: if replication is disabled then RETURN NEW; END if;
            + "    tableSchema := TG_TABLE_SCHEMA; "
            + "    tableName := TG_TABLE_NAME;  "
            + "    if (tableSchema IS NOT NULL and LENGTH(tableSchema)>0) then "
            + "        command_text := 'delimiter $; TRUNCATE TABLE ' || tableSchema || '.'|| tableName; "
            + "    else "
            + "        command_text := 'delimiter $; TRUNCATE TABLE ' || tableName; "
            + "    end if; "
            + "    if(TG_NARGS > 1) THEN "
            + "        for argumentNo in 1..TG_NARGS-1 LOOP  "
            + "            command_text := command_text || ' ' ||  TG_ARGV[argumentNo ]; "
            + "            argumentNo := argumentNo+1; "
            + "        END LOOP; "
            + "    end if; "
            + "    if (tableName like '%.%') then "
            + "           tableSchema := split_part(tableName, '.', 1); "
            + "           tableName := split_part(tableName, '.', 2); "
            + "    end if; "
            + "    tableSchema := trim(both '\\\"' from tableSchema); "
            + "    tableName := trim(both '\\\"' from tableName); "
            + "    select tr.trigger_id, tr.channel_id, max(th.trigger_hist_id) "
            + "      into triggerId, channelId, histId "
            + "      from $(defaultSchema).$(prefixName)_trigger tr  "
            + "      join $(defaultSchema).$(prefixName)_trigger_hist th "
            + "        on th.trigger_id = tr.trigger_id and th.inactive_time is null "
            + "      where tr.source_schema_name = lower(tableSchema) and tr.source_table_name = lower(tableName) "
            + "      group by tr.trigger_id, tr.channel_id "
            + "      limit 1; "
            + "    if (channelId is null) then  "
            + "        RAISE EXCEPTION 'Unable to capture TRUNCATE event because SymmetricDS configuration is incomplete (channel is NULL)! Table=%', tableName "
            + "            USING MESSAGE = 'Run SyncTriggers job in SymmetricDS (Disabling this trigger will cause data to be out-of-sync!)'; "
            + "        RETURN NEW;  "
            + "    end if; "
            + "    INSERT INTO $(defaultSchema).$(prefixName)_data(table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, transaction_id, create_time) "
            + "      VALUES (tableName, 'S', histId, command_text, channelId, 'source', TO_CHAR(CURRENT_TIMESTAMP, 'truncate-YYYY-MM-DD')||'T'||TO_CHAR(CURRENT_TIMESTAMP, 'HH24:MI:MSOF'),  CURRENT_TIMESTAMP); "
            + "    RAISE NOTICE 'Detected truncate of the % table; command_text=%; %, TG_NARGS=%; channel=%', tableName, command_text, CURRENT_TIMESTAMP, TG_NARGS, channelId; "
            + "    RETURN NEW;  "
            + "END $$ LANGUAGE plpgsql;";

    public PostgreSqlSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
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
        sharedTriggersDisabledFunction = this.parameterService.getTablePrefix() + "_triggers_disabled";
        sharedNodeDisabledFunction = this.parameterService.getTablePrefix() + "_node_disabled";
        sharedReadLargeObjectFunction = this.parameterService.getTablePrefix() + "_largeobject";
        sharedTruncateEventFunction = "f" + this.parameterService.getTablePrefix() + "_on_truncate_table_confgured_for_replication";
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
        if (!installed(SQL_FUNCTION_INSTALLED, sharedTriggersDisabledFunction)) {
            String sql = "CREATE or REPLACE FUNCTION $(functionName)() RETURNS INTEGER AS $$ "
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
        if (!installed(SQL_FUNCTION_INSTALLED, sharedNodeDisabledFunction)) {
            String sql = "CREATE or REPLACE FUNCTION $(functionName)() RETURNS VARCHAR AS $$ "
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
        if (!installed(SQL_FUNCTION_INSTALLED, sharedReadLargeObjectFunction)) {
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
        createSharedTruncateCaptureFunctions(ddl);
    }

    public String[] getAllSchemas() {
        String[] schemas = {};
        String schemaListDelimiter = ";";
        String getAllSchemasAvailable = "select STRING_AGG(source_schema_name, '" + schemaListDelimiter + "') as schemas from"
                + "( select distinct tr.source_schema_name"
                + "  from " + this.parameterService.getTablePrefix() + "_trigger as tr"
                + "  join information_schema.schemata as sc on sc.schema_name = tr.source_schema_name)";
        String schemaList = platform.getSqlTemplate().queryForString(getAllSchemasAvailable);
        if (schemaList == null || schemaList.length() < 1) {
            return schemas;
        }
        schemas = schemaList.split(schemaListDelimiter);
        return schemas;
    }

    public void createSharedTruncateCaptureFunctions(StringBuilder ddl) {
        if (!parameterService.is(ParameterConstants.TRIGGER_CAPTURE_DDL_CHANGES)) {
            log.debug("Skipped creating shared function {} because {} parameter is off.", sharedTruncateEventFunction,
                    ParameterConstants.TRIGGER_CAPTURE_DDL_CHANGES);
            return;
        }

        try {
            String[] schemas = getAllSchemas();
            if (schemas == null || schemas.length < 1) {
                log.warn(
                        "Skipped creating shared function for monitoring truncate events because there are no schemas with tables configured for replication.");
                return;
            }
            for (String schema : schemas) {
                if (schema == null || schema.length() < 1) {
                    continue;
                }
                if (!installed(SQL_FUNCTION_INSTALLED_FOR_SCHEMA + "'" + schema + "'", sharedTruncateEventFunction)) {
                    String currentSchemaSharedTruncateEventFunction = "\"" + schema + "\".\"" + sharedTruncateEventFunction + "\"";
                    install(sharedTruncateEventFunctionTemplate, currentSchemaSharedTruncateEventFunction, ddl);
                    log.info(
                            "Created shared function {} for monitoring truncate events in schema {}", currentSchemaSharedTruncateEventFunction,
                            schema);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to create shared function for monitoring truncate events in schemas with tables configured for replication.", ex);
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        if (installed(SQL_FUNCTION_INSTALLED, sharedTriggersDisabledFunction)) {
            uninstall(SQL_DROP_FUNCTION + "() cascade", sharedTriggersDisabledFunction);
        }
        if (installed(SQL_FUNCTION_INSTALLED, sharedNodeDisabledFunction)) {
            uninstall(SQL_DROP_FUNCTION + "() cascade", sharedNodeDisabledFunction);
        }
        String largeObjects = this.parameterService.getTablePrefix() + "_" + "largeobject";
        if (installed(SQL_FUNCTION_INSTALLED, largeObjects)) {
            uninstall(SQL_DROP_FUNCTION + "(objectId oid) cascade", largeObjects);
        }
        dropSharedTruncateCaptureFunctions();
    }

    public void dropSharedTruncateCaptureFunctions() {
        if (!parameterService.is(ParameterConstants.TRIGGER_CAPTURE_DDL_CHANGES)) {
            log.debug("Skipped removing shared function {} because {} parameter is off.", sharedTruncateEventFunction,
                    ParameterConstants.TRIGGER_CAPTURE_DDL_CHANGES);
            return;
        }
        try {
            String[] schemas = getAllSchemas();
            if (schemas == null || schemas.length < 1) {
                return;
            }
            for (String schema : schemas) {
                if (schema == null || schema.length() < 1) {
                    continue;
                }
                if (installed(SQL_FUNCTION_INSTALLED_FOR_SCHEMA + "'" + schema + "'", sharedTruncateEventFunction)) {
                    String currentSchemaSharedTruncateEventFunction = "\"" + schema + "\".\"" + sharedTruncateEventFunction + "\"";
                    uninstall(SQL_DROP_FUNCTION + "() cascade", currentSchemaSharedTruncateEventFunction);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to check shared function for monitoring truncate events in schemas with tables configured for replication.", ex);
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
}
