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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.observability.interfaces.IIncreasingCounter;
import org.jumpmind.symmetric.observability.interfaces.IMetricsService;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricDefinition;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

/**
 * Base class which owns multiple metrics (counters, gauges). Subclasses supply the attributes that identify the metric's scope (e.g. engine name, host).
 */
abstract class AbstractMetricsService implements IMetricsService {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final MetricsManager metricsManager;
    protected static String hostname = AppUtils.getHostName();
    protected final Attributes attributes;
    private final Map<String, ISymMetric> metrics = new ConcurrentHashMap<>();
    private final boolean isOtelPublishingEnabled;

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
        ISymMetric m = metrics.computeIfAbsent(instrumentKey(definition.id(), attrs), k -> createUpDownCounterInternal(definition, attrs));
        return m instanceof IUpDownCounter c ? c : null;
    }

    private UpDownCounter createUpDownCounterInternal(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        UpDownCounter counter = new UpDownCounter(definition, this.attributes, attrs);
        if (counter.isEnabled()) {
            if (isOtelPublishingEnabled) {
                Attributes instrAttrs = buildInstrumentAttributes(attrs);
                ObservableLongUpDownCounter otelHandle = metricsManager.createUpDownCounter(
                        definition.id(), definition.description(), definition.unit(), counter::getValue, instrAttrs);
                counter.open(otelHandle);
            } else {
                counter.open(null);
            }
        }
        return counter;
    }

    private Attributes buildInstrumentAttributes(List<MetricAttribute> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return this.attributes;
        }
        AttributesBuilder builder = this.attributes.toBuilder();
        for (MetricAttribute a : attrs) {
            if (a.name() != null && a.value() != null) {
                builder.put(AttributeKey.stringKey(a.name()), a.value());
            }
        }
        return builder.build();
    }

    public IUpDownCounter getUpDownCounter(String metricId) {
        return getUpDownCounter(metricId, List.of());
    }

    public IUpDownCounter getUpDownCounter(String metricId, List<MetricAttribute> attrs) {
        ISymMetric m = metrics.get(instrumentKey(metricId, attrs));
        return m instanceof IUpDownCounter c ? c : null;
    }

    public IIncreasingCounter registerIncreasingCounter(ISymMetricDefinition definition) {
        return registerIncreasingCounter(definition, List.of());
    }

    public IIncreasingCounter registerIncreasingCounter(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        ISymMetric m = metrics.computeIfAbsent(instrumentKey(definition.id(), attrs), k -> createIncreasingCounterInternal(definition, attrs));
        return m instanceof IIncreasingCounter c ? c : null;
    }

    private IncreasingCounter createIncreasingCounterInternal(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        IncreasingCounter counter = new IncreasingCounter(definition, this.attributes, attrs);
        if (counter.isEnabled()) {
            if (isOtelPublishingEnabled) {
                Attributes instrAttrs = buildInstrumentAttributes(attrs);
                ObservableLongCounter handle = metricsManager.createIncreasingCounter(
                        definition.id(), definition.description(), definition.unit(), counter::getValue, instrAttrs);
                counter.open(handle);
            } else {
                counter.open(null);
            }
        }
        return counter;
    }

    public IIncreasingCounter getIncreasingCounter(String metricId) {
        return getIncreasingCounter(metricId, List.of());
    }

    public IIncreasingCounter getIncreasingCounter(String metricId, List<MetricAttribute> attrs) {
        ISymMetric m = metrics.get(instrumentKey(metricId, attrs));
        return m instanceof IIncreasingCounter c ? c : null;
    }

    public ISymDoubleGauge registerDoubleGauge(ISymMetricDefinition definition) {
        return registerDoubleGauge(definition, List.of());
    }

    public ISymDoubleGauge registerDoubleGauge(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        ISymMetric m = metrics.computeIfAbsent(instrumentKey(definition.id(), attrs), k -> createDoubleGaugeInternal(definition, attrs));
        return m instanceof ISymDoubleGauge g ? g : null;
    }

    private SymDoubleGauge createDoubleGaugeInternal(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        SymDoubleGauge gauge = new SymDoubleGauge(definition, this.attributes, attrs);
        if (gauge.isEnabled()) {
            if (isOtelPublishingEnabled) {
                Attributes instrAttrs = buildInstrumentAttributes(attrs);
                ObservableDoubleGauge otelHandle = metricsManager.createDoubleGauge(
                        definition.id(), definition.description(), definition.unit(), gauge::getValue, instrAttrs);
                gauge.open(otelHandle);
            } else {
                gauge.open(null);
            }
        }
        return gauge;
    }

    public ISymDoubleGauge getDoubleGauge(String metricId) {
        return getDoubleGauge(metricId, List.of());
    }

    public ISymDoubleGauge getDoubleGauge(String metricId, List<MetricAttribute> attrs) {
        ISymMetric m = metrics.get(instrumentKey(metricId, attrs));
        return m instanceof ISymDoubleGauge g ? g : null;
    }

    public ISymLongGauge registerLongGauge(String metricId, List<MetricAttribute> attrs) {
        return registerLongGauge(metricsManager.getMetricDefinitionFactory().getDefinition(metricId), attrs);
    }

    public ISymLongGauge registerLongGauge(ISymMetricDefinition definition) {
        return registerLongGauge(definition, List.of());
    }

    public ISymLongGauge registerLongGauge(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        ISymMetric m = metrics.computeIfAbsent(instrumentKey(definition.id(), attrs), k -> createLongGaugeInternal(definition, attrs));
        return m instanceof ISymLongGauge g ? g : null;
    }

    private SymLongGauge createLongGaugeInternal(ISymMetricDefinition definition, List<MetricAttribute> attrs) {
        SymLongGauge gauge = new SymLongGauge(definition, this.attributes, attrs);
        if (gauge.isEnabled()) {
            if (isOtelPublishingEnabled) {
                Attributes instrAttrs = buildInstrumentAttributes(attrs);
                ObservableLongGauge otelHandle = metricsManager.createLongGauge(
                        definition.id(), definition.description(), definition.unit(), gauge::getValue, instrAttrs);
                gauge.open(otelHandle);
            } else {
                gauge.open(null);
            }
        }
        return gauge;
    }

    public ISymLongGauge getLongGauge(String metricId) {
        return getLongGauge(metricId, List.of());
    }

    public ISymLongGauge getLongGauge(String metricId, List<MetricAttribute> attrs) {
        ISymMetric m = metrics.get(instrumentKey(metricId, attrs));
        return m instanceof ISymLongGauge g ? g : null;
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
        return Collections.unmodifiableCollection(metrics.values());
    }

    protected void resetGaugesToZero() {
        for (ISymMetric metric : metrics.values()) {
            if (!metric.isEnabled()) {
                continue;
            }
            if (metric instanceof ISymDoubleGauge g) {
                g.setValue(0.0);
            } else if (metric instanceof ISymLongGauge g) {
                g.setValue(0L);
            }
        }
    }

    @Override
    public void shutdown() {
        closeAllMetrics();
        metrics.clear();
    }

    protected void closeAllMetrics() {
        for (ISymMetric metric : metrics.values()) {
            if (!metric.isOpen()) {
                continue;
            }
            try {
                metric.close();
                metric.removeAllObservations();
                log.debug("Closed metric {}", metric.getMetricId());
            } catch (Exception ex) {
                log.warn("Failed to close metric " + metric.getMetricId(), ex);
            }
        }
    }
}
