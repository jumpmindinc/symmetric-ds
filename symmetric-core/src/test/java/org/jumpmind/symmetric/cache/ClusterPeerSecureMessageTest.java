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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ClusterPeerSecureMessageTest {
    @Test
    void getVersionNo_returnsExpectedConstant() {
        long messageSalt = 98765L;
        long timestamp = System.currentTimeMillis();
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, "fingerprint", "payload");
        assertEquals(20260611, msg.getVersionNo());
    }

    @Test
    void isHeaderChecksumValid_freshMessage_returnsTrue() {
        long messageSalt = 98765L;
        long timestamp = System.currentTimeMillis();
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, "fingerprint", "payload");
        assertTrue(msg.isHeaderChecksumValid());
    }

    @Test
    void isHeaderChecksumValid_tamperedChecksum_returnsFalse() throws Exception {
        long messageSalt = 98765L;
        long timestamp = System.currentTimeMillis();
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, "fingerprint", "payload");
        Field f = ClusterPeerSecureMessage.class.getDeclaredField("headerChecksum");
        f.setAccessible(true);
        f.set(msg, "tampered-checksum");
        assertFalse(msg.isHeaderChecksumValid());
    }

    @Test
    void isHeaderChecksumValid_nullHeaderChecksum_returnsFalse() {
        long messageSalt = 98765L;
        long timestamp = System.currentTimeMillis();
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, null, "fingerprint", "payload");
        assertFalse(msg.isHeaderChecksumValid());
    }

    @Test
    void computeChecksum_algorithmUnavailable_throwsRuntimeException() throws Exception {
        try (MockedStatic<MessageDigest> mocked = mockStatic(MessageDigest.class)) {
            mocked.when(() -> MessageDigest.getInstance("SHA-512")).thenThrow(new NoSuchAlgorithmException("no such algorithm"));
            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> ClusterPeerSecureMessage.computeChecksum("server1", System.currentTimeMillis(), 98765L));
            assertEquals(NoSuchAlgorithmException.class, e.getCause().getClass());
        }
    }

    @Test
    void getTimestamp_returnsConstructedValue() {
        long messageSalt = 98765L;
        long timestamp = System.currentTimeMillis();
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, "fingerprint", "payload");
        assertEquals(timestamp, msg.getTimestamp());
    }

    @Test
    void getTimestampAsDate_returnsDateMatchingTimestamp() {
        long messageSalt = 98765L;
        long timestamp = System.currentTimeMillis();
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, "fingerprint", "payload");
        assertEquals(new Date(timestamp), msg.getTimestampAsDate());
    }

    @Test
    void getAgeMs_returnsDifferenceBetweenNowAndTimestamp() {
        long messageSalt = 98765L;
        long timestamp = 1000L;
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, "fingerprint", "payload");
        assertEquals(1500L, msg.getAgeMs(2500L));
    }

    @Test
    void isStale_ageExceedsThreshold_returnsTrue() {
        long messageSalt = 98765L;
        long timestamp = 1000L;
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, "fingerprint", "payload");
        assertTrue(msg.isStale(5000L, 1000L));
    }

    @Test
    void isStale_ageWithinThreshold_returnsFalse() {
        long messageSalt = 98765L;
        long timestamp = 1000L;
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage msg = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, "fingerprint", "payload");
        assertFalse(msg.isStale(1500L, 1000L));
    }
}
