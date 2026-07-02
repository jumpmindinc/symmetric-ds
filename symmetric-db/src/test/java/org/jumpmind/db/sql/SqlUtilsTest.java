package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SqlUtilsTest {
    @Test
    void escapeStringDoublesSingleQuotes() {
        assertEquals("O''Brien", SqlUtils.escapeString("O'Brien"));
    }

    @Test
    void escapeStringLeavesStringWithoutQuotesUnchanged() {
        assertEquals("hello", SqlUtils.escapeString("hello"));
    }

    @Test
    void escapeStringReturnsNullForNull() {
        assertNull(SqlUtils.escapeString(null));
    }

    @Test
    void sanitizeIdentifierRemovesDangerousChars() {
        assertEquals("abcde", SqlUtils.sanitizeIdentifier("a\"b'c/d;e"));
    }

    @Test
    void sanitizeIdentifierKeepsSpaces() {
        assertEquals("my table", SqlUtils.sanitizeIdentifier("my table")); // space stays
    }

    @Test
    void sanitizeIdentifierReturnsNullForNull() {
        assertNull(SqlUtils.sanitizeIdentifier(null));
    }

    @Test
    void sanitizeFunctionRemovesDangerousCharsAndSpaces() {
        assertEquals("mytable", SqlUtils.sanitizeFunction("my\"ta'b/le;"));
        assertEquals("ab", SqlUtils.sanitizeFunction("a b")); // space removed
    }

    @Test
    void sanitizeFunctionReturnsNullForNull() {
        assertNull(SqlUtils.sanitizeFunction(null));
    }

    @Test
    void sanitizeIdentifierTruncatesToMaxLength() {
        String longName = "a".repeat(256); // 256 safe chars (none get stripped)
        assertEquals(255, SqlUtils.sanitizeIdentifier(longName).length());
    }

    @Test
    void sanitizeTablePrefixReplacesNonWordCharsWithUnderscore() {
        assertEquals("a_b", SqlUtils.sanitizeTablePrefix("a!b"));
    }

    @Test
    void sanitizeTablePrefixCollapsesConsecutiveUnderscores() {
        assertEquals("a_b", SqlUtils.sanitizeTablePrefix("a!!b")); // !! -> __ -> _
    }

    @Test
    void sanitizeTablePrefixLeavesCleanNameUnchanged() {
        assertEquals("sym", SqlUtils.sanitizeTablePrefix("sym"));
    }

    @Test
    void sanitizeTablePrefixReturnsNullForNull() {
        assertNull(SqlUtils.sanitizeTablePrefix(null));
    }
}
