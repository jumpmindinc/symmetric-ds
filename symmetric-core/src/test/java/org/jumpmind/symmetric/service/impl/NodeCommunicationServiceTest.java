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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatus.Status;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeCommunicationServiceTest {
    private IParameterService parameterService;
    private ISymmetricDialect dialect;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;
    private IClusterService clusterService;
    private INodeService nodeService;
    private NodeCommunicationService service;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        dialect = mock(ISymmetricDialect.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        clusterService = mock(IClusterService.class);
        nodeService = mock(INodeService.class);
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getNodeService()).thenReturn(nodeService);
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
    void testNodeCommunicationTypeLengths() {
        final int MAX_LENGTH_IN_DB = 10;
        for (CommunicationType communicationType : NodeCommunication.CommunicationType.values()) {
            String msg = communicationType.name() + " is too long for DB. " + communicationType.name().length() + " <= " + MAX_LENGTH_IN_DB;
            assertTrue(communicationType.name().length() <= MAX_LENGTH_IN_DB, msg);
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

    @Test
    void testLock_clustered_normalSqlSucceeds_touchesNodeHostHeartbeat() {
        when(clusterService.isClusteringEnabled()).thenReturn(true);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        NodeCommunication nc = nodeCommunication("other-server", new Date());
        service.lock(nc, new Date());
        verify(nodeService, times(1)).updateNodeHostForCurrentNode(false);
    }

    @Test
    void testLock_clustered_secondAcquisitionWithinThrottleWindow_doesNotTouchHeartbeatAgain() {
        when(clusterService.isClusteringEnabled()).thenReturn(true);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(parameterService.getLong(ParameterConstants.CLUSTER_LOCK_REFRESH_MS)).thenReturn(1200000L);
        NodeCommunication nc = nodeCommunication("other-server", new Date());
        service.lock(nc, new Date());
        service.lock(nc, new Date());
        verify(nodeService, times(1)).updateNodeHostForCurrentNode(false);
    }

    @Test
    void testLock_clustered_sqlFails_ownerNotStale_doesNotTouchNodeHostHeartbeat() {
        when(clusterService.isClusteringEnabled()).thenReturn(true);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        NodeCommunication nc = nodeCommunication("other-server", new Date());
        service.lock(nc, new Date());
        verify(nodeService, never()).updateNodeHostForCurrentNode(any(Boolean.class));
    }

    @Test
    void testLock_inMemory_succeeds_doesNotTouchNodeHostHeartbeat() {
        NodeCommunication nc = nodeCommunication(null, null);
        service.lock(nc, new Date());
        verify(nodeService, never()).updateNodeHostForCurrentNode(any(Boolean.class));
    }

    @Test
    void testIsDueForAttempt_noPreviousAttempt_isDue() {
        assertTrue(service.isDueForAttempt(nodeCommunication(null, null)));
    }

    @Test
    void testUpdateNextAttemptTime_busyPull_delaysNextAttempt() {
        when(parameterService.getLong(eq(ParameterConstants.PULL_MINIMUM_PERIOD_MS), anyLong())).thenReturn(3000L);
        when(parameterService.getLong(eq(ParameterConstants.JOB_PULL_PERIOD_TIME_MS), anyLong())).thenReturn(10000L);
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setFailCount(1);
        RemoteNodeStatus status = new RemoteNodeStatus("node1", "default", null);
        status.setStatus(Status.BUSY);
        service.updateNextAttemptTime(nc, status, System.currentTimeMillis());
        assertFalse(service.isDueForAttempt(nc));
    }

    @Test
    void testUpdateNextAttemptTime_offlinePush_delaysNextAttempt() {
        when(parameterService.getLong(eq(ParameterConstants.PUSH_MINIMUM_PERIOD_MS), anyLong())).thenReturn(3000L);
        when(parameterService.getLong(eq(ParameterConstants.JOB_PUSH_PERIOD_TIME_MS), anyLong())).thenReturn(10000L);
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setCommunicationType(CommunicationType.PUSH);
        nc.setFailCount(1);
        RemoteNodeStatus status = new RemoteNodeStatus("node1", "default", null);
        status.setStatus(Status.OFFLINE);
        service.updateNextAttemptTime(nc, status, System.currentTimeMillis());
        assertFalse(service.isDueForAttempt(nc));
    }

    @Test
    void testUpdateNextAttemptTime_delayElapsed_isDue() {
        when(parameterService.getLong(eq(ParameterConstants.PULL_MINIMUM_PERIOD_MS), anyLong())).thenReturn(3000L);
        when(parameterService.getLong(eq(ParameterConstants.JOB_PULL_PERIOD_TIME_MS), anyLong())).thenReturn(10000L);
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setFailCount(1);
        long attemptStartTime = System.currentTimeMillis() - 3000 - 2 * NodeCommunicationService.RETRY_DELAY_BASE_MS - 1;
        RemoteNodeStatus status = new RemoteNodeStatus("node1", "default", null);
        status.setStatus(Status.BUSY);
        service.updateNextAttemptTime(nc, status, attemptStartTime);
        assertTrue(service.isDueForAttempt(nc));
    }

    @Test
    void testUpdateNextAttemptTime_successAfterBusy_clearsDelay() {
        when(parameterService.getLong(eq(ParameterConstants.PULL_MINIMUM_PERIOD_MS), anyLong())).thenReturn(3000L);
        when(parameterService.getLong(eq(ParameterConstants.JOB_PULL_PERIOD_TIME_MS), anyLong())).thenReturn(10000L);
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setFailCount(1);
        RemoteNodeStatus status = new RemoteNodeStatus("node1", "default", null);
        status.setStatus(Status.BUSY);
        service.updateNextAttemptTime(nc, status, System.currentTimeMillis());
        status.setStatus(Status.DATA_PROCESSED);
        service.updateNextAttemptTime(nc, status, System.currentTimeMillis());
        assertTrue(service.isDueForAttempt(nc));
    }

    @Test
    void testUpdateNextAttemptTime_failuresOtherThanRejection_keepCurrentBehavior() {
        when(parameterService.getLong(eq(ParameterConstants.PULL_MINIMUM_PERIOD_MS), anyLong())).thenReturn(3000L);
        when(parameterService.getLong(eq(ParameterConstants.JOB_PULL_PERIOD_TIME_MS), anyLong())).thenReturn(10000L);
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setFailCount(1);
        for (Status otherStatus : new Status[] { Status.DATA_ERROR, Status.NOT_AUTHORIZED, Status.REGISTRATION_REQUIRED,
                Status.UNKNOWN_ERROR }) {
            RemoteNodeStatus status = new RemoteNodeStatus("node1", "default", null);
            status.setStatus(otherStatus);
            service.updateNextAttemptTime(nc, status, System.currentTimeMillis());
            assertTrue(service.isDueForAttempt(nc), otherStatus.name());
        }
    }

    @Test
    void testUpdateNextAttemptTime_otherCommunicationType_isDue() {
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setCommunicationType(CommunicationType.FILE_PULL);
        RemoteNodeStatus status = new RemoteNodeStatus("node1", "default", null);
        status.setStatus(Status.BUSY);
        service.updateNextAttemptTime(nc, status, System.currentTimeMillis());
        assertTrue(service.isDueForAttempt(nc));
    }

    @Test
    void testGetRetryDelayMs_staysWithinMinimumPlusRandomRange() {
        when(parameterService.getLong(eq(ParameterConstants.PULL_MINIMUM_PERIOD_MS), anyLong())).thenReturn(3000L);
        when(parameterService.getLong(eq(ParameterConstants.JOB_PULL_PERIOD_TIME_MS), anyLong())).thenReturn(10000L);
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setFailCount(3);
        for (int i = 0; i < 1000; i++) {
            long delayMs = service.getRetryDelayMs(nc);
            assertTrue(delayMs >= 3000 && delayMs <= 11000, String.valueOf(delayMs));
        }
    }

    @Test
    void testGetRetryDelayMs_minimumPeriodZero_usesRandomPartOnly() {
        when(parameterService.getLong(eq(ParameterConstants.PULL_MINIMUM_PERIOD_MS), anyLong())).thenReturn(0L);
        when(parameterService.getLong(eq(ParameterConstants.JOB_PULL_PERIOD_TIME_MS), anyLong())).thenReturn(10000L);
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setFailCount(1);
        for (int i = 0; i < 1000; i++) {
            long delayMs = service.getRetryDelayMs(nc);
            assertTrue(delayMs >= 0 && delayMs <= 2000, String.valueOf(delayMs));
        }
    }

    @Test
    void testGetMaxRandomRetryDelayMs_growsWithFailCount() {
        assertEquals(1000L, NodeCommunicationService.getMaxRandomRetryDelayMs(0, 10000));
        assertEquals(2000L, NodeCommunicationService.getMaxRandomRetryDelayMs(1, 10000));
        assertEquals(4000L, NodeCommunicationService.getMaxRandomRetryDelayMs(2, 10000));
        assertEquals(8000L, NodeCommunicationService.getMaxRandomRetryDelayMs(3, 10000));
        assertEquals(16000L, NodeCommunicationService.getMaxRandomRetryDelayMs(4, 10000));
    }

    @Test
    void testGetMaxRandomRetryDelayMs_cappedAtTwiceJobPeriod() {
        assertEquals(20000L, NodeCommunicationService.getMaxRandomRetryDelayMs(5, 10000));
        assertEquals(20000L, NodeCommunicationService.getMaxRandomRetryDelayMs(Long.MAX_VALUE, 10000));
    }

    @Test
    void testGetRetryDelayMs_jobPeriodNotSet_usesDefaultJobPeriodForCap() {
        when(parameterService.getLong(eq(ParameterConstants.PULL_MINIMUM_PERIOD_MS), anyLong())).thenReturn(0L);
        when(parameterService.getLong(eq(ParameterConstants.JOB_PULL_PERIOD_TIME_MS), anyLong())).thenReturn(0L);
        NodeCommunication nc = nodeCommunication(null, null);
        nc.setFailCount(10);
        for (int i = 0; i < 1000; i++) {
            long delayMs = service.getRetryDelayMs(nc);
            assertTrue(delayMs <= 2 * NodeCommunicationService.DEFAULT_JOB_PERIOD_TIME_MS, String.valueOf(delayMs));
        }
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
