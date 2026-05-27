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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataReader;
import org.jumpmind.util.CollectionUtils;
import org.jumpmind.util.FormatUtils;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractDataReader implements IDataReader {
    public static final String DATA_CONTEXT_CURRENT_CSV_DATA = "csvData";
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();
    protected IDatabasePlatform platform;
    protected List<IExtractDataReaderSource> sourcesToUse;
    protected IDatabasePlatform targetPlatform;
    protected IExtractDataReaderSource currentSource;
    @SuppressWarnings("removal")
    protected List<IExtractDataFilter> filters = new ArrayList<>();
    protected List<IRelationExtractDataFilter> relationFilters = new ArrayList<>();
    protected Batch batch;
    protected Relation relation;
    protected CsvData data;
    protected DataContext dataContext;
    protected boolean isSybaseASE;
    protected boolean isUsingUnitypes;
    private static final Logger log = LoggerFactory.getLogger(ExtractDataReader.class);

    public ExtractDataReader(IDatabasePlatform platform, IExtractDataReaderSource source, IDatabasePlatform targetPlatform) {
        this.sourcesToUse = new ArrayList<IExtractDataReaderSource>();
        this.sourcesToUse.add(source);
        this.platform = platform;
        this.targetPlatform = targetPlatform;
        this.isSybaseASE = platform.getName().equals(DatabaseNamesConstants.ASE);
    }

    @SuppressWarnings("removal")
    public ExtractDataReader(IDatabasePlatform platform, IExtractDataReaderSource source, List<IExtensionPoint> filters,
            boolean isUsingUnitypes, IDatabasePlatform targetPlatform) {
        this.sourcesToUse = new ArrayList<IExtractDataReaderSource>();
        this.sourcesToUse.add(source);
        this.platform = platform;
        this.targetPlatform = targetPlatform;
        for (IExtensionPoint filter : filters) {
            if (filter instanceof IExtractDataFilter tableFilter) {
                this.filters.add(tableFilter);
            } else if (filter instanceof IRelationExtractDataFilter relationFilter) {
                relationFilters.add(relationFilter);
            }
        }
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
    public Relation nextRelation() {
        this.relation = null;
        if (this.currentSource != null) {
            if (this.data == null) {
                this.data = this.currentSource.next();
            }
            if (this.data != null) {
                this.relation = this.currentSource.getTargetRelation();
                if (this.relation != null) {
                    this.relation.setCatalog(substituteVariables(this.relation.getCatalog()));
                    this.relation.setSchema(substituteVariables(this.relation.getSchema()));
                }
            }
        }
        if (this.relation == null && this.batch != null) {
            this.batch.setComplete(true);
        }
        return this.relation;
    }

    protected String substituteVariables(String sourceString) {
        if (sourceString != null && sourceString.indexOf("$(") != -1) {
            sourceString = FormatUtils.replace("sourceNodeId", (String) dataContext.get("sourceNodeId"), sourceString);
            String sourceNodeExternalId = (String) dataContext.get("sourceNodeExternalId");
            sourceString = FormatUtils.replace("sourceNodeExternalId", sourceNodeExternalId, sourceString);
            sourceString = FormatUtils.replace("sourceExternalId", sourceNodeExternalId, sourceString);
            sourceString = FormatUtils.replace("sourceNodeGroupId", (String) dataContext.get("sourceNodeGroupId"), sourceString);
            sourceString = FormatUtils.replace("targetNodeId", (String) dataContext.get("targetNodeId"), sourceString);
            String targetNodeExternalId = (String) dataContext.get("targetNodeExternalId");
            sourceString = FormatUtils.replace("targetNodeExternalId", targetNodeExternalId, sourceString);
            sourceString = FormatUtils.replace("targetExternalId", targetNodeExternalId, sourceString);
            sourceString = FormatUtils.replace("targetNodeGroupId", (String) dataContext.get("targetNodeGroupId"), sourceString);
        }
        return sourceString;
    }

    @SuppressWarnings("removal")
    @Override
    public CsvData nextData() {
        CsvData nextData = nextDataFromSource();
        if (nextData != null && !relationFilters.isEmpty()) {
            nextData = applyFilterLoop(nextData, csvData -> {
                boolean accepted = true;
                for (IRelationExtractDataFilter f : relationFilters) {
                    accepted &= f.filterData(dataContext, batch, relation, csvData);
                }
                return accepted;
            });
        } else if (nextData != null && !filters.isEmpty()) {
            nextData = applyFilterLoop(nextData, csvData -> {
                boolean accepted = true;
                for (IExtractDataFilter f : filters) {
                    accepted &= f.filterData(dataContext, batch, (Table) relation, csvData);
                }
                return accepted;
            });
        }
        return nextData;
    }

    private CsvData applyFilterLoop(CsvData current, Predicate<CsvData> allFiltersPass) {
        while (current != null && !allFiltersPass.test(current)) {
            current = nextDataFromSource();
        }
        return current;
    }

    protected CsvData nextDataFromSource() {
        if (this.relation != null) {
            if (this.data == null) {
                this.data = this.currentSource.next();
            }
            if (data == null) {
                closeCurrentSource();
            } else if (data.getDataEventType() == null) {
                // empty batch from reload
                data = null;
            } else {
                Relation targetRelation = this.currentSource.getTargetRelation();
                if (targetRelation != null && targetRelation.equals(this.relation)) {
                    data = enhanceWithLobsFromSourceIfNeeded(this.currentSource.getSourceRelation(), data);
                    if (isSybaseASE && isUsingUnitypes) {
                        data = convertUtf16toUTF8(this.currentSource.getSourceRelation(), data);
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
        this.relation = null;
        this.data = null;
    }

    @Override
    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }

    protected CsvData enhanceWithLobsFromSourceIfNeeded(Relation relation, CsvData data) {
        if (!this.currentSource.requiresLobsSelectedFromSource(data)) {
            return data;
        }
        if (data.getDataEventType() != DataEventType.UPDATE && data.getDataEventType() != DataEventType.INSERT) {
            return data;
        }
        List<Column> lobColumns = platform.getLobColumns(relation);
        if (!lobColumns.isEmpty()) {
            fillLobColumnsFromSource(relation, data, lobColumns);
        }
        return data;
    }

    private void fillLobColumnsFromSource(Relation relation, CsvData data, List<Column> lobColumns) {
        String[] columnNames = relation.getColumnNames();
        String[] rowData = data.getParsedData(CsvData.ROW_DATA);
        Column[] orderedColumns = relation.getColumns();
        Object[] objectValues = platform.getObjectValues(batch.getBinaryEncoding(), rowData, orderedColumns);
        Map<String, Object> columnDataMap = CollectionUtils.toMap(columnNames, objectValues);
        Column[] pkColumns = relation.getPrimaryKeyColumns();
        Object[] args = new Object[pkColumns.length];
        for (int i = 0; i < pkColumns.length; i++) {
            args[i] = columnDataMap.get(pkColumns[i].getName());
        }
        String sql = buildSelect(relation, lobColumns, pkColumns);
        Row row = targetPlatform.getSqlTemplate().queryForRow(sql, args);
        if (row == null) {
            row = createRowForRequiredLobs(lobColumns);
        }
        if (row != null) {
            for (Column lobColumn : lobColumns) {
                String valueForCsv = platform.isBlob(lobColumn)
                        ? convertBlobValueForCsv(lobColumn, row)
                        : row.getString(lobColumn.getName());
                rowData[ArrayUtils.indexOf(columnNames, lobColumn.getName())] = valueForCsv;
            }
            data.putParsedData(CsvData.ROW_DATA, rowData);
        }
    }

    private String convertBlobValueForCsv(Column lobColumn, Row row) {
        byte[] binaryData = row.getBytes(lobColumn.getName());
        if (binaryData == null) {
            return null;
        }
        if (isUniType(lobColumn.getJdbcTypeName())) {
            return convertUniTypeBlobValueForCsv(lobColumn, row);
        } else if (batch.getBinaryEncoding() == BinaryEncoding.BASE64) {
            return new String(Base64.encodeBase64(binaryData), Charset.defaultCharset());
        } else if (batch.getBinaryEncoding() == BinaryEncoding.HEX) {
            return new String(Hex.encodeHex(binaryData));
        } else {
            return new String(binaryData, Charset.defaultCharset());
        }
    }

    private String convertUniTypeBlobValueForCsv(Column lobColumn, Row row) {
        try {
            if (lobColumn.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                return row.getString(lobColumn.getName());
            }
            String baseString = "fffe" + row.getString(lobColumn.getName());
            String utf16String = new String(Hex.decodeHex(baseString), StandardCharsets.UTF_16);
            return new String(utf16String.getBytes(Charset.defaultCharset()), Charset.defaultCharset());
        } catch (DecoderException e) {
            log.warn("Failed to convert unitype blob for column '" + lobColumn.getName() + "'", e);
            return null;
        }
    }

    protected CsvData convertUtf16toUTF8(Relation relation, CsvData data) {
        if (data.getDataEventType() != DataEventType.UPDATE && data.getDataEventType() != DataEventType.INSERT) {
            return data;
        }
        List<Column> uniColumns = getUniColumns(relation);
        if (!uniColumns.isEmpty()) {
            convertUniColumnsInRowData(relation, data, uniColumns);
        }
        return data;
    }

    private void convertUniColumnsInRowData(Relation relation, CsvData data, List<Column> uniColumns) {
        String[] columnNames = relation.getColumnNames();
        String[] rowData = data.getParsedData(CsvData.ROW_DATA);
        boolean skipUnitext = this.currentSource.requiresLobsSelectedFromSource(data);
        String fullyQualifiedRelationName = relation.getFullyQualifiedName();
        for (Column uniColumn : uniColumns) {
            String jdbcType = uniColumn.getJdbcTypeName();
            if (jdbcType != null && jdbcType.equalsIgnoreCase("unitext") && skipUnitext) {
                continue;
            }
            int index = ArrayUtils.indexOf(columnNames, uniColumn.getName());
            if (index >= 0 && rowData[index] != null) {
                rowData[index] = convertColumnUtf16ToUtf8(uniColumn, rowData[index], fullyQualifiedRelationName);
            }
        }
        data.putParsedData(CsvData.ROW_DATA, rowData);
    }

    private String convertColumnUtf16ToUtf8(Column uniColumn, String value, String fullyQualifiedRelationName) {
        try {
            String baseString = "fffe" + value;
            byte[] utf16Bytes = Hex.decodeHex(baseString);
            String utf16Str = new String(utf16Bytes, StandardCharsets.UTF_16);
            return new String(utf16Str.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        } catch (DecoderException e) {
            log.warn("Failed to decode UTF-16 to UTF-8 for column '{}' with value '{}' in table '{}': {}",
                    uniColumn.getName(), value, fullyQualifiedRelationName, e.getMessage());
            return value;
        }
    }

    public List<Column> getUniColumns(Relation relation) {
        List<Column> uniColumns = new ArrayList<Column>(1);
        Column[] allColumns = relation.getColumns();
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

    protected String buildSelect(Relation relation, List<Column> lobColumns, Column[] pkColumns) {
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
        sql.append(relation.getQualifiedName(quote, dbInfo.getCatalogSeparator(), dbInfo.getSchemaSeparator()));
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
