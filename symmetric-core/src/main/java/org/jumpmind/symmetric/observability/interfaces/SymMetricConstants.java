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
package org.jumpmind.symmetric.observability.interfaces;

/** Metric ID and instrument type constants. Referenced by callers that register or look up pre-registered metrics by ID. */
public final class SymMetricConstants {
    private SymMetricConstants() {
    }

    public enum InstrumentType {
        /** Monotonic counter — value only increases. Maps to an OTel observable counter. */
        COUNTER,
        /** Up-down counter — value can increase or decrease. Maps to an OTel observable up-down counter. */
        UPDOWN_COUNTER,
        /** Gauge tracking a floating-point value. Maps to an OTel observable double gauge. */
        DOUBLE_GAUGE,
        /** Gauge tracking a long integer value. Maps to an OTel observable long gauge. */
        LONG_GAUGE,
        /** Histogram for recording distributions. Maps to an OTel histogram. */
        HISTOGRAM
    }

    public static final String OTEL_ENV_PREFIX = "OTEL_";
    public static final String OTEL_SCOPE = "otel.scope";
    public static final String OTEL_SCOPE_DEFAULT = "symmetricds";
    public static final String OTEL_SDK_DISABLED = "otel.sdk.disabled";
    public static final String METRIC_UNIT_PERCENT = "percent";
    public static final String METRIC_UNIT_CONNECTIONS = "connections";
    public static final String METRIC_UNIT_BYTES = "bytes";
    public static final String METRIC_UNIT_MB = "megabytes";
    public static final String METRIC_UNIT_MILLIS = "milliseconds";
    public static final String METRIC_UNIT_SECONDS = "seconds";
    public static final String METRIC_UNIT_MINUTES = "minutes";
    public static final String METRIC_UNIT_HOURS = "hours";
    public static final String METRIC_UNIT_DAYS = "days";
    public static final String METRIC_UNIT_MONTHS = "months";
    public static final String METRIC_UNIT_BATCHES = "batches";
    public static final String METRIC_UNIT_ROWS = "rows";
    public static final String METRIC_UNIT_NODES = "nodes";
    public static final String METRIC_UNIT_TRIGGERS = "triggers";
    // Node-scoped gauges (OutgoingBatchService)
    public static final String METRIC_ID_BATCHES_OUTGOING = "batches.outgoing.count";
    public static final String METRIC_ID_DATA_OUTGOING = "rows.outgoing.count";
    public static final String METRIC_ID_BATCHES_INCOMING = "batches.incoming.count";
    public static final String METRIC_ID_DATA_INCOMING = "rows.incoming.count";
    // Incoming client connection counters (ConcurrentConnectionManager)
    public static final String METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS = "server.reservations.count";
    public static final String METRIC_ID_SERVER_CONNECTIONS_UTILIZATION = "server.connections.utilization";
    // Channel-scoped data counters (StatisticManager)
    public static final String METRIC_ID_DATA_ROUTED = "rows.routed.count";
    public static final String METRIC_ID_DATA_EXTRACTED = "rows.extracted.count";
    public static final String METRIC_ID_DATA_EXTRACTED_BYTES = "rows.extracted.bytes";
    public static final String METRIC_ID_DATA_EXTRACTED_ERRORS = "rows.extracted.errors";
    public static final String METRIC_ID_DATA_EVENTS_INSERTED = "rows.events.inserted.count";
    public static final String METRIC_ID_DATA_SENT = "rows.sent.count";
    public static final String METRIC_ID_DATA_SENT_BYTES = "rows.sent.bytes";
    public static final String METRIC_ID_DATA_SENT_ERRORS = "rows.sent.errors";
    public static final String METRIC_ID_DATA_RECEIVED = "rows.received.count";
    public static final String METRIC_ID_DATA_RECEIVED_BYTES = "rows.received.bytes";
    public static final String METRIC_ID_DATA_LOADED = "rows.loaded.count";
    public static final String METRIC_ID_DATA_LOADED_BYTES = "rows.loaded.bytes";
    public static final String METRIC_ID_DATA_LOADED_ERRORS = "rows.loaded.errors";
    public static final String METRIC_ID_DATA_LOADED_OUTGOING = "rows.loaded.outgoing.count";
    public static final String METRIC_ID_DATA_LOADED_OUTGOING_BYTES = "rows.loaded.outgoing.bytes";
    public static final String METRIC_ID_DATA_LOADED_OUTGOING_ERRORS = "rows.loaded.outgoing.errors";
    // Channel-scoped gauges (StatisticManager)
    public static final String METRIC_ID_DATA_UNROUTED_CHANNEL = "rows.unrouted.channel.count";
    public static final String METRIC_ID_DATA_CREATE_TIME_MIN = "rows.create.time.min";
    public static final String METRIC_ID_DATA_CREATE_TIME_MAX = "rows.create.time.max";
    // Routing and CGC gauges (StatisticManager)
    public static final String METRIC_ID_DATA_GAP_COUNT = "rows.gap.count";
    public static final String METRIC_ID_DATA_UNROUTED_COUNT = "rows.unrouted.count";
    // Database connection pool gauges (StatisticManager)
    public static final String METRIC_ID_RUNTIME_DBPOOL_ACTIVE = "db.client.connection.count";
    public static final String METRIC_ID_RUNTIME_DBPOOL_IDLE = "db.client.connection.idle.count";
    public static final String METRIC_ID_RUNTIME_DBPOOL_UTILIZATION = "db.client.connection.utilization";
    // Engine / node counters (StatisticManager)
    public static final String METRIC_ID_ENGINE_RESTARTS = "engine.restart.count";
    public static final String METRIC_ID_NODES_PULLED = "server.nodes.pulled.count";
    public static final String METRIC_ID_NODES_PUSHED = "server.nodes.pushed.count";
    public static final String METRIC_ID_NODES_PULLED_TIME = "server.nodes.pulled.time";
    public static final String METRIC_ID_NODES_PUSHED_TIME = "server.nodes.pushed.time";
    public static final String METRIC_ID_NODES_REJECTED = "server.nodes.rejected.count";
    public static final String METRIC_ID_NODES_REGISTERED = "server.nodes.registered.count";
    public static final String METRIC_ID_NODES_LOADED = "server.nodes.loaded.count";
    public static final String METRIC_ID_NODES_DISABLED = "server.nodes.disabled.count";
    // Purge counters (StatisticManager)
    public static final String METRIC_ID_PURGE_BATCH_INCOMING_ROWS = "purge.batch.incoming.rows";
    public static final String METRIC_ID_PURGE_BATCH_OUTGOING_ROWS = "purge.batch.outgoing.rows";
    public static final String METRIC_ID_PURGE_DATA_ROWS = "purge.data.rows";
    public static final String METRIC_ID_PURGE_DATA_EVENT_ROWS = "purge.data.event.rows";
    public static final String METRIC_ID_PURGE_STRANDED_DATA_ROWS = "purge.stranded.data.rows";
    public static final String METRIC_ID_PURGE_STRANDED_DATA_EVENT_ROWS = "purge.stranded.data.event.rows";
    public static final String METRIC_ID_PURGE_EXPIRED_DATA_ROWS = "purge.expired.data.rows";
    // Trigger counters (StatisticManager)
    public static final String METRIC_ID_TRIGGERS_REMOVED = "triggers.removed.count";
    public static final String METRIC_ID_TRIGGERS_REBUILT = "triggers.rebuilt.count";
    public static final String METRIC_ID_TRIGGERS_CREATED = "triggers.created.count";
    protected static final String[] DEFAULT_METRIC_IDS = {
            METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS,
            METRIC_ID_SERVER_CONNECTIONS_UTILIZATION,
            METRIC_ID_DATA_ROUTED,
            METRIC_ID_DATA_EXTRACTED,
            METRIC_ID_DATA_EXTRACTED_BYTES,
            METRIC_ID_DATA_EXTRACTED_ERRORS,
            METRIC_ID_DATA_EVENTS_INSERTED,
            METRIC_ID_DATA_SENT,
            METRIC_ID_DATA_SENT_BYTES,
            METRIC_ID_DATA_SENT_ERRORS,
            METRIC_ID_DATA_RECEIVED,
            METRIC_ID_DATA_RECEIVED_BYTES,
            METRIC_ID_DATA_LOADED,
            METRIC_ID_DATA_LOADED_BYTES,
            METRIC_ID_DATA_LOADED_ERRORS,
            METRIC_ID_DATA_LOADED_OUTGOING,
            METRIC_ID_DATA_LOADED_OUTGOING_BYTES,
            METRIC_ID_DATA_LOADED_OUTGOING_ERRORS,
            METRIC_ID_DATA_UNROUTED_CHANNEL,
            METRIC_ID_DATA_CREATE_TIME_MIN,
            METRIC_ID_DATA_CREATE_TIME_MAX,
            METRIC_ID_DATA_GAP_COUNT,
            METRIC_ID_DATA_UNROUTED_COUNT,
            METRIC_ID_RUNTIME_DBPOOL_ACTIVE,
            METRIC_ID_RUNTIME_DBPOOL_IDLE,
            METRIC_ID_RUNTIME_DBPOOL_UTILIZATION,
            METRIC_ID_ENGINE_RESTARTS,
            METRIC_ID_NODES_PULLED,
            METRIC_ID_NODES_PUSHED,
            METRIC_ID_NODES_PULLED_TIME,
            METRIC_ID_NODES_PUSHED_TIME,
            METRIC_ID_NODES_REJECTED,
            METRIC_ID_NODES_REGISTERED,
            METRIC_ID_NODES_LOADED,
            METRIC_ID_NODES_DISABLED,
            METRIC_ID_PURGE_BATCH_INCOMING_ROWS,
            METRIC_ID_PURGE_BATCH_OUTGOING_ROWS,
            METRIC_ID_PURGE_DATA_ROWS,
            METRIC_ID_PURGE_DATA_EVENT_ROWS,
            METRIC_ID_PURGE_STRANDED_DATA_ROWS,
            METRIC_ID_PURGE_STRANDED_DATA_EVENT_ROWS,
            METRIC_ID_PURGE_EXPIRED_DATA_ROWS,
            METRIC_ID_TRIGGERS_REMOVED,
            METRIC_ID_TRIGGERS_REBUILT,
            METRIC_ID_TRIGGERS_CREATED
    };
}
