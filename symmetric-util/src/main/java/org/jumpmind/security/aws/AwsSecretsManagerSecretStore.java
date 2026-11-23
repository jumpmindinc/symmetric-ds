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

import java.util.function.Supplier;

import org.jumpmind.security.ISecretStore;
import org.jumpmind.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.InvalidRequestException;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * Keeps named secret values in AWS Secrets Manager, one secret per alias, named with a prefix that defaults to
 * {@value AwsSettings#DEFAULT_SECRETS_MANAGER_PREFIX}. New secrets are encrypted with the KMS key in {@link SecurityConstants#AWS_KMS_KEY_ID} when it is set,
 * otherwise with the account's AWS managed key. The region is taken from {@link SecurityConstants#AWS_KMS_REGION}, because a secret must be in the same region
 * as the KMS key that encrypts it.
 * <p>
 * Each call opens its own client and closes it, because entries are only read and saved when the startup parameters are loaded or saved, and a service built by
 * {@link org.jumpmind.security.SecurityServiceFactory} has no point at which a shared client could be closed.
 */
public class AwsSecretsManagerSecretStore implements ISecretStore {
    static final int MAX_SAVE_ATTEMPTS = 5;
    static final long DEFAULT_RETRY_DELAY_MILLIS = 2000;
    private static final Logger log = LoggerFactory.getLogger(AwsSecretsManagerSecretStore.class);
    private final Supplier<SecretsManagerClient> clientSupplier;
    private final String namePrefix;
    private final String kmsKeyId;
    private final long retryDelayMillis;

    AwsSecretsManagerSecretStore(Supplier<SecretsManagerClient> clientSupplier, String namePrefix, String kmsKeyId, long retryDelayMillis) {
        this.clientSupplier = clientSupplier;
        this.namePrefix = namePrefix;
        this.kmsKeyId = kmsKeyId;
        this.retryDelayMillis = retryDelayMillis;
    }

    public static AwsSecretsManagerSecretStore fromSystemProperties() {
        return new AwsSecretsManagerSecretStore(AwsSettings::buildSecretsManagerClient, AwsSettings.getSecretsManagerPrefix(), AwsSettings.getKmsKeyId(),
                DEFAULT_RETRY_DELAY_MILLIS);
    }

    @Override
    public String getEntry(String alias) {
        try (SecretsManagerClient client = clientSupplier.get()) {
            return client.getSecretValue(GetSecretValueRequest.builder().secretId(toSecretName(alias)).build()).secretString();
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    /**
     * Adds a new version to the alias's secret, creating the secret when it does not exist yet. Secrets Manager finishes deleting a secret in the background,
     * so saving an alias straight after {@link #deleteEntry(String)} can be rejected for a few seconds; the save is retried up to {@value #MAX_SAVE_ATTEMPTS}
     * times.
     */
    @Override
    public void setEntry(String alias, String value) {
        String secretName = toSecretName(alias);
        try (SecretsManagerClient client = clientSupplier.get()) {
            for (int attempt = 1;; attempt++) {
                try {
                    saveSecret(client, secretName, value);
                    return;
                } catch (InvalidRequestException e) {
                    if (attempt == MAX_SAVE_ATTEMPTS) {
                        throw e;
                    }
                    log.info("AWS Secrets Manager rejected saving secret {}, which may still be being deleted, so retrying: {}", secretName, e.getMessage());
                    waitBeforeRetry();
                }
            }
        }
    }

    private void saveSecret(SecretsManagerClient client, String secretName, String value) {
        try {
            putSecretValue(client, secretName, value);
        } catch (ResourceNotFoundException e) {
            createSecret(client, secretName, value);
        }
    }

    private void putSecretValue(SecretsManagerClient client, String secretName, String value) {
        client.putSecretValue(PutSecretValueRequest.builder().secretId(secretName).secretString(value).build());
    }

    private void createSecret(SecretsManagerClient client, String secretName, String value) {
        try {
            client.createSecret(CreateSecretRequest.builder().name(secretName).kmsKeyId(kmsKeyId).secretString(value).build());
            log.info("Created AWS Secrets Manager secret {}", secretName);
        } catch (ResourceExistsException e) {
            putSecretValue(client, secretName, value);
        }
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry saving an AWS Secrets Manager secret", e);
        }
    }

    /**
     * Deletes the alias's secret without a recovery window, so the same alias can be saved again once Secrets Manager finishes deleting it, usually within
     * seconds; {@link #setEntry(String, String)} retries until then.
     */
    @Override
    public void deleteEntry(String alias) {
        String secretName = toSecretName(alias);
        try (SecretsManagerClient client = clientSupplier.get()) {
            client.deleteSecret(DeleteSecretRequest.builder().secretId(secretName).forceDeleteWithoutRecovery(true).build());
        } catch (ResourceNotFoundException e) {
            log.info("Unable to delete AWS Secrets Manager secret {} because it does not exist", secretName);
        }
    }

    String toSecretName(String alias) {
        return namePrefix + alias;
    }
}
