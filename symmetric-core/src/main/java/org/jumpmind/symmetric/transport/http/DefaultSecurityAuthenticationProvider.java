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

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.ISecurityAuthenticationProvider;
import org.jumpmind.symmetric.web.WebConstants;

/**
 * Authenticates with the node's security token, sent in a request header, and with the session the remote node hands back after the first successful request.
 */
public class DefaultSecurityAuthenticationProvider implements ISecurityAuthenticationProvider {
    protected final Map<String, String> sessionIdByUri = new HashMap<String, String>();
    protected final boolean useHeaderSecurityToken;
    protected final boolean useSessionAuth;

    public DefaultSecurityAuthenticationProvider(ISymmetricEngine engine) {
        IParameterService parameterService = engine.getParameterService();
        useHeaderSecurityToken = parameterService.is(ParameterConstants.TRANSPORT_HTTP_USE_HEADER_SECURITY_TOKEN);
        useSessionAuth = parameterService.is(ParameterConstants.TRANSPORT_HTTP_USE_SESSION_AUTH);
    }

    @Override
    public void authenticate(HttpConnection connection, String securityToken) {
        boolean hasSession = false;
        if (useSessionAuth) {
            String sessionId = sessionIdByUri.get(getUri(connection));
            if (sessionId != null) {
                connection.setRequestProperty(WebConstants.HEADER_SESSION_ID, sessionId);
                hasSession = true;
            }
        }
        if (securityToken != null && useHeaderSecurityToken && !hasSession) {
            connection.setRequestProperty(WebConstants.HEADER_SECURITY_TOKEN, securityToken);
        }
    }

    @Override
    public boolean isSecurityTokenInHeader() {
        return useHeaderSecurityToken;
    }

    @Override
    public void updateSession(HttpConnection connection) {
        if (useSessionAuth) {
            String sessionId = connection.getHeaderField(WebConstants.HEADER_SET_SESSION_ID);
            if (sessionId != null) {
                sessionIdByUri.put(getUri(connection), sessionId);
            }
        }
    }

    @Override
    public void clearSession(HttpConnection connection) {
        if (useSessionAuth) {
            sessionIdByUri.remove(getUri(connection));
        }
    }

    protected String getUri(HttpConnection connection) {
        String uri = connection.getURL().toExternalForm();
        uri = uri.substring(0, uri.lastIndexOf("/"));
        return uri;
    }
}
