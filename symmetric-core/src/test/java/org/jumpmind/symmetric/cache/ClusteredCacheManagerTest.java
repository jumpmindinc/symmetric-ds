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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.CacheCoordinatorNetworkSettings;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClusteredCacheManagerTest {
    private static final long STALE_THRESHOLD_MS = 9000L;
    private static final String PEER_1 = "peer1";
    private static final String PEER_2 = "peer2";
    private static final String PARTITION_ID = "cluster1";
    private static final String ENGINE_1 = "engine1";
    private static final String ENGINE_2 = "engine2";
    private static final String MY_SERVER_ID = "myServer1";
    private ClusteredCacheManager manager;
    private ISymmetricEngine mockEngine;
    private ISymmetricEngine mockEngine2;
    private IClusterService mockClusterService;
    private INodeCommunicationService mockNodeCommService;
    private IParameterService mockParameterService;
    private IClusterCacheCoordinator mockCoordinator;
    private Method isPeerAliveMethod;
    private Method detectPeerStateMethod;
    private Method detectEngineStateMethod;
    private Map<String, Boolean> engineStateMap;
    private Map<String, Boolean> peerWasPreviouslyAlive;
    private Map<String, ISymmetricEngine> registeredEnginesMap;
    private Map<String, String> lastEngineStatesMap;
    private Map<String, Object> originalFieldValues;

    @BeforeEach
    void setUp() throws Exception {
        mockEngine = mock(ISymmetricEngine.class);
        mockEngine2 = mock(ISymmetricEngine.class);
        mockClusterService = mock(IClusterService.class);
        mockNodeCommService = mock(INodeCommunicationService.class);
        mockParameterService = mock(IParameterService.class);
        mockCoordinator = mock(IClusterCacheCoordinator.class);
        when(mockEngine.getEngineName()).thenReturn(ENGINE_1);
        when(mockEngine.getClusterService()).thenReturn(mockClusterService);
        when(mockEngine.getNodeCommunicationService()).thenReturn(mockNodeCommService);
        when(mockEngine.getParameterService()).thenReturn(mockParameterService);
        when(mockParameterService.getEngineName()).thenReturn(ENGINE_1);
        // Clustering must appear enabled, otherwise onPeerJoined() below routes into enforceClusterLockingOrExit(),
        // which can call System.exit() - fatal for the test JVM.
        when(mockClusterService.isClusteringEnabled()).thenReturn(true);
        when(mockEngine2.getEngineName()).thenReturn(ENGINE_2);
        manager = (ClusteredCacheManager) ClusteredCacheManager.getInstance();
        isPeerAliveMethod = ClusteredCacheManager.class.getDeclaredMethod("isPeerAlive", String.class, ClusterServerStatusMessage.class, long.class,
                long.class);
        isPeerAliveMethod.setAccessible(true);
        detectPeerStateMethod = ClusteredCacheManager.class.getDeclaredMethod("detectPeerStateAndFireEvents", String.class,
                ClusterServerStatusMessage.class, long.class, long.class);
        detectPeerStateMethod.setAccessible(true);
        detectEngineStateMethod = ClusteredCacheManager.class.getDeclaredMethod("detectEngineStateAndFireEvents", String.class, String.class,
                ClusterEngineStateMessage.class, long.class, long.class);
        detectEngineStateMethod.setAccessible(true);
        engineStateMap = (Map<String, Boolean>) getField("engineStateMap");
        peerWasPreviouslyAlive = (Map<String, Boolean>) getField("peerWasPreviouslyAlive");
        registeredEnginesMap = (Map<String, ISymmetricEngine>) getField("registeredEngines");
        lastEngineStatesMap = (Map<String, String>) getField("lastEngineStates");
        originalFieldValues = new HashMap<>();
        for (String fieldName : SNAPSHOT_FIELDS) {
            originalFieldValues.put(fieldName, getField(fieldName));
        }
        setField("peerNetworkCoordinator", mockCoordinator);
        setField("myServerId", MY_SERVER_ID);
    }

    private static final String[] SNAPSHOT_FIELDS = {
            "peerNetworkCoordinator", "converter", "myServerId", "myClusterPartitionId", "myStartTimeMs",
            "isClusterPeerListenerStarted", "isClusterLockingEnabled", "currentHeartbeatMs", "currentStaleThresholdMs",
            "lastBroadcastEventType", "symmetricEngineHolder", "heartbeatThread", "isHeartbeatLoopRunning",
            "isInitializationComplete", "exitProcessAction"
    };

    @AfterEach
    void tearDown() throws Exception {
        // ClusteredCacheManager.getInstance() is a JVM-wide singleton, so state from one test must not leak into the next.
        registeredEnginesMap.clear();
        engineStateMap.clear();
        peerWasPreviouslyAlive.clear();
        lastEngineStatesMap.clear();
        Thread heartbeatThread = (Thread) getField("heartbeatThread");
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
        }
        for (String fieldName : SNAPSHOT_FIELDS) {
            setField(fieldName, originalFieldValues.get(fieldName));
        }
    }

    private Object getField(String fieldName) throws Exception {
        Field field = ClusteredCacheManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(manager);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = ClusteredCacheManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private boolean callIsPeerAlive(String peerId, ClusterServerStatusMessage msg, long now, long staleThresholdMs) throws Exception {
        return (boolean) isPeerAliveMethod.invoke(manager, peerId, msg, now, staleThresholdMs);
    }

    private boolean callDetectPeerState(String peerId, ClusterServerStatusMessage msg, long now, long staleThresholdMs) throws Exception {
        return (boolean) detectPeerStateMethod.invoke(manager, peerId, msg, now, staleThresholdMs);
    }

    private void callDetectEngineState(String peerId, String engineName, ClusterEngineStateMessage msg, long now, long staleThresholdMs) throws Exception {
        detectEngineStateMethod.invoke(manager, peerId, engineName, msg, now, staleThresholdMs);
    }

    private boolean callAuthenticateAndJoinClusterPartition(ISymmetricEngine engine, ClusterServerStatusMessage msg) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("authenticateAndJoinClusterPartition", ISymmetricEngine.class,
                ClusterServerStatusMessage.class);
        method.setAccessible(true);
        return (boolean) method.invoke(manager, engine, msg);
    }

    private void setClusterLockingEnabled(boolean value) throws Exception {
        setField("isClusterLockingEnabled", value);
    }

    private void setMyClusterPartitionId(String value) throws Exception {
        setField("myClusterPartitionId", value);
    }

    private void setConverter(ClusterMessageConverter value) throws Exception {
        setField("converter", value);
    }

    private boolean callIsOwnServerId(String serverId) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(manager, serverId);
    }

    private void callEnsurePeerListenerStarted(CacheCoordinatorNetworkSettings settings) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("ensurePeerListenerStarted", CacheCoordinatorNetworkSettings.class);
        method.setAccessible(true);
        try {
            method.invoke(manager, settings);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    private void callStartClusterPeerListener(ISecurityService securityService, String clusterPartitionId, String serverId, boolean isClusterLockingEnabled)
            throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("startClusterPeerListener", ISecurityService.class, String.class, String.class,
                boolean.class);
        method.setAccessible(true);
        method.invoke(manager, securityService, clusterPartitionId, serverId, isClusterLockingEnabled);
    }

    private void callBroadcastCurrentStateAndEngines() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("broadcastCurrentStateAndEngines");
        method.setAccessible(true);
        method.invoke(manager);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ClusteredEngineState> callGetCurrentEngineStateSnapshot() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("getCurrentEngineStateSnapshot");
        method.setAccessible(true);
        return (Map<String, ClusteredEngineState>) method.invoke(manager);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ClusteredEngineState> callBuildCurrentEngineStateSnapshotFromRegistered() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("buildCurrentEngineStateSnapshotFromRegistered");
        method.setAccessible(true);
        return (Map<String, ClusteredEngineState>) method.invoke(manager);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ClusteredEngineState> callInvokeSymmetricEngineHolderMethod(String methodName) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("invokeSymmetricEngineHolderMethod", String.class);
        method.setAccessible(true);
        try {
            return (Map<String, ClusteredEngineState>) method.invoke(manager, methodName);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private Map<String, String> callConvertEngineStatesToStrings(Map<String, ClusteredEngineState> engineStates) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("convertEngineStatesToStrings", Map.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(manager, engineStates);
    }

    private void callSendMessageToPeers(String eventType) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("sendMessageToPeers", String.class);
        method.setAccessible(true);
        method.invoke(manager, eventType);
    }

    private void backdateMessageTimestamp(ClusterPlainMessage msg, long ageMs) throws Exception {
        Field field = ClusterPlainMessage.class.getDeclaredField("timestamp");
        field.setAccessible(true);
        field.setLong(msg, System.currentTimeMillis() - ageMs);
    }

    public static class FakeEngineHolderWithSnapshot {
        private final Map<String, ClusteredEngineState> snapshot;

        public FakeEngineHolderWithSnapshot(Map<String, ClusteredEngineState> snapshot) {
            this.snapshot = snapshot;
        }

        Map<String, ClusteredEngineState> buildCurrentEngineStateSnapshot() {
            return snapshot;
        }
    }

    public static class FakeEngineHolderThatThrows {
        Map<String, ClusteredEngineState> buildCurrentEngineStateSnapshot() {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void authenticateAndJoinClusterPartition_clusterLockingDisabled_skipsAuthenticationAndIgnoresLiveParameterService() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        setClusterLockingEnabled(false);
        when(mockParameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertTrue(callAuthenticateAndJoinClusterPartition(mockEngine, msg));
        verify(mockParameterService, never()).is(ParameterConstants.CLUSTER_LOCKING_ENABLED);
    }

    @Test
    void authenticateAndJoinClusterPartition_clusterLockingEnabled_authenticatesAndIgnoresLiveParameterService() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        setClusterLockingEnabled(true);
        when(mockParameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(false);
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertTrue(callAuthenticateAndJoinClusterPartition(mockEngine, msg));
        verify(mockParameterService, never()).is(ParameterConstants.CLUSTER_LOCKING_ENABLED);
    }

    @Test
    void isPeerAlive_freshHeartbeat_returnsTrue() throws Exception {
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertTrue(callIsPeerAlive(PEER_1, msg, msg.getTimestamp() + 1, STALE_THRESHOLD_MS));
    }

    @Test
    void isPeerAlive_staleHeartbeat_returnsFalse() throws Exception {
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertFalse(callIsPeerAlive(PEER_1, msg, msg.getTimestamp() + STALE_THRESHOLD_MS + 1, STALE_THRESHOLD_MS));
    }

    @Test
    void isPeerAlive_leavingEvent_returnsFalseRegardlessOfFreshness() throws Exception {
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_LEAVING, PEER_1, PARTITION_ID, 0L);
        assertFalse(callIsPeerAlive(PEER_1, msg, msg.getTimestamp() + 1, STALE_THRESHOLD_MS));
    }

    @Test
    void isPeerAlive_joiningEvent_returnsTrueWhenFresh() throws Exception {
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_JOINING, PEER_1, PARTITION_ID, 0L);
        assertTrue(callIsPeerAlive(PEER_1, msg, msg.getTimestamp() + 1, STALE_THRESHOLD_MS));
    }

    @Test
    void isPeerAlive_joiningEvent_returnsFalseIfStale() throws Exception {
        // The staleness check runs before the JOINING/INITIALIZING special-casing, so staleness always wins regardless of event type.
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_JOINING, PEER_1, PARTITION_ID, 0L);
        assertFalse(callIsPeerAlive(PEER_1, msg, msg.getTimestamp() + STALE_THRESHOLD_MS + 1, STALE_THRESHOLD_MS));
    }

    @Test
    void isPeerAlive_nullMessage_returnsFalse() throws Exception {
        assertFalse(callIsPeerAlive(PEER_1, null, System.currentTimeMillis(), STALE_THRESHOLD_MS));
    }

    @Test
    void detectPeerStateAndFireEvents_newAlivePeer_marksAliveAndReturnsTrue() throws Exception {
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertTrue(callDetectPeerState(PEER_1, msg, msg.getTimestamp() + 1, STALE_THRESHOLD_MS));
        assertEquals(Boolean.TRUE, peerWasPreviouslyAlive.get(PEER_1));
    }

    @Test
    void detectPeerStateAndFireEvents_aliveToStale_firesOnPeerCrashedAndClearsLocks() throws Exception {
        manager.registerEngine(mockEngine);
        ClusterServerStatusMessage aliveMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertTrue(callDetectPeerState(PEER_1, aliveMsg, aliveMsg.getTimestamp() + 1, STALE_THRESHOLD_MS));
        ClusterServerStatusMessage staleMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertFalse(callDetectPeerState(PEER_1, staleMsg, staleMsg.getTimestamp() + STALE_THRESHOLD_MS + 1, STALE_THRESHOLD_MS));
        assertEquals(Boolean.FALSE, peerWasPreviouslyAlive.get(PEER_1));
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    void detectPeerStateAndFireEvents_aliveToLeaving_firesOnPeerLeftAndClearsLocks() throws Exception {
        manager.registerEngine(mockEngine);
        ClusterServerStatusMessage aliveMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertTrue(callDetectPeerState(PEER_1, aliveMsg, aliveMsg.getTimestamp() + 1, STALE_THRESHOLD_MS));
        ClusterServerStatusMessage leavingMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_LEAVING, PEER_1, PARTITION_ID, 0L);
        assertFalse(callDetectPeerState(PEER_1, leavingMsg, leavingMsg.getTimestamp() + 1, STALE_THRESHOLD_MS));
        assertEquals(Boolean.FALSE, peerWasPreviouslyAlive.get(PEER_1));
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    void detectEngineStateAndFireEvents_activeToOffline_firesOnceAndDoesNotRefireOnRepeat() throws Exception {
        manager.registerEngine(mockEngine);
        String key = IClusterCacheCoordinator.generateEngineClusterPeerKey(PEER_1, ENGINE_1);
        ClusterEngineStateMessage activeMsg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, ENGINE_1, PEER_1, PARTITION_ID);
        callDetectEngineState(PEER_1, ENGINE_1, activeMsg, activeMsg.getTimestamp() + 1, STALE_THRESHOLD_MS);
        assertEquals(Boolean.TRUE, engineStateMap.get(key));
        verify(mockClusterService, never()).clearLocksForServer(PEER_1);
        ClusterEngineStateMessage offlineMsg = new ClusterEngineStateMessage(ClusteredEngineState.OFFLINE, ENGINE_1, PEER_1, PARTITION_ID);
        callDetectEngineState(PEER_1, ENGINE_1, offlineMsg, offlineMsg.getTimestamp() + 1, STALE_THRESHOLD_MS);
        assertEquals(Boolean.FALSE, engineStateMap.get(key));
        verify(mockClusterService, times(1)).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, times(1)).clearLocksForServer(PEER_1);
        // Reporting the same already-inactive engine again must not re-fire the crash callback.
        callDetectEngineState(PEER_1, ENGINE_1, offlineMsg, offlineMsg.getTimestamp() + 2, STALE_THRESHOLD_MS);
        verify(mockClusterService, times(1)).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, times(1)).clearLocksForServer(PEER_1);
    }

    @Test
    void refreshNodeHostHeartbeats_refreshesEveryRegisteredEngine() {
        IDataService mockDataService1 = mock(IDataService.class);
        IDataService mockDataService2 = mock(IDataService.class);
        when(mockEngine.getDataService()).thenReturn(mockDataService1);
        when(mockEngine2.getDataService()).thenReturn(mockDataService2);
        manager.registerEngine(mockEngine);
        manager.registerEngine(mockEngine2);
        manager.refreshNodeHostHeartbeats();
        verify(mockDataService1).updateNodeHostForCurrentNode(true);
        verify(mockDataService2).updateNodeHostForCurrentNode(true);
    }

    @Test
    void refreshNodeHostHeartbeats_isolatesFailurePerEngine() {
        when(mockEngine.getDataService()).thenThrow(new RuntimeException("boom"));
        IDataService mockDataService2 = mock(IDataService.class);
        when(mockEngine2.getDataService()).thenReturn(mockDataService2);
        manager.registerEngine(mockEngine);
        manager.registerEngine(mockEngine2);
        assertDoesNotThrow(() -> manager.refreshNodeHostHeartbeats());
        verify(mockDataService2).updateNodeHostForCurrentNode(true);
    }

    @Test
    void refreshNodeHostHeartbeats_noRegisteredEngines_doesNotThrow() {
        assertDoesNotThrow(() -> manager.refreshNodeHostHeartbeats());
    }

    @Test
    void getAnyEngine_returnsRegisteredEngine_notNull() throws Exception {
        manager.registerEngine(mockEngine);
        Method getAnyEngine = ClusteredCacheManager.class.getDeclaredMethod("getAnyEngine");
        getAnyEngine.setAccessible(true);
        assertSame(mockEngine, getAnyEngine.invoke(manager));
    }

    @Test
    void addPeer_nullServerId_returnsFalse() {
        assertFalse(manager.addPeer(null, null, PARTITION_ID));
    }

    @Test
    void addPeer_ownServerId_returnsFalse() {
        assertFalse(manager.addPeer(MY_SERVER_ID, null, PARTITION_ID));
        verify(mockCoordinator, never()).addPeer(anyString());
    }

    @Test
    void addPeer_partitionIdMismatch_returnsFalse() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        assertFalse(manager.addPeer(PEER_1, null, "otherPartition"));
        verify(mockCoordinator, never()).addPeer(anyString());
    }

    @Test
    void addPeer_blacklistedByConverter_returnsFalse() throws Exception {
        ClusterMessageConverter mockConverter = mock(ClusterMessageConverter.class);
        ClusterMessageConverter.RejectionInfo rejectionInfo = mock(ClusterMessageConverter.RejectionInfo.class);
        when(rejectionInfo.getReason()).thenReturn(ClusterMessageConverter.ConversionFailureReason.CHECKSUM);
        Map<String, ClusterMessageConverter.RejectionInfo> rejected = new HashMap<>();
        rejected.put(PEER_1, rejectionInfo);
        when(mockConverter.getRejectedServers()).thenReturn(rejected);
        setConverter(mockConverter);
        assertFalse(manager.addPeer(PEER_1, null, null));
        verify(mockCoordinator, never()).addPeer(anyString());
    }

    @Test
    void addPeer_coordinatorReportsNotNew_returnsFalse() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(false);
        assertFalse(manager.addPeer(PEER_1, null, null));
    }

    @Test
    void addPeer_newPeerFreshHeartbeat_returnsTrue() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        assertTrue(manager.addPeer(PEER_1, new java.util.Date(), null));
    }

    @Test
    void addPeer_newPeerNullHeartbeat_asksCoordinatorForStaleness() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        when(mockCoordinator.detectIfPeerIsStale(eq(PEER_1), any(Long.class))).thenReturn(false);
        assertTrue(manager.addPeer(PEER_1, null, null));
        verify(mockCoordinator).detectIfPeerIsStale(eq(PEER_1), any(Long.class));
    }

    @Test
    void addPeer_newPeerWithNoRegisteredEngines_doesNotEnforceClusterLocking() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        boolean[] exitInvoked = { false };
        setField("exitProcessAction", (Runnable) () -> exitInvoked[0] = true);
        assertTrue(manager.addPeer(PEER_1, new java.util.Date(), null));
        assertFalse(exitInvoked[0]);
    }

    @Test
    void addPeer_engineWithClusteringDisabled_enforcesClusterLockingViaExitAction() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        when(mockClusterService.isClusteringEnabled()).thenReturn(false);
        manager.registerEngine(mockEngine);
        // Heartbeat must be fresh (not stale) for the enforcement loop to run at all; myStartTimeMs must be later than the
        // peer's start time (derived from the heartbeat) so this node is judged "newer" => amINewer branch => exit action invoked.
        setField("myStartTimeMs", System.currentTimeMillis() + 3_600_000L);
        boolean[] exitInvoked = { false };
        setField("exitProcessAction", (Runnable) () -> exitInvoked[0] = true);
        assertTrue(manager.addPeer(PEER_1, new java.util.Date(), null));
        assertTrue(exitInvoked[0]);
    }

    @Test
    void removePeer_nullServerId_returnsFalse() {
        assertFalse(manager.removePeer(null));
        verify(mockCoordinator, never()).removePeer(anyString());
    }

    @Test
    void removePeer_ownServerId_returnsFalse() {
        assertFalse(manager.removePeer(MY_SERVER_ID));
        verify(mockCoordinator, never()).removePeer(anyString());
    }

    @Test
    void removePeer_delegatesToCoordinator_returnsTrue() {
        when(mockCoordinator.removePeer(PEER_1)).thenReturn(true);
        assertTrue(manager.removePeer(PEER_1));
    }

    @Test
    void removePeer_delegatesToCoordinator_returnsFalse() {
        when(mockCoordinator.removePeer(PEER_1)).thenReturn(false);
        assertFalse(manager.removePeer(PEER_1));
    }

    @Test
    void removePeer_coordinatorIsNull_returnsFalse() throws Exception {
        setField("peerNetworkCoordinator", null);
        assertFalse(manager.removePeer(PEER_1));
    }

    @Test
    void announceDiscoveredPeer_nullServerId_returnsFalse() {
        assertFalse(manager.announceDiscoveredPeer(null, "host:1101"));
        verify(mockCoordinator, never()).announceDiscoveredPeer(anyString(), anyString());
    }

    @Test
    void announceDiscoveredPeer_ownServerId_returnsFalse() {
        assertFalse(manager.announceDiscoveredPeer(MY_SERVER_ID, "host:1101"));
        verify(mockCoordinator, never()).announceDiscoveredPeer(anyString(), anyString());
    }

    @Test
    void announceDiscoveredPeer_delegatesToCoordinator_returnsTrue() {
        when(mockCoordinator.announceDiscoveredPeer(PEER_1, "host:1101")).thenReturn(true);
        assertTrue(manager.announceDiscoveredPeer(PEER_1, "host:1101"));
    }

    @Test
    void announceDiscoveredPeer_delegatesToCoordinator_returnsFalse() {
        when(mockCoordinator.announceDiscoveredPeer(PEER_1, "host:1101")).thenReturn(false);
        assertFalse(manager.announceDiscoveredPeer(PEER_1, "host:1101"));
    }

    @Test
    void recordPeerOffline_nullServerId_returnsFalse() {
        assertFalse(manager.recordPeerOffline(null));
    }

    @Test
    void recordPeerOffline_ownServerId_returnsFalse() {
        assertFalse(manager.recordPeerOffline(MY_SERVER_ID));
    }

    @Test
    void recordPeerOffline_otherServerId_returnsTrue() {
        assertTrue(manager.recordPeerOffline(PEER_1));
    }

    @Test
    void isOwnServerId_matchesMyServerId_returnsTrue() throws Exception {
        assertTrue(callIsOwnServerId(MY_SERVER_ID));
    }

    @Test
    void isOwnServerId_matchesRegisteredEngineServerId_returnsTrue() throws Exception {
        when(mockClusterService.getServerId()).thenReturn(PEER_1);
        manager.registerEngine(mockEngine);
        assertTrue(callIsOwnServerId(PEER_1));
    }

    @Test
    void isOwnServerId_matchesNeither_returnsFalse() throws Exception {
        when(mockClusterService.getServerId()).thenReturn(PEER_1);
        manager.registerEngine(mockEngine);
        assertFalse(callIsOwnServerId(PEER_2));
    }

    @Test
    void isClusterPeerListenerStarted_reflectsFieldTrue() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        assertTrue(manager.isClusterPeerListenerStarted());
    }

    @Test
    void isClusterPeerListenerStarted_reflectsFieldFalse() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        assertFalse(manager.isClusterPeerListenerStarted());
    }

    @Test
    void startClusterPeerListener_clusterLockingEnabled_startsPeerListener() throws Exception {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        callStartClusterPeerListener(mockSecurityService, PARTITION_ID, PEER_1, true);
        assertEquals(PARTITION_ID, manager.getClusterPartitionId());
        assertEquals(PEER_1, manager.getServerId());
        assertTrue(manager.isClusterLockingEnabled());
        assertTrue(manager.isClusterPeerListenerStarted());
        verify(mockCoordinator).start(any(CacheCoordinatorNetworkSettings.class), eq(Collections.emptySet()), any(ClusterMessageConverter.class));
    }

    @Test
    void startClusterPeerListener_clusterLockingDisabled_doesNotStartPeerListener() throws Exception {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        callStartClusterPeerListener(mockSecurityService, PARTITION_ID, PEER_1, false);
        assertEquals(PARTITION_ID, manager.getClusterPartitionId());
        assertEquals(PEER_1, manager.getServerId());
        assertFalse(manager.isClusterLockingEnabled());
        assertFalse(manager.isClusterPeerListenerStarted());
        verify(mockCoordinator, never()).start(any(), any(), any());
    }

    @Test
    void initialize_clusterLockingEnabled_startsClusterHeartbeatLoop() throws Exception {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.isInitialized()).thenReturn(true);
        try {
            manager.initialize(mockSecurityService, PARTITION_ID, PEER_1, true, null);
            assertTrue(manager.isClusterPeerListenerStarted());
            Thread thread = (Thread) getField("heartbeatThread");
            assertNotNull(thread);
            assertTrue(thread.isAlive());
        } finally {
            setField("isHeartbeatLoopRunning", false);
            Thread thread = (Thread) getField("heartbeatThread");
            if (thread != null) {
                thread.interrupt();
                thread.join(2000);
            }
        }
    }

    @Test
    void initialize_clusterLockingDisabled_doesNotStartClusterHeartbeatLoop() throws Exception {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.isInitialized()).thenReturn(true);
        manager.initialize(mockSecurityService, PARTITION_ID, PEER_1, false, null);
        assertFalse(manager.isClusterPeerListenerStarted());
        assertNull(getField("heartbeatThread"));
    }

    @Test
    void getClusterPartitionId_returnsFieldValue() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        assertEquals(PARTITION_ID, manager.getClusterPartitionId());
    }

    @Test
    void getServerId_returnsFieldValue() {
        assertEquals(MY_SERVER_ID, manager.getServerId());
    }

    @Test
    void getHeartbeatIntervalMs_returnsFieldValue() throws Exception {
        setField("currentHeartbeatMs", 12345L);
        assertEquals(12345L, manager.getHeartbeatIntervalMs());
    }

    @Test
    void getStaleIntervalMs_returnsFieldValue() throws Exception {
        setField("currentStaleThresholdMs", 54321L);
        assertEquals(54321L, manager.getStaleIntervalMs());
    }

    @Test
    void ensurePeerListenerStarted_firstCall_startsCoordinatorAndSetsFlag() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings(PEER_1, PARTITION_ID, 1101, "db", 3000L);
        callEnsurePeerListenerStarted(settings);
        assertTrue(manager.isClusterPeerListenerStarted());
        verify(mockCoordinator, times(1)).start(eq(settings), eq(Collections.emptySet()), any());
    }

    @Test
    void ensurePeerListenerStarted_calledAgainWhenAlreadyStarted_isSkipped() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        setField("isClusterPeerListenerStarted", true);
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings(PEER_1, PARTITION_ID, 1101, "db", 3000L);
        callEnsurePeerListenerStarted(settings);
        verify(mockCoordinator, never()).start(any(), any(), any());
    }

    @Test
    void ensurePeerListenerStarted_coordinatorThrows_wrapsInRuntimeExceptionAndLeavesFlagFalse() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        doThrow(new RuntimeException("boom")).when(mockCoordinator).start(any(), any(), any());
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings(PEER_1, PARTITION_ID, 1101, "db", 3000L);
        assertThrows(RuntimeException.class, () -> callEnsurePeerListenerStarted(settings));
        assertFalse(manager.isClusterPeerListenerStarted());
    }

    @Test
    void isAnyPeerInState_matchingPeer_returnsTrue() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(java.util.Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_JOINING, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        assertTrue(manager.isAnyPeerInState(ClusterServerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    void isAnyPeerInState_noPeerMatches_returnsFalse() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(java.util.Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        assertFalse(manager.isAnyPeerInState(ClusterServerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    void isAnyPeerInState_nullMessageForPeer_skippedGracefully() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(java.util.Arrays.asList(PEER_1)));
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(null);
        assertFalse(manager.isAnyPeerInState(ClusterServerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    void isAnyPeerOnline_anyPeerAlive_returnsTrue() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(java.util.Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        assertTrue(manager.isAnyPeerOnline());
    }

    @Test
    void isAnyPeerOnline_noPeers_returnsFalse() {
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        assertFalse(manager.isAnyPeerOnline());
    }

    @Test
    void isAnyPeerOnline_allPeersStale_returnsFalse() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(java.util.Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        backdateMessageTimestamp(msg, ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS + 1000L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        assertFalse(manager.isAnyPeerOnline());
    }

    @Test
    void broadcastEngineState_listenerNotStarted_updatesMapButDoesNotSend() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        manager.broadcastEngineState(ENGINE_1, ClusteredEngineState.RUNNING);
        assertEquals(ClusteredEngineState.RUNNING.getValue(), lastEngineStatesMap.get(ENGINE_1));
        verify(mockCoordinator, never()).sendEngineStates(any());
    }

    @Test
    void broadcastEngineState_listenerStarted_sendsEngineStatesMessage() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setMyClusterPartitionId(PARTITION_ID);
        manager.broadcastEngineState(ENGINE_1, ClusteredEngineState.RUNNING);
        ArgumentCaptor<ClusterEngineStateMessage> captor = ArgumentCaptor.forClass(ClusterEngineStateMessage.class);
        verify(mockCoordinator).sendEngineStates(captor.capture());
        assertEquals(ClusteredEngineState.RUNNING.getValue(), captor.getValue().getEngineState(ENGINE_1));
        assertEquals(MY_SERVER_ID, captor.getValue().getServerId());
    }

    @Test
    void startClusterHeartbeat_listenerNotStarted_doesNotStartThread() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        manager.startClusterHeartbeat();
        assertNull(getField("heartbeatThread"));
    }

    @Test
    void startClusterHeartbeat_alreadyRunning_doesNotStartAnotherThread() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setField("isHeartbeatLoopRunning", true);
        manager.startClusterHeartbeat();
        assertNull(getField("heartbeatThread"));
    }

    @Test
    void startClusterHeartbeat_listenerStartedAndNotRunning_startsDaemonThread() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.getObservedPeers()).thenReturn(Collections.emptySet());
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        try {
            manager.startClusterHeartbeat();
            Thread thread = (Thread) getField("heartbeatThread");
            assertNotNull(thread);
            assertTrue(thread.isDaemon());
            assertTrue(thread.isAlive());
        } finally {
            setField("isHeartbeatLoopRunning", false);
            Thread thread = (Thread) getField("heartbeatThread");
            if (thread != null) {
                thread.interrupt();
                thread.join(2000);
            }
        }
    }

    @Test
    void shutdown_listenerStarted_sendsLeavingMessageAndStopsCoordinator() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setMyClusterPartitionId(PARTITION_ID);
        when(mockCoordinator.isInitialized()).thenReturn(true);
        manager.shutdown();
        ArgumentCaptor<ClusterServerStatusMessage> statusCaptor = ArgumentCaptor.forClass(ClusterServerStatusMessage.class);
        verify(mockCoordinator).sendServerStatus(statusCaptor.capture());
        assertEquals(ClusterServerStatusMessage.EVENT_PEER_LEAVING, statusCaptor.getValue().getEventType());
        verify(mockCoordinator).sendEngineStates(any());
        verify(mockCoordinator).stop();
        assertFalse(manager.isClusterPeerListenerStarted());
        assertNull(getField("peerNetworkCoordinator"));
    }

    @Test
    void shutdown_listenerNotStarted_doesNotSendMessagesButStopsCoordinatorIfInitialized() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        when(mockCoordinator.isInitialized()).thenReturn(true);
        manager.shutdown();
        verify(mockCoordinator, never()).sendServerStatus(any());
        verify(mockCoordinator, never()).sendEngineStates(any());
        verify(mockCoordinator).stop();
    }

    @Test
    void shutdown_coordinatorNotInitialized_skipsStopCall() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        when(mockCoordinator.isInitialized()).thenReturn(false);
        manager.shutdown();
        verify(mockCoordinator, never()).stop();
    }

    @Test
    void shutdown_marksAllTrackedEngineStatesOffline() throws Exception {
        lastEngineStatesMap.put(ENGINE_1, ClusteredEngineState.RUNNING.getValue());
        when(mockCoordinator.isInitialized()).thenReturn(false);
        manager.shutdown();
        assertEquals(ClusteredEngineState.OFFLINE.getValue(), lastEngineStatesMap.get(ENGINE_1));
    }

    @Test
    void shutdown_interruptsRunningHeartbeatThreadWithoutThrowing() throws Exception {
        Thread runningThread = new Thread(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        runningThread.setDaemon(true);
        runningThread.start();
        setField("heartbeatThread", runningThread);
        when(mockCoordinator.isInitialized()).thenReturn(false);
        assertDoesNotThrow(() -> manager.shutdown());
        assertNull(getField("heartbeatThread"));
        runningThread.join(2000);
    }

    @Test
    void broadcastCurrentStateAndEngines_sendsServerStatusAndEngineStates() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        callBroadcastCurrentStateAndEngines();
        verify(mockCoordinator).sendServerStatus(any());
        verify(mockCoordinator).sendEngineStates(any());
    }

    @Test
    void getCurrentEngineStateSnapshot_holderNull_fallsBackToRegisteredEnginesSnapshot() throws Exception {
        setField("symmetricEngineHolder", null);
        lastEngineStatesMap.put(ENGINE_1, ClusteredEngineState.RUNNING.name());
        Map<String, ClusteredEngineState> snapshot = callGetCurrentEngineStateSnapshot();
        assertEquals(ClusteredEngineState.RUNNING, snapshot.get(ENGINE_1));
    }

    @Test
    void getCurrentEngineStateSnapshot_holderPresent_usesHolderSnapshot() throws Exception {
        Map<String, ClusteredEngineState> holderSnapshot = new HashMap<>();
        holderSnapshot.put(ENGINE_1, ClusteredEngineState.UPGRADING);
        setField("symmetricEngineHolder", new FakeEngineHolderWithSnapshot(holderSnapshot));
        Map<String, ClusteredEngineState> snapshot = callGetCurrentEngineStateSnapshot();
        assertEquals(ClusteredEngineState.UPGRADING, snapshot.get(ENGINE_1));
    }

    @Test
    void getCurrentEngineStateSnapshot_holderMethodThrows_fallsBackToRegisteredEnginesSnapshot() throws Exception {
        setField("symmetricEngineHolder", new FakeEngineHolderThatThrows());
        lastEngineStatesMap.put(ENGINE_1, ClusteredEngineState.STARTING.name());
        Map<String, ClusteredEngineState> snapshot = callGetCurrentEngineStateSnapshot();
        assertEquals(ClusteredEngineState.STARTING, snapshot.get(ENGINE_1));
    }

    @Test
    void buildCurrentEngineStateSnapshotFromRegistered_validStateStrings_parsedCorrectly() throws Exception {
        lastEngineStatesMap.put(ENGINE_1, ClusteredEngineState.RUNNING.name());
        Map<String, ClusteredEngineState> snapshot = callBuildCurrentEngineStateSnapshotFromRegistered();
        assertEquals(ClusteredEngineState.RUNNING, snapshot.get(ENGINE_1));
    }

    @Test
    void buildCurrentEngineStateSnapshotFromRegistered_invalidStateString_fallsBackToOffline() throws Exception {
        lastEngineStatesMap.put(ENGINE_1, "NOT_A_REAL_STATE");
        Map<String, ClusteredEngineState> snapshot = callBuildCurrentEngineStateSnapshotFromRegistered();
        assertEquals(ClusteredEngineState.OFFLINE, snapshot.get(ENGINE_1));
    }

    /**
     * Documents a real mismatch: broadcastEngineState()/shutdown() store engineState.getValue() (e.g. "ENGINE_RUNNING") into lastEngineStates, but this parser
     * expects the enum's name() (e.g. "RUNNING") via ClusteredEngineState.valueOf(). A value written by normal production code paths therefore always fails to
     * parse and silently falls back to OFFLINE here, regardless of the engine's true state.
     */
    @Test
    void buildCurrentEngineStateSnapshotFromRegistered_stateStringAsWrittenByBroadcastEngineState_fallsBackToOffline() throws Exception {
        lastEngineStatesMap.put(ENGINE_1, ClusteredEngineState.RUNNING.getValue());
        Map<String, ClusteredEngineState> snapshot = callBuildCurrentEngineStateSnapshotFromRegistered();
        assertEquals(ClusteredEngineState.OFFLINE, snapshot.get(ENGINE_1));
    }

    @Test
    void buildCurrentEngineStateSnapshotFromRegistered_empty_returnsEmptyMap() throws Exception {
        Map<String, ClusteredEngineState> snapshot = callBuildCurrentEngineStateSnapshotFromRegistered();
        assertTrue(snapshot.isEmpty());
    }

    @Test
    void invokeSymmetricEngineHolderMethod_successfulInvocation_returnsSnapshot() throws Exception {
        Map<String, ClusteredEngineState> holderSnapshot = new HashMap<>();
        holderSnapshot.put(ENGINE_1, ClusteredEngineState.RUNNING);
        setField("symmetricEngineHolder", new FakeEngineHolderWithSnapshot(holderSnapshot));
        Map<String, ClusteredEngineState> result = callInvokeSymmetricEngineHolderMethod("buildCurrentEngineStateSnapshot");
        assertEquals(ClusteredEngineState.RUNNING, result.get(ENGINE_1));
    }

    @Test
    void invokeSymmetricEngineHolderMethod_noSuchMethod_throwsException() throws Exception {
        setField("symmetricEngineHolder", new FakeEngineHolderWithSnapshot(new HashMap<>()));
        assertThrows(Exception.class, () -> callInvokeSymmetricEngineHolderMethod("noSuchMethodOnThisObject"));
    }

    @Test
    void convertEngineStatesToStrings_convertsEnumValuesToStrings() throws Exception {
        Map<String, ClusteredEngineState> input = new HashMap<>();
        input.put(ENGINE_1, ClusteredEngineState.RUNNING);
        input.put(ENGINE_2, ClusteredEngineState.OFFLINE);
        Map<String, String> result = callConvertEngineStatesToStrings(input);
        assertEquals(ClusteredEngineState.RUNNING.getValue(), result.get(ENGINE_1));
        assertEquals(ClusteredEngineState.OFFLINE.getValue(), result.get(ENGINE_2));
    }

    @Test
    void convertEngineStatesToStrings_emptyMap_returnsEmptyMap() throws Exception {
        assertTrue(callConvertEngineStatesToStrings(new HashMap<>()).isEmpty());
    }

    @Test
    void sendMessageToPeers_updatesLastBroadcastEventTypeAndSendsMessage() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        callSendMessageToPeers(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT);
        assertEquals(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, getField("lastBroadcastEventType"));
        ArgumentCaptor<ClusterServerStatusMessage> captor = ArgumentCaptor.forClass(ClusterServerStatusMessage.class);
        verify(mockCoordinator).sendServerStatus(captor.capture());
        assertEquals(MY_SERVER_ID, captor.getValue().getServerId());
        assertEquals(PARTITION_ID, captor.getValue().getClusterPartitionId());
        assertEquals(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, captor.getValue().getEventType());
    }

    @Test
    void getActiveServerIds_onlyIncludesAlivePeers() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(java.util.Arrays.asList(PEER_1, PEER_2)));
        ClusterServerStatusMessage aliveMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(aliveMsg);
        when(mockCoordinator.getPeerStatusMessage(PEER_2)).thenReturn(null);
        Set<String> active = manager.getActiveServerIds();
        assertTrue(active.contains(PEER_1));
        assertFalse(active.contains(PEER_2));
    }

    @Test
    void isPeerOfflineLongEnough_staleMessage_returnsTrue() throws Exception {
        ClusterServerStatusMessage staleMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        backdateMessageTimestamp(staleMsg, 1000L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(staleMsg);
        assertTrue(manager.isPeerOfflineLongEnough(PEER_1, 500L));
    }

    @Test
    void isPeerOfflineLongEnough_noMessage_returnsFalse() {
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(null);
        assertFalse(manager.isPeerOfflineLongEnough(PEER_1, STALE_THRESHOLD_MS));
    }

    @Test
    void isAnyPeerWithEngineInState_matchingFreshMessages_returnsTrue() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(java.util.Arrays.asList(PEER_1)));
        ClusterServerStatusMessage statusMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(statusMsg);
        ClusterEngineStateMessage engineMsg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, ENGINE_1, PEER_1, PARTITION_ID);
        when(mockCoordinator.getEngineStateMessage(PEER_1)).thenReturn(engineMsg);
        assertTrue(manager.isAnyPeerWithEngineInState(ENGINE_1, ClusteredEngineState.RUNNING));
    }

    @Test
    void isAnyPeerWithEngineInState_noMatch_returnsFalse() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(java.util.Arrays.asList(PEER_1)));
        ClusterServerStatusMessage statusMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(statusMsg);
        ClusterEngineStateMessage engineMsg = new ClusterEngineStateMessage(ClusteredEngineState.OFFLINE, ENGINE_1, PEER_1, PARTITION_ID);
        when(mockCoordinator.getEngineStateMessage(PEER_1)).thenReturn(engineMsg);
        assertFalse(manager.isAnyPeerWithEngineInState(ENGINE_1, ClusteredEngineState.RUNNING));
    }

    @Test
    void rebroadcastCurrentState_listenerStarted_broadcasts() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setMyClusterPartitionId(PARTITION_ID);
        manager.rebroadcastCurrentState();
        verify(mockCoordinator).sendServerStatus(any());
        verify(mockCoordinator).sendEngineStates(any());
    }

    @Test
    void rebroadcastCurrentState_listenerNotStarted_doesNothing() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        manager.rebroadcastCurrentState();
        verify(mockCoordinator, never()).sendServerStatus(any());
        verify(mockCoordinator, never()).sendEngineStates(any());
    }

    @Test
    void broadcastStateToPeers_listenerStarted_sendsMessage() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setMyClusterPartitionId(PARTITION_ID);
        manager.broadcastStateToPeers(ClusterPeerServerState.HEARTBEAT);
        ArgumentCaptor<ClusterServerStatusMessage> captor = ArgumentCaptor.forClass(ClusterServerStatusMessage.class);
        verify(mockCoordinator).sendServerStatus(captor.capture());
        assertEquals(ClusterPeerServerState.HEARTBEAT.getValue(), captor.getValue().getEventType());
    }

    @Test
    void broadcastStateToPeers_listenerNotStarted_doesNothing() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        manager.broadcastStateToPeers(ClusterPeerServerState.HEARTBEAT);
        verify(mockCoordinator, never()).sendServerStatus(any());
    }

    @Test
    void generatePeerCoordinationDelay_isBoundedByHeartbeatAndStaleInterval() throws Exception {
        setField("currentHeartbeatMs", 100L);
        setField("currentStaleThresholdMs", 200L);
        long delay = manager.generatePeerCoordinationDelay();
        assertTrue(delay >= 100L && delay <= 200L);
    }

    @Test
    void pickDelay_minGreaterThanMax_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ClusteredCacheManager.pickDelay(200L, 100L));
    }

    @Test
    void pickDelay_minEqualsMax_returnsThatValue() {
        assertEquals(500L, ClusteredCacheManager.pickDelay(500L, 500L));
    }

    @Test
    void pickDelay_minLessThanMax_returnsValueWithinRange() {
        long delay = ClusteredCacheManager.pickDelay(10L, 20L);
        assertTrue(delay >= 10L && delay <= 20L);
    }
}
