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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.ExtractRequest.ExtractStatus;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.DataExtractorService.ExtractMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * SYM-7915. Covers the logic added to reconcile an extract request marked {@code OK} whose batches are still {@code RQ}:
 * {@link DataExtractorService#isExtractRequestComplete}, the two range predicates, the live-owner guard added to answer the "clustered servers overtaking each
 * other" review concern, and {@link DataExtractorService#recoverStuckExtractRequests}.
 */
class DataExtractorServiceRecoveryTest {
    protected ISymmetricEngine engine;
    protected IParameterService parameterService;
    protected ISymmetricDialect symmetricDialect;
    protected IDatabasePlatform platform;
    protected ISqlTemplate sqlTemplate;
    protected ISqlTemplate sqlTemplateDirty;
    protected IOutgoingBatchService outgoingBatchService;
    protected INodeCommunicationService nodeCommunicationService;

    @BeforeEach
    void setup() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        outgoingBatchService = mock(IOutgoingBatchService.class);
        nodeCommunicationService = mock(INodeCommunicationService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(engine.getNodeCommunicationService()).thenReturn(nodeCommunicationService);
        when(engine.getNodeId()).thenReturn("001");
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(symmetricDialect.getSqlTypeForIds()).thenReturn(java.sql.Types.VARCHAR);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        // AbstractSqlMap.putSql runs every statement through scrubSql; a bare mock returns null for every
        // entry, which makes every getSql(...) lookup in the service return null.
        when(platform.scrubSql(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private DataExtractorService createSpyService() {
        return spy(new DataExtractorService(engine));
    }

    private ExtractRequest newRequest(long requestId, String nodeId, long start, long end, long rows, long extractedRows,
            long parentRequestId, String queue, Integer extractThreadId) {
        ExtractRequest request = new ExtractRequest();
        request.setRequestId(requestId);
        request.setNodeId(nodeId);
        request.setStartBatchId(start);
        request.setEndBatchId(end);
        request.setRows(rows);
        request.setExtractedRows(extractedRows);
        request.setParentRequestId(parentRequestId);
        request.setQueue(queue);
        request.setExtractThreadId(extractThreadId);
        request.setTableName("test_table");
        request.setLoadId(1);
        return request;
    }

    private NodeCommunication lock(boolean locked) {
        NodeCommunication communication = new NodeCommunication();
        if (locked) {
            communication.setLockTime(new java.util.Date());
        }
        return communication;
    }
    // ---- isExtractRequestComplete --------------------------------------------------------------------

    @Test
    void extractOnlyModeIsNeverComplete() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 1, 5, 10, 10, 0, "reload", 0);
        assertFalse(service.isExtractRequestComplete(request, ExtractMode.EXTRACT_ONLY));
    }

    @Test
    void completeWhenNoRequestedBatchesRemain() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 1, 5, 10, 10, 0, "reload", 0);
        doReturn(false).when(service).hasRequestedBatchesInRange(request);
        assertTrue(service.isExtractRequestComplete(request, ExtractMode.FOR_SYM_CLIENT));
    }

    @Test
    void notCompleteWhileRequestedBatchesRemain() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 1, 5, 10, 10, 0, "reload", 0);
        doReturn(true).when(service).hasRequestedBatchesInRange(request);
        assertFalse(service.isExtractRequestComplete(request, ExtractMode.FOR_SYM_CLIENT));
    }
    // ---- range predicates -----------------------------------------------------------------------------

    @Test
    void hasRequestedBatchesInRangeBindsNodeIdAndBatchRange() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 100, 200, 10, 0, 0, "reload", 0);
        when(sqlTemplate.query(eq(service.getSql("selectRequestedBatchesForExtractRequestSql")), eq(1), any(),
                any(Object[].class), any(int[].class))).thenReturn(Collections.singletonList(150L));
        assertTrue(service.hasRequestedBatchesInRange(request));
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(sqlTemplate).query(eq(service.getSql("selectRequestedBatchesForExtractRequestSql")), eq(1), any(),
                params.capture(), any(int[].class));
        assertEquals(Arrays.asList("001", 100L, 200L), Arrays.asList(params.getValue()));
    }

    @Test
    void noRequestedBatchesInRangeWhenQueryReturnsEmpty() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 100, 200, 10, 0, 0, "reload", 0);
        when(sqlTemplate.query(eq(service.getSql("selectRequestedBatchesForExtractRequestSql")), eq(1), any(),
                any(Object[].class), any(int[].class))).thenReturn(Collections.emptyList());
        assertFalse(service.hasRequestedBatchesInRange(request));
    }

    @Test
    void hasDeliveredBatchesInRangeReflectsQueryResult() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 100, 200, 10, 0, 0, "reload", 0);
        when(sqlTemplate.query(eq(service.getSql("selectDeliveredBatchesForExtractRequestSql")), eq(1), any(),
                any(Object[].class), any(int[].class))).thenReturn(Collections.singletonList(180L));
        assertTrue(service.hasDeliveredBatchesInRange(request));
    }
    // ---- isExtractQueueLocked --------------------------------------------------------------------------

    @Test
    void extractQueueLockedWhenNodeCommunicationRowHasLockTime() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 3);
        when(nodeCommunicationService.find("001", "reload!3", CommunicationType.EXTRACT)).thenReturn(lock(true));
        assertTrue(service.isExtractQueueLocked(request));
    }

    @Test
    void extractQueueNotLockedWhenNodeCommunicationRowHasNoLockTime() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 3);
        when(nodeCommunicationService.find("001", "reload!3", CommunicationType.EXTRACT)).thenReturn(lock(false));
        assertFalse(service.isExtractQueueLocked(request));
    }
    // ---- recoverStuckExtractRequests -------------------------------------------------------------------

    @Test
    void throttledScanDoesNotQueryTheDatabase() {
        DataExtractorService service = createSpyService();
        service.lastStuckExtractRequestCheckMs = System.currentTimeMillis();
        when(parameterService.getLong(eq(ParameterConstants.INITIAL_LOAD_EXTRACT_TIMEOUT_MS), anyLong())).thenReturn(7200000L);
        assertEquals(0, service.recoverStuckExtractRequests(false));
        verifyNoInteractions(sqlTemplateDirty);
    }

    @Test
    void forceBypassesTheThrottle() {
        DataExtractorService service = createSpyService();
        service.lastStuckExtractRequestCheckMs = System.currentTimeMillis();
        when(parameterService.getLong(eq(ParameterConstants.INITIAL_LOAD_EXTRACT_TIMEOUT_MS), anyLong())).thenReturn(7200000L);
        when(sqlTemplateDirty.query(eq(service.getSql("selectStuckExtractRequestsSql")), any(org.jumpmind.db.sql.ISqlRowMapper.class), anyString(), anyString(),
                any(java.util.Date.class)))
                .thenReturn(Collections.emptyList());
        assertEquals(0, service.recoverStuckExtractRequests(true));
        verify(sqlTemplateDirty).query(eq(service.getSql("selectStuckExtractRequestsSql")), any(org.jumpmind.db.sql.ISqlRowMapper.class), anyString(),
                anyString(), any(java.util.Date.class));
    }

    @Test
    void liveQueueLockSkipsRestartWithoutForce() {
        DataExtractorService service = createSpyService();
        when(parameterService.getLong(eq(ParameterConstants.INITIAL_LOAD_EXTRACT_TIMEOUT_MS), anyLong())).thenReturn(7200000L);
        ExtractRequest stuck = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 0);
        when(sqlTemplateDirty.query(eq(service.getSql("selectStuckExtractRequestsSql")), any(org.jumpmind.db.sql.ISqlRowMapper.class), anyString(), anyString(),
                any(java.util.Date.class)))
                .thenReturn(Collections.singletonList(stuck));
        doReturn(true).when(service).isExtractQueueLocked(stuck);
        doNothing().when(service).restartExtractRequest(any(ExtractRequest.class));
        assertEquals(0, service.recoverStuckExtractRequests(false));
        verify(service, never()).restartExtractRequest(any(ExtractRequest.class));
        verify(service, never()).hasDeliveredBatchesInRange(any());
    }

    @Test
    void deliveredBatchesAreReportedNotRestartedWithoutForce() {
        DataExtractorService service = createSpyService();
        when(parameterService.getLong(eq(ParameterConstants.INITIAL_LOAD_EXTRACT_TIMEOUT_MS), anyLong())).thenReturn(7200000L);
        ExtractRequest stuck = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 0);
        when(sqlTemplateDirty.query(eq(service.getSql("selectStuckExtractRequestsSql")), any(org.jumpmind.db.sql.ISqlRowMapper.class), anyString(), anyString(),
                any(java.util.Date.class)))
                .thenReturn(Collections.singletonList(stuck));
        doReturn(false).when(service).isExtractQueueLocked(stuck);
        doReturn(true).when(service).hasDeliveredBatchesInRange(stuck);
        doNothing().when(service).restartExtractRequest(any(ExtractRequest.class));
        assertEquals(0, service.recoverStuckExtractRequests(false));
        verify(service, never()).restartExtractRequest(any(ExtractRequest.class));
    }

    @Test
    void forceRestartsEvenWithDeliveredBatches() {
        DataExtractorService service = createSpyService();
        when(parameterService.getLong(eq(ParameterConstants.INITIAL_LOAD_EXTRACT_TIMEOUT_MS), anyLong())).thenReturn(7200000L);
        ExtractRequest stuck = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 0);
        when(sqlTemplateDirty.query(eq(service.getSql("selectStuckExtractRequestsSql")), any(org.jumpmind.db.sql.ISqlRowMapper.class), anyString(), anyString(),
                any(java.util.Date.class)))
                .thenReturn(Collections.singletonList(stuck));
        doReturn(true).when(service).hasDeliveredBatchesInRange(stuck);
        doNothing().when(service).restartExtractRequest(any(ExtractRequest.class));
        assertEquals(1, service.recoverStuckExtractRequests(true));
        verify(service).restartExtractRequest(stuck);
        // force skips the live-queue check entirely, so it is never even asked
        verify(service, never()).isExtractQueueLocked(any());
    }

    @Test
    void multipleStuckRequestsAreAllRestarted() {
        DataExtractorService service = createSpyService();
        when(parameterService.getLong(eq(ParameterConstants.INITIAL_LOAD_EXTRACT_TIMEOUT_MS), anyLong())).thenReturn(7200000L);
        ExtractRequest first = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 0);
        ExtractRequest second = newRequest(2, "001", 6, 10, 10, 0, 0, "reload", 1);
        when(sqlTemplateDirty.query(eq(service.getSql("selectStuckExtractRequestsSql")), any(org.jumpmind.db.sql.ISqlRowMapper.class), anyString(), anyString(),
                any(java.util.Date.class)))
                .thenReturn(Arrays.asList(first, second));
        doReturn(false).when(service).isExtractQueueLocked(any());
        doReturn(false).when(service).hasDeliveredBatchesInRange(any());
        doNothing().when(service).restartExtractRequest(any(ExtractRequest.class));
        assertEquals(2, service.recoverStuckExtractRequests(false));
        verify(service, times(2)).restartExtractRequest(any(ExtractRequest.class));
    }
    // ---- updateExtractRequestOnBatchFinished, the extractOutgoingBatch integration point -----------------

    @Test
    void completingTheRequestWritesOkAndChecksDeferredForeignKeys() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 0);
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(5);
        batch.setExtractRowCount(10);
        batch.setExtractMillis(250);
        Node targetNode = new Node();
        doReturn(true).when(service).isExtractRequestComplete(request, ExtractMode.FOR_SYM_CLIENT);
        doNothing().when(service).checkSendDeferredForeignKeys(anyLong(), any());
        service.updateExtractRequestOnBatchFinished(request, batch, ExtractMode.FOR_SYM_CLIENT, targetNode);
        verify(sqlTemplate).update(eq(service.getSql("updateExtractRequestStatus")), eq(ExtractStatus.OK.name()), any(),
                eq(10L), eq(250L), eq(1L));
        verify(service).checkSendDeferredForeignKeys(request.getLoadId(), targetNode);
        verify(sqlTemplate, never()).update(eq(service.getSql("updateExtractRequestExtractedStats")), any(), any(), any(), any());
    }

    @Test
    void incompleteRequestAccumulatesStatisticsInsteadOfWritingOk() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 0);
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(3);
        batch.setExtractRowCount(4);
        batch.setExtractMillis(80);
        Node targetNode = new Node();
        doReturn(false).when(service).isExtractRequestComplete(request, ExtractMode.FOR_SYM_CLIENT);
        service.updateExtractRequestOnBatchFinished(request, batch, ExtractMode.FOR_SYM_CLIENT, targetNode);
        verify(sqlTemplate).update(eq(service.getSql("updateExtractRequestExtractedStats")), eq(4L), eq(80L), any(), eq(1L));
        verify(sqlTemplate, never()).update(eq(service.getSql("updateExtractRequestStatus")), any(), any(), any(), any(), any());
        verify(service, never()).checkSendDeferredForeignKeys(anyLong(), any());
    }
    // ---- FileSyncExtractorService override --------------------------------------------------------------

    @Test
    void fileSyncExtractorServiceNeverRecoversAnyRequest() {
        // Not applicable to file sync, for the same reason as updateExtractRequestsForThreading(): it inherits
        // queueWork and would otherwise scan and restart ordinary data extract requests.
        FileSyncExtractorService service = new FileSyncExtractorService(engine);
        assertEquals(0, service.recoverStuckExtractRequests(false));
        assertEquals(0, service.recoverStuckExtractRequests(true));
    }
    // ---- describeStuckRequest --------------------------------------------------------------------------

    @Test
    void describeStuckRequestIncludesTheIdentifyingFields() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(7, "001", 1, 5, 100, 40, 0, "reload", 0);
        String description = service.describeStuckRequest(request);
        assertTrue(description.contains("7"));
        assertTrue(description.contains("001"));
        assertTrue(description.contains("test_table"));
        assertTrue(description.contains("40"));
        assertTrue(description.contains("100"));
    }
    // ---- restartExtractRequest(ExtractRequest) delegation ------------------------------------------------

    @Test
    void restartLoadsBatchRangeAndChildRequestsForAStandaloneRequest() {
        DataExtractorService service = createSpyService();
        ExtractRequest request = newRequest(1, "001", 1, 5, 10, 0, 0, "reload", 0);
        List<ExtractRequest> children = new ArrayList<>();
        children.add(newRequest(2, "002", 1, 5, 10, 0, 1, "reload", 0));
        org.jumpmind.symmetric.model.OutgoingBatches batches = new org.jumpmind.symmetric.model.OutgoingBatches();
        when(outgoingBatchService.getOutgoingBatchRange(1, 5)).thenReturn(batches);
        doReturn(children).when(service).getExtractChildRequestsForNode(any(ExtractRequest.class));
        doNothing().when(service).restartExtractRequest(any(), eq(request), eq(children));
        service.restartExtractRequest(request);
        verify(service).restartExtractRequest(batches.getBatches(), request, children);
    }

    @Test
    void restartDoesNotLookUpChildRequestsForAChildRequest() {
        DataExtractorService service = createSpyService();
        ExtractRequest child = newRequest(2, "002", 1, 5, 10, 0, 1, "reload", 0);
        org.jumpmind.symmetric.model.OutgoingBatches batches = new org.jumpmind.symmetric.model.OutgoingBatches();
        when(outgoingBatchService.getOutgoingBatchRange(1, 5)).thenReturn(batches);
        doNothing().when(service).restartExtractRequest(any(), eq(child), eq(null));
        service.restartExtractRequest(child);
        verify(service, never()).getExtractChildRequestsForNode(any(ExtractRequest.class));
        verify(service).restartExtractRequest(batches.getBatches(), child, null);
    }
}
