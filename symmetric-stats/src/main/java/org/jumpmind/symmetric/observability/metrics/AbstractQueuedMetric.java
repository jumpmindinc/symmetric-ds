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

import org.jumpmind.symmetric.observability.models.MetricFactType;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.jumpmind.symmetric.observability.models.ObservationLong;
import org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.interfaces.ISymObservation;
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
    protected final Attributes attributes;
    protected volatile long lastModified;
    protected volatile boolean isMetricClosed = false;
    protected ObservationsQueue<ISymObservation> observations = new ObservationsQueue<ISymObservation>();
    protected IStatsAccumulator currentIntervalAccumulator;
    protected MetricIntervalStatsQueue completedIntervals = new MetricIntervalStatsQueue();
    protected MetricSeriesSlidingWorkset workset = new MetricSeriesSlidingWorkset();

    AbstractQueuedMetric(String metricId, Attributes attributes) {
        this.metricId = metricId;
        this.attributes = attributes;
        this.currentIntervalAccumulator = null;
    }

    @Override
    public String getMetricId() {
        return metricId;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    /** Returns the fact type for this metric, used to select the correct accumulator and persistence table. */
    public abstract MetricFactType getFactType();

    /**
     * Returns estimated number of recorded observations (can change quickly in a highly concurrent environment)
     */
    public int getObservationsCountEstimate() {
        return this.observations.size();
    }

    @Override
    public void close() {
        isMetricClosed = true;
    }

    @Override
    public boolean isClosed() {
        return isMetricClosed;
    }

    @Override
    public IStatsAccumulator createAccumulator(long intervalStart) {
        return new Float64StatsAccumulator(intervalStart);
    }

    /**
     * Add new observation to an internal collection. Silently ignored when the metric is closed.
     */
    public void addObservation(ISymObservation observation) {
        if (isMetricClosed) {
            return;
        }
        observations.add(observation);
    }

    /**
     * Atomic operation of retrieving the old queue object and replacing it with new empty queue.
     */
    protected synchronized ObservationsQueue<ISymObservation> retrieveAndSwapForNewQueue() {
        ObservationsQueue<ISymObservation> oldObservations = this.observations;
        this.observations = new ObservationsQueue<ISymObservation>();
        return oldObservations;
    }

    /**
     * Returns all currently available observations (which can change quickly in a highly concurrent environment) and removes them from an internal queue
     */
    public ISymObservation[] removeAllObservations() {
        if (this.observations.size() < 1) {
            return new ISymObservation[] {};
        }
        ObservationsQueue<ISymObservation> oldObservations = retrieveAndSwapForNewQueue();
        ObservationLong[] removedObservations = (ObservationLong[]) oldObservations.toArray();
        oldObservations.clear();
        return removedObservations;
    }

    /**
     * Assigns each observation to the current accumulator, rolling over to a new window whenever an observation falls into a later bucket. Completed intervals
     * are enqueued into completedIntervals.
     */
    public int processObservations(ISymObservation[] obs) {
        int processedCount = 0;
        for (ISymObservation observation : obs) {
            processedCount += processObservation(observation);
        }
        log.debug("Processed {} observations. MetricId={}", processedCount, getMetricId());
        return processedCount;
    }

    public int processObservation(ISymObservation observation) {
        long bucketStart = AbstractStatsAccumulator.calculateIntervalStart(observation.getTimestamp());
        if (currentIntervalAccumulator == null) {
            currentIntervalAccumulator = createAccumulator(bucketStart);
        }
        if (currentIntervalAccumulator.isInScope(bucketStart)) {
            currentIntervalAccumulator.addObservation(observation);
            return 1;
        }
        if (bucketStart < currentIntervalAccumulator.getIntervalStart()) {
            log.debug("Throwing away delinquent observation. timestamp={}, MetricId={}, current.interval.start={}",
                    observation.getTimestamp(), getMetricId(), currentIntervalAccumulator.getIntervalStart());
            return 0;
        }
        // bucketStart is in the "future" — close current window and carry-forward until we reach bucketStart.
        // createNext() preserves native precision (long for counters, double for gauges) without any type casting in the caller.
        log.debug(
                "Closing previous interval and creating new one for observation. timestamp={}, MetricId={}, current.interval.start={}",
                observation.getTimestamp(), getMetricId(), currentIntervalAccumulator.getIntervalStart());
        while (!currentIntervalAccumulator.isInScope(bucketStart)) {
            long nextIntervalStart = currentIntervalAccumulator.getIntervalEnd();
            currentIntervalAccumulator.close();
            addToCompletedIntervals(currentIntervalAccumulator.toStats());
            currentIntervalAccumulator = currentIntervalAccumulator.createNext(nextIntervalStart);
        }
        currentIntervalAccumulator.addObservation(observation);
        return 1;
    }

    /**
     * If the current accumulator's window has expired, closes it and enqueues the completed interval.
     */
    public void closeExpiredAccumulatorIfNeeded(long nowMs) {
        if (currentIntervalAccumulator != null && currentIntervalAccumulator.getIntervalEnd() <= nowMs) {
            currentIntervalAccumulator.close();
            addToCompletedIntervals(currentIntervalAccumulator.toStats());
            currentIntervalAccumulator = null;
        }
    }

    /**
     * Closes any accumulator whose window has expired, moving it into the completed-intervals queue so it is ready for the service layer to export and persist.
     */
    @Override
    public void closeCompletedIntervals() {
        closeExpiredAccumulatorIfNeeded(System.currentTimeMillis());
    }

    /**
     * Drains all completed intervals from this metric's queue.
     */
    public List<ISymIntervalStats> exportCompletedIntervals(MetricKey key) {
        return completedIntervals.exportAll();
    }

    /**
     * Initializes the sliding workset with provided historical intervals (typically loaded from the database), bypassing outlier detection so the IQR window is
     * ready from the first live interval. Enables the outlier detection logic. Should be called once, immediately after the metric is created.
     */
    public void seedWorkset(List<ISymIntervalStats> history) {
        if (history == null || history.isEmpty()) {
            log.warn("No data to kick-off outlier detection with historical intervals for metric={}", metricId);
            return;
        }
        int intervalsCount = history.size();
        if (intervalsCount < MetricSeriesSlidingWorkset.IQR_INTERVALS_MIN) {
            log.warn("Insufficient data to kick-off outlier detection with historical intervals for metric={}, records={}", metricId, intervalsCount);
        }
        for (ISymIntervalStats interval : history) {
            workset.seed(interval);
        }
        log.info("Primed outlier detection with {} historical intervals for metric={}", intervalsCount, metricId);
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
