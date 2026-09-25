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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.service.IExtensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaColumnTransformTest {
    private IDatabasePlatform platform;
    private DataContext dataContext;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private IExtensionService extensionService;
    private JavaColumnTransform javaColumnTransform;

    @BeforeEach
    void setup() {
        javaColumnTransform = new JavaColumnTransform();
        platform = mock(IDatabasePlatform.class);
        dataContext = new DataContext();
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        when(transformColumn.getTransformId()).thenReturn("t1");
        when(transformColumn.getTargetColumnName()).thenReturn("col1");
        when(transformColumn.getIncludeOn()).thenReturn(TransformColumn.IncludeOnType.ALL);
        when(transformColumn.getTransformExpression()).thenReturn("return newValue;");
        transformedData = mock(TransformedData.class);
        extensionService = mock(IExtensionService.class);
        javaColumnTransform.setExtensionService(extensionService);
    }

    @Test
    void testGetName() {
        assertEquals("java", javaColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(javaColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(javaColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_delegatesToCompiledClassAndReturnsResult() throws Exception {
        ISingleValueColumnTransform compiled = mock(ISingleValueColumnTransform.class);
        when(compiled.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "new", "old")).thenReturn("result1");
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        String result = javaColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "new", "old");
        assertEquals("result1", result);
    }

    @Test
    void testTransform_reusesCompiledClassFromContextCache() throws Exception {
        ISingleValueColumnTransform compiled = mock(ISingleValueColumnTransform.class);
        when(compiled.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "new", "old")).thenReturn("result1");
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        javaColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "new", "old");
        javaColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "new", "old");
        verify(extensionService, times(1)).getCompiledClass(anyString());
    }

    @Test
    void testTransform_wrapsCheckedExceptionAsRuntimeException() throws Exception {
        ISingleValueColumnTransform compiled = mock(ISingleValueColumnTransform.class);
        when(compiled.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "new", "old"))
                .thenThrow(new IgnoreRowException());
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> javaColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "new", "old"));
        assertTrue(thrown.getCause() instanceof IgnoreRowException);
    }

    @Test
    void testTransform_wrapsExtensionServiceFailureAsRuntimeException() throws Exception {
        when(extensionService.getCompiledClass(anyString())).thenThrow(new RuntimeException("compile failed"));
        assertThrows(RuntimeException.class,
                () -> javaColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, "new", "old"));
    }

    @Test
    void testGetCompiledClass_buildsDifferentCacheKeysPerColumn() throws Exception {
        ISingleValueColumnTransform compiled = mock(ISingleValueColumnTransform.class);
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        javaColumnTransform.getCompiledClass(dataContext, transformColumn);
        TransformColumn otherColumn = mock(TransformColumn.class);
        when(otherColumn.getTransformId()).thenReturn("t2");
        when(otherColumn.getTargetColumnName()).thenReturn("col2");
        when(otherColumn.getIncludeOn()).thenReturn(TransformColumn.IncludeOnType.ALL);
        when(otherColumn.getTransformExpression()).thenReturn("return newValue;");
        javaColumnTransform.getCompiledClass(dataContext, otherColumn);
        verify(extensionService, times(2)).getCompiledClass(anyString());
    }
}
