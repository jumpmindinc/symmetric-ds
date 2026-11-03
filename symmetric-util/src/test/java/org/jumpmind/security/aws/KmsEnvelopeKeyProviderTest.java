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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.jumpmind.security.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

class KmsEnvelopeKeyProviderTest {
    private static final String KEY_ID = "alias/symmetricds";
    private static final byte[] PLAIN_KEY = filled((byte) 0x11);
    private static final byte[] OTHER_PLAIN_KEY = filled((byte) 0x22);
    private static final byte[] WRAPPED_KEY = "wrapped-data-key".getBytes();
    private static final byte[] OTHER_WRAPPED_KEY = "other-data-key".getBytes();
    private KmsClient kms;
    private WrappedDataKeySource wrappedDataKeySource;
    private KmsEnvelopeKeyProvider provider;

    @BeforeEach
    void setUp() {
        kms = mock(KmsClient.class);
        wrappedDataKeySource = mock(WrappedDataKeySource.class);
        when(wrappedDataKeySource.describe()).thenReturn("/opt/symmetricds/security/kms-wrapped.key");
        provider = new KmsEnvelopeKeyProvider(() -> kms, KEY_ID, wrappedDataKeySource);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.AWS_KMS_KEY_ID);
    }

    @Test
    void testGetSecretKey_onFirstStartGeneratesAndSavesDataKey() throws Exception {
        when(wrappedDataKeySource.read()).thenReturn(Optional.empty());
        when(wrappedDataKeySource.writeIfAbsent(WRAPPED_KEY)).thenReturn(true);
        when(kms.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(generatedDataKey());
        SecretKey key = provider.getSecretKey();
        assertArrayEquals(PLAIN_KEY, key.getEncoded());
        assertEquals("AES", key.getAlgorithm());
        verify(wrappedDataKeySource).writeIfAbsent(WRAPPED_KEY);
    }

    @Test
    void testGetSecretKey_onFirstStartRequestsAes256WithEncryptionContext() throws Exception {
        when(wrappedDataKeySource.read()).thenReturn(Optional.empty());
        when(wrappedDataKeySource.writeIfAbsent(WRAPPED_KEY)).thenReturn(true);
        when(kms.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(generatedDataKey());
        provider.getSecretKey();
        ArgumentCaptor<GenerateDataKeyRequest> request = ArgumentCaptor.forClass(GenerateDataKeyRequest.class);
        verify(kms).generateDataKey(request.capture());
        assertEquals(KEY_ID, request.getValue().keyId());
        assertEquals(DataKeySpec.AES_256, request.getValue().keySpec());
        assertEquals(KmsEnvelopeKeyProvider.ENCRYPTION_CONTEXT, request.getValue().encryptionContext());
    }

    @Test
    void testGetSecretKey_withSavedKeyDecryptsItWithoutGenerating() throws Exception {
        when(wrappedDataKeySource.read()).thenReturn(Optional.of(WRAPPED_KEY));
        when(kms.decrypt(any(DecryptRequest.class))).thenReturn(decryptedDataKey(PLAIN_KEY));
        assertArrayEquals(PLAIN_KEY, provider.getSecretKey().getEncoded());
        verify(kms, never()).generateDataKey(any(GenerateDataKeyRequest.class));
    }

    @Test
    void testGetSecretKey_withSavedKeyDecryptsWithKeyIdAndEncryptionContext() throws Exception {
        when(wrappedDataKeySource.read()).thenReturn(Optional.of(WRAPPED_KEY));
        when(kms.decrypt(any(DecryptRequest.class))).thenReturn(decryptedDataKey(PLAIN_KEY));
        provider.getSecretKey();
        ArgumentCaptor<DecryptRequest> request = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(kms).decrypt(request.capture());
        assertEquals(KEY_ID, request.getValue().keyId());
        assertArrayEquals(WRAPPED_KEY, request.getValue().ciphertextBlob().asByteArray());
        assertEquals(KmsEnvelopeKeyProvider.ENCRYPTION_CONTEXT, request.getValue().encryptionContext());
    }

    @Test
    void testGetSecretKey_whenAnotherProcessSavedFirstUsesSavedKey() throws Exception {
        when(wrappedDataKeySource.read()).thenReturn(Optional.empty(), Optional.of(OTHER_WRAPPED_KEY));
        when(wrappedDataKeySource.writeIfAbsent(WRAPPED_KEY)).thenReturn(false);
        when(kms.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(generatedDataKey());
        when(kms.decrypt(any(DecryptRequest.class))).thenReturn(decryptedDataKey(OTHER_PLAIN_KEY));
        assertArrayEquals(OTHER_PLAIN_KEY, provider.getSecretKey().getEncoded());
    }

    @Test
    void testGetSecretKey_closesKmsClient() throws Exception {
        when(wrappedDataKeySource.read()).thenReturn(Optional.of(WRAPPED_KEY));
        when(kms.decrypt(any(DecryptRequest.class))).thenReturn(decryptedDataKey(PLAIN_KEY));
        provider.getSecretKey();
        verify(kms).close();
    }

    @Test
    void testGetSecretKey_whenKmsRejectsRequestThrowsAndClosesClient() throws Exception {
        when(wrappedDataKeySource.read()).thenReturn(Optional.of(WRAPPED_KEY));
        KmsException accessDenied = (KmsException) KmsException.builder().message("access denied").build();
        when(kms.decrypt(any(DecryptRequest.class))).thenThrow(accessDenied);
        assertThrows(KmsException.class, () -> provider.getSecretKey());
        verify(kms).close();
    }

    @Test
    void testGetKeyIdentity_includesKeyIdAndSavedKeyLocation() {
        assertEquals("aws-kms:alias/symmetricds:/opt/symmetricds/security/kms-wrapped.key", provider.getKeyIdentity());
    }

    @Test
    void testGetKeyIdentity_differsForOtherKmsKey() {
        KmsEnvelopeKeyProvider otherProvider = new KmsEnvelopeKeyProvider(() -> kms, "alias/other", wrappedDataKeySource);
        assertNotEquals(provider.getKeyIdentity(), otherProvider.getKeyIdentity());
    }

    @Test
    void testFromSystemProperties_throwsWithoutKeyId() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> KmsEnvelopeKeyProvider.fromSystemProperties());
        assertEquals("Set the system property -Daws.kms.key.id to the ID, ARN or alias of the AWS KMS key that protects the SymmetricDS secret key",
                exception.getMessage());
    }

    @Test
    void testFromSystemProperties_withKeyIdUsesItInIdentity() {
        System.setProperty(SecurityConstants.AWS_KMS_KEY_ID, KEY_ID);
        assertTrue(((String) KmsEnvelopeKeyProvider.fromSystemProperties().getKeyIdentity()).startsWith("aws-kms:" + KEY_ID + ":"));
    }

    private GenerateDataKeyResponse generatedDataKey() {
        return GenerateDataKeyResponse.builder().plaintext(SdkBytes.fromByteArray(PLAIN_KEY)).ciphertextBlob(SdkBytes.fromByteArray(WRAPPED_KEY)).build();
    }

    private DecryptResponse decryptedDataKey(byte[] plainKey) {
        return DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(plainKey)).build();
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }
}
