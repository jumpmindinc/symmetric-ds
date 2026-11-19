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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import org.jumpmind.security.ITlsMaterialProvider;
import org.jumpmind.security.SecurityServiceParts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.secrets.SecretClient;

class AzureKeyVaultPartsTest {
    private SecurityServiceParts parts;

    @BeforeEach
    void setUp() {
        SecretClient secretClient = mock(SecretClient.class);
        CertificateClient certificateClient = mock(CertificateClient.class);
        parts = AzureKeyVaultParts.create(new AzureKeyVault(secretClient, certificateClient));
    }

    @Test
    void testCreate_keepsKeyStoreSecretKeyAndEntriesInVault() {
        assertInstanceOf(AzureKeyVaultKeyStoreRepository.class, parts.getKeyStoreRepository().get());
        assertInstanceOf(AzureKeyVaultSecretKeyProvider.class, parts.getSecretKeyProvider().get());
        assertInstanceOf(AzureKeyVaultSecretStore.class, parts.getSecretStore().get());
    }

    @Test
    void testCreate_managesCertificatesWithVault() {
        ITlsMaterialProvider tlsMaterialProvider = mock(ITlsMaterialProvider.class);
        assertInstanceOf(AzureKeyVaultCertificateManager.class,
                parts.getCertificateManagerFactory().get().create(parts.getKeyStoreRepository().get(), tlsMaterialProvider));
    }
}
