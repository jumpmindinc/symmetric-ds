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

import java.util.Collection;
import java.util.List;

import org.jumpmind.symmetric.statistic.IStatisticManager;

public interface IEngineMetricsService extends IMetricsService {
    String getEngineName();

    IStatisticManager getStatisticManager();

    boolean isOtelPublishingEnabled();

    /**
     * Returns the counter registered for {@code metricId} with no attributes. MetricDefinitionFactory must have already registered the metric.
     */
    IUpDownCounter getUpDownCounter(String metricId);

    /**
     * Returns the counter registered for {@code metricId} with the given attributes. MetricDefinitionFactory must have already registered the metric.
     */
    IUpDownCounter getUpDownCounter(String metricId, List<MetricAttribute> attrs);

    /**
     * Returns the gauge registered for {@code metricId} with no attributes. MetricDefinitionFactory must have already registered the metric.
     */
    ISymDoubleGauge getGauge(String metricId);

    /**
     * Returns the gauge registered for {@code metricId} with the given attributes. MetricDefinitionFactory must have already registered the metric.
     */
    ISymDoubleGauge getGauge(String metricId, List<MetricAttribute> attrs);

    IUpDownCounter registerUpDownCounter(ISymMetricDefinition definition);

    IUpDownCounter registerUpDownCounter(ISymMetricDefinition definition, List<MetricAttribute> attrs);

    ISymDoubleGauge registerGauge(ISymMetricDefinition definition);

    ISymDoubleGauge registerGauge(ISymMetricDefinition definition, List<MetricAttribute> attrs);

    Collection<ISymMetric> getAllMetrics();

    /**
     * Drains all completed intervals from this metric's queue into permanent storage (database).
     */
    void saveCompletedIntervalStats();

    /**
     * Deletes metric interval stats older than the configured retention period and removes any metric_context rows that are no longer referenced by any
     * remaining stats rows.
     */
    void purgeMetricStats(boolean force);
}
