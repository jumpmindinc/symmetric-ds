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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

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
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.OutgoingBatches;
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
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    private TestableDataExtractorService service;

    static class TestableDataExtractorService extends DataExtractorService {
        OutgoingBatches pendingBatches;
        List<OutgoingBatch> batchesHandedToExtract;

        TestableDataExtractorService(ISymmetricEngine engine) {
            super(engine);
        }

        @Override
        protected OutgoingBatches loadPendingBatches(ProcessInfo extractInfo, Node targetNode, String queue, IOutgoingTransport transport) {
            return pendingBatches;
        }

        @Override
        protected List<OutgoingBatch> extract(ProcessInfo extractInfo, Node targetNode, List<OutgoingBatch> activeBatches, IDataWriter dataWriter,
                BufferedWriter writer, ExtractMode mode) {
            batchesHandedToExtract = activeBatches;
            return activeBatches;
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
    void extract_localSuspendListRemovesEveryBatch_neverAsksTransportForReservation() {
        IOutgoingTransport transport = reservingTransport();
        service.pendingBatches = batchesOn(EXTRACT_CHANNEL);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(suspending(EXTRACT_CHANNEL));
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertTrue(extracted.isEmpty(), "nothing should be extracted when the local list suspends the channel");
        verify(transport, never()).getSuspendIgnoreChannelLists(any(), any(), any());
        verify(transport, never()).openWriter();
    }

    @Test
    void extract_noPendingBatches_neverAsksTransportForReservation() {
        IOutgoingTransport transport = reservingTransport();
        service.pendingBatches = new OutgoingBatches(new ArrayList<OutgoingBatch>());
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertTrue(extracted.isEmpty(), "nothing should be extracted when there are no pending batches");
        verify(transport, never()).getSuspendIgnoreChannelLists(any(), any(), any());
    }

    @Test
    void extract_localIgnoreListRemovesEveryBatch_marksBatchIgnoredWithoutReservation() {
        IOutgoingTransport transport = reservingTransport();
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
        IOutgoingTransport transport = reservingTransport();
        service.pendingBatches = batchesOn(EXTRACT_CHANNEL);
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(suspending(EXTRACT_CHANNEL));
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertTrue(extracted.isEmpty(), "nothing should be extracted when the remote list suspends the channel");
        verify(transport).getSuspendIgnoreChannelLists(any(), any(), any());
        verify(transport, never()).openWriter();
    }

    @Test
    void extract_batchesSurviveBothFilters_takesReservationAndExtracts() {
        IOutgoingTransport transport = reservingTransport();
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
    void extract_withoutReservation_remoteIgnoreWinsOverLocalSuspend_marksBatchIgnored() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        OutgoingBatch batch = new OutgoingBatch(EXTRACT_TARGET_NODE_ID, EXTRACT_CHANNEL, Status.NE);
        service.pendingBatches = new OutgoingBatches(new ArrayList<OutgoingBatch>(Collections.singletonList(batch)));
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(suspending(EXTRACT_CHANNEL));
        NodeChannels combinedChannels = suspending(EXTRACT_CHANNEL);
        combinedChannels.addIgnoreChannels(EXTRACT_TARGET_NODE_ID, EXTRACT_CHANNEL);
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(combinedChannels);
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertTrue(extracted.isEmpty(), "an ignored batch should not be extracted");
        assertEquals(Status.OK, batch.getStatus(), "the remote ignore should complete the batch even though the local list suspends the channel");
        assertEquals(1, batch.getIgnoreCount(), "the ignore count should be incremented once");
        verify(outgoingBatchService).updateOutgoingBatches(anyList());
        verify(transport, never()).openWriter();
    }

    @Test
    void extract_withoutReservation_suspendListRemovesEveryBatch_leavesBatchPending() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        OutgoingBatch batch = new OutgoingBatch(EXTRACT_TARGET_NODE_ID, EXTRACT_CHANNEL, Status.NE);
        service.pendingBatches = new OutgoingBatches(new ArrayList<OutgoingBatch>(Collections.singletonList(batch)));
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(suspending(EXTRACT_CHANNEL));
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertTrue(extracted.isEmpty(), "a suspended batch should not be extracted");
        assertEquals(Status.NE, batch.getStatus(), "a suspended batch should stay pending");
        verify(configurationService, never()).getSuspendIgnoreChannelLists();
        verify(transport, never()).openWriter();
    }

    @Test
    void extract_withoutReservation_batchesSurviveFilter_extracts() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        service.pendingBatches = batchesOn(EXTRACT_CHANNEL);
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(new NodeChannels());
        when(transport.openWriter()).thenReturn(new BufferedWriter(new StringWriter()));
        List<OutgoingBatch> extracted = service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertEquals(1, extracted.size(), "the surviving batch should be extracted");
        verify(transport).getSuspendIgnoreChannelLists(any(), any(), any());
        verify(transport).openWriter();
    }

    static Stream<Status> pendingStatuses() {
        return Stream.of(Status.RQ, Status.NE, Status.QY, Status.SE, Status.LD, Status.ER, Status.IG, Status.RS);
    }

    @ParameterizedTest
    @MethodSource("pendingStatuses")
    void extract_withReservation_localIgnoreList_marksBatchOkWhateverItsStatus(Status status) {
        IOutgoingTransport transport = reservingTransport();
        OutgoingBatch batch = pendingBatchIn(status);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(ignoring(EXTRACT_CHANNEL));
        service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertEquals(Status.OK, batch.getStatus());
        assertEquals(1, batch.getIgnoreCount());
    }

    @ParameterizedTest
    @MethodSource("pendingStatuses")
    void extract_withReservation_localSuspendList_leavesStatusUnchanged(Status status) {
        IOutgoingTransport transport = reservingTransport();
        OutgoingBatch batch = pendingBatchIn(status);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(suspending(EXTRACT_CHANNEL));
        service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertEquals(status, batch.getStatus());
        assertEquals(0, batch.getIgnoreCount());
    }

    @ParameterizedTest
    @MethodSource("pendingStatuses")
    void extract_withReservation_remoteIgnoreList_marksBatchOkWhateverItsStatus(Status status) {
        IOutgoingTransport transport = reservingTransport();
        OutgoingBatch batch = pendingBatchIn(status);
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(ignoring(EXTRACT_CHANNEL));
        service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertEquals(Status.OK, batch.getStatus());
        assertEquals(1, batch.getIgnoreCount());
    }

    @ParameterizedTest
    @MethodSource("pendingStatuses")
    void extract_withoutReservation_ignoreWinsOverSuspend_marksBatchOkWhateverItsStatus(Status status) {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        OutgoingBatch batch = pendingBatchIn(status);
        NodeChannels combinedChannels = suspending(EXTRACT_CHANNEL);
        combinedChannels.addIgnoreChannels(EXTRACT_TARGET_NODE_ID, EXTRACT_CHANNEL);
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(combinedChannels);
        service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertEquals(Status.OK, batch.getStatus());
        assertEquals(1, batch.getIgnoreCount());
    }

    @ParameterizedTest
    @MethodSource("pendingStatuses")
    void extract_withoutReservation_suspendList_leavesStatusUnchanged(Status status) {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        OutgoingBatch batch = pendingBatchIn(status);
        when(transport.getSuspendIgnoreChannelLists(any(), any(), any())).thenReturn(suspending(EXTRACT_CHANNEL));
        service.extract(new ProcessInfo(), extractTarget(), EXTRACT_QUEUE, transport);
        assertEquals(status, batch.getStatus());
        assertEquals(0, batch.getIgnoreCount());
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

    private IOutgoingTransport reservingTransport() {
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        when(transport.isReservationRequired()).thenReturn(true);
        return transport;
    }

    private OutgoingBatch pendingBatchIn(Status status) {
        OutgoingBatch batch = new OutgoingBatch(EXTRACT_TARGET_NODE_ID, EXTRACT_CHANNEL, status);
        service.pendingBatches = new OutgoingBatches(new ArrayList<OutgoingBatch>(Collections.singletonList(batch)));
        return batch;
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
    }
}
