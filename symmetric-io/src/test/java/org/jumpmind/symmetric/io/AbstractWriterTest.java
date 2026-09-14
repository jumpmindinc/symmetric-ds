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
package org.jumpmind.symmetric.io;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.sqlite.SqliteDatabasePlatform;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterSettings;
import org.jumpmind.symmetric.io.data.writer.DynamicDefaultDatabaseWriter;
import org.jumpmind.symmetric.io.data.writer.IgnoreBatchException;
import org.jumpmind.util.Statistics;
import org.junit.Assert;

abstract public class AbstractWriterTest {
    protected static IDatabasePlatform platform;
    protected boolean errorExpected = true;
    protected final static String TEST_TABLE = "test_dataloader_table";
    protected final static String[] TEST_KEYS = { "id" };
    protected final static String[] TEST_COLUMNS = { "id", "string_value", "string_required_value",
            "char_value", "char_required_value", "date_value", "time_value", "boolean_value",
            "integer_value", "decimal_value", "double_value" };
    protected static long batchId = 10000;
    protected static long sequenceId = 10000;
    protected DatabaseWriterSettings writerSettings = new DatabaseWriterSettings();
    protected IDataWriter lastDataWriterUsed;

    protected synchronized long getNextBatchId() {
        return ++batchId;
    }

    protected synchronized long getBatchId() {
        return batchId;
    }

    protected synchronized String getNextId() {
        return String.valueOf(++sequenceId);
    }

    protected synchronized String getId() {
        return String.valueOf(sequenceId);
    }

    protected Table buildSourceTable(String tableName, String[] keyNames, String[] columnNames) {
        return Table.buildTable(tableName, keyNames, columnNames);
    }

    protected void writeData(CsvData data, String[] expectedValues) {
        writeData(data, expectedValues, getTestColumns());
    }

    protected String getTestTable() {
        return TEST_TABLE;
    }

    protected String[] getTestKeys() {
        return TEST_KEYS;
    }

    protected String[] getTestColumns() {
        return TEST_COLUMNS;
    }

    protected void writeData(CsvData... data) {
        Table table = buildSourceTable(getTestTable(), getTestKeys(), getTestColumns());
        writeData(new TableCsvData(table, data));
    }

    protected void writeData(CsvData data, String[] expectedValues, String[] columnNames) {
        writeData(data, expectedValues, getTestTable(), getTestKeys(), columnNames);
    }

    protected void writeData(CsvData data, String[] expectedValues, String tableName,
            String[] keyNames, String[] columnNames) {
        Table table = buildSourceTable(tableName, keyNames, columnNames);
        writeData(new TableCsvData(table, data));
        String[] pkData = data.getParsedData(CsvData.ROW_DATA);
        if (pkData == null) {
            pkData = data.getParsedData(CsvData.PK_DATA);
        }
        assertTestTableEquals(pkData[0], expectedValues);
    }

    protected long writeData(TableCsvData... datas) {
        return writeData(new DynamicDefaultDatabaseWriter(platform, platform, "sym", writerSettings), datas);
    }

    protected long writeData(BinaryEncoding encoding, TableCsvData... datas) {
        return writeData(new DynamicDefaultDatabaseWriter(platform, platform, "sym", writerSettings), encoding, datas);
    }

    protected long writeData(IDataWriter writer, TableCsvData... datas) {
        return writeData(writer, BinaryEncoding.BASE64, datas);
    }

    protected long writeData(IDataWriter writer, BinaryEncoding encoding, TableCsvData... datas) {
        this.lastDataWriterUsed = writer;
        try {
            for (TableCsvData tableCsvData : datas) {
                Batch batch = new Batch(BatchType.LOAD, getNextBatchId(), "default", encoding, "00000", "00001", false);
                DataContext context = new DataContext(batch);
                writer.open(context);
                try {
                    writer.start(batch);
                    if (writer.start(tableCsvData.table)) {
                        for (CsvData d : tableCsvData.data) {
                            writer.write(d);
                        }
                        writer.end(tableCsvData.table);
                    }
                    writer.end(batch, false);
                } catch (IgnoreBatchException ex) {
                    writer.end(batch, false);
                } catch (Exception ex) {
                    writer.end(batch, true);
                    if (!isErrorExpected()) {
                        if (ex instanceof RuntimeException) {
                            throw (RuntimeException) ex;
                        } else {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }
        } finally {
            writer.close();
        }
        long statementCount = 0;
        Collection<Statistics> stats = writer.getStatistics().values();
        for (Statistics statistics : stats) {
            statementCount += statistics.get(DataWriterStatisticConstants.ROWCOUNT);
        }
        return statementCount;
    }

    protected long writeData(IDataWriter writer, DataContext context, TableCsvData... datas) {
        return writeData(writer, context, BinaryEncoding.BASE64, datas);
    }

    protected long writeData(IDataWriter writer, DataContext context, BinaryEncoding encoding, TableCsvData... datas) {
        this.lastDataWriterUsed = writer;
        try {
            for (TableCsvData tableCsvData : datas) {
                Batch batch = new Batch(BatchType.LOAD, getNextBatchId(), "default", encoding, "00000", "00001", false);
                writer.open(context);
                try {
                    writer.start(batch);
                    if (writer.start(tableCsvData.table)) {
                        for (CsvData d : tableCsvData.data) {
                            writer.write(d);
                        }
                        writer.end(tableCsvData.table);
                    }
                    writer.end(batch, false);
                } catch (IgnoreBatchException ex) {
                    writer.end(batch, false);
                } catch (Exception ex) {
                    writer.end(batch, true);
                    if (!isErrorExpected()) {
                        if (ex instanceof RuntimeException) {
                            throw (RuntimeException) ex;
                        } else {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }
        } finally {
            writer.close();
        }
        long statementCount = 0;
        Collection<Statistics> stats = writer.getStatistics().values();
        for (Statistics statistics : stats) {
            statementCount += statistics.get(DataWriterStatisticConstants.ROWCOUNT);
        }
        return statementCount;
    }

    protected void assertTestTableEquals(String testTableId, String[] expectedValues) {
        String sql = "select " + getSelect(getTestColumns()) + " from " + getTestTable() + " where "
                + getWhere(getTestKeys());
        Map<String, Object> results = platform.getSqlTemplate().queryForMap(sql, Long.valueOf(testTableId));
        if (expectedValues != null) {
            expectedValues[1] = translateExpectedString(expectedValues[1], false);
            expectedValues[2] = translateExpectedString(expectedValues[2], true);
            expectedValues[3] = translateExpectedCharString(expectedValues[3], 50, false);
            expectedValues[4] = translateExpectedCharString(expectedValues[4], 50, true);
        }
        assertEquals(getTestColumns(), expectedValues, results);
    }

    protected String getSelect(String[] columns) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            str.append(columns[i]).append(i + 1 < columns.length ? ", " : "");
        }
        return str.toString();
    }

    protected String getWhere(String[] columns) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            str.append(columns[i]).append(" = ?").append(i + 1 < columns.length ? " AND " : "");
        }
        return str.toString();
    }

    protected String translateExpectedString(String value, boolean isRequired) {
        if (isRequired
                && (value == null || (value.equals("") && platform.getDatabaseInfo()
                        .isEmptyStringNulled()))) {
            return AbstractDatabasePlatform.REQUIRED_FIELD_NULL_SUBSTITUTE;
        } else if (value != null && value.equals("")
                && platform.getDatabaseInfo().isEmptyStringNulled()) {
            return null;
        }
        return value;
    }

    protected String translateExpectedCharString(String value, int size, boolean isRequired) {
        if (isRequired && value == null) {
            if (!platform.getDatabaseInfo().isRequiredCharColumnEmptyStringSameAsNull() ||
                    platform.getDatabaseInfo().isEmptyStringNulled()) {
                value = AbstractDatabasePlatform.REQUIRED_FIELD_NULL_SUBSTITUTE;
            }
        }
        if (value != null
                && ((StringUtils.isBlank(value) && platform.getDatabaseInfo()
                        .isBlankCharColumnSpacePadded()) || (StringUtils.isNotBlank(value) && platform
                                .getDatabaseInfo().isNonBlankCharColumnSpacePadded()))) {
            return StringUtils.rightPad(value, size);
        } else if (value != null && platform.getDatabaseInfo().isCharColumnSpaceTrimmed()) {
            return value.replaceFirst(" *$", "");
        }
        return value;
    }

    protected void assertEquals(String[] name, String[] expected, Map<String, Object> results) {
        if (expected == null) {
            Assert.assertNull("Expected empty results. " + printDatabase(), results);
        } else {
            Assert.assertNotNull(String.format("Did not find the expected row: %s.", Arrays.toString(expected)), results);
            for (int i = 0; i < expected.length; i++) {
                Object resultObj = results.get(name[i]);
                String resultValue = null;
                char decimal = ((DecimalFormat) DecimalFormat.getInstance())
                        .getDecimalFormatSymbols().getDecimalSeparator();
                if ((resultObj instanceof Double || resultObj instanceof BigDecimal) && expected[i].indexOf(decimal) != -1) {
                    DecimalFormat df = new DecimalFormat("0.00####################################");
                    resultValue = df.format(resultObj);
                } else if (resultObj instanceof Timestamp) {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.0000000");
                    resultValue = df.format(resultObj);
                } else if (resultObj instanceof Date) {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                    resultValue = df.format(resultObj);
                } else if (resultObj instanceof LocalDateTime) {
                    DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.0000000");
                    resultValue = ((LocalDateTime) resultObj).format(df);
                } else if (resultObj instanceof LocalDate) {
                    DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    resultValue = ((LocalDate) resultObj).format(df);
                } else if (resultObj instanceof Boolean) {
                    resultValue = ((Boolean) resultObj) ? "1" : "0";
                } else if (resultObj instanceof Double) {
                    resultValue = resultObj.toString();
                    if (platform instanceof SqliteDatabasePlatform) {
                        expected[i] = Double.valueOf(expected[i]).toString();
                    }
                } else if (resultObj != null) {
                    resultValue = resultObj.toString().trim();
                    expected[i] = expected[i].trim();
                }
                Assert.assertEquals(name[i] + ". " + printDatabase(), expected[i], resultValue);
            }
        }
    }

    protected String printDatabase() {
        return " The database we are testing against is " + platform.getName() + ".";
    }

    protected boolean isOracle() {
        return DatabaseNamesConstants.ORACLE.equals(platform.getName()) || DatabaseNamesConstants.ORACLE122.equals(platform.getName())
                || DatabaseNamesConstants.ORACLE23.equals(platform.getName());
    }

    public void setErrorExpected(boolean errorExpected) {
        this.errorExpected = errorExpected;
    }

    public boolean isErrorExpected() {
        return errorExpected;
    }

    public Map<String, Object> queryForRow(String id) {
        return platform.getSqlTemplate().queryForMap("select * from " + getTestTable() + " where id=?", Integer.valueOf(id));
    }

    protected static class TableCsvData {
        Table table;
        List<CsvData> data;

        public TableCsvData(Table table, CsvData... csvDatas) {
            this.table = table;
            this.data = new ArrayList<CsvData>();
            for (CsvData csvData : csvDatas) {
                this.data.add(csvData);
            }
        }

        public TableCsvData(Table table, List<CsvData> data) {
            this.table = table;
            this.data = data;
        }
    }

    protected long countRows(String tableName) {
        return platform.getSqlTemplate().queryForInt(String.format("select count(*) from %s", tableName));
    }
}
