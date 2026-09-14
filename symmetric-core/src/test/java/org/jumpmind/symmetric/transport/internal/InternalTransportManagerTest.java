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
package org.jumpmind.symmetric.transport.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.SyncDisabledException;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InternalTransportManagerTest {
    private InternalTransportManager manager;
    private ISymmetricEngine engine;
    private ISymmetricEngine targetEngine;
    private Node remoteNode;
    private Node localNode;
    private IParameterService parameterService;
    private INodeService nodeService;
    private IExtensionService extensionService;
    private IAcknowledgeService acknowledgeService;
    private IRegistrationService registrationService;
    private IIncomingBatchService incomingBatchService;
    private IOutgoingBatchService outgoingBatchService;
    private IConfigurationService configurationService;
    private INodeCommunicationService nodeCommunicationService;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        targetEngine = mock(ISymmetricEngine.class);
        remoteNode = mock(Node.class);
        localNode = mock(Node.class);
        parameterService = mock(IParameterService.class);
        nodeService = mock(INodeService.class);
        extensionService = mock(IExtensionService.class);
        acknowledgeService = mock(IAcknowledgeService.class);
        registrationService = mock(IRegistrationService.class);
        incomingBatchService = mock(IIncomingBatchService.class);
        outgoingBatchService = mock(IOutgoingBatchService.class);
        configurationService = mock(IConfigurationService.class);
        nodeCommunicationService = mock(INodeCommunicationService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getIncomingBatchService()).thenReturn(incomingBatchService);
        when(targetEngine.getNodeService()).thenReturn(nodeService);
        when(targetEngine.getAcknowledgeService()).thenReturn(acknowledgeService);
        when(targetEngine.getParameterService()).thenReturn(parameterService);
        when(targetEngine.getRegistrationService()).thenReturn(registrationService);
        when(targetEngine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(targetEngine.getConfigurationService()).thenReturn(configurationService);
        when(targetEngine.getNodeCommunicationService()).thenReturn(nodeCommunicationService);
        when(remoteNode.getNodeId()).thenReturn("remote-001");
        when(remoteNode.getSyncUrl()).thenReturn("internal://server");
        when(localNode.getNodeId()).thenReturn("local-001");
        manager = spy(new InternalTransportManager(engine));
    }

    @Test
    void testConstructor() {
        InternalTransportManager tm = new InternalTransportManager(engine);
        assertNotNull(tm);
    }

    @Test
    void testGetPingTransport_returnsTransportWhenEngineExists() throws IOException {
        when(nodeService.findIdentityNodeId()).thenReturn("server-001");
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        IIncomingTransport transport = manager.getPingTransport(remoteNode, localNode, "http://reg");
        assertNotNull(transport);
    }

    @Test
    void testGetPingTransport_returnsNullWhenEngineIsNull() throws IOException {
        doReturn(null).when(manager).getTargetEngine(anyString());
        IIncomingTransport transport = manager.getPingTransport(remoteNode, localNode, "http://reg");
        assertNull(transport);
    }

    @Test
    void testGetPingTransport_returnsNullWhenIdentityNodeIdIsNull() throws IOException {
        when(nodeService.findIdentityNodeId()).thenReturn(null);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        IIncomingTransport transport = manager.getPingTransport(remoteNode, localNode, "http://reg");
        assertNull(transport);
    }

    @Test
    void testGetTargetEngine_throwsNullPointerWhenEngineNotFound() {
        assertThrows(NullPointerException.class,
                () -> new InternalTransportManager(engine).getTargetEngine("internal://nonexistent"));
    }

    @Test
    void testSendAcknowledgement_returnsOkForEmptyList() throws IOException {
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        int result = manager.sendAcknowledgement(remoteNode, Collections.emptyList(), localNode, "token", "http://reg");
        assertEquals(WebConstants.SC_OK, result);
        verify(acknowledgeService, never()).ack(any(BatchAck.class));
    }

    @Test
    void testSendAcknowledgement_returnsOkForNullList() throws IOException {
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        int result = manager.sendAcknowledgement(remoteNode, null, localNode, "token", "http://reg");
        assertEquals(WebConstants.SC_OK, result);
        verify(acknowledgeService, never()).ack(any(BatchAck.class));
    }

    @Test
    void testSendAcknowledgement_processesBatches() throws IOException {
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        when(remoteNode.requires13Compatiblity()).thenReturn(false);
        List<IncomingBatch> batches = new ArrayList<>();
        IncomingBatch batch = new IncomingBatch();
        batch.setBatchId(1);
        batch.setStatus(Status.OK);
        batches.add(batch);
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", "http://reg");
        assertEquals(WebConstants.SC_OK, result);
        verify(acknowledgeService).ack(any(BatchAck.class));
    }

    @Test
    void testSendAcknowledgement_handlesException() throws IOException {
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        when(remoteNode.requires13Compatiblity()).thenThrow(new RuntimeException("Test exception"));
        List<IncomingBatch> batches = new ArrayList<>();
        IncomingBatch batch = new IncomingBatch();
        batch.setBatchId(1);
        batch.setStatus(Status.OK);
        batches.add(batch);
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", "http://reg");
        assertEquals(-1, result);
    }

    @Test
    void testSendAcknowledgement_withRequestProperties() throws IOException {
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        when(remoteNode.requires13Compatiblity()).thenReturn(false);
        List<IncomingBatch> batches = new ArrayList<>();
        IncomingBatch batch = new IncomingBatch();
        batch.setBatchId(1);
        batch.setStatus(Status.OK);
        batches.add(batch);
        Map<String, String> requestProps = new HashMap<>();
        requestProps.put("key", "value");
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", requestProps, "http://reg");
        assertEquals(WebConstants.SC_OK, result);
    }

    @Test
    void testSendCopyRequest_returnsOk() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        when(parameterService.getExternalId()).thenReturn("new-external-id");
        when(parameterService.getNodeGroupId()).thenReturn("new-group");
        when(localNode.getNodeId()).thenReturn("old-node-001");
        when(nodeService.findIdentityNodeId()).thenReturn("server-001");
        when(registrationService.openRegistration(anyString(), anyString())).thenReturn("new-node-001");
        when(incomingBatchService.findMaxBatchIdsByChannel()).thenReturn(new HashMap<String, BatchId>());
        when(configurationService.getChannels(false)).thenReturn(new HashMap<String, Channel>());
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        int result = manager.sendCopyRequest(localNode);
        assertEquals(WebConstants.SC_OK, result);
    }

    @Test
    void testSendCopyRequest_handlesException() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        when(targetEngine.getNodeService()).thenThrow(new RuntimeException("Test exception"));
        int result = manager.sendCopyRequest(localNode);
        assertEquals(-1, result);
    }

    @Test
    void testSendCopyRequest_copiesBatchesForMatchingChannels() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        when(parameterService.getExternalId()).thenReturn("new-external-id");
        when(parameterService.getNodeGroupId()).thenReturn("new-group");
        when(localNode.getNodeId()).thenReturn("old-node-001");
        when(nodeService.findIdentityNodeId()).thenReturn("server-001");
        when(registrationService.openRegistration(anyString(), anyString())).thenReturn("new-node-001");
        Map<String, BatchId> batchIds = new HashMap<String, BatchId>();
        BatchId batchId = new BatchId();
        batchId.setBatchId(100);
        batchId.setNodeId("server-001");
        batchIds.put("default", batchId);
        when(incomingBatchService.findMaxBatchIdsByChannel()).thenReturn(batchIds);
        Map<String, Channel> channels = new HashMap<String, Channel>();
        Channel channel = new Channel();
        channel.setChannelId("default");
        channels.put("default", channel);
        when(configurationService.getChannels(false)).thenReturn(channels);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        int result = manager.sendCopyRequest(localNode);
        assertEquals(WebConstants.SC_OK, result);
        verify(outgoingBatchService).copyOutgoingBatches(eq("default"), eq(100L), eq("old-node-001"), eq("new-node-001"));
    }

    @Test
    void testSendStatusRequest_returnsOk() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        Map<String, String> statuses = new HashMap<String, String>();
        int result = manager.sendStatusRequest(localNode, statuses);
        assertEquals(WebConstants.SC_OK, result);
    }

    @Test
    void testSendStatusRequest_updatesBatchCounts() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        when(localNode.getNodeId()).thenReturn("local-001");
        Map<String, Integer> queueCounts = new HashMap<String, Integer>();
        queueCounts.put("default", 5);
        when(nodeCommunicationService.parseQueueToBatchCounts(anyString())).thenReturn(queueCounts);
        Map<String, String> statuses = new HashMap<String, String>();
        statuses.put(WebConstants.BATCH_TO_SEND_COUNT, "default:5");
        int result = manager.sendStatusRequest(localNode, statuses);
        assertEquals(WebConstants.SC_OK, result);
        verify(nodeCommunicationService).parseQueueToBatchCounts("default:5");
        verify(nodeCommunicationService).updateBatchToSendCounts(eq("local-001"), eq(queueCounts));
    }

    @Test
    void testSendStatusRequest_handlesException() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        when(targetEngine.getNodeCommunicationService()).thenThrow(new RuntimeException("Test exception"));
        Map<String, String> statuses = new HashMap<String, String>();
        statuses.put(WebConstants.BATCH_TO_SEND_COUNT, "default:5");
        int result = manager.sendStatusRequest(localNode, statuses);
        assertEquals(-1, result);
    }

    @Test
    void testWriteAcknowledgement() throws IOException {
        when(remoteNode.requires13Compatiblity()).thenReturn(false);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        IncomingBatch batch = new IncomingBatch();
        batch.setBatchId(1);
        batch.setStatus(Status.OK);
        batches.add(batch);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manager.writeAcknowledgement(out, remoteNode, batches, localNode, "token");
        String output = out.toString(StandardCharsets.UTF_8);
        assertNotNull(output);
        assertEquals(true, output.contains("batch-1=ok"));
    }

    @Test
    void testCheckIfRegistrationIsOpen_throwsWhenPushNotAllowed() {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(false);
        ISymmetricEngine clientEngine = mock(ISymmetricEngine.class);
        when(clientEngine.getParameterService()).thenReturn(parameterService);
        Node server = mock(Node.class);
        assertThrows(RegistrationNotOpenException.class, () -> manager.checkIfRegistrationIsOpen(clientEngine, server));
    }

    @Test
    void testCheckIfRegistrationIsOpen_throwsWhenRegistrationNotOpen() {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(true);
        when(registrationService.isRegisteredWithServer()).thenReturn(true);
        when(registrationService.isRegistrationOpen()).thenReturn(false);
        ISymmetricEngine clientEngine = mock(ISymmetricEngine.class);
        when(clientEngine.getParameterService()).thenReturn(parameterService);
        when(clientEngine.getRegistrationService()).thenReturn(registrationService);
        Node server = mock(Node.class);
        assertThrows(RegistrationNotOpenException.class, () -> manager.checkIfRegistrationIsOpen(clientEngine, server));
    }

    @Test
    void testCheckIfRegistrationIsOpen_throwsWhenUrlMismatch() {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(true);
        when(registrationService.isRegisteredWithServer()).thenReturn(false);
        when(parameterService.getRegistrationUrl()).thenReturn("internal://expected");
        ISymmetricEngine clientEngine = mock(ISymmetricEngine.class);
        when(clientEngine.getParameterService()).thenReturn(parameterService);
        when(clientEngine.getRegistrationService()).thenReturn(registrationService);
        Node server = mock(Node.class);
        when(server.getSyncUrl()).thenReturn("internal://different");
        assertThrows(RegistrationNotOpenException.class, () -> manager.checkIfRegistrationIsOpen(clientEngine, server));
    }

    @Test
    void testGetComparePullTransport_returnsNull() throws IOException {
        assertNull(manager.getComparePullTransport(remoteNode, localNode, "token", "http://reg", new HashMap<String, String>()));
    }

    @Test
    void testGetComparePushTransport_returnsNull() throws IOException {
        assertNull(manager.getComparePushTransport(remoteNode, localNode, "token", "http://reg", new HashMap<String, String>()));
    }

    @Test
    void testGetFilePullTransport_returnsIncomingTransport() throws IOException {
        NodeChannels nodeChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(nodeChannels);
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getFilePullTransport(remoteNode, localNode, "token", null, "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalIncomingTransport.class, transport);
    }

    @Test
    void testGetPullTransport_returnsIncomingTransport() throws IOException {
        NodeChannels nodeChannels = new NodeChannels();
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(nodeChannels);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getPullTransport(remoteNode, localNode, "token", null, "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalIncomingTransport.class, transport);
    }

    @Test
    void testGetPullTransport_setsChannelQueueFromRequestProperties() throws IOException {
        NodeChannels nodeChannels = new NodeChannels();
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(nodeChannels);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        Map<String, String> requestProperties = new HashMap<String, String>();
        requestProperties.put(WebConstants.CHANNEL_QUEUE, "custom-queue");
        IIncomingTransport transport = manager.getPullTransport(remoteNode, localNode, "token", requestProperties, "http://reg");
        assertNotNull(transport);
        assertEquals("custom-queue", nodeChannels.getChannelQueue());
    }

    @Test
    void testGetPullTransport_throwsSyncDisabledExceptionWhenTargetNodeIsDisabled() {
        Node disabledNode = mock(Node.class);
        when(disabledNode.isSyncEnabled()).thenReturn(false);
        when(nodeService.findNode(anyString(), eq(true))).thenReturn(disabledNode);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        assertThrows(SyncDisabledException.class,
                () -> manager.getPullTransport(remoteNode, localNode, "token", null, "http://reg"));
        verify(manager, never()).runAtClient(anyString(), any(), any(), any());
    }

    @Test
    void testGetPullTransport_proceedsWhenRegistrationOpenDespiteSyncDisabled() throws IOException {
        Node disabledNode = mock(Node.class);
        when(disabledNode.isSyncEnabled()).thenReturn(false);
        when(nodeService.findNode(anyString(), eq(true))).thenReturn(disabledNode);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationEnabled(true);
        when(nodeService.findNodeSecurity(anyString(), eq(true))).thenReturn(nodeSecurity);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(new NodeChannels());
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getPullTransport(remoteNode, localNode, "token", null, "http://reg");
        assertNotNull(transport);
    }

    @Test
    void testGetPullTransport_proceedsWhenTargetNodeIsEnabled() throws IOException {
        Node enabledNode = mock(Node.class);
        when(enabledNode.isSyncEnabled()).thenReturn(true);
        when(nodeService.findNode(anyString(), eq(true))).thenReturn(enabledNode);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(new NodeChannels());
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getPullTransport(remoteNode, localNode, "token", null, "http://reg");
        assertNotNull(transport);
    }

    @Test
    void testGetPushTransport_returnsOutgoingWithResponseTransport() throws IOException {
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(new NodeChannels());
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IOutgoingWithResponseTransport transport = manager.getPushTransport(remoteNode, localNode, "token", "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalOutgoingWithResponseTransport.class, transport);
    }

    @Test
    void testGetPushTransport_throwsSyncDisabledExceptionWhenTargetNodeIsDisabled() {
        Node disabledNode = mock(Node.class);
        when(disabledNode.isSyncEnabled()).thenReturn(false);
        when(nodeService.findNode(anyString(), eq(true))).thenReturn(disabledNode);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        assertThrows(SyncDisabledException.class,
                () -> manager.getPushTransport(remoteNode, localNode, "token", "http://reg"));
        verify(manager, never()).runAtClient(anyString(), any(), any(), any());
    }

    @Test
    void testGetPushTransport_proceedsWhenRegistrationOpenDespiteSyncDisabled() throws IOException {
        Node disabledNode = mock(Node.class);
        when(disabledNode.isSyncEnabled()).thenReturn(false);
        when(nodeService.findNode(anyString(), eq(true))).thenReturn(disabledNode);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationEnabled(true);
        when(nodeService.findNodeSecurity(anyString(), eq(true))).thenReturn(nodeSecurity);
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(new NodeChannels());
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IOutgoingWithResponseTransport transport = manager.getPushTransport(remoteNode, localNode, "token", "http://reg");
        assertNotNull(transport);
    }

    @Test
    void testGetPushTransport_proceedsWhenTargetNodeIsEnabled() throws IOException {
        Node enabledNode = mock(Node.class);
        when(enabledNode.isSyncEnabled()).thenReturn(true);
        when(nodeService.findNode(anyString(), eq(true))).thenReturn(enabledNode);
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(new NodeChannels());
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IOutgoingWithResponseTransport transport = manager.getPushTransport(remoteNode, localNode, "token", "http://reg");
        assertNotNull(transport);
    }

    @Test
    void testGetPushTransport_withRequestProperties() throws IOException {
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(new NodeChannels());
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        Map<String, String> requestProperties = new HashMap<String, String>();
        requestProperties.put(WebConstants.CHANNEL_QUEUE, "custom-queue");
        IOutgoingWithResponseTransport transport = manager.getPushTransport(remoteNode, localNode, "token", requestProperties, "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalOutgoingWithResponseTransport.class, transport);
    }

    @Test
    void testGetPushTransport_fetchesRemoteSuspendIgnoreConfig() throws IOException {
        NodeChannels remoteNodeChannels = new NodeChannels();
        remoteNodeChannels.addSuspendChannels("local-001", "suspended-channel");
        when(configurationService.getSuspendIgnoreChannelLists(eq("local-001"))).thenReturn(remoteNodeChannels);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IOutgoingWithResponseTransport transport = manager.getPushTransport(remoteNode, localNode, "token", "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalOutgoingWithResponseTransport.class, transport);
        verify(configurationService).getSuspendIgnoreChannelLists(eq("local-001"));
    }

    @Test
    void testGetPushTransport_combinesRemoteAndLocalSuspendIgnoreConfig() throws IOException {
        NodeChannels remoteNodeChannels = new NodeChannels();
        remoteNodeChannels.addSuspendChannels("local-001", "remote-suspend");
        remoteNodeChannels.addIgnoreChannels("local-001", "remote-ignore");
        when(configurationService.getSuspendIgnoreChannelLists(eq("local-001"))).thenReturn(remoteNodeChannels);
        NodeChannels localNodeChannels = new NodeChannels();
        localNodeChannels.addSuspendChannels("local-001", "local-suspend");
        localNodeChannels.addIgnoreChannels("local-001", "local-ignore");
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localNodeChannels);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IOutgoingWithResponseTransport transport = manager.getPushTransport(remoteNode, localNode, "token", "http://reg");
        assertNotNull(transport);
        Node mockTargetNode = mock(Node.class);
        when(mockTargetNode.getNodeId()).thenReturn("local-001");
        NodeChannels combined = transport.getSuspendIgnoreChannelLists(configurationService, "default", mockTargetNode);
        String suspendChannels = combined.getSuspendChannelsAsString("local-001");
        String ignoreChannels = combined.getIgnoreChannelsAsString("local-001");
        assertTrue(suspendChannels.contains("remote-suspend"));
        assertTrue(suspendChannels.contains("local-suspend"));
        assertTrue(ignoreChannels.contains("remote-ignore"));
        assertTrue(ignoreChannels.contains("local-ignore"));
    }

    @Test
    void testGetFilePushTransport_returnsOutgoingWithResponseTransport() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IOutgoingWithResponseTransport transport = manager.getFilePushTransport(remoteNode, localNode, "token", "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalOutgoingWithResponseTransport.class, transport);
    }

    @Test
    void testGetRegisterTransport_returnsIncomingTransport() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getRegisterTransport(localNode, "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalIncomingTransport.class, transport);
    }

    @Test
    void testGetRegisterTransport_withRequestProperties() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        Map<String, String> requestProperties = new HashMap<String, String>();
        requestProperties.put(WebConstants.PUSH_REGISTRATION, "true");
        IIncomingTransport transport = manager.getRegisterTransport(localNode, "http://reg", requestProperties);
        assertNotNull(transport);
        assertInstanceOf(InternalIncomingTransport.class, transport);
    }

    @Test
    void testGetRegisterPushTransport_returnsOutgoingWithResponseTransport() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IOutgoingWithResponseTransport transport = manager.getRegisterPushTransport(remoteNode, localNode);
        assertNotNull(transport);
        assertInstanceOf(InternalOutgoingWithResponseTransport.class, transport);
    }

    @Test
    void testGetConfigTransport_returnsIncomingTransport() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getConfigTransport(remoteNode, localNode, "token", "3.14.0", "3.14.0", "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalIncomingTransport.class, transport);
    }

    @Test
    void testGetBandwidthPullTransport_returnsIncomingTransport() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getBandwidthPullTransport(remoteNode, localNode, "token", null, "http://reg", 1000);
        assertNotNull(transport);
        assertInstanceOf(InternalIncomingTransport.class, transport);
    }

    @Test
    void testGetBandwidthPullTransport_usesDefaultSampleSizeWhenZero() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getBandwidthPullTransport(remoteNode, localNode, "token", null, "http://reg", 0);
        assertNotNull(transport);
    }

    @Test
    void testGetBandwidthPullTransport_usesDefaultSampleSizeWhenNegative() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IIncomingTransport transport = manager.getBandwidthPullTransport(remoteNode, localNode, "token", null, "http://reg", -1);
        assertNotNull(transport);
    }

    @Test
    void testGetBandwidthPushTransport_returnsOutgoingWithResponseTransport() throws IOException {
        doNothing().when(manager).runAtClient(anyString(), any(), any(), any());
        IOutgoingWithResponseTransport transport = manager.getBandwidthPushTransport(remoteNode, localNode, "token", null, "http://reg");
        assertNotNull(transport);
        assertInstanceOf(InternalOutgoingWithResponseTransport.class, transport);
    }

    @Test
    void testHandlePull_whenNodeSecurityIsNull() throws IOException {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(targetEngine.getStatisticManager()).thenReturn(statisticManager);
        when(nodeService.findNodeSecurity(anyString(), anyBoolean())).thenReturn(null);
        NodeChannels suspendIgnoreChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        Map<String, String> headers = new ConcurrentHashMap<String, String>();
        CountDownLatch headersReady = new CountDownLatch(1);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        manager.handlePull(targetEngine, localNode, suspendIgnoreChannels, null, headers, headersReady, os);
        assertEquals(0, headersReady.getCount());
        verify(statisticManager).incrementNodesPulled(1);
    }

    @Test
    void testHandlePull_whenRegistrationEnabled() throws IOException {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(targetEngine.getStatisticManager()).thenReturn(statisticManager);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationEnabled(true);
        nodeSecurity.setCreatedAtNodeId("server-001");
        when(nodeService.findNodeSecurity(anyString(), anyBoolean())).thenReturn(nodeSecurity);
        when(nodeService.findIdentityNodeId()).thenReturn("server-001");
        when(registrationService.registerNode(any(Node.class), anyString(), anyString(), any(OutputStream.class), any(),
                any(), anyBoolean())).thenReturn(true);
        NodeChannels suspendIgnoreChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        Map<String, String> headers = new ConcurrentHashMap<String, String>();
        CountDownLatch headersReady = new CountDownLatch(1);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        manager.handlePull(targetEngine, localNode, suspendIgnoreChannels, null, headers, headersReady, os);
        assertEquals(0, headersReady.getCount());
        verify(registrationService).registerNode(any(Node.class), anyString(), anyString(), any(OutputStream.class),
                any(), any(), anyBoolean());
    }

    @Test
    void testHandlePull_extractsDataWhenNotRegistering() throws IOException {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IDataExtractorService dataExtractorService = mock(IDataExtractorService.class);
        when(targetEngine.getStatisticManager()).thenReturn(statisticManager);
        when(targetEngine.getDataExtractorService()).thenReturn(dataExtractorService);
        when(targetEngine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationEnabled(false);
        when(nodeService.findNodeSecurity(anyString(), anyBoolean())).thenReturn(nodeSecurity);
        when(nodeService.findIdentityNodeId()).thenReturn("server-001");
        ProcessInfo processInfo = mock(ProcessInfo.class);
        when(statisticManager.newProcessInfo(any(ProcessInfoKey.class))).thenReturn(processInfo);
        when(dataExtractorService.extract(any(ProcessInfo.class), any(Node.class), any())).thenReturn(new ArrayList<OutgoingBatch>());
        NodeChannels suspendIgnoreChannels = new NodeChannels();
        suspendIgnoreChannels.setChannelQueue("default");
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        Map<String, String> headers = new ConcurrentHashMap<String, String>();
        CountDownLatch headersReady = new CountDownLatch(1);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        manager.handlePull(targetEngine, localNode, suspendIgnoreChannels, null, headers, headersReady, os);
        verify(dataExtractorService).extract(any(ProcessInfo.class), any(Node.class), eq("default"), any());
    }

    @Test
    void testHandlePush_loadsDataAndUpdatesStatistics() throws IOException {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IDataLoaderService dataLoaderService = mock(IDataLoaderService.class);
        when(targetEngine.getStatisticManager()).thenReturn(statisticManager);
        when(targetEngine.getDataLoaderService()).thenReturn(dataLoaderService);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        manager.handlePush(targetEngine, localNode, "default", null, os);
        verify(dataLoaderService).loadDataFromPush(eq(localNode), eq("default"), any(), any());
        verify(statisticManager).incrementNodesPushed(1);
    }

    @Test
    void testAddReadyQueuesHeader_noExceptionWhenValueNotNull() {
        when(targetEngine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        Map<String, String> headers = new ConcurrentHashMap<String, String>();
        manager.addReadyQueuesHeader(targetEngine, "node-001", headers);
        assertNotNull(headers);
    }

    @Test
    void testAddPendingBatchCounts_noExceptionWhenValueNotNull() {
        when(targetEngine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        Map<String, String> headers = new ConcurrentHashMap<String, String>();
        manager.addPendingBatchCounts(targetEngine, "node-001", headers);
        assertNotNull(headers);
    }

    @Test
    void testLogDataReceivedFromPull_noExceptionWhenBatchesExist() {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        OutgoingBatch batch = new OutgoingBatch();
        batch.setStatus(OutgoingBatch.Status.LD);
        batch.setDataRowCount(10);
        batchList.add(batch);
        manager.logDataReceivedFromPull(localNode, batchList);
    }

    @Test
    void testLogDataReceivedFromPull_noExceptionWithEmptyList() {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        manager.logDataReceivedFromPull(localNode, batchList);
    }

    @Test
    void testRegisterNode_extractsUserIdAndPasswordFromRequestProperties() throws IOException {
        when(registrationService.registerNode(any(Node.class), anyString(), anyString(), any(OutputStream.class),
                eq("user123"), eq("pass456"), anyBoolean())).thenReturn(true);
        Map<String, String> requestProperties = new HashMap<String, String>();
        requestProperties.put(WebConstants.REG_USER_ID, "user123");
        requestProperties.put(WebConstants.REG_PASSWORD, "pass456");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        boolean result = manager.registerNode(targetEngine, localNode, requestProperties, os);
        assertTrue(result);
        verify(registrationService).registerNode(any(Node.class), anyString(), anyString(), any(OutputStream.class),
                eq("user123"), eq("pass456"), anyBoolean());
    }

    @Test
    void testRegisterNode_handlesNullRequestProperties() throws IOException {
        when(registrationService.registerNode(any(Node.class), anyString(), anyString(), any(OutputStream.class),
                eq(null), eq(null), anyBoolean())).thenReturn(true);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        boolean result = manager.registerNode(targetEngine, localNode, null, os);
        assertTrue(result);
    }

    @Test
    void testCheckIfRegistrationIsOpen_noExceptionWhenAllConditionsMet() {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(true);
        when(registrationService.isRegisteredWithServer()).thenReturn(false);
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        ISymmetricEngine clientEngine = mock(ISymmetricEngine.class);
        when(clientEngine.getParameterService()).thenReturn(parameterService);
        when(clientEngine.getRegistrationService()).thenReturn(registrationService);
        Node server = mock(Node.class);
        when(server.getSyncUrl()).thenReturn("internal://server");
        manager.checkIfRegistrationIsOpen(clientEngine, server);
    }

    @Test
    void testSendAcknowledgement_sortsBatchesByBatchId() throws IOException {
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        when(remoteNode.requires13Compatiblity()).thenReturn(false);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        IncomingBatch batch1 = new IncomingBatch();
        batch1.setBatchId(3);
        batch1.setStatus(Status.OK);
        batches.add(batch1);
        IncomingBatch batch2 = new IncomingBatch();
        batch2.setBatchId(1);
        batch2.setStatus(Status.OK);
        batches.add(batch2);
        IncomingBatch batch3 = new IncomingBatch();
        batch3.setBatchId(2);
        batch3.setStatus(Status.OK);
        batches.add(batch3);
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", "http://reg");
        assertEquals(WebConstants.SC_OK, result);
        verify(acknowledgeService, org.mockito.Mockito.times(3)).ack(any(BatchAck.class));
    }

    @Test
    void testSendCopyRequest_skipsSystemChannels() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        when(parameterService.getExternalId()).thenReturn("new-external-id");
        when(parameterService.getNodeGroupId()).thenReturn("new-group");
        when(localNode.getNodeId()).thenReturn("old-node-001");
        when(nodeService.findIdentityNodeId()).thenReturn("server-001");
        when(registrationService.openRegistration(anyString(), anyString())).thenReturn("new-node-001");
        Map<String, BatchId> batchIds = new HashMap<String, BatchId>();
        BatchId batchId = new BatchId();
        batchId.setBatchId(100);
        batchId.setNodeId("server-001");
        batchIds.put(Constants.CHANNEL_CONFIG, batchId);
        batchIds.put(Constants.CHANNEL_HEARTBEAT, batchId);
        batchIds.put(Constants.CHANNEL_SYSTEM, batchId);
        when(incomingBatchService.findMaxBatchIdsByChannel()).thenReturn(batchIds);
        Map<String, Channel> channels = new HashMap<String, Channel>();
        Channel configChannel = new Channel();
        configChannel.setChannelId(Constants.CHANNEL_CONFIG);
        channels.put(Constants.CHANNEL_CONFIG, configChannel);
        Channel heartbeatChannel = new Channel();
        heartbeatChannel.setChannelId(Constants.CHANNEL_HEARTBEAT);
        channels.put(Constants.CHANNEL_HEARTBEAT, heartbeatChannel);
        Channel systemChannel = new Channel();
        systemChannel.setChannelId(Constants.CHANNEL_SYSTEM);
        channels.put(Constants.CHANNEL_SYSTEM, systemChannel);
        when(configurationService.getChannels(false)).thenReturn(channels);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        int result = manager.sendCopyRequest(localNode);
        assertEquals(WebConstants.SC_OK, result);
        verify(outgoingBatchService, never()).copyOutgoingBatches(eq(Constants.CHANNEL_CONFIG), any(Long.class),
                anyString(), anyString());
        verify(outgoingBatchService, never()).copyOutgoingBatches(eq(Constants.CHANNEL_HEARTBEAT), any(Long.class),
                anyString(), anyString());
        verify(outgoingBatchService, never()).copyOutgoingBatches(eq(Constants.CHANNEL_SYSTEM), any(Long.class),
                anyString(), anyString());
    }

    @Test
    void testSendCopyRequest_skipsBatchWhenNodeIdDoesNotMatch() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("internal://server");
        when(parameterService.getExternalId()).thenReturn("new-external-id");
        when(parameterService.getNodeGroupId()).thenReturn("new-group");
        when(localNode.getNodeId()).thenReturn("old-node-001");
        when(nodeService.findIdentityNodeId()).thenReturn("server-001");
        when(registrationService.openRegistration(anyString(), anyString())).thenReturn("new-node-001");
        Map<String, BatchId> batchIds = new HashMap<String, BatchId>();
        BatchId batchId = new BatchId();
        batchId.setBatchId(100);
        batchId.setNodeId("different-node");
        batchIds.put("default", batchId);
        when(incomingBatchService.findMaxBatchIdsByChannel()).thenReturn(batchIds);
        Map<String, Channel> channels = new HashMap<String, Channel>();
        Channel defaultChannel = new Channel();
        defaultChannel.setChannelId("default");
        channels.put("default", defaultChannel);
        when(configurationService.getChannels(false)).thenReturn(channels);
        doReturn(targetEngine).when(manager).getTargetEngine(anyString());
        int result = manager.sendCopyRequest(localNode);
        assertEquals(WebConstants.SC_OK, result);
        verify(outgoingBatchService, never()).copyOutgoingBatches(anyString(), any(Long.class), anyString(), anyString());
    }

    @Test
    void testWriteAcknowledgement_with13Compatibility() throws IOException {
        when(remoteNode.requires13Compatiblity()).thenReturn(true);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        IncomingBatch batch = new IncomingBatch();
        batch.setBatchId(1);
        batch.setStatus(Status.OK);
        batches.add(batch);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manager.writeAcknowledgement(out, remoteNode, batches, localNode, "token");
        String output = out.toString(StandardCharsets.UTF_8);
        assertNotNull(output);
        assertTrue(output.contains("batch-1=ok"));
    }

    @Test
    void testWriteAcknowledgement_withResendStatus() throws IOException {
        when(remoteNode.requires13Compatiblity()).thenReturn(false);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        IncomingBatch batch = new IncomingBatch();
        batch.setBatchId(1);
        batch.setStatus(Status.RS);
        batches.add(batch);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manager.writeAcknowledgement(out, remoteNode, batches, localNode, "token");
        String output = out.toString(StandardCharsets.UTF_8);
        assertNotNull(output);
        assertTrue(output.contains("batch-1=resend"));
    }

    @Test
    void testWriteAcknowledgement_withErrorStatus() throws IOException {
        when(remoteNode.requires13Compatiblity()).thenReturn(false);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        IncomingBatch batch = new IncomingBatch();
        batch.setBatchId(1);
        batch.setStatus(Status.ER);
        batch.setFailedRowNumber(5);
        batch.setSqlState("23000");
        batch.setSqlCode(1062);
        batch.setSqlMessage("Duplicate entry");
        batches.add(batch);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manager.writeAcknowledgement(out, remoteNode, batches, localNode, "token");
        String output = out.toString(StandardCharsets.UTF_8);
        assertNotNull(output);
        assertTrue(output.contains("batch-1=5"));
        assertTrue(output.contains("sqlState-1=23000"));
        assertTrue(output.contains("sqlCode-1=1062"));
    }
}
