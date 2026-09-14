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
package org.jumpmind.symmetric.observability.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.Test;

class MetricSeriesTest {
    private static MetricKey sampleKey(String metricId) {
        return new MetricKey(1L, "host", "engine", metricId, MetricFactType.INT64, InstrumentType.LONG_GAUGE, true);
    }

    @Test
    void accessors_returnConstructorValues() {
        MetricKey key = sampleKey("metric.a");
        MetricSeries series = new MetricSeries(99L, key);
        assertEquals(99L, series.metricAttrId());
        assertSame(key, series.key());
    }

    @Test
    void equals_sameContent_areEqual() {
        MetricKey key = sampleKey("metric.b");
        MetricSeries s1 = new MetricSeries(10L, key);
        MetricSeries s2 = new MetricSeries(10L, key);
        assertEquals(s1, s2);
    }

    @Test
    void equals_differentMetricAttrId_notEqual() {
        MetricKey key = sampleKey("metric.c");
        MetricSeries s1 = new MetricSeries(1L, key);
        MetricSeries s2 = new MetricSeries(2L, key);
        assertNotEquals(s1, s2);
    }

    @Test
    void hashCode_equalSeries_sameHashCode() {
        MetricKey key = sampleKey("metric.d");
        MetricSeries s1 = new MetricSeries(5L, key);
        MetricSeries s2 = new MetricSeries(5L, key);
        assertEquals(s1.hashCode(), s2.hashCode());
    }
}
