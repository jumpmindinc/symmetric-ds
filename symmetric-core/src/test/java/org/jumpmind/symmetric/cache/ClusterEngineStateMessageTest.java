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
package org.jumpmind.symmetric.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class ClusterEngineStateMessageTest {
    @Test
    void constructor_withEngineStatesMap_populatesEngineStates() {
        Map<String, String> engineStates = new HashMap<>();
        engineStates.put("engine1", ClusterEngineStateMessage.ENGINE_ONLINE);
        engineStates.put("engine2", ClusterEngineStateMessage.ENGINE_STARTING);
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(engineStates, "server1", "inst1");
        assertEquals(2, msg.getEngineStates().size());
        assertEquals(ClusterEngineStateMessage.ENGINE_ONLINE, msg.getEngineState("engine1"));
        assertEquals(ClusterEngineStateMessage.ENGINE_STARTING, msg.getEngineState("engine2"));
    }

    @Test
    void constructor_withNullEngineStatesMap_producesEmptyMap() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage((Map<String, String>) null, "server1", "inst1");
        assertTrue(msg.getEngineStates().isEmpty());
    }

    @Test
    void constructor_withEmptyEngineStatesMap_producesEmptyMap() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        assertTrue(msg.getEngineStates().isEmpty());
    }

    @Test
    void constructor_withSingleStateString_populatesSingleEngineEntry() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(ClusterEngineStateMessage.ENGINE_OFFLINE, "engine1", "server1", "inst1");
        assertEquals(1, msg.getEngineStates().size());
        assertEquals(ClusterEngineStateMessage.ENGINE_OFFLINE, msg.getEngineState("engine1"));
    }

    @Test
    void constructor_withClusteredEngineState_usesStateValue() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, "engine1", "server1", "inst1");
        assertEquals(ClusteredEngineState.RUNNING.getValue(), msg.getEngineState("engine1"));
    }

    @Test
    void getEventType_returnsEngineStatesMessageType() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        assertEquals(ClusterEngineStateMessage.MSG_TYPE_ENGINE_STATES, msg.getEventType());
    }

    @Test
    void getEngineState_missingEngine_returnsNull() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        assertNull(msg.getEngineState("nonexistent"));
    }

    @Test
    void getEngineStates_returnsUnmodifiableMap() {
        Map<String, String> engineStates = new HashMap<>();
        engineStates.put("engine1", ClusterEngineStateMessage.ENGINE_ONLINE);
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(engineStates, "server1", "inst1");
        Map<String, String> result = msg.getEngineStates();
        assertThrows(UnsupportedOperationException.class, () -> result.put("engine2", ClusterEngineStateMessage.ENGINE_OFFLINE));
    }

    @Test
    void getEngineStates_mutatingOriginalMapAfterConstruction_doesNotAffectMessage() {
        Map<String, String> engineStates = new HashMap<>();
        engineStates.put("engine1", ClusterEngineStateMessage.ENGINE_ONLINE);
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(engineStates, "server1", "inst1");
        engineStates.put("engine2", ClusterEngineStateMessage.ENGINE_OFFLINE);
        assertEquals(1, msg.getEngineStates().size());
    }

    @Test
    void getServerId_returnsConstructorValue() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        assertEquals("server1", msg.getServerId());
    }

    @Test
    void getClusterPartitionId_returnsConstructorValue() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        assertEquals("inst1", msg.getClusterPartitionId());
    }

    @Test
    void getVersion_returnsNonNullVersion() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        assertNotNull(msg.getVersion());
    }

    @Test
    void getTimestamp_isSetToApproximatelyCurrentTime() {
        long before = System.currentTimeMillis();
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        long after = System.currentTimeMillis();
        assertTrue(msg.getTimestamp() >= before && msg.getTimestamp() <= after);
    }

    @Test
    void isStale_ageExceedsThreshold_returnsTrue() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        assertTrue(msg.isStale(msg.getTimestamp() + 10_000L, 5_000L));
    }

    @Test
    void isStale_ageWithinThreshold_returnsFalse() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(new TreeMap<>(), "server1", "inst1");
        assertFalse(msg.isStale(msg.getTimestamp() + 1_000L, 5_000L));
    }

    @Test
    void fromEngineStates_extractsOnlyMatchingServerIdEntriesWithPrefixStripped() {
        EngineAndPeerStateMap allStates = new EngineAndPeerStateMap();
        allStates.put(EngineAndPeerStateMap.generateKey("server1", "engine1"), ClusteredEngineState.RUNNING);
        allStates.put(EngineAndPeerStateMap.generateKey("server2", "engine1"), ClusteredEngineState.OFFLINE);
        ClusterEngineStateMessage msg = ClusterEngineStateMessage.fromEngineStates(allStates, "server1", "inst1");
        assertEquals(1, msg.getEngineStates().size());
        assertEquals(ClusteredEngineState.RUNNING.getValue(), msg.getEngineState("engine1"));
    }

    @Test
    void fromEngineStates_noMatchingServerId_producesEmptyMessage() {
        EngineAndPeerStateMap allStates = new EngineAndPeerStateMap();
        allStates.put(EngineAndPeerStateMap.generateKey("server2", "engine1"), ClusteredEngineState.OFFLINE);
        ClusterEngineStateMessage msg = ClusterEngineStateMessage.fromEngineStates(allStates, "server1", "inst1");
        assertTrue(msg.getEngineStates().isEmpty());
    }

    @Test
    void fromEngineStates_setsServerIdAndClusterPartitionId() {
        EngineAndPeerStateMap allStates = new EngineAndPeerStateMap();
        ClusterEngineStateMessage msg = ClusterEngineStateMessage.fromEngineStates(allStates, "server1", "inst1");
        assertEquals("server1", msg.getServerId());
        assertEquals("inst1", msg.getClusterPartitionId());
    }
}
