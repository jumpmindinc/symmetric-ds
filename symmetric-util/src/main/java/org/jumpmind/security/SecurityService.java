/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
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
    protected static volatile SecretKey secretKey;
    protected static String keyStoreFileName;
    protected static URL keyStoreURL;
    protected static volatile boolean hasInitKeyStore;
    protected static String trustStoreFileName;
    protected static URL trustStoreURL;
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
    }

    protected SecurityService() {
    }

    @Override
    public synchronized void init() {
    }

    @Override
    public KeyStore getTrustStore() {
        try {
            String keyStoreType = System.getProperty(SecurityConstants.SYSPROP_TRUSTSTORE_TYPE, KeyStore.getDefaultType());
            KeyStore ks = KeyStore.getInstance(keyStoreType);
            if (trustStoreFileName != null) {
                try (FileInputStream is = new FileInputStream(trustStoreFileName)) {
                    ks.load(is, getTrustStorePassword().toCharArray());
                }
            } else if (trustStoreURL != null) {
                try (InputStream is = trustStoreURL.openStream()) {
                    ks.load(is, getTrustStorePassword().toCharArray());
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
    public KeyStore getKeyStore() {
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
        KeyManagerFactory keyManagerFactory;
        try {
            keyManagerFactory = KeyManagerFactory.getInstance(getKeyManagerFactoryAlgorithm());
            keyManagerFactory.init(getKeyStore(), getKeyStorePassword().toCharArray());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return keyManagerFactory;
    }

    @Override
    public void installTrustedCert(TrustedCertificateEntry entry) {
        try {
            KeyStore keyStore = getTrustStore();
            String alias = keyStore.getCertificateAlias(entry.getTrustedCertificate());
            if (alias == null) {
                alias = String.valueOf(entry.getTrustedCertificate().hashCode());
                keyStore.setEntry(alias, entry, null);
                log.info("Installing trusted certificate: {}",
                        ((X509Certificate) entry.getTrustedCertificate()).getSubjectX500Principal().getName());
                saveTrustStore(keyStore);
            } else {
                log.info("Trusted certificate already installed: {}",
                        ((X509Certificate) entry.getTrustedCertificate()).getSubjectX500Principal().getName());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TrustedCertificateEntry createTrustedCert(byte[] content, String fileType, String alias, String password) {
        return null;
    }

    @Override
    public void installDefaultSslCert(String host) {
    }

    @Override
    public void installDefaultSamlSslCert(String host, int lifetimeInDays) {
    }

    @Override
    public void installSslCert(KeyStore.PrivateKeyEntry entry) {
        throw new NotImplementedException();
    }

    @Override
    public void installSslCert(KeyStore.PrivateKeyEntry entry, String alias) {
        throw new NotImplementedException();
    }

    @Override
    public KeyStore.PrivateKeyEntry createDefaultSslCert(String host) {
        throw new NotImplementedException();
    }

    @Override
    public KeyStore.PrivateKeyEntry createSslCert(byte[] content, String fileType, String alias, String password) {
        throw new NotImplementedException();
    }

    @Override
    public X509Certificate getCurrentSslCert() {
        throw new NotImplementedException();
    }

    @Override
    public X509Certificate getCurrentSamlSslCert() {
        throw new NotImplementedException();
    }

    @Override
    public String exportCurrentSslCert(boolean includePrivateKey) {
        throw new NotImplementedException();
    }

    @Override
    public String exportTrustedCert(String alias) {
        throw new NotImplementedException();
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
            checkThatKeystoreFileExists();
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
            checkThatKeystoreFileExists();
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
        return new String(rot13(new String(Base64.decodeBase64(obfText.getBytes(Charset.defaultCharset())), Charset.defaultCharset())));
    }

    @Override
    public String unobfuscateIfNeeded(String systemPropertyName) {
        String value = System.getProperty(systemPropertyName);
        if (value != null && value.startsWith(SecurityConstants.PREFIX_OBF)) {
            value = unobfuscate(value.substring(SecurityConstants.PREFIX_OBF.length()));
            System.setProperty(systemPropertyName, value);
        }
        return value;
    }

    private String rot13(String text) {
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
        Cipher cipher = Cipher.getInstance(secretKey.getAlgorithm());
        initializeCipher(cipher, mode);
        if (log.isDebugEnabled()) {
            log.debug("Using {} algorithm {}-bit provided by {}.", cipher.getAlgorithm(),
                    secretKey.getEncoded().length * 8, cipher.getProvider().getName());
        }
        return cipher;
    }

    protected void initializeCipher(Cipher cipher, int mode) throws Exception {
        AlgorithmParameterSpec paramSpec = Cipher.getMaxAllowedParameterSpec(cipher.getAlgorithm());
        if (paramSpec instanceof PBEParameterSpec || cipher.getAlgorithm().startsWith("PBE")) {
            paramSpec = new PBEParameterSpec(SecurityConstants.SALT, SecurityConstants.ITERATION_COUNT);
            cipher.init(mode, secretKey, paramSpec);
        } else if (paramSpec instanceof IvParameterSpec) {
            paramSpec = new IvParameterSpec(SecurityConstants.SALT);
            cipher.init(mode, secretKey, paramSpec);
        } else {
            cipher.init(mode, secretKey);
        }
    }

    protected String getTrustStorePassword() {
        return StringUtils.defaultIfBlank(unobfuscateIfNeeded(SecurityConstants.SYSPROP_TRUSTSTORE_PASSWORD), SecurityConstants.KEYSTORE_PASSWORD);
    }

    protected String getKeyStorePassword() {
        return StringUtils.defaultIfBlank(unobfuscateIfNeeded(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD), SecurityConstants.KEYSTORE_PASSWORD);
    }

    protected String getKeyManagerFactoryAlgorithm() {
        String algorithm = System.getProperty(SecurityConstants.SYSPROP_KEY_MANAGER_FACTORY_ALGORITHM);
        if (algorithm == null) {
            algorithm = KeyManagerFactory.getDefaultAlgorithm();
        }
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        return algorithm;
    }

    protected void initializeSecretKey() throws Exception {
        if (secretKey == null) {
            synchronized (SecurityService.class) {
                if (secretKey == null) {
                    String password = getKeyStorePassword();
                    KeyStore.ProtectionParameter param = new KeyStore.PasswordProtection(password.toCharArray());
                    KeyStore ks = getKeyStore();
                    KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) ks.getEntry(SecurityConstants.ALIAS_SYM_SECRET_KEY, param);
                    if (entry == null) {
                        String seed = resolveConfiguredSeed();
                        SecretKey key = StringUtils.isNotBlank(seed) ? createSecretKeyFromSeed(seed) : getDefaultSecretKey();
                        entry = new KeyStore.SecretKeyEntry(key);
                        ks.setEntry(SecurityConstants.ALIAS_SYM_SECRET_KEY, entry, param);
                        saveKeyStore(ks, password);
                    } else {
                        log.debug("Retrieving secret key");
                    }
                    secretKey = entry.getSecretKey();
                    log.info("Initialized with {} {}-bit", entry.getSecretKey().getAlgorithm(), entry.getSecretKey().getEncoded().length * 8);
                }
            }
        }
    }

    protected String resolveConfiguredSeed() {
        String seed = System.getProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED);
        if (StringUtils.isBlank(seed)) {
            String legacy = System.getProperty(SecurityConstants.ALIAS_SYM_SECRET_KEY);
            if (StringUtils.isNotBlank(legacy)) {
                log.warn("System property -D{} as a key seed is deprecated; use -D{} instead",
                        SecurityConstants.ALIAS_SYM_SECRET_KEY, SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED);
                seed = legacy;
            }
        }
        return seed;
    }

    protected SecretKey createSecretKeyFromSeed(String base64Seed) {
        byte[] keyBytes = Base64.decodeBase64(base64Seed);
        log.info("Using configured secret key");
        return new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String getKeystoreEntry(String alias) throws Exception {
        String password = getKeyStorePassword();
        KeyStore ks = getKeyStore();
        Key entry = ks.getKey(alias, password.toCharArray());
        return entry == null ? null : new String(entry.getEncoded());
    }

    @Override
    public void setKeystoreEntry(String alias, String value) throws Exception {
        String password = getKeyStorePassword();
        SecretKey mySecretKey = new SecretKeySpec(value.getBytes(), "AES");
        KeyStore ks = getKeyStore();
        ks.setKeyEntry(alias, mySecretKey, password.toCharArray(), null);
        saveKeyStore(ks, password);
    }

    @Override
    public void deleteKeystoreEntry(String alias) throws Exception {
        String password = getKeyStorePassword();
        KeyStore ks = getKeyStore();
        try {
            ks.deleteEntry(alias);
        } catch (KeyStoreException e) {
            log.info("Unable to delete keystore entry with name {}", alias);
        }
        saveKeyStore(ks, password);
    }

    @Override
    public String nextSecureHexString(int len) {
        if (len <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        SecureRandom random = new SecureRandom();
        int maxInt = SecurityConstants.PASSWORD_CHARS.length();
        char[] password = new char[len];
        for (int i = 0; i < len; i++) {
            password[i] = SecurityConstants.PASSWORD_CHARS.charAt(random.nextInt(maxInt));
        }
        return new String(password);
    }

    protected SecretKey getDefaultSecretKey() throws Exception {
        for (int i = 0; i < SecurityConstants.CIPHERS.length; i++) {
            try {
                if (SecurityConstants.CIPHERS[i].startsWith("DESede")) {
                    SecretKeyFactory kf = SecretKeyFactory.getInstance(SecurityConstants.KEYSPECS[i]);
                    secretKey = kf.generateSecret(new DESedeKeySpec(getBytes(SecurityConstants.BYTESIZES[i])));
                } else {
                    secretKey = new SecretKeySpec(getBytes(SecurityConstants.BYTESIZES[i]), SecurityConstants.KEYSPECS[i]);
                }
                initializeCipher(Cipher.getInstance(SecurityConstants.CIPHERS[i]), Cipher.ENCRYPT_MODE);
                log.info("Generated secret key using {} {}", SecurityConstants.CIPHERS[i],
                        SecurityConstants.BYTESIZES[i] * 8);
                break;
            } catch (Exception e) {
                log.debug("Cannot use {} {}-bit because: {}", SecurityConstants.CIPHERS[i],
                        SecurityConstants.BYTESIZES[i] * 8, e.getMessage());
            }
        }
        return secretKey;
    }

    protected byte[] getBytes(int byteSize) {
        byte[] bytes = new byte[byteSize];
        if (keyStoreFileName != null) {
            log.info("Using random bytes for secret key");
            SecureRandom random = new SecureRandom();
            random.nextBytes(bytes);
        } else {
            log.info("Using keystore password as bytes for secret key");
            byte[] password = getKeyStorePassword().getBytes(Charset.defaultCharset());
            for (int i = 0; i < byteSize; i++) {
                bytes[i] = password[i < password.length ? i : password.length - 1];
            }
        }
        return bytes;
    }

    @Override
    public void saveTrustStore(KeyStore ks) throws Exception {
        if (trustStoreFileName != null) {
            log.info("Saving truststore {}", trustStoreFileName);
            try (FileOutputStream os = new FileOutputStream(trustStoreFileName)) {
                ks.store(os, getTrustStorePassword().toCharArray());
            }
        }
    }

    protected void saveKeyStore(KeyStore ks, String password) throws Exception {
        if (keyStoreFileName != null) {
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
}