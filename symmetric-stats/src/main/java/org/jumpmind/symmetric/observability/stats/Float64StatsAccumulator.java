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

/**
 * {@code double}-precision accumulator. Tracks {@code min}, {@code max}, and {@code lastValue} as {@code double}. Used for gauge-type metrics.
 */
public class Float64StatsAccumulator extends AbstractStatsAccumulator {
    private double min;
    private double max;
    private double lastValue;

    public Float64StatsAccumulator(long intervalStart, double carryForwardValue) {
        super(intervalStart);
        this.lastValue = carryForwardValue;
        this.min = carryForwardValue;
        this.max = carryForwardValue;
    }

    public Float64StatsAccumulator(long intervalStart) {
        this(intervalStart, INTERVAL_VALUE_DEFAULT);
    }

    public Float64StatsAccumulator() {
        this(calculateIntervalStart(System.currentTimeMillis()), INTERVAL_VALUE_DEFAULT);
    }

    @Override
    public double getLastValueAsDouble() {
        return lastValue;
    }

    @Override
    protected void updateMinMaxAndLastValue(double value) {
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
        lastValue = value;
    }

    @Override
    public IStatsAccumulator createNext() {
        return new Float64StatsAccumulator(this.intervalEnd, this.lastValue);
    }

    @Override
    public double getMinAsDouble() {
        return min;
    }

    @Override
    public double getMaxAsDouble() {
        return max;
    }
}
