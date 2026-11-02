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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeystoreSecretKeyProviderTest {
    private InMemoryKeyStoreRepository repository;
    private KeystoreSecretKeyProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        repository = InMemoryKeyStoreRepository.fileBased();
        provider = new KeystoreSecretKeyProvider(repository, new SecureRandom());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED);
        System.clearProperty(SecurityConstants.ALIAS_SYM_SECRET_KEY);
    }

    @Test
    void testGetSecretKey_returnsExistingEntryWithoutSaving() throws Exception {
        SecretKey existing = new SecretKeySpec(new byte[32], "AES");
        repository.load().setEntry(SecurityConstants.ALIAS_SYM_SECRET_KEY, new KeyStore.SecretKeyEntry(existing),
                new KeyStore.PasswordProtection(InMemoryKeyStoreRepository.PASSWORD.toCharArray()));
        SecretKey key = provider.getSecretKey();
        assertArrayEquals(existing.getEncoded(), key.getEncoded());
        assertEquals(0, repository.getSaveCount());
    }

    @Test
    void testGetSecretKey_whenMissingCreatesFromSeedAndSaves() throws Exception {
        byte[] seedBytes = new byte[16];
        Arrays.fill(seedBytes, (byte) 0xAA);
        System.setProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED, Base64.encodeBase64String(seedBytes));
        SecretKey key = provider.getSecretKey();
        assertEquals("AES", key.getAlgorithm());
        assertArrayEquals(seedBytes, key.getEncoded());
        assertEquals(1, repository.getSaveCount());
        assertTrue(repository.load().containsAlias(SecurityConstants.ALIAS_SYM_SECRET_KEY));
    }

    @Test
    void testGetSecretKey_whenMissingGeneratesAndSaves() throws Exception {
        SecretKey key = provider.getSecretKey();
        assertNotNull(key);
        assertEquals(1, repository.getSaveCount());
        assertTrue(repository.load().containsAlias(SecurityConstants.ALIAS_SYM_SECRET_KEY));
    }

    @Test
    void testGetSecretKey_returnsSameKeyOnSecondCall() throws Exception {
        SecretKey first = provider.getSecretKey();
        SecretKey second = provider.getSecretKey();
        assertArrayEquals(first.getEncoded(), second.getEncoded());
        assertEquals(1, repository.getSaveCount());
    }

    @Test
    void testGetSecretKey_throwsWhenSaveFails() {
        repository.failOnSave();
        assertThrows(IllegalStateException.class, () -> provider.getSecretKey());
    }

    @Test
    void testGenerateDefaultSecretKey_withFileBasedKeystoreUsesRandomBytes() {
        SecretKey first = provider.generateDefaultSecretKey();
        SecretKey second = provider.generateDefaultSecretKey();
        assertNotNull(first);
        assertFalse(Arrays.equals(first.getEncoded(), second.getEncoded()));
    }

    @Test
    void testGetKeyBytes_withoutFileBasedKeystoreDerivesFromPassword() throws Exception {
        KeystoreSecretKeyProvider passwordProvider = new KeystoreSecretKeyProvider(InMemoryKeyStoreRepository.withoutFile(), new SecureRandom());
        assertArrayEquals("changeittt".getBytes(), passwordProvider.getKeyBytes(10));
    }

    @Test
    void testGetKeyBytes_withShortLengthTruncatesPassword() throws Exception {
        KeystoreSecretKeyProvider passwordProvider = new KeystoreSecretKeyProvider(InMemoryKeyStoreRepository.withoutFile(), new SecureRandom());
        assertArrayEquals("chan".getBytes(), passwordProvider.getKeyBytes(4));
    }

    @Test
    void testIsValidAesKeyLength() {
        assertTrue(provider.isValidAesKeyLength(new byte[24]));
        assertFalse(provider.isValidAesKeyLength(new byte[20]));
    }
}
