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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LinkedCaseInsensitiveMapTest {
    @Test
    void testGet_resolvesAnyCasing() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("Content-Type", "application/json");
        assertEquals("application/json", map.get("content-type"));
        assertEquals("application/json", map.get("CONTENT-TYPE"));
        assertEquals("application/json", map.get("Content-Type"));
    }

    @Test
    void testContainsKey_resolvesAnyCasing() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("Accept", "*/*");
        assertTrue(map.containsKey("accept"));
        assertTrue(map.containsKey("ACCEPT"));
        assertFalse(map.containsKey("missing"));
    }

    @Test
    void testRemove_resolvesAnyCasing() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("Host", "localhost");
        assertEquals("localhost", map.remove("HOST"));
        assertNull(map.get("host"));
        assertFalse(map.containsKey("host"));
    }

    @Test
    void testPut_sameCaseOverwriteKeepsLatestValue() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("key", "first");
        map.put("key", "second");
        assertEquals("second", map.get("key"));
        assertEquals(1, map.size());
    }

    @Test
    void testKeySet_reportsOriginalCasing() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("Content-Type", "a");
        // Stored under the original casing, not lower-cased.
        assertTrue(map.containsKey("content-type"));
        assertEquals(List.of("Content-Type"), new ArrayList<>(map.keySet()));
    }

    @Test
    void testKeySet_insertionOrderPreserved() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("first", "1");
        map.put("second", "2");
        map.put("third", "3");
        assertEquals(List.of("first", "second", "third"), new ArrayList<>(map.keySet()));
    }

    @Test
    void testGet_nonStringKeyReturnsNullOrFalse() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("key", "value");
        assertNull(map.get(Integer.valueOf(1)));
        assertFalse(map.containsKey(Integer.valueOf(1)));
        assertNull(map.remove(Integer.valueOf(1)));
    }

    @Test
    void testPut_nullKeyThrows() {
        // Null keys are explicitly unsupported (convertKey calls toLowerCase on the key).
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        assertThrows(NullPointerException.class, () -> map.put(null, "x"));
    }

    @Test
    void testGet_nullKeyLookupsAreSafe() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        assertNull(map.get(null));
        assertFalse(map.containsKey(null));
        assertNull(map.remove(null));
    }

    @Test
    void testConstructor_mapCopiesEntriesCaseInsensitively() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("Foo", "1");
        source.put("Bar", "2");
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>(source);
        assertEquals("1", map.get("foo"));
        assertEquals("2", map.get("BAR"));
        assertEquals(2, map.size());
    }

    @Test
    void testClear_emptiesBothBackingMaps() {
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("Key", "value");
        map.clear();
        assertEquals(0, map.size());
        assertNull(map.get("key"));
        assertFalse(map.containsKey("key"));
        // Re-use after clear still works (case-insensitive index was cleared too).
        map.put("Key", "again");
        assertEquals("again", map.get("KEY"));
    }

    @Test
    void testPut_differentCaseInflatesSize() {
        // get/containsKey stay case-insensitive, but this version leaves the prior
        // original-cased entry in the backing LinkedHashMap, so size() over-counts.
        // Documents current behavior; would flag a future fix to the production class.
        LinkedCaseInsensitiveMap<String> map = new LinkedCaseInsensitiveMap<>();
        map.put("Name", "a");
        map.put("NAME", "b");
        assertEquals("b", map.get("name"));
        assertEquals(2, map.size());
    }
}
