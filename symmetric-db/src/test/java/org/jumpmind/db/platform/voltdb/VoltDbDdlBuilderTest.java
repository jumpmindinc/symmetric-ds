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
package org.jumpmind.db.platform.voltdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoltDbDdlBuilderTest {
    private VoltDbDdlBuilder ddlBuilder;

    @BeforeEach
    void setUp() {
        ddlBuilder = new VoltDbDdlBuilder();
    }

    @Test
    void testConstructor_removesTheIdentifierLengthLimit() {
        assertEquals(-1, ddlBuilder.getDatabaseInfo().getMaxTableNameLength());
        assertEquals(-1, ddlBuilder.getDatabaseInfo().getMaxColumnNameLength());
    }

    @Test
    void testConstructor_mapsDateAndTimeTypesToTimestamp() {
        assertEquals("TIMESTAMP", ddlBuilder.getDatabaseInfo().getNativeType(Types.DATE));
        assertEquals("TIMESTAMP", ddlBuilder.getDatabaseInfo().getNativeType(Types.TIME));
        assertEquals("TIMESTAMP", ddlBuilder.getDatabaseInfo().getNativeType(ColumnTypes.TIMETZ));
        assertEquals(Types.TIMESTAMP, ddlBuilder.getDatabaseInfo().getTargetJdbcType(Types.DATE));
    }

    @Test
    void testConstructor_mapsBitToTinyIntAndDoubleToDecimal() {
        assertEquals("TINYINT", ddlBuilder.getDatabaseInfo().getNativeType(Types.BIT));
        assertEquals(Types.TINYINT, ddlBuilder.getDatabaseInfo().getTargetJdbcType(Types.BIT));
        assertEquals("DECIMAL", ddlBuilder.getDatabaseInfo().getNativeType(Types.DOUBLE));
        assertEquals(Types.DECIMAL, ddlBuilder.getDatabaseInfo().getTargetJdbcType(Types.DOUBLE));
    }

    @Test
    void testConstructor_mapsLargeTextTypesToWideVarchar() {
        assertEquals("VARCHAR(100000)", ddlBuilder.getDatabaseInfo().getNativeType(Types.CLOB));
        assertEquals("VARCHAR(100000)", ddlBuilder.getDatabaseInfo().getNativeType(Types.LONGVARCHAR));
        assertEquals(Types.VARCHAR, ddlBuilder.getDatabaseInfo().getTargetJdbcType(Types.CLOB));
    }

    @Test
    void testConstructor_mapsBinaryTypesToVarchar() {
        assertEquals("VARCHAR", ddlBuilder.getDatabaseInfo().getNativeType(Types.BINARY));
        assertEquals("VARCHAR", ddlBuilder.getDatabaseInfo().getNativeType(Types.VARBINARY));
        assertEquals("VARCHAR", ddlBuilder.getDatabaseInfo().getNativeType(Types.LONGVARBINARY));
        assertEquals("VARCHAR", ddlBuilder.getDatabaseInfo().getNativeType(Types.BLOB));
        assertEquals("VARCHAR", ddlBuilder.getDatabaseInfo().getNativeType(Types.CHAR));
    }

    @Test
    void testConstructor_setsDefaultAndMaximumSizes() {
        assertEquals(254, ddlBuilder.getDatabaseInfo().getDefaultSize(Types.CHAR));
        assertEquals(254, ddlBuilder.getDatabaseInfo().getDefaultSize(Types.VARCHAR));
        assertEquals(6, ddlBuilder.getDatabaseInfo().getMaxSize("TIMESTAMP"));
    }

    @Test
    void testWriteColumnAutoIncrementStmt_writesNothing() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumnAutoIncrementStmt(new Table("item"), new Column("item_id", true), ddl);
        assertEquals(0, ddl.length());
    }

    @Test
    void testCreateTable_turnsOffAutoIncrement() {
        Column column = new Column("item_id", true, Types.INTEGER, 10, 0);
        column.setAutoIncrement(true);
        Table table = new Table("item", column);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.createTable(table, ddl, false, false);
        assertFalse(column.isAutoIncrement());
        assertTrue(ddl.toString().toUpperCase().contains("CREATE TABLE"));
    }

    @Test
    void testWriteCascadeAttributesForForeignKey_writesNothing() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKey(new ForeignKey("fk_item"), ddl);
        assertEquals(0, ddl.length());
    }

    @Test
    void testAlignPrimaryKeys_reordersCurrentColumnsToMatchDesired() {
        Table current = new Table("item", pkColumn("store_id"), pkColumn("item_id"), new Column("name"));
        Table desired = new Table("item", pkColumn("item_id"), pkColumn("store_id"), new Column("name"));
        ddlBuilder.alignPrimaryKeys(current, desired);
        assertEquals("item_id", current.getColumn(0).getName());
        assertEquals("store_id", current.getColumn(1).getName());
        assertEquals("name", current.getColumn(2).getName());
    }

    @Test
    void testAlignPrimaryKeys_keepsNonKeyColumnsAtTheEnd() {
        Table current = new Table("item", new Column("name"), pkColumn("item_id"));
        Table desired = new Table("item", pkColumn("item_id"), new Column("name"));
        ddlBuilder.alignPrimaryKeys(current, desired);
        assertEquals("item_id", current.getColumn(0).getName());
        assertEquals("name", current.getColumn(1).getName());
    }

    @Test
    void testAlignIndexColumns_reordersAndRenumbersColumns() {
        IIndex current = newIndex("idx_item", "store_id", "item_id");
        IIndex desired = newIndex("idx_item", "item_id", "store_id");
        ddlBuilder.alignIndexColumns(current, desired);
        assertEquals("item_id", current.getColumn(0).getName());
        assertEquals(1, current.getColumn(0).getOrdinalPosition());
        assertEquals("store_id", current.getColumn(1).getName());
        assertEquals(2, current.getColumn(1).getOrdinalPosition());
    }

    @Test
    void testAlignIndexColumns_keepsColumnsMissingFromDesiredIndex() {
        IIndex current = newIndex("idx_item", "store_id", "extra");
        IIndex desired = newIndex("idx_item", "store_id");
        ddlBuilder.alignIndexColumns(current, desired);
        assertEquals(2, current.getColumnCount());
        assertEquals("extra", current.getColumn(0).getName());
        assertEquals(0, current.getColumn(0).getOrdinalPosition());
        assertEquals("store_id", current.getColumn(1).getName());
        assertEquals(1, current.getColumn(1).getOrdinalPosition());
    }

    @Test
    void testIsAlterDatabase_alignsPrimaryKeysBeforeComparing() {
        Database current = newDatabase(new Table("item", pkColumn("store_id"), pkColumn("item_id")));
        Database desired = newDatabase(new Table("item", pkColumn("item_id"), pkColumn("store_id")));
        assertFalse(ddlBuilder.isAlterDatabase(current, desired));
        assertEquals("item_id", current.findTable("item").getColumn(0).getName());
    }

    @Test
    void testIsAlterDatabase_alignsIndexColumnsBeforeComparing() {
        Table currentTable = new Table("item", pkColumn("item_id"), new Column("store_id"));
        currentTable.addIndex(newIndex("idx_item", "store_id", "item_id"));
        Table desiredTable = new Table("item", pkColumn("item_id"), new Column("store_id"));
        desiredTable.addIndex(newIndex("idx_item", "item_id", "store_id"));
        assertFalse(ddlBuilder.isAlterDatabase(newDatabase(currentTable), newDatabase(desiredTable)));
        assertEquals("item_id", currentTable.getIndex(0).getColumn(0).getName());
    }

    @Test
    void testIsAlterDatabase_reportsChangesWhenTheModelsDiffer() {
        Database current = newDatabase(new Table("item", pkColumn("item_id")));
        Database desired = newDatabase(new Table("item", pkColumn("item_id"), new Column("name")));
        assertTrue(ddlBuilder.isAlterDatabase(current, desired));
    }

    private Column pkColumn(String name) {
        return new Column(name, true, Types.INTEGER, 10, 0);
    }

    private IIndex newIndex(String name, String... columnNames) {
        IIndex index = new NonUniqueIndex(name);
        for (String columnName : columnNames) {
            index.addColumn(new IndexColumn(columnName));
        }
        return index;
    }

    private Database newDatabase(Table table) {
        Database database = new Database();
        database.setName("test");
        database.addTable(table);
        return database;
    }
}
