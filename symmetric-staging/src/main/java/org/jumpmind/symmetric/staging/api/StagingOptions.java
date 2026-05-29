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
package org.jumpmind.symmetric.staging.api;

import java.nio.charset.Charset;
import java.time.Duration;

public final class StagingOptions {
    public static final long DEFAULT_MEMORY_THRESHOLD_BYTES = 64L * 1024L;
    public static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(5);
    private final ResourceKind kind;
    private final Charset charset;
    private final long memoryThresholdBytes;
    private final boolean encryptionEnabled;
    private final boolean compressionEnabled;
    private final CompressionAlgorithm compressionAlgorithm;
    private final int compressionLevel;
    private final Duration lockTtl;
    private final boolean checksumEnabled;

    private StagingOptions(Builder builder) {
        this.kind = builder.kind;
        this.charset = builder.charset;
        this.memoryThresholdBytes = builder.memoryThresholdBytes;
        this.encryptionEnabled = builder.encryptionEnabled;
        this.compressionEnabled = builder.compressionEnabled;
        this.compressionAlgorithm = builder.compressionAlgorithm;
        this.compressionLevel = builder.compressionLevel;
        this.lockTtl = builder.lockTtl;
        this.checksumEnabled = builder.checksumEnabled;
    }

    public static StagingOptions defaults() {
        return builder().build();
    }

    public static StagingOptions plain() {
        return builder()
                .withEncryptionEnabled(false)
                .withCompressionEnabled(false)
                .build();
    }

    public StagingOptions forScratch() {
        return toBuilder().withMemoryThresholdBytes(0L).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .withKind(kind)
                .withCharset(charset)
                .withMemoryThresholdBytes(memoryThresholdBytes)
                .withEncryptionEnabled(encryptionEnabled)
                .withCompressionEnabled(compressionEnabled)
                .withCompressionAlgorithm(compressionAlgorithm)
                .withCompressionLevel(compressionLevel)
                .withLockTtl(lockTtl)
                .withChecksumEnabled(checksumEnabled);
    }

    public ResourceKind getKind() {
        return kind;
    }

    public Charset getCharset() {
        return charset != null ? charset : Charset.defaultCharset();
    }

    public boolean hasExplicitCharset() {
        return charset != null;
    }

    public long getMemoryThresholdBytes() {
        return memoryThresholdBytes;
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public CompressionAlgorithm getCompressionAlgorithm() {
        return compressionAlgorithm;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public boolean isChecksumEnabled() {
        return checksumEnabled;
    }

    public boolean isByteExact() {
        return !encryptionEnabled && !compressionEnabled;
    }

    public static final class Builder {
        private ResourceKind kind = ResourceKind.BINARY;
        private Charset charset;
        private long memoryThresholdBytes = DEFAULT_MEMORY_THRESHOLD_BYTES;
        private boolean encryptionEnabled;
        private boolean compressionEnabled;
        private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.NONE;
        private int compressionLevel = -1;
        private Duration lockTtl = DEFAULT_LOCK_TTL;
        private boolean checksumEnabled;

        public Builder withKind(ResourceKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder withCharset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder withMemoryThresholdBytes(long memoryThresholdBytes) {
            this.memoryThresholdBytes = memoryThresholdBytes;
            return this;
        }

        public Builder withEncryptionEnabled(boolean encryptionEnabled) {
            this.encryptionEnabled = encryptionEnabled;
            return this;
        }

        public Builder withCompressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        public Builder withCompressionAlgorithm(CompressionAlgorithm compressionAlgorithm) {
            this.compressionAlgorithm = compressionAlgorithm;
            return this;
        }

        public Builder withCompressionLevel(int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        public Builder withLockTtl(Duration lockTtl) {
            this.lockTtl = lockTtl;
            return this;
        }

        public Builder withChecksumEnabled(boolean checksumEnabled) {
            this.checksumEnabled = checksumEnabled;
            return this;
        }

        public StagingOptions build() {
            return new StagingOptions(this);
        }
    }
}
