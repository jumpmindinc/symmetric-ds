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

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.extract.MultiBatchStagingWriter;
import org.jumpmind.symmetric.file.FileSyncZipDataWriter;
import org.jumpmind.symmetric.io.data.IDataProcessorListener;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;

public class FileSyncExtractorService extends DataExtractorService {
    private IFileSyncService fileSyncService;
    private INodeService nodeService;
    private IStagingManager stagingManager;
    private IConfigurationService configurationService;
    private INodeCommunicationService nodeCommunicationService;
    private IExtensionService extensionService;

    public FileSyncExtractorService(ISymmetricEngine engine) {
        super(engine);
        this.fileSyncService = engine.getFileSyncService();
        this.nodeService = engine.getNodeService();
        this.stagingManager = engine.getStagingManager();
        this.configurationService = engine.getConfigurationService();
        this.nodeCommunicationService = engine.getNodeCommunicationService();
        this.extensionService = engine.getExtensionService();
    }

    @Override
    protected boolean isApplicable(NodeCommunication nodeCommunication) {
        return parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)
                && nodeCommunication.getCommunicationType() == CommunicationType.FILE_XTRCT;
    }

    @Override
    protected void updateExtractRequestsForThreading() {
    }

    @Override
    protected boolean canProcessExtractRequest(ExtractRequest request, CommunicationType communicationType) {
        return request.getTableName().equalsIgnoreCase(TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_SNAPSHOT));
    }

    @Override
    protected IDataWriter wrapWithTransformWriter(Node sourceNode, Node targetNode, ProcessInfo processInfo, IDataWriter dataWriter,
            boolean useStagingDataWriter) {
        return dataWriter;
    }

    @Override
    protected IStagedResource getStagedResource(OutgoingBatch currentBatch) {
        return stagingManager.find(fileSyncService.getStagingPathComponents(currentBatch));
    }

    @Override
    protected OutgoingBatch extractOutgoingBatch(ProcessInfo processInfo, Node targetNode, IDataWriter dataWriter, OutgoingBatch currentBatch,
            boolean useStagingDataWriter, boolean updateBatchStatistics, ExtractMode mode, IDataProcessorListener listener) {
        if (!parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)) {
            return null;
        }
        Channel channel = configurationService.getChannel(currentBatch.getChannelId());
        if (channel.isFileSyncFlag()) {
            return super.extractOutgoingBatch(processInfo, targetNode, dataWriter, currentBatch, useStagingDataWriter, updateBatchStatistics, mode, null);
        } else {
            log.debug("Skipping non-file sync channel {}", channel);
            return null;
        }
    }

    @Override
    protected MultiBatchStagingWriter buildMultiBatchStagingWriter(ExtractRequest request, List<ExtractRequest> childRequests, final Node sourceNode,
            final Node targetNode,
            List<OutgoingBatch> batches, ProcessInfo processInfo, Channel channel, boolean isRestarted) {
        MultiBatchStagingWriter multiBatchStagingWriter = new MultiBatchStagingWriter(engine, request, childRequests, sourceNode.getNodeId(),
                batches, channel.getMaxBatchSize(), processInfo, isRestarted, false) {
            @Override
            protected IDataWriter buildWriter() {
                IStagedResource stagedResource = stagingManager.create(
                        fileSyncService.getStagingPathComponents(outgoingBatch));
                log.info("Extracting file sync batch {} to resource '{}'", outgoingBatch.getNodeBatchId(), stagedResource);
                long maxBytesToSync = parameterService.getLong(ParameterConstants.TRANSPORT_MAX_BYTES_TO_SYNC);
                int compressionLevel = parameterService.getInt(ParameterConstants.FILE_SYNC_COMPRESSION_LEVEL);
                FileSyncZipDataWriter fileSyncWriter = new FileSyncZipDataWriter(maxBytesToSync, compressionLevel, stagedResource, engine) {
                    @Override
                    public void close() {
                        super.finish();
                    }
                };
                return fileSyncWriter;
            }
        };
        return multiBatchStagingWriter;
    }

    @Override
    protected void queue(String nodeId, String queue, RemoteNodeStatuses statuses) {
        if (parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)) {
            final NodeCommunication.CommunicationType TYPE = NodeCommunication.CommunicationType.FILE_XTRCT;
            int availableThreads = nodeCommunicationService.getAvailableThreads(TYPE);
            NodeCommunication lock = nodeCommunicationService.find(nodeId, queue, TYPE);
            if (availableThreads > 0) {
                nodeCommunicationService.execute(lock, statuses, this);
            }
        }
    }

    @Override
    protected ProcessType getProcessType() {
        return ProcessType.FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB;
    }
}
