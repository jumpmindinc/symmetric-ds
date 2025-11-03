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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.DataEventType;

public class AbstractBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        OK("Ok"), ER("Error"), RQ("Request"), NE("New"), QY("Querying"), SE("Sending"), LD("Loading"), RT("Routing"), IG("Ignored"), RS(
                "Resend"), XX("Unknown"), LS("LoadSetup");

        private String description;

        Status(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }

        public static String getNameFromDescription(String description) {
            for (Status status : Status.values()) {
                if (status.toString().equals(description)) {
                    return status.name();
                }
            }
            return null;
        }
    }

    private long batchId = -1;
    private String nodeId;
    private String channelId;
    private boolean errorFlag;
    private long routerMillis;
    private long networkMillis;
    private long filterMillis;
    private long loadMillis;
    private long extractMillis;
    private long transformExtractMillis;
    private long transformLoadMillis;
    private long byteCount;
    private long ignoreCount;
    private String sqlState;
    private int sqlCode;
    private String sqlMessage;
    private String lastUpdatedHostName;
    private Date lastUpdatedTime;
    private Date createTime;
    private String createBy;
    private String summary;
    private Status status;
    private boolean loadFlag;
    private long extractCount;
    private long sentCount;
    private long loadCount;
    private long reloadRowCount;
    private long otherRowCount;
    private long dataRowCount;
    private long dataInsertRowCount;
    private long dataUpdateRowCount;
    private long dataDeleteRowCount;
    private long oldDataRowCount = 0;
    private long oldByteCount = 0;
    private long oldFilterMillis = 0;
    private long oldExtractMillis = 0;
    private long oldLoadMillis = 0;
    private long oldNetworkMillis = 0;
    private long loadId = -1;
    private boolean commonFlag;
    private boolean bulkLoaderFlag;
    private long fallbackInsertCount;
    private long fallbackUpdateCount;
    private long conflictWinCount;
    private long conflictLoseCount;
    private long ignoreRowCount;
    private long missingDeleteCount;
    private long skipCount;
    private long loadRowCount;
    private long loadInsertRowCount;
    private long loadUpdateRowCount;
    private long loadDeleteRowCount;
    private long extractRowCount;
    private long extractInsertRowCount;
    private long extractUpdateRowCount;
    private long extractDeleteRowCount;
    private long failedDataId;
    private long failedLineNumber;
    private transient Map<String, Integer> tableCounts = new LinkedHashMap<String, Integer>();
    private transient Map<String, Integer> fileCounts = new LinkedHashMap<String, Integer>();
    private transient long processedRowCount;
    private Map<String, Map<String, Long>> tableLoadedCount;
    private Map<String, Map<String, Long>> tableExtractedCount;

    public void resetStats() {
        // save off old stats in case there
        // is an error and we want to be able to
        // restore the previous stats
        this.oldExtractMillis = this.extractMillis;
        this.oldDataRowCount = this.dataRowCount;
        this.oldByteCount = getByteCount();
        this.oldNetworkMillis = getNetworkMillis();
        this.oldFilterMillis = getFilterMillis();
        this.oldLoadMillis = getLoadMillis();
        this.extractMillis = 0;
        this.dataRowCount = 0;
        setByteCount(0);
        setNetworkMillis(0);
        setFilterMillis(0);
        setLoadMillis(0);
    }

    public void resetRouterStats() {
        dataRowCount = 0;
        dataInsertRowCount = 0;
        dataUpdateRowCount = 0;
        dataDeleteRowCount = 0;
        otherRowCount = 0;
        reloadRowCount = 0;
        tableCounts.clear();
        fileCounts.clear();
    }

    public void revertStatsOnError() {
        if (this.oldDataRowCount > 0) {
            this.extractMillis = this.oldExtractMillis;
            this.dataRowCount = this.oldDataRowCount;
            setByteCount(this.oldByteCount);
            setNetworkMillis(this.oldNetworkMillis);
            setFilterMillis(this.oldFilterMillis);
            setLoadMillis(this.oldLoadMillis);
        }
    }

    public void resetExtractRowStats() {
        this.extractRowCount = 0;
        this.extractInsertRowCount = 0;
        this.extractUpdateRowCount = 0;
        this.extractDeleteRowCount = 0;
    }

    public long getBatchId() {
        return batchId;
    }

    public void setBatchId(long batchId) {
        this.batchId = batchId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getNodeBatchId() {
        return nodeId + "-" + batchId;
    }

    public void setErrorFlag(boolean errorFlag) {
        this.errorFlag = errorFlag;
    }

    public boolean isErrorFlag() {
        return errorFlag;
    }

    public long getRouterMillis() {
        return routerMillis;
    }

    public void setRouterMillis(long routerMillis) {
        this.routerMillis = routerMillis;
    }

    public long getNetworkMillis() {
        return networkMillis;
    }

    public void setNetworkMillis(long networkMillis) {
        this.networkMillis = networkMillis;
    }

    public long getFilterMillis() {
        return filterMillis;
    }

    public void setFilterMillis(long filterMillis) {
        this.filterMillis = filterMillis;
    }

    public long getLoadMillis() {
        return loadMillis;
    }

    public void setLoadMillis(long loadMillis) {
        this.loadMillis = loadMillis;
    }

    public void setExtractMillis(long extractMillis) {
        this.extractMillis = extractMillis;
    }

    public long getExtractMillis() {
        return extractMillis;
    }

    public long getTransformExtractMillis() {
        return transformExtractMillis;
    }

    public void setTransformExtractMillis(long transformExtractMillis) {
        this.transformExtractMillis = transformExtractMillis;
    }

    public long getTransformLoadMillis() {
        return transformLoadMillis;
    }

    public void setTransformLoadMillis(long transformLoadMillis) {
        this.transformLoadMillis = transformLoadMillis;
    }

    public long getByteCount() {
        return byteCount;
    }

    public void setByteCount(long byteCount) {
        this.byteCount = byteCount;
    }

    public String getStagedLocation() {
        return Batch.getStagedLocation(false, nodeId, batchId);
    }

    public void incrementByteCount(int size) {
        this.byteCount += size;
    }

    public void setIgnoreCount(long ignoreCount) {
        this.ignoreCount = ignoreCount;
    }

    public void incrementIgnoreCount() {
        this.ignoreCount++;
    }

    public long getIgnoreCount() {
        return ignoreCount;
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

    public void setSqlMessage(Throwable t) {
        this.sqlMessage = t.getMessage();
        if (this.sqlMessage == null) {
            this.sqlMessage = t.getClass().getName();
        }
    }

    public String getLastUpdatedHostName() {
        return lastUpdatedHostName;
    }

    public void setLastUpdatedHostName(String lastUpdatedHostName) {
        this.lastUpdatedHostName = lastUpdatedHostName;
    }

    public Date getLastUpdatedTime() {
        return lastUpdatedTime;
    }

    public void setLastUpdatedTime(Date lastUpdatedTime) {
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getCreateBy() {
        return createBy;
    }

    public Map<String, Integer> getTableCounts() {
        return new LinkedHashMap<String, Integer>(tableCounts);
    }

    public Map<String, Integer> getFileCounts() {
        return new LinkedHashMap<String, Integer>(fileCounts);
    }

    public String getSummary() {
        if ((summary == null || summary.length() == 0) && tableCounts != null) {
            summary = buildBatchSummary();
        }
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    protected String buildBatchSummary() {
        final int SIZE = 254;
        StringBuilder buff = new StringBuilder(SIZE);
        if (tableCounts.isEmpty()) {
            for (Entry<String, Integer> fileCount : fileCounts.entrySet()) {
                buff.append(fileCount.getKey()).append(", ");
            }
            if (buff.length() > 2) {
                buff.setLength(buff.length() - 2);
            }
        } else {
            for (Entry<String, Integer> tableCount : tableCounts.entrySet()) {
                buff.append(tableCount.getKey()).append(", ");
            }
            if (buff.length() > 2) {
                buff.setLength(buff.length() - 2);
            }
        }
        return StringUtils.abbreviate(buff.toString(), SIZE);
    }

    public void incrementTableCount(String tableName) {
        Integer count = tableCounts.get(tableName);
        if (count == null) {
            count = Integer.valueOf(0);
        }
        tableCounts.put(tableName, ++count);
        summary = null;
    }

    public void incrementFileCount(String fileName) {
        Integer count = fileCounts.get(fileName);
        if (count == null) {
            count = Integer.valueOf(0);
        }
        fileCounts.put(fileName, ++count);
        summary = null;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setStatusFromString(String status) {
        try {
            this.status = Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            this.status = Status.XX;
        }
    }

    public void setLoadFlag(boolean loadFlag) {
        this.loadFlag = loadFlag;
    }

    public boolean isLoadFlag() {
        return loadFlag;
    }

    public void setExtractCount(long extractCount) {
        this.extractCount = extractCount;
    }

    public long getExtractCount() {
        return extractCount;
    }

    public void setSentCount(long sentCount) {
        this.sentCount = sentCount;
    }

    public long getSentCount() {
        return sentCount;
    }

    public void setLoadCount(long loadCount) {
        this.loadCount = loadCount;
    }

    public long getLoadCount() {
        return loadCount;
    }

    public void setReloadRowCount(long reloadRowCount) {
        this.reloadRowCount = reloadRowCount;
    }

    public long getReloadRowCount() {
        return reloadRowCount;
    }

    public void setOtherRowCount(long otherRowCount) {
        this.otherRowCount = otherRowCount;
    }

    public long getOtherRowCount() {
        return otherRowCount;
    }

    public void setDataUpdateRowCount(long dataUpdateRowCount) {
        this.dataUpdateRowCount = dataUpdateRowCount;
    }

    public long getDataUpdateRowCount() {
        return dataUpdateRowCount;
    }

    public void setDataDeleteRowCount(long dataDeleteRowCount) {
        this.dataDeleteRowCount = dataDeleteRowCount;
    }

    public long getDataDeleteRowCount() {
        return dataDeleteRowCount;
    }

    public void incrementRowCount(DataEventType type) {
        switch (type) {
            case RELOAD:
                reloadRowCount++;
                break;
            case INSERT:
                dataInsertRowCount++;
                break;
            case UPDATE:
                dataUpdateRowCount++;
                break;
            case DELETE:
                dataDeleteRowCount++;
                break;
            default:
                otherRowCount++;
                break;
        }
    }

    public void incrementExtractRowCount(DataEventType type) {
        switch (type) {
            case INSERT:
                extractInsertRowCount++;
                break;
            case UPDATE:
                extractUpdateRowCount++;
                break;
            case DELETE:
                extractDeleteRowCount++;
                break;
            default:
                break;
        }
    }

    public void incrementExtractRowCount() {
        this.extractRowCount++;
    }

    public void setDataInsertRowCount(long dataInsertRowCount) {
        this.dataInsertRowCount = dataInsertRowCount;
    }

    public long getDataInsertRowCount() {
        return dataInsertRowCount;
    }

    public long getDataRowCount() {
        return dataRowCount;
    }

    public void setDataRowCount(long dataRowCount) {
        this.dataRowCount = dataRowCount;
    }

    public void incrementDataRowCount() {
        this.dataRowCount++;
    }

    public void incrementDataInsertRowCount() {
        this.dataInsertRowCount++;
    }

    public long totalRowCount() {
        return dataInsertRowCount + dataUpdateRowCount + dataDeleteRowCount + otherRowCount;
    }

    public void setLoadId(long loadId) {
        this.loadId = loadId;
    }

    public long getLoadId() {
        return loadId;
    }

    public void setCommonFlag(boolean commonFlag) {
        this.commonFlag = commonFlag;
    }

    public boolean isCommonFlag() {
        return commonFlag;
    }

    public long getFallbackInsertCount() {
        return fallbackInsertCount;
    }

    public void setFallbackInsertCount(long fallbackInsertCount) {
        this.fallbackInsertCount = fallbackInsertCount;
    }

    public long getFallbackUpdateCount() {
        return fallbackUpdateCount;
    }

    public void setFallbackUpdateCount(long fallbackUpdateCount) {
        this.fallbackUpdateCount = fallbackUpdateCount;
    }

    public long getConflictWinCount() {
        return conflictWinCount;
    }

    public void setConflictWinCount(long conflictWinCount) {
        this.conflictWinCount = conflictWinCount;
    }

    public long getConflictLoseCount() {
        return conflictLoseCount;
    }

    public void setConflictLoseCount(long conflictLoseCount) {
        this.conflictLoseCount = conflictLoseCount;
    }

    public long getMissingDeleteCount() {
        return missingDeleteCount;
    }

    public void setMissingDeleteCount(long missingDeleteCount) {
        this.missingDeleteCount = missingDeleteCount;
    }

    public void setSkipCount(long skipCount) {
        this.skipCount = skipCount;
    }

    public long getSkipCount() {
        return skipCount;
    }

    public long getIgnoreRowCount() {
        return ignoreRowCount;
    }

    public void incrementIgnoreRowCount() {
        this.ignoreRowCount++;
    }

    public void setIgnoreRowCount(long ignoreRowCount) {
        this.ignoreRowCount = ignoreRowCount;
    }

    public long getLoadRowCount() {
        return loadRowCount;
    }

    public void setLoadRowCount(long loadRowCount) {
        this.loadRowCount = loadRowCount;
    }

    public long getLoadInsertRowCount() {
        return loadInsertRowCount;
    }

    public void setLoadInsertRowCount(long loadInsertRowCount) {
        this.loadInsertRowCount = loadInsertRowCount;
    }

    public long getLoadUpdateRowCount() {
        return loadUpdateRowCount;
    }

    public void setLoadUpdateRowCount(long loadUpdateRowCount) {
        this.loadUpdateRowCount = loadUpdateRowCount;
    }

    public long getLoadDeleteRowCount() {
        return loadDeleteRowCount;
    }

    public void setLoadDeleteRowCount(long loadDeleteRowCount) {
        this.loadDeleteRowCount = loadDeleteRowCount;
    }

    public long getExtractRowCount() {
        return extractRowCount;
    }

    public void setExtractRowCount(long extractRowCount) {
        this.extractRowCount = extractRowCount;
    }

    public long getExtractInsertRowCount() {
        return extractInsertRowCount;
    }

    public void setExtractInsertRowCount(long extractInsertRowCount) {
        this.extractInsertRowCount = extractInsertRowCount;
    }

    public long getExtractUpdateRowCount() {
        return extractUpdateRowCount;
    }

    public void setExtractUpdateRowCount(long extractUpdateRowCount) {
        this.extractUpdateRowCount = extractUpdateRowCount;
    }

    public long getExtractDeleteRowCount() {
        return extractDeleteRowCount;
    }

    public void setExtractDeleteRowCount(long extractDeleteRowCount) {
        this.extractDeleteRowCount = extractDeleteRowCount;
    }

    public long getFailedDataId() {
        return failedDataId;
    }

    public void setFailedDataId(long failedDataId) {
        this.failedDataId = failedDataId;
    }

    public void setFailedLineNumber(long failedLineNumber) {
        this.failedLineNumber = failedLineNumber;
    }

    public long getFailedLineNumber() {
        return failedLineNumber;
    }

    public long getProcessedRowCount() {
        return processedRowCount;
    }

    public void setProcessedRowCount(long processedRowCount) {
        this.processedRowCount = processedRowCount;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof AbstractBatch)) {
            return false;
        }
        AbstractBatch b = (AbstractBatch) o;
        return getBatchId() == b.getBatchId() && Strings.CS.equals(getNodeId(), b.getNodeId());
    }

    @Override
    public int hashCode() {
        return (String.valueOf(getBatchId()) + "-" + getNodeId()).hashCode();
    }

    public Map<String, Map<String, Long>> getTableLoadedCount() {
        return tableLoadedCount;
    }

    public void setTableLoadedCount(Map<String, Map<String, Long>> tableLoadedCount) {
        this.tableLoadedCount = tableLoadedCount;
    }

    public Map<String, Map<String, Long>> getTableExtractedCount() {
        return tableExtractedCount;
    }

    public void setTableExtractedCount(Map<String, Map<String, Long>> tableExtractedCount) {
        this.tableExtractedCount = tableExtractedCount;
    }

    public boolean isBulkLoaderFlag() {
        return bulkLoaderFlag;
    }

    public void setBulkLoaderFlag(boolean bulkLoaderFlag) {
        this.bulkLoaderFlag = bulkLoaderFlag;
    }
}
