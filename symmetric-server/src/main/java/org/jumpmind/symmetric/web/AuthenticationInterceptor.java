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
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.INodeService.AuthenticationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Protect handlers by checking that the request is allowed.
 */
public class AuthenticationInterceptor implements IInterceptor {
    private Logger log = LoggerFactory.getLogger(getClass());
    private INodeService nodeService;
    private ISecurityService securityService;
    private Map<String, AuthenticationSession> sessions = new ConcurrentHashMap<String, AuthenticationSession>();
    private boolean useSessionAuth;
    private int sessionExpireMillis;
    private int maxSessions;
    private long maxSessionsLastTime;

    public AuthenticationInterceptor(INodeService nodeService, ISecurityService securityService,
            boolean useSessionAuth, int sessionExpireSeconds, int maxSessions) {
        this.nodeService = nodeService;
        this.securityService = securityService;
        this.useSessionAuth = useSessionAuth;
        this.sessionExpireMillis = sessionExpireSeconds * 1000;
        this.maxSessions = maxSessions;
    }

    public boolean before(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String nodeId = null;
        String securityToken = null;
        AuthenticationStatus status = null;
        if (log.isDebugEnabled()) {
            X509Certificate[] certs = (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate");
            if (certs != null && certs.length > 0) {
                log.debug("Client cert: " + certs[0].getSubjectX500Principal().getName());
            }
        }
        if (useSessionAuth && req.getHeader(WebConstants.HEADER_SESSION_ID) != null) {
            AuthenticationSession session = getSession(req, false);
            if (session != null) {
                nodeId = (String) session.getAttribute(WebConstants.NODE_ID);
                securityToken = (String) session.getAttribute(WebConstants.SECURITY_TOKEN);
                if (!StringUtils.isEmpty(securityToken) && !StringUtils.isEmpty(nodeId)) {
                    status = nodeService.getAuthenticationStatus(nodeId, securityToken);
                }
                if (status == null || status == AuthenticationStatus.FORBIDDEN || (status == AuthenticationStatus.ACCEPTED
                        && sessionExpireMillis > 0 && System.currentTimeMillis() - session.getCreationTime() > sessionExpireMillis)) {
                    log.debug("Node '{}' needs to renew authentication", nodeId);
                    ServletUtils.sendError(resp, WebConstants.SC_AUTH_EXPIRED);
                    return false;
                }
            } else {
                nodeId = req.getParameter(WebConstants.NODE_ID);
                log.debug("Node '{}' has unknown session ID", nodeId);
                ServletUtils.sendError(resp, WebConstants.SC_AUTH_EXPIRED);
                return false;
            }
        } else {
            securityToken = req.getHeader(WebConstants.HEADER_SECURITY_TOKEN);
            if (securityToken == null) {
                securityToken = req.getParameter(WebConstants.SECURITY_TOKEN);
            }
            nodeId = req.getParameter(WebConstants.NODE_ID);
            if (StringUtils.isEmpty(securityToken) || StringUtils.isEmpty(nodeId)) {
                if (!StringUtils.isEmpty(nodeId)) {
                    log.warn("Node '{}' failed to authenticate.  It is missing the security token", nodeId);
                }
                ServletUtils.sendError(resp, WebConstants.SC_FORBIDDEN, "Missing node ID or security token");
                return false;
            }
            status = nodeService.getAuthenticationStatus(nodeId, securityToken);
            if (useSessionAuth && status == AuthenticationStatus.ACCEPTED) {
                AuthenticationSession session = getSession(req, true);
                session.setAttribute(WebConstants.NODE_ID, nodeId);
                session.setAttribute(WebConstants.SECURITY_TOKEN, securityToken);
                resp.setHeader(WebConstants.HEADER_SET_SESSION_ID, session.getId());
            }
        }
        if (status == AuthenticationStatus.ACCEPTED) {
            log.debug("Node '{}' successfully authenticated", nodeId);
            nodeService.resetNodeFailedLogins(nodeId);
            return true;
        } else if (status == AuthenticationStatus.REGISTRATION_REQUIRED) {
            log.debug("Node '{}' failed to authenticate.  It was not registered", nodeId);
            ServletUtils.sendError(resp, WebConstants.REGISTRATION_REQUIRED);
            return false;
        } else if (status == AuthenticationStatus.SYNC_DISABLED) {
            log.debug("Node '{}' failed to authenticate.  It was not enabled", nodeId);
            ServletUtils.sendError(resp, WebConstants.SYNC_DISABLED);
            return false;
        } else {
            if (status == AuthenticationStatus.LOCKED) {
                log.warn("Node '{}' failed to authenticate.  It had too many login attempts", nodeId);
            } else if (status == AuthenticationStatus.FAILED_DECRYPT) {
                log.warn("Node '{}' failed to authenticate.  Failed to decrypt node password", nodeId);
            } else {
                log.warn("Node '{}' failed to authenticate.  It had the wrong password", nodeId);
                nodeService.incrementNodeFailedLogins(nodeId);
            }
            ServletUtils.sendError(resp, WebConstants.SC_FORBIDDEN);
            return false;
        }
    }

    protected AuthenticationSession getSession(HttpServletRequest req, boolean create) {
        String sessionId = req.getHeader(WebConstants.HEADER_SESSION_ID);
        AuthenticationSession session = null;
        if (sessionId != null) {
            session = sessions.get(sessionId);
        }
        if (session == null && create) {
            String nodeId = req.getParameter(WebConstants.NODE_ID);
            for (AuthenticationSession existingSession : sessions.values()) {
                if (nodeId.equals(existingSession.getAttribute(WebConstants.NODE_ID))) {
                    session = existingSession;
                    session.setCreationTime(System.currentTimeMillis());
                    break;
                }
            }
            if (session == null) {
                if (sessions.size() >= maxSessions) {
                    removeOldSessions();
                }
                String id = securityService.nextSecureHexString(30);
                session = new AuthenticationSession(id);
                sessions.put(id, session);
            }
        }
        return session;
    }

    protected synchronized void removeOldSessions() {
        long now = System.currentTimeMillis();
        int removedSessions = 0;
        AuthenticationSession oldestSession = null;
        Iterator<AuthenticationSession> iter = sessions.values().iterator();
        while (iter.hasNext()) {
            AuthenticationSession session = iter.next();
            if (now - session.getCreationTime() > sessionExpireMillis) {
                iter.remove();
                removedSessions++;
            } else if (oldestSession == null || session.getCreationTime() < oldestSession.getCreationTime()) {
                oldestSession = session;
            }
        }
        if (removedSessions == 0 && oldestSession != null) {
            sessions.remove(oldestSession.getId());
        }
        if (maxSessionsLastTime == 0 || now - maxSessionsLastTime > 60000) {
            maxSessionsLastTime = now;
            log.warn("Max node authentication sessions reached, removing old sessions. See parameter " + ParameterConstants.TRANSPORT_HTTP_SESSION_MAX_COUNT);
        }
    }

    public void after(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
    }
}