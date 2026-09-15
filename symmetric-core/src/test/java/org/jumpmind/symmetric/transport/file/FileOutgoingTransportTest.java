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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileOutgoingTransportTest {
    private Node remoteNode;
    private Node localNode;

    @BeforeEach
    void setUp() {
        remoteNode = new Node("node2", "store");
        localNode = new Node("node1", "corp");
    }

    @Test
    void testGetOutgoingDir_returnsConstructorValue(@TempDir File outgoingDir) {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        assertEquals(outgoingDir.getPath(), transport.getOutgoingDir());
    }

    @Test
    void testGetWriter_beforeOpenWriter_returnsNull(@TempDir File outgoingDir) {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        assertNull(transport.getWriter());
    }

    @Test
    void testOpenWriter_createsTempFileAndReturnsWriter(@TempDir File outgoingDir) throws IOException {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        BufferedWriter writer = transport.openWriter();
        writer.write("some,csv,data");
        writer.flush();
        assertSame(writer, transport.getWriter());
        File[] tmpFiles = outgoingDir.listFiles((dir, name) -> name.endsWith(".tmp"));
        assertEquals(1, tmpFiles.length);
        transport.close();
    }

    @Test
    void testOpenStream_createsTempFile(@TempDir File outgoingDir) throws IOException {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        transport.openStream().write(new byte[] { 1, 2, 3 });
        File[] tmpFiles = outgoingDir.listFiles((dir, name) -> name.endsWith(".tmp"));
        assertEquals(1, tmpFiles.length);
        transport.close();
    }

    @Test
    void testReadResponse_usesProcessedBatches(@TempDir File outgoingDir) throws IOException {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(42L);
        List<OutgoingBatch> processedBatches = new ArrayList<OutgoingBatch>();
        processedBatches.add(batch);
        transport.setProcessedBatches(processedBatches);
        BufferedReader reader = transport.readResponse();
        String response = reader.readLine();
        assertTrue(response.contains(WebConstants.ACK_BATCH_NAME + "42=" + WebConstants.ACK_BATCH_OK));
        assertTrue(response.contains(WebConstants.ACK_NODE_ID + "42=" + remoteNode.getNodeId()));
    }

    @Test
    void testReadResponse_usesWriterBatchIdsWhenNoProcessedBatches(@TempDir File outgoingDir) throws IOException {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        BufferedWriter writer = transport.openWriter();
        writer.write("data");
        BufferedReader reader = transport.readResponse();
        assertNull(reader.readLine());
        transport.close();
    }

    @Test
    void testGetProcessedBatches_returnsSetValue(@TempDir File outgoingDir) {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        List<OutgoingBatch> processedBatches = new ArrayList<OutgoingBatch>();
        transport.setProcessedBatches(processedBatches);
        assertSame(processedBatches, transport.getProcessedBatches());
    }

    @Test
    void testClose_setsOpenFalse(@TempDir File outgoingDir) {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testIsOpen_defaultsToTrue(@TempDir File outgoingDir) {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        assertTrue(transport.isOpen());
    }

    @Test
    void testGetSuspendIgnoreChannelLists_delegatesToConfigurationService(@TempDir File outgoingDir) {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        IConfigurationService configurationService = mock(IConfigurationService.class);
        NodeChannels expected = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(expected);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "queue", remoteNode);
        assertSame(expected, result);
    }

    @Test
    void testComplete_successWithWriter_renamesToCsv(@TempDir File outgoingDir) throws IOException {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        transport.openWriter().write("data");
        transport.close();
        transport.complete(true);
        File[] csvFiles = outgoingDir.listFiles((dir, name) -> name.endsWith(".csv"));
        assertEquals(1, csvFiles.length);
        assertEquals(0, outgoingDir.listFiles((dir, name) -> name.endsWith(".tmp")).length);
    }

    @Test
    void testComplete_successWithoutWriter_renamesToZip(@TempDir File outgoingDir) throws IOException {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        transport.openStream().write(new byte[] { 1, 2, 3 });
        transport.close();
        transport.complete(true);
        File[] zipFiles = outgoingDir.listFiles((dir, name) -> name.endsWith(".zip"));
        assertEquals(1, zipFiles.length);
    }

    @Test
    void testComplete_failure_deletesTmpFile(@TempDir File outgoingDir) throws IOException {
        FileOutgoingTransport transport = new FileOutgoingTransport(remoteNode, localNode, outgoingDir.getPath());
        transport.openWriter().write("data");
        transport.close();
        transport.complete(false);
        assertEquals(0, outgoingDir.listFiles().length);
    }
}
