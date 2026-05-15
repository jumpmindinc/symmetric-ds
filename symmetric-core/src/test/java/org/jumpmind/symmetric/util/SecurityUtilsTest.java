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
    void testSanitizeInternalIdentifierNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier(null));
    }

    @Test
    void testSanitizeInternalIdentifierEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier(""));
    }

    @Test
    void testSanitizeInternalIdentifierAlphanumericReturnsInput() {
        assertEquals("node001", SecurityUtils.sanitizeInternalIdentifier("node001"));
    }

    @Test
    void testSanitizeInternalIdentifierHyphenAllowed() {
        assertEquals("server-001", SecurityUtils.sanitizeInternalIdentifier("server-001"));
    }

    @Test
    void testSanitizeInternalIdentifierUnderscoreAllowed() {
        assertEquals("channel_default", SecurityUtils.sanitizeInternalIdentifier("channel_default"));
    }

    @Test
    void testSanitizeInternalIdentifierDotAllowed() {
        assertEquals("myschema.mytable", SecurityUtils.sanitizeInternalIdentifier("myschema.mytable"));
    }

    @Test
    void testSanitizeInternalIdentifierSingleQuoteThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier("node'; DROP TABLE sym_node; --"));
    }

    @Test
    void testSanitizeInternalIdentifierSpaceThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier("node id"));
    }

    @Test
    void testSanitizeInternalIdentifierNewlineThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier("node\ninjection"));
    }

    @Test
    void testSanitizeInternalIdentifierSemicolonThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier("node;other"));
    }

    @Test
    void testSanitizeInternalIdentifierMixedValidCharsReturnsInput() {
        assertEquals("corp-A.users_v2", SecurityUtils.sanitizeInternalIdentifier("corp-A.users_v2"));
    }

    @Test
    void testSanitizeInternalIdentifierDigitsOnlyAllowed() {
        assertEquals("12345", SecurityUtils.sanitizeInternalIdentifier("12345"));
    }

    @Test
    void testSanitizeInternalIdentifierUppercaseAllowed() {
        assertEquals("GROUPNAME", SecurityUtils.sanitizeInternalIdentifier("GROUPNAME"));
    }

    @Test
    void testSanitizeInternalIdentifierBlockCommentThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier("node/*comment*/"));
    }

    @Test
    void testSanitizeInternalIdentifierBackslashThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier("node\\path"));
    }

    @Test
    void testSanitizeInternalIdentifierHashThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeInternalIdentifier("node#comment"));
    }
}
