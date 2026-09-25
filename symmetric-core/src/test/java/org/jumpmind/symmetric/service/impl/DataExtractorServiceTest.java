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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.extract.SelectFromSymDataSource;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.route.AbstractFileParsingRouter;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IInitialLoadService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.impl.DataExtractorService.ExtractMode;
<<<<<<< HEAD
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DataExtractorServiceTest {
    private static final String EXTRACT_TARGET_NODE_ID = "target1";
    private static final String EXTRACT_CHANNEL = "testchannel";
    private static final String EXTRACT_SECOND_CHANNEL = "othertestchannel";
    private static final String EXTRACT_QUEUE = "default";
    protected ISymmetricEngine engine;
    private IParameterService parameterService;
    private IConfigurationService configurationService;
    private IOutgoingBatchService outgoingBatchService;
    private IRouterService routerService;
    private IInitialLoadService initialLoadService;
=======
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
>>>>>>> fdbf2082ab (SYM-8099: Revert-Skip reservation process, when filtered list of batches is empty (release/3.18) (#1145))
    private TestableDataExtractorService service;

    static class TestableDataExtractorService extends DataExtractorService {
<<<<<<< HEAD
        OutgoingBatches pendingBatches;
        List<OutgoingBatch> batchesHandedToExtract;
=======
        AtomicInteger sendPasses = new AtomicInteger();
>>>>>>> fdbf2082ab (SYM-8099: Revert-Skip reservation process, when filtered list of batches is empty (release/3.18) (#1145))

        TestableDataExtractorService(ISymmetricEngine engine) {
            super(engine);
        }

        @Override
<<<<<<< HEAD
        protected OutgoingBatches loadPendingBatches(ProcessInfo extractInfo, Node targetNode, String queue, IOutgoingTransport transport) {
            return pendingBatches;
        }

        @Override
        protected List<OutgoingBatch> extract(ProcessInfo extractInfo, Node targetNode, List<OutgoingBatch> activeBatches, IDataWriter dataWriter,
                BufferedWriter writer, ExtractMode mode) {
            batchesHandedToExtract = activeBatches;
            return activeBatches;
=======
        public List<ExtractRequest> getTablesForExtractByLoadId(long loadId) {
            sendPasses.incrementAndGet();
            return Collections.emptyList();
>>>>>>> fdbf2082ab (SYM-8099: Revert-Skip reservation process, when filtered list of batches is empty (release/3.18) (#1145))
        }
    }

    @BeforeEach
    public void setUp() {
        engine = mock(ISymmetricEngine.class);
        when(engine.getTablePrefix()).thenReturn("sym");
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
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getSqlReplacementTokens()).thenReturn(new HashMap<String, String>());
        when(platform.getSqlTemplate()).thenReturn(mock(ISqlTemplate.class));
        when(platform.getSqlTemplateDirty()).thenReturn(mock(ISqlTemplate.class));
        IDataService dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
<<<<<<< HEAD
        when(engine.getNodeService()).thenReturn(mock(INodeService.class));
        configurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        outgoingBatchService = mock(IOutgoingBatchService.class);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        routerService = mock(IRouterService.class);
        when(engine.getRouterService()).thenReturn(routerService);
        initialLoadService = mock(IInitialLoadService.class);
        when(engine.getInitialLoadService()).thenReturn(initialLoadService);
=======
        nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
>>>>>>> fdbf2082ab (SYM-8099: Revert-Skip reservation process, when filtered list of batches is empty (release/3.18) (#1145))
        service = new TestableDataExtractorService(engine);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void selectFromSymDataSource_csvValuesAreExtracted_triggerRouterIsNotMarkedAsMissing() {
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
<<<<<<< HEAD
    void extract_localSuspendListRemovesEveryBatch_neverAsksTransportForReservation() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        service.pendingBatches = batchesOn(EXTRACT_CHANNEL);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(suspending(EXTRACT_CHANNEL));
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertTrue(extracted.isEmpty(), "nothing should be extracted when the local list suspends the channel");
        verify(transport, never()).getSuspendIgnoreChannelLists(any(), any(), any());
        verify(transport, never()).openWriter();
    }

    @Test
    void extract_noPendingBatches_neverAsksTransportForReservation() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        service.pendingBatches = new OutgoingBatches(new ArrayList<OutgoingBatch>());
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertTrue(extracted.isEmpty(), "nothing should be extracted when there are no pending batches");
        verify(transport, never()).getSuspendIgnoreChannelLists(any(), any(), any());
    }

    @Test
    void extract_localIgnoreListRemovesEveryBatch_marksBatchIgnoredWithoutReservation() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        OutgoingBatch batch = new OutgoingBatch(EXTRACT_TARGET_NODE_ID, EXTRACT_CHANNEL, Status.NE);
        service.pendingBatches = new OutgoingBatches(new ArrayList<OutgoingBatch>(Collections.singletonList(batch)));
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(ignoring(EXTRACT_CHANNEL));
        service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertEquals(Status.OK, batch.getStatus(), "an ignored batch should be completed rather than left pending");
        assertEquals(1, batch.getIgnoreCount(), "the ignore count should be incremented once");
        verify(outgoingBatchService).updateOutgoingBatches(anyList());
        verify(transport, never()).getSuspendIgnoreChannelLists(any(), any(), any());
    }

    @Test
    void extract_remoteSuspendListRemovesEveryBatch_takesReservationButWritesNothing() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        service.pendingBatches = batchesOn(EXTRACT_CHANNEL);
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(suspending(EXTRACT_CHANNEL));
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertTrue(extracted.isEmpty(), "nothing should be extracted when the remote list suspends the channel");
        verify(transport).getSuspendIgnoreChannelLists(any(), any(), any());
        verify(transport, never()).openWriter();
    }

    @Test
    void extract_batchesSurviveBothFilters_takesReservationAndExtracts() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        service.pendingBatches = batchesOn(EXTRACT_CHANNEL);
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(new NodeChannels());
        when(transport.openWriter()).thenReturn(new BufferedWriter(new StringWriter()));
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertEquals(1, extracted.size(), "the surviving batch should be extracted");
        assertEquals(1, service.batchesHandedToExtract.size(), "the surviving batch should be handed to the extractor");
        verify(transport).getSuspendIgnoreChannelLists(any(), any(), any());
        verify(transport).openWriter();
    }

    @Test
    void isRoutingJobStarted_startRouteJob38IsBlank_fallsBackToStartRouteJob() {
        when(parameterService.getString(ParameterConstants.START_ROUTE_JOB_38)).thenReturn(null);
        when(parameterService.is(ParameterConstants.START_ROUTE_JOB)).thenReturn(true);
        assertTrue(service.isRoutingJobStarted(), "the legacy parameter should decide when the 3.8 parameter is blank");
    }

    @Test
    void isRoutingJobStarted_startRouteJob38IsSet_ignoresStartRouteJob() {
        when(parameterService.getString(ParameterConstants.START_ROUTE_JOB_38)).thenReturn("false");
        when(parameterService.is(ParameterConstants.START_ROUTE_JOB)).thenReturn(true);
        when(parameterService.is(ParameterConstants.START_ROUTE_JOB_38)).thenReturn(false);
        assertFalse(service.isRoutingJobStarted(), "the 3.8 parameter should win when it is set");
    }

    @Test
    void routeDataIfRequiredBeforeExtract_routingJobIsStarted_doesNotRoute() {
        when(parameterService.is(ParameterConstants.START_ROUTE_JOB)).thenReturn(true);
        when(parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)).thenReturn(true);
        service.routeDataIfRequiredBeforeExtract();
        verify(routerService, never()).routeData(anyBoolean());
        verify(initialLoadService, never()).queueLoads(anyBoolean());
    }

    @Test
    void routeDataIfRequiredBeforeExtract_routeOnExtractIsDisabled_doesNotRoute() {
        when(parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)).thenReturn(false);
        service.routeDataIfRequiredBeforeExtract();
        verify(routerService, never()).routeData(anyBoolean());
        verify(initialLoadService, never()).queueLoads(anyBoolean());
    }

    @Test
    void routeDataIfRequiredBeforeExtract_routingJobIsOffAndRouteOnExtractIsEnabled_routes() {
        when(parameterService.is(ParameterConstants.START_ROUTE_JOB)).thenReturn(false);
        when(parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)).thenReturn(true);
        service.routeDataIfRequiredBeforeExtract();
        verify(initialLoadService).queueLoads(true);
        verify(routerService).routeData(true);
    }

    @Test
    void filterSuspendedAndIgnoredBatches_nothingIsSuspendedOrIgnored_returnsNoRemovedBatches() {
        OutgoingBatches batches = batchesOn(EXTRACT_CHANNEL);
        List<OutgoingBatch> removed = service.filterSuspendedAndIgnoredBatches(batches, new NodeChannels());
        assertTrue(removed.isEmpty(), "no batch should be reported as removed");
        assertEquals(1, batches.getBatches().size(), "the batch should survive");
    }

    @Test
    void filterSuspendedAndIgnoredBatches_channelIsIgnored_returnsTheIgnoredBatch() {
        OutgoingBatches batches = batchesOn(EXTRACT_CHANNEL);
        OutgoingBatch ignored = batches.getBatches().get(0);
        List<OutgoingBatch> removed = service.filterSuspendedAndIgnoredBatches(batches, ignoring(EXTRACT_CHANNEL));
        assertEquals(1, removed.size(), "the ignored batch should be reported as removed");
        assertSame(ignored, removed.get(0), "the reported batch should be the ignored one");
        assertTrue(batches.getBatches().isEmpty(), "the ignored batch should no longer be pending");
    }

    @Test
    void filterSuspendedAndIgnoredBatches_channelIsSuspended_returnsTheSuspendedBatch() {
        OutgoingBatches batches = batchesOn(EXTRACT_CHANNEL);
        OutgoingBatch suspended = batches.getBatches().get(0);
        List<OutgoingBatch> removed = service.filterSuspendedAndIgnoredBatches(batches, suspending(EXTRACT_CHANNEL));
        assertEquals(1, removed.size(), "the suspended batch should be reported as removed");
        assertSame(suspended, removed.get(0), "the reported batch should be the suspended one");
        assertTrue(batches.getBatches().isEmpty(), "the suspended batch should no longer be pending");
    }

    @Test
    void filterSuspendedAndIgnoredBatches_oneChannelIgnoredAndAnotherSuspended_returnsBothBatches() {
        OutgoingBatches batches = batchesOn(EXTRACT_CHANNEL, EXTRACT_SECOND_CHANNEL);
        List<OutgoingBatch> pending = new ArrayList<OutgoingBatch>(batches.getBatches());
        NodeChannels nodeChannels = ignoring(EXTRACT_CHANNEL);
        nodeChannels.addSuspendChannels(EXTRACT_TARGET_NODE_ID, EXTRACT_SECOND_CHANNEL);
        List<OutgoingBatch> removed = service.filterSuspendedAndIgnoredBatches(batches, nodeChannels);
        assertEquals(2, removed.size(), "both the ignored and the suspended batch should be reported as removed");
        assertTrue(removed.containsAll(pending), "the reported batches should be the ignored and the suspended one");
        assertTrue(batches.getBatches().isEmpty(), "neither batch should remain pending");
    }

    @Test
    void filterSuspendedAndIgnoredBatches_batchesAreNull_returnsNoRemovedBatches() {
        assertTrue(service.filterSuspendedAndIgnoredBatches(null, new NodeChannels()).isEmpty(), "a missing batch list should remove nothing");
    }

    @Test
    void filterSuspendedAndIgnoredBatches_suspendIgnoreListIsNull_returnsNoRemovedBatches() {
        OutgoingBatches batches = batchesOn(EXTRACT_CHANNEL);
        assertTrue(service.filterSuspendedAndIgnoredBatches(batches, null).isEmpty(), "a missing suspend and ignore list should remove nothing");
        assertEquals(1, batches.getBatches().size(), "the batch should survive");
    }

    @Test
    void filterSuspendedAndIgnoredBatches_thereAreNoBatches_returnsNoRemovedBatches() {
        OutgoingBatches batches = new OutgoingBatches(new ArrayList<OutgoingBatch>());
        assertTrue(service.filterSuspendedAndIgnoredBatches(batches, ignoring(EXTRACT_CHANNEL)).isEmpty(), "an empty batch list should remove nothing");
    }

    private Node extractTarget() {
        Node node = new Node();
        node.setNodeId(EXTRACT_TARGET_NODE_ID);
        node.setSymmetricVersion("3.17.0");
        return node;
    }

    private OutgoingBatches batchesOn(String... channelIds) {
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        for (String channelId : channelIds) {
            OutgoingBatch batch = new OutgoingBatch(EXTRACT_TARGET_NODE_ID, channelId, Status.NE);
            batch.setBatchId(batches.size() + 1);
            batches.add(batch);
        }
        return new OutgoingBatches(batches);
    }

    private NodeChannels suspending(String channelId) {
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addSuspendChannels(EXTRACT_TARGET_NODE_ID, channelId);
        return nodeChannels;
    }

    private NodeChannels ignoring(String channelId) {
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addIgnoreChannels(EXTRACT_TARGET_NODE_ID, channelId);
        return nodeChannels;
=======
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
>>>>>>> fdbf2082ab (SYM-8099: Revert-Skip reservation process, when filtered list of batches is empty (release/3.18) (#1145))
    }
}
