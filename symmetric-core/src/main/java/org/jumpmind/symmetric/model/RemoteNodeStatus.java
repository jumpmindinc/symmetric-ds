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
package org.jumpmind.symmetric.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Indicates the status of an attempt to transport data from or to a remove node.
 */
public class RemoteNodeStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    public static enum Status {
        OFFLINE, BUSY, NOT_AUTHORIZED, REGISTRATION_REQUIRED, INITIAL_LOAD_PENDING, SYNC_DISABLED, NO_DATA, DATA_PROCESSED, DATA_ERROR, UNKNOWN_ERROR
    };

    private final Object monitor = new Object();
    private String nodeId;
    private String queue;
    private Status status;
    private long dataProcessed;
    private long batchesProcessed;
    private long reloadBatchesProcessed;
    private volatile boolean complete = false;
    private Map<String, Channel> channels;
    private Map<String, Integer> tableCounts = new LinkedHashMap<String, Integer>();
    private Set<String> tableSummary = new LinkedHashSet<String>();

    public RemoteNodeStatus(String nodeId, String channelId, Map<String, Channel> channels) {
        this.status = Status.NO_DATA;
        this.nodeId = nodeId;
        this.channels = channels;
        this.queue = channelId;
    }

    public boolean failed() {
        return status != Status.NO_DATA && status != Status.DATA_PROCESSED;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getDataProcessed() {
        return dataProcessed;
    }

    public void setDataProcessed(long dataProcessed) {
        this.dataProcessed = dataProcessed;
    }

    public long getBatchesProcessed() {
        return batchesProcessed;
    }

    public long getReloadBatchesProcessed() {
        return reloadBatchesProcessed;
    }

    public void updateIncomingStatus(List<IncomingBatch> incomingBatches) {
        if (incomingBatches != null) {
            for (IncomingBatch incomingBatch : incomingBatches) {
                if (incomingBatch.getIgnoreCount() == 0) {
                    incrementTableCounts(incomingBatch);
                    dataProcessed += incomingBatch.getLoadRowCount();
                }
                batchesProcessed++;
                if (incomingBatch.getStatus() == org.jumpmind.symmetric.model.IncomingBatch.Status.ER) {
                    status = Status.DATA_ERROR;
                }
            }
        }
        if (status != Status.DATA_ERROR && dataProcessed > 0) {
            status = Status.DATA_PROCESSED;
        }
    }

    public void updateOutgoingStatus(List<OutgoingBatch> outgoingBatches, List<BatchAck> batches) {
        int numberOfAcks = 0;
        if (batches != null) {
            numberOfAcks = batches.size();
            for (BatchAck batch : batches) {
                if (!batch.isOk()) {
                    status = Status.DATA_ERROR;
                }
            }
        }
        int numberOfBatches = 0;
        if (outgoingBatches != null) {
            numberOfBatches = outgoingBatches.size();
            for (OutgoingBatch batch : outgoingBatches) {
                batchesProcessed++;
                if (batch.getIgnoreCount() == 0) {
                    incrementTableCounts(batch);
                    dataProcessed += batch.totalRowCount();
                }
                Channel channel = channels.get(batch.getChannelId());
                if (channel != null && channel.isReloadFlag()) {
                    reloadBatchesProcessed++;
                }
                if (batch.getStatus() == OutgoingBatch.Status.ER) {
                    status = Status.DATA_ERROR;
                }
            }
        }
        if (numberOfAcks != numberOfBatches) {
            status = Status.DATA_ERROR;
        }
        if (status != Status.DATA_ERROR && dataProcessed > 0) {
            status = Status.DATA_PROCESSED;
        }
    }

    public void resetCounts() {
        dataProcessed = 0;
        batchesProcessed = 0;
        reloadBatchesProcessed = 0;
    }

    public void setComplete(boolean complete) {
        synchronized (monitor) {
            this.complete = complete;
            monitor.notifyAll();
        }
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean waitCompleted(long milliseconds) throws InterruptedException {
        synchronized (monitor) {
            if (complete) {
                return true;
            }
            monitor.wait(milliseconds);
            return complete;
        }
    }

    public Map<String, Integer> getTableCounts() {
        return tableCounts;
    }

    public String getTableSummary() {
        final int MAX_SUMMARY_LENGTH = 512;
        if (tableCounts != null && !tableCounts.isEmpty()) {
            StringBuilder buff = new StringBuilder();
            for (String table : tableCounts.keySet()) {
                buff.append(table).append(", ");
            }
            if (buff.length() > 2) {
                buff.setLength(buff.length() - 2);
            }
            return StringUtils.abbreviate(buff.toString(), MAX_SUMMARY_LENGTH);
        } else if (!tableSummary.isEmpty()) {
            StringBuilder buff = new StringBuilder();
            for (String table : tableSummary) {
                buff.append(table).append(", ");
            }
            if (buff.length() > 2) {
                buff.setLength(buff.length() - 2);
            }
            return StringUtils.abbreviate(buff.toString(), MAX_SUMMARY_LENGTH);
        } else {
            return "";
        }
    }

    protected void incrementTableCounts(AbstractBatch batch) {
        if (!batch.getTableCounts().isEmpty()) {
            for (String table : batch.getTableCounts().keySet()) {
                Integer value = tableCounts.get(table);
                if (value == null) {
                    tableCounts.put(table, Integer.valueOf(1));
                } else {
                    tableCounts.replace(table, value.intValue() + batch.getTableCounts().get(table));
                }
            }
        } else if (!StringUtils.isEmpty(batch.getSummary())) {
            String[] tables = batch.getSummary().split(",");
            for (String table : tables) {
                String tableTrimmed = table.trim();
                if (!tableSummary.contains(tableTrimmed)) {
                    tableSummary.add(tableTrimmed);
                }
            }
        }
    }

    public void resetTableSummary() {
        tableCounts.clear();
        tableSummary.clear();
    }
}
