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

import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.X509Certificate;

import org.apache.commons.lang3.NotImplementedException;

/**
 * Null Object for nodes that do not manage certificates, such as clients without Bouncy Castle. Installing default certificates does nothing so startup can
 * proceed, every supports method returns false, and the remaining operations throw {@link NotImplementedException}.
 */
public class UnsupportedCertificateManager implements ICertificateManager {
    @Override
    public void installDefaultSslCert(String host) {
    }

    @Override
    public void installDefaultSamlSslCert(String host, int lifetimeInDays) {
    }

    @Override
    public void installSslCert(PrivateKeyEntry entry) {
        throw new NotImplementedException();
    }

    @Override
    public void installSslCert(PrivateKeyEntry entry, String alias) {
        throw new NotImplementedException();
    }

    @Override
    public PrivateKeyEntry createDefaultSslCert(String host) {
        throw new NotImplementedException();
    }

    @Override
    public PrivateKeyEntry createSslCert(byte[] content, String fileType, String alias, String password) {
        throw new NotImplementedException();
    }

    /**
     * @return null, because certificates cannot be read without Bouncy Castle
     */
    @Override
    public TrustedCertificateEntry createTrustedCert(byte[] content, String fileType, String alias, String password) {
        return null;
    }

    @Override
    public X509Certificate getCurrentSslCert() {
        throw new NotImplementedException();
    }

    @Override
    public X509Certificate getCurrentSamlSslCert() {
        throw new NotImplementedException();
    }

    @Override
    public String exportCurrentSslCert(boolean includePrivateKey) {
        throw new NotImplementedException();
    }

    @Override
    public String exportTrustedCert(String alias) {
        throw new NotImplementedException();
    }

    @Override
    public void deleteCertificate(String alias) {
        throw new NotImplementedException();
    }

    @Override
    public boolean supportsExportCertificate() {
        return false;
    }

    @Override
    public boolean supportsImportCertificate() {
        return false;
    }

    @Override
    public boolean supportsBackupCertificate() {
        return false;
    }

    @Override
    public boolean supportsGenerateSelfSignedCertificate() {
        return false;
    }
}
