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
package org.jumpmind.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = new Context();
    }

    @Test
    void testGet_returnsStoredValue() {
        context.put("key", "value");
        assertEquals("value", context.get("key"));
    }

    @Test
    void testPut_overwritesExistingValue() {
        context.put("key", "first");
        context.put("key", "second");
        assertEquals("second", context.get("key"));
    }

    @Test
    void testGet_missingKeyReturnsNull() {
        assertNull(context.get("absent"));
    }

    @Test
    void testPut_storesAnyObjectType() {
        Object value = new int[] { 1, 2, 3 };
        context.put("array", value);
        assertSame(value, context.get("array"));
    }

    @Test
    void testPut_storesNullValue() {
        // Backed by HashMap, so a null value is allowed and distinct from "absent".
        context.put("key", null);
        assertNull(context.get("key"));
        assertTrue(context.keySet().contains("key"));
    }

    @Test
    void testRemove_returnsValueAndDeletesKey() {
        context.put("key", "value");
        assertEquals("value", context.remove("key"));
        assertNull(context.get("key"));
    }

    @Test
    void testRemove_missingKeyReturnsNull() {
        assertNull(context.remove("absent"));
    }

    @Test
    void testKeySet_reflectsStoredKeys() {
        context.put("a", 1);
        context.put("b", 2);
        assertEquals(2, context.keySet().size());
        assertTrue(context.keySet().containsAll(java.util.List.of("a", "b")));
    }

    @Test
    void testKeySet_emptyForNewContext() {
        assertTrue(context.keySet().isEmpty());
    }

    @Test
    void testGetContext_exposesAllEntries() {
        context.put("key", "value");
        assertEquals(1, context.getContext().size());
        assertEquals("value", context.getContext().get("key"));
    }

    @Test
    void testGetContext_isLiveBackingMap() {
        // getContext() returns the internal map, so changes through it are visible via get().
        context.getContext().put("injected", 42);
        assertEquals(42, context.get("injected"));
    }
}
