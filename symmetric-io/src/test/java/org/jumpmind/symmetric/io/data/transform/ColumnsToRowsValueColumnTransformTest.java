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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ColumnsToRowsValueColumnTransformTest {
    private ColumnsToRowsValueColumnTransform columnsToRowsValueColumnTransform;
    private DataContext dataContext;
    private TransformColumn transformColumn;
    private String contextBase;

    @BeforeEach
    void setup() {
        columnsToRowsValueColumnTransform = new ColumnsToRowsValueColumnTransform();
        dataContext = new DataContext();
        transformColumn = new TransformColumn();
        transformColumn.setTransformId("t1");
        contextBase = ColumnsToRowsKeyColumnTransform.getContextBase("t1");
    }

    private void putReverseMapAndPkColumn(Map<String, String> reverseMap, String pkColumnName) {
        dataContext.put(contextBase + ColumnsToRowsKeyColumnTransform.CONTEXT_MAP, reverseMap);
        dataContext.put(contextBase + ColumnsToRowsKeyColumnTransform.CONTEXT_PK_COLUMN, pkColumnName);
    }

    private TransformedData buildData(DataEventType sourceDmlType, String pkColumnName, String pkValue,
            Map<String, String> sourceValues, Map<String, String> oldSourceValues) {
        TransformedData data = new TransformedData(null, sourceDmlType, null, oldSourceValues, sourceValues);
        TransformColumn pkColumn = new TransformColumn();
        pkColumn.setTargetColumnName(pkColumnName);
        data.put(pkColumn, pkValue, null, true);
        return data;
    }

    @Test
    void testGetName() {
        assertEquals("columnsToRowsValue", columnsToRowsValueColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform_returnsTrue() {
        assertTrue(columnsToRowsValueColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform_returnsTrue() {
        assertTrue(columnsToRowsValueColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withNoReverseMapInContext_throwsRuntimeException() {
        TransformedData data = buildData(DataEventType.INSERT, "PK_COL", "pk1", new HashMap<>(), null);
        assertThrows(RuntimeException.class, () -> columnsToRowsValueColumnTransform.transform(
                null, dataContext, transformColumn, data, new HashMap<>(), null, null));
    }

    @Test
    void testTransform_withNoPkColumnInContext_throwsRuntimeException() {
        dataContext.put(contextBase + ColumnsToRowsKeyColumnTransform.CONTEXT_MAP, new HashMap<String, String>());
        TransformedData data = buildData(DataEventType.INSERT, "PK_COL", "pk1", new HashMap<>(), null);
        assertThrows(RuntimeException.class, () -> columnsToRowsValueColumnTransform.transform(
                null, dataContext, transformColumn, data, new HashMap<>(), null, null));
    }

    @Test
    void testTransform_withNoMatchingTargetKeyValue_throwsRuntimeException() {
        Map<String, String> reverseMap = new HashMap<>();
        reverseMap.put("pk1", "col1");
        putReverseMapAndPkColumn(reverseMap, "PK_COL");
        TransformedData data = buildData(DataEventType.INSERT, "OTHER_COL", "pk1", new HashMap<>(), null);
        assertThrows(RuntimeException.class, () -> columnsToRowsValueColumnTransform.transform(
                null, dataContext, transformColumn, data, new HashMap<>(), null, null));
    }

    @Test
    void testTransform_withUnmappedPkValue_throwsRuntimeException() {
        Map<String, String> reverseMap = new HashMap<>();
        reverseMap.put("pk1", "col1");
        putReverseMapAndPkColumn(reverseMap, "PK_COL");
        TransformedData data = buildData(DataEventType.INSERT, "PK_COL", "unmapped", new HashMap<>(), null);
        assertThrows(RuntimeException.class, () -> columnsToRowsValueColumnTransform.transform(
                null, dataContext, transformColumn, data, new HashMap<>(), null, null));
    }

    @Test
    void testTransform_onInsert_returnsSourceValueForMappedColumn() throws IgnoreRowException, IgnoreColumnException {
        Map<String, String> reverseMap = new HashMap<>();
        reverseMap.put("pk1", "col1");
        putReverseMapAndPkColumn(reverseMap, "PK_COL");
        Map<String, String> sourceValues = new HashMap<>();
        sourceValues.put("col1", "val1");
        TransformedData data = buildData(DataEventType.INSERT, "PK_COL", "pk1", sourceValues, null);
        String result = columnsToRowsValueColumnTransform.transform(null, dataContext, transformColumn, data, sourceValues, null, null);
        assertEquals("val1", result);
    }

    @Test
    void testTransform_withIgnoreNullsOnInsertAndNullSourceValue_throwsIgnoreRowException() {
        transformColumn.setTransformExpression("ignoreNulls=true");
        Map<String, String> reverseMap = new HashMap<>();
        reverseMap.put("pk1", "col1");
        putReverseMapAndPkColumn(reverseMap, "PK_COL");
        Map<String, String> sourceValues = new HashMap<>();
        sourceValues.put("col1", null);
        TransformedData data = buildData(DataEventType.INSERT, "PK_COL", "pk1", sourceValues, null);
        assertThrows(IgnoreRowException.class, () -> columnsToRowsValueColumnTransform.transform(
                null, dataContext, transformColumn, data, sourceValues, null, null));
    }

    @Test
    void testTransform_withChangesOnlyOnUpdateAndUnchangedValue_throwsIgnoreRowException() {
        transformColumn.setTransformExpression("changesOnly=true");
        Map<String, String> reverseMap = new HashMap<>();
        reverseMap.put("pk1", "col1");
        putReverseMapAndPkColumn(reverseMap, "PK_COL");
        Map<String, String> sourceValues = new HashMap<>();
        sourceValues.put("col1", "same");
        Map<String, String> oldSourceValues = new HashMap<>();
        oldSourceValues.put("col1", "same");
        TransformedData data = buildData(DataEventType.UPDATE, "PK_COL", "pk1", sourceValues, oldSourceValues);
        assertThrows(IgnoreRowException.class, () -> columnsToRowsValueColumnTransform.transform(
                null, dataContext, transformColumn, data, sourceValues, null, null));
    }

    @Test
    void testTransform_withIgnoreNullsOnUpdateAndNullNewValue_setsTargetDmlTypeToDelete() throws IgnoreRowException, IgnoreColumnException {
        transformColumn.setTransformExpression("ignoreNulls=true");
        Map<String, String> reverseMap = new HashMap<>();
        reverseMap.put("pk1", "col1");
        putReverseMapAndPkColumn(reverseMap, "PK_COL");
        Map<String, String> sourceValues = new HashMap<>();
        sourceValues.put("col1", null);
        Map<String, String> oldSourceValues = new HashMap<>();
        oldSourceValues.put("col1", "old");
        TransformedData data = buildData(DataEventType.UPDATE, "PK_COL", "pk1", sourceValues, oldSourceValues);
        String result = columnsToRowsValueColumnTransform.transform(null, dataContext, transformColumn, data, sourceValues, null, null);
        assertNull(result);
        assertEquals(DataEventType.DELETE, data.getTargetDmlType());
    }
}
