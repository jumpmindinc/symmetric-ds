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
package org.jumpmind.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Date;
import java.util.Set;
import java.util.zip.ZipEntry;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void testResolveZipEntry_validEntry() throws IOException {
        File toDir = tempDir.toFile();
        File resolved = AppUtils.resolveZipEntry(toDir, new ZipEntry("subdir/file.txt"));
        assertTrue(resolved.getCanonicalPath().startsWith(toDir.getCanonicalPath() + File.separator));
    }

    @Test
    void testResolveZipEntry_nestedValidEntry() throws IOException {
        File toDir = tempDir.toFile();
        File resolved = AppUtils.resolveZipEntry(toDir, new ZipEntry("a/b/c/file.txt"));
        assertTrue(resolved.getCanonicalPath().startsWith(toDir.getCanonicalPath() + File.separator));
    }

    @Test
    void testResolveZipEntry_pathTraversal() {
        File toDir = tempDir.toFile();
        assertThrows(IOException.class, () -> AppUtils.resolveZipEntry(toDir, new ZipEntry("../../etc/passwd")));
    }

    @Test
    void testResolveZipEntry_singleLevelTraversal() {
        File toDir = tempDir.toFile();
        assertThrows(IOException.class, () -> AppUtils.resolveZipEntry(toDir, new ZipEntry("../sibling.txt")));
    }

    @Test
    void testResolveZipEntry_absoluteValidEntry() throws IOException {
        File tmp = File.createTempFile("zip-entry-test", ".tmp");
        tmp.deleteOnExit();
        try {
            String canonicalPath = tmp.getCanonicalPath();
            File resolved = AppUtils.resolveZipEntry(new ZipEntry(canonicalPath));
            assertEquals(canonicalPath, resolved.getCanonicalPath());
        } finally {
            tmp.delete();
        }
    }

    @Test
    void testResolveZipEntry_absoluteTraversal() throws IOException {
        File tmp = File.createTempFile("zip-entry-test", ".tmp");
        tmp.deleteOnExit();
        try {
            String traversalName = tmp.getParentFile().getCanonicalPath()
                    + File.separator + "subdir" + File.separator + ".." + File.separator + tmp.getName();
            assertThrows(IOException.class, () -> AppUtils.resolveZipEntry(new ZipEntry(traversalName)));
        } finally {
            tmp.delete();
        }
    }

    @Test
    void testAssertPathWithinDirectory_validPath() throws IOException {
        AppUtils.assertPathWithinDirectory(Path.of("/tmp/a/file.txt"), Path.of("/tmp/a"), "file.txt");
    }

    @Test
    void testAssertPathWithinDirectory_escapesDirectory() {
        assertThrows(IOException.class, () -> AppUtils.assertPathWithinDirectory(Path.of("/etc/passwd"), Path.of("/tmp/a"), "../../etc/passwd"));
    }

    @Test
    void testAssertPathWithinDirectory_exactDirectoryMatch() {
        assertThrows(IOException.class, () -> AppUtils.assertPathWithinDirectory(Path.of("/tmp"), Path.of("/tmp/a"), ".."));
    }

    @Test
    void testCreateTempFile_usesPrefixSuffixAndSystemTempDir() throws IOException {
        File file = AppUtils.createTempFile("myprefix", ".mysuffix");
        try {
            assertTrue(file.exists());
            assertTrue(file.getName().startsWith("myprefix"));
            assertTrue(file.getName().endsWith(".mysuffix"));
            assertEquals(new File(System.getProperty("java.io.tmpdir")).getCanonicalPath(),
                    file.getParentFile().getCanonicalPath());
            assertTrue(file.canRead());
            assertTrue(file.canWrite());
        } finally {
            file.delete();
        }
    }

    @Test
    void testCreateTempFile_restrictsPermissionsToOwnerOnly() throws IOException {
        File file = AppUtils.createTempFile("apputils-test", ".tmp");
        try {
            assertTrue(file.exists());
            if (file.toPath().getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file.toPath());
                assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms);
            } else {
                assertTrue(file.canRead());
                assertTrue(file.canWrite());
            }
        } finally {
            file.delete();
        }
    }

    @Test
    void testGetLocalDateForOffset() {
        Date gmt = AppUtils.getLocalDateForOffset("+00:00");
        Date plusFour = AppUtils.getLocalDateForOffset("+04:00");
        Date minusFour = AppUtils.getLocalDateForOffset("-04:00");
        long nearZero = plusFour.getTime() - gmt.getTime() - DateUtils.MILLIS_PER_HOUR * 4;
        assertTrue(Math.abs(nearZero) < 1000, nearZero + " was the left over ms");
        nearZero = plusFour.getTime() - minusFour.getTime() - DateUtils.MILLIS_PER_HOUR * 8;
        assertTrue(Math.abs(nearZero) < 1000, nearZero + " was the left over ms");
    }
}