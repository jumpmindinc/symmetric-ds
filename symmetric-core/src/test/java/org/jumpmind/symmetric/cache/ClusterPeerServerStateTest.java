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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClusterPeerServerStateTest {
    @ParameterizedTest
    @EnumSource(ClusterPeerServerState.class)
    void fromValue_eachStatesOwnValue_returnsSameState(ClusterPeerServerState state) {
        assertSame(state, ClusterPeerServerState.fromValue(state.getValue()));
    }

    @Test
    void getValue_initializing_returnsExpectedString() {
        assertEquals("PEER_SERVER_INITIALIZING", ClusterPeerServerState.INITIALIZING.getValue());
    }

    @Test
    void getValue_heartbeat_returnsExpectedString() {
        assertEquals("PEER_SERVER_HEARTBEAT", ClusterPeerServerState.HEARTBEAT.getValue());
    }

    @Test
    void getValue_leaving_returnsExpectedString() {
        assertEquals("PEER_SERVER_LEAVING", ClusterPeerServerState.LEAVING.getValue());
    }

    @Test
    void getValue_offline_returnsExpectedString() {
        assertEquals("PEER_SERVER_OFFLINE", ClusterPeerServerState.OFFLINE.getValue());
    }

    @Test
    void getValue_discovering_returnsExpectedString() {
        assertEquals("PEER_SERVER_DISCOVERING", ClusterPeerServerState.DISCOVERING.getValue());
    }

    @Test
    void fromValue_unknownValue_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ClusterPeerServerState.fromValue("NOT_A_REAL_STATE"));
        assertEquals("Unknown peer server state: NOT_A_REAL_STATE", ex.getMessage());
    }

    @Test
    void fromValue_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ClusterPeerServerState.fromValue(null));
    }
}
