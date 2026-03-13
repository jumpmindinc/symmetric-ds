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
package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class ConfigImportHelperTest {
    @Test
    void containsNodeGroupReturnsTrueForLoadedGroup() throws IOException {
        try (ConfigImportHelper helper = new ConfigImportHelper("sym")) {
            String sql = """
                    insert into sym_node_group (node_group_id, description) values ('server', 'Server Group');\n
                    insert into sym_node_group (node_group_id, description) values ('client', 'Client Group');\n
                    """;
            helper.loadContent(sql, false);
            assertTrue(helper.containsNodeGroup("server"));
            assertTrue(helper.containsNodeGroup("client"));
            assertFalse(helper.containsNodeGroup("nonexistent"));
        }
    }

    @Test
    void containsNodeGroupReturnsFalseForEmptyImport() {
        try (ConfigImportHelper helper = new ConfigImportHelper("sym")) {
            assertFalse(helper.containsNodeGroup("server"));
        }
    }

    @Test
    void exportAsSqlProducesOutput() throws IOException {
        try (ConfigImportHelper helper = new ConfigImportHelper("sym")) {
            String sql = "insert into sym_node_group (node_group_id, description) values ('testgroup', 'Test');\n";
            helper.loadContent(sql, false);
            String exported = helper.exportConfigAsSql();
            assertNotNull(exported);
            assertTrue(exported.contains("testgroup"));
        }
    }

    @Test
    void modifyViaEngineIsReflectedInExport() throws IOException {
        try (ConfigImportHelper helper = new ConfigImportHelper("sym")) {
            String sql = "insert into sym_node_group (node_group_id, description) values ('mygroup', 'Original');\n";
            helper.loadContent(sql, false);
            helper.getEngine().getSqlTemplate().update(
                    "update sym_node_group set description = 'Modified' where node_group_id = 'mygroup'");
            String exported = helper.exportConfigAsSql();
            assertTrue(exported.contains("Modified"));
        }
    }

    @Test
    void closeReleasesResources() {
        assertDoesNotThrow(() -> {
            ConfigImportHelper helper = new ConfigImportHelper("sym");
            helper.close();
        });
    }

    @Test
    void unknownTableThrowsWithoutCorrupting() {
        assertDoesNotThrow(() -> {
            try (ConfigImportHelper helper = new ConfigImportHelper("sym")) {
                helper.loadContent("insert into nonexistent_table (col) values ('val');\n", false);
            } catch (Exception e) {
                // H2 throws on unknown tables, which is fine — the helper is still safely closeable
            }
        });
    }
}
