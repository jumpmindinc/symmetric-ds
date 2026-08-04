/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StagedResourceTest {
    @TempDir
    File tempDir;

    @Test
    void testGetGenerationTime_isStableAcrossRefreshLastUpdateTime() {
        StagingManager manager = newManager();
        StagedResource resource = new StagedResource(tempDir, "path1", manager);
        long generationTime = resource.getGenerationTime();
        resource.refreshLastUpdateTime();
        resource.refreshLastUpdateTime();
        assertEquals(generationTime, resource.getGenerationTime());
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
        StagedResource resource = new StagedResource(tempDir, "path1", manager);
        writeAndClose(resource, "brand new", true);
        assertEquals("brand new", readContent(resource));
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
        StagedResource resource = new StagedResource(tempDir, "path1", manager);
        writeAndClose(resource, "content", false);
        assertTrue(resource.isFileResource());
        assertTrue(resource.delete());
        assertTrue(!resource.getFile().exists());
    }

    @Test
    void testGetSize_reflectsWrittenContentLength() throws IOException {
        StagingManager manager = newManager();
        StagedResource resource = new StagedResource(tempDir, "path1", manager);
        writeAndClose(resource, "12345", false);
        assertEquals(5, resource.getSize());
    }

    @Test
    void testGetState_defaultsToCreateForNewResource() {
        StagingManager manager = newManager();
        StagedResource resource = new StagedResource(tempDir, "path1", manager);
        assertEquals(State.CREATE, resource.getState());
    }

    private StagingManager newManager() {
        return new StagingManager(tempDir.getAbsolutePath(), false);
    }

    private void writeAndClose(StagedResource resource, String content, boolean append) throws IOException {
        BufferedWriter writer = resource.getWriter(0, append);
        writer.write(content);
        writer.close();
        resource.close();
    }

    private String readContent(StagedResource resource) throws IOException {
        String content = IOUtils.toString(resource.getReader());
        resource.closeReaders();
        return content;
    }
}
