package org.jumpmind.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Properties;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;

class TypedPropertiesTest {
    @Test
    void renameKeysWithUnderscores_convertsMatchingKeysToPropertyFormat() {
        TypedProperties source = new TypedProperties();
        source.setProperty("OTEL_SERVICE_NAME", "my-service");
        source.setProperty("OTEL_SCOPE", "symmetricds");
        source.setProperty("OTHER_KEY", "value");
        TypedProperties result = source.renameKeysWithUnderscores("OTEL_");
        assertEquals("my-service", result.getProperty("otel.service.name"));
        assertEquals("symmetricds", result.getProperty("otel.scope"));
        assertNull(result.getProperty("other.key"));
    }

    @Test
    void testMerge() {
        TypedProperties target = new TypedProperties();
        target.setProperty("db.url", "original-url");
        target.setProperty("db.user", "original-user");
        Properties source = new Properties();
        source.setProperty("db.url", "updated-url");
        source.setProperty("db.user", "updated-user");
        source.setProperty("db.password", "password");
        target.merge(source);
        assertEquals("updated-url", target.getProperty("db.url"));
        assertEquals("updated-user", target.getProperty("db.user"));
        assertNull(target.getProperty("db.password"));
    }

    @Test
    void testMergeIgnoresKeyNotInTarget() {
        TypedProperties target = new TypedProperties();
        target.setProperty("db.url", "original");
        Properties source = new Properties();
        source.setProperty("db.url", "updated");
        source.setProperty("db.user", "user");
        target.merge(source);
        assertNull(target.getProperty("db.user"));
    }

    @Test
    void testPutAll() {
        TypedProperties target = new TypedProperties();
        target.setProperty("db.url", "original-url");
        target.setProperty("db.user", "original-user");
        Properties source = new Properties();
        source.setProperty("db.url", "updated-url");
        source.setProperty("db.user", "updated-user");
        source.setProperty("db.password", "password");
        target.putAll(source);
        assertEquals("updated-url", target.getProperty("db.url"));
        assertEquals("updated-user", target.getProperty("db.user"));
        assertEquals("password", target.getProperty("db.password"));
    }
}
