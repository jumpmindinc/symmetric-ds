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
package org.jumpmind.symmetric.observability.interfaces;

import java.util.List;

import org.jumpmind.symmetric.model.MetricFactType;

public interface ISymMetric {
    String getMetricId();

    long getLastModified();

    /**
     * Drains all completed intervals from this metric's queue into permanent storage (database).
     */
    void closeCompletedIntervals();

    /**
     * Closes this metric, preventing any further observations from being recorded. Also unregisters the associated OTel callback if one is present.
     */
    void close();

    boolean isEnabled();

    /**
     * Creates the first accumulator for this metric. Called once when no prior interval exists. Implementations return the type appropriate for this metric
     * ({@link org.jumpmind.symmetric.observability.stats.Float64StatsAccumulator} for gauges,
     * {@link org.jumpmind.symmetric.observability.stats.Int64StatsAccumulator} for counters). Carry-forward for subsequent windows uses
     * {@link org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator#createNext}.
     */
    IStatsAccumulator createAccumulator(long intervalStart);

    ISymObservation[] removeAllObservations();

    int processObservations(ISymObservation[] observations);

    MetricFactType getFactType();

    List<ISymIntervalStats> exportCompletedIntervals();
}
