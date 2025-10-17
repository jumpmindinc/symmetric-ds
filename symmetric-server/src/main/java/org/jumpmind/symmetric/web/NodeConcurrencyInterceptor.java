/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.model.ChannelMap;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationStatus;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An intercepter that controls access to this node for pushes and pulls. It is configured within symmetric-web.xml
 */
public class NodeConcurrencyInterceptor implements IInterceptor {
    private static Logger log = LoggerFactory.getLogger(NodeConcurrencyInterceptor.class);
    private IConcurrentConnectionManager concurrentConnectionManager;
    private IConfigurationService configurationService;
    private INodeService nodeService;
    private IStatisticManager statisticManager;

    public NodeConcurrencyInterceptor(IConcurrentConnectionManager concurrentConnectionManager,
            IConfigurationService configurationService, INodeService nodeService, IStatisticManager statisticManager) {
        this.concurrentConnectionManager = concurrentConnectionManager;
        this.configurationService = configurationService;
        this.nodeService = nodeService;
        this.statisticManager = statisticManager;
    }

    public boolean before(HttpServletRequest req, HttpServletResponse resp) throws IOException,
            ServletException {
        String poolId = req.getRequestURI();
        String identityNodeId = nodeService.findIdentityNodeId();
        String nodeId = getNodeId(req);
        String method = req.getMethod();
        String threadChannel = req.getHeader(WebConstants.CHANNEL_QUEUE);
        String normalizedRequestUri = ServletUtils.normalizeRequestUri(req);
        boolean isPush = normalizedRequestUri.contains("push");
        boolean isFileSync = normalizedRequestUri.contains("filesync");
        if (method.equals(WebConstants.METHOD_HEAD) && isPush) {
            resp.setContentLength(0);
            ReservationStatus status = concurrentConnectionManager.reserveConnection(nodeId, threadChannel, poolId, ReservationType.SOFT, false);
            if (status == ReservationStatus.ACCEPTED) {
                try {
                    buildSuspendIgnoreResponseHeaders(nodeId, identityNodeId, resp);
                } catch (Exception ex) {
                    concurrentConnectionManager.releaseConnection(nodeId, threadChannel, poolId);
                    log.error("Error building response headers", ex);
                    ServletUtils.sendError(resp, WebConstants.SC_SERVICE_ERROR);
                }
            } else {
                statisticManager.incrementNodesRejected(1);
                sendError(resp, status, nodeId);
                return false;
            }
            if (configurationService.isMasterToMaster() && nodeService.isDataLoadStarted()) {
                NodeSecurity identity = nodeService.findNodeSecurity(identityNodeId, true);
                if (identity != null && nodeId != null && "registration".equals(identity.getInitialLoadCreateBy()) &&
                        !nodeId.equals(identity.getCreatedAtNodeId())) {
                    log.debug("Not allowing push from node {} until initial load from {} is complete", nodeId, identity.getCreatedAtNodeId());
                    ServletUtils.sendError(resp, WebConstants.INITIAL_LOAD_PENDING);
                    return false;
                }
            }
            NodeSecurity nodeSecurity = nodeService.findNodeSecurity(nodeId, true);
            if (nodeSecurity != null) {
                String createdAtNodeId = nodeSecurity.getCreatedAtNodeId();
                if (nodeSecurity.isRegistrationEnabled() && (createdAtNodeId == null || createdAtNodeId.equals(identityNodeId))) {
                    if (nodeSecurity.getRegistrationTime() != null) {
                        log.debug("Not allowing push from node {} because registration is pending", nodeId);
                        ServletUtils.sendError(resp, WebConstants.REGISTRATION_PENDING);
                    } else {
                        log.debug("Not allowing push from node {} because registration is required", nodeId);
                        ServletUtils.sendError(resp, WebConstants.REGISTRATION_REQUIRED);
                    }
                }
            }
            return false;
        } else {
            ReservationStatus status = concurrentConnectionManager.reserveConnection(nodeId, threadChannel, poolId, ReservationType.HARD, isPush
                    && (!isFileSync));
            if (status == ReservationStatus.ACCEPTED) {
                try {
                    buildSuspendIgnoreResponseHeaders(isPush ? nodeId : identityNodeId, isPush ? identityNodeId : nodeId, resp);
                    return true;
                } catch (Exception ex) {
                    concurrentConnectionManager.releaseConnection(nodeId, threadChannel, poolId);
                    log.error("Error building response headers", ex);
                    ServletUtils.sendError(resp, WebConstants.SC_SERVICE_ERROR);
                    return false;
                }
            } else {
                statisticManager.incrementNodesRejected(1);
                if (isPush && status == ReservationStatus.NOT_FOUND) {
                    log.warn("Missing reservation for push, so rejecting node {}", new Object[] { nodeId });
                }
                sendError(resp, status, nodeId);
                return false;
            }
        }
    }

    protected void sendError(HttpServletResponse resp, ReservationStatus status, String nodeId) throws IOException {
        if (status == ReservationStatus.DUPLICATE) {
            log.debug("Node {} is already connected", nodeId);
            ServletUtils.sendError(resp, WebConstants.SC_ALREADY_CONNECTED);
        } else if (status == ReservationStatus.NOT_FOUND) {
            log.debug("Node {} has no reservation here", nodeId);
            ServletUtils.sendError(resp, WebConstants.SC_NO_RESERVATION);
        } else {
            log.debug("Node {} rejected because service is busy", nodeId);
            ServletUtils.sendError(resp, WebConstants.SC_SERVICE_BUSY);
        }
    }

    protected String getNodeId(HttpServletRequest req) {
        String nodeId = StringUtils.trimToNull(req.getParameter(WebConstants.NODE_ID));
        if (StringUtils.isBlank(nodeId)) {
            // if this is a registration request, we won't have a node id to use.
            nodeId = StringUtils.trimToNull(req.getParameter(WebConstants.EXTERNAL_ID));
        }
        return nodeId;
    }

    public void after(HttpServletRequest req, HttpServletResponse resp) throws IOException,
            ServletException {
        String poolId = req.getRequestURI();
        String nodeId = getNodeId(req);
        String threadChannel = req.getHeader(WebConstants.CHANNEL_QUEUE);
        concurrentConnectionManager.releaseConnection(nodeId, threadChannel, poolId);
    }

    protected void buildSuspendIgnoreResponseHeaders(final String sourceNodeId, final String targetNodeId, final ServletResponse resp) {
        HttpServletResponse httpResponse = (HttpServletResponse) resp;
        ChannelMap suspendIgnoreChannels = configurationService.getSuspendIgnoreChannelLists(sourceNodeId);
        httpResponse.setHeader(WebConstants.SUSPENDED_CHANNELS, suspendIgnoreChannels.getSuspendChannelsAsString(targetNodeId));
        httpResponse.setHeader(WebConstants.IGNORED_CHANNELS, suspendIgnoreChannels.getIgnoreChannelsAsString(targetNodeId));
    }
}