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
package org.jumpmind.db.platform.hsqldb;

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
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HsqlDbDdlReaderTest {
    private static final String TABLE_NAME = "TEST_TABLE";
    private static final String SCHEMA_NAME = "PUBLIC";
    private static final String CATALOG_NAME = "CATALOG1";
    private static final String COLUMN_NAME = "ID";
    private IDatabasePlatform platform;
    private HsqlDbDdlReader ddlReader;
    private Connection connection;
    private DatabaseMetaDataWrapper metaData;

    @BeforeEach
    void setup() {
        platform = mock(IDatabasePlatform.class);
        ddlReader = new HsqlDbDdlReader(platform);
        connection = mock(Connection.class);
        metaData = new DatabaseMetaDataWrapper();
    }

    @Test
    void testConstructor_clearsDefaultCatalogAndSchemaPatterns() {
        assertNull(ddlReader.getDefaultCatalogPattern());
        assertNull(ddlReader.getDefaultSchemaPattern());
    }

    @Test
    void testReadColumn_textDefaultValue_unescapesDoubledQuotes() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.VARCHAR);
        values.put("TYPE_NAME", "VARCHAR");
        values.put("COLUMN_DEF", "'it''s'");
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("'it's'", column.getDefaultValue());
    }

    @Test
    void testReadColumn_nonTextDefaultValue_leavesValueUnchanged() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.INTEGER);
        values.put("TYPE_NAME", "INTEGER");
        values.put("COLUMN_DEF", "5");
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("5", column.getDefaultValue());
    }

    @Test
    void testReadColumn_timestampType_adjustsColumnSizeDownByTwenty() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.TIMESTAMP);
        values.put("TYPE_NAME", "TIMESTAMP");
        values.put("COLUMN_SIZE", "23");
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("3", column.getSize());
    }

    @Test
    void testReadColumn_timeType_adjustsColumnSizeDownByNine() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.TIME);
        values.put("TYPE_NAME", "TIME");
        values.put("COLUMN_SIZE", "12");
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("3", column.getSize());
    }

    @Test
    void testReadColumn_dateType_removesColumnSize() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.DATE);
        values.put("TYPE_NAME", "DATE");
        Column column = ddlReader.readColumn(metaData, values);
        assertNull(column.getSize());
    }

    @Test
    void testReadColumn_otherType_leavesColumnSizeUnchanged() throws SQLException {
        Map<String, Object> values = baseColumnValues();
        values.put("DATA_TYPE", Types.INTEGER);
        values.put("TYPE_NAME", "INTEGER");
        values.put("COLUMN_SIZE", "50");
        Column column = ddlReader.readColumn(metaData, values);
        assertEquals("50", column.getSize());
    }

    @Test
    void testIsInternalForeignKeyIndex_sysIdxPrefixedName_returnsTrue() {
        ForeignKey fk = new ForeignKey("FK_TEST");
        IIndex index = new NonUniqueIndex("SYS_IDX_10");
        Table table = new Table(TABLE_NAME);
        assertTrue(ddlReader.isInternalForeignKeyIndex(connection, metaData, table, fk, index));
    }

    @Test
    void testIsInternalForeignKeyIndex_otherName_returnsFalse() {
        ForeignKey fk = new ForeignKey("FK_TEST");
        IIndex index = new NonUniqueIndex("FK_TEST");
        Table table = new Table(TABLE_NAME);
        assertFalse(ddlReader.isInternalForeignKeyIndex(connection, metaData, table, fk, index));
    }

    @Test
    void testIsInternalForeignKeyIndex_nullName_returnsFalse() {
        ForeignKey fk = new ForeignKey("FK_TEST");
        IIndex index = new NonUniqueIndex();
        Table table = new Table(TABLE_NAME);
        assertFalse(ddlReader.isInternalForeignKeyIndex(connection, metaData, table, fk, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_sysPkPrefixedName_returnsTrue() {
        IIndex index = new NonUniqueIndex("SYS_PK_10");
        Table table = new Table(TABLE_NAME);
        assertTrue(ddlReader.isInternalPrimaryKeyIndex(connection, metaData, table, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_sysIdxPrefixedName_returnsTrue() {
        IIndex index = new NonUniqueIndex("SYS_IDX_10");
        Table table = new Table(TABLE_NAME);
        assertTrue(ddlReader.isInternalPrimaryKeyIndex(connection, metaData, table, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_otherName_returnsFalse() {
        IIndex index = new NonUniqueIndex("MY_INDEX");
        Table table = new Table(TABLE_NAME);
        assertFalse(ddlReader.isInternalPrimaryKeyIndex(connection, metaData, table, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_nullName_returnsFalse() {
        IIndex index = new NonUniqueIndex();
        Table table = new Table(TABLE_NAME);
        assertFalse(ddlReader.isInternalPrimaryKeyIndex(connection, metaData, table, index));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetTriggers_queriesWithTableNameSchemaAndCatalog() throws Exception {
        JdbcSqlTemplate sqlTemplate = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME), eq(CATALOG_NAME)))
                .thenReturn(new ArrayList<Trigger>());
        List<Trigger> triggers = ddlReader.getTriggers(CATALOG_NAME, SCHEMA_NAME, TABLE_NAME);
        assertTrue(triggers.isEmpty());
        verify(sqlTemplate).query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME), eq(CATALOG_NAME));
    }

    @Test
    void testGetTriggers_mapRow_insertTrigger_setsTriggerTypeAndRemovesSql() throws Exception {
        Trigger trigger = mapTriggerRow("INSERT");
        assertEquals(TriggerType.INSERT, trigger.getTriggerType());
        assertTrue(trigger.isEnabled());
        assertEquals("BEGIN END", trigger.getSource());
        assertFalse(trigger.getMetaData().containsKey("SQL"));
    }

    @Test
    void testGetTriggers_mapRow_updateTrigger_setsTriggerType() throws Exception {
        Trigger trigger = mapTriggerRow("UPDATE");
        assertEquals(TriggerType.UPDATE, trigger.getTriggerType());
    }

    @Test
    void testGetTriggers_mapRow_deleteTrigger_setsTriggerType() throws Exception {
        Trigger trigger = mapTriggerRow("DELETE");
        assertEquals(TriggerType.DELETE, trigger.getTriggerType());
    }

    @Test
    void testGetTriggers_mapRow_unrecognizedTriggerType_leavesTriggerTypeNull() throws Exception {
        Trigger trigger = mapTriggerRow("REFRESH");
        assertNull(trigger.getTriggerType());
    }

    @SuppressWarnings("unchecked")
    private Trigger mapTriggerRow(String triggerType) throws Exception {
        JdbcSqlTemplate sqlTemplate = mock(JdbcSqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ArgumentCaptor<ISqlRowMapper<Trigger>> captor = (ArgumentCaptor<ISqlRowMapper<Trigger>>) (ArgumentCaptor<?>) ArgumentCaptor
                .forClass(ISqlRowMapper.class);
        when(sqlTemplate.query(anyString(), captor.capture(), eq(TABLE_NAME), eq(SCHEMA_NAME), eq(CATALOG_NAME)))
                .thenReturn(new ArrayList<Trigger>());
        ddlReader.getTriggers(CATALOG_NAME, SCHEMA_NAME, TABLE_NAME);
        Row row = new Row(
                new String[] { "TRIGGER_NAME", "TRIGGER_CATALOG", "TRIGGER_SCHEMA", "TABLE_NAME", "SQL", "TRIGGER_TYPE" },
                new Object[] { "TRG1", CATALOG_NAME, SCHEMA_NAME, TABLE_NAME, "BEGIN END", triggerType });
        return captor.getValue().mapRow(row);
    }

    private Map<String, Object> baseColumnValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("COLUMN_NAME", COLUMN_NAME);
        values.put("NUM_PREC_RADIX", 10);
        values.put("DECIMAL_DIGITS", 0);
        values.put("COLUMN_SIZE", "50");
        return values;
    }
}
