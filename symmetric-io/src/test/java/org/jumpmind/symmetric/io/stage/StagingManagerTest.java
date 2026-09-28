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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StagingManagerTest {
    @TempDir
    private File tempDir;
    private StagingManager stagingManager;

    @BeforeEach
    void setUp() {
        stagingManager = new StagingManager(tempDir.getAbsolutePath(), false);
    }

    @Test
    void testBuildFilePath_joinsPartsWithSlash() {
        assertEquals("outgoing/default", stagingManager.buildFilePath("outgoing", "default"));
    }

    @Test
    void testBuildFilePath_leftPadsNumbers() {
        assertEquals("outgoing/0000000042", stagingManager.buildFilePath("outgoing", 42));
    }

    @Test
    void testBuildFilePath_withSinglePart() {
        assertEquals("outgoing", stagingManager.buildFilePath("outgoing"));
    }

    @Test
    void testBuildFilePath_withNoParts() {
        assertEquals("", stagingManager.buildFilePath());
    }

    @Test
    void testCreate_registersResourcePath() {
        stagingManager.create("outgoing", "default", 1);
        assertTrue(stagingManager.getResourceReferences().contains("outgoing/default/0000000001"));
    }

    @Test
    void testCreate_returnsWritableResource() throws IOException {
        IStagedResource resource = stagingManager.create("outgoing", "default", 1);
        writeAndClose(resource, "hello");
        assertTrue(resource.exists());
    }

    @Test
    void testCreate_replacesExistingResource() throws IOException {
        writeAndClose(stagingManager.create("outgoing", 1), "first");
        IStagedResource replacement = stagingManager.create("outgoing", 1);
        assertFalse(replacement.exists());
    }

    @Test
    void testCreate_throwsWhenFreeSpaceBelowThreshold() {
        StagingManager tightManager = new StagingManager(tempDir.getAbsolutePath(), false, Long.MAX_VALUE);
        assertThrows(StagingLowFreeSpace.class, () -> tightManager.create("outgoing", 1));
    }

    @Test
    void testFind_returnsResourceInUse() {
        IStagedResource created = stagingManager.create("outgoing", 1);
        assertSame(created, stagingManager.find("outgoing", 1));
    }

    @Test
    void testFind_withUnknownPathReturnsNull() {
        assertNull(stagingManager.find("outgoing", 99));
    }

    @Test
    void testFind_locatesResourceOnDiskAfterRemoval() throws IOException {
        writeAndClose(stagingManager.create("outgoing", 1), "hello");
        stagingManager.removeResourcePath("outgoing/0000000001");
        assertNotNull(stagingManager.find("outgoing/0000000001"));
    }

    @Test
    void testRemoveResourcePath_clearsReference() {
        stagingManager.create("outgoing", 1);
        stagingManager.removeResourcePath("outgoing/0000000001");
        assertFalse(stagingManager.getResourceReferences().contains("outgoing/0000000001"));
    }

    @Test
    void testGetResourceReferences_isEmptyForNewManager() {
        assertTrue(stagingManager.getResourceReferences().isEmpty());
    }

    @Test
    void testGetResourceReferences_isSorted() {
        stagingManager.create("outgoing", 2);
        stagingManager.create("outgoing", 1);
        assertEquals("outgoing/0000000001", stagingManager.getResourceReferences().iterator().next());
    }

    @Test
    void testAcquireFileLock_createsLockFile() {
        StagingFileLock fileLock = stagingManager.acquireFileLock("server-1", "outgoing", 1);
        assertTrue(fileLock.isAcquired());
        assertTrue(fileLock.getLockFile().exists());
    }

    @Test
    void testAcquireFileLock_failsWhenAlreadyHeld() {
        stagingManager.acquireFileLock("server-1", "outgoing", 1);
        StagingFileLock second = stagingManager.acquireFileLock("server-2", "outgoing", 1);
        assertFalse(second.isAcquired());
    }

    @Test
    void testAcquireFileLock_succeedsAfterRelease() {
        stagingManager.acquireFileLock("server-1", "outgoing", 1).releaseLock();
        assertTrue(stagingManager.acquireFileLock("server-2", "outgoing", 1).isAcquired());
    }

    @Test
    void testClean_removesExpiredDoneResources() throws IOException {
        IStagedResource resource = stagingManager.create("outgoing", 1);
        writeAndClose(resource, "hello");
        resource.setState(State.DONE);
        resource.getFile().setLastModified(System.currentTimeMillis() - 120000);
        stagingManager.clean(60000);
        assertFalse(stagingManager.getResourceReferences().contains("outgoing/0000000001"));
    }

    @Test
    void testClean_keepsFreshResources() throws IOException {
        IStagedResource resource = stagingManager.create("outgoing", 1);
        writeAndClose(resource, "hello");
        resource.setState(State.DONE);
        stagingManager.clean(600000);
        assertTrue(stagingManager.getResourceReferences().contains("outgoing/0000000001"));
    }

    private void writeAndClose(IStagedResource resource, String text) throws IOException {
        BufferedWriter writer = resource.getWriter(0);
        writer.write(text);
        writer.close();
        resource.close();
    }
}
