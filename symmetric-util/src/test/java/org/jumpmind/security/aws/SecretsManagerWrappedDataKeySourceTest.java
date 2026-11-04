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
package org.jumpmind.security.aws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.InvalidRequestException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

class SecretsManagerWrappedDataKeySourceTest {
    private static final String SECRET_NAME = "symmetricds/kms-wrapped-key";
    private static final String KMS_KEY_ID = "alias/symmetricds";
    private static final byte[] WRAPPED_KEY = "wrapped-data-key".getBytes(StandardCharsets.UTF_8);
    private SecretsManagerClient client;
    private SecretsManagerWrappedDataKeySource source;

    @BeforeEach
    void setUp() {
        client = mock(SecretsManagerClient.class);
        source = new SecretsManagerWrappedDataKeySource(() -> client, SECRET_NAME, KMS_KEY_ID);
    }

    @Test
    void testRead_decodesSecretString() {
        ArgumentCaptor<GetSecretValueRequest> request = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        when(client.getSecretValue(request.capture())).thenReturn(GetSecretValueResponse.builder().secretString(Base64.encodeBase64String(WRAPPED_KEY))
                .build());
        assertArrayEquals(WRAPPED_KEY, source.read().get());
        assertEquals(SECRET_NAME, request.getValue().secretId());
    }

    @Test
    void testRead_returnsEmptyWhenSecretMissing() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenThrow(ResourceNotFoundException.builder().message("not found").build());
        assertFalse(source.read().isPresent());
    }

    @Test
    void testRead_throwsWhenSecretCannotBeRead() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenThrow(InvalidRequestException.builder().message("scheduled for deletion").build());
        assertThrows(InvalidRequestException.class, source::read);
    }

    @Test
    void testRead_closesClient() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenThrow(ResourceNotFoundException.builder().message("not found").build());
        source.read();
        verify(client).close();
    }

    @Test
    void testWriteIfAbsent_createsSecretWithKmsKey() {
        ArgumentCaptor<CreateSecretRequest> request = ArgumentCaptor.forClass(CreateSecretRequest.class);
        assertTrue(source.writeIfAbsent(WRAPPED_KEY));
        verify(client).createSecret(request.capture());
        assertEquals(SECRET_NAME, request.getValue().name());
        assertEquals(KMS_KEY_ID, request.getValue().kmsKeyId());
        assertArrayEquals(WRAPPED_KEY, Base64.decodeBase64(request.getValue().secretString()));
    }

    @Test
    void testWriteIfAbsent_returnsFalseWhenAnotherNodeSavedFirst() {
        when(client.createSecret(any(CreateSecretRequest.class))).thenThrow(ResourceExistsException.builder().message("exists").build());
        assertFalse(source.writeIfAbsent(WRAPPED_KEY));
        verify(client, times(1)).close();
    }

    @Test
    void testDescribe_namesSecret() {
        assertEquals("AWS Secrets Manager secret " + SECRET_NAME, source.describe());
    }
}
