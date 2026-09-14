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
package org.jumpmind.db.platform.greenplum;

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
import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
import org.jumpmind.db.platform.postgresql.PostgreSqlDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlDdlBuilder;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GreenplumDdlReaderTest {
    private static final String TABLE_NAME = "item";
    private static final String SCHEMA_NAME = "public";
    private IDatabasePlatform platformMock;
    private JdbcSqlTemplate sqlTemplateMock;
    private GreenplumDdlReader ddlReader;

    @BeforeEach
    void setUp() {
        platformMock = mock(PostgreSqlDatabasePlatform.class);
        sqlTemplateMock = mock(JdbcSqlTemplate.class);
        when(platformMock.getSqlTemplate()).thenReturn(sqlTemplateMock);
        when(platformMock.getDdlBuilder()).thenReturn(new PostgreSqlDdlBuilder());
        ddlReader = new GreenplumDdlReader(platformMock);
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
    void testSetDistributionKeys_marksMatchingColumn() throws Exception {
        Table table = newTable();
        ddlReader.setDistributionKeys(newConnection("id"), table, SCHEMA_NAME);
        assertTrue(table.getColumnWithName("id").isDistributionKey());
        assertFalse(table.getColumnWithName("name").isDistributionKey());
    }

    @Test
    void testSetDistributionKeys_trimsColumnName() throws Exception {
        Table table = newTable();
        ddlReader.setDistributionKeys(newConnection("  name  "), table, SCHEMA_NAME);
        assertTrue(table.getColumnWithName("name").isDistributionKey());
    }

    @Test
    void testSetDistributionKeys_withUnknownColumn() throws Exception {
        Table table = newTable();
        ddlReader.setDistributionKeys(newConnection("absent"), table, SCHEMA_NAME);
        assertFalse(table.getColumnWithName("id").isDistributionKey());
    }

    @Test
    void testSetDistributionKeys_passesSchemaAndTableParameters() throws Exception {
        PreparedStatement statementMock = newStatement("id");
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(statementMock);
        ddlReader.setDistributionKeys(connectionMock, newTable(), SCHEMA_NAME);
        verify(statementMock).setString(1, SCHEMA_NAME);
        verify(statementMock).setString(2, TABLE_NAME);
        verify(statementMock).close();
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

    @Test
    void testGetTriggers_whenNoneExist() {
        when(sqlTemplateMock.query(anyString(), any(ISqlRowMapper.class), eq(TABLE_NAME), eq(SCHEMA_NAME)))
                .thenReturn(Arrays.asList());
        assertTrue(ddlReader.getTriggers("shop", SCHEMA_NAME, TABLE_NAME).isEmpty());
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

    private Connection newConnection(String distributionColumnName) throws Exception {
        PreparedStatement statementMock = newStatement(distributionColumnName);
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(statementMock);
        return connectionMock;
    }

    private PreparedStatement newStatement(String distributionColumnName) throws Exception {
        ResultSet resultSetMock = mock(ResultSet.class);
        when(resultSetMock.next()).thenReturn(true, false);
        when(resultSetMock.getString(2)).thenReturn(distributionColumnName);
        PreparedStatement statementMock = mock(PreparedStatement.class);
        when(statementMock.executeQuery()).thenReturn(resultSetMock);
        return statementMock;
    }

    private Table newTable() {
        Column id = new Column("id", true, Types.INTEGER, 0, 0);
        Column name = new Column("name", false, Types.VARCHAR, 255, 0);
        return new Table(TABLE_NAME, id, name);
    }
}
