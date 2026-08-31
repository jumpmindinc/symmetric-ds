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
package org.jumpmind.symmetric.job;

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_FIFTEEN_MINUTES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_BATCHES_INCOMING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_BATCHES_OUTGOING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_INCOMING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_OUTGOING;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.IncomingBatchSummaryByNodeBriefStats;
import org.jumpmind.symmetric.model.OutgoingBatchSummaryByNodeBriefStats;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusMetricsMap;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import io.micrometer.common.util.StringUtils;

public class RefreshBacklogReportJob extends AbstractJob {
    private INodeBatchStatusMetricsMap outgoingBatchMetrics;
    private INodeBatchStatusMetricsMap incomingBatchMetrics;

    public RefreshBacklogReportJob(ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(ClusterConstants.REFRESH_BACKLOG_REPORT, engine, taskScheduler);
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults()
                .schedule(EVERY_FIFTEEN_MINUTES)
                .description("Refresh outgoing backlog report and update batch metrics gauges");
    }

    @Override
    protected long getMinSchedulePeriodMs() {
        return Long.parseLong(EVERY_FIFTEEN_MINUTES);
    }

    @Override
    public boolean isRateLimited() {
        return true;
    }

    @Override
    public void doJob(boolean force) throws Exception {
        List<OutgoingBatchSummaryByNodeBriefStats> outgoingStats = engine.getOutgoingBatchService().findOutgoingBatchSummaryByNodeBriefStats();
        populateOutgoingNodeMetrics(outgoingStats);
        List<IncomingBatchSummaryByNodeBriefStats> incomingStats = engine.getIncomingBatchService().findIncomingBatchSummaryByNodeBriefStats();
        populateIncomingNodeMetrics(incomingStats);
    }

    protected void populateOutgoingNodeMetrics(List<OutgoingBatchSummaryByNodeBriefStats> stats) {
        if (outgoingBatchMetrics == null) {
            outgoingBatchMetrics = engine.getMetricsService()
                    .createNodeBatchStatusMetricsMap(METRIC_ID_BATCHES_OUTGOING, METRIC_ID_DATA_OUTGOING);
        }
        accumulateAndSetMetrics(stats, outgoingBatchMetrics, OutgoingBatchSummaryByNodeBriefStats::nodeId,
                OutgoingBatchSummaryByNodeBriefStats::status, OutgoingBatchSummaryByNodeBriefStats::batchCount,
                OutgoingBatchSummaryByNodeBriefStats::dataRows);
    }

    protected void populateIncomingNodeMetrics(List<IncomingBatchSummaryByNodeBriefStats> stats) {
        if (incomingBatchMetrics == null) {
            incomingBatchMetrics = engine.getMetricsService()
                    .createNodeBatchStatusMetricsMap(METRIC_ID_BATCHES_INCOMING, METRIC_ID_DATA_INCOMING);
        }
        accumulateAndSetMetrics(stats, incomingBatchMetrics, IncomingBatchSummaryByNodeBriefStats::nodeId,
                IncomingBatchSummaryByNodeBriefStats::status, IncomingBatchSummaryByNodeBriefStats::batchCount,
                IncomingBatchSummaryByNodeBriefStats::dataRows);
    }

    private <T> void accumulateAndSetMetrics(List<T> stats, INodeBatchStatusMetricsMap metricsMap,
            Function<T, String> nodeIdOf, Function<T, String> statusOf,
            ToLongFunction<T> batchCountOf, ToLongFunction<T> dataRowsOf) {
        // Results are ORDER BY node_id, status, batch_date — accumulate totals per (node_id, status) group - in one pass!
        NodeBatchGroup current = null;
        long processedEntriesCount = 0;
        long currentEntriesCount = 0;
        for (T row : stats) {
            String entryNodeId = nodeIdOf.apply(row);
            String entryStatus = statusOf.apply(row);
            if (StringUtils.isBlank(entryNodeId) || StringUtils.isBlank(entryStatus) || Constants.UNROUTED_NODE_ID.equals(entryNodeId)) {
                continue;
            }
            if (current != null && (!entryNodeId.equals(current.nodeId()) || !entryStatus.equals(current.batchStatus()))) {
                metricsMap.setBatchAndRowCounts(current.nodeId(), current.batchStatus(), current.totalBatches(), current.totalDataRows());
                if (log.isDebugEnabled()) {
                    log.debug("Recorded backlog observation. Node={}, Batch.status={}, batches={}, rows={}, entries={}",
                            entryNodeId, entryStatus, current.totalBatches(), current.totalDataRows(), currentEntriesCount);
                }
                currentEntriesCount = 0;
                current = null;
            }
            if (current == null) {
                current = new NodeBatchGroup(entryNodeId, entryStatus, 0, 0);
            }
            current = current.accumulate(batchCountOf.applyAsLong(row), dataRowsOf.applyAsLong(row));
            processedEntriesCount++;
            currentEntriesCount++;
        }
        if (current != null) {
            metricsMap.setBatchAndRowCounts(current.nodeId(), current.batchStatus(), current.totalBatches(), current.totalDataRows());
        }
        log.info("Processed {} node-batch-date entries to populate metrics.", processedEntriesCount);
    }

    private record NodeBatchGroup(String nodeId, String batchStatus, long totalBatches, long totalDataRows) {
        NodeBatchGroup accumulate(long batches, long rows) {
            return new NodeBatchGroup(nodeId, batchStatus, totalBatches + batches, totalDataRows + rows);
        }
    }
}
