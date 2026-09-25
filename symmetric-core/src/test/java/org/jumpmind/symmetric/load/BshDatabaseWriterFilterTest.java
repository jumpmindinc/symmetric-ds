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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
import org.jumpmind.symmetric.model.LoadFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.TargetError;

class BshDatabaseWriterFilterTest {
    private ISymmetricEngine engine;
    private BshDatabaseWriterFilter filter;
    private Table table;
    private CsvData data;
    private DataContext context;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        filter = new BshDatabaseWriterFilter(engine, new HashMap<String, List<LoadFilter>>());
        table = new Table("CAT", "SCH", "TEST", new String[] { "id", "name" }, new String[] { "id" });
        data = new CsvData(DataEventType.INSERT);
        data.putParsedData(CsvData.ROW_DATA, new String[] { "1", "Bob" });
        context = new DataContext();
    }

    @Test
    void testProcessLoadFilters_beforeWriteReturnsTrue() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("return true;");
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_beforeWriteReturnsFalse() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("return false;");
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_afterWrite() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setAfterWriteScript("return false;");
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.AFTER_WRITE, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_handleError() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setHandleErrorScript("return false;");
        boolean writeRow = filter.processLoadFilters(context, table, data, new Exception("boom"), WriteMethod.HANDLE_ERROR, Arrays.asList(loadFilter));
        assertFalse(writeRow);
    }

    @Test
    void testProcessLoadFilters_withNonMatchingEventType() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setFilterOnInsert(false);
        loadFilter.setBeforeWriteScript("return false;");
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_withBlankScript() {
        LoadFilter loadFilter = newLoadFilter();
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_scriptCanReadRowDataColumns() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("return NAME.equals(\"Bob\");");
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_scriptCanReadPkDataColumnsWhenNoRowData() {
        CsvData pkOnlyData = new CsvData(DataEventType.INSERT);
        pkOnlyData.putParsedData(CsvData.PK_DATA, new String[] { "7" });
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("return ID.equals(\"7\");");
        boolean writeRow = filter.processLoadFilters(context, table, pkOnlyData, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_scriptCanReadOldDataColumns() {
        data.putParsedData(CsvData.OLD_DATA, new String[] { "0", "Ann" });
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("return OLD_NAME.equals(\"Ann\");");
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testProcessLoadFilters_throwsWhenFailOnError() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("this is not valid bsh {{{");
        loadFilter.setFailOnError(true);
        List<LoadFilter> loadFilters = Arrays.asList(loadFilter);
        assertThrows(SymmetricException.class,
                () -> filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, loadFilters));
    }

    @Test
    void testProcessLoadFilters_swallowsErrorWhenNotFailOnError() {
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setBeforeWriteScript("this is not valid bsh {{{");
        loadFilter.setFailOnError(false);
        boolean writeRow = filter.processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE, Arrays.asList(loadFilter));
        assertTrue(writeRow);
    }

    @Test
    void testExecuteScripts_withValidScript() {
        Set<String> scripts = new HashSet<String>(Arrays.asList("1 + 1;"));
        assertDoesNotThrow(() -> filter.executeScripts(context, "key", scripts, true));
    }

    @Test
    void testExecuteScripts_withNullScripts() {
        assertDoesNotThrow(() -> filter.executeScripts(context, "key", null, false));
    }

    @Test
    void testExecuteScripts_throwsParseExceptionWhenFailOnError() {
        Set<String> scripts = new HashSet<String>(Arrays.asList("this is not valid bsh {{{"));
        assertThrows(SymmetricException.class, () -> filter.executeScripts(context, "key", scripts, true));
    }

    @Test
    void testExecuteScripts_swallowsParseExceptionWhenNotFailOnError() {
        Set<String> scripts = new HashSet<String>(Arrays.asList("this is not valid bsh {{{"));
        assertDoesNotThrow(() -> filter.executeScripts(context, "key", scripts, false));
    }

    @Test
    void testExecuteScripts_rethrowsRuntimeTargetExceptionWhenFailOnError() {
        Set<String> scripts = new HashSet<String>(Arrays.asList("throw new RuntimeException(\"boom\");"));
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> filter.executeScripts(context, "key", scripts, true));
        assertEquals("boom", thrown.getMessage());
    }

    @Test
    void testExecuteScripts_wrapsCheckedTargetExceptionWhenFailOnError() {
        Set<String> scripts = new HashSet<String>(Arrays.asList("throw new java.io.IOException(\"ioboom\");"));
        SymmetricException thrown = assertThrows(SymmetricException.class, () -> filter.executeScripts(context, "key", scripts, true));
        assertEquals("ioboom", thrown.getCause().getMessage());
    }

    @Test
    void testExecuteScripts_swallowsTargetExceptionWhenNotFailOnError() {
        Set<String> scripts = new HashSet<String>(Arrays.asList("throw new RuntimeException(\"boom\");"));
        assertDoesNotThrow(() -> filter.executeScripts(context, "key", scripts, false));
    }

    @Test
    void testGetInterpreter_reusesSameInstanceFromContext() {
        Interpreter first = filter.getInterpreter(context);
        Interpreter second = filter.getInterpreter(context);
        assertNotNull(first);
        assertSame(first, second);
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

    @Test
    void testProcessError_unwrapsTargetError() {
        TargetError targetError = evalToTargetError("throw new RuntimeException(\"boom\");");
        LoadFilter loadFilter = newLoadFilter();
        loadFilter.setFailOnError(true);
        SymmetricException thrown = assertThrows(SymmetricException.class, () -> filter.processError(loadFilter, table, targetError));
        assertEquals("boom", thrown.getCause().getMessage());
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
