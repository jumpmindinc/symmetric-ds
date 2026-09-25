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
package org.jumpmind.db.platform.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.junit.jupiter.api.Test;

class SqliteDmlStatementTest {
    @Test
    void testIsUpsertSupported() {
        assertTrue(newStatement(DmlType.UPSERT).isUpsertSupported());
    }

    @Test
    void testBuildUpsertSql_usesInsertOrReplace() {
        assertEquals("insert or replace into item (id, name) values (?,?)", newStatement(DmlType.UPSERT).getSql());
    }

    @Test
    void testBuildUpsertSql_withSingleColumn() {
        Table table = new Table("item");
        table.addColumn(new Column("id", true, Types.INTEGER, 10, 0));
        assertEquals("insert or replace into item (id) values (?)", newStatement(DmlType.UPSERT, table).getSql());
    }

    @Test
    void testBuildInsertSql_isUnchanged() {
        assertEquals("insert into item (id, name) values (?,?)", newStatement(DmlType.INSERT).getSql());
    }

    private SqliteDmlStatement newStatement(DmlType dmlType) {
        Table table = new Table("item");
        table.addColumn(new Column("id", true, Types.INTEGER, 10, 0));
        table.addColumn(new Column("name", false, Types.VARCHAR, 50, 0));
        return newStatement(dmlType, table);
    }

    private SqliteDmlStatement newStatement(DmlType dmlType, Table table) {
        DmlStatementOptions options = new DmlStatementOptions(dmlType, table)
                .databaseInfo(new SqliteDdlBuilder().getDatabaseInfo());
        return new SqliteDmlStatement(options);
    }
}
