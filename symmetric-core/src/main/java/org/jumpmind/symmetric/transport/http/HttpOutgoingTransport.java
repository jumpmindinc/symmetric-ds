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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.jumpmind.exception.HttpException;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.InitialLoadPendingException;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.service.RegistrationPendingException;
import org.jumpmind.symmetric.service.RegistrationRequiredException;
import org.jumpmind.symmetric.transport.AuthenticationException;
import org.jumpmind.symmetric.transport.AuthenticationExpiredException;
import org.jumpmind.symmetric.transport.ConnectionDuplicateException;
import org.jumpmind.symmetric.transport.ConnectionRejectedException;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.NoReservationException;
import org.jumpmind.symmetric.transport.ServiceNotReadyException;
import org.jumpmind.symmetric.transport.ServiceUnavailableException;
import org.jumpmind.symmetric.transport.SyncDisabledException;
import org.jumpmind.symmetric.web.WebConstants;

public class HttpOutgoingTransport implements IOutgoingWithResponseTransport {
    static final String CRLF = "\r\n";
    private String boundary;
    private HttpTransportManager httpTransportManager;
    private URL url;
    private OutputStream os;
    private BufferedWriter writer;
    private BufferedReader reader;
    private HttpConnection connection;
    private int httpTimeout;
    private int httpConnectTimeout;
    private boolean useCompression;
    private int compressionStrategy;
    private int compressionLevel;
    private String nodeId;
    private String securityToken;
    private boolean streamOutputEnabled = false;
    private int streamOutputChunkSize = 30720;
    private boolean fileUpload = false;
    private Map<String, String> requestProperties;

    public HttpOutgoingTransport(HttpTransportManager httpTransportManager, URL url, int httpTimeout, int httpConnectTimeout, boolean useCompression,
            int compressionStrategy, int compressionLevel, String nodeId,
            String securityToken, boolean streamOutputEnabled, int streamOutputSize,
            boolean fileUpload) {
        this.httpTransportManager = httpTransportManager;
        this.url = url;
        this.httpTimeout = httpTimeout;
        this.httpConnectTimeout = httpConnectTimeout;
        this.useCompression = useCompression;
        this.compressionLevel = compressionLevel;
        this.compressionStrategy = compressionStrategy;
        this.nodeId = nodeId;
        this.securityToken = securityToken;
        this.streamOutputChunkSize = streamOutputSize;
        this.streamOutputEnabled = streamOutputEnabled;
        this.fileUpload = fileUpload;
    }

    public HttpOutgoingTransport(HttpTransportManager httpTransportManager, URL url, int httpTimeout, int httpConnectTimeout, boolean useCompression,
            int compressionStrategy, int compressionLevel, String nodeId,
            String securityToken, boolean streamOutputEnabled, int streamOutputSize,
            boolean fileUpload, Map<String, String> requestProperties) {
        this(httpTransportManager, url, httpTimeout, httpConnectTimeout, useCompression, compressionStrategy, compressionLevel, nodeId, securityToken,
                streamOutputEnabled, streamOutputSize, fileUpload);
        this.requestProperties = requestProperties;
    }

    public void close() {
        closeWriter(true);
        closeOutputStream(true);
        closeReader();
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }

    private void closeReader() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
            }
            reader = null;
        }
    }

    private void closeOutputStream(boolean closeQuietly) {
        if (os != null) {
            try {
                if (fileUpload) {
                    IOUtils.write(CRLF + "--" + boundary + "--" + CRLF, os, Charset.defaultCharset());
                }
                os.flush();
            } catch (IOException ex) {
                throw new IoException(ex);
            } finally {
                if (closeQuietly) {
                    try {
                        os.close();
                    } catch (IOException e) {
                    }
                } else {
                    try {
                        os.close();
                    } catch (IOException ex) {
                        throw new IoException(ex);
                    }
                }
                os = null;
            }
        }
    }

    private void closeWriter(boolean closeQuietly) {
        if (writer != null) {
            try {
                if (fileUpload) {
                    IOUtils.write(CRLF + "--" + boundary + "--" + CRLF, os, Charset.defaultCharset());
                }
                writer.flush();
            } catch (IOException ex) {
                throw new IoException(ex);
            } finally {
                if (closeQuietly) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                    }
                } else {
                    try {
                        writer.close();
                    } catch (IOException ex) {
                        throw new IoException(ex);
                    }
                }
                writer = null;
                os = null;
            }
        }
    }

    private HttpConnection requestReservation(String queue) {
        try {
            connection = httpTransportManager.openConnection(url, nodeId, securityToken);
            connection.setUseCaches(false);
            connection.setConnectTimeout(httpConnectTimeout);
            connection.setReadTimeout(httpTimeout);
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty(WebConstants.CHANNEL_QUEUE, queue);
            analyzeResponseCode(connection.getResponseCode());
            httpTransportManager.updateSession(connection);
        } catch (IOException ex) {
            throw new IoException(ex);
        }
        return connection;
    }

    public OutputStream openStream() {
        try {
            connection = httpTransportManager.openConnection(url, nodeId, securityToken);
            if (streamOutputEnabled) {
                connection.setChunkedStreamingMode(streamOutputChunkSize);
            }
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(httpConnectTimeout);
            connection.setReadTimeout(httpTimeout);
            if (requestProperties != null) {
                for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
                    connection.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
                }
            }
            if (!fileUpload) {
                connection.setRequestMethod("PUT");
                connection.setRequestProperty("Accept-Encoding", "gzip");
                if (useCompression) {
                    connection.addRequestProperty("Content-Type", "gzip"); // application/x-gzip?
                }
            } else {
                connection.setRequestMethod("POST");
                boundary = Long.toHexString(System.currentTimeMillis());
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            }
            os = connection.getOutputStream();
            if (!fileUpload && useCompression) {
                os = new GZIPOutputStream(os, 128, true) {
                    {
                        this.def.setLevel(compressionLevel);
                        this.def.setStrategy(compressionStrategy);
                    }
                };
            }
            if (fileUpload) {
                final String fileName = "file.zip";
                IOUtils.write("--" + boundary + CRLF, os, Charset.defaultCharset());
                IOUtils.write("Content-Disposition: form-data; name=\"binaryFile\"; filename=\""
                        + fileName + "\"" + CRLF, os, Charset.defaultCharset());
                IOUtils.write("Content-Type: " + URLConnection.guessContentTypeFromName(fileName)
                        + CRLF, os, Charset.defaultCharset());
                IOUtils.write("Content-Transfer-Encoding: binary" + CRLF + CRLF, os, Charset.defaultCharset());
                os.flush();
            }
            return os;
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    public BufferedWriter openWriter() {
        OutputStreamWriter wout = new OutputStreamWriter(openStream(), StandardCharsets.UTF_8);
        writer = new BufferedWriter(wout);
        return writer;
    }

    @Override
    public BufferedWriter getWriter() {
        return writer;
    }

    private void analyzeResponseCode(int code) {
        httpTransportManager.checkResponseCode(connection, code);
        if (WebConstants.SC_SERVICE_BUSY == code) {
            throw new ConnectionRejectedException();
        } else if (WebConstants.SC_SERVICE_UNAVAILABLE == code) {
            throw new ServiceUnavailableException();
        } else if (WebConstants.SC_SERVICE_NOT_READY == code) {
            throw new ServiceNotReadyException();
        } else if (WebConstants.SC_NO_RESERVATION == code) {
            throw new NoReservationException();
        } else if (WebConstants.SC_ALREADY_CONNECTED == code) {
            throw new ConnectionDuplicateException();
        } else if (WebConstants.SC_FORBIDDEN == code) {
            httpTransportManager.clearSession(connection);
            throw new AuthenticationException();
        } else if (WebConstants.SC_AUTH_EXPIRED == code) {
            httpTransportManager.clearSession(connection);
            throw new AuthenticationExpiredException();
        } else if (WebConstants.SYNC_DISABLED == code) {
            throw new SyncDisabledException();
        } else if (WebConstants.REGISTRATION_REQUIRED == code) {
            throw new RegistrationRequiredException();
        } else if (WebConstants.REGISTRATION_PENDING == code) {
            throw new RegistrationPendingException();
        } else if (WebConstants.INITIAL_LOAD_PENDING == code) {
            throw new InitialLoadPendingException();
        } else if (WebConstants.REGISTRATION_NOT_OPEN == code) {
            throw new RegistrationNotOpenException();
        } else if (code != WebConstants.SC_OK) {
            throw new HttpException(code, "Received an unexpected response code of " + code + " from the server");
        }
    }

    public BufferedReader readResponse() throws IOException {
        closeWriter(false);
        closeOutputStream(false);
        analyzeResponseCode(connection.getResponseCode());
        httpTransportManager.updateSession(connection);
        this.reader = HttpTransportManager.getReaderFrom(connection);
        return this.reader;
    }

    public boolean isOpen() {
        return connection != null;
    }

    public NodeChannels getSuspendIgnoreChannelLists(IConfigurationService configurationService, String queue, Node targetNode) {
        NodeChannels suspendIgnoreChannelsList = new NodeChannels();
        String targetNodeId = targetNode.getNodeId();
        try (HttpConnection connection = requestReservation(queue)) {
            // Connection contains remote suspend/ignore channels list if
            // reservation was successful.
            String suspends = connection.getHeaderField(WebConstants.SUSPENDED_CHANNELS);
            String ignores = connection.getHeaderField(WebConstants.IGNORED_CHANNELS);
            suspendIgnoreChannelsList.addSuspendChannels(targetNodeId, suspends);
            suspendIgnoreChannelsList.addIgnoreChannels(targetNodeId, ignores);
            NodeChannels localSuspendIgnoreChannelsList = configurationService.getSuspendIgnoreChannelLists();
            suspendIgnoreChannelsList.addSuspendChannels(localSuspendIgnoreChannelsList.getSuspendChannels());
            suspendIgnoreChannelsList.addIgnoreChannels(localSuspendIgnoreChannelsList.getIgnoreChannels());
        }
        return suspendIgnoreChannelsList;
    }

    public HttpConnection getConnection() {
        return connection;
    }
}