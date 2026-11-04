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

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.security.SecurityConstants;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Reads the AWS settings the AWS parts share from system properties, and builds their clients. Both clients use {@link SecurityConstants#AWS_KMS_REGION},
 * because a secret must be in the same region as the KMS key that encrypts it.
 */
final class AwsSettings {
    static final String DEFAULT_SECRETS_MANAGER_PREFIX = "symmetricds/";

    private AwsSettings() {
    }

    /**
     * @return the KMS key from {@link SecurityConstants#AWS_KMS_KEY_ID}, or null when it is not set
     */
    static String getKmsKeyId() {
        return StringUtils.trimToNull(System.getProperty(SecurityConstants.AWS_KMS_KEY_ID));
    }

    static String getSecretsManagerPrefix() {
        return StringUtils.defaultIfBlank(System.getProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX), DEFAULT_SECRETS_MANAGER_PREFIX).trim();
    }

    static KmsClient buildKmsClient() {
        return configure(KmsClient.builder(), SecurityConstants.AWS_KMS_ENDPOINT).build();
    }

    static SecretsManagerClient buildSecretsManagerClient() {
        return configure(SecretsManagerClient.builder(), SecurityConstants.AWS_SECRETS_MANAGER_ENDPOINT).build();
    }

    private static <B extends AwsClientBuilder<B, ?>> B configure(B builder, String endpointProperty) {
        String region = StringUtils.trimToNull(System.getProperty(SecurityConstants.AWS_KMS_REGION));
        if (region != null) {
            builder.region(Region.of(region));
        }
        String endpoint = StringUtils.trimToNull(System.getProperty(endpointProperty));
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder;
    }
}
