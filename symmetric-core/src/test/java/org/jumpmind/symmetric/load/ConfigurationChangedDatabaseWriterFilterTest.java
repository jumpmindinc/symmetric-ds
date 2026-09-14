/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.jumpmind.db.model.RelationsList;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ConfigurationChangedHelper;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IInitialLoadService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationChangedDatabaseWriterFilterTest {
    private static final String SUFFIX = ConfigurationChangedHelper.class.getSimpleName();
    private static final String CTX_KEY_RESYNC_ALLOWED = "ResyncAllowed." + SUFFIX;
    private static final String CTX_KEY_RESYNC_NEEDED = "Resync." + SUFFIX;
    private ConfigurationChangedDatabaseWriterFilter filter;
    private ISymmetricEngine engine;
    private DataContext context;
    private ParameterService parameterService;
    private CsvData data;
    private Table otherTable;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(ParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getTablePrefix()).thenReturn("");
        filter = new ConfigurationChangedDatabaseWriterFilter(engine);
        context = new DataContext();
        Batch batch = new Batch();
        batch.setBatchId(1L);
        context.setBatch(batch);
        data = new CsvData();
        otherTable = new Table("OTHER_TABLE");
    }

    @Test
    void testAfterWriteForAlterSqlEvent() {
        String[] testData = new String[] {
                "INSERT INTO TEST VALUES (1);\n " + "ALTER TABLE TEST ADD NEW_COL VARCHAR(25);\n" + "INSERT INTO TEST VALUES (2);" };
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
        callAfterWriteWithSqlEvent(testData, true);
        assertEquals(true, context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWriteForAlterNoTableSqlEvent() {
        String[] testData = new String[] {
                "INSERT INTO TEST VALUES (1);\n " + "ALTER INDEX TEST ON USERS(NAME);\n" + "INSERT INTO TEST VALUES (2);" };
        callAfterWriteWithSqlEvent(testData, true);
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWriteForCreateSqlEvent() {
        String[] testData = new String[] {
                "INSERT INTO OTHER VALUES (1);\n " + "CREATE TABLE TEST (ID int);\n" + "INSERT INTO ANOTHER VALUES (1);" };
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
        callAfterWriteWithSqlEvent(testData, true);
        assertEquals(true, context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWriteForCreateNoTableSqlEvent() {
        String[] testData = new String[] {
                "INSERT INTO OTHER VALUES (1);\n " + "CREATE INDEX TEST ON USERS(NAME);\n" + "INSERT INTO ANOTHER VALUES (1);" };
        callAfterWriteWithSqlEvent(testData, true);
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWriteForSqlEventWithResyncAllowed() {
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
        callAfterWriteWithSqlEvent(new String[] { "CREATE TABLE TEST (ID int);" }, true);
        assertEquals(true, context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWriteForSqlEventWithResyncNotAllowed() {
        callAfterWriteWithSqlEvent(new String[] { "CREATE TABLE TEST (ID int);" }, false);
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testBeforeWrite_withTableNotMatchingPrefix_returnsTrueWithoutProcessing() {
        when(parameterService.getTablePrefix()).thenReturn("sym");
        ConfigurationChangedDatabaseWriterFilter prefixedFilter = new ConfigurationChangedDatabaseWriterFilter(engine);
        assertTrue(prefixedFilter.beforeWrite(context, otherTable, data));
        verify(engine, never()).getExtensionService();
    }

    @Test
    void testBeforeWrite_withVirtualRegistrationBatch_setsSyncTriggersAllowed() {
        Batch batch = new Batch();
        batch.setBatchId(Constants.VIRTUAL_BATCH_FOR_REGISTRATION);
        context.setBatch(batch);
        assertTrue(filter.beforeWrite(context, otherTable, data));
        assertEquals(true, context.get(CTX_KEY_RESYNC_ALLOWED));
    }

    @Test
    void testBeforeWrite_withNodeSecurityInsertStartingInitialLoad_notifiesReloadListeners() {
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(new NodeSecurity());
        filter.syncStarted(context);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        IClientReloadListener listener = mock(IClientReloadListener.class);
        when(extensionService.getExtensionPointList(IClientReloadListener.class)).thenReturn(Arrays.asList(listener));
        CsvData insertData = new CsvData(DataEventType.INSERT);
        insertData.putParsedData(CsvData.ROW_DATA, new String[] { "node1", "1", "2024-01-01 00:00:00.0", "", "0" });
        assertTrue(filter.beforeWrite(context, newNodeSecurityTable(), insertData));
        verify(listener).reloadStarted();
    }

    @Test
    void testBeforeWrite_withNodeSecurityInsertLoadAlreadyInProgress_doesNotNotifyListeners() {
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setInitialLoadTime(new Date());
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(nodeSecurity);
        filter.syncStarted(context);
        CsvData insertData = new CsvData(DataEventType.INSERT);
        insertData.putParsedData(CsvData.ROW_DATA, new String[] { "node1", "1", "2024-01-01 00:00:00.0", "", "0" });
        assertTrue(filter.beforeWrite(context, newNodeSecurityTable(), insertData));
        verify(engine, never()).getExtensionService();
    }

    @Test
    void testBeforeWrite_withMonitorSuppressedIds_returnsFalse() {
        Table monitorTable = new Table(null, null, "monitor", new String[] { "monitor_id" }, new String[] { "monitor_id" });
        assertFalse(filter.beforeWrite(context, monitorTable, monitorInsert("SystemBatchErrorMonitor")));
        assertFalse(filter.beforeWrite(context, monitorTable, monitorInsert("SystemLogMonitor")));
        assertFalse(filter.beforeWrite(context, monitorTable, monitorInsert("SystemOfflineNodeMonitor")));
    }

    @Test
    void testBeforeWrite_withMonitorOtherId_returnsTrue() {
        Table monitorTable = new Table(null, null, "monitor", new String[] { "monitor_id" }, new String[] { "monitor_id" });
        assertTrue(filter.beforeWrite(context, monitorTable, monitorInsert("CustomMonitor")));
    }

    @Test
    void testAfterWrite_withTablePrefixMismatch_skipsHandling() {
        when(parameterService.getTablePrefix()).thenReturn("sym");
        ConfigurationChangedDatabaseWriterFilter prefixedFilter = new ConfigurationChangedDatabaseWriterFilter(engine);
        prefixedFilter.beforeWrite(context, otherTable, data);
        CsvData insertData = new CsvData(DataEventType.INSERT);
        prefixedFilter.afterWrite(context, otherTable, insertData);
        verify(engine, never()).getExtensionService();
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWrite_withNodeSecurityUpdateCompletingInitialLoad_requestsSyncTriggersAndDisablesRegistration() {
        completeInitialLoad("node1", 42L, false);
        assertEquals(true, context.get(CTX_KEY_RESYNC_NEEDED));
        verify(engine.getRegistrationService()).setAllowClientRegistration(false);
    }

    @Test
    void testAfterWrite_withNodeSecurityUpdateNotCompleting_doesNotRequestSync() {
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(new NodeSecurity());
        filter.syncStarted(context);
        Batch batch = new Batch(BatchType.LOAD, 1L, null, null, "sourceNode", "node1", false);
        context.setBatch(batch);
        CsvData updateData = new CsvData(DataEventType.UPDATE);
        updateData.putParsedData(CsvData.ROW_DATA, new String[] { "node1", "1", "2024-01-01 00:00:00.0", "2024-01-01 01:00:00.0", "42" });
        Table nodeSecurityTable = newNodeSecurityTable();
        filter.beforeWrite(context, nodeSecurityTable, updateData);
        filter.afterWrite(context, nodeSecurityTable, updateData);
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
        verify(engine, never()).getRegistrationService();
    }

    @Test
    void testBatchCommitted_refreshesNodeSecurityAfterChange() {
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        Table nodeSecurityTable = newNodeSecurityTable();
        CsvData deleteData = new CsvData(DataEventType.DELETE);
        filter.beforeWrite(context, nodeSecurityTable, deleteData);
        filter.afterWrite(context, nodeSecurityTable, deleteData);
        filter.batchCommitted(context);
        verify(nodeService).findNodeSecurity("node1", true);
    }

    @Test
    void testBatchCommitted_withOldClientRegistrationBatch_marksNodeAsRegistered() {
        Batch batch = new Batch(BatchType.LOAD, Constants.VIRTUAL_BATCH_FOR_REGISTRATION, null, null, "sourceNode1", null, false);
        context.setBatch(batch);
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        Node sourceNode = new Node();
        sourceNode.setSymmetricVersion("3.11.0");
        when(nodeService.findNode("sourceNode1")).thenReturn(sourceNode);
        NodeSecurity security = new NodeSecurity();
        security.setRegistrationEnabled(true);
        when(nodeService.findNodeSecurity("node1")).thenReturn(security);
        IRegistrationService registrationService = mock(IRegistrationService.class);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        filter.batchCommitted(context);
        verify(registrationService).markNodeAsRegistered("node1");
    }

    @Test
    void testBatchCommitted_withNewerClientRegistrationBatch_doesNotMarkNodeAsRegistered() {
        Batch batch = new Batch(BatchType.LOAD, Constants.VIRTUAL_BATCH_FOR_REGISTRATION, null, null, "sourceNode1", null, false);
        context.setBatch(batch);
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        Node sourceNode = new Node();
        sourceNode.setSymmetricVersion("3.99.0");
        when(nodeService.findNode("sourceNode1")).thenReturn(sourceNode);
        filter.batchCommitted(context);
        verify(engine, never()).getRegistrationService();
    }

    @Test
    void testSyncStarted_withAutoSyncEnabled_allowsSyncTriggersAndLoadsNodeSecurity() {
        when(parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)).thenReturn(true);
        when(parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS_AFTER_CONFIG_LOADED)).thenReturn(true);
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(new NodeSecurity());
        filter.syncStarted(context);
        assertEquals(true, context.get(CTX_KEY_RESYNC_ALLOWED));
        verify(nodeService).findNodeSecurity("node1", true);
    }

    @Test
    void testSyncStarted_withAutoSyncDisabled_doesNotAllowSyncTriggers() {
        when(parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)).thenReturn(false);
        when(parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS_AFTER_CONFIG_LOADED)).thenReturn(true);
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn(null);
        filter.syncStarted(context);
        assertEquals(false, context.get(CTX_KEY_RESYNC_ALLOWED));
        verify(nodeService, never()).findNodeSecurity(any(), anyBoolean());
    }

    @Test
    void testSyncEnded_withResyncTableNeeded_syncsTriggers() {
        filter.afterWrite(context, otherTable, new CsvData(DataEventType.CREATE));
        when(parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRIGGER_CREATE_BEFORE_INITIAL_LOAD)).thenReturn(true);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getTargetDialect()).thenReturn(symmetricDialect);
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        filter.syncEnded(context, Collections.emptyList(), null);
        verify(triggerRouterService).syncTriggers(any(RelationsList.class), eq(false));
    }

    @Test
    void testSyncEnded_withInitialLoadCompleted_notifiesListenersAndChecksReloadRequest() {
        completeInitialLoad("node1", 42L, true);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        IClientReloadListener listener = mock(IClientReloadListener.class);
        when(extensionService.getExtensionPointList(IClientReloadListener.class)).thenReturn(Arrays.asList(listener));
        IDataService dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        TableReloadRequest reloadRequest = new TableReloadRequest();
        reloadRequest.setCreateTable(true);
        when(dataService.getTableReloadRequest(42L)).thenReturn(reloadRequest);
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        filter.syncEnded(context, Collections.emptyList(), null);
        verify(listener).reloadCompleted();
        verify(triggerRouterService).syncTriggers();
    }

    @Test
    void testSyncEnded_withCancelledTableReload_cancelsInitialLoad() {
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        filter.syncStarted(context);
        Table reloadStatusTable = new Table(null, null, "table_reload_status",
                new String[] { "cancelled", "source_node_id", "load_id" }, new String[] { "load_id" });
        CsvData updateData = new CsvData(DataEventType.UPDATE);
        updateData.putParsedData(CsvData.OLD_DATA, new String[] { "0", "node1", "55" });
        updateData.putParsedData(CsvData.ROW_DATA, new String[] { "1", "node1", "55" });
        filter.beforeWrite(context, reloadStatusTable, updateData);
        filter.afterWrite(context, reloadStatusTable, updateData);
        IDataService dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        TableReloadStatus status = new TableReloadStatus();
        when(dataService.getTableReloadStatusByLoadIdAndSourceNodeId(55L, "node1")).thenReturn(status);
        IInitialLoadService initialLoadService = mock(IInitialLoadService.class);
        when(engine.getInitialLoadService()).thenReturn(initialLoadService);
        filter.syncEnded(context, Collections.emptyList(), null);
        verify(initialLoadService).cancelLoad(status);
    }

    private CsvData monitorInsert(String monitorId) {
        CsvData insertData = new CsvData(DataEventType.INSERT);
        insertData.putParsedData(CsvData.ROW_DATA, new String[] { monitorId });
        return insertData;
    }

    private Table newNodeSecurityTable() {
        return new Table(null, null, "node_security",
                new String[] { "node_id", "initial_load_enabled", "initial_load_time", "initial_load_end_time", "initial_load_id" },
                new String[] { "node_id" });
    }

    private void completeInitialLoad(String nodeId, long loadId, boolean triggerCreateBeforeInitialLoad) {
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentityNodeId()).thenReturn(nodeId);
        when(nodeService.findNodeSecurity(nodeId, true)).thenReturn(new NodeSecurity());
        filter.syncStarted(context);
        Batch batch = new Batch(BatchType.LOAD, 1L, null, null, "sourceNode", nodeId, false);
        context.setBatch(batch);
        when(parameterService.is(ParameterConstants.TRIGGER_CREATE_BEFORE_INITIAL_LOAD)).thenReturn(triggerCreateBeforeInitialLoad);
        when(engine.getRegistrationService()).thenReturn(mock(IRegistrationService.class));
        Table nodeSecurityTable = newNodeSecurityTable();
        CsvData updateData = new CsvData(DataEventType.UPDATE);
        updateData.putParsedData(CsvData.ROW_DATA,
                new String[] { nodeId, "0", "2024-01-01 00:00:00.0", "2024-01-01 01:00:00.0", String.valueOf(loadId) });
        filter.beforeWrite(context, nodeSecurityTable, updateData);
        filter.afterWrite(context, nodeSecurityTable, updateData);
    }

    private void callAfterWriteWithSqlEvent(String[] parsedData, boolean isTriggerResyncAllowed) {
        context.put(CTX_KEY_RESYNC_ALLOWED, isTriggerResyncAllowed);
        data.putParsedData(CsvData.ROW_DATA, parsedData);
        data.setDataEventType(DataEventType.SQL);
        filter.afterWrite(context, null, data);
    }
}
