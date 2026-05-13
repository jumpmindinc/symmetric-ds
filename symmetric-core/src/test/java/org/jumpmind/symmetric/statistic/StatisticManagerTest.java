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
package org.jumpmind.symmetric.statistic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.IStatisticService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;

class StatisticManagerTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private IStatisticService statisticService;
    private IClusterService clusterService;
    private IDataService dataService;
    private StatisticManagerUnderTest manager;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        nodeService = mock(INodeService.class);
        configurationService = mock(IConfigurationService.class);
        statisticService = mock(IStatisticService.class);
        clusterService = mock(IClusterService.class);
        dataService = mock(IDataService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticService()).thenReturn(statisticService);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getDataService()).thenReturn(dataService);
        when(configurationService.getNodeChannels(false)).thenReturn(Collections.emptyList());
        when(clusterService.getServerId()).thenReturn("server-1");
        when(engine.getMetricsService()).thenReturn(null);
        when(engine.getTablePrefix()).thenReturn("sym_");
        manager = new StatisticManagerUnderTest(engine);
    }

    private static ProcessInfoKey key(String src, String tgt, ProcessType type) {
        return new ProcessInfoKey(src, tgt, type);
    }

    private static Node node(String nodeId) {
        Node n = new Node();
        n.setNodeId(nodeId);
        return n;
    }

    @Test
    void newProcessInfo_firstCall_returnsNewProcessInNewStatus() {
        ProcessInfoKey k = key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfo p = manager.newProcessInfo(k);
        assertNotNull(p);
        assertEquals(ProcessStatus.NEW, p.getStatus());
    }

    @Test
    void newProcessInfo_secondCallSameKey_replacesExisting() {
        ProcessInfoKey k = key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfo first = manager.newProcessInfo(k);
        ProcessInfo second = manager.newProcessInfo(k);
        assertNotSame(first, second);
    }

    @Test
    void newProcessInfo_previousHadData_currentOkWithNoData_historyIsAccessible() {
        ProcessInfoKey k = key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfo first = manager.newProcessInfo(k);
        first.setCurrentDataCount(10);
        ProcessInfo second = manager.newProcessInfo(k); // stores first in processInfosThatHaveDoneWork
        second.setStatus(ProcessStatus.OK); // current is OK with 0 data
        List<ProcessInfo> result = manager.getProcessInfosThatHaveDoneWork();
        assertTrue(result.stream().anyMatch(p -> p.getCurrentDataCount() == 10));
    }

    @Test
    void getNodesWithProcessesInError_nullIdentityNodeId_returnsEmptySet() {
        when(nodeService.findIdentityNodeId()).thenReturn(null);
        Set<String> result = manager.getNodesWithProcessesInError();
        assertTrue(result.isEmpty());
    }

    @Test
    void getNodesWithProcessesInError_noErrorProcesses_returnsEmptySet() {
        when(nodeService.findIdentityNodeId()).thenReturn("node1");
        Set<String> result = manager.getNodesWithProcessesInError();
        assertTrue(result.isEmpty());
    }

    @Test
    void getProcessInfosThatHaveDoneWork_processNotOkWithData_returnsItself() {
        ProcessInfoKey k = key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfo p = manager.newProcessInfo(k);
        p.setCurrentDataCount(7);
        List<ProcessInfo> result = manager.getProcessInfosThatHaveDoneWork();
        assertTrue(result.stream().anyMatch(pi -> pi.getCurrentDataCount() == 7));
    }

    @Test
    void getMostRecentUserProcessInfos_noProcesses_returnsEmpty() {
        assertTrue(manager.getMostRecentUserProcessInfos(ProcessType.PUSH_JOB_EXTRACT).isEmpty());
    }

    @Test
    void getMostRecentUserProcessInfos_activeUserProcess_includesIt() {
        ProcessInfoKey k = key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfo p = manager.newProcessInfo(k);
        p.setCurrentTableName("user_orders");
        p.setCurrentChannelId(Constants.CHANNEL_DEFAULT);
        List<ProcessInfo> result = manager.getMostRecentUserProcessInfos(ProcessType.PUSH_JOB_EXTRACT);
        assertFalse(result.isEmpty());
    }

    @Test
    void getMostRecentUserProcessInfos_processOkWithNoData_fallsBackToLastWorkDone() {
        ProcessInfoKey k = key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfo first = manager.newProcessInfo(k);
        first.setCurrentDataCount(5);
        first.setCurrentTableName("user_orders");
        first.setCurrentChannelId(Constants.CHANNEL_DEFAULT);
        ProcessInfo second = manager.newProcessInfo(k); // first goes to userLastWorkDoneMap
        second.setCurrentTableName("user_orders");
        second.setCurrentChannelId(Constants.CHANNEL_DEFAULT);
        second.setStatus(ProcessStatus.OK);
        List<ProcessInfo> result = manager.getMostRecentUserProcessInfos(ProcessType.PUSH_JOB_EXTRACT);
        assertTrue(result.stream().anyMatch(p -> p.getCurrentDataCount() == 5));
    }

    @Test
    void isUserProcessInfo_userTableAndDefaultChannel_returnsTrue() {
        ProcessInfo p = manager.newProcessInfo(key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT));
        p.setCurrentTableName("user_orders");
        p.setCurrentChannelId(Constants.CHANNEL_DEFAULT);
        assertTrue(manager.isUserProcessInfo(p));
    }

    @Test
    void isUserProcessInfo_tableNameStartsWithPrefix_returnsFalse() {
        ProcessInfo p = manager.newProcessInfo(key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT));
        p.setCurrentTableName("sym_data");
        p.setCurrentChannelId(Constants.CHANNEL_DEFAULT);
        assertFalse(manager.isUserProcessInfo(p));
    }

    @Test
    void isUserProcessInfo_configChannel_returnsFalse() {
        ProcessInfo p = manager.newProcessInfo(key("src", "tgt", ProcessType.PUSH_JOB_EXTRACT));
        p.setCurrentTableName("user_orders");
        p.setCurrentChannelId(Constants.CHANNEL_CONFIG);
        assertFalse(manager.isUserProcessInfo(p));
    }

    @Test
    void isUserProcessInfo_systemQueue_returnsFalse() {
        ProcessInfoKey k = new ProcessInfoKey("src", Constants.QUEUE_SYSTEM, "tgt", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfo p = manager.newProcessInfo(k);
        p.setCurrentTableName("user_orders");
        p.setCurrentChannelId(Constants.CHANNEL_DEFAULT);
        assertFalse(manager.isUserProcessInfo(p));
    }

    @Test
    void getMostRecentUserDataSyncProcessInfo_notYetSet_returnsNull() {
        assertNull(manager.getMostRecentUserDataSyncProcessInfo("src", "tgt"));
    }

    @Test
    void addJobStats_basicOverload_addsToJobStats() {
        manager.addJobStats("Job1", 0L, 100L, 50L);
        assertFalse(manager.getWorkingJobStats().isEmpty());
    }

    @Test
    void addJobStats_withErrorMessage_addsToJobStats() {
        manager.addJobStats("Job2", 0L, 100L, 0L, "error message");
        assertFalse(manager.getWorkingJobStats().isEmpty());
    }

    @Test
    void addJobStats_withException_addsToJobStats() {
        manager.addJobStats("Job3", 0L, 100L, 0L, new RuntimeException("failure"));
        assertFalse(manager.getWorkingJobStats().isEmpty());
    }

    @Test
    void addJobStats_withTargetNodeId_addsToJobStats() {
        manager.addJobStats("node-tgt", 1, "Job4", 0L, 100L, 10L);
        assertFalse(manager.getWorkingJobStats().isEmpty());
    }

    @Test
    void addRouterStats_unroutedNodeBatch_isNotAddedToMap() {
        OutgoingBatch unrouted = new OutgoingBatch();
        unrouted.setNodeId(Constants.UNROUTED_NODE_ID);
        unrouted.setBatchId(99L);
        manager.addRouterStats(1L, 10L, 10, 0,
                Collections.emptyList(), Collections.emptySet(), List.of(unrouted));
        assertNull(manager.getRouterStatsByBatch(99L));
    }

    @Test
    void addRouterStats_normalBatch_isAddedToMap() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setNodeId("node1");
        batch.setBatchId(42L);
        manager.addRouterStats(1L, 10L, 10, 0,
                Collections.emptyList(), Collections.emptySet(), List.of(batch));
        assertNotNull(manager.getRouterStatsByBatch(42L));
    }

    @Test
    void getRouterStatsByBatch_unknownId_returnsNull() {
        assertNull(manager.getRouterStatsByBatch(9999L));
    }

    @Test
    void removeRouterStatsByBatch_removesEntry() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setNodeId("node1");
        batch.setBatchId(55L);
        manager.addRouterStats(1L, 5L, 5, 0,
                Collections.emptyList(), Collections.emptySet(), List.of(batch));
        manager.removeRouterStatsByBatch(55L);
        assertNull(manager.getRouterStatsByBatch(55L));
    }

    @Test
    void incrementDataRouted_nullMetricsService_doesNotThrow() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataRouted(Constants.CHANNEL_DEFAULT, 5L);
        assertEquals(5L, manager.getWorkingChannelStats().get(Constants.CHANNEL_DEFAULT).getDataRouted());
    }

    @Test
    void setDataUnRouted_nullMetricsService_doesNotThrow() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.setDataUnRouted(Constants.CHANNEL_DEFAULT, 10L);
        assertEquals(10L, manager.getWorkingChannelStats().get(Constants.CHANNEL_DEFAULT).getDataUnRouted());
    }

    @Test
    void incrementDataExtracted_nullMetricsService_doesNotThrow() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataExtracted(Constants.CHANNEL_DEFAULT, 3L);
        assertEquals(3L, manager.getWorkingChannelStats().get(Constants.CHANNEL_DEFAULT).getDataExtracted());
    }

    @Test
    void incrementDataBytesExtracted_nullMetricsService_doesNotThrow() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataBytesExtracted(Constants.CHANNEL_DEFAULT, 100L);
        assertEquals(100L, manager.getWorkingChannelStats().get(Constants.CHANNEL_DEFAULT).getDataBytesExtracted());
    }

    @Test
    void incrementDataLoaded_nullMetricsService_doesNotThrow() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataLoaded(Constants.CHANNEL_DEFAULT, 7L);
        assertEquals(7L, manager.getWorkingChannelStats().get(Constants.CHANNEL_DEFAULT).getDataLoaded());
    }

    @Test
    void incrementRestart_nullMetricsService_doesNotThrow() {
        manager.incrementRestart();
        assertEquals(1L, manager.getWorkingHostStats().getRestarted());
    }

    @Test
    void incrementDataLoadedOutgoing_nonSystemChannel_updatesLastDataSyncMaps() {
        manager.incrementDataLoadedOutgoing(Constants.CHANNEL_DEFAULT, 10L, "node1");
        assertNotNull(manager.getLastDataLoadedTimeMap().get("node1"));
        assertEquals(10L, manager.getLastDataLoadedRowsMap().get("node1"));
    }

    @Test
    void incrementDataLoadedOutgoing_systemChannel_doesNotUpdateLastDataSyncMaps() {
        manager.incrementDataLoadedOutgoing(Constants.CHANNEL_CONFIG, 5L, "node1");
        assertNull(manager.getLastDataLoadedTimeMap().get("node1"));
    }

    @Test
    void incrementDataBytesLoadedOutgoing_nonSystemChannel_updatesLastDataSyncBytesMap() {
        manager.incrementDataBytesLoadedOutgoing(Constants.CHANNEL_DEFAULT, 200L, "node1");
        assertEquals(200L, manager.getLastDataLoadedBytesMap().get("node1"));
    }

    @Test
    void incrementDataBytesLoadedOutgoing_systemChannel_doesNotUpdateLastDataSyncBytesMap() {
        manager.incrementDataBytesLoadedOutgoing(Constants.CHANNEL_CONFIG, 100L, "node1");
        assertNull(manager.getLastDataLoadedBytesMap().get("node1"));
    }

    @Test
    void setDataGapCount_preventsFlushFromCallingCountDataGaps() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        manager.setDataGapCount(5L);
        assertEquals(5L, manager.peekHostStats().getDataGapCount());
        manager.flush();
        verify(dataService, never()).countDataGaps();
    }

    @Test
    void setDataUnroutedCount_doesNotThrow() {
        manager.setDataUnroutedCount(3L);
        assertEquals(3L, manager.peekHostStats().getDataUnroutedCount());
    }

    @Test
    void getChannelStats_whenNodeIdentityIsNull_doesNotThrow() {
        when(nodeService.getCachedIdentity()).thenReturn(null);
        manager.incrementDataRouted(Constants.CHANNEL_DEFAULT, 1L);
        assertTrue(manager.getWorkingChannelStats().isEmpty());
    }

    @Test
    void getHostStats_whenNodeIdentityIsNull_doesNotThrow() {
        when(nodeService.getCachedIdentity()).thenReturn(null);
        assertDoesNotThrow(() -> manager.incrementRestart());
        assertNotNull(manager.getWorkingHostStats());
    }

    @Test
    void flush_withRecordStatisticsDisabled_doesNotSaveChannelStats() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(false);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataRouted(Constants.CHANNEL_DEFAULT, 5L);
        assertEquals(5L, manager.getWorkingChannelStats().get(Constants.CHANNEL_DEFAULT).getDataRouted());
        manager.flush();
        verify(statisticService, never()).save(any(ChannelStats.class));
    }

    @Test
    void flush_withRecordStatisticsEnabled_nonZeroStats_savesChannelStats() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataRouted(Constants.CHANNEL_DEFAULT, 5L);
        assertEquals(5L, manager.getWorkingChannelStats().get(Constants.CHANNEL_DEFAULT).getDataRouted());
        manager.flush();
        verify(statisticService, atLeastOnce()).save(any(ChannelStats.class));
    }

    @Test
    void flush_calledTwice_doesNotThrowOnSecondCall() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(false);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        manager.flush();
        assertDoesNotThrow(() -> manager.flush());
    }

    @Test
    void getWorkingChannelStats_afterIncrement_containsChannelEntry() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataRouted("chan1", 3L);
        Map<String, ChannelStats> snapshot = manager.getWorkingChannelStats();
        assertTrue(snapshot.containsKey("chan1"));
    }

    @Test
    void getWorkingJobStats_afterAddJobStats_returnsSnapshot() {
        manager.addJobStats("Job1", 0L, 100L, 5L);
        List<JobStats> snapshot = manager.getWorkingJobStats();
        assertFalse(snapshot.isEmpty());
        assertEquals("Job1", snapshot.get(0).getJobName());
    }

    @Test
    void getWorkingHostStats_afterIncrement_returnsNonNull() {
        manager.incrementRestart();
        assertNotNull(manager.getWorkingHostStats());
    }

    @Test
    void getLastDataLoadedTimeMap_initiallyEmpty() {
        assertTrue(manager.getLastDataLoadedTimeMap().isEmpty());
    }

    @Test
    void getLastDataLoadedRowsMap_initiallyEmpty() {
        assertTrue(manager.getLastDataLoadedRowsMap().isEmpty());
    }

    @Test
    void getLastDataLoadedBytesMap_initiallyEmpty() {
        assertTrue(manager.getLastDataLoadedBytesMap().isEmpty());
    }

    @Test
    void getProcessInfos_sortThrowsIllegalArgument_fallsBackToDeepCopiedList() {
        StatisticManagerUnderTest testManager = new StatisticManagerUnderTest(engine);
        ProcessInfoKey k1 = new ProcessInfoKey("src1", "tgt1", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfoKey k2 = new ProcessInfoKey("src2", "tgt2", ProcessType.PUSH_JOB_EXTRACT);
        testManager.injectProcessInfo(k1, new ThrowingProcessInfo(k1));
        testManager.injectProcessInfo(k2, new ThrowingProcessInfo(k2));
        List<ProcessInfo> result = testManager.getProcessInfos();
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void incrementDataSent_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataSent("chan1", 3L);
        assertEquals(3L, manager.getWorkingChannelStats().get("chan1").getDataSent());
    }

    @Test
    void incrementDataBytesSent_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataBytesSent("chan1", 100L);
        assertEquals(100L, manager.getWorkingChannelStats().get("chan1").getDataBytesSent());
    }

    @Test
    void incrementDataSentErrors_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataSentErrors("chan1", 2L);
        assertEquals(2L, manager.getWorkingChannelStats().get("chan1").getDataSentErrors());
    }

    @Test
    void incrementDataReceived_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataReceived("chan1", 4L);
        assertEquals(4L, manager.getWorkingChannelStats().get("chan1").getDataReceived());
    }

    @Test
    void incrementDataBytesReceived_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataBytesReceived("chan1", 200L);
        assertEquals(200L, manager.getWorkingChannelStats().get("chan1").getDataBytesReceived());
    }

    @Test
    void incrementDataBytesLoaded_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataBytesLoaded("chan1", 50L);
        assertEquals(50L, manager.getWorkingChannelStats().get("chan1").getDataBytesLoaded());
    }

    @Test
    void incrementDataLoadedErrors_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataLoadedErrors("chan1", 1L);
        assertEquals(1L, manager.getWorkingChannelStats().get("chan1").getDataLoadedErrors());
    }

    @Test
    void incrementDataLoadedOutgoingErrors_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataLoadedOutgoingErrors("chan1", 2L);
        assertEquals(2L, manager.getWorkingChannelStats().get("chan1").getDataLoadedOutgoingErrors());
    }

    @Test
    void updateDataMaxCreateTime_updatesChannelStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        Date maxTime = new Date();
        manager.updateDataMaxCreateTime("chan1", maxTime);
        assertEquals(maxTime, manager.getWorkingChannelStats().get("chan1").getDataMaxCreateTime());
    }

    @Test
    void incrementNodesPulled_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementNodesPulled(3L);
        assertEquals(3L, manager.getWorkingHostStats().getNodesPulled());
    }

    @Test
    void incrementTotalNodesPulledTime_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementTotalNodesPulledTime(100L);
        assertEquals(100L, manager.getWorkingHostStats().getTotalNodesPullTime());
    }

    @Test
    void incrementTotalNodesPushedTime_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementTotalNodesPushedTime(200L);
        assertEquals(200L, manager.getWorkingHostStats().getTotalNodesPushTime());
    }

    @Test
    void incrementNodesRejected_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementNodesRejected(1L);
        assertEquals(1L, manager.getWorkingHostStats().getNodesRejected());
    }

    @Test
    void incrementNodesRegistered_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementNodesRegistered(2L);
        assertEquals(2L, manager.getWorkingHostStats().getNodesRegistered());
    }

    @Test
    void incrementNodesLoaded_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementNodesLoaded(4L);
        assertEquals(4L, manager.getWorkingHostStats().getNodesLoaded());
    }

    @Test
    void incrementNodesDisabled_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementNodesDisabled(1L);
        assertEquals(1L, manager.getWorkingHostStats().getNodesDisabled());
    }

    @Test
    void incrementPurgedBatchIncomingRows_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementPurgedBatchIncomingRows(10L);
        assertEquals(10L, manager.getWorkingHostStats().getPurgedBatchIncomingRows());
    }

    @Test
    void incrementPurgedBatchOutgoingRows_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementPurgedBatchOutgoingRows(5L);
        assertEquals(5L, manager.getWorkingHostStats().getPurgedBatchOutgoingRows());
    }

    @Test
    void incrementPurgedDataRows_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementPurgedDataRows(20L);
        assertEquals(20L, manager.getWorkingHostStats().getPurgedDataRows());
    }

    @Test
    void incrementPurgedDataEventRows_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementPurgedDataEventRows(15L);
        assertEquals(15L, manager.getWorkingHostStats().getPurgedDataEventRows());
    }

    @Test
    void incrementPurgedStrandedDataRows_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementPurgedStrandedDataRows(7L);
        assertEquals(7L, manager.getWorkingHostStats().getPurgedStrandedDataRows());
    }

    @Test
    void incrementPurgedStrandedDataEventRows_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementPurgedStrandedDataEventRows(3L);
        assertEquals(3L, manager.getWorkingHostStats().getPurgedStrandedDataEventRows());
    }

    @Test
    void incrementPurgedExpiredDataRows_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementPurgedExpiredDataRows(8L);
        assertEquals(8L, manager.getWorkingHostStats().getPurgedExpiredDataRows());
    }

    @Test
    void incrementTriggersRemovedCount_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementTriggersRemovedCount(2L);
        assertEquals(2L, manager.getWorkingHostStats().getTriggersRemovedCount());
    }

    @Test
    void incrementTriggersRebuiltCount_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementTriggersRebuiltCount(4L);
        assertEquals(4L, manager.getWorkingHostStats().getTriggersRebuiltCount());
    }

    @Test
    void incrementTriggersCreatedCount_updatesHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementTriggersCreatedCount(6L);
        assertEquals(6L, manager.getWorkingHostStats().getTriggersCreatedCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void flush_channelStatsWithUnknownNodeId_updatesToResolvedNodeId() throws Exception {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        Field field = StatisticManager.class.getDeclaredField("channelStats");
        field.setAccessible(true);
        Map<String, ChannelStats> csMap = (Map<String, ChannelStats>) field.get(manager);
        ChannelStats unknownStats = new ChannelStats("Unknown", "server-1", new Date(), null, "chan1");
        unknownStats.incrementDataRouted(1);
        csMap.put("chan1", unknownStats);
        manager.flush();
        ArgumentCaptor<ChannelStats> captor = ArgumentCaptor.forClass(ChannelStats.class);
        verify(statisticService, atLeastOnce()).save(captor.capture());
        assertEquals("node1", captor.getValue().getNodeId());
    }

    @Test
    void flush_debugLoggingEnabled_logsPeriodicStats() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataSent(Constants.CHANNEL_DEFAULT, 10L);
        assertEquals(10L, manager.getWorkingChannelStats().get(Constants.CHANNEL_DEFAULT).getDataSent());
        manager.flush();
    }

    @Test
    void flush_hostStatsWithUnknownNodeId_updatesToResolvedNodeId() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        when(nodeService.getCachedIdentity()).thenReturn(null, node("node1"));
        manager.incrementRestart();
        assertEquals("Unknown", manager.getWorkingHostStats().getNodeId());
        manager.flush();
        ArgumentCaptor<HostStats> captor = ArgumentCaptor.forClass(HostStats.class);
        verify(statisticService, atLeastOnce()).save(captor.capture());
        assertEquals("node1", captor.getValue().getNodeId());
    }

    @Test
    void flush_hostStatsDataGapCountNull_queriesDataService() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        when(dataService.countDataGaps()).thenReturn(42L);
        manager.incrementRestart();
        assertNull(manager.getWorkingHostStats().getDataGapCount());
        manager.flush();
        verify(dataService, atLeastOnce()).countDataGaps();
    }

    @Test
    void flush_hostStatsDataUnroutedCountNull_queriesRouterService() {
        IRouterService routerService = mock(IRouterService.class);
        when(routerService.getUnroutedDataCount()).thenReturn(7L);
        when(engine.getRouterService()).thenReturn(routerService);
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementRestart();
        assertNull(manager.getWorkingHostStats().getDataUnroutedCount());
        manager.flush();
        verify(routerService, atLeastOnce()).getUnroutedDataCount();
    }

    @Test
    void flush_jobStatsAboveThreshold_savesJobStats() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(0L);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.addJobStats("Job1", 0L, 100L, 10L);
        assertEquals("Job1", manager.getWorkingJobStats().get(0).getJobName());
        manager.flush();
        verify(statisticService, atLeastOnce()).save(any(JobStats.class));
    }

    @Test
    void getNodeStatsForPeriod_withCurrentChannelStats_includesCurrentStats() {
        Date start = new Date(System.currentTimeMillis() - 60000);
        Date end = new Date();
        NodeStatsByPeriodMap periodMap = new NodeStatsByPeriodMap(start, end, Collections.emptyList(), 1);
        when(statisticService.getNodeStatsForPeriod(any(), any(), any(), anyInt())).thenReturn(periodMap);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataRouted("chan1", 1L);
        assertDoesNotThrow(() -> manager.getNodeStatsForPeriod(start, end, "node1", 1));
    }

    @Test
    void getWorkingChannelStats_whenNullChannelStats_returnsEmptyMap() throws Exception {
        Field field = StatisticManager.class.getDeclaredField("channelStats");
        field.setAccessible(true);
        field.set(manager, null);
        Map<String, ChannelStats> result = manager.getWorkingChannelStats();
        assertTrue(result.isEmpty());
    }

    @Test
    void getWorkingJobStats_whenNullJobStats_returnsEmptyList() throws Exception {
        Field field = StatisticManager.class.getDeclaredField("jobStats");
        field.setAccessible(true);
        field.set(manager, null);
        List<JobStats> result = manager.getWorkingJobStats();
        assertTrue(result.isEmpty());
    }

    @Test
    void getWorkingHostStats_whenNullHostStats_returnsDefaultHostStats() {
        HostStats result = manager.getWorkingHostStats();
        assertNotNull(result);
        assertEquals(0L, result.getRestarted());
    }

    @Test
    void resetChannelStats_withConfiguredChannels_initializesChannelEntry() {
        when(configurationService.getNodeChannels(false)).thenReturn(Collections.singletonList(new NodeChannel("chan1")));
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(false);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        manager.flush();
        assertTrue(manager.getWorkingChannelStats().containsKey("chan1"));
    }

    @Test
    void incrementNodesPushed_withKnownNode_setsNodeIdOnHostStats() {
        when(nodeService.getCachedIdentity()).thenReturn(node("node42"));
        manager.incrementNodesPushed(1L);
        assertEquals("node42", manager.getWorkingHostStats().getNodeId());
    }

    @Test
    void incrementDataSent_withNonNullMetricsCounter_invokesCounterAdd() {
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        IUpDownCounter counter = mock(IUpDownCounter.class);
        when(engine.getMetricsService()).thenReturn(metricsService);
        when(metricsService.getUpDownCounter(anyString(), any())).thenReturn(counter);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataSent("chan1", 5L);
        assertEquals(5L, manager.getWorkingChannelStats().get("chan1").getDataSent());
        verify(counter).add(5L);
    }

    @Test
    void setDataUnRouted_withNonNullMetricsGauge_invokesGaugeSetValue() {
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        ISymDoubleGauge gauge = mock(ISymDoubleGauge.class);
        when(engine.getMetricsService()).thenReturn(metricsService);
        when(metricsService.getDoubleGauge(anyString(), any())).thenReturn(gauge);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.setDataUnRouted("chan1", 10L);
        assertEquals(10L, manager.getWorkingChannelStats().get("chan1").getDataUnRouted());
        verify(gauge).setValue(10.0);
    }

    @Test
    void updateDataMinCreateTime_withNonNullMetricsService_reachesGaugeNullCheck() {
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        when(engine.getMetricsService()).thenReturn(metricsService);
        when(metricsService.getLongGauge(anyString(), any())).thenReturn(null);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        Date minTime = new Date();
        manager.updateDataMinCreateTime("chan1", minTime);
        assertEquals(minTime, manager.getWorkingChannelStats().get("chan1").getDataMinCreateTime());
    }

    @Test
    void incrementRestart_withNonNullMetricsCounter_invokesCounterAdd() {
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        IUpDownCounter counter = mock(IUpDownCounter.class);
        when(engine.getMetricsService()).thenReturn(metricsService);
        when(metricsService.getUpDownCounter(anyString())).thenReturn(counter);
        manager.incrementRestart();
        assertEquals(1L, manager.getWorkingHostStats().getRestarted());
        verify(counter).add(1L);
    }

    @Test
    void setDataGapCount_withNonNullMetricsGauge_invokesGaugeSetValue() {
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        ISymDoubleGauge gauge = mock(ISymDoubleGauge.class);
        when(engine.getMetricsService()).thenReturn(metricsService);
        when(metricsService.getDoubleGauge(anyString())).thenReturn(gauge);
        manager.setDataGapCount(5L);
        assertEquals(5L, manager.peekHostStats().getDataGapCount());
        verify(gauge).setValue(5.0);
    }

    @Test
    void getMostRecentUserDataSyncProcessInfo_afterEntryStored_returnsStoredProcess() {
        ProcessInfoKey k = new ProcessInfoKey("src1", "tgt1", ProcessType.PUSH_JOB_EXTRACT);
        ProcessInfo first = manager.newProcessInfo(k);
        first.setCurrentDataCount(10);
        first.setCurrentTableName("user_orders");
        first.setCurrentChannelId(Constants.CHANNEL_DEFAULT);
        manager.newProcessInfo(k);
        ProcessInfo result = manager.getMostRecentUserDataSyncProcessInfo("src1", "tgt1");
        assertNotNull(result);
        assertEquals(10L, result.getCurrentDataCount());
    }

    private static class ThrowingProcessInfo extends ProcessInfo {
        ThrowingProcessInfo(ProcessInfoKey k) {
            super(k);
        }

        @Override
        public int compareTo(ProcessInfo o) {
            throw new IllegalArgumentException("Simulated comparator failure");
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return new ProcessInfo(getKey());
        }
    }

    private static class StatisticManagerUnderTest extends StatisticManager {
        StatisticManagerUnderTest(ISymmetricEngine engine) {
            super(engine);
        }

        void injectProcessInfo(ProcessInfoKey key, ProcessInfo info) {
            processInfos.put(key, info);
        }

        HostStats peekHostStats() {
            return getHostStats();
        }
    }
}
