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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;
import org.junit.jupiter.api.Test;

class TransformedDataTest {
    private TransformTable newTransformTable() {
        TransformTable table = new TransformTable();
        table.setSourceCatalogName("SRC_CAT");
        table.setSourceSchemaName("SRC_SCH");
        table.setSourceTableName("SRC_TBL");
        table.setTargetCatalogName("TGT_CAT");
        table.setTargetSchemaName("TGT_SCH");
        table.setTargetTableName("TGT_TBL");
        return table;
    }

    @Test
    void testConstructor_setsSourceAndTargetDmlTypeFromSourceDmlType() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        assertEquals(DataEventType.INSERT, data.getSourceDmlType());
        assertEquals(DataEventType.INSERT, data.getTargetDmlType());
    }

    @Test
    void testSetTargetDmlType_overridesTargetIndependentlyOfSource() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.UPDATE, null, null, null);
        data.setTargetDmlType(DataEventType.INSERT);
        assertEquals(DataEventType.UPDATE, data.getSourceDmlType());
        assertEquals(DataEventType.INSERT, data.getTargetDmlType());
    }

    @Test
    void testTableNameGetters_delegateToTransformation() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        assertEquals("TGT_TBL", data.getTableName());
        assertEquals("TGT_CAT", data.getCatalogName());
        assertEquals("TGT_SCH", data.getSchemaName());
        assertEquals("TGT_CAT.TGT_SCH.TGT_TBL", data.getFullyQualifiedTableName());
    }

    @Test
    void testSourceGetters_returnConstructorValues() {
        Map<String, String> sourceKeyValues = new HashMap<String, String>();
        sourceKeyValues.put("id", "1");
        Map<String, String> oldSourceValues = new HashMap<String, String>();
        oldSourceValues.put("name", "old");
        Map<String, String> sourceValues = new HashMap<String, String>();
        sourceValues.put("name", "new");
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.UPDATE, sourceKeyValues, oldSourceValues, sourceValues);
        assertEquals(sourceKeyValues, data.getSourceKeyValues());
        assertEquals(oldSourceValues, data.getOldSourceValues());
        assertEquals(sourceValues, data.getSourceValues());
    }

    @Test
    void testGetSetGeneratedIdentityNeeded() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        assertFalse(data.isGeneratedIdentityNeeded());
        data.setGeneratedIdentityNeeded(true);
        assertTrue(data.isGeneratedIdentityNeeded());
    }

    @Test
    void testGetSetTargetAction() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        assertNull(data.getTargetAction());
        data.setTargetAction(TargetDmlAction.INS_ROW);
        assertEquals(TargetDmlAction.INS_ROW, data.getTargetAction());
    }

    @Test
    void testGetTransformation_returnsConstructorValue() {
        TransformTable table = newTransformTable();
        TransformedData data = new TransformedData(table, DataEventType.INSERT, null, null, null);
        assertEquals(table, data.getTransformation());
    }

    @Test
    void testPutAndGetTargetValues_forInsert_usesInsertAndAllIncludeOnTypes() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn allColumn = new TransformColumn("srcAll", "tgtAll", false);
        allColumn.setIncludeOn(IncludeOnType.ALL);
        TransformColumn insertColumn = new TransformColumn("srcIns", "tgtIns", false);
        insertColumn.setIncludeOn(IncludeOnType.INSERT);
        TransformColumn deleteColumn = new TransformColumn("srcDel", "tgtDel", false);
        deleteColumn.setIncludeOn(IncludeOnType.DELETE);
        data.put(allColumn, "allVal", "oldAllVal", false);
        data.put(insertColumn, "insVal", "oldInsVal", false);
        data.put(deleteColumn, "delVal", "oldDelVal", false);
        Map<String, String> targetValues = data.getTargetValues();
        assertEquals("allVal", targetValues.get("tgtAll"));
        assertEquals("insVal", targetValues.get("tgtIns"));
        assertFalse(targetValues.containsKey("tgtDel"));
    }

    @Test
    void testPutAndGetTargetValues_forDelete_usesDeleteAndAllIncludeOnTypes() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.DELETE, null, null, null);
        TransformColumn deleteColumn = new TransformColumn("srcDel", "tgtDel", false);
        deleteColumn.setIncludeOn(IncludeOnType.DELETE);
        TransformColumn insertColumn = new TransformColumn("srcIns", "tgtIns", false);
        insertColumn.setIncludeOn(IncludeOnType.INSERT);
        data.put(deleteColumn, "delVal", null, false);
        data.put(insertColumn, "insVal", null, false);
        Map<String, String> targetValues = data.getTargetValues();
        assertEquals("delVal", targetValues.get("tgtDel"));
        assertFalse(targetValues.containsKey("tgtIns"));
    }

    @Test
    void testPutAndGetTargetValues_forUpdateWithNonDeleteSource_usesUpdateIncludeOnType() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.UPDATE, null, null, null);
        TransformColumn updateColumn = new TransformColumn("srcUpd", "tgtUpd", false);
        updateColumn.setIncludeOn(IncludeOnType.UPDATE);
        data.put(updateColumn, "updVal", null, false);
        assertEquals("updVal", data.getTargetValues().get("tgtUpd"));
    }

    @Test
    void testPutAndGetTargetValues_forUpdateWithDeleteSource_usesDeleteIncludeOnType() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.DELETE, null, null, null);
        data.setTargetDmlType(DataEventType.UPDATE);
        TransformColumn deleteColumn = new TransformColumn("srcDel", "tgtDel", false);
        deleteColumn.setIncludeOn(IncludeOnType.DELETE);
        data.put(deleteColumn, "delVal", null, false);
        assertEquals("delVal", data.getTargetValues().get("tgtDel"));
    }

    @Test
    void testPutWithRecordAsKey_populatesTargetKeyValuesUsingOldValueWhenPresent() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.UPDATE, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "newVal", "oldVal", true);
        assertEquals("oldVal", data.getTargetKeyValues().get("tgtId"));
    }

    @Test
    void testPutWithRecordAsKey_usesColumnValueWhenOldValueIsNull() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "newVal", null, true);
        assertEquals("newVal", data.getTargetKeyValues().get("tgtId"));
    }

    @Test
    void testPutWithoutRecordAsKey_leavesTargetKeyValuesEmpty() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn column = new TransformColumn("src", "tgt", false);
        column.setIncludeOn(IncludeOnType.ALL);
        data.put(column, "val", null, false);
        assertTrue(data.getTargetKeyValues().isEmpty());
    }

    @Test
    void testGetColumnNamesAndValues_returnInInsertionOrder() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn first = new TransformColumn("srcA", "tgtA", false);
        first.setIncludeOn(IncludeOnType.ALL);
        TransformColumn second = new TransformColumn("srcB", "tgtB", false);
        second.setIncludeOn(IncludeOnType.ALL);
        data.put(first, "valA", null, false);
        data.put(second, "valB", null, false);
        assertArrayEquals(new String[] { "tgtA", "tgtB" }, data.getColumnNames());
        assertArrayEquals(new String[] { "valA", "valB" }, data.getColumnValues());
    }

    @Test
    void testGetKeyNamesAndValues_reflectRecordedKeys() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "1", null, true);
        assertArrayEquals(new String[] { "tgtId" }, data.getKeyNames());
        assertArrayEquals(new String[] { "1" }, data.getKeyValues());
    }

    @Test
    void testGetOldColumnValues_returnsNullWhenAllOldValuesAreNull() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn column = new TransformColumn("src", "tgt", false);
        column.setIncludeOn(IncludeOnType.ALL);
        data.put(column, "val", null, false);
        assertNull(data.getOldColumnValues());
    }

    @Test
    void testGetOldColumnValues_returnsArrayWhenAtLeastOneOldValueIsPresent() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.UPDATE, null, null, null);
        TransformColumn column = new TransformColumn("src", "tgt", false);
        column.setIncludeOn(IncludeOnType.ALL);
        data.put(column, "newVal", "oldVal", false);
        assertArrayEquals(new String[] { "oldVal" }, data.getOldColumnValues());
    }

    @Test
    void testCopy_isIndependentDeepCopyOfTargetMaps() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn column = new TransformColumn("src", "tgt", true);
        column.setIncludeOn(IncludeOnType.ALL);
        data.put(column, "val", null, true);
        TransformedData copy = data.copy();
        assertNotSame(data, copy);
        assertEquals("val", copy.getTargetValues().get("tgt"));
        TransformColumn secondColumn = new TransformColumn("src2", "tgt2", false);
        secondColumn.setIncludeOn(IncludeOnType.ALL);
        copy.put(secondColumn, "val2", null, false);
        assertFalse(data.getTargetValues().containsKey("tgt2"));
    }

    @Test
    void testHasSameKeyValues_withEqualArrays_isTrue() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "1", null, true);
        assertTrue(data.hasSameKeyValues(new String[] { "1" }));
    }

    @Test
    void testHasSameKeyValues_withDifferentLengths_isFalse() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "1", null, true);
        assertFalse(data.hasSameKeyValues(new String[] { "1", "2" }));
    }

    @Test
    void testHasSameKeyValues_withDifferentValues_isFalse() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "1", null, true);
        assertFalse(data.hasSameKeyValues(new String[] { "2" }));
    }

    @Test
    void testHasSameKeyValues_withNoKeysRecordedAndEmptyOtherArray_isTrue() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        assertTrue(data.hasSameKeyValues(new String[0]));
    }

    @Test
    void testHasSameKeyValues_withNoKeysRecordedAndNullOtherArray_isFalse() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        assertFalse(data.hasSameKeyValues(null));
    }

    @Test
    void testHasSameKeyValues_withOtherNullButHasKeys_isFalse() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "1", null, true);
        assertFalse(data.hasSameKeyValues(null));
    }

    @Test
    void testBuildTargetTable_marksKeyColumnsAsPrimaryKey() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        TransformColumn valueColumn = new TransformColumn("srcName", "tgtName", false);
        valueColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "1", null, true);
        data.put(valueColumn, "someName", null, false);
        Table table = data.buildTargetTable();
        assertEquals("TGT_TBL", table.getName());
        Column idColumn = table.findColumn("tgtId");
        assertTrue(idColumn.isPrimaryKey());
        Column nameColumn = table.findColumn("tgtName");
        assertFalse(nameColumn.isPrimaryKey());
    }

    @Test
    void testBuildTargetTable_withNoColumns_returnsNull() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        assertNull(data.buildTargetTable());
    }

    @Test
    void testBuildTargetCsvData_forInsert_setsRowDataOnly() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.INSERT, null, null, null);
        TransformColumn column = new TransformColumn("srcName", "tgtName", false);
        column.setIncludeOn(IncludeOnType.ALL);
        data.put(column, "someName", null, false);
        CsvData csvData = data.buildTargetCsvData(null);
        assertArrayEquals(new String[] { "someName" }, csvData.getParsedData(CsvData.ROW_DATA));
        assertEquals("SRC_TBL", csvData.getAttribute(CsvData.ATTRIBUTE_TABLE_NAME));
    }

    @Test
    void testBuildTargetCsvData_forUpdate_setsRowOldAndPkData() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.UPDATE, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        TransformColumn valueColumn = new TransformColumn("srcName", "tgtName", false);
        valueColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "1", "1", true);
        data.put(valueColumn, "newName", "oldName", false);
        CsvData csvData = data.buildTargetCsvData(null);
        assertArrayEquals(new String[] { "1", "newName" }, csvData.getParsedData(CsvData.ROW_DATA));
        assertArrayEquals(new String[] { "1", "oldName" }, csvData.getParsedData(CsvData.OLD_DATA));
        assertArrayEquals(new String[] { "1" }, csvData.getParsedData(CsvData.PK_DATA));
    }

    @Test
    void testBuildTargetCsvData_forDelete_setsOldAndPkDataButNotRowData() {
        TransformedData data = new TransformedData(newTransformTable(), DataEventType.DELETE, null, null, null);
        TransformColumn keyColumn = new TransformColumn("srcId", "tgtId", true);
        keyColumn.setIncludeOn(IncludeOnType.ALL);
        data.put(keyColumn, "1", "1", true);
        CsvData csvData = data.buildTargetCsvData(null);
        assertNull(csvData.getParsedData(CsvData.ROW_DATA));
        assertArrayEquals(new String[] { "1" }, csvData.getParsedData(CsvData.PK_DATA));
    }
}
