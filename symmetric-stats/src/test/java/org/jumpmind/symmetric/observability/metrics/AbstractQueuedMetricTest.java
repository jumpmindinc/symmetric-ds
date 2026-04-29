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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricContext;
import org.jumpmind.symmetric.observability.interfaces.ISymObservation;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.models.ObservationLong;
import org.jumpmind.symmetric.observability.stats.AbstractStatsAccumulator;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;

/**
 * Tests the observation-processing pipeline in {@link AbstractQueuedMetric} via the {@link UpDownCounter} concrete subclass (same package, package-private
 * constructor).
 */
class AbstractQueuedMetricTest {
    // Jan 1 2020 00:00:00 UTC — aligned to 5-minute boundary
    private static final long T = 1_577_836_800_000L;
    private static final long D = AbstractStatsAccumulator.INTERVAL_DURATION_MS; // 300_000

    private static UpDownCounter newCounter() {
        return new UpDownCounter("test.metric", Attributes.empty(), List.of());
    }

    private static ObservationLong obs(long value, long timestamp) {
        return new ObservationLong(value, timestamp);
    }
    // ── addObservation ────────────────────────────────────────────────────────

    @Test
    void addObservation_enabled_enqueuesObservation() {
        UpDownCounter m = newCounter();
        m.addObservation(obs(5L, T));
        assertEquals(1, m.getObservationsCountEstimate());
    }

    @Test
    void addObservation_disabled_isIgnored() {
        UpDownCounter m = newCounter();
        m.close();
        m.addObservation(obs(5L, T));
        assertEquals(0, m.getObservationsCountEstimate());
    }
    // ── removeAllObservations ─────────────────────────────────────────────────

    @Test
    void removeAllObservations_emptyQueue_returnsEmptyList() {
        UpDownCounter m = newCounter();
        assertTrue(m.removeAllObservations().isEmpty());
    }

    @Test
    void removeAllObservations_clearsQueue() {
        UpDownCounter m = newCounter();
        m.addObservation(obs(1L, T));
        m.addObservation(obs(2L, T + 1));
        List<ISymObservation> removed = m.removeAllObservations();
        assertEquals(2, removed.size());
        assertEquals(0, m.getObservationsCountEstimate());
    }
    // ── processObservation ────────────────────────────────────────────────────

    @Test
    void processObservation_firstObs_createsAccumulatorAndReturnsOne() {
        UpDownCounter m = newCounter();
        assertEquals(1, m.processObservation(obs(5L, T)));
    }

    @Test
    void processObservation_sameWindow_addedToCurrentAccumulator() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T));
        assertEquals(1, m.processObservation(obs(10L, T + 1000)));
    }

    @Test
    void processObservation_futureWindow_closesCurrentAndOpensNew() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T)); // window [T, T+D)
        m.processObservation(obs(10L, T + D)); // window [T+D, T+2D) — closes previous
        List<ISymIntervalStats> completed = m.exportCompletedIntervals();
        assertEquals(1, completed.size());
        assertEquals(T, completed.get(0).getStartEpoch());
    }

    @Test
    void processObservation_delinquentObs_isDiscardedReturnsZero() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T + D)); // window [T+D, T+2D)
        int processed = m.processObservation(obs(1L, T)); // window [T, T+D) — delinquent
        assertEquals(0, processed);
    }

    @Test
    void processObservation_skipOneWindow_closesIntermediateWindow() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T)); // window 0
        m.processObservation(obs(10L, T + 2 * D)); // window 2 — closes windows 0 and 1
        List<ISymIntervalStats> completed = m.exportCompletedIntervals();
        assertEquals(2, completed.size());
    }
    // ── closeExpiredAccumulatorIfNeeded ───────────────────────────────────────

    @Test
    void closeExpiredAccumulatorIfNeeded_notYetExpired_doesNotCloseInterval() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T));
        m.closeExpiredAccumulatorIfNeeded(T); // intervalEnd = T+D, which is > T
        assertTrue(m.exportCompletedIntervals().isEmpty());
    }

    @Test
    void closeExpiredAccumulatorIfNeeded_atIntervalEnd_closesInterval() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T));
        m.closeExpiredAccumulatorIfNeeded(T + D); // exactly at intervalEnd
        List<ISymIntervalStats> completed = m.exportCompletedIntervals();
        assertEquals(1, completed.size());
        assertEquals(T, completed.get(0).getStartEpoch());
    }

    @Test
    void closeExpiredAccumulatorIfNeeded_noAccumulator_doesNotThrow() {
        UpDownCounter m = newCounter();
        m.closeExpiredAccumulatorIfNeeded(T + D); // accumulator is null, must not throw
    }
    // ── exportCompletedIntervals ──────────────────────────────────────────────

    @Test
    void exportCompletedIntervals_empty_returnsEmptyList() {
        UpDownCounter m = newCounter();
        assertTrue(m.exportCompletedIntervals().isEmpty());
    }

    @Test
    void exportCompletedIntervals_drains_subsequentCallReturnsEmpty() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T));
        m.processObservation(obs(10L, T + D)); // closes window 0
        m.exportCompletedIntervals();
        assertTrue(m.exportCompletedIntervals().isEmpty());
    }

    @Test
    void exportCompletedIntervals_statsAreNotOutlierByDefault() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T));
        m.processObservation(obs(10L, T + D));
        List<ISymIntervalStats> completed = m.exportCompletedIntervals();
        assertFalse(completed.get(0).isOutlier());
    }
    // ── seedWorkset ───────────────────────────────────────────────────────────

    @Test
    void seedWorkset_emptyList_doesNotThrow() {
        UpDownCounter m = newCounter();
        m.seedWorkset(List.of());
        // Must still process observations normally after seed
        assertEquals(1, m.processObservation(obs(5L, T)));
    }

    @Test
    void seedWorkset_nullList_doesNotThrow() {
        UpDownCounter m = newCounter();
        m.seedWorkset(null);
        assertEquals(1, m.processObservation(obs(5L, T)));
    }
    // ── isEnabled / close ─────────────────────────────────────────────────────

    @Test
    void isEnabled_newMetric_returnsTrue() {
        UpDownCounter m = newCounter();
        assertTrue(m.isEnabled());
    }

    @Test
    void isEnabled_afterClose_returnsFalse() {
        UpDownCounter m = newCounter();
        m.close();
        assertFalse(m.isEnabled());
    }

    // ── getAttributes ─────────────────────────────────────────────────────────

    @Test
    void getAttributes_returnsEmptyList() {
        UpDownCounter m = newCounter();
        assertNotNull(m.getAttributes());
        assertTrue(m.getAttributes().isEmpty());
    }

    // ── getContext / setContext ───────────────────────────────────────────────

    @Test
    void getContext_initiallyNull() {
        UpDownCounter m = newCounter();
        assertNull(m.getContext());
    }

    @Test
    void setContext_setsContext() {
        UpDownCounter m = newCounter();
        ISymMetricContext ctx = mock(ISymMetricContext.class);
        m.setContext(ctx);
        assertEquals(ctx, m.getContext());
    }

    @Test
    void setContext_calledTwice_firstValueWins() {
        UpDownCounter m = newCounter();
        ISymMetricContext first = mock(ISymMetricContext.class);
        ISymMetricContext second = mock(ISymMetricContext.class);
        m.setContext(first);
        m.setContext(second); // compareAndSet will not replace first
        assertEquals(first, m.getContext());
    }

    // ── getLastModified ───────────────────────────────────────────────────────

    @Test
    void getLastModified_returnsTimestamp() {
        UpDownCounter m = newCounter();
        assertTrue(m.getLastModified() > 0);
    }

    // ── getFactType / getMetricType ───────────────────────────────────────────

    @Test
    void getFactType_returnsCorrectType() {
        UpDownCounter m = newCounter();
        assertEquals(MetricFactType.INT64, m.getFactType());
    }

    @Test
    void getMetricType_returnsCorrectType() {
        UpDownCounter m = newCounter();
        assertEquals(InstrumentType.UPDOWN_COUNTER, m.getMetricType());
    }

    // ── processAllObservations ────────────────────────────────────────────────

    @Test
    void processAllObservations_withObservations_processesAll() {
        UpDownCounter m = newCounter();
        m.addObservation(obs(1L, T));
        m.addObservation(obs(2L, T + 1));
        m.processAllObservations();
        assertEquals(0, m.getObservationsCountEstimate());
    }

    // ── processAllObservationsAndRefreshInterval ──────────────────────────────

    @Test
    void processAllObservationsAndRefreshInterval_doesNotThrow() {
        UpDownCounter m = newCounter();
        m.addObservation(obs(5L, T));
        assertDoesNotThrow(m::processAllObservationsAndRefreshInterval);
    }

    // ── closeCompletedIntervals ───────────────────────────────────────────────

    @Test
    void closeCompletedIntervals_withNoAccumulator_doesNotThrow() {
        UpDownCounter m = newCounter();
        assertDoesNotThrow(m::closeCompletedIntervals);
    }
}
