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
package org.jumpmind.symmetric.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

<<<<<<< HEAD
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.RouterService;
=======
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.service.IRouterService;
>>>>>>> a389fbcdc0 (SYM-8244: Changing table assignment to a non-default router stops routing its rows until cache expires (#1266))
import org.jumpmind.util.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationChangedHelperTest {
<<<<<<< HEAD
    private static final String[] ROUTER_COLUMN_NAMES = { "router_id", "router_type", "source_node_group_id" };
    private static final String CURRENT_NODE_GROUP_ID = "store";
    private ConfigurationChangedHelper helper;
    private ISymmetricEngine engine;
    private IParameterService parameterService;
=======
    private static final String TABLE_PREFIX = "sym";
    private ConfigurationChangedHelper helper;
    private ISymmetricEngine engine;
    private ICacheManager cacheManager;
    private IRouterService routerService;
>>>>>>> a389fbcdc0 (SYM-8244: Changing table assignment to a non-default router stops routing its rows until cache expires (#1266))

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
<<<<<<< HEAD
        parameterService = mock(IParameterService.class);
        when(engine.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getNodeGroupId()).thenReturn(CURRENT_NODE_GROUP_ID);
=======
        cacheManager = mock(ICacheManager.class);
        routerService = mock(IRouterService.class);
        when(engine.getTablePrefix()).thenReturn(TABLE_PREFIX);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getRouterService()).thenReturn(routerService);
>>>>>>> a389fbcdc0 (SYM-8244: Changing table assignment to a non-default router stops routing its rows until cache expires (#1266))
        helper = new ConfigurationChangedHelper(engine);
    }

    private Table buildRouterTable() {
        Table table = new Table(TableConstants.getTableName("sym", TableConstants.SYM_ROUTER));
        for (String columnName : ROUTER_COLUMN_NAMES) {
            table.addColumn(new Column(columnName));
        }
        return table;
    }

    @Test
    void testHandleChangeChecksNodeGroupForColumnSegmentRouterOnInsert() {
        Table table = buildRouterTable();
        CsvData data = new CsvData(DataEventType.INSERT,
                new String[] { "router1", RouterService.COLUMN_SEGMENT_ROUTER_TYPE, CURRENT_NODE_GROUP_ID });
        helper.handleChange(new Context(), table, data);
        verify(parameterService).getNodeGroupId();
    }

    @Test
    void testHandleChangeChecksNodeGroupForColumnSegmentRouterOnUpdate() {
        Table table = buildRouterTable();
        CsvData data = new CsvData(DataEventType.UPDATE,
                new String[] { "router1", RouterService.COLUMN_SEGMENT_ROUTER_TYPE, CURRENT_NODE_GROUP_ID });
        helper.handleChange(new Context(), table, data);
        verify(parameterService).getNodeGroupId();
    }

    @Test
    void testHandleChangeChecksNodeGroupForColumnSegmentRouterInDifferentNodeGroup() {
        Table table = buildRouterTable();
        CsvData data = new CsvData(DataEventType.INSERT,
                new String[] { "router1", RouterService.COLUMN_SEGMENT_ROUTER_TYPE, "warehouse" });
        helper.handleChange(new Context(), table, data);
        verify(parameterService).getNodeGroupId();
    }

    @Test
    void testHandleChangeSkipsCheckForNonColumnSegmentRouterType() {
        Table table = buildRouterTable();
        CsvData data = new CsvData(DataEventType.INSERT, new String[] { "router1", "default", CURRENT_NODE_GROUP_ID });
        helper.handleChange(new Context(), table, data);
        verify(parameterService, never()).getNodeGroupId();
    }

    @Test
    void testHandleChangeSkipsCheckOnDelete() {
        Table table = buildRouterTable();
        CsvData data = new CsvData(DataEventType.DELETE,
                new String[] { "router1", RouterService.COLUMN_SEGMENT_ROUTER_TYPE, CURRENT_NODE_GROUP_ID });
        helper.handleChange(new Context(), table, data);
        verify(parameterService, never()).getNodeGroupId();
    }

    @Test
    void testGetSqlStatements() {
        String script = "CREATE TABLE customers (ID INT);";
        List<String> result = helper.getSqlStatements(script);
        assertEquals(1, result.size());
        assertEquals(removeTrailingDelimiter(script), result.get(0));
    }

    @Test
    void testGetSqlStatementsWithDelimiter() {
        String statement = "CREATE TABLE customers (ID int)-";
        String script = "delimiter -;\n" + statement;
        List<String> result = helper.getSqlStatements(script);
        assertEquals(1, result.size());
        assertEquals(removeTrailingDelimiter(statement), result.get(0));
    }

    @Test
    void testGetSqlStatementsWithComments() {
        String statement = "CREATE TABLE customers (ID int);";
        String script = "--todo:test\n" + statement;
        List<String> result = helper.getSqlStatements(script);
        assertEquals(1, result.size());
        assertEquals(removeTrailingDelimiter(statement), result.get(0));
    }

    @Test
    void testGetSqlStatementsWithMultiple() {
        String statement0 = "CREATE TABLE customers (ID int);";
        String statement1 = "INSERT INTO items VALUES (1);";
        String script = statement0 + "\n" + statement1;
        List<String> result = helper.getSqlStatements(script);
        assertEquals(2, result.size());
        assertEquals(removeTrailingDelimiter(statement0), result.get(0));
        assertEquals(removeTrailingDelimiter(statement1), result.get(1));
    }

    @Test
    void testHandleChange_flushesRoutersOnTriggerRouterInsert() {
        handleChangeAndCommit(TableConstants.SYM_TRIGGER_ROUTER, DataEventType.INSERT);
        verifyRoutersFlushed();
    }

    @Test
    void testHandleChange_flushesRoutersOnTriggerRouterUpdate() {
        handleChangeAndCommit(TableConstants.SYM_TRIGGER_ROUTER, DataEventType.UPDATE);
        verifyRoutersFlushed();
    }

    @Test
    void testHandleChange_flushesRoutersOnTriggerRouterDelete() {
        handleChangeAndCommit(TableConstants.SYM_TRIGGER_ROUTER, DataEventType.DELETE);
        verifyRoutersFlushed();
    }

    @Test
    void testHandleChange_flushesRoutersOnTriggerUpdate() {
        handleChangeAndCommit(TableConstants.SYM_TRIGGER, DataEventType.UPDATE);
        verifyRoutersFlushed();
    }

    @Test
    void testHandleChange_doesNotFlushRoutersOnUnrelatedTable() {
        handleChangeAndCommit(TableConstants.SYM_TRANSFORM_TABLE, DataEventType.UPDATE);
        verify(cacheManager, never()).flushAllWithRouters();
        verify(routerService, never()).flushCache();
    }

    private void handleChangeAndCommit(String tableSuffix, DataEventType eventType) {
        Context context = new Context();
        Table table = new Table(TableConstants.getTableName(TABLE_PREFIX, tableSuffix));
        helper.handleChange(context, table, new CsvData(eventType));
        helper.contextCommitted(context);
    }

    private void verifyRoutersFlushed() {
        verify(cacheManager).flushAllWithRouters();
        verify(routerService).flushCache();
    }

    private static String removeTrailingDelimiter(String input) {
        if (input != null && (input.endsWith(";") || input.endsWith("-"))) {
            return input.substring(0, input.length() - 1);
        }
        return input;
    }
}
