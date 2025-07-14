package org.jumpmind.symmetric.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PullServiceTest {
    protected PullService pullService;

    @BeforeEach
    public void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        pullService = new PullService(engine);
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
