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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VariableColumnTransformTest {
    private IDatabasePlatform platform;
    private DataContext dataContext;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private VariableColumnTransform variableColumnTransform;

    @BeforeEach
    void setup() {
        variableColumnTransform = new VariableColumnTransform();
        platform = mock(IDatabasePlatform.class);
        dataContext = mock(DataContext.class);
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        transformedData = mock(TransformedData.class);
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.INSERT);
    }

    @Test
    void testGetName() {
        assertEquals("variable", variableColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(variableColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(variableColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withOldValueOption_resolvesToOldValue() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn(TransformVariableUtils.OPTION_OLD_VALUE);
        NewAndOldValue result = variableColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null,
                "original");
        assertEquals("original", result.getNewValue());
    }

    @Test
    void testTransform_withNullOption_resolvesToNull() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn(TransformVariableUtils.OPTION_NULL);
        NewAndOldValue result = variableColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null,
                "anything");
        assertNull(result.getNewValue());
    }

    @Test
    void testTransform_withUnrecognizedExpression_resolvesToNull() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn("not_a_variable");
        NewAndOldValue result = variableColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null,
                "original");
        assertNull(result.getNewValue());
    }

    @Test
    void testTransform_withPkColumnDuringUpdate_setsBothNewAndOldValue() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn(TransformVariableUtils.OPTION_OLD_VALUE);
        when(transformColumn.isPk()).thenReturn(true);
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.UPDATE);
        NewAndOldValue result = variableColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null,
                "pkVal");
        assertEquals("pkVal", result.getNewValue());
        assertEquals("pkVal", result.getOldValue());
    }

    @Test
    void testTransform_withDeleteAndOldSourceValues_setsOldValueOnly() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn(TransformVariableUtils.OPTION_OLD_VALUE);
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.DELETE);
        when(transformedData.getOldSourceValues()).thenReturn(new HashMap<>());
        NewAndOldValue result = variableColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null,
                "delVal");
        assertEquals("delVal", result.getOldValue());
        assertNull(result.getNewValue());
    }
}
