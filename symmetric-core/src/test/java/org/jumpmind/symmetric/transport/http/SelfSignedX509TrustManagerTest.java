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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfSignedX509TrustManagerTest {
    private X509TrustManager standardTrustManager;
    private SelfSignedX509TrustManager trustManager;

    @BeforeEach
    void setUp() throws Exception {
        standardTrustManager = mock(X509TrustManager.class);
        trustManager = new SelfSignedX509TrustManager(null);
        Field field = SelfSignedX509TrustManager.class.getDeclaredField("standardTrustManager");
        field.setAccessible(true);
        field.set(trustManager, standardTrustManager);
    }

    @Test
    void testConstructor_withEmptyKeystoreSucceedsWithNoTrustAnchors() throws Exception {
        KeyStore emptyKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
        emptyKeystore.load(null, null);
        assertDoesNotThrow(() -> assertNotNull(new SelfSignedX509TrustManager(emptyKeystore)));
    }

    @Test
    void testConstructor_withNullKeystoreUsesDefaultTrustStore() {
        assertDoesNotThrow(() -> assertNotNull(new SelfSignedX509TrustManager(null)));
    }

    @Test
    void testCheckClientTrusted_delegatesToStandardTrustManager() throws Exception {
        X509Certificate[] certificates = new X509Certificate[] { mock(X509Certificate.class) };
        trustManager.checkClientTrusted(certificates, "RSA");
        verify(standardTrustManager).checkClientTrusted(certificates, "RSA");
    }

    @Test
    void testCheckClientTrusted_propagatesCertificateException() throws Exception {
        X509Certificate[] certificates = new X509Certificate[] { mock(X509Certificate.class) };
        doThrow(new CertificateException("untrusted")).when(standardTrustManager).checkClientTrusted(any(), anyString());
        assertThrows(CertificateException.class, () -> trustManager.checkClientTrusted(certificates, "RSA"));
    }

    @Test
    void testCheckServerTrusted_withSingleValidCertificateReturnsWithoutDelegating() throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        X509Certificate[] certificates = new X509Certificate[] { cert };
        assertDoesNotThrow(() -> trustManager.checkServerTrusted(certificates, "RSA"));
        verify(standardTrustManager, never()).checkServerTrusted(any(), anyString());
    }

    @Test
    void testCheckServerTrusted_withSingleExpiredCertificateThrowsCertificateExpiredException() throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        doThrow(new CertificateExpiredException()).when(cert).checkValidity();
        X509Certificate[] certificates = new X509Certificate[] { cert };
        assertThrows(CertificateExpiredException.class, () -> trustManager.checkServerTrusted(certificates, "RSA"));
    }

    @Test
    void testCheckServerTrusted_withMultipleEqualCertificatesReturnsWithoutDelegating() throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        X509Certificate[] certificates = new X509Certificate[] { cert, cert };
        assertDoesNotThrow(() -> trustManager.checkServerTrusted(certificates, "RSA"));
        verify(standardTrustManager, never()).checkServerTrusted(any(), anyString());
    }

    @Test
    void testCheckServerTrusted_withMultipleUnequalCertificatesDelegatesToStandardTrustManager() throws Exception {
        X509Certificate[] certificates = new X509Certificate[] { mock(X509Certificate.class), mock(X509Certificate.class) };
        trustManager.checkServerTrusted(certificates, "RSA");
        verify(standardTrustManager).checkServerTrusted(certificates, "RSA");
    }

    @Test
    void testCheckServerTrusted_withNullCertificatesDelegatesToStandardTrustManager() throws Exception {
        trustManager.checkServerTrusted(null, "RSA");
        verify(standardTrustManager).checkServerTrusted(null, "RSA");
    }

    @Test
    void testGetAcceptedIssuers_delegatesToStandardTrustManager() {
        X509Certificate[] issuers = new X509Certificate[] { mock(X509Certificate.class) };
        when(standardTrustManager.getAcceptedIssuers()).thenReturn(issuers);
        assertArrayEquals(issuers, trustManager.getAcceptedIssuers());
    }
}
