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

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.jumpmind.security.ISecurityService;

/**
 * Base class for cluster peer messages transmitted via JCS lateral TCP cache.
 *
 * Plain fields (serverId, version, timestamp) are visible to peers without decryption so they can route, log, and perform stale checks efficiently.
 * headerChecksum is SHA-256(messageSalt|timestamp|serverId) and is validated first to quickly reject corrupt or malformed messages before decryption is
 * attempted. All remaining message content is encrypted using the shared sym.secret AES key so that rogue nodes cannot read or forge payload details.
 */
public abstract class ClusterPeerSecureMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int FORMAT = 20260611;

    public enum Type {
        PEER_JOINING, PEER_HEARTBEAT, PEER_LEAVING
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile ISecurityService securityService;
    private final int format;
    private final String serverId;
    private final String version;
    private final long timestamp;
    private final long messageSalt;
    private final String headerChecksum;
    private final String encryptedPayload;

    protected ClusterPeerSecureMessage(String serverId, String version, long timestamp, String plainPayload) {
        this.format = FORMAT;
        this.serverId = serverId;
        this.version = version;
        this.timestamp = timestamp;
        this.messageSalt = RANDOM.nextLong();
        this.headerChecksum = computeChecksum(serverId, timestamp, messageSalt);
        this.encryptedPayload = encrypt(plainPayload);
    }

    public static void setSecurityService(ISecurityService service) {
        securityService = service;
    }

    private static String computeChecksum(String serverId, long timestamp, long messageSalt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = (messageSalt + "|" + timestamp + "|" + serverId).getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isHeaderChecksumValid() {
        return headerChecksum != null && headerChecksum.equals(computeChecksum(serverId, timestamp, messageSalt));
    }

    private static String encrypt(String plaintext) {
        ISecurityService svc = securityService;
        if (svc == null) {
            throw new IllegalStateException("Security service not initialized on ClusterPeerSecureMessage");
        }
        return svc.encrypt(plaintext);
    }

    protected String decryptPayload() {
        ISecurityService svc = securityService;
        if (svc == null) {
            throw new IllegalStateException("Security service not initialized on ClusterPeerSecureMessage");
        }
        return svc.decrypt(encryptedPayload);
    }

    public int getFormat() {
        return format;
    }

    public String getServerId() {
        return serverId;
    }

    public String getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTimestampAsDate() {
        return Instant.ofEpochMilli(timestamp).toString();
    }

    public boolean isStale(long now, long staleThresholdMs) {
        return now - timestamp > staleThresholdMs;
    }
}
