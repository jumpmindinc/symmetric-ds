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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeCommunicationService.INodeCommunicationExecutor;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IPullService;
import org.jumpmind.symmetric.service.IRegistrationService;

/**
 * @see IPullService
 */
public class PullService extends AbstractOfflineDetectorService implements IPullService, INodeCommunicationExecutor {
    private INodeService nodeService;
    private IRegistrationService registrationService;
    private IClusterService clusterService;
    private INodeCommunicationService nodeCommunicationService;
    private IDataLoaderService dataLoaderService;
    private IIncomingBatchService incomingBatchService;
    private IConfigurationService configurationService;

    public PullService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect(), engine.getExtensionService());
        nodeService = engine.getNodeService();
        registrationService = engine.getRegistrationService();
        clusterService = engine.getClusterService();
        nodeCommunicationService = engine.getNodeCommunicationService();
        dataLoaderService = engine.getDataLoaderService();
        incomingBatchService = engine.getIncomingBatchService();
        configurationService = engine.getConfigurationService();
    }

    synchronized public RemoteNodeStatuses pullData(boolean force) {
        final RemoteNodeStatuses statuses = new RemoteNodeStatuses(configurationService.getChannels(false));
        Node identity = nodeService.findIdentity();
        if (identity == null || identity.isSyncEnabled()) {
            long minimumPeriodMs = parameterService.getLong(ParameterConstants.PULL_MINIMUM_PERIOD_MS, -1);
            if (force || !clusterService.isInfiniteLocked(ClusterConstants.PULL)) {
                // register if we haven't already been registered
                // Only pull if we did not have to register this time
                if (!registrationService.registerWithServer()) {
                    identity = nodeService.findIdentity();
                    if (identity != null) {
                        List<NodeCommunication> nodes = nodeCommunicationService.list(CommunicationType.PULL);
                        int availableThreads = nodeCommunicationService.getAvailableThreads(CommunicationType.PULL);
                        boolean m2mLoadInProgress = configurationService.isMasterToMaster() && nodeService.isDataLoadStarted();
                        nodes = filterForReadyQueues(nodes);
                        for (NodeCommunication nodeCommunication : nodes) {
                            NodeSecurity nodeSecurity = nodeService.findNodeSecurity(nodeCommunication.getNodeId(), true);
                            boolean meetsMinimumTime = true;
                            if (minimumPeriodMs > 0 && nodeCommunication.getLastLockTime() != null &&
                                    (System.currentTimeMillis() - nodeCommunication.getLastLockTime().getTime()) < minimumPeriodMs) {
                                meetsMinimumTime = false;
                            }
                            boolean m2mLockout = false;
                            if (m2mLoadInProgress) {
                                m2mLockout = identity.getCreatedAtNodeId() != null && "registration".equals(nodeSecurity.getInitialLoadCreateBy()) &&
                                        !identity.getCreatedAtNodeId().equals(nodeCommunication.getNodeId());
                                if (m2mLockout) {
                                    log.debug("Not pulling from node {} until initial load from {} is complete", nodeCommunication.getNodeId(), identity
                                            .getCreatedAtNodeId());
                                }
                            }
                            if (availableThreads > 0 && meetsMinimumTime && !m2mLockout && nodeSecurity != null && !nodeSecurity.isRegistrationEnabled()) {
                                if (nodeCommunicationService.execute(nodeCommunication, statuses, this)) {
                                    availableThreads--;
                                }
                            }
                            nodeCommunication.setBatchToSendCount(0);
                        }
                    }
                } else {
                    identity = nodeService.findIdentity();
                    log.info("Node {}:{} just registered, not pulling yet", identity != null ? identity.getNodeGroupId() : "", identity != null ? identity
                            .getNodeId() : "");
                }
            } else {
                log.debug("Did not run the pull process because it has been stopped");
            }
        }
        return statuses;
    }

    protected List<NodeCommunication> filterForReadyQueues(List<NodeCommunication> nodes) {
        List<NodeCommunication> filteredNodes = nodes;
        if (parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES) && configurationService.getQueues(false).size() > 1 &&
                !parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)) {
            filteredNodes = new ArrayList<NodeCommunication>();
            for (NodeCommunication nodeCommunication : nodes) {
                Collection<String> readyQueues = incomingBatchService.getReadyQueues(nodeCommunication.getNodeId());
                if (nodeCommunication.getQueue().equals(Constants.QUEUE_DEFAULT) || readyQueues.contains(nodeCommunication.getQueue())) {
                    filteredNodes.add(nodeCommunication);
                }
            }
        }
        return filteredNodes;
    }

    public void execute(NodeCommunication nodeCommunication, RemoteNodeStatus status) {
        Node node = nodeCommunication.getNode();
        boolean immediatePullIfDataFound = parameterService.is(ParameterConstants.PULL_IMMEDIATE_IF_DATA_FOUND, false);
        if (StringUtils.isNotBlank(node.getSyncUrl()) || !parameterService.isRegistrationServer()) {
            long lastBatchesProcessed = 0;
            long lastDataProcessed = 0;
            long cumulativeBatchesProcessed = 0;
            long cumulativeDataProcessed = 0;
            do {
                log.debug("Pull requested for {}", node);
                if (lastBatchesProcessed > 0) {
                    if (nodeService.isDataLoadStarted()) {
                        log.info("Immediate pull requested while in reload mode");
                    } else {
                        log.debug("Immediate pull requested while data found");
                    }
                }
                try {
                    dataLoaderService.loadDataFromPull(node, status);
                    fireOnline(node, status);
                } catch (Exception ex) {
                    fireOffline(ex, node, status);
                }
                lastBatchesProcessed = status.getBatchesProcessed() - cumulativeBatchesProcessed;
                lastDataProcessed = status.getDataProcessed() - cumulativeDataProcessed;
                if (!status.failed() && (lastDataProcessed > 0 || lastBatchesProcessed > 0)) {
                    log.info(
                            "Pull data received from {} on queue {}. {} rows and {} batches were processed. ({})",
                            new Object[] { node.toString(), nodeCommunication.getQueue(), lastDataProcessed, lastBatchesProcessed, status.getTableSummary() });
                } else if (status.failed()) {
                    log.debug(
                            "There was a failure while pulling data from {} on queue {}. {} rows and {} batches were processed. ({})",
                            new Object[] { node.toString(), nodeCommunication.getQueue(),
                                    lastDataProcessed, lastBatchesProcessed, status.getTableSummary() });
                }
                /*
                 * Re-pull immediately if we are in the middle of an initial load so that the initial load completes as quickly as possible.
                 */
                cumulativeDataProcessed = status.getDataProcessed();
                cumulativeBatchesProcessed = status.getBatchesProcessed();
                status.resetTableSummary();
                if (Thread.interrupted()) {
                    throw new SymmetricException("Thread was interrupted");
                }
            } while ((immediatePullIfDataFound || nodeService.isDataLoadStarted()) && !status.failed()
                    && lastBatchesProcessed > 0);
        } else {
            log.warn("Cannot pull node '{}' in the group '{}'.  The sync url is blank",
                    node.getNodeId(), node.getNodeGroupId());
        }
    }

    public RemoteNodeStatus pullConfigData(boolean force) {
        Node local = nodeService.findIdentity();
        RemoteNodeStatus status = null;
        if (parameterService.is(ParameterConstants.AUTO_SYNC_CONFIG_AFTER_UPGRADE, true) &&
                !parameterService.isRegistrationServer() && local != null && (force || !Version.version().equals(local.getConfigVersion()))) {
            Node remote = new Node();
            remote.setSyncUrl(parameterService.getRegistrationUrl());
            status = new RemoteNodeStatus(remote.getNodeId(), Constants.CHANNEL_CONFIG, configurationService.getChannels(false));
            try {
                dataLoaderService.loadDataFromConfig(remote, status, force);
            } catch (Exception e) {
                fireOffline(e, remote, status);
            }
        }
        return status;
    }
}
