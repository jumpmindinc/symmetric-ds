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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.security.ISecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusterServerStatusMessageTest {
    private static final long THRESHOLD_MS = 9000L;

    @BeforeEach
    void setUp() {
        ISecurityService securityService = mock(ISecurityService.class);
        when(securityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(securityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(securityService);
    }

    private ClusterServerStatusMessage heartbeat() {
        return new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 0L);
    }

    @Test
    void eventTypeConstants_areNonNull() {
        assertNotNull(ClusterServerStatusMessage.EVENT_PEER_JOINING);
        assertNotNull(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT);
        assertNotNull(ClusterServerStatusMessage.EVENT_PEER_LEAVING);
    }

    @Test
    void getEventType_returnsConstructedType() {
        assertEquals(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, heartbeat().getEventType());
    }

    @Test
    void getClusterPartitionId_returnsConstructedClusterPartitionId() {
        assertEquals("inst1", heartbeat().getClusterPartitionId());
    }

    @Test
    void isStaleReturnsTrueWhenAgeExceedsThreshold() {
        ClusterServerStatusMessage msg = heartbeat();
        long now = System.currentTimeMillis() + THRESHOLD_MS + 1;
        assertTrue(msg.isStale(now, THRESHOLD_MS));
    }

    @Test
    void isStaleReturnsFalseWhenAgeEqualsThreshold() {
        ClusterServerStatusMessage msg = heartbeat();
        long now = msg.getTimestamp() + THRESHOLD_MS;
        assertFalse(msg.isStale(now, THRESHOLD_MS));
    }

    @Test
    void isStaleReturnsFalseWhenFresh() {
        assertFalse(heartbeat().isStale(System.currentTimeMillis(), THRESHOLD_MS));
    }

    @Test
    void getTimestampAsStringReturnsNonEmptyString() {
        String date = heartbeat().getTimestampAsString();
        assertNotNull(date);
        assertFalse(date.isEmpty());
    }
}
