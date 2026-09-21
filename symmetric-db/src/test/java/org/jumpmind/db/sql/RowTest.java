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
package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import org.apache.commons.codec.binary.Hex;
import org.jumpmind.exception.IoException;
import org.junit.jupiter.api.Test;

class RowTest {
    @Test
    void testConstructor_withNumberOfColumnsCreatesEmptyRow() {
        Row row = new Row(3);
        assertTrue(row.isEmpty());
    }

    @Test
    void testConstructor_withColumnNameAndValue() {
        Row row = new Row("col", "value");
        assertEquals("value", row.get("col"));
    }

    @Test
    void testConstructor_withNamesAndValuesArrays() {
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { "a", "b" });
        assertEquals("a", row.get("col1"));
        assertEquals("b", row.get("col2"));
    }

    @Test
    void testConstructor_withNamesAndValuesArrays_stopsAtShorterArray() {
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { "a" });
        assertEquals("a", row.get("col1"));
        assertFalse(row.containsKey("col2"));
    }

    @Test
    void testBytesValue_withByteArray() {
        byte[] bytes = { 1, 2, 3 };
        Row row = new Row("col", bytes);
        assertArrayEquals(bytes, row.bytesValue());
    }

    @Test
    void testBytesValue_withBlob() throws SQLException {
        Blob blobMock = mock(Blob.class);
        when(blobMock.getBinaryStream()).thenReturn(new ByteArrayInputStream(new byte[] { 4, 5, 6 }));
        Row row = new Row("col", blobMock);
        assertArrayEquals(new byte[] { 4, 5, 6 }, row.bytesValue());
    }

    @Test
    void testBytesValue_withBlobThatThrowsSQLException() throws SQLException {
        Blob blobMock = mock(Blob.class);
        when(blobMock.getBinaryStream()).thenThrow(new SQLException("boom"));
        Row row = new Row("col", blobMock);
        assertThrows(SqlException.class, row::bytesValue);
    }

    @Test
    void testBytesValue_withBlobStreamThatThrowsIOException() throws IOException, SQLException {
        Blob blobMock = mock(Blob.class);
        InputStream inputStreamMock = mock(InputStream.class);
        when(inputStreamMock.read()).thenThrow(new IOException("boom"));
        when(inputStreamMock.read(any(byte[].class))).thenThrow(new IOException("boom"));
        when(inputStreamMock.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException("boom"));
        when(blobMock.getBinaryStream()).thenReturn(inputStreamMock);
        Row row = new Row("col", blobMock);
        assertThrows(IoException.class, row::bytesValue);
    }

    @Test
    void testBytesValue_withString() {
        Row row = new Row("col", "hello");
        assertArrayEquals("hello".getBytes(), row.bytesValue());
    }

    @Test
    void testBytesValue_withNull() {
        Row row = new Row("col", null);
        assertNull(row.bytesValue());
    }

    @Test
    void testBytesValue_withUnsupportedType_throwsIllegalStateException() {
        Row row = new Row("col", 42);
        assertThrows(IllegalStateException.class, row::bytesValue);
    }

    @Test
    void testNumberValue_withNumber() {
        Integer value = 42;
        Row row = new Row("col", value);
        assertSame(value, row.numberValue());
    }

    @Test
    void testNumberValue_withNonNumberString() {
        Row row = new Row("col", "42.5");
        assertEquals(new BigDecimal("42.5"), row.numberValue());
    }

    @Test
    void testNumberValue_withNull() {
        Row row = new Row("col", null);
        assertNull(row.numberValue());
    }

    @Test
    void testDateValue_withDate() {
        Date value = new Date();
        Row row = new Row("col", value);
        assertSame(value, row.dateValue());
    }

    @Test
    void testDateValue_withString() {
        Row row = new Row("col", "2024-01-01 12:00:00.0");
        assertEquals(Timestamp.valueOf("2024-01-01 12:00:00.0"), row.dateValue());
    }

    @Test
    void testDateValue_withNull() {
        Row row = new Row("col", null);
        assertNull(row.dateValue());
    }

    @Test
    void testLongValue_withLong() {
        Row row = new Row("col", 42L);
        assertEquals(42L, row.longValue());
    }

    @Test
    void testLongValue_withDouble() {
        Row row = new Row("col", 42.9d);
        assertEquals(42L, row.longValue());
    }

    @Test
    void testLongValue_withString() {
        Row row = new Row("col", "42");
        assertEquals(42L, row.longValue());
    }

    @Test
    void testLongValue_withNull() {
        Row row = new Row("col", null);
        assertNull(row.longValue());
    }

    @Test
    void testStringValue_withValue() {
        Row row = new Row("col", 42);
        assertEquals("42", row.stringValue());
    }

    @Test
    void testStringValue_withNull() {
        Row row = new Row("col", null);
        assertNull(row.stringValue());
    }

    @Test
    void testCsvValue_withMultipleColumns() {
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { "a", "b" });
        assertEquals("a,b", row.csvValue());
    }

    @Test
    void testCsvValue_withNullColumn() {
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { "a", null });
        assertEquals("a,", row.csvValue());
    }

    @Test
    void testCsvValue_withSingleColumn() {
        Row row = new Row("col1", "a");
        assertEquals("a", row.csvValue());
    }

    @Test
    void testCsvValue_withEmptyRow() {
        Row row = new Row(0);
        assertEquals("", row.csvValue());
    }

    @Test
    void testGetBytes_withByteArray() {
        byte[] bytes = { 1, 2, 3 };
        Row row = new Row("col", bytes);
        assertArrayEquals(bytes, row.getBytes("col"));
    }

    @Test
    void testGetString_withStringValue() {
        Row row = new Row("col", "value");
        assertEquals("value", row.getString("col"));
    }

    @Test
    void testGetString_withBigDecimalValue() {
        Row row = new Row("col", new BigDecimal("1.50"));
        assertEquals("1.50", row.getString("col"));
    }

    @Test
    void testGetString_withByteArrayValue() {
        byte[] bytes = { 1, 2, 3 };
        Row row = new Row("col", bytes);
        assertEquals(Hex.encodeHexString(bytes), row.getString("col"));
    }

    @Test
    void testGetString_withOtherObjectValue() {
        Row row = new Row("col", 42);
        assertEquals("42", row.getString("col"));
    }

    @Test
    void testGetString_withNullValueAndColumnPresent_returnsNull() {
        Row row = new Row("col", null);
        assertNull(row.getString("col"));
    }

    @Test
    void testGetString_withMissingColumn_throwsColumnNotFoundException() {
        Row row = new Row(0);
        assertThrows(ColumnNotFoundException.class, () -> row.getString("missing"));
    }

    @Test
    void testGetString_withMissingColumnAndCheckForColumnFalse_returnsNull() {
        Row row = new Row(0);
        assertNull(row.getString("missing", false));
    }

    @Test
    void testGetInt_withNumberValue() {
        Row row = new Row("col", 42);
        assertEquals(42, row.getInt("col"));
    }

    @Test
    void testGetInt_withStringValue() {
        Row row = new Row("col", "42");
        assertEquals(42, row.getInt("col"));
    }

    @Test
    void testGetInt_withUnsupportedTypePresent_returnsZero() {
        Row row = new Row("col", true);
        assertEquals(0, row.getInt("col"));
    }

    @Test
    void testGetInt_withMissingColumn_throwsColumnNotFoundException() {
        Row row = new Row(0);
        assertThrows(ColumnNotFoundException.class, () -> row.getInt("missing"));
    }

    @Test
    void testGetInteger_withNumberValue() {
        Row row = new Row("col", 42);
        assertEquals(42, row.getInteger("col"));
    }

    @Test
    void testGetInteger_withStringValue() {
        Row row = new Row("col", "42");
        assertEquals(42, row.getInteger("col"));
    }

    @Test
    void testGetInteger_withMissingColumn_returnsNull() {
        Row row = new Row(0);
        assertNull(row.getInteger("missing"));
    }

    @Test
    void testGetLong_withNumberValue() {
        Row row = new Row("col", 42);
        assertEquals(42L, row.getLong("col"));
    }

    @Test
    void testGetLong_withStringValue() {
        Row row = new Row("col", "42");
        assertEquals(42L, row.getLong("col"));
    }

    @Test
    void testGetLong_withMissingColumn_throwsColumnNotFoundException() {
        Row row = new Row(0);
        assertThrows(ColumnNotFoundException.class, () -> row.getLong("missing"));
    }

    @Test
    void testGetFloat_withNumberValue() {
        Row row = new Row("col", 42.5f);
        assertEquals(42.5f, row.getFloat("col"));
    }

    @Test
    void testGetFloat_withStringValue() {
        Row row = new Row("col", "42.5");
        assertEquals(42.5f, row.getFloat("col"));
    }

    @Test
    void testGetFloat_withMissingColumn_throwsColumnNotFoundException() {
        Row row = new Row(0);
        assertThrows(ColumnNotFoundException.class, () -> row.getFloat("missing"));
    }

    @Test
    void testGetBigDecimal_withBigDecimalValue() {
        BigDecimal value = new BigDecimal("1.50");
        Row row = new Row("col", value);
        assertSame(value, row.getBigDecimal("col"));
    }

    @Test
    void testGetBigDecimal_withStringValue() {
        Row row = new Row("col", "1.50");
        assertEquals(new BigDecimal("1.50"), row.getBigDecimal("col"));
    }

    @Test
    void testGetBigDecimal_withIntegerValue() {
        Row row = new Row("col", 5);
        assertEquals(new BigDecimal(5), row.getBigDecimal("col"));
    }

    @Test
    void testGetBigDecimal_withMissingColumn_throwsColumnNotFoundException() {
        Row row = new Row(0);
        assertThrows(ColumnNotFoundException.class, () -> row.getBigDecimal("missing"));
    }

    @Test
    void testGetBoolean_withStringOne_returnsTrue() {
        Row row = new Row("col", "1");
        assertTrue(row.getBoolean("col"));
    }

    @Test
    void testGetBoolean_withPositiveNumber_returnsTrue() {
        Row row = new Row("col", 5);
        assertTrue(row.getBoolean("col"));
    }

    @Test
    void testGetBoolean_withZeroNumber_returnsFalse() {
        Row row = new Row("col", 0);
        assertFalse(row.getBoolean("col"));
    }

    @Test
    void testGetBoolean_withBooleanValue() {
        Row row = new Row("col", Boolean.TRUE);
        assertTrue(row.getBoolean("col"));
    }

    @Test
    void testGetBoolean_withStringValue() {
        Row row = new Row("col", "true");
        assertTrue(row.getBoolean("col"));
    }

    @Test
    void testGetBoolean_withMissingColumn_throwsColumnNotFoundException() {
        Row row = new Row(0);
        assertThrows(ColumnNotFoundException.class, () -> row.getBoolean("missing"));
    }

    @Test
    void testGetTime_withTimeValue() {
        Time value = new Time(1000L);
        Row row = new Row("col", value);
        assertSame(value, row.getTime("col"));
    }

    @Test
    void testGetTime_withDateValue() {
        Date value = new Date(60000L);
        Row row = new Row("col", value);
        assertEquals(new Time(value.getTime()), row.getTime("col"));
    }

    @Test
    void testGetTimestamp_withTimestampValue() {
        Timestamp value = new Timestamp(1000L);
        Row row = new Row("col", value);
        assertSame(value, row.getTimestamp("col"));
    }

    @Test
    void testGetTimestamp_withLocalDateTimeValue() {
        LocalDateTime value = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        Row row = new Row("col", value);
        assertEquals(Timestamp.valueOf(value), row.getTimestamp("col"));
    }

    @Test
    void testGetTimestamp_withLocalDateValue() {
        LocalDate value = LocalDate.of(2024, 1, 1);
        Row row = new Row("col", value);
        assertEquals(Timestamp.valueOf(value.atStartOfDay()), row.getTimestamp("col"));
    }

    @Test
    void testGetTimestamp_withDateValue() {
        Date value = new Date(60000L);
        Row row = new Row("col", value);
        assertEquals(new Timestamp(value.getTime()), row.getTimestamp("col"));
    }

    @Test
    void testGetTimestamp_withMissingColumn_throwsColumnNotFoundException() {
        Row row = new Row(0);
        assertThrows(ColumnNotFoundException.class, () -> row.getTimestamp("missing"));
    }

    @Test
    void testGetDateTime_withNumberValue() {
        Row row = new Row("col", 60000L);
        assertEquals(new Date(60000L), row.getDateTime("col"));
    }

    @Test
    void testGetDateTime_withDateValue() {
        Date value = new Date(60000L);
        Row row = new Row("col", value);
        assertSame(value, row.getDateTime("col"));
    }

    @Test
    void testGetDateTime_withTimezoneOffsetStringValue() {
        Row row = new Row("col", "2024-01-01 12:00:00.123456789 +02:00");
        Timestamp expected = Timestamp.from(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 123456789, ZoneOffset.ofHours(2)).toInstant());
        assertEquals(expected, row.getDateTime("col"));
    }

    @Test
    void testGetDateTime_withStringValue() {
        Row row = new Row("col", "2024-01-01 12:00:00.000");
        assertEquals(Timestamp.valueOf("2024-01-01 12:00:00.000"), row.getDateTime("col"));
    }

    @Test
    void testGetDateTime_withShortFractionStringValue_matchesTimestampPattern() {
        Row row = new Row("col", "2024-01-01 12:00:00.0");
        // Timestamp.valueOf(...).equals(Date) is always false since Timestamp.equals requires
        // the argument to also be a Timestamp, so compare millis instead of the objects.
        assertEquals(Timestamp.valueOf("2024-01-01 12:00:00.0").getTime(), row.getDateTime("col").getTime());
    }

    @Test
    void testGetDateTime_withNumericStringValue_fallsBackToEpochMillis() {
        Row row = new Row("col", "60000");
        assertEquals(new Date(60000L), row.getDateTime("col"));
    }

    @Test
    void testGetDateTime_withLocalDateTimeValue() {
        LocalDateTime value = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        Row row = new Row("col", value);
        assertEquals(new Date(Timestamp.valueOf(value).getTime()), row.getDateTime("col"));
    }

    @Test
    void testGetDateTime_withLocalDateValue() {
        LocalDate value = LocalDate.of(2024, 1, 1);
        Row row = new Row("col", value);
        assertEquals(new Date(Timestamp.valueOf(value.atStartOfDay()).getTime()), row.getDateTime("col"));
    }

    @Test
    void testGetDateTime_withMissingColumn_throwsColumnNotFoundException() {
        Row row = new Row(0);
        assertThrows(ColumnNotFoundException.class, () -> row.getDateTime("missing"));
    }

    @Test
    void testToArray_withKeys() {
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { "a", "b" });
        assertArrayEquals(new Object[] { "a", "b" }, row.toArray(new String[] { "col1", "col2" }));
    }

    @Test
    void testToArray_withKeys_missingKeyReturnsNull() {
        Row row = new Row("col1", "a");
        assertArrayEquals(new Object[] { "a", null }, row.toArray(new String[] { "col1", "missing" }));
    }

    @Test
    void testToArray_returnsAllValuesInOrder() {
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { "a", "b" });
        assertArrayEquals(new Object[] { "a", "b" }, row.toArray());
    }

    @Test
    void testToStringArray_withKeys() {
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { "a", 42 });
        assertArrayEquals(new String[] { "a", "42" }, row.toStringArray(new String[] { "col1", "col2" }));
    }

    @Test
    void testGetLength_withStringValues() {
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { "ab", "cde" });
        assertEquals(5L, row.getLength());
    }

    @Test
    void testGetLength_withBlobValue() throws SQLException {
        Blob blobMock = mock(Blob.class);
        when(blobMock.length()).thenReturn(7L);
        Row row = new Row("col", blobMock);
        assertEquals(7L, row.getLength());
    }

    @Test
    void testGetLength_withClobValue() throws SQLException {
        Clob clobMock = mock(Clob.class);
        when(clobMock.length()).thenReturn(9L);
        Row row = new Row("col", clobMock);
        assertEquals(9L, row.getLength());
    }

    @Test
    void testGetLength_whenBlobThrowsSQLException_skipsEntry() throws SQLException {
        Blob blobMock = mock(Blob.class);
        when(blobMock.length()).thenThrow(new SQLException("boom"));
        Row row = new Row(new String[] { "col1", "col2" }, new Object[] { blobMock, "ab" });
        assertEquals(2L, row.getLength());
    }
}
