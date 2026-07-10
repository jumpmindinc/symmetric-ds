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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusteredCacheManagerTest {
    private static final long STALE_THRESHOLD_MS = 9000L;
    private static final String PEER_1 = "peer1";
    private static final String PARTITION_ID = "cluster1";
    private static final String ENGINE_1 = "engine1";
    private static final String ENGINE_2 = "engine2";
    private ClusteredCacheManager manager;
    private ISymmetricEngine mockEngine;
    private ISymmetricEngine mockEngine2;
    private IClusterService mockClusterService;
    private INodeCommunicationService mockNodeCommService;
    private IParameterService mockParameterService;
    private Method isPeerAliveMethod;
    private Method detectPeerStateMethod;
    private Method detectEngineStateMethod;
    private Map<String, Boolean> engineStateMap;
    private Map<String, Boolean> peerWasPreviouslyAlive;
    private Map<String, ISymmetricEngine> registeredEnginesMap;

    @BeforeEach
    void setUp() throws Exception {
        mockEngine = mock(ISymmetricEngine.class);
        mockEngine2 = mock(ISymmetricEngine.class);
        mockClusterService = mock(IClusterService.class);
        mockNodeCommService = mock(INodeCommunicationService.class);
        mockParameterService = mock(IParameterService.class);
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
        Field engineStateMapField = ClusteredCacheManager.class.getDeclaredField("engineStateMap");
        engineStateMapField.setAccessible(true);
        engineStateMap = (Map<String, Boolean>) engineStateMapField.get(manager);
        Field peerWasPreviouslyAliveField = ClusteredCacheManager.class.getDeclaredField("peerWasPreviouslyAlive");
        peerWasPreviouslyAliveField.setAccessible(true);
        peerWasPreviouslyAlive = (Map<String, Boolean>) peerWasPreviouslyAliveField.get(manager);
        Field registeredEnginesField = ClusteredCacheManager.class.getDeclaredField("registeredEngines");
        registeredEnginesField.setAccessible(true);
        registeredEnginesMap = (Map<String, ISymmetricEngine>) registeredEnginesField.get(manager);
    }

    @AfterEach
    void tearDown() {
        // ClusteredCacheManager.getInstance() is a JVM-wide singleton, so state from one test must not leak into the next.
        registeredEnginesMap.clear();
        engineStateMap.clear();
        peerWasPreviouslyAlive.clear();
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
}
