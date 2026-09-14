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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrimColumnTransformTest {
    private TrimColumnTransform trimColumnTransform;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        trimColumnTransform = new TrimColumnTransform();
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("trim", trimColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(trimColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(trimColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_trimsWhitespaceFromBothValues() throws IgnoreColumnException, IgnoreRowException {
        NewAndOldValue result = trimColumnTransform.transform(null, null, null, null, sourceValues, "  abc  ", " def ");
        assertEquals("abc", result.getNewValue());
        assertEquals("def", result.getOldValue());
    }

    @Test
    void testTransform_withNullValues_returnsNullUnchanged() throws IgnoreColumnException, IgnoreRowException {
        NewAndOldValue result = trimColumnTransform.transform(null, null, null, null, sourceValues, null, null);
        assertNull(result.getNewValue());
        assertNull(result.getOldValue());
    }

    @Test
    void testTransform_withNullNewValueAndWhitespaceOldValue_trimsOldValueOnly() throws IgnoreColumnException, IgnoreRowException {
        NewAndOldValue result = trimColumnTransform.transform(null, null, null, null, sourceValues, null, "  x  ");
        assertNull(result.getNewValue());
        assertEquals("x", result.getOldValue());
    }
}
