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
package org.jumpmind.symmetric.observability.interfaces;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MetricAttributeListTest {
    private static final String NA = "N/A";
    public static final int ATTR_NUM_VALUES = 3;

    @Test
    void of_noArgs_returnsEmptyList() {
        MetricAttributeList list = MetricAttributeList.of();
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void of_singleAttr_returnsSingletonList() {
        MetricAttributeList list = MetricAttributeList.of(new MetricAttribute("k", "v"));
        assertEquals(1, list.size());
        assertEquals("k", list.get(0).name());
        assertEquals("v", list.get(0).value());
    }

    @Test
    void of_multipleAttrs_returnsInOrder() {
        MetricAttributeList list = MetricAttributeList.of(
                new MetricAttribute("a", "1"),
                new MetricAttribute("b", "2"),
                new MetricAttribute("c", "3"));
        assertEquals(ATTR_NUM_VALUES, list.size());
        assertEquals("a", list.get(0).name());
        assertEquals("c", list.get(2).name());
    }

    @Test
    void constructor_nullCollection_returnsEmptyList() {
        MetricAttributeList list = new MetricAttributeList(ATTR_NUM_VALUES);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void packNamesIntoArray_empty_allNulls() {
        MetricAttributeList list = new MetricAttributeList(ATTR_NUM_VALUES);
        assertArrayEquals(new String[] { null, null, null }, list.packNamesIntoArray(ATTR_NUM_VALUES));
    }

    @Test
    void packNamesIntoArray_oneAttr_firstSlotFilledRestNull() {
        MetricAttributeList list = MetricAttributeList.of(new MetricAttribute("k1", "v"));
        assertArrayEquals(new String[] { "k1", null, null }, list.packNamesIntoArray(ATTR_NUM_VALUES));
    }

    @Test
    void packNamesIntoArray_attrWithNullName_usesNull() {
        MetricAttributeList list = MetricAttributeList.of(new MetricAttribute(null, "v"));
        assertArrayEquals(new String[] { null, null, null }, list.packNamesIntoArray(ATTR_NUM_VALUES));
    }

    @Test
    void packNamesIntoArray_maxSizeAttrs_allSlotsFromAttrs() {
        MetricAttributeList list = MetricAttributeList.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"));
        assertArrayEquals(new String[] { "a", "b", "c" }, list.packNamesIntoArray(ATTR_NUM_VALUES));
    }

    @Test
    void packNamesIntoArray_moreAttrsThanMaxSize_truncatedToMaxSize() {
        MetricAttributeList list = MetricAttributeList.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"),
                new MetricAttribute("d", "v4"));
        assertArrayEquals(new String[] { "a", "b", "c" }, list.packNamesIntoArray(ATTR_NUM_VALUES));
    }

    @Test
    void packValuesIntoArray_empty_allDefault() {
        MetricAttributeList list = new MetricAttributeList(ATTR_NUM_VALUES);
        assertArrayEquals(new String[] { NA, NA, NA }, list.packValuesIntoArray(ATTR_NUM_VALUES, NA));
    }

    @Test
    void packValuesIntoArray_oneAttr_firstSlotFilledRestDefault() {
        MetricAttributeList list = MetricAttributeList.of(new MetricAttribute("k", "v1"));
        assertArrayEquals(new String[] { "v1", NA, NA }, list.packValuesIntoArray(ATTR_NUM_VALUES, NA));
    }

    @Test
    void packValuesIntoArray_attrWithNullValue_usesDefault() {
        MetricAttributeList list = MetricAttributeList.of(new MetricAttribute("k", null));
        assertArrayEquals(new String[] { NA, NA, NA }, list.packValuesIntoArray(ATTR_NUM_VALUES, NA));
    }

    @Test
    void packValuesIntoArray_maxSizeAttrs_allSlotsFromAttrs() {
        MetricAttributeList list = MetricAttributeList.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"));
        assertArrayEquals(new String[] { "v1", "v2", "v3" }, list.packValuesIntoArray(ATTR_NUM_VALUES, NA));
    }

    @Test
    void packNamesAndValuesIntoArray_empty_allNulls() {
        MetricAttributeList list = new MetricAttributeList(ATTR_NUM_VALUES);
        String[] av = list.packNamesAndValuesIntoArray(ATTR_NUM_VALUES, null);
        assertEquals(2 * ATTR_NUM_VALUES, av.length);
        for (String s : av) {
            assertEquals(null, s);
        }
    }

    @Test
    void packNamesAndValuesIntoArray_oneAttr_firstPairFilledRestNull() {
        MetricAttributeList list = MetricAttributeList.of(new MetricAttribute("attr1", "value1"));
        String[] av = list.packNamesAndValuesIntoArray(ATTR_NUM_VALUES, null);
        assertEquals(2 * ATTR_NUM_VALUES, av.length);
        assertEquals("attr1", av[0]);
        assertEquals("value1", av[1]);
        assertEquals(null, av[2]);
        assertEquals(null, av[3]);
        assertEquals(null, av[4]);
        assertEquals(null, av[5]);
        assertEquals("attr1", list.concatenateNames("+", ATTR_NUM_VALUES));
    }

    @Test
    void packNamesAndValuesIntoArray_threeAttrs_allPairsInterleaved() {
        MetricAttributeList list = MetricAttributeList.of(
                new MetricAttribute("a", "1"),
                new MetricAttribute("b", "2"),
                new MetricAttribute("c", "3"));
        String[] av = list.packNamesAndValuesIntoArray(ATTR_NUM_VALUES, null);
        assertArrayEquals(new String[] { "a", "1", "b", "2", "c", "3" }, av);
    }

    @Test
    void concatenateNames_empty_returnsEmptyString() {
        MetricAttributeList list = new MetricAttributeList(ATTR_NUM_VALUES);
        assertEquals("", list.concatenateNames("+", ATTR_NUM_VALUES));
    }

    @Test
    void concatenateNames_twoAttrs_joinedBySeparator() {
        MetricAttributeList list = MetricAttributeList.of(
                new MetricAttribute("channel", "v1"),
                new MetricAttribute("node_group", "v2"));
        assertEquals("channel+node_group", list.concatenateNames("+", ATTR_NUM_VALUES));
    }

    @Test
    void concatenateNames_respectsMaxSize() {
        MetricAttributeList list = MetricAttributeList.of(
                new MetricAttribute("a", "1"),
                new MetricAttribute("b", "2"),
                new MetricAttribute("c", "3"),
                new MetricAttribute("d", "4"));
        assertEquals("a+b+c", list.concatenateNames("+", ATTR_NUM_VALUES));
    }

    @Test
    void generateHashOfValues_empty_sameAsAllDefaultValues() {
        MetricAttributeList empty = new MetricAttributeList(0);
        MetricAttributeList allDefault = MetricAttributeList.of(
                new MetricAttribute("a", NA),
                new MetricAttribute("b", NA),
                new MetricAttribute("c", NA));
        assertEquals(empty.generateHashOfValues(ATTR_NUM_VALUES, NA),
                allDefault.generateHashOfValues(ATTR_NUM_VALUES, NA));
    }

    @Test
    void generateHashOfValues_attrWithNullValue_usesDefault() {
        MetricAttributeList withNull = MetricAttributeList.of(new MetricAttribute("k", null));
        MetricAttributeList withDefault = MetricAttributeList.of(new MetricAttribute("k", NA));
        assertEquals(withNull.generateHashOfValues(ATTR_NUM_VALUES, NA),
                withDefault.generateHashOfValues(ATTR_NUM_VALUES, NA));
    }

    @Test
    void generateHashOfValues_differentValues_differentHash() {
        MetricAttributeList a = MetricAttributeList.of(new MetricAttribute("k", "v1"));
        MetricAttributeList b = MetricAttributeList.of(new MetricAttribute("k", "v2"));
        assertNotEquals(a.generateHashOfValues(ATTR_NUM_VALUES, NA),
                b.generateHashOfValues(ATTR_NUM_VALUES, NA));
    }

    @Test
    void generateHashOfValues_moreAttrsThanMaxSize_truncated() {
        MetricAttributeList full = MetricAttributeList.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"));
        MetricAttributeList extra = MetricAttributeList.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"),
                new MetricAttribute("d", "v4"));
        assertEquals(full.generateHashOfValues(ATTR_NUM_VALUES, NA),
                extra.generateHashOfValues(ATTR_NUM_VALUES, NA));
    }
}
