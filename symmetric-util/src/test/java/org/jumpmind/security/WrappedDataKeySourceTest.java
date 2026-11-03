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
package org.jumpmind.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrappedDataKeySourceTest {
    private static final byte[] WRAPPED_KEY = "wrapped-data-key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER_WRAPPED_KEY = "other-data-key".getBytes(StandardCharsets.UTF_8);
    @TempDir
    Path directory;
    private Path file;
    private String originalKeyStoreFile;

    @BeforeEach
    void setUp() {
        file = directory.resolve(WrappedDataKeySource.DEFAULT_FILE_NAME);
        originalKeyStoreFile = System.getProperty(SecurityConstants.SYSPROP_KEYSTORE);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY);
        System.clearProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY_FILE);
        if (originalKeyStoreFile != null) {
            System.setProperty(SecurityConstants.SYSPROP_KEYSTORE, originalKeyStoreFile);
        } else {
            System.clearProperty(SecurityConstants.SYSPROP_KEYSTORE);
        }
    }

    @Test
    void testRead_returnsInlineKey() throws Exception {
        WrappedDataKeySource source = new WrappedDataKeySource(Base64.encodeBase64String(WRAPPED_KEY), file);
        assertArrayEquals(WRAPPED_KEY, source.read().get());
    }

    @Test
    void testRead_inlineKeyTakesPrecedenceOverFile() throws Exception {
        Files.writeString(file, Base64.encodeBase64String(OTHER_WRAPPED_KEY));
        WrappedDataKeySource source = new WrappedDataKeySource(Base64.encodeBase64String(WRAPPED_KEY), file);
        assertArrayEquals(WRAPPED_KEY, source.read().get());
    }

    @Test
    void testRead_returnsKeyFromFileIgnoringTrailingNewline() throws Exception {
        Files.writeString(file, Base64.encodeBase64String(WRAPPED_KEY) + "\n");
        assertArrayEquals(WRAPPED_KEY, new WrappedDataKeySource(null, file).read().get());
    }

    @Test
    void testRead_returnsEmptyWhenNothingSaved() throws Exception {
        assertFalse(new WrappedDataKeySource("  ", file).read().isPresent());
    }

    @Test
    void testWriteIfAbsent_savesKeyThatReadReturns() throws Exception {
        WrappedDataKeySource source = new WrappedDataKeySource(null, file);
        assertTrue(source.writeIfAbsent(WRAPPED_KEY));
        assertArrayEquals(WRAPPED_KEY, source.read().get());
    }

    @Test
    void testWriteIfAbsent_createsMissingDirectories() throws Exception {
        Path nestedFile = directory.resolve("security").resolve(WrappedDataKeySource.DEFAULT_FILE_NAME);
        assertTrue(new WrappedDataKeySource(null, nestedFile).writeIfAbsent(WRAPPED_KEY));
        assertTrue(Files.exists(nestedFile));
    }

    @Test
    void testWriteIfAbsent_restrictsFileToOwner() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        new WrappedDataKeySource(null, file).writeIfAbsent(WRAPPED_KEY);
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }

    @Test
    void testWriteIfAbsent_keepsExistingFileAndReturnsFalse() throws Exception {
        WrappedDataKeySource source = new WrappedDataKeySource(null, file);
        source.writeIfAbsent(WRAPPED_KEY);
        assertFalse(source.writeIfAbsent(OTHER_WRAPPED_KEY));
        assertArrayEquals(WRAPPED_KEY, source.read().get());
    }

    @Test
    void testWriteIfAbsent_whenAnotherWriterFinishedFirstKeepsTheirKey() throws Exception {
        WrappedDataKeySource firstWriter = new WrappedDataKeySource(null, file);
        WrappedDataKeySource secondWriter = new WrappedDataKeySource(null, file);
        assertTrue(firstWriter.writeIfAbsent(WRAPPED_KEY));
        assertFalse(secondWriter.writeIfAbsent(OTHER_WRAPPED_KEY));
        assertArrayEquals(WRAPPED_KEY, secondWriter.read().get());
    }

    @Test
    void testWriteIfAbsent_leavesNoTemporaryFiles() throws Exception {
        WrappedDataKeySource source = new WrappedDataKeySource(null, file);
        source.writeIfAbsent(WRAPPED_KEY);
        source.writeIfAbsent(OTHER_WRAPPED_KEY);
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(file), files.collect(Collectors.toList()));
        }
    }

    @Test
    void testWriteIfAbsent_throwsWhenKeyIsSetInline() {
        WrappedDataKeySource source = new WrappedDataKeySource(Base64.encodeBase64String(WRAPPED_KEY), file);
        assertThrows(IllegalStateException.class, () -> source.writeIfAbsent(OTHER_WRAPPED_KEY));
    }

    @Test
    void testDescribe_namesPropertyWhenKeyIsInline() {
        assertEquals("system property " + SecurityConstants.AWS_KMS_WRAPPED_KEY, new WrappedDataKeySource("abc", file).describe());
    }

    @Test
    void testDescribe_returnsAbsoluteFilePath() {
        assertEquals(file.toAbsolutePath().toString(), new WrappedDataKeySource(null, file).describe());
    }

    @Test
    void testFromSystemProperties_withConfiguredFile() {
        System.setProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY_FILE, file.toString());
        assertEquals(file.toAbsolutePath().toString(), WrappedDataKeySource.fromSystemProperties().describe());
    }

    @Test
    void testFromSystemProperties_defaultsToFileNextToKeystore() {
        System.setProperty(SecurityConstants.SYSPROP_KEYSTORE, directory.resolve("keystore").toString());
        assertEquals(file.toAbsolutePath().toString(), WrappedDataKeySource.fromSystemProperties().describe());
    }

    @Test
    void testFromSystemProperties_withoutKeystoreDefaultsToSecurityDirectory() {
        System.clearProperty(SecurityConstants.SYSPROP_KEYSTORE);
        Path expected = Paths.get("security", WrappedDataKeySource.DEFAULT_FILE_NAME).toAbsolutePath();
        assertEquals(expected.toString(), WrappedDataKeySource.fromSystemProperties().describe());
    }

    @Test
    void testFromSystemProperties_withInlineKey() throws Exception {
        System.setProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY, Base64.encodeBase64String(WRAPPED_KEY));
        assertArrayEquals(WRAPPED_KEY, WrappedDataKeySource.fromSystemProperties().read().get());
    }
}
