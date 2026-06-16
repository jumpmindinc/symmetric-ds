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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import javax.crypto.SecretKey;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SecurityServiceTest {
    private static class TestSecurityService extends SecurityService {
    }

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty(SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED);
        System.clearProperty(SecurityConstants.ALIAS_SYM_SECRET_KEY);
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
}
