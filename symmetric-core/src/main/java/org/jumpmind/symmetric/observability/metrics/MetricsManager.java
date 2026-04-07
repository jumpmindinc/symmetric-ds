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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.observability.stats.MetricAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

public class MetricsManager {
    private static final Logger log = LoggerFactory.getLogger(MetricsManager.class);
    private static volatile MetricsManager globalInstance;
    private OpenTelemetry openTelemetry;
    private final boolean isOtelSdkInternal;
    private final boolean isOtelPublishingEnabled;
    private HostMetricsService hostMetricsService;
    private Meter otelMeter;
    private final List<IEngineMetricsService> engineMetricsServices = new CopyOnWriteArrayList<>();
    private MetricAggregator aggregator;

    private MetricsManager() {
        this.isOtelPublishingEnabled = isSystemPropetryOtelPublishingEnabled();
        log.debug("Starting MetricsManager. isOtelPublishingEnabled={}", isOtelPublishingEnabled);
        OpenTelemetry global = GlobalOpenTelemetry.get();
        if (global == OpenTelemetry.noop()) {
            this.openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
            this.isOtelSdkInternal = true;
            otelMeter = openTelemetry.getMeter(SymMetricConstants.OTEL_INSTRUMENTATION_SCOPE);
            log.info("MetricsManager initialized with autoconfigured OpenTelemetry SDK");
        } else {
            this.openTelemetry = global;
            this.isOtelSdkInternal = false;
            otelMeter = openTelemetry.getMeter(SymMetricConstants.OTEL_INSTRUMENTATION_SCOPE);
            log.info("MetricsManager initialized using existing global OpenTelemetry (agent detected)");
        }
    }

    MetricsManager(OpenTelemetry openTelemetry) {
        this.isOtelPublishingEnabled = isSystemPropetryOtelPublishingEnabled();
        this.openTelemetry = openTelemetry;
        this.isOtelSdkInternal = false;
        otelMeter = openTelemetry.getMeter(SymMetricConstants.OTEL_INSTRUMENTATION_SCOPE);
        log.info("MetricsManager initialized using specified OpenTelemetry");
    }

    private boolean isSystemPropetryOtelPublishingEnabled() {
        String otelEnabledProperty = System.getProperty(ParameterConstants.OTEL_METRICS_ENABLED);
        return StringUtils.isBlank(otelEnabledProperty) || otelEnabledProperty.equalsIgnoreCase("true");
    }

    public static MetricsManager getGlobalInstance() {
        if (globalInstance != null) {
            return globalInstance;
        }
        try {
            synchronized (MetricsManager.class) {
                if (globalInstance == null) {
                    globalInstance = new MetricsManager();
                }
            }
        } catch (Exception ex) {
            log.error("Failed to initialize MetricsManager! Double-check OpenTelemetry configuration.", ex);
        }
        return globalInstance;
    }

    public synchronized HostMetricsService getHostMetricsService() {
        if (hostMetricsService != null) {
            return hostMetricsService;
        }
        try {
            if (hostMetricsService == null) {
                hostMetricsService = new HostMetricsService(this, this.isOtelPublishingEnabled);
            }
        } catch (Exception ex) {
            log.error("Failed to initialize Host metrics service!", ex);
        }
        return hostMetricsService;
    }

    protected OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    protected Meter getOtelMeter() {
        return this.otelMeter;
    }

    public DoubleGauge createGauge(String metricId, String description, String unitOfMeasurement) {
        return otelMeter.gaugeBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .build();
    }

    public ObservableDoubleGauge createObservableGauge(String metricId, String description, String unitOfMeasurement,
            Supplier<Double> valueSupplier) {
        return otelMeter.gaugeBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .buildWithCallback(measurement -> measurement.record(valueSupplier.get()));
    }

    public LongCounter createIncreasingCounter(String metricId, String description, String unitOfMeasurement) {
        return otelMeter.counterBuilder(metricId)
                .setUnit(unitOfMeasurement)
                .setDescription(description)
                .build();
    }

    public LongUpDownCounter createUpDownCounter(String metricId, String description, String unitOfMeasurement) {
        return otelMeter.upDownCounterBuilder(metricId)
                .setUnit(unitOfMeasurement)
                .setDescription(description)
                .build();
    }

    public DoubleHistogram createHistogram(String metricId, String description, String unitOfMeasurement) {
        return otelMeter.histogramBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .build();
    }

    public void register(IEngineMetricsService engineMetricsService) {
        engineMetricsServices.add(engineMetricsService);
    }

    public void unregister(IEngineMetricsService engineMetricsService) {
        engineMetricsServices.remove(engineMetricsService);
    }

    public List<IEngineMetricsService> getEngineMetricsServices() {
        return Collections.unmodifiableList(engineMetricsServices);
    }

    public synchronized void startAggregation() {
        if (aggregator == null) {
            aggregator = new MetricAggregator(this, resolveHostname());
        }
        aggregator.start();
    }

    public MetricAggregator getAggregator() {
        return aggregator;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    public void shutdown() {
        if (aggregator != null) {
            aggregator.stop();
            aggregator = null;
        }
        if (isOtelSdkInternal && openTelemetry instanceof OpenTelemetrySdk sdk) {
            sdk.shutdown();
            log.info("MetricsManager and OpenTelemetry SDK are shut down");
        } else {
            log.info("MetricsManager is shut down");
        }
        this.openTelemetry = null;
        this.hostMetricsService = null;
        this.otelMeter = null;
    }
}
