package org.jumpmind.db.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BasicDataSourceFactoryTest {
    @BeforeEach
    void setUp() {
        BasicDataSourceFactory.requiredConnectionProperties.clear();
    }

    @Test
    void testPrepareDriverSetsDbCloseOnExitForH2() throws Exception {
        BasicDataSourceFactory.prepareDriver("org.h2.Driver");
        assertEquals("FALSE", BasicDataSourceFactory.requiredConnectionProperties.get("DB_CLOSE_ON_EXIT"));
    }

    @Test
    void testPrepareDriverDoesNotSetDbCloseOnExitForOtherDrivers() throws Exception {
        BasicDataSourceFactory.prepareDriver("org.hsqldb.jdbc.JDBCDriver");
        assertFalse(BasicDataSourceFactory.requiredConnectionProperties.containsKey("DB_CLOSE_ON_EXIT"));
    }

    @Test
    void testCreateDoesNotDuplicateRequiredPropertyAlreadyInUrl() throws Exception {
        TypedProperties props = new TypedProperties();
        props.put(BasicDataSourcePropertyConstants.DB_POOL_DRIVER, "org.h2.Driver");
        props.put(BasicDataSourcePropertyConstants.DB_POOL_URL, "jdbc:h2:mem:test;DB_CLOSE_ON_EXIT=FALSE");
        assertDoesNotThrow(() -> {
            ResettableBasicDataSource ds = BasicDataSourceFactory.create(props);
            ds.getConnection().close();
            ds.close();
        });
    }

    @Test
    void testCreateSkipsDbCloseOnExitWhenH2AutoServerEnabled() throws Exception {
        TypedProperties props = new TypedProperties();
        props.put(BasicDataSourcePropertyConstants.DB_POOL_DRIVER, "org.h2.Driver");
        props.put(BasicDataSourcePropertyConstants.DB_POOL_URL, "jdbc:h2:file:./build/test-auto-server;AUTO_SERVER=TRUE;LOCK_TIMEOUT=60000");
        ResettableBasicDataSource ds = BasicDataSourceFactory.create(props);
        assertDoesNotThrow(() -> {
            ds.getConnection().close();
        });
        ds.close();
    }
}
