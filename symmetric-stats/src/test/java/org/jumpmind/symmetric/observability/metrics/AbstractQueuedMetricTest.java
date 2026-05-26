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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricContext;
import org.jumpmind.symmetric.observability.interfaces.ISymObservation;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.MetricConfigurationException;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.jumpmind.symmetric.observability.models.ObservationLong;
import org.jumpmind.symmetric.observability.stats.AbstractStatsAccumulator;
import org.jumpmind.symmetric.observability.stats.Float64StatsAccumulator;
import org.jumpmind.symmetric.observability.stats.Int64StatsAccumulator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import io.opentelemetry.api.common.Attributes;

class AbstractQueuedMetricTest {
    // Jan 1 2020 00:00:00 UTC — aligned to 5-minute boundary
    private static final long T = 1_577_836_800_000L;
    private static final long D = AbstractStatsAccumulator.INTERVAL_DURATION_MS; // 300_000

    private static UpDownCounter newCounter() {
        UpDownCounter metric = new UpDownCounter(new SymMetricDefinition("test.metric", "", "", InstrumentType.UPDOWN_COUNTER), Attributes.empty(),
                MetricAttributeList.of());
        metric.open(null);
        return metric;
    }

    private static UpDownCounter newCounterWithAttrs(MetricAttributeList attrs) {
        UpDownCounter metric = new UpDownCounter(new SymMetricDefinition("test.metric", "", "", InstrumentType.UPDOWN_COUNTER), Attributes.empty(), attrs);
        metric.open(null);
        return metric;
    }

    private static ObservationLong obs(long value, long timestamp) {
        return new ObservationLong(value, timestamp);
    }

    private static class BrokenProcessingMetric extends UpDownCounter {
        BrokenProcessingMetric() {
            super(new SymMetricDefinition("test.metric", "", "", InstrumentType.UPDOWN_COUNTER), Attributes.empty(), MetricAttributeList.of());
        }

        @Override
        public int processObservations(List<ISymObservation> obs) {
            throw new RuntimeException("simulated processing failure");
        }
    }

    private static class FailingCloseMetric extends UpDownCounter {
        FailingCloseMetric() {
            super(new SymMetricDefinition("test.metric", "", "", InstrumentType.UPDOWN_COUNTER), Attributes.empty(), MetricAttributeList.of());
        }

        @Override
        public void closeExpiredAccumulatorIfNeeded(long epochMillis) {
            throw new RuntimeException("simulated close failure");
        }
    }

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
        assertDoesNotThrow(() -> m.closeExpiredAccumulatorIfNeeded(T + D));
    }

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

    @Test
    void isEnabled_newMetric_returnsTrue() {
        UpDownCounter m = newCounter();
        assertTrue(m.isEnabled());
    }

    @Test
    void isOpen_afterClose_returnsFalse() {
        UpDownCounter metric = newCounter();
        metric.close();
        assertFalse(metric.isOpen());
    }

    @Test
    void getAttributes_returnsEmptyList() {
        UpDownCounter m = newCounter();
        assertNotNull(m.getAttributes());
        assertTrue(m.getAttributes().isEmpty());
    }

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

    @Test
    void getLastModified_returnsTimestamp() {
        UpDownCounter m = newCounter();
        assertTrue(m.getLastModified() > 0);
    }

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

    @Test
    void processAllObservations_withObservations_processesAll() {
        UpDownCounter m = newCounter();
        m.addObservation(obs(1L, T));
        m.addObservation(obs(2L, T + 1));
        m.processAllObservations();
        assertEquals(0, m.getObservationsCountEstimate());
    }

    @Test
    void processAllObservationsAndRefreshInterval_doesNotThrow() {
        UpDownCounter m = newCounter();
        m.addObservation(obs(5L, T));
        assertDoesNotThrow(m::processAllObservationsAndRefreshInterval);
    }

    @Test
    void closeCompletedIntervals_withNoAccumulator_doesNotThrow() {
        UpDownCounter m = newCounter();
        assertDoesNotThrow(m::closeCompletedIntervals);
    }

    @Test
    void closeCompletedIntervals_withExpiredAccumulator_closesInterval() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(5L, T)); // opens window at T (epoch 2020) — already expired
        m.closeCompletedIntervals(); // System.currentTimeMillis() >> T+D, so window closes
        assertFalse(m.exportCompletedIntervals().isEmpty());
    }

    @Test
    void processObservations_emptyList_returnsZero() {
        UpDownCounter m = newCounter();
        assertEquals(0, m.processObservations(List.of()));
    }

    @Test
    void processObservations_singleObs_returnsOne() {
        UpDownCounter m = newCounter();
        assertEquals(1, m.processObservations(List.of(obs(5L, T))));
    }

    @Test
    void processObservations_multipleObs_returnsTotalProcessedCount() {
        UpDownCounter m = newCounter();
        List<ISymObservation> list = List.of(obs(1L, T), obs(2L, T + 1000), obs(3L, T + 2000));
        assertEquals(3, m.processObservations(list));
    }

    @Test
    void processObservations_delinquentMixed_returnsOnlyValidCount() {
        UpDownCounter m = newCounter();
        m.processObservation(obs(10L, T + D)); // open window at T+D
        // obs at T is delinquent (before current window), obs at T+D+1 is in window
        List<ISymObservation> list = List.of(obs(1L, T), obs(5L, T + D + 1000));
        assertEquals(1, m.processObservations(list));
    }

    @Test
    void processAllObservations_emptyQueue_doesNotThrow() {
        UpDownCounter m = newCounter();
        assertDoesNotThrow(m::processAllObservations);
        assertEquals(0, m.getObservationsCountEstimate());
    }

    @Test
    void createAccumulator_returnsInt64AccumulatorWithCorrectStart() {
        UpDownCounter m = newCounter();
        IStatsAccumulator acc = m.createAccumulator(T);
        assertInstanceOf(Int64StatsAccumulator.class, acc);
        assertEquals(T, acc.getIntervalStart());
    }

    @Test
    void getAttributes_nonEmptyList_returnsProvidedAttributes() {
        MetricAttribute attr = new MetricAttribute("channel", Constants.CHANNEL_DEFAULT);
        UpDownCounter m = newCounterWithAttrs(MetricAttributeList.of(attr));
        MetricAttributeList attrs = m.getAttributes();
        assertEquals(1, attrs.size());
        assertEquals(attr, attrs.get(0));
    }

    @Test
    void getLastModified_updatesAfterAddObservation() {
        UpDownCounter m = newCounter();
        long before = m.getLastModified();
        m.addObservation(obs(1L, T));
        assertTrue(m.getLastModified() >= before);
    }

    @Test
    void seedWorkset_belowMinIntervals_doesNotThrowAndCallsSeed() {
        UpDownCounter m = newCounter();
        List<ISymIntervalStats> history = List.of(
                new MetricIntervalStats(T, T + D, 1.0, 0.0, 2.0, 0.0, 1, 1.0, false),
                new MetricIntervalStats(T + D, T + 2 * D, 2.0, 1.0, 3.0, 0.5, 2, 2.0, false));
        assertDoesNotThrow(() -> m.seedWorkset(history));
        // subsequent observation processing still works
        assertEquals(1, m.processObservation(obs(5L, T)));
    }

    @Test
    void seedWorkset_sufficientIntervals_primedWorkset() {
        UpDownCounter m = newCounter();
        List<ISymIntervalStats> history = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            history.add(new MetricIntervalStats(T + (long) i * D, T + (long) (i + 1) * D,
                    1.0, 0.0, 2.0, 0.0, 1, 1.0, false));
        }
        assertDoesNotThrow(() -> m.seedWorkset(history));
    }

    @Test
    void getObservationsCountEstimate_afterAddingObservations_returnsCount() {
        UpDownCounter m = newCounter();
        m.addObservation(obs(1L, T));
        m.addObservation(obs(2L, T + 1));
        m.addObservation(obs(3L, T + 2));
        assertEquals(3, m.getObservationsCountEstimate());
    }

    @Test
    void processObservation_extremeValueAfterSeededWorkset_completedIntervalTaggedAsOutlier() {
        UpDownCounter m = newCounter();
        // Seed workset with IQR_INTERVALS_MIN uniform intervals so outlier detection is active
        List<ISymIntervalStats> history = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            history.add(new MetricIntervalStats(T - (long) (i + 1) * D, T - (long) i * D,
                    1.0, 1.0, 1.0, 0.0, 1, 1.0, false));
        }
        m.seedWorkset(history);
        // Extreme value in window [T, T+D) — far above the IQR fence
        m.processObservation(obs(100_000L, T));
        // Advance to next window to force-close the extreme interval
        m.processObservation(obs(1L, T + D));
        List<ISymIntervalStats> completed = m.exportCompletedIntervals();
        assertFalse(completed.isEmpty());
        assertTrue(completed.stream().anyMatch(ISymIntervalStats::isOutlier));
    }

    @Test
    void open_whenDisabled_throwsMetricConfigurationException() {
        UpDownCounter m = new UpDownCounter(new SymMetricDefinition("test.metric", "", "", InstrumentType.UPDOWN_COUNTER), Attributes.empty(),
                MetricAttributeList.of());
        m.isMetricEnabled = false;
        assertThrows(MetricConfigurationException.class, () -> m.open(null));
    }

    @Test
    void createAccumulator_doubleGauge_returnsFloat64AccumulatorWithCorrectStart() {
        SymDoubleGauge m = new SymDoubleGauge(new SymMetricDefinition("test.double", "", "", InstrumentType.DOUBLE_GAUGE), Attributes.empty(),
                MetricAttributeList.of());
        m.open(null);
        IStatsAccumulator acc = m.createAccumulator(T);
        assertInstanceOf(Float64StatsAccumulator.class, acc);
        assertEquals(T, acc.getIntervalStart());
    }

    @Test
    void addObservation_whenClosedWithDebugEnabled_logsAndIgnores() {
        Logger raw = LoggerFactory.getLogger(AbstractQueuedMetric.class);
        ch.qos.logback.classic.Logger logbackLogger = (raw instanceof ch.qos.logback.classic.Logger) ? (ch.qos.logback.classic.Logger) raw : null;
        Level original = logbackLogger != null ? logbackLogger.getLevel() : null;
        if (logbackLogger != null) {
            logbackLogger.setLevel(Level.DEBUG);
        }
        try {
            UpDownCounter m = newCounter();
            m.close();
            m.addObservation(obs(5L, T));
            assertEquals(0, m.getObservationsCountEstimate());
        } finally {
            if (logbackLogger != null) {
                logbackLogger.setLevel(original);
            }
        }
    }

    @Test
    void processAllObservations_whenProcessingThrows_swallowsException() {
        BrokenProcessingMetric m = new BrokenProcessingMetric();
        m.open(null);
        m.addObservation(obs(5L, T));
        assertDoesNotThrow(m::processAllObservations);
    }

    @Test
    void closeAccumulatorAndOpenNewOne_nullAccumulator_initializesNewAccumulator() throws Exception {
        UpDownCounter m = newCounter();
        m.currentIntervalAccumulator = null;
        Method method = AbstractQueuedMetric.class.getDeclaredMethod("closeAccumulatorAndOpenNewOne");
        method.setAccessible(true);
        method.invoke(m);
        assertNotNull(m.currentIntervalAccumulator);
    }

    @Test
    void closeCompletedIntervals_whenCloseThrows_recoversWithNewAccumulator() {
        FailingCloseMetric m = new FailingCloseMetric();
        m.open(null);
        assertDoesNotThrow(m::closeCompletedIntervals);
        assertNotNull(m.currentIntervalAccumulator);
    }
}
