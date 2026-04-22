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

    public IUpDownCounter registerUpDownCounter(ISymMetricDefinition definition) {
        return upDownCounters.computeIfAbsent(definition.id(),
                id -> createUpDownCounterInternal(definition));
    }

    private UpDownCounter createUpDownCounterInternal(ISymMetricDefinition definition) {
        UpDownCounter counter = new UpDownCounter(definition.id(), attributes);
        if (isOtelPublishingEnabled) {
            otelHandles.add(metricsManager.createUpDownCounter(
                    definition.id(), definition.description(), definition.unit(), counter::getValue, attributes));
        }
        return counter;
    }

    public IUpDownCounter getUpDownCounter(String metricId) {
        return upDownCounters.get(metricId);
    }

    public ISymDoubleGauge registerGauge(ISymMetricDefinition definition) {
        return gauges.computeIfAbsent(definition.id(), k -> createGaugeInternal(definition));
    }

    private SymDoubleGauge createGaugeInternal(ISymMetricDefinition definition) {
        SymDoubleGauge gauge = new SymDoubleGauge(definition.id(), attributes);
        if (isOtelPublishingEnabled) {
            otelHandles.add(metricsManager.createGauge(
                    definition.id(), definition.description(), definition.unit(), gauge::getValue, attributes));
        }
        return gauge;
    }

    public ISymDoubleGauge getGauge(String metricId) {
        return gauges.get(metricId);
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
