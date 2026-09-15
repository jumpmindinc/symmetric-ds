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
package org.jumpmind.symmetric.transport.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.transport.file.FileIncomingTransport.FileIncomingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileIncomingTransportTest {
    private static final long OLD_ENOUGH_MILLIS = System.currentTimeMillis() - 5000;
    private Node remoteNode;
    private Node localNode;

    @BeforeEach
    void setUp() {
        remoteNode = new Node("node2", "store");
        localNode = new Node("node1", "corp");
    }

    @Test
    void testOpenReader_withNoMatchingFile_returnsEmptyReader(@TempDir File incomingDir) throws IOException {
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, null);
        BufferedReader reader = transport.openReader();
        assertNull(reader.readLine());
    }

    @Test
    void testOpenReader_withMatchingFile_returnsFileContents(@TempDir File incomingDir) throws IOException {
        File csvFile = new File(incomingDir, "store-node2.csv");
        Files.write(csvFile.toPath(), "some,csv,data".getBytes());
        csvFile.setLastModified(OLD_ENOUGH_MILLIS);
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, null);
        BufferedReader reader = transport.openReader();
        assertEquals("some,csv,data", reader.readLine());
        transport.close();
    }

    @Test
    void testOpenReader_withRecentFile_returnsEmptyReader(@TempDir File incomingDir) throws IOException {
        File csvFile = new File(incomingDir, "store-node2.csv");
        Files.write(csvFile.toPath(), "some,csv,data".getBytes());
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, null);
        BufferedReader reader = transport.openReader();
        assertNull(reader.readLine());
    }

    @Test
    void testOpenStream_withNoMatchingFile_returnsEmptyStream(@TempDir File incomingDir) throws IOException {
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, null);
        InputStream in = transport.openStream();
        assertEquals(-1, in.read());
    }

    @Test
    void testOpenStream_withMatchingFile_returnsFileContents(@TempDir File incomingDir) throws IOException {
        File zipFile = new File(incomingDir, "store-node2.zip");
        Files.write(zipFile.toPath(), new byte[] { 1, 2, 3 });
        zipFile.setLastModified(OLD_ENOUGH_MILLIS);
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, null);
        InputStream in = transport.openStream();
        assertEquals(1, in.read());
        transport.close();
    }

    @Test
    void testGetIncomingFile_withMultipleMatches_returnsFirstSorted(@TempDir File incomingDir) throws IOException {
        File second = new File(incomingDir, "store-node2.b.csv");
        File first = new File(incomingDir, "store-node2.a.csv");
        Files.write(second.toPath(), "second".getBytes());
        Files.write(first.toPath(), "first".getBytes());
        second.setLastModified(OLD_ENOUGH_MILLIS);
        first.setLastModified(OLD_ENOUGH_MILLIS);
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, null);
        File incomingFile = transport.getIncomingFile("csv");
        assertEquals(first.getName(), incomingFile.getName());
    }

    @Test
    void testClose_withNoOpenResources_doesNotThrow() {
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, "unused", null, null);
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testIsOpen_defaultsToTrue() {
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, "unused", null, null);
        assertTrue(transport.isOpen());
    }

    @Test
    void testGetRedirectionUrl_returnsNull() {
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, "unused", null, null);
        assertNull(transport.getRedirectionUrl());
    }

    @Test
    void testGetUrl_returnsEmptyString() {
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, "unused", null, null);
        assertEquals("", transport.getUrl());
    }

    @Test
    void testGetHeaders_returnsNull() {
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, "unused", null, null);
        assertNull(transport.getHeaders());
    }

    @Test
    void testComplete_withNoIncomingFile_doesNothing() {
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, "unused", null, null);
        transport.complete(true);
        transport.complete(false);
        assertNull(transport.incomingFile);
        assertTrue(transport.isOpen());
    }

    @Test
    void testComplete_successWithArchiveDir_movesFileToArchiveDir(@TempDir File incomingDir) throws IOException {
        File archiveDir = new File(incomingDir, "archive");
        archiveDir.mkdirs();
        File incomingFile = new File(incomingDir, "store-node2.csv");
        Files.write(incomingFile.toPath(), "data".getBytes());
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), archiveDir.getPath(), null);
        transport.incomingFile = incomingFile;
        transport.complete(true);
        assertFalse(incomingFile.exists());
        assertTrue(new File(archiveDir, "store-node2.csv").exists());
    }

    @Test
    void testComplete_successWithoutArchiveDir_deletesFile(@TempDir File incomingDir) throws IOException {
        File incomingFile = new File(incomingDir, "store-node2.csv");
        Files.write(incomingFile.toPath(), "data".getBytes());
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, null);
        transport.incomingFile = incomingFile;
        transport.complete(true);
        assertFalse(incomingFile.exists());
    }

    @Test
    void testComplete_failureWithErrorDir_movesFileToErrorDir(@TempDir File incomingDir) throws IOException {
        File errorDir = new File(incomingDir, "error");
        errorDir.mkdirs();
        File incomingFile = new File(incomingDir, "store-node2.csv");
        Files.write(incomingFile.toPath(), "data".getBytes());
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, errorDir.getPath());
        transport.incomingFile = incomingFile;
        transport.complete(false);
        assertFalse(incomingFile.exists());
        assertTrue(new File(errorDir, "store-node2.csv").exists());
    }

    @Test
    void testComplete_failureWithoutErrorDir_leavesFileInPlace(@TempDir File incomingDir) throws IOException {
        File incomingFile = new File(incomingDir, "store-node2.csv");
        Files.write(incomingFile.toPath(), "data".getBytes());
        FileIncomingTransport transport = new FileIncomingTransport(remoteNode, localNode, incomingDir.getPath(), null, null);
        transport.incomingFile = incomingFile;
        transport.complete(false);
        assertTrue(incomingFile.exists());
    }

    @Test
    void testFileIncomingFilter_accept_withMatchingPrefixAndSuffix(@TempDir File incomingDir) {
        FileIncomingFilter filter = new FileIncomingFilter(remoteNode, "csv");
        assertTrue(filter.accept(incomingDir, "store-node2.csv"));
    }

    @Test
    void testFileIncomingFilter_accept_withWrongPrefix(@TempDir File incomingDir) {
        FileIncomingFilter filter = new FileIncomingFilter(remoteNode, "csv");
        assertFalse(filter.accept(incomingDir, "other-node9.csv"));
    }

    @Test
    void testFileIncomingFilter_accept_withWrongExtension(@TempDir File incomingDir) {
        FileIncomingFilter filter = new FileIncomingFilter(remoteNode, "csv");
        assertFalse(filter.accept(incomingDir, "store-node2.zip"));
    }
}
