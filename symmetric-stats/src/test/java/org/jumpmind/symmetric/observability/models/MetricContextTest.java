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
package org.jumpmind.symmetric.observability.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.repository.MetricsRepository;
import org.junit.jupiter.api.Test;

class MetricContextTest {
    @Test
    void undefined_isNegativeOne() {
        assertEquals(MetricContext.UNDEFINED, -1L);
    }

    @Test
    void na_isNAString() {
        assertEquals("N/A", MetricContext.NA);
    }

    @Test
    void seedIdsEnd_exactValue() {
        assertEquals(20_000_000_000L, MetricContext.SEED_IDS_END);
    }

    @Test
    void seedIdsEnd_isAboveAllKnownSeedIds() {
        long highestSeed = 12_000_262_020L; // highest known seed ID is ATTR_CHANNEL "usr"
        assertTrue(highestSeed <= MetricContext.SEED_IDS_END,
                "SEED_IDS_END must cover all seed IDs");
    }

    @Test
    void seedIdsEnd_isPositiveAndBelowHalfMaxLong() {
        assertTrue(MetricContext.SEED_IDS_END > 0);
        assertTrue(MetricContext.SEED_IDS_END < Long.MAX_VALUE / 2);
    }

    @Test
    void getContextId_returnsConstructorValue() {
        MetricContext ctx = new MetricContext(42L, new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES));
        assertEquals(42L, ctx.getContextId());
        assertEquals(42L, ctx.contextId());
    }

    @Test
    void getAttributes_returnsConstructorValue() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        MetricContext ctx = new MetricContext(1L, attrs);
        assertEquals(attrs, ctx.getAttributes());
        assertEquals(attrs, ctx.attributes());
    }

    @Test
    void undefined_usedAsContextId_isAccessible() {
        MetricContext ctx = new MetricContext(MetricContext.UNDEFINED, new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES));
        assertEquals(MetricContext.UNDEFINED, ctx.getContextId());
    }

    @Test
    void equals_sameContextIdAndAttributes_areEqual() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("k", "v"));
        MetricContext a = new MetricContext(7L, attrs);
        MetricContext b = new MetricContext(7L, attrs);
        assertEquals(a, b);
    }

    @Test
    void equals_differentContextId_notEqual() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("k", "v"));
        MetricContext a = new MetricContext(1L, attrs);
        MetricContext b = new MetricContext(2L, attrs);
        assertNotEquals(a, b);
    }

    @Test
    void equals_differentAttributes_notEqual() {
        MetricContext a = new MetricContext(1L, MetricAttributeList.of(new MetricAttribute("k", "v1")));
        MetricContext b = new MetricContext(1L, MetricAttributeList.of(new MetricAttribute("k", "v2")));
        assertNotEquals(a, b);
    }

    @Test
    void hashCode_equalRecords_sameHashCode() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("k", "v"));
        MetricContext a = new MetricContext(5L, attrs);
        MetricContext b = new MetricContext(5L, attrs);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void computeHash_nullList_isStable() {
        int h1 = MetricContext.computeHash(null);
        int h2 = MetricContext.computeHash(null);
        assertEquals(h1, h2);
    }

    @Test
    void computeHash_emptyList_equalsNullList() {
        assertEquals(MetricContext.computeHash(null), MetricContext.computeHash(MetricAttributeList.of()));
    }

    @Test
    void computeHash_singleAttr_isRepeatable() {
        var attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        assertEquals(MetricContext.computeHash(attrs), MetricContext.computeHash(attrs));
    }

    @Test
    void computeHash_differentValues_produceDifferentHashes() {
        var a = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        var b = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_RELOAD));
        assertNotEquals(MetricContext.computeHash(a), MetricContext.computeHash(b));
    }

    @Test
    void computeHash_differentNames_produceDifferentHashes() {
        var a = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        var b = MetricAttributeList.of(new MetricAttribute("node_group", "default"));
        assertNotEquals(MetricContext.computeHash(a), MetricContext.computeHash(b));
    }

    @Test
    void computeHash_nullAttrName_substitutedWithNA() {
        var withNull = MetricAttributeList.of(new MetricAttribute(null, "v"));
        var withNA = MetricAttributeList.of(new MetricAttribute(MetricContext.NA, "v"));
        assertEquals(MetricContext.computeHash(withNull), MetricContext.computeHash(withNA));
    }

    @Test
    void computeHash_nullAttrValue_substitutedWithNA() {
        var withNull = MetricAttributeList.of(new MetricAttribute("n", null));
        var withNA = MetricAttributeList.of(new MetricAttribute("n", MetricContext.NA));
        assertEquals(MetricContext.computeHash(withNull), MetricContext.computeHash(withNA));
    }

    @Test
    void computeHash_orderMatters_positionOneVsTwo() {
        var a = MetricAttributeList.of(new MetricAttribute("k1", "v1"), new MetricAttribute("k2", "v2"));
        var b = MetricAttributeList.of(new MetricAttribute("k2", "v2"), new MetricAttribute("k1", "v1"));
        assertNotEquals(MetricContext.computeHash(a), MetricContext.computeHash(b));
    }

    @Test
    void computeHash_twoAttrs_differFromOneAttr() {
        var one = MetricAttributeList.of(new MetricAttribute("a", "1"));
        var two = MetricAttributeList.of(new MetricAttribute("a", "1"), new MetricAttribute("b", "2"));
        assertNotEquals(MetricContext.computeHash(one), MetricContext.computeHash(two));
    }

    @Test
    void computeHash_threeAttrs_differFromTwoAttrs() {
        var two = MetricAttributeList.of(new MetricAttribute("a", "1"), new MetricAttribute("b", "2"));
        var three = MetricAttributeList.of(new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"));
        assertNotEquals(MetricContext.computeHash(two), MetricContext.computeHash(three));
    }

    @Test
    void computeHash_fourthAttrIgnored() {
        var three = MetricAttributeList.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"));
        var four = MetricAttributeList.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"),
                new MetricAttribute("d", "4"));
        assertEquals(MetricContext.computeHash(three), MetricContext.computeHash(four));
    }

    @Test
    void computeHash_thirdAttrDiffers_produceDifferentHashes() {
        var a = MetricAttributeList.of(
                new MetricAttribute("x", "1"), new MetricAttribute("y", "2"), new MetricAttribute("z", "a"));
        var b = MetricAttributeList.of(
                new MetricAttribute("x", "1"), new MetricAttribute("y", "2"), new MetricAttribute("z", "b"));
        assertNotEquals(MetricContext.computeHash(a), MetricContext.computeHash(b));
    }
}
