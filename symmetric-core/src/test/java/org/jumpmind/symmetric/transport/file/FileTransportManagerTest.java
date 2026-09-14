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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTransportManagerTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private FileTransportManager transportManager;
    private Node remoteNode;
    private Node localNode;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        transportManager = new FileTransportManager(engine);
        remoteNode = new Node("node2", "store");
        localNode = new Node("node1", "corp");
    }

    @Test
    void testSendAcknowledgement_returnsScOk() throws IOException {
        int result = transportManager.sendAcknowledgement(remoteNode, new ArrayList<IncomingBatch>(), localNode, "token", "http://reg");
        assertEquals(WebConstants.SC_OK, result);
    }

    @Test
    void testWriteAcknowledgement_doesNotWriteToStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transportManager.writeAcknowledgement(out, remoteNode, new ArrayList<IncomingBatch>(), localNode, "token");
        assertEquals(0, out.size());
    }

    @Test
    void testGetDirName_withTokens_replacesAndCreatesDirectory(@TempDir File tempDir) {
        String template = tempDir.getPath() + File.separator + "$(nodeGroupId)-$(nodeId)";
        when(parameterService.getString(ParameterConstants.NODE_OFFLINE_INCOMING_DIR)).thenReturn(template);
        String dirName = transportManager.getDirName(ParameterConstants.NODE_OFFLINE_INCOMING_DIR, localNode);
        assertEquals(tempDir.getPath() + File.separator + "corp-node1", dirName);
        assertTrue(new File(dirName).exists());
    }

    @Test
    void testGetDirName_withBlankValue_returnsBlankWithoutCreatingDirectory() {
        when(parameterService.getString(anyString())).thenReturn(null);
        String dirName = transportManager.getDirName(ParameterConstants.NODE_OFFLINE_INCOMING_DIR, localNode);
        assertNull(dirName);
    }

    @Test
    void testGetPullTransport_returnsConfiguredFileIncomingTransport(@TempDir File tempDir) throws IOException {
        when(parameterService.getString(ParameterConstants.NODE_OFFLINE_INCOMING_DIR)).thenReturn(tempDir.getPath());
        Map<String, String> requestProps = new HashMap<String, String>();
        IIncomingTransport transport = transportManager.getPullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertTrue(transport instanceof FileIncomingTransport);
    }

    @Test
    void testGetFilePullTransport_returnsConfiguredFileIncomingTransport(@TempDir File tempDir) throws IOException {
        when(parameterService.getString(ParameterConstants.NODE_OFFLINE_INCOMING_DIR)).thenReturn(tempDir.getPath());
        Map<String, String> requestProps = new HashMap<String, String>();
        IIncomingTransport transport = transportManager.getFilePullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertTrue(transport instanceof FileIncomingTransport);
    }

    @Test
    void testGetPushTransport_returnsConfiguredFileOutgoingTransport(@TempDir File tempDir) throws IOException {
        when(parameterService.getString(ParameterConstants.NODE_OFFLINE_OUTGOING_DIR)).thenReturn(tempDir.getPath());
        IOutgoingWithResponseTransport transport = transportManager.getPushTransport(remoteNode, localNode, "token", "http://reg");
        assertTrue(transport instanceof FileOutgoingTransport);
        assertEquals(tempDir.getPath(), ((FileOutgoingTransport) transport).getOutgoingDir());
    }

    @Test
    void testGetFilePushTransport_returnsConfiguredFileOutgoingTransport(@TempDir File tempDir) throws IOException {
        when(parameterService.getString(ParameterConstants.NODE_OFFLINE_OUTGOING_DIR)).thenReturn(tempDir.getPath());
        IOutgoingWithResponseTransport transport = transportManager.getFilePushTransport(remoteNode, localNode, "token", "http://reg");
        assertTrue(transport instanceof FileOutgoingTransport);
        assertEquals(tempDir.getPath(), ((FileOutgoingTransport) transport).getOutgoingDir());
    }
}
