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
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
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
import org.jumpmind.symmetric.transport.http.IHttpResumeCache;
import org.jumpmind.symmetric.transport.http.ResumeCacheEntry;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleStagingDataWriter {
    protected final static int MAX_WRITE_LENGTH = 32768;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected CsvReader reader;
    protected ISymmetricEngine engine;
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
    protected ResumeCacheEntry resumeEntry;
    protected StagedResourceETag currentBatchEtag;
    protected long stagedCharCount;

    private SimpleStagingDataWriter(Builder builder) {
        this.reader = new CsvReader(builder.reader);
        this.reader.setEscapeMode(CsvReader.ESCAPE_MODE_BACKSLASH);
        this.reader.setSafetySwitch(false);
        this.engine = builder.engine;
        this.stagingManager = builder.engine.getStagingManager();
        this.memoryThresholdInBytes = builder.memoryThresholdInBytes;
        this.category = builder.category;
        this.batchType = builder.batchType;
        this.sourceNodeId = builder.sourceNodeId;
        this.targetNodeId = builder.targetNodeId;
        this.listeners = builder.listeners;
        this.context = builder.context;
        this.processInfo = builder.processInfo;
        this.resumeEntry = builder.resumeEntry;
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
            if (resumeEntry != null) {
                resource = beginResumedBatch();
            }
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
                    currentBatchEtag = null;
                    stagedCharCount = 0;
                    writeLine(nodeLine);
                    writeLine(binaryLine);
                    writeLine(channelLine);
                    writeLine(line);
                    if (listeners != null && exception == null) {
                        for (IProtocolDataWriterListener listener : listeners) {
                            listener.start(context, batch);
                        }
                    }
                } else if (line.startsWith(CsvConstants.ETAG)) {
                    currentBatchEtag = StagedResourceETag.fromJson(getArgLine(line));
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
                        clearResumeCacheEntry(batch.getBatchId());
                    }
                    batchStats = null;
                    resource = null;
                    currentBatchEtag = null;
                } else if (line.startsWith(CsvConstants.RETRY)) {
                    batch = new Batch(batchType, Long.parseLong(getArgLine(line)), getArgLine(channelLine), getBinaryEncoding(binaryLine),
                            getArgLine(nodeLine), targetNodeId, false);
                    processInfo.setCurrentBatchId(batch.getBatchId());
                    processInfo.setCurrentBatchStartTime(new Date());
                    processInfo.incrementBatchCount();
                    processInfo.setCurrentDataCount(0);
                    processInfo.setTotalDataCount(0);
                    if (resource != null) {
                        resource.close();
                        resource.setState(State.DONE);
                    }
                    resource = getStagedResource();
                    if (resource == null || resource.getState() == State.CREATE) {
                        if (resource != null) {
                            resource.delete();
                        }
                        resource = null;
                        writer = null;
                    }
                    currentBatchEtag = null;
                    stagedCharCount = 0;
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
                        stagedCharCount += size + 1;
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
                if (isResumableInterruption(ex, resource)) {
                    registerForResume(resource);
                } else {
                    resource.delete();
                }
            }
            processInfo.setStatus(ProcessStatus.ERROR);
            /*
             * Just log an error here. We want batches that come before us to continue to process and to be acknowledged
             */
            log.error("Failed to write batch into staging from {}.  {}: {}", context.getContext().get(Constants.DATA_CONTEXT_SOURCE_NODE).toString(),
                    ex.getClass().getName(), ex.getMessage());
        } finally {
            if (invalidLineCount > 0) {
                throw new ProtocolException("Received {} invalid lines from node {} that were outside of a batch", invalidLineCount, sourceNodeId);
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
                stagedCharCount += line.length() + 1;
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

    protected IStagedResource getStagedResource() {
        IStagedResource resource = null;
        boolean isSourceStagingEnabled = engine.getConfigurationService().isUseSourceStagingEnabled(batch.getSourceNodeId());
        ISymmetricEngine sourceEngine = isSourceStagingEnabled ? AbstractSymmetricEngine.findEngineByNodeId(batch.getSourceNodeId()) : null;
        if (sourceEngine != null) {
            Batch outgoingBatch = new Batch(BatchType.EXTRACT, batch.getBatchId(), batch.getChannelId(), batch.getBinaryEncoding(),
                    batch.getSourceNodeId(), batch.getTargetNodeId(), batch.isCommon());
            resource = sourceEngine.getStagingManager().find(Constants.STAGING_CATEGORY_OUTGOING, outgoingBatch.getStagedLocation(), batch.getBatchId());
            if (resource == null) {
                outgoingBatch.setCommon(true);
                resource = sourceEngine.getStagingManager().find(Constants.STAGING_CATEGORY_OUTGOING, outgoingBatch.getStagedLocation(), batch.getBatchId());
            }
        }
        if (resource == null) {
            resource = stagingManager.find(category, batch.getStagedLocation(), batch.getBatchId());
        }
        return resource;
    }

    /**
     * A confirmed resumed ({@code 206}) response contains only the remaining row data for the one batch being resumed, not its preamble
     * (NODEID/BINARY/CHANNEL/BATCH/ETAG lines) — that part was already received and staged on the prior, interrupted attempt. So unlike a fresh batch, there's
     * no {@code BATCH} line here to trigger the normal setup; instead, reconstruct the batch's identity from {@link #resumeEntry} and reopen its existing local
     * partial resource in append mode, before any lines are read from the wire.
     *
     * @return the reopened local resource, or {@code null} if it was missing or already finalized, in which case this pull cannot complete that batch and a
     *         later pull will retry it in full
     */
    protected IStagedResource beginResumedBatch() {
        BinaryEncoding binaryEncoding = resumeEntry.getBinaryEncoding() != null ? BinaryEncoding.valueOf(resumeEntry.getBinaryEncoding()) : null;
        Batch candidateBatch = new Batch(batchType, resumeEntry.getBatchId(), resumeEntry.getChannelId(),
                binaryEncoding, sourceNodeId, targetNodeId, false);
        IStagedResource existingResource = stagingManager.find(category, candidateBatch.getStagedLocation(), candidateBatch.getBatchId());
        if (existingResource == null || existingResource.getState() != State.CREATE) {
            log.warn("Resume requested for batch {} from node {}, but the local partial staged resource was missing or already finalized ({}). "
                    + "This pull cannot complete that batch; a subsequent pull will retry it in full.",
                    candidateBatch.getBatchId(), sourceNodeId, existingResource == null ? "not found" : existingResource.getState());
            clearResumeCacheEntry(resumeEntry.getBatchId());
            return null;
        }
        batch = candidateBatch;
        writer = existingResource.getWriter(memoryThresholdInBytes, true);
        currentBatchEtag = resumeEntry.getEtag();
        stagedCharCount = 0;
        processInfo.setCurrentBatchId(batch.getBatchId());
        processInfo.setCurrentBatchStartTime(new Date());
        processInfo.incrementBatchCount();
        processInfo.setCurrentDataCount(0);
        processInfo.setTotalDataCount(0);
        if (listeners != null) {
            for (IProtocolDataWriterListener listener : listeners) {
                listener.start(context, batch);
            }
        }
        return existingResource;
    }

    /**
     * @return whether {@code ex} represents a connection-level failure (not a data/protocol error) for a batch that's genuinely eligible to be preserved for a
     *         resumed retry: resume is enabled, the batch's staged content is file-backed, and its ETag was captured before the interruption
     */
    protected boolean isResumableInterruption(Exception ex, IStagedResource resource) {
        return ex instanceof IOException && batch != null && currentBatchEtag != null && resource.isFileResource()
                && engine.getParameterService().is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)
                && getResumeCache() != null;
    }

    protected void registerForResume(IStagedResource resource) {
        resource.close();
        long receivedCount = (resumeEntry != null ? resumeEntry.getReceivedCount() : 0) + stagedCharCount;
        IHttpResumeCache resumeCache = getResumeCache();
        if (resumeCache == null) {
            return;
        }
        resumeCache.put(sourceNodeId, batch.getBatchId(), ResumeCacheEntry.builder()
                .nodeId(sourceNodeId)
                .batchId(batch.getBatchId())
                .etag(currentBatchEtag)
                .receivedCount(receivedCount)
                .channelId(batch.getChannelId())
                .binaryEncoding(batch.getBinaryEncoding() != null ? batch.getBinaryEncoding().name() : null)
                .cachedAtTime(System.currentTimeMillis())
                .queue(processInfo.getQueue())
                .build());
        log.info("Preserving partially-received batch {} from node {} for a resumed retry ({} characters received).",
                batch.getBatchId(), sourceNodeId, receivedCount);
    }

    protected void clearResumeCacheEntry(long batchId) {
        IHttpResumeCache resumeCache = getResumeCache();
        if (resumeCache != null) {
            resumeCache.remove(sourceNodeId, batchId);
        }
    }

    protected IHttpResumeCache getResumeCache() {
        return engine.getTransportManager() != null ? engine.getTransportManager().getResumeCache() : null;
    }

    public static Builder builder() {
        return new Builder();
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
            return Strings.CS.equals(catalogLine, t.catalogLine) && Strings.CS.equals(schemaLine, t.schemaLine)
                    && Strings.CS.equals(tableLine, t.tableLine);
        }

        @Override
        public int hashCode() {
            return (catalogLine + "." + schemaLine + "." + tableLine).hashCode();
        }
    }

    public static class Builder {
        private ProcessInfo processInfo;
        private BufferedReader reader;
        private ISymmetricEngine engine;
        private String category;
        private long memoryThresholdInBytes;
        private BatchType batchType;
        private String sourceNodeId;
        private String targetNodeId;
        private DataContext context;
        private ResumeCacheEntry resumeEntry;
        private IProtocolDataWriterListener[] listeners = new IProtocolDataWriterListener[0];

        public Builder processInfo(ProcessInfo processInfo) {
            this.processInfo = processInfo;
            return this;
        }

        public Builder reader(BufferedReader reader) {
            this.reader = reader;
            return this;
        }

        public Builder engine(ISymmetricEngine engine) {
            this.engine = engine;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder memoryThresholdInBytes(long memoryThresholdInBytes) {
            this.memoryThresholdInBytes = memoryThresholdInBytes;
            return this;
        }

        public Builder batchType(BatchType batchType) {
            this.batchType = batchType;
            return this;
        }

        public Builder sourceNodeId(String sourceNodeId) {
            this.sourceNodeId = sourceNodeId;
            return this;
        }

        public Builder targetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
            return this;
        }

        public Builder context(DataContext context) {
            this.context = context;
            return this;
        }

        /**
         * @param resumeEntry
         *            non-null only when this pull is a confirmed resume of one specific, previously-interrupted batch; {@code null} for every normal (fresh)
         *            pull
         */
        public Builder resumeEntry(ResumeCacheEntry resumeEntry) {
            this.resumeEntry = resumeEntry;
            return this;
        }

        public Builder listeners(IProtocolDataWriterListener... listeners) {
            this.listeners = listeners;
            return this;
        }

        public SimpleStagingDataWriter build() {
            return new SimpleStagingDataWriter(this);
        }
    }
}
