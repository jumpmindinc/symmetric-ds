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
package org.jumpmind.util;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

public class JarBuilderUnitTest {
    @Test
    public void testJarCreation() throws Exception {
        final String TEST_JAR_DIR = "target/test.jar.dir";
        File outputFile = new File("target/test.jar");
        outputFile.delete();
        assertFalse(outputFile.exists());
        FileUtils.deleteDirectory(new File(TEST_JAR_DIR));
        mkdir(TEST_JAR_DIR + "/subdir");
        mkdir(TEST_JAR_DIR + "/META-INF");
        emptyFile(TEST_JAR_DIR + "/META-INF/MANIFEST.MF");
        emptyFile(TEST_JAR_DIR + "/subdir/file2.txt");
        emptyFile(TEST_JAR_DIR + "/file2.txt");
        emptyFile("target/file1.txt");
        emptyFile(TEST_JAR_DIR + "/file3.txt");
        JarBuilder jarFile = new JarBuilder(new File(TEST_JAR_DIR), outputFile, new File[] { new File(TEST_JAR_DIR), new File("target/file1.txt") }, "3.0.0");
        jarFile.build();
        assertTrue(outputFile.exists());
        JarFile finalJar = new JarFile(outputFile);
        assertNotNull(finalJar.getEntry("subdir/file2.txt"));
        assertNotNull(finalJar.getEntry("file2.txt"));
        assertNull(finalJar.getEntry("target/test.jar.dir"));
        assertNull(finalJar.getEntry("test.jar.dir"));
        assertNull(finalJar.getEntry("file1.txt"));
        assertNotNull(finalJar.getEntry("file3.txt"));
        finalJar.close();
    }

    private void mkdir(String dir) {
        new File(dir).mkdirs();
    }

    private void emptyFile(String file) throws IOException {
        new File(file).createNewFile();
    }
}