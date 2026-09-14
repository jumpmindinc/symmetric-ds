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

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CopyIfChangedColumnTransformTest {
    private CopyIfChangedColumnTransform copyIfChangedColumnTransform;
    private TransformColumn transformColumn;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        copyIfChangedColumnTransform = new CopyIfChangedColumnTransform();
        transformColumn = new TransformColumn();
        sourceValues = new HashMap<>();
    }

    private DataContext contextWithEventType(DataEventType eventType) {
        DataContext context = new DataContext();
        context.setData(new CsvData(eventType));
        return context;
    }

    @Test
    void testGetName() {
        assertEquals("copyIfChanged", copyIfChangedColumnTransform.getName());
    }

    @Test
    void testTransform_whenValuesUnchangedAndNotDelete_throwsIgnoreRowException() {
        DataContext context = contextWithEventType(DataEventType.UPDATE);
        assertThrows(IgnoreRowException.class, () -> copyIfChangedColumnTransform.transform(
                null, context, transformColumn, null, sourceValues, "same", "same"));
    }

    @Test
    void testTransform_whenValuesUnchangedAndExpressionIsIgnoreColumn_throwsIgnoreColumnException() {
        transformColumn.setTransformExpression("IgnoreColumn");
        DataContext context = contextWithEventType(DataEventType.UPDATE);
        assertThrows(IgnoreColumnException.class, () -> copyIfChangedColumnTransform.transform(
                null, context, transformColumn, null, sourceValues, "same", "same"));
    }

    @Test
    void testTransform_whenValuesUnchangedAndExpressionIsIgnoreColumnCaseInsensitive_throwsIgnoreColumnException() {
        transformColumn.setTransformExpression("ignorecolumn");
        DataContext context = contextWithEventType(DataEventType.UPDATE);
        assertThrows(IgnoreColumnException.class, () -> copyIfChangedColumnTransform.transform(
                null, context, transformColumn, null, sourceValues, "same", "same"));
    }

    @Test
    void testTransform_whenValuesChanged_returnsNewAndOldValue() throws IgnoreColumnException, IgnoreRowException {
        DataContext context = contextWithEventType(DataEventType.UPDATE);
        NewAndOldValue result = copyIfChangedColumnTransform.transform(null, context, transformColumn, null, sourceValues, "new", "old");
        assertEquals("new", result.getNewValue());
        assertEquals("old", result.getOldValue());
    }

    @Test
    void testTransform_whenDeleteEventAndValuesUnchanged_returnsNewAndOldValue() throws IgnoreColumnException, IgnoreRowException {
        DataContext context = contextWithEventType(DataEventType.DELETE);
        NewAndOldValue result = copyIfChangedColumnTransform.transform(null, context, transformColumn, null, sourceValues, "same", "same");
        assertEquals("same", result.getNewValue());
        assertEquals("same", result.getOldValue());
    }

    @Test
    void testTransform_withBothValuesNull_treatsAsUnchangedAndThrowsIgnoreRowException() {
        DataContext context = contextWithEventType(DataEventType.UPDATE);
        assertThrows(IgnoreRowException.class, () -> copyIfChangedColumnTransform.transform(
                null, context, transformColumn, null, sourceValues, null, null));
    }
}
