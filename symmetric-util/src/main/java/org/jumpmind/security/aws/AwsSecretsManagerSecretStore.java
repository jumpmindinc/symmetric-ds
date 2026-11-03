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

import java.net.URI;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.security.ISecretStore;
import org.jumpmind.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * Keeps named secret values in AWS Secrets Manager, one secret per alias, named with a prefix that defaults to {@value #DEFAULT_NAME_PREFIX}. New secrets are
 * encrypted with the KMS key in {@link SecurityConstants#AWS_KMS_KEY_ID} when it is set, otherwise with the account's AWS managed key. The region is taken from
 * {@link SecurityConstants#AWS_KMS_REGION}, because a secret must be in the same region as the KMS key that encrypts it.
 */
public class AwsSecretsManagerSecretStore implements ISecretStore {
    static final String DEFAULT_NAME_PREFIX = "symmetricds/";
    private static final Logger log = LoggerFactory.getLogger(AwsSecretsManagerSecretStore.class);
    private final Supplier<SecretsManagerClient> clientSupplier;
    private final String namePrefix;
    private final String kmsKeyId;
    private SecretsManagerClient client;

    AwsSecretsManagerSecretStore(Supplier<SecretsManagerClient> clientSupplier, String namePrefix, String kmsKeyId) {
        this.clientSupplier = clientSupplier;
        this.namePrefix = namePrefix;
        this.kmsKeyId = kmsKeyId;
    }

    public static AwsSecretsManagerSecretStore fromSystemProperties() {
        String namePrefix = StringUtils.defaultIfBlank(System.getProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX), DEFAULT_NAME_PREFIX).trim();
        String kmsKeyId = StringUtils.trimToNull(System.getProperty(SecurityConstants.AWS_KMS_KEY_ID));
        String region = StringUtils.trimToNull(System.getProperty(SecurityConstants.AWS_KMS_REGION));
        String endpoint = StringUtils.trimToNull(System.getProperty(SecurityConstants.AWS_SECRETS_MANAGER_ENDPOINT));
        return new AwsSecretsManagerSecretStore(() -> buildClient(region, endpoint), namePrefix, kmsKeyId);
    }

    private static SecretsManagerClient buildClient(String region, String endpoint) {
        SecretsManagerClientBuilder builder = SecretsManagerClient.builder();
        if (region != null) {
            builder.region(Region.of(region));
        }
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Override
    public String getEntry(String alias) {
        try {
            return getClient().getSecretValue(GetSecretValueRequest.builder().secretId(toSecretName(alias)).build()).secretString();
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    /**
     * Adds a new version to the alias's secret, creating the secret when it does not exist yet.
     */
    @Override
    public void setEntry(String alias, String value) {
        String secretName = toSecretName(alias);
        try {
            putSecretValue(secretName, value);
        } catch (ResourceNotFoundException e) {
            createSecret(secretName, value);
        }
    }

    private void putSecretValue(String secretName, String value) {
        getClient().putSecretValue(PutSecretValueRequest.builder().secretId(secretName).secretString(value).build());
    }

    private void createSecret(String secretName, String value) {
        try {
            getClient().createSecret(CreateSecretRequest.builder().name(secretName).kmsKeyId(kmsKeyId).secretString(value).build());
            log.info("Created AWS Secrets Manager secret {}", secretName);
        } catch (ResourceExistsException e) {
            putSecretValue(secretName, value);
        }
    }

    /**
     * Deletes the alias's secret without a recovery window, so the same alias can be saved again straight away.
     */
    @Override
    public void deleteEntry(String alias) {
        String secretName = toSecretName(alias);
        try {
            getClient().deleteSecret(DeleteSecretRequest.builder().secretId(secretName).forceDeleteWithoutRecovery(true).build());
        } catch (ResourceNotFoundException e) {
            log.info("Unable to delete AWS Secrets Manager secret {} because it does not exist", secretName);
        }
    }

    String toSecretName(String alias) {
        return namePrefix + alias;
    }

    /**
     * Creates the client on first use and keeps it, because entries are read and saved each time the startup parameters are loaded or saved.
     */
    private synchronized SecretsManagerClient getClient() {
        if (client == null) {
            client = clientSupplier.get();
        }
        return client;
    }
}
