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

import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_BATCHES_OUTGOING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_OUTGOING;

import java.util.List;

import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.OutgoingBatchSummaryByNodeBriefStats;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusMetricsMap;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import io.micrometer.common.util.StringUtils;

public class RefreshBacklogReportJob extends AbstractJob {
    private INodeBatchStatusMetricsMap outgoingBatchMetrics;

    public RefreshBacklogReportJob(ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(ClusterConstants.REFRESH_BACKLOG_REPORT, engine, taskScheduler);
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults()
                .schedule(JobDefaults.EVERY_3_MINUTES)
                .description("Refresh outgoing backlog report and update batch metrics gauges");
    }

    @Override
    public void doJob(boolean force) throws Exception {
        List<OutgoingBatchSummaryByNodeBriefStats> stats = engine.getOutgoingBatchService().findOutgoingBatchSummaryByNodeBriefStats();
        populateNodeMetrics(stats);
        populateNodeOutgoingBacklogReport(stats);
    }

    private void populateNodeMetrics(List<OutgoingBatchSummaryByNodeBriefStats> stats) {
        if (outgoingBatchMetrics == null) {
            outgoingBatchMetrics = engine.getMetricsService()
                    .createNodeBatchStatusMetricsMap(METRIC_ID_BATCHES_OUTGOING, METRIC_ID_DATA_OUTGOING);
        }
        // Results are ORDER BY node_id, status, batch_date — accumulate totals per (node_id, status) group - in one pass!
        String currentNodeId = null;
        String currentStatus = null;
        long totalBatches = 0;
        long totalDataRows = 0;
        long processedEntriesCount = 0;
        long currentEntriesCount = 0;
        for (OutgoingBatchSummaryByNodeBriefStats row : stats) {
            String entryNodeId = row.nodeId();
            String entryStatus = row.status();
            if (StringUtils.isBlank(entryNodeId) || StringUtils.isBlank(entryStatus) || entryNodeId.equals("-1")) {
                continue;
            }
            if (!entryNodeId.equals(currentNodeId) || !entryStatus.equals(currentStatus)) {
                if (currentNodeId != null) {
                    outgoingBatchMetrics.setBatchAndRowCounts(currentNodeId, currentStatus, totalBatches, totalDataRows);
                    if (log.isDebugEnabled()) {
                        log.debug("Recorded backlog dobservation. Node={}, Batch.status={}, batches={}, rows={}, entries={}",
                                entryNodeId, entryStatus, totalBatches, totalDataRows, currentEntriesCount);
                    }
                    currentEntriesCount = 0;
                }
                currentNodeId = entryNodeId;
                currentStatus = entryStatus;
                totalBatches = 0;
                totalDataRows = 0;
            }
            totalBatches += row.batchCount();
            totalDataRows += row.dataRows();
            processedEntriesCount++;
            currentEntriesCount++;
        }
        if (!StringUtils.isBlank(currentNodeId)) {
            outgoingBatchMetrics.setBatchAndRowCounts(currentNodeId, currentStatus, totalBatches, totalDataRows);
        }
        log.info("Processed {} node-batch-date entries to populate metrics.", processedEntriesCount);
    }

    private void populateNodeOutgoingBacklogReport(List<OutgoingBatchSummaryByNodeBriefStats> stats) {
        try {
            @SuppressWarnings("unchecked")
            Class<IExtensionPoint> saverClass = (Class<IExtensionPoint>) Class.forName(
                    "com.jumpmind.symmetric.console.service.INodeBatchBacklogReportSaver");
            IExtensionPoint saver = engine.getExtensionService().getExtensionPoint(saverClass);
            if (saver != null) {
                saverClass.getMethod("saveReport", List.class).invoke(saver, stats);
            }
        } catch (ClassNotFoundException e) {
            log.debug("Backlog report saver not available");
        } catch (ReflectiveOperationException e) {
            log.warn("Failed to save outgoing backlog report", e);
        }
    }
}
