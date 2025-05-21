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
package org.jumpmind.symmetric.service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.OutgoingBatchSummary;
import org.jumpmind.symmetric.model.OutgoingBatches;
import org.jumpmind.symmetric.model.ReadyChannels;

/**
 * This service provides an API to access to the outgoing batch table.
 */
public interface IOutgoingBatchService {
    public List<String> getNodesInError();

    public void updateOutgoingError(long batchId, String nodeId);

    public void markAllAsSentForNode(String nodeId, boolean includeConfigChannel);

    public void markAllConfigAsSentForNode(String nodeId);

    public void updateAbandonedRoutingBatches();

    public OutgoingBatch findOutgoingBatch(long batchId, String nodeId);

    public OutgoingBatch findOutgoingBatchFirstCommon(long batchId);

    public OutgoingBatches getOutgoingBatches(String nodeId, boolean includeDisabledChannels);

    public OutgoingBatches getOutgoingBatches(String nodeId, String channelId, boolean includeDisabledChannels);

    public OutgoingBatches getOutgoingBatches(String nodeId, String channelThread, NodeGroupLinkAction eventAction,
            NodeGroupLinkAction defaultEventAction, boolean includeDisabledChannels);

    public OutgoingBatches getOutgoingBatchRange(long startBatchId, long endBatchId);

    public OutgoingBatches getOutgoingBatchByLoad(long loadI);

    public OutgoingBatches getOutgoingBatchByLoadRangeAndTable(long loadId, long startBatchId,
            long endBatchId, String tableName);

    public int cancelLoadBatches(long loadId);

    public OutgoingBatches getOutgoingBatchRange(String nodeId, Date startDate, Date endDate, String... channels);

    public OutgoingBatches getOutgoingBatchErrors(int maxRows);

    public boolean isInitialLoadComplete(String nodeId);

    public boolean areAllLoadBatchesComplete(String nodeId);

    public boolean isUnsentDataOnChannelForNode(String channelId, String nodeId);

    public void updateOutgoingBatch(OutgoingBatch batch);

    public void updateOutgoingBatchStatus(ISqlTransaction transaction, Status status, String nodeId, long startBatchId, long endBatchId);

    public void updateOutgoingSetupBatchStatusByStatus(ISqlTransaction transaction, String targetNodeId, long loadId,
            long maxBatchId, String fromStatus, String toStatus);

    public void updateOutgoingLoadBatchStatusByStatus(ISqlTransaction transaction, String targetNodeId, long loadId,
            long startDataBatchId, long endDataBatchId, String fromStatus, String toStatus);

    public void updateOutgoingFinalizeBatchStatusByStatus(ISqlTransaction transaction, String targetNodeId, long loadId,
            long minBatchId, String fromStatus, String toStatus);

    public void updateCommonBatchExtractStatistics(OutgoingBatch batch);

    public void updateOutgoingBatch(ISqlTransaction transaction, OutgoingBatch outgoingBatch);

    public void updateOutgoingBatches(List<OutgoingBatch> batches);

    public void updateOutgoingBatches(ISqlTransaction transaction, List<OutgoingBatch> batches, int flushSize);

    public void insertOutgoingBatch(OutgoingBatch outgoingBatch);

    public void insertOutgoingBatch(ISqlTransaction transaction, OutgoingBatch outgoingBatch);

    public void insertOutgoingBatches(ISqlTransaction transaction, List<OutgoingBatch> batches, int flushSize, boolean isCommon);

    public int countOutgoingBatchesInError();

    public Date getOutgoingBatchesLatestUpdateSql();

    public int countOutgoingBatchesUnsent();

    public int countOutgoingBatchesUnsentOfflineNodes(String minsBeforeOfflineParam);

    public int[] countOutgoingNonSystemBatchesRowsUnsent();

    public int countOutgoingBatchesInError(String channelId);

    public int countOutgoingBatchesUnsent(String channelId);

    public int countOutgoingBatchesUnsentHeartbeat();

    public Map<String, Integer> countOutgoingBatchesPendingByChannel(String nodeId);

    public long countUnsentRowsByTargetNode(String nodeId);

    public int countUnsentBatchesByTargetNode(String nodeId, boolean includeHeartbeats);

    public Map<String, Long> countUnsentBatchesBlocked();

    public List<OutgoingBatchSummary> findOutgoingBatchSummary(OutgoingBatch.Status... statuses);

    public List<OutgoingBatchSummary> findOutgoingBatchSummaryByChannel(OutgoingBatch.Status... statuses);

    public List<OutgoingBatchSummary> findOutgoingBatchSummaryByNode(String nodeId,
            Date sinceCreateTime, Status... statuses);

    public int countOutgoingBatches(List<String> nodeIds, List<String> channels,
            List<OutgoingBatch.Status> statuses, List<Long> loads);

    public List<OutgoingBatch> listOutgoingBatches(List<String> nodeIds, List<String> channels,
            List<OutgoingBatch.Status> statuses, List<Long> loads, long startAtBatchId,
            Date startAtLastUpdateTime, int rowsExpected, boolean ascending);

    public List<OutgoingBatch> listOutgoingBatchesWithLimit(int offset, int limit, List<FilterCriterion> filter,
            String orderColumn, String orderDirection);

    public int countOutgoingBatchesWithLimit(List<FilterCriterion> filter);

    public void copyOutgoingBatches(String channelId, long startBatchId, String fromNodeId, String toNodeId);

    public List<Long> getAllBatches();

    public List<OutgoingBatch> getBatchesInProgress();

    public Collection<String> getReadyQueues(String nodeId, boolean refreshCache);

    public Map<String, Collection<String>> getReadyQueues(boolean refreshCache);

    public Map<String, ReadyChannels> getReadyChannelsFromDb();
}