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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Enumeration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.jumpmind.security.IKeyStoreRepository;
import org.jumpmind.security.ISecretStore;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificateWithPolicy;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;

/**
 * Presents Azure Key Vault as a keystore held in memory. Each secret becomes a secret key entry, or a private key entry when it holds a PKCS12 file, and the
 * SSL certificate becomes a private key entry when the vault lets its key be exported. Saving writes every entry except the SSL certificate back to the vault:
 * private key entries, such as the SAML certificate, as PKCS12 files so their key and certificate chain survive a restart, and other entries as the text of
 * their key.
 */
class AzureKeyVaultKeyStoreRepository implements IKeyStoreRepository {
    private static final String SECRET_ENTRY_ALGORITHM = "DES";
    private static final String CONTENT_TYPE_PKCS12 = "application/x-pkcs12";
    private static final String CONTENT_TYPE_PEM = "application/x-pem-file";
    private static final String PEM_BEGIN_PRIVATE_KEY = "BEGIN PRIVATE KEY";
    private static final String PEM_END_PRIVATE_KEY = "END PRIVATE KEY";
    private static final char[] PKCS12_PASSWORD = new char[0];
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SecretClient secretClient;
    private final CertificateClient certificateClient;
    private final ISecretStore secretStore;
    private KeyStore keyStore;

    AzureKeyVaultKeyStoreRepository(AzureKeyVault vault, ISecretStore secretStore) {
        this.secretClient = vault.getSecretClient();
        this.certificateClient = vault.getCertificateClient();
        this.secretStore = secretStore;
    }

    /**
     * Reads the vault on first use and returns the same keystore afterwards, so callers that change it and then {@link #save(KeyStore)} it keep seeing their
     * changes.
     */
    @Override
    public synchronized KeyStore load() {
        if (keyStore == null) {
            try {
                keyStore = readVault();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return keyStore;
    }

    private KeyStore readVault() throws Exception {
        KeyStore ks = KeyStore.getInstance(System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_TYPE, SecurityConstants.KEYSTORE_TYPE));
        char[] password = getPassword().toCharArray();
        ks.load(null, password);
        String certAlias = AzureKeyVault.getSslCertAlias();
        for (SecretProperties secret : secretClient.listPropertiesOfSecrets()) {
            if (!certAlias.equals(secret.getName())) {
                log.debug("Retrieving secret with name {}", secret.getName());
                addSecretEntry(ks, secretClient.getSecret(secret.getName(), secret.getVersion()), password);
            }
        }
        addSslCertEntry(ks, certAlias, password);
        return ks;
    }

    private void addSecretEntry(KeyStore ks, KeyVaultSecret secret, char[] password) throws Exception {
        String alias = AzureKeyVault.toAlias(secret.getName());
        if (isPkcs12(secret)) {
            PrivateKeyEntry entry = readPkcs12Entry(secret.getValue());
            ks.setKeyEntry(alias, entry.getPrivateKey(), password, entry.getCertificateChain());
        } else {
            SecretKey entry = new SecretKeySpec(secret.getValue().getBytes(), SECRET_ENTRY_ALGORITHM);
            ks.setKeyEntry(alias, entry, password, null);
        }
    }

    private void addSslCertEntry(KeyStore ks, String certAlias, char[] password) throws Exception {
        KeyVaultCertificateWithPolicy certificate = certificateClient.getCertificate(certAlias);
        if (!certificate.getPolicy().isExportable()) {
            return;
        }
        KeyVaultSecret secret = secretClient.getSecret(certAlias);
        Key key = null;
        if (isPkcs12(secret)) {
            key = readPkcs12Entry(secret.getValue()).getPrivateKey();
        } else if (CONTENT_TYPE_PEM.equals(secret.getProperties().getContentType())) {
            key = readPemKey(secret.getValue(), certificate.getPolicy().getKeyType().toString());
        }
        if (key != null) {
            ks.setKeyEntry(certAlias, key, password, new Certificate[] { readCertificate(certificate.getCer()) });
        }
    }

    private static boolean isPkcs12(KeyVaultSecret secret) {
        return secret.getProperties() != null && CONTENT_TYPE_PKCS12.equals(secret.getProperties().getContentType());
    }

    private Certificate readCertificate(byte[] cer) throws Exception {
        return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cer));
    }

    private static PrivateKeyEntry readPkcs12Entry(String base64Pkcs12) throws Exception {
        KeyStore pkcs12 = KeyStore.getInstance(SecurityConstants.KEYSTORE_TYPE_PKCS12);
        pkcs12.load(new ByteArrayInputStream(Base64.getDecoder().decode(base64Pkcs12)), PKCS12_PASSWORD);
        return (PrivateKeyEntry) pkcs12.getEntry(pkcs12.aliases().nextElement(), new PasswordProtection(PKCS12_PASSWORD));
    }

    private static String toBase64Pkcs12(String alias, PrivateKeyEntry entry) throws Exception {
        KeyStore pkcs12 = KeyStore.getInstance(SecurityConstants.KEYSTORE_TYPE_PKCS12);
        pkcs12.load(null, null);
        pkcs12.setKeyEntry(alias, entry.getPrivateKey(), PKCS12_PASSWORD, entry.getCertificateChain());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pkcs12.store(out, PKCS12_PASSWORD);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    static PrivateKey readPemKey(String pem, String keyType) throws Exception {
        StringBuilder base64 = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(pem))) {
            String line = reader.readLine();
            if (line == null || !line.contains(PEM_BEGIN_PRIVATE_KEY)) {
                throw new IllegalArgumentException("No PRIVATE KEY found");
            }
            while ((line = reader.readLine()) != null && !line.contains(PEM_END_PRIVATE_KEY)) {
                base64.append(line);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return KeyFactory.getInstance(keyType).generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64.toString())));
    }

    /**
     * Writes each entry except the SSL certificate, whose key the vault manages, to the vault as a secret.
     */
    @Override
    public void save(KeyStore ks) throws Exception {
        String certAlias = AzureKeyVault.getSslCertAlias();
        char[] password = getPassword().toCharArray();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (certAlias.equals(alias)) {
                continue;
            }
            if (ks.entryInstanceOf(alias, PrivateKeyEntry.class)) {
                savePrivateKeyEntry(alias, (PrivateKeyEntry) ks.getEntry(alias, new PasswordProtection(password)));
            } else {
                secretStore.setEntry(alias, new String(ks.getKey(alias, password).getEncoded()));
            }
        }
    }

    private void savePrivateKeyEntry(String alias, PrivateKeyEntry entry) throws Exception {
        KeyVaultSecret secret = new KeyVaultSecret(AzureKeyVault.toVaultName(alias), toBase64Pkcs12(alias, entry));
        secret.setProperties(new SecretProperties().setContentType(CONTENT_TYPE_PKCS12));
        secretClient.setSecret(secret);
    }

    @Override
    public String getPassword() {
        return SecurityService.getConfiguredKeyStorePassword();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
