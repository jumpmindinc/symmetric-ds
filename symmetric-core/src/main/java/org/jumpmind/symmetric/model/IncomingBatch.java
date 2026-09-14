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

import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.COMMON_FLAG;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.DATA_DELETE_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.DATA_INSERT_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.DATA_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.DATA_UPDATE_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.EXTRACT_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.EXTRACT_DELETE_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.EXTRACT_INSERT_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.EXTRACT_MILLIS;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.EXTRACT_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.EXTRACT_UPDATE_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.FAILED_DATA_ID;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.LOAD_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.LOAD_FLAG;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.LOAD_ID;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.OTHER_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.RELOAD_ROW_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.ROUTER_MILLIS;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.SENT_COUNT;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.TRANSFORM_EXTRACT_MILLIS;
import static org.jumpmind.symmetric.io.data.reader.DataReaderStatistics.TRANSFORM_LOAD_MILLIS;
import static org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants.FALLBACKINSERTCOUNT;
import static org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants.FALLBACKUPDATECOUNT;
import static org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants.FILTERMILLIS;
import static org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants.LOADMILLIS;
import static org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants.ROWCOUNT;
import static org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants.TRANSFORMMILLIS;

import java.util.Date;
import java.util.Map;

import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.reader.DataReaderStatistics;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncomingBatch extends AbstractBatch {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(IncomingBatch.class);
    private long failedRowNumber;
    private long startTime;
    private boolean retry;

    public IncomingBatch() {
    }

    public IncomingBatch(Batch batch) {
        setBatchId(batch.getBatchId());
        setNodeId(batch.getSourceNodeId());
        setChannelId(batch.getChannelId());
        setStatus(Status.LD);
        setBulkLoaderFlag(batch.isBulkLoaderFlag());
    }

    public void setValues(Statistics readerStatistics, Statistics writerStatistics, boolean isSuccess) {
        if (readerStatistics != null) {
            setByteCount(readerStatistics.get(DataReaderStatistics.READ_BYTE_COUNT));
            mergeInjectedBatchStatistics(readerStatistics);
        }
        if (writerStatistics != null) {
            setFilterMillis(writerStatistics.get(FILTERMILLIS));
            setLoadMillis(writerStatistics.get(LOADMILLIS));
            setLoadRowCount(writerStatistics.get(ROWCOUNT));
            setTransformLoadMillis(writerStatistics.get(TRANSFORMMILLIS));
            setFallbackInsertCount(writerStatistics.get(FALLBACKINSERTCOUNT));
            setFallbackUpdateCount(writerStatistics.get(FALLBACKUPDATECOUNT));
            setConflictWinCount(writerStatistics.get(DataWriterStatisticConstants.CONFLICTWINCOUNT));
            setConflictLoseCount(writerStatistics.get(DataWriterStatisticConstants.CONFLICTLOSECOUNT));
            setMissingDeleteCount(writerStatistics.get(DataWriterStatisticConstants.MISSINGDELETECOUNT));
            setIgnoreCount(writerStatistics.get(DataWriterStatisticConstants.IGNORECOUNT));
            setIgnoreRowCount(writerStatistics.get(DataWriterStatisticConstants.IGNOREROWCOUNT));
            setStartTime(writerStatistics.get(DataWriterStatisticConstants.STARTTIME));
            setLastUpdatedTime(new Date());
            if (!isSuccess) {
                failedRowNumber = getLoadRowCount();
                setFailedLineNumber(writerStatistics.get(DataWriterStatisticConstants.LINENUMBER));
            }
            setLoadInsertRowCount(writerStatistics.get(DataWriterStatisticConstants.INSERTCOUNT));
            setLoadUpdateRowCount(writerStatistics.get(DataWriterStatisticConstants.UPDATECOUNT));
            setLoadDeleteRowCount(writerStatistics.get(DataWriterStatisticConstants.DELETECOUNT));
            setTableLoadedCount(writerStatistics.getTableStats());
            if (log.isDebugEnabled()) {
                for (Map.Entry<String, Map<String, Long>> entry : writerStatistics.getTableStats().entrySet()) {
                    for (Map.Entry<String, Long> dmlEntry : entry.getValue().entrySet()) {
                        log.debug("Loaded table: {}, {}, {} rows", entry.getKey(), dmlEntry.getKey(), dmlEntry.getValue());
                    }
                }
            }
        }
    }

    public void mergeInjectedBatchStatistics(Statistics statistics) {
        if (statistics.contains(LOAD_FLAG))
            setLoadFlag(statistics.get(LOAD_FLAG) == 1);
        if (statistics.contains(EXTRACT_COUNT))
            setExtractCount(statistics.get(DataReaderStatistics.EXTRACT_COUNT));
        if (statistics.contains(SENT_COUNT))
            setSentCount(statistics.get(DataReaderStatistics.SENT_COUNT));
        if (statistics.contains(LOAD_COUNT))
            setLoadCount(statistics.get(DataReaderStatistics.LOAD_COUNT));
        if (statistics.contains(LOAD_ID))
            setLoadId(statistics.get(DataReaderStatistics.LOAD_ID));
        if (statistics.contains(COMMON_FLAG))
            setCommonFlag(statistics.get(DataReaderStatistics.COMMON_FLAG) == 1);
        if (statistics.contains(ROUTER_MILLIS))
            setRouterMillis(statistics.get(DataReaderStatistics.ROUTER_MILLIS));
        if (statistics.contains(EXTRACT_MILLIS))
            setExtractMillis(statistics.get(DataReaderStatistics.EXTRACT_MILLIS));
        if (statistics.contains(TRANSFORM_EXTRACT_MILLIS))
            setTransformExtractMillis(statistics.get(DataReaderStatistics.TRANSFORM_EXTRACT_MILLIS));
        if (statistics.contains(TRANSFORM_LOAD_MILLIS))
            setTransformLoadMillis(statistics.get(DataReaderStatistics.TRANSFORM_LOAD_MILLIS));
        if (statistics.contains(RELOAD_ROW_COUNT))
            setReloadRowCount(statistics.get(DataReaderStatistics.RELOAD_ROW_COUNT));
        if (statistics.contains(OTHER_ROW_COUNT))
            setOtherRowCount(statistics.get(DataReaderStatistics.OTHER_ROW_COUNT));
        if (statistics.contains(DATA_ROW_COUNT))
            setDataRowCount(statistics.get(DataReaderStatistics.DATA_ROW_COUNT));
        if (statistics.contains(DATA_INSERT_ROW_COUNT))
            setDataInsertRowCount(statistics.get(DataReaderStatistics.DATA_INSERT_ROW_COUNT));
        if (statistics.contains(DATA_UPDATE_ROW_COUNT))
            setDataUpdateRowCount(statistics.get(DataReaderStatistics.DATA_UPDATE_ROW_COUNT));
        if (statistics.contains(DATA_DELETE_ROW_COUNT))
            setDataDeleteRowCount(statistics.get(DataReaderStatistics.DATA_DELETE_ROW_COUNT));
        if (statistics.contains(EXTRACT_ROW_COUNT))
            setExtractRowCount(statistics.get(DataReaderStatistics.EXTRACT_ROW_COUNT));
        if (statistics.contains(EXTRACT_INSERT_ROW_COUNT))
            setExtractInsertRowCount(statistics.get(DataReaderStatistics.EXTRACT_INSERT_ROW_COUNT));
        if (statistics.contains(EXTRACT_UPDATE_ROW_COUNT))
            setExtractUpdateRowCount(statistics.get(DataReaderStatistics.EXTRACT_UPDATE_ROW_COUNT));
        if (statistics.contains(EXTRACT_DELETE_ROW_COUNT))
            setExtractDeleteRowCount(statistics.get(DataReaderStatistics.EXTRACT_DELETE_ROW_COUNT));
        if (statistics.contains(FAILED_DATA_ID))
            setFailedDataId(statistics.get(DataReaderStatistics.FAILED_DATA_ID));
    }

    public void setNodeBatchId(String value) {
        if (value != null) {
            int splitIndex = value.lastIndexOf("-");
            if (splitIndex > 0) {
                setNodeId(value.substring(0, splitIndex));
                setBatchId(Long.parseLong(value.substring(splitIndex + 1)));
            }
        }
    }

    public boolean isRetry() {
        return retry;
    }

    public void setRetry(boolean isRetry) {
        this.retry = isRetry;
    }

    public long getFailedRowNumber() {
        return failedRowNumber;
    }

    public void setFailedRowNumber(long failedRowNumber) {
        this.failedRowNumber = failedRowNumber;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * An indicator to the incoming batch service as to whether this batch should be saved off.
     * 
     * @return
     */
    public boolean isPersistable() {
        return getBatchId() >= 0;
    }

    @Override
    public String toString() {
        return "IncomingBatch " + getBatchId();
    }
}