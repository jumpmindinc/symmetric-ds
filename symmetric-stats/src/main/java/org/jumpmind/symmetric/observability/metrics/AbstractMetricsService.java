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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.observability.interfaces.IMetricsService;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricDefinition;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
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
    protected static String hostname = AppUtils.getHostName();
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

    public IUpDownCounter registerUpDownCounter(ISymMetricDefinition definition) {
        return registerUpDownCounter(definition, List.of());
    }

    public IUpDownCounter registerUpDownCounter(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        return upDownCounters.computeIfAbsent(instrumentKey(definition.id(), attrs),
                k -> createUpDownCounterInternal(definition, attrs));
    }

    private UpDownCounter createUpDownCounterInternal(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        UpDownCounter counter = new UpDownCounter(definition.id(), attributes, attrs);
        if (isOtelPublishingEnabled) {
            otelHandles.add(metricsManager.createUpDownCounter(
                    definition.id(), definition.description(), definition.unit(), counter::getValue, attributes));
        }
        return counter;
    }

    public IUpDownCounter getUpDownCounter(String metricId) {
        return getUpDownCounter(metricId, List.of());
    }

    public IUpDownCounter getUpDownCounter(String metricId, List<MetricAttribute> attrs) {
        return upDownCounters.get(instrumentKey(metricId, attrs));
    }

    public ISymDoubleGauge registerGauge(ISymMetricDefinition definition) {
        return registerGauge(definition, List.of());
    }

    public ISymDoubleGauge registerGauge(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        return gauges.computeIfAbsent(instrumentKey(definition.id(), attrs), k -> createGaugeInternal(definition, attrs));
    }

    private SymDoubleGauge createGaugeInternal(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        SymDoubleGauge gauge = new SymDoubleGauge(definition.id(), attributes, attrs);
        if (isOtelPublishingEnabled) {
            otelHandles.add(metricsManager.createGauge(
                    definition.id(), definition.description(), definition.unit(), gauge::getValue, attributes));
        }
        return gauge;
    }

    public ISymDoubleGauge getGauge(String metricId) {
        return getGauge(metricId, List.of());
    }

    public ISymDoubleGauge getGauge(String metricId, List<MetricAttribute> attrs) {
        return gauges.get(instrumentKey(metricId, attrs));
    }

    private static String instrumentKey(String metricId, List<MetricAttribute> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return metricId;
        }
        StringBuilder sb = new StringBuilder(metricId);
        for (MetricAttribute a : attrs) {
            sb.append('\0').append(a.name() != null ? a.name() : "").append('=').append(a.value() != null ? a.value() : "");
        }
        return sb.toString();
    }

    public Collection<ISymMetric> getAllMetrics() {
        List<ISymMetric> all = new ArrayList<>(upDownCounters.values());
        all.addAll(gauges.values());
        return all;
    }

    @Override
    public void shutdown() {
        closeAllCounters();
        closeAllGauges();
        closeAllOtelHandles();
    }

    private void closeAllCounters() {
        for (UpDownCounter metric : upDownCounters.values()) {
            try {
                metric.close();
                metric.removeAllObservations();
            } catch (Exception e) {
                log.warn("Failed to close counter metric" + metric.getMetricId(), e);
            }
        }
        upDownCounters.clear();
    }

    private void closeAllGauges() {
        for (SymDoubleGauge metric : gauges.values()) {
            try {
                metric.close();
                metric.removeAllObservations();
            } catch (Exception e) {
                log.warn("Failed to close gauge metric" + metric.getMetricId(), e);
            }
        }
        gauges.clear();
    }

    private void closeAllOtelHandles() {
        for (AutoCloseable handle : otelHandles) {
            try {
                handle.close();
            } catch (Exception e) {
                log.warn("Failed to close OTel instrument handle", e);
            }
        }
        otelHandles.clear();
    }
}
