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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine state tracked across both this JVM's own engines and remote cluster peers' engines, keyed by {@link #generateKey(String, String)}.
 */
public class EngineAndPeerStateMap extends ConcurrentHashMap<String, ClusteredEngineState> {
    private static final long serialVersionUID = 1L;
    public static final String ENGINE_PEER_KEY_SEPARATOR = "/";

    public static String generateKey(String serverId, String engineName) {
        return serverId + ENGINE_PEER_KEY_SEPARATOR + engineName;
    }

    /** Resets every engine tracked under {@code serverId} (own or peer) to {@code newState}. */
    public void setStateForAllEnginesAtServer(String serverId, ClusteredEngineState newState) {
        String prefix = serverId + ENGINE_PEER_KEY_SEPARATOR;
        for (String key : keySet()) {
            if (key.startsWith(prefix)) {
                put(key, newState);
            }
        }
    }

    /** Imports/updates every entry from {@code other}, overwriting this map's existing state for any key {@code other} also tracks. */
    public void importStatesFrom(EngineAndPeerStateMap other) {
        for (Map.Entry<String, ClusteredEngineState> entry : other.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
}
