package org.jumpmind.symmetric.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.util.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationChangedHelperTest {
    private static final String TABLE_PREFIX = "sym";
    private ConfigurationChangedHelper helper;
    private ISymmetricEngine engine;
    private ICacheManager cacheManager;
    private IRouterService routerService;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        cacheManager = mock(ICacheManager.class);
        routerService = mock(IRouterService.class);
        when(engine.getTablePrefix()).thenReturn(TABLE_PREFIX);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getRouterService()).thenReturn(routerService);
        helper = new ConfigurationChangedHelper(engine);
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
