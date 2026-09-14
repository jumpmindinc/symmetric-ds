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
package org.jumpmind.symmetric.io.data.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.SchemaObject;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.csv.CsvReader;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataReader;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.util.CollectionUtils;
import org.jumpmind.util.Statistics;

public class ProtocolDataReader extends AbstractDataReader implements IDataReader {
    public static final String CTX_LINE_NUMBER = ProtocolDataReader.class.getSimpleName() + ".lineNumber";
    protected IStagedResource stagedResource;
    protected Reader reader;
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();
    protected CsvReader csvReader;
    protected DataContext context;
    protected Object next;
    protected Batch batch;
    protected String channelId;
    protected String tableName;
    protected String sourceNodeId;
    protected String targetNodeId;
    protected BinaryEncoding binaryEncoding;
    protected boolean noBinaryOldData = false;
    protected BatchType batchType;
    protected int lineNumber = 0;
    protected String[] tokens;
    protected boolean streamToFile = true;
    protected long baseTime;
    protected Timestamp createTime;

    public ProtocolDataReader(BatchType batchType, String targetNodeId, StringBuilder input) {
        this(batchType, targetNodeId, new BufferedReader(new StringReader(input.toString())));
    }

    public ProtocolDataReader(BatchType batchType, String targetNodeId, InputStream is) {
        this(batchType, targetNodeId, toReader(is));
    }

    public ProtocolDataReader(BatchType batchType, String targetNodeId, IStagedResource stagedResource) {
        this.stagedResource = stagedResource;
        this.targetNodeId = targetNodeId;
        this.batchType = batchType;
    }

    public ProtocolDataReader(BatchType batchType, String targetNodeId, String input) {
        this(batchType, targetNodeId, new BufferedReader(new StringReader(input)));
    }

    public ProtocolDataReader(BatchType batchType, String targetNodeId, Reader reader) {
        this.reader = reader;
        this.targetNodeId = targetNodeId;
        this.batchType = batchType;
    }

    public ProtocolDataReader(BatchType batchType, String targetNodeId, Reader reader, boolean streamToFile) {
        this.reader = reader;
        this.targetNodeId = targetNodeId;
        this.batchType = batchType;
        this.streamToFile = streamToFile;
    }

    public ProtocolDataReader(BatchType batchType, String targetNodeId, File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader in = new InputStreamReader(fis, StandardCharsets.UTF_8.name());
            this.targetNodeId = targetNodeId;
            this.batchType = batchType;
            this.reader = new BufferedReader(in);
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    public IStagedResource getStagedResource() {
        return stagedResource;
    }

    public void open(DataContext context) {
        if (this.stagedResource != null && this.reader == null) {
            this.reader = this.stagedResource.getReader();
        }
        this.lineNumber = 0;
        this.context = context;
        this.csvReader = CsvUtils.getCsvReader(reader);
        this.next = readNext();
    }

    public Object readNext() {
        try {
            Set<String> keys = null;
            String schemaName = null;
            String catalogName = null;
            String[] parsedOldData = null;
            long bytesRead = 0;
            String[] statsColumns = null;
            String[] statsValues = null;
            Relation relation = null;
            while (tokens != null || csvReader.readRecord()) {
                lineNumber++;
                context.put(CTX_LINE_NUMBER, lineNumber);
                if (tokens == null) {
                    tokens = csvReader.getValues();
                }
                bytesRead += logDebugAndCountBytes(tokens);
                Statistics stats = null;
                if (batch != null) {
                    stats = statistics.get(batch);
                    stats.increment(DataReaderStatistics.READ_BYTE_COUNT, bytesRead);
                    bytesRead = 0;
                }
                if (relation != null && !(tokens[0].equals(CsvConstants.TABLE) || tokens[0].equals(CsvConstants.KEYS)
                        || tokens[0].equals(CsvConstants.COLUMNS))) {
                    return relation;
                }
                if (stats != null && (tokens[0].equals(CsvConstants.INSERT) || tokens[0].equals(CsvConstants.UPDATE)
                        || tokens[0].equals(CsvConstants.DELETE))) {
                    stats.increment(DataReaderStatistics.READ_RECORD_COUNT, 1);
                }
                if (tokens[0].equals(CsvConstants.INSERT)) {
                    CsvData data = new CsvData();
                    data.setNoBinaryOldData(noBinaryOldData);
                    data.setDataEventType(DataEventType.INSERT);
                    data.putParsedData(CsvData.ROW_DATA, CollectionUtils.copyOfRange(tokens, 1, tokens.length));
                    if (createTime != null) {
                        data.putAttribute(CsvData.ATTRIBUTE_CREATE_TIME, createTime);
                    }
                    data.putAttribute(CsvData.ATTRIBUTE_CHANNEL_ID, channelId);
                    data.putAttribute(CsvData.ATTRIBUTE_TABLE_NAME, tableName);
                    data.putAttribute(CsvData.ATTRIBUTE_SOURCE_NODE_ID, sourceNodeId);
                    tokens = null;
                    return data;
                } else if (tokens[0].equals(CsvConstants.OLD)) {
                    parsedOldData = CollectionUtils.copyOfRange(tokens, 1, tokens.length);
                } else if (tokens[0].equals(CsvConstants.UPDATE)) {
                    CsvData data = new CsvData();
                    data.setNoBinaryOldData(noBinaryOldData);
                    data.setDataEventType(DataEventType.UPDATE);
                    int columnCount = context.getLastParsedRelation().getColumnCount();
                    if (tokens.length <= columnCount) {
                        String msg = String.format(
                                "Invalid state while parsing csv data.  "
                                        + "The number of columns (%d) reported for table '%s' doesn't match up with the token count (%d) data: %s",
                                columnCount, context.getLastParsedRelation().getFullyQualifiedName(), tokens.length,
                                ArrayUtils.toString(tokens));
                        throw new ProtocolException(msg);
                    }
                    data.putParsedData(CsvData.ROW_DATA, CollectionUtils.copyOfRange(tokens, 1, columnCount + 1));
                    data.putParsedData(CsvData.PK_DATA, CollectionUtils.copyOfRange(tokens, columnCount + 1, tokens.length));
                    data.putParsedData(CsvData.OLD_DATA, parsedOldData);
                    if (createTime != null) {
                        data.putAttribute(CsvData.ATTRIBUTE_CREATE_TIME, createTime);
                    }
                    data.putAttribute(CsvData.ATTRIBUTE_CHANNEL_ID, channelId);
                    data.putAttribute(CsvData.ATTRIBUTE_TABLE_NAME, tableName);
                    data.putAttribute(CsvData.ATTRIBUTE_SOURCE_NODE_ID, sourceNodeId);
                    tokens = null;
                    return data;
                } else if (tokens[0].equals(CsvConstants.DELETE)) {
                    CsvData data = new CsvData();
                    data.setNoBinaryOldData(noBinaryOldData);
                    data.setDataEventType(DataEventType.DELETE);
                    data.putParsedData(CsvData.PK_DATA, CollectionUtils.copyOfRange(tokens, 1, tokens.length));
                    data.putParsedData(CsvData.OLD_DATA, parsedOldData);
                    if (createTime != null) {
                        data.putAttribute(CsvData.ATTRIBUTE_CREATE_TIME, createTime);
                    }
                    data.putAttribute(CsvData.ATTRIBUTE_CHANNEL_ID, channelId);
                    data.putAttribute(CsvData.ATTRIBUTE_TABLE_NAME, tableName);
                    data.putAttribute(CsvData.ATTRIBUTE_SOURCE_NODE_ID, sourceNodeId);
                    tokens = null;
                    return data;
                } else if (tokens[0].equals(CsvConstants.TIME)) {
                    createTime = new Timestamp(Long.parseLong(tokens[1]) + baseTime);
                } else if (tokens[0].equals(CsvConstants.BATCH) || tokens[0].equals(CsvConstants.RETRY)) {
                    Batch batch = new Batch(batchType, Long.parseLong(tokens[1]), channelId, binaryEncoding, sourceNodeId, targetNodeId,
                            false);
                    stats = stats != null ? stats : new DataReaderStatistics();
                    statistics.put(batch, stats);
                    if (tokens[0].equals(CsvConstants.RETRY) && !streamToFile) {
                        batch.setInvalidRetry(true);
                    }
                    tokens = null;
                    createTime = null;
                    return batch;
                } else if (tokens[0].equals(CsvConstants.NO_BINARY_OLD_DATA)) {
                    if (tokens.length > 1) {
                        noBinaryOldData = Boolean.parseBoolean(tokens[1]);
                    }
                } else if (tokens[0].equals(CsvConstants.NODEID)) {
                    this.sourceNodeId = tokens[1];
                } else if (tokens[0].equals(CsvConstants.BINARY)) {
                    this.binaryEncoding = BinaryEncoding.valueOf(tokens[1]);
                } else if (tokens[0].equals(CsvConstants.CHANNEL)) {
                    this.channelId = tokens[1];
                } else if (tokens[0].equals(CsvConstants.SCHEMA)) {
                    schemaName = tokens.length == 1 || StringUtils.isBlank(tokens[1]) ? null : tokens[1];
                } else if (tokens[0].equals(CsvConstants.CATALOG)) {
                    catalogName = tokens.length == 1 || StringUtils.isBlank(tokens[1]) ? null : tokens[1];
                } else if (tokens[0].equals(CsvConstants.TABLE)) {
                    tableName = tokens[1];
                    relation = context.getParsedRelations().get(SchemaObject.getFullyQualifiedName(catalogName, schemaName, tableName));
                    if (relation != null) {
                        context.setLastParsedRelation(relation);
                    } else {
                        relation = new Table(catalogName, schemaName, tableName);
                        context.setLastParsedRelation(relation);
                    }
                } else if (tokens[0].equals(CsvConstants.KEYS)) {
                    if (keys == null) {
                        keys = HashSet.newHashSet(tokens.length);
                    }
                    for (int i = 1; i < tokens.length; i++) {
                        keys.add(tokens[i]);
                    }
                } else if (tokens[0].equals(CsvConstants.COLUMNS)) {
                    relation.removeAllColumns();
                    for (int i = 1; i < tokens.length; i++) {
                        Column column = new Column(tokens[i], keys != null && keys.contains(tokens[i]));
                        relation.addColumn(column);
                    }
                    context.getParsedRelations().put(relation.getFullyQualifiedName(), relation);
                } else if (tokens[0].equals(CsvConstants.COMMIT)) {
                    if (batch != null) {
                        batch.setComplete(true);
                    }
                    tokens = null;
                    return null;
                } else if (tokens[0].equals(CsvConstants.SQL)) {
                    CsvData data = new CsvData();
                    data.setNoBinaryOldData(noBinaryOldData);
                    data.setDataEventType(DataEventType.SQL);
                    data.putParsedData(CsvData.ROW_DATA, new String[] { tokens[1], tokens.length > 2 ? tokens[2] : "0" });
                    tokens = null;
                    return data;
                } else if (tokens[0].equals(CsvConstants.BSH)) {
                    CsvData data = new CsvData();
                    data.setNoBinaryOldData(noBinaryOldData);
                    data.setDataEventType(DataEventType.BSH);
                    data.putParsedData(CsvData.ROW_DATA, new String[] { tokens[1] });
                    tokens = null;
                    return data;
                } else if (tokens[0].equals(CsvConstants.CREATE)) {
                    CsvData data = new CsvData();
                    data.setNoBinaryOldData(noBinaryOldData);
                    data.setDataEventType(DataEventType.CREATE);
                    data.putParsedData(CsvData.ROW_DATA, new String[] { tokens[1] });
                    tokens = null;
                    return data;
                } else if (tokens[0].equals(CsvConstants.IGNORE)) {
                    if (batch != null) {
                        batch.setIgnored(true);
                    }
                } else if (tokens[0].equals(CsvConstants.STATS_COLUMNS)) {
                    statsColumns = CollectionUtils.copyOfRange(tokens, 1, tokens.length);
                } else if (tokens[0].equals(CsvConstants.STATS)) {
                    statsValues = CollectionUtils.copyOfRange(tokens, 1, tokens.length);
                    stats = stats != null ? stats : new DataReaderStatistics();
                    putStats(stats, statsColumns, statsValues);
                } else if (tokens[0].equals(CsvConstants.BASETIME)) {
                    baseTime = Long.parseLong(tokens[1]);
                    createTime = new Timestamp(baseTime);
                } else {
                    log.info("Unable to handle unknown csv values: " + Arrays.toString(tokens));
                }
                tokens = null;
            }
        } catch (IOException ex) {
            throw new IoException(ex);
        }
        return null;
    }

    public Batch nextBatch() {
        if (next instanceof Batch) {
            this.batch = (Batch) next;
            next = null;
            return batch;
        } else {
            do {
                next = readNext();
                if (next instanceof Batch) {
                    this.batch = (Batch) next;
                    next = null;
                    return batch;
                }
            } while (next != null);
        }
        return null;
    }

    public Relation nextRelation() {
        if (next instanceof Relation relation) {
            context.setLastParsedRelation(relation);
            next = null;
            return relation;
        } else {
            do {
                next = readNext();
                if (next instanceof Relation relation) {
                    context.setLastParsedRelation(relation);
                    next = null;
                    return relation;
                }
            } while (next != null && !(next instanceof Batch));
        }
        return null;
    }

    public CsvData nextData() {
        if (next instanceof CsvData) {
            CsvData data = (CsvData) next;
            next = null;
            return data;
        } else {
            do {
                next = readNext();
                if (next instanceof CsvData) {
                    CsvData data = (CsvData) next;
                    next = null;
                    return data;
                }
            } while (next != null && !(next instanceof Batch) && !(next instanceof Table));
        }
        return null;
    }

    public void close() {
        if (csvReader != null) {
            csvReader.close();
        }
        if (stagedResource != null) {
            stagedResource.close();
        }
    }

    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }

    protected void putStats(Statistics stats, String[] statsColumns, String[] statsValues) {
        if (statsValues != null && statsColumns != null) {
            for (int i = 0; i < statsColumns.length; i++) {
                String column = statsColumns[i];
                if (i < statsValues.length) {
                    long stat = Long.parseLong(statsValues[i]);
                    stats.set(column, stat);
                }
            }
        }
    }
}
