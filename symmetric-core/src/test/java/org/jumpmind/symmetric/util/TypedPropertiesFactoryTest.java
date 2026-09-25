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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.Map;
import java.util.Properties;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TypedPropertiesFactoryTest {
    @AfterEach
    void clearSystemProperties() {
        System.clearProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED);
        System.clearProperty(ServerConstants.CLUSTER_PEER_DISCOVERY);
        System.clearProperty(ServerConstants.CLUSTER_PEER_DISCOVERY_SERVERS);
    }

    @Test
    void testImportJvmEnvVars_setsSystemPropertyWhenEnvVarPresent() {
        TypedPropertiesFactory.importJvmEnvVars(Map.of("SYM_CLUSTER_KEYSTORE_SEED", "seed-value"));
        assertEquals("seed-value", System.getProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED));
    }

    @Test
    void testImportJvmEnvVars_setsClusterPeerDiscoverySystemPropertyWhenEnvVarPresent() {
        TypedPropertiesFactory.importJvmEnvVars(Map.of("SYM_CLUSTER_PEER_DISCOVERY", "udp"));
        assertEquals("udp", System.getProperty(ServerConstants.CLUSTER_PEER_DISCOVERY));
    }

    @Test
    void testImportJvmEnvVars_setsClusterPeerDiscoveryStaticServersSystemPropertyWhenEnvVarPresent() {
        TypedPropertiesFactory.importJvmEnvVars(Map.of("SYM_CLUSTER_PEER_DISCOVERY_STATIC_SERVERS", "sympod1:1101,sympod2:1101"));
        assertEquals("sympod1:1101,sympod2:1101", System.getProperty(ServerConstants.CLUSTER_PEER_DISCOVERY_SERVERS));
    }

    @Test
    void testImportJvmEnvVars_leavesSystemPropertyUnsetWhenEnvVarAbsent() {
        TypedPropertiesFactory.importJvmEnvVars(Map.of());
        assertNull(System.getProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED));
    }

    @Test
    void testImportJvmEnvVars_leavesSystemPropertyUnsetWhenEnvVarBlank() {
        TypedPropertiesFactory.importJvmEnvVars(Map.of("SYM_CLUSTER_KEYSTORE_SEED", " "));
        assertNull(System.getProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED));
    }

    @Test
    void testMergeAndOverrideWithJvmAndEnvironmentVariablesAddVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_PASSWORD", "password")), new TypedProperties());
        assertEquals(3, fileProps.size());
        assertEquals("password", fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithJvmAndEnvironmentVariablesOverrideAndAddVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_USER", "updated-user", "SYM_DB_PASSWORD", "password")), new TypedProperties());
        assertEquals(3, fileProps.size());
        assertEquals("updated-user", fileProps.getProperty("db.user"));
        assertEquals("password", fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithJvmAndEnvironmentVariablesNoAddedVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, false,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_PASSWORD", "password")), new TypedProperties());
        assertEquals(2, fileProps.size());
        assertNull(fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithJvmAndEnvironmentVariablesOverrideAndNoAddedVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, false,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_USER", "updated-user", "SYM_DB_PASSWORD", "password")), new TypedProperties());
        assertEquals(2, fileProps.size());
        assertEquals("updated-user", fileProps.getProperty("db.user"));
        assertEquals(null, fileProps.getProperty("db.password"));
    }

    @Test
    void testOtelEnvVariableAddedAsPropertyKey() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("OTEL_SERVICE_NAME", "my-service")), new TypedProperties());
        assertEquals("my-service", fileProps.getProperty("otel.service.name"));
    }

    @Test
    void testOtelEnvVariableNotAddedWhenNotMissingProperties() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, false,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("OTEL_SERVICE_NAME", "my-service")), new TypedProperties());
        assertNull(fileProps.getProperty("otel.service.name"));
    }

    @Test
    void testOtelEnvVariableOverriddenBySymVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("OTEL_SERVICE_NAME", "otel-name", "SYM_OTEL_SERVICE_NAME", "sym-name")),
                new TypedProperties());
        assertEquals("sym-name", fileProps.getProperty("otel.service.name"));
    }

    @Test
    void testJvmPropertyOverridesEnvironmentVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        TypedProperties jvmProps = new TypedProperties();
        jvmProps.setProperty("db.user", "jvm-user");
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_USER", "env-user")), jvmProps);
        assertEquals("jvm-user", fileProps.getProperty("db.user"));
    }

    @Test
    void testReplaceSystemAndEnvironmentVariables_engineNameToken() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.ENGINE_NAME, "myEngine");
        props.setProperty("some.prop", "prefix-$(engineName)-suffix");
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(props);
        assertEquals("prefix-myEngine-suffix", props.getProperty("some.prop"));
    }

    @Test
    void testReplaceSystemAndEnvironmentVariables_nodeGroupIdToken() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.NODE_GROUP_ID, "corp");
        props.setProperty("some.prop", "group-$(nodeGroupId)");
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(props);
        assertEquals("group-corp", props.getProperty("some.prop"));
    }

    @Test
    void testReplaceSystemAndEnvironmentVariables_externalIdToken() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.EXTERNAL_ID, "ext-001");
        props.setProperty("some.prop", "id=$(externalId)");
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(props);
        assertEquals("id=ext-001", props.getProperty("some.prop"));
    }

    @Test
    void testReplaceSystemAndEnvironmentVariables_syncUrlToken() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.SYNC_URL, "http://host/sync");
        props.setProperty("some.prop", "url=$(syncUrl)");
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(props);
        assertEquals("url=http://host/sync", props.getProperty("some.prop"));
    }

    @Test
    void testReplaceSystemAndEnvironmentVariables_registrationUrlToken() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.REGISTRATION_URL, "http://host/reg");
        props.setProperty("some.prop", "reg=$(registrationUrl)");
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(props);
        assertEquals("reg=http://host/reg", props.getProperty("some.prop"));
    }

    @Test
    void testReplaceSystemAndEnvironmentVariables_noTokensUnchanged() {
        Properties props = new Properties();
        props.setProperty("some.prop", "plain-value");
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(props);
        assertEquals("plain-value", props.getProperty("some.prop"));
    }

    @Test
    void testMergeAndOverrideWithJvmAndEnvironmentVariables_throwsWhenBothEmpty() {
        TypedProperties fileProps = new TypedProperties();
        try {
            TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(fileProps, true,
                    TypedPropertiesFactory.getEnvironmentVariables(Map.of()), new TypedProperties());
            throw new AssertionError("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Property files were not found", e.getMessage());
        }
    }

    @Test
    void testGetPropertiesFile_returnsFilePassedToInit() {
        TypedPropertiesFactory factory = new TypedPropertiesFactory();
        File propertiesFile = new File("some-engine.properties");
        factory.init(propertiesFile, null);
        assertEquals(propertiesFile, factory.getPropertiesFile());
    }

    @Test
    void testGetPropertiesFile_noFilePassedToInit_returnsNull() {
        TypedPropertiesFactory factory = new TypedPropertiesFactory();
        factory.init(null, null);
        assertNull(factory.getPropertiesFile());
    }
}
