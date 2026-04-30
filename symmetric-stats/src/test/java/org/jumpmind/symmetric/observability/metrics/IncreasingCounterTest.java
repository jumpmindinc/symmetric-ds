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

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongCounter;

class IncreasingCounterTest {
    private static IncreasingCounter counter() {
        return new IncreasingCounter("test.increasing", Attributes.empty(), List.of());
    }
    // ── positive add ─────────────────────────────────────────────────────────

    @Test
    void add_positiveValue_accumulatesInValue() {
        IncreasingCounter c = counter();
        c.add(10L);
        assertEquals(10L, c.getValue());
    }

    @Test
    void add_multipleTimes_accumulates() {
        IncreasingCounter c = counter();
        c.add(3L);
        c.add(7L);
        assertEquals(10L, c.getValue());
    }

    @Test
    void add_positiveValue_enqueuessObservation() {
        IncreasingCounter c = counter();
        c.add(5L);
        assertEquals(1, c.getObservationsCountEstimate());
    }
    // ── rejection of negative deltas ─────────────────────────────────────────

    @Test
    void add_negativeValue_throwsIllegalArgument() {
        IncreasingCounter c = counter();
        assertThrows(IllegalArgumentException.class, () -> c.add(-1L));
    }

    @Test
    void add_negativeValue_doesNotChangeValue() {
        IncreasingCounter c = counter();
        c.add(5L);
        try {
            c.add(-2L);
        } catch (IllegalArgumentException ignored) {
        }
        assertEquals(5L, c.getValue());
    }

    @Test
    void add_negativeValue_doesNotEnqueueObservation() {
        IncreasingCounter c = counter();
        try {
            c.add(-1L);
        } catch (IllegalArgumentException ignored) {
        }
        assertEquals(0, c.getObservationsCountEstimate());
    }
    // ── zero delta ────────────────────────────────────────────────────────────

    @Test
    void add_zero_isNoOp_noObservationEnqueued() {
        IncreasingCounter c = counter();
        c.add(5L);
        c.add(0L);
        assertEquals(5L, c.getValue());
        assertEquals(1, c.getObservationsCountEstimate()); // only first add enqueued
    }
    // ── increment ─────────────────────────────────────────────────────────────

    @Test
    void increment_addsOne() {
        IncreasingCounter c = counter();
        c.increment();
        assertEquals(1L, c.getValue());
    }

    @Test
    void increment_multiple_accumulates() {
        IncreasingCounter c = counter();
        c.increment();
        c.increment();
        c.increment();
        assertEquals(3L, c.getValue());
    }

    @Test
    void increment_enqueuessObservation() {
        IncreasingCounter c = counter();
        c.increment();
        assertEquals(1, c.getObservationsCountEstimate());
    }
    // ── close ─────────────────────────────────────────────────────────────────

    @Test
    void close_stopsObservationsFromBeingQueued() {
        IncreasingCounter c = counter();
        c.add(5L); // queues 1 observation
        c.close();
        c.add(10L); // atomic value updates but observation is NOT queued (metric disabled)
        assertEquals(1, c.getObservationsCountEstimate());
    }

    @Test
    void close_withNoOtelHandle_disablesMetric() {
        IncreasingCounter c = counter();
        c.close();
        assertFalse(c.isEnabled());
    }
    // ── setOtelHandle / close with handle ─────────────────────────────────────

    @Test
    void setOtelHandle_close_invokesHandleClose() throws Exception {
        IncreasingCounter c = counter();
        ObservableLongCounter handle = mock(ObservableLongCounter.class);
        c.setOtelHandle(handle);
        c.close();
        verify(handle).close();
    }

    @Test
    void close_withOtelHandle_disablesMetric() {
        IncreasingCounter c = counter();
        c.setOtelHandle(mock(ObservableLongCounter.class));
        c.close();
        assertFalse(c.isEnabled());
    }

    @Test
    void close_whenOtelHandleThrows_doesNotPropagateAndStillDisablesMetric() {
        IncreasingCounter c = counter();
        ObservableLongCounter handle = mock(ObservableLongCounter.class);
        doThrow(new RuntimeException("otel close failed")).when(handle).close();
        c.setOtelHandle(handle);
        assertDoesNotThrow(c::close);
        assertFalse(c.isEnabled());
    }
}
