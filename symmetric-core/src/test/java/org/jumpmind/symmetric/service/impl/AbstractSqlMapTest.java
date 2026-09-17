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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.common.TableConstants;
import org.junit.jupiter.api.Test;

class AbstractSqlMapTest {
    @Test
    void testMergeSqlReplacementTokens_withPrefix() {
        Map<String, String> tokens = AbstractSqlMap.mergeSqlReplacementTokens(null, "sym");
        assertEquals("sym_node", tokens.get(TableConstants.SYM_NODE));
    }

    @Test
    void testMergeSqlReplacementTokens_withBlankPrefix() {
        Map<String, String> tokens = AbstractSqlMap.mergeSqlReplacementTokens(null, "");
        assertEquals("node", tokens.get(TableConstants.SYM_NODE));
    }

    @Test
    void testMergeSqlReplacementTokens_coversEveryTable() {
        Map<String, String> tokens = AbstractSqlMap.mergeSqlReplacementTokens(null, "sym");
        assertEquals(TableConstants.getTablesWithoutPrefix().size(), tokens.size());
    }

    @Test
    void testMergeSqlReplacementTokens_withExtraTokensOverridingTableNames() {
        Map<String, String> extra = new HashMap<String, String>();
        extra.put(TableConstants.SYM_NODE, "other_node");
        extra.put("custom", "value");
        Map<String, String> tokens = AbstractSqlMap.mergeSqlReplacementTokens(extra, "sym");
        assertEquals("other_node", tokens.get(TableConstants.SYM_NODE));
        assertEquals("value", tokens.get("custom"));
    }

    @Test
    void testPutSql_replacesTableTokens() {
        TestSqlMap sqlMap = new TestSqlMap(null, "sym");
        sqlMap.put("selectSql", "select * from $(node)");
        assertEquals("select * from sym_node", sqlMap.getSql("selectSql"));
    }

    @Test
    void testPutSql_collapsesWhitespace() {
        TestSqlMap sqlMap = new TestSqlMap(null, "sym");
        sqlMap.put("selectSql", "select *\n   from  $(node)\twhere node_id = ?");
        assertEquals("select * from sym_node where node_id = ?", sqlMap.getSql("selectSql"));
    }

    @Test
    void testPutSql_leavesUnknownTokenAlone() {
        TestSqlMap sqlMap = new TestSqlMap(null, "sym");
        sqlMap.put("selectSql", "select * from $(not_a_table)");
        assertEquals("select * from $(not_a_table)", sqlMap.getSql("selectSql"));
    }

    @Test
    void testPutSql_withoutReplacementTokensLeavesTokenAlone() {
        TestSqlMap sqlMap = new TestSqlMap(null, (Map<String, String>) null);
        sqlMap.put("selectSql", "select * from $(node)");
        assertEquals("select * from $(node)", sqlMap.getSql("selectSql"));
    }

    @Test
    void testPutSql_scrubsSqlThroughPlatform() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.scrubSql("select * from sym_node")).thenReturn("scrubbed sql");
        TestSqlMap sqlMap = new TestSqlMap(platform, "sym");
        sqlMap.put("selectSql", "select * from $(node)");
        assertEquals("scrubbed sql", sqlMap.getSql("selectSql"));
    }

    @Test
    void testGetSql_withMultipleKeysAppendsLiteralTextForUnknownKeys() {
        TestSqlMap sqlMap = new TestSqlMap(null, "sym");
        sqlMap.put("selectSql", "select * from $(node)");
        assertEquals("select * from sym_node where node_id = ? ", sqlMap.getSql("selectSql", "where node_id = ?"));
    }

    @Test
    void testGetSql_withMultipleKeysSkipsNullKeys() {
        TestSqlMap sqlMap = new TestSqlMap(null, "sym");
        sqlMap.put("selectSql", "select * from $(node)");
        assertEquals("select * from sym_node ", sqlMap.getSql("selectSql", null));
    }

    @Test
    void testGetSql_withNoKeys() {
        assertEquals("", new TestSqlMap(null, "sym").getSql());
    }

    @Test
    void testGetSql_withNullKeyArray() {
        assertEquals("", new TestSqlMap(null, "sym").getSql((String[]) null));
    }

    // Defect pinned, not endorsed: a single unknown key is appended to the buffer without a null
    // check, so callers get the four-character string "null" back instead of null or an empty string.
    @Test
    void testGetSql_withSingleUnknownKey() {
        assertEquals("null", new TestSqlMap(null, "sym").getSql("missingSql"));
    }

    private static class TestSqlMap extends AbstractSqlMap {
        TestSqlMap(IDatabasePlatform platform, String tablePrefix) {
            super(platform, tablePrefix);
        }

        TestSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
            super(platform, replacementTokens);
        }

        void put(String key, String sql) {
            putSql(key, sql);
        }
    }
}
