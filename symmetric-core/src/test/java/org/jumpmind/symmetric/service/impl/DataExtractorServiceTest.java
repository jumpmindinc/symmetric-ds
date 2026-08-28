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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.extract.SelectFromSymDataSource;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.route.AbstractFileParsingRouter;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IInitialLoadService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.impl.DataExtractorService.ExtractMode;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataExtractorServiceTest {
    private static final long LOAD_ID = 7929;
    protected ISymmetricEngine engine;
    private IParameterService parameterService;
    private ISqlTemplate sqlTemplate;
    private ISqlTemplate sqlTemplateDirty;
    private IDataService dataService;
    private INodeService nodeService;
    private TestableDataExtractorService service;
    private Node targetNode;

    static class TestableDataExtractorService extends DataExtractorService {
        AtomicInteger sendPasses = new AtomicInteger();

        TestableDataExtractorService(ISymmetricEngine engine) {
            super(engine);
        }

        @Override
        public List<ExtractRequest> getTablesForExtractByLoadId(long loadId) {
            sendPasses.incrementAndGet();
            return Collections.emptyList();
        }
    }

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        when(engine.getTablePrefix()).thenReturn("sym");
        when(engine.getNodeId()).thenReturn("source");
        TriggerRouterService triggerRouterService = mock(TriggerRouterService.class);
        when(triggerRouterService.getTriggerRoutersByTriggerHist("target", false)).thenReturn(null);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getName()).thenReturn("H2");
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.supportsParametersInSelect()).thenReturn(true);
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getSqlReplacementTokens()).thenReturn(new HashMap<String, String>());
        sqlTemplate = mock(ISqlTemplate.class);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        service = new TestableDataExtractorService(engine);
        targetNode = new Node();
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        TableReloadRequest createTableLoad = new TableReloadRequest();
        createTableLoad.setCreateTable(true);
        when(dataService.getTableReloadRequest(anyLong())).thenReturn(createTableLoad);
        when(sqlTemplateDirty.queryForLong(any(), any(), any())).thenReturn(0L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectFromSymDataSource_csvValuesAreExtracted_triggerRouterIsNotMarkedAsMissing() {
        ISqlReadCursor<Data> cursor = mock(ISqlReadCursor.class);
        TriggerHistory hist = new TriggerHistory("foo", "id", "id");
        hist.setTriggerId(AbstractFileParsingRouter.TRIGGER_ID_FILE_PARSER);
        Data data = new Data(1, "1", "1", DataEventType.INSERT, "foo", new Date(), hist, "default", null, null);
        when(cursor.next()).thenReturn(data);
        when(engine.getDataService().selectDataFor(any(), any(), eq(false))).thenReturn(cursor);
        SelectFromSymDataSource source = new SelectFromSymDataSource(engine, new OutgoingBatch(), new Node(), new Node(), new ProcessInfo(), false);
        assertTrue(source.next().equals(data));
    }

    @Test
    void checkSendDeferredForeignKeys_deferConstraintsDisabled_neverSends() {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(false);
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(0, service.sendPasses.get(), "nothing should be sent when constraint deferral is off");
    }

    @Test
    void checkSendDeferredForeignKeys_extractThreadsStillRunning_skipsWithoutConsumingClaim() {
        when(sqlTemplateDirty.queryForLong(any(), any(), any())).thenReturn(3L).thenReturn(0L);
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(0, service.sendPasses.get(), "should skip while other extract threads are incomplete");
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(1, service.sendPasses.get(),
                "a skipped call must not consume the claim; the last thread to finish still has to send");
    }

    @Test
    void checkSendDeferredForeignKeys_calledAgainForSameLoad_sendsOnlyOnce() {
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(1, service.sendPasses.get(), "second call for the same load must be a no-op");
    }

    @Test
    void checkSendDeferredForeignKeys_differentLoads_eachLoadSendsOnce() {
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        service.checkSendDeferredForeignKeys(LOAD_ID + 1, targetNode);
        assertEquals(2, service.sendPasses.get(), "the claim is per load, not global");
    }

    @Test
    void checkSendDeferredForeignKeys_concurrentExtractThreads_exactlyOneSends() throws Exception {
        int threadCount = 8;
        CyclicBarrier lineUp = new CyclicBarrier(threadCount);
        Callable<Void> race = () -> {
            lineUp.await();
            service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
            return null;
        };
        ExecutorService threads = Executors.newFixedThreadPool(threadCount);
        try {
            for (Future<Void> result : threads.invokeAll(Collections.nCopies(threadCount, race))) {
                result.get();
            }
        } finally {
            threads.shutdown();
        }
        assertEquals(1, service.sendPasses.get(),
                "all racing extract threads see zero incomplete requests, but only one may send (SYM-7929)");
    }

    @Test
    void handleLoadTerminated_releasesClaim_soANewLoadRunCanSendAgain() {
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(1, service.sendPasses.get());
        TableReloadStatus status = new TableReloadStatus();
        status.setLoadId((int) LOAD_ID);
        status.setFullLoad(true);
        status.setCompleted(true);
        service.handleLoadTerminated(mock(ISqlTransaction.class), status, "target");
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(2, service.sendPasses.get(),
                "terminating the load must release its claim so a later load with the same id could send");
    }

    @Test
    void handleLoadTerminated_partialLoad_marksPartialLoadEndedAndReleasesClaim() {
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        TableReloadStatus status = new TableReloadStatus();
        status.setLoadId((int) LOAD_ID);
        status.setFullLoad(false);
        status.setCompleted(true);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        service.handleLoadTerminated(transaction, status, "target");
        verify(nodeService).setPartialLoadEnded(transaction, "target");
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(2, service.sendPasses.get(), "a terminated partial load must release its claim too");
    }

    @Test
    void updateExtractRequestLoadTime_loadReachesTerminalState_marksInitialLoadEndedAndReleasesClaim() {
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(1, service.sendPasses.get());
        TableReloadStatus status = new TableReloadStatus();
        status.setLoadId((int) LOAD_ID);
        status.setFullLoad(true);
        status.setCompleted(true);
        when(dataService.updateTableReloadStatusDataLoaded(any(), anyLong(), any(), anyLong(), anyInt(), anyBoolean())).thenReturn(status);
        OutgoingBatch outgoingBatch = new OutgoingBatch();
        outgoingBatch.setBatchId(1);
        outgoingBatch.setNodeId("target");
        outgoingBatch.setLoadId(LOAD_ID);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        service.updateExtractRequestLoadTime(transaction, new Date(), outgoingBatch);
        verify(nodeService).setInitialLoadEnded(transaction, "target");
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(2, service.sendPasses.get(),
                "a load completed through the batch-loaded path must release its deferred-constraints claim");
    }

    @Test
    void extract_failureDuringSendPhase_incrementsDataSentErrorsOnly() throws Exception {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        DataExtractorService extractService = spy(buildExtractService(statisticManager, outgoingBatchService));
        OutgoingBatch batch = new OutgoingBatch("target1", "testchannel", Status.NE);
        batch.setBatchId(1);
        when(outgoingBatchService.findOutgoingBatch(1, "target1")).thenReturn(batch);
        doReturn(new DataExtractorService.FutureOutgoingBatch(batch, false)).when(extractService)
                .extractBatch(any(OutgoingBatch.class), any(), any(ProcessInfo.class), any(Node.class), any(), any(), anyList());
        doThrow(new RuntimeException("simulated send failure")).when(extractService)
                .sendOutgoingBatch(any(ProcessInfo.class), any(Node.class), any(OutgoingBatch.class), anyBoolean(), any(), any(), any());
        Node extractTargetNode = new Node();
        extractTargetNode.setNodeId("target1");
        ProcessInfo extractInfo = new ProcessInfo(new ProcessInfoKey("source1", "target1", null));
        List<OutgoingBatch> activeBatches = new ArrayList<OutgoingBatch>();
        activeBatches.add(batch);
        extractService.extract(extractInfo, extractTargetNode, activeBatches, mock(IDataWriter.class), null, ExtractMode.FOR_PAYLOAD_CLIENT);
        verify(statisticManager).incrementDataSentErrors("testchannel", 1);
        verify(statisticManager, never()).incrementDataExtractedErrors(any(), anyLong());
    }

    @Test
    void extract_failureDuringExtractPhase_incrementsDataExtractedErrorsOnly() throws Exception {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        DataExtractorService extractService = spy(buildExtractService(statisticManager, outgoingBatchService));
        OutgoingBatch batch = new OutgoingBatch("target1", "testchannel", Status.NE);
        batch.setBatchId(1);
        when(outgoingBatchService.findOutgoingBatch(1, "target1")).thenReturn(batch);
        doThrow(new RuntimeException("simulated extract failure")).when(extractService)
                .extractBatch(any(OutgoingBatch.class), any(), any(ProcessInfo.class), any(Node.class), any(), any(), anyList());
        Node extractTargetNode = new Node();
        extractTargetNode.setNodeId("target1");
        ProcessInfo extractInfo = new ProcessInfo(new ProcessInfoKey("source1", "target1", null));
        List<OutgoingBatch> activeBatches = new ArrayList<OutgoingBatch>();
        activeBatches.add(batch);
        extractService.extract(extractInfo, extractTargetNode, activeBatches, mock(IDataWriter.class), null, ExtractMode.FOR_PAYLOAD_CLIENT);
        verify(statisticManager).incrementDataExtractedErrors("testchannel", 1);
        verify(statisticManager, never()).incrementDataSentErrors(any(), anyLong());
    }

    private DataExtractorService buildExtractService(IStatisticManager statisticManager, IOutgoingBatchService outgoingBatchService) {
        IParameterService testParameterService = mock(IParameterService.class);
        when(testParameterService.getTablePrefix()).thenReturn("sym");
        when(testParameterService.getEngineName()).thenReturn("Test");
        when(testParameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED)).thenReturn(false);
        when(testParameterService.is(ParameterConstants.SYNCHRONIZE_ALL_JOBS)).thenReturn(false);
        when(testParameterService.getLong(ParameterConstants.DATA_LOADER_SEND_ACK_KEEPALIVE)).thenReturn(30000L);
        when(testParameterService.getLong(ParameterConstants.INITIAL_LOAD_TRANSPORT_MAX_BYTES_TO_SYNC)).thenReturn(Long.MAX_VALUE);
        when(engine.getParameterService()).thenReturn(testParameterService);
        IDatabasePlatform testPlatform = mock(IDatabasePlatform.class);
        ISymmetricDialect testSymmetricDialect = mock(ISymmetricDialect.class);
        when(testSymmetricDialect.getPlatform()).thenReturn(testPlatform);
        when(engine.getSymmetricDialect()).thenReturn(testSymmetricDialect);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        IRouterService testRouterService = mock(IRouterService.class);
        when(engine.getRouterService()).thenReturn(testRouterService);
        IDataService testDataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(testDataService);
        IConfigurationService testConfigurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(testConfigurationService);
        ITriggerRouterService testTriggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(testTriggerRouterService);
        INodeService testNodeService = mock(INodeService.class);
        Node sourceNode = new Node();
        sourceNode.setNodeId("source1");
        when(testNodeService.findIdentity()).thenReturn(sourceNode);
        when(testNodeService.findIdentityNodeId()).thenReturn("source1");
        when(engine.getNodeService()).thenReturn(testNodeService);
        ITransformService testTransformService = mock(ITransformService.class);
        when(engine.getTransformService()).thenReturn(testTransformService);
        when(statisticManager.newProcessInfo(any(ProcessInfoKey.class)))
                .thenAnswer(invocation -> new ProcessInfo(invocation.getArgument(0)));
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        IStagingManager testStagingManager = mock(IStagingManager.class);
        when(engine.getStagingManager()).thenReturn(testStagingManager);
        INodeCommunicationService testNodeCommunicationService = mock(INodeCommunicationService.class);
        when(engine.getNodeCommunicationService()).thenReturn(testNodeCommunicationService);
        IClusterService testClusterService = mock(IClusterService.class);
        when(engine.getClusterService()).thenReturn(testClusterService);
        ISequenceService testSequenceService = mock(ISequenceService.class);
        when(engine.getSequenceService()).thenReturn(testSequenceService);
        IInitialLoadService testInitialLoadService = mock(IInitialLoadService.class);
        when(engine.getInitialLoadService()).thenReturn(testInitialLoadService);
        return new DataExtractorService(engine);
    }
}
