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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CipherInitializerTest {
    private static final String PLAIN_TEXT = "keystore-integrity-test";
    private static final String AES_ECB = "AES/ECB/PKCS5Padding";
    private static final String DES_CBC = "DES/CBC/PKCS5Padding";
    private static final String PBE_WITH_MD5_AND_DES = "PBEWithMD5AndDES";
    private SecureRandom random;

    @BeforeEach
    void setUp() {
        random = new SecureRandom();
    }

    @Test
    void testInitialize_withKeyOnlyCipherRoundTrips() throws Exception {
        SecretKey key = new SecretKeySpec(new byte[16], "AES");
        Cipher encryptCipher = Cipher.getInstance(AES_ECB);
        Cipher decryptCipher = Cipher.getInstance(AES_ECB);
        CipherInitializer.initialize(encryptCipher, Cipher.ENCRYPT_MODE, key, random);
        CipherInitializer.initialize(decryptCipher, Cipher.DECRYPT_MODE, key, random);
        byte[] encrypted = encryptCipher.doFinal(PLAIN_TEXT.getBytes(StandardCharsets.UTF_8));
        assertEquals(PLAIN_TEXT, new String(decryptCipher.doFinal(encrypted), StandardCharsets.UTF_8));
    }

    @Test
    void testInitialize_withKeyOnlyCipherUsesNoIv() throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ECB);
        CipherInitializer.initialize(cipher, Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[16], "AES"), random);
        assertNull(cipher.getIV());
    }

    @Test
    void testInitialize_withPbeCipherUsesFixedSaltAndIterationCount() throws Exception {
        Cipher cipher = Cipher.getInstance(PBE_WITH_MD5_AND_DES);
        CipherInitializer.initialize(cipher, Cipher.ENCRYPT_MODE, createPbeKey(), random);
        PBEParameterSpec spec = cipher.getParameters().getParameterSpec(PBEParameterSpec.class);
        assertArrayEquals(SecurityConstants.SALT, spec.getSalt());
        assertEquals(SecurityConstants.ITERATION_COUNT, spec.getIterationCount());
    }

    @Test
    void testInitialize_withPbeCipherRoundTrips() throws Exception {
        SecretKey key = createPbeKey();
        Cipher encryptCipher = Cipher.getInstance(PBE_WITH_MD5_AND_DES);
        Cipher decryptCipher = Cipher.getInstance(PBE_WITH_MD5_AND_DES);
        CipherInitializer.initialize(encryptCipher, Cipher.ENCRYPT_MODE, key, random);
        CipherInitializer.initialize(decryptCipher, Cipher.DECRYPT_MODE, key, random);
        byte[] encrypted = encryptCipher.doFinal(PLAIN_TEXT.getBytes(StandardCharsets.UTF_8));
        assertEquals(PLAIN_TEXT, new String(decryptCipher.doFinal(encrypted), StandardCharsets.UTF_8));
    }

    @Test
    void testInitialize_withIvCipherSuppliesEightByteIv() throws Exception {
        Cipher cipher = Cipher.getInstance(DES_CBC);
        try (MockedStatic<Cipher> cipherStatics = mockStatic(Cipher.class, CALLS_REAL_METHODS)) {
            cipherStatics.when(() -> Cipher.getMaxAllowedParameterSpec(anyString())).thenReturn(new IvParameterSpec(new byte[8]));
            CipherInitializer.initialize(cipher, Cipher.DECRYPT_MODE, new SecretKeySpec(new byte[8], "DES"), random);
        }
        assertEquals(8, cipher.getIV().length);
    }

    @Test
    void testInitialize_withIvCipherThrowsWhenBranchNotTaken() throws Exception {
        Cipher cipher = Cipher.getInstance(DES_CBC);
        SecretKey key = new SecretKeySpec(new byte[8], "DES");
        assertThrows(InvalidKeyException.class, () -> CipherInitializer.initialize(cipher, Cipher.DECRYPT_MODE, key, random));
    }

    @Test
    void testInitialize_throwsWhenKeyDoesNotMatchAlgorithm() throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ECB);
        SecretKey invalidAesKey = new SecretKeySpec(new byte[7], "AES");
        assertThrows(InvalidKeyException.class, () -> CipherInitializer.initialize(cipher, Cipher.ENCRYPT_MODE, invalidAesKey, random));
    }

    private SecretKey createPbeKey() throws Exception {
        return SecretKeyFactory.getInstance(PBE_WITH_MD5_AND_DES).generateSecret(new PBEKeySpec("changeit".toCharArray()));
    }
}
