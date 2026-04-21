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

/**
 * {@code long}-precision accumulator. Tracks {@code min}, {@code max}, and {@code lastValue} as {@code long}. Incoming observations are truncated from
 * {@code double} to {@code long} on arrival. Weighted-sum arithmetic remains {@code double} internally. Used for counter-type metrics.
 */
public class Int64StatsAccumulator extends AbstractStatsAccumulator {
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;
    private long lastValue;

    public Int64StatsAccumulator(long intervalStart, long carryForwardValue) {
        super(intervalStart, (double) carryForwardValue);
        this.lastValue = carryForwardValue;
    }

    public Int64StatsAccumulator(long intervalStart) {
        this(intervalStart, 0L);
    }

    public Int64StatsAccumulator() {
        this(calculateIntervalStart(System.currentTimeMillis()), 0L);
    }

    /** Returns the last observed value at full {@code long} precision. */
    public long getLastValueAsLong() {
        return lastValue;
    }

    @Override
    public double getLastValueAsDouble() {
        return (double) lastValue;
    }

    /** Creates the successor window carrying {@code lastValue} forward at {@code long} precision — no {@code double} roundtrip. */
    @Override
    public IStatsAccumulator createNext(long intervalStart) {
        return new Int64StatsAccumulator(intervalStart, lastValue);
    }

    @Override
    protected void updateMinMaxAndLastValue(double value) {
        long v = (long) value;
        if (v < min)
            min = v;
        if (v > max)
            max = v;
        lastValue = v;
    }

    @Override
    protected double getMinAsDouble() {
        return (double) min;
    }

    @Override
    protected double getMaxAsDouble() {
        return (double) max;
    }
}
