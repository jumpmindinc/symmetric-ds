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
package org.jumpmind.symmetric.staging.spi;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class LockObjectFormat {
    public static final String DELIMITER = "|";

    private LockObjectFormat() {
    }

    public static byte[] encode(String hostname, String symVersion, long acquiredAtMs) {
        Objects.requireNonNull(hostname, "hostname");
        Objects.requireNonNull(symVersion, "symVersion");
        if (hostname.contains(DELIMITER) || symVersion.contains(DELIMITER)) {
            throw new IllegalArgumentException("Lock body fields must not contain delimiter '|'");
        }
        String body = hostname + DELIMITER + symVersion + DELIMITER + acquiredAtMs;
        return body.getBytes(StandardCharsets.UTF_8);
    }

    public static LockOwner decode(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        String content = new String(body, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            long acquiredAtMs = Long.parseLong(parts[2]);
            return new LockOwner(parts[0], parts[1], acquiredAtMs);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static final class LockOwner {
        private final String hostname;
        private final String symVersion;
        private final long acquiredAtMs;

        public LockOwner(String hostname, String symVersion, long acquiredAtMs) {
            this.hostname = hostname;
            this.symVersion = symVersion;
            this.acquiredAtMs = acquiredAtMs;
        }

        public String getHostname() {
            return hostname;
        }

        public String getSymVersion() {
            return symVersion;
        }

        public long getAcquiredAtMs() {
            return acquiredAtMs;
        }

        public boolean isExpired(long ttlMs, long now) {
            return now - acquiredAtMs > ttlMs;
        }

        @Override
        public String toString() {
            return hostname + DELIMITER + symVersion + DELIMITER + acquiredAtMs;
        }
    }
}
