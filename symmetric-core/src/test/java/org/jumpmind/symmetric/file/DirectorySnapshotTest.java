package org.jumpmind.symmetric.file;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collections;

import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Router;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DirectorySnapshotTest {
    // File snapshotDirectory = new File("target/snapshots");
    static File testDirectory = new File("target/test");
    static File sourceDirectory = new File(testDirectory, "source");
    static File targetDirectory = new File(testDirectory, "target");
    static File fileInSource1 = new File(sourceDirectory, "temp1.test");
    // static File fileInSource2 = new File(sourceDirectory, "temp2.test");
    static File fileInTarget1 = new File(targetDirectory, "temp1.test");
    static FileTriggerRouter fileTriggerRouter1 = new FileTriggerRouter();

    @BeforeAll
    static void setupBeforeAll() {
        FileTrigger dummyFileTrigger1 = new FileTrigger(sourceDirectory.getPath(), false, "*", "");
        dummyFileTrigger1.setTriggerId("dummyFileTrigger1");
        Router dummyRouter1 = new Router();
        dummyRouter1.setRouterId("dummyRouter1");
        fileTriggerRouter1.setFileTrigger(dummyFileTrigger1);
        fileTriggerRouter1.setRouter(dummyRouter1);
    }

    /**
     * File change in the source snapshot; The target directory snapshot (which is missing this file) must detect this change as same type.
     */
    @ParameterizedTest
    @ValueSource(strings = { "C", "M" })
    void testDiff_FileNotInTarget_AcceptChange(String lastEventTypeCode) {
        // Arrange
        LastEventType lastEventType = LastEventType.fromCode(lastEventTypeCode);
        DirectorySnapshot sourceDir = new DirectorySnapshot(fileTriggerRouter1);
        sourceDir.add(new FileSnapshot(fileTriggerRouter1, fileInSource1, lastEventType, false));
        DirectorySnapshot targetDir = new DirectorySnapshot(fileTriggerRouter1);
        // Act
        DirectorySnapshot differences = targetDir.diff(sourceDir);
        // Assert
        assertEquals(1, differences.size());
        FileSnapshot firstDiff = differences.get(0);
        assertEquals(lastEventType, firstDiff.getLastEventType());
    }

    /**
     * A delete in the source snapshot; The target directory snapshot (which is missing this file) must ignore this change.
     */
    @Test
    void testDiff_FileNotInTarget_DropDeleteChange() {
        // Arrange
        DirectorySnapshot sourceDir = new DirectorySnapshot(fileTriggerRouter1);
        sourceDir.add(new FileSnapshot(fileTriggerRouter1, fileInSource1, LastEventType.DELETE, false));
        DirectorySnapshot targetDir = new DirectorySnapshot(fileTriggerRouter1);
        // Act
        DirectorySnapshot differences = sourceDir.diff(targetDir);
        // Assert
        assertEquals(0, differences.size());
    }

    /**
     * File in the source snapshot; The target directory snapshot (which has same file) must ignore == no real change. strings = { "C", "M","D" })
     */
    @ParameterizedTest
    @ValueSource(strings = { "C", "M", "D" })
    void testDiff_FileIsInTarget_NoRealChange(String lastEventTypeCode) {
        // Arrange
        LastEventType lastEventType = LastEventType.fromCode(lastEventTypeCode);
        DirectorySnapshot sourceDir = new DirectorySnapshot(fileTriggerRouter1);
        sourceDir.add(new FileSnapshot(fileTriggerRouter1, fileInSource1, lastEventType, false));
        DirectorySnapshot targetDir = new DirectorySnapshot(fileTriggerRouter1);
        File fileInSource1Same = new File(sourceDirectory, fileInSource1.getName());
        targetDir.add(new FileSnapshot(fileTriggerRouter1, fileInSource1Same, lastEventType, false));
        // Act
        DirectorySnapshot differences = targetDir.diff(sourceDir);
        // Assert
        assertEquals(0, differences.size());
    }

    /**
     * File CREATEd in the source snapshot; The target directory snapshot (which has same file as MODIFY, but different size) must flip both to MODIFIED.
     */
    @Test
    void testDiff_SameFileIsInTarget_CreateFlipsToModify() {
        // Arrange
        DirectorySnapshot sourceDir = new DirectorySnapshot(fileTriggerRouter1);
        FileSnapshot sourceFileShapshot = new FileSnapshot(fileTriggerRouter1, fileInSource1, LastEventType.CREATE, false);
        sourceFileShapshot.setFileModifiedTime(System.currentTimeMillis()); // Make time different from the target (newer too)
        sourceDir.add(sourceFileShapshot);
        DirectorySnapshot targetDir = new DirectorySnapshot(fileTriggerRouter1);
        File fileInSource1Same = new File(sourceDirectory, fileInSource1.getName());
        targetDir.add(new FileSnapshot(fileTriggerRouter1, fileInSource1Same, LastEventType.MODIFY, false));
        // Act
        DirectorySnapshot differences = targetDir.diff(sourceDir);
        // Assert
        assertEquals(1, differences.size());
        FileSnapshot firstDiff = differences.get(0);
        assertEquals(LastEventType.MODIFY, firstDiff.getLastEventType());
        assertEquals(LastEventType.MODIFY, sourceFileShapshot.getLastEventType());
    }

    /**
     * Source snapshot is empty; The target directory snapshot (which has file changes CREATE/MODIFY) must report 1 difference as DELETE.
     */
    @ParameterizedTest
    @ValueSource(strings = { "C", "M" })
    void testDiff_FileIsInTarget_NoChange(String lastEventTypeCode) {
        // Arrange
        LastEventType lastEventType = LastEventType.fromCode(lastEventTypeCode);
        DirectorySnapshot sourceDir = new DirectorySnapshot(fileTriggerRouter1);
        DirectorySnapshot targetDir = new DirectorySnapshot(fileTriggerRouter1);
        targetDir.add(new FileSnapshot(fileTriggerRouter1, fileInTarget1, lastEventType, false));
        // Act
        DirectorySnapshot differences = targetDir.diff(sourceDir);
        // Assert
        assertEquals(1, differences.size());
        FileSnapshot firstDiff = differences.get(0);
        assertEquals(LastEventType.DELETE, firstDiff.getLastEventType());
    }

    /**
     * Source snapshot is empty; The target directory snapshot (which has file change as DELETE) must report no differences.
     */
    @Test
    void testDiff_FileIsInTargetAs_NoDeleteChange() {
        // Arrange
        LastEventType lastEventType = LastEventType.DELETE;
        DirectorySnapshot sourceDir = new DirectorySnapshot(fileTriggerRouter1);
        DirectorySnapshot targetDir = new DirectorySnapshot(fileTriggerRouter1);
        targetDir.add(new FileSnapshot(fileTriggerRouter1, fileInTarget1, lastEventType, false));
        // Act
        DirectorySnapshot differences = targetDir.diff(sourceDir);
        // Assert
        assertEquals(0, differences.size());
    }

    /**
     * Source and target have lots of files with same names and only 5 differences in ModifiedTime. Time the diff() method!
     */
    @Test
    void testDiff_LotsOfFilesAndFewChanges() {
        // Arrange
        int maxIterations = 100;
        int iteration = 1;
        int expectedDifferences = 5;
        int generatedDifferences = 0;
        int frequencyDifferences = maxIterations / expectedDifferences;
        // System.out.println("testDiff_LotsOfFilesAndFewChanges start; maxIterations " + maxIterations + "; expectedDifferences=" + expectedDifferences);
        DirectorySnapshot sourceDir = new DirectorySnapshot(fileTriggerRouter1);
        DirectorySnapshot targetDir = new DirectorySnapshot(fileTriggerRouter1);
        while (iteration++ < maxIterations) {
            String fileName = String.format("temp%d.test", iteration);
            File currFileInSource = new File(sourceDirectory, fileName);
            FileSnapshot sourceFileShapshot = new FileSnapshot(fileTriggerRouter1, currFileInSource, LastEventType.CREATE, false);
            sourceDir.add(sourceFileShapshot);
            File currFileInTarget = new File(sourceDirectory, fileName);
            FileSnapshot targetFileShapshot = new FileSnapshot(fileTriggerRouter1, currFileInTarget, LastEventType.MODIFY, false);
            if (generatedDifferences < expectedDifferences && iteration % frequencyDifferences == 0) {
                generatedDifferences++;
                targetFileShapshot.setFileModifiedTime(System.currentTimeMillis()); // Make time different from the target (newer too)
            }
            targetDir.add(targetFileShapshot);
        }
        // Act
        long startTime = System.currentTimeMillis();
        DirectorySnapshot differences = targetDir.diff(sourceDir);
        // Assert
        assertEquals(expectedDifferences, differences.size());
        long runTimeMs = System.currentTimeMillis() - startTime;
        assertTrue(50 > runTimeMs);
        // System.out.println("testDiff_LotsOfFilesAndFewChanges done; Runtime ms=" + (runTimeMs));
    }

    @Test
    void testGetFileTriggerRouter() {
        assertEquals(fileTriggerRouter1, new DirectorySnapshot(fileTriggerRouter1).getFileTriggerRouter());
    }

    @Test
    void testConstructor_withExistingSnapshotCopiesEntries() {
        FileSnapshot file = new FileSnapshot(fileTriggerRouter1, fileInSource1, LastEventType.CREATE, false);
        DirectorySnapshot snapshot = new DirectorySnapshot(fileTriggerRouter1, Collections.singletonList(file));
        assertEquals(1, snapshot.size());
        assertEquals(file, snapshot.get(0));
    }

    @Test
    void testConstructor_withoutSnapshotIsEmpty() {
        assertTrue(new DirectorySnapshot(fileTriggerRouter1).isEmpty());
    }

    @Test
    void testMerge_replacesCreateWithIncomingCreate() {
        DirectorySnapshot base = new DirectorySnapshot(fileTriggerRouter1);
        base.add(newSnapshot(fileInSource1, LastEventType.CREATE));
        DirectorySnapshot incoming = new DirectorySnapshot(fileTriggerRouter1);
        incoming.add(newSnapshot(fileInSource1, LastEventType.CREATE));
        base.merge(incoming);
        assertEquals(1, base.size());
        assertEquals(LastEventType.CREATE, base.get(0).getLastEventType());
    }

    @Test
    void testMerge_appliesIncomingModify() {
        DirectorySnapshot base = new DirectorySnapshot(fileTriggerRouter1);
        base.add(newSnapshot(fileInSource1, LastEventType.MODIFY));
        DirectorySnapshot incoming = new DirectorySnapshot(fileTriggerRouter1);
        incoming.add(newSnapshot(fileInSource1, LastEventType.MODIFY));
        base.merge(incoming);
        assertEquals(1, base.size());
        assertEquals(LastEventType.MODIFY, base.get(0).getLastEventType());
    }

    @Test
    void testMerge_dropsEntryWhenIncomingIsDelete() {
        DirectorySnapshot base = new DirectorySnapshot(fileTriggerRouter1);
        base.add(newSnapshot(fileInSource1, LastEventType.MODIFY));
        DirectorySnapshot incoming = new DirectorySnapshot(fileTriggerRouter1);
        incoming.add(newSnapshot(fileInSource1, LastEventType.DELETE));
        base.merge(incoming);
        assertTrue(base.isEmpty());
    }

    @Test
    void testMerge_leavesUnrelatedEntriesAlone() {
        DirectorySnapshot base = new DirectorySnapshot(fileTriggerRouter1);
        base.add(newSnapshot(fileInSource1, LastEventType.CREATE));
        base.merge(new DirectorySnapshot(fileTriggerRouter1));
        assertEquals(1, base.size());
    }

    private static FileSnapshot newSnapshot(File file, LastEventType lastEventType) {
        return new FileSnapshot(fileTriggerRouter1, file, lastEventType, false);
    }
}
