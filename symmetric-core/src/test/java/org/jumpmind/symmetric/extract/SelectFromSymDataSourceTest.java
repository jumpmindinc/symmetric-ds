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
package org.jumpmind.symmetric.extract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        when(symmetricDialect.getBinaryEncoding()).thenReturn(BinaryEncoding.HEX);
        when(symmetricDialect.getName()).thenReturn("H2");
        when(symmetricDialect.getTargetDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(triggerRouterService.getTriggerRoutersByTriggerHist(anyString(), anyBoolean())).thenReturn(null);
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
        when(outgoingBatch.getChannelId()).thenReturn("default");
        when(outgoingBatch.getNodeId()).thenReturn("target");
        when(outgoingBatch.isCommonFlag()).thenReturn(false);
        when(outgoingBatch.getLoadId()).thenReturn(0L);
        when(outgoingBatch.getNodeBatchId()).thenReturn("target-1");
        tableWithFksAndIndexes = buildTableWithFksAndIndexes();
        when(platform.getTableFromCache(any(), any(), any(), anyBoolean())).thenReturn(tableWithFksAndIndexes);
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

    private SelectFromSymDataSource createSource() {
        return new SelectFromSymDataSource(engine, outgoingBatch, sourceNode, targetNode, new ProcessInfo(), false);
    }

    private void setColumnsAccordingToTriggerHistory(SelectFromSymDataSource source, Table targetTable) throws Exception {
        ColumnsAccordingToTriggerHistory mockLookup = mock(ColumnsAccordingToTriggerHistory.class);
        when(mockLookup.lookup(anyString(), any(TriggerHistory.class), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean())).thenReturn(targetTable);
        Field field = SelectFromSymDataSource.class.getDeclaredField("columnsAccordingToTriggerHistory");
        field.setAccessible(true);
        field.set(source, mockLookup);
    }

    @Test
    void processCreateEvent_preSetupFkDrop_stripsFksKeepsIndexes() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceTable = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, "default", null, null);
        data.setOldData(Constants.SEND_SCHEMA_EXCLUDE_FOREIGN_KEYS);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertFalse(xml.contains("foreign-key"), "Phase 1 setup batch should strip foreign keys");
        assertTrue(xml.contains("index"), "Phase 1 setup batch should keep indexes");
    }

    @Test
    void processCreateEvent_setupBatch_stripsBothFksAndIndexes() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(true);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceTable = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, "default", null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertFalse(xml.contains("foreign-key"), "Phase 2 setup batch should strip foreign keys");
        assertFalse(xml.contains("index"), "Phase 2 setup batch should strip indexes");
    }

    @Test
    void processCreateEvent_phase1Finalize_stripsFksKeepsIndexes() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(false);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceTable = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, "default", null, null);
        data.setOldData(Constants.SEND_SCHEMA_EXCLUDE_FOREIGN_KEYS);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertFalse(xml.contains("foreign-key"), "Phase 1 finalize batch should strip foreign keys");
        assertTrue(xml.contains("index"), "Phase 1 finalize batch should keep indexes");
    }

    @Test
    void processCreateEvent_phase2Finalize_keepsAll() throws Exception {
        when(outgoingBatch.isLoadFlag()).thenReturn(false);
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        SelectFromSymDataSource source = createSource();
        source.sourceTable = tableWithFksAndIndexes;
        setColumnsAccordingToTriggerHistory(source, buildTableWithFksAndIndexes());
        TriggerHistory hist = new TriggerHistory("test_table", "id", "id,name,ref_id");
        Data data = new Data("test_table", DataEventType.CREATE, "", null, hist, "default", null, null);
        boolean result = source.processCreateEvent(hist, "router1", data);
        assertTrue(result);
        String xml = data.getRowData();
        assertTrue(xml.contains("foreign-key"), "Phase 2 finalize batch should keep foreign keys");
        assertTrue(xml.contains("index"), "Phase 2 finalize batch should keep indexes");
    }
}
