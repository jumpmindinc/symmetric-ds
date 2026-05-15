package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SecurityUtilsTest {
    @Test
    void testSanitizeForLoggingNullReturnsNullString() {
        assertEquals("null", SecurityUtils.sanitizeForLogging(null));
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
        assertEquals("line1 line2", SecurityUtils.sanitizeForLogging("line1\nline2"));
    }

    @Test
    void testSanitizeForLoggingCarriageReturnReplaced() {
        assertEquals("line1 line2", SecurityUtils.sanitizeForLogging("line1\rline2"));
    }

    @Test
    void testSanitizeForLoggingTabReplaced() {
        assertEquals("col1 col2", SecurityUtils.sanitizeForLogging("col1\tcol2"));
    }

    @Test
    void testSanitizeForLoggingMixedSpecialCharsAllReplaced() {
        assertEquals("a b c d", SecurityUtils.sanitizeForLogging("a\nb\rc\td"));
    }

    @Test
    void testSanitizeForLoggingMultipleConsecutiveSpecialChars() {
        assertEquals("a   b", SecurityUtils.sanitizeForLogging("a\n\r\tb"));
    }

    @Test
    void testSanitizeForLoggingLeadingAndTrailingNewlinesReplaced() {
        assertEquals(" hello ", SecurityUtils.sanitizeForLogging("\nhello\n"));
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
    void testSanitizeInternalIdentifierNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank(null));
    }

    @Test
    void testSanitizeInternalIdentifierEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank(""));
    }

    @Test
    void testSanitizeInternalIdentifierAlphanumericReturnsInput() {
        assertEquals("node001", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("node001"));
    }

    @Test
    void testSanitizeInternalIdentifierHyphenAllowed() {
        assertEquals("server-001", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("server-001"));
    }

    @Test
    void testSanitizeInternalIdentifierUnderscoreAllowed() {
        assertEquals("channel_default", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("channel_default"));
    }

    @Test
    void testSanitizeInternalIdentifierDotAllowed() {
        assertEquals("myschema.mytable", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("myschema.mytable"));
    }

    @Test
    void testSanitizeInternalIdentifierSingleQuoteThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("node'; DROP TABLE sym_node; --"));
    }

    @Test
    void testSanitizeInternalIdentifierSpaceThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("node id"));
    }

    @Test
    void testSanitizeInternalIdentifierNewlineThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("node\ninjection"));
    }

    @Test
    void testSanitizeInternalIdentifierSemicolonThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("node;other"));
    }

    @Test
    void testSanitizeInternalIdentifierMixedValidCharsReturnsInput() {
        assertEquals("corp-A.users_v2", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("corp-A.users_v2"));
    }

    @Test
    void testSanitizeInternalIdentifierDigitsOnlyAllowed() {
        assertEquals("12345", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("12345"));
    }

    @Test
    void testSanitizeInternalIdentifierUppercaseAllowed() {
        assertEquals("GROUPNAME", SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("GROUPNAME"));
    }

    @Test
    void testSanitizeInternalIdentifierBlockCommentThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("node/*comment*/"));
    }

    @Test
    void testSanitizeInternalIdentifierBackslashThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("node\\path"));
    }

    @Test
    void testSanitizeInternalIdentifierHashThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifierOrLeaveBlankOrLeaveBlank("node#comment"));
    }
}
