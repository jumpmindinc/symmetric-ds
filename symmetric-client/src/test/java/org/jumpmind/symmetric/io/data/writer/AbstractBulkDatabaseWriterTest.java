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

import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.ase.AseDatabasePlatform;
import org.jumpmind.db.platform.mysql.MySqlDatabasePlatform;
import org.jumpmind.db.platform.oracle.OracleDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlDatabasePlatform;
import org.jumpmind.db.platform.sqlanywhere.SqlAnywhereDatabasePlatform;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.io.AbstractWriterTest;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.junit.Assert;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
public abstract class AbstractBulkDatabaseWriterTest extends AbstractWriterTest {
    protected final static String[] TEST_COLUMNS = { "id", "string_value", "string_required_value", "char_value",
            "char_required_value", "date_value", "time_value", "boolean_value", "integer_value", "decimal_value", "double_value",
            "img_value" };

    @Override
    protected String getTestTable() {
        return "test_bulkload_table_2";
    }

    protected abstract boolean shouldTestRun(IDatabasePlatform platform);

    protected String encodeBase64(String str) {
        return new String(Base64.encodeBase64(str.getBytes(Charset.defaultCharset())), Charset.defaultCharset());
    }

    protected String encodeBase64(int[] bytes) {
        ByteBuffer bb = ByteBuffer.allocate(bytes.length);
        for (int b : bytes) {
            bb.put((byte) b);
        }
        return new String(Base64.encodeBase64(bb.array()), Charset.defaultCharset());
    }

    protected String encodeHex(int[] bytes) {
        ByteBuffer bb = ByteBuffer.allocate(bytes.length);
        for (int b : bytes) {
            bb.put((byte) b);
        }
        return new String(Hex.encodeHex(bb.array()));
    }

    protected void insertAndVerify(String[] values, BinaryEncoding encoding) {
        List<CsvData> data = new ArrayList<CsvData>();
        data.add(new CsvData(DataEventType.INSERT, ArrayUtils.clone(values)));
        writeData(encoding, data);
        assertTestTableEquals(values[0], encoding, massageExpectectedResultsForDialect(values));
    }

    protected void insertEncodeAndVerify(String[] values, int elementToEncode, int[] bytes) {
        values[elementToEncode] = new String(encodeHex(bytes));
        insertAndVerify(values, BinaryEncoding.HEX);
        values[0] = getNextId();
        values[elementToEncode] = new String(encodeBase64(bytes));
        insertAndVerify(values, BinaryEncoding.BASE64);
    }

    protected void insertEncodeAndVerify(String[] values, int elementToEncode) {
        if (values[11] != null) {
            values[elementToEncode] = new String(Hex.encodeHex(values[elementToEncode].getBytes(Charset.defaultCharset())));
            insertAndVerify(values, BinaryEncoding.HEX);
            values[0] = getNextId();
            values[elementToEncode] = new String(encodeBase64(values[elementToEncode]));
        }
        insertAndVerify(values, BinaryEncoding.BASE64);
    }

    protected abstract long writeData(List<CsvData> data);

    protected abstract long writeData(BinaryEncoding encoding, List<CsvData> data);

    @Test
    public void testInsert() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), "string with space in it", "string-with-no-space", "string with space in it",
                    "string-with-no-space", "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "0", "47", "67.89", "-0.0747663",
                    "string with space in it" };
            insertEncodeAndVerify(values, 11);
        }
    }

    @Test
    public void testInsertAcrossMaxFlush() {
        if (shouldTestRun(platform)) {
            platform.getSqlTemplate().update("truncate table " + getTestTable());
            List<CsvData> data = new ArrayList<CsvData>();
            for (int i = 0; i < 30; i++) {
                String[] values = { getNextId(), "stri'ng2", "string not null2", "char2", "char not null2",
                        "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "0", "47", "67.89", "-0.0747663", "string" };
                data.add(new CsvData(DataEventType.INSERT, values));
            }
            Assert.assertEquals(writeData(data), countRows(getTestTable()));
        }
    }

    @Test
    public void testInsertWithNull() {
        if (shouldTestRun(platform)) {
            for (int index : new int[] { 1, 3, 5, 6, 7, 8, 9, 10, 11 }) {
                String[] values = { "", "stri'ng2", "string not null2", "char2", "char not null2", "2007-01-02 00:00:00.000",
                        "2007-02-03 04:05:06.000", "0", "47", "67.89", "-0.0747663", "string" };
                values[0] = getNextId();
                values[index] = null;
                insertEncodeAndVerify(values, 11);
            }
        }
    }

    @Test
    public void testInsertWithBackslash() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), "back\\slash", "double\\\\back\\slash", "back\\slash", "double\\\\back\\slash",
                    "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "1", "47", "67.89", "-0.0747663", "back\\slash" };
            insertEncodeAndVerify(values, 11);
        }
    }

    @Test
    public void testInsertWithQuotes() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), "single'qoute", "double''single'quote", "single'quote", "double''single'quote",
                    "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "1", "47", "67.89", "-0.0747663", "single'qoute" };
            insertEncodeAndVerify(values, 11);
            String[] values2 = { getNextId(), "single\"qoute", "double\"\"single\"quote", "single\"quote",
                    "double\"\"single\"quote", "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "1", "47", "67.89",
                    "-0.0747663", "single\"quote" };
            insertEncodeAndVerify(values2, 11);
        }
    }

    @Test
    public void testInsertWithCommas() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), "single,comma", "double,,comma,comma", "single,comma", "double,,comma,comma",
                    "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "1", "47", "67.89", "-0.0747663", "single,comma" };
            insertEncodeAndVerify(values, 11);
        }
    }

    @Test
    public void testInsertWithNonEscaped() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), null, "\n\0\r\t\f\'\"", null, "\n\0\r\t\f\'\"",
                    "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "1", "47", "67.89", "-0.0747663", "\n\0\r\t\f\'\"" };
            insertEncodeAndVerify(values, 11);
        }
    }

    @Test
    public void testInsertWithSpecialEscape() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), "\\n\\N\\0\\r\\t\\b\\f\\", "\\n\\N\\0\\r\\t\\b\\f\\", "\\x31\\x32\\x33", "\\061\\062\\063",
                    "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "1", "47", "67.89", "-0.0747663",
                    "\\n\\N\\0\\r\\t\\\n\r\t\b\f\0\\x31\\x32\\x33" };
            insertEncodeAndVerify(values, 11);
        }
    }

    @Test
    public void testInsertWithUnicode() {
        if (shouldTestRun(platform)) {
            String unicode = "\u007E\u00A7\u2702\u28FF\uFFE6\uFFFC\uFFFF";
            String[] values = { getNextId(), null, "", null, "", "2007-01-02 00:00:00.000",
                    "2007-02-03 04:05:06.000", "1", "47", "67.89", "-0.0747663", unicode };
            insertEncodeAndVerify(values, 11);
        }
    }

    @Test
    public void testInsertBlobInvalidUnicode() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), null, "x", null, "x", null, null, null, null, null, null, null };
            int[] bytes = new int[] { 0x10, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x14, 0x00, 0x00, 0xF5 };
            insertEncodeAndVerify(values, 11, bytes);
        }
    }

    @Test
    public void testInsertBlobRange() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), null, "x", null, "x", null, null, null, null, null, null, null };
            int[] bytes = new int[255 * 255];
            for (int i = 0; i <= 255; i++) {
                bytes[i] = i;
                for (int j = 0; j <= 255; j++) {
                    bytes[i + j + 1] = j;
                }
            }
            insertEncodeAndVerify(values, 11, bytes);
        }
    }

    @Test
    public void testInsertBlobRandom() {
        if (shouldTestRun(platform)) {
            String[] values = { getNextId(), null, "x", null, "x", null, null, null, null, null, null, null };
            int[] bytes = new int[8192];
            SecureRandom randomGenerator = new SecureRandom();
            for (int i = 0; i < bytes.length; ++i) {
                bytes[i] = randomGenerator.nextInt(256);
            }
            insertEncodeAndVerify(values, 11, bytes);
        }
    }

    @Test
    public void testDuplicateRow() {
        if (shouldTestRun(platform)) {
            platform.getSqlTemplate().update("truncate table " + getTestTable());
            List<CsvData> data = new ArrayList<CsvData>();
            String id = getNextId();
            String[] values1 = { id, "stri'ng2", "string not null2", "char2", "char not null2",
                    "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "0", "47", "67.89", "-0.0747663", encodeBase64("string") };
            data.add(new CsvData(DataEventType.INSERT, values1));
            String[] values2 = { id, "stri'ng2", "string not null2", "char2", "char not null2",
                    "2007-01-02 00:00:00.000", "2007-02-03 04:05:06.000", "0", "47", "67.89", "-0.0747663", encodeBase64("string") };
            data.add(new CsvData(DataEventType.INSERT, values2));
            Table table = platform.getTableFromCache(getTestTable(), false);
            AbstractDatabaseWriter bulkWriter = create();
            DataContext context = new DataContext();
            try {
                /* first try should have failed */
                writeData(bulkWriter, context, new TableCsvData(table, data));
                fail("The bulk writer should have failed");
            } catch (Exception ex) {
            }
            /* Recreate the writer because in the real world that is what would happen */
            bulkWriter = create();
            context = new DataContext();
            IncomingBatch expectedBatch = new IncomingBatch();
            expectedBatch.setErrorFlag(true);
            context.put("currentBatch", expectedBatch);
            // Simulate DataLoaderService when bulk load fails. Fall back to default.
            context.put(ContextConstants.CONTEXT_BULK_WRITER_TO_USE, "default");
            /* second try should be success because the bulk writer should fail back to using the default writer */
            long statementCount = writeData(bulkWriter, context, new TableCsvData(table, data));
            Assert.assertEquals(2, statementCount);
            Assert.assertEquals(1, countRows(getTestTable()));
        }
    }

    protected abstract AbstractDatabaseWriter create();

    @Override
    protected void assertTestTableEquals(String testTableId, String[] expectedValues) {
        assertTestTableEquals(testTableId, BinaryEncoding.BASE64, expectedValues);
    }

    protected void assertTestTableEquals(String testTableId, BinaryEncoding encoding, String[] expectedValues) {
        String sql = "select " + getSelect(TEST_COLUMNS) + " from " + getTestTable() + " where " + getWhere(TEST_KEYS);
        Map<String, Object> results = platform.getSqlTemplate().queryForMap(sql, Long.valueOf(testTableId));
        if (expectedValues != null) {
            expectedValues[1] = translateExpectedString(expectedValues[1], false);
            expectedValues[2] = translateExpectedString(expectedValues[2], true);
            expectedValues[3] = translateExpectedCharString(expectedValues[3], 50, false);
            expectedValues[4] = translateExpectedCharString(expectedValues[4], 50, true);
            if (expectedValues[11] != null) {
                if (encoding == BinaryEncoding.HEX) {
                    expectedValues[11] = new String(expectedValues[11]);
                    results.put(TEST_COLUMNS[11], new String(Hex.encodeHex((byte[]) results.get(TEST_COLUMNS[11]))));
                } else {
                    expectedValues[11] = new String(Hex.encodeHex(Base64.decodeBase64(expectedValues[11].getBytes(Charset.defaultCharset()))));
                    results.put(TEST_COLUMNS[11], new String(Hex.encodeHex((byte[]) results.get(TEST_COLUMNS[11]))));
                }
            }
        }
        assertEquals(TEST_COLUMNS, expectedValues, results);
    }

    protected String[] massageExpectectedResultsForDialect(String[] values) {
        if (values[5] != null && (!(platform instanceof OracleDatabasePlatform
                || ((platform.getName().startsWith(DatabaseNamesConstants.MSSQL2000) || platform.getName().startsWith(DatabaseNamesConstants.MSSQL2005))
                        && !(platform.getName().startsWith(DatabaseNamesConstants.MSSQL2008) || platform.getName().startsWith(
                                DatabaseNamesConstants.MSSQL2016)))
                || platform instanceof AseDatabasePlatform || platform instanceof SqlAnywhereDatabasePlatform))) {
            values[5] = values[5].replaceFirst(" \\d\\d:\\d\\d:\\d\\d.*", "");
        }
        if (values[6] != null && (platform.getName().startsWith(DatabaseNamesConstants.MSSQL2008) || platform.getName().startsWith(
                DatabaseNamesConstants.MSSQL2016)
                || platform instanceof MySqlDatabasePlatform || platform instanceof PostgreSqlDatabasePlatform)) {
            if (values[6].length() == 23) {
                values[6] = values[6] + "0000";
            }
        }
        return values;
    }
}