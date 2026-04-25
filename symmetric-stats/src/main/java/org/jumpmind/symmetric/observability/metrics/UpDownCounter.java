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

import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

/**
 * Tracks a long value that can increase or decrease. Accepts positive and negative deltas. The OTel SDK reads the current value via a callback (registered in
 * {@link AbstractMetricsService}) rather than receiving a push on every {@link #add} call, so no OTel work happens on the instrumented thread. The
 * {@link ObservableLongUpDownCounter} handle is held here so it can be closed (unregistering the callback) during service shutdown.
 */
public class UpDownCounter extends AbstractCounterMetric implements IUpDownCounter {
    private ObservableLongUpDownCounter otelHandle;

    UpDownCounter(String metricId, Attributes attributes, List<MetricAttribute> metricAttributes) {
        super(metricId, attributes, metricAttributes);
    }

    void setOtelHandle(ObservableLongUpDownCounter handle) {
        this.otelHandle = handle;
    }

    @Override
    public void close() {
        if (otelHandle != null) {
            try {
                otelHandle.close();
            } catch (Exception e) {
                log.warn("Failed to close OTel handle for {}", getMetricId(), e);
            }
        }
        super.close();
    }

    /**
     * Decrements the current value in an atomic operation and records time of change in a new observation.
     */
    public void decrement() {
        add(-1);
    }
}
