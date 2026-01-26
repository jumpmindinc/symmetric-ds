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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.DbTestUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.ase.AseDatabasePlatform;
import org.jumpmind.db.platform.informix.InformixDatabasePlatform;
import org.jumpmind.db.platform.mysql.MySqlDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlDatabasePlatform;
import org.jumpmind.db.platform.sqlanywhere.SqlAnywhereDatabasePlatform;
import org.jumpmind.symmetric.io.AbstractWriterTest;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.Conflict.DetectConflict;
import org.jumpmind.symmetric.io.data.writer.Conflict.ResolveConflict;
import org.jumpmind.util.CollectionUtils;
import org.jumpmind.util.Statistics;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DatabaseWriterTest extends AbstractWriterTest {
    @BeforeAll
    public static void setup() throws Exception {
        platform = DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT);
        platform.createDatabase(platform.readDatabaseFromXml("/testDatabaseWriter.xml", true),
                true, false);
    }

    @BeforeEach
    public void notExpectingError() {
        setErrorExpected(false);
        writerSettings.setDefaultConflictSetting(new Conflict());
    }

    @Test
    public void testUpdateDetectTimestampNewerWins() {
        Conflict setting = new Conflict();
        setting.setConflictId("unit.test");
        setting.setDetectType(DetectConflict.USE_TIMESTAMP);
        setting.setDetectExpression("time_value");
        setting.setResolveRowOnly(true);
        setting.setResolveChangesOnly(true);
        setting.setResolveType(ResolveConflict.NEWER_WINS);
        writerSettings.setDefaultConflictSetting(setting);
        String id = getNextId();
        String[] originalValues = massageExpectectedResultsForDialect(new String[] { id, "string2",
                "string not null2", "char2", "char not null2", "2007-01-02 03:20:10.000",
                "2012-03-12 07:00:00.000", "0", "47", "67.89", "-0.0747663" });
        CsvData data = new CsvData(DataEventType.INSERT, originalValues);
        writeData(data, originalValues);
        String[] updateShouldNotBeApplied = CollectionUtils.copyOfRange(originalValues, 0,
                originalValues.length);
        updateShouldNotBeApplied[2] = "updated string";
        updateShouldNotBeApplied[6] = "2012-03-12 06:00:00.0";
        data = new CsvData(DataEventType.UPDATE,
                massageExpectectedResultsForDialect(updateShouldNotBeApplied));
        writeData(data, originalValues);
        String[] updateShouldBeApplied = CollectionUtils.copyOfRange(originalValues, 0,
                originalValues.length);
        updateShouldBeApplied[2] = "string3";
        updateShouldBeApplied[6] = "2012-03-12 08:00:00.000";
        data = new CsvData(DataEventType.UPDATE,
                massageExpectectedResultsForDialect(updateShouldBeApplied));
        writeData(data, updateShouldBeApplied);
    }

    @Test
    public void testInsertDetectTimestampNewerWins() {
        Conflict setting = new Conflict();
        setting.setConflictId("unit.test");
        setting.setDetectType(DetectConflict.USE_TIMESTAMP);
        setting.setDetectExpression("time_value");
        setting.setResolveRowOnly(true);
        setting.setResolveChangesOnly(false);
        setting.setResolveType(ResolveConflict.NEWER_WINS);
        writerSettings.setDefaultConflictSetting(setting);
        String id = getNextId();
        String[] originalValues = massageExpectectedResultsForDialect(new String[] { id, "string2",
                "string not null2", "char2", "char not null2", "2007-01-02 03:20:10.000",
                "2012-03-12 07:00:00.000", "0", "47", "67.89", "-0.0747663" });
        CsvData data = new CsvData(DataEventType.INSERT, originalValues);
        writeData(data, originalValues);
        String[] updateShouldNotBeApplied = CollectionUtils.copyOfRange(originalValues, 0,
                originalValues.length);
        updateShouldNotBeApplied[2] = "updated string";
        updateShouldNotBeApplied[6] = "2012-03-12 06:00:00.0";
        data = new CsvData(DataEventType.INSERT,
                massageExpectectedResultsForDialect(updateShouldNotBeApplied));
        writeData(data, originalValues);
        String[] updateShouldBeApplied = CollectionUtils.copyOfRange(originalValues, 0,
                originalValues.length);
        updateShouldBeApplied[2] = "string3";
        updateShouldBeApplied[6] = "2012-03-12 08:00:00.000";
        data = new CsvData(DataEventType.INSERT,
                massageExpectectedResultsForDialect(updateShouldBeApplied));
        writeData(data, updateShouldBeApplied);
    }

    @Test
    public void testUpdateDetectVersionNewWins() {
        Conflict setting = new Conflict();
        setting.setConflictId("unit.test");
        setting.setDetectType(DetectConflict.USE_VERSION);
        setting.setDetectExpression("integer_value");
        setting.setResolveRowOnly(true);
        setting.setResolveChangesOnly(false);
        setting.setResolveType(ResolveConflict.NEWER_WINS);
        writerSettings.setDefaultConflictSetting(setting);
        String id = getNextId();
        String[] originalValues = massageExpectectedResultsForDialect(new String[] { id, "string2",
                "string not null2", "char2", "char not null2", "2007-01-02 03:20:10.000",
                "2012-03-12 07:00:00.000", "0", "47", "67.89", "-0.0747663" });
        CsvData data = new CsvData(DataEventType.INSERT, originalValues);
        writeData(data, originalValues);
        String[] updateShouldNotBeApplied = CollectionUtils.copyOfRange(originalValues, 0,
                originalValues.length);
        updateShouldNotBeApplied[2] = "updated string";
        updateShouldNotBeApplied[8] = "46";
        data = new CsvData(DataEventType.UPDATE,
                massageExpectectedResultsForDialect(updateShouldNotBeApplied));
        writeData(data, originalValues);
        String[] updateShouldBeApplied = CollectionUtils.copyOfRange(originalValues, 0,
                originalValues.length);
        updateShouldBeApplied[2] = "string3";
        updateShouldBeApplied[8] = "48";
        data = new CsvData(DataEventType.UPDATE,
                massageExpectectedResultsForDialect(updateShouldBeApplied));
        writeData(data, updateShouldBeApplied);
    }

    @Test
    public void testUpdateDetectVersionIgnoreBatch() {
        Conflict setting = new Conflict();
        setting.setConflictId("unit.test");
        setting.setDetectType(DetectConflict.USE_VERSION);
        setting.setDetectExpression("integer_value");
        setting.setResolveRowOnly(false);
        setting.setResolveChangesOnly(false);
        setting.setResolveType(ResolveConflict.NEWER_WINS);
        writerSettings.setDefaultConflictSetting(setting);
        String id = getNextId();
        String[] originalValues = massageExpectectedResultsForDialect(new String[] { id, "string2",
                "string not null2", "char2", "char not null2", "2007-01-02 03:20:10.000",
                "2012-03-12 07:00:00.000", "0", "2", "67.89", "-0.0747663" });
        CsvData data = new CsvData(DataEventType.INSERT, originalValues);
        writeData(data, originalValues);
        long before = countRows(TEST_TABLE);
        String[] updateShouldNotBeApplied = CollectionUtils.copyOfRange(originalValues, 0,
                originalValues.length);
        updateShouldNotBeApplied[2] = "updated string";
        updateShouldNotBeApplied[8] = "1";
        CsvData update = new CsvData(DataEventType.UPDATE,
                massageExpectectedResultsForDialect(updateShouldNotBeApplied));
        String newId = getNextId();
        CsvData newInsert = new CsvData(DataEventType.INSERT,
                massageExpectectedResultsForDialect(new String[] { newId, "string2",
                        "string not null2", "char2", "char not null2", "2007-01-02 03:20:10.0",
                        "2012-03-12 07:00:00.0", "0", "2", "67.89", "-0.0747663" }));
        writeData(update, newInsert);
        Assert.assertEquals(before, countRows(TEST_TABLE));
    }

    @Test
    public void testUpdateDetectOldDataIgnoreRow() {
    }

    @Test
    public void testUpdateDetectOldDataIgnoreBatch() {
    }

    @Test
    public void testUpdateDetectOldDataManual() {
        Conflict setting = new Conflict();
        setting.setConflictId("unit.test");
        setting.setDetectType(DetectConflict.USE_OLD_DATA);
        setting.setResolveRowOnly(false);
        setting.setResolveChangesOnly(false);
        setting.setResolveType(ResolveConflict.MANUAL);
        writerSettings.setDefaultConflictSetting(setting);
        String origId = getNextId();
        String[] originalValues = massageExpectectedResultsForDialect(new String[] { origId,
                "string2", "changed value", "char2", "char not null2", "2007-01-02 03:20:10.000",
                "2012-03-12 07:00:00.000", "0", "2", "67.89", "-0.0747663" });
        CsvData data = new CsvData(DataEventType.INSERT, originalValues);
        writeData(data, originalValues);
        String[] oldData = CollectionUtils.copyOfRange(originalValues, 0, originalValues.length);
        oldData[2] = "original value";
        oldData = massageExpectectedResultsForDialect(oldData);
        String[] newData = CollectionUtils.copyOfRange(originalValues, 0, originalValues.length);
        newData[2] = "new value";
        newData = massageExpectectedResultsForDialect(newData);
        CsvData update = new CsvData(DataEventType.UPDATE);
        update.putParsedData(CsvData.ROW_DATA, newData);
        update.putParsedData(CsvData.OLD_DATA, oldData);
        try {
            writeData(update);
            Assert.fail("Should have received a conflict exception");
        } catch (ConflictException ex) {
            Statistics stats = lastDataWriterUsed.getStatistics().values().iterator().next();
            long statementNumber = stats.get(DataWriterStatisticConstants.ROWCOUNT);
            ResolvedData resolvedData = new ResolvedData(statementNumber,
                    update.getCsvData(CsvData.ROW_DATA), false);
            writerSettings.setResolvedData(resolvedData);
            writeData(update);
            Map<String, Object> row = queryForRow(origId);
            Assert.assertNotNull(row);
            Assert.assertEquals(newData[2], row.get("string_required_value"));
        }
    }

    @Test
    public void testUpdateDetectOldDataWithNullManual() {
        Conflict setting = new Conflict();
        setting.setConflictId("unit.test");
        setting.setDetectType(DetectConflict.USE_OLD_DATA);
        setting.setResolveRowOnly(false);
        setting.setResolveChangesOnly(false);
        setting.setResolveType(ResolveConflict.MANUAL);
        writerSettings.setDefaultConflictSetting(setting);
        String origId = getNextId();
        String[] originalValues = massageExpectectedResultsForDialect(new String[] { origId,
                null, "changed value", "char2", "char not null2", "2007-01-02 03:20:10.000",
                "2012-03-12 07:00:00.000", "0", "2", "67.89", "-0.0747663" });
        CsvData data = new CsvData(DataEventType.INSERT, originalValues);
        writeData(data, originalValues);
        String[] oldData = CollectionUtils.copyOfRange(originalValues, 0, originalValues.length);
        oldData[2] = "original value";
        oldData = massageExpectectedResultsForDialect(oldData);
        String[] newData = CollectionUtils.copyOfRange(originalValues, 0, originalValues.length);
        newData[2] = "new value";
        newData = massageExpectectedResultsForDialect(newData);
        CsvData update = new CsvData(DataEventType.UPDATE);
        update.putParsedData(CsvData.ROW_DATA, newData);
        update.putParsedData(CsvData.OLD_DATA, oldData);
        try {
            writeData(update);
            Assert.fail("Should have received a conflict exception");
        } catch (ConflictException ex) {
            Statistics stats = lastDataWriterUsed.getStatistics().values().iterator().next();
            long statementNumber = stats.get(DataWriterStatisticConstants.ROWCOUNT);
            ResolvedData resolvedData = new ResolvedData(statementNumber,
                    update.getCsvData(CsvData.ROW_DATA), false);
            writerSettings.setResolvedData(resolvedData);
            writeData(update);
            Map<String, Object> row = queryForRow(origId);
            Assert.assertNotNull(row);
            Assert.assertEquals(newData[2], row.get("string_required_value"));
        }
    }

    @Test
    public void testUpdateDetectChangedDataIgnoreRow() {
    }

    @Test
    public void testUpdateDetectChangedDataIgnoreBatch() {
    }

    @Test
    public void testUpdateDetectChangedDataFallbackAll() {
    }

    @Test
    public void testDeleteDetectTimestampIgnoreRow() {
    }

    @Test
    public void testDeleteDetectTimestampIgnoreBatch() {
    }

    @Test
    public void testDeleteDetectTimestampNewerWins() {
    }

    @Test
    public void testInsertDetectTimestampManual() {
    }

    @Test
    public void testUpdateDetectChangedDataManual() {
    }

    @Test
    public void testInsertExisting() throws Exception {
        String[] values = { getNextId(), "string2", "string not null2", "char2", "char not null2",
                "2007-01-02 03:20:10.000", "2007-02-03 04:05:06.000", "0", "47", "67.89", "-0.0747663" };
        massageExpectectedResultsForDialect(values);
        CsvData data = new CsvData(DataEventType.INSERT, values);
        writeData(data, values);
        values[1] = "insert fallback to update";
        massageExpectectedResultsForDialect(values);
        writeData(data, values);
    }

    @Test
    public void testLargeDouble() throws Exception {
        String[] values = new String[TEST_COLUMNS.length];
        values[0] = getNextId();
        values[10] = "-0.0747663551401869";
        String[] expectedValues = ArrayUtils.clone(values);
        massageExpectectedResultsForDialect(expectedValues);
        if (platform.getDatabaseInfo().isRequiredCharColumnEmptyStringSameAsNull()) {
            expectedValues[4] = AbstractDatabasePlatform.REQUIRED_FIELD_NULL_SUBSTITUTE;
        }
        writeData(new CsvData(DataEventType.INSERT, values), expectedValues);
    }

    @Test
    public void testDecimalLocale() throws Exception {
        String[] values = new String[TEST_COLUMNS.length];
        values[0] = getNextId();
        values[10] = "123456,99";
        String[] expectedValues = ArrayUtils.clone(values);
        massageExpectectedResultsForDialect(expectedValues);
        if (platform.getDatabaseInfo().isRequiredCharColumnEmptyStringSameAsNull()) {
            expectedValues[4] = AbstractDatabasePlatform.REQUIRED_FIELD_NULL_SUBSTITUTE;
        }
        writeData(new CsvData(DataEventType.INSERT, values), expectedValues);
    }

    @Test
    public void testUpdateNotExisting() throws Exception {
        String id = getNextId();
        String[] values = { id, "it's /a/  string", "it's  -not-  null", "You're a \"character\"",
                "Where are you?", "2007-12-31 02:33:45.000", "2007-12-31 23:59:59.000", "1", "13",
                "9.95", "-0.0747" };
        String[] expectedValues = ArrayUtils.subarray(values, 0, values.length);
        massageExpectectedResultsForDialect(expectedValues);
        writeData(new CsvData(DataEventType.UPDATE, new String[] { id }, values), expectedValues);
    }

    @Test
    public void testStringQuotes() throws Exception {
        String[] values = new String[TEST_COLUMNS.length];
        values[0] = getNextId();
        values[1] = "It's \"quoted,\" with a comma";
        values[2] = "two 'ticks'";
        values[3] = "One quote\"";
        values[4] = "One comma,";
        writeData(new CsvData(DataEventType.INSERT, values), values);
    }

    @Test
    public void testStringSpaces() throws Exception {
        String[] values = new String[TEST_COLUMNS.length];
        values[0] = getNextId();
        values[1] = "  two spaces before";
        if (!(platform instanceof AseDatabasePlatform)) {
            values[2] = "two spaces after  ";
        }
        values[3] = " one space before";
        values[4] = "one space after ";
        writeData(new CsvData(DataEventType.INSERT, values), values);
    }

    @Test
    public void testStringOneSpace() throws Exception {
        String[] values = new String[TEST_COLUMNS.length];
        values[0] = getNextId();
        values[2] = values[4] = " ";
        writeData(new CsvData(DataEventType.INSERT, values), values);
    }

    @Test
    public void testStringEmpty() throws Exception {
        String[] values = new String[TEST_COLUMNS.length];
        values[0] = getNextId();
        if (!(platform instanceof AseDatabasePlatform)) {
            values[1] = values[2] = "";
        }
        values[3] = values[4] = "";
        String[] expectedValues = ArrayUtils.clone(values);
        if (platform.getDatabaseInfo().isRequiredCharColumnEmptyStringSameAsNull()) {
            expectedValues[4] = null;
        }
        writeData(new CsvData(DataEventType.INSERT, values), expectedValues);
    }

    @Test
    public void testStringNull() throws Exception {
        String[] values = new String[TEST_COLUMNS.length];
        values[0] = getNextId();
        String[] expectedValues = ArrayUtils.clone(values);
        if (platform.getDatabaseInfo().isRequiredCharColumnEmptyStringSameAsNull()) {
            expectedValues[4] = AbstractDatabasePlatform.REQUIRED_FIELD_NULL_SUBSTITUTE;
        }
        writeData(new CsvData(DataEventType.INSERT, values), expectedValues);
    }

    @Test
    public void testStringBackslash() throws Exception {
        String[] values = new String[TEST_COLUMNS.length];
        values[0] = getNextId();
        values[1] = "Here's a \\, a (backslash)";
        values[2] = "Fix TODO";
        // TODO: Fix backslashing alphanumeric
        // values[2] = "\\a\\b\\c\\ \\1\\2\\3";
        values[3] = "Tick quote \\'\\\"";
        values[4] = "Comma quote \\,\\\"";
        writeData(new CsvData(DataEventType.INSERT, values), values);
    }

    @Test
    public void testDeleteExisting() throws Exception {
        String[] values = { getNextId(), "a row to be deleted", "testDeleteExisting", "char2",
                "char not null2", "2007-01-02 03:20:10.000", "2007-02-03 04:05:06.000", "0", "47",
                "67.89", "-0.0747" };
        massageExpectectedResultsForDialect(values);
        writeData(new CsvData(DataEventType.INSERT, values), values);
        writeData(new CsvData(DataEventType.DELETE, new String[] { getId() }, null), null);
    }

    @Test
    public void testDeleteNotExisting() throws Exception {
        writeData(new CsvData(DataEventType.DELETE, new String[] { getNextId() }, null), null);
    }

    @Test
    public void testColumnNotExisting() throws Exception {
        List<String> testColumns = new ArrayList<String>(Arrays.asList(TEST_COLUMNS));
        testColumns.add(4, "Unknown_Column");
        String[] columns = testColumns.toArray(new String[testColumns.size()]);
        String[] values = { getNextId(), "testColumnNotExisting", "string not null", "char",
                "i do not exist!", "char not null", "2007-01-02 00:00:00.000",
                "2007-02-03 04:05:06.000", "0", "47", "67.89", "-0.0747" };
        massageExpectectedResultsForDialect2(values);
        List<String> valuesAsList = new ArrayList<String>(Arrays.asList(values));
        valuesAsList.remove(4);
        String[] expectedValues = valuesAsList.toArray(new String[valuesAsList.size()]);
        writeData(new CsvData(DataEventType.INSERT, values), expectedValues, columns);
    }

    @Test
    public void testTableNotExisting() throws Exception {
        String[] values = { getNextId(), "testTableNotExisting", "This row should load", "char",
                "char not null", "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "0", "0",
                "12.10", "-0.0747" };
        Table badTable = buildSourceTable("UnknownTable", TEST_KEYS, TEST_COLUMNS);
        writeData(new TableCsvData(badTable, new CsvData(DataEventType.INSERT, values)),
                new TableCsvData(buildSourceTable(TEST_TABLE, TEST_KEYS, TEST_COLUMNS),
                        new CsvData(DataEventType.INSERT, values)));
        massageExpectectedResultsForDialect(values);
        assertTestTableEquals(values[0], values);
    }

    @Test
    public void testColumnLevelSync() throws Exception {
        String[] insertValues = new String[TEST_COLUMNS.length];
        insertValues[2] = insertValues[4] = "column sync";
        insertValues[0] = getNextId();
        String[] updateValues = new String[2];
        updateValues[0] = insertValues[0];
        updateValues[1] = "new value";
        writeData(new CsvData(DataEventType.INSERT, insertValues), insertValues);
        // update a single column
        String[] columns = { "id", "string_value" };
        insertValues[1] = updateValues[1];
        writeData(new CsvData(DataEventType.UPDATE, new String[] { getId() }, updateValues),
                insertValues, columns);
        // update a single column
        columns = new String[] { "id", "char_value" };
        insertValues[3] = updateValues[1];
        writeData(new CsvData(DataEventType.UPDATE, new String[] { getId() }, updateValues),
                insertValues, columns);
    }

    @Test
    public void testBinaryColumnTypesForPostgres() throws Exception {
        if (platform instanceof PostgreSqlDatabasePlatform) {
            platform.getSqlTemplate().update("drop table if exists test_postgres_binary_types");
            platform.getSqlTemplate().update(
                    "create table test_postgres_binary_types (binary_data oid)");
            String tableName = "test_postgres_binary_types";
            String[] keys = { "binary_data" };
            String[] columns = { "binary_data" };
            String[] values = { "dGVzdCAxIDIgMw==" };
            Table table = buildSourceTable(tableName, keys, columns);
            writeData(new TableCsvData(table, new CsvData(DataEventType.INSERT, values)));
            String result = platform
                    .getSqlTemplate()
                    .queryForObject(
                            "select encode(data,'escape') from pg_largeobject where loid in (select binary_data from test_postgres_binary_types)",
                            String.class);
            // clean up the object from pg_largeobject, otherwise it becomes
            // abandoned on subsequent runs
            platform.getSqlTemplate().query(
                    "select lo_unlink(binary_data) from test_postgres_binary_types");
            Assert.assertEquals("test 1 2 3", result);
        }
    }

    @Test
    public void testBenchmark() throws Exception {
        Table table = buildSourceTable(TEST_TABLE, TEST_KEYS, TEST_COLUMNS);
        int startId = Integer.parseInt(getId()) + 1;
        List<CsvData> datas = new ArrayList<CsvData>();
        for (int i = 0; i < 1600; i++) {
            String[] values = { getNextId(), UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), "2007-01-02 03:20:10.0", "2007-02-03 04:05:06.0",
                    "0", "47", "67.89", "-0.0747663" };
            datas.add(new CsvData(DataEventType.INSERT, values));
        }
        for (int i = startId; i < 1600 + startId; i++) {
            String[] values = { Integer.toString(i) };
            datas.add(new CsvData(DataEventType.DELETE, values, null));
        }
        long startTime = System.currentTimeMillis();
        long statementCount = writeData(new TableCsvData(table, datas));
        double totalSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        double targetTime = 15.0;
        if (platform instanceof InformixDatabasePlatform) {
            targetTime = 20.0;
        }
        Assert.assertEquals(3200, statementCount);
        // TODO: this used to run in 1 second; can we do some optimization?
        Assert.assertTrue("DataLoader running in " + totalSeconds + " is too slow",
                totalSeconds <= targetTime);
    }

    private String[] massageExpectectedResultsForDialect(String[] values) {
        RoundingMode mode = RoundingMode.DOWN;
        if (values[5] != null && (platform.getName().startsWith(DatabaseNamesConstants.MSSQL2008) || platform.getName().startsWith(
                DatabaseNamesConstants.MSSQL2016))) {
            // No time portion for a date field
            values[5] = values[5].replaceFirst(" \\d\\d:\\d\\d:\\d\\d\\.000", "");
        } else if (values[5] != null
                && (!(platform instanceof AseDatabasePlatform
                        || platform instanceof SqlAnywhereDatabasePlatform))) {
            values[5] = values[5].replaceFirst(" \\d\\d:\\d\\d:\\d\\d.*", "");
        } else if (values[5] != null && values[5].length() == 23) {
            values[5] = values[5] + "0000";
        }
        if (values[6] != null && values[6].length() == 23) {
            values[6] = values[6] + "0000";
        }
        if (values[10] != null) {
            values[10] = values[10].replace(',', '.');
            int scale = 17;
            if (platform instanceof MySqlDatabasePlatform) {
                scale = 16;
            }
            DecimalFormat df = new DecimalFormat("0.00####################################");
            values[10] = df.format(new BigDecimal(values[10]).setScale(scale, mode));
        }
        // Adjust character fields that may have been adjusted from null to a default space with appropriate padding
        values[3] = translateExpectedCharString(values[3], 50, false);
        values[4] = translateExpectedCharString(values[4], 50, true);
        return values;
    }

    private String[] massageExpectectedResultsForDialect2(String[] values) {
        if (values[6] != null
                && (!(platform instanceof AseDatabasePlatform
                        || platform instanceof SqlAnywhereDatabasePlatform))) {
            values[6] = values[6].replaceFirst(" \\d\\d:\\d\\d:\\d\\d.*", "");
        }
        if (values[7] != null && values[7].length() == 23) {
            values[7] = values[7] + "0000";
        }
        return values;
    }
}
