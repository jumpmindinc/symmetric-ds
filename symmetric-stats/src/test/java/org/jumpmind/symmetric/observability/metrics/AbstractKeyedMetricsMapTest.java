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
package org.jumpmind.symmetric.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractKeyedMetricsMapTest {
    /** Concrete subclass that uses the string value itself as the key. */
    private static class StringMap extends AbstractKeyedMetricsMap<String> {
        @Override
        protected String generateEntryKey(String entry) {
            return entry;
        }
    }

    private StringMap map;

    @BeforeEach
    void setUp() {
        map = new StringMap();
    }

    @Test
    void size_emptyMap_isZero() {
        assertEquals(0, map.size());
    }

    @Test
    void put_singleEntry_sizeIsOne() {
        map.put("alpha");
        assertEquals(1, map.size());
    }

    @Test
    void put_duplicateKey_doesNotGrowSize() {
        map.put("alpha");
        map.put("alpha");
        assertEquals(1, map.size());
    }

    @Test
    void get_presentKey_returnsEntry() {
        map.put("beta");
        Optional<String> result = map.get("beta");
        assertTrue(result.isPresent());
        assertEquals("beta", result.get());
    }

    @Test
    void get_absentKey_returnsEmpty() {
        Optional<String> result = map.get("missing");
        assertFalse(result.isPresent());
    }

    @Test
    void contains_presentKey_returnsTrue() {
        map.put("gamma");
        assertTrue(map.contains("gamma"));
    }

    @Test
    void contains_absentKey_returnsFalse() {
        assertFalse(map.contains("nope"));
    }

    @Test
    void remove_presentKey_decreasesSize() {
        map.put("delta");
        map.remove("delta");
        assertEquals(0, map.size());
        assertFalse(map.contains("delta"));
    }

    @Test
    void remove_absentKey_isNoOp() {
        map.put("epsilon");
        map.remove("ghost");
        assertEquals(1, map.size());
    }

    @Test
    void all_returnsAllValues() {
        map.put("a");
        map.put("b");
        assertEquals(2, map.all().size());
        assertTrue(map.all().contains("a"));
        assertTrue(map.all().contains("b"));
    }

    @Test
    void all_isUnmodifiable() {
        map.put("x");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> map.all().add("y"));
    }

    @Test
    void keys_returnsAllKeys() {
        map.put("one");
        map.put("two");
        assertTrue(map.keys().contains("one"));
        assertTrue(map.keys().contains("two"));
        assertEquals(2, map.keys().size());
    }

    @Test
    void keys_isUnmodifiable() {
        map.put("k");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> map.keys().add("z"));
    }

    @Test
    void getOrCreate_absentKey_callsFactory() {
        String result = map.getOrCreate("new", () -> "new");
        assertEquals("new", result);
        assertTrue(map.contains("new"));
    }

    @Test
    void getOrCreate_presentKey_returnsExistingEntry() {
        map.put("existing");
        String result = map.getOrCreate("existing", () -> "should-not-be-used");
        assertSame("existing", result);
    }

    @Test
    void getOrCreate_factoryCalledOnceForSameKey() {
        int[] callCount = { 0 };
        map.getOrCreate("once", () -> {
            callCount[0]++;
            return "once";
        });
        map.getOrCreate("once", () -> {
            callCount[0]++;
            return "once";
        });
        assertEquals(1, callCount[0]);
    }
}
