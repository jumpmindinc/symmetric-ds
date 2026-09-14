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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.io.data.DataContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ColumnsToRowsKeyColumnTransformTest {
    private ColumnsToRowsKeyColumnTransform columnsToRowsKeyColumnTransform;
    private DataContext dataContext;
    private TransformColumn transformColumn;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        columnsToRowsKeyColumnTransform = new ColumnsToRowsKeyColumnTransform();
        dataContext = new DataContext();
        transformColumn = new TransformColumn();
        transformColumn.setTransformId("t1");
        transformColumn.setTargetColumnName("PK_COL");
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("columnsToRowsKey", columnsToRowsKeyColumnTransform.getName());
    }

    @Test
    void testGetContextBase() {
        assertEquals("columnsToRowsKey:t1:", ColumnsToRowsKeyColumnTransform.getContextBase("t1"));
    }

    @Test
    void testIsExtractColumnTransform_returnsTrue() {
        assertTrue(columnsToRowsKeyColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform_returnsTrue() {
        assertTrue(columnsToRowsKeyColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withBlankExpression_throwsRuntimeException() {
        transformColumn.setTransformExpression(null);
        assertThrows(RuntimeException.class, () -> columnsToRowsKeyColumnTransform.transform(
                null, dataContext, transformColumn, null, sourceValues, null, null));
    }

    @Test
    void testTransform_withEntryMissingEqualsSign_throwsRuntimeException() {
        transformColumn.setTransformExpression("pk1col1");
        assertThrows(RuntimeException.class, () -> columnsToRowsKeyColumnTransform.transform(
                null, dataContext, transformColumn, null, sourceValues, null, null));
    }

    @Test
    void testTransform_parsesMapAndStoresReverseMapAndPkColumnInContext() throws IgnoreRowException {
        transformColumn.setTransformExpression("pk1=col1 pk2=col2");
        List<String> result = columnsToRowsKeyColumnTransform.transform(null, dataContext, transformColumn, null, sourceValues, null, null);
        assertEquals(2, result.size());
        assertTrue(result.contains("col1"));
        assertTrue(result.contains("col2"));
        @SuppressWarnings("unchecked")
        Map<String, String> reverseMap = (Map<String, String>) dataContext.get(
                ColumnsToRowsKeyColumnTransform.getContextBase("t1") + ColumnsToRowsKeyColumnTransform.CONTEXT_MAP);
        assertEquals("pk1", reverseMap.get("col1"));
        assertEquals("pk2", reverseMap.get("col2"));
        assertEquals("PK_COL", dataContext.get(
                ColumnsToRowsKeyColumnTransform.getContextBase("t1") + ColumnsToRowsKeyColumnTransform.CONTEXT_PK_COLUMN));
    }
}
