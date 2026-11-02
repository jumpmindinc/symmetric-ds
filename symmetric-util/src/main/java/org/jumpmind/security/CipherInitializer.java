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

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEParameterSpec;

/**
 * Initializes a {@link Cipher} with the parameters its algorithm family needs: password-based (PBE) ciphers get the fixed salt and iteration count, ciphers
 * that take an initialization vector get a random one, and all others get the key alone.
 */
final class CipherInitializer {
    private static final String PASSWORD_BASED_ALGORITHM_PREFIX = "PBE";
    private static final int IV_LENGTH_IN_BYTES = 8;

    private CipherInitializer() {
    }

    static void initialize(Cipher cipher, int mode, SecretKey key, SecureRandom random) throws GeneralSecurityException {
        AlgorithmParameterSpec maxAllowedSpec = Cipher.getMaxAllowedParameterSpec(cipher.getAlgorithm());
        if (isPasswordBased(cipher, maxAllowedSpec)) {
            initializeWithSaltAndIterationCount(cipher, mode, key);
        } else if (maxAllowedSpec instanceof IvParameterSpec) {
            initializeWithRandomIv(cipher, mode, key, random);
        } else {
            cipher.init(mode, key);
        }
    }

    private static boolean isPasswordBased(Cipher cipher, AlgorithmParameterSpec maxAllowedSpec) {
        return maxAllowedSpec instanceof PBEParameterSpec || cipher.getAlgorithm().startsWith(PASSWORD_BASED_ALGORITHM_PREFIX);
    }

    private static void initializeWithSaltAndIterationCount(Cipher cipher, int mode, SecretKey key) throws GeneralSecurityException {
        cipher.init(mode, key, new PBEParameterSpec(SecurityConstants.SALT, SecurityConstants.ITERATION_COUNT));
    }

    private static void initializeWithRandomIv(Cipher cipher, int mode, SecretKey key, SecureRandom random) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH_IN_BYTES];
        random.nextBytes(iv);
        cipher.init(mode, key, new IvParameterSpec(iv));
    }
}
