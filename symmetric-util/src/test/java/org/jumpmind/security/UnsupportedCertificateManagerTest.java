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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnsupportedCertificateManagerTest {
    private static final byte[] CONTENT = new byte[0];
    private UnsupportedCertificateManager manager;

    @BeforeEach
    void setUp() {
        manager = new UnsupportedCertificateManager();
    }

    @Test
    void testInstallDefaultSslCert_doesNothing() {
        assertDoesNotThrow(() -> manager.installDefaultSslCert("host"));
    }

    @Test
    void testInstallDefaultSamlSslCert_doesNothing() {
        assertDoesNotThrow(() -> manager.installDefaultSamlSslCert("host", 30));
    }

    @Test
    void testCreateTrustedCert_returnsNull() {
        assertNull(manager.createTrustedCert(CONTENT, "pem", null, null));
    }

    @Test
    void testInstallSslCert_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.installSslCert(null));
    }

    @Test
    void testInstallSslCert_withAliasThrowsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.installSslCert(null, "alias"));
    }

    @Test
    void testCreateDefaultSslCert_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.createDefaultSslCert("host"));
    }

    @Test
    void testCreateSslCert_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.createSslCert(CONTENT, "pem", null, null));
    }

    @Test
    void testGetCurrentSslCert_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.getCurrentSslCert());
    }

    @Test
    void testGetCurrentSamlSslCert_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.getCurrentSamlSslCert());
    }

    @Test
    void testExportCurrentSslCert_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.exportCurrentSslCert(false));
    }

    @Test
    void testExportTrustedCert_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.exportTrustedCert("alias"));
    }

    @Test
    void testDeleteCertificate_throwsNotImplemented() {
        assertThrows(NotImplementedException.class, () -> manager.deleteCertificate(SecurityConstants.ALIAS_SAML_PRIVATE_KEY));
    }

    @Test
    void testSupports_returnsFalseForEveryOperation() {
        assertFalse(manager.supportsExportCertificate());
        assertFalse(manager.supportsImportCertificate());
        assertFalse(manager.supportsBackupCertificate());
        assertFalse(manager.supportsGenerateSelfSignedCertificate());
    }
}
