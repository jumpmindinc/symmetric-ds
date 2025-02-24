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
package org.jumpmind.symmetric.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

public class ExtractRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ExtractStatus {
        NE, OK, LS
    };

    private long requestId;
    private String nodeId;
    private ExtractStatus status;
    private long startBatchId;
    private long endBatchId;
    private String triggerId;
    private String routerId;
    private TriggerRouter triggerRouter;
    private Date lastUpdateTime;
    private Date createTime;
    private String queue;
    private long loadId;
    private String tableName;
    private long rows;
    private long extractedRows;
    private long transferredRows;
    private long loadedRows;
    private long lastTransferredBatchId;
    private long lastLoadedBatchId;
    private long extractedMillis;
    private long transferredMillis;
    private long loadedMillis;
    private long parentRequestId;
    private long numRowsBulkLoaded;
    private Integer extractThreadId;
    private Integer loadThreadId;

    public Integer getThreadId() {
        return getThreadId(status);
    }

    public Integer getThreadId(ExtractStatus s) {
        return s == null || s == ExtractStatus.NE || s == ExtractStatus.LS ? extractThreadId : loadThreadId;
    }

    public void setThreadId(Integer threadId) {
        setThreadId(threadId, status);
    }

    public void setThreadId(Integer threadId, ExtractStatus s) {
        if (s == null || s == ExtractStatus.NE || s == ExtractStatus.LS) {
            extractThreadId = threadId;
        } else {
            loadThreadId = threadId;
        }
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public ExtractStatus getStatus() {
        return status;
    }

    public void setStatus(ExtractStatus status) {
        this.status = status;
    }

    public long getStartBatchId() {
        return startBatchId;
    }

    public void setStartBatchId(long startBatchId) {
        this.startBatchId = startBatchId;
    }

    public long getEndBatchId() {
        return endBatchId;
    }

    public void setEndBatchId(long endBatchId) {
        this.endBatchId = endBatchId;
    }

    public TriggerRouter getTriggerRouter() {
        return triggerRouter;
    }

    public void setTriggerRouter(TriggerRouter triggerRouter) {
        this.triggerRouter = triggerRouter;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public String getRouterId() {
        return routerId;
    }

    public void setRouterId(String routerId) {
        this.routerId = routerId;
    }

    public long getLoadId() {
        return loadId;
    }

    public void setLoadId(long loadId) {
        this.loadId = loadId;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public long getRows() {
        return rows;
    }

    public void setRows(long rows) {
        this.rows = rows;
    }

    public long getTransferredRows() {
        return transferredRows;
    }

    public void setTransferredRows(long transferredRows) {
        this.transferredRows = transferredRows;
    }

    public long getLoadedRows() {
        return loadedRows;
    }

    public void setLoadedRows(long loadedRows) {
        this.loadedRows = loadedRows;
    }

    public long getLastTransferredBatchId() {
        return lastTransferredBatchId;
    }

    public void setLastTransferredBatchId(long lastTransferredBatchId) {
        this.lastTransferredBatchId = lastTransferredBatchId;
    }

    public long getLastLoadedBatchId() {
        return lastLoadedBatchId;
    }

    public void setLastLoadedBatchId(long lastLoadedBatchId) {
        this.lastLoadedBatchId = lastLoadedBatchId;
    }

    public long getTransferredMillis() {
        return transferredMillis;
    }

    public void setTransferredMillis(long transferredMillis) {
        this.transferredMillis = transferredMillis;
    }

    public long getLoadedMillis() {
        return loadedMillis;
    }

    public void setLoadedMillis(long loadedMillis) {
        this.loadedMillis = loadedMillis;
    }

    public long getParentRequestId() {
        return parentRequestId;
    }

    public void setParentRequestId(long parentRequestId) {
        this.parentRequestId = parentRequestId;
    }

    public long getExtractedRows() {
        return extractedRows;
    }

    public void setExtractedRows(long extractedRows) {
        this.extractedRows = extractedRows;
    }

    public long getExtractedMillis() {
        return extractedMillis;
    }

    public void setExtractedMillis(long extractedMillis) {
        this.extractedMillis = extractedMillis;
    }

    public Integer getExtractThreadId() {
        return extractThreadId;
    }

    public void setExtractThreadId(Integer extractThreadId) {
        this.extractThreadId = extractThreadId;
    }

    public Integer getLoadThreadId() {
        return loadThreadId;
    }

    public void setLoadThreadId(Integer loadThreadId) {
        this.loadThreadId = loadThreadId;
    }

    public void setNumRowsBulkLoaded(long numRowsBulkLoaded) {
        this.numRowsBulkLoaded = numRowsBulkLoaded;
    }

    public long getNumRowsBulkLoaded() {
        return this.numRowsBulkLoaded;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        }
        ExtractRequest other = (ExtractRequest) obj;
        return requestId == other.requestId;
    }
}
