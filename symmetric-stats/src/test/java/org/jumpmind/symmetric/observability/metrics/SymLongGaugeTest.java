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

import java.util.List;

import org.jumpmind.symmetric.observability.stats.Int64StatsAccumulator;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Tests {@link AbstractLongGaugeMetric} behaviour via the package-private {@link SymLongGauge} constructor.
 */
class SymLongGaugeTest {
    private static SymLongGauge gauge() {
        return new SymLongGauge("test.long.gauge", Attributes.empty(), List.of());
    }

    @Test
    void getValue_initiallyZero() {
        assertEquals(0L, gauge().getValue());
    }

    @Test
    void setValue_changesValue() {
        SymLongGauge g = gauge();
        g.setValue(42L);
        assertEquals(42L, g.getValue());
    }

    @Test
    void setValue_enqueuessObservation() {
        SymLongGauge g = gauge();
        g.setValue(10L);
        assertEquals(1, g.getObservationsCountEstimate());
    }

    @Test
    void add_accumulatesValue() {
        SymLongGauge g = gauge();
        g.add(5L);
        g.add(3L);
        assertEquals(8L, g.getValue());
    }

    @Test
    void add_enqueuessObservation() {
        SymLongGauge g = gauge();
        g.add(7L);
        assertEquals(1, g.getObservationsCountEstimate());
    }

    @Test
    void createAccumulator_returnsInt64StatsAccumulator() {
        SymLongGauge g = gauge();
        assertEquals(Int64StatsAccumulator.class, g.createAccumulator(0L).getClass());
    }

    @Test
    void close_withNoOtelHandle_doesNotThrow() {
        SymLongGauge g = gauge();
        assertDoesNotThrow(g::close);
    }

    @Test
    void setOtelHandle_close_invokesHandleClose() throws Exception {
        SymLongGauge g = gauge();
        ObservableLongGauge handle = mock(ObservableLongGauge.class);
        g.setOtelHandle(handle);
        g.close();
        verify(handle).close();
    }

    @Test
    void close_withOtelHandle_disablesMetric() {
        SymLongGauge g = gauge();
        g.setOtelHandle(mock(ObservableLongGauge.class));
        g.close();
        assertFalse(g.isEnabled());
    }

    @Test
    void close_whenOtelHandleThrows_doesNotPropagateAndStillDisablesMetric() {
        SymLongGauge g = gauge();
        ObservableLongGauge handle = mock(ObservableLongGauge.class);
        doThrow(new RuntimeException("otel close failed")).when(handle).close();
        g.setOtelHandle(handle);
        assertDoesNotThrow(g::close);
        assertFalse(g.isEnabled());
    }
}
