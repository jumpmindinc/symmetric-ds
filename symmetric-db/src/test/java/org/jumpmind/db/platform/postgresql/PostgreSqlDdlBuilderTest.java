package org.jumpmind.db.platform.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.UniqueIndex;
import org.jumpmind.db.platform.DatabaseNamesConstants;
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

    @Test
    void testWriteGeneratedColumn_persisted_emitsStoredKeyword() {
        PostgreSqlDdlBuilder ddlBuilder = new PostgreSqlDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        String sql = ddlBuilder.createTable(buildTableWithComputedColumn(true));
        assertTrue(sql.contains("GENERATED ALWAYS AS"));
        assertTrue(sql.contains("STORED"));
        assertFalse(sql.contains("VIRTUAL"));
    }

    @Test
    void testWriteGeneratedColumn_nonPersisted_emitsVirtualKeyword_whenTargetSupportsNonPersisted() {
        PostgreSqlDdlBuilder ddlBuilder = new PostgreSqlDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setNonPersistedGeneratedColumnsSupported(true);
        String sql = ddlBuilder.createTable(buildTableWithComputedColumn(false));
        assertTrue(sql.contains("VIRTUAL"));
        assertFalse(sql.contains("STORED"));
    }

    @Test
    void testWriteGeneratedColumn_nonPersisted_fallsBackToStored_whenTargetLacksNonPersistedSupport() {
        PostgreSqlDdlBuilder ddlBuilder = new PostgreSqlDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setNonPersistedGeneratedColumnsSupported(false);
        String sql = ddlBuilder.createTable(buildTableWithComputedColumn(false));
        assertTrue(sql.contains("STORED"));
        assertFalse(sql.contains("VIRTUAL"));
    }

    @Test
    void testWriteGeneratedColumn_indexRetained_whenPersistedGeneratedColumnsSupported() {
        PostgreSqlDdlBuilder ddlBuilder = new PostgreSqlDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setPersistedGeneratedColumnsSupported(true);
        Table table = buildTableWithComputedColumn(true);
        NonUniqueIndex index = new NonUniqueIndex("idx_computed");
        index.addColumn(new IndexColumn("total"));
        table.addIndex(index);
        String sql = ddlBuilder.createTable(table);
        assertTrue(sql.contains("idx_computed"));
    }

    private Table buildTableWithComputedColumn(boolean persisted) {
        Column idCol = new Column("id", true, Types.INTEGER, 0, 0);
        Column aCol = new Column("a", false, Types.INTEGER, 0, 0);
        Column bCol = new Column("b", false, Types.INTEGER, 0, 0);
        Column computedCol = new Column("total");
        computedCol.setMappedTypeCode(Types.INTEGER);
        computedCol.setGenerated(true);
        computedCol.setPersisted(persisted);
        computedCol.setDefaultValue("(a + b)");
        computedCol.addPlatformColumn(new PlatformColumn(DatabaseNamesConstants.POSTGRESQL, "INTEGER", 0, 0, null));
        return new Table("test_computed", idCol, aCol, bCol, computedCol);
    }
}
