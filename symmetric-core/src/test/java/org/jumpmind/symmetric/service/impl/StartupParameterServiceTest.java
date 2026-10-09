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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.model.StartupParameter;
import org.jumpmind.symmetric.model.StartupParameter.Source;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.jumpmind.symmetric.service.IStartupParameterSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StartupParameterServiceTest {
    private static final String SOURCE_KEY = "test.source.supplied.key";
    private static final String SENSITIVE_SOURCE_KEY = "http.jwt.client.token";
    private final IStartupParameterService service = StartupParameterService.getInstance();
    private final List<String> registeredEngineNames = new ArrayList<>();

    @AfterEach
    void unregisterTestEngines() {
        for (String name : registeredEngineNames) {
            service.unregisterEngine(name);
        }
        registeredEngineNames.clear();
        service.unregisterEngine(IStartupParameterService.GLOBAL_ENGINE_NAME);
    }

    @Test
    void getInstance_returnsSameObjectAcrossCalls() {
        assertSame(StartupParameterService.getInstance(), StartupParameterService.getInstance());
    }

    @Test
    void registerEngine_derivesEngineNameFromResolvedProperties_andReturnsResolvedProperties() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(ServerConstants.HTTP_PORT, "31415");
        TypedProperties resolved = registerEngineAndReturnResolvedProperties(properties);
        assertEquals("31415", resolved.getProperty(ServerConstants.HTTP_PORT));
    }

    @Test
    void getParameter_twoRegisteredEngines_resolveIndependently() {
        TypedProperties firstProperties = new TypedProperties();
        firstProperties.setProperty("shared.key", "first-value");
        String first = registerEngine(firstProperties, Map.of());
        TypedProperties secondProperties = new TypedProperties();
        secondProperties.setProperty("shared.key", "second-value");
        String second = registerEngine(secondProperties, Map.of());
        assertEquals("first-value", service.getString(first, "shared.key"));
        assertEquals("second-value", service.getString(second, "shared.key"));
    }

    @Test
    void refreshSystemProperty_updatesEveryRegisteredEngineAndGlobalBucketInOneCall() {
        String key = "test.jvm.cli.flag." + UUID.randomUUID();
        service.registerGlobal(new TypedProperties(), Map.of());
        String first = registerEngine(new TypedProperties(), Map.of());
        String second = registerEngine(new TypedProperties(), Map.of());
        assertNull(service.getString(first, key));
        assertNull(service.getGlobalString(key));
        try {
            System.setProperty(key, "cli-value");
            service.refreshSystemProperty(key);
            assertEquals("cli-value", service.getString(first, key));
            assertEquals("cli-value", service.getString(second, key));
            assertEquals("cli-value", service.getGlobalString(key));
            assertEquals(Source.JVM_SYSTEM_PROPERTY, service.getParameter(first, key).source());
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void registerEngine_duplicateEngineName_overwritesPriorRegistration() {
        String name = "test-engine-" + UUID.randomUUID();
        TypedProperties first = new TypedProperties();
        first.setProperty(ParameterConstants.ENGINE_NAME, name);
        first.setProperty("some.key", "first-value");
        ITypedPropertiesFactory firstFactory = mock(ITypedPropertiesFactory.class);
        when(firstFactory.reload()).thenReturn(first);
        service.registerEngine(firstFactory, Map.of(), Map.of());
        registeredEngineNames.add(name);
        assertEquals("first-value", service.getString(name, "some.key"));
        TypedProperties second = new TypedProperties();
        second.setProperty(ParameterConstants.ENGINE_NAME, name);
        second.setProperty("some.key", "second-value");
        ITypedPropertiesFactory secondFactory = mock(ITypedPropertiesFactory.class);
        when(secondFactory.reload()).thenReturn(second);
        service.registerEngine(secondFactory, Map.of(), Map.of());
        assertEquals("second-value", service.getString(name, "some.key"));
    }

    @Test
    void testRegisterEngine_withSource_appliesValueAndTagsSourceLabel() {
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(SOURCE_KEY, "from-source"));
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
        StartupParameter parameter = service.getParameter(name, SOURCE_KEY);
        assertEquals("from-source", parameter.rawValue());
        assertEquals(Source.EXTERNAL_FILE, parameter.source());
        assertEquals("from-source", service.asTypedProperties(name).getProperty(SOURCE_KEY));
    }

    @Test
    void testRegisterEngine_withSourceAndFileValue_sourceWins() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(SOURCE_KEY, "from-file");
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(SOURCE_KEY, "from-source"));
        String name = registerEngine(properties, Map.of(SOURCE_KEY, Source.ENGINE_PROPERTIES_FILE), Map.of(), source);
        assertEquals("from-source", service.getString(name, SOURCE_KEY));
        assertEquals(Source.EXTERNAL_FILE, service.getParameter(name, SOURCE_KEY).source());
    }

    @Test
    void testRegisterEngine_withSourceAndJvmProperty_sourceWins() {
        String key = "test.source.jvm.key." + UUID.randomUUID();
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(key, "from-source"));
        try {
            System.setProperty(key, "from-jvm");
            service.refreshSystemProperty(key);
            String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
            assertEquals("from-source", service.getString(name, key));
            assertEquals(Source.EXTERNAL_FILE, service.getParameter(name, key).source());
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void testRegisterEngine_withEmptySource_leavesKeyUnset() {
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of());
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
        assertNull(service.getString(name, SOURCE_KEY));
    }

    @Test
    void testRegisterEngine_withNullSourceValue_ignoresKey() {
        Map<String, String> values = new HashMap<>();
        values.put(SOURCE_KEY, null);
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), new FakeStartupParameterSource(values));
        assertNull(service.getString(name, SOURCE_KEY));
    }

    @Test
    void testRegisterEngine_withSource_refreshesSourceWithResolvedProperties() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty("some.location", "from-file");
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of());
        registerEngine(properties, Map.of(), Map.of(), source);
        assertEquals("from-file", source.lastStartupProperties.getProperty("some.location"));
    }

    @Test
    void unregisterEngine_removesBucket_subsequentLookupsReturnGracefulDefaults() {
        String name = registerEngine(new TypedProperties(), Map.of());
        service.unregisterEngine(name);
        registeredEngineNames.remove(name);
        assertNull(service.getString(name, "some.key"));
        assertTrue(service.getAllParameters(name).isEmpty());
    }

    @Test
    void neverRegisteredEngineName_returnsCallerSuppliedDefaultsInsteadOfThrowing() {
        String neverRegistered = "engine-" + UUID.randomUUID();
        assertNull(service.getString(neverRegistered, "some.key"));
        assertEquals("fallback", service.getString(neverRegistered, "some.key", "fallback"));
        assertEquals(7, service.getInt(neverRegistered, "some.key", 7));
        assertFalse(service.is(neverRegistered, "some.key", false));
        assertNull(service.getParameter(neverRegistered, "some.key"));
        assertTrue(service.getAllParameters(neverRegistered).isEmpty());
        assertFalse(service.refresh(neverRegistered));
        assertNotNull(service.asTypedProperties(neverRegistered));
    }

    @Test
    void dumpAsText_excludesDatabaseOverridableParameterAtDefault_butGetParameterAndGetAllParametersStillResolveIt() {
        String key = "test.db.overridable.param";
        ParameterMetaData metaData = new ParameterMetaData();
        metaData.setKey(key);
        metaData.setDefaultValue("the-default");
        metaData.setDatabaseOverridable(true);
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, "the-default");
        String name = registerEngine(properties, Map.of(), Map.of(key, metaData));
        String dump = service.dumpAsText(name);
        assertFalse(dump.contains(key));
        StartupParameter parameter = service.getParameter(name, key);
        assertNotNull(parameter);
        assertEquals(Source.DEFAULT, parameter.source());
        assertTrue(service.getAllParameters(name).containsKey(key));
    }

    @Test
    void dumpAsText_databaseOverridableParameterOverridden_included() {
        String key = "test.db.overridable.param.overridden";
        ParameterMetaData metaData = new ParameterMetaData();
        metaData.setKey(key);
        metaData.setDefaultValue("the-default");
        metaData.setDatabaseOverridable(true);
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, "not-the-default");
        String name = registerEngine(properties, Map.of(), Map.of(key, metaData));
        assertTrue(service.dumpAsText(name).contains(key));
    }

    @Test
    void dumpAsText_masksEncryptedAndKeystorePasswordValues() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(ParameterConstants.DB_PASSWORD, "super-secret");
        properties.setProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD, "keystore-secret");
        String name = registerEngine(properties, Map.of());
        String dump = service.dumpAsText(name);
        assertFalse(dump.contains("super-secret"));
        assertFalse(dump.contains("keystore-secret"));
        assertTrue(dump.contains(ParameterConstants.REDACTED));
    }

    @Test
    void dumpAsText_masksSymOptionsEnvironmentVariableValue() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty("options", "-Dfile.encoding=utf-8 -Djavax.net.ssl.keyStorePassword=wrapper-secret");
        String name = registerEngine(properties, Map.of());
        String dump = service.dumpAsText(name);
        assertFalse(dump.contains("wrapper-secret"));
        assertTrue(dump.contains(ParameterConstants.REDACTED));
    }

    @Test
    void testDumpAsText_withSourceSuppliedRedactedKey_masksValue() {
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(SENSITIVE_SOURCE_KEY, "header.payload.signature"));
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
        String dump = service.dumpAsText(name);
        assertFalse(dump.contains("header.payload.signature"));
        assertTrue(dump.contains(SENSITIVE_SOURCE_KEY + "=" + ParameterConstants.REDACTED));
        assertTrue(service.getParameter(name, SENSITIVE_SOURCE_KEY).isSensitive());
    }

    @Test
    void getParameter_keystorePasswordSystemProperty_isSensitive() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD, "super-secret");
        String name = registerEngine(properties, Map.of());
        assertTrue(service.getParameter(name, SecurityConstants.SYSPROP_KEYSTORE_PASSWORD).isSensitive());
    }

    @Test
    void getParameter_ordinaryStringParameter_isNotSensitive() {
        String key = "my.custom.script.parameter";
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, "custom-value");
        String name = registerEngine(properties, Map.of());
        assertFalse(service.getParameter(name, key).isSensitive());
    }

    @Test
    void getParameter_valueMatchesJvmSystemProperty_taggedAsJvmSource() {
        String key = "java.io.tmpdir";
        String value = System.getProperty(key);
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, value);
        String name = registerEngine(properties, Map.of());
        StartupParameter parameter = service.getParameter(name, key);
        assertEquals(value, parameter.rawValue());
        assertEquals(Source.JVM_SYSTEM_PROPERTY, parameter.source());
    }

    @Test
    void getParameter_noValueAnywhere_defaultSourceAndDefaultValue() {
        String name = registerEngine(new TypedProperties(), Map.of());
        StartupParameter parameter = service.getParameter(name, ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES);
        assertEquals(Source.DEFAULT, parameter.source());
        assertEquals(parameter.defaultValue(), parameter.asString());
    }

    @Test
    void getParameter_valueMatchesDefault_defaultSource() {
        String key = ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES;
        String defaultValue = ParameterConstants.getParameterMetaData().get(key).getDefaultValue();
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, defaultValue);
        String name = registerEngine(properties, Map.of());
        assertEquals(Source.DEFAULT, service.getParameter(name, key).source());
    }

    @Test
    void getParameter_knownFileSource_tagsKeyWithGivenSource() {
        String key = ServerConstants.CLUSTER_PARTITION_ID;
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, "configured-partition-id");
        String name = registerEngine(properties, Map.of(key, Source.ENGINE_PROPERTIES_FILE));
        StartupParameter parameter = service.getParameter(name, key);
        assertEquals("configured-partition-id", parameter.rawValue());
        assertEquals(Source.ENGINE_PROPERTIES_FILE, parameter.source());
    }

    @Test
    void getParameter_unknownFileSource_defaultsToSymmetricPropertiesFileSource() {
        String key = "as400.journal.library";
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, "SYM");
        String name = registerEngine(properties, Map.of());
        StartupParameter parameter = service.getParameter(name, key);
        assertNull(parameter.defaultValue());
        assertEquals(Source.SYMMETRIC_PROPERTIES_FILE, parameter.source());
    }

    @Test
    void registerEngine_supplementalParameterMetaData_resolvesDefaultAndDatabaseOverridableFlagFromSupplement() {
        String key = "as400.journal.library";
        ParameterMetaData metaData = new ParameterMetaData();
        metaData.setKey(key);
        metaData.setDefaultValue("SYM");
        metaData.setDatabaseOverridable(true);
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, "SYM");
        String name = registerEngine(properties, Map.of(), Map.of(key, metaData));
        StartupParameter parameter = service.getParameter(name, key);
        assertEquals("SYM", parameter.defaultValue());
        assertEquals(Source.DEFAULT, parameter.source());
        assertTrue(service.getAllParameters(name).containsKey(key));
        assertFalse(service.dumpAsText(name).contains(key));
    }

    @Test
    void getInt_malformedValue_fallsBackToCallerDefault() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(ServerConstants.CLUSTER_JCS_PORT, "not-a-number");
        String name = registerEngine(properties, Map.of());
        assertEquals(99, service.getInt(name, ServerConstants.CLUSTER_JCS_PORT, 99));
    }

    @Test
    void getInt_unknownKey_returnsCallerDefault() {
        String name = registerEngine(new TypedProperties(), Map.of());
        assertEquals(42, service.getInt(name, "some.unregistered.key", 42));
    }

    @Test
    void is_valueOne_returnsTrue() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "1");
        String name = registerEngine(properties, Map.of());
        assertTrue(service.is(name, ParameterConstants.CLUSTER_LOCKING_ENABLED, false));
    }

    @Test
    void is_noValue_returnsCallerDefault() {
        String name = registerEngine(new TypedProperties(), Map.of());
        assertFalse(service.is(name, "some.unregistered.flag", false));
        assertTrue(service.is(name, "some.unregistered.flag", true));
    }

    @Test
    void getDouble_validValue() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty("some.decimal.param", "3.14");
        String name = registerEngine(properties, Map.of());
        assertEquals(3.14, service.getDouble(name, "some.decimal.param", 0.0));
    }

    @Test
    void getAllParameters_includesUnrecognizedCustomParameter() {
        String key = "my.custom.script.parameter";
        TypedProperties properties = new TypedProperties();
        properties.setProperty(key, "custom-value");
        String name = registerEngine(properties, Map.of());
        assertTrue(service.getAllParameters(name).containsKey(key));
    }

    @Test
    void asTypedProperties_reflectsEngineMergedProperties() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(ServerConstants.HTTP_PORT, "31415");
        String name = registerEngine(properties, Map.of());
        assertEquals("31415", service.asTypedProperties(name).getProperty(ServerConstants.HTTP_PORT));
    }

    @Test
    void refresh_reReadsFromFactory_returnsTrueWhenValueChanged() {
        String name = "test-engine-" + UUID.randomUUID();
        TypedProperties first = new TypedProperties();
        first.setProperty(ParameterConstants.ENGINE_NAME, name);
        first.setProperty(ServerConstants.HTTP_PORT, "31415");
        TypedProperties second = new TypedProperties();
        second.setProperty(ParameterConstants.ENGINE_NAME, name);
        second.setProperty(ServerConstants.HTTP_PORT, "8080");
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        when(factory.reload()).thenReturn(first).thenReturn(second);
        service.registerEngine(factory, Map.of(), Map.of());
        registeredEngineNames.add(name);
        assertEquals("31415", service.getString(name, ServerConstants.HTTP_PORT));
        assertTrue(service.refresh(name));
        assertEquals("8080", service.getString(name, ServerConstants.HTTP_PORT));
    }

    @Test
    void testRefresh_withSource_keepsValueAndReportsNoChange() {
        String name = "test-engine-" + UUID.randomUUID();
        TypedProperties first = new TypedProperties();
        first.setProperty(ParameterConstants.ENGINE_NAME, name);
        TypedProperties second = new TypedProperties();
        second.setProperty(ParameterConstants.ENGINE_NAME, name);
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        when(factory.reload()).thenReturn(first).thenReturn(second);
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(SOURCE_KEY, "from-source"));
        service.registerEngine(factory, Map.of(), Map.of(), source);
        registeredEngineNames.add(name);
        assertFalse(service.refresh(name));
        assertEquals("from-source", service.getString(name, SOURCE_KEY));
        assertEquals(Source.EXTERNAL_FILE, service.getParameter(name, SOURCE_KEY).source());
    }

    @Test
    void testRefresh_withSource_refreshesSourceWithReloadedProperties() {
        String name = "test-engine-" + UUID.randomUUID();
        TypedProperties first = new TypedProperties();
        first.setProperty(ParameterConstants.ENGINE_NAME, name);
        TypedProperties second = new TypedProperties();
        second.setProperty(ParameterConstants.ENGINE_NAME, name);
        second.setProperty("some.location", "reloaded");
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        when(factory.reload()).thenReturn(first).thenReturn(second);
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of());
        service.registerEngine(factory, Map.of(), Map.of(), source);
        registeredEngineNames.add(name);
        service.refresh(name);
        assertEquals("reloaded", source.lastStartupProperties.getProperty("some.location"));
    }

    @Test
    void testRefreshSources_withChangedSource_appliesNewValueAndReturnsTrue() {
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(SOURCE_KEY, "first-value"));
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
        source.supply(Map.of(SOURCE_KEY, "second-value"));
        assertTrue(service.refreshSources(name));
        assertEquals("second-value", service.getString(name, SOURCE_KEY));
        assertEquals("second-value", service.getParameter(name, SOURCE_KEY).rawValue());
        assertEquals("second-value", service.asTypedProperties(name).getProperty(SOURCE_KEY));
    }

    @Test
    void testRefreshSources_refreshesSourceWithResolvedProperties() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty("some.location", "from-file");
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of());
        String name = registerEngine(properties, Map.of(), Map.of(), source);
        source.lastStartupProperties = null;
        service.refreshSources(name);
        assertEquals("from-file", source.lastStartupProperties.getProperty("some.location"));
    }

    @Test
    void testRefreshSources_withNewKey_addsKeyWithSourceLabel() {
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of());
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
        source.supply(Map.of(SOURCE_KEY, "late-value"));
        assertTrue(service.refreshSources(name));
        assertEquals("late-value", service.getString(name, SOURCE_KEY));
        assertEquals(Source.EXTERNAL_FILE, service.getParameter(name, SOURCE_KEY).source());
    }

    @Test
    void testRefreshSources_withUnchangedSource_keepsValueAndReturnsFalse() {
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(SOURCE_KEY, "first-value"));
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
        source.supplyWithoutReportingChange(Map.of(SOURCE_KEY, "second-value"));
        assertFalse(service.refreshSources(name));
        assertEquals("first-value", service.getString(name, SOURCE_KEY));
    }

    @Test
    void testRefreshSources_withChangeReportedButSameValue_returnsFalse() {
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(SOURCE_KEY, "same-value"));
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
        source.supply(Map.of(SOURCE_KEY, "same-value"));
        assertFalse(service.refreshSources(name));
        assertEquals("same-value", service.getString(name, SOURCE_KEY));
    }

    @Test
    void testRefreshSources_whenSourceStopsSupplyingKey_removesKey() {
        FakeStartupParameterSource source = new FakeStartupParameterSource(Map.of(SOURCE_KEY, "first-value"));
        String name = registerEngine(new TypedProperties(), Map.of(), Map.of(), source);
        source.supply(Map.of());
        assertTrue(service.refreshSources(name));
        assertFalse(service.getAllParameters(name).containsKey(SOURCE_KEY));
        assertNull(service.asTypedProperties(name).getProperty(SOURCE_KEY));
        assertNull(service.getString(name, SOURCE_KEY));
    }

    @Test
    void testRefreshSources_withEngineWithoutSource_returnsFalse() {
        String name = registerEngine(new TypedProperties(), Map.of());
        assertFalse(service.refreshSources(name));
    }

    @Test
    void testRefreshSources_withUnregisteredEngine_returnsFalse() {
        assertFalse(service.refreshSources("engine-" + UUID.randomUUID()));
    }

    @Test
    void refreshGlobal_globalBucketHasNoFactory_returnsFalse() {
        service.registerGlobal(new TypedProperties(), Map.of());
        assertFalse(service.refreshGlobal());
    }

    @Test
    void registerGlobal_isIndependentFromEngineBuckets() {
        TypedProperties globalProperties = new TypedProperties();
        globalProperties.setProperty("shared.key", "global-value");
        service.registerGlobal(globalProperties, Map.of());
        TypedProperties engineProperties = new TypedProperties();
        engineProperties.setProperty("shared.key", "engine-value");
        String name = registerEngine(engineProperties, Map.of());
        assertEquals("global-value", service.getGlobalString("shared.key"));
        assertEquals("engine-value", service.getString(name, "shared.key"));
    }

    private String registerEngine(TypedProperties properties, Map<String, Source> knownFileSources) {
        return registerEngine(properties, knownFileSources, Map.of());
    }

    private String registerEngine(TypedProperties properties, Map<String, Source> knownFileSources,
            Map<String, ParameterMetaData> supplementalParameterMetaData) {
        return registerEngine(properties, knownFileSources, supplementalParameterMetaData, null);
    }

    private String registerEngine(TypedProperties properties, Map<String, Source> knownFileSources,
            Map<String, ParameterMetaData> supplementalParameterMetaData, IStartupParameterSource source) {
        String name = "test-engine-" + UUID.randomUUID();
        properties.setProperty(ParameterConstants.ENGINE_NAME, name);
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        when(factory.reload()).thenReturn(properties);
        service.registerEngine(factory, knownFileSources, supplementalParameterMetaData, source);
        registeredEngineNames.add(name);
        return name;
    }

    private TypedProperties registerEngineAndReturnResolvedProperties(TypedProperties properties) {
        String name = "test-engine-" + UUID.randomUUID();
        properties.setProperty(ParameterConstants.ENGINE_NAME, name);
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        when(factory.reload()).thenReturn(properties);
        TypedProperties resolved = service.registerEngine(factory, Map.of(), Map.of());
        registeredEngineNames.add(name);
        return resolved;
    }

    private static class FakeStartupParameterSource implements IStartupParameterSource {
        private Map<String, String> values;
        private boolean isChanged;
        private TypedProperties lastStartupProperties;

        private FakeStartupParameterSource(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Source getSource() {
            return Source.EXTERNAL_FILE;
        }

        @Override
        public boolean refresh(TypedProperties startupProperties) {
            lastStartupProperties = startupProperties;
            boolean wasChanged = isChanged;
            isChanged = false;
            return wasChanged;
        }

        @Override
        public Map<String, String> getParameters() {
            return values;
        }

        private void supply(Map<String, String> newValues) {
            values = newValues;
            isChanged = true;
        }

        private void supplyWithoutReportingChange(Map<String, String> newValues) {
            values = newValues;
        }
    }
}
