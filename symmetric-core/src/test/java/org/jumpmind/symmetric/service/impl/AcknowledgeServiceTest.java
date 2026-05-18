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
 * software distributed under the LICENSE is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.NumberMapper;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ErrorConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.RegistrationRequest;
import org.jumpmind.symmetric.model.RegistrationRequest.RegistrationStatus;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AcknowledgeServiceTest {
    private static final String NODE_ID = "node-001";
    private static final long NORMAL_BATCH_ID = 100L;
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ISymmetricDialect symmetricDialect;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;
    private ISqlTemplate sqlTemplateDirty;
    private ISqlTransaction sqlTransaction;
    private IRegistrationService registrationService;
    private IOutgoingBatchService outgoingBatchService;
    private INodeService nodeService;
    private IStatisticManager statisticManager;
    private IStagingManager stagingManager;
    private IExtensionService extensionService;
    private IDataExtractorService dataExtractorService;
    private IConfigurationService configurationService;
    private AcknowledgeService service;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        sqlTransaction = mock(ISqlTransaction.class);
        registrationService = mock(IRegistrationService.class);
        outgoingBatchService = mock(IOutgoingBatchService.class);
        nodeService = mock(INodeService.class);
        statisticManager = mock(IStatisticManager.class);
        stagingManager = mock(IStagingManager.class);
        extensionService = mock(IExtensionService.class);
        dataExtractorService = mock(IDataExtractorService.class);
        configurationService = mock(IConfigurationService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getStagingManager()).thenReturn(stagingManager);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getDataExtractorService()).thenReturn(dataExtractorService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getChannel(anyString())).thenReturn(null);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.is(ParameterConstants.ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED, true)).thenReturn(true);
        when(extensionService.getExtensionPointList(any())).thenReturn(Collections.emptyList());
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        service = new AcknowledgeService(engine);
    }

    private BatchAck virtualRegistrationBatch(boolean ok) {
        BatchAck batch = ok
                ? new BatchAck(Constants.VIRTUAL_BATCH_FOR_REGISTRATION)
                : new BatchAck(Constants.VIRTUAL_BATCH_FOR_REGISTRATION, 1L);
        batch.setNodeId(NODE_ID);
        return batch;
    }

    private BatchAck normalBatch(long batchId, boolean ok) {
        BatchAck batch = ok ? new BatchAck(batchId) : new BatchAck(batchId, 0L);
        batch.setNodeId(NODE_ID);
        return batch;
    }

    private OutgoingBatch outgoingBatch(Status status) {
        OutgoingBatch ob = new OutgoingBatch(NODE_ID, "default", status);
        ob.setBatchId(NORMAL_BATCH_ID);
        return ob;
    }

    @Test
    void registrationBatchOkMarksNodeRegistered() {
        BatchAck batch = virtualRegistrationBatch(true);
        service.ack(batch);
        verify(registrationService).markNodeAsRegistered(NODE_ID);
    }

    @Test
    void registrationBatchFailWithSqlCodeLogsWarn() {
        BatchAck batch = virtualRegistrationBatch(false);
        batch.setSqlCode(1062);
        batch.setSqlMessage("Duplicate entry");
        service.ack(batch);
        verify(registrationService, never()).markNodeAsRegistered(anyString());
    }

    @Test
    void registrationBatchFailFetchesLatestRequestWhenNodeFound() {
        BatchAck batch = virtualRegistrationBatch(false);
        batch.setSqlCode(1);
        Node node = new Node();
        node.setNodeGroupId("client");
        node.setExternalId("ext-001");
        when(nodeService.findNode(NODE_ID)).thenReturn(node);
        service.ack(batch);
        verify(registrationService).getLatestRegistrationRequest("client", "ext-001");
    }

    @Test
    void registrationBatchFailSetsRequestStatusToEr() {
        BatchAck batch = virtualRegistrationBatch(false);
        batch.setSqlCode(1);
        Node node = new Node();
        node.setNodeGroupId("client");
        node.setExternalId("ext-001");
        RegistrationRequest request = new RegistrationRequest();
        request.setStatus(RegistrationStatus.RQ);
        when(nodeService.findNode(NODE_ID)).thenReturn(node);
        when(registrationService.getLatestRegistrationRequest("client", "ext-001")).thenReturn(request);
        service.ack(batch);
        verify(registrationService).updateRegistrationRequest(request);
        assertSame(request.getStatus(), RegistrationStatus.ER);
    }

    @Test
    void normalBatchLookupOutgoingBatch() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, true);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        verify(outgoingBatchService).findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID);
    }

    @Test
    void outgoingBatchIgnoredStatusAckedOkBecomesOk() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, true);
        OutgoingBatch ob = outgoingBatch(Status.IG);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        verify(outgoingBatchService).updateOutgoingBatch(any(), any());
        assertSame(ob.getStatus(), Status.OK);
    }

    @Test
    void outgoingBatchAlreadyOkOverridesIncomingErrorToIgnore() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setSqlCode(1);
        OutgoingBatch ob = outgoingBatch(Status.OK);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        assertSame(ob.getStatus(), Status.IG);
    }

    @Test
    void ignoredBatchIncrementsIgnoreCount() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, true);
        batch.setIgnored(true);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        long ignoreCountBefore = ob.getIgnoreCount();
        service.ack(batch);
        assertTrue(ob.getIgnoreCount() > ignoreCountBefore);
    }

    @Test
    void okStatusClearsFailedDataIdAndLineNumber() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, true);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setFailedDataId(42L);
        ob.setFailedLineNumber(3L);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        assertEquals(0L, ob.getFailedDataId());
        assertEquals(0L, ob.getFailedLineNumber());
    }

    @Test
    void errorBatchWithLoadFlagSetsIsNewErrorFromSentCount() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setErrorLine(1);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setLoadFlag(true);
        ob.setSentCount(1);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        verify(statisticManager).incrementDataLoadedOutgoingErrors(any(), anyLong());
    }

    @Test
    void errorBatchQueriesDataIdsWhenErrorLineChanged() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setErrorLine(1);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setFailedLineNumber(0);
        List<Number> ids = Arrays.asList(101L, 202L);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        when(sqlTemplateDirty.query(anyString(), any(NumberMapper.class), anyLong())).thenReturn(ids);
        service.ack(batch);
        verify(sqlTemplateDirty).query(anyString(), any(NumberMapper.class), anyLong());
        assertEquals(101L, ob.getFailedDataId());
    }

    @Test
    void newErrorIncrementsOutgoingErrorStats() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setErrorLine(1);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setFailedLineNumber(0);
        List<Number> ids = Arrays.asList(101L);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        when(sqlTemplateDirty.query(anyString(), any(NumberMapper.class), anyLong())).thenReturn(ids);
        service.ack(batch);
        verify(statisticManager).incrementDataLoadedOutgoingErrors(any(), anyLong());
    }

    @Test
    void fkViolationTriggersAutoResolveWhenEnabled() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setErrorLine(1);
        batch.setSqlCode(ErrorConstants.FK_VIOLATION_CODE);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setSqlCode(ErrorConstants.FK_VIOLATION_CODE);
        ob.setFailedLineNumber(0);
        List<Number> ids = Arrays.asList(55L);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        when(sqlTemplateDirty.query(anyString(), any(NumberMapper.class), anyLong())).thenReturn(ids);
        when(parameterService.is(ParameterConstants.AUTO_RESOLVE_FOREIGN_KEY_VIOLATION)).thenReturn(true);
        when(engine.getDataService()).thenReturn(mock(IDataService.class));
        when(engine.getDataService().reloadMissingForeignKeyRows(anyLong(), anyString(), anyLong(), anyLong())).thenReturn(true);
        service.ack(batch);
        verify(engine.getDataService()).reloadMissingForeignKeyRows(anyLong(), anyString(), anyLong(), anyLong());
    }

    @Test
    void fkResolveNotDefinitiveClearsErrorTracking() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setErrorLine(1);
        batch.setSqlCode(ErrorConstants.FK_VIOLATION_CODE);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setSqlCode(ErrorConstants.FK_VIOLATION_CODE);
        ob.setFailedLineNumber(0);
        List<Number> ids = Arrays.asList(55L);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        when(sqlTemplateDirty.query(anyString(), any(NumberMapper.class), anyLong())).thenReturn(ids);
        when(parameterService.is(ParameterConstants.AUTO_RESOLVE_FOREIGN_KEY_VIOLATION)).thenReturn(true);
        IDataService dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        when(dataService.reloadMissingForeignKeyRows(anyLong(), anyString(), anyLong(), anyLong())).thenReturn(false);
        service.ack(batch);
        assertEquals(0L, ob.getFailedLineNumber());
        assertEquals(0L, ob.getFailedDataId());
    }

    @Test
    void fkViolationLoadBatchWithReverseReloadSuppressesError() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setErrorLine(1);
        batch.setSqlCode(ErrorConstants.FK_VIOLATION_CODE);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setLoadFlag(true);
        ob.setSentCount(1);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        when(parameterService.is(ParameterConstants.AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE_RELOAD)).thenReturn(true);
        service.ack(batch);
        assertFalse(ob.isErrorFlag());
        assertSame(Status.LD, ob.getStatus());
    }

    @Test
    void protocolViolationLoadBatchLogsInfoWithoutDeletingStaging() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setSqlCode(ErrorConstants.PROTOCOL_VIOLATION_CODE);
        batch.setSqlState(ErrorConstants.PROTOCOL_VIOLATION_STATE);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setLoadFlag(true);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        verify(stagingManager, never()).find(anyString(), anyString(), anyLong());
    }

    @Test
    void protocolViolationNonLoadBatchLooksUpStagingResource() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setSqlCode(ErrorConstants.PROTOCOL_VIOLATION_CODE);
        batch.setSqlState(ErrorConstants.PROTOCOL_VIOLATION_STATE);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        verify(stagingManager).find(anyString(), anyString(), anyLong());
    }

    @Test
    void suppressedErrorClearsErrorFlagAndSetsStatusLd() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        batch.setErrorLine(1);
        batch.setSqlCode(ErrorConstants.DEADLOCK_CODE);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setFailedLineNumber(0);
        List<Number> ids = Arrays.asList(55L);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        when(sqlTemplateDirty.query(anyString(), any(NumberMapper.class), anyLong())).thenReturn(ids);
        service.ack(batch);
        assertFalse(ob.isErrorFlag());
        assertSame(Status.LD, ob.getStatus());
    }

    @Test
    void nonSuppressedErrorKeepsErrorStatus() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, false);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        assertSame(Status.ER, ob.getStatus());
        assertTrue(ob.isErrorFlag());
    }

    @Test
    void firstTimeOkWithLoadIdUpdatesExtractRequestLoadTime() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, true);
        OutgoingBatch ob = outgoingBatch(Status.SE);
        ob.setLoadId(5L);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        verify(dataExtractorService).updateExtractRequestLoadTime(any(), any(), any());
    }

    @Test
    void duplicateOkWithLoadIdSkipsExtractRequestLoadTimeUpdate() {
        BatchAck batch = normalBatch(NORMAL_BATCH_ID, true);
        OutgoingBatch ob = outgoingBatch(Status.OK);
        ob.setLoadId(5L);
        when(outgoingBatchService.findOutgoingBatch(NORMAL_BATCH_ID, NODE_ID)).thenReturn(ob);
        service.ack(batch);
        verify(dataExtractorService, never()).updateExtractRequestLoadTime(any(), any(), any());
    }
}
