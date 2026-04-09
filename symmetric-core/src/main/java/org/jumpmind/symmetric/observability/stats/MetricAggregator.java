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

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.observability.metrics.AbstractQueuedMetric;
import org.jumpmind.symmetric.observability.metrics.IEngineMetricsService;
import org.jumpmind.symmetric.observability.metrics.ISymObservation;
import org.jumpmind.symmetric.observability.metrics.MetricsManager;
import org.jumpmind.symmetric.observability.models.MetricInterval;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically drains observation queues from all registered {@link IEngineMetricsService} instances,
 * assigns observations to 5-minute window accumulators, and stores completed {@link MetricInterval} records.
 *
 * <p>Runs on a dedicated daemon thread. Call {@link #start()} once and {@link #stop()} on shutdown.
 * The thread exits cleanly on both an explicit {@code stop()} and a JVM interrupt.
 */
public class MetricAggregator {
    private static final Logger log = LoggerFactory.getLogger(MetricAggregator.class);

    static final int MAX_HISTORY_INTERVALS = 12;
    static final long PROCESSING_INTERVAL_MS = 30_000L;

    private final MetricsManager metricsManager;
    private final String hostname;
    private final Map<MetricKey, MetricIntervalAccumulator> openAccumulators = new ConcurrentHashMap<>();
    private final Map<MetricKey, ArrayDeque<MetricInterval>> completedIntervals = new ConcurrentHashMap<>();

    private volatile boolean running;
    private volatile Thread thread;
    private volatile EngineMetricIntervalsCollector collector;

    public MetricAggregator(MetricsManager metricsManager, String hostname) {
        this.metricsManager = metricsManager;
        this.hostname = hostname;
    }

    public void start() {
        running = true;
        thread = new Thread(this::run, "metrics-aggregator");
        thread.setDaemon(true);
        thread.start();
        log.info("MetricAggregator started (hostname={})", hostname);
    }

    public void stop() {
        running = false;
        Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
    }

    public void setCollector(EngineMetricIntervalsCollector collector) {
        this.collector = collector;
    }

    private void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                processAll();
                Thread.sleep(PROCESSING_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error during metric aggregation", e);
            }
        }
        processAll();  // final drain before exit
        log.info("MetricAggregator stopped");
    }

    void processAll() {
        long nowMs = System.currentTimeMillis();
        List<Map.Entry<MetricKey, MetricInterval>> newlyCompleted = new ArrayList<>();
        for (IEngineMetricsService svc : metricsManager.getEngineMetricsServices()) {
            for (AbstractQueuedMetric metric : svc.getAllMetrics()) {
                MetricKey key = new MetricKey(hostname, svc.getEngineName(), metric.getMetricId());
                ISymObservation[] obs = metric.removeAllObservations();
                if (obs.length > 0) {
                    processObservations(key, obs, newlyCompleted);
                }
            }
        }
        closeExpiredAccumulators(nowMs, newlyCompleted);
        if (collector != null && !newlyCompleted.isEmpty()) {
            collector.save(newlyCompleted);
        }
    }

    private void processObservations(MetricKey key, ISymObservation[] obs, List<Map.Entry<MetricKey, MetricInterval>> newlyCompleted) {
        for (ISymObservation o : obs) {
            double value = o.getValueAsDouble();
            long ts = o.getTimestamp();
            long bucketStart = (ts / MetricIntervalAccumulator.INTERVAL_DURATION_MS) * MetricIntervalAccumulator.INTERVAL_DURATION_MS;

            MetricIntervalAccumulator acc = openAccumulators.get(key);

            if (acc == null) {
                acc = new MetricIntervalAccumulator(bucketStart, 0.0);
                openAccumulators.put(key, acc);
            } else if (ts >= acc.intervalEnd) {
                acc.closeAt(acc.intervalEnd);
                save(key, acc.toMetricInterval(), newlyCompleted);

                double carryForward = acc.getLastValue();
                long nextStart = acc.intervalEnd;
                while (nextStart + MetricIntervalAccumulator.INTERVAL_DURATION_MS <= bucketStart) {
                    MetricIntervalAccumulator empty = new MetricIntervalAccumulator(nextStart, carryForward);
                    empty.closeAt(nextStart + MetricIntervalAccumulator.INTERVAL_DURATION_MS);
                    save(key, empty.toMetricInterval(), newlyCompleted);
                    nextStart += MetricIntervalAccumulator.INTERVAL_DURATION_MS;
                }

                acc = new MetricIntervalAccumulator(bucketStart, carryForward);
                openAccumulators.put(key, acc);
            }

            acc.addObservation(value, ts);
        }
    }

    private void closeExpiredAccumulators(long nowMs, List<Map.Entry<MetricKey, MetricInterval>> newlyCompleted) {
        Iterator<Map.Entry<MetricKey, MetricIntervalAccumulator>> it = openAccumulators.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<MetricKey, MetricIntervalAccumulator> entry = it.next();
            MetricIntervalAccumulator acc = entry.getValue();
            if (acc.intervalEnd <= nowMs) {
                acc.closeAt(acc.intervalEnd);
                save(entry.getKey(), acc.toMetricInterval(), newlyCompleted);
                it.remove();
            }
        }
    }

    private void save(MetricKey key, MetricInterval interval, List<Map.Entry<MetricKey, MetricInterval>> newlyCompleted) {
        ArrayDeque<MetricInterval> deque = completedIntervals.computeIfAbsent(key, k -> new ArrayDeque<>());
        deque.addFirst(interval);
        while (deque.size() > MAX_HISTORY_INTERVALS) {
            deque.removeLast();
        }
        newlyCompleted.add(new AbstractMap.SimpleImmutableEntry<>(key, interval));
    }

    public List<MetricInterval> getCompletedIntervals(MetricKey key) {
        ArrayDeque<MetricInterval> deque = completedIntervals.get(key);
        return deque == null ? List.of() : List.copyOf(deque);
    }

    // private static double doubleValueOf(ISymObservation obs) {
    // if(obs instanceof ObservationLong ) {
    // return (double) ol.value();
    // }
    // else
    // if(obs instanceof ObservationDouble ) { od -> od.value();
    // default -> throw new IllegalArgumentException("Unknown observation type: " + obs.getClass());
    // };
    // }
}
