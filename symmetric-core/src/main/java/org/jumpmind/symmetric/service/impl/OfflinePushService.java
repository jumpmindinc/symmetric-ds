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
package org.jumpmind.symmetric.service.impl;

import java.util.List;

import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeCommunicationService.INodeCommunicationExecutor;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOfflinePushService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.file.FileOutgoingTransport;

public class OfflinePushService extends AbstractService implements IOfflinePushService, INodeCommunicationExecutor {
    private IDataExtractorService dataExtractorService;
    private IAcknowledgeService acknowledgeService;
    private ITransportManager transportManager;
    private INodeService nodeService;
    private IClusterService clusterService;
    private INodeCommunicationService nodeCommunicationService;
    private IStatisticManager statisticManager;
    private IConfigurationService configurationService;

    public OfflinePushService(IParameterService parameterService, ISymmetricDialect symmetricDialect,
            IDataExtractorService dataExtractorService, IAcknowledgeService acknowledgeService,
            ITransportManager transportManager, INodeService nodeService,
            IClusterService clusterService, INodeCommunicationService nodeCommunicationService, IStatisticManager statisticManager,
            IConfigurationService configrationService, IExtensionService extensionService) {
        super(parameterService, symmetricDialect);
        this.dataExtractorService = dataExtractorService;
        this.acknowledgeService = acknowledgeService;
        this.transportManager = transportManager;
        this.nodeService = nodeService;
        this.clusterService = clusterService;
        this.nodeCommunicationService = nodeCommunicationService;
        this.statisticManager = statisticManager;
        this.configurationService = configrationService;
    }

    synchronized public RemoteNodeStatuses pushData(boolean force) {
        RemoteNodeStatuses statuses = new RemoteNodeStatuses(configurationService.getChannels(false));
        Node identity = nodeService.findIdentity();
        if (identity != null && identity.isSyncEnabled()) {
            if (force || !clusterService.isInfiniteLocked(ClusterConstants.OFFLINE_PUSH)) {
                List<NodeCommunication> nodes = nodeCommunicationService.list(CommunicationType.OFFLN_PUSH);
                int availableThreads = nodeCommunicationService.getAvailableThreads(CommunicationType.OFFLN_PUSH);
                for (NodeCommunication nodeCommunication : nodes) {
                    if (availableThreads > 0) {
                        if (nodeCommunicationService.execute(nodeCommunication, statuses, this)) {
                            availableThreads--;
                        }
                    }
                }
            } else {
                log.debug("Did not run the offline push process because it has been stopped");
            }
        }
        return statuses;
    }

    public void execute(NodeCommunication nodeCommunication, RemoteNodeStatus status) {
        Node node = nodeCommunication.getNode();
        long batchesProcessedCount = 0;
        do {
            batchesProcessedCount = status.getBatchesProcessed();
            log.debug("Offline push requested for {}", node);
            pushToNode(node, status);
            if (!status.failed() && status.getBatchesProcessed() > batchesProcessedCount) {
                log.info("Offline push data written for {}. {} data and {} batches were processed",
                        new Object[] { node, status.getDataProcessed(), status.getBatchesProcessed() });
            } else if (status.failed()) {
                log.info("There was a failure while writing offline push data for {}. {} data and {} batches were processed",
                        new Object[] { node, status.getDataProcessed(), status.getBatchesProcessed() });
            }
            log.debug("Offline push completed for {}", node);
        } while (!status.failed() && status.getBatchesProcessed() > batchesProcessedCount);
    }

    private void pushToNode(Node remote, RemoteNodeStatus status) {
        Node identity = nodeService.findIdentity();
        FileOutgoingTransport transport = null;
        ProcessInfo processInfo = statisticManager.newProcessInfo(new ProcessInfoKey(
                identity.getNodeId(), status.getQueue(), remote.getNodeId(), ProcessType.OFFLINE_PUSH));
        List<OutgoingBatch> extractedBatches = null;
        try {
            transport = (FileOutgoingTransport) transportManager.getPushTransport(remote, identity, null, null);
            extractedBatches = dataExtractorService.extract(processInfo, remote, status.getQueue(), transport);
            if (extractedBatches.size() > 0) {
                log.info("Offline push data written for {} at {}", remote, transport.getOutgoingDir());
                List<BatchAck> batchAcks = readAcks(extractedBatches, remote.getNodeId(), transport, transportManager, acknowledgeService,
                        dataExtractorService);
                status.updateOutgoingStatus(extractedBatches, batchAcks);
            }
            if (processInfo.getStatus() != ProcessStatus.ERROR) {
                processInfo.setStatus(ProcessStatus.OK);
            }
        } catch (Exception ex) {
            processInfo.setStatus(ProcessStatus.ERROR);
            log.error("Failed to write offline file", ex);
        } finally {
            if (transport != null) {
                transport.close();
                transport.complete(processInfo.getStatus() == ProcessStatus.OK);
            }
        }
    }
}
