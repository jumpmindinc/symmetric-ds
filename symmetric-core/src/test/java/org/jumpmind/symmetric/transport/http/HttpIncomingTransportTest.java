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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.jumpmind.symmetric.transport.NoContentException;
import org.jumpmind.symmetric.transport.ServiceNotReadyException;
import org.jumpmind.symmetric.transport.ServiceUnavailableException;
import org.jumpmind.symmetric.transport.SyncDisabledException;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpIncomingTransportTest {
    private static final String NODE_ID = "node1";
    private static final String SECURITY_TOKEN = "token1";
    private HttpTransportManager httpTransportManager;
    private HttpConnection connection;
    private IParameterService parameterService;

    @BeforeEach
    void setUp() {
        httpTransportManager = mock(HttpTransportManager.class);
        connection = mock(HttpConnection.class);
        parameterService = mock(IParameterService.class);
        when(parameterService.getInt(ParameterConstants.TRANSPORT_HTTP_TIMEOUT)).thenReturn(30000);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED, true)).thenReturn(false);
    }

    @Test
    void testConstructor_readsHttpTimeoutFromParameterService() {
        HttpIncomingTransport transport = newTransport();
        assertNotNull(transport);
        verify(parameterService).getInt(ParameterConstants.TRANSPORT_HTTP_TIMEOUT);
    }

    @Test
    void testConstructor_withRequestPropertiesSetsThem() {
        Map<String, String> props = new HashMap<>();
        props.put("key1", "value1");
        HttpIncomingTransport transport = newTransportWithRequestProperties(props);
        assertEquals(props, transport.getRequestProperties());
    }

    @Test
    void testConstructor_withNodeIdAndSecurityToken() {
        HttpIncomingTransport transport = newTransportWithNodeIdAndSecurityToken();
        assertNotNull(transport);
        assertEquals(connection, transport.getConnection());
    }

    @Test
    void testGetUrl_returnsExternalFormOfConnectionUrl() throws Exception {
        URL url = URI.create("http://node.example.com/sync").toURL();
        when(connection.getURL()).thenReturn(url);
        HttpIncomingTransport transport = newTransport();
        assertEquals(url.toExternalForm(), transport.getUrl());
    }

    @Test
    void testClose_closesConnectionAndReaderAndStream() throws Exception {
        stubSuccessfulResponse();
        HttpIncomingTransport transport = newTransport();
        transport.openStream();
        transport.close();
        verify(connection).close();
        assertDoesNotThrow(transport::close);
    }

    @Test
    void testClose_swallowsExceptionsFromConnectionClose() {
        doThrow(new RuntimeException("boom")).when(connection).close();
        HttpIncomingTransport transport = newTransport();
        assertDoesNotThrow(transport::close);
    }

    @Test
    void testIsOpen_returnsFalseInitially() {
        HttpIncomingTransport transport = newTransport();
        assertFalse(transport.isOpen());
    }

    @Test
    void testIsOpen_returnsTrueAfterOpenReader() throws Exception {
        stubSuccessfulResponse();
        HttpIncomingTransport transport = newTransport();
        transport.openReader();
        assertTrue(transport.isOpen());
    }

    @Test
    void testGetRedirectionUrl_returnsNullWhenNoRedirectOccurred() {
        HttpIncomingTransport transport = newTransport();
        assertNull(transport.getRedirectionUrl());
    }

    @Test
    void testOpenStream_withManualRedirectsDisabledSkipsRedirectCheck() throws Exception {
        stubSuccessfulResponse();
        HttpIncomingTransport transport = newTransport();
        InputStream result = transport.openStream();
        assertNotNull(result);
        verify(httpTransportManager).updateSession(connection);
    }

    @Test
    void testOpenStream_withRegistrationNotOpenThrowsException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.REGISTRATION_NOT_OPEN);
        HttpIncomingTransport transport = newTransport();
        assertThrows(RegistrationNotOpenException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withRegistrationRequiredThrowsException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.REGISTRATION_REQUIRED);
        HttpIncomingTransport transport = newTransport();
        assertThrows(RegistrationRequiredException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withRegistrationPendingThrowsException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.REGISTRATION_PENDING);
        HttpIncomingTransport transport = newTransport();
        assertThrows(RegistrationPendingException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withSyncDisabledThrowsException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SYNC_DISABLED);
        HttpIncomingTransport transport = newTransport();
        assertThrows(SyncDisabledException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withServiceBusyThrowsConnectionRejectedException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_SERVICE_BUSY);
        HttpIncomingTransport transport = newTransport();
        assertThrows(ConnectionRejectedException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withAlreadyConnectedThrowsConnectionDuplicateException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_ALREADY_CONNECTED);
        HttpIncomingTransport transport = newTransport();
        assertThrows(ConnectionDuplicateException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withServiceUnavailableThrowsException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_SERVICE_UNAVAILABLE);
        HttpIncomingTransport transport = newTransport();
        assertThrows(ServiceUnavailableException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withServiceNotReadyThrowsException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_SERVICE_NOT_READY);
        HttpIncomingTransport transport = newTransport();
        assertThrows(ServiceNotReadyException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withForbiddenClearsSessionAndThrowsAuthenticationException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_FORBIDDEN);
        HttpIncomingTransport transport = newTransport();
        assertThrows(AuthenticationException.class, transport::openStream);
        verify(httpTransportManager).clearSession(connection);
    }

    @Test
    void testOpenStream_withAuthExpiredClearsSessionAndThrowsAuthenticationExpiredException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_AUTH_EXPIRED);
        HttpIncomingTransport transport = newTransport();
        assertThrows(AuthenticationExpiredException.class, transport::openStream);
        verify(httpTransportManager).clearSession(connection);
    }

    @Test
    void testOpenStream_withNoContentThrowsException() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_NO_CONTENT);
        HttpIncomingTransport transport = newTransport();
        assertThrows(NoContentException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withUnknownResponseCodeThrowsHttpException() throws Exception {
        when(connection.getResponseCode()).thenReturn(999);
        HttpIncomingTransport transport = newTransport();
        assertThrows(HttpException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withManualRedirectsEnabledFollowsRedirectAndReturnsStream() throws Exception {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED, true)).thenReturn(true);
        HttpConnection redirectedConnection = mock(HttpConnection.class);
        when(connection.getResponseCode()).thenReturn(302);
        when(connection.getURL()).thenReturn(URI.create("http://node.example.com/sync").toURL());
        when(connection.getHeaderField("Location")).thenReturn("http://redirect.example.com/sync");
        when(httpTransportManager.openConnection(any(URL.class), any(), any())).thenReturn(redirectedConnection);
        when(redirectedConnection.getResponseCode()).thenReturn(WebConstants.SC_OK);
        when(redirectedConnection.getContentEncoding()).thenReturn("");
        when(redirectedConnection.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        HttpIncomingTransport transport = newTransportWithNodeIdAndSecurityToken();
        InputStream result = transport.openStream();
        assertNotNull(result);
        verify(httpTransportManager).updateSession(redirectedConnection);
        assertEquals(redirectedConnection, transport.getConnection());
    }

    @Test
    void testOpenStream_withRedirectExceedingProtocolThrowsSecurityException() throws Exception {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED, true)).thenReturn(true);
        when(connection.getResponseCode()).thenReturn(302);
        when(connection.getURL()).thenReturn(URI.create("http://node.example.com/sync").toURL());
        when(connection.getHeaderField("Location")).thenReturn("ftp://bad.example.com/file");
        HttpIncomingTransport transport = newTransport();
        assertThrows(SecurityException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withTooManyRedirectsThrowsSecurityException() throws Exception {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED, true)).thenReturn(true);
        HttpConnection redirectedConnection = mock(HttpConnection.class);
        when(connection.getResponseCode()).thenReturn(302);
        when(connection.getURL()).thenReturn(URI.create("http://node.example.com/sync").toURL());
        when(connection.getHeaderField("Location")).thenReturn("http://redirect.example.com/sync");
        when(redirectedConnection.getResponseCode()).thenReturn(302);
        when(redirectedConnection.getURL()).thenReturn(URI.create("http://redirect.example.com/sync").toURL());
        when(redirectedConnection.getHeaderField("Location")).thenReturn("http://redirect.example.com/sync");
        when(httpTransportManager.openConnection(any(URL.class), any(), any())).thenReturn(redirectedConnection);
        HttpIncomingTransport transport = newTransportWithNodeIdAndSecurityToken();
        assertThrows(SecurityException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withNotModifiedStatusIsNotTreatedAsRedirect() throws Exception {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED, true)).thenReturn(true);
        when(connection.getResponseCode()).thenReturn(HttpConnection.HTTP_NOT_MODIFIED);
        when(connection.getURL()).thenReturn(URI.create("http://node.example.com/sync").toURL());
        HttpIncomingTransport transport = newTransportWithNodeIdAndSecurityToken();
        assertThrows(HttpException.class, transport::openStream);
        assertEquals(connection, transport.getConnection());
    }

    @Test
    void testOpenReader_returnsReaderWrappingOpenStreamResult() throws Exception {
        stubSuccessfulResponse();
        HttpIncomingTransport transport = newTransport();
        BufferedReader reader = transport.openReader();
        assertNotNull(reader);
        assertTrue(transport.isOpen());
    }

    @Test
    void testGetHeaders_returnsCaseInsensitiveMapSkippingNullKey() {
        Map<String, List<String>> rawHeaders = new LinkedHashMap<>();
        rawHeaders.put(null, List.of("HTTP/1.1 200 OK"));
        rawHeaders.put("Content-Type", List.of("text/xml"));
        rawHeaders.put("X-Custom-Header", List.of("value1"));
        when(connection.getHeaderFields()).thenReturn(rawHeaders);
        when(connection.getHeaderField("Content-Type")).thenReturn("text/xml");
        when(connection.getHeaderField("X-Custom-Header")).thenReturn("value1");
        HttpIncomingTransport transport = newTransport();
        Map<String, String> headers = transport.getHeaders();
        assertEquals(2, headers.size());
        assertEquals("text/xml", headers.get("content-type"));
        assertEquals("value1", headers.get("X-CUSTOM-HEADER"));
    }

    @Test
    void testApplyRequestProperties_withNullRequestPropertiesDoesNothing() throws Exception {
        HttpIncomingTransport transport = newTransport();
        transport.applyRequestProperties();
        verify(connection, never()).setRequestMethod(anyString());
        verify(connection, never()).setDoOutput(anyBoolean());
    }

    @Test
    void testApplyRequestProperties_withRequestPropertiesSetsPostAndWritesThem() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put("key1", "value1");
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        HttpIncomingTransport transport = newTransportWithRequestProperties(props);
        transport.applyRequestProperties();
        verify(connection).setRequestMethod("POST");
        verify(connection).setDoOutput(true);
        verify(httpTransportManager).writeRequestProperties(eq(props), any(OutputStream.class));
    }

    @Test
    void testGetConnection_returnsConnection() {
        HttpIncomingTransport transport = newTransport();
        assertEquals(connection, transport.getConnection());
    }

    @Test
    void testSetRequestProperties_roundTripsThroughGetRequestProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("a", "b");
        HttpIncomingTransport transport = newTransport();
        transport.setRequestProperties(props);
        assertEquals(props, transport.getRequestProperties());
    }

    private void stubSuccessfulResponse() throws Exception {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_OK);
        when(connection.getContentEncoding()).thenReturn("");
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    }

    private HttpIncomingTransport newTransport() {
        return new HttpIncomingTransport(httpTransportManager, connection, parameterService);
    }

    private HttpIncomingTransport newTransportWithRequestProperties(Map<String, String> requestProperties) {
        return new HttpIncomingTransport(httpTransportManager, connection, parameterService, requestProperties);
    }

    private HttpIncomingTransport newTransportWithNodeIdAndSecurityToken() {
        return new HttpIncomingTransport(httpTransportManager, connection, parameterService, NODE_ID, SECURITY_TOKEN);
    }
}
