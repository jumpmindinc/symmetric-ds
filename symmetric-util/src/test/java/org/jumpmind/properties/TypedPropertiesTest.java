package org.jumpmind.properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.jumpmind.exception.IoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

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

    @Test
    void testConstructor_default() {
        TypedProperties props = new TypedProperties();
        assertEquals(0, props.size());
    }

    @Test
    void testConstructor_withInputStream_loadsProperties() {
        InputStream is = new ByteArrayInputStream("key=value".getBytes());
        TypedProperties props = new TypedProperties(is);
        assertEquals("value", props.getProperty("key"));
    }

    @Test
    void testConstructor_withInputStream_closesStreamAfterLoad() {
        boolean[] closed = { false };
        InputStream is = new ByteArrayInputStream("key=value".getBytes()) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        new TypedProperties(is);
        assertTrue(closed[0]);
    }

    @Test
    void testConstructor_withFile_loadsProperties(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("test.properties").toFile();
        Files.writeString(file.toPath(), "key=value");
        TypedProperties props = new TypedProperties(file);
        assertEquals("value", props.getProperty("key"));
    }

    @Test
    void testConstructor_withFile_throwsIoExceptionWhenMissing() {
        File file = new File("does-not-exist-typed-properties-test.properties");
        assertThrows(IoException.class, () -> new TypedProperties(file));
    }

    @Test
    void testConstructor_withURL_loadsProperties(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("test.properties").toFile();
        Files.writeString(file.toPath(), "key=value");
        TypedProperties props = new TypedProperties(file.toURI().toURL());
        assertEquals("value", props.getProperty("key"));
    }

    @Test
    void testConstructor_withProperties_copiesEntries() {
        Properties source = new Properties();
        source.setProperty("a", "1");
        source.setProperty("b", "2");
        TypedProperties props = new TypedProperties(source);
        assertEquals("1", props.getProperty("a"));
        assertEquals("2", props.getProperty("b"));
    }

    @Test
    void testPut_withNonNullValue_returnsPreviousValue() {
        TypedProperties props = new TypedProperties();
        assertNull(props.put("key", "first"));
        assertEquals("first", props.put("key", "second"));
        assertEquals("second", props.getProperty("key"));
    }

    @Test
    void testPut_withNullValue_returnsNullAndDoesNotStore() {
        TypedProperties props = new TypedProperties();
        assertNull(props.put("key", null));
        assertFalse(props.containsKey("key"));
    }

    @Test
    void testSetProperty_withNullValue_doesNotStore() {
        TypedProperties props = new TypedProperties();
        props.setProperty("key", (String) null);
        assertNull(props.getProperty("key"));
    }

    @Test
    void testGetLong_withValidValue() {
        TypedProperties props = new TypedProperties();
        props.setProperty("count", "100");
        assertEquals(100L, props.getLong("count"));
    }

    @Test
    void testGetLong_withMissingKey_returnsDefaultNegativeOne() {
        assertEquals(-1L, new TypedProperties().getLong("missing"));
    }

    @Test
    void testGetLong_withInvalidValue_returnsDefault() {
        TypedProperties props = new TypedProperties();
        props.setProperty("count", "not-a-number");
        assertEquals(42L, props.getLong("count", 42L));
    }

    @Test
    void testGetInt_withValidValue() {
        TypedProperties props = new TypedProperties();
        props.setProperty("count", "100");
        assertEquals(100, props.getInt("count"));
    }

    @Test
    void testGetInt_withMissingKey_returnsDefaultZero() {
        assertEquals(0, new TypedProperties().getInt("missing"));
    }

    @Test
    void testGetInt_withInvalidValue_returnsDefault() {
        TypedProperties props = new TypedProperties();
        props.setProperty("count", "not-a-number");
        assertEquals(42, props.getInt("count", 42));
    }

    @Test
    void testIs_withTrueValue() {
        TypedProperties props = new TypedProperties();
        props.setProperty("flag", "true");
        assertTrue(props.is("flag"));
    }

    @Test
    void testIs_withFalseValue() {
        TypedProperties props = new TypedProperties();
        props.setProperty("flag", "false");
        assertFalse(props.is("flag"));
    }

    @Test
    void testIs_withMissingKey_returnsDefault() {
        assertTrue(new TypedProperties().is("missing", true));
    }

    @Test
    void testIs_withInvalidValue_returnsFalse() {
        TypedProperties props = new TypedProperties();
        props.setProperty("flag", "not-a-boolean");
        assertFalse(props.is("flag", true));
    }

    @Test
    void testCollectFrom_withNamesAndLookupFunction() {
        TypedProperties props = new TypedProperties();
        String[] names = { "host", "port" };
        props.collectFrom("db.", names, key -> "value-for-" + key);
        assertEquals("value-for-db.host", props.getProperty("host"));
        assertEquals("value-for-db.port", props.getProperty("port"));
    }

    @Test
    void testCollectFrom_withLookupReturningNull_skipsProperty() {
        TypedProperties props = new TypedProperties();
        props.collectFrom("db.", new String[] { "host" }, key -> null);
        assertNull(props.getProperty("host"));
    }

    @Test
    void testCollectFrom_withSourcePropertiesAndDropPrefixTrue() {
        TypedProperties source = new TypedProperties();
        source.setProperty("DB_HOST", "localhost");
        source.setProperty("DB_PORT", "5432");
        source.setProperty("OTHER_KEY", "value");
        TypedProperties target = new TypedProperties();
        target.collectFrom(source, "DB_", true);
        assertEquals("localhost", target.getProperty("host"));
        assertEquals("5432", target.getProperty("port"));
        assertNull(target.getProperty("other.key"));
    }

    @Test
    void testCollectFrom_withSourcePropertiesAndDropPrefixFalse() {
        TypedProperties source = new TypedProperties();
        source.setProperty("DB_HOST", "localhost");
        TypedProperties target = new TypedProperties();
        target.collectFrom(source, "DB_", false);
        assertEquals("localhost", target.getProperty("DB_HOST"));
    }

    @Test
    void testGet_withPresentKey() {
        TypedProperties props = new TypedProperties();
        props.setProperty("key", "value");
        assertEquals("value", props.get("key"));
    }

    @Test
    void testGet_withMissingKey_returnsNull() {
        assertNull(new TypedProperties().get("missing"));
    }

    @Test
    void testGet_withMissingKey_returnsDefaultValue() {
        assertEquals("default", new TypedProperties().get("missing", "default"));
    }

    @Test
    void testSetProperty_withIntValue() {
        TypedProperties props = new TypedProperties();
        props.setProperty("count", 5);
        assertEquals("5", props.getProperty("count"));
    }

    @Test
    void testSetProperty_withLongValue() {
        TypedProperties props = new TypedProperties();
        props.setProperty("count", 5L);
        assertEquals("5", props.getProperty("count"));
    }

    @Test
    void testGetArray_withCommaSeparatedValue_splitsIntoElements() {
        TypedProperties props = new TypedProperties();
        props.setProperty("list", "a,b,c");
        assertArrayEquals(new String[] { "a", "b", "c" }, props.getArray("list", null));
    }

    @Test
    void testGetArray_withMissingKey_returnsDefaultValue() {
        String[] defaultValue = { "x", "y" };
        assertArrayEquals(defaultValue, new TypedProperties().getArray("missing", defaultValue));
    }

    @Test
    void testCopy_createsIndependentCopy() {
        TypedProperties original = new TypedProperties();
        original.setProperty("key", "value");
        TypedProperties copy = original.copy();
        copy.setProperty("key", "changed");
        assertEquals("value", original.getProperty("key"));
        assertEquals("changed", copy.getProperty("key"));
    }

    @Test
    void testLogPropertiesException_withBlankValue_doesNotLog() {
        Logger logger = mock(Logger.class);
        TypedProperties.logPropertiesException(logger, "key", "");
        verify(logger, never()).error(anyString());
    }

    @Test
    void testLogPropertiesException_withNonBlankValue_logsError() {
        Logger logger = mock(Logger.class);
        TypedProperties.logPropertiesException(logger, "key", "bad-value");
        verify(logger).error(anyString());
    }

    @Test
    void testLogAllKeys_doesNotThrow() {
        TypedProperties props = new TypedProperties();
        props.setProperty("key", "value");
        assertDoesNotThrow(() -> props.logAllKeys("test-source"));
    }
}
