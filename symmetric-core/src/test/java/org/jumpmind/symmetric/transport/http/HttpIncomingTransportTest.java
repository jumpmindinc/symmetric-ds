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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jumpmind.exception.HttpException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.RegistrationRequiredException;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpIncomingTransportTest {
    private HttpTransportManager httpTransportManager;
    private HttpConnection connection;
    private IParameterService parameterService;

    @BeforeEach
    void setUp() throws IOException {
        httpTransportManager = mock(HttpTransportManager.class);
        connection = mock(HttpConnection.class);
        parameterService = mock(IParameterService.class);
        when(parameterService.is(eq(ParameterConstants.TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED), anyBoolean())).thenReturn(false);
        when(parameterService.getInt(ParameterConstants.TRANSPORT_HTTP_TIMEOUT)).thenReturn(30000);
        when(connection.getContentEncoding()).thenReturn(null);
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
    }

    @Test
    void testOpenStream_withOk_returnsInputStream() throws IOException {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_OK);
        HttpIncomingTransport transport = new HttpIncomingTransport(httpTransportManager, connection, parameterService);
        InputStream result = transport.openStream();
        assertNotNull(result);
    }

    @Test
    void testOpenStream_withPartialContent_returnsInputStream() throws IOException {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_PARTIAL_CONTENT);
        HttpIncomingTransport transport = new HttpIncomingTransport(httpTransportManager, connection, parameterService);
        InputStream result = transport.openStream();
        assertNotNull(result);
    }

    @Test
    void testOpenStream_withPartialContent_updatesSessionLikeOk() throws IOException {
        when(connection.getResponseCode()).thenReturn(WebConstants.SC_PARTIAL_CONTENT);
        HttpIncomingTransport transport = new HttpIncomingTransport(httpTransportManager, connection, parameterService);
        transport.openStream();
        verify(httpTransportManager).updateSession(connection);
    }

    @Test
    void testOpenStream_withRegistrationRequired_throwsRegistrationRequiredException() throws IOException {
        when(connection.getResponseCode()).thenReturn(WebConstants.REGISTRATION_REQUIRED);
        HttpIncomingTransport transport = new HttpIncomingTransport(httpTransportManager, connection, parameterService);
        assertThrows(RegistrationRequiredException.class, transport::openStream);
    }

    @Test
    void testOpenStream_withUnrecognizedCode_throwsHttpException() throws IOException {
        when(connection.getResponseCode()).thenReturn(599);
        HttpIncomingTransport transport = new HttpIncomingTransport(httpTransportManager, connection, parameterService);
        HttpException ex = assertThrows(HttpException.class, transport::openStream);
        assertEquals(599, ex.getCode());
    }
}
