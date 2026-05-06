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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricSeriesInterquartileRange;

/**
 * Maintains a sliding workset of {@link ISymIntervalStats} records, computes the interquartile range (IQR) of their {@code mean} values, and detects outliers
 * using Tukey's fences. Addition of new outliers into the main workset is delayed until the internal outliers buffer is full.
 * <p>
 * See also: https://en.wikipedia.org/wiki/Interquartile_range
 */
public class MetricSeriesSlidingWorkset {
    protected static final int IQR_OUTLIERS_MAX = 10; // Maximum number of outlier intervals deferred for adoption into workset (should be < IQR_INTERVALS_MIN).
    protected static final int EXPIRE_OUTLIERS_RATIO = 7; // Multiplier (* time window size) for discarding an outlier (should be less than IQR_OUTLIERS_MAX).
    protected static final int IQR_INTERVALS_MIN = 100; // Minimum number of intervals required before IQR and outlier detection are active
    protected static final int IQR_INTERVALS_MAX = 200; // Maximum number of intervals retained; oldest is evicted when this is reached.
    protected static final double IQR_Q1_MULTIPLIER = 0.25; // Quartile of the smallest values
    protected static final double IQR_Q2_MULTIPLIER = 0.5; // Quartile of the ordinary median
    protected static final double IQR_Q3_MULTIPLIER = 0.75; // Quartile of the largest values
    /**
     * Multiplier applied to IQR to form Tukey's outer fences. Values beyond the outer fences are considered "significant" outliers. Values between the inner
     * and outer fences are considered "mild" outliers.
     */
    protected static final double INNER_FENCE_MULTIPLIER = 1.5;
    protected static final double OUTER_FENCE_MULTIPLIER = 3.0;
    protected static final double IQR_ZERO_FLAT_TOLERANCE = 0.05; // Dead-band fraction of Q2 applied when IQR=0; prevents flagging changes smaller than 5% of the
                                                               // median.
    private final ArrayDeque<ISymIntervalStats> intervals = new ArrayDeque<>(IQR_INTERVALS_MAX); // FILO: Oldest time window at the end of queue, newest first
    private final ArrayDeque<ISymIntervalStats> outliers = new ArrayDeque<>(IQR_OUTLIERS_MAX);

    public static int getMinIntervalsForOutlierDetection(){
        return IQR_INTERVALS_MIN;
    }

    /**
     * Adds an interval directly to the workset, bypassing outlier detection. Intended for seed data for outlier detection from historical entries at startup.
     */
    public void seed(Collection<ISymIntervalStats> history) {
        List<ISymIntervalStats> oldestLast = history.stream().sorted((a, b) -> b.compareTo(a)).limit(IQR_INTERVALS_MAX).toList();
        intervals.addAll(oldestLast);
        while (intervals.size() > IQR_INTERVALS_MAX) {
            intervals.removeLast();
        }
    }

    /** Evicts the oldest entry if the workset is at capacity, then appends interval. */
    private void addToWorkset(ISymIntervalStats interval) {
        if (intervals.size() >= IQR_INTERVALS_MAX) {
            intervals.removeLast();
        }
        intervals.add(interval);
        checkExpirationOfOldestOutlier(interval.getStartEpoch() - EXPIRE_OUTLIERS_RATIO * interval.getDurationMillis());
    }

    /** Adds to the outliers collection. If the outliers buffer is full, then moves all to the current workset (adopts all outliers as new trend). */
    private void addToOutliers(ISymIntervalStats interval) {
        ISymIntervalStats markedInterval = interval;
        if (!interval.isOutlier()) {
            markedInterval = interval.cloneOutlier(true);
        }
        outliers.add(markedInterval);
        if (outliers.size() >= IQR_OUTLIERS_MAX) {
            moveOutliersToWorkset();
        }
    }

    private ISymIntervalStats markAsOutlier(ISymIntervalStats interval) {
        if (interval.isOutlier()) {
            return interval;
        }
        return interval.cloneOutlier(true);
    }

    private void checkExpirationOfOldestOutlier(long cutOff) {
        if (outliers.isEmpty()) {
            return;
        }
        ISymIntervalStats oldestOutlier = outliers.peekLast();
        if (oldestOutlier != null && oldestOutlier.getEndEpoch() <= cutOff) {
            outliers.removeLastOccurrence(oldestOutlier);
        }
    }

    private void moveOutliersToWorkset() {
        intervals.addAll(outliers);
        outliers.clear();
    }

    /**
     * Evaluates whether interval is an outlier relative to the current workset by checking its min, avg and max values against outer fences.
     */
    public boolean detectOutlier(ISymIntervalStats interval) {
        if (!hasEnoughData()) {
            return false;
        }
        MetricSeriesInterquartileRange meansIqr = computePercentiles(sortedMeans());
        if (interval.mean() < meansIqr.lowerOutlierFence() || interval.mean() > meansIqr.upperOutlierFence()) {
            return true;
        }
        MetricSeriesInterquartileRange maxIqr = computePercentiles(sortedMaxs());
        if (interval.max() < maxIqr.lowerOutlierFence() || interval.max() > maxIqr.upperOutlierFence()) {
            return true;
        }
        MetricSeriesInterquartileRange minsIqr = computePercentiles(sortedMins());
        return interval.getMin() < minsIqr.lowerOutlierFence() || interval.getMin() > minsIqr.upperOutlierFence();
    }

    /**
     * Evaluates whether interval is an outlier relative to the current workset, sets isOutlier flag and stores the result in a workset/outlier collection.
     */
    public void add(ISymIntervalStats interval) {
        if (detectOutlier(interval)) {
            addToOutliers(markAsOutlier(interval));
            return;
        }
        addToWorkset(interval);
    }

    /** Returns the number of intervals currently held. */
    public int size() {
        return intervals.size();
    }

    /** True if at least {@link #IQR_INTERVALS_MIN} entries are present. */
    public boolean hasEnoughData() {
        return intervals.size() >= IQR_INTERVALS_MIN;
    }

    private double[] sortedMins() {
        return intervals.stream()
                .mapToDouble(ISymIntervalStats::getMin)
                .sorted()
                .toArray();
    }

    private double[] sortedMeans() {
        return intervals.stream()
                .mapToDouble(ISymIntervalStats::mean)
                .sorted()
                .toArray();
    }

    private double[] sortedMaxs() {
        return intervals.stream()
                .mapToDouble(ISymIntervalStats::max)
                .sorted()
                .toArray();
    }

    /**
     * Computes interquartile range (IQR) and Tukey fences from a sorted array of values. Each quartile is resolved via linear interpolation between adjacent
     * elements.
     */
    static MetricSeriesInterquartileRange computePercentiles(double[] sorted) {
        double q1 = interpolatePercentile(sorted, IQR_Q1_MULTIPLIER);
        double q2 = interpolatePercentile(sorted, IQR_Q2_MULTIPLIER);
        double q3 = interpolatePercentile(sorted, IQR_Q3_MULTIPLIER);
        double iqr = q3 - q1;
        double iqrFloor = Math.abs(q2) * IQR_ZERO_FLAT_TOLERANCE / OUTER_FENCE_MULTIPLIER;
        double effectiveIqr = Math.max(iqr, iqrFloor);
        double lowerOutlierFence = q1 - OUTER_FENCE_MULTIPLIER * effectiveIqr;
        double upperOutlierFence = q3 + OUTER_FENCE_MULTIPLIER * effectiveIqr;
        return new MetricSeriesInterquartileRange(q1, q2, iqr, lowerOutlierFence, upperOutlierFence);
    }

    /**
     * Returns the value for fractional rank (percentile) using linear interpolation between adjacent elements of a sorted array (ascending).
     */
    private static double interpolatePercentile(double[] sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.length < 2) {
            throw new IllegalArgumentException("Sorted array cannot be null or trivial");
        }
        if (percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile value must be between 0.0 and 1.0");
        }
        double index = percentile * (sortedValues.length - 1);
        int lo = (int) index;
        int hi = lo + 1;
        if (hi >= sortedValues.length) {
            return sortedValues[lo];
        }
        double fraction = index - lo;
        return sortedValues[lo] + fraction * (sortedValues[hi] - sortedValues[lo]);
    }
}
