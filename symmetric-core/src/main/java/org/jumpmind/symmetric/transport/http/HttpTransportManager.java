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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.transport.AbstractTransportManager;
import org.jumpmind.symmetric.transport.IHttpConnectionHandler;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.TransportUtils;
import org.jumpmind.symmetric.web.WebConstants;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allow remote communication to nodes, in order to push data, pull data, and send messages.
 */
public class HttpTransportManager extends AbstractTransportManager implements ITransportManager {
    private static final Logger log = LoggerFactory.getLogger(HttpTransportManager.class);
    public static final int DEFAULT_MAX_FORM_KEYS = 100000;
    protected ISymmetricEngine engine;
    protected Map<String, String> sessionIdByUri = new HashMap<String, String>();
    protected boolean useHeaderSecurityToken;
    protected boolean useSessionAuth;
    protected int backOffPostCount;
    protected IHttpResumeCache resumeCache;

    public HttpTransportManager() {
    }

    public HttpTransportManager(ISymmetricEngine engine) {
        super(engine.getExtensionService());
        this.engine = engine;
        useHeaderSecurityToken = engine.getParameterService().is(ParameterConstants.TRANSPORT_HTTP_USE_HEADER_SECURITY_TOKEN);
        useSessionAuth = engine.getParameterService().is(ParameterConstants.TRANSPORT_HTTP_USE_SESSION_AUTH);
        resumeCache = AppUtils.newInstance(IHttpResumeCache.class, DefaultHttpResumeCache.class,
                new Object[] { engine }, new Class<?>[] { ISymmetricEngine.class });
    }

    @Override
    public IHttpResumeCache getResumeCache() {
        return resumeCache;
    }

    public int sendCopyRequest(Node local) throws IOException {
        StringBuilder data = new StringBuilder();
        Map<String, BatchId> batchIds = engine.getIncomingBatchService().findMaxBatchIdsByChannel();
        for (String channelId : batchIds.keySet()) {
            if (!Constants.CHANNEL_CONFIG.equals(channelId) && !Constants.CHANNEL_HEARTBEAT.equals(channelId) && !Constants.CHANNEL_SYSTEM.equals(channelId)) {
                BatchId batchId = batchIds.get(channelId);
                append(data, channelId + "-" + batchId.getNodeId(), batchId.getBatchId());
            }
        }
        String securityToken = engine.getNodeService().findNodeSecurity(local.getNodeId())
                .getNodePassword();
        String url = addNodeInfo(engine.getParameterService().getRegistrationUrl() + "/" + WebConstants.URL_COPY, local.getNodeId(), securityToken, false);
        url = add(url, WebConstants.EXTERNAL_ID, engine.getParameterService().getExternalId(), "&");
        url = add(url, WebConstants.NODE_GROUP_ID, engine.getParameterService().getNodeGroupId(), "&");
        log.info("Contact server to do node copy using a url of: " + url);
        return sendMessage(URI.create(url).toURL(), local.getNodeId(), securityToken, null, data.toString());
    }

    @Override
    public int sendStatusRequest(Node local, Map<String, String> statuses) throws IOException {
        String securityToken = engine.getNodeService().findNodeSecurity(local.getNodeId()).getNodePassword();
        String url = addNodeInfo(engine.getParameterService().getRegistrationUrl() + "/" + WebConstants.URL_PUSHSTATUS + "/", local.getNodeId(), securityToken,
                false);
        url = add(url, WebConstants.EXTERNAL_ID, engine.getParameterService().getExternalId(), "&");
        url = add(url, WebConstants.NODE_GROUP_ID, engine.getParameterService().getNodeGroupId(), "&");
        for (String key : statuses.keySet()) {
            url = add(url, key, statuses.get(key), "&");
        }
        log.debug("Sending status with URL: " + url);
        return sendMessage(URI.create(url).toURL(), local.getNodeId(), securityToken, null, "");
    }

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local,
            String securityToken, String registrationUrl) throws IOException {
        return sendAcknowledgement(remote, list, local, securityToken, null, registrationUrl);
    }

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local,
            String securityToken, Map<String, String> requestProperties, String registrationUrl) throws IOException {
        int statusCode = HttpConnection.HTTP_OK;
        if (list != null && list.size() > 0) {
            int maxFormKeys = engine.getParameterService().getInt(ParameterConstants.TRANSPORT_MAX_FORM_KEYS);
            if (backOffPostCount > 0 && maxFormKeys <= 0) {
                maxFormKeys = DEFAULT_MAX_FORM_KEYS;
            }
            for (int i = 0; i < backOffPostCount && maxFormKeys > 1; i++) {
                maxFormKeys /= 2;
            }
            int maxByteSize = engine.getParameterService().getInt(ParameterConstants.TRANSPORT_MAX_BYTES_TO_SYNC);
            for (String data : getAcknowledgementData(remote.requires13Compatiblity(), local.getNodeId(), list, maxFormKeys, maxByteSize)) {
                log.debug("Sending ack: {}", data);
                statusCode = sendMessage("ack", remote, local, data, securityToken, requestProperties, registrationUrl);
                if (statusCode != HttpConnection.HTTP_OK) {
                    if (statusCode != WebConstants.REGISTRATION_REQUIRED && statusCode != WebConstants.REGISTRATION_PENDING
                            && statusCode != WebConstants.SYNC_DISABLED && statusCode != WebConstants.SC_FORBIDDEN
                            && statusCode != WebConstants.SC_AUTH_EXPIRED) {
                        if (maxFormKeys > 0 && maxFormKeys <= FORM_KEYS_PER_BATCH) {
                            log.error("Ack received a {} response from node {}. The form key limit of {} cannot be reduced any further.",
                                    statusCode, remote.getNodeId(), maxFormKeys);
                        } else {
                            backOffPostCount++;
                            if (maxFormKeys > FORM_KEYS_PER_BATCH) {
                                log.warn("Ack received a {} response from node {}. The form key limit will be reduced from {} to {} during the next attempt.",
                                        statusCode, remote.getNodeId(), maxFormKeys, Math.max(maxFormKeys / 2, FORM_KEYS_PER_BATCH));
                            } else {
                                log.warn("Ack received a {} response from node {}. A form key limit of {} will take effect during the next attempt.",
                                        statusCode, remote.getNodeId(), DEFAULT_MAX_FORM_KEYS / 2);
                            }
                        }
                    }
                    break;
                }
            }
        }
        return statusCode;
    }

    public void writeAcknowledgement(OutputStream out, Node remote, List<IncomingBatch> list, Node local,
            String securityToken) throws IOException {
        for (String data : getAcknowledgementData(remote.requires13Compatiblity(), local.getNodeId(), list, -1, -1)) {
            log.debug("Sending ack: {}", data);
            writeMessage(out, data);
        }
    }

    protected int sendMessage(String action, Node remote, Node local, String data,
            String securityToken, Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return sendMessage(URI.create(buildURL(action, remote, local, securityToken, registrationUrl)).toURL(), local.getNodeId(), securityToken,
                requestProperties, data);
    }

    protected int sendMessage(URL url, String nodeId, String securityToken, Map<String, String> requestProperties, String data) throws IOException {
        int rc = 0;
        try (HttpConnection conn = openConnection(url, nodeId, securityToken)) {
            if (requestProperties != null) {
                for (String key : requestProperties.keySet()) {
                    conn.addRequestProperty(key, requestProperties.get(key));
                }
            }
            conn.setRequestMethod(WebConstants.METHOD_POST);
            conn.setAllowUserInteraction(false);
            conn.setDoOutput(true);
            conn.setConnectTimeout(getHttpConnectTimeOutInMs());
            conn.setReadTimeout(getHttpTimeOutInMs());
            try (OutputStream os = conn.getOutputStream()) {
                writeMessage(os, data);
                checkForConnectionUpgrade(conn);
                rc = conn.getResponseCode();
                if (rc == WebConstants.SC_OK) {
                    try (InputStream is = conn.getInputStream()) {
                        byte[] bytes = new byte[32];
                        while (is.read(bytes) != -1) {
                            log.debug("Read keep-alive");
                        }
                    }
                }
            }
        }
        return rc;
    }

    protected void checkForConnectionUpgrade(HttpConnection conn) {
    }

    public HttpConnection openConnection(URL url, String nodeId, String securityToken)
            throws IOException {
        HttpConnection conn = new HttpConnection(url);
        IHttpConnectionHandler handler = extensionService.getExtensionPoint(IHttpConnectionHandler.class);
        if (handler != null) {
            handler.prepare(conn);
        }
        conn.setRequestProperty(WebConstants.HEADER_ACCEPT_CHARSET, StandardCharsets.UTF_8.name());
        boolean hasSession = false;
        if (useSessionAuth) {
            String sessionId = sessionIdByUri.get(getUri(conn));
            if (sessionId != null) {
                conn.setRequestProperty(WebConstants.HEADER_SESSION_ID, sessionId);
                hasSession = true;
            }
        }
        if (securityToken != null && useHeaderSecurityToken && !hasSession) {
            conn.setRequestProperty(WebConstants.HEADER_SECURITY_TOKEN, securityToken);
        }
        return conn;
    }

    public void checkResponseCode(HttpConnection conn, int responseCode) {
        IHttpConnectionHandler handler = extensionService.getExtensionPoint(IHttpConnectionHandler.class);
        if (handler != null) {
            handler.checkResponse(conn, responseCode);
        }
    }

    public void updateSession(HttpConnection conn) {
        if (useSessionAuth) {
            String sessionId = conn.getHeaderField(WebConstants.HEADER_SET_SESSION_ID);
            if (sessionId != null) {
                sessionIdByUri.put(getUri(conn), sessionId);
            }
        }
    }

    public void clearSession(HttpConnection conn) {
        if (useSessionAuth) {
            sessionIdByUri.remove(getUri(conn));
        }
    }

    protected String getUri(HttpConnection conn) {
        String uri = conn.getURL().toExternalForm();
        uri = uri.substring(0, uri.lastIndexOf("/"));
        return uri;
    }

    public int getOutputStreamSize() {
        return engine.getParameterService().getInt(ParameterConstants.TRANSPORT_HTTP_PUSH_STREAM_SIZE);
    }

    public boolean isOutputStreamEnabled() {
        return engine.getParameterService().is(ParameterConstants.TRANSPORT_HTTP_PUSH_STREAM_ENABLED);
    }

    public int getHttpTimeOutInMs() {
        return engine.getParameterService().getInt(ParameterConstants.TRANSPORT_HTTP_TIMEOUT);
    }

    public int getHttpConnectTimeOutInMs() {
        return engine.getParameterService().getInt(ParameterConstants.TRANSPORT_HTTP_CONNECT_TIMEOUT);
    }

    public boolean isUseCompression(Node targetNode) {
        // if the node is local, no need to use compression
        ISymmetricEngine targetEngine = AbstractSymmetricEngine.findEngineByUrl(targetNode.getSyncUrl());
        return engine.getParameterService().is(ParameterConstants.TRANSPORT_HTTP_USE_COMPRESSION_CLIENT) && targetEngine == null;
    }

    public int getCompressionLevel() {
        return engine.getParameterService().getInt(ParameterConstants.TRANSPORT_HTTP_COMPRESSION_LEVEL);
    }

    public int getCompressionStrategy() {
        return engine.getParameterService().getInt(ParameterConstants.TRANSPORT_HTTP_COMPRESSION_STRATEGY);
    }

    public void writeMessage(OutputStream out, String data) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        pw.println(data);
        pw.flush();
    }

    public IIncomingTransport getFilePullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return getFilePullTransport(remote, local, securityToken, requestProperties, registrationUrl, null);
    }

    @Override
    public IIncomingTransport getFilePullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl, Long resumeBatchId) throws IOException {
        String url = buildURL(WebConstants.URL_FILESYNC_PULL, remote, local, securityToken, registrationUrl);
        if (resumeBatchId != null) {
            url = add(url, WebConstants.BATCH_ID, String.valueOf(resumeBatchId), "&");
        }
        HttpConnection conn = createGetConnectionFor(URI.create(url).toURL(), local.getNodeId(), securityToken);
        if (requestProperties != null) {
            for (String key : requestProperties.keySet()) {
                conn.addRequestProperty(key, requestProperties.get(key));
            }
        }
        log.debug("Requesting file pull from {} with headers {}", url, requestProperties);
        return new HttpIncomingTransport(this, conn, engine.getParameterService(), local.getNodeId(), securityToken);
    }

    public IIncomingTransport getPullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return getPullTransport(remote, local, securityToken, requestProperties, registrationUrl, null);
    }

    @Override
    public IIncomingTransport getPullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl, Long resumeBatchId) throws IOException {
        String url = buildURL(WebConstants.URL_PULL, remote, local, securityToken, registrationUrl);
        if (resumeBatchId != null) {
            url = add(url, WebConstants.BATCH_ID, String.valueOf(resumeBatchId), "&");
        }
        HttpConnection conn = createGetConnectionFor(URI.create(url).toURL(), local.getNodeId(), securityToken);
        if (requestProperties != null) {
            for (String key : requestProperties.keySet()) {
                conn.addRequestProperty(key, requestProperties.get(key));
            }
        }
        log.debug("Requesting pull from {} with headers {}", url, requestProperties);
        return new HttpIncomingTransport(this, conn, engine.getParameterService(), local.getNodeId(), securityToken);
    }

    public IIncomingTransport getPingTransport(Node remote, Node local, String registrationUrl) throws IOException {
        HttpConnection conn = createGetConnectionFor(URI.create(resolveURL(remote.getSyncUrl(), registrationUrl) + "/" + WebConstants.URL_PING).toURL());
        return new HttpIncomingTransport(this, conn, engine.getParameterService());
    }

    public IOutgoingWithResponseTransport getPushTransport(Node remote, Node local,
            String securityToken, Map<String, String> requestProperties,
            String registrationUrl) throws IOException {
        URL url = URI.create(buildURL(WebConstants.URL_PUSH, remote, local, securityToken, registrationUrl)).toURL();
        return new HttpOutgoingTransport(this, url, getHttpTimeOutInMs(), getHttpConnectTimeOutInMs(), isUseCompression(remote),
                getCompressionStrategy(), getCompressionLevel(), local.getNodeId(),
                securityToken, isOutputStreamEnabled(), getOutputStreamSize(), false, requestProperties);
    }

    public IOutgoingWithResponseTransport getPushTransport(Node remote, Node local,
            String securityToken, String registrationUrl) throws IOException {
        URL url = URI.create(buildURL(WebConstants.URL_PUSH, remote, local, securityToken, registrationUrl)).toURL();
        return new HttpOutgoingTransport(this, url, getHttpTimeOutInMs(), getHttpConnectTimeOutInMs(), isUseCompression(remote),
                getCompressionStrategy(), getCompressionLevel(), local.getNodeId(),
                securityToken, isOutputStreamEnabled(), getOutputStreamSize(), false);
    }

    public IOutgoingWithResponseTransport getFilePushTransport(Node remote, Node local,
            String securityToken, String registrationUrl) throws IOException {
        URL url = URI.create(buildURL(WebConstants.URL_FILESYNC_PUSH, remote, local, securityToken, registrationUrl)).toURL();
        return new HttpOutgoingTransport(this, url, getHttpTimeOutInMs(), getHttpConnectTimeOutInMs(), isUseCompression(remote),
                getCompressionStrategy(), getCompressionLevel(), local.getNodeId(),
                securityToken, isOutputStreamEnabled(), getOutputStreamSize(), true);
    }

    public IIncomingTransport getConfigTransport(Node remote, Node local, String securityToken,
            String symmetricVersion, String configVersion, String registrationUrl) throws IOException {
        StringBuilder builder = new StringBuilder(buildURL(WebConstants.URL_CONFIG, remote, local, securityToken, registrationUrl));
        append(builder, WebConstants.SYMMETRIC_VERSION, symmetricVersion);
        append(builder, WebConstants.CONFIG_VERSION, configVersion);
        HttpConnection conn = createGetConnectionFor(URI.create(builder.toString()).toURL(), local.getNodeId(), securityToken);
        return new HttpIncomingTransport(this, conn, engine.getParameterService());
    }

    public IIncomingTransport getRegisterTransport(Node node, String registrationUrl) throws IOException {
        return getRegisterTransport(node, registrationUrl, null);
    }

    public IIncomingTransport getRegisterTransport(Node node, String registrationUrl, Map<String, String> requestProperties) throws IOException {
        return new HttpIncomingTransport(this, createGetConnectionFor(URI.create(buildRegistrationUrl(
                registrationUrl, node)).toURL()), engine.getParameterService(), TransportUtils.convertNodeToProperties(node, requestProperties));
    }

    public IOutgoingWithResponseTransport getRegisterPushTransport(Node remote, Node local) throws IOException {
        StringBuilder builder = new StringBuilder(buildRegistrationUrl(remote.getSyncUrl(), remote)).append("?");
        append(builder, WebConstants.PUSH_REGISTRATION, Boolean.TRUE);
        append(builder, WebConstants.NODE_ID, local.getNodeId());
        append(builder, WebConstants.NODE_GROUP_ID, local.getNodeGroupId());
        append(builder, WebConstants.EXTERNAL_ID, local.getExternalId());
        append(builder, WebConstants.SYNC_URL, local.getSyncUrl());
        URL url = URI.create(builder.toString()).toURL();
        return new HttpOutgoingTransport(this, url, getHttpTimeOutInMs(), getHttpConnectTimeOutInMs(), isUseCompression(remote),
                getCompressionStrategy(), getCompressionLevel(), local.getNodeId(),
                null, isOutputStreamEnabled(), getOutputStreamSize(), false);
    }

    @Override
    public IIncomingTransport getBandwidthPullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl, long sampleSize) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(buildURL(WebConstants.URL_BANDWIDTH, remote, local, securityToken, registrationUrl));
        boolean supportsPropertiesInHeader = !Version.isOlderThanVersion(remote.getSymmetricVersion(), "3.16.4");
        if (!supportsPropertiesInHeader) {
            if (requestProperties != null) {
                for (Map.Entry<String, String> entry : requestProperties.entrySet()) {
                    append(urlBuilder, entry.getKey(), entry.getValue());
                }
            }
            append(urlBuilder, WebConstants.DIRECTION, WebConstants.URL_PULL);
            append(urlBuilder, WebConstants.SAMPLE_SIZE, String.valueOf(sampleSize));
        }
        String localNodeId = local.getNodeId();
        HttpConnection conn = createGetConnectionFor(URI.create(urlBuilder.toString()).toURL(), localNodeId, securityToken);
        if (supportsPropertiesInHeader) {
            if (requestProperties != null) {
                for (Map.Entry<String, String> entry : requestProperties.entrySet()) {
                    conn.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            conn.addRequestProperty(WebConstants.HEADER_DIRECTION, WebConstants.URL_PULL);
            conn.addRequestProperty(WebConstants.HEADER_SAMPLE_SIZE, String.valueOf(sampleSize));
        }
        return new HttpIncomingTransport(this, conn, engine.getParameterService(), localNodeId, securityToken);
    }

    @Override
    public IOutgoingWithResponseTransport getBandwidthPushTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(buildURL(WebConstants.URL_BANDWIDTH, remote, local, securityToken, registrationUrl));
        boolean supportsPropertiesInHeader = !Version.isOlderThanVersion(remote.getSymmetricVersion(), "3.16.4");
        if (supportsPropertiesInHeader) {
            if (requestProperties == null) {
                requestProperties = new HashMap<String, String>();
            }
            requestProperties.put(WebConstants.HEADER_DIRECTION, WebConstants.URL_PUSH);
        } else {
            if (requestProperties != null) {
                for (Map.Entry<String, String> entry : requestProperties.entrySet()) {
                    append(urlBuilder, entry.getKey(), entry.getValue());
                }
            }
            append(urlBuilder, WebConstants.DIRECTION, WebConstants.URL_PUSH);
        }
        URL url = URI.create(urlBuilder.toString()).toURL();
        return new HttpOutgoingTransport(this, url, getHttpTimeOutInMs(), getHttpConnectTimeOutInMs(), isUseCompression(remote),
                getCompressionStrategy(), getCompressionLevel(), local.getNodeId(),
                securityToken, isOutputStreamEnabled(), getOutputStreamSize(), false, requestProperties);
    }

    @Override
    public IIncomingTransport getComparePullTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException {
        StringBuilder builder = new StringBuilder(buildURL(WebConstants.URL_COMPARE_PULL, remote, local, securityToken, registrationUrl));
        for (Map.Entry<String, String> entry : requestParameters.entrySet()) {
            append(builder, entry.getKey(), entry.getValue());
        }
        URL url = URI.create(builder.toString()).toURL();
        HttpConnection conn = createGetConnectionFor(url, local.getNodeId(), securityToken);
        conn.addRequestProperty(WebConstants.CHANNEL_QUEUE, requestParameters.get(WebConstants.CHANNEL_QUEUE));
        return new HttpIncomingTransport(this, conn, engine.getParameterService(), local.getNodeId(), securityToken);
    }

    @Override
    public IOutgoingWithResponseTransport getComparePushTransport(Node remote, Node local,
            String securityToken, String registrationUrl, Map<String, String> requestParameters) throws IOException {
        StringBuilder builder = new StringBuilder(buildURL(WebConstants.URL_COMPARE_PUSH, remote, local, securityToken, registrationUrl));
        for (Map.Entry<String, String> entry : requestParameters.entrySet()) {
            append(builder, entry.getKey(), entry.getValue());
        }
        URL url = URI.create(builder.toString()).toURL();
        Map<String, String> param = new HashMap<String, String>();
        param.put(WebConstants.CHANNEL_QUEUE, requestParameters.get(WebConstants.CHANNEL_QUEUE));
        return new HttpOutgoingTransport(this, url, getHttpTimeOutInMs(), getHttpConnectTimeOutInMs(), isUseCompression(remote),
                getCompressionStrategy(), getCompressionLevel(), local.getNodeId(), securityToken, isOutputStreamEnabled(), getOutputStreamSize(),
                false, param);
    }

    public static String buildRegistrationUrl(String baseUrl, Node node) {
        if (baseUrl == null) {
            baseUrl = "";
        }
        StringBuilder builder = new StringBuilder(baseUrl);
        builder.append("/" + WebConstants.URL_REGISTRATION);
        return builder.toString();
    }

    protected HttpConnection createGetConnectionFor(URL url, String nodeId, String securityToken) throws IOException {
        HttpConnection conn = openConnection(url, nodeId, securityToken);
        conn.setRequestProperty("accept-encoding", "gzip");
        conn.setConnectTimeout(getHttpConnectTimeOutInMs());
        conn.setReadTimeout(getHttpTimeOutInMs());
        conn.setRequestMethod(WebConstants.METHOD_GET);
        return conn;
    }

    protected HttpConnection createGetConnectionFor(URL url) throws IOException {
        return createGetConnectionFor(url, null, null);
    }

    protected static InputStream getInputStreamFrom(HttpConnection connection) throws IOException {
        String type = connection.getContentEncoding();
        InputStream in = connection.getInputStream();
        if (!StringUtils.isBlank(type) && type.equals("gzip")) {
            in = new GZIPInputStream(in);
        }
        return in;
    }

    /**
     * If the content is gzip'd, then uncompress.
     */
    protected static BufferedReader getReaderFrom(HttpConnection connection) throws IOException {
        String type = connection.getContentEncoding();
        InputStream in = connection.getInputStream();
        if (!StringUtils.isBlank(type) && type.equals("gzip")) {
            in = new GZIPInputStream(in);
        }
        return TransportUtils.toReader(in);
    }

    /**
     * Build a url for an action.
     */
    protected String buildURL(String action, Node remote, Node local, String securityToken, String registrationUrl) throws IOException {
        boolean forceParamSecurityToken = Version.isOlderMinorVersion(remote.getSymmetricVersion(), "3.11");
        String url = addNodeInfo((resolveURL(remote.getSyncUrl(), registrationUrl) + "/" + action), local.getNodeId(), securityToken,
                forceParamSecurityToken);
        log.debug("Building transport url: {}", url);
        return url;
    }

    protected String addNodeInfo(String base, String nodeId, String securityToken, boolean forceParamSecurityToken) {
        StringBuilder sb = new StringBuilder(addNodeId(base, nodeId, "?"));
        if (!useHeaderSecurityToken || forceParamSecurityToken) {
            sb.append("&").append(WebConstants.SECURITY_TOKEN).append("=").append(securityToken);
        }
        return sb.toString();
    }

    protected String addNodeId(String base, String nodeId, String connector) {
        return add(base, WebConstants.NODE_ID, nodeId, connector);
    }

    protected String add(String base, String key, String value, String connector) {
        StringBuilder sb = new StringBuilder(base);
        sb.append(connector);
        sb.append(key);
        sb.append("=");
        try {
            sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            throw new IoException(e);
        }
        return sb.toString();
    }

    protected ISymmetricEngine getEngine() {
        return engine;
    }
}