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
package org.jumpmind.symmetric.common;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jumpmind.properties.DefaultParameterParser;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.symmetric.service.IParameterService;

/**
 * Constants that represent parameters that can be retrieved or saved via the {@link IParameterService}
 */
final public class ParameterConstants {
    public static final String ALL = "ALL";
    private static Map<String, ParameterMetaData> parameterMetaData = new DefaultParameterParser("/symmetric-default.properties").parse();
    public static final String REDACTED = "***REDACTED***";
    public static final String[] REDACTED_PROPERTIES = new String[] { "db.password", "target.db.password", "smtp.password", "redshift.bulk.load.s3.access.key",
            "redshift.bulk.load.s3.secret.key", "opensearch.load.aws.access.key", "opensearch.load.aws.secret.key", "cloud.bulk.load.s3.access.key",
            "cloud.bulk.load.s3.secret.key", "cloud.bulk.load.azure.sas.token", "registration.secret", "file.sync.s3.secret.key" };

    private ParameterConstants() {
    }

    public static final String AUTO_START_ENGINE = "auto.start.engine";
    public static final String JDBC_EXECUTE_BATCH_SIZE = "db.jdbc.execute.batch.size";
    public static final String JDBC_EXECUTE_BULK_BATCH_SIZE = "db.jdbc.bulk.execute.batch.size";
    public static final String JDBC_EXECUTE_BULK_BATCH_OVERRIDE = "db.jdbc.bulk.execute.batch.override";
    public static final String JDBC_READ_STRINGS_AS_BYTES = "db.read.strings.as.bytes";
    public static final String JDBC_ISOLATION_LEVEL = "db.jdbc.isolation.level";
    public static final String DB_AWS_ACTIVE_KEY = "target.db.aws.active.key";
    public static final String DB_AWS_SECRET_KEY = "target.db.aws.secret.key";
    public static final String DB_AWS_REGION = "target.db.aws.region";
    public static final String DB_AWS_BUCKET = "target.db.aws.bucket";
    public static final String DB_AWS_FOLDER = "target.db.aws.folder";
    public static final String DB_USER = "db.user";
    public static final String DB_PASSWORD = "db.password";
    public static final String START_PULL_JOB = "start.pull.job";
    public static final String START_PUSH_JOB = "start.push.job";
    public static final String START_PURGE_OUTGOING_JOB = "start.purge.incoming.job"; // In <= 3.8m was start.purge.outgoing.job
    public static final String START_PURGE_INCOMING_JOB = "start.purge.outgoing.job"; // In <= 3.8, was start.purge.outgoing.job
    public static final String START_PURGE_JOB_38 = "start.purge.incoming.job";
    public static final String START_ROUTE_JOB = "start.routing.job"; // In <= 3.8, was start.route.job
    public static final String START_ROUTE_JOB_38 = "start.route.job";
    public static final String START_HEARTBEAT_JOB = "start.heartbeat.job";
    public static final String START_SYNCTRIGGERS_JOB = "start.synctriggers.job";
    public static final String START_SYNC_CONFIG_JOB = "start.sync.config.job";
    public static final String START_STATISTIC_FLUSH_JOB = "start.stat.flush.job";
    public static final String START_STAGE_MGMT_JOB = "start.stage.management.job";
    public static final String START_WATCHDOG_JOB = "start.watchdog.job";
    public static final String START_OFFLINE_PULL_JOB = "start.offline.pull.job";
    public static final String START_OFFLINE_PUSH_JOB = "start.offline.push.job";
    public static final String START_REFRESH_CACHE_JOB = "start.refresh.cache.job";
    public static final String START_REFRESH_BACKLOG_REPORT_JOB = "start.refresh.backlog.report.job";
    public static final String START_FILE_SYNC_TRACKER_JOB = "start.file.sync.tracker.job";
    public static final String START_FILE_SYNC_PUSH_JOB = "start.file.sync.push.job";
    public static final String START_FILE_SYNC_PULL_JOB = "start.file.sync.pull.job";
    public static final String START_LOG_MINER_JOB = "start.log.miner.job";
    public static final String ROUTE_ON_EXTRACT = "route.on.extract";
    public static final String PULL_THREAD_COUNT_PER_SERVER = "pull.thread.per.server.count";
    public static final String PULL_MINIMUM_PERIOD_MS = "pull.period.minimum.ms";
    public static final String PULL_LOCK_TIMEOUT_MS = "pull.lock.timeout.ms";
    public static final String PULL_IMMEDIATE_IF_DATA_FOUND = "pull.immediate.if.data.found";
    public static final String PUSH_THREAD_COUNT_PER_SERVER = "push.thread.per.server.count";
    public static final String PUSH_MINIMUM_PERIOD_MS = "push.period.minimum.ms";
    public static final String PUSH_LOCK_TIMEOUT_MS = "push.lock.timeout.ms";
    public static final String PUSH_IMMEDIATE_IF_DATA_FOUND = "push.immediate.if.data.found";
    public static final String OFFLINE_PULL_THREAD_COUNT_PER_SERVER = "offline.pull.thread.per.server.count";
    public static final String OFFLINE_PULL_LOCK_TIMEOUT_MS = "offline.pull.lock.timeout.ms";
    public static final String OFFLINE_PUSH_THREAD_COUNT_PER_SERVER = "offline.push.thread.per.server.count";
    public static final String OFFLINE_PUSH_LOCK_TIMEOUT_MS = "offline.push.lock.timeout.ms";
    public static final String FILE_PULL_THREAD_COUNT_PER_SERVER = "file.pull.thread.per.server.count";
    public static final String FILE_PULL_MINIMUM_PERIOD_MS = "file.pull.period.minimum.ms";
    public static final String FILE_PULL_LOCK_TIMEOUT_MS = "file.pull.lock.timeout.ms";
    public static final String FILE_PUSH_THREAD_COUNT_PER_SERVER = "file.push.thread.per.server.count";
    public static final String FILE_PUSH_MINIMUM_PERIOD_MS = "file.push.period.minimum.ms";
    public static final String FILE_PUSH_LOCK_TIMEOUT_MS = "file.push.lock.timeout.ms";
    public static final String JOB_RANDOM_MAX_START_TIME_MS = "job.random.max.start.time.ms";
    public static final String JOB_PULL_PERIOD_TIME_MS = "job.pull.period.time.ms";
    public static final String JOB_PUSH_PERIOD_TIME_MS = "job.push.period.time.ms";
    public static final String JOB_FILE_SYNC_PULL_PERIOD_TIME_MS = "job.file.sync.pull.period.time.ms";
    public static final String JOB_FILE_SYNC_PUSH_PERIOD_TIME_MS = "job.file.sync.push.period.time.ms";
    public static final String JOB_ROUTING_PERIOD_TIME_MS = "job.routing.period.time.ms";
    public static final String REGISTRATION_NUMBER_OF_ATTEMPTS = "registration.number.of.attempts";
    public static final String REGISTRATION_REOPEN_USE_SAME_PASSWORD = "registration.reopen.use.same.password";
    public static final String REGISTRATION_REQUIRE_NODE_GROUP_LINK = "registration.require.node.group.link";
    public static final String REGISTRATION_REQUIRE_INITIAL_LOAD = "registration.require.initial.load";
    public static final String REGISTRATION_PUSH_CONFIG_ALLOWED = "registration.push.config.allowed";
    public static final String REGISTRATION_AUTO_CREATE_GROUP_LINK = "registration.auto.create.group.link";
    public static final String REGISTRATION_URL = "registration.url";
    public static final String REGISTRATION_MAX_TIME_BETWEEN_RETRIES = "registration.max.time.between.retries";
    public static final String SYNC_URL = "sync.url";
    public static final String ENGINE_NAME = "engine.name";
    public static final String NODE_GROUP_ID = "group.id";
    public static final String EXTERNAL_ID = "external.id";
    public static final String SCHEMA_VERSION = "schema.version";
    public static final String AUTO_REGISTER_ENABLED = "auto.registration";
    public static final String AUTO_RELOAD_ENABLED = "auto.reload";
    public static final String AUTO_RELOAD_USE_CONFIG = "auto.reload.use.config";
    public static final String AUTO_RELOAD_REVERSE_ENABLED = "auto.reload.reverse";
    public static final String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION = "auto.resolve.foreign.key.violation";
    public static final String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE = "auto.resolve.foreign.key.violation.reverse";
    public static final String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE_PEERS = "auto.resolve.foreign.key.violation.reverse.peers";
    public static final String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE_RELOAD = "auto.resolve.foreign.key.violation.reverse.reload";
    public static final String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_DELETE = "auto.resolve.foreign.key.violation.delete";
    public static final String AUTO_RESOLVE_PRIMARY_KEY_VIOLATION = "auto.resolve.primary.key.violation";
    public static final String AUTO_RESOLVE_UNIQUE_INDEX_VIOLATION = "auto.resolve.unique.index.violation";
    public static final String AUTO_RESOLVE_UNIQUE_INDEX_IGNORE_NULL_VALUES = "auto.resolve.unique.index.ignore.null.values";
    public static final String AUTO_RESOLVE_CAPTURE_DELETE_MISSING_ROWS = "auto.resolve.capture.delete.missing.rows";
    public static final String AUTO_INSERT_REG_SVR_IF_NOT_FOUND = "auto.insert.registration.svr.if.not.found";
    public static final String AUTO_SYNC_CONFIGURATION = "auto.sync.configuration";
    public static final String AUTO_SYNC_CONFIGURATION_ON_INCOMING = "auto.sync.configuration.on.incoming";
    public static final String AUTO_CONFIGURE_DATABASE = "auto.config.database";
    public static final String AUTO_CONFIGURE_DATABASE_FAST = "auto.config.database.fast";
    public static final String AUTO_SYNC_TRIGGERS = "auto.sync.triggers";
    public static final String AUTO_SYNC_TRIGGERS_AT_STARTUP = "auto.sync.triggers.at.startup";
    public static final String AUTO_SYNC_TRIGGERS_AT_STARTUP_FORCE = "auto.sync.triggers.at.startup.force";
    public static final String AUTO_SYNC_CONFIG_AT_STARTUP = "auto.sync.config.at.startup";
    public static final String AUTO_SYNC_CONFIG_AFTER_UPGRADE = "auto.sync.config.after.upgrade";
    public static final String AUTO_SYNC_TRIGGERS_AFTER_CONFIG_CHANGED = "auto.sync.triggers.after.config.change";
    public static final String AUTO_SYNC_TRIGGERS_AFTER_CONFIG_LOADED = "auto.sync.triggers.after.config.loaded";
    public static final String AUTO_REFRESH_AFTER_CONFIG_CHANGED = "auto.refresh.after.config.changes.detected";
    public static final String AUTO_CONFIGURE_REG_SVR_SQL_SCRIPT = "auto.config.registration.svr.sql.script";
    public static final String AUTO_CONFIGURE_REG_SVR_DDLUTIL_XML = "auto.config.registration.svr.ddlutil.xml";
    public static final String AUTO_CONFIGURE_EXTRA_TABLES = "auto.config.extra.tables.ddlutil.xml";
    public static final String AUTO_UPDATE_NODE_VALUES = "auto.update.node.values.from.properties";
    public static final String INITIAL_LOAD_BLOCK_CHANNELS = "initial.load.block.channels";
    public static final String INITIAL_LOAD_UNBLOCK_CHANNELS_ON_ERROR = "initial.load.unblock.channels.on.error";
    public static final String INITIAL_LOAD_BEFORE_SQL = "initial.load.before.sql";
    public static final String INITIAL_LOAD_AFTER_SQL = "initial.load.after.sql";
    public static final String INITIAL_LOAD_REVERSE_BEFORE_SQL = "initial.load.reverse.before.sql";
    public static final String INITIAL_LOAD_REVERSE_AFTER_SQL = "initial.load.reverse.after.sql";
    public static final String INITIAL_LOAD_DELETE_BEFORE_RELOAD = "initial.load.delete.first";
    public static final String INITIAL_LOAD_DELETE_FIRST_SQL = "initial.load.delete.first.sql";
    public static final String INITIAL_LOAD_CREATE_SCHEMA_BEFORE_RELOAD = "initial.load.create.first";
    public static final String INITIAL_LOAD_USE_RELOAD_CHANNEL = "initial.load.use.reload.channel";
    public static final String INITIAL_LOAD_REVERSE_FIRST = "initial.load.reverse.first";
    public static final String INITIAL_LOAD_USE_EXTRACT_JOB = "initial.load.use.extract.job.enabled";
    public static final String INITIAL_LOAD_CONCAT_CSV_IN_SQL_ENABLED = "initial.load.concat.csv.in.sql.enabled";
    public static final String INITIAL_LOAD_USE_COLUMN_TEMPLATES_ENABLED = "initial.load.use.column.templates.enabled";
    public static final String INITIAL_LOAD_EXTRACT_THREAD_COUNT_PER_SERVER = "initial.load.extract.thread.per.server.count";
    public static final String INITIAL_LOAD_EXTRACT_MAX_PROCESS_TIME_MS = "initial.load.extract.max.process.time.ms";
    public static final String INITIAL_LOAD_EXTRACT_TIMEOUT_MS = "initial.load.extract.timeout.ms";
    public static final String INITIAL_LOAD_EXTRACT_USE_TWO_PASS_LOB = "initial.load.extract.use.two.pass.lob";
    public static final String INITIAL_LOAD_EXTRACT_JOB_START = "start.initial.load.extract.job";
    public static final String INITIAL_LOAD_SCHEMA_DUMP_COMMAND = "initial.load.schema.dump.command";
    public static final String INITIAL_LOAD_SCHEMA_LOAD_COMMAND = "initial.load.schema.load.command";
    public static final String INITIAL_LOAD_TRANSPORT_MAX_BYTES_TO_SYNC = "initial.load.transport.max.bytes.to.sync";
    public static final String INITIAL_LOAD_USE_ESTIMATED_COUNTS = "initial.load.use.estimated.counts";
    public static final String INITIAL_LOAD_PURGE_STAGE_IMMEDIATE_THRESHOLD_ROWS = "initial.load.purge.stage.immediate.threshold.rows";
    public static final String INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS = "initial.load.defer.create.constraints";
    public static final String INITIAL_LOAD_DEFER_TABLE_LOGGING = "initial.load.defer.table.logging";
    public static final String INITIAL_LOAD_RECURSION_SELF_FK = "initial.load.recursion.self.fk";
    public static final String EXTRACT_CHECK_ROW_SIZE = "extract.check.row.size";
    public static final String EXTRACT_ROW_MAX_LENGTH = "extract.row.max.length";
    public static final String EXTRACT_ROW_CAPTURE_TIME = "extract.row.capture.time";
    public static final String CREATE_TABLE_WITHOUT_DEFAULTS = "create.table.without.defaults";
    public static final String CREATE_TABLE_WITHOUT_FOREIGN_KEYS = "create.table.without.foreign.keys";
    public static final String CREATE_TABLE_WITHOUT_INDEXES = "create.table.without.indexes";
    public static final String CREATE_TABLE_WITHOUT_PK_IF_SOURCE_WITHOUT_PK = "create.table.without.pk.if.source.without.pk";
    public static final String CREATE_TABLE_NOT_NULL_COLUMNS = "create.table.not.null.columns.supported";
    public static final String CREATE_TABLE_INCLUDE_APPLICATION_TRIGGERS = "create.table.include.application.triggers";
    public static final String CREATE_INDEX_CONVERT_UNIQUE_TO_NONUNIQUE_WHEN_COLUMNS_NOT_REQUIRED = "create.index.convert.unique.to.nonunique.when.columns.not.required";
    public static final String STREAM_TO_FILE_ENABLED = "stream.to.file.enabled";
    public static final String STREAM_TO_FILE_THRESHOLD = "stream.to.file.threshold.bytes";
    public static final String STREAM_TO_FILE_TIME_TO_LIVE_MS = "stream.to.file.ttl.ms";
    public static final String STREAM_TO_FILE_MIN_TIME_TO_LIVE_MS = "stream.to.file.min.ttl.ms";
    public static final String STREAM_TO_FILE_PURGE_ON_TTL_ENABLED = "stream.to.file.purge.on.ttl.enabled";
    public static final String PARAMETER_REFRESH_PERIOD_IN_MS = "parameter.reload.timeout.ms";
    public static final String CONCURRENT_WORKERS = "http.concurrent.workers.max";
    public static final String OTEL_METRICS_ENABLED = "metrics.opentelemetry.enabled";
    public static final String CONCURRENT_RESERVATION_TIMEOUT = "http.concurrent.reservation.timeout.ms";
    public static final String OUTGOING_BATCH_PEEK_AHEAD_BATCH_COMMIT_SIZE = "outgoing.batches.peek.ahead.batch.commit.size";
    public static final String OUTGOING_BATCH_COPY_TO_INCOMING_STAGING = "outgoing.batches.copy.to.incoming.staging";
    public static final String ROUTING_FLUSH_JDBC_BATCH_SIZE = "routing.flush.jdbc.batch.size";
    public static final String ROUTING_FLUSH_BATCHES_JDBC_BATCH_SIZE = "routing.flush.batches.jdbc.batch.size";
    public static final String ROUTING_WAIT_FOR_DATA_TIMEOUT_SECONDS = "routing.wait.for.data.timeout.seconds";
    public static final String ROUTING_MAX_GAPS_TO_QUALIFY_IN_SQL = "routing.max.gaps.to.qualify.in.sql";
    public static final String ROUTING_PEEK_AHEAD_MEMORY_THRESHOLD = "routing.peek.ahead.memory.threshold.percent";
    public static final String ROUTING_PEEK_AHEAD_WINDOW = "routing.peek.ahead.window.after.max.size";
    public static final String ROUTING_STALE_DATA_ID_GAP_TIME = "routing.stale.dataid.gap.time.ms";
    public static final String ROUTING_STALE_GAP_BUSY_EXPIRE_TIME = "routing.stale.gap.busy.expire.time.ms";
    public static final String ROUTING_LARGEST_GAP_SIZE = "routing.largest.gap.size";
    public static final String ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED = "routing.data.reader.order.by.gap.id.enabled";
    public static final String ROUTING_DATA_READER_INTO_MEMORY_ENABLED = "routing.data.reader.into.memory.enabled";
    public static final String ROUTING_DATA_READER_THRESHOLD_GAPS_TO_USE_GREATER_QUERY = "routing.data.reader.threshold.gaps.to.use.greater.than.query";
    public static final String ROUTING_DATA_READER_USE_MULTIPLE_QUERIES = "routing.data.reader.use.multiple.queries";
    public static final String ROUTING_LOG_STATS_ON_BATCH_ERROR = "routing.log.stats.on.batch.error";
    public static final String ROUTING_COLLECT_STATS_UNROUTED = "routing.collect.stats.unrouted";
    public static final String ROUTING_USE_FAST_GAP_DETECTOR = "routing.use.fast.gap.detector";
    public static final String ROUTING_DETECT_INVALID_GAPS = "routing.detect.invalid.gaps";
    public static final String ROUTING_QUERY_CHANNELS_FIRST = "routing.query.channels.first";
    public static final String ROUTING_MAX_GAP_CHANGES = "routing.max.gap.changes";
    public static final String ROUTING_USE_COMMON_GROUPS = "routing.use.common.groups";
    public static final String ROUTING_USE_NON_COMMON_FOR_INCOMING = "routing.use.non.common.for.incoming";
    public static final String ROUTING_GAPS_USE_TRANSACTION_VIEW = "routing.gaps.use.transaction.view";
    public static final String ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS = "routing.gaps.transaction.view.clock.sync.threshold";
    public static final String ROUTING_MAX_BATCH_SIZE_EXCEED_PERCENT = "routing.max.batch.size.exceed.percent";
    public static final String ROUTING_USE_CHANNEL_THREADS = "routing.use.channel.threads";
    public static final String ROUTING_THREAD_COUNT_PER_SERVER = "routing.thread.per.server.count";
    public static final String ROUTING_LOCK_TIMEOUT_MS = "routing.lock.timeout.ms";
    public static final String INCOMING_BATCH_SKIP_DUPLICATE_BATCHES_ENABLED = "incoming.batches.skip.duplicates";
    @Deprecated
    public static final String INCOMING_BATCH_DELETE_ON_LOAD = "incoming.batch.delete.on.load";
    public static final String INCOMING_BATCH_RECORD_OK_ENABLED = "incoming.batches.record.ok.enabled";
    public static final String INCOMING_BATCHES_USE_SOURCE_STAGING = "incoming.batches.use.source.staging";
    public static final String DATA_LOADER_ENABLED = "dataloader.enable";
    public static final String DATA_LOADER_APPLY_CHANGES_ONLY = "dataloader.apply.changes.only";
    public static final String DATA_LOADER_IGNORE_MISSING_TABLES = "dataloader.ignore.missing.tables";
    public static final String DATA_LOADER_FIT_TO_COLUMN = "dataloader.fit.to.column";
    public static final String DATA_LOADER_ERROR_RECORD_CUR_VAL = "dataloader.error.save.curval";
    public static final String DATA_LOADER_NUM_OF_ACK_RETRIES = "num.of.ack.retries";
    public static final String DATA_LOADER_SEND_ACK_KEEPALIVE = "send.ack.keepalive.ms";
    public static final String DATA_LOADER_TIME_BETWEEN_ACK_RETRIES = "time.between.ack.retries.ms";
    public static final String DATA_LOADER_MAX_ROWS_BEFORE_COMMIT = "dataloader.max.rows.before.commit";
    public static final String DATA_LOADER_CREATE_TABLE_ALTER_TO_MATCH_DB_CASE = "dataloader.create.table.alter.to.match.db.case";
    public static final String DATA_LOADER_CREATE_TABLE_WITHOUT_DEFAULTS_ON_ERROR = "dataloader.create.table.without.defaults.on.error";
    public static final String DATA_LOADER_TEXT_COLUMN_EXPRESSION = "dataloader.text.column.expression";
    public static final String DATA_LOADER_SLEEP_TIME_AFTER_EARLY_COMMIT = "dataloader.sleep.time.after.early.commit";
    public static final String DATA_LOADER_TREAT_DATETIME_AS_VARCHAR = "db.treat.date.time.as.varchar.enabled";
    public static final String DATA_LOADER_TREAT_BIT_AS_INTEGER = "db.treat.bit.as.integer.enabled";
    public static final String DATA_LOADER_USE_PRIMARY_KEYS_FROM_SOURCE = "dataloader.use.primary.keys.from.source";
    public static final String DATA_LOADER_IGNORE_SQL_EVENT_ERRORS = "dataloader.ignore.sql.event.errors";
    public static final String DATA_LOADER_SQL_EVENT_STRIP_COMMENTS = "dataloader.sql.event.strip.comments";
    public static final String DATA_LOADER_LOG_SQL_PARAMS_ON_ERROR = "dataloader.log.sql.params.on.error";
    public static final String DATA_RELOAD_IS_BATCH_INSERT_TRANSACTIONAL = "datareload.batch.insert.transactional";
    public static final String DATA_EXTRACTOR_ENABLED = "dataextractor.enable";
    public static final String DATA_EXTRACTOR_TEXT_COLUMN_EXPRESSION = "dataextractor.text.column.expression";
    public static final String DATA_FLUSH_JDBC_BATCH_SIZE = "data.flush.jdbc.batch.size";
    public static final String OUTGOING_BATCH_MAX_BATCHES_TO_SELECT = "outgoing.batches.max.to.select";
    public static final String DBDIALECT_ORACLE_USE_TRANSACTION_VIEW_LEGACY = "oracle.use.transaction.view";
    public static final String DBDIALECT_ORACLE_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS_LEGACY = "oracle.transaction.view.clock.sync.threshold.ms";
    public static final String DATA_ID_INCREMENT_BY = "data.id.increment.by";
    public static final String TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED = "http.manual.redirects.enabled";
    public static final String TRANSPORT_HTTP_TIMEOUT = "http.timeout.ms";
    public static final String TRANSPORT_HTTP_CONNECT_TIMEOUT = "http.connect.timeout.ms";
    public static final String TRANSPORT_HTTP_PUSH_STREAM_ENABLED = "http.push.stream.output.enabled";
    public static final String TRANSPORT_HTTP_PUSH_STREAM_SIZE = "http.push.stream.output.size";
    public static final String TRANSPORT_HTTP_USE_COMPRESSION_CLIENT = "http.compression";
    public static final String TRANSPORT_HTTP_COMPRESSION_DISABLED_SERVLET = "web.compression.disabled";
    public static final String TRANSPORT_HTTP_COMPRESSION_LEVEL = "compression.level";
    public static final String TRANSPORT_HTTP_COMPRESSION_STRATEGY = "compression.strategy";
    public static final String TRANSPORT_HTTP_USE_SESSION_AUTH = "http.use.session.auth";
    public static final String TRANSPORT_HTTP_SESSION_EXPIRE_SECONDS = "http.session.expire.seconds";
    public static final String TRANSPORT_HTTP_SESSION_MAX_COUNT = "http.session.max.count";
    public static final String TRANSPORT_HTTP_USE_HEADER_SECURITY_TOKEN = "http.use.header.security.token";
    public static final String TRANSPORT_TYPE = "transport.type";
    public static final String TRANSPORT_MAX_BYTES_TO_SYNC = "transport.max.bytes.to.sync";
    public static final String TRANSPORT_MAX_FORM_KEYS = "transport.max.form.keys";
    public static final String TRANSPORT_MAX_ERROR_MILLIS = "transport.max.error.millis";
    public static final String CACHE_TIMEOUT_GROUPLETS_IN_MS = "cache.grouplets.time.ms";
    public static final String CACHE_TIMEOUT_NODE_SECURITY_IN_MS = "cache.node.security.time.ms";
    public static final String CACHE_TIMEOUT_NODE_IN_MS = "cache.node.time.ms";
    public static final String CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS = "cache.trigger.router.time.ms";
    public static final String CACHE_TIMEOUT_CHANNEL_IN_MS = "cache.channel.time.ms";
    public static final String CACHE_TIMEOUT_READY_QUEUE_IN_MS = "cache.ready.queue.time.ms";
    public static final String CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS = "cache.node.group.link.time.ms";
    public static final String CACHE_TIMEOUT_TRANSFORM_IN_MS = "cache.transform.time.ms";
    public static final String CACHE_TIMEOUT_LOAD_FILTER_IN_MS = "cache.load.filter.time.ms";
    public static final String CACHE_TIMEOUT_CONFLICT_IN_MS = "cache.conflict.time.ms";
    public static final String CACHE_TIMEOUT_TABLES_IN_MS = "cache.table.time.ms";
    public static final String CACHE_CHANNEL_COMMON_BATCHES_IN_MS = "cache.channel.common.batches.time.ms";
    public static final String CACHE_CHANNEL_DEFAULT_ROUTER_IN_MS = "cache.channel.default.router.time.ms";
    public static final String TRIGGER_UPDATE_CAPTURE_CHANGED_DATA_ONLY = "trigger.update.capture.changed.data.only.enabled";
    public static final String TRIGGER_CREATE_BEFORE_INITIAL_LOAD = "trigger.create.before.initial.load.enabled";
    public static final String TRIGGER_CAPTURE_DDL_CHANGES = "trigger.capture.ddl.changes";
    public static final String TRIGGER_CAPTURE_DDL_DELIMITER = "trigger.capture.ddl.delimiter";
    public static final String TRIGGER_CAPTURE_DDL_CHECK_TRIGGER_HIST = "trigger.capture.ddl.check.trigger.hist";
    public static final String TRIGGER_USE_INSERT_DELETE_FOR_PRIMARY_KEY_CHANGES = "trigger.use.insert.delete.for.primary.key.changes";
    public static final String DB_METADATA_IGNORE_CASE = "db.metadata.ignore.case";
    public static final String DB_NATIVE_EXTRACTOR = "db.native.extractor";
    public static final String DB_QUERY_TIMEOUT_SECS = "db.sql.query.timeout.seconds";
    public static final String DB_FETCH_SIZE = "db.jdbc.streaming.results.fetch.size";
    public static final String DB_DELIMITED_IDENTIFIER_MODE = "db.delimited.identifier.mode";
    public static final String TARGET_DB_DELIMITED_IDENTIFIER_MODE = "target.db.delimited.identifier.mode";
    public static final String DB_JNDI_NAME = "db.jndi.name";
    public static final String DB_SPRING_BEAN_NAME = "db.spring.bean.name";
    public static final String RUNTIME_CONFIG_TRIGGER_PREFIX = "sync.trigger.prefix";
    public static final String RUNTIME_CONFIG_TABLE_PREFIX = "sync.table.prefix";
    public static final String NODE_ID_CREATOR_SCRIPT = "node.id.creator.script";
    public static final String NODE_ID_CREATOR_MAX_NODES = "node.id.creator.max.nodes";
    public static final String EXTERNAL_ID_IS_UNIQUE = "external.id.is.unique.enabled";
    public static final String CLUSTER_PEER_HEARTBEAT_MS = "cluster.peer.heartbeat.ms";
    public static final String CLUSTER_PEER_STALE_MS = "cluster.peer.stale.ms";
    public static final String CLUSTER_PEER_OBSOLETE_MS = "cluster.peer.obsolete.ms";
    public static final String CLUSTER_LOCKING_ENABLED = "cluster.lock.enabled";
    public static final String CLUSTER_STAGING_ENABLED = "cluster.staging.enabled";
    public static final String CLUSTER_LOCK_TIMEOUT_MS = "cluster.lock.timeout.ms";
    public static final String CLUSTER_LOCK_REFRESH_MS = "cluster.lock.refresh.ms";
    public static final String LOCK_TIMEOUT_MS = "lock.timeout.ms";
    public static final String LOCK_WAIT_RETRY_MILLIS = "lock.wait.retry.ms";
    public static final String PURGE_LOG_SUMMARY_MINUTES = "purge.log.summary.retention.minutes";
    public static final String PURGE_RETENTION_MINUTES = "purge.retention.minutes";
    public static final String PURGE_EXTRACT_REQUESTS_RETENTION_MINUTES = "purge.extract.request.retention.minutes";
    public static final String PURGE_REGISTRATION_REQUEST_RETENTION_MINUTES = "purge.registration.request.retention.minutes";
    public static final String PURGE_STATS_RETENTION_MINUTES = "purge.stats.retention.minutes";
    public static final String PURGE_METRIC_STATS_RETENTION_MINUTES = "purge.metric.stats.retention.minutes";
    public static final String PURGE_TRIGGER_HIST_RETENTION_MINUTES = "purge.trigger.hist.retention.minutes";
    public static final String PURGE_EXPIRED_DATA_GAP_RETENTION_MINUTES = "purge.expired.data.gap.retention.minutes";
    public static final String PURGE_STRANDED_DATA_RECAPTURE_ENABLED = "job.purge.recapture.stranded.data";
    public static final String PURGE_STRANDED_DATA_TIME_LIMIT_MS = "job.purge.stranded.max.time.ms";
    public static final String PURGE_MONITOR_EVENT_RETENTION_MINUTES = "purge.monitor.event.retention.minutes";
    public static final String PURGE_NODE_HOST_RETENTION_MINUTES = "purge.node.host.retention.minutes";
    public static final String PURGE_MAX_NUMBER_OF_DATA_IDS = "job.purge.max.num.data.to.delete.in.tx";
    public static final String PURGE_MAX_NUMBER_OF_BATCH_IDS = "job.purge.max.num.batches.to.delete.in.tx";
    public static final String PURGE_MAX_NUMBER_OF_EVENT_BATCH_IDS = "job.purge.max.num.data.event.batches.to.delete.in.tx";
    public static final String PURGE_MAX_LINGERING_BATCHES_READ = "job.purge.max.lingering.batches.read";
    public static final String PURGE_MAX_EXPIRED_DATA_GAPS_READ = "job.purge.max.data.gaps.read";
    public static final String PURGE_FIRST_PASS = "job.purge.first.pass";
    public static final String PURGE_FIRST_PASS_OUTSTANDING_BATCHES_THRESHOLD = "job.purge.first.pass.outstanding.batches.threshold";
    public static final String JMX_LINE_FEED = "jmx.line.feed";
    public static final String IP_FILTERS = "ip.filters";
    public static final String NODE_COPY_MODE_ENABLED = "node.copy.mode.enabled";
    public static final String NODE_PASSWORD_FAILED_ATTEMPTS = "node.password.failed.attempts";
    public static final String USER_PASSWORD_FAILED_ATTEMPTS = "console.password.failed.attempts";
    public static final String NODE_OFFLINE = "node.offline";
    public static final String NODE_OFFLINE_INCOMING_DIR = "node.offline.incoming.dir";
    public static final String NODE_OFFLINE_INCOMING_ACCEPT_ALL = "node.offline.incoming.accept.all";
    public static final String NODE_OFFLINE_OUTGOING_DIR = "node.offline.outgoing.dir";
    public static final String NODE_OFFLINE_ERROR_DIR = "node.offline.error.dir";
    public static final String NODE_OFFLINE_ARCHIVE_DIR = "node.offline.archive.dir";
    public static final String OFFLINE_NODE_DETECTION_PERIOD_MINUTES = "offline.node.detection.period.minutes";
    public static final String OFFLINE_NODE_DETECTION_RESTART_MINUTES = "offline.node.detection.restart.minutes";
    public static final String HEARTBEAT_SYNC_ON_PUSH_PERIOD_SEC = "heartbeat.sync.on.push.period.sec";
    public static final String HEARTBEAT_JOB_PERIOD_MS = "job.heartbeat.period.time.ms";
    public static final String HEARTBEAT_SYNC_ON_STARTUP = "heartbeat.sync.on.startup";
    public static final String HEARTBEAT_UPDATE_NODE_WITH_BATCH_STATUS = "heartbeat.update.node.with.batch.status";
    public static final String HEARTBEAT_ENABLED = "heartbeat.sync.on.push.enabled";
    public static final String STATISTIC_RECORD_ENABLE = "statistic.record.enable";
    public static final String STATISTIC_RECORD_COUNT_THRESHOLD = "statistic.record.count.threshold";
    public static final String CURRENT_ACTIVITY_HISTORY_KEEP_COUNT = "statistic.activity.history.keep.count";
    public static final String STORES_UPPERCASE_NAMES_IN_CATALOG = "stores.uppercase.names.in.catalog";
    public static final String DB_MASTER_COLLATION = "db.master.collation";
    public static final String SEQUENCE_TIMEOUT_MS = "sequence.timeout.ms";
    public static final String SYNCHRONIZE_ALL_JOBS = "jobs.synchronized.enable";
    public static final String FILE_SYNC_ENABLE = "file.sync.enable";
    public static final String FILE_SYNC_FAST_SCAN = "file.sync.fast.scan";
    public static final String FILE_SYNC_USE_CRC = "file.sync.use.crc";
    public static final String FILE_SYNC_PREVENT_PING_BACK = "file.sync.prevent.ping.back";
    public static final String FILE_SYNC_LOCK_WAIT_MS = "file.sync.lock.wait.ms";
    public static final String FILE_SYNC_DELETE_ZIP_FILE_AFTER_SYNC = "file.sync.delete.zip.file.after.sync";
    public static final String FILE_SYNC_DELETE_CTL_FILE_AFTER_SYNC = "file.sync.delete.ctl.file.after.sync";
    public static final String FILE_SYNC_USE_CTL_AS_FILE_EXT = "file.sync.use.ctl.as.file.ext";
    public static final String FILE_SYNC_COMPRESSION_LEVEL = "file.compression.level";
    public static final String FILE_SYNC_RETRY_COUNT = "file.sync.retry.count";
    public static final String FILE_SYNC_RETRY_DELAY_MS = "file.sync.retry.delay.ms";
    public static final String BSH_LOAD_FILTER_HANDLES_MISSING_TABLES = "bsh.load.filter.handles.missing.tables";
    public static final String BSH_TRANSFORM_GLOBAL_SCRIPT = "bsh.transform.global.script";
    public static final String BSH_EXTENSION_GLOBAL_SCRIPT = "bsh.extension.global.script";
    public static final String DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC = "sybase.ase.convert.unitypes.for.sync";
    public static final String SYBASE_ROW_LEVEL_LOCKS_ONLY = "sybase.allow.only.row.level.locks.on.runtime.tables";
    public static final String SYBASE_CHANGE_IDENTITY_GAP = "sybase.change.identity.gap.on.runtime.tables";
    public static final String ASE_BULK_EXTRACT_USE_BCP = "ase.bulk.extract.use.bcp";
    public static final String SQLITE_TRIGGER_FUNCTION_TO_USE = "sqlite.trigger.function.to.use";
    public static final String AS400_CAST_CLOB_TO = "as400.cast.clob.to";
    public static final String EXTENSIONS_XML = "extensions.xml";
    public static final String EXTENSION_USE_LEGACY_INTERFACE = "extension.use.legacy.interface.enabled";
    public static final String DATA_CREATE_TIME_TIMEZONE = "data.create_time.timezone";
    public static final String LOG_SLOW_SQL_THRESHOLD_MILLIS = "log.slow.sql.threshold.millis";
    public static final String CONSOLE_LOG_SLOW_SQL_THRESHOLD_MILLIS = "console.log.slow.sql.threshold.millis";
    public static final String LOG_SQL_PARAMETERS_INLINE = "log.sql.parameters.inline";
    public static final String SYNC_TRIGGERS_THREAD_COUNT_PER_SERVER = "sync.triggers.thread.count.per.server";
    public static final String SYNC_TRIGGERS_TIMEOUT_IN_SECONDS = "sync.triggers.timeout.in.seconds";
    public static final String SYNC_TRIGGERS_REG_SVR_INSTALL_WITHOUT_CONFIG = "sync.triggers.reg.svr.install.without.config";
    public static final String SYNC_TRIGGERS_FIX_DUPLICATE_ACTIVE_TRIGGER_HISTORIES = "sync.triggers.fix.duplicate.active.trigger.histories";
    public static final String MONITOR_EVENTS_CAPTURE_ENABLED = "monitor.events.capture.enabled";
    public static final String HYBRID_PUSH_PULL_ENABLED = "hybrid.push.pull.enabled";
    public static final String HYBRID_PUSH_PULL_TIMEOUT = "hybrid.push.pull.timeout.ms";
    public static final String HYBRID_PUSH_PULL_BUFFER_STATUS_UPDATES = "hybrid.push.pull.buffer.status.updates";
    public static final String DBF_ROUTER_VALIDATE_HEADER = "dbf.router.validate.header";
    public static final String OUTGOING_BATCH_UPDATE_STATUS_MILLIS = "outgoing.batches.update.status.millis";
    public static final String OUTGOING_BATCH_UPDATE_STATUS_DATA_COUNT = "outgoing.batches.update.status.data.count";
    public static final String FIREBIRD_EXTRACT_VARCHAR_ROW_OLD_PK_DATA = "firebird.extract.varchar.row.old.pk.data";
    public static final String GROUPLET_ENABLE = "grouplet.enable";
    public static final String CHECK_SOFTWARE_UPDATES = "check.software.updates";
    public static final String SEND_USAGE_STATS = "send.usage.stats";
    public static final String LOG_CONFLICT_RESOLUTION = "log.conflict.resolution";
    public static final String CONFLICT_DEFAULT_PK_WITH_FALLBACK = "conflict.default.pk.with.fallback";
    public static final String UPDATE_SERVICE_CLASS = "update.service.class";
    public static final String STAGING_MANAGER_CLASS = "staging.manager.class";
    public static final String STAGING_DIR = "staging.dir";
    public static final String STAGING_LOW_SPACE_THRESHOLD_MEGABYTES = "staging.low.space.threshold.megabytes";
    public static final String STATISTIC_MANAGER_CLASS = "statistic.manager.class";
    public static final String DB2_CAPTURE_TRANSACTION_ID = "db2.capture.transaction.id";
    public static final String TREAT_BINARY_AS_LOB_ENABLED = "treat.binary.as.lob.enabled";
    public static final String RIGHT_TRIM_CHAR_VALUES = "right.trim.char.values";
    public static final String ALLOW_UPDATES_WITH_RESULTS = "allow.updates.with.results";
    public static final String ALLOW_TRIGGER_CREATE_OR_REPLACE = "trigger.allow.create.or.replace";
    public static final String NODE_LOAD_ONLY = "load.only";
    public static final String MYSQL_TINYINT_DDL_TO_BOOLEAN = "mysql.tinyint.ddl.to.boolean";
    public static final String LOAD_ONLY_PROPERTY_PREFIX = "target.";
    public static final String KAFKA_PRODUCER = "kafka.producer";
    public static final String KAFKA_FORMAT = "kafka.format";
    public static final String KAFKA_MESSAGE_BY = "kafka.message.by";
    public static final String KAFKA_TOPIC_BY = "kafka.topic.by";
    public static final String KAFKA_CONFLUENT_REGISTRY_URL = "kafka.confluent.registry.url";
    public static final String KAFKA_AVRO_JAVA_PACKAGE = "kafka.avro.java.package";
    public static final String KAFKACLIENT_SECURITY_PROTOCOL = "kafkaclient.security.protocol";
    public static final String KAFKACLIENT_SSL_KEYSTORE_LOCATION = "kafkaclient.ssl.keystore.location";
    public static final String KAFKACLIENT_SSL_KEYSTORE_PASSWORD = "kafkaclient.ssl.keystore.password";
    public static final String KAFKACLIENT_SSL_TRUSTSTORE_LOCATION = "kafkaclient.ssl.truststore.location";
    public static final String KAFKACLIENT_SSL_KEYSTORE_TYPE = "kafkaclient.ssl.keystore.type";
    public static final String[] ALL_KAFKA_PARAMS = new String[] { KAFKA_PRODUCER, KAFKA_FORMAT, KAFKA_MESSAGE_BY,
            KAFKA_TOPIC_BY, KAFKA_CONFLUENT_REGISTRY_URL, KAFKA_AVRO_JAVA_PACKAGE, KAFKACLIENT_SECURITY_PROTOCOL,
            KAFKACLIENT_SSL_KEYSTORE_LOCATION, KAFKACLIENT_SSL_KEYSTORE_PASSWORD, KAFKACLIENT_SSL_TRUSTSTORE_LOCATION,
            KAFKACLIENT_SSL_KEYSTORE_TYPE };
    public static final String RABBITMQ_FORMAT = "rabbitmq.format";
    public static final String RABBITMQ_QUEUE_NAME = "rabbitmq.queue.name";
    public static final String RABBITMQ_MESSAGE_BY = "rabbitmq.message.by";
    public static final String RABBITMQ_QUEUE_BY = "rabbitmq.queue.by";
    public static final String RABBITMQ_USE_SSL = "rabbitmq.use.ssl";
    public static final String SNOWFLAKE_STAGING_TYPE = "snowflake.staging.type";
    public static final String SNOWFLAKE_INTERNAL_STAGE_NAME = "snowflake.internal.stage.name";
    public static final String SNAPSHOT_FILE_INCLUDE_HOSTNAME = "snapshot.file.include.hostname";
    public static final String SNAPSHOT_MAX_FILES = "snapshot.max.files";
    public static final String SNAPSHOT_MAX_BATCHES = "snapshot.max.batches";
    public static final String SNAPSHOT_MAX_NODE_CHANNELS = "snapshot.max.node.channels";
    public static final String SNAPSHOT_OPERATION_TIMEOUT_MS = "snapshot.operation.timeout.ms";
    public static final String POSTGRES_TRIGGER_CAPTURE_TRUNCATE = "postgres.trigger.capture.truncate.event";
    public static final String POSTGRES_SECURITY_DEFINER = "postgres.security.definer";
    public static final String POSTGRES_CONVERT_INFINITY_DATE_TO_NULL = "postgres.convert.infinity.date.to.null";
    public static final String INCLUDE_ROWIDENTIFIER_AS_COLUMN = "include.rowidentifier.as.column";
    public static final String GOOGLE_BIG_QUERY_MAX_ROWS_PER_RPC = "google.bigquery.max.rows.per.rpc";
    public static final String GOOGLE_BIG_QUERY_LOCATION = "google.bigquery.location";
    public static final String GOOGLE_BIG_QUERY_PROJECT_ID = "google.bigquery.project.id";
    public static final String GOOGLE_BIG_QUERY_SECURITY_CREDENTIALS_PATH = "google.bigquery.security.credentials.path";
    public static final String[] ALL_GOOGLE_BIG_QUERY_PARAMS = new String[] { GOOGLE_BIG_QUERY_MAX_ROWS_PER_RPC, GOOGLE_BIG_QUERY_LOCATION,
            GOOGLE_BIG_QUERY_PROJECT_ID, GOOGLE_BIG_QUERY_SECURITY_CREDENTIALS_PATH };
    public static final String HBASE_SITE_XML_PATH = "hbase.site.xml.path";
    public static final String MONGO_DEFAULT_DATABASE = "mongodb.default.database";
    public static final String MONGO_USE_MONGO_IDS = "mongodb.use.mongo.ids";
    public static final String[] ALL_MONGODB_PARAMS = new String[] { MONGO_DEFAULT_DATABASE, MONGO_USE_MONGO_IDS };
    public static final String COSMOS_DEFAULT_DATABASE = "cosmosdb.default.database";
    public static final String[] ALL_COSMOS_PARAMS = new String[] { COSMOS_DEFAULT_DATABASE };
    public static final String OPENSEARCH_LOAD_AWS_ACCESS_KEY = "opensearch.load.aws.access.key";
    public static final String OPENSEARCH_LOAD_AWS_SECRET_KEY = "opensearch.load.aws.secret.key";
    public static final String S3_LOAD_AWS_ACCESS_KEY = "s3.load.aws.access.key";
    public static final String S3_LOAD_AWS_SECRET_KEY = "s3.load.aws.secret.key";
    public static final String S3_LOAD_FORMAT = "s3.load.format";
    public static final String SINGLESTORE_AUDIT_LOG_DIR = "single.store.audit.log.dir";
    public static final String SPATIAL_TYPES_ENABLED = "spatial.data.types.enabled";
    public static final String DEFAULT_VALUES_TO_LEAVE_UNQUOTED = "default.values.to.leave.unquoted";
    public static final String DEFAULT_VALUES_TO_TRANSLATE = "default.values.to.translate";
    public static final String COMPARE_QUEUE_PER_REQUEST_COUNT = "compare.queue.per.request.count";
    public static final String COMPARE_THREAD_PER_SERVER_COUNT = "compare.thread.per.server.count";
    public static final String COMPARE_LOCK_TIMEOUT_MS = "compare.lock.timeout.ms";
    public static final String CAPTURE_TYPE_TIME_BASED = "time.based.capture";
    public static final String FILESYNCTRACKER_MAX_ROWS_BEFORE_COMMIT = "filesynctracker.max.rows.before.commit";
    public static final String SYNC_USE_READY_QUEUES = "sync.use.ready.queues";
    public static final String KEEP_BULK_STAGING_FILES = "keep.bulk.staging.files";
    public static final String SHOW_PIPELINES_VIEW = "console.show.pipelines.view";
    public final static String TRIGGER_CAPTURE_DDL_SEND_TABLE = "trigger.capture.ddl.send.table";
    // Collections of parameters:
    public static final String DB_POOL_URL = "db.url";
    public static final String DB_POOL_USER = "db.user";
    public static final String[] STARTUP_DB_OBJECTS_SETUP_PARAMS = new String[] {
            TRIGGER_CAPTURE_DDL_CHANGES,
            POSTGRES_TRIGGER_CAPTURE_TRUNCATE,
            TRIGGER_CAPTURE_DDL_CHECK_TRIGGER_HIST,
            TRIGGER_CAPTURE_DDL_DELIMITER,
            DB_POOL_USER,
            DB_POOL_URL,
            LOAD_ONLY_PROPERTY_PREFIX + DB_POOL_URL };
    public static final String[] ALL_JDBC_PARAMS = new String[] { DB_FETCH_SIZE, DB_QUERY_TIMEOUT_SECS, JDBC_EXECUTE_BATCH_SIZE, JDBC_ISOLATION_LEVEL,
            JDBC_READ_STRINGS_AS_BYTES, TREAT_BINARY_AS_LOB_ENABLED, LOG_SLOW_SQL_THRESHOLD_MILLIS, LOG_SQL_PARAMETERS_INLINE,
            INCLUDE_ROWIDENTIFIER_AS_COLUMN };

    public static Map<String, ParameterMetaData> getParameterMetaData() {
        return parameterMetaData;
    }

    public static Set<String> getAllParameterTags() {
        TreeSet<String> tags = new TreeSet<String>();
        Collection<ParameterMetaData> meta = parameterMetaData.values();
        for (ParameterMetaData parameterMetaData : meta) {
            tags.addAll(parameterMetaData.getTags());
        }
        return tags;
    }
}
