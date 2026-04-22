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

import org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.ISymObservation;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;

/**
 * Shared state and logic for time-weighted statistics accumulation over a single fixed-width window. All internal arithmetic uses {@code double}. Subclasses
 * supply the precision used for {@code min}, {@code max}, and {@code lastValue} by implementing {@link #updateMinMaxAndLastValue}, {@link #getMinAsDouble},
 * {@link #getMaxAsDouble}, and {@link #getLastValueAsDouble}.
 */
public abstract class AbstractStatsAccumulator implements IStatsAccumulator {
    public static final long INTERVAL_DURATION_MS = 5 * 60_000L;
    public static final long INTERVAL_TIME_UNKNOWN = 0;
    public static final double INTERVAL_VALUE_DEFAULT = 0.0;
    public final long intervalStart;
    public final long intervalEnd;
    protected double weightedSum;
    protected double weightedSumOfSquares;
    protected long totalWeightMs;
    protected int count;
    protected double valueSum;
    protected long lastTimestamp;

    protected AbstractStatsAccumulator(long intervalStart, double carryForwardValue) {
        this.intervalStart = intervalStart;
        this.intervalEnd = intervalStart + INTERVAL_DURATION_MS;
        this.lastTimestamp = intervalStart;
    }

    public static long calculateIntervalStart(long epochTimestamp) {
        return (epochTimestamp / INTERVAL_DURATION_MS) * INTERVAL_DURATION_MS;
    }

    @Override
    public long getIntervalStart() {
        return intervalStart;
    }

    @Override
    public long getIntervalEnd() {
        return intervalEnd;
    }

    @Override
    public boolean isInScope(long epochTimestamp) {
        return epochTimestamp >= this.intervalStart && epochTimestamp < this.intervalEnd;
    }

    @Override
    public boolean isInScope(ISymObservation observation) {
        return isInScope(observation.getTimestamp());
    }

    @Override
    public void addObservation(ISymObservation observation) {
        addObservation(observation.getValueAsDouble(), observation.getTimestamp());
    }

    @Override
    public void addObservation(double value, long epochTimestamp) {
        long delta = epochTimestamp - lastTimestamp;
        if (delta > 0) {
            double lv = getLastValueAsDouble();
            weightedSum += lv * delta;
            weightedSumOfSquares += lv * lv * delta;
            totalWeightMs += delta;
        }
        count++;
        valueSum += value;
        updateMinMaxAndLastValue(value);
        lastTimestamp = epochTimestamp;
    }

    /** Updates min, max, and lastValue from {@code value}. Subclass casts to its native precision before storing. */
    protected abstract void updateMinMaxAndLastValue(double value);

    protected abstract double getMinAsDouble();

    protected abstract double getMaxAsDouble();

    @Override
    public long closeAtObservation(long epochTimestamp) {
        if (epochTimestamp > this.intervalEnd)
            throw new IllegalArgumentException(String.format("Observation occurs after this interval ends! timestamp=%d, end=%d", epochTimestamp,
                    this.intervalEnd));
        long delta = epochTimestamp - lastTimestamp;
        if (delta > 0) {
            totalWeightMs += delta;
            lastTimestamp = epochTimestamp;
            if (getLastValueAsDouble() != INTERVAL_VALUE_DEFAULT) {
                double lv = getLastValueAsDouble();
                weightedSum += lv * delta;
                weightedSumOfSquares += lv * lv * delta;
            }
        }
        return lastTimestamp;
    }

    @Override
    public long close() {
        long delta = this.intervalEnd - lastTimestamp;
        if (delta > 0) {
            totalWeightMs += delta;
            if (getLastValueAsDouble() != INTERVAL_VALUE_DEFAULT) {
                double lv = getLastValueAsDouble();
                weightedSum += lv * delta;
                weightedSumOfSquares += lv * lv * delta;
            }
        }
        return lastTimestamp;
    }

    protected double computeAvg() {
        return totalWeightMs > 0 ? weightedSum / totalWeightMs : getLastValueAsDouble();
    }

    protected double computeStdDev(double avg) {
        double variance = totalWeightMs > 0 ? (weightedSumOfSquares / totalWeightMs) - (avg * avg) : 0.0;
        return Math.sqrt(Math.max(0.0, variance));
    }

    protected double computeMean() {
        return count > 0 ? valueSum / count : getLastValueAsDouble();
    }

    @Override
    public ISymIntervalStats toStats() {
        double avg = computeAvg();
        double stdDev = computeStdDev(avg);
        double effectiveMin = count > 0 ? getMinAsDouble() : getLastValueAsDouble();
        double effectiveMax = count > 0 ? getMaxAsDouble() : getLastValueAsDouble();
        double mean = computeMean();
        return new MetricIntervalStats(intervalStart, intervalEnd, avg, effectiveMin, effectiveMax, stdDev, count, mean, false);
    }
}
