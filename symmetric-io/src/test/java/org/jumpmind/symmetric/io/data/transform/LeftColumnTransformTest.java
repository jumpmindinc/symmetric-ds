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
import java.util.stream.Stream;

import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LeftColumnTransformTest {
    private LeftColumnTransform leftColumnTransform;
    private TransformColumn transformColumn;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        leftColumnTransform = new LeftColumnTransform();
        transformColumn = new TransformColumn();
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("left", leftColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(leftColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(leftColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withInsertAndExpression_truncatesNewValueToLength() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("3");
        TransformedData data = new TransformedData(null, DataEventType.INSERT, null, null, null);
        NewAndOldValue result = leftColumnTransform.transform(null, null, transformColumn, data, sourceValues, "abcdef", null);
        assertEquals("abc", result.getNewValue());
        assertNull(result.getOldValue());
    }

    @ParameterizedTest(name = "expression={0}, value=\"{1}\"")
    @MethodSource("provideUnchangedValueTransformCases")
    void testTransform_returnsValueUnchanged(String expression, String value, String expectedValue) throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression(expression);
        TransformedData data = new TransformedData(null, DataEventType.INSERT, null, null, null);
        NewAndOldValue result = leftColumnTransform.transform(null, null, transformColumn, data, sourceValues, value, null);
        assertEquals(expectedValue, result.getNewValue());
    }

    private static Stream<Arguments> provideUnchangedValueTransformCases() {
        return Stream.of(
                Arguments.of("5", "ab", "ab"),
                Arguments.of(null, "abcdef", "abcdef"),
                Arguments.of("2", "", ""));
    }

    @Test
    void testTransform_withDeleteEventType_usesOldValue() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("2");
        Map<String, String> oldSourceValues = new HashMap<>();
        TransformedData data = new TransformedData(null, DataEventType.DELETE, null, oldSourceValues, null);
        NewAndOldValue result = leftColumnTransform.transform(null, null, transformColumn, data, sourceValues, null, "xyz");
        assertEquals("xy", result.getOldValue());
        assertNull(result.getNewValue());
    }
}
