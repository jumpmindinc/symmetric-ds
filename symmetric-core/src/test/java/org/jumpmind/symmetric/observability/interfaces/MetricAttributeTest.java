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
package org.jumpmind.symmetric.observability.interfaces;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class MetricAttributeTest {
    private static final String DEFAULT = "N/A";

    @Test
    void packNamesIntoArray_nullAttrs_allDefault() {
        assertArrayEquals(new String[] { DEFAULT, DEFAULT, DEFAULT },
                MetricAttribute.packNamesIntoArray(null, 3, DEFAULT));
    }

    @Test
    void packNamesIntoArray_emptyAttrs_allDefault() {
        assertArrayEquals(new String[] { DEFAULT, DEFAULT, DEFAULT },
                MetricAttribute.packNamesIntoArray(List.of(), 3, DEFAULT));
    }

    @Test
    void packNamesIntoArray_oneAttr_firstSlotFilledRestDefault() {
        List<MetricAttribute> attrs = List.of(new MetricAttribute("k1", "v"));
        assertArrayEquals(new String[] { "k1", DEFAULT, DEFAULT },
                MetricAttribute.packNamesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packNamesIntoArray_attrWithNullName_usesDefault() {
        List<MetricAttribute> attrs = List.of(new MetricAttribute(null, "v"));
        assertArrayEquals(new String[] { DEFAULT, DEFAULT, DEFAULT },
                MetricAttribute.packNamesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packNamesIntoArray_maxSizeAttrs_allSlotsFromAttrs() {
        List<MetricAttribute> attrs = List.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"));
        assertArrayEquals(new String[] { "a", "b", "c" },
                MetricAttribute.packNamesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packNamesIntoArray_moreAttrsThanMaxSize_truncatedToMaxSize() {
        List<MetricAttribute> attrs = List.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"),
                new MetricAttribute("d", "v4"));
        assertArrayEquals(new String[] { "a", "b", "c" },
                MetricAttribute.packNamesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packNamesIntoArray_mixedNullNames_defaultForNulls() {
        List<MetricAttribute> attrs = List.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute(null, "v2"),
                new MetricAttribute("c", "v3"));
        assertArrayEquals(new String[] { "a", DEFAULT, "c" },
                MetricAttribute.packNamesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packValuesIntoArray_nullAttrs_allDefault() {
        assertArrayEquals(new String[] { DEFAULT, DEFAULT, DEFAULT },
                MetricAttribute.packValuesIntoArray(null, 3, DEFAULT));
    }

    @Test
    void packValuesIntoArray_emptyAttrs_allDefault() {
        assertArrayEquals(new String[] { DEFAULT, DEFAULT, DEFAULT },
                MetricAttribute.packValuesIntoArray(List.of(), 3, DEFAULT));
    }

    @Test
    void packValuesIntoArray_oneAttr_firstSlotFilledRestDefault() {
        List<MetricAttribute> attrs = List.of(new MetricAttribute("k", "v1"));
        assertArrayEquals(new String[] { "v1", DEFAULT, DEFAULT },
                MetricAttribute.packValuesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packValuesIntoArray_attrWithNullValue_usesDefault() {
        List<MetricAttribute> attrs = List.of(new MetricAttribute("k", null));
        assertArrayEquals(new String[] { DEFAULT, DEFAULT, DEFAULT },
                MetricAttribute.packValuesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packValuesIntoArray_maxSizeAttrs_allSlotsFromAttrs() {
        List<MetricAttribute> attrs = List.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"));
        assertArrayEquals(new String[] { "v1", "v2", "v3" },
                MetricAttribute.packValuesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packValuesIntoArray_moreAttrsThanMaxSize_truncatedToMaxSize() {
        List<MetricAttribute> attrs = List.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", "v2"),
                new MetricAttribute("c", "v3"),
                new MetricAttribute("d", "v4"));
        assertArrayEquals(new String[] { "v1", "v2", "v3" },
                MetricAttribute.packValuesIntoArray(attrs, 3, DEFAULT));
    }

    @Test
    void packValuesIntoArray_mixedNullValues_defaultForNulls() {
        List<MetricAttribute> attrs = List.of(
                new MetricAttribute("a", "v1"),
                new MetricAttribute("b", null),
                new MetricAttribute("c", "v3"));
        assertArrayEquals(new String[] { "v1", DEFAULT, "v3" },
                MetricAttribute.packValuesIntoArray(attrs, 3, DEFAULT));
    }
}
