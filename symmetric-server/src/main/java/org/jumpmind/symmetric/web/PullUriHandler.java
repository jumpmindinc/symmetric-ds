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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.NodeChannels;
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
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-");
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
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addSuspendChannels(nodeId, req.getHeader(WebConstants.SUSPENDED_CHANNELS));
        nodeChannels.addIgnoreChannels(nodeId, req.getHeader(WebConstants.IGNORED_CHANNELS));
        nodeChannels.setChannelQueue(req.getHeader(WebConstants.CHANNEL_QUEUE));
        // pull out headers and pass to pull() method
        String batchIdParam = ServletUtils.getParameter(req, WebConstants.BATCH_ID);
        String ifETagHeader = req.getHeader(WebConstants.HEADER_IF_ETAG);
        String rangeHeader = req.getHeader(WebConstants.HEADER_RANGE);
        log.debug("Pull request from node {} on queue {}: batchId={}, {}={}, {}={}", nodeId, nodeChannels.getChannelQueue(),
                batchIdParam, WebConstants.HEADER_IF_ETAG, ifETagHeader, WebConstants.HEADER_RANGE, rangeHeader);
        ResumeRequest resumeRequest = new ResumeRequest(nodeId, batchIdParam, ifETagHeader, rangeHeader, nodeChannels.getChannelQueue());
        handlePull(resumeRequest, req.getRemoteHost(), req.getRemoteAddr(), res.getOutputStream(),
                req.getHeader(WebConstants.HEADER_ACCEPT_CHARSET), res, nodeChannels);
        log.debug("Pull completed for {} at remote address {}", nodeId, req.getRemoteAddr());
    }

    protected void handlePull(ResumeRequest resumeRequest, String remoteHost, String remoteAddress,
            OutputStream outputStream, String encoding, HttpServletResponse res, NodeChannels nodeChannels) throws IOException {
        String nodeId = resumeRequest.getNodeId();
        NodeSecurity nodeSecurity = nodeService.findNodeSecurity(nodeId, true);
        long ts = System.currentTimeMillis();
        try {
            NodeChannels remoteSuspendIgnoreChannelsList = configurationService.getSuspendIgnoreChannelLists();
            nodeChannels.addSuspendChannels(remoteSuspendIgnoreChannelsList.getSuspendChannels());
            nodeChannels.addIgnoreChannels(remoteSuspendIgnoreChannelsList.getIgnoreChannels());
            if (nodeSecurity != null) {
                String createdAtNodeId = nodeSecurity.getCreatedAtNodeId();
                if (nodeSecurity.isRegistrationEnabled() &&
                        (createdAtNodeId == null || createdAtNodeId.equals(nodeService.findIdentityNodeId()))) {
                    registrationService.registerNode(nodeService.findNode(nodeId), remoteHost,
                            remoteAddress, outputStream, null, null, false);
                } else {
                    IOutgoingTransport outgoingTransport = createOutgoingTransport(outputStream, encoding,
                            nodeChannels);
                    ProcessInfo processInfo = statisticManager.newProcessInfo(new ProcessInfoKey(
                            nodeService.findIdentityNodeId(), nodeChannels.getChannelQueue(), nodeId, ProcessType.PULL_HANDLER_EXTRACT));
                    try {
                        Node targetNode = nodeService.findNode(nodeId, true);
                        if (Constants.QUEUE_DEFAULT.equals(nodeChannels.getChannelQueue())) {
                            addReadyQueuesHeader(nodeId, res);
                        }
                        if (StringUtils.isNotBlank(resumeRequest.getBatchIdParam()) && parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)
                                && handleResume(resumeRequest, outgoingTransport, res, processInfo)) {
                            processInfo.setStatus(ProcessStatus.OK);
                        } else {
                            List<OutgoingBatch> batchList = dataExtractorService.extract(processInfo, targetNode,
                                    nodeChannels.getChannelQueue(), outgoingTransport);
                            logDataReceivedFromPull(targetNode, batchList, processInfo, remoteHost);
                            if (processInfo.getStatus() != ProcessStatus.ERROR) {
                                addPendingBatchCounts(targetNode.getNodeId(), res);
                                processInfo.setStatus(ProcessStatus.OK);
                            }
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
        log.debug("Pull completed for {} at remote address {} for queue {}", nodeId, remoteAddress, nodeChannels.getChannelQueue());
    }

    /**
     * Serves a single, previously-interrupted batch pull directly from its staged resource, instead of the normal multi-batch {@code extract()} path. Returns
     * {@code false} (leaving the response untouched) whenever no valid, fully-staged, file-backed resource exists for the requested batch, so the caller can
     * fall through to a normal pull unchanged.
     * <p>
     * {@code request.getQueue()} must match the requested batch's own channel's configured queue - a node can have multiple active pull queues (e.g. "default"
     * and "system"), and the client's resume cache is keyed per node, not per queue, so a request for one queue's own batches could otherwise carry a stale or
     * mismatched {@code batchId} left over from a different queue's pending resume.
     */
    boolean handleResume(ResumeRequest request, IOutgoingTransport outgoingTransport, HttpServletResponse res, ProcessInfo processInfo) {
        String nodeId = request.getNodeId();
        long batchId = NumberUtils.toLong(request.getBatchIdParam(), -1L);
        if (batchId < 0) {
            return false;
        }
        OutgoingBatch batch = outgoingBatchService.findOutgoingBatch(batchId, nodeId);
        IStagedResource stagedResource = dataExtractorService.getStagedResourceForResume(batch);
        if (batch == null || stagedResource == null || stagedResource.getState() != State.DONE || !stagedResource.isFileResource()) {
            log.debug("Resume requested for batch {} from node {}, but no resumable staged resource was found. Falling back to a full pull.",
                    batchId, nodeId);
            return false;
        }
        Channel channel = configurationService.getChannel(batch.getChannelId());
        if (channel == null || !channel.getQueue().equals(request.getQueue())) {
            log.debug("Resume requested for batch {} from node {} on queue {}, but that batch's channel {} belongs to queue {}. "
                    + "Falling back to a full pull.", batchId, nodeId, request.getQueue(), batch.getChannelId(),
                    channel != null ? channel.getQueue() : null);
            return false;
        }
        stagedResource.refreshLastUpdateTime();
        long totalSize = stagedResource.getSize();
        StagedResourceETag etag = new StagedResourceETag(stagedResource.getGenerationTime(), totalSize);
        StagedResourceETag requestedETag = StagedResourceETag.fromJson(request.getIfETagHeader());
        Long requestedSkipCount = parseRangeSkipCount(request.getRangeHeader());
        boolean isPartial = etag.equals(requestedETag) && requestedSkipCount != null && requestedSkipCount >= 0
                && requestedSkipCount < totalSize;
        long skipCount = isPartial ? requestedSkipCount : 0;
        res.setHeader(WebConstants.HEADER_ETAG, etag.toJson());
        res.setHeader(WebConstants.HEADER_ACCEPT_RANGES, "bytes");
        if (isPartial) {
            res.setStatus(WebConstants.SC_PARTIAL_CONTENT);
            res.setHeader(WebConstants.HEADER_CONTENT_RANGE, skipCount + "-" + (totalSize - 1) + "/" + totalSize);
        }
        dataExtractorService.extractSingleBatchForResume(batch, stagedResource, outgoingTransport.getWriter(), skipCount, processInfo);
        log.debug("Served {} pull for batch {} to node {} ({} of {} skipped)", isPartial ? "resumed" : "full", batchId, nodeId,
                skipCount, totalSize);
        return true;
    }

    Long parseRangeSkipCount(String rangeHeader) {
        if (StringUtils.isBlank(rangeHeader)) {
            return null;
        }
        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    private void addReadyQueuesHeader(String nodeId, HttpServletResponse res) {
        String headerValue = TransportUtils.buildReadyQueuesHeader(parameterService, configurationService, outgoingBatchService, nodeId);
        if (headerValue != null) {
            log.debug("Ready queues for node {}: {}", nodeId, headerValue);
            res.setHeader(WebConstants.HEADER_READY_QUEUES, headerValue);
        }
    }

    private void addPendingBatchCounts(String targetNodeId, HttpServletResponse res) {
        String headerValue = TransportUtils.buildPendingBatchCountsHeader(parameterService, outgoingBatchService, targetNodeId);
        if (headerValue != null) {
            res.addHeader(WebConstants.BATCH_TO_SEND_COUNT, headerValue);
        }
    }

    private void logDataReceivedFromPull(Node targetNode, List<OutgoingBatch> batchList, ProcessInfo processInfo, String remoteHost) {
        int[] counts = TransportUtils.countLoadedBatches(batchList);
        int dataCount = counts[0], batchesCount = counts[1];
        if (batchesCount > 0) {
            log.info("{} data and {} batches sent during pull request from {}", dataCount, batchesCount, targetNode);
        }
    }

    static class ResumeRequest {
        private final String nodeId;
        private final String batchIdParam;
        private final String ifETagHeader;
        private final String rangeHeader;
        private final String queue;

        ResumeRequest(String nodeId, String batchIdParam, String ifETagHeader, String rangeHeader, String queue) {
            this.nodeId = nodeId;
            this.batchIdParam = batchIdParam;
            this.ifETagHeader = ifETagHeader;
            this.rangeHeader = rangeHeader;
            this.queue = queue;
        }

        String getNodeId() {
            return nodeId;
        }

        String getBatchIdParam() {
            return batchIdParam;
        }

        String getIfETagHeader() {
            return ifETagHeader;
        }

        String getRangeHeader() {
            return rangeHeader;
        }

        String getQueue() {
            return queue;
        }
    }
}
