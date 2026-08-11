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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    private EngineAndPeerStateMap engineAndPeerStateMap;
    private Map<String, Boolean> peerWasPreviouslyAlive;
    private Map<String, ISymmetricEngine> registeredEnginesMap;
    private Map<String, IClusteredCacheManager.PeerState> peerStatesMap;
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
        // Most tests assume a fully running local engine; tests exercising the not-started skip path override this explicitly.
        when(mockEngine.isStarted()).thenReturn(true);
        when(mockEngine2.getEngineName()).thenReturn(ENGINE_2);
        when(mockEngine2.isStarted()).thenReturn(true);
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
        engineAndPeerStateMap = (EngineAndPeerStateMap) getField("engineAndPeerStateMap");
        peerWasPreviouslyAlive = (Map<String, Boolean>) getField("peerWasPreviouslyAlive");
        registeredEnginesMap = (Map<String, ISymmetricEngine>) getField("registeredEngines");
        peerStatesMap = (Map<String, IClusteredCacheManager.PeerState>) getField("lastPeerStateMap");
        originalFieldValues = new HashMap<>();
        for (String fieldName : SNAPSHOT_FIELDS) {
            originalFieldValues.put(fieldName, getField(fieldName));
        }
        setField("peerNetworkCoordinator", mockCoordinator);
        setField("myServerId", MY_SERVER_ID);
        setField("isInitializationComplete", true);
        // Most tests assume clustering is enabled; tests exercising the disabled path override this explicitly.
        setField("isClusterLockingEnabled", true);
    }

    private static final String[] SNAPSHOT_FIELDS = {
            "peerNetworkCoordinator", "converter", "peerDiscovery", "myServerId", "myClusterPartitionId", "myStartTimeMs",
            "isClusterPeerListenerStarted", "isClusterLockingEnabled", "currentHeartbeatMs", "currentStaleThresholdMs",
            "lastBroadcastEventType", "symmetricEngineHolder", "heartbeatThread", "isHeartbeatLoopRunning",
            "isInitializationComplete", "exitProcessAction"
    };

    @AfterEach
    void tearDown() throws Exception {
        // ClusteredCacheManager.getInstance() is a JVM-wide singleton, so state from one test must not leak into the next.
        registeredEnginesMap.clear();
        engineAndPeerStateMap.clear();
        peerWasPreviouslyAlive.clear();
        peerStatesMap.clear();
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

    private boolean callIsClusterPeerListenerActive() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("isClusterPeerListenerActive");
        method.setAccessible(true);
        return (boolean) method.invoke(manager);
    }

    private boolean callIsOwnServerId(String serverId) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("isOwnServerId", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(manager, serverId);
    }

    private void callStartClusterPeerListener(CacheCoordinatorNetworkSettings settings) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("startClusterPeerListener", CacheCoordinatorNetworkSettings.class);
        method.setAccessible(true);
        try {
            method.invoke(manager, settings);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    private void callInitializeClusterCommunicationAndDiscovery(ISecurityService securityService) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("initializeClusterCommunicationAndDiscovery", ISecurityService.class);
        method.setAccessible(true);
        method.invoke(manager, securityService);
    }

    private int callDiscoverPeersFromNodeHostTable() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("discoverPeersFromNodeHostTable");
        method.setAccessible(true);
        return (int) method.invoke(manager);
    }

    private void callBroadcastCurrentStateAndEngines() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("broadcastCurrentStateAndEngines");
        method.setAccessible(true);
        method.invoke(manager);
    }

    private void callImportCurrentEngineStatesFromHolder() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("importCurrentEngineStatesFromHolder");
        method.setAccessible(true);
        method.invoke(manager);
    }

    @SuppressWarnings("unchecked")
    private EngineAndPeerStateMap callInvokeBuildCurrentEngineStateSnapshot() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("invokeBuildCurrentEngineStateSnapshot");
        method.setAccessible(true);
        try {
            return (EngineAndPeerStateMap) method.invoke(manager);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private void callSendMessageToPeers(String eventType) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("sendMessageToPeers", String.class);
        method.setAccessible(true);
        method.invoke(manager, eventType);
    }

    private void callPurgeObsoletePeers(long now, long obsoleteThresholdMs) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("purgeObsoletePeers", long.class, long.class);
        method.setAccessible(true);
        method.invoke(manager, now, obsoleteThresholdMs);
    }

    private void callPurgePeerStates(long now, long obsoleteThresholdMs) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("purgePeerStates", long.class, long.class);
        method.setAccessible(true);
        method.invoke(manager, now, obsoleteThresholdMs);
    }

    private int callDiscoverPeersIncomingHeartbeats() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("discoverPeersIncomingHeartbeats");
        method.setAccessible(true);
        return (int) method.invoke(manager);
    }

    private int callCountActivePeers(long staleThresholdMs) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("countActivePeers", long.class);
        method.setAccessible(true);
        return (int) method.invoke(manager, staleThresholdMs);
    }

    private long callRefreshSleepBetweenHeartbeats() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("refreshSleepBetweenHeartbeats");
        method.setAccessible(true);
        return (long) method.invoke(manager);
    }

    private long callRefreshStaleThreshold() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("refreshStaleThreshold");
        method.setAccessible(true);
        return (long) method.invoke(manager);
    }

    private long callGetObsoleteMs(ISymmetricEngine engine) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("getObsoleteMs", ISymmetricEngine.class);
        method.setAccessible(true);
        return (long) method.invoke(manager, engine);
    }

    private void callLogEngineStates() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("logEngineStates");
        method.setAccessible(true);
        method.invoke(manager);
    }

    private void callLogPeerStates() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("logPeerStates");
        method.setAccessible(true);
        method.invoke(manager);
    }

    private String callElapsedSince(long startTimeMs) throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("elapsedSince", long.class);
        method.setAccessible(true);
        return (String) method.invoke(null, startTimeMs);
    }

    private void callExecuteClusterHeartbeatAndDiscoveryTick() throws Exception {
        Method method = ClusteredCacheManager.class.getDeclaredMethod("executeClusterHeartbeatAndDiscoveryTick");
        method.setAccessible(true);
        try {
            method.invoke(manager);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    private void backdateMessageTimestamp(ClusterPlainMessage msg, long ageMs) throws Exception {
        Field field = ClusterPlainMessage.class.getDeclaredField("timestamp");
        field.setAccessible(true);
        field.setLong(msg, System.currentTimeMillis() - ageMs);
    }

    public static class FakeEngineHolderWithSnapshot {
        private final EngineAndPeerStateMap snapshot;

        public FakeEngineHolderWithSnapshot(EngineAndPeerStateMap snapshot) {
            this.snapshot = snapshot;
        }

        public EngineAndPeerStateMap buildCurrentEngineStateSnapshot(String serverId) {
            return snapshot;
        }
    }

    public static class FakeEngineHolderThatThrows {
        public EngineAndPeerStateMap buildCurrentEngineStateSnapshot(String serverId) {
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
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
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
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
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
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        String key = EngineAndPeerStateMap.generateKey(PEER_1, ENGINE_1);
        ClusterEngineStateMessage activeMsg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, ENGINE_1, PEER_1, PARTITION_ID);
        callDetectEngineState(PEER_1, ENGINE_1, activeMsg, activeMsg.getTimestamp() + 1, STALE_THRESHOLD_MS);
        assertEquals(ClusteredEngineState.RUNNING, engineAndPeerStateMap.get(key));
        verify(mockClusterService, never()).clearLocksForServer(PEER_1);
        ClusterEngineStateMessage offlineMsg = new ClusterEngineStateMessage(ClusteredEngineState.OFFLINE, ENGINE_1, PEER_1, PARTITION_ID);
        callDetectEngineState(PEER_1, ENGINE_1, offlineMsg, offlineMsg.getTimestamp() + 1, STALE_THRESHOLD_MS);
        assertEquals(ClusteredEngineState.OFFLINE, engineAndPeerStateMap.get(key));
        verify(mockClusterService, times(1)).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, times(1)).clearLocksForServer(PEER_1);
        // Reporting the same already-inactive engine again must not re-fire the crash callback.
        callDetectEngineState(PEER_1, ENGINE_1, offlineMsg, offlineMsg.getTimestamp() + 2, STALE_THRESHOLD_MS);
        verify(mockClusterService, times(1)).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, times(1)).clearLocksForServer(PEER_1);
    }

    @Test
    void isClusterPeerListenerActive_listenerNotStarted_returnsFalse() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        when(mockCoordinator.isInitialized()).thenReturn(true);
        assertFalse(callIsClusterPeerListenerActive());
    }

    @Test
    void isClusterPeerListenerActive_listenerStartedAndCoordinatorInitialized_returnsTrue() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.isInitialized()).thenReturn(true);
        assertTrue(callIsClusterPeerListenerActive());
    }

    @Test
    void isClusterPeerListenerActive_listenerStartedButCoordinatorNotInitialized_returnsFalse() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.isInitialized()).thenReturn(false);
        assertFalse(callIsClusterPeerListenerActive());
    }

    @Test
    void isClusterPeerListenerActive_coordinatorNull_returnsFalse() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setField("peerNetworkCoordinator", null);
        assertFalse(callIsClusterPeerListenerActive());
    }

    @Test
    void detectPeerStateAndFireEvents_aliveToNullMessage_coordinatorNotInitialized_skipsCrashDeclaration() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.isInitialized()).thenReturn(false);
        ClusterServerStatusMessage aliveMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertTrue(callDetectPeerState(PEER_1, aliveMsg, aliveMsg.getTimestamp() + 1, STALE_THRESHOLD_MS));
        assertFalse(callDetectPeerState(PEER_1, null, System.currentTimeMillis(), STALE_THRESHOLD_MS));
        assertEquals(Boolean.TRUE, peerWasPreviouslyAlive.get(PEER_1));
        verify(mockClusterService, never()).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, never()).clearLocksForServer(PEER_1);
    }

    @Test
    void detectPeerStateAndFireEvents_aliveToNullMessage_coordinatorInitialized_stillFiresCrash() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.isInitialized()).thenReturn(true);
        ClusterServerStatusMessage aliveMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        assertTrue(callDetectPeerState(PEER_1, aliveMsg, aliveMsg.getTimestamp() + 1, STALE_THRESHOLD_MS));
        assertFalse(callDetectPeerState(PEER_1, null, System.currentTimeMillis(), STALE_THRESHOLD_MS));
        assertEquals(Boolean.FALSE, peerWasPreviouslyAlive.get(PEER_1));
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    void detectEngineStateAndFireEvents_activeToNullMessage_coordinatorNotInitialized_skipsCrashDetection() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.isInitialized()).thenReturn(false);
        String key = EngineAndPeerStateMap.generateKey(PEER_1, ENGINE_1);
        ClusterEngineStateMessage activeMsg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, ENGINE_1, PEER_1, PARTITION_ID);
        callDetectEngineState(PEER_1, ENGINE_1, activeMsg, activeMsg.getTimestamp() + 1, STALE_THRESHOLD_MS);
        assertEquals(ClusteredEngineState.RUNNING, engineAndPeerStateMap.get(key));
        callDetectEngineState(PEER_1, ENGINE_1, null, System.currentTimeMillis(), STALE_THRESHOLD_MS);
        assertEquals(ClusteredEngineState.RUNNING, engineAndPeerStateMap.get(key));
        verify(mockClusterService, never()).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, never()).clearLocksForServer(PEER_1);
    }

    @Test
    void detectEngineStateAndFireEvents_activeToNullMessage_coordinatorInitialized_stillFiresCrash() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.isInitialized()).thenReturn(true);
        String key = EngineAndPeerStateMap.generateKey(PEER_1, ENGINE_1);
        ClusterEngineStateMessage activeMsg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, ENGINE_1, PEER_1, PARTITION_ID);
        callDetectEngineState(PEER_1, ENGINE_1, activeMsg, activeMsg.getTimestamp() + 1, STALE_THRESHOLD_MS);
        callDetectEngineState(PEER_1, ENGINE_1, null, System.currentTimeMillis(), STALE_THRESHOLD_MS);
        assertEquals(ClusteredEngineState.OFFLINE, engineAndPeerStateMap.get(key));
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    void onPeerCrashed_localEngineNotStarted_skipsLockClearing() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        when(mockEngine.isStarted()).thenReturn(false);
        manager.onPeerCrashed(PEER_1);
        verify(mockClusterService, never()).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, never()).clearLocksForServer(PEER_1);
    }

    @Test
    void onPeerCrashed_localEngineStarted_clearsLocks() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.onPeerCrashed(PEER_1);
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    void onPeerLeft_localEngineNotStarted_skipsLockClearing() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        when(mockEngine.isStarted()).thenReturn(false);
        manager.onPeerLeft(PEER_1);
        verify(mockClusterService, never()).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, never()).clearLocksForServer(PEER_1);
    }

    @Test
    void onPeerEngineCrashed_localEngineNotStarted_skipsLockClearing() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        when(mockEngine.isStarted()).thenReturn(false);
        manager.onPeerEngineCrashed(PEER_1, ENGINE_1);
        verify(mockClusterService, never()).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, never()).clearLocksForServer(PEER_1);
    }

    @Test
    void onPeerEngineCrashed_engineNotRegistered_skipsLockClearingWithoutThrowing() {
        assertDoesNotThrow(() -> manager.onPeerEngineCrashed(PEER_1, "unknownEngine"));
    }

    @Test
    void clearLocksForPeer_oneOfTwoLocalEnginesNotStarted_clearsOnlyForStartedEngine() throws Exception {
        IClusterService mockClusterService2 = mock(IClusterService.class);
        INodeCommunicationService mockNodeCommService2 = mock(INodeCommunicationService.class);
        when(mockEngine2.getClusterService()).thenReturn(mockClusterService2);
        when(mockEngine2.getNodeCommunicationService()).thenReturn(mockNodeCommService2);
        when(mockEngine.isStarted()).thenReturn(false);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.registerEngine(mockEngine2, ClusteredEngineState.STARTING);
        manager.onPeerCrashed(PEER_1);
        verify(mockClusterService, never()).clearLocksForServer(PEER_1);
        verify(mockNodeCommService, never()).clearLocksForServer(PEER_1);
        verify(mockClusterService2).clearLocksForServer(PEER_1);
        verify(mockNodeCommService2).clearLocksForServer(PEER_1);
    }

    @Test
    void updateOwnNodeHostHeartbeat_refreshesEveryRegisteredEngine() {
        IDataService mockDataService1 = mock(IDataService.class);
        IDataService mockDataService2 = mock(IDataService.class);
        when(mockEngine.getDataService()).thenReturn(mockDataService1);
        when(mockEngine2.getDataService()).thenReturn(mockDataService2);
        when(mockEngine.isStarted()).thenReturn(true);
        when(mockEngine2.isStarted()).thenReturn(true);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.registerEngine(mockEngine2, ClusteredEngineState.STARTING);
        manager.updateOwnNodeHostHeartbeat();
        verify(mockDataService1).updateNodeHostForCurrentNode(true);
        verify(mockDataService2).updateNodeHostForCurrentNode(true);
    }

    @Test
    void updateOwnNodeHostHeartbeat_isolatesFailurePerEngine() {
        when(mockEngine.getDataService()).thenThrow(new RuntimeException("boom"));
        IDataService mockDataService2 = mock(IDataService.class);
        when(mockEngine2.getDataService()).thenReturn(mockDataService2);
        when(mockEngine.isStarted()).thenReturn(true);
        when(mockEngine2.isStarted()).thenReturn(true);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.registerEngine(mockEngine2, ClusteredEngineState.STARTING);
        assertDoesNotThrow(() -> manager.updateOwnNodeHostHeartbeat());
        verify(mockDataService2).updateNodeHostForCurrentNode(true);
    }

    @Test
    void updateOwnNodeHostHeartbeat_noRegisteredEngines_doesNotThrow() {
        assertDoesNotThrow(() -> manager.updateOwnNodeHostHeartbeat());
    }

    @Test
    void updateOwnNodeHostHeartbeat_engineNotYetStarted_skipsDbWrite() {
        when(mockEngine.getDataService()).thenReturn(mock(IDataService.class));
        when(mockEngine.isStarted()).thenReturn(false);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.updateOwnNodeHostHeartbeat();
        verify(mockEngine.getDataService(), never()).updateNodeHostForCurrentNode(true);
    }

    @Test
    void discoverPeersFromNodeHostTable_noRegisteredEngines_returnsZero() throws Exception {
        assertEquals(0, callDiscoverPeersFromNodeHostTable());
    }

    @Test
    void discoverPeersFromNodeHostTable_engineNotYetInitialized_skipsPeerRediscovery() throws Exception {
        when(mockEngine.isInitialized()).thenReturn(false);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        int newPeersCount = callDiscoverPeersFromNodeHostTable();
        assertEquals(0, newPeersCount);
        verify(mockEngine, never()).refreshClusterPeersFromNodeHost();
    }

    @Test
    void discoverPeersFromNodeHostTable_engineInitialized_rediscoversPeersAndReturnsCount() throws Exception {
        when(mockEngine.isInitialized()).thenReturn(true);
        when(mockEngine.refreshClusterPeersFromNodeHost()).thenReturn(2);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        int newPeersCount = callDiscoverPeersFromNodeHostTable();
        assertEquals(2, newPeersCount);
        verify(mockEngine).refreshClusterPeersFromNodeHost();
    }

    @Test
    void discoverPeersFromNodeHostTable_isolatesFailurePerEngine() throws Exception {
        when(mockEngine.isInitialized()).thenReturn(true);
        when(mockEngine.refreshClusterPeersFromNodeHost()).thenThrow(new RuntimeException("boom"));
        when(mockEngine2.isInitialized()).thenReturn(true);
        when(mockEngine2.refreshClusterPeersFromNodeHost()).thenReturn(1);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.registerEngine(mockEngine2, ClusteredEngineState.STARTING);
        int newPeersCount = callDiscoverPeersFromNodeHostTable();
        assertEquals(1, newPeersCount);
    }

    @Test
    void getAnyEngine_returnsRegisteredEngine_notNull() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
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
        assertTrue(manager.addPeer(PEER_1, new Date(), null));
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
        assertTrue(manager.addPeer(PEER_1, new Date(), null));
        assertFalse(exitInvoked[0]);
    }

    @Test
    void addPeer_engineWithClusteringDisabled_enforcesClusterLockingViaExitAction() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        when(mockClusterService.isClusteringEnabled()).thenReturn(false);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        // Heartbeat must be fresh (not stale) for the enforcement loop to run at all; myStartTimeMs must be later than the
        // peer's start time (derived from the heartbeat) so this node is judged "newer" => amINewer branch => exit action invoked.
        setField("myStartTimeMs", System.currentTimeMillis() + 3_600_000L);
        boolean[] exitInvoked = { false };
        setField("exitProcessAction", (Runnable) () -> exitInvoked[0] = true);
        assertTrue(manager.addPeer(PEER_1, new Date(), null));
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
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        assertTrue(callIsOwnServerId(PEER_1));
    }

    @Test
    void isOwnServerId_matchesNeither_returnsFalse() throws Exception {
        when(mockClusterService.getServerId()).thenReturn(PEER_1);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
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
    void initializeClusterCommunicationAndDiscovery_clusterLockingEnabled_startsPeerListener() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        setField("myServerId", PEER_1);
        setClusterLockingEnabled(true);
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        callInitializeClusterCommunicationAndDiscovery(mockSecurityService);
        assertEquals(PARTITION_ID, manager.getClusterPartitionId());
        assertEquals(PEER_1, manager.getServerId());
        assertTrue(manager.isClusterLockingEnabled());
        assertTrue(manager.isClusterPeerListenerStarted());
        verify(mockCoordinator).start(any(CacheCoordinatorNetworkSettings.class), eq(Collections.emptySet()), any(ClusterMessageConverter.class), any());
    }

    @Test
    void initializeClusterCommunicationAndDiscovery_clusterLockingDisabled_doesNotStartPeerListener() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        setField("myServerId", PEER_1);
        setClusterLockingEnabled(false);
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        callInitializeClusterCommunicationAndDiscovery(mockSecurityService);
        assertEquals(PARTITION_ID, manager.getClusterPartitionId());
        assertEquals(PEER_1, manager.getServerId());
        assertFalse(manager.isClusterLockingEnabled());
        assertFalse(manager.isClusterPeerListenerStarted());
        verify(mockCoordinator, never()).start(any(), any(), any(), any());
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
    void startClusterPeerListener_firstCall_startsCoordinatorAndSetsFlag() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings(PEER_1, PARTITION_ID, 1101, "db", 3000L);
        callStartClusterPeerListener(settings);
        assertTrue(manager.isClusterPeerListenerStarted());
        verify(mockCoordinator, times(1)).start(eq(settings), eq(Collections.emptySet()), any(), any());
    }

    @Test
    void startClusterPeerListener_calledAgainWhenAlreadyStarted_isSkipped() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        setField("isClusterPeerListenerStarted", true);
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings(PEER_1, PARTITION_ID, 1101, "db", 3000L);
        callStartClusterPeerListener(settings);
        verify(mockCoordinator, never()).start(any(), any(), any(), any());
    }

    @Test
    void startClusterPeerListener_coordinatorThrows_wrapsInRuntimeExceptionAndLeavesFlagFalse() throws Exception {
        setConverter(mock(ClusterMessageConverter.class));
        doThrow(new RuntimeException("boom")).when(mockCoordinator).start(any(), any(), any(), any());
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings(PEER_1, PARTITION_ID, 1101, "db", 3000L);
        assertThrows(RuntimeException.class, () -> callStartClusterPeerListener(settings));
        assertFalse(manager.isClusterPeerListenerStarted());
    }

    @Test
    void isAnyPeerInState_matchingPeer_returnsTrue() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_JOINING, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        assertTrue(manager.isAnyPeerInState(ClusterServerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    void isAnyPeerInState_noPeerMatches_returnsFalse() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        assertFalse(manager.isAnyPeerInState(ClusterServerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    void isAnyPeerInState_nullMessageForPeer_skippedGracefully() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(null);
        assertFalse(manager.isAnyPeerInState(ClusterServerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    void isAnyPeerOnline_anyPeerAlive_returnsTrue() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
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
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        backdateMessageTimestamp(msg, ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS + 1000L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        assertFalse(manager.isAnyPeerOnline());
    }

    @Test
    void isAnyPeerOnline_clusteringDisabled_returnsFalseWithoutTouchingCoordinator() throws Exception {
        setClusterLockingEnabled(false);
        setField("peerNetworkCoordinator", null);
        assertFalse(manager.isAnyPeerOnline());
    }

    @Test
    void isAnyPeerInState_clusteringDisabled_returnsFalseWithoutTouchingCoordinator() throws Exception {
        setClusterLockingEnabled(false);
        setField("peerNetworkCoordinator", null);
        assertFalse(manager.isAnyPeerInState(ClusterServerStatusMessage.EVENT_PEER_JOINING));
    }

    @Test
    void broadcastEngineState_listenerNotStarted_updatesMapButDoesNotSend() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        manager.broadcastEngineState(ENGINE_1, ClusteredEngineState.RUNNING);
        assertEquals(ClusteredEngineState.RUNNING, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1)));
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
    void startClusterHeartbeatThread_listenerNotStarted_doesNotStartThread() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        manager.startClusterHeartbeatThread();
        assertNull(getField("heartbeatThread"));
    }

    @Test
    void startClusterHeartbeatThread_alreadyRunning_doesNotStartAnotherThread() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setField("isHeartbeatLoopRunning", true);
        manager.startClusterHeartbeatThread();
        assertNull(getField("heartbeatThread"));
    }

    @Test
    void startClusterHeartbeatThread_listenerStartedAndNotRunning_startsDaemonThread() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.getObservedPeers()).thenReturn(Collections.emptySet());
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        try {
            manager.startClusterHeartbeatThread();
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
    void shutdown_listenerStarted_stopsCoordinatorWithoutSendingLeavingMessage() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setMyClusterPartitionId(PARTITION_ID);
        when(mockCoordinator.isInitialized()).thenReturn(true);
        manager.shutdown();
        verify(mockCoordinator, never()).sendServerStatus(any());
        verify(mockCoordinator).sendEngineStates(any());
        verify(mockCoordinator).stop();
        assertFalse(manager.isClusterPeerListenerStarted());
        assertNull(getField("peerNetworkCoordinator"));
    }

    @Test
    void announceLeaving_listenerStarted_sendsLeavingMessageAndOfflineEngineStates() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        setMyClusterPartitionId(PARTITION_ID);
        when(mockCoordinator.isInitialized()).thenReturn(true);
        engineAndPeerStateMap.put(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1), ClusteredEngineState.RUNNING);
        manager.announceLeaving();
        ArgumentCaptor<ClusterServerStatusMessage> statusCaptor = ArgumentCaptor.forClass(ClusterServerStatusMessage.class);
        verify(mockCoordinator).sendServerStatus(statusCaptor.capture());
        assertEquals(ClusterServerStatusMessage.EVENT_PEER_LEAVING, statusCaptor.getValue().getEventType());
        ArgumentCaptor<ClusterEngineStateMessage> engineStatesCaptor = ArgumentCaptor.forClass(ClusterEngineStateMessage.class);
        verify(mockCoordinator).sendEngineStates(engineStatesCaptor.capture());
        assertEquals(ClusteredEngineState.OFFLINE.getValue(), engineStatesCaptor.getValue().getEngineState(ENGINE_1));
    }

    @Test
    void announceLeaving_listenerNotStarted_doesNotSendMessage() throws Exception {
        setField("isClusterPeerListenerStarted", false);
        manager.announceLeaving();
        verify(mockCoordinator, never()).sendServerStatus(any());
        verify(mockCoordinator, never()).sendEngineStates(any());
    }

    @Test
    void announceLeaving_coordinatorNotInitialized_doesNotSend() throws Exception {
        setField("isClusterPeerListenerStarted", true);
        when(mockCoordinator.isInitialized()).thenReturn(false);
        assertDoesNotThrow(() -> manager.announceLeaving());
        verify(mockCoordinator, never()).sendServerStatus(any());
        verify(mockCoordinator, never()).sendEngineStates(any());
    }

    @Test
    void announceLeaving_marksOwnEngineStatesOffline() {
        engineAndPeerStateMap.put(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1), ClusteredEngineState.RUNNING);
        manager.announceLeaving();
        assertEquals(ClusteredEngineState.OFFLINE, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1)));
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
        engineAndPeerStateMap.put(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1), ClusteredEngineState.RUNNING);
        when(mockCoordinator.isInitialized()).thenReturn(false);
        manager.shutdown();
        assertEquals(ClusteredEngineState.OFFLINE, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1)));
    }

    @Test
    void shutdown_interruptsRunningHeartbeatThreadWithoutThrowing() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        Thread runningThread = new Thread(() -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
            }
        });
        runningThread.setDaemon(true);
        runningThread.start();
        setField("heartbeatThread", runningThread);
        when(mockCoordinator.isInitialized()).thenReturn(false);
        assertDoesNotThrow(() -> manager.shutdown());
        assertNull(getField("heartbeatThread"));
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
    }

    @Test
    void broadcastCurrentStateAndEngines_sendsServerStatusAndEngineStates() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        callBroadcastCurrentStateAndEngines();
        verify(mockCoordinator).sendServerStatus(any());
        verify(mockCoordinator).sendEngineStates(any());
    }

    @Test
    void invokeBuildCurrentEngineStateSnapshot_delegatesToHolderWithOwnServerId_returnsSnapshot() throws Exception {
        EngineAndPeerStateMap holderSnapshot = new EngineAndPeerStateMap();
        holderSnapshot.put(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1), ClusteredEngineState.UPGRADING);
        setField("symmetricEngineHolder", new FakeEngineHolderWithSnapshot(holderSnapshot));
        EngineAndPeerStateMap result = callInvokeBuildCurrentEngineStateSnapshot();
        assertEquals(ClusteredEngineState.UPGRADING, result.get(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1)));
    }

    @Test
    void invokeBuildCurrentEngineStateSnapshot_holderMethodThrows_propagatesException() throws Exception {
        setField("symmetricEngineHolder", new FakeEngineHolderThatThrows());
        assertThrows(Exception.class, this::callInvokeBuildCurrentEngineStateSnapshot);
    }

    @Test
    void importCurrentEngineStatesFromHolder_holderNull_leavesMapUnchanged() throws Exception {
        setField("symmetricEngineHolder", null);
        callImportCurrentEngineStatesFromHolder();
        assertTrue(engineAndPeerStateMap.isEmpty());
    }

    @Test
    void importCurrentEngineStatesFromHolder_holderPresent_mergesIntoMap() throws Exception {
        EngineAndPeerStateMap holderSnapshot = new EngineAndPeerStateMap();
        holderSnapshot.put(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1), ClusteredEngineState.UPGRADING);
        setField("symmetricEngineHolder", new FakeEngineHolderWithSnapshot(holderSnapshot));
        callImportCurrentEngineStatesFromHolder();
        assertEquals(ClusteredEngineState.UPGRADING, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1)));
    }

    @Test
    void importCurrentEngineStatesFromHolder_holderMethodThrows_doesNotThrowAndLeavesMapUnchanged() throws Exception {
        engineAndPeerStateMap.put(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1), ClusteredEngineState.RUNNING);
        setField("symmetricEngineHolder", new FakeEngineHolderThatThrows());
        assertDoesNotThrow(this::callImportCurrentEngineStatesFromHolder);
        assertEquals(ClusteredEngineState.RUNNING, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1)));
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
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1, PEER_2)));
        ClusterServerStatusMessage aliveMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(aliveMsg);
        when(mockCoordinator.getPeerStatusMessage(PEER_2)).thenReturn(null);
        Set<String> active = manager.getActiveServerIds();
        assertTrue(active.contains(PEER_1));
        assertFalse(active.contains(PEER_2));
    }

    @Test
    void getActiveServerIds_clusteringDisabled_returnsEmptySetWithoutTouchingCoordinator() throws Exception {
        setClusterLockingEnabled(false);
        setField("peerNetworkCoordinator", null);
        assertTrue(manager.getActiveServerIds().isEmpty());
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
    void isPeerOfflineLongEnough_clusteringDisabled_returnsFalseWithoutTouchingCoordinator() throws Exception {
        setClusterLockingEnabled(false);
        setField("peerNetworkCoordinator", null);
        assertFalse(manager.isPeerOfflineLongEnough(PEER_1, STALE_THRESHOLD_MS));
    }

    @Test
    void isAnyPeerWithEngineInState_matchingFreshMessages_returnsTrue() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        ClusterServerStatusMessage statusMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(statusMsg);
        ClusterEngineStateMessage engineMsg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, ENGINE_1, PEER_1, PARTITION_ID);
        when(mockCoordinator.getEngineStateMessage(PEER_1)).thenReturn(engineMsg);
        assertTrue(manager.isAnyPeerWithEngineInState(ENGINE_1, ClusteredEngineState.RUNNING));
    }

    @Test
    void isAnyPeerWithEngineInState_noMatch_returnsFalse() {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        ClusterServerStatusMessage statusMsg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(statusMsg);
        ClusterEngineStateMessage engineMsg = new ClusterEngineStateMessage(ClusteredEngineState.OFFLINE, ENGINE_1, PEER_1, PARTITION_ID);
        when(mockCoordinator.getEngineStateMessage(PEER_1)).thenReturn(engineMsg);
        assertFalse(manager.isAnyPeerWithEngineInState(ENGINE_1, ClusteredEngineState.RUNNING));
    }

    @Test
    void isAnyPeerWithEngineInState_clusteringDisabled_returnsFalseWithoutTouchingCoordinator() throws Exception {
        setClusterLockingEnabled(false);
        setField("peerNetworkCoordinator", null);
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

    @Test
    void unregisterEngine_removesFromRegisteredEnginesAndRecordsFinalStateWithoutBroadcasting() {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        assertTrue(registeredEnginesMap.containsKey(ENGINE_1));
        manager.unregisterEngine(mockEngine, ClusteredEngineState.OFFLINE);
        assertFalse(registeredEnginesMap.containsKey(ENGINE_1));
        assertEquals(ClusteredEngineState.OFFLINE, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1)));
        verify(mockCoordinator, never()).sendEngineStates(any());
    }

    @Test
    void unregisterEngine_recordsCustomFinalState() {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.unregisterEngine(mockEngine, ClusteredEngineState.FAILED);
        assertEquals(ClusteredEngineState.FAILED, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1)));
    }

    @Test
    void getObsoleteMs_nullEngine_returnsDefault() throws Exception {
        assertEquals(ServerConstants.CLUSTER_PEER_OBSOLETE_DEFAULT_MS, callGetObsoleteMs(null));
    }

    @Test
    void getObsoleteMs_engineProvided_usesParameterValue() throws Exception {
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS), anyLong())).thenReturn(999L);
        assertEquals(999L, callGetObsoleteMs(mockEngine));
    }

    @Test
    void purgeObsoletePeers_stalePeer_removedFromCoordinator() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        backdateMessageTimestamp(msg, 10_000L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        callPurgeObsoletePeers(System.currentTimeMillis(), 5_000L);
        verify(mockCoordinator).removePeer(PEER_1);
    }

    @Test
    void purgeObsoletePeers_freshPeer_notRemoved() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        callPurgeObsoletePeers(System.currentTimeMillis(), 5_000L);
        verify(mockCoordinator, never()).removePeer(anyString());
    }

    @Test
    void purgePeerStates_obsoleteOfflineEntry_isRemoved() throws Exception {
        long longAgo = System.currentTimeMillis() - 20_000L;
        peerStatesMap.put(PEER_1, new IClusteredCacheManager.PeerState(false, longAgo));
        callPurgePeerStates(System.currentTimeMillis(), 5_000L);
        assertFalse(peerStatesMap.containsKey(PEER_1));
    }

    @Test
    void purgePeerStates_recentOfflineEntry_isRetained() throws Exception {
        peerStatesMap.put(PEER_1, new IClusteredCacheManager.PeerState(false, System.currentTimeMillis()));
        callPurgePeerStates(System.currentTimeMillis(), 5_000L);
        assertTrue(peerStatesMap.containsKey(PEER_1));
    }

    @Test
    void purgePeerStates_aliveEntry_isNeverPurgedRegardlessOfAge() throws Exception {
        long longAgo = System.currentTimeMillis() - 20_000L;
        peerStatesMap.put(PEER_1, new IClusteredCacheManager.PeerState(true, longAgo));
        callPurgePeerStates(System.currentTimeMillis(), 5_000L);
        assertTrue(peerStatesMap.containsKey(PEER_1));
    }

    @Test
    void addPeer_freshHeartbeat_seedsPeerStateAlive() throws Exception {
        setConverter(new ClusterMessageConverter(mock(ISecurityService.class), PARTITION_ID));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        manager.addPeer(PEER_1, new Date(), null);
        assertTrue(peerStatesMap.get(PEER_1).alive());
    }

    @Test
    void addPeer_staleHeartbeat_seedsPeerStateOffline() throws Exception {
        setConverter(new ClusterMessageConverter(mock(ISecurityService.class), PARTITION_ID));
        setField("currentStaleThresholdMs", 5_000L);
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        manager.addPeer(PEER_1, new Date(System.currentTimeMillis() - 10_000L), null);
        assertFalse(peerStatesMap.get(PEER_1).alive());
    }

    @Test
    void detectPeerStateAndFireEvents_peerActive_updatesPeerStateAlive() throws Exception {
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        callDetectPeerState(PEER_1, msg, msg.getTimestamp() + 1, STALE_THRESHOLD_MS);
        IClusteredCacheManager.PeerState state = peerStatesMap.get(PEER_1);
        assertNotNull(state);
        assertTrue(state.alive());
    }

    @Test
    void onPeerCrashed_marksPeerStateOfflinePreservingLastAliveMs() {
        long lastAliveMs = System.currentTimeMillis() - 1000L;
        peerStatesMap.put(PEER_1, new IClusteredCacheManager.PeerState(true, lastAliveMs));
        manager.onPeerCrashed(PEER_1);
        IClusteredCacheManager.PeerState state = peerStatesMap.get(PEER_1);
        assertFalse(state.alive());
        assertEquals(lastAliveMs, state.lastAliveMs());
    }

    @Test
    void onPeerLeft_marksPeerStateOfflinePreservingLastAliveMs() {
        long lastAliveMs = System.currentTimeMillis() - 1000L;
        peerStatesMap.put(PEER_1, new IClusteredCacheManager.PeerState(true, lastAliveMs));
        manager.onPeerLeft(PEER_1);
        IClusteredCacheManager.PeerState state = peerStatesMap.get(PEER_1);
        assertFalse(state.alive());
        assertEquals(lastAliveMs, state.lastAliveMs());
    }

    @Test
    void logPeerStates_peerRemovedFromCoordinatorButRetainedInPeerStates_stillLogged() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        peerStatesMap.put(PEER_1, new IClusteredCacheManager.PeerState(false, System.currentTimeMillis()));
        assertDoesNotThrow(this::callLogPeerStates);
    }

    @Test
    void discoverPeersIncomingHeartbeats_newPeerObserved_addsAndReturnsCount() throws Exception {
        setMyClusterPartitionId(PARTITION_ID);
        setConverter(new ClusterMessageConverter(mock(ISecurityService.class), PARTITION_ID));
        when(mockCoordinator.addPeer(PEER_1)).thenReturn(true);
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getObservedPeers()).thenReturn(new HashSet<>(Arrays.asList(msg)));
        assertEquals(1, callDiscoverPeersIncomingHeartbeats());
    }

    @Test
    void discoverPeersIncomingHeartbeats_noObservedPeers_returnsZero() throws Exception {
        when(mockCoordinator.getObservedPeers()).thenReturn(Collections.emptySet());
        assertEquals(0, callDiscoverPeersIncomingHeartbeats());
    }

    @Test
    void countActivePeers_activePeer_countsOne() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, PEER_1, PARTITION_ID, 0L);
        when(mockCoordinator.getPeerStatusMessage(PEER_1)).thenReturn(msg);
        assertEquals(1, callCountActivePeers(STALE_THRESHOLD_MS));
    }

    @Test
    void countActivePeers_noPeers_returnsZero() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        assertEquals(0, callCountActivePeers(STALE_THRESHOLD_MS));
    }

    @Test
    void refreshSleepBetweenHeartbeats_noRegisteredEngines_returnsCurrentValue() throws Exception {
        setField("currentHeartbeatMs", 3000L);
        assertEquals(3000L, callRefreshSleepBetweenHeartbeats());
    }

    @Test
    void refreshSleepBetweenHeartbeats_engineChangesValue_updatesCurrentHeartbeatMs() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        setField("currentHeartbeatMs", 3000L);
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS), anyLong())).thenReturn(5000L);
        assertEquals(5000L, callRefreshSleepBetweenHeartbeats());
        assertEquals(5000L, getField("currentHeartbeatMs"));
    }

    @Test
    void refreshStaleThreshold_noRegisteredEngines_returnsCurrentValue() throws Exception {
        setField("currentStaleThresholdMs", 9000L);
        assertEquals(9000L, callRefreshStaleThreshold());
    }

    @Test
    void refreshStaleThreshold_engineChangesValue_updatesCurrentStaleThresholdMs() throws Exception {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        setField("currentStaleThresholdMs", 9000L);
        when(mockParameterService.getLong(eq(ParameterConstants.CLUSTER_PEER_STALE_MS), anyLong())).thenReturn(15000L);
        assertEquals(15000L, callRefreshStaleThreshold());
        assertEquals(15000L, getField("currentStaleThresholdMs"));
    }

    @Test
    void onPeerCrashed_marksEnginesOfflineAndClearsLocks() {
        engineAndPeerStateMap.put(EngineAndPeerStateMap.generateKey(PEER_1, ENGINE_1), ClusteredEngineState.RUNNING);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.onPeerCrashed(PEER_1);
        assertEquals(ClusteredEngineState.OFFLINE, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(PEER_1, ENGINE_1)));
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    void onPeerLeft_marksEnginesOfflineAndClearsLocks() {
        engineAndPeerStateMap.put(EngineAndPeerStateMap.generateKey(PEER_1, ENGINE_1), ClusteredEngineState.RUNNING);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.onPeerLeft(PEER_1);
        assertEquals(ClusteredEngineState.OFFLINE, engineAndPeerStateMap.get(EngineAndPeerStateMap.generateKey(PEER_1, ENGINE_1)));
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    void onPeerLeft_purgesPeerFromCoordinatorSoItsCachedMessagesDoNotLinger() {
        manager.onPeerLeft(PEER_1);
        verify(mockCoordinator).removePeer(PEER_1);
    }

    @Test
    void onPeerEngineCrashed_knownEngine_clearsLocksOnThatEngine() {
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        manager.onPeerEngineCrashed(PEER_1, ENGINE_1);
        verify(mockClusterService).clearLocksForServer(PEER_1);
        verify(mockNodeCommService).clearLocksForServer(PEER_1);
    }

    @Test
    void onPeerEngineCrashed_unknownEngine_doesNothing() {
        assertDoesNotThrow(() -> manager.onPeerEngineCrashed(PEER_1, "unknown-engine"));
        verify(mockClusterService, never()).clearLocksForServer(anyString());
    }

    @Test
    void logEngineStates_emptyMap_doesNotThrow() {
        assertDoesNotThrow(this::callLogEngineStates);
    }

    @Test
    void logEngineStates_withEntries_doesNotThrow() {
        engineAndPeerStateMap.put(EngineAndPeerStateMap.generateKey(MY_SERVER_ID, ENGINE_1), ClusteredEngineState.RUNNING);
        assertDoesNotThrow(this::callLogEngineStates);
    }

    @Test
    void logPeerStates_emptyPeers_doesNotThrow() {
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        assertDoesNotThrow(this::callLogPeerStates);
    }

    @Test
    void logPeerStates_withPeers_doesNotThrow() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(new HashSet<>(Arrays.asList(PEER_1)));
        assertDoesNotThrow(this::callLogPeerStates);
    }

    @Test
    void elapsedSince_returnsFormattedDuration() throws Exception {
        String elapsed = callElapsedSince(System.currentTimeMillis() - 50L);
        assertNotNull(elapsed);
        assertFalse(elapsed.isEmpty());
    }

    @Test
    void executeClusterHeartbeatAndDiscoveryTick_heartbeatLoopNotRunning_completesFullTickWithoutSleeping() throws Exception {
        setField("isHeartbeatLoopRunning", false);
        setField("symmetricEngineHolder", null);
        manager.registerEngine(mockEngine, ClusteredEngineState.STARTING);
        when(mockEngine.isStarted()).thenReturn(true);
        when(mockEngine.getDataService()).thenReturn(mock(IDataService.class));
        when(mockEngine.isInitialized()).thenReturn(true);
        when(mockEngine.refreshClusterPeersFromNodeHost()).thenReturn(0);
        when(mockCoordinator.getObservedPeers()).thenReturn(Collections.emptySet());
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        setMyClusterPartitionId(PARTITION_ID);
        assertDoesNotThrow(this::callExecuteClusterHeartbeatAndDiscoveryTick);
        verify(mockCoordinator).sendServerStatus(any());
        verify(mockCoordinator).sendEngineStates(any());
    }

    @Test
    void sleepUntilNextHeartbeat_processingOverdue_logsWarnAndDoesNotSleep() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        long startTime = System.currentTimeMillis() - 500L;
        Method method = ClusteredCacheManager.class.getDeclaredMethod("sleepUntilNextHeartbeat", long.class, long.class, long.class);
        method.setAccessible(true);
        long before = System.currentTimeMillis();
        method.invoke(manager, startTime, 0L, STALE_THRESHOLD_MS);
        long elapsed = System.currentTimeMillis() - before;
        assertTrue(elapsed < 200L, "should return immediately without sleeping, took " + elapsed + "ms");
    }

    @Test
    void sleepUntilNextHeartbeat_withinBudget_sleepsBriefly() throws Exception {
        when(mockCoordinator.getPeerIds()).thenReturn(Collections.emptySet());
        long startTime = System.currentTimeMillis();
        Method method = ClusteredCacheManager.class.getDeclaredMethod("sleepUntilNextHeartbeat", long.class, long.class, long.class);
        method.setAccessible(true);
        long before = System.currentTimeMillis();
        method.invoke(manager, startTime, 50L, STALE_THRESHOLD_MS);
        long elapsed = System.currentTimeMillis() - before;
        assertTrue(elapsed >= 40L, "should sleep close to the requested duration, took " + elapsed + "ms");
    }
}
