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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jumpmind.symmetric.common.InfoConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;

/**
 * Responsible for providing high level information about the node in property format
 */
public class InfoUriHandler extends AbstractUriHandler {
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private IClusterService clusterService;

    public InfoUriHandler(IParameterService parameterService,
            INodeService nodeService,
            IConfigurationService configurationService, IClusterService clusterService, IInterceptor[] interceptors) {
        super("/info/*", parameterService, interceptors);
        this.nodeService = nodeService;
        this.configurationService = configurationService;
        this.clusterService = clusterService;
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        res.setContentType("text/plain");
        Node identity = nodeService.findIdentity();
        List<NodeGroupLink> nodeGroupLinks = configurationService.getNodeGroupLinks(true);
        Properties properties = new Properties();
        properties.setProperty(ParameterConstants.EXTERNAL_ID, parameterService.getExternalId());
        properties.setProperty(ParameterConstants.NODE_GROUP_ID, parameterService.getNodeGroupId());
        properties.setProperty(ParameterConstants.EXTERNAL_ID, parameterService.getExternalId());
        if (nodeGroupLinks != null) {
            Set<String> groups = new HashSet<String>();
            StringBuilder b = new StringBuilder();
            for (NodeGroupLink nodeGroupLink : nodeGroupLinks) {
                if (nodeGroupLink.getSourceNodeGroupId().equals(identity.getNodeGroupId())) {
                    groups.add(nodeGroupLink.getTargetNodeGroupId());
                } else if (nodeGroupLink.getTargetNodeGroupId().equals(identity.getNodeGroupId())) {
                    groups.add(nodeGroupLink.getSourceNodeGroupId());
                }
            }
            if (parameterService.is(ParameterConstants.SHOW_PIPELINES_VIEW)) {
                Set<String> occupiedGroupSet = new HashSet<String>();
                List<Node> nodeList = nodeService.findAllNodes(true);
                for (String group : groups) {
                    for (Node node : nodeList) {
                        if (node.getNodeGroupId().equals(group)) {
                            occupiedGroupSet.add(group);
                            log.debug("Group {} is occupied, so it will be excluded from the {}", group, InfoConstants.NODE_GROUP_IDS);
                            break;
                        }
                    }
                }
                groups.removeAll(occupiedGroupSet);
            }
            for (String group : groups) {
                b.append(group).append(",");
            }
            properties.setProperty(InfoConstants.NODE_GROUP_IDS, b.substring(0, b.length() > 0 ? b.length() - 1 : 0));
        }
        if (identity != null) {
            properties.setProperty(InfoConstants.NODE_ID, identity.getNodeId());
            if (identity.getDatabaseType() != null) {
                properties.setProperty(InfoConstants.DATABASE_TYPE, identity.getDatabaseType());
            }
            if (identity.getDatabaseVersion() != null) {
                properties.setProperty(InfoConstants.DATABASE_VERSION, identity.getDatabaseVersion());
            }
            if (identity.getDeploymentType() != null) {
                properties.setProperty(InfoConstants.DEPLOYMENT_TYPE, identity.getDeploymentType());
            }
            if (identity.getSymmetricVersion() != null) {
                properties.setProperty(InfoConstants.SYMMETRIC_VERSION, identity.getSymmetricVersion());
            }
        }
        properties.setProperty(InfoConstants.BULK_LOADER_ENABLED, String.valueOf(configurationService.isBulkLoaderEnabled()));
        boolean isClustered = parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        properties.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, Boolean.toString(isClustered));
        if (isClustered) {
            properties.setProperty(ParameterConstants.CLUSTER_SERVER_ID, clusterService.getServerId());
        }
        properties.store(res.getOutputStream(), "SymmetricDS");
        res.flushBuffer();
    }
}