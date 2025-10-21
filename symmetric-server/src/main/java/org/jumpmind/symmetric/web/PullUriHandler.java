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
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.ChannelMapWrapper;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.jumpmind.symmetric.transport.TransportUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles data pulls from other nodes.
 */
public class PullUriHandler extends AbstractCompressionUriHandler {
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private IDataExtractorService dataExtractorService;
    private IRegistrationService registrationService;
    private IStatisticManager statisticManager;
    private IOutgoingBatchService outgoingBatchService;

    public PullUriHandler(IParameterService parameterService,
            INodeService nodeService,
            IConfigurationService configurationService, IDataExtractorService dataExtractorService,
            IRegistrationService registrationService, IStatisticManager statisticManager, IOutgoingBatchService outgoingBatchService,
            IInterceptor... interceptors) {
        super("/pull/*", parameterService, interceptors);
        this.nodeService = nodeService;
        this.configurationService = configurationService;
        this.dataExtractorService = dataExtractorService;
        this.registrationService = registrationService;
        this.statisticManager = statisticManager;
        this.outgoingBatchService = outgoingBatchService;
    }

    public void handleWithCompression(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        // request has the "other" nodes info
        String nodeId = ServletUtils.getParameter(req, WebConstants.NODE_ID);
        log.debug("Pull requested from node {} at remote address {}", nodeId, req.getRemoteAddr());
        if (StringUtils.isBlank(nodeId)) {
            ServletUtils.sendError(res, WebConstants.SC_BAD_REQUEST, "Node must be specified");
            return;
        }
        ChannelMapWrapper mapWrapper = new ChannelMapWrapper();
        mapWrapper.addSuspendChannels(nodeId, req.getHeader(WebConstants.SUSPENDED_CHANNELS));
        mapWrapper.addIgnoreChannels(nodeId, req.getHeader(WebConstants.IGNORED_CHANNELS));
        mapWrapper.setChannelQueue(req.getHeader(WebConstants.CHANNEL_QUEUE));
        // pull out headers and pass to pull() method
        handlePull(nodeId, req.getRemoteHost(), req.getRemoteAddr(), res.getOutputStream(),
                req.getHeader(WebConstants.HEADER_ACCEPT_CHARSET), res, mapWrapper);
        log.debug("Pull completed for {} at remote address {}", nodeId, req.getRemoteAddr());
    }

    protected void handlePull(String nodeId, String remoteHost, String remoteAddress,
            OutputStream outputStream, String encoding, HttpServletResponse res, ChannelMapWrapper mapWrapper) throws IOException {
        NodeSecurity nodeSecurity = nodeService.findNodeSecurity(nodeId, true);
        long ts = System.currentTimeMillis();
        try {
            ChannelMapWrapper remoteSuspendIgnoreChannelsList = configurationService.getSuspendIgnoreChannelLists();
            mapWrapper.addSuspendChannels(remoteSuspendIgnoreChannelsList.getSuspendChannels());
            mapWrapper.addUnsuspendChannels(remoteSuspendIgnoreChannelsList.getUnsuspendChannels());
            mapWrapper.addIgnoreChannels(remoteSuspendIgnoreChannelsList.getIgnoreChannels());
            mapWrapper.addUnignoreChannels(remoteSuspendIgnoreChannelsList.getUnignoreChannels());
            if (nodeSecurity != null) {
                String createdAtNodeId = nodeSecurity.getCreatedAtNodeId();
                if (nodeSecurity.isRegistrationEnabled() &&
                        (createdAtNodeId == null || createdAtNodeId.equals(nodeService.findIdentityNodeId()))) {
                    registrationService.registerNode(nodeService.findNode(nodeId), remoteHost,
                            remoteAddress, outputStream, null, null, false);
                } else {
                    IOutgoingTransport outgoingTransport = createOutgoingTransport(outputStream, encoding,
                            mapWrapper);
                    ProcessInfo processInfo = statisticManager.newProcessInfo(new ProcessInfoKey(
                            nodeService.findIdentityNodeId(), mapWrapper.getChannelQueue(), nodeId, ProcessType.PULL_HANDLER_EXTRACT));
                    try {
                        Node targetNode = nodeService.findNode(nodeId, true);
                        if (Constants.QUEUE_DEFAULT.equals(mapWrapper.getChannelQueue())) {
                            addReadyQueuesHeader(nodeId, res);
                        }
                        List<OutgoingBatch> batchList = dataExtractorService.extract(processInfo, targetNode,
                                mapWrapper.getChannelQueue(), outgoingTransport);
                        logDataReceivedFromPull(targetNode, batchList, processInfo, remoteHost);
                        if (processInfo.getStatus() != ProcessStatus.ERROR) {
                            addPendingBatchCounts(targetNode.getNodeId(), res);
                            processInfo.setStatus(ProcessStatus.OK);
                        }
                    } finally {
                        if (processInfo.getStatus() != ProcessStatus.OK) {
                            processInfo.setStatus(ProcessStatus.ERROR);
                        }
                    }
                    outgoingTransport.close();
                }
            } else {
                log.warn("Node {} does not exist", nodeId);
            }
        } finally {
            statisticManager.incrementNodesPulled(1);
            statisticManager.incrementTotalNodesPulledTime(System.currentTimeMillis() - ts);
        }
        log.debug("Pull completed for {} at remote address {} for queue {}", nodeId, remoteAddress, mapWrapper.getChannelQueue());
    }

    private void addReadyQueuesHeader(String nodeId, HttpServletResponse res) {
        if (parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES) && configurationService.getQueues(false).size() > 1 &&
                !parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)) {
            Collection<String> readyQueues = outgoingBatchService.getReadyQueues(nodeId, false);
            String readyQueuesHeader = StringUtils.joinWith(",", readyQueues.toArray());
            log.debug("Ready queues for node {}: {}", nodeId, readyQueuesHeader);
            res.setHeader(WebConstants.HEADER_READY_QUEUES, readyQueuesHeader);
        }
    }

    private void addPendingBatchCounts(String targetNodeId, HttpServletResponse res) {
        if (this.parameterService.is(ParameterConstants.HYBRID_PUSH_PULL_ENABLED)) {
            Map<String, Integer> batchesToSendByChannel = this.outgoingBatchService.countOutgoingBatchesPendingByChannel(targetNodeId);
            if (batchesToSendByChannel != null && !batchesToSendByChannel.isEmpty()) {
                res.addHeader(WebConstants.BATCH_TO_SEND_COUNT, TransportUtils.toCSV(batchesToSendByChannel));
            }
        }
    }

    private void logDataReceivedFromPull(Node targetNode, List<OutgoingBatch> batchList, ProcessInfo processInfo, String remoteHost) {
        int batchesCount = 0;
        int dataCount = 0;
        for (OutgoingBatch outgoingBatch : batchList) {
            if (outgoingBatch.getStatus() == org.jumpmind.symmetric.model.OutgoingBatch.Status.LD) {
                batchesCount++;
                dataCount += outgoingBatch.getDataRowCount();
            }
        }
        if (batchesCount > 0) {
            log.info("{} data and {} batches sent during pull request from {}", dataCount, batchesCount, targetNode);
        }
    }
}
