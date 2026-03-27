package org.jumpmind.util.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Properties;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;

public class TypedPropertiesTest {
    @Test
    public void testMerge() {
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
    public void testMergeIgnoresKeyNotInTarget() {
        TypedProperties target = new TypedProperties();
        target.setProperty("db.url", "original");
        Properties source = new Properties();
        source.setProperty("db.url", "updated");
        source.setProperty("db.user", "user");
        target.merge(source);
        assertNull(target.getProperty("db.user"));
    }

    @Test
    public void testPutAll() {
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
