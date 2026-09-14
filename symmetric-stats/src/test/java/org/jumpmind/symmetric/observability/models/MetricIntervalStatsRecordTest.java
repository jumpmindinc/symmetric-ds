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
import static org.mockito.Mockito.mock;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.Test;

class MetricIntervalStatsRecordTest {
    private static MetricKey sampleKey() {
        return new MetricKey(1L, "host1", "engine1", "metric.id", MetricFactType.FLOAT64, InstrumentType.LONG_GAUGE, true);
    }

    @Test
    void accessors_returnConstructorValues() {
        MetricKey key = sampleKey();
        ISymIntervalStats stats = mock(ISymIntervalStats.class);
        MetricIntervalStatsRecord actual = new MetricIntervalStatsRecord(key, 42L, stats);
        assertSame(key, actual.key());
        assertEquals(42L, actual.contextId());
        assertSame(stats, actual.stats());
    }

    @Test
    void equals_sameContent_areEqual() {
        MetricKey key = sampleKey();
        ISymIntervalStats stats = mock(ISymIntervalStats.class);
        MetricIntervalStatsRecord r1 = new MetricIntervalStatsRecord(key, 42L, stats);
        MetricIntervalStatsRecord r2 = new MetricIntervalStatsRecord(key, 42L, stats);
        assertEquals(r1, r2);
    }

    @Test
    void equals_differentContextId_notEqual() {
        MetricKey key = sampleKey();
        ISymIntervalStats stats = mock(ISymIntervalStats.class);
        MetricIntervalStatsRecord r1 = new MetricIntervalStatsRecord(key, 1L, stats);
        MetricIntervalStatsRecord r2 = new MetricIntervalStatsRecord(key, 2L, stats);
        assertNotEquals(r1, r2);
    }

    @Test
    void hashCode_equalRecords_sameHashCode() {
        MetricKey key = sampleKey();
        ISymIntervalStats stats = mock(ISymIntervalStats.class);
        MetricIntervalStatsRecord r1 = new MetricIntervalStatsRecord(key, 7L, stats);
        MetricIntervalStatsRecord r2 = new MetricIntervalStatsRecord(key, 7L, stats);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}
