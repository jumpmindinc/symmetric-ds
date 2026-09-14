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

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConstantColumnTransformTest {
    private ConstantColumnTransform constantColumnTransform;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        constantColumnTransform = new ConstantColumnTransform();
        transformColumn = new TransformColumn();
        transformedData = new TransformedData(null, DataEventType.INSERT, null, null, null);
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("const", constantColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform_returnsTrue() {
        assertTrue(constantColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform_returnsTrue() {
        assertTrue(constantColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_returnsTransformExpressionRegardlessOfInputValues() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("CONSTANT_VALUE");
        NewAndOldValue result = constantColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "ignored_new", "ignored_old");
        assertEquals("CONSTANT_VALUE", result.getNewValue());
    }

    @Test
    void testTransform_withNullExpression_returnsNull() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression(null);
        NewAndOldValue result = constantColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "new", "old");
        assertNull(result.getNewValue());
    }
}
