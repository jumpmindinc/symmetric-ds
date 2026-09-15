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
package org.jumpmind.db.platform.nuodb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NuoDbDdlReaderTest {
    private IDatabasePlatform platform;
    private NuoDbDdlReader ddlReader;

    @BeforeEach
    void setUp() {
        platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.NUODB);
        ddlReader = new NuoDbDdlReader(platform);
    }

    @Test
    void testConstructor_clearsDefaultPatterns() {
        assertNull(ddlReader.getDefaultCatalogPattern());
        assertNull(ddlReader.getDefaultSchemaPattern());
        assertNull(ddlReader.getDefaultTablePattern());
    }

    @Test
    void testGetName_translatesMappedColumnName() {
        assertEquals("TABLENAME", ddlReader.getName("TABLE_NAME"));
        assertEquals("FIELD", ddlReader.getName("COLUMN_NAME"));
        assertEquals("FOREIGNKEYNAME", ddlReader.getName("FK_NAME"));
    }

    @Test
    void testGetName_unmappedName_fallsBackToDefault() {
        assertEquals("SOME_UNMAPPED_NAME", ddlReader.getName("SOME_UNMAPPED_NAME"));
    }

    @Test
    void testGetResultSetSchemaName_returnsDefaultFromSuper() {
        assertEquals("TABLE_SCHEM", ddlReader.getResultSetSchemaName());
    }

    @Test
    void testReadColumn_withEmptyDefaultValue_setsNullDefaultValue() throws SQLException {
        Map<String, Object> values = baseColumnValues("VARCHAR", "");
        Column column = ddlReader.readColumn(null, values);
        assertNull(column.getDefaultValue());
    }

    @Test
    void testReadColumn_withNonEmptyDefaultValue_keepsTrimmedDefaultValue() throws SQLException {
        Map<String, Object> values = baseColumnValues("VARCHAR", " abc ");
        Column column = ddlReader.readColumn(null, values);
        assertEquals("abc", column.getDefaultValue());
    }

    @Test
    void testReadColumn_withEnumType_parsesEnumValues() throws SQLException {
        Map<String, Object> values = baseColumnValues("ENUM", null);
        values.put("SCHEMA", "MY_SCHEMA");
        values.put("TABLENAME", "MY_TABLE");
        ISqlTemplate template = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(template);
        when(template.queryForString(anyString(), any(), any(), any())).thenReturn("A^B");
        Column column = ddlReader.readColumn(null, values);
        assertEquals(Types.VARCHAR, column.getMappedTypeCode());
        assertEquals("VARCHAR", column.getMappedType());
        assertArrayEquals(new String[] { "A", "B" }, column.getPlatformColumns().get(DatabaseNamesConstants.NUODB).getEnumValues());
    }

    @Test
    void testReadColumn_withEnumTypeAndNoUnparsedEnums_leavesEnumValuesUnset() throws SQLException {
        Map<String, Object> values = baseColumnValues("ENUM", null);
        values.put("SCHEMA", "MY_SCHEMA");
        values.put("TABLENAME", "MY_TABLE");
        ISqlTemplate template = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(template);
        when(template.queryForString(anyString(), any(), any(), any())).thenReturn(null);
        Column column = ddlReader.readColumn(null, values);
        assertNull(column.getPlatformColumns().get(DatabaseNamesConstants.NUODB).getEnumValues());
    }

    @Test
    void testIsInternalPrimaryKeyIndex_matchesNamingConvention() {
        Table table = new Table("MY_TABLE");
        NonUniqueIndex index = new NonUniqueIndex("MY_TABLE..PRIMARY_KEY");
        assertTrue(ddlReader.isInternalPrimaryKeyIndex(null, null, table, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_withUnrelatedIndexName_returnsFalse() {
        Table table = new Table("MY_TABLE");
        NonUniqueIndex index = new NonUniqueIndex("IDX_OTHER");
        assertFalse(ddlReader.isInternalPrimaryKeyIndex(null, null, table, index));
    }

    @Test
    void testIsInternalForeignKeyIndex_matchesForeignKeyName() {
        Table table = new Table("CHILD");
        ForeignKey fk = new ForeignKey("FK_CHILD_PARENT");
        NonUniqueIndex index = new NonUniqueIndex("FK_CHILD_PARENT");
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(ddlBuilder.getForeignKeyName(table, fk)).thenReturn("FK_CHILD_PARENT");
        assertTrue(ddlReader.isInternalForeignKeyIndex(null, null, table, fk, index));
    }

    @Test
    void testIsInternalForeignKeyIndex_withUnrelatedIndexName_returnsFalse() {
        Table table = new Table("CHILD");
        ForeignKey fk = new ForeignKey("FK_CHILD_PARENT");
        NonUniqueIndex index = new NonUniqueIndex("IDX_OTHER");
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(ddlBuilder.getForeignKeyName(table, fk)).thenReturn("FK_CHILD_PARENT");
        assertFalse(ddlReader.isInternalForeignKeyIndex(null, null, table, fk, index));
    }

    @Test
    void testReadForeignKeys_buildsForeignKeyFromResultSet() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(1)).thenReturn("ID");
        when(rs.getString(2)).thenReturn("PARENT");
        when(rs.getString(3)).thenReturn("PARENT_ID");
        when(rs.getInt(4)).thenReturn(1);
        when(rs.getString(5)).thenReturn("FK_CHILD_PARENT");
        Collection<ForeignKey> fks = ddlReader.readForeignKeys(connection, null, "CHILD");
        assertEquals(1, fks.size());
        ForeignKey fk = fks.iterator().next();
        assertEquals("FK_CHILD_PARENT", fk.getName());
        assertEquals("PARENT", fk.getForeignTableName());
        assertEquals(1, fk.getReferenceCount());
        Reference ref = fk.getFirstReference();
        assertEquals("PARENT_ID", ref.getLocalColumnName());
        assertEquals("ID", ref.getForeignColumnName());
        assertEquals(1, ref.getSequenceValue());
        verify(ps).setString(1, "CHILD");
    }

    @Test
    void testGetTriggers_queriesSystemTriggersTableWithTableAndSchema() {
        JdbcSqlTemplate sqlTemplate = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class))).thenReturn(new ArrayList<Trigger>());
        List<Trigger> triggers = ddlReader.getTriggers("MY_CATALOG", "MY_SCHEMA", "MY_TABLE");
        assertTrue(triggers.isEmpty());
        verify(sqlTemplate).query(
                eq("SELECT TRIGGERNAME, SCHEMA, TRIGGER_TYPE, TABLENAME, TRIG.* FROM SYSTEM.TRIGGERS AS TRIG WHERE TABLENAME=? and SCHEMA=? ;"),
                any(ISqlRowMapper.class), eq("MY_TABLE"), eq("MY_CATALOG"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetTriggers_mapRow_setsRecognizedTriggerType() {
        JdbcSqlTemplate sqlTemplate = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ArgumentCaptor<ISqlRowMapper<Trigger>> captor = ArgumentCaptor.forClass(ISqlRowMapper.class);
        when(sqlTemplate.query(anyString(), captor.capture(), any(Object[].class))).thenReturn(new ArrayList<Trigger>());
        ddlReader.getTriggers("MY_CATALOG", "MY_SCHEMA", "MY_TABLE");
        Row row = mock(Row.class);
        when(row.getString("TRIGGERNAME")).thenReturn("TRIG1");
        when(row.getString("SCHEMA")).thenReturn("MY_SCHEMA");
        when(row.getString("TABLENAME")).thenReturn("MY_TABLE");
        when(row.getString("TRIGGER_TYPE")).thenReturn("INSERT");
        Trigger trigger = captor.getValue().mapRow(row);
        assertEquals("TRIG1", trigger.getName());
        assertEquals("MY_SCHEMA", trigger.getSchemaName());
        assertEquals("MY_TABLE", trigger.getTableName());
        assertTrue(trigger.isEnabled());
        assertEquals(Trigger.TriggerType.INSERT, trigger.getTriggerType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetTriggers_mapRow_unrecognizedTriggerType_leavesTriggerTypeUnset() {
        JdbcSqlTemplate sqlTemplate = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ArgumentCaptor<ISqlRowMapper<Trigger>> captor = ArgumentCaptor.forClass(ISqlRowMapper.class);
        when(sqlTemplate.query(anyString(), captor.capture(), any(Object[].class))).thenReturn(new ArrayList<Trigger>());
        ddlReader.getTriggers("MY_CATALOG", "MY_SCHEMA", "MY_TABLE");
        Row row = mock(Row.class);
        when(row.getString("TRIGGERNAME")).thenReturn("TRIG1");
        when(row.getString("SCHEMA")).thenReturn("MY_SCHEMA");
        when(row.getString("TABLENAME")).thenReturn("MY_TABLE");
        when(row.getString("TRIGGER_TYPE")).thenReturn("TRUNCATE");
        Trigger trigger = captor.getValue().mapRow(row);
        assertNull(trigger.getTriggerType());
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_clobType_mapsToLongVarchar() {
        Map<String, Object> values = new HashMap<>();
        values.put("DATA_TYPE", Types.CLOB);
        assertEquals(Types.LONGVARCHAR, ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_nonClobType_returnsNull() {
        Map<String, Object> values = new HashMap<>();
        values.put("DATA_TYPE", Types.VARCHAR);
        assertNull(ddlReader.mapUnknownJdbcTypeForColumn(values));
    }

    @Test
    void testReadForeignKeyUpdateRule_alwaysSetsNoAction() {
        ForeignKey fk = new ForeignKey("FK_TEST");
        ddlReader.readForeignKeyUpdateRule(new HashMap<>(), fk);
        assertEquals(ForeignKeyAction.NOACTION, fk.getOnUpdateAction());
    }

    @Test
    void testReadForeignKeyDeleteRule_alwaysSetsNoAction() {
        ForeignKey fk = new ForeignKey("FK_TEST");
        ddlReader.readForeignKeyDeleteRule(new HashMap<>(), fk);
        assertEquals(ForeignKeyAction.NOACTION, fk.getOnDeleteAction());
    }

    private Map<String, Object> baseColumnValues(String typeName, String defaultValue) {
        Map<String, Object> values = new HashMap<>();
        values.put("FIELD", "STATUS");
        values.put("TYPE_NAME", typeName);
        values.put("DATA_TYPE", Types.VARCHAR);
        values.put("NUM_PREC_RADIX", 10);
        values.put("SCALE", 0);
        values.put("LENGTH", "10");
        values.put("IS_NULLABLE", "YES");
        values.put("COLUMN_DEF", defaultValue);
        return values;
    }
}
