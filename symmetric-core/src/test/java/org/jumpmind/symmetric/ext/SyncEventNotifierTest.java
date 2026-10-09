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
package org.jumpmind.symmetric.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingError;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SyncEventNotifierTest {
    private ISymmetricEngine engine;
    private IExtensionService extensionService;
    private IParameterService parameterService;
    private ISqlTransaction transaction;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger notifierLogger;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        extensionService = mock(IExtensionService.class);
        transaction = mock(ISqlTransaction.class);
        parameterService = mock(IParameterService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.is(ParameterConstants.SYNC_EVENT_ENABLED)).thenReturn(true);
        notifierLogger = (Logger) LoggerFactory.getLogger(SyncEventNotifier.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        notifierLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        notifierLogger.detachAppender(logAppender);
    }

    @Test
    void testIsEnabled_whenParameterIsTrue() {
        assertTrue(SyncEventNotifier.isEnabled(engine));
    }

    @Test
    void testIsEnabled_whenParameterIsFalse() {
        when(parameterService.is(ParameterConstants.SYNC_EVENT_ENABLED)).thenReturn(false);
        assertFalse(SyncEventNotifier.isEnabled(engine));
    }

    @Test
    void testNotifyListeners_whenParameterIsFalse_doesNotCallAnyListener() {
        when(parameterService.is(ParameterConstants.SYNC_EVENT_ENABLED)).thenReturn(false);
        ISyncEventListener listener = mock(ISyncEventListener.class);
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(listener));
        TableReloadStatus status = new TableReloadStatus();
        SyncEventNotifier.incomingBatchEnded(engine, transaction, new IncomingBatch(), new IncomingError());
        SyncEventNotifier.outgoingBatchEnded(engine, transaction, new OutgoingBatch());
        SyncEventNotifier.loadStarted(engine, null, status, "target");
        SyncEventNotifier.loadTerminated(engine, null, status, "target");
        verifyNoInteractions(listener);
    }

    @Test
    void testIncomingBatchEnded_withMultipleListeners() {
        ISyncEventListener first = mock(ISyncEventListener.class);
        ISyncEventListener second = mock(ISyncEventListener.class);
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(first, second));
        IncomingBatch batch = new IncomingBatch();
        IncomingError error = new IncomingError();
        SyncEventNotifier.incomingBatchEnded(engine, transaction, batch, error);
        verify(first).incomingBatchEnded(transaction, batch, error);
        verify(second).incomingBatchEnded(transaction, batch, error);
    }

    @Test
    void testOutgoingBatchEnded_withListener() {
        ISyncEventListener listener = mock(ISyncEventListener.class);
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(listener));
        OutgoingBatch batch = new OutgoingBatch();
        SyncEventNotifier.outgoingBatchEnded(engine, transaction, batch);
        verify(listener).outgoingBatchEnded(transaction, batch);
    }

    @Test
    void testLoadStarted_withListener() {
        ISyncEventListener listener = mock(ISyncEventListener.class);
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(listener));
        TableReloadStatus status = new TableReloadStatus();
        SyncEventNotifier.loadStarted(engine, null, status, "target");
        verify(listener).loadStarted(null, status, "target");
    }

    @Test
    void testLoadStarted_withFailingListenerNotifiesLaterListeners() {
        ISyncEventListener failing = mock(ISyncEventListener.class);
        ISyncEventListener later = mock(ISyncEventListener.class);
        TableReloadStatus status = new TableReloadStatus();
        doThrow(new IllegalStateException("listener failure")).when(failing).loadStarted(null, status, "target");
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(failing, later));
        SyncEventNotifier.loadStarted(engine, null, status, "target");
        verify(later).loadStarted(null, status, "target");
    }

    @Test
    void testLoadStarted_withListenerThrowingError_notifiesLaterListeners() {
        ISyncEventListener failing = mock(ISyncEventListener.class);
        ISyncEventListener later = mock(ISyncEventListener.class);
        TableReloadStatus status = new TableReloadStatus();
        doThrow(new NoClassDefFoundError("missing listener dependency")).when(failing).loadStarted(null, status, "target");
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(failing, later));
        SyncEventNotifier.loadStarted(engine, null, status, "target");
        verify(later).loadStarted(null, status, "target");
        assertEquals(1, logAppender.list.size());
        assertEquals(Level.ERROR, logAppender.list.get(0).getLevel());
    }

    @Test
    void testLoadTerminated_withListener() {
        ISyncEventListener listener = mock(ISyncEventListener.class);
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(listener));
        TableReloadStatus status = new TableReloadStatus();
        SyncEventNotifier.loadTerminated(engine, null, status, "target");
        verify(listener).loadTerminated(null, status, "target");
    }

    @Test
    void testOutgoingBatchEnded_withFailingListenerNotifiesLaterListeners() {
        ISyncEventListener failing = mock(ISyncEventListener.class);
        ISyncEventListener later = mock(ISyncEventListener.class);
        OutgoingBatch batch = new OutgoingBatch();
        doThrow(new IllegalStateException("listener failure")).when(failing).outgoingBatchEnded(transaction, batch);
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(failing, later));
        SyncEventNotifier.outgoingBatchEnded(engine, transaction, batch);
        verify(later).outgoingBatchEnded(transaction, batch);
    }

    @Test
    void testOutgoingBatchEnded_withFailingListenerLogsError() {
        ISyncEventListener failing = mock(ISyncEventListener.class);
        OutgoingBatch batch = new OutgoingBatch();
        doThrow(new IllegalStateException("listener failure")).when(failing).outgoingBatchEnded(transaction, batch);
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of(failing));
        SyncEventNotifier.outgoingBatchEnded(engine, transaction, batch);
        assertEquals(1, logAppender.list.size());
        assertEquals(Level.ERROR, logAppender.list.get(0).getLevel());
        assertTrue(logAppender.list.get(0).getFormattedMessage().startsWith("Sync event listener "));
    }

    @Test
    void testIncomingBatchEnded_withNoListeners() {
        when(extensionService.getExtensionPointList(ISyncEventListener.class)).thenReturn(List.of());
        SyncEventNotifier.incomingBatchEnded(engine, transaction, new IncomingBatch(), null);
        assertTrue(logAppender.list.isEmpty());
    }
}
