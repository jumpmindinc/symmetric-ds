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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.jumpmind.security.ISecretKeyProvider;
import org.jumpmind.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;
import software.amazon.awssdk.services.kms.model.ReEncryptRequest;
import software.amazon.awssdk.services.kms.model.ReEncryptResponse;

/**
 * Supplies an AES-256 secret key protected by an AWS KMS key, using envelope encryption: on first start KMS generates the data key and only its encrypted copy
 * is saved, by default in AWS Secrets Manager (see {@link WrappedDataKeySource#fromSystemProperties()}); on later starts KMS decrypts that copy. The plain key
 * exists only in memory, and the KMS key never leaves KMS.
 * <p>
 * Configured with {@link SecurityConstants#AWS_KMS_KEY_ID} and optionally {@link SecurityConstants#AWS_KMS_REGION} and
 * {@link SecurityConstants#AWS_KMS_ENDPOINT}. Credentials come from the AWS default provider chain, such as environment variables, a profile, an EC2 instance
 * role or EKS pod identity. Every KMS request carries {@link #ENCRYPTION_CONTEXT}, so the encrypted data key can only be decrypted for this purpose.
 * <p>
 * The key ID may be an alias. When the alias is pointed at a new KMS key, the saved copy is decrypted with the old key and re-encrypted under the new one,
 * which needs kms:DescribeKey and kms:ReEncrypt* permissions as well as kms:Decrypt.
 */
public class AwsKmsEnvelopeKeyProvider implements ISecretKeyProvider {
    static final Map<String, String> ENCRYPTION_CONTEXT = Map.of("application", "symmetricds", "purpose", SecurityConstants.ALIAS_SYM_SECRET_KEY);
    private static final String KEY_ALGORITHM = "AES";
    private static final Logger log = LoggerFactory.getLogger(AwsKmsEnvelopeKeyProvider.class);
    private final Supplier<KmsClient> kmsClientSupplier;
    private final String keyId;
    private final WrappedDataKeySource wrappedDataKeySource;

    AwsKmsEnvelopeKeyProvider(Supplier<KmsClient> kmsClientSupplier, String keyId, WrappedDataKeySource wrappedDataKeySource) {
        this.kmsClientSupplier = kmsClientSupplier;
        this.keyId = keyId;
        this.wrappedDataKeySource = wrappedDataKeySource;
    }

    /**
     * @throws IllegalStateException
     *             when {@link SecurityConstants#AWS_KMS_KEY_ID} is not set
     */
    public static AwsKmsEnvelopeKeyProvider fromSystemProperties() {
        String keyId = AwsSettings.getKmsKeyId();
        if (keyId == null) {
            throw new IllegalStateException("Set the system property -D" + SecurityConstants.AWS_KMS_KEY_ID
                    + " to the ID, ARN or alias of the AWS KMS key that protects the SymmetricDS secret key");
        }
        return new AwsKmsEnvelopeKeyProvider(AwsSettings::buildKmsClient, keyId, WrappedDataKeySource.fromSystemProperties());
    }

    /**
     * Opens a KMS client for this call only, because the key is fetched once per JVM.
     */
    @Override
    public SecretKey getSecretKey() throws Exception {
        try (KmsClient kms = kmsClientSupplier.get()) {
            Optional<byte[]> wrappedKey = wrappedDataKeySource.read();
            if (wrappedKey.isPresent()) {
                log.info("Decrypting the secret key from {} with AWS KMS", wrappedDataKeySource.describe());
                return decrypt(kms, wrappedKey.get());
            }
            return generate(kms);
        }
    }

    private SecretKey generate(KmsClient kms) throws Exception {
        GenerateDataKeyResponse response = kms.generateDataKey(GenerateDataKeyRequest.builder().keyId(keyId).keySpec(DataKeySpec.AES_256)
                .encryptionContext(ENCRYPTION_CONTEXT).build());
        if (wrappedDataKeySource.writeIfAbsent(response.ciphertextBlob().asByteArray())) {
            log.warn("Generated a new secret key with AWS KMS key {} and saved it, encrypted, to {}. Keep that copy safe: values encrypted with any other"
                    + " secret key cannot be decrypted with this one, and every node that shares encrypted values must use this same encrypted key", keyId,
                    wrappedDataKeySource.describe());
            return toSecretKey(response.plaintext());
        }
        log.info("Another process saved an encrypted secret key to {} first, so using that key", wrappedDataKeySource.describe());
        byte[] savedKey = wrappedDataKeySource.read()
                .orElseThrow(() -> new IllegalStateException("Encrypted secret key disappeared from " + wrappedDataKeySource.describe()));
        return decrypt(kms, savedKey);
    }

    /**
     * Leaves the key ID out of the request, because KMS reads it from the encrypted data key: after the alias in {@link SecurityConstants#AWS_KMS_KEY_ID} is
     * pointed at a new KMS key, the saved copy can still be decrypted with the old one, and is then moved to the new one.
     */
    private SecretKey decrypt(KmsClient kms, byte[] wrappedKey) {
        DecryptResponse response = kms.decrypt(DecryptRequest.builder().ciphertextBlob(SdkBytes.fromByteArray(wrappedKey)).encryptionContext(
                ENCRYPTION_CONTEXT).build());
        reEncryptIfKeyRotated(kms, wrappedKey, response.keyId());
        return toSecretKey(response.plaintext());
    }

    /**
     * Encrypts the same data key under the KMS key that {@link SecurityConstants#AWS_KMS_KEY_ID} names now, so the old KMS key can be retired. The secret key
     * itself does not change, so values encrypted with it stay readable. A failure is logged rather than thrown, because the key was already decrypted.
     */
    private void reEncryptIfKeyRotated(KmsClient kms, byte[] wrappedKey, String usedKeyArn) {
        if (usedKeyArn == null || usedKeyArn.equals(keyId)) {
            return;
        }
        try {
            String currentKeyArn = kms.describeKey(DescribeKeyRequest.builder().keyId(keyId).build()).keyMetadata().arn();
            if (usedKeyArn.equals(currentKeyArn)) {
                return;
            }
            if (!wrappedDataKeySource.isWritable()) {
                log.warn("The secret key in {} is encrypted with AWS KMS key {}, but {} now names {}. Re-encrypt it under the new key and update the setting"
                        + " before retiring the old key", wrappedDataKeySource.describe(), usedKeyArn, keyId, currentKeyArn);
                return;
            }
            ReEncryptResponse response = kms.reEncrypt(ReEncryptRequest.builder().ciphertextBlob(SdkBytes.fromByteArray(wrappedKey)).sourceEncryptionContext(
                    ENCRYPTION_CONTEXT).destinationKeyId(currentKeyArn).destinationEncryptionContext(ENCRYPTION_CONTEXT).build());
            wrappedDataKeySource.replace(response.ciphertextBlob().asByteArray());
            log.info("Re-encrypted the secret key in {} from AWS KMS key {} to {}", wrappedDataKeySource.describe(), usedKeyArn, currentKeyArn);
        } catch (SdkException | IOException e) {
            log.warn("Unable to move the secret key in {} from AWS KMS key {} to the key {} names now, so it still depends on the old key",
                    wrappedDataKeySource.describe(), usedKeyArn, keyId, e);
        }
    }

    private SecretKey toSecretKey(SdkBytes plaintext) {
        byte[] keyBytes = plaintext.asByteArray();
        try {
            return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /**
     * Providers using the same KMS key and the same saved data key supply the same secret key.
     */
    @Override
    public Object getKeyIdentity() {
        return "aws-kms:" + keyId + ":" + wrappedDataKeySource.describe();
    }
}
