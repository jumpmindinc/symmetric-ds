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

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jumpmind.log.LogThrottle;
import org.jumpmind.log.ThrottledLogger;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.NumberMapper;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
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
public class DataGapDetector {
    protected final Logger log = LoggerFactory.getLogger(getLoggerName());
    protected final DataGapDetectionSummary detectionSummary = new DataGapDetectionSummary(System.currentTimeMillis());
    protected final ThrottledLogger progressLog = new ThrottledLogger(log,
            new LogThrottle(System.currentTimeMillis(), Constants.LONG_OPERATION_THRESHOLD, Constants.LONG_OPERATION_DEBUG_THRESHOLD, true));
    protected IDataService dataService;
    protected IParameterService parameterService;
    protected ISymmetricDialect symmetricDialect;
    protected IRouterService routerService;
    protected IStatisticManager statisticManager;
    protected INodeService nodeService;

    public DataGapDetector() {
    }

    public DataGapDetector(IDataService dataService, IParameterService parameterService,
            ISymmetricDialect symmetricDialect, IRouterService routerService, IStatisticManager statisticManager, INodeService nodeService) {
        this.dataService = dataService;
        this.parameterService = parameterService;
        this.routerService = routerService;
        this.symmetricDialect = symmetricDialect;
        this.statisticManager = statisticManager;
        this.nodeService = nodeService;
    }

    /**
     * Always make sure sym_data_gap is up to date to make sure that we don't dual route data.
     */
    public void beforeRouting() {
        DataGapPassStats passStats = new DataGapPassStats();
        ProcessInfo processInfo = this.statisticManager.newProcessInfo(new ProcessInfoKey(
                nodeService.findIdentityNodeId(), null, ProcessType.GAP_DETECT));
        try {
            long ts = System.currentTimeMillis();
            processInfo.setStatus(ProcessStatus.QUERYING);
            final List<DataGap> gaps = dataService.findDataGaps();
            long lastDataId = -1;
            final int dataIdIncrementBy = parameterService
                    .getInt(ParameterConstants.DATA_ID_INCREMENT_BY);
            final long maxDataToSelect = parameterService
                    .getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE);
            final long gapTimoutInMs = parameterService
                    .getLong(ParameterConstants.ROUTING_STALE_DATA_ID_GAP_TIME);
            long databaseTime = symmetricDialect.getDatabaseTime();
            Set<DataGap> gapCheck = new HashSet<DataGap>(gaps);
            boolean supportsTransactionViews = symmetricDialect.supportsTransactionViews();
            long earliestTransactionTime = 0;
            if (supportsTransactionViews) {
                Date date = symmetricDialect.getEarliestTransactionStartTime();
                if (date != null) {
                    earliestTransactionTime = date.getTime() - parameterService.getLong(
                            ParameterConstants.ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS, 60000);
                }
            }
            for (final DataGap dataGap : gaps) {
                final boolean lastGap = dataGap.equals(gaps.get(gaps.size() - 1));
                String sql = routerService.getSql("selectDistinctDataIdFromDataEventUsingGapsSql");
                ISqlTemplate sqlTemplate = symmetricDialect.getPlatform().getSqlTemplate();
                Object[] params = new Object[] { dataGap.getStartId(), dataGap.getEndId() };
                lastDataId = -1;
                processInfo.setStatus(ProcessStatus.QUERYING);
                long queryForIdsTs = System.currentTimeMillis();
                List<Number> ids = sqlTemplate.query(sql, new NumberMapper(), params);
                if (System.currentTimeMillis() - queryForIdsTs > Constants.LONG_OPERATION_THRESHOLD) {
                    log.info("It took longer than {}ms to run the following sql for gap from {} to {}.  {}",
                            new Object[] { Constants.LONG_OPERATION_THRESHOLD, dataGap.getStartId(), dataGap.getEndId(), sql });
                }
                processInfo.setStatus(ProcessStatus.PROCESSING);
                passStats.addIdsFound(ids.size());
                passStats.addRangeChecked(dataGap.getEndId() - dataGap.getStartId());
                ISqlTransaction transaction = null;
                try {
                    transaction = sqlTemplate.startSqlTransaction();
                    for (Number number : ids) {
                        long dataId = number.longValue();
                        processInfo.incrementCurrentDataCount();
                        if (lastDataId == -1 && dataGap.getStartId() + dataIdIncrementBy <= dataId) {
                            // there was a new gap at the start
                            DataGap newGap = new DataGap(dataGap.getStartId(), dataId - 1);
                            if (!gapCheck.contains(newGap)) {
                                dataService.insertDataGap(transaction, newGap);
                                gapCheck.add(newGap);
                            }
                            passStats.incrementGapsAdded();
                        } else if (lastDataId != -1 && lastDataId + dataIdIncrementBy != dataId && lastDataId != dataId) {
                            // found a gap somewhere in the existing gap
                            DataGap newGap = new DataGap(lastDataId + 1, dataId - 1);
                            if (!gapCheck.contains(newGap)) {
                                dataService.insertDataGap(transaction, newGap);
                                gapCheck.add(newGap);
                            }
                            passStats.incrementGapsAdded();
                        }
                        lastDataId = dataId;
                    }
                    // if we found data in the gap
                    if (lastDataId != -1) {
                        if (!lastGap && lastDataId + dataIdIncrementBy <= dataGap.getEndId()) {
                            DataGap newGap = new DataGap(lastDataId + dataIdIncrementBy, dataGap.getEndId());
                            if (!gapCheck.contains(newGap)) {
                                dataService.insertDataGap(transaction, newGap);
                                gapCheck.add(newGap);
                            }
                            passStats.incrementGapsAdded();
                        }
                        dataService.deleteDataGap(transaction, dataGap);
                        passStats.incrementGapsDeleted();
                        // if we did not find data in the gap and it was not the
                        // last gap
                    } else if (!lastGap) {
                        Date createTime = dataGap.getCreateTime();
                        if (supportsTransactionViews) {
                            if (createTime != null && (createTime.getTime() < earliestTransactionTime || earliestTransactionTime == 0)) {
                                if (dataService.countDataInRange(dataGap.getStartId() - 1, dataGap.getEndId() + 1) == 0) {
                                    if (dataGap.getStartId() == dataGap.getEndId()) {
                                        log.info(
                                                "Found a gap in data_id at {}.  Skipping it because there are no pending transactions in the database",
                                                dataGap.getStartId());
                                    } else {
                                        log.info(
                                                "Found a gap in data_id from {} to {}.  Skipping it because there are no pending transactions in the database",
                                                dataGap.getStartId(), dataGap.getEndId());
                                    }
                                    dataService.deleteDataGap(transaction, dataGap);
                                    passStats.incrementGapsDeleted();
                                }
                            }
                        } else if (createTime != null && databaseTime - createTime.getTime() > gapTimoutInMs) {
                            if (dataService.countDataInRange(dataGap.getStartId() - 1, dataGap.getEndId() + 1) == 0) {
                                if (dataGap.getStartId() == dataGap.getEndId()) {
                                    log.info("Found a gap in data_id at {}.  Skipping it because the gap expired", dataGap.getStartId());
                                } else {
                                    log.info("Found a gap in data_id from {} to {}.  Skipping it because the gap expired",
                                            dataGap.getStartId(), dataGap.getEndId());
                                }
                                dataService.deleteDataGap(transaction, dataGap);
                                passStats.incrementGapsDeleted();
                            }
                        }
                    }
                    if (progressLog.isDebugDue(System.currentTimeMillis())) {
                        progressLog.infoOrDebug(System.currentTimeMillis(),
                                "The data gap detection process has been running for {}ms, detected {} rows that have been previously routed over a total gap range of {}, "
                                        + "inserted {} new gaps, and deleted {} gaps", new Object[] { System.currentTimeMillis() - ts,
                                                passStats.getIdsFound(), passStats.getRangeChecked(), passStats.getGapsAdded(),
                                                passStats.getGapsDeleted() });
                    }
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
            if (lastDataId != -1) {
                DataGap newGap = new DataGap(lastDataId + 1, lastDataId + maxDataToSelect);
                if (!gapCheck.contains(newGap)) {
                    dataService.insertDataGap(newGap);
                    gapCheck.add(newGap);
                }
            }
            long updateTimeInMs = System.currentTimeMillis() - ts;
            progressLog.infoOrDebug(System.currentTimeMillis(), "Detecting gaps took {} ms", updateTimeInMs);
            detectionSummary.recordDetection(updateTimeInMs, gapCheck.size() - passStats.getGapsDeleted());
            detectionSummary.recordGapChanges(passStats.getGapsAdded(), passStats.getGapsDeleted());
            if (lastDataId != -1) {
                detectionSummary.recordLastDataId(lastDataId);
            }
            logGapDetectionSummary(System.currentTimeMillis());
            detectionSummary.resetCounters(System.currentTimeMillis());
            processInfo.setStatus(ProcessStatus.OK);
        } catch (RuntimeException ex) {
            processInfo.setStatus(ProcessStatus.ERROR);
            throw ex;
        }
    }

    // Overridden by subclasses (e.g. PRO)
    protected String getLoggerName() {
        return getClass().getName();
    }

    protected void logGapDetectionSummary(long currentTime) {
        if (progressLog.isInfoDue(currentTime)) {
            progressLog.info(currentTime, "{}", detectionSummary.getInfoMessage(currentTime));
        } else {
            progressLog.infoOrDebug(currentTime, "{}", detectionSummary.getDebugMessage());
        }
    }

    DataGapDetectionSummary getDetectionSummary() {
        return detectionSummary;
    }

    public void afterRouting() {
    }

    public List<DataGap> getDataGaps() {
        return dataService.findDataGaps();
    }

    public DataGap getLastDataGap() {
        List<DataGap> gaps = getDataGaps();
        if (gaps.size() > 0) {
            return gaps.get(gaps.size() - 1);
        }
        return null;
    }

    public void addDataIds(List<Long> dataIds) {
    }

    public void setIsAllDataRead(boolean isAllDataRead) {
    }

    public void setFullGapAnalysis(boolean isFullGapAnalysis) {
    }

    public void setFullGapAnalysis(ISqlTransaction sqlTransaction, boolean b) {
    }
}
