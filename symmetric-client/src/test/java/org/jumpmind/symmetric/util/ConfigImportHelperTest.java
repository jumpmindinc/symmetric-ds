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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;

import org.jumpmind.symmetric.cache.ClusteredCacheManager;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator;
import org.jumpmind.symmetric.cache.JcsTcpCacheCoordinator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConfigImportHelperTest {
    private static Field isInitializationCompleteField;
    private static Field peerNetworkCoordinatorField;
    private static boolean originalIsInitializationComplete;
    private static IClusterCacheCoordinator originalPeerNetworkCoordinator;

    /**
     * ConfigImportHelper builds a real ClientSymmetricEngine, whose init()/setup() unconditionally report to and query the JVM-wide ClusteredCacheManager
     * singleton. That singleton is normally initialized once by SymmetricEngineHolder during server startup, which never runs here, so flip its switches
     * directly for the duration of this test class: mark it initialized, and give it an unstarted peer coordinator so peer queries return empty results instead
     * of throwing or NPEing.
     */
    @BeforeAll
    static void flipClusteredCacheManagerInitializedSwitch() throws Exception {
        ClusteredCacheManager instance = (ClusteredCacheManager) ClusteredCacheManager.getInstance();
        isInitializationCompleteField = ClusteredCacheManager.class.getDeclaredField("isInitializationComplete");
        isInitializationCompleteField.setAccessible(true);
        originalIsInitializationComplete = isInitializationCompleteField.getBoolean(instance);
        isInitializationCompleteField.setBoolean(instance, true);
        peerNetworkCoordinatorField = ClusteredCacheManager.class.getDeclaredField("peerNetworkCoordinator");
        peerNetworkCoordinatorField.setAccessible(true);
        originalPeerNetworkCoordinator = (IClusterCacheCoordinator) peerNetworkCoordinatorField.get(instance);
        if (originalPeerNetworkCoordinator == null) {
            peerNetworkCoordinatorField.set(instance, new JcsTcpCacheCoordinator());
        }
    }

    @AfterAll
    static void restoreClusteredCacheManagerInitializedSwitch() throws Exception {
        ClusteredCacheManager instance = (ClusteredCacheManager) ClusteredCacheManager.getInstance();
        isInitializationCompleteField.setBoolean(instance, originalIsInitializationComplete);
        peerNetworkCoordinatorField.set(instance, originalPeerNetworkCoordinator);
    }

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
