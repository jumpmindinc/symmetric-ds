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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.util.AppUtils;

import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.SyncPoller;
import com.azure.identity.CredentialBuilderBase;
import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.CertificateClientBuilder;
import com.azure.security.keyvault.certificates.models.CertificateKeyType;
import com.azure.security.keyvault.certificates.models.CertificateOperation;
import com.azure.security.keyvault.certificates.models.CertificatePolicy;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificate;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificateWithPolicy;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;

public class AzureKeyVaultSecurityService extends BouncyCastleSecurityService implements ISecurityService {
    private SecretClient secretClient;
    private KeyClient keyClient;
    private CertificateClient certificateClient;
    private KeyStore keyStore;
    private String azureKeyVaultUri;

    protected AzureKeyVaultSecurityService() {
        try {
            new BouncyCastleHelper().checkProviderInstalled();
            initializeAzureKeyVaultClients();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
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

    private Certificate buildCertificate(KeyVaultCertificateWithPolicy keyVaultCertificate) {
        X509Certificate certificate = null;
        String certificateString = new String(keyVaultCertificate.getCer());
        if (certificateString != null) {
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                certificate = (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(keyVaultCertificate.getCer()));
            } catch (CertificateException ce) {
                log.error("Certificate error", ce);
                throw new RuntimeException(ce);
            }
        }
        return certificate;
    }

    private PrivateKey createPrivateKeyFromPem(String pemString, String keyType)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(pemString))) {
            String line = reader.readLine();
            if (line == null || !line.contains("BEGIN PRIVATE KEY")) {
                throw new IllegalArgumentException("No PRIVATE KEY found");
            }
            line = "";
            while (line != null) {
                if (line.contains("END PRIVATE KEY")) {
                    break;
                }
                builder.append(line);
                line = reader.readLine();
            }
        }
        byte[] bytes = Base64.getDecoder().decode(builder.toString());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        KeyFactory factory = KeyFactory.getInstance(keyType);
        return factory.generatePrivate(spec);
    }

    @Override
    public KeyStore getKeyStore() {
        try {
            if (keyStore == null) {
                String keyStoreType = System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_TYPE, SecurityConstants.KEYSTORE_TYPE);
                keyStore = KeyStore.getInstance(keyStoreType);
                String password = getKeyStorePassword();
                keyStore.load(null, password.toCharArray());
                String certAlias = System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS, SecurityConstants.ALIAS_SYM_PRIVATE_KEY);
                for (SecretProperties secret : secretClient.listPropertiesOfSecrets()) {
                    if (!certAlias.equals(secret.getName())) {
                        log.debug("Retrieving secret with name {}", secret.getName());
                        KeyVaultSecret secretWithValue = secretClient.getSecret(secret.getName(), secret.getVersion());
                        SecretKey mySecretKey = new SecretKeySpec(secretWithValue.getValue().getBytes(), "DES");
                        keyStore.setKeyEntry(convertToParameterName(secretWithValue.getName()), mySecretKey, password.toCharArray(), null);
                    }
                }
                KeyVaultCertificateWithPolicy certificate = certificateClient.getCertificate(certAlias);
                if (certificate.getPolicy().isExportable()) {
                    KeyVaultSecret secret = secretClient.getSecret(certAlias);
                    Certificate x509Certificate = buildCertificate(certificate);
                    if ("application/x-pkcs12".equals(secret.getProperties().getContentType())) {
                        try {
                            KeyStore localKeyStore = KeyStore.getInstance("PKCS12");
                            localKeyStore.load(
                                    new ByteArrayInputStream(Base64.getDecoder().decode(secret.getValue())), "".toCharArray());
                            String alias = localKeyStore.aliases().nextElement();
                            Key key = localKeyStore.getKey(alias, "".toCharArray());
                            keyStore.setKeyEntry(certAlias, key, password.toCharArray(), new Certificate[] { x509Certificate });
                        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException ex) {
                            log.error("Unable to decode key", ex);
                            throw new RuntimeException(ex);
                        }
                    } else if ("application/x-pem-file".equals(secret.getProperties().getContentType())) {
                        Key key = createPrivateKeyFromPem(secret.getValue(), certificate.getPolicy().getKeyType().toString());
                        keyStore.setKeyEntry(certAlias, key, password.toCharArray(), new Certificate[] { x509Certificate });
                    }
                }
            }
            return keyStore;
        } catch (RuntimeException re) {
            keyStore = null;
            throw re;
        } catch (Exception e) {
            keyStore = null;
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void saveKeyStore(KeyStore ks, String password) throws Exception {
        String certAlias = System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS, SecurityConstants.ALIAS_SYM_PRIVATE_KEY);
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            // Can't attempt to save the key for the self signed certificate
            if (!certAlias.equals(alias)) {
                Key key = ks.getKey(alias, password.toCharArray());
                setKeystoreEntry(alias, key);
            }
        }
    }

    @Override
    public String getKeystoreEntry(String alias) throws Exception {
        KeyVaultSecret retrievedSecret = secretClient.getSecret(convertToKeyVaultName(alias));
        return retrievedSecret.getValue();
    }

    @Override
    public void setKeystoreEntry(String alias, String value) throws Exception {
        KeyVaultSecret key = new KeyVaultSecret(convertToKeyVaultName(alias), value);
        secretClient.setSecret(key);
    }

    public void setKeystoreEntry(String alias, Key key) throws Exception {
        String value = new String(key.getEncoded());
        setKeystoreEntry(alias, value);
    }

    @Override
    public void deleteKeystoreEntry(String alias) {
        try {
            SyncPoller<DeletedSecret, Void> deleteSecretPoller = secretClient.beginDeleteSecret(convertToKeyVaultName(alias));
            // Secret is being deleted on server.
            deleteSecretPoller.waitForCompletion();
            secretClient.purgeDeletedSecret(convertToKeyVaultName(alias));
        } catch (ResourceNotFoundException e) {
            log.info("Trying to delete key {}, but did not exist in the vault.", alias);
        }
    }

    @Override
    public synchronized void installDefaultSslCert(String host) {
        host = host == null ? AppUtils.getHostName() : host;
        String alias = System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS, SecurityConstants.ALIAS_SYM_PRIVATE_KEY);
        KeyVaultCertificate cert = null;
        try {
            cert = certificateClient.getCertificate(convertToKeyVaultName(alias));
        } catch (ResourceNotFoundException e) {
            CertificatePolicy policy = new CertificatePolicy("Self", String.format("CN=%s, OU=SymmetricDS, O=JumpMind", host))
                    .setKeyReusable(true)
                    .setKeyType(CertificateKeyType.RSA)
                    .setKeySize(2048)
                    .setValidityInMonths(300);
            SyncPoller<CertificateOperation, KeyVaultCertificateWithPolicy> certificatePoller = certificateClient.beginCreateCertificate(convertToKeyVaultName(
                    alias), policy, true, new HashMap<>());
            certificatePoller.waitUntil(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
            cert = certificatePoller.getFinalResult();
            log.info("Successfully installed default ssl cert {}", cert.getName());
        }
    }

    @Override
    public synchronized void installSslCert(PrivateKeyEntry entry) {
        throw new NotImplementedException();
    }

    @Override
    public synchronized X509Certificate getCurrentSslCert() {
        String alias = System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS, SecurityConstants.ALIAS_SYM_PRIVATE_KEY);
        byte[] cer = certificateClient.getCertificate(convertToKeyVaultName(alias)).getCer();
        try (InputStream inStream = new ByteArrayInputStream(cer)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inStream);
            return cert;
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public synchronized String exportCurrentSslCert(boolean includePrivateKey) {
        throw new NotImplementedException();
    }

    @SuppressWarnings("rawtypes")
    private void initializeAzureKeyVaultClients() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        azureKeyVaultUri = System.getProperties().getProperty(SecurityConstants.AZURE_KEYVAULT_URI);
        String className = System.getProperties().getProperty(SecurityConstants.AZURE_CREDENTIAL_BUILDER_CLASSNAME);
        CredentialBuilderBase credentialBuilder;
        Method buildMethod;
        TokenCredential credential;
        if (className == null || className.trim().equals("")) {
            className = "com.azure.identity.DefaultAzureCredentialBuilder";
        }
        try {
            credentialBuilder = (CredentialBuilderBase) Class.forName(className).getDeclaredConstructor().newInstance();
            buildMethod = credentialBuilder.getClass().getMethod("build", (Class<?>[]) null);
            credential = (TokenCredential) buildMethod.invoke(credentialBuilder);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (secretClient == null) {
            secretClient = new SecretClientBuilder()
                    .vaultUrl(azureKeyVaultUri)
                    .credential(credential)
                    .buildClient();
        }
        if (keyClient == null) {
            keyClient = new KeyClientBuilder()
                    .vaultUrl(azureKeyVaultUri)
                    .credential(credential)
                    .buildClient();
        }
        if (certificateClient == null) {
            certificateClient = new CertificateClientBuilder()
                    .vaultUrl(azureKeyVaultUri)
                    .credential(credential)
                    .buildClient();
        }
    }

    private String convertToParameterName(String keyVaultName) {
        return StringUtils.replaceChars(keyVaultName, '-', '.');
    }

    private String convertToKeyVaultName(String parameterName) {
        return StringUtils.replaceChars(parameterName, '.', '-');
    }

    @Override
    protected void checkThatKeystoreFileExists() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
    }

    @Override
    protected void initializeSecretKey() {
        if (secretKey == null) {
            synchronized (SecurityService.class) {
                if (secretKey == null) {
                    try {
                        try {
                            String secret = secretClient.getSecret(convertToKeyVaultName(SecurityConstants.ALIAS_SYM_SECRET_KEY)).getValue();
                            secretKey = SerializationUtils.deserialize(Base64.getDecoder().decode(secret));
                        } catch (ResourceNotFoundException e) {
                            secretKey = getDefaultSecretKey();
                            String secret = Base64.getEncoder().encodeToString(SerializationUtils.serialize(secretKey));
                            secretClient.setSecret(convertToKeyVaultName(SecurityConstants.ALIAS_SYM_SECRET_KEY), secret);
                        }
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }
}
