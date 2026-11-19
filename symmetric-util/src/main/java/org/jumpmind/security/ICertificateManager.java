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

/**
 * Creates, installs, exports and removes the node's SSL and SAML certificates. This is the implementor side of a Bridge behind the matching
 * {@link ISecurityService} methods. The supports methods tell callers which operations an implementation offers.
 */
public interface ICertificateManager {
    public void installDefaultSslCert(String host);

    public void installDefaultSamlSslCert(String host, int lifetimeInDays);

    public void installSslCert(PrivateKeyEntry entry);

    public void installSslCert(PrivateKeyEntry entry, String alias);

    public PrivateKeyEntry createDefaultSslCert(String host);

    public PrivateKeyEntry createSslCert(byte[] content, String fileType, String alias, String password);

    public TrustedCertificateEntry createTrustedCert(byte[] content, String fileType, String alias, String password);

    public X509Certificate getCurrentSslCert();

    public X509Certificate getCurrentSamlSslCert();

    public String exportCurrentSslCert(boolean includePrivateKey);

    public String exportTrustedCert(String alias);

    /**
     * Removes the node's own certificate and private key stored under the alias, such as the SSL certificate or
     * {@link SecurityConstants#ALIAS_SAML_PRIVATE_KEY} before the SAML certificate is rotated. Trusted certificates in the truststore are not affected.
     */
    public void deleteCertificate(String alias) throws Exception;

    public boolean supportsExportCertificate();

    public boolean supportsImportCertificate();

    public boolean supportsBackupCertificate();

    public boolean supportsGenerateSelfSignedCertificate();
}
