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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqliteDdlBuilderTest {
    private SqliteDdlBuilder ddlBuilder;
    private StringBuilder ddl;

    @BeforeEach
    void setUp() {
        ddlBuilder = new SqliteDdlBuilder();
        ddl = new StringBuilder();
    }

    @Test
    void testDatabaseInfo_hasSqliteDefaults() {
        assertTrue(ddlBuilder.getDatabaseInfo().isPrimaryKeyEmbedded());
        assertFalse(ddlBuilder.getDatabaseInfo().isNonPKIdentityColumnsSupported());
        assertFalse(ddlBuilder.getDatabaseInfo().isIdentityOverrideAllowed());
        assertTrue(ddlBuilder.getDatabaseInfo().isRequiresAutoCommitForDdl());
        assertTrue(ddlBuilder.getDatabaseInfo().isForeignKeysSupported());
    }

    @Test
    void testGetIndexName_replacesDashesWithUnderscores() {
        assertEquals("idx_item_name", ddlBuilder.getIndexName(new NonUniqueIndex("idx-item-name")));
    }

    @Test
    void testGetIndexName_leavesNameWithoutDashes() {
        assertEquals("idx_item", ddlBuilder.getIndexName(new NonUniqueIndex("idx_item")));
    }

    @Test
    void testWriteExternalIndexDropStmt() {
        ddlBuilder.writeExternalIndexDropStmt(newTable(), new NonUniqueIndex("idx-item"), ddl);
        assertTrue(ddl.toString().startsWith("DROP INDEX \"idx_item\";"));
    }

    @Test
    void testWriteExternalForeignKeyCreateStmt_writesNothing() {
        ddlBuilder.writeExternalForeignKeyCreateStmt(new Database(), newTable(), new ForeignKey("fk_item"), ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testWriteExternalForeignKeyDropStmt_writesNothing() {
        ddlBuilder.writeExternalForeignKeyDropStmt(newTable(), new ForeignKey("fk_item"), ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testDropTable_usesIfExists() {
        ddlBuilder.dropTable(newTable(), ddl, false, false);
        assertTrue(ddl.toString().startsWith("DROP TABLE IF EXISTS \"item\";"));
    }

    @Test
    void testWriteColumnAutoIncrementStmt() {
        ddlBuilder.writeColumnAutoIncrementStmt(newTable(), new Column("id", true, Types.INTEGER, 10, 0), ddl);
        assertEquals("AUTOINCREMENT", ddl.toString());
    }

    @Test
    void testWriteColumnEmbeddedPrimaryKey_withSingleKeyColumn() {
        Table table = newTable();
        ddlBuilder.writeColumnEmbeddedPrimaryKey(table, table.getColumn(0), ddl);
        assertEquals(" PRIMARY KEY ", ddl.toString());
    }

    @Test
    void testWriteColumnEmbeddedPrimaryKey_withCompositeKey() {
        Table table = newCompositeKeyTable();
        ddlBuilder.writeColumnEmbeddedPrimaryKey(table, table.getColumn(0), ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testMapDefaultValue_withSysdateOnDateColumn() {
        assertEquals("CURRENT_DATE", ddlBuilder.mapDefaultValue("SYSDATE", dateColumn()));
    }

    @Test
    void testMapDefaultValue_withCurrentDateOnDateColumn() {
        assertEquals("CURRENT_DATE", ddlBuilder.mapDefaultValue("current_date", dateColumn()));
    }

    @Test
    void testMapDefaultValue_withSystimestampOnDateColumn() {
        assertEquals("CURRENT_TIMESTAMP", ddlBuilder.mapDefaultValue("SYSTIMESTAMP", dateColumn()));
    }

    @Test
    void testMapDefaultValue_withCurrentTimeOnDateColumn() {
        assertEquals("CURRENT_TIME", ddlBuilder.mapDefaultValue("CURRENT_TIME", dateColumn()));
    }

    @Test
    void testMapDefaultValue_withFunctionCallOnDateColumn() {
        assertEquals("('2020-01-01'", ddlBuilder.mapDefaultValue("to_date('2020-01-01')", dateColumn()));
    }

    @Test
    void testMapDefaultValue_withUnclosedFunctionCallOnDateColumn() {
        assertEquals("('2020-01-01'", ddlBuilder.mapDefaultValue("to_date('2020-01-01'", dateColumn()));
    }

    @Test
    void testMapDefaultValue_withNewIdOnStringColumn() {
        String mapped = ddlBuilder.mapDefaultValue("NEWID()", stringColumn());
        assertTrue(mapped.startsWith("HEX(RANDOMBLOB(4))"));
        assertTrue(mapped.endsWith("HEX(RANDOMBLOB(6))"));
    }

    @Test
    void testMapDefaultValue_withNewSequentialIdOnStringColumn() {
        String mapped = ddlBuilder.mapDefaultValue("NEWSEQUENTIALID()", stringColumn());
        assertTrue(mapped.startsWith("HEX(RANDOMBLOB(4))"));
        assertTrue(mapped.contains("STRFTIME"));
    }

    @Test
    void testMapDefaultValue_withNull() {
        assertEquals("NULL", ddlBuilder.mapDefaultValue(null, stringColumn()));
    }

    @Test
    void testMapDefaultValue_withPlainStringValue() {
        assertEquals("abc", ddlBuilder.mapDefaultValue("abc", stringColumn()));
    }

    @Test
    void testShouldUseQuotes_withRandomBlobDefault() {
        assertFalse(ddlBuilder.shouldUseQuotes("NEWID()", stringColumn()));
    }

    @Test
    void testShouldUseQuotes_withPlainStringDefault() {
        assertTrue(ddlBuilder.shouldUseQuotes("abc", stringColumn()));
    }

    @Test
    void testCreateTable_dropsAutoIncrementOnCompositeKey() {
        Table table = newCompositeKeyTable();
        table.getColumn(0).setAutoIncrement(true);
        ddlBuilder.createTable(table, ddl, false, false);
        assertFalse(table.getColumn(0).isAutoIncrement());
        assertFalse(ddl.toString().contains("AUTOINCREMENT"));
    }

    @Test
    void testCreateTable_keepsAutoIncrementOnSingleKey() {
        Table table = newTable();
        table.getColumn(0).setAutoIncrement(true);
        ddlBuilder.createTable(table, ddl, false, false);
        assertTrue(table.getColumn(0).isAutoIncrement());
        assertTrue(ddl.toString().contains("AUTOINCREMENT"));
    }

    private Table newTable() {
        Table table = new Table("item");
        table.addColumn(new Column("id", true, Types.INTEGER, 10, 0));
        table.addColumn(new Column("name", false, Types.VARCHAR, 50, 0));
        return table;
    }

    private Table newCompositeKeyTable() {
        Table table = new Table("item");
        table.addColumn(new Column("id", true, Types.INTEGER, 10, 0));
        table.addColumn(new Column("store_id", true, Types.INTEGER, 10, 0));
        table.addColumn(new Column("name", false, Types.VARCHAR, 50, 0));
        return table;
    }

    private Column dateColumn() {
        return new Column("create_time", false, Types.TIMESTAMP, 0, 0);
    }

    private Column stringColumn() {
        return new Column("name", false, Types.VARCHAR, 50, 0);
    }
}
