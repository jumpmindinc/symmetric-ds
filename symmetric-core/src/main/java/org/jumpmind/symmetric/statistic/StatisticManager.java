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
package org.jumpmind.symmetric.statistic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.CHANNEL;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.*;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IStatisticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @see IStatisticManager
 */
public class StatisticManager implements IStatisticManager {
    protected Logger log = LoggerFactory.getLogger(getClass());
    private static final String UNKNOWN = "Unknown";
    private static final int NUMBER_OF_PERMITS = 1000;
    private Map<String, ChannelStats> channelStats = new ConcurrentHashMap<String, ChannelStats>();
    private List<JobStats> jobStats = new ArrayList<JobStats>();
    private HostStats hostStats;
    private ConcurrentHashMap<Long, RouterStats> routerStatsByBatch = new ConcurrentHashMap<Long, RouterStats>();
    protected ISymmetricEngine engine;
    protected INodeService nodeService;
    protected IStatisticService statisticService;
    protected IParameterService parameterService;
    protected IConfigurationService configurationService;
    protected IClusterService clusterService;
    protected IDataService dataService;
    protected Semaphore channelStatsLock = new Semaphore(NUMBER_OF_PERMITS, true);
    protected Semaphore hostStatsLock = new Semaphore(NUMBER_OF_PERMITS, true);
    protected Semaphore jobStatsLock = new Semaphore(NUMBER_OF_PERMITS, true);
    protected Semaphore tableStatsLock = new Semaphore(NUMBER_OF_PERMITS, true);
    protected Map<ProcessInfoKey, ProcessInfo> processInfos = new ConcurrentHashMap<ProcessInfoKey, ProcessInfo>();
    protected Map<ProcessInfoKey, ProcessInfo> processInfosThatHaveDoneWork = new ConcurrentHashMap<ProcessInfoKey, ProcessInfo>();
    protected Map<ProcessType, ProcessInfo> userLastWorkDoneMap = new ConcurrentHashMap<ProcessType, ProcessInfo>();
    protected Map<SourceTargetNodeId, ProcessInfo> userLastDataSyncWorkDoneMap = new ConcurrentHashMap<SourceTargetNodeId, ProcessInfo>();
    private Map<Date, Map<String, ChannelStats>> baseChannelStatsInMemory = new LinkedHashMap<Date, Map<String, ChannelStats>>();
    private Set<String> systemChannelIds = new HashSet<String>(Arrays.asList(new String[] { Constants.CHANNEL_CONFIG, Constants.CHANNEL_SYSTEM,
            Constants.CHANNEL_MONITOR, Constants.CHANNEL_HEARTBEAT, Constants.CHANNEL_DYNAMIC }));
    private Map<String, Date> lastDataSyncMap = new HashMap<String, Date>();
    private Map<String, Long> lastDataSyncRowsMap = new HashMap<String, Long>();
    private Map<String, Long> lastDataSyncBytesMap = new HashMap<String, Long>();

    public StatisticManager(ISymmetricEngine engine) {
        this.engine = engine;
        parameterService = engine.getParameterService();
        nodeService = engine.getNodeService();
        configurationService = engine.getConfigurationService();
        statisticService = engine.getStatisticService();
        clusterService = engine.getClusterService();
        dataService = engine.getDataService();
        init();
    }

    protected void init() {
    }

    public ProcessInfo newProcessInfo(ProcessInfoKey key) {
        ProcessInfo process = new ProcessInfo(key);
        ProcessInfo old = processInfos.get(key);
        if (old != null) {
            if (old.getStatus() != ProcessStatus.OK && old.getStatus() != ProcessStatus.ERROR) {
                log.warn(
                        "Starting a new process even though the previous '{}' process had not finished",
                        old.getProcessType());
                log.info("Details from the previous process: {}", old);
            }
            if (old.getCurrentDataCount() > 0 || old.getTotalDataCount() > 0) {
                processInfosThatHaveDoneWork.put(key, old);
                if (isUserProcessInfo(old)) {
                    ProcessType oldType = old.getProcessType();
                    userLastWorkDoneMap.put(oldType, old);
                    String oldSourceNodeId = old.getSourceNodeId(), oldTargetNodeId = old.getTargetNodeId();
                    if (oldSourceNodeId != null && oldTargetNodeId != null && ArrayUtils.contains(ProcessType.dataSyncProcessTypes, oldType)) {
                        SourceTargetNodeId nodeIdKey = new SourceTargetNodeId(oldSourceNodeId, oldTargetNodeId);
                        ProcessInfo lastWorkDone = userLastDataSyncWorkDoneMap.get(nodeIdKey);
                        if (lastWorkDone == null || old.getLastStatusChangeTime().after(lastWorkDone.getLastStatusChangeTime())) {
                            userLastDataSyncWorkDoneMap.put(nodeIdKey, old);
                        }
                    }
                }
            }
        }
        processInfos.put(key, process);
        return process;
    }

    public Set<String> getNodesWithProcessesInError() {
        String identityNodeId = nodeService.findIdentityNodeId();
        Set<String> status = new HashSet<String>();
        if (identityNodeId != null) {
            List<ProcessInfo> list = getProcessInfos();
            for (ProcessInfo processInfo : list) {
                String nodeIdInError = processInfo.showInError(identityNodeId);
                if (nodeIdInError != null) {
                    status.add(nodeIdInError);
                }
            }
        }
        return status;
    }

    public List<ProcessInfo> getProcessInfos() {
        List<ProcessInfo> list = new ArrayList<ProcessInfo>(processInfos.values());
        try {
            Collections.sort(list);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to sort process infos", e);
            List<ProcessInfo> deepCopyList = new ArrayList<ProcessInfo>();
            for (ProcessInfo processInfo : list) {
                deepCopyList.add(processInfo.copy());
            }
            list = deepCopyList;
            Collections.sort(list);
        }
        return list;
    }

    public List<ProcessInfo> getProcessInfosThatHaveDoneWork() {
        List<ProcessInfo> toReturn = new ArrayList<ProcessInfo>();
        List<ProcessInfo> infosList = new ArrayList<ProcessInfo>(processInfos.values());
        Iterator<ProcessInfo> i = infosList.iterator();
        while (i.hasNext()) {
            ProcessInfo info = i.next();
            if (info.getStatus() == ProcessInfo.ProcessStatus.OK && info.getCurrentDataCount() == 0) {
                ProcessInfo lastThatDidWork = processInfosThatHaveDoneWork.get(info.getKey());
                if (lastThatDidWork != null) {
                    toReturn.add(lastThatDidWork.copy());
                }
            } else {
                toReturn.add(info.copy());
            }
        }
        return toReturn;
    }

    public List<ProcessInfo> getMostRecentUserProcessInfos(ProcessType... processTypes) {
        List<ProcessInfo> userProcessInfoList = getProcessInfos().stream().filter(i -> isUserProcessInfo(i)).collect(Collectors.toList());
        List<ProcessInfo> mostRecentUserProcessInfoList = new ArrayList<ProcessInfo>();
        for (ProcessType processType : processTypes) {
            List<ProcessInfo> filteredUserProcessInfoList = userProcessInfoList.stream()
                    .filter(i -> processType.equals(i.getProcessType())).collect(Collectors.toList());
            boolean foundActiveProcessInfo = false;
            for (ProcessInfo processInfo : filteredUserProcessInfoList) {
                if (processInfo.getStatus() != ProcessStatus.OK && processInfo.getStatus() != ProcessStatus.ERROR) {
                    mostRecentUserProcessInfoList.add(processInfo);
                    foundActiveProcessInfo = true;
                }
            }
            if (!foundActiveProcessInfo) {
                if (userLastWorkDoneMap.containsKey(processType)) {
                    mostRecentUserProcessInfoList.add(userLastWorkDoneMap.get(processType));
                } else {
                    mostRecentUserProcessInfoList.addAll(filteredUserProcessInfoList);
                }
            }
        }
        return mostRecentUserProcessInfoList;
    }

    protected boolean isUserProcessInfo(ProcessInfo processInfo) {
        String tableName = processInfo.getCurrentTableName();
        String tablePrefix = engine.getTablePrefix();
        return !Strings.CI.startsWith(tableName, tablePrefix) && !Strings.CI.contains(tableName, "." + tablePrefix)
                && !systemChannelIds.contains(processInfo.getCurrentChannelId()) && !Constants.QUEUE_SYSTEM.equals(processInfo.getQueue());
    }

    public ProcessInfo getMostRecentUserDataSyncProcessInfo(String sourceNodeId, String targetNodeId) {
        return userLastDataSyncWorkDoneMap.get(new SourceTargetNodeId(sourceNodeId, targetNodeId));
    }

    public void addJobStats(String jobName, long startTime, long endTime, long processedCount) {
        jobStatsLock.acquireUninterruptibly();
        try {
            JobStats stats = new JobStats(jobName, startTime, endTime, processedCount);
            jobStats.add(stats);
        } finally {
            jobStatsLock.release();
        }
    }

    public void addJobStats(String jobName, long startTime, long endTime, long processedCount, String errorMessage) {
        jobStatsLock.acquireUninterruptibly();
        try {
            JobStats stats = new JobStats(jobName, startTime, endTime, processedCount, errorMessage);
            jobStats.add(stats);
        } finally {
            jobStatsLock.release();
        }
    }

    public void addJobStats(String jobName, long startTime, long endTime, long processedCount, Exception e) {
        jobStatsLock.acquireUninterruptibly();
        try {
            JobStats stats = new JobStats(jobName, startTime, endTime, processedCount, e);
            jobStats.add(stats);
        } finally {
            jobStatsLock.release();
        }
    }

    public void addJobStats(String targetNodeId, int targetNodeCount, String jobName, long startTime, long endTime, long processedCount) {
        jobStatsLock.acquireUninterruptibly();
        try {
            JobStats stats = new JobStats(targetNodeId, targetNodeCount, startTime, endTime, jobName, processedCount);
            jobStats.add(stats);
        } finally {
            jobStatsLock.release();
        }
    }

    public RouterStats getRouterStatsByBatch(Long batchId) {
        return routerStatsByBatch.get(batchId);
    }

    public void addRouterStats(long startDataId, long endDataId, long dataReadCount,
            long peekAheadFillCount, List<DataGap> dataGaps, Set<String> transactions,
            Collection<OutgoingBatch> batches) {
        RouterStats routerStats = new RouterStats(startDataId, endDataId, dataReadCount,
                peekAheadFillCount, dataGaps, transactions);
        for (OutgoingBatch batch : batches) {
            if (!batch.getNodeId().equals(Constants.UNROUTED_NODE_ID)) {
                routerStatsByBatch.put(batch.getBatchId(), routerStats);
            }
        }
    }

    public void removeRouterStatsByBatch(Long batchId) {
        routerStatsByBatch.remove(batchId);
    }

    public void incrementDataRouted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataRouted(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_ROUTED, channelId, count);
    }

    public void setDataUnRouted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).setDataUnRouted(count);
        } finally {
            channelStatsLock.release();
        }
        setChannelDoubleGauge(METRIC_ID_DATA_UNROUTED_CHANNEL, channelId, count);
    }

    public void incrementDataExtracted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataExtracted(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_EXTRACTED, channelId, count);
    }

    public void incrementDataBytesExtracted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesExtracted(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_EXTRACTED_BYTES, channelId, count);
    }

    public void incrementDataExtractedErrors(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataExtractedErrors(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_EXTRACTED_ERRORS, channelId, count);
    }

    public void incrementDataEventInserted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataEventInserted(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_EVENTS_INSERTED, channelId, count);
    }

    public void incrementDataSent(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataSent(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_SENT, channelId, count);
    }

    public void incrementDataBytesSent(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesSent(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_SENT_BYTES, channelId, count);
    }

    public void incrementDataSentErrors(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataSentErrors(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_SENT_ERRORS, channelId, count);
    }

    public void incrementDataReceived(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataReceived(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_RECEIVED, channelId, count);
    }

    public void incrementDataBytesReceived(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesReceived(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_RECEIVED_BYTES, channelId, count);
    }

    public void incrementDataLoaded(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataLoaded(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_LOADED, channelId, count);
    }

    public void incrementDataBytesLoaded(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesLoaded(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_LOADED_BYTES, channelId, count);
    }

    public void incrementDataLoadedErrors(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataLoadedErrors(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_LOADED_ERRORS, channelId, count);
    }

    public void incrementDataLoadedOutgoing(String channelId, long count, String nodeId) {
        if (!systemChannelIds.contains(channelId)) {
            this.lastDataSyncMap.put(nodeId, new Date());
            this.lastDataSyncRowsMap.put(nodeId, count);
        }
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataLoadedOutgoing(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_LOADED_OUTGOING, channelId, count);
    }

    public void incrementDataBytesLoadedOutgoing(String channelId, long count, String nodeId) {
        if (!systemChannelIds.contains(channelId)) {
            this.lastDataSyncBytesMap.put(nodeId, count);
        }
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesLoadedOutgoing(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_LOADED_OUTGOING_BYTES, channelId, count);
    }

    public void incrementDataLoadedOutgoingErrors(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataLoadedOutgoingErrors(count);
        } finally {
            channelStatsLock.release();
        }
        addChannelCounter(METRIC_ID_DATA_LOADED_OUTGOING_ERRORS, channelId, count);
    }

    public void updateDataMinCreateTime(String channelId, Date minCreateTime) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).updateDataMinCreateTime(minCreateTime);
        } finally {
            channelStatsLock.release();
        }
        setChannelLongGauge(METRIC_ID_DATA_CREATE_TIME_MIN, channelId, minCreateTime.getTime());
    }

    public void updateDataMaxCreateTime(String channelId, Date maxCreateTime) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).updateDataMaxCreateTime(maxCreateTime);
        } finally {
            channelStatsLock.release();
        }
        setChannelLongGauge(METRIC_ID_DATA_CREATE_TIME_MAX, channelId, maxCreateTime.getTime());
    }

    public void incrementRestart() {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementRestarted(1);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_ENGINE_RESTARTS, 1);
    }

    public void incrementNodesPulled(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesPulled(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_NODES_PULLED, count);
    }

    public void incrementNodesPushed(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesPushed(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_NODES_PUSHED, count);
    }

    public void incrementTotalNodesPulledTime(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTotalNodesPullTime(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_NODES_PULLED_TIME, count);
    }

    public void incrementTotalNodesPushedTime(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTotalNodesPushTime(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_NODES_PUSHED_TIME, count);
    }

    public void incrementNodesRejected(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesRejected(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_NODES_REJECTED, count);
    }

    public void incrementNodesRegistered(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesRegistered(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_NODES_REGISTERED, count);
    }

    public void incrementNodesLoaded(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesLoaded(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_NODES_LOADED, count);
    }

    public void incrementNodesDisabled(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesDisabled(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_NODES_DISABLED, count);
    }

    public void incrementPurgedBatchIncomingRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedBatchIncomingRows(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_PURGE_BATCH_INCOMING_ROWS, count);
    }

    public void incrementPurgedBatchOutgoingRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedBatchOutgoingRows(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_PURGE_BATCH_OUTGOING_ROWS, count);
    }

    public void incrementPurgedDataRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedDataRows(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_PURGE_DATA_ROWS, count);
    }

    public void incrementPurgedDataEventRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedDataEventRows(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_PURGE_DATA_EVENT_ROWS, count);
    }

    public void incrementPurgedStrandedDataRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedStrandedDataRows(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_PURGE_STRANDED_DATA_ROWS, count);
    }

    public void incrementPurgedStrandedDataEventRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedStrandedDataEventRows(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_PURGE_STRANDED_DATA_EVENT_ROWS, count);
    }

    public void incrementPurgedExpiredDataRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedExpiredDataRows(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_PURGE_EXPIRED_DATA_ROWS, count);
    }

    public void incrementTriggersRemovedCount(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTriggersRemovedCount(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_TRIGGERS_REMOVED, count);
    }

    public void incrementTriggersRebuiltCount(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTriggersRebuiltCount(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_TRIGGERS_REBUILT, count);
    }

    public void incrementTriggersCreatedCount(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTriggersCreatedCount(count);
        } finally {
            hostStatsLock.release();
        }
        addEngineCounter(METRIC_ID_TRIGGERS_CREATED, count);
    }

    @Override
    public void setDataGapCount(long count) {
        if (hostStatsLock.tryAcquire()) {
            try {
                getHostStats().setDataGapCount(count);
            } finally {
                hostStatsLock.release();
            }
        }
        setEngineDoubleGauge(METRIC_ID_DATA_GAP_COUNT, count);
    }

    @Override
    public void setDataUnroutedCount(long count) {
        if (hostStatsLock.tryAcquire()) {
            try {
                getHostStats().setDataUnroutedCount(count);
            } finally {
                hostStatsLock.release();
            }
        }
        setEngineDoubleGauge(METRIC_ID_DATA_UNROUTED_TOTAL, count);
    }

    protected void saveAdditionalStats(Date endTime, ChannelStats stats) {
        if (baseChannelStatsInMemory.get(endTime) == null) {
            baseChannelStatsInMemory.put(endTime, new HashMap<String, ChannelStats>());
        }
        baseChannelStatsInMemory.get(endTime).put(stats.getChannelId(), stats);
    }

    public void flush() {
        boolean recordStatistics = parameterService.is(ParameterConstants.STATISTIC_RECORD_ENABLE,
                false);
        long recordStatisticsCountThreshold = parameterService.getLong(ParameterConstants.STATISTIC_RECORD_COUNT_THRESHOLD, -1);
        if (channelStats != null) {
            channelStatsLock.acquireUninterruptibly(NUMBER_OF_PERMITS);
            try {
                if (recordStatistics) {
                    Date endTime = new Date();
                    for (ChannelStats stats : channelStats.values()) {
                        if (stats.getNodeId().equals(UNKNOWN)) {
                            Node node = nodeService.getCachedIdentity();
                            if (node != null) {
                                stats.setNodeId(node.getNodeId());
                            }
                        }
                        stats.setEndTime(endTime);
                        saveAdditionalStats(endTime, stats);
                        if (stats.isNonZero()) {
                            statisticService.save(stats);
                        }
                    }
                }
                resetChannelStats(true);
            } finally {
                channelStatsLock.release(NUMBER_OF_PERMITS);
            }
        }
        int rowsLoaded = 0;
        int rowsSent = 0;
        for (Map.Entry<Date, Map<String, ChannelStats>> entry : baseChannelStatsInMemory.entrySet()) {
            for (Map.Entry<String, ChannelStats> channelEntry : entry.getValue().entrySet()) {
                if (!channelEntry.getKey().equals("config") && !channelEntry.getKey().equals("heartbeat")) {
                    rowsLoaded += channelEntry.getValue().getDataLoaded();
                    rowsSent += channelEntry.getValue().getDataSent();
                }
            }
            if (log.isDebugEnabled() && (rowsLoaded > 0 || rowsSent > 0)) {
                log.debug("===================================");
                log.debug("Date: " + entry.getKey());
                log.debug("Rows Out: " + rowsSent);
                log.debug("Rows In: " + rowsLoaded);
                log.debug("===================================");
            }
        }
        if (hostStats != null) {
            hostStatsLock.acquireUninterruptibly(NUMBER_OF_PERMITS);
            try {
                if (recordStatistics) {
                    if (hostStats.getNodeId().equals(UNKNOWN)) {
                        Node node = nodeService.getCachedIdentity();
                        if (node != null) {
                            hostStats.setNodeId(node.getNodeId());
                        }
                    }
                    if (hostStats.getDataGapCount() == null) {
                        hostStats.setDataGapCount(dataService.countDataGaps());
                    }
                    if (hostStats.getDataUnroutedCount() == null && engine.getRouterService() != null) {
                        hostStats.setDataUnroutedCount(engine.getRouterService().getUnroutedDataCount());
                    }
                    hostStats.setEndTime(new Date());
                    statisticService.save(hostStats);
                }
                hostStats = null;
            } finally {
                hostStatsLock.release(NUMBER_OF_PERMITS);
            }
        }
        if (jobStats != null) {
            List<JobStats> toFlush = null;
            jobStatsLock.acquireUninterruptibly(NUMBER_OF_PERMITS);
            try {
                toFlush = jobStats;
                jobStats = new ArrayList<JobStats>();
            } finally {
                jobStatsLock.release(NUMBER_OF_PERMITS);
            }
            if (toFlush != null && recordStatistics) {
                Node node = nodeService.getCachedIdentity();
                if (node != null) {
                    String nodeId = node.getNodeId();
                    String serverId = clusterService.getServerId();
                    for (JobStats stats : toFlush) {
                        if (recordStatisticsCountThreshold != -1 && (stats.isErrorFlag() || stats.getProcessedCount() > recordStatisticsCountThreshold)) {
                            stats.setNodeId(nodeId);
                            stats.setHostName(serverId);
                            statisticService.save(stats);
                        }
                    }
                }
            }
        }
    }

    public TreeMap<Date, Map<String, ChannelStats>> getNodeStatsForPeriod(Date start, Date end, String nodeId, int periodSizeInMinutes) {
        Map<String, ChannelStats> currentStats = getWorkingChannelStats();
        NodeStatsByPeriodMap savedStatsPeriodMap = (NodeStatsByPeriodMap) statisticService.getNodeStatsForPeriod(start, end, nodeId,
                periodSizeInMinutes);
        for (String key : currentStats.keySet()) {
            ChannelStats stat = currentStats.get(key);
            Date date = stat.getStartTime();
            savedStatsPeriodMap.add(date, stat);
        }
        return savedStatsPeriodMap;
    }

    public Map<String, ChannelStats> getWorkingChannelStats() {
        if (channelStats != null) {
            HashMap<String, ChannelStats> stats = new HashMap<String, ChannelStats>();
            for (ChannelStats stat : channelStats.values()) {
                ChannelStats newStat = new ChannelStats(stat.getNodeId(), stat.getHostName(), stat.getStartTime(),
                        stat.getEndTime(), stat.getChannelId());
                newStat.add(stat);
                stats.put(newStat.getChannelId(), newStat);
            }
            return stats;
        } else {
            return new HashMap<String, ChannelStats>();
        }
    }

    public List<JobStats> getWorkingJobStats() {
        if (jobStats != null) {
            List<JobStats> stats = new ArrayList<JobStats>();
            for (JobStats stat : jobStats) {
                stats.add(new JobStats(stat));
            }
            return stats;
        }
        return new ArrayList<JobStats>();
    }

    public HostStats getWorkingHostStats() {
        if (this.hostStats != null) {
            return new HostStats(this.hostStats);
        } else {
            return new HostStats();
        }
    }

    protected void resetChannelStats(boolean force) {
        if (force) {
            channelStats = null;
        }
        if (channelStats == null) {
            List<NodeChannel> channels = configurationService.getNodeChannels(false);
            channelStats = new HashMap<String, ChannelStats>(channels.size());
            for (NodeChannel nodeChannel : channels) {
                getChannelStats(nodeChannel.getChannelId());
            }
        }
    }

    protected ChannelStats getChannelStats(String channelId) {
        resetChannelStats(false);
        ChannelStats stats = channelStats.get(channelId);
        if (stats == null) {
            Node node = nodeService.getCachedIdentity();
            if (node != null) {
                stats = new ChannelStats(node.getNodeId(), clusterService.getServerId(),
                        new Date(), null, channelId);
                channelStats.put(channelId, stats);
            } else {
                stats = new ChannelStats(UNKNOWN, clusterService.getServerId(), new Date(), null,
                        channelId);
            }
        }
        return stats;
    }

    protected HostStats getHostStats() {
        if (hostStats == null) {
            Node node = nodeService.getCachedIdentity();
            if (node != null) {
                hostStats = new HostStats(node.getNodeId(), clusterService.getServerId(),
                        new Date(), null);
            } else {
                hostStats = new HostStats(UNKNOWN, clusterService.getServerId(), new Date(), null);
            }
        }
        return hostStats;
    }

    @Override
    public void incrementTableRows(Map<String, Map<String, Long>> tableCounts, boolean loaded) {
    }

    @Override
    public String getMostRecentActiveTableSynced() {
        return "";
    }

    @Override
    public Map<Integer, Date> getTotalLoadedRows() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Date> getLastDataLoadedTimeMap() {
        return this.lastDataSyncMap;
    }

    @Override
    public Map<String, Long> getLastDataLoadedRowsMap() {
        return this.lastDataSyncRowsMap;
    }

    @Override
    public Map<String, Long> getLastDataLoadedBytesMap() {
        return this.lastDataSyncBytesMap;
    }

    private void addChannelCounter(String metricId, String channelId, long delta) {
        try {
            IEngineMetricsService svc = engine.getMetricsService();
            if (svc == null)
                return;
            IUpDownCounter c = svc.getUpDownCounter(metricId, MetricAttributeList.of(new MetricAttribute(CHANNEL, channelId)));
            if (c != null)
                c.add(delta);
        } catch (Exception e) {
            log.debug("Failed to record channel metric {}@{}", metricId, channelId, e);
        }
    }

    private void setChannelDoubleGauge(String metricId, String channelId, double value) {
        try {
            IEngineMetricsService svc = engine.getMetricsService();
            if (svc == null)
                return;
            ISymDoubleGauge g = svc.getDoubleGauge(metricId, MetricAttributeList.of(new MetricAttribute(CHANNEL, channelId)));
            if (g != null)
                g.setValue(value);
        } catch (Exception e) {
            log.debug("Failed to record channel metric {}@{}", metricId, channelId, e);
        }
    }

    private void setChannelLongGauge(String metricId, String channelId, long value) {
        try {
            IEngineMetricsService svc = engine.getMetricsService();
            if (svc == null)
                return;
            ISymLongGauge g = svc.getLongGauge(metricId, MetricAttributeList.of(new MetricAttribute(CHANNEL, channelId)));
            if (g != null)
                g.setValue(value);
        } catch (Exception e) {
            log.debug("Failed to record channel metric {}@{}", metricId, channelId, e);
        }
    }

    private void addEngineCounter(String metricId, long delta) {
        try {
            IEngineMetricsService svc = engine.getMetricsService();
            if (svc == null)
                return;
            IUpDownCounter c = svc.getUpDownCounter(metricId);
            if (c != null)
                c.add(delta);
        } catch (Exception e) {
            log.debug("Failed to record engine metric {}", metricId, e);
        }
    }

    private void setEngineDoubleGauge(String metricId, double value) {
        try {
            IEngineMetricsService svc = engine.getMetricsService();
            if (svc == null)
                return;
            ISymDoubleGauge g = svc.getDoubleGauge(metricId);
            if (g != null)
                g.setValue(value);
        } catch (Exception e) {
            log.debug("Failed to record engine metric {}", metricId, e);
        }
    }

    private class SourceTargetNodeId {
        public String sourceNodeId;
        public String targetNodeId;

        public SourceTargetNodeId(String sourceNodeId, String targetNodeId) {
            this.sourceNodeId = sourceNodeId;
            this.targetNodeId = targetNodeId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Objects.hash(sourceNodeId, targetNodeId);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SourceTargetNodeId)) {
                return false;
            }
            SourceTargetNodeId other = (SourceTargetNodeId) obj;
            return Objects.equals(sourceNodeId, other.sourceNodeId) && Objects.equals(targetNodeId, other.targetNodeId);
        }
    }
}