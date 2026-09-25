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

import org.apache.commons.io.IOUtils;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StagedResourceTest {
    private static final String PATH = "outgoing/0000000001";
    @TempDir
    File tempDir;
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

    @Test
    void testGetGenerationTime_isStableAcrossRefreshLastUpdateTime() {
        StagingManager manager = newManager();
        StagedResource generationResource = new StagedResource(tempDir, "path1", manager);
        long generationTime = generationResource.getGenerationTime();
        generationResource.refreshLastUpdateTime();
        generationResource.refreshLastUpdateTime();
        assertEquals(generationTime, generationResource.getGenerationTime());
    }

    @Test
    void testGetGenerationTime_forFreshlyLookedUpResource_reflectsFileLastModified() throws IOException {
        StagingManager manager = newManager();
        StagedResource original = new StagedResource(tempDir, "path1", manager);
        writeAndClose(original, "hello", false);
        StagedResource lookedUpAgain = new StagedResource(tempDir, "path1", manager);
        assertEquals(original.getFile().lastModified(), lookedUpAgain.getGenerationTime());
    }

    @Test
    void testCreate_reusingPathWithStaleLeftoverFile_doesNotInheritStaleGenerationTime() throws IOException {
        StagingManager manager = newManager();
        IStagedResource first = manager.create("path1");
        writeAndClose((StagedResource) first, "stale content from a prior attempt", false);
        first.getFile().setLastModified(System.currentTimeMillis() - 60000);
        long beforeRecreate = System.currentTimeMillis();
        IStagedResource second = manager.create("path1");
        assertTrue(second.getGenerationTime() >= beforeRecreate);
    }

    @Test
    void testGetGenerationTime_forDoneResource_isStableAcrossReconstructionAfterMultipleWrites() throws IOException {
        StagingManager manager = newManager();
        StagedResource original = new StagedResource(tempDir, "path1", manager);
        long originalGenerationTime = original.getGenerationTime();
        BufferedWriter writer = original.getWriter(0);
        writer.write("first chunk ");
        writer.flush();
        assertTrue(original.file.setLastModified(originalGenerationTime - 5000));
        writer.write("second chunk");
        original.setState(State.DONE);
        StagedResource reconstructed = new StagedResource(tempDir, "path1", manager);
        assertEquals(originalGenerationTime, reconstructed.getGenerationTime());
    }

    @Test
    void testGetWriter_nonAppendMode_overwritesExistingContent() throws IOException {
        StagingManager manager = newManager();
        StagedResource first = new StagedResource(tempDir, "path1", manager);
        writeAndClose(first, "original content", false);
        StagedResource second = new StagedResource(tempDir, "path1", manager);
        writeAndClose(second, "new", false);
        assertEquals("new", readContent(second));
    }

    @Test
    void testGetWriter_appendMode_preservesExistingContent() throws IOException {
        StagingManager manager = newManager();
        StagedResource first = new StagedResource(tempDir, "path1", manager);
        writeAndClose(first, "hello ", false);
        StagedResource second = new StagedResource(tempDir, "path1", manager);
        writeAndClose(second, "world", true);
        assertEquals("hello world", readContent(second));
    }

    @Test
    void testGetWriter_appendMode_onFreshResource_createsFileFromScratch() throws IOException {
        StagingManager manager = newManager();
        StagedResource freshResource = new StagedResource(tempDir, "path1", manager);
        writeAndClose(freshResource, "brand new", true);
        assertEquals("brand new", readContent(freshResource));
    }

    @Test
    void testGetWriter_defaultOneArgOverload_behavesSameAsNonAppend() throws IOException {
        StagingManager manager = newManager();
        StagedResource first = new StagedResource(tempDir, "path1", manager);
        writeAndClose(first, "original content", false);
        StagedResource second = new StagedResource(tempDir, "path1", manager);
        BufferedWriter writer = second.getWriter(0);
        writer.write("new");
        writer.close();
        second.close();
        assertEquals("new", readContent(second));
    }

    @Test
    void testDelete_removesFileAndReportsGone() throws IOException {
        StagingManager manager = newManager();
        StagedResource deleteResource = new StagedResource(tempDir, "path1", manager);
        writeAndClose(deleteResource, "content", false);
        assertTrue(deleteResource.isFileResource());
        assertTrue(deleteResource.delete());
        assertTrue(!deleteResource.getFile().exists());
    }

    @Test
    void testGetSize_reflectsWrittenContentLength() throws IOException {
        StagingManager manager = newManager();
        StagedResource sizeResource = new StagedResource(tempDir, "path1", manager);
        writeAndClose(sizeResource, "12345", false);
        assertEquals(5, sizeResource.getSize());
    }

    @Test
    void testGetState_defaultsToCreateForNewResource() {
        StagingManager manager = newManager();
        StagedResource stateResource = new StagedResource(tempDir, "path1", manager);
        assertEquals(State.CREATE, stateResource.getState());
    }

    private StagingManager newManager() {
        return new StagingManager(tempDir.getAbsolutePath(), false);
    }

    private void writeAndClose(StagedResource resourceToWrite, String content, boolean append) throws IOException {
        BufferedWriter writer = resourceToWrite.getWriter(0, append);
        writer.write(content);
        writer.close();
        resourceToWrite.close();
    }

    private String readContent(StagedResource resourceToRead) throws IOException {
        String content = IOUtils.toString(resourceToRead.getReader());
        resourceToRead.closeReaders();
        return content;
    }

    private void writeText(StagedResource resourceToWrite, String text) throws IOException {
        BufferedWriter writer = resourceToWrite.getWriter(0);
        writer.write(text);
        writer.close();
        resourceToWrite.close();
    }
}
