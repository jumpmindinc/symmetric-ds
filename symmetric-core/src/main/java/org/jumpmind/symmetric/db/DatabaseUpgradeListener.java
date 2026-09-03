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

import java.io.IOException;
import java.sql.Types;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.AddPrimaryKeyChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.ColumnSizeChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.IModelChange;
import org.jumpmind.db.alter.PrimaryKeyChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.RemovePrimaryKeyChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IAlterDatabaseInterceptor;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.MultiInstanceofPredicate;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.ext.IDatabaseUpgradeListener;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseUpgradeListener implements IDatabaseUpgradeListener, ISymmetricEngineAware, IBuiltInExtensionPoint {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ISymmetricEngine engine;
    protected boolean isUpgradeFromPre38;
    protected boolean isUpgradeFrom38;
    protected boolean isUpgradeFromPre3125;
    protected boolean isUpgradeFromPre314;
    protected boolean isUpgradeFromPre315;
    protected boolean isUpgradeFromPre316;
    protected boolean isUpgradeFromPre317;

    @Override
    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @Override
    public String beforeUpgrade(ISymmetricDialect symmetricDialect, String tablePrefix, Database currentModel, Database desiredModel)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        ISqlTemplate sqlTemplate = engine.getSqlTemplate();
        isUpgradeFromPre38 = isUpgradeFromPre38(tablePrefix, currentModel, desiredModel);
        if (isUpgradeFromPre38) {
            beforeUpgradeFromPre3_8(tablePrefix, currentModel, sqlTemplate);
        }
        if (isUpgradeFromPre310(tablePrefix, currentModel, desiredModel)) {
            beforeUpgradeFromPre3_10(tablePrefix, currentModel, sqlTemplate);
        }
        if (isUpgradeFromPre311(tablePrefix, currentModel, desiredModel)) {
            beforeUpgradeFromPre3_11(tablePrefix);
        }
        if (isUpgradeFromPre312(tablePrefix, currentModel, desiredModel)) {
            beforeUpgradeFromPre3_12(tablePrefix, currentModel, sqlTemplate);
        }
        if (isUpgradeFromPre3125(tablePrefix, currentModel, desiredModel)) {
            isUpgradeFromPre3125 = true;
        }
        isUpgradeFromPre314 = isUpgradeFromPre314(tablePrefix, currentModel, desiredModel);
        fixInformixTriggerLongVarcharColumns(tablePrefix, desiredModel);
        if (isUpgradeFromPre315(tablePrefix, currentModel)) {
            isUpgradeFromPre315 = true;
        }
        if (isUpgradeFromPre315) {
            beforeUpgradeFromPre3_15(tablePrefix, currentModel, sqlTemplate);
        }
        isUpgradeFromPre316 = isUpgradeFromPre316(tablePrefix, currentModel);
        if (isUpgradeFromPre316) {
            beforeUpgradeFromPre3_16(tablePrefix, currentModel, sqlTemplate);
        }
        isUpgradeFromPre317 = isUpgradeFromPre317(tablePrefix, currentModel);
        if (isUpgradeFromPre317) {
            beforeUpgradeFromPre3_17(tablePrefix, currentModel, sqlTemplate);
        }
        // Leave this last in the sequence of steps to make sure to capture any DML changes done before this
        if (engine.getParameterService().is(ParameterConstants.AUTO_SYNC_TRIGGERS) &&
                currentModel.getTableCount() > 0 && currentModel.findTable(tablePrefix + "_" + TableConstants.SYM_TRIGGER_HIST) != null) {
            dropSymTriggersIfNecessary(currentModel, desiredModel);
        }
        return sb.toString();
    }

    protected boolean dropTableDueToUpgrade(Table table, Database currentModel, ISqlTemplate sqlTemplate) {
        if (table == null) {
            return false;
        }
        dropTriggers(currentModel, table.getName());
        try {
            log.info("Per upgrade process, dropping table: {}", table.getName());
            sqlTemplate.update("drop table " + table.getName());
            return true;
        } catch (Exception e) {
            log.warn("Unable to drop table {} during upgrade process because: {}", table.getName(), e.getMessage());
        }
        return false;
    }

    protected boolean dropTables(String[] tableNames, Database currentModel, ISqlTemplate sqlTemplate) {
        boolean success = true;
        for (String tableName : tableNames) {
            Table table = currentModel.findTable(tableName);
            if (table != null) {
                success &= dropTableDueToUpgrade(table, currentModel, sqlTemplate);
            }
        }
        return success;
    }

    protected boolean truncateTableDueToUpgrade(Table table, ISqlTemplate sqlTemplate) {
        if (table == null) {
            return false;
        }
        try {
            log.info("Per upgrade process, truncating table: {}", table.getName());
            sqlTemplate.update("truncate table " + table.getName());
            return true;
        } catch (Exception e) {
            log.warn("Unable to truncate table {} during upgrade process because: {}", table.getName(), e.getMessage());
        }
        return false;
    }

    protected boolean truncateTables(String[] tableNames, Database currentModel, ISqlTemplate sqlTemplate) {
        boolean success = true;
        for (String tableName : tableNames) {
            Table table = currentModel.findTable(tableName);
            if (table != null) {
                success &= truncateTableDueToUpgrade(table, sqlTemplate);
            }
        }
        return success;
    }

    protected boolean dropPrimaryKeyConstraintDueToUpgrade(Table table, ISqlTemplate sqlTemplate) {
        if (table == null) {
            return false;
        }
        try {
            String constraintName = sqlTemplate.queryForString(
                    "select name from sysobjects where xtype = 'PK' and parent_obj = object_id('" + table.getName() + "')");
            return dropConstraintFromTable(table, constraintName, sqlTemplate);
        } catch (Exception e) {
            log.warn("Unable to find primary key constraint for table {} during upgrade process because: {}", table.getName(), e.getMessage());
        }
        return false;
    }

    protected boolean dropPkFromTables(String[] tableNames, Database currentModel, ISqlTemplate sqlTemplate) {
        boolean success = true;
        for (String tableName : tableNames) {
            Table table = currentModel.findTable(tableName);
            if (table != null) {
                success &= dropPrimaryKeyConstraintDueToUpgrade(table, sqlTemplate);
            }
        }
        return success;
    }

    protected boolean dropIndexFromTable(Table table, String indexName, ISqlTemplate sqlTemplate) {
        if (table == null) {
            return false;
        }
        log.info("Per upgrade process, dropping index {} from table: {}", indexName, table.getName());
        try {
            sqlTemplate.update("drop index " + table.getName() + "." + indexName);
            return true;
        } catch (Exception e) {
            log.warn("Unable to drop index {} from table {} during upgrade process because: {}", indexName, table.getName(), e.getMessage());
        }
        return false;
    }

    protected boolean dropForeignKeyConstraintDueToUpgrade(Table table, ISqlTemplate sqlTemplate) {
        if (table == null) {
            return false;
        }
        try {
            String constraintName = sqlTemplate.queryForString(
                    "select name from sysobjects where xtype = 'F' and parent_obj = object_id('" + table.getName() + "')");
            return dropConstraintFromTable(table, constraintName, sqlTemplate);
        } catch (Exception e) {
            log.warn("Unable to find foreign key constraint for table {} during upgrade process because: {}", table.getName(), e.getMessage());
        }
        return false;
    }

    protected boolean dropFkFromTables(String[] tableNames, Database currentModel, ISqlTemplate sqlTemplate) {
        boolean success = true;
        for (String tableName : tableNames) {
            Table table = currentModel.findTable(tableName);
            if (table != null) {
                success &= dropForeignKeyConstraintDueToUpgrade(table, sqlTemplate);
            }
        }
        return success;
    }

    protected boolean dropConstraintFromTable(Table table, String constraintName, ISqlTemplate sqlTemplate) {
        if (table == null) {
            return false;
        }
        log.info("Per upgrade process, dropping constraint {} from table: {}", constraintName, table.getName());
        try {
            sqlTemplate.update("alter table " + table.getName() + " drop constraint " + constraintName);
            return true;
        } catch (Exception e) {
            log.warn("Unable to drop constraint {} from table {} during upgrade process because: {}", constraintName, table.getName(), e.getMessage());
        }
        return false;
    }

    protected boolean deleteFromTableDueToUpgrade(Table table, ISqlTemplate sqlTemplate) {
        if (table == null) {
            return false;
        }
        try {
            log.info("Per upgrade process, deleting from table: {}", table.getName());
            sqlTemplate.update("delete from " + table.getName());
            return true;
        } catch (Exception e) {
            log.warn("Unable to delete from table {} during upgrade process because: {}", table.getName(), e.getMessage());
        }
        return false;
    }

    protected boolean deleteFromTables(String[] tableNames, Database currentModel, ISqlTemplate sqlTemplate) {
        boolean success = true;
        for (String tableName : tableNames) {
            Table table = currentModel.findTable(tableName);
            if (table != null) {
                success &= deleteFromTableDueToUpgrade(table, sqlTemplate);
            }
        }
        return success;
    }

    protected void dropSymTriggersIfNecessary(Database currentModel, Database desiredModel) {
        List<IAlterDatabaseInterceptor> alterDatabaseInterceptors = engine.getExtensionService()
                .getExtensionPointList(IAlterDatabaseInterceptor.class);
        List<IModelChange> modelChanges = engine.getDatabasePlatform().getDdlBuilder().getDetectedChanges(currentModel, desiredModel,
                alterDatabaseInterceptors.toArray(new IAlterDatabaseInterceptor[alterDatabaseInterceptors.size()]));
        MultiInstanceofPredicate predicate = new MultiInstanceofPredicate(
                new Class<?>[] { RemovePrimaryKeyChange.class, AddPrimaryKeyChange.class, PrimaryKeyChange.class, RemoveColumnChange.class,
                        AddColumnChange.class, ColumnDataTypeChange.class, ColumnSizeChange.class, CopyColumnValueChange.class });
        Collection<IModelChange> modelChangesAffectingTriggers = modelChanges.stream().filter(c -> predicate.test(c)).collect(Collectors.toList());
        Set<String> setOfTableNamesToDropTriggersFor = new HashSet<String>();
        for (IModelChange change : modelChangesAffectingTriggers) {
            if (change instanceof TableChange) {
                setOfTableNamesToDropTriggersFor.add(((TableChange) change).getChangedTable().getName());
            }
        }
        engine.getTriggerRouterService().dropTriggers(setOfTableNamesToDropTriggersFor);
    }

    @Override
    public String afterUpgrade(ISymmetricDialect symmetricDialect, String tablePrefix, Database model) throws IOException {
        if (isUpgradeFromPre314 && engine.getNodeId() != null) {
            log.info("Fixing extract request table after upgrade");
            engine.getSqlTemplate().update("update " + tablePrefix + "_" + TableConstants.SYM_EXTRACT_REQUEST
                    + " set source_node_id = ? where source_node_id = 'default'", engine.getNodeId());
        }
        // Leave this first so triggers are put back in place before any DML is
        // done against SymmetricDS tables
        // Reinstall triggers on sym tables
        engine.getTriggerRouterService().syncTriggers();
        StringBuilder sb = new StringBuilder();
        if (isUpgradeFromPre38) {
            engine.getSqlTemplate().update(
                    "update " + tablePrefix + "_" + TableConstants.SYM_SEQUENCE + " set cache_size = 10 where sequence_name = ?",
                    Constants.SEQUENCE_OUTGOING_BATCH);
            engine.getSqlTemplate().update(
                    "update  " + tablePrefix + "_" + TableConstants.SYM_CHANNEL + " set max_batch_size = 10000 where reload_flag = 1 ");
        }
        if (isUpgradeFromPre3125 && engine.getParameterService().isRegistrationServer()) {
            log.info("After upgrade, fixing initial_load_end_time");
            engine.getSqlTemplate().update("update " + tablePrefix + "_" + TableConstants.SYM_NODE_SECURITY
                    + " set initial_load_end_time = initial_load_time where initial_load_time is not null");
        }
        if (isUpgradeFromPre316) {
            log.info("After upgrading, fixing loaded_time on sym_extract_request");
            engine.getSqlTemplate().update("update " + tablePrefix + "_" + TableConstants.SYM_EXTRACT_REQUEST
                    + " set loaded_time = last_update_time where source_node_id = ? and loaded_time is null"
                    + " and load_id in (select load_id from " + tablePrefix + "_" + TableConstants.SYM_TABLE_RELOAD_STATUS
                    + " where source_node_id = ? and completed = 1)",
                    engine.getNodeId(), engine.getNodeId());
        }
        if (isUpgradeFromPre317) {
            IConfigurationService configService = engine.getConfigurationService();
            Channel reloadChannel = configService.getChannel(Constants.CHANNEL_RELOAD);
            if (reloadChannel != null && !"bulk".equals(reloadChannel.getDataLoaderType())) {
                reloadChannel.setDataLoaderType("bulk");
                configService.saveChannel(reloadChannel, false);
            }
            migrateOracleTransactionViewParameters();
        }
        if (engine.getDatabasePlatform().getName().equals(DatabaseNamesConstants.H2)) {
            createH2SequenceIfMissing(symmetricDialect, tablePrefix);
        }
        engine.getPullService().pullConfigData(false);
        return sb.toString();
    }

    protected void migrateOracleTransactionViewParameters() {
        IParameterService parameterService = engine.getParameterService();
        if (parameterService.is(ParameterConstants.DBDIALECT_ORACLE_USE_TRANSACTION_VIEW_LEGACY)) {
            parameterService.saveParameter(ParameterConstants.ROUTING_GAPS_USE_TRANSACTION_VIEW, true, "upgrade");
            log.info("Migrated {} to {}", ParameterConstants.DBDIALECT_ORACLE_USE_TRANSACTION_VIEW_LEGACY,
                    ParameterConstants.ROUTING_GAPS_USE_TRANSACTION_VIEW);
        }
        long threshold = parameterService.getLong(ParameterConstants.DBDIALECT_ORACLE_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS_LEGACY, 60000);
        if (threshold != 60000) {
            parameterService.saveParameter(ParameterConstants.ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS, threshold, "upgrade");
            log.info("Migrated {} ({}) to {}", ParameterConstants.DBDIALECT_ORACLE_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS_LEGACY,
                    threshold, ParameterConstants.ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS);
        }
    }

    protected void checkForDroppedColumns(Database currentModel, Database desiredModel) {
        for (Table currentTable : currentModel.getTables()) {
            Table desiredTable = desiredModel.findTable(currentTable.getName());
            if (desiredTable != null) {
                for (Column currentColumn : currentTable.getColumns()) {
                    Column desiredColumn = desiredTable.findColumn(currentColumn.getName());
                    if (desiredColumn == null) {
                        dropTriggers(currentModel, currentTable.getName(), currentColumn.getName());
                        break;
                    }
                }
            }
        }
    }

    protected void dropTriggers(Database currentModel, String tableName, String columnName) {
        Table table = currentModel.findTable(tableName);
        if (table != null && table.findColumn(columnName) != null) {
            TriggerHistory hist = engine.getTriggerRouterService().findTriggerHistory(null, null, tableName);
            if (hist != null) {
                log.info("Dropping triggers on " + tableName + " because " + columnName + " needs dropped");
                engine.getTriggerRouterService().dropTriggers(hist);
            }
        }
    }

    protected void dropTriggers(Database currentModel, String tableName) {
        Table table = currentModel.findTable(tableName);
        if (table != null) {
            TriggerHistory hist = engine.getTriggerRouterService().findTriggerHistory(null, null, tableName);
            if (hist != null) {
                log.info("Dropping triggers on " + tableName);
                engine.getTriggerRouterService().dropTriggers(hist);
            }
        }
    }

    protected boolean isUpgradeFromPre38(String tablePrefix, Database currentModel, Database desiredModel) {
        String monitorTableName = tablePrefix + "_" + TableConstants.SYM_MONITOR;
        String nodeTableName = tablePrefix + "_" + TableConstants.SYM_NODE;
        if (currentModel.findTable(nodeTableName) != null && currentModel.findTable(monitorTableName) == null
                && desiredModel.findTable(monitorTableName) != null) {
            log.info("Detected upgrade from pre-3.8 version.");
            return true;
        } else {
            return false;
        }
    }

    protected boolean isUpgradeFromPre310(String tablePrefix, Database currentModel, Database desiredModel) {
        String nodeTableName = tablePrefix + "_" + TableConstants.SYM_NODE;
        Table nodeTable = currentModel.findTable(nodeTableName);
        if (nodeTable != null) {
            Column heartbeatTime = nodeTable.getColumnWithName("heartbeat_time");
            if (heartbeatTime != null) {
                return true;
            }
        }
        return false;
    }

    protected boolean isUpgradeFromPre311(String tablePrefix, Database currentModel, Database desiredModel) {
        Table eventTable = currentModel.findTable(tablePrefix + "_" + TableConstants.SYM_DATA_EVENT);
        if (eventTable != null && eventTable.findColumn("router_id") != null) {
            log.info("Detected upgrade from pre-3.11 version.");
            return true;
        } else {
            return false;
        }
    }

    protected boolean shouldFixDataEvent311(String tablePrefix) {
        boolean shouldFix = engine.getParameterService().is("upgrade.force.fix.data.event");
        if (!shouldFix && !engine.getParameterService().is("upgrade.skip.fix.data.event")) {
            HashSet<String> set = new HashSet<String>();
            String sql = "select t.trigger_id, r.target_node_group_id from " + tablePrefix + "_trigger t inner join " + tablePrefix +
                    "_trigger_router tr on tr.trigger_id = t.trigger_id inner join " + tablePrefix +
                    "_router r on r.router_id = tr.router_id where r.source_node_group_id = ?";
            List<Row> rows = engine.getSqlTemplate().query(sql, new Object[] { engine.getParameterService().getNodeGroupId() });
            for (Row row : rows) {
                String key = row.getString("trigger_id") + "-" + row.getString("target_node_group_id");
                if (set.contains(key)) {
                    shouldFix = true;
                    break;
                }
                set.add(key);
            }
        }
        return shouldFix;
    }

    protected void fixDataEvent311(String tablePrefix) {
        log.info("Checking data_event for upgrade");
        List<Row> rows = engine.getDatabasePlatform().getSqlTemplateDirty().query("select batch_id, data_id, max(router_id) router_id " +
                "from " + tablePrefix + "_data_event group by batch_id, data_id having count(*) > 1");
        log.info("Found {} rows in data_event with duplicates", rows.size());
        if (rows.size() > 0) {
            long ts = System.currentTimeMillis();
            int commitSize = engine.getParameterService().getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_DATA_IDS);
            ISqlTransaction transaction = null;
            try {
                transaction = engine.getSqlTemplate().startSqlTransaction();
                transaction.setInBatchMode(true);
                transaction.prepare("delete from " + tablePrefix + "_data_event where batch_id = ? and data_id = ? and router_id != ?");
                int[] types = new int[] { engine.getSymmetricDialect().getSqlTypeForIds(), engine.getSymmetricDialect().getSqlTypeForIds(),
                        Types.VARCHAR };
                int uncommittedCount = 0, totalRowCount = 0;
                for (Row row : rows) {
                    uncommittedCount += transaction.addRow(row, new Object[] { row.getLong("batch_id"), row.getLong("data_id"),
                            row.getString("router_id") }, types);
                    totalRowCount++;
                    if (uncommittedCount >= commitSize) {
                        transaction.commit();
                        uncommittedCount = 0;
                    }
                    if (System.currentTimeMillis() - ts > DateUtils.MILLIS_PER_MINUTE) {
                        log.info("Processed {} of {} rows so far", totalRowCount, rows.size());
                        ts = System.currentTimeMillis();
                    }
                }
                transaction.commit();
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
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
        log.info("Done preparing data_event for upgrade");
    }

    protected boolean isUpgradeFromPre312(String tablePrefix, Database currentModel, Database desiredModel) {
        Table eventTable = currentModel.findTable(tablePrefix + "_" + TableConstants.SYM_NODE_SECURITY);
        if (eventTable != null && eventTable.findColumn("failed_logins") == null) {
            log.info("Detected upgrade from pre-3.12 version.");
            return true;
        } else {
            return false;
        }
    }

    protected boolean isUpgradeFromPre3125(String tablePrefix, Database currentModel, Database desiredModel) {
        Table eventTable = currentModel.findTable(tablePrefix + "_" + TableConstants.SYM_NODE_SECURITY);
        if (eventTable != null && eventTable.findColumn("initial_load_end_time") == null) {
            log.info("Detected upgrade from pre-3.12.5 version.");
            return true;
        } else {
            return false;
        }
    }

    protected boolean isUpgradeFromPre314(String tablePrefix, Database currentModel, Database desiredModel) {
        Table table = currentModel.findTable(tablePrefix + "_" + TableConstants.SYM_EXTRACT_REQUEST);
        if (table != null && table.findColumn("source_node_id") == null) {
            log.info("Detected upgrade from pre-3.14 version.");
            return true;
        } else {
            return false;
        }
    }

    protected boolean isUpgradeFromPre315(String tablePrefix, Database currentModel) {
        Table table = currentModel.findTable(tablePrefix + "_" + TableConstants.SYM_TABLE_RELOAD_REQUEST);
        if (table != null) {
            Column createTime = table.findColumn("create_time");
            if (createTime.getSizeAsInt() == 2) {
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    protected boolean isUpgradeFromPre316(String tablePrefix, Database currentModel) {
        Table table = currentModel.findTable(tablePrefix + "_" + TableConstants.SYM_EXTRACT_REQUEST);
        return table != null && table.findColumn("extract_thread_id") == null;
    }

    protected boolean isUpgradeFromPre317(String tablePrefix, Database currentModel) {
        Table nodeHostChannelStatsTable = currentModel.findTable(TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST_CHANNEL_STATS));
        return nodeHostChannelStatsTable != null && nodeHostChannelStatsTable.findColumn("data_received") == null;
    }

    protected void createH2SequenceIfMissing(ISymmetricDialect symmetricDialect, String tablePrefix) {
        String dataIdSequenceName = symmetricDialect.getSequenceName(SequenceIdentifier.DATA).toUpperCase() + "_SEQ";
        if (engine.getSqlTemplate().queryForInt("select count(*) from information_schema.sequences where sequence_name = ?", dataIdSequenceName) == 0) {
            String dataTableName = TableConstants.getTableName(tablePrefix, TableConstants.SYM_DATA);
            ISqlTransaction transaction = null;
            try {
                transaction = engine.getSqlTemplate().startSqlTransaction();
                transaction.prepareAndExecute("set exclusive 1");
                long maxDataId = Math.max(transaction.queryForInt("select max(data_id) from " + dataTableName), 1);
                log.info("After upgrade, creating {} sequence with a value of {} because it doesn't exist", dataIdSequenceName, maxDataId + 1);
                transaction.prepareAndExecute("create sequence \"" + dataIdSequenceName + "\" start with " + (maxDataId + 1));
                log.info("After upgrade, altering {}.data_id to use the new sequence instead of auto_increment", dataTableName);
                transaction.prepareAndExecute("alter table " + dataTableName
                        + " alter column data_id bigint not null default nextval('" + dataIdSequenceName + "')");
                transaction.prepareAndExecute("set exclusive 0");
            } catch (Exception e) {
                log.info("Unable to create sequence: {}", e.getMessage());
                if (transaction != null) {
                    transaction.rollback();
                }
            } finally {
                if (transaction != null) {
                    transaction.close();
                }
            }
        }
    }

    protected void beforeUpgradeFromPre3_8(String tablePrefix, Database currentModel, ISqlTemplate sqlTemplate) {
        Table transformTable = currentModel.findTable(TableConstants.getTableName(tablePrefix, TableConstants.SYM_TRANSFORM_TABLE));
        if (transformTable != null && transformTable.findColumn("update_action") != null) {
            sqlTemplate.update("update " + TableConstants.getTableName(tablePrefix, TableConstants.SYM_TRANSFORM_TABLE)
                    + " set update_action = 'UPD_ROW' where update_action is null");
        }
        String[] tableNames = { TableConstants.getTableName(tablePrefix, TableConstants.SYM_DATA_GAP),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_COMMUNICATION) };
        deleteFromTables(tableNames, currentModel, sqlTemplate);
    }

    protected void beforeUpgradeFromPre3_10(String tablePrefix, Database currentModel, ISqlTemplate sqlTemplate) {
        if (engine.getDatabasePlatform().getName().equals(DatabaseNamesConstants.ASE)) {
            dropConstraintFromTable(currentModel.findTable(TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_IDENTITY)),
                    tablePrefix + "_fk_ident_2_node", sqlTemplate);
            dropConstraintFromTable(currentModel.findTable(TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_SECURITY)),
                    tablePrefix + "_fk_sec_2_node", sqlTemplate);
        }
    }

    protected void beforeUpgradeFromPre3_11(String tablePrefix) {
        if (shouldFixDataEvent311(tablePrefix)) {
            fixDataEvent311(tablePrefix);
        }
    }

    protected void beforeUpgradeFromPre3_12(String tablePrefix, Database currentModel, ISqlTemplate sqlTemplate) {
        if (engine.getParameterService().isRegistrationServer()) {
            log.info("Before upgrade, fixing router_type");
            sqlTemplate.update("update " + TableConstants.getTableName(tablePrefix, TableConstants.SYM_ROUTER)
                    + " set router_type = 'default' where router_type is null");
        }
        /*
         * Workarounds for missing features (bugs) in ddl-utils
         */
        String name = engine.getDatabasePlatform().getName();
        if (name.equals(DatabaseNamesConstants.ORACLE) || name.equals(DatabaseNamesConstants.ORACLE122) || name.equals(DatabaseNamesConstants.ORACLE23)) {
            dropConstraintFromTable(currentModel.findTable(TableConstants.getTableName(tablePrefix, TableConstants.SYM_DATA)),
                    TableConstants.getTableName(tablePrefix, TableConstants.SYM_DATA) + "_pk", sqlTemplate);
        }
        if (name.equals(DatabaseNamesConstants.ASE)) {
            dropIndexFromTable(currentModel.findTable(TableConstants.getTableName(tablePrefix, TableConstants.SYM_DATA)),
                    tablePrefix + "_idx_d_channel_id", sqlTemplate);
            dropConstraintFromTable(currentModel.findTable(TableConstants.getTableName(tablePrefix, TableConstants.SYM_TRIGGER_ROUTER)),
                    tablePrefix + "_fk_tr_2_rtr", sqlTemplate);
            dropConstraintFromTable(currentModel.findTable(TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_TRIGGER_ROUTER)),
                    tablePrefix + "_fk_ftr_2_rtr", sqlTemplate);
        }
    }

    // Informix cannot create sym_trigger with LONGVARCHAR columns (ticket 0002748), so always downgrade
    // them to VARCHAR(255) on this platform; unlike the other fixes here, this is not tied to any upgrade version.
    protected void fixInformixTriggerLongVarcharColumns(String tablePrefix, Database desiredModel) {
        if (engine.getDatabasePlatform().getName().equals(DatabaseNamesConstants.INFORMIX)) {
            Table triggerTable = desiredModel.findTable(tablePrefix + "_" + TableConstants.SYM_TRIGGER);
            if (triggerTable != null) {
                for (Column column : triggerTable.getColumns()) {
                    if (column.getMappedTypeCode() == Types.LONGVARCHAR) {
                        column.setJdbcTypeCode(Types.VARCHAR);
                        column.setMappedType("VARCHAR");
                        column.setMappedTypeCode(Types.VARCHAR);
                        column.setSize("255");
                    }
                }
            }
        }
    }

    protected void beforeUpgradeFromPre3_15(String tablePrefix, Database currentModel, ISqlTemplate sqlTemplate) {
        String name = engine.getDatabasePlatform().getName();
        String[] pre315TableNames = { TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_RELOAD_REQUEST),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_GROUP_CHANNEL_WND),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST_CHANNEL_STATS),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST_JOB_STATS),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST_STATS),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_REGISTRATION_REQUEST) };
        if (name.contains(DatabaseNamesConstants.MSSQL)) {
            dropPkFromTables(pre315TableNames, currentModel, sqlTemplate);
        }
        if (name.equals(DatabaseNamesConstants.ORACLE) || name.equals(DatabaseNamesConstants.ORACLE122) || name.equals(DatabaseNamesConstants.ORACLE23)) {
            truncateTables(pre315TableNames, currentModel, sqlTemplate);
        }
    }

    protected void beforeUpgradeFromPre3_16(String tablePrefix, Database currentModel, ISqlTemplate sqlTemplate) {
        String[] tableNames = { TableConstants.getTableName(tablePrefix, TableConstants.SYM_DESIGN_DIAGRAM),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_DIAGRAM_GROUP) };
        dropTables(tableNames, currentModel, sqlTemplate);
    }

    protected void beforeUpgradeFromPre3_17(String tablePrefix, Database currentModel, ISqlTemplate sqlTemplate) {
        String[] tableNames = { TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_CHANNEL_CTL) };
        dropTables(tableNames, currentModel, sqlTemplate);
    }
}
