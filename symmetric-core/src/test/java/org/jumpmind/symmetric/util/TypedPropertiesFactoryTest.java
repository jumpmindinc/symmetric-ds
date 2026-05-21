package org.jumpmind.symmetric.util;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Properties;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.Test;

class TypedPropertiesFactoryTest {
    @Test
    void testMergeAndOverrideWithEnvironmentVariablesAddVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_PASSWORD", "password")));
        assertEquals(3, fileProps.size());
        assertEquals("password", fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithEnvironmentVariablesOverrideAndAddVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_USER", "updated-user", "SYM_DB_PASSWORD", "password")));
        assertEquals(3, fileProps.size());
        assertEquals("updated-user", fileProps.getProperty("db.user"));
        assertEquals("password", fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithEnvironmentVariablesNoAddedVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, false,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_PASSWORD", "password")));
        assertEquals(2, fileProps.size());
        assertNull(fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithEnvironmentVariablesOverrideAndNoAddedVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, false,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("SYM_DB_USER", "updated-user", "SYM_DB_PASSWORD", "password")));
        assertEquals(2, fileProps.size());
        assertEquals("updated-user", fileProps.getProperty("db.user"));
        assertEquals(null, fileProps.getProperty("db.password"));
    }

    @Test
    void testOtelEnvVariableAddedWithOriginalKey() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("OTEL_SERVICE_NAME", "my-service")));
        assertEquals("my-service", fileProps.getProperty("OTEL_SERVICE_NAME"));
    }

    @Test
    void testOtelEnvVariableNotAddedWhenNotMissingProperties() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, false,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("OTEL_SERVICE_NAME", "my-service")));
        assertNull(fileProps.getProperty("OTEL_SERVICE_NAME"));
    }

    @Test
    void testOtelEnvVariableOverriddenBySymVariable() {
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, true,
                TypedPropertiesFactory.getEnvironmentVariables(Map.of("OTEL_SERVICE_NAME", "otel-name", "SYM_OTEL_SERVICE_NAME", "sym-name")));
        assertEquals("otel-name", fileProps.getProperty("OTEL_SERVICE_NAME"));
        assertEquals("sym-name", fileProps.getProperty("otel.service.name"));
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
    void testMergeAndOverrideWithEnvironmentVariables_throwsWhenBothEmpty() {
        TypedProperties fileProps = new TypedProperties();
        try {
            TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, true,
                    TypedPropertiesFactory.getEnvironmentVariables(Map.of()));
            throw new AssertionError("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Property files were not found", e.getMessage());
        }
    }
}
