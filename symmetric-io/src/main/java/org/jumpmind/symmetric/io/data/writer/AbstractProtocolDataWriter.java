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
package org.jumpmind.symmetric.io.data.writer;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractProtocolDataWriter implements IDataWriter {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected DataContext context;
    protected Batch batch;
    protected Table table;
    protected Map<String, String> processedTables = new HashMap<String, String>();
    protected String delimiter = ",";
    protected boolean flushNodeId = true;
    protected boolean flushBatchId = true;
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();
    protected List<IProtocolDataWriterListener> listeners;
    protected String sourceNodeId;
    protected boolean noBinaryOldData = false;
    protected boolean backwardsCompatible = false;
    protected boolean sendCaptureTime = false;
    protected boolean sendRowCaptureTime = false;
    protected long baseTime;
    protected long lastTime;

    public AbstractProtocolDataWriter(String sourceNodeId,
            List<IProtocolDataWriterListener> listeners, boolean backwardsCompatible, boolean sendCaptureTime, boolean sendRowCaptureTime) {
        this.listeners = listeners;
        this.sourceNodeId = sourceNodeId;
        this.backwardsCompatible = backwardsCompatible;
        this.sendCaptureTime = sendCaptureTime;
        this.sendRowCaptureTime = sendRowCaptureTime;
    }

    @Override
    public void open(DataContext context) {
        this.context = context;
    }

    @Override
    public void close() {
    }

    @Override
    public void start(Batch batch) {
        this.statistics.put(batch, new Statistics());
        this.batch = batch;
        this.baseTime = 0;
        this.lastTime = 0;
        if (listeners != null) {
            for (IProtocolDataWriterListener listener : listeners) {
                listener.start(context, batch);
            }
        }
        if (StringUtils.isBlank(sourceNodeId)) {
            sourceNodeId = batch.getSourceNodeId();
        }
        if (flushNodeId) {
            if (StringUtils.isNotBlank(sourceNodeId)) {
                println(CsvConstants.NODEID, sourceNodeId);
            }
            flushNodeId = false;
        }
        if (!backwardsCompatible && StringUtils.isNotBlank(batch.getChannelId())) {
            println(CsvConstants.CHANNEL, batch.getChannelId());
        }
        flushBatchId = true;
    }

    private void printBinary() {
        BinaryEncoding binaryEncoding = batch.getBinaryEncoding();
        if (binaryEncoding != null) {
            println(CsvConstants.BINARY, binaryEncoding.name());
        }
    }

    private void printBatchIfNeeded() {
        if (flushBatchId) {
            if (!backwardsCompatible) {
                printBinary();
            }
            println(CsvConstants.BATCH, Long.toString(batch.getBatchId()));
            if (backwardsCompatible) {
                printBinary();
            }
            flushBatchId = false;
        }
    }

    @Override
    public boolean start(Table table) {
        printBatchIfNeeded();
        if (!batch.isIgnored()) {
            this.table = table;
            if (!backwardsCompatible) {
                String catalogName = table.getCatalog();
                println(CsvConstants.CATALOG, StringUtils.isNotBlank(catalogName) ? catalogName
                        : "");
                String schemaName = table.getSchema();
                println(CsvConstants.SCHEMA, StringUtils.isNotBlank(schemaName) ? schemaName : "");
            }
            String tableKey = table.getKey();
            String fullyQualifiedTableName = table.getFullyQualifiedName();
            String previousTableKey = processedTables.get(fullyQualifiedTableName);
            println(CsvConstants.TABLE, CsvUtils.escapeAndQuoteCsvData(table.getName()));
            if (!tableKey.equals(previousTableKey)) {
                println(CsvConstants.KEYS, table.getPrimaryKeyColumns());
                println(CsvConstants.COLUMNS, table.getColumns());
                this.processedTables.put(fullyQualifiedTableName, tableKey);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void write(CsvData data) {
        if (!batch.isIgnored()) {
            if (noBinaryOldData != data.isNoBinaryOldData() && !backwardsCompatible) {
                noBinaryOldData = data.isNoBinaryOldData();
                println(CsvConstants.NO_BINARY_OLD_DATA, Boolean.toString(noBinaryOldData));
            }
            statistics.get(batch).increment(DataWriterStatisticConstants.ROWCOUNT);
            statistics.get(batch).increment(DataWriterStatisticConstants.LINENUMBER);
            switch (data.getDataEventType()) {
                case INSERT:
                    printTime(data);
                    println(CsvConstants.INSERT, data.getCsvData(CsvData.ROW_DATA));
                    statistics.get(batch).increment(DataWriterStatisticConstants.INSERTCOUNT);
                    statistics.get(batch).incrementTableStats(this.table.getName(), DataEventType.INSERT.getCode(), 1);
                    break;
                case UPDATE:
                    printTime(data);
                    if (!backwardsCompatible) {
                        String oldData = data.getCsvData(CsvData.OLD_DATA);
                        if (StringUtils.isNotBlank(oldData)) {
                            println(CsvConstants.OLD, oldData);
                        }
                    }
                    println(CsvConstants.UPDATE, data.getCsvData(CsvData.ROW_DATA),
                            data.getCsvData(CsvData.PK_DATA));
                    statistics.get(batch).increment(DataWriterStatisticConstants.UPDATECOUNT);
                    statistics.get(batch).incrementTableStats(this.table.getName(), DataEventType.UPDATE.getCode(), 1);
                    break;
                case DELETE:
                    printTime(data);
                    if (!backwardsCompatible) {
                        String oldData = data.getCsvData(CsvData.OLD_DATA);
                        if (StringUtils.isNotBlank(oldData)) {
                            println(CsvConstants.OLD, oldData);
                        }
                    }
                    println(CsvConstants.DELETE, data.getCsvData(CsvData.PK_DATA));
                    statistics.get(batch).increment(DataWriterStatisticConstants.DELETECOUNT);
                    statistics.get(batch).incrementTableStats(this.table.getName(), DataEventType.DELETE.getCode(), 1);
                    break;
                case CREATE:
                    println(CsvConstants.CREATE, data.getCsvData(CsvData.ROW_DATA));
                    break;
                case BSH:
                    println(CsvConstants.BSH, data.getCsvData(CsvData.ROW_DATA));
                    break;
                case SQL:
                    println(CsvConstants.SQL, data.getCsvData(CsvData.ROW_DATA));
                    break;
                case RELOAD:
                default:
                    break;
            }
        }
    }

    protected void printTime(CsvData data) {
        Date createTime = (Date) data.getAttribute(CsvData.ATTRIBUTE_CREATE_TIME);
        if (sendCaptureTime && baseTime == 0 && createTime != null) {
            baseTime = createTime.getTime();
            println(CsvConstants.BASETIME, String.valueOf(baseTime));
        }
        if (sendCaptureTime && sendRowCaptureTime && createTime != null) {
            long thisTime = createTime.getTime() - baseTime;
            if (thisTime != lastTime) {
                println(CsvConstants.TIME, String.valueOf(thisTime));
                lastTime = thisTime;
            }
        }
    }

    @Override
    public void end(Table table) {
    }

    @Override
    final public void end(Batch batch, boolean inError) {
        printBatchIfNeeded();
        if (batch.isIgnored() && !backwardsCompatible) {
            println(CsvConstants.IGNORE);
        }
        if (!inError) {
            println(CsvConstants.COMMIT, Long.toString(batch.getBatchId()));
            endBatch(batch);
        }
        if (listeners != null && !inError) {
            for (IProtocolDataWriterListener listener : listeners) {
                notifyEndBatch(batch, listener);
            }
        }
    }

    protected void endBatch(Batch batch) {
        printBatchIfNeeded();
    }

    abstract protected void notifyEndBatch(Batch batch, IProtocolDataWriterListener listener);

    protected int println(String key, List<Column> columns) {
        return println(key, columns.toArray(new Column[columns.size()]));
    }

    protected int println(String key, Column[] columns) {
        StringBuilder buffer = new StringBuilder(key);
        for (int i = 0; i < columns.length; i++) {
            buffer.append(delimiter);
            String name = columns[i].getName();
            buffer.append(Table.escapeColumnNameForCsv(name));
        }
        println(buffer.toString());
        return buffer.length();
    }

    abstract protected void print(Batch batch, String data);

    protected long println(String... data) {
        long byteCount = 0;
        for (int i = 0; i < data.length; i++) {
            if (i != 0) {
                print(batch, delimiter);
                byteCount += delimiter.length();
            }
            print(batch, data[i]);
            if (data[i] != null) {
                byteCount += data[i].length();
            }
        }
        print(batch, "\n");
        byteCount += "\n".length();
        statistics.get(batch).increment(DataWriterStatisticConstants.BYTECOUNT, byteCount);
        return byteCount;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getDelimiter() {
        return delimiter;
    }

    @Override
    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }
}
