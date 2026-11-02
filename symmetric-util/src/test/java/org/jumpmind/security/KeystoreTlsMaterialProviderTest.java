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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.KeyManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeystoreTlsMaterialProviderTest {
    private static final String TRUSTED_CERT_RESOURCE = "trusted-cert.pem";
    private InMemoryKeyStoreRepository keyStoreRepository;
    private InMemoryKeyStoreRepository trustStoreRepository;
    private KeystoreTlsMaterialProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        keyStoreRepository = InMemoryKeyStoreRepository.fileBased();
        trustStoreRepository = InMemoryKeyStoreRepository.fileBased();
        provider = new KeystoreTlsMaterialProvider(keyStoreRepository, trustStoreRepository);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.SYSPROP_KEY_MANAGER_FACTORY_ALGORITHM);
    }

    @Test
    void testGetKeyManagerFactory_returnsInitializedFactory() {
        assertEquals(1, provider.getKeyManagerFactory().getKeyManagers().length);
    }

    @Test
    void testGetKeyManagerFactory_withConfiguredAlgorithm() {
        System.setProperty(SecurityConstants.SYSPROP_KEY_MANAGER_FACTORY_ALGORITHM, "PKIX");
        assertEquals("PKIX", provider.getKeyManagerFactory().getAlgorithm());
    }

    @Test
    void testGetKeyManagerFactory_throwsForUnknownAlgorithm() {
        System.setProperty(SecurityConstants.SYSPROP_KEY_MANAGER_FACTORY_ALGORITHM, "NoSuchAlgorithm");
        RuntimeException exception = assertThrows(RuntimeException.class, () -> provider.getKeyManagerFactory());
        assertInstanceOf(NoSuchAlgorithmException.class, exception.getCause());
    }

    @Test
    void testGetKeyManagerFactory_throwsWhenKeyStoreNotLoaded() throws Exception {
        KeystoreTlsMaterialProvider unloadedProvider = new KeystoreTlsMaterialProvider(InMemoryKeyStoreRepository.unloaded(), trustStoreRepository);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> unloadedProvider.getKeyManagerFactory());
        assertInstanceOf(KeyStoreException.class, exception.getCause());
    }

    @Test
    void testGetKeyManagerFactoryAlgorithm_defaultsToJdkDefault() {
        assertEquals(KeyManagerFactory.getDefaultAlgorithm(), provider.getKeyManagerFactoryAlgorithm());
    }

    @Test
    void testGetTrustStore_returnsRepositoryTrustStore() {
        assertSame(trustStoreRepository.load(), provider.getTrustStore());
    }

    @Test
    void testSaveTrustStore_savesThroughRepository() throws Exception {
        provider.saveTrustStore(trustStoreRepository.load());
        assertEquals(1, trustStoreRepository.getSaveCount());
        assertEquals(0, keyStoreRepository.getSaveCount());
    }

    @Test
    void testInstallTrustedCert_addsNewCertificateAndSaves() throws Exception {
        Certificate certificate = loadTrustedCertificate();
        provider.installTrustedCert(new TrustedCertificateEntry(certificate));
        assertEquals(String.valueOf(certificate.hashCode()), trustStoreRepository.load().getCertificateAlias(certificate));
        assertEquals(1, trustStoreRepository.getSaveCount());
    }

    @Test
    void testInstallTrustedCert_skipsCertificateAlreadyTrusted() throws Exception {
        Certificate certificate = loadTrustedCertificate();
        KeyStore trustStore = trustStoreRepository.load();
        trustStore.setCertificateEntry("existing", certificate);
        provider.installTrustedCert(new TrustedCertificateEntry(certificate));
        assertEquals(1, trustStore.size());
        assertEquals(0, trustStoreRepository.getSaveCount());
    }

    @Test
    void testInstallTrustedCert_throwsWhenSaveFails() throws Exception {
        TrustedCertificateEntry entry = new TrustedCertificateEntry(loadTrustedCertificate());
        trustStoreRepository.failOnSave();
        assertThrows(IllegalStateException.class, () -> provider.installTrustedCert(entry));
    }

    private Certificate loadTrustedCertificate() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(TRUSTED_CERT_RESOURCE)) {
            assertNotNull(is);
            return CertificateFactory.getInstance("X.509").generateCertificate(is);
        }
    }
}
