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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParameterColumnTransformTest {
    private IDatabasePlatform platform;
    private DataContext dataContext;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private IParameterService parameterService;
    private ParameterColumnTransform parameterColumnTransform;

    @BeforeEach
    void setup() {
        parameterColumnTransform = new ParameterColumnTransform();
        platform = mock(IDatabasePlatform.class);
        dataContext = mock(DataContext.class);
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        transformedData = mock(TransformedData.class);
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.INSERT);
        parameterService = mock(IParameterService.class);
        parameterColumnTransform.setParameterService(parameterService);
    }

    @Test
    void testGetName() {
        assertEquals("parameter", parameterColumnTransform.getName());
    }

    @Test
    void testIsParameterServiceRequired() {
        assertTrue(parameterColumnTransform.isParameterServiceRequired());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(parameterColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(parameterColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withParamName_returnsParameterValue() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn("my.param");
        when(parameterService.getString("my.param")).thenReturn("paramValue123");
        NewAndOldValue result = parameterColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("paramValue123", result.getNewValue());
    }

    @Test
    void testTransform_withNullParamName_returnsNullValueWithoutCallingParameterService() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn(null);
        NewAndOldValue result = parameterColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertNull(result.getNewValue());
        verify(parameterService, never()).getString(any());
    }

    @Test
    void testTransform_withPkColumnDuringUpdate_setsBothNewAndOldValue() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn("my.param");
        when(transformColumn.isPk()).thenReturn(true);
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.UPDATE);
        when(parameterService.getString("my.param")).thenReturn("pkValue");
        NewAndOldValue result = parameterColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("pkValue", result.getNewValue());
        assertEquals("pkValue", result.getOldValue());
    }

    @Test
    void testTransform_withDeleteAndOldSourceValues_setsOldValueOnly() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn("my.param");
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.DELETE);
        when(transformedData.getOldSourceValues()).thenReturn(new HashMap<>());
        when(parameterService.getString("my.param")).thenReturn("delVal");
        NewAndOldValue result = parameterColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("delVal", result.getOldValue());
        assertNull(result.getNewValue());
    }
}
