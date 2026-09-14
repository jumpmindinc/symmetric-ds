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
package org.jumpmind.symmetric.wrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrapperTest {
    @TempDir
    private Path tempDir;

    @Test
    void testGetParentDir_withSeparator() {
        assertEquals(File.separator + "opt" + File.separator + "symmetric" + File.separator + "..",
                Wrapper.getParentDir(File.separator + "opt" + File.separator + "symmetric" + File.separator + "sym.jar"));
    }

    @Test
    void testGetParentDir_withoutSeparator() {
        assertEquals("..", Wrapper.getParentDir("sym.jar"));
    }

    @Test
    void testGetParentDir_withTrailingSeparator() {
        assertEquals(File.separator + "opt" + File.separator + "..", Wrapper.getParentDir(File.separator + "opt" + File.separator));
    }

    @Test
    void testGetParentDir_withEmptyPath() {
        assertEquals("..", Wrapper.getParentDir(""));
    }

    @Test
    void testFindConfigFile_withMatchingFile() throws Exception {
        Files.createFile(tempDir.resolve("sym_service.conf"));
        assertEquals(tempDir + File.separator + "sym_service.conf", Wrapper.findConfigFile(tempDir.toString()));
    }

    @Test
    void testFindConfigFile_withoutMatchingFile() throws Exception {
        Files.createFile(tempDir.resolve("other.properties"));
        assertEquals(tempDir + File.separator + "wrapper_service.conf", Wrapper.findConfigFile(tempDir.toString()));
    }

    @Test
    void testFindConfigFile_withEmptyDirectory() {
        assertEquals(tempDir + File.separator + "wrapper_service.conf", Wrapper.findConfigFile(tempDir.toString()));
    }

    @Test
    void testFindConfigFile_withMissingDirectory() {
        String missing = tempDir.resolve("nope").toString();
        assertEquals(missing + File.separator + "wrapper_service.conf", Wrapper.findConfigFile(missing));
    }

    @Test
    void testFindConfigFile_whenPathIsAFile() throws Exception {
        Path file = Files.createFile(tempDir.resolve("conf"));
        assertEquals(file + File.separator + "wrapper_service.conf", Wrapper.findConfigFile(file.toString()));
    }

    @Test
    void testPrintUsage() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured));
            Wrapper.printUsage();
        } finally {
            System.setOut(originalOut);
        }
        String usage = captured.toString();
        assertTrue(usage.startsWith("Usage: <start|stop|restart|install|uninstall|status|console>"));
        for (String command : new String[] { "start", "stop", "restart", "install", "uninstall", "status", "console" }) {
            assertTrue(usage.contains("   " + command), "usage is missing the " + command + " command");
        }
    }
}
