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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IStatisticService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticManagerTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private IStatisticService statisticService;
    private IClusterService clusterService;
    private IDataService dataService;
    private StatisticManager manager;

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
        manager = new StatisticManager(engine);
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
        assertDoesNotThrow(() -> manager.incrementDataRouted(Constants.CHANNEL_DEFAULT, 5L));
    }

    @Test
    void setDataUnRouted_nullMetricsService_doesNotThrow() {
        assertDoesNotThrow(() -> manager.setDataUnRouted(Constants.CHANNEL_DEFAULT, 10L));
    }

    @Test
    void incrementDataExtracted_nullMetricsService_doesNotThrow() {
        assertDoesNotThrow(() -> manager.incrementDataExtracted(Constants.CHANNEL_DEFAULT, 3L));
    }

    @Test
    void incrementDataBytesExtracted_nullMetricsService_doesNotThrow() {
        assertDoesNotThrow(() -> manager.incrementDataBytesExtracted(Constants.CHANNEL_DEFAULT, 100L));
    }

    @Test
    void incrementDataLoaded_nullMetricsService_doesNotThrow() {
        assertDoesNotThrow(() -> manager.incrementDataLoaded(Constants.CHANNEL_DEFAULT, 7L));
    }

    @Test
    void incrementRestart_nullMetricsService_doesNotThrow() {
        assertDoesNotThrow(() -> manager.incrementRestart());
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
        manager.flush();
        verify(dataService, never()).countDataGaps();
    }

    @Test
    void setDataUnroutedCount_doesNotThrow() {
        assertDoesNotThrow(() -> manager.setDataUnroutedCount(3L));
    }

    @Test
    void getChannelStats_whenNodeIdentityIsNull_doesNotThrow() {
        when(nodeService.getCachedIdentity()).thenReturn(null);
        assertDoesNotThrow(() -> manager.incrementDataRouted(Constants.CHANNEL_DEFAULT, 1L));
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
        manager.flush();
        verify(statisticService, never()).save(any(ChannelStats.class));
    }

    @Test
    void flush_withRecordStatisticsEnabled_nonZeroStats_savesChannelStats() {
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(true);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(-1L);
        when(nodeService.getCachedIdentity()).thenReturn(node("node1"));
        manager.incrementDataRouted(Constants.CHANNEL_DEFAULT, 5L);
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
}
