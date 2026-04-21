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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.common.Attributes;

/**
 * Base class which owns multiple metrics (counters, gauges). Subclasses supply the attributes that identify the metric's scope (e.g. engine name, host).
 */
abstract class AbstractMetricsService implements IMetricsService {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final MetricsManager metricsManager;
    protected static final String hostname = AppUtils.getHostName();
    protected final Attributes attributes;
    private final Map<String, UpDownCounter> upDownCounters = new ConcurrentHashMap<>();
    private final Map<String, SymDoubleGauge> gauges = new ConcurrentHashMap<>();
    private final boolean isOtelPublishingEnabled;
    private final List<AutoCloseable> otelHandles = new ArrayList<>();

    protected AbstractMetricsService(MetricsManager metricsManager, Attributes attributes, boolean isOtelPublishingEnabled) {
        this.metricsManager = metricsManager;
        this.attributes = attributes;
        this.isOtelPublishingEnabled = isOtelPublishingEnabled;
    }

    @Override
    public void initRepository() {
    }

    public boolean isOtelPublishingEnabled() {
        return isOtelPublishingEnabled;
    }

    public UpDownCounter getOrCreateUpDownCounter(String metricId, String description, String unitOfMeasurement) {
        return upDownCounters.computeIfAbsent(metricId,
                id -> createUpDownCounterInternal(id, description, unitOfMeasurement));
    }

    private UpDownCounter createUpDownCounterInternal(String metricId, String description, String unitOfMeasurement) {
        UpDownCounter counter = new UpDownCounter(metricId, attributes);
        if (isOtelPublishingEnabled) {
            otelHandles.add(metricsManager.createUpDownCounter(
                    metricId, description, unitOfMeasurement, counter::getValue, attributes));
        }
        return counter;
    }

    public UpDownCounter getUpDownCounter(String metricId) {
        return upDownCounters.get(metricId);
    }

    public SymDoubleGauge getOrCreateGauge(String metricId, String description, String unitOfMeasurement) {
        return gauges.computeIfAbsent(metricId, k -> createGaugeInternal(k, description, unitOfMeasurement));
    }

    private SymDoubleGauge createGaugeInternal(String metricId, String description, String unitOfMeasurement) {
        SymDoubleGauge gauge = new SymDoubleGauge(metricId, attributes);
        if (isOtelPublishingEnabled) {
            otelHandles.add(metricsManager.createGauge(
                    metricId, description, unitOfMeasurement, gauge::getValue, attributes));
        }
        return gauge;
    }

    public SymDoubleGauge getGauge(String metricId) {
        return gauges.get(metricId);
    }

    public List<AbstractQueuedMetric> getAllMetrics() {
        List<AbstractQueuedMetric> all = new ArrayList<>(upDownCounters.values());
        all.addAll(gauges.values());
        return all;
    }

    @Override
    public void shutdown() {
        for (AutoCloseable handle : otelHandles) {
            try {
                handle.close();
            } catch (Exception e) {
                log.warn("Failed to close OTel instrument handle", e);
            }
        }
        otelHandles.clear();
        upDownCounters.clear();
        gauges.clear();
    }
}
