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

public class MockStatisticManager implements IStatisticManager {
    public List<ProcessInfo> getProcessInfos() {
        return null;
    }

    @Override
    public List<ProcessInfo> getProcessInfosThatHaveDoneWork() {
        return null;
    }

    public void removeProcessInfo(ProcessInfoKey key) {
    }

    public ProcessInfo newProcessInfo(ProcessInfoKey key) {
        return null;
    }

    public Set<String> getNodesWithProcessesInError() {
        return null;
    }

    public void flush() {
    }

    public void addJobStats(String jobName, long startTime, long endTime, long processedCount) {
    }

    public void incrementRestart() {
    }

    public void incrementNodesPulled(long count) {
    }

    public void incrementNodesRegistered(long count) {
    }

    public void incrementNodesPushed(long count) {
    }

    public void incrementTotalNodesPulledTime(long count) {
    }

    public void incrementTotalNodesPushedTime(long count) {
    }

    public void incrementNodesRejected(long count) {
    }

    public synchronized void incrementDataLoadedErrors(String channelId, long count) {
    }

    public synchronized void incrementDataBytesLoaded(String channelId, long count) {
    }

    public synchronized void incrementDataBytesSent(String channelId, long count) {
    }

    public synchronized void incrementDataBytesReceived(String channelId, long count) {
    }

    public synchronized void incrementDataEventInserted(String channelId, long count) {
    }

    public synchronized void incrementDataExtractedErrors(String channelId, long count) {
    }

    public synchronized void incrementDataBytesExtracted(String channelId, long count) {
    }

    public synchronized void setDataUnRouted(String channelId, long count) {
    }

    public synchronized void incrementDataRouted(String channelId, long count) {
    }

    public synchronized void incrementDataSentErrors(String channelId, long count) {
    }

    public void incrementDataExtracted(String channelId, long count) {
    }

    public void incrementDataLoaded(String channelId, long count) {
    }

    public void incrementDataSent(String channelId, long count) {
    };

    public void incrementDataReceived(String channelId, long count) {
    }

    public Map<String, ChannelStats> getWorkingChannelStats() {
        return null;
    }

    public HostStats getWorkingHostStats() {
        return null;
    }

    public void incrementNodesLoaded(long count) {
    }

    public void incrementNodesDisabled(long count) {
    }

    public void incrementPurgedBatchIncomingRows(long count) {
    }

    public void incrementPurgedBatchOutgoingRows(long count) {
    }

    public void incrementPurgedDataRows(long count) {
    }

    public void incrementPurgedDataEventRows(long count) {
    }

    public void incrementTriggersRemovedCount(long count) {
    }

    public void incrementTriggersRebuiltCount(long count) {
    }

    public void incrementTriggersCreatedCount(long count) {
    }

    @Override
    public void setDataGapCount(long count) {
    }

    @Override
    public void setDataUnroutedCount(long count) {
    }

    public void addRouterStats(long startDataId, long endDataId, long dataReadCount,
            long peekAheadFillCount, List<DataGap> dataGaps, Set<String> transactions,
            Collection<OutgoingBatch> batches) {
    }

    public RouterStats getRouterStatsByBatch(Long batchId) {
        return null;
    }

    public void removeRouterStatsByBatch(Long batchId) {
    }

    @Override
    public void addJobStats(String targetNodeId, int targetNodeCount, String jobName, long startTime, long endTime, long processedCount) {
    }

    @Override
    public TreeMap<Date, Map<String, ChannelStats>> getNodeStatsForPeriod(Date start, Date end, String nodeId, int periodSizeInMinutes) {
        return null;
    }

    @Override
    public void incrementDataLoadedOutgoingErrors(String channelId, long count) {
    }

    @Override
    public void incrementDataBytesLoadedOutgoing(String channelId, long count, String nodeId) {
    }

    @Override
    public void incrementDataLoadedOutgoing(String channelId, long count, String nodeId) {
    }

    @Override
    public void updateDataMinCreateTime(String channelId, Date minCreateTime) {
    }

    @Override
    public void updateDataMaxCreateTime(String channelId, Date maxCreateTime) {
    }

    @Override
    public void incrementTableRows(Map<String, Map<String, Long>> tableCounts, boolean loaded) {
    }

    @Override
    public void incrementPurgedStrandedDataRows(long count) {
    }

    @Override
    public void incrementPurgedStrandedDataEventRows(long count) {
    }

    @Override
    public void incrementPurgedExpiredDataRows(long count) {
    }

    public String getMostRecentActiveTableSynced() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Integer, Date> getTotalLoadedRows() {
        // TODO Auto-generated method stub
        return null;
    }

    public void addJobStats(String jobName, long startTime, long endTime, long processedCount, Exception e) {
        // TODO Auto-generated method stub
    }

    public List<JobStats> getWorkingJobStats() {
        return null;
    }

    @Override
    public void addJobStats(String jobName, long startTime, long endTime, long processedCount, String errorMessage) {
        // TODO Auto-generated method stub
    }

    @Override
    public Map<String, Date> getLastDataLoadedTimeMap() {
        return null;
    }

    @Override
    public Map<String, Long> getLastDataLoadedRowsMap() {
        return null;
    }

    @Override
    public Map<String, Long> getLastDataLoadedBytesMap() {
        return null;
    }
}