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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.X509Certificate;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.SyncPoller;
import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.models.CertificateOperation;
import com.azure.security.keyvault.certificates.models.CertificatePolicy;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificateWithPolicy;

class AzureKeyVaultCertificateManagerTest {
    private static final String CERT_ALIAS = SecurityConstants.ALIAS_SYM_PRIVATE_KEY;
    private static final String HOST = "test-host";
    private CertificateClient certificateClient;
    private ISecretStore secretStore;
    private InMemoryKeyStoreRepository keyStoreRepository;
    private AzureKeyVaultCertificateManager manager;

    @BeforeEach
    void setUp() throws Exception {
        new BouncyCastleHelper().checkProviderInstalled();
        certificateClient = mock(CertificateClient.class);
        secretStore = mock(ISecretStore.class);
        keyStoreRepository = InMemoryKeyStoreRepository.persistent();
        ITlsMaterialProvider tlsMaterialProvider = mock(ITlsMaterialProvider.class);
        manager = new AzureKeyVaultCertificateManager(keyStoreRepository, tlsMaterialProvider, new AzureKeyVault(null, certificateClient), secretStore);
    }

    @Test
    void testInstallDefaultSslCert_keepsExistingVaultCertificate() {
        KeyVaultCertificateWithPolicy existing = mock(KeyVaultCertificateWithPolicy.class);
        when(certificateClient.getCertificate(CERT_ALIAS)).thenReturn(existing);
        manager.installDefaultSslCert(HOST);
        verify(certificateClient, never()).beginCreateCertificate(anyString(), any(CertificatePolicy.class), anyBoolean(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInstallDefaultSslCert_whenMissingHasVaultCreateSelfSignedCertificate() {
        when(certificateClient.getCertificate(CERT_ALIAS)).thenThrow(new ResourceNotFoundException("not found", null));
        SyncPoller<CertificateOperation, KeyVaultCertificateWithPolicy> poller = mock(SyncPoller.class);
        KeyVaultCertificateWithPolicy created = mock(KeyVaultCertificateWithPolicy.class);
        when(poller.getFinalResult()).thenReturn(created);
        ArgumentCaptor<CertificatePolicy> policy = ArgumentCaptor.forClass(CertificatePolicy.class);
        when(certificateClient.beginCreateCertificate(eq(CERT_ALIAS), policy.capture(), eq(true), anyMap())).thenReturn(poller);
        manager.installDefaultSslCert(HOST);
        verify(poller).waitUntil(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
        assertEquals("CN=test-host, OU=SymmetricDS, O=JumpMind", policy.getValue().getSubject());
        assertEquals(2048, policy.getValue().getKeySize());
    }

    @Test
    void testGetCurrentSslCert_readsCertificateFromVault() throws Exception {
        PrivateKeyEntry entry = manager.createDefaultSslCert(HOST);
        KeyVaultCertificateWithPolicy certificate = mock(KeyVaultCertificateWithPolicy.class);
        when(certificate.getCer()).thenReturn(entry.getCertificate().getEncoded());
        when(certificateClient.getCertificate(CERT_ALIAS)).thenReturn(certificate);
        X509Certificate current = manager.getCurrentSslCert();
        assertEquals(entry.getCertificate(), current);
    }

    @Test
    void testInstallSslCert_throwsNotImplemented() throws Exception {
        PrivateKeyEntry entry = manager.createDefaultSslCert(HOST);
        assertThrows(NotImplementedException.class, () -> manager.installSslCert(entry));
    }

    @Test
    void testExportCurrentSslCert_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.exportCurrentSslCert(true));
    }

    @Test
    void testDeleteCertificate_removesFromKeyStoreAndVault() throws Exception {
        manager.installDefaultSamlSslCert(HOST, 30);
        manager.deleteCertificate(SecurityConstants.ALIAS_SAML_PRIVATE_KEY);
        assertFalse(keyStoreRepository.load().containsAlias(SecurityConstants.ALIAS_SAML_PRIVATE_KEY));
        verify(secretStore).deleteEntry(SecurityConstants.ALIAS_SAML_PRIVATE_KEY);
    }

    @Test
    void testInstallDefaultSamlSslCert_afterDeleteInstallsNewCertificate() throws Exception {
        manager.installDefaultSamlSslCert(HOST, 30);
        X509Certificate original = manager.getCurrentSamlSslCert();
        manager.deleteCertificate(SecurityConstants.ALIAS_SAML_PRIVATE_KEY);
        manager.installDefaultSamlSslCert(HOST, 30);
        assertNotEquals(original, manager.getCurrentSamlSslCert());
    }

    @Test
    void testInstallDefaultSamlSslCert_savesToKeyStoreRepository() throws Exception {
        manager.installDefaultSamlSslCert(HOST, 30);
        assertTrue(keyStoreRepository.load().containsAlias(SecurityConstants.ALIAS_SAML_PRIVATE_KEY));
        assertEquals(1, keyStoreRepository.getSaveCount());
    }

    @Test
    void testSupports_allFalse() {
        assertFalse(manager.supportsExportCertificate());
        assertFalse(manager.supportsImportCertificate());
        assertFalse(manager.supportsBackupCertificate());
        assertFalse(manager.supportsGenerateSelfSignedCertificate());
    }
}
