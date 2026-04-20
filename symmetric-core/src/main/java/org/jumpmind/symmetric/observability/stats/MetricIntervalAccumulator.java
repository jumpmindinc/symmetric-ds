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

import org.jumpmind.symmetric.observability.metrics.ISymObservation;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;

/**
 * Mutable accumulator for time-weighted statistics over a single window (a.k.a. interval). Each observation contributes to the weighted sum based on how long
 * its value was held before the next observation arrived (step-function model).
 */
public class MetricIntervalAccumulator {
    public static final long INTERVAL_DURATION_MS = 5 * 60_000L;
    public static final long INTERVAL_TIME_UNKNOWN = 0;
    public static final double INTERVAL_VALUE_DEFAULT = 0.0;
    public final long intervalStart;
    public final long intervalEnd;
    private double weightedSum;
    private double weightedSumOfSquares;
    private long totalWeightMs;
    private double min = Double.MAX_VALUE;
    private double max = -Double.MAX_VALUE;
    private int count;
    private double valueSum;
    private double lastValue;
    private long lastTimestamp;

    public MetricIntervalAccumulator(long intervalStart, double carryForwardValue) {
        this.intervalStart = intervalStart;
        this.intervalEnd = intervalStart + INTERVAL_DURATION_MS;
        this.lastValue = carryForwardValue;
        this.lastTimestamp = intervalStart;
    }

    public MetricIntervalAccumulator(long intervalStart) {
        this(intervalStart, MetricIntervalAccumulator.INTERVAL_VALUE_DEFAULT);
    }

    public MetricIntervalAccumulator() {
        this(calculateIntervalStart(System.currentTimeMillis()), MetricIntervalAccumulator.INTERVAL_VALUE_DEFAULT);
    }

    public static long calculateIntervalStart(long epochTimestamp) {
        return (epochTimestamp / MetricIntervalAccumulator.INTERVAL_DURATION_MS) * MetricIntervalAccumulator.INTERVAL_DURATION_MS;
    }

    public boolean isInScope(long epochTimestamp) {
        return epochTimestamp >= this.intervalStart || epochTimestamp < this.intervalEnd;
    }

    public boolean isInScope(ISymObservation observation) {
        return isInScope(observation.getTimestamp());
    }

    public void addObservation(ISymObservation observation) {
        addObservation(observation.getValueAsDouble(), observation.getTimestamp());
    }

    /**
     * Records an observation. The previous value is credited for the duration since the last timestamp.
     */
    public void addObservation(double value, long epochTimestamp) {
        long delta = epochTimestamp - lastTimestamp;
        if (delta > 0) {
            weightedSum += lastValue * delta;
            weightedSumOfSquares += lastValue * lastValue * delta;
            totalWeightMs += delta;
        }
        if (value < min)
            min = value;
        if (value > max)
            max = value;
        count++;
        valueSum += value;
        lastValue = value;
        lastTimestamp = epochTimestamp;
    }

    /**
     * Closes the accumulation window at {@code endTimestamp}, crediting the last known value for any remaining time.
     */
    public long closeAtObservation(long epochTimestamp) {
        if (epochTimestamp > this.intervalEnd)
            throw new IllegalArgumentException(String.format("Observation occurs after this internal ends! timestamp=%d, end=%d", epochTimestamp,
                    this.intervalEnd));
        long delta = epochTimestamp - lastTimestamp;
        if (delta > 0) {
            totalWeightMs += delta;
            lastTimestamp = epochTimestamp;
            if (lastValue != INTERVAL_VALUE_DEFAULT) {
                weightedSum += lastValue * delta;
                weightedSumOfSquares += lastValue * lastValue * delta;
            }
        }
        return lastTimestamp;
    }

    /**
     * Closes the accumulation window at the end of the interval, crediting the last known value (if any) for any remaining time.
     */
    public long close() {
        long delta = this.intervalEnd - lastTimestamp;
        if (delta > 0) {
            totalWeightMs += delta;
            if (lastValue != INTERVAL_VALUE_DEFAULT) {
                weightedSum += lastValue * delta;
                weightedSumOfSquares += lastValue * lastValue * delta;
            }
        }
        return lastTimestamp;
    }

    public double getLastValue() {
        return lastValue;
    }

    public MetricIntervalStats toMetricIntervalStats() {
        double avg = totalWeightMs > 0 ? weightedSum / totalWeightMs : lastValue;
        double variance = totalWeightMs > 0 ? (weightedSumOfSquares / totalWeightMs) - (avg * avg) : 0.0;
        double stdDev = Math.sqrt(Math.max(0.0, variance));
        double effectiveMin = count > 0 ? min : lastValue;
        double effectiveMax = count > 0 ? max : lastValue;
        double mean = count > 0 ? valueSum / count : lastValue;
        return new MetricIntervalStats(intervalStart, intervalEnd, avg, effectiveMin, effectiveMax, stdDev, count, mean, false);
    }
}
