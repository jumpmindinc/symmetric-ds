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
package org.jumpmind.symmetric.io.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.InvalidRetryException;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.writer.IgnoreBatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataProcessorTest {
    private static final String PROCESSOR_NAME = "test-processor";
    private IDataReader reader;
    private IDataWriter writer;
    private IDataProcessorListener listener;
    private DataProcessor processor;
    private DataContext context;
    private Batch batch;

    @BeforeEach
    void setUp() {
        reader = mock(IDataReader.class);
        writer = mock(IDataWriter.class);
        listener = mock(IDataProcessorListener.class);
        context = new DataContext();
        batch = new Batch(BatchType.LOAD, 1L, "default", BinaryEncoding.NONE, "source-1", "target-1", false);
        batch.setComplete(true);
        processor = new DataProcessor(reader, writer, listener, PROCESSOR_NAME);
        when(reader.nextBatch()).thenReturn(batch, (Batch) null);
        when(listener.beforeBatchStarted(any(DataContext.class))).thenReturn(true);
    }

    @Test
    void testProcess_withEmptyBatch() {
        processor.process(context);
        assertSame(reader, context.getReader());
        assertSame(batch, context.getBatch());
        verify(reader).open(context);
        verify(writer).open(context);
        verify(writer).start(batch);
        verify(listener).afterBatchStarted(context);
        verify(listener).beforeBatchEnd(context);
        verify(writer).end(batch, false);
        verify(listener).batchSuccessful(context);
        verify(writer).close();
        verify(reader).close();
    }

    @Test
    void testProcess_withNoContext() {
        processor.process();
        verify(writer).start(batch);
        verify(writer).end(batch, false);
    }

    @Test
    void testProcess_withDataRow() {
        CsvData data = new CsvData(DataEventType.INSERT, new String[] { "1" });
        when(reader.nextData()).thenReturn(data, (CsvData) null);
        processor.process(context);
        verify(writer).write(data);
        verify(listener).dataRowProcessed();
        assertEquals(1L, batch.getLineCount());
    }

    @Test
    void testProcess_withTable() {
        Table table = new Table("item");
        CsvData data = new CsvData(DataEventType.INSERT, new String[] { "1" });
        when(reader.nextRelation()).thenReturn(table, (Relation) null);
        when(writer.start(table)).thenReturn(true);
        when(reader.nextData()).thenReturn(null, data, null);
        processor.process(context);
        verify(writer).start(table);
        verify(writer).write(data);
        verify(writer).end(table);
    }

    @Test
    void testProcess_whenWriterIgnoresTheBatchWhileWritingData() {
        CsvData data = new CsvData(DataEventType.INSERT, new String[] { "1" });
        when(reader.nextData()).thenReturn(data, (CsvData) null);
        doThrow(new IgnoreBatchException()).when(writer).write(data);
        assertThrows(IgnoreBatchException.class, () -> processor.process(context));
        verify(listener).batchInError(eq(context), any(IgnoreBatchException.class));
    }

    @Test
    void testProcess_whenWriterIgnoresTheBatchWhileStartingTable() {
        Table table = new Table("item");
        when(reader.nextRelation()).thenReturn(table, (Relation) null);
        doThrow(new IgnoreBatchException()).when(writer).start(table);
        processor.process(context);
        verify(writer, never()).end(table);
        verify(writer).end(batch, false);
    }

    @Test
    void testProcess_withoutListener() {
        DataProcessor unlistenedProcessor = new DataProcessor(reader, writer, PROCESSOR_NAME);
        unlistenedProcessor.process(context);
        verify(writer).start(batch);
        verify(writer).end(batch, false);
    }

    @Test
    void testProcess_withDataReaderAndWriterSetAfterConstruction() {
        DataProcessor emptyProcessor = new DataProcessor();
        emptyProcessor.setDataReader(reader);
        emptyProcessor.setDefaultDataWriter(writer);
        emptyProcessor.setListener(listener);
        emptyProcessor.process(context);
        verify(writer).end(batch, false);
        verify(listener).batchSuccessful(context);
    }

    @Test
    void testProcess_whenListenerRejectsBatch() {
        when(listener.beforeBatchStarted(any(DataContext.class))).thenReturn(false);
        processor.process(context);
        verify(writer, never()).open(any(DataContext.class));
        verify(writer, never()).start(batch);
        verify(listener, never()).batchSuccessful(context);
        verify(reader).close();
    }

    @Test
    void testProcess_whenNoDataWriterIsAvailable() {
        processor.setDefaultDataWriter(null);
        processor.process(context);
        verify(listener).beforeBatchStarted(context);
        verify(listener, never()).batchSuccessful(context);
    }

    @Test
    void testProcess_whenNoBatchIsAvailable() {
        when(reader.nextBatch()).thenReturn(null);
        processor.process(context);
        verify(writer, never()).open(any(DataContext.class));
        verify(reader).close();
    }

    @Test
    void testProcess_throwsWhenBatchIsNotComplete() {
        batch.setComplete(false);
        assertThrows(ProtocolException.class, () -> processor.process(context));
        verify(writer).end(batch, true);
        verify(listener).batchInError(eq(context), any(ProtocolException.class));
        verify(writer).close();
        verify(reader).close();
    }

    @Test
    void testProcess_throwsWhenBatchIsAnInvalidRetry() {
        batch.setInvalidRetry(true);
        assertThrows(InvalidRetryException.class, () -> processor.process(context));
        verify(listener).batchInError(eq(context), any(InvalidRetryException.class));
    }

    @Test
    void testProcess_recordsLastErrorWhenWriterFails() {
        IllegalStateException failure = new IllegalStateException("writer failed");
        doThrow(failure).when(writer).start(batch);
        assertThrows(IllegalStateException.class, () -> processor.process(context));
        assertSame(failure, context.getLastError());
    }

    @Test
    void testRethrow_withRuntimeException() {
        IllegalStateException failure = new IllegalStateException("failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> processor.rethrow(failure)));
    }

    @Test
    void testRethrow_withCheckedException() {
        Exception failure = new Exception("failed");
        assertSame(failure, assertThrows(RuntimeException.class, () -> processor.rethrow(failure)).getCause());
    }

    @Test
    void testClose_whenResourceThrows() {
        IDataResource resource = mock(IDataResource.class);
        doThrow(new IllegalStateException("failed")).when(resource).close();
        assertDoesNotThrow(() -> processor.close(resource));
    }

    @Test
    void testClose_withNullResource() {
        assertDoesNotThrow(() -> processor.close(null));
    }
}
