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
package org.jumpmind.symmetric.service.impl;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeCommunicationService.INodeCommunicationExecutor;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOfflinePullService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.file.FileIncomingTransport;

public class OfflinePullService extends AbstractService implements IOfflinePullService, INodeCommunicationExecutor {
    private INodeService nodeService;
    private IClusterService clusterService;
    private INodeCommunicationService nodeCommunicationService;
    private IDataLoaderService dataLoaderService;
    private IConfigurationService configurationService;
    private ITransportManager transportManager;

    public OfflinePullService(IParameterService parameterService, ISymmetricDialect symmetricDialect,
            INodeService nodeService, IDataLoaderService dataLoaderService, IClusterService clusterService,
            INodeCommunicationService nodeCommunicationService, IConfigurationService configurationService,
            IExtensionService extensionService, ITransportManager transportManager) {
        super(parameterService, symmetricDialect);
        this.nodeService = nodeService;
        this.clusterService = clusterService;
        this.nodeCommunicationService = nodeCommunicationService;
        this.dataLoaderService = dataLoaderService;
        this.configurationService = configurationService;
        this.transportManager = transportManager;
    }

    synchronized public RemoteNodeStatuses pullData(boolean force) {
        RemoteNodeStatuses statuses = new RemoteNodeStatuses(configurationService.getChannels(false));
        Node identity = nodeService.findIdentity();
        if (identity != null && identity.isSyncEnabled()) {
            if (force || !clusterService.isInfiniteLocked(ClusterConstants.OFFLINE_PULL)) {
                List<NodeCommunication> nodes = nodeCommunicationService.list(CommunicationType.OFFLN_PULL);
                int availableThreads = nodeCommunicationService.getAvailableThreads(CommunicationType.OFFLN_PULL);
                for (NodeCommunication nodeCommunication : nodes) {
                    if (availableThreads > 0) {
                        if (nodeCommunicationService.execute(nodeCommunication, statuses, this)) {
                            availableThreads--;
                        }
                    }
                }
            } else {
                log.debug("Did not run the offline pull process because it has been stopped");
            }
        }
        return statuses;
    }

    public void execute(NodeCommunication nodeCommunication, RemoteNodeStatus status) {
        Node node = nodeCommunication.getNode();
        Node local = nodeService.findIdentity();
        try {
            long batchesProcessedCount = 0;
            do {
                batchesProcessedCount = status.getBatchesProcessed();
                log.debug("Offline pull requested for {}", node);
                FileIncomingTransport transport = (FileIncomingTransport) transportManager.getPullTransport(node, local, null, null, null);
                dataLoaderService.loadDataFromOfflineTransport(node, status, transport);
                if (!status.failed() && status.getBatchesProcessed() > batchesProcessedCount) {
                    log.info("Offline pull data read for {}.  {} rows and {} batches were processed",
                            new Object[] { node.toString(), status.getDataProcessed(), status.getBatchesProcessed() });
                } else if (status.failed()) {
                    log.info("There was a failure while reading pull data for {}.  {} rows and {} batches were processed",
                            new Object[] { node.toString(), status.getDataProcessed(), status.getBatchesProcessed() });
                }
                transport.complete(!status.failed());
            } while (!status.failed() && status.getBatchesProcessed() > batchesProcessedCount);
        } catch (IOException e) {
            log.error("An IO exception happened while attempting to read offline pull data", e);
        }
    }

    public static class FileIncomingFilter implements FilenameFilter {
        String endFilter;

        public FileIncomingFilter(String fileExtension) {
            endFilter = "." + fileExtension;
        }

        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(endFilter);
        }
    }
}
