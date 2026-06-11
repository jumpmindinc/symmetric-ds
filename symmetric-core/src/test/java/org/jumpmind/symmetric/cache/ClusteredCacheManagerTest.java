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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusteredCacheManagerTest {
    private static final long THRESHOLD_MS = 9000L;
    private ClusteredCacheManager manager;
    private Method isPeerAlive;
    private Method detectPeerState;
    private Map<String, Boolean> peerStateMap;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        Constructor<ClusteredCacheManager> ctor = ClusteredCacheManager.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        manager = ctor.newInstance();
        isPeerAlive = ClusteredCacheManager.class.getDeclaredMethod(
                "isPeerAlive", String.class, ClusterPeerStateMessage.class, long.class, long.class);
        isPeerAlive.setAccessible(true);
        detectPeerState = ClusteredCacheManager.class.getDeclaredMethod(
                "detectPeerStateAndFireEvents", String.class, ClusterPeerStateMessage.class, long.class, long.class);
        detectPeerState.setAccessible(true);
        Field field = ClusteredCacheManager.class.getDeclaredField("peerStateMap");
        field.setAccessible(true);
        peerStateMap = (Map<String, Boolean>) field.get(manager);
    }

    private boolean callIsPeerAlive(String peerId, ClusterPeerStateMessage msg) throws Exception {
        return (boolean) isPeerAlive.invoke(manager, peerId, msg, System.currentTimeMillis(), THRESHOLD_MS);
    }

    private boolean callDetectPeerState(String peerId, ClusterPeerStateMessage msg) throws Exception {
        return (boolean) detectPeerState.invoke(manager, peerId, msg, System.currentTimeMillis(), THRESHOLD_MS);
    }

    private ClusterPeerStateMessage msg(ClusterPeerStateMessage.Type type, String peerId) {
        return new ClusterPeerStateMessage(type, peerId, "inst-" + peerId, "1.0");
    }
    // --- isPeerAlive ---

    @Test
    public void isPeerAlive_nullMessage_returnsFalse() throws Exception {
        assertFalse(callIsPeerAlive("peer1", null));
    }

    @Test
    public void isPeerAlive_peerLeaving_returnsFalse() throws Exception {
        assertFalse(callIsPeerAlive("peer1", msg(ClusterPeerStateMessage.Type.PEER_LEAVING, "peer1")));
    }

    @Test
    public void isPeerAlive_staleHeartbeat_returnsFalse() throws Exception {
        ClusterPeerStateMessage stale = msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1");
        long farFuture = System.currentTimeMillis() + THRESHOLD_MS + 1000L;
        assertFalse((boolean) isPeerAlive.invoke(manager, "peer1", stale, farFuture, THRESHOLD_MS));
    }

    @Test
    public void isPeerAlive_freshHeartbeat_returnsTrue() throws Exception {
        assertTrue(callIsPeerAlive("peer1", msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1")));
    }

    @Test
    public void isPeerAlive_peerJoining_returnsTrue() throws Exception {
        assertTrue(callIsPeerAlive("peer1", msg(ClusterPeerStateMessage.Type.PEER_JOINING, "peer1")));
    }
    // --- detectPeerStateAndFireEvents ---

    @Test
    public void detectPeerState_firstHeartbeat_peerMarkedAlive() throws Exception {
        boolean isActive = callDetectPeerState("peer1", msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1"));
        assertTrue(isActive);
        assertTrue(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_consecutiveHeartbeats_staysAlive() throws Exception {
        ClusterPeerStateMessage hb = msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1");
        callDetectPeerState("peer1", hb);
        assertTrue((boolean) callDetectPeerState("peer1", hb));
        assertTrue(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_peerJoining_peerMarkedAlive() throws Exception {
        boolean isActive = callDetectPeerState("peer1", msg(ClusterPeerStateMessage.Type.PEER_JOINING, "peer1"));
        assertTrue(isActive);
        assertTrue(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_nullMessageAfterAlive_peerMarkedCrashed() throws Exception {
        callDetectPeerState("peer1", msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1"));
        boolean isActive = callDetectPeerState("peer1", null);
        assertFalse(isActive);
        assertFalse(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_staleMessageAfterAlive_peerMarkedCrashed() throws Exception {
        callDetectPeerState("peer1", msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1"));
        ClusterPeerStateMessage stale = msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1");
        long farFuture = System.currentTimeMillis() + THRESHOLD_MS + 1000L;
        boolean isActive = (boolean) detectPeerState.invoke(manager, "peer1", stale, farFuture, THRESHOLD_MS);
        assertFalse(isActive);
        assertFalse(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_peerLeavingAfterAlive_removedFromStateMap() throws Exception {
        callDetectPeerState("peer1", msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1"));
        boolean isActive = callDetectPeerState("peer1", msg(ClusterPeerStateMessage.Type.PEER_LEAVING, "peer1"));
        assertFalse(isActive);
        assertNull(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_nullMessageNeverAlive_noStateRecorded() throws Exception {
        boolean isActive = callDetectPeerState("peer1", null);
        assertFalse(isActive);
        assertNull(peerStateMap.get("peer1"));
    }

    @Test
    public void detectPeerState_crashedPeerRejoins_markedAliveAgain() throws Exception {
        callDetectPeerState("peer1", msg(ClusterPeerStateMessage.Type.PEER_HEARTBEAT, "peer1"));
        callDetectPeerState("peer1", null);
        assertFalse(peerStateMap.get("peer1"));
        boolean isActive = callDetectPeerState("peer1", msg(ClusterPeerStateMessage.Type.PEER_JOINING, "peer1"));
        assertTrue(isActive);
        assertTrue(peerStateMap.get("peer1"));
    }
}
