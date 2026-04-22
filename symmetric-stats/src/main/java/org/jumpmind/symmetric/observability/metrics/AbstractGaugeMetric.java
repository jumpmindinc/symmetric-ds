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

import java.util.concurrent.atomic.DoubleAdder;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.models.ObservationDouble;

import io.opentelemetry.api.common.Attributes;

public abstract class AbstractGaugeMetric extends AbstractQueuedMetric {
    protected final DoubleAdder currentValue = new DoubleAdder();

    AbstractGaugeMetric(String metricId, Attributes attributes) {
        super(metricId, attributes);
    }

    @Override
    public MetricFactType getFactType() {
        return MetricFactType.FLOAT64;
    }

    public double getValue() {
        return this.currentValue.sum();
    }

    /**
     * Sets new value in an atomic operation and records time of change in a new observation
     */
    public void setValue(double newValue) {
        this.currentValue.reset();
        this.currentValue.add(newValue - this.currentValue.sum());
        addObservation(new ObservationDouble(this.currentValue.sum(), this.lastModified = System.currentTimeMillis()));
    }

    /**
     * Adds to the current value in an atomic operation and records time of change in a new observation
     */
    public void add(double delta) {
        this.currentValue.add(delta);
        // Update current value via atomic operation and record observation:
        addObservation(new ObservationDouble(this.currentValue.sum(), this.lastModified = System.currentTimeMillis()));
    }
}
