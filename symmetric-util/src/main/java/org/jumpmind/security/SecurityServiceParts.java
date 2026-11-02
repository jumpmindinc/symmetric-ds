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

import java.util.Objects;
import java.util.Optional;

/**
 * The parts a {@link SecurityService} delegates to. A part left unset is supplied by the service from the local keystore and truststore, so a subclass sets
 * only the parts it replaces, for example a cloud key management service:
 *
 * <pre>
 * super(SecurityServiceParts.builder().secretKeyProvider(kmsKeyProvider).build());
 * </pre>
 */
public final class SecurityServiceParts {
    private final ISecretKeyProvider secretKeyProvider;
    private final ISecretStore secretStore;
    private final ITlsMaterialProvider tlsMaterialProvider;
    private final CertificateManagerFactory certificateManagerFactory;

    private SecurityServiceParts(Builder builder) {
        secretKeyProvider = builder.secretKeyProvider;
        secretStore = builder.secretStore;
        tlsMaterialProvider = builder.tlsMaterialProvider;
        certificateManagerFactory = builder.certificateManagerFactory;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<ISecretKeyProvider> getSecretKeyProvider() {
        return Optional.ofNullable(secretKeyProvider);
    }

    public Optional<ISecretStore> getSecretStore() {
        return Optional.ofNullable(secretStore);
    }

    public Optional<ITlsMaterialProvider> getTlsMaterialProvider() {
        return Optional.ofNullable(tlsMaterialProvider);
    }

    public Optional<CertificateManagerFactory> getCertificateManagerFactory() {
        return Optional.ofNullable(certificateManagerFactory);
    }

    /**
     * Creates the certificate manager once the service can hand it the local keystore and the service's TLS material provider, for example
     * {@code BouncyCastleCertificateManager::new}.
     */
    @FunctionalInterface
    public interface CertificateManagerFactory {
        public ICertificateManager create(IKeyStoreRepository keyStoreRepository, ITlsMaterialProvider tlsMaterialProvider);
    }

    public static final class Builder {
        private ISecretKeyProvider secretKeyProvider;
        private ISecretStore secretStore;
        private ITlsMaterialProvider tlsMaterialProvider;
        private CertificateManagerFactory certificateManagerFactory;

        private Builder() {
        }

        public Builder secretKeyProvider(ISecretKeyProvider secretKeyProvider) {
            this.secretKeyProvider = requireSet(secretKeyProvider, "secretKeyProvider");
            return this;
        }

        public Builder secretStore(ISecretStore secretStore) {
            this.secretStore = requireSet(secretStore, "secretStore");
            return this;
        }

        public Builder tlsMaterialProvider(ITlsMaterialProvider tlsMaterialProvider) {
            this.tlsMaterialProvider = requireSet(tlsMaterialProvider, "tlsMaterialProvider");
            return this;
        }

        public Builder certificateManagerFactory(CertificateManagerFactory certificateManagerFactory) {
            this.certificateManagerFactory = requireSet(certificateManagerFactory, "certificateManagerFactory");
            return this;
        }

        public SecurityServiceParts build() {
            return new SecurityServiceParts(this);
        }

        private static <T> T requireSet(T part, String name) {
            return Objects.requireNonNull(part, () -> name + " must not be null; leave it unset to use the local keystore");
        }
    }
}
