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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlAnywhereDmlStatementTest {
    private static final String TABLE_NAME = "item";
    private SqlAnywhereDdlBuilder ddlBuilder;

    @BeforeEach
    void setUp() {
        ddlBuilder = new SqlAnywhereDdlBuilder();
    }

    @Test
    void testGetTypeCode_withDateOverriddenToTimestamp() {
        assertEquals(Types.TIMESTAMP, newStatement().getTypeCode(new Column("d", false, Types.DATE, 0, 0), true));
    }

    @Test
    void testGetTypeCode_withoutDateOverride() {
        assertEquals(Types.DATE, newStatement().getTypeCode(new Column("d", false, Types.DATE, 0, 0), false));
    }

    @Test
    void testGetTypeCode_leavesOtherTypesAlone() {
        SqlAnywhereDmlStatement statement = newStatement();
        assertEquals(Types.VARCHAR, statement.getTypeCode(new Column("s", false, Types.VARCHAR, 50, 0), true));
        assertEquals(Types.TIMESTAMP, statement.getTypeCode(new Column("t", false, Types.TIMESTAMP, 0, 0), true));
        assertEquals(Types.INTEGER, statement.getTypeCode(new Column("i", false, Types.INTEGER, 0, 0), true));
    }

    @Test
    void testGetSql_forInsert() {
        String sql = newStatement(DmlType.INSERT, new Column("name", false, Types.VARCHAR, 100, 0)).getSql();
        assertTrue(sql.startsWith("insert into " + TABLE_NAME), sql);
        assertTrue(sql.contains("name"), sql);
    }

    @Test
    void testGetSql_forUpdate() {
        String sql = newStatement(DmlType.UPDATE, new Column("name", false, Types.VARCHAR, 100, 0)).getSql();
        assertTrue(sql.startsWith("update " + TABLE_NAME), sql);
        assertTrue(sql.contains("where"), sql);
    }

    @Test
    void testGetSql_forDelete() {
        assertTrue(newStatement(DmlType.DELETE).getSql().startsWith("delete from " + TABLE_NAME));
    }

    @Test
    void testGetTypes_forDateColumnUsesTimestamp() {
        SqlAnywhereDmlStatement statement = newStatement(DmlType.INSERT, new Column("d", false, Types.DATE, 0, 0));
        assertEquals(Types.TIMESTAMP, statement.getTypes()[statement.getTypes().length - 1]);
    }

    private SqlAnywhereDmlStatement newStatement() {
        return newStatement(DmlType.INSERT, new Column("name", false, Types.VARCHAR, 100, 0));
    }

    private SqlAnywhereDmlStatement newStatement(DmlType dmlType, Column... columns) {
        Column key = new Column("id", true, Types.INTEGER, 0, 0);
        Column[] allColumns = new Column[columns.length + 1];
        allColumns[0] = key;
        System.arraycopy(columns, 0, allColumns, 1, columns.length);
        DmlStatementOptions options = new DmlStatementOptions(dmlType, TABLE_NAME)
                .databaseInfo(ddlBuilder.getDatabaseInfo()).keys(new Column[] { key }).columns(allColumns);
        return new SqlAnywhereDmlStatement(options);
    }
}
