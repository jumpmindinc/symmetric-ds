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
package org.jumpmind.symmetric.io.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StagingFileLockTest {
    @TempDir
    private File tempDir;
    private StagingFileLock fileLock;
    private File lockFile;

    @BeforeEach
    void setUp() throws IOException {
        fileLock = new StagingFileLock();
        lockFile = new File(tempDir, "batch.lock");
        Files.createFile(lockFile.toPath());
        fileLock.setLockFile(lockFile);
    }

    @Test
    void testIsAcquired_isFalseByDefault() {
        assertFalse(new StagingFileLock().isAcquired());
    }

    @Test
    void testSetAcquired() {
        fileLock.setAcquired(true);
        assertTrue(fileLock.isAcquired());
    }

    @Test
    void testGetLockFile() {
        assertEquals(lockFile, fileLock.getLockFile());
    }

    @Test
    void testGetLockFailureMessage_isNullByDefault() {
        assertNull(fileLock.getLockFailureMessage());
    }

    @Test
    void testSetLockFailureMessage() {
        fileLock.setLockFailureMessage("already held");
        assertEquals("already held", fileLock.getLockFailureMessage());
    }

    @Test
    void testGetLockAge_withoutLockFile() {
        assertEquals(0, new StagingFileLock().getLockAge());
    }

    @Test
    void testGetLockAge_forFreshLockFile() {
        assertTrue(fileLock.getLockAge() < 60000);
    }

    @Test
    void testGetLockAge_forOlderLockFile() {
        lockFile.setLastModified(System.currentTimeMillis() - 120000);
        assertTrue(fileLock.getLockAge() >= 120000);
    }

    @Test
    void testGetLockAge_forMissingFileReturnsZero() {
        lockFile.delete();
        assertEquals(0, fileLock.getLockAge());
    }

    @Test
    void testReleaseLock_deletesLockFile() {
        fileLock.releaseLock();
        assertFalse(lockFile.exists());
    }

    @Test
    void testBreakLock_deletesLockFile() {
        fileLock.breakLock();
        assertFalse(lockFile.exists());
    }

    @Test
    void testBreakLock_whenFileAlreadyGone() {
        lockFile.delete();
        fileLock.breakLock();
        assertFalse(lockFile.exists());
    }

    @Test
    void testToString_includesLockFilePath() {
        assertTrue(fileLock.toString().contains(lockFile.toString()));
    }
}
