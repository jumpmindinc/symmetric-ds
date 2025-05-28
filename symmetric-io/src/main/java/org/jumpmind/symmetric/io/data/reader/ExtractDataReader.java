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
package org.jumpmind.symmetric.io.data.reader;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataReader;
import org.jumpmind.util.CollectionUtils;
import org.jumpmind.util.FormatUtils;
import org.jumpmind.util.Statistics;

public class ExtractDataReader implements IDataReader {
    public static final String DATA_CONTEXT_CURRENT_CSV_DATA = "csvData";
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();
    protected IDatabasePlatform platform;
    protected IDatabasePlatform targetPlatform;
    protected List<IExtractDataReaderSource> sourcesToUse;
    protected IExtractDataReaderSource currentSource;
    protected List<IExtractDataFilter> filters;
    protected Batch batch;
    protected Table table;
    protected CsvData data;
    protected DataContext dataContext;
    protected boolean isSybaseASE;
    protected boolean isUsingUnitypes;

    public ExtractDataReader(IDatabasePlatform platform, IExtractDataReaderSource source, IDatabasePlatform targetPlatform) {
        this.sourcesToUse = new ArrayList<IExtractDataReaderSource>();
        this.sourcesToUse.add(source);
        this.platform = platform;
        this.targetPlatform = targetPlatform;
        this.isSybaseASE = platform.getName().equals(DatabaseNamesConstants.ASE);
    }

    public ExtractDataReader(IDatabasePlatform platform, IExtractDataReaderSource source, List<IExtractDataFilter> filters,
            boolean isUsingUnitypes, IDatabasePlatform targetPlatform) {
        this.sourcesToUse = new ArrayList<IExtractDataReaderSource>();
        this.sourcesToUse.add(source);
        this.platform = platform;
        this.targetPlatform = targetPlatform;
        this.filters = filters;
        this.isUsingUnitypes = isUsingUnitypes;
        this.isSybaseASE = platform.getName().equals(DatabaseNamesConstants.ASE);
    }

    public ExtractDataReader(IDatabasePlatform platform, List<IExtractDataReaderSource> sources, IDatabasePlatform targetPlatform) {
        this.sourcesToUse = new ArrayList<IExtractDataReaderSource>(sources);
        this.platform = platform;
        this.targetPlatform = targetPlatform;
        isSybaseASE = platform.getName().equals(DatabaseNamesConstants.ASE);
    }

    @Override
    public void open(DataContext context) {
        this.dataContext = context;
    }

    @Override
    public Batch nextBatch() {
        closeCurrentSource();
        if (this.sourcesToUse.size() > 0) {
            this.currentSource = this.sourcesToUse.remove(0);
            this.batch = this.currentSource.getBatch();
        } else {
            this.batch = null;
        }
        return this.batch;
    }

    @Override
    public Table nextTable() {
        this.table = null;
        if (this.currentSource != null) {
            if (this.data == null) {
                this.data = this.currentSource.next();
            }
            if (this.data != null) {
                this.table = this.currentSource.getTargetTable();
                if (this.table != null) {
                    this.table.setCatalog(substituteVariables(this.table.getCatalog()));
                    this.table.setSchema(substituteVariables(this.table.getSchema()));
                }
            }
        }
        if (this.table == null && this.batch != null) {
            this.batch.setComplete(true);
        }
        return this.table;
    }

    protected String substituteVariables(String sourceString) {
        if (sourceString != null && sourceString.indexOf("$(") != -1) {
            sourceString = FormatUtils.replace("sourceNodeId", (String) dataContext.get("sourceNodeId"), sourceString);
            sourceString = FormatUtils.replace("sourceNodeExternalId", (String) dataContext.get("sourceNodeExternalId"), sourceString);
            sourceString = FormatUtils.replace("sourceNodeGroupId", (String) dataContext.get("sourceNodeGroupId"), sourceString);
            sourceString = FormatUtils.replace("targetNodeId", (String) dataContext.get("targetNodeId"), sourceString);
            sourceString = FormatUtils.replace("targetNodeExternalId", (String) dataContext.get("targetNodeExternalId"), sourceString);
            sourceString = FormatUtils.replace("targetNodeGroupId", (String) dataContext.get("targetNodeGroupId"), sourceString);
        }
        return sourceString;
    }

    @Override
    public CsvData nextData() {
        CsvData nextData = nextDataFromSource();
        if (nextData != null && filters != null && filters.size() != 0) {
            boolean shouldExtract = true;
            while (shouldExtract) {
                for (IExtractDataFilter filter : filters) {
                    shouldExtract &= filter.filterData(dataContext, batch, table, nextData);
                }
                if (shouldExtract) {
                    break;
                } else {
                    nextData = nextDataFromSource();
                    shouldExtract = nextData != null;
                }
            }
        }
        return nextData;
    }

    protected CsvData nextDataFromSource() {
        if (this.table != null) {
            if (this.data == null) {
                this.data = this.currentSource.next();
            }
            if (data == null) {
                closeCurrentSource();
            } else if (data.getDataEventType() == null) {
                // empty batch from reload
                data = null;
            } else {
                Table targetTable = this.currentSource.getTargetTable();
                if (targetTable != null && targetTable.equals(this.table)) {
                    data = enhanceWithLobsFromSourceIfNeeded(this.currentSource.getSourceTable(), data);
                    if (isSybaseASE && isUsingUnitypes && !this.currentSource.requiresLobsSelectedFromSource(data)) {
                        data = convertUtf16toUTF8(this.currentSource.getSourceTable(), data);
                    }
                } else {
                    // the table has changed
                    return null;
                }
            }
        }
        CsvData dataToReturn = this.data;
        this.data = null;
        this.dataContext.put(DATA_CONTEXT_CURRENT_CSV_DATA, dataToReturn);
        return dataToReturn;
    }

    @Override
    public void close() {
        closeCurrentSource();
        this.batch = null;
    }

    protected void closeCurrentSource() {
        if (this.currentSource != null) {
            this.currentSource.close();
            this.currentSource = null;
        }
        this.table = null;
        this.data = null;
    }

    @Override
    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }

    protected CsvData enhanceWithLobsFromSourceIfNeeded(Table table, CsvData data) {
        if (this.currentSource.requiresLobsSelectedFromSource(data)
                && (data.getDataEventType() == DataEventType.UPDATE || data.getDataEventType() == DataEventType.INSERT)) {
            List<Column> lobColumns = platform.getLobColumns(table);
            if (lobColumns.size() > 0) {
                String[] columnNames = table.getColumnNames();
                String[] rowData = data.getParsedData(CsvData.ROW_DATA);
                Column[] orderedColumns = table.getColumns();
                Object[] objectValues = platform.getObjectValues(batch.getBinaryEncoding(),
                        rowData, orderedColumns);
                Map<String, Object> columnDataMap = CollectionUtils
                        .toMap(columnNames, objectValues);
                Column[] pkColumns = table.getPrimaryKeyColumns();
                ISqlTemplate sqlTemplate = targetPlatform.getSqlTemplate();
                Object[] args = new Object[pkColumns.length];
                for (int i = 0; i < pkColumns.length; i++) {
                    args[i] = columnDataMap.get(pkColumns[i].getName());
                }
                String sql = buildSelect(table, lobColumns, pkColumns);
                Row row = sqlTemplate.queryForRow(sql, args);
                if (row == null) {
                    row = createRowForRequiredLobs(lobColumns);
                }
                if (row != null) {
                    for (Column lobColumn : lobColumns) {
                        String valueForCsv = null;
                        if (platform.isBlob(lobColumn)) {
                            byte[] binaryData = row.getBytes(lobColumn.getName());
                            if (binaryData != null) {
                                if (isUniType(lobColumn.getJdbcTypeName())) {
                                    try {
                                        if (lobColumn.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                                            valueForCsv = row.getString(lobColumn.getName());
                                        } else {
                                            String utf16String = null;
                                            String baseString = row.getString(lobColumn.getName());
                                            baseString = "fffe" + baseString;
                                            utf16String = new String(Hex.decodeHex(baseString), "UTF-16");
                                            String utf8String = new String(utf16String.getBytes(Charset.defaultCharset()), Charset.defaultCharset());
                                            valueForCsv = utf8String;
                                        }
                                    } catch (UnsupportedEncodingException | DecoderException e) {
                                        e.printStackTrace();
                                    }
                                } else if (batch.getBinaryEncoding() == BinaryEncoding.BASE64) {
                                    valueForCsv = new String(Base64.encodeBase64(binaryData), Charset.defaultCharset());
                                } else if (batch.getBinaryEncoding() == BinaryEncoding.HEX) {
                                    valueForCsv = new String(Hex.encodeHex(binaryData));
                                } else {
                                    valueForCsv = new String(binaryData, Charset.defaultCharset());
                                }
                                binaryData = null;
                            }
                        } else {
                            valueForCsv = row.getString(lobColumn.getName());
                        }
                        int index = ArrayUtils.indexOf(columnNames, lobColumn.getName());
                        rowData[index] = valueForCsv;
                    }
                    data.putParsedData(CsvData.ROW_DATA, rowData);
                }
            }
        }
        return data;
    }

    protected CsvData convertUtf16toUTF8(Table table, CsvData data) {
        if (data.getDataEventType() == DataEventType.UPDATE || data.getDataEventType() == DataEventType.INSERT) {
            List<Column> uniColumns = getUniColumns(table);
            if (!uniColumns.isEmpty()) {
                String[] columnNames = table.getColumnNames();
                String[] rowData = data.getParsedData(CsvData.ROW_DATA);
                Column[] orderedColumns = table.getColumns();
                Object[] objectValues = platform.getObjectValues(batch.getBinaryEncoding(), rowData, orderedColumns);
                Map<String, Object> columnDataMap = CollectionUtils.toMap(columnNames, objectValues);
                Column[] pkColumns = table.getPrimaryKeyColumns();
                Object[] args = new Object[pkColumns.length];
                for (int i = 0; i < pkColumns.length; i++) {
                    if (pkColumns[i].getJdbcTypeName() != null && (isUniType(pkColumns[i].getJdbcTypeName()))) {
                        String utf16String = null;
                        String baseString = (String) columnDataMap.get(pkColumns[i].getName());
                        baseString = "fffe" + baseString;
                        try {
                            utf16String = new String(Hex.decodeHex(baseString), "UTF-16");
                        } catch (UnsupportedEncodingException | DecoderException e) {
                            e.printStackTrace();
                        }
                        String utf8String = new String(utf16String.getBytes(Charset.defaultCharset()), Charset.defaultCharset());
                        args[i] = utf8String;
                    } else {
                        args[i] = columnDataMap.get(pkColumns[i].getName());
                    }
                }
                for (Column uniColumn : uniColumns) {
                    try {
                        int index = ArrayUtils.indexOf(columnNames, uniColumn.getName());
                        if (rowData[index] != null && !uniColumn.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                            String utf16String = null;
                            String baseString = rowData[index];
                            baseString = "fffe" + baseString;
                            utf16String = new String(Hex.decodeHex(baseString), "UTF-16");
                            String utf8String = new String(utf16String.getBytes(Charset.defaultCharset()), Charset.defaultCharset());
                            rowData[index] = utf8String;
                        }
                    } catch (UnsupportedEncodingException | DecoderException e) {
                        e.printStackTrace();
                    }
                }
                data.putParsedData(CsvData.ROW_DATA, rowData);
            }
        }
        return data;
    }

    public List<Column> getUniColumns(Table table) {
        List<Column> uniColumns = new ArrayList<Column>(1);
        Column[] allColumns = table.getColumns();
        for (Column column : allColumns) {
            if (isUniType(column.getJdbcTypeName())) {
                uniColumns.add(column);
            }
        }
        return uniColumns;
    }

    public boolean isUniType(String type) {
        return type.equalsIgnoreCase("UNITEXT") || type.equalsIgnoreCase("UNICHAR") || type.equalsIgnoreCase("UNIVARCHAR");
    }

    protected String buildSelect(Table table, List<Column> lobColumns, Column[] pkColumns) {
        StringBuilder sql = new StringBuilder("select ");
        DatabaseInfo dbInfo = platform.getDatabaseInfo();
        String quote = platform.getDdlBuilder().isDelimitedIdentifierModeOn() ? dbInfo.getDelimiterToken() : "";
        for (Column lobColumn : lobColumns) {
            if ("XMLTYPE".equalsIgnoreCase(lobColumn.getJdbcTypeName()) && 2009 == lobColumn.getJdbcTypeCode()) {
                sql.append("extract(").append(quote).append(lobColumn.getName()).append(quote);
                sql.append(", '/').getClobVal()");
            } else if (isUniType(lobColumn.getJdbcTypeName()) && !lobColumn.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                sql.append("bintostr(convert(varbinary(16384)," + lobColumn.getName() + ")) as " + lobColumn.getName());
            } else {
                sql.append(quote).append(lobColumn.getName()).append(quote);
            }
            sql.append(",");
        }
        sql.delete(sql.length() - 1, sql.length());
        sql.append(" from ");
        sql.append(table.getQualifiedTableName(quote, dbInfo.getCatalogSeparator(), dbInfo.getSchemaSeparator()));
        sql.append(" where ");
        for (Column col : pkColumns) {
            sql.append(quote).append(col.getName()).append(quote);
            sql.append("=? and ");
        }
        sql.delete(sql.length() - 5, sql.length());
        return sql.toString();
    }

    /**
     * When the row is missing because it was deleted, we need to temporarily satisfy not-null constraint at target
     */
    protected Row createRowForRequiredLobs(List<Column> lobColumns) {
        Row row = null;
        boolean isRequired = false;
        for (Column lobColumn : lobColumns) {
            if (lobColumn.isRequired()) {
                isRequired = true;
                break;
            }
        }
        if (isRequired) {
            row = new Row(lobColumns.size());
            for (Column lobColumn : lobColumns) {
                if (lobColumn.isRequired()) {
                    if (platform.isBlob(lobColumn)) {
                        row.put(lobColumn.getName(), new byte[0]);
                    } else {
                        row.put(lobColumn.getName(), "");
                    }
                } else {
                    row.put(lobColumn.getName(), null);
                }
            }
        }
        return row;
    }
}
