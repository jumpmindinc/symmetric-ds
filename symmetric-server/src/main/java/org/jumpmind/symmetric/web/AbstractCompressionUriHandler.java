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
import java.util.Enumeration;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.web.compression.CompressionServletResponseWrapper;

abstract public class AbstractCompressionUriHandler extends AbstractUriHandler {
    public AbstractCompressionUriHandler(String uriPattern,
            IParameterService parameterService, IInterceptor... interceptors) {
        super(uriPattern, parameterService, interceptors);
    }

    final public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        boolean compressionEnabled = !parameterService
                .is(ParameterConstants.TRANSPORT_HTTP_COMPRESSION_DISABLED_SERVLET);
        if (compressionEnabled) {
            int compressionLevel = parameterService
                    .getInt(ParameterConstants.TRANSPORT_HTTP_COMPRESSION_LEVEL);
            int compressionStrategy = parameterService
                    .getInt(ParameterConstants.TRANSPORT_HTTP_COMPRESSION_STRATEGY);
            log.debug("@doFilter");
            boolean supportCompression = false;
            log.debug("requestURI= {}", req.getRequestURI());
            // Are we allowed to compress ?
            String s = (String) req.getParameter("gzip");
            if ("false".equals(s)) {
                log.debug("Got parameter of gzip=false.  Don't compress, just chain filter.");
                handleWithCompression(req, res);
                return;
            }
            @SuppressWarnings("rawtypes")
            Enumeration e = req.getHeaders("Accept-Encoding");
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                if (name.indexOf("gzip") != -1) {
                    log.debug("Supports compression.");
                    supportCompression = true;
                } else {
                    log.debug("Does not support compression.");
                }
            }
            if (!supportCompression) {
                log.debug("doFilter gets called without compression");
                handleWithCompression(req, res);
                return;
            } else {
                CompressionServletResponseWrapper wrappedResponse = new CompressionServletResponseWrapper(
                        res, compressionLevel, compressionStrategy);
                log.debug("doFilter gets called with compression");
                try {
                    handleWithCompression(req, wrappedResponse);
                } finally {
                    wrappedResponse.finishResponse();
                }
                return;
            }
        } else {
            handleWithCompression(req, res);
        }
    }

    abstract protected void handleWithCompression(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException;
}
