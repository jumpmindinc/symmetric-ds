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

import java.io.IOException;
import java.util.Collections;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogRequestResponseFilter implements Filter {
    private boolean enabled = false;
    private static Logger LOG = LoggerFactory.getLogger(LogRequestResponseFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (Boolean.valueOf(filterConfig.getInitParameter("enabled"))) {
            enabled = true;
        }
    }

    @Override
    public void doFilter(ServletRequest argRequest, ServletResponse argResponse, FilterChain chain) throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(argRequest, argResponse);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) argRequest;
        HttpServletResponse response = (HttpServletResponse) argResponse;
        StringBuilder buff = new StringBuilder(256);
        buildRequest(request, buff);
        long start = System.currentTimeMillis();
        chain.doFilter(request, response);
        long elapsed = System.currentTimeMillis() - start;
        buildResponse(response, elapsed, buff);
        buff.setLength(buff.length() - 2); // remove last CR/LF
        LOG.info(buff.toString());
    }

    private void buildRequest(HttpServletRequest request, StringBuilder buff) {
        buff.append(request.getMethod()).append(" REQUEST: ").append(getUrl(request)).append(" from ").append(request.getRemoteHost()).append("\r\n");
        for (String headerName : Collections.list(request.getHeaderNames())) {
            buff.append("\t").append(headerName).append("=").append(request.getHeader(headerName)).append("\r\n");
        }
    }

    private void buildResponse(HttpServletResponse response, long elapsed, StringBuilder buff) {
        buff.append("  RESPONSE took ").append(elapsed).append("ms.");
        if (response.getContentType() != null) {
            buff.append(" Content Type: ").append(response.getContentType());
        }
        buff.append(" HTTP status ").append(response.getStatus()).append("\r\n");
        for (String headerName : response.getHeaderNames()) {
            buff.append("\t").append(headerName).append("=").append(response.getHeader(headerName)).append("\r\n");
        }
    }

    private String getUrl(HttpServletRequest request) {
        String uri = request.getScheme() + "://" +
                request.getServerName() +
                ("http".equals(request.getScheme()) && request.getServerPort() == 80 || "https".equals(request.getScheme()) && request.getServerPort() == 443
                        ? ""
                        : ":" + request.getServerPort()) +
                request.getRequestURI() +
                (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        return uri;
    }

    @Override
    public void destroy() {
        // no-op.
    }
}
