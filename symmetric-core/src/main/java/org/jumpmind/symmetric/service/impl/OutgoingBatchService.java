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

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.mapper.LongMapper;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.ext.IOutgoingBatchFilter;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.BacklogSummary;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroupChannelWindow;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.NodeHost;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.OutgoingBatchSummary;
import org.jumpmind.symmetric.model.OutgoingBatchSummaryByNodeBriefStats;
import org.jumpmind.symmetric.model.OutgoingBatches;
import org.jumpmind.symmetric.model.ReadyChannels;
import org.jumpmind.symmetric.service.FilterCriterion;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.util.QueueThread;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.FormatUtils;

public class OutgoingBatchService extends AbstractService implements IOutgoingBatchService {
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private ISequenceService sequenceService;
    private IClusterService clusterService;
    private IExtensionService extensionService;
    private ICacheManager cacheManager;
    private IParameterService parameterService;

    public OutgoingBatchService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.parameterService = engine.getParameterService();
        this.nodeService = engine.getNodeService();
        this.configurationService = engine.getConfigurationService();
        this.sequenceService = engine.getSequenceService();
        this.clusterService = engine.getClusterService();
        this.extensionService = engine.getExtensionService();
        this.cacheManager = engine.getCacheManager();
        setSqlMap(new OutgoingBatchServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    public void updateOutgoingError(long batchId, String nodeId) {
        sqlTemplate.update(getSql("updateOutgoingError"), batchId, nodeId);
    }

    @Override
    public int cancelLoadBatches(long loadId) {
        return sqlTemplate.update(getSql("cancelLoadBatchesSql"), new Date(), loadId);
    }

    public void markAllAsSentForNode(String nodeId, boolean includeConfigChannel) {
        OutgoingBatches batches = null;
        int configCount;
        do {
            configCount = 0;
            batches = getOutgoingBatches(nodeId, true);
            List<OutgoingBatch> list = batches.getBatches();
            /*
             * Sort in reverse order so we don't get fk errors for batches that are currently processing. We don't make the update transactional to prevent
             * contention in highly loaded systems
             */
            Collections.sort(list, new Comparator<OutgoingBatch>() {
                public int compare(OutgoingBatch o1, OutgoingBatch o2) {
                    return -Long.valueOf(o1.getBatchId()).compareTo(o2.getBatchId());
                }
            });
            for (OutgoingBatch outgoingBatch : list) {
                if (includeConfigChannel || (!outgoingBatch.getChannelId().equals(Constants.CHANNEL_CONFIG) &&
                        !outgoingBatch.getChannelId().equals(Constants.CHANNEL_MONITOR) &&
                        !outgoingBatch.getChannelId().equals(Constants.CHANNEL_SYSTEM))) {
                    outgoingBatch.setStatus(Status.OK);
                    outgoingBatch.setErrorFlag(false);
                    updateOutgoingBatch(outgoingBatch);
                } else {
                    configCount++;
                }
            }
        } while (batches.getBatches().size() > configCount);
    }

    public void markAllConfigAsSentForNode(String nodeId) {
        int updateCount;
        do {
            updateCount = 0;
            OutgoingBatches batches = getOutgoingBatches(nodeId, false);
            List<OutgoingBatch> list = batches.getBatches();
            for (OutgoingBatch outgoingBatch : list) {
                if (outgoingBatch.getChannelId().equals(Constants.CHANNEL_CONFIG) || outgoingBatch.getChannelId().equals(Constants.CHANNEL_SYSTEM)) {
                    outgoingBatch.setStatus(Status.OK);
                    outgoingBatch.setErrorFlag(false);
                    outgoingBatch.setIgnoreCount(1);
                    updateOutgoingBatch(outgoingBatch);
                    updateCount++;
                }
            }
        } while (updateCount > 0);
    }

    public void copyOutgoingBatches(String channelId, long startBatchId, String fromNodeId, String toNodeId) {
        log.info("Copying outgoing batches for channel '{}' from node '{}' to node '{}' starting at {}",
                new Object[] { channelId, fromNodeId, toNodeId, startBatchId });
        sqlTemplate.update(getSql("deleteOutgoingBatchesForNodeSql"), toNodeId, channelId, fromNodeId, channelId);
        int count = sqlTemplate.update(getSql("copyOutgoingBatchesSql"), toNodeId, new Date(), fromNodeId, channelId, startBatchId);
        log.info("Copied {} outgoing batches for channel '{}' from node '{}' to node '{}'",
                new Object[] { count, channelId, fromNodeId, toNodeId });
    }

    public void updateAbandonedRoutingBatches() {
        int count = sqlTemplate.queryForInt(getSql("countOutgoingBatchesWithStatusSql"), Status.RT.name());
        if (count > 0) {
            log.info("Cleaning up {} batches that were abandoned by a failed or aborted attempt at routing", count);
            sqlTemplate.update(getSql("updateOutgoingBatchesStatusSql"), Status.OK.name(), Status.RT.name());
        }
    }

    public void updateOutgoingBatches(List<OutgoingBatch> outgoingBatches) {
        for (OutgoingBatch batch : outgoingBatches) {
            updateOutgoingBatch(batch);
        }
    }

    public void updateOutgoingBatch(OutgoingBatch outgoingBatch) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            updateOutgoingBatch(transaction, outgoingBatch);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    public void updateCommonBatchExtractStatistics(OutgoingBatch outgoingBatch) {
        sqlTemplate.update(getSql("updateCommonBatchExtractStatsSql"),
                new Object[] { outgoingBatch.getByteCount(), outgoingBatch.getDataRowCount(), outgoingBatch.getDataInsertRowCount(), outgoingBatch
                        .getDataUpdateRowCount(),
                        outgoingBatch.getDataDeleteRowCount(), outgoingBatch.getOtherRowCount(), outgoingBatch.getExtractRowCount(), outgoingBatch
                                .getExtractInsertRowCount(),
                        outgoingBatch.getExtractUpdateRowCount(), outgoingBatch.getExtractDeleteRowCount(), outgoingBatch.getBatchId(),
                        outgoingBatch.getNodeId() },
                new int[] { Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                        Types.NUMERIC,
                        Types.NUMERIC, symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
    }

    public void updateOutgoingBatch(ISqlTransaction transaction, OutgoingBatch outgoingBatch) {
        outgoingBatch.setLastUpdatedTime(new Date());
        outgoingBatch.setLastUpdatedHostName(clusterService.getServerId());
        String sql = getSql("updateOutgoingBatchSql");
        if (outgoingBatch.getStatus() != Status.OK) {
            sql += getSql("statusNotOk");
        }
        transaction.prepareAndExecute(sql,
                new Object[] { outgoingBatch.getStatus().name(), outgoingBatch.getLoadId(), outgoingBatch.isExtractJobFlag() ? 1 : 0,
                        outgoingBatch.isLoadFlag() ? 1 : 0, outgoingBatch.isErrorFlag() ? 1 : 0, outgoingBatch.getByteCount(),
                        outgoingBatch.getExtractCount(), outgoingBatch.getSentCount(), outgoingBatch.getLoadCount(),
                        outgoingBatch.getDataRowCount(), outgoingBatch.getReloadRowCount(), outgoingBatch.getDataInsertRowCount(),
                        outgoingBatch.getDataUpdateRowCount(), outgoingBatch.getDataDeleteRowCount(), outgoingBatch.getOtherRowCount(),
                        outgoingBatch.getIgnoreCount(), outgoingBatch.getRouterMillis(), outgoingBatch.getNetworkMillis(),
                        outgoingBatch.getFilterMillis(), outgoingBatch.getLoadMillis(), outgoingBatch.getExtractMillis(),
                        outgoingBatch.getExtractStartTime(), outgoingBatch.getTransferStartTime(), outgoingBatch.getLoadStartTime(),
                        outgoingBatch.getSqlState(), outgoingBatch.getSqlCode(), FormatUtils.abbreviateForLogging(outgoingBatch.getSqlMessage()),
                        outgoingBatch.getFailedDataId(), outgoingBatch.getFailedLineNumber(),
                        outgoingBatch.getLastUpdatedHostName(), new Date(), outgoingBatch.getSummary(), outgoingBatch.getLoadRowCount(),
                        outgoingBatch.getLoadInsertRowCount(), outgoingBatch.getLoadUpdateRowCount(), outgoingBatch.getLoadDeleteRowCount(),
                        outgoingBatch.getFallbackInsertCount(), outgoingBatch.getFallbackUpdateCount(), outgoingBatch.getConflictWinCount(),
                        outgoingBatch.getConflictLoseCount(), outgoingBatch.getIgnoreRowCount(), outgoingBatch.getMissingDeleteCount(),
                        outgoingBatch.getSkipCount(), outgoingBatch.getExtractRowCount(), outgoingBatch.getExtractInsertRowCount(),
                        outgoingBatch.getExtractUpdateRowCount(), outgoingBatch.getExtractDeleteRowCount(),
                        outgoingBatch.getTransformExtractMillis(), outgoingBatch.getTransformLoadMillis(), outgoingBatch.isBulkLoaderFlag() ? 1 : 0,
                        outgoingBatch.getDataMinCreateTime(), outgoingBatch.getDataMaxCreateTime(), outgoingBatch.getBatchId(), outgoingBatch.getNodeId() },
                new int[] { Types.CHAR, symmetricDialect.getSqlTypeForIds(), Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                        Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                        Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                        Types.TIMESTAMP, Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR, Types.NUMERIC, Types.VARCHAR, symmetricDialect.getSqlTypeForIds(),
                        Types.NUMERIC, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                        Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                        Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.TIMESTAMP, Types.TIMESTAMP,
                        symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
    }

    public void updateOutgoingBatches(ISqlTransaction transaction, List<OutgoingBatch> batches, int flushSize) {
        int[] types = new int[] { Types.CHAR, symmetricDialect.getSqlTypeForIds(), Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                Types.TIMESTAMP, Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR, Types.NUMERIC, Types.VARCHAR, symmetricDialect.getSqlTypeForIds(),
                Types.NUMERIC, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.TIMESTAMP, Types.TIMESTAMP,
                symmetricDialect.getSqlTypeForIds(), Types.VARCHAR };
        int count = 0;
        transaction.prepare(getSql("updateOutgoingBatchSql"));
        for (OutgoingBatch outgoingBatch : batches) {
            outgoingBatch.setLastUpdatedTime(new Date());
            outgoingBatch.setLastUpdatedHostName(clusterService.getServerId());
            transaction.addRow(getSql("updateOutgoingBatchSql"),
                    new Object[] { outgoingBatch.getStatus().name(), outgoingBatch.getLoadId(), outgoingBatch.isExtractJobFlag() ? 1 : 0,
                            outgoingBatch.isLoadFlag() ? 1 : 0, outgoingBatch.isErrorFlag() ? 1 : 0, outgoingBatch.getByteCount(),
                            outgoingBatch.getExtractCount(), outgoingBatch.getSentCount(), outgoingBatch.getLoadCount(),
                            outgoingBatch.getDataRowCount(), outgoingBatch.getReloadRowCount(), outgoingBatch.getDataInsertRowCount(),
                            outgoingBatch.getDataUpdateRowCount(), outgoingBatch.getDataDeleteRowCount(), outgoingBatch.getOtherRowCount(),
                            outgoingBatch.getIgnoreCount(), outgoingBatch.getRouterMillis(), outgoingBatch.getNetworkMillis(),
                            outgoingBatch.getFilterMillis(), outgoingBatch.getLoadMillis(), outgoingBatch.getExtractMillis(),
                            outgoingBatch.getExtractStartTime(), outgoingBatch.getTransferStartTime(), outgoingBatch.getLoadStartTime(),
                            outgoingBatch.getSqlState(), outgoingBatch.getSqlCode(), FormatUtils.abbreviateForLogging(outgoingBatch.getSqlMessage()),
                            outgoingBatch.getFailedDataId(), outgoingBatch.getFailedLineNumber(),
                            outgoingBatch.getLastUpdatedHostName(), new Date(), outgoingBatch.getSummary(), outgoingBatch.getLoadRowCount(),
                            outgoingBatch.getLoadInsertRowCount(), outgoingBatch.getLoadUpdateRowCount(), outgoingBatch.getLoadDeleteRowCount(),
                            outgoingBatch.getFallbackInsertCount(), outgoingBatch.getFallbackUpdateCount(), outgoingBatch.getConflictWinCount(),
                            outgoingBatch.getConflictLoseCount(), outgoingBatch.getIgnoreRowCount(), outgoingBatch.getMissingDeleteCount(),
                            outgoingBatch.getSkipCount(), outgoingBatch.getExtractRowCount(), outgoingBatch.getExtractInsertRowCount(),
                            outgoingBatch.getExtractUpdateRowCount(), outgoingBatch.getExtractDeleteRowCount(),
                            outgoingBatch.getTransformExtractMillis(), outgoingBatch.getTransformLoadMillis(), outgoingBatch.isBulkLoaderFlag() ? 1 : 0,
                            outgoingBatch.getDataMinCreateTime(), outgoingBatch.getDataMaxCreateTime(),
                            outgoingBatch.getBatchId(), outgoingBatch.getNodeId() }, types);
            if (++count >= flushSize) {
                transaction.flush();
                count = 0;
            }
        }
        transaction.flush();
    }

    public void updateOutgoingBatchStatus(ISqlTransaction transaction, Status status, String nodeId, long startBatchId, long endBatchId) {
        transaction.prepareAndExecute(getSql("updateOutgoingBatchStatusSql"),
                new Object[] { status.name(), new Date(), clusterService.getServerId(), nodeId, startBatchId, endBatchId },
                new int[] { Types.CHAR, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR,
                        symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() });
    }

    public void updateOutgoingSetupBatchStatusByStatus(ISqlTransaction transaction, String targetNodeId, long loadId,
            long maxBatchId, String fromStatus, String toStatus) {
        transaction.prepareAndExecute(getSql("updateOutgoingSetupBatchStatusByStatus"),
                new Object[] { toStatus, new Date(), clusterService.getServerId(),
                        targetNodeId, loadId, fromStatus, maxBatchId },
                new int[] { Types.CHAR, Types.TIMESTAMP, Types.VARCHAR,
                        Types.VARCHAR, Types.NUMERIC, Types.CHAR, Types.NUMERIC });
    }

    public void updateOutgoingLoadBatchStatusByStatus(ISqlTransaction transaction, String targetNodeId, long loadId,
            long startDataBatchId, long endDataBatchId, String fromStatus, String toStatus) {
        transaction.prepareAndExecute(getSql("updateOutgoingLoadBatchStatusByStatus"),
                new Object[] { toStatus, new Date(), clusterService.getServerId(),
                        targetNodeId, loadId, fromStatus, startDataBatchId, endDataBatchId },
                new int[] { Types.CHAR, Types.TIMESTAMP, Types.VARCHAR,
                        Types.VARCHAR, Types.NUMERIC, Types.CHAR, Types.NUMERIC, Types.NUMERIC });
    }

    public void updateOutgoingFinalizeBatchStatusByStatus(ISqlTransaction transaction, String targetNodeId, long loadId,
            long minBatchId, String fromStatus, String toStatus) {
        transaction.prepareAndExecute(getSql("updateOutgoingFinalizeBatchStatusByStatus"),
                new Object[] { toStatus, new Date(), clusterService.getServerId(),
                        targetNodeId, loadId, fromStatus, minBatchId },
                new int[] { Types.CHAR, Types.TIMESTAMP, Types.VARCHAR,
                        Types.VARCHAR, Types.NUMERIC, Types.CHAR, Types.NUMERIC });
    }

    public void insertOutgoingBatch(final OutgoingBatch outgoingBatch) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            insertOutgoingBatch(transaction, outgoingBatch);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    public void insertOutgoingBatch(ISqlTransaction transaction, OutgoingBatch outgoingBatch) {
        outgoingBatch.setLastUpdatedHostName(clusterService.getServerId());
        long batchId = outgoingBatch.getBatchId();
        if (batchId <= 0) {
            if (platform.supportsMultiThreadedTransactions()) {
                batchId = sequenceService.nextVal(Constants.SEQUENCE_OUTGOING_BATCH);
            } else {
                batchId = sequenceService.nextVal(transaction, Constants.SEQUENCE_OUTGOING_BATCH);
            }
        }
        transaction.prepareAndExecute(getSql("insertOutgoingBatchSql"), batchId, outgoingBatch.getNodeId(), outgoingBatch.getChannelId(),
                outgoingBatch.getStatus().name(), outgoingBatch.getLoadId(), outgoingBatch.isExtractJobFlag() ? 1 : 0,
                outgoingBatch.isLoadFlag() ? 1 : 0, outgoingBatch.isCommonFlag() ? 1 : 0, outgoingBatch.getReloadRowCount(),
                outgoingBatch.getOtherRowCount(), outgoingBatch.getDataUpdateRowCount(), outgoingBatch.getDataInsertRowCount(),
                outgoingBatch.getDataDeleteRowCount(), outgoingBatch.getLastUpdatedHostName(), new Date(), new Date(),
                outgoingBatch.getCreateBy(), outgoingBatch.getSummary(), outgoingBatch.getDataRowCount(),
                outgoingBatch.getDataMinCreateTime(), outgoingBatch.getDataMaxCreateTime());
        outgoingBatch.setBatchId(batchId);
    }

    public void insertOutgoingBatches(ISqlTransaction transaction, List<OutgoingBatch> batches, int flushSize, boolean isCommon) {
        long batchId = 0;
        int count = 0;
        int size = isCommon ? 1 : batches.size();
        if (platform.supportsMultiThreadedTransactions()) {
            batchId = sequenceService.nextRange(Constants.SEQUENCE_OUTGOING_BATCH, size);
        } else {
            batchId = sequenceService.nextRange(transaction, Constants.SEQUENCE_OUTGOING_BATCH, size);
        }
        transaction.prepare(getSql("insertOutgoingBatchSql"));
        for (OutgoingBatch batch : batches) {
            batch.setLastUpdatedHostName(clusterService.getServerId());
            batch.setBatchId(batchId);
            transaction.addRow(batch, new Object[] { batch.getBatchId(), batch.getNodeId(), batch.getChannelId(), batch.getStatus().name(),
                    batch.getLoadId(), batch.isExtractJobFlag() ? 1 : 0, batch.isLoadFlag() ? 1 : 0, batch.isCommonFlag() ? 1 : 0,
                    batch.getReloadRowCount(), batch.getOtherRowCount(), batch.getDataUpdateRowCount(), batch.getDataInsertRowCount(),
                    batch.getDataDeleteRowCount(), batch.getLastUpdatedHostName(), new Date(), new Date(), batch.getCreateBy(),
                    batch.getSummary(), batch.getDataRowCount(), batch.getDataMinCreateTime(), batch.getDataMaxCreateTime() },
                    new int[] { symmetricDialect.getSqlTypeForIds(), Types.VARCHAR, Types.VARCHAR, Types.CHAR, Types.NUMERIC,
                            Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                            Types.NUMERIC, Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC,
                            Types.TIMESTAMP, Types.TIMESTAMP });
            if (!isCommon) {
                batchId++;
            }
            if (++count >= flushSize) {
                transaction.flush();
                count = 0;
            }
        }
        transaction.flush();
    }

    public OutgoingBatch findOutgoingBatch(long batchId, String nodeId) {
        List<OutgoingBatch> list = null;
        if (StringUtils.isNotBlank(nodeId)) {
            list = (List<OutgoingBatch>) sqlTemplateDirty.query(getSql("selectOutgoingBatchPrefixSql", "findOutgoingBatchSql"),
                    new OutgoingBatchMapper(true), new Object[] { batchId, nodeId },
                    new int[] { symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
        } else {
            /*
             * Pushing to an older version of symmetric might result in a batch without the node id
             */
            list = (List<OutgoingBatch>) sqlTemplateDirty.query(getSql("selectOutgoingBatchPrefixSql", "findOutgoingBatchByIdOnlySql"),
                    new OutgoingBatchMapper(true), new Object[] { batchId }, new int[] { symmetricDialect.getSqlTypeForIds() });
        }
        if (list != null && list.size() > 0) {
            return list.get(0);
        } else {
            return null;
        }
    }

    @Override
    public OutgoingBatch findOutgoingBatchFirstCommon(long batchId) {
        List<OutgoingBatch> list = (List<OutgoingBatch>) sqlTemplateDirty.query(getSql("selectOutgoingBatchPrefixSql", "findOutgoingBatchFirstCommonSql"),
                new OutgoingBatchMapper(true), new Object[] { batchId }, new int[] { symmetricDialect.getSqlTypeForIds() });
        OutgoingBatch batch = null;
        if (!list.isEmpty()) {
            batch = list.get(0);
        }
        return batch;
    }

    public int countOutgoingBatchesInError() {
        return sqlTemplateDirty.queryForInt(getSql("countOutgoingBatchesErrorsSql"));
    }

    public int countOutgoingBatchesInError(String channelId) {
        return sqlTemplateDirty.queryForInt(getSql("countOutgoingBatchesErrorsOnChannelSql"), channelId);
    }

    @Override
    public Date getOutgoingBatchesLatestUpdateSql() {
        return sqlTemplateDirty.queryForObject(getSql("getOutgoingBatchesLatestUpdateSql"), Date.class);
    }

    public int countOutgoingBatchesUnsent() {
        return sqlTemplateDirty.queryForInt(getSql("countOutgoingBatchesUnsentSql"));
    }

    @Override
    public List<OutgoingBatchSummaryByNodeBriefStats> findOutgoingBatchSummaryByNodeBriefStats() {
        return sqlTemplateDirty.query(getSql("selectOutgoingBatchSummaryByNodeBriefStatsSql"),
                new OutgoingBatchSummaryByNodeBriefStatsMapper());
    }

    public int countOutgoingBatchesUnsentOfflineNodes(int minutesBeforeOffline) {
        int unsentBatchCount = 0;
        if (minutesBeforeOffline < 0) {
            return unsentBatchCount;
        }
        for (String offlineNodeId : nodeService.findOfflineNodeIds(minutesBeforeOffline)) {
            unsentBatchCount += countUnsentBatchesByTargetNode(offlineNodeId, true);
        }
        return unsentBatchCount;
    }

    public int[] countOutgoingNonSystemBatchesRowsUnsent() {
        int[] batchesRows = new int[2];
        for (Row row : sqlTemplateDirty.query(getSql("countOutgoingNonSystemBatchesUnsentSql"))) {
            batchesRows[0] = row.getInt("batch_count");
            batchesRows[1] = row.getInt("rows_count");
        }
        return batchesRows;
    }

    @Override
    public int countOutgoingBatchesUnsent(String channelId) {
        return sqlTemplateDirty.queryForInt(getSql("countOutgoingBatchesUnsentOnChannelSql"), channelId);
    }

    @Override
    public int countOutgoingBatchesUnsentHeartbeat() {
        return sqlTemplateDirty.queryForInt(getSql("countOutgoingBatchesUnsentHeartbeat"));
    }

    @Override
    public Map<String, Integer> countOutgoingBatchesPendingByChannel(String nodeId) {
        List<Row> rows = sqlTemplateDirty.query(getSql("countOutgoingBatchesByChannelSql"), new Object[] { nodeId });
        Map<String, Integer> results = new HashMap<String, Integer>();
        if (rows != null && !rows.isEmpty()) {
            for (Row row : rows) {
                results.put(row.getString("channel_id"), row.getInt("batch_count"));
            }
        }
        Set<String> channelIds = configurationService.getChannels(false).keySet();
        for (String channelId : channelIds) {
            if (!results.containsKey(channelId) && !Constants.CHANNEL_HEARTBEAT.equals(channelId)) {
                results.put(channelId, 0);
            }
        }
        return results;
    }

    @Override
    public int countUnsentBatchesByTargetNode(String nodeId, boolean includeHeartbeats) {
        if (includeHeartbeats) {
            return sqlTemplateDirty.queryForInt(getSql("countOutgoingBatchesByTargetNodeSql"), new Object[] { nodeId });
        }
        return sqlTemplateDirty.queryForInt(getSql("countOutgoingBatchesByTargetNodeExcludingHeartbeatsSql"), new Object[] { nodeId });
    }

    @Override
    public long countUnsentRowsByTargetNode(String nodeId) {
        return sqlTemplateDirty.queryForLong(getSql("countOutgoingRowsByTargetNodeSql"), new Object[] { nodeId });
    }

    @Override
    public Map<String, Long> countUnsentBatchesBlocked() {
        Map<String, Long> result = new HashMap<String, Long>();
        List<Row> rows = sqlTemplateDirty.query(getSql("countUnsentBatchesBlocked"));
        if (!rows.isEmpty()) {
            Row row = rows.get(0);
            for (String key : row.keySet()) {
                Long count = row.getLong(key);
                result.put(key.toLowerCase(), count == null ? 0 : count);
            }
        }
        return result;
    }

    @Override
    public int countOutgoingBatches(List<String> nodeIds, List<String> channels,
            List<OutgoingBatch.Status> statuses, List<Long> loads) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("NODES", nodeIds);
        params.put("CHANNELS", channels);
        params.put("STATUSES", toStringList(statuses));
        return sqlTemplateDirty.queryForInt(getSql("selectCountBatchesPrefixSql", buildBatchWhere(nodeIds, channels, statuses, loads, null)),
                params);
    }

    public List<OutgoingBatch> listOutgoingBatches(List<String> nodeIds, List<String> channels,
            List<OutgoingBatch.Status> statuses, List<Long> loads, long startAtBatchId, Date startAtLastUpdateTime,
            final int maxRowsToRetrieve, boolean ascending) {
        String where = buildBatchWhere(nodeIds, channels, statuses, loads, startAtLastUpdateTime);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("NODES", nodeIds);
        params.put("CHANNELS", channels);
        params.put("STATUSES", toStringList(statuses));
        params.put("LAST_UPDATE_TIME", startAtLastUpdateTime);
        params.put("LOADS", loads);
        String startAtBatchIdSql = null;
        if (startAtBatchId > 0) {
            if (StringUtils.isBlank(where)) {
                where = " where 1=1 ";
            }
            params.put("BATCH_ID", startAtBatchId);
            startAtBatchIdSql = " and batch_id = :BATCH_ID ";
        }
        String sql = getSql("selectOutgoingBatchPrefixSql", where, startAtBatchIdSql,
                ascending ? " order by batch_id asc" : " order by batch_id desc");
        return sqlTemplateDirty.query(sql, maxRowsToRetrieve, new OutgoingBatchMapper(true), params);
    }

    public List<OutgoingBatch> listOutgoingBatchesWithLimit(int offset, int limit, List<FilterCriterion> filter,
            String orderColumn, String orderDirection) {
        String where = filter != null ? buildBatchWhereFromFilter(filter) : null;
        Map<String, Object> params = filter != null ? buildBatchParams(filter) : new HashMap<String, Object>();
        String orderBy = buildBatchOrderBy(orderColumn, orderDirection);
        String sql = getSql("selectOutgoingBatchPrefixSql", where, orderBy);
        List<OutgoingBatch> batchList;
        if (platform.supportsLimitOffset()) {
            sql = platform.massageForLimitOffset(sql, limit, offset);
            batchList = sqlTemplateDirty.query(sql, Integer.MAX_VALUE, new OutgoingBatchMapper(true), params);
        } else {
            ISqlReadCursor<OutgoingBatch> cursor = sqlTemplateDirty.queryForCursor(sql, new OutgoingBatchMapper(true), params);
            try {
                OutgoingBatch next = null;
                batchList = new ArrayList<OutgoingBatch>();
                int rowCount = 0;
                do {
                    next = cursor.next();
                    if (next != null) {
                        if (offset <= rowCount && rowCount < limit + offset) {
                            batchList.add(next);
                        }
                        rowCount++;
                    }
                    if (rowCount >= limit + offset) {
                        break;
                    }
                } while (next != null);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        int maxBatches = parameterService.getInt("batch.screen.max.to.select");
        int batchesToReturn = maxBatches - offset;
        if (maxBatches > 0 && limit + offset > maxBatches && batchesToReturn < batchList.size() - 1) {
            batchList = batchList.subList(0, batchesToReturn);
        }
        return batchList;
    }

    public int countOutgoingBatchesWithLimit(List<FilterCriterion> filter) {
        String where = filter != null ? buildBatchWhereFromFilter(filter) : null;
        Map<String, Object> params = filter != null ? buildBatchParams(filter) : new HashMap<String, Object>();
        String sql = getSql("selectCountBatchesPrefixSql", where);
        int count = sqlTemplateDirty.queryForInt(sql, params);
        int maxBatches = parameterService.getInt("batch.screen.max.to.select");
        return maxBatches > 0 ? Math.min(count, maxBatches) : count;
    }

    protected List<String> toStringList(List<OutgoingBatch.Status> statuses) {
        List<String> statusStrings = new ArrayList<String>(statuses.size());
        for (Status status : statuses) {
            statusStrings.add(status.name());
        }
        return statusStrings;
    }

    protected boolean containsOnlyStatus(OutgoingBatch.Status status, List<OutgoingBatch.Status> statuses) {
        return statuses.size() == 1 && statuses.get(0) == status;
    }

    /**
     * Select batches to process. Batches that are NOT in error will be returned first. They will be ordered by batch id as the batches will have already been
     * created by {@link #buildOutgoingBatches(String)} in channel priority order.
     */
    public OutgoingBatches getOutgoingBatches(String nodeId, boolean includeDisabledChannels) {
        return getOutgoingBatches(nodeId, null, includeDisabledChannels);
    }

    public OutgoingBatches getOutgoingBatches(String nodeId, String channelThread, boolean includeDisabledChannels) {
        return getOutgoingBatches(nodeId, channelThread, null, null, includeDisabledChannels);
    }

    @Override
    public OutgoingBatches getOutgoingBatches(String nodeId, String channelThread, NodeGroupLinkAction eventAction,
            NodeGroupLinkAction defaultEventAction, boolean includeDisabledChannels) {
        long ts = System.currentTimeMillis();
        final int maxNumberOfBatchesToSelect = parameterService.getInt(ParameterConstants.OUTGOING_BATCH_MAX_BATCHES_TO_SELECT, 1000);
        String sql = null;
        Object[] params = null;
        int[] types = null;
        QueueThread queueThread = new QueueThread(channelThread);
        channelThread = queueThread.getQueueName();
        if (channelThread != null && channelThread.equals(Constants.QUEUE_RELOAD) && queueThread.isUsingThreading()) {
            sql = getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchByThreadSql");
            params = new Object[] { nodeId, Constants.CHANNEL_RELOAD, OutgoingBatch.Status.RQ.name(), OutgoingBatch.Status.NE.name(),
                    OutgoingBatch.Status.QY.name(), OutgoingBatch.Status.SE.name(), OutgoingBatch.Status.LD.name(),
                    OutgoingBatch.Status.ER.name(), OutgoingBatch.Status.IG.name(), OutgoingBatch.Status.RS.name(), queueThread.getThreadId() };
            types = new int[] { Types.VARCHAR, Types.VARCHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR,
                    Types.CHAR, Types.CHAR, Types.INTEGER };
            log.debug("Querying outgoing batches on reload for thread {}", queueThread.getThreadId());
        } else if (eventAction != null) {
            if (eventAction.equals(defaultEventAction)) {
                sql = getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchChannelActionNullSql");
            } else {
                sql = getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchChannelActionSql");
            }
            params = new Object[] { eventAction.name(), nodeId, channelThread, OutgoingBatch.Status.RQ.name(), OutgoingBatch.Status.NE.name(),
                    OutgoingBatch.Status.QY.name(), OutgoingBatch.Status.SE.name(), OutgoingBatch.Status.LD.name(),
                    OutgoingBatch.Status.ER.name(), OutgoingBatch.Status.IG.name(), OutgoingBatch.Status.RS.name() };
            types = new int[] { Types.CHAR, Types.VARCHAR, Types.VARCHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR,
                    Types.CHAR, Types.CHAR, Types.CHAR };
        } else if (channelThread != null) {
            sql = getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchChannelSql");
            params = new Object[] { nodeId, channelThread, OutgoingBatch.Status.RQ.name(), OutgoingBatch.Status.NE.name(),
                    OutgoingBatch.Status.QY.name(), OutgoingBatch.Status.SE.name(), OutgoingBatch.Status.LD.name(),
                    OutgoingBatch.Status.ER.name(), OutgoingBatch.Status.IG.name(), OutgoingBatch.Status.RS.name() };
            types = new int[] { Types.VARCHAR, Types.VARCHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR,
                    Types.CHAR, Types.CHAR };
        } else {
            sql = getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchSql");
            params = new Object[] { nodeId, OutgoingBatch.Status.RQ.name(), OutgoingBatch.Status.NE.name(), OutgoingBatch.Status.QY.name(),
                    OutgoingBatch.Status.SE.name(), OutgoingBatch.Status.LD.name(), OutgoingBatch.Status.ER.name(),
                    OutgoingBatch.Status.IG.name(), OutgoingBatch.Status.RS.name() };
            types = new int[] { Types.VARCHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR, Types.CHAR,
                    Types.CHAR };
        }
        List<OutgoingBatch> list = (List<OutgoingBatch>) sqlTemplateDirty.query(sql, maxNumberOfBatchesToSelect,
                new OutgoingBatchMapper(includeDisabledChannels), params, types);
        int batchCount = list.size();
        if (batchCount == maxNumberOfBatchesToSelect) {
            log.warn("The {} parameter limited the number of outgoing batch rows to {}. "
                    + "This could prevent batches on a channel with an earlier processing_order from being processed.",
                    ParameterConstants.OUTGOING_BATCH_MAX_BATCHES_TO_SELECT, batchCount);
        }
        OutgoingBatches batches = new OutgoingBatches(list);
        List<NodeChannel> channels = new ArrayList<NodeChannel>(configurationService.getNodeChannels(nodeId, true));
        batches.sortChannels(channels);
        List<IOutgoingBatchFilter> filters = extensionService.getExtensionPointList(IOutgoingBatchFilter.class);
        List<OutgoingBatch> keepers = new ArrayList<OutgoingBatch>();
        for (NodeChannel channel : channels) {
            List<OutgoingBatch> batchesForChannel = getBatchesForChannelWindows(batches, nodeId, channel,
                    configurationService.getNodeGroupChannelWindows(parameterService.getNodeGroupId(), channel.getChannelId()));
            if (filters != null) {
                for (IOutgoingBatchFilter filter : filters) {
                    batchesForChannel = filter.filter(channel, batchesForChannel);
                }
            }
            if (parameterService.is(ParameterConstants.DATA_EXTRACTOR_ENABLED) || channel.getChannelId().equals(Constants.CHANNEL_CONFIG)
                    || channel.getChannelId().equals(Constants.CHANNEL_SYSTEM)) {
                keepers.addAll(batchesForChannel);
            }
        }
        batches.setBatches(keepers);
        long executeTimeInMs = System.currentTimeMillis() - ts;
        if (executeTimeInMs > Constants.LONG_OPERATION_THRESHOLD) {
            log.info("Selecting {} outgoing batch rows for node {} on queue '{}' took {} ms", batchCount, nodeId, channelThread,
                    executeTimeInMs);
        }
        return batches;
    }

    public List<OutgoingBatch> getBatchesForChannelWindows(OutgoingBatches batches, String targetNodeId, NodeChannel channel,
            List<NodeGroupChannelWindow> windows) {
        List<OutgoingBatch> keeping = new ArrayList<OutgoingBatch>();
        List<OutgoingBatch> current = batches.getBatches();
        if (current != null && current.size() > 0) {
            if (inTimeWindow(windows, targetNodeId)) {
                int maxBatchesToSend = channel.getMaxBatchToSend();
                for (OutgoingBatch outgoingBatch : current) {
                    if (channel.getChannelId().equals(outgoingBatch.getChannelId()) && maxBatchesToSend > 0) {
                        keeping.add(outgoingBatch);
                        maxBatchesToSend--;
                    }
                }
            }
        }
        return keeping;
    }

    /**
     * If {@link NodeGroupChannelWindow}s are defined for this channel, then check to see if the time (according to the offset passed in) is within on of the
     * configured windows.
     */
    public boolean inTimeWindow(List<NodeGroupChannelWindow> windows, String targetNodeId) {
        if (windows != null && windows.size() > 0) {
            for (NodeGroupChannelWindow window : windows) {
                String timezoneOffset = null;
                List<NodeHost> hosts = nodeService.findNodeHosts(targetNodeId);
                if (hosts.size() > 0) {
                    timezoneOffset = hosts.get(0).getTimezoneOffset();
                } else {
                    timezoneOffset = AppUtils.getTimezoneOffset();
                }
                if (window.inTimeWindow(timezoneOffset)) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    public OutgoingBatches getOutgoingBatchRange(String nodeId, Date startDate, Date endDate, String... channels) {
        OutgoingBatches batches = new OutgoingBatches();
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        for (String channel : channels) {
            batchList.addAll(sqlTemplate.query(getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchTimeRangeSql"),
                    new OutgoingBatchMapper(true), nodeId, channel, startDate, endDate));
        }
        batches.setBatches(batchList);
        return batches;
    }

    public OutgoingBatches getOutgoingBatchRange(long startBatchId, long endBatchId) {
        OutgoingBatches batches = new OutgoingBatches();
        batches.setBatches(sqlTemplate.query(getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchRangeSql"),
                new OutgoingBatchMapper(true), startBatchId, endBatchId));
        return batches;
    }

    public OutgoingBatches getOutgoingBatchByLoad(long loadId) {
        OutgoingBatches batches = new OutgoingBatches();
        batches.setBatches(sqlTemplate.query(getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchLoadSql"),
                new OutgoingBatchMapper(true), loadId));
        return batches;
    }

    public OutgoingBatches getOutgoingBatchByLoadRangeAndTable(long loadId, long startBatchId,
            long endBatchId, String tableName) {
        OutgoingBatches batches = new OutgoingBatches();
        batches.setBatches(sqlTemplate.query(getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchLoadByBatchRangeByTableNameSql"),
                new OutgoingBatchMapper(true), loadId, startBatchId, endBatchId, tableName));
        return batches;
    }

    public OutgoingBatches getOutgoingBatchErrors(int maxRows) {
        OutgoingBatches batches = new OutgoingBatches();
        batches.setBatches(sqlTemplateDirty.query(getSql("selectOutgoingBatchPrefixSql", "selectOutgoingBatchErrorsSql"), maxRows,
                new OutgoingBatchMapper(true), null, null));
        return batches;
    }

    public List<String> getNodesInError() {
        return sqlTemplate.query(getSql("selectNodesInErrorSql"), new StringMapper());
    }

    public List<OutgoingBatch> getNextOutgoingBatchForEachNode() {
        return sqlTemplateDirty.query(
                getSql("getNextOutgoingBatchForEachNodeSql"),
                new OutgoingBatchMapper(true, true));
    }

    public boolean isInitialLoadComplete(String nodeId) {
        return areAllLoadBatchesComplete(nodeId) && !isUnsentDataOnChannelForNode(Constants.CHANNEL_SYSTEM, nodeId);
    }

    public boolean areAllLoadBatchesComplete(String nodeId) {
        NodeSecurity security = nodeService.findNodeSecurity(nodeId);
        if (security == null || security.isInitialLoadEnabled()) {
            return false;
        }
        List<String> statuses = (List<String>) sqlTemplate.query(getSql("initialLoadStatusSql"), new StringMapper(), nodeId, 1);
        if (statuses == null || statuses.size() == 0) {
            throw new RuntimeException("The initial load has not been started for " + nodeId);
        }
        for (String status : statuses) {
            if (!Status.OK.name().equals(status)) {
                return false;
            }
        }
        return true;
    }

    public boolean isUnsentDataOnChannelForNode(String channelId, String nodeId) {
        int unsentCount = sqlTemplate.queryForInt(getSql("unsentBatchesForNodeIdChannelIdSql"), new Object[] { nodeId, channelId });
        if (unsentCount > 0) {
            return true;
        }
        // Do we need to check for unbatched data?
        return false;
    }

    protected StringBuilder buildStatusList(Object[] args, Status... statuses) {
        StringBuilder inList = new StringBuilder();
        for (int i = 0; i < statuses.length; i++) {
            args[i] = statuses[i].name();
            inList.append("?,");
        }
        return inList;
    }

    public List<OutgoingBatchSummary> findOutgoingBatchSummaryByNode(String nodeId,
            Date sinceCreateTime, Status... statuses) {
        Object[] args = new Object[statuses.length + 1];
        args[args.length - 1] = nodeId;
        StringBuilder inList = buildStatusList(args, statuses);
        String sql = getSql("selectOutgoingBatchSummaryPrefixSql",
                "selectOutgoingBatchSummaryStatsPrefixSql",
                "whereStatusAndNodeGroupByStatusSql").replace(":STATUS_LIST", inList.substring(0, inList.length() - 1));
        return sqlTemplateDirty.query(sql, new OutgoingBatchSummaryMapper(false, false), args);
    }

    public List<OutgoingBatchSummary> findOutgoingBatchSummary(Status... statuses) {
        Object[] args = new Object[statuses.length];
        StringBuilder inList = buildStatusList(args, statuses);
        String sql = getSql("selectOutgoingBatchSummaryByNodePrefixSql",
                "selectOutgoingBatchSummaryStatsPrefixSql",
                "whereStatusGroupByStatusAndNodeSql").replace(":STATUS_LIST", inList.substring(0, inList.length() - 1));
        return sqlTemplateDirty.query(sql, new OutgoingBatchSummaryMapper(true, false), args);
    }

    public List<OutgoingBatchSummary> findOutgoingBatchSummaryByChannel(Status... statuses) {
        Object[] args = new Object[statuses.length];
        StringBuilder inList = buildStatusList(args, statuses);
        String sql = getSql("selectOutgoingBatchSummaryByNodeAndChannelPrefixSql",
                "selectOutgoingBatchSummaryStatsPrefixSql",
                "whereStatusGroupByStatusAndNodeAndChannelSql").replace(":STATUS_LIST",
                        inList.substring(0, inList.length() - 1));
        return sqlTemplateDirty.query(sql, new OutgoingBatchSummaryMapper(true, true), args);
    }

    @Override
    public List<Long> getAllBatches() {
        return sqlTemplateDirty.query(getSql("getAllBatchesSql"), new LongMapper());
    }

    @Override
    public List<OutgoingBatch> getBatchesInProgress() {
        return sqlTemplateDirty.query(getSql("selectOutgoingBatchPrefixSql", "whereInProgressStatusSql"), new OutgoingBatchMapper(true),
                Status.ER.name(), Status.LD.name(), Status.QY.name(), Status.RS.name(), Status.SE.name());
    }

    @Override
    public Collection<String> getReadyQueues(String nodeId, boolean refreshCache) {
        Collection<String> queues = null;
        if (parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)) {
            queues = cacheManager.getReadyQueues(refreshCache).get(nodeId);
        }
        if (queues == null) {
            queues = new HashSet<>();
        }
        return queues;
    }

    @Override
    public Map<String, Collection<String>> getReadyQueues(boolean refreshCache) {
        Map<String, Collection<String>> readyQueuesMap = null;
        if (parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)) {
            readyQueuesMap = cacheManager.getReadyQueues(refreshCache);
        } else {
            readyQueuesMap = new HashMap<>();
        }
        return readyQueuesMap;
    }

    @Override
    public Map<String, ReadyChannels> getReadyChannelsFromDb() {
        List<Row> rows = sqlTemplateDirty.query(getSql("selectReadyChannels"), new Object[] {
                OutgoingBatch.Status.NE.name(), OutgoingBatch.Status.QY.name(), OutgoingBatch.Status.SE.name(), OutgoingBatch.Status.LD.name(),
                OutgoingBatch.Status.ER.name(), OutgoingBatch.Status.IG.name(), OutgoingBatch.Status.RS.name() });
        Map<String, ReadyChannels> readyChannelMap = new HashMap<>();
        for (Row row : rows) {
            String nodeId = row.getString("node_id");
            String channelId = row.getString("channel_id");
            Integer threadId = row.getInteger("thread_id");
            ReadyChannels channels = readyChannelMap.get(nodeId);
            if (channels == null) {
                channels = new ReadyChannels(nodeId);
                readyChannelMap.put(nodeId, channels);
            }
            channels.add(channelId, threadId);
        }
        return readyChannelMap;
    }

    @Override
    public BacklogSummary getBacklogSummaryByTargetNode(String nodeId) {
        return sqlTemplateDirty.queryForObject(getSql("selectBacklogByTargetNodeSql"), new BacklogSummaryMapper(), new Object[] { nodeId });
    }

    @Override
    public int countOutgoingBatchesInErrorByNode(String nodeId) {
        return sqlTemplateDirty.queryForInt(getSql("selectDataErrorCountByTargetNodeSql"), nodeId);
    }

    static class OutgoingBatchSummaryMapper implements ISqlRowMapper<OutgoingBatchSummary> {
        boolean withNode = false;
        boolean withChannel = false;

        public OutgoingBatchSummaryMapper(boolean withNode, boolean withChannel) {
            this.withNode = withNode;
            this.withChannel = withChannel;
        }

        public OutgoingBatchSummary mapRow(Row rs) {
            OutgoingBatchSummary summary = new OutgoingBatchSummary();
            if (withNode) {
                summary.setNodeId(rs.getString("node_id"));
            }
            if (withChannel) {
                summary.setChannel(rs.getString("channel_id"));
            }
            summary.setBatchCount(rs.getInt("batches"));
            summary.setDataCount(rs.getInt("data"));
            summary.setStatus(Status.valueOf(rs.getString("status")));
            summary.setOldestBatchCreateTime(rs.getDateTime("oldest_batch_time"));
            summary.setLastBatchUpdateTime(rs.getDateTime("last_update_time"));
            summary.setTotalBytes(rs.getLong("total_bytes"));
            summary.setTotalMillis(rs.getLong("total_millis"));
            summary.setErrorFlag(rs.getBoolean("error_flag"));
            summary.setMinBatchId(rs.getLong("batch_id"));
            summary.setInsertCount(rs.getInt("insert_event_count"));
            summary.setUpdateCount(rs.getInt("update_event_count"));
            summary.setDeleteCount(rs.getInt("delete_event_count"));
            summary.setOtherCount(rs.getInt("other_event_count"));
            summary.setOtherCount(rs.getInt("reload_event_count"));
            summary.setRouterMillis(rs.getLong("total_router_millis"));
            summary.setExtractMillis(rs.getLong("total_extract_millis"));
            summary.setTransferMillis(rs.getLong("total_network_millis"));
            summary.setLoadMillis(rs.getLong("total_load_millis"));
            return summary;
        }
    }

    class OutgoingBatchMapper implements ISqlRowMapper<OutgoingBatch> {
        private boolean statusOnly = false;
        private boolean includeDisabledChannels = false;
        private Map<String, Channel> channels;

        public OutgoingBatchMapper(boolean includeDisabledChannels, boolean statusOnly) {
            this.includeDisabledChannels = includeDisabledChannels;
            this.statusOnly = statusOnly;
            this.channels = configurationService.getChannels(false);
        }

        public OutgoingBatchMapper(boolean includeDisabledChannels) {
            this(includeDisabledChannels, false);
        }

        public OutgoingBatch mapRow(Row rs) {
            String channelId = rs.getString("channel_id");
            Channel channel = channels.get(channelId);
            if (channel != null && (includeDisabledChannels || channel.isEnabled())) {
                OutgoingBatch batch = new OutgoingBatch();
                batch.setNodeId(rs.getString("node_id"));
                batch.setStatusFromString(rs.getString("status"));
                batch.setBatchId(rs.getLong("batch_id"));
                if (!statusOnly) {
                    batch.setChannelId(channelId);
                    batch.setByteCount(rs.getLong("byte_count"));
                    batch.setExtractCount(rs.getLong("extract_count"));
                    batch.setSentCount(rs.getLong("sent_count"));
                    batch.setLoadCount(rs.getLong("load_count"));
                    batch.setDataRowCount(rs.getLong("data_row_count"));
                    batch.setLoadRowCount(rs.getLong("load_row_count"));
                    batch.setExtractRowCount(rs.getLong("extract_row_count"));
                    batch.setReloadRowCount(rs.getLong("reload_row_count"));
                    batch.setDataInsertRowCount(rs.getLong("data_insert_row_count"));
                    batch.setDataUpdateRowCount(rs.getLong("data_update_row_count"));
                    batch.setDataDeleteRowCount(rs.getLong("data_delete_row_count"));
                    batch.setLoadInsertRowCount(rs.getLong("load_insert_row_count"));
                    batch.setLoadUpdateRowCount(rs.getLong("load_update_row_count"));
                    batch.setLoadDeleteRowCount(rs.getLong("load_delete_row_count"));
                    batch.setExtractInsertRowCount(rs.getLong("extract_insert_row_count"));
                    batch.setExtractUpdateRowCount(rs.getLong("extract_update_row_count"));
                    batch.setExtractDeleteRowCount(rs.getLong("extract_delete_row_count"));
                    batch.setOtherRowCount(rs.getLong("other_row_count"));
                    batch.setIgnoreCount(rs.getLong("ignore_count"));
                    batch.setRouterMillis(rs.getLong("router_millis"));
                    batch.setNetworkMillis(rs.getLong("network_millis"));
                    batch.setFilterMillis(rs.getLong("filter_millis"));
                    batch.setLoadMillis(rs.getLong("load_millis"));
                    batch.setExtractMillis(rs.getLong("extract_millis"));
                    batch.setTransformExtractMillis(rs.getLong("transform_extract_millis"));
                    batch.setTransformLoadMillis(rs.getLong("transform_load_millis"));
                    batch.setExtractStartTime(rs.getDateTime("extract_start_time"));
                    batch.setTransferStartTime(rs.getDateTime("transfer_start_time"));
                    batch.setLoadStartTime(rs.getDateTime("load_start_time"));
                    batch.setSqlState(rs.getString("sql_state"));
                    batch.setSqlCode(rs.getInt("sql_code"));
                    batch.setSqlMessage(rs.getString("sql_message"));
                    batch.setFailedDataId(rs.getLong("failed_data_id"));
                    batch.setFailedLineNumber(rs.getLong("failed_line_number"));
                    batch.setLastUpdatedHostName(rs.getString("last_update_hostname"));
                    batch.setLastUpdatedTime(rs.getDateTime("last_update_time"));
                    batch.setCreateTime(rs.getDateTime("create_time"));
                    batch.setLoadFlag(rs.getBoolean("load_flag"));
                    batch.setErrorFlag(rs.getBoolean("error_flag"));
                    batch.setCommonFlag(rs.getBoolean("common_flag"));
                    batch.setExtractJobFlag(rs.getBoolean("extract_job_flag"));
                    batch.setLoadId(rs.getLong("load_id"));
                    batch.setCreateBy(rs.getString("create_by"));
                    batch.setSummary(rs.getString("summary"));
                    batch.setFallbackInsertCount(rs.getLong("fallback_insert_count"));
                    batch.setFallbackUpdateCount(rs.getLong("fallback_update_count"));
                    batch.setConflictWinCount(rs.getLong("conflict_win_count"));
                    batch.setConflictLoseCount(rs.getLong("conflict_lose_count"));
                    batch.setIgnoreRowCount(rs.getLong("ignore_row_count"));
                    batch.setMissingDeleteCount(rs.getLong("missing_delete_count"));
                    batch.setSkipCount(rs.getLong("skip_count"));
                    batch.setBulkLoaderFlag(rs.getBoolean("bulk_loader_flag"));
                    batch.setThreadId(rs.getInteger("thread_id"));
                    batch.setDataMinCreateTime(rs.getDateTime("data_min_create_time"));
                    batch.setDataMaxCreateTime(rs.getDateTime("data_max_create_time"));
                }
                return batch;
            } else {
                return null;
            }
        }
    }

    class BacklogSummaryMapper implements ISqlRowMapper<BacklogSummary> {
        @Override
        public BacklogSummary mapRow(Row row) {
            BacklogSummary summary = new BacklogSummary();
            summary.setByteCount(row.getLong("byte_count"));
            summary.setRowCount(row.getLong("rows_count"));
            return summary;
        }
    }

    static class OutgoingBatchSummaryByNodeBriefStatsMapper implements ISqlRowMapper<OutgoingBatchSummaryByNodeBriefStats> {
        @Override
        public OutgoingBatchSummaryByNodeBriefStats mapRow(Row row) {
            return new OutgoingBatchSummaryByNodeBriefStats(
                    row.getString("node_id"),
                    row.getString("status"),
                    row.getDateTime("batch_date"),
                    row.getLong("batches"),
                    row.getLong("data_rows"));
        }
    }
}
