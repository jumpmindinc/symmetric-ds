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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.load.IReloadGenerator;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.IGroupletService;
import org.jumpmind.symmetric.service.IInitialLoadService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IPurgeService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class DataServiceTest {
    private static final String MAPPER_TABLE = "test_table";
    private static final String MAPPER_TRIGGER_ID = "group.dbo.test_table";
    private static final int MAPPER_HIST_ID = 500;
    ISqlTemplate sqlTemplate;
    ISqlTransaction sqlTransaction;
    IDataService dataService;
    IParameterService parameterService;
    ISymmetricDialect symmetricDialect;
    TableReloadRequest request;
    ISymmetricEngine engine;
    IDatabasePlatform platform;
    public final String strandedTableName = "sym_node_host";
    public final String strandedChannelId = "heartbeat";
    public final String strandedColumnNames = "node_id,host_name,instance_id";
    public final String strandedPkNames = "node_id,host_name";

    @BeforeEach
    public void setUp() throws Exception {
        sqlTemplate = mock(ISqlTemplate.class);
        sqlTransaction = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        symmetricDialect = mock(AbstractSymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        parameterService = mock(ParameterService.class);
        when(parameterService.getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE)).thenReturn(50000000L);
        IExtensionService extensionService = mock(ExtensionService.class);
        engine = mock(AbstractSymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        dataService = new DataService(engine, extensionService);
        // request = mock(TableReloadRequest.class);
    }

    @Test
    public void testFindDataGaps2() throws Exception {
        final List<DataGap> gaps1 = new ArrayList<DataGap>();
        gaps1.add(new DataGap(30953884, 80953883));
        gaps1.add(new DataGap(30953883, 80953883));
        when(sqlTemplate.queryForLong(ArgumentMatchers.anyString())).thenReturn(0L);
        String sql = ArgumentMatchers.anyString();
        @SuppressWarnings("unchecked")
        ISqlRowMapper<DataGap> anyMapper = (ISqlRowMapper<DataGap>) ArgumentMatchers.any();
        when(sqlTemplate.query(sql, anyMapper, (Object[]) ArgumentMatchers.any())).thenReturn(gaps1);
        dataService.findDataGaps();
        verifyNoMoreInteractions(sqlTransaction);
    }

    @Test
    void testInsertTableReloadRequest() throws Exception {
        // mocked interactions
        request = new TableReloadRequest();
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTransaction.prepareAndExecute(ArgumentMatchers.anyString(), (JdbcSqlTransaction) ArgumentMatchers.any())).thenReturn(1);
        dataService.insertTableReloadRequest(request);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @CsvSource({ "" + 0 + "", "" + 1 + "", "" + 2 + "" })
    void testInsertReloadEvents(int scenario) throws Exception {
        // actual variables
        Node targetNode = new Node();
        targetNode.setNodeGroupId("client");
        targetNode.setExternalId("client");
        targetNode.setNodeId("client");
        Node sourceNode = new Node();
        sourceNode.setExternalId("server");
        sourceNode.setNodeGroupId("server");
        sourceNode.setNodeId("server");
        NodeGroupLink link = new NodeGroupLink("server", "client");
        List<Channel> channels = new ArrayList<Channel>();
        Trigger trigger = null;
        if (scenario == 2) {
            trigger = new Trigger("sym_file_snapshot", "default");
        } else {
            trigger = new Trigger("testTable", "default");
        }
        Router router = new Router("testRouter", link);
        TriggerRouter triggerRouter = new TriggerRouter(trigger, router);
        request = new TableReloadRequest();
        TableReloadRequest reloadRequest = new TableReloadRequest();
        reloadRequest.setLoadId(1);
        reloadRequest.setTriggerId("testTable");
        reloadRequest.setRouterId("testRouter");
        TableReloadRequest reloadRequestForAll = new TableReloadRequest();
        reloadRequestForAll.setLoadId(1);
        reloadRequestForAll.setTriggerId("ALL");
        reloadRequestForAll.setRouterId("ALL");
        Table table = new Table("testTable");
        List<String> columns = new ArrayList<String>();
        columns.add("Id");
        columns.add("age");
        columns.add("weight");
        int counter = 0;
        for (String columnName : columns) {
            Column column = new Column(columnName);
            if (counter < 1) {
                column.setPrimaryKey(true);
            }
            table.addColumn(column);
            counter++;
        }
        TableReloadStatus tableReloadStatus = new TableReloadStatus();
        List<TableReloadRequest> reloadRequests = new ArrayList<TableReloadRequest>();
        if (scenario == 2) {
            TableReloadRequest fileSyncReloadRequest = new TableReloadRequest();
            fileSyncReloadRequest.setLoadId(1);
            fileSyncReloadRequest.setTriggerId("sym_file_snapshot");
            fileSyncReloadRequest.setRouterId("testRouter");
            reloadRequests.add(fileSyncReloadRequest);
            Channel channel = new Channel("filesync", 0, 1000, 1000, true,
                    9999999, false, true, true);
            channels.add(channel);
        } else {
            reloadRequests.add(reloadRequest);
        }
        List<TableReloadRequest> reloadRequestsForAll = new ArrayList<TableReloadRequest>();
        reloadRequestsForAll.add(reloadRequestForAll);
        ProcessInfo processInfo = new ProcessInfo();
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        triggerRouters.add(triggerRouter);
        Map<Integer, ExtractRequest> extractRequests = new HashMap<Integer, ExtractRequest>();
        ExtractRequest extractRequest = new ExtractRequest();
        extractRequest.setCreateTime(null);
        extractRequest.setEndBatchId(2l);
        extractRequest.setExtractedMillis(10000);
        extractRequest.setExtractedRows(200);
        extractRequest.setLastLoadedBatchId(2);
        extractRequest.setLastTransferredBatchId(2);
        extractRequest.setLastUpdateTime(null);
        extractRequest.setLoadedMillis(30000);
        extractRequest.setLoadedRows(5);
        extractRequest.setLoadId(0);
        extractRequest.setNodeId("server");
        extractRequest.setParentRequestId(0);
        extractRequest.setQueue(null);
        extractRequest.setRequestId(1);
        extractRequest.setRouterId(null);
        extractRequest.setRows(20000);
        extractRequest.setStartBatchId(0);
        extractRequest.setStatus(null);
        extractRequest.setTableName(null);
        extractRequest.setTransferredMillis(2000);
        extractRequest.setTransferredRows(400);
        extractRequest.setTriggerId(null);
        extractRequest.setTriggerRouter(triggerRouter);
        extractRequests.put(0, extractRequest);
        Map<Integer, List<TriggerRouter>> triggerRouterByHist = new HashMap<Integer, List<TriggerRouter>>();
        triggerRouterByHist.put(0, triggerRouters);
        List<TriggerHistory> triggerHistories = new ArrayList<TriggerHistory>();
        TriggerHistory triggerHistory = new TriggerHistory("testTable", "Id", "Id,age,weight");
        triggerHistory.setTriggerId("testTable");
        triggerHistories.add(triggerHistory);
        Map<String, Channel> channelMap = new HashMap<String, Channel>();
        Channel channel = new Channel("default", 0);
        channelMap.put("default", channel);
        Set<TriggerRouter> triggerRouterSet = new HashSet<TriggerRouter>();
        Trigger triggerForSet = new Trigger("sym_node_security", "default");
        Router routerForSet = new Router("routerForSet", link);
        TriggerRouter triggerRouterForSet = new TriggerRouter(triggerForSet, routerForSet);
        TriggerHistory triggerHist = new TriggerHistory("sym_node_security", "NODE_ID",
                "NODE_ID,NODE_PASSWORD,REGISTRATION_ENABLED,REGISTRATION_TIME,REGISTRATION_NOT_BEFORE,REGISTRATION_NOT_AFTER,INITIAL_LOAD_ENABLED,INITIAL_LOAD_TIME,INITIAL_LOAD_END_TIME,INITIAL_LOAD_ID,INITIAL_LOAD_CREATE_BY,REV_INITIAL_LOAD_ENABLED,REV_INITIAL_LOAD_TIME,REV_INITIAL_LOAD_ID,REV_INITIAL_LOAD_CREATE_BY,FAILED_LOGINS,CREATED_AT_NODE_ID");
        triggerRouterSet.add(triggerRouterForSet);
        IReloadGenerator reloadGenerator = mock(IReloadGenerator.class);
        IClusterService clusterService = mock(IClusterService.class);
        INodeService nodeService = mock(INodeService.class);
        TriggerRouterService triggerRouterService = mock(TriggerRouterService.class);
        IInitialLoadService initialLoadService = mock(IInitialLoadService.class);
        NodeSecurity nodeSecurity = mock(NodeSecurity.class);
        ISequenceService sequenceService = mock(ISequenceService.class);
        IDataExtractorService dataExtractorService = mock(IDataExtractorService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IGroupletService groupletService = mock(IGroupletService.class);
        ITransformService transformService = mock(ITransformService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IPurgeService purgeService = mock(IPurgeService.class);
        IFileSyncService fileSyncService = mock(IFileSyncService.class);
        // mocked interactions
        when(engine.getClusterService()).thenReturn(clusterService);
        when(clusterService.lock(ClusterConstants.SYNC_TRIGGERS)).thenReturn(true);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentity()).thenReturn(sourceNode);
        when(nodeService.findNodeSecurity(ArgumentMatchers.anyString())).thenReturn(nodeSecurity);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(parameterService.is(ParameterConstants.DATA_RELOAD_IS_BATCH_INSERT_TRANSACTIONAL)).thenReturn(true);
        when(engine.getNodeId()).thenReturn("server");
        when(engine.getInitialLoadService()).thenReturn(initialLoadService);
        doNothing().when(initialLoadService).cancelLoad(ArgumentMatchers.any());
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTransaction.prepareAndExecute(ArgumentMatchers.anyString(), (JdbcSqlTransaction) ArgumentMatchers.any())).thenReturn(1);
        when(reloadGenerator.getActiveTriggerHistories(targetNode)).thenReturn(triggerHistories);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(platform.supportsMultiThreadedTransactions()).thenReturn(false);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(sequenceService.nextVal(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(1L);
        when(nodeSecurity.getInitialLoadCreateBy()).thenReturn("test user");
        when(triggerRouterService.getActiveTriggerHistories((Trigger) ArgumentMatchers.any())).thenReturn(triggerHistories);
        when(triggerRouterService.fillTriggerRoutersByHistIdAndSortHist(ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyList(), ArgumentMatchers.anyList(),
                ArgumentMatchers.anyBoolean())).thenReturn(triggerRouterByHist);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getChannels(false)).thenReturn(channelMap);
        when(engine.getConfigurationService().getChannels(false)).thenReturn(channelMap);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(groupletService.isTargetEnabled(triggerRouter, targetNode)).thenReturn(true);
        when(engine.getDataExtractorService()).thenReturn(dataExtractorService);
        when(symmetricDialect.getTargetDialect()).thenReturn(symmetricDialect);
        when(platform.getTableFromCache(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean())).thenReturn(
                table);
        doNothing().when(dataExtractorService).releaseMissedExtractRequests();
        when(triggerRouterService.getRouterById(ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())).thenReturn(router);
        when(engine.getTransformService()).thenReturn(transformService);
        when(transformService.findTransformsFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        doNothing().when(outgoingBatchService).insertOutgoingBatch(ArgumentMatchers.any(), ArgumentMatchers.any());
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        doNothing().when(statisticManager).incrementNodesLoaded(1);
        when(sqlTransaction.prepareAndExecute(ArgumentMatchers.anyString(), ArgumentMatchers.any(Object[].class))).thenReturn(1);
        when(sqlTransaction.prepareAndExecute(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(1);
        when(sqlTemplate.queryForObject(ArgumentMatchers.anyString(), (ISqlRowMapper<TableReloadStatus>) ArgumentMatchers.any(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyString())).thenReturn(tableReloadStatus);
        when(dataExtractorService.requestExtractRequest(sqlTransaction, targetNode.getNodeId(), channel.getQueue(), triggerRouter, -1, -1, 1, table.getName(),
                0, 0)).thenReturn(extractRequest);
        // Scenario 1 callers for Full Load testing
        if (scenario == 1) {
            doNothing().when(outgoingBatchService).markAllAsSentForNode(targetNode.getNodeId(), false);
            doReturn(triggerRouterSet).when(triggerRouterService).getTriggerRouterForTableForCurrentNode(ArgumentMatchers.any(), ArgumentMatchers.any(),
                    ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean());
            doReturn(triggerHist).when(triggerRouterService).getNewestTriggerHistoryForTrigger(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers
                    .any(), ArgumentMatchers.any());
            when(engine.getTriggerRouterService()
                    .findTriggerHistoryForGenericSync()).thenReturn(triggerHistory);
            when(engine.getTriggerRouterService().getTriggerById("testTable", false)).thenReturn(trigger);
            when(engine.getPurgeService()).thenReturn(purgeService);
            doNothing().when(purgeService).purgeAllIncomingEventsForNode(ArgumentMatchers.anyString());
        }
        // Scenario 2 callers for Filesync testing
        if (scenario == 2) {
            List<FileTriggerRouter> fileTriggerRouters = new ArrayList<FileTriggerRouter>();
            FileTrigger fileTrigger = new FileTrigger("basedir/test", true, "*", null);
            FileTriggerRouter fileTriggerRouter = new FileTriggerRouter(fileTrigger, router);
            Trigger fileSyncTrigger = new Trigger("sym_file_snapshot", "default");
            TriggerHistory fileSyncTriggerHist = new TriggerHistory("sym_file_snapshot", "trigger_id,router_id,relative_dir,file_name",
                    "trigger_id,router_id,relative_dir,file_name,channel_id,reload_channel_id,last_event_type,crc32_checksum,file_size,file_modified_time,last_update_time,last_update_by,create_time");
            fileSyncTriggerHist.setTriggerId("sym_file_snapshot");
            String routerName = String.format("%s_%s_2_%s", fileSyncTriggerHist.getTriggerId(), "server", targetNode.getNodeGroupId());
            TriggerRouter fileSyncTriggerRouter = new TriggerRouter(fileSyncTrigger, router);
            fileTriggerRouters.add(fileTriggerRouter);
            when(parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)).thenReturn(true);
            when(engine.getFileSyncService()).thenReturn(fileSyncService);
            when(fileSyncService.getFileTriggerRoutersForCurrentNode(false)).thenReturn(fileTriggerRouters);
            when(triggerRouterService.findTriggerHistory(null, null, "sym_file_snapshot")).thenReturn(fileSyncTriggerHist);
            when(parameterService.getNodeGroupId()).thenReturn("server");
            when(triggerRouterService.buildSymmetricTableRouterId(
                    fileSyncTriggerHist.getTriggerId(), "server",
                    targetNode.getNodeGroupId())).thenReturn(routerName);
            when(triggerRouterService.getTriggerRouterForCurrentNode(fileSyncTriggerHist.getTriggerId(),
                    routerName, true)).thenReturn(fileSyncTriggerRouter);
            when(engine.getConfigurationService()).thenReturn(configurationService);
            when(configurationService.getFileSyncChannels()).thenReturn(channels);
        }
        // Actual Tests and Results
        Map<Integer, ExtractRequest> actualResults = new HashMap<Integer, ExtractRequest>();
        Map<Integer, ExtractRequest> expectedResults = new HashMap<Integer, ExtractRequest>();
        if (scenario == 1) {
            actualResults = dataService.insertReloadEvents(targetNode, false, reloadRequestsForAll, processInfo, triggerRouters, extractRequests,
                    reloadGenerator);
        } else {
            actualResults = dataService.insertReloadEvents(targetNode, false, reloadRequests, processInfo, triggerRouters, extractRequests, reloadGenerator);
        }
        expectedResults.put(0, extractRequest);
        assertEquals(actualResults, expectedResults);
    }

    @SuppressWarnings("unchecked")
    private ReloadTestFixture setupFullReloadTest(boolean deferConstraints) {
        ReloadTestFixture fixture = new ReloadTestFixture();
        Node targetNode = new Node();
        targetNode.setNodeGroupId("client");
        targetNode.setExternalId("client");
        targetNode.setNodeId("client");
        fixture.targetNode = targetNode;
        Node sourceNode = new Node();
        sourceNode.setExternalId("server");
        sourceNode.setNodeGroupId("server");
        sourceNode.setNodeId("server");
        NodeGroupLink link = new NodeGroupLink("server", "client");
        Trigger trigger = new Trigger("testTable", "default");
        Router router = new Router("testRouter", link);
        TriggerRouter triggerRouter = new TriggerRouter(trigger, router);
        TableReloadRequest reloadRequestForAll = new TableReloadRequest();
        reloadRequestForAll.setLoadId(1);
        reloadRequestForAll.setTriggerId("ALL");
        reloadRequestForAll.setRouterId("ALL");
        reloadRequestForAll.setCreateTable(true);
        Table table = new Table("testTable");
        Column idCol = new Column("Id");
        idCol.setPrimaryKey(true);
        table.addColumn(idCol);
        table.addColumn(new Column("age"));
        table.addColumn(new Column("weight"));
        TableReloadStatus tableReloadStatus = new TableReloadStatus();
        List<TableReloadRequest> reloadRequestsForAll = new ArrayList<TableReloadRequest>();
        reloadRequestsForAll.add(reloadRequestForAll);
        fixture.reloadRequests = reloadRequestsForAll;
        fixture.processInfo = new ProcessInfo();
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        triggerRouters.add(triggerRouter);
        fixture.triggerRouters = triggerRouters;
        Map<Integer, ExtractRequest> extractRequests = new HashMap<Integer, ExtractRequest>();
        ExtractRequest extractRequest = new ExtractRequest();
        extractRequest.setEndBatchId(2L);
        extractRequest.setLoadId(0);
        extractRequest.setNodeId("server");
        extractRequest.setParentRequestId(0);
        extractRequest.setRequestId(1);
        extractRequest.setStartBatchId(0);
        extractRequest.setTriggerRouter(triggerRouter);
        extractRequests.put(0, extractRequest);
        fixture.extractRequests = extractRequests;
        Map<Integer, List<TriggerRouter>> triggerRouterByHist = new HashMap<Integer, List<TriggerRouter>>();
        triggerRouterByHist.put(0, triggerRouters);
        List<TriggerHistory> triggerHistories = new ArrayList<TriggerHistory>();
        TriggerHistory triggerHistory = new TriggerHistory("testTable", "Id", "Id,age,weight");
        triggerHistory.setTriggerId("testTable");
        triggerHistories.add(triggerHistory);
        Map<String, Channel> channelMap = new HashMap<String, Channel>();
        Channel channel = new Channel("default", 0);
        channelMap.put("default", channel);
        Channel reloadChannel = new Channel("reload", 0);
        reloadChannel.setReloadFlag(true);
        channelMap.put("reload", reloadChannel);
        Set<TriggerRouter> triggerRouterSet = new HashSet<TriggerRouter>();
        Trigger triggerForSet = new Trigger("sym_node_security", "default");
        Router routerForSet = new Router("routerForSet", link);
        TriggerRouter triggerRouterForSet = new TriggerRouter(triggerForSet, routerForSet);
        TriggerHistory triggerHist = new TriggerHistory("sym_node_security", "NODE_ID",
                "NODE_ID,NODE_PASSWORD,REGISTRATION_ENABLED,REGISTRATION_TIME,REGISTRATION_NOT_BEFORE,REGISTRATION_NOT_AFTER,INITIAL_LOAD_ENABLED,INITIAL_LOAD_TIME,INITIAL_LOAD_END_TIME,INITIAL_LOAD_ID,INITIAL_LOAD_CREATE_BY,REV_INITIAL_LOAD_ENABLED,REV_INITIAL_LOAD_TIME,REV_INITIAL_LOAD_ID,REV_INITIAL_LOAD_CREATE_BY,FAILED_LOGINS,CREATED_AT_NODE_ID");
        triggerHist.setTriggerId("sym_node_security");
        triggerRouterSet.add(triggerRouterForSet);
        IReloadGenerator reloadGenerator = mock(IReloadGenerator.class);
        fixture.reloadGenerator = reloadGenerator;
        IClusterService clusterService = mock(IClusterService.class);
        INodeService nodeService = mock(INodeService.class);
        TriggerRouterService triggerRouterService = mock(TriggerRouterService.class);
        fixture.triggerRouterService = triggerRouterService;
        IInitialLoadService initialLoadService = mock(IInitialLoadService.class);
        NodeSecurity nodeSecurity = mock(NodeSecurity.class);
        ISequenceService sequenceService = mock(ISequenceService.class);
        IDataExtractorService dataExtractorService = mock(IDataExtractorService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IGroupletService groupletService = mock(IGroupletService.class);
        ITransformService transformService = mock(ITransformService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        fixture.outgoingBatchService = outgoingBatchService;
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IPurgeService purgeService = mock(IPurgeService.class);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(clusterService.lock(ClusterConstants.SYNC_TRIGGERS)).thenReturn(true);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentity()).thenReturn(sourceNode);
        when(nodeService.findNodeSecurity(ArgumentMatchers.anyString())).thenReturn(nodeSecurity);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(parameterService.is(ParameterConstants.DATA_RELOAD_IS_BATCH_INSERT_TRANSACTIONAL)).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(deferConstraints);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_USE_RELOAD_CHANNEL)).thenReturn(true);
        when(engine.getNodeId()).thenReturn("server");
        when(engine.getInitialLoadService()).thenReturn(initialLoadService);
        doNothing().when(initialLoadService).cancelLoad(ArgumentMatchers.any());
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTransaction.prepareAndExecute(ArgumentMatchers.anyString(), (JdbcSqlTransaction) ArgumentMatchers.any())).thenReturn(1);
        when(reloadGenerator.getActiveTriggerHistories(targetNode)).thenReturn(triggerHistories);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.supportsMultiThreadedTransactions()).thenReturn(false);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(sequenceService.nextVal(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(1L);
        when(nodeSecurity.getInitialLoadCreateBy()).thenReturn("test user");
        when(triggerRouterService.getActiveTriggerHistories((Trigger) ArgumentMatchers.any())).thenReturn(triggerHistories);
        when(triggerRouterService.fillTriggerRoutersByHistIdAndSortHist(ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyList(), ArgumentMatchers.anyList(),
                ArgumentMatchers.anyBoolean())).thenReturn(triggerRouterByHist);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getChannels(false)).thenReturn(channelMap);
        when(engine.getConfigurationService().getChannels(false)).thenReturn(channelMap);
        when(configurationService.getChannel("reload")).thenReturn(reloadChannel);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(groupletService.isTargetEnabled(triggerRouter, targetNode)).thenReturn(true);
        when(engine.getDataExtractorService()).thenReturn(dataExtractorService);
        when(symmetricDialect.getTargetDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getTablePrefix()).thenReturn("sym");
        when(platform.getTableFromCache(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean())).thenReturn(
                table);
        doNothing().when(dataExtractorService).releaseMissedExtractRequests();
        when(triggerRouterService.getRouterById(ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())).thenReturn(router);
        when(engine.getTransformService()).thenReturn(transformService);
        when(transformService.findTransformsFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        doNothing().when(outgoingBatchService).insertOutgoingBatch(ArgumentMatchers.any(), ArgumentMatchers.any());
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        doNothing().when(statisticManager).incrementNodesLoaded(1);
        when(sqlTransaction.prepareAndExecute(ArgumentMatchers.anyString(), ArgumentMatchers.any(Object[].class))).thenReturn(1);
        when(sqlTransaction.prepareAndExecute(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(1);
        when(sqlTemplate.queryForObject(ArgumentMatchers.anyString(), (ISqlRowMapper<TableReloadStatus>) ArgumentMatchers.any(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyString())).thenReturn(tableReloadStatus);
        when(dataExtractorService.requestExtractRequest(sqlTransaction, targetNode.getNodeId(), channel.getQueue(), triggerRouter, -1, -1, 1, table.getName(),
                0, 0)).thenReturn(extractRequest);
        doNothing().when(outgoingBatchService).markAllAsSentForNode(targetNode.getNodeId(), false);
        doReturn(triggerRouterSet).when(triggerRouterService).getTriggerRouterForTableForCurrentNode(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean());
        doReturn(triggerHist).when(triggerRouterService).getNewestTriggerHistoryForTrigger(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers
                .any(), ArgumentMatchers.any());
        when(engine.getTriggerRouterService()
                .findTriggerHistoryForGenericSync()).thenReturn(triggerHistory);
        when(engine.getTriggerRouterService().getTriggerById("testTable", false)).thenReturn(trigger);
        when(engine.getPurgeService()).thenReturn(purgeService);
        doNothing().when(purgeService).purgeAllIncomingEventsForNode(ArgumentMatchers.anyString());
        return fixture;
    }

    @Test
    void testInsertReloadEventsWithDeferConstraints() {
        ReloadTestFixture f = setupFullReloadTest(true);
        Map<Integer, ExtractRequest> actualResults = dataService.insertReloadEvents(f.targetNode, false, f.reloadRequests,
                f.processInfo, f.triggerRouters, f.extractRequests, f.reloadGenerator);
        Map<Integer, ExtractRequest> expectedResults = new HashMap<Integer, ExtractRequest>();
        expectedResults.put(0, f.extractRequests.get(0));
        assertEquals(actualResults, expectedResults);
        // Verify sortByFk=false when deferConstraints is true and createTable is true
        verify(f.triggerRouterService).fillTriggerRoutersByHistIdAndSortHist(
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyList(), ArgumentMatchers.anyList(), ArgumentMatchers.eq(false));
        // Verify deferred index batches were created on the reload channel
        ArgumentCaptor<OutgoingBatch> batchCaptor = ArgumentCaptor.forClass(OutgoingBatch.class);
        verify(f.outgoingBatchService, Mockito.atLeastOnce()).insertOutgoingBatch(ArgumentMatchers.any(ISqlTransaction.class), batchCaptor.capture());
        List<OutgoingBatch> capturedBatches = batchCaptor.getAllValues();
        long reloadChannelBatchCount = capturedBatches.stream()
                .filter(b -> "reload".equals(b.getChannelId()))
                .count();
        assertEquals(6, reloadChannelBatchCount,
                "Expected 6 batches on reload channel (base batches + DROP FK + deferred index)");
    }

    @Test
    void testDeferredIndexBatchesNotCreatedWhenDisabled() {
        ReloadTestFixture f = setupFullReloadTest(false);
        dataService.insertReloadEvents(f.targetNode, false, f.reloadRequests, f.processInfo,
                f.triggerRouters, f.extractRequests, f.reloadGenerator);
        ArgumentCaptor<OutgoingBatch> batchCaptor = ArgumentCaptor.forClass(OutgoingBatch.class);
        verify(f.outgoingBatchService, Mockito.atLeastOnce()).insertOutgoingBatch(ArgumentMatchers.any(ISqlTransaction.class), batchCaptor.capture());
        List<OutgoingBatch> capturedBatches = batchCaptor.getAllValues();
        long reloadChannelBatchCount = capturedBatches.stream()
                .filter(b -> "reload".equals(b.getChannelId()))
                .count();
        assertEquals(4, reloadChannelBatchCount,
                "Expected 4 batches on reload channel (no DROP FK or deferred index)");
    }

    @ParameterizedTest
    @CsvSource({ "" + 0 + "", "" + 1 + "", "" + 2 + "" })
    void testSendSQL(int scenario) throws Exception {
        // actual variables
        Node targetNode = new Node();
        targetNode.setNodeGroupId("client");
        targetNode.setExternalId("client");
        targetNode.setNodeId("client");
        Node sourceNode = new Node();
        sourceNode.setExternalId("server");
        sourceNode.setNodeGroupId("server");
        sourceNode.setNodeId("server");
        TriggerHistory triggerHistory = new TriggerHistory("testTable", "Id", "Id,age,weight");
        triggerHistory.setTriggerId("testTable");
        TriggerHistory triggerHist = new TriggerHistory("sym_node_host", "node_id,host_name",
                "node_id,host_name,instance_id,ip_address,os_user,os_name,os_arch,os_version,available_processors,free_memory_bytes,total_memory_bytes,max_memory_bytes,java_version,java_vendor,jdbc_version,symmetric_version,timezone_offset,heartbeat_time,last_restart_time,create_time");
        triggerHist.setTriggerId("sym_node_host");
        Trigger triggerForNodeHost = new Trigger("sym_node_host", "default");
        // mocked variables
        INodeService nodeService = mock(INodeService.class);
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        // mocked interactions
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentity()).thenReturn(sourceNode);
        when(nodeService.findNode(ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())).thenReturn(targetNode);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        if (scenario == 2) {
            when(triggerRouterService.findTriggerHistory(null, null, "sym_node_host")).thenReturn(null);
        } else {
            when(triggerRouterService.findTriggerHistory(null, null, "sym_node_host")).thenReturn(triggerHist);
        }
        when(triggerRouterService.getTriggerById(triggerHist.getTriggerId())).thenReturn(triggerForNodeHost);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        doNothing().when(outgoingBatchService).insertOutgoingBatch(ArgumentMatchers.any(), ArgumentMatchers.any());
        // Actual Tests and Results
        if (scenario == 0) {
            dataService.sendSQL("client", null);
        } else if (scenario == 1) {
            dataService.sendSQL("failure", null);
        } else if (scenario == 2) {
            dataService.sendSQL("client", null);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetTableReloadRequest() throws Exception {
        // actual variables
        TableReloadRequest reloadRequest = new TableReloadRequest();
        reloadRequest.setLoadId(1);
        reloadRequest.setTriggerId("testTable");
        reloadRequest.setRouterId("testRouter");
        List<TableReloadRequest> reloadRequests = new ArrayList<TableReloadRequest>();
        reloadRequests.add(reloadRequest);
        // mocked interactions
        when(sqlTemplate.query(ArgumentMatchers.any(), (ISqlRowMapper<TableReloadRequest>) ArgumentMatchers.any(), ArgumentMatchers.anyLong())).thenReturn(
                reloadRequests);
        dataService.getTableReloadRequest(0);
    }

    @Test
    public void recaptureStranded_Insert_ExistingRow() throws Exception {
        String strandedRowData = "I1, bobo, abc", pkData = "I1, bobo", actualRowData = "I1, bobo, abc";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.INSERT, strandedRowData, pkData, null,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(1)).queryForObject("queryPk", String.class);
        assertEquals(1, recapturedCount);
    }

    @Test
    public void recaptureStranded_Insert_MissingRow() throws Exception {
        String strandedRowData = "I2, bobo, abc", pkData = "I2, bobo", actualRowData = null;
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.INSERT, strandedRowData, pkData, null,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Insert_ExtraColumn_ExistingRow() throws Exception {
        String strandedRowData = "I3, bobo, abc, EXTRA", pkData = "I3, bobo", actualRowData = "I3, bobo3, abc";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.INSERT, strandedRowData, pkData, null,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(1)).queryForObject("queryPk", String.class);
        assertEquals(1, recapturedCount);
    }

    @Test
    public void recaptureStranded_Insert_ExtraColumn_MissingRow() throws Exception {
        String strandedRowData = "I4, bobo, abc, EXTRA", pkData = "I4, bobo", actualRowData = null;
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.INSERT, strandedRowData, pkData, null,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Insert_MissingPkColumn_MissingRow() throws Exception {
        String strandedRowData = "I5", pkData = "I5", actualRowData = null;
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.INSERT, strandedRowData, pkData, null,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Insert_MissingAllColumns_ExistingRow() throws Exception {
        String strandedRowData = "", pkData = "MISsing", actualRowData = null;
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.INSERT, strandedRowData, pkData, null,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(0)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Insert_MissingColumn_MissingRow() throws Exception {
        String strandedRowData = "I7, MISsing", pkData = "I7, MISsing", actualRowData = null;
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.INSERT, strandedRowData, pkData, null,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Update_ExistingRow() throws Exception {
        String strandedRowData = "U1, bobo, abc", pkData = "U1, bobo", actualRowData = "U1, bobo, abc", oldData = "U1, bobo, abc-before";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.UPDATE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(1, recapturedCount);
    }

    @Test
    public void recaptureStranded_Update_MissingRow() throws Exception {
        String strandedRowData = "U2, bobo, abc", pkData = "U2, bobo", actualRowData = null, oldData = "U2, bobo, abc-before";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.UPDATE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Update_ExtraColumn_ExistingRow() throws Exception {
        String strandedRowData = "U3, bobo, abc, EXTRA", pkData = "U3, bobo", actualRowData = "U3, bobo3, abc", oldData = "U3, bobo, abc-before";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.UPDATE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(1, recapturedCount);
    }

    @Test
    public void recaptureStranded_Update_ExtraColumn_MissingRow() throws Exception {
        String strandedRowData = "U4, bobo, abc, EXTRA", pkData = "U4, bobo", actualRowData = null, oldData = "U4, bobo, abc-before, EXTRA";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.UPDATE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Update_MissingPkColumn_MissingRow() throws Exception {
        String strandedRowData = "U5", pkData = "U5", actualRowData = null, oldData = "U5";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.UPDATE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Update_MissingAllColumns_ExistingRow() throws Exception {
        String strandedRowData = null, pkData = "MISSING", actualRowData = null, oldData = null;
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.UPDATE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(0)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Update_MissingColumn_MissingRow() throws Exception {
        String strandedRowData = "U7, MISSING3rd", pkData = "U7, MISSING3rd", actualRowData = null, oldData = "U7, MISSING3rd-before";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.UPDATE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Delete_ExistingRow() throws Exception {
        String strandedRowData = null, pkData = "D1, bobo", actualRowData = "D1, bobo, abc", oldData = "D1, bobo, abc-before-delete";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.DELETE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Delete_MissingRow() throws Exception {
        String strandedRowData = null, pkData = "D2, bobo", actualRowData = null, oldData = "D2, bobo, abc-before-delete";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.DELETE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(1, recapturedCount);
    }

    @Test
    public void recaptureStranded_Delete_ExtraColumn_ExistingRow() throws Exception {
        String strandedRowData = null, pkData = "D3, bobo, EXTRA", actualRowData = "D3, bobo3, abc", oldData = "D3, bobo, abc-before-delete";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.DELETE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Delete_ExtraColumn_MissingRow() throws Exception {
        String strandedRowData = null, pkData = "D4, bobo, EXTRA", actualRowData = null, oldData = "D4, bobo, abc-before-delete, EXTRA";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.DELETE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Delete_MissingPkColumn_MissingRow() throws Exception {
        String strandedRowData = null, pkData = "MISSING", actualRowData = null, oldData = "D5, bobo, abc-before-delete, EXTRA";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.DELETE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Delete_MissingAllColumns_ExistingRow() throws Exception {
        String strandedRowData = null, pkData = "", actualRowData = null, oldData = "D6, bobo, abc-before-delete, EXTRA";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.DELETE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(0)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    @Test
    public void recaptureStranded_Delete_MissingColumn_MissingRow() throws Exception {
        String strandedRowData = null, pkData = "D7, MISSING3rd", actualRowData = null, oldData = "D7, MISSING3rd-before-delete";
        setupRecapture(strandedTableName, strandedChannelId, strandedColumnNames, strandedPkNames, DataEventType.DELETE, strandedRowData, pkData, oldData,
                actualRowData);
        int recapturedCount = dataService.reCaptureData(1, 1);
        verify(sqlTransaction, times(1)).queryForObject("queryData", String.class);
        verify(sqlTransaction, times(0)).queryForObject("queryPk", String.class);
        assertEquals(0, recapturedCount);
    }

    private Row buildMapperRow(String tableName, int triggerHistId) {
        Row row = new Row(14);
        row.put("ROW_DATA", null);
        row.put("PK_DATA", null);
        row.put("OLD_DATA", null);
        row.put("CHANNEL_ID", "default");
        row.put("TRANSACTION_ID", null);
        row.put("TABLE_NAME", tableName);
        row.put("EVENT_TYPE", "D");
        row.put("SOURCE_NODE_ID", null);
        row.put("EXTERNAL_DATA", null);
        row.put("NODE_LIST", null);
        row.put("DATA_ID", 1L);
        row.put("CREATE_TIME", null);
        row.put("TRIGGER_HIST_ID", triggerHistId);
        row.put("IS_PREROUTED", false);
        return row;
    }

    private ITriggerRouterService setUpTriggerRouterServiceForMapperFallback() {
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        INodeService nodeService = mock(INodeService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(engine.getNodeService()).thenReturn(nodeService);
        Node identity = new Node();
        identity.setNodeGroupId("server");
        when(nodeService.findIdentity()).thenReturn(identity);
        when(triggerRouterService.getTriggerHistory(MAPPER_HIST_ID)).thenReturn(null);
        return triggerRouterService;
    }

    private Trigger buildMapperTrigger(String catalogName, String schemaName) {
        Trigger trigger = new Trigger();
        trigger.setTriggerId(MAPPER_TRIGGER_ID);
        trigger.setSourceTableName(MAPPER_TABLE);
        trigger.setSourceCatalogName(catalogName);
        trigger.setSourceSchemaName(schemaName);
        return trigger;
    }

    private TriggerHistory buildMapperTriggerHistory(String catalogName, String schemaName) {
        TriggerHistory history = new TriggerHistory();
        history.setTriggerHistoryId(MAPPER_HIST_ID);
        history.setTriggerId(MAPPER_TRIGGER_ID);
        history.setSourceTableName(MAPPER_TABLE);
        history.setSourceCatalogName(catalogName);
        history.setSourceSchemaName(schemaName);
        return history;
    }

    private void setUpHistoryReturnValues(ITriggerRouterService triggerRouterService, TriggerHistory history) {
        when(triggerRouterService.getActiveTriggerHistories()).thenReturn(Collections.singletonList(history));
        Map<Long, TriggerHistory> histMap = new HashMap<Long, TriggerHistory>();
        histMap.put((long) MAPPER_HIST_ID, history);
        when(triggerRouterService.getHistoryRecords()).thenReturn(histMap);
    }

    @Test
    void testFindOrCreateTriggerHistory_triggerHistFoundInCache_noFallback() {
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        TriggerHistory cachedHistory = buildMapperTriggerHistory("catalog_1", "dbo");
        when(triggerRouterService.getTriggerHistory(MAPPER_HIST_ID)).thenReturn(cachedHistory);
        DataService.DataMapper mapper = ((DataService) dataService).new DataMapper();
        Data data = mapper.mapRow(buildMapperRow(MAPPER_TABLE, MAPPER_HIST_ID));
        assertSame(cachedHistory, data.getTriggerHistory());
        verify(triggerRouterService, never()).getAllTriggerRoutersForCurrentNode(ArgumentMatchers.any());
    }

    @Test
    void testFindOrCreateTriggerHistory_nonWildcardCatalogAndSchema() {
        String catalog = "catalog_1";
        String schema = "dbo";
        ITriggerRouterService triggerRouterService = setUpTriggerRouterServiceForMapperFallback();
        Trigger trigger = buildMapperTrigger(catalog, schema);
        TriggerRouter tr = mock(TriggerRouter.class);
        when(tr.isEnabled()).thenReturn(true);
        when(tr.getTrigger()).thenReturn(trigger);
        when(triggerRouterService.getAllTriggerRoutersForCurrentNode("server")).thenReturn(Collections.singletonList(tr));
        TriggerHistory history = buildMapperTriggerHistory(catalog, schema);
        setUpHistoryReturnValues(triggerRouterService, history);
        when(platform.getTableFromCache(catalog, schema, MAPPER_TABLE, false)).thenReturn(mock(Table.class));
        DataService.DataMapper mapper = ((DataService) dataService).new DataMapper();
        Data data = mapper.mapRow(buildMapperRow(MAPPER_TABLE, MAPPER_HIST_ID));
        assertSame(history, data.getTriggerHistory());
        verify(platform).getTableFromCache(catalog, schema, MAPPER_TABLE, false);
    }

    @Test
    void testFindOrCreateTriggerHistory_wildcardCatalog_resolvesFromActiveTriggerHistories() {
        String wildcardCatalog = "catalog_*";
        String resolvedCatalog = "catalog_1";
        String schema = "dbo";
        ITriggerRouterService triggerRouterService = setUpTriggerRouterServiceForMapperFallback();
        Trigger trigger = buildMapperTrigger(wildcardCatalog, schema);
        TriggerRouter tr = mock(TriggerRouter.class);
        when(tr.isEnabled()).thenReturn(true);
        when(tr.getTrigger()).thenReturn(trigger);
        when(triggerRouterService.getAllTriggerRoutersForCurrentNode("server")).thenReturn(Collections.singletonList(tr));
        TriggerHistory history = buildMapperTriggerHistory(resolvedCatalog, schema);
        setUpHistoryReturnValues(triggerRouterService, history);
        when(platform.getTableFromCache(resolvedCatalog, schema, MAPPER_TABLE, false)).thenReturn(mock(Table.class));
        DataService.DataMapper mapper = ((DataService) dataService).new DataMapper();
        Data data = mapper.mapRow(buildMapperRow(MAPPER_TABLE, MAPPER_HIST_ID));
        assertSame(history, data.getTriggerHistory());
        verify(platform).getTableFromCache(resolvedCatalog, schema, MAPPER_TABLE, false);
        verify(platform, never()).getTableFromCache(ArgumentMatchers.eq(wildcardCatalog), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }

    @Test
    void testFindOrCreateTriggerHistory_wildcardSchema_resolvesFromActiveTriggerHistories() {
        String catalog = "catalog_1";
        String wildcardSchema = "schema_*";
        String resolvedSchema = "schema_1";
        ITriggerRouterService triggerRouterService = setUpTriggerRouterServiceForMapperFallback();
        Trigger trigger = buildMapperTrigger(catalog, wildcardSchema);
        TriggerRouter tr = mock(TriggerRouter.class);
        when(tr.isEnabled()).thenReturn(true);
        when(tr.getTrigger()).thenReturn(trigger);
        when(triggerRouterService.getAllTriggerRoutersForCurrentNode("server")).thenReturn(Collections.singletonList(tr));
        TriggerHistory history = buildMapperTriggerHistory(catalog, resolvedSchema);
        setUpHistoryReturnValues(triggerRouterService, history);
        when(platform.getTableFromCache(catalog, resolvedSchema, MAPPER_TABLE, false)).thenReturn(mock(Table.class));
        DataService.DataMapper mapper = ((DataService) dataService).new DataMapper();
        Data data = mapper.mapRow(buildMapperRow(MAPPER_TABLE, MAPPER_HIST_ID));
        assertSame(history, data.getTriggerHistory());
        verify(platform).getTableFromCache(catalog, resolvedSchema, MAPPER_TABLE, false);
        verify(platform, never()).getTableFromCache(ArgumentMatchers.any(), ArgumentMatchers.eq(wildcardSchema),
                ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }

    @Test
    void testFindOrCreateTriggerHistory_wildcardCatalogAndSchema_resolvesFromActiveTriggerHistories() {
        String wildcardCatalog = "catalog_*";
        String wildcardSchema = "schema_*";
        String resolvedCatalog = "catalog_1";
        String resolvedSchema = "schema_1";
        ITriggerRouterService triggerRouterService = setUpTriggerRouterServiceForMapperFallback();
        Trigger trigger = buildMapperTrigger(wildcardCatalog, wildcardSchema);
        TriggerRouter tr = mock(TriggerRouter.class);
        when(tr.isEnabled()).thenReturn(true);
        when(tr.getTrigger()).thenReturn(trigger);
        when(triggerRouterService.getAllTriggerRoutersForCurrentNode("server")).thenReturn(Collections.singletonList(tr));
        TriggerHistory history = buildMapperTriggerHistory(resolvedCatalog, resolvedSchema);
        setUpHistoryReturnValues(triggerRouterService, history);
        when(platform.getTableFromCache(resolvedCatalog, resolvedSchema, MAPPER_TABLE, false)).thenReturn(mock(Table.class));
        DataService.DataMapper mapper = ((DataService) dataService).new DataMapper();
        Data data = mapper.mapRow(buildMapperRow(MAPPER_TABLE, MAPPER_HIST_ID));
        assertSame(history, data.getTriggerHistory());
        verify(platform).getTableFromCache(resolvedCatalog, resolvedSchema, MAPPER_TABLE, false);
    }

    @Test
    void testFindOrCreateTriggerHistory_wildcardCatalog_noResolvedHistory_onlyNullsCatalog() {
        // When catalog is wildcarded but schema is not, and no resolved history exists,
        // only catalogName should become null — schemaName keeps its concrete trigger value.
        String wildcardCatalog = "catalog_*";
        String schema = "dbo";
        ITriggerRouterService triggerRouterService = setUpTriggerRouterServiceForMapperFallback();
        Trigger trigger = buildMapperTrigger(wildcardCatalog, schema);
        TriggerRouter tr = mock(TriggerRouter.class);
        when(tr.isEnabled()).thenReturn(true);
        when(tr.getTrigger()).thenReturn(trigger);
        when(triggerRouterService.getAllTriggerRoutersForCurrentNode("server")).thenReturn(Collections.singletonList(tr));
        when(triggerRouterService.getActiveTriggerHistories()).thenReturn(Collections.emptyList());
        when(triggerRouterService.getHistoryRecords()).thenReturn(new HashMap<Long, TriggerHistory>());
        when(platform.getTableFromCache(null, schema, MAPPER_TABLE, false)).thenReturn(null);
        DataService.DataMapper mapper = ((DataService) dataService).new DataMapper();
        Data data = mapper.mapRow(buildMapperRow(MAPPER_TABLE, MAPPER_HIST_ID));
        assertNotNull(data.getTriggerHistory());
        assertEquals(MAPPER_HIST_ID, data.getTriggerHistory().getTriggerHistoryId());
        verify(platform).getTableFromCache(null, schema, MAPPER_TABLE, false);
        verify(platform, never()).getTableFromCache(ArgumentMatchers.eq(wildcardCatalog), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }

    @Test
    void testFindOrCreateTriggerHistory_wildcardCatalogOnly_resolvesOnlyCatalogFromHistory() {
        // When only catalog is wildcarded, the resolved history supplies the catalog but the
        // non-wildcarded schema comes unchanged from the trigger definition.
        String wildcardCatalog = "catalog_*";
        String resolvedCatalog = "catalog_1";
        String schema = "dbo";
        ITriggerRouterService triggerRouterService = setUpTriggerRouterServiceForMapperFallback();
        Trigger trigger = buildMapperTrigger(wildcardCatalog, schema);
        TriggerRouter tr = mock(TriggerRouter.class);
        when(tr.isEnabled()).thenReturn(true);
        when(tr.getTrigger()).thenReturn(trigger);
        when(triggerRouterService.getAllTriggerRoutersForCurrentNode("server")).thenReturn(Collections.singletonList(tr));
        // History has a different schema value — it should be ignored since schema is not wildcarded.
        TriggerHistory history = buildMapperTriggerHistory(resolvedCatalog, "other_schema");
        setUpHistoryReturnValues(triggerRouterService, history);
        when(platform.getTableFromCache(resolvedCatalog, schema, MAPPER_TABLE, false)).thenReturn(mock(Table.class));
        DataService.DataMapper mapper = ((DataService) dataService).new DataMapper();
        Data data = mapper.mapRow(buildMapperRow(MAPPER_TABLE, MAPPER_HIST_ID));
        assertSame(history, data.getTriggerHistory());
        // catalog resolved from history; schema kept from trigger (not from history)
        verify(platform).getTableFromCache(resolvedCatalog, schema, MAPPER_TABLE, false);
    }

    @Test
    void testFindOrCreateTriggerHistory_noMatchingTriggerRouter_returnsStubTriggerHistory() {
        ITriggerRouterService triggerRouterService = setUpTriggerRouterServiceForMapperFallback();
        when(triggerRouterService.getAllTriggerRoutersForCurrentNode("server")).thenReturn(Collections.emptyList());
        DataService.DataMapper mapper = ((DataService) dataService).new DataMapper();
        Data data = mapper.mapRow(buildMapperRow(MAPPER_TABLE, MAPPER_HIST_ID));
        assertNotNull(data.getTriggerHistory());
        assertEquals(MAPPER_HIST_ID, data.getTriggerHistory().getTriggerHistoryId());
        verify(platform, never()).getTableFromCache(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }

    @SuppressWarnings("unchecked")
    protected void setupRecapture(String tableName, String channelName, String columnNames, String pkNames, DataEventType dataEventType,
            String rowData, String pkData, String oldData, String existingRow) throws Exception {
        reset(sqlTransaction); // Clear previously mocked return values
        List<Data> datas = new ArrayList<Data>();
        String[] columnNamesArr = CsvUtils.tokenizeCsvData(columnNames);
        String[] pkNamesArr = CsvUtils.tokenizeCsvData(pkNames);
        TriggerHistory hist = new TriggerHistory(tableName, pkNames, columnNames);
        Data data = new Data(tableName, dataEventType, rowData, pkData, hist, channelName, null, null);
        data.setOldData(oldData);
        String[] parsedRowData = data.getParsedData(Data.ROW_DATA);
        String[] parsedPkData = data.getParsedData(Data.PK_DATA);
        String[] parsedOldData = data.getParsedData(Data.OLD_DATA);
        datas.add(data);
        when(sqlTemplate.query(ArgumentMatchers.any(), (ISqlRowMapper<Data>) ArgumentMatchers.any(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong()))
                .thenReturn(datas);
        String[] existingRowData = (existingRow != null) ? CsvUtils.tokenizeCsvData(existingRow) : (parsedRowData != null ? parsedRowData : parsedOldData);
        String existingRowDataAsString = (existingRow == null) ? null : CsvUtils.escapeCsvData(existingRowData);
        String existingRowPkDataAsString = (existingRow == null) ? null : pkData;
        when(sqlTransaction.queryForObject(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(existingRowDataAsString).thenReturn(
                existingRowPkDataAsString);
        Set<TriggerRouter> triggerRouters = new HashSet<TriggerRouter>();
        triggerRouters.add(new TriggerRouter(new Trigger(tableName, channelName), new Router()));
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(triggerRouterService.getTriggerRouterForTableForCurrentNode(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.anyBoolean())).thenReturn(triggerRouters);
        Table table = new Table(null, null, tableName, columnNamesArr, pkNamesArr);
        when(platform.getTableFromCache(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean())).thenReturn(
                table);
        when(platform.getObjectValues(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(existingRowData);
        DmlStatement st = mock(DmlStatement.class);
        when(platform.createDmlStatement(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(st);
        when(st.buildDynamicSql(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())).thenReturn(
                "where pk = ?;");
        when(symmetricDialect.createCsvPrimaryKeySql(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn("queryPk");
        when(symmetricDialect.createCsvDataSql(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn("queryData");
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        Row row = new Row(0);
        if (parsedPkData != null) {
            row = new Row(pkNamesArr, parsedPkData);
        }
        when(sqlTransaction.queryForRow(ArgumentMatchers.eq("queryPk"))).thenReturn(row);
        row = new Row(0);
        if (parsedRowData != null) {
            row = new Row(columnNamesArr, parsedRowData);
        }
        when(sqlTransaction.queryForRow(ArgumentMatchers.eq("queryData"))).thenReturn(row);
    }

    /**
     * Holds test fixtures and mocks for reload event tests.
     */
    private static class ReloadTestFixture {
        Node targetNode;
        List<TableReloadRequest> reloadRequests;
        ProcessInfo processInfo;
        List<TriggerRouter> triggerRouters;
        Map<Integer, ExtractRequest> extractRequests;
        IReloadGenerator reloadGenerator;
        IOutgoingBatchService outgoingBatchService;
        TriggerRouterService triggerRouterService;
    }
}
