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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * All-engine cluster state message transmitted via the SYM_CLUSTER_ENGINES JCS region, consolidating states for all engines in a peer into a single message
 * stored in one JCS cache slot (keyed by peerId only). Decoupled from JVM-level peer status so that multiple engines sharing a JVM can each report independent
 * states (e.g. engine A upgrading while engine B is online). Message is re-transmitted by heartbeat and occupies only one JCS slot per peer regardless of
 * engine count.
 */
public class ClusterEngineStateMessage extends ClusterPlainMessage {
    private static final long serialVersionUID = 1L;
    public static final String MSG_TYPE_ENGINE_STATES = "ENGINE_STATES";
    public static final String ENGINE_STARTING = "ENGINE_STARTING";
    public static final String ENGINE_UPGRADING_DB = "ENGINE_UPGRADING_DB";
    public static final String ENGINE_ONLINE = "ENGINE_ONLINE";
    public static final String ENGINE_OFFLINE = "ENGINE_OFFLINE";
    private transient Map<String, String> engineStates;

    public ClusterEngineStateMessage(Map<String, String> allEngineStates,
            String serverId, String clusterPartitionId) {
        this(allEngineStates, serverId, clusterPartitionId, System.currentTimeMillis());
    }

    /**
     * A constructor overload taking {@code Map<String, ClusteredEngineState>} would have the same erasure as the {@code Map<String, String>} constructor above,
     * so this is a factory method instead. {@code allEngineStates} is expected to be keyed by {@link EngineAndPeerStateMap#generateKey} across potentially many
     * peers (e.g. ClusteredCacheManager's unified engine/peer state map); only the entries belonging to {@code serverId} are extracted into this message, with
     * the peer prefix stripped back down to a plain engine name.
     */
    public static ClusterEngineStateMessage fromEngineStates(EngineAndPeerStateMap allEngineStates,
            String serverId, String clusterPartitionId) {
        String searchKey = serverId + EngineAndPeerStateMap.ENGINE_PEER_KEY_SEPARATOR;
        Map<String, String> stringStates = new HashMap<>();
        for (Map.Entry<String, ClusteredEngineState> entry : allEngineStates.entrySet()) {
            if (entry.getKey().startsWith(searchKey)) {
                stringStates.put(entry.getKey().substring(searchKey.length()), entry.getValue().getValue());
            }
        }
        return new ClusterEngineStateMessage(stringStates, serverId, clusterPartitionId);
    }

    /**
     * Used by ClusterMessageConverter when reconstructing a message from a received/cached secure envelope, passing the envelope's own timestamp so staleness
     * reflects when the peer actually sent it, not when this JVM happened to decode it.
     */
    ClusterEngineStateMessage(Map<String, String> allEngineStates, String serverId, String clusterPartitionId, long timestamp) {
        super(serverId, clusterPartitionId, timestamp);
        this.engineStates = allEngineStates != null ? new TreeMap<>(allEngineStates) : new TreeMap<>();
    }

    public ClusterEngineStateMessage(ClusteredEngineState state, String engineName,
            String serverId, String clusterPartitionId) {
        this(state.getValue(), engineName, serverId, clusterPartitionId);
    }

    public ClusterEngineStateMessage(String state, String engineName,
            String serverId, String clusterPartitionId) {
        super(serverId, clusterPartitionId, System.currentTimeMillis());
        this.engineStates = new TreeMap<>();
        this.engineStates.put(engineName, state);
    }

    @Override
    public String getEventType() {
        return MSG_TYPE_ENGINE_STATES;
    }

    public Map<String, String> getEngineStates() {
        return Collections.unmodifiableMap(engineStates);
    }

    public String getEngineState(String name) {
        return engineStates.get(name);
    }
}
