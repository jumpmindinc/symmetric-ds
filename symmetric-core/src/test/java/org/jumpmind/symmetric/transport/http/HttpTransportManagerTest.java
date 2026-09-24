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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.IHttpConnectionHandler;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HttpTransportManagerTest {
    private HttpTransportManager manager;
    private ISymmetricEngine engine;
    private Node remoteNode;
    private Node localNode;
    private IncomingBatch batch;
    private IParameterService ps;
    private IExtensionService extensionService;
    private HttpConnection connMock;
    private CookieHandler originalCookieHandler;

    @BeforeEach
    void setUp() throws Exception {
        originalCookieHandler = CookieHandler.getDefault();
        CookieHandler.setDefault(null);
        engine = mock(ISymmetricEngine.class);
        remoteNode = mock(Node.class);
        localNode = mock(Node.class);
        batch = mock(IncomingBatch.class);
        ps = mock(IParameterService.class);
        extensionService = mock(IExtensionService.class);
        when(remoteNode.getNodeId()).thenReturn("remote-001");
        when(localNode.getNodeId()).thenReturn("local-001");
        when(remoteNode.getNodeGroupId()).thenReturn("group-remote");
        when(localNode.getNodeGroupId()).thenReturn("group-local");
        when(remoteNode.getSymmetricVersion()).thenReturn("3.18.0");
        when(engine.getParameterService()).thenReturn(ps);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService().getInt(ParameterConstants.TRANSPORT_MAX_FORM_KEYS)).thenReturn(1000);
        when(engine.getParameterService().getInt(ParameterConstants.TRANSPORT_MAX_BYTES_TO_SYNC)).thenReturn(100000);
        when(engine.getParameterService().is(anyString())).thenReturn(false);
        manager = spy(new HttpTransportManager(engine));
        doReturn(200).when(manager).sendMessage(
                anyString(), any(Node.class), any(Node.class),
                anyString(), anyString(), anyMap(), anyString());
        connMock = mock(HttpConnection.class);
        doReturn(connMock).when(manager).createGetConnectionFor(any(URL.class), anyString(), anyString());
        doReturn(connMock).when(manager).createGetConnectionFor(any(URL.class));
    }

    @AfterEach
    void tearDown() {
        CookieHandler.setDefault(originalCookieHandler);
    }

    @Test
    void testSendCopyRequest_excludesSystemChannelsAndSendsData() throws Exception {
        IIncomingBatchService incomingBatchService = mock(IIncomingBatchService.class);
        when(engine.getIncomingBatchService()).thenReturn(incomingBatchService);
        Map<String, BatchId> batchIds = new LinkedHashMap<>();
        batchIds.put(Constants.CHANNEL_CONFIG, new BatchId(1, "src-node"));
        batchIds.put("sales", new BatchId(2, "src-node"));
        when(incomingBatchService.findMaxBatchIdsByChannel()).thenReturn(batchIds);
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        NodeSecurity security = mock(NodeSecurity.class);
        when(security.getNodePassword()).thenReturn("pw");
        when(nodeService.findNodeSecurity("local-001")).thenReturn(security);
        when(ps.getRegistrationUrl()).thenReturn("http://reg.example.com");
        when(ps.getExternalId()).thenReturn("ext1");
        when(ps.getNodeGroupId()).thenReturn("group-x");
        doReturn(200).when(manager).sendMessage(any(URL.class), eq("local-001"), eq("pw"), any(), anyString());
        int result = manager.sendCopyRequest(localNode);
        assertEquals(200, result);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        ArgumentCaptor<String> dataCaptor = ArgumentCaptor.forClass(String.class);
        verify(manager).sendMessage(urlCaptor.capture(), eq("local-001"), eq("pw"), any(), dataCaptor.capture());
        assertTrue(urlCaptor.getValue().toString().contains(WebConstants.URL_COPY));
        assertTrue(dataCaptor.getValue().contains("sales-src-node=2"));
        assertFalse(dataCaptor.getValue().contains(Constants.CHANNEL_CONFIG + "-"));
    }

    @Test
    void testSendStatusRequest_buildsPushStatusUrlWithStatuses() throws Exception {
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        NodeSecurity security = mock(NodeSecurity.class);
        when(security.getNodePassword()).thenReturn("pw");
        when(nodeService.findNodeSecurity("local-001")).thenReturn(security);
        when(ps.getRegistrationUrl()).thenReturn("http://reg.example.com");
        when(ps.getExternalId()).thenReturn("ext1");
        when(ps.getNodeGroupId()).thenReturn("group-x");
        doReturn(200).when(manager).sendMessage(any(URL.class), eq("local-001"), eq("pw"), any(), anyString());
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put("cpu", "10");
        int result = manager.sendStatusRequest(localNode, statuses);
        assertEquals(200, result);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(manager).sendMessage(urlCaptor.capture(), eq("local-001"), eq("pw"), any(), anyString());
        assertTrue(urlCaptor.getValue().toString().contains(WebConstants.URL_PUSHSTATUS));
        assertTrue(urlCaptor.getValue().toString().contains("cpu=10"));
    }

    @Test
    void testSendAcknowledgement_emptyListReturnsOk() throws Exception {
        int result = manager.sendAcknowledgement(remoteNode, Collections.emptyList(), localNode, "token", "http://url");
        assertEquals(200, result);
        verify(manager, never()).sendMessage(any(), any(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void testSendAcknowledgement_nullListReturnsOk() throws Exception {
        int result = manager.sendAcknowledgement(remoteNode, null, localNode, "token", "http://url");
        assertEquals(200, result);
        verify(manager, never()).sendMessage(any(), any(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void testSendAcknowledgement_basic() throws Exception {
        List<IncomingBatch> batches = List.of(batch);
        doReturn(200).when(manager).sendMessage(
                eq("ack"), any(Node.class), any(Node.class),
                eq("ackData"), anyString(), anyMap(), anyString());
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", new HashMap<>(), "http://url");
        assertEquals(200, result);
    }

    @Test
    void testSendAcknowledgement_handlesBadRequest() throws Exception {
        List<IncomingBatch> batches = List.of(batch);
        doReturn(400).when(manager).sendMessage(anyString(), any(), any(), anyString(), any(), any(), anyString());
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", null, "http://url");
        assertEquals(400, result);
        assertEquals(1, manager.backOffPostCount);
    }

    @Test
    void testSendAcknowledgement_setsDefaultMaxFormKeys_whenBackOffAndZeroMaxFormKeys() throws Exception {
        manager.backOffPostCount = 1;
        when(ps.getInt(ParameterConstants.TRANSPORT_MAX_FORM_KEYS)).thenReturn(0);
        doReturn(200).when(manager).sendMessage(anyString(), any(Node.class), any(Node.class),
                anyString(), anyString(), anyMap(), anyString());
        List<IncomingBatch> batches = List.of(batch);
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", new HashMap<>(), "http://url");
        assertEquals(200, result);
    }

    @Test
    void testWriteAcknowledgement_writesNonEmptyDataForEachBatch() throws Exception {
        when(remoteNode.requires13Compatiblity()).thenReturn(false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manager.writeAcknowledgement(out, remoteNode, List.of(batch), localNode, "token");
        assertFalse(out.toString(StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    void testSendMessageActionOverload_delegatesToUrlOverload() throws Exception {
        doCallRealMethod().when(manager).sendMessage(anyString(), any(Node.class), any(Node.class),
                anyString(), anyString(), anyMap(), anyString());
        doReturn(201).when(manager).sendMessage(any(URL.class), anyString(), anyString(), any(), anyString());
        when(remoteNode.getSyncUrl()).thenReturn("http://remote-host/sync");
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        int result = manager.sendMessage("ack", remoteNode, localNode, "data", "token", new HashMap<>(), "http://reg");
        verify(manager).sendMessage(urlCaptor.capture(), eq("local-001"), eq("token"), anyMap(), eq("data"));
        URL expectedUrl = URI.create(manager.buildURL("ack", remoteNode, localNode, "token", "http://reg")).toURL();
        assertEquals(expectedUrl.toExternalForm(), urlCaptor.getValue().toExternalForm());
        assertEquals(201, result);
    }

    @Test
    void testSendMessage_returnsResponseCodeAndDrainsKeepAliveOnOk() throws Exception {
        doReturn(connMock).when(manager).openConnection(any(URL.class), anyString(), anyString());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        when(connMock.getOutputStream()).thenReturn(os);
        when(connMock.getResponseCode()).thenReturn(WebConstants.SC_OK);
        when(connMock.getInputStream()).thenReturn(new ByteArrayInputStream("keepalive".getBytes(StandardCharsets.UTF_8)));
        int rc = manager.sendMessage(URI.create("http://node.example.com/sync").toURL(), "node1", "token", null, "data");
        assertEquals(WebConstants.SC_OK, rc);
        verify(connMock).getInputStream();
        assertTrue(os.toString(StandardCharsets.UTF_8).contains("data"));
    }

    @Test
    void testSendMessage_skipsKeepAliveDrainWhenNotOk() throws Exception {
        doReturn(connMock).when(manager).openConnection(any(URL.class), anyString(), anyString());
        when(connMock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(connMock.getResponseCode()).thenReturn(500);
        int rc = manager.sendMessage(URI.create("http://node.example.com/sync").toURL(), "node1", "token", null, "data");
        assertEquals(500, rc);
        verify(connMock, never()).getInputStream();
    }

    @Test
    void testSendMessage_appliesRequestPropertiesToConnection() throws Exception {
        doReturn(connMock).when(manager).openConnection(any(URL.class), anyString(), anyString());
        when(connMock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(connMock.getResponseCode()).thenReturn(500);
        Map<String, String> requestProperties = new LinkedHashMap<>();
        requestProperties.put("X-Custom", "value1");
        manager.sendMessage(URI.create("http://node.example.com/sync").toURL(), "node1", "token", requestProperties, "data");
        verify(connMock).addRequestProperty("X-Custom", "value1");
    }

    @Test
    void testCheckForConnectionUpgrade_isNoOp() {
        assertDoesNotThrow(() -> manager.checkForConnectionUpgrade(mock(HttpConnection.class)));
    }

    @Test
    void testOpenConnection_withNoHandlerStillSetsAcceptCharsetHeader() throws Exception {
        HttpURLConnection httpUrlConnectionMock = mock(HttpURLConnection.class);
        URL url = newTestUrl(httpUrlConnectionMock);
        when(extensionService.getExtensionPoint(IHttpConnectionHandler.class)).thenReturn(null);
        HttpConnection conn = manager.openConnection(url, "node1", "token");
        assertEquals(url, conn.getURL());
        verify(httpUrlConnectionMock).setRequestProperty(WebConstants.HEADER_ACCEPT_CHARSET, StandardCharsets.UTF_8.name());
        verify(httpUrlConnectionMock, never()).setRequestProperty(eq(WebConstants.HEADER_SECURITY_TOKEN), anyString());
    }

    @Test
    void testOpenConnection_withHandlerInvokesPrepare() throws Exception {
        HttpURLConnection httpUrlConnectionMock = mock(HttpURLConnection.class);
        URL url = newTestUrl(httpUrlConnectionMock);
        IHttpConnectionHandler handler = mock(IHttpConnectionHandler.class);
        when(extensionService.getExtensionPoint(IHttpConnectionHandler.class)).thenReturn(handler);
        HttpConnection conn = manager.openConnection(url, "node1", "token");
        verify(handler).prepare(conn);
    }

    @Test
    void testOpenConnection_withHeaderSecurityTokenEnabledSetsSecurityTokenHeader() throws Exception {
        HttpTransportManager tokenManager = newManager(true, false);
        HttpURLConnection httpUrlConnectionMock = mock(HttpURLConnection.class);
        URL url = newTestUrl(httpUrlConnectionMock);
        tokenManager.openConnection(url, "node1", "secret");
        verify(httpUrlConnectionMock).setRequestProperty(WebConstants.HEADER_SECURITY_TOKEN, "secret");
    }

    @Test
    void testOpenConnection_withHeaderSecurityTokenDisabledOmitsSecurityTokenHeader() throws Exception {
        HttpTransportManager tokenManager = newManager(false, false);
        HttpURLConnection httpUrlConnectionMock = mock(HttpURLConnection.class);
        URL url = newTestUrl(httpUrlConnectionMock);
        tokenManager.openConnection(url, "node1", "secret");
        verify(httpUrlConnectionMock, never()).setRequestProperty(eq(WebConstants.HEADER_SECURITY_TOKEN), anyString());
    }

    @Test
    void testOpenConnection_withCachedSessionUsesSessionHeaderInsteadOfSecurityToken() throws Exception {
        HttpTransportManager sessionManager = newManager(true, true);
        HttpURLConnection httpUrlConnectionMock = mock(HttpURLConnection.class);
        URL url = newTestUrl(httpUrlConnectionMock);
        String uri = url.toExternalForm();
        sessionManager.sessionIdByUri.put(uri.substring(0, uri.lastIndexOf("/")), "sess-1");
        sessionManager.openConnection(url, "node1", "secret");
        verify(httpUrlConnectionMock).setRequestProperty(WebConstants.HEADER_SESSION_ID, "sess-1");
        verify(httpUrlConnectionMock, never()).setRequestProperty(eq(WebConstants.HEADER_SECURITY_TOKEN), anyString());
    }

    @Test
    void testCheckResponseCode_withHandlerInvokesCheckResponse() {
        IHttpConnectionHandler handler = mock(IHttpConnectionHandler.class);
        when(extensionService.getExtensionPoint(IHttpConnectionHandler.class)).thenReturn(handler);
        HttpConnection conn = mock(HttpConnection.class);
        manager.checkResponseCode(conn, 200);
        verify(handler).checkResponse(conn, 200);
    }

    @Test
    void testCheckResponseCode_withNoHandlerDoesNothing() {
        when(extensionService.getExtensionPoint(IHttpConnectionHandler.class)).thenReturn(null);
        HttpConnection conn = mock(HttpConnection.class);
        assertDoesNotThrow(() -> manager.checkResponseCode(conn, 200));
    }

    @Test
    void testUpdateSession_withSessionAuthEnabledAndHeaderPresent_storesSessionId() throws Exception {
        HttpTransportManager sessionManager = newManager(false, true);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://host/action").toURL());
        when(conn.getHeaderField(WebConstants.HEADER_SET_SESSION_ID)).thenReturn("sess-9");
        sessionManager.updateSession(conn);
        assertEquals("sess-9", sessionManager.sessionIdByUri.get("http://host"));
    }

    @Test
    void testUpdateSession_withSessionAuthDisabled_doesNothing() {
        HttpTransportManager sessionManager = newManager(false, false);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getHeaderField(WebConstants.HEADER_SET_SESSION_ID)).thenReturn("sess-9");
        sessionManager.updateSession(conn);
        assertTrue(sessionManager.sessionIdByUri.isEmpty());
    }

    @Test
    void testUpdateSession_withSessionAuthEnabledButHeaderAbsent_doesNothing() {
        HttpTransportManager sessionManager = newManager(false, true);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getHeaderField(WebConstants.HEADER_SET_SESSION_ID)).thenReturn(null);
        sessionManager.updateSession(conn);
        assertTrue(sessionManager.sessionIdByUri.isEmpty());
    }

    @Test
    void testClearSession_withSessionAuthEnabled_removesEntry() throws Exception {
        HttpTransportManager sessionManager = newManager(false, true);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://host/path").toURL());
        sessionManager.sessionIdByUri.put(sessionManager.getUri(conn), "sess-1");
        sessionManager.clearSession(conn);
        assertTrue(sessionManager.sessionIdByUri.isEmpty());
    }

    @Test
    void testClearSession_withSessionAuthDisabled_doesNothing() throws Exception {
        HttpTransportManager sessionManager = newManager(false, false);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://host/path").toURL());
        sessionManager.sessionIdByUri.put(sessionManager.getUri(conn), "sess-1");
        sessionManager.clearSession(conn);
        assertFalse(sessionManager.sessionIdByUri.isEmpty());
    }

    @Test
    void testBeginReservation_incrementsCountForUri() throws Exception {
        URL url = URI.create("http://node.example.com/sync/push?nodeId=1").toURL();
        manager.beginReservation(url);
        assertEquals(1, manager.outstandingReservationsByUri.size());
        manager.beginReservation(url);
        assertEquals(2, manager.outstandingReservationsByUri.values().iterator().next().get());
    }

    @Test
    void testBeginReservation_pushAndPullUrlsToSameHostShareCount() throws Exception {
        URL pushUrl = URI.create("http://node.example.com/sync/push?nodeId=1").toURL();
        URL pullUrl = URI.create("http://node.example.com/sync/pull?nodeId=1").toURL();
        manager.beginReservation(pushUrl);
        manager.beginReservation(pullUrl);
        assertEquals(1, manager.outstandingReservationsByUri.size());
        assertEquals(2, manager.outstandingReservationsByUri.values().iterator().next().get());
    }

    @Test
    void testEndReservation_decrementsAndRemovesEntryWhenCountReachesZero() throws Exception {
        URL url = URI.create("http://node.example.com/sync/push").toURL();
        manager.beginReservation(url);
        manager.beginReservation(url);
        manager.endReservation(url);
        assertEquals(1, manager.outstandingReservationsByUri.values().iterator().next().get());
        manager.endReservation(url);
        assertTrue(manager.outstandingReservationsByUri.isEmpty());
    }

    @Test
    void testEndReservation_withNoExistingEntry_doesNotThrow() throws Exception {
        URL url = URI.create("http://node.example.com/sync/push").toURL();
        assertDoesNotThrow(() -> manager.endReservation(url));
        assertTrue(manager.outstandingReservationsByUri.isEmpty());
    }

    @Test
    void testHandleServiceBusy_whenDisabled_doesNotDelegateToClearReservationCookieForUri() throws Exception {
        when(ps.is(ParameterConstants.TRANSPORT_HTTP_SESSION_STICKY_RESET_ENABLED, false)).thenReturn(false);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        manager.handleServiceBusy(conn);
        verify(manager, never()).clearReservationCookieForUri(conn);
    }

    @Test
    void testHandleServiceBusy_whenEnabled_delegatesToClearReservationCookieForUri() {
        when(ps.is(ParameterConstants.TRANSPORT_HTTP_SESSION_STICKY_RESET_ENABLED, false)).thenReturn(true);
        HttpConnection conn = mock(HttpConnection.class);
        doNothing().when(manager).clearReservationCookieForUri(conn);
        manager.handleServiceBusy(conn);
        verify(manager).clearReservationCookieForUri(conn);
    }

    @Test
    void testClearReservationCookieForUri_withOutstandingReservation_leavesCookiesIntact() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        URI targetUri = URI.create("http://node.example.com/");
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("JSESSIONID", "abc"));
        URL url = URI.create("http://node.example.com/sync/push").toURL();
        manager.beginReservation(url);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(url);
        manager.clearReservationCookieForUri(conn);
        assertEquals(1, cookieManager.getCookieStore().get(targetUri).size());
    }

    @Test
    void testClearReservationCookieForUri_withNoCookieManagerInstalled_doesNotThrow() throws Exception {
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        assertDoesNotThrow(() -> manager.clearReservationCookieForUri(conn));
    }

    @Test
    void testClearReservationCookieForUri_withCookieManagerInstalled_clearsAllCookiesForHostOnly() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        URI targetUri = URI.create("http://node.example.com/");
        URI otherUri = URI.create("http://other.example.com/");
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("JSESSIONID", "abc"));
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("WAFCOOKIE", "xyz"));
        cookieManager.getCookieStore().add(otherUri, new HttpCookie("OTHERHOSTCOOKIE", "other"));
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        manager.clearReservationCookieForUri(conn);
        assertTrue(cookieManager.getCookieStore().get(targetUri).isEmpty());
        assertEquals(1, cookieManager.getCookieStore().get(otherUri).size());
    }

    @Test
    void testClearReservationCookieForUri_withSameNamedCookieOnDifferentHost_alsoRemovesIt() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        URI targetUri = URI.create("http://node.example.com/");
        URI otherUri = URI.create("http://other.example.com/");
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("JSESSIONID", "abc"));
        cookieManager.getCookieStore().add(otherUri, new HttpCookie("JSESSIONID", "other"));
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        manager.clearReservationCookieForUri(conn);
        assertTrue(cookieManager.getCookieStore().get(otherUri).isEmpty());
    }

    @Test
    void testGetUri_returnsUrlWithoutLastPathSegment() throws Exception {
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://host/path/action").toURL());
        assertEquals("http://host/path", manager.getUri(conn));
    }

    @Test
    void testGetOutputStreamSize_delegatesToParameterService() {
        when(ps.getInt(ParameterConstants.TRANSPORT_HTTP_PUSH_STREAM_SIZE)).thenReturn(4096);
        assertEquals(4096, manager.getOutputStreamSize());
    }

    @Test
    void testIsOutputStreamEnabled_delegatesToParameterService() {
        when(ps.is(ParameterConstants.TRANSPORT_HTTP_PUSH_STREAM_ENABLED)).thenReturn(true);
        assertTrue(manager.isOutputStreamEnabled());
    }

    @Test
    void testGetHttpTimeOutInMs_delegatesToParameterService() {
        when(ps.getInt(ParameterConstants.TRANSPORT_HTTP_TIMEOUT)).thenReturn(30000);
        assertEquals(30000, manager.getHttpTimeOutInMs());
    }

    @Test
    void testGetHttpConnectTimeOutInMs_delegatesToParameterService() {
        when(ps.getInt(ParameterConstants.TRANSPORT_HTTP_CONNECT_TIMEOUT)).thenReturn(15000);
        assertEquals(15000, manager.getHttpConnectTimeOutInMs());
    }

    @Test
    void testIsUseCompression_returnsTrueWhenParameterEnabledAndNoLocalEngineRegistered() {
        when(remoteNode.getSyncUrl()).thenReturn("http://unregistered.example.com/sync");
        when(ps.is(ParameterConstants.TRANSPORT_HTTP_USE_COMPRESSION_CLIENT)).thenReturn(true);
        assertTrue(manager.isUseCompression(remoteNode));
    }

    @Test
    void testIsUseCompression_returnsFalseWhenParameterDisabled() {
        when(remoteNode.getSyncUrl()).thenReturn("http://unregistered.example.com/sync");
        when(ps.is(ParameterConstants.TRANSPORT_HTTP_USE_COMPRESSION_CLIENT)).thenReturn(false);
        assertFalse(manager.isUseCompression(remoteNode));
    }

    @Test
    void testGetCompressionLevel_delegatesToParameterService() {
        when(ps.getInt(ParameterConstants.TRANSPORT_HTTP_COMPRESSION_LEVEL)).thenReturn(5);
        assertEquals(5, manager.getCompressionLevel());
    }

    @Test
    void testGetCompressionStrategy_delegatesToParameterService() {
        when(ps.getInt(ParameterConstants.TRANSPORT_HTTP_COMPRESSION_STRATEGY)).thenReturn(1);
        assertEquals(1, manager.getCompressionStrategy());
    }

    @Test
    void testWriteMessage_writesDataFollowedByLineSeparator() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manager.writeMessage(out, "hello");
        assertEquals("hello" + System.lineSeparator(), out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testGetFilePullTransport_returnsIncomingTransportForFileSyncPullUrl() throws Exception {
        IIncomingTransport transport = manager.getFilePullTransport(remoteNode, localNode, "token", null, "http://reg.example.com");
        assertTrue(transport instanceof HttpIncomingTransport);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(manager).createGetConnectionFor(urlCaptor.capture(), eq("local-001"), eq("token"));
        assertTrue(urlCaptor.getValue().toString().contains(WebConstants.URL_FILESYNC_PULL));
    }

    @Test
    void testGetPullTransport_returnsIncomingTransportForPullUrl() throws Exception {
        IIncomingTransport transport = manager.getPullTransport(remoteNode, localNode, "token", null, "http://reg.example.com");
        assertTrue(transport instanceof HttpIncomingTransport);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(manager).createGetConnectionFor(urlCaptor.capture(), eq("local-001"), eq("token"));
        assertTrue(urlCaptor.getValue().toString().contains(WebConstants.URL_PULL));
    }

    @Test
    void testGetPingTransport_returnsIncomingTransportForPingUrl() throws Exception {
        IIncomingTransport transport = manager.getPingTransport(remoteNode, localNode, "http://reg.example.com");
        assertTrue(transport instanceof HttpIncomingTransport);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(manager).createGetConnectionFor(urlCaptor.capture());
        assertTrue(urlCaptor.getValue().toString().contains(WebConstants.URL_PING));
    }

    @Test
    void testGetPushTransport_withRequestProperties_returnsOutgoingTransport() throws Exception {
        IOutgoingWithResponseTransport transport = manager.getPushTransport(remoteNode, localNode, "token", new HashMap<>(), "http://reg.example.com");
        assertTrue(transport instanceof HttpOutgoingTransport);
    }

    @Test
    void testGetPushTransport_withoutRequestProperties_returnsOutgoingTransport() throws Exception {
        IOutgoingWithResponseTransport transport = manager.getPushTransport(remoteNode, localNode, "token", "http://reg.example.com");
        assertTrue(transport instanceof HttpOutgoingTransport);
    }

    @Test
    void testGetFilePushTransport_returnsOutgoingTransport() throws Exception {
        IOutgoingWithResponseTransport transport = manager.getFilePushTransport(remoteNode, localNode, "token", "http://reg.example.com");
        assertTrue(transport instanceof HttpOutgoingTransport);
    }

    @Test
    void testGetConfigTransport_buildsConfigUrlWithVersionParams() throws Exception {
        IIncomingTransport transport = manager.getConfigTransport(remoteNode, localNode, "token", "3.18.0", "cfg-version-1", "http://reg.example.com");
        assertTrue(transport instanceof HttpIncomingTransport);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(manager).createGetConnectionFor(urlCaptor.capture(), eq("local-001"), eq("token"));
        String url = urlCaptor.getValue().toString();
        assertTrue(url.contains(WebConstants.URL_CONFIG));
        assertTrue(url.contains("3.18.0"));
        assertTrue(url.contains("cfg-version-1"));
    }

    @Test
    void testGetRegisterTransport_returnsIncomingTransportForRegistrationUrl() throws Exception {
        IIncomingTransport transport = manager.getRegisterTransport(remoteNode, "http://reg.example.com");
        assertTrue(transport instanceof HttpIncomingTransport);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(manager).createGetConnectionFor(urlCaptor.capture());
        assertTrue(urlCaptor.getValue().toString().contains(WebConstants.URL_REGISTRATION));
    }

    @Test
    void testGetRegisterTransport_withRequestProperties_mergesNodePropertiesIntoRequestProperties() throws Exception {
        Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("custom", "value");
        HttpIncomingTransport transport = (HttpIncomingTransport) manager.getRegisterTransport(remoteNode, "http://reg.example.com", requestProperties);
        assertNotNull(transport.getRequestProperties());
        assertFalse(transport.getRequestProperties().isEmpty());
    }

    @Test
    void testGetRegisterPushTransport_returnsOutgoingTransport() throws Exception {
        when(remoteNode.getSyncUrl()).thenReturn("http://reg.example.com");
        IOutgoingWithResponseTransport transport = manager.getRegisterPushTransport(remoteNode, localNode);
        assertTrue(transport instanceof HttpOutgoingTransport);
    }

    @Test
    void testGetBandwidthPullTransport_withNewerRemoteVersion_addsSampleHeaders() throws Exception {
        when(remoteNode.getSymmetricVersion()).thenReturn("3.17.0");
        Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("custom", "value");
        IIncomingTransport transport = manager.getBandwidthPullTransport(remoteNode, localNode, "token", requestProperties, "http://reg.example.com", 500);
        assertTrue(transport instanceof HttpIncomingTransport);
        verify(connMock).addRequestProperty("custom", "value");
        verify(connMock).addRequestProperty(WebConstants.HEADER_DIRECTION, WebConstants.URL_PULL);
        verify(connMock).addRequestProperty(WebConstants.HEADER_SAMPLE_SIZE, "500");
    }

    @Test
    void testGetBandwidthPullTransport_withOlderRemoteVersion_addsSampleParamsToUrl() throws Exception {
        when(remoteNode.getSymmetricVersion()).thenReturn("3.16.0");
        IIncomingTransport transport = manager.getBandwidthPullTransport(remoteNode, localNode, "token", null, "http://reg.example.com", 500);
        assertTrue(transport instanceof HttpIncomingTransport);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(manager).createGetConnectionFor(urlCaptor.capture(), eq("local-001"), eq("token"));
        String url = urlCaptor.getValue().toString();
        assertTrue(url.contains("direction=pull"));
        assertTrue(url.contains("sampleSize=500"));
        verify(connMock, never()).addRequestProperty(eq(WebConstants.HEADER_DIRECTION), anyString());
    }

    @Test
    void testGetBandwidthPushTransport_withNewerRemoteVersion_addsDirectionToRequestProperties() throws Exception {
        when(remoteNode.getSymmetricVersion()).thenReturn("3.17.0");
        Map<String, String> requestProperties = new HashMap<>();
        IOutgoingWithResponseTransport transport = manager.getBandwidthPushTransport(remoteNode, localNode, "token", requestProperties,
                "http://reg.example.com");
        assertTrue(transport instanceof HttpOutgoingTransport);
        assertEquals(WebConstants.URL_PUSH, requestProperties.get(WebConstants.HEADER_DIRECTION));
    }

    @Test
    void testGetBandwidthPushTransport_withOlderRemoteVersion_leavesRequestPropertiesUnchanged() throws Exception {
        when(remoteNode.getSymmetricVersion()).thenReturn("3.16.0");
        Map<String, String> requestProperties = new HashMap<>();
        IOutgoingWithResponseTransport transport = manager.getBandwidthPushTransport(remoteNode, localNode, "token", requestProperties,
                "http://reg.example.com");
        assertTrue(transport instanceof HttpOutgoingTransport);
        assertFalse(requestProperties.containsKey(WebConstants.HEADER_DIRECTION));
    }

    @Test
    void testGetComparePullTransport_addsChannelQueueHeaderAndBuildsUrl() throws Exception {
        Map<String, String> requestParameters = new LinkedHashMap<>();
        requestParameters.put(WebConstants.CHANNEL_QUEUE, "queue1");
        requestParameters.put("since", "100");
        IIncomingTransport transport = manager.getComparePullTransport(remoteNode, localNode, "token", "http://reg.example.com", requestParameters);
        assertTrue(transport instanceof HttpIncomingTransport);
        verify(connMock).addRequestProperty(WebConstants.CHANNEL_QUEUE, "queue1");
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(manager).createGetConnectionFor(urlCaptor.capture(), eq("local-001"), eq("token"));
        String url = urlCaptor.getValue().toString();
        assertTrue(url.contains(WebConstants.URL_COMPARE_PULL));
        assertTrue(url.contains("since=100"));
    }

    @Test
    void testGetComparePushTransport_returnsOutgoingTransport() throws Exception {
        Map<String, String> requestParameters = new LinkedHashMap<>();
        requestParameters.put(WebConstants.CHANNEL_QUEUE, "queue1");
        IOutgoingWithResponseTransport transport = manager.getComparePushTransport(remoteNode, localNode, "token", "http://reg.example.com",
                requestParameters);
        assertTrue(transport instanceof HttpOutgoingTransport);
    }

    @Test
    void testBuildRegistrationUrl_withNullBaseUrl_startsWithRegistrationSegment() {
        String url = HttpTransportManager.buildRegistrationUrl(null, remoteNode);
        assertEquals("/" + WebConstants.URL_REGISTRATION, url);
    }

    @Test
    void testBuildRegistrationUrl_withBaseUrl_prefixesBaseUrl() {
        String url = HttpTransportManager.buildRegistrationUrl("http://reg.example.com", remoteNode);
        assertEquals("http://reg.example.com/" + WebConstants.URL_REGISTRATION, url);
    }

    @Test
    void testCreateGetConnectionFor_withNodeIdAndToken_setsGetMethodAndEncodingHeader() throws Exception {
        HttpURLConnection httpUrlConnectionMock = mock(HttpURLConnection.class);
        URL url = newTestUrl(httpUrlConnectionMock);
        when(ps.getInt(ParameterConstants.TRANSPORT_HTTP_CONNECT_TIMEOUT)).thenReturn(1000);
        when(ps.getInt(ParameterConstants.TRANSPORT_HTTP_TIMEOUT)).thenReturn(2000);
        HttpConnection conn = realCreateGetConnectionFor(url, "node1", "token");
        assertNotNull(conn);
        verify(httpUrlConnectionMock).setRequestProperty("accept-encoding", "gzip");
        verify(httpUrlConnectionMock).setRequestMethod(WebConstants.METHOD_GET);
        verify(httpUrlConnectionMock).setConnectTimeout(1000);
        verify(httpUrlConnectionMock).setReadTimeout(2000);
    }

    @Test
    void testCreateGetConnectionFor_withoutNodeIdAndToken_stillSetsGetMethod() throws Exception {
        HttpURLConnection httpUrlConnectionMock = mock(HttpURLConnection.class);
        URL url = newTestUrl(httpUrlConnectionMock);
        HttpTransportManager plainManager = new HttpTransportManager(engine);
        HttpConnection conn = plainManager.createGetConnectionFor(url);
        assertNotNull(conn);
        verify(httpUrlConnectionMock).setRequestMethod(WebConstants.METHOD_GET);
        verify(httpUrlConnectionMock, never()).setRequestProperty(eq(WebConstants.HEADER_SECURITY_TOKEN), anyString());
    }

    @Test
    void testGetInputStreamFrom_withGzipEncoding_decompresses() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getContentEncoding()).thenReturn("gzip");
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream(compressed.toByteArray()));
        byte[] result = HttpTransportManager.getInputStreamFrom(conn).readAllBytes();
        assertEquals("hello", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void testGetInputStreamFrom_withoutGzipEncoding_returnsRawStream() throws Exception {
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getContentEncoding()).thenReturn(null);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("plain".getBytes(StandardCharsets.UTF_8)));
        byte[] result = HttpTransportManager.getInputStreamFrom(conn).readAllBytes();
        assertEquals("plain", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void testGetReaderFrom_withGzipEncoding_decompresses() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write("hello-reader".getBytes(StandardCharsets.UTF_8));
        }
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getContentEncoding()).thenReturn("gzip");
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream(compressed.toByteArray()));
        assertEquals("hello-reader", HttpTransportManager.getReaderFrom(conn).readLine());
    }

    @Test
    void testGetReaderFrom_withoutGzipEncoding_returnsRawStream() throws Exception {
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getContentEncoding()).thenReturn(null);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("plain-reader".getBytes(StandardCharsets.UTF_8)));
        assertEquals("plain-reader", HttpTransportManager.getReaderFrom(conn).readLine());
    }

    @Test
    void testBuildURL_withCurrentRemoteVersionAndHeaderSecurityToken_omitsTokenFromUrl() throws Exception {
        HttpTransportManager tokenManager = newManager(true, false);
        when(remoteNode.getSymmetricVersion()).thenReturn("3.18.0");
        String url = tokenManager.buildURL(WebConstants.URL_PULL, remoteNode, localNode, "secret", "http://reg.example.com");
        assertFalse(url.contains(WebConstants.SECURITY_TOKEN + "="));
    }

    @Test
    void testBuildURL_withOlderRemoteMinorVersion_forcesTokenInUrl() throws Exception {
        HttpTransportManager tokenManager = newManager(true, false);
        when(remoteNode.getSymmetricVersion()).thenReturn("3.10.0");
        String url = tokenManager.buildURL(WebConstants.URL_PULL, remoteNode, localNode, "secret", "http://reg.example.com");
        assertTrue(url.contains(WebConstants.SECURITY_TOKEN + "="));
    }

    @Test
    void testBuildURL_withHeaderSecurityTokenDisabled_alwaysIncludesTokenInUrl() throws Exception {
        HttpTransportManager plainManager = newManager(false, false);
        when(remoteNode.getSymmetricVersion()).thenReturn("3.18.0");
        String url = plainManager.buildURL(WebConstants.URL_PULL, remoteNode, localNode, "secret", "http://reg.example.com");
        assertTrue(url.contains(WebConstants.SECURITY_TOKEN + "="));
    }

    @Test
    void testAddNodeInfo_withForceParamSecurityTokenTrue_includesTokenRegardlessOfHeaderSetting() {
        HttpTransportManager tokenManager = newManager(true, false);
        String url = tokenManager.addNodeInfo("http://host/action", "n1", "secret", true);
        assertTrue(url.contains(WebConstants.SECURITY_TOKEN + "=secret"));
    }

    @Test
    void testAddNodeInfo_withHeaderSecurityTokenEnabledAndNotForced_omitsToken() {
        HttpTransportManager tokenManager = newManager(true, false);
        String url = tokenManager.addNodeInfo("http://host/action", "n1", "secret", false);
        assertFalse(url.contains(WebConstants.SECURITY_TOKEN + "="));
    }

    @Test
    void testAddNodeInfo_withHeaderSecurityTokenDisabled_includesToken() {
        HttpTransportManager plainManager = newManager(false, false);
        String url = plainManager.addNodeInfo("http://host/action", "n1", "secret", false);
        assertTrue(url.contains(WebConstants.SECURITY_TOKEN + "=secret"));
    }

    @Test
    void testAddNodeId_appendsNodeIdWithConnector() {
        String url = manager.addNodeId("http://host/action", "n1", "?");
        assertEquals("http://host/action?" + WebConstants.NODE_ID + "=n1", url);
    }

    @Test
    void testAdd_urlEncodesValue() {
        String url = manager.add("http://host/action", "key", "a b&c", "&");
        assertTrue(url.startsWith("http://host/action&key="));
        assertFalse(url.contains("a b&c"));
    }

    @Test
    void testGetEngine_returnsConstructorEngine() {
        assertSame(engine, manager.getEngine());
    }

    private HttpTransportManager newManager(boolean useHeaderSecurityToken, boolean useSessionAuth) {
        ISymmetricEngine testEngine = mock(ISymmetricEngine.class);
        IParameterService testPs = mock(IParameterService.class);
        IExtensionService testExtensionService = mock(IExtensionService.class);
        when(testEngine.getParameterService()).thenReturn(testPs);
        when(testEngine.getExtensionService()).thenReturn(testExtensionService);
        when(testPs.is(ParameterConstants.TRANSPORT_HTTP_USE_HEADER_SECURITY_TOKEN)).thenReturn(useHeaderSecurityToken);
        when(testPs.is(ParameterConstants.TRANSPORT_HTTP_USE_SESSION_AUTH)).thenReturn(useSessionAuth);
        return new HttpTransportManager(testEngine);
    }

    private HttpConnection realCreateGetConnectionFor(URL url, String nodeId, String securityToken) throws Exception {
        HttpTransportManager plainManager = new HttpTransportManager(engine);
        return plainManager.createGetConnectionFor(url, nodeId, securityToken);
    }

    private URL newTestUrl(HttpURLConnection connectionToReturn) throws Exception {
        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return connectionToReturn;
            }
        };
        return URL.of(URI.create("http://node.example.com/sync"), handler);
    }
}
