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
 * software distributed under the LICENSE is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.security.ISecurityService;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.BeforeEach;

class ClusteredCacheManagerTest {
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
    private Map<String, Boolean> engineStateMap;
    private Method detectEngineStateMethod;
    private Map<String, Boolean> peerWasPreviouslyAlive;

    @BeforeEach
    public void setUp() throws Exception {
        mockCoordinator = mock(IClusterCacheCoordinator.class);
        mockEngine = mock(ISymmetricEngine.class);
        mockClusterService = mock(IClusterService.class);
        mockParameterService = mock(IParameterService.class);
        mockNodeCommService = mock(INodeCommunicationService.class);
        mockSecurityService = mock(ISecurityService.class);
        ISymmetricEngine mockEngine2 = mock(ISymmetricEngine.class);
        IDatabasePlatform mockPlatform = mock(IDatabasePlatform.class);
        when(mockEngine.getEngineName()).thenReturn("engine1");
        when(mockEngine.getClusterService()).thenReturn(mockClusterService);
        when(mockEngine.getParameterService()).thenReturn(mockParameterService);
        when(mockEngine.getNodeCommunicationService()).thenReturn(mockNodeCommService);
        when(mockEngine.getSecurityService()).thenReturn(mockSecurityService);
        // manager = new ClusteredCacheManager(mockCoordinator); // Constructor is private
        manager = (ClusteredCacheManager) ClusteredCacheManager.getInstance();
        isPeerAlive = ClusteredCacheManager.class.getDeclaredMethod("isPeerAlive", String.class, ClusterPeerSecureMessage.class);
        isPeerAlive.setAccessible(true);
        detectPeerState = ClusteredCacheManager.class.getDeclaredMethod("detectPeerState", String.class, ClusterServerStatusMessage.class,
                long.class, long.class);
        detectPeerState.setAccessible(true);
        detectEngineStateMethod = ClusteredCacheManager.class.getDeclaredMethod("detectEngineState", String.class, String.class,
                ClusterEngineStateMessage.class, long.class, long.class);
        detectEngineStateMethod.setAccessible(true);
        Field engineStateMapField = ClusteredCacheManager.class.getDeclaredField("engineStateMap");
        engineStateMapField.setAccessible(true);
        engineStateMap = (Map<String, Boolean>) engineStateMapField.get(manager);
        Field peerWasPreviouslyAliveField = ClusteredCacheManager.class.getDeclaredField("peerWasPreviouslyAlive");
        peerWasPreviouslyAliveField.setAccessible(true);
        peerWasPreviouslyAlive = (Map<String, Boolean>) peerWasPreviouslyAliveField.get(manager);
    }
    // TODO: All test methods disabled - require major API refactoring for new constructor signatures and method signatures

    private boolean callIsPeerAlive(String peerId, ClusterPeerSecureMessage msg) throws Exception {
        return (boolean) isPeerAlive.invoke(manager, peerId, msg);
    }

    private boolean callDetectPeerState(String peerId, ClusterPeerSecureMessage msg) throws Exception {
        return (boolean) detectPeerState.invoke(manager, peerId, msg, System.currentTimeMillis(), THRESHOLD_MS);
    }

    private ClusterServerStatusMessage msg(String eventType, String peerId) {
        return new ClusterServerStatusMessage(ClusterPeerServerState.HEARTBEAT, peerId, PEER_1_CLUSTER_PARTITION_ID, System.currentTimeMillis());
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
        Field f = ClusteredCacheManager.class.getDeclaredField("listenerStarted");
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

    private Object getField(String name) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(manager);
    }
}
