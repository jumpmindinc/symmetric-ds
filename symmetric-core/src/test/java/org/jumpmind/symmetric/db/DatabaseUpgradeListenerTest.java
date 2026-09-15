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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;

import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.RemoveTableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IAlterDatabaseInterceptor;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IPullService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatabaseUpgradeListenerTest {
    private DatabaseUpgradeListener listener;
    private ISymmetricEngine engine;
    private ITriggerRouterService triggerRouterService;
    private ISqlTemplate sqlTemplate;
    private Database currentModel;
    private StringBuilder sqlScript;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        sqlTemplate = mock(ISqlTemplate.class);
        listener = new DatabaseUpgradeListener();
        listener.setSymmetricEngine(engine);
        currentModel = new Database();
        currentModel.addTable(new Table("sym_test_table"));
        currentModel.addTable(new Table("sym_second_table"));
        sqlScript = new StringBuilder();
    }

    private IDatabasePlatform stubDatabasePlatform(String databaseName) {
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        when(databasePlatform.getName()).thenReturn(databaseName);
        when(engine.getDatabasePlatform()).thenReturn(databasePlatform);
        return databasePlatform;
    }

    private IParameterService stubParameterService() {
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        return parameterService;
    }

    private IPullService stubPullService() {
        IPullService pullService = mock(IPullService.class);
        when(engine.getPullService()).thenReturn(pullService);
        return pullService;
    }

    @Test
    void testDropTableDueToUpgrade_TableIsNull_ReturnsFalseAndNeverCallsSqlTemplate() {
        boolean result = listener.dropTableDueToUpgrade(null, currentModel, sqlTemplate, sqlScript);
        assertFalse(result, "A null table should not be dropped");
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testDropTableDueToUpgrade_TableExists_DropsTableAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropTableDueToUpgrade(table, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "An existing table should be dropped");
        verify(sqlTemplate, times(1)).update("drop table sym_test_table");
    }

    @Test
    void testDropTableDueToUpgrade_SqlTemplateThrows_ReturnsFalse() {
        when(sqlTemplate.update(anyString())).thenThrow(new RuntimeException("table does not exist"));
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropTableDueToUpgrade(table, currentModel, sqlTemplate, sqlScript);
        assertFalse(result, "A failed drop should not propagate the exception");
    }

    @Test
    void testTruncateTableDueToUpgrade_TableExists_TruncatesAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.truncateTableDueToUpgrade(table, sqlTemplate, sqlScript);
        assertTrue(result, "An existing table should be truncated");
        verify(sqlTemplate, times(1)).update("truncate table sym_test_table");
    }

    @Test
    void testDropPrimaryKeyConstraintDueToUpgrade_TableExists_QueriesConstraintNameAndDropsIt() {
        when(sqlTemplate.queryForString(anyString())).thenReturn("sym_pk_test_table");
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropPrimaryKeyConstraintDueToUpgrade(table, sqlTemplate, sqlScript);
        assertTrue(result, "An existing table's primary key should be dropped");
        verify(sqlTemplate, times(1)).update("alter table sym_test_table drop constraint sym_pk_test_table");
    }

    @Test
    void testDropTables_AllTablesExist_ActsOnEachAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_second_table" };
        boolean result = listener.dropTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Dropping all existing tables should succeed");
        verify(sqlTemplate, times(1)).update("drop table sym_test_table");
        verify(sqlTemplate, times(1)).update("drop table sym_second_table");
    }

    @Test
    void testDropTables_OneTableMissing_SkipsItAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_missing_table" };
        boolean result = listener.dropTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing table should be skipped without affecting the overall result");
        verify(sqlTemplate, times(1)).update("drop table sym_test_table");
        verify(sqlTemplate, never()).update("drop table sym_missing_table");
    }

    @Test
    void testTruncateTables_OneTableMissing_SkipsItAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_missing_table" };
        boolean result = listener.truncateTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing table should be skipped without affecting the overall result");
        verify(sqlTemplate, times(1)).update("truncate table sym_test_table");
        verify(sqlTemplate, never()).update("truncate table sym_missing_table");
    }

    @Test
    void testTruncateTables_AllTablesExist_ActsOnEachAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_second_table" };
        boolean result = listener.truncateTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Truncating all existing tables should succeed");
        verify(sqlTemplate, times(1)).update("truncate table sym_test_table");
        verify(sqlTemplate, times(1)).update("truncate table sym_second_table");
    }

    @Test
    void testDropPkFromTables_OneTableMissing_SkipsItAndReturnsTrue() {
        when(sqlTemplate.queryForString(anyString())).thenReturn("sym_pk_test_table");
        String[] tableNames = { "sym_test_table", "sym_missing_table" };
        boolean result = listener.dropPkFromTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing table should be skipped without affecting the overall result");
        verify(sqlTemplate, times(1)).update("alter table sym_test_table drop constraint sym_pk_test_table");
    }

    @Test
    void testDropIndexFromTable_TableIsNull_ReturnsFalseAndNeverCallsSqlTemplate() {
        boolean result = listener.dropIndexFromTable(null, "sym_idx_test", sqlTemplate, sqlScript);
        assertFalse(result, "A null table should not have an index dropped");
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testDropIndexFromTable_TableExists_DropsIndexAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropIndexFromTable(table, "sym_idx_test", sqlTemplate, sqlScript);
        assertTrue(result, "An existing table's index should be dropped");
        verify(sqlTemplate, times(1)).update("drop index sym_test_table.sym_idx_test");
    }

    @Test
    void testDropIndexFromTable_SqlTemplateThrows_ReturnsFalse() {
        when(sqlTemplate.update(anyString())).thenThrow(new RuntimeException("index does not exist"));
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropIndexFromTable(table, "sym_idx_test", sqlTemplate, sqlScript);
        assertFalse(result, "A failed index drop should not propagate the exception");
    }

    @Test
    void testDeleteFromTableDueToUpgrade_TableExists_DeletesAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.deleteFromTableDueToUpgrade(table, sqlTemplate, sqlScript);
        assertTrue(result, "An existing table's rows should be deleted");
        verify(sqlTemplate, times(1)).update("delete from sym_test_table");
    }

    @Test
    void testDeleteFromTables_OneTableMissing_SkipsItAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_missing_table" };
        boolean result = listener.deleteFromTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing table should be skipped without affecting the overall result");
        verify(sqlTemplate, times(1)).update("delete from sym_test_table");
        verify(sqlTemplate, never()).update("delete from sym_missing_table");
    }

    @Test
    void testDropConstraintFromTable_TableIsNull_ReturnsFalseAndNeverCallsSqlTemplate() {
        boolean result = listener.dropConstraintFromTable(null, "sym_fk_test", sqlTemplate, sqlScript);
        assertFalse(result, "A null table should not have a constraint dropped");
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testDropConstraintFromTable_TableExists_DropsConstraintAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropConstraintFromTable(table, "sym_fk_test", sqlTemplate, sqlScript);
        assertTrue(result, "An existing table's constraint should be dropped");
        verify(sqlTemplate, times(1)).update("alter table sym_test_table drop constraint sym_fk_test");
    }

    @Test
    void testDropConstraintFromTable_SqlTemplateThrows_ReturnsFalse() {
        when(sqlTemplate.update(anyString())).thenThrow(new RuntimeException("constraint does not exist"));
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropConstraintFromTable(table, "sym_fk_test", sqlTemplate, sqlScript);
        assertFalse(result, "A failed constraint drop should not propagate the exception");
    }

    @Test
    void testIsUpgradeFromPre3_8_MonitorTableMissingFromCurrentButPresentInDesired_ReturnsTrue() {
        Database currentModelForTest = new Database();
        currentModelForTest.addTable(new Table("sym_node"));
        Database desiredModel = new Database();
        desiredModel.addTable(new Table("sym_monitor"));
        boolean result = listener.isUpgradeFromPre3_8("sym", currentModelForTest, desiredModel);
        assertTrue(result, "A missing monitor table with an existing node table indicates a pre-3.8 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_8_MonitorTableAlreadyExists_ReturnsFalse() {
        Database currentModelForTest = new Database();
        currentModelForTest.addTable(new Table("sym_node"));
        currentModelForTest.addTable(new Table("sym_monitor"));
        Database desiredModel = new Database();
        desiredModel.addTable(new Table("sym_monitor"));
        boolean result = listener.isUpgradeFromPre3_8("sym", currentModelForTest, desiredModel);
        assertFalse(result, "An already-present monitor table means this is not a pre-3.8 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_8_NodeTableMissing_ReturnsFalse() {
        Database currentModelForTest = new Database();
        Database desiredModel = new Database();
        desiredModel.addTable(new Table("sym_monitor"));
        boolean result = listener.isUpgradeFromPre3_8("sym", currentModelForTest, desiredModel);
        assertFalse(result, "A missing node table means there is nothing to upgrade from");
    }

    @Test
    void testIsUpgradeFromPre3_10_NodeTableHasHeartbeatTimeColumn_ReturnsTrue() {
        Database currentModelForTest = new Database();
        Table nodeTable = new Table("sym_node");
        nodeTable.addColumn(new Column("heartbeat_time"));
        currentModelForTest.addTable(nodeTable);
        boolean result = listener.isUpgradeFromPre3_10("sym", currentModelForTest);
        assertTrue(result, "A node table with a heartbeat_time column indicates a pre-3.10 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_10_NodeTableMissingHeartbeatTimeColumn_ReturnsFalse() {
        Database currentModelForTest = new Database();
        currentModelForTest.addTable(new Table("sym_node"));
        boolean result = listener.isUpgradeFromPre3_10("sym", currentModelForTest);
        assertFalse(result, "A node table without a heartbeat_time column means this is not a pre-3.10 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_11_DataEventTableHasRouterIdColumn_ReturnsTrue() {
        Database currentModelForTest = new Database();
        Table dataEventTable = new Table("sym_data_event");
        dataEventTable.addColumn(new Column("router_id"));
        currentModelForTest.addTable(dataEventTable);
        boolean result = listener.isUpgradeFromPre3_11("sym", currentModelForTest);
        assertTrue(result, "A data_event table with a router_id column indicates a pre-3.11 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_11_DataEventTableMissing_ReturnsFalse() {
        boolean result = listener.isUpgradeFromPre3_11("sym", new Database());
        assertFalse(result, "A missing data_event table means this is not a pre-3.11 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_12_NodeSecurityTableMissingFailedLoginsColumn_ReturnsTrue() {
        Database currentModelForTest = new Database();
        currentModelForTest.addTable(new Table("sym_node_security"));
        boolean result = listener.isUpgradeFromPre3_12("sym", currentModelForTest);
        assertTrue(result, "A node_security table without a failed_logins column indicates a pre-3.12 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_12_NodeSecurityTableHasFailedLoginsColumn_ReturnsFalse() {
        Database currentModelForTest = new Database();
        Table nodeSecurityTable = new Table("sym_node_security");
        nodeSecurityTable.addColumn(new Column("failed_logins"));
        currentModelForTest.addTable(nodeSecurityTable);
        boolean result = listener.isUpgradeFromPre3_12("sym", currentModelForTest);
        assertFalse(result, "A node_security table with a failed_logins column means this is not a pre-3.12 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_12_5_NodeSecurityTableMissingInitialLoadEndTimeColumn_ReturnsTrue() {
        Database currentModelForTest = new Database();
        currentModelForTest.addTable(new Table("sym_node_security"));
        boolean result = listener.isUpgradeFromPre3_12_5("sym", currentModelForTest);
        assertTrue(result, "A node_security table without an initial_load_end_time column indicates a pre-3.12.5 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_12_5_NodeSecurityTableHasInitialLoadEndTimeColumn_ReturnsFalse() {
        Database currentModelForTest = new Database();
        Table nodeSecurityTable = new Table("sym_node_security");
        nodeSecurityTable.addColumn(new Column("initial_load_end_time"));
        currentModelForTest.addTable(nodeSecurityTable);
        boolean result = listener.isUpgradeFromPre3_12_5("sym", currentModelForTest);
        assertFalse(result, "A node_security table with an initial_load_end_time column means this is not a pre-3.12.5 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_14_ExtractRequestTableMissingSourceNodeIdColumn_ReturnsTrue() {
        Database currentModelForTest = new Database();
        currentModelForTest.addTable(new Table("sym_extract_request"));
        boolean result = listener.isUpgradeFromPre3_14("sym", currentModelForTest);
        assertTrue(result, "An extract_request table without a source_node_id column indicates a pre-3.14 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_14_ExtractRequestTableHasSourceNodeIdColumn_ReturnsFalse() {
        Database currentModelForTest = new Database();
        Table extractRequestTable = new Table("sym_extract_request");
        extractRequestTable.addColumn(new Column("source_node_id"));
        currentModelForTest.addTable(extractRequestTable);
        boolean result = listener.isUpgradeFromPre3_14("sym", currentModelForTest);
        assertFalse(result, "An extract_request table with a source_node_id column means this is not a pre-3.14 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_15_TableReloadRequestTableMissing_ReturnsFalse() {
        boolean result = listener.isUpgradeFromPre3_15("sym", new Database());
        assertFalse(result, "A missing table_reload_request table means this is not a pre-3.15 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_15_CreateTimeColumnSizeIsTwo_ReturnsFalse() {
        Database currentModelForTest = new Database();
        Table tableReloadRequestTable = new Table("sym_table_reload_request");
        Column createTime = new Column("create_time");
        createTime.setSize("2");
        tableReloadRequestTable.addColumn(createTime);
        currentModelForTest.addTable(tableReloadRequestTable);
        boolean result = listener.isUpgradeFromPre3_15("sym", currentModelForTest);
        assertFalse(result, "A create_time column already sized at 2 means this is not a pre-3.15 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_15_CreateTimeColumnSizeIsNotTwo_ReturnsTrue() {
        Database currentModelForTest = new Database();
        Table tableReloadRequestTable = new Table("sym_table_reload_request");
        Column createTime = new Column("create_time");
        createTime.setSize("19");
        tableReloadRequestTable.addColumn(createTime);
        currentModelForTest.addTable(tableReloadRequestTable);
        boolean result = listener.isUpgradeFromPre3_15("sym", currentModelForTest);
        assertTrue(result, "A create_time column not yet sized at 2 indicates a pre-3.15 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_16_ExtractRequestTableMissingExtractThreadIdColumn_ReturnsTrue() {
        Database currentModelForTest = new Database();
        currentModelForTest.addTable(new Table("sym_extract_request"));
        boolean result = listener.isUpgradeFromPre3_16("sym", currentModelForTest);
        assertTrue(result, "An extract_request table without an extract_thread_id column indicates a pre-3.16 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_16_ExtractRequestTableHasExtractThreadIdColumn_ReturnsFalse() {
        Database currentModelForTest = new Database();
        Table extractRequestTable = new Table("sym_extract_request");
        extractRequestTable.addColumn(new Column("extract_thread_id"));
        currentModelForTest.addTable(extractRequestTable);
        boolean result = listener.isUpgradeFromPre3_16("sym", currentModelForTest);
        assertFalse(result, "An extract_request table with an extract_thread_id column means this is not a pre-3.16 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_17_NodeHostChannelStatsTableMissingDataReceivedColumn_ReturnsTrue() {
        Database currentModelForTest = new Database();
        currentModelForTest.addTable(new Table(TableConstants.getTableName("sym", TableConstants.SYM_NODE_HOST_CHANNEL_STATS)));
        boolean result = listener.isUpgradeFromPre3_17("sym", currentModelForTest);
        assertTrue(result, "A node_host_channel_stats table without a data_received column indicates a pre-3.17 upgrade");
    }

    @Test
    void testIsUpgradeFromPre3_17_NodeHostChannelStatsTableHasDataReceivedColumn_ReturnsFalse() {
        Database currentModelForTest = new Database();
        Table nodeHostChannelStatsTable = new Table(TableConstants.getTableName("sym", TableConstants.SYM_NODE_HOST_CHANNEL_STATS));
        nodeHostChannelStatsTable.addColumn(new Column("data_received"));
        currentModelForTest.addTable(nodeHostChannelStatsTable);
        boolean result = listener.isUpgradeFromPre3_17("sym", currentModelForTest);
        assertFalse(result, "A node_host_channel_stats table with a data_received column means this is not a pre-3.17 upgrade");
    }

    @Test
    void testDropTriggers_TwoArg_TableMissing_NeverLooksUpTriggerHistory() {
        listener.dropTriggers(currentModel, "sym_missing_table");
        verify(triggerRouterService, never()).findTriggerHistory(any(), any(), any());
    }

    @Test
    void testDropTriggers_TwoArg_TriggerHistoryFound_DropsTriggers() {
        TriggerHistory triggerHistory = new TriggerHistory("sym_test_table", "id", "id,name");
        when(triggerRouterService.findTriggerHistory(null, null, "sym_test_table")).thenReturn(triggerHistory);
        listener.dropTriggers(currentModel, "sym_test_table");
        verify(triggerRouterService, times(1)).dropTriggers(triggerHistory);
    }

    @Test
    void testDropTriggers_TwoArg_NoTriggerHistoryFound_NeverDropsTriggers() {
        listener.dropTriggers(currentModel, "sym_test_table");
        verify(triggerRouterService, never()).dropTriggers(any(TriggerHistory.class));
    }

    @Test
    void testDropTriggers_ThreeArg_ColumnMissing_NeverLooksUpTriggerHistory() {
        listener.dropTriggers(currentModel, "sym_test_table", "missing_column");
        verify(triggerRouterService, never()).findTriggerHistory(any(), any(), any());
    }

    @Test
    void testDropTriggers_ThreeArg_ColumnFoundAndTriggerHistoryFound_DropsTriggers() {
        Table table = currentModel.findTable("sym_test_table");
        table.addColumn(new Column("legacy_column"));
        TriggerHistory triggerHistory = new TriggerHistory("sym_test_table", "id", "id,legacy_column");
        when(triggerRouterService.findTriggerHistory(null, null, "sym_test_table")).thenReturn(triggerHistory);
        listener.dropTriggers(currentModel, "sym_test_table", "legacy_column");
        verify(triggerRouterService, times(1)).dropTriggers(triggerHistory);
    }

    @Test
    void testCheckForDroppedColumns_ColumnDroppedInDesiredModel_DropsTriggersForTable() {
        Table currentOrderTable = new Table("sym_order");
        currentOrderTable.addColumn(new Column("order_id"));
        currentOrderTable.addColumn(new Column("legacy_status"));
        currentModel.addTable(currentOrderTable);
        Database desiredModel = new Database();
        Table desiredOrderTable = new Table("sym_order");
        desiredOrderTable.addColumn(new Column("order_id"));
        desiredModel.addTable(desiredOrderTable);
        TriggerHistory triggerHistory = new TriggerHistory("sym_order", "order_id", "order_id,legacy_status");
        when(triggerRouterService.findTriggerHistory(null, null, "sym_order")).thenReturn(triggerHistory);
        listener.checkForDroppedColumns(currentModel, desiredModel);
        verify(triggerRouterService, times(1)).dropTriggers(triggerHistory);
    }

    @Test
    void testCheckForDroppedColumns_NoColumnDropped_NeverDropsTriggers() {
        Table currentOrderTable = new Table("sym_order");
        currentOrderTable.addColumn(new Column("order_id"));
        currentModel.addTable(currentOrderTable);
        Database desiredModel = new Database();
        Table desiredOrderTable = new Table("sym_order");
        desiredOrderTable.addColumn(new Column("order_id"));
        desiredModel.addTable(desiredOrderTable);
        listener.checkForDroppedColumns(currentModel, desiredModel);
        verify(triggerRouterService, never()).findTriggerHistory(any(), any(), any());
    }

    @Test
    void testFixInformixTriggerLongVarcharColumns_NotInformix_LeavesColumnUnchanged() {
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        Database desiredModel = new Database();
        Table triggerTable = new Table("sym_trigger");
        Column longVarcharColumn = new Column("source_table_name", false, Types.LONGVARCHAR, 100, 0);
        triggerTable.addColumn(longVarcharColumn);
        desiredModel.addTable(triggerTable);
        listener.fixInformixTriggerLongVarcharColumns("sym", desiredModel);
        assertEquals(Types.LONGVARCHAR, longVarcharColumn.getMappedTypeCode(), "A non-Informix platform should leave the column type unchanged");
    }

    @Test
    void testFixInformixTriggerLongVarcharColumns_Informix_DowngradesLongVarcharColumnToVarchar255() {
        stubDatabasePlatform(DatabaseNamesConstants.INFORMIX);
        Database desiredModel = new Database();
        Table triggerTable = new Table("sym_trigger");
        Column longVarcharColumn = new Column("source_table_name", false, Types.LONGVARCHAR, 100, 0);
        triggerTable.addColumn(longVarcharColumn);
        desiredModel.addTable(triggerTable);
        listener.fixInformixTriggerLongVarcharColumns("sym", desiredModel);
        assertEquals(Types.VARCHAR, longVarcharColumn.getMappedTypeCode(), "Informix should downgrade a LONGVARCHAR column to VARCHAR");
        assertEquals(255, longVarcharColumn.getSizeAsInt(), "Informix should size the downgraded column at 255");
    }

    @Test
    void testMigrateOracleTransactionViewParameters_LegacyTransactionViewFlagEnabled_SavesNewParameter() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is(ParameterConstants.DBDIALECT_ORACLE_USE_TRANSACTION_VIEW_LEGACY)).thenReturn(true);
        when(parameterService.getLong(ParameterConstants.DBDIALECT_ORACLE_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS_LEGACY, 60000)).thenReturn(60000L);
        listener.migrateOracleTransactionViewParameters();
        verify(parameterService, times(1)).saveParameter(ParameterConstants.ROUTING_GAPS_USE_TRANSACTION_VIEW, true, "upgrade");
        verify(parameterService, never()).saveParameter(eq(ParameterConstants.ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS), any(), anyString());
    }

    @Test
    void testMigrateOracleTransactionViewParameters_LegacyThresholdChanged_SavesNewParameter() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.getLong(ParameterConstants.DBDIALECT_ORACLE_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS_LEGACY, 60000)).thenReturn(30000L);
        listener.migrateOracleTransactionViewParameters();
        verify(parameterService, times(1)).saveParameter(ParameterConstants.ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS, 30000L, "upgrade");
        verify(parameterService, never()).saveParameter(eq(ParameterConstants.ROUTING_GAPS_USE_TRANSACTION_VIEW), any(), anyString());
    }

    @Test
    void testMigrateOracleTransactionViewParameters_NothingChanged_NeverSavesParameters() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.getLong(ParameterConstants.DBDIALECT_ORACLE_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS_LEGACY, 60000)).thenReturn(60000L);
        listener.migrateOracleTransactionViewParameters();
        verify(parameterService, never()).saveParameter(anyString(), any(), anyString());
    }

    @Test
    void testBeforeUpgradeFromPre3_8_TransformTableHasUpdateActionColumn_RunsFixupAndDeletesRows() {
        Table transformTable = new Table("sym_transform_table");
        transformTable.addColumn(new Column("update_action"));
        currentModel.addTable(transformTable);
        currentModel.addTable(new Table("sym_data_gap"));
        currentModel.addTable(new Table("sym_node_communication"));
        boolean result = listener.beforeUpgradeFromPre3_8("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "All pre-3.8 fix-up steps should succeed");
        verify(sqlTemplate, times(1)).update("update sym_transform_table set update_action = 'UPD_ROW' where update_action is null");
        verify(sqlTemplate, times(1)).update("delete from sym_data_gap");
        verify(sqlTemplate, times(1)).update("delete from sym_node_communication");
    }

    @Test
    void testBeforeUpgradeFromPre3_8_TransformTableMissing_SkipsFixupButStillDeletesRows() {
        currentModel.addTable(new Table("sym_data_gap"));
        currentModel.addTable(new Table("sym_node_communication"));
        boolean result = listener.beforeUpgradeFromPre3_8("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing transform_table should not block the delete steps");
        verify(sqlTemplate, never()).update("update sym_transform_table set update_action = 'UPD_ROW' where update_action is null");
        verify(sqlTemplate, times(1)).update("delete from sym_data_gap");
        verify(sqlTemplate, times(1)).update("delete from sym_node_communication");
    }

    @Test
    void testBeforeUpgradeFromPre3_8_DeleteFails_ReturnsFalse() {
        currentModel.addTable(new Table("sym_data_gap"));
        when(sqlTemplate.update("delete from sym_data_gap")).thenThrow(new RuntimeException("table locked"));
        boolean result = listener.beforeUpgradeFromPre3_8("sym", currentModel, sqlTemplate, sqlScript);
        assertFalse(result, "A failed delete should cause the whole step to report failure");
    }

    @Test
    void testBeforeUpgradeFromPre3_10_AsePlatform_DropsBothForeignKeyConstraints() {
        stubDatabasePlatform(DatabaseNamesConstants.ASE);
        currentModel.addTable(new Table("sym_node_identity"));
        currentModel.addTable(new Table("sym_node_security"));
        boolean result = listener.beforeUpgradeFromPre3_10("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Dropping the ASE-specific foreign keys should succeed");
        verify(sqlTemplate, times(1)).update("alter table sym_node_identity drop constraint sym_fk_ident_2_node");
        verify(sqlTemplate, times(1)).update("alter table sym_node_security drop constraint sym_fk_sec_2_node");
    }

    @Test
    void testBeforeUpgradeFromPre3_10_NonAsePlatform_SkipsForeignKeyDrops() {
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        boolean result = listener.beforeUpgradeFromPre3_10("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A non-ASE platform should skip the foreign key drops entirely");
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testBeforeUpgradeFromPre3_10_AsePlatformConstraintDropFails_ReturnsFalse() {
        stubDatabasePlatform(DatabaseNamesConstants.ASE);
        currentModel.addTable(new Table("sym_node_identity"));
        currentModel.addTable(new Table("sym_node_security"));
        when(sqlTemplate.update("alter table sym_node_identity drop constraint sym_fk_ident_2_node")).thenThrow(new RuntimeException("no such constraint"));
        boolean result = listener.beforeUpgradeFromPre3_10("sym", currentModel, sqlTemplate, sqlScript);
        assertFalse(result, "A failed foreign key drop should cause the step to report failure");
    }

    @Test
    void testBeforeUpgradeFromPre3_11_ForceFixFlagEnabled_RunsFixDataEventAndReturnsTrue() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is("upgrade.force.fix.data.event")).thenReturn(true);
        IDatabasePlatform databasePlatform = stubDatabasePlatform(DatabaseNamesConstants.H2);
        when(databasePlatform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(sqlTemplate.query(anyString())).thenReturn(Collections.emptyList());
        boolean result = listener.beforeUpgradeFromPre3_11("sym");
        assertTrue(result, "Forcing the fix should run fixDataEvent3_11 and succeed when there are no duplicate rows");
        verify(parameterService, never()).is("upgrade.skip.fix.data.event");
    }

    @Test
    void testBeforeUpgradeFromPre3_11_SkipFixFlagEnabled_NeverRunsFixDataEvent() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is("upgrade.force.fix.data.event")).thenReturn(false);
        when(parameterService.is("upgrade.skip.fix.data.event")).thenReturn(true);
        boolean result = listener.beforeUpgradeFromPre3_11("sym");
        assertTrue(result, "Skipping the fix should still report success without scanning for duplicates");
        verify(engine, never()).getDatabasePlatform();
    }

    @Test
    void testBeforeUpgradeFromPre3_11_NoDuplicateTriggerRouterPairs_NeverRunsFixDataEvent() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is("upgrade.force.fix.data.event")).thenReturn(false);
        when(parameterService.is("upgrade.skip.fix.data.event")).thenReturn(false);
        when(parameterService.getNodeGroupId()).thenReturn("group1");
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.query(anyString(), any(Object[].class))).thenReturn(Collections.emptyList());
        boolean result = listener.beforeUpgradeFromPre3_11("sym");
        assertTrue(result, "No duplicate trigger/router pairs means there is nothing to fix");
        verify(engine, never()).getDatabasePlatform();
    }

    @Test
    void testBeforeUpgradeFromPre3_11_DuplicateTriggerRouterPairFound_RunsFixDataEventAndReturnsTrue() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is("upgrade.force.fix.data.event")).thenReturn(false);
        when(parameterService.is("upgrade.skip.fix.data.event")).thenReturn(false);
        when(parameterService.getNodeGroupId()).thenReturn("group1");
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        Row duplicateRow = new Row(new String[] { "trigger_id", "target_node_group_id" }, new Object[] { "trig1", "group1" });
        when(sqlTemplate.query(anyString(), any(Object[].class))).thenReturn(Arrays.asList(duplicateRow, duplicateRow));
        IDatabasePlatform databasePlatform = stubDatabasePlatform(DatabaseNamesConstants.H2);
        when(databasePlatform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(sqlTemplate.query(anyString())).thenReturn(Collections.emptyList());
        boolean result = listener.beforeUpgradeFromPre3_11("sym");
        assertTrue(result, "A duplicate trigger/router pair should trigger the data_event fix, which succeeds when there are no duplicate rows");
    }

    @Test
    void testBeforeUpgradeFromPre3_12_RegistrationServerWithNonOracleAsePlatform_FixesRouterTypeAndReturnsTrue() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.isRegistrationServer()).thenReturn(true);
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        boolean result = listener.beforeUpgradeFromPre3_12("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Fixing router_type on a non-Oracle/ASE platform should succeed");
        verify(sqlTemplate, times(1)).update("update sym_router set router_type = 'default' where router_type is null");
    }

    @Test
    void testBeforeUpgradeFromPre3_12_RegistrationServerRouterTypeUpdateFails_ReturnsFalse() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.isRegistrationServer()).thenReturn(true);
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        when(sqlTemplate.update("update sym_router set router_type = 'default' where router_type is null")).thenThrow(new RuntimeException("locked"));
        boolean result = listener.beforeUpgradeFromPre3_12("sym", currentModel, sqlTemplate, sqlScript);
        assertFalse(result, "A failed router_type fix-up should cause the step to report failure");
    }

    @Test
    void testBeforeUpgradeFromPre3_12_NotRegistrationServer_SkipsRouterTypeFixup() {
        stubParameterService();
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        boolean result = listener.beforeUpgradeFromPre3_12("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A non-registration server should skip the router_type fix-up and still succeed");
        verify(sqlTemplate, never()).update("update sym_router set router_type = 'default' where router_type is null");
    }

    @Test
    void testBeforeUpgradeFromPre3_12_OraclePlatform_DropsDataPrimaryKeyConstraint() {
        stubParameterService();
        stubDatabasePlatform(DatabaseNamesConstants.ORACLE);
        currentModel.addTable(new Table("sym_data"));
        boolean result = listener.beforeUpgradeFromPre3_12("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Dropping the sym_data primary key on Oracle should succeed");
        verify(sqlTemplate, times(1)).update("alter table sym_data drop constraint sym_data_pk");
    }

    @Test
    void testBeforeUpgradeFromPre3_12_AsePlatform_DropsIndexAndForeignKeyConstraints() {
        stubParameterService();
        stubDatabasePlatform(DatabaseNamesConstants.ASE);
        currentModel.addTable(new Table("sym_data"));
        currentModel.addTable(new Table("sym_trigger_router"));
        currentModel.addTable(new Table("sym_file_trigger_router"));
        boolean result = listener.beforeUpgradeFromPre3_12("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Dropping the ASE-specific index and foreign keys should succeed");
        verify(sqlTemplate, times(1)).update("drop index sym_data.sym_idx_d_channel_id");
        verify(sqlTemplate, times(1)).update("alter table sym_trigger_router drop constraint sym_fk_tr_2_rtr");
        verify(sqlTemplate, times(1)).update("alter table sym_file_trigger_router drop constraint sym_fk_ftr_2_rtr");
    }

    @Test
    void testBeforeUpgradeFromPre3_15_MssqlPlatform_DropsPrimaryKeysFromPre315Tables() {
        stubDatabasePlatform(DatabaseNamesConstants.MSSQL2016);
        when(sqlTemplate.queryForString(anyString())).thenReturn("sym_pk_x");
        currentModel.addTable(new Table("sym_table_reload_request"));
        currentModel.addTable(new Table("sym_registration_request"));
        boolean result = listener.beforeUpgradeFromPre3_15("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Dropping primary keys on MSSQL should succeed");
        verify(sqlTemplate, times(1)).update("alter table sym_table_reload_request drop constraint sym_pk_x");
        verify(sqlTemplate, times(1)).update("alter table sym_registration_request drop constraint sym_pk_x");
    }

    @Test
    void testBeforeUpgradeFromPre3_15_OraclePlatform_TruncatesPre315Tables() {
        stubDatabasePlatform(DatabaseNamesConstants.ORACLE122);
        currentModel.addTable(new Table("sym_table_reload_request"));
        currentModel.addTable(new Table("sym_registration_request"));
        boolean result = listener.beforeUpgradeFromPre3_15("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Truncating pre-3.15 tables on Oracle should succeed");
        verify(sqlTemplate, times(1)).update("truncate table sym_table_reload_request");
        verify(sqlTemplate, times(1)).update("truncate table sym_registration_request");
    }

    @Test
    void testBeforeUpgradeFromPre3_15_NonMssqlNonOraclePlatform_SkipsBothBranches() {
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        boolean result = listener.beforeUpgradeFromPre3_15("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A platform that is neither MSSQL nor Oracle should skip both fix-up branches");
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testBeforeUpgradeFromPre3_16_BothTablesPresent_DropsBothAndReturnsTrue() {
        currentModel.addTable(new Table("sym_design_diagram"));
        currentModel.addTable(new Table("sym_diagram_group"));
        boolean result = listener.beforeUpgradeFromPre3_16("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Dropping both design-diagram tables should succeed");
        verify(sqlTemplate, times(1)).update("drop table sym_design_diagram");
        verify(sqlTemplate, times(1)).update("drop table sym_diagram_group");
    }

    @Test
    void testBeforeUpgradeFromPre3_16_DiagramGroupTableMissing_DropsOnlyDesignDiagram() {
        currentModel.addTable(new Table("sym_design_diagram"));
        boolean result = listener.beforeUpgradeFromPre3_16("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing diagram_group table should be skipped without affecting the result");
        verify(sqlTemplate, times(1)).update("drop table sym_design_diagram");
        verify(sqlTemplate, never()).update("drop table sym_diagram_group");
    }

    @Test
    void testBeforeUpgradeFromPre3_17_NodeChannelCtlTablePresent_DropsTableAndReturnsTrue() {
        currentModel.addTable(new Table("sym_node_channel_ctl"));
        boolean result = listener.beforeUpgradeFromPre3_17("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Dropping the node_channel_ctl table should succeed");
        verify(sqlTemplate, times(1)).update("drop table sym_node_channel_ctl");
    }

    @Test
    void testBeforeUpgradeFromPre3_17_NodeChannelCtlTableMissing_SkipsDropAndReturnsTrue() {
        boolean result = listener.beforeUpgradeFromPre3_17("sym", currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing node_channel_ctl table means there is nothing to drop");
        verify(sqlTemplate, never()).update("drop table sym_node_channel_ctl");
    }

    @Test
    void testSetSymmetricEngine_WiresProvidedEngineForSubsequentCalls() {
        DatabaseUpgradeListener freshListener = new DatabaseUpgradeListener();
        ISymmetricEngine anotherEngine = mock(ISymmetricEngine.class);
        ITriggerRouterService anotherTriggerRouterService = mock(ITriggerRouterService.class);
        when(anotherEngine.getTriggerRouterService()).thenReturn(anotherTriggerRouterService);
        freshListener.setSymmetricEngine(anotherEngine);
        Table table = currentModel.findTable("sym_test_table");
        boolean result = freshListener.dropTableDueToUpgrade(table, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "The listener should use the engine instance passed to setSymmetricEngine");
        verify(anotherTriggerRouterService, times(1)).findTriggerHistory(null, null, "sym_test_table");
        verify(triggerRouterService, never()).findTriggerHistory(any(), any(), any());
    }

    @Test
    void testBeforeUpgrade_NothingToUpgrade_ReturnsEmptyStringWithoutRunningAnyFixup() throws Exception {
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        stubParameterService();
        currentModel.addTable(new Table("sym_monitor"));
        Database desiredModel = new Database();
        String result = listener.beforeUpgrade(null, "sym", currentModel, desiredModel);
        assertEquals("", result, "beforeUpgrade always returns an empty command string");
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testBeforeUpgrade_Pre3_8UpgradeDetected_DispatchesToBeforeUpgradeFromPre3_8() throws Exception {
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        stubParameterService();
        currentModel.addTable(new Table("sym_node"));
        currentModel.addTable(new Table("sym_data_gap"));
        Database desiredModel = new Database();
        desiredModel.addTable(new Table("sym_monitor"));
        String result = listener.beforeUpgrade(null, "sym", currentModel, desiredModel);
        assertEquals("", result, "beforeUpgrade always returns an empty command string");
        verify(sqlTemplate, times(1)).update("delete from sym_data_gap");
    }

    @Test
    void testShouldFixDataEvent3_11_ForceFixFlagEnabled_ReturnsTrueWithoutCheckingSkipFlagOrScanning() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is("upgrade.force.fix.data.event")).thenReturn(true);
        boolean result = listener.shouldFixDataEvent3_11("sym");
        assertTrue(result, "A forced fix flag should short-circuit to true");
        verify(parameterService, never()).is("upgrade.skip.fix.data.event");
        verify(engine, never()).getSqlTemplate();
    }

    @Test
    void testShouldFixDataEvent3_11_SkipFixFlagEnabled_ReturnsFalseWithoutScanning() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is("upgrade.force.fix.data.event")).thenReturn(false);
        when(parameterService.is("upgrade.skip.fix.data.event")).thenReturn(true);
        boolean result = listener.shouldFixDataEvent3_11("sym");
        assertFalse(result, "An explicit skip flag should avoid scanning for duplicate trigger/router pairs");
        verify(engine, never()).getSqlTemplate();
    }

    @Test
    void testShouldFixDataEvent3_11_NoDuplicateTriggerRouterPairs_ReturnsFalse() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is("upgrade.force.fix.data.event")).thenReturn(false);
        when(parameterService.is("upgrade.skip.fix.data.event")).thenReturn(false);
        when(parameterService.getNodeGroupId()).thenReturn("group1");
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.query(anyString(), any(Object[].class))).thenReturn(Collections.emptyList());
        boolean result = listener.shouldFixDataEvent3_11("sym");
        assertFalse(result, "No duplicate trigger/router pairs means there is nothing to fix");
    }

    @Test
    void testShouldFixDataEvent3_11_DuplicateTriggerRouterPairFound_ReturnsTrue() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.is("upgrade.force.fix.data.event")).thenReturn(false);
        when(parameterService.is("upgrade.skip.fix.data.event")).thenReturn(false);
        when(parameterService.getNodeGroupId()).thenReturn("group1");
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        Row duplicateRow = new Row(new String[] { "trigger_id", "target_node_group_id" }, new Object[] { "trig1", "group1" });
        when(sqlTemplate.query(anyString(), any(Object[].class))).thenReturn(Arrays.asList(duplicateRow, duplicateRow));
        boolean result = listener.shouldFixDataEvent3_11("sym");
        assertTrue(result, "A repeated (trigger_id, target_node_group_id) pair indicates duplicate data_event rows to fix");
    }

    @Test
    void testFixDataEvent3_11_NoDuplicateRows_ReturnsTrueWithoutStartingTransaction() {
        IDatabasePlatform databasePlatform = stubDatabasePlatform(DatabaseNamesConstants.H2);
        when(databasePlatform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(sqlTemplate.query("select batch_id, data_id, max(router_id) router_id from sym_data_event group by batch_id, data_id having count(*) > 1"))
                .thenReturn(Collections.emptyList());
        boolean result = listener.fixDataEvent3_11("sym");
        assertTrue(result, "No duplicate rows means there is nothing to delete");
        verify(engine, never()).getSqlTemplate();
    }

    @Test
    void testFixDataEvent3_11_DuplicateRowsPresent_DeletesExtraRowsAndCommits() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_DATA_IDS)).thenReturn(1000);
        IDatabasePlatform databasePlatform = stubDatabasePlatform(DatabaseNamesConstants.H2);
        when(databasePlatform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        Row duplicateRow = new Row(new String[] { "batch_id", "data_id", "router_id" }, new Object[] { 100L, 200L, "trig1" });
        when(sqlTemplate.query("select batch_id, data_id, max(router_id) router_id from sym_data_event group by batch_id, data_id having count(*) > 1"))
                .thenReturn(Collections.singletonList(duplicateRow));
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(transaction);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getSqlTypeForIds()).thenReturn(Types.BIGINT);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        boolean result = listener.fixDataEvent3_11("sym");
        assertTrue(result, "Deleting the extra duplicate rows should succeed");
        verify(transaction, times(1)).setInBatchMode(true);
        verify(transaction, times(1)).prepare("delete from sym_data_event where batch_id = ? and data_id = ? and router_id != ?");
        ArgumentCaptor<Object[]> valuesCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(transaction, times(1)).addRow(eq(duplicateRow), valuesCaptor.capture(), any(int[].class));
        assertEquals(100L, valuesCaptor.getValue()[0], "The delete parameters should carry the row's batch_id first");
        assertEquals(200L, valuesCaptor.getValue()[1], "The delete parameters should carry the row's data_id second");
        assertEquals("trig1", valuesCaptor.getValue()[2], "The delete parameters should carry the surviving router_id last");
        verify(transaction, times(1)).commit();
        verify(transaction, never()).rollback();
        verify(transaction, times(1)).close();
    }

    @Test
    void testFixDataEvent3_11_AddRowThrows_RollsBackAndClosesTransactionThenPropagates() {
        IParameterService parameterService = stubParameterService();
        when(parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_DATA_IDS)).thenReturn(1000);
        IDatabasePlatform databasePlatform = stubDatabasePlatform(DatabaseNamesConstants.H2);
        when(databasePlatform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        Row duplicateRow = new Row(new String[] { "batch_id", "data_id", "router_id" }, new Object[] { 100L, 200L, "trig1" });
        when(sqlTemplate.query("select batch_id, data_id, max(router_id) router_id from sym_data_event group by batch_id, data_id having count(*) > 1"))
                .thenReturn(Collections.singletonList(duplicateRow));
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(transaction);
        when(transaction.addRow(any(), any(), any())).thenThrow(new RuntimeException("delete failed"));
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> listener.fixDataEvent3_11("sym"),
                "An addRow failure should propagate rather than be swallowed");
        assertEquals("delete failed", thrown.getMessage(), "The original failure should propagate unchanged");
        verify(transaction, times(1)).rollback();
        verify(transaction, never()).commit();
        verify(transaction, times(1)).close();
    }

    @Test
    void testDropSymTriggersIfNecessary_ColumnChangeDetected_DropsTriggersForAffectedTable() {
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(extensionService.getExtensionPointList(IAlterDatabaseInterceptor.class)).thenReturn(Collections.emptyList());
        IDatabasePlatform databasePlatform = stubDatabasePlatform(DatabaseNamesConstants.H2);
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(databasePlatform.getDdlBuilder()).thenReturn(ddlBuilder);
        Database desiredModel = new Database();
        AddColumnChange addColumnChange = new AddColumnChange(new Table("sym_order"), new Column("new_col"), null, null);
        when(ddlBuilder.getDetectedChanges(eq(currentModel), eq(desiredModel), any(IAlterDatabaseInterceptor[].class))).thenReturn(Collections.singletonList(
                addColumnChange));
        listener.dropSymTriggersIfNecessary(currentModel, desiredModel);
        verify(triggerRouterService, times(1)).dropTriggers(Collections.singleton("sym_order"));
    }

    @Test
    void testDropSymTriggersIfNecessary_ChangeNotAffectingTriggers_DropsTriggersForEmptySet() {
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(extensionService.getExtensionPointList(IAlterDatabaseInterceptor.class)).thenReturn(Collections.emptyList());
        IDatabasePlatform databasePlatform = stubDatabasePlatform(DatabaseNamesConstants.H2);
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(databasePlatform.getDdlBuilder()).thenReturn(ddlBuilder);
        Database desiredModel = new Database();
        RemoveTableChange removeTableChange = new RemoveTableChange(new Table("sym_order"));
        when(ddlBuilder.getDetectedChanges(eq(currentModel), eq(desiredModel), any(IAlterDatabaseInterceptor[].class))).thenReturn(Collections.singletonList(
                removeTableChange));
        listener.dropSymTriggersIfNecessary(currentModel, desiredModel);
        verify(triggerRouterService, times(1)).dropTriggers(Collections.emptySet());
    }

    @Test
    void testAfterUpgrade_NoUpgradeFlagsSet_SyncsTriggersAndPullsConfigWithoutFixups() throws Exception {
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        IPullService pullService = stubPullService();
        String result = listener.afterUpgrade(null, "sym", new Database());
        assertEquals("", result, "afterUpgrade always returns an empty command string");
        verify(triggerRouterService, times(1)).syncTriggers();
        verify(pullService, times(1)).pullConfigData(false);
        verify(engine, never()).getSqlTemplate();
    }

    @Test
    void testAfterUpgrade_Pre314WithKnownNodeId_FixesExtractRequestSourceNodeId() throws Exception {
        listener.isUpgradeFromPre314 = true;
        when(engine.getNodeId()).thenReturn("node1");
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        stubPullService();
        listener.afterUpgrade(null, "sym", new Database());
        verify(sqlTemplate, times(1)).update("update sym_extract_request set source_node_id = ? where source_node_id = 'default'", "node1");
    }

    @Test
    void testAfterUpgrade_Pre314WithoutNodeId_SkipsExtractRequestFixup() throws Exception {
        listener.isUpgradeFromPre314 = true;
        when(engine.getNodeId()).thenReturn(null);
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        stubPullService();
        listener.afterUpgrade(null, "sym", new Database());
        verify(engine, never()).getSqlTemplate();
    }

    @Test
    void testAfterUpgrade_Pre38_FixesSequenceCacheSizeAndChannelBatchSize() throws Exception {
        listener.isUpgradeFromPre38 = true;
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        stubPullService();
        listener.afterUpgrade(null, "sym", new Database());
        verify(sqlTemplate, times(1)).update("update sym_sequence set cache_size = 10 where sequence_name = ?", Constants.SEQUENCE_OUTGOING_BATCH);
        verify(sqlTemplate, times(1)).update("update  sym_channel set max_batch_size = 10000 where reload_flag = 1 ");
    }

    @Test
    void testAfterUpgrade_Pre3125OnRegistrationServer_FixesInitialLoadEndTime() throws Exception {
        listener.isUpgradeFromPre3125 = true;
        IParameterService parameterService = stubParameterService();
        when(parameterService.isRegistrationServer()).thenReturn(true);
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        stubPullService();
        listener.afterUpgrade(null, "sym", new Database());
        verify(sqlTemplate, times(1)).update("update sym_node_security set initial_load_end_time = initial_load_time where initial_load_time is not null");
    }

    @Test
    void testAfterUpgrade_Pre3125NotRegistrationServer_SkipsInitialLoadEndTimeFixup() throws Exception {
        listener.isUpgradeFromPre3125 = true;
        stubParameterService();
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        stubPullService();
        listener.afterUpgrade(null, "sym", new Database());
        verify(engine, never()).getSqlTemplate();
    }

    @Test
    void testAfterUpgrade_Pre316_FixesExtractRequestLoadedTime() throws Exception {
        listener.isUpgradeFromPre316 = true;
        when(engine.getNodeId()).thenReturn("node1");
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        stubPullService();
        listener.afterUpgrade(null, "sym", new Database());
        verify(sqlTemplate, times(1)).update(
                "update sym_extract_request set loaded_time = last_update_time where source_node_id = ? and loaded_time is null"
                        + " and load_id in (select load_id from sym_table_reload_status where source_node_id = ? and completed = 1)",
                "node1", "node1");
    }

    @Test
    void testAfterUpgrade_Pre317WithNonBulkReloadChannel_SwitchesChannelToBulkAndMigratesParameters() throws Exception {
        listener.isUpgradeFromPre317 = true;
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        Channel reloadChannel = new Channel();
        reloadChannel.setDataLoaderType("default");
        when(configurationService.getChannel(Constants.CHANNEL_RELOAD)).thenReturn(reloadChannel);
        stubParameterService();
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        stubPullService();
        listener.afterUpgrade(null, "sym", new Database());
        assertEquals("bulk", reloadChannel.getDataLoaderType(), "A non-bulk reload channel should be switched to bulk");
        verify(configurationService, times(1)).saveChannel(reloadChannel, false);
    }

    @Test
    void testAfterUpgrade_Pre317WithAlreadyBulkReloadChannel_SkipsSavingChannel() throws Exception {
        listener.isUpgradeFromPre317 = true;
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        Channel reloadChannel = new Channel();
        reloadChannel.setDataLoaderType("bulk");
        when(configurationService.getChannel(Constants.CHANNEL_RELOAD)).thenReturn(reloadChannel);
        stubParameterService();
        stubDatabasePlatform(DatabaseNamesConstants.POSTGRESQL);
        stubPullService();
        listener.afterUpgrade(null, "sym", new Database());
        verify(configurationService, never()).saveChannel(any(Channel.class), anyBoolean());
    }

    @Test
    void testAfterUpgrade_H2Platform_InvokesH2SequenceCheck() throws Exception {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getSequenceName(SequenceIdentifier.DATA)).thenReturn("sym_data");
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.queryForInt("select count(*) from information_schema.sequences where sequence_name = ?", "SYM_DATA_SEQ")).thenReturn(1);
        stubDatabasePlatform(DatabaseNamesConstants.H2);
        stubPullService();
        listener.afterUpgrade(symmetricDialect, "sym", new Database());
        verify(sqlTemplate, times(1)).queryForInt("select count(*) from information_schema.sequences where sequence_name = ?", "SYM_DATA_SEQ");
        verify(sqlTemplate, never()).startSqlTransaction();
    }
}
