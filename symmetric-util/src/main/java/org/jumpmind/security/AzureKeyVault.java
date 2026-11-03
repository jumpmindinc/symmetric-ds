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

import java.lang.reflect.Method;

import org.apache.commons.lang3.StringUtils;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.CredentialBuilderBase;
import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.CertificateClientBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;

/**
 * The clients for the Azure Key Vault named by {@link SecurityConstants#AZURE_KEYVAULT_URI}, shared by the Azure parts of {@link AzureKeyVaultSecurityService},
 * and the naming rule they share: Key Vault names allow dashes but not dots, so the dots in an alias are stored as dashes.
 */
class AzureKeyVault {
    private static final String DEFAULT_CREDENTIAL_BUILDER_CLASS = "com.azure.identity.DefaultAzureCredentialBuilder";
    private final SecretClient secretClient;
    private final CertificateClient certificateClient;

    AzureKeyVault(SecretClient secretClient, CertificateClient certificateClient) {
        this.secretClient = secretClient;
        this.certificateClient = certificateClient;
    }

    /**
     * Signs in with the credential builder named by {@link SecurityConstants#AZURE_CREDENTIAL_BUILDER_CLASSNAME}, which defaults to
     * {@value #DEFAULT_CREDENTIAL_BUILDER_CLASS}.
     */
    static AzureKeyVault connect() {
        String vaultUri = System.getProperty(SecurityConstants.AZURE_KEYVAULT_URI);
        TokenCredential credential = buildCredential(StringUtils.defaultIfBlank(System.getProperty(SecurityConstants.AZURE_CREDENTIAL_BUILDER_CLASSNAME),
                DEFAULT_CREDENTIAL_BUILDER_CLASS));
        return new AzureKeyVault(new SecretClientBuilder().vaultUrl(vaultUri).credential(credential).buildClient(),
                new CertificateClientBuilder().vaultUrl(vaultUri).credential(credential).buildClient());
    }

    @SuppressWarnings("rawtypes")
    private static TokenCredential buildCredential(String credentialBuilderClassName) {
        try {
            CredentialBuilderBase credentialBuilder = (CredentialBuilderBase) Class.forName(credentialBuilderClassName).getDeclaredConstructor().newInstance();
            Method buildMethod = credentialBuilder.getClass().getMethod("build", (Class<?>[]) null);
            return (TokenCredential) buildMethod.invoke(credentialBuilder);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    SecretClient getSecretClient() {
        return secretClient;
    }

    CertificateClient getCertificateClient() {
        return certificateClient;
    }

    static String toVaultName(String alias) {
        return StringUtils.replaceChars(alias, '.', '-');
    }

    static String toAlias(String vaultName) {
        return StringUtils.replaceChars(vaultName, '-', '.');
    }

    static String getSslCertAlias() {
        return System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS, SecurityConstants.ALIAS_SYM_PRIVATE_KEY);
    }
}
