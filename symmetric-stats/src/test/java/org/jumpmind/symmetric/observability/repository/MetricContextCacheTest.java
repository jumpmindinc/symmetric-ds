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

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.models.MetricContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MetricContextCacheTest {
    @Test
    void cacheKey_emptyAttrs_returnsEmptyNamesPart() {
        String key = MetricsRepository.generateContextCacheKey(new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES));
        assertTrue(key.startsWith("="), "Key with no attrs should start with '='");
    }

    @Test
    void cacheKey_singleAttr_containsName() {
        String key = MetricsRepository.generateContextCacheKey(MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT)));
        assertTrue(key.startsWith("channel="), "Key should start with attr name");
    }

    @Test
    void cacheKey_twoAttrs_namesJoinedByPlus() {
        var attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT), new MetricAttribute("node_group", "store"));
        String key = MetricsRepository.generateContextCacheKey(attrs);
        assertTrue(key.startsWith("channel+node_group="));
    }

    @Test
    void cacheKey_threeAttrs_allNamesPresent() {
        var attrs = MetricAttributeList.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"));
        String key = MetricsRepository.generateContextCacheKey(attrs);
        assertTrue(key.startsWith("a+b+c="));
    }

    @Test
    void cacheKey_fourthAttrIgnored_sameKeyAsThree() {
        var three = MetricAttributeList.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"));
        var four = MetricAttributeList.of(
                new MetricAttribute("a", "1"), new MetricAttribute("b", "2"), new MetricAttribute("c", "3"),
                new MetricAttribute("d", "4"));
        assertEquals(MetricsRepository.generateContextCacheKey(three), MetricsRepository.generateContextCacheKey(four));
    }

    @Test
    void cacheKey_sameAttrs_isRepeatable() {
        var attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        assertEquals(MetricsRepository.generateContextCacheKey(attrs), MetricsRepository.generateContextCacheKey(attrs));
    }

    @Test
    void cacheKey_differentValues_produceDifferentKeys() {
        var a = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        var b = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_RELOAD));
        assertNotEquals(MetricsRepository.generateContextCacheKey(a), MetricsRepository.generateContextCacheKey(b));
    }

    @Test
    void cacheKey_differentNames_produceDifferentKeys() {
        var a = MetricAttributeList.of(new MetricAttribute("channel", "x"));
        var b = MetricAttributeList.of(new MetricAttribute("node_group", "x"));
        assertNotEquals(MetricsRepository.generateContextCacheKey(a), MetricsRepository.generateContextCacheKey(b));
    }

    @ParameterizedTest(name = "swapped order [{0},{1}] vs [{1},{0}]")
    @CsvSource({ "channel,node_group", "a,b", "x,y" })
    void cacheKey_orderMatters(String n1, String n2) {
        var ab = MetricAttributeList.of(new MetricAttribute(n1, "v"), new MetricAttribute(n2, "v"));
        var ba = MetricAttributeList.of(new MetricAttribute(n2, "v"), new MetricAttribute(n1, "v"));
        assertNotEquals(MetricsRepository.generateContextCacheKey(ab), MetricsRepository.generateContextCacheKey(ba),
                "Key must encode position, not just name set");
    }

    @Test
    void attributesMatch_sameAttrs_returnsTrue() {
        var attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        MetricContext ctx = new MetricContext(1L, attrs);
        assertTrue(MetricsRepository.attributesMatch(ctx, attrs));
    }

    @Test
    void attributesMatch_differentValue_returnsFalse() {
        var ctxAttrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        var queryAttrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_RELOAD));
        MetricContext ctx = new MetricContext(1L, ctxAttrs);
        assertFalse(MetricsRepository.attributesMatch(ctx, queryAttrs));
    }

    @Test
    void attributesMatch_differentName_returnsFalse() {
        var ctxAttrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        var queryAttrs = MetricAttributeList.of(new MetricAttribute("node_group", "default"));
        MetricContext ctx = new MetricContext(1L, ctxAttrs);
        assertFalse(MetricsRepository.attributesMatch(ctx, queryAttrs));
    }

    @Test
    void attributesMatch_emptyVsEmpty_returnsTrue() {
        MetricContext ctx = new MetricContext(1L, new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES));
        assertTrue(MetricsRepository.attributesMatch(ctx, new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES)));
    }

    @Test
    void attributesMatch_emptyVsNonEmpty_returnsFalse() {
        MetricContext ctx = new MetricContext(1L, new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES));
        assertFalse(MetricsRepository.attributesMatch(ctx, MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT))));
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
