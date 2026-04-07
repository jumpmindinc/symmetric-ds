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
package org.jumpmind.symmetric.observability.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;

/**
 * Base class providing common metric collection functionality (counters, gauges) backed by OpenTelemetry.
 * Subclasses supply the attributes that identify the metric's scope (e.g. engine name, host).
 */
abstract class AbstractMetricsService implements IMetricsService {
    protected final MetricsManager metricsManager;
    protected final Attributes attributes;
    private final Map<String, UpDownCounter> upDownCounters = new ConcurrentHashMap<>();
    private final Map<String, SymDoubleGauge> gauges = new ConcurrentHashMap<>();
    private final boolean isOtelPublishingEnabled;

    protected AbstractMetricsService(MetricsManager metricsManager, Attributes attributes, boolean isOtelPublishingEnabled) {
        this.metricsManager = metricsManager;
        this.attributes = attributes;
        this.isOtelPublishingEnabled = isOtelPublishingEnabled;
    }

    public boolean isOtelPublishingEnabled() {
        return isOtelPublishingEnabled;
    }

    public UpDownCounter getOrCreateUpDownCounter(String metricId, String description, String unitOfMeasurement) {
        return upDownCounters.computeIfAbsent(metricId,
                k -> createUpDownCounterInternal(k, description, unitOfMeasurement));
    }

    private UpDownCounter createUpDownCounterInternal(String metricId, String description, String unitOfMeasurement) {
        LongUpDownCounter otelCounter = null;
        if (isOtelPublishingEnabled) {
            otelCounter = metricsManager.createUpDownCounter(metricId, description, unitOfMeasurement);
        }
        return new UpDownCounter(metricId, otelCounter, attributes);
    }

    public UpDownCounter getUpDownCounter(String metricId) {
        return upDownCounters.get(metricId);
    }

    public SymDoubleGauge getOrCreateGauge(String metricId, String description, String unitOfMeasurement) {
        return gauges.computeIfAbsent(metricId, k -> createGaugeInternal(k, description, unitOfMeasurement));
    }

    private SymDoubleGauge createGaugeInternal(String metricId, String description, String unitOfMeasurement) {
        DoubleGauge otelGauge = null;
        if (isOtelPublishingEnabled) {
            otelGauge = metricsManager.createGauge(metricId, description, unitOfMeasurement);
        }
        return new SymDoubleGauge(metricId, otelGauge, attributes);
    }

    public SymDoubleGauge getGauge(String metricId) {
        return gauges.get(metricId);
    }

    @Override
    public void shutdown() {
    }
}
