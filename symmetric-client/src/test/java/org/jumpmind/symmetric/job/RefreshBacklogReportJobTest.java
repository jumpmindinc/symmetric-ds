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
package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.OutgoingBatchSummaryByNodeBriefStats;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusMetricsMap;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RefreshBacklogReportJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IOutgoingBatchService outgoingBatchService;
    private IEngineMetricsService metricsService;
    private INodeBatchStatusMetricsMap batchMetrics;
    private ThreadPoolTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        outgoingBatchService = mock(IOutgoingBatchService.class);
        metricsService = mock(IEngineMetricsService.class);
        batchMetrics = mock(INodeBatchStatusMetricsMap.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(engine.getMetricsService()).thenReturn(metricsService);
        when(metricsService.createNodeBatchStatusMetricsMap(any(), any())).thenReturn(batchMetrics);
    }

    private RefreshBacklogReportJob newJob() {
        return new RefreshBacklogReportJob(engine, taskScheduler);
    }

    private static OutgoingBatchSummaryByNodeBriefStats row(String nodeId, String status, long batches, long rows) {
        return new OutgoingBatchSummaryByNodeBriefStats(nodeId, status, new Date(), batches, rows);
    }

    @Test
    void getDefaults_returnsNonNull() {
        assertNotNull(newJob().getDefaults());
    }

    @Test
    void isRateLimited_returnsTrue() {
        assertTrue(newJob().isRateLimited());
    }

    @Test
    void getMinSchedulePeriodMs_matchesEveryFifteenMinutes() {
        long expected = Long.parseLong(JobDefaults.EVERY_FIFTEEN_MINUTES);
        assertEquals(expected, newJob().getMinSchedulePeriodMs());
    }

    @Test
    void doJob_callsFindOutgoingBatchSummaryByNodeBriefStats() {
        when(outgoingBatchService.findOutgoingBatchSummaryByNodeBriefStats()).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> newJob().doJob(false));
        verify(outgoingBatchService).findOutgoingBatchSummaryByNodeBriefStats();
    }

    @Test
    void populateNodeMetrics_emptyList_noBatchAndRowCountsCalled() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(Collections.emptyList());
        verify(batchMetrics, never()).setBatchAndRowCounts(any(), any(), anyLong(), anyLong());
    }

    @Test
    void populateNodeMetrics_blankNodeId_isSkipped() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(row("  ", "R", 1, 10)));
        verify(batchMetrics, never()).setBatchAndRowCounts(any(), any(), anyLong(), anyLong());
    }

    @Test
    void populateNodeMetrics_emptyStatus_isSkipped() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(row("node1", "", 1, 10)));
        verify(batchMetrics, never()).setBatchAndRowCounts(any(), any(), anyLong(), anyLong());
    }

    @Test
    void populateNodeMetrics_unroutedNodeId_isSkipped() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(row(Constants.UNROUTED_NODE_ID, "R", 1, 10)));
        verify(batchMetrics, never()).setBatchAndRowCounts(any(), any(), anyLong(), anyLong());
    }

    @Test
    void populateNodeMetrics_singleEntry_flushesAtEndOfList() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(row("node1", "R", 5, 100)));
        verify(batchMetrics, times(1)).setBatchAndRowCounts("node1", "R", 5L, 100L);
    }

    @Test
    void populateNodeMetrics_multipleEntriesSameNodeAndStatus_accumulates() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(
                row("node1", "R", 3, 50),
                row("node1", "R", 2, 30)));
        verify(batchMetrics, times(1)).setBatchAndRowCounts("node1", "R", 5L, 80L);
    }

    @Test
    void populateNodeMetrics_differentNodeId_flushesOnTransitionAndAtEnd() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(
                row("node1", "R", 3, 50),
                row("node2", "R", 2, 30)));
        verify(batchMetrics).setBatchAndRowCounts("node1", "R", 3L, 50L);
        verify(batchMetrics).setBatchAndRowCounts("node2", "R", 2L, 30L);
    }

    @Test
    void populateNodeMetrics_differentStatus_flushesOnTransitionAndAtEnd() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(
                row("node1", "R", 3, 50),
                row("node1", "E", 1, 5)));
        verify(batchMetrics).setBatchAndRowCounts("node1", "R", 3L, 50L);
        verify(batchMetrics).setBatchAndRowCounts("node1", "E", 1L, 5L);
    }

    @Test
    void populateNodeMetrics_firstCall_initializesOutgoingBatchMetrics() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(row("node1", "R", 1, 10)));
        verify(metricsService).createNodeBatchStatusMetricsMap(any(), any());
    }

    @Test
    void populateNodeMetrics_secondCall_reusesExistingMetricsMap() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(row("node1", "R", 1, 10)));
        job.populateNodeMetrics(List.of(row("node2", "W", 2, 20)));
        verify(metricsService, times(1)).createNodeBatchStatusMetricsMap(any(), any());
    }

    @Test
    void populateNodeMetrics_mixedSkippedAndValidEntries_onlyFlusheValid() {
        RefreshBacklogReportJob job = newJob();
        job.populateNodeMetrics(List.of(
                row(Constants.UNROUTED_NODE_ID, "R", 1, 10),
                row("", "R", 1, 10),
                row("node1", "R", 4, 80)));
        verify(batchMetrics, times(1)).setBatchAndRowCounts("node1", "R", 4L, 80L);
    }
}
