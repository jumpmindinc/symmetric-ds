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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.observability.interfaces.InvalidMetricDataException;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.*;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants;
import org.jumpmind.symmetric.observability.models.MetricContext;
import org.jumpmind.symmetric.observability.metrics.SymMetricDefinition.InstrumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry that maps metric IDs to their {@link SymMetricDefinition}. Pre-populated with default engine metrics at construction time; additional metrics may be
 * registered via {@link #register}. Call {@link #initializeMetrics} to materialize all registered definitions on a metrics service instance.
 */
public class MetricDefinitionFactory implements IMetricDefinitionFactory {
    private static final Logger log = LoggerFactory.getLogger(MetricDefinitionFactory.class);
    public static final long ATTR_DEFAULT_IDS_END = 19999999999L;
    private final Map<String, SymMetricDefinition> registry = new LinkedHashMap<>();
    private final List<ContextDefinition> defaultContexts = new ArrayList<>(List.of(
            new ContextDefinition(MetricContext.UNDEFINED, List.of(new MetricAttribute("UNDEFINED", "UNDEFINED"))), // sentinel for attribute-less metrics
            new ContextDefinition(10000102009L, List.of(new MetricAttribute(CHANNEL, "default"))), // SymmetricDS default channels
            new ContextDefinition(10000112009L, List.of(new MetricAttribute(CHANNEL, "config"))),
            new ContextDefinition(10000122009L, List.of(new MetricAttribute(CHANNEL, "system"))),
            new ContextDefinition(10000132009L, List.of(new MetricAttribute(CHANNEL, "reload"))),
            new ContextDefinition(10000142009L, List.of(new MetricAttribute(CHANNEL, "heartbeat"))),
            new ContextDefinition(10000152009L, List.of(new MetricAttribute(CHANNEL, "monitor"))),
            new ContextDefinition(10000162009L, List.of(new MetricAttribute(CHANNEL, "dynamic"))),
            new ContextDefinition(10000172009L, List.of(new MetricAttribute(CHANNEL, "filesync"))),
            new ContextDefinition(10000182009L, List.of(new MetricAttribute(CHANNEL, "filesync_reload"))),
            new ContextDefinition(12000102020L, List.of(new MetricAttribute(CHANNEL, "business_unit"))), // JMC channels
            new ContextDefinition(12000112020L, List.of(new MetricAttribute(CHANNEL, "carrier"))),
            new ContextDefinition(12000122020L, List.of(new MetricAttribute(CHANNEL, "ctx"))),
            new ContextDefinition(12000132020L, List.of(new MetricAttribute(CHANNEL, "cust"))),
            new ContextDefinition(12000142020L, List.of(new MetricAttribute(CHANNEL, "device"))),
            new ContextDefinition(12000152020L, List.of(new MetricAttribute(CHANNEL, "foundation"))),
            new ContextDefinition(12000162020L, List.of(new MetricAttribute(CHANNEL, "i18n"))),
            new ContextDefinition(12000172020L, List.of(new MetricAttribute(CHANNEL, "itm"))),
            new ContextDefinition(12000182020L, List.of(new MetricAttribute(CHANNEL, "ops"))),
            new ContextDefinition(12000192020L, List.of(new MetricAttribute(CHANNEL, "prc"))),
            new ContextDefinition(12000202020L, List.of(new MetricAttribute(CHANNEL, "prm"))),
            new ContextDefinition(12000212020L, List.of(new MetricAttribute(CHANNEL, "pub"))),
            new ContextDefinition(12000222020L, List.of(new MetricAttribute(CHANNEL, "sls"))),
            new ContextDefinition(12000232020L, List.of(new MetricAttribute(CHANNEL, "sls_from_corp"))),
            new ContextDefinition(12000242020L, List.of(new MetricAttribute(CHANNEL, "tax"))),
            new ContextDefinition(12000252020L, List.of(new MetricAttribute(CHANNEL, "tng"))),
            new ContextDefinition(12000262020L, List.of(new MetricAttribute(CHANNEL, "usr"))),
            new ContextDefinition(10200102009L, List.of(new MetricAttribute(NODE_GROUP, "central"))), // Common node groups
            new ContextDefinition(10200112009L, List.of(new MetricAttribute(NODE_GROUP, "server"))),
            new ContextDefinition(10200122009L, List.of(new MetricAttribute(NODE_GROUP, "source"))),
            new ContextDefinition(10200132009L, List.of(new MetricAttribute(NODE_GROUP, "corp"))),
            new ContextDefinition(10200142009L, List.of(new MetricAttribute(NODE_GROUP, "master"))),
            new ContextDefinition(10200152009L, List.of(new MetricAttribute(NODE_GROUP, "main"))),
            new ContextDefinition(10200162009L, List.of(new MetricAttribute(NODE_GROUP, "cloud"))),
            new ContextDefinition(10200172009L, List.of(new MetricAttribute(NODE_GROUP, "replica"))),
            new ContextDefinition(10200182009L, List.of(new MetricAttribute(NODE_GROUP, "client"))),
            new ContextDefinition(10200192009L, List.of(new MetricAttribute(NODE_GROUP, "target"))),
            new ContextDefinition(10200202009L, List.of(new MetricAttribute(NODE_GROUP, "device"))),
            new ContextDefinition(10200212009L, List.of(new MetricAttribute(NODE_GROUP, "fixed"))),
            new ContextDefinition(10200222009L, List.of(new MetricAttribute(NODE_GROUP, "businessunit"))),
            new ContextDefinition(10200232009L, List.of(new MetricAttribute(NODE_GROUP, "store"))),
            new ContextDefinition(10200242009L, List.of(new MetricAttribute(NODE_GROUP, "pos"))),
            new ContextDefinition(10200252009L, List.of(new MetricAttribute(NODE_GROUP, "test"))),
            new ContextDefinition(10200262009L, List.of(new MetricAttribute(NODE_GROUP, "lab"))),
            new ContextDefinition(10200272009L, List.of(new MetricAttribute(NODE_GROUP, "isp"))),
            new ContextDefinition(10200282009L, List.of(new MetricAttribute(NODE_GROUP, "ship"))),
            new ContextDefinition(10200292009L, List.of(new MetricAttribute(NODE_GROUP, "shore"))),
            new ContextDefinition(10200302009L, List.of(new MetricAttribute(NODE_GROUP, "warehouse"))),
            new ContextDefinition(10200312009L, List.of(new MetricAttribute(NODE_GROUP, "hub"))),
            new ContextDefinition(10200322009L, List.of(new MetricAttribute(NODE_GROUP, "enterprise_hub"))),
            new ContextDefinition(10200332009L, List.of(new MetricAttribute(NODE_GROUP, "rig"))),
            new ContextDefinition(10200342009L, List.of(new MetricAttribute(NODE_GROUP, "iot"))),
            new ContextDefinition(10200352009L, List.of(new MetricAttribute(NODE_GROUP, "clinic"))),
            new ContextDefinition(10200362009L, List.of(new MetricAttribute(NODE_GROUP, "azure"))),
            new ContextDefinition(10200382009L, List.of(new MetricAttribute(NODE_GROUP, "bigquery"))),
            new ContextDefinition(10200392009L, List.of(new MetricAttribute(NODE_GROUP, "aws"))),
            new ContextDefinition(10300102009L, List.of(new MetricAttribute(HTTP_METHOD, "GET"))), // HTTP methods
            new ContextDefinition(10300112009L, List.of(new MetricAttribute(HTTP_METHOD, "POST"))),
            new ContextDefinition(10300122009L, List.of(new MetricAttribute(HTTP_METHOD, "PUT"))),
            new ContextDefinition(10300132009L, List.of(new MetricAttribute(HTTP_METHOD, "DELETE"))),
            new ContextDefinition(10300142009L, List.of(new MetricAttribute(HTTP_METHOD, "PATCH"))),
            new ContextDefinition(10300152009L, List.of(new MetricAttribute(HTTP_METHOD, "HEAD"))),
            new ContextDefinition(10300162009L, List.of(new MetricAttribute(HTTP_METHOD, "OPTIONS"))),
            new ContextDefinition(10300172009L, List.of(new MetricAttribute(HTTP_METHOD, "TRACE"))),
            new ContextDefinition(10300182009L, List.of(new MetricAttribute(HTTP_METHOD, "CONNECT"))),
            new ContextDefinition(10400102009L, List.of(new MetricAttribute(JOB, "compare"))), // SymmetricDS built-in jobs
            new ContextDefinition(10400112009L, List.of(new MetricAttribute(JOB, "data_refresh"))),
            new ContextDefinition(10400122009L, List.of(new MetricAttribute(JOB, "file_sync_pull"))),
            new ContextDefinition(10400132009L, List.of(new MetricAttribute(JOB, "file_sync_push"))),
            new ContextDefinition(10400142009L, List.of(new MetricAttribute(JOB, "file_sync_tracker"))),
            new ContextDefinition(10400152009L, List.of(new MetricAttribute(JOB, "heartbeat"))),
            new ContextDefinition(10400162009L, List.of(new MetricAttribute(JOB, "initial_load_extract"))),
            new ContextDefinition(10400172009L, List.of(new MetricAttribute(JOB, "initial_load_queue"))),
            new ContextDefinition(10400182009L, List.of(new MetricAttribute(JOB, "log_miner"))),
            new ContextDefinition(10400192009L, List.of(new MetricAttribute(JOB, "monitor"))),
            new ContextDefinition(10400202009L, List.of(new MetricAttribute(JOB, "offline_pull"))),
            new ContextDefinition(10400212009L, List.of(new MetricAttribute(JOB, "offline_push"))),
            new ContextDefinition(10400222009L, List.of(new MetricAttribute(JOB, "pull"))),
            new ContextDefinition(10400232009L, List.of(new MetricAttribute(JOB, "purge_data_gaps"))),
            new ContextDefinition(10400242009L, List.of(new MetricAttribute(JOB, "purge_incoming"))),
            new ContextDefinition(10400252009L, List.of(new MetricAttribute(JOB, "purge_outgoing"))),
            new ContextDefinition(10400262009L, List.of(new MetricAttribute(JOB, "purge_statistics"))),
            new ContextDefinition(10400272009L, List.of(new MetricAttribute(JOB, "push"))),
            new ContextDefinition(10400282009L, List.of(new MetricAttribute(JOB, "refresh_analytics"))),
            new ContextDefinition(10400292009L, List.of(new MetricAttribute(JOB, "refresh_cache"))),
            new ContextDefinition(10400302009L, List.of(new MetricAttribute(JOB, "report_status"))),
            new ContextDefinition(10400312009L, List.of(new MetricAttribute(JOB, "routing"))),
            new ContextDefinition(10400322009L, List.of(new MetricAttribute(JOB, "stage_management"))),
            new ContextDefinition(10400332009L, List.of(new MetricAttribute(JOB, "stat_flush"))),
            new ContextDefinition(10400342009L, List.of(new MetricAttribute(JOB, "sync_config"))),
            new ContextDefinition(10400352009L, List.of(new MetricAttribute(JOB, "sync_triggers"))),
            new ContextDefinition(10400362009L, List.of(new MetricAttribute(JOB, "watchdog"))),
            new ContextDefinition(10400372009L, List.of(new MetricAttribute(JOB, "custom_bsh"))),
            new ContextDefinition(10400382009L, List.of(new MetricAttribute(JOB, "custom_java"))),
            new ContextDefinition(10400392009L, List.of(new MetricAttribute(JOB, "custom_sql"))),
            new ContextDefinition(ATTR_DEFAULT_IDS_END, List.of(new MetricAttribute("system", "specific")))));

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

    public void registerDefaultContext(ContextDefinition... definitions) {
        if (definitions == null || definitions.length == 0) {
            return;
        }
        for (ContextDefinition def : definitions) {
            if (def != null) {
                defaultContexts.add(def);
            }
        }
    }

    public List<ContextDefinition> getDefaultContexts() {
        return Collections.unmodifiableList(defaultContexts);
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
