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

import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the secret key from the {@link SecurityConstants#ALIAS_SYM_SECRET_KEY} entry of the local keystore. When the entry is missing, a key is created from
 * {@link SecurityConstants#SYSPROP_CLUSTER_KEYSTORE_SEED} if set, otherwise generated, and then saved to the keystore.
 */
public class KeystoreSecretKeyProvider implements ISecretKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(KeystoreSecretKeyProvider.class);
    private final IKeyStoreRepository keyStoreRepository;
    private final SecureRandom random;

    public KeystoreSecretKeyProvider(IKeyStoreRepository keyStoreRepository, SecureRandom random) {
        this.keyStoreRepository = keyStoreRepository;
        this.random = random;
    }

    @Override
    public SecretKey getSecretKey() throws Exception {
        KeyStore.ProtectionParameter param = new KeyStore.PasswordProtection(keyStoreRepository.getPassword().toCharArray());
        KeyStore ks = keyStoreRepository.load();
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) ks.getEntry(SecurityConstants.ALIAS_SYM_SECRET_KEY, param);
        if (entry == null) {
            entry = new KeyStore.SecretKeyEntry(createSecretKey());
            ks.setEntry(SecurityConstants.ALIAS_SYM_SECRET_KEY, entry, param);
            keyStoreRepository.save(ks);
        } else {
            log.debug("Retrieving secret key");
        }
        SecretKey key = entry.getSecretKey();
        log.info("Initialized with {} {}-bit", key.getAlgorithm(), key.getEncoded().length * 8);
        return key;
    }

    private SecretKey createSecretKey() {
        String seed = resolveConfiguredSeed();
        return StringUtils.isNotBlank(seed) ? createSecretKeyFromSeed(seed) : generateDefaultSecretKey();
    }

    String resolveConfiguredSeed() {
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

    SecretKey createSecretKeyFromSeed(String base64Seed) {
        byte[] keyBytes = Base64.decodeBase64(base64Seed);
        if (!isValidAesKeyLength(keyBytes)) {
            log.error("Invalid value for system property {}: expected a Base64-encoded 16, 24, or 32-byte AES key, but decoded to {} bytes",
                    SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED, keyBytes.length);
            System.exit(1);
        }
        if (log.isDebugEnabled()) {
            log.debug("Using configured secret key. Length={} bytes", keyBytes.length);
        } else {
            log.info("Using configured secret key");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    boolean isValidAesKeyLength(byte[] keyBytes) {
        return keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32;
    }

    /**
     * Tries each of {@link SecurityConstants#CIPHERS} in order of preference and returns a key for the first one this JVM supports.
     */
    public SecretKey generateDefaultSecretKey() {
        SecretKey candidate = null;
        for (int i = 0; i < SecurityConstants.CIPHERS.length; i++) {
            try {
                candidate = createCandidateKey(SecurityConstants.KEYSPECS[i], SecurityConstants.CIPHERS[i], SecurityConstants.BYTESIZES[i]);
                CipherInitializer.initialize(Cipher.getInstance(SecurityConstants.CIPHERS[i]), Cipher.ENCRYPT_MODE, candidate, random);
                log.info("Generated secret key using {} {}", SecurityConstants.CIPHERS[i], SecurityConstants.BYTESIZES[i] * 8);
                break;
            } catch (Exception e) {
                log.debug("Cannot use {} {}-bit because: {}", SecurityConstants.CIPHERS[i], SecurityConstants.BYTESIZES[i] * 8, e.getMessage());
            }
        }
        return candidate;
    }

    private SecretKey createCandidateKey(String keySpec, String cipherName, int byteSize) throws Exception {
        if (cipherName.startsWith("DESede")) {
            return SecretKeyFactory.getInstance(keySpec).generateSecret(new DESedeKeySpec(getKeyBytes(byteSize)));
        }
        return new SecretKeySpec(getKeyBytes(byteSize), keySpec);
    }

    byte[] getKeyBytes(int byteSize) {
        byte[] bytes = new byte[byteSize];
        if (keyStoreRepository.isPersistent()) {
            log.info("Using random bytes for secret key");
            random.nextBytes(bytes);
        } else {
            log.info("Using keystore password as bytes for secret key");
            byte[] password = keyStoreRepository.getPassword().getBytes(Charset.defaultCharset());
            for (int i = 0; i < byteSize; i++) {
                bytes[i] = password[i < password.length ? i : password.length - 1];
            }
        }
        return bytes;
    }
}
