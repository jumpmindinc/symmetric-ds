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
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jumpmind.exception.IoException;
import org.jumpmind.util.AppUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SymmetricAdminTest {
    @TempDir
    Path tempDir;
    private static final SymmetricAdmin ADMIN = new SymmetricAdmin("symadmin", "<subcommand> [options] [args]", "SymAdmin.Option.");

    private static Options inOptions() {
        return new Options().addOption(Option.builder("i").longOpt("in").hasArg().build());
    }

    private CommandLine commandLineWithIn(String path) throws Exception {
        return new DefaultParser().parse(inOptions(), new String[] { "--in", path });
    }

    @Test
    void testRestore_nullFilenameThrows() throws Exception {
        CommandLine line = new DefaultParser().parse(inOptions(), new String[0]);
        assertThrows(IoException.class, () -> ADMIN.restore(line));
    }

    @Test
    void testRestore_absoluteEntryWritesToAbsolutePath() throws Exception {
        File targetFile = tempDir.resolve("absolute-restore.txt").toFile();
        File zipFile = tempDir.resolve("backup.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry(targetFile.getAbsolutePath()));
            zos.write("restored".getBytes());
            zos.closeEntry();
        }
        ADMIN.restore(commandLineWithIn(zipFile.getAbsolutePath()));
        assertTrue(targetFile.exists());
    }

    @Test
    void testRestore_relativeEntryWritesUnderSymHome() throws Exception {
        String relativeEntry = "sym-restore-test/restore-test.txt";
        File zipFile = tempDir.resolve("backup.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry(relativeEntry));
            zos.write("restored".getBytes());
            zos.closeEntry();
        }
        File expected = new File(AppUtils.getSymHome(), relativeEntry);
        try {
            ADMIN.restore(commandLineWithIn(zipFile.getAbsolutePath()));
            assertTrue(expected.exists());
        } finally {
            expected.delete();
            expected.getParentFile().delete();
        }
    }
}
