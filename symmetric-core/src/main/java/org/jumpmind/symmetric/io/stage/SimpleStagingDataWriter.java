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
package org.jumpmind.symmetric.io.stage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.csv.CsvReader;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.io.data.writer.IProtocolDataWriterListener;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleStagingDataWriter {
    protected final static int MAX_WRITE_LENGTH = 32768;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected CsvReader reader;
    protected IStagingManager stagingManager;
    protected IProtocolDataWriterListener[] listeners;
    protected long memoryThresholdInBytes;
    protected String category;
    protected BatchType batchType;
    protected String sourceNodeId;
    protected String targetNodeId;
    protected DataContext context;
    protected ProcessInfo processInfo;
    protected BufferedWriter writer;
    protected Batch batch;
    protected long invalidLineCount;
    protected Exception exception;

    public SimpleStagingDataWriter(ProcessInfo processInfo, BufferedReader reader, IStagingManager stagingManager, String category, long memoryThresholdInBytes,
            BatchType batchType, String sourceNodeId, String targetNodeId, DataContext context, IProtocolDataWriterListener... listeners) {
        this.reader = new CsvReader(reader);
        this.reader.setEscapeMode(CsvReader.ESCAPE_MODE_BACKSLASH);
        this.reader.setSafetySwitch(false);
        this.stagingManager = stagingManager;
        this.memoryThresholdInBytes = memoryThresholdInBytes;
        this.category = category;
        this.batchType = batchType;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.listeners = listeners;
        this.context = context;
        this.processInfo = processInfo;
    }

    public void process() throws IOException {
        IStagedResource resource = null;
        try {
            String catalogLine = null, schemaLine = null, nodeLine = null, binaryLine = null, channelLine = null;
            TableLine tableLine = null;
            Map<TableLine, TableLine> syncTableLines = new HashMap<TableLine, TableLine>();
            Map<TableLine, TableLine> batchTableLines = new HashMap<TableLine, TableLine>();
            String line = null;
            long startTime = System.currentTimeMillis(), ts = startTime, lineCount = 0;
            String batchStatsColumnsLine = null;
            String batchStatsLine = null;
            Statistics batchStats = null;
            while (reader.readRecord()) {
                line = reader.getRawRecord();
                if (line.startsWith(CsvConstants.CATALOG)) {
                    catalogLine = line;
                    writeLine(line);
                } else if (line.startsWith(CsvConstants.SCHEMA)) {
                    schemaLine = line;
                    writeLine(line);
                } else if (line.startsWith(CsvConstants.TABLE)) {
                    tableLine = new TableLine(catalogLine, schemaLine, line);
                    TableLine batchTableLine = batchTableLines.get(tableLine);
                    if (batchTableLine != null) {
                        tableLine = batchTableLine;
                        writeLine(line);
                    } else {
                        TableLine syncTableLine = syncTableLines.get(tableLine);
                        if (syncTableLine != null) {
                            tableLine = syncTableLine;
                            writeLine(tableLine.catalogLine);
                            writeLine(tableLine.schemaLine);
                            writeLine(line);
                            writeLine(tableLine.keysLine);
                            writeLine(tableLine.columnsLine);
                        } else {
                            syncTableLines.put(tableLine, tableLine);
                            batchTableLines.put(tableLine, tableLine);
                            writeLine(line);
                        }
                    }
                } else if (line.startsWith(CsvConstants.KEYS)) {
                    tableLine.keysLine = line;
                    writeLine(line);
                } else if (line.startsWith(CsvConstants.COLUMNS)) {
                    tableLine.columnsLine = line;
                    writeLine(line);
                } else if (line.startsWith(CsvConstants.BATCH)) {
                    batch = new Batch(batchType, Long.parseLong(getArgLine(line)), getArgLine(channelLine), getBinaryEncoding(binaryLine),
                            getArgLine(nodeLine), targetNodeId, false);
                    processInfo.setCurrentBatchId(batch.getBatchId());
                    processInfo.setCurrentBatchStartTime(new Date());
                    processInfo.incrementBatchCount();
                    processInfo.setCurrentDataCount(0);
                    processInfo.setTotalDataCount(0);
                    String location = batch.getStagedLocation();
                    if (resource != null) {
                        resource.close();
                        resource.setState(State.DONE);
                    }
                    resource = stagingManager.create(category, location, batch.getBatchId());
                    writer = resource.getWriter(memoryThresholdInBytes);
                    writeLine(nodeLine);
                    writeLine(binaryLine);
                    writeLine(channelLine);
                    writeLine(line);
                    if (listeners != null && exception == null) {
                        for (IProtocolDataWriterListener listener : listeners) {
                            listener.start(context, batch);
                        }
                    }
                } else if (line.startsWith(CsvConstants.COMMIT)) {
                    if (writer != null) {
                        writeLine(line);
                        resource.close();
                        resource.setState(State.DONE);
                        writer = null;
                    }
                    batchTableLines.clear();
                    if (batch != null) {
                        batch.setStatistics(batchStats);
                        if (listeners != null && exception == null) {
                            for (IProtocolDataWriterListener listener : listeners) {
                                listener.end(context, batch, resource);
                            }
                        }
                    }
                    batchStats = null;
                    resource = null;
                } else if (line.startsWith(CsvConstants.RETRY)) {
                    batch = new Batch(batchType, Long.parseLong(getArgLine(line)), getArgLine(channelLine), getBinaryEncoding(binaryLine),
                            getArgLine(nodeLine), targetNodeId, false);
                    processInfo.setCurrentBatchId(batch.getBatchId());
                    processInfo.setCurrentBatchStartTime(new Date());
                    processInfo.incrementBatchCount();
                    processInfo.setCurrentDataCount(0);
                    processInfo.setTotalDataCount(0);
                    String location = batch.getStagedLocation();
                    if (resource != null) {
                        resource.close();
                        resource.setState(State.DONE);
                    }
                    resource = stagingManager.find(category, location, batch.getBatchId());
                    if (resource == null || resource.getState() == State.CREATE) {
                        if (resource != null) {
                            resource.delete();
                        }
                        resource = null;
                        writer = null;
                    }
                    if (log.isDebugEnabled()) {
                        debugLine(nodeLine);
                        debugLine(binaryLine);
                        debugLine(channelLine);
                        debugLine(line);
                    }
                    if (listeners != null && exception == null) {
                        for (IProtocolDataWriterListener listener : listeners) {
                            listener.start(context, batch);
                        }
                    }
                } else if (line.startsWith(CsvConstants.NODEID)) {
                    nodeLine = line;
                } else if (line.startsWith(CsvConstants.BINARY)) {
                    binaryLine = line;
                } else if (line.startsWith(CsvConstants.CHANNEL)) {
                    channelLine = line;
                } else if (line.startsWith(CsvConstants.STATS_COLUMNS)) {
                    batchStatsColumnsLine = line;
                } else if (line.startsWith(CsvConstants.STATS)) {
                    batchStatsLine = line;
                    batchStats = new Statistics();
                    putStats(batchStats, batchStatsColumnsLine, batchStatsLine);
                    processInfo.setTotalDataCount(batchStats.get("DATA_ROW_COUNT"));
                } else if (writer == null) {
                    invalidLineCount++;
                    if (log.isDebugEnabled() && line != null) {
                        log.debug("Invalid line received outside of a batch: {}", line);
                    }
                } else {
                    TableLine batchLine = batchTableLines.get(tableLine);
                    if (batchLine == null || (batchLine != null && batchLine.columnsLine == null)) {
                        TableLine syncLine = syncTableLines.get(tableLine);
                        if (syncLine != null) {
                            log.debug("Injecting keys and columns to be backwards compatible");
                            if (batchLine == null) {
                                batchLine = syncLine;
                                batchTableLines.put(batchLine, batchLine);
                                writeLine(batchLine.tableLine);
                            }
                            batchLine.keysLine = syncLine.keysLine;
                            writeLine(syncLine.keysLine);
                            batchLine.columnsLine = syncLine.columnsLine;
                            writeLine(syncLine.columnsLine);
                        }
                    }
                    if (line.startsWith(CsvConstants.INSERT) || line.startsWith(CsvConstants.DELETE) || line.startsWith(CsvConstants.UPDATE)
                            || line.startsWith(CsvConstants.CREATE) || line.startsWith(CsvConstants.SQL)
                            || line.startsWith(CsvConstants.BSH)) {
                        processInfo.incrementCurrentDataCount();
                    }
                    int size = line.length();
                    if (size > MAX_WRITE_LENGTH) {
                        log.debug("Exceeded max line length with {}", size);
                        for (int i = 0; i < size; i = i + MAX_WRITE_LENGTH) {
                            int end = i + MAX_WRITE_LENGTH;
                            writer.append(line, i, end < size ? end : size);
                        }
                        writer.append("\n");
                    } else {
                        writeLine(line);
                    }
                }
                lineCount++;
                if (System.currentTimeMillis() - ts > 60000) {
                    log.info(
                            "Batch '{}', from node '{}', for process 'transfer to stage' has been processing for {} seconds.  The following stats have been gathered: {}",
                            new Object[] { (batch != null ? batch.getBatchId() : "?"), sourceNodeId,
                                    (System.currentTimeMillis() - startTime) / 1000,
                                    "LINES=" + lineCount + ", BYTES=" + ((resource == null) ? 0 : resource.getSize()) });
                    ts = System.currentTimeMillis();
                }
            }
            if (resource != null) {
                resource.close();
                resource.setState(State.DONE);
            }
            processInfo.setStatus(ProcessStatus.OK);
        } catch (Exception ex) {
            if (exception == null) {
                exception = ex;
            }
            if (resource != null) {
                resource.delete();
            }
            processInfo.setStatus(ProcessStatus.ERROR);
            /*
             * Just log an error here. We want batches that come before us to continue to process and to be acknowledged
             */
            log.error("Failed to write batch into staging from {}.  {}: {}", context.getContext().get(Constants.DATA_CONTEXT_SOURCE_NODE).toString(),
                    ex.getClass().getName(), ex.getMessage());
        } finally {
            if (invalidLineCount > 0) {
                log.warn("Received {} invalid lines from node {} that were outside of a batch", invalidLineCount, sourceNodeId);
            }
        }
    }

    public Exception getException() {
        return exception;
    }

    protected String getArgLine(String line) throws IOException {
        if (line != null) {
            int i = line.indexOf(",");
            if (i >= 0) {
                return line.substring(i + 1).trim();
            }
            throw new IOException("Invalid token line in CSV: " + line);
        }
        return null;
    }

    protected BinaryEncoding getBinaryEncoding(String line) throws IOException {
        String value = getArgLine(line);
        if (value != null) {
            return BinaryEncoding.valueOf(value);
        }
        return null;
    }

    protected void writeLine(String line) throws IOException {
        if (line != null) {
            if (log.isDebugEnabled()) {
                log.debug("Writing staging data: {}", line);
            }
            if (writer != null) {
                writer.write(line);
                writer.write("\n");
            } else {
                exception = new ProtocolException("Batch data is corrupt from node " + sourceNodeId + " because no batch ID was present");
                processInfo.setStatus(ProcessStatus.ERROR);
            }
        }
    }

    protected void putStats(Statistics stats, String columnsString, String statsString) {
        String statsColumns[] = StringUtils.split(columnsString, ',');
        String statsValues[] = StringUtils.split(statsString, ',');
        if (statsValues != null && statsColumns != null) {
            for (int i = 1; i < statsColumns.length; i++) {
                String column = statsColumns[i];
                if (i < statsValues.length) {
                    long stat = Long.parseLong(statsValues[i]);
                    stats.set(column, stat);
                }
            }
        }
    }

    protected void debugLine(String line) {
        if (line != null) {
            log.debug("Received: {}", line);
        }
    }

    static class TableLine {
        String catalogLine;
        String schemaLine;
        String tableLine;
        String keysLine;
        String columnsLine;

        public TableLine(String catalogLine, String schemaLine, String tableLine) {
            this.catalogLine = catalogLine;
            this.schemaLine = schemaLine;
            this.tableLine = tableLine;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof TableLine)) {
                return false;
            }
            TableLine t = (TableLine) o;
            return StringUtils.equals(catalogLine, t.catalogLine) && StringUtils.equals(schemaLine, t.schemaLine)
                    && StringUtils.equals(tableLine, t.tableLine);
        }

        @Override
        public int hashCode() {
            return (catalogLine + "." + schemaLine + "." + tableLine).hashCode();
        }
    }
}
