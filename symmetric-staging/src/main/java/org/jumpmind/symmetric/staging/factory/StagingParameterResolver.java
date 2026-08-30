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
package org.jumpmind.symmetric.staging.factory;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.jumpmind.symmetric.staging.api.AccountType;
import org.jumpmind.symmetric.staging.api.CompressionAlgorithm;
import org.jumpmind.symmetric.staging.api.IStagingAuthProvider;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StagingOptions;
import org.jumpmind.symmetric.staging.api.StorageKind;

public final class StagingParameterResolver {
    private final Map<String, String> parameters;
    private final Map<String, String> environment;

    public StagingParameterResolver(Map<String, String> parameters, Map<String, String> environment) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public String resolve(String parameterName, String envVarName, String defaultValue) {
        if (envVarName != null) {
            String envValue = environment.get(envVarName);
            if (envValue != null && !envValue.isBlank()) {
                return envValue;
            }
        }
        String paramValue = parameters.get(parameterName);
        if (paramValue != null && !paramValue.isBlank()) {
            return paramValue;
        }
        return defaultValue;
    }

    public boolean resolveBoolean(String parameterName, boolean defaultValue) {
        String value = parameters.get(parameterName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public long resolveLong(String parameterName, long defaultValue) {
        String value = parameters.get(parameterName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public StagingConfig buildConfig() {
        String providerType = resolve(StagingParameterNames.STAGING_PROVIDER_TYPE,
                StagingParameterNames.ENV_STAGING_PROVIDER_TYPE, "file");
        StorageKind storageKind = StorageKind.fromProviderType(providerType);
        String accountTypeRaw = resolve(StagingParameterNames.STAGING_ACCOUNT_TYPE,
                StagingParameterNames.ENV_STAGING_ACCOUNT_TYPE, "implied");
        AccountType accountType = AccountType.fromString(accountTypeRaw);
        String stagingDir = resolve(StagingParameterNames.STAGING_DIR,
                StagingParameterNames.ENV_STAGING_DIR, null);
        String scratchDir = resolve(StagingParameterNames.STAGING_SCRATCH_DIR,
                StagingParameterNames.ENV_STAGING_SCRATCH_DIR,
                System.getProperty("java.io.tmpdir"));
        String providerUrl = resolve(StagingParameterNames.STAGING_PROVIDER_URL,
                StagingParameterNames.ENV_STAGING_PROVIDER_URL, null);
        String providerBucket = resolve(StagingParameterNames.STAGING_PROVIDER_BUCKET,
                StagingParameterNames.ENV_STAGING_PROVIDER_BUCKET, null);
        String accountKey = resolve(StagingParameterNames.STAGING_ACCOUNT_KEY,
                StagingParameterNames.ENV_STAGING_ACCOUNT_KEY, null);
        String accountSecret = resolve(StagingParameterNames.STAGING_ACCOUNT_SECRET,
                StagingParameterNames.ENV_STAGING_ACCOUNT_SECRET, null);
        long lowFreeMb = resolveLong(StagingParameterNames.STAGING_LOW_SPACE_THRESHOLD_MEGABYTES, 0L);
        long memoryThreshold = resolveLong(StagingParameterNames.STAGING_MEMORY_THRESHOLD_BYTES,
                StagingOptions.DEFAULT_MEMORY_THRESHOLD_BYTES);
        long lockTtlMs = resolveLong(StagingParameterNames.STAGING_LOCK_TTL_MS,
                StagingOptions.DEFAULT_LOCK_TTL.toMillis());
        String charsetName = parameters.get(StagingParameterNames.STAGING_CHARSET);
        Charset charset = (charsetName != null && !charsetName.isBlank())
                ? Charset.forName(charsetName.trim())
                : null;
        boolean encryptionEnabled = resolveBoolean(StagingParameterNames.STAGING_ENCRYPTION_ENABLED, false);
        boolean compressionEnabled = resolveBoolean(StagingParameterNames.STAGING_COMPRESSION_ENABLED, false);
        boolean checksumEnabled = resolveBoolean(StagingParameterNames.STAGING_CHECKSUM_ENABLED, false);
        String compressionAlgoRaw = parameters.get(StagingParameterNames.STAGING_COMPRESSION_ALGORITHM);
        CompressionAlgorithm compressionAlgorithm = compressionAlgoRaw == null || compressionAlgoRaw.isBlank()
                ? CompressionAlgorithm.GZIP
                : CompressionAlgorithm.valueOf(compressionAlgoRaw.trim().toUpperCase());
        IStagingAuthProvider authProvider = buildAuthProvider(accountType, accountKey, accountSecret);
        return StagingConfig.builder()
                .withStorageKind(storageKind)
                .withStagingDir(stagingDir)
                .withScratchDir(scratchDir)
                .withProviderUrl(providerUrl)
                .withProviderBucket(providerBucket)
                .withAuthProvider(authProvider)
                .withLowFreeSpaceMegabytes(lowFreeMb)
                .withMemoryThresholdBytes(memoryThreshold)
                .withLockTtl(Duration.ofMillis(lockTtlMs))
                .withCharset(charset)
                .withEncryptionEnabled(encryptionEnabled)
                .withCompressionEnabled(compressionEnabled)
                .withCompressionAlgorithm(compressionAlgorithm)
                .withChecksumEnabled(checksumEnabled)
                .build();
    }

    private IStagingAuthProvider buildAuthProvider(AccountType accountType, String key, String secret) {
        switch (accountType) {
            case IMPLIED:
                return new ImpliedAuthProvider();
            case NATIVE:
                return new NativeAuthProvider(key, secret);
            case TOKEN:
            case OPENID:
            case LDAP:
                throw new NotImplementedException("staging.account.type=" + accountType.name().toLowerCase()
                        + " is not yet supported");
            default:
                throw new IllegalArgumentException("Unknown account type: " + accountType);
        }
    }
}
