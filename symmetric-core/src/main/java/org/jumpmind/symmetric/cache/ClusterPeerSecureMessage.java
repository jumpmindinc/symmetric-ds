/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the LICENSE is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.cache;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Encrypted envelope for cluster peer messages transmitted via JCS lateral TCP cache. A simple value object — all encryption/decryption and salt generation is
 * handled externally by {@link ClusterMessageConverter}.
 *
 * Plain fields (serverId, clusterPartitionId, keystoreFingerprint, version, timestamp) are visible to peers without decryption so they can route, log, perform
 * stale checks, and authenticate cluster membership efficiently. keystoreFingerprint is the sender's version string encrypted with its sym.secret AES key; a
 * receiving peer decrypts it with its own key and compares the result back to the plaintext version field to detect keystore mismatches. headerChecksum is
 * SHA-512(messageSalt|timestamp|serverId) validated before decryption to quickly reject corrupt messages. The encrypted payload and fingerprint are both salted
 * (prepended with messageSalt) before encryption to ensure no two messages encrypt identical plaintext to the same ciphertext.
 */
public final class ClusterPeerSecureMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int CURRENT_VERSION_NO = 20260611;
    private static final String CHECKSUM_ALGORITHM = "SHA-512";
    private final int versionNo;
    private final String serverId;
    private final String clusterPartitionId;
    private final String keystoreFingerprint;
    private final String version;
    private final long timestamp;
    private final long messageSalt;
    private final String headerChecksum;
    private final String encryptedPayload;

    ClusterPeerSecureMessage(String serverId, String clusterPartitionId, String version, long timestamp,
            long messageSalt, String headerChecksum, String keystoreFingerprint, String encryptedPayload) {
        this.versionNo = CURRENT_VERSION_NO;
        this.serverId = serverId;
        this.clusterPartitionId = clusterPartitionId;
        this.version = version;
        this.keystoreFingerprint = keystoreFingerprint;
        this.timestamp = timestamp;
        this.messageSalt = messageSalt;
        this.headerChecksum = headerChecksum;
        this.encryptedPayload = encryptedPayload;
    }

    static String computeChecksum(String serverId, long timestamp, long messageSalt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
            byte[] input = (messageSalt + "|" + timestamp + "|" + serverId).getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isHeaderChecksumValid() {
        return headerChecksum != null && headerChecksum.equals(computeChecksum(serverId, timestamp, messageSalt));
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getServerId() {
        return serverId;
    }

    public String getClusterPartitionId() {
        return clusterPartitionId;
    }

    public String getKeystoreFingerprint() {
        return keystoreFingerprint;
    }

    public String getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Date getTimestampAsDate() {
        return new Date(timestamp);
    }

    public String getTimestampAsString() {
        return Instant.ofEpochMilli(timestamp).toString();
    }

    public long getAgeMs(long now) {
        return now - timestamp;
    }

    public boolean isStale(long now, long staleThresholdMs) {
        return getAgeMs(now) > staleThresholdMs;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public long getMessageSalt() {
        return messageSalt;
    }
}
