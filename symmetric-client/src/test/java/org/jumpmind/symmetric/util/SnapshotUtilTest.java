package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SnapshotUtilTest {
    @TempDir
    Path tempDir;
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

    @Test
    void writeLoggingConfigFile_existingConfigs_areCopiedIntoSnapshot() throws IOException {
        File confDir = tempDir.resolve("conf").toFile();
        File snapshotDir = tempDir.resolve("snapshot").toFile();
        confDir.mkdirs();
        snapshotDir.mkdirs();
        Files.writeString(confDir.toPath().resolve("logback.xml"), "<configuration/>");
        Files.writeString(confDir.toPath().resolve("log4j2.xml.deprecated"), "<Configuration/>");
        SnapshotUtil.writeLoggingConfigFile(snapshotDir, new File(confDir, "logback.xml").getAbsolutePath());
        SnapshotUtil.writeLoggingConfigFile(snapshotDir, new File(confDir, "log4j2.xml.deprecated").getAbsolutePath());
        assertTrue(new File(snapshotDir, "logback.xml").exists(), "the live logging config should be in the snapshot");
        assertTrue(new File(snapshotDir, "log4j2.xml.deprecated").exists(),
                "the retired pre-upgrade config should be included so support can compare source and result of conversion");
    }

    @Test
    void writeLoggingConfigFile_missingConfig_doesNotThrowOrCreateStrayFiles() {
        File confDir = tempDir.resolve("conf").toFile();
        File snapshotDir = tempDir.resolve("snapshot").toFile();
        confDir.mkdirs();
        snapshotDir.mkdirs();
        assertDoesNotThrow(() -> SnapshotUtil.writeLoggingConfigFile(snapshotDir, new File(confDir, "logback.xml").getAbsolutePath()));
        assertEquals(0, snapshotDir.list().length, "nothing should be copied when no logging configs exist");
    }

    @Test
    void writeLoggingConfigFile_copyFails_logsWarningInsteadOfThrowing() throws IOException {
        File confDir = tempDir.resolve("conf").toFile();
        File snapshotDir = tempDir.resolve("snapshot").toFile();
        confDir.mkdirs();
        snapshotDir.mkdirs();
        File notActuallyAFile = new File(confDir, "logback.xml");
        notActuallyAFile.mkdirs();
        assertDoesNotThrow(() -> SnapshotUtil.writeLoggingConfigFile(snapshotDir, notActuallyAFile.getAbsolutePath()));
        assertEquals(0, snapshotDir.list().length, "a failed copy should not leave a partial file behind");
    }
}
