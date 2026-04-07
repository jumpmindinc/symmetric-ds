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

import org.jumpmind.symmetric.observability.models.MetricInterval;

/**
 * Mutable accumulator for time-weighted statistics over a single 5-minute window.
 * Each observation contributes to the weighted sum based on how long its value was held
 * before the next observation arrived (step-function model).
 */
class MetricIntervalAccumulator {
    static final long INTERVAL_DURATION_MS = 5 * 60_000L;

    final long intervalStart;
    final long intervalEnd;

    private double weightedSum;
    private double weightedSumOfSquares;
    private long totalWeightMs;
    private double min = Double.MAX_VALUE;
    private double max = -Double.MAX_VALUE;
    private int count;

    private double lastValue;
    private long lastTimestamp;

    MetricIntervalAccumulator(long intervalStart, double carryForwardValue) {
        this.intervalStart = intervalStart;
        this.intervalEnd = intervalStart + INTERVAL_DURATION_MS;
        this.lastValue = carryForwardValue;
        this.lastTimestamp = intervalStart;
    }

    /**
     * Records an observation. The previous value is credited for the duration since the last timestamp.
     */
    void addObservation(double value, long timestamp) {
        long delta = timestamp - lastTimestamp;
        if (delta > 0) {
            weightedSum += lastValue * delta;
            weightedSumOfSquares += lastValue * lastValue * delta;
            totalWeightMs += delta;
        }
        if (value < min) min = value;
        if (value > max) max = value;
        count++;
        lastValue = value;
        lastTimestamp = timestamp;
    }

    /**
     * Closes the window at {@code endTimestamp}, crediting the last known value for any remaining time.
     */
    void closeAt(long endTimestamp) {
        long delta = endTimestamp - lastTimestamp;
        if (delta > 0) {
            weightedSum += lastValue * delta;
            weightedSumOfSquares += lastValue * lastValue * delta;
            totalWeightMs += delta;
            lastTimestamp = endTimestamp;
        }
    }

    double getLastValue() {
        return lastValue;
    }

    MetricInterval toMetricInterval() {
        double avg = totalWeightMs > 0 ? weightedSum / totalWeightMs : lastValue;
        double variance = totalWeightMs > 0 ? (weightedSumOfSquares / totalWeightMs) - (avg * avg) : 0.0;
        double stdDev = Math.sqrt(Math.max(0.0, variance));
        double effectiveMin = count > 0 ? min : lastValue;
        double effectiveMax = count > 0 ? max : lastValue;
        return new MetricInterval(intervalStart, intervalEnd, avg, effectiveMin, effectiveMax, stdDev, count);
    }
}
