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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.security.ISecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusterPeerStatusMessageTest {
    private static final long THRESHOLD_MS = 9000L;

    @BeforeEach
    public void setUp() {
        ISecurityService securityService = mock(ISecurityService.class);
        when(securityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(securityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(securityService);
    }

    private ClusterPeerStatusMessage heartbeat() {
        return new ClusterPeerStatusMessage(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
    }

    @Test
    public void eventTypeConstants_areNonNull() {
        assertNotNull(ClusterPeerStatusMessage.EVENT_PEER_JOINING);
        assertNotNull(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT);
        assertNotNull(ClusterPeerStatusMessage.EVENT_PEER_LEAVING);
    }

    @Test
    public void getEventType_returnsConstructedType() {
        assertEquals(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, heartbeat().getEventType());
    }

    @Test
    public void getInstanceId_returnsConstructedInstance() {
        assertEquals("inst1", heartbeat().getInstanceId());
    }

    @Test
    public void isStaleReturnsTrueWhenAgeExceedsThreshold() {
        ClusterPeerStatusMessage msg = heartbeat();
        long now = System.currentTimeMillis() + THRESHOLD_MS + 1;
        assertTrue(msg.isStale(now, THRESHOLD_MS));
    }

    @Test
    public void isStaleReturnsFalseWhenAgeEqualsThreshold() {
        ClusterPeerStatusMessage msg = heartbeat();
        long now = msg.getTimestamp() + THRESHOLD_MS;
        assertFalse(msg.isStale(now, THRESHOLD_MS));
    }

    @Test
    public void isStaleReturnsFalseWhenFresh() {
        assertFalse(heartbeat().isStale(System.currentTimeMillis(), THRESHOLD_MS));
    }

    @Test
    public void getTimestampAsDateReturnsNonEmptyString() {
        String date = heartbeat().getTimestampAsDate();
        assertNotNull(date);
        assertFalse(date.isEmpty());
    }
}
