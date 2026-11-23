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

import java.io.BufferedReader;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore.Entry;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Security service that can generate, import and export certificates, using {@link BouncyCastleCertificateManager}.
 */
public class BouncyCastleSecurityService extends SecurityService {
    public BouncyCastleSecurityService() {
        this(SecurityServiceParts.builder().build());
    }

    /**
     * @param parts
     *            the parts to use; when they set no certificate manager factory, {@link BouncyCastleCertificateManager} is used
     * @throws IllegalArgumentException
     *             when the certificate manager factory creates something other than a {@link BouncyCastleCertificateManager}, which the deprecated methods
     *             below delegate to
     */
    protected BouncyCastleSecurityService(SecurityServiceParts parts) {
        super(withBouncyCastleCertificateManager(parts));
        if (!(getCertificateManager() instanceof BouncyCastleCertificateManager)) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " needs a " + BouncyCastleCertificateManager.class.getSimpleName()
                    + ", but its certificate manager factory created a " + getCertificateManager().getClass().getSimpleName());
        }
    }

    private static SecurityServiceParts withBouncyCastleCertificateManager(SecurityServiceParts parts) {
        if (parts.getCertificateManagerFactory().isPresent()) {
            return parts;
        }
        return parts.toBuilder().certificateManagerFactory(BouncyCastleCertificateManager::new).build();
    }

    /**
     * @deprecated Overriding this no longer changes how certificates are generated. Supply an {@link ICertificateManager} instead.
     */
    @Deprecated
    protected KeyPair generateRSAKeyPair() throws Exception {
        return getBouncyCastleCertificateManager().generateRSAKeyPair();
    }

    /**
     * @deprecated Overriding this no longer changes how certificates are generated. Supply an {@link ICertificateManager} instead.
     */
    @Deprecated
    protected X509Certificate generateV1Certificate(String host, KeyPair pair, int lifetimeInDays) throws Exception {
        return getBouncyCastleCertificateManager().generateV1Certificate(host, pair, lifetimeInDays);
    }

    /**
     * @deprecated Overriding this no longer changes how certificates are imported. Supply an {@link ICertificateManager} instead.
     */
    @Deprecated
    protected Entry createSslCert(byte[] content, String fileType, String alias, String password, boolean isKeyEntry) {
        return getBouncyCastleCertificateManager().createSslCert(content, fileType, alias, password, isKeyEntry);
    }

    /**
     * @deprecated Overriding this no longer changes how certificates are imported. Supply an {@link ICertificateManager} instead.
     */
    @Deprecated
    protected byte[] readPemBytes(BufferedReader reader) throws IOException, CertificateException {
        return getBouncyCastleCertificateManager().readPemBytes(reader);
    }

    private BouncyCastleCertificateManager getBouncyCastleCertificateManager() {
        return (BouncyCastleCertificateManager) getCertificateManager();
    }
}
