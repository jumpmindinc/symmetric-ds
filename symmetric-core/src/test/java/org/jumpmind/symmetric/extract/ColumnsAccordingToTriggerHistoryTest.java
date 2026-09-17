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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.extract.ColumnsAccordingToTriggerHistory.CacheKey;
import org.jumpmind.symmetric.io.data.transform.ColumnPolicy;
import org.jumpmind.symmetric.io.data.transform.RemoveColumnTransform;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ColumnsAccordingToTriggerHistoryTest {
    private ISymmetricEngine engine;
    private ITriggerRouterService triggerRouterService;
    private ITransformService transformService;
    private ISymmetricDialect symmetricDialect;
    private ISymmetricDialect targetDialect;
    private IDatabasePlatform sourcePlatform;
    private IDatabasePlatform targetPlatform;
    private Node sourceNode;
    private Node targetNode;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        triggerRouterService = mock(ITriggerRouterService.class);
        transformService = mock(ITransformService.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        targetDialect = mock(ISymmetricDialect.class);
        sourcePlatform = mock(IDatabasePlatform.class);
        targetPlatform = mock(IDatabasePlatform.class);
        sourceNode = new Node("source", "server");
        targetNode = new Node("target", "client");
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(engine.getTransformService()).thenReturn(transformService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getTablePrefix()).thenReturn("sym");
        when(engine.getEngineName()).thenReturn("engine-" + System.nanoTime());
        when(symmetricDialect.getPlatform()).thenReturn(sourcePlatform);
        when(symmetricDialect.getTargetDialect()).thenReturn(targetDialect);
        when(symmetricDialect.getTargetPlatform()).thenReturn(targetPlatform);
        when(targetDialect.getPlatform()).thenReturn(targetPlatform);
    }

    @Test
    void testNew_populatesFieldsFromEngine() throws Exception {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        assertSame(engine, getField(columns, "engine"));
        assertSame(sourceNode, getField(columns, "sourceNode"));
        assertSame(targetNode, getField(columns, "targetNode"));
        assertSame(triggerRouterService, getField(columns, "triggerRouterService"));
        assertSame(transformService, getField(columns, "transformService"));
        assertSame(symmetricDialect, getField(columns, "symmetricDialect"));
        assertEquals("sym", getField(columns, "tablePrefix"));
    }

    @Test
    void testLookup_withUseDatabaseDefinitionFalse_buildsRelationFromTriggerHistoryColumns() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Relation relation = columns.lookup("router1", triggerHistory, false, false, false, false);
        assertTrue(relation instanceof Table);
        assertArrayEquals(new String[] { "id", "name" }, relation.getColumnNames());
        assertArrayEquals(new String[] { "id" }, relation.getPrimaryKeyColumnNames());
    }

    @Test
    void testLookup_cachesResultForSameArguments() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Relation first = columns.lookup("router1", triggerHistory, false, false, false, false);
        Relation second = columns.lookup("router1", triggerHistory, false, false, false, false);
        assertSame(first, second);
        verify(triggerRouterService, times(1)).getRouterById("router1", false);
    }

    @Test
    void testLookup_withDifferentArguments_recomputesRelation() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Relation first = columns.lookup("router1", triggerHistory, false, false, false, false);
        Relation second = columns.lookup("router1", triggerHistory, true, false, false, false);
        assertNotSame(first, second);
        verify(triggerRouterService, times(2)).getRouterById("router1", false);
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withRouterAndSetTargetTableName_appliesRouterTargetNames() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setSourceCatalogName("src_cat");
        triggerHistory.setSourceSchemaName("src_schema");
        Router router = new Router();
        router.setUseSourceCatalogSchema(false);
        router.setTargetCatalogName(Constants.NONE_TOKEN);
        router.setTargetSchemaName(null);
        router.setTargetTableName("renamed_table");
        when(triggerRouterService.getRouterById("router1", false)).thenReturn(router);
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, true, false, false, false);
        assertNull(relation.getCatalog());
        assertNull(relation.getSchema());
        assertEquals("renamed_table", relation.getName());
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withRouterNull_leavesNamesUnchanged() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        when(triggerRouterService.getRouterById("router1", false)).thenReturn(null);
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, true, false, false, false);
        assertEquals("test_table", relation.getName());
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withUseTransforms_appliesTransformsForExtractPoint() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        TransformColumn transformColumn = new TransformColumn("name", "renamed_name", false);
        TransformTableNodeGroupLink transform = new TransformTableNodeGroupLink();
        transform.setTransformPoint(TransformPoint.EXTRACT);
        transform.setTransformOrder(0);
        transform.setColumnPolicy(ColumnPolicy.SPECIFIED);
        transform.setTargetTableName("transformed_table");
        transform.setTransformColumns(Arrays.asList(transformColumn));
        when(transformService.findTransformsFor(any(), any(), any(), any(), any())).thenReturn(Collections.singletonList(transform));
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, false, false, true, false);
        assertEquals("transformed_table", relation.getName());
        assertArrayEquals(new String[] { "renamed_name" }, relation.getColumnNames());
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withDatabaseDefinitionAndNotUsingTargetExternalId_delegatesToLookupRelation() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Table cachedTable = buildTable("test_table", "id", "name");
        when(targetPlatform.getRelationFromCache(null, null, "test_table", false)).thenReturn(cachedTable);
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, false, true, false, false);
        assertEquals("test_table", relation.getName());
        assertArrayEquals(new String[] { "id", "name" }, relation.getColumnNames());
        verify(targetPlatform).getRelationFromCache(null, null, "test_table", false);
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withDatabaseDefinitionAndUsingTargetExternalId_delegatesToLookupRelationExpanded()
            throws Exception {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        setUsingTargetExternalId(columns, true);
        targetNode.setExternalId("client01");
        String tableName = "test_table_client01";
        TriggerHistory triggerHistory = new TriggerHistory(tableName, "id", "id,name");
        Table cachedTable = buildTable(tableName, "id", "name");
        when(targetPlatform.getRelationFromCache(null, null, tableName, false)).thenReturn(cachedTable);
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, false, true, false, false);
        assertEquals(tableName, relation.getName());
        Map<String, Relation> sourceRelationMap = columns.getSourceRelationMap(engine.getEngineName());
        assertTrue(sourceRelationMap.containsKey("test_table_-f"));
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withUseSourceCatalogSchemaTrue_copiesSourceCatalogAndSchema() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setSourceCatalogName("src_cat");
        triggerHistory.setSourceSchemaName("src_schema");
        Router router = new Router();
        router.setUseSourceCatalogSchema(true);
        when(triggerRouterService.getRouterById("router1", false)).thenReturn(router);
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, true, false, false, false);
        assertEquals("src_cat", relation.getCatalog());
        assertEquals("src_schema", relation.getSchema());
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withLiteralTargetCatalogAndSchema_appliesReplacedValues() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Router router = new Router();
        router.setUseSourceCatalogSchema(false);
        router.setTargetCatalogName("literal_cat");
        router.setTargetSchemaName("literal_schema");
        when(triggerRouterService.getRouterById("router1", false)).thenReturn(router);
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, true, false, false, false);
        assertEquals("literal_cat", relation.getCatalog());
        assertEquals("literal_schema", relation.getSchema());
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withNoneTokenTargetSchema_setsSchemaNull() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        triggerHistory.setSourceSchemaName("src_schema");
        Router router = new Router();
        router.setUseSourceCatalogSchema(true);
        router.setTargetSchemaName(Constants.NONE_TOKEN);
        when(triggerRouterService.getRouterById("router1", false)).thenReturn(router);
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, true, false, false, false);
        assertNull(relation.getSchema());
    }

    @Test
    void testLookupAndOrderColumnsAccordingToTriggerHistory_withBlankTargetTableName_leavesNameUnchanged() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Router router = new Router();
        router.setTargetTableName("");
        when(triggerRouterService.getRouterById("router1", false)).thenReturn(router);
        Relation relation = columns.lookupAndOrderColumnsAccordingToTriggerHistory("router1", triggerHistory, true, false, false, false);
        assertEquals("test_table", relation.getName());
    }

    @Test
    void testGetTargetPlatform_withSymPrefixedTable_returnsSourcePlatform() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        assertSame(sourcePlatform, columns.getTargetPlatform("sym_node"));
    }

    @Test
    void testGetTargetPlatform_withNonPrefixedTable_returnsTargetDialectPlatform() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        assertSame(targetPlatform, columns.getTargetPlatform("test_table"));
    }

    @Test
    void testLookupRelation_filtersColumnsFromCachedRelation() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Table cachedTable = buildTable("test_table", "id", "name");
        when(targetPlatform.getRelationFromCache(null, null, "test_table", false)).thenReturn(cachedTable);
        Relation relation = columns.lookupRelation(targetPlatform, null, null, "test_table", triggerHistory, false);
        assertEquals("test_table", relation.getName());
        assertArrayEquals(new String[] { "id", "name" }, relation.getColumnNames());
    }

    @Test
    void testLookupRelation_withStaleCache_refreshesFromPlatform() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Table staleTable = buildTable("test_table", "id");
        Table freshTable = buildTable("test_table", "id", "name");
        when(targetPlatform.getRelationFromCache(null, null, "test_table", false)).thenReturn(staleTable);
        when(targetPlatform.getRelationFromCache(null, null, "test_table", true)).thenReturn(freshTable);
        Relation relation = columns.lookupRelation(targetPlatform, null, null, "test_table", triggerHistory, false);
        assertArrayEquals(new String[] { "id", "name" }, relation.getColumnNames());
        verify(targetPlatform).getRelationFromCache(null, null, "test_table", true);
    }

    @Test
    void testLookupRelation_withRelationNotFound_throwsSymmetricException() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        when(targetPlatform.getRelationFromCache(null, null, "test_table", false)).thenReturn(null);
        when(targetPlatform.getRelationFromCache(null, null, "test_table", true)).thenReturn(null);
        assertThrows(SymmetricException.class,
                () -> columns.lookupRelation(targetPlatform, null, null, "test_table", triggerHistory, false));
    }

    @Test
    void testLookupRelationExpanded_withoutTargetExternalIdInName_delegatesToLookupRelation() throws Exception {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        setUsingTargetExternalId(columns, true);
        targetNode.setExternalId("client01");
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Table cachedTable = buildTable("test_table", "id", "name");
        when(targetPlatform.getRelationFromCache(null, null, "test_table", false)).thenReturn(cachedTable);
        Relation relation = columns.lookupRelationExpanded(targetPlatform, null, null, "test_table", triggerHistory, false);
        assertEquals("test_table", relation.getName());
    }

    @Test
    void testLookupRelationExpanded_withTargetExternalIdInName_populatesEngineCacheOnFirstLookup() throws Exception {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        setUsingTargetExternalId(columns, true);
        targetNode.setExternalId("client01");
        String relationName = "test_table_client01";
        TriggerHistory triggerHistory = new TriggerHistory("test_table_client01", "id", "id,name");
        Table cachedTable = buildTable(relationName, "id", "name");
        when(targetPlatform.getRelationFromCache(null, null, relationName, false)).thenReturn(cachedTable);
        Relation relation = columns.lookupRelationExpanded(targetPlatform, null, null, relationName, triggerHistory, false);
        assertEquals(relationName, relation.getName());
        Map<String, Relation> sourceRelationMap = columns.getSourceRelationMap(engine.getEngineName());
        assertTrue(sourceRelationMap.containsKey("test_table_-f"));
    }

    @Test
    void testLookupRelationExpanded_withCachedBaseTableName_copiesAndFiltersInsteadOfQueryingPlatform() throws Exception {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        setUsingTargetExternalId(columns, true);
        targetNode.setExternalId("client01");
        String relationName = "test_table_client01";
        TriggerHistory triggerHistory = new TriggerHistory("test_table_client01", "id", "id,name");
        Table cachedTable = buildTable(relationName, "id", "name");
        when(targetPlatform.getRelationFromCache(null, null, relationName, false)).thenReturn(cachedTable);
        Relation first = columns.lookupRelationExpanded(targetPlatform, null, null, relationName, triggerHistory, false);
        Relation second = columns.lookupRelationExpanded(targetPlatform, null, null, relationName, triggerHistory, false);
        assertNotSame(first, second);
        assertEquals(relationName, second.getName());
        verify(targetPlatform, times(1)).getRelationFromCache(null, null, relationName, false);
    }

    @Test
    void testGetSourceRelationMap_returnsSameMapInstanceForSameEngineName() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        Map<String, Relation> first = columns.getSourceRelationMap("engine-a");
        Map<String, Relation> second = columns.getSourceRelationMap("engine-a");
        assertSame(first, second);
    }

    @Test
    void testGetSourceRelationMap_returnsDifferentMapInstanceForDifferentEngineName() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        Map<String, Relation> first = columns.getSourceRelationMap("engine-b");
        Map<String, Relation> second = columns.getSourceRelationMap("engine-c");
        assertNotSame(first, second);
    }

    @Test
    void testGetTransform_returnsMatchingTransformAtOrAboveOrder() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        Table relation = buildTable("test_table", "id", "name");
        TransformTableNodeGroupLink transform = new TransformTableNodeGroupLink();
        transform.setTransformPoint(TransformPoint.EXTRACT);
        transform.setTransformOrder(2);
        when(transformService.findTransformsFor(sourceNode.getNodeGroupId(), targetNode.getNodeGroupId(), "test_table", null, null))
                .thenReturn(Collections.singletonList(transform));
        assertSame(transform, columns.getTransform(relation, TransformPoint.EXTRACT, 1));
    }

    @Test
    void testGetTransform_returnsNullWhenNoMatch() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        Table relation = buildTable("test_table", "id", "name");
        TransformTableNodeGroupLink transform = new TransformTableNodeGroupLink();
        transform.setTransformPoint(TransformPoint.LOAD);
        transform.setTransformOrder(0);
        when(transformService.findTransformsFor(sourceNode.getNodeGroupId(), targetNode.getNodeGroupId(), "test_table", null, null))
                .thenReturn(Collections.singletonList(transform));
        assertNull(columns.getTransform(relation, TransformPoint.EXTRACT, 0));
    }

    @Test
    void testApplyTransform_withSpecifiedPolicy_removesUnmappedColumnsAndRenamesMappedColumn() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        Table relation = buildTable("test_table", "id", "name");
        TransformColumn transformColumn = new TransformColumn("name", "renamed_name", false);
        TransformTableNodeGroupLink transform = new TransformTableNodeGroupLink();
        transform.setColumnPolicy(ColumnPolicy.SPECIFIED);
        transform.setTargetTableName("transformed_table");
        transform.setTransformColumns(Arrays.asList(transformColumn));
        columns.applyTransform(relation, transform);
        assertArrayEquals(new String[] { "renamed_name" }, relation.getColumnNames());
        assertEquals("transformed_table", relation.getName());
    }

    @Test
    void testApplyTransform_withRemoveColumnTransformType_removesMappedColumn() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        Table relation = buildTable("test_table", "id", "name");
        TransformColumn transformColumn = new TransformColumn("name", "name", false, RemoveColumnTransform.NAME, null);
        TransformTableNodeGroupLink transform = new TransformTableNodeGroupLink();
        transform.setColumnPolicy(ColumnPolicy.IMPLIED);
        transform.setTargetTableName("test_table");
        transform.setTransformColumns(Arrays.asList(transformColumn));
        columns.applyTransform(relation, transform);
        assertArrayEquals(new String[] { "id" }, relation.getColumnNames());
    }

    @Test
    void testApplyTransform_withBlankSourceColumnName_addsNewVarcharColumn() {
        ColumnsAccordingToTriggerHistory columns = new ColumnsAccordingToTriggerHistory(engine, sourceNode, targetNode);
        Table relation = buildTable("test_table", "id", "name");
        TransformColumn transformColumn = new TransformColumn();
        transformColumn.setTargetColumnName("new_col");
        TransformTableNodeGroupLink transform = new TransformTableNodeGroupLink();
        transform.setColumnPolicy(ColumnPolicy.IMPLIED);
        transform.setTargetTableName("test_table");
        transform.setTransformColumns(Arrays.asList(transformColumn));
        columns.applyTransform(relation, transform);
        assertNotNull(relation.getColumnWithName("new_col"));
        assertArrayEquals(new String[] { "id", "name", "new_col" }, relation.getColumnNames());
    }

    @Test
    void testCacheKey_equalsAndHashCode_reflectAllFields() {
        CacheKey key1 = new CacheKey("router1", 1, true, false, true, false);
        CacheKey key2 = new CacheKey("router1", 1, true, false, true, false);
        CacheKey differentRouter = new CacheKey("router2", 1, true, false, true, false);
        CacheKey differentHistoryId = new CacheKey("router1", 2, true, false, true, false);
        CacheKey differentSetTargetTableName = new CacheKey("router1", 1, false, false, true, false);
        CacheKey nullRouter = new CacheKey(null, 1, true, false, true, false);
        assertEquals(key1, key1);
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, differentRouter);
        assertNotEquals(key1, differentHistoryId);
        assertNotEquals(key1, differentSetTargetTableName);
        assertNotEquals(key1, nullRouter);
        assertNotEquals(nullRouter, key1);
        assertNotEquals(key1, null);
        assertNotEquals(key1, "not a cache key");
    }

    private Table buildTable(String name, String... columnNames) {
        Table table = new Table(name);
        for (String columnName : columnNames) {
            table.addColumn(new Column(columnName));
        }
        return table;
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = ColumnsAccordingToTriggerHistory.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setUsingTargetExternalId(ColumnsAccordingToTriggerHistory target, boolean value) throws Exception {
        Field field = ColumnsAccordingToTriggerHistory.class.getDeclaredField("isUsingTargetExternalId");
        field.setAccessible(true);
        field.set(target, value);
    }
}
