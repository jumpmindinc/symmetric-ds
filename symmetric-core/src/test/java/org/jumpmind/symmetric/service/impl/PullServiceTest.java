package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PullServiceTest {
    protected PullService pullService;
    private IRegistrationService registrationService;
    private INodeService nodeService;
    private INodeCommunicationService nodeCommunicationService;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        registrationService = mock(IRegistrationService.class);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        nodeCommunicationService = mock(INodeCommunicationService.class);
        when(engine.getNodeCommunicationService()).thenReturn(nodeCommunicationService);
        when(engine.getClusterService()).thenReturn(mock(IClusterService.class));
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(configurationService.getChannels(false)).thenReturn(new HashMap<>());
        when(engine.getConfigurationService()).thenReturn(configurationService);
        pullService = new PullService(engine);
    }

    @Test
    void testFilterForDefaultQueueWhileRegisteringKeepsAllQueuesWhenNotRegistering() {
        when(registrationService.isRegistrationInProgress()).thenReturn(false);
        List<NodeCommunication> nodes = Arrays.asList(buildNodeCommunication("default"), buildNodeCommunication("reload"));
        List<NodeCommunication> filtered = pullService.filterForDefaultQueueWhileRegistering(nodes);
        assertEquals(2, filtered.size());
    }

    @Test
    void testFilterForDefaultQueueWhileRegisteringKeepsOnlyDefaultQueue() {
        when(registrationService.isRegistrationInProgress()).thenReturn(true);
        List<NodeCommunication> nodes = Arrays.asList(buildNodeCommunication("reload"), buildNodeCommunication("default"), buildNodeCommunication("system"));
        List<NodeCommunication> filtered = pullService.filterForDefaultQueueWhileRegistering(nodes);
        assertEquals(1, filtered.size());
        assertEquals("default", filtered.get(0).getQueue());
    }

    @Test
    void testPullDataExecutesNodeDueForAttempt() {
        Node identity = new Node();
        identity.setNodeId("client");
        identity.setSyncEnabled(true);
        when(nodeService.findIdentity()).thenReturn(identity);
        NodeCommunication nodeCommunication = new NodeCommunication();
        nodeCommunication.setNodeId("server");
        nodeCommunication.setQueue("default");
        when(nodeCommunicationService.list(CommunicationType.PULL)).thenReturn(Arrays.asList(nodeCommunication));
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.PULL)).thenReturn(1);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationEnabled(false);
        when(nodeService.findNodeSecurity("server", true)).thenReturn(nodeSecurity);
        when(nodeCommunicationService.isDueForAttempt(nodeCommunication)).thenReturn(true);
        pullService.pullData(false);
        verify(nodeCommunicationService).execute(eq(nodeCommunication), any(), any());
    }

    @Test
    void testPullDataSkipsNodeNotDueForAttempt() {
        Node identity = new Node();
        identity.setNodeId("client");
        identity.setSyncEnabled(true);
        when(nodeService.findIdentity()).thenReturn(identity);
        NodeCommunication nodeCommunication = new NodeCommunication();
        nodeCommunication.setNodeId("server");
        nodeCommunication.setQueue("default");
        when(nodeCommunicationService.list(CommunicationType.PULL)).thenReturn(Arrays.asList(nodeCommunication));
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.PULL)).thenReturn(1);
        NodeSecurity nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationEnabled(false);
        when(nodeService.findNodeSecurity("server", true)).thenReturn(nodeSecurity);
        when(nodeCommunicationService.isDueForAttempt(nodeCommunication)).thenReturn(false);
        pullService.pullData(false);
        verify(nodeCommunicationService, never()).execute(any(), any(), any());
    }

    private NodeCommunication buildNodeCommunication(String queue) {
        NodeCommunication nodeCommunication = new NodeCommunication();
        nodeCommunication.setNodeId("server");
        nodeCommunication.setQueue(queue);
        return nodeCommunication;
    }

    @Test
    void testIsAllowedToPull() {
        NodeSecurity nodeSecurity = null;
        assertFalse(pullService.isAllowedToPull(nodeSecurity, "current_node"));
        nodeSecurity = new NodeSecurity();
        nodeSecurity.setRegistrationEnabled(false);
        assertTrue(pullService.isAllowedToPull(nodeSecurity, "current_node"));
        nodeSecurity.setRegistrationEnabled(true);
        nodeSecurity.setCreatedAtNodeId(null);
        assertTrue(pullService.isAllowedToPull(nodeSecurity, "current_node"));
        nodeSecurity.setCreatedAtNodeId("");
        assertTrue(pullService.isAllowedToPull(nodeSecurity, "current_node"));
        nodeSecurity.setCreatedAtNodeId("parent_node");
        assertTrue(pullService.isAllowedToPull(nodeSecurity, "current_node"));
        nodeSecurity.setCreatedAtNodeId("current_node");
        assertFalse(pullService.isAllowedToPull(nodeSecurity, "current_node"));
    }
}
