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
package org.jumpmind.symmetric.transport;

import java.lang.reflect.Constructor;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.exception.SecurityException;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.transport.file.FileTransportManager;
import org.jumpmind.symmetric.transport.http.HttpTransportManager;
import org.jumpmind.symmetric.transport.http.SelfSignedX509TrustManager;
import org.jumpmind.symmetric.transport.http.SimpleHostnameVerifier;
import org.jumpmind.symmetric.transport.internal.InternalTransportManager;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransportManagerFactory {
    private static final Logger log = LoggerFactory.getLogger(TransportManagerFactory.class);
    private static boolean isStaticInitialized;
    private ISymmetricEngine symmetricEngine;

    public TransportManagerFactory(ISymmetricEngine symmetricEngine) {
        this.symmetricEngine = symmetricEngine;
    }

    public static synchronized void initHttps(final String httpSslVerifiedServerNames,
            boolean allowSelfSignedCerts, boolean enableHttps2) {
        try {
            if (!isStaticInitialized) {
                if (!StringUtils.isBlank(httpSslVerifiedServerNames)) {
                    HostnameVerifier hostnameVerifier = new SimpleHostnameVerifier(httpSslVerifiedServerNames);
                    HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
                }
                if (allowSelfSignedCerts) {
                    initSelfSignedSocketFactory(enableHttps2);
                }
                isStaticInitialized = true;
            }
        } catch (GeneralSecurityException ex) {
            throw new SecurityException(ex);
        }
    }

    public ITransportManager create() {
        return create(symmetricEngine.getParameterService().getString(
                ParameterConstants.TRANSPORT_TYPE));
    }

    public ITransportManager create(String transport) {
        boolean isHttp = Constants.PROTOCOL_HTTP.equalsIgnoreCase(transport);
        if (isHttp || Constants.PROTOCOL_HYBRID.equalsIgnoreCase(transport)) {
            String httpSslVerifiedServerNames = symmetricEngine.getParameterService().getString(
                    ServerConstants.HTTPS_VERIFIED_SERVERS);
            // Allow self signed certs based on the parameter value.
            boolean allowSelfSignedCerts = symmetricEngine.getParameterService().is(
                    ServerConstants.HTTPS_ALLOW_SELF_SIGNED_CERTS, false);
            boolean https2Enabled = symmetricEngine.getParameterService().is(ServerConstants.HTTPS2_ENABLE, false);
            initHttps(httpSslVerifiedServerNames, allowSelfSignedCerts, https2Enabled);
            if (isHttp) {
                return createHttpTransportManager(symmetricEngine);
            }
            return new HybridTransportManager(symmetricEngine);
        } else if (Constants.PROTOCOL_FILE.equalsIgnoreCase(transport)) {
            return new FileTransportManager(symmetricEngine);
        } else if (Constants.PROTOCOL_INTERNAL.equalsIgnoreCase(transport)) {
            return createInternalTransportManager(symmetricEngine);
        } else {
            throw new IllegalStateException("An invalid transport type of " + transport
                    + " was specified.");
        }
    }

    public static HttpTransportManager createHttpTransportManager(ISymmetricEngine symmetricEngine) {
        String impl = symmetricEngine.getParameterService().getString(ServerConstants.HTTP_TRANSPORT_MANAGER_CLASS);
        if (StringUtils.isEmpty(impl)) {
            return new HttpTransportManager(symmetricEngine);
        } else {
            String className = impl.trim();
            try {
                Class<?> clazz = ClassUtils.getClass(className);
                HttpTransportManager httpTransportManager = null;
                for (Constructor<?> c : clazz.getConstructors()) {
                    if (c.getParameterTypes().length == 1
                            && c.getParameterTypes()[0].isAssignableFrom(ISymmetricEngine.class)) {
                        httpTransportManager = (HttpTransportManager) c.newInstance(symmetricEngine);
                    }
                }
                if (httpTransportManager == null) {
                    httpTransportManager = (HttpTransportManager) clazz.getDeclaredConstructor().newInstance();
                }
                return httpTransportManager;
            } catch (Exception ex) {
                throw new SymmetricException("Failed to create custom HttpTransportManager impl '" + impl + "'", ex);
            }
        }
    }

    public static InternalTransportManager createInternalTransportManager(ISymmetricEngine symmetricEngine) {
        return AppUtils.newInstance(InternalTransportManager.class, InternalTransportManager.class,
                new Object[] { symmetricEngine }, new Class<?>[] { ISymmetricEngine.class });
    }

    /**
     * Create an SSL Socket Factory that accepts self signed certificates.
     * 
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws KeyStoreException
     */
    private static void initSelfSignedSocketFactory(boolean enableHttps2)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        SSLContext context = SSLContext.getInstance("TLS");
        ISecurityService securityService = SecurityServiceFactory.create();
        KeyStore trustStore = null;
        try {
            trustStore = securityService.getTrustStore();
        } catch (Exception e) {
            log.warn("No trust store found: " + e.getMessage());
        }
        X509TrustManager trustManager = new SelfSignedX509TrustManager(trustStore);
        KeyManager[] keyManagers = null;
        try {
            keyManagers = securityService.getKeyManagerFactory().getKeyManagers();
        } catch (Exception e) {
            log.warn("No key managers found: " + e.getMessage());
        }
        context.init(keyManagers, new TrustManager[] { trustManager }, new SecureRandom());
        SSLSocketFactory sslSocketFactory = context.getSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
    }
}