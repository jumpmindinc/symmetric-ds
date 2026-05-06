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

import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;

/**
 * Tests {@link AbstractDoubleGaugeMetric} behaviour via the package-private {@link SymDoubleGauge} constructor.
 */
class SymDoubleGaugeTest {
    private static SymDoubleGauge gauge() {
        SymDoubleGauge gauge = new SymDoubleGauge(new SymMetricDefinition("test.double.gauge", "", "", InstrumentType.DOUBLE_GAUGE), Attributes.empty(), List
                .of());
        gauge.open(null);
        return gauge;
    }

    @Test
    void getValue_initiallyZero() {
        assertEquals(0.0, gauge().getValue(), 1e-9);
    }

    @Test
    void setValue_changesValue() {
        SymDoubleGauge gauge = gauge();
        gauge.setValue(3.14);
        assertEquals(3.14, gauge.getValue(), 1e-9);
    }

    @Test
    void setValue_enqueuessObservation() {
        SymDoubleGauge gauge = gauge();
        gauge.setValue(1.5);
        assertEquals(1, gauge.getObservationsCountEstimate());
    }

    @Test
    void add_accumulatesValue() {
        SymDoubleGauge gauge = gauge();
        gauge.add(1.1);
        gauge.add(2.2);
        assertEquals(3.3, gauge.getValue(), 1e-9);
    }

    @Test
    void add_enqueuessObservation() {
        SymDoubleGauge gauge = gauge();
        gauge.add(0.5);
        assertEquals(1, gauge.getObservationsCountEstimate());
    }

    @Test
    void close_withNoOtelHandle_doesNotThrow() {
        SymDoubleGauge gauge = gauge();
        assertDoesNotThrow(gauge::close);
    }

    @Test
    void open_close_invokesHandleClose() throws Exception {
        SymDoubleGauge gauge = gauge();
        ObservableDoubleGauge handle = mock(ObservableDoubleGauge.class);
        gauge.open(handle);
        gauge.close();
        verify(handle).close();
    }

    @Test
    void close_withOtelHandle_isOpenReturnsFalse() {
        SymDoubleGauge gauge = gauge();
        gauge.open(mock(ObservableDoubleGauge.class));
        gauge.close();
        assertFalse(gauge.isOpen());
    }

    @Test
    void close_whenOtelHandleThrows_doesNotPropagateAndIsOpenReturnsFalse() {
        SymDoubleGauge gauge = gauge();
        ObservableDoubleGauge handle = mock(ObservableDoubleGauge.class);
        doThrow(new RuntimeException("otel close failed")).when(handle).close();
        gauge.open(handle);
        assertDoesNotThrow(gauge::close);
        assertFalse(gauge.isOpen());
    }
}
