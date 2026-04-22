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

import static org.jumpmind.symmetric.observability.stats.MetricSeriesSlidingWorkset.IQR_INTERVALS_MAX;
import static org.jumpmind.symmetric.observability.stats.MetricSeriesSlidingWorkset.IQR_INTERVALS_MIN;
import static org.jumpmind.symmetric.observability.stats.MetricSeriesSlidingWorkset.IQR_OUTLIERS_MAX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricSeriesInterquartileRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricSeriesSlidingWorksetTest {
    // 2020-01-01 00:00:00 UTC — well after Y2K
    private static final long T_2020 = 1_577_836_800_000L;
    private static final long D = 300_000L; // 5-minute window
    private MetricSeriesSlidingWorkset workset;

    @BeforeEach
    void setUp() {
        workset = new MetricSeriesSlidingWorkset();
    }

    /** Interval with no meaningful timestamp (for tests that don't exercise time-ordering). */
    private static MetricIntervalStats interval(double mean, double min, double max) {
        return new MetricIntervalStats(0L, D, mean, min, max, 0.0, 1, mean, false);
    }

    /** Interval with a real post-2000 start time. */
    private static MetricIntervalStats timedInterval(long start, double mean, double min, double max) {
        return new MetricIntervalStats(start, start + D, mean, min, max, 0.0, 1, mean, false);
    }

    /**
     * Seeds the workset with {@code count} uniform untimed intervals in a single {@code seed()} call. All intervals have the same value so the IQR fences
     * collapse to that value.
     */
    private void seedUniform(int count, double value) {
        List<ISymIntervalStats> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            history.add(interval(value, value, value));
        }
        workset.seed(history);
    }

    /**
     * Seeds the workset with {@code count} timed intervals starting at {@code t0}, each separated by {@code D} milliseconds.
     */
    private void seedTimed(int count, double value, long t0) {
        List<ISymIntervalStats> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            history.add(timedInterval(t0 + (long) i * D, value, value, value));
        }
        workset.seed(history);
    }
    // -----------------------------------------------------------------------
    // computePercentiles — direct unit tests
    // -----------------------------------------------------------------------

    @Test
    void computePercentiles_evenDistribution() {
        // Q1: index=0.75 → 1+0.75*(2-1)=1.75; Q3: index=2.25 → 3+0.25*(4-3)=3.25; IQR=1.5
        double[] sorted = { 1.0, 2.0, 3.0, 4.0 };
        MetricSeriesInterquartileRange result = MetricSeriesSlidingWorkset.computePercentiles(sorted);
        assertEquals(1.75, result.q1(), 1e-9);
        assertEquals(2.5, result.q2(), 1e-9);
        assertEquals(1.5, result.iqr(), 1e-9);
        assertEquals(-2.75, result.lowerOutlierFence(), 1e-9);
        assertEquals(7.75, result.upperOutlierFence(), 1e-9);
    }

    @Test
    void computePercentiles_twoElements() {
        // Q1=25, Q2=50, Q3=75, IQR=50, lowerFence=-125, upperFence=225
        double[] sorted = { 0.0, 100.0 };
        MetricSeriesInterquartileRange result = MetricSeriesSlidingWorkset.computePercentiles(sorted);
        assertEquals(25.0, result.q1(), 1e-9);
        assertEquals(50.0, result.q2(), 1e-9);
        assertEquals(50.0, result.iqr(), 1e-9);
        assertEquals(-125.0, result.lowerOutlierFence(), 1e-9);
        assertEquals(225.0, result.upperOutlierFence(), 1e-9);
    }

    @Test
    void computePercentiles_uniformValues_iqrIsZero_deadBandAppliesToFences() {
        // IQR=0 with Q2=5: iqrFloor = 5*0.05/3 ≈ 0.0833, so fences expand by ±0.25 (5%)
        double[] sorted = { 5.0, 5.0, 5.0, 5.0 };
        MetricSeriesInterquartileRange result = MetricSeriesSlidingWorkset.computePercentiles(sorted);
        assertEquals(0.0, result.iqr(), 1e-9);
        assertEquals(4.75, result.lowerOutlierFence(), 1e-9);
        assertEquals(5.25, result.upperOutlierFence(), 1e-9);
    }

    @Test
    void computePercentiles_uniformValues_smallDeviationWithinDeadBand_notOutlier() {
        // Baseline uniform at 50; value 51 is a 2% change — must stay within dead-band fences.
        // iqrFloor = 50*0.05/3 ≈ 0.833; fences = [50-2.5, 50+2.5] = [47.5, 52.5]
        double[] sorted = new double[300];
        java.util.Arrays.fill(sorted, 50.0);
        MetricSeriesInterquartileRange result = MetricSeriesSlidingWorkset.computePercentiles(sorted);
        assertTrue(51.0 <= result.upperOutlierFence(), "value 2% above median should be within dead-band upper fence");
        assertTrue(49.0 >= result.lowerOutlierFence(), "value 2% below median should be within dead-band lower fence");
    }

    @Test
    void computePercentiles_nullArray_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> MetricSeriesSlidingWorkset.computePercentiles(null));
    }

    @Test
    void computePercentiles_singleElement_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> MetricSeriesSlidingWorkset.computePercentiles(new double[] { 42.0 }));
    }
    // -----------------------------------------------------------------------
    // hasEnoughData
    // -----------------------------------------------------------------------

    @Test
    void hasEnoughData_belowMinimum_returnsFalse() {
        seedUniform(IQR_INTERVALS_MIN - 1, 50.0);
        assertFalse(workset.hasEnoughData());
    }

    @Test
    void hasEnoughData_atMinimum_returnsTrue() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        assertTrue(workset.hasEnoughData());
    }
    // -----------------------------------------------------------------------
    // detectOutlier — guards
    // -----------------------------------------------------------------------

    @Test
    void detectOutlier_notEnoughData_alwaysFalse() {
        seedUniform(IQR_INTERVALS_MIN - 1, 50.0);
        assertFalse(workset.detectOutlier(interval(9999.0, 9999.0, 9999.0)));
    }
    // -----------------------------------------------------------------------
    // detectOutlier — with enough data (seed=50, IQR=0, fences=[50,50])
    // -----------------------------------------------------------------------

    @Test
    void detectOutlier_valueEqualToFence_notOutlier() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        assertFalse(workset.detectOutlier(interval(50.0, 50.0, 50.0)));
    }

    @Test
    void detectOutlier_meanAboveUpperFence_isOutlier() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        assertTrue(workset.detectOutlier(interval(200.0, 50.0, 50.0)));
    }

    @Test
    void detectOutlier_meanBelowLowerFence_isOutlier() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        assertTrue(workset.detectOutlier(interval(-100.0, 50.0, 50.0)));
    }

    @Test
    void detectOutlier_minBelowLowerFence_isOutlier() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        assertTrue(workset.detectOutlier(interval(50.0, -100.0, 50.0)));
    }

    @Test
    void detectOutlier_maxAboveUpperFence_isOutlier() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        assertTrue(workset.detectOutlier(interval(50.0, 50.0, 200.0)));
    }
    // -----------------------------------------------------------------------
    // seed — basic collection API
    // -----------------------------------------------------------------------

    @Test
    void seed_extremeValue_addedToWorksetWithoutDetection() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        workset.seed(List.of(interval(99999.0, 99999.0, 99999.0)));
        assertEquals(IQR_INTERVALS_MIN + 1, workset.size());
    }

    @Test
    void seed_emptyCollection_worksetUnchanged() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        workset.seed(List.of());
        assertEquals(IQR_INTERVALS_MIN, workset.size());
    }

    @Test
    void seed_atMaxCapacity_additionalItemTrimsToMax() {
        seedUniform(IQR_INTERVALS_MAX, 50.0);
        assertEquals(IQR_INTERVALS_MAX, workset.size());
        workset.seed(List.of(interval(50.0, 50.0, 50.0)));
        assertEquals(IQR_INTERVALS_MAX, workset.size());
    }
    // -----------------------------------------------------------------------
    // seed — post-2000 timestamps, chronological ordering
    // -----------------------------------------------------------------------

    @Test
    void seed_timedIntervals_loadsAllWhenUnderCapacity() {
        seedTimed(IQR_INTERVALS_MIN, 50.0, T_2020);
        assertEquals(IQR_INTERVALS_MIN, workset.size());
        assertTrue(workset.hasEnoughData());
    }

    @Test
    void seed_timedIntervalsOutOfOrder_sortedByTimeBeforeLoad() {
        // Supply intervals in reverse chronological order; seed() must sort them.
        List<ISymIntervalStats> history = new ArrayList<>(IQR_INTERVALS_MIN);
        for (int i = IQR_INTERVALS_MIN - 1; i >= 0; i--) {
            history.add(timedInterval(T_2020 + (long) i * D, 50.0, 50.0, 50.0));
        }
        workset.seed(history);
        assertEquals(IQR_INTERVALS_MIN, workset.size());
        // IQR statistics must be valid regardless of insertion order
        assertFalse(workset.detectOutlier(timedInterval(T_2020, 50.0, 50.0, 50.0)));
    }
    // -----------------------------------------------------------------------
    // seed — older intervals discarded when collection exceeds IQR_INTERVALS_MAX
    // -----------------------------------------------------------------------

    @Test
    void seed_collectionExceedsMax_sizeIsCappedAtMax() {
        seedTimed(IQR_INTERVALS_MAX + IQR_INTERVALS_MIN, 50.0, T_2020);
        assertEquals(IQR_INTERVALS_MAX, workset.size());
    }

    @Test
    void seed_collectionExceedsMax_oldestIntervalsDiscarded() {
        // Oldest IQR_INTERVALS_MIN intervals have value=100; newest IQR_INTERVALS_MAX have value=50.
        // After seeding with the full collection, seed() must keep only the newest IQR_INTERVALS_MAX.
        // With all-50 workset: IQR=0, fences=[50,50] so value=100 is an outlier.
        // If the oldest (100s) were wrongly retained the IQR would widen and 100 would not be detected.
        int extra = IQR_INTERVALS_MIN;
        List<ISymIntervalStats> history = new ArrayList<>(IQR_INTERVALS_MAX + extra);
        for (int i = 0; i < extra; i++) {
            history.add(timedInterval(T_2020 + (long) i * D, 100.0, 100.0, 100.0));
        }
        for (int i = extra; i < extra + IQR_INTERVALS_MAX; i++) {
            history.add(timedInterval(T_2020 + (long) i * D, 50.0, 50.0, 50.0));
        }
        workset.seed(history);
        assertEquals(IQR_INTERVALS_MAX, workset.size());
        // Workset is uniform at 50 → fences=[50,50]
        assertFalse(workset.detectOutlier(timedInterval(T_2020, 50.0, 50.0, 50.0)));
        assertTrue(workset.detectOutlier(timedInterval(T_2020, 100.0, 100.0, 100.0)));
    }
    // -----------------------------------------------------------------------
    // add — normal interval goes to workset
    // -----------------------------------------------------------------------

    @Test
    void add_normalInterval_increasesWorksetSize() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        workset.add(interval(50.0, 50.0, 50.0));
        assertEquals(IQR_INTERVALS_MIN + 1, workset.size());
    }
    // -----------------------------------------------------------------------
    // add — outlier goes to outlier buffer, not workset
    // -----------------------------------------------------------------------

    @Test
    void add_outlierInterval_doesNotIncreaseWorksetSize() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        workset.add(interval(9999.0, 9999.0, 9999.0));
        assertEquals(IQR_INTERVALS_MIN, workset.size());
    }

    @Test
    void add_outlierBuffer_promotesToWorksetWhenFull() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        MetricIntervalStats outlier = interval(9999.0, 9999.0, 9999.0);
        for (int i = 0; i < IQR_OUTLIERS_MAX - 1; i++) {
            workset.add(outlier);
        }
        assertEquals(IQR_INTERVALS_MIN, workset.size()); // still buffered
        workset.add(outlier); // 10th outlier triggers promotion
        assertEquals(IQR_INTERVALS_MIN + IQR_OUTLIERS_MAX, workset.size());
    }
}
