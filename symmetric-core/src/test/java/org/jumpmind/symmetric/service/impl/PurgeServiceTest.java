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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.mapper.LongMapper;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataEvent;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IPurgeService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.statistic.StatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class PurgeServiceTest {
    private IParameterService parameterService;
    private ISymmetricDialect symmetricDialect;
    private ISqlTemplate sqlTemplate;
    private ISqlTemplate sqlTemplateDirty;
    private IClusterService clusterService;
    private IDataService dataService;
    private ISequenceService sequenceService;
    private IStatisticManager statisticManager;
    private IExtensionService extensionService;
    private IContextService contextService;
    private IPurgeService purgeService;
    private AbstractService service;

    @BeforeEach
    public void setUp() throws Exception {
        sqlTemplate = mock(ISqlTemplate.class);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseInfo databaseInfo = new DatabaseInfo();
        when(platform.getDatabaseInfo()).thenReturn(databaseInfo);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(platform.scrubSql(anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArgument(0);
            }
        });
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(ddlBuilder.getDatabaseInfo()).thenReturn(databaseInfo);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        symmetricDialect = mock(AbstractSymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.supportsTransactionViews()).thenReturn(false);
        when(symmetricDialect.getDatabaseTime()).thenReturn(0L);
        when(symmetricDialect.getName()).thenReturn("");
        parameterService = mock(ParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getInt(ParameterConstants.PURGE_RETENTION_MINUTES)).thenReturn(5);
        when(parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_BATCH_IDS)).thenReturn(100);
        when(parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_EVENT_BATCH_IDS)).thenReturn(100);
        when(parameterService.is(ParameterConstants.PURGE_FIRST_PASS)).thenReturn(true);
        when(parameterService.getLong(ParameterConstants.PURGE_MAX_LINGERING_BATCHES_READ)).thenReturn(10000L);
        when(parameterService.getInt(ParameterConstants.PURGE_MAX_NUMBER_OF_DATA_IDS)).thenReturn(100);
        when(parameterService.getLong(ParameterConstants.PURGE_FIRST_PASS_OUTSTANDING_BATCHES_THRESHOLD)).thenReturn(10000L);
        when(parameterService.getInt(ParameterConstants.PURGE_EXPIRED_DATA_GAP_RETENTION_MINUTES)).thenReturn(10);
        when(parameterService.getInt(ParameterConstants.PURGE_MAX_EXPIRED_DATA_GAPS_READ)).thenReturn(100);
        when(parameterService.getInt(ParameterConstants.PURGE_EXTRACT_REQUESTS_RETENTION_MINUTES)).thenReturn(1);
        when(parameterService.getInt(ParameterConstants.PURGE_REGISTRATION_REQUEST_RETENTION_MINUTES)).thenReturn(1);
        when(parameterService.getInt(ParameterConstants.PURGE_TRIGGER_HIST_RETENTION_MINUTES)).thenReturn(1440);
        when(parameterService.getInt(ParameterConstants.PURGE_STATS_RETENTION_MINUTES)).thenReturn(10080);
        extensionService = mock(ExtensionService.class);
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        clusterService = mock(ClusterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getDataService()).thenReturn(dataService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(clusterService.lock(ClusterConstants.PURGE_OUTGOING)).thenReturn(true);
        when(clusterService.lock(ClusterConstants.PURGE_INCOMING)).thenReturn(true);
        contextService = mock(ContextService.class);
        dataService = mock(DataService.class);
        statisticManager = mock(StatisticManager.class);
        sequenceService = mock(SequenceService.class);
        when(statisticManager.newProcessInfo((ProcessInfoKey) any())).thenReturn(new ProcessInfo());
        when(sequenceService.currVal(Constants.SEQUENCE_OUTGOING_BATCH)).thenReturn(104L);
        purgeService = new PurgeService(parameterService, symmetricDialect, clusterService, dataService, sequenceService, statisticManager,
                extensionService, contextService);
        service = (AbstractService) purgeService;
        @SuppressWarnings("unchecked")
        ISqlReadCursor<Long> lingeringBatchesCursor = mock(ISqlReadCursor.class);
        when(sqlTemplateDirty.queryForCursor(eq(service.getSql("selectLingeringBatches")), any(LongMapper.class), any(Object[].class),
                any(int[].class))).thenReturn(lingeringBatchesCursor);
    }

    @Test
    public void testPurgeDelayedChannel() {
        List<Data> datas = newDataList(1, 2, 3, 4);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(1, 100));
        dataEvents.add(new DataEvent(2, 101));
        dataEvents.add(new DataEvent(3, 103));
        dataEvents.add(new DataEvent(4, 102));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(100, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(101, Constants.CHANNEL_CONFIG, Status.OK, true));
        batches.add(newOutgoingBatch(103, Constants.CHANNEL_DEFAULT, Status.LD, true));
        batches.add(newOutgoingBatch(102, Constants.CHANNEL_HEARTBEAT, Status.OK, true));
        setupWhen(datas, dataEvents, batches, 5);
        purgeService.purgeOutgoing(false);
        verifyPurgeData(1, 2, 3, 4);
        verifyPurgeDataEvent(100, 102, 103, 103);
        verifyPurgeOutgoingBatch(100, 102, 103, 103);
        verifyPurgeContext(4, 103, 103);
    }

    @Test
    public void testPurgeDelayedChannelAgain() {
        List<Data> datas = newDataList(1, 2, 3, 4);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(1, 100));
        dataEvents.add(new DataEvent(2, 101));
        dataEvents.add(new DataEvent(3, 103));
        dataEvents.add(new DataEvent(4, 102));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(100, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(101, Constants.CHANNEL_DEFAULT, Status.LD, true));
        batches.add(newOutgoingBatch(103, Constants.CHANNEL_CONFIG, Status.LD, true));
        batches.add(newOutgoingBatch(102, Constants.CHANNEL_HEARTBEAT, Status.OK, true));
        setupWhen(datas, dataEvents, batches, 5);
        purgeService.purgeOutgoing(true);
        verifyPurgeData(1, 1, 2, 4);
        verifyPurgeDataEvent(100, 100, 101, 103);
        verifyPurgeOutgoingBatch(100, 100, 101, 103);
        verifyPurgeContext(4, 103, 103);
    }

    @Test
    public void testPurgeNewBatchOldDataTwoChannels() {
        List<Data> datas = newDataList(1, 2, 5, 3, 4);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(1, 100));
        dataEvents.add(new DataEvent(2, 101));
        dataEvents.add(new DataEvent(5, 102));
        dataEvents.add(new DataEvent(3, 103));
        dataEvents.add(new DataEvent(4, 104));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(100, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(101, Constants.CHANNEL_HEARTBEAT, Status.OK, true));
        batches.add(newOutgoingBatch(102, Constants.CHANNEL_HEARTBEAT, Status.OK, true));
        batches.add(newOutgoingBatch(103, Constants.CHANNEL_DEFAULT, Status.LD, false));
        batches.add(newOutgoingBatch(104, Constants.CHANNEL_DEFAULT, Status.LD, false));
        setupWhen(datas, dataEvents, batches, 6);
        purgeService.purgeOutgoing(true);
        verifyPurgeData(1, 2, 3, 5);
        verifyPurgeDataEvent(100, 102, -1, -1);
        verifyPurgeOutgoingBatch(100, 102, -1, -1);
        verifyPurgeContext(5, 102, 102);
    }

    @Test
    public void testPurgeNewBatchOldDataOneChannel() {
        List<Data> datas = newDataList(1, 2, 3);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(1, 100));
        dataEvents.add(new DataEvent(3, 101));
        dataEvents.add(new DataEvent(2, 102));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(100, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(101, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(102, Constants.CHANNEL_DEFAULT, Status.OK, true));
        setupWhen(datas, dataEvents, batches, 6);
        purgeService.purgeOutgoing(true);
        verifyPurgeData(1, 3, 0, 0);
        verifyPurgeDataEvent(100, 102, 0, 0);
        verifyPurgeOutgoingBatch(100, 102, 0, 0);
        verifyPurgeContext(3, 102, 102);
    }

    @Test
    public void testPurgeGapFilledAfterPurgeWindow() {
        when(contextService.getLong(ContextConstants.PURGE_LAST_DATA_ID)).thenReturn(2L);
        List<Data> datas = newDataList(1, 2, 3);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(1, 100));
        dataEvents.add(new DataEvent(3, 101));
        dataEvents.add(new DataEvent(2, 102));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(100, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(101, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(102, Constants.CHANNEL_DEFAULT, Status.OK, true));
        setupWhen(datas, dataEvents, batches, 4);
        purgeService.purgeOutgoing(true);
        verifyPurgeData(1, 3, 0, 0);
        verifyPurgeDataEvent(100, 102, 0, 0);
        verifyPurgeOutgoingBatch(100, 102, 0, 0);
        verifyPurgeContext(3, 102, 102);
    }

    @Test
    public void testFirstPassEligibleWhenOutstandingBatchAheadOfPurgePosition() {
        setupFirstPassEligibleData();
        purgeService.purgeOutgoing(true);
        verifyPurgeData(11, 12, 13, 13);
        verifyPurgeDataEvent(104, 105, 106, 106);
        verifyPurgeOutgoingBatch(104, 105, 106, 106);
        verifyPurgeContext(13, 106, 106);
    }

    @Test
    public void testFirstPassSkippedWhenOutstandingBatchHoldsDataBehindPurgePosition() {
        List<Data> datas = newDataList(3, 11, 12);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(3, 101));
        dataEvents.add(new DataEvent(11, 104));
        dataEvents.add(new DataEvent(12, 105));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(101, Constants.CHANNEL_DEFAULT, Status.LD, true));
        batches.add(newOutgoingBatch(104, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(105, Constants.CHANNEL_DEFAULT, Status.OK, true));
        setupWhen(datas, dataEvents, batches, 13, 10, 103, 103);
        purgeService.purgeOutgoing(true);
        verifyNoFirstPass();
        verifyPurgeDataExists(11, 12);
        verifyPurgeDataEventExists(104, 105);
        verifyPurgeOutgoingBatchExists(104, 105);
        verifyPurgeContext(12, 105, 105);
    }

    @Test
    public void testFirstPassSkippedWhenOldOutstandingBatchPinsBatchScan() {
        List<Data> datas = newDataList(3, 11, 12);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(3, 101));
        dataEvents.add(new DataEvent(11, 104));
        dataEvents.add(new DataEvent(12, 106));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(100, Constants.CHANNEL_HEARTBEAT, Status.LD, true));
        batches.add(newOutgoingBatch(101, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(104, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(106, Constants.CHANNEL_DEFAULT, Status.LD, true));
        setupWhen(datas, dataEvents, batches, 13, 10, 103, 103);
        purgeService.purgeOutgoing(true);
        verifyNoFirstPass();
        verifyPurgeDataExists(11, 12);
        verifyPurgeDataEventExists(104, 106);
        verifyPurgeOutgoingBatchExists(104, 106);
        verifyPurgeContext(12, 106, 106);
    }

    @Test
    public void testFirstPassSkippedOverThresholdWhenOldOutstandingBatchPinsBatchScan() {
        when(parameterService.getLong(ParameterConstants.PURGE_FIRST_PASS_OUTSTANDING_BATCHES_THRESHOLD)).thenReturn(1L);
        List<Data> datas = newDataList(3, 11, 12);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(3, 101));
        dataEvents.add(new DataEvent(11, 104));
        dataEvents.add(new DataEvent(12, 106));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(100, Constants.CHANNEL_HEARTBEAT, Status.LD, true));
        batches.add(newOutgoingBatch(101, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(104, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(106, Constants.CHANNEL_DEFAULT, Status.LD, true));
        setupWhen(datas, dataEvents, batches, 13, 10, 103, 103);
        purgeService.purgeOutgoing(true);
        verifyNoFirstPass();
        verifyPurgeDataExists(11, 12);
        verifyPurgeDataEventExists(104, 106);
        verifyPurgeOutgoingBatchExists(104, 106);
        verifyPurgeContext(12, 106, 106);
    }

    @Test
    public void testLingeringBatchKeepsDataHeldByOutstandingBatch() {
        setupFirstPassEligibleData();
        @SuppressWarnings("unchecked")
        ISqlReadCursor<Long> lingeringBatchesCursor = mock(ISqlReadCursor.class);
        when(lingeringBatchesCursor.next()).thenReturn(101L, (Long) null);
        when(sqlTemplateDirty.queryForCursor(eq(service.getSql("selectLingeringBatches")), any(LongMapper.class), any(Object[].class),
                any(int[].class))).thenReturn(lingeringBatchesCursor);
        purgeService.purgeOutgoing(true);
        int idType = symmetricDialect.getSqlTypeForIds();
        verify(sqlTemplate).update(service.getSql("deleteDataByBatchId"), new Object[] { 101L, OutgoingBatch.Status.OK.name() },
                new int[] { idType, Types.VARCHAR });
    }

    @Test
    public void testDeleteDataByBatchIdUsesAntiJoinForOutstandingBatches() {
        String sql = service.getSql("deleteDataByBatchId").toLowerCase();
        assertTrue(sql.contains("not exists"));
        assertFalse(sql.contains("not in"));
    }

    @Test
    public void testIsEligibleForFirstPassPurgeWithoutOutstandingBatches() {
        assertTrue(getPurgeServiceImpl().isEligibleForFirstPassPurge(0, 104, Duration.ZERO));
    }

    @Test
    public void testIsEligibleForFirstPassPurgeWhenOutstandingBatchAtOrAheadOfPurgePosition() {
        assertTrue(getPurgeServiceImpl().isEligibleForFirstPassPurge(104, 104, Duration.ZERO));
        assertTrue(getPurgeServiceImpl().isEligibleForFirstPassPurge(106, 104, Duration.ZERO));
    }

    @Test
    public void testIsEligibleForFirstPassPurgeWhenOutstandingBatchBehindPurgePosition() {
        assertFalse(getPurgeServiceImpl().isEligibleForFirstPassPurge(103, 104, Duration.ZERO));
    }

    @Test
    public void testIsEligibleForFirstPassPurgeWhenQueryIsSlow() {
        assertFalse(getPurgeServiceImpl().isEligibleForFirstPassPurge(106, 104, Duration.ofMillis(Constants.LONG_OPERATION_THRESHOLD + 1)));
    }

    @Test
    public void testFirstPassQueryTimeoutTripsLongOperation() {
        assertEquals(Duration.ofSeconds(33), PurgeService.FIRST_PASS_QUERY_TIMEOUT);
        assertTrue(getPurgeServiceImpl().isLongOperation("test query", PurgeService.FIRST_PASS_QUERY_TIMEOUT));
    }

    @Test
    public void testFirstPassDataQueryFailureWithinThresholdStopsPurge() {
        setupFirstPassEligibleData();
        when(sqlTemplateDirty.queryForLong(service.getSql("countOutgoingBatchNotStatusSql"), PurgeService.FIRST_PASS_QUERY_TIMEOUT,
                OutgoingBatch.Status.OK.name())).thenThrow(new SqlException("connection reset"));
        purgeService.purgeOutgoing(true);
        verify(sqlTemplate, never()).update(eq(service.getSql("deleteDataExistsSql")), any(Object[].class), any(int[].class));
    }

    @Test
    public void testIsLongOperationWithinThreshold() {
        assertFalse(getPurgeServiceImpl().isLongOperation("test query", Duration.ZERO));
        assertFalse(getPurgeServiceImpl().isLongOperation("test query", Duration.ofMillis(Constants.LONG_OPERATION_THRESHOLD)));
    }

    @Test
    public void testIsLongOperationExceedsThreshold() {
        assertTrue(getPurgeServiceImpl().isLongOperation("test query", Duration.ofMillis(Constants.LONG_OPERATION_THRESHOLD + 1)));
    }

    @Test
    public void testStrandedDataRecapturedWithDeleteCutoff() {
        when(parameterService.is(ParameterConstants.PURGE_STRANDED_DATA_RECAPTURE_ENABLED)).thenReturn(true);
        setupFirstPassEligibleData();
        when(sqlTemplateDirty.queryForLong(service.getSql("minDataEventId"))).thenReturn(20L);
        when(sqlTemplateDirty.queryForLong(service.getSql("minDataGapStartId"))).thenReturn(21L);
        purgeService.purgeOutgoing(true);
        ArgumentCaptor<Object[]> deleteArgs = ArgumentCaptor.forClass(Object[].class);
        verify(sqlTemplate).update(eq(service.getSql("deleteStrandedData")), deleteArgs.capture(), any(int[].class));
        assertEquals(11L, deleteArgs.getValue()[0]);
        assertEquals(19L, deleteArgs.getValue()[1]);
        verify(dataService).reCaptureData(11, 19, (Date) deleteArgs.getValue()[2]);
        verify(dataService, never()).reCaptureData(anyLong(), anyLong());
    }

    @Test
    public void testPurgeAroundSmallGaps() {
        List<DataGap> gaps = new ArrayList<DataGap>();
        gaps.add(new DataGap(1821, 1824));
        gaps.add(new DataGap(1838, 1838));
        long[] minMax = PurgeService.getMinMaxAvoidGaps(1821, 1846, gaps);
        assertEquals(1825, minMax[0]);
        assertEquals(1837, minMax[1]);
        assertEquals(gaps.size(), 1);
        minMax = PurgeService.getMinMaxAvoidGaps(1838, 1838, gaps);
        assertEquals(-1839, minMax[1]);
        assertEquals(gaps.size(), 1);
        minMax = PurgeService.getMinMaxAvoidGaps(1838, 1846, gaps);
        assertEquals(1839, minMax[0]);
        assertEquals(1846, minMax[1]);
        assertEquals(gaps.size(), 0);
    }

    protected void setupFirstPassEligibleData() {
        List<Data> datas = newDataList(11, 12, 13);
        List<DataEvent> dataEvents = new ArrayList<DataEvent>();
        dataEvents.add(new DataEvent(11, 104));
        dataEvents.add(new DataEvent(12, 105));
        dataEvents.add(new DataEvent(13, 106));
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        batches.add(newOutgoingBatch(104, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(105, Constants.CHANNEL_DEFAULT, Status.OK, true));
        batches.add(newOutgoingBatch(106, Constants.CHANNEL_DEFAULT, Status.LD, true));
        setupWhen(datas, dataEvents, batches, 14, 10, 103, 103);
    }

    protected PurgeService getPurgeServiceImpl() {
        return (PurgeService) purgeService;
    }

    protected List<Data> newDataList(int... ids) {
        List<Data> datas = new ArrayList<Data>();
        for (int id : ids) {
            Data data = new Data();
            data.setDataId(id);
            datas.add(data);
        }
        return datas;
    }

    protected OutgoingBatch newOutgoingBatch(long batchId, String channelId, Status status, boolean isOld) {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(batchId);
        batch.setChannelId(channelId);
        batch.setStatus(status);
        if (!isOld) {
            batch.setCreateTime(new Date());
        }
        return batch;
    }

    protected void setupWhen(List<Data> datas, List<DataEvent> dataEvents, List<OutgoingBatch> batches, long minGapStartId, long lastDataId,
            long lastEventBatchId, long lastBatchId) {
        when(contextService.getLong(ContextConstants.PURGE_LAST_DATA_ID)).thenReturn(lastDataId);
        when(contextService.getLong(ContextConstants.PURGE_LAST_EVENT_BATCH_ID)).thenReturn(lastEventBatchId);
        when(contextService.getLong(ContextConstants.PURGE_LAST_BATCH_ID)).thenReturn(lastBatchId);
        setupWhen(datas, dataEvents, batches, minGapStartId, lastBatchId + 1);
    }

    protected void setupWhen(List<Data> datas, List<DataEvent> dataEvents, List<OutgoingBatch> batches, long minGapStartId) {
        setupWhen(datas, dataEvents, batches, minGapStartId, 0);
    }

    protected void setupWhen(List<Data> datas, List<DataEvent> dataEvents, List<OutgoingBatch> batches, long minGapStartId, long startBatchId) {
        TestContext c = getTestContext(datas, dataEvents, batches, startBatchId);
        when(sqlTemplateDirty.queryForLong(service.getSql("minDataId"))).thenReturn(c.minDataId);
        when(sqlTemplateDirty.queryForLong(service.getSql("minOutgoingBatchId"))).thenReturn(c.minBatchId);
        List<Long> maxBatchIdForOldBatchesList = new ArrayList<Long>();
        maxBatchIdForOldBatchesList.add(c.maxBatchIdForOldBatches);
        when(sqlTemplateDirty.query(eq(service.getSql("maxBatchIdForOldBatches")), any(LongMapper.class), any(Object[].class), any(int[].class))).thenReturn(
                maxBatchIdForOldBatchesList);
        List<Row> rows = new ArrayList<Row>();
        Row row = new Row(2);
        row.put("min_data_id", c.minDataIdForBatches);
        row.put("max_data_id", c.maxDataIdForBatches);
        rows.add(row);
        when(sqlTemplateDirty.query(eq(service.getSql("minMaxDataIdForOldBatches")), any(Object[].class), any(int[].class))).thenReturn(rows);
        when(sqlTemplateDirty.queryForLong(service.getSql("countOutgoingBatchNotStatusSql"), PurgeService.FIRST_PASS_QUERY_TIMEOUT,
                OutgoingBatch.Status.OK.name())).thenReturn(c.countBatchNotOk);
        when(sqlTemplateDirty.queryForLong(service.getSql("minOutgoingBatchNotStatusSql"), OutgoingBatch.Status.OK.name())).thenReturn(c.minBatchIdNotOk);
        when(sqlTemplateDirty.queryForLong(service.getSql("selectDataEventMinNotStatusSql"), PurgeService.FIRST_PASS_QUERY_TIMEOUT,
                OutgoingBatch.Status.OK.name())).thenReturn(c.minDataEventIdNotOk);
        when(sqlTemplateDirty.queryForLong(service.getSql("minDataGapStartId"))).thenReturn(minGapStartId);
    }

    protected TestContext getTestContext(List<Data> datas, List<DataEvent> dataEvents, List<OutgoingBatch> batches, long startBatchId) {
        TestContext context = new TestContext();
        context.startBatchId = startBatchId;
        populateContextData(context, datas);
        populateContextOutgoingBatch(context, batches);
        populateContextDataEvent(context, dataEvents);
        return context;
    }

    protected void populateContextData(TestContext context, List<Data> datas) {
        for (Data data : datas) {
            if (data.getDataId() < context.minDataId || context.minDataId == 0) {
                context.minDataId = data.getDataId();
            }
        }
    }

    protected void populateContextOutgoingBatch(TestContext context, List<OutgoingBatch> batches) {
        for (OutgoingBatch batch : batches) {
            context.batchesByBatchId.put(batch.getBatchId(), batch);
            if (batch.getBatchId() < context.minBatchId || context.minBatchId == 0) {
                context.minBatchId = batch.getBatchId();
            }
            if (batch.getStatus() != Status.OK) {
                context.countBatchNotOk++;
                if (batch.getBatchId() < context.minBatchIdNotOk || context.minBatchIdNotOk == 0) {
                    context.minBatchIdNotOk = batch.getBatchId();
                }
            }
            if (batch.getCreateTime() == null && batch.getBatchId() > context.maxBatchIdForOldBatches) {
                context.maxBatchIdForOldBatches = batch.getBatchId();
            }
        }
    }

    protected void populateContextDataEvent(TestContext context, List<DataEvent> dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            OutgoingBatch batch = context.batchesByBatchId.get(dataEvent.getBatchId());
            if (batch.getStatus() != Status.OK && (dataEvent.getDataId() < context.minDataEventIdNotOk || context.minDataEventIdNotOk == 0)) {
                context.minDataEventIdNotOk = dataEvent.getDataId();
            }
            if (dataEvent.getBatchId() >= context.startBatchId && dataEvent.getBatchId() <= context.maxBatchIdForOldBatches) {
                if (dataEvent.getDataId() < context.minDataIdForBatches || context.minDataIdForBatches == 0) {
                    context.minDataIdForBatches = dataEvent.getDataId();
                }
                if (dataEvent.getDataId() > context.maxDataIdForBatches) {
                    context.maxDataIdForBatches = dataEvent.getDataId();
                }
            }
        }
    }

    protected void verifyPurgeData(long startRangeId, long endRangeId, long startExistsId, long endExistsId) {
        int[] types = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() };
        int[] typesOk = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds(), Types.VARCHAR };
        verify(sqlTemplate).update(service.getSql("deleteDataByRangeSql"), new Object[] { startRangeId, endRangeId }, types);
        if (startExistsId > 0) {
            verify(sqlTemplate).update(service.getSql("deleteDataExistsSql"), new Object[] { startExistsId, endExistsId, OutgoingBatch.Status.OK.name() },
                    typesOk);
        }
    }

    protected void verifyPurgeDataEvent(long startRangeId, long endRangeId, long startExistsId, long endExistsId) {
        int[] types = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() };
        int[] typesOk = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds(), Types.VARCHAR };
        verify(sqlTemplate).update(service.getSql("deleteDataEventByRangeSql"), new Object[] { startRangeId, endRangeId }, types);
        if (startExistsId > 0) {
            verify(sqlTemplate).update(service.getSql("deleteDataEventExistsSql"), new Object[] { startExistsId, endExistsId, OutgoingBatch.Status.OK.name() },
                    typesOk);
        }
    }

    protected void verifyPurgeOutgoingBatch(long startRangeId, long endRangeId, long startExistsId, long endExistsId) {
        int[] types = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() };
        int[] typesOk = new int[] { Types.VARCHAR, symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() };
        verify(sqlTemplate).update(service.getSql("deleteOutgoingBatchByRangeSql"), new Object[] { startRangeId, endRangeId }, types);
        if (startExistsId > 0) {
            verify(sqlTemplate).update(service.getSql("deleteOutgoingBatchExistsSql"), new Object[] { OutgoingBatch.Status.OK.name(), startExistsId,
                    endExistsId }, typesOk);
        }
    }

    protected void verifyNoFirstPass() {
        verify(sqlTemplate, never()).update(eq(service.getSql("deleteDataByRangeSql")), any(Object[].class), any(int[].class));
        verify(sqlTemplate, never()).update(eq(service.getSql("deleteDataEventByRangeSql")), any(Object[].class), any(int[].class));
        verify(sqlTemplate, never()).update(eq(service.getSql("deleteOutgoingBatchByRangeSql")), any(Object[].class), any(int[].class));
    }

    protected void verifyPurgeDataExists(long startId, long endId) {
        int[] typesOk = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds(), Types.VARCHAR };
        verify(sqlTemplate).update(service.getSql("deleteDataExistsSql"), new Object[] { startId, endId, OutgoingBatch.Status.OK.name() }, typesOk);
    }

    protected void verifyPurgeDataEventExists(long startId, long endId) {
        int[] typesOk = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds(), Types.VARCHAR };
        verify(sqlTemplate).update(service.getSql("deleteDataEventExistsSql"), new Object[] { startId, endId, OutgoingBatch.Status.OK.name() }, typesOk);
    }

    protected void verifyPurgeOutgoingBatchExists(long startId, long endId) {
        int[] typesOk = new int[] { Types.VARCHAR, symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() };
        verify(sqlTemplate).update(service.getSql("deleteOutgoingBatchExistsSql"), new Object[] { OutgoingBatch.Status.OK.name(), startId, endId },
                typesOk);
    }

    protected void verifyPurgeContext(long lastDataId, long lastEventBatchId, long lastBatchId) {
        verify(contextService).save(ContextConstants.PURGE_LAST_DATA_ID, String.valueOf(lastDataId));
        verify(contextService).save(ContextConstants.PURGE_LAST_EVENT_BATCH_ID, String.valueOf(lastEventBatchId));
        verify(contextService).save(ContextConstants.PURGE_LAST_BATCH_ID, String.valueOf(lastBatchId));
    }

    class TestContext {
        long minDataId;
        long minBatchId;
        long startBatchId;
        Map<Long, OutgoingBatch> batchesByBatchId = new HashMap<Long, OutgoingBatch>();
        long maxBatchIdForOldBatches;
        long minDataIdForBatches;
        long maxDataIdForBatches;
        long countBatchNotOk;
        long minBatchIdNotOk;
        long minDataEventIdNotOk;
    }
}
