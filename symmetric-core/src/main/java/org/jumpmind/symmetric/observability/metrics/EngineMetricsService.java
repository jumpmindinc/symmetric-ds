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
    public void initRepository() {
        getOrInitRepository();
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
        int exportedMetrics = 0;
        for (AbstractQueuedMetric metric : getAllMetrics()) {
            try {
                metric.closeCompletedIntervals();
                MetricKey key = repo.getMetricKey(metric.getMetricId(), metric.getFactType());
                for (ISymIntervalStats interval : metric.exportCompletedIntervals(key)) {
                    newlyCompleted.add(new MetricIntervalStatsRecord(key, interval));
                }
                exportedMetrics++;
            } catch (Exception ex) {
                log.warn("Failed to export completed intervals for metric={}", metric.getMetricId());
                continue;
            }
        }
        if (exportedMetrics > 0 && !newlyCompleted.isEmpty()) {
            log.debug("Saving {} metric interval stats records...", newlyCompleted.size());
            repo.saveIntervals(newlyCompleted);
        }
        log.debug("Saved {} completed interval stats records for {} metrics.", newlyCompleted.size(), exportedMetrics);
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
        try {
            initializeImportantMetrics(repository);
        } catch (Exception ex) {
            log.warn("Failed to initialize metrics repository with important metrics (and historical values) for engine {}", engine.getEngineName());
        }
        try {
            initializeStatsWorksetsForAllMetrics(repository);
        } catch (Exception ex) {
            log.warn("Failed to initialize metrics repository with historical values for engine {}", engine.getEngineName());
        }
        return repository;
    }

    /**
     * Initializes important metrics, to achieve two goals: faster instrumentation ramp-up and seed historical data for outlier detection. Prepares for outlier
     * interval detection logic and accelerates move of completed intervals to database.
     */
    protected int initializeImportantMetrics(MetricsRepository repo) {
        int count = DefaultEngineMetrics.initializeDefaultMetrics(this);
        log.debug("Initialized repository with {} important metrics for engine {}", count, engine.getEngineName());
        return count;
    }

    /**
     * Seeds the sliding workset of every registered metric with historical intervals to enable outlier interval detection logic.
     */
    private int initializeStatsWorksetsForAllMetrics(MetricsRepository repo) {
        int metricsInitialized = 0;
        for (AbstractQueuedMetric metric : getAllMetrics()) {
            try {
                MetricKey key = repo.getMetricKey(metric.getMetricId(), metric.getFactType());
                List<ISymIntervalStats> history = repo.loadRecentIntervalsForKeyFromDatabase(key);
                metric.seedWorkset(history);
                metricsInitialized++;
            } catch (Exception e) {
                log.warn("Failed to pre-warm workset for metric=" + metric.getMetricId(), e);
            }
        }
        log.debug("Initialized {} metrics in repository for engine {}", metricsInitialized, engine.getEngineName());
        return metricsInitialized;
    }

}
