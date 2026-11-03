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

import java.util.Base64;
import java.util.function.Supplier;

import javax.crypto.SecretKey;

import org.apache.commons.lang3.SerializationUtils;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;

/**
 * Reads the secret key from the {@link SecurityConstants#ALIAS_SYM_SECRET_KEY} secret in Azure Key Vault, where it is kept as a serialized key in Base64. When
 * the secret is missing, a new key is generated and saved there.
 */
class AzureKeyVaultSecretKeyProvider implements ISecretKeyProvider {
    private static final String SECRET_NAME = AzureKeyVault.toVaultName(SecurityConstants.ALIAS_SYM_SECRET_KEY);
    private final SecretClient secretClient;
    private final Supplier<SecretKey> keyGenerator;

    AzureKeyVaultSecretKeyProvider(AzureKeyVault vault, Supplier<SecretKey> keyGenerator) {
        this.secretClient = vault.getSecretClient();
        this.keyGenerator = keyGenerator;
    }

    @Override
    public SecretKey getSecretKey() {
        try {
            return SerializationUtils.deserialize(Base64.getDecoder().decode(secretClient.getSecret(SECRET_NAME).getValue()));
        } catch (ResourceNotFoundException e) {
            SecretKey key = keyGenerator.get();
            secretClient.setSecret(SECRET_NAME, Base64.getEncoder().encodeToString(SerializationUtils.serialize(key)));
            return key;
        }
    }

    /**
     * Providers reading the same vault supply the same secret key.
     */
    @Override
    public Object getKeyIdentity() {
        return "azure-keyvault:" + secretClient.getVaultUrl();
    }
}
