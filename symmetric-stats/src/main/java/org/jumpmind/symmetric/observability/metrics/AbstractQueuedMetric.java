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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricContext;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricDefinition;
import org.jumpmind.symmetric.observability.interfaces.ISymObservation;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.stats.AbstractStatsAccumulator;
import org.jumpmind.symmetric.observability.stats.Float64StatsAccumulator;
import org.jumpmind.symmetric.observability.stats.MetricIntervalStatsQueue;
import org.jumpmind.symmetric.observability.stats.MetricSeriesSlidingWorkset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.common.Attributes;

/**
 * Holds internal size-limited (and temporary) collection of observations, until they are removed, processed and/or stored somewhere else. All observations
 * should be the for the same metric, i.e. same context (node, server, etc.) and attributes.
 */
public abstract class AbstractQueuedMetric implements ISymMetric {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    private final String metricId;
    protected volatile AutoCloseable externalTelemetryHandle = null;
    protected final Attributes attributes;
    private final List<MetricAttribute> metricAttributes;
    private final MetricFactType factType;
    private final InstrumentType metricType;
    private final AtomicReference<ISymMetricContext> contextRef = new AtomicReference<>();
    protected volatile long lastModified = System.currentTimeMillis();
    protected volatile boolean isMetricEnabled = true;
    protected volatile boolean isMetricOpen = false;
    protected ObservationsQueue<ISymObservation> observations = new ObservationsQueue<>();
    protected IStatsAccumulator currentIntervalAccumulator;
    protected final Object accumulatorLock = new Object();
    protected MetricIntervalStatsQueue completedIntervals = new MetricIntervalStatsQueue();
    protected MetricSeriesSlidingWorkset workset = new MetricSeriesSlidingWorkset();

    AbstractQueuedMetric(ISymMetricDefinition definition, Attributes attributes, List<MetricAttribute> metricAttributes, MetricFactType factType,
            InstrumentType metricType) {
        this.metricId = definition.id();
        this.attributes = attributes;
        this.metricAttributes = metricAttributes != null ? List.copyOf(metricAttributes) : List.of();
        this.factType = factType;
        this.metricType = metricType;
        this.currentIntervalAccumulator = null;
    }

    @Override
    public String getMetricId() {
        return metricId;
    }

    @Override
    public ISymMetricContext getContext() {
        return contextRef.get();
    }

    @Override
    public void setContext(ISymMetricContext context) {
        contextRef.compareAndSet(null, context);
    }

    @Override
    public List<MetricAttribute> getAttributes() {
        return metricAttributes;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    /** Returns the fact type for this metric, used to select the correct accumulator and persistence table. */
    @Override
    public MetricFactType getFactType() {
        return factType;
    }

    @Override
    public InstrumentType getMetricType() {
        return metricType;
    }

    /**
     * Returns estimated number of recorded observations (can change quickly in a highly concurrent environment)
     */
    public int getObservationsCountEstimate() {
        return this.observations.size();
    }

    @Override
    public boolean isOpen() {
        return isMetricOpen;
    }

    @Override
    public boolean isEnabled() {
        return isMetricEnabled;
    }

    @Override
    public void open(AutoCloseable externalMetricHandle) {
        if (!isMetricEnabled) {
            String message = String.format("Cannot open a disabled metric %s", metricId);
            log.warn(message);
            throw new RuntimeException(message);
        }
        lastModified = System.currentTimeMillis();
        externalTelemetryHandle = externalMetricHandle;
        isMetricOpen = true;
    }

    @Override
    public void close() {
        lastModified = System.currentTimeMillis();
        isMetricOpen = false;
        if (externalTelemetryHandle != null) {
            try {
                externalTelemetryHandle.close();
            } catch (Exception e) {
                log.warn("Failed to close external telemetry handle for {}", getMetricId(), e);
            } finally {
                externalTelemetryHandle = null;
            }
        }
    }

    @Override
    public IStatsAccumulator createAccumulator(long intervalStart) {
        return new Float64StatsAccumulator(intervalStart);
    }

    /**
     * Add new observation to an internal collection. Silently ignored when the metric is closed.
     */
    public void addObservation(ISymObservation observation) {
        if (!isMetricOpen || !isMetricEnabled) {
            if (log.isDebugEnabled()) {
                log.debug("Metric is not accepting new observations. MetricId={}, isOpen={}, isEnabled={}", 
                    metricId, isMetricOpen, isMetricEnabled);
            }
            return;
        }
        observations.add(observation);
        lastModified = System.currentTimeMillis();
    }

    /**
     * Atomically swaps the observation queue for a new empty one and returns the contents of the old queue as a list.
     */
    protected synchronized List<ISymObservation> retrieveAndSwapForNewQueue() {
        ObservationsQueue<ISymObservation> oldObservations = this.observations;
        this.observations = new ObservationsQueue<>();
        return oldObservations.toList();
    }

    /**
     * Returns all currently available observations (which can change quickly in a highly concurrent environment) and removes them from an internal queue
     */
    @Override
    public List<ISymObservation> removeAllObservations() {
        if (this.observations.isEmpty()) {
            return List.of();
        }
        List<ISymObservation> removed = retrieveAndSwapForNewQueue();
        lastModified = System.currentTimeMillis();
        if (log.isTraceEnabled()) {
            log.trace("Removed {} observations from the queue. MetricId={}", removed.size(), metricId);
        }
        return removed;
    }

    /**
     * Processes all available observations from an internal queue to interval accumulator.
     */
    public void processAllObservations() {
        try {
            List<ISymObservation> unprocessed = removeAllObservations();
            if (!unprocessed.isEmpty()) {
                processObservations(unprocessed);
            }
        } catch (Exception ex) {
            log.warn("Trouble processing observations for MetricId=" + metricId, ex);
        }
    }

    @Override
    public void processAllObservationsAndRefreshInterval() {
        processAllObservations();
        closeCompletedIntervals();
    }

    /**
     * Assigns each observation to the current accumulator, rolling over to a new window whenever an observation falls into a later bucket. Completed intervals
     * are enqueued into completedIntervals.
     */
    @Override
    public int processObservations(List<ISymObservation> obs) {
        int processedCount = 0;
        for (ISymObservation observation : obs) {
            processedCount += processObservation(observation);
        }
        if (log.isDebugEnabled()) {
            log.debug("Processed {} observations. MetricId={}", processedCount, metricId);
        }
        return processedCount;
    }

    public int processObservation(ISymObservation observation) {
        long timeWindowStart = AbstractStatsAccumulator.calculateIntervalStart(observation.getTimestamp());
        synchronized (accumulatorLock) {
            lastModified = System.currentTimeMillis();
            if (currentIntervalAccumulator == null) {
                currentIntervalAccumulator = createAccumulator(timeWindowStart);
            }
            if (currentIntervalAccumulator.isInScope(timeWindowStart)) {
                currentIntervalAccumulator.addObservation(observation);
                if (log.isTraceEnabled()) {
                    log.trace("Processed new observation. value={}, timestamp={}, MetricId={}, current.interval.start={}",
                            observation.getValueAsDouble(), observation.getTimestamp(), metricId, currentIntervalAccumulator.getIntervalStart());
                }
                return 1;
            }
            if (timeWindowStart < currentIntervalAccumulator.getIntervalStart()) {
                if (log.isDebugEnabled()) {
                    log.debug("Throwing away delinquent observation. timestamp={}, MetricId={}, current.interval.start={}",
                            observation.getTimestamp(), metricId, currentIntervalAccumulator.getIntervalStart());
                }
                return 0;
            }
            // The timeWindowStart is in the future, so close current window (plus carry-forward last value) and open new - until it reaches timeWindowStart:
            if (log.isDebugEnabled()) {
                log.debug("Closing previous interval and creating new one for incoming observation. timestamp={}, MetricId={}, interval.start={}",
                        observation.getTimestamp(), metricId, currentIntervalAccumulator.getIntervalStart());
            }
            while (!currentIntervalAccumulator.isInScope(timeWindowStart)) {
                closeAccumulatorAndOpenNewOne();
            }
            currentIntervalAccumulator.addObservation(observation);
            if (log.isDebugEnabled()) {
                log.debug("Started new interval and added observation. timestamp={}, MetricId={}, interval.start={}",
                        observation.getTimestamp(), metricId, currentIntervalAccumulator.getIntervalStart());
            }
        }
        return 1;
    }

    /**
     * Closes current accumulator and carries last value to the new accumulate instance (for next time window). Should be called from inside a
     * synchronized(accumulatorLock) block.
     */
    private void closeAccumulatorAndOpenNewOne() {
        if (currentIntervalAccumulator == null) {
            currentIntervalAccumulator = createAccumulator(AbstractStatsAccumulator.calculateIntervalStart(System.currentTimeMillis()));
        } else {
            currentIntervalAccumulator.close();
            addToCompletedIntervals(currentIntervalAccumulator.toStats());
            currentIntervalAccumulator = currentIntervalAccumulator.createNext();
        }
    }

    /**
     * If the current accumulator's window has expired, closes it and enqueues the completed interval.
     */
    public void closeExpiredAccumulatorIfNeeded(long epochMillis) {
        synchronized (accumulatorLock) {
            if (currentIntervalAccumulator != null && currentIntervalAccumulator.getIntervalEnd() <= epochMillis) {
                if (log.isDebugEnabled()) {
                    log.debug("Closing previous interval and creating new one. MetricId={}, interval.start={}, average={}",
                            metricId, currentIntervalAccumulator.getIntervalStart(), currentIntervalAccumulator.computeAvg());
                }
                closeAccumulatorAndOpenNewOne();
            }
        }
    }

    /**
     * Closes any accumulator whose window has expired, moving it into the completed-intervals queue so it is ready for the service layer to export and persist.
     */
    @Override
    public void closeCompletedIntervals() {
        try {
            closeExpiredAccumulatorIfNeeded(System.currentTimeMillis());
        } catch (Exception ex) {
            log.warn("Trouble closing previous interval for MetricId=" + metricId, ex);
            currentIntervalAccumulator = createAccumulator(AbstractStatsAccumulator.calculateIntervalStart(System.currentTimeMillis()));
        }
    }

    /**
     * Drains all completed intervals from this metric's queue.
     */
    @Override
    public List<ISymIntervalStats> exportCompletedIntervals() {
        return completedIntervals.exportAll();
    }

    /**
     * Initializes the sliding workset with provided historical intervals (typically loaded from the database), bypassing outlier detection so the IQR window is
     * ready from the first live interval. Enables the outlier detection logic. Should be called once, immediately after the metric is created.
     */
    public void seedWorkset(List<ISymIntervalStats> history) {
        if (history == null || history.isEmpty()) {
            log.debug("No data to kick-off outlier detection with historical intervals for metric={}", metricId);
            return;
        }
        workset.seed(history);
        log.debug("Primed outlier detection with {} historical intervals for metric={}", history.size(), metricId);
    }

    /**
     * Runs outlier detection on the closed interval, tags it if needed, then enqueues it in {@link #completedIntervals} and updates the sliding workset.
     */
    private void addToCompletedIntervals(ISymIntervalStats interval) {
        boolean isOutlier = workset.detectOutlier(interval);
        ISymIntervalStats tagged = isOutlier ? interval.cloneOutlier(true) : interval;
        completedIntervals.add(tagged);
        workset.add(tagged);
    }
}
