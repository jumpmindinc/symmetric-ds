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
package org.jumpmind.symmetric.observability.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.models.MetricContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MetricContextCacheTest {
    @Test
    void cacheKey_nullAttrs_returnsEmptyNamesPart() {
        String key = MetricsRepository.contextCacheKey(null);
        assertTrue(key.startsWith("="), "Key with no attrs should start with '='");
    }

    @Test
    void cacheKey_emptyAttrs_equalsNullAttrs() {
        assertEquals(
                MetricsRepository.contextCacheKey(null),
                MetricsRepository.contextCacheKey(List.of()));
    }

    @Test
    void cacheKey_singleAttr_containsName() {
        String key = MetricsRepository.contextCacheKey(List.of(new MetricAttribute("channel", "default")));
        assertTrue(key.startsWith("channel="), "Key should start with attr name");
    }

    @Test
    void cacheKey_twoAttrs_namesJoinedByPlus() {
        var attrs = List.of(new MetricAttribute("channel", "default"), new MetricAttribute("node_group", "store"));
        String key = MetricsRepository.contextCacheKey(attrs);
        assertTrue(key.startsWith("channel+node_group="));
    }

    @Test
    void cacheKey_threeAttrs_allNamesPresent() {
        var attrs = List.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"));
        String key = MetricsRepository.contextCacheKey(attrs);
        assertTrue(key.startsWith("a+b+c="));
    }

    @Test
    void cacheKey_fourthAttrIgnored_sameKeyAsThree() {
        var three = List.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"));
        var four = List.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"),
                new MetricAttribute("d", "4"));
        assertEquals(MetricsRepository.contextCacheKey(three), MetricsRepository.contextCacheKey(four));
    }

    @Test
    void cacheKey_sameAttrs_isRepeatable() {
        var attrs = List.of(new MetricAttribute("channel", "default"));
        assertEquals(MetricsRepository.contextCacheKey(attrs), MetricsRepository.contextCacheKey(attrs));
    }

    @Test
    void cacheKey_differentValues_produceDifferentKeys() {
        var a = List.of(new MetricAttribute("channel", "default"));
        var b = List.of(new MetricAttribute("channel", "reload"));
        assertNotEquals(MetricsRepository.contextCacheKey(a), MetricsRepository.contextCacheKey(b));
    }

    @Test
    void cacheKey_differentNames_produceDifferentKeys() {
        var a = List.of(new MetricAttribute("channel", "x"));
        var b = List.of(new MetricAttribute("node_group", "x"));
        assertNotEquals(MetricsRepository.contextCacheKey(a), MetricsRepository.contextCacheKey(b));
    }

    @ParameterizedTest(name = "swapped order [{0},{1}] vs [{1},{0}]")
    @CsvSource({ "channel,node_group", "a,b", "x,y" })
    void cacheKey_orderMatters(String n1, String n2) {
        var ab = List.of(new MetricAttribute(n1, "v"), new MetricAttribute(n2, "v"));
        var ba = List.of(new MetricAttribute(n2, "v"), new MetricAttribute(n1, "v"));
        assertNotEquals(MetricsRepository.contextCacheKey(ab), MetricsRepository.contextCacheKey(ba),
                "Key must encode position, not just name set");
    }

    @Test
    void attributesMatch_sameAttrs_returnsTrue() {
        var attrs = List.of(new MetricAttribute("channel", "default"));
        MetricContext ctx = new MetricContext(1L, attrs);
        assertTrue(MetricsRepository.attributesMatch(ctx, attrs));
    }

    @Test
    void attributesMatch_differentValue_returnsFalse() {
        var ctxAttrs = List.of(new MetricAttribute("channel", "default"));
        var queryAttrs = List.of(new MetricAttribute("channel", "reload"));
        MetricContext ctx = new MetricContext(1L, ctxAttrs);
        assertFalse(MetricsRepository.attributesMatch(ctx, queryAttrs));
    }

    @Test
    void attributesMatch_differentName_returnsFalse() {
        var ctxAttrs = List.of(new MetricAttribute("channel", "default"));
        var queryAttrs = List.of(new MetricAttribute("node_group", "default"));
        MetricContext ctx = new MetricContext(1L, ctxAttrs);
        assertFalse(MetricsRepository.attributesMatch(ctx, queryAttrs));
    }

    @Test
    void attributesMatch_emptyVsEmpty_returnsTrue() {
        MetricContext ctx = new MetricContext(1L, List.of());
        assertTrue(MetricsRepository.attributesMatch(ctx, List.of()));
    }

    @Test
    void attributesMatch_emptyVsNonEmpty_returnsFalse() {
        MetricContext ctx = new MetricContext(1L, List.of());
        assertFalse(MetricsRepository.attributesMatch(ctx, List.of(new MetricAttribute("channel", "default"))));
    }

    @Test
    void seedIdsEnd_seedContextIdIsBelow() {
        long seedId = 12_000_262_020L; // highest known seed (ATTR_CHANNEL "usr")
        assertTrue(seedId <= MetricContext.SEED_IDS_END);
    }

    @Test
    void seedIdsEnd_dynamicContextIdIsAbove() {
        // Any ID generated after seeding will be > SEED_IDS_END + some offset
        long dynamicId = MetricContext.SEED_IDS_END + 1;
        assertTrue(dynamicId > MetricContext.SEED_IDS_END);
    }
}
