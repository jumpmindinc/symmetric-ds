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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.cache.ClusterMessageConverter.ConversionFailureReason;
import org.jumpmind.symmetric.cache.ClusterMessageConverter.RejectionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusterMessageConverterTest {
    private ClusterMessageConverter converter;
    private ISecurityService mockSecurityService;

    @BeforeEach
    void setUp() {
        mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.nextSecureLong()).thenReturn(12345L);
        converter = new ClusterMessageConverter(mockSecurityService, "inst1");
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
    void toServerStatusMessage_preservesOriginalSecureEnvelopeTimestamp_notDecodeTime() {
        // A message decoded long after it was originally sent (e.g. sitting unchanged in a peer's local JCS cache) must keep reporting its true age --
        // otherwise every decode would "refresh" it to look perpetually fresh and stale-peer detection could never fire. Rebuild the envelope with a
        // backdated timestamp and a matching recomputed checksum (mutating the original's timestamp field directly would just fail checksum validation).
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        long backdatedTimestamp = System.currentTimeMillis() - 60_000L;
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", backdatedTimestamp, encrypted.getMessageSalt());
        ClusterPeerSecureMessage backdated = new ClusterPeerSecureMessage("server1", "inst1", encrypted.getVersion(),
                backdatedTimestamp, encrypted.getMessageSalt(), headerChecksum, encrypted.getKeystoreFingerprint(), encrypted.getEncryptedPayload());
        ClusterServerStatusMessage decoded = converter.toServerStatusMessage(backdated, "inst1");
        assertNotNull(decoded);
        assertEquals(backdatedTimestamp, decoded.getTimestamp());
    }

    @Test
    void toEngineStateMessage_preservesOriginalSecureEnvelopeTimestamp_notDecodeTime() {
        Map<String, String> engineStates = new HashMap<>();
        engineStates.put("engine1", "ONLINE");
        ClusterEngineStateMessage plain = new ClusterEngineStateMessage(engineStates, "server1", "inst1");
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        long backdatedTimestamp = System.currentTimeMillis() - 60_000L;
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", backdatedTimestamp, encrypted.getMessageSalt());
        ClusterPeerSecureMessage backdated = new ClusterPeerSecureMessage("server1", "inst1", encrypted.getVersion(),
                backdatedTimestamp, encrypted.getMessageSalt(), headerChecksum, encrypted.getKeystoreFingerprint(), encrypted.getEncryptedPayload());
        ClusterEngineStateMessage decoded = converter.toEngineStateMessage(backdated, "inst1");
        assertNotNull(decoded);
        assertEquals(backdatedTimestamp, decoded.getTimestamp());
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
        assertNotEquals(msg1.getMessageSalt(), msg2.getMessageSalt());
    }

    @Test
    void toEncryptedMessage_fingerprintSeededFromClusterPartitionIdAndSoftwareVersion() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        String decryptedFingerprint = mockSecurityService.decrypt(encrypted.getKeystoreFingerprint());
        assertTrue(decryptedFingerprint.endsWith("inst1" + Version.version()));
    }

    @Test
    void toEncryptedMessage_fingerprintStaysConsistentOnceSeededDespiteLaterPartitionIdChange() {
        ClusterServerStatusMessage plain1 = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterServerStatusMessage plain2 = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst2", 1000L);
        ClusterPeerSecureMessage encrypted1 = converter.toEncryptedMessage(plain1);
        ClusterPeerSecureMessage encrypted2 = converter.toEncryptedMessage(plain2);
        String fingerprint1 = mockSecurityService.decrypt(encrypted1.getKeystoreFingerprint());
        String fingerprint2 = mockSecurityService.decrypt(encrypted2.getKeystoreFingerprint());
        assertEquals(fingerprint1, fingerprint2);
    }

    @Test
    void toServerStatusMessage_fingerprintFromDifferentClusterPartitionId_isRejected() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        ClusterMessageConverter otherPartitionConverter = new ClusterMessageConverter(mockSecurityService, "inst2");
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", encrypted.getTimestamp(), encrypted.getMessageSalt());
        ClusterPeerSecureMessage forgedPartition = new ClusterPeerSecureMessage("server1", "inst2", encrypted.getVersion(),
                encrypted.getTimestamp(), encrypted.getMessageSalt(), headerChecksum,
                encrypted.getKeystoreFingerprint(), encrypted.getEncryptedPayload());
        ClusterServerStatusMessage result = otherPartitionConverter.toServerStatusMessage(forgedPartition, "inst2");
        assertNull(result);
        assertEquals(ConversionFailureReason.FINGERPRINT,
                otherPartitionConverter.getRejectedServers().get("server1").getReason());
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

    private boolean invokeIsFromAuthorizedPartition(String messagePartitionId, String expectedPartitionId) throws Exception {
        Method m = ClusterMessageConverter.class.getDeclaredMethod("isFromAuthorizedPartition", String.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(converter, messagePartitionId, expectedPartitionId);
    }

    @Test
    void isFromAuthorizedPartition_nullMessagePartitionId_returnsFalse() throws Exception {
        assertFalse(invokeIsFromAuthorizedPartition(null, "inst1"));
    }

    @Test
    void isFromAuthorizedPartition_nullExpectedPartitionId_returnsFalse() throws Exception {
        assertFalse(invokeIsFromAuthorizedPartition("inst1", null));
    }

    @Test
    void isFromAuthorizedPartition_mismatchedPartitionIds_returnsFalse() throws Exception {
        assertFalse(invokeIsFromAuthorizedPartition("inst1", "inst2"));
    }

    @Test
    void isFromAuthorizedPartition_matchingPartitionIds_returnsTrue() throws Exception {
        assertTrue(invokeIsFromAuthorizedPartition("inst1", "inst1"));
    }

    @Test
    void getRejectedPartitionIdMismatch_incrementsOnPartitionMismatchOnly() {
        assertEquals(0, converter.getRejectedPartitionIdMismatch());
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        converter.toServerStatusMessage(encrypted, "inst2");
        assertEquals(1, converter.getRejectedPartitionIdMismatch());
        assertEquals(0, converter.getRejectedFingerprintFailure());
    }

    @Test
    void getRejectedFingerprintFailure_incrementsOnFingerprintMismatchOnly() {
        assertEquals(0, converter.getRejectedFingerprintFailure());
        ClusterPeerSecureMessage secure = mock(ClusterPeerSecureMessage.class);
        when(secure.getServerId()).thenReturn("server1");
        when(secure.getClusterPartitionId()).thenReturn("inst1");
        when(secure.isHeaderChecksumValid()).thenReturn(true);
        when(secure.getKeystoreFingerprint()).thenReturn("encrypted-fp");
        when(secure.getVersion()).thenReturn("real-version");
        when(mockSecurityService.decrypt("encrypted-fp")).thenReturn("wrong-version");
        ClusterServerStatusMessage result = converter.toServerStatusMessage(secure, "inst1");
        assertNull(result);
        assertEquals(1, converter.getRejectedFingerprintFailure());
        assertEquals(0, converter.getRejectedPartitionIdMismatch());
    }

    @Test
    void getTotalRejected_sumsPartitionMismatchAndFingerprintFailures() {
        assertEquals(0, converter.getTotalRejected());
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encryptedMismatch = converter.toEncryptedMessage(plain);
        converter.toServerStatusMessage(encryptedMismatch, "inst2");
        ClusterPeerSecureMessage secureFingerprintFail = mock(ClusterPeerSecureMessage.class);
        when(secureFingerprintFail.getServerId()).thenReturn("server2");
        when(secureFingerprintFail.getClusterPartitionId()).thenReturn("inst1");
        when(secureFingerprintFail.isHeaderChecksumValid()).thenReturn(true);
        when(secureFingerprintFail.getKeystoreFingerprint()).thenReturn("encrypted-fp");
        when(secureFingerprintFail.getVersion()).thenReturn("real-version");
        when(mockSecurityService.decrypt("encrypted-fp")).thenReturn("wrong-version");
        converter.toServerStatusMessage(secureFingerprintFail, "inst1");
        assertEquals(1, converter.getRejectedPartitionIdMismatch());
        assertEquals(1, converter.getRejectedFingerprintFailure());
        assertEquals(2, converter.getTotalRejected());
    }

    @Test
    void getTotalRejected_checksumFailureIsNotCounted() {
        ClusterPeerSecureMessage secure = mock(ClusterPeerSecureMessage.class);
        when(secure.getServerId()).thenReturn("server1");
        when(secure.isHeaderChecksumValid()).thenReturn(false);
        converter.toServerStatusMessage(secure, "inst1");
        assertEquals(0, converter.getTotalRejected());
    }

    @Test
    void logMetrics_doesNotThrow() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        converter.toEncryptedMessage(plain);
        assertDoesNotThrow(() -> converter.logMetrics());
    }

    private String invokeSalt(long messageSalt, String value) throws Exception {
        Method m = ClusterMessageConverter.class.getDeclaredMethod("salt", long.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, messageSalt, value);
    }

    private String invokeUnsalt(String saltedValue) throws Throwable {
        Method m = ClusterMessageConverter.class.getDeclaredMethod("unsalt", String.class);
        m.setAccessible(true);
        try {
            return (String) m.invoke(null, saltedValue);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void salt_producesSixteenHexCharPrefixFollowedByDelimiterAndValue() throws Exception {
        assertEquals("00000000000000ff|payload", invokeSalt(255L, "payload"));
    }

    @Test
    void salt_negativeMessageSalt_producesFixedLengthSixteenHexDigits() throws Exception {
        String salted = invokeSalt(-1L, "payload");
        assertEquals("ffffffffffffffff|payload", salted);
        assertEquals('|', salted.charAt(16));
    }

    @Test
    void salt_zeroMessageSalt_isZeroPadded() throws Exception {
        assertEquals("0000000000000000|payload", invokeSalt(0L, "payload"));
    }

    @Test
    void unsalt_stripsFixedLengthPrefixAndDelimiter() throws Throwable {
        assertEquals("payload", invokeUnsalt("00000000000000ff|payload"));
    }

    @Test
    void unsalt_roundTripsWithValueContainingDelimiter() throws Throwable {
        String salted = invokeSalt(12345L, "eventType|1000");
        assertEquals("eventType|1000", invokeUnsalt(salted));
    }

    @Test
    void unsalt_missingDelimiterAtExpectedPosition_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> invokeUnsalt("00000000000000ffXpayload"));
    }

    @Test
    void unsalt_tooShortToContainPrefix_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> invokeUnsalt("short"));
    }

    @Test
    void toServerStatusMessage_corruptedDecryptedPayload_isCaughtAndRecordedAsCorruptedPayload() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", encrypted.getTimestamp(), encrypted.getMessageSalt());
        ClusterPeerSecureMessage corrupted = new ClusterPeerSecureMessage("server1", "inst1", encrypted.getVersion(),
                encrypted.getTimestamp(), encrypted.getMessageSalt(), headerChecksum,
                encrypted.getKeystoreFingerprint(), "not-a-valid-salted-payload");
        ClusterServerStatusMessage result = converter.toServerStatusMessage(corrupted, "inst1");
        assertNull(result);
        assertEquals(ConversionFailureReason.CORRUPTED_PAYLOAD, converter.getRejectedServers().get("server1").getReason());
    }

    @Test
    void toServerStatusMessage_corruptedPayloadForOnePeer_stillProcessesSubsequentPeer() {
        ClusterServerStatusMessage plain1 = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted1 = converter.toEncryptedMessage(plain1);
        String headerChecksum1 = ClusterPeerSecureMessage.computeChecksum("server1", encrypted1.getTimestamp(), encrypted1.getMessageSalt());
        ClusterPeerSecureMessage corrupted = new ClusterPeerSecureMessage("server1", "inst1", encrypted1.getVersion(),
                encrypted1.getTimestamp(), encrypted1.getMessageSalt(), headerChecksum1,
                encrypted1.getKeystoreFingerprint(), "not-a-valid-salted-payload");
        ClusterServerStatusMessage plain2 = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server2", "inst1", 2000L);
        ClusterPeerSecureMessage encrypted2 = converter.toEncryptedMessage(plain2);
        assertNull(converter.toServerStatusMessage(corrupted, "inst1"));
        ClusterServerStatusMessage result2 = converter.toServerStatusMessage(encrypted2, "inst1");
        assertNotNull(result2);
        assertEquals("server2", result2.getServerId());
        assertEquals(1, converter.getSuccessfullyConverted());
    }

    @Test
    void toServerStatusMessage_corruptedFingerprint_isCaughtAndRecordedAsFingerprintFailure() {
        ClusterPeerSecureMessage secure = mock(ClusterPeerSecureMessage.class);
        when(secure.getServerId()).thenReturn("server1");
        when(secure.getClusterPartitionId()).thenReturn("inst1");
        when(secure.isHeaderChecksumValid()).thenReturn(true);
        when(secure.getKeystoreFingerprint()).thenReturn("encrypted-fp");
        when(mockSecurityService.decrypt("encrypted-fp")).thenReturn("not-a-valid-salted-fingerprint");
        ClusterServerStatusMessage result = converter.toServerStatusMessage(secure, "inst1");
        assertNull(result);
        assertEquals(ConversionFailureReason.FINGERPRINT, converter.getRejectedServers().get("server1").getReason());
    }

    @Test
    void toEngineStateMessage_corruptedDecryptedPayload_isCaughtAndRecordedAsCorruptedPayload() {
        Map<String, String> engineStates = new HashMap<>();
        engineStates.put("engine1", "ONLINE");
        ClusterEngineStateMessage plain = new ClusterEngineStateMessage(engineStates, "server1", "inst1");
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", encrypted.getTimestamp(), encrypted.getMessageSalt());
        ClusterPeerSecureMessage corrupted = new ClusterPeerSecureMessage("server1", "inst1", encrypted.getVersion(),
                encrypted.getTimestamp(), encrypted.getMessageSalt(), headerChecksum,
                encrypted.getKeystoreFingerprint(), "not-a-valid-salted-payload");
        ClusterEngineStateMessage result = converter.toEngineStateMessage(corrupted, "inst1");
        assertNull(result);
        assertEquals(ConversionFailureReason.CORRUPTED_PAYLOAD, converter.getRejectedServers().get("server1").getReason());
    }

    @Test
    @SuppressWarnings("deprecation")
    void toPlainMessage_delegatesToToServerStatusMessage() {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(
                ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage encrypted = converter.toEncryptedMessage(plain);
        ClusterPlainMessage decoded = converter.toPlainMessage(encrypted, "inst1");
        assertNotNull(decoded);
        assertEquals(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, decoded.getEventType());
    }

    @Test
    void toEngineStateMessage_payloadWithMalformedPair_skipsPairWithoutEquals() {
        long messageSalt = 98765L;
        long timestamp = System.currentTimeMillis();
        String rawPayload = "no-equals-sign;engine1=ONLINE";
        String saltedPayload = String.format("%016x|%s", messageSalt, rawPayload);
        String saltedFingerprint = String.format("%016x|%s", messageSalt, "inst1" + Version.version());
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum("server1", timestamp, messageSalt);
        ClusterPeerSecureMessage secure = new ClusterPeerSecureMessage("server1", "inst1", "1.0", timestamp,
                messageSalt, headerChecksum, saltedFingerprint, saltedPayload);
        ClusterEngineStateMessage decrypted = converter.toEngineStateMessage(secure, "inst1");
        assertNotNull(decrypted);
        assertEquals(1, decrypted.getEngineStates().size());
        assertEquals("ONLINE", decrypted.getEngineState("engine1"));
    }
}
