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

public class TableReloadStatus {
    protected int loadId;
    protected String sourceNodeId;
    protected String targetNodeId;
    protected boolean completed;
    protected boolean cancelled;
    protected boolean fullLoad;
    protected Date startTime;
    protected Date endTime;
    protected long startDataBatchId;
    protected long endDataBatchId;
    protected int setupBatchCount;
    protected int dataBatchCount;
    protected int finalizeBatchCount;
    protected int setupBatchLoaded;
    protected int dataBatchLoaded;
    protected int finalizeBatchLoaded;
    protected int tableCount;
    protected long rowsLoaded;
    protected long rowsCount;
    protected boolean errorFlag;
    protected String sqlState;
    protected int sqlCode;
    protected String sqlMessage;
    protected String lastUpdatedBy;
    protected Date lastUpdateTime = new Date();
    protected int numBatchesBulkLoaded;
    protected long numRowsBulkLoaded;

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + loadId;
        result = prime * result + ((sourceNodeId == null) ? 0 : sourceNodeId.hashCode());
        result = prime * result + ((startTime == null) ? 0 : startTime.hashCode());
        result = prime * result + ((targetNodeId == null) ? 0 : targetNodeId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TableReloadStatus other = (TableReloadStatus) obj;
        if (loadId != other.loadId) {
            return false;
        }
        if (sourceNodeId == null) {
            if (other.sourceNodeId != null) {
                return false;
            }
        } else if (!sourceNodeId.equals(other.sourceNodeId)) {
            return false;
        }
        if (startTime == null) {
            if (other.startTime != null) {
                return false;
            }
        } else if (!startTime.equals(other.startTime)) {
            return false;
        }
        if (targetNodeId == null) {
            if (other.targetNodeId != null) {
                return false;
            }
        } else if (!targetNodeId.equals(other.targetNodeId)) {
            return false;
        }
        return true;
    }

    public int getLoadId() {
        return loadId;
    }

    public void setLoadId(int loadId) {
        this.loadId = loadId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isFullLoad() {
        return fullLoad;
    }

    public void setFullLoad(boolean fullLoad) {
        this.fullLoad = fullLoad;
    }

    public long getStartDataBatchId() {
        return startDataBatchId;
    }

    public void setStartDataBatchId(long startDataBatchId) {
        this.startDataBatchId = startDataBatchId;
    }

    public long getEndDataBatchId() {
        return endDataBatchId;
    }

    public void setEndDataBatchId(long endDataBatchId) {
        this.endDataBatchId = endDataBatchId;
    }

    public int getSetupBatchCount() {
        return setupBatchCount;
    }

    public void setSetupBatchCount(int setupBatchCount) {
        this.setupBatchCount = setupBatchCount;
    }

    public int getDataBatchCount() {
        return dataBatchCount;
    }

    public void setDataBatchCount(int dataBatchCount) {
        this.dataBatchCount = dataBatchCount;
    }

    public int getFinalizeBatchCount() {
        return finalizeBatchCount;
    }

    public void setFinalizeBatchCount(int finalizeBatchCount) {
        this.finalizeBatchCount = finalizeBatchCount;
    }

    public int getSetupBatchLoaded() {
        return setupBatchLoaded;
    }

    public void setSetupBatchLoaded(int setupBatchLoaded) {
        this.setupBatchLoaded = setupBatchLoaded;
    }

    public int getDataBatchLoaded() {
        return dataBatchLoaded;
    }

    public void setDataBatchLoaded(int dataBatchLoaded) {
        this.dataBatchLoaded = dataBatchLoaded;
    }

    public int getFinalizeBatchLoaded() {
        return finalizeBatchLoaded;
    }

    public void setFinalizeBatchLoaded(int finalizeBatchLoaded) {
        this.finalizeBatchLoaded = finalizeBatchLoaded;
    }

    public int getTableCount() {
        return tableCount;
    }

    public void setTableCount(int tableCount) {
        this.tableCount = tableCount;
    }

    public long getRowsLoaded() {
        return rowsLoaded;
    }

    public void setRowsLoaded(long rowsLoaded) {
        this.rowsLoaded = rowsLoaded;
    }

    public long getRowsCount() {
        return rowsCount;
    }

    public void setRowsCount(long rowsCount) {
        this.rowsCount = rowsCount;
    }

    public boolean isErrorFlag() {
        return errorFlag;
    }

    public void setErrorFlag(boolean errorFlag) {
        this.errorFlag = errorFlag;
    }

    public String getSqlState() {
        return sqlState;
    }

    public void setSqlState(String sqlState) {
        this.sqlState = sqlState;
    }

    public int getSqlCode() {
        return sqlCode;
    }

    public void setSqlCode(int sqlCode) {
        this.sqlCode = sqlCode;
    }

    public String getSqlMessage() {
        return sqlMessage;
    }

    public void setSqlMessage(String sqlMessage) {
        this.sqlMessage = sqlMessage;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public int getTotalBatchCount() {
        return Integer.max(setupBatchCount, 0) + Integer.max(dataBatchCount, 0) + Integer.max(finalizeBatchCount, 0);
    }

    public int getTotalBatchLoaded() {
        return this.setupBatchLoaded + this.dataBatchLoaded + this.finalizeBatchLoaded;
    }

    public int getNumBatchesBulkLoaded() {
        return numBatchesBulkLoaded;
    }

    public void setNumBatchesBulkLoaded(int numBatchesBulkLoaded) {
        this.numBatchesBulkLoaded = numBatchesBulkLoaded;
    }

    public long getNumRowsBulkLoaded() {
        return numRowsBulkLoaded;
    }

    public void setNumRowsBulkLoaded(long numRowsBulkLoaded) {
        this.numRowsBulkLoaded = numRowsBulkLoaded;
    }
}
