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

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.observability.metrics.SymMetricDefinition.InstrumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Declares the standard set of engine-scoped metrics. Add new default metrics to {@link #METRICS}.
 */
public final class DefaultEngineMetrics {
    private static final Logger log = LoggerFactory.getLogger(DefaultEngineMetrics.class);

    static final SymMetricDefinition[] DEFAULT_METRICS = {
        new SymMetricDefinition(
                SymMetricConstants.METRIC_CONNECTIONS_RESERVATIONS_ID,
                SymMetricConstants.METRIC_CONNECTIONS_RESERVATIONS_DESC,
                SymMetricConstants.METRIC_CONNECTIONS_RESERVATIONS_UNIT,
                InstrumentType.COUNTER),
    };

    private DefaultEngineMetrics() {}

    /**
     * Registers all entries in {@link #METRICS} on {@code service}. Returns the number of metrics successfully initialized.
     */
    public static int initializeDefaultMetrics(AbstractMetricsService service) {
        int count = 0;
        for (SymMetricDefinition def : DEFAULT_METRICS) {
            if (StringUtils.isBlank(def.id())) {
                log.warn("Skipping metric definition with a blank id! {}", def);
                continue;
            }
            try {
                switch (def.type()) {
                    case COUNTER -> service.getOrCreateUpDownCounter(def.id(), def.description(), def.unit());
                    case GAUGE -> service.getOrCreateGauge(def.id(), def.description(), def.unit());
                    default -> {
                        log.warn("Unsupported instrument type {} for metric={}, skipping", def.type(), def.id());
                        continue;
                    }
                }
                count++;
            } catch (Exception e) {
                log.warn("Failed to initialize metric from definition. Id="+ def.id(), e);
            }
        }
        return count;
    }
}
