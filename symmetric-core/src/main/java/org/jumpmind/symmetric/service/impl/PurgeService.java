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

import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongConsumer;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.mapper.LongMapper;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.ext.IPurgeListener;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.RegistrationRequest;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IPurgeService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.statistic.IStatisticManager;

/**
 * @see IPurgeService
 */
public class PurgeService extends AbstractService implements IPurgeService {
    enum MinMaxDeleteSql {
        DATA, DATA_EXISTS, DATA_RANGE, DATA_EVENT, DATA_EVENT_EXISTS, DATA_EVENT_RANGE, OUTGOING_BATCH, OUTGOING_BATCH_EXISTS, OUTGOING_BATCH_RANGE, STRANDED_DATA, STRANDED_DATA_EVENT
    };

    private IClusterService clusterService;
    private IDataService dataService;
    private ISequenceService sequenceService;
    private IStatisticManager statisticManager;
    private IExtensionService extensionService;
    private IContextService contextService;
    private FastDateFormat fastFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");
    private final int MINS_IN_ONE_WEEK = 10080;
    private final int MINS_IN_60_DAYS = 86400;

    public PurgeService(IParameterService parameterService, ISymmetricDialect symmetricDialect, IClusterService clusterService,
            IDataService dataService, ISequenceService sequenceService, IStatisticManager statisticManager, IExtensionService extensionService,
            IContextService contextService) {
        super(parameterService, symmetricDialect);
        this.clusterService = clusterService;
        this.dataService = dataService;
        this.sequenceService = sequenceService;
        this.statisticManager = statisticManager;
        this.extensionService = extensionService;
        this.contextService = contextService;
        setSqlMap(new PurgeServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    @Override
    public long purgeOutgoing(boolean force) {
        long rowsPurged = 0;
        long startTime = System.currentTimeMillis();
        Calendar retentionCutoff = Calendar.getInstance();
        retentionCutoff.add(Calendar.MINUTE, -parameterService.getInt(ParameterConstants.PURGE_RETENTION_MINUTES));
        try {
            rowsPurged += purgeOutgoing(retentionCutoff, force);
            if (rowsPurged != -1) {
                statisticManager.addJobStats(ClusterConstants.PURGE_OUTGOING, startTime, System.currentTimeMillis(), rowsPurged);
            }
        } catch (Exception ex) {
            try {
                statisticManager.addJobStats(ClusterConstants.PURGE_OUTGOING, startTime, System.currentTimeMillis(), rowsPurged, ex);
                log.info("Failed to execute purge, but will try again,", ex);
                rowsPurged += purgeOutgoing(retentionCutoff, force);
                if (rowsPurged != -1) {
                    statisticManager.addJobStats(ClusterConstants.PURGE_OUTGOING, startTime, System.currentTimeMillis(), rowsPurged);
                }
            } catch (Exception e) {
                log.error("Failed to execute purge, so aborting,", e);
                statisticManager.addJobStats(ClusterConstants.PURGE_OUTGOING, startTime, System.currentTimeMillis(), rowsPurged, e);
            }
        } finally {
            log.info("The outgoing purge process has completed");
        }
        return rowsPurged;
    }

    @Override
    public long purgeIncoming(boolean force) {
        long rowsPurged = 0;
        Calendar retentionCutoff = Calendar.getInstance();
        retentionCutoff.add(Calendar.MINUTE, -parameterService.getInt(ParameterConstants.PURGE_RETENTION_MINUTES));
        rowsPurged += purgeIncoming(retentionCutoff, force);
        return rowsPurged;
    }

    @Override
    public long purgeOutgoing(Calendar retentionCutoff, boolean force) {
        long rowsPurged = 0;
        if (force || clusterService.lock(ClusterConstants.PURGE_OUTGOING)) {
            try {
                log.info("The outgoing purge process is about to run for data older than {}",
                        fastFormat.format(retentionCutoff.getTime()));
                List<IPurgeListener> purgeListeners = extensionService.getExtensionPointList(IPurgeListener.class);
                for (IPurgeListener purgeListener : purgeListeners) {
                    try {
                        rowsPurged += purgeListener.beforePurgeOutgoing(force);
                    } catch (Throwable e) {
                        log.error(e.getMessage(), e);
                    }
                }
                // VoltDB doesn't support capture, or subselects. So we'll just be purging heartbeats
                // by date here.
                if (getSymmetricDialect().getName().equalsIgnoreCase(DatabaseNamesConstants.VOLTDB)) {
                    rowsPurged += purgeOutgoingByRetentionCutoff(retentionCutoff);
                } else {
                    OutgoingContext context = buildOutgoingContext(retentionCutoff);
                    rowsPurged += purgeStrandedBatches();
                    rowsPurged += purgeDataRows(context);
                    rowsPurged += purgeOutgoingBatch(context);
                    rowsPurged += purgeLingeringBatches(context);
                    rowsPurged += purgeDataGapsExpired(context);
                    rowsPurged += purgeStranded(context);
                    rowsPurged += purgeExtractRequests();
                    rowsPurged += purgeStrandedChannels();
                }
                for (IPurgeListener purgeListener : purgeListeners) {
                    try {
                        rowsPurged += purgeListener.purgeOutgoing(force);
                    } catch (Throwable e) {
                        log.error(e.getMessage(), e);
                    }
                }
            } finally {
                if (!force) {
                    clusterService.unlock(ClusterConstants.PURGE_OUTGOING);
                }
            }
        } else {
            log.debug("Could not get a lock to run an outgoing purge");
            rowsPurged = -1;
        }
        return rowsPurged;
    }

    protected long purgeOutgoingByRetentionCutoff(Calendar retentionCutoff) {
        int totalCount = 0;
        totalCount += executePurgeDelete(getSql("deleteOutgoingBatchByCreateTimeSql"), retentionCutoff.getTime());
        totalCount += executePurgeDelete(getSql("deleteDataEventByCreateTimeSql"), retentionCutoff.getTime());
        totalCount += executePurgeDelete(getSql("deleteDataByCreateTimeSql"), retentionCutoff.getTime());
        totalCount += executePurgeDelete(getSql("deleteExtractRequestByCreateTimeSql"), retentionCutoff.getTime());
        log.info("Done purging {} rows", totalCount);
        return totalCount;
    }

    protected int executePurgeDelete(String deleteSql, Object argument) {
        log.debug("Running the following statement: {} with the following arguments: {}", deleteSql, argument);
        int count = sqlTemplate.update(deleteSql, argument);
        log.debug("Deleted {} rows", count);
        return count;
    }

    /**
     * Deletes across sym_data_event and sym_outgoing_batch using ranges of batch_ids. The total eligible range of batch_ids is the first batch_id in the table,
     * or the next batch_id from the previous run, up to the batch_id of the newest batch that is old enough to purge. The first half of the range uses a fast
     * delete by PK. It stops just before the first batch_id of any batch with a not-OK status. The second half of the range uses a slower delete. When deleting
     * sym_data_event, it checks that there is no associated non-OK batch. When deleting sym_outgoing_batch, it checks that the batch has no rows routed in
     * sym_data_event.
     * 
     * @param time
     *            Calendar date/time of retention cutoff
     * @return number of rows deleted
     */
    private long purgeOutgoingBatch(OutgoingContext context) {
        long[] batchMinMax = { context.getMinBatchId(), context.getMaxBatchId() };
        long[] eventMinMax = { context.getMinEventBatchId(), context.getMaxBatchId() };
        int maxNumOfBatchIdsToPurgeInTx = parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_BATCH_IDS);
        int maxNumOfDataEventsToPurgeInTx = parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_EVENT_BATCH_IDS);
        int dataEventsPurgedCount = 0;
        int outgoingbatchPurgedCount = 0;
        if (parameterService.is(ParameterConstants.PURGE_FIRST_PASS)) {
            log.info("Getting first batch_id for outstanding batches");
            long notOkBatchId = sqlTemplateDirty.queryForLong(getSql("minOutgoingBatchNotStatusSql"), OutgoingBatch.Status.OK.name());
            long batchRangeMinMax[] = getRangeMinMax(batchMinMax, notOkBatchId);
            long eventRangeMinMax[] = getRangeMinMax(eventMinMax, notOkBatchId);
            batchMinMax = getMinMax(batchMinMax, notOkBatchId, batchRangeMinMax);
            eventMinMax = getMinMax(eventMinMax, notOkBatchId, eventRangeMinMax);
            dataEventsPurgedCount += purgeByMinMax(eventRangeMinMax, MinMaxDeleteSql.DATA_EVENT_RANGE, context, maxNumOfDataEventsToPurgeInTx,
                    count -> statisticManager.incrementPurgedDataEventRows(count));
            outgoingbatchPurgedCount += purgeByMinMax(batchRangeMinMax, MinMaxDeleteSql.OUTGOING_BATCH_RANGE, context, maxNumOfBatchIdsToPurgeInTx,
                    count -> statisticManager.incrementPurgedBatchOutgoingRows(count));
        }
        dataEventsPurgedCount += purgeByMinMax(eventMinMax, MinMaxDeleteSql.DATA_EVENT, context, maxNumOfDataEventsToPurgeInTx,
                count -> statisticManager.incrementPurgedDataEventRows(count));
        outgoingbatchPurgedCount += purgeByMinMax(batchMinMax, MinMaxDeleteSql.OUTGOING_BATCH, context, maxNumOfBatchIdsToPurgeInTx,
                count -> statisticManager.incrementPurgedBatchOutgoingRows(count));
        return dataEventsPurgedCount + outgoingbatchPurgedCount;
    }

    private long[] getRangeMinMax(long[] minMax, long notOkBatchId) {
        return new long[] { minMax[0], Math.min(notOkBatchId > 0 ? notOkBatchId - 1 : minMax[1], minMax[1]) };
    }

    private long[] getMinMax(long[] minMax, long notOkBatchId, long[] rangeMinMax) {
        if (rangeMinMax[1] == minMax[1]) {
            minMax[1] = -1;
        } else {
            minMax[0] = notOkBatchId;
        }
        return minMax;
    }

    private long purgeLingeringBatches(OutgoingContext context) {
        long totalRowsPurged = 0, totalBatchesPurged = 0;
        long ts = System.currentTimeMillis();
        final long lastBatchId = context.getMinBatchId();
        final long maxRows = parameterService.getLong(ParameterConstants.PURGE_MAX_LINGERING_BATCHES_READ);
        final int idType = symmetricDialect.getSqlTypeForIds();
        List<Long> batchIds = getLingeringBatchIds(lastBatchId, maxRows);
        while (batchIds.size() > 0) {
            for (Long batchId : batchIds) {
                long dataDeleteCount = 0, eventDeleteCount = 0, batchDeleteCount = 0;
                long countNotOkay = sqlTemplateDirty.queryForLong(getSql("countCommonBatchNotStatusForBatchId"),
                        batchId, OutgoingBatch.Status.OK.name());
                if (countNotOkay == 0) {
                    dataDeleteCount = sqlTemplate.update(getSql("deleteDataByBatchId"), new Object[] { batchId },
                            new int[] { idType });
                    statisticManager.incrementPurgedDataRows(dataDeleteCount);
                    eventDeleteCount = sqlTemplate.update(getSql("deleteDataEventByBatchId"), new Object[] { batchId },
                            new int[] { idType });
                    statisticManager.incrementPurgedDataEventRows(eventDeleteCount);
                    batchDeleteCount = sqlTemplate.update(getSql("deleteOutgoingBatchByBatchId"),
                            new Object[] { batchId, OutgoingBatch.Status.OK.name() }, new int[] { idType, Types.CHAR });
                    statisticManager.incrementPurgedBatchOutgoingRows(batchDeleteCount);
                } else {
                    batchDeleteCount = sqlTemplate.update(getSql("deleteOutgoingBatchByBatchId"),
                            new Object[] { batchId, OutgoingBatch.Status.OK.name() }, new int[] { idType, Types.CHAR });
                    statisticManager.incrementPurgedBatchOutgoingRows(batchDeleteCount);
                }
                totalRowsPurged += (dataDeleteCount + eventDeleteCount + batchDeleteCount);
                totalBatchesPurged++;
                if (System.currentTimeMillis() - ts > DateUtils.MILLIS_PER_MINUTE * 5) {
                    log.info("Purged {} of {} batches and {} rows so far", totalBatchesPurged, batchIds.size(), totalRowsPurged);
                    ts = System.currentTimeMillis();
                    clusterService.refreshLock(ClusterConstants.PURGE_OUTGOING);
                }
            }
            if (batchIds.size() == maxRows) {
                batchIds = getLingeringBatchIds(lastBatchId, maxRows);
                totalRowsPurged = 0;
                totalBatchesPurged = 0;
            } else {
                break;
            }
        }
        log.info("Done purging {} lingering batches and {} rows", totalBatchesPurged, totalRowsPurged);
        return totalRowsPurged;
    }

    private List<Long> getLingeringBatchIds(long lastBatchId, long maxRows) {
        List<Long> batchIds = new ArrayList<Long>();
        if (lastBatchId > 0) {
            ISqlReadCursor<Long> cursor = null;
            log.info("Looking for lingering batches before batch {}", lastBatchId);
            try {
                cursor = sqlTemplateDirty.queryForCursor(getSql("selectLingeringBatches"), new LongMapper(),
                        new Object[] { lastBatchId, OutgoingBatch.Status.OK.name() },
                        new int[] { symmetricDialect.getSqlTypeForIds(), Types.CHAR });
                Long batchId = null;
                long count = 0;
                while (count++ < maxRows && (batchId = cursor.next()) != null) {
                    batchIds.add(batchId);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            if (batchIds.size() > 0) {
                log.info("Found {} lingering batches to purge", batchIds.size());
            }
        }
        return batchIds;
    }

    /**
     * Set batches to OK status for nodes that don't exist or are sync disabled. Set batches to OK status for channels that don't exist.
     * 
     * @return number of rows updated
     */
    private long purgeStrandedBatches() {
        int totalRowsPurged = 0;
        log.info("Looking for old nodes in batches");
        List<String> nodes = sqlTemplateDirty.query(getSql("selectNodesWithStrandedBatches"),
                new StringMapper(), 1, OutgoingBatch.Status.OK.name());
        if (nodes.size() > 0) {
            log.info("Found {} old nodes in batches", nodes.size());
            for (String nodeId : nodes) {
                int rowsPurged = sqlTemplate.update(getSql("updateStrandedBatches"),
                        OutgoingBatch.Status.OK.name(), nodeId, OutgoingBatch.Status.OK.name());
                log.info("Set the status to {} for {} batches associated with node ID {}",
                        OutgoingBatch.Status.OK.name(), rowsPurged, nodeId);
                totalRowsPurged += rowsPurged;
                statisticManager.incrementPurgedBatchOutgoingRows(rowsPurged);
            }
        }
        log.info("Looking for old channels in batches");
        List<String> channels = sqlTemplateDirty.query(getSql("selectChannelsWithStrandedBatches"),
                new StringMapper(), OutgoingBatch.Status.OK.name());
        if (channels.size() > 0) {
            log.info("Found {} old channels in batches", channels.size());
            for (String channelId : channels) {
                int rowsPurged = sqlTemplate.update(getSql("updateStrandedBatchesByChannel"),
                        OutgoingBatch.Status.OK.name(), channelId, OutgoingBatch.Status.OK.name());
                log.info("Set the status to {} for {} batches associated with channel ID {}",
                        OutgoingBatch.Status.OK.name(), rowsPurged, channelId);
                totalRowsPurged += rowsPurged;
                statisticManager.incrementPurgedBatchOutgoingRows(rowsPurged);
            }
        }
        return totalRowsPurged;
    }

    /**
     * Purge non-existent channels from sym_data (that are unrouted when channel was deleted). Purging them here avoids them being counted as stranded data.
     * 
     * @return number of rows deleted
     */
    private long purgeStrandedChannels() {
        int totalRowsPurged = 0;
        log.info("Looking for old channels in data");
        List<String> channels = sqlTemplateDirty.query(getSql("selectOldChannelsForData"), new StringMapper());
        if (channels.size() > 0) {
            log.info("Found {} old channels in data", channels.size());
            for (String channelId : channels) {
                log.info("Purging data for channel {}", channelId);
                int rowsPurged = sqlTemplate.update(getSql("deleteDataByChannel"), channelId);
                totalRowsPurged += rowsPurged;
                statisticManager.incrementPurgedDataRows(rowsPurged);
            }
            log.info("Done purging {} data rows with old channels", totalRowsPurged);
        }
        return totalRowsPurged;
    }

    /**
     * Deletes across sym_data using ranges of data_ids. The total eligible range of data_ids is the first data_id in the table, or the next data_id from the
     * previous run, up to the largest data_id from the newest batch that is old enough to purge. The first half of the range uses a fast delete by PK. It stops
     * just before the first data_id of any batch with a not-OK status or the first data_id of any data gaps. The second half of the range uses a slower delete
     * that checks that the row is routed in sym_data_event and has a batch with an OK status.
     * 
     * @param time
     *            Calendar date/time of retention cutoff
     * @return number of rows deleted
     */
    private long purgeDataRows(OutgoingContext context) {
        long[] minMax = { context.getMinDataId(), context.getMaxDataId() };
        long minGapStartId = context.getMinDataGapStartId();
        int maxNumOfDataIdsToPurgeInTx = parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_DATA_IDS);
        long dataDeletedCount = 0;
        if (parameterService.is(ParameterConstants.PURGE_FIRST_PASS)) {
            log.info("Getting count of outstanding batches");
            long outstandingCount = sqlTemplateDirty.queryForLong(getSql("countOutgoingBatchNotStatusSql"),
                    OutgoingBatch.Status.OK.name());
            long maxOutstandingCount = parameterService.getLong(ParameterConstants.PURGE_FIRST_PASS_OUTSTANDING_BATCHES_THRESHOLD);
            if (outstandingCount > 0) {
                log.info("Found " + outstandingCount + " outstanding batches, threshold is " + maxOutstandingCount);
            }
            if (outstandingCount <= maxOutstandingCount) {
                long minDataId = 0;
                if (outstandingCount > 0) {
                    log.info("Getting first data_id for outstanding batches");
                    minDataId = sqlTemplateDirty.queryForLong(getSql("selectDataEventMinNotStatusSql"),
                            OutgoingBatch.Status.OK.name());
                }
                long rangeMinMax[] = new long[] { minMax[0], Math.min(Math.min(minDataId > 0 ? minDataId - 1 : minMax[1],
                        minMax[1]), minGapStartId - 1) };
                if (rangeMinMax[1] == minMax[1]) {
                    minMax[1] = -1;
                } else if (rangeMinMax[1] == minDataId - 1) {
                    minMax[0] = minDataId;
                } else if (rangeMinMax[1] == minGapStartId - 1) {
                    minMax[0] = minGapStartId;
                }
                dataDeletedCount += purgeByMinMax(rangeMinMax, MinMaxDeleteSql.DATA_RANGE, context, maxNumOfDataIdsToPurgeInTx,
                        count -> statisticManager.incrementPurgedDataRows(count));
            }
        }
        dataDeletedCount += purgeByMinMax(minMax, MinMaxDeleteSql.DATA, context, maxNumOfDataIdsToPurgeInTx,
                count -> statisticManager.incrementPurgedDataRows(count));
        return dataDeletedCount;
    }

    /**
     * Purge old rows from sym_data_event for batch_ids that are less than min batch_id in sym_outgoing_batch. Stranded rows in sym_data start at the min
     * data_id and end at the lesser of the min data_id in sym_data_event or sym_data_gap. If an expired data gap is within stranded range, then it is repaired
     * before purging. Old expired data gaps are purged. Old stranded data is purged from sym_data, being careful to step around expired data gaps.
     * 
     * @param time
     *            Calendar date/time of retention cutoff
     * @return number of rows deleted
     */
    private long purgeStranded(OutgoingContext context) {
        log.info("Getting range for stranded data events");
        int maxNumOfDataEventsToPurgeInTx = parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_EVENT_BATCH_IDS);
        long minGapStartId = context.getMinDataGapStartId();
        long[] minMaxEvent = queryForMinMax(sqlTemplateDirty, getSql("selectStrandedDataEventRangeSql"), null, null);
        int strandedEventDeletedCount = purgeByMinMax(minMaxEvent, MinMaxDeleteSql.STRANDED_DATA_EVENT, context, maxNumOfDataEventsToPurgeInTx,
                count -> statisticManager.incrementPurgedStrandedDataEventRows(count));
        log.info("Getting range for stranded data");
        int maxNumOfDataIdsToPurgeInTx = parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_DATA_IDS);
        long minDataId = sqlTemplateDirty.queryForLong(getSql("minDataId"));
        long minDataEventId = sqlTemplateDirty.queryForLong(getSql("minDataEventId"));
        long[] minMax = new long[] { minDataId, Math.min(minDataEventId, minGapStartId) - 1 };
        int strandedDeletedCount = purgeByMinMax(minMax, MinMaxDeleteSql.STRANDED_DATA, context, maxNumOfDataIdsToPurgeInTx,
                count -> statisticManager.incrementPurgedStrandedDataRows(count));
        return strandedEventDeletedCount + strandedDeletedCount;
    }

    private int purgeDataGapsExpired(OutgoingContext context) {
        int purgedDataRowCount = 0;
        List<DataGap> dataGapsExpired = context.getDataGapsExpired();
        List<DataGap> dataGapsExpiredToCheck = getDataGapsExpiredToCheck(context);
        if (dataGapsExpiredToCheck.size() > 0) {
            log.info("Looking for data in {} expired gaps", dataGapsExpiredToCheck.size());
            int purgedDataGapCount = 0, checkedDataGapCount = 0;
            long ts = System.currentTimeMillis();
            int[] argTypes = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() };
            for (DataGap gap : dataGapsExpiredToCheck) {
                Object[] args = new Object[] { gap.getStartId(), gap.getEndId() };
                if (parameterService.is(ParameterConstants.PURGE_STRANDED_DATA_RECAPTURE_ENABLED)) {
                    int recapturedRowCount = dataService.reCaptureData(gap.getStartId(), gap.getEndId());
                    if (log.isDebugEnabled()) {
                        log.debug("Recaptured {} rows of stranded data for gap {} - {}", recapturedRowCount, gap.getStartId(), gap.getEndId());
                    }
                } else if (log.isDebugEnabled()) {
                    log.debug("Skipped recapture of stranded data for gap {} - {}", gap.getStartId(), gap.getEndId());
                }
                int count = sqlTemplate.update(getSql("deleteDataByRangeSql"), args, argTypes);
                purgedDataRowCount += count;
                statisticManager.incrementPurgedExpiredDataRows(count);
                purgedDataGapCount++;
                checkedDataGapCount++;
                if (System.currentTimeMillis() - ts > DateUtils.MILLIS_PER_MINUTE) {
                    log.info("Checked {} expired data gaps. Deleted {} data rows.", checkedDataGapCount, count);
                    ts = System.currentTimeMillis();
                }
            }
            if (purgedDataRowCount > 0) {
                log.info("Repaired {} data in {} expired data gaps", purgedDataRowCount, purgedDataGapCount);
            }
        }
        if (dataGapsExpired.size() > 0) {
            Calendar gapRetentionCutoff = Calendar.getInstance();
            gapRetentionCutoff.add(Calendar.MINUTE, -parameterService.getInt(ParameterConstants.PURGE_EXPIRED_DATA_GAP_RETENTION_MINUTES));
            log.info("Purging expired data gaps that are older than {}", fastFormat.format(gapRetentionCutoff.getTime()));
            int deletedCount = sqlTemplate.update(getSql("deleteFromDataGapsSql"), gapRetentionCutoff.getTime(), context.getMaxDataId());
            if (deletedCount > 0) {
                log.info("Purged {} expired data gaps", deletedCount);
            }
        }
        return purgedDataRowCount;
    }

    private List<DataGap> getDataGapsExpiredToCheck(OutgoingContext context) {
        List<DataGap> dataGapsExpired = context.getDataGapsExpired();
        List<DataGap> dataGapsExpiredToCheck = dataGapsExpired;
        int maxDataGapsExpired = parameterService.getInt(ParameterConstants.PURGE_MAX_EXPIRED_DATA_GAPS_READ, 100);
        if (dataGapsExpired.size() > maxDataGapsExpired) {
            log.info("Getting range for expired data");
            long[] minMax = queryForMinMax(sqlTemplate, getSql("selectExpiredDataRangeSql"), new Object[] { context.getMinDataGapStartId() },
                    new int[] { symmetricDialect.getSqlTypeForIds() });
            dataGapsExpiredToCheck = new ArrayList<DataGap>();
            for (DataGap gap : dataGapsExpired) {
                if (gap.getStartId() > minMax[1]) {
                    break;
                } else if (gap.getEndId() >= minMax[0]) {
                    dataGapsExpiredToCheck.add(gap);
                }
            }
        }
        return dataGapsExpiredToCheck;
    }

    private long[] queryForMinMax(ISqlTemplate template, String sql, Object[] args, int[] types) {
        long[] minMax = new long[] { 0, 0 };
        List<Row> rows = template.query(sql, args, types);
        for (Row row : rows) {
            minMax[0] = row.getLong("min_id");
            minMax[1] = row.getLong("max_id");
        }
        return minMax;
    }

    private long purgeExtractRequests() {
        Calendar retentionCutoff = Calendar.getInstance();
        retentionCutoff.add(Calendar.MINUTE, -parameterService.getInt(ParameterConstants.PURGE_EXTRACT_REQUESTS_RETENTION_MINUTES));
        log.info("Purging table reload statuses that are older than {}", fastFormat.format(retentionCutoff.getTime()));
        long count = sqlTemplate.update(getSql("deleteTableReloadStatusSql"), retentionCutoff.getTime());
        if (count > 0) {
            log.info("Purged {} table reload statuses", count);
        }
        log.info("Purging table reload requests that are older than {}", fastFormat.format(retentionCutoff.getTime()));
        count = sqlTemplate.update(getSql("deleteTableReloadRequestSql"), retentionCutoff.getTime());
        if (count > 0) {
            log.info("Purged {} table reload requests", count);
        }
        log.info("Purging extract requests that are older than {}", fastFormat.format(retentionCutoff.getTime()));
        count = sqlTemplate.update(getSql("deleteExtractRequestSql"), ExtractRequest.ExtractStatus.OK.name(), retentionCutoff.getTime());
        if (count > 0) {
            log.info("Purged {} extract requests", count);
        }
        return count;
    }

    private long purgeNodeHost() {
        Calendar retentionCutoff = Calendar.getInstance();
        retentionCutoff.add(Calendar.MINUTE, -parameterService.getInt(ParameterConstants.PURGE_NODE_HOST_RETENTION_MINUTES, MINS_IN_60_DAYS));
        long count = sqlTemplate.update(getSql("purgeNodeHostSql"), retentionCutoff.getTime());
        if (count > 0) {
            log.info("Purged {} node host rows older than {}", count, fastFormat.format(retentionCutoff.getTime()));
        }
        return count;
    }

    private long purgeRegistrationRequests() {
        Calendar retentionCutoff = Calendar.getInstance();
        retentionCutoff.add(Calendar.MINUTE, -parameterService
                .getInt(ParameterConstants.PURGE_REGISTRATION_REQUEST_RETENTION_MINUTES));
        log.info("Purging registration requests that are older than {}", fastFormat.format(retentionCutoff.getTime()));
        long count = sqlTemplate.update(getSql("deleteRegistrationRequestSql"),
                RegistrationRequest.RegistrationStatus.OK.name(),
                RegistrationRequest.RegistrationStatus.RJ.name(),
                RegistrationRequest.RegistrationStatus.RR.name(), retentionCutoff.getTime());
        if (count > 0) {
            log.info("Purged {} registration requests", count);
        }
        return count;
    }

    private long purgeTriggerHist() {
        Calendar retentionCutoff = Calendar.getInstance();
        retentionCutoff.add(Calendar.MINUTE, -parameterService.getInt(ParameterConstants.PURGE_TRIGGER_HIST_RETENTION_MINUTES));
        Date retentionCutoffDate = retentionCutoff.getTime();
        Timestamp minDataCreateTime = sqlTemplateDirty.queryForObject(getSql("minDataCreateTime"), Timestamp.class);
        if (minDataCreateTime != null && minDataCreateTime.before(retentionCutoffDate)) {
            log.warn("Skipping inactive trigger histories created between {} and {} because of a backlog of captured data",
                    fastFormat.format(minDataCreateTime), fastFormat.format(retentionCutoffDate));
            retentionCutoffDate = minDataCreateTime;
        }
        if (log.isInfoEnabled()) {
            log.info("Purging trigger histories that are inactive, older than {}, and don't have a build reason of N",
                    fastFormat.format(retentionCutoffDate));
        }
        long count = sqlTemplate.update(getSql("deleteInactiveTriggerHistSql"), retentionCutoffDate);
        if (count > 0) {
            log.info("Purged {} trigger histories", count);
        }
        return count;
    }

    private int purgeByMinMax(long[] minMax, MinMaxDeleteSql identifier, OutgoingContext context, int maxNumtoPurgeinTx, LongConsumer statConsumer) {
        long minId = minMax[0];
        long maxId = 0;
        long purgeUpToId = minMax[1];
        long ts = System.currentTimeMillis();
        int totalCount = 0;
        int totalDeleteStmts = 0;
        int idSqlType = symmetricDialect.getSqlTypeForIds();
        Timestamp cutoffTime = new Timestamp(context.getRetentionCutoff().getTime().getTime());
        identifier = getIdentifierIfUsingExists(identifier);
        String name = getIdentifierName(identifier);
        if (minMax[0] > minMax[1] || minMax[0] <= 0) {
            log.debug("Ending purge early for {} using range {} through {}", name, minMax[0], minMax[1]);
            return 0;
        }
        List<DataGap> dataGapsExpired = new ArrayList<DataGap>(context.getDataGapsExpired());
        log.info("About to purge {} using range {} through {}", name, minMax[0], minMax[1]);
        while (minId <= purgeUpToId) {
            totalDeleteStmts++;
            maxId = minId + maxNumtoPurgeinTx;
            if (maxId > purgeUpToId) {
                maxId = purgeUpToId;
            }
            String deleteSql = null;
            Object[] args = null;
            int[] argTypes = null;
            switch (identifier) {
                case DATA:
                    deleteSql = getSql("deleteDataSql");
                    args = new Object[] { minId, maxId, minId, maxId, minId, maxId, OutgoingBatch.Status.OK.name() };
                    argTypes = new int[] { idSqlType, idSqlType, idSqlType, idSqlType, idSqlType, idSqlType, Types.VARCHAR };
                    break;
                case DATA_EXISTS:
                    deleteSql = getSql("deleteDataExistsSql");
                    args = new Object[] { minId, maxId, OutgoingBatch.Status.OK.name() };
                    argTypes = new int[] { idSqlType, idSqlType, Types.VARCHAR };
                    break;
                case DATA_RANGE:
                    deleteSql = getSql("deleteDataByRangeSql");
                    long[] minMaxAvoidGaps = getMinMaxAvoidGaps(minId, maxId, dataGapsExpired);
                    if (minMaxAvoidGaps[1] < 0) {
                        minId = -minMaxAvoidGaps[1];
                        continue;
                    }
                    args = new Object[] { minId = minMaxAvoidGaps[0], maxId = minMaxAvoidGaps[1] };
                    argTypes = new int[] { idSqlType, idSqlType };
                    break;
                case DATA_EVENT:
                    deleteSql = getSql("deleteDataEventSql");
                    args = new Object[] { minId, maxId, OutgoingBatch.Status.OK.name(), minId, maxId };
                    argTypes = new int[] { idSqlType, idSqlType, Types.VARCHAR, idSqlType, idSqlType };
                    break;
                case DATA_EVENT_EXISTS:
                    deleteSql = getSql("deleteDataEventExistsSql");
                    args = new Object[] { minId, maxId, OutgoingBatch.Status.OK.name() };
                    argTypes = new int[] { idSqlType, idSqlType, Types.VARCHAR };
                    break;
                case DATA_EVENT_RANGE:
                    deleteSql = getSql("deleteDataEventByRangeSql");
                    args = new Object[] { minId, maxId };
                    argTypes = new int[] { idSqlType, idSqlType };
                    break;
                case OUTGOING_BATCH:
                    deleteSql = getSql("deleteOutgoingBatchSql");
                    args = new Object[] { OutgoingBatch.Status.OK.name(), minId, maxId, minId, maxId };
                    argTypes = new int[] { Types.VARCHAR, idSqlType, idSqlType, idSqlType, idSqlType };
                    break;
                case OUTGOING_BATCH_EXISTS:
                    deleteSql = getSql("deleteOutgoingBatchExistsSql");
                    args = new Object[] { OutgoingBatch.Status.OK.name(), minId, maxId };
                    argTypes = new int[] { Types.VARCHAR, idSqlType, idSqlType };
                    break;
                case OUTGOING_BATCH_RANGE:
                    deleteSql = getSql("deleteOutgoingBatchByRangeSql");
                    args = new Object[] { minId, maxId };
                    argTypes = new int[] { idSqlType, idSqlType };
                    break;
                case STRANDED_DATA:
                    deleteSql = getSql("deleteStrandedData");
                    minMaxAvoidGaps = getMinMaxAvoidGaps(minId, maxId, dataGapsExpired);
                    if (minMaxAvoidGaps[1] < 0) {
                        minId = -minMaxAvoidGaps[1];
                        continue;
                    }
                    args = new Object[] { minId = minMaxAvoidGaps[0], maxId = minMaxAvoidGaps[1], cutoffTime };
                    argTypes = new int[] { idSqlType, idSqlType, Types.TIMESTAMP };
                    if (parameterService.is(ParameterConstants.PURGE_STRANDED_DATA_RECAPTURE_ENABLED)) {
                        int recapturedRowCount = dataService.reCaptureData(minId, maxId);
                        log.debug("Recaptured {} stranded data rows for range {} - {}", recapturedRowCount, minId, maxId);
                    } else if (log.isDebugEnabled()) {
                        log.debug("Skipped recapture of stranded data for range {} - {}", minId, maxId);
                    }
                    break;
                case STRANDED_DATA_EVENT:
                    deleteSql = getSql("deleteStrandedDataEvent");
                    args = new Object[] { minId, maxId, cutoffTime };
                    argTypes = new int[] { idSqlType, idSqlType, Types.TIMESTAMP };
                    break;
            }
            log.debug("Running the following statement: {} with the following arguments: {}", deleteSql, Arrays.toString(args));
            int count = sqlTemplate.update(deleteSql, args, argTypes);
            log.debug("Deleted {} rows", count);
            statConsumer.accept(count);
            totalCount += count;
            long currentRunTimeMs = System.currentTimeMillis() - ts;
            if (count == 0 && (identifier == MinMaxDeleteSql.STRANDED_DATA || identifier == MinMaxDeleteSql.STRANDED_DATA_EVENT)) {
                long runtimeLimit = parameterService.getLong(ParameterConstants.PURGE_STRANDED_DATA_TIME_LIMIT_MS);
                if (runtimeLimit > 0 && currentRunTimeMs >= runtimeLimit) {
                    log.info("Ending purge of {} early at {} after finding empty space. Total rows purged={}", name, maxId, totalCount);
                    break;
                } else {
                    if (currentRunTimeMs > DateUtils.MILLIS_PER_MINUTE * 5) {
                        log.info("Skipping empty space in {} for range {} - {}", name, minId, maxId);
                    } else {
                        log.debug("Skipping empty space in {} for range {} - {}. Total rows purged={}", name, minId, maxId, totalCount);
                    }
                }
            }
            if (currentRunTimeMs > DateUtils.MILLIS_PER_MINUTE * 5) {
                log.info("Purged {} of {} rows so far using {} statements", new Object[] { totalCount, name, totalDeleteStmts });
                ts = System.currentTimeMillis();
                clusterService.refreshLock(ClusterConstants.PURGE_OUTGOING);
                saveContextLastId(identifier, maxId);
            }
            minId = maxId + 1;
        }
        saveContextLastId(identifier, maxId);
        log.info("Done purging {} of {} rows", totalCount, name);
        return totalCount;
    }

    protected MinMaxDeleteSql getIdentifierIfUsingExists(MinMaxDeleteSql identifier) {
        if (symmetricDialect.getPlatform().getDdlBuilder().getDatabaseInfo().canDeleteUsingExists()) {
            if (identifier == MinMaxDeleteSql.DATA) {
                identifier = MinMaxDeleteSql.DATA_EXISTS;
            } else if (identifier == MinMaxDeleteSql.DATA_EVENT) {
                identifier = MinMaxDeleteSql.DATA_EVENT_EXISTS;
            } else if (identifier == MinMaxDeleteSql.OUTGOING_BATCH) {
                identifier = MinMaxDeleteSql.OUTGOING_BATCH_EXISTS;
            }
        }
        return identifier;
    }

    protected String getIdentifierName(MinMaxDeleteSql identifier) {
        return identifier.toString().toLowerCase().replaceAll("_exists", "");
    }

    protected void saveContextLastId(MinMaxDeleteSql identifier, long lastId) {
        if (lastId > 0) {
            if (identifier == MinMaxDeleteSql.DATA || identifier == MinMaxDeleteSql.DATA_EXISTS || identifier == MinMaxDeleteSql.DATA_RANGE) {
                contextService.save(ContextConstants.PURGE_LAST_DATA_ID, String.valueOf(lastId));
            } else if (identifier == MinMaxDeleteSql.DATA_EVENT || identifier == MinMaxDeleteSql.DATA_EVENT_EXISTS
                    || identifier == MinMaxDeleteSql.DATA_EVENT_RANGE) {
                contextService.save(ContextConstants.PURGE_LAST_EVENT_BATCH_ID, String.valueOf(lastId));
            } else if (identifier == MinMaxDeleteSql.OUTGOING_BATCH || identifier == MinMaxDeleteSql.OUTGOING_BATCH_EXISTS
                    || identifier == MinMaxDeleteSql.OUTGOING_BATCH_RANGE) {
                contextService.save(ContextConstants.PURGE_LAST_BATCH_ID, String.valueOf(lastId));
            }
        }
    }

    /**
     * Takes a range of data_ids that are about to be purged, and modifies the range to go around any expired data gaps that are being watched for data to be
     * committed. This allows statistics to be mutually exclusive for purged rows from expired data gaps and purged rows from stranded data.
     * 
     * @param minId
     *            Min data ID of range
     * @param maxId
     *            Max data ID of range
     * @param dataGapsExpired
     *            List of expired data gaps to avoid
     * @return
     */
    public static long[] getMinMaxAvoidGaps(long minId, long maxId, List<DataGap> dataGapsExpired) {
        if (dataGapsExpired.size() > 0) {
            Iterator<DataGap> iter = dataGapsExpired.iterator();
            while (iter.hasNext()) {
                DataGap gap = iter.next();
                if (maxId < gap.getStartId()) {
                    break;
                } else if (minId > gap.getEndId()) {
                    iter.remove();
                } else if (minId < gap.getStartId()) {
                    // range is half inside or completely covering the gap
                    maxId = gap.getStartId() - 1;
                    break;
                } else {
                    // range is half outside the gap
                    minId = gap.getEndId() + 1;
                    // range is completely inside the gap
                    if (maxId <= gap.getEndId()) {
                        maxId = -(gap.getEndId() + 1);
                        break;
                    }
                    iter.remove();
                }
            }
        }
        return new long[] { minId, maxId };
    }

    @Override
    public long purgeIncoming(Calendar retentionCutoff, boolean force) {
        long purgedRowCount = 0;
        long startTime = System.currentTimeMillis();
        try {
            if (force || clusterService.lock(ClusterConstants.PURGE_INCOMING)) {
                try {
                    log.info("The incoming purge process is about to run");
                    purgedRowCount += purgeNodeHost();
                    List<IPurgeListener> purgeListeners = extensionService.getExtensionPointList(IPurgeListener.class);
                    for (IPurgeListener purgeListener : purgeListeners) {
                        try {
                            purgedRowCount += purgeListener.beforePurgeIncoming(force);
                        } catch (Throwable e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                    purgedRowCount = purgeIncomingBatch(retentionCutoff);
                    purgedRowCount += purgeIncomingError();
                    purgedRowCount += purgeRegistrationRequests();
                    purgedRowCount += purgeTriggerHist();
                    for (IPurgeListener purgeListener : purgeListeners) {
                        try {
                            purgedRowCount += purgeListener.purgeIncoming(force);
                        } catch (Throwable e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                    statisticManager.addJobStats(ClusterConstants.PURGE_INCOMING, startTime, System.currentTimeMillis(), purgedRowCount);
                } finally {
                    if (!force) {
                        clusterService.unlock(ClusterConstants.PURGE_INCOMING);
                    }
                    log.info("The incoming purge process has completed");
                }
            } else {
                log.debug("Could not get a lock to run an incoming purge");
            }
        } catch (Exception ex) {
            log.error("", ex);
            statisticManager.addJobStats(ClusterConstants.PURGE_INCOMING, startTime, System.currentTimeMillis(), purgedRowCount, ex);
        }
        return purgedRowCount;
    }

    private long purgeIncomingError() {
        log.info("Purging incoming_error table");
        long rowCount = 0;
        if (getSymmetricDialect().supportsSubselectsInDelete()) {
            rowCount = sqlTemplate.update(getSql("deleteIncomingErrorsSql"));
        } else {
            rowCount = selectIdsAndDelete(getSql("selectIncomingErrorsBatchIdsSql"),
                    "batch_id", getSql("deleteIncomingErrorsBatchIdsSql"));
        }
        log.info("Purged {} rows from incoming_error", rowCount);
        return rowCount;
    }

    private long purgeIncomingBatch(final Calendar time) {
        log.info("Getting range for incoming batch");
        List<NodeBatchRange> nodeBatchRangeList = sqlTemplateDirty.query(
                getSql("selectIncomingBatchRangeSql"), new ISqlRowMapper<NodeBatchRange>() {
                    @Override
                    public NodeBatchRange mapRow(Row rs) {
                        return new NodeBatchRange(rs.getString("node_id"), rs.getLong("min_id"), rs
                                .getLong("max_id"));
                    }
                }, time.getTime(), IncomingBatch.Status.OK.name());
        return purgeByNodeBatchRangeList(nodeBatchRangeList);
    }

    private int purgeByNodeBatchRangeList(List<NodeBatchRange> nodeBatchRangeList) {
        long ts = System.currentTimeMillis();
        int totalCount = 0;
        int totalDeleteStmts = 0;
        log.info("About to purge incoming batch");
        for (NodeBatchRange nodeBatchRange : nodeBatchRangeList) {
            int maxNumOfDataIdsToPurgeInTx = parameterService
                    .getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_BATCH_IDS);
            long minBatchId = nodeBatchRange.getMinBatchId();
            long purgeUpToBatchId = nodeBatchRange.getMaxBatchId();
            while (minBatchId <= purgeUpToBatchId) {
                totalDeleteStmts++;
                long maxBatchId = minBatchId + maxNumOfDataIdsToPurgeInTx;
                if (maxBatchId > purgeUpToBatchId) {
                    maxBatchId = purgeUpToBatchId;
                }
                int rowCount = sqlTemplate.update(getSql("deleteIncomingBatchSql"),
                        new Object[] { minBatchId, maxBatchId, nodeBatchRange.getNodeId(),
                                IncomingBatch.Status.OK.name() });
                minBatchId = maxBatchId + 1;
                totalCount += rowCount;
                statisticManager.incrementPurgedBatchIncomingRows(rowCount);
            }
            if (totalCount > 0
                    && (System.currentTimeMillis() - ts > DateUtils.MILLIS_PER_MINUTE * 5)) {
                log.info("Purged {} incoming batch rows so far using {} statements", new Object[] {
                        totalCount, totalDeleteStmts });
                ts = System.currentTimeMillis();
            }
        }
        log.info("Done purging {} incoming batch rows", totalCount);
        return totalCount;
    }

    @Override
    public void purgeStats(boolean force) {
        Calendar retentionCutoff = Calendar.getInstance();
        retentionCutoff.add(Calendar.MINUTE, -Integer.max(parameterService.getInt(ParameterConstants.PURGE_STATS_RETENTION_MINUTES), MINS_IN_ONE_WEEK));
        if (force || clusterService.lock(ClusterConstants.PURGE_STATISTICS)) {
            try {
                int purgedCount = sqlTemplate.update(getSql("purgeNodeHostChannelStatsSql"),
                        retentionCutoff.getTime());
                purgedCount += sqlTemplate.update(getSql("purgeNodeHostStatsSql"),
                        retentionCutoff.getTime());
                purgedCount += sqlTemplate.update(getSql("purgeNodeHostJobStatsSql"),
                        retentionCutoff.getTime());
                if (purgedCount > 0) {
                    log.debug("{} stats rows were purged", purgedCount);
                }
            } finally {
                if (!force) {
                    clusterService.unlock(ClusterConstants.PURGE_STATISTICS);
                }
            }
        }
    }

    @Override
    public void purgeAllIncomingEventsForNode(String nodeId) {
        int count = sqlTemplate.update(getSql("deleteIncomingBatchByNodeSql"),
                new Object[] { nodeId });
        log.info("Purged all {} incoming batch for node {}", count, nodeId);
    }

    protected int selectIdsAndDelete(String selectSql, String fieldName, String deleteSql) {
        List<Row> results = sqlTemplate.query(selectSql);
        int rowCount = 0;
        if (!results.isEmpty()) {
            List<Integer> ids = new ArrayList<Integer>(results.size());
            for (Row row : results) {
                ids.add(row.getInt(fieldName));
            }
            results = null;
            StringBuilder placeHolders = new StringBuilder(ids.size() * 2);
            for (int i = 0; i < ids.size(); i++) {
                placeHolders.append("?,");
            }
            placeHolders.setLength(placeHolders.length() - 1);
            String deleteStatement = deleteSql.replace("?", placeHolders);
            rowCount = sqlTemplate.update(deleteStatement, ids.toArray());
        }
        return rowCount;
    }

    /**
     * Builds context for various purge routines, including the range for eligible data_ids and batch_ids. It uses the largest data_id and batch_id together
     * from the newest batch that is eligible for purge on any channel.
     * 
     * @param time
     *            Calendar date/time of retention cutoff
     * @return max data_idp
     */
    private OutgoingContext buildOutgoingContext(Calendar retentionCutoff) {
        log.info("Getting ranges for purge");
        OutgoingContext context = new OutgoingContext(retentionCutoff);
        long startDataId = contextService.getLong(ContextConstants.PURGE_LAST_DATA_ID) + 1;
        if (startDataId == 1) {
            startDataId = sqlTemplateDirty.queryForLong(getSql("minDataId"));
        }
        context.setMinDataId(startDataId);
        long startBatchId = contextService.getLong(ContextConstants.PURGE_LAST_BATCH_ID) + 1;
        long startEventBatchId = contextService.getLong(ContextConstants.PURGE_LAST_EVENT_BATCH_ID) + 1;
        if (startBatchId == 1 || startEventBatchId == 1) {
            long minBatchId = sqlTemplateDirty.queryForLong(getSql("minOutgoingBatchId"));
            startBatchId = Math.max(startBatchId, minBatchId);
            startEventBatchId = Math.max(startEventBatchId, minBatchId);
        }
        context.setMinBatchId(startBatchId);
        context.setMinEventBatchId(startEventBatchId);
        // Leave 1 batch and its data around so MySQL auto increment doesn't reset
        long endBatchId = sequenceService.currVal(Constants.SEQUENCE_OUTGOING_BATCH) - 1;
        List<Long> batchIds = sqlTemplateDirty.query(getSql("maxBatchIdForOldBatches"), new LongMapper(),
                new Object[] { startBatchId, endBatchId, new Timestamp(context.getRetentionCutoff().getTime().getTime()) },
                new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds(), Types.TIMESTAMP });
        if (batchIds != null && batchIds.size() > 0) {
            context.setMaxBatchId(batchIds.get(0));
            log.info("Max eligible batch ID: {}", context.getMaxBatchId());
            List<Row> rows = sqlTemplateDirty.query(getSql("minMaxDataIdForOldBatches"), new Object[] { startBatchId,
                    context.getMaxBatchId() }, new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() });
            if (rows != null && rows.size() > 0) {
                Row row = rows.get(0);
                long minDataId = row.getLong("min_data_id");
                long maxDataId = row.getLong("max_data_id");
                context.setMaxDataId(maxDataId);
                log.info("Max eligible data ID: {}", context.getMaxDataId());
                if (minDataId < context.getMinDataId()) {
                    log.info("Moving starting data ID back from {} to {}", context.getMinDataId(), minDataId);
                    context.setMinDataId(minDataId);
                }
            }
        }
        context.setMinDataGapStartId(sqlTemplateDirty.queryForLong(getSql("minDataGapStartId")));
        context.setDataGapsExpired(dataService.findDataGapsExpired());
        if (context.getMinBatchId() == context.getMinEventBatchId()) {
            log.info("Eligible ranges: outgoing batch [{} - {}], data [{} - {}], first data gap [{}]", context.getMinBatchId(), context.getMaxBatchId(),
                    context.getMinDataId(), context.getMaxDataId(), context.getMinDataGapStartId());
        } else {
            log.info("Eligible ranges: outgoing batch [{} - {}], data event [{} - {}], data [{} - {}], first data gap [{}]", context.getMinBatchId(),
                    context.getMaxBatchId(), context.getMinEventBatchId(), context.getMaxBatchId(), context.getMinDataId(), context.getMaxDataId(),
                    context.getMinDataGapStartId());
        }
        return context;
    }

    static class OutgoingContext {
        private Calendar retentionCutoff;
        private long minDataGapStartId;
        private long minDataId;
        private long maxDataId;
        private long minBatchId;
        private long maxBatchId;
        private long minEventBatchId;
        private List<DataGap> dataGapsExpired;

        public OutgoingContext(Calendar retentionCutoff) {
            this.retentionCutoff = retentionCutoff;
        }

        public Calendar getRetentionCutoff() {
            return retentionCutoff;
        }

        public void setRetentionCutoff(Calendar retentionCutoff) {
            this.retentionCutoff = retentionCutoff;
        }

        public long getMinDataGapStartId() {
            return minDataGapStartId;
        }

        public void setMinDataGapStartId(long minDataGapStartId) {
            this.minDataGapStartId = minDataGapStartId;
        }

        public long getMinDataId() {
            return minDataId;
        }

        public void setMinDataId(long minDataId) {
            this.minDataId = minDataId;
        }

        public long getMaxDataId() {
            return maxDataId;
        }

        public long getMinBatchId() {
            return minBatchId;
        }

        public void setMinBatchId(long minBatchId) {
            this.minBatchId = minBatchId;
        }

        public void setMaxDataId(long maxDataId) {
            this.maxDataId = maxDataId;
        }

        public long getMaxBatchId() {
            return maxBatchId;
        }

        public void setMaxBatchId(long maxBatchId) {
            this.maxBatchId = maxBatchId;
        }

        public long getMinEventBatchId() {
            return minEventBatchId;
        }

        public void setMinEventBatchId(long minEventBatchId) {
            this.minEventBatchId = minEventBatchId;
        }

        public List<DataGap> getDataGapsExpired() {
            return dataGapsExpired;
        }

        public void setDataGapsExpired(List<DataGap> dataGapsExpired) {
            this.dataGapsExpired = dataGapsExpired;
        }
    }

    static class NodeBatchRange {
        private String nodeId;
        private long minBatchId;
        private long maxBatchId;

        public NodeBatchRange(String nodeId, long minBatchId, long maxBatchId) {
            this.nodeId = nodeId;
            this.minBatchId = minBatchId;
            this.maxBatchId = maxBatchId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public long getMaxBatchId() {
            return maxBatchId;
        }

        public long getMinBatchId() {
            return minBatchId;
        }
    }
}
