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

public enum ClusterPeerServerState {
    INITIALIZING("PEER_SERVER_INITIALIZING"), HEARTBEAT("PEER_SERVER_HEARTBEAT"), LEAVING("PEER_SERVER_LEAVING"), OFFLINE("PEER_SERVER_OFFLINE"), DISCOVERING(
            "PEER_SERVER_DISCOVERING");

    private final String value;

    ClusterPeerServerState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ClusterPeerServerState fromValue(String value) {
        for (ClusterPeerServerState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown peer server state: " + value);
    }
}
