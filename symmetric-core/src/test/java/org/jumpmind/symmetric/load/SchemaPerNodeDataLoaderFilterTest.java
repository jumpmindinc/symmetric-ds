package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.DataContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaPerNodeDataLoaderFilterTest {
    private SchemaPerNodeDataLoaderFilter filter;
    private DataContext context;
    private Table table;

    @BeforeEach
    void setUp() {
        filter = new SchemaPerNodeDataLoaderFilter();
        filter.setTablePrefix("sym_");
        Batch batch = new Batch();
        batch.setSourceNodeId("node1");
        context = new DataContext();
        context.setBatch(batch);
        table = new Table("TEST_TABLE");
    }

    @Test
    void testBeforeWrite_setsSchemaToSourceNodeId() {
        boolean result = filter.beforeWrite(context, table, null);
        assertTrue(result);
        assertEquals("node1", table.getSchema());
    }

    @Test
    void testBeforeWrite_withSchemaPrefixPrependsPrefix() {
        filter.setSchemaPrefix("tenant_");
        boolean result = filter.beforeWrite(context, table, null);
        assertTrue(result);
        assertEquals("tenant_node1", table.getSchema());
    }

    @Test
    void testBeforeWrite_skipsWhenTableNameStartsWithPrefix() {
        Table symTable = new Table("sym_node");
        boolean result = filter.beforeWrite(context, symTable, null);
        assertTrue(result);
        assertNull(symTable.getSchema());
    }
}
