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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.junit.jupiter.api.Test;

class FileSyncServiceTest {
    @Test
    void sendFiles_failureDuringSendPhase_incrementsDataSentErrorsOnly() throws Exception {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        FileSyncService service = spy(buildFileSyncService(statisticManager, outgoingBatchService));
        OutgoingBatch batch = new OutgoingBatch("target1", "testchannel", Status.NE);
        batch.setBatchId(1);
        List<OutgoingBatch> batchesToProcess = new ArrayList<OutgoingBatch>();
        batchesToProcess.add(batch);
        doReturn(batchesToProcess).when(service).getBatchesToProcess(any(Node.class));
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(stagedResource.exists()).thenReturn(true);
        when(stagedResource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        doReturn(stagedResource).when(service).getStagedResource(any(OutgoingBatch.class));
        OutputStream failingOutputStream = mock(OutputStream.class);
        doThrow(new IOException("simulated network failure")).when(failingOutputStream).flush();
        IOutgoingTransport outgoingTransport = mock(IOutgoingTransport.class);
        when(outgoingTransport.openStream()).thenReturn(failingOutputStream);
        Node targetNode = new Node();
        targetNode.setNodeId("target1");
        ProcessInfo processInfo = new ProcessInfo(new ProcessInfoKey("source1", "target1", null));
        try {
            service.sendFiles(processInfo, targetNode, outgoingTransport);
        } catch (RuntimeException expected) {
            // sendFiles rethrows after recording statistics
        }
        verify(statisticManager).incrementDataSentErrors("testchannel", 1);
        verify(statisticManager, never()).incrementDataExtractedErrors(any(), anyLong());
    }

    @Test
    void sendFiles_failureDuringExtractPhase_incrementsDataExtractedErrorsOnly() throws Exception {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        FileSyncService service = spy(buildFileSyncService(statisticManager, outgoingBatchService));
        OutgoingBatch batch = new OutgoingBatch("target1", "testchannel", Status.NE);
        batch.setBatchId(1);
        List<OutgoingBatch> batchesToProcess = new ArrayList<OutgoingBatch>();
        batchesToProcess.add(batch);
        doReturn(batchesToProcess).when(service).getBatchesToProcess(any(Node.class));
        doThrow(new RuntimeException("simulated extract failure")).when(service).getStagedResource(any(OutgoingBatch.class));
        Node targetNode = new Node();
        targetNode.setNodeId("target1");
        ProcessInfo processInfo = new ProcessInfo(new ProcessInfoKey("source1", "target1", null));
        IOutgoingTransport outgoingTransport = mock(IOutgoingTransport.class);
        try {
            service.sendFiles(processInfo, targetNode, outgoingTransport);
        } catch (RuntimeException expected) {
            // sendFiles rethrows after recording statistics
        }
        verify(statisticManager).incrementDataExtractedErrors("testchannel", 1);
        verify(statisticManager, never()).incrementDataSentErrors(any(), anyLong());
    }

    private FileSyncService buildFileSyncService(IStatisticManager statisticManager, IOutgoingBatchService outgoingBatchService) {
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
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getConfigurationService()).thenReturn(mock(IConfigurationService.class));
        return new FileSyncService(engine);
    }
}
