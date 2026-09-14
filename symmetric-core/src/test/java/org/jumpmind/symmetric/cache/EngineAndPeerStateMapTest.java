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

import org.junit.jupiter.api.Test;

class EngineAndPeerStateMapTest {
    @Test
    void generateKey_combinesServerIdAndEngineNameWithSeparator() {
        assertEquals("server1/engine1", EngineAndPeerStateMap.generateKey("server1", "engine1"));
    }

    @Test
    void setStateForAllEnginesAtServer_matchingKeys_areUpdated() {
        EngineAndPeerStateMap map = new EngineAndPeerStateMap();
        map.put(EngineAndPeerStateMap.generateKey("server1", "engine1"), ClusteredEngineState.RUNNING);
        map.put(EngineAndPeerStateMap.generateKey("server1", "engine2"), ClusteredEngineState.RUNNING);
        map.setStateForAllEnginesAtServer("server1", ClusteredEngineState.OFFLINE);
        assertEquals(ClusteredEngineState.OFFLINE, map.get(EngineAndPeerStateMap.generateKey("server1", "engine1")));
        assertEquals(ClusteredEngineState.OFFLINE, map.get(EngineAndPeerStateMap.generateKey("server1", "engine2")));
    }

    @Test
    void setStateForAllEnginesAtServer_nonMatchingKeys_areLeftUnchanged() {
        EngineAndPeerStateMap map = new EngineAndPeerStateMap();
        map.put(EngineAndPeerStateMap.generateKey("server1", "engine1"), ClusteredEngineState.RUNNING);
        map.put(EngineAndPeerStateMap.generateKey("server2", "engine1"), ClusteredEngineState.RUNNING);
        map.setStateForAllEnginesAtServer("server1", ClusteredEngineState.OFFLINE);
        assertEquals(ClusteredEngineState.OFFLINE, map.get(EngineAndPeerStateMap.generateKey("server1", "engine1")));
        assertEquals(ClusteredEngineState.RUNNING, map.get(EngineAndPeerStateMap.generateKey("server2", "engine1")));
    }

    @Test
    void importStatesFrom_copiesAndOverwritesEntries() {
        EngineAndPeerStateMap target = new EngineAndPeerStateMap();
        target.put(EngineAndPeerStateMap.generateKey("server1", "engine1"), ClusteredEngineState.RUNNING);
        EngineAndPeerStateMap source = new EngineAndPeerStateMap();
        source.put(EngineAndPeerStateMap.generateKey("server1", "engine1"), ClusteredEngineState.OFFLINE);
        source.put(EngineAndPeerStateMap.generateKey("server2", "engine1"), ClusteredEngineState.STARTING);
        target.importStatesFrom(source);
        assertEquals(ClusteredEngineState.OFFLINE, target.get(EngineAndPeerStateMap.generateKey("server1", "engine1")));
        assertEquals(ClusteredEngineState.STARTING, target.get(EngineAndPeerStateMap.generateKey("server2", "engine1")));
    }
}
