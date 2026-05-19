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
    IUpDownCounter getUpDownCounter(String metricId, MetricAttributeList attrs);

    /**
     * Returns the double gauge registered for {@code metricId} with no attributes. MetricDefinitionFactory must have already registered the metric.
     */
    ISymDoubleGauge getDoubleGauge(String metricId);

    /**
     * Returns the double gauge registered for {@code metricId} with the given attributes. MetricDefinitionFactory must have already registered the metric.
     */
    ISymDoubleGauge getDoubleGauge(String metricId, MetricAttributeList attrs);

    IUpDownCounter registerUpDownCounter(ISymMetricDefinition definition);

    IUpDownCounter registerUpDownCounter(ISymMetricDefinition definition, MetricAttributeList attrs);

    /**
     * Returns the monotonic counter registered for {@code metricId} with no attributes. MetricDefinitionFactory must have already registered the metric.
     */
    IIncreasingCounter getIncreasingCounter(String metricId);

    /**
     * Returns the monotonic counter registered for {@code metricId} with the given attributes. MetricDefinitionFactory must have already registered the metric.
     */
    IIncreasingCounter getIncreasingCounter(String metricId, MetricAttributeList attrs);

    IIncreasingCounter registerIncreasingCounter(ISymMetricDefinition definition);

    IIncreasingCounter registerIncreasingCounter(ISymMetricDefinition definition, MetricAttributeList attrs);

    ISymDoubleGauge registerDoubleGauge(ISymMetricDefinition definition);

    ISymDoubleGauge registerDoubleGauge(ISymMetricDefinition definition, MetricAttributeList attrs);

    /**
     * Returns the long gauge registered for {@code metricId} with no attributes. MetricDefinitionFactory must have already registered the metric.
     */
    ISymLongGauge getLongGauge(String metricId);

    /**
     * Returns the long gauge registered for {@code metricId} with the given attributes. MetricDefinitionFactory must have already registered the metric.
     */
    ISymLongGauge getLongGauge(String metricId, MetricAttributeList attrs);

    ISymLongGauge registerLongGauge(ISymMetricDefinition definition);

    ISymLongGauge registerLongGauge(ISymMetricDefinition definition, MetricAttributeList attrs);

    /**
     * Registers (or returns the existing) long gauge for {@code metricId} with the given attributes. The definition must already be registered in
     * {@code MetricDefinitionFactory}; throws {@code InvalidMetricDataException} if it is not. Never pass an inline definition — all built-in metric
     * definitions must be declared and registered exclusively in {@code MetricDefinitionFactory}.
     */
    ISymLongGauge registerLongGauge(String metricId, MetricAttributeList attrs);

    /**
     * Creates a new {@link INodeBatchStatusMetricsMap} backed by this service. {@code batchesMetricId} and {@code rowsMetricId} must already be registered in
     * {@code MetricDefinitionFactory}; throws {@code InvalidMetricDataException} if either is not.
     */
    INodeBatchStatusMetricsMap createNodeBatchStatusMetricsMap(String batchesMetricId, String rowsMetricId);

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
