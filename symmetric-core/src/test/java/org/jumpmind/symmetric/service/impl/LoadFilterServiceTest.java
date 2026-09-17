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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.LoadFilter.LoadFilterType;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.LoadFilterService.LoadFilterNodeGroupLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoadFilterServiceTest {
    private static final NodeGroupLink CORP_TO_STORE = new NodeGroupLink("corp", "store");
    private IParameterService parameterService;
    private IConfigurationService configurationService;
    private ICacheManager cacheManager;
    private ISqlTemplate sqlTemplate;
    private LoadFilterService loadFilterService;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        configurationService = mock(IConfigurationService.class);
        cacheManager = mock(ICacheManager.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(returnsFirstArg());
        loadFilterService = new LoadFilterService(engine, symmetricDialect);
    }

    @Test
    void testFindLoadFiltersFor_returnsTheEntryForTheRequestedLink() {
        Map<LoadFilterType, Map<String, List<LoadFilter>>> byType = new HashMap<LoadFilterType, Map<String, List<LoadFilter>>>();
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> cached = new HashMap<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>>();
        cached.put(CORP_TO_STORE, byType);
        when(cacheManager.findLoadFilters(CORP_TO_STORE, true)).thenReturn(cached);
        assertEquals(byType, loadFilterService.findLoadFiltersFor(CORP_TO_STORE, true));
    }

    @Test
    void testFindLoadFiltersFor_withNothingCached() {
        assertNull(loadFilterService.findLoadFiltersFor(CORP_TO_STORE, false));
    }

    @Test
    void testFindLoadFiltersFromDb_groupsByLinkTypeAndQualifiedTable() {
        stubLoadFilters(newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH));
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> byLink = loadFilterService.findLoadFiltersFromDb();
        Map<String, List<LoadFilter>> byTable = byLink.get(CORP_TO_STORE).get(LoadFilterType.BSH);
        assertEquals(1, byTable.get("CORP.PUBLIC.ITEM").size());
    }

    @Test
    void testFindLoadFiltersFromDb_replacesBlankNamesWithWildcards() {
        stubLoadFilters(newLoadFilter("filter-1", null, "", "  ", LoadFilterType.SQL));
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> byLink = loadFilterService.findLoadFiltersFromDb();
        assertTrue(byLink.get(CORP_TO_STORE).get(LoadFilterType.SQL).containsKey("*.*.*"));
    }

    @Test
    void testFindLoadFiltersFromDb_uppercasesNamesWhenMetadataIsCaseInsensitive() {
        when(parameterService.is(ParameterConstants.DB_METADATA_IGNORE_CASE)).thenReturn(true);
        stubLoadFilters(newLoadFilter("filter-1", "corp", "public", "item", LoadFilterType.JAVA));
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> byLink = loadFilterService.findLoadFiltersFromDb();
        assertTrue(byLink.get(CORP_TO_STORE).get(LoadFilterType.JAVA).containsKey("CORP.PUBLIC.ITEM"));
    }

    @Test
    void testFindLoadFiltersFromDb_collectsFiltersForTheSameTableTogether() {
        stubLoadFilters(newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH),
                newLoadFilter("filter-2", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH));
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> byLink = loadFilterService.findLoadFiltersFromDb();
        assertEquals(2, byLink.get(CORP_TO_STORE).get(LoadFilterType.BSH).get("CORP.PUBLIC.ITEM").size());
    }

    @Test
    void testFindLoadFiltersFromDb_skipsFiltersWithoutAResolvableLink() {
        LoadFilterNodeGroupLink loadFilter = newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH);
        loadFilter.setNodeGroupLink(null);
        stubLoadFilters(loadFilter);
        assertTrue(loadFilterService.findLoadFiltersFromDb().isEmpty());
    }

    @Test
    void testLoadFilterMapper_mapsEveryColumn() {
        when(configurationService.getNodeGroupLinkFor("corp", "store", false)).thenReturn(CORP_TO_STORE);
        LoadFilterNodeGroupLink loadFilter = loadFilterService.new LoadFilterMapper().mapRow(newLoadFilterRow("bsh"));
        assertEquals("filter-1", loadFilter.getLoadFilterId());
        assertEquals(CORP_TO_STORE, loadFilter.getNodeGroupLink());
        assertEquals("CORP", loadFilter.getTargetCatalogName());
        assertEquals("PUBLIC", loadFilter.getTargetSchemaName());
        assertEquals("ITEM", loadFilter.getTargetTableName());
        assertTrue(loadFilter.isFilterOnInsert());
        assertFalse(loadFilter.isFilterOnUpdate());
        assertTrue(loadFilter.isFilterOnDelete());
        assertEquals("before", loadFilter.getBeforeWriteScript());
        assertEquals("after", loadFilter.getAfterWriteScript());
        assertEquals("complete", loadFilter.getBatchCompleteScript());
        assertEquals("commit", loadFilter.getBatchCommitScript());
        assertEquals("rollback", loadFilter.getBatchRollbackScript());
        assertEquals("error", loadFilter.getHandleErrorScript());
        assertEquals("system", loadFilter.getLastUpdateBy());
        assertEquals(2, loadFilter.getLoadFilterOrder());
        assertTrue(loadFilter.isFailOnError());
        assertEquals(LoadFilterType.BSH, loadFilter.getLoadFilterType());
    }

    @Test
    void testLoadFilterMapper_withAnUnknownFilterTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> loadFilterService.new LoadFilterMapper().mapRow(newLoadFilterRow("groovy")));
    }

    @Test
    void testRefreshFromDatabase_withNoRowsInTheTable() {
        assertFalse(loadFilterService.refreshFromDatabase());
    }

    @Test
    void testRefreshFromDatabase_withANewerUpdateTime() {
        when(sqlTemplate.queryForObject(sqlFor("selectMaxLastUpdateTime"), Date.class)).thenReturn(Timestamp.valueOf("2024-01-02 03:04:05"));
        assertTrue(loadFilterService.refreshFromDatabase());
        verify(cacheManager).flushLoadFilters();
    }

    @Test
    void testRefreshFromDatabase_withAnUnchangedUpdateTime() {
        when(sqlTemplate.queryForObject(sqlFor("selectMaxLastUpdateTime"), Date.class)).thenReturn(Timestamp.valueOf("2024-01-02 03:04:05"));
        loadFilterService.refreshFromDatabase();
        assertFalse(loadFilterService.refreshFromDatabase());
    }

    @Test
    void testClearCache() {
        loadFilterService.clearCache();
        verify(cacheManager).flushLoadFilters();
    }

    @Test
    void testDeleteLoadFilter() {
        loadFilterService.deleteLoadFilter("filter-1");
        verify(sqlTemplate).update(sqlFor("deleteLoadFilterSql"), "filter-1");
        verify(cacheManager).flushLoadFilters();
    }

    @Test
    void testDeleteAllLoadFilters() {
        loadFilterService.deleteAllLoadFilters();
        verify(sqlTemplate).update(sqlFor("deleteAllLoadFiltersSql"));
        verify(cacheManager).flushLoadFilters();
    }

    @Test
    void testSaveLoadFilter_insertsWhenTheUpdateMatchedNothing() {
        when(sqlTemplate.update(eq(sqlFor("updateLoadFilterSql")), any(Object[].class))).thenReturn(0);
        loadFilterService.saveLoadFilter(newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH));
        verify(sqlTemplate).update(eq(sqlFor("insertLoadFilterSql")), any(Object[].class));
    }

    @Test
    void testSaveLoadFilter_stampsTheLastUpdateTime() {
        LoadFilterNodeGroupLink loadFilter = newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH);
        loadFilterService.saveLoadFilter(loadFilter);
        assertTrue(loadFilter.getLastUpdateTime() != null);
    }

    @Test
    void testSaveLoadFilterAsCopy_appendsTheFirstFreeSuffix() {
        when(sqlTemplate.query(anyString(), anyLoadFilterMapper(), eq("filter-1%")))
                .thenReturn(Arrays.asList(newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH),
                        newLoadFilter("filter-1_2", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH)));
        LoadFilterNodeGroupLink loadFilter = newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH);
        loadFilterService.saveLoadFilterAsCopy(loadFilter);
        assertEquals("filter-1_3", loadFilter.getLoadFilterId());
    }

    @Test
    void testRenameLoadFilter_deletesTheOldIdFirst() {
        loadFilterService.renameLoadFilter("filter-0", newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH));
        verify(sqlTemplate).update(sqlFor("deleteLoadFilterSql"), "filter-0");
    }

    @Test
    void testGetLoadFilterNodeGroupLinks_readsThroughTheTemplate() {
        stubLoadFilters(newLoadFilter("filter-1", "CORP", "PUBLIC", "ITEM", LoadFilterType.BSH));
        assertEquals(1, loadFilterService.getLoadFilterNodeGroupLinks().size());
    }

    private void stubLoadFilters(LoadFilterNodeGroupLink... loadFilters) {
        when(sqlTemplate.query(anyString(), anyLoadFilterMapper())).thenReturn(Arrays.asList(loadFilters));
    }

    private LoadFilterNodeGroupLink newLoadFilter(String loadFilterId, String catalogName, String schemaName, String tableName, LoadFilterType type) {
        LoadFilterNodeGroupLink loadFilter = new LoadFilterNodeGroupLink();
        loadFilter.setLoadFilterId(loadFilterId);
        loadFilter.setNodeGroupLink(CORP_TO_STORE);
        loadFilter.setTargetCatalogName(catalogName);
        loadFilter.setTargetSchemaName(schemaName);
        loadFilter.setTargetTableName(tableName);
        loadFilter.setLoadFilterType(type);
        loadFilter.setLastUpdateBy("system");
        return loadFilter;
    }

    private Row newLoadFilterRow(String loadFilterType) {
        Row row = new Row(21);
        row.put("load_filter_id", "filter-1");
        row.put("source_node_group_id", "corp");
        row.put("target_node_group_id", "store");
        row.put("target_catalog_name", "CORP");
        row.put("target_schema_name", "PUBLIC");
        row.put("target_table_name", "ITEM");
        row.put("filter_on_insert", 1);
        row.put("filter_on_update", 0);
        row.put("filter_on_delete", 1);
        row.put("before_write_script", "before");
        row.put("after_write_script", "after");
        row.put("batch_complete_script", "complete");
        row.put("batch_commit_script", "commit");
        row.put("batch_rollback_script", "rollback");
        row.put("handle_error_script", "error");
        row.put("create_time", Timestamp.valueOf("2023-11-14 17:13:20"));
        row.put("last_update_by", "system");
        row.put("last_update_time", Timestamp.valueOf("2024-01-02 03:04:05"));
        row.put("load_filter_order", 2);
        row.put("fail_on_error", 1);
        row.put("load_filter_type", loadFilterType);
        return row;
    }

    private ISqlRowMapper<LoadFilterNodeGroupLink> anyLoadFilterMapper() {
        return any();
    }

    private String sqlFor(String key) {
        return loadFilterService.getSql(key);
    }
}
