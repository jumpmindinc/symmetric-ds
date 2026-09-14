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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsvDataTest {
    private static final String[] ROW_VALUES = new String[] { "1", "widget" };
    private static final String[] OLD_VALUES = new String[] { "1", "gadget" };
    private CsvData data;

    @BeforeEach
    void setUp() {
        data = new CsvData(DataEventType.UPDATE);
    }

    @Test
    void testConstructor_withRowData() {
        CsvData rowOnly = new CsvData(DataEventType.INSERT, ROW_VALUES);
        assertEquals(DataEventType.INSERT, rowOnly.getDataEventType());
        assertArrayEquals(ROW_VALUES, rowOnly.getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testConstructor_withPkAndRowData() {
        CsvData withPk = new CsvData(DataEventType.UPDATE, new String[] { "1" }, ROW_VALUES);
        assertArrayEquals(new String[] { "1" }, withPk.getParsedData(CsvData.PK_DATA));
        assertArrayEquals(ROW_VALUES, withPk.getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testConstructor_withRowOldAndResolveData() {
        String[] resolveValues = new String[] { "1", "resolved" };
        CsvData withResolve = new CsvData(DataEventType.UPDATE, ROW_VALUES, OLD_VALUES, resolveValues);
        assertArrayEquals(ROW_VALUES, withResolve.getParsedData(CsvData.ROW_DATA));
        assertArrayEquals(OLD_VALUES, withResolve.getParsedData(CsvData.OLD_DATA));
        assertArrayEquals(resolveValues, withResolve.getParsedData(CsvData.RESOLVE_DATA));
    }

    @Test
    void testConstructor_withNoArguments() {
        assertNull(new CsvData().getDataEventType());
    }

    @Test
    void testSetDataEventType() {
        data.setDataEventType(DataEventType.DELETE);
        assertEquals(DataEventType.DELETE, data.getDataEventType());
    }

    @Test
    void testContains_withParsedData() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        assertTrue(data.contains(CsvData.ROW_DATA));
    }

    @Test
    void testContains_withCsvData() {
        data.putCsvData(CsvData.ROW_DATA, "\"1\",\"widget\"");
        assertTrue(data.contains(CsvData.ROW_DATA));
    }

    @Test
    void testContains_whenKeyIsAbsent() {
        assertFalse(data.contains(CsvData.ROW_DATA));
    }

    @Test
    void testPutAttribute() {
        data.putAttribute(CsvData.ATTRIBUTE_TABLE_NAME, "item");
        assertEquals("item", data.getAttribute(CsvData.ATTRIBUTE_TABLE_NAME));
    }

    @Test
    void testGetAttribute_whenNoAttributesWereAdded() {
        assertNull(data.getAttribute(CsvData.ATTRIBUTE_TABLE_NAME));
    }

    @Test
    void testSetAttributes() {
        Map<String, Object> attributes = Map.of(CsvData.ATTRIBUTE_TABLE_ID, 7);
        data.setAttributes(attributes);
        assertSame(attributes, data.getAttributes());
    }

    @Test
    void testGetCsvData_fromParsedData() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        assertEquals("\"1\",\"widget\"", data.getCsvData(CsvData.ROW_DATA));
    }

    @Test
    void testGetCsvData_whenKeyIsAbsent() {
        assertNull(data.getCsvData(CsvData.ROW_DATA));
    }

    @Test
    void testGetParsedData_fromCsvData() {
        data.putCsvData(CsvData.ROW_DATA, "\"1\",\"widget\"");
        assertArrayEquals(ROW_VALUES, data.getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testGetParsedData_whenKeyIsAbsent() {
        assertNull(data.getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testRemoveAllData() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        data.removeAllData(CsvData.ROW_DATA);
        assertFalse(data.contains(CsvData.ROW_DATA));
    }

    @Test
    void testRemoveCsvData() {
        data.putCsvData(CsvData.ROW_DATA, "\"1\",\"widget\"");
        data.removeCsvData(CsvData.ROW_DATA);
        assertFalse(data.contains(CsvData.ROW_DATA));
    }

    @Test
    void testGetChangedDataIndicators_withOldData() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        data.putParsedData(CsvData.OLD_DATA, OLD_VALUES);
        assertArrayEquals(new boolean[] { false, true }, data.getChangedDataIndicators());
    }

    @Test
    void testGetChangedDataIndicators_withoutOldData() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        assertArrayEquals(new boolean[] { true, true }, data.getChangedDataIndicators());
    }

    @Test
    void testGetChangedDataIndicators_whenNewValueIsNull() {
        data.putParsedData(CsvData.ROW_DATA, new String[] { "1", null });
        data.putParsedData(CsvData.OLD_DATA, OLD_VALUES);
        assertArrayEquals(new boolean[] { false, true }, data.getChangedDataIndicators());
    }

    @Test
    void testGetChangedDataIndicators_whenOldValueIsNull() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        data.putParsedData(CsvData.OLD_DATA, new String[] { "1", null });
        assertArrayEquals(new boolean[] { false, true }, data.getChangedDataIndicators());
    }

    @Test
    void testGetChangedDataIndicators_whenBothValuesAreNull() {
        data.putParsedData(CsvData.ROW_DATA, new String[] { "1", null });
        data.putParsedData(CsvData.OLD_DATA, new String[] { "1", null });
        assertArrayEquals(new boolean[] { false, false }, data.getChangedDataIndicators());
    }

    @Test
    void testGetChangedDataIndicators_whenOldDataIsShorter() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        data.putParsedData(CsvData.OLD_DATA, new String[] { "1" });
        assertArrayEquals(new boolean[] { false, true }, data.getChangedDataIndicators());
    }

    @Test
    void testGetChangedDataIndicators_isCachedUntilDataChanges() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        boolean[] indicators = data.getChangedDataIndicators();
        assertSame(indicators, data.getChangedDataIndicators());
        data.putParsedData(CsvData.ROW_DATA, OLD_VALUES);
        assertNotSame(indicators, data.getChangedDataIndicators());
    }

    @Test
    void testRequiresTable() {
        assertTrue(new CsvData(DataEventType.INSERT).requiresTable());
    }

    @Test
    void testRequiresTable_withCreate() {
        assertFalse(new CsvData(DataEventType.CREATE).requiresTable());
    }

    @Test
    void testRequiresTable_withBsh() {
        assertFalse(new CsvData(DataEventType.BSH).requiresTable());
    }

    @Test
    void testRequiresTable_whenEventTypeIsNull() {
        assertFalse(new CsvData().requiresTable());
    }

    @Test
    void testSetNoBinaryOldData() {
        assertFalse(data.isNoBinaryOldData());
        data.setNoBinaryOldData(true);
        assertTrue(data.isNoBinaryOldData());
    }

    @Test
    void testCopyWithoutOldData() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        data.putParsedData(CsvData.OLD_DATA, OLD_VALUES);
        data.putAttribute(CsvData.ATTRIBUTE_TABLE_NAME, "item");
        CsvData copy = data.copyWithoutOldData();
        assertEquals(DataEventType.UPDATE, copy.getDataEventType());
        assertArrayEquals(ROW_VALUES, copy.getParsedData(CsvData.ROW_DATA));
        assertFalse(copy.contains(CsvData.OLD_DATA));
        assertEquals("item", copy.getAttribute(CsvData.ATTRIBUTE_TABLE_NAME));
    }

    @Test
    void testToColumnNameValuePairs() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        Map<String, String> pairs = data.toColumnNameValuePairs(new String[] { "id", "name" }, CsvData.ROW_DATA);
        assertEquals("1", pairs.get("id"));
        assertEquals("widget", pairs.get("name"));
    }

    @Test
    void testToColumnNameValuePairs_whenFewerValuesThanNames() {
        data.putParsedData(CsvData.ROW_DATA, new String[] { "1" });
        assertTrue(data.toColumnNameValuePairs(new String[] { "id", "name" }, CsvData.ROW_DATA).isEmpty());
    }

    @Test
    void testToColumnNameValuePairs_whenKeyIsAbsent() {
        assertTrue(data.toColumnNameValuePairs(new String[] { "id" }, CsvData.ROW_DATA).isEmpty());
    }

    @Test
    void testToKeyColumnValuePairs_withPkData() {
        data.putParsedData(CsvData.PK_DATA, new String[] { "1" });
        Map<String, String> pairs = data.toKeyColumnValuePairs(newTable());
        assertEquals(1, pairs.size());
        assertEquals("1", pairs.get("id"));
    }

    @Test
    void testToKeyColumnValuePairs_withRowDataOnly() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        Map<String, String> pairs = data.toKeyColumnValuePairs(newTable());
        assertEquals(1, pairs.size());
        assertEquals("1", pairs.get("id"));
    }

    @Test
    void testGetPkData() {
        data.putParsedData(CsvData.PK_DATA, new String[] { "1" });
        assertArrayEquals(new String[] { "1" }, data.getPkData(newTable()));
    }

    @Test
    void testWriteCsvDataDetails() {
        data.putCsvData(CsvData.PK_DATA, "\"1\"");
        data.putCsvData(CsvData.ROW_DATA, "\"1\",\"widget\"");
        data.putCsvData(CsvData.OLD_DATA, "\"1\",\"gadget\"");
        StringBuilder message = new StringBuilder();
        data.writeCsvDataDetails(message);
        assertEquals("Failed pk data was: \"1\"\nFailed row data was: \"1\",\"widget\"\nFailed old data was: \"1\",\"gadget\"\n", message.toString());
    }

    @Test
    void testWriteCsvDataDetails_whenNoDataIsPresent() {
        StringBuilder message = new StringBuilder();
        data.writeCsvDataDetails(message);
        assertEquals(0, message.length());
    }

    @Test
    void testWriteCsvDataDetails_whenRowDataIsTooLargeToPrint() {
        data.putCsvData(CsvData.ROW_DATA, "a".repeat(CsvData.MAX_DATA_SIZE_TO_PRINT_TO_LOG));
        StringBuilder message = new StringBuilder();
        data.writeCsvDataDetails(message);
        assertTrue(message.toString().startsWith("Row data was bigger than 32768 bytes (it was 32768 bytes)."));
    }

    @Test
    void testWriteCsvDataDetails_whenOldDataIsTooLargeToPrint() {
        data.putCsvData(CsvData.OLD_DATA, "a".repeat(CsvData.MAX_DATA_SIZE_TO_PRINT_TO_LOG));
        StringBuilder message = new StringBuilder();
        data.writeCsvDataDetails(message);
        assertTrue(message.toString().startsWith("Old data was bigger than 32768 bytes (it was 32768 bytes)."));
    }

    @Test
    void testGetSizeInBytes() {
        data.putCsvData(CsvData.ROW_DATA, "widget");
        data.putCsvData(CsvData.OLD_DATA, "gadget");
        assertEquals("widget".getBytes(Charset.defaultCharset()).length * 2L, data.getSizeInBytes());
    }

    @Test
    void testGetSizeInBytes_whenNoCsvDataIsPresent() {
        data.putParsedData(CsvData.ROW_DATA, ROW_VALUES);
        assertEquals(0L, data.getSizeInBytes());
    }

    private Table newTable() {
        return new Table("item", new Column("id", true), new Column("name"));
    }
}
