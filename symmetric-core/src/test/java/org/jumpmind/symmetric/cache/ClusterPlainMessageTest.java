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

import java.util.Date;

import org.junit.jupiter.api.Test;

class ClusterPlainMessageTest {
    private static final class TestMessage extends ClusterPlainMessage {
        private static final long serialVersionUID = 1L;

        TestMessage(String serverId, String clusterPartitionId, long timestamp) {
            super(serverId, clusterPartitionId, timestamp);
        }

        @Override
        public String getEventType() {
            return "TEST_EVENT";
        }
    }

    @Test
    void getServerId_returnsConstructedServerId() {
        assertEquals("server1", new TestMessage("server1", "inst1", 0L).getServerId());
    }

    @Test
    void getClusterPartitionId_returnsConstructedClusterPartitionId() {
        assertEquals("inst1", new TestMessage("server1", "inst1", 0L).getClusterPartitionId());
    }

    @Test
    void getVersion_returnsNonBlankVersion() {
        String version = new TestMessage("server1", "inst1", 0L).getVersion();
        assertNotNull(version);
        assertFalse(version.isEmpty());
    }

    @Test
    void getTimestamp_returnsConstructedTimestamp() {
        assertEquals(12345L, new TestMessage("server1", "inst1", 12345L).getTimestamp());
    }

    @Test
    void getTimestampAsDate_returnsDateMatchingTimestamp() {
        assertEquals(new Date(12345L), new TestMessage("server1", "inst1", 12345L).getTimestampAsDate());
    }

    @Test
    void getAgeMs_returnsDifferenceBetweenNowAndTimestamp() {
        assertEquals(1500L, new TestMessage("server1", "inst1", 1000L).getAgeMs(2500L));
    }

    @Test
    void isStale_ageExceedsThreshold_returnsTrue() {
        assertTrue(new TestMessage("server1", "inst1", 1000L).isStale(6001L, 5000L));
    }

    @Test
    void isStale_ageWithinThreshold_returnsFalse() {
        assertFalse(new TestMessage("server1", "inst1", 1000L).isStale(5999L, 5000L));
    }
}
