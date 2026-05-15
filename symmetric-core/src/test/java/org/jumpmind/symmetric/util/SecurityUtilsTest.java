package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SecurityUtilsTest {
    @Test
    void testSanitizeForLoggingNullReturnsNull() {
        assertNull(SecurityUtils.sanitizeForLogging(null));
    }

    @Test
    void testSanitizeForLoggingEmptyStringUnchanged() {
        assertEquals("", SecurityUtils.sanitizeForLogging(""));
    }

    @Test
    void testSanitizeForLoggingNoSpecialCharsUnchanged() {
        assertEquals("normal string", SecurityUtils.sanitizeForLogging("normal string"));
    }

    @Test
    void testSanitizeForLoggingNewlineReplaced() {
        assertEquals("line1_line2", SecurityUtils.sanitizeForLogging("line1\nline2"));
    }

    @Test
    void testSanitizeForLoggingCarriageReturnReplaced() {
        assertEquals("line1_line2", SecurityUtils.sanitizeForLogging("line1\rline2"));
    }

    @Test
    void testSanitizeForLoggingTabReplaced() {
        assertEquals("col1_col2", SecurityUtils.sanitizeForLogging("col1\tcol2"));
    }

    @Test
    void testSanitizeForLoggingMixedSpecialCharsAllReplaced() {
        assertEquals("a_b_c_d", SecurityUtils.sanitizeForLogging("a\nb\rc\td"));
    }

    @Test
    void testSanitizeForLoggingMultipleConsecutiveSpecialChars() {
        assertEquals("a___b", SecurityUtils.sanitizeForLogging("a\n\r\tb"));
    }

    @Test
    void testSanitizeForLoggingLeadingAndTrailingNewlinesReplaced() {
        assertEquals("_hello_", SecurityUtils.sanitizeForLogging("\nhello\n"));
    }

    @Test
    void testSanitizeForLoggingPipeNotReplaced() {
        assertEquals("a|b", SecurityUtils.sanitizeForLogging("a|b"));
    }

    @Test
    void testSanitizeForLoggingOtherSpecialCharsNotReplaced() {
        assertEquals("a;b'c--d", SecurityUtils.sanitizeForLogging("a;b'c--d"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank(null));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank(""));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankAlphanumericReturnsInput() {
        assertEquals("node001", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("node001"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankHyphenAllowed() {
        assertEquals("server-001", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("server-001"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankUnderscoreAllowed() {
        assertEquals("channel_default", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("channel_default"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankDotAllowed() {
        assertEquals("myschema.mytable", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("myschema.mytable"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankSingleQuoteThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("node'; DROP TABLE sym_node; --"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankSpaceThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("node id"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankNewlineThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("node\ninjection"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankSemicolonThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("node;other"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankMixedValidCharsReturnsInput() {
        assertEquals("corp-A.users_v2", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("corp-A.users_v2"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankDigitsOnlyAllowed() {
        assertEquals("12345", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("12345"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankUppercaseAllowed() {
        assertEquals("GROUPNAME", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("GROUPNAME"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankBlockCommentThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("node/*comment*/"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankBackslashThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("node\\path"));
    }

    @Test
    void testsanitizeInternalIdentifierOrLeaveBlankHashThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlank("node#comment"));
    }
}
