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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.ext.ISyncEventListener;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataReader;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingError;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.util.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManageIncomingBatchListenerTest {
    private static final String TABLE_PREFIX = "sym";
    private ISymmetricEngine engine;
    private IIncomingBatchService incomingBatchService;
    private IDataLoaderService dataLoaderService;
    private ISyncEventListener syncEventListener;
    private ISqlTransaction writerTransaction;
    private DataContext context;
    private Batch batch;
    private IncomingBatch incomingBatch;
    private ManageIncomingBatchListener listener;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        incomingBatchService = mock(IIncomingBatchService.class);
        dataLoaderService = mock(IDataLoaderService.class);
        syncEventListener = mock(ISyncEventListener.class);
        writerTransaction = mock(ISqlTransaction.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(syncEventListener));
        when(engine.getParameterService()).thenReturn(mock(IParameterService.class));
        when(engine.getSymmetricDialect()).thenReturn(mock(ISymmetricDialect.class));
        when(engine.getDataLoaderService()).thenReturn(dataLoaderService);
        when(engine.getIncomingBatchService()).thenReturn(incomingBatchService);
        when(engine.getStatisticManager()).thenReturn(mock(IStatisticManager.class));
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getTablePrefix()).thenReturn(TABLE_PREFIX);
        batch = new Batch();
        batch.setBatchId(5);
        context = mock(DataContext.class);
        IDataReader reader = mock(IDataReader.class);
        IDataWriter writer = mock(IDataWriter.class);
        Map<Batch, Statistics> readerStatistics = new HashMap<>();
        readerStatistics.put(batch, new Statistics());
        Map<Batch, Statistics> writerStatistics = new HashMap<>();
        writerStatistics.put(batch, new Statistics());
        when(reader.getStatistics()).thenReturn(readerStatistics);
        when(writer.getStatistics()).thenReturn(writerStatistics);
        when(context.getBatch()).thenReturn(batch);
        when(context.getReader()).thenReturn(reader);
        when(context.getWriter()).thenReturn(writer);
        when(context.findSymmetricTransaction(TABLE_PREFIX)).thenReturn(writerTransaction);
        incomingBatch = new IncomingBatch();
        incomingBatch.setBatchId(5);
        incomingBatch.setNodeId("source");
        incomingBatch.setStatus(Status.LD);
        listener = new ManageIncomingBatchListener(new ProcessInfo(), engine);
        listener.currentBatch = incomingBatch;
    }

    private void failOnTable(String tableName) {
        Table table = new Table(tableName, new Column("ID", true));
        when(context.getRelation()).thenReturn(table);
        when(context.getData()).thenReturn(new CsvData(DataEventType.INSERT));
    }

    @Test
    void batchSuccessful_notifiesSyncEventListenersWithOkBatchOutsideTransaction() {
        listener.batchSuccessful(context);
        verify(syncEventListener).incomingBatchEnded(isNull(), any(IncomingBatch.class), isNull());
        assertEquals(Status.OK, incomingBatch.getStatus());
    }

    @Test
    void batchSuccessful_statusUpdateFails_doesNotNotifySyncEventListeners() {
        when(incomingBatchService.isRecordOkBatchesEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("update failed")).when(incomingBatchService).updateIncomingBatch(incomingBatch);
        assertThrows(IllegalStateException.class, () -> listener.batchSuccessful(context));
        verify(syncEventListener, never()).incomingBatchEnded(any(), any(), any());
        assertEquals(Status.LD, incomingBatch.getStatus());
    }

    @Test
    void batchInError_failingRow_notifiesSyncEventListenersWithWriterTransactionAndFailingTable() {
        failOnTable("ORDERS");
        listener.batchInError(context, new IllegalStateException("load failed"));
        ArgumentCaptor<IncomingError> error = ArgumentCaptor.forClass(IncomingError.class);
        verify(syncEventListener).incomingBatchEnded(any(ISqlTransaction.class), any(IncomingBatch.class), error.capture());
        verify(syncEventListener).incomingBatchEnded(writerTransaction, incomingBatch, error.getValue());
        assertEquals("ORDERS", error.getValue().getTargetTableName());
        assertEquals(Status.ER, incomingBatch.getStatus());
    }

    @Test
    void batchInError_failingRowUnknown_notifiesSyncEventListenersWithoutError() {
        listener.batchInError(context, new IllegalStateException("load failed"));
        verify(syncEventListener).incomingBatchEnded(writerTransaction, incomingBatch, null);
    }

    @Test
    void batchInError_suppressedError_doesNotNotifySyncEventListeners() {
        batch.setLineCount(7);
        failOnTable("ORDERS");
        listener.batchInError(context, new ProtocolException("bad protocol"));
        verify(syncEventListener, never()).incomingBatchEnded(any(), any(), any());
        assertEquals(Status.LD, incomingBatch.getStatus());
    }

    @Test
    void batchInError_failingSyncEventListener_stillRecordsBatchStatus() {
        doThrow(new IllegalStateException("listener failure")).when(syncEventListener).incomingBatchEnded(any(), any(), any());
        listener.batchInError(context, new IllegalStateException("load failed"));
        verify(incomingBatchService).insertIncomingBatch(writerTransaction, incomingBatch);
        assertSame(incomingBatch, listener.currentBatch);
    }
}
