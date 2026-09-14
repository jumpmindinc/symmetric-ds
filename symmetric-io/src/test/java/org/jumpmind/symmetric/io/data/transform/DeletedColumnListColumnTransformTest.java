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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedColumnListColumnTransformTest {
    private DeletedColumnListColumnTransform deletedColumnListColumnTransform;
    private TransformColumn transformColumn;

    @BeforeEach
    void setup() {
        deletedColumnListColumnTransform = new DeletedColumnListColumnTransform();
        transformColumn = new TransformColumn();
    }

    @Test
    void testGetName() {
        assertEquals("deletedColumns", deletedColumnListColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform_returnsTrue() {
        assertTrue(deletedColumnListColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform_returnsTrue() {
        assertTrue(deletedColumnListColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withNonUpdateEvent_returnsEmptyString() throws IgnoreColumnException, IgnoreRowException {
        Map<String, String> sourceValues = new HashMap<>();
        sourceValues.put("COL1", null);
        Map<String, String> oldSourceValues = new HashMap<>();
        oldSourceValues.put("COL1", "old");
        TransformedData data = new TransformedData(null, DataEventType.INSERT, null, oldSourceValues, sourceValues);
        NewAndOldValue result = deletedColumnListColumnTransform.transform(null, null, transformColumn, data, sourceValues, null, null);
        assertEquals("", result.getNewValue());
    }

    @Test
    void testTransform_withUpdateEventAndOneDeletedColumn_returnsLowercasedColumnName() throws IgnoreColumnException, IgnoreRowException {
        Map<String, String> sourceValues = new HashMap<>();
        sourceValues.put("COL1", null);
        Map<String, String> oldSourceValues = new HashMap<>();
        oldSourceValues.put("COL1", "old_value");
        TransformedData data = new TransformedData(null, DataEventType.UPDATE, null, oldSourceValues, sourceValues);
        NewAndOldValue result = deletedColumnListColumnTransform.transform(null, null, transformColumn, data, sourceValues, null, null);
        assertEquals("col1", result.getNewValue());
    }

    @Test
    void testTransform_withUpdateEventAndMultipleDeletedColumns_joinsWithComma() throws IgnoreColumnException, IgnoreRowException {
        Map<String, String> sourceValues = new HashMap<>();
        sourceValues.put("COL1", null);
        sourceValues.put("COL2", null);
        Map<String, String> oldSourceValues = new HashMap<>();
        oldSourceValues.put("COL1", "old_value1");
        oldSourceValues.put("COL2", "old_value2");
        TransformedData data = new TransformedData(null, DataEventType.UPDATE, null, oldSourceValues, sourceValues);
        NewAndOldValue result = deletedColumnListColumnTransform.transform(null, null, transformColumn, data, sourceValues, null, null);
        assertTrue(result.getNewValue().contains("col1"));
        assertTrue(result.getNewValue().contains("col2"));
        assertEquals(2, result.getNewValue().split(",").length);
    }

    @Test
    void testTransform_withUpdateEventAndNoDeletedColumns_returnsEmptyString() throws IgnoreColumnException, IgnoreRowException {
        Map<String, String> sourceValues = new HashMap<>();
        sourceValues.put("COL1", "still_present");
        Map<String, String> oldSourceValues = new HashMap<>();
        oldSourceValues.put("COL1", "old_value");
        TransformedData data = new TransformedData(null, DataEventType.UPDATE, null, oldSourceValues, sourceValues);
        NewAndOldValue result = deletedColumnListColumnTransform.transform(null, null, transformColumn, data, sourceValues, null, null);
        assertEquals("", result.getNewValue());
    }
}
