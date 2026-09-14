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
import java.io.OutputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.RegistrationRedirectException;
import org.jumpmind.symmetric.transport.http.HttpTransportManager;

/**
 * Handler that delegates to the {@link IRegistrationService}
 */
public class RegistrationUriHandler extends AbstractUriHandler {
    private IRegistrationService registrationService;

    public RegistrationUriHandler(IParameterService parameterService,
            IRegistrationService registrationService, IInterceptor... interceptors) {
        super("/registration/*", parameterService, interceptors);
        this.registrationService = registrationService;
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        Node node = transform(req);
        try {
            OutputStream outputStream = res.getOutputStream();
            String pushRegistration = ServletUtils.getParameter(req, WebConstants.PUSH_REGISTRATION);
            if (Boolean.TRUE.toString().equals(pushRegistration)) {
                node.setNodeId(ServletUtils.getParameter(req, WebConstants.NODE_ID));
                if (!parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)) {
                    ServletUtils.sendError(res, WebConstants.REGISTRATION_NOT_OPEN, "Registration not allowed over push");
                    return;
                }
                if (registrationService.isRegisteredWithServer() && !registrationService.isRegistrationOpen()) {
                    ServletUtils.sendError(res, WebConstants.REGISTRATION_NOT_OPEN, "Registration not open");
                    return;
                }
                if (!Strings.CS.equals(node.getSyncUrl(), parameterService.getRegistrationUrl())) {
                    ServletUtils.sendError(res, WebConstants.REGISTRATION_NOT_OPEN, String.format("Not allowed to register with %s", node.getSyncUrl()));
                    return;
                }
                boolean success = false;
                if (WebConstants.METHOD_POST.equals(req.getMethod())) {
                    log.info("Received push registration request from {}", node);
                    success = registrationService.writeRegistrationProperties(outputStream);
                } else if (WebConstants.METHOD_PUT.equals(req.getMethod())) {
                    log.info("Loading push registration batch from {}", node);
                    success = registrationService.loadRegistrationBatch(node, createInputStream(req), outputStream);
                }
                if (!success) {
                    ServletUtils.sendError(res, WebConstants.SC_SERVICE_ERROR, "Error during registration");
                }
                return;
            }
            String userId = ServletUtils.getParameter(req, WebConstants.REG_USER_ID);
            String password = ServletUtils.getParameter(req, WebConstants.REG_PASSWORD);
            if (!registerNode(node, getHostName(req), getIpAddress(req), outputStream, userId, password)) {
                log.warn("{} was not allowed to register.", node);
                ServletUtils.sendError(res, WebConstants.REGISTRATION_NOT_OPEN, String.format("%s was not allowed to register.",
                        node));
            }
        } catch (RegistrationRedirectException e) {
            String redirectedRegistrationURL = HttpTransportManager.buildRegistrationUrl(e.getRedirectionUrl(), node);
            if (StringUtils.isNotEmpty(req.getQueryString())) {
                redirectedRegistrationURL = redirectedRegistrationURL + "?" + req.getQueryString();
            }
            res.sendRedirect(redirectedRegistrationURL);
        }
    }

    private Node transform(HttpServletRequest req) {
        Node node = new Node();
        node.setNodeGroupId(ServletUtils.getParameter(req, WebConstants.NODE_GROUP_ID));
        node.setSymmetricVersion(ServletUtils.getParameter(req, WebConstants.SYMMETRIC_VERSION));
        node.setExternalId(ServletUtils.getParameter(req, WebConstants.EXTERNAL_ID));
        String syncUrlString = ServletUtils.getParameter(req, WebConstants.SYNC_URL);
        if (StringUtils.isNotBlank(syncUrlString)) {
            node.setSyncUrl(syncUrlString);
        }
        node.setSchemaVersion(ServletUtils.getParameter(req, WebConstants.SCHEMA_VERSION));
        node.setDatabaseType(ServletUtils.getParameter(req, WebConstants.DATABASE_TYPE));
        node.setDatabaseVersion(ServletUtils.getParameter(req, WebConstants.DATABASE_VERSION));
        node.setDeploymentType(ServletUtils.getParameter(req, WebConstants.DEPLOYMENT_TYPE));
        node.setDatabaseName(ServletUtils.getParameter(req, WebConstants.DATABASE_NAME));
        return node;
    }

    protected String getHostName(HttpServletRequest req) {
        String hostName = ServletUtils.getParameter(req, WebConstants.HOST_NAME);
        if (StringUtils.isBlank(hostName)) {
            hostName = req.getRemoteHost();
        }
        return hostName;
    }

    protected String getIpAddress(HttpServletRequest req) {
        String ipAdddress = ServletUtils.getParameter(req, WebConstants.IP_ADDRESS);
        if (StringUtils.isBlank(ipAdddress)) {
            ipAdddress = req.getRemoteAddr();
        }
        return ipAdddress;
    }

    protected boolean registerNode(Node node, String remoteHost, String remoteAddress, OutputStream outputStream,
            String userId, String password) throws IOException {
        return registrationService.registerNode(node, remoteHost, remoteAddress, outputStream, userId, password, true);
    }
}
