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
package org.jumpmind.symmetric.observability.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator;
import org.jumpmind.symmetric.observability.models.ObservationDouble;
import org.jumpmind.symmetric.observability.models.ObservationLong;
import org.junit.jupiter.api.Test;

class Int64StatsAccumulatorTest {
    // Jan 1 2020 00:00:00 UTC — aligned to 5-minute boundary
    private static final long T = 1_577_836_800_000L;
    private static final long D = AbstractStatsAccumulator.INTERVAL_DURATION_MS;

    @Test
    void constructor_carryForwardLong_usedAsLastValue() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 42L);
        assertEquals(42.0, acc.getLastValueAsDouble(), 1e-9);
        assertEquals(42L, acc.getLastValueAsLong());
    }

    @Test
    void constructor_noArg_lastValueIsZero() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T);
        assertEquals(0L, acc.getLastValueAsLong());
    }

    @Test
    void addObservation_doubleValueTruncatedToLong() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 0L);
        acc.addObservation(new ObservationDouble(3.9, T));
        assertEquals(3L, acc.getLastValueAsLong());
    }

    @Test
    void addObservation_negativeTruncation_towardsZero() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 0L);
        acc.addObservation(new ObservationDouble(-2.9, T));
        assertEquals(-2L, acc.getLastValueAsLong());
    }

    @Test
    void addObservation_longObs_storedWithinDoubleRepresentableRange() {
        // Values up to 2^53 are exactly representable as double, so no precision loss
        long exactValue = 9_007_199_254_740_992L; // 2^53
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 0L);
        acc.addObservation(new ObservationLong(exactValue, T));
        assertEquals(exactValue, acc.getLastValueAsLong());
    }

    @Test
    void addObservation_tracksMinMaxAsLong() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 0L);
        acc.addObservation(new ObservationLong(100L, T));
        acc.addObservation(new ObservationLong(20L, T + 1000));
        assertEquals(0.0, acc.getMinAsDouble(), 1e-9); // carry-forward (0) is lower than any obs
        assertEquals(100.0, acc.getMaxAsDouble(), 1e-9);
    }

    @Test
    void computeAvg_singleObsAtIntervalStart_equalsObsValue() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 0L);
        acc.addObservation(new ObservationLong(10L, T));
        acc.close();
        assertEquals(10.0, acc.computeAvg(), 1e-9);
    }

    @Test
    void computeAvg_noObs_returnsCarryForwardAsDouble() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 7L);
        acc.close();
        assertEquals(7.0, acc.computeAvg(), 1e-9);
    }

    @Test
    void createNext_carriesLongValueWithoutDoubleRoundtrip() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 0L);
        acc.addObservation(new ObservationLong(55L, T + 1000));
        IStatsAccumulator next = acc.createNext();
        assertTrue(next instanceof Int64StatsAccumulator);
        assertEquals(55L, ((Int64StatsAccumulator) next).getLastValueAsLong());
    }

    @Test
    void createNext_startsAtNextIntervalBoundary() {
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 0L);
        IStatsAccumulator next = acc.createNext();
        assertEquals(T + D, next.getIntervalStart());
        assertEquals(T + 2 * D, next.getIntervalEnd());
    }

    @Test
    void createNext_carryForwardPreservedInChain() {
        // Each window in the chain should carry forward the last long value
        Int64StatsAccumulator first = new Int64StatsAccumulator(T, 0L);
        first.addObservation(new ObservationLong(99L, T));
        IStatsAccumulator second = first.createNext();
        IStatsAccumulator third = second.createNext();
        assertTrue(third instanceof Int64StatsAccumulator);
        assertEquals(99L, ((Int64StatsAccumulator) third).getLastValueAsLong());
    }

    @Test
    void noArgConstructor_createsAccumulatorAtCurrentIntervalBoundary() {
        long before = AbstractStatsAccumulator.calculateIntervalStart(System.currentTimeMillis());
        Int64StatsAccumulator acc = new Int64StatsAccumulator();
        long after = AbstractStatsAccumulator.calculateIntervalStart(System.currentTimeMillis());
        assertTrue(acc.getIntervalStart() >= before && acc.getIntervalStart() <= after);
        assertEquals(0L, acc.getLastValueAsLong());
    }

    @Test
    void closeAtObservation_withNonZeroCarryForward_includesLastValueInWeightedAvg() {
        // Carry-forward = 4.0; closeAtObservation at intervalEnd covers the lastValue != INTERVAL_VALUE_DEFAULT
        Int64StatsAccumulator acc = new Int64StatsAccumulator(T, 10L);
        acc.closeAtObservation(T + D);
        assertEquals(10.0, acc.computeAvg(), 1e-9);
    }
}
