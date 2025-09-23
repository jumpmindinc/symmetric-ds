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

import java.util.Date;

public class HostStats extends AbstractNodeHostStats {
    private long restarted;
    private long nodesPulled;
    private long totalNodesPullTime;
    private long nodesPushed;
    private long totalNodesPushTime;
    private long nodesRejected;
    private long nodesRegistered;
    private long nodesLoaded;
    private long nodesDisabled;
    private long purgedDataRows;
    private long purgedDataEventRows;
    private long purgedBatchOutgoingRows;
    private long purgedBatchIncomingRows;
    private long purgedStrandedDataRows;
    private long purgedStrandedDataEventRows;
    private long purgedExpiredDataRows;
    private long triggersCreatedCount;
    private long triggersRebuiltCount;
    private long triggersRemovedCount;
    private Long dataGapCount;
    private Long dataUnroutedCount;

    public HostStats() {
    }

    public HostStats(String nodeId, String hostName, Date startTime, Date endTime) {
        super(nodeId, hostName, startTime, endTime);
    }

    public HostStats(HostStats source) {
        super(source.getNodeId(), source.getHostName(), source.getStartTime(), source.getEndTime());
        add(source);
    }

    public void add(HostStats stats) {
        restarted += stats.getRestarted();
        nodesPulled += stats.getNodesPulled();
        totalNodesPullTime += stats.getTotalNodesPullTime();
        nodesPushed += stats.getNodesPushed();
        totalNodesPushTime += stats.getTotalNodesPushTime();
        nodesRejected += stats.getNodesRejected();
        nodesRegistered += stats.getNodesRegistered();
        nodesLoaded += stats.getNodesLoaded();
        nodesDisabled += stats.getNodesDisabled();
        purgedDataRows += stats.getPurgedDataRows();
        purgedDataEventRows += stats.getPurgedDataEventRows();
        purgedBatchOutgoingRows += stats.getPurgedBatchOutgoingRows();
        purgedBatchIncomingRows += stats.getPurgedBatchIncomingRows();
        purgedStrandedDataRows += stats.getPurgedStrandedDataRows();
        purgedStrandedDataEventRows += stats.getPurgedStrandedDataEventRows();
        purgedExpiredDataRows += stats.getPurgedExpiredDataRows();
        triggersCreatedCount += stats.getTriggersCreatedCount();
        triggersRebuiltCount += stats.getTriggersRebuiltCount();
        triggersRemovedCount += stats.getTriggersRemovedCount();
    }

    public long getRestarted() {
        return restarted;
    }

    public void incrementRestarted(long value) {
        restarted += value;
    }

    public long getTotalNodesPullTime() {
        return totalNodesPullTime;
    }

    public void setTotalNodesPullTime(long totalNodesPullTime) {
        this.totalNodesPullTime = totalNodesPullTime;
    }

    public void incrementTotalNodesPullTime(long value) {
        totalNodesPullTime += value;
    }

    public long getTotalNodesPushTime() {
        return totalNodesPushTime;
    }

    public void setTotalNodesPushTime(long totalNodesPushTime) {
        this.totalNodesPushTime = totalNodesPushTime;
    }

    public void incrementTotalNodesPushTime(long value) {
        totalNodesPushTime += value;
    }

    public long getNodesPulled() {
        return nodesPulled;
    }

    public void incrementNodesPulled(long value) {
        nodesPulled += value;
    }

    public long getNodesPushed() {
        return nodesPushed;
    }

    public void incrementNodesPushed(long value) {
        nodesPushed += value;
    }

    public long getNodesRejected() {
        return nodesRejected;
    }

    public void incrementNodesRejected(long value) {
        nodesRejected += value;
    }

    public long getNodesRegistered() {
        return nodesRegistered;
    }

    public void incrementNodesRegistered(long value) {
        nodesRegistered += value;
    }

    public long getNodesLoaded() {
        return nodesLoaded;
    }

    public void incrementNodesLoaded(long value) {
        nodesLoaded += value;
    }

    public long getNodesDisabled() {
        return nodesDisabled;
    }

    public void incrementNodesDisabled(long value) {
        nodesDisabled += value;
    }

    public long getPurgedDataRows() {
        return purgedDataRows;
    }

    public void incrementPurgedDataRows(long value) {
        purgedDataRows += value;
    }

    public long getPurgedDataEventRows() {
        return purgedDataEventRows;
    }

    public void incrementPurgedDataEventRows(long value) {
        purgedDataEventRows += value;
    }

    public long getPurgedBatchOutgoingRows() {
        return purgedBatchOutgoingRows;
    }

    public void incrementPurgedBatchOutgoingRows(long value) {
        purgedBatchOutgoingRows += value;
    }

    public long getPurgedBatchIncomingRows() {
        return purgedBatchIncomingRows;
    }

    public void incrementPurgedBatchIncomingRows(long value) {
        purgedBatchIncomingRows += value;
    }

    public long getTriggersCreatedCount() {
        return triggersCreatedCount;
    }

    public void incrementTriggersCreatedCount(long count) {
        triggersCreatedCount += count;
    }

    public void incrementTriggersRebuiltCount(long count) {
        triggersRebuiltCount += count;
    }

    public void incrementTriggersRemovedCount(long count) {
        triggersRemovedCount += count;
    }

    public long getTriggersRebuiltCount() {
        return triggersRebuiltCount;
    }

    public long getTriggersRemovedCount() {
        return triggersRemovedCount;
    }

    public void setRestarted(long restarted) {
        this.restarted = restarted;
    }

    public void setNodesPulled(long nodesPulled) {
        this.nodesPulled = nodesPulled;
    }

    public void setNodesPushed(long nodesPushed) {
        this.nodesPushed = nodesPushed;
    }

    public void setNodesRejected(long nodesRejected) {
        this.nodesRejected = nodesRejected;
    }

    public void setNodesRegistered(long nodesRegistered) {
        this.nodesRegistered = nodesRegistered;
    }

    public void setNodesLoaded(long nodesLoaded) {
        this.nodesLoaded = nodesLoaded;
    }

    public void setNodesDisabled(long nodesDisabled) {
        this.nodesDisabled = nodesDisabled;
    }

    public void setPurgedDataRows(long purgedDataRows) {
        this.purgedDataRows = purgedDataRows;
    }

    public void setPurgedDataEventRows(long purgedDataEventRows) {
        this.purgedDataEventRows = purgedDataEventRows;
    }

    public void setPurgedBatchOutgoingRows(long purgedBatchOutgoingRows) {
        this.purgedBatchOutgoingRows = purgedBatchOutgoingRows;
    }

    public void setPurgedBatchIncomingRows(long purgedBatchIncomingRows) {
        this.purgedBatchIncomingRows = purgedBatchIncomingRows;
    }

    public void setTriggersCreatedCount(long triggersCreatedCount) {
        this.triggersCreatedCount = triggersCreatedCount;
    }

    public void setTriggersRebuiltCount(long triggersRebuiltCount) {
        this.triggersRebuiltCount = triggersRebuiltCount;
    }

    public void setTriggersRemovedCount(long triggersRemovedCount) {
        this.triggersRemovedCount = triggersRemovedCount;
    }

    public long getPurgedStrandedDataRows() {
        return purgedStrandedDataRows;
    }

    public void setPurgedStrandedDataRows(long purgedStrandedDataRows) {
        this.purgedStrandedDataRows = purgedStrandedDataRows;
    }

    public void incrementPurgedStrandedDataRows(long value) {
        this.purgedStrandedDataRows += value;
    }

    public long getPurgedStrandedDataEventRows() {
        return purgedStrandedDataEventRows;
    }

    public void setPurgedStrandedDataEventRows(long purgedStrandedDataEventRows) {
        this.purgedStrandedDataEventRows = purgedStrandedDataEventRows;
    }

    public void incrementPurgedStrandedDataEventRows(long value) {
        this.purgedStrandedDataEventRows += value;
    }

    public long getPurgedExpiredDataRows() {
        return purgedExpiredDataRows;
    }

    public void setPurgedExpiredDataRows(long purgedExpiredDataRows) {
        this.purgedExpiredDataRows = purgedExpiredDataRows;
    }

    public void incrementPurgedExpiredDataRows(long value) {
        this.purgedExpiredDataRows += value;
    }

    public Long getDataGapCount() {
        return dataGapCount;
    }

    public void setDataGapCount(long dataGapCount) {
        this.dataGapCount = dataGapCount;
    }

    public Long getDataUnroutedCount() {
        return dataUnroutedCount;
    }

    public void setDataUnroutedCount(long dataUnroutedCount) {
        this.dataUnroutedCount = dataUnroutedCount;
    }
}