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
package org.jumpmind.symmetric.transport.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.RegistrationFailedException;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.AbstractTransportManager;
import org.jumpmind.symmetric.transport.BandwidthTestResults;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.ServiceNotReadyException;
import org.jumpmind.symmetric.transport.TransportUtils;
import org.jumpmind.symmetric.web.WebConstants;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Coordinates interaction between two symmetric engines in the same JVM.
 */
public class InternalTransportManager extends AbstractTransportManager implements ITransportManager {
    private static final Logger log = LoggerFactory.getLogger(InternalTransportManager.class);
    protected ISymmetricEngine symmetricEngine;

    public InternalTransportManager(ISymmetricEngine engine) {
        super(engine.getExtensionService());
        this.symmetricEngine = engine;
    }

    public IIncomingTransport getFilePullTransport(Node remote, final Node local,
            String securityToken, Map<String, String> requestProperties, String registrationUrl)
            throws IOException {
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(remote.getSyncUrl(), null, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os)
                    throws Exception {
                String localNodeId = local.getNodeId();
                log.debug("Internal file sync pull request received from {}", localNodeId);
                IOutgoingTransport transport = new InternalOutgoingTransport(respOs,
                        engine.getConfigurationService().getSuspendIgnoreChannelLists(localNodeId), null);
                ProcessInfo processInfo = engine.getStatisticManager().newProcessInfo(new ProcessInfoKey(
                        engine.getNodeService().findIdentityNodeId(), localNodeId, ProcessType.FILE_SYNC_PULL_HANDLER));
                try {
                    engine.getFileSyncService().sendFiles(processInfo, local, transport);
                    if (processInfo.getTotalBatchCount() == 0) {
                        log.debug("No files to pull");
                    }
                    processInfo.setStatus(ProcessStatus.OK);
                } catch (RuntimeException ex) {
                    processInfo.setStatus(ProcessStatus.ERROR);
                    throw ex;
                } finally {
                    transport.close();
                }
            }
        });
        return new InternalIncomingTransport(respIs);
    }

    public IIncomingTransport getPullTransport(Node remote, final Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        final NodeChannels suspendIgnoreChannels = symmetricEngine.getConfigurationService()
                .getSuspendIgnoreChannelLists(remote.getNodeId());
        if (requestProperties != null) {
            suspendIgnoreChannels.setChannelQueue(requestProperties.get(WebConstants.CHANNEL_QUEUE));
        }
        final Map<String, String> headers = new ConcurrentHashMap<String, String>();
        final CountDownLatch headersReady = new CountDownLatch(1);
        runAtClient(remote.getSyncUrl(), null, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os)
                    throws Exception {
                log.debug("Internal pull requested from node {}", local.getNodeId());
                try {
                    handlePull(engine, local, suspendIgnoreChannels, requestProperties, headers, headersReady, os);
                } finally {
                    headersReady.countDown();
                }
                log.debug("Internal pull completed for {} for queue {}", local.getNodeId(), suspendIgnoreChannels.getChannelQueue());
            }
        });
        return new InternalIncomingTransport(respIs, headers, headersReady);
    }

    protected void handlePull(ISymmetricEngine engine, Node local, NodeChannels suspendIgnoreChannels,
            Map<String, String> requestProperties, Map<String, String> headers, CountDownLatch headersReady,
            OutputStream os) throws IOException {
        INodeService nodeService = engine.getNodeService();
        IStatisticManager statisticManager = engine.getStatisticManager();
        String localNodeId = local.getNodeId();
        NodeSecurity localNodeSecurity = nodeService.findNodeSecurity(localNodeId, true);
        long ts = System.currentTimeMillis();
        try {
            NodeChannels remoteSuspendIgnoreChannels = engine.getConfigurationService().getSuspendIgnoreChannelLists();
            suspendIgnoreChannels.addSuspendChannels(remoteSuspendIgnoreChannels.getSuspendChannels());
            suspendIgnoreChannels.addIgnoreChannels(remoteSuspendIgnoreChannels.getIgnoreChannels());
            if (localNodeSecurity != null) {
                String createdAtNodeId = localNodeSecurity.getCreatedAtNodeId();
                if (localNodeSecurity.isRegistrationEnabled()
                        && (createdAtNodeId == null || createdAtNodeId.equals(nodeService.findIdentityNodeId()))) {
                    headersReady.countDown();
                    registerNode(engine, local, requestProperties, os);
                } else {
                    String queue = suspendIgnoreChannels.getChannelQueue();
                    if (Constants.QUEUE_DEFAULT.equals(queue)) {
                        addReadyQueuesHeader(engine, localNodeId, headers);
                    }
                    headersReady.countDown();
                    IOutgoingTransport transport = new InternalOutgoingTransport(os, suspendIgnoreChannels, StandardCharsets.UTF_8.name());
                    ProcessInfo processInfo = statisticManager
                            .newProcessInfo(new ProcessInfoKey(engine.getNodeService().findIdentityNodeId(), queue,
                                    localNodeId, ProcessType.PULL_HANDLER_EXTRACT));
                    List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
                    try {
                        batchList = engine.getDataExtractorService().extract(processInfo, local, transport);
                        addPendingBatchCounts(engine, localNodeId, headers);
                        processInfo.setStatus(ProcessStatus.OK);
                    } catch (RuntimeException ex) {
                        processInfo.setStatus(ProcessStatus.ERROR);
                        throw ex;
                    } finally {
                        logDataReceivedFromPull(local, batchList);
                    }
                    transport.close();
                }
            } else {
                log.warn("Node {} does not exist", localNodeId);
                headersReady.countDown();
            }
        } finally {
            statisticManager.incrementNodesPulled(1);
            statisticManager.incrementTotalNodesPulledTime(System.currentTimeMillis() - ts);
        }
    }

    protected void addReadyQueuesHeader(ISymmetricEngine engine, String nodeId, Map<String, String> headers) {
        String headerValue = TransportUtils.buildReadyQueuesHeader(engine.getParameterService(),
                engine.getConfigurationService(), engine.getOutgoingBatchService(), nodeId);
        if (headerValue != null) {
            log.debug("Ready queues for node {}: {}", nodeId, headerValue);
            headers.put(WebConstants.HEADER_READY_QUEUES, headerValue);
        }
    }

    protected void addPendingBatchCounts(ISymmetricEngine engine, String targetNodeId, Map<String, String> headers) {
        String headerValue = TransportUtils.buildPendingBatchCountsHeader(engine.getParameterService(),
                engine.getOutgoingBatchService(), targetNodeId);
        if (headerValue != null) {
            headers.put(WebConstants.BATCH_TO_SEND_COUNT, headerValue);
        }
    }

    protected void logDataReceivedFromPull(Node local, List<OutgoingBatch> batchList) {
        int[] counts = TransportUtils.countLoadedBatches(batchList);
        int dataCount = counts[0], batchesCount = counts[1];
        if (batchesCount > 0) {
            log.info("{} data and {} batches sent during internal pull request from {}", dataCount, batchesCount, local);
        }
    }

    public IIncomingTransport getPingTransport(Node remote, Node local, String registrationUrl) throws IOException {
        ISymmetricEngine targetEngine = getTargetEngine(remote.getSyncUrl());
        if (targetEngine != null && targetEngine.getNodeService().findIdentityNodeId() != null) {
            ByteArrayInputStream responseStream = new ByteArrayInputStream("pong".getBytes(StandardCharsets.UTF_8));
            return new InternalIncomingTransport(responseStream);
        }
        return null;
    }

    public IOutgoingWithResponseTransport getPushTransport(final Node targetNode, final Node sourceNode,
            String securityToken, String registrationUrl) throws IOException {
        return getPushTransport(targetNode, sourceNode, securityToken, null, registrationUrl);
    }

    @Override
    public IOutgoingWithResponseTransport getPushTransport(final Node remote, final Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        ISymmetricEngine targetEngine = getTargetEngine(remote.getSyncUrl());
        NodeChannels remoteNodeChannels = null;
        if (targetEngine != null) {
            remoteNodeChannels = targetEngine.getConfigurationService().getSuspendIgnoreChannelLists(local.getNodeId());
        }
        final PipedOutputStream pushOs = new PipedOutputStream();
        final PipedInputStream pushIs = new PipedInputStream(pushOs);
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(remote.getSyncUrl(), pushIs, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os)
                    throws Exception {
                // This should be basically what the push servlet does ...
                log.debug("Internal push requested from node {}", local.getNodeId());
                handlePush(engine, local, requestProperties != null ? requestProperties.get(WebConstants.CHANNEL_QUEUE) : null, is, os);
                log.debug("Internal push completed for {}", local.getNodeId());
            }
        });
        return new InternalOutgoingWithResponseTransport(pushOs, respIs, remoteNodeChannels);
    }

    protected void handlePush(ISymmetricEngine engine, Node local, String channelQueue, InputStream is, OutputStream os) throws IOException {
        long ts = System.currentTimeMillis();
        try {
            engine.getDataLoaderService().loadDataFromPush(local, channelQueue, is, os);
        } finally {
            IStatisticManager statisticManager = engine.getStatisticManager();
            statisticManager.incrementNodesPushed(1);
            statisticManager.incrementTotalNodesPushedTime(System.currentTimeMillis() - ts);
        }
    }

    public IOutgoingWithResponseTransport getFilePushTransport(final Node targetNode, final Node sourceNode,
            String securityToken, String registrationUrl) throws IOException {
        final PipedOutputStream pushOs = new PipedOutputStream();
        final PipedInputStream pushIs = new PipedInputStream(pushOs);
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(targetNode.getSyncUrl(), pushIs, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os) {
                // This should be basically what the push servlet does ...
                log.debug("Internal file sync push request received from {}", sourceNode.getNodeId());
                engine.getFileSyncService().loadFilesFromPush(sourceNode.getNodeId(), is, os);
            }
        });
        return new InternalOutgoingWithResponseTransport(pushOs, respIs);
    }

    public IIncomingTransport getRegisterTransport(final Node client, String registrationUrl) throws IOException {
        return getRegisterTransport(client, registrationUrl, null);
    }

    public IIncomingTransport getRegisterTransport(final Node client, String registrationUrl, Map<String, String> requestProperties)
            throws IOException {
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        boolean pushRegistration = requestProperties != null
                && Boolean.TRUE.toString().equals(requestProperties.get(WebConstants.PUSH_REGISTRATION));
        runAtClient(registrationUrl, null, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os)
                    throws Exception {
                if (pushRegistration) {
                    checkIfRegistrationIsOpen(engine, client);
                    log.info("Internally writing registration properties for push registration request");
                    if (!engine.getRegistrationService().writeRegistrationProperties(os)) {
                        log.error("Failed to write registration properties internally.");
                    }
                } else if (!registerNode(engine, client, requestProperties, os)) {
                    log.error("{} was not allowed to register internally with {}.", client, engine.getNodeService().findIdentityNodeId());
                }
            }
        });
        return new InternalIncomingTransport(respIs);
    }

    public IOutgoingWithResponseTransport getRegisterPushTransport(Node remote, Node local) throws IOException {
        final PipedOutputStream pushOs = new PipedOutputStream();
        final PipedInputStream pushIs = new PipedInputStream(pushOs);
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(remote.getSyncUrl(), pushIs, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os) throws Exception {
                checkIfRegistrationIsOpen(engine, local);
                log.info("Internally loading push registration batch from {}", local);
                if (!engine.getRegistrationService().loadRegistrationBatch(local, is, os)) {
                    throw new RegistrationFailedException("Error during internal registration");
                }
            }
        });
        return new InternalOutgoingWithResponseTransport(pushOs, respIs);
    }

    protected void checkIfRegistrationIsOpen(ISymmetricEngine clientEngine, Node server) {
        IParameterService clientParameterService = clientEngine.getParameterService();
        if (!clientParameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)) {
            throw new RegistrationNotOpenException("Internal registration not allowed over push");
        }
        IRegistrationService clientRegistrationService = clientEngine.getRegistrationService();
        if (clientRegistrationService.isRegisteredWithServer() && !clientRegistrationService.isRegistrationOpen()) {
            throw new RegistrationNotOpenException("Internal registration not open");
        }
        if (!Strings.CS.equals(server.getSyncUrl(), clientParameterService.getRegistrationUrl())) {
            throw new RegistrationNotOpenException(String.format("Not allowed to internally register with %s", server.getSyncUrl()));
        }
    }

    protected boolean registerNode(ISymmetricEngine engine, Node nodeToRegister, Map<String, String> requestProperties,
            OutputStream os) throws IOException {
        String userId = null, password = null;
        if (requestProperties != null) {
            userId = requestProperties.get(WebConstants.REG_USER_ID);
            password = requestProperties.get(WebConstants.REG_PASSWORD);
        }
        return engine.getRegistrationService().registerNode(nodeToRegister, AppUtils.getHostName(),
                AppUtils.getIpAddress(), os, userId, password, false);
    }

    @Override
    public int sendCopyRequest(Node local) throws IOException {
        try {
            String registrationUrl = symmetricEngine.getParameterService().getRegistrationUrl();
            ISymmetricEngine targetEngine = getTargetEngine(registrationUrl);
            String copyFromNodeId = local.getNodeId();
            String newExternalId = symmetricEngine.getParameterService().getExternalId();
            String newGroupId = symmetricEngine.getParameterService().getNodeGroupId();
            String identityNodeId = targetEngine.getNodeService().findIdentityNodeId();
            String newNodeId = targetEngine.getRegistrationService().openRegistration(newGroupId, newExternalId);
            log.info("Internal copy request. New external_id={}, new node_group_id={}, old node_id={}, new node_id={}",
                    newExternalId, newGroupId, copyFromNodeId, newNodeId);
            Map<String, BatchId> batchIds = symmetricEngine.getIncomingBatchService().findMaxBatchIdsByChannel();
            Set<String> channelIds = targetEngine.getConfigurationService().getChannels(false).keySet();
            for (String channelId : channelIds) {
                if (!Constants.CHANNEL_CONFIG.equals(channelId) && !Constants.CHANNEL_HEARTBEAT.equals(channelId)
                        && !Constants.CHANNEL_SYSTEM.equals(channelId)) {
                    BatchId batchId = batchIds.get(channelId);
                    if (batchId != null && identityNodeId.equals(batchId.getNodeId())) {
                        targetEngine.getOutgoingBatchService().copyOutgoingBatches(channelId, batchId.getBatchId(), copyFromNodeId, newNodeId);
                    }
                }
            }
            return WebConstants.SC_OK;
        } catch (Exception ex) {
            log.error("Error during internal copy request", ex);
            return -1;
        }
    }

    @Override
    public int sendStatusRequest(Node local, Map<String, String> statuses) throws IOException {
        try {
            String registrationUrl = symmetricEngine.getParameterService().getRegistrationUrl();
            ISymmetricEngine targetEngine = getTargetEngine(registrationUrl);
            String nodeId = local.getNodeId();
            String batchToSendCountParam = statuses.get(WebConstants.BATCH_TO_SEND_COUNT);
            log.debug("Internal push stats for nodeId: {} batchToSendCountParam: '{}'", nodeId, batchToSendCountParam);
            if (StringUtils.isNotBlank(batchToSendCountParam)) {
                Map<String, Integer> queuesToBatchCounts = targetEngine.getNodeCommunicationService()
                        .parseQueueToBatchCounts(batchToSendCountParam);
                log.debug("Internal push stats for nodeId: {} queuesToBatchCounts: '{}'", nodeId, queuesToBatchCounts);
                targetEngine.getNodeCommunicationService().updateBatchToSendCounts(nodeId, queuesToBatchCounts);
            }
            return WebConstants.SC_OK;
        } catch (Exception ex) {
            log.error("Error during internal status request", ex);
            return -1;
        }
    }

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local,
            String securityToken, String registrationUrl) throws IOException {
        return sendAcknowledgement(remote, list, local, securityToken, null, registrationUrl);
    }

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local,
            String securityToken, Map<String, String> requestProperties, String registrationUrl) throws IOException {
        try {
            if (list != null && list.size() > 0) {
                ISymmetricEngine remoteEngine = getTargetEngine(remote.getSyncUrl());
                for (String ackData : getAcknowledgementData(remote.requires13Compatiblity(), local.getNodeId(), list, -1, -1)) {
                    List<BatchAck> batches = readAcknowledgement(ackData);
                    Collections.sort(batches, new Comparator<BatchAck>() {
                        public int compare(BatchAck batchInfo1, BatchAck batchInfo2) {
                            Long batchId1 = batchInfo1.getBatchId();
                            Long batchId2 = batchInfo2.getBatchId();
                            return batchId1.compareTo(batchId2);
                        }
                    });
                    for (BatchAck batchInfo : batches) {
                        remoteEngine.getAcknowledgeService().ack(batchInfo);
                    }
                }
            }
            return WebConstants.SC_OK;
        } catch (Exception ex) {
            log.error("", ex);
            return -1;
        }
    }

    public void writeAcknowledgement(OutputStream out, Node remote, List<IncomingBatch> list,
            Node local, String securityToken) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        for (String ackData : getAcknowledgementData(remote.requires13Compatiblity(), local.getNodeId(), list, -1, -1)) {
            pw.println(ackData);
        }
        pw.close();
    }

    public void runAtClient(final String url, final InputStream is, final OutputStream os,
            final IClientRunnable runnable) {
        new Thread() {
            public void run() {
                try {
                    ISymmetricEngine engine = getTargetEngine(url);
                    runnable.run(engine, is, os);
                } catch (ServiceNotReadyException e) {
                    log.debug("Service not ready: {}", e.getMessage());
                } catch (Exception e) {
                    log.error("", e);
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException e) {
                    }
                    try {
                        if (os != null) {
                            os.close();
                        }
                    } catch (IOException e) {
                    }
                }
            }
        }.start();
    }

    protected ISymmetricEngine getTargetEngine(String url) {
        ISymmetricEngine engine = AbstractSymmetricEngine.findEngineByUrl(url);
        if (engine == null) {
            throw new NullPointerException(
                    "Could not find the engine reference for the following url: " + url);
        } else {
            return engine;
        }
    }

    public interface IClientRunnable {
        public void run(ISymmetricEngine engine, InputStream is, OutputStream os) throws Exception;
    }

    @Override
    public IIncomingTransport getConfigTransport(Node remote, Node local, String securityToken,
            String symmetricVersion, String configVersion, String registrationUrl) throws IOException {
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        final Node remoteNode = new Node();
        remoteNode.setNodeId(local.getNodeId());
        remoteNode.setSymmetricVersion(symmetricVersion);
        remoteNode.setConfigVersion(configVersion);
        runAtClient(remote.getSyncUrl(), null, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os) throws Exception {
                log.info("Internal configuration request from node ID {} {{symmetricVersion={}, configVersion={}}}",
                        remoteNode.getNodeId(), symmetricVersion, configVersion);
                if (StringUtils.isBlank(configVersion) || Version.isOlderMinorVersion(configVersion, Version.version())) {
                    log.info("Internally sending configuration to node ID {}", remoteNode.getNodeId());
                    engine.getDataExtractorService().extractConfigurationStandalone(remoteNode, TransportUtils.toWriter(os),
                            TableConstants.getConfigTablesExcludedFromExport());
                }
            }
        });
        return new InternalIncomingTransport(respIs);
    }

    @Override
    public IIncomingTransport getBandwidthPullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl, long sampleSize) throws IOException {
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        final long finalSampleSize = sampleSize > 0 ? sampleSize : 1000;
        runAtClient(remote.getSyncUrl(), null, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os) throws Exception {
                IParameterService parameterService = engine.getParameterService();
                long testSlowBandwidthDelay = parameterService != null ? parameterService.getLong("test.slow.bandwidth.delay") : 0;
                for (int i = 0; i < finalSampleSize; i++) {
                    os.write(1);
                    if (testSlowBandwidthDelay > 0) {
                        AppUtils.sleep(testSlowBandwidthDelay);
                    }
                }
            }
        });
        return new InternalIncomingTransport(respIs);
    }

    @Override
    public IOutgoingWithResponseTransport getBandwidthPushTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl)
            throws IOException {
        final PipedOutputStream pushOs = new PipedOutputStream();
        final PipedInputStream pushIs = new PipedInputStream(pushOs);
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(remote.getSyncUrl(), pushIs, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os) throws Exception {
                BandwidthTestResults bwtr = new BandwidthTestResults();
                bwtr.start();
                byte[] b = new byte[1024];
                int count;
                while ((count = is.read(b, 0, b.length)) != -1) {
                    bwtr.transmitted(count);
                }
                bwtr.stop();
                Gson gson = new Gson();
                os.write(gson.toJson(bwtr).getBytes(Charset.defaultCharset()));
            }
        });
        return new InternalOutgoingWithResponseTransport(pushOs, respIs);
    }

    @Override
    public IIncomingTransport getComparePullTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException {
        return null;
    }

    @Override
    public IOutgoingWithResponseTransport getComparePushTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException {
        return null;
    }
}
