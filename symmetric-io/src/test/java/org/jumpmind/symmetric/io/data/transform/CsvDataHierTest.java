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
package org.jumpmind.symmetric.io.data.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.junit.jupiter.api.Test;

class CsvDataHierTest {
    @Test
    void testConstructor_copiesFieldsFromTable() {
        CsvData csvData = new CsvData();
        Table table = new Table("MyCatalog", "MySchema", "MyTable");
        CsvDataHier hier = new CsvDataHier(csvData, table, "PARENT");
        assertSame(csvData, hier.getCsvData());
        assertEquals("MyCatalog", hier.getCatalog());
        assertEquals("MySchema", hier.getSchema());
        assertEquals("MyTable", hier.getTable());
        assertEquals("PARENT", hier.getRelationType());
    }

    @Test
    void testLowerCaseGetters_lowercaseNonNullValues() {
        CsvDataHier hier = new CsvDataHier(new CsvData(), new Table("CATALOG", "SCHEMA", "TABLE"), "PARENT");
        assertEquals("catalog", hier.getCatalogLowerCase());
        assertEquals("schema", hier.getSchemaLowerCase());
        assertEquals("table", hier.getTableLowerCase());
    }

    @Test
    void testLowerCaseGetters_returnNullWhenFieldIsNull() {
        CsvDataHier hier = new CsvDataHier(new CsvData(), new Table(null, null, "TABLE"), "PARENT");
        assertNull(hier.getCatalogLowerCase());
        assertNull(hier.getSchemaLowerCase());
        assertEquals("table", hier.getTableLowerCase());
    }

    @Test
    void testAddChild_lazilyInitializesChildrenList() {
        CsvDataHier parent = new CsvDataHier(new CsvData(), new Table("C", "S", "PARENT"), "PARENT");
        assertNull(parent.getChildren());
        CsvDataHier child = new CsvDataHier(new CsvData(), new Table("C", "S", "CHILD"), "CHILD");
        parent.addChild(child);
        List<CsvDataHier> children = parent.getChildren();
        assertEquals(1, children.size());
        assertSame(child, children.get(0));
    }

    @Test
    void testAddChild_appendsAdditionalChildrenToExistingList() {
        CsvDataHier parent = new CsvDataHier(new CsvData(), new Table("C", "S", "PARENT"), "PARENT");
        CsvDataHier firstChild = new CsvDataHier(new CsvData(), new Table("C", "S", "CHILD1"), "CHILD");
        CsvDataHier secondChild = new CsvDataHier(new CsvData(), new Table("C", "S", "CHILD2"), "CHILD");
        parent.addChild(firstChild);
        parent.addChild(secondChild);
        List<CsvDataHier> children = parent.getChildren();
        assertEquals(2, children.size());
        assertTrue(children.contains(firstChild));
        assertTrue(children.contains(secondChild));
    }

    @Test
    void testSetters_overrideConstructorValues() {
        CsvDataHier hier = new CsvDataHier(new CsvData(), new Table("C", "S", "T"), "PARENT");
        CsvData newData = new CsvData();
        hier.setCsvData(newData);
        hier.setCatalog("NewCatalog");
        hier.setSchema("NewSchema");
        hier.setTable("NewTable");
        hier.setRelationType("CHILD");
        assertSame(newData, hier.getCsvData());
        assertEquals("NewCatalog", hier.getCatalog());
        assertEquals("NewSchema", hier.getSchema());
        assertEquals("NewTable", hier.getTable());
        assertEquals("CHILD", hier.getRelationType());
    }
}
