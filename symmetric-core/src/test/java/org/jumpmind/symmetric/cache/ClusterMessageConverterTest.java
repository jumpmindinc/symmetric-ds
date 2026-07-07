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

import java.util.HashMap;
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
        when(mockSecurityService.nextSecureLong()).thenReturn(12345L);
        converter.setSecurityService(mockSecurityService);
    }

    @Test
    void toServerStatusMessage_validMessage_returnsMessage() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        assertNotNull(encrypted);
        ClusterServerStatusMessage decrypted = converter.toServerStatusMessage(encrypted, "inst1");
        assertNotNull(decrypted);
        assertEquals(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, decrypted.getEventType());
        assertEquals("server1", decrypted.getServerId());
        assertEquals("inst1", decrypted.getClusterPartitionId());
        assertEquals(1000L, decrypted.getStartTimeMs());
        assertEquals(1, converter.getSuccessfullyConverted());
    }

    @Test
    void toServerStatusMessage_nullMessage_returnsNull() {
        ClusterServerStatusMessage result = converter.toServerStatusMessage(null, "inst1");
        assertNull(result);
        assertEquals(0, converter.getSuccessfullyConverted());
    }

    @Test
    void toEngineStateMessage_validMessage_returnsMessage() {
        Map<String, String> engineStates = new HashMap<>();
        engineStates.put("engine1", "ONLINE");
        engineStates.put("engine2", "STARTING");
        ClusterEngineStateMessage plain = new ClusterEngineStateMessage(engineStates, "server1", "inst1");
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        assertNotNull(encrypted);
        ClusterEngineStateMessage decrypted = converter.toEngineStateMessage(encrypted, "inst1");
        assertNotNull(decrypted);
        assertEquals("server1", decrypted.getServerId());
        assertEquals("inst1", decrypted.getClusterPartitionId());
        assertEquals(2, decrypted.getEngineStates().size());
        assertEquals("ONLINE", decrypted.getEngineState("engine1"));
        assertEquals("STARTING", decrypted.getEngineState("engine2"));
        assertEquals(1, converter.getSuccessfullyConverted());
    }

    @Test
    void toServerStatusMessage_partitionMismatch_recordsRejection() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        ClusterServerStatusMessage result = converter.toServerStatusMessage(encrypted, "inst2");
        assertNull(result);
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertTrue(rejected.containsKey("server1"));
        assertEquals(ConversionFailureReason.PARTITION_MISMATCH, rejected.get("server1").getReason());
    }

    @Test
    void toServerStatusMessage_checksumFailure_recordsRejection() {
        ClusterPeerSecureMessage secure = mock(ClusterPeerSecureMessage.class);
        when(secure.getServerId()).thenReturn("server1");
        when(secure.getClusterPartitionId()).thenReturn("inst1");
        when(secure.isHeaderChecksumValid()).thenReturn(false);
        ClusterServerStatusMessage result = converter.toServerStatusMessage(secure, "inst1");
        assertNull(result);
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertTrue(rejected.containsKey("server1"));
        assertEquals(ConversionFailureReason.CHECKSUM, rejected.get("server1").getReason());
    }

    @Test
    void toEngineStateMessage_fingerprintFailure_recordsRejection() {
        ClusterPeerSecureMessage secure = mock(ClusterPeerSecureMessage.class);
        when(secure.getServerId()).thenReturn("server1");
        when(secure.getClusterPartitionId()).thenReturn("inst1");
        when(secure.isHeaderChecksumValid()).thenReturn(true);
        when(secure.getKeystoreFingerprint()).thenReturn("encrypted-fp");
        when(mockSecurityService.decrypt(anyString())).thenReturn("wrong-version");
        converter.setSecurityService(mockSecurityService);
        ClusterEngineStateMessage result = converter.toEngineStateMessage(secure, "inst1");
        assertNull(result);
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertTrue(rejected.containsKey("server1"));
        assertEquals(ConversionFailureReason.FINGERPRINT, rejected.get("server1").getReason());
    }

    @Test
    void rejectionInfo_tracksServerDetails() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        converter.toServerStatusMessage(encrypted, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        RejectionInfo info = rejected.get("server1");
        assertEquals("server1", info.getServerId());
        assertEquals(ConversionFailureReason.PARTITION_MISMATCH, info.getReason());
        assertTrue(info.getRejectedAtMs() > 0);
        assertNotNull(info.getLastRejectedMessage());
        String debugInfo = info.getDebugInfo();
        assertTrue(debugInfo.contains("server1"));
        assertTrue(debugInfo.contains("PARTITION_MISMATCH"));
    }

    @Test
    void encryptedMessages_withDifferentSalts_producesDifferentCiphertexts() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        when(mockSecurityService.nextSecureLong()).thenReturn(12345L);
        ClusterPeerSecureMessage msg1 = converter.toEncryptedMessage(plain);
        when(mockSecurityService.nextSecureLong()).thenReturn(54321L);
        ClusterPeerSecureMessage msg2 = converter.toEncryptedMessage(plain);
        assertNotNull(msg1);
        assertNotNull(msg2);
        assertFalse(msg1.getEncryptedPayload().equals(msg2.getEncryptedPayload()));
        assertFalse(msg1.getMessageSalt() == msg2.getMessageSalt());
    }

    @Test
    void getRejectedServers_multipleServersRejected_tracksSeparately() {
        ClusterServerStatusMessage plain1 = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterServerStatusMessage plain2 = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server2", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted1 = converter.toEncryptedMessage(plain1);
        ClusterPeerSecureMessage encrypted2 = converter.toEncryptedMessage(plain2);
        converter.toServerStatusMessage(encrypted1, "inst2");
        converter.toServerStatusMessage(encrypted2, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertEquals(2, rejected.size());
        assertTrue(rejected.containsKey("server1"));
        assertTrue(rejected.containsKey("server2"));
    }

    @Test
    void getRejectedServers_multipleRejections_tracksLatest() {
        ClusterServerStatusMessage plain1 = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterServerStatusMessage plain2 = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_JOINING, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted1 = converter.toEncryptedMessage(plain1);
        ClusterPeerSecureMessage encrypted2 = converter.toEncryptedMessage(plain2);
        converter.toServerStatusMessage(encrypted1, "inst2");
        converter.toServerStatusMessage(encrypted2, "inst2");
        Map<String, RejectionInfo> rejected = converter.getRejectedServers();
        assertEquals(1, rejected.size());
        assertTrue(rejected.containsKey("server1"));
        assertNotNull(rejected.get("server1"));
    }

    @Test
    void successfullyConverted_incrementsCounterForValidMessages() {
        assertEquals(0, converter.getSuccessfullyConverted());
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        converter.toServerStatusMessage(encrypted, "inst1");
        assertEquals(1, converter.getSuccessfullyConverted());
    }
}
