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

import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.junit.jupiter.api.Test;

class MetricContextTest {
    // -----------------------------------------------------------------------
    // computeHash — deterministic, null-safe, positional
    // -----------------------------------------------------------------------
    @Test
    void computeHash_nullList_isStable() {
        int h1 = MetricContext.computeHash(null);
        int h2 = MetricContext.computeHash(null);
        assertEquals(h1, h2);
    }

    @Test
    void computeHash_emptyList_equalsNullList() {
        assertEquals(MetricContext.computeHash(null), MetricContext.computeHash(List.of()));
    }

    @Test
    void computeHash_singleAttr_isRepeatable() {
        var attrs = List.of(new MetricAttribute("channel", "default"));
        assertEquals(MetricContext.computeHash(attrs), MetricContext.computeHash(attrs));
    }

    @Test
    void computeHash_differentValues_produceDifferentHashes() {
        var a = List.of(new MetricAttribute("channel", "default"));
        var b = List.of(new MetricAttribute("channel", "reload"));
        assertNotEquals(MetricContext.computeHash(a), MetricContext.computeHash(b));
    }

    @Test
    void computeHash_differentNames_produceDifferentHashes() {
        var a = List.of(new MetricAttribute("channel", "default"));
        var b = List.of(new MetricAttribute("node_group", "default"));
        assertNotEquals(MetricContext.computeHash(a), MetricContext.computeHash(b));
    }

    @Test
    void computeHash_nullAttrName_substitutedWithNA() {
        var withNull = List.of(new MetricAttribute(null, "v"));
        var withNA = List.of(new MetricAttribute(MetricContext.NA, "v"));
        assertEquals(MetricContext.computeHash(withNull), MetricContext.computeHash(withNA));
    }

    @Test
    void computeHash_nullAttrValue_substitutedWithNA() {
        var withNull = List.of(new MetricAttribute("n", null));
        var withNA = List.of(new MetricAttribute("n", MetricContext.NA));
        assertEquals(MetricContext.computeHash(withNull), MetricContext.computeHash(withNA));
    }

    @Test
    void computeHash_orderMatters_positionOneVsTwo() {
        var a = List.of(new MetricAttribute("k1", "v1"), new MetricAttribute("k2", "v2"));
        var b = List.of(new MetricAttribute("k2", "v2"), new MetricAttribute("k1", "v1"));
        assertNotEquals(MetricContext.computeHash(a), MetricContext.computeHash(b));
    }

    @Test
    void computeHash_fourthAttrIgnored() {
        var three = List.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"));
        var four = List.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"),
                new MetricAttribute("d", "4"));
        assertEquals(MetricContext.computeHash(three), MetricContext.computeHash(four));
    }
    // -----------------------------------------------------------------------
    // SEED_IDS_END boundary — separates seed from dynamic IDs
    // -----------------------------------------------------------------------

    @Test
    void seedIdsEnd_isAboveAllKnownSeedIds() {
        // highest known seed ID is 12000262020 (ATTR_CHANNEL "usr")
        long highestSeed = 12_000_262_020L;
        assert highestSeed <= MetricContext.SEED_IDS_END : "SEED_IDS_END must cover all seed IDs";
    }

    @Test
    void seedIdsEnd_isBelowReasonableDynamicStart() {
        // Dynamic IDs allocated from MAX(context_id)+1 will exceed SEED_IDS_END
        // as long as seed values are below it — just verify the constant is sane
        assert MetricContext.SEED_IDS_END > 0;
        assert MetricContext.SEED_IDS_END < Long.MAX_VALUE / 2;
    }
}
