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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.models.CertificatePolicy;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificateWithPolicy;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;

class AzureKeyVaultSecurityServiceTest {
    private static final String ALIAS = "corp-000.db.password";
    private SecretClient secretClient;
    private AzureKeyVaultSecurityService securityService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        SecurityService.clearSecretKeyCache();
        secretClient = mock(SecretClient.class);
        when(secretClient.getVaultUrl()).thenReturn("https://symmetricds.vault.azure.net");
        PagedIterable<SecretProperties> noSecrets = mock(PagedIterable.class);
        when(noSecrets.iterator()).thenAnswer(invocation -> List.<SecretProperties> of().iterator());
        when(secretClient.listPropertiesOfSecrets()).thenReturn(noSecrets);
        CertificateClient certificateClient = mock(CertificateClient.class);
        KeyVaultCertificateWithPolicy certificate = mock(KeyVaultCertificateWithPolicy.class);
        CertificatePolicy policy = mock(CertificatePolicy.class);
        when(certificate.getPolicy()).thenReturn(policy);
        when(certificateClient.getCertificate(SecurityConstants.ALIAS_SYM_PRIVATE_KEY)).thenReturn(certificate);
        securityService = new AzureKeyVaultSecurityService(new AzureKeyVault(secretClient, certificateClient));
    }

    @AfterEach
    void tearDown() {
        SecurityService.clearSecretKeyCache();
    }

    @Test
    void testConstructor_usesAzureParts() {
        assertInstanceOf(AzureKeyVaultSecretKeyProvider.class, securityService.getSecretKeyProvider());
        assertInstanceOf(AzureKeyVaultSecretStore.class, securityService.getSecretStore());
        assertInstanceOf(AzureKeyVaultCertificateManager.class, securityService.getCertificateManager());
    }

    @Test
    void testConstructor_readsTlsMaterialFromVaultKeyStore() {
        assertInstanceOf(KeystoreTlsMaterialProvider.class, securityService.getTlsMaterialProvider());
    }

    @Test
    void testGetKeyStore_returnsVaultKeyStore() {
        assertSame(securityService.getKeyStore(), securityService.getKeyStore());
        verify(secretClient).listPropertiesOfSecrets();
    }

    @Test
    void testGetKeystoreEntry_readsVaultSecret() throws Exception {
        when(secretClient.getSecret("corp-000-db-password")).thenReturn(new KeyVaultSecret("corp-000-db-password", "s3cret"));
        assertEquals("s3cret", securityService.getKeystoreEntry(ALIAS));
    }

    @Test
    void testEncrypt_withNewVaultGeneratesKeySavesItAndRoundTrips() {
        when(secretClient.getSecret("sym-secret")).thenThrow(new ResourceNotFoundException("not found", null));
        String encrypted = securityService.encrypt("plain text");
        assertEquals("plain text", securityService.decrypt(encrypted));
        verify(secretClient).setSecret(eq("sym-secret"), anyString());
    }
}
