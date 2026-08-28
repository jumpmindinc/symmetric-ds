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
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingBatchSummary;
import org.jumpmind.symmetric.model.IncomingBatchSummaryByNodeBriefStats;

/**
 * This service provides an API to access to the incoming batch table.
 */
public interface IIncomingBatchService {
    public List<String> getNodesInError();

    public int countIncomingBatchesInError();

    public int countIncomingBatchesInError(String channelId);

    public Date getIncomingBatchesLatestUpdateSql();

    public IncomingBatch findIncomingBatch(long batchId, String nodeId);

    public void refreshIncomingBatch(IncomingBatch batch);

    public List<IncomingBatch> findIncomingBatchErrors(int maxRows);

    public boolean acquireIncomingBatch(IncomingBatch batch);

    public void insertIncomingBatch(ISqlTransaction transaction, IncomingBatch batch);

    public void insertIncomingBatch(IncomingBatch batch);

    public int updateIncomingBatch(ISqlTransaction transaction, IncomingBatch batch);

    public int updateIncomingBatch(IncomingBatch batch);

    public int deleteIncomingBatch(IncomingBatch batch);

    public List<Date> listIncomingBatchTimes(List<String> nodeIds, List<String> channels,
            List<IncomingBatch.Status> statuses, List<Long> loads, boolean ascending);

    public List<IncomingBatch> listIncomingBatches(List<String> nodeIds, List<String> channels,
            List<IncomingBatch.Status> statuses, List<Long> loads, Date startAtCreateTime,
            Date startAtLastUpdateTime, int maxRowsToRetrieve, boolean ascending);

    public List<IncomingBatch> listIncomingBatchesWithLimit(int offset, int limit, List<FilterCriterion> filter,
            String orderColumn, String orderDirection);

    public int countIncomingBatchesWithLimit(List<FilterCriterion> filter);

    public void markIncomingBatchesOk(String nodeId);

    public void removingIncomingBatches(String nodeId);

    public List<IncomingBatch> listIncomingBatchesInErrorFor(String nodeId);

    public boolean isRecordOkBatchesEnabled();

    public Map<String, BatchId> findMaxBatchIdsByChannel();

    public List<IncomingBatchSummary> findIncomingBatchSummaryByChannel(Status... statuses);

    public List<IncomingBatchSummary> findIncomingBatchSummary(Status... statuses);

    public List<IncomingBatchSummary> findIncomingBatchSummaryByNode(String nodeId,
            Date sinceCreateTime, Status... statuses);

    public List<IncomingBatchSummary> findIncomingBatchSummaryByNodeAndChannel(String nodeId, String channelId,
            Date sinceCreateTime, Status... statuses);

    public List<IncomingBatchSummaryByNodeBriefStats> findIncomingBatchSummaryByNodeBriefStats();

    public Map<String, Date> findLastUpdatedByChannel();

    public List<BatchId> getAllBatches();

    public Collection<String> getReadyQueues(String sourceNodeId);

    public void setReadyQueues(String sourceNodeId, Collection<String> queues);
}