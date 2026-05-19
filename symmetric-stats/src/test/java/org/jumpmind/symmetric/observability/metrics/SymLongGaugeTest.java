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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.stats.Int64StatsAccumulator;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Tests {@link AbstractLongGaugeMetric} behaviour via the package-private {@link SymLongGauge} constructor.
 */
class SymLongGaugeTest {
    private static SymLongGauge gauge() {
        SymLongGauge gauge = new SymLongGauge(new SymMetricDefinition("test.long.gauge", "", "", InstrumentType.LONG_GAUGE), Attributes.empty(),
                MetricAttributeList.of());
        gauge.open(null);
        return gauge;
    }

    @Test
    void getValue_initiallyZero() {
        assertEquals(0L, gauge().getValue());
    }

    @Test
    void setValue_changesValue() {
        SymLongGauge gauge = gauge();
        gauge.setValue(42L);
        assertEquals(42L, gauge.getValue());
    }

    @Test
    void setValue_enqueuessObservation() {
        SymLongGauge gauge = gauge();
        gauge.setValue(10L);
        assertEquals(1, gauge.getObservationsCountEstimate());
    }

    @Test
    void add_accumulatesValue() {
        SymLongGauge gauge = gauge();
        gauge.add(5L);
        gauge.add(3L);
        assertEquals(8L, gauge.getValue());
    }

    @Test
    void add_enqueuessObservation() {
        SymLongGauge gauge = gauge();
        gauge.add(7L);
        assertEquals(1, gauge.getObservationsCountEstimate());
    }

    @Test
    void createAccumulator_returnsInt64StatsAccumulator() {
        SymLongGauge gauge = gauge();
        assertEquals(Int64StatsAccumulator.class, gauge.createAccumulator(0L).getClass());
    }

    @Test
    void close_withNoOtelHandle_doesNotThrow() {
        SymLongGauge gauge = gauge();
        assertDoesNotThrow(gauge::close);
    }

    @Test
    void open_close_invokesHandleClose() throws Exception {
        SymLongGauge gauge = gauge();
        ObservableLongGauge handle = mock(ObservableLongGauge.class);
        gauge.open(handle);
        gauge.close();
        verify(handle).close();
    }

    @Test
    void close_withOtelHandle_isOpenReturnsFalse() {
        SymLongGauge gauge = gauge();
        gauge.open(mock(ObservableLongGauge.class));
        gauge.close();
        assertFalse(gauge.isOpen());
    }

    @Test
    void close_whenOtelHandleThrows_doesNotPropagateAndIsOpenReturnsFalse() {
        SymLongGauge gauge = gauge();
        ObservableLongGauge handle = mock(ObservableLongGauge.class);
        doThrow(new RuntimeException("otel close failed")).when(handle).close();
        gauge.open(handle);
        assertDoesNotThrow(gauge::close);
        assertFalse(gauge.isOpen());
    }
}
