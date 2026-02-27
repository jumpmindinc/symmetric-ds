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
package org.jumpmind.symmetric.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for managing gaps in data ids to ensure that all captured data is routed for delivery to other nodes.
 */
public class DataGapFastDetector extends DataGapDetector implements ISqlRowMapper<Long> {
    private static final Logger log = LoggerFactory.getLogger(DataGapFastDetector.class);
    protected IContextService contextService;
    protected IClusterService clusterService;
    protected List<DataGap> gaps;
    protected DataGap lastGap;
    protected List<Long> dataIds;
    protected boolean isAllDataRead = true;
    protected long maxDataToSelect;
    protected boolean isFullGapAnalysis = true;
    protected long lastBusyExpireRunTime;
    protected Set<DataGap> gapsAll;
    protected Set<DataGap> gapsAdded;
    protected Set<DataGap> gapsDeleted;
    protected Set<DataGap> gapsExpired;
    protected boolean detectInvalidGaps;
    protected boolean useInMemoryGaps;
    protected long routingStartTime;
    protected long earliestTransactionTime = 0;
    protected boolean supportsTransactionViews;
    protected static Map<String, Boolean> firstTime = Collections.synchronizedMap(new HashMap<String, Boolean>());

    public DataGapFastDetector(IDataService dataService, IParameterService parameterService, IContextService contextService,
            ISymmetricDialect symmetricDialect, IRouterService routerService, IStatisticManager statisticManager, INodeService nodeService,
            IClusterService clusterService) {
        this.dataService = dataService;
        this.parameterService = parameterService;
        this.contextService = contextService;
        this.routerService = routerService;
        this.symmetricDialect = symmetricDialect;
        this.statisticManager = statisticManager;
        this.nodeService = nodeService;
        this.clusterService = clusterService;
    }

    @Override
    public void beforeRouting() {
        ProcessInfo processInfo = this.statisticManager.newProcessInfo(new ProcessInfoKey(
                nodeService.findIdentityNodeId(), null, ProcessType.GAP_DETECT));
        processInfo.setStatus(ProcessStatus.QUERYING);
        try {
            maxDataToSelect = parameterService.getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE);
            detectInvalidGaps = parameterService.is(ParameterConstants.ROUTING_DETECT_INVALID_GAPS) || firstTime.get(parameterService.getEngineName()) == null;
            reset();
            if (isFullGapAnalysis()) {
                log.info("Full gap analysis is running");
                long ts = System.currentTimeMillis();
                gaps = dataService.findDataGaps();
                if (detectInvalidGaps) {
                    fixOverlappingGaps(gaps, processInfo);
                }
                queryDataIdMap();
                processInfo.setStatus(ProcessStatus.OK);
                log.info("Querying data in gaps from database took {} ms", System.currentTimeMillis() - ts);
                isAllDataRead = false;
                afterRouting();
                reset();
                log.info("Full gap analysis is done after {} ms", System.currentTimeMillis() - ts);
            } else if (gaps == null || parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
                gaps = dataService.findDataGaps();
                if (detectInvalidGaps) {
                    fixOverlappingGaps(gaps, processInfo);
                }
                processInfo.setStatus(ProcessStatus.OK);
            } else {
                processInfo.setStatus(ProcessStatus.OK);
            }
        } catch (RuntimeException e) {
            if (processInfo.getStatus() != ProcessStatus.OK) {
                processInfo.setStatus(ProcessStatus.ERROR);
            }
            throw e;
        } finally {
            firstTime.put(parameterService.getEngineName(), true);
        }
    }

    protected void reset() {
        isAllDataRead = true;
        dataIds = new ArrayList<Long>();
        gapsAll = new HashSet<DataGap>();
        gapsAdded = new HashSet<DataGap>();
        gapsDeleted = new HashSet<DataGap>();
        gapsExpired = new HashSet<DataGap>();
        routingStartTime = System.currentTimeMillis();
        supportsTransactionViews = symmetricDialect.supportsTransactionViews();
        earliestTransactionTime = 0;
        if (supportsTransactionViews) {
            Date date = symmetricDialect.getEarliestTransactionStartTime();
            if (date != null) {
                earliestTransactionTime = date.getTime() - parameterService.getLong(
                        ParameterConstants.ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS, 10000);
                log.debug("Earliest transaction time is {}", earliestTransactionTime);
            }
            routingStartTime = symmetricDialect.getDatabaseTime();
        }
    }

    protected void softReset() {
        gapsAll = new HashSet<DataGap>();
        gapsAdded = new HashSet<DataGap>();
        gapsDeleted = new HashSet<DataGap>();
    }

    /**
     * Always make sure sym_data_gap is up to date to make sure that we don't dual route data.
     */
    @Override
    public void afterRouting() {
        ProcessInfo processInfo = this.statisticManager.newProcessInfo(new ProcessInfoKey(
                nodeService.findIdentityNodeId(), null, ProcessType.GAP_DETECT));
        processInfo.setStatus(ProcessStatus.PROCESSING);
        long printStats = System.currentTimeMillis();
        long gapTimoutInMs = parameterService.getLong(ParameterConstants.ROUTING_STALE_DATA_ID_GAP_TIME);
        final int dataIdIncrementBy = parameterService.getInt(ParameterConstants.DATA_ID_INCREMENT_BY);
        Date currentDate = new Date(routingStartTime);
        boolean isBusyExpire = false;
        long lastBusyExpireRunTime = getLastBusyExpireRunTime();
        if (!isAllDataRead) {
            if (lastBusyExpireRunTime == 0) {
                setLastBusyExpireRunTime(System.currentTimeMillis());
            } else {
                long busyExpireMillis = parameterService.getLong(ParameterConstants.ROUTING_STALE_GAP_BUSY_EXPIRE_TIME);
                isBusyExpire = System.currentTimeMillis() - lastBusyExpireRunTime >= busyExpireMillis;
            }
        } else if (lastBusyExpireRunTime != 0) {
            setLastBusyExpireRunTime(0);
        }
        try {
            long ts = System.currentTimeMillis();
            long lastDataId = -1;
            int dataIdCount = 0;
            int rangeChecked = 0;
            int expireChecked = 0;
            gapsAll.addAll(gaps);
            Map<DataGap, List<Long>> dataIdMap = getDataIdMap();
            if (System.currentTimeMillis() - ts > 30000) {
                log.info("It took {}ms to map {} data IDs into {} gaps", new Object[] { System.currentTimeMillis() - ts,
                        dataIds.size(), gaps.size() });
            }
            for (final DataGap dataGap : gaps) {
                final boolean lastGap = dataGap.equals(gaps.get(gaps.size() - 1));
                lastDataId = -1;
                List<Long> ids = dataIdMap.get(dataGap);
                dataIdCount += ids.size();
                rangeChecked += dataGap.getEndId() - dataGap.getStartId();
                // if we found data in the gap
                if (ids.size() > 0) {
                    gapsDeleted.add(dataGap);
                    gapsAll.remove(dataGap);
                    // if we did not find data in the gap and it was not the last gap
                } else if (!lastGap && (isAllDataRead || isBusyExpire) && isGapExpired(dataGap, gapTimoutInMs)) {
                    boolean isGapEmpty = false;
                    if (!isAllDataRead) {
                        isGapEmpty = dataService.countDataInRange(dataGap.getStartId() - 1, dataGap.getEndId() + 1) == 0;
                        expireChecked++;
                    }
                    if (isAllDataRead || isGapEmpty) {
                        gapsExpired.add(dataGap);
                        gapsAll.remove(dataGap);
                    }
                }
                for (Number number : ids) {
                    long dataId = number.longValue();
                    processInfo.incrementCurrentDataCount();
                    if (lastDataId == -1 && dataGap.getStartId() + dataIdIncrementBy <= dataId) {
                        // there was a new gap at the start
                        addDataGap(new DataGap(dataGap.getStartId(), dataId - 1, currentDate));
                    } else if (lastDataId != -1 && lastDataId + dataIdIncrementBy != dataId && lastDataId != dataId) {
                        // found a gap somewhere in the existing gap
                        addDataGap(new DataGap(lastDataId + 1, dataId - 1, currentDate));
                    }
                    lastDataId = dataId;
                }
                // if we found data in the gap
                if (lastDataId != -1 && !lastGap && lastDataId + dataIdIncrementBy <= dataGap.getEndId()) {
                    addDataGap(new DataGap(lastDataId + dataIdIncrementBy, dataGap.getEndId(), currentDate));
                }
                if (Thread.interrupted()) {
                    throw new SymmetricException("Thread received interrupt");
                }
                if (System.currentTimeMillis() - printStats > 30000) {
                    checkInterrupted();
                    clusterService.refreshLock(ClusterConstants.ROUTE);
                    log.info("The data gap detection has been running for {}ms, detected {} rows over a gap range of {}, "
                            + "found {} new gaps, found old {} gaps, and checked data in {} gaps", new Object[] { System.currentTimeMillis() - ts,
                                    dataIdCount, rangeChecked, gapsAdded.size(), gapsDeleted.size(), expireChecked });
                    printStats = System.currentTimeMillis();
                }
            }
            if (lastDataId != -1) {
                DataGap newGap = new DataGap(lastDataId + 1, lastDataId + maxDataToSelect, currentDate);
                if (addDataGap(newGap)) {
                    log.debug("Inserting new last data gap: {}", newGap);
                }
            }
            processInfo.setStatus(ProcessStatus.CREATING);
            saveDataGaps();
            if (gaps.size() > 0) {
                lastGap = gaps.get(gaps.size() - 1);
            }
            setFullGapAnalysis(false);
            if (isBusyExpire) {
                setLastBusyExpireRunTime(System.currentTimeMillis());
            }
            long updateTimeInMs = System.currentTimeMillis() - ts;
            if (updateTimeInMs > 10000) {
                log.info("Detecting gaps took {} ms", updateTimeInMs);
            }
            processInfo.setStatus(ProcessStatus.OK);
        } catch (RuntimeException ex) {
            processInfo.setStatus(ProcessStatus.ERROR);
            throw ex;
        } finally {
            logExpiredDataGaps();
            softReset();
        }
    }

    protected boolean addDataGap(DataGap dataGap) {
        boolean isOkay = true;
        if (detectInvalidGaps) {
            if (gapsAll.contains(dataGap)) {
                log.warn("Detected a duplicate data gap: " + dataGap);
                isOkay = false;
            } else if (dataGap.getStartId() > dataGap.getEndId()) {
                log.warn("Detected an invalid gap range: " + dataGap);
                isOkay = false;
            } else if (dataGap.gapSize() < maxDataToSelect - 1 && dataGap.gapSize() >= (long) (maxDataToSelect * 0.75)) {
                log.warn("Detected a very large gap range: " + dataGap);
                isOkay = false;
            }
        }
        if (isOkay) {
            gapsAdded.add(dataGap);
            gapsAll.add(dataGap);
        } else {
            printGapState();
        }
        return isOkay;
    }

    private void printGapState() {
        StringBuilder buff = new StringBuilder();
        buff.append("\nData IDs: " + dataIds).append("\n");
        buff.append("Data Gaps: " + gaps).append("\n");
        buff.append("Added Data Gaps: " + gapsAdded).append("\n");
        buff.append("Deleted Data Gaps: " + gapsDeleted).append("\n");
        log.info(buff.toString());
    }

    protected void saveDataGaps() {
        ISqlTemplate sqlTemplate = symmetricDialect.getPlatform().getSqlTemplate();
        int totalGapChanges = gapsDeleted.size() + gapsAdded.size() + gapsExpired.size();
        if (totalGapChanges > 0) {
            ISqlTransaction transaction = null;
            gaps = new ArrayList<DataGap>(gapsAll);
            Collections.sort(gaps);
            try {
                transaction = sqlTemplate.startSqlTransaction();
                int maxGapChanges = parameterService.getInt(ParameterConstants.ROUTING_MAX_GAP_CHANGES);
                if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) && (totalGapChanges > maxGapChanges || useInMemoryGaps)) {
                    dataService.deleteAllDataGaps(transaction);
                    if (useInMemoryGaps && totalGapChanges <= maxGapChanges) {
                        log.info("There are {} data gap changes, which is within the max of {}, so switching to database",
                                totalGapChanges, maxGapChanges);
                        useInMemoryGaps = false;
                        dataService.insertDataGaps(transaction, gaps);
                    } else {
                        if (!useInMemoryGaps) {
                            log.info("There are {} data gap changes, which exceeds the max of {}, so switching to in-memory",
                                    totalGapChanges, maxGapChanges);
                            useInMemoryGaps = true;
                        }
                        DataGap newGap = new DataGap(gaps.get(0).getStartId(), gaps.get(gaps.size() - 1).getEndId());
                        dataService.insertDataGap(transaction, newGap);
                    }
                } else {
                    dataService.deleteDataGaps(transaction, gapsDeleted);
                    dataService.insertDataGaps(transaction, gapsAdded);
                }
                dataService.expireDataGaps(transaction, gapsExpired);
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
                if (transaction != null) {
                    transaction.close();
                }
            }
        }
    }

    protected synchronized void queryDataIdMap() {
        String sql = routerService.getSql("selectDistinctDataIdFromDataEventUsingGapsSql");
        ISqlTemplate sqlTemplate = symmetricDialect.getPlatform().getSqlTemplate();
        for (DataGap dataGap : gaps) {
            long queryForIdsTs = System.currentTimeMillis();
            Object[] params = new Object[] { dataGap.getStartId(), dataGap.getEndId() };
            List<Long> ids = sqlTemplate.query(sql, this, params);
            dataIds.addAll(ids);
            if (System.currentTimeMillis() - queryForIdsTs > Constants.LONG_OPERATION_THRESHOLD) {
                checkInterrupted();
                clusterService.refreshLock(ClusterConstants.ROUTE);
                log.info("It took longer than {}ms to run the following sql for gap from {} to {}.  {}",
                        new Object[] { Constants.LONG_OPERATION_THRESHOLD, dataGap.getStartId(), dataGap.getEndId(), sql });
            }
        }
    }

    protected synchronized Map<DataGap, List<Long>> getDataIdMap() {
        HashMap<DataGap, List<Long>> map = new HashMap<DataGap, List<Long>>();
        Collections.sort(dataIds);
        Iterator<Long> iterator = dataIds.iterator();
        long dataId = -1;
        if (iterator.hasNext()) {
            dataId = iterator.next().longValue();
        }
        for (DataGap gap : gaps) {
            List<Long> idList = map.get(gap);
            if (idList == null) {
                idList = new ArrayList<Long>();
                map.put(gap, idList);
            }
            do {
                if (dataId < gap.getStartId()) {
                    continue;
                } else if (dataId <= gap.getEndId()) {
                    idList.add(dataId);
                } else {
                    break;
                }
            } while (iterator.hasNext() && (dataId = iterator.next().longValue()) != -1);
        }
        return map;
    }

    protected void fixOverlappingGaps(List<DataGap> gapsToCheck, ProcessInfo processInfo) {
        List<DataGap> gapsCopy = new ArrayList<DataGap>(gapsToCheck);
        boolean ok = true;
        try {
            ISqlTransaction transaction = null;
            log.debug("Looking for overlapping gaps");
            try {
                ISqlTemplate sqlTemplate = symmetricDialect.getPlatform().getSqlTemplate();
                transaction = sqlTemplate.startSqlTransaction();
                DataGap prevGap = null, lastGap = null;
                for (int i = 0; i < gapsCopy.size(); i++) {
                    DataGap curGap = gapsCopy.get(i);
                    if (lastGap != null) {
                        ok = false;
                        log.warn("Removing gap found after last gap: " + curGap);
                        dataService.deleteDataGap(transaction, curGap);
                        gapsCopy.remove(i--);
                    } else {
                        if (lastGap == null && curGap.gapSize() >= maxDataToSelect - 1 && gapsCopy.get(gapsCopy.size() - 1).gapSize() < maxDataToSelect - 1) {
                            lastGap = curGap;
                        }
                        if (prevGap != null) {
                            if (prevGap.overlaps(curGap)) {
                                ok = false;
                                log.warn("Removing overlapping gaps: " + prevGap + ", " + curGap);
                                dataService.deleteDataGap(transaction, prevGap);
                                dataService.deleteDataGap(transaction, curGap);
                                DataGap newGap = null;
                                if (curGap.equals(lastGap)) {
                                    newGap = new DataGap(prevGap.getStartId(), prevGap.getStartId() + maxDataToSelect - 1);
                                } else {
                                    newGap = new DataGap(prevGap.getStartId(),
                                            prevGap.getEndId() > curGap.getEndId() ? prevGap.getEndId() : curGap.getEndId());
                                }
                                log.warn("Inserting new gap to fix overlap: " + newGap);
                                dataService.insertDataGap(transaction, newGap);
                                gapsCopy.remove(i--);
                                gapsCopy.set(i, newGap);
                                curGap = newGap;
                            }
                        }
                    }
                    prevGap = curGap;
                }
                transaction.commit();
                if (!ok) {
                    printGapState();
                    log.info("Fixed gaps: " + gapsCopy);
                }
                gaps.clear();
                gaps.addAll(gapsCopy);
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
                if (transaction != null) {
                    transaction.close();
                }
            }
        } catch (RuntimeException ex) {
            processInfo.setStatus(ProcessStatus.ERROR);
            throw ex;
        }
    }

    protected void logExpiredDataGaps() {
        if (gapsExpired.isEmpty()) {
            return;
        }
        if (log.isDebugEnabled()) {
            for (DataGap dataGap : gapsExpired) {
                if (dataGap.getStartId() == dataGap.getEndId()) {
                    log.debug("Expired data gap at data_id {} create_time {}.  Skipping it because " +
                            (supportsTransactionViews ? "there are no pending transactions" : "the gap expired"), dataGap.getStartId(), dataGap
                                    .getCreateTime());
                } else {
                    log.debug("Expired data gap between data_id {} and {} create_time {}.  Skipping it because " +
                            (supportsTransactionViews ? "there are no pending transactions" : "the gap expired"),
                            dataGap.getStartId(), dataGap.getEndId(), dataGap.getCreateTime());
                }
            }
            return;
        }
        Date minDate = null;
        Date maxDate = null;
        long minDataId = Long.MAX_VALUE;
        long maxDataId = 0;
        for (DataGap dataGap : gapsExpired) {
            if (minDate == null || dataGap.getCreateTime().before(minDate)) {
                minDate = dataGap.getCreateTime();
            }
            if (maxDate == null || dataGap.getCreateTime().after(maxDate)) {
                maxDate = dataGap.getCreateTime();
            }
            minDataId = Math.min(minDataId, dataGap.getStartId());
            maxDataId = Math.max(maxDataId, dataGap.getEndId());
        }
        log.info("Expired {} data gap(s) between data_id {} and {} and between create_time {} and {}",
                gapsExpired.size(), minDataId, maxDataId, minDate, maxDate);
    }

    protected boolean isGapExpired(DataGap dataGap, long gapTimoutInMs) {
        Date createTime = dataGap.getCreateTime();
        if (supportsTransactionViews) {
            return createTime != null && (createTime.getTime() < earliestTransactionTime || earliestTransactionTime == 0);
        }
        return createTime != null && routingStartTime - createTime.getTime() > gapTimoutInMs;
    }

    protected void checkInterrupted() {
        if (Thread.interrupted()) {
            throw new RuntimeException("Thread received interrupt");
        }
    }

    public Long mapRow(Row row) {
        return row.getLong("data_id");
    }

    @Override
    public List<DataGap> getDataGaps() {
        return gaps;
    }

    @Override
    public DataGap getLastDataGap() {
        return lastGap;
    }

    @Override
    public synchronized void addDataIds(List<Long> dataIds) {
        this.dataIds.addAll(dataIds);
    }

    @Override
    public void setIsAllDataRead(boolean isAllDataRead) {
        this.isAllDataRead = isAllDataRead;
    }

    public boolean isFullGapAnalysis() {
        if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            isFullGapAnalysis = contextService.is(ContextConstants.ROUTING_FULL_GAP_ANALYSIS);
        }
        return isFullGapAnalysis;
    }

    @Override
    public void setFullGapAnalysis(boolean isFullGapAnalysis) {
        setFullGapAnalysis(null, isFullGapAnalysis);
    }

    @Override
    public void setFullGapAnalysis(ISqlTransaction sqlTransaction, boolean isFullGapAnalysis) {
        if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            contextService.save(sqlTransaction, ContextConstants.ROUTING_FULL_GAP_ANALYSIS, Boolean.toString(isFullGapAnalysis));
        }
        this.isFullGapAnalysis = isFullGapAnalysis;
    }

    protected long getLastBusyExpireRunTime() {
        if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            lastBusyExpireRunTime = contextService.getLong(ContextConstants.ROUTING_LAST_BUSY_EXPIRE_RUN_TIME);
        }
        return lastBusyExpireRunTime;
    }

    protected void setLastBusyExpireRunTime(long lastBusyExpireRunTime) {
        if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            contextService.save(ContextConstants.ROUTING_LAST_BUSY_EXPIRE_RUN_TIME, String.valueOf(lastBusyExpireRunTime));
        }
        this.lastBusyExpireRunTime = lastBusyExpireRunTime;
    }
}
