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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import bsh.Interpreter;

class BshColumnTransformTest {
    private IDatabasePlatform platform;
    private DataContext dataContext;
    private Map<String, Object> contextBackingMap;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private IParameterService parameterService;
    private BshColumnTransform bshColumnTransform;

    @BeforeEach
    void setup() {
        bshColumnTransform = new BshColumnTransform();
        platform = mock(IDatabasePlatform.class);
        dataContext = mock(DataContext.class);
        contextBackingMap = new HashMap<>();
        when(dataContext.get(anyString())).thenAnswer(invocation -> contextBackingMap.get(invocation.getArgument(0, String.class)));
        doAnswer(invocation -> contextBackingMap.put(invocation.getArgument(0, String.class), invocation.getArgument(1))).when(dataContext)
                .put(anyString(), any());
        when(dataContext.getBatch()).thenReturn(new Batch());
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        transformedData = mock(TransformedData.class);
        when(transformedData.getSourceDmlType()).thenReturn(DataEventType.INSERT);
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.INSERT);
        parameterService = mock(IParameterService.class);
        bshColumnTransform.setParameterService(parameterService);
    }

    @Test
    void testGetName() {
        assertEquals("bsh", bshColumnTransform.getName());
    }

    @Test
    void testIsParameterServiceRequired() {
        assertTrue(bshColumnTransform.isParameterServiceRequired());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(bshColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(bshColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withSimpleScript_returnsStringResultAsNewValue() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn("return currentValue + \"_transformed\";");
        NewAndOldValue result = bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null);
        assertEquals("input_transformed", result.newValue);
        assertNull(result.oldValue);
    }

    @Test
    void testTransform_withDeleteAndOldSourceValues_returnsResultAsOldValue() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn("return oldValue + \"_deleted\";");
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.DELETE);
        Map<String, String> oldSourceValues = new HashMap<>();
        oldSourceValues.put("col", "val");
        when(transformedData.getOldSourceValues()).thenReturn(oldSourceValues);
        NewAndOldValue result = bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, "input");
        assertEquals("input_deleted", result.oldValue);
        assertNull(result.newValue);
    }

    @Test
    void testTransform_withNewAndOldValueScriptResult_returnsItDirectly() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn(
                "return new org.jumpmind.symmetric.io.data.transform.NewAndOldValue(\"n\", \"o\");");
        NewAndOldValue result = bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null);
        assertEquals("n", result.newValue);
        assertEquals("o", result.oldValue);
    }

    @Test
    void testTransform_withNonStringNonNewAndOldValueResult_returnsToStringAsNewValue() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn("return new Integer(42);");
        NewAndOldValue result = bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null);
        assertEquals("42", result.newValue);
        assertNull(result.oldValue);
    }

    @Test
    void testTransform_withNullScriptResult_returnsNull() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn(";");
        NewAndOldValue result = bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null);
        assertNull(result);
    }

    @Test
    void testTransform_withEmptyExpression_throwsTransformColumnException() {
        when(transformColumn.getTransformExpression()).thenReturn("");
        when(transformColumn.getTargetColumnName()).thenReturn("col1");
        when(transformColumn.getTransformId()).thenReturn("t1");
        assertThrows(TransformColumnException.class,
                () -> bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null));
    }

    @Test
    void testTransform_withScriptThrowingIgnoreColumnException_propagatesIt() {
        when(transformColumn.getTransformExpression())
                .thenReturn("throw new org.jumpmind.symmetric.io.data.transform.IgnoreColumnException();");
        when(transformColumn.getTargetColumnName()).thenReturn("col1");
        when(transformColumn.getTransformId()).thenReturn("t1");
        assertThrows(IgnoreColumnException.class,
                () -> bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null));
    }

    @Test
    void testTransform_withScriptThrowingIgnoreRowException_propagatesIt() {
        when(transformColumn.getTransformExpression())
                .thenReturn("throw new org.jumpmind.symmetric.io.data.transform.IgnoreRowException();");
        when(transformColumn.getTargetColumnName()).thenReturn("col1");
        when(transformColumn.getTransformId()).thenReturn("t1");
        assertThrows(IgnoreRowException.class,
                () -> bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null));
    }

    @Test
    void testTransform_withMalformedScript_throwsTransformColumnException() {
        when(transformColumn.getTransformExpression()).thenReturn("this is not valid beanshell {{{");
        when(transformColumn.getTargetColumnName()).thenReturn("col1");
        when(transformColumn.getTransformId()).thenReturn("t1");
        assertThrows(TransformColumnException.class,
                () -> bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null));
    }

    @Test
    void testTransform_withGlobalScript_evaluatesGlobalScriptFirst() throws Exception {
        when(parameterService.getString(ParameterConstants.BSH_TRANSFORM_GLOBAL_SCRIPT)).thenReturn("int addOne(int x) { return x + 1; }");
        when(transformColumn.getTransformExpression()).thenReturn("return \"\" + addOne(1);");
        NewAndOldValue result = bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null);
        assertEquals("2", result.newValue);
    }

    @Test
    void testTransform_withSourceValues_setsUppercasedColumnVariables() throws Exception {
        sourceValues.put("mycol", "abc");
        when(transformColumn.getTransformExpression()).thenReturn("return MYCOL + \"_x\";");
        NewAndOldValue result = bshColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "input", null);
        assertEquals("abc_x", result.newValue);
    }

    @Test
    void testGetInterpreter_reusesSameInterpreterForSameContext() {
        Interpreter first = bshColumnTransform.getInterpreter(dataContext);
        Interpreter second = bshColumnTransform.getInterpreter(dataContext);
        assertSame(first, second);
    }
}
