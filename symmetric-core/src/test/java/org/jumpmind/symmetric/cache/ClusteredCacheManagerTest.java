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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusteredCacheManagerTest {
    private static final long THRESHOLD_MS = 9000L;
    private ClusteredCacheManager manager;
    private IClusterCacheCoordinator mockCoordinator;
    private ISymmetricEngine mockEngine;
    private IClusterService mockClusterService;
    private IParameterService mockParameterService;
    private INodeCommunicationService mockNodeCommService;
    private ISecurityService mockSecurityService;
    private Method isPeerAlive;
    private Method detectPeerState;
    private Map<String, Boolean> peerStateMap;
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
        Field coordinatorField = ClusteredCacheManager.class.getDeclaredField("coordinator");
        coordinatorField.setAccessible(true);
        coordinatorField.set(manager, mockCoordinator);
        mockClusterService = mock(IClusterService.class);
        when(mockClusterService.getServerId()).thenReturn("server1");
        when(mockClusterService.getInstanceId()).thenReturn("instance1");
        mockParameterService = mock(IParameterService.class);
        when(mockParameterService.getEngineName()).thenReturn("engine1");
        when(mockParameterService.getLong(anyString(), anyLong())).thenReturn(3000L);
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_STALE_MS), anyLong())).thenReturn(9000L);
        mockNodeCommService = mock(INodeCommunicationService.class);
        mockEngine = mock(ISymmetricEngine.class);
        when(mockEngine.getEngineName()).thenReturn("engine1");
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
        Field peerStateMapField = ClusteredCacheManager.class.getDeclaredField("peerStateMap");
        peerStateMapField.setAccessible(true);
        peerStateMap = (Map<String, Boolean>) peerStateMapField.get(manager);
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
        return new ClusterPeerStatusMessage(eventType, peerId, "inst-" + peerId, "1.0");
    }

    private void setRunning(boolean value) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("running");
        f.setAccessible(true);
        f.set(manager, value);
    }

    private boolean getRunning() throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("running");
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

    private void setMyInstanceId(String value) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("myInstanceId");
        f.setAccessible(true);
        f.set(manager, value);
    }

    private void callDetectEngineState(String peerId, String engineName, ClusterEngineStateMessage msg) throws Exception {
        detectEngineStateMethod.invoke(manager, peerId, engineName, msg, System.currentTimeMillis(), THRESHOLD_MS);
    }

    @Test
    public void registerEngine_doesNotStartCoordinator() throws Exception {
        manager.registerEngine(mockEngine);
        verify(mockCoordinator, never()).start(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void registerEngine_secondEngine_doesNotRestartCoordinator() throws Exception {
        ISymmetricEngine engine2 = mock(ISymmetricEngine.class);
        when(engine2.getEngineName()).thenReturn("engine2");
        when(engine2.getClusterService()).thenReturn(mockClusterService);
        when(engine2.getParameterService()).thenReturn(mockParameterService);
        when(engine2.getSecurityService()).thenReturn(mockSecurityService);
        manager.registerEngine(mockEngine);
        manager.registerEngine(engine2);
        verify(mockCoordinator, never()).start(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
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
        when(engine2.getEngineName()).thenReturn("engine2");
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
        manager.addPeer(null);
        verify(mockCoordinator, never()).addPeer(any());
    }

    @Test
    public void addPeer_ownServerId_ignored() {
        manager.registerEngine(mockEngine);
        manager.addPeer("server1");
        verify(mockCoordinator, never()).addPeer(any());
    }

    @Test
    public void addPeer_newPeer_delegatedToCoordinator() {
        manager.registerEngine(mockEngine);
        manager.addPeer("server2");
        verify(mockCoordinator).addPeer("server2");
    }

    @Test
    public void getActiveServerIds_emptyStateMap_returnsEmpty() {
        assertTrue(manager.getActiveServerIds().isEmpty());
    }

    @Test
    public void getActiveServerIds_returnsOnlyAliveServers() throws Exception {
        peerStateMap.put("server1", true);
        peerStateMap.put("server2", false);
        peerStateMap.put("server3", true);
        Set<String> active = manager.getActiveServerIds();
        assertEquals(2, active.size());
        assertTrue(active.contains("server1"));
        assertTrue(active.contains("server3"));
        assertFalse(active.contains("server2"));
    }

    @Test
    public void isOwnServerId_matchesRegisteredEngine_returnsTrue() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        m.setAccessible(true);
        assertTrue((boolean) m.invoke(manager, "server1"));
    }

    @Test
    public void isOwnServerId_noMatch_returnsFalse() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(manager, "server99"));
    }

    @Test
    public void isOwnServerId_noEngines_returnsFalse() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(manager, "server1"));
    }

    @Test
    public void stopClusterCommunication_setsRunningFalseAndCallsCoordinatorStop() throws Exception {
        setListenerStarted(true);
        manager.startClusterHeartbeat();
        assertTrue(getRunning());
        manager.stopClusterCommunication();
        assertFalse(getRunning());
        verify(mockCoordinator).stop();
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

    @Test
    public void sendMessageToPeers_constructsMessageAndCallsCoordinator() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("sendMessageToPeers", String.class);
        m.setAccessible(true);
        m.invoke(manager, ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT);
        verify(mockCoordinator, times(1)).sendMessageToPeers(any(ClusterPeerStatusMessage.class));
    }

    @Test
    public void startClusterHeartbeat_createsDaemonThreadWithCorrectName() throws Exception {
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
    public void getHeartbeatMs_nullEngine_returnsDefault() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("getHeartbeatMs", ISymmetricEngine.class);
        m.setAccessible(true);
        assertEquals(3000L, m.invoke(manager, (Object) null));
    }

    @Test
    public void getHeartbeatMs_withEngine_returnsFromParameterService() throws Exception {
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong())).thenReturn(5000L);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("getHeartbeatMs", ISymmetricEngine.class);
        m.setAccessible(true);
        assertEquals(5000L, m.invoke(manager, mockEngine));
    }

    @Test
    public void checkAllClusterPeers_noPeers_returnsZero() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>());
        Method m = ClusteredCacheManager.class.getDeclaredMethod("checkAllClusterPeers", long.class);
        m.setAccessible(true);
        assertEquals(0, m.invoke(manager, THRESHOLD_MS));
    }

    @Test
    public void checkAllClusterPeers_alivePeer_returnsOne() throws Exception {
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        ClusterPeerStatusMessage heartbeat = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage("peer1")).thenReturn(heartbeat);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("checkAllClusterPeers", long.class);
        m.setAccessible(true);
        assertEquals(1, m.invoke(manager, THRESHOLD_MS));
    }

    @Test
    public void checkAllClusterPeers_nullMessage_returnsZero() throws Exception {
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage("peer1")).thenReturn(null);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("checkAllClusterPeers", long.class);
        m.setAccessible(true);
        assertEquals(0, m.invoke(manager, THRESHOLD_MS));
    }

    @Test
    public void isPeerAlive_nullMessage_returnsFalse() throws Exception {
        assertFalse(callIsPeerAlive("peer1", null));
    }

    @Test
    public void isPeerAlive_peerLeaving_returnsFalse() throws Exception {
        assertFalse(callIsPeerAlive("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, "peer1")));
    }

    @Test
    public void isPeerAlive_staleHeartbeat_returnsFalse() throws Exception {
        ClusterPeerStatusMessage stale = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        long farFuture = System.currentTimeMillis() + THRESHOLD_MS + 1000L;
        assertFalse((boolean) isPeerAlive.invoke(manager, "peer1", stale, farFuture, THRESHOLD_MS));
    }

    @Test
    public void isPeerAlive_freshHeartbeat_returnsTrue() throws Exception {
        assertTrue(callIsPeerAlive("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1")));
    }

    @Test
    public void isPeerAlive_peerJoining_returnsTrue() throws Exception {
        assertTrue(callIsPeerAlive("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_JOINING, "peer1")));
    }

    @Test
    public void detectPeerState_firstHeartbeat_peerMarkedAlive() throws Exception {
        boolean isActive = callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1"));
        assertTrue(isActive);
        assertTrue(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_consecutiveHeartbeats_staysAlive() throws Exception {
        ClusterPeerStatusMessage hb = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        callDetectPeerState("peer1", hb);
        assertTrue((boolean) callDetectPeerState("peer1", hb));
        assertTrue(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_peerJoining_peerMarkedAlive() throws Exception {
        assertTrue(callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_JOINING, "peer1")));
        assertTrue(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_nullMessageAfterAlive_peerMarkedCrashed() throws Exception {
        callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1"));
        assertFalse(callDetectPeerState("peer1", null));
        assertFalse(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_staleMessageAfterAlive_peerMarkedCrashed() throws Exception {
        callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1"));
        ClusterPeerStatusMessage stale = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        long farFuture = System.currentTimeMillis() + THRESHOLD_MS + 1000L;
        assertFalse((boolean) detectPeerState.invoke(manager, "peer1", stale, farFuture, THRESHOLD_MS));
        assertFalse(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_peerLeavingAfterAlive_removedFromStateMap() throws Exception {
        callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1"));
        assertFalse(callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, "peer1")));
        assertNull(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_nullMessageNeverAlive_noStateRecorded() throws Exception {
        assertFalse(callDetectPeerState("peer1", null));
        assertNull(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_crashedPeerRejoins_markedAliveAgain() throws Exception {
        callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1"));
        callDetectPeerState("peer1", null);
        assertFalse(peerStateMap.get("peer1"));
        assertTrue(callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_JOINING, "peer1")));
        assertTrue(peerStateMap.get("peer1"));
    }

    @Test
    public void onPeerJoined_differentInstanceWithLockingEnabled_doesNotShutdown() throws Exception {
        when(mockParameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        manager.registerEngine(mockEngine);
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, "server2", "other-instance", "1.0");
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        verify(mockEngine, never()).stop();
    }

    @Test
    public void onPeerJoined_sameInstanceDifferentServer_triggersShutdown() throws Exception {
        manager.registerEngine(mockEngine);
        suppressExit();
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, "server2", "instance1", "1.0");
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        verify(mockEngine).stop();
    }

    @Test
    public void onPeerJoined_clusterLockingDisabled_triggersShutdown() throws Exception {
        when(mockParameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(false);
        manager.registerEngine(mockEngine);
        suppressExit();
        ClusterPeerStatusMessage joinMsg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, "server2", "other-instance", "1.0");
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerJoined", ClusterPeerSecureMessage.class);
        m.setAccessible(true);
        m.invoke(manager, joinMsg);
        verify(mockEngine).stop();
    }

    private void suppressExit() throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("exitAction");
        f.setAccessible(true);
        f.set(manager, (Runnable) () -> {
        });
    }

    @Test
    public void onPeerCrashed_clearsLocksOnAllEngines() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerCrashed", String.class);
        m.setAccessible(true);
        m.invoke(manager, "crashed-server");
        verify(mockClusterService).clearLocksForServer("crashed-server");
        verify(mockNodeCommService).clearLocksForServer("crashed-server");
    }

    @Test
    public void onPeerLeft_clearsLocksOnAllEngines() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerLeft", String.class);
        m.setAccessible(true);
        m.invoke(manager, "leaving-server");
        verify(mockClusterService).clearLocksForServer("leaving-server");
        verify(mockNodeCommService).clearLocksForServer("leaving-server");
    }

    @Test
    public void detectPeerState_peerLeavingAfterAlive_clearsLocks() throws Exception {
        when(mockParameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        manager.registerEngine(mockEngine);
        callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1"));
        callDetectPeerState("peer1", msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, "peer1"));
        verify(mockClusterService).clearLocksForServer("peer1");
        verify(mockNodeCommService).clearLocksForServer("peer1");
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
    public void getStaleMs_nullEngine_returns100xDefaultHeartbeat() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("getStaleMs", ISymmetricEngine.class);
        m.setAccessible(true);
        assertEquals(100 * ClusteredCacheManager.DEFAULT_HEARTBEAT_MS, m.invoke(manager, (Object) null));
    }

    @Test
    public void getStaleMs_withEngine_returnsConfiguredValue() throws Exception {
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_STALE_MS), anyLong())).thenReturn(120_000L);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("getStaleMs", ISymmetricEngine.class);
        m.setAccessible(true);
        assertEquals(120_000L, m.invoke(manager, mockEngine));
    }

    @Test
    public void isPeerAlive_ageAtExact20xHeartbeat_returnsTrue() throws Exception {
        long heartbeatMs = 1000L;
        Field f = ClusteredCacheManager.class.getDeclaredField("currentHeartbeatMs");
        f.setAccessible(true);
        f.set(manager, heartbeatMs);
        ClusterPeerStatusMessage msg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        long now = msg.getTimestamp() + 20 * heartbeatMs;
        long staleThresholdMs = 200 * heartbeatMs;
        assertTrue((boolean) isPeerAlive.invoke(manager, "peer1", msg, now, staleThresholdMs));
    }

    @Test
    public void isPeerAlive_ageAt21xHeartbeat_returnsTrue() throws Exception {
        long heartbeatMs = 1000L;
        Field f = ClusteredCacheManager.class.getDeclaredField("currentHeartbeatMs");
        f.setAccessible(true);
        f.set(manager, heartbeatMs);
        ClusterPeerStatusMessage msg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        long now = msg.getTimestamp() + 21 * heartbeatMs;
        long staleThresholdMs = 200 * heartbeatMs;
        assertTrue((boolean) isPeerAlive.invoke(manager, "peer1", msg, now, staleThresholdMs));
    }

    @Test
    public void isPeerAlive_ageAt40xHeartbeat_returnsTrue() throws Exception {
        long heartbeatMs = 1000L;
        Field f = ClusteredCacheManager.class.getDeclaredField("currentHeartbeatMs");
        f.setAccessible(true);
        f.set(manager, heartbeatMs);
        ClusterPeerStatusMessage msg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        long now = msg.getTimestamp() + 40 * heartbeatMs;
        long staleThresholdMs = 200 * heartbeatMs;
        assertTrue((boolean) isPeerAlive.invoke(manager, "peer1", msg, now, staleThresholdMs));
    }

    @Test
    public void startClusterPeerListener_whenNotStarted_callsCoordinatorStart() throws Exception {
        manager.startClusterPeerListener(mockSecurityService);
        verify(mockCoordinator).start(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void startClusterPeerListener_whenAlreadyStarted_doesNotCallCoordinatorStartAgain() throws Exception {
        manager.startClusterPeerListener(mockSecurityService);
        manager.startClusterPeerListener(mockSecurityService);
        verify(mockCoordinator, times(1)).start(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void ensurePeerListenerStarted_coordinatorThrows_wrapsInRuntimeException() {
        doThrow(new RuntimeException("bind failed")).when(mockCoordinator).start(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
        Assertions.assertThrows(RuntimeException.class, () -> manager.startClusterPeerListener(mockSecurityService));
    }

    @Test
    public void isAnyPeerInState_noPeers_returnsFalse() {
        assertFalse(manager.isAnyPeerInState(ClusterPeerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    public void isAnyPeerInState_peerWithMatchingState_returnsTrue() {
        ClusterPeerStatusMessage joiningMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_JOINING, "peer1");
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage("peer1")).thenReturn(joiningMsg);
        assertTrue(manager.isAnyPeerInState(ClusterPeerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    public void isAnyPeerInState_peerWithNullMessage_returnsFalse() {
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage("peer1")).thenReturn(null);
        assertFalse(manager.isAnyPeerInState(ClusterPeerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    public void isAnyPeerInState_peerWithDifferentState_returnsFalse() {
        ClusterPeerStatusMessage heartbeatMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage("peer1")).thenReturn(heartbeatMsg);
        assertFalse(manager.isAnyPeerInState(ClusterPeerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    public void isAnyPeerOnline_noPeers_returnsFalse() {
        assertFalse(manager.isAnyPeerOnline());
    }

    @Test
    public void isAnyPeerOnline_freshHeartbeat_returnsTrue() {
        ClusterPeerStatusMessage heartbeatMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1");
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage("peer1")).thenReturn(heartbeatMsg);
        assertTrue(manager.isAnyPeerOnline());
    }

    @Test
    public void isAnyPeerOnline_peerLeaving_returnsFalse() {
        ClusterPeerStatusMessage leavingMsg = msg(ClusterPeerStatusMessage.EVENT_PEER_LEAVING, "peer1");
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getPeerStatusMessage("peer1")).thenReturn(leavingMsg);
        assertFalse(manager.isAnyPeerOnline());
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
        setMyServerId("server1");
        setMyInstanceId("instance1");
        manager.broadcastEngineState("engine1", ClusterEngineStateMessage.ENGINE_ONLINE);
        verify(mockCoordinator).sendEngineStateMessage(any(ClusterEngineStateMessage.class));
    }

    @Test
    public void broadcastEngineState_listenerNotStarted_doesNotSend() throws Exception {
        setMyServerId("server1");
        setMyInstanceId("instance1");
        manager.broadcastEngineState("engine1", ClusterEngineStateMessage.ENGINE_ONLINE);
        verify(mockCoordinator, never()).sendEngineStateMessage(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void broadcastEngineState_listenerStarted_updatesLastEngineStates() throws Exception {
        setListenerStarted(true);
        setMyServerId("server1");
        setMyInstanceId("instance1");
        manager.broadcastEngineState("engine1", ClusterEngineStateMessage.ENGINE_ONLINE);
        Field f = ClusteredCacheManager.class.getDeclaredField("lastEngineStates");
        f.setAccessible(true);
        Map<String, String> lastEngineStates = (Map<String, String>) f.get(manager);
        assertEquals(ClusterEngineStateMessage.ENGINE_ONLINE, lastEngineStates.get("engine1"));
    }

    @Test
    public void isAnyPeerWithEngineInState_noPeers_returnsFalse() {
        assertFalse(manager.isAnyPeerWithEngineInState("engine1", ClusterEngineStateMessage.ENGINE_ONLINE));
    }

    @Test
    public void isAnyPeerWithEngineInState_matchingFreshState_returnsTrue() {
        ClusterEngineStateMessage onlineMsg = new ClusterEngineStateMessage(
                ClusterEngineStateMessage.ENGINE_ONLINE, "engine1", "peer1", "inst-peer1", "1.0");
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getEngineStateMessage("peer1", "engine1")).thenReturn(onlineMsg);
        assertTrue(manager.isAnyPeerWithEngineInState("engine1", ClusterEngineStateMessage.ENGINE_ONLINE));
    }

    @Test
    public void isAnyPeerWithEngineInState_priorStateIsStale_returnsFalse() {
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        ClusterEngineStateMessage staleMsg = mock(ClusterEngineStateMessage.class);
        when(staleMsg.getEngineState()).thenReturn(ClusterEngineStateMessage.ENGINE_ONLINE);
        when(staleMsg.isStale(anyLong(), anyLong())).thenReturn(true);
        when(mockCoordinator.getEngineStateMessage("peer1", "engine1")).thenReturn(staleMsg);
        assertFalse(manager.isAnyPeerWithEngineInState("engine1", ClusterEngineStateMessage.ENGINE_ONLINE));
    }

    @Test
    public void isAnyPeerWithEngineInState_differentState_returnsFalse() {
        ClusterEngineStateMessage startingMsg = new ClusterEngineStateMessage(
                ClusterEngineStateMessage.ENGINE_STARTING, "engine1", "peer1", "inst-peer1", "1.0");
        Set<String> peers = new HashSet<>();
        peers.add("peer1");
        when(mockCoordinator.getPeerIds()).thenReturn(peers);
        when(mockCoordinator.getEngineStateMessage("peer1", "engine1")).thenReturn(startingMsg);
        assertFalse(manager.isAnyPeerWithEngineInState("engine1", ClusterEngineStateMessage.ENGINE_ONLINE));
    }

    @Test
    public void monitorClusterPeers_withEngine_readsHeartbeatFromParameterService() throws Exception {
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong())).thenReturn(100L);
        manager.registerEngine(mockEngine);
        manager.startClusterHeartbeat();
        Thread.sleep(50);
        stopHeartbeatThread();
        verify(mockParameterService, atLeastOnce()).getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong());
    }

    @Test
    public void detectEngineStateAndFireEvents_freshOnlineMsg_setsActiveInMap() throws Exception {
        callDetectEngineState("peer1", "engine1",
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_ONLINE, "engine1", "peer1", "inst-peer1", "1.0"));
        assertEquals(Boolean.TRUE, engineStateMap.get("peer1|engine1"));
    }

    @Test
    public void detectEngineStateAndFireEvents_nullMsg_neverActive_noChange() throws Exception {
        manager.registerEngine(mockEngine);
        callDetectEngineState("peer1", "engine1", null);
        verify(mockClusterService, never()).clearLocksForServer(anyString());
    }

    @Test
    public void detectEngineStateAndFireEvents_staleMsgAfterActive_callsOnPeerEngineCrashed() throws Exception {
        manager.registerEngine(mockEngine);
        callDetectEngineState("peer1", "engine1",
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_ONLINE, "engine1", "peer1", "inst-peer1", "1.0"));
        ClusterEngineStateMessage staleMsg = mock(ClusterEngineStateMessage.class);
        when(staleMsg.getEngineState()).thenReturn(ClusterEngineStateMessage.ENGINE_ONLINE);
        when(staleMsg.isStale(anyLong(), anyLong())).thenReturn(true);
        callDetectEngineState("peer1", "engine1", staleMsg);
        verify(mockClusterService).clearLocksForServer("peer1");
        verify(mockNodeCommService).clearLocksForServer("peer1");
    }

    @Test
    public void detectEngineStateAndFireEvents_offlineStateAfterActive_callsOnPeerEngineCrashed() throws Exception {
        manager.registerEngine(mockEngine);
        callDetectEngineState("peer1", "engine1",
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_ONLINE, "engine1", "peer1", "inst-peer1", "1.0"));
        callDetectEngineState("peer1", "engine1",
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_OFFLINE, "engine1", "peer1", "inst-peer1", "1.0"));
        verify(mockClusterService).clearLocksForServer("peer1");
        verify(mockNodeCommService).clearLocksForServer("peer1");
    }

    @Test
    public void detectEngineStateAndFireEvents_nullMsgAfterActive_callsOnPeerEngineCrashed() throws Exception {
        manager.registerEngine(mockEngine);
        callDetectEngineState("peer1", "engine1",
                new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_ONLINE, "engine1", "peer1", "inst-peer1", "1.0"));
        callDetectEngineState("peer1", "engine1", null);
        verify(mockClusterService).clearLocksForServer("peer1");
        verify(mockNodeCommService).clearLocksForServer("peer1");
    }

    @Test
    public void detectEngineStateAndFireEvents_continuouslyActive_doesNotCallCrashed() throws Exception {
        manager.registerEngine(mockEngine);
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(
                ClusterEngineStateMessage.ENGINE_ONLINE, "engine1", "peer1", "inst-peer1", "1.0");
        callDetectEngineState("peer1", "engine1", msg);
        callDetectEngineState("peer1", "engine1", msg);
        verify(mockClusterService, never()).clearLocksForServer(anyString());
    }

    @Test
    public void onPeerEngineCrashed_withRegisteredEngine_clearsLocks() throws Exception {
        manager.registerEngine(mockEngine);
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerEngineCrashed", String.class, String.class);
        m.setAccessible(true);
        m.invoke(manager, "peer1", "engine1");
        verify(mockClusterService).clearLocksForServer("peer1");
        verify(mockNodeCommService).clearLocksForServer("peer1");
    }

    @Test
    public void onPeerEngineCrashed_noRegisteredEngine_doesNotThrow() throws Exception {
        Method m = ClusteredCacheManager.class.getDeclaredMethod("onPeerEngineCrashed", String.class, String.class);
        m.setAccessible(true);
        m.invoke(manager, "peer1", "unknownEngine");
        verify(mockClusterService, never()).clearLocksForServer(anyString());
    }
}
