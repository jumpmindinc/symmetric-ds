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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;

class PullUriHandlerTest {
    private static final String SERVER_NODE_ID = "server";
    private static final String CLIENT_NODE_ID = "client-1";
    private static final String REMOTE_HOST = "client-host";
    private static final String REMOTE_ADDRESS = "10.0.0.5";
    private static final String ENCODING = "UTF-8";
    private INodeService nodeService;
    private IRegistrationService registrationService;
    private IDataExtractorService dataExtractorService;
    private IStatisticManager statisticManager;
    private HttpServletResponse response;
    private ByteArrayOutputStream outputStream;
    private Node clientNode;
    private NodeSecurity clientSecurity;
    private PullUriHandler handler;

    @BeforeEach
    void setUp() {
        IParameterService parameterService = mock(IParameterService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        nodeService = mock(INodeService.class);
        registrationService = mock(IRegistrationService.class);
        dataExtractorService = mock(IDataExtractorService.class);
        statisticManager = mock(IStatisticManager.class);
        response = mock(HttpServletResponse.class);
        outputStream = new ByteArrayOutputStream();
        clientNode = new Node();
        clientNode.setNodeId(CLIENT_NODE_ID);
        clientNode.setNodeGroupId("client");
        clientNode.setExternalId("1");
        clientSecurity = new NodeSecurity();
        clientSecurity.setNodeId(CLIENT_NODE_ID);
        clientSecurity.setRegistrationEnabled(true);
        clientSecurity.setCreatedAtNodeId(SERVER_NODE_ID);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        when(nodeService.findIdentityNodeId()).thenReturn(SERVER_NODE_ID);
        when(nodeService.findNodeSecurity(CLIENT_NODE_ID, true)).thenReturn(clientSecurity);
        when(nodeService.findNode(CLIENT_NODE_ID)).thenReturn(clientNode);
        when(nodeService.findNode(CLIENT_NODE_ID, true)).thenReturn(clientNode);
        when(statisticManager.newProcessInfo(any(ProcessInfoKey.class))).thenAnswer(invocation -> new ProcessInfo(invocation.getArgument(0)));
        when(dataExtractorService.extract(any(ProcessInfo.class), any(Node.class), anyString(), any(IOutgoingTransport.class)))
                .thenReturn(Collections.emptyList());
        handler = new PullUriHandler(parameterService, nodeService, configurationService, dataExtractorService, registrationService,
                statisticManager, outgoingBatchService);
    }

    @Test
    void registersNodeWhenRegistrationEnabledOnDefaultQueue() throws IOException {
        handler.handlePull(CLIENT_NODE_ID, REMOTE_HOST, REMOTE_ADDRESS, outputStream, ENCODING, response, buildNodeChannels(Constants.QUEUE_DEFAULT));
        verify(registrationService).registerNode(eq(clientNode), eq(REMOTE_HOST), eq(REMOTE_ADDRESS), eq(outputStream), isNull(), isNull(), eq(false));
        verify(dataExtractorService, never()).extract(any(ProcessInfo.class), any(Node.class), anyString(), any(IOutgoingTransport.class));
    }

    @Test
    void registersNodeWhenRegistrationEnabledAndQueueHeaderMissing() throws IOException {
        handler.handlePull(CLIENT_NODE_ID, REMOTE_HOST, REMOTE_ADDRESS, outputStream, ENCODING, response, buildNodeChannels(null));
        verify(registrationService).registerNode(eq(clientNode), eq(REMOTE_HOST), eq(REMOTE_ADDRESS), eq(outputStream), isNull(), isNull(), eq(false));
    }

    @Test
    void skipsRegistrationWhenRegistrationEnabledOnNonDefaultQueue() throws IOException {
        handler.handlePull(CLIENT_NODE_ID, REMOTE_HOST, REMOTE_ADDRESS, outputStream, ENCODING, response, buildNodeChannels("reload"));
        verify(registrationService, never()).registerNode(any(Node.class), anyString(), anyString(), any(), any(), any(), eq(false));
        verify(dataExtractorService, never()).extract(any(ProcessInfo.class), any(Node.class), anyString(), any(IOutgoingTransport.class));
    }

    @Test
    void extractsDataOnNonDefaultQueueWhenRegistrationNotEnabled() throws IOException {
        clientSecurity.setRegistrationEnabled(false);
        String queue = "reload";
        handler.handlePull(CLIENT_NODE_ID, REMOTE_HOST, REMOTE_ADDRESS, outputStream, ENCODING, response, buildNodeChannels(queue));
        verify(dataExtractorService).extract(any(ProcessInfo.class), eq(clientNode), eq(queue), any(IOutgoingTransport.class));
        verify(registrationService, never()).registerNode(any(Node.class), anyString(), anyString(), any(), any(), any(), eq(false));
    }

    @Test
    void isRegistrationQueueAcceptsDefaultAndMissingQueue() {
        assertTrue(handler.isRegistrationQueue(Constants.QUEUE_DEFAULT));
        assertTrue(handler.isRegistrationQueue(null));
        assertFalse(handler.isRegistrationQueue("reload"));
    }

    private NodeChannels buildNodeChannels(String channelQueue) {
        NodeChannels map = new NodeChannels();
        map.setChannelQueue(channelQueue);
        return map;
    }
}
