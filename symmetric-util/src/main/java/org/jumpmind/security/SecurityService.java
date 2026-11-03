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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.net.ssl.KeyManagerFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @see ISecurityService
 */
public class SecurityService implements ISecurityService {
    protected Logger log = LoggerFactory.getLogger(getClass());
    /**
     * The key most recently fetched by any service in this JVM. Kept for subclasses outside this library that set or read it directly; this class uses the key
     * cached for its own provider instead.
     */
    protected static volatile SecretKey secretKey;
    private static final ConcurrentMap<Object, SecretKey> SECRET_KEYS_BY_IDENTITY = new ConcurrentHashMap<>();
    protected static String keyStoreFileName;
    protected static URL keyStoreURL;
    protected static volatile boolean hasInitKeyStore;
    protected static String trustStoreFileName;
    protected static URL trustStoreURL;
    protected static SecureRandom random;
    protected static volatile boolean isKeystoreInitialized;
    private final ISecretKeyProvider secretKeyProvider;
    private final ISecretStore secretStore;
    private final ITlsMaterialProvider tlsMaterialProvider;
    private final ICertificateManager certificateManager;
    private final IKeyStoreRepository keyStoreRepository;
    private final boolean keyStoreRepositorySupplied;
    private final boolean usesLocalKeyStore;
    private final boolean usesLocalTrustStore;
    private volatile SecretKey serviceSecretKey;
    static {
        keyStoreFileName = StringUtils.trimToNull(System.getProperty(SecurityConstants.SYSPROP_KEYSTORE));
        if (keyStoreFileName == null) {
            keyStoreURL = SecurityService.class.getClassLoader().getResource("/keystore");
            hasInitKeyStore = true;
        }
        trustStoreFileName = StringUtils.trimToNull(System.getProperty(SecurityConstants.SYSPROP_TRUSTSTORE));
        if (trustStoreFileName == null) {
            trustStoreURL = SecurityService.class.getClassLoader().getResource("/cacerts");
            if (trustStoreURL == null) {
                trustStoreFileName = System.getProperty("java.home") + File.separatorChar + "lib" + File.separatorChar + "security"
                        + File.separator + "cacerts";
            }
        }
        random = new SecureRandom();
    }

    protected SecurityService() {
        this(SecurityServiceParts.builder().build());
    }

    /**
     * @param parts
     *            the parts that replace the local keystore and truststore; parts left unset use them
     */
    protected SecurityService(SecurityServiceParts parts) {
        isKeystoreInitialized = false;
        keyStoreRepository = parts.getKeyStoreRepository().orElseGet(LocalKeyStoreRepository::new);
        keyStoreRepositorySupplied = parts.getKeyStoreRepository().isPresent();
        secretKeyProvider = parts.getSecretKeyProvider().orElseGet(this::newKeystoreSecretKeyProvider);
        secretStore = parts.getSecretStore().orElseGet(this::newKeystoreSecretStore);
        tlsMaterialProvider = parts.getTlsMaterialProvider().orElseGet(this::newKeystoreTlsMaterialProvider);
        certificateManager = parts.getCertificateManagerFactory().map(this::createCertificateManager).orElseGet(UnsupportedCertificateManager::new);
        usesLocalTrustStore = !parts.getTlsMaterialProvider().isPresent();
        usesLocalKeyStore = !keyStoreRepositorySupplied && (usesLocalTrustStore || !parts.getSecretKeyProvider().isPresent()
                || !parts.getSecretStore().isPresent() || parts.getCertificateManagerFactory().isPresent());
    }

    private ICertificateManager createCertificateManager(SecurityServiceParts.CertificateManagerFactory factory) {
        return Objects.requireNonNull(factory.create(keyStoreRepository, tlsMaterialProvider), "certificateManagerFactory returned null");
    }

    private KeystoreSecretKeyProvider newKeystoreSecretKeyProvider() {
        return new KeystoreSecretKeyProvider(keyStoreRepository, random);
    }

    private KeystoreSecretStore newKeystoreSecretStore() {
        return new KeystoreSecretStore(keyStoreRepository);
    }

    private KeystoreTlsMaterialProvider newKeystoreTlsMaterialProvider() {
        return new KeystoreTlsMaterialProvider(keyStoreRepository, new LocalTrustStoreRepository());
    }

    protected ISecretKeyProvider getSecretKeyProvider() {
        return secretKeyProvider;
    }

    protected ISecretStore getSecretStore() {
        return secretStore;
    }

    protected ITlsMaterialProvider getTlsMaterialProvider() {
        return tlsMaterialProvider;
    }

    protected ICertificateManager getCertificateManager() {
        return certificateManager;
    }

    /**
     * Fails fast when a deprecated keystore helper is called on a service whose secret key does not come from the local keystore, rather than quietly reading
     * or generating a keystore key the service never uses.
     */
    private KeystoreSecretKeyProvider getKeystoreSecretKeyProvider() {
        if (secretKeyProvider instanceof KeystoreSecretKeyProvider) {
            return (KeystoreSecretKeyProvider) secretKeyProvider;
        }
        throw new NotImplementedException(String.format("%s gets its secret key from %s, not the local keystore", getClass().getSimpleName(),
                secretKeyProvider.getClass().getSimpleName()));
    }

    @Override
    public synchronized void init() {
        if (isKeystoreInitialized) {
            log.debug("Already initialized. Key store={}", keyStoreURL);
            return;
        }
        if (usesLocalKeyStore) {
            requireKeyStoreFile();
        }
        if (usesLocalTrustStore) {
            requireTrustStoreFile();
        }
        long secureRandom = nextSecureLong();
        log.debug("Confirmed secure random is available. Result={}. Running keystore integrity check...", secureRandom);
        isKeystoreInitialized = validateKeystoreIntegrity();
    }

    private void requireKeyStoreFile() {
        if (keyStoreFileName == null) {
            log.debug("Using keystore from classpath {}", keyStoreURL);
            throw new RuntimeException("Keystore path not set! Please specify a keystore file using the system property -D"
                    + SecurityConstants.SYSPROP_KEYSTORE);
        }
        log.info("Using keystore from file {}", keyStoreFileName);
    }

    private void requireTrustStoreFile() {
        if (trustStoreFileName == null) {
            log.debug("Using truststore from classpath {}", trustStoreURL);
            throw new RuntimeException("Truststore path not set! Please specify a truststore file using the system property -D"
                    + SecurityConstants.SYSPROP_TRUSTSTORE);
        }
        log.info("Using truststore from file {}", trustStoreFileName);
    }

    @Override
    public boolean validateKeystoreIntegrity() {
        log.debug("Testing keystore integrity. Key store={}", keyStoreFileName);
        try {
            encrypt("keystore-integrity-test");
            return true;
        } catch (Exception ex) {
            log.debug("Failed to encrypt using current keystore! Key store=" + keyStoreFileName, ex);
            throw ex;
        }
    }

    public boolean isInitialized() {
        return isKeystoreInitialized;
    }

    @Override
    public KeyStore getTrustStore() {
        return tlsMaterialProvider.getTrustStore();
    }

    /**
     * @return the keystore from the repository this service was built with, or else from the local keystore file
     */
    @Override
    public KeyStore getKeyStore() {
        return keyStoreRepositorySupplied ? keyStoreRepository.load() : loadKeyStoreFile();
    }

    private KeyStore loadKeyStoreFile() {
        try {
            checkThatKeystoreFileExists();
            String keyStoreType = getKeyStoreType();
            KeyStore ks = KeyStore.getInstance(keyStoreType);
            if (keyStoreFileName != null) {
                log.debug("Loading keystore from file {}", keyStoreFileName);
                try (FileInputStream is = new FileInputStream(keyStoreFileName)) {
                    ks.load(is, getKeyStorePassword().toCharArray());
                }
            } else if (keyStoreURL != null) {
                log.debug("Loading keystore from classpath {}", keyStoreURL);
                try (InputStream is = keyStoreURL.openStream()) {
                    ks.load(is, getKeyStorePassword().toCharArray());
                }
            } else {
                log.debug("Loading keystore from memory");
                ks.load(null);
            }
            return ks;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected String getKeyStoreType() {
        String keyStoreType = System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_TYPE);
        if (keyStoreType == null) {
            byte[] buffer = new byte[2];
            if (keyStoreFileName != null) {
                try (InputStream is = new FileInputStream(keyStoreFileName)) {
                    is.read(buffer, 0, 2);
                } catch (IOException e) {
                }
            } else if (keyStoreURL != null) {
                try (InputStream is = keyStoreURL.openStream()) {
                    is.read(buffer, 0, 2);
                } catch (IOException e) {
                }
            }
            if (Byte.toUnsignedInt(buffer[0]) == 0xCE && Byte.toUnsignedInt(buffer[1]) == 0xCE) {
                keyStoreType = SecurityConstants.KEYSTORE_TYPE;
            } else if (Byte.toUnsignedInt(buffer[0]) == 0xFE && Byte.toUnsignedInt(buffer[1]) == 0xED) {
                keyStoreType = SecurityConstants.KEYSTORE_TYPE_JKS;
            }
        }
        if (keyStoreType == null) {
            keyStoreType = SecurityConstants.KEYSTORE_TYPE_PKCS12;
        }
        return keyStoreType;
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory() {
        return tlsMaterialProvider.getKeyManagerFactory();
    }

    @Override
    public void installTrustedCert(TrustedCertificateEntry entry) {
        tlsMaterialProvider.installTrustedCert(entry);
    }

    @Override
    public TrustedCertificateEntry createTrustedCert(byte[] content, String fileType, String alias, String password) {
        return certificateManager.createTrustedCert(content, fileType, alias, password);
    }

    @Override
    public void installDefaultSslCert(String host) {
        certificateManager.installDefaultSslCert(host);
    }

    @Override
    public void installDefaultSamlSslCert(String host, int lifetimeInDays) {
        certificateManager.installDefaultSamlSslCert(host, lifetimeInDays);
    }

    @Override
    public void installSslCert(KeyStore.PrivateKeyEntry entry) {
        certificateManager.installSslCert(entry);
    }

    @Override
    public void installSslCert(KeyStore.PrivateKeyEntry entry, String alias) {
        certificateManager.installSslCert(entry, alias);
    }

    @Override
    public KeyStore.PrivateKeyEntry createDefaultSslCert(String host) {
        return certificateManager.createDefaultSslCert(host);
    }

    @Override
    public KeyStore.PrivateKeyEntry createSslCert(byte[] content, String fileType, String alias, String password) {
        return certificateManager.createSslCert(content, fileType, alias, password);
    }

    @Override
    public X509Certificate getCurrentSslCert() {
        return certificateManager.getCurrentSslCert();
    }

    @Override
    public X509Certificate getCurrentSamlSslCert() {
        return certificateManager.getCurrentSamlSslCert();
    }

    @Override
    public String exportCurrentSslCert(boolean includePrivateKey) {
        return certificateManager.exportCurrentSslCert(includePrivateKey);
    }

    @Override
    public String exportTrustedCert(String alias) {
        return certificateManager.exportTrustedCert(alias);
    }

    @Override
    public void deleteCertificate(String alias) throws Exception {
        certificateManager.deleteCertificate(alias);
    }

    @Override
    public boolean supportsExportCertificate() {
        return certificateManager.supportsExportCertificate();
    }

    @Override
    public boolean supportsImportCertificate() {
        return certificateManager.supportsImportCertificate();
    }

    @Override
    public boolean supportsBackupCertificate() {
        return certificateManager.supportsBackupCertificate();
    }

    @Override
    public boolean supportsGenerateSelfSignedCertificate() {
        return certificateManager.supportsGenerateSelfSignedCertificate();
    }

    protected void checkThatKeystoreFileExists() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        if (!hasInitKeyStore) {
            synchronized (SecurityService.class) {
                if (!hasInitKeyStore && keyStoreFileName != null && !new File(keyStoreFileName).exists()) {
                    String keyStoreType = System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_TYPE, SecurityConstants.KEYSTORE_TYPE_PKCS12);
                    KeyStore ks = KeyStore.getInstance(keyStoreType);
                    ks.load(null, getKeyStorePassword().toCharArray());
                    try (FileOutputStream os = new FileOutputStream(keyStoreFileName)) {
                        ks.store(os, getKeyStorePassword().toCharArray());
                    }
                    hasInitKeyStore = true;
                }
            }
        }
    }

    @Override
    public String encrypt(String plainText) {
        try {
            byte[] bytes = plainText.getBytes(SecurityConstants.CHARSET);
            byte[] enc = getCipher(Cipher.ENCRYPT_MODE).doFinal(bytes);
            return new String(Base64.encodeBase64(enc), SecurityConstants.CHARSET);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String decrypt(String encText) {
        try {
            byte[] dec = Base64.decodeBase64(encText.getBytes(Charset.defaultCharset()));
            byte[] bytes = getCipher(Cipher.DECRYPT_MODE).doFinal(dec);
            return new String(bytes, SecurityConstants.CHARSET);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String obfuscate(String plainText) {
        return new String(Base64.encodeBase64(rot13(plainText).getBytes(Charset.defaultCharset())), Charset.defaultCharset());
    }

    @Override
    public String unobfuscate(String obfText) {
        return deobfuscate(obfText);
    }

    private static String deobfuscate(String obfText) {
        return new String(rot13(new String(Base64.decodeBase64(obfText.getBytes(Charset.defaultCharset())), Charset.defaultCharset())));
    }

    @Override
    public String unobfuscateIfNeeded(String systemPropertyName) {
        return unobfuscateSystemProperty(systemPropertyName);
    }

    private static String unobfuscateSystemProperty(String systemPropertyName) {
        String value = System.getProperty(systemPropertyName);
        if (value != null && value.startsWith(SecurityConstants.PREFIX_OBF)) {
            value = deobfuscate(value.substring(SecurityConstants.PREFIX_OBF.length()));
            System.setProperty(systemPropertyName, value);
        }
        return value;
    }

    private static String rot13(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'm') || (c >= 'A' && c <= 'M')) {
                c += 13;
            } else if ((c >= 'n' && c <= 'z') || (c >= 'N' && c <= 'Z')) {
                c -= 13;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public Cipher getCipher(int mode) throws Exception {
        initializeSecretKey();
        SecretKey key = getActiveSecretKey();
        Cipher cipher = Cipher.getInstance(key.getAlgorithm());
        initializeCipher(cipher, mode);
        if (log.isDebugEnabled()) {
            log.debug("Using {} algorithm {}-bit provided by {}.", cipher.getAlgorithm(),
                    key.getEncoded().length * 8, cipher.getProvider().getName());
        }
        return cipher;
    }

    protected void initializeCipher(Cipher cipher, int mode) throws Exception {
        CipherInitializer.initialize(cipher, mode, getActiveSecretKey(), random);
    }

    /**
     * @return the key fetched for this service's provider, or the shared {@link #secretKey} when a subclass's own {@link #initializeSecretKey()} set only that
     */
    private SecretKey getActiveSecretKey() {
        return serviceSecretKey != null ? serviceSecretKey : secretKey;
    }

    protected String getTrustStorePassword() {
        return StringUtils.defaultIfBlank(unobfuscateIfNeeded(SecurityConstants.SYSPROP_TRUSTSTORE_PASSWORD), SecurityConstants.KEYSTORE_PASSWORD);
    }

    protected String getKeyStorePassword() {
        return getConfiguredKeyStorePassword();
    }

    /**
     * @return the password from {@link SecurityConstants#SYSPROP_KEYSTORE_PASSWORD}, unobfuscated, or the default password when it is not set
     */
    static String getConfiguredKeyStorePassword() {
        return StringUtils.defaultIfBlank(unobfuscateSystemProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD), SecurityConstants.KEYSTORE_PASSWORD);
    }

    /**
     * @deprecated Overriding this no longer changes which algorithm {@link #getKeyManagerFactory()} uses. Supply an {@link ITlsMaterialProvider} instead.
     * @throws NotImplementedException
     *             when TLS material does not come from the local keystore
     */
    @Deprecated
    protected String getKeyManagerFactoryAlgorithm() {
        if (tlsMaterialProvider instanceof KeystoreTlsMaterialProvider) {
            return ((KeystoreTlsMaterialProvider) tlsMaterialProvider).getKeyManagerFactoryAlgorithm();
        }
        throw new NotImplementedException(String.format("%s gets TLS material from %s, not the local keystore", getClass().getSimpleName(),
                tlsMaterialProvider.getClass().getSimpleName()));
    }

    /**
     * Fetches the key from this service's provider once per JVM for each {@link ISecretKeyProvider#getKeyIdentity()}, so services built with different
     * providers never share a key.
     */
    protected void initializeSecretKey() throws Exception {
        if (serviceSecretKey == null) {
            synchronized (SecurityService.class) {
                if (serviceSecretKey == null) {
                    Object identity = secretKeyProvider.getKeyIdentity();
                    SecretKey key = SECRET_KEYS_BY_IDENTITY.get(identity);
                    if (key == null) {
                        key = secretKeyProvider.getSecretKey();
                        SECRET_KEYS_BY_IDENTITY.put(identity, key);
                    }
                    serviceSecretKey = key;
                    secretKey = key;
                }
            }
        }
    }

    static void clearSecretKeyCache() {
        SECRET_KEYS_BY_IDENTITY.clear();
        secretKey = null;
    }

    /**
     * @deprecated Overriding this no longer changes how the secret key is created. Supply an {@link ISecretKeyProvider} instead.
     * @throws NotImplementedException
     *             when the secret key does not come from the local keystore
     */
    @Deprecated
    protected String resolveConfiguredSeed() {
        return getKeystoreSecretKeyProvider().resolveConfiguredSeed();
    }

    /**
     * @deprecated Overriding this no longer changes how the secret key is created. Supply an {@link ISecretKeyProvider} instead.
     * @throws NotImplementedException
     *             when the secret key does not come from the local keystore
     */
    @Deprecated
    protected SecretKey createSecretKeyFromSeed(String base64Seed) {
        return getKeystoreSecretKeyProvider().createSecretKeyFromSeed(base64Seed);
    }

    /**
     * @deprecated Overriding this no longer changes how the secret key is created. Supply an {@link ISecretKeyProvider} instead.
     * @throws NotImplementedException
     *             when the secret key does not come from the local keystore
     */
    @Deprecated
    protected boolean isValidAesKeyLength(byte[] keyBytes) {
        return getKeystoreSecretKeyProvider().isValidAesKeyLength(keyBytes);
    }

    @Override
    public String getKeystoreEntry(String alias) throws Exception {
        return secretStore.getEntry(alias);
    }

    @Override
    public void setKeystoreEntry(String alias, String value) throws Exception {
        secretStore.setEntry(alias, value);
    }

    @Override
    public void deleteKeystoreEntry(String alias) throws Exception {
        secretStore.deleteEntry(alias);
    }

    @Override
    public String nextSecureHexString(int len) {
        if (len <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        int maxInt = SecurityConstants.PASSWORD_CHARS.length();
        char[] password = new char[len];
        for (int i = 0; i < len; i++) {
            password[i] = SecurityConstants.PASSWORD_CHARS.charAt(random.nextInt(maxInt));
        }
        return new String(password);
    }

    @Override
    public long nextSecureLong() {
        return random.nextLong();
    }

    /**
     * @deprecated Overriding this no longer changes how the secret key is created. Supply an {@link ISecretKeyProvider} instead.
     * @throws NotImplementedException
     *             when the secret key does not come from the local keystore
     */
    @Deprecated
    protected SecretKey getDefaultSecretKey() throws Exception {
        return getKeystoreSecretKeyProvider().generateDefaultSecretKey();
    }

    /**
     * @deprecated Overriding this no longer changes how the secret key is created. Supply an {@link ISecretKeyProvider} instead.
     * @throws NotImplementedException
     *             when the secret key does not come from the local keystore
     */
    @Deprecated
    protected byte[] getBytes(int byteSize) {
        return getKeystoreSecretKeyProvider().getKeyBytes(byteSize);
    }

    @Override
    public void saveTrustStore(KeyStore ks) throws Exception {
        tlsMaterialProvider.saveTrustStore(ks);
    }

    protected void saveKeyStore(KeyStore ks, String password) throws Exception {
        if (keyStoreRepositorySupplied) {
            keyStoreRepository.save(ks);
        } else if (keyStoreFileName != null) {
            log.info("Saving keystore {}", keyStoreFileName);
            try {
                FileUtils.copyFile(new File(keyStoreFileName), new File(keyStoreFileName + ".bak"));
            } catch (IOException e) {
                log.warn("Unable to backup keystore: ", e.getMessage());
            }
            try (FileOutputStream os = new FileOutputStream(keyStoreFileName)) {
                ks.store(os, password.toCharArray());
            }
        }
    }

    /**
     * Adapts this service's overridable keystore methods to {@link IKeyStoreRepository}, so subclasses that override {@link #getKeyStore()} or
     * {@link #saveKeyStore(KeyStore, String)} still control where the secret key, keystore entries and TLS key material are read and stored.
     */
    private class LocalKeyStoreRepository implements IKeyStoreRepository {
        @Override
        public KeyStore load() {
            return getKeyStore();
        }

        @Override
        public void save(KeyStore keyStore) throws Exception {
            saveKeyStore(keyStore, getKeyStorePassword());
        }

        @Override
        public String getPassword() {
            return getKeyStorePassword();
        }

        @Override
        public boolean isPersistent() {
            return keyStoreFileName != null;
        }
    }

    /**
     * Reads the truststore from {@link SecurityConstants#SYSPROP_TRUSTSTORE}, or from the classpath when that is not set, and writes it back to the file.
     */
    private class LocalTrustStoreRepository implements IKeyStoreRepository {
        @Override
        public KeyStore load() {
            try {
                KeyStore ks = KeyStore.getInstance(System.getProperty(SecurityConstants.SYSPROP_TRUSTSTORE_TYPE, KeyStore.getDefaultType()));
                if (trustStoreFileName != null) {
                    try (FileInputStream is = new FileInputStream(trustStoreFileName)) {
                        ks.load(is, getPassword().toCharArray());
                    }
                } else if (trustStoreURL != null) {
                    try (InputStream is = trustStoreURL.openStream()) {
                        ks.load(is, getPassword().toCharArray());
                    }
                }
                return ks;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void save(KeyStore trustStore) throws Exception {
            if (trustStoreFileName != null) {
                log.info("Saving truststore {}", trustStoreFileName);
                try (FileOutputStream os = new FileOutputStream(trustStoreFileName)) {
                    trustStore.store(os, getPassword().toCharArray());
                }
            }
        }

        @Override
        public String getPassword() {
            return getTrustStorePassword();
        }

        @Override
        public boolean isPersistent() {
            return trustStoreFileName != null;
        }
    }
}