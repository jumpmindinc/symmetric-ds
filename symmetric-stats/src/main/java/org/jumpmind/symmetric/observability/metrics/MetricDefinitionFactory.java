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

import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.CHANNEL;
import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.HTTP_METHOD;
import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.JOB;
import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.NODE_GROUP;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_BATCHES_INCOMING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_BATCHES_OUTGOING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_CREATE_TIME_MAX;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_CREATE_TIME_MIN;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_EVENTS_INSERTED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_EXTRACTED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_EXTRACTED_BYTES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_EXTRACTED_ERRORS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_GAP_COUNT;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_INCOMING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_LOADED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_LOADED_BYTES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_LOADED_ERRORS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_LOADED_OUTGOING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_LOADED_OUTGOING_BYTES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_LOADED_OUTGOING_ERRORS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_OUTGOING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_RECEIVED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_RECEIVED_BYTES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_ROUTED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_SENT;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_SENT_BYTES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_SENT_ERRORS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_UNROUTED_CHANNEL;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_UNROUTED_TOTAL;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_ENGINE_RESTARTS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_NODES_DISABLED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_NODES_LOADED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_NODES_PULLED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_NODES_PULLED_TIME;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_NODES_PUSHED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_NODES_PUSHED_TIME;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_NODES_REGISTERED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_NODES_REJECTED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_PURGE_BATCH_INCOMING_ROWS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_PURGE_BATCH_OUTGOING_ROWS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_PURGE_DATA_EVENT_ROWS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_PURGE_DATA_ROWS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_PURGE_EXPIRED_DATA_ROWS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_PURGE_STRANDED_DATA_EVENT_ROWS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_PURGE_STRANDED_DATA_ROWS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_RUNTIME_DBPOOL_ACTIVE;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_RUNTIME_DBPOOL_IDLE;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_RUNTIME_DBPOOL_UTILIZATION;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_UTILIZATION;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_TRIGGERS_CREATED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_TRIGGERS_REBUILT;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_TRIGGERS_REMOVED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_UNIT_BATCHES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_UNIT_BYTES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_UNIT_CONNECTIONS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_UNIT_MILLIS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_UNIT_NODES;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_UNIT_PERCENT;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_UNIT_ROWS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_UNIT_TRIGGERS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.InvalidMetricDataException;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.models.MetricContext;
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
    private final Set<String> channelScopedMetricIds = new LinkedHashSet<>(List.of(
            METRIC_ID_DATA_ROUTED, METRIC_ID_DATA_EXTRACTED, METRIC_ID_DATA_EXTRACTED_BYTES,
            METRIC_ID_DATA_EXTRACTED_ERRORS, METRIC_ID_DATA_EVENTS_INSERTED,
            METRIC_ID_DATA_SENT, METRIC_ID_DATA_SENT_BYTES, METRIC_ID_DATA_SENT_ERRORS,
            METRIC_ID_DATA_RECEIVED, METRIC_ID_DATA_RECEIVED_BYTES,
            METRIC_ID_DATA_LOADED, METRIC_ID_DATA_LOADED_BYTES, METRIC_ID_DATA_LOADED_ERRORS,
            METRIC_ID_DATA_LOADED_OUTGOING, METRIC_ID_DATA_LOADED_OUTGOING_BYTES,
            METRIC_ID_DATA_LOADED_OUTGOING_ERRORS,
            METRIC_ID_DATA_UNROUTED_CHANNEL, METRIC_ID_DATA_CREATE_TIME_MIN, METRIC_ID_DATA_CREATE_TIME_MAX));
    private final Set<String> nodeScopedMetricIds = new LinkedHashSet<>(List.of(
            METRIC_ID_BATCHES_OUTGOING, METRIC_ID_DATA_OUTGOING,
            METRIC_ID_BATCHES_INCOMING, METRIC_ID_DATA_INCOMING));
    private final List<SymMetricDefinition> defaultMetrics = new ArrayList<>(List.of(
            // Server connection metrics
            new SymMetricDefinition(METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS, "Active connection reservations to this server from other nodes",
                    METRIC_UNIT_CONNECTIONS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_SERVER_CONNECTIONS_UTILIZATION, "Active connection as a percentage of max concurrent workers",
                    METRIC_UNIT_PERCENT, InstrumentType.DOUBLE_GAUGE),
            // Node-scoped batch gauges
            new SymMetricDefinition(METRIC_ID_BATCHES_OUTGOING, "Outgoing batches per node and status", METRIC_UNIT_BATCHES,
                    InstrumentType.LONG_GAUGE),
            new SymMetricDefinition(METRIC_ID_DATA_OUTGOING, "Outgoing data rows per node and status", METRIC_UNIT_ROWS,
                    InstrumentType.LONG_GAUGE),
            new SymMetricDefinition(METRIC_ID_BATCHES_INCOMING, "Incoming batches per node and status", METRIC_UNIT_BATCHES,
                    InstrumentType.LONG_GAUGE),
            new SymMetricDefinition(METRIC_ID_DATA_INCOMING, "Incoming data rows per node and status", METRIC_UNIT_ROWS,
                    InstrumentType.LONG_GAUGE),
            // Channel-scoped data counters
            new SymMetricDefinition(METRIC_ID_DATA_ROUTED, "Data rows routed per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_EXTRACTED, "Data rows extracted per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_EXTRACTED_BYTES, "Data bytes extracted per channel", METRIC_UNIT_BYTES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_EXTRACTED_ERRORS, "Data extraction errors per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_EVENTS_INSERTED, "Data events inserted per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_SENT, "Data rows sent per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_SENT_BYTES, "Data bytes sent per channel", METRIC_UNIT_BYTES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_SENT_ERRORS, "Data send errors per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_RECEIVED, "Data rows received per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_RECEIVED_BYTES, "Data bytes received per channel", METRIC_UNIT_BYTES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_LOADED, "Data rows loaded per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_LOADED_BYTES, "Data bytes loaded per channel", METRIC_UNIT_BYTES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_LOADED_ERRORS, "Data load errors per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_LOADED_OUTGOING, "Outgoing data rows loaded per channel", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_LOADED_OUTGOING_BYTES, "Outgoing data bytes loaded per channel", METRIC_UNIT_BYTES,
                    InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_DATA_LOADED_OUTGOING_ERRORS, "Outgoing data load errors per channel", METRIC_UNIT_ROWS,
                    InstrumentType.UPDOWN_COUNTER),
            // Channel-scoped gauges
            new SymMetricDefinition(METRIC_ID_DATA_UNROUTED_CHANNEL, "Unrouted data rows per channel", METRIC_UNIT_ROWS, InstrumentType.DOUBLE_GAUGE),
            new SymMetricDefinition(METRIC_ID_DATA_CREATE_TIME_MIN, "Minimum data create time per channel (epoch ms)", METRIC_UNIT_MILLIS,
                    InstrumentType.LONG_GAUGE),
            new SymMetricDefinition(METRIC_ID_DATA_CREATE_TIME_MAX, "Maximum data create time per channel (epoch ms)", METRIC_UNIT_MILLIS,
                    InstrumentType.LONG_GAUGE),
            // Routing and CDC gauges
            new SymMetricDefinition(METRIC_ID_DATA_GAP_COUNT, "Total open data gaps", METRIC_UNIT_ROWS, InstrumentType.DOUBLE_GAUGE),
            new SymMetricDefinition(METRIC_ID_DATA_UNROUTED_TOTAL, "Total unrouted data rows", METRIC_UNIT_ROWS, InstrumentType.DOUBLE_GAUGE),
            // Runtime DB connection pool gauges
            new SymMetricDefinition(METRIC_ID_RUNTIME_DBPOOL_ACTIVE, "DB connection pool active connections", METRIC_UNIT_CONNECTIONS,
                    InstrumentType.DOUBLE_GAUGE),
            new SymMetricDefinition(METRIC_ID_RUNTIME_DBPOOL_IDLE, "DB connection pool idle connections", METRIC_UNIT_CONNECTIONS, InstrumentType.LONG_GAUGE),
            new SymMetricDefinition(METRIC_ID_RUNTIME_DBPOOL_UTILIZATION, "DB connection pool utilization as a percentage of max", METRIC_UNIT_PERCENT,
                    InstrumentType.DOUBLE_GAUGE),
            // Engine / node counters
            new SymMetricDefinition(METRIC_ID_ENGINE_RESTARTS, "Engine restart count", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_NODES_PULLED, "Nodes pulled from", METRIC_UNIT_NODES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_NODES_PUSHED, "Nodes pushed to", METRIC_UNIT_NODES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_NODES_PULLED_TIME, "Total elapsed time for node pull operations", METRIC_UNIT_MILLIS,
                    InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_NODES_PUSHED_TIME, "Total elapsed time for node push operations", METRIC_UNIT_MILLIS,
                    InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_NODES_REJECTED, "Nodes rejected", METRIC_UNIT_NODES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_NODES_REGISTERED, "Nodes registered", METRIC_UNIT_NODES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_NODES_LOADED, "Nodes loaded", METRIC_UNIT_NODES, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_NODES_DISABLED, "Nodes disabled", METRIC_UNIT_NODES, InstrumentType.UPDOWN_COUNTER),
            // Purge counters
            new SymMetricDefinition(METRIC_ID_PURGE_BATCH_INCOMING_ROWS, "Purged incoming batch rows", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_PURGE_BATCH_OUTGOING_ROWS, "Purged outgoing batch rows", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_PURGE_DATA_ROWS, "Purged data rows", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_PURGE_DATA_EVENT_ROWS, "Purged data event rows", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_PURGE_STRANDED_DATA_ROWS, "Purged stranded data rows", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_PURGE_STRANDED_DATA_EVENT_ROWS, "Purged stranded data event rows", METRIC_UNIT_ROWS,
                    InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_PURGE_EXPIRED_DATA_ROWS, "Purged expired data rows", METRIC_UNIT_ROWS, InstrumentType.UPDOWN_COUNTER),
            // Trigger counters
            new SymMetricDefinition(METRIC_ID_TRIGGERS_REMOVED, "Triggers removed", METRIC_UNIT_TRIGGERS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_TRIGGERS_REBUILT, "Triggers rebuilt", METRIC_UNIT_TRIGGERS, InstrumentType.UPDOWN_COUNTER),
            new SymMetricDefinition(METRIC_ID_TRIGGERS_CREATED, "Triggers created", METRIC_UNIT_TRIGGERS, InstrumentType.UPDOWN_COUNTER)));
    private final List<ContextDefinition> defaultContexts = new ArrayList<>(List.of(
            new ContextDefinition(MetricContext.UNDEFINED, List.of(new MetricAttribute("UNDEFINED", "UNDEFINED"))), // sentinel for attribute-less metrics
            new ContextDefinition(10000102009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_DEFAULT))), // SymmetricDS default channels
            new ContextDefinition(10000112009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_CONFIG))),
            new ContextDefinition(10000122009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_SYSTEM))),
            new ContextDefinition(10000132009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_RELOAD))),
            new ContextDefinition(10000142009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_HEARTBEAT))),
            new ContextDefinition(10000152009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_MONITOR))),
            new ContextDefinition(10000162009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_DYNAMIC))),
            new ContextDefinition(10000172009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_FILESYNC))),
            new ContextDefinition(10000182009L, List.of(new MetricAttribute(CHANNEL, Constants.CHANNEL_FILESYNC_RELOAD))),
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
        defaultMetrics.forEach(this::register);
    }

    public void registerDefaultMetric(SymMetricDefinition... definitions) {
        if (definitions == null || definitions.length == 0) {
            return;
        }
        for (SymMetricDefinition def : definitions) {
            if (def != null) {
                defaultMetrics.add(def);
                register(def);
            }
        }
    }

    public List<SymMetricDefinition> getDefaultMetrics() {
        return Collections.unmodifiableList(defaultMetrics);
    }

    @Override
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

    @Override
    public List<ContextDefinition> getDefaultContexts() {
        return Collections.unmodifiableList(defaultContexts);
    }

    @Override
    public void register(SymMetricDefinition definition) {
        if (definition == null) {
            String message = "Metric definition cannot be null!";
            log.error(message);
            throw new InvalidMetricDataException(message);
        }
        registry.put(definition.id(), definition);
    }

    @Override
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
     * Materializes all registered definitions on {@code service}. Returns the number of metrics successfully registered. Channel-scoped metrics are also
     * pre-registered for every built-in channel context to ensure consistent instrument keys at startup.
     */
    @Override
    public int initializeMetrics(AbstractMetricsService service) {
        int count = preRegisterBuiltInNonChannelMetrics(service);
        count += preRegisterBuiltInChannelMetrics(service);
        return count;
    }

    private int preRegisterBuiltInNonChannelMetrics(AbstractMetricsService service) {
        int count = 0;
        for (SymMetricDefinition def : registry.values()) {
            if (StringUtils.isBlank(def.id())) {
                log.warn("Skipping metric definition with a blank id! {}", def);
            } else if (!channelScopedMetricIds.contains(def.id()) && !nodeScopedMetricIds.contains(def.id())) {
                try {
                    switch (def.type()) {
                        case UPDOWN_COUNTER -> service.registerUpDownCounter(def);
                        case COUNTER -> service.registerIncreasingCounter(def);
                        case DOUBLE_GAUGE -> service.registerDoubleGauge(def);
                        case LONG_GAUGE -> service.registerLongGauge(def);
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
        }
        return count;
    }

    private int preRegisterBuiltInChannelMetrics(AbstractMetricsService service) {
        List<String> defaultChannels = defaultContexts.stream()
                .filter(cd -> cd.attributes().size() == 1 && CHANNEL.equals(cd.attributes().get(0).name()))
                .map(cd -> cd.attributes().get(0).value())
                .toList();
        int count = 0;
        for (String channelId : defaultChannels) {
            List<MetricAttribute> channelAttrs = List.of(new MetricAttribute(CHANNEL, channelId));
            for (String metricId : channelScopedMetricIds) {
                SymMetricDefinition def = registry.get(metricId);
                if (def != null) {
                    try {
                        switch (def.type()) {
                            case UPDOWN_COUNTER -> service.registerUpDownCounter(def, channelAttrs);
                            case COUNTER -> service.registerIncreasingCounter(def, channelAttrs);
                            case DOUBLE_GAUGE -> service.registerDoubleGauge(def, channelAttrs);
                            case LONG_GAUGE -> service.registerLongGauge(def, channelAttrs);
                            default -> {
                                continue;
                            }
                        }
                        count++;
                    } catch (Exception e) {
                        String message = String.format("Failed to pre-register channel-scoped metric %s for channel %s", metricId, channelId);
                        log.warn(message, e);
                    }
                }
            }
        }
        log.debug("Pre-registered {} channel-scoped metric instances for {} channels", count, defaultChannels.size());
        return count;
    }
}
