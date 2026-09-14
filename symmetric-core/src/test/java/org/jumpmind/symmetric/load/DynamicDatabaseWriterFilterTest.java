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
package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.load.DynamicDatabaseWriterFilter.WriteMethod;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.LoadFilter.LoadFilterType;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DynamicDatabaseWriterFilterTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private Map<String, List<LoadFilter>> loadFilters;
    private TestableDynamicDatabaseWriterFilter filter;
    private DataContext context;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getTablePrefix()).thenReturn("sym");
        loadFilters = new HashMap<String, List<LoadFilter>>();
        filter = new TestableDynamicDatabaseWriterFilter(engine, loadFilters);
        context = new DataContext();
    }

    @Test
    void testBeforeWrite_withNullTableSkipsLookup() {
        boolean result = filter.beforeWrite(context, null, new CsvData());
        assertTrue(result);
        assertEquals(0, filter.processLoadFiltersCallCount);
    }

    @Test
    void testBeforeWrite_withNoMatchingFilters() {
        Table table = new Table("MYTABLE");
        boolean result = filter.beforeWrite(context, table, new CsvData());
        assertTrue(result);
        assertEquals(0, filter.processLoadFiltersCallCount);
    }

    @Test
    void testBeforeWrite_matchesWildcardTableNameFilter() {
        Table table = new Table("MYTABLE");
        LoadFilter loadFilter = newLoadFilter();
        loadFilters.put("*.*.MYTABLE", Arrays.asList(loadFilter));
        filter.writeRow = false;
        boolean result = filter.beforeWrite(context, table, new CsvData());
        assertFalse(result);
        assertEquals(1, filter.processLoadFiltersCallCount);
        assertEquals(WriteMethod.BEFORE_WRITE, filter.lastWriteMethod);
        assertEquals(Arrays.asList(loadFilter), filter.lastLoadFilters);
    }

    @Test
    void testAfterWrite_usesAfterWriteMethod() {
        Table table = new Table("MYTABLE");
        loadFilters.put("*.*.MYTABLE", Arrays.asList(newLoadFilter()));
        filter.afterWrite(context, table, new CsvData());
        assertEquals(WriteMethod.AFTER_WRITE, filter.lastWriteMethod);
    }

    @Test
    void testHandleError_usesHandleErrorMethod() {
        Table table = new Table("MYTABLE");
        loadFilters.put("*.*.MYTABLE", Arrays.asList(newLoadFilter()));
        filter.handleError(context, table, new CsvData(), new Exception("boom"));
        assertEquals(WriteMethod.HANDLE_ERROR, filter.lastWriteMethod);
    }

    @Test
    void testEarlyCommit_doesNothing() {
        assertDoesNotThrow(() -> filter.earlyCommit(context));
    }

    @Test
    void testProcessLoadFilters_mergesFiltersFoundUnderMultipleKeys() {
        Table table = new Table("CAT", "SCH", "MYTABLE", new String[] { "id" }, new String[] { "id" });
        LoadFilter wildcardFilter = newLoadFilter();
        LoadFilter exactFilter = newLoadFilter();
        loadFilters.put("*.*.MYTABLE", Arrays.asList(wildcardFilter));
        loadFilters.put("CAT.SCH.MYTABLE", Arrays.asList(exactFilter));
        filter.beforeWrite(context, table, new CsvData());
        assertEquals(2, filter.lastLoadFilters.size());
        assertTrue(filter.lastLoadFilters.contains(wildcardFilter));
        assertTrue(filter.lastLoadFilters.contains(exactFilter));
    }

    @Test
    void testProcessLoadFilters_skipsCatalogSchemaWildcardLookupWhenTableStartsWithPrefix() {
        Table table = new Table(null, "myschema", "SYM_NODE");
        loadFilters.put("myschema.*", Arrays.asList(newLoadFilter()));
        boolean result = filter.beforeWrite(context, table, new CsvData());
        assertTrue(result);
        assertEquals(0, filter.processLoadFiltersCallCount);
    }

    @Test
    void testBatchLifecycle_executesScriptsAddedDuringLookup() {
        Table table = new Table("MYTABLE");
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBatchCompleteScript("completeScript");
        loadFilter.setBatchCommitScript("commitScript");
        loadFilter.setBatchRollbackScript("rollbackScript");
        loadFilters.put("*.*.MYTABLE", Arrays.asList(loadFilter));
        filter.beforeWrite(context, table, new CsvData());
        filter.batchComplete(context);
        assertEquals(new HashSet<String>(Arrays.asList("completeScript")), filter.lastScripts);
        assertTrue(filter.lastIsFailOnError);
        filter.batchCommitted(context);
        assertEquals(new HashSet<String>(Arrays.asList("commitScript")), filter.lastScripts);
        filter.batchRolledback(context);
        assertEquals(new HashSet<String>(Arrays.asList("rollbackScript")), filter.lastScripts);
    }

    @Test
    void testBatchLifecycle_withFailOnErrorFalse() {
        Table table = new Table("MYTABLE");
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setFailOnError(false);
        loadFilter.setBatchCompleteScript("completeScript");
        loadFilters.put("*.*.MYTABLE", Arrays.asList(loadFilter));
        filter.beforeWrite(context, table, new CsvData());
        filter.batchComplete(context);
        assertFalse(filter.lastIsFailOnError);
    }

    @Test
    void testIsIgnoreCase() {
        when(parameterService.is(ParameterConstants.DB_METADATA_IGNORE_CASE)).thenReturn(true);
        assertTrue(filter.isIgnoreCase());
        when(parameterService.is(ParameterConstants.DB_METADATA_IGNORE_CASE)).thenReturn(false);
        assertFalse(filter.isIgnoreCase());
    }

    @Test
    void testHandlesMissingTable_returnsTrueWhenParameterEnabled() {
        Table table = new Table("MYTABLE");
        when(parameterService.is(ParameterConstants.BSH_LOAD_FILTER_HANDLES_MISSING_TABLES)).thenReturn(true);
        assertTrue(filter.handlesMissingTable(context, table));
    }

    @Test
    void testHandlesMissingTable_fallsBackToLoadFiltersMap() {
        Table table = new Table("MYTABLE");
        when(parameterService.is(ParameterConstants.BSH_LOAD_FILTER_HANDLES_MISSING_TABLES)).thenReturn(false);
        assertFalse(filter.handlesMissingTable(context, table));
        loadFilters.put(table.getFullyQualifiedName(), Arrays.asList(newLoadFilter()));
        assertTrue(filter.handlesMissingTable(context, table));
    }

    @Test
    void testGetDatabaseWriterFilters_createsFilterPerType() {
        Map<LoadFilterType, Map<String, List<LoadFilter>>> byType = new EnumMap<LoadFilterType, Map<String, List<LoadFilter>>>(LoadFilterType.class);
        byType.put(LoadFilterType.BSH, new HashMap<String, List<LoadFilter>>());
        byType.put(LoadFilterType.JAVA, new HashMap<String, List<LoadFilter>>());
        byType.put(LoadFilterType.SQL, new HashMap<String, List<LoadFilter>>());
        List<DynamicDatabaseWriterFilter> filters = DynamicDatabaseWriterFilter.getDatabaseWriterFilters(engine, byType);
        assertEquals(3, filters.size());
        assertTrue(filters.stream().anyMatch(BshDatabaseWriterFilter.class::isInstance));
        assertTrue(filters.stream().anyMatch(JavaDatabaseWriterFilter.class::isInstance));
        assertTrue(filters.stream().anyMatch(SQLDatabaseWriterFilter.class::isInstance));
    }

    @Test
    void testGetDatabaseWriterFilters_withNullMapReturnsEmptyList() {
        List<DynamicDatabaseWriterFilter> filters = DynamicDatabaseWriterFilter.getDatabaseWriterFilters(engine, null);
        assertTrue(filters.isEmpty());
    }

    private LoadFilter newLoadFilter() {
        LoadFilter loadFilter = new LoadFilter();
        loadFilter.setLoadFilterId("lf1");
        return loadFilter;
    }

    private static class TestableDynamicDatabaseWriterFilter extends DynamicDatabaseWriterFilter {
        private boolean writeRow = true;
        private int processLoadFiltersCallCount;
        private List<LoadFilter> lastLoadFilters;
        private WriteMethod lastWriteMethod;
        private Set<String> lastScripts;
        private boolean lastIsFailOnError;

        TestableDynamicDatabaseWriterFilter(ISymmetricEngine engine, Map<String, List<LoadFilter>> loadFilters) {
            super(engine, loadFilters);
        }

        @Override
        protected boolean processLoadFilters(DataContext context, Table table, CsvData data, Exception error,
                WriteMethod writeMethod, List<LoadFilter> loadFiltersForTable) {
            processLoadFiltersCallCount++;
            lastLoadFilters = loadFiltersForTable;
            lastWriteMethod = writeMethod;
            return writeRow;
        }

        @Override
        protected void executeScripts(DataContext context, String key, Set<String> scripts, boolean isFailOnError) {
            lastScripts = scripts;
            lastIsFailOnError = isFailOnError;
        }
    }
}
