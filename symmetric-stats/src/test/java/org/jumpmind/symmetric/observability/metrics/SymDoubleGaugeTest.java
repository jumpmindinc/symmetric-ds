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

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;

/**
 * Tests {@link AbstractDoubleGaugeMetric} behaviour via the package-private {@link SymDoubleGauge} constructor.
 */
class SymDoubleGaugeTest {
    private static SymDoubleGauge gauge() {
        return new SymDoubleGauge("test.double.gauge", Attributes.empty(), List.of());
    }

    @Test
    void getValue_initiallyZero() {
        assertEquals(0.0, gauge().getValue(), 1e-9);
    }

    @Test
    void setValue_changesValue() {
        SymDoubleGauge g = gauge();
        g.setValue(3.14);
        assertEquals(3.14, g.getValue(), 1e-9);
    }

    @Test
    void setValue_enqueuessObservation() {
        SymDoubleGauge g = gauge();
        g.setValue(1.5);
        assertEquals(1, g.getObservationsCountEstimate());
    }

    @Test
    void add_accumulatesValue() {
        SymDoubleGauge g = gauge();
        g.add(1.1);
        g.add(2.2);
        assertEquals(3.3, g.getValue(), 1e-9);
    }

    @Test
    void add_enqueuessObservation() {
        SymDoubleGauge g = gauge();
        g.add(0.5);
        assertEquals(1, g.getObservationsCountEstimate());
    }

    @Test
    void close_withNoOtelHandle_doesNotThrow() {
        SymDoubleGauge g = gauge();
        assertDoesNotThrow(g::close);
    }

    @Test
    void setOtelHandle_close_invokesHandleClose() throws Exception {
        SymDoubleGauge g = gauge();
        ObservableDoubleGauge handle = mock(ObservableDoubleGauge.class);
        g.setOtelHandle(handle);
        g.close();
        verify(handle).close();
    }

    @Test
    void close_withOtelHandle_disablesMetric() {
        SymDoubleGauge g = gauge();
        g.setOtelHandle(mock(ObservableDoubleGauge.class));
        g.close();
        assertFalse(g.isEnabled());
    }

    @Test
    void close_whenOtelHandleThrows_doesNotPropagateAndStillDisablesMetric() {
        SymDoubleGauge g = gauge();
        ObservableDoubleGauge handle = mock(ObservableDoubleGauge.class);
        doThrow(new RuntimeException("otel close failed")).when(handle).close();
        g.setOtelHandle(handle);
        assertDoesNotThrow(g::close);
        assertFalse(g.isEnabled());
    }
}
