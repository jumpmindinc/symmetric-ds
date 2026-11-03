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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.models.CertificateKeyType;
import com.azure.security.keyvault.certificates.models.CertificatePolicy;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificateWithPolicy;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;

class AzureKeyVaultKeyStoreRepositoryTest {
    private static final String CERT_ALIAS = SecurityConstants.ALIAS_SYM_PRIVATE_KEY;
    private static final String SECRET_NAME = "db-password";
    private static final String SECRET_VALUE = "s3cret";
    private SecretClient secretClient;
    private CertificateClient certificateClient;
    private ISecretStore secretStore;
    private CertificatePolicy policy;
    private AzureKeyVaultKeyStoreRepository repository;

    @BeforeEach
    void setUp() {
        new BouncyCastleHelper().checkProviderInstalled();
        secretClient = mock(SecretClient.class);
        certificateClient = mock(CertificateClient.class);
        secretStore = mock(ISecretStore.class);
        policy = mock(CertificatePolicy.class);
        KeyVaultCertificateWithPolicy certificate = mock(KeyVaultCertificateWithPolicy.class);
        when(certificate.getPolicy()).thenReturn(policy);
        when(certificateClient.getCertificate(CERT_ALIAS)).thenReturn(certificate);
        listSecrets();
        repository = new AzureKeyVaultKeyStoreRepository(new AzureKeyVault(secretClient, certificateClient), secretStore);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD);
    }

    @Test
    void testLoad_addsEachSecretAsEntryNamedByAlias() throws Exception {
        listSecrets(secretProperties(SECRET_NAME));
        when(secretClient.getSecret(SECRET_NAME, "1")).thenReturn(new KeyVaultSecret(SECRET_NAME, SECRET_VALUE));
        KeyStore ks = repository.load();
        assertArrayEquals(SECRET_VALUE.getBytes(), ks.getKey("db.password", password()).getEncoded());
    }

    @Test
    void testLoad_skipsSecretHoldingSslCertificate() throws Exception {
        listSecrets(secretProperties(CERT_ALIAS));
        assertFalse(repository.load().containsAlias(CERT_ALIAS));
        verify(secretClient, never()).getSecret(anyString(), anyString());
    }

    @Test
    void testLoad_returnsSameKeyStoreAfterFirstRead() {
        assertSame(repository.load(), repository.load());
        verify(secretClient, times(1)).listPropertiesOfSecrets();
    }

    @Test
    void testLoad_withPkcs12SslCertAddsPrivateKeyEntry() throws Exception {
        PrivateKeyEntry entry = createSslCertEntry();
        exportSslCert(entry, "application/x-pkcs12", toBase64Pkcs12(entry));
        KeyStore ks = repository.load();
        assertArrayEquals(entry.getPrivateKey().getEncoded(), ks.getKey(CERT_ALIAS, password()).getEncoded());
        assertEquals(entry.getCertificate(), ks.getCertificate(CERT_ALIAS));
    }

    @Test
    void testLoad_withPemSslCertAddsPrivateKeyEntry() throws Exception {
        PrivateKeyEntry entry = createSslCertEntry();
        when(policy.getKeyType()).thenReturn(CertificateKeyType.RSA);
        exportSslCert(entry, "application/x-pem-file", toPem(entry));
        assertArrayEquals(entry.getPrivateKey().getEncoded(), repository.load().getKey(CERT_ALIAS, password()).getEncoded());
    }

    @Test
    void testLoad_withSslCertNotExportableLeavesItOut() throws Exception {
        when(policy.isExportable()).thenReturn(false);
        assertFalse(repository.load().containsAlias(CERT_ALIAS));
        verify(secretClient, never()).getSecret(CERT_ALIAS);
    }

    @Test
    void testLoad_usesConfiguredKeyStorePassword() throws Exception {
        System.setProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD, "vault-pass");
        listSecrets(secretProperties(SECRET_NAME));
        when(secretClient.getSecret(SECRET_NAME, "1")).thenReturn(new KeyVaultSecret(SECRET_NAME, SECRET_VALUE));
        assertNotNull(repository.load().getKey("db.password", "vault-pass".toCharArray()));
    }

    @Test
    void testSave_writesEntriesExceptSslCertToSecretStore() throws Exception {
        KeyStore ks = repository.load();
        ks.setKeyEntry("db.password", new SecretKeySpec(SECRET_VALUE.getBytes(), "DES"), password(), null);
        PrivateKeyEntry sslCert = createSslCertEntry();
        ks.setKeyEntry(CERT_ALIAS, sslCert.getPrivateKey(), password(), sslCert.getCertificateChain());
        repository.save(ks);
        verify(secretStore).setEntry("db.password", SECRET_VALUE);
        verify(secretStore, never()).setEntry(eq(CERT_ALIAS), anyString());
        verify(secretClient, never()).setSecret(any(KeyVaultSecret.class));
    }

    @Test
    void testSave_writesPrivateKeyEntryAsPkcs12Secret() throws Exception {
        KeyVaultSecret saved = saveSamlEntry(createSslCertEntry());
        assertEquals(SecurityConstants.ALIAS_SAML_PRIVATE_KEY, saved.getName());
        assertEquals("application/x-pkcs12", saved.getProperties().getContentType());
        verify(secretStore, never()).setEntry(eq(SecurityConstants.ALIAS_SAML_PRIVATE_KEY), anyString());
    }

    @Test
    void testLoad_withSavedPrivateKeyEntryRestoresKeyAndCertificateChain() throws Exception {
        PrivateKeyEntry samlCert = createSslCertEntry();
        KeyVaultSecret saved = saveSamlEntry(samlCert);
        listSecrets(secretProperties(saved.getName()));
        when(secretClient.getSecret(saved.getName(), "1")).thenReturn(saved);
        KeyStore ks = new AzureKeyVaultKeyStoreRepository(new AzureKeyVault(secretClient, certificateClient), secretStore).load();
        assertArrayEquals(samlCert.getPrivateKey().getEncoded(), ks.getKey(SecurityConstants.ALIAS_SAML_PRIVATE_KEY, password()).getEncoded());
        assertArrayEquals(samlCert.getCertificateChain(), ks.getCertificateChain(SecurityConstants.ALIAS_SAML_PRIVATE_KEY));
    }

    @Test
    void testReadPemKey_throwsWithoutPrivateKeyHeader() {
        assertThrows(IllegalArgumentException.class, () -> AzureKeyVaultKeyStoreRepository.readPemKey("-----BEGIN CERTIFICATE-----", "RSA"));
    }

    @Test
    void testIsPersistent() {
        assertTrue(repository.isPersistent());
    }

    private KeyVaultSecret saveSamlEntry(PrivateKeyEntry entry) throws Exception {
        KeyStore ks = repository.load();
        ks.setKeyEntry(SecurityConstants.ALIAS_SAML_PRIVATE_KEY, entry.getPrivateKey(), password(), entry.getCertificateChain());
        repository.save(ks);
        ArgumentCaptor<KeyVaultSecret> saved = ArgumentCaptor.forClass(KeyVaultSecret.class);
        verify(secretClient).setSecret(saved.capture());
        return saved.getValue();
    }

    private char[] password() {
        return repository.getPassword().toCharArray();
    }

    @SuppressWarnings("unchecked")
    private void listSecrets(SecretProperties... secrets) {
        PagedIterable<SecretProperties> paged = mock(PagedIterable.class);
        when(paged.iterator()).thenAnswer(invocation -> List.of(secrets).iterator());
        when(secretClient.listPropertiesOfSecrets()).thenReturn(paged);
    }

    private SecretProperties secretProperties(String name) {
        SecretProperties properties = mock(SecretProperties.class);
        when(properties.getName()).thenReturn(name);
        when(properties.getVersion()).thenReturn("1");
        return properties;
    }

    private void exportSslCert(PrivateKeyEntry entry, String contentType, String value) throws Exception {
        when(policy.isExportable()).thenReturn(true);
        KeyVaultCertificateWithPolicy certificate = certificateClient.getCertificate(CERT_ALIAS);
        when(certificate.getCer()).thenReturn(entry.getCertificate().getEncoded());
        KeyVaultSecret secret = new KeyVaultSecret(CERT_ALIAS, value);
        secret.setProperties(new SecretProperties().setContentType(contentType));
        when(secretClient.getSecret(CERT_ALIAS)).thenReturn(secret);
    }

    private static PrivateKeyEntry createSslCertEntry() throws Exception {
        return new BouncyCastleCertificateManager(InMemoryKeyStoreRepository.persistent(), mock(ITlsMaterialProvider.class)).createDefaultSslCert("test-host");
    }

    private static String toBase64Pkcs12(PrivateKeyEntry entry) throws Exception {
        KeyStore pkcs12 = KeyStore.getInstance(SecurityConstants.KEYSTORE_TYPE_PKCS12);
        pkcs12.load(null, null);
        pkcs12.setKeyEntry("cert", entry.getPrivateKey(), "".toCharArray(), new Certificate[] { entry.getCertificate() });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pkcs12.store(out, "".toCharArray());
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static String toPem(PrivateKeyEntry entry) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(entry.getPrivateKey().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
    }
}
