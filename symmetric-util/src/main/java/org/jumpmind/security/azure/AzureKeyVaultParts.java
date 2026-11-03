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

import java.security.SecureRandom;

import org.jumpmind.security.BouncyCastleHelper;
import org.jumpmind.security.KeystoreSecretKeyProvider;
import org.jumpmind.security.SecurityServiceParts;

/**
 * Builds the parts that keep the secret key, keystore entries and the SSL certificate in an Azure Key Vault, for
 * {@link org.jumpmind.security.AzureKeyVaultSecurityService}.
 */
public final class AzureKeyVaultParts {
    private AzureKeyVaultParts() {
    }

    public static SecurityServiceParts create(AzureKeyVault vault) {
        new BouncyCastleHelper().checkProviderInstalled();
        AzureKeyVaultSecretStore secretStore = new AzureKeyVaultSecretStore(vault);
        AzureKeyVaultKeyStoreRepository keyStoreRepository = new AzureKeyVaultKeyStoreRepository(vault, secretStore);
        return SecurityServiceParts.builder()
                .keyStoreRepository(keyStoreRepository)
                .secretKeyProvider(new AzureKeyVaultSecretKeyProvider(vault,
                        () -> new KeystoreSecretKeyProvider(keyStoreRepository, new SecureRandom()).generateDefaultSecretKey()))
                .secretStore(secretStore)
                .certificateManagerFactory((repository, tlsMaterialProvider) -> new AzureKeyVaultCertificateManager(repository, tlsMaterialProvider, vault,
                        secretStore))
                .build();
    }
}
