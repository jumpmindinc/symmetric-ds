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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AzureKeyVaultTest {
    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS);
    }

    @Test
    void testToVaultName_replacesDotsWithDashes() {
        assertEquals("corp-000-db-password", AzureKeyVault.toVaultName("corp-000.db.password"));
    }

    @Test
    void testToAlias_replacesDashesWithDots() {
        assertEquals("sym.secret", AzureKeyVault.toAlias("sym-secret"));
    }

    @Test
    void testGetSslCertAlias_defaultsToSymPrivateKey() {
        assertEquals(SecurityConstants.ALIAS_SYM_PRIVATE_KEY, AzureKeyVault.getSslCertAlias());
    }

    @Test
    void testGetSslCertAlias_withConfiguredAlias() {
        System.setProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS, "custom");
        assertEquals("custom", AzureKeyVault.getSslCertAlias());
    }
}
