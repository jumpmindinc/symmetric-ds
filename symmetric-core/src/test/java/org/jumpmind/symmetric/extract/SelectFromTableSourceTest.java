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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.load.IReloadVariableFilter;
import org.jumpmind.symmetric.load.IRelationReloadVariableFilter;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.DefaultDataRouter;
import org.jumpmind.symmetric.route.IDataRouter;
import org.jumpmind.symmetric.route.SimpleRouterContext;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SelectFromTableSourceTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private INodeService nodeService;
    private IRouterService routerService;
    private IDatabasePlatform platform;
    private ISymmetricDialect symmetricDialect;
    private IConfigurationService configurationService;
    private IExtensionService extensionService;
    private Node targetNode;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        IDataService dataService = mock(IDataService.class);
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        platform = mock(IDatabasePlatform.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        nodeService = mock(INodeService.class);
        routerService = mock(IRouterService.class);
        configurationService = mock(IConfigurationService.class);
        extensionService = mock(IExtensionService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
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
        when(symmetricDialect.getTargetDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(triggerRouterService.getTriggerRoutersByTriggerHist(anyString(), anyBoolean())).thenReturn(null);
        targetNode = new Node("target", "client");
        Node identityNode = new Node("source", "server");
        when(nodeService.findNode("target", true)).thenReturn(targetNode);
        when(nodeService.findIdentity()).thenReturn(identityNode);
        Map<String, IDataRouter> routers = new HashMap<>();
        routers.put("default", new DefaultDataRouter());
        when(routerService.getRouters()).thenReturn(routers);
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(buildTable("test_table", "id", "name"));
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_USE_COLUMN_TEMPLATES_ENABLED)).thenReturn(true);
    }

    @Test
    void testNew_withSingleEvent_initializesFieldsAndResetsExtractStats() {
        OutgoingBatch outgoingBatch = mock(OutgoingBatch.class);
        Batch batch = buildBatch();
        SelectFromTableEvent event = buildDataDrivenEvent();
        SelectFromTableSource source = new SelectFromTableSource(engine, outgoingBatch, batch, event);
        assertSame(outgoingBatch, source.outgoingBatch);
        assertEquals(1, source.selectFromTableEventsToSend.size());
        assertSame(targetNode, source.node);
        assertTrue(source.nodeSet.contains(targetNode));
        verify(outgoingBatch).resetExtractRowStats();
    }

    @Test
    void testNew_withEventList_initializesFields() {
        Batch batch = buildBatch();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, buildTriggerRouter(), triggerHistory, null);
        List<SelectFromTableEvent> events = new ArrayList<>(List.of(event));
        SelectFromTableSource source = new SelectFromTableSource(engine, batch, events);
        assertEquals(1, source.selectFromTableEventsToSend.size());
        assertSame(targetNode, source.node);
    }

    @Test
    void testInit_whenNodeNotFound_throwsSymmetricException() {
        when(nodeService.findNode("target", true)).thenReturn(null);
        Batch batch = buildBatch();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, buildTriggerRouter(), triggerHistory, null);
        List<SelectFromTableEvent> events = new ArrayList<>(List.of(event));
        assertThrows(SymmetricException.class, () -> new SelectFromTableSource(engine, batch, events));
    }

    @Test
    void testSetConfiguration() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.setConfiguration(true);
        assertTrue(source.isConfiguration);
        source.setConfiguration(false);
        assertFalse(source.isConfiguration);
    }

    @Test
    void testNext_withDataDrivenEvent_returnsDataAndSetsRelations() {
        OutgoingBatch outgoingBatch = mock(OutgoingBatch.class);
        when(outgoingBatch.isExtractJobFlag()).thenReturn(false);
        Batch batch = buildBatch();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Data data = new Data("test_table", DataEventType.INSERT, "1,foo", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        SelectFromTableEvent event = new SelectFromTableEvent(data, buildTriggerRouter());
        SelectFromTableSource source = new SelectFromTableSource(engine, outgoingBatch, batch, event);
        CsvData result = source.next();
        assertSame(data, result);
        assertTrue(source.getSourceRelation() instanceof Table);
        assertTrue(source.getTargetRelation() instanceof Table);
        verify(outgoingBatch).incrementExtractRowCount();
        verify(outgoingBatch).incrementExtractRowCount(DataEventType.INSERT);
    }

    @Test
    void testNext_withNoEventsRemaining_returnsNull() {
        Batch batch = buildBatch();
        SelectFromTableSource source = new SelectFromTableSource(engine, batch, new ArrayList<>());
        assertNull(source.next());
    }

    @Test
    void testShouldDataBeRouted_whenNodeIdInRoutedSet_returnsTrue() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.sourceRelation = buildTable("test_table", "id", "name");
        source.triggerRouter = buildTriggerRouter();
        source.routingContext = new SimpleRouterContext("target", new NodeChannel(Constants.CHANNEL_DEFAULT));
        IDataRouter router = mock(IDataRouter.class);
        source.dataRouter = router;
        Set<String> nodeIds = new HashSet<>(List.of("target"));
        when(router.routeToNodes(any(), any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(nodeIds);
        assertTrue(source.shouldDataBeRouted(mock(Data.class)));
    }

    @Test
    void testShouldDataBeRouted_whenNodeIdNotInRoutedSet_returnsFalse() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.sourceRelation = buildTable("test_table", "id", "name");
        source.triggerRouter = buildTriggerRouter();
        source.routingContext = new SimpleRouterContext("target", new NodeChannel(Constants.CHANNEL_DEFAULT));
        IDataRouter router = mock(IDataRouter.class);
        source.dataRouter = router;
        when(router.routeToNodes(any(), any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(null);
        assertFalse(source.shouldDataBeRouted(mock(Data.class)));
    }

    @Test
    void testSelectNext_withRouterDrivenEvent_startsCursorAndReturnsFirstRow() {
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        Batch batch = buildBatch();
        SelectFromTableSource source = new SelectFromTableSource(engine, batch, new ArrayList<>(List.of(event)));
        ISqlReadCursor<Data> sourceCursor = newCursorMock();
        Data row = new Data("test_table", DataEventType.INSERT, "1,foo", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        when(sourceCursor.next()).thenReturn(row);
        doReturn(sourceCursor).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), selectSqlCaptor.capture())).thenReturn("select * from test_table");
        CsvData result = source.next();
        assertSame(row, result);
        assertNull(selectSqlCaptor.getValue());
        assertFalse(source.isSelfReferencingFk);
    }

    @Test
    void testSelectNext_withSelfReferencingFkEnabledAndNoRows_buildsLevelZeroSelectSql() {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_RECURSION_SELF_FK)).thenReturn(true);
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        Table selfRefTable = buildTable("test_table", "id", "parent_id");
        ForeignKey selfFk = new ForeignKey("fk_self", "test_table");
        selfFk.addReference(new Reference(selfRefTable.getColumnWithName("parent_id"), selfRefTable.getColumnWithName("id")));
        selfRefTable.addForeignKey(selfFk);
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(selfRefTable);
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,parent_id");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        Batch batch = buildBatch();
        SelectFromTableSource source = new SelectFromTableSource(engine, batch, new ArrayList<>(List.of(event)));
        ISqlReadCursor<Data> sourceCursor = newCursorMock();
        when(sourceCursor.next()).thenReturn(null);
        doReturn(sourceCursor).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), selectSqlCaptor.capture())).thenReturn("select * from test_table");
        assertNull(source.next());
        assertTrue(source.isSelfReferencingFk);
        assertEquals(0, source.selfRefLevel);
        assertTrue(selectSqlCaptor.getValue().contains("\"parent_id\" is null or \"parent_id\" = \"id\""));
    }

    @Test
    void testSelectNext_withSelfReferencingFkAfterFirstRow_buildsLevelOneSelectSql() {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_RECURSION_SELF_FK)).thenReturn(true);
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        Table selfRefTable = buildTable("test_table", "id", "parent_id");
        ForeignKey selfFk = new ForeignKey("fk_self", "test_table");
        selfFk.addReference(new Reference(selfRefTable.getColumnWithName("parent_id"), selfRefTable.getColumnWithName("id")));
        selfRefTable.addForeignKey(selfFk);
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(selfRefTable);
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,parent_id");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        Batch batch = buildBatch();
        SelectFromTableSource source = new SelectFromTableSource(engine, batch, new ArrayList<>(List.of(event)));
        ISqlReadCursor<Data> cursorLevel0 = newCursorMock();
        Data row1 = new Data("test_table", DataEventType.INSERT, "1,", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        when(cursorLevel0.next()).thenReturn(row1, (Data) null);
        ISqlReadCursor<Data> cursorLevel1 = newCursorMock();
        Data row2 = new Data("test_table", DataEventType.INSERT, "2,", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        when(cursorLevel1.next()).thenReturn(row2);
        doReturn(cursorLevel0, cursorLevel1).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), selectSqlCaptor.capture())).thenReturn("select * from test_table");
        assertSame(row1, source.next());
        assertSame(row2, source.next());
        assertEquals(1, source.selfRefLevel);
        List<String> selectSqls = selectSqlCaptor.getAllValues();
        assertEquals(2, selectSqls.size());
        assertTrue(selectSqls.get(0).contains("is null or"));
        assertTrue(selectSqls.get(1).contains("\"parent_id\" in ("));
    }

    @Test
    void testSelectNext_withCustomRouterType_looksUpDataRouterByType() {
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        ISqlReadCursor<Data> sourceCursor = newCursorMock();
        when(sourceCursor.next()).thenReturn(null);
        doReturn(sourceCursor).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        IDataRouter customRouter = mock(IDataRouter.class);
        routerService.getRouters().put("custom", customRouter);
        Router router = new Router("router1", "server", "client", "custom");
        Trigger trigger = new Trigger();
        trigger.setChannelId(Constants.CHANNEL_DEFAULT);
        TriggerRouter triggerRouter = new TriggerRouter(trigger, router);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        SelectFromTableSource source = new SelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertSame(customRouter, source.dataRouter);
        assertFalse(source.isDefaultRouter);
    }

    @Test
    void testSelectNext_withUnknownRouterType_fallsBackToDefaultRouter() {
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        ISqlReadCursor<Data> sourceCursor = newCursorMock();
        when(sourceCursor.next()).thenReturn(null);
        doReturn(sourceCursor).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        Router router = new Router("router1", "server", "client", "unknown");
        Trigger trigger = new Trigger();
        trigger.setChannelId(Constants.CHANNEL_DEFAULT);
        TriggerRouter triggerRouter = new TriggerRouter(trigger, router);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        SelectFromTableSource source = new SelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertTrue(source.isDefaultRouter);
        assertSame(routerService.getRouters().get("default"), source.dataRouter);
    }

    @Test
    void testSelectNext_withNodeChannelFromConfigurationService_usesReturnedChannel() {
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        ISqlReadCursor<Data> sourceCursor = newCursorMock();
        when(sourceCursor.next()).thenReturn(null);
        doReturn(sourceCursor).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        NodeChannel nodeChannel = new NodeChannel(Constants.CHANNEL_DEFAULT);
        when(configurationService.getNodeChannel(Constants.CHANNEL_DEFAULT, false)).thenReturn(nodeChannel);
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        SelectFromTableSource source = new SelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertSame(nodeChannel, source.routingContext.getChannel());
    }

    @Test
    void testSelectNext_withWhereInitialLoadSelect_stripsWherePrefix() {
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        ISqlReadCursor<Data> sourceCursor = newCursorMock();
        when(sourceCursor.next()).thenReturn(null);
        doReturn(sourceCursor).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), selectSqlCaptor.capture())).thenReturn("select * from test_table");
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, "WHERE foo=1");
        SelectFromTableSource source = new SelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertEquals(" foo=1", selectSqlCaptor.getValue());
    }

    @Test
    void testSelectNext_withMultiColumnSelfReferencingFk_skipsSelfRefDetection() {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_RECURSION_SELF_FK)).thenReturn(true);
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        Table selfRefTable = buildTable("test_table", "id", "parent_id", "parent_id2");
        ForeignKey selfFk = new ForeignKey("fk_self", "test_table");
        selfFk.addReference(new Reference(selfRefTable.getColumnWithName("parent_id"), selfRefTable.getColumnWithName("id")));
        selfFk.addReference(new Reference(selfRefTable.getColumnWithName("parent_id2"), selfRefTable.getColumnWithName("id")));
        selfRefTable.addForeignKey(selfFk);
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(selfRefTable);
        ISqlReadCursor<Data> sourceCursor = newCursorMock();
        when(sourceCursor.next()).thenReturn(null);
        doReturn(sourceCursor).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,parent_id,parent_id2");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        SelectFromTableSource source = new SelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertFalse(source.isSelfReferencingFk);
    }

    @Test
    void testSelectNext_withPendingCreateTableAndDeferConstraints_skipsSelfRefDetection() {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_RECURSION_SELF_FK)).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS)).thenReturn(true);
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        Table selfRefTable = buildTable("test_table", "id", "parent_id");
        ForeignKey selfFk = new ForeignKey("fk_self", "test_table");
        selfFk.addReference(new Reference(selfRefTable.getColumnWithName("parent_id"), selfRefTable.getColumnWithName("id")));
        selfRefTable.addForeignKey(selfFk);
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(selfRefTable);
        OutgoingBatch outgoingBatch = mock(OutgoingBatch.class);
        when(outgoingBatch.getLoadId()).thenReturn(7L);
        TableReloadRequest loadRequest = new TableReloadRequest();
        loadRequest.setCreateTable(true);
        when(engine.getDataService().getTableReloadRequest(7L)).thenReturn(loadRequest);
        ISqlReadCursor<Data> sourceCursor = newCursorMock();
        when(sourceCursor.next()).thenReturn(null);
        doReturn(sourceCursor).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,parent_id");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        SelectFromTableSource source = new SelectFromTableSource(engine, outgoingBatch, buildBatch(), event);
        source.next();
        assertFalse(source.isSelfReferencingFk);
    }

    @Test
    void testSelectNext_withSelfReferencingFkAfterTwoRows_buildsLevelTwoSelectSql() {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_RECURSION_SELF_FK)).thenReturn(true);
        ISqlTemplate sqlTemplate = stubStartNewCursorDependencies();
        Table selfRefTable = buildTable("test_table", "id", "parent_id");
        ForeignKey selfFk = new ForeignKey("fk_self", "test_table");
        selfFk.addReference(new Reference(selfRefTable.getColumnWithName("parent_id"), selfRefTable.getColumnWithName("id")));
        selfRefTable.addForeignKey(selfFk);
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(selfRefTable);
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,parent_id");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        SelectFromTableSource source = new SelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        ISqlReadCursor<Data> cursorLevel0 = newCursorMock();
        Data row1 = new Data("test_table", DataEventType.INSERT, "1,", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        when(cursorLevel0.next()).thenReturn(row1, (Data) null);
        ISqlReadCursor<Data> cursorLevel1 = newCursorMock();
        Data row2 = new Data("test_table", DataEventType.INSERT, "2,", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        when(cursorLevel1.next()).thenReturn(row2, (Data) null);
        ISqlReadCursor<Data> cursorLevel2 = newCursorMock();
        Data row3 = new Data("test_table", DataEventType.INSERT, "3,", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        when(cursorLevel2.next()).thenReturn(row3);
        doReturn(cursorLevel0, cursorLevel1, cursorLevel2).when(sqlTemplate).queryForCursor(anyString(), any(), anyBoolean());
        ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), selectSqlCaptor.capture())).thenReturn("select * from test_table");
        assertSame(row1, source.next());
        assertSame(row2, source.next());
        assertSame(row3, source.next());
        assertEquals(2, source.selfRefLevel);
        List<String> selectSqls = selectSqlCaptor.getAllValues();
        assertEquals(3, selectSqls.size());
        String expectedLevelTwoSql = "\"parent_id\" in (select \"id\" from \"test_table\" where \"parent_id\" in "
                + "(select \"id\" from \"test_table\" where \"parent_id\" is null or \"id\" = \"parent_id\" ) and \"parent_id\" != \"id\")";
        assertEquals(expectedLevelTwoSql, selectSqls.get(2));
    }

    @Test
    void testStartNewCursor_withTwoPassLobEnabled_computesLobSqlForBothPasses() {
        stubStartNewCursorDependencies();
        when(symmetricDialect.isInitialLoadTwoPassLob(any())).thenReturn(true);
        when(symmetricDialect.getInitialLoadTwoPassLobSql(any(), any(), eq(true))).thenReturn("select first-pass-lob");
        when(symmetricDialect.getInitialLoadTwoPassLobSql(any(), any(), eq(false))).thenReturn("select second-pass-lob");
        NodeChannel nodeChannel = new NodeChannel(Constants.CHANNEL_DEFAULT);
        nodeChannel.setReloadFlag(true);
        when(configurationService.getNodeChannel(Constants.CHANNEL_DEFAULT, false)).thenReturn(nodeChannel);
        Channel reloadChannel = new Channel();
        reloadChannel.setReloadFlag(true);
        when(configurationService.getChannel(any())).thenReturn(reloadChannel);
        ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), selectSqlCaptor.capture())).thenReturn("select * from test_table");
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        TestableSelectFromTableSource source = new TestableSelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        assertNull(source.next());
        List<String> selectSqls = selectSqlCaptor.getAllValues();
        assertEquals(2, selectSqls.size());
        assertEquals("select first-pass-lob", selectSqls.get(0));
        assertEquals("select second-pass-lob", selectSqls.get(1));
        assertFalse(source.isLobFirstPass);
    }

    @SuppressWarnings("removal")
    @Test
    void testStartNewCursor_withLegacyInterfaceEnabled_appliesReloadVariableFilterToSql() {
        stubStartNewCursorDependencies();
        when(parameterService.is(ParameterConstants.EXTENSION_USE_LEGACY_INTERFACE)).thenReturn(true);
        IReloadVariableFilter legacyFilter = mock(IReloadVariableFilter.class);
        when(legacyFilter.filterInitalLoadSql(anyString(), any(), any())).thenReturn("legacy-filtered-sql");
        when(extensionService.getExtensionPointList(IReloadVariableFilter.class))
                .thenReturn(Collections.singletonList(legacyFilter));
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        TestableSelectFromTableSource source = new TestableSelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertEquals(1, source.capturedOptions.size());
        assertEquals("legacy-filtered-sql", source.capturedOptions.get(0).getInitialLoadSql());
    }

    @Test
    void testStartNewCursor_withRelationReloadVariableFilter_appliesFilterToSql() {
        stubStartNewCursorDependencies();
        IRelationReloadVariableFilter relationFilter = mock(IRelationReloadVariableFilter.class);
        when(relationFilter.filterInitalLoadSql(anyString(), any(), any())).thenReturn("relation-filtered-sql");
        when(extensionService.getExtensionPointList(IRelationReloadVariableFilter.class))
                .thenReturn(Collections.singletonList(relationFilter));
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        TestableSelectFromTableSource source = new TestableSelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertEquals(1, source.capturedOptions.size());
        assertEquals("relation-filtered-sql", source.capturedOptions.get(0).getInitialLoadSql());
    }

    @Test
    void testStartNewCursor_buildsOptionsFromChannelDialectAndParameters() {
        stubStartNewCursorDependencies();
        Channel channel = new Channel();
        channel.setMaxBatchSize(250);
        when(configurationService.getChannel(any())).thenReturn(channel);
        AbstractTriggerTemplate template = mock(AbstractTriggerTemplate.class);
        when(template.useTriggerTemplateForColumnTemplatesDuringInitialLoad()).thenReturn(false);
        when(symmetricDialect.getTriggerTemplate()).thenReturn(template);
        boolean[] columnPositions = new boolean[] { true, false };
        when(symmetricDialect.getColumnPositionUsingTemplate(any(), any())).thenReturn(columnPositions);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_CONCAT_CSV_IN_SQL_ENABLED)).thenReturn(true);
        when(parameterService.getLong(ParameterConstants.EXTRACT_ROW_MAX_LENGTH, 1000000000)).thenReturn(123456L);
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        TestableSelectFromTableSource source = new TestableSelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertEquals(1, source.capturedOptions.size());
        SelectFromTableOptions options = source.capturedOptions.get(0);
        assertEquals(250, options.getMaxBatchSize());
        assertTrue(options.isSelectedAsCsv());
        assertTrue(options.isObjectValuesWillNeedEscaped());
        assertArrayEquals(columnPositions, options.isColumnPositionUsingTemplate());
        assertFalse(options.isCheckRowLength());
        assertEquals(123456L, options.getRowMaxLength());
        assertEquals(1, options.getExpectedCommaCount());
    }

    @Test
    void testStartNewCursor_withCheckRowLengthAndLobColumns_setsReturnLobObjectsTrue() {
        stubStartNewCursorDependencies();
        when(parameterService.is(ParameterConstants.EXTRACT_CHECK_ROW_SIZE, false)).thenReturn(true);
        when(symmetricDialect.getTablePrefix()).thenReturn("other");
        Table tableWithLob = buildTable("test_table", "id", "blob_col");
        when(platform.isLob(any(Column.class))).thenAnswer(invocation -> "blob_col".equals(((Column) invocation.getArgument(0)).getName()));
        when(platform.getRelationFromCache(any(), any(), any(), anyBoolean())).thenReturn(tableWithLob);
        when(symmetricDialect.createInitialLoadSqlFor(any(), any(), any(), any(), any(), any())).thenReturn("select * from test_table");
        TriggerRouter triggerRouter = buildTriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,blob_col");
        SelectFromTableEvent event = new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, null);
        TestableSelectFromTableSource source = new TestableSelectFromTableSource(engine, buildBatch(), new ArrayList<>(List.of(event)));
        source.next();
        assertEquals(1, source.capturedOptions.size());
        assertTrue(source.capturedOptions.get(0).isReturnLobObjects());
    }

    @Test
    void testCloseCursor_closesAndNullsCursor() throws Exception {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        ISqlReadCursor<Data> cursor = newCursorMock();
        setCursor(source, cursor);
        source.closeCursor();
        verify(cursor).close();
        assertNull(getCursor(source));
    }

    @Test
    void testGetSymmetricDialect_withConfigurationTable_returnsSourceDialect() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.setConfiguration(true);
        assertSame(symmetricDialect, source.getSymmetricDialect());
    }

    @Test
    void testGetSymmetricDialect_withRegularTable_returnsTargetDialect() {
        ISymmetricDialect targetDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getTargetDialect()).thenReturn(targetDialect);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.sourceRelation = buildTable("test_table", "id");
        assertSame(targetDialect, source.getSymmetricDialect());
    }

    @Test
    void testCreateCursor_defaultMapping_usesCsvValue() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.sourceRelation = buildTable("test_table", "id", "name");
        SelectFromTableOptions options = new SelectFromTableOptions().triggerHistory(new TriggerHistory("test_table", "id", "id,name"))
                .initialLoadSql("select * from test_table");
        ISqlRowMapper<Data> captured = captureRowMapper(source, options);
        Row row = new Row("id", "1");
        row.put("name", "foo");
        Data data = captured.mapRow(row);
        assertEquals("1,foo", data.getRowData());
    }

    @Test
    void testCreateCursor_selectedAsCsv_withCommaCountBelowExpected_throwsSymmetricException() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.sourceRelation = buildTable("test_table", "id", "name");
        SelectFromTableOptions options = new SelectFromTableOptions().triggerHistory(new TriggerHistory("test_table", "id", "id,name"))
                .initialLoadSql("select * from test_table").selectedAsCsv(true).expectedCommaCount(2);
        ISqlRowMapper<Data> captured = captureRowMapper(source, options);
        Row row = new Row("id", "1");
        assertThrows(SymmetricException.class, () -> captured.mapRow(row));
    }

    @Test
    void testCreateCursor_objectValuesWillNeedEscaped_usesPlatformCsvStringValue() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.sourceRelation = buildTable("test_table", "id", "name");
        when(platform.getCsvStringValue(any(), any(), any(), any())).thenReturn("escaped-csv");
        SelectFromTableOptions options = new SelectFromTableOptions().triggerHistory(new TriggerHistory("test_table", "id", "id,name"))
                .initialLoadSql("select * from test_table").objectValuesWillNeedEscaped(true);
        ISqlRowMapper<Data> captured = captureRowMapper(source, options);
        Row row = new Row("id", "1");
        Data data = captured.mapRow(row);
        assertEquals("escaped-csv", data.getRowData());
    }

    @Test
    void testCreateCursor_checkRowLengthExceeded_returnsSqlEventTypeRowWithoutThrowing() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        Table table = buildTable("test_table", "id");
        table.getColumnWithName("id").setPrimaryKey(true);
        source.sourceRelation = table;
        SelectFromTableOptions options = new SelectFromTableOptions().triggerHistory(new TriggerHistory("test_table", "id", "id"))
                .initialLoadSql("select * from test_table").checkRowLength(true).rowMaxLength(2);
        ISqlRowMapper<Data> captured = captureRowMapper(source, options);
        Row row = new Row("id", "12345678");
        Data data = captured.mapRow(row);
        assertEquals(DataEventType.SQL, data.getDataEventType());
    }

    @Test
    void testRequiresLobsSelectedFromSource_withUseStreamLobsTrigger_returnsTrue() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        SelectFromTableEvent event = buildDataDrivenEvent();
        event.getTriggerRouter().getTrigger().setUseStreamLobs(true);
        source.currentInitialLoadEvent = event;
        assertTrue(source.requiresLobsSelectedFromSource(mock(CsvData.class)));
    }

    @Test
    void testRequiresLobsSelectedFromSource_withoutColumnTemplatesEnabled_returnsFalse() {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_USE_COLUMN_TEMPLATES_ENABLED)).thenReturn(false);
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        assertFalse(source.requiresLobsSelectedFromSource(mock(CsvData.class)));
    }

    @Test
    void testRequiresLobsSelectedFromSource_withoutCurrentEvent_returnsFalse() {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        source.next();
        assertFalse(source.requiresLobsSelectedFromSource(mock(CsvData.class)));
    }

    @Test
    void testClose_delegatesToCloseCursor() throws Exception {
        SelectFromTableSource source = createSourceWithDataDrivenEvent();
        ISqlReadCursor<Data> cursor = newCursorMock();
        setCursor(source, cursor);
        source.close();
        verify(cursor).close();
        assertNull(getCursor(source));
    }

    private Table buildTable(String name, String... columnNames) {
        Table table = new Table(name);
        for (String columnName : columnNames) {
            table.addColumn(new Column(columnName));
        }
        return table;
    }

    private Batch buildBatch() {
        return new Batch(BatchType.EXTRACT, 1L, Constants.CHANNEL_DEFAULT, BinaryEncoding.HEX, "source", "target", false);
    }

    private TriggerRouter buildTriggerRouter() {
        Router router = new Router("router1", "server", "client", "default");
        Trigger trigger = new Trigger();
        trigger.setChannelId(Constants.CHANNEL_DEFAULT);
        return new TriggerRouter(trigger, router);
    }

    private SelectFromTableEvent buildDataDrivenEvent() {
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Data data = new Data("test_table", DataEventType.INSERT, "1,foo", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        return new SelectFromTableEvent(data, buildTriggerRouter());
    }

    private SelectFromTableSource createSourceWithDataDrivenEvent() {
        OutgoingBatch outgoingBatch = mock(OutgoingBatch.class);
        Batch batch = buildBatch();
        return new SelectFromTableSource(engine, outgoingBatch, batch, buildDataDrivenEvent());
    }

    private ISqlTemplate stubStartNewCursorDependencies() {
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(symmetricDialect.getParameterService()).thenReturn(parameterService);
        AbstractTriggerTemplate triggerTemplate = mock(AbstractTriggerTemplate.class);
        when(symmetricDialect.getTriggerTemplate()).thenReturn(triggerTemplate);
        when(configurationService.getChannel(any())).thenReturn(new Channel());
        when(extensionService.getExtensionPointList(IRelationReloadVariableFilter.class))
                .thenReturn(Collections.<IRelationReloadVariableFilter> emptyList());
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        return sqlTemplate;
    }

    @SuppressWarnings("unchecked")
    private ISqlReadCursor<Data> newCursorMock() {
        return mock(ISqlReadCursor.class);
    }

    @SuppressWarnings("unchecked")
    private ISqlRowMapper<Data> captureRowMapper(SelectFromTableSource source, SelectFromTableOptions options) {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ArgumentCaptor<ISqlRowMapper<Data>> captor = ArgumentCaptor.forClass(ISqlRowMapper.class);
        when(sqlTemplate.queryForCursor(anyString(), captor.capture(), anyBoolean())).thenReturn(newCursorMock());
        source.createCursor(symmetricDialect, options);
        return captor.getValue();
    }

    private void setCursor(SelectFromTableSource source, ISqlReadCursor<Data> cursor) throws Exception {
        Field field = SelectFromTableSource.class.getDeclaredField("cursor");
        field.setAccessible(true);
        field.set(source, cursor);
    }

    @SuppressWarnings("unchecked")
    private ISqlReadCursor<Data> getCursor(SelectFromTableSource source) throws Exception {
        Field field = SelectFromTableSource.class.getDeclaredField("cursor");
        field.setAccessible(true);
        return (ISqlReadCursor<Data>) field.get(source);
    }

    private static class TestableSelectFromTableSource extends SelectFromTableSource {
        final List<SelectFromTableOptions> capturedOptions = new ArrayList<>();

        TestableSelectFromTableSource(ISymmetricEngine engine, Batch batch, List<SelectFromTableEvent> initialLoadEvents) {
            super(engine, batch, initialLoadEvents);
        }

        @Override
        protected ISqlReadCursor<Data> createCursor(ISymmetricDialect symmetricDialectToUse, SelectFromTableOptions options) {
            capturedOptions.add(options);
            return newExhaustedCursor();
        }

        @SuppressWarnings("unchecked")
        private ISqlReadCursor<Data> newExhaustedCursor() {
            ISqlReadCursor<Data> cursor = mock(ISqlReadCursor.class);
            when(cursor.next()).thenReturn(null);
            return cursor;
        }
    }
}
