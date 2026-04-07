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

import org.jumpmind.symmetric.observability.models.ISymObservation;
import org.jumpmind.symmetric.observability.models.ObservationLong;

import io.opentelemetry.api.common.Attributes;

/**
 * Maintains internal (temporary and size-limited) collection of observations, until they are removed, processed and/or stored somewhere else. All observations
 * should be the for the same metric, i.e. same context (node, server, etc.) and attributes.
 */
public abstract class AbstractQueuedMetric implements ISymMetric {
    private final String metricId;
    protected final Attributes attributes;
    protected volatile long lastModified;
    protected ObservationsQueue<ISymObservation> observations = new ObservationsQueue<ISymObservation>();

    AbstractQueuedMetric(String metricId, Attributes attributes) {
        this.metricId = metricId;
        this.attributes = attributes;
    }

    @Override
    public String getMetricId() {
        return metricId;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Returns estimated number of recorded observations (can change quickly in a highly concurrent environment)
     */
    public int getObservationsCountEstimate() {
        return this.observations.size();
    }

    /**
     * Add new observation to an internal collection.
     */
    public void addObservation(ISymObservation observation) {
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
}
