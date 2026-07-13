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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.cache.ClusteredCacheManager;
import org.jumpmind.symmetric.cache.ClusteredEngineState;
import org.jumpmind.symmetric.cache.IClusteredCacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

class SymmetricEngineHolderTest {
    @TempDir
    File tempDir;

    private File createPropertiesFile(String name, String registrationUrl, String syncUrl) throws IOException {
        File file = new File(tempDir, name + ".properties");
        Properties props = new Properties();
        props.setProperty(ParameterConstants.ENGINE_NAME, name);
        if (registrationUrl != null) {
            props.setProperty(ParameterConstants.REGISTRATION_URL, registrationUrl);
        }
        if (syncUrl != null) {
            props.setProperty(ParameterConstants.SYNC_URL, syncUrl);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, null);
        }
        return file;
    }

    @Nested
    class FilterEngineStartersByRegistrationType {
        @Test
        void testFindsRegistrationNodeWithBlankUrl() throws IOException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Set<SymmetricEngineStarter> starters = new LinkedHashSet<>();
            File regFile = createPropertiesFile("corp", "", "http://localhost:31415/sync/corp");
            File clientFile = createPropertiesFile("store", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store");
            starters.add(new SymmetricEngineStarter(clientFile.getAbsolutePath(), holder));
            starters.add(new SymmetricEngineStarter(regFile.getAbsolutePath(), holder));
            Set<SymmetricEngineStarter> result = holder.filterEngineStartersByRegistrationType(true, starters);
            assertEquals(1, result.size());
            assertTrue(result.iterator().next().getPropertiesFile().contains("corp"));
        }

        @Test
        void testFindsRegistrationNodeWhenUrlEqualsSyncUrl() throws IOException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Set<SymmetricEngineStarter> starters = new LinkedHashSet<>();
            File regFile = createPropertiesFile("corp", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/corp");
            File clientFile = createPropertiesFile("store", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store");
            starters.add(new SymmetricEngineStarter(clientFile.getAbsolutePath(), holder));
            starters.add(new SymmetricEngineStarter(regFile.getAbsolutePath(), holder));
            Set<SymmetricEngineStarter> result = holder.filterEngineStartersByRegistrationType(true, starters);
            assertEquals(1, result.size());
            assertTrue(result.iterator().next().getPropertiesFile().contains("corp"));
        }

        @Test
        void testReturnsEmptyWhenNoRegistrationNode() throws IOException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Set<SymmetricEngineStarter> starters = new LinkedHashSet<>();
            File client1 = createPropertiesFile("store1", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store1");
            File client2 = createPropertiesFile("store2", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store2");
            starters.add(new SymmetricEngineStarter(client1.getAbsolutePath(), holder));
            starters.add(new SymmetricEngineStarter(client2.getAbsolutePath(), holder));
            Set<SymmetricEngineStarter> result = holder.filterEngineStartersByRegistrationType(true, starters);
            assertTrue(result.isEmpty());
        }

        @Test
        void nonRegistrationFilterReturnsOnlyClientEngines() throws IOException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Set<SymmetricEngineStarter> starters = new LinkedHashSet<>();
            File regFile = createPropertiesFile("corp", "", "http://localhost:31415/sync/corp");
            File clientFile = createPropertiesFile("store", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store");
            starters.add(new SymmetricEngineStarter(regFile.getAbsolutePath(), holder));
            starters.add(new SymmetricEngineStarter(clientFile.getAbsolutePath(), holder));
            Set<SymmetricEngineStarter> result = holder.filterEngineStartersByRegistrationType(false, starters);
            assertEquals(1, result.size());
            assertTrue(result.iterator().next().getPropertiesFile().contains("store"));
        }
    }

    @Nested
    class GetEngineName {
        @ParameterizedTest
        @CsvSource({
                "my-engine, , , my-engine",
                ", corp, store-001, corp-store-001",
                ", corp, corp, corp",
                ", my group, store 1, my_group-store_1"
        })
        void testEngineNameGeneration(String engineName, String groupId, String externalId, String expected) {
            Properties props = new Properties();
            if (engineName != null) {
                props.setProperty(ParameterConstants.ENGINE_NAME, engineName);
            }
            if (groupId != null) {
                props.setProperty(ParameterConstants.NODE_GROUP_ID, groupId);
            }
            if (externalId != null) {
                props.setProperty(ParameterConstants.EXTERNAL_ID, externalId);
            }
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertEquals(expected, holder.getEngineName(props));
        }
    }

    @Nested
    class ValidateRequiredProperties {
        @Test
        void throwsWhenExternalIdMissing() {
            Properties props = new Properties();
            props.setProperty(ParameterConstants.NODE_GROUP_ID, "corp");
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertThrows(IllegalStateException.class, () -> holder.validateRequiredProperties(props));
        }

        @Test
        void throwsWhenGroupIdMissing() {
            Properties props = new Properties();
            props.setProperty(ParameterConstants.EXTERNAL_ID, "store-001");
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertThrows(IllegalStateException.class, () -> holder.validateRequiredProperties(props));
        }

        @Test
        void throwsWhenDbDriverMissing() {
            Properties props = new Properties();
            props.setProperty(ParameterConstants.EXTERNAL_ID, "store-001");
            props.setProperty(ParameterConstants.NODE_GROUP_ID, "corp");
            props.setProperty(ParameterConstants.ENGINE_NAME, "test");
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertThrows(IllegalStateException.class, () -> holder.validateRequiredProperties(props));
        }

        @Test
        void defaultsRegistrationUrlToBlankWhenMissing() {
            Properties props = new Properties();
            props.setProperty(ParameterConstants.EXTERNAL_ID, "store-001");
            props.setProperty(ParameterConstants.NODE_GROUP_ID, "corp");
            props.setProperty(ParameterConstants.ENGINE_NAME, "test");
            props.setProperty(DataSourceProperties.DB_POOL_DRIVER, "org.postgresql.Driver");
            props.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:postgresql://localhost/test");
            props.setProperty(DataSourceProperties.DB_POOL_USER, "postgres");
            props.setProperty(DataSourceProperties.DB_POOL_PASSWORD, "postgres");
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.validateRequiredProperties(props);
            assertEquals("", props.getProperty(ParameterConstants.REGISTRATION_URL));
        }
    }

    @Nested
    class ValidateEngineFiles {
        @Test
        void throwsOnDuplicateDbConnections() throws IOException {
            File file1 = new File(tempDir, "engine1.properties");
            File file2 = new File(tempDir, "engine2.properties");
            Properties props = new Properties();
            props.setProperty(DataSourceProperties.DB_POOL_USER, "postgres");
            props.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:postgresql://localhost/mydb");
            try (var out = new java.io.FileOutputStream(file1)) {
                props.store(out, null);
            }
            try (var out = new java.io.FileOutputStream(file2)) {
                props.store(out, null);
            }
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertThrows(SymmetricException.class,
                    () -> holder.validateEngineFiles(new File[] { file1, file2 }));
        }

        @Test
        void allowsDifferentDbConnections() throws IOException {
            File file1 = new File(tempDir, "engine1.properties");
            File file2 = new File(tempDir, "engine2.properties");
            Properties props1 = new Properties();
            props1.setProperty(DataSourceProperties.DB_POOL_USER, "postgres");
            props1.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:postgresql://localhost:5432/db1");
            Properties props2 = new Properties();
            props2.setProperty(DataSourceProperties.DB_POOL_USER, "postgres");
            props2.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:postgresql://localhost:5433/db2");
            try (var out = new java.io.FileOutputStream(file1)) {
                props1.store(out, null);
            }
            try (var out = new java.io.FileOutputStream(file2)) {
                props2.store(out, null);
            }
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertDoesNotThrow(
                    () -> holder.validateEngineFiles(new File[] { file1, file2 }));
        }
    }

    @Nested
    class EngineStateChecks {
        @Test
        void areEnginesStartingTrueBeforeHolderStarts() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertTrue(holder.areEnginesStarting());
        }

        @Test
        void areEnginesConfiguredFalseWhenEmpty() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertFalse(holder.areEnginesConfigured());
        }

        @Test
        void areEnginesInErrorFalseWhenEmpty() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertFalse(holder.areEnginesInError());
        }

        @Test
        void areEnginesStartingFalseAfterHolderStarts() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.setAutoDiscoverEngines(false);
            holder.start();
            assertFalse(holder.areEnginesStarting());
        }

        @Test
        void areEnginesStartingTrueWhenStartersRemain() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.setAutoDiscoverEngines(false);
            holder.start();
            holder.getEnginesStarting().add(new SymmetricEngineStarter("fake.properties", holder));
            assertTrue(holder.areEnginesStarting());
        }
    }

    @Nested
    class IsRegistrationEngineStarter {
        @Test
        void returnsFalseWhenPropertiesFileCannotBeRead() {
            // Unreadable/missing file must fail safe to non-registration rather than throw.
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            File missing = new File(tempDir, "does-not-exist.properties");
            SymmetricEngineStarter starter = new SymmetricEngineStarter(missing.getAbsolutePath(), holder);
            assertFalse(starter.isRegistrationEngineStarter());
        }
    }

    @Nested
    class StartAllEngines {
        @Test
        void routesRegistrationEngineThroughBlockingStart() throws IOException {
            // A registration engine (blank registration.url) drives startAllEngines down its
            // "registration engines first" branch and through startEnginesAndWait. The properties
            // file is intentionally incomplete, so engine creation fails fast and no database is
            // required -- we only need to prove the registration path executed and the engine was
            // picked up and processed.
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.setAutoDiscoverEngines(false);
            File regFile = createPropertiesFile("corp", "", "http://localhost:31415/sync/corp");
            holder.getEnginesStarting().add(new SymmetricEngineStarter(regFile.getAbsolutePath(), holder));
            holder.start();
            assertFalse(holder.areEnginesStarting());
            assertTrue(holder.areEnginesInError());
        }
    }

    @Nested
    class DiscoverEngines {
        @Test
        void multiServerModeLoadsPropertiesFileAsStarter() throws IOException {
            // start() -> discoverEngines() multi-server branch -> loadMultiServerEngines happy path
            // (validateEngineFiles + the add loop). Creation fails fast without a database.
            createPropertiesFile("corp", "", "http://localhost:31415/sync/corp");
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.setMultiServerMode(true);
            String previousEnginesDir = System.setProperty(SystemConstants.SYSPROP_ENGINES_DIR, tempDir.getAbsolutePath());
            try {
                holder.start();
                assertFalse(holder.areEnginesStarting());
            } finally {
                restoreEnginesDir(previousEnginesDir);
            }
        }

        @Test
        void multiServerModeWithNoPropertiesFilesLogsNoneFound() {
            // loadMultiServerEngines: directory exists but is empty -> size-unchanged "none found" branch.
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.setMultiServerMode(true);
            String previousEnginesDir = System.setProperty(SystemConstants.SYSPROP_ENGINES_DIR, tempDir.getAbsolutePath());
            try {
                holder.start();
                assertFalse(holder.areEnginesStarting());
            } finally {
                restoreEnginesDir(previousEnginesDir);
            }
        }

        @Test
        void singleServerModeLoadsConfiguredPropertiesFile() throws IOException {
            // start() -> discoverEngines() single-server branch -> loadSingleServerEngine isNotBlank path.
            File props = createPropertiesFile("single", "", "http://localhost:31415/sync/single");
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.setMultiServerMode(false);
            holder.setSingleServerPropertiesFile(props.getAbsolutePath());
            holder.start();
            assertFalse(holder.areEnginesStarting());
        }

        @Test
        void staticEnginesModeSwitchesToStaticState() {
            // start() static branch -> switchToStaticEnginesMode().
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.setStaticEnginesMode(true);
            holder.setAutoDiscoverEngines(false);
            holder.start();
            assertFalse(holder.areEnginesStarting());
        }

        private void restoreEnginesDir(String previousValue) {
            if (previousValue == null) {
                System.clearProperty(SystemConstants.SYSPROP_ENGINES_DIR);
            } else {
                System.setProperty(SystemConstants.SYSPROP_ENGINES_DIR, previousValue);
            }
        }
    }

    @Nested
    class BuildCurrentEngineStateSnapshot {
        private String previousContainerMode;

        @BeforeEach
        void setUp() {
            previousContainerMode = System.getProperty(ServerConstants.CONTAINER_MODE_ENABLED);
        }

        @AfterEach
        void tearDown() {
            if (previousContainerMode == null) {
                System.clearProperty(ServerConstants.CONTAINER_MODE_ENABLED);
            } else {
                System.setProperty(ServerConstants.CONTAINER_MODE_ENABLED, previousContainerMode);
            }
        }

        @Test
        void returnsEmptySnapshotWhenNoEngines() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Map<String, ClusteredEngineState> snapshot = holder.buildCurrentEngineStateSnapshot("server1");
            assertTrue(snapshot.isEmpty());
        }

        @Test
        void containsRunningEnginesInSnapshot() throws IOException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            File props = createPropertiesFile("running1", "", "http://localhost:31415/sync/running1");
            holder.getEnginesStarting().add(new SymmetricEngineStarter(props.getAbsolutePath(), holder));
            Map<String, ClusteredEngineState> snapshot = holder.buildCurrentEngineStateSnapshot("server1");
            assertTrue(snapshot.values().stream().anyMatch(state -> state == ClusteredEngineState.STARTING));
        }

        @Test
        void containsFailedEnginesInSnapshot() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.getEnginesFailed().put("failed-engine", new FailedEngineInfo("failed-engine", "failed.properties", "Test failure"));
            Map<String, ClusteredEngineState> snapshot = holder.buildCurrentEngineStateSnapshot("server1");
            assertTrue(snapshot.containsValue(ClusteredEngineState.FAILED));
        }
    }

    @Nested
    class ParallelEngineStop {
        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void stopDoesNotDeadlock() throws InterruptedException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Thread stopThread = new Thread(holder::stop);
            stopThread.start();
            stopThread.join();
            assertTrue(holder.getEngines().isEmpty());
        }

        @Test
        void clearsMapsAfterStop() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.getEnginesStarting().add(new SymmetricEngineStarter("fake.properties", holder));
            holder.stop();
            assertTrue(holder.getEngines().isEmpty());
            assertTrue(holder.getEnginesStarting().isEmpty());
            assertTrue(holder.getEnginesStartingNames().isEmpty());
            assertTrue(holder.getEnginesFailed().isEmpty());
        }

        @Test
        void handlesEmptyEngineListGracefully() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            assertDoesNotThrow(holder::stop);
        }
    }

    @Nested
    class ContainerModeShutdownThreshold {
        private String previousContainerMode;

        @BeforeEach
        void setUp() {
            previousContainerMode = System.getProperty(ServerConstants.CONTAINER_MODE_ENABLED);
        }

        @AfterEach
        void tearDown() {
            if (previousContainerMode == null) {
                System.clearProperty(ServerConstants.CONTAINER_MODE_ENABLED);
            } else {
                System.setProperty(ServerConstants.CONTAINER_MODE_ENABLED, previousContainerMode);
            }
        }

        @Test
        void tracksTransitionFromRunningToNoEngines() throws Exception {
            System.setProperty(ServerConstants.CONTAINER_MODE_ENABLED, "true");
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            File props = createPropertiesFile("engine1", "", "http://localhost:31415/sync/engine1");
            holder.getEnginesStarting().add(new SymmetricEngineStarter(props.getAbsolutePath(), holder));
            Map<String, ClusteredEngineState> snapshot1 = holder.buildCurrentEngineStateSnapshot("server1");
            assertTrue(snapshot1.values().stream().anyMatch(s -> s == ClusteredEngineState.STARTING));
            holder.getEnginesStarting().clear();
            Map<String, ClusteredEngineState> snapshot2 = holder.buildCurrentEngineStateSnapshot("server1");
            assertTrue(snapshot2.isEmpty());
        }

        @Test
        void onlyShutdownsWhenThresholdExceeded() {
            System.setProperty(ServerConstants.CONTAINER_MODE_ENABLED, "true");
            SymmetricEngineHolder holder = spy(new SymmetricEngineHolder());
            holder.getEnginesStarting().clear();
            holder.buildCurrentEngineStateSnapshot("server1");
            verify(holder, Mockito.never()).stop();
        }
    }
}
