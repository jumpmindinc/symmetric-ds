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
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.jumpmind.symmetric.service.impl.StartupParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;

class SymmetricBootTest {
    private final IStartupParameterService startupParameterService = StartupParameterService.getInstance();

    @BeforeEach
    void setUp() {
        clearSystemProperties();
    }

    @AfterEach
    void tearDown() {
        clearSystemProperties();
        startupParameterService.unregisterEngine(IStartupParameterService.GLOBAL_ENGINE_NAME);
    }

    private void clearSystemProperties() {
        clearSystemProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        clearSystemProperty(ServerConstants.CLUSTER_SERVER_ID);
        clearSystemProperty("test.war.server.property");
    }

    private void clearSystemProperty(String key) {
        System.clearProperty(key);
        startupParameterService.refreshSystemProperty(key);
    }

    @Test
    void registerServerProperties_classpathFile_registersGlobalStartupParameters(@TempDir File tempDir) throws IOException {
        File serverPropertiesFile = new File(tempDir, "symmetric-server.properties");
        Properties fileContents = new Properties();
        fileContents.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        fileContents.setProperty(ServerConstants.CLUSTER_SERVER_ID, "war-pod-a");
        try (FileOutputStream out = new FileOutputStream(serverPropertiesFile)) {
            fileContents.store(out, null);
        }
        SymmetricBoot.registerServerProperties(serverPropertiesFile.toURI().toURL(), startupParameterService);
        assertTrue(startupParameterService.isGlobal(ParameterConstants.CLUSTER_LOCKING_ENABLED, false));
        assertEquals("war-pod-a", startupParameterService.getGlobalString(ServerConstants.CLUSTER_SERVER_ID));
    }

    @Test
    void registerServerProperties_classpathFile_copiesEveryPropertyToSystemProperties(@TempDir File tempDir) throws IOException {
        File serverPropertiesFile = new File(tempDir, "symmetric-server.properties");
        Properties fileContents = new Properties();
        fileContents.setProperty("test.war.server.property", "value");
        try (FileOutputStream out = new FileOutputStream(serverPropertiesFile)) {
            fileContents.store(out, null);
        }
        SymmetricBoot.registerServerProperties(serverPropertiesFile.toURI().toURL(), startupParameterService);
        assertEquals("value", System.getProperty("test.war.server.property"));
    }

    @Test
    void registerServerProperties_missingFile_resolvesFromJvmSystemProperty() {
        System.setProperty(ServerConstants.CLUSTER_SERVER_ID, "war-pod-b");
        startupParameterService.refreshSystemProperty(ServerConstants.CLUSTER_SERVER_ID);
        SymmetricBoot.registerServerProperties(null, startupParameterService);
        assertEquals("war-pod-b", startupParameterService.getGlobalString(ServerConstants.CLUSTER_SERVER_ID));
        assertFalse(startupParameterService.isGlobal(ParameterConstants.CLUSTER_LOCKING_ENABLED, false));
    }

    @Test
    void symmetricBoot_noArgConstructor_instantiableByServletContainer() {
        assertDoesNotThrow(() -> SymmetricBoot.class.getConstructor().newInstance());
    }

    @Test
    void configure_warDeployment_registersGlobalStartupParametersBeforeSpringStarts() {
        System.setProperty(ServerConstants.CLUSTER_SERVER_ID, "war-pod-c");
        startupParameterService.refreshSystemProperty(ServerConstants.CLUSTER_SERVER_ID);
        SpringApplicationBuilder builder = new SpringApplicationBuilder();
        assertSame(builder, new SymmetricBoot().configure(builder));
        assertEquals("war-pod-c", startupParameterService.getGlobalString(ServerConstants.CLUSTER_SERVER_ID));
    }
}
