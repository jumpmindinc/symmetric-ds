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
package org.jumpmind.symmetric.transport.http;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A X509TrustManager that accepts self-signed certificates.
 * </p>
 * <p>
 * This trust manager SHOULD NOT be used for production systems due to security reasons unless you are aware of security implications of accepting self-signed
 * certificates.
 * </p>
 * 
 *
 * 
 */
public class SelfSignedX509TrustManager implements X509TrustManager {
    private X509TrustManager standardTrustManager = null;
    /** Log object for this class. */
    protected Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Constructor for SelfSignedX509TrustManager.
     */
    public SelfSignedX509TrustManager(KeyStore keystore) throws NoSuchAlgorithmException, KeyStoreException {
        super();
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keystore);
        TrustManager[] trustmanagers = factory.getTrustManagers();
        if (trustmanagers.length == 0) {
            throw new NoSuchAlgorithmException("no trust manager found");
        }
        this.standardTrustManager = (X509TrustManager) trustmanagers[0];
    }

    /**
     * @see javax.net.ssl.X509TrustManager#checkClientTrusted(X509Certificate[],String authType)
     */
    public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        standardTrustManager.checkClientTrusted(certificates, authType);
    }

    /**
     * @see javax.net.ssl.X509TrustManager#checkServerTrusted(X509Certificate[],String authType)
     */
    public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        if ((certificates != null) && log.isDebugEnabled()) {
            log.debug("Server certificate chain:");
            for (int i = 0; i < certificates.length; i++) {
                log.debug("X509Certificate[" + i + "]=" + certificates[i]);
            }
        }
        if (certificates != null) {
            if (certificates.length == 1 && certificates[0] != null) {
                certificates[0].checkValidity();
                return;
            } else if (certificates.length > 1 && certificates[0] != null) {
                boolean certificatesAreEqual = true;
                for (int i = 1; i < certificates.length; i++) {
                    certificatesAreEqual &= certificates[0].equals(certificates[i]);
                }
                if (certificatesAreEqual) {
                    certificates[0].checkValidity();
                    return;
                }
            }
        }
        standardTrustManager.checkServerTrusted(certificates, authType);
    }

    /**
     * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
     */
    public X509Certificate[] getAcceptedIssuers() {
        return this.standardTrustManager.getAcceptedIssuers();
    }
}
