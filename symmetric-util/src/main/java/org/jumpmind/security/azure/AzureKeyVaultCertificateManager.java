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
package org.jumpmind.security.azure;

import java.io.ByteArrayInputStream;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import org.apache.commons.lang3.NotImplementedException;
import org.jumpmind.security.BouncyCastleCertificateManager;
import org.jumpmind.security.IKeyStoreRepository;
import org.jumpmind.security.ISecretStore;
import org.jumpmind.security.ITlsMaterialProvider;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.SyncPoller;
import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.models.CertificateKeyType;
import com.azure.security.keyvault.certificates.models.CertificateOperation;
import com.azure.security.keyvault.certificates.models.CertificatePolicy;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificateWithPolicy;

/**
 * Lets Azure Key Vault create and hold the SSL certificate, which cannot be installed, exported or backed up from here. Other certificates, such as the SAML
 * one, are handled by {@link BouncyCastleCertificateManager} over the vault's keystore and saved to the vault as secrets.
 */
class AzureKeyVaultCertificateManager extends BouncyCastleCertificateManager {
    private static final int SSL_CERT_KEY_SIZE = 2048;
    private static final int SSL_CERT_VALIDITY_IN_MONTHS = 300;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final CertificateClient certificateClient;
    private final ISecretStore secretStore;

    AzureKeyVaultCertificateManager(IKeyStoreRepository keyStoreRepository, ITlsMaterialProvider tlsMaterialProvider, AzureKeyVault vault,
            ISecretStore secretStore) {
        super(keyStoreRepository, tlsMaterialProvider);
        this.certificateClient = vault.getCertificateClient();
        this.secretStore = secretStore;
    }

    /**
     * Has the vault create a self-signed certificate when it has none.
     */
    @Override
    public synchronized void installDefaultSslCert(String host) {
        host = host == null ? AppUtils.getHostName() : host;
        String vaultName = AzureKeyVault.toVaultName(AzureKeyVault.getSslCertAlias());
        try {
            certificateClient.getCertificate(vaultName);
        } catch (ResourceNotFoundException e) {
            CertificatePolicy policy = new CertificatePolicy("Self", String.format("CN=%s, OU=SymmetricDS, O=JumpMind", host))
                    .setKeyReusable(true)
                    .setKeyType(CertificateKeyType.RSA)
                    .setKeySize(SSL_CERT_KEY_SIZE)
                    .setValidityInMonths(SSL_CERT_VALIDITY_IN_MONTHS);
            SyncPoller<CertificateOperation, KeyVaultCertificateWithPolicy> poller = certificateClient.beginCreateCertificate(vaultName, policy, true,
                    new HashMap<>());
            poller.waitUntil(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
            KeyVaultCertificateWithPolicy cert = poller.getFinalResult();
            log.info("Successfully installed default ssl cert {}", cert.getName());
        }
    }

    @Override
    public synchronized void installSslCert(PrivateKeyEntry entry) {
        throw new NotImplementedException();
    }

    @Override
    public synchronized X509Certificate getCurrentSslCert() {
        byte[] cer = certificateClient.getCertificate(AzureKeyVault.toVaultName(AzureKeyVault.getSslCertAlias())).getCer();
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cer));
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized String exportCurrentSslCert(boolean includePrivateKey) {
        throw new NotImplementedException();
    }

    /**
     * Removes the certificate from the keystore loaded from the vault as well as from the vault, where {@link AzureKeyVaultKeyStoreRepository#save} keeps it as
     * a secret. Otherwise the loaded copy would stop a replacement from being installed and be saved back to the vault.
     */
    @Override
    public void deleteCertificate(String alias) throws Exception {
        super.deleteCertificate(alias);
        secretStore.deleteEntry(alias);
    }

    @Override
    public boolean supportsExportCertificate() {
        return false;
    }

    @Override
    public boolean supportsImportCertificate() {
        return false;
    }

    @Override
    public boolean supportsBackupCertificate() {
        return false;
    }

    @Override
    public boolean supportsGenerateSelfSignedCertificate() {
        return false;
    }
}
