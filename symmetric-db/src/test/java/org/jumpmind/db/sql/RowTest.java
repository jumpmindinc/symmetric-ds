package org.jumpmind.db.sql;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;

class RowTest {

    @Test
    public void testCsvValue_withStrings() {
        Row row = new Row(3);
        row.put("col1", "value1");
        row.put("col2", "value2");
        row.put("col3", "value3");

        String csv = row.csvValue();

        assertEquals("value1,value2,value3", csv);
    }

    @Test
    public void testCsvValue_withByteArray() {
        Row row = new Row(2);
        row.put("col1", "text");
        byte[] binaryData = new byte[] { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF };
        row.put("col2", binaryData);

        String csv = row.csvValue();

        // Byte array should be hex encoded
        assertEquals("text,deadbeef", csv);
    }

    @Test
    public void testCsvValue_withNullValues() {
        Row row = new Row(3);
        row.put("col1", "value1");
        row.put("col2", null);
        row.put("col3", "value3");

        String csv = row.csvValue();

        assertEquals("value1,,value3", csv);
    }

    @Test
    public void testCsvValue_withMixedTypes() {
        Row row = new Row(4);
        row.put("col1", "text");
        row.put("col2", 123);
        row.put("col3", 45.67);
        row.put("col4", true);

        String csv = row.csvValue();

        assertEquals("text,123,45.67,true", csv);
    }

    @Test
    public void testCsvValue_withEmptyRow() {
        Row row = new Row(0);

        String csv = row.csvValue();

        assertEquals("", csv);
    }

    @Test
    public void testCsvValue_withSingleValue() {
        Row row = new Row(1);
        row.put("col1", "single");

        String csv = row.csvValue();

        assertEquals("single", csv);
    }

    @Test
    public void testCsvValue_withEmptyByteArray() {
        Row row = new Row(2);
        row.put("col1", "text");
        row.put("col2", new byte[0]);

        String csv = row.csvValue();

        assertEquals("text,", csv);
    }

    @Test
    public void testCsvValue_withBinary16() {
        Row row = new Row(2);
        row.put("id", 1);
        // Simulates a BINARY(16) UUID-like value
        byte[] binary16 = new byte[] {
            (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
            (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
            (byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0x98,
            (byte) 0x76, (byte) 0x54, (byte) 0x32, (byte) 0x10
        };
        row.put("uuid", binary16);

        String csv = row.csvValue();

        assertEquals("1,0123456789abcdeffedcba9876543210", csv);
    }
}
