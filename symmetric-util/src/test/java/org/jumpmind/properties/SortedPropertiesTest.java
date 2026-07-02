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
package org.jumpmind.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;

class SortedPropertiesTest {
    /** Drain keys() into a list so order can be asserted. */
    private static List<String> keysOf(SortedProperties props) {
        List<String> keys = new ArrayList<>();
        Enumeration<?> e = props.keys();
        while (e.hasMoreElements()) {
            keys.add((String) e.nextElement());
        }
        return keys;
    }

    @Test
    void testKeys_sortedOrderRegardlessOfInsertionOrder() {
        SortedProperties props = new SortedProperties();
        props.setProperty("charlie", "3");
        props.setProperty("alpha", "1");
        props.setProperty("bravo", "2");
        assertEquals(List.of("alpha", "bravo", "charlie"), keysOf(props));
    }

    @Test
    void testKeys_caseSensitiveLexicographicSort() {
        // Uppercase sorts before lowercase (ASCII order), so "Apple" precedes "banana".
        SortedProperties props = new SortedProperties();
        props.setProperty("banana", "1");
        props.setProperty("Apple", "2");
        props.setProperty("cherry", "3");
        assertEquals(List.of("Apple", "banana", "cherry"), keysOf(props));
    }

    @Test
    void testKeys_numericKeysSortLexicographicallyNotNumerically() {
        // "10" comes before "2" because comparison is character-by-character on strings.
        SortedProperties props = new SortedProperties();
        props.setProperty("2", "a");
        props.setProperty("10", "b");
        props.setProperty("1", "c");
        assertEquals(List.of("1", "10", "2"), keysOf(props));
    }

    @Test
    void testKeys_singleKey() {
        SortedProperties props = new SortedProperties();
        props.setProperty("only", "1");
        assertEquals(List.of("only"), keysOf(props));
    }

    @Test
    void testKeys_emptyReturnsNoKeys() {
        assertEquals(List.of(), keysOf(new SortedProperties()));
    }
}
