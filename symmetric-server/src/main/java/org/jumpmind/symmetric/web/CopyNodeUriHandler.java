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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.math.NumberUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IRegistrationService;

/**
 * Handler that delegates to the {@link IRegistrationService}
 */
public class CopyNodeUriHandler extends AbstractUriHandler {
    private ISymmetricEngine engine;

    public CopyNodeUriHandler(ISymmetricEngine engine, IInterceptor... interceptors) {
        super("/copy/*", engine.getParameterService(), interceptors);
        this.engine = engine;
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        IRegistrationService registrationService = engine.getRegistrationService();
        IOutgoingBatchService outgoingBatchService = engine.getOutgoingBatchService();
        INodeService nodeService = engine.getNodeService();
        IConfigurationService configurationService = engine.getConfigurationService();
        String identityNodeId = nodeService.findIdentityNodeId();
        String copyFromNodeId = req.getParameter(WebConstants.NODE_ID);
        String newExternalId = req.getParameter(WebConstants.EXTERNAL_ID);
        String newGroupId = req.getParameter(WebConstants.NODE_GROUP_ID);
        String newNodeId = registrationService.openRegistration(newGroupId, newExternalId);
        log.info("Received a copy request.  New external_id={}, new node_group_id={}, old node_id={}, new node_id={}", new Object[] { newExternalId, newGroupId,
                copyFromNodeId, newNodeId });
        Set<String> channelIds = configurationService.getChannels(false).keySet();
        for (String channelId : channelIds) {
            String batchId = req.getParameter(channelId + "-" + identityNodeId);
            if (isNotBlank(batchId)) {
                outgoingBatchService.copyOutgoingBatches(channelId, NumberUtils.toLong(batchId.trim()), copyFromNodeId, newNodeId);
            }
        }
    }
}
