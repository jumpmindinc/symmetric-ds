/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.observability.interfaces;

/**
 * Contract for a mutable, time-windowed statistics accumulator. Implementations differ in the precision used for min, max, and last-value tracking
 * ({@link org.jumpmind.symmetric.observability.stats.Float64StatsAccumulator} uses {@code double};
 * {@link org.jumpmind.symmetric.observability.stats.Int64StatsAccumulator} uses {@code long}).
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

    double getMinAsDouble();

    double getMaxAsDouble();

    double computeAvg();

    /**
     * Creates a new accumulator of the same concrete type, carrying this accumulator's last value forward. The new accumulator covers the next time window.
     */
    IStatsAccumulator createNext();

    ISymIntervalStats toStats();
}
