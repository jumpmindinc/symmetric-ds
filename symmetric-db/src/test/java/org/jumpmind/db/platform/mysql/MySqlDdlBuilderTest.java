package org.jumpmind.db.platform.mysql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
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
}
