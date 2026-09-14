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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

class ServletUtilsTest {
    @Test
    void testNormalizeRequestUri_normalizeStripsContextAndServletPath() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/sync/engine/ack");
        when(req.getContextPath()).thenReturn("/sync");
        when(req.getServletPath()).thenReturn("");
        assertEquals("/engine/ack", ServletUtils.normalizeRequestUri(req));
    }

    @Test
    void testNormalizeRequestUri_normalizeLeavesUriWhenNoContextPath() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/sync/engine/ack");
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn("");
        assertEquals("/sync/engine/ack", ServletUtils.normalizeRequestUri(req));
    }

    @Test
    void testFindEngine_returnsNullWhenNoHolder() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/engine/ack");
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn("");
        ServletContext ctxt = mock(ServletContext.class);
        when(ctxt.getAttribute(WebConstants.ATTR_ENGINE_HOLDER)).thenReturn(null);
        assertNull(ServletUtils.findEngine(req, ctxt));
    }

    @Test
    void testFindEngine_fallsBackToSoleEngine() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/");
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn("");
        ServerSymmetricEngine engine = mock(ServerSymmetricEngine.class);
        Map<String, ServerSymmetricEngine> engines = new HashMap<>();
        engines.put("only", engine);
        SymmetricEngineHolder holder = mock(SymmetricEngineHolder.class);
        when(holder.getEngines()).thenReturn(engines);
        when(holder.getEngineCount()).thenReturn(1);
        when(holder.getNumerOfEnginesStarting()).thenReturn(0);
        ServletContext ctx = mock(ServletContext.class);
        when(ctx.getAttribute(WebConstants.ATTR_ENGINE_HOLDER)).thenReturn(holder);
        assertSame(engine, ServletUtils.findEngine(req, ctx));
    }

    @Test
    void testGetEngineNameFromUrl_returnsFirstSegment() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/engine/ack");
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn("");
        assertEquals("engine", ServletUtils.getEngineNameFromUrl(req));
    }

    @Test
    void testGetEngineNameFromUrl_returnsNullWhenNoTrailingSlash() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/engine");
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn("");
        assertEquals(null, ServletUtils.getEngineNameFromUrl(req));
    }

    @Test
    void testWhereAreYou_prefersForwardedHeader() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7");
        assertEquals("203.0.113.7", ServletUtils.whereAreYou(req));
    }

    @Test
    void testWhereAreYou_fallsBackToRemoteAddr() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("10.0.0.1");
        assertEquals("10.0.0.1", ServletUtils.whereAreYou(req));
    }

    @Test
    void testWhereAreYou_skipsUnknownHeader() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(req.getRemoteAddr()).thenReturn("10.0.0.1");
        assertEquals("10.0.0.1", ServletUtils.whereAreYou(req));
    }

    @Test
    void testGetParameter_trimsToNull() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("p")).thenReturn("    abc    ");
        assertEquals("abc", ServletUtils.getParameter(req, "p"));
    }

    @Test
    void testGetParameter_blankBecomesNull() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("p")).thenReturn("   ");
        assertNull(ServletUtils.getParameter(req, "p"));
    }

    @Test
    void testGetParameter_useDefaultWhenBlank() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("p")).thenReturn(null);
        assertEquals("default", ServletUtils.getParameter(req, "p", "default"));
    }

    @Test
    void testGetParameterAsNumber_parsesLong() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("n")).thenReturn("42");
        assertEquals(42L, ServletUtils.getParameterAsNumber(req, "n"));
    }

    @Test
    void testGetParameterAsNumber_zeroForNonNumeric() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("n")).thenReturn("abc");
        assertEquals(0L, ServletUtils.getParameterAsNumber(req, "n"));
    }

    @Test
    void testSendError_sendsWhenNotCommitted() throws Exception {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.isCommitted()).thenReturn(false);
        boolean result = ServletUtils.sendError(resp, 404, "boom");
        assertTrue(result);
        verify(resp).sendError(404, "boom");
    }

    @Test
    void testSendError_skipsWhenCommitted() throws Exception {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.isCommitted()).thenReturn(true);
        boolean result = ServletUtils.sendError(resp, 404, "boom");
        assertFalse(result);
        verify(resp, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testGetEndpointName_extractsEndpoint() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/myengine/ack/extra");
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn("");
        Map<String, ServerSymmetricEngine> engines = new HashMap<>();
        engines.put("myengine", mock(ServerSymmetricEngine.class));
        SymmetricEngineHolder holder = mock(SymmetricEngineHolder.class);
        when(holder.getEngines()).thenReturn(engines);
        ServletContext ctx = mock(ServletContext.class);
        when(ctx.getAttribute(WebConstants.ATTR_ENGINE_HOLDER)).thenReturn(holder);
        assertEquals("ack", ServletUtils.getEndpointNameFromUrl(req, ctx));
    }

    @Test
    void testGetEndpointName_throwsForUnknownEngine() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/ghost/ack");
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn("");
        SymmetricEngineHolder holder = mock(SymmetricEngineHolder.class);
        when(holder.getEngines()).thenReturn(new HashMap<>());
        ServletContext ctx = mock(ServletContext.class);
        when(ctx.getAttribute(WebConstants.ATTR_ENGINE_HOLDER)).thenReturn(holder);
        assertThrows(IllegalArgumentException.class, () -> ServletUtils.getEndpointNameFromUrl(req, ctx));
    }
}
