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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;

class HttpConnectionTest {
    @Test
    void testGetURL_returnsUrlPassedToConstructor() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        URL url = buildUrl(connMock);
        try (HttpConnection httpConnection = new HttpConnection(url)) {
            assertSame(url, httpConnection.getURL());
        }
    }

    @Test
    void testDisconnect() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.disconnect();
            verify(connMock).disconnect();
        }
    }

    @Test
    void testClose() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertDoesNotThrow(httpConnection::close);
        }
    }

    @Test
    void testGetContentEncoding() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        when(connMock.getContentEncoding()).thenReturn("gzip");
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertEquals("gzip", httpConnection.getContentEncoding());
        }
    }

    @Test
    void testGetInputStream() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        InputStream inputStream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        when(connMock.getInputStream()).thenReturn(inputStream);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertSame(inputStream, httpConnection.getInputStream());
        }
    }

    @Test
    void testGetInputStream_throwsIOException() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        when(connMock.getInputStream()).thenThrow(new IOException("failed"));
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertThrows(IOException.class, httpConnection::getInputStream);
        }
    }

    @Test
    void testGetOutputStream() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        OutputStream outputStream = new ByteArrayOutputStream();
        when(connMock.getOutputStream()).thenReturn(outputStream);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertSame(outputStream, httpConnection.getOutputStream());
        }
    }

    @Test
    void testGetOutputStream_throwsIOException() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        when(connMock.getOutputStream()).thenThrow(new IOException("failed"));
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertThrows(IOException.class, httpConnection::getOutputStream);
        }
    }

    @Test
    void testSetConnectTimeout() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setConnectTimeout(5000);
            verify(connMock).setConnectTimeout(5000);
        }
    }

    @Test
    void testSetReadTimeout() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setReadTimeout(6000);
            verify(connMock).setReadTimeout(6000);
        }
    }

    @Test
    void testSetDoInput() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setDoInput(true);
            verify(connMock).setDoInput(true);
        }
    }

    @Test
    void testSetDoOutput() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setDoOutput(true);
            verify(connMock).setDoOutput(true);
        }
    }

    @Test
    void testSetAllowUserInteraction() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setAllowUserInteraction(true);
            verify(connMock).setAllowUserInteraction(true);
        }
    }

    @Test
    void testSetUseCaches() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setUseCaches(false);
            verify(connMock).setUseCaches(false);
        }
    }

    @Test
    void testSetRequestProperty() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setRequestProperty("Content-Type", "text/xml");
            verify(connMock).setRequestProperty("Content-Type", "text/xml");
        }
    }

    @Test
    void testAddRequestProperty() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.addRequestProperty("Accept-Encoding", "gzip");
            verify(connMock).addRequestProperty("Accept-Encoding", "gzip");
        }
    }

    @Test
    void testSetChunkedStreamingMode() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setChunkedStreamingMode(1024);
            verify(connMock).setChunkedStreamingMode(1024);
        }
    }

    @Test
    void testGetHeaderField() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        when(connMock.getHeaderField("Location")).thenReturn("http://redirect.example.com");
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertEquals("http://redirect.example.com", httpConnection.getHeaderField("Location"));
        }
    }

    @Test
    void testGetHeaderFields() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        Map<String, List<String>> headerFields = new HashMap<>();
        when(connMock.getHeaderFields()).thenReturn(headerFields);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertSame(headerFields, httpConnection.getHeaderFields());
        }
    }

    @Test
    void testSetInstanceFollowRedirects() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setInstanceFollowRedirects(false);
            verify(connMock).setInstanceFollowRedirects(false);
        }
    }

    @Test
    void testSetRequestMethod() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setRequestMethod("POST");
            verify(connMock).setRequestMethod("POST");
        }
    }

    @Test
    void testSetRequestMethod_throwsProtocolException() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        doThrow(new ProtocolException("bad method")).when(connMock).setRequestMethod("INVALID");
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertThrows(ProtocolException.class, () -> httpConnection.setRequestMethod("INVALID"));
        }
    }

    @Test
    void testGetResponseCode() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        when(connMock.getResponseCode()).thenReturn(HttpConnection.HTTP_OK);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertEquals(HttpConnection.HTTP_OK, httpConnection.getResponseCode());
        }
    }

    @Test
    void testGetResponseCode_throwsIOException() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        when(connMock.getResponseCode()).thenThrow(new IOException("failed"));
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertThrows(IOException.class, httpConnection::getResponseCode);
        }
    }

    @Test
    void testSetHostnameVerifier_withHttpsConnectionSetsVerifier() throws Exception {
        HttpsURLConnection connMock = mock(HttpsURLConnection.class);
        HostnameVerifier verifier = mock(HostnameVerifier.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setHostnameVerifier(verifier);
            verify(connMock).setHostnameVerifier(verifier);
        }
    }

    @Test
    void testSetHostnameVerifier_withPlainHttpConnectionDoesNothing() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        HostnameVerifier verifier = mock(HostnameVerifier.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertDoesNotThrow(() -> httpConnection.setHostnameVerifier(verifier));
        }
    }

    @Test
    void testSetSslSocketFactory_withHttpsConnectionSetsFactory() throws Exception {
        HttpsURLConnection connMock = mock(HttpsURLConnection.class);
        SSLSocketFactory factory = mock(SSLSocketFactory.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            httpConnection.setSslSocketFactory(factory);
            verify(connMock).setSSLSocketFactory(factory);
        }
    }

    @Test
    void testSetSslSocketFactory_withPlainHttpConnectionDoesNothing() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        SSLSocketFactory factory = mock(SSLSocketFactory.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertDoesNotThrow(() -> httpConnection.setSslSocketFactory(factory));
        }
    }

    @Test
    void testGetServerCertificates_withHttpsConnectionReturnsServerCertificates() throws Exception {
        HttpsURLConnection connMock = mock(HttpsURLConnection.class);
        Certificate[] certificates = new Certificate[] { mock(Certificate.class) };
        when(connMock.getServerCertificates()).thenReturn(certificates);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertSame(certificates, httpConnection.getServerCertificates());
            verify(connMock).connect();
        }
    }

    @Test
    void testGetServerCertificates_withHttpsConnectionAndSslPeerUnverifiedExceptionReturnsEmptyArray() throws Exception {
        HttpsURLConnection connMock = mock(HttpsURLConnection.class);
        when(connMock.getServerCertificates()).thenThrow(new SSLPeerUnverifiedException("unverified"));
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertEquals(0, httpConnection.getServerCertificates().length);
        }
    }

    @Test
    void testGetServerCertificates_withHttpsConnectionAndIOExceptionOnConnectReturnsEmptyArray() throws Exception {
        HttpsURLConnection connMock = mock(HttpsURLConnection.class);
        doThrow(new IOException("connect failed")).when(connMock).connect();
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertEquals(0, httpConnection.getServerCertificates().length);
        }
    }

    @Test
    void testGetServerCertificates_withPlainHttpConnectionReturnsEmptyArray() throws Exception {
        HttpURLConnection connMock = mock(HttpURLConnection.class);
        try (HttpConnection httpConnection = newHttpConnection(connMock)) {
            assertEquals(0, httpConnection.getServerCertificates().length);
            verify(connMock, never()).connect();
        }
    }

    private URL buildUrl(HttpURLConnection delegate) throws Exception {
        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return delegate;
            }
        };
        return URL.of(URI.create("http://test.example.com/sync"), handler);
    }

    private HttpConnection newHttpConnection(HttpURLConnection delegate) throws Exception {
        return new HttpConnection(buildUrl(delegate));
    }
}
