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
import java.util.concurrent.atomic.AtomicReference;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.models.MetricContext;
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
    private final AtomicReference<MetricsRepository> repository = new AtomicReference<>();

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
        log.info("Engine metrics service shut down");
    }

    @Override
    public void initRepository() {
        getOrInitRepository();
    }

    /**
     * Drains completed intervals from all metrics owned by this service and persists them to the {@link MetricsRepository}.
     */
    @Override
    public void saveCompletedIntervalStats() {
        MetricsRepository repo = getOrInitRepository();
        List<MetricIntervalStatsRecord> newlyCompleted = new ArrayList<>();
        int processedMetrics = 0;
        Collection<ISymMetric> allMetrics = getAllMetrics();
        for (ISymMetric metric : allMetrics) {
            if (!metric.isEnabled()) {
                continue;
            }
            try {
                metric.closeCompletedIntervals();
                MetricKey key = repo.getMetricKey(metric.getMetricId(), metric.getFactType(), metric.isEnabled());
                long contextId = getOrAssignContextId(metric, repo);
                for (ISymIntervalStats interval : metric.exportCompletedIntervals()) {
                    newlyCompleted.add(new MetricIntervalStatsRecord(key, contextId, interval));
                }
                processedMetrics++;
            } catch (Exception ex) {
                log.warn("Failed to export completed intervals for metric={}", metric.getMetricId(), ex);
            }
        }
        if (processedMetrics > 0 && !newlyCompleted.isEmpty()) {
            log.debug("Saving {} metric interval stats records...", newlyCompleted.size());
            repo.saveIntervals(newlyCompleted);
        }
        log.debug("Saved {} completed interval stats records for {} metrics (in specific context).", newlyCompleted.size(), processedMetrics);
    }

    protected long getOrAssignContextId(ISymMetric metric, MetricsRepository repo) {
        long contextId = MetricContext.UNDEFINED;
        if (metric.getContext() != null) {
            contextId = metric.getContext().getContextId();
        } else if (repo != null && !metric.getAttributes().isEmpty()) {
            MetricContext ctx = repo.getOrRegisterContext(metric.getAttributes());
            metric.setContext(ctx);
            contextId = ctx.contextId();
        }
        return contextId;
    }

    protected MetricsRepository createMetricsRepository() {
        MetricsRepository repo = new MetricsRepository(engine, hostname);
        log.debug("Created metrics repository object for engine {}", engine.getEngineName());
        return repo;
    }

    private MetricsRepository getOrInitRepository() {
        MetricsRepository repo = repository.get();
        if (repo != null) {
            return repo;
        }
        log.debug("About to create metrics repository object for engine {}", engine.getEngineName());
        synchronized (this) {
            repo = repository.get();
            if (repo == null) {
                repo = createMetricsRepository();
                initializeDefaultMetrics();
                initializeDefaultContexts();
                initializeStatsWorksetsForAllMetrics(repo);
                repository.set(repo);
            }
        }
        return repository.get();
    }

    /**
     * Initializes default metrics, to achieve two goals: faster instrumentation ramp-up and seed historical data for outlier detection. Prepares for outlier
     * interval detection logic and accelerates move of completed intervals to database.
     */
    protected int initializeDefaultMetrics() {
        try {
            int count = metricsManager.getMetricDefinitionFactory().initializeMetrics(this);
            log.debug("Initialized repository with {} default metrics for engine {}", count, engine.getEngineName());
            return count;
        } catch (Exception ex) {
            log.warn("Failed to initialize metrics repository with important metrics (and historical values) for engine {}", engine.getEngineName(), ex);
            return 0;
        }
    }

    /**
     * Initializes default metrics, to have consistent IDs for default context entries across all installations.
     */
    protected void initializeDefaultContexts() {
        MetricsRepository repo = getOrInitRepository();
        List<ContextDefinition> defs = metricsManager.getMetricDefinitionFactory().getDefaultContexts();
        int count = 0;
        for (ContextDefinition def : defs) {
            try {
                repo.getOrRegisterContext(def);
                count++;
            } catch (Exception ex) {
                log.warn("Failed to register default context id={}", def.contextId(), ex);
            }
        }
        log.info("Initialized {} default metric contexts for engine {}", count, engine.getEngineName());
    }

    /**
     * Seeds the sliding workset of every registered metric with historical intervals to enable outlier interval detection logic.
     */
    private int initializeStatsWorksetsForAllMetrics(MetricsRepository repo) {
        int metricsInitialized = 0;
        try {
            for (AbstractQueuedMetric metric : (java.util.Collection<AbstractQueuedMetric>) (java.util.Collection<?>) getAllMetrics()) {
                metricsInitialized += initWorksetForMetric(metric, repo);
            }
            log.debug("Initialized {} metrics in repository for engine {}", metricsInitialized, engine.getEngineName());
        } catch (Exception ex) {
            log.warn("Failed to initialize metrics repository with historical values for engine {}", engine.getEngineName(), ex);
        }
        return metricsInitialized;
    }

    private int initWorksetForMetric(AbstractQueuedMetric metric, MetricsRepository repo) {
        try {
            MetricKey key = repo.getMetricKey(metric.getMetricId(), metric.getFactType(), metric.isEnabled());
            if (key.isEnabled()) {
                List<ISymIntervalStats> history = repo.loadRecentIntervalsForKeyFromDatabase(key);
                metric.seedWorkset(history);
                return 1;
            }
            metric.close();
            log.warn("Metric was closed because it is not enabled in database. {}", key);
        } catch (Exception e) {
            log.warn("Failed to pre-warm workset for metric=" + metric.getMetricId(), e);
        }
        return 0;
    }
}
