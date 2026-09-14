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
package org.jumpmind.symmetric.service.impl;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.db.sql.mapper.DateMapper;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingBatchSummary;
import org.jumpmind.symmetric.model.IncomingBatchSummaryByNodeBriefStats;
import org.jumpmind.symmetric.service.FilterCriterion;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.util.FormatUtils;

/**
 * @see IIncomingBatchService
 */
public class IncomingBatchService extends AbstractService implements IIncomingBatchService {
    protected IClusterService clusterService;
    protected Map<String, Collection<String>> readyQueuesCache = new HashMap<>();

    @Override
    public List<String> getNodesInError() {
        return sqlTemplate.query(getSql("selectNodesInErrorSql"), new StringMapper());
    }

    public IncomingBatchService(IParameterService parameterService, ISymmetricDialect symmetricDialect, IClusterService clusterService) {
        super(parameterService, symmetricDialect);
        this.clusterService = clusterService;
        setSqlMap(new IncomingBatchServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    public void refreshIncomingBatch(IncomingBatch batch) {
        sqlTemplate.queryForObject(getSql("selectIncomingBatchPrefixSql", "findIncomingBatchSql"), new IncomingBatchMapper(batch),
                batch.getBatchId(), batch.getNodeId());
    }

    public IncomingBatch findIncomingBatch(long batchId, String nodeId) {
        if (nodeId != null) {
            return sqlTemplate.queryForObject(getSql("selectIncomingBatchPrefixSql", "findIncomingBatchSql"), new IncomingBatchMapper(),
                    batchId, nodeId);
        } else {
            return sqlTemplate.queryForObject(getSql("selectIncomingBatchPrefixSql", "findIncomingBatchByBatchIdSql"),
                    new IncomingBatchMapper(), batchId);
        }
    }

    public int countIncomingBatchesInError() {
        return sqlTemplateDirty.queryForInt(getSql("countIncomingBatchesErrorsSql"));
    }

    public int countIncomingBatchesInError(String channelId) {
        return sqlTemplateDirty.queryForInt(getSql("countIncomingBatchesErrorsOnChannelSql"), channelId);
    }

    public Date getIncomingBatchesLatestUpdateSql() {
        return sqlTemplateDirty.queryForObject(getSql("getIncomingBatchesLatestUpdateSql"), Date.class);
    }

    public List<IncomingBatch> findIncomingBatchErrors(int maxRows) {
        return sqlTemplateDirty.query(getSql("selectIncomingBatchPrefixSql", "findIncomingBatchErrorsSql"), maxRows,
                new IncomingBatchMapper());
    }

    public void markIncomingBatchesOk(String nodeId) {
        List<IncomingBatch> batches = listIncomingBatchesInErrorFor(nodeId);
        for (IncomingBatch incomingBatch : batches) {
            if (isRecordOkBatchesEnabled()) {
                incomingBatch.setErrorFlag(false);
                incomingBatch.setStatus(Status.OK);
                updateIncomingBatch(incomingBatch);
            } else {
                deleteIncomingBatch(incomingBatch);
            }
        }
    }

    public void removingIncomingBatches(String nodeId) {
        sqlTemplate.update(getSql("deleteIncomingBatchByNodeSql"), nodeId);
    }

    public List<IncomingBatch> listIncomingBatchesInErrorFor(String nodeId) {
        return sqlTemplate.query(getSql("selectIncomingBatchPrefixSql", "listIncomingBatchesInErrorForNodeSql"), new IncomingBatchMapper(),
                nodeId);
    }

    @SuppressWarnings("deprecation")
    public boolean isRecordOkBatchesEnabled() {
        boolean enabled = true;
        if (!parameterService.is(ParameterConstants.INCOMING_BATCH_RECORD_OK_ENABLED, true)) {
            enabled = false;
        }
        if (parameterService.is(ParameterConstants.INCOMING_BATCH_DELETE_ON_LOAD, false)) {
            enabled = false;
        }
        return enabled;
    }

    public List<Date> listIncomingBatchTimes(List<String> nodeIds, List<String> channels,
            List<IncomingBatch.Status> statuses, List<Long> loads, boolean ascending) {
        String whereClause = buildBatchWhere(nodeIds, channels, statuses, loads, null);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("NODES", nodeIds);
        params.put("CHANNELS", channels);
        params.put("STATUSES", toStringList(statuses));
        params.put("LOADS", loads);
        String sql = getSql("selectCreateTimePrefixSql", whereClause, ascending ? " order by create_time" : " order by create_time desc");
        return sqlTemplate.query(sql, new DateMapper(), params);
    }

    public List<IncomingBatch> listIncomingBatches(List<String> nodeIds, List<String> channels,
            List<IncomingBatch.Status> statuses, List<Long> loads, Date startAtCreateTime,
            Date startAtLastUpdateTime, final int maxRowsToRetrieve, boolean ascending) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("NODES", nodeIds);
        params.put("CHANNELS", channels);
        params.put("STATUSES", toStringList(statuses));
        params.put("CREATE_TIME", startAtCreateTime);
        params.put("LAST_UPDATE_TIME", startAtLastUpdateTime);
        params.put("LOADS", loads);
        String where = buildBatchWhere(nodeIds, channels, statuses, loads, startAtLastUpdateTime);
        String createTimeLimiter = "";
        if (startAtCreateTime != null) {
            if (StringUtils.isBlank(where)) {
                where = " where 1=1 ";
            }
            createTimeLimiter = " and create_time " + (ascending ? ">=" : "<=") + " :CREATE_TIME";
        }
        String sql = getSql("selectIncomingBatchPrefixSql", where, createTimeLimiter,
                ascending ? " order by create_time" : " order by create_time desc");
        return sqlTemplateDirty.query(sql, maxRowsToRetrieve, new IncomingBatchMapper(), params);
    }

    public List<IncomingBatch> listIncomingBatchesWithLimit(int offset, int limit, List<FilterCriterion> filter,
            String orderColumn, String orderDirection) {
        String where = filter != null ? buildBatchWhereFromFilter(filter) : null;
        Map<String, Object> params = filter != null ? buildBatchParams(filter) : new HashMap<String, Object>();
        String orderBy = buildBatchOrderBy(orderColumn, orderDirection);
        String sql = getSql("selectIncomingBatchPrefixSql", where, orderBy);
        List<IncomingBatch> batchList;
        if (platform.supportsLimitOffset()) {
            sql = platform.massageForLimitOffset(sql, limit, offset);
            batchList = sqlTemplateDirty.query(sql, Integer.MAX_VALUE, new IncomingBatchMapper(), params);
        } else {
            ISqlReadCursor<IncomingBatch> cursor = sqlTemplateDirty.queryForCursor(sql, new IncomingBatchMapper(), params);
            try {
                IncomingBatch next = null;
                batchList = new ArrayList<IncomingBatch>();
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

    public int countIncomingBatchesWithLimit(List<FilterCriterion> filter) {
        String where = filter != null ? buildBatchWhereFromFilter(filter) : null;
        Map<String, Object> params = filter != null ? buildBatchParams(filter) : new HashMap<String, Object>();
        String sql = getSql("selectCountBatchesPrefixSql", where);
        int count = sqlTemplateDirty.queryForInt(sql, params);
        int maxBatches = parameterService.getInt("batch.screen.max.to.select");
        return maxBatches > 0 ? Math.min(count, maxBatches) : count;
    }

    protected boolean containsOnlyErrorStatus(List<IncomingBatch.Status> statuses) {
        return statuses.size() == 1 && statuses.get(0) == IncomingBatch.Status.ER;
    }

    protected List<String> toStringList(List<IncomingBatch.Status> statuses) {
        List<String> statusStrings = new ArrayList<String>(statuses.size());
        for (Status status : statuses) {
            statusStrings.add(status.name());
        }
        return statusStrings;
    }

    public boolean acquireIncomingBatch(IncomingBatch batch) {
        boolean okayToProcess = true;
        if (batch.isPersistable()) {
            IncomingBatch existingBatch = null;
            if (isRecordOkBatchesEnabled()) {
                try {
                    insertIncomingBatch(batch);
                } catch (UniqueKeyException e) {
                    batch.setRetry(true);
                    existingBatch = findIncomingBatch(batch.getBatchId(), batch.getNodeId());
                }
            } else {
                existingBatch = findIncomingBatch(batch.getBatchId(), batch.getNodeId());
                if (existingBatch != null) {
                    batch.setRetry(true);
                }
            }
            if (batch.isRetry()) {
                if (existingBatch.getStatus() == Status.ER || existingBatch.getStatus() == Status.LD || existingBatch.getStatus() == Status.RS
                        || !parameterService.is(ParameterConstants.INCOMING_BATCH_SKIP_DUPLICATE_BATCHES_ENABLED)) {
                    okayToProcess = true;
                    batch.setErrorFlag(existingBatch.isErrorFlag());
                    batch.setFailedLineNumber(existingBatch.getFailedLineNumber());
                    batch.setFailedRowNumber(existingBatch.getFailedRowNumber());
                    existingBatch.setStatus(Status.LD);
                    log.info("Retrying batch {}", batch.getNodeBatchId());
                } else if (existingBatch.getStatus() == Status.IG) {
                    okayToProcess = false;
                    batch.setStatus(Status.OK);
                    batch.incrementIgnoreCount();
                    existingBatch.setStatus(Status.OK);
                    existingBatch.incrementIgnoreCount();
                    log.info("Ignoring batch {}", batch.getNodeBatchId());
                } else {
                    okayToProcess = false;
                    batch.setStatus(existingBatch.getStatus());
                    batch.setByteCount(existingBatch.getByteCount());
                    batch.setLoadMillis(existingBatch.getLoadMillis());
                    batch.setNetworkMillis(existingBatch.getNetworkMillis());
                    batch.setFilterMillis(existingBatch.getFilterMillis());
                    batch.setSkipCount(existingBatch.getSkipCount() + 1);
                    batch.setLoadRowCount(existingBatch.getLoadRowCount());
                    existingBatch.setSkipCount(existingBatch.getSkipCount() + 1);
                    log.info("Skipping batch {} that was already loaded", batch.getNodeBatchId());
                }
                updateIncomingBatch(existingBatch);
            }
        }
        return okayToProcess;
    }

    public void insertIncomingBatch(ISqlTransaction transaction, IncomingBatch batch) {
        if (batch.isPersistable()) {
            boolean alreadyExists = false;
            if (symmetricDialect.getName().equals(DatabaseNamesConstants.REDSHIFT)
                    || (symmetricDialect.getDriverName() != null
                            && symmetricDialect.getDriverName().equalsIgnoreCase(DatabaseNamesConstants.OPENEDGE))) {
                if (findIncomingBatch(batch.getBatchId(), batch.getNodeId()) != null) {
                    alreadyExists = true;
                }
            }
            if (!alreadyExists) {
                batch.setLastUpdatedHostName(clusterService.getServerId());
                batch.setLastUpdatedTime(new Date());
                transaction.prepareAndExecute(getSql("insertIncomingBatchSql"),
                        new Object[] { batch.getBatchId(), batch.getNodeId(), batch.getChannelId(), batch.getStatus().name(),
                                batch.getNetworkMillis(), batch.getFilterMillis(), batch.getLoadMillis(), batch.getFailedRowNumber(),
                                batch.getFailedLineNumber(), batch.getByteCount(), batch.getLoadRowCount(), batch.getFallbackInsertCount(),
                                batch.getFallbackUpdateCount(), batch.getConflictWinCount(), batch.getConflictLoseCount(), batch.getIgnoreCount(),
                                batch.getIgnoreRowCount(), batch.getMissingDeleteCount(), batch.getSkipCount(), batch.getSqlState(), batch.getSqlCode(),
                                FormatUtils.abbreviateForLogging(batch.getSqlMessage()), batch.getLastUpdatedHostName(), new Date(), batch.getSummary(),
                                new Date(), batch.isLoadFlag(), batch.getExtractCount(), batch.getSentCount(), batch.getLoadCount(), batch.getLoadId(),
                                batch.isCommonFlag(), batch.getRouterMillis(), batch.getExtractMillis(), batch.getTransformExtractMillis(),
                                batch.getTransformLoadMillis(), batch.getReloadRowCount(), batch.getOtherRowCount(), batch.getDataRowCount(),
                                batch.getDataInsertRowCount(), batch.getDataUpdateRowCount(), batch.getDataDeleteRowCount(),
                                batch.getExtractRowCount(), batch.getExtractInsertRowCount(), batch.getExtractUpdateRowCount(),
                                batch.getLoadInsertRowCount(), batch.getLoadUpdateRowCount(), batch.getLoadDeleteRowCount(),
                                batch.getExtractDeleteRowCount(), batch.getFailedDataId() },
                        new int[] { Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.CHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.VARCHAR, Types.NUMERIC,
                                Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR, Types.TIMESTAMP, Types.NUMERIC, Types.NUMERIC,
                                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                                Types.NUMERIC });
            }
        }
    }

    public void insertIncomingBatch(IncomingBatch batch) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            insertIncomingBatch(transaction, batch);
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

    public int deleteIncomingBatch(IncomingBatch batch) {
        return sqlTemplate.update(getSql("deleteIncomingBatchSql"), new Object[] { batch.getBatchId(), batch.getNodeId() },
                new int[] { symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
    }

    public int updateIncomingBatch(IncomingBatch batch) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            int count = updateIncomingBatch(transaction, batch);
            transaction.commit();
            return count;
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

    public int updateIncomingBatch(ISqlTransaction transaction, IncomingBatch batch) {
        int count = 0;
        if (batch.isPersistable()) {
            String sql = getSql("updateIncomingBatchSql");
            if (batch.getStatus() == IncomingBatch.Status.OK) {
                batch.setErrorFlag(false);
                batch.setFailedDataId(0);
                batch.setFailedLineNumber(0l);
                batch.setFailedRowNumber(0l);
            } else {
                sql += getSql("statusNotOk");
            }
            batch.setLastUpdatedHostName(clusterService.getServerId());
            count = transaction.prepareAndExecute(sql,
                    new Object[] { batch.getStatus().name(), batch.isErrorFlag() ? 1 : 0, batch.getNetworkMillis(), batch.getFilterMillis(),
                            batch.getLoadMillis(), batch.getFailedRowNumber(), batch.getFailedLineNumber(), batch.getByteCount(),
                            batch.getLoadRowCount(), batch.getFallbackInsertCount(), batch.getFallbackUpdateCount(), batch.getConflictWinCount(),
                            batch.getConflictLoseCount(), batch.getIgnoreCount(), batch.getIgnoreRowCount(), batch.getMissingDeleteCount(),
                            batch.getSkipCount(), batch.getSqlState(), batch.getSqlCode(), FormatUtils.abbreviateForLogging(batch.getSqlMessage()),
                            batch.getLastUpdatedHostName(), new Date(), batch.getSummary(), batch.isLoadFlag(), batch.getExtractCount(), batch.getSentCount(),
                            batch.getLoadCount(), batch.getLoadId(), batch.isCommonFlag(), batch.getRouterMillis(), batch.getExtractMillis(),
                            batch.getTransformExtractMillis(), batch.getTransformLoadMillis(), batch.getReloadRowCount(),
                            batch.getOtherRowCount(), batch.getDataRowCount(), batch.getDataInsertRowCount(), batch.getDataUpdateRowCount(),
                            batch.getDataDeleteRowCount(), batch.getExtractRowCount(), batch.getExtractInsertRowCount(),
                            batch.getExtractUpdateRowCount(), batch.getExtractDeleteRowCount(), batch.getLoadInsertRowCount(),
                            batch.getLoadUpdateRowCount(), batch.getLoadDeleteRowCount(), batch.getFailedDataId(), batch.isBulkLoaderFlag(), batch.getBatchId(),
                            batch.getNodeId() },
                    new int[] { Types.CHAR, Types.SMALLINT, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                            Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                            Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.VARCHAR, Types.NUMERIC, Types.VARCHAR, Types.VARCHAR,
                            Types.TIMESTAMP, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                            Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                            Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                            Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                            symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
        }
        return count;
    }

    public Map<String, BatchId> findMaxBatchIdsByChannel() {
        Map<String, BatchId> ids = new HashMap<String, BatchId>();
        sqlTemplate.query(getSql("maxBatchIdsSql"), new BatchIdMapper(ids), IncomingBatch.Status.OK.name());
        return ids;
    }

    @Override
    public List<IncomingBatchSummary> findIncomingBatchSummaryByNode(String nodeId, Date sinceCreateTime,
            Status... statuses) {
        Object[] args = new Object[statuses.length + 1];
        args[args.length - 1] = nodeId;
        StringBuilder inList = buildStatusList(args, statuses);
        String sql = getSql("selectIncomingBatchSummaryPrefixSql",
                "selectIncomingBatchSummaryStatsPrefixSql",
                "whereStatusAndNodeGroupByStatusSql").replace(":STATUS_LIST", inList.substring(0, inList.length() - 1));
        return sqlTemplateDirty.query(sql, new IncomingBatchSummaryMapper(false, false), args);
    }

    protected StringBuilder buildStatusList(Object[] args, Status... statuses) {
        StringBuilder inList = new StringBuilder();
        for (int i = 0; i < statuses.length; i++) {
            args[i] = statuses[i].name();
            inList.append("?,");
        }
        return inList;
    }

    public List<IncomingBatchSummary> findIncomingBatchSummaryByChannel(Status... statuses) {
        Object[] args = new Object[statuses.length];
        StringBuilder inList = buildStatusList(args, statuses);
        String sql = getSql("selectIncomingBatchSummaryByNodeAndChannelPrefixSql",
                "selectIncomingBatchSummaryStatsPrefixSql",
                "whereStatusGroupByStatusAndNodeAndChannelSql").replace(":STATUS_LIST",
                        inList.substring(0, inList.length() - 1));
        return sqlTemplateDirty.query(sql, new IncomingBatchSummaryMapper(true, true), args);
    }

    public List<IncomingBatchSummary> findIncomingBatchSummary(Status... statuses) {
        Object[] args = new Object[statuses.length];
        StringBuilder inList = buildStatusList(args, statuses);
        String sql = getSql("selectIncomingBatchSummaryByNodePrefixSql",
                "selectIncomingBatchSummaryStatsPrefixSql",
                "whereStatusGroupByStatusAndNodeSql").replace(":STATUS_LIST", inList.substring(0, inList.length() - 1));
        return sqlTemplateDirty.query(sql, new IncomingBatchSummaryMapper(true, false), args);
    }

    public List<IncomingBatchSummary> findIncomingBatchSummaryByNodeAndChannel(String nodeId, String channelId,
            Date sinceCreateTime, Status... statuses) {
        Object[] args = new Object[statuses.length + 2];
        args[args.length - 1] = nodeId;
        args[args.length - 2] = channelId;
        StringBuilder inList = buildStatusList(args, statuses);
        String sql = getSql("selectIncomingBatchSummaryPrefixSql",
                "selectIncomingBatchSummaryStatsPrefixSql",
                "whereStatusAndNodeAndChannelGroupByStatusSql").replace(":STATUS_LIST", inList.substring(0, inList.length() - 1));
        return sqlTemplateDirty.query(sql, new IncomingBatchSummaryMapper(false, false), args);
    }

    @Override
    public List<IncomingBatchSummaryByNodeBriefStats> findIncomingBatchSummaryByNodeBriefStats() {
        return sqlTemplateDirty.query(getSql("selectIncomingBatchSummaryByNodeBriefStatsSql"),
                new IncomingBatchSummaryByNodeBriefStatsMapper());
    }

    @Override
    public Map<String, Date> findLastUpdatedByChannel() {
        Map<String, Date> captureMap = new HashMap<String, Date>();
        LastCaptureByChannelMapper mapper = new LastCaptureByChannelMapper(captureMap);
        sqlTemplate.query(getSql("lastUpdateByChannelSql"), mapper);
        return mapper.getCaptureMap();
    }

    @Override
    public List<BatchId> getAllBatches() {
        return sqlTemplateDirty.query(getSql("getAllBatchesSql"), new BatchIdMapper());
    }

    @Override
    public Collection<String> getReadyQueues(String sourceNodeId) {
        Collection<String> queues = readyQueuesCache.get(sourceNodeId);
        if (queues == null) {
            queues = new HashSet<String>();
        }
        return queues;
    }

    @Override
    public void setReadyQueues(String sourceNodeId, Collection<String> queues) {
        readyQueuesCache.put(sourceNodeId, queues);
    }

    static class BatchIdMapper implements ISqlRowMapper<BatchId> {
        Map<String, BatchId> ids;

        public BatchIdMapper() {
        }

        public BatchIdMapper(Map<String, BatchId> ids) {
            this.ids = ids;
        }

        public BatchId mapRow(Row rs) {
            BatchId batch = new BatchId();
            batch.setBatchId(rs.getLong("batch_id"));
            batch.setNodeId(rs.getString("node_id"));
            if (ids != null) {
                ids.put(rs.getString("channel_id"), batch);
            }
            return batch;
        }
    }

    static class LastCaptureByChannelMapper implements ISqlRowMapper<String> {
        private Map<String, Date> captureMap;

        public LastCaptureByChannelMapper(Map<String, Date> map) {
            captureMap = map;
        }

        public Map<String, Date> getCaptureMap() {
            return captureMap;
        }

        @Override
        public String mapRow(Row row) {
            captureMap.put(row.getString("CHANNEL_ID"), row.getDateTime("LAST_UPDATE_TIME"));
            return null;
        }
    }

    static class IncomingBatchMapper implements ISqlRowMapper<IncomingBatch> {
        IncomingBatch batchToRefresh = null;

        public IncomingBatchMapper(IncomingBatch batchToRefresh) {
            this.batchToRefresh = batchToRefresh;
        }

        public IncomingBatchMapper() {
        }

        public IncomingBatch mapRow(Row rs) {
            IncomingBatch batch = batchToRefresh != null ? batchToRefresh : new IncomingBatch();
            batch.setBatchId(rs.getLong("batch_id"));
            batch.setNodeId(rs.getString("node_id"));
            batch.setChannelId(rs.getString("channel_id"));
            batch.setStatusFromString(rs.getString("status"));
            batch.setRouterMillis(rs.getLong("router_millis"));
            batch.setNetworkMillis(rs.getLong("network_millis"));
            batch.setFilterMillis(rs.getLong("filter_millis"));
            batch.setLoadMillis(rs.getLong("load_millis"));
            batch.setExtractMillis(rs.getLong("extract_millis"));
            batch.setTransformExtractMillis(rs.getLong("transform_extract_millis"));
            batch.setTransformLoadMillis(rs.getLong("transform_load_millis"));
            batch.setFailedRowNumber(rs.getLong("failed_row_number"));
            batch.setFailedLineNumber(rs.getLong("failed_line_number"));
            batch.setByteCount(rs.getLong("byte_count"));
            batch.setLoadFlag(rs.getBoolean("load_flag"));
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
            batch.setFallbackInsertCount(rs.getLong("fallback_insert_count"));
            batch.setFallbackUpdateCount(rs.getLong("fallback_update_count"));
            batch.setConflictWinCount(rs.getLong("conflict_win_count"));
            batch.setConflictLoseCount(rs.getLong("conflict_lose_count"));
            batch.setIgnoreCount(rs.getLong("ignore_count"));
            batch.setIgnoreRowCount(rs.getLong("ignore_row_count"));
            batch.setMissingDeleteCount(rs.getLong("missing_delete_count"));
            batch.setSkipCount(rs.getLong("skip_count"));
            batch.setSqlState(rs.getString("sql_state"));
            batch.setSqlCode(rs.getInt("sql_code"));
            batch.setSqlMessage(rs.getString("sql_message"));
            batch.setLastUpdatedHostName(rs.getString("last_update_hostname"));
            batch.setLastUpdatedTime(rs.getDateTime("last_update_time"));
            batch.setCreateTime(rs.getDateTime("create_time"));
            batch.setErrorFlag(rs.getBoolean("error_flag"));
            batch.setSummary(rs.getString("summary"));
            batch.setLoadId(rs.getLong("load_id"));
            batch.setCommonFlag(rs.getBoolean("common_flag"));
            batch.setFailedDataId(rs.getLong("failed_data_id"));
            batch.setBulkLoaderFlag(rs.getBoolean("bulk_loader_flag"));
            return batch;
        }
    }

    static class IncomingBatchSummaryMapper implements ISqlRowMapper<IncomingBatchSummary> {
        boolean withNode = false;
        boolean withChannel = false;

        public IncomingBatchSummaryMapper(boolean withNode, boolean withChannel) {
            this.withNode = withNode;
            this.withChannel = withChannel;
        }

        public IncomingBatchSummary mapRow(Row rs) {
            IncomingBatchSummary summary = new IncomingBatchSummary();
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

    static class IncomingBatchSummaryByNodeBriefStatsMapper implements ISqlRowMapper<IncomingBatchSummaryByNodeBriefStats> {
        @Override
        public IncomingBatchSummaryByNodeBriefStats mapRow(Row row) {
            return new IncomingBatchSummaryByNodeBriefStats(
                    row.getString("node_id"),
                    row.getString("status"),
                    row.getDateTime("batch_date"),
                    row.getLong("batches"),
                    row.getLong("data_rows"));
        }
    }
}
