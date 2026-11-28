package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.util.AppUtils;
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
    void writeLoggingConfigFiles_noConfigFilesPresentDoesNotThrow() {
        File snapshotDir = tempDir.resolve("snapshot").toFile();
        snapshotDir.mkdirs();
        assertDoesNotThrow(() -> SnapshotUtil.writeLoggingConfigFiles(snapshotDir));
    }

    @Test
    void writeLoggingConfigFile_existingConfigsAreCopiedIntoSnapshot() throws IOException {
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
    void writeLoggingConfigFile_missingConfigDoesNotThrowOrCreateStrayFiles() {
        File confDir = tempDir.resolve("conf").toFile();
        File snapshotDir = tempDir.resolve("snapshot").toFile();
        confDir.mkdirs();
        snapshotDir.mkdirs();
        assertDoesNotThrow(() -> SnapshotUtil.writeLoggingConfigFile(snapshotDir, new File(confDir, "logback.xml").getAbsolutePath()));
        assertEquals(0, snapshotDir.list().length, "nothing should be copied when no logging configs exist");
    }

    @Test
    void writeLoggingConfigFile_copyFailsLogsWarningInsteadOfThrowing() throws IOException {
        File confDir = tempDir.resolve("conf").toFile();
        File snapshotDir = tempDir.resolve("snapshot").toFile();
        confDir.mkdirs();
        snapshotDir.mkdirs();
        File notActuallyAFile = new File(confDir, "logback.xml");
        notActuallyAFile.mkdirs();
        assertDoesNotThrow(() -> SnapshotUtil.writeLoggingConfigFile(snapshotDir, notActuallyAFile.getAbsolutePath()));
        assertEquals(0, snapshotDir.list().length, "a failed copy should not leave a partial file behind");
    }

    @Test
    void notifyAfterSnapshot_listenerReadsAndChangesSnapshotFiles() throws IOException {
        File snapshotDir = Files.createDirectory(tempDir.resolve("engine-snapshot")).toFile();
        Files.writeString(snapshotDir.toPath().resolve("config-export.csv"), "secret");
        ISnapshotUtilListener sanitizer = new ISnapshotUtilListener() {
            public void beforeSnapshot(ISymmetricEngine snapshotEngine, File dir) {
            }

            public void afterSnapshot(ISymmetricEngine snapshotEngine, File dir) {
                try {
                    Path file = dir.toPath().resolve("config-export.csv");
                    Files.writeString(file, Files.readString(file).replace("secret", "*****"));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        whenListeners(sanitizer);
        SnapshotUtil.notifyAfterSnapshot(engine, snapshotDir);
        assertEquals("*****", Files.readString(snapshotDir.toPath().resolve("config-export.csv")));
    }

    @Test
    void notifyAfterSnapshot_listenerExceptionIsNotThrown() throws IOException {
        File snapshotDir = Files.createDirectory(tempDir.resolve("engine-snapshot")).toFile();
        ISnapshotUtilListener failingListener = mock(ISnapshotUtilListener.class);
        doThrow(new IllegalStateException("listener failed")).when(failingListener).afterSnapshot(engine, snapshotDir);
        whenListeners(failingListener);
        assertDoesNotThrow(() -> SnapshotUtil.notifyAfterSnapshot(engine, snapshotDir));
    }

    @Test
    void packageSnapshot_zipsFilesAndDeletesTempDirectory() throws IOException {
        File snapshotDir = Files.createDirectory(tempDir.resolve("engine-snapshot")).toFile();
        Files.writeString(snapshotDir.toPath().resolve("config-export.csv"), "*****");
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getTempDirectory()).thenReturn(tempDir.toString());
        File zipFile = SnapshotUtil.packageSnapshot(engine, snapshotDir);
        assertFalse(snapshotDir.exists());
        assertEquals("engine-snapshot.zip", zipFile.getName());
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.stream().filter(e -> e.getName().endsWith("config-export.csv")).findFirst().orElseThrow();
            assertEquals("*****", new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private void whenListeners(ISnapshotUtilListener... listeners) {
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(extensionService.getExtensionPointList(ISnapshotUtilListener.class)).thenReturn(Arrays.asList(listeners));
    }

    @Test
    void packageSnapshot_includeHostnameParameter_prefixesZipName() throws IOException {
        File snapshotDir = Files.createDirectory(tempDir.resolve("engine-snapshot")).toFile();
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getTempDirectory()).thenReturn(tempDir.toString());
        when(parameterService.is(ParameterConstants.SNAPSHOT_FILE_INCLUDE_HOSTNAME)).thenReturn(true);
        File zipFile = SnapshotUtil.packageSnapshot(engine, snapshotDir);
        assertEquals(AppUtils.getHostName() + "_engine-snapshot.zip", zipFile.getName());
    }

    @Test
    void packageSnapshot_missingDirectory_throwsIoException() {
        File missingDir = tempDir.resolve("missing-snapshot").toFile();
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getTempDirectory()).thenReturn(tempDir.toString());
        assertThrows(IoException.class, () -> SnapshotUtil.packageSnapshot(engine, missingDir));
    }
}
