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

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricIntervalStatsRecord;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.jumpmind.symmetric.observability.repository.MetricsRepository;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

/**
 * Per parent class, collects observations for registered metrics (counters, gauges, histograms). It is intended for engine/node-specific metrics. Results are
 * aggregated by time window and stored in engine's runtime database.
 */
public class EngineMetricsService extends AbstractMetricsService implements IEngineMetricsService {
    private final ISymmetricEngine engine;
    private volatile MetricsRepository repository;

    public EngineMetricsService(ISymmetricEngine engine, MetricsManager metricsManager, boolean isOtelPublishingEnabled) {
        super(metricsManager, Attributes.of(AttributeKey.stringKey("engine.name"), engine.getEngineName()), isOtelPublishingEnabled);
        this.engine = engine;
        metricsManager.register(this);
    }

    @Override
    public String getEngineName() {
        return engine.getEngineName();
    }

    @Override
    public void shutdown() {
        metricsManager.unregister(this);
        super.shutdown();
        log.info("Host metrics service shut down");
    }

    @Override
    public void saveCompletedIntervalStats() {
        saveCompletedIntervalsForAllMetrics();
    }

    /**
     * Drains completed intervals from every metric owned by this service and persists them to the engine's database via {@link MetricsRepository}.
     */
    protected void saveCompletedIntervalsForAllMetrics() {
        MetricsRepository repo = getOrInitRepository();
        List<MetricIntervalStatsRecord> newlyCompleted = new ArrayList<>();
        for (AbstractQueuedMetric metric : getAllMetrics()) {
            MetricKey key = repo.getMetricKey(metric.getMetricId());
            for (MetricIntervalStats interval : metric.exportCompletedIntervals(key)) {
                newlyCompleted.add(new MetricIntervalStatsRecord(key, interval));
            }
        }
        log.info("Saving {} metric intervat stats records...", newlyCompleted.size());
        if (!newlyCompleted.isEmpty()) {
            repo.saveIntervals(newlyCompleted);
        }
    }

    protected MetricsRepository createMetricsRepository() {
        return new MetricsRepository(engine, hostname);
    }

    private MetricsRepository getOrInitRepository() {
        if (repository != null) {
            return repository;
        }
        log.debug("About to create metrics repository object for engine {}", engine.getEngineName());
        synchronized (this) {
            if (repository == null) {
                repository = createMetricsRepository();
            }
        }
        log.debug("Created metrics repository object for engine {}", engine.getEngineName());
        initializeImportantMetrics(repository);
        initializeStatsWorksetsForAllMetrics(repository);
        log.info("Initialized metrics repository for engine {}", engine.getEngineName());
        return repository;
    }

    /**
     * Seeds the sliding workset of every registered metric with historical intervals loaded from the database. Called once, immediately after the repository is
     * initialized (on the aggregator thread), so IQR outlier detection is ready without blocking instrumented code.
     */
    private void initializeStatsWorksetsForAllMetrics(MetricsRepository repo) {
        for (AbstractQueuedMetric metric : getAllMetrics()) {
            try {
                MetricKey key = repo.getMetricKey(metric.getMetricId());
                List<MetricIntervalStats> history = repo.loadRecentIntervalsForKeyFromDatabase(key);
                metric.prewarmWorkset(history);
            } catch (Exception e) {
                log.warn("Failed to pre-warm workset for metric {}", metric.getMetricId(), e);
            }
        }
    }

    /**
     * Initializes important metrics, to achive two goals: faster instrumentation ramp-up and seed historical data for outlier detection.
     */
    protected void initializeImportantMetrics(MetricsRepository repo) {
        getOrCreateUpDownCounter(SymMetricConstants.METRIC_CONNECTIONS_RESERVATIONS_ID,
                SymMetricConstants.METRIC_CONNECTIONS_RESERVATIONS_DESC, SymMetricConstants.METRIC_CONNECTIONS_RESERVATIONS_UNIT);
    }
}
