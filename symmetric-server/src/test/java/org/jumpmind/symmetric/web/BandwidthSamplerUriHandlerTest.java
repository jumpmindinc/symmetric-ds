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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BandwidthSamplerUriHandlerTest {
    private IParameterService parameterService;
    private BandwidthSamplerUriHandler handler;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ByteArrayOutputStream responseBody;

    @BeforeEach
    void setUp() throws IOException {
        parameterService = mock(IParameterService.class);
        when(parameterService.getLong("test.slow.bandwidth.delay")).thenReturn(0L);
        handler = new BandwidthSamplerUriHandler(parameterService, new IInterceptor[0]);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new StubServletOutputStream(responseBody));
    }

    @Test
    void testGetUriPattern() {
        assertEquals("/bandwidth/*", handler.getUriPattern());
    }

    @Test
    void testHandle_pullWritesRequestedSampleSize() throws IOException, ServletException {
        whenDirectionIs(WebConstants.URL_PULL);
        when(request.getHeader(WebConstants.HEADER_SAMPLE_SIZE)).thenReturn("16");
        handler.handle(request, response);
        assertEquals(16, responseBody.size());
    }

    @Test
    void testHandle_pullReadsSampleSizeFromParameterWhenHeaderIsAbsent() throws IOException, ServletException {
        whenDirectionIs(WebConstants.URL_PULL);
        when(request.getParameter(WebConstants.SAMPLE_SIZE)).thenReturn("8");
        handler.handle(request, response);
        assertEquals(8, responseBody.size());
    }

    @Test
    void testHandle_pullFallsBackToDefaultSampleSizeWhenUnparseable() throws IOException, ServletException {
        whenDirectionIs(WebConstants.URL_PULL);
        when(request.getHeader(WebConstants.HEADER_SAMPLE_SIZE)).thenReturn("not-a-number");
        handler.handle(request, response);
        assertEquals(1000, responseBody.size());
    }

    @Test
    void testHandle_pushReportsTransmittedBytesAsJson() throws IOException, ServletException {
        whenDirectionIs(WebConstants.URL_PUSH);
        when(request.getInputStream()).thenReturn(new StubServletInputStream("payload"));
        handler.handle(request, response);
        assertTrue(responseBody.toString(StandardCharsets.UTF_8).contains("\"total\":7"));
    }

    @Test
    void testHandle_withUnknownDirectionThrows() {
        whenDirectionIs("sideways");
        when(request.getMethod()).thenReturn("GET");
        assertThrows(IOException.class, () -> handler.handle(request, response));
    }

    @Test
    void testHandle_withMissingDirectionThrows() {
        when(request.getMethod()).thenReturn("GET");
        assertThrows(IOException.class, () -> handler.handle(request, response));
    }

    @Test
    void testHandle_ignoresHeadRequestWithoutDirection() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("HEAD");
        handler.handle(request, response);
        assertEquals(0, responseBody.size());
    }

    @Test
    void testSetDefaultTestSlowBandwidthDelay_isUsedWhenNoParameterService() throws IOException, ServletException {
        BandwidthSamplerUriHandler bareHandler = new BandwidthSamplerUriHandler(null, new IInterceptor[0]);
        bareHandler.setDefaultTestSlowBandwidthDelay(0);
        whenDirectionIs(WebConstants.URL_PULL);
        when(request.getHeader(WebConstants.HEADER_SAMPLE_SIZE)).thenReturn("4");
        bareHandler.handle(request, response);
        assertEquals(4, responseBody.size());
    }

    private void whenDirectionIs(String direction) {
        when(request.getHeader(WebConstants.HEADER_DIRECTION)).thenReturn(direction);
    }

    private static class StubServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream delegate;

        private StubServletOutputStream(ByteArrayOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) {
            delegate.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException("This stub only supports blocking writes");
        }
    }

    private static class StubServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        private StubServletInputStream(String content) {
            delegate = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("This stub only supports blocking reads");
        }
    }
}
