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

    public static final String OTEL_SCOPE = "symmetricds";
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
    public static final String METRIC_UNIT_ROWS = "rows";
    public static final String METRIC_UNIT_NODES = "nodes";
    public static final String METRIC_UNIT_TRIGGERS = "triggers";
    // Incoming client connection counters (ConcurrentConnectionManager)
    public static final String METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS = OTEL_SCOPE + ".server.reservations.count";
    public static final String METRIC_ID_SERVER_CONNECTIONS_UTILIZATION = OTEL_SCOPE + ".server.connections.utilization";
    // Channel-scoped data counters (StatisticManager)
    public static final String METRIC_ID_DATA_ROUTED = OTEL_SCOPE + ".data.routed.count";
    public static final String METRIC_ID_DATA_EXTRACTED = OTEL_SCOPE + ".data.extracted.count";
    public static final String METRIC_ID_DATA_EXTRACTED_BYTES = OTEL_SCOPE + ".data.extracted.bytes";
    public static final String METRIC_ID_DATA_EXTRACTED_ERRORS = OTEL_SCOPE + ".data.extracted.errors";
    public static final String METRIC_ID_DATA_EVENTS_INSERTED = OTEL_SCOPE + ".data.events.inserted.count";
    public static final String METRIC_ID_DATA_SENT = OTEL_SCOPE + ".data.sent.count";
    public static final String METRIC_ID_DATA_SENT_BYTES = OTEL_SCOPE + ".data.sent.bytes";
    public static final String METRIC_ID_DATA_SENT_ERRORS = OTEL_SCOPE + ".data.sent.errors";
    public static final String METRIC_ID_DATA_RECEIVED = OTEL_SCOPE + ".data.received.count";
    public static final String METRIC_ID_DATA_RECEIVED_BYTES = OTEL_SCOPE + ".data.received.bytes";
    public static final String METRIC_ID_DATA_LOADED = OTEL_SCOPE + ".data.loaded.count";
    public static final String METRIC_ID_DATA_LOADED_BYTES = OTEL_SCOPE + ".data.loaded.bytes";
    public static final String METRIC_ID_DATA_LOADED_ERRORS = OTEL_SCOPE + ".data.loaded.errors";
    public static final String METRIC_ID_DATA_LOADED_OUTGOING = OTEL_SCOPE + ".data.loaded.outgoing.count";
    public static final String METRIC_ID_DATA_LOADED_OUTGOING_BYTES = OTEL_SCOPE + ".data.loaded.outgoing.bytes";
    public static final String METRIC_ID_DATA_LOADED_OUTGOING_ERRORS = OTEL_SCOPE + ".data.loaded.outgoing.errors";
    // Channel-scoped gauges (StatisticManager)
    public static final String METRIC_ID_DATA_UNROUTED_CHANNEL = OTEL_SCOPE + ".data.unrouted.channel.count";
    public static final String METRIC_ID_DATA_CREATE_TIME_MIN = OTEL_SCOPE + ".data.create.time.min";
    public static final String METRIC_ID_DATA_CREATE_TIME_MAX = OTEL_SCOPE + ".data.create.time.max";
    // Routing and CGC gauges (StatisticManager)
    public static final String METRIC_ID_DATA_GAP_COUNT = OTEL_SCOPE + ".data.gap.count";
    public static final String METRIC_ID_DATA_UNROUTED_TOTAL = OTEL_SCOPE + ".data.unrouted.total";
    // Database connection pool gauges (StatisticManager)
    public static final String METRIC_ID_RUNTIME_DBPOOL_ACTIVE = OTEL_SCOPE + ".dbpool.active.count";
    public static final String METRIC_ID_RUNTIME_DBPOOL_IDLE = OTEL_SCOPE + ".dbpool.idle.count";
    public static final String METRIC_ID_RUNTIME_DBPOOL_UTILIZATION = OTEL_SCOPE + ".dbpool.connections.utilization";
    public static final String METRIC_ID_RUNTIME_DBPOOL_WAITERS = OTEL_SCOPE + ".dbpool.waiters.count";
    public static final String METRIC_ID_RUNTIME_DBPOOL_WAITERS_DELAY_MEAN = OTEL_SCOPE + ".dbpool.waiters.delay.mean";
    // Engine / node counters (StatisticManager)
    public static final String METRIC_ID_ENGINE_RESTARTS = OTEL_SCOPE + ".engine.restarts";
    public static final String METRIC_ID_NODES_PULLED = OTEL_SCOPE + ".nodes.pulled.count";
    public static final String METRIC_ID_NODES_PUSHED = OTEL_SCOPE + ".nodes.pushed.count";
    public static final String METRIC_ID_NODES_PULLED_TIME = OTEL_SCOPE + ".nodes.pulled.time";
    public static final String METRIC_ID_NODES_PUSHED_TIME = OTEL_SCOPE + ".nodes.pushed.time";
    public static final String METRIC_ID_NODES_REJECTED = OTEL_SCOPE + ".nodes.rejected.count";
    public static final String METRIC_ID_NODES_REGISTERED = OTEL_SCOPE + ".nodes.registered.count";
    public static final String METRIC_ID_NODES_LOADED = OTEL_SCOPE + ".nodes.loaded.count";
    public static final String METRIC_ID_NODES_DISABLED = OTEL_SCOPE + ".nodes.disabled.count";
    // Purge counters (StatisticManager)
    public static final String METRIC_ID_PURGE_BATCH_INCOMING_ROWS = OTEL_SCOPE + ".purge.batch.incoming.rows";
    public static final String METRIC_ID_PURGE_BATCH_OUTGOING_ROWS = OTEL_SCOPE + ".purge.batch.outgoing.rows";
    public static final String METRIC_ID_PURGE_DATA_ROWS = OTEL_SCOPE + ".purge.data.rows";
    public static final String METRIC_ID_PURGE_DATA_EVENT_ROWS = OTEL_SCOPE + ".purge.data.event.rows";
    public static final String METRIC_ID_PURGE_STRANDED_DATA_ROWS = OTEL_SCOPE + ".purge.stranded.data.rows";
    public static final String METRIC_ID_PURGE_STRANDED_DATA_EVENT_ROWS = OTEL_SCOPE + ".purge.stranded.data.event.rows";
    public static final String METRIC_ID_PURGE_EXPIRED_DATA_ROWS = OTEL_SCOPE + ".purge.expired.data.rows";
    // Trigger counters (StatisticManager)
    public static final String METRIC_ID_TRIGGERS_REMOVED = OTEL_SCOPE + ".triggers.removed.count";
    public static final String METRIC_ID_TRIGGERS_REBUILT = OTEL_SCOPE + ".triggers.rebuilt.count";
    public static final String METRIC_ID_TRIGGERS_CREATED = OTEL_SCOPE + ".triggers.created.count";
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
            METRIC_ID_DATA_UNROUTED_TOTAL,
            METRIC_ID_RUNTIME_DBPOOL_ACTIVE,
            METRIC_ID_RUNTIME_DBPOOL_IDLE,
            METRIC_ID_RUNTIME_DBPOOL_UTILIZATION,
            METRIC_ID_RUNTIME_DBPOOL_WAITERS,
            METRIC_ID_RUNTIME_DBPOOL_WAITERS_DELAY_MEAN,
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
