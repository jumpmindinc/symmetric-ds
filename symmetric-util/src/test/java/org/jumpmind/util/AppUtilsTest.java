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
import java.nio.file.Path;
import java.util.Date;
import java.util.zip.ZipEntry;

import org.apache.commons.lang3.time.DateUtils;
import static org.junit.Assert.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    public void testResolveZipEntry_validEntry() throws IOException {
        File toDir = tempDir.toFile();
        File resolved = AppUtils.resolveZipEntry(toDir, new ZipEntry("subdir/file.txt"));
        assertTrue(resolved.getCanonicalPath().startsWith(toDir.getCanonicalPath() + File.separator));
    }

    @Test
    public void testResolveZipEntry_nestedValidEntry() throws IOException {
        File toDir = tempDir.toFile();
        File resolved = AppUtils.resolveZipEntry(toDir, new ZipEntry("a/b/c/file.txt"));
        assertTrue(resolved.getCanonicalPath().startsWith(toDir.getCanonicalPath() + File.separator));
    }

    @Test
    public void testResolveZipEntry_pathTraversal() {
        File toDir = tempDir.toFile();
        assertThrows(IOException.class, () -> AppUtils.resolveZipEntry(toDir, new ZipEntry("../../etc/passwd")));
    }

    @Test
    public void testResolveZipEntry_singleLevelTraversal() {
        File toDir = tempDir.toFile();
        assertThrows(IOException.class, () -> AppUtils.resolveZipEntry(toDir, new ZipEntry("../sibling.txt")));
    }

    @Test
    public void testResolveZipEntry_absoluteValidEntry() throws IOException {
        File resolved = AppUtils.resolveZipEntry(new ZipEntry("/usr/local/sym/engines/node.properties"));
        assertEquals(new File("/usr/local/sym/engines/node.properties").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void testResolveZipEntry_absoluteTraversal() {
        assertThrows(IOException.class, () -> AppUtils.resolveZipEntry(new ZipEntry("/usr/local/../etc/passwd")));
    }

    @Test
    public void testGetLocalDateForOffset() {
        Date gmt = AppUtils.getLocalDateForOffset("+00:00");
        Date plusFour = AppUtils.getLocalDateForOffset("+04:00");
        Date minusFour = AppUtils.getLocalDateForOffset("-04:00");
        long nearZero = plusFour.getTime() - gmt.getTime() - DateUtils.MILLIS_PER_HOUR * 4;
        assertTrue(nearZero + " was the left over ms", Math.abs(nearZero) < 1000);
        nearZero = plusFour.getTime() - minusFour.getTime() - DateUtils.MILLIS_PER_HOUR * 8;
        assertTrue(nearZero + " was the left over ms", Math.abs(nearZero) < 1000);
    }
}