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
import java.io.InputStream;
import java.io.OutputStream;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.RegistrationPendingException;
import org.jumpmind.symmetric.service.RegistrationRequiredException;
import org.jumpmind.symmetric.statistic.IStatisticManager;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles data pushes from nodes.
 */
public class PushUriHandler extends AbstractUriHandler {
    private IDataLoaderService dataLoaderService;
    private IStatisticManager statisticManager;
    private INodeService nodeService;

    public PushUriHandler(IParameterService parameterService, IDataLoaderService dataLoaderService,
            IStatisticManager statisticManager, INodeService nodeService,
            IInterceptor... interceptors) {
        super("/push/*", parameterService, interceptors);
        this.dataLoaderService = dataLoaderService;
        this.statisticManager = statisticManager;
        this.nodeService = nodeService;
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        String nodeId = ServletUtils.getParameter(req, WebConstants.NODE_ID);
        log.debug("Push requested from node {} at remote address {}", nodeId, req.getRemoteAddr());
        InputStream inputStream = createInputStream(req);
        OutputStream outputStream = res.getOutputStream();
        String channelQueue = req.getHeader(WebConstants.CHANNEL_QUEUE);
        int rc = push(nodeId, channelQueue, inputStream, outputStream);
        if (rc != WebConstants.SC_OK) {
            res.sendError(rc);
        }
        res.flushBuffer();
        log.debug("Push completed for {} at remote address {}", nodeId, req.getRemoteAddr());
    }

    protected int push(String sourceNodeId, String channelQueue, InputStream inputStream, OutputStream outputStream) throws IOException {
        long ts = System.currentTimeMillis();
        try {
            Node sourceNode = nodeService.findNode(sourceNodeId, true);
            dataLoaderService.loadDataFromPush(sourceNode, channelQueue, inputStream, outputStream);
        } catch (RegistrationPendingException e) {
            return WebConstants.REGISTRATION_PENDING;
        } catch (RegistrationRequiredException e) {
            return WebConstants.REGISTRATION_REQUIRED;
        } finally {
            statisticManager.incrementNodesPushed(1);
            statisticManager.incrementTotalNodesPushedTime(System.currentTimeMillis() - ts);
        }
        return WebConstants.SC_OK;
    }
}
