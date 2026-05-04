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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.IPrimaryMetricAggregator;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants;
import org.jumpmind.symmetric.observability.stats.PrimaryMetricAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

/**
 * The singular and central point of managing both embedded metrics and OpenTelemetry (if configured).
 */
public class MetricsManager {
    private static final Logger log = LoggerFactory.getLogger(MetricsManager.class);
    private static final AtomicReference<MetricsManager> globalInstance = new AtomicReference<>();
    private OpenTelemetry openTelemetry;
    private final boolean isOtelSdkInternal;
    private final boolean isOtelPublishingEnabled;
    private HostMetricsService hostMetricsService;
    private Meter otelMeter;
    private final List<IEngineMetricsService> engineMetricsServices = new CopyOnWriteArrayList<>();
    private IPrimaryMetricAggregator aggregator; // Runs on a dedicated thread, to avoid impacting instrumented code.
    private final IMetricDefinitionFactory definitionFactory;

    private MetricsManager() {
        this.isOtelPublishingEnabled = isSystemPropertyOtelPublishingEnabled();
        log.debug("Starting MetricsManager. isOtelPublishingEnabled={}", isOtelPublishingEnabled);
        OpenTelemetry global = GlobalOpenTelemetry.get();
        if (global == OpenTelemetry.noop()) {
            this.openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
            this.isOtelSdkInternal = true;
            otelMeter = openTelemetry.getMeter(SymMetricConstants.OTEL_SCOPE);
            log.info("MetricsManager initialized with autoconfigured OpenTelemetry SDK");
        } else {
            this.openTelemetry = global;
            this.isOtelSdkInternal = false;
            otelMeter = openTelemetry.getMeter(SymMetricConstants.OTEL_SCOPE);
            log.info("MetricsManager initialized using existing global OpenTelemetry (agent detected)");
        }
        definitionFactory = getMetricDefinitionFactory();
    }

    MetricsManager(OpenTelemetry openTelemetry) {
        this.isOtelPublishingEnabled = isSystemPropertyOtelPublishingEnabled();
        this.openTelemetry = openTelemetry;
        this.isOtelSdkInternal = false;
        otelMeter = openTelemetry.getMeter(SymMetricConstants.OTEL_SCOPE);
        log.info("MetricsManager initialized using specified OpenTelemetry");
        definitionFactory = getMetricDefinitionFactory();
    }

    public IMetricDefinitionFactory getMetricDefinitionFactory() {
        if (definitionFactory == null) {
            return new MetricDefinitionFactory();
        }
        return definitionFactory;
    }

    private boolean isSystemPropertyOtelPublishingEnabled() {
        String otelEnabledProperty = System.getProperty(ParameterConstants.OTEL_METRICS_ENABLED);
        return StringUtils.isBlank(otelEnabledProperty) || otelEnabledProperty.equalsIgnoreCase("true");
    }

    public static MetricsManager getGlobalInstance() {
        MetricsManager instance = globalInstance.get();
        if (instance != null) {
            return instance;
        }
        try {
            synchronized (MetricsManager.class) {
                if (globalInstance.get() == null) {
                    globalInstance.set(new MetricsManager());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to initialize MetricsManager! Double-check OpenTelemetry configuration.", ex);
        }
        return globalInstance.get();
    }

    public synchronized HostMetricsService getHostMetricsService() {
        if (hostMetricsService != null) {
            return hostMetricsService;
        }
        try {
            hostMetricsService = new HostMetricsService(this, this.isOtelPublishingEnabled);
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

    /**
     * Registers a callback-based observable double gauge. The OTel SDK pulls the current value from {@code valueSupplier} on each export cycle rather than
     * blocking the instrumented thread. The returned handle is {@link AutoCloseable}; close it to unregister the callback.
     */
    public ObservableDoubleGauge createDoubleGauge(String metricId, String description, String unitOfMeasurement,
            DoubleSupplier valueSupplier, Attributes attributes) {
        return otelMeter.gaugeBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .buildWithCallback(measurement -> measurement.record(valueSupplier.getAsDouble(), attributes));
    }

    /**
     * Registers a callback-based observable double gauge with no extra attributes. The returned handle is {@link AutoCloseable}; close it to unregister the
     * callback.
     */
    public ObservableDoubleGauge createObservableDoubleGauge(String metricId, String description, String unitOfMeasurement,
            DoubleSupplier valueSupplier) {
        return otelMeter.gaugeBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .buildWithCallback(measurement -> measurement.record(valueSupplier.getAsDouble()));
    }

    /**
     * Registers a callback-based observable long gauge. The OTel SDK pulls the current value from {@code valueSupplier} on each export cycle rather than
     * blocking the instrumented thread. The returned handle is {@link AutoCloseable}; close it to unregister the callback.
     */
    public ObservableLongGauge createLongGauge(String metricId, String description, String unitOfMeasurement,
            LongSupplier valueSupplier, Attributes attributes) {
        return otelMeter.gaugeBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(valueSupplier.getAsLong(), attributes));
    }

    /**
     * Registers a callback-based observable long gauge with no extra attributes. The returned handle is {@link AutoCloseable}; close it to unregister the
     * callback.
     */
    public ObservableLongGauge createObservableLongGauge(String metricId, String description, String unitOfMeasurement,
            LongSupplier valueSupplier) {
        return otelMeter.gaugeBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .ofLongs()
                .buildWithCallback(measurement -> measurement.record(valueSupplier.getAsLong()));
    }

    /**
     * Registers a callback-based observable monotonic counter. The OTel SDK reads the cumulative total from {@code valueSupplier} on each export cycle. The
     * returned handle is {@link AutoCloseable}; close it to unregister the callback.
     */
    public ObservableLongCounter createIncreasingCounter(String metricId, String description, String unitOfMeasurement,
            LongSupplier valueSupplier, Attributes attributes) {
        return otelMeter.counterBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .buildWithCallback(measurement -> measurement.record(valueSupplier.getAsLong(), attributes));
    }

    /**
     * Registers a callback-based observable up-down counter. The OTel SDK reads the current value from {@code valueSupplier} on each export cycle rather than
     * blocking the instrumented thread. The returned handle is {@link AutoCloseable}; close it to unregister the callback.
     */
    public ObservableLongUpDownCounter createUpDownCounter(String metricId, String description, String unitOfMeasurement,
            LongSupplier valueSupplier, Attributes attributes) {
        return otelMeter.upDownCounterBuilder(metricId)
                .setDescription(description)
                .setUnit(unitOfMeasurement)
                .buildWithCallback(measurement -> measurement.record(valueSupplier.getAsLong(), attributes));
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
            aggregator = new PrimaryMetricAggregator(this, resolveHostname());
        }
        aggregator.start();
    }

    public IPrimaryMetricAggregator getAggregator() {
        return aggregator;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    /**
     * Stops the aggregator (which shuts down all registered engine services and closes their OTel handles), then shuts down the host metrics service, and
     * finally tears down the OTel SDK if it was auto-initialized by this manager.
     */
    public void shutdown() {
        if (aggregator != null) {
            aggregator.stop();
            aggregator = null;
        }
        if (hostMetricsService != null) {
            hostMetricsService.shutdown();
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
