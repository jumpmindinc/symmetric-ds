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
package org.jumpmind.db.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

class ViewTest {
    private static View viewWithColumns(String viewName, String... columnNames) {
        View v = new View(viewName);
        for (String name : columnNames) {
            v.addColumn(new Column(name));
        }
        return v;
    }

    @Test
    void testConstructor_noArg() {
        View v = new View();
        assertNull(v.getName());
        assertNull(v.getCatalog());
        assertNull(v.getSchema());
    }

    @Test
    void testConstructor_name() {
        View v = new View("sales_view");
        assertEquals("sales_view", v.getName());
        assertNull(v.getCatalog());
        assertNull(v.getSchema());
    }

    @Test
    void testConstructor_catalogSchemaName() {
        View v = new View("mydb", "dbo", "sales_view");
        assertEquals("mydb", v.getCatalog());
        assertEquals("dbo", v.getSchema());
        assertEquals("sales_view", v.getName());
    }

    @Test
    void testCopy_returnsViewInstance() {
        View v = viewWithColumns("sales_view", "id", "amount");
        Relation copy = v.copy();
        assertTrue(copy instanceof View);
    }

    @Test
    void testCopy_copiesColumnsAndName() {
        View v = viewWithColumns("sales_view", "id", "amount");
        View copy = v.copy();
        assertEquals("sales_view", copy.getName());
        assertEquals(2, copy.getColumnCount());
        assertEquals("id", copy.getColumn(0).getName());
        assertEquals("amount", copy.getColumn(1).getName());
    }

    @Test
    void testCopy_isDeepCopy_columns() {
        View v = viewWithColumns("sales_view", "id", "name");
        View copy = v.copy();
        assertNotSame(v.getColumn(0), copy.getColumn(0));
        copy.getColumn(0).setName("modified");
        assertEquals("id", v.getColumn(0).getName());
    }

    @Test
    void testCopy_isDeepCopy_columnList() {
        View v = viewWithColumns("sales_view", "id");
        View copy = v.copy();
        copy.addColumn(new Column("extra"));
        assertEquals(1, v.getColumnCount());
        assertEquals(2, copy.getColumnCount());
    }

    @Test
    void testCopyAndFilterColumns_ordersColumns() {
        View v = viewWithColumns("sales_view", "id", "name", "amount");
        View copy = v.copyAndFilterColumns(new String[] { "amount", "id" }, null, false, false);
        assertNotNull(copy);
        assertEquals(2, copy.getColumnCount());
        assertEquals("amount", copy.getColumn(0).getName());
        assertEquals("id", copy.getColumn(1).getName());
    }

    @Test
    void testCopyAndFilterColumns_setPrimaryKeys() {
        View v = viewWithColumns("sales_view", "id", "name", "amount");
        View copy = v.copyAndFilterColumns(
                new String[] { "id", "name", "amount" },
                new String[] { "id" },
                true, false);
        assertTrue(copy.findColumn("id").isPrimaryKey());
        assertFalse(copy.findColumn("name").isPrimaryKey());
        assertFalse(copy.findColumn("amount").isPrimaryKey());
    }

    @Test
    void testCopyAndFilterColumns_noSetPrimaryKeys_preservesExistingPkFlag() {
        View v = new View("sales_view");
        Column id = new Column("id");
        id.setPrimaryKey(true);
        Column name = new Column("name");
        v.addColumn(id);
        v.addColumn(name);
        View copy = v.copyAndFilterColumns(new String[] { "id", "name" }, null, false, false);
        assertTrue(copy.findColumn("id").isPrimaryKey());
    }

    @Test
    void testCopyAndFilterColumns_addMissingColumns() {
        View v = viewWithColumns("sales_view", "id");
        View copy = v.copyAndFilterColumns(new String[] { "id", "extra" }, null, false, true);
        assertEquals(2, copy.getColumnCount());
        assertNotNull(copy.findColumn("extra"));
    }

    @Test
    void testCopyAndFilterColumns_doesNotCopyIndicesOrForeignKeys() {
        View v = viewWithColumns("sales_view", "id", "name");
        View copy = v.copyAndFilterColumns(new String[] { "id", "name" }, null, false, false);
        assertTrue(copy instanceof View);
    }

    @Test
    void testEquals_sameFullyQualifiedName() {
        View v1 = new View("mydb", "dbo", "sales_view");
        View v2 = new View("mydb", "dbo", "sales_view");
        assertEquals(v1, v2);
    }

    @Test
    void testEquals_differentName() {
        View v1 = new View("sales_view");
        View v2 = new View("other_view");
        assertNotEquals(v1, v2);
    }

    @Test
    void testEquals_differentSchema() {
        View v1 = new View("mydb", "dbo", "sales_view");
        View v2 = new View("mydb", "other", "sales_view");
        assertNotEquals(v1, v2);
    }

    @Test
    void testEquals_tableNotEqualToView() {
        View v = new View("orders");
        Table t = new Table("orders");
        assertNotEquals(v, t);
    }

    @Test
    void testEquals_null() {
        View v = new View("sales_view");
        assertNotEquals(v, null);
    }

    @Test
    void testEquals_self() {
        View v = new View("sales_view");
        assertEquals(v, v);
    }

    @Test
    void testHashCode_sameForEqualViews() {
        View v1 = new View("mydb", "dbo", "sales_view");
        View v2 = new View("mydb", "dbo", "sales_view");
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void testHashCode_basedOnFullyQualifiedName() {
        View v = new View("mydb", "dbo", "sales_view");
        assertEquals(v.getFullyQualifiedName().hashCode(), v.hashCode());
    }

    @Test
    void testToString_containsViewNameAndColumnCount() {
        View v = viewWithColumns("sales_view", "id", "amount");
        String str = v.toString();
        assertTrue(str, str.contains("sales_view"));
        assertTrue(str, str.contains("2"));
    }

    @Test
    void testClone_deepCopiesColumns() {
        View v = viewWithColumns("sales_view", "id", "name");
        View clone = v.copy();
        assertNotSame(v.getColumn(0), clone.getColumn(0));
        clone.getColumn(0).setName("modified");
        assertEquals("id", v.getColumn(0).getName());
    }

    @Test
    void testCopy_doesNotShareColumnList() {
        View v = viewWithColumns("sales_view", "id");
        View copy = v.copy();
        assertNotSame(v.getColumnsAsList(), copy.getColumnsAsList());
    }
}
