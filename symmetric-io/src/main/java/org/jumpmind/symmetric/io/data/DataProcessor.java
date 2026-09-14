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
package org.jumpmind.symmetric.io.data;

import java.util.concurrent.CancellationException;

import org.jumpmind.db.model.Table;
import org.jumpmind.exception.InvalidRetryException;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.writer.IgnoreBatchException;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataProcessor {
    private static final String STAT_WRITE_DATA = "statWriteData";
    private static final String STAT_READ_DATA = "statReadData";
    private static final Logger log = LoggerFactory.getLogger(DataProcessor.class);
    protected IDataReader dataReader;
    protected IDataWriter defaultDataWriter;
    protected IDataProcessorListener listener;
    protected Table currentTable;
    protected CsvData currentData;
    protected Batch currentBatch;
    protected String name;

    public DataProcessor() {
    }

    public DataProcessor(IDataReader dataReader, IDataWriter defaultDataWriter, String name) {
        this(dataReader, defaultDataWriter, null, name);
    }

    public DataProcessor(IDataReader dataReader, IDataWriter defaultDataWriter,
            IDataProcessorListener listener, String name) {
        this.dataReader = dataReader;
        this.defaultDataWriter = defaultDataWriter;
        this.listener = listener;
        this.name = name;
    }

    /**
     * This method may be overridden in order to choose different {@link IDataWriter} based on the batch that is being written.
     * 
     * @param batch
     *            The batch that is about to be written
     * @return The data writer to use for the writing of the batch
     */
    protected IDataWriter chooseDataWriter(Batch batch) {
        return this.defaultDataWriter;
    }

    public void process() {
        process(new DataContext());
    }

    public void process(DataContext context) {
        try {
            context.setReader(dataReader);
            dataReader.open(context);
            do {
                currentBatch = dataReader.nextBatch();
                if (currentBatch != null) {
                    context.setBatch(currentBatch);
                    boolean endBatchCalled = false;
                    IDataWriter dataWriter = null;
                    try {
                        boolean processBatch = listener == null ? true
                                : listener
                                        .beforeBatchStarted(context);
                        if (processBatch) {
                            dataWriter = chooseDataWriter(currentBatch);
                            processBatch &= dataWriter != null;
                        }
                        if (processBatch) {
                            context.setWriter(dataWriter);
                            dataWriter.open(context);
                            dataWriter.start(currentBatch);
                            if (listener != null) {
                                listener.afterBatchStarted(context);
                            }
                        }
                        if (currentBatch.isInvalidRetry()) {
                            throw new InvalidRetryException();
                        }
                        // pull and process any data events that are not wrapped
                        // in a table
                        forEachDataInTable(context, processBatch, true, currentBatch);
                        // pull and process all data events wrapped in tables
                        forEachTableInBatch(context, processBatch, currentBatch);
                        if (currentBatch != null && !currentBatch.isComplete()) {
                            String msg = "The batch %s was not complete";
                            if (currentBatch.getBatchType() == BatchType.EXTRACT) {
                                msg += ".  Note that this is the error you receive on Oracle when the total size of row_data in sym_data is greater than 4k.  You can work around this by changing the contains_big_lobs in sym_channel to 1";
                            }
                            throw new ProtocolException(msg, currentBatch.getNodeBatchId());
                        }
                        if (processBatch) {
                            if (listener != null) {
                                listener.beforeBatchEnd(context);
                            }
                            dataWriter.end(currentBatch, false);
                            endBatchCalled = true;
                            if (listener != null) {
                                listener.batchSuccessful(context);
                            }
                        }
                    } catch (Throwable ex) {
                        try {
                            context.setLastError(ex);
                            if (dataWriter != null && !endBatchCalled) {
                                dataWriter.end(currentBatch, true);
                            }
                        } finally {
                            if (listener != null) {
                                listener.batchInError(context, ex);
                            }
                        }
                        rethrow(ex);
                    } finally {
                        close(dataWriter);
                    }
                }
            } while (currentBatch != null);
        } finally {
            close(this.dataReader);
        }
    }

    protected int forEachTableInBatch(DataContext context, boolean processBatch, Batch batch) {
        int dataRow = 0;
        do {
            currentTable = (Table) dataReader.nextRelation();
            context.setRelation(currentTable);
            if (currentTable != null) {
                boolean processTable = false;
                try {
                    try {
                        if (processBatch) {
                            processTable = context.getWriter().start(currentTable);
                        }
                        dataRow += forEachDataInTable(context, processBatch, processTable, batch);
                    } catch (IgnoreBatchException ex) {
                        processBatch = false;
                    }
                } finally {
                    if (processTable) {
                        context.getWriter().end(currentTable);
                    }
                }
            }
        } while (currentTable != null);
        return dataRow;
    }

    protected int forEachDataInTable(DataContext context, boolean processBatch, boolean processTable, Batch batch) {
        int dataRow = 0;
        IgnoreBatchException ignore = null;
        long startTime = System.currentTimeMillis();
        long ts = System.currentTimeMillis();
        do {
            batch.startTimer(STAT_READ_DATA);
            currentData = dataReader.nextData();
            context.setData(currentData);
            batch.incrementDataReadMillis(batch.endTimer(STAT_READ_DATA));
            if (currentData != null) {
                dataRow++;
                if (processTable || !currentData.requiresTable()) {
                    try {
                        batch.startTimer(STAT_WRITE_DATA);
                        batch.incrementLineCount();
                        if (context.getWriter() == null) {
                            context.setWriter(chooseDataWriter(batch));
                        }
                        if (processBatch) {
                            context.getWriter().write(currentData);
                        }
                        batch.incrementDataWriteMillis(batch.endTimer(STAT_WRITE_DATA));
                    } catch (IgnoreBatchException ex) {
                        ignore = ex;
                        processTable = false;
                    }
                }
                if (listener != null) {
                    listener.dataRowProcessed();
                }
            }
            if (System.currentTimeMillis() - ts > 60000 && context.getWriter() != null) {
                Statistics stats = context.getWriter().getStatistics().get(batch);
                if (listener != null) {
                    listener.batchProgressUpdate(context);
                }
                if (stats != null) {
                    log.info(
                            "Batch '{}', for node '{}', for process '{}' has been processing for {} seconds.  The following stats have been gathered: {}",
                            new Object[] { batch.getBatchId(), batch.getTargetNodeId(), name,
                                    (System.currentTimeMillis() - startTime) / 1000,
                                    stats.toString() });
                }
                ts = System.currentTimeMillis();
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("This thread was interrupted");
            }
        } while (currentData != null);
        if (ignore != null) {
            throw ignore;
        }
        return dataRow;
    }

    protected void rethrow(Throwable ex) {
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        } else {
            throw new RuntimeException(ex);
        }
    }

    protected void close(IDataResource dataResource) {
        try {
            if (dataResource != null) {
                dataResource.close();
            }
        } catch (Exception ex) {
            log.error("Failed to close dataResource:" + dataResource, ex);
        }
    }

    public void setListener(IDataProcessorListener listener) {
        this.listener = listener;
    }

    public void setDataReader(IDataReader dataReader) {
        this.dataReader = dataReader;
    }

    public void setDefaultDataWriter(IDataWriter dataWriter) {
        this.defaultDataWriter = dataWriter;
    }
}
