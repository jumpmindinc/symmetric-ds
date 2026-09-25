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

class CopyColumnTransformTest {
    private CopyColumnTransform copyColumnTransform;
    private TransformColumn transformColumn;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        copyColumnTransform = new CopyColumnTransform();
        transformColumn = new TransformColumn();
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("copy", copyColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform_returnsTrue() {
        assertTrue(copyColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform_returnsTrue() {
        assertTrue(copyColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_returnsNewAndOldValueUnchanged() throws IgnoreColumnException, IgnoreRowException {
        NewAndOldValue result = copyColumnTransform.transform(null, null, transformColumn, null, sourceValues, "new", "old");
        assertEquals("new", result.getNewValue());
        assertEquals("old", result.getOldValue());
    }

    @Test
    void testTransform_withNullValues_returnsNulls() throws IgnoreColumnException, IgnoreRowException {
        NewAndOldValue result = copyColumnTransform.transform(null, null, transformColumn, null, sourceValues, null, null);
        assertNull(result.getNewValue());
        assertNull(result.getOldValue());
    }
}
