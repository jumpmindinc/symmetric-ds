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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;

/**
 * This class is intended for collecting a small number of in-memory metrics, which are not engine-specific, but rather describe the host (server) as a whole.
 */
class HostMetricsService implements IMetricsService {

    private static final Logger log = LoggerFactory.getLogger(HostMetricsService.class);


    private final MetricsManager metricsManager;
    private final Map<String, LongCounter> counters = new ConcurrentHashMap<>();
    private final Map<String, DoubleHistogram> histograms = new ConcurrentHashMap<>();

    // private HostMetricsService() {
    // log.debug("Starting Host metrics service");
    // this.meter = MetricsManager.getGlobalInstance().getOpenTelemetry().getMeter(INSTRUMENTATION_SCOPE);
    // MetricsManager.getGlobalInstance().register(this);
    // log.debug("Started Host metrics service");
    // }

    HostMetricsService(MetricsManager metricsManager) {
        this.metricsManager = metricsManager;
        log.debug("Started Host metrics service");
    }


    //
    // @Override
    // public void createGauge(String name, String description, String unit,
    // Supplier<Double> valueSupplier) {
    // meter.gaugeBuilder(name)
    // .setDescription(description)
    // .setUnit(unit)
    // .buildWithCallback(measurement -> measurement.record(valueSupplier.get()));
    // }
    //
    // @Override
    // public void createCounter(String name, String description, long delta) {
    // LongCounter counter = counters.computeIfAbsent(name, k ->
    // meter.counterBuilder(k)
    // .setDescription(description)
    // .build());
    // counter.add(delta);
    // }
    //
    // @Override
    // public void createHistogram(String name, String description, String unit, double value) {
    // DoubleHistogram histogram = histograms.computeIfAbsent(name, k ->
    // meter.histogramBuilder(k)
    // .setDescription(description)
    // .setUnit(unit)
    // .build());
    // histogram.record(value);
    // }

    @Override
    public void shutdown() {
        log.info("Host metrics service shut down");
    }
}
