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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.security.ISecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JcsTcpCacheCoordinatorTest {
    private JcsTcpCacheCoordinator coordinator;

    @BeforeEach
    void setUp() {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(mockSecurityService);
        coordinator = new JcsTcpCacheCoordinator();
    }

    @Test
    void getPeerIds_emptyByDefault() {
        assertTrue(coordinator.getPeerIds().isEmpty());
    }

    @Test
    void addPeer_addsToPeerIds() {
        assertTrue(coordinator.addPeer("server1"));
        assertTrue(coordinator.getPeerIds().contains("server1"));
        assertEquals(1, coordinator.getPeerIds().size());
    }

    @Test
    void addPeer_duplicate_notAddedTwice() {
        coordinator.addPeer("server1");
        assertFalse(coordinator.addPeer("server1"));
        assertEquals(1, coordinator.getPeerIds().size());
    }

    @Test
    void addPeer_multiplePeers_allTracked() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server2");
        coordinator.addPeer("server3");
        assertEquals(3, coordinator.getPeerIds().size());
        assertTrue(coordinator.getPeerIds().contains("server1"));
        assertTrue(coordinator.getPeerIds().contains("server2"));
        assertTrue(coordinator.getPeerIds().contains("server3"));
    }

    @Test
    void removePeer_knownPeer_removesFromPeerIds() {
        coordinator.addPeer("server1");
        assertTrue(coordinator.removePeer("server1"));
        assertTrue(coordinator.getPeerIds().isEmpty());
    }

    @Test
    void removePeer_unknownPeer_returnsFalse() {
        assertFalse(coordinator.removePeer("server1"));
    }

    @Test
    void removePeer_onlyRemovesSpecifiedPeer() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server2");
        coordinator.removePeer("server1");
        assertEquals(1, coordinator.getPeerIds().size());
        assertTrue(coordinator.getPeerIds().contains("server2"));
        assertFalse(coordinator.getPeerIds().contains("server1"));
    }

    @Test
    void getPeerStatusMessage_notStarted_returnsNull() {
        assertNull(coordinator.getPeerStatusMessage("server1"));
    }

    @Test
    void getMessage_knownRegion_notStarted_returnsNull() {
        assertNull(coordinator.getMessage("SYM_CLUSTER_PEERS", "server1"));
    }

    @Test
    void getMessage_unknownRegion_returnsNull() {
        assertNull(coordinator.getMessage("OTHER_REGION", "server1"));
    }

    @Test
    void isInitialized_beforeStart_returnsFalse() {
        assertFalse(coordinator.isInitialized());
    }

    @Test
    void getConverter_returnsNonNullConverter() {
        assertNotNull(coordinator.getConverter());
    }

    @Test
    void detectIfPeerIsStale_noHeartbeat_returnsTrue() {
        long now = System.currentTimeMillis();
        assertTrue(coordinator.detectIfPeerIsStale("server1", now));
    }

    @Test
    void getEngineStateMessage_notStarted_returnsNull() {
        assertNull(coordinator.getEngineStateMessage("server1"));
    }

    @Test
    void getEngineState_bothArgumentsNull_returnsNull() {
        assertNull(coordinator.getEngineState("server1", "engine1"));
    }

    @Test
    void addPeer_thenRemove_checksAreCorrect() {
        assertEquals(0, coordinator.getPeerIds().size());
        coordinator.addPeer("peer1");
        assertEquals(1, coordinator.getPeerIds().size());
        coordinator.removePeer("peer1");
        assertEquals(0, coordinator.getPeerIds().size());
    }
}
