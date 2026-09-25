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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubstrColumnTransformTest {
    private SubstrColumnTransform substrColumnTransform;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        substrColumnTransform = new SubstrColumnTransform();
        transformColumn = new TransformColumn();
        transformedData = new TransformedData(null, DataEventType.INSERT, null, null, null);
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("substr", substrColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(substrColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(substrColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withSingleIndexToken_returnsSubstringFromIndex() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("2");
        NewAndOldValue result = substrColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "abcdef", null);
        assertEquals("cdef", result.getNewValue());
    }

    @Test
    void testTransform_withSingleIndexBeyondLength_returnsEmptyString() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("10");
        NewAndOldValue result = substrColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "abc", null);
        assertEquals("", result.getNewValue());
    }

    @Test
    void testTransform_withTwoTokens_returnsSubstringBetweenIndexes() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("1,4");
        NewAndOldValue result = substrColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "abcdef", null);
        assertEquals("bcd", result.getNewValue());
    }

    @Test
    void testTransform_withTwoTokensEndBeyondLength_returnsSubstringFromBeginIndex() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("1,10");
        NewAndOldValue result = substrColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "abcdef", null);
        assertEquals("bcdef", result.getNewValue());
    }

    @Test
    void testTransform_withTwoTokensBeginIndexBeyondLength_returnsEmptyString() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("10,12");
        NewAndOldValue result = substrColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "abc", null);
        assertEquals("", result.getNewValue());
    }

    @Test
    void testTransform_withBlankExpression_returnsValueUnchanged() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression(null);
        NewAndOldValue result = substrColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "abc", null);
        assertEquals("abc", result.getNewValue());
    }

    @Test
    void testTransform_withEmptyNewValue_returnsNull() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("2");
        NewAndOldValue result = substrColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, null, null);
        assertNull(result.getNewValue());
    }

    @Test
    void testPrepend_addsValueToFrontOfArray() {
        String[] result = substrColumnTransform.prepend("x", new String[] { "a", "b" });
        assertArrayEquals(new String[] { "x", "a", "b" }, result);
    }
}
