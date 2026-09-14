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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;
import org.junit.jupiter.api.Test;

class TransformTableTest {
    @Test
    void testColumnsConstructor_separatesPrimaryKeyColumns() {
        TransformColumn keyColumn = new TransformColumn("id", "id", true);
        TransformColumn valueColumn = new TransformColumn("name", "name", false);
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.EXTRACT, keyColumn, valueColumn);
        assertEquals("SRC", table.getSourceTableName());
        assertEquals("TGT", table.getTargetTableName());
        assertEquals(TransformPoint.EXTRACT, table.getTransformPoint());
        assertEquals(2, table.getTransformColumns().size());
        assertEquals(1, table.getPrimaryKeyColumns().size());
        assertTrue(table.getPrimaryKeyColumns().contains(keyColumn));
    }

    @Test
    void testColumnsConstructor_withNullColumns_hasEmptyLists() {
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.LOAD, (TransformColumn[]) null);
        assertTrue(table.getTransformColumns().isEmpty());
        assertTrue(table.getPrimaryKeyColumns().isEmpty());
    }

    @Test
    void testGetFullyQualifiedSourceTableName_combinesCatalogSchemaAndTable() {
        TransformTable table = new TransformTable();
        table.setSourceCatalogName("CAT");
        table.setSourceSchemaName("SCH");
        table.setSourceTableName("TBL");
        assertEquals("CAT.SCH.TBL", table.getFullyQualifiedSourceTableName());
    }

    @Test
    void testGetFullyQualifiedTargetTableName_withOnlyTableName() {
        TransformTable table = new TransformTable();
        table.setTargetTableName("TBL");
        assertEquals("TBL", table.getFullyQualifiedTargetTableName());
    }

    @Test
    void testSetTransformColumns_rebuildsPrimaryKeyColumns() {
        TransformTable table = new TransformTable();
        TransformColumn keyColumn = new TransformColumn("id", "id", true);
        TransformColumn valueColumn = new TransformColumn("name", "name", false);
        List<TransformColumn> columns = new ArrayList<TransformColumn>();
        columns.add(keyColumn);
        columns.add(valueColumn);
        table.setTransformColumns(columns);
        assertEquals(2, table.getTransformColumns().size());
        assertEquals(1, table.getPrimaryKeyColumns().size());
        assertTrue(table.getPrimaryKeyColumns().contains(keyColumn));
    }

    @Test
    void testSetTransformColumns_withNull_clearsPrimaryKeyColumns() {
        TransformTable table = new TransformTable();
        table.setTransformColumns(null);
        assertNull(table.getTransformColumns());
        assertTrue(table.getPrimaryKeyColumns().isEmpty());
    }

    @Test
    void testAddTransformColumn_lazilyInitializesListsAndTracksPk() {
        TransformTable table = new TransformTable();
        assertNull(table.getTransformColumns());
        TransformColumn keyColumn = new TransformColumn("id", "id", true);
        table.addTransformColumn(keyColumn);
        assertEquals(1, table.getTransformColumns().size());
        assertEquals(1, table.getPrimaryKeyColumns().size());
        TransformColumn valueColumn = new TransformColumn("name", "name", false);
        table.addTransformColumn(valueColumn);
        assertEquals(2, table.getTransformColumns().size());
        assertEquals(1, table.getPrimaryKeyColumns().size());
    }

    @Test
    void testGetTransformColumnFor_matchesSourceColumnNameCaseInsensitively() {
        TransformColumn column = new TransformColumn("MyColumn", "target", false);
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.EXTRACT, column);
        List<TransformColumn> matches = table.getTransformColumnFor("mycolumn");
        assertEquals(1, matches.size());
        assertEquals(column, matches.get(0));
        assertTrue(table.getTransformColumnFor("nomatch").isEmpty());
    }

    @Test
    void testGetTransformColumnTo_matchesTargetColumnNameCaseInsensitively() {
        TransformColumn column = new TransformColumn("source", "MyTarget", false);
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.EXTRACT, column);
        List<TransformColumn> matches = table.getTransformColumnTo("mytarget");
        assertEquals(1, matches.size());
        assertEquals(column, matches.get(0));
        assertTrue(table.getTransformColumnTo("nomatch").isEmpty());
    }

    @Test
    void testGetTransformColumn_matchesTargetColumnNameAndIncludeOn() {
        TransformColumn column = new TransformColumn("source", "MyTarget", false);
        column.setIncludeOn(IncludeOnType.UPDATE);
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.EXTRACT, column);
        assertEquals(column, table.getTransformColumn("mytarget", IncludeOnType.UPDATE));
        assertNull(table.getTransformColumn("mytarget", IncludeOnType.INSERT));
        assertNull(table.getTransformColumn("nomatch", IncludeOnType.UPDATE));
    }

    @Test
    void testEvaluateTargetDmlAction_withValidEnumName_returnsDirectlyWithoutInterpreter() {
        TransformTable table = new TransformTable();
        table.setUpdateAction("INS_ROW");
        TargetDmlAction action = table.evaluateTargetDmlAction(null, null);
        assertEquals(TargetDmlAction.INS_ROW, action);
    }

    @Test
    void testEvaluateTargetDmlAction_withBshScript_evaluatesScriptToDetermineAction() {
        TransformTable table = new TransformTable();
        table.setUpdateAction("return \"DEL_ROW\";");
        DataContext dataContext = new DataContext();
        Map<String, String> sourceValues = new HashMap<String, String>();
        sourceValues.put("id", "1");
        TransformedData transformedData = new TransformedData(null, DataEventType.UPDATE, null, null, sourceValues);
        TargetDmlAction action = table.evaluateTargetDmlAction(dataContext, transformedData);
        assertEquals(TargetDmlAction.DEL_ROW, action);
    }

    @Test
    void testGetSetDeleteAction() {
        TransformTable table = new TransformTable();
        assertEquals(TargetDmlAction.DEL_ROW, table.getDeleteAction());
        table.setDeleteAction(TargetDmlAction.NONE);
        assertEquals(TargetDmlAction.NONE, table.getDeleteAction());
    }

    @Test
    void testGetSetUpdateFirst() {
        TransformTable table = new TransformTable();
        assertFalse(table.isUpdateFirst());
        table.setUpdateFirst(true);
        assertTrue(table.isUpdateFirst());
    }

    @Test
    void testGetSetColumnPolicy() {
        TransformTable table = new TransformTable();
        assertEquals(ColumnPolicy.IMPLIED, table.getColumnPolicy());
        table.setColumnPolicy(ColumnPolicy.SPECIFIED);
        assertEquals(ColumnPolicy.SPECIFIED, table.getColumnPolicy());
    }

    @Test
    void testHashCodeAndEquals_withTransformId_comparesById() {
        TransformTable first = new TransformTable();
        first.setTransformId("transform1");
        TransformTable second = new TransformTable();
        second.setTransformId("transform1");
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        TransformTable third = new TransformTable();
        third.setTransformId("transform2");
        assertNotEquals(first, third);
    }

    @Test
    void testEquals_withNoTransformId_fallsBackToIdentity() {
        TransformTable first = new TransformTable();
        TransformTable second = new TransformTable();
        assertNotEquals(first, second);
        assertEquals(first, first);
    }

    @Test
    void testEquals_withTransformIdAgainstNonTransformTable_isNotEqual() {
        TransformTable table = new TransformTable();
        table.setTransformId("transform1");
        assertNotEquals("not a transform table", table);
    }

    @Test
    void testToString_withTransformId_returnsTransformId() {
        TransformTable table = new TransformTable();
        table.setTransformId("transform1");
        assertEquals("transform1", table.toString());
    }

    @Test
    void testToString_withNoTransformId_fallsBackToObjectToString() {
        TransformTable table = new TransformTable();
        assertEquals(table.toString(), table.toString());
        assertNotEquals("transform1", table.toString());
    }

    @Test
    void testEnhanceWithImpliedColumns_addsImplicitAllTypeColumnsWhenNotCovered() {
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.EXTRACT);
        TransformTable enhanced = table.enhanceWithImpliedColumns(new String[] { "id" }, new String[] { "name" });
        assertEquals(2, enhanced.getTransformColumns().size());
        assertEquals(1, enhanced.getPrimaryKeyColumns().size());
        TransformColumn keyColumn = enhanced.getPrimaryKeyColumns().get(0);
        assertEquals("id", keyColumn.getSourceColumnName());
        assertEquals(IncludeOnType.ALL, keyColumn.getIncludeOn());
        assertTrue(keyColumn.isPk());
        TransformColumn valueColumn = enhanced.getTransformColumnTo("name").get(0);
        assertEquals(IncludeOnType.ALL, valueColumn.getIncludeOn());
        assertFalse(valueColumn.isPk());
    }

    @Test
    void testEnhanceWithImpliedColumns_doesNotDuplicateAlreadyCoveredColumns() {
        TransformColumn existingKeyColumn = new TransformColumn("id", "id", true);
        existingKeyColumn.setIncludeOn(IncludeOnType.ALL);
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.EXTRACT, existingKeyColumn);
        TransformTable enhanced = table.enhanceWithImpliedColumns(new String[] { "id" }, new String[0]);
        assertEquals(1, enhanced.getTransformColumns().size());
        assertEquals(1, enhanced.getPrimaryKeyColumns().size());
    }

    @Test
    void testEnhanceWithImpliedColumns_fillsInMissingIncludeOnTypesForPartiallyCoveredKey() {
        TransformColumn insertOnlyKeyColumn = new TransformColumn("id", "id", true);
        insertOnlyKeyColumn.setIncludeOn(IncludeOnType.INSERT);
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.EXTRACT, insertOnlyKeyColumn);
        TransformTable enhanced = table.enhanceWithImpliedColumns(new String[] { "id" }, new String[0]);
        List<TransformColumn> idColumns = enhanced.getTransformColumnFor("id");
        assertEquals(3, idColumns.size());
    }

    @Test
    void testEnhanceWithImpliedColumns_withSpecifiedColumnPolicy_addsNothing() {
        TransformTable table = new TransformTable("SRC", "TGT", TransformPoint.EXTRACT);
        table.setColumnPolicy(ColumnPolicy.SPECIFIED);
        TransformTable enhanced = table.enhanceWithImpliedColumns(new String[] { "id" }, new String[] { "name" });
        assertTrue(enhanced.getTransformColumns().isEmpty());
        assertNotSame(table, enhanced);
    }
}
