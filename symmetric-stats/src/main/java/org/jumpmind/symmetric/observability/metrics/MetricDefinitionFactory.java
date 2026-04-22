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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.observability.interfaces.InvalidMetricDataException;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants;
import org.jumpmind.symmetric.observability.metrics.SymMetricDefinition.InstrumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry that maps metric IDs to their {@link SymMetricDefinition}. Pre-populated with default engine metrics at construction time; additional metrics may be
 * registered via {@link #register}. Call {@link #initializeMetrics} to materialize all registered definitions on a metrics service instance.
 */
public class MetricDefinitionFactory implements IMetricDefinitionFactory {
    private static final Logger log = LoggerFactory.getLogger(MetricDefinitionFactory.class);
    private final Map<String, SymMetricDefinition> registry = new LinkedHashMap<>();

    MetricDefinitionFactory() {
        register(new SymMetricDefinition(
                SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS,
                "Active connection reservations to this server from other nodes",
                SymMetricConstants.METRIC_UNIT_CONNECTIONS,
                InstrumentType.COUNTER));
        register(new SymMetricDefinition(
                SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_UTILIZATION,
                "Active connection as a percentage of max concurrent workers",
                SymMetricConstants.METRIC_UNIT_PERCENT,
                InstrumentType.GAUGE));
    }

    public void register(SymMetricDefinition definition) {
         if (definition == null) {
            String message = String.format("Metric definition cannot be null!");
            log.error(message);
            throw new InvalidMetricDataException(message);
        }
        registry.put(definition.id(), definition);
    }

    public SymMetricDefinition getDefinition(String metricId) {
        SymMetricDefinition def = registry.get(metricId);
        if (def == null) {
            String message = String.format("No metric definition found for id=%s. Register it before use.", metricId);
            log.error(message);
            throw new InvalidMetricDataException(message);
        }
        return def;
    }

    /**
     * Materializes all registered definitions on {@code service}. Returns the number of metrics successfully initialized.
     */
    public int initializeMetrics(AbstractMetricsService service) {
        int count = 0;
        for (SymMetricDefinition def : registry.values()) {
            if (StringUtils.isBlank(def.id())) {
                log.warn("Skipping metric definition with a blank id! {}", def);
                continue;
            }
            try {
                switch (def.type()) {
                    case COUNTER -> service.registerUpDownCounter(def);
                    case GAUGE -> service.registerGauge(def);
                    default -> {
                        log.warn("Unsupported instrument type {} for metric={}, skipping", def.type(), def.id());
                        continue;
                    }
                }
                count++;
            } catch (Exception e) {
                log.warn("Failed to initialize metric from definition. Id=" + def.id(), e);
            }
        }
        return count;
    }
}
