package org.jumpmind.symmetric.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PullServiceTest {
    protected PullService pullService;
    private IRegistrationService registrationService;

    @BeforeEach
    public void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        registrationService = mock(IRegistrationService.class);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        pullService = new PullService(engine);
    }

    @Test
    public void testFilterForDefaultQueueWhileRegisteringKeepsAllQueuesWhenNotRegistering() {
        when(registrationService.isRegistrationInProgress()).thenReturn(false);
        List<NodeCommunication> nodes = Arrays.asList(buildNodeCommunication("default"), buildNodeCommunication("reload"));
        List<NodeCommunication> filtered = pullService.filterForDefaultQueueWhileRegistering(nodes);
        assertEquals(2, filtered.size());
    }

    @Test
    public void testFilterForDefaultQueueWhileRegisteringKeepsOnlyDefaultQueue() {
        when(registrationService.isRegistrationInProgress()).thenReturn(true);
        List<NodeCommunication> nodes = Arrays.asList(buildNodeCommunication("reload"), buildNodeCommunication("default"), buildNodeCommunication("system"));
        List<NodeCommunication> filtered = pullService.filterForDefaultQueueWhileRegistering(nodes);
        assertEquals(1, filtered.size());
        assertEquals("default", filtered.get(0).getQueue());
    }

    private NodeCommunication buildNodeCommunication(String queue) {
        NodeCommunication nodeCommunication = new NodeCommunication();
        nodeCommunication.setNodeId("server");
        nodeCommunication.setQueue(queue);
        return nodeCommunication;
    }

    @Test
    public void testIsAllowedToPull() {
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
