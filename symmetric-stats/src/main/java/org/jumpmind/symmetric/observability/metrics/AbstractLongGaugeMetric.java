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
import java.util.concurrent.atomic.LongAdder;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.IStatsAccumulator;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricDefinition;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.models.ObservationLong;
import org.jumpmind.symmetric.observability.stats.Int64StatsAccumulator;

import io.opentelemetry.api.common.Attributes;

public abstract class AbstractLongGaugeMetric extends AbstractQueuedMetric {
    protected final LongAdder currentValue = new LongAdder();

    AbstractLongGaugeMetric(ISymMetricDefinition definition, Attributes attributes, List<MetricAttribute> metricAttributes) {
        super(definition, attributes, metricAttributes, MetricFactType.INT64, InstrumentType.LONG_GAUGE);
    }

    @Override
    public IStatsAccumulator createAccumulator(long intervalStart) {
        return new Int64StatsAccumulator(intervalStart);
    }

    public long getValue() {
        return this.currentValue.sum();
    }

    public void setValue(long newValue) {
        this.currentValue.reset();
        this.currentValue.add(newValue - this.currentValue.sum());
        long now = System.currentTimeMillis();
        this.lastModified = now;
        addObservation(new ObservationLong(this.currentValue.sum(), now));
    }

    public void add(long delta) {
        this.currentValue.add(delta);
        long now = System.currentTimeMillis();
        this.lastModified = now;
        addObservation(new ObservationLong(this.currentValue.sum(), now));
    }
}
