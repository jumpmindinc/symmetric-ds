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

import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricSeriesInterquartileRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricSeriesSlidingWorksetTest {
    private MetricSeriesSlidingWorkset workset;

    @BeforeEach
    void setUp() {
        workset = new MetricSeriesSlidingWorkset();
    }

    private static MetricIntervalStats interval(double mean, double min, double max) {
        return new MetricIntervalStats(0L, 300_000L, mean, min, max, 0.0, 1, mean, false);
    }

    /** Seeds workset with count uniform intervals all having the same mean/min/max value. */
    private void seedUniform(int count, double value) {
        for (int i = 0; i < count; i++) {
            workset.seed(interval(value, value, value));
        }
    }

    // -----------------------------------------------------------------------
    // computePercentiles — direct unit tests
    // With uniform seed value=50: IQR=0 so fences both equal 50.
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
    void computePercentiles_uniformValues_iqrIsZero() {
        double[] sorted = { 5.0, 5.0, 5.0, 5.0 };
        MetricSeriesInterquartileRange result = MetricSeriesSlidingWorkset.computePercentiles(sorted);
        assertEquals(0.0, result.iqr(), 1e-9);
        assertEquals(5.0, result.lowerOutlierFence(), 1e-9);
        assertEquals(5.0, result.upperOutlierFence(), 1e-9);
    }

    @Test
    void computePercentiles_nullArray_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> MetricSeriesSlidingWorkset.computePercentiles(null));
    }

    @Test
    void computePercentiles_singleElement_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> MetricSeriesSlidingWorkset.computePercentiles(new double[]{ 42.0 }));
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
    // seed — bypasses outlier detection
    // -----------------------------------------------------------------------

    @Test
    void seed_extremeValue_addedToWorksetWithoutDetection() {
        seedUniform(IQR_INTERVALS_MIN, 50.0);
        workset.seed(interval(99999.0, 99999.0, 99999.0));
        assertEquals(IQR_INTERVALS_MIN + 1, workset.size());
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

    // -----------------------------------------------------------------------
    // Sliding window eviction
    // -----------------------------------------------------------------------

    @Test
    void seed_atMaxCapacity_evictsOldestEntry() {
        seedUniform(IQR_INTERVALS_MAX, 50.0);
        assertEquals(IQR_INTERVALS_MAX, workset.size());
        workset.seed(interval(50.0, 50.0, 50.0));
        assertEquals(IQR_INTERVALS_MAX, workset.size());
    }
}
