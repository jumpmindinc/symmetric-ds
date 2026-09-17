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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ErrorConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.AbstractFileParsingRouter;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.util.CounterStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class SelectFromSymDataSourceTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IDataService dataService;
    private ITriggerRouterService triggerRouterService;
    private IDatabasePlatform platform;
    private ISymmetricDialect symmetricDialect;
    private OutgoingBatch outgoingBatch;
    private Node sourceNode;
    private Node targetNode;
    private Table tableWithFksAndIndexes;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        dataService = mock(IDataService.class);
        triggerRouterService = mock(ITriggerRouterService.class);
        platform = mock(IDatabasePlatform.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        INodeService nodeService = mock(INodeService.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        ITransformService transformService = mock(ITransformService.class);
        IRouterService routerService = mock(IRouterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getTransformService()).thenReturn(transformService);
        when(engine.getTablePrefix()).thenReturn("sym");
        when(engine.getDataService()).thenReturn(dataService);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getRouterService()).thenReturn(routerService);
        when(symmetricDialect.getBinaryEncoding()).thenReturn(BinaryEncoding.HEX);
        when(symmetricDialect.getName()).thenReturn("H2");
        when(symmetricDialect.getTargetDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(triggerRouterService.getTriggerRoutersByTriggerHist(anyString(), anyBoolean())).thenReturn(new HashMap<>());
        when(parameterService.is(ParameterConstants.CREATE_TABLE_WITHOUT_DEFAULTS, false)).thenReturn(false);
        when(parameterService.is(ParameterConstants.CREATE_TABLE_WITHOUT_FOREIGN_KEYS, false)).thenReturn(false);
        when(parameterService.is(ParameterConstants.CREATE_TABLE_WITHOUT_INDEXES, false)).thenReturn(false);
        when(parameterService.is(ParameterConstants.CREATE_TABLE_WITHOUT_PK_IF_SOURCE_WITHOUT_PK, false)).thenReturn(false);
        when(parameterService.is(ParameterConstants.CREATE_TABLE_INCLUDE_APPLICATION_TRIGGERS, false)).thenReturn(false);
        when(parameterService.is(ParameterConstants.MYSQL_TINYINT_DDL_TO_BOOLEAN, false)).thenReturn(false);
        when(parameterService.is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC)).thenReturn(false);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_TABLE_LOGGING, false)).thenReturn(false);
        sourceNode = new Node("source", "server");
        targetNode = new Node("target", "client");
        outgoingBatch = mock(OutgoingBatch.class);
        when(outgoingBatch.getBatchId()).thenReturn(1L);
        when(outgoingBatch.getChannelId()).thenReturn(Constants.CHANNEL_DEFAULT);
        when(outgoingBatch.getNodeId()).thenReturn("target");
        when(outgoingBatch.isCommonFlag()).thenReturn(false);
        when(outgoingBatch.getLoadId()).thenReturn(0L);
        when(outgoingBatch.getNodeBatchId()).thenReturn("target-1");
        tableWithFksAndIndexes = buildTableWithFksAndIndexes();
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(tableWithFksAndIndexes);
    }

    @Test
    void testNew_withNonMsSqlDialect_initializesFieldsAndDialectHasNoOldBinaryDataFalse() throws Exception {
        SelectFromSymDataSource source = createSource();
        assertSame(sourceNode.getNodeId(), source.getBatch().getSourceNodeId());
        verify(outgoingBatch).resetExtractRowStats();
        assertFalse(getBoolean(source, "dialectHasNoOldBinaryData"));
    }

    @Test
    void testNew_withMsSqlDialect_setsDialectHasNoOldBinaryDataTrue() throws Exception {
        when(symmetricDialect.getName()).thenReturn(DatabaseNamesConstants.MSSQL2008);
        SelectFromSymDataSource source = createSource();
        assertTrue(getBoolean(source, "dialectHasNoOldBinaryData"));
    }

    @Test
    void testNext_firstCall_obtainsCursorFromDataService() throws Exception {
        SelectFromSymDataSource source = createSource();
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(null);
        source.next();
        verify(dataService).selectDataFor(1L, "target", false);
    }

    @Test
    void testNext_whenCursorExhausted_closesCursorAndReturnsNull() throws Exception {
        SelectFromSymDataSource source = createSource();
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(null);
        CsvData result = source.next();
        assertNull(result);
        verify(cursor).close();
    }

    @Test
    void testNext_withMissingTriggerRouter_tracksMissingCounterAndAdvancesCursor() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(5);
        triggerHistory.setSourceTableName("test_table");
        triggerHistory.setTriggerId("trigger1");
        Data missingRouterData = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(),
                triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(missingRouterData, (Data) null);
        when(triggerRouterService.getTriggerRouterByTriggerHist("client", 5, true)).thenReturn(null);
        CsvData result = source.next();
        assertNull(result);
        verify(cursor).close();
        Map<Integer, CounterStat> missing = getMissingTriggerRoutersMap(source);
        assertTrue(missing.containsKey(5));
    }

    @Test
    void testNext_withInsertEvent_setsRelationsAndIncrementsExtractCounts() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(7);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 7, triggerRouter);
        Data data = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        CsvData result = source.next();
        assertSame(data, result);
        assertNotNull(source.getSourceRelation());
        assertNotNull(source.getTargetRelation());
        verify(outgoingBatch).incrementExtractRowCount();
        verify(outgoingBatch).incrementExtractRowCount(DataEventType.INSERT);
    }

    @Test
    void testNext_withInsertEvent_corruptedColumnCount_throwsProtocolException() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(8);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 8, triggerRouter);
        Data data = new Data(0, null, "1", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        assertThrows(ProtocolException.class, source::next);
    }

    @Test
    void testNext_withDeleteEvent_corruptedPkColumnCount_throwsProtocolException() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id,ref", "id,ref,name");
        triggerHistory.setTriggerHistoryId(9);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 9, triggerRouter);
        when(outgoingBatch.getSqlCode()).thenReturn(ErrorConstants.PROTOCOL_VIOLATION_CODE);
        Data data = new Data(0, "1", null, DataEventType.DELETE, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        assertThrows(ProtocolException.class, source::next);
    }

    @Test
    void testNext_withReloadEvent_delegatesToProcessReloadEvent() throws Exception {
        TestableSelectFromSymDataSource source = createTestableSource();
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(11);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 11, triggerRouter);
        Data reloadData = new Data(0, null, "1=1", DataEventType.RELOAD, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(reloadData, (Data) null);
        SelectFromTableSource reloadSourceMock = mock(SelectFromTableSource.class);
        source.reloadSourceToReturn = reloadSourceMock;
        Data returnedFromReload = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(),
                triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        when(reloadSourceMock.next()).thenReturn(returnedFromReload);
        when(reloadSourceMock.getSourceRelation()).thenReturn(tableWithFksAndIndexes);
        when(reloadSourceMock.getTargetRelation()).thenReturn(tableWithFksAndIndexes);
        when(reloadSourceMock.requiresLobsSelectedFromSource(returnedFromReload)).thenReturn(false);
        CsvData result = source.next();
        assertSame(returnedFromReload, result);
        assertEquals("1=1", source.capturedEvent.getInitialLoadSelect());
    }

    @Test
    void testNext_withActiveReloadSource_continuesStreamingFromReloadSource() throws Exception {
        SelectFromSymDataSource source = createSource();
        SelectFromTableSource reloadSourceMock = mock(SelectFromTableSource.class);
        setReloadSource(source, reloadSourceMock);
        Data nextReloadRow = new Data(0, null, "2,bar", DataEventType.INSERT, "test_table", new Date(),
                new TriggerHistory("test_table", "id", "id,name"), Constants.CHANNEL_DEFAULT, null, null);
        when(reloadSourceMock.next()).thenReturn(nextReloadRow);
        when(reloadSourceMock.getSourceRelation()).thenReturn(tableWithFksAndIndexes);
        when(reloadSourceMock.getTargetRelation()).thenReturn(tableWithFksAndIndexes);
        when(reloadSourceMock.requiresLobsSelectedFromSource(nextReloadRow)).thenReturn(true);
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(newCursorMock());
        CsvData result = source.next();
        assertSame(nextReloadRow, result);
        assertTrue(getBoolean(source, "requiresLobSelectedFromSource"));
        assertSame(reloadSourceMock, getReloadSource(source));
    }

    @Test
    void testNext_withActiveReloadSourceExhausted_closesReloadSourceAndFallsThroughToCursor() throws Exception {
        SelectFromSymDataSource source = createSource();
        SelectFromTableSource reloadSourceMock = mock(SelectFromTableSource.class);
        setReloadSource(source, reloadSourceMock);
        when(reloadSourceMock.next()).thenReturn(null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(null);
        CsvData result = source.next();
        assertNull(result);
        verify(reloadSourceMock).close();
        assertNull(getReloadSource(source));
        verify(cursor).close();
    }

    @Test
    void testNext_withFileParserRouterEvent_buildsSyntheticTriggerRouterAndProcessesEvent() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(70);
        triggerHistory.setTriggerId(AbstractFileParsingRouter.TRIGGER_ID_FILE_PARSER);
        Data data = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        data.setExternalData("R=router1");
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        CsvData result = source.next();
        assertSame(data, result);
        verify(outgoingBatch).incrementExtractRowCount();
    }

    @Test
    void testNext_withTriggerRouterNotInMap_resolvesViaDirectLookupAndCachesIt() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(30);
        triggerHistory.setTriggerId("trigger1");
        when(triggerRouterService.getTriggerRouterByTriggerHist("client", 30, true)).thenReturn(triggerRouter);
        Data data = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        CsvData result = source.next();
        assertSame(data, result);
        assertSame(triggerRouter, getTriggerRoutersMap(source).get(30));
    }

    @Test
    void testNext_withAlreadyKnownMissingTriggerRouter_incrementsCounterAndAdvancesCursor() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(31);
        triggerHistory.setTriggerId("trigger1");
        CounterStat existingStat = new CounterStat(100L, 1);
        getMissingTriggerRoutersMap(source).put(31, existingStat);
        Data data = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        CsvData result = source.next();
        assertNull(result);
        assertEquals(2, existingStat.getCount());
        verify(cursor).close();
    }

    @Test
    void testNext_withSameTriggerHistoryAndRouterAsLastCall_skipsRelationRelookup() throws Exception {
        SelectFromSymDataSource source = createSource();
        ColumnsAccordingToTriggerHistory mockLookup = injectColumnsLookupMock(source);
        when(mockLookup.lookup(anyString(), any(TriggerHistory.class), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(tableWithFksAndIndexes);
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(40);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 40, triggerRouter);
        Data data1 = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        Data data2 = new Data(0, null, "2,bar", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data1, data2, (Data) null);
        source.next();
        verify(mockLookup, times(2)).lookup(anyString(), any(TriggerHistory.class), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        source.next();
        verify(mockLookup, times(2)).lookup(anyString(), any(TriggerHistory.class), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void testNext_withUseStreamLobsTrigger_setsRequiresLobSelectedFromSourceTrue() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        triggerRouter.getTrigger().setUseStreamLobs(true);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(50);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 50, triggerRouter);
        Data data = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        source.next();
        assertTrue(source.requiresLobsSelectedFromSource(data));
    }

    @Test
    void testNext_withLobColumnDataMarker_setsRequiresLobSelectedFromSourceTrue() throws Exception {
        SelectFromSymDataSource source = createSource();
        Table tableWithLob = buildTableWithLobColumn();
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(tableWithLob);
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,blob_col");
        triggerHistory.setTriggerHistoryId(51);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 51, triggerRouter);
        Data data = new Data(0, null, "1,\b", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        source.next();
        assertTrue(source.requiresLobsSelectedFromSource(data));
    }

    @Test
    void testNext_withInsertEventAndProtocolViolationSqlCode_corruptedParsedData_throwsProtocolException() throws Exception {
        SelectFromSymDataSource source = createSource();
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(60);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 60, triggerRouter);
        when(outgoingBatch.getSqlCode()).thenReturn(ErrorConstants.PROTOCOL_VIOLATION_CODE);
        Data data = new Data(0, null, "1", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        assertThrows(ProtocolException.class, source::next);
    }

    @Test
    void testNext_withInsertEventCorruptedAndContainsBigLob_includesRowDataInExceptionMessage() throws Exception {
        SelectFromSymDataSource source = new SelectFromSymDataSource(engine, outgoingBatch, sourceNode, targetNode, new ProcessInfo(), true);
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(61);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 61, triggerRouter);
        Data data = new Data(0, null, "1", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        ProtocolException ex = assertThrows(ProtocolException.class, source::next);
        assertTrue(ex.getMessage().contains("Corrupted row for data ID"));
    }

    @Test
    void testNext_withCreateEvent_delegatesToProcessCreateEventAndReturnsData() throws Exception {
        TestableSelectFromSymDataSource source = createTestableSource();
        source.processCreateEventResult = true;
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(20);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 20, triggerRouter);
        Data data = new Data(0, null, "", DataEventType.CREATE, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        CsvData result = source.next();
        assertSame(data, result);
        assertSame(triggerHistory, source.capturedCreateTriggerHistory);
        assertEquals("router1", source.capturedCreateRouterId);
    }

    @Test
    void testNext_withCreateEventAndProcessCreateEventReturnsFalse_returnsNull() throws Exception {
        TestableSelectFromSymDataSource source = createTestableSource();
        source.processCreateEventResult = false;
        TriggerRouter triggerRouter = buildTriggerRouter(false);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setTriggerHistoryId(21);
        triggerHistory.setTriggerId("trigger1");
        putTriggerRouterByHist(source, 21, triggerRouter);
        Data data = new Data(0, null, "", DataEventType.CREATE, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        ISqlReadCursor<Data> cursor = newCursorMock();
        when(dataService.selectDataFor(anyLong(), anyString(), anyBoolean())).thenReturn(cursor);
        when(cursor.next()).thenReturn(data, (Data) null);
        CsvData result = source.next();
        assertNull(result);
    }

    @Test
    void testProcessReloadEvent_withStreamRowAndNoInitialLoadSelect_buildsDynamicSql() throws Exception {
        TestableSelectFromSymDataSource source = createTestableSource();
        TriggerRouter triggerRouter = buildTriggerRouter(true);
        Table sourceTable = buildTableWithFksAndIndexes();
        setColumnsAccordingToTriggerHistory(source, sourceTable);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name,ref_id");
        DmlStatement dmlStmt = mock(DmlStatement.class);
        when(platform.createDmlStatement(eq(DmlType.WHERE), any(), any(), any(), any(), any(), any(), any())).thenReturn(dmlStmt);
        when(dmlStmt.buildDynamicSql(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn("select * from test_table where id = 1");
        Data data = new Data(0, "1", null, DataEventType.RELOAD, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        SelectFromTableSource reloadSourceMock = mock(SelectFromTableSource.class);
        source.reloadSourceToReturn = reloadSourceMock;
        when(reloadSourceMock.next()).thenReturn(null);
        when(reloadSourceMock.getSourceRelation()).thenReturn(sourceTable);
        when(reloadSourceMock.getTargetRelation()).thenReturn(sourceTable);
        when(reloadSourceMock.requiresLobsSelectedFromSource(isNull())).thenReturn(false);
        Data result = source.processReloadEvent(triggerHistory, triggerRouter, data);
        assertEquals("select * from test_table where id = 1", source.capturedEvent.getInitialLoadSelect());
        assertNotNull(result);
    }

    @Test
    void testProcessReloadEvent_withInitialLoadSelectProvided_usesProvidedSelect() throws Exception {
        TestableSelectFromSymDataSource source = createTestableSource();
        TriggerRouter triggerRouter = buildTriggerRouter(true);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Data data = new Data(0, null, "where 1=1", DataEventType.RELOAD, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        SelectFromTableSource reloadSourceMock = mock(SelectFromTableSource.class);
        source.reloadSourceToReturn = reloadSourceMock;
        Data fromReload = new Data(0, null, "1,foo", DataEventType.INSERT, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        when(reloadSourceMock.next()).thenReturn(fromReload);
        when(reloadSourceMock.getSourceRelation()).thenReturn(tableWithFksAndIndexes);
        when(reloadSourceMock.getTargetRelation()).thenReturn(tableWithFksAndIndexes);
        when(reloadSourceMock.requiresLobsSelectedFromSource(fromReload)).thenReturn(true);
        Data result = source.processReloadEvent(triggerHistory, triggerRouter, data);
        assertEquals("where 1=1", source.capturedEvent.getInitialLoadSelect());
        assertSame(fromReload, result);
        assertTrue(getBoolean(source, "requiresLobSelectedFromSource"));
        verify(platform, never()).createDmlStatement(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testProcessReloadEvent_whenReloadSourceReturnsNull_returnsEmptyData() throws Exception {
        TestableSelectFromSymDataSource source = createTestableSource();
        TriggerRouter triggerRouter = buildTriggerRouter(true);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Data data = new Data(0, null, "where 1=1", DataEventType.RELOAD, "test_table", new Date(), triggerHistory,
                Constants.CHANNEL_DEFAULT, null, null);
        SelectFromTableSource reloadSourceMock = mock(SelectFromTableSource.class);
        source.reloadSourceToReturn = reloadSourceMock;
        when(reloadSourceMock.next()).thenReturn(null);
        when(reloadSourceMock.getSourceRelation()).thenReturn(null);
        when(reloadSourceMock.getTargetRelation()).thenReturn(null);
        when(reloadSourceMock.requiresLobsSelectedFromSource(isNull())).thenReturn(false);
        Data result = source.processReloadEvent(triggerHistory, triggerRouter, data);
        assertNotNull(result);
        assertNull(result.getTableName());
    }

    @Test
    void testCreateSelectFromTableSource_returnsNewSelectFromTableSource() throws Exception {
        when(engine.getNodeService()).thenReturn(mock(INodeService.class));
        when(engine.getNodeService().findNode("target", true)).thenReturn(targetNode);
        when(engine.getRouterService().getRouters()).thenReturn(new HashMap<>());
        SelectFromSymDataSource source = createSource();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, buildTriggerRouter(false), triggerHistory, null);
        SelectFromTableSource result = source.createSelectFromTableSource(event);
        assertNotNull(result);
    }

    @Test
    void testEvaluateDeferTableLogging_whenNotLoadFlag_returnsFalse() {
        SelectFromSymDataSource source = createSource();
        when(outgoingBatch.isLoadFlag()).thenReturn(false);
        assertFalse(source.evaluateDeferTableLogging(outgoingBatch, false));
    }

    @Test
    void testEvaluateDeferTableLogging_whenParameterDisabled_returnsFalse() {
        SelectFromSymDataSource source = createSource();
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_TABLE_LOGGING, false)).thenReturn(false);
        assertFalse(source.evaluateDeferTableLogging(outgoingBatch, false));
    }

    @Test
    void testEvaluateDeferTableLogging_whenTableLevelLoggingNotSupported_returnsFalse() {
        SelectFromSymDataSource source = createSource();
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_TABLE_LOGGING, false)).thenReturn(true);
        DatabaseInfo info = new DatabaseInfo();
        info.setTableLevelLoggingSupported(false);
        when(platform.getDatabaseInfo()).thenReturn(info);
        assertFalse(source.evaluateDeferTableLogging(outgoingBatch, false));
    }

    @Test
    void testEvaluateDeferTableLogging_whenNoReloadRequest_returnsFalse() {
        SelectFromSymDataSource source = createSource();
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_TABLE_LOGGING, false)).thenReturn(true);
        DatabaseInfo info = new DatabaseInfo();
        info.setTableLevelLoggingSupported(true);
        when(platform.getDatabaseInfo()).thenReturn(info);
        when(dataService.getTableReloadRequest(0L)).thenReturn(null);
        assertFalse(source.evaluateDeferTableLogging(outgoingBatch, false));
    }

    @Test
    void testEvaluateDeferTableLogging_whenDeferIndicesTrue_returnsTrue() {
        SelectFromSymDataSource source = createSource();
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_TABLE_LOGGING, false)).thenReturn(true);
        DatabaseInfo info = new DatabaseInfo();
        info.setTableLevelLoggingSupported(true);
        when(platform.getDatabaseInfo()).thenReturn(info);
        TableReloadRequest reloadRequest = new TableReloadRequest();
        when(dataService.getTableReloadRequest(0L)).thenReturn(reloadRequest);
        assertTrue(source.evaluateDeferTableLogging(outgoingBatch, true));
    }

    @Test
    void testEvaluateDeferTableLogging_whenReloadRequestCreatesTable_returnsTrue() {
        SelectFromSymDataSource source = createSource();
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_TABLE_LOGGING, false)).thenReturn(true);
        DatabaseInfo info = new DatabaseInfo();
        info.setTableLevelLoggingSupported(true);
        when(platform.getDatabaseInfo()).thenReturn(info);
        TableReloadRequest reloadRequest = new TableReloadRequest();
        reloadRequest.setCreateTable(true);
        when(dataService.getTableReloadRequest(0L)).thenReturn(reloadRequest);
        assertTrue(source.evaluateDeferTableLogging(outgoingBatch, false));
    }

    @Test
    void testProcessCreateEvent_preSetupFkDrop_stripsFksKeepsIndexes() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        data.setOldData(Constants.SEND_SCHEMA_EXCLUDE_FOREIGN_KEYS);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertFalse(xml.contains("foreign-key"), "Phase 1 setup batch should strip foreign keys");
        assertTrue(xml.contains("index"), "Phase 1 setup batch should keep indexes");
    }

    @Test
    void testProcessCreateEvent_setupBatch_stripsBothFksAndIndexes() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertFalse(xml.contains("foreign-key"), "Phase 2 setup batch should strip foreign keys");
        assertFalse(xml.contains("index"), "Phase 2 setup batch should strip indexes");
    }

    @Test
    void testProcessCreateEvent_phase1Finalize_stripsFksKeepsIndexes() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(false);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        data.setOldData(Constants.SEND_SCHEMA_EXCLUDE_FOREIGN_KEYS);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertFalse(xml.contains("foreign-key"), "Phase 1 finalize batch should strip foreign keys");
        assertTrue(xml.contains("index"), "Phase 1 finalize batch should keep indexes");
    }

    @Test
    void testProcessCreateEvent_phase2Finalize_keepsAll() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(false);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertTrue(xml.contains("foreign-key"), "Phase 2 finalize batch should keep foreign keys");
        assertTrue(xml.contains("index"), "Phase 2 finalize batch should keep indexes");
    }

    @Test
    void testProcessCreateEvent_loadAlreadyComplete_discardsEventAndWarns() throws Exception {
        when(engine.getNodeId()).thenReturn("source");
        when(outgoingBatch.getLoadId()).thenReturn(2L);
        when(outgoingBatch.getSummary()).thenReturn("test_table");
        TableReloadStatus completedLoad = new TableReloadStatus();
        completedLoad.setLoadId(2);
        completedLoad.setCompleted(true);
        when(dataService.getTableReloadStatusByLoadIdAndSourceNodeId(2, "source")).thenReturn(completedLoad);
        SelectFromSymDataSource source = createSource();
        Logger log = injectMockLogger(source);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertFalse(result, "a create event for an already-completed load must be discarded");
        verify(log).warn(contains("Discarding create event"), any(), any(), any());
    }

    @Test
    void testProcessCreateEvent_loadNotComplete_isNotDiscardedByCompletedLoadGuard() throws Exception {
        when(engine.getNodeId()).thenReturn("source");
        when(outgoingBatch.getLoadId()).thenReturn(2L);
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        TableReloadStatus inFlightLoad = new TableReloadStatus();
        inFlightLoad.setLoadId(2);
        inFlightLoad.setCompleted(false);
        when(dataService.getTableReloadStatusByLoadIdAndSourceNodeId(2, "source")).thenReturn(inFlightLoad);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        Logger log = injectMockLogger(source);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result, "a create event for an in-flight load must be processed");
        verify(log, never()).warn(contains("Discarding create event"), any(), any(), any());
    }

    @Test
    void testProcessCreateEvent_withDdlGeneratedMarker_refreshesTriggerHistoryFromActiveList() throws Exception {
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        ColumnsAccordingToTriggerHistory mockLookup = injectColumnsLookupMock(source);
        ArgumentCaptor<TriggerHistory> historyCaptor = ArgumentCaptor.forClass(TriggerHistory.class);
        when(mockLookup.lookup(anyString(), historyCaptor.capture(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(buildTableWithFksAndIndexes());
        TriggerHistory oldHist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        oldHist.setTriggerId("trigger1");
        Trigger trigger = new Trigger();
        trigger.setTriggerId("trigger1");
        when(triggerRouterService.getTriggerById("trigger1")).thenReturn(trigger);
        TriggerHistory newHist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        when(triggerRouterService.getActiveTriggerHistories(trigger)).thenReturn(List.of(newHist));
        Data data = new Data("test_table", DataEventType.CREATE, AbstractTriggerTemplate.CREATE_EVENT_DDL_GENERATED, null, oldHist,
                Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(oldHist, "router1", data);
        assertTrue(result);
        verify(triggerRouterService).syncTriggers(List.of(trigger), null, true, false, false);
        assertSame(newHist, historyCaptor.getValue());
    }

    @Test
    void testProcessCreateEvent_withExcludeDefaultsParameter_stripsColumnDefaults() throws Exception {
        when(parameterService.is(ParameterConstants.CREATE_TABLE_WITHOUT_DEFAULTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        Table tableWithDefault = buildTableWithFksAndIndexes();
        tableWithDefault.getColumnWithName("name").setDefaultValue("foo");
        setColumnsAccordingToTriggerHistory(source, tableWithDefault);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        assertFalse(data.getRowData().contains("default="), "default value should be stripped from XML");
    }

    @Test
    void testProcessCreateEvent_withExcludeIndexesParameter_stripsIndexesButKeepsForeignKeys() throws Exception {
        when(parameterService.is(ParameterConstants.CREATE_TABLE_WITHOUT_INDEXES, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertFalse(xml.contains("index"), "indexes should be stripped");
        assertTrue(xml.contains("foreign-key"), "foreign keys should be kept");
    }

    @Test
    void testProcessCreateEvent_withOldDataExcludeIndicesToken_stripsIndexesOnly() throws Exception {
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        data.setOldData(Constants.SEND_SCHEMA_EXCLUDE_INDICES);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertFalse(xml.contains("index"), "indexes should be stripped");
        assertTrue(xml.contains("foreign-key"), "foreign keys should be kept");
    }

    @Test
    void testProcessCreateEvent_withOldDataExcludeDefaultsToken_stripsDefaultsOnly() throws Exception {
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        Table tableWithDefault = buildTableWithFksAndIndexes();
        tableWithDefault.getColumnWithName("name").setDefaultValue("foo");
        setColumnsAccordingToTriggerHistory(source, tableWithDefault);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        data.setOldData(Constants.SEND_SCHEMA_EXCLUDE_DEFAULTS);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        assertFalse(data.getRowData().contains("default="), "default value should be stripped from XML");
    }

    @Test
    void testProcessCreateEvent_withDeferTableLoggingTrue_setsTableLoggingFalse() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_TABLE_LOGGING, false)).thenReturn(true);
        DatabaseInfo info = new DatabaseInfo();
        info.setTableLevelLoggingSupported(true);
        when(platform.getDatabaseInfo()).thenReturn(info);
        TableReloadRequest reloadRequest = new TableReloadRequest();
        reloadRequest.setCreateTable(true);
        when(dataService.getTableReloadRequest(0L)).thenReturn(reloadRequest);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        assertTrue(data.getRowData().contains("logging=\\\"false\\\""), "logging attribute is only written when table logging is deferred");
    }

    @Test
    void testProcessCreateEvent_withIncludeTriggerDdlAndTriggersFound_addsTriggersToTable() throws Exception {
        when(parameterService.is(ParameterConstants.CREATE_TABLE_INCLUDE_APPLICATION_TRIGGERS, false)).thenReturn(true);
        when(symmetricDialect.getTablePrefix()).thenReturn("sym");
        IDdlReader ddlReader = mock(IDdlReader.class);
        when(platform.getDdlReader()).thenReturn(ddlReader);
        org.jumpmind.db.model.Trigger dbTrigger = new org.jumpmind.db.model.Trigger();
        dbTrigger.setName("trg1");
        when(ddlReader.getApplicationTriggersForModel(any(), any(), any(), any())).thenReturn(List.of(dbTrigger));
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        verify(ddlReader).getApplicationTriggersForModel(any(), any(), any(), eq("sym"));
    }

    @Test
    void testProcessCreateEvent_withSourceWithoutPkParameter_stripsPrimaryKeyFromTargetTable() throws Exception {
        when(parameterService.is(ParameterConstants.CREATE_TABLE_WITHOUT_PK_IF_SOURCE_WITHOUT_PK, false)).thenReturn(true);
        Table sourceTableNoPk = buildTableWithFksAndIndexes();
        sourceTableNoPk.getColumnWithName("id").setPrimaryKey(false);
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(sourceTableNoPk);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = sourceTableNoPk;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        assertFalse(data.getRowData().contains("primaryKey="), "target table primary keys should be stripped");
    }

    @Test
    void testProcessCreateEvent_withMysqlTinyintToBooleanParameter_remapsTinyintColumns() throws Exception {
        when(parameterService.is(ParameterConstants.MYSQL_TINYINT_DDL_TO_BOOLEAN, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        Table tableWithTinyint = buildTableWithFksAndIndexes();
        Column flagCol = new Column("flag_col");
        flagCol.setJdbcTypeCode(Types.TINYINT);
        flagCol.setMappedTypeCode(Types.TINYINT);
        tableWithTinyint.addColumn(flagCol);
        setColumnsAccordingToTriggerHistory(source, tableWithTinyint);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id,flag_col");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        assertTrue(data.getRowData().contains("BOOLEAN"));
        assertFalse(data.getRowData().contains("TINYINT"));
    }

    @Test
    void testProcessCreateEvent_withSybaseAseUnitextParameter_remapsToClob() throws Exception {
        when(parameterService.is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        Table tableWithUnitext = new Table("test_table");
        Column idCol = new Column("id");
        idCol.setPrimaryKey(true);
        idCol.addPlatformColumn(new PlatformColumn());
        Column uniTextCol = new Column("utext_col");
        PlatformColumn asePlatformColumn = new PlatformColumn();
        asePlatformColumn.setName(DatabaseNamesConstants.ASE);
        asePlatformColumn.setType("UNITEXT");
        uniTextCol.addPlatformColumn(asePlatformColumn);
        tableWithUnitext.addColumn(idCol);
        tableWithUnitext.addColumn(uniTextCol);
        setColumnsAccordingToTriggerHistory(source, tableWithUnitext);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,utext_col");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        assertTrue(data.getRowData().contains("CLOB"));
    }

    @Test
    void testProcessCreateEvent_withSybaseAseUnicharParameter_remapsToChar() throws Exception {
        when(parameterService.is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        Table tableWithUnichar = new Table("test_table");
        Column idCol = new Column("id");
        idCol.setPrimaryKey(true);
        idCol.addPlatformColumn(new PlatformColumn());
        Column uniCharCol = new Column("uchar_col");
        PlatformColumn asePlatformColumn = new PlatformColumn();
        asePlatformColumn.setName(DatabaseNamesConstants.ASE);
        asePlatformColumn.setType("UNICHAR");
        uniCharCol.addPlatformColumn(asePlatformColumn);
        tableWithUnichar.addColumn(idCol);
        tableWithUnichar.addColumn(uniCharCol);
        setColumnsAccordingToTriggerHistory(source, tableWithUnichar);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,uchar_col");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        assertTrue(data.getRowData().contains("CHAR"));
    }

    @Test
    void testProcessCreateEvent_withSybaseAseUnivarcharParameter_remapsToVarchar() throws Exception {
        when(parameterService.is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        Table tableWithUnivarchar = new Table("test_table");
        Column idCol = new Column("id");
        idCol.setPrimaryKey(true);
        idCol.addPlatformColumn(new PlatformColumn());
        Column uniVarcharCol = new Column("uvarchar_col");
        PlatformColumn asePlatformColumn = new PlatformColumn();
        asePlatformColumn.setName(DatabaseNamesConstants.ASE);
        asePlatformColumn.setType("UNIVARCHAR");
        uniVarcharCol.addPlatformColumn(asePlatformColumn);
        tableWithUnivarchar.addColumn(idCol);
        tableWithUnivarchar.addColumn(uniVarcharCol);
        setColumnsAccordingToTriggerHistory(source, tableWithUnivarchar);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,uvarchar_col");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        assertTrue(data.getRowData().contains("VARCHAR"));
    }

    @Test
    void testProcessCreateEvent_withNonTableTargetRelation_ignoresViewAndReturnsFalse() throws Exception {
        SelectFromSymDataSource source = createSource();
        source.sourceRelation = tableWithFksAndIndexes;
        Relation viewRelation = mock(Relation.class);
        ColumnsAccordingToTriggerHistory mockLookup = injectColumnsLookupMock(source);
        when(mockLookup.lookup(anyString(), any(TriggerHistory.class), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(viewRelation);
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, Constants.CHANNEL_DEFAULT, null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertFalse(result);
    }

    @Test
    void testRequiresLobsSelectedFromSource_returnsFieldValue() throws Exception {
        SelectFromSymDataSource source = createSource();
        setBoolean(source, "requiresLobSelectedFromSource", true);
        assertTrue(source.requiresLobsSelectedFromSource(mock(CsvData.class)));
        setBoolean(source, "requiresLobSelectedFromSource", false);
        assertFalse(source.requiresLobsSelectedFromSource(mock(CsvData.class)));
    }

    @Test
    void testCloseCursor_closesCursorAndClearsRelationsAndBatch() throws Exception {
        SelectFromSymDataSource source = createSource();
        ISqlReadCursor<Data> cursor = newCursorMock();
        setCursor(source, cursor);
        source.sourceRelation = tableWithFksAndIndexes;
        source.targetRelation = tableWithFksAndIndexes;
        source.closeCursor();
        verify(cursor).close();
        assertNull(getCursor(source));
        assertNull(source.getBatch());
        assertNull(source.getSourceRelation());
        assertNull(source.getTargetRelation());
    }

    @Test
    void testClose_delegatesToCloseCursorAndReloadSourceAndLogsMissingTriggerRouters() throws Exception {
        TestableSelectFromSymDataSource source = createTestableSource();
        ISqlReadCursor<Data> cursor = newCursorMock();
        setCursor(source, cursor);
        SelectFromTableSource reloadSourceMock = mock(SelectFromTableSource.class);
        setReloadSource(source, reloadSourceMock);
        Logger log = injectMockLogger(source);
        Map<Integer, CounterStat> missing = getMissingTriggerRoutersMap(source);
        missing.put(42, new CounterStat(99L, 3));
        source.close();
        verify(cursor).close();
        verify(reloadSourceMock).close();
        verify(log).warn(contains("Could not find trigger router"), eq(42), eq(3L), eq(99L));
    }

    private Table buildTableWithFksAndIndexes() {
        Column idCol = new Column("id");
        idCol.setPrimaryKey(true);
        Column nameCol = new Column("name");
        Column refCol = new Column("ref_id");
        Table table = new Table("test_table");
        table.addColumn(idCol);
        table.addColumn(nameCol);
        table.addColumn(refCol);
        ForeignKey fk = new ForeignKey("fk_ref", "other_table");
        fk.addReference(new Reference(refCol, new Column("id")));
        table.addForeignKey(fk);
        NonUniqueIndex idx = new NonUniqueIndex("idx_name");
        idx.addColumn(new IndexColumn(nameCol));
        table.addIndex(idx);
        return table;
    }

    private Table buildTableWithLobColumn() {
        Table table = new Table("test_table");
        table.addColumn(new Column("id"));
        table.addColumn(new Column("blob_col"));
        when(platform.isLob(any(Column.class))).thenAnswer(invocation -> "blob_col".equals(((Column) invocation.getArgument(0)).getName()));
        return table;
    }

    private SelectFromSymDataSource createSource() {
        return new SelectFromSymDataSource(engine, outgoingBatch, sourceNode, targetNode, new ProcessInfo(), false);
    }

    private TestableSelectFromSymDataSource createTestableSource() {
        return new TestableSelectFromSymDataSource(engine, outgoingBatch, sourceNode, targetNode, new ProcessInfo(), false);
    }

    private void setColumnsAccordingToTriggerHistory(SelectFromSymDataSource source, Table targetTable) throws Exception {
        ColumnsAccordingToTriggerHistory mockLookup = mock(ColumnsAccordingToTriggerHistory.class);
        when(mockLookup.lookup(anyString(), any(TriggerHistory.class), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean())).thenReturn(targetTable);
        Field field = SelectFromSymDataSource.class.getDeclaredField("columnsAccordingToTriggerHistory");
        field.setAccessible(true);
        field.set(source, mockLookup);
    }

    private ColumnsAccordingToTriggerHistory injectColumnsLookupMock(SelectFromSymDataSource source) throws Exception {
        ColumnsAccordingToTriggerHistory mockLookup = mock(ColumnsAccordingToTriggerHistory.class);
        Field field = SelectFromSymDataSource.class.getDeclaredField("columnsAccordingToTriggerHistory");
        field.setAccessible(true);
        field.set(source, mockLookup);
        return mockLookup;
    }

    private Logger injectMockLogger(SelectFromSymDataSource source) throws Exception {
        Logger log = mock(Logger.class);
        Field field = SelectFromSymDataSource.class.getDeclaredField("log");
        field.setAccessible(true);
        field.set(source, log);
        return log;
    }

    private TriggerRouter buildTriggerRouter(boolean streamRow) {
        Router router = new Router("router1", "server", "client", "default");
        Trigger trigger = new Trigger();
        trigger.setChannelId(Constants.CHANNEL_DEFAULT);
        trigger.setStreamRow(streamRow);
        return new TriggerRouter(trigger, router);
    }

    @SuppressWarnings("unchecked")
    private ISqlReadCursor<Data> newCursorMock() {
        return mock(ISqlReadCursor.class);
    }

    private boolean getBoolean(Object target, String fieldName) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private void setCursor(SelectFromSymDataSource source, ISqlReadCursor<Data> cursor) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField("cursor");
        field.setAccessible(true);
        field.set(source, cursor);
    }

    @SuppressWarnings("unchecked")
    private ISqlReadCursor<Data> getCursor(SelectFromSymDataSource source) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField("cursor");
        field.setAccessible(true);
        return (ISqlReadCursor<Data>) field.get(source);
    }

    private void setReloadSource(SelectFromSymDataSource source, SelectFromTableSource reloadSource) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField("reloadSource");
        field.setAccessible(true);
        field.set(source, reloadSource);
    }

    private SelectFromTableSource getReloadSource(SelectFromSymDataSource source) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField("reloadSource");
        field.setAccessible(true);
        return (SelectFromTableSource) field.get(source);
    }

    private void putTriggerRouterByHist(SelectFromSymDataSource source, int triggerHistoryId, TriggerRouter triggerRouter) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField("triggerRoutersByTriggerHist");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, TriggerRouter> map = (Map<Integer, TriggerRouter>) field.get(source);
        map.put(triggerHistoryId, triggerRouter);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, TriggerRouter> getTriggerRoutersMap(SelectFromSymDataSource source) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField("triggerRoutersByTriggerHist");
        field.setAccessible(true);
        return (Map<Integer, TriggerRouter>) field.get(source);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, CounterStat> getMissingTriggerRoutersMap(SelectFromSymDataSource source) throws Exception {
        Field field = SelectFromSymDataSource.class.getDeclaredField("missingTriggerRoutersByTriggerHist");
        field.setAccessible(true);
        return (Map<Integer, CounterStat>) field.get(source);
    }

    private static class TestableSelectFromSymDataSource extends SelectFromSymDataSource {
        SelectFromTableSource reloadSourceToReturn;
        SelectFromTableEvent capturedEvent;
        Boolean processCreateEventResult;
        TriggerHistory capturedCreateTriggerHistory;
        String capturedCreateRouterId;

        TestableSelectFromSymDataSource(ISymmetricEngine engine, OutgoingBatch outgoingBatch, Node sourceNode, Node targetNode,
                ProcessInfo processInfo, boolean containsBigLob) {
            super(engine, outgoingBatch, sourceNode, targetNode, processInfo, containsBigLob);
        }

        @Override
        protected SelectFromTableSource createSelectFromTableSource(SelectFromTableEvent event) {
            capturedEvent = event;
            return reloadSourceToReturn;
        }

        @Override
        protected boolean processCreateEvent(TriggerHistory triggerHistory, String routerId, Data data) {
            capturedCreateTriggerHistory = triggerHistory;
            capturedCreateRouterId = routerId;
            if (processCreateEventResult != null) {
                return processCreateEventResult;
            }
            return super.processCreateEvent(triggerHistory, routerId, data);
        }
    }
}
