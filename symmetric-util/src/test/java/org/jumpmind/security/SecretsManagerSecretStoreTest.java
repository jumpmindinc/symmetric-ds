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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

class SecretsManagerSecretStoreTest {
    private static final String PREFIX = "symmetricds/";
    private static final String KMS_KEY_ID = "alias/symmetricds";
    private static final String ALIAS = "corp-000.db.password";
    private static final String SECRET_NAME = PREFIX + ALIAS;
    private static final String VALUE = "s3cret";
    private SecretsManagerClient client;
    private AtomicInteger clientsCreated;
    private SecretsManagerSecretStore store;

    @BeforeEach
    void setUp() {
        client = mock(SecretsManagerClient.class);
        clientsCreated = new AtomicInteger();
        store = createStore(KMS_KEY_ID);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX);
    }

    @Test
    void testGetEntry_returnsSecretStringOfPrefixedName() {
        ArgumentCaptor<GetSecretValueRequest> request = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        when(client.getSecretValue(request.capture())).thenReturn(GetSecretValueResponse.builder().secretString(VALUE).build());
        assertEquals(VALUE, store.getEntry(ALIAS));
        assertEquals(SECRET_NAME, request.getValue().secretId());
    }

    @Test
    void testGetEntry_returnsNullWhenSecretMissing() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenThrow(notFound());
        assertNull(store.getEntry(ALIAS));
    }

    @Test
    void testGetEntry_throwsOtherServiceErrors() {
        SecretsManagerException accessDenied = (SecretsManagerException) SecretsManagerException.builder().message("access denied").build();
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenThrow(accessDenied);
        assertThrows(SecretsManagerException.class, () -> store.getEntry(ALIAS));
    }

    @Test
    void testSetEntry_addsVersionToExistingSecret() {
        ArgumentCaptor<PutSecretValueRequest> request = ArgumentCaptor.forClass(PutSecretValueRequest.class);
        when(client.putSecretValue(request.capture())).thenReturn(PutSecretValueResponse.builder().build());
        store.setEntry(ALIAS, VALUE);
        assertEquals(SECRET_NAME, request.getValue().secretId());
        assertEquals(VALUE, request.getValue().secretString());
        verify(client, never()).createSecret(any(CreateSecretRequest.class));
    }

    @Test
    void testSetEntry_createsMissingSecretWithKmsKey() {
        when(client.putSecretValue(any(PutSecretValueRequest.class))).thenThrow(notFound());
        ArgumentCaptor<CreateSecretRequest> request = ArgumentCaptor.forClass(CreateSecretRequest.class);
        store.setEntry(ALIAS, VALUE);
        verify(client).createSecret(request.capture());
        assertEquals(SECRET_NAME, request.getValue().name());
        assertEquals(KMS_KEY_ID, request.getValue().kmsKeyId());
        assertEquals(VALUE, request.getValue().secretString());
    }

    @Test
    void testSetEntry_withoutKmsKeyLeavesKeyToAwsManagedKey() {
        when(client.putSecretValue(any(PutSecretValueRequest.class))).thenThrow(notFound());
        ArgumentCaptor<CreateSecretRequest> request = ArgumentCaptor.forClass(CreateSecretRequest.class);
        createStore(null).setEntry(ALIAS, VALUE);
        verify(client).createSecret(request.capture());
        assertNull(request.getValue().kmsKeyId());
    }

    @Test
    void testSetEntry_whenAnotherNodeCreatedSecretFirstAddsVersion() {
        when(client.putSecretValue(any(PutSecretValueRequest.class))).thenThrow(notFound()).thenReturn(PutSecretValueResponse.builder().build());
        when(client.createSecret(any(CreateSecretRequest.class))).thenThrow(ResourceExistsException.builder().message("exists").build());
        store.setEntry(ALIAS, VALUE);
        verify(client, times(2)).putSecretValue(any(PutSecretValueRequest.class));
    }

    @Test
    void testDeleteEntry_deletesWithoutRecoveryWindow() {
        ArgumentCaptor<DeleteSecretRequest> request = ArgumentCaptor.forClass(DeleteSecretRequest.class);
        store.deleteEntry(ALIAS);
        verify(client).deleteSecret(request.capture());
        assertEquals(SECRET_NAME, request.getValue().secretId());
        assertEquals(Boolean.TRUE, request.getValue().forceDeleteWithoutRecovery());
    }

    @Test
    void testDeleteEntry_ignoresMissingSecret() {
        when(client.deleteSecret(any(DeleteSecretRequest.class))).thenThrow(notFound());
        store.deleteEntry(ALIAS);
        verify(client).deleteSecret(any(DeleteSecretRequest.class));
    }

    @Test
    void testGetClient_createsClientOnceOnFirstUse() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(GetSecretValueResponse.builder().secretString(VALUE).build());
        assertEquals(0, clientsCreated.get());
        store.getEntry(ALIAS);
        store.deleteEntry(ALIAS);
        assertEquals(1, clientsCreated.get());
    }

    @Test
    void testFromSystemProperties_defaultsPrefix() {
        assertEquals(SECRET_NAME, SecretsManagerSecretStore.fromSystemProperties().toSecretName(ALIAS));
    }

    @Test
    void testFromSystemProperties_withConfiguredPrefix() {
        System.setProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX, " prod/sym/ ");
        assertEquals("prod/sym/" + ALIAS, SecretsManagerSecretStore.fromSystemProperties().toSecretName(ALIAS));
    }

    @Test
    void testFromSystemProperties_withBlankPrefixUsesDefault() {
        System.setProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX, "  ");
        assertEquals(SECRET_NAME, SecretsManagerSecretStore.fromSystemProperties().toSecretName(ALIAS));
    }

    private SecretsManagerSecretStore createStore(String kmsKeyId) {
        return new SecretsManagerSecretStore(() -> {
            clientsCreated.incrementAndGet();
            return client;
        }, PREFIX, kmsKeyId);
    }

    private static ResourceNotFoundException notFound() {
        return ResourceNotFoundException.builder().message("not found").build();
    }
}
