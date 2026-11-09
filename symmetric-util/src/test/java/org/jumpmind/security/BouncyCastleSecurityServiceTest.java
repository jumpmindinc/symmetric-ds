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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore.TrustedCertificateEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class BouncyCastleSecurityServiceTest {
    private static final String TRUSTED_CERT_RESOURCE = "trusted-cert.pem";
    private BouncyCastleSecurityService securityService;

    @BeforeEach
    void setUp() {
        new BouncyCastleHelper().checkProviderInstalled();
        securityService = new BouncyCastleSecurityService();
    }

    @Test
    void testCreateCertificateManager_returnsBouncyCastleManager() {
        assertInstanceOf(BouncyCastleCertificateManager.class, securityService.getCertificateManager());
    }

    @Test
    void testConstructor_withoutCertificateManagerFactoryUsesBouncyCastleAndKeepsOtherParts() {
        ISecretStore secretStore = mock(ISecretStore.class);
        BouncyCastleSecurityService service = new BouncyCastleSecurityService(SecurityServiceParts.builder().secretStore(secretStore).build());
        assertInstanceOf(BouncyCastleCertificateManager.class, service.getCertificateManager());
        assertSame(secretStore, service.getSecretStore());
    }

    @Test
    void testConstructor_withoutCertificateManagerFactoryDeprecatedMethodsWork() throws Exception {
        BouncyCastleSecurityService service = new BouncyCastleSecurityService(SecurityServiceParts.builder().build());
        assertEquals("RSA", service.generateRSAKeyPair().getPublic().getAlgorithm());
    }

    @Test
    void testConstructor_withBouncyCastleSubclassManager() {
        BouncyCastleSecurityService service = new BouncyCastleSecurityService(SecurityServiceParts.builder().certificateManagerFactory((repository,
                tlsMaterialProvider) -> new BouncyCastleCertificateManager(repository, tlsMaterialProvider) {
                }).build());
        assertInstanceOf(BouncyCastleCertificateManager.class, service.getCertificateManager());
    }

    @Test
    void testConstructor_withOtherCertificateManagerThrows() {
        SecurityServiceParts parts = SecurityServiceParts.builder().certificateManagerFactory((repository,
                tlsMaterialProvider) -> new UnsupportedCertificateManager())
                .build();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new BouncyCastleSecurityService(parts));
        assertEquals("BouncyCastleSecurityService needs a BouncyCastleCertificateManager, but its certificate manager factory created a"
                + " UnsupportedCertificateManager", exception.getMessage());
    }

    @Test
    void testSupports_returnsTrueForEveryOperation() {
        assertTrue(securityService.supportsExportCertificate());
        assertTrue(securityService.supportsImportCertificate());
        assertTrue(securityService.supportsBackupCertificate());
        assertTrue(securityService.supportsGenerateSelfSignedCertificate());
    }

    @Test
    void testGenerateRSAKeyPair_delegatesToCertificateManager() throws Exception {
        assertEquals("RSA", securityService.generateRSAKeyPair().getPublic().getAlgorithm());
    }

    @Test
    void testGenerateV1Certificate_delegatesToCertificateManager() throws Exception {
        String subject = securityService.generateV1Certificate("test-host", securityService.generateRSAKeyPair(), 1).getSubjectX500Principal().getName();
        assertEquals("O=JumpMind,OU=SymmetricDS,CN=test-host", subject);
    }

    @Test
    void testCreateSslCert_delegatesToCertificateManager() throws Exception {
        assertInstanceOf(TrustedCertificateEntry.class, securityService.createSslCert(readTrustedCertPem(), "pem", null, null, false));
    }

    @Test
    void testReadPemBytes_delegatesToCertificateManager() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(TRUSTED_CERT_RESOURCE), StandardCharsets.UTF_8))) {
            reader.readLine();
            assertTrue(securityService.readPemBytes(reader).length > 0);
        }
    }

    private byte[] readTrustedCertPem() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(TRUSTED_CERT_RESOURCE)) {
            return is.readAllBytes();
        }
    }
}
