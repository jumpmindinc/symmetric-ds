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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.file.FileOutgoingTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfflinePushServiceTest {
    private INodeService nodeService;
    private IClusterService clusterService;
    private INodeCommunicationService nodeCommunicationService;
    private IDataExtractorService dataExtractorService;
    private ITransportManager transportManager;
    private ProcessInfo processInfo;
    private OfflinePushService offlinePushService;

    @BeforeEach
    void setUp() {
        nodeService = mock(INodeService.class);
        clusterService = mock(IClusterService.class);
        nodeCommunicationService = mock(INodeCommunicationService.class);
        dataExtractorService = mock(IDataExtractorService.class);
        transportManager = mock(ITransportManager.class);
        processInfo = new ProcessInfo();
        IParameterService parameterService = mock(IParameterService.class);
        IAcknowledgeService acknowledgeService = mock(IAcknowledgeService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        offlinePushService = new OfflinePushService(parameterService, newSymmetricDialect(), dataExtractorService, acknowledgeService, transportManager,
                nodeService, clusterService, nodeCommunicationService, newStatisticManager(), configurationService, extensionService);
    }

    @Test
    void testPushData_withoutAnIdentityDoesNothing() {
        assertTrue(offlinePushService.pushData(false).isEmpty());
        verify(nodeCommunicationService, never()).list(CommunicationType.OFFLN_PUSH);
    }

    @Test
    void testPushData_withSyncDisabledOnTheIdentityDoesNothing() {
        Node identity = new Node("store-001", "store");
        identity.setSyncEnabled(false);
        when(nodeService.findIdentity()).thenReturn(identity);
        offlinePushService.pushData(false);
        verify(nodeCommunicationService, never()).list(CommunicationType.OFFLN_PUSH);
    }

    @Test
    void testPushData_whileTheJobIsStoppedDoesNothing() {
        stubSyncEnabledIdentity();
        when(clusterService.isInfiniteLocked(ClusterConstants.OFFLINE_PUSH)).thenReturn(true);
        offlinePushService.pushData(false);
        verify(nodeCommunicationService, never()).list(CommunicationType.OFFLN_PUSH);
    }

    @Test
    void testPushData_whenForcedIgnoresTheStoppedJob() {
        stubSyncEnabledIdentity();
        when(clusterService.isInfiniteLocked(ClusterConstants.OFFLINE_PUSH)).thenReturn(true);
        stubNodesToPush(1, newNodeCommunication("store-002"));
        offlinePushService.pushData(true);
        verify(nodeCommunicationService).execute(any(NodeCommunication.class), any(), eq(offlinePushService));
    }

    @Test
    void testPushData_executesOneNodePerAvailableThread() {
        stubSyncEnabledIdentity();
        stubNodesToPush(1, newNodeCommunication("store-002"), newNodeCommunication("store-003"));
        when(nodeCommunicationService.execute(any(NodeCommunication.class), any(), eq(offlinePushService))).thenReturn(true);
        offlinePushService.pushData(false);
        verify(nodeCommunicationService, times(1)).execute(any(NodeCommunication.class), any(), eq(offlinePushService));
    }

    @Test
    void testPushData_withoutAnyAvailableThreadsExecutesNothing() {
        stubSyncEnabledIdentity();
        stubNodesToPush(0, newNodeCommunication("store-002"));
        offlinePushService.pushData(false);
        verify(nodeCommunicationService, never()).execute(any(NodeCommunication.class), any(), eq(offlinePushService));
    }

    @Test
    void testExecute_marksTheProcessCompleteWhenNothingWasExtracted() throws IOException {
        FileOutgoingTransport transport = stubPushTransport();
        when(dataExtractorService.extract(eq(processInfo), any(Node.class), eq("default"), eq(transport)))
                .thenReturn(Collections.<OutgoingBatch> emptyList());
        offlinePushService.execute(newNodeCommunicationFor(new Node("store-002", "store")), new RemoteNodeStatus("store-002", "default", null));
        assertEquals(ProcessStatus.OK, processInfo.getStatus());
        verify(transport).close();
        verify(transport).complete(true);
    }

    @Test
    void testExecute_marksTheProcessInErrorWhenTheTransportCannotBeOpened() throws IOException {
        stubSyncEnabledIdentity();
        when(transportManager.getPushTransport(any(Node.class), any(Node.class), eq(null), eq(null))).thenThrow(new IOException("no such dir"));
        offlinePushService.execute(newNodeCommunicationFor(new Node("store-002", "store")), new RemoteNodeStatus("store-002", "default", null));
        assertEquals(ProcessStatus.ERROR, processInfo.getStatus());
    }

    private ISymmetricDialect newSymmetricDialect() {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        return symmetricDialect;
    }

    private IStatisticManager newStatisticManager() {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(statisticManager.newProcessInfo(any(ProcessInfoKey.class))).thenReturn(processInfo);
        return statisticManager;
    }

    private FileOutgoingTransport stubPushTransport() throws IOException {
        stubSyncEnabledIdentity();
        FileOutgoingTransport transport = mock(FileOutgoingTransport.class);
        when(transportManager.getPushTransport(any(Node.class), any(Node.class), eq(null), eq(null))).thenReturn(transport);
        return transport;
    }

    private void stubSyncEnabledIdentity() {
        Node identity = new Node("store-001", "store");
        identity.setSyncEnabled(true);
        when(nodeService.findIdentity()).thenReturn(identity);
    }

    private void stubNodesToPush(int availableThreads, NodeCommunication... nodes) {
        when(nodeCommunicationService.list(CommunicationType.OFFLN_PUSH)).thenReturn(Arrays.asList(nodes));
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.OFFLN_PUSH)).thenReturn(availableThreads);
    }

    private NodeCommunication newNodeCommunication(String nodeId) {
        NodeCommunication nodeCommunication = new NodeCommunication();
        nodeCommunication.setNodeId(nodeId);
        nodeCommunication.setQueue("default");
        return nodeCommunication;
    }

    private NodeCommunication newNodeCommunicationFor(Node node) {
        NodeCommunication nodeCommunication = newNodeCommunication(node.getNodeId());
        nodeCommunication.setNode(node);
        return nodeCommunication;
    }
}
