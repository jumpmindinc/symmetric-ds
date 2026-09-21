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
package org.jumpmind.db.platform.sqlanywhere;

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
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlAnywhereDdlReaderTest {
    private static final String TABLE_NAME = "item";
    private static final String SCHEMA_NAME = "dba";
    private IDatabasePlatform platformMock;
    private JdbcSqlTemplate sqlTemplateMock;
    private SqlAnywhereDdlReader ddlReader;

    @BeforeEach
    void setUp() {
        platformMock = mock(IDatabasePlatform.class);
        sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platformMock.getSqlTemplate()).thenReturn(sqlTemplateMock);
        ddlReader = new SqlAnywhereDdlReader(platformMock);
    }

    @Test
    void testConstructor_setsDefaultPatterns() {
        assertNull(ddlReader.getDefaultCatalogPattern());
        assertNull(ddlReader.getDefaultSchemaPattern());
        assertEquals("%", ddlReader.getDefaultTablePattern());
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_withTextType() {
        assertEquals(Integer.valueOf(Types.LONGVARCHAR), ddlReader.mapUnknownJdbcTypeForColumn(values("TEXT")));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_withNTextType() {
        assertEquals(Integer.valueOf(Types.LONGNVARCHAR), ddlReader.mapUnknownJdbcTypeForColumn(values("NTEXT")));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_withLongNVarcharType() {
        assertEquals(Integer.valueOf(Types.LONGNVARCHAR), ddlReader.mapUnknownJdbcTypeForColumn(values("LONG NVARCHAR")));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_ignoresCase() {
        assertEquals(Integer.valueOf(Types.LONGVARCHAR), ddlReader.mapUnknownJdbcTypeForColumn(values("text")));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_withNullTypeName() {
        assertNull(ddlReader.mapUnknownJdbcTypeForColumn(values(null)));
    }

    @Test
    void testMapUnknownJdbcTypeForColumn_withUnmappedTypeName() {
        assertNull(ddlReader.mapUnknownJdbcTypeForColumn(values("GEOMETRY")));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_whenSysindexesFlagIsSet() throws Exception {
        ResultSet resultSetMock = mock(ResultSet.class);
        when(resultSetMock.next()).thenReturn(Boolean.TRUE);
        Statement statementMock = mock(Statement.class);
        when(statementMock.executeQuery(anyString())).thenReturn(resultSetMock);
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.createStatement()).thenReturn(statementMock);
        assertTrue(ddlReader.isInternalPrimaryKeyIndex(connectionMock, null, new Table(TABLE_NAME), new NonUniqueIndex("pk_item")));
        verify(statementMock).close();
        verify(resultSetMock).close();
    }

    @Test
    void testIsInternalPrimaryKeyIndex_whenNoRowMatches() throws Exception {
        ResultSet resultSetMock = mock(ResultSet.class);
        when(resultSetMock.next()).thenReturn(Boolean.FALSE);
        Statement statementMock = mock(Statement.class);
        when(statementMock.executeQuery(anyString())).thenReturn(resultSetMock);
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.createStatement()).thenReturn(statementMock);
        assertFalse(ddlReader.isInternalPrimaryKeyIndex(connectionMock, null, new Table(TABLE_NAME), new NonUniqueIndex("idx_item")));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_queriesSysindexesStatusFlag() throws Exception {
        ResultSet resultSetMock = mock(ResultSet.class);
        Statement statementMock = mock(Statement.class);
        when(statementMock.executeQuery(anyString())).thenReturn(resultSetMock);
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.createStatement()).thenReturn(statementMock);
        ddlReader.isInternalPrimaryKeyIndex(connectionMock, null, new Table(TABLE_NAME), new NonUniqueIndex("pk_item"));
        verify(statementMock).executeQuery(
                "SELECT name = si.name FROM dbo.sysindexes si, dbo.sysobjects so WHERE so.name = 'item' AND si.name = 'pk_item' AND so.id = si.id AND (si.status & 2048) > 0");
    }

    @Test
    void testGetSchemasHandleException_queriesSpTables() throws Exception {
        ResultSet resultSetMock = mock(ResultSet.class);
        Statement statementMock = mock(Statement.class);
        when(statementMock.executeQuery(anyString())).thenReturn(resultSetMock);
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.createStatement()).thenReturn(statementMock);
        assertEquals(resultSetMock, ddlReader.getSchemasHandleException(connectionMock, null, "cat", "%"));
        verify(statementMock).executeQuery("select distinct table_owner as TABLE_SCHEM, table_qualifier as TABLE_CATALOG from sp_tables()");
    }

    @Test
    void testGetTriggers_mapsTriggerFields() {
        Trigger trigger = singleTrigger(newTriggerRow("INSERT"));
        assertEquals("trg_item_insert", trigger.getName());
        assertEquals(SCHEMA_NAME, trigger.getSchemaName());
        assertEquals(TABLE_NAME, trigger.getTableName());
        assertEquals(TriggerType.INSERT, trigger.getTriggerType());
        assertEquals("create trigger trg_item_insert", trigger.getSource());
        assertTrue(trigger.isEnabled());
    }

    @Test
    void testGetTriggers_removesSourceFromMetaData() {
        assertFalse(singleTrigger(newTriggerRow("INSERT")).getMetaData().containsKey("trigdefn"));
    }

    @Test
    void testGetTriggers_mapsUpdateAndDeleteTypes() {
        assertEquals(TriggerType.UPDATE, singleTrigger(newTriggerRow("UPDATE")).getTriggerType());
        assertEquals(TriggerType.DELETE, singleTrigger(newTriggerRow("DELETE")).getTriggerType());
    }

    @Test
    void testGetTriggers_withUnsupportedTriggerType_leavesTypeUnset() {
        assertNull(singleTrigger(newTriggerRow("TRUNCATE")).getTriggerType());
    }

    @Test
    void testGetTriggers_withNoRows() {
        when(sqlTemplateMock.query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME)))
                .thenReturn(Arrays.<Trigger> asList());
        assertTrue(ddlReader.getTriggers(null, SCHEMA_NAME, TABLE_NAME).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Trigger singleTrigger(Row row) {
        when(sqlTemplateMock.query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME)))
                .thenAnswer(invocation -> {
                    ISqlRowMapper<Trigger> mapper = invocation.getArgument(1);
                    return Arrays.asList(mapper.mapRow(row));
                });
        List<Trigger> triggers = ddlReader.getTriggers(null, SCHEMA_NAME, TABLE_NAME);
        assertEquals(1, triggers.size());
        return triggers.get(0);
    }

    private Row newTriggerRow(String triggerType) {
        Row row = new Row(6);
        row.put("trigger_name", "trg_item_insert");
        row.put("owner", SCHEMA_NAME);
        row.put("table_name", TABLE_NAME);
        row.put("trigger_type", triggerType);
        row.put("trigger_time", "AFTER");
        row.put("trigdefn", "create trigger trg_item_insert");
        return row;
    }

    private Map<String, Object> values(String typeName) {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("TYPE_NAME", typeName);
        return values;
    }
}
