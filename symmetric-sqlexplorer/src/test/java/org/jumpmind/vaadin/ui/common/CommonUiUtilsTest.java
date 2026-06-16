package org.jumpmind.vaadin.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.Test;

class CommonUiUtilsTest {
    @Test
    void isFilteredOut_emptyFilter_notFilteredOut() {
        assertFalse(CommonUiUtils.isFilteredOut("anything", ""));
    }

    @Test
    void isFilteredOut_nullFilter_notFilteredOut() {
        assertFalse(CommonUiUtils.isFilteredOut("anything", null));
    }

    @Test
    void isFilteredOut_shortFilter_usesStartsWith() {
        assertFalse(CommonUiUtils.isFilteredOut("hello", "he"));
        assertTrue(CommonUiUtils.isFilteredOut("hello", "el"));
    }

    @Test
    void isFilteredOut_longFilter_usesContains() {
        assertFalse(CommonUiUtils.isFilteredOut("hello world", "world"));
        assertTrue(CommonUiUtils.isFilteredOut("hello world", "xyz"));
    }

    @Test
    void isFilteredOut_caseInsensitive() {
        assertFalse(CommonUiUtils.isFilteredOut("Hello", "hel"));
        assertFalse(CommonUiUtils.isFilteredOut("hello", "HEL"));
    }

    @Test
    void isFilteredOut_exactTwoCharFilter_usesStartsWith() {
        assertFalse(CommonUiUtils.isFilteredOut("hello", "he"));
        assertTrue(CommonUiUtils.isFilteredOut("hello", "ll"));
    }

    @Test
    void formatDuration_underOneSecond_showsMillis() {
        assertEquals("500 ms", CommonUiUtils.formatDuration(500));
    }

    @Test
    void formatDuration_exactlyOneSecond_showsMillis() {
        assertEquals("1000 ms", CommonUiUtils.formatDuration(1000));
    }

    @Test
    void formatDuration_justOverOneSecond_showsSeconds() {
        assertEquals("1 s", CommonUiUtils.formatDuration(1001));
    }

    @Test
    void formatDuration_overOneSecond_showsSeconds() {
        assertEquals("5 s", CommonUiUtils.formatDuration(5000));
    }

    @Test
    void formatDuration_overOneMinute_showsMinutesAndSeconds() {
        assertEquals("1 m 30 s", CommonUiUtils.formatDuration(90000));
    }

    @Test
    void formatDuration_exactlyOneMinute_showsSeconds() {
        assertEquals("60 s", CommonUiUtils.formatDuration(60000));
    }

    @Test
    void formatDuration_justOverOneMinute_showsMinutesAndSeconds() {
        assertEquals("1 m 0 s", CommonUiUtils.formatDuration(60001));
    }

    @Test
    void formatDuration_twoMinutes_showsCorrectValue() {
        assertEquals("2 m 5 s", CommonUiUtils.formatDuration(125000));
    }

    @Test
    void castToNumber_noString_returnsZero() {
        assertEquals("0", CommonUiUtils.castToNumber("NO"));
        assertEquals("0", CommonUiUtils.castToNumber("no"));
        assertEquals("0", CommonUiUtils.castToNumber("FALSE"));
        assertEquals("0", CommonUiUtils.castToNumber("false"));
    }

    @Test
    void castToNumber_yesString_returnsOne() {
        assertEquals("1", CommonUiUtils.castToNumber("YES"));
        assertEquals("1", CommonUiUtils.castToNumber("yes"));
        assertEquals("1", CommonUiUtils.castToNumber("TRUE"));
        assertEquals("1", CommonUiUtils.castToNumber("true"));
    }

    @Test
    void castToNumber_decimalWithComma_replacesCommaWithPeriod() {
        assertEquals("1.5", CommonUiUtils.castToNumber("1,5"));
        assertEquals("1000.99", CommonUiUtils.castToNumber("1000,99"));
    }

    @Test
    void castToNumber_normalNumber_returnsAsIs() {
        assertEquals("42", CommonUiUtils.castToNumber("42"));
        assertEquals("3.14", CommonUiUtils.castToNumber("3.14"));
    }

    @Test
    void formatDateTime_nullDate_returnsNull() {
        assertNull(CommonUiUtils.formatDateTime(null));
    }

    @Test
    void formatDateTime_todaysDate_returnsTimeOnly() {
        String result = CommonUiUtils.formatDateTime(new Date());
        assertNotNull(result);
        assertFalse(result.contains("-"), "Expected time-only format but got: " + result);
    }

    @Test
    void formatDateTime_pastDate_returnsFullDateTime() {
        Calendar cal = Calendar.getInstance();
        cal.set(2020, Calendar.JANUARY, 1, 10, 30, 0);
        String result = CommonUiUtils.formatDateTime(cal.getTime());
        assertNotNull(result);
        assertTrue(result.startsWith("2020-01-01"), "Expected full datetime format but got: " + result);
    }

    @Test
    void formatDateTime_pastDate_includesTimeComponent() {
        Calendar cal = Calendar.getInstance();
        cal.set(2020, Calendar.JUNE, 15, 14, 45, 30);
        String result = CommonUiUtils.formatDateTime(cal.getTime());
        assertTrue(result.contains("14:45:30"), "Expected time in result but got: " + result);
    }

    @Test
    void getJdbcTypeValue_charTypes_returnEmptyString() {
        assertEquals("", CommonUiUtils.getJdbcTypeValue("CHAR"));
        assertEquals("", CommonUiUtils.getJdbcTypeValue("VARCHAR"));
        assertEquals("", CommonUiUtils.getJdbcTypeValue("LONGVARCHAR"));
    }

    @Test
    void getJdbcTypeValue_integerTypes_returnZero() {
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("INTEGER"));
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("TINYINT"));
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("SMALLINT"));
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("BIGINT"));
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("BIT"));
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("BOOLEAN"));
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("NUMERIC"));
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("REAL"));
    }

    @Test
    void getJdbcTypeValue_decimalTypes_returnDecimalZero() {
        assertEquals("0.00", CommonUiUtils.getJdbcTypeValue("DECIMAL"));
        assertEquals("0.0", CommonUiUtils.getJdbcTypeValue("DOUBLE"));
    }

    @Test
    void getJdbcTypeValue_dateTimeTypes_returnLiterals() {
        assertEquals("'2014-07-08'", CommonUiUtils.getJdbcTypeValue("DATE"));
        assertEquals("'12:00:00'", CommonUiUtils.getJdbcTypeValue("TIME"));
    }

    @Test
    void getJdbcTypeValue_timestamp_returnsNonNullFormattedString() {
        String result = CommonUiUtils.getJdbcTypeValue("TIMESTAMP");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void getJdbcTypeValue_lobTypes_returnEmptyQuotedString() {
        assertEquals("''", CommonUiUtils.getJdbcTypeValue("CLOB"));
        assertEquals("''", CommonUiUtils.getJdbcTypeValue("BLOB"));
    }

    @Test
    void getJdbcTypeValue_binaryTypes_returnNull() {
        assertNull(CommonUiUtils.getJdbcTypeValue("BINARY"));
        assertNull(CommonUiUtils.getJdbcTypeValue("VARBINARY"));
        assertNull(CommonUiUtils.getJdbcTypeValue("LONGBINARY"));
    }

    @Test
    void getJdbcTypeValue_collectionTypes_returnLiterals() {
        assertEquals("[]", CommonUiUtils.getJdbcTypeValue("ARRAY"));
    }

    @Test
    void getJdbcTypeValue_unknownType_returnsNull() {
        assertNull(CommonUiUtils.getJdbcTypeValue("UNKNOWN_TYPE"));
        assertNull(CommonUiUtils.getJdbcTypeValue("JAVA_OBJECT"));
        assertNull(CommonUiUtils.getJdbcTypeValue("REF"));
        assertNull(CommonUiUtils.getJdbcTypeValue("STRUCT"));
        assertNull(CommonUiUtils.getJdbcTypeValue("DISTINCT"));
        assertNull(CommonUiUtils.getJdbcTypeValue("DATALINK"));
    }

    @Test
    void getJdbcTypeValue_caseInsensitive() {
        assertEquals("", CommonUiUtils.getJdbcTypeValue("varchar"));
        assertEquals("0", CommonUiUtils.getJdbcTypeValue("integer"));
        assertEquals("''", CommonUiUtils.getJdbcTypeValue("blob"));
    }
}
