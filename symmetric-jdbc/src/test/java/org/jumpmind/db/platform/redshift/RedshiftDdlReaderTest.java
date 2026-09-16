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
package org.jumpmind.db.platform.redshift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.model.UniqueIndex;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedshiftDdlReaderTest {
    private static final String TABLE_NAME = "item";
    private static final String SCHEMA_NAME = "public";
    private IDatabasePlatform platformMock;
    private JdbcSqlTemplate sqlTemplateMock;
    private RedshiftDdlReader ddlReader;

    @BeforeEach
    void setUp() {
        platformMock = mock(IDatabasePlatform.class);
        sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platformMock.getSqlTemplate()).thenReturn(sqlTemplateMock);
        when(platformMock.getName()).thenReturn("redshift");
        ddlReader = new RedshiftDdlReader(platformMock);
    }

    @Test
    void testConstructor_clearsDefaultPatterns() {
        assertNull(ddlReader.getDefaultCatalogPattern());
        assertNull(ddlReader.getDefaultSchemaPattern());
        assertNull(ddlReader.getDefaultTablePattern());
    }

    @Test
    void testGetName_mapsIsNullableToNullable() {
        assertEquals("NULLABLE", ddlReader.getName("IS_NULLABLE"));
    }

    @Test
    void testGetName_returnsUnmappedNameUnchanged() {
        assertEquals("COLUMN_NAME", ddlReader.getName("COLUMN_NAME"));
    }

    @Test
    void testReadColumn_varcharAtMaxSize_convertsToLongVarchar() throws Exception {
        Column column = readColumn(columnValues("VARCHAR", Types.VARCHAR, "65535", null));
        assertEquals(Types.LONGVARCHAR, column.getJdbcTypeCode());
        assertEquals(Types.LONGVARCHAR, column.getMappedTypeCode());
        assertNull(column.getSize());
    }

    @Test
    void testReadColumn_varcharBelowMaxSize_isNotConverted() throws Exception {
        Column column = readColumn(columnValues("VARCHAR", Types.VARCHAR, "50", null));
        assertEquals(Types.VARCHAR, column.getJdbcTypeCode());
    }

    @Test
    void testReadColumn_integerAtMaxVarcharSize_isNotConverted() throws Exception {
        Column column = readColumn(columnValues("INTEGER", Types.INTEGER, "65535", null));
        assertEquals(Types.INTEGER, column.getJdbcTypeCode());
    }

    @Test
    void testReadColumn_identityDefaultValue_marksAutoIncrement() throws Exception {
        Column column = readColumn(columnValues("INTEGER", Types.INTEGER, null,
                "\"identity\"(102643, 0, '1,1'::text)"));
        assertTrue(column.isAutoIncrement());
        assertNull(column.getDefaultValue());
    }

    @Test
    void testReadColumn_undelimitedDefaultValue_forBigint() throws Exception {
        Column column = readColumn(columnValues("BIGINT", Types.BIGINT, null, "5::bigint"));
        assertEquals("5", column.getDefaultValue());
        assertFalse(column.isAutoIncrement());
    }

    @Test
    void testReadColumn_undelimitedSequenceDefaultValue_reattachesClosingParen() throws Exception {
        Column column = readColumn(columnValues("INTEGER", Types.INTEGER, null,
                "nextval('\"sym_data_data_id_seq\"'::text)"));
        assertEquals("nextval('\"sym_data_data_id_seq\"')", column.getDefaultValue());
    }

    @Test
    void testReadColumn_undelimitedParenthesizedDefaultValue_stripsParens() throws Exception {
        Column column = readColumn(columnValues("DECIMAL", Types.DECIMAL, null, "(-9000000000000000000)"));
        assertEquals("-9000000000000000000", column.getDefaultValue());
    }

    @Test
    void testReadColumn_delimitedDefaultValue_forVarchar() throws Exception {
        Column column = readColumn(columnValues("VARCHAR", Types.VARCHAR, "50", "'test'::character varying"));
        assertEquals("test", column.getDefaultValue());
    }

    @Test
    void testReadColumn_delimitedDefaultValueWithEscapedQuote_isUnescaped() throws Exception {
        Column column = readColumn(columnValues("VARCHAR", Types.VARCHAR, "50", "'it''s'::character varying"));
        assertEquals("it's", column.getDefaultValue());
    }

    @Test
    void testReadColumn_delimitedDefaultValue_forDate() throws Exception {
        Column column = readColumn(columnValues("DATE", Types.DATE, null, "'2000-01-01'::date"));
        assertEquals("2000-01-01", column.getDefaultValue());
    }

    @Test
    void testReadColumn_withNoDefaultValue_leavesDefaultValueNull() throws Exception {
        Column column = readColumn(columnValues("VARCHAR", Types.VARCHAR, "50", null));
        assertNull(column.getDefaultValue());
    }

    @Test
    void testIsInternalPrimaryKeyIndex_whenIndexCoversOnlyPrimaryKey() {
        Table table = newTable();
        IIndex index = new UniqueIndex("item_pkey");
        index.addColumn(new IndexColumn("id"));
        assertTrue(ddlReader.isInternalPrimaryKeyIndex(null, null, table, index));
    }

    @Test
    void testIsInternalPrimaryKeyIndex_whenIndexCoversOtherColumns() {
        Table table = newTable();
        IIndex index = new NonUniqueIndex("item_name_idx");
        index.addColumn(new IndexColumn("name"));
        assertFalse(ddlReader.isInternalPrimaryKeyIndex(null, null, table, index));
    }

    @Test
    void testGetTriggers_mapsTriggerFields() {
        stubTriggerQuery(triggerRow("INSERT"));
        List<Trigger> triggers = ddlReader.getTriggers("shop", SCHEMA_NAME, TABLE_NAME);
        assertEquals(1, triggers.size());
        Trigger trigger = triggers.get(0);
        assertEquals("item_insert_trigger", trigger.getName());
        assertEquals("shop", trigger.getCatalogName());
        assertEquals(SCHEMA_NAME, trigger.getSchemaName());
        assertEquals(TABLE_NAME, trigger.getTableName());
        assertTrue(trigger.isEnabled());
        assertEquals("begin end", trigger.getSource());
        assertEquals(TriggerType.INSERT, trigger.getTriggerType());
    }

    @Test
    void testGetTriggers_removesSourceFromMetaData() {
        stubTriggerQuery(triggerRow("UPDATE"));
        Trigger trigger = ddlReader.getTriggers("shop", SCHEMA_NAME, TABLE_NAME).get(0);
        assertFalse(((Row) trigger.getMetaData()).containsKey("prosrc"));
    }

    @Test
    void testGetTriggers_forDeleteType() {
        stubTriggerQuery(triggerRow("DELETE"));
        assertEquals(TriggerType.DELETE, ddlReader.getTriggers("shop", SCHEMA_NAME, TABLE_NAME).get(0).getTriggerType());
    }

    @Test
    void testGetTriggers_withUnsupportedTriggerType_leavesTypeUnset() {
        stubTriggerQuery(triggerRow("TRUNCATE"));
        assertNull(ddlReader.getTriggers("shop", SCHEMA_NAME, TABLE_NAME).get(0).getTriggerType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetTriggers_whenNoneExist() {
        when(sqlTemplateMock.query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME)))
                .thenReturn(Arrays.asList());
        assertTrue(ddlReader.getTriggers("shop", SCHEMA_NAME, TABLE_NAME).isEmpty());
    }

    @Test
    void testReadForeignKeyUpdateRule_isAlwaysNoAction() {
        ForeignKey foreignKey = new ForeignKey("fk_item_order");
        foreignKey.setOnUpdateAction(ForeignKeyAction.CASCADE);
        ddlReader.readForeignKeyUpdateRule(new HashMap<String, Object>(), foreignKey);
        assertEquals(ForeignKeyAction.NOACTION, foreignKey.getOnUpdateAction());
    }

    @Test
    void testReadForeignKeyDeleteRule_isAlwaysNoAction() {
        ForeignKey foreignKey = new ForeignKey("fk_item_order");
        foreignKey.setOnDeleteAction(ForeignKeyAction.CASCADE);
        ddlReader.readForeignKeyDeleteRule(new HashMap<String, Object>(), foreignKey);
        assertEquals(ForeignKeyAction.NOACTION, foreignKey.getOnDeleteAction());
    }

    private Column readColumn(Map<String, Object> values) throws Exception {
        return ddlReader.readColumn(null, values);
    }

    private Map<String, Object> columnValues(String typeName, int dataType, String columnSize, String columnDef) {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("COLUMN_NAME", "mycol");
        values.put("TYPE_NAME", typeName);
        values.put("DATA_TYPE", Integer.valueOf(dataType));
        values.put("NUM_PREC_RADIX", Integer.valueOf(10));
        values.put("COLUMN_SIZE", columnSize);
        values.put("DECIMAL_DIGITS", Integer.valueOf(0));
        values.put("COLUMN_DEF", columnDef);
        values.put("NULLABLE", "YES");
        return values;
    }

    @SuppressWarnings("unchecked")
    private void stubTriggerQuery(Row row) {
        when(sqlTemplateMock.query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME)))
                .thenAnswer(invocation -> {
                    ISqlRowMapper<Trigger> mapper = invocation.getArgument(1);
                    return Arrays.asList(mapper.mapRow(row));
                });
    }

    private Row triggerRow(String triggerType) {
        Row row = new Row(6);
        row.put("trigger_name", "item_insert_trigger");
        row.put("trigger_catalog", "shop");
        row.put("trigger_schema", SCHEMA_NAME);
        row.put("table_name", TABLE_NAME);
        row.put("prosrc", "begin end");
        row.put("trigger_type", triggerType);
        return row;
    }

    private Table newTable() {
        Column id = new Column("id", true, Types.INTEGER, 0, 0);
        Column name = new Column("name", false, Types.VARCHAR, 255, 0);
        return new Table(TABLE_NAME, id, name);
    }
}
