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
package org.jumpmind.symmetric.observability.stats;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.models.ObservationDouble;
import org.junit.jupiter.api.Test;

class Float64StatsAccumulatorTest {
    private static final long T = 1_577_836_800_000L;
    private static final long D = AbstractStatsAccumulator.INTERVAL_DURATION_MS; // 300_000

    @Test
    void calculateIntervalStart_alignedTimestamp_returnsSame() {
        assertEquals(T, AbstractStatsAccumulator.calculateIntervalStart(T));
    }

    @Test
    void calculateIntervalStart_midInterval_roundsDown() {
        assertEquals(T, AbstractStatsAccumulator.calculateIntervalStart(T + 150_000));
    }

    @Test
    void calculateIntervalStart_oneBeforeNextBoundary_stillInCurrentWindow() {
        assertEquals(T, AbstractStatsAccumulator.calculateIntervalStart(T + D - 1));
    }

    @Test
    void calculateIntervalStart_atNextBoundary_startsNextWindow() {
        assertEquals(T + D, AbstractStatsAccumulator.calculateIntervalStart(T + D));
    }

    @Test
    void isInScope_timestampAtStart_returnsTrue() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        assertTrue(acc.isInScope(T));
    }

    @Test
    void isInScope_timestampOneBeforeEnd_returnsTrue() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        assertTrue(acc.isInScope(T + D - 1));
    }

    @Test
    void isInScope_timestampAtEnd_returnsFalse() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        assertFalse(acc.isInScope(T + D));
    }

    @Test
    void isInScope_timestampBeforeStart_returnsFalse() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        assertFalse(acc.isInScope(T - 1));
    }

    @Test
    void constructor_carryForwardValue_usedAsLastValue() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 7.5);
        assertEquals(7.5, acc.getLastValueAsDouble(), 1e-9);
    }

    @Test
    void constructor_noCarryForward_lastValueIsZero() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        assertEquals(0.0, acc.getLastValueAsDouble(), 1e-9);
    }

    @Test
    void addObservation_singleObs_updatesMinMaxLastValue() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        acc.addObservation(new ObservationDouble(5.0, T));
        assertEquals(5.0, acc.getLastValueAsDouble(), 1e-9);
        assertEquals(0.0, acc.getMinAsDouble(), 1e-9); // carry-forward (0.0) is lower than obs (5.0)
        assertEquals(5.0, acc.getMaxAsDouble(), 1e-9);
    }

    @Test
    void addObservation_twoObs_tracksMinAndMax() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        acc.addObservation(new ObservationDouble(10.0, T));
        acc.addObservation(new ObservationDouble(3.0, T + 1000));
        assertEquals(0.0, acc.getMinAsDouble(), 1e-9); // carry-forward (0.0) is lower than any obs
        assertEquals(10.0, acc.getMaxAsDouble(), 1e-9);
        assertEquals(3.0, acc.getLastValueAsDouble(), 1e-9);
    }

    @Test
    void computeAvg_singleObsAtIntervalStart_equalsObsValue() {
        // carry-forward=0; obs at T=value 5; close fills rest of interval at 5 → avg=5.0
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 0.0);
        acc.addObservation(new ObservationDouble(5.0, T));
        acc.close();
        assertEquals(5.0, acc.computeAvg(), 1e-9);
    }

    @Test
    void computeAvg_noObs_returnsCarryForwardValue() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 3.14);
        acc.close();
        assertEquals(3.14, acc.computeAvg(), 1e-9);
    }

    @Test
    void computeAvg_obsAtMidpoint_timeWeightedHalfAndHalf() {
        // carry-forward=0 for first 150_000ms, then 10 for last 150_000ms → avg=5.0
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 0.0);
        acc.addObservation(new ObservationDouble(10.0, T + D / 2));
        acc.close();
        assertEquals(5.0, acc.computeAvg(), 1e-9);
    }

    @Test
    void computeAvg_twoObsEqualHalves_averagesCorrectly() {
        // obs at T (value=5): no weighted delta yet; obs at midpoint (value=10)
        // → value 5 for 150_000ms, value 10 for 150_000ms → avg=7.5
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 0.0);
        acc.addObservation(new ObservationDouble(5.0, T));
        acc.addObservation(new ObservationDouble(10.0, T + D / 2));
        acc.close();
        assertEquals(7.5, acc.computeAvg(), 1e-9);
    }

    @Test
    void stdDev_constantValue_isZero() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 0.0);
        acc.addObservation(new ObservationDouble(5.0, T));
        acc.close();
        assertEquals(0.0, acc.toStats().getStdDeviation(), 1e-9);
    }

    @Test
    void stdDev_twoEqualHalves_isCorrect() {
        // E[X²] = (25*150_000 + 100*150_000)/300_000 = 62.5; var = 62.5 − 56.25 = 6.25; σ = 2.5
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 0.0);
        acc.addObservation(new ObservationDouble(5.0, T));
        acc.addObservation(new ObservationDouble(10.0, T + D / 2));
        acc.close();
        assertEquals(2.5, acc.toStats().getStdDeviation(), 1e-9);
    }

    @Test
    void createNext_carriesLastValueForward() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 0.0);
        acc.addObservation(new ObservationDouble(7.0, T + 1000));
        IStatsAccumulator next = acc.createNext();
        assertEquals(7.0, next.getLastValueAsDouble(), 1e-9);
    }

    @Test
    void createNext_startsAtNextIntervalBoundary() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        IStatsAccumulator next = acc.createNext();
        assertEquals(T + D, next.getIntervalStart());
        assertEquals(T + 2 * D, next.getIntervalEnd());
    }

    @Test
    void createNext_producesFloat64Accumulator() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        IStatsAccumulator next = acc.createNext();
        assertTrue(next instanceof Float64StatsAccumulator);
    }

    @Test
    void toStats_countMatchesObservationCount() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 0.0);
        acc.addObservation(new ObservationDouble(1.0, T));
        acc.addObservation(new ObservationDouble(2.0, T + 1000));
        acc.addObservation(new ObservationDouble(3.0, T + 2000));
        acc.close();
        assertEquals(3, acc.toStats().getObservationCount());
    }

    @Test
    void toStats_noObs_countZero_avgIsCarryForward() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 5.0);
        acc.close();
        ISymIntervalStats stats = acc.toStats();
        assertEquals(0, stats.getObservationCount());
        assertEquals(5.0, stats.getAvg(), 1e-9);
    }

    @Test
    void toStats_intervalBoundsMatchAccumulator() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 0.0);
        acc.close();
        ISymIntervalStats stats = acc.toStats();
        assertEquals(T, stats.getStartEpoch());
        assertEquals(T + D, stats.getEndEpoch());
    }

    @Test
    void closeAtObservation_atIntervalEnd_doesNotThrow() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        assertDoesNotThrow(() -> acc.closeAtObservation(T + D));
    }

    @Test
    void closeAtObservation_afterIntervalEnd_throwsIllegalArgument() {
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T);
        assertThrows(IllegalArgumentException.class, () -> acc.closeAtObservation(T + D + 1));
    }

    @Test
    void closeAtObservation_withNonZeroCarryForward_includesLastValueInWeightedAvg() {
        // Carry-forward = 4.0; closeAtObservation at intervalEnd covers the lastValue != INTERVAL_VALUE_DEFAULT
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 4.0);
        acc.closeAtObservation(T + D);
        assertEquals(4.0, acc.computeAvg(), 1e-9);
    }

    @Test
    void close_afterCloseAtObservation_zeroDelta_doesNotDoubleCount() {
        // closeAtObservation sets lastTimestamp = intervalEnd; subsequent close()
        // sees delta = 0 and skips the weighted-sum update entirely.
        Float64StatsAccumulator acc = new Float64StatsAccumulator(T, 6.0);
        acc.closeAtObservation(T + D);
        long ts = acc.close();
        assertEquals(T + D, ts);
        // avg should still reflect the original close-at-observation weight only
        assertEquals(6.0, acc.computeAvg(), 1e-9);
    }

    @Test
    void noArgConstructor_createsAccumulatorAtCurrentIntervalBoundary() {
        long before = AbstractStatsAccumulator.calculateIntervalStart(System.currentTimeMillis());
        Float64StatsAccumulator acc = new Float64StatsAccumulator();
        long after = AbstractStatsAccumulator.calculateIntervalStart(System.currentTimeMillis());
        assertTrue(acc.getIntervalStart() >= before && acc.getIntervalStart() <= after);
        assertEquals(0.0, acc.getLastValueAsDouble(), 1e-9);
    }
}
