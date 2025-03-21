package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Properties;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SnapshotUtilTest {
    private File logDir;
    private File stagingDir;
    private File tmpDir;
    private IStagingManager stagingManager;
    private ISymmetricEngine engine;
    private Properties properties;

    @BeforeEach
    void setUp() {
        logDir = mock(File.class);
        stagingDir = mock(File.class);
        tmpDir = mock(File.class);
        stagingManager = mock(IStagingManager.class);
        engine = mock(ISymmetricEngine.class);
        properties = new Properties();
        when(engine.getStagingManager()).thenReturn(stagingManager);
        when(stagingManager.getStagingDirectory()).thenReturn(stagingDir);
        when(logDir.getUsableSpace()).thenReturn(1024L * 1024 * 1024);
        when(stagingDir.getUsableSpace()).thenReturn(1024L * 1024);
        when(tmpDir.getUsableSpace()).thenReturn(-512L);
    }

    @Test
    void testAddUsableDiskSpaceProperties() {
        SnapshotUtil.addUsableDiskSpaceProperties(properties, engine, tmpDir, logDir);
        assertEquals("1 GB", properties.getProperty("log.directory.space.usable"));
        assertEquals("1 MB", properties.getProperty("staging.directory.space.usable"));
        assertEquals("-512 bytes", properties.getProperty("temp.directory.space.usable"));
    }
}
