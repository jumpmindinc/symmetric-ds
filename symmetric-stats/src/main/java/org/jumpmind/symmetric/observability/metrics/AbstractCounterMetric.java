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

import java.util.concurrent.atomic.AtomicLong;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricDefinition;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.models.ObservationLong;
import org.jumpmind.symmetric.observability.stats.Int64StatsAccumulator;

import io.opentelemetry.api.common.Attributes;

/**
 * Tracks a current value of long type.
 */
public abstract class AbstractCounterMetric extends AbstractQueuedMetric {
    protected final AtomicLong currentValue = new AtomicLong(0);

    AbstractCounterMetric(ISymMetricDefinition definition, Attributes attributes, MetricAttributeList metricAttributes, InstrumentType instrumentType) {
        super(definition, attributes, metricAttributes, MetricFactType.INT64, instrumentType);
    }

    @Override
    public IStatsAccumulator createAccumulator(long intervalStart) {
        return new Int64StatsAccumulator(intervalStart);
    }

    /**
     * Returns the current value of the counter (can change quickly in a highly concurrent environment)
     */
    public long getValue() {
        return currentValue.get();
    }

    /**
     * Adds to the current value in an atomic operation and records time of change in a new observation
     */
    public void add(long delta) {
        if (delta == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        this.lastModified = now;
        addObservation(new ObservationLong(this.currentValue.addAndGet(delta), now));
    }

    /**
     * Increments the current value in an atomic operation and records time of change in a new observation
     */
    public void increment() {
        add(1);
    }
}
