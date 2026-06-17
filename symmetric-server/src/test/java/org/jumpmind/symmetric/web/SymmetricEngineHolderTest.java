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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


class SymmetricEngineHolderTest {

    @TempDir
    File tempDir;

    @Nested
    class FindRegistrationStarter {
        private File createPropertiesFile(String name, String registrationUrl, String syncUrl) throws IOException {
            File file = new File(tempDir, name + ".properties");
            Properties props = new Properties();
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

        @Test
        void testFindsRegistrationNodeWithBlankUrl() throws IOException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Set<SymmetricEngineStarter> starters = new LinkedHashSet<>();
            File regFile = createPropertiesFile("corp", "", "http://localhost:31415/sync/corp");
            File clientFile = createPropertiesFile("store", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store");
            starters.add(new SymmetricEngineStarter(clientFile.getAbsolutePath(), holder));
            starters.add(new SymmetricEngineStarter(regFile.getAbsolutePath(), holder));
            SymmetricEngineStarter result = holder.findRegistrationStarter(starters);
            assertNotNull(result);
            assertTrue(result.getPropertiesFile().contains("corp"));
        }

        @Test
        void testFindsRegistrationNodeWhenUrlEqualsSyncUrl() throws IOException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Set<SymmetricEngineStarter> starters = new LinkedHashSet<>();
            File regFile = createPropertiesFile("corp", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/corp");
            File clientFile = createPropertiesFile("store", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store");
            starters.add(new SymmetricEngineStarter(clientFile.getAbsolutePath(), holder));
            starters.add(new SymmetricEngineStarter(regFile.getAbsolutePath(), holder));
            SymmetricEngineStarter result = holder.findRegistrationStarter(starters);
            assertNotNull(result);
            assertTrue(result.getPropertiesFile().contains("corp"));
        }

        @Test
        void testReturnsNullWhenNoRegistrationNode() throws IOException {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            Set<SymmetricEngineStarter> starters = new LinkedHashSet<>();
            File client1 = createPropertiesFile("store1", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store1");
            File client2 = createPropertiesFile("store2", "http://localhost:31415/sync/corp", "http://localhost:31415/sync/store2");
            starters.add(new SymmetricEngineStarter(client1.getAbsolutePath(), holder));
            starters.add(new SymmetricEngineStarter(client2.getAbsolutePath(), holder));
            SymmetricEngineStarter result = holder.findRegistrationStarter(starters);
            assertNull(result);
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
            try (var out = new java.io.FileOutputStream(file1)) { props.store(out, null); }
            try (var out = new java.io.FileOutputStream(file2)) { props.store(out, null); }

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
            try (var out = new java.io.FileOutputStream(file1)) { props1.store(out, null); }
            try (var out = new java.io.FileOutputStream(file2)) { props2.store(out, null); }

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
            holder.setAutoCreate(false);
            holder.start();
            assertFalse(holder.areEnginesStarting());
        }

        @Test
        void areEnginesStartingTrueWhenStartersRemain() {
            SymmetricEngineHolder holder = new SymmetricEngineHolder();
            holder.setAutoCreate(false);
            holder.start();
            holder.getEnginesStarting().add(new SymmetricEngineStarter("fake.properties", holder));
            assertTrue(holder.areEnginesStarting());
        }
    }
    
}
