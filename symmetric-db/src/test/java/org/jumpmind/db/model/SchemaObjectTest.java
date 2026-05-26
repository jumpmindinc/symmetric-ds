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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

class SchemaObjectTest {
    @Test
    void testGetFullyQualifiedName_nameOnly() {
        Table t = new Table("orders");
        assertEquals("orders", t.getFullyQualifiedName());
    }

    @Test
    void testGetFullyQualifiedName_schemaAndName() {
        Table t = new Table(null, "dbo", "orders");
        assertEquals("dbo.orders", t.getFullyQualifiedName());
    }

    @Test
    void testGetFullyQualifiedName_catalogSchemaAndName() {
        Table t = new Table("mydb", "dbo", "orders");
        assertEquals("mydb.dbo.orders", t.getFullyQualifiedName());
    }

    @Test
    void testGetFullyQualifiedName_catalogOnly() {
        View v = new View("mydb", null, "sales_view");
        assertEquals("mydb.sales_view", v.getFullyQualifiedName());
    }

    @Test
    void testGetFullyQualifiedName_static_noQualifiers() {
        assertEquals("orders", SchemaObject.getFullyQualifiedName(null, null, "orders"));
    }

    @Test
    void testGetFullyQualifiedName_static_withCatalogAndSchema() {
        assertEquals("mydb.dbo.orders", SchemaObject.getFullyQualifiedName("mydb", "dbo", "orders"));
    }

    @Test
    void testGetFullyQualifiedName_static_withQuoteString() {
        String fqn = SchemaObject.getFullyQualifiedName("mydb", "dbo", "orders", "\"", ".", ".");
        assertEquals("\"mydb\".\"dbo\".\"orders\"", fqn);
    }

    @Test
    void testGetFullyQualifiedPrefix_static_bothQualifiers() {
        assertEquals("mydb.dbo.", SchemaObject.getFullyQualifiedPrefix("mydb", "dbo"));
    }

    @Test
    void testGetFullyQualifiedPrefix_static_schemaOnly() {
        assertEquals("dbo.", SchemaObject.getFullyQualifiedPrefix(null, "dbo"));
    }

    @Test
    void testGetFullyQualifiedPrefix_static_neitherQualifier() {
        assertEquals("", SchemaObject.getFullyQualifiedPrefix(null, null));
    }

    @Test
    void testGetFullyQualifiedNameLowerCase() {
        Table t = new Table("MyDB", "MySchema", "ORDERS");
        assertEquals("mydb.myschema.orders", t.getFullyQualifiedNameLowerCase());
    }

    @Test
    void testGetNameLowerCase() {
        Table t = new Table("ORDERS");
        assertEquals("orders", t.getNameLowerCase());
    }

    @Test
    void testSetName_invalidatesFullyQualifiedNameCache() {
        Table t = new Table("mydb", "dbo", "orders");
        String before = t.getFullyQualifiedName();
        t.setName("shipments");
        assertNotSame(before, t.getFullyQualifiedName());
        assertEquals("mydb.dbo.shipments", t.getFullyQualifiedName());
    }

    @Test
    void testSetCatalog_invalidatesFullyQualifiedNameCache() {
        Table t = new Table("mydb", "dbo", "orders");
        t.getFullyQualifiedName();
        t.setCatalog("newdb");
        assertEquals("newdb.dbo.orders", t.getFullyQualifiedName());
    }

    @Test
    void testSetSchema_invalidatesFullyQualifiedNameCache() {
        Table t = new Table("mydb", "dbo", "orders");
        t.getFullyQualifiedName();
        t.setSchema("sales");
        assertEquals("mydb.sales.orders", t.getFullyQualifiedName());
    }

    @Test
    void testGettersAndSetters() {
        Table t = new Table();
        t.setName("orders");
        t.setCatalog("mydb");
        t.setSchema("dbo");
        t.setDescription("Order table");
        t.setType("TABLE");
        assertEquals("orders", t.getName());
        assertEquals("mydb", t.getCatalog());
        assertEquals("dbo", t.getSchema());
        assertEquals("Order table", t.getDescription());
        assertEquals("TABLE", t.getType());
    }

    @Test
    void testGetDescription_nullByDefault() {
        Table t = new Table("orders");
        assertNull(t.getDescription());
    }

    @Test
    void testCompareTo_lessThan() {
        Table t1 = new Table("alpha");
        Table t2 = new Table("beta");
        assertTrue(t1.compareTo(t2) < 0);
    }

    @Test
    void testCompareTo_greaterThan() {
        Table t1 = new Table("beta");
        Table t2 = new Table("alpha");
        assertTrue(t1.compareTo(t2) > 0);
    }

    @Test
    void testCompareTo_equal() {
        Table t1 = new Table("orders");
        Table t2 = new Table("orders");
        assertEquals(0, t1.compareTo(t2));
    }

    @Test
    void testCompareTo_byFullyQualifiedName() {
        Table t1 = new Table(null, "aaa", "orders");
        Table t2 = new Table(null, "zzz", "orders");
        assertTrue(t1.compareTo(t2) < 0);
    }

    @Test
    void testClone_resetsNameCaches() throws CloneNotSupportedException {
        View v = new View("mydb", "dbo", "sales_view");
        v.getFullyQualifiedName();
        v.getFullyQualifiedNameLowerCase();
        v.getNameLowerCase();
        View clone = v.copy();
        clone.setName("other_view");
        assertEquals("mydb.dbo.other_view", clone.getFullyQualifiedName());
        assertEquals("mydb.dbo.sales_view", v.getFullyQualifiedName());
    }

    @Test
    void testGetQualifiedName_schemaAndName() {
        Table t = new Table(null, "dbo", "orders");
        assertEquals("dbo.orders", t.getQualifiedName());
    }
}
