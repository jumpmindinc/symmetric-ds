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

import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricDefinition;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.MetricConfigurationException;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;

/**
 * Tracks a double value. The OTel SDK reads the current value via a callback (registered in {@link AbstractMetricsService}) rather than receiving a push on
 * every {@link #setValue} or {@link #add} call, so no OTel work happens on the instrumented thread. The {@link ObservableDoubleGauge} handle is held here so it
 * can be closed (unregistering the callback) during service shutdown.
 */
public class SymDoubleGauge extends AbstractDoubleGaugeMetric implements ISymDoubleGauge {
    SymDoubleGauge(ISymMetricDefinition definition, Attributes attributes, MetricAttributeList metricAttributes) {
        super(definition, attributes, metricAttributes);
    }

    @Override
    public synchronized void open(AutoCloseable handle) {
        if (handle != null && !(handle instanceof ObservableDoubleGauge)) {
            String message = String.format("Expected ObservableDoubleGauge, got %s", handle.getClass().getName());
            throw new MetricConfigurationException(message);
        }
        super.open(handle);
    }
}
