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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.cache.ClusterMessageConverter.ConversionFailureReason;
import org.jumpmind.symmetric.cache.ClusterMessageConverter.RejectionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusterMessageConverterTest {
    private ClusterMessageConverter converter;
    private ISecurityService mockSecurityService;

    @BeforeEach
    void setUp() {
        converter = new ClusterMessageConverter();
        mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(mockSecurityService);
    }

    @Test
    void toPlainMessage_validMessage_returnsPlainMessage() {
        ClusterPeerStatusMessage secure = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        ClusterPlainMessage result = converter.toPlainMessage(secure, "inst1");
        assertNotNull(result);
        assertEquals(1, converter.getSuccessfullyConverted());
    }

    @Test
    void toPlainMessage_nullMessage_returnsNull() {
        ClusterPlainMessage result = converter.toPlainMessage(null, "inst1");
        assertNull(result);
        assertEquals(0, converter.getSuccessfullyConverted());
    }

    @Test
    void recordRejection_tracksRejectionInfo() {
        ClusterPeerStatusMessage secure = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        converter.toPlainMessage(secure, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertTrue(rejected.containsKey("server1"));
    }

    @Test
    void recordRejection_multipleRejections_tracksLatest() {
        ClusterPeerStatusMessage secure1 = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        ClusterPeerStatusMessage secure2 = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_JOINING, "server1", "inst1", "1.0");
        converter.toPlainMessage(secure1, "inst2");
        converter.toPlainMessage(secure2, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertEquals(1, rejected.size());
        RejectionInfo info = rejected.get("server1");
        assertNotNull(info);
    }

    @Test
    void getRejectedServers_returnsCorrectMap() {
        ClusterPeerStatusMessage secure = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        converter.toPlainMessage(secure, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertEquals(1, rejected.size());
        assertTrue(rejected.containsKey("server1"));
    }

    @Test
    void toPlainMessage_partitionMismatch_recordsRejection() {
        ClusterPeerStatusMessage secure = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        converter.toPlainMessage(secure, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        RejectionInfo info = rejected.get("server1");
        assertEquals(ConversionFailureReason.PARTITION_MISMATCH, info.getReason());
    }

    @Test
    void toPlainMessage_fingerprintFailure_recordsRejection() {
        ClusterPeerStatusMessage secure = mock(ClusterPeerStatusMessage.class);
        when(secure.getServerId()).thenReturn("server1");
        when(secure.getClusterPartitionId()).thenReturn("inst1");
        when(secure.isHeaderChecksumValid()).thenReturn(true);
        when(secure.isKeystoreFingerprintValid()).thenReturn(false);
        ClusterPlainMessage result = converter.toPlainMessage(secure, "inst1");
        assertNull(result);
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        RejectionInfo info = rejected.get("server1");
        assertEquals(ConversionFailureReason.FINGERPRINT, info.getReason());
    }

    @Test
    void toPlainMessage_checksumFailure_recordsRejection() {
        ClusterPeerStatusMessage secure = mock(ClusterPeerStatusMessage.class);
        when(secure.getServerId()).thenReturn("server1");
        when(secure.getClusterPartitionId()).thenReturn("inst1");
        when(secure.isHeaderChecksumValid()).thenReturn(false);
        ClusterPlainMessage result = converter.toPlainMessage(secure, "inst1");
        assertNull(result);
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        RejectionInfo info = rejected.get("server1");
        assertEquals(ConversionFailureReason.CHECKSUM, info.getReason());
    }

    @Test
    void rejectionInfo_containsFullMessageDetails() {
        ClusterPeerStatusMessage secure = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        converter.toPlainMessage(secure, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        RejectionInfo info = rejected.get("server1");
        assertEquals("server1", info.getServerId());
        assertEquals(ConversionFailureReason.PARTITION_MISMATCH, info.getReason());
        assertTrue(info.getRejectedAtMs() > 0);
        assertNotNull(info.getLastRejectedMessage());
    }

    @Test
    void rejectionInfo_getDebugInfo_includesAllDetails() {
        ClusterPeerStatusMessage secure = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        converter.toPlainMessage(secure, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        RejectionInfo info = rejected.get("server1");
        String debugInfo = info.getDebugInfo();
        assertTrue(debugInfo.contains("server1"));
        assertTrue(debugInfo.contains("PARTITION_MISMATCH"));
    }

    @Test
    void toPlainMessage_multipleServersRejected_tracksSeparately() {
        ClusterPeerStatusMessage secure1 = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        ClusterPeerStatusMessage secure2 = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server2", "inst1", "1.0");
        converter.toPlainMessage(secure1, "inst2");
        converter.toPlainMessage(secure2, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertEquals(2, rejected.size());
        assertTrue(rejected.containsKey("server1"));
        assertTrue(rejected.containsKey("server2"));
    }

    @Test
    void toPlainMessage_successfullyConverted_incrementsCounter() {
        ClusterPeerStatusMessage secure = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        assertEquals(0, converter.getSuccessfullyConverted());
        converter.toPlainMessage(secure, "inst1");
        assertEquals(1, converter.getSuccessfullyConverted());
    }

    @Test
    void getTotalRejected_returnsCorrectSum() {
        ClusterPeerStatusMessage secure1 = mock(ClusterPeerStatusMessage.class);
        when(secure1.getServerId()).thenReturn("server1");
        when(secure1.getClusterPartitionId()).thenReturn("inst1");
        when(secure1.isHeaderChecksumValid()).thenReturn(false);
        ClusterPeerStatusMessage secure2 = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server2", "inst1", "1.0");
        converter.toPlainMessage(secure1, "inst1");
        converter.toPlainMessage(secure2, "inst2");
        assertEquals(1, converter.getRejectedPartitionIdMismatch());
    }

    @Test
    void rejectionInfo_nullMessage_handledGracefully() {
        ClusterPeerStatusMessage secure = mock(ClusterPeerStatusMessage.class);
        when(secure.getServerId()).thenReturn("server1");
        when(secure.getClusterPartitionId()).thenReturn("inst1");
        when(secure.isHeaderChecksumValid()).thenReturn(true);
        when(secure.isKeystoreFingerprintValid()).thenReturn(false);
        converter.toPlainMessage(secure, "inst1");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        RejectionInfo info = rejected.get("server1");
        assertNotNull(info.getDebugInfo());
        assertFalse(info.getDebugInfo().isEmpty());
    }
}
