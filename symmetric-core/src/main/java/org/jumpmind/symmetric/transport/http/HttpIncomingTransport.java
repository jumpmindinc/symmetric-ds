/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jumpmind.exception.HttpException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.service.RegistrationPendingException;
import org.jumpmind.symmetric.service.RegistrationRequiredException;
import org.jumpmind.symmetric.transport.AuthenticationException;
import org.jumpmind.symmetric.transport.AuthenticationExpiredException;
import org.jumpmind.symmetric.transport.ConnectionDuplicateException;
import org.jumpmind.symmetric.transport.ConnectionRejectedException;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.NoContentException;
import org.jumpmind.symmetric.transport.ServiceNotReadyException;
import org.jumpmind.symmetric.transport.ServiceUnavailableException;
import org.jumpmind.symmetric.transport.SyncDisabledException;
import org.jumpmind.symmetric.transport.TransportUtils;
import org.jumpmind.symmetric.web.WebConstants;

public class HttpIncomingTransport implements IIncomingTransport {
    private HttpTransportManager httpTransportManager;
    private HttpConnection connection;
    private BufferedReader reader;
    private InputStream is;
    private IParameterService parameterService;
    private int httpTimeout;
    private String redirectionUrl;
    private String nodeId;
    private String securityToken;
    private Map<String, String> requestProperties;

    public HttpIncomingTransport(HttpTransportManager httpTransportManager, HttpConnection connection, IParameterService parameterService) {
        this.httpTransportManager = httpTransportManager;
        this.connection = connection;
        this.parameterService = parameterService;
        this.httpTimeout = parameterService.getInt(ParameterConstants.TRANSPORT_HTTP_TIMEOUT);
    }

    public HttpIncomingTransport(HttpTransportManager httpTransportManager, HttpConnection connection, IParameterService parameterService,
            Map<String, String> requestProperties) {
        this(httpTransportManager, connection, parameterService);
        this.requestProperties = requestProperties;
    }

    public HttpIncomingTransport(HttpTransportManager httpTransportManager, HttpConnection connection, IParameterService parameterService,
            String nodeId, String securityToken) {
        this(httpTransportManager, connection, parameterService);
        this.nodeId = nodeId;
        this.securityToken = securityToken;
    }

    @Override
    public String getUrl() {
        return this.connection.getURL().toExternalForm();
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
            }
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
            }
            reader = null;
        }
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
            }
            is = null;
        }
    }

    @Override
    public boolean isOpen() {
        return reader != null;
    }

    @Override
    public String getRedirectionUrl() {
        return redirectionUrl;
    }

    @Override
    public InputStream openStream() throws IOException {
        applyRequestProperties();
        boolean manualRedirects = parameterService.is(ParameterConstants.TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED, true);
        if (manualRedirects) {
            connection = openConnectionCheckRedirects();
        }
        int code = connection.getResponseCode();
        httpTransportManager.checkResponseCode(connection, code);
        switch (code) {
            case WebConstants.REGISTRATION_NOT_OPEN:
                throw new RegistrationNotOpenException();
            case WebConstants.REGISTRATION_REQUIRED:
                throw new RegistrationRequiredException();
            case WebConstants.REGISTRATION_PENDING:
                throw new RegistrationPendingException();
            case WebConstants.SYNC_DISABLED:
                throw new SyncDisabledException();
            case WebConstants.SC_SERVICE_BUSY:
                throw new ConnectionRejectedException();
            case WebConstants.SC_ALREADY_CONNECTED:
                throw new ConnectionDuplicateException();
            case WebConstants.SC_SERVICE_UNAVAILABLE:
                throw new ServiceUnavailableException();
            case WebConstants.SC_SERVICE_NOT_READY:
                throw new ServiceNotReadyException();
            case WebConstants.SC_FORBIDDEN:
                httpTransportManager.clearSession(connection);
                throw new AuthenticationException();
            case WebConstants.SC_AUTH_EXPIRED:
                httpTransportManager.clearSession(connection);
                throw new AuthenticationExpiredException();
            case WebConstants.SC_NO_CONTENT:
                throw new NoContentException();
            case WebConstants.SC_OK:
                httpTransportManager.updateSession(connection);
                is = HttpTransportManager.getInputStreamFrom(connection);
                return is;
            default:
                throw new HttpException(code, "Received an unexpected response code of " + code + " from the server");
        }
    }

    @Override
    public BufferedReader openReader() throws IOException {
        InputStream stream = openStream();
        reader = TransportUtils.toReader(stream);
        return reader;
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (String name : connection.getHeaderFields().keySet()) {
            if (name != null) {
                headers.put(name, connection.getHeaderField(name));
            }
        }
        return headers;
    }

    /**
     * This method support redirection from an http connection to an https connection. See
     * {@link http://java.sun.com/j2se/1.4.2/docs/guide/deployment/deployment-guide/upgrade-guide/article-17.html} for more information.
     * 
     * @param connection
     * @return
     * @throws IOException
     */
    private HttpConnection openConnectionCheckRedirects() throws IOException {
        boolean redir;
        int redirects = 0;
        do {
            connection.setInstanceFollowRedirects(false);
            redir = false;
            int stat = connection.getResponseCode();
            if (stat >= 300 && stat <= 307 && stat != 306 && stat != HttpConnection.HTTP_NOT_MODIFIED) {
                URL base = connection.getURL();
                redirectionUrl = connection.getHeaderField("Location");
                URL target = null;
                if (redirectionUrl != null) {
                    target = new URL(base, redirectionUrl);
                }
                connection.disconnect();
                // Redirection should be allowed only for HTTP and HTTPS
                // and should be limited to 5 redirections at most.
                if (target == null || !(target.getProtocol().equals("http")
                        || target.getProtocol().equals("https"))
                        || redirects >= 5) {
                    throw new SecurityException("illegal URL redirect");
                }
                redir = true;
                connection = httpTransportManager.openConnection(target, nodeId, securityToken);
                connection.setConnectTimeout(httpTimeout);
                connection.setReadTimeout(httpTimeout);
                applyRequestProperties();
                redirects++;
            }
        } while (redir);
        return connection;
    }

    protected void applyRequestProperties() throws IOException {
        if (requestProperties != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                httpTransportManager.writeRequestProperties(requestProperties, os);
            }
        }
    }

    public HttpConnection getConnection() {
        return connection;
    }

    public Map<String, String> getRequestProperties() {
        return requestProperties;
    }

    public void setRequestProperties(Map<String, String> requestProperties) {
        this.requestProperties = requestProperties;
    }
}