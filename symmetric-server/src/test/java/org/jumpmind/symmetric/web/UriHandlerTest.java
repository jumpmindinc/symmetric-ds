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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UriHandlerTest {
    private IParameterService parameterService;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws IOException {
        parameterService = mock(IParameterService.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void testPingUriHandler_writesPong() throws IOException, ServletException {
        PingUriHandler handler = new PingUriHandler(parameterService, new IInterceptor[0]);
        handler.handle(request, response);
        verify(response).setContentType("text/plain");
        assertEquals("pong", responseBody.toString());
    }

    @Test
    void testPingUriHandler_usesPingUriPattern() {
        assertEquals("/ping/*", new PingUriHandler(parameterService, new IInterceptor[0]).getUriPattern());
    }

    @Test
    void testLivelinessUriHandler_reportsUpWhenNoTrackerIsRegistered() throws IOException, ServletException {
        new LivelinessUriHandler().handle(request, response);
        verify(response).setContentType("application/json");
        assertTrue(responseBody.toString().contains("UP"));
    }

    @Test
    void testLivelinessUriHandler_usesLivelinessUriPattern() {
        assertEquals("/liveliness", new LivelinessUriHandler().getUriPattern());
    }

    @Test
    void testLivelinessUriHandler_hasNoInterceptors() {
        assertTrue(new LivelinessUriHandler().getInterceptors().isEmpty());
    }

    @Test
    void testLivelinessUriHandler_isEnabled() {
        assertTrue(new LivelinessUriHandler().isEnabled());
    }

    @Test
    void testAbstractUriHandler_isEnabledByDefault() {
        assertTrue(newHandler().isEnabled());
    }

    @Test
    void testAbstractUriHandler_setEnabled() {
        AbstractUriHandler handler = newHandler();
        handler.setEnabled(false);
        assertFalse(handler.isEnabled());
    }

    @Test
    void testAbstractUriHandler_getUriPattern() {
        assertEquals("/test/*", newHandler().getUriPattern());
    }

    @Test
    void testAbstractUriHandler_setUriPattern() {
        AbstractUriHandler handler = newHandler();
        handler.setUriPattern("/other/*");
        assertEquals("/other/*", handler.getUriPattern());
    }

    @Test
    void testAbstractUriHandler_retainsConstructorInterceptors() {
        IInterceptor interceptor = mock(IInterceptor.class);
        AbstractUriHandler handler = new StubUriHandler("/test/*", parameterService, interceptor);
        assertEquals(Collections.singletonList(interceptor), handler.getInterceptors());
    }

    @Test
    void testAbstractUriHandler_setInterceptors() {
        IInterceptor interceptor = mock(IInterceptor.class);
        AbstractUriHandler handler = newHandler();
        handler.setInterceptors(Arrays.asList(interceptor));
        assertEquals(Arrays.asList(interceptor), handler.getInterceptors());
    }

    @Test
    void testAbstractUriHandler_withNoInterceptors() {
        assertTrue(newHandler().getInterceptors().isEmpty());
    }

    @Test
    void testAbstractUriHandler_createInputStream_withoutCompression() throws IOException {
        StubUriHandler handler = newHandler();
        when(request.getHeader("Content-Type")).thenReturn("text/plain");
        when(request.getInputStream()).thenReturn(new StubServletInputStream("payload"));
        assertEquals('p', handler.createInputStream(request).read());
    }

    @Test
    void testAbstractUriHandler_createInputStream_withoutContentTypeHeader() throws IOException {
        StubUriHandler handler = newHandler();
        when(request.getHeader("Content-Type")).thenReturn(null);
        when(request.getInputStream()).thenReturn(new StubServletInputStream("payload"));
        assertEquals('p', handler.createInputStream(request).read());
    }

    @Test
    void testAbstractUriHandler_setParameterService() {
        IParameterService other = mock(IParameterService.class);
        StubUriHandler handler = newHandler();
        handler.setParameterService(other);
        assertEquals(other, handler.parameterService);
    }

    private StubUriHandler newHandler() {
        return new StubUriHandler("/test/*", parameterService);
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

    private static class StubUriHandler extends AbstractUriHandler {
        private StubUriHandler(String uriPattern, IParameterService parameterService, IInterceptor... interceptors) {
            super(uriPattern, parameterService, interceptors);
        }

        @Override
        public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
            res.getWriter().write("handled");
        }
    }
}
