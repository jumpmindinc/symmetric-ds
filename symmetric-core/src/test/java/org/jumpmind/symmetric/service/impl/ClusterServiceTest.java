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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.cache.ClusterServerStatusMessage;
import org.jumpmind.symmetric.cache.ClusteredCacheManager;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator;
import org.jumpmind.symmetric.cache.IClusteredCacheManager.PeerState;
import org.jumpmind.symmetric.cache.JcsTcpCacheCoordinator;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Lock;
import org.jumpmind.symmetric.model.NodeHost;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for ClusterService.
 */
class ClusterServiceTest {
    private IParameterService parameterService;
    private ISymmetricDialect dialect;
    private INodeService nodeService;
    private IExtensionService extensionService;
    private ISqlTemplate sqlTemplate;
    private IDatabasePlatform platform;
    private ClusterService clusterService;
    private IClusterCacheCoordinator originalPeerNetworkCoordinator;
    private boolean originalClusterLockingEnabled;
    private boolean originalIsInitializationComplete;

    @BeforeEach
    void setUp() throws Exception {
        parameterService = mock(IParameterService.class);
        dialect = mock(ISymmetricDialect.class);
        nodeService = mock(INodeService.class);
        extensionService = mock(IExtensionService.class);
        sqlTemplate = mock(ISqlTemplate.class);
        platform = mock(IDatabasePlatform.class);
        when(dialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(parameterService.getLong(ParameterConstants.CLUSTER_LOCK_TIMEOUT_MS)).thenReturn(60000L);
        when(parameterService.getLong(ParameterConstants.LOCK_TIMEOUT_MS)).thenReturn(60000L);
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(false);
        when(nodeService.findIdentityNodeId()).thenReturn("test-node");
        when(nodeService.findNodeHosts(anyString())).thenReturn(new ArrayList<>());
        clusterService = new ClusterService(parameterService, dialect, nodeService, extensionService);
        ClusterService.instanceId = "my-instance-id";
        Field coordinatorField = ClusteredCacheManager.class.getDeclaredField("peerNetworkCoordinator");
        coordinatorField.setAccessible(true);
        originalPeerNetworkCoordinator = (IClusterCacheCoordinator) coordinatorField.get(ClusteredCacheManager.getInstance());
        if (originalPeerNetworkCoordinator == null) {
            // peerNetworkCoordinator is only lazily constructed by ClusteredCacheManager.initialize(), which this test never calls; provide a real,
            // unstarted coordinator so tests that don't call mockActivePeers() still exercise ClusteredCacheManager.getActiveServerIds() safely.
            coordinatorField.set(ClusteredCacheManager.getInstance(), new JcsTcpCacheCoordinator());
        }
        originalClusterLockingEnabled = ClusteredCacheManager.getInstance().isClusterLockingEnabled();
        Field initField = ClusteredCacheManager.class.getDeclaredField("isInitializationComplete");
        initField.setAccessible(true);
        originalIsInitializationComplete = (boolean) initField.get(ClusteredCacheManager.getInstance());
        initField.set(ClusteredCacheManager.getInstance(), true);
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void clearActivePeers() throws Exception {
        ClusterService.instanceId = null;
        Field peerStatesField = ClusteredCacheManager.class.getDeclaredField("lastPeerStateMap");
        peerStatesField.setAccessible(true);
        ((Map<String, PeerState>) peerStatesField.get(ClusteredCacheManager.getInstance())).clear();
        Field coordinatorField = ClusteredCacheManager.class.getDeclaredField("peerNetworkCoordinator");
        coordinatorField.setAccessible(true);
        coordinatorField.set(ClusteredCacheManager.getInstance(), originalPeerNetworkCoordinator);
        setClusterLockingEnabled(originalClusterLockingEnabled);
        Field initField = ClusteredCacheManager.class.getDeclaredField("isInitializationComplete");
        initField.setAccessible(true);
        initField.set(ClusteredCacheManager.getInstance(), originalIsInitializationComplete);
    }

    private void setClusterLockingEnabled(boolean value) throws Exception {
        Field field = ClusteredCacheManager.class.getDeclaredField("isClusterLockingEnabled");
        field.setAccessible(true);
        field.set(ClusteredCacheManager.getInstance(), value);
    }

    /**
     * Replaces the singleton's real JcsTcpCacheCoordinator with a mock reporting the given peer IDs as alive (fresh heartbeat, non-stale), so
     * ClusteredCacheManager.getActiveServerIds() reflects exactly this set. Restored to the real coordinator in {@link #clearActivePeers()}.
     */
    private void mockActivePeers(String... peerIds) throws Exception {
        IClusterCacheCoordinator mockCoordinator = mock(IClusterCacheCoordinator.class);
        when(mockCoordinator.getPeerIds()).thenReturn(Set.of(peerIds));
        for (String peerId : peerIds) {
            ClusterServerStatusMessage freshMessage = new ClusterServerStatusMessage(
                    ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, peerId, "test-partition", 0L);
            when(mockCoordinator.getPeerStatusMessage(peerId)).thenReturn(freshMessage);
        }
        Field coordinatorField = ClusteredCacheManager.class.getDeclaredField("peerNetworkCoordinator");
        coordinatorField.setAccessible(true);
        coordinatorField.set(ClusteredCacheManager.getInstance(), mockCoordinator);
    }

    @Test
    void testLoadLocksFromDatabase_mergesLastLockTimeIntoCache() {
        Date lastLockTime = new Date(System.currentTimeMillis() - 60000);
        String lastLockingServerId = "server-1";
        List<Lock> dbLocks = new ArrayList<>();
        Lock dbLock = new Lock();
        dbLock.setLockAction(ClusterConstants.PUSH);
        dbLock.setLockType(ClusterConstants.TYPE_CLUSTER);
        dbLock.setLastLockTime(lastLockTime);
        dbLock.setLastLockingServerId(lastLockingServerId);
        dbLocks.add(dbLock);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(dbLocks);
        clusterService.loadLocksFromDatabase();
        Map<String, Lock> locks = clusterService.findLocks();
        Lock cachedLock = locks.get(ClusterConstants.PUSH);
        assertNotNull(cachedLock);
        assertEquals(lastLockTime, cachedLock.getLastLockTime());
        assertEquals(lastLockingServerId, cachedLock.getLastLockingServerId());
    }

    @Test
    void testLoadLocksFromDatabase_ignoresUnknownActions() {
        List<Lock> dbLocks = new ArrayList<>();
        Lock dbLock = new Lock();
        dbLock.setLockAction("UNKNOWN_ACTION");
        dbLock.setLockType(ClusterConstants.TYPE_CLUSTER);
        dbLock.setLastLockTime(new Date());
        dbLock.setLastLockingServerId("server-1");
        dbLocks.add(dbLock);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(dbLocks);
        clusterService.loadLocksFromDatabase();
        Map<String, Lock> locks = clusterService.findLocks();
        assertNull(locks.get("UNKNOWN_ACTION"));
    }

    @Test
    void testLoadLocksFromDatabase_handlesEmptyResult() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(new ArrayList<>());
        clusterService.loadLocksFromDatabase();
        Map<String, Lock> locks = clusterService.findLocks();
        Lock cachedLock = locks.get(ClusterConstants.PUSH);
        assertNotNull(cachedLock);
        assertNull(cachedLock.getLastLockTime());
    }

    @Test
    void testLoadLocksFromDatabase_handlesException() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenThrow(new RuntimeException("DB error"));
        clusterService.loadLocksFromDatabase();
        Map<String, Lock> locks = clusterService.findLocks();
        assertNotNull(locks.get(ClusterConstants.PUSH));
    }

    @Test
    void testPersistLastLockTime_updateSucceeds() {
        Date lastLockTime = new Date();
        String lastLockingServerId = "server-1";
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        clusterService.persistLastLockTime(ClusterConstants.PUSH, lastLockTime, lastLockingServerId);
        // Only update called, no insert needed
        verify(sqlTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void testPersistLastLockTime_updateReturnsZero_insertSucceeds() {
        Date lastLockTime = new Date();
        String lastLockingServerId = "server-1";
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0).thenReturn(1);
        clusterService.persistLastLockTime(ClusterConstants.PUSH, lastLockTime, lastLockingServerId);
        // Update returns 0, then insert called
        verify(sqlTemplate, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void testPersistLastLockTime_updateReturnsZero_insertThrowsUniqueKey() {
        Date lastLockTime = new Date();
        String lastLockingServerId = "server-1";
        when(sqlTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(0)
                .thenThrow(new UniqueKeyException());
        clusterService.persistLastLockTime(ClusterConstants.PUSH, lastLockTime, lastLockingServerId);
        // Update returns 0, insert throws UniqueKeyException - handled silently
        verify(sqlTemplate, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void testPersistLastLockTime_handlesException() {
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("DB error"));
        assertDoesNotThrow(() -> clusterService.persistLastLockTime(ClusterConstants.PUSH, new Date(), "server-1"));
    }

    @Test
    void testUnlockCluster_persistsLastLockTime() {
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        clusterService.lock(ClusterConstants.PUSH);
        clusterService.unlock(ClusterConstants.PUSH);
        Map<String, Lock> locks = clusterService.findLocks();
        Lock lock = locks.get(ClusterConstants.PUSH);
        assertNotNull(lock.getLastLockTime());
        assertNotNull(lock.getLastLockingServerId());
        assertNull(lock.getLockTime());
        assertNull(lock.getLockingServerId());
    }

    @Test
    void testInitCallsLoadLocksFromDatabase() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(new ArrayList<>());
        clusterService.init();
        verify(sqlTemplate).query(anyString(), any(ISqlRowMapper.class));
    }

    @Test
    void testInit_clusterLockingEnabledMatchesLiveParameter_completesNormally() throws Exception {
        setClusterLockingEnabled(false);
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(false);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(new ArrayList<>());
        assertDoesNotThrow(() -> clusterService.init());
        verify(parameterService, times(2)).is(ParameterConstants.CLUSTER_LOCKING_ENABLED);
    }

    @Test
    void testInit_clusterLockingEnabledDiffersFromLiveParameter_completesNormally() throws Exception {
        setClusterLockingEnabled(true);
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(false);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(new ArrayList<>());
        assertDoesNotThrow(() -> clusterService.init());
        verify(parameterService, times(2)).is(ParameterConstants.CLUSTER_LOCKING_ENABLED);
    }

    @Test
    void testRemoveObsoleteNodeHosts_clearsLocksForObsoleteHostsOnly() {
        when(parameterService.getLong(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS)).thenReturn(86_400_000L);
        Lock pushLock = clusterService.findLocks().get(ClusterConstants.PUSH);
        pushLock.setLockingServerId("obsolete-host");
        pushLock.setLockTime(new Date());
        Lock pullLock = clusterService.findLocks().get(ClusterConstants.PULL);
        pullLock.setLockingServerId("live-host");
        pullLock.setLockTime(new Date());
        NodeHost obsoleteHost = new NodeHost();
        obsoleteHost.setHostName("obsolete-host");
        obsoleteHost.setHeartbeatTime(new Date(System.currentTimeMillis() - 90_000_000L));
        NodeHost liveHost = new NodeHost();
        liveHost.setHostName("live-host");
        liveHost.setHeartbeatTime(new Date());
        when(nodeService.findNodeHosts(anyString())).thenReturn(List.of(obsoleteHost, liveHost));
        clusterService.removeObsoleteNodeHosts();
        assertNull(pushLock.getLockingServerId());
        assertEquals("live-host", pullLock.getLockingServerId());
    }

    @Test
    void testCheckSymDbOwnership_noNodeHosts_doesNotThrow() {
        assertDoesNotThrow(() -> clusterService.checkSymDbOwnership());
    }

    @Test
    void testCheckSymDbOwnership_matchingInstanceId_doesNotThrow() {
        NodeHost nodeHost = new NodeHost();
        nodeHost.setInstanceId(ClusterService.instanceId);
        when(nodeService.findNodeHosts(anyString())).thenReturn(List.of(nodeHost));
        assertDoesNotThrow(() -> clusterService.checkSymDbOwnership());
    }

    @Test
    void testCheckSymDbOwnership_differentInstanceId_recentHeartbeat_throws() {
        when(parameterService.getLong(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS)).thenReturn(2_700_000L);
        NodeHost nodeHost = new NodeHost();
        nodeHost.setInstanceId("other-instance-id");
        nodeHost.setHeartbeatTime(new Date());
        when(nodeService.findNodeHosts(anyString())).thenReturn(List.of(nodeHost));
        assertThrows(SymmetricException.class, () -> clusterService.checkSymDbOwnership());
    }

    @Test
    void testCheckSymDbOwnership_differentInstanceId_recentHeartbeat_throwsWithHostnames() {
        when(parameterService.getLong(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS)).thenReturn(2_700_000L);
        NodeHost nodeHost = new NodeHost();
        nodeHost.setInstanceId("other-instance-id");
        nodeHost.setHostName("other-host");
        nodeHost.setHeartbeatTime(new Date());
        when(nodeService.findNodeHosts(anyString())).thenReturn(List.of(nodeHost));
        SymmetricException ex = assertThrows(SymmetricException.class, () -> clusterService.checkSymDbOwnership());
        assertTrue(ex.getMessage().contains("other-host"));
    }

    @Test
    void testCheckSymDbOwnership_differentInstanceId_staleHeartbeat_doesNotThrow() {
        when(parameterService.getLong(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS)).thenReturn(2_700_000L);
        NodeHost nodeHost = new NodeHost();
        nodeHost.setInstanceId("other-instance-id");
        nodeHost.setHeartbeatTime(new Date(System.currentTimeMillis() - 3_000_000L));
        when(nodeService.findNodeHosts(anyString())).thenReturn(List.of(nodeHost));
        assertDoesNotThrow(() -> clusterService.checkSymDbOwnership());
    }

    @Test
    void testCheckSymDbOwnership_differentInstanceId_nullHeartbeat_treatedAsStale_doesNotThrow() {
        when(parameterService.getLong(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS)).thenReturn(2_700_000L);
        NodeHost nodeHost = new NodeHost();
        nodeHost.setInstanceId("other-instance-id");
        nodeHost.setHeartbeatTime(null);
        when(nodeService.findNodeHosts(anyString())).thenReturn(List.of(nodeHost));
        assertDoesNotThrow(() -> clusterService.checkSymDbOwnership());
    }

    @Test
    void testGenerateInstanceId_producesWellFormedRandomUuidSuffix() {
        String instanceId = ClusterService.generateInstanceId("testhost");
        String uuidPart = instanceId.substring(instanceId.length() - 36);
        assertEquals(uuidPart, UUID.fromString(uuidPart).toString());
    }

    @Test
    void testGenerateInstanceId_hostnameIsTruncatedTo23Chars() {
        String longHost = "this-hostname-is-definitely-longer-than-23-characters";
        String instanceId = ClusterService.generateInstanceId(longHost);
        String prefix = instanceId.substring(0, instanceId.length() - 37);
        assertEquals(23, prefix.length());
    }

    @SuppressWarnings("unchecked")
    private void injectActivePeer(String peerId) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("lastPeerStateMap");
        f.setAccessible(true);
        ((Map<String, PeerState>) f.get(ClusteredCacheManager.getInstance())).put(peerId, new PeerState(true, System.currentTimeMillis()));
    }

    @Test
    void testIsStaleServer_nullOwner_returnsFalse() {
        assertFalse(clusterService.isStaleServer(null));
    }

    @Test
    void testIsStaleServer_emptyActivePeers_returnsFalse() {
        assertFalse(clusterService.isStaleServer("other-server"));
    }

    @Test
    void testIsStaleServer_ownerInActivePeers_returnsFalse() throws Exception {
        injectActivePeer("other-server");
        assertFalse(clusterService.isStaleServer("other-server"));
    }

    @Test
    void testIsStaleServer_ownServerId_returnsFalse() throws Exception {
        injectActivePeer("some-active-peer");
        assertFalse(clusterService.isStaleServer(clusterService.getServerId()));
    }

    @Test
    void testIsLockExpiredOrServerStale_nullLockTime_returnsTrue() {
        assertTrue(clusterService.isLockExpiredOrServerStale(null, null, new Date()));
    }

    @Test
    void testIsLockExpiredOrServerStale_expiredLock_returnsTrue() {
        Date lockTime = new Date(System.currentTimeMillis() - 120_000);
        Date lockTimeout = new Date(System.currentTimeMillis() - 60_000);
        assertTrue(clusterService.isLockExpiredOrServerStale("other-server", lockTime, lockTimeout));
    }

    @Test
    void testIsLockExpiredOrServerStale_freshLock_notStale_returnsFalse() {
        Date lockTime = new Date();
        Date lockTimeout = new Date(System.currentTimeMillis() - 60_000);
        assertFalse(clusterService.isLockExpiredOrServerStale("other-server", lockTime, lockTimeout));
    }

    @Test
    void testIsLockExpiredOrServerStale_freshLock_ownerStale_returnsTrue() throws Exception {
        mockActivePeers("active-server");
        Date lockTime = new Date();
        Date lockTimeout = new Date(System.currentTimeMillis() - 60_000);
        assertTrue(clusterService.isLockExpiredOrServerStale("other-server", lockTime, lockTimeout));
    }

    @Test
    void testLockCluster_freshLockNotStale_doesNotBreak() {
        Lock lock = clusterService.lockCache.get(ClusterConstants.PUSH);
        lock.setLockingServerId("active-server");
        lock.setLockTime(new Date());
        Date timeToBreakLock = new Date(System.currentTimeMillis() - 1);
        assertFalse(clusterService.lockCluster(ClusterConstants.PUSH, timeToBreakLock, new Date(), clusterService.getServerId()));
    }
}
