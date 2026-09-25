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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.OfflinePullService.FileIncomingFilter;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.file.FileIncomingTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfflinePullServiceTest {
    private INodeService nodeService;
    private IClusterService clusterService;
    private INodeCommunicationService nodeCommunicationService;
    private IDataLoaderService dataLoaderService;
    private ITransportManager transportManager;
    private OfflinePullService offlinePullService;

    @BeforeEach
    void setUp() {
        nodeService = mock(INodeService.class);
        clusterService = mock(IClusterService.class);
        nodeCommunicationService = mock(INodeCommunicationService.class);
        dataLoaderService = mock(IDataLoaderService.class);
        transportManager = mock(ITransportManager.class);
        IParameterService parameterService = mock(IParameterService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        offlinePullService = new OfflinePullService(parameterService, newSymmetricDialect(), nodeService, dataLoaderService, clusterService,
                nodeCommunicationService, configurationService, extensionService, transportManager);
    }

    @Test
    void testPullData_withoutAnIdentityDoesNothing() {
        assertTrue(offlinePullService.pullData(false).isEmpty());
        verify(nodeCommunicationService, never()).list(CommunicationType.OFFLN_PULL);
    }

    @Test
    void testPullData_withSyncDisabledOnTheIdentityDoesNothing() {
        Node identity = new Node("store-001", "store");
        identity.setSyncEnabled(false);
        when(nodeService.findIdentity()).thenReturn(identity);
        offlinePullService.pullData(false);
        verify(nodeCommunicationService, never()).list(CommunicationType.OFFLN_PULL);
    }

    @Test
    void testPullData_whileTheJobIsStoppedDoesNothing() {
        stubSyncEnabledIdentity();
        when(clusterService.isInfiniteLocked(ClusterConstants.OFFLINE_PULL)).thenReturn(true);
        offlinePullService.pullData(false);
        verify(nodeCommunicationService, never()).list(CommunicationType.OFFLN_PULL);
    }

    @Test
    void testPullData_whenForcedIgnoresTheStoppedJob() {
        stubSyncEnabledIdentity();
        when(clusterService.isInfiniteLocked(ClusterConstants.OFFLINE_PULL)).thenReturn(true);
        stubNodesToPull(1, newNodeCommunication("store-002"));
        offlinePullService.pullData(true);
        verify(nodeCommunicationService).execute(any(NodeCommunication.class), any(), eq(offlinePullService));
    }

    @Test
    void testPullData_executesOneNodePerAvailableThread() {
        stubSyncEnabledIdentity();
        stubNodesToPull(1, newNodeCommunication("store-002"), newNodeCommunication("store-003"));
        when(nodeCommunicationService.execute(any(NodeCommunication.class), any(), eq(offlinePullService))).thenReturn(true);
        offlinePullService.pullData(false);
        verify(nodeCommunicationService, times(1)).execute(any(NodeCommunication.class), any(), eq(offlinePullService));
    }

    @Test
    void testPullData_withoutAnyAvailableThreadsExecutesNothing() {
        stubSyncEnabledIdentity();
        stubNodesToPull(0, newNodeCommunication("store-002"));
        offlinePullService.pullData(false);
        verify(nodeCommunicationService, never()).execute(any(NodeCommunication.class), any(), eq(offlinePullService));
    }

    @Test
    void testExecute_loadsAndCompletesTheTransport() throws IOException {
        FileIncomingTransport transport = mock(FileIncomingTransport.class);
        Node remote = new Node("store-002", "store");
        stubSyncEnabledIdentity();
        when(transportManager.getPullTransport(eq(remote), any(Node.class), eq(null), eq(null), eq(null))).thenReturn(transport);
        RemoteNodeStatus status = new RemoteNodeStatus("store-002", "default", null);
        offlinePullService.execute(newNodeCommunicationFor(remote), status);
        verify(dataLoaderService).loadDataFromOfflineTransport(remote, status, transport);
        verify(transport).complete(true);
    }

    @Test
    void testExecute_swallowsAnIoException() throws IOException {
        Node remote = new Node("store-002", "store");
        stubSyncEnabledIdentity();
        when(transportManager.getPullTransport(eq(remote), any(Node.class), eq(null), eq(null), eq(null))).thenThrow(new IOException("no such dir"));
        offlinePullService.execute(newNodeCommunicationFor(remote), new RemoteNodeStatus("store-002", "default", null));
        verify(dataLoaderService, never()).loadDataFromOfflineTransport(any(Node.class), any(RemoteNodeStatus.class), any());
    }

    @Test
    void testFileIncomingFilter_acceptsTheConfiguredExtension() {
        FileIncomingFilter filter = new FileIncomingFilter("csv");
        assertTrue(filter.accept(new File("/tmp"), "batch-1.csv"));
    }

    @Test
    void testFileIncomingFilter_rejectsOtherExtensions() {
        FileIncomingFilter filter = new FileIncomingFilter("csv");
        assertFalse(filter.accept(new File("/tmp"), "batch-1.zip"));
        assertFalse(filter.accept(new File("/tmp"), "csv"));
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

    private void stubSyncEnabledIdentity() {
        Node identity = new Node("store-001", "store");
        identity.setSyncEnabled(true);
        when(nodeService.findIdentity()).thenReturn(identity);
    }

    private void stubNodesToPull(int availableThreads, NodeCommunication... nodes) {
        when(nodeCommunicationService.list(CommunicationType.OFFLN_PULL)).thenReturn(Arrays.asList(nodes));
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.OFFLN_PULL)).thenReturn(availableThreads);
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
