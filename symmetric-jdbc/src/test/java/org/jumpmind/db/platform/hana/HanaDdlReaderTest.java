package org.jumpmind.db.platform.hana;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HanaDdlReaderTest {
    private HanaDatabasePlatform platform;
    private HanaDdlReader ddlReader;

    @BeforeEach
    void setup() {
        platform = mock(HanaDatabasePlatform.class);
        ddlReader = new HanaDdlReader(platform);
    }

    @Test
    void testConstructor() {
        assertNotNull(ddlReader);
    }

    @Test
    void testDetermineExtraColumnInfo_byDefaultAsIdentity() {
        ISqlTemplate sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateMock);
        Row row = mock(Row.class);
        Column column = new Column("TEST_COLUMN");
        when(row.getString("column_name")).thenReturn(column.getName());
        when(row.getString("generation_type")).thenReturn("By dEFAuLT aS IdEntItY");
        when(sqlTemplateMock.query(any(String.class), any(Object[].class))).thenReturn(List.of(row));
        Table table = mock(Table.class);
        table.setName("TEST_TABLE");
        table.setSchema("TEST_SCHEMA");
        when(table.findColumn(column.getName())).thenReturn(column);
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        ddlReader.determineExtraColumnInfo(table);
        assertTrue(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        verify(sqlTemplateMock).query("SELECT column_name, generation_type FROM sys.table_columns WHERE schema_name = ? AND table_name = ?", new Object[] {
                table.getSchema(), table.getName() });
    }

    @Test
    void testDetermineExtraColumnInfo_alwaysAsIdentity() {
        ISqlTemplate sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateMock);
        Row row = mock(Row.class);
        Column column = new Column("TEST_COLUMN");
        when(row.getString("column_name")).thenReturn(column.getName());
        when(row.getString("generation_type")).thenReturn("always as identity");
        when(sqlTemplateMock.query(any(String.class), any(Object[].class))).thenReturn(List.of(row));
        Table table = mock(Table.class);
        table.setName("TEST_TABLE");
        table.setSchema("TEST_SCHEMA");
        when(table.findColumn(column.getName())).thenReturn(column);
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        ddlReader.determineExtraColumnInfo(table);
        assertTrue(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        verify(sqlTemplateMock).query("SELECT column_name, generation_type FROM sys.table_columns WHERE schema_name = ? AND table_name = ?", new Object[] {
                table.getSchema(), table.getName() });
    }

    @Test
    void testDetermineExtraColumnInfo_nullAutoIncrementValue() {
        ISqlTemplate sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateMock);
        Row row = mock(Row.class);
        Column column = new Column("TEST_COLUMN");
        when(row.getString("column_name")).thenReturn(column.getName());
        when(row.getString("generation_type")).thenReturn(null);
        when(sqlTemplateMock.query(any(String.class), any(Object[].class))).thenReturn(List.of(row));
        Table table = mock(Table.class);
        table.setName("TEST_TABLE");
        table.setSchema("TEST_SCHEMA");
        when(table.findColumn(column.getName())).thenReturn(column);
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        ddlReader.determineExtraColumnInfo(table);
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        verify(sqlTemplateMock).query("SELECT column_name, generation_type FROM sys.table_columns WHERE schema_name = ? AND table_name = ?", new Object[] {
                table.getSchema(), table.getName() });
    }

    @Test
    void testDetermineExtraColumnInfo_nullColumn() {
        ISqlTemplate sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateMock);
        Row row = mock(Row.class);
        Column column = new Column("TEST_COLUMN");
        when(row.getString("column_name")).thenReturn(column.getName());
        when(row.getString("generation_type")).thenReturn(null);
        when(sqlTemplateMock.query(any(String.class), any(Object[].class))).thenReturn(List.of(row));
        Table table = mock(Table.class);
        table.setName("TEST_TABLE");
        table.setSchema("TEST_SCHEMA");
        when(table.findColumn(column.getName())).thenReturn(null);
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        ddlReader.determineExtraColumnInfo(table);
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        verify(sqlTemplateMock).query("SELECT column_name, generation_type FROM sys.table_columns WHERE schema_name = ? AND table_name = ?", new Object[] {
                table.getSchema(), table.getName() });
    }

    @Test
    void testDetermineExtraColumnInfo_noRows() {
        ISqlTemplate sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateMock);
        when(sqlTemplateMock.query(any(String.class), any(Object[].class))).thenReturn(Collections.emptyList());
        Table testTable = mock(Table.class);
        ddlReader.determineExtraColumnInfo(testTable);
        verify(sqlTemplateMock).query("SELECT column_name, generation_type FROM sys.table_columns WHERE schema_name = ? AND table_name = ?", new Object[] {
                testTable.getSchema(), testTable.getName() });
        verify(testTable, times(1)).getFullyQualifiedName(); // verifies warn level logging
    }
}
