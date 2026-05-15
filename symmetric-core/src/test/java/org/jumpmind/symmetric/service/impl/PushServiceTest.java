package org.jumpmind.symmetric.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PushServiceTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ISymmetricDialect symmetricDialect;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;
    private IExtensionService extensionService;
    private IDataExtractorService dataExtractorService;
    private IAcknowledgeService acknowledgeService;
    private IRegistrationService registrationService;
    private ITransportManager transportManager;
    private INodeService nodeService;
    private IClusterService clusterService;
    private INodeCommunicationService nodeCommunicationService;
    private IStatisticManager statisticManager;
    private IConfigurationService configurationService;
    private IOutgoingBatchService outgoingBatchService;
    private PushService pushService;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        extensionService = mock(IExtensionService.class);
        dataExtractorService = mock(IDataExtractorService.class);
        acknowledgeService = mock(IAcknowledgeService.class);
        registrationService = mock(IRegistrationService.class);
        transportManager = mock(ITransportManager.class);
        nodeService = mock(INodeService.class);
        clusterService = mock(IClusterService.class);
        nodeCommunicationService = mock(INodeCommunicationService.class);
        statisticManager = mock(IStatisticManager.class);
        configurationService = mock(IConfigurationService.class);
        outgoingBatchService = mock(IOutgoingBatchService.class);

        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getDataExtractorService()).thenReturn(dataExtractorService);
        when(engine.getAcknowledgeService()).thenReturn(acknowledgeService);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(engine.getTransportManager()).thenReturn(transportManager);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getNodeCommunicationService()).thenReturn(nodeCommunicationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);

        when(parameterService.getLong(ParameterConstants.PUSH_MINIMUM_PERIOD_MS, -1)).thenReturn(-1L);
        when(parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)).thenReturn(false);
        when(configurationService.getChannels(false)).thenReturn(new HashMap<>());

        pushService = new PushService(engine);
    }

    private Node newNode(String nodeId, String groupId) {
        Node node = new Node();
        node.setNodeId(nodeId);
        node.setNodeGroupId(groupId);
        node.setSyncEnabled(true);
        node.setSyncUrl("http://node/" + nodeId);
        return node;
    }

    private NodeSecurity newNodeSecurity(String nodeId) {
        NodeSecurity security = new NodeSecurity();
        security.setNodeId(nodeId);
        security.setNodePassword("password");
        return security;
    }

    private NodeCommunication newNodeCommunication(String nodeId, String queue) {
        NodeCommunication nc = new NodeCommunication();
        nc.setNodeId(nodeId);
        nc.setQueue(queue);
        nc.setNode(newNode(nodeId, "group1"));
        return nc;
    }

    private void setupForExecute(ProcessInfo processInfo) throws Exception {
        Node identity = newNode("node1", "group1");
        when(nodeService.findIdentity()).thenReturn(identity);
        NodeSecurity identitySecurity = newNodeSecurity("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(identitySecurity);
        when(statisticManager.newProcessInfo(any())).thenReturn(processInfo);
        when(processInfo.getStatus()).thenReturn(ProcessStatus.OK);
        when(parameterService.is(ParameterConstants.PUSH_IMMEDIATE_IF_DATA_FOUND, false)).thenReturn(false);
        when(parameterService.getRegistrationUrl()).thenReturn("http://server");
        when(extensionService.getExtensionPointList(any())).thenReturn(Collections.emptyList());
        when(dataExtractorService.extract(any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(transportManager.readAcknowledgement(any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    void testPushDataClusterLockedSkipsNodeListing() {
        Node identity = newNode("node1", "group1");
        when(nodeService.findIdentity()).thenReturn(identity);
        when(clusterService.isInfiniteLocked(ClusterConstants.PUSH)).thenReturn(true);

        pushService.pushData(false);

        verify(nodeCommunicationService, never()).list(any());
    }

    @Test
    void testPushDataIdentitySecurityNullSkipsExecution() {
        Node identity = newNode("node1", "group1");
        when(nodeService.findIdentity()).thenReturn(identity);
        when(clusterService.isInfiniteLocked(ClusterConstants.PUSH)).thenReturn(false);
        NodeCommunication nc = newNodeCommunication("node2", "default");
        when(nodeCommunicationService.list(CommunicationType.PUSH)).thenReturn(List.of(nc));
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(null);
        when(nodeService.findIdentity(false)).thenReturn(identity);
        when(nodeService.findNodeSecurity("node1", false)).thenReturn(null);

        pushService.pushData(false);

        verify(nodeCommunicationService, never()).execute(any(), any(), any());
    }

    @Test
    void testPushDataM2mLoadInProgressSkipsExecution() {
        Node identity = newNode("node1", "group1");
        when(nodeService.findIdentity()).thenReturn(identity);
        when(clusterService.isInfiniteLocked(ClusterConstants.PUSH)).thenReturn(false);
        NodeCommunication nc = newNodeCommunication("node2", "default");
        when(nodeCommunicationService.list(CommunicationType.PUSH)).thenReturn(List.of(nc));
        NodeSecurity identitySecurity = newNodeSecurity("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(identitySecurity);
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.PUSH)).thenReturn(1);
        when(configurationService.isMasterToMaster()).thenReturn(true);
        when(nodeService.isDataLoadStarted("node2")).thenReturn(true);
        NodeSecurity remoteSecurity = newNodeSecurity("node2");
        remoteSecurity.setInitialLoadCreateBy("registration");
        remoteSecurity.setCreatedAtNodeId("node3");
        when(nodeService.findNodeSecurity("node2", true)).thenReturn(remoteSecurity);

        pushService.pushData(false);

        verify(nodeCommunicationService, never()).execute(any(), any(), any());
    }

    @Test
    void testPushDataM2mRemoteSecurityNullAllowsExecution() {
        Node identity = newNode("node1", "group1");
        when(nodeService.findIdentity()).thenReturn(identity);
        when(clusterService.isInfiniteLocked(ClusterConstants.PUSH)).thenReturn(false);
        NodeCommunication nc = newNodeCommunication("node2", "default");
        when(nodeCommunicationService.list(CommunicationType.PUSH)).thenReturn(List.of(nc));
        NodeSecurity identitySecurity = newNodeSecurity("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(identitySecurity);
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.PUSH)).thenReturn(1);
        when(configurationService.isMasterToMaster()).thenReturn(true);
        when(nodeService.isDataLoadStarted("node2")).thenReturn(true);
        when(nodeService.findNodeSecurity("node2", true)).thenReturn(null);
        when(nodeCommunicationService.execute(any(), any(), any())).thenReturn(true);

        pushService.pushData(false);

        verify(nodeCommunicationService).execute(eq(nc), any(), any());
    }

    @Test
    void testPushDataAvailableThreadsExhaustedAfterFirstExecute() {
        Node identity = newNode("node1", "group1");
        when(nodeService.findIdentity()).thenReturn(identity);
        when(clusterService.isInfiniteLocked(ClusterConstants.PUSH)).thenReturn(false);
        NodeCommunication nc1 = newNodeCommunication("node2", "default");
        NodeCommunication nc2 = newNodeCommunication("node3", "default");
        when(nodeCommunicationService.list(CommunicationType.PUSH)).thenReturn(List.of(nc1, nc2));
        NodeSecurity identitySecurity = newNodeSecurity("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(identitySecurity);
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.PUSH)).thenReturn(1);
        when(configurationService.isMasterToMaster()).thenReturn(false);
        when(nodeCommunicationService.execute(eq(nc1), any(), any())).thenReturn(true);

        pushService.pushData(false);

        verify(nodeCommunicationService).execute(eq(nc1), any(), any());
        verify(nodeCommunicationService, never()).execute(eq(nc2), any(), any());
    }

    @Test
    void testFilterForReadyQueuesIncludesMatchingQueue() {
        Node identity = newNode("node1", "group1");
        when(nodeService.findIdentity()).thenReturn(identity);
        when(clusterService.isInfiniteLocked(ClusterConstants.PUSH)).thenReturn(false);
        NodeCommunication nc = newNodeCommunication("node2", "default");
        when(nodeCommunicationService.list(CommunicationType.PUSH)).thenReturn(List.of(nc));
        NodeSecurity identitySecurity = newNodeSecurity("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(identitySecurity);
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.PUSH)).thenReturn(1);
        when(configurationService.isMasterToMaster()).thenReturn(false);
        when(parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)).thenReturn(true);
        when(parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)).thenReturn(false);
        when(configurationService.getQueues(false)).thenReturn(List.of("default", "reload"));
        when(outgoingBatchService.getReadyQueues("node2", false)).thenReturn(List.of("default"));
        when(nodeCommunicationService.execute(any(), any(), any())).thenReturn(true);

        pushService.pushData(false);

        verify(nodeCommunicationService).execute(eq(nc), any(), any());
    }

    @Test
    void testFilterForReadyQueuesExcludesNonMatchingQueue() {
        Node identity = newNode("node1", "group1");
        when(nodeService.findIdentity()).thenReturn(identity);
        when(clusterService.isInfiniteLocked(ClusterConstants.PUSH)).thenReturn(false);
        NodeCommunication nc = newNodeCommunication("node2", "reload");
        when(nodeCommunicationService.list(CommunicationType.PUSH)).thenReturn(List.of(nc));
        NodeSecurity identitySecurity = newNodeSecurity("node1");
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(identitySecurity);
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.PUSH)).thenReturn(1);
        when(configurationService.isMasterToMaster()).thenReturn(false);
        when(parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)).thenReturn(true);
        when(parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)).thenReturn(false);
        when(configurationService.getQueues(false)).thenReturn(List.of("default", "reload"));
        when(outgoingBatchService.getReadyQueues("node2", false)).thenReturn(List.of("default"));

        pushService.pushData(false);

        verify(nodeCommunicationService, never()).execute(any(), any(), any());
    }

    @Test
    void testExecuteSkipsWhenBlankSyncUrlAndRegistrationServer() throws Exception {
        ProcessInfo processInfo = mock(ProcessInfo.class);
        setupForExecute(processInfo);
        when(parameterService.isRegistrationServer()).thenReturn(true);
        NodeCommunication nc = newNodeCommunication("node2", "default");
        nc.getNode().setSyncUrl(null);
        NodeSecurity remoteSecurity = newNodeSecurity("node2");
        when(nodeService.findNodeSecurity("node2", true)).thenReturn(remoteSecurity);
        RemoteNodeStatus status = new RemoteNodeStatus("node2", "default", Collections.emptyMap());

        pushService.execute(nc, status);

        verify(dataExtractorService, never()).extract(any(), any(), any(), any());
    }

    @Test
    void testExecuteUsesPushTransportWhenRegistrationNotEnabled() throws Exception {
        ProcessInfo processInfo = mock(ProcessInfo.class);
        setupForExecute(processInfo);
        IOutgoingWithResponseTransport transport = mock(IOutgoingWithResponseTransport.class);
        when(transport.readResponse()).thenReturn(new BufferedReader(new StringReader("")));
        when(transportManager.getPushTransport(any(), any(), any(), any(), any())).thenReturn(transport);
        NodeCommunication nc = newNodeCommunication("node2", "default");
        NodeSecurity remoteSecurity = newNodeSecurity("node2");
        remoteSecurity.setRegistrationEnabled(false);
        when(nodeService.findNodeSecurity("node2", true)).thenReturn(remoteSecurity);
        RemoteNodeStatus status = new RemoteNodeStatus("node2", "default", Collections.emptyMap());

        pushService.execute(nc, status);

        verify(transportManager).getPushTransport(any(), any(), any(), any(), any());
        verify(transportManager, never()).getRegisterPushTransport(any(), any());
    }

    @Test
    void testExecuteUsesRegisterTransportWhenRegistrationEnabled() throws Exception {
        ProcessInfo processInfo = mock(ProcessInfo.class);
        setupForExecute(processInfo);
        IOutgoingWithResponseTransport transport = mock(IOutgoingWithResponseTransport.class);
        when(transport.readResponse()).thenReturn(new BufferedReader(new StringReader("")));
        when(transportManager.getRegisterPushTransport(any(), any())).thenReturn(transport);
        when(registrationService.registerWithClient(any(), any())).thenReturn(Collections.emptyList());
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(true);
        NodeCommunication nc = newNodeCommunication("node2", "default");
        NodeSecurity remoteSecurity = newNodeSecurity("node2");
        remoteSecurity.setRegistrationEnabled(true);
        remoteSecurity.setCreatedAtNodeId("node1");
        when(nodeService.findNodeSecurity("node2", true)).thenReturn(remoteSecurity);
        RemoteNodeStatus status = new RemoteNodeStatus("node2", "default", Collections.emptyMap());

        pushService.execute(nc, status);

        verify(transportManager).getRegisterPushTransport(any(), any());
        verify(transportManager, never()).getPushTransport(any(), any(), any(), any(), any());
    }

    @Test
    void testExecuteWarnsWhenNoAcksReceived() throws Exception {
        ProcessInfo processInfo = mock(ProcessInfo.class);
        setupForExecute(processInfo);
        IOutgoingWithResponseTransport transport = mock(IOutgoingWithResponseTransport.class);
        when(transport.readResponse()).thenReturn(new BufferedReader(new StringReader("")));
        when(transportManager.getPushTransport(any(), any(), any(), any(), any())).thenReturn(transport);
        when(transportManager.readAcknowledgement(any(), any())).thenReturn(Collections.emptyList());
        OutgoingBatch batch = new OutgoingBatch("node2", "default", Status.NE);
        batch.setBatchId(1L);
        when(dataExtractorService.extract(any(), any(), any(), any())).thenReturn(List.of(batch));
        NodeCommunication nc = newNodeCommunication("node2", "default");
        NodeSecurity remoteSecurity = newNodeSecurity("node2");
        remoteSecurity.setRegistrationEnabled(false);
        when(nodeService.findNodeSecurity("node2", true)).thenReturn(remoteSecurity);
        RemoteNodeStatus status = new RemoteNodeStatus("node2", "default", Collections.emptyMap());

        pushService.execute(nc, status);

        verify(dataExtractorService).extract(any(), any(), any(), any());
        verify(transportManager).readAcknowledgement(any(), any());
    }

    @Test
    void testExecuteProcessesAcksWhenReceived() throws Exception {
        ProcessInfo processInfo = mock(ProcessInfo.class);
        setupForExecute(processInfo);
        IOutgoingWithResponseTransport transport = mock(IOutgoingWithResponseTransport.class);
        when(transport.readResponse()).thenReturn(new BufferedReader(new StringReader("")));
        when(transportManager.getPushTransport(any(), any(), any(), any(), any())).thenReturn(transport);
        BatchAck ack = new BatchAck(1L);
        when(transportManager.readAcknowledgement(any(), any())).thenReturn(List.of(ack));
        OutgoingBatch batch = new OutgoingBatch("node2", "default", Status.NE);
        batch.setBatchId(1L);
        when(dataExtractorService.extract(any(), any(), any(), any())).thenReturn(List.of(batch));
        NodeCommunication nc = newNodeCommunication("node2", "default");
        NodeSecurity remoteSecurity = newNodeSecurity("node2");
        remoteSecurity.setRegistrationEnabled(false);
        when(nodeService.findNodeSecurity("node2", true)).thenReturn(remoteSecurity);
        RemoteNodeStatus status = new RemoteNodeStatus("node2", "default", Collections.emptyMap());

        pushService.execute(nc, status);

        verify(dataExtractorService).extract(any(), any(), any(), any());
        verify(transportManager).readAcknowledgement(any(), any());
        verify(acknowledgeService).ack(ack);
    }

    @Test
    void testExecuteSetsProcessInfoToOk() throws Exception {
        ProcessInfo processInfo = mock(ProcessInfo.class);
        setupForExecute(processInfo);
        IOutgoingWithResponseTransport transport = mock(IOutgoingWithResponseTransport.class);
        when(transport.readResponse()).thenReturn(new BufferedReader(new StringReader("")));
        when(transportManager.getPushTransport(any(), any(), any(), any(), any())).thenReturn(transport);
        NodeCommunication nc = newNodeCommunication("node2", "default");
        NodeSecurity remoteSecurity = newNodeSecurity("node2");
        remoteSecurity.setRegistrationEnabled(false);
        when(nodeService.findNodeSecurity("node2", true)).thenReturn(remoteSecurity);
        RemoteNodeStatus status = new RemoteNodeStatus("node2", "default", Collections.emptyMap());

        pushService.execute(nc, status);

        verify(processInfo).setStatus(ProcessStatus.OK);
    }
}
