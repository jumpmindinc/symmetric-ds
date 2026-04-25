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
package org.jumpmind.symmetric.observability.stats;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.IPrimaryMetricAggregator;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.metrics.MetricsManager;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Periodically drains observation queues from all registered {@link IEngineMetricsService} instances, assigns observations to an interval (time window) via
 * accumulators, and initiates export of completed {@link MetricIntervalStats} records to database. *
 * <p>
 * Runs on a dedicated daemon thread. Call {@link #start()} once and {@link #stop()} on shutdown. The thread exits cleanly on both an explicit {@code stop()}
 * and a JVM interrupt.
 */
public class PrimaryMetricAggregator implements IPrimaryMetricAggregator {
    private static final Logger log = LoggerFactory.getLogger(PrimaryMetricAggregator.class);
    static final int MAX_HISTORY_INTERVALS = 12;
    static final long PROCESSING_INTERVAL_10SEC = 10000L;
    static final String AGGREGATOR_PROCESSING_THREAD = "metrics-primary-aggregator";
    private final MetricsManager metricsManager;
    private final String hostname;
    private volatile boolean running;
    private static final AtomicReference<Thread> thread = new AtomicReference<>();

    public PrimaryMetricAggregator(MetricsManager metricsManager, String hostname) {
        this.metricsManager = metricsManager;
        this.hostname = hostname;
    }

    public synchronized boolean isRunning() {
        Thread t = thread.get();
        return t != null && t.isAlive();
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            log.debug("{} thread is already running, skipping start. Hostname={}, threadId={}", AGGREGATOR_PROCESSING_THREAD, hostname, thread.get().getId());
            return;
        }
        log.debug("Starting {} thread... Hostname={}", AGGREGATOR_PROCESSING_THREAD, hostname);
        running = true;
        Thread t = new Thread(this::run, AGGREGATOR_PROCESSING_THREAD);
        t.setDaemon(true);
        thread.set(t);
        t.start();
        log.info("Started {} thread. Hostname={}, threadId={}", AGGREGATOR_PROCESSING_THREAD, hostname, t.getId());
    }

    @Override
    public void stop() {
        if (!isRunning()) {
            log.debug("{} thread was already stopped, skipping interrupt. Hostname={}", AGGREGATOR_PROCESSING_THREAD, hostname);
            return;
        }
        Thread t = thread.get();
        log.debug("Stopping {} thread... Hostname={}, threadId={}", AGGREGATOR_PROCESSING_THREAD, hostname, t != null ? t.getId() : -1);
        running = false;
        if (t != null) {
            t.interrupt();
            log.info("Stopped {} thread. Hostname={}, Thread.id={}", AGGREGATOR_PROCESSING_THREAD, hostname, t.getId());
        }
    }

    private void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                processAll();
                Thread.sleep(PROCESSING_INTERVAL_10SEC);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted " + AGGREGATOR_PROCESSING_THREAD, e);
                closeAll();
                return; // Interruptions require immediate exit, without draining pending observations!
            } catch (Exception e) {
                log.error("Error during metrics processing", e);
            }
        }
        processAll(); // final drain before exit
        closeAll(); // close all open accumulators before exit
        log.info("Exited metrics processing gracefully.");
    }

    void closeAll() {
        for (IEngineMetricsService svc : metricsManager.getEngineMetricsServices()) {
            try {
                svc.shutdown();
            } catch (Exception e) {
                log.error("Failed to close metrics service for engine " + svc.getEngineName(), e);
            }
        }
    }

    void processAll() {
        for (IEngineMetricsService svc : metricsManager.getEngineMetricsServices()) {
            MDC.put("engineName", svc.getEngineName());
            try {
                Collection<ISymMetric> allMetrics = svc.getAllMetrics();
                for (ISymMetric metric : allMetrics) {
                    metric.processAllObservationsAndRefreshInterval();
                }
                svc.saveCompletedIntervalStats();
            } catch (Exception ex) {
                log.warn("Failed to process metrics for engine " + svc.getEngineName(), ex);
            } finally {
                MDC.remove("engineName");
            }
        }
    }
}
