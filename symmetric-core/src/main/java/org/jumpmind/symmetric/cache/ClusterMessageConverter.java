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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterMessageConverter {
    private static final Logger log = LoggerFactory.getLogger(ClusterMessageConverter.class);
    private final AtomicLong successfullyConverted = new AtomicLong(0);
    private final AtomicLong rejectedPartitionIdMismatch = new AtomicLong(0);
    private final AtomicLong rejectedFingerprintFailure = new AtomicLong(0);
    private final Map<String, RejectionInfo> rejectedServers = new ConcurrentHashMap<>();

    public ClusterPlainMessage toPlainMessage(ClusterPeerSecureMessage secure, String expectedPartitionId) {
        if (secure == null) {
            log.debug("Received null secure message, skipping conversion");
            return null;
        }
        if (!secure.isHeaderChecksumValid()) {
            recordRejection(secure, ConversionFailureReason.CHECKSUM);
            RejectionInfo rejection = rejectedServers.get(secure.getServerId());
            log.debug("Message header checksum invalid, rejecting message. {}", rejection != null ? rejection.getDebugInfo()
                    : "serverId=" + secure.getServerId());
            return null;
        }
        String messagePartitionId = secure.getClusterPartitionId();
        if (!isFromAuthorizedPartition(messagePartitionId, expectedPartitionId)) {
            rejectedPartitionIdMismatch.incrementAndGet();
            recordRejection(secure, ConversionFailureReason.PARTITION_MISMATCH);
            RejectionInfo rejection = rejectedServers.get(secure.getServerId());
            log.debug("Message rejected due to partition ID mismatch. {}, ExpectedPartitionId={}, MessagePartitionId={}",
                    rejection != null ? rejection.getDebugInfo() : "serverId=" + secure.getServerId(), expectedPartitionId, messagePartitionId);
            return null;
        }
        if (!secure.isKeystoreFingerprintValid()) {
            rejectedFingerprintFailure.incrementAndGet();
            recordRejection(secure, ConversionFailureReason.FINGERPRINT);
            RejectionInfo rejection = rejectedServers.get(secure.getServerId());
            log.debug("Message rejected due to keystore fingerprint validation failure. {}", rejection != null ? rejection.getDebugInfo()
                    : "serverId=" + secure.getServerId());
            return null;
        }
        ClusterPlainMessage plainMessage = createPlainMessageFromSecure(secure);
        if (plainMessage != null) {
            successfullyConverted.incrementAndGet();
            log.debug("Successfully converted secure message to plain message. MessageType={}, ServerId={}, ClusterPartitionId={}, Timestamp={}",
                    plainMessage.getClass().getSimpleName(), secure.getServerId(), secure.getClusterPartitionId(), secure.getTimestampAsString());
        }
        return plainMessage;
    }

    private ClusterPlainMessage createPlainMessageFromSecure(ClusterPeerSecureMessage secure) {
        String eventType = secure.getEventType();
        if (ClusterEngineStateMessage.MSG_TYPE_ENGINE_STATES.equals(eventType)) {
            if (secure instanceof ClusterEngineStateMessage) {
                ClusterEngineStateMessage engineMsg = (ClusterEngineStateMessage) secure;
                return new ClusterEngineStateMessage(engineMsg.getEngineStates(), secure.getServerId(),
                        secure.getClusterPartitionId());
            }
            return null;
        } else {
            if (secure instanceof ClusterServerStatusMessage) {
                ClusterServerStatusMessage statusMsg = (ClusterServerStatusMessage) secure;
                return new ClusterServerStatusMessage(eventType, secure.getServerId(), secure.getClusterPartitionId(),
                        statusMsg.getStartTimeMs());
            } else {
                return new ClusterServerStatusMessage(eventType, secure.getServerId(), secure.getClusterPartitionId(),
                        System.currentTimeMillis());
            }
        }
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
