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
package org.jumpmind.security;

import java.util.List;

import org.jumpmind.security.aws.AwsKmsEnvelopeKeyProvider;
import org.jumpmind.security.aws.AwsSecretsManagerSecretStore;

/**
 * Security service whose secret key is protected by AWS KMS through {@link AwsKmsEnvelopeKeyProvider}. Select it with
 * {@code security.service.class.name=org.jumpmind.security.AwsKmsSecurityService} and the aws.kms.* system properties in {@link SecurityConstants}, on
 * SymmetricDS Pro with the awskms module installed. The KMS-encrypted copy of the secret key is kept in AWS Secrets Manager, so every node with the same secret
 * name prefix shares it, unless {@link SecurityConstants#AWS_KMS_WRAPPED_KEY} or {@link SecurityConstants#AWS_KMS_WRAPPED_KEY_FILE} keeps it on the node
 * instead.
 * <p>
 * Keystore entries, such as startup parameters saved with the {@link SecurityConstants#PREFIX_KEYSTORE_STORAGE} prefix, are kept in AWS Secrets Manager through
 * {@link AwsSecretsManagerSecretStore}. TLS material stays in the local keystore and truststore. Certificates are managed with Bouncy Castle when it is on the
 * classpath, as on servers.
 * <p>
 * Use it for new installations only. A secret key already in the local keystore is not migrated, so values encrypted with it cannot be decrypted after
 * switching.
 */
public class AwsKmsSecurityService extends SecurityService {
    private static final String BOUNCY_CASTLE_PROVIDER_CLASS = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    private static final String BOUNCY_CASTLE_CERTIFICATE_BUILDER_CLASS = "org.bouncycastle.cert.X509v1CertificateBuilder";
    static final List<String> REQUIRED_AWS_CLASSES = List.of("software.amazon.awssdk.awscore.client.builder.AwsClientBuilder",
            "software.amazon.awssdk.regions.Region", "software.amazon.awssdk.services.kms.KmsClient",
            "software.amazon.awssdk.services.secretsmanager.SecretsManagerClient", "software.amazon.awssdk.protocols.json.AwsJsonProtocolFactory");

    public AwsKmsSecurityService() {
        super(createParts());
    }

    private static SecurityServiceParts createParts() {
        requireAwsSdk(REQUIRED_AWS_CLASSES);
        SecurityServiceParts.Builder parts = SecurityServiceParts.builder().secretKeyProvider(AwsKmsEnvelopeKeyProvider.fromSystemProperties())
                .secretStore(AwsSecretsManagerSecretStore.fromSystemProperties());
        // Bouncy Castle ships with symmetric-server, so it is missing only when symmetric-client runs without it.
        // The impact is certificates then can't be generated, imported or exported, but encryption and TLS with existing certificates work.
        if (isBouncyCastleAvailable()) {
            parts.certificateManagerFactory(BouncyCastleCertificateManager::new);
        }
        return parts.build();
    }

    /**
     * Fails at startup when this installation cannot run the service.
     *
     * @throws IllegalStateException
     *             when the service cannot run on this installation
     */
    static void requireAwsSdk(List<String> requiredClasses) {
        if (requiredClasses.stream().anyMatch(className -> !isOnClasspath(className))) {
            throw new IllegalStateException("The AWS KMS security service is not supported in SymmetricDS open source. It requires SymmetricDS Pro."
                    + " Contact the SymmetricDS sales team for more information.");
        }
    }

    /**
     * @return true when both Bouncy Castle jars that {@link BouncyCastleCertificateManager} needs, bcprov and bcpkix, are on the classpath
     */
    static boolean isBouncyCastleAvailable() {
        return isOnClasspath(BOUNCY_CASTLE_PROVIDER_CLASS) && isOnClasspath(BOUNCY_CASTLE_CERTIFICATE_BUILDER_CLASS);
    }

    static boolean isOnClasspath(String className) {
        try {
            Class.forName(className, false, AwsKmsSecurityService.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
