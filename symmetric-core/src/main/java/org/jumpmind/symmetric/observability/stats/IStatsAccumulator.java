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

import org.jumpmind.symmetric.observability.metrics.ISymIntervalStats;
import org.jumpmind.symmetric.observability.metrics.ISymObservation;

/**
 * Contract for a mutable, time-windowed statistics accumulator. Implementations differ in the precision used for min, max, and last-value tracking
 * ({@link Float64StatsAccumulator} uses {@code double}; {@link Int64StatsAccumulator} uses {@code long}).
 */
public interface IStatsAccumulator {
    long getIntervalStart();

    long getIntervalEnd();

    boolean isInScope(long epochTimestamp);

    boolean isInScope(ISymObservation observation);

    void addObservation(ISymObservation observation);

    void addObservation(double value, long epochTimestamp);

    long closeAtObservation(long epochTimestamp);

    long close();

    double getLastValueAsDouble();

    /**
     * Creates a new accumulator of the same concrete type for {@code intervalStart}, carrying this accumulator's last value forward. The carry value is
     * transferred at native precision — no {@code double} conversion for {@code long}-typed accumulators.
     */
    IStatsAccumulator createNext(long intervalStart);

    ISymIntervalStats toStats();
}
