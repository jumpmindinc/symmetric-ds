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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.jumpmind.security.ISecurityService;

/**
 * Base class for cluster peer messages transmitted via JCS lateral TCP cache.
 *
 * Plain fields (serverId, clusterPartitionId, keystoreFingerprint, version, timestamp) are visible to peers without decryption so they can route, log, perform
 * stale checks, and authenticate cluster membership efficiently. keystoreFingerprint is this node's own version string encrypted with its sym.secret AES key; a
 * receiving peer decrypts it with its own key via isKeystoreFingerprintValid() and compares the result back to the plaintext version field, which lets peers
 * detect a keystore mismatch even though the main encrypted payload itself would be undecryptable in that case. headerChecksum is
 * SHA-512(messageSalt|timestamp|serverId) and is validated first to quickly reject corrupt or malformed messages before decryption is attempted. All remaining
 * message content is encrypted using the shared sym.secret AES key so that rogue nodes cannot read or forge payload details.
 *
 * Subclasses define the payload structure by implementing parsePayload(String). The ensureDecrypted() template method lazily decrypts the payload on first
 * access and caches the result. Subclasses that construct a message locally (not deserialized) must call markDecrypted() in their constructor to skip the
 * unnecessary decrypt round-trip.
 */
public abstract class ClusterPeerSecureMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int CURRENT_VERSION_NO = 20260611;
    private static final String CHECKSUM_ALGORITHM = "SHA-512";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile ISecurityService securityService;
    private final int versionNo;
    private final String serverId;
    private final String clusterPartitionId;
    private final String keystoreFingerprint;
    private final String version;
    private final long timestamp;
    private final long messageSalt;
    private final String headerChecksum;
    private final String encryptedPayload;
    private transient volatile boolean payloadDecrypted;

    protected ClusterPeerSecureMessage(String serverId, String clusterPartitionId, String version, long timestamp, String plainPayload) {
        this.versionNo = CURRENT_VERSION_NO;
        this.serverId = serverId;
        this.clusterPartitionId = clusterPartitionId;
        this.version = version;
        this.keystoreFingerprint = encrypt(version);
        this.timestamp = timestamp;
        this.messageSalt = RANDOM.nextLong();
        this.headerChecksum = computeChecksum(serverId, timestamp, messageSalt);
        this.encryptedPayload = encrypt(plainPayload);
    }

    public static void setSecurityService(ISecurityService service) {
        securityService = service;
    }

    /** Called by subclass constructors to skip decryption when fields are already populated. */
    protected final void markDecrypted() {
        payloadDecrypted = true;
    }

    /** Lazily decrypts the payload on first access, delegating field population to parsePayload. */
    protected final void ensureDecrypted() {
        if (!payloadDecrypted) {
            parsePayload(decryptPayload());
            payloadDecrypted = true;
        }
    }

    /** Subclass parses the decrypted plaintext string and populates its transient cached fields. */
    protected abstract void parsePayload(String plainPayload);

    public abstract String getEventType();

    private static String computeChecksum(String serverId, long timestamp, long messageSalt) {
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

    /** Decrypts keystoreFingerprint with this node's own key and compares it to the plaintext version field to detect a cluster keystore mismatch. */
    public boolean isKeystoreFingerprintValid() {
        try {
            return version.equals(getSecurityService().decrypt(keystoreFingerprint));
        } catch (Exception e) {
            return false;
        }
    }

    private static ISecurityService getSecurityService() {
        ISecurityService svc = securityService;
        if (svc == null) {
            throw new IllegalStateException("Security service not initialized on ClusterPeerSecureMessage");
        }
        return svc;
    }

    private static String encrypt(String plaintext) {
        return getSecurityService().encrypt(plaintext);
    }

    protected String decryptPayload() {
        return getSecurityService().decrypt(encryptedPayload);
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
}
