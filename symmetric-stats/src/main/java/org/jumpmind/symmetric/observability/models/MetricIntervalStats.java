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
package org.jumpmind.symmetric.observability.models;

import java.sql.Date;

import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;

/**
 * Immutable snapshot of aggregated statistics for one closed 5-minute window of a single metric.
 *
 * <p>
 * {@code avg} is a time-weighted average (step-function model). {@code mean} is the simple arithmetic mean: sum of observed values divided by
 * {@code observationCount}. {@code isOutlier} flags whether this interval has been identified as statistically anomalous.
 */
public record MetricIntervalStats(
        long intervalStart,
        long intervalEnd,
        double avg,
        double min,
        double max,
        double stdDev,
        int observationCount,
        double mean,
        boolean isOutlier) implements ISymIntervalStats {
    @Override
    public long getStartEpoch() {
        return intervalStart;
    }

    @Override
    public long getEndEpoch() {
        return intervalEnd;
    }

    @Override
    public long getDurationMillis() {
        return intervalEnd - intervalStart;
    }

    @Override
    public long getDurationSeconds() {
        return (intervalEnd - intervalStart) / 1000l;
    }

    @Override
    public double getAvg() {
        return avg;
    }

    @Override
    public double getMin() {
        return min;
    }

    @Override
    public double getStdDeviation() {
        return stdDev;
    }

    @Override
    public long getObservationCount() {
        return observationCount;
    }

    @Override
    public Date getStartTimeUtc() {
        return new Date(intervalStart);
    }

    /** Returns a copy of this interval with {@code isOutlier} set to the given value. */
    @Override
    public MetricIntervalStats cloneOutlier(boolean isOutlier) {
        return new MetricIntervalStats(intervalStart, intervalEnd, avg, min, max, stdDev, observationCount, mean, isOutlier);
    }

    /** Produces ascending time order (oldest first) */
    @Override
    public int compareTo(ISymIntervalStats other) {
        int cmp = Long.compare(this.intervalEnd, other.getEndEpoch());
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare(this.intervalStart, other.getStartEpoch());
    }
}
