package org.jumpmind.db.platform.mysql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.junit.jupiter.api.Test;

public class MySqlDdlBuilderTest {
    @Test
    void testWriteEmbeddedPrimaryKeysStmt_SinglePkAutoIncrement_NoExtraKey() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        Column id = new Column("id", true, Types.INTEGER, 10, 0);
        id.setAutoIncrement(true);
        Table table = new Table("test_table", id);
        String ddl = ddlBuilder.createTable(table);
        assertTrue(ddl.contains("PRIMARY KEY"), "Should contain PRIMARY KEY");
        assertFalse(ddl.contains("KEY `id` (`id`)"), "Single PK should not add extra KEY");
    }

    @Test
    void testWriteEmbeddedPrimaryKeysStmt_CompositePkNoAutoIncrement_NoExtraKey() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        Column col1 = new Column("col1", true, Types.INTEGER, 10, 0);
        Column col2 = new Column("col2", true, Types.INTEGER, 10, 0);
        Table table = new Table("test_table", col1, col2);
        String ddl = ddlBuilder.createTable(table);
        assertTrue(ddl.contains("PRIMARY KEY"), "Should contain PRIMARY KEY");
        assertFalse(ddl.contains("KEY `col1` (`col1`)"), "No extra KEY expected for non-auto-increment composite PK");
        assertFalse(ddl.contains("KEY `col2` (`col2`)"), "No extra KEY expected for non-auto-increment composite PK");
    }

    @Test
    void testWriteEmbeddedPrimaryKeysStmt_CompositePkAutoIncrementOnFirstColumn_NoExtraKey() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        Column col1 = new Column("col1", true, Types.INTEGER, 10, 0);
        col1.setAutoIncrement(true);
        Column col2 = new Column("col2", true, Types.INTEGER, 10, 0);
        Table table = new Table("test_table", col1, col2);
        String ddl = ddlBuilder.createTable(table);
        assertTrue(ddl.contains("PRIMARY KEY"), "Should contain PRIMARY KEY");
        assertFalse(ddl.contains("KEY `col1` (`col1`)"), "Auto-increment on first PK column should not add extra KEY");
        assertFalse(ddl.contains("KEY `col2` (`col2`)"), "Non-auto-increment column should not add extra KEY");
    }

    @Test
    void testWriteEmbeddedPrimaryKeysStmt_CompositePkAutoIncrementOnNonFirstColumn_AddsExtraKey() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        Column col1 = new Column("col1", true, Types.INTEGER, 10, 0);
        Column col2 = new Column("col2", true, Types.INTEGER, 10, 0);
        col2.setAutoIncrement(true);
        Table table = new Table("test_table", col1, col2);
        String ddl = ddlBuilder.createTable(table);
        assertTrue(ddl.contains("PRIMARY KEY"), "Should contain PRIMARY KEY");
        assertTrue(ddl.contains("KEY `col2` (`col2`)"), "Auto-increment on non-first PK column should add extra KEY");
        assertFalse(ddl.contains("KEY `col1` (`col1`)"), "Non-auto-increment column should not add extra KEY");
    }

    @Test
    void testGetSqlType_EnumColumnTaggedWithAuroraMySqlVariant_StillAppliesEnumValues() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        Column column = new Column("status", false, Types.CHAR, 10, 0);
        column.setJdbcTypeName("ENUM");
        PlatformColumn platformColumn = new PlatformColumn(DatabaseNamesConstants.AURORA_MYSQL, "ENUM", null);
        platformColumn.setEnumValues(new String[] { "ACTIVE", "INACTIVE" });
        column.addPlatformColumn(platformColumn);
        String sqlType = ddlBuilder.getSqlType(column);
        assertTrue(sqlType.contains("'ACTIVE'") && sqlType.contains("'INACTIVE'"),
                "Expected enum values sourced via the MySQL-family PlatformColumn even though it was tagged '"
                        + DatabaseNamesConstants.AURORA_MYSQL + "' instead of the literal '" + DatabaseNamesConstants.MYSQL + "'");
    }

    @Test
    void testWriteGeneratedColumn_persisted_emitsStoredKeyword() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        String ddl = ddlBuilder.createTable(buildTableWithComputedColumn(true));
        assertTrue(ddl.contains(" AS (a + b) STORED"), "Expected persisted generated column to be written as STORED");
        assertFalse(ddl.contains("VIRTUAL"), "Persisted generated column should not be written as VIRTUAL");
    }

    @Test
    void testWriteGeneratedColumn_nonPersisted_emitsVirtualKeyword() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        String ddl = ddlBuilder.createTable(buildTableWithComputedColumn(false));
        assertTrue(ddl.contains(" AS (a + b) VIRTUAL"), "Expected non-persisted generated column to be written as VIRTUAL");
        assertFalse(ddl.contains("STORED"), "Non-persisted generated column should not be written as STORED");
    }

    @Test
    void testWriteGeneratedColumn_indexRetained_whenPersistedGeneratedColumnsSupported() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setPersistedGeneratedColumnsSupported(true);
        Table table = buildTableWithComputedColumn(true);
        NonUniqueIndex index = new NonUniqueIndex("idx_computed");
        index.addColumn(new IndexColumn("total"));
        table.addIndex(index);
        String ddl = ddlBuilder.createTable(table);
        assertTrue(ddl.contains("idx_computed"), "Expected index on persisted generated column to be created");
    }

    @Test
    void testWriteGeneratedColumn_indexSkipped_forNonPersistedColumn() {
        MySqlDdlBuilder ddlBuilder = new MySqlDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setPersistedGeneratedColumnsSupported(true);
        Table table = buildTableWithComputedColumn(false);
        NonUniqueIndex index = new NonUniqueIndex("idx_computed");
        index.addColumn(new IndexColumn("total"));
        table.addIndex(index);
        String ddl = ddlBuilder.createTable(table);
        assertFalse(ddl.contains("idx_computed"), "Index on a non-persisted (VIRTUAL) generated column should be skipped");
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
        computedCol.addPlatformColumn(new PlatformColumn(DatabaseNamesConstants.MYSQL, "INTEGER", null));
        return new Table("test_computed", idCol, aCol, bCol, computedCol);
    }
}
