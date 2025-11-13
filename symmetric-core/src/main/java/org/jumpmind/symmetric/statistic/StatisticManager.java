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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

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
    }

    public void setDataUnRouted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).setDataUnRouted(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataExtracted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataExtracted(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataBytesExtracted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesExtracted(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataExtractedErrors(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataExtractedErrors(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataEventInserted(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataEventInserted(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataSent(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataSent(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataBytesSent(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesSent(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataSentErrors(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataSentErrors(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataReceived(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataReceived(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataBytesReceived(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesReceived(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataLoaded(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataLoaded(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataBytesLoaded(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataBytesLoaded(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementDataLoadedErrors(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataLoadedErrors(count);
        } finally {
            channelStatsLock.release();
        }
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
    }

    public void incrementDataLoadedOutgoingErrors(String channelId, long count) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).incrementDataLoadedOutgoingErrors(count);
        } finally {
            channelStatsLock.release();
        }
    }

    public void updateDataMinCreateTime(String channelId, Date minCreateTime) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).updateDataMinCreateTime(minCreateTime);
        } finally {
            channelStatsLock.release();
        }
    }

    public void updateDataMaxCreateTime(String channelId, Date maxCreateTime) {
        channelStatsLock.acquireUninterruptibly();
        try {
            getChannelStats(channelId).updateDataMaxCreateTime(maxCreateTime);
        } finally {
            channelStatsLock.release();
        }
    }

    public void incrementRestart() {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementRestarted(1);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementNodesPulled(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesPulled(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementNodesPushed(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesPushed(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementTotalNodesPulledTime(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTotalNodesPullTime(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementTotalNodesPushedTime(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTotalNodesPushTime(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementNodesRejected(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesRejected(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementNodesRegistered(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesRegistered(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementNodesLoaded(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesLoaded(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementNodesDisabled(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementNodesDisabled(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementPurgedBatchIncomingRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedBatchIncomingRows(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementPurgedBatchOutgoingRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedBatchOutgoingRows(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementPurgedDataRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedDataRows(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementPurgedDataEventRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedDataEventRows(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementPurgedStrandedDataRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedStrandedDataRows(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementPurgedStrandedDataEventRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedStrandedDataEventRows(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementPurgedExpiredDataRows(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementPurgedExpiredDataRows(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementTriggersRemovedCount(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTriggersRemovedCount(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementTriggersRebuiltCount(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTriggersRebuiltCount(count);
        } finally {
            hostStatsLock.release();
        }
    }

    public void incrementTriggersCreatedCount(long count) {
        hostStatsLock.acquireUninterruptibly();
        try {
            getHostStats().incrementTriggersCreatedCount(count);
        } finally {
            hostStatsLock.release();
        }
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
            if (rowsLoaded > 0 || rowsSent > 0) {
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
}