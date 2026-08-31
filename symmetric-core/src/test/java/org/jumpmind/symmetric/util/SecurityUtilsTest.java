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
package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void testSanitizeGroupNameNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeGroupName(null));
    }

    @Test
    void testSanitizeGroupNameEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeGroupName(""));
    }

    @Test
    void testSanitizeGroupNameValidReturnsInput() {
        assertEquals("corp-west", SecurityUtils.sanitizeGroupName("corp-west"));
    }

    @Test
    void testSanitizeGroupNameInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeGroupName("group name"));
    }

    @Test
    void testSanitizeNodeIdNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeNodeId(null));
    }

    @Test
    void testSanitizeNodeIdEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeNodeId(""));
    }

    @Test
    void testSanitizeNodeIdValidReturnsInput() {
        assertEquals("node-001", SecurityUtils.sanitizeNodeId("node-001"));
    }

    @Test
    void testSanitizeNodeIdInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeNodeId("node;drop"));
    }

    @Test
    void testSanitizeExternalIdNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeExternalId(null));
    }

    @Test
    void testSanitizeExternalIdEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeExternalId(""));
    }

    @Test
    void testSanitizeExternalIdValidReturnsInput() {
        assertEquals("client-001", SecurityUtils.sanitizeExternalId("client-001"));
    }

    @Test
    void testSanitizeExternalIdWithSpaceReturnsInput() {
        assertEquals("ext id", SecurityUtils.sanitizeExternalId("ext id"));
    }

    @Test
    void testSanitizeExternalIdInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtils.sanitizeExternalId("ext;id"));
    }

    @Test
    void testSanitizeLogArgumentsNoArgs() {
        assertArrayEquals(new Object[0], SecurityUtils.sanitizeLogArguments());
    }

    @Test
    void testSanitizeLogArgumentsSingleString() {
        assertArrayEquals(new Object[] { "hello" }, SecurityUtils.sanitizeLogArguments("hello"));
    }

    @Test
    void testSanitizeLogArgumentsNullArg() {
        assertArrayEquals(new Object[] { "null" }, SecurityUtils.sanitizeLogArguments((Object) null));
    }

    @Test
    void testSanitizeLogArgumentsMultipleArgs() {
        assertArrayEquals(new Object[] { "nodeA", "channelB", "42" }, SecurityUtils.sanitizeLogArguments("nodeA", "channelB", 42));
    }

    @Test
    void testSanitizeLogArgumentsStripsNewlines() {
        assertArrayEquals(new Object[] { "line1 line2" }, SecurityUtils.sanitizeLogArguments("line1\nline2"));
    }

    @Test
    void testSanitizeLogArgumentsStripsCarriageReturn() {
        assertArrayEquals(new Object[] { "a b" }, SecurityUtils.sanitizeLogArguments("a\rb"));
    }

    @Test
    void testSanitizeLogArgumentsStripsTab() {
        assertArrayEquals(new Object[] { "col1 col2" }, SecurityUtils.sanitizeLogArguments("col1\tcol2"));
    }

    @Test
    void testSanitizeLogArgumentsMixedTypesConverted() {
        assertArrayEquals(new Object[] { "true", "3.14", "99" }, SecurityUtils.sanitizeLogArguments(true, 3.14, 99L));
    }

    @Test
    void testSanitizeLogArgumentsNullAmongOthers() {
        assertArrayEquals(new Object[] { "a", "null", "b" }, SecurityUtils.sanitizeLogArguments("a", null, "b"));
    }

    @Test
    void testSanitizeLogArgumentsSanitizesEachArg() {
        assertArrayEquals(new Object[] { "a b", "c d" }, SecurityUtils.sanitizeLogArguments("a\nb", "c\td"));
    }
}
