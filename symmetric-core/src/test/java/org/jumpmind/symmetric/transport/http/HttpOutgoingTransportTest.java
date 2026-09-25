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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.Deflater;

import org.jumpmind.exception.HttpException;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.model.ChannelNodesMap;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.InitialLoadPendingException;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.service.RegistrationPendingException;
import org.jumpmind.symmetric.service.RegistrationRequiredException;
import org.jumpmind.symmetric.transport.AuthenticationException;
import org.jumpmind.symmetric.transport.AuthenticationExpiredException;
import org.jumpmind.symmetric.transport.ConnectionDuplicateException;
import org.jumpmind.symmetric.transport.ConnectionRejectedException;
import org.jumpmind.symmetric.transport.NoReservationException;
import org.jumpmind.symmetric.transport.ServiceNotReadyException;
import org.jumpmind.symmetric.transport.ServiceUnavailableException;
import org.jumpmind.symmetric.transport.SyncDisabledException;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class HttpOutgoingTransportTest {
    private static final String NODE_ID = "node1";
    private static final String SECURITY_TOKEN = "secToken";
    private static final int HTTP_TIMEOUT = 5000;
    private static final int HTTP_CONNECT_TIMEOUT = 3000;
    private HttpTransportManager manager;
    private HttpConnection connection;
    private URL url;

    @BeforeEach
    void setUp() throws Exception {
        manager = mock(HttpTransportManager.class);
        connection = mock(HttpConnection.class);
        url = URI.create("http://node.example.com/push").toURL();
        when(manager.openConnection(any(URL.class), any(), any())).thenReturn(connection);
    }

    @Test
    void testConstructor_withoutRequestPropertiesLeavesThemNull() throws Exception {
        HttpOutgoingTransport transport = new HttpOutgoingTransport(manager, url, HTTP_TIMEOUT, HTTP_CONNECT_TIMEOUT, false,
                Deflater.DEFAULT_STRATEGY, Deflater.DEFAULT_COMPRESSION, NODE_ID, SECURITY_TOKEN, false, 30720, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        assertDoesNotThrow(transport::openStream);
    }

    @Test
    void testConstructor_withRequestPropertiesAppliesThemDuringOpenStream() throws Exception {
        Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("X-Custom", "value1");
        requestProperties.put("X-Other", "value2");
        HttpOutgoingTransport transport = new HttpOutgoingTransport(manager, url, HTTP_TIMEOUT, HTTP_CONNECT_TIMEOUT, false,
                Deflater.DEFAULT_STRATEGY, Deflater.DEFAULT_COMPRESSION, NODE_ID, SECURITY_TOKEN, false, 30720, false, requestProperties);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        verify(connection).setRequestProperty("X-Custom", "value1");
        verify(connection).setRequestProperty("X-Other", "value2");
    }

    @Test
    void testClose_closesWriterOutputStreamReaderAndDisconnectsConnection() throws Exception {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        transport.close();
        verify(connection).disconnect();
        assertNull(transport.getConnection());
    }

    @Test
    void testClose_withFileUploadWritesClosingBoundary() throws Exception {
        HttpOutgoingTransport transport = newTransport(false, true);
        ByteArrayOutputStream underlying = new ByteArrayOutputStream();
        when(connection.getOutputStream()).thenReturn(underlying);
        transport.openStream();
        String boundary = captureMultipartBoundary();
        transport.close();
        String content = new String(underlying.toByteArray(), Charset.defaultCharset());
        assertTrue(content.endsWith("--" + boundary + "--" + HttpOutgoingTransport.CRLF));
    }

    @Test
    void testOpenStream_setsPutMethodAndHeadersWhenNotFileUpload() throws Exception {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        OutputStream result = transport.openStream();
        verify(connection).setRequestMethod("PUT");
        verify(connection).setRequestProperty("Accept-Encoding", "gzip");
        assertNotNull(result);
    }

    @Test
    void testOpenStream_addsGzipContentTypeWhenCompressionEnabled() throws IOException {
        HttpOutgoingTransport transport = newTransport(true, false);
        ByteArrayOutputStream underlying = new ByteArrayOutputStream();
        when(connection.getOutputStream()).thenReturn(underlying);
        OutputStream result = transport.openStream();
        verify(connection).addRequestProperty("Content-Type", "gzip");
        result.write("hello".getBytes(StandardCharsets.UTF_8));
        result.flush();
        byte[] bytes = underlying.toByteArray();
        assertEquals((byte) 0x1f, bytes[0]);
        assertEquals((byte) 0x8b, bytes[1]);
    }

    @Test
    void testOpenStream_setsChunkedStreamingModeWhenStreamOutputEnabled() throws Exception {
        int chunkSize = 8192;
        HttpOutgoingTransport transport = newTransport(false, false, true, chunkSize, null);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        verify(connection).setChunkedStreamingMode(chunkSize);
    }

    @Test
    void testOpenStream_setsPostMethodAndMultipartHeadersWhenFileUpload() throws Exception {
        HttpOutgoingTransport transport = newTransport(false, true);
        ByteArrayOutputStream underlying = new ByteArrayOutputStream();
        when(connection.getOutputStream()).thenReturn(underlying);
        transport.openStream();
        verify(connection).setRequestMethod("POST");
        String boundary = captureMultipartBoundary();
        assertNotNull(boundary);
        String content = new String(underlying.toByteArray(), Charset.defaultCharset());
        assertTrue(content.contains("Content-Disposition: form-data; name=\"binaryFile\"; filename=\"file.zip\""));
    }

    @Test
    void testOpenStream_wrapsIOExceptionAsIoException() throws IOException {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenThrow(new IOException("boom"));
        assertThrows(IoException.class, transport::openStream);
    }

    @Test
    void testOpenWriter_returnsBufferedWriterWrappingOpenStreamOutput() throws IOException {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        BufferedWriter writer = transport.openWriter();
        assertNotNull(writer);
        assertSame(writer, transport.getWriter());
    }

    @Test
    void testGetWriter_returnsNullBeforeOpenWriterCalled() {
        HttpOutgoingTransport transport = newTransport(false, false);
        assertNull(transport.getWriter());
    }

    @Test
    void testReadResponse_onSuccessUpdatesSessionAndReturnsReader() throws IOException {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_OK);
        when(connection.getContentEncoding()).thenReturn("");
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        BufferedReader reader = transport.readResponse();
        assertNotNull(reader);
        verify(manager).updateSession(connection);
    }

    @ParameterizedTest
    @MethodSource("responseCodeExceptions")
    void testReadResponse_throwsExceptionForKnownErrorCode(int code, Class<? extends RuntimeException> expectedType) throws Exception {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        when(connection.getResponseCode()).thenReturn(code);
        assertThrows(expectedType, transport::readResponse);
    }

    @Test
    void testReadResponse_withForbiddenOrAuthExpiredClearsSession() throws Exception {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_FORBIDDEN);
        assertThrows(AuthenticationException.class, transport::readResponse);
        verify(manager, times(1)).clearSession(connection);
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_AUTH_EXPIRED);
        assertThrows(AuthenticationExpiredException.class, transport::readResponse);
        verify(manager, times(2)).clearSession(connection);
    }

    @Test
    void testReadResponse_withUnknownCodeThrowsHttpException() throws Exception {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        when(connection.getResponseCode()).thenReturn(500);
        assertThrows(HttpException.class, transport::readResponse);
    }

    @Test
    void testIsOpen_returnsFalseInitially() {
        HttpOutgoingTransport transport = newTransport(false, false);
        assertFalse(transport.isOpen());
    }

    @Test
    void testIsOpen_returnsTrueAfterOpenStream() throws Exception {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        assertTrue(transport.isOpen());
    }

    @Test
    void testGetSuspendIgnoreChannelLists_combinesHeaderAndLocalChannels() throws IOException {
        HttpOutgoingTransport transport = newTransport(false, false);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        Node targetNode = mock(Node.class);
        when(targetNode.getNodeId()).thenReturn("target1");
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_OK);
        when(connection.getHeaderField(WebConstants.SUSPENDED_CHANNELS)).thenReturn("chanA,chanB");
        when(connection.getHeaderField(WebConstants.IGNORED_CHANNELS)).thenReturn("chanC");
        NodeChannels localChannels = new NodeChannels();
        localChannels.addSuspendChannels("otherNode", "chanD");
        localChannels.addIgnoreChannels("otherNode", "chanE");
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "queue1", targetNode);
        ChannelNodesMap suspendChannels = result.getSuspendChannels();
        ChannelNodesMap ignoreChannels = result.getIgnoreChannels();
        assertEquals(Collections.singleton("target1"), suspendChannels.getByChannelId("chanA"));
        assertEquals(Collections.singleton("target1"), suspendChannels.getByChannelId("chanB"));
        assertEquals(Collections.singleton("target1"), ignoreChannels.getByChannelId("chanC"));
        assertEquals(Collections.singleton("otherNode"), suspendChannels.getByChannelId("chanD"));
        assertEquals(Collections.singleton("otherNode"), ignoreChannels.getByChannelId("chanE"));
        verify(connection).setRequestMethod("HEAD");
        verify(connection).close();
    }

    @Test
    void testGetConnection_returnsCurrentConnection() throws Exception {
        HttpOutgoingTransport transport = newTransport(false, false);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        transport.openStream();
        assertSame(connection, transport.getConnection());
    }

    private static Stream<Arguments> responseCodeExceptions() {
        return Stream.of(
                Arguments.of(WebConstants.SC_SERVICE_BUSY, ConnectionRejectedException.class),
                Arguments.of(WebConstants.SC_SERVICE_UNAVAILABLE, ServiceUnavailableException.class),
                Arguments.of(WebConstants.SC_SERVICE_NOT_READY, ServiceNotReadyException.class),
                Arguments.of(WebConstants.SC_NO_RESERVATION, NoReservationException.class),
                Arguments.of(WebConstants.SC_ALREADY_CONNECTED, ConnectionDuplicateException.class),
                Arguments.of(WebConstants.SYNC_DISABLED, SyncDisabledException.class),
                Arguments.of(WebConstants.REGISTRATION_REQUIRED, RegistrationRequiredException.class),
                Arguments.of(WebConstants.REGISTRATION_PENDING, RegistrationPendingException.class),
                Arguments.of(WebConstants.INITIAL_LOAD_PENDING, InitialLoadPendingException.class),
                Arguments.of(WebConstants.REGISTRATION_NOT_OPEN, RegistrationNotOpenException.class));
    }

    private String captureMultipartBoundary() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection).setRequestProperty(eq("Content-Type"), captor.capture());
        assertTrue(captor.getValue().startsWith("multipart/form-data; boundary="));
        return captor.getValue().substring("multipart/form-data; boundary=".length());
    }

    private HttpOutgoingTransport newTransport(boolean useCompression, boolean fileUpload) {
        return newTransport(useCompression, fileUpload, false, 30720, null);
    }

    private HttpOutgoingTransport newTransport(boolean useCompression, boolean fileUpload, boolean streamOutputEnabled, int streamOutputChunkSize,
            Map<String, String> requestProperties) {
        if (requestProperties != null) {
            return new HttpOutgoingTransport(manager, url, HTTP_TIMEOUT, HTTP_CONNECT_TIMEOUT, useCompression, Deflater.DEFAULT_STRATEGY,
                    Deflater.DEFAULT_COMPRESSION, NODE_ID, SECURITY_TOKEN, streamOutputEnabled, streamOutputChunkSize, fileUpload, requestProperties);
        }
        return new HttpOutgoingTransport(manager, url, HTTP_TIMEOUT, HTTP_CONNECT_TIMEOUT, useCompression, Deflater.DEFAULT_STRATEGY,
                Deflater.DEFAULT_COMPRESSION, NODE_ID, SECURITY_TOKEN, streamOutputEnabled, streamOutputChunkSize, fileUpload);
    }
}
