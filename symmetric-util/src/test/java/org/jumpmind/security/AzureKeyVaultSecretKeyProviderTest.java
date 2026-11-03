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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;

class AzureKeyVaultSecretKeyProviderTest {
    private static final String SECRET_NAME = "sym-secret";
    private static final String VAULT_URL = "https://symmetricds.vault.azure.net";
    private static final SecretKey KEY = new SecretKeySpec(new byte[32], "AES");
    private SecretClient secretClient;
    private AzureKeyVault vault;

    @BeforeEach
    void setUp() {
        secretClient = mock(SecretClient.class);
        vault = new AzureKeyVault(secretClient, null);
    }

    @Test
    void testGetSecretKey_readsSerializedKeyFromVault() {
        when(secretClient.getSecret(SECRET_NAME)).thenReturn(new KeyVaultSecret(SECRET_NAME, serialize(KEY)));
        AzureKeyVaultSecretKeyProvider provider = new AzureKeyVaultSecretKeyProvider(vault, () -> fail("should not generate a key"));
        assertArrayEquals(KEY.getEncoded(), provider.getSecretKey().getEncoded());
    }

    @Test
    void testGetSecretKey_whenMissingGeneratesAndSavesKey() {
        when(secretClient.getSecret(SECRET_NAME)).thenThrow(new ResourceNotFoundException("not found", null));
        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        SecretKey key = new AzureKeyVaultSecretKeyProvider(vault, () -> KEY).getSecretKey();
        assertSame(KEY, key);
        verify(secretClient).setSecret(eq(SECRET_NAME), saved.capture());
        assertEquals(serialize(KEY), saved.getValue());
    }

    @Test
    void testGetKeyIdentity_namesVault() {
        when(secretClient.getVaultUrl()).thenReturn(VAULT_URL);
        assertEquals("azure-keyvault:" + VAULT_URL, new AzureKeyVaultSecretKeyProvider(vault, () -> KEY).getKeyIdentity());
    }

    private static String serialize(SecretKey key) {
        return Base64.getEncoder().encodeToString(SerializationUtils.serialize(key));
    }
}
