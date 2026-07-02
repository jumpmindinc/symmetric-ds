/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

class IsNullTransformTest extends AbstractTransformTest {
    private IDatabasePlatform platform;
    private DataContext dataContext;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private IsNullTransform isNullTransform;

    @BeforeEach
    void setup() {
        isNullTransform = new IsNullTransform();
        platform = mock(IDatabasePlatform.class);
        dataContext = mock(DataContext.class);
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        transformedData = mock(TransformedData.class);
    }

    @Test
    void testGetName() {
        assertEquals("isNull", isNullTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(isNullTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(isNullTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withDelete_returnsOldValueAsNewValue() throws IgnoreColumnException, IgnoreRowException {
        String oldValue = "old";
        testTransformDelete(Expected.of(NewValue.of(oldValue), null), null, OldValue.of(oldValue), TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withDelete_returnsExpressionWhenNewAndOldValueAreNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformDelete(Expected.of(NewValue.of(expression), null), null, null, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withDelete_returnsEmptyWhenOldValueAndExpressionAreNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformDelete(Expected.of(NewValue.of(""), null), null, null, TransformExpression.of(null));
    }

    @Test
    void testTransform_withUpdate_returnsNewValueWhenNotNull() throws IgnoreColumnException, IgnoreRowException {
        String newValue = "new";
        testTransformUpdate(Expected.of(NewValue.of(newValue), null), NewValue.of(newValue), OldValue.of("old"), TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withUpdate_returnsExpressionWhenNewValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformUpdate(Expected.of(NewValue.of(expression), null), null, OldValue.of("old"), TransformExpression.of(expression));
    }

    @Test
    void testTransform_withUpdate_returnsEmptyWhenNewValueAndExpressionAreNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformUpdate(Expected.of(NewValue.of(""), null), null, OldValue.of("old"), TransformExpression.of(null));
    }

    @Test
    void testTransform_withInsert_returnsNewValueWhenNotNull() throws IgnoreColumnException, IgnoreRowException {
        String newValue = "new";
        testTransformInsert(Expected.of(NewValue.of(newValue), null), NewValue.of(newValue), null, TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withInsert_returnsExpressionWhenNewValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformInsert(Expected.of(NewValue.of(expression), null), null, null, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withInsert_returnsEmptyWhenNewValueAndExpressionAreNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformInsert(Expected.of(NewValue.of(""), null), null, null, TransformExpression.of(null));
    }

    @Test
    void testTransform_withVariableExpression_resolvesTimestamp() throws IgnoreColumnException, IgnoreRowException {
        when(transformColumn.getTransformExpression()).thenReturn("$(" + TransformVariableUtils.OPTION_TIMESTAMP + ")");
        when(transformedData.getSourceDmlType()).thenReturn(DataEventType.INSERT);
        NewAndOldValue result = isNullTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertNotNull(result.newValue);
        assertTrue(result.newValue.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
    }

    @Test
    void testTransform_withVariableNullExpression_returnsNull() throws IgnoreColumnException, IgnoreRowException {
        when(transformColumn.getTransformExpression()).thenReturn("$(" + TransformVariableUtils.OPTION_NULL + ")");
        when(transformedData.getSourceDmlType()).thenReturn(DataEventType.INSERT);
        NewAndOldValue result = isNullTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals(null, result.newValue);
    }

    @Test
    void testTransform_withPlainExpression_usesLiteralValue() throws IgnoreColumnException, IgnoreRowException {
        when(transformColumn.getTransformExpression()).thenReturn("DEFAULT");
        when(transformedData.getSourceDmlType()).thenReturn(DataEventType.INSERT);
        NewAndOldValue result = isNullTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("DEFAULT", result.newValue);
    }

    private void testTransformDelete(Expected expected, NewValue newValue, OldValue oldValue, TransformExpression transformExpression)
            throws IgnoreColumnException, IgnoreRowException {
        testTransform(expected, DataEventType.DELETE, newValue, oldValue, transformExpression);
    }

    private void testTransformUpdate(Expected expected, NewValue newValue, OldValue oldValue, TransformExpression transformExpression)
            throws IgnoreColumnException, IgnoreRowException {
        testTransform(expected, DataEventType.UPDATE, newValue, oldValue, transformExpression);
    }

    private void testTransformInsert(Expected expected, NewValue newValue, OldValue oldValue, TransformExpression transformExpression)
            throws IgnoreColumnException, IgnoreRowException {
        testTransform(expected, DataEventType.INSERT, newValue, oldValue, transformExpression);
    }

    private void testTransform(Expected expected,
            DataEventType eventType,
            NewValue newValue,
            OldValue oldValue, TransformExpression transformExpression) throws IgnoreColumnException, IgnoreRowException {
        when(transformColumn.getTransformExpression()).thenReturn(transformExpression.get());
        when(transformedData.getSourceDmlType()).thenReturn(eventType);
        NewAndOldValue result = isNullTransform.transform(
                platform,
                dataContext,
                transformColumn,
                transformedData,
                sourceValues,
                ((newValue != null) ? newValue.get() : null),
                ((oldValue != null) ? oldValue.get() : null));
        expected.assertMatches(result.newValue, result.oldValue);
    }
}
