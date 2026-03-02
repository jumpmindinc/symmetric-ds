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
package org.jumpmind.symmetric.route;

import static org.jumpmind.symmetric.common.Constants.LOG_PROCESS_SUMMARY_THRESHOLD;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * This class is responsible for reading data for the purpose of routing. It reads ahead and tries to keep a blocking queue populated for another thread to
 * process.
 */
public class DataGapRouteReader implements IDataToRouteReader {
    private static final Logger log = LoggerFactory.getLogger(DataGapRouteReader.class);
    protected List<DataGap> dataGaps;
    protected DataGap currentGap;
    protected BlockingQueue<Data> dataQueue;
    protected ChannelRouterContext context;
    protected ISymmetricEngine engine;
    protected volatile boolean reading = true;
    protected int peekAheadCount = 1000;
    protected int takeTimeout;
    protected ProcessInfo processInfo;
    protected double percentOfHeapToUse = .5;
    protected long peekAheadSizeInBytes = 0;
    protected boolean finishTransactionMode = false;
    protected boolean isEachGapQueried;
    protected String lastTransactionId = null;
    protected long lastStatsPrintOutBaselineInMs = System.currentTimeMillis();

    public DataGapRouteReader(ChannelRouterContext context, ISymmetricEngine engine) {
        this.engine = engine;
        IParameterService parameterService = engine.getParameterService();
        this.peekAheadCount = parameterService.getInt(ParameterConstants.ROUTING_PEEK_AHEAD_WINDOW);
        this.percentOfHeapToUse = (double) parameterService.getInt(ParameterConstants.ROUTING_PEEK_AHEAD_MEMORY_THRESHOLD) / (double) 100;
        this.takeTimeout = engine.getParameterService().getInt(
                ParameterConstants.ROUTING_WAIT_FOR_DATA_TIMEOUT_SECONDS, 330);
        if (parameterService.is(ParameterConstants.SYNCHRONIZE_ALL_JOBS)) {
            /* there will not be a separate thread to read a blocked queue so make sure the queue is big enough that it can be filled */
            this.dataQueue = new LinkedBlockingQueue<Data>();
        } else {
            this.dataQueue = new LinkedBlockingQueue<Data>(peekAheadCount);
        }
        this.context = context;
    }

    public void run() {
        try {
            MDC.put("engineName", engine.getParameterService().getEngineName());
            execute();
        } catch (Throwable ex) {
            log.error("", ex);
        }
    }

    protected IDataGapRouteCursor createCursor(ChannelRouterContext context, ISymmetricEngine engine) {
        if (engine.getParameterService().is(ParameterConstants.ROUTING_DATA_READER_USE_MULTIPLE_QUERIES)) {
            return new DataGapRouteMultiCursor(context, engine);
        }
        return new DataGapRouteCursor(context, engine);
    }

    protected void initializeGaps(IDataGapRouteCursor cursor) {
        isEachGapQueried = cursor.isEachGapQueried();
        if (!isEachGapQueried) {
            dataGaps = new ArrayList<DataGap>(context.getDataGaps());
            currentGap = dataGaps.remove(0);
        }
    }

    protected void execute() {
        ISymmetricDialect symmetricDialect = engine.getSymmetricDialect();
        IDataGapRouteCursor cursor = null;
        processInfo = engine.getStatisticManager().newProcessInfo(
                new ProcessInfoKey(engine.getNodeService().findIdentityNodeId(), context.getChannel().getChannelId(), null,
                        ProcessType.ROUTER_READER));
        processInfo.setCurrentChannelId(context.getChannel().getChannelId());
        try {
            boolean transactional = !context.getChannel().getBatchAlgorithm()
                    .equals(NonTransactionalBatchAlgorithm.NAME)
                    || !symmetricDialect.supportsTransactionId();
            processInfo.setStatus(ProcessStatus.QUERYING);
            cursor = createCursor(context, engine);
            initializeGaps(cursor);
            processInfo.setStatus(ProcessStatus.EXTRACTING);
            if (transactional) {
                executeTransactional(cursor);
            } else {
                executeNonTransactional(cursor);
            }
            processInfo.setStatus(ProcessStatus.OK);
        } catch (Throwable ex) {
            processInfo.setStatus(ProcessStatus.ERROR);
            if (!context.isOverrideContainsBigLob() && engine.getSqlTemplate().isDataTruncationViolation(ex)) {
                log.warn(ex.getMessage());
                log.info("Re-attempting routing with contains_big_lobs temporarily enabled for channel {}",
                        context.getChannel().getChannelId());
                context.setOverrideContainsBigLob(true);
                execute();
            } else {
                log.error("Failed to read data for routing", ex);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            copyToQueue(new EOD());
            reading = false;
        }
    }

    protected void executeTransactional(ISqlReadCursor<Data> cursor) throws Exception {
        long maxPeekAheadSizeInBytes = (long) (Runtime.getRuntime().maxMemory() * percentOfHeapToUse);
        int lastPeekAheadIndex = 0;
        int dataCount = 0;
        long maxDataToRoute = context.getChannel().getMaxDataToRoute();
        List<Data> peekAheadQueue = new ArrayList<Data>(peekAheadCount);
        boolean moreData = true;
        while (dataCount < maxDataToRoute || (lastTransactionId != null)) {
            if (moreData && (lastTransactionId != null || peekAheadQueue.size() == 0)) {
                moreData = fillPeekAheadQueue(peekAheadQueue, peekAheadCount, cursor);
            }
            int dataWithSameTransactionIdCount = 0;
            while (peekAheadQueue.size() > 0 && lastTransactionId == null &&
                    dataCount < maxDataToRoute) {
                Data data = peekAheadQueue.remove(0);
                copyToQueue(data);
                dataCount++;
                processInfo.incrementCurrentDataCount();
                processInfo.setCurrentTableName(data.getTableName());
                lastTransactionId = data.getTransactionId();
                dataWithSameTransactionIdCount++;
            }
            if (lastTransactionId != null && peekAheadQueue.size() > 0) {
                Iterator<Data> datas = peekAheadQueue.iterator();
                int index = 0;
                while (datas.hasNext()) {
                    Data data = datas.next();
                    if (lastTransactionId.equals(data.getTransactionId())) {
                        dataWithSameTransactionIdCount++;
                        datas.remove();
                        copyToQueue(data);
                        dataCount++;
                        processInfo.incrementCurrentDataCount();
                        processInfo.setCurrentTableName(data.getTableName());
                        lastPeekAheadIndex = index;
                    } else {
                        index++;
                    }
                }
                if (dataWithSameTransactionIdCount == 0 || peekAheadQueue.size() - lastPeekAheadIndex > peekAheadCount) {
                    lastTransactionId = null;
                    lastPeekAheadIndex = 0;
                }
            }
            if (!moreData && peekAheadQueue.size() == 0) {
                // we've reached the end of the result set
                break;
            } else if (peekAheadSizeInBytes >= maxPeekAheadSizeInBytes) {
                log.info("The peek ahead queue has reached its max size of {} bytes.  Finishing reading the current transaction", peekAheadSizeInBytes);
                finishTransactionMode = true;
                peekAheadQueue.clear();
            }
        }
    }

    protected void executeNonTransactional(ISqlReadCursor<Data> cursor) throws Exception {
        long maxDataToRoute = context.getChannel().getMaxDataToRoute();
        List<Data> peekAheadQueue = new ArrayList<Data>(peekAheadCount);
        int dataCount = 0;
        while (dataCount < maxDataToRoute) {
            fillPeekAheadQueue(peekAheadQueue, peekAheadCount, cursor);
            if (peekAheadQueue.size() > 0) {
                while (peekAheadQueue.size() > 0 && dataCount < maxDataToRoute) {
                    Data data = peekAheadQueue.remove(0);
                    copyToQueue(data);
                    dataCount++;
                    processInfo.incrementCurrentDataCount();
                    processInfo.setCurrentTableName(data.getTableName());
                }
            } else {
                break;
            }
        }
    }

    protected boolean process(Data data) {
        long dataId = data.getDataId();
        boolean okToProcess = false;
        if (!finishTransactionMode
                || (lastTransactionId != null && finishTransactionMode && lastTransactionId
                        .equals(data.getTransactionId()))) {
            if (isEachGapQueried) {
                okToProcess = true;
            } else {
                while (!okToProcess && currentGap != null && dataId >= currentGap.getStartId()) {
                    if (dataId <= currentGap.getEndId()) {
                        okToProcess = true;
                    } else {
                        // past current gap. move to next gap
                        if (dataGaps.size() > 0) {
                            currentGap = dataGaps.remove(0);
                        } else {
                            currentGap = null;
                        }
                    }
                }
            }
        }
        return okToProcess;
    }

    protected boolean isInDataGap(long dataId) {
        // binary search algorithm
        int start = 0;
        int end = dataGaps.size() - 1;
        while (start <= end) {
            int mid = (start + end) / 2;
            DataGap midGap = dataGaps.get(mid);
            if (dataId >= midGap.getStartId() && dataId <= midGap.getEndId()) {
                return true;
            }
            if (dataId < midGap.getStartId()) {
                end = mid - 1;
            } else {
                start = mid + 1;
            }
        }
        return false;
    }

    public Data take() throws InterruptedException {
        Data data = null;
        do {
            data = dataQueue.poll(takeTimeout, TimeUnit.SECONDS);
            if (data == null && !reading) {
                throw new SymmetricException("The read of the data to route queue has timed out");
            } else if (data instanceof EOD) {
                data = null;
                break;
            }
        } while (data == null && reading);
        return data;
    }

    protected boolean fillPeekAheadQueue(List<Data> peekAheadQueue, int peekAheadCount,
            ISqlReadCursor<Data> cursor) throws SQLException {
        boolean moreData = true;
        int dataCount = 0;
        long ts = System.currentTimeMillis();
        Data data = null;
        boolean isFirstRead = context.getStartDataId() == 0;
        while (reading && dataCount < peekAheadCount) {
            data = cursor.next();
            if (data != null) {
                if (process(data)) {
                    peekAheadQueue.add(data);
                    peekAheadSizeInBytes += data.getSizeInBytes();
                    dataCount++;
                    context.incrementStat(System.currentTimeMillis() - ts,
                            ChannelRouterContext.STAT_READ_DATA_MS);
                } else {
                    context.incrementDataRereadCount();
                    context.incrementStat(System.currentTimeMillis() - ts,
                            ChannelRouterContext.STAT_REREAD_DATA_MS);
                }
                if (isFirstRead) {
                    context.setStartDataId(data.getDataId());
                    isFirstRead = false;
                }
                context.setEndDataId(data.getDataId());
                ts = System.currentTimeMillis();
                long totalTimeInMs = System.currentTimeMillis() - lastStatsPrintOutBaselineInMs;
                if (totalTimeInMs > LOG_PROCESS_SUMMARY_THRESHOLD) {
                    log.info(
                            "Reading channel '{}' for {} seconds, dataCount={}, dataRereadCount={}",
                            context.getChannel().getChannelId(), (System.currentTimeMillis() - context.getCreatedTimeInMs()) / 1000,
                            dataCount + context.getDataReadCount(), context.getDataRereadCount());
                    lastStatsPrintOutBaselineInMs = System.currentTimeMillis();
                }
            } else {
                moreData = false;
                break;
            }
        }
        context.incrementDataReadCount(dataCount);
        context.incrementPeekAheadFillCount(1);
        int size = peekAheadQueue.size();
        if (context.getMaxPeekAheadQueueSize() < size) {
            context.setMaxPeekAheadQueueSize(size);
        }
        return moreData && reading;
    }

    protected void copyToQueue(Data data) {
        long ts = System.currentTimeMillis();
        peekAheadSizeInBytes -= data.getSizeInBytes();
        while (!dataQueue.offer(data) && reading) {
            AppUtils.sleep(50);
        }
        context.incrementStat(System.currentTimeMillis() - ts,
                ChannelRouterContext.STAT_ENQUEUE_DATA_MS);
    }

    public boolean isReading() {
        return reading;
    }

    public void setReading(boolean reading) {
        this.reading = reading;
        if (processInfo != null && processInfo.getStatus() != ProcessStatus.ERROR) {
            processInfo.setStatus(ProcessStatus.OK);
        }
    }

    public BlockingQueue<Data> getDataQueue() {
        return dataQueue;
    }

    static class EOD extends Data {
        private static final long serialVersionUID = 1L;
    }
}
