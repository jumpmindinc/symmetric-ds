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

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.util.LinkedCaseInsensitiveMap;

/**
 * Holder for references to both parsed and unparsed CSV data.
 */
public class CsvData {
    public static final int MAX_DATA_SIZE_TO_PRINT_TO_LOG = 32768;
    public static final String OLD_DATA = "oldData";
    public static final String ROW_DATA = "rowData";
    public static final String PK_DATA = "pkData";
    public static final String RESOLVE_DATA = "resolveData";
    public static final String ATTRIBUTE_TABLE_NAME = "tableName";
    public static final String ATTRIBUTE_CHANNEL_ID = "channelId";
    public static final String ATTRIBUTE_TABLE_ID = "tableId";
    public static final String ATTRIBUTE_TX_ID = "transactionId";
    public static final String ATTRIBUTE_SOURCE_NODE_ID = "sourceNodeId";
    public static final String ATTRIBUTE_EXTERNAL_DATA = "externalData";
    public static final String ATTRIBUTE_NODE_LIST = "nodeList";
    public static final String ATTRIBUTE_DATA_ID = "dataId";
    public static final String ATTRIBUTE_CREATE_TIME = "createTime";
    private Map<String, String[]> parsedCsvData = null;
    private Map<String, String> csvData = null;
    private Map<String, Object> attributes;
    private boolean noBinaryOldData = false;
    protected DataEventType dataEventType;
    protected boolean[] changedDataIndicators;

    public CsvData(DataEventType dataEventType) {
        this.dataEventType = dataEventType;
    }

    public CsvData(DataEventType dataEventType, String[] pkData, String[] rowData) {
        this(dataEventType);
        this.putParsedData(PK_DATA, pkData);
        this.putParsedData(ROW_DATA, rowData);
    }

    public CsvData(DataEventType dataEventType, String[] rowData) {
        this(dataEventType);
        this.putParsedData(ROW_DATA, rowData);
    }

    public CsvData(DataEventType dataEventType, String[] rowData, String[] oldData,
            String[] resolveData) {
        this(dataEventType);
        this.putParsedData(ROW_DATA, rowData);
        this.putParsedData(OLD_DATA, oldData);
        this.putParsedData(RESOLVE_DATA, resolveData);
    }

    public CsvData() {
    }

    public boolean contains(String key) {
        return (parsedCsvData != null && parsedCsvData.get(key) != null)
                || (csvData != null && csvData.get(key) != null);
    }

    public void setDataEventType(DataEventType dataEventType) {
        this.dataEventType = dataEventType;
    }

    public DataEventType getDataEventType() {
        return dataEventType;
    }

    public void putAttribute(String attributeName, Object attributeValue) {
        if (attributes == null) {
            attributes = new HashMap<String, Object>();
        }
        attributes.put(attributeName, attributeValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String attributeName) {
        return attributes == null ? null : (T) attributes.get(attributeName);
    }

    public void removeCsvData(String key) {
        if (csvData != null) {
            csvData.remove(key);
        }
    }

    public void removeParsedData(String key) {
        if (parsedCsvData != null) {
            parsedCsvData.remove(key);
        }
    }

    public void removeAllData(String key) {
        removeParsedData(key);
        removeCsvData(key);
    }

    public void putCsvData(String key, String data) {
        removeAllData(key);
        if (csvData == null) {
            csvData = new HashMap<String, String>(2);
        }
        changedDataIndicators = null;
        csvData.put(key, data);
    }

    public String getCsvData(String key) {
        String data = null;
        if (csvData != null) {
            data = csvData.get(key);
        }
        if (data == null && parsedCsvData != null) {
            String[] parsedData = parsedCsvData.get(key);
            if (parsedData != null) {
                data = CsvUtils.escapeCsvData(parsedData);
                // swap out data for parsed data so we don't
                // don't double the amount of memory being used
                putCsvData(key, data);
            }
        }
        return data;
    }

    public boolean[] getChangedDataIndicators() {
        if (changedDataIndicators == null) {
            String[] newData = getParsedData(ROW_DATA);
            boolean[] changes = new boolean[newData.length];
            String[] oldData = getParsedData(OLD_DATA);
            for (int i = 0; i < newData.length; i++) {
                if (oldData != null && oldData.length > i) {
                    if (newData[i] == null) {
                        changes[i] = oldData[i] != null;
                    } else if (oldData[i] == null) {
                        changes[i] = newData[i] != null;
                    } else {
                        changes[i] = !newData[i].equals(oldData[i]);
                    }
                } else {
                    changes[i] = true;
                }
            }
            changedDataIndicators = changes;
        }
        return changedDataIndicators;
    }

    public final void putParsedData(String key, String[] data) {
        removeAllData(key);
        if (parsedCsvData == null) {
            parsedCsvData = new HashMap<String, String[]>(2);
        }
        changedDataIndicators = null;
        parsedCsvData.put(key, data);
    }

    public String[] getParsedData(String key) {
        String[] values = null;
        if (parsedCsvData != null && parsedCsvData.containsKey(key)) {
            values = parsedCsvData.get(key);
        } else if (csvData != null && csvData.containsKey(key)) {
            String data = csvData.get(key);
            if (data != null) {
                values = CsvUtils.tokenizeCsvData(data);
                putParsedData(key, values);
            }
        }
        return values;
    }

    public Map<String, String> toKeyColumnValuePairs(Table table) {
        Map<String, String> data = toColumnNameValuePairs(table.getPrimaryKeyColumnNames(), CsvData.PK_DATA);
        if (data.size() == 0) {
            data = toColumnNameValuePairs(table.getColumnNames(), CsvData.OLD_DATA);
            if (data.size() == 0) {
                data = toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
            }
            Column[] columns = table.getColumns();
            for (Column column : columns) {
                if (!column.isPrimaryKey()) {
                    data.remove(column.getName());
                }
            }
        }
        return data;
    }

    public String[] getPkData(Table table) {
        Map<String, String> data = toKeyColumnValuePairs(table);
        return data.values().toArray(new String[data.size()]);
    }

    public Map<String, String> toColumnNameValuePairs(String[] keyNames, String key) {
        String[] values = getParsedData(key);
        if (values != null && keyNames != null && values.length >= keyNames.length) {
            Map<String, String> map = new LinkedCaseInsensitiveMap<String>(keyNames.length);
            for (int i = 0; i < keyNames.length; i++) {
                map.put(keyNames[i], values[i]);
            }
            return map;
        } else {
            return new HashMap<String, String>(0);
        }
    }

    public boolean requiresTable() {
        return dataEventType != null && dataEventType != DataEventType.CREATE
                && dataEventType != DataEventType.BSH;
    }

    public boolean isNoBinaryOldData() {
        return noBinaryOldData;
    }

    public void setNoBinaryOldData(boolean noBinaryOldData) {
        this.noBinaryOldData = noBinaryOldData;
    }

    public CsvData copyWithoutOldData() {
        CsvData data = new CsvData(getDataEventType(), getParsedData(CsvData.ROW_DATA));
        data.attributes = attributes;
        return data;
    }

    public void writeCsvDataDetails(StringBuilder message) {
        String rowData = getCsvData(CsvData.PK_DATA);
        if (StringUtils.isNotBlank(rowData)) {
            message.append("Failed pk data was: ");
            message.append(StringUtils.abbreviate(rowData, MAX_DATA_SIZE_TO_PRINT_TO_LOG));
            message.append("\n");
        }
        rowData = getCsvData(CsvData.ROW_DATA);
        if (StringUtils.isNotBlank(rowData)) {
            if (rowData.length() < MAX_DATA_SIZE_TO_PRINT_TO_LOG) {
                message.append("Failed row data was: ");
                message.append(rowData);
                message.append("\n");
            } else {
                message.append("Row data was bigger than ");
                message.append(MAX_DATA_SIZE_TO_PRINT_TO_LOG);
                message.append(" bytes (it was ");
                message.append(rowData.length());
                message.append(" bytes).  It will not be printed to the log file");
            }
        }
        rowData = getCsvData(CsvData.OLD_DATA);
        if (StringUtils.isNotBlank(rowData)) {
            if (rowData.length() < MAX_DATA_SIZE_TO_PRINT_TO_LOG) {
                message.append("Failed old data was: ");
                message.append(rowData);
                message.append("\n");
            } else {
                message.append("Old data was bigger than ");
                message.append(MAX_DATA_SIZE_TO_PRINT_TO_LOG);
                message.append(" bytes (it was ");
                message.append(rowData.length());
                message.append(" bytes).  It will not be printed to the log file");
            }
        }
    }

    public long getSizeInBytes() {
        long size = 0;
        if (csvData != null) {
            Collection<String> values = csvData.values();
            for (String string : values) {
                if (string != null) {
                    size += string.getBytes(Charset.defaultCharset()).length;
                }
            }
        }
        return size;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}