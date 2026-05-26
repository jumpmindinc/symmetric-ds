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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

class RelationTest {
    private static Table tableWithColumns(String... names) {
        Table t = new Table("orders");
        for (String name : names) {
            t.addColumn(new Column(name));
        }
        return t;
    }

    private static Table tableWithPrimaryKey() {
        Table t = new Table("orders");
        Column id = new Column("id");
        id.setPrimaryKey(true);
        Column name = new Column("name");
        t.addColumn(id);
        t.addColumn(name);
        return t;
    }

    @Test
    void testAddColumn_incrementsCount() {
        Table t = new Table("orders");
        assertEquals(0, t.getColumnCount());
        t.addColumn(new Column("id"));
        assertEquals(1, t.getColumnCount());
        t.addColumn(new Column("name"));
        assertEquals(2, t.getColumnCount());
    }

    @Test
    void testAddColumn_atIndex() {
        Table t = tableWithColumns("a", "c");
        t.addColumn(1, new Column("b"));
        assertEquals("a", t.getColumn(0).getName());
        assertEquals("b", t.getColumn(1).getName());
        assertEquals("c", t.getColumn(2).getName());
    }

    @Test
    void testAddColumns_fromStringArray() {
        Table t = new Table("orders");
        t.addColumns(new String[] { "id", "name", "amount" });
        assertEquals(3, t.getColumnCount());
        assertEquals("id", t.getColumn(0).getName());
        assertEquals("amount", t.getColumn(2).getName());
    }

    @Test
    void testRemoveColumn() {
        Table t = tableWithColumns("id", "name");
        Column col = t.getColumn(0);
        t.removeColumn(col);
        assertEquals(1, t.getColumnCount());
        assertEquals("name", t.getColumn(0).getName());
    }

    @Test
    void testRemoveColumn_byIndex() {
        Table t = tableWithColumns("id", "name", "amount");
        t.removeColumn(1);
        assertEquals(2, t.getColumnCount());
        assertEquals("id", t.getColumn(0).getName());
        assertEquals("amount", t.getColumn(1).getName());
    }

    @Test
    void testRemoveAllColumns() {
        Table t = tableWithColumns("id", "name");
        t.removeAllColumns();
        assertEquals(0, t.getColumnCount());
    }

    @Test
    void testGetColumns_returnsAllColumns() {
        Table t = tableWithColumns("id", "name", "amount");
        Column[] cols = t.getColumns();
        assertEquals(3, cols.length);
        assertEquals("id", cols[0].getName());
        assertEquals("name", cols[1].getName());
        assertEquals("amount", cols[2].getName());
    }

    @Test
    void testGetColumnNames() {
        Table t = tableWithColumns("id", "name", "amount");
        assertArrayEquals(new String[] { "id", "name", "amount" }, t.getColumnNames());
    }

    @Test
    void testFindColumn_caseInsensitive() {
        Table t = tableWithColumns("ID", "Name");
        assertNotNull(t.findColumn("id"));
        assertNotNull(t.findColumn("NAME"));
        assertEquals("ID", t.findColumn("id").getName());
    }

    @Test
    void testFindColumn_caseSensitive_found() {
        Table t = tableWithColumns("ID", "Name");
        assertNotNull(t.findColumn("ID", true));
    }

    @Test
    void testFindColumn_caseSensitive_notFound() {
        Table t = tableWithColumns("ID", "Name");
        assertNull(t.findColumn("id", true));
    }

    @Test
    void testFindColumn_notPresent_returnsNull() {
        Table t = tableWithColumns("id");
        assertNull(t.findColumn("unknown"));
    }

    @Test
    void testGetColumnWithName_caseInsensitive() {
        Table t = tableWithColumns("OrderId");
        assertNotNull(t.getColumnWithName("ORDERID"));
        assertNotNull(t.getColumnWithName("orderid"));
    }

    @Test
    void testGetColumnIndex_found() {
        Table t = tableWithColumns("id", "name", "amount");
        assertEquals(1, t.getColumnIndex("name"));
        assertEquals(1, t.getColumnIndex("NAME"));
    }

    @Test
    void testGetColumnIndex_notFound() {
        Table t = tableWithColumns("id");
        assertEquals(-1, t.getColumnIndex("unknown"));
    }

    @Test
    void testHasPrimaryKey_true() {
        Table t = tableWithPrimaryKey();
        assertTrue(t.hasPrimaryKey());
    }

    @Test
    void testHasPrimaryKey_false() {
        Table t = tableWithColumns("id", "name");
        assertFalse(t.hasPrimaryKey());
    }

    @Test
    void testGetPrimaryKeyColumns() {
        Table t = tableWithPrimaryKey();
        Column[] pkCols = t.getPrimaryKeyColumns();
        assertEquals(1, pkCols.length);
        assertEquals("id", pkCols[0].getName());
    }

    @Test
    void testGetPrimaryKeyColumnCount() {
        Table t = tableWithPrimaryKey();
        assertEquals(1, t.getPrimaryKeyColumnCount());
    }

    @Test
    void testGetPrimaryKeyColumnNames() {
        Table t = tableWithPrimaryKey();
        assertArrayEquals(new String[] { "id" }, t.getPrimaryKeyColumnNames());
    }

    @Test
    void testGetNonPrimaryKeyColumns() {
        Table t = tableWithPrimaryKey();
        Column[] nonPkCols = t.getNonPrimaryKeyColumns();
        assertEquals(1, nonPkCols.length);
        assertEquals("name", nonPkCols[0].getName());
    }

    @Test
    void testSetPrimaryKeys_setsSpecifiedColumns() {
        Table t = tableWithColumns("id", "name", "amount");
        t.setPrimaryKeys(new String[] { "id", "amount" });
        assertTrue(t.findColumn("id").isPrimaryKey());
        assertFalse(t.findColumn("name").isPrimaryKey());
        assertTrue(t.findColumn("amount").isPrimaryKey());
    }

    @Test
    void testSetPrimaryKeys_clearsExistingPrimaryKeys() {
        Table t = tableWithPrimaryKey();
        t.setPrimaryKeys(new String[] { "name" });
        assertFalse(t.findColumn("id").isPrimaryKey());
        assertTrue(t.findColumn("name").isPrimaryKey());
    }

    @Test
    void testGetPrimaryKeyColumnIndex() {
        Table t = new Table("orders");
        Column id = new Column("id");
        id.setPrimaryKey(true);
        Column code = new Column("code");
        code.setPrimaryKey(true);
        t.addColumn(new Column("ignored"));
        t.addColumn(id);
        t.addColumn(code);
        assertEquals(0, t.getPrimaryKeyColumnIndex("id"));
        assertEquals(1, t.getPrimaryKeyColumnIndex("code"));
        assertEquals(-1, t.getPrimaryKeyColumnIndex("ignored"));
    }

    @Test
    void testMadeAllColumnsPrimaryKey_defaultFalse() {
        Table t = new Table("orders");
        assertFalse(t.isMadeAllColumnsPrimaryKey());
    }

    @Test
    void testMadeAllColumnsPrimaryKey_setAndGet() {
        Table t = new Table("orders");
        t.setMadeAllColumnsPrimaryKey(true);
        assertTrue(t.isMadeAllColumnsPrimaryKey());
    }

    @Test
    void testGetAutoIncrementColumns() {
        Table t = new Table("orders");
        Column auto = new Column("id");
        auto.setAutoIncrement(true);
        Column plain = new Column("name");
        t.addColumn(auto);
        t.addColumn(plain);
        Column[] result = t.getAutoIncrementColumns();
        assertEquals(1, result.length);
        assertEquals("id", result[0].getName());
    }

    @Test
    void testEqualsByName_sameNameAndColumns() {
        Table t1 = tableWithColumns("id", "name");
        Table t2 = tableWithColumns("id", "name");
        assertTrue(t1.equalsByName(t2));
    }

    @Test
    void testEqualsByName_differentName() {
        Table t1 = tableWithColumns("id");
        Table t2 = new Table("other");
        t2.addColumn(new Column("id"));
        assertFalse(t1.equalsByName(t2));
    }

    @Test
    void testEqualsByName_differentColumnCount() {
        Table t1 = tableWithColumns("id");
        Table t2 = tableWithColumns("id", "name");
        assertFalse(t1.equalsByName(t2));
    }

    @Test
    void testEqualsByName_caseInsensitiveNameMatch() {
        Table t1 = new Table("ORDERS");
        t1.addColumn(new Column("ID"));
        Table t2 = new Table("orders");
        t2.addColumn(new Column("id"));
        assertTrue(t1.equalsByName(t2));
    }

    @Test
    void testEqualsByName_self() {
        Table t = tableWithColumns("id", "name");
        assertTrue(t.equalsByName(t));
    }

    @Test
    void testEqualsByName_null() {
        Table t = tableWithColumns("id");
        assertFalse(t.equalsByName(null));
    }

    @Test
    void testCalculateHashcode_consistentForSameData() {
        Table t1 = tableWithColumns("id", "name");
        Table t2 = tableWithColumns("id", "name");
        assertEquals(t1.calculateHashcode(), t2.calculateHashcode());
    }

    @Test
    void testCalculateHashcode_differentForDifferentName() {
        Table t1 = new Table("orders");
        t1.addColumn(new Column("id"));
        Table t2 = new Table("shipments");
        t2.addColumn(new Column("id"));
        assertTrue(t1.calculateHashcode() != t2.calculateHashcode());
    }

    @Test
    void testGetKey_containsName() {
        Table t = tableWithColumns("id");
        assertTrue(t.getKey().contains("orders"));
    }

    @Test
    void testToVerboseString_containsClassNameAndColumnInfo() {
        Table t = new Table("orders");
        t.addColumn(new Column("id"));
        String verbose = t.toVerboseString();
        assertTrue(verbose.contains("Table"));
        assertTrue(verbose.contains("orders"));
    }

    @Test
    void testClone_deepCopiesColumns() {
        Table t = tableWithColumns("id", "name");
        Table clone = t.copy();
        assertNotSame(t.getColumn(0), clone.getColumn(0));
        assertEquals("id", clone.getColumn(0).getName());
        clone.getColumn(0).setName("modified");
        assertEquals("id", t.getColumn(0).getName());
    }

    @Test
    void testGetCommaDeliminatedColumns() {
        Table t = tableWithColumns("id", "name", "amount");
        String result = Relation.getCommaDeliminatedColumns(t.getColumns());
        assertEquals("id,name,amount", result);
    }

    @Test
    void testAreAllColumnsPrimaryKeys_allPk() {
        Column c1 = new Column("id");
        c1.setPrimaryKey(true);
        Column c2 = new Column("code");
        c2.setPrimaryKey(true);
        assertTrue(Relation.areAllColumnsPrimaryKeys(new Column[] { c1, c2 }));
    }

    @Test
    void testAreAllColumnsPrimaryKeys_notAllPk() {
        Column c1 = new Column("id");
        c1.setPrimaryKey(true);
        Column c2 = new Column("name");
        assertFalse(Relation.areAllColumnsPrimaryKeys(new Column[] { c1, c2 }));
    }
}
