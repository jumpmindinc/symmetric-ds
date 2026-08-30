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

public final class StagingConfig {
    private final StorageKind storageKind;
    private final String stagingDir;
    private final String scratchDir;
    private final String providerUrl;
    private final String providerBucket;
    private final IStagingAuthProvider authProvider;
    private final long lowFreeSpaceMegabytes;
    private final long memoryThresholdBytes;
    private final Duration lockTtl;
    private final Charset charset;
    private final boolean encryptionEnabled;
    private final boolean compressionEnabled;
    private final CompressionAlgorithm compressionAlgorithm;
    private final boolean checksumEnabled;

    private StagingConfig(Builder builder) {
        this.storageKind = builder.storageKind;
        this.stagingDir = builder.stagingDir;
        this.scratchDir = builder.scratchDir;
        this.providerUrl = builder.providerUrl;
        this.providerBucket = builder.providerBucket;
        this.authProvider = builder.authProvider;
        this.lowFreeSpaceMegabytes = builder.lowFreeSpaceMegabytes;
        this.memoryThresholdBytes = builder.memoryThresholdBytes;
        this.lockTtl = builder.lockTtl;
        this.charset = builder.charset;
        this.encryptionEnabled = builder.encryptionEnabled;
        this.compressionEnabled = builder.compressionEnabled;
        this.compressionAlgorithm = builder.compressionAlgorithm;
        this.checksumEnabled = builder.checksumEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public StorageKind getStorageKind() {
        return storageKind;
    }

    public String getStagingDir() {
        return stagingDir;
    }

    public String getScratchDir() {
        return scratchDir;
    }

    public String getProviderUrl() {
        return providerUrl;
    }

    public String getProviderBucket() {
        return providerBucket;
    }

    public IStagingAuthProvider getAuthProvider() {
        return authProvider;
    }

    public long getLowFreeSpaceMegabytes() {
        return lowFreeSpaceMegabytes;
    }

    public long getMemoryThresholdBytes() {
        return memoryThresholdBytes;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public Charset getCharset() {
        return charset != null ? charset : Charset.defaultCharset();
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

    public boolean isChecksumEnabled() {
        return checksumEnabled;
    }

    public StagingOptions defaultOptions() {
        return StagingOptions.builder()
                .withMemoryThresholdBytes(memoryThresholdBytes)
                .withEncryptionEnabled(encryptionEnabled)
                .withCompressionEnabled(compressionEnabled)
                .withCompressionAlgorithm(compressionAlgorithm)
                .withChecksumEnabled(checksumEnabled)
                .withLockTtl(lockTtl)
                .withCharset(charset)
                .build();
    }

    public static final class Builder {
        private StorageKind storageKind = StorageKind.FILESYSTEM;
        private String stagingDir;
        private String scratchDir = System.getProperty("java.io.tmpdir");
        private String providerUrl;
        private String providerBucket;
        private IStagingAuthProvider authProvider;
        private long lowFreeSpaceMegabytes;
        private long memoryThresholdBytes = StagingOptions.DEFAULT_MEMORY_THRESHOLD_BYTES;
        private Duration lockTtl = StagingOptions.DEFAULT_LOCK_TTL;
        private Charset charset;
        private boolean encryptionEnabled;
        private boolean compressionEnabled;
        private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.NONE;
        private boolean checksumEnabled;

        public Builder withStorageKind(StorageKind storageKind) {
            this.storageKind = storageKind;
            return this;
        }

        public Builder withStagingDir(String stagingDir) {
            this.stagingDir = stagingDir;
            return this;
        }

        public Builder withScratchDir(String scratchDir) {
            this.scratchDir = scratchDir;
            return this;
        }

        public Builder withProviderUrl(String providerUrl) {
            this.providerUrl = providerUrl;
            return this;
        }

        public Builder withProviderBucket(String providerBucket) {
            this.providerBucket = providerBucket;
            return this;
        }

        public Builder withAuthProvider(IStagingAuthProvider authProvider) {
            this.authProvider = authProvider;
            return this;
        }

        public Builder withLowFreeSpaceMegabytes(long lowFreeSpaceMegabytes) {
            this.lowFreeSpaceMegabytes = lowFreeSpaceMegabytes;
            return this;
        }

        public Builder withMemoryThresholdBytes(long memoryThresholdBytes) {
            this.memoryThresholdBytes = memoryThresholdBytes;
            return this;
        }

        public Builder withLockTtl(Duration lockTtl) {
            this.lockTtl = lockTtl;
            return this;
        }

        public Builder withCharset(Charset charset) {
            this.charset = charset;
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

        public Builder withChecksumEnabled(boolean checksumEnabled) {
            this.checksumEnabled = checksumEnabled;
            return this;
        }

        public StagingConfig build() {
            return new StagingConfig(this);
        }
    }
}
