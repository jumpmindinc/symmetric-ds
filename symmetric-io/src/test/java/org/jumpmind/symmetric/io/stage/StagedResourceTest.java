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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StagedResourceTest {
    private static final String PATH = "outgoing/0000000001";
    @TempDir
    private File tempDir;
    private StagingManager stagingManager;
    private StagedResource resource;

    @BeforeEach
    void setUp() {
        stagingManager = new StagingManager(tempDir.getAbsolutePath(), false);
        resource = new StagedResource(tempDir, PATH, stagingManager);
    }

    @Test
    void testConstructor_startsInCreateState() {
        assertEquals(State.CREATE, resource.getState());
    }

    @Test
    void testConstructor_namesFileWithCreateExtension() {
        assertTrue(resource.getFile().getName().endsWith(".create"));
    }

    @Test
    void testConstructor_adoptsExistingDoneFile() throws IOException {
        writeText(resource, "hello");
        resource.setState(State.DONE);
        StagedResource reopened = new StagedResource(tempDir, PATH, stagingManager);
        assertEquals(State.DONE, reopened.getState());
    }

    @Test
    void testGetPath() {
        assertEquals(PATH, resource.getPath());
    }

    @Test
    void testExists_isFalseForEmptyResource() {
        assertFalse(resource.exists());
    }

    @Test
    void testExists_isTrueAfterWrite() throws IOException {
        writeText(resource, "hello");
        assertTrue(resource.exists());
    }

    @Test
    void testGetSize_isZeroForEmptyResource() {
        assertEquals(0, resource.getSize());
    }

    @Test
    void testGetSize_matchesWrittenBytes() throws IOException {
        writeText(resource, "hello");
        assertEquals(5, resource.getSize());
    }

    @Test
    void testIsFileResource_afterWritingToDisk() throws IOException {
        writeText(resource, "hello");
        assertTrue(resource.isFileResource());
    }

    @Test
    void testIsMemoryResource_isFalseForFileBackedResource() throws IOException {
        writeText(resource, "hello");
        assertFalse(resource.isMemoryResource());
    }

    @Test
    void testSetState_renamesFileToDoneExtension() throws IOException {
        writeText(resource, "hello");
        resource.setState(State.DONE);
        assertEquals(State.DONE, resource.getState());
        assertTrue(resource.getFile().getName().endsWith(".done"));
        assertTrue(resource.getFile().exists());
    }

    @Test
    void testReferenceAndDereference_trackInUse() {
        resource.reference();
        assertTrue(resource.isInUse());
        resource.dereference();
        assertFalse(resource.isInUse());
    }

    @Test
    void testIsInUse_isFalseForNewResource() {
        assertFalse(resource.isInUse());
    }

    @Test
    void testDelete_removesFileAndReference() throws IOException {
        writeText(resource, "hello");
        File file = resource.getFile();
        assertTrue(resource.delete());
        assertFalse(file.exists());
    }

    @Test
    void testDelete_onEmptyResourceReturnsFalse() {
        assertFalse(resource.delete());
    }

    @Test
    void testRefreshLastUpdateTime() {
        long before = resource.getLastUpdateTime();
        resource.refreshLastUpdateTime();
        assertTrue(resource.getLastUpdateTime() >= before);
    }

    @Test
    void testToString_showsFilePathWhenOnDisk() throws IOException {
        writeText(resource, "hello");
        assertEquals(resource.getFile().getAbsolutePath(), resource.toString());
    }

    @Test
    void testToString_showsByteCountWhenNotOnDisk() {
        assertEquals("0 bytes in memory", resource.toString());
    }

    @Test
    void testToPath_stripsDirectoryAndExtension() {
        File file = new File(tempDir, "outgoing/0000000001.create");
        assertEquals("outgoing/0000000001", StagedResource.toPath(tempDir, file));
    }

    @Test
    void testToPath_throwsWhenExtensionMissing() {
        File file = new File(tempDir, "outgoing/0000000001");
        assertThrows(IllegalStateException.class, () -> StagedResource.toPath(tempDir, file));
    }

    private void writeText(StagedResource resource, String text) throws IOException {
        BufferedWriter writer = resource.getWriter(0);
        writer.write(text);
        writer.close();
        resource.close();
    }
}
