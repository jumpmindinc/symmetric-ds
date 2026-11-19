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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.spec.SecretKeySpec;

import org.jumpmind.util.AppUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BouncyCastleCertificateManagerTest {
    private static final String HOST = "test-host";
    private static final String TRUSTED_CERT_RESOURCE = "trusted-cert.pem";
    private static final long MILLIS_PER_DAY = 86400000L;
    private InMemoryKeyStoreRepository keyStoreRepository;
    private KeyStore trustStore;
    private BouncyCastleCertificateManager manager;

    @BeforeEach
    void setUp() throws Exception {
        new BouncyCastleHelper().checkProviderInstalled();
        keyStoreRepository = InMemoryKeyStoreRepository.persistent();
        trustStore = InMemoryKeyStoreRepository.persistent().load();
        ITlsMaterialProvider tlsMaterialProvider = mock(ITlsMaterialProvider.class);
        when(tlsMaterialProvider.getTrustStore()).thenReturn(trustStore);
        manager = new BouncyCastleCertificateManager(keyStoreRepository, tlsMaterialProvider);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS);
    }

    @Test
    void testInstallDefaultSslCert_createsSelfSignedCertificateAndSaves() throws Exception {
        manager.installDefaultSslCert(HOST);
        X509Certificate cert = (X509Certificate) keyStoreRepository.load().getCertificate(SecurityConstants.ALIAS_SYM_PRIVATE_KEY);
        assertEquals("O=JumpMind,OU=SymmetricDS,CN=test-host", cert.getSubjectX500Principal().getName());
        assertEquals(1, keyStoreRepository.getSaveCount());
    }

    @Test
    void testInstallDefaultSslCert_skipsWhenCertificateExists() {
        manager.installDefaultSslCert(HOST);
        manager.installDefaultSslCert(HOST);
        assertEquals(1, keyStoreRepository.getSaveCount());
    }

    @Test
    void testInstallDefaultSslCert_replacesEntryThatIsNotPrivateKey() throws Exception {
        KeyStore keyStore = keyStoreRepository.load();
        keyStore.setKeyEntry(SecurityConstants.ALIAS_SAML_PRIVATE_KEY, new SecretKeySpec("garbled".getBytes(), "DES"),
                InMemoryKeyStoreRepository.PASSWORD.toCharArray(), null);
        manager.installDefaultSamlSslCert(HOST, 10);
        assertNotNull(manager.getCurrentSamlSslCert());
    }

    @Test
    void testInstallDefaultSslCert_withConfiguredAlias() throws Exception {
        System.setProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS, "custom");
        manager.installDefaultSslCert(HOST);
        assertTrue(keyStoreRepository.load().containsAlias("custom"));
        assertFalse(keyStoreRepository.load().containsAlias(SecurityConstants.ALIAS_SYM_PRIVATE_KEY));
    }

    @Test
    void testInstallDefaultSamlSslCert_usesSamlAliasAndLifetime() {
        long before = System.currentTimeMillis();
        manager.installDefaultSamlSslCert(HOST, 10);
        X509Certificate cert = manager.getCurrentSamlSslCert();
        assertNotNull(cert);
        assertTrue(Math.abs(cert.getNotAfter().getTime() - (before + 10 * MILLIS_PER_DAY)) < MILLIS_PER_DAY);
    }

    @Test
    void testGetCurrentSslCert_returnsNullWhenMissing() {
        assertNull(manager.getCurrentSslCert());
    }

    @Test
    void testGetCurrentSslCert_returnsInstalledCertificate() throws Exception {
        manager.installDefaultSslCert(HOST);
        assertEquals(keyStoreRepository.load().getCertificate(SecurityConstants.ALIAS_SYM_PRIVATE_KEY), manager.getCurrentSslCert());
    }

    @Test
    void testExportCurrentSslCert_withoutPrivateKey() {
        manager.installDefaultSslCert(HOST);
        String pem = manager.exportCurrentSslCert(false);
        assertTrue(pem.contains("-----BEGIN CERTIFICATE-----"));
        assertFalse(pem.contains("PRIVATE KEY"));
    }

    @Test
    void testExportCurrentSslCert_withPrivateKey() {
        manager.installDefaultSslCert(HOST);
        assertTrue(manager.exportCurrentSslCert(true).contains("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    void testExportCurrentSslCert_returnsNullWhenMissing() {
        assertNull(manager.exportCurrentSslCert(true));
    }

    @Test
    void testCreateSslCert_fromExportedPemRoundTrips() {
        manager.installDefaultSslCert(HOST);
        byte[] pem = manager.exportCurrentSslCert(true).getBytes(StandardCharsets.UTF_8);
        PrivateKeyEntry entry = manager.createSslCert(pem, "pem", null, null);
        assertEquals(manager.getCurrentSslCert(), entry.getCertificate());
    }

    @Test
    void testCreateSslCert_throwsWithoutPrivateKey() throws Exception {
        byte[] certOnly = readTrustedCertPem();
        RuntimeException exception = assertThrows(RuntimeException.class, () -> manager.createSslCert(certOnly, "pem", null, null));
        assertEquals("Missing TLS private key", exception.getMessage());
    }

    @Test
    void testCreateSslCert_throwsForUnknownFormat() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> manager.createSslCert(new byte[0], "txt", null, null));
        assertEquals("Unknown TLS certificate format", exception.getMessage());
    }

    @Test
    void testCreateTrustedCert_fromPem() throws Exception {
        TrustedCertificateEntry entry = manager.createTrustedCert(readTrustedCertPem(), "pem", null, null);
        assertEquals("CN=symmetricds-test,OU=SymmetricDS,O=JumpMind", ((X509Certificate) entry.getTrustedCertificate()).getSubjectX500Principal().getName());
    }

    @Test
    void testInstallSslCert_withAliasStoresEntryAndSaves() throws Exception {
        PrivateKeyEntry entry = manager.createDefaultSslCert(HOST);
        manager.installSslCert(entry, "imported");
        assertEquals(entry.getCertificate(), keyStoreRepository.load().getCertificate("imported"));
        assertEquals(1, keyStoreRepository.getSaveCount());
    }

    @Test
    void testExportTrustedCert_readsTrustStoreOfTlsMaterialProvider() throws Exception {
        trustStore.setCertificateEntry("ca", manager.createTrustedCert(readTrustedCertPem(), "pem", null, null).getTrustedCertificate());
        assertTrue(manager.exportTrustedCert("ca").startsWith("-----BEGIN CERTIFICATE-----"));
    }

    @Test
    void testExportTrustedCert_returnsNullWhenMissing() {
        assertNull(manager.exportTrustedCert("ca"));
    }

    @Test
    void testDeleteCertificate_removesEntryAndSaves() throws Exception {
        manager.installDefaultSamlSslCert(HOST, 10);
        manager.deleteCertificate(SecurityConstants.ALIAS_SAML_PRIVATE_KEY);
        assertNull(manager.getCurrentSamlSslCert());
        assertEquals(2, keyStoreRepository.getSaveCount());
    }

    @Test
    void testDeleteCertificate_whenKeyStoreRejectsDeleteStillSaves() throws Exception {
        InMemoryKeyStoreRepository unloadedRepository = InMemoryKeyStoreRepository.unloaded();
        new BouncyCastleCertificateManager(unloadedRepository, mock(ITlsMaterialProvider.class)).deleteCertificate(SecurityConstants.ALIAS_SAML_PRIVATE_KEY);
        assertEquals(1, unloadedRepository.getSaveCount());
    }

    @Test
    void testGenerateV1Certificate_withInvalidLifetimeUsesDefault() throws Exception {
        long before = System.currentTimeMillis();
        X509Certificate cert = manager.generateV1Certificate(HOST, manager.generateRSAKeyPair(), 0);
        long expected = before + SecurityConstants.DEFAULT_CERT_LIFETIME_IN_DAYS * MILLIS_PER_DAY;
        assertTrue(Math.abs(cert.getNotAfter().getTime() - expected) < MILLIS_PER_DAY);
    }

    @Test
    void testGenerateV1Certificate_withWildcardHostUsesHostName() throws Exception {
        KeyPair pair = manager.generateRSAKeyPair();
        X509Certificate cert = manager.generateV1Certificate("0.0.0.0", pair, 1);
        assertTrue(cert.getSubjectX500Principal().getName().endsWith(",CN=" + AppUtils.getHostName()));
    }

    @Test
    void testGenerateRSAKeyPair_returns2048BitKey() throws Exception {
        KeyPair pair = manager.generateRSAKeyPair();
        assertEquals(2048, assertInstanceOf(RSAPublicKey.class, pair.getPublic()).getModulus().bitLength());
    }

    @Test
    void testSupports_returnsTrueForEveryOperation() {
        assertTrue(manager.supportsExportCertificate());
        assertTrue(manager.supportsImportCertificate());
        assertTrue(manager.supportsBackupCertificate());
        assertTrue(manager.supportsGenerateSelfSignedCertificate());
    }

    private byte[] readTrustedCertPem() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(TRUSTED_CERT_RESOURCE)) {
            assertNotNull(is);
            return is.readAllBytes();
        }
    }
}
