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

class BinaryLeftColumnTransformTest {
    private BinaryLeftColumnTransform binaryLeftColumnTransform;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        binaryLeftColumnTransform = new BinaryLeftColumnTransform();
        transformColumn = new TransformColumn();
        transformedData = new TransformedData(null, DataEventType.INSERT, null, null, null);
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("bleft", binaryLeftColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform_returnsTrue() {
        assertTrue(binaryLeftColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform_returnsTrue() {
        assertTrue(binaryLeftColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withBlankExpression_returnsNewValueUnchanged() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression(null);
        NewAndOldValue result = binaryLeftColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "hello world", null);
        assertEquals("hello world", result.getNewValue());
    }

    @Test
    void testTransform_withBlankNewValue_returnsNull() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("5");
        NewAndOldValue result = binaryLeftColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, null, null);
        assertNull(result.getNewValue());
    }

    @Test
    void testTransform_truncatesToMaxBytes() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("5");
        NewAndOldValue result = binaryLeftColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "hello world", null);
        assertEquals("hello", result.getNewValue());
    }

    @Test
    void testBleft_withMaxBytesGreaterThanLength_returnsFullString() {
        assertEquals("abc", binaryLeftColumnTransform.bleft("abc", 10));
    }

    @Test
    void testBleft_withZeroMaxBytes_returnsEmptyString() {
        assertEquals("", binaryLeftColumnTransform.bleft("abc", 0));
    }

    @Test
    void testBleft_withMaxBytesEqualToLength_returnsFullString() {
        assertEquals("abc", binaryLeftColumnTransform.bleft("abc", 3));
    }
}
