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
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload2.core.FileUploadException;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.springframework.util.StringUtils;

public class PushStatusUriHandler extends AbstractUriHandler {
    private INodeCommunicationService nodeCommunicationService;

    public PushStatusUriHandler(IParameterService parameterService,
            INodeCommunicationService nodeCommunicationService, IInterceptor... interceptors) {
        super("/pushstatus/*", parameterService, interceptors);
        this.nodeCommunicationService = nodeCommunicationService;
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException, FileUploadException {
        // http://localhost:31415/sync/corp-000/pushstats?nodeId=001&securityToken=5d1c92bbacbe2edb9e1ca5dbb0e481&hostName=mac-mmichalek.local&ipAddress=fe80%3A0%3A0%3A0%3A0%3A0%3A0%3A1%251
        String nodeId = ServletUtils.getParameter(req, WebConstants.NODE_ID);
        String batchToSendCountParam = ServletUtils.getParameter(req, WebConstants.BATCH_TO_SEND_COUNT);
        log.debug("Push stats for nodeId: {} batchToSendCountParam: '{}'", nodeId, batchToSendCountParam);
        // queueName:4,anotherQueueName:6
        if (StringUtils.hasLength(batchToSendCountParam)) {
            try {
                Map<String, Integer> queuesToBatchCounts = nodeCommunicationService.parseQueueToBatchCounts(batchToSendCountParam);
                log.debug("Push stats for nodeId: {} queuesToBatchCounts: '{}'", nodeId, queuesToBatchCounts);
                nodeCommunicationService.updateBatchToSendCounts(nodeId, queuesToBatchCounts);
            } catch (Exception ex) {
                String msg = "Failed to parse batchToSendCountParam [" + batchToSendCountParam + "] " + req;
                log.warn(msg, ex);
                res.sendError(WebConstants.SC_BAD_REQUEST, "Couldn't parse batch_to_send_count.");
            }
        }
    }
}
