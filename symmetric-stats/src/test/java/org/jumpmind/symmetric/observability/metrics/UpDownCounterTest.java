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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;

class UpDownCounterTest {
    private static UpDownCounter counter() {
        return new UpDownCounter("test.updown", Attributes.empty(), List.of());
    }
    // ── add (negative allowed) ────────────────────────────────────────────────

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
    // ── decrement ─────────────────────────────────────────────────────────────

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
    // ── combined arithmetic ───────────────────────────────────────────────────

    @Test
    void add_positiveAndNegativeCombined_netResult() {
        UpDownCounter c = counter();
        c.add(10L);
        c.add(-3L);
        c.add(2L);
        c.decrement();
        assertEquals(8L, c.getValue());
    }

    // ── close ─────────────────────────────────────────────────────────────────

    @Test
    void close_disablesMetric() {
        UpDownCounter c = counter();
        c.close();
        assertFalse(c.isEnabled());
    }
}
