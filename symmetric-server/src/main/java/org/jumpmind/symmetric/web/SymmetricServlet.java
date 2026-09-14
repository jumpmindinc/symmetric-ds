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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * This servlet handles web requests to SymmetricDS.
 * 
 * Configured within web.xml
 * 
 * <pre>
 *  &lt;servlet&gt;
 *    &lt;servlet-name&gt;SymmetricServlet&lt;/filter-name&gt;
 *    &lt;servlet-class&gt;
 *      org.jumpmind.symmetric.web.SymmetricServlet
 *    &lt;/servlet-class&gt;
 *  &lt;/servlet&gt;
 * 
 *  &lt;servlet-mapping&gt;
 *    &lt;servlet-name&gt;SymmetricServlet&lt;/servlet-name&gt;
 *    &lt;url-pattern&gt;*&lt;/url-pattern&gt;
 *  &lt;/servlet-mapping&gt;
 * </pre>
 * 
 * @since 1.4.0
 */
public class SymmetricServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_NETWORK_ERROR_FOR_LOGGING = 5;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected Map<String, Integer> rejectionStatusByEngine = new HashMap<String, Integer>();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        ServerSymmetricEngine engine = ServletUtils.findEngine(req, getServletContext());
        MDC.put("engineName", engine != null ? engine.getEngineName() : "?");
        if (engine == null) {
            if (ServletUtils.getSymmetricEngineHolder(getServletContext()).areEnginesStarting()) {
                if (shouldLog(ServletUtils.getEngineNameFromUrl(req), 1)) {
                    log.info("Requests for engine {} are being rejected because nodes are still initializing", ServletUtils.getEngineNameFromUrl(req));
                }
                sendError(req, res, WebConstants.SC_SERVICE_UNAVAILABLE, "Service is starting");
            } else {
                log.warn("Rejected request for unknown engine {} from host {} with URI of {}", ServletUtils.getEngineNameFromUrl(req), getHost(req),
                        ServletUtils.normalizeRequestUri(req));
                sendError(req, res, WebConstants.SC_NO_ENGINE, "No engine here with that name");
            }
        } else if (engine.isStarted()) {
            IUriHandler handler = findMatchingHandler(engine, req);
            if (handler != null) {
                List<IInterceptor> beforeInterceptors = handler.getInterceptors();
                List<IInterceptor> afterInterceptors = null;
                try {
                    String nodeId = req.getParameter(WebConstants.NODE_ID);
                    if (beforeInterceptors != null) {
                        afterInterceptors = new ArrayList<IInterceptor>(beforeInterceptors.size());
                        for (IInterceptor interceptor : beforeInterceptors) {
                            if (interceptor.before(req, res)) {
                                afterInterceptors.add(interceptor);
                            } else {
                                return;
                            }
                        }
                    }
                    handler.handle(req, res);
                    engine.resetErrorCountForNode(nodeId);
                } catch (Exception e) {
                    logException(req, engine, e);
                    if (!res.isCommitted()) {
                        ServletUtils.sendError(res, WebConstants.SC_INTERNAL_ERROR, "Internal error occurred, see log file");
                    }
                } finally {
                    if (afterInterceptors != null) {
                        for (IInterceptor interceptor : afterInterceptors) {
                            interceptor.after(req, res);
                        }
                    }
                }
            } else {
                sendError(req, res, WebConstants.SC_BAD_REQUEST, "No matching URI handler");
            }
        } else if (engine.isStarting()) {
            if (shouldLog(engine.getEngineName(), 2)) {
                log.info("Requests for engine {} are being rejected while it is starting", engine.getEngineName());
            }
            sendError(req, res, WebConstants.SC_SERVICE_UNAVAILABLE, "Engine is starting");
        } else if (!engine.isConfigured()) {
            if (shouldLog(engine.getEngineName(), 3)) {
                log.info("Requests for engine {} are being rejected because it is not configured properly", engine.getEngineName());
            }
            sendError(req, res, WebConstants.SC_SERVICE_UNAVAILABLE, "Engine is not configured");
        } else {
            if (shouldLog(engine.getEngineName(), 4)) {
                log.info("Requests for engine {} are being rejected because it is not started", engine.getEngineName());
            }
            sendError(req, res, WebConstants.SC_SERVICE_UNAVAILABLE, "Engine is not started");
        }
    }

    protected Collection<IUriHandler> getUriHandlersFrom(ServerSymmetricEngine engine) {
        if (engine != null) {
            return engine.getUriHandlers();
        } else {
            return null;
        }
    }

    protected IUriHandler findMatchingHandler(ServerSymmetricEngine engine, HttpServletRequest req)
            throws ServletException {
        Collection<IUriHandler> handlers = getUriHandlersFrom(engine);
        if (handlers != null) {
            for (IUriHandler handler : handlers) {
                if (matchesUriPattern(normalizeUri(engine, req), handler.getUriPattern())
                        && handler.isEnabled()) {
                    return handler;
                }
            }
        }
        return null;
    }

    protected boolean matchesUriPattern(String uri, String uriPattern) {
        boolean retVal = false;
        String path = StringUtils.defaultIfEmpty(uri, "/");
        final String pattern = StringUtils.defaultIfEmpty(uriPattern, "/");
        if ("/".equals(pattern) || "/*".equals(pattern) || pattern.equals(path)) {
            retVal = true;
        } else {
            final String[] patternParts = StringUtils.split(pattern, "/");
            final String[] pathParts = StringUtils.split(path, "/");
            boolean matches = true;
            for (int i = 0; i < patternParts.length && i < pathParts.length && matches; i++) {
                final String patternPart = patternParts[i];
                matches = "*".equals(patternPart) || patternPart.equals(pathParts[i]);
            }
            retVal = matches;
        }
        return retVal;
    }

    protected String normalizeUri(ISymmetricEngine engine, HttpServletRequest req) {
        String uri = ServletUtils.normalizeRequestUri((HttpServletRequest) req);
        if (engine != null) {
            String removeString = "/" + engine.getEngineName();
            if (uri.startsWith(removeString)) {
                uri = uri.substring(removeString.length());
            }
        }
        return uri;
    }

    protected void logException(HttpServletRequest req, ServerSymmetricEngine engine, Exception ex) {
        String nodeId = req.getParameter(WebConstants.NODE_ID);
        String externalId = req.getParameter(WebConstants.EXTERNAL_ID);
        String method = req instanceof HttpServletRequest ? ((HttpServletRequest) req).getMethod() : "";
        Throwable root = ExceptionUtils.getRootCause(ex);
        int errorCount = engine.getErrorCountFor(nodeId);
        String msg = String.format("Error while processing %s request for node: %s", method, nodeId);
        if (!StringUtils.isEmpty(externalId) && !Strings.CS.equals(nodeId, externalId)) {
            msg += String.format(" externalId: %s", externalId);
        }
        msg += " at " + getHost(req);
        msg += String.format(" with path: %s", ServletUtils.normalizeRequestUri(req));
        if (!(ex instanceof IOException || root instanceof IOException) || errorCount >= MAX_NETWORK_ERROR_FOR_LOGGING) {
            log.error(msg, ex);
        } else {
            if (log.isDebugEnabled()) {
                log.info(msg, ex);
            } else {
                log.info(msg + " The message is: {} {}", ex.getClass().getName(), ex.getMessage());
            }
        }
        engine.incrementErrorCountForNode(nodeId);
    }

    protected String getHost(HttpServletRequest req) {
        String address = req.getRemoteAddr();
        String hostName = req.getRemoteHost();
        if (!StringUtils.isEmpty(hostName) && !StringUtils.isEmpty(address)) {
            if (Strings.CS.equals(hostName, address)) {
                return hostName;
            } else {
                return hostName + " (" + address + ")";
            }
        } else if (!StringUtils.isEmpty(hostName)) {
            return hostName;
        } else {
            return address;
        }
    }

    protected void sendError(HttpServletRequest req, HttpServletResponse res, int code, String message) throws IOException {
        log.debug("Rejecting request {} from host {} with code {} and message: {}", ServletUtils.normalizeRequestUri(req), getHost(req), code, message);
        ServletUtils.sendError(res, code, message);
    }

    protected boolean shouldLog(String engineName, int status) {
        Integer lastStatus = rejectionStatusByEngine.get(engineName);
        if (lastStatus == null || lastStatus.intValue() < status) {
            if (rejectionStatusByEngine.size() > 10000) {
                rejectionStatusByEngine.clear();
            }
            rejectionStatusByEngine.put(engineName, status);
            return true;
        }
        return false;
    }
}
