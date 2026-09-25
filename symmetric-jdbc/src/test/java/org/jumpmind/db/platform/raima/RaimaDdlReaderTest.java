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
package org.jumpmind.db.platform.raima;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

class RaimaDdlReaderTest {
    private IDatabasePlatform platform;
    private RaimaDdlReader ddlReader;

    @BeforeEach
    void setup() {
        platform = mock(IDatabasePlatform.class);
        ddlReader = new RaimaDdlReader(platform);
    }

    @Test
    void testConstructor_clearsDefaultPatterns() {
        assertNull(ddlReader.getDefaultCatalogPattern());
        assertNull(ddlReader.getDefaultSchemaPattern());
        assertNull(ddlReader.getDefaultTablePattern());
    }

    @Test
    void testRemoveNonPkDuplicateIndices_removesNonPrimaryKeyDuplicates() throws Exception {
        Column pkColumn = new Column("id", true, Types.INTEGER, 0, 0);
        Table table = new Table("my_table", pkColumn);
        NonUniqueIndex pkIndex = new NonUniqueIndex("pk_index");
        pkIndex.addColumn(new IndexColumn(pkColumn));
        NonUniqueIndex otherIndex = new NonUniqueIndex("other_index");
        otherIndex.addColumn(new IndexColumn(new Column("other_col")));
        table.addIndex(pkIndex);
        table.addIndex(otherIndex);
        Method method = RaimaDdlReader.class.getDeclaredMethod("removeNonPkDuplicateIndices", Table.class);
        method.setAccessible(true);
        method.invoke(ddlReader, table);
        assertEquals(1, table.getIndexCount());
        assertEquals("other_index", table.getIndex(0).getName());
    }

    @Test
    void testRemoveNonPkDuplicateIndices_noIndices_doesNothing() throws Exception {
        Table table = new Table("my_table");
        Method method = RaimaDdlReader.class.getDeclaredMethod("removeNonPkDuplicateIndices", Table.class);
        method.setAccessible(true);
        method.invoke(ddlReader, table);
        assertEquals(0, table.getIndexCount());
    }

    @Test
    void testIsPrimaryKeyIndex_matchingColumns_returnsTrue() throws Exception {
        Column pkColumn = new Column("id", true, Types.INTEGER, 0, 0);
        Table table = new Table("my_table", pkColumn);
        NonUniqueIndex index = new NonUniqueIndex("pk_index");
        index.addColumn(new IndexColumn(pkColumn));
        Method method = RaimaDdlReader.class.getDeclaredMethod("isPrimaryKeyIndex", IIndex.class, Table.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(ddlReader, index, table);
        assertTrue(result);
    }

    @Test
    void testIsPrimaryKeyIndex_differentColumnCount_returnsFalse() throws Exception {
        Column pkColumn = new Column("id", true, Types.INTEGER, 0, 0);
        Table table = new Table("my_table", pkColumn);
        NonUniqueIndex index = new NonUniqueIndex("multi_col_index");
        index.addColumn(new IndexColumn(pkColumn));
        index.addColumn(new IndexColumn(new Column("other_col")));
        Method method = RaimaDdlReader.class.getDeclaredMethod("isPrimaryKeyIndex", IIndex.class, Table.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(ddlReader, index, table);
        assertFalse(result);
    }

    @Test
    void testIsPrimaryKeyIndex_nonPkColumn_returnsFalse() throws Exception {
        Column pkColumn = new Column("id", true, Types.INTEGER, 0, 0);
        Table table = new Table("my_table", pkColumn);
        NonUniqueIndex index = new NonUniqueIndex("other_index");
        index.addColumn(new IndexColumn(new Column("other_col")));
        Method method = RaimaDdlReader.class.getDeclaredMethod("isPrimaryKeyIndex", IIndex.class, Table.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(ddlReader, index, table);
        assertFalse(result);
    }

    @Test
    void testReadColumn_populatesColumnFromValues() throws Exception {
        DatabaseMetaDataWrapper metaData = mock(DatabaseMetaDataWrapper.class);
        Map<String, Object> values = new HashMap<>();
        values.put("COLUMN_NAME", "MY_COLUMN");
        values.put("TYPE_NAME", "VARCHAR");
        values.put("DATA_TYPE", Types.VARCHAR);
        values.put("NUM_PREC_RADIX", 10);
        values.put("COLUMN_SIZE", "50");
        values.put("DECIMAL_DIGITS", 0);
        values.put("IS_NULLABLE", "NO");
        Column column = ddlReader.readColumn(metaData, values);
        assertNotNull(column);
        assertEquals("MY_COLUMN", column.getName());
        assertEquals(Types.VARCHAR, column.getMappedTypeCode());
        assertTrue(column.isRequired());
    }

    @Test
    void testIsInternalPrimaryKeyIndex_matchingName_returnsTrue() {
        Table table = new Table("my_table");
        IIndex index = new NonUniqueIndex("MY_TABLE..PRIMARY_KEY");
        assertTrue(ddlReader.isInternalPrimaryKeyIndex(null, null, table, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_nonMatchingName_returnsFalse() {
        Table table = new Table("my_table");
        IIndex index = new NonUniqueIndex("some_other_index");
        assertFalse(ddlReader.isInternalPrimaryKeyIndex(null, null, table, index));
    }

    @Test
    void testIsInternalForeignKeyIndex_matchingForeignKeyName_returnsTrue() {
        Table table = new Table("child_table");
        ForeignKey fk = new ForeignKey("fk_test");
        IIndex index = new NonUniqueIndex("fk_test");
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(ddlBuilder.getForeignKeyName(table, fk)).thenReturn("fk_test");
        assertTrue(ddlReader.isInternalForeignKeyIndex(null, null, table, fk, index));
    }

    @Test
    void testIsInternalForeignKeyIndex_nonMatchingForeignKeyName_returnsFalse() {
        Table table = new Table("child_table");
        ForeignKey fk = new ForeignKey("fk_test");
        IIndex index = new NonUniqueIndex("some_other_index");
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(ddlBuilder.getForeignKeyName(table, fk)).thenReturn("fk_test");
        assertFalse(ddlReader.isInternalForeignKeyIndex(null, null, table, fk, index));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetTriggers_mapsKnownAndUnknownEventTypes() {
        JdbcSqlTemplate sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        Row insertRow = mock(Row.class);
        when(insertRow.getString("NAME")).thenReturn("trg_insert");
        when(insertRow.getString("SCHEMANAME")).thenReturn("my_schema");
        when(insertRow.getString("TABNAME")).thenReturn("my_table");
        when(insertRow.getString("EVENT")).thenReturn("insert");
        Row updateRow = mock(Row.class);
        when(updateRow.getString("NAME")).thenReturn("trg_update");
        when(updateRow.getString("SCHEMANAME")).thenReturn("my_schema");
        when(updateRow.getString("TABNAME")).thenReturn("my_table");
        when(updateRow.getString("EVENT")).thenReturn("UPDATE");
        Row deleteRow = mock(Row.class);
        when(deleteRow.getString("NAME")).thenReturn("trg_delete");
        when(deleteRow.getString("SCHEMANAME")).thenReturn("my_schema");
        when(deleteRow.getString("TABNAME")).thenReturn("my_table");
        when(deleteRow.getString("EVENT")).thenReturn("DELETE");
        Row unknownRow = mock(Row.class);
        when(unknownRow.getString("NAME")).thenReturn("trg_other");
        when(unknownRow.getString("SCHEMANAME")).thenReturn("my_schema");
        when(unknownRow.getString("TABNAME")).thenReturn("my_table");
        when(unknownRow.getString("EVENT")).thenReturn("SELECT");
        List<Row> rows = List.of(insertRow, updateRow, deleteRow, unknownRow);
        when(sqlTemplateMock.query(anyString(), any(ISqlRowMapper.class), any(Object[].class)))
                .thenAnswer((InvocationOnMock invocation) -> {
                    ISqlRowMapper<Trigger> mapper = invocation.getArgument(1);
                    List<Trigger> triggers = new ArrayList<>();
                    for (Row row : rows) {
                        triggers.add(mapper.mapRow(row));
                    }
                    return triggers;
                });
        List<Trigger> triggers = ddlReader.getTriggers("catalog", "my_schema", "my_table");
        assertEquals(4, triggers.size());
        assertEquals(TriggerType.INSERT, triggers.get(0).getTriggerType());
        assertEquals(TriggerType.UPDATE, triggers.get(1).getTriggerType());
        assertEquals(TriggerType.DELETE, triggers.get(2).getTriggerType());
        assertNull(triggers.get(3).getTriggerType());
        for (Trigger trigger : triggers) {
            assertTrue(trigger.isEnabled());
            assertEquals("my_schema", trigger.getSchemaName());
            assertEquals("my_table", trigger.getTableName());
        }
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_rowid_mapsToBigint() {
        Map<String, Object> values = new HashMap<>();
        values.put("DATA_TYPE", Types.ROWID);
        assertEquals(Types.BIGINT, ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_nonRowid_delegatesToSuper() {
        Map<String, Object> values = new HashMap<>();
        values.put("DATA_TYPE", Types.INTEGER);
        assertNull(ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_nullType_delegatesToSuper() {
        Map<String, Object> values = new HashMap<>();
        values.put("DATA_TYPE", null);
        assertNull(ddlReader.mapUnknownJdbcTypeForColumn(values));
    }
}
