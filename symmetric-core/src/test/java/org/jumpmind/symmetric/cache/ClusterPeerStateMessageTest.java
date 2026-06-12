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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

public class ClusterPeerStateMessageTest {
    private static final long THRESHOLD_MS = 9000L;

    private ClusterPeerStateMessage heartbeat() {
        return new ClusterPeerStateMessage(ClusterPeerSecureMessage.EventType.PEER_HEARTBEAT, "server1", "inst1", "1.0");
    }

    @Test
    public void isStaleReturnsTrueWhenAgeExceedsThreshold() {
        ClusterPeerStateMessage msg = heartbeat();
        long now = System.currentTimeMillis() + THRESHOLD_MS + 1;
        assertTrue(msg.isStale(now, THRESHOLD_MS));
    }

    @Test
    public void isStaleReturnsFalseWhenAgeEqualsThreshold() {
        ClusterPeerStateMessage msg = heartbeat();
        long now = msg.getTimestamp() + THRESHOLD_MS;
        assertFalse(msg.isStale(now, THRESHOLD_MS));
    }

    @Test
    public void isStaleReturnsFalseWhenFresh() {
        ClusterPeerStateMessage msg = heartbeat();
        assertFalse(msg.isStale(System.currentTimeMillis(), THRESHOLD_MS));
    }

    @Test
    public void getTimestampAsDateReturnsNonEmptyString() {
        ClusterPeerStateMessage msg = heartbeat();
        String date = msg.getTimestampAsDate();
        assertNotNull(date);
        assertFalse(date.isEmpty());
    }
}
