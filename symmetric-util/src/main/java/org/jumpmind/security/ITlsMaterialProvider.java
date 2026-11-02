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

import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;

import javax.net.ssl.KeyManagerFactory;

/**
 * Supplies the key managers and trusted certificates a node needs for TLS connections. This is the implementor side of a Bridge behind the matching
 * {@link ISecurityService} methods, so a backend other than the local keystore and truststore files can provide them.
 * <p>
 * Creating, importing and exporting certificates is not part of this interface.
 */
public interface ITlsMaterialProvider {
    public KeyManagerFactory getKeyManagerFactory();

    /**
     * @return the trusted certificates, which callers may change and then pass to {@link #saveTrustStore(KeyStore)}
     */
    public KeyStore getTrustStore();

    /**
     * Replaces the stored trusted certificates with the given ones.
     */
    public void saveTrustStore(KeyStore trustStore) throws Exception;

    /**
     * Adds the certificate to the truststore unless a certificate equal to it is already there.
     */
    public void installTrustedCert(TrustedCertificateEntry entry);
}
