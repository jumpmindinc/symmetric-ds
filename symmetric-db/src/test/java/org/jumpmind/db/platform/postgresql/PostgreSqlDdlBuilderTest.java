package org.jumpmind.db.platform.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.UniqueIndex;
import org.junit.jupiter.api.Test;

public class PostgreSqlDdlBuilderTest {
    @Test
    void testWriteExternalIndexCreate() {
        PostgreSqlDdlBuilder ddlBuilder = new PostgreSqlDdlBuilder();
        StringBuilder ddl = new StringBuilder();
        Table table = new Table("test_table");
        IndexColumn column1 = new IndexColumn("test_column1");
        IIndex index = new UniqueIndex("test_index");
        index.addColumn(column1);
        ddlBuilder.writeExternalIndexCreate(table, index, ddl);
        String expectedDdl = "CREATE UNIQUE INDEX \"test_index\" ON \"test_table\" (\"test_column1\")";
        assertEquals(expectedDdl, ddl.toString().trim());
    }

    @Test
    void testWriteExternalIndexCreateWithInclude() {
        PostgreSqlDdlBuilder ddlBuilder = new PostgreSqlDdlBuilder();
        StringBuilder ddl = new StringBuilder();
        Table table = new Table("test_table");
        IndexColumn column1 = new IndexColumn("test_column1");
        IndexColumn column2 = new IndexColumn("test_column2");
        IIndex index = new UniqueIndex("test_index");
        index.addColumn(column1);
        index.addIncludedColumn(column2);
        ddlBuilder.writeExternalIndexCreate(table, index, ddl);
        String expectedDdl = "CREATE UNIQUE INDEX \"test_index\" ON \"test_table\" (\"test_column1\") INCLUDE (\"test_column2\")";
        assertEquals(expectedDdl, ddl.toString().trim());
    }
}
