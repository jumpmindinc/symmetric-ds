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
package org.jumpmind.symmetric.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.IClusteredCacheManager.PeerState;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.util.AppUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusteredCacheManagerTest {
    private static final long THRESHOLD_MS = 9000L;
    private static final String MY_CLUSTER_PARTITION_ID = "instance1";
    private static final String TEST_CLUSTER_PARTITION_ID = "cluster1";
    private static final String TEST_SERVER_ID = "server1";
    private static final String PEER_1 = "peer1";
    private static final String PEER_1_CLUSTER_PARTITION_ID = "inst-peer1";
    private static final String PEER_2 = "peer2";
    private static final String ENGINE_1 = "engine1";
    private static final String ENGINE_2 = "engine2";
    private static final String UNKNOWN_ENGINE = "unknownEngine";
    private static final String SERVER_1 = "server1";
    private static final String SERVER_2 = "server2";
    private static final String SERVER_3 = "server3";
    private static final String SERVER_99 = "server99";
    private static final String CRASHED_SERVER = "crashed-server";
    private static final String LEAVING_SERVER = "leaving-server";
    private static final String OTHER_CLUSTER_PARTITION_ID = "other-instance";
    private static final String MANAGER_SERVER_ID = "myServer";
    private static final String MANAGER_CLUSTER_PARTITION_ID = "myInstance";
    private static final String TEST_VERSION = "1.0";
    private static final long OLDER_START_TIME_MS = 1000L;
    private static final long NEWER_START_TIME_MS = 2000L;
    private static final long RECENT_HEARTBEAT_OFFSET_MS = 1000L;
    private static final long STALE_HEARTBEAT_EXTRA_OFFSET_MS = 10_000L;
    private ClusteredCacheManager manager;
    private IClusterCacheCoordinator mockCoordinator;
    private ISymmetricEngine mockEngine;
    private IClusterService mockClusterService;
    private IParameterService mockParameterService;
    private INodeCommunicationService mockNodeCommService;
    private ISecurityService mockSecurityService;
    private Method isPeerAlive;
    private Method detectPeerState;
    private Map<String, PeerState> peerStates;
    private Map<String, Boolean> engineStateMap;
    private Method detectEngineStateMethod;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(mockSecurityService);
        Constructor<ClusteredCacheManager> ctor = ClusteredCacheManager.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        manager = ctor.newInstance();
        mockCoordinator = mock(IClusterCacheCoordinator.class);
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>());
        when(mockCoordinator.getObservedPeers()).thenReturn(new HashSet<>());
        Field coordinatorField = ClusteredCacheManager.class.getDeclaredField("coordinator");
        coordinatorField.setAccessible(true);
        coordinatorField.set(manager, mockCoordinator);
        mockClusterService = mock(IClusterService.class);
        when(mockClusterService.getServerId()).thenReturn(SERVER_1);
        mockParameterService = mock(IParameterService.class);
        when(mockParameterService.getEngineName()).thenReturn(ENGINE_1);
        when(mockParameterService.getLong(anyString(), anyLong())).thenReturn(3000L);
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_STALE_MS), anyLong())).thenReturn(ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS);
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong())).thenReturn(
                ServerConstants.CLUSTER_PEER_HEARTBEAT_DEFAULT_MS);
        when(mockParameterService.getString(eq(ServerConstants.CLUSTER_PARTITION_ID), anyString())).thenReturn(TEST_CLUSTER_PARTITION_ID);
        when(mockParameterService.getString(eq(ServerConstants.CLUSTER_SERVER_ID), anyString())).thenReturn(MANAGER_SERVER_ID);
        when(mockParameterService.getString(eq(ServerConstants.CLUSTER_PARTITION_ID), anyString())).thenReturn(MANAGER_CLUSTER_PARTITION_ID);
        mockNodeCommService = mock(INodeCommunicationService.class);
        mockEngine = mock(ISymmetricEngine.class);
        when(mockEngine.getEngineName()).thenReturn(ENGINE_1);
        when(mockEngine.getClusterService()).thenReturn(mockClusterService);
        when(mockEngine.getParameterService()).thenReturn(mockParameterService);
        when(mockEngine.getNodeCommunicationService()).thenReturn(mockNodeCommService);
        when(mockEngine.getSecurityService()).thenReturn(mockSecurityService);
        isPeerAlive = ClusteredCacheManager.class.getDeclaredMethod(
                "isPeerAlive", String.class, ClusterPeerSecureMessage.class, long.class, long.class);
        isPeerAlive.setAccessible(true);
        detectPeerState = ClusteredCacheManager.class.getDeclaredMethod(
                "detectPeerStateAndFireEvents", String.class, ClusterPeerSecureMessage.class, long.class, long.class);
        detectPeerState.setAccessible(true);
        Field peerStatesField = ClusteredCacheManager.class.getDeclaredField("peerStates");
        peerStatesField.setAccessible(true);
        peerStates = (Map<String, PeerState>) peerStatesField.get(manager);
        detectEngineStateMethod = ClusteredCacheManager.class.getDeclaredMethod(
                "detectEngineStateAndFireEvents", String.class, String.class,
                ClusterEngineStateMessage.class, long.class, long.class);
        detectEngineStateMethod.setAccessible(true);
        Field engineStateMapField = ClusteredCacheManager.class.getDeclaredField("engineStateMap");
        engineStateMapField.setAccessible(true);
        engineStateMap = (Map<String, Boolean>) engineStateMapField.get(manager);
    }

    private boolean callIsPeerAlive(String peerId, ClusterPeerStatusMessage msg) throws Exception {
        return (boolean) isPeerAlive.invoke(manager, peerId, msg, System.currentTimeMillis(), THRESHOLD_MS);
    }

    private boolean callDetectPeerState(String peerId, ClusterPeerStatusMessage msg) throws Exception {
        return (boolean) detectPeerState.invoke(manager, peerId, msg, System.currentTimeMillis(), THRESHOLD_MS);
    }

    private ClusterPeerStatusMessage msg(String eventType, String peerId) {
        return new ClusterPeerStatusMessage(eventType, peerId, "inst-" + peerId, TEST_VERSION);
    }

    private void setRunning(boolean value) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("isHeartbeatLoopRunning");
        f.setAccessible(true);
        f.set(manager, value);
    }

    private boolean getRunning() throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("isHeartbeatLoopRunning");
        f.setAccessible(true);
        return (boolean) f.get(manager);
    }

    private void setListenerStarted(boolean value) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("isClusterPeerListenerStarted");
        f.setAccessible(true);
        f.set(manager, value);
    }

    private void setMyServerId(String value) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("myServerId");
        f.setAccessible(true);
        f.set(manager, value);
    }

    private String getMyServerId() throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("myServerId");
        f.setAccessible(true);
        return (String) f.get(manager);
    }

    private void setMyClusterPartitionId(String value) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("myClusterPartitionId");
        f.setAccessible(true);
        f.set(manager, value);
    }

    private void setMyStartTimeMs(long value) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("myStartTimeMs");
        f.setAccessible(true);
        f.set(manager, value);
    }

    private void callDetectEngineState(String peerId, String engineName, ClusterEngineStateMessage msg) throws Exception {
        detectEngineStateMethod.invoke(manager, peerId, engineName, msg, System.currentTimeMillis(), THRESHOLD_MS);
    }

    @Test
    public void registerEngine_doesNotStartCoordinator() throws Exception {
        manager.registerEngine(mockEngine);
        verify(mockCoordinator, never()).start(any(IClusterCacheCoordinator.InitialSettings.class), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    public void registerEngine_secondEngine_doesNotRestartCoordinator() throws Exception {
        ISymmetricEngine engine2 = mock(ISymmetricEngine.class);
        when(engine2.getEngineName()).thenReturn(ENGINE_2);
        when(engine2.getClusterService()).thenReturn(mockClusterService);
        when(engine2.getParameterService()).thenReturn(mockParameterService);
        when(engine2.getSecurityService()).thenReturn(mockSecurityService);
        manager.registerEngine(mockEngine);
        manager.registerEngine(engine2);
        verify(mockCoordinator, never()).start(any(IClusterCacheCoordinator.InitialSettings.class), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    public void unregisterEngine_doesNotStopCoordinator() throws Exception {
        manager.registerEngine(mockEngine);
        manager.unregisterEngine(mockEngine);
        verify(mockCoordinator, never()).stop();
    }

    @Test
    public void stopClusterCommunication_stopsCoordinator() throws Exception {
        setListenerStarted(true);
        manager.stopClusterCommunication();
        verify(mockCoordinator).stop();
    }

    @Test
    public void unregisterEngine_notLastEngine_doesNotStop() throws Exception {
        ISymmetricEngine engine2 = mock(ISymmetricEngine.class);
        when(engine2.getEngineName()).thenReturn(ENGINE_2);
        when(engine2.getClusterService()).thenReturn(mockClusterService);
        when(engine2.getParameterService()).thenReturn(mockParameterService);
        when(engine2.getSecurityService()).thenReturn(mockSecurityService);
        manager.registerEngine(mockEngine);
        manager.registerEngine(engine2);
        manager.unregisterEngine(engine2);
        verify(mockCoordinator, never()).stop();
    }

    @Test
    public void addPeer_null_ignored() {
        manager.addPeer(null, null);
        verify(mockCoordinator, never()).addPeer(any());
    }

    @Test
    public void addPeer_ownServerId_ignored() {
        manager.registerEngine(mockEngine);
        manager.addPeer(SERVER_1, new Date());
        verify(mockCoordinator, never()).addPeer(any());
    }

    @Test
    public void addPeer_newPeer_delegatedToCoordinator() {
        manager.registerEngine(mockEngine);
        manager.addPeer(SERVER_2, new Date());
        verify(mockCoordinator).addPeer(SERVER_2);
    }

    @Test
    public void addPeer_recentHeartbeat_seedsOnline() {
        manager.registerEngine(mockEngine);
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(true);
        Date recentHeartbeat = new Date(System.currentTimeMillis() - 1000L);
        manager.addPeer(SERVER_2, recentHeartbeat);
        assertTrue(peerStates.get(SERVER_2).alive());
    }

    @Test
    public void addPeer_staleHeartbeat_seedsOffline() throws Exception {
        manager.registerEngine(mockEngine);
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(true);
        when(mockCoordinator.detectIfPeerIsStale(eq(SERVER_2), anyLong())).thenReturn(true);
        Field staleThresholdField = ClusteredCacheManager.class.getDeclaredField("currentStaleThresholdMs");
        staleThresholdField.setAccessible(true);
        staleThresholdField.set(manager, THRESHOLD_MS);
        Date staleHeartbeat = new Date(System.currentTimeMillis() - (THRESHOLD_MS + 10_000L));
        manager.addPeer(SERVER_2, staleHeartbeat);
        assertFalse(peerStates.get(SERVER_2).alive());
    }

    @Test
    public void addPeer_notNewPeer_skipsStateSeed() {
        manager.registerEngine(mockEngine);
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(false);
        manager.addPeer(SERVER_2, new Date());
        assertFalse(peerStates.containsKey(SERVER_2));
    }

    @Test
    public void addPeer_clusteringNotEnabled_myStartTimeNewer_triggersShutdown() throws Exception {
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        AtomicBoolean exitCalled = suppressExit();
        long now = System.currentTimeMillis();
        setMyStartTimeMs(now);
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(true);
        manager.addPeer(SERVER_2, new Date(now - RECENT_HEARTBEAT_OFFSET_MS));
        assertTrue(exitCalled.get());
    }

    @Test
    public void addPeer_clusteringNotEnabled_myStartTimeOlder_doesNotShutdown() throws Exception {
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        long now = System.currentTimeMillis();
        setMyStartTimeMs(now - RECENT_HEARTBEAT_OFFSET_MS);
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(true);
        manager.addPeer(SERVER_2, new Date(now));
        verify(mockEngine, never()).stop();
    }

    @Test
    public void addPeer_clusteringEnabled_doesNotShutdownRegardlessOfStartTime() throws Exception {
        when(mockClusterService.isClusteringEnabled()).thenReturn(true);
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        long now = System.currentTimeMillis();
        setMyStartTimeMs(now);
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(true);
        manager.addPeer(SERVER_2, new Date(now - RECENT_HEARTBEAT_OFFSET_MS));
        verify(mockEngine, never()).stop();
    }

    @Test
    public void addPeer_staleHeartbeat_skipsShutdownCheck() throws Exception {
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        setMyStartTimeMs(System.currentTimeMillis());
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(true);
        when(mockCoordinator.detectIfPeerIsStale(eq(SERVER_2), anyLong())).thenReturn(true);
        Field staleThresholdField = ClusteredCacheManager.class.getDeclaredField("currentStaleThresholdMs");
        staleThresholdField.setAccessible(true);
        staleThresholdField.set(manager, THRESHOLD_MS);
        Date staleHeartbeat = new Date(System.currentTimeMillis() - (THRESHOLD_MS + STALE_HEARTBEAT_EXTRA_OFFSET_MS));
        manager.addPeer(SERVER_2, staleHeartbeat);
        verify(mockEngine, never()).stop();
    }

    @Test
    public void announceDiscoveredPeer_null_ignored() {
        manager.announceDiscoveredPeer(null, "10.0.0.5");
        verify(mockCoordinator, never()).announceDiscoveredPeer(any(), any());
    }

    @Test
    public void announceDiscoveredPeer_ownServerId_ignored() {
        manager.registerEngine(mockEngine);
        manager.announceDiscoveredPeer(SERVER_1, "10.0.0.5");
        verify(mockCoordinator, never()).announceDiscoveredPeer(any(), any());
    }

    @Test
    public void announceDiscoveredPeer_newPeer_delegatedToCoordinator() {
        manager.registerEngine(mockEngine);
        when(mockCoordinator.announceDiscoveredPeer(SERVER_2, "10.0.0.5")).thenReturn(true);
        assertTrue(manager.announceDiscoveredPeer(SERVER_2, "10.0.0.5"));
        verify(mockCoordinator).announceDiscoveredPeer(SERVER_2, "10.0.0.5");
    }

    @Test
    public void discoverPeersIncomingHeartbeats_noObservedPeers_returnsZero() throws Exception {
        manager.registerEngine(mockEngine);
        assertEquals(0, invokeDiscoverPeersIncomingHeartbeats());
        verify(mockCoordinator, never()).addPeer(anyString());
    }

    @Test
    public void discoverPeersIncomingHeartbeats_newPeerObserved_addsPeerAndReturnsOne() throws Exception {
        manager.registerEngine(mockEngine);
        ClusterPeerStatusMessage observedMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, SERVER_2);
        when(mockCoordinator.getObservedPeers()).thenReturn(Set.of(observedMsg));
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(true);
        assertEquals(1, invokeDiscoverPeersIncomingHeartbeats());
        verify(mockCoordinator).addPeer(SERVER_2);
    }

    @Test
    public void discoverPeersIncomingHeartbeats_multipleObservedPeers_addsEachOneAndReturnsCount() throws Exception {
        manager.registerEngine(mockEngine);
        ClusterPeerStatusMessage peer1Msg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        ClusterPeerStatusMessage peer2Msg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_2);
        when(mockCoordinator.getObservedPeers()).thenReturn(Set.of(peer1Msg, peer2Msg));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        when(mockCoordinator.addPeer(PEER_2)).thenReturn(true);
        assertEquals(2, invokeDiscoverPeersIncomingHeartbeats());
        verify(mockCoordinator).addPeer(PEER_1);
        verify(mockCoordinator).addPeer(PEER_2);
    }

    @Test
    public void discoverPeersIncomingHeartbeats_ownServerIdObserved_ignoredSafelyAndReturnsZero() throws Exception {
        manager.registerEngine(mockEngine);
        ClusterPeerStatusMessage observedMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, SERVER_1);
        when(mockCoordinator.getObservedPeers()).thenReturn(Set.of(observedMsg));
        assertEquals(0, invokeDiscoverPeersIncomingHeartbeats());
        verify(mockCoordinator, never()).addPeer(SERVER_1);
    }

    @Test
    public void discoverPeersIncomingHeartbeats_notNewPeer_notCountedAsNew() throws Exception {
        manager.registerEngine(mockEngine);
        ClusterPeerStatusMessage observedMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, SERVER_2);
        when(mockCoordinator.getObservedPeers()).thenReturn(Set.of(observedMsg));
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(false);
        assertEquals(0, invokeDiscoverPeersIncomingHeartbeats());
    }

    private int invokeDiscoverPeersIncomingHeartbeats() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("discoverPeersIncomingHeartbeats");
        m.setAccessible(true);
        return (int) m.invoke(manager);
    }

    @Test
    public void getActiveServerIds_emptyStateMap_returnsEmpty() {
        assertTrue(manager.getActiveServerIds().isEmpty());
    }

    @Test
    public void getActiveServerIds_returnsOnlyAliveServers() throws Exception {
        peerStates.put(SERVER_1, new PeerState(true, System.currentTimeMillis()));
        peerStates.put(SERVER_2, new PeerState(false, System.currentTimeMillis()));
        peerStates.put(SERVER_3, new PeerState(true, System.currentTimeMillis()));
        Set<String> active = manager.getActiveServerIds();
        assertEquals(2, active.size());
        assertTrue(active.contains(SERVER_1));
        assertTrue(active.contains(SERVER_3));
        assertFalse(active.contains(SERVER_2));
    }

    @Test
    public void purgeObsoletePeers_longOfflinePeer_isRemoved() throws Exception {
        long now = System.currentTimeMillis();
        peerStates.put(SERVER_2, new PeerState(false, now - 100_000L));
        invokePurgeObsoletePeers(now, 50_000L);
        assertFalse(peerStates.containsKey(SERVER_2));
    }

    @Test
    public void purgeObsoletePeers_longOfflinePeer_removedFromCoordinator() throws Exception {
        long now = System.currentTimeMillis();
        peerStates.put(SERVER_2, new PeerState(false, now - 100_000L));
        invokePurgeObsoletePeers(now, 50_000L);
        verify(mockCoordinator).removePeer(SERVER_2);
    }

    @Test
    public void purgeObsoletePeers_recentlyOfflinePeer_isRetained() throws Exception {
        long now = System.currentTimeMillis();
        peerStates.put(SERVER_2, new PeerState(false, now - 10_000L));
        invokePurgeObsoletePeers(now, 50_000L);
        assertTrue(peerStates.containsKey(SERVER_2));
    }

    @Test
    public void purgeObsoletePeers_recentlyOfflinePeer_doesNotRemoveFromCoordinator() throws Exception {
        long now = System.currentTimeMillis();
        peerStates.put(SERVER_2, new PeerState(false, now - 10_000L));
        invokePurgeObsoletePeers(now, 50_000L);
        verify(mockCoordinator, never()).removePeer(anyString());
    }

    @Test
    public void purgeObsoletePeers_alivePeer_isNeverRemovedRegardlessOfAge() throws Exception {
        long now = System.currentTimeMillis();
        peerStates.put(SERVER_2, new PeerState(true, now - 1_000_000L));
        invokePurgeObsoletePeers(now, 50_000L);
        assertTrue(peerStates.containsKey(SERVER_2));
    }

    @Test
    public void purgeObsoletePeers_alivePeer_doesNotRemoveFromCoordinator() throws Exception {
        long now = System.currentTimeMillis();
        peerStates.put(SERVER_2, new PeerState(true, now - 1_000_000L));
        invokePurgeObsoletePeers(now, 50_000L);
        verify(mockCoordinator, never()).removePeer(anyString());
    }

    private void invokePurgeObsoletePeers(long now, long obsoleteThresholdMs) throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("purgeObsoletePeers", long.class, long.class);
        m.setAccessible(true);
        m.invoke(manager, now, obsoleteThresholdMs);
    }

    @Test
    public void isOwnServerId_matchesRegisteredEngine_returnsTrue() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        m.setAccessible(true);
        assertTrue((boolean) m.invoke(manager, SERVER_1));
    }

    @Test
    public void isOwnServerId_noMatch_returnsFalse() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(manager, SERVER_99));
    }

    @Test
    public void isOwnServerId_noEngines_returnsFalse() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(manager, SERVER_1));
    }

    @Test
    public void isOwnServerId_noEnginesButMatchesMyServerId_returnsTrue() throws Exception {
        // Regression guard: after the last engine unregisters, no registered engine is left to recognize this JVM's own serverId, but myServerId is still
        // valid for the JVM's lifetime and must still be recognized as "self" — otherwise a lingering self-message gets misclassified as a new external peer.
        setMyServerId(MANAGER_SERVER_ID);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        m.setAccessible(true);
        assertTrue((boolean) m.invoke(manager, MANAGER_SERVER_ID));
    }

    @Test
    public void stopClusterCommunication_setsRunningFalseAndCallsCoordinatorStop() throws Exception {
        setListenerStarted(true);
        manager.startClusterHeartbeat();
        assertTrue(getRunning());
        manager.stopClusterCommunication();
        assertFalse(getRunning());
        verify(mockCoordinator).stop();
        joinHeartbeatThread();
    }

    @Test
    public void stopClusterCommunication_doesNotSendLeavingWhenListenerNotStarted() throws Exception {
        manager.startClusterHeartbeat();
        stopHeartbeatThread();
        clearInvocations(mockCoordinator);
        manager.stopClusterCommunication();
        verify(mockCoordinator, never()).sendMessageToPeers(any(ClusterPeerStatusMessage.class));
    }

    private void stopHeartbeatThread() throws Exception {
        setRunning(false);
        Field f = ClusteredCacheManager.class.getDeclaredField("heartbeatThread");
        f.setAccessible(true);
        Thread thread = (Thread) f.get(manager);
        if (thread != null) {
            thread.interrupt();
            thread.join(500);
        }
    }

    private void joinHeartbeatThread() throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("heartbeatThread");
        f.setAccessible(true);
        Thread thread = (Thread) f.get(manager);
        if (thread != null) {
            thread.join(500);
        }
    }

    @Test
    public void sendMessageToPeers_constructsMessageAndCallsCoordinator() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("sendMessageToPeers", String.class);
        m.setAccessible(true);
        m.invoke(manager, ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT);
        verify(mockCoordinator, times(1)).sendMessageToPeers(any(ClusterPeerStatusMessage.class));
    }

    @Test
    public void startClusterHeartbeat_createsDaemonThreadWithCorrectName() throws Exception {
        manager.startClusterPeerListener(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID, true);
        manager.startClusterHeartbeat();
        Field f = ClusteredCacheManager.class.getDeclaredField("heartbeatThread");
        f.setAccessible(true);
        Thread thread = (Thread) f.get(manager);
        assertNotNull(thread);
        assertTrue(thread.isDaemon());
        assertEquals("sym-cluster-heartbeat", thread.getName());
        stopHeartbeatThread();
    }

    @Test
    public void refreshSleepBetweenHeartbeats_noRegisteredEngine_returnsDefault() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("refreshSleepBetweenHeartbeats");
        m.setAccessible(true);
        assertEquals(ServerConstants.CLUSTER_PEER_HEARTBEAT_DEFAULT_MS, m.invoke(manager));
    }

    @Test
    public void refreshSleepBetweenHeartbeats_withRegisteredEngine_returnsFromParameterService() throws Exception {
        manager.registerEngine(mockEngine);
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong())).thenReturn(5000L);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("refreshSleepBetweenHeartbeats");
        m.setAccessible(true);
        assertEquals(5000L, m.invoke(manager));
    }

    @Test
    public void checkAllClusterPeers_noPeers_returnsZero() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>());
        Method m = ClusteredCacheManager.class.getDeclaredMethod("countActivePeers", long.class);
        m.setAccessible(true);
        assertEquals(0, m.invoke(manager, THRESHOLD_MS));
    }

    @Test
    public void checkAllClusterPeers_alivePeer_returnsOne() throws Exception {
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        ClusterPeerStatusMessage heartbeat = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(heartbeat);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("countActivePeers", long.class);
        m.setAccessible(true);
        assertEquals(1, m.invoke(manager, THRESHOLD_MS));
    }

    @Test
    public void checkAllClusterPeers_nullMessage_returnsZero() throws Exception {
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(null);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("countActivePeers", long.class);
        m.setAccessible(true);
        assertEquals(0, m.invoke(manager, THRESHOLD_MS));
    }

    @Test
    public void checkAllClusterPeers_multiplePeers_countsOnlyActiveOnes() throws Exception {
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        peers.add(PEER_2);
        ClusterPeerStatusMessage heartbeatMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(heartbeatMsg);
        when(mockCoordinator.getPeerStatusMessage(PEER_2)).thenReturn(null);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("countActivePeers", long.class);
        m.setAccessible(true);
        assertEquals(1, m.invoke(manager, THRESHOLD_MS));
    }

    @Test
    public void checkAllClusterPeers_withRegisteredEngine_detectsEngineCrash() throws Exception {
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        suppressExit();
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        ClusterPeerStatusMessage heartbeatMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(heartbeatMsg);
        ClusterEngineStateMessage onlineMsg = new ClusterEngineStateMessage(
                ClusterEngineStateMessage.ENGINE_ONLINE, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION);
        when(mockCoordinator.getEngineStateMessage(PEER_1, ENGINE_1)).thenReturn(onlineMsg);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("countActivePeers", long.class);
        m.setAccessible(true);
        m.invoke(manager, THRESHOLD_MS);
        assertEquals(Boolean.TRUE, engineStateMap.get(IClusterCacheCoordinator.generateEngineClusterPeerKey(PEER_1, ENGINE_1)));
        when(mockCoordinator.getEngineStateMessage(PEER_1, ENGINE_1)).thenReturn(null);
        m.invoke(manager, THRESHOLD_MS);
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    public void isPeerAlive_nullMessage_returnsFalse() throws Exception {
        assertFalse(callIsPeerAlive(PEER_1, null));
    }

    @Test
    public void isPeerAlive_peerLeaving_returnsFalse() throws Exception {
        assertFalse(callIsPeerAlive(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, PEER_1)));
    }

    @Test
    public void isPeerAlive_staleHeartbeat_returnsFalse() throws Exception {
        ClusterPeerStatusMessage stale = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        long farFuture = System.currentTimeMillis() + THRESHOLD_MS + 1000L;
        assertFalse((boolean) isPeerAlive.invoke(manager, PEER_1, stale, farFuture, THRESHOLD_MS));
    }

    @Test
    public void isPeerAlive_freshHeartbeat_returnsTrue() throws Exception {
        assertTrue(callIsPeerAlive(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1)));
    }

    @Test
    public void isPeerAlive_peerJoining_returnsTrue() throws Exception {
        assertTrue(callIsPeerAlive(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_JOINING, PEER_1)));
    }

    @Test
    public void isPeerAlive_peerInitializing_returnsTrue() throws Exception {
        assertTrue(callIsPeerAlive(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_INITIALIZING, PEER_1)));
    }

    @Test
    public void isPeerAlive_peerUpgradingDb_returnsTrue() throws Exception {
        assertTrue(callIsPeerAlive(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_UPGRADING_DB, PEER_1)));
    }

    @Test
    public void isPeerAlive_invalidChecksum_returnsFalse() throws Exception {
        ClusterPeerStatusMessage tampered = spy(msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1));
        when(tampered.isHeaderChecksumValid()).thenReturn(false);
        assertFalse(callIsPeerAlive(PEER_1, tampered));
    }

    @Test
    public void detectPeerState_firstHeartbeat_peerMarkedAlive() throws Exception {
        boolean isActive = callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1));
        assertTrue(isActive);
        assertTrue(peerStates.get(PEER_1).alive());
    }

    @Test
    public void detectPeerState_consecutiveHeartbeats_staysAlive() throws Exception {
        ClusterPeerStatusMessage hb = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        callDetectPeerState(PEER_1, hb);
        assertTrue((boolean) callDetectPeerState(PEER_1, hb));
        assertTrue(peerStates.get(PEER_1).alive());
    }

    @Test
    public void detectPeerState_peerJoining_peerMarkedAlive() throws Exception {
        assertTrue(callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_JOINING, PEER_1)));
        assertTrue(peerStates.get(PEER_1).alive());
    }

    @Test
    public void detectPeerState_nullMessageAfterAlive_peerMarkedCrashed() throws Exception {
        callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1));
        assertFalse(callDetectPeerState(PEER_1, null));
        assertFalse(peerStates.get(PEER_1).alive());
    }

    @Test
    public void detectPeerState_staleMessageAfterAlive_peerMarkedCrashed() throws Exception {
        ClusteredCacheManager spiedManager = spy(manager);
        ClusterPeerStatusMessage initialHeartbeat = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        long firstNow = System.currentTimeMillis();
        detectPeerState.invoke(spiedManager, PEER_1, initialHeartbeat, firstNow, THRESHOLD_MS);
        ClusterPeerStatusMessage stale = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        long farFuture = System.currentTimeMillis() + THRESHOLD_MS + 1000L;
        assertFalse((boolean) detectPeerState.invoke(spiedManager, PEER_1, stale, farFuture, THRESHOLD_MS));
        assertFalse(peerStates.get(PEER_1).alive());
        assertEquals(initialHeartbeat.getTimestamp(), peerStates.get(PEER_1).lastAliveMs());
        verify(spiedManager).onPeerCrashed(PEER_1);
        verify(spiedManager, never()).onPeerLeft(anyString());
    }

    @Test
    public void detectPeerState_freshButExplicitlyLeavingAfterAlive_marksLeftNotCrashed() throws Exception {
        ClusteredCacheManager spiedManager = spy(manager);
        detectPeerState.invoke(spiedManager, PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1),
                System.currentTimeMillis(), THRESHOLD_MS);
        long now = System.currentTimeMillis();
        assertFalse((boolean) detectPeerState.invoke(spiedManager, PEER_1,
                msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, PEER_1), now, THRESHOLD_MS));
        assertFalse(peerStates.get(PEER_1).alive());
        verify(spiedManager, never()).onPeerCrashed(anyString());
        verify(spiedManager).onPeerLeft(PEER_1);
    }

    @Test
    public void detectPeerState_peerLeavingAfterAlive_markedOfflineButRetained() throws Exception {
        callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1));
        long beforeLeave = System.currentTimeMillis();
        assertFalse(callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, PEER_1)));
        assertNotNull(peerStates.get(PEER_1));
        assertFalse(peerStates.get(PEER_1).alive());
        assertTrue(peerStates.get(PEER_1).lastAliveMs() >= beforeLeave);
    }

    @Test
    public void detectPeerState_nullMessageNeverAlive_noStateRecorded() throws Exception {
        assertFalse(callDetectPeerState(PEER_1, null));
        assertNull(peerStates.get(PEER_1));
    }

    @Test
    public void detectPeerState_crashedPeerRejoins_markedAliveAgain() throws Exception {
        callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1));
        callDetectPeerState(PEER_1, null);
        assertFalse(peerStates.get(PEER_1).alive());
        assertTrue(callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_JOINING, PEER_1)));
        assertTrue(peerStates.get(PEER_1).alive());
    }

    @Test
    public void onPeerJoined_differentInstanceWithClusteringEnabled_doesNotShutdown() throws Exception {
        when(mockClusterService.isClusteringEnabled()).thenReturn(true);
        manager.registerEngine(mockEngine);
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, SERVER_2, OTHER_CLUSTER_PARTITION_ID, TEST_VERSION);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        verify(mockEngine, never()).stop();
    }

    @Test
    public void onPeerJoined_sameInstanceDifferentServer_myStartTimeNewer_triggersShutdown() throws Exception {
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        setMyStartTimeMs(NEWER_START_TIME_MS);
        AtomicBoolean exitCalled = suppressExit();
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, SERVER_2, MY_CLUSTER_PARTITION_ID, TEST_VERSION, OLDER_START_TIME_MS);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        assertTrue(exitCalled.get());
    }

    @Test
    public void onPeerJoined_clusteringNotEnabled_myStartTimeNewer_triggersShutdown() throws Exception {
        when(mockClusterService.isClusteringEnabled()).thenReturn(false);
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        setMyStartTimeMs(NEWER_START_TIME_MS);
        AtomicBoolean exitCalled = suppressExit();
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, SERVER_2, OTHER_CLUSTER_PARTITION_ID, TEST_VERSION, OLDER_START_TIME_MS);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        assertTrue(exitCalled.get());
    }

    @Test
    public void onPeerJoined_lockingParameterTrueButClusteringNotEnforced_myStartTimeNewer_triggersShutdown() throws Exception {
        when(mockParameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        when(mockClusterService.isClusteringEnabled()).thenReturn(false);
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        setMyStartTimeMs(NEWER_START_TIME_MS);
        AtomicBoolean exitCalled = suppressExit();
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, SERVER_2, OTHER_CLUSTER_PARTITION_ID, TEST_VERSION, OLDER_START_TIME_MS);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        assertTrue(exitCalled.get());
    }

    @Test
    public void onPeerJoined_clusteringNotEnabled_myStartTimeOlder_doesNotShutdown() throws Exception {
        when(mockClusterService.isClusteringEnabled()).thenReturn(false);
        manager.registerEngine(mockEngine);
        setMyServerId(MANAGER_SERVER_ID);
        setMyStartTimeMs(OLDER_START_TIME_MS);
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, SERVER_2, OTHER_CLUSTER_PARTITION_ID, TEST_VERSION, NEWER_START_TIME_MS);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        verify(mockEngine, never()).stop();
    }

    @Test
    public void onPeerJoined_equalStartTimes_tieBrokenByServerIdComparison() throws Exception {
        when(mockClusterService.isClusteringEnabled()).thenReturn(false);
        manager.registerEngine(mockEngine);
        setMyServerId(SERVER_2);
        setMyStartTimeMs(OLDER_START_TIME_MS);
        AtomicBoolean exitCalled = suppressExit();
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, SERVER_1, OTHER_CLUSTER_PARTITION_ID, TEST_VERSION, OLDER_START_TIME_MS);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        assertTrue(exitCalled.get());
    }

    private AtomicBoolean suppressExit() throws Exception {
        AtomicBoolean exitCalled = new AtomicBoolean(false);
        Field f = ClusteredCacheManager.class.getDeclaredField("exitProcessAction");
        f.setAccessible(true);
        f.set(manager, (Runnable) () -> exitCalled.set(true));
        return exitCalled;
    }

    @Test
    public void onPeerCrashed_clearsLocksOnAllEngines() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerCrashed", String.class);
        m.setAccessible(true);
        m.invoke(manager, CRASHED_SERVER);
        verify(mockClusterService).clearLocksForServer(CRASHED_SERVER);
        verify(mockNodeCommService).clearLocksForServer(CRASHED_SERVER);
    }

    @Test
    public void onPeerLeft_clearsLocksOnAllEngines() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerLeft", String.class);
        m.setAccessible(true);
        m.invoke(manager, LEAVING_SERVER);
        verify(mockClusterService).clearLocksForServer(LEAVING_SERVER);
        verify(mockNodeCommService).clearLocksForServer(LEAVING_SERVER);
    }

    @Test
    public void detectPeerState_peerLeavingAfterAlive_clearsLocks() throws Exception {
        when(mockClusterService.isClusteringEnabled()).thenReturn(true);
        manager.registerEngine(mockEngine);
        callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1));
        callDetectPeerState(PEER_1, msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, PEER_1));
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    public void getAnyEngine_noEngines_returnsNull() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("getAnyEngine");
        m.setAccessible(true);
        assertNull(m.invoke(manager));
    }

    @Test
    public void getAnyEngine_withEngine_returnsEngine() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("getAnyEngine");
        m.setAccessible(true);
        assertNotNull(m.invoke(manager));
    }

    @Test
    public void refreshStaleThreshold_noRegisteredEngine_returnsDefault() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("refreshStaleThreshold");
        m.setAccessible(true);
        assertEquals(ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS, m.invoke(manager));
    }

    @Test
    public void refreshStaleThreshold_withRegisteredEngine_returnsConfiguredValue() throws Exception {
        manager.registerEngine(mockEngine);
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_STALE_MS), anyLong())).thenReturn(120_000L);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("refreshStaleThreshold");
        m.setAccessible(true);
        assertEquals(120_000L, m.invoke(manager));
    }

    @Test
    public void isPeerAlive_ageAtExact20xHeartbeat_returnsTrue() throws Exception {
        long heartbeatMs = 1000L;
        Field f = ClusteredCacheManager.class.getDeclaredField("currentHeartbeatMs");
        f.setAccessible(true);
        f.set(manager, heartbeatMs);
        ClusterPeerStatusMessage msg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        long now = msg.getTimestamp() + 20 * heartbeatMs;
        long staleThresholdMs = 200 * heartbeatMs;
        assertTrue((boolean) isPeerAlive.invoke(manager, PEER_1, msg, now, staleThresholdMs));
    }

    @Test
    public void isPeerAlive_ageAt21xHeartbeat_returnsTrue() throws Exception {
        long heartbeatMs = 1000L;
        Field f = ClusteredCacheManager.class.getDeclaredField("currentHeartbeatMs");
        f.setAccessible(true);
        f.set(manager, heartbeatMs);
        ClusterPeerStatusMessage msg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        long now = msg.getTimestamp() + 21 * heartbeatMs;
        long staleThresholdMs = 200 * heartbeatMs;
        assertTrue((boolean) isPeerAlive.invoke(manager, PEER_1, msg, now, staleThresholdMs));
    }

    @Test
    public void isPeerAlive_ageAt40xHeartbeat_returnsTrue() throws Exception {
        long heartbeatMs = 1000L;
        Field f = ClusteredCacheManager.class.getDeclaredField("currentHeartbeatMs");
        f.setAccessible(true);
        f.set(manager, heartbeatMs);
        ClusterPeerStatusMessage msg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        long now = msg.getTimestamp() + 40 * heartbeatMs;
        long staleThresholdMs = 200 * heartbeatMs;
        assertTrue((boolean) isPeerAlive.invoke(manager, PEER_1, msg, now, staleThresholdMs));
    }

    @Test
    public void startClusterPeerListener_whenNotStarted_callsCoordinatorStart() throws Exception {
        manager.startClusterPeerListener(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID, true);
        verify(mockCoordinator).start(any(IClusterCacheCoordinator.InitialSettings.class), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    public void startClusterPeerListener_whenAlreadyStarted_doesNotCallCoordinatorStartAgain() throws Exception {
        manager.startClusterPeerListener(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID, true);
        manager.startClusterPeerListener(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID, true);
        verify(mockCoordinator, times(1)).start(any(IClusterCacheCoordinator.InitialSettings.class), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    public void initialize_withServerIdGiven_usesGivenServerIdDirectly() throws Exception {
        manager.initialize(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID, true);
        assertEquals(TEST_SERVER_ID, getMyServerId());
    }

    @Test
    public void initialize_setsClusterPartitionId_exposedViaGetter() throws Exception {
        manager.initialize(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID, true);
        assertEquals(TEST_CLUSTER_PARTITION_ID, manager.getClusterPartitionId());
    }

    @Test
    public void initialize_blankServerId_resolvesFromSystemProperty() throws Exception {
        System.setProperty(ServerConstants.CLUSTER_SERVER_ID, "configured-server-id");
        try {
            manager.initialize(mockSecurityService, TEST_CLUSTER_PARTITION_ID, "", true);
            assertEquals("configured-server-id", getMyServerId());
        } finally {
            System.clearProperty(ServerConstants.CLUSTER_SERVER_ID);
        }
    }

    @Test
    public void initialize_nullServerIdAndNoConfiguration_fallsBackToHostname() throws Exception {
        System.clearProperty(ServerConstants.CLUSTER_SERVER_ID);
        manager.initialize(mockSecurityService, TEST_CLUSTER_PARTITION_ID, null, true);
        assertEquals(AppUtils.getHostName(), getMyServerId());
    }

    @Test
    public void ensurePeerListenerStarted_coordinatorThrows_wrapsInRuntimeException() {
        doThrow(new RuntimeException("bind failed")).when(mockCoordinator).start(any(IClusterCacheCoordinator.InitialSettings.class),
                org.mockito.ArgumentMatchers.anySet());
        Assertions.assertThrows(RuntimeException.class, () -> manager.startClusterPeerListener(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID,
                true));
    }

    @Test
    public void isAnyPeerInState_noPeers_returnsFalse() {
        assertFalse(manager.isAnyPeerInState(ClusterPeerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    public void isAnyPeerInState_peerWithMatchingState_returnsTrue() {
        ClusterPeerStatusMessage joiningMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_JOINING, PEER_1);
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(joiningMsg);
        assertTrue(manager.isAnyPeerInState(ClusterPeerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    public void isAnyPeerInState_peerWithNullMessage_returnsFalse() {
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(null);
        assertFalse(manager.isAnyPeerInState(ClusterPeerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    public void isAnyPeerInState_peerWithDifferentState_returnsFalse() {
        ClusterPeerStatusMessage heartbeatMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(heartbeatMsg);
        assertFalse(manager.isAnyPeerInState(ClusterPeerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    public void isAnyPeerOnline_noPeers_returnsFalse() {
        assertFalse(manager.isAnyPeerOnline());
    }

    @Test
    public void isAnyPeerOnline_freshHeartbeat_returnsTrue() {
        ClusterPeerStatusMessage heartbeatMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1);
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(heartbeatMsg);
        assertTrue(manager.isAnyPeerOnline());
    }

    @Test
    public void isAnyPeerOnline_peerLeaving_returnsFalse() {
        ClusterPeerStatusMessage leavingMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, PEER_1);
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(leavingMsg);
        assertFalse(manager.isAnyPeerOnline());
    }

    @Test
    public void addPeer_newPeerWhileListenerStarted_doesNotBroadcastDirectly() throws Exception {
        setListenerStarted(true);
        setMyServerId(MANAGER_SERVER_ID);
        setMyClusterPartitionId(MANAGER_CLUSTER_PARTITION_ID);
        when(mockCoordinator.addPeer(SERVER_2)).thenReturn(true);
        manager.registerEngine(mockEngine);
        manager.addPeer(SERVER_2, null);
        verify(mockCoordinator, never()).sendMessageToPeers(any());
        verify(mockCoordinator, never()).sendEngineStateMessage(any());
    }

    @Test
    public void addPeer_listenerNotStarted_doesNotRebroadcast() throws Exception {
        setMyServerId(MANAGER_SERVER_ID);
        setMyClusterPartitionId(MANAGER_CLUSTER_PARTITION_ID);
        manager.registerEngine(mockEngine);
        manager.addPeer(SERVER_2, null);
        verify(mockCoordinator, never()).sendMessageToPeers(any());
        verify(mockCoordinator, never()).sendEngineStateMessage(any());
    }

    @Test
    public void rebroadcastCurrentState_listenerStarted_sendsMessageToPeers() throws Exception {
        setListenerStarted(true);
        setMyServerId(MANAGER_SERVER_ID);
        setMyClusterPartitionId(MANAGER_CLUSTER_PARTITION_ID);
        manager.rebroadcastCurrentState();
        verify(mockCoordinator, atLeastOnce()).sendMessageToPeers(any(ClusterPeerStatusMessage.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rebroadcastCurrentState_listenerStarted_withEngineState_sendsEngineStateMessage() throws Exception {
        setListenerStarted(true);
        setMyServerId(MANAGER_SERVER_ID);
        setMyClusterPartitionId(MANAGER_CLUSTER_PARTITION_ID);
        Field f = ClusteredCacheManager.class.getDeclaredField("lastEngineStates");
        f.setAccessible(true);
        ((Map<String, String>) f.get(manager)).put(ENGINE_1, ClusterEngineStateMessage.ENGINE_ONLINE);
        clearInvocations(mockCoordinator);
        manager.rebroadcastCurrentState();
        verify(mockCoordinator, atLeastOnce()).sendEngineStateMessage(any(ClusterEngineStateMessage.class));
    }

    @Test
    public void rebroadcastCurrentState_listenerNotStarted_doesNotBroadcast() throws Exception {
        setMyServerId(MANAGER_SERVER_ID);
        setMyClusterPartitionId(MANAGER_CLUSTER_PARTITION_ID);
        manager.rebroadcastCurrentState();
        verify(mockCoordinator, never()).sendMessageToPeers(any());
        verify(mockCoordinator, never()).sendEngineStateMessage(any());
    }

    @Test
    public void broadcastPeerState_listenerStarted_sendsMessageToPeers() throws Exception {
        setListenerStarted(true);
        manager.broadcastPeerState(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT);
        verify(mockCoordinator).sendMessageToPeers(any(ClusterPeerStatusMessage.class));
    }

    @Test
    public void broadcastPeerState_listenerNotStarted_doesNotSendMessage() {
        manager.broadcastPeerState(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT);
        verify(mockCoordinator, never()).sendMessageToPeers(any());
    }

    @Test
    public void broadcastEngineState_listenerStarted_sendsEngineStateMessage() throws Exception {
        setListenerStarted(true);
        setMyServerId(SERVER_1);
        setMyClusterPartitionId(MY_CLUSTER_PARTITION_ID);
        manager.broadcastEngineState(ENGINE_1, ClusterEngineStateMessage.ENGINE_ONLINE);
        verify(mockCoordinator).sendEngineStateMessage(any(ClusterEngineStateMessage.class));
    }

    @Test
    public void broadcastEngineState_listenerNotStarted_doesNotSend() throws Exception {
        setMyServerId(SERVER_1);
        setMyClusterPartitionId(MY_CLUSTER_PARTITION_ID);
        manager.broadcastEngineState(ENGINE_1, ClusterEngineStateMessage.ENGINE_ONLINE);
        verify(mockCoordinator, never()).sendEngineStateMessage(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void broadcastEngineState_listenerStarted_updatesLastEngineStates() throws Exception {
        setListenerStarted(true);
        setMyServerId(SERVER_1);
        setMyClusterPartitionId(MY_CLUSTER_PARTITION_ID);
        manager.broadcastEngineState(ENGINE_1, ClusterEngineStateMessage.ENGINE_ONLINE);
        Field f = ClusteredCacheManager.class.getDeclaredField("lastEngineStates");
        f.setAccessible(true);
        Map<String, String> lastEngineStates = (Map<String, String>) f.get(manager);
        assertEquals(ClusterEngineStateMessage.ENGINE_ONLINE, lastEngineStates.get(ENGINE_1));
    }

    @Test
    public void isAnyPeerWithEngineInState_noPeers_returnsFalse() {
        assertFalse(manager.isAnyPeerWithEngineInState(ENGINE_1, ClusterEngineStateMessage.ENGINE_ONLINE));
    }

    @Test
    public void isAnyPeerWithEngineInState_matchingFreshState_returnsTrue() {
        ClusterEngineStateMessage onlineMsg = new ClusterEngineStateMessage(
                ClusterEngineStateMessage.ENGINE_ONLINE, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION);
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getEngineStateMessage(PEER_1, ENGINE_1)).thenReturn(onlineMsg);
        assertTrue(manager.isAnyPeerWithEngineInState(ENGINE_1, ClusterEngineStateMessage.ENGINE_ONLINE));
    }

    @Test
    public void isAnyPeerWithEngineInState_priorStateIsStale_returnsFalse() {
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        ClusterEngineStateMessage staleMsg = mock(ClusterEngineStateMessage.class);
        when(staleMsg.getEngineState()).thenReturn(ClusterEngineStateMessage.ENGINE_ONLINE);
        when(staleMsg.isStale(anyLong(), anyLong())).thenReturn(true);
        when(mockCoordinator.getEngineStateMessage(PEER_1, ENGINE_1)).thenReturn(staleMsg);
        assertFalse(manager.isAnyPeerWithEngineInState(ENGINE_1, ClusterEngineStateMessage.ENGINE_ONLINE));
    }

    @Test
    public void isAnyPeerWithEngineInState_differentState_returnsFalse() {
        ClusterEngineStateMessage startingMsg = new ClusterEngineStateMessage(
                ClusterEngineStateMessage.ENGINE_STARTING, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION);
        Set<String> peers = new HashSet<>();
        peers.add(PEER_1);
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getEngineStateMessage(PEER_1, ENGINE_1)).thenReturn(startingMsg);
        assertFalse(manager.isAnyPeerWithEngineInState(ENGINE_1, ClusterEngineStateMessage.ENGINE_ONLINE));
    }

    @Test
    public void monitorClusterPeers_withEngine_readsHeartbeatFromParameterService() throws Exception {
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong())).thenReturn(100L);
        manager.registerEngine(mockEngine);
        manager.startClusterPeerListener(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID, true);
        manager.startClusterHeartbeat();
        Thread.sleep(50);
        stopHeartbeatThread();
        verify(mockParameterService, atLeastOnce()).getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong());
    }

    @Test
    public void monitorClusterPeers_staleThresholdElapsed_recordsSummaryLogTimestamp() throws Exception {
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong())).thenReturn(20L);
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_STALE_MS), anyLong())).thenReturn(1L);
        manager.registerEngine(mockEngine);
        manager.startClusterPeerListener(mockSecurityService, TEST_CLUSTER_PARTITION_ID, TEST_SERVER_ID, true);
        manager.startClusterHeartbeat();
        Thread.sleep(50);
        stopHeartbeatThread();
        Field f = ClusteredCacheManager.class.getDeclaredField("lastHeartbeatSummaryLogMs");
        f.setAccessible(true);
        assertTrue((long) f.get(manager) > 0);
    }

    @Test
    public void detectEngineStateAndFireEvents_freshOnlineMsg_setsActiveInMap() throws Exception {
        callDetectEngineState(PEER_1, ENGINE_1,
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_ONLINE, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION));
        assertEquals(Boolean.TRUE, engineStateMap.get(IClusterCacheCoordinator.generateEngineClusterPeerKey(PEER_1, ENGINE_1)));
    }

    @Test
    public void detectEngineStateAndFireEvents_nullMsg_neverActive_noChange() throws Exception {
        manager.registerEngine(mockEngine);
        callDetectEngineState(PEER_1, ENGINE_1, null);
        verify(mockClusterService, never()).clearLocksForServer(anyString());
    }

    @Test
    public void detectEngineStateAndFireEvents_staleMsgAfterActive_callsOnPeerEngineCrashed() throws Exception {
        manager.registerEngine(mockEngine);
        callDetectEngineState(PEER_1, ENGINE_1,
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_ONLINE, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION));
        ClusterEngineStateMessage staleMsg = mock(ClusterEngineStateMessage.class);
        when(staleMsg.getEngineState()).thenReturn(ClusterEngineStateMessage.ENGINE_ONLINE);
        when(staleMsg.isStale(anyLong(), anyLong())).thenReturn(true);
        callDetectEngineState(PEER_1, ENGINE_1, staleMsg);
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    public void detectEngineStateAndFireEvents_offlineStateAfterActive_callsOnPeerEngineCrashed() throws Exception {
        manager.registerEngine(mockEngine);
        callDetectEngineState(PEER_1, ENGINE_1,
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_ONLINE, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION));
        callDetectEngineState(PEER_1, ENGINE_1,
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_OFFLINE, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION));
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    public void detectEngineStateAndFireEvents_nullMsgAfterActive_callsOnPeerEngineCrashed() throws Exception {
        manager.registerEngine(mockEngine);
        callDetectEngineState(PEER_1, ENGINE_1,
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_ONLINE, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION));
        callDetectEngineState(PEER_1, ENGINE_1, null);
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    public void detectEngineStateAndFireEvents_continuouslyActive_doesNotCallCrashed() throws Exception {
        manager.registerEngine(mockEngine);
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(
                ClusterEngineStateMessage.ENGINE_ONLINE, ENGINE_1, PEER_1, PEER_1_CLUSTER_PARTITION_ID, TEST_VERSION);
        callDetectEngineState(PEER_1, ENGINE_1, msg);
        callDetectEngineState(PEER_1, ENGINE_1, msg);
        verify(mockClusterService, never()).clearLocksForServer(anyString());
    }

    @Test
    public void onPeerEngineCrashed_withRegisteredEngine_clearsLocks() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerEngineCrashed", String.class, String.class);
        m.setAccessible(true);
        m.invoke(manager, PEER_1, ENGINE_1);
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    public void onPeerEngineCrashed_noRegisteredEngine_doesNotThrow() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerEngineCrashed", String.class, String.class);
        m.setAccessible(true);
        m.invoke(manager, PEER_1, UNKNOWN_ENGINE);
        verify(mockClusterService, never()).clearLocksForServer(anyString());
    }

    @Test
    public void pickDelay_returnsValueWithinInclusiveRange() {
        long min = 100L;
        long max = 200L;
        for (int i = 0; i < 200; i++) {
            long delay = ClusteredCacheManager.pickDelay(min, max);
            assertTrue(delay >= min);
            assertTrue(delay <= max);
        }
    }

    @Test
    public void pickDelay_minEqualsMax_returnsThatValue() {
        assertEquals(500L, ClusteredCacheManager.pickDelay(500L, 500L));
    }

    @Test
    public void pickDelay_minGreaterThanMax_throwsIllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ClusteredCacheManager.pickDelay(200L, 100L));
    }

    @Test
    public void pickDelay_minZero_returnsWithinRange() {
        long delay = ClusteredCacheManager.pickDelay(0L, 50L);
        assertTrue(delay >= 0L);
        assertTrue(delay <= 50L);
    }

    @Test
    public void pickDelay_acrossManyCalls_producesVariedValues() {
        Set<Long> observed = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            observed.add(ClusteredCacheManager.pickDelay(0L, 1_000_000L));
        }
        assertTrue(observed.size() > 1);
    }

    @Test
    public void generatePeerCoordinationDelay_usesHeartbeatAndStaleIntervalAsBounds() throws Exception {
        Field heartbeatField = ClusteredCacheManager.class.getDeclaredField("currentHeartbeatMs");
        heartbeatField.setAccessible(true);
        heartbeatField.set(manager, 100L);
        Field staleField = ClusteredCacheManager.class.getDeclaredField("currentStaleThresholdMs");
        staleField.setAccessible(true);
        staleField.set(manager, 200L);
        long delay = manager.generatePeerCoordinationDelay();
        assertTrue(delay >= 100L);
        assertTrue(delay <= 200L);
    }
}
