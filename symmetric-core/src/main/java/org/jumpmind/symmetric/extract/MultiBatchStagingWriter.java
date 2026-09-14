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
package org.jumpmind.symmetric.extract;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.symmetric.io.data.writer.IProtocolDataWriterListener;
import org.jumpmind.symmetric.io.data.writer.StagingDataWriter;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiBatchStagingWriter implements IDataWriter {
    public static final String CONTEXT_IS_SINGLE_LOCAL_TARGET = "isSingleLocalTarget";
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ISymmetricEngine engine;
    protected ExtractRequest request;
    protected long maxBatchSize;
    protected IDataWriter currentDataWriter;
    protected List<OutgoingBatch> batches;
    protected List<OutgoingBatch> finishedBatches;
    protected Map<Batch, Statistics> finishedStats;
    protected IStagingManager stagingManager;
    protected String sourceNodeId;
    protected DataContext context;
    protected Table table;
    protected OutgoingBatch outgoingBatch;
    protected Batch batch;
    protected boolean inError = false;
    protected ProcessInfo processInfo;
    protected long startTime, ts, rowCount, byteCount;
    protected List<ExtractRequest> childRequests;
    protected Map<Long, OutgoingBatch> childBatches;
    protected long memoryThresholdInBytes;
    protected boolean isRestarted;
    protected boolean isSingleLocalTarget;
    protected boolean synchronizeJobs;

    public MultiBatchStagingWriter(ISymmetricEngine engine, ExtractRequest request, List<ExtractRequest> childRequests, String sourceNodeId,
            List<OutgoingBatch> batches, long maxBatchSize, ProcessInfo processInfo, boolean isRestarted, boolean isSingleLocalTarget) {
        this.engine = engine;
        this.stagingManager = engine.getStagingManager();
        this.request = request;
        this.sourceNodeId = sourceNodeId;
        this.maxBatchSize = maxBatchSize;
        this.batches = new ArrayList<OutgoingBatch>(batches);
        this.finishedBatches = new ArrayList<OutgoingBatch>(batches.size());
        this.finishedStats = new HashMap<Batch, Statistics>();
        this.processInfo = processInfo;
        this.startTime = this.ts = System.currentTimeMillis();
        this.childRequests = childRequests;
        this.memoryThresholdInBytes = engine.getParameterService().getLong(ParameterConstants.STREAM_TO_FILE_THRESHOLD);
        this.childBatches = new HashMap<Long, OutgoingBatch>();
        this.isRestarted = isRestarted;
        this.isSingleLocalTarget = isSingleLocalTarget;
        this.synchronizeJobs = engine.getParameterService().is(ParameterConstants.SYNCHRONIZE_ALL_JOBS);
    }

    @Override
    public void open(DataContext context) {
        this.context = context;
        context.put(CONTEXT_IS_SINGLE_LOCAL_TARGET, isSingleLocalTarget);
        nextBatch();
        currentDataWriter = buildWriter();
        currentDataWriter.open(context);
    }

    protected IDataWriter buildWriter() {
        return new StagingDataWriter(memoryThresholdInBytes, false, sourceNodeId, Constants.STAGING_CATEGORY_OUTGOING, stagingManager,
                false, false, (IProtocolDataWriterListener[]) null);
    }

    @Override
    public void close() {
        while (!inError && batches.size() > 0) {
            startNewBatch();
            if (table != null) {
                end(table);
            }
            end(batch, false);
            log.debug("Batch {} is empty", new Object[] { batch.getNodeBatchId() });
            Statistics stats = closeCurrentDataWriter();
            if (!synchronizeJobs) {
                checkSend(stats);
            }
        }
        closeCurrentDataWriter();
        if (synchronizeJobs) {
            for (OutgoingBatch outgoingBatch : finishedBatches) {
                this.outgoingBatch = outgoingBatch;
                batch = new Batch(BatchType.EXTRACT, outgoingBatch.getBatchId(), outgoingBatch.getChannelId(),
                        engine.getSymmetricDialect().getBinaryEncoding(), sourceNodeId, outgoingBatch.getNodeId(), false);
                checkSend(finishedStats.get(batch));
            }
        }
    }

    private Statistics closeCurrentDataWriter() {
        Statistics stats = null;
        if (currentDataWriter != null) {
            stats = currentDataWriter.getStatistics().get(batch);
            finishedBatches.add(outgoingBatch);
            finishedStats.put(batch, stats);
            outgoingBatch.setByteCount(stats.get(DataWriterStatisticConstants.BYTECOUNT));
            outgoingBatch.setExtractMillis(System.currentTimeMillis() - batch.getStartTime().getTime());
            currentDataWriter.close();
            currentDataWriter = null;
            if (inError) {
                IStagedResource resource = getStagedResource(outgoingBatch);
                if (resource != null) {
                    resource.delete();
                }
            } else if (!synchronizeJobs) {
                checkSend(stats);
            }
        }
        return stats;
    }

    @Override
    public Map<Batch, Statistics> getStatistics() {
        if (currentDataWriter != null) {
            return currentDataWriter.getStatistics();
        } else {
            return Collections.emptyMap();
        }
    }

    public void start(Batch batch) {
        this.batch = batch;
        if (batch != null) {
            processInfo.setCurrentBatchId(batch.getBatchId());
            processInfo.setCurrentChannelId(batch.getChannelId());
            processInfo.incrementBatchCount();
            processInfo.setCurrentDataCount(0);
        }
        currentDataWriter.start(batch);
    }

    public boolean start(Table table) {
        this.table = table;
        if (table != null) {
            processInfo.setCurrentTableName(table.getFullyQualifiedName());
        }
        currentDataWriter.start(table);
        return true;
    }

    @Override
    public void write(CsvData data) {
        outgoingBatch.incrementDataRowCount();
        outgoingBatch.incrementDataInsertRowCount();
        currentDataWriter.write(data);
        if (outgoingBatch.getDataRowCount() >= maxBatchSize && batches.size() > 0) {
            currentDataWriter.end(table);
            currentDataWriter.end(batch, false);
            closeCurrentDataWriter();
            startNewBatch();
        }
        if (System.currentTimeMillis() - ts > 60000) {
            long currentRowCount = rowCount + currentDataWriter.getStatistics().get(batch).get(DataWriterStatisticConstants.ROWCOUNT);
            long currentByteCount = byteCount + currentDataWriter.getStatistics().get(batch).get(DataWriterStatisticConstants.BYTECOUNT);
            log.info(
                    "Extract request {} for table {} extracting for {} seconds, {} batches, {} rows, and {} bytes.  Current batch is {} in range {}-{}.",
                    request.getRequestId(), request.getTableName(), (System.currentTimeMillis() - startTime) / 1000, finishedBatches.size() + 1,
                    currentRowCount, currentByteCount, batch.getBatchId(), request.getStartBatchId(), request.getEndBatchId());
            ts = System.currentTimeMillis();
        }
    }

    public void checkSend(Statistics stats) {
        IStagedResource resource = getStagedResource(outgoingBatch);
        if (resource != null) {
            resource.setState(State.DONE);
        }
        OutgoingBatch batchFromDatabase = engine.getOutgoingBatchService().findOutgoingBatch(outgoingBatch.getBatchId(),
                outgoingBatch.getNodeId());
        if (!batchFromDatabase.getStatus().equals(Status.OK) && !batchFromDatabase.getStatus().equals(Status.IG)) {
            outgoingBatch.setStatus(Status.NE);
            if (outgoingBatch.getDataRowCount() == 0) {
                outgoingBatch.setDataRowCount(batchFromDatabase.getDataRowCount());
                outgoingBatch.setDataInsertRowCount(batchFromDatabase.getDataInsertRowCount());
            }
            outgoingBatch.setExtractRowCount(outgoingBatch.getDataRowCount());
            outgoingBatch.setExtractInsertRowCount(outgoingBatch.getDataInsertRowCount());
            checkSendChildRequests(batchFromDatabase, resource, stats);
            engine.getOutgoingBatchService().updateOutgoingBatch(outgoingBatch);
        } else {
            // The user canceled a batch before it tried to load, so they probably canceled all batches.
            log.info("User cancelled batches, so cancelling extract request");
            throw new CancellationException();
        }
    }

    protected void checkSendChildRequests(OutgoingBatch parentBatch, IStagedResource parentResource, Statistics stats) {
        if (childRequests != null) {
            long batchIndex = outgoingBatch.getBatchId() - request.getStartBatchId();
            for (ExtractRequest childRequest : childRequests) {
                long childBatchId = childRequest.getStartBatchId() + batchIndex;
                Date startExtractTime = new Date();
                if (parentResource != null) {
                    IStagedResource childResource = stagingManager.create(Constants.STAGING_CATEGORY_OUTGOING,
                            Batch.getStagedLocation(false, childRequest.getNodeId(), childBatchId), childBatchId);
                    log.debug("About to copy batch {} to batch {}-{}", outgoingBatch.getNodeBatchId(), childRequest.getNodeId(), childBatchId);
                    BufferedReader reader = parentResource.getReader();
                    BufferedWriter writer = childResource.getWriter(memoryThresholdInBytes);
                    try {
                        StringBuffer sb = new StringBuffer();
                        int i;
                        while ((i = reader.read()) != -1) {
                            char c = (char) i;
                            sb.append(c);
                            if (c == '\n') {
                                String s = replaceBatchId(sb.toString(), outgoingBatch.getBatchId(), childBatchId);
                                writer.write(s);
                                sb.delete(0, sb.length());
                            }
                        }
                        if (sb.length() > 0) {
                            String s = replaceBatchId(sb.toString(), outgoingBatch.getBatchId(), childBatchId);
                            writer.write(s);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to copy batch " + outgoingBatch.getNodeBatchId() + " to batch " +
                                childRequest.getNodeId() + "-" + childBatchId + ": " + e.getMessage());
                    } finally {
                        childResource.close();
                        parentResource.close();
                    }
                    childResource.setState(State.DONE);
                }
                OutgoingBatch childBatch = engine.getOutgoingBatchService().findOutgoingBatch(childBatchId, childRequest.getNodeId());
                childBatch.setExtractStartTime(startExtractTime);
                childBatch.setExtractMillis(System.currentTimeMillis() - startExtractTime.getTime());
                if (stats != null) {
                    childBatch.setDataRowCount(stats.get(DataWriterStatisticConstants.ROWCOUNT));
                    childBatch.setDataInsertRowCount(stats.get(DataWriterStatisticConstants.INSERTCOUNT));
                    childBatch.setByteCount(stats.get(DataWriterStatisticConstants.BYTECOUNT));
                }
                if (!childBatch.getStatus().equals(Status.OK) && !childBatch.getStatus().equals(Status.IG)) {
                    childBatch.setStatus(Status.NE);
                    engine.getOutgoingBatchService().updateOutgoingBatch(childBatch);
                }
                childBatches.put(childBatch.getBatchId(), childBatch);
            }
        }
    }

    private static String replaceBatchId(String s, long originalBatchId, long newBatchId) {
        if (s.startsWith(CsvConstants.BATCH) || s.startsWith(CsvConstants.COMMIT)) {
            s = s.replace(Long.toString(originalBatchId), Long.toString(newBatchId));
        }
        return s;
    }

    @Override
    public void end(Table table) {
        if (currentDataWriter != null) {
            currentDataWriter.end(table);
            Statistics stats = currentDataWriter.getStatistics().get(batch);
            outgoingBatch.setByteCount(stats.get(DataWriterStatisticConstants.BYTECOUNT));
            outgoingBatch.setExtractMillis(System.currentTimeMillis() - batch.getStartTime().getTime());
        }
    }

    @Override
    public void end(Batch batch, boolean inError) {
        this.inError = inError;
        if (currentDataWriter != null) {
            // Use last batch we worked on instead of batch passed in, which is actually first batch
            currentDataWriter.end(this.batch, inError);
            closeCurrentDataWriter();
        }
    }

    protected void nextBatch() {
        if (outgoingBatch != null) {
            rowCount += outgoingBatch.getDataRowCount();
            byteCount += outgoingBatch.getByteCount();
            engine.getStatisticManager().incrementDataBytesExtracted(outgoingBatch.getChannelId(), outgoingBatch.getByteCount());
            engine.getStatisticManager().incrementDataExtracted(outgoingBatch.getChannelId(), outgoingBatch.getDataRowCount());
            engine.getStatisticManager().incrementTableRows(outgoingBatch.getTableLoadedCount(), false);
        }
        outgoingBatch = batches.remove(0);
        outgoingBatch.setDataRowCount(0);
        outgoingBatch.setDataInsertRowCount(0);
        outgoingBatch.setExtractStartTime(new Date());
        if (finishedBatches.size() > 0) {
            outgoingBatch.setExtractCount(outgoingBatch.getExtractCount() + 1);
        }
        /*
         * Update the last update time so the batch isn't purged prematurely
         */
        if (engine.getParameterService().is(ParameterConstants.STREAM_TO_FILE_PURGE_ON_TTL_ENABLED, false)) {
            for (OutgoingBatch batch : finishedBatches) {
                IStagedResource resource = getStagedResource(batch);
                if (resource != null) {
                    resource.refreshLastUpdateTime();
                }
            }
        }
    }

    protected void startNewBatch() {
        nextBatch();
        currentDataWriter = buildWriter();
        batch = new Batch(BatchType.EXTRACT, outgoingBatch.getBatchId(), outgoingBatch.getChannelId(),
                engine.getSymmetricDialect().getBinaryEncoding(), sourceNodeId, outgoingBatch.getNodeId(), false);
        currentDataWriter.open(context);
        currentDataWriter.start(batch);
        processInfo.incrementBatchCount();
        if (table != null) {
            currentDataWriter.start(table);
        }
    }

    protected IStagedResource getStagedResource(OutgoingBatch currentBatch) {
        return stagingManager.find(Constants.STAGING_CATEGORY_OUTGOING, currentBatch.getStagedLocation(), currentBatch.getBatchId());
    }
}