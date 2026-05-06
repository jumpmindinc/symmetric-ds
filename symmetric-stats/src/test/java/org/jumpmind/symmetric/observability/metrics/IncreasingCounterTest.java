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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongCounter;

class IncreasingCounterTest {
    private static IncreasingCounter counter() {
        IncreasingCounter counter = new IncreasingCounter(new SymMetricDefinition("test.increasing", "", "", InstrumentType.COUNTER), Attributes.empty(), List
                .of());
        counter.open(null);
        return counter;
    }

    @Test
    void add_positiveValue_accumulatesInValue() {
        IncreasingCounter counter = counter();
        counter.add(10L);
        assertEquals(10L, counter.getValue());
    }

    @Test
    void add_multipleTimes_accumulates() {
        IncreasingCounter counter = counter();
        counter.add(3L);
        counter.add(7L);
        assertEquals(10L, counter.getValue());
    }

    @Test
    void add_positiveValue_enqueuessObservation() {
        IncreasingCounter counter = counter();
        counter.add(5L);
        assertEquals(1, counter.getObservationsCountEstimate());
    }

    @Test
    void add_negativeValue_throwsIllegalArgument() {
        IncreasingCounter counter = counter();
        assertThrows(IllegalArgumentException.class, () -> counter.add(-1L));
    }

    @Test
    void add_negativeValue_doesNotChangeValue() {
        IncreasingCounter counter = counter();
        counter.add(5L);
        try {
            counter.add(-2L);
        } catch (IllegalArgumentException ignored) {
        }
        assertEquals(5L, counter.getValue());
    }

    @Test
    void add_negativeValue_doesNotEnqueueObservation() {
        IncreasingCounter counter = counter();
        try {
            counter.add(-1L);
        } catch (IllegalArgumentException ignored) {
        }
        assertEquals(0, counter.getObservationsCountEstimate());
    }

    @Test
    void add_zero_isNoOp_noObservationEnqueued() {
        IncreasingCounter counter = counter();
        counter.add(5L);
        counter.add(0L);
        assertEquals(5L, counter.getValue());
        assertEquals(1, counter.getObservationsCountEstimate());
    }

    @Test
    void increment_addsOne() {
        IncreasingCounter counter = counter();
        counter.increment();
        assertEquals(1L, counter.getValue());
    }

    @Test
    void increment_multiple_accumulates() {
        IncreasingCounter counter = counter();
        counter.increment();
        counter.increment();
        counter.increment();
        assertEquals(3L, counter.getValue());
    }

    @Test
    void increment_enqueuessObservation() {
        IncreasingCounter counter = counter();
        counter.increment();
        assertEquals(1, counter.getObservationsCountEstimate());
    }

    @Test
    void close_stopsObservationsFromBeingQueued() {
        IncreasingCounter counter = counter();
        counter.add(5L);
        counter.close();
        counter.add(10L);
        assertEquals(1, counter.getObservationsCountEstimate());
    }

    @Test
    void close_withNoOtelHandle_isOpenReturnsFalse() {
        IncreasingCounter counter = counter();
        counter.close();
        assertFalse(counter.isOpen());
    }

    @Test
    void open_close_invokesHandleClose() throws Exception {
        IncreasingCounter counter = counter();
        ObservableLongCounter handle = mock(ObservableLongCounter.class);
        counter.open(handle);
        counter.close();
        verify(handle).close();
    }

    @Test
    void close_withOtelHandle_isOpenReturnsFalse() {
        IncreasingCounter counter = counter();
        counter.open(mock(ObservableLongCounter.class));
        counter.close();
        assertFalse(counter.isOpen());
    }

    @Test
    void close_whenOtelHandleThrows_doesNotPropagateAndIsOpenReturnsFalse() {
        IncreasingCounter counter = counter();
        ObservableLongCounter handle = mock(ObservableLongCounter.class);
        doThrow(new RuntimeException("otel close failed")).when(handle).close();
        counter.open(handle);
        assertDoesNotThrow(counter::close);
        assertFalse(counter.isOpen());
    }
}
