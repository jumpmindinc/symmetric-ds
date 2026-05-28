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
package org.jumpmind.symmetric.staging.fs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jumpmind.symmetric.staging.api.IStagedResource;
import org.jumpmind.symmetric.staging.api.IStagingLock;
import org.jumpmind.symmetric.staging.api.ResourceLocation;
import org.jumpmind.symmetric.staging.api.ResourceState;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StagingOptions;
import org.jumpmind.symmetric.staging.api.StorageKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemStagingManagerTest {
    @Test
    void writeAndReadBack_byteExact(@TempDir Path stagingDir, @TempDir Path scratchDir) throws Exception {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        IStagedResource resource = manager.create(StagingOptions.plain(), "outgoing", 1L, "data");
        byte[] expected = "hello, staging".getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = resource.openOutputStream()) {
            out.write(expected);
        }
        resource.setState(ResourceState.DONE);
        try (InputStream in = resource.openInputStream()) {
            byte[] actual = in.readAllBytes();
            assertArrayEquals(expected, actual);
        }
        resource.close();
    }

    @Test
    void create_setsInitialLocationToMemory(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        IStagedResource resource = manager.create(StagingOptions.plain(), "p1");
        assertEquals(ResourceLocation.MEMORY, resource.getCurrentLocation());
    }

    @Test
    void getStorageKind_returnsFilesystem(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        assertEquals(StorageKind.FILESYSTEM, manager.getStorageKind());
    }

    @Test
    void isLocalStorageAvailable_trueWhenWritable(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        assertTrue(manager.isLocalStorageAvailable());
    }

    @Test
    void acquireLock_secondAttemptFails(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        IStagingLock first = manager.acquireLock("server-A", 60_000L, "lock", "alpha");
        IStagingLock second = manager.acquireLock("server-B", 60_000L, "lock", "alpha");
        assertTrue(first.isAcquired());
        assertFalse(second.isAcquired());
        assertNotNull(second.getFailureMessage());
        first.release();
    }

    @Test
    void breakExpiredLock_removesStaleLock(@TempDir Path stagingDir, @TempDir Path scratchDir) throws Exception {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        IStagingLock lock = manager.acquireLock("server-A", 1L, "lock", "stale");
        assertTrue(lock.isAcquired());
        Thread.sleep(50L);
        boolean broken = manager.breakExpiredLock(1L, "lock", "stale");
        assertTrue(broken);
    }

    @Test
    void byteExactGuarantee_forBinaryContent(@TempDir Path stagingDir, @TempDir Path scratchDir) throws Exception {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        byte[] random = new byte[64 * 1024];
        for (int i = 0; i < random.length; i++) {
            random[i] = (byte) (i & 0xFF);
        }
        IStagedResource resource = manager.create(StagingOptions.plain(), "bin", "blob");
        try (OutputStream out = resource.openOutputStream()) {
            out.write(random);
        }
        resource.setState(ResourceState.DONE);
        try (InputStream in = resource.openInputStream()) {
            byte[] roundTrip = in.readAllBytes();
            assertArrayEquals(random, roundTrip);
        }
    }

    @Test
    void delete_removesFile(@TempDir Path stagingDir, @TempDir Path scratchDir) throws Exception {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        IStagedResource resource = manager.create(StagingOptions.plain(), "p");
        try (OutputStream out = resource.openOutputStream()) {
            out.write(new byte[] { 1, 2, 3 });
        }
        resource.setState(ResourceState.DONE);
        assertTrue(resource.exists());
        resource.delete();
        assertFalse(resource.exists());
    }

    @Test
    void createScratchResource_landsInScratch(@TempDir Path stagingDir, @TempDir Path scratchDir) throws Exception {
        FileSystemStagingManager manager = newManager(stagingDir, scratchDir);
        IStagedResource scratch = manager.createScratchResource(StagingOptions.plain(), "tmp", "abc");
        try (OutputStream out = scratch.openOutputStream()) {
            out.write("scratch".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(ResourceLocation.FILESYSTEM_SCRATCH, scratch.getCurrentLocation());
        scratch.delete();
    }

    private static FileSystemStagingManager newManager(Path stagingDir, Path scratchDir) {
        try {
            Files.createDirectories(stagingDir);
            Files.createDirectories(scratchDir);
        } catch (Exception ignored) {
        }
        StagingConfig config = StagingConfig.builder()
                .withStorageKind(StorageKind.FILESYSTEM)
                .withStagingDir(stagingDir.toString())
                .withScratchDir(scratchDir.toString())
                .build();
        return new FileSystemStagingManager(config);
    }
}
