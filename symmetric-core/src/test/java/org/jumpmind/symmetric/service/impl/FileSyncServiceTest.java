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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.OutgoingBatches;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FileSyncServiceTest {
    private static final String SOURCE_NODE_ID = "source1";
    private static final String TARGET_NODE_ID = "target1";
    private IStatisticManager statisticManager;
    private IConfigurationService configurationService;
    private IDataExtractorService dataExtractorService;
    private IOutgoingTransport outgoingTransport;
    private Node targetNode;
    private FileSyncService service;

    @BeforeEach
    void setUp() {
        statisticManager = mock(IStatisticManager.class);
        configurationService = mock(IConfigurationService.class);
        dataExtractorService = mock(IDataExtractorService.class);
        outgoingTransport = mock(IOutgoingTransport.class);
        targetNode = new Node();
        targetNode.setNodeId(TARGET_NODE_ID);
        service = spy(new FileSyncService(mockEngine()));
    }

    @Test
    void sendFiles_failureDuringSendPhase_incrementsDataSentErrorsOnly() throws Exception {
        doReturn(new ArrayList<>(List.of(newFileSyncBatch(1)))).when(service).getBatchesToProcess(any(Node.class), any(IOutgoingTransport.class));
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(stagedResource.exists()).thenReturn(true);
        when(stagedResource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        doReturn(stagedResource).when(service).getStagedResource(any(OutgoingBatch.class));
        OutputStream failingOutputStream = mock(OutputStream.class);
        doThrow(new IOException("simulated network failure")).when(failingOutputStream).flush();
        when(outgoingTransport.openStream()).thenReturn(failingOutputStream);
        try {
            service.sendFiles(newProcessInfo(), targetNode, outgoingTransport);
        } catch (RuntimeException expected) {
            // sendFiles rethrows after recording statistics
        }
        verify(statisticManager).incrementDataSentErrors(Constants.CHANNEL_FILESYNC, 1);
        verify(statisticManager, never()).incrementDataExtractedErrors(any(), anyLong());
    }

    @Test
    void sendFiles_failureDuringExtractPhase_incrementsDataExtractedErrorsOnly() {
        doReturn(new ArrayList<>(List.of(newFileSyncBatch(1)))).when(service).getBatchesToProcess(any(Node.class), any(IOutgoingTransport.class));
        doThrow(new RuntimeException("simulated extract failure")).when(service).getStagedResource(any(OutgoingBatch.class));
        try {
            service.sendFiles(newProcessInfo(), targetNode, outgoingTransport);
        } catch (RuntimeException expected) {
            // sendFiles rethrows after recording statistics
        }
        verify(statisticManager).incrementDataExtractedErrors(Constants.CHANNEL_FILESYNC, 1);
        verify(statisticManager, never()).incrementDataSentErrors(any(), anyLong());
    }

    @Test
    void sendFiles_allBatchesSuspended_opensNoStream() {
        doReturn(new ArrayList<>(List.of(newFileSyncBatch(1)))).when(service).getPendingFileSyncBatches(targetNode);
        doAnswer(invocation -> removeAllBatches(invocation.getArgument(0))).when(dataExtractorService)
                .filterSuspendedAndIgnoredBatches(any(OutgoingBatches.class), any(NodeChannels.class));
        List<OutgoingBatch> sentBatches = service.sendFiles(newProcessInfo(), targetNode, outgoingTransport);
        assertTrue(sentBatches.isEmpty());
        verify(outgoingTransport, never()).openStream();
    }

    @Test
    void getBatchesToProcess_noBatchIsSuspendedOrIgnored_returnsPendingBatchesAfterLocalAndTransportFilters() {
        OutgoingBatch batch = newFileSyncBatch(1);
        doReturn(new ArrayList<>(List.of(batch))).when(service).getPendingFileSyncBatches(targetNode);
        NodeChannels localSuspendIgnoreChannels = new NodeChannels();
        NodeChannels transportSuspendIgnoreChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localSuspendIgnoreChannels);
        when(outgoingTransport.getSuspendIgnoreChannelLists(configurationService, null, targetNode)).thenReturn(transportSuspendIgnoreChannels);
        List<OutgoingBatch> batchesToProcess = service.getBatchesToProcess(targetNode, outgoingTransport);
        assertEquals(List.of(batch), batchesToProcess);
        InOrder filterOrder = inOrder(dataExtractorService);
        filterOrder.verify(dataExtractorService).filterSuspendedAndIgnoredBatches(any(OutgoingBatches.class), same(localSuspendIgnoreChannels));
        filterOrder.verify(dataExtractorService).filterSuspendedAndIgnoredBatches(any(OutgoingBatches.class), same(transportSuspendIgnoreChannels));
    }

    @Test
    void getBatchesToProcess_localListRemovesEveryBatch_skipsTransportReservation() {
        doReturn(new ArrayList<>(List.of(newFileSyncBatch(1)))).when(service).getPendingFileSyncBatches(targetNode);
        doAnswer(invocation -> removeAllBatches(invocation.getArgument(0))).when(dataExtractorService)
                .filterSuspendedAndIgnoredBatches(any(OutgoingBatches.class), any(NodeChannels.class));
        List<OutgoingBatch> batchesToProcess = service.getBatchesToProcess(targetNode, outgoingTransport);
        assertTrue(batchesToProcess.isEmpty());
        verify(outgoingTransport, never()).getSuspendIgnoreChannelLists(any(), any(), any());
    }

    @Test
    void getBatchesToProcess_noPendingBatches_skipsTransportReservation() {
        doReturn(new ArrayList<>()).when(service).getPendingFileSyncBatches(targetNode);
        List<OutgoingBatch> batchesToProcess = service.getBatchesToProcess(targetNode, outgoingTransport);
        assertTrue(batchesToProcess.isEmpty());
        verify(outgoingTransport, never()).getSuspendIgnoreChannelLists(any(), any(), any());
    }

    @Test
    void getBatchesToProcess_transportListSuspendsBatch_returnsNoBatches() {
        doReturn(new ArrayList<>(List.of(newFileSyncBatch(1)))).when(service).getPendingFileSyncBatches(targetNode);
        NodeChannels remoteSuspendIgnoreChannels = new NodeChannels();
        when(outgoingTransport.getSuspendIgnoreChannelLists(configurationService, null, targetNode)).thenReturn(remoteSuspendIgnoreChannels);
        doAnswer(invocation -> removeAllBatches(invocation.getArgument(0))).when(dataExtractorService)
                .filterSuspendedAndIgnoredBatches(any(OutgoingBatches.class), same(remoteSuspendIgnoreChannels));
        List<OutgoingBatch> batchesToProcess = service.getBatchesToProcess(targetNode, outgoingTransport);
        assertTrue(batchesToProcess.isEmpty());
        verify(outgoingTransport).getSuspendIgnoreChannelLists(same(configurationService), isNull(), same(targetNode));
    }

    @Test
    void buildSuspendIgnoreRequestProperties_filesyncSuspendedAndReloadIgnored_sendsBothHeaders() {
        Node remoteNode = new Node();
        remoteNode.setNodeId(SOURCE_NODE_ID);
        NodeChannels suspendIgnoreChannels = new NodeChannels();
        suspendIgnoreChannels.addSuspendChannels(TARGET_NODE_ID, Constants.CHANNEL_FILESYNC);
        suspendIgnoreChannels.addIgnoreChannels(TARGET_NODE_ID, Constants.CHANNEL_FILESYNC_RELOAD);
        when(configurationService.getSuspendIgnoreChannelLists(SOURCE_NODE_ID)).thenReturn(suspendIgnoreChannels);
        Map<String, String> requestProperties = service.buildSuspendIgnoreRequestProperties(remoteNode, targetNode);
        assertEquals(Constants.CHANNEL_FILESYNC, requestProperties.get(WebConstants.SUSPENDED_CHANNELS));
        assertEquals(Constants.CHANNEL_FILESYNC_RELOAD, requestProperties.get(WebConstants.IGNORED_CHANNELS));
    }

    @Test
    void buildSuspendIgnoreRequestProperties_nothingSuspendedOrIgnored_sendsEmptyHeaders() {
        Node remoteNode = new Node();
        remoteNode.setNodeId(SOURCE_NODE_ID);
        when(configurationService.getSuspendIgnoreChannelLists(SOURCE_NODE_ID)).thenReturn(new NodeChannels());
        Map<String, String> requestProperties = service.buildSuspendIgnoreRequestProperties(remoteNode, targetNode);
        assertEquals("", requestProperties.get(WebConstants.SUSPENDED_CHANNELS));
        assertEquals("", requestProperties.get(WebConstants.IGNORED_CHANNELS));
    }

    private static List<OutgoingBatch> removeAllBatches(OutgoingBatches batches) {
        List<OutgoingBatch> removed = new ArrayList<>(batches.getBatches());
        batches.getBatches().clear();
        return removed;
    }

    private static OutgoingBatch newFileSyncBatch(long batchId) {
        OutgoingBatch batch = new OutgoingBatch(TARGET_NODE_ID, Constants.CHANNEL_FILESYNC, Status.NE);
        batch.setBatchId(batchId);
        return batch;
    }

    private static ProcessInfo newProcessInfo() {
        return new ProcessInfo(new ProcessInfoKey(SOURCE_NODE_ID, TARGET_NODE_ID, null));
    }

    private ISymmetricEngine mockEngine() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(mock(IExtensionService.class));
        when(engine.getCacheManager()).thenReturn(mock(ICacheManager.class));
        when(engine.getOutgoingBatchService()).thenReturn(mock(IOutgoingBatchService.class));
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getDataExtractorService()).thenReturn(dataExtractorService);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        return engine;
    }
}
