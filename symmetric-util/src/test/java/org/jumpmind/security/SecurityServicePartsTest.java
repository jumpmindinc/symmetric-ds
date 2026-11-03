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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import org.jumpmind.security.SecurityServiceParts.CertificateManagerFactory;

class SecurityServicePartsTest {
    @Test
    void testBuild_withNothingSetLeavesEveryPartEmpty() {
        SecurityServiceParts parts = SecurityServiceParts.builder().build();
        assertFalse(parts.getSecretKeyProvider().isPresent());
        assertFalse(parts.getSecretStore().isPresent());
        assertFalse(parts.getTlsMaterialProvider().isPresent());
        assertFalse(parts.getCertificateManagerFactory().isPresent());
        assertFalse(parts.getKeyStoreRepository().isPresent());
    }

    @Test
    void testBuild_withEveryPartSet() {
        ISecretKeyProvider secretKeyProvider = mock(ISecretKeyProvider.class);
        ISecretStore secretStore = mock(ISecretStore.class);
        ITlsMaterialProvider tlsMaterialProvider = mock(ITlsMaterialProvider.class);
        CertificateManagerFactory certificateManagerFactory = (keyStoreRepository, tls) -> new UnsupportedCertificateManager();
        IKeyStoreRepository keyStoreRepository = mock(IKeyStoreRepository.class);
        SecurityServiceParts parts = SecurityServiceParts.builder().secretKeyProvider(secretKeyProvider).secretStore(secretStore)
                .tlsMaterialProvider(tlsMaterialProvider).certificateManagerFactory(certificateManagerFactory).keyStoreRepository(keyStoreRepository).build();
        assertSame(secretKeyProvider, parts.getSecretKeyProvider().get());
        assertSame(secretStore, parts.getSecretStore().get());
        assertSame(tlsMaterialProvider, parts.getTlsMaterialProvider().get());
        assertSame(certificateManagerFactory, parts.getCertificateManagerFactory().get());
        assertSame(keyStoreRepository, parts.getKeyStoreRepository().get());
    }

    @Test
    void testSecretKeyProvider_rejectsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> SecurityServiceParts.builder().secretKeyProvider(null));
        assertEquals("secretKeyProvider must not be null; leave it unset to use the local keystore", exception.getMessage());
    }

    @Test
    void testSecretStore_rejectsNull() {
        assertThrows(NullPointerException.class, () -> SecurityServiceParts.builder().secretStore(null));
    }

    @Test
    void testTlsMaterialProvider_rejectsNull() {
        assertThrows(NullPointerException.class, () -> SecurityServiceParts.builder().tlsMaterialProvider(null));
    }

    @Test
    void testCertificateManagerFactory_rejectsNull() {
        assertThrows(NullPointerException.class, () -> SecurityServiceParts.builder().certificateManagerFactory(null));
    }

    @Test
    void testKeyStoreRepository_rejectsNull() {
        assertThrows(NullPointerException.class, () -> SecurityServiceParts.builder().keyStoreRepository(null));
    }
}
