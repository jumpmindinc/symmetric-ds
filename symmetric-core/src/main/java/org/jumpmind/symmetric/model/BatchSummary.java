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

import java.util.Date;

public class BatchSummary {
    private static final long serialVersionUID = 1L;
    private String nodeId;
    private int batchCount;
    private int dataCount;
    private AbstractBatch.Status status;
    private Date oldestBatchCreateTime;
    private Date lastBatchUpdateTime;
    private String channel;
    private long minBatchId;
    private boolean errorFlag;
    private long totalBytes;
    private long totalMillis;
    private long extractMillis;
    private long transferMillis;
    private long loadMillis;
    private long routerMillis;
    private int insertCount;
    private int updateCount;
    private int deleteCount;
    private int otherCount;
    private int reloadCount;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public int getBatchCount() {
        return batchCount;
    }

    public void setBatchCount(int batchCount) {
        this.batchCount = batchCount;
    }

    public int getDataCount() {
        return dataCount;
    }

    public void setDataCount(int dataCount) {
        this.dataCount = dataCount;
    }

    public IncomingBatch.Status getStatus() {
        return status;
    }

    public void setStatus(IncomingBatch.Status status) {
        this.status = status;
    }

    public Date getOldestBatchCreateTime() {
        return oldestBatchCreateTime;
    }

    public void setOldestBatchCreateTime(Date oldestBatchCreateTime) {
        this.oldestBatchCreateTime = oldestBatchCreateTime;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Date getLastBatchUpdateTime() {
        return lastBatchUpdateTime;
    }

    public void setLastBatchUpdateTime(Date lastBatchUpdateTime) {
        this.lastBatchUpdateTime = lastBatchUpdateTime;
    }

    public void setErrorFlag(boolean errorFlag) {
        this.errorFlag = errorFlag;
    }

    public boolean isErrorFlag() {
        return errorFlag;
    }

    public long getMinBatchId() {
        return minBatchId;
    }

    public void setMinBatchId(long errorBatchId) {
        this.minBatchId = errorBatchId;
    }

    public long getExtractMillis() {
        return extractMillis;
    }

    public void setExtractMillis(long extractMillis) {
        this.extractMillis = extractMillis;
    }

    public long getTransferMillis() {
        return transferMillis;
    }

    public void setTransferMillis(long transferMillis) {
        this.transferMillis = transferMillis;
    }

    public long getLoadMillis() {
        return loadMillis;
    }

    public void setLoadMillis(long loadMillis) {
        this.loadMillis = loadMillis;
    }

    public long getRouterMillis() {
        return routerMillis;
    }

    public void setRouterMillis(long routerMillis) {
        this.routerMillis = routerMillis;
    }

    public int getInsertCount() {
        return insertCount;
    }

    public void setInsertCount(int insertCount) {
        this.insertCount = insertCount;
    }

    public int getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(int updateCount) {
        this.updateCount = updateCount;
    }

    public int getDeleteCount() {
        return deleteCount;
    }

    public void setDeleteCount(int deleteCount) {
        this.deleteCount = deleteCount;
    }

    public int getOtherCount() {
        return otherCount;
    }

    public void setOtherCount(int otherCount) {
        this.otherCount = otherCount;
    }

    public int getReloadCount() {
        return reloadCount;
    }

    public void setReloadCount(int reloadCount) {
        this.reloadCount = reloadCount;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getTotalMillis() {
        return totalMillis;
    }

    public void setTotalMillis(long totalMillis) {
        this.totalMillis = totalMillis;
    }
}
