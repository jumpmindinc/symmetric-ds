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

import org.jumpmind.symmetric.observability.interfaces.IIncreasingCounter;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongCounter;

/**
 * Monotonically increasing counter. Rejects negative deltas with {@link IllegalArgumentException}. The OTel SDK pulls the cumulative total via callback; call
 * {@link #close()} on shutdown to unregister it.
 */
public class IncreasingCounter extends AbstractCounterMetric implements IIncreasingCounter {
    private ObservableLongCounter otelHandle;

    IncreasingCounter(String metricId, Attributes attributes, List<MetricAttribute> metricAttributes) {
        super(metricId, attributes, metricAttributes, InstrumentType.COUNTER);
    }

    void setOtelHandle(ObservableLongCounter handle) {
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

    @Override
    public void add(long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("IncreasingCounter does not accept negative deltas: " + delta);
        }
        super.add(delta);
    }
}
