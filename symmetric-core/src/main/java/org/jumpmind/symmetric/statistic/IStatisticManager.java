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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;

/**
 * This manager provides an API record statistics
 */
public interface IStatisticManager {
    public ProcessInfo newProcessInfo(ProcessInfoKey key);

    public List<ProcessInfo> getProcessInfos();

    public List<ProcessInfo> getProcessInfosThatHaveDoneWork();

    public List<ProcessInfo> getMostRecentUserProcessInfos(ProcessType... processTypes);

    public ProcessInfo getMostRecentUserDataSyncProcessInfo(String sourceNodeId, String targetNodeId);

    public Set<String> getNodesWithProcessesInError();

    public void flush();

    public void addJobStats(String jobName, long startTime, long endTime, long processedCount);

    public void addJobStats(String jobName, long startTime, long endTime, long processedCount, Exception e);

    public void addJobStats(String jobName, long startTime, long endTime, long processedCount, String errorMessage);

    public void addJobStats(String targetNodeId, int targetNodeCount, String jobName, long startTime, long endTime, long processedCount);

    public void addRouterStats(long startDataId, long endDataId, long dataReadCount, long peekAheadFillCount,
            List<DataGap> dataGaps, Set<String> transactions, Collection<OutgoingBatch> batches);

    public RouterStats getRouterStatsByBatch(Long batchId);

    public void removeRouterStatsByBatch(Long batchId);

    public void incrementDataLoadedErrors(String channelId, long count);

    public void incrementDataBytesLoaded(String channelId, long count);

    public void incrementDataLoaded(String channelId, long count);

    public void incrementDataLoadedOutgoingErrors(String channelId, long count);

    public void incrementDataBytesLoadedOutgoing(String channelId, long count, String targetNodeId);

    public void incrementDataLoadedOutgoing(String channelId, long count, String targetNodeId);

    public void incrementDataBytesReceived(String channelId, long count);

    public void incrementDataReceived(String channelId, long count);

    public void incrementDataBytesSent(String channelId, long count);

    public void incrementDataSent(String channelId, long count);

    public void incrementDataEventInserted(String channelId, long count);

    public void incrementDataExtractedErrors(String channelId, long count);

    public void incrementDataBytesExtracted(String channelId, long count);

    public void incrementDataExtracted(String channelId, long count);

    public void setDataUnRouted(String channelId, long count);

    public void incrementDataRouted(String channelId, long count);

    public void incrementDataSentErrors(String channelId, long count);

    public void updateDataMinCreateTime(String channelId, Date minCreateTime);

    public void updateDataMaxCreateTime(String channelId, Date maxCreateTime);

    public void incrementRestart();

    public void incrementNodesPulled(long count);

    public void incrementNodesPushed(long count);

    public void incrementTotalNodesPulledTime(long count);

    public void incrementTotalNodesPushedTime(long count);

    public void incrementNodesRejected(long count);

    public void incrementNodesLoaded(long count);

    public void incrementNodesRegistered(long count);

    public void incrementNodesDisabled(long count);

    public void incrementPurgedBatchIncomingRows(long count);

    public void incrementPurgedBatchOutgoingRows(long count);

    public void incrementPurgedDataRows(long count);

    public void incrementPurgedDataEventRows(long count);

    public void incrementPurgedStrandedDataRows(long count);

    public void incrementPurgedStrandedDataEventRows(long count);

    public void incrementPurgedExpiredDataRows(long count);

    public void incrementTriggersRemovedCount(long count);

    public void incrementTriggersRebuiltCount(long count);

    public void incrementTriggersCreatedCount(long count);

    public void setDataGapCount(long count);

    public void setDataUnroutedCount(long count);

    public void incrementTableRows(Map<String, Map<String, Long>> tableCounts, boolean loaded);

    public Map<String, ChannelStats> getWorkingChannelStats();

    public List<JobStats> getWorkingJobStats();

    public HostStats getWorkingHostStats();

    public TreeMap<Date, Map<String, ChannelStats>> getNodeStatsForPeriod(Date start, Date end, String nodeId, int periodSizeInMinutes);

    public String getMostRecentActiveTableSynced();

    public Map<Integer, Date> getTotalLoadedRows();

    public Map<String, Date> getLastDataLoadedTimeMap();

    public Map<String, Long> getLastDataLoadedRowsMap();

    public Map<String, Long> getLastDataLoadedBytesMap();
}
