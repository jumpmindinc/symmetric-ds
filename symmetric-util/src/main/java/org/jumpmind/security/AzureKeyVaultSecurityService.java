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

import java.security.SecureRandom;

/**
 * Security service that keeps the secret key, keystore entries and the SSL certificate in the Azure Key Vault named by
 * {@link SecurityConstants#AZURE_KEYVAULT_URI}. The truststore stays local. Other certificates, such as the SAML one, are generated with Bouncy Castle and
 * saved to the vault as secrets.
 */
public class AzureKeyVaultSecurityService extends BouncyCastleSecurityService {
    protected AzureKeyVaultSecurityService() {
        this(AzureKeyVault.connect());
    }

    AzureKeyVaultSecurityService(AzureKeyVault vault) {
        super(createParts(vault));
    }

    private static SecurityServiceParts createParts(AzureKeyVault vault) {
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
