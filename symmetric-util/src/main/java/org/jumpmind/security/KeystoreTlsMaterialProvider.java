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

import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds key managers from the local keystore and keeps trusted certificates in the local truststore.
 */
public class KeystoreTlsMaterialProvider implements ITlsMaterialProvider {
    private static final String FALLBACK_KEY_MANAGER_FACTORY_ALGORITHM = "SunX509";
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final IKeyStoreRepository keyStoreRepository;
    private final IKeyStoreRepository trustStoreRepository;

    public KeystoreTlsMaterialProvider(IKeyStoreRepository keyStoreRepository, IKeyStoreRepository trustStoreRepository) {
        this.keyStoreRepository = keyStoreRepository;
        this.trustStoreRepository = trustStoreRepository;
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory() {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getKeyManagerFactoryAlgorithm());
            keyManagerFactory.init(keyStoreRepository.load(), keyStoreRepository.getPassword().toCharArray());
            return keyManagerFactory;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public KeyStore getTrustStore() {
        return trustStoreRepository.load();
    }

    @Override
    public void saveTrustStore(KeyStore trustStore) throws Exception {
        trustStoreRepository.save(trustStore);
    }

    @Override
    public void installTrustedCert(TrustedCertificateEntry entry) {
        String subject = ((X509Certificate) entry.getTrustedCertificate()).getSubjectX500Principal().getName();
        try {
            KeyStore trustStore = getTrustStore();
            if (trustStore.getCertificateAlias(entry.getTrustedCertificate()) == null) {
                trustStore.setEntry(String.valueOf(entry.getTrustedCertificate().hashCode()), entry, null);
                log.info("Installing trusted certificate: {}", subject);
                saveTrustStore(trustStore);
            } else {
                log.info("Trusted certificate already installed: {}", subject);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String getKeyManagerFactoryAlgorithm() {
        String algorithm = System.getProperty(SecurityConstants.SYSPROP_KEY_MANAGER_FACTORY_ALGORITHM);
        if (algorithm == null) {
            algorithm = KeyManagerFactory.getDefaultAlgorithm();
        }
        if (algorithm == null) {
            algorithm = FALLBACK_KEY_MANAGER_FACTORY_ALGORITHM;
        }
        return algorithm;
    }
}
