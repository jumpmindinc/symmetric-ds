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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.junit.jupiter.api.Test;

class RedshiftDmlStatementTest {
    @Test
    void testBuildInsertSql_withKeyColumns_buildsMergeStyleInsert() {
        RedshiftDmlStatement statement = newStatement(DmlType.INSERT, tableWithPrimaryKey());
        assertEquals("insert into item(id, name) (select ?,? where (select count(*) from item where  id = ?) = 0)",
                statement.getSql());
    }

    @Test
    void testBuildInsertSql_withoutKeyColumns_fallsBackToPlainInsert() {
        RedshiftDmlStatement statement = newStatement(DmlType.INSERT, tableWithoutPrimaryKey());
        assertEquals("insert into item (id, name) values (?,?)", statement.getSql());
    }

    @Test
    void testGetMetaData_forInsert_returnsColumnsAndKeysCombined() {
        RedshiftDmlStatement statement = newStatement(DmlType.INSERT, tableWithPrimaryKey());
        Column[] metaData = statement.getMetaData();
        assertEquals(3, metaData.length);
        assertEquals("id", metaData[0].getName());
        assertEquals("name", metaData[1].getName());
        assertEquals("id", metaData[2].getName());
    }

    @Test
    void testGetMetaData_forDelete_delegatesToSuper() {
        RedshiftDmlStatement statement = newStatement(DmlType.DELETE, tableWithPrimaryKey());
        Column[] metaData = statement.getMetaData();
        assertEquals(1, metaData.length);
        assertEquals("id", metaData[0].getName());
    }

    @Test
    void testGetValueArray_forInsert_concatenatesColumnAndKeyValues() {
        RedshiftDmlStatement statement = newStatement(DmlType.INSERT, tableWithPrimaryKey());
        String[] result = statement.getValueArray(new String[] { "a", "b" }, new String[] { "k" });
        assertArrayEquals(new String[] { "a", "b", "k" }, result);
    }

    @Test
    void testGetValueArray_forDelete_delegatesToSuper() {
        RedshiftDmlStatement statement = newStatement(DmlType.DELETE, tableWithPrimaryKey());
        String[] result = statement.getValueArray(new String[] { "a", "b" }, new String[] { "k" });
        assertArrayEquals(new String[] { "k" }, result);
    }

    @Test
    void testGetValueArrayFromParams_forInsert_appendsKeyValuesAfterColumnValues() {
        RedshiftDmlStatement statement = newStatement(DmlType.INSERT, tableWithPrimaryKey());
        Map<String, Object> params = new HashMap<>();
        params.put("id", 1);
        params.put("name", "widget");
        Object[] args = statement.getValueArray(params);
        assertArrayEquals(new Object[] { 1, "widget", 1 }, args);
    }

    @Test
    void testGetValueArrayFromParams_forDelete_delegatesToSuper() {
        RedshiftDmlStatement statement = newStatement(DmlType.DELETE, tableWithPrimaryKey());
        Map<String, Object> params = new HashMap<>();
        params.put("id", 1);
        params.put("name", "widget");
        Object[] args = statement.getValueArray(params);
        assertArrayEquals(new Object[] { 1 }, args);
    }

    @Test
    void testGetValueArrayFromParams_withNullParams_returnsNull() {
        RedshiftDmlStatement statement = newStatement(DmlType.INSERT, tableWithPrimaryKey());
        assertNull(statement.getValueArray((Map<String, Object>) null));
    }

    @Test
    void testGetTypes_forInsert_includesKeyColumnTypesAfterColumnTypes() {
        RedshiftDmlStatement statement = newStatement(DmlType.INSERT, tableWithPrimaryKey());
        assertArrayEquals(new int[] { Types.INTEGER, Types.VARCHAR, Types.INTEGER }, statement.getTypes());
    }

    private RedshiftDmlStatement newStatement(DmlType dmlType, Table table) {
        DmlStatementOptions options = new DmlStatementOptions(dmlType, table)
                .databaseInfo(new RedshiftDdlBuilder().getDatabaseInfo());
        return new RedshiftDmlStatement(options);
    }

    private Table tableWithPrimaryKey() {
        Column id = new Column("id", true, Types.INTEGER, 10, 0);
        Column name = new Column("name", false, Types.VARCHAR, 50, 0);
        return new Table("item", id, name);
    }

    private Table tableWithoutPrimaryKey() {
        Column id = new Column("id", false, Types.INTEGER, 10, 0);
        Column name = new Column("name", false, Types.VARCHAR, 50, 0);
        return new Table("item", id, name);
    }
}
