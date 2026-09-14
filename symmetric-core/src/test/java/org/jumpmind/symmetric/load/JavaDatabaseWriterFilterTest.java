package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.load.DynamicDatabaseWriterFilter.WriteMethod;
import org.jumpmind.symmetric.load.JavaDatabaseWriterFilter.JavaLoadFilter;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.service.IExtensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaDatabaseWriterFilterTest {
    private ISymmetricEngine engine;
    private IExtensionService extensionService;
    private JavaDatabaseWriterFilter filter;
    private Table table;
    private CsvData data;
    private DataContext context;
    private JavaLoadFilter compiledFilter;

    @BeforeEach
    void setUp() throws Exception {
        engine = mock(ISymmetricEngine.class);
        extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        compiledFilter = mock(JavaLoadFilter.class);
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiledFilter);
        filter = new JavaDatabaseWriterFilter(engine, new HashMap<String, List<LoadFilter>>());
        table = new Table("CAT", "SCH", "TEST", new String[] { "ID" }, new String[] { "ID" });
        data = new CsvData(DataEventType.INSERT);
        context = mock(DataContext.class);
    }

    @Test
    void testProcessLoadFilters_beforeWriteReturnsTrue() throws Exception {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("return true;");
        when(compiledFilter.execute(context, table, data, null)).thenReturn(true);
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_beforeWriteReturnsFalse() throws Exception {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("return false;");
        when(compiledFilter.execute(context, table, data, null)).thenReturn(false);
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_afterWrite() throws Exception {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setAfterWriteScript("return false;");
        when(compiledFilter.execute(context, table, data, null)).thenReturn(false);
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.AFTER_WRITE, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_handleError() throws Exception {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setHandleErrorScript("return false;");
        Exception error = new Exception("boom");
        when(compiledFilter.execute(context, table, data, error)).thenReturn(false);
        boolean writeRow = filter.processLoadFilters(context, table, data, error, WriteMethod.HANDLE_ERROR, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_withNonMatchingEventType() throws Exception {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setFilterOnInsert(false);
        loadFilter.setBeforeWriteScript("return false;");
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
        verify(extensionService, never()).getCompiledClass(anyString());
    }

    @Test
    void testProcessLoadFilters_withBlankScript() throws Exception {
        LoadFilter loadFilter = newLoadFilter();
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
        verify(extensionService, never()).getCompiledClass(anyString());
    }

    @Test
    void testProcessLoadFilters_throwsWhenFailOnError() throws Exception {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("invalid(");
        loadFilter.setFailOnError(true);
        when(extensionService.getCompiledClass(anyString())).thenThrow(new RuntimeException("compile failed"));
        assertThrows(SymmetricException.class,
                () -> filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter)));
    }

    @Test
    void testProcessLoadFilters_swallowsErrorWhenNotFailOnError() throws Exception {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("invalid(");
        loadFilter.setFailOnError(false);
        when(extensionService.getCompiledClass(anyString())).thenThrow(new RuntimeException("compile failed"));
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testExecuteScripts_withScripts() throws Exception {
        Set<String> scripts = new HashSet<String>(Arrays.asList("return true;", "return false;"));
        filter.executeScripts(context, "key", scripts, false);
        verify(extensionService, times(2)).getCompiledClass(anyString());
    }

    @Test
    void testExecuteScripts_withNullScripts() throws Exception {
        filter.executeScripts(context, "key", null, false);
        verify(extensionService, never()).getCompiledClass(anyString());
    }

    @Test
    void testExecuteScripts_throwsWhenFailOnError() throws Exception {
        when(extensionService.getCompiledClass(anyString())).thenThrow(new RuntimeException("compile failed"));
        Set<String> scripts = new HashSet<String>(Arrays.asList("bad script"));
        assertThrows(SymmetricException.class, () -> filter.executeScripts(context, "key", scripts, true));
    }

    @Test
    void testExecuteScripts_swallowsErrorWhenNotFailOnError() throws Exception {
        when(extensionService.getCompiledClass(anyString())).thenThrow(new RuntimeException("compile failed"));
        Set<String> scripts = new HashSet<String>(Arrays.asList("bad script"));
        filter.executeScripts(context, "key", scripts, false);
    }

    @Test
    void testGetCompiledClass() throws Exception {
        JavaLoadFilter result = filter.getCompiledClass("return true;");
        assertEquals(compiledFilter, result);
        verify(extensionService).getCompiledClass(contains("return true;"));
    }

    @Test
    void testCountHeaderLines() {
        assertEquals(6, JavaDatabaseWriterFilter.countHeaderLines());
    }

    private LoadFilter newLoadFilter() {
        LoadFilter loadFilter = new LoadFilter();
        loadFilter.setLoadFilterId("lf1");
        return loadFilter;
    }
}
