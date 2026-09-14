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

import java.io.File;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

import org.apache.commons.io.FileUtils;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Router;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FileTriggerTrackerTest {
    File snapshotDirectory = new File("target/snapshots");
    File directory = new File("target/test");
    File subdirectory = new File(directory, "a");
    File fileInDirectory1 = new File(directory, "1.txt");
    File fileInDirectory2 = new File(directory, "2.csv");
    File fileInSubDirectory = new File(subdirectory, "3.doc");

    @BeforeEach
    public void setupTest() throws Exception {
        recreateDirectorySpecAndFiles();
    }

    @Test
    public void testTakeFullSnapshotRecursive() throws Exception {
        FileTrigger fileTrigger = new FileTrigger(directory.getAbsolutePath(), true, null, null);
        Router router = new Router();
        FileTriggerRouter fileTriggerRouter = new FileTriggerRouter(fileTrigger, router);
        FileTriggerTracker tracker = new FileTriggerTracker(fileTriggerRouter, null, null, false, null);
        DirectorySnapshot snapshot = new DirectorySnapshot(fileTriggerRouter);
        tracker.takeFullSnapshot(snapshot);
        assertEquals(4, snapshot.size());
    }

    @Test
    public void testTakeFullSnapshotNonRecursive() throws Exception {
        FileTrigger fileTrigger = new FileTrigger(directory.getAbsolutePath(), false, null, null);
        Router router = new Router();
        FileTriggerRouter fileTriggerRouter = new FileTriggerRouter(fileTrigger, router);
        FileTriggerTracker tracker = new FileTriggerTracker(fileTriggerRouter, null, null, false, null);
        DirectorySnapshot snapshot = new DirectorySnapshot(fileTriggerRouter);
        tracker.takeFullSnapshot(snapshot);
        assertEquals(2, snapshot.size());
    }

    @Test
    public void testTakeFullSnapshotIncludes() throws Exception {
        FileTrigger fileTrigger = new FileTrigger(directory.getAbsolutePath(), false, "*.txt", null);
        Router router = new Router();
        FileTriggerRouter fileTriggerRouter = new FileTriggerRouter(fileTrigger, router);
        FileTriggerTracker tracker = new FileTriggerTracker(fileTriggerRouter, null, null, false, null);
        DirectorySnapshot snapshot = new DirectorySnapshot(fileTriggerRouter);
        tracker.takeFullSnapshot(snapshot);
        assertEquals(1, snapshot.size());
        assertEquals(snapshot.get(0).getFileName(), FileSyncUtils.getRelativePath(fileInDirectory1, directory));
    }

    @Test
    public void testTakeFullSnapshotExcludes() throws Exception {
        FileTrigger fileTrigger = new FileTrigger(directory.getAbsolutePath(), false, null, "*.txt");
        Router router = new Router();
        FileTriggerRouter fileTriggerRouter = new FileTriggerRouter(fileTrigger, router);
        FileTriggerTracker tracker = new FileTriggerTracker(fileTriggerRouter, null, null, false, null);
        DirectorySnapshot snapshot = new DirectorySnapshot(fileTriggerRouter);
        tracker.takeFullSnapshot(snapshot);
        assertEquals(1, snapshot.size());
        assertEquals(snapshot.get(0).getFileName(), FileSyncUtils.getRelativePath(fileInDirectory2, directory));
    }

    @Test
    public void testTakeSnapshotRecursiveTestDelete() throws Exception {
        FileTrigger fileTrigger = new FileTrigger(directory.getAbsolutePath(), true, null, null);
        Router router = new Router();
        FileTriggerRouter fileTriggerRouter = new FileTriggerRouter(fileTrigger, router);
        FileTriggerTracker tracker = new FileTriggerTracker(fileTriggerRouter, null, null, false, null);
        tracker.trackChanges();
        FileUtils.deleteQuietly(fileInDirectory1);
        DirectorySnapshot snapshot = tracker.trackChanges();
        assertEquals(1, snapshot.size());
        FileSnapshot change = snapshot.get(0);
        assertEquals(change.getFileName(), FileSyncUtils.getRelativePath(fileInDirectory1, directory));
        assertEquals(change.getLastEventType(), LastEventType.DELETE);
    }

    @Test
    public void testTakeSnapshotAfterRestart() throws Exception {
    }

    protected void recreateDirectorySpecAndFiles() throws Exception {
        FileUtils.deleteQuietly(snapshotDirectory);
        FileUtils.deleteQuietly(directory);
        directory.mkdirs();
        subdirectory.mkdirs();
        FileUtils.write(fileInDirectory1, "abc", Charset.defaultCharset(), false);
        FileUtils.write(fileInDirectory2, "1,2,3", Charset.defaultCharset(), false);
        FileUtils.write(fileInSubDirectory, "abc", Charset.defaultCharset(), false);
    }
}
