/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
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
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class FormatUtilsTest {
    @Test
    public void testReplaceTokens() {
        assertEquals("test", FormatUtils.replaceTokens("test", null, true));
        assertEquals("test", FormatUtils.replaceTokens("test", new HashMap<String, String>(), true));
        Map<String, String> params = new HashMap<String, String>();
        params.put("test", "1");
        assertEquals("test1", FormatUtils.replaceTokens("test$(test)", params, true));
        assertEquals("test0001", FormatUtils.replaceTokens("test$(test|%04d)", params, true));
    }

    @Test
    public void testReplaceCurrentTimestamp() {
        String beforeSql = "insert into sym_node values ('00000', 'test-root-group', '00000', 1, null, null, '2.0', null, null, current_timestamp, null, 0, 0, '00000', 'engine')";
        String afterSql = "insert into sym_node values ('00000', 'test-root-group', '00000', 1, null, null, '2.0', null, null, XXXX, null, 0, 0, '00000', 'engine')";
        Map<String, String> replacementTokens = new HashMap<String, String>();
        replacementTokens.put("current_timestamp", "XXXX");
        assertEquals(afterSql, FormatUtils.replaceTokens(beforeSql, replacementTokens, false));
    }

    @Test
    public void testReplace() {
        assertEquals(FormatUtils.replace("nodeId", "001", "nodeId = $(nodeId)"), "nodeId = 001");
        assertEquals(FormatUtils.replace("nodeId", "001", "nodeId = $(nodeId:0)"), "nodeId = 001");
        assertEquals(FormatUtils.replace("nodeId", "001", "nodeId = $(nodeId:0:10)"), "nodeId = 001");
        assertEquals(FormatUtils.replace("nodeId", "1234567890ABC", "nodeId = $(nodeId:10)"), "nodeId = ABC");
        assertEquals(FormatUtils.replace("nodeId", "1234567890ABC", "nodeId = $(nodeId:10:11)"), "nodeId = A");
        assertEquals(FormatUtils.replace("nodeId", "001-002", "nodeId = $(nodeId:4)"), "nodeId = 002");
    }

    @Test
    public void testIsWildcardMatch() {
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
    public void testFormatSize() {
        assertEquals("1.00 KB", FormatUtils.formatSize(1024));
        assertEquals("1.00 MB", FormatUtils.formatSize(1024 * 1024));
        assertEquals("1.00 GB", FormatUtils.formatSize(1024 * 1024 * 1024));
        assertEquals("1024.00 GB", FormatUtils.formatSize(1024L * 1024 * 1024 * 1024));
        assertEquals("1070596096.00 GB", FormatUtils.formatSize(1024L * 1024 * 1024 * 1024 * 1024 * 1021));
        assertEquals("0 B", FormatUtils.formatSize(0));
        assertEquals("-1234567890 B", FormatUtils.formatSize(-1234567890L));
        assertEquals("1.15 GB", FormatUtils.formatSize(1234567890L));
    }
}
