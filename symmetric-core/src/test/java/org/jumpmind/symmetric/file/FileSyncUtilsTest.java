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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import org.junit.jupiter.api.Test;

class FileSyncUtilsTest {
    @Test
    void testGetRelativePathsUnix() {
        assertEquals("stuff/xyz.dat", FileSyncUtils.getRelativePath("/var/data/stuff/xyz.dat", "/var/data/", "/"));
        assertEquals("../../b/c", FileSyncUtils.getRelativePath("/a/b/c", "/a/x/y/", "/"));
        assertEquals("../../b/c", FileSyncUtils.getRelativePath("/m/n/o/a/b/c", "/m/n/o/a/x/y/", "/"));
    }

    @Test
    void testGetRelativePathFileToFile() {
        String target = "C:\\Windows\\Boot\\Fonts\\chs_boot.ttf";
        String base = "C:\\Windows\\Speech\\Common\\sapisvr.exe";
        String relPath = FileSyncUtils.getRelativePath(target, base, "\\");
        assertEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", relPath);
    }

    @Test
    void testGetRelativePathDirectoryToFile() {
        String target = "C:\\Windows\\Boot\\Fonts\\chs_boot.ttf";
        String base = "C:\\Windows\\Speech\\Common\\";
        String relPath = FileSyncUtils.getRelativePath(target, base, "\\");
        assertEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", relPath);
    }

    @Test
    void testGetRelativePathFileToDirectory() {
        String target = "C:\\Windows\\Boot\\Fonts";
        String base = "C:\\Windows\\Speech\\Common\\foo.txt";
        String relPath = FileSyncUtils.getRelativePath(target, base, "\\");
        assertEquals("..\\..\\Boot\\Fonts", relPath);
    }

    @Test
    void testGetRelativePathDirectoryToDirectory() {
        String target = "C:\\Windows\\Boot\\";
        String base = "C:\\Windows\\Speech\\Common\\";
        String expected = "..\\..\\Boot";
        String relPath = FileSyncUtils.getRelativePath(target, base, "\\");
        assertEquals(expected, relPath);
    }

    @Test
    void testGetRelativePathDifferentDriveLetters() {
        String target = "D:\\sources\\recovery\\RecEnv.exe";
        String base = "C:\\Java\\workspace\\AcceptanceTests\\Standard test data\\geo\\";
        try {
            FileSyncUtils.getRelativePath(target, base, "\\");
            fail();
        } catch (PathResolutionException ex) {
            // expected exception
        }
    }

    @Test
    void testGetRelativePath_withFileArguments() {
        File base = new File("target/relative-base");
        File target = new File(base, "nested/item.txt");
        assertEquals("nested" + File.separator + "item.txt", FileSyncUtils.getRelativePath(target, base));
    }

    // Defect pinned, not endorsed: when target and base are the same path the common prefix is one
    // separator longer than the path itself, so the trailing substring underflows instead of returning "".
    @Test
    void testGetRelativePath_forIdenticalPaths() {
        assertThrows(StringIndexOutOfBoundsException.class, () -> FileSyncUtils.getRelativePath("/var/data", "/var/data", "/"));
    }

    @Test
    void testGetRelativePath_forChildOfBaseDirectory() {
        assertEquals("xyz.dat", FileSyncUtils.getRelativePath("/var/data/xyz.dat", "/var/data/", "/"));
    }

    @Test
    void testGetRelativePath_withUnrecognisedSeparator() {
        assertThrows(IllegalArgumentException.class, () -> FileSyncUtils.getRelativePath("/a/b", "/a/c", ":"));
    }

    @Test
    void testGetRelativePath_normalisesRedundantSegments() {
        assertEquals("b" + "/" + "c", FileSyncUtils.getRelativePath("/a/./x/../b/c", "/a/", "/"));
    }
}
