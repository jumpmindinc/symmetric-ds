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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.springframework.context.support.GenericXmlApplicationContext;

class SymmetricAdminTest {
    @TempDir
    Path tempDir;
    private static final SymmetricAdmin ADMIN = new SymmetricAdmin("symadmin", "<subcommand> [options] [args]", "SymAdmin.Option.");
    private static final String EXTENSION_BEAN_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<beans xmlns=\"http://www.springframework.org/schema/beans\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "    xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd\">\n"
            + "    <bean id=\"customExtension\" class=\"java.lang.String\"><constructor-arg value=\"loaded\" /></bean>\n"
            + "</beans>\n";

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

    @Test
    void testCopyExtensionsXmlFiles_copiesOnlyExtensionsXmlIntoClassesConf() throws Exception {
        File confDir = Files.createDirectories(tempDir.resolve("conf")).toFile();
        Files.write(new File(confDir, "symmetric-extensions.xml").toPath(), EXTENSION_BEAN_XML.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(confDir, "custom-extensions.xml").toPath(), EXTENSION_BEAN_XML.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(confDir, "logback.xml").toPath(), "<configuration/>".getBytes(StandardCharsets.UTF_8));
        File classesDir = tempDir.resolve("war/WEB-INF/classes").toFile();
        SymmetricAdmin.copyExtensionsXmlFiles(confDir, classesDir);
        assertTrue(new File(classesDir, "conf/symmetric-extensions.xml").exists());
        assertTrue(new File(classesDir, "conf/custom-extensions.xml").exists());
        assertFalse(new File(classesDir, "conf/logback.xml").exists());
    }

    @Test
    void testCopyExtensionsXmlFiles_withMissingConfDirCopiesNothing() throws Exception {
        File classesDir = tempDir.resolve("war/WEB-INF/classes").toFile();
        SymmetricAdmin.copyExtensionsXmlFiles(tempDir.resolve("missing").toFile(), classesDir);
        assertFalse(new File(classesDir, "conf").exists());
    }

    @Test
    void testCopyExtensionsXmlFiles_extensionIsFoundByWarClasspathImport() throws Exception {
        File confDir = Files.createDirectories(tempDir.resolve("conf")).toFile();
        Files.write(new File(confDir, "custom-extensions.xml").toPath(), EXTENSION_BEAN_XML.getBytes(StandardCharsets.UTF_8));
        File classesDir = tempDir.resolve("war/WEB-INF/classes").toFile();
        SymmetricAdmin.copyExtensionsXmlFiles(confDir, classesDir);
        try (URLClassLoader warClassLoader = new URLClassLoader(new URL[] { classesDir.toURI().toURL() }, getClass().getClassLoader());
                GenericXmlApplicationContext ctx = new GenericXmlApplicationContext()) {
            ctx.setClassLoader(warClassLoader);
            ctx.load("classpath*:/conf/*-extensions.xml");
            ctx.refresh();
            assertEquals("loaded", ctx.getBean("customExtension", String.class));
        }
    }
}
