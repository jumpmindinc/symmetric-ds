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
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.security.aws;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.codec.binary.Base64;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * Keeps the KMS-encrypted data key, as Base64 text, in an AWS Secrets Manager secret named {@value #SECRET_NAME} after the secret name prefix. It survives
 * reinstalls and containers without a persistent volume, and nodes in a cluster share it without extra setup.
 */
class SecretsManagerWrappedDataKeySource implements WrappedDataKeySource {
    static final String SECRET_NAME = "kms-wrapped-key";
    private static final String DESCRIPTION = "SymmetricDS secret key, encrypted with AWS KMS";
    private final Supplier<SecretsManagerClient> clientSupplier;
    private final String secretName;
    private final String kmsKeyId;

    SecretsManagerWrappedDataKeySource(Supplier<SecretsManagerClient> clientSupplier, String secretName, String kmsKeyId) {
        this.clientSupplier = clientSupplier;
        this.secretName = secretName;
        this.kmsKeyId = kmsKeyId;
    }

    static SecretsManagerWrappedDataKeySource fromSystemProperties() {
        return new SecretsManagerWrappedDataKeySource(AwsSettings::buildSecretsManagerClient, AwsSettings.getSecretsManagerPrefix() + SECRET_NAME,
                AwsSettings.getKmsKeyId());
    }

    /**
     * Opens a client for this call only, because the key is read once per JVM.
     */
    @Override
    public Optional<byte[]> read() {
        try (SecretsManagerClient client = clientSupplier.get()) {
            return Optional.of(Base64.decodeBase64(client.getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build()).secretString()));
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Creates the secret, which fails atomically when it exists, so two nodes that start at once never overwrite each other's key.
     */
    @Override
    public boolean writeIfAbsent(byte[] wrappedKey) {
        try (SecretsManagerClient client = clientSupplier.get()) {
            client.createSecret(CreateSecretRequest.builder().name(secretName).description(DESCRIPTION).kmsKeyId(kmsKeyId)
                    .secretString(Base64.encodeBase64String(wrappedKey)).build());
            return true;
        } catch (ResourceExistsException e) {
            return false;
        }
    }

    @Override
    public String describe() {
        return "AWS Secrets Manager secret " + secretName;
    }
}
