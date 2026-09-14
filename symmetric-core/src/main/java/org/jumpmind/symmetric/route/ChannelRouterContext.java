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
package org.jumpmind.symmetric.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataEvent;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.slf4j.Logger;

public class ChannelRouterContext extends SimpleRouterContext {
    public static final String STAT_INSERT_DATA_EVENTS_MS = "data.events.insert.time.ms";
    public static final String STAT_DATA_ROUTER_MS = "data.router.time.ms";
    public static final String STAT_QUERY_EXEC_TIME_MS = "query.exec.time.ms";
    public static final String STAT_READ_DATA_MS = "data.read.total.time.ms";
    public static final String STAT_REREAD_DATA_MS = "data.reread.time.ms";
    public static final String STAT_ENQUEUE_DATA_MS = "data.enqueue.time.ms";
    public static final String STAT_ENQUEUE_EOD_MS = "data.enqueue.eod.time.ms";
    public static final String STAT_DATA_EVENTS_INSERTED = "data.events.insert.count";
    public static final String STAT_DATA_ROUTED_COUNT = "data.routed.count";
    public static final String STAT_INSERT_BATCHES_MS = "batches.insert.time.ms";
    public static final String STAT_BATCHES_INSERTED = "batches.insert.count";
    public static final String STAT_BATCHES_COMMON = "batches.common.count";
    public static final String STAT_BATCHES_NONCOMMON = "batches.noncommon.count";
    public static final String STAT_UPDATE_BATCHES_MS = "batches.update.time.ms";
    public static final String STAT_MULTI_QUERY_COUNT = "multi.query.count";
    public static final String STAT_LOOKUP_AVAILABLE_NODES_MS = "lookup.avail.nodes.ms";
    public static final String STAT_LOOKUP_TRIGGER_ROUTERS_MS = "lookup.trigger.routers.ms";
    public static final String STAT_ROUTE_TOTAL_TIME = "total.time.ms";
    private Map<String, OutgoingBatch> batchesByNodes = new HashMap<String, OutgoingBatch>();
    private Map<Integer, Map<String, OutgoingBatch>> batchesByGroups = new HashMap<Integer, Map<String, OutgoingBatch>>();
    private Map<TriggerRouter, Set<Node>> availableNodes = new HashMap<TriggerRouter, Set<Node>>();
    private Set<IDataRouter> usedDataRouters = new HashSet<IDataRouter>();
    private Map<String, Long> timesByRouter = new HashMap<String, Long>();
    private ISqlTransaction sqlTransaction;
    private boolean needsCommitted = false;
    private long createdTimeInMs = System.currentTimeMillis();
    private Data lastDataProcessed;
    private List<DataEvent> dataEventsToSend = new ArrayList<DataEvent>();
    private boolean produceCommonBatches = false;
    private boolean produceGroupBatches = false;
    private boolean nonCommonForIncoming = false;
    private boolean forceNonCommon = false;
    private boolean onlyDefaultRoutersAssigned = false;
    private boolean overrideContainsBigLob = false;
    private long lastLoadId = -1;
    private long startDataId;
    private long endDataId;
    private long dataReadCount;
    private long peekAheadFillCount;
    private long maxPeekAheadQueueSize;
    private int maxBatchesJdbcFlushSize;
    private long dataRereadCount;
    private List<DataGap> dataGaps = new ArrayList<DataGap>();
    private long lastDataId = -1;
    private List<Long> dataIds = new ArrayList<Long>();
    private List<Long> uncommittedDataIds = new ArrayList<Long>();
    private long uncommittedDataEventCount = 0;
    private long committedDataEventCount = 0;
    private long committedDataIdCount = 0;
    private IBatchAlgorithm batchAlgorithm;
    private Map<Long, DataMetaData> configDataIdsProcessed = new HashMap<Long, DataMetaData>();

    public ChannelRouterContext(String nodeId, NodeChannel channel, ISqlTransaction transaction, IBatchAlgorithm batchAlgorithm) {
        super(nodeId, channel);
        this.sqlTransaction = transaction;
        this.sqlTransaction.setInBatchMode(true);
        this.batchAlgorithm = batchAlgorithm;
    }

    public List<DataEvent> getDataEventList() {
        return dataEventsToSend;
    }

    public void clearDataEventsList() {
        dataEventsToSend.clear();
    }

    public void addDataEvent(long dataId, long batchId) {
        dataEventsToSend.add(new DataEvent(dataId, batchId));
        if (dataId != lastDataId) {
            uncommittedDataIds.add(dataId);
            lastDataId = dataId;
        }
        uncommittedDataEventCount++;
    }

    public void addData(long dataId) {
        if (dataId != lastDataId) {
            uncommittedDataIds.add(dataId);
            lastDataId = dataId;
        }
    }

    public void addConfigDataMetaData(DataMetaData dataMetaData) {
        configDataIdsProcessed.put(dataMetaData.getData().getDataId(), dataMetaData);
    }

    public DataMetaData getConfigDataMetaData(Long dataId) {
        return configDataIdsProcessed.get(dataId);
    }

    public long getCommittedDataEventCount() {
        return this.committedDataEventCount;
    }

    public long getCommittedDataIdCount() {
        return this.committedDataIdCount;
    }

    public Map<String, OutgoingBatch> getBatchesByNodes() {
        return batchesByNodes;
    }

    public Map<Integer, Map<String, OutgoingBatch>> getBatchesByGroups() {
        return batchesByGroups;
    }

    public Map<TriggerRouter, Set<Node>> getAvailableNodes() {
        return availableNodes;
    }

    public void commit() {
        try {
            sqlTransaction.commit();
            dataIds.addAll(uncommittedDataIds);
            committedDataEventCount += uncommittedDataEventCount;
            committedDataIdCount += uncommittedDataIds.size();
        } finally {
            clearState();
        }
    }

    protected void clearState() {
        this.usedDataRouters.clear();
        this.timesByRouter.clear();
        this.encountedTransactionBoundary = false;
        this.requestGapDetection = false;
        this.batchesByNodes.clear();
        this.batchesByGroups.clear();
        this.forceNonCommon = false;
        this.availableNodes.clear();
        this.dataEventsToSend.clear();
        this.uncommittedDataIds.clear();
        this.uncommittedDataEventCount = 0;
    }

    public void rollback() {
        try {
            sqlTransaction.rollback();
        } catch (SqlException e) {
            log.warn("Rollback attempt failed", e);
        } finally {
            clearState();
        }
    }

    public void cleanup() {
        try {
            this.sqlTransaction.commit();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SymmetricException(ex);
        } finally {
            this.sqlTransaction.close();
        }
    }

    @Override
    synchronized public void logStats(Logger log, long totalTimeInMs) {
        super.logStats(log, totalTimeInMs);
        if (log.isDebugEnabled()) {
            log.debug(channel.getChannelId() + ", startDataId=" + startDataId + ", endDataId=" + endDataId +
                    ", lastDataId=" + lastDataId + ", dataReadCount=" + dataReadCount + ", peekAheadFillCount=" + peekAheadFillCount +
                    ", dataGaps=" + dataGaps.size());
        }
    }

    public void setNeedsCommitted(boolean b) {
        this.needsCommitted = b;
    }

    public boolean isNeedsCommitted() {
        return needsCommitted;
    }

    public Set<IDataRouter> getUsedDataRouters() {
        return usedDataRouters;
    }

    public void addUsedDataRouter(IDataRouter dataRouter) {
        this.usedDataRouters.add(dataRouter);
    }

    public Map<String, Long> getTimesByRouter() {
        return timesByRouter;
    }

    public void addTimesByRouter(String routerId, long millis) {
        Long totalMillis = timesByRouter.get(routerId);
        if (totalMillis == null) {
            timesByRouter.put(routerId, millis);
        } else {
            timesByRouter.put(routerId, totalMillis + millis);
        }
    }

    public void resetForNextData() {
        this.needsCommitted = false;
    }

    public long getCreatedTimeInMs() {
        return createdTimeInMs;
    }

    public void setLastDataProcessed(Data lastDataProcessed) {
        this.lastDataProcessed = lastDataProcessed;
    }

    public Data getLastDataProcessed() {
        return lastDataProcessed;
    }

    public ISqlTransaction getSqlTransaction() {
        return sqlTransaction;
    }

    public void setProduceCommonBatches(boolean defaultRoutersOnly) {
        this.produceCommonBatches = defaultRoutersOnly;
    }

    public boolean isProduceCommonBatches() {
        return produceCommonBatches;
    }

    public boolean isNonCommonForIncoming() {
        return nonCommonForIncoming;
    }

    public void setNonCommonForIncoming(boolean nonCommonForIncoming) {
        this.nonCommonForIncoming = nonCommonForIncoming;
    }

    public boolean isForceNonCommon() {
        return forceNonCommon;
    }

    public void setForceNonCommon(boolean forceNonCommon) {
        this.forceNonCommon = forceNonCommon;
    }

    public void setProduceGroupBatches(boolean produceGroupBatches) {
        this.produceGroupBatches = produceGroupBatches;
    }

    public boolean isProduceGroupBatches() {
        return produceGroupBatches;
    }

    public void setLastLoadId(long lastLoadId) {
        this.lastLoadId = lastLoadId;
    }

    public long getLastLoadId() {
        return lastLoadId;
    }

    public long getStartDataId() {
        return startDataId;
    }

    public void setStartDataId(long startDataId) {
        this.startDataId = startDataId;
    }

    public long getEndDataId() {
        return endDataId;
    }

    public void setEndDataId(long endDataId) {
        this.endDataId = endDataId;
    }

    public long getDataReadCount() {
        return dataReadCount;
    }

    public void incrementDataReadCount(long dataReadCount) {
        this.dataReadCount += dataReadCount;
    }

    public long getDataRereadCount() {
        return dataRereadCount;
    }

    public void incrementDataRereadCount() {
        this.dataRereadCount++;
    }

    public long getPeekAheadFillCount() {
        return peekAheadFillCount;
    }

    public long getMaxPeekAheadQueueSize() {
        return maxPeekAheadQueueSize;
    }

    public void setMaxPeekAheadQueueSize(long maxPeekAheadQueueSize) {
        this.maxPeekAheadQueueSize = maxPeekAheadQueueSize;
    }

    public void incrementPeekAheadFillCount(long peekAheadFillCount) {
        this.peekAheadFillCount += peekAheadFillCount;
    }

    public List<DataGap> getDataGaps() {
        return dataGaps;
    }

    public void setDataGaps(List<DataGap> dataGaps) {
        this.dataGaps = dataGaps;
    }

    public void setOnlyDefaultRoutersAssigned(boolean onlyDefaultRoutersAssigned) {
        this.onlyDefaultRoutersAssigned = onlyDefaultRoutersAssigned;
    }

    public boolean isOnlyDefaultRoutersAssigned() {
        return onlyDefaultRoutersAssigned;
    }

    public List<Long> getDataIds() {
        return dataIds;
    }

    public boolean isOverrideContainsBigLob() {
        return overrideContainsBigLob;
    }

    public void setOverrideContainsBigLob(boolean overrideContainsBigLob) {
        this.overrideContainsBigLob = overrideContainsBigLob;
    }

    public boolean isBatchComplete(OutgoingBatch batch, DataMetaData dataMetaData) {
        return batchAlgorithm.isBatchComplete(batch, dataMetaData, this);
    }

    public int getMaxBatchesJdbcFlushSize() {
        return maxBatchesJdbcFlushSize;
    }

    public void setMaxBatchesJdbcFlushSize(int maxBatchesJdbcFlushSize) {
        this.maxBatchesJdbcFlushSize = maxBatchesJdbcFlushSize;
    }
}
