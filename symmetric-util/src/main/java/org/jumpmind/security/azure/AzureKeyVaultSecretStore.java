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

import org.jumpmind.security.ISecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;

/**
 * Keeps named secret values as Azure Key Vault secrets, one per alias.
 */
class AzureKeyVaultSecretStore implements ISecretStore {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SecretClient secretClient;

    AzureKeyVaultSecretStore(AzureKeyVault vault) {
        this.secretClient = vault.getSecretClient();
    }

    @Override
    public String getEntry(String alias) {
        try {
            return secretClient.getSecret(AzureKeyVault.toVaultName(alias)).getValue();
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    @Override
    public void setEntry(String alias, String value) {
        secretClient.setSecret(new KeyVaultSecret(AzureKeyVault.toVaultName(alias), value));
    }

    /**
     * Deletes and then purges the secret, so the same alias can be saved again straight away.
     */
    @Override
    public void deleteEntry(String alias) {
        String vaultName = AzureKeyVault.toVaultName(alias);
        try {
            secretClient.beginDeleteSecret(vaultName).waitForCompletion();
            secretClient.purgeDeletedSecret(vaultName);
        } catch (ResourceNotFoundException e) {
            log.info("Trying to delete key {}, but did not exist in the vault.", alias);
        }
    }
}
