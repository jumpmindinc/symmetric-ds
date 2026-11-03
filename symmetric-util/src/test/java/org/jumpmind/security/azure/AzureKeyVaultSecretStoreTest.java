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
package org.jumpmind.security.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.util.polling.SyncPoller;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;

class AzureKeyVaultSecretStoreTest {
    private static final String ALIAS = "corp-000.db.password";
    private static final String VAULT_NAME = "corp-000-db-password";
    private static final String VALUE = "s3cret";
    private SecretClient secretClient;
    private AzureKeyVaultSecretStore store;

    @BeforeEach
    void setUp() {
        secretClient = mock(SecretClient.class);
        store = new AzureKeyVaultSecretStore(new AzureKeyVault(secretClient, null));
    }

    @Test
    void testGetEntry_readsSecretWithVaultName() {
        when(secretClient.getSecret(VAULT_NAME)).thenReturn(new KeyVaultSecret(VAULT_NAME, VALUE));
        assertEquals(VALUE, store.getEntry(ALIAS));
    }

    @Test
    void testGetEntry_returnsNullWhenSecretMissing() {
        when(secretClient.getSecret(VAULT_NAME)).thenThrow(new ResourceNotFoundException("not found", null));
        assertNull(store.getEntry(ALIAS));
    }

    @Test
    void testSetEntry_savesSecretWithVaultName() {
        ArgumentCaptor<KeyVaultSecret> secret = ArgumentCaptor.forClass(KeyVaultSecret.class);
        store.setEntry(ALIAS, VALUE);
        verify(secretClient).setSecret(secret.capture());
        assertEquals(VAULT_NAME, secret.getValue().getName());
        assertEquals(VALUE, secret.getValue().getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteEntry_deletesThenPurges() {
        SyncPoller<DeletedSecret, Void> poller = mock(SyncPoller.class);
        when(secretClient.beginDeleteSecret(VAULT_NAME)).thenReturn(poller);
        store.deleteEntry(ALIAS);
        verify(poller).waitForCompletion();
        verify(secretClient).purgeDeletedSecret(VAULT_NAME);
    }

    @Test
    void testDeleteEntry_ignoresMissingSecret() {
        when(secretClient.beginDeleteSecret(VAULT_NAME)).thenThrow(new ResourceNotFoundException("not found", null));
        store.deleteEntry(ALIAS);
        verify(secretClient, never()).purgeDeletedSecret(any());
    }
}
