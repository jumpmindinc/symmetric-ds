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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.security.aws.AwsKmsEnvelopeKeyProvider;
import org.jumpmind.security.aws.AwsSecretsManagerSecretStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AwsKmsSecurityServiceTest {
    @BeforeEach
    void setUp() {
        System.setProperty(SecurityConstants.AWS_KMS_KEY_ID, "alias/symmetricds");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.AWS_KMS_KEY_ID);
    }

    @Test
    void testConstructor_usesAwsKmsEnvelopeKeyProvider() {
        assertInstanceOf(AwsKmsEnvelopeKeyProvider.class, new AwsKmsSecurityService().getSecretKeyProvider());
    }

    @Test
    void testConstructor_usesAwsSecretsManagerSecretStore() {
        assertInstanceOf(AwsSecretsManagerSecretStore.class, new AwsKmsSecurityService().getSecretStore());
    }

    @Test
    void testConstructor_keepsTlsMaterialLocal() {
        assertInstanceOf(KeystoreTlsMaterialProvider.class, new AwsKmsSecurityService().getTlsMaterialProvider());
    }

    @Test
    void testConstructor_withBouncyCastleAvailableManagesCertificates() {
        assertInstanceOf(BouncyCastleCertificateManager.class, new AwsKmsSecurityService().getCertificateManager());
    }

    @Test
    void testConstructor_throwsWithoutKmsKeyId() {
        System.clearProperty(SecurityConstants.AWS_KMS_KEY_ID);
        assertThrows(IllegalStateException.class, () -> new AwsKmsSecurityService());
    }

    @Test
    void testIsOnClasspath_returnsFalseForMissingClass() {
        assertFalse(AwsKmsSecurityService.isOnClasspath("org.bouncycastle.NoSuchClass"));
    }

    @Test
    void testIsBouncyCastleAvailable_whenOnClasspath() {
        assertTrue(AwsKmsSecurityService.isBouncyCastleAvailable());
    }
}
