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

import java.util.concurrent.atomic.AtomicLong;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterMessageConverter {
    private static final Logger log = LoggerFactory.getLogger(ClusterMessageConverter.class);
    private static final String SALT_DELIMITER = "|";
    private final AtomicLong successfullyConverted = new AtomicLong(0);
    private final AtomicLong rejectedPartitionIdMismatch = new AtomicLong(0);
    private final AtomicLong rejectedFingerprintFailure = new AtomicLong(0);
    private final Map<String, RejectionInfo> rejectedServers = new ConcurrentHashMap<>();
    private final ISecurityService securityService;
    private final String serverFingerprint;

    public ClusterMessageConverter(ISecurityService securityService, String clusterPartitionId) {
        this.securityService = securityService;
        this.serverFingerprint = clusterPartitionId + Version.version();
    }

    public ClusterPeerSecureMessage toEncryptedMessage(ClusterServerStatusMessage plain) {
        String payload = plain.getEventType() + "|" + plain.getStartTimeMs();
        return buildEncryptedMessage(plain.getServerId(), plain.getClusterPartitionId(), plain.getVersion(),
                plain.getTimestamp(), payload);
    }

    public ClusterPeerSecureMessage toEncryptedMessage(ClusterEngineStateMessage plain) {
        String payload = serializeEngineStates(plain.getEngineStates());
        return buildEncryptedMessage(plain.getServerId(), plain.getClusterPartitionId(), plain.getVersion(),
                plain.getTimestamp(), payload);
    }

    private ClusterPeerSecureMessage buildEncryptedMessage(String serverId, String clusterPartitionId, String version,
            long timestamp, String plainPayload) {
        long messageSalt = securityService.nextSecureLong();
        String headerChecksum = ClusterPeerSecureMessage.computeChecksum(serverId, timestamp, messageSalt);
        String keystoreFingerprint = securityService.encrypt(salt(messageSalt, serverFingerprint));
        String encryptedPayload = securityService.encrypt(salt(messageSalt, plainPayload));
        return new ClusterPeerSecureMessage(serverId, clusterPartitionId, version, timestamp, messageSalt,
                headerChecksum, keystoreFingerprint, encryptedPayload);
    }

    public ClusterServerStatusMessage toServerStatusMessage(ClusterPeerSecureMessage secure, String expectedPartitionId) {
        if (!isValid(secure, expectedPartitionId)) {
            return null;
        }
        String plainPayload = unsalt(securityService.decrypt(secure.getEncryptedPayload()));
        String[] parts = plainPayload.split("\\|", 2);
        long startTimeMs = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(parts[0], secure.getServerId(),
                secure.getClusterPartitionId(), startTimeMs);
        successfullyConverted.incrementAndGet();
        log.debug("Successfully converted secure message to ClusterServerStatusMessage. EventType={}, ServerId={}, ClusterPartitionId={}, Timestamp={}",
                plain.getEventType(), secure.getServerId(), secure.getClusterPartitionId(), secure.getTimestampAsString());
        return plain;
    }

    public ClusterEngineStateMessage toEngineStateMessage(ClusterPeerSecureMessage secure, String expectedPartitionId) {
        if (!isValid(secure, expectedPartitionId)) {
            return null;
        }
        String plainPayload = unsalt(securityService.decrypt(secure.getEncryptedPayload()));
        Map<String, String> engineStates = parseEngineStates(plainPayload);
        ClusterEngineStateMessage plain = new ClusterEngineStateMessage(engineStates, secure.getServerId(),
                secure.getClusterPartitionId());
        successfullyConverted.incrementAndGet();
        log.debug("Successfully converted secure message to ClusterEngineStateMessage. EngineStatesCount={}, ServerId={}, ClusterPartitionId={}, Timestamp={}",
                engineStates.size(), secure.getServerId(), secure.getClusterPartitionId(), secure.getTimestampAsString());
        return plain;
    }

    private boolean isValid(ClusterPeerSecureMessage secure, String expectedPartitionId) {
        if (secure == null) {
            log.debug("Received null secure message, skipping conversion");
            return false;
        }
        if (!secure.isHeaderChecksumValid()) {
            recordRejection(secure, ConversionFailureReason.CHECKSUM);
            RejectionInfo rejection = rejectedServers.get(secure.getServerId());
            log.warn("Message header checksum invalid, rejecting message. {}", rejection != null ? rejection.getDebugInfo()
                    : "serverId=" + secure.getServerId());
            return false;
        }
        String messagePartitionId = secure.getClusterPartitionId();
        if (!isFromAuthorizedPartition(messagePartitionId, expectedPartitionId)) {
            rejectedPartitionIdMismatch.incrementAndGet();
            recordRejection(secure, ConversionFailureReason.PARTITION_MISMATCH);
            RejectionInfo rejection = rejectedServers.get(secure.getServerId());
            log.debug("Message rejected due to partition ID mismatch. {}, ExpectedPartitionId={}, MessagePartitionId={}",
                    rejection != null ? rejection.getDebugInfo() : "serverId=" + secure.getServerId(), expectedPartitionId, messagePartitionId);
            return false;
        }
        if (!isKeystoreFingerprintValid(secure)) {
            rejectedFingerprintFailure.incrementAndGet();
            recordRejection(secure, ConversionFailureReason.FINGERPRINT);
            RejectionInfo rejection = rejectedServers.get(secure.getServerId());
            log.debug("Message rejected due to keystore fingerprint validation failure. {}", rejection != null ? rejection.getDebugInfo()
                    : "serverId=" + secure.getServerId());
            return false;
        }
        return true;
    }

    private boolean isKeystoreFingerprintValid(ClusterPeerSecureMessage secure) {
        try {
            String decryptedFingerprint = securityService.decrypt(secure.getKeystoreFingerprint());
            return serverFingerprint.equals(unsalt(decryptedFingerprint));
        } catch (Exception e) {
            return false;
        }
    }

    private static String salt(long messageSalt, String value) {
        return messageSalt + SALT_DELIMITER + value;
    }

    private static String unsalt(String saltedValue) {
        int delimiterIndex = saltedValue.indexOf(SALT_DELIMITER);
        return delimiterIndex >= 0 ? saltedValue.substring(delimiterIndex + 1) : saltedValue;
    }

    private String serializeEngineStates(Map<String, String> engineStates) {
        if (engineStates == null || engineStates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : engineStates.entrySet()) {
            if (!first) {
                sb.append(";");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    private Map<String, String> parseEngineStates(String payload) {
        Map<String, String> engineStates = new TreeMap<>();
        if (payload == null || payload.isEmpty()) {
            return engineStates;
        }
        String[] pairs = payload.split(";");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0) {
                String name = pair.substring(0, eqIndex);
                String state = pair.substring(eqIndex + 1);
                engineStates.put(name, state);
            }
        }
        return engineStates;
    }

    @Deprecated
    public ClusterPlainMessage toPlainMessage(ClusterPeerSecureMessage secure, String expectedPartitionId) {
        return toServerStatusMessage(secure, expectedPartitionId);
    }

    private boolean isFromAuthorizedPartition(String messagePartitionId, String expectedPartitionId) {
        if (messagePartitionId == null || expectedPartitionId == null) {
            log.debug("Cannot validate partition ID: messagePartitionId={}, expectedPartitionId={}", messagePartitionId, expectedPartitionId);
            return false;
        }
        return messagePartitionId.equals(expectedPartitionId);
    }

    public long getSuccessfullyConverted() {
        return successfullyConverted.get();
    }

    public long getRejectedPartitionIdMismatch() {
        return rejectedPartitionIdMismatch.get();
    }

    public long getRejectedFingerprintFailure() {
        return rejectedFingerprintFailure.get();
    }

    public long getTotalRejected() {
        return rejectedPartitionIdMismatch.get() + rejectedFingerprintFailure.get();
    }

    public void logMetrics() {
        log.debug("ClusterMessageConverter metrics: SuccessfullyConverted={}, RejectedPartitionIdMismatch={}, RejectedFingerprintFailure={}, TotalRejected={}",
                successfullyConverted.get(), rejectedPartitionIdMismatch.get(), rejectedFingerprintFailure.get(), getTotalRejected());
    }

    private void recordRejection(ClusterPeerSecureMessage message, ConversionFailureReason reason) {
        if (message == null) {
            return;
        }
        String serverId = message.getServerId();
        rejectedServers.put(serverId, new RejectionInfo(serverId, reason, System.currentTimeMillis(), message));
    }

    public Map<String, RejectionInfo> getRejectedServers() {
        return rejectedServers;
    }

    public enum ConversionFailureReason {
        PARTITION_MISMATCH, FINGERPRINT, CHECKSUM
    }

    public static class RejectionInfo {
        private final String serverId;
        private final ConversionFailureReason reason;
        private final long rejectedAtMs;
        private final ClusterPeerSecureMessage lastRejectedMessage;

        public RejectionInfo(String serverId, ConversionFailureReason reason, long rejectedAtMs,
                ClusterPeerSecureMessage lastRejectedMessage) {
            this.serverId = serverId;
            this.reason = reason;
            this.rejectedAtMs = rejectedAtMs;
            this.lastRejectedMessage = lastRejectedMessage;
        }

        public String getServerId() {
            return serverId;
        }

        public ConversionFailureReason getReason() {
            return reason;
        }

        public long getRejectedAtMs() {
            return rejectedAtMs;
        }

        public ClusterPeerSecureMessage getLastRejectedMessage() {
            return lastRejectedMessage;
        }

        public String getDebugInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append("serverId=").append(serverId);
            sb.append(", reason=").append(reason);
            sb.append(", rejectedAtMs=").append(rejectedAtMs);
            if (lastRejectedMessage != null) {
                sb.append(", messageTimestamp=").append(lastRejectedMessage.getTimestampAsString());
                sb.append(", messageVersion=").append(lastRejectedMessage.getVersion());
            }
            return sb.toString();
        }
    }
}
