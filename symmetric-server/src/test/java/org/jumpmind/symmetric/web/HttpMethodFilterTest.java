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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpMethodFilterTest {
    private HttpMethodFilter filter;
    private FilterConfig filterConfig;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new HttpMethodFilter();
        filterConfig = mock(FilterConfig.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void testDoFilter_allowsMethodWhenNothingIsConfigured() throws IOException, ServletException {
        initFilter(null, null);
        whenMethodIs("GET");
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilter_allowsConfiguredMethod() throws IOException, ServletException {
        initFilter("GET,POST", null);
        whenMethodIs("POST");
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilter_forbidsMethodOutsideAllowList() throws IOException, ServletException {
        initFilter("GET,POST", null);
        whenMethodIs("DELETE");
        filter.doFilter(request, response, filterChain);
        verify(response).sendError(eq(WebConstants.SC_FORBIDDEN), anyString());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testDoFilter_forbidsDisallowedMethod() throws IOException, ServletException {
        initFilter(null, "TRACE");
        whenMethodIs("TRACE");
        filter.doFilter(request, response, filterChain);
        verify(response).sendError(eq(WebConstants.SC_FORBIDDEN), anyString());
    }

    @Test
    void testDoFilter_disallowListWinsOverAllowList() throws IOException, ServletException {
        initFilter("TRACE", "TRACE");
        whenMethodIs("TRACE");
        filter.doFilter(request, response, filterChain);
        verify(response).sendError(eq(WebConstants.SC_FORBIDDEN), anyString());
    }

    @Test
    void testDoFilter_matchesConfiguredMethodCaseInsensitively() throws IOException, ServletException {
        initFilter("get", null);
        whenMethodIs("GET");
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilter_uppercasesIncomingMethodBeforeMatching() throws IOException, ServletException {
        initFilter(null, "TRACE");
        whenMethodIs("trace");
        filter.doFilter(request, response, filterChain);
        verify(response).sendError(eq(WebConstants.SC_FORBIDDEN), anyString());
    }

    @Test
    void testDoFilter_ignoresEmptyEntriesInConfiguration() throws IOException, ServletException {
        initFilter("GET,,POST", null);
        whenMethodIs("POST");
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilter_reportsRejectedMethodInErrorMessage() throws IOException, ServletException {
        initFilter(null, "TRACE");
        whenMethodIs("TRACE");
        filter.doFilter(request, response, filterChain);
        verify(response).sendError(anyInt(), eq("Method TRACE is not allowed."));
    }

    private void initFilter(String allowed, String disallowed) throws ServletException {
        when(filterConfig.getInitParameter("server.allow.http.methods")).thenReturn(allowed);
        when(filterConfig.getInitParameter("server.disallow.http.methods")).thenReturn(disallowed);
        filter.init(filterConfig);
    }

    private void whenMethodIs(String method) {
        when(request.getMethod()).thenReturn(method);
    }
}
