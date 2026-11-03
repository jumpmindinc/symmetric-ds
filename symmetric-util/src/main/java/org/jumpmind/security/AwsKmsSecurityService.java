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

/**
 * Security service whose secret key is protected by AWS KMS through {@link KmsEnvelopeKeyProvider}. Select it with
 * {@code security.service.class.name=org.jumpmind.security.AwsKmsSecurityService} and the aws.kms.* system properties in {@link SecurityConstants}, with the
 * awskms module installed.
 * <p>
 * Keystore entries and TLS material stay in the local keystore and truststore. Certificates are managed with Bouncy Castle when it is on the classpath, as on
 * servers.
 * <p>
 * Use it for new installations only. A secret key already in the local keystore is not migrated, so values encrypted with it cannot be decrypted after
 * switching.
 */
public class AwsKmsSecurityService extends SecurityService {
    private static final String BOUNCY_CASTLE_PROVIDER_CLASS = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    private static final String BOUNCY_CASTLE_CERTIFICATE_BUILDER_CLASS = "org.bouncycastle.cert.X509v1CertificateBuilder";

    public AwsKmsSecurityService() {
        super(createParts());
    }

    private static SecurityServiceParts createParts() {
        SecurityServiceParts.Builder parts = SecurityServiceParts.builder().secretKeyProvider(KmsEnvelopeKeyProvider.fromSystemProperties());
        if (isBouncyCastleAvailable()) {
            parts.certificateManagerFactory(BouncyCastleCertificateManager::new);
        }
        return parts.build();
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
