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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.model.StartupParameter;
import org.jumpmind.symmetric.model.StartupParameter.Source;
import org.jumpmind.symmetric.model.StartupParameter.Type;
import org.junit.jupiter.api.Test;

class StartupParameterServiceTest {
    @Test
    void testResolve_valuePresentInAllThreeLayers_jvmSystemPropertyWins() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "jvm-value");
        TypedProperties jvm = new TypedProperties();
        jvm.setProperty(ServerConstants.CLUSTER_SERVER_ID, "jvm-value");
        Map<String, String> env = Map.of("SYM_CLUSTER_SERVER_ID", "env-value");
        StartupParameterService service = new StartupParameterService(merged, jvm, env, Map.of());
        StartupParameter parameter = service.getParameter(ServerConstants.CLUSTER_SERVER_ID);
        assertEquals("jvm-value", parameter.rawValue());
        assertEquals(Source.JVM_SYSTEM_PROPERTY, parameter.source());
    }

    @Test
    void testResolve_envVarOnly_environmentVariableSource() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "env-value");
        Map<String, String> env = Map.of("SYM_CLUSTER_PARTITION_ID", "env-value");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), env, Map.of());
        StartupParameter parameter = service.getParameter(ServerConstants.CLUSTER_PARTITION_ID);
        assertEquals(Source.ENVIRONMENT_VARIABLE, parameter.source());
    }

    @Test
    void testResolve_jvmOnlyKeyNeverInPropertiesFile_stillResolvesFromJvmProperties() {
        TypedProperties jvm = new TypedProperties();
        jvm.setProperty(SecurityConstants.SYSPROP_KEYSTORE, "/opt/sym/security/keystore");
        StartupParameterService service = new StartupParameterService(new TypedProperties(), jvm, Map.of(), Map.of());
        StartupParameter parameter = service.getParameter(SecurityConstants.SYSPROP_KEYSTORE);
        assertEquals("/opt/sym/security/keystore", parameter.rawValue());
        assertEquals(Source.JVM_SYSTEM_PROPERTY, parameter.source());
        assertEquals("/opt/sym/security/keystore", service.getString(SecurityConstants.SYSPROP_KEYSTORE));
    }

    @Test
    void testResolve_envVarOnlyKeyNeverInPropertiesFile_stillResolvesFromEnvironmentVariables() {
        Map<String, String> env = Map.of("SYM_SOME_JVM_ONLY_KEY", "env-only-value");
        StartupParameterService service = new StartupParameterService(new TypedProperties(), new TypedProperties(), env, Map.of());
        StartupParameter parameter = service.getParameter("some.jvm.only.key");
        assertEquals("env-only-value", parameter.rawValue());
        assertEquals(Source.ENVIRONMENT_VARIABLE, parameter.source());
    }

    @Test
    void testResolve_fileOnly_symmetricPropertiesFileSource() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_JCS_PORT, "1234");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        StartupParameter parameter = service.getParameter(ServerConstants.CLUSTER_JCS_PORT);
        assertEquals(Source.SYMMETRIC_PROPERTIES_FILE, parameter.source());
    }

    @Test
    void testResolve_noValueAnywhere_defaultSourceAndDefaultValue() {
        StartupParameterService service = new StartupParameterService(new TypedProperties(), new TypedProperties(), Map.of(), Map.of());
        StartupParameter parameter = service.getParameter(ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES);
        assertEquals(Source.DEFAULT, parameter.source());
        assertEquals(parameter.defaultValue(), parameter.asString());
    }

    @Test
    void testResolve_valueMatchesDefault_defaultSource() {
        String key = ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES;
        String defaultValue = ParameterConstants.getParameterMetaData().get(key).getDefaultValue();
        TypedProperties merged = new TypedProperties();
        merged.setProperty(key, defaultValue);
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals(Source.DEFAULT, service.getParameter(key).source());
    }

    @Test
    void testGetInt_malformedValue_logsAndFallsBackToCallerDefault() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_JCS_PORT, "not-a-number");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals(99, service.getInt(ServerConstants.CLUSTER_JCS_PORT, 99));
    }

    @Test
    void testGetInt_unknownKey_returnsCallerDefault() {
        StartupParameterService service = new StartupParameterService(new TypedProperties(), new TypedProperties(), Map.of(), Map.of());
        assertEquals(42, service.getInt("some.unregistered.key", 42));
    }

    @Test
    void testIs_valueOne_returnsTrue() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "1");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertTrue(service.is(ParameterConstants.CLUSTER_LOCKING_ENABLED, false));
    }

    @Test
    void testIs_noValue_returnsCallerDefault() {
        StartupParameterService service = new StartupParameterService(new TypedProperties(), new TypedProperties(), Map.of(), Map.of());
        assertFalse(service.is("some.unregistered.flag", false));
        assertTrue(service.is("some.unregistered.flag", true));
    }

    @Test
    void testGetDouble_validValue() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty("some.decimal.param", "3.14");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals(3.14, service.getDouble("some.decimal.param", 0.0));
    }

    @Test
    void testGetAllParameters_returnsEveryKeyFromMergedProperties() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.HTTP_PORT, "31415");
        merged.setProperty(ServerConstants.HTTPS_PORT, "31417");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        Map<String, StartupParameter> all = service.getAllParameters();
        assertTrue(all.containsKey(ServerConstants.HTTP_PORT));
        assertTrue(all.containsKey(ServerConstants.HTTPS_PORT));
    }

    @Test
    void testAsTypedProperties_reflectsMergedProperties() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.HTTP_PORT, "31415");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals("31415", service.asTypedProperties().getProperty(ServerConstants.HTTP_PORT));
    }

    @Test
    void testAsTypedProperties_includesJvmOnlyKeyNeverInPropertiesFile() {
        TypedProperties jvm = new TypedProperties();
        jvm.setProperty(SecurityConstants.SYSPROP_KEYSTORE, "/opt/sym/security/keystore");
        StartupParameterService service = new StartupParameterService(new TypedProperties(), jvm, Map.of(), Map.of());
        assertEquals("/opt/sym/security/keystore", service.asTypedProperties().getProperty(SecurityConstants.SYSPROP_KEYSTORE));
    }

    @Test
    void testRefresh_withoutFactory_returnsFalse() {
        StartupParameterService service = new StartupParameterService(new TypedProperties());
        assertFalse(service.refresh());
    }

    @Test
    void testRefreshSystemProperty_picksUpNewlySetSystemProperty_andTagsJvmSource() {
        String key = "some.cli.provided.key";
        StartupParameterService service = new StartupParameterService(new TypedProperties(), new TypedProperties(), Map.of(), Map.of());
        assertNull(service.getString(key));
        try {
            System.setProperty(key, "cli-value");
            service.refreshSystemProperty(key);
            assertEquals("cli-value", service.getString(key));
            assertEquals(Source.JVM_SYSTEM_PROPERTY, service.getParameter(key).source());
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void testConstructor_factoryWithKnownFileSources_tagsKeyWithGivenSource() {
        TypedProperties fileProperties = new TypedProperties();
        fileProperties.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "configured-partition-id");
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        when(factory.reload()).thenReturn(fileProperties);
        StartupParameterService service = new StartupParameterService(factory,
                Map.of(ServerConstants.CLUSTER_PARTITION_ID, Source.ENGINE_PROPERTIES_FILE), Map.of());
        StartupParameter parameter = service.getParameter(ServerConstants.CLUSTER_PARTITION_ID);
        assertEquals("configured-partition-id", parameter.rawValue());
        assertEquals(Source.ENGINE_PROPERTIES_FILE, parameter.source());
    }

    @Test
    void testConstructor_factoryWithKnownFileSources_refreshStillWorksThroughFactory() {
        TypedProperties first = new TypedProperties();
        first.setProperty(ServerConstants.HTTP_PORT, "31415");
        TypedProperties second = new TypedProperties();
        second.setProperty(ServerConstants.HTTP_PORT, "8080");
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        when(factory.reload()).thenReturn(first).thenReturn(second);
        StartupParameterService service = new StartupParameterService(factory, Map.of(), Map.of());
        assertEquals("31415", service.getString(ServerConstants.HTTP_PORT));
        assertTrue(service.refresh());
        assertEquals("8080", service.getString(ServerConstants.HTTP_PORT));
    }

    @Test
    void testDumpAsText_masksEncryptedAndKeystorePasswordValues() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ParameterConstants.DB_PASSWORD, "super-secret");
        TypedProperties jvm = new TypedProperties();
        jvm.setProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD, "keystore-secret");
        merged.setProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD, "keystore-secret");
        StartupParameterService service = new StartupParameterService(merged, jvm, Map.of(), Map.of());
        String dump = service.dumpAsText();
        assertFalse(dump.contains("super-secret"));
        assertFalse(dump.contains("keystore-secret"));
        assertTrue(dump.contains(ParameterConstants.REDACTED));
    }

    @Test
    void testDumpAsText_masksSymOptionsEnvironmentVariableValue() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty("options", "-Dfile.encoding=utf-8 -Djavax.net.ssl.keyStorePassword=wrapper-secret");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        String dump = service.dumpAsText();
        assertFalse(dump.contains("wrapper-secret"));
        assertTrue(dump.contains(ParameterConstants.REDACTED));
    }

    @Test
    void testMatchesDefault() {
        StartupParameter matching = new StartupParameter("key", Type.STRING, "same", "same", Source.DEFAULT);
        StartupParameter differing = new StartupParameter("key", Type.STRING, "raw", "default", Source.SYMMETRIC_PROPERTIES_FILE);
        assertTrue(matching.matchesDefault());
        assertFalse(differing.matchesDefault());
    }

    @Test
    void testStartupParameter_asIntWithMalformedValue_fallsBackToZero() {
        StartupParameter parameter = new StartupParameter("key", Type.INT, "not-a-number", null, Source.SYMMETRIC_PROPERTIES_FILE);
        assertEquals(0, parameter.asInt());
    }

    @Test
    void testStartupParameter_asBoolean_valueOne_returnsTrue() {
        StartupParameter parameter = new StartupParameter("key", Type.BOOLEAN, "1", null, Source.SYMMETRIC_PROPERTIES_FILE);
        assertTrue(parameter.asBoolean());
    }

    @Test
    void testConstructor_typedPropertiesOnly_usesLiveJvmAndEnvironment() {
        StartupParameterService service = new StartupParameterService(new TypedProperties());
        assertNotNull(service.getAllParameters());
    }

    @Test
    void testGetAllParameters_databaseOverridableParameterMatchesDefault_excludedButStillIndividuallyResolvable() {
        String key = ParameterConstants.REGISTRATION_AUTO_CREATE_GROUP_LINK;
        String defaultValue = ParameterConstants.getParameterMetaData().get(key).getDefaultValue();
        TypedProperties merged = new TypedProperties();
        merged.setProperty(key, defaultValue);
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertFalse(service.getAllParameters().containsKey(key));
        assertEquals(Source.DEFAULT, service.getParameter(key).source());
    }

    @Test
    void testGetAllParameters_databaseOverridableParameterOverridden_included() {
        String key = ParameterConstants.REGISTRATION_AUTO_CREATE_GROUP_LINK;
        TypedProperties merged = new TypedProperties();
        merged.setProperty(key, "false");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertTrue(service.getAllParameters().containsKey(key));
    }

    @Test
    void testGetAllParameters_nonDatabaseOverridableParameterMatchesDefault_stillIncluded() {
        String key = ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES;
        String defaultValue = ParameterConstants.getParameterMetaData().get(key).getDefaultValue();
        TypedProperties merged = new TypedProperties();
        merged.setProperty(key, defaultValue);
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertTrue(service.getAllParameters().containsKey(key));
    }

    @Test
    void testGetAllParameters_unrecognizedCustomParameter_stillIncluded() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty("my.custom.script.parameter", "custom-value");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertTrue(service.getAllParameters().containsKey("my.custom.script.parameter"));
    }

    @Test
    void testConstructor_supplementalParameterMetaData_resolvesDefaultAndDatabaseOverridableFlagFromSupplement() {
        String key = "as400.journal.library";
        ParameterMetaData metaData = new ParameterMetaData();
        metaData.setKey(key);
        metaData.setDefaultValue("SYM");
        metaData.setDatabaseOverridable(true);
        TypedProperties merged = new TypedProperties();
        merged.setProperty(key, "SYM");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of(),
                Map.of(key, metaData));
        StartupParameter parameter = service.getParameter(key);
        assertEquals("SYM", parameter.defaultValue());
        assertEquals(Source.DEFAULT, parameter.source());
        assertFalse(service.getAllParameters().containsKey(key));
    }

    @Test
    void testConstructor_withoutSupplementalParameterMetaData_unknownKeyHasNullDefaultAndPropertiesFileSource() {
        String key = "as400.journal.library";
        TypedProperties merged = new TypedProperties();
        merged.setProperty(key, "SYM");
        StartupParameterService service = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        StartupParameter parameter = service.getParameter(key);
        assertNull(parameter.defaultValue());
        assertEquals(Source.SYMMETRIC_PROPERTIES_FILE, parameter.source());
    }
}
