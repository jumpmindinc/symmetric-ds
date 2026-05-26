/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.db.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

class TableTest {
    private static Table tableWithColumns(String tableName, String... columnNames) {
        Table t = new Table(tableName);
        for (String name : columnNames) {
            t.addColumn(new Column(name));
        }
        return t;
    }

    @Test
    void testConstructor_noArg() {
        Table t = new Table();
        assertNull(t.getName());
        assertNull(t.getCatalog());
        assertNull(t.getSchema());
        assertEquals(0, t.getColumnCount());
        assertEquals(0, t.getForeignKeyCount());
    }

    @Test
    void testConstructor_name() {
        Table t = new Table("orders");
        assertEquals("orders", t.getName());
        assertNull(t.getCatalog());
        assertNull(t.getSchema());
    }

    @Test
    void testConstructor_catalogSchemaName() {
        Table t = new Table("mydb", "dbo", "orders");
        assertEquals("mydb", t.getCatalog());
        assertEquals("dbo", t.getSchema());
        assertEquals("orders", t.getName());
    }

    @Test
    void testConstructor_nameAndColumns() {
        Column id = new Column("id");
        Column name = new Column("name");
        Table t = new Table("orders", id, name);
        assertEquals(2, t.getColumnCount());
        assertEquals("id", t.getColumn(0).getName());
        assertEquals("name", t.getColumn(1).getName());
    }

    @Test
    void testConstructor_catalogSchemaNameColumnNamesKeyNames() {
        Table t = new Table("mydb", "dbo", "orders",
                new String[] { "id", "name" },
                new String[] { "id" });
        assertEquals(2, t.getColumnCount());
        assertTrue(t.findColumn("id").isPrimaryKey());
        assertFalse(t.findColumn("name").isPrimaryKey());
    }

    @Test
    void testSetCatalog_propagatesToMatchingForeignTableCatalog() {
        Table t = new Table("olddb", "dbo", "orders");
        ForeignKey fk = new ForeignKey("fk_customer", "customer");
        fk.setForeignTableCatalog("olddb");
        fk.setForeignTableSchema("dbo");
        t.addForeignKey(fk);
        t.setCatalog("newdb");
        assertEquals("newdb", t.getCatalog());
        assertEquals("newdb", t.getForeignKey(0).getForeignTableCatalog());
    }

    @Test
    void testSetCatalog_doesNotUpdateNonMatchingForeignTableCatalog() {
        Table t = new Table("mydb", "dbo", "orders");
        ForeignKey fk = new ForeignKey("fk_customer", "customer");
        fk.setForeignTableCatalog("otherdb");
        t.addForeignKey(fk);
        t.setCatalog("newdb");
        assertEquals("otherdb", t.getForeignKey(0).getForeignTableCatalog());
    }

    @Test
    void testSetCatalog_withNullOriginalCatalog_storesOldCatalog() {
        Table t = new Table("orders");
        t.setCatalog("newdb");
        assertEquals("newdb", t.getCatalog());
        assertEquals("newdb", t.getOldCatalog());
    }

    @Test
    void testSetSchema_propagatesToMatchingForeignTableSchema() {
        Table t = new Table("mydb", "oldschema", "orders");
        ForeignKey fk = new ForeignKey("fk_customer", "customer");
        fk.setForeignTableSchema("oldschema");
        t.addForeignKey(fk);
        t.setSchema("newschema");
        assertEquals("newschema", t.getSchema());
        assertEquals("newschema", t.getForeignKey(0).getForeignTableSchema());
    }

    @Test
    void testSetSchema_doesNotUpdateNonMatchingForeignTableSchema() {
        Table t = new Table("mydb", "dbo", "orders");
        ForeignKey fk = new ForeignKey("fk_customer", "customer");
        fk.setForeignTableSchema("other");
        t.addForeignKey(fk);
        t.setSchema("newschema");
        assertEquals("other", t.getForeignKey(0).getForeignTableSchema());
    }

    @Test
    void testAddForeignKey_incrementsCount() {
        Table t = new Table("orders");
        assertEquals(0, t.getForeignKeyCount());
        t.addForeignKey(new ForeignKey("fk1", "customer"));
        assertEquals(1, t.getForeignKeyCount());
    }

    @Test
    void testRemoveForeignKey() {
        Table t = new Table("orders");
        ForeignKey fk = new ForeignKey("fk1", "customer");
        t.addForeignKey(fk);
        t.removeForeignKey(fk);
        assertEquals(0, t.getForeignKeyCount());
    }

    @Test
    void testCopy_returnsTableInstance() {
        Table t = tableWithColumns("orders", "id", "name");
        Relation copy = t.copy();
        assertTrue(copy instanceof Table);
    }

    @Test
    void testCopy_copiesNameAndColumns() {
        Table t = tableWithColumns("orders", "id", "name");
        Table copy = t.copy();
        assertEquals("orders", copy.getName());
        assertEquals(2, copy.getColumnCount());
        assertEquals("id", copy.getColumn(0).getName());
    }

    @Test
    void testCopy_deepCopies_columns() {
        Table t = tableWithColumns("orders", "id", "name");
        Table copy = t.copy();
        assertNotSame(t.getColumn(0), copy.getColumn(0));
        copy.getColumn(0).setName("modified");
        assertEquals("id", t.getColumn(0).getName());
    }

    @Test
    void testCopy_deepCopies_foreignKeys() {
        Table t = new Table("orders");
        t.addForeignKey(new ForeignKey("fk_cust", "customer"));
        Table copy = t.copy();
        assertNotSame(t.getForeignKey(0), copy.getForeignKey(0));
        assertEquals(1, copy.getForeignKeyCount());
    }

    @Test
    void testCopy_doesNotShareColumnList() {
        Table t = tableWithColumns("orders", "id");
        Table copy = t.copy();
        copy.addColumn(new Column("extra"));
        assertEquals(1, t.getColumnCount());
        assertEquals(2, copy.getColumnCount());
    }

    @Test
    void testCopyAndFilterColumns_ordersColumns() {
        Table t = tableWithColumns("orders", "id", "name", "amount");
        Table copy = t.copyAndFilterColumns(new String[] { "amount", "id" }, null, false, false);
        assertNotNull(copy);
        assertEquals(2, copy.getColumnCount());
        assertEquals("amount", copy.getColumn(0).getName());
        assertEquals("id", copy.getColumn(1).getName());
    }

    @Test
    void testCopyAndFilterColumns_setPrimaryKeys() {
        Table t = tableWithColumns("orders", "id", "name", "amount");
        Table copy = t.copyAndFilterColumns(
                new String[] { "id", "name", "amount" },
                new String[] { "id", "amount" },
                true, false);
        assertTrue(copy.findColumn("id").isPrimaryKey());
        assertFalse(copy.findColumn("name").isPrimaryKey());
        assertTrue(copy.findColumn("amount").isPrimaryKey());
    }

    @Test
    void testEquals_sameStructure() {
        Table t1 = tableWithColumns("orders", "id", "name");
        Table t2 = tableWithColumns("orders", "id", "name");
        assertEquals(t1, t2);
    }

    @Test
    void testEquals_differentName() {
        Table t1 = tableWithColumns("orders", "id");
        Table t2 = tableWithColumns("shipments", "id");
        assertFalse(t1.equals(t2));
    }

    @Test
    void testEquals_differentColumns() {
        Table t1 = tableWithColumns("orders", "id");
        Table t2 = tableWithColumns("orders", "code");
        assertFalse(t1.equals(t2));
    }

    @Test
    void testEquals_tableNotEqualToView() {
        Table t = new Table("orders");
        View v = new View("orders");
        assertFalse(t.equals(v));
    }

    @Test
    void testEquals_null() {
        Table t = new Table("orders");
        assertFalse(t.equals(null));
    }

    @Test
    void testHashCode_sameForEqualTables() {
        Table t1 = tableWithColumns("orders", "id", "name");
        Table t2 = tableWithColumns("orders", "id", "name");
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void testGetOldCatalog_preservesPreviousValue() {
        Table t = new Table("mydb", "dbo", "orders");
        t.setCatalog("newdb");
        assertEquals("mydb", t.getOldCatalog());
    }

    @Test
    void testGetOldSchema_preservesPreviousValue() {
        Table t = new Table("mydb", "oldschema", "orders");
        t.setSchema("newschema");
        assertEquals("oldschema", t.getOldSchema());
    }
}
