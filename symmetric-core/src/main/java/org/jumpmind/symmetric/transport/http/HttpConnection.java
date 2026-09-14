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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

public class HttpConnection implements Closeable {
    public static final int HTTP_OK = HttpURLConnection.HTTP_OK;
    public static final int HTTP_NOT_MODIFIED = HttpURLConnection.HTTP_NOT_MODIFIED;
    protected URL url;
    protected HttpURLConnection conn;

    public HttpConnection(URL url) throws IOException {
        this.url = url;
        conn = (HttpURLConnection) url.openConnection();
    }

    public void disconnect() {
        conn.disconnect();
    }

    @Override
    public void close() {
    }

    public URL getURL() {
        return url;
    }

    public String getContentEncoding() {
        return conn.getContentEncoding();
    }

    public InputStream getInputStream() throws IOException {
        return conn.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return conn.getOutputStream();
    }

    public void setConnectTimeout(int timeout) {
        conn.setConnectTimeout(timeout);
    }

    public void setReadTimeout(int timeout) {
        conn.setReadTimeout(timeout);
    }

    public void setDoInput(boolean doinput) {
        conn.setDoInput(doinput);
    }

    public void setDoOutput(boolean dooutput) {
        conn.setDoOutput(dooutput);
    }

    public void setAllowUserInteraction(boolean allowuserinteraction) {
        conn.setAllowUserInteraction(allowuserinteraction);
    }

    public void setUseCaches(boolean usecaches) {
        conn.setUseCaches(usecaches);
    }

    public void setRequestProperty(String key, String value) {
        conn.setRequestProperty(key, value);
    }

    public void addRequestProperty(String key, String value) {
        conn.addRequestProperty(key, value);
    }

    public void setChunkedStreamingMode(int chunklen) {
        conn.setChunkedStreamingMode(chunklen);
    }

    public String getHeaderField(String name) {
        return conn.getHeaderField(name);
    }

    public Map<String, List<String>> getHeaderFields() {
        return conn.getHeaderFields();
    }

    public void setInstanceFollowRedirects(boolean followRedirects) {
        conn.setInstanceFollowRedirects(followRedirects);
    }

    public void setRequestMethod(String method) throws ProtocolException {
        conn.setRequestMethod(method);
    }

    public int getResponseCode() throws IOException {
        return conn.getResponseCode();
    }

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setHostnameVerifier(hostnameVerifier);
        }
    }

    public void setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
        }
    }

    public Certificate[] getServerCertificates() {
        if (conn instanceof HttpsURLConnection) {
            try {
                conn.connect();
                return ((HttpsURLConnection) conn).getServerCertificates();
            } catch (SSLPeerUnverifiedException e) {
            } catch (IOException e) {
            }
        }
        return new Certificate[] {};
    }
}
