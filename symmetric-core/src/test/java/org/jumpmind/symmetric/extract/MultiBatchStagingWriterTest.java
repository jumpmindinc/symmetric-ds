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
package org.jumpmind.symmetric.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.symmetric.io.data.writer.StagingDataWriter;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.util.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiBatchStagingWriterTest {
    private ISymmetricEngine engine;
    private IStagingManager stagingManager;
    private IParameterService parameterService;
    private ISymmetricDialect symmetricDialect;
    private IOutgoingBatchService outgoingBatchService;
    private IStatisticManager statisticManager;
    private ProcessInfo processInfo;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        stagingManager = mock(IStagingManager.class);
        parameterService = mock(IParameterService.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        outgoingBatchService = mock(IOutgoingBatchService.class);
        statisticManager = mock(IStatisticManager.class);
        processInfo = new ProcessInfo();
        when(engine.getStagingManager()).thenReturn(stagingManager);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(symmetricDialect.getBinaryEncoding()).thenReturn(BinaryEncoding.HEX);
        when(parameterService.getLong(ParameterConstants.STREAM_TO_FILE_THRESHOLD)).thenReturn(100000L);
        when(parameterService.is(ParameterConstants.SYNCHRONIZE_ALL_JOBS)).thenReturn(false);
    }

    @Test
    void testNew_populatesFieldsFromEngine() {
        List<OutgoingBatch> batches = new ArrayList<>(List.of(createBatch(1), createBatch(2)));
        ExtractRequest request = new ExtractRequest();
        TestableMultiBatchStagingWriter writer = createWriter(request, null, batches, 100L, false, false);
        assertEquals(request, writer.request);
        assertEquals("source", writer.sourceNodeId);
        assertEquals(100L, writer.maxBatchSize);
        assertEquals(2, writer.batches.size());
        assertNotSame(batches, writer.batches);
        assertTrue(writer.finishedBatches.isEmpty());
        assertEquals(processInfo, writer.processInfo);
        assertFalse(writer.isRestarted);
        assertFalse(writer.isSingleLocalTarget);
        assertEquals(100000L, writer.memoryThresholdInBytes);
        assertFalse(writer.synchronizeJobs);
    }

    @Test
    void testOpen_startsFirstBatchAndOpensWriter() {
        List<OutgoingBatch> batches = new ArrayList<>(List.of(createBatch(1), createBatch(2)));
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null, batches, 100L, false, true);
        DataContext context = new DataContext();
        writer.open(context);
        assertEquals(Boolean.TRUE, context.get(MultiBatchStagingWriter.CONTEXT_IS_SINGLE_LOCAL_TARGET));
        assertEquals(1, writer.outgoingBatch.getBatchId());
        assertEquals(1, writer.batches.size());
        assertEquals(1, writer.createdWriters.size());
        verify(writer.createdWriters.get(0)).open(context);
    }

    @Test
    void testBuildWriter_returnsStagingDataWriter() {
        MultiBatchStagingWriter writer = new MultiBatchStagingWriter(engine, new ExtractRequest(), null, "source",
                List.of(createBatch(1)), 100L, processInfo, false, false);
        IDataWriter dataWriter = writer.buildWriter();
        assertNotNull(dataWriter);
        assertTrue(dataWriter instanceof StagingDataWriter);
    }

    @Test
    void testClose_withRemainingEmptyBatches_finishesThemAndLeavesFirstBatchOpen() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1), createBatch(2), createBatch(3))), 100L, false, false);
        writer.open(new DataContext());
        stubFindOutgoingBatchNotOkOrIgnored();
        writer.close();
        assertEquals(2, writer.finishedBatches.size());
        verify(writer.createdWriters.get(0), never()).end(any(Batch.class), anyBoolean());
        verify(writer.createdWriters.get(0), never()).close();
        verify(writer.createdWriters.get(1)).close();
        verify(writer.createdWriters.get(2)).close();
        // Defect pinned, not endorsed: close()'s loop calls end(batch,false) (which itself calls
        // closeCurrentDataWriter() and, when not synchronized, checkSend()) and then calls
        // closeCurrentDataWriter()/checkSend() again explicitly, double-firing checkSend per empty batch.
        verify(outgoingBatchService, times(4)).updateOutgoingBatch(any(OutgoingBatch.class));
    }

    @Test
    void testClose_whenInError_deletesStagedResourceAndSkipsRemainingBatches() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1), createBatch(2))), 100L, false, false);
        writer.open(new DataContext());
        startCurrentBatch(writer);
        writer.inError = true;
        IStagedResource resource = mock(IStagedResource.class);
        when(stagingManager.find(any(), any(), any())).thenReturn(resource);
        writer.close();
        assertEquals(1, writer.batches.size());
        verify(resource).delete();
        verify(outgoingBatchService, never()).updateOutgoingBatch(any(OutgoingBatch.class));
    }

    @Test
    void testClose_withSynchronizeJobsEnabled_sendsFinishedBatchesAfterLoop() {
        when(parameterService.is(ParameterConstants.SYNCHRONIZE_ALL_JOBS)).thenReturn(true);
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        startCurrentBatch(writer);
        stubFindOutgoingBatchNotOkOrIgnored();
        writer.close();
        verify(outgoingBatchService, times(1)).updateOutgoingBatch(any(OutgoingBatch.class));
    }

    @Test
    void testGetStatistics_withoutOpenWriter_returnsEmptyMap() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        assertTrue(writer.getStatistics().isEmpty());
    }

    @Test
    void testGetStatistics_withOpenWriter_delegatesToCurrentDataWriter() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        assertSame(writer.createdWriters.get(0).getStatistics(), writer.getStatistics());
    }

    @Test
    void testStart_batch_setsFieldAndUpdatesProcessInfo() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        Batch sourceBatch = new Batch(BatchType.EXTRACT, 999L, Constants.CHANNEL_DEFAULT, BinaryEncoding.HEX, "source", "target", false);
        writer.start(sourceBatch);
        assertEquals(sourceBatch, writer.batch);
        assertEquals(999L, processInfo.getCurrentBatchId());
        assertEquals(Constants.CHANNEL_DEFAULT, processInfo.getCurrentChannelId());
        assertEquals(0, processInfo.getCurrentDataCount());
        verify(writer.createdWriters.get(0)).start(sourceBatch);
    }

    @Test
    void testStart_batch_withNullBatch_skipsProcessInfoUpdates() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        processInfo.setCurrentChannelId("untouched");
        writer.start((Batch) null);
        assertEquals("untouched", processInfo.getCurrentChannelId());
        verify(writer.createdWriters.get(0)).start((Batch) null);
    }

    @Test
    void testStart_table_setsFieldUpdatesProcessInfoAndReturnsTrue() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        Table table = mock(Table.class);
        when(table.getFullyQualifiedName()).thenReturn("cat.tbl");
        boolean result = writer.start(table);
        assertTrue(result);
        assertEquals(table, writer.table);
        assertEquals("cat.tbl", processInfo.getCurrentTableName());
        verify(writer.createdWriters.get(0)).start(table);
    }

    @Test
    void testStart_table_withNullTable_stillReturnsTrue() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        boolean result = writer.start((Table) null);
        assertTrue(result);
        verify(writer.createdWriters.get(0)).start((Table) null);
    }

    @Test
    void testWrite_incrementsCountsAndDelegatesWithoutRollover() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1), createBatch(2))), 100L, false, false);
        writer.open(new DataContext());
        CsvData data = mock(CsvData.class);
        writer.write(data);
        writer.write(data);
        assertEquals(2, writer.outgoingBatch.getDataRowCount());
        assertEquals(2, writer.outgoingBatch.getDataInsertRowCount());
        assertEquals(1, writer.createdWriters.size());
        verify(writer.createdWriters.get(0), times(2)).write(data);
    }

    @Test
    void testWrite_whenMaxBatchSizeReachedAndBatchesRemain_rotatesToNewBatch() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1), createBatch(2))), 1L, false, false);
        writer.open(new DataContext());
        startCurrentBatch(writer);
        stubFindOutgoingBatchNotOkOrIgnored();
        CsvData data = mock(CsvData.class);
        writer.write(data);
        assertTrue(writer.batches.isEmpty());
        assertEquals(2, writer.createdWriters.size());
        assertEquals(2, writer.outgoingBatch.getBatchId());
        verify(writer.createdWriters.get(0)).end((Table) null);
        verify(writer.createdWriters.get(0)).end(any(Batch.class), eq(false));
        verify(writer.createdWriters.get(0)).close();
        verify(outgoingBatchService).updateOutgoingBatch(any(OutgoingBatch.class));
        verify(writer.createdWriters.get(1)).start(any(Batch.class));
    }

    @Test
    void testWrite_whenTimestampIsStale_logsProgressAndResetsTimestamp() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1), createBatch(2))), 100L, false, false);
        writer.open(new DataContext());
        startCurrentBatch(writer);
        long staleTs = System.currentTimeMillis() - 70000;
        writer.ts = staleTs;
        writer.write(mock(CsvData.class));
        assertTrue(writer.ts - staleTs >= 60000, "ts should have been reset to a fresh timestamp");
    }

    @Test
    void testCheckSend_withBatchNotOkOrIgnored_marksDoneAndUpdatesStatus() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        IStagedResource resource = mock(IStagedResource.class);
        when(stagingManager.find(any(), any(), any())).thenReturn(resource);
        OutgoingBatch fromDatabase = createBatch(1);
        fromDatabase.setStatus(Status.NE);
        fromDatabase.setDataRowCount(5);
        fromDatabase.setDataInsertRowCount(5);
        when(outgoingBatchService.findOutgoingBatch(1L, "target")).thenReturn(fromDatabase);
        writer.checkSend(new Statistics());
        verify(resource).setState(State.DONE);
        assertEquals(Status.NE, writer.outgoingBatch.getStatus());
        verify(outgoingBatchService).updateOutgoingBatch(writer.outgoingBatch);
    }

    @Test
    void testCheckSend_withBatchAlreadyOk_throwsCancellationException() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        OutgoingBatch fromDatabase = createBatch(1);
        fromDatabase.setStatus(Status.OK);
        when(outgoingBatchService.findOutgoingBatch(1L, "target")).thenReturn(fromDatabase);
        Statistics statistics = new Statistics();
        assertThrows(CancellationException.class, () -> writer.checkSend(statistics));
        verify(outgoingBatchService, never()).updateOutgoingBatch(any(OutgoingBatch.class));
    }

    @Test
    void testCheckSendChildRequests_withNoChildRequests_doesNothing() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        writer.checkSendChildRequests(writer.outgoingBatch, null, new Statistics());
        assertTrue(writer.childBatches.isEmpty());
    }

    @Test
    void testCheckSendChildRequests_withChildRequest_copiesStagedResourceAndUpdatesChildBatch() throws Exception {
        ExtractRequest request = new ExtractRequest();
        request.setStartBatchId(100L);
        ExtractRequest childRequest = new ExtractRequest();
        childRequest.setStartBatchId(200L);
        childRequest.setNodeId("child1");
        TestableMultiBatchStagingWriter writer = createWriter(request, List.of(childRequest),
                new ArrayList<>(List.of(createBatch(105))), 100L, false, false);
        writer.open(new DataContext());
        IStagedResource parentResource = mock(IStagedResource.class);
        BufferedReader reader = new BufferedReader(new StringReader("batch, 105, x\ncommit, 105\n"));
        when(parentResource.getReader()).thenReturn(reader);
        IStagedResource childResource = mock(IStagedResource.class);
        StringWriter sw = new StringWriter();
        BufferedWriter bufferedWriter = new BufferedWriter(sw);
        when(childResource.getWriter(anyLong())).thenReturn(bufferedWriter);
        when(stagingManager.create(any(), any(), any())).thenReturn(childResource);
        OutgoingBatch childBatchFromDb = createBatch(205);
        childBatchFromDb.setNodeId("child1");
        childBatchFromDb.setStatus(Status.NE);
        when(outgoingBatchService.findOutgoingBatch(205L, "child1")).thenReturn(childBatchFromDb);
        Statistics stats = new Statistics();
        stats.set(DataWriterStatisticConstants.ROWCOUNT, 3L);
        stats.set(DataWriterStatisticConstants.INSERTCOUNT, 2L);
        stats.set(DataWriterStatisticConstants.BYTECOUNT, 40L);
        writer.checkSendChildRequests(writer.outgoingBatch, parentResource, stats);
        bufferedWriter.flush();
        assertTrue(sw.toString().contains("batch, 205"));
        verify(childResource).setState(State.DONE);
        verify(childResource).close();
        verify(parentResource).close();
        verify(outgoingBatchService).updateOutgoingBatch(childBatchFromDb);
        assertTrue(writer.childBatches.containsKey(205L));
        assertEquals(3L, childBatchFromDb.getDataRowCount());
        assertEquals(2L, childBatchFromDb.getDataInsertRowCount());
        assertEquals(40L, childBatchFromDb.getByteCount());
    }

    @Test
    void testCheckSendChildRequests_withNullParentResource_skipsCopyButUpdatesChildBatch() {
        ExtractRequest request = new ExtractRequest();
        request.setStartBatchId(100L);
        ExtractRequest childRequest = new ExtractRequest();
        childRequest.setStartBatchId(200L);
        childRequest.setNodeId("child1");
        TestableMultiBatchStagingWriter writer = createWriter(request, List.of(childRequest),
                new ArrayList<>(List.of(createBatch(105))), 100L, false, false);
        writer.open(new DataContext());
        OutgoingBatch childBatchFromDb = createBatch(205);
        childBatchFromDb.setNodeId("child1");
        childBatchFromDb.setStatus(Status.NE);
        when(outgoingBatchService.findOutgoingBatch(205L, "child1")).thenReturn(childBatchFromDb);
        writer.checkSendChildRequests(writer.outgoingBatch, null, new Statistics());
        verify(stagingManager, never()).create(any(), any(), any());
        verify(outgoingBatchService).updateOutgoingBatch(childBatchFromDb);
        assertTrue(writer.childBatches.containsKey(205L));
    }

    @Test
    void testCheckSendChildRequests_withMultipleChildRequests_computesEachChildBatchId() {
        ExtractRequest request = new ExtractRequest();
        request.setStartBatchId(100L);
        ExtractRequest childRequest1 = new ExtractRequest();
        childRequest1.setStartBatchId(200L);
        childRequest1.setNodeId("child1");
        ExtractRequest childRequest2 = new ExtractRequest();
        childRequest2.setStartBatchId(300L);
        childRequest2.setNodeId("child2");
        TestableMultiBatchStagingWriter writer = createWriter(request, List.of(childRequest1, childRequest2),
                new ArrayList<>(List.of(createBatch(105))), 100L, false, false);
        writer.open(new DataContext());
        OutgoingBatch childBatch1 = createBatch(205);
        childBatch1.setNodeId("child1");
        childBatch1.setStatus(Status.NE);
        OutgoingBatch childBatch2 = createBatch(305);
        childBatch2.setNodeId("child2");
        childBatch2.setStatus(Status.NE);
        when(outgoingBatchService.findOutgoingBatch(205L, "child1")).thenReturn(childBatch1);
        when(outgoingBatchService.findOutgoingBatch(305L, "child2")).thenReturn(childBatch2);
        writer.checkSendChildRequests(writer.outgoingBatch, null, new Statistics());
        assertTrue(writer.childBatches.containsKey(205L));
        assertTrue(writer.childBatches.containsKey(305L));
        verify(outgoingBatchService).updateOutgoingBatch(childBatch1);
        verify(outgoingBatchService).updateOutgoingBatch(childBatch2);
    }

    @Test
    void testCheckSendChildRequests_withNonBatchOrCommitLine_leavesLineUnchanged() throws Exception {
        ExtractRequest request = new ExtractRequest();
        request.setStartBatchId(100L);
        ExtractRequest childRequest = new ExtractRequest();
        childRequest.setStartBatchId(200L);
        childRequest.setNodeId("child1");
        TestableMultiBatchStagingWriter writer = createWriter(request, List.of(childRequest),
                new ArrayList<>(List.of(createBatch(105))), 100L, false, false);
        writer.open(new DataContext());
        IStagedResource parentResource = mock(IStagedResource.class);
        BufferedReader reader = new BufferedReader(new StringReader("insert, 105, foo\n"));
        when(parentResource.getReader()).thenReturn(reader);
        IStagedResource childResource = mock(IStagedResource.class);
        StringWriter sw = new StringWriter();
        BufferedWriter bufferedWriter = new BufferedWriter(sw);
        when(childResource.getWriter(anyLong())).thenReturn(bufferedWriter);
        when(stagingManager.create(any(), any(), any())).thenReturn(childResource);
        OutgoingBatch childBatchFromDb = createBatch(205);
        childBatchFromDb.setNodeId("child1");
        childBatchFromDb.setStatus(Status.NE);
        when(outgoingBatchService.findOutgoingBatch(205L, "child1")).thenReturn(childBatchFromDb);
        writer.checkSendChildRequests(writer.outgoingBatch, parentResource, new Statistics());
        bufferedWriter.flush();
        assertTrue(sw.toString().contains("insert, 105, foo"));
    }

    @Test
    void testCheckSendChildRequests_withTrailingUnterminatedLine_copiesFinalLine() throws Exception {
        ExtractRequest request = new ExtractRequest();
        request.setStartBatchId(100L);
        ExtractRequest childRequest = new ExtractRequest();
        childRequest.setStartBatchId(200L);
        childRequest.setNodeId("child1");
        TestableMultiBatchStagingWriter writer = createWriter(request, List.of(childRequest),
                new ArrayList<>(List.of(createBatch(105))), 100L, false, false);
        writer.open(new DataContext());
        IStagedResource parentResource = mock(IStagedResource.class);
        BufferedReader reader = new BufferedReader(new StringReader("batch, 105"));
        when(parentResource.getReader()).thenReturn(reader);
        IStagedResource childResource = mock(IStagedResource.class);
        StringWriter sw = new StringWriter();
        BufferedWriter bufferedWriter = new BufferedWriter(sw);
        when(childResource.getWriter(anyLong())).thenReturn(bufferedWriter);
        when(stagingManager.create(any(), any(), any())).thenReturn(childResource);
        OutgoingBatch childBatchFromDb = createBatch(205);
        childBatchFromDb.setNodeId("child1");
        childBatchFromDb.setStatus(Status.NE);
        when(outgoingBatchService.findOutgoingBatch(205L, "child1")).thenReturn(childBatchFromDb);
        writer.checkSendChildRequests(writer.outgoingBatch, parentResource, new Statistics());
        bufferedWriter.flush();
        assertTrue(sw.toString().contains("batch, 205"));
    }

    @Test
    void testCheckSendChildRequests_whenReaderThrows_wrapsExceptionAndStillClosesResources() throws Exception {
        ExtractRequest request = new ExtractRequest();
        request.setStartBatchId(100L);
        ExtractRequest childRequest = new ExtractRequest();
        childRequest.setStartBatchId(200L);
        childRequest.setNodeId("child1");
        TestableMultiBatchStagingWriter writer = createWriter(request, List.of(childRequest),
                new ArrayList<>(List.of(createBatch(105))), 100L, false, false);
        writer.open(new DataContext());
        IStagedResource parentResource = mock(IStagedResource.class);
        BufferedReader reader = mock(BufferedReader.class);
        when(reader.read()).thenThrow(new IOException("boom"));
        when(parentResource.getReader()).thenReturn(reader);
        IStagedResource childResource = mock(IStagedResource.class);
        BufferedWriter bufferedWriter = mock(BufferedWriter.class);
        when(childResource.getWriter(anyLong())).thenReturn(bufferedWriter);
        when(stagingManager.create(any(), any(), any())).thenReturn(childResource);
        Statistics statistics = new Statistics();
        assertThrows(RuntimeException.class,
                () -> writer.checkSendChildRequests(writer.outgoingBatch, parentResource, statistics));
        verify(childResource).close();
        verify(parentResource).close();
    }

    @Test
    void testCheckSendChildRequests_withNullStats_skipsSettingCounts() {
        ExtractRequest request = new ExtractRequest();
        request.setStartBatchId(100L);
        ExtractRequest childRequest = new ExtractRequest();
        childRequest.setStartBatchId(200L);
        childRequest.setNodeId("child1");
        TestableMultiBatchStagingWriter writer = createWriter(request, List.of(childRequest),
                new ArrayList<>(List.of(createBatch(105))), 100L, false, false);
        writer.open(new DataContext());
        OutgoingBatch childBatchFromDb = createBatch(205);
        childBatchFromDb.setNodeId("child1");
        childBatchFromDb.setStatus(Status.NE);
        when(outgoingBatchService.findOutgoingBatch(205L, "child1")).thenReturn(childBatchFromDb);
        writer.checkSendChildRequests(writer.outgoingBatch, null, null);
        assertEquals(0L, childBatchFromDb.getDataRowCount());
        assertEquals(0L, childBatchFromDb.getDataInsertRowCount());
        assertEquals(0L, childBatchFromDb.getByteCount());
        verify(outgoingBatchService).updateOutgoingBatch(childBatchFromDb);
    }

    @Test
    void testCheckSendChildRequests_whenChildBatchAlreadyOk_skipsStatusUpdateButStillTracksIt() {
        ExtractRequest request = new ExtractRequest();
        request.setStartBatchId(100L);
        ExtractRequest childRequest = new ExtractRequest();
        childRequest.setStartBatchId(200L);
        childRequest.setNodeId("child1");
        TestableMultiBatchStagingWriter writer = createWriter(request, List.of(childRequest),
                new ArrayList<>(List.of(createBatch(105))), 100L, false, false);
        writer.open(new DataContext());
        OutgoingBatch childBatchFromDb = createBatch(205);
        childBatchFromDb.setNodeId("child1");
        childBatchFromDb.setStatus(Status.OK);
        when(outgoingBatchService.findOutgoingBatch(205L, "child1")).thenReturn(childBatchFromDb);
        writer.checkSendChildRequests(writer.outgoingBatch, null, new Statistics());
        assertEquals(Status.OK, childBatchFromDb.getStatus());
        verify(outgoingBatchService, never()).updateOutgoingBatch(any(OutgoingBatch.class));
        assertTrue(writer.childBatches.containsKey(205L));
    }

    @Test
    void testEnd_table_updatesByteCountAndExtractMillis() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        startCurrentBatch(writer);
        Table table = mock(Table.class);
        writer.end(table);
        verify(writer.createdWriters.get(0)).end(table);
        assertEquals(0, writer.outgoingBatch.getByteCount());
    }

    @Test
    void testEnd_batch_setsInErrorAndClosesWriter() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        startCurrentBatch(writer);
        Batch batchArg = writer.batch;
        writer.end(batchArg, true);
        assertTrue(writer.inError);
        verify(writer.createdWriters.get(0)).end(batchArg, true);
        verify(writer.createdWriters.get(0)).close();
    }

    @Test
    void testNextBatch_withPurgeOnTtlEnabled_refreshesFinishedBatchResources() {
        when(parameterService.is(ParameterConstants.STREAM_TO_FILE_PURGE_ON_TTL_ENABLED, false)).thenReturn(true);
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1), createBatch(2), createBatch(3))), 1L, false, false);
        writer.open(new DataContext());
        startCurrentBatch(writer);
        stubFindOutgoingBatchNotOkOrIgnored();
        writer.write(mock(CsvData.class));
        IStagedResource resource = mock(IStagedResource.class);
        when(stagingManager.find(any(), any(), any())).thenReturn(resource);
        writer.nextBatchForTest();
        verify(resource, times(1)).refreshLastUpdateTime();
    }

    @Test
    void testStartNewBatch_opensAndStartsNewWriter() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1), createBatch(2))), 100L, false, false);
        DataContext context = new DataContext();
        writer.open(context);
        Table table = mock(Table.class);
        writer.start(table);
        writer.startNewBatchForTest();
        assertEquals(2, writer.createdWriters.size());
        assertEquals(2, writer.outgoingBatch.getBatchId());
        verify(writer.createdWriters.get(1)).open(context);
        verify(writer.createdWriters.get(1)).start(writer.batch);
        verify(writer.createdWriters.get(1)).start(table);
    }

    @Test
    void testGetStagedResource_delegatesToStagingManagerFind() {
        TestableMultiBatchStagingWriter writer = createWriter(new ExtractRequest(), null,
                new ArrayList<>(List.of(createBatch(1))), 100L, false, false);
        writer.open(new DataContext());
        IStagedResource resource = mock(IStagedResource.class);
        when(stagingManager.find(any(), any(), any())).thenReturn(resource);
        assertEquals(resource, writer.getStagedResourceForTest(writer.outgoingBatch));
    }

    private void startCurrentBatch(TestableMultiBatchStagingWriter writer) {
        OutgoingBatch outgoingBatch = writer.outgoingBatch;
        writer.start(new Batch(BatchType.EXTRACT, outgoingBatch.getBatchId(), outgoingBatch.getChannelId(), BinaryEncoding.HEX,
                "source", outgoingBatch.getNodeId(), false));
    }

    private void stubFindOutgoingBatchNotOkOrIgnored() {
        when(outgoingBatchService.findOutgoingBatch(anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    OutgoingBatch batch = createBatch(invocation.getArgument(0));
                    batch.setStatus(Status.NE);
                    return batch;
                });
    }

    private OutgoingBatch createBatch(long batchId) {
        OutgoingBatch batch = new OutgoingBatch("target", Constants.CHANNEL_DEFAULT, Status.RT);
        batch.setBatchId(batchId);
        return batch;
    }

    private TestableMultiBatchStagingWriter createWriter(ExtractRequest request, List<ExtractRequest> childRequests,
            List<OutgoingBatch> batches, long maxBatchSize, boolean isRestarted, boolean isSingleLocalTarget) {
        return new TestableMultiBatchStagingWriter(engine, request, childRequests, "source", batches, maxBatchSize, processInfo,
                isRestarted, isSingleLocalTarget);
    }

    private static class TestableMultiBatchStagingWriter extends MultiBatchStagingWriter {
        private final List<IDataWriter> createdWriters = new ArrayList<>();
        private final Map<Batch, Statistics> statsByBatch = new HashMap<>();

        TestableMultiBatchStagingWriter(ISymmetricEngine engine, ExtractRequest request, List<ExtractRequest> childRequests,
                String sourceNodeId, List<OutgoingBatch> batches, long maxBatchSize, ProcessInfo processInfo, boolean isRestarted,
                boolean isSingleLocalTarget) {
            super(engine, request, childRequests, sourceNodeId, batches, maxBatchSize, processInfo, isRestarted, isSingleLocalTarget);
        }

        @Override
        protected IDataWriter buildWriter() {
            IDataWriter writer = mock(IDataWriter.class);
            when(writer.getStatistics()).thenAnswer(invocation -> {
                statsByBatch.computeIfAbsent(batch, b -> new Statistics());
                return statsByBatch;
            });
            createdWriters.add(writer);
            return writer;
        }

        void nextBatchForTest() {
            nextBatch();
        }

        void startNewBatchForTest() {
            startNewBatch();
        }

        IStagedResource getStagedResourceForTest(OutgoingBatch outgoingBatch) {
            return getStagedResource(outgoingBatch);
        }
    }
}
