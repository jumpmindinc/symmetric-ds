package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.jumpmind.db.model.Transaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.csv.CsvReader;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SnapshotUtilTest {
    private static final String DB_USER = "symmetric";
    private static final String OTHER_USER = "app";
    private static final int BLOCKING_ID_COLUMN = 7;
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
    void createTransactionsFiles_unblockedSessionsGoOnlyToAllTransactionsFile() throws IOException {
        List<Transaction> transactions = Arrays.asList(transaction("101", DB_USER, null), transaction("202", DB_USER, null));
        SnapshotUtil.createTransactionsFiles(tempDir.toString(), transactions, DB_USER);
        assertEquals(Arrays.asList("101", "202"), readTransactionIds(SnapshotUtil.TRANSACTIONS_FILE_NAME));
        assertFalse(tempDir.resolve(SnapshotUtil.BLOCKED_TRANSACTIONS_FILE_NAME).toFile().exists());
    }

    @Test
    void createTransactionsFiles_lockChainGoesToBothFiles() throws IOException {
        List<Transaction> transactions = Arrays.asList(transaction("101", OTHER_USER, null), transaction("202", DB_USER, "101"),
                transaction("303", OTHER_USER, null));
        SnapshotUtil.createTransactionsFiles(tempDir.toString(), transactions, DB_USER);
        assertEquals(Arrays.asList("101", "202", "303"), readTransactionIds(SnapshotUtil.TRANSACTIONS_FILE_NAME));
        assertEquals(Arrays.asList("202", "101"), readTransactionIds(SnapshotUtil.BLOCKED_TRANSACTIONS_FILE_NAME));
    }

    @Test
    void createTransactionsFiles_writesBlockingIdColumn() throws IOException {
        List<Transaction> transactions = Arrays.asList(transaction("101", OTHER_USER, null), transaction("202", DB_USER, "101"));
        SnapshotUtil.createTransactionsFiles(tempDir.toString(), transactions, DB_USER);
        List<String[]> records = readCsvRecords(SnapshotUtil.TRANSACTIONS_FILE_NAME);
        assertEquals(Arrays.asList("ID", "Username", "Remote IP", "Remote Host", "Status", "Reads", "Writes", "Blocking ID", "Duration", "Text"),
                Arrays.asList(records.get(0)));
        assertEquals("", records.get(1)[BLOCKING_ID_COLUMN]);
        assertEquals("101", records.get(2)[BLOCKING_ID_COLUMN]);
    }

    private Transaction transaction(String id, String username, String blockingId) {
        return new Transaction(id, username, blockingId, new Date(), "select 1");
    }

    private List<String> readTransactionIds(String fileName) throws IOException {
        return readCsvRecords(fileName).stream().skip(1).map(values -> values[0]).collect(Collectors.toList());
    }

    private List<String[]> readCsvRecords(String fileName) throws IOException {
        List<String[]> records = new ArrayList<>();
        try (CsvReader reader = new CsvReader(tempDir.resolve(fileName).toString(), ',', StandardCharsets.ISO_8859_1)) {
            while (reader.readRecord()) {
                records.add(reader.getValues());
            }
        }
        return records;
    }
}
