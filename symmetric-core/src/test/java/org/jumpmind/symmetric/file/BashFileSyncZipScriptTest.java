/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashFileSyncZipScriptTest {
    private static final String TARGET_BASE_DIR = "/opt/target";
    @TempDir
    private File tempDir;
    private BashFileSyncZipScript script;
    private Batch batch;
    private FileTrigger fileTrigger;
    private FileTriggerRouter triggerRouter;
    private File existingFile;

    @BeforeEach
    void setUp() throws IOException {
        script = new BashFileSyncZipScript();
        batch = new Batch(BatchType.EXTRACT, 1, "filesync", BinaryEncoding.BASE64, "store-1", "corp", false);
        fileTrigger = new FileTrigger(tempDir.getAbsolutePath(), true, null, null);
        triggerRouter = new FileTriggerRouter();
        triggerRouter.setFileTrigger(fileTrigger);
        existingFile = new File(tempDir, "item.txt");
        Files.write(existingFile.toPath(), "payload".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testGetScriptFileName() {
        assertEquals("sync.sh", script.getScriptFileName(batch));
    }

    @Test
    void testBuildScriptStart_emitsShebangAndArguments() {
        script.buildScriptStart(batch);
        String text = script.getScript().toString();
        assertTrue(text.startsWith("#!/bin/bash"));
        assertTrue(text.contains("batchDir=$1"));
        assertTrue(text.contains("sourceNodeId=$2"));
        assertTrue(text.contains("outputFileName=$3"));
    }

    @Test
    void testBuildScriptEnd_emitsNothing() {
        script.buildScriptEnd(batch);
        assertEquals("", script.getScript().toString());
    }

    @Test
    void testBuildScriptFileSnapshot_forCreateCopiesFile() {
        buildSnapshot(LastEventType.CREATE, existingFile);
        String text = script.getScript().toString();
        assertTrue(text.contains("sourceFileName=\"item.txt\""));
        assertTrue(text.contains("mkdir -p \"$targetBaseDir\""));
        assertTrue(text.contains("cp -a \"$sourceFile\" \"$targetDir\""));
        assertTrue(text.contains("=C\" >> $outputFileName"));
    }

    @Test
    void testBuildScriptFileSnapshot_forModifyCopiesFile() {
        buildSnapshot(LastEventType.MODIFY, existingFile);
        String text = script.getScript().toString();
        assertTrue(text.contains("cp -a \"$sourceFile\" \"$targetDir\""));
        assertTrue(text.contains("=M\" >> $outputFileName"));
    }

    @Test
    void testBuildScriptFileSnapshot_forDeleteRemovesFile() {
        buildSnapshot(LastEventType.DELETE, existingFile);
        String text = script.getScript().toString();
        assertTrue(text.contains("rm -rf \"$targetBaseDir/$targetRelativeDir/$targetFileName\""));
        assertFalse(text.contains("cp -a"));
    }

    @Test
    void testBuildScriptFileSnapshot_forCreateOfMissingFileSkipsCopy() {
        buildSnapshot(LastEventType.CREATE, new File(tempDir, "gone.txt"));
        assertFalse(script.getScript().toString().contains("cp -a"));
    }

    @Test
    void testBuildScriptFileSnapshot_includesTargetBaseDir() {
        buildSnapshot(LastEventType.CREATE, existingFile);
        assertTrue(script.getScript().toString().contains("targetBaseDir=\"" + TARGET_BASE_DIR + "\""));
    }

    @Test
    void testBuildScriptFileSnapshot_emitsBeforeCopyScript() {
        fileTrigger.setBeforeCopyScript("processFile=false");
        buildSnapshot(LastEventType.CREATE, existingFile);
        assertTrue(script.getScript().toString().contains("processFile=false"));
    }

    @Test
    void testBuildScriptFileSnapshot_emitsAfterCopyScript() {
        fileTrigger.setAfterCopyScript("echo done");
        buildSnapshot(LastEventType.CREATE, existingFile);
        assertTrue(script.getScript().toString().contains("echo done"));
    }

    @Test
    void testBuildScriptFileSnapshot_leavesTargetRelativeDirEmptyForCurrentDirectory() {
        buildSnapshot(LastEventType.CREATE, existingFile);
        assertTrue(script.getScript().toString().contains("targetRelativeDir=\"\""));
    }

    @Test
    void testBuildScriptFileSnapshot_includesNestedRelativeDir() {
        FileSnapshot snapshot = newSnapshot(LastEventType.CREATE, existingFile);
        snapshot.setRelativeDir("nested/dir");
        script.buildScriptFileSnapshot(batch, snapshot, triggerRouter, fileTrigger, existingFile, TARGET_BASE_DIR, "item.txt");
        assertTrue(script.getScript().toString().contains("targetRelativeDir=\"nested/dir\""));
    }

    private void buildSnapshot(LastEventType eventType, File file) {
        script.buildScriptFileSnapshot(batch, newSnapshot(eventType, file), triggerRouter, fileTrigger, file, TARGET_BASE_DIR, file.getName());
    }

    private FileSnapshot newSnapshot(LastEventType eventType, File file) {
        FileSnapshot snapshot = new FileSnapshot();
        snapshot.setFileName(file.getName());
        snapshot.setRelativeDir(".");
        snapshot.setLastEventType(eventType);
        return snapshot;
    }
}
