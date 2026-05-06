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
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

class UpDownCounterTest {
    private static UpDownCounter counter() {
        return new UpDownCounter(new SymMetricDefinition("test.updown", "", "", InstrumentType.UPDOWN_COUNTER), Attributes.empty(), List.of());
    }

    @Test
    void add_negativeDelta_isAllowed() {
        UpDownCounter c = counter();
        c.add(10L);
        c.add(-3L);
        assertEquals(7L, c.getValue());
    }

    @Test
    void add_negativeDelta_enqueuessObservation() {
        UpDownCounter c = counter();
        c.add(5L);
        c.add(-2L);
        assertEquals(2, c.getObservationsCountEstimate());
    }

    @Test
    void add_belowZero_isAllowed() {
        UpDownCounter c = counter();
        c.add(-5L);
        assertEquals(-5L, c.getValue());
    }

    @Test
    void decrement_subtractsOne() {
        UpDownCounter c = counter();
        c.add(5L);
        c.decrement();
        assertEquals(4L, c.getValue());
    }

    @Test
    void decrement_enqueuessObservation() {
        UpDownCounter c = counter();
        c.decrement();
        assertEquals(1, c.getObservationsCountEstimate());
    }

    @Test
    void add_positiveAndNegativeCombined_netResult() {
        UpDownCounter c = counter();
        c.add(10L);
        c.add(-3L);
        c.add(2L);
        c.decrement();
        assertEquals(8L, c.getValue());
    }

    @Test
    void close_disablesMetric() {
        UpDownCounter c = counter();
        c.close();
        assertFalse(c.isEnabled());
    }

    @Test
    void setOtelHandle_close_invokesHandleClose() throws Exception {
        UpDownCounter c = counter();
        ObservableLongUpDownCounter handle = mock(ObservableLongUpDownCounter.class);
        c.setOtelHandle(handle);
        c.close();
        verify(handle).close();
    }

    @Test
    void close_withOtelHandle_disablesMetric() {
        UpDownCounter c = counter();
        c.setOtelHandle(mock(ObservableLongUpDownCounter.class));
        c.close();
        assertFalse(c.isEnabled());
    }

    @Test
    void close_whenOtelHandleThrows_doesNotPropagateAndStillDisablesMetric() {
        UpDownCounter c = counter();
        ObservableLongUpDownCounter handle = mock(ObservableLongUpDownCounter.class);
        doThrow(new RuntimeException("otel close failed")).when(handle).close();
        c.setOtelHandle(handle);
        assertDoesNotThrow(c::close);
        assertFalse(c.isEnabled());
    }
}
