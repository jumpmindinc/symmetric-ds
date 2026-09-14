/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
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
import org.jumpmind.symmetric.observability.interfaces.ISymMetricDefinition;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.models.ObservationDouble;

import io.opentelemetry.api.common.Attributes;

public abstract class AbstractDoubleGaugeMetric extends AbstractQueuedMetric {
    protected final DoubleAdder currentValue = new DoubleAdder();

    AbstractDoubleGaugeMetric(ISymMetricDefinition definition, Attributes attributes, MetricAttributeList metricAttributes) {
        super(definition, attributes, metricAttributes, MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE);
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
        long now = System.currentTimeMillis();
        this.lastModified = now;
        addObservation(new ObservationDouble(this.currentValue.sum(), now));
    }

    /**
     * Adds to the current value in an atomic operation and records time of change in a new observation
     */
    public void add(double delta) {
        this.currentValue.add(delta);
        long now = System.currentTimeMillis();
        this.lastModified = now;
        addObservation(new ObservationDouble(this.currentValue.sum(), now));
    }
}
