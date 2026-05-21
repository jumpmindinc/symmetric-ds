/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General License,
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
package org.jumpmind.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FormatUtilsTest {
    @Test
    void testReplaceTokens() {
        assertEquals("test", FormatUtils.replaceTokens("test", null, true));
        assertEquals("test", FormatUtils.replaceTokens("test", new HashMap<String, String>(), true));
        Map<String, String> params = new HashMap<String, String>();
        params.put("test", "1");
        assertEquals("test1", FormatUtils.replaceTokens("test$(test)", params, true));
        assertEquals("test0001", FormatUtils.replaceTokens("test$(test|%04d)", params, true));
    }

    @Test
    void testReplaceCurrentTimestamp() {
        String beforeSql = "insert into sym_node values ('00000', 'test-root-group', '00000', 1, null, null, '2.0', null, null, current_timestamp, null, 0, 0, '00000', 'engine')";
        String afterSql = "insert into sym_node values ('00000', 'test-root-group', '00000', 1, null, null, '2.0', null, null, XXXX, null, 0, 0, '00000', 'engine')";
        Map<String, String> replacementTokens = new HashMap<String, String>();
        replacementTokens.put("current_timestamp", "XXXX");
        assertEquals(afterSql, FormatUtils.replaceTokens(beforeSql, replacementTokens, false));
    }

    @Test
    void testReplace() {
        assertEquals(FormatUtils.replace("nodeId", "001", "nodeId = $(nodeId)"), "nodeId = 001");
        assertEquals(FormatUtils.replace("nodeId", "001", "nodeId = $(nodeId:0)"), "nodeId = 001");
        assertEquals(FormatUtils.replace("nodeId", "001", "nodeId = $(nodeId:0:10)"), "nodeId = 001");
        assertEquals(FormatUtils.replace("nodeId", "1234567890ABC", "nodeId = $(nodeId:10)"), "nodeId = ABC");
        assertEquals(FormatUtils.replace("nodeId", "1234567890ABC", "nodeId = $(nodeId:10:11)"), "nodeId = A");
        assertEquals(FormatUtils.replace("nodeId", "001-002", "nodeId = $(nodeId:4)"), "nodeId = 002");
    }

    @Test
    void testRemovePrefix() {
        assertEquals("VALUE", FormatUtils.removePrefix("PREFIX_VALUE", "PREFIX_"));
        assertEquals("VALUE", FormatUtils.removePrefix("VALUE", ""));
        assertEquals("VALUE", FormatUtils.removePrefix("VALUE", "OTHER_"));
        assertEquals("VALUE", FormatUtils.removePrefix("VALUE", null));
        assertEquals(null, FormatUtils.removePrefix(null, "PREFIX_"));
    }

    @Test
    void testIsWildcardMatch() {
        assertTrue(FormatUtils.isWildCardMatch("TEST_1", "TEST_*"));
        assertTrue(FormatUtils.isWildCardMatch("TEST_2", "TEST_*"));
        assertTrue(FormatUtils.isWildCardMatch("TEST_TEST_TEST", "TEST_*"));
        assertFalse(FormatUtils.isWildCardMatch("NOT_A_MATCH", "TEST_*"));
        assertFalse(FormatUtils.isWildCardMatch("NOT_A_MATCH_TEST_1", "TEST_*"));
        assertTrue(FormatUtils.isWildCardMatch("NOT_A_MATCH_TEST_1", "*TEST*"));
        assertFalse(FormatUtils.isWildCardMatch("TEST_12", "TEST_1"));
        assertFalse(FormatUtils.isWildCardMatch("B_A", "*A*B"));
        assertTrue(FormatUtils.isWildCardMatch("A_B", "*A*B"));
        assertFalse(FormatUtils.isWildCardMatch("TEST_NO_MATCH", "TEST_*,!TEST_NO_MATCH"));
        assertTrue(FormatUtils.isWildCardMatch("A_B", "A*B"));
        assertFalse(FormatUtils.isWildCardMatch("item_price", "*item"));
        assertTrue(FormatUtils.isWildCardMatch("item_price", "*item*"));
        assertTrue(FormatUtils.isWildCardMatch("item_price", "item*"));
        assertFalse(FormatUtils.isWildCardMatch("c$table3", "*$table"));
    }

    @Test
    void testIsWildcardMatchIgnoreCase() {
        assertTrue(FormatUtils.isWildCardMatch("test_1", "TEST_*", true));
        assertFalse(FormatUtils.isWildCardMatch("other", "TEST_*", true));
        assertTrue(FormatUtils.isWildCardMatch("TEST_1", "TEST_*", false));
    }

    @Test
    void testToBoolean() {
        assertTrue(FormatUtils.toBoolean("true"));
        assertTrue(FormatUtils.toBoolean("1"));
        assertFalse(FormatUtils.toBoolean("false"));
        assertFalse(FormatUtils.toBoolean("0"));
        assertFalse(FormatUtils.toBoolean(""));
        assertFalse(FormatUtils.toBoolean(null));
    }

    @Test
    void testIsMixedCase() {
        assertTrue(FormatUtils.isMixedCase("HelloWorld"));
        assertFalse(FormatUtils.isMixedCase("ALLCAPS"));
        assertFalse(FormatUtils.isMixedCase("alllower"));
    }

    @Test
    void testIsWildCarded() {
        assertTrue(FormatUtils.isWildCarded("TAB_*"));
        assertTrue(FormatUtils.isWildCarded("TAB_1,TAB_2"));
        assertTrue(FormatUtils.isWildCarded("!TAB_1"));
        assertFalse(FormatUtils.isWildCarded("TAB_1"));
        assertFalse(FormatUtils.isWildCarded(null));
        assertFalse(FormatUtils.isWildCarded("TAB**_1"));
        assertFalse(FormatUtils.isWildCarded("TAB,,_1"));
        assertFalse(FormatUtils.isWildCarded("!!TAB_1"));
    }

    @Test
    void testEscapeAndUnescapeWildCards() {
        assertEquals("TAB**_1", FormatUtils.escapeWildCards("TAB*_1"));
        assertEquals("TAB*_1", FormatUtils.unescapeWildCards("TAB**_1"));
        assertNull(FormatUtils.escapeWildCards(null));
        assertNull(FormatUtils.unescapeWildCards(null));
    }

    @Test
    void testWordWrap() {
        String[] lines = FormatUtils.wordWrap("short", 80);
        assertEquals(1, lines.length);
        assertEquals("short", lines[0]);
        String[] wrapped = FormatUtils.wordWrap("one two three four five", 10);
        assertTrue(wrapped.length > 1);
    }

    @Test
    void testWordWrapDifferentLineSizes() {
        String[] lines = FormatUtils.wordWrap("one two three four five six seven eight", 15, 10);
        assertTrue(lines.length > 1);
    }

    @Test
    void testAbbreviateForLogging_shortString() {
        assertEquals("hello", FormatUtils.abbreviateForLogging("hello"));
    }

    @Test
    void testAbbreviateForLogging_longString() {
        String longStr = "a".repeat(1100);
        String result = FormatUtils.abbreviateForLogging(longStr);
        assertTrue(result.length() <= FormatUtils.MAX_CHARS_TO_LOG);
    }

    @Test
    void testAbbreviateForLogging_list() {
        List<String> items = Arrays.asList("alpha", "beta", "gamma");
        String result = FormatUtils.abbreviateForLogging(items, 1000);
        assertTrue(result.contains("alpha"));
        assertTrue(result.contains("beta"));
        assertTrue(result.startsWith("["));
        assertTrue(result.endsWith("]"));
    }

    @Test
    void testSplitOnSpacePreserveQuotedStrings() {
        String[] parts = FormatUtils.splitOnSpacePreserveQuotedStrings("one \"two three\" four");
        assertEquals(3, parts.length);
        assertEquals("one", parts[0]);
        assertEquals("two three", parts[1]);
        assertEquals("four", parts[2]);
    }

    @Test
    void testReplaceCharsToShortenName() {
        assertEquals("TBL", FormatUtils.replaceCharsToShortenName("TABLE"));
        assertEquals("t_b_l", FormatUtils.replaceCharsToShortenName("t_b_l"));
        assertEquals("symtblnm", FormatUtils.replaceCharsToShortenName("sym.table.name"));
    }

    @Test
    void testIsInteger() {
        assertTrue(FormatUtils.isInteger("123"));
        assertTrue(FormatUtils.isInteger("-456"));
        assertFalse(FormatUtils.isInteger("12.3"));
        assertFalse(FormatUtils.isInteger("abc"));
    }

    @Test
    void testFormatString_integer() {
        assertEquals("0042", FormatUtils.formatString("%04d", "42"));
    }

    @Test
    void testFormatString_string() {
        assertEquals("hello     ", FormatUtils.formatString("%-10s", "hello"));
    }

    @Test
    void testReplaceToken() {
        assertEquals("value=abc", FormatUtils.replaceToken("value=$(key)", "key", "abc", true));
        assertEquals("value=abc", FormatUtils.replaceToken("value=key", "key", "abc", false));
    }
}
