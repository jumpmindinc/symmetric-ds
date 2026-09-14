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
package org.jumpmind.symmetric.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

class UpDownCounterTest {
    private static UpDownCounter counter() {
        UpDownCounter counter = new UpDownCounter(new SymMetricDefinition("test.updown", "", "", InstrumentType.UPDOWN_COUNTER), Attributes.empty(),
                MetricAttributeList.of());
        counter.open(null);
        return counter;
    }

    @Test
    void add_negativeDelta_isAllowed() {
        UpDownCounter counter = counter();
        counter.add(10L);
        counter.add(-3L);
        assertEquals(7L, counter.getValue());
    }

    @Test
    void add_negativeDelta_enqueuessObservation() {
        UpDownCounter counter = counter();
        counter.add(5L);
        counter.add(-2L);
        assertEquals(2, counter.getObservationsCountEstimate());
    }

    @Test
    void add_belowZero_isAllowed() {
        UpDownCounter counter = counter();
        counter.add(-5L);
        assertEquals(-5L, counter.getValue());
    }

    @Test
    void decrement_subtractsOne() {
        UpDownCounter counter = counter();
        counter.add(5L);
        counter.decrement();
        assertEquals(4L, counter.getValue());
    }

    @Test
    void decrement_enqueuessObservation() {
        UpDownCounter counter = counter();
        counter.decrement();
        assertEquals(1, counter.getObservationsCountEstimate());
    }

    @Test
    void add_positiveAndNegativeCombined_netResult() {
        UpDownCounter counter = counter();
        counter.add(10L);
        counter.add(-3L);
        counter.add(2L);
        counter.decrement();
        assertEquals(8L, counter.getValue());
    }

    @Test
    void close_isOpenReturnsFalse() {
        UpDownCounter counter = counter();
        counter.close();
        assertFalse(counter.isOpen());
    }

    @Test
    void open_close_invokesHandleClose() {
        UpDownCounter counter = counter();
        ObservableLongUpDownCounter handle = mock(ObservableLongUpDownCounter.class);
        counter.open(handle);
        counter.close();
        verify(handle).close();
    }

    @Test
    void close_withOtelHandle_isOpenReturnsFalse() {
        UpDownCounter counter = counter();
        counter.open(mock(ObservableLongUpDownCounter.class));
        counter.close();
        assertFalse(counter.isOpen());
    }

    @Test
    void close_whenOtelHandleThrows_doesNotPropagateAndIsOpenReturnsFalse() {
        UpDownCounter counter = counter();
        ObservableLongUpDownCounter handle = mock(ObservableLongUpDownCounter.class);
        doThrow(new RuntimeException("otel close failed")).when(handle).close();
        counter.open(handle);
        assertDoesNotThrow(counter::close);
        assertFalse(counter.isOpen());
    }
}
