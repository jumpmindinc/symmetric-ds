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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.jumpmind.security.SecurityConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.jumpmind.symmetric.service.impl.StartupParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbstractCommandLauncherTest {
    private static final SymmetricAdmin LAUNCHER = new SymmetricAdmin("symadmin", "<subcommand> [options] [args]", "SymAdmin.Option.");
    private static final String KEYSTORE_SEED = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @BeforeEach
    void setUp() {
        clearSystemProperties();
    }

    @AfterEach
    void tearDown() {
        clearSystemProperties();
    }

    private void clearSystemProperties() {
        clearSystemProperty(ServerConstants.CONTAINER_MODE_ENABLED);
        clearSystemProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        clearSystemProperty(ServerConstants.CLUSTER_SERVER_ID);
        clearSystemProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED);
        clearSystemProperty(ServerConstants.HTTPS_ALLOW_SELF_SIGNED_CERTS);
        clearSystemProperty(ServerConstants.HTTPS_ENABLE);
        clearSystemProperty(ServerConstants.SERVER_DISALLOW_HTTP_METHODS);
        clearSystemProperty("test.unlisted.server.property");
    }

    private void clearSystemProperty(String key) {
        System.clearProperty(key);
        StartupParameterService.getInstance().refreshSystemProperty(key);
    }

    @Test
    void registerServerProperties_startupParametersInFile_areExportedToSystemProperties(@TempDir File tempDir) throws IOException {
        File serverPropertiesFile = new File(tempDir, "symmetric-server.properties");
        Properties fileContents = new Properties();
        fileContents.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        fileContents.setProperty(ServerConstants.CLUSTER_SERVER_ID, "pod-a");
        fileContents.setProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED, KEYSTORE_SEED);
        fileContents.setProperty(ServerConstants.HTTPS_ALLOW_SELF_SIGNED_CERTS, "false");
        try (FileOutputStream out = new FileOutputStream(serverPropertiesFile)) {
            fileContents.store(out, null);
        }
        IStartupParameterService startupParameterService = StartupParameterService.getInstance();
        assertTrue(AbstractCommandLauncher.registerServerProperties(serverPropertiesFile, startupParameterService));
        assertEquals("true", System.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
        assertEquals("pod-a", System.getProperty(ServerConstants.CLUSTER_SERVER_ID));
        assertEquals(KEYSTORE_SEED, System.getProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED));
        assertEquals("false", System.getProperty(ServerConstants.HTTPS_ALLOW_SELF_SIGNED_CERTS));
        assertTrue(startupParameterService.isGlobal(ParameterConstants.CLUSTER_LOCKING_ENABLED, false));
    }

    @Test
    void registerServerProperties_unlistedProperty_staysOutOfSystemProperties(@TempDir File tempDir) throws IOException {
        File serverPropertiesFile = new File(tempDir, "symmetric-server.properties");
        Properties fileContents = new Properties();
        fileContents.setProperty("test.unlisted.server.property", "value");
        try (FileOutputStream out = new FileOutputStream(serverPropertiesFile)) {
            fileContents.store(out, null);
        }
        assertTrue(AbstractCommandLauncher.registerServerProperties(serverPropertiesFile, StartupParameterService.getInstance()));
        assertNull(System.getProperty("test.unlisted.server.property"));
    }

    @Test
    void registerServerProperties_jvmPropertyAlreadySet_keepsJvmValue(@TempDir File tempDir) throws IOException {
        File serverPropertiesFile = new File(tempDir, "symmetric-server.properties");
        Properties fileContents = new Properties();
        fileContents.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        try (FileOutputStream out = new FileOutputStream(serverPropertiesFile)) {
            fileContents.store(out, null);
        }
        System.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "false");
        StartupParameterService.getInstance().refreshSystemProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        assertTrue(AbstractCommandLauncher.registerServerProperties(serverPropertiesFile, StartupParameterService.getInstance()));
        assertEquals("false", System.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }

    @Test
    void registerServerProperties_httpSettingsInFile_areExportedToSystemProperties(@TempDir File tempDir) throws IOException {
        File serverPropertiesFile = new File(tempDir, "symmetric-server.properties");
        Properties fileContents = new Properties();
        fileContents.setProperty(ServerConstants.SERVER_DISALLOW_HTTP_METHODS, "OPTIONS,TRACE");
        fileContents.setProperty(ServerConstants.HTTPS_ENABLE, "false");
        try (FileOutputStream out = new FileOutputStream(serverPropertiesFile)) {
            fileContents.store(out, null);
        }
        assertTrue(AbstractCommandLauncher.registerServerProperties(serverPropertiesFile, StartupParameterService.getInstance()));
        assertEquals("OPTIONS,TRACE", System.getProperty(ServerConstants.SERVER_DISALLOW_HTTP_METHODS));
        assertEquals("false", System.getProperty(ServerConstants.HTTPS_ENABLE));
    }

    @Test
    void registerServerProperties_disallowedHttpMethodsAlreadySet_keepsJvmValue(@TempDir File tempDir) throws IOException {
        File serverPropertiesFile = new File(tempDir, "symmetric-server.properties");
        Properties fileContents = new Properties();
        fileContents.setProperty(ServerConstants.SERVER_DISALLOW_HTTP_METHODS, "OPTIONS,TRACE");
        try (FileOutputStream out = new FileOutputStream(serverPropertiesFile)) {
            fileContents.store(out, null);
        }
        System.setProperty(ServerConstants.SERVER_DISALLOW_HTTP_METHODS, "OPTIONS");
        StartupParameterService.getInstance().refreshSystemProperty(ServerConstants.SERVER_DISALLOW_HTTP_METHODS);
        assertTrue(AbstractCommandLauncher.registerServerProperties(serverPropertiesFile, StartupParameterService.getInstance()));
        assertEquals("OPTIONS", System.getProperty(ServerConstants.SERVER_DISALLOW_HTTP_METHODS));
    }

    @Test
    void registerServerProperties_missingFile_returnsFalse(@TempDir File tempDir) {
        File serverPropertiesFile = new File(tempDir, "does-not-exist.properties");
        assertFalse(AbstractCommandLauncher.registerServerProperties(serverPropertiesFile, StartupParameterService.getInstance()));
        assertNull(System.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }

    @Test
    void setContainerized_true_setsContainerModeEnabledSystemProperty() {
        LAUNCHER.setContainerized(true);
        assertEquals("true", System.getProperty(ServerConstants.CONTAINER_MODE_ENABLED));
    }

    @Test
    void setContainerized_false_setsContainerModeEnabledSystemPropertyToFalse() {
        LAUNCHER.setContainerized(false);
        assertEquals("false", System.getProperty(ServerConstants.CONTAINER_MODE_ENABLED));
    }

    @Test
    void setContainerized_true_updatesIsContainerized() {
        LAUNCHER.setContainerized(true);
        assertTrue(LAUNCHER.isContainerized());
    }

    @Test
    void setContainerized_false_updatesIsContainerized() {
        LAUNCHER.setContainerized(false);
        assertFalse(LAUNCHER.isContainerized());
    }

    @Test
    void setContainerized_true_neverTouchesClusterLockingEnabled() {
        LAUNCHER.setContainerized(true);
        assertNull(System.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }

    @Test
    void setContainerized_true_doesNotOverrideExplicitlySetClusterLockingEnabled() {
        System.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "false");
        LAUNCHER.setContainerized(true);
        assertEquals("false", System.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }
}
