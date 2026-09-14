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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.mockito.ArgumentCaptor;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.load.DynamicDatabaseWriterFilter.WriteMethod;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.TargetError;

class SQLDatabaseWriterFilterTest {
    private ISymmetricEngine engine;
    private SQLDatabaseWriterFilter filter;
    private Table table;
    private CsvData data;
    private DataContext context;
    private ISqlTransaction transaction;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        filter = new SQLDatabaseWriterFilter(engine, new HashMap<String, List<LoadFilter>>());
        table = new Table("CAT", "SCH", "TEST", new String[] { "id", "name" }, new String[] { "id" });
        data = new CsvData(DataEventType.INSERT);
        data.putParsedData(CsvData.ROW_DATA, new String[] { "1", "Bob" });
        context = mock(DataContext.class);
        transaction = mock(ISqlTransaction.class);
        when(context.findTransaction()).thenReturn(transaction);
    }

    @Test
    void testProcessLoadFilters_returnsTrueWhenNoResult() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("update test set name = :name where id = :id");
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenReturn(Collections.emptyList());
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_returnsFalseWhenQueryResultIsFalse() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("select 0");
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenReturn(Arrays.asList(Boolean.FALSE));
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_afterWrite() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setAfterWriteScript("select 0");
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenReturn(Arrays.asList(Boolean.FALSE));
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.AFTER_WRITE, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_handleError() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setHandleErrorScript("select 0");
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenReturn(Arrays.asList(Boolean.FALSE));
        boolean writeRow = filter.processLoadFilters(context, table, data, new Exception("boom"), WriteMethod.HANDLE_ERROR, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_withNonMatchingEventType() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setFilterOnInsert(false);
        loadFilter.setBeforeWriteScript("select 0");
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
        verify(transaction, never()).query(anyString(), any(), any());
    }

    @Test
    void testProcessLoadFilters_withBlankScript() {
        LoadFilter loadFilter = newLoadFilter();
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
        verify(transaction, never()).query(anyString(), any(), any());
    }

    @Test
    void testProcessLoadFilters_throwsWhenFailOnError() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("select 0");
        loadFilter.setFailOnError(true);
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenThrow(new RuntimeException("boom"));
        List<LoadFilter> loadFilters = Arrays.asList(loadFilter);
        assertThrows(SymmetricException.class,
                () -> filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, loadFilters));
    }

    @Test
    void testProcessLoadFilters_swallowsErrorWhenNotFailOnError() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("select 0");
        loadFilter.setFailOnError(false);
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenThrow(new RuntimeException("boom"));
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_usesPkDataWhenNoRowData() {
        CsvData pkOnlyData = new CsvData(DataEventType.INSERT);
        pkOnlyData.putParsedData(CsvData.PK_DATA, new String[] { "7" });
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("select 0");
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenReturn(null);
        filter.processLoadFilters(context, table, pkOnlyData, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(transaction).query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), captor.capture());
        assertEquals("7", captor.getValue().get("id"));
    }

    @Test
    void testProcessLoadFilters_includesOldDataInNamedParams() {
        data.putParsedData(CsvData.OLD_DATA, new String[] { "0", "Ann" });
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("select 0");
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenReturn(null);
        filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(transaction).query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), captor.capture());
        assertEquals("0", captor.getValue().get("OLD_id"));
    }

    @Test
    void testExecuteScripts_withScripts() {
        Set<String> scripts = new HashSet<String>(Arrays.asList("select 1", "select 2"));
        filter.executeScripts(context, "key", scripts, false);
        verify(transaction, times(2)).query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any());
    }

    @Test
    void testExecuteScripts_withNullScripts() {
        filter.executeScripts(context, "key", null, false);
        verify(transaction, never()).query(anyString(), any(), any());
    }

    @Test
    void testExecuteScripts_throwsWhenFailOnError() {
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenThrow(new RuntimeException("boom"));
        Set<String> scripts = new HashSet<String>(Arrays.asList("select 1"));
        assertThrows(RuntimeException.class, () -> filter.executeScripts(context, "key", scripts, true));
    }

    @Test
    void testExecuteScripts_swallowsErrorWhenNotFailOnError() {
        when(transaction.query(anyString(), eq(SQLDatabaseWriterFilter.lookupColumnRowMapper), any())).thenThrow(new RuntimeException("boom"));
        Set<String> scripts = new HashSet<String>(Arrays.asList("select 1"));
        assertDoesNotThrow(() -> filter.executeScripts(context, "key", scripts, false));
    }

    @Test
    void testDoTokenReplacementOnSql_withBlankSql() {
        assertNull(filter.doTokenReplacementOnSql(context, null));
        assertEquals("", filter.doTokenReplacementOnSql(context, ""));
    }

    @Test
    void testDoTokenReplacementOnSql_withNoCsvDataInContext() {
        String sql = filter.doTokenReplacementOnSql(context, "select $(sourceCatalogName)");
        assertEquals("select $(sourceCatalogName)", sql);
    }

    @Test
    void testDoTokenReplacementOnSql_withCsvDataButNoTriggerHistory() {
        when(context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA)).thenReturn(new Data());
        String sql = filter.doTokenReplacementOnSql(context, "select $(sourceCatalogName)");
        assertEquals("select $(sourceCatalogName)", sql);
    }

    @Test
    void testDoTokenReplacementOnSql_replacesTokens() {
        Data csvData = new Data();
        TriggerHistory hist = new TriggerHistory();
        hist.setSourceCatalogName("mycat");
        hist.setSourceSchemaName("mysch");
        csvData.setTriggerHistory(hist);
        when(context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA)).thenReturn(csvData);
        String sql = filter.doTokenReplacementOnSql(context, "select * from $(sourceCatalogName).$(sourceSchemaName).tbl");
        assertEquals("select * from mycat.mysch.tbl", sql);
    }

    @Test
    void testProcessError_throwsWhenFailOnError() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setFailOnError(true);
        RuntimeException error = new RuntimeException("boom");
        assertThrows(SymmetricException.class, () -> filter.processError(loadFilter, table, error));
    }

    @Test
    void testProcessError_doesNotThrowWhenNotFailOnError() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setFailOnError(false);
        assertDoesNotThrow(() -> filter.processError(loadFilter, table, new RuntimeException("boom")));
    }

    // Defect pinned, not endorsed: processError formats "N/A" for a null filter but then
    // unconditionally calls currentFilter.isFailOnError(), so a null filter NPEs instead of a graceful no-op.
    @Test
    void testProcessError_withNullFilterThrowsNpe() {
        RuntimeException error = new RuntimeException("boom");
        assertThrows(NullPointerException.class, () -> filter.processError(null, table, error));
    }

    @Test
    void testProcessError_unwrapsTargetError() {
        final TargetError targetError = evalToTargetError("throw new RuntimeException(\"boom\");");
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setFailOnError(true);
        SymmetricException thrown = assertThrows(SymmetricException.class, () -> filter.processError(loadFilter, table, targetError));
        assertEquals("boom", thrown.getCause().getMessage());
    }

    @Test
    void testLookupColumnRowMapper_withBooleanValue() {
        assertTrue(SQLDatabaseWriterFilter.lookupColumnRowMapper.mapRow(new Row("result", Boolean.TRUE)));
        assertFalse(SQLDatabaseWriterFilter.lookupColumnRowMapper.mapRow(new Row("result", Boolean.FALSE)));
    }

    @Test
    void testLookupColumnRowMapper_withNumberValue() {
        assertTrue(SQLDatabaseWriterFilter.lookupColumnRowMapper.mapRow(new Row("result", 1)));
        assertFalse(SQLDatabaseWriterFilter.lookupColumnRowMapper.mapRow(new Row("result", 0)));
    }

    @Test
    void testLookupColumnRowMapper_withStringValue() {
        assertFalse(SQLDatabaseWriterFilter.lookupColumnRowMapper.mapRow(new Row("result", "0")));
        assertFalse(SQLDatabaseWriterFilter.lookupColumnRowMapper.mapRow(new Row("result", "false")));
        assertTrue(SQLDatabaseWriterFilter.lookupColumnRowMapper.mapRow(new Row("result", "1")));
    }

    @Test
    void testLookupColumnRowMapper_withNoColumns() {
        assertFalse(SQLDatabaseWriterFilter.lookupColumnRowMapper.mapRow(new Row(0)));
    }

    private LoadFilter newLoadFilter() {
        LoadFilter loadFilter = new LoadFilter();
        loadFilter.setLoadFilterId("lf1");
        return loadFilter;
    }

    private TargetError evalToTargetError(String script) {
        try {
            new Interpreter().eval(script);
        } catch (EvalError e) {
            return (TargetError) e;
        }
        return null;
    }
}
