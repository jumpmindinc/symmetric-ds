package org.jumpmind.symmetric.util;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;

class TypedPropertiesFactoryTest {
    @Test
    void testMergeAndOverrideWithEnvironmentVariablesAddVariable() {
        Map<String, String> envVariables = Map.of("SYM_DB_PASSWORD", "password");
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, true, envVariables);
        assertEquals(3, fileProps.size());
        assertEquals("password", fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithEnvironmentVariablesOverrideAndAddVariable() {
        Map<String, String> envVariables = Map.of("SYM_DB_USER", "updated-user", "SYM_DB_PASSWORD", "password");
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, true, envVariables);
        assertEquals(3, fileProps.size());
        assertEquals("updated-user", fileProps.getProperty("db.user"));
        assertEquals("password", fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithEnvironmentVariablesNoAddedVariable() {
        Map<String, String> envVariables = Map.of("SYM_DB_PASSWORD", "password");
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, false, envVariables);
        assertEquals(2, fileProps.size());
        assertNull(fileProps.getProperty("db.password"));
    }

    @Test
    void testMergeAndOverrideWithEnvironmentVariablesOverrideAndNoAddedVariable() {
        Map<String, String> envVariables = Map.of("SYM_DB_USER", "updated-user", "SYM_DB_PASSWORD", "password");
        TypedProperties fileProps = new TypedProperties();
        fileProps.setProperty("db.url", "url");
        fileProps.setProperty("db.user", "user");
        TypedPropertiesFactory.mergeAndOverrideWithEnvironmentVariables(fileProps, false, envVariables);
        assertEquals(2, fileProps.size());
        assertEquals("updated-user", fileProps.getProperty("db.user"));
        assertEquals(null, fileProps.getProperty("db.password"));
    }
}
