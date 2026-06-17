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
package org.jumpmind.symmetric.service.impl;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NodeCommunicationServiceTest {
    private IParameterService parameterService;
    private ISymmetricDialect dialect;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;
    private IClusterService clusterService;
    private NodeCommunicationService service;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        dialect = mock(ISymmetricDialect.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        clusterService = mock(IClusterService.class);
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getNodeService()).thenReturn(mock(INodeService.class));
        when(engine.getConfigurationService()).thenReturn(mock(IConfigurationService.class));
        when(engine.getExtensionService()).thenReturn(mock(IExtensionService.class));
        when(dialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getInt(anyString(), anyInt())).thenReturn(7200000);
        when(clusterService.getServerId()).thenReturn("test-server");
        when(clusterService.isClusteringEnabled()).thenReturn(false);
        when(clusterService.isStaleServer(any())).thenReturn(false);
        service = new NodeCommunicationService(engine);
    }

    @Test
    public void testNodeCommunicationTypeLengths() {
        final int MAX_LENGTH_IN_DB = 10;
        for (CommunicationType communicationType : NodeCommunication.CommunicationType.values()) {
            String msg = communicationType.name() + " is too long for DB. " + communicationType.name().length() + " <= " + MAX_LENGTH_IN_DB;
            assertTrue(msg, communicationType.name().length() <= MAX_LENGTH_IN_DB);
        }
    }

    @Test
    void testLock_inMemory_nullLockTime_succeeds() {
        NodeCommunication nc = nodeCommunication(null, null);
        assertTrue(service.lock(nc, new Date()));
    }

    @Test
    void testLock_inMemory_expiredLock_succeeds() {
        Date expiredLockTime = new Date(System.currentTimeMillis() - 3 * 3600_000L);
        NodeCommunication nc = nodeCommunication("other-server", expiredLockTime);
        assertTrue(service.lock(nc, new Date()));
    }

    @Test
    void testLock_inMemory_activeLock_notStale_fails() {
        NodeCommunication nc = nodeCommunication("other-server", new Date());
        assertFalse(service.lock(nc, new Date()));
    }

    @Test
    void testLock_inMemory_activeLock_staleOwner_succeeds() {
        when(clusterService.isStaleServer("stale-server")).thenReturn(true);
        NodeCommunication nc = nodeCommunication("stale-server", new Date());
        assertTrue(service.lock(nc, new Date()));
    }

    @Test
    void testLock_clustered_normalSqlSucceeds_returnsTrue() {
        when(clusterService.isClusteringEnabled()).thenReturn(true);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        NodeCommunication nc = nodeCommunication("other-server", new Date());
        assertTrue(service.lock(nc, new Date()));
    }

    @Test
    void testLock_clustered_sqlFails_ownerNotStale_returnsFalse() {
        when(clusterService.isClusteringEnabled()).thenReturn(true);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        NodeCommunication nc = nodeCommunication("other-server", new Date());
        assertFalse(service.lock(nc, new Date()));
    }

    @Test
    void testLock_clustered_sqlFails_ownerStale_stealSqlSucceeds_returnsTrue() {
        when(clusterService.isClusteringEnabled()).thenReturn(true);
        when(clusterService.isStaleServer("stale-server")).thenReturn(true);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0).thenReturn(1);
        NodeCommunication nc = nodeCommunication("stale-server", new Date());
        assertTrue(service.lock(nc, new Date()));
    }

    @Test
    void testLock_clustered_sqlFails_ownerStale_stealSqlAlsoFails_returnsFalse() {
        when(clusterService.isClusteringEnabled()).thenReturn(true);
        when(clusterService.isStaleServer("stale-server")).thenReturn(true);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        NodeCommunication nc = nodeCommunication("stale-server", new Date());
        assertFalse(service.lock(nc, new Date()));
    }

    private NodeCommunication nodeCommunication(String lockingServerId, Date lockTime) {
        NodeCommunication nc = new NodeCommunication();
        nc.setNodeId("node1");
        nc.setQueue("default");
        nc.setCommunicationType(CommunicationType.PULL);
        nc.setLockingServerId(lockingServerId);
        nc.setLockTime(lockTime);
        return nc;
    }
}
