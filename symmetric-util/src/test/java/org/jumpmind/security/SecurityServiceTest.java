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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class SecurityServiceTest {
    private static final ISecretKeyProvider FIXED_KEY_PROVIDER = () -> new SecretKeySpec(new byte[32], "AES");

    private static class TestSecurityService extends SecurityService {
        private TestSecurityService() {
        }

        private TestSecurityService(ISecretKeyProvider secretKeyProvider) {
            super(secretKeyProvider);
        }

        private static void clearCachedSecretKey() {
            secretKey = null;
        }

        private static SecretKey getCachedSecretKey() {
            return secretKey;
        }
    }

    private static class InMemoryKeyStoreSecurityService extends SecurityService {
        private final KeyStore keyStore;
        private KeyStore savedKeyStore;
        private String savedPassword;
        private int saveCount;

        private InMemoryKeyStoreSecurityService() throws Exception {
            keyStore = KeyStore.getInstance(SecurityConstants.KEYSTORE_TYPE_PKCS12);
            keyStore.load(null, getKeyStorePassword().toCharArray());
        }

        @Override
        public KeyStore getKeyStore() {
            return keyStore;
        }

        @Override
        protected void saveKeyStore(KeyStore ks, String password) {
            savedKeyStore = ks;
            savedPassword = password;
            saveCount++;
        }
    }

    @BeforeEach
    void setUp() {
        TestSecurityService.clearCachedSecretKey();
    }

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED);
        System.clearProperty(SecurityConstants.ALIAS_SYM_SECRET_KEY);
        TestSecurityService.clearCachedSecretKey();
    }

    @Test
    void testResolveConfiguredSeed_returnsClusterKeystoreSeed() {
        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte) 0xAA);
        String seed = Base64.encodeBase64String(keyBytes);
        System.setProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED, seed);
        assertEquals(seed, new TestSecurityService().resolveConfiguredSeed());
    }

    @Test
    void testResolveConfiguredSeed_fallsBackToLegacySymSecret() {
        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte) 0xBB);
        String seed = Base64.encodeBase64String(keyBytes);
        System.setProperty(SecurityConstants.ALIAS_SYM_SECRET_KEY, seed);
        assertEquals(seed, new TestSecurityService().resolveConfiguredSeed());
    }

    @Test
    void testResolveConfiguredSeed_canonicalTakesPrecedenceOverLegacy() {
        byte[] canonicalBytes = new byte[32];
        Arrays.fill(canonicalBytes, (byte) 0xAA);
        byte[] legacyBytes = new byte[32];
        Arrays.fill(legacyBytes, (byte) 0xBB);
        String canonical = Base64.encodeBase64String(canonicalBytes);
        String legacy = Base64.encodeBase64String(legacyBytes);
        System.setProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED, canonical);
        System.setProperty(SecurityConstants.ALIAS_SYM_SECRET_KEY, legacy);
        assertEquals(canonical, new TestSecurityService().resolveConfiguredSeed());
    }

    @Test
    void testResolveConfiguredSeed_returnsNullWhenNeitherSet() {
        assertNull(new TestSecurityService().resolveConfiguredSeed());
    }

    @Test
    void testCreateSecretKeyFromSeed_decodesBase64AndCreatesAesKey() {
        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte) 0x42);
        String seed = Base64.encodeBase64String(keyBytes);
        SecretKey key = new TestSecurityService().createSecretKeyFromSeed(seed);
        assertEquals("AES", key.getAlgorithm());
        assertArrayEquals(keyBytes, key.getEncoded());
    }

    @Test
    void testIsValidAesKeyLength_acceptsValidAesSizes() {
        TestSecurityService securityService = new TestSecurityService();
        assertEquals(true, securityService.isValidAesKeyLength(new byte[16]));
        assertEquals(true, securityService.isValidAesKeyLength(new byte[24]));
        assertEquals(true, securityService.isValidAesKeyLength(new byte[32]));
    }

    @Test
    void testIsValidAesKeyLength_rejectsInvalidSizes() {
        TestSecurityService securityService = new TestSecurityService();
        assertEquals(false, securityService.isValidAesKeyLength(new byte[0]));
        assertEquals(false, securityService.isValidAesKeyLength(new byte[8]));
        assertEquals(false, securityService.isValidAesKeyLength(new byte[15]));
        assertEquals(false, securityService.isValidAesKeyLength(new byte[20]));
        assertEquals(false, securityService.isValidAesKeyLength(new byte[33]));
    }

    @Test
    void nextSecureLong_returnsVaryingValues() {
        TestSecurityService securityService = new TestSecurityService();
        long a = securityService.nextSecureLong();
        long b = securityService.nextSecureLong();
        assertNotEquals(a, b);
    }

    @Test
    void testEncrypt_withInjectedSecretKeyProvider() {
        TestSecurityService securityService = new TestSecurityService(() -> new SecretKeySpec(new byte[32], "AES"));
        String encrypted = securityService.encrypt("secret value");
        assertNotEquals("secret value", encrypted);
        assertEquals("secret value", securityService.decrypt(encrypted));
    }

    @Test
    void testGetCipher_asksProviderOnlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TestSecurityService securityService = new TestSecurityService(() -> {
            calls.incrementAndGet();
            return new SecretKeySpec(new byte[16], "AES");
        });
        securityService.getCipher(Cipher.ENCRYPT_MODE);
        securityService.getCipher(Cipher.DECRYPT_MODE);
        assertEquals(1, calls.get());
    }

    @Test
    void testGetCipher_throwsWhenProviderFails() {
        TestSecurityService securityService = new TestSecurityService(() -> {
            throw new IllegalStateException("key service unavailable");
        });
        assertThrows(IllegalStateException.class, () -> securityService.getCipher(Cipher.ENCRYPT_MODE));
    }

    @Test
    void testGetSecretKeyProvider_returnsInjectedProvider() {
        assertSame(FIXED_KEY_PROVIDER, new TestSecurityService(FIXED_KEY_PROVIDER).getSecretKeyProvider());
    }

    @Test
    void testGetSecretKeyProvider_defaultsToKeystoreProvider() {
        assertInstanceOf(KeystoreSecretKeyProvider.class, new TestSecurityService().getSecretKeyProvider());
    }

    @Test
    void testGetDefaultSecretKey_withKeystoreProvider() throws Exception {
        assertNotNull(new TestSecurityService().getDefaultSecretKey());
    }

    @Test
    void testGetDefaultSecretKey_throwsWithNonKeystoreProvider() {
        TestSecurityService securityService = new TestSecurityService(FIXED_KEY_PROVIDER);
        assertThrows(NotImplementedException.class, () -> securityService.getDefaultSecretKey());
    }

    @Test
    void testResolveConfiguredSeed_throwsWithNonKeystoreProvider() {
        TestSecurityService securityService = new TestSecurityService(FIXED_KEY_PROVIDER);
        assertThrows(NotImplementedException.class, () -> securityService.resolveConfiguredSeed());
    }

    @Test
    void testCreateSecretKeyFromSeed_throwsWithNonKeystoreProvider() {
        TestSecurityService securityService = new TestSecurityService(FIXED_KEY_PROVIDER);
        assertThrows(NotImplementedException.class, () -> securityService.createSecretKeyFromSeed(Base64.encodeBase64String(new byte[16])));
    }

    @Test
    void testIsValidAesKeyLength_throwsWithNonKeystoreProvider() {
        TestSecurityService securityService = new TestSecurityService(FIXED_KEY_PROVIDER);
        assertThrows(NotImplementedException.class, () -> securityService.isValidAesKeyLength(new byte[16]));
    }

    @Test
    void testGetBytes_throwsWithNonKeystoreProvider() {
        TestSecurityService securityService = new TestSecurityService(FIXED_KEY_PROVIDER);
        assertThrows(NotImplementedException.class, () -> securityService.getBytes(16));
    }

    @Test
    void testGetCipher_withDefaultProviderReadsKeyFromOverriddenGetKeyStore() throws Exception {
        InMemoryKeyStoreSecurityService securityService = new InMemoryKeyStoreSecurityService();
        byte[] keyBytes = new byte[16];
        Arrays.fill(keyBytes, (byte) 0x11);
        securityService.keyStore.setEntry(SecurityConstants.ALIAS_SYM_SECRET_KEY, new KeyStore.SecretKeyEntry(new SecretKeySpec(keyBytes, "AES")),
                new KeyStore.PasswordProtection(securityService.getKeyStorePassword().toCharArray()));
        securityService.getCipher(Cipher.ENCRYPT_MODE);
        assertArrayEquals(keyBytes, TestSecurityService.getCachedSecretKey().getEncoded());
        assertEquals(0, securityService.saveCount);
    }

    @Test
    void testGetCipher_withDefaultProviderSavesNewKeyThroughOverriddenSaveKeyStore() throws Exception {
        InMemoryKeyStoreSecurityService securityService = new InMemoryKeyStoreSecurityService();
        securityService.getCipher(Cipher.ENCRYPT_MODE);
        assertEquals(1, securityService.saveCount);
        assertTrue(securityService.savedKeyStore.containsAlias(SecurityConstants.ALIAS_SYM_SECRET_KEY));
        assertEquals(securityService.getKeyStorePassword(), securityService.savedPassword);
    }
}
