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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MathColumnTransformTest {
    private MathColumnTransform mathColumnTransform;
    private DataContext dataContext;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        mathColumnTransform = new MathColumnTransform();
        dataContext = new DataContext();
        dataContext.setBatch(new Batch());
        transformColumn = new TransformColumn();
        transformedData = new TransformedData(null, DataEventType.INSERT, null, null, null);
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("math", mathColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(mathColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(mathColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withWholeNumberResult_truncatesDecimalPlace() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("1+2");
        NewAndOldValue result = mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("3", result.getNewValue());
    }

    @Test
    void testTransform_withFractionalResult_keepsDecimalPlaces() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("1/4");
        NewAndOldValue result = mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("0.25", result.getNewValue());
    }

    @Test
    void testTransform_withCurrentValueVariable_usesNewValue() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("#{currentValue}+5");
        NewAndOldValue result = mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, "10", null);
        assertEquals("15", result.getNewValue());
    }

    @Test
    void testTransform_withOldValueVariable_usesOldValue() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("#{oldValue}*2");
        NewAndOldValue result = mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, null, "4");
        assertEquals("8", result.getNewValue());
    }

    @Test
    void testTransform_withSourceColumnVariable_usesSourceValue() throws IgnoreColumnException, IgnoreRowException {
        sourceValues.put("qty", "3");
        transformColumn.setTransformExpression("#{qty}+2");
        NewAndOldValue result = mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("5", result.getNewValue());
    }

    @Test
    void testTransform_reusesEvaluatorStoredInContext() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("1+1");
        mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertNotNull(dataContext.get(MathColumnTransform.EVALUATOR));
        NewAndOldValue result = mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("2", result.getNewValue());
    }

    @Test
    void testTransform_withResultInScientificNotation_formatsAsPlainDecimal() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("1/100000000");
        NewAndOldValue result = mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertFalse(result.getNewValue().contains("E"));
        assertEquals(1e-8, Double.parseDouble(result.getNewValue()), 1e-15);
    }

    @Test
    void testTransform_withInvalidExpression_throwsRuntimeException() {
        transformColumn.setTransformExpression("#bogusFunction(1)");
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> mathColumnTransform.transform(null, dataContext, transformColumn, transformedData, sourceValues, null, null));
        assertTrue(e.getMessage().startsWith("Unable to evaluate transform expression:"));
    }
}
