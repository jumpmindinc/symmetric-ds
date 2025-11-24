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

    private ParameterConstants() {
    }

    public final static String AUTO_START_ENGINE = "auto.start.engine";
    public final static String JDBC_EXECUTE_BATCH_SIZE = "db.jdbc.execute.batch.size";
    public final static String JDBC_EXECUTE_BULK_BATCH_SIZE = "db.jdbc.bulk.execute.batch.size";
    public final static String JDBC_EXECUTE_BULK_BATCH_OVERRIDE = "db.jdbc.bulk.execute.batch.override";
    public final static String JDBC_READ_STRINGS_AS_BYTES = "db.read.strings.as.bytes";
    public final static String JDBC_ISOLATION_LEVEL = "db.jdbc.isolation.level";
    public final static String DB_AWS_ACTIVE_KEY = "target.db.aws.active.key";
    public final static String DB_AWS_SECRET_KEY = "target.db.aws.secret.key";
    public final static String DB_AWS_REGION = "target.db.aws.region";
    public final static String DB_AWS_BUCKET = "target.db.aws.bucket";
    public final static String DB_AWS_FOLDER = "target.db.aws.folder";
    public final static String DB_USER = "db.user";
    public final static String DB_PASSWORD = "db.password";
    public final static String START_PULL_JOB = "start.pull.job";
    public final static String START_PUSH_JOB = "start.push.job";
    public final static String START_PURGE_OUTGOING_JOB = "start.purge.incoming.job"; // In <= 3.8m was start.purge.outgoing.job
    public final static String START_PURGE_INCOMING_JOB = "start.purge.outgoing.job"; // In <= 3.8, was start.purge.outgoing.job
    public final static String START_PURGE_JOB_38 = "start.purge.incoming.job";
    public final static String START_ROUTE_JOB = "start.routing.job"; // In <= 3.8, was start.route.job
    public final static String START_ROUTE_JOB_38 = "start.route.job";
    public final static String START_HEARTBEAT_JOB = "start.heartbeat.job";
    public final static String START_SYNCTRIGGERS_JOB = "start.synctriggers.job";
    public final static String START_SYNC_CONFIG_JOB = "start.sync.config.job";
    public final static String START_STATISTIC_FLUSH_JOB = "start.stat.flush.job";
    public final static String START_STAGE_MGMT_JOB = "start.stage.management.job";
    public final static String START_WATCHDOG_JOB = "start.watchdog.job";
    public final static String START_OFFLINE_PULL_JOB = "start.offline.pull.job";
    public final static String START_OFFLINE_PUSH_JOB = "start.offline.push.job";
    public final static String START_REFRESH_CACHE_JOB = "start.refresh.cache.job";
    public final static String START_FILE_SYNC_TRACKER_JOB = "start.file.sync.tracker.job";
    public final static String START_FILE_SYNC_PUSH_JOB = "start.file.sync.push.job";
    public final static String START_FILE_SYNC_PULL_JOB = "start.file.sync.pull.job";
    public final static String START_LOG_MINER_JOB = "start.log.miner.job";
    public final static String ROUTE_ON_EXTRACT = "route.on.extract";
    public final static String PULL_THREAD_COUNT_PER_SERVER = "pull.thread.per.server.count";
    public final static String PULL_MINIMUM_PERIOD_MS = "pull.period.minimum.ms";
    public final static String PULL_LOCK_TIMEOUT_MS = "pull.lock.timeout.ms";
    public final static String PULL_IMMEDIATE_IF_DATA_FOUND = "pull.immediate.if.data.found";
    public final static String PUSH_THREAD_COUNT_PER_SERVER = "push.thread.per.server.count";
    public final static String PUSH_MINIMUM_PERIOD_MS = "push.period.minimum.ms";
    public final static String PUSH_LOCK_TIMEOUT_MS = "push.lock.timeout.ms";
    public final static String PUSH_IMMEDIATE_IF_DATA_FOUND = "push.immediate.if.data.found";
    public final static String OFFLINE_PULL_THREAD_COUNT_PER_SERVER = "offline.pull.thread.per.server.count";
    public final static String OFFLINE_PULL_LOCK_TIMEOUT_MS = "offline.pull.lock.timeout.ms";
    public final static String OFFLINE_PUSH_THREAD_COUNT_PER_SERVER = "offline.push.thread.per.server.count";
    public final static String OFFLINE_PUSH_LOCK_TIMEOUT_MS = "offline.push.lock.timeout.ms";
    public final static String FILE_PULL_THREAD_COUNT_PER_SERVER = "file.pull.thread.per.server.count";
    public final static String FILE_PULL_MINIMUM_PERIOD_MS = "file.pull.period.minimum.ms";
    public final static String FILE_PULL_LOCK_TIMEOUT_MS = "file.pull.lock.timeout.ms";
    public final static String FILE_PUSH_THREAD_COUNT_PER_SERVER = "file.push.thread.per.server.count";
    public final static String FILE_PUSH_MINIMUM_PERIOD_MS = "file.push.period.minimum.ms";
    public final static String FILE_PUSH_LOCK_TIMEOUT_MS = "file.push.lock.timeout.ms";
    public final static String JOB_RANDOM_MAX_START_TIME_MS = "job.random.max.start.time.ms";
    public final static String JOB_PULL_PERIOD_TIME_MS = "job.pull.period.time.ms";
    public final static String JOB_PUSH_PERIOD_TIME_MS = "job.push.period.time.ms";
    public final static String JOB_FILE_SYNC_PULL_PERIOD_TIME_MS = "job.file.sync.pull.period.time.ms";
    public final static String JOB_FILE_SYNC_PUSH_PERIOD_TIME_MS = "job.file.sync.push.period.time.ms";
    public final static String JOB_ROUTING_PERIOD_TIME_MS = "job.routing.period.time.ms";
    public final static String REGISTRATION_NUMBER_OF_ATTEMPTS = "registration.number.of.attempts";
    public final static String REGISTRATION_REOPEN_USE_SAME_PASSWORD = "registration.reopen.use.same.password";
    public final static String REGISTRATION_REQUIRE_NODE_GROUP_LINK = "registration.require.node.group.link";
    public final static String REGISTRATION_REQUIRE_INITIAL_LOAD = "registration.require.initial.load";
    public final static String REGISTRATION_PUSH_CONFIG_ALLOWED = "registration.push.config.allowed";
    public final static String REGISTRATION_AUTO_CREATE_GROUP_LINK = "registration.auto.create.group.link";
    public final static String REGISTRATION_URL = "registration.url";
    public final static String REGISTRATION_MAX_TIME_BETWEEN_RETRIES = "registration.max.time.between.retries";
    public final static String SYNC_URL = "sync.url";
    public final static String ENGINE_NAME = "engine.name";
    public final static String NODE_GROUP_ID = "group.id";
    public final static String EXTERNAL_ID = "external.id";
    public final static String SCHEMA_VERSION = "schema.version";
    public final static String AUTO_REGISTER_ENABLED = "auto.registration";
    public final static String AUTO_RELOAD_ENABLED = "auto.reload";
    public final static String AUTO_RELOAD_USE_CONFIG = "auto.reload.use.config";
    public final static String AUTO_RELOAD_REVERSE_ENABLED = "auto.reload.reverse";
    public final static String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION = "auto.resolve.foreign.key.violation";
    public final static String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE = "auto.resolve.foreign.key.violation.reverse";
    public final static String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE_PEERS = "auto.resolve.foreign.key.violation.reverse.peers";
    public final static String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE_RELOAD = "auto.resolve.foreign.key.violation.reverse.reload";
    public final static String AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_DELETE = "auto.resolve.foreign.key.violation.delete";
    public final static String AUTO_RESOLVE_PRIMARY_KEY_VIOLATION = "auto.resolve.primary.key.violation";
    public final static String AUTO_RESOLVE_UNIQUE_INDEX_VIOLATION = "auto.resolve.unique.index.violation";
    public final static String AUTO_RESOLVE_UNIQUE_INDEX_IGNORE_NULL_VALUES = "auto.resolve.unique.index.ignore.null.values";
    public final static String AUTO_RESOLVE_CAPTURE_DELETE_MISSING_ROWS = "auto.resolve.capture.delete.missing.rows";
    public final static String AUTO_INSERT_REG_SVR_IF_NOT_FOUND = "auto.insert.registration.svr.if.not.found";
    public final static String AUTO_SYNC_CONFIGURATION = "auto.sync.configuration";
    public final static String AUTO_SYNC_CONFIGURATION_ON_INCOMING = "auto.sync.configuration.on.incoming";
    public final static String AUTO_CONFIGURE_DATABASE = "auto.config.database";
    public final static String AUTO_CONFIGURE_DATABASE_FAST = "auto.config.database.fast";
    public final static String AUTO_SYNC_TRIGGERS = "auto.sync.triggers";
    public final static String AUTO_SYNC_TRIGGERS_AT_STARTUP = "auto.sync.triggers.at.startup";
    public final static String AUTO_SYNC_TRIGGERS_AT_STARTUP_FORCE = "auto.sync.triggers.at.startup.force";
    public final static String AUTO_SYNC_CONFIG_AT_STARTUP = "auto.sync.config.at.startup";
    public final static String AUTO_SYNC_CONFIG_AFTER_UPGRADE = "auto.sync.config.after.upgrade";
    public final static String AUTO_SYNC_TRIGGERS_AFTER_CONFIG_CHANGED = "auto.sync.triggers.after.config.change";
    public final static String AUTO_SYNC_TRIGGERS_AFTER_CONFIG_LOADED = "auto.sync.triggers.after.config.loaded";
    public final static String AUTO_REFRESH_AFTER_CONFIG_CHANGED = "auto.refresh.after.config.changes.detected";
    public final static String AUTO_CONFIGURE_REG_SVR_SQL_SCRIPT = "auto.config.registration.svr.sql.script";
    public final static String AUTO_CONFIGURE_REG_SVR_DDLUTIL_XML = "auto.config.registration.svr.ddlutil.xml";
    public final static String AUTO_CONFIGURE_EXTRA_TABLES = "auto.config.extra.tables.ddlutil.xml";
    public final static String AUTO_UPDATE_NODE_VALUES = "auto.update.node.values.from.properties";
    public final static String INITIAL_LOAD_BLOCK_CHANNELS = "initial.load.block.channels";
    public final static String INITIAL_LOAD_UNBLOCK_CHANNELS_ON_ERROR = "initial.load.unblock.channels.on.error";
    public final static String INITIAL_LOAD_BEFORE_SQL = "initial.load.before.sql";
    public final static String INITIAL_LOAD_AFTER_SQL = "initial.load.after.sql";
    public final static String INITIAL_LOAD_REVERSE_BEFORE_SQL = "initial.load.reverse.before.sql";
    public final static String INITIAL_LOAD_REVERSE_AFTER_SQL = "initial.load.reverse.after.sql";
    public final static String INITIAL_LOAD_DELETE_BEFORE_RELOAD = "initial.load.delete.first";
    public final static String INITIAL_LOAD_DELETE_FIRST_SQL = "initial.load.delete.first.sql";
    public final static String INITIAL_LOAD_CREATE_SCHEMA_BEFORE_RELOAD = "initial.load.create.first";
    public final static String INITIAL_LOAD_USE_RELOAD_CHANNEL = "initial.load.use.reload.channel";
    public final static String INITIAL_LOAD_REVERSE_FIRST = "initial.load.reverse.first";
    public final static String INITIAL_LOAD_USE_EXTRACT_JOB = "initial.load.use.extract.job.enabled";
    public final static String INITIAL_LOAD_CONCAT_CSV_IN_SQL_ENABLED = "initial.load.concat.csv.in.sql.enabled";
    public final static String INITIAL_LOAD_USE_COLUMN_TEMPLATES_ENABLED = "initial.load.use.column.templates.enabled";
    public final static String INITIAL_LOAD_EXTRACT_THREAD_COUNT_PER_SERVER = "initial.load.extract.thread.per.server.count";
    public final static String INITIAL_LOAD_EXTRACT_MAX_PROCESS_TIME_MS = "initial.load.extract.max.process.time.ms";
    public final static String INITIAL_LOAD_EXTRACT_TIMEOUT_MS = "initial.load.extract.timeout.ms";
    public final static String INITIAL_LOAD_EXTRACT_USE_TWO_PASS_LOB = "initial.load.extract.use.two.pass.lob";
    public final static String INITIAL_LOAD_EXTRACT_JOB_START = "start.initial.load.extract.job";
    public final static String INITIAL_LOAD_SCHEMA_DUMP_COMMAND = "initial.load.schema.dump.command";
    public final static String INITIAL_LOAD_SCHEMA_LOAD_COMMAND = "initial.load.schema.load.command";
    public final static String INITIAL_LOAD_TRANSPORT_MAX_BYTES_TO_SYNC = "initial.load.transport.max.bytes.to.sync";
    public final static String INITIAL_LOAD_USE_ESTIMATED_COUNTS = "initial.load.use.estimated.counts";
    public final static String INITIAL_LOAD_PURGE_STAGE_IMMEDIATE_THRESHOLD_ROWS = "initial.load.purge.stage.immediate.threshold.rows";
    public final static String INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS = "initial.load.defer.create.constraints";
    public final static String INITIAL_LOAD_DEFER_TABLE_LOGGING = "initial.load.defer.table.logging";
    public final static String INITIAL_LOAD_RECURSION_SELF_FK = "initial.load.recursion.self.fk";
    public final static String EXTRACT_CHECK_ROW_SIZE = "extract.check.row.size";
    public final static String EXTRACT_ROW_MAX_LENGTH = "extract.row.max.length";
    public final static String EXTRACT_ROW_CAPTURE_TIME = "extract.row.capture.time";
    public final static String CREATE_TABLE_WITHOUT_DEFAULTS = "create.table.without.defaults";
    public final static String CREATE_TABLE_WITHOUT_FOREIGN_KEYS = "create.table.without.foreign.keys";
    public final static String CREATE_TABLE_WITHOUT_INDEXES = "create.table.without.indexes";
    public final static String CREATE_TABLE_WITHOUT_PK_IF_SOURCE_WITHOUT_PK = "create.table.without.pk.if.source.without.pk";
    public final static String CREATE_TABLE_NOT_NULL_COLUMNS = "create.table.not.null.columns.supported";
    public final static String CREATE_TABLE_INCLUDE_APPLICATION_TRIGGERS = "create.table.include.application.triggers";
    public final static String CREATE_INDEX_CONVERT_UNIQUE_TO_NONUNIQUE_WHEN_COLUMNS_NOT_REQUIRED = "create.index.convert.unique.to.nonunique.when.columns.not.required";
    public final static String STREAM_TO_FILE_ENABLED = "stream.to.file.enabled";
    public final static String STREAM_TO_FILE_THRESHOLD = "stream.to.file.threshold.bytes";
    public final static String STREAM_TO_FILE_TIME_TO_LIVE_MS = "stream.to.file.ttl.ms";
    public final static String STREAM_TO_FILE_MIN_TIME_TO_LIVE_MS = "stream.to.file.min.ttl.ms";
    public final static String STREAM_TO_FILE_PURGE_ON_TTL_ENABLED = "stream.to.file.purge.on.ttl.enabled";
    public final static String PARAMETER_REFRESH_PERIOD_IN_MS = "parameter.reload.timeout.ms";
    public final static String CONCURRENT_WORKERS = "http.concurrent.workers.max";
    public final static String CONCURRENT_RESERVATION_TIMEOUT = "http.concurrent.reservation.timeout.ms";
    public final static String OUTGOING_BATCH_PEEK_AHEAD_BATCH_COMMIT_SIZE = "outgoing.batches.peek.ahead.batch.commit.size";
    public final static String OUTGOING_BATCH_COPY_TO_INCOMING_STAGING = "outgoing.batches.copy.to.incoming.staging";
    public final static String ROUTING_FLUSH_JDBC_BATCH_SIZE = "routing.flush.jdbc.batch.size";
    public final static String ROUTING_FLUSH_BATCHES_JDBC_BATCH_SIZE = "routing.flush.batches.jdbc.batch.size";
    public final static String ROUTING_WAIT_FOR_DATA_TIMEOUT_SECONDS = "routing.wait.for.data.timeout.seconds";
    public final static String ROUTING_MAX_GAPS_TO_QUALIFY_IN_SQL = "routing.max.gaps.to.qualify.in.sql";
    public final static String ROUTING_PEEK_AHEAD_MEMORY_THRESHOLD = "routing.peek.ahead.memory.threshold.percent";
    public final static String ROUTING_PEEK_AHEAD_WINDOW = "routing.peek.ahead.window.after.max.size";
    public final static String ROUTING_STALE_DATA_ID_GAP_TIME = "routing.stale.dataid.gap.time.ms";
    public final static String ROUTING_STALE_GAP_BUSY_EXPIRE_TIME = "routing.stale.gap.busy.expire.time.ms";
    public final static String ROUTING_LARGEST_GAP_SIZE = "routing.largest.gap.size";
    public final static String ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED = "routing.data.reader.order.by.gap.id.enabled";
    public final static String ROUTING_DATA_READER_INTO_MEMORY_ENABLED = "routing.data.reader.into.memory.enabled";
    public final static String ROUTING_DATA_READER_THRESHOLD_GAPS_TO_USE_GREATER_QUERY = "routing.data.reader.threshold.gaps.to.use.greater.than.query";
    public final static String ROUTING_DATA_READER_USE_MULTIPLE_QUERIES = "routing.data.reader.use.multiple.queries";
    public final static String ROUTING_LOG_STATS_ON_BATCH_ERROR = "routing.log.stats.on.batch.error";
    public final static String ROUTING_COLLECT_STATS_UNROUTED = "routing.collect.stats.unrouted";
    public final static String ROUTING_USE_FAST_GAP_DETECTOR = "routing.use.fast.gap.detector";
    public final static String ROUTING_DETECT_INVALID_GAPS = "routing.detect.invalid.gaps";
    public final static String ROUTING_QUERY_CHANNELS_FIRST = "routing.query.channels.first";
    public final static String ROUTING_MAX_GAP_CHANGES = "routing.max.gap.changes";
    public final static String ROUTING_USE_COMMON_GROUPS = "routing.use.common.groups";
    public final static String ROUTING_USE_NON_COMMON_FOR_INCOMING = "routing.use.non.common.for.incoming";
    public final static String ROUTING_GAPS_USE_TRANSACTION_VIEW = "routing.gaps.use.transaction.view";
    public final static String ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS = "routing.gaps.transaction.view.clock.sync.threshold";
    public final static String ROUTING_MAX_BATCH_SIZE_EXCEED_PERCENT = "routing.max.batch.size.exceed.percent";
    public final static String ROUTING_USE_CHANNEL_THREADS = "routing.use.channel.threads";
    public final static String ROUTING_THREAD_COUNT_PER_SERVER = "routing.thread.per.server.count";
    public final static String ROUTING_LOCK_TIMEOUT_MS = "routing.lock.timeout.ms";
    public final static String INCOMING_BATCH_SKIP_DUPLICATE_BATCHES_ENABLED = "incoming.batches.skip.duplicates";
    @Deprecated
    public final static String INCOMING_BATCH_DELETE_ON_LOAD = "incoming.batch.delete.on.load";
    public final static String INCOMING_BATCH_RECORD_OK_ENABLED = "incoming.batches.record.ok.enabled";
    public final static String INCOMING_BATCHES_USE_SOURCE_STAGING = "incoming.batches.use.source.staging";
    public final static String DATA_LOADER_ENABLED = "dataloader.enable";
    public final static String DATA_LOADER_APPLY_CHANGES_ONLY = "dataloader.apply.changes.only";
    public final static String DATA_LOADER_IGNORE_MISSING_TABLES = "dataloader.ignore.missing.tables";
    public final static String DATA_LOADER_FIT_TO_COLUMN = "dataloader.fit.to.column";
    public final static String DATA_LOADER_ERROR_RECORD_CUR_VAL = "dataloader.error.save.curval";
    public final static String DATA_LOADER_NUM_OF_ACK_RETRIES = "num.of.ack.retries";
    public final static String DATA_LOADER_SEND_ACK_KEEPALIVE = "send.ack.keepalive.ms";
    public final static String DATA_LOADER_TIME_BETWEEN_ACK_RETRIES = "time.between.ack.retries.ms";
    public final static String DATA_LOADER_MAX_ROWS_BEFORE_COMMIT = "dataloader.max.rows.before.commit";
    public final static String DATA_LOADER_CREATE_TABLE_ALTER_TO_MATCH_DB_CASE = "dataloader.create.table.alter.to.match.db.case";
    public final static String DATA_LOADER_CREATE_TABLE_WITHOUT_DEFAULTS_ON_ERROR = "dataloader.create.table.without.defaults.on.error";
    public final static String DATA_LOADER_TEXT_COLUMN_EXPRESSION = "dataloader.text.column.expression";
    public final static String DATA_LOADER_SLEEP_TIME_AFTER_EARLY_COMMIT = "dataloader.sleep.time.after.early.commit";
    public final static String DATA_LOADER_TREAT_DATETIME_AS_VARCHAR = "db.treat.date.time.as.varchar.enabled";
    public final static String DATA_LOADER_TREAT_BIT_AS_INTEGER = "db.treat.bit.as.integer.enabled";
    public final static String DATA_LOADER_USE_PRIMARY_KEYS_FROM_SOURCE = "dataloader.use.primary.keys.from.source";
    public final static String DATA_LOADER_IGNORE_SQL_EVENT_ERRORS = "dataloader.ignore.sql.event.errors";
    public final static String DATA_LOADER_SQL_EVENT_STRIP_COMMENTS = "dataloader.sql.event.strip.comments";
    public final static String DATA_LOADER_LOG_SQL_PARAMS_ON_ERROR = "dataloader.log.sql.params.on.error";
    public final static String DATA_RELOAD_IS_BATCH_INSERT_TRANSACTIONAL = "datareload.batch.insert.transactional";
    public final static String DATA_EXTRACTOR_ENABLED = "dataextractor.enable";
    public final static String DATA_EXTRACTOR_TEXT_COLUMN_EXPRESSION = "dataextractor.text.column.expression";
    public final static String DATA_FLUSH_JDBC_BATCH_SIZE = "data.flush.jdbc.batch.size";
    public final static String OUTGOING_BATCH_MAX_BATCHES_TO_SELECT = "outgoing.batches.max.to.select";
    public final static String DBDIALECT_ORACLE_USE_TRANSACTION_VIEW = "oracle.use.transaction.view";
    public final static String DBDIALECT_ORACLE_TEMPLATE_NUMBER_SPEC = "oracle.template.precision";
    public final static String DBDIALECT_ORACLE_TEMPLATE_NUMBER_TEXT_MINIMUM = "oracle.template.precision.text.minimum";
    public final static String DBDIALECT_ORACLE_USE_HINTS = "oracle.use.hints";
    public final static String DBDIALECT_ORACLE_USE_SELECT_START_DATA_ID_HINT = "oracle.use.select.data.using.start.data.id.hint";
    public final static String DBDIALECT_ORACLE_SEQUENCE_NOORDER = "oracle.sequence.noorder";
    public final static String DBDIALECT_ORACLE_SEQUENCE_NOORDER_NEXTVALUE_DB_URLS = "oracle.sequence.noorder.nextvalue.db.urls";
    public final static String DBDIALECT_ORACLE_LOAD_QUERY_HINT_PARALLEL_COUNT = "oracle.load.query.hint.parallel.count";
    public final static String DBDIALECT_ORACLE_USE_NTYPES_FOR_SYNC = "oracle.use.ntypes.for.sync";
    public final static String DBDIALECT_ORACLE_JDBC_LOB_HANDLING = "oracle.jdbc.lob.handling";
    public final static String DBDIALECT_TIBERO_USE_TRANSACTION_VIEW = "tibero.use.transaction.view";
    public final static String DBDIALECT_TIBERO_TEMPLATE_NUMBER_SPEC = "tibero.template.precision";
    public final static String DBDIALECT_TIBERO_USE_HINTS = "tibero.use.hints";
    public final static String DBDIALECT_ORACLE_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS = "oracle.transaction.view.clock.sync.threshold.ms";
    public final static String DATA_ID_INCREMENT_BY = "data.id.increment.by";
    public final static String TRANSPORT_HTTP_MANUAL_REDIRECTS_ENABLED = "http.manual.redirects.enabled";
    public final static String TRANSPORT_HTTP_TIMEOUT = "http.timeout.ms";
    public final static String TRANSPORT_HTTP_CONNECT_TIMEOUT = "http.connect.timeout.ms";
    public final static String TRANSPORT_HTTP_PUSH_STREAM_ENABLED = "http.push.stream.output.enabled";
    public final static String TRANSPORT_HTTP_PUSH_STREAM_SIZE = "http.push.stream.output.size";
    public final static String TRANSPORT_HTTP_USE_COMPRESSION_CLIENT = "http.compression";
    public final static String TRANSPORT_HTTP_COMPRESSION_DISABLED_SERVLET = "web.compression.disabled";
    public final static String TRANSPORT_HTTP_COMPRESSION_LEVEL = "compression.level";
    public final static String TRANSPORT_HTTP_COMPRESSION_STRATEGY = "compression.strategy";
    public final static String TRANSPORT_HTTP_USE_SESSION_AUTH = "http.use.session.auth";
    public final static String TRANSPORT_HTTP_SESSION_EXPIRE_SECONDS = "http.session.expire.seconds";
    public final static String TRANSPORT_HTTP_SESSION_MAX_COUNT = "http.session.max.count";
    public final static String TRANSPORT_HTTP_USE_HEADER_SECURITY_TOKEN = "http.use.header.security.token";
    public final static String TRANSPORT_TYPE = "transport.type";
    public final static String TRANSPORT_MAX_BYTES_TO_SYNC = "transport.max.bytes.to.sync";
    public final static String TRANSPORT_MAX_FORM_KEYS = "transport.max.form.keys";
    public final static String TRANSPORT_MAX_ERROR_MILLIS = "transport.max.error.millis";
    public final static String CACHE_TIMEOUT_GROUPLETS_IN_MS = "cache.grouplets.time.ms";
    public final static String CACHE_TIMEOUT_NODE_SECURITY_IN_MS = "cache.node.security.time.ms";
    public final static String CACHE_TIMEOUT_NODE_IN_MS = "cache.node.time.ms";
    public final static String CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS = "cache.trigger.router.time.ms";
    public final static String CACHE_TIMEOUT_CHANNEL_IN_MS = "cache.channel.time.ms";
    public final static String CACHE_TIMEOUT_READY_QUEUE_IN_MS = "cache.ready.queue.time.ms";
    public final static String CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS = "cache.node.group.link.time.ms";
    public final static String CACHE_TIMEOUT_TRANSFORM_IN_MS = "cache.transform.time.ms";
    public final static String CACHE_TIMEOUT_LOAD_FILTER_IN_MS = "cache.load.filter.time.ms";
    public final static String CACHE_TIMEOUT_CONFLICT_IN_MS = "cache.conflict.time.ms";
    public final static String CACHE_TIMEOUT_TABLES_IN_MS = "cache.table.time.ms";
    public final static String CACHE_CHANNEL_COMMON_BATCHES_IN_MS = "cache.channel.common.batches.time.ms";
    public final static String CACHE_CHANNEL_DEFAULT_ROUTER_IN_MS = "cache.channel.default.router.time.ms";
    public final static String TRIGGER_UPDATE_CAPTURE_CHANGED_DATA_ONLY = "trigger.update.capture.changed.data.only.enabled";
    public final static String TRIGGER_CREATE_BEFORE_INITIAL_LOAD = "trigger.create.before.initial.load.enabled";
    public final static String TRIGGER_CAPTURE_DDL_CHANGES = "trigger.capture.ddl.changes";
    public final static String TRIGGER_CAPTURE_DDL_DELIMITER = "trigger.capture.ddl.delimiter";
    public final static String TRIGGER_CAPTURE_DDL_CHECK_TRIGGER_HIST = "trigger.capture.ddl.check.trigger.hist";
    public final static String TRIGGER_USE_INSERT_DELETE_FOR_PRIMARY_KEY_CHANGES = "trigger.use.insert.delete.for.primary.key.changes";
    public final static String DB_METADATA_IGNORE_CASE = "db.metadata.ignore.case";
    public final static String DB_NATIVE_EXTRACTOR = "db.native.extractor";
    public final static String DB_QUERY_TIMEOUT_SECS = "db.sql.query.timeout.seconds";
    public final static String DB_FETCH_SIZE = "db.jdbc.streaming.results.fetch.size";
    public final static String DB_DELIMITED_IDENTIFIER_MODE = "db.delimited.identifier.mode";
    public final static String DB_JNDI_NAME = "db.jndi.name";
    public final static String DB_SPRING_BEAN_NAME = "db.spring.bean.name";
    public final static String RUNTIME_CONFIG_TRIGGER_PREFIX = "sync.trigger.prefix";
    public final static String RUNTIME_CONFIG_TABLE_PREFIX = "sync.table.prefix";
    public final static String NODE_ID_CREATOR_SCRIPT = "node.id.creator.script";
    public final static String NODE_ID_CREATOR_MAX_NODES = "node.id.creator.max.nodes";
    public final static String EXTERNAL_ID_IS_UNIQUE = "external.id.is.unique.enabled";
    public final static String CLUSTER_SERVER_ID = "cluster.server.id";
    public final static String CLUSTER_LOCKING_ENABLED = "cluster.lock.enabled";
    public final static String CLUSTER_STAGING_ENABLED = "cluster.staging.enabled";
    public final static String CLUSTER_LOCK_TIMEOUT_MS = "cluster.lock.timeout.ms";
    public final static String CLUSTER_LOCK_REFRESH_MS = "cluster.lock.refresh.ms";
    public final static String LOCK_TIMEOUT_MS = "lock.timeout.ms";
    public final static String LOCK_WAIT_RETRY_MILLIS = "lock.wait.retry.ms";
    public final static String PURGE_LOG_SUMMARY_MINUTES = "purge.log.summary.retention.minutes";
    public final static String PURGE_RETENTION_MINUTES = "purge.retention.minutes";
    public final static String PURGE_EXTRACT_REQUESTS_RETENTION_MINUTES = "purge.extract.request.retention.minutes";
    public final static String PURGE_REGISTRATION_REQUEST_RETENTION_MINUTES = "purge.registration.request.retention.minutes";
    public final static String PURGE_STATS_RETENTION_MINUTES = "purge.stats.retention.minutes";
    public final static String PURGE_TRIGGER_HIST_RETENTION_MINUTES = "purge.trigger.hist.retention.minutes";
    public final static String PURGE_EXPIRED_DATA_GAP_RETENTION_MINUTES = "purge.expired.data.gap.retention.minutes";
    public final static String PURGE_MONITOR_EVENT_RETENTION_MINUTES = "purge.monitor.event.retention.minutes";
    public final static String PURGE_MAX_NUMBER_OF_DATA_IDS = "job.purge.max.num.data.to.delete.in.tx";
    public final static String PURGE_MAX_NUMBER_OF_BATCH_IDS = "job.purge.max.num.batches.to.delete.in.tx";
    public final static String PURGE_MAX_NUMBER_OF_EVENT_BATCH_IDS = "job.purge.max.num.data.event.batches.to.delete.in.tx";
    public final static String PURGE_MAX_LINGERING_BATCHES_READ = "job.purge.max.lingering.batches.read";
    public final static String PURGE_MAX_EXPIRED_DATA_GAPS_READ = "job.purge.max.data.gaps.read";
    public final static String PURGE_FIRST_PASS = "job.purge.first.pass";
    public final static String PURGE_FIRST_PASS_OUTSTANDING_BATCHES_THRESHOLD = "job.purge.first.pass.outstanding.batches.threshold";
    public final static String JMX_LINE_FEED = "jmx.line.feed";
    public final static String IP_FILTERS = "ip.filters";
    public final static String NODE_COPY_MODE_ENABLED = "node.copy.mode.enabled";
    public final static String NODE_PASSWORD_FAILED_ATTEMPTS = "node.password.failed.attempts";
    public final static String USER_PASSWORD_FAILED_ATTEMPTS = "console.password.failed.attempts";
    public final static String NODE_OFFLINE = "node.offline";
    public final static String NODE_OFFLINE_INCOMING_DIR = "node.offline.incoming.dir";
    public final static String NODE_OFFLINE_INCOMING_ACCEPT_ALL = "node.offline.incoming.accept.all";
    public final static String NODE_OFFLINE_OUTGOING_DIR = "node.offline.outgoing.dir";
    public final static String NODE_OFFLINE_ERROR_DIR = "node.offline.error.dir";
    public final static String NODE_OFFLINE_ARCHIVE_DIR = "node.offline.archive.dir";
    public final static String OFFLINE_NODE_DETECTION_PERIOD_MINUTES = "offline.node.detection.period.minutes";
    public final static String OFFLINE_NODE_DETECTION_RESTART_MINUTES = "offline.node.detection.restart.minutes";
    public final static String HEARTBEAT_SYNC_ON_PUSH_PERIOD_SEC = "heartbeat.sync.on.push.period.sec";
    public final static String HEARTBEAT_JOB_PERIOD_MS = "job.heartbeat.period.time.ms";
    public final static String HEARTBEAT_SYNC_ON_STARTUP = "heartbeat.sync.on.startup";
    public final static String HEARTBEAT_UPDATE_NODE_WITH_BATCH_STATUS = "heartbeat.update.node.with.batch.status";
    public final static String HEARTBEAT_ENABLED = "heartbeat.sync.on.push.enabled";
    public final static String STATISTIC_RECORD_ENABLE = "statistic.record.enable";
    public final static String STATISTIC_RECORD_COUNT_THRESHOLD = "statistic.record.count.threshold";
    public final static String CURRENT_ACTIVITY_HISTORY_KEEP_COUNT = "statistic.activity.history.keep.count";
    public final static String STORES_UPPERCASE_NAMES_IN_CATALOG = "stores.uppercase.names.in.catalog";
    public final static String DB_MASTER_COLLATION = "db.master.collation";
    public final static String SEQUENCE_TIMEOUT_MS = "sequence.timeout.ms";
    public final static String SYNCHRONIZE_ALL_JOBS = "jobs.synchronized.enable";
    public final static String FILE_SYNC_ENABLE = "file.sync.enable";
    public final static String FILE_SYNC_FAST_SCAN = "file.sync.fast.scan";
    public final static String FILE_SYNC_USE_CRC = "file.sync.use.crc";
    public final static String FILE_SYNC_PREVENT_PING_BACK = "file.sync.prevent.ping.back";
    public final static String FILE_SYNC_LOCK_WAIT_MS = "file.sync.lock.wait.ms";
    public final static String FILE_SYNC_DELETE_ZIP_FILE_AFTER_SYNC = "file.sync.delete.zip.file.after.sync";
    public final static String FILE_SYNC_DELETE_CTL_FILE_AFTER_SYNC = "file.sync.delete.ctl.file.after.sync";
    public final static String FILE_SYNC_USE_CTL_AS_FILE_EXT = "file.sync.use.ctl.as.file.ext";
    public final static String FILE_SYNC_COMPRESSION_LEVEL = "file.compression.level";
    public final static String FILE_SYNC_RETRY_COUNT = "file.sync.retry.count";
    public final static String FILE_SYNC_RETRY_DELAY_MS = "file.sync.retry.delay.ms";
    public final static String BSH_LOAD_FILTER_HANDLES_MISSING_TABLES = "bsh.load.filter.handles.missing.tables";
    public final static String BSH_TRANSFORM_GLOBAL_SCRIPT = "bsh.transform.global.script";
    public final static String BSH_EXTENSION_GLOBAL_SCRIPT = "bsh.extension.global.script";
    public final static String MSSQL_ROW_LEVEL_LOCKS_ONLY = "mssql.allow.only.row.level.locks.on.runtime.tables";
    public final static String MSSQL_USE_NTYPES_FOR_SYNC = "mssql.use.ntypes.for.sync";
    public final static String MSSQL_USE_VARCHAR_FOR_LOB_IN_SYNC = "mssql.use.varchar.for.lob.in.sync";
    public final static String MSSQL_LOCK_ESCALATION_DISABLED = "mssql.lock.escalation.disabled";
    public final static String MSSQL_INCLUDE_CATALOG_IN_TRIGGERS = "mssql.include.catalog.in.triggers";
    public final static String MSSQL_TRIGGER_EXECUTE_AS = "mssql.trigger.execute.as";
    public final static String MSSQL_TRIGGER_ORDER_FIRST = "mssql.trigger.order.first";
    public final static String MSSQL_USE_SNAPSHOT_ISOLATION = "mssql.use.snapshot.isolation";
    public final static String MSSQL_BULK_EXTRACT_USE_BCP = "mssql.bulk.extract.use.bcp";
    public final static String MSSQL_METADATA_QUERY_FOR_COMPRESSION_FILTERS_INCLUDECOLUMNS = "mssql.metadata.query.for.compression.filters.includecolumns";
    public final static String DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC = "sybase.ase.convert.unitypes.for.sync";
    public final static String SYBASE_ROW_LEVEL_LOCKS_ONLY = "sybase.allow.only.row.level.locks.on.runtime.tables";
    public final static String SYBASE_CHANGE_IDENTITY_GAP = "sybase.change.identity.gap.on.runtime.tables";
    public final static String ASE_BULK_EXTRACT_USE_BCP = "ase.bulk.extract.use.bcp";
    public final static String SQLITE_TRIGGER_FUNCTION_TO_USE = "sqlite.trigger.function.to.use";
    public final static String AS400_CAST_CLOB_TO = "as400.cast.clob.to";
    public final static String EXTENSIONS_XML = "extensions.xml";
    public final static String DATA_CREATE_TIME_TIMEZONE = "data.create_time.timezone";
    public final static String LOG_SLOW_SQL_THRESHOLD_MILLIS = "log.slow.sql.threshold.millis";
    public final static String CONSOLE_LOG_SLOW_SQL_THRESHOLD_MILLIS = "console.log.slow.sql.threshold.millis";
    public final static String LOG_SQL_PARAMETERS_INLINE = "log.sql.parameters.inline";
    public final static String SYNC_TRIGGERS_THREAD_COUNT_PER_SERVER = "sync.triggers.thread.count.per.server";
    public final static String SYNC_TRIGGERS_TIMEOUT_IN_SECONDS = "sync.triggers.timeout.in.seconds";
    public final static String SYNC_TRIGGERS_REG_SVR_INSTALL_WITHOUT_CONFIG = "sync.triggers.reg.svr.install.without.config";
    public final static String SYNC_TRIGGERS_FIX_DUPLICATE_ACTIVE_TRIGGER_HISTORIES = "sync.triggers.fix.duplicate.active.trigger.histories";
    public final static String MONITOR_EVENTS_CAPTURE_ENABLED = "monitor.events.capture.enabled";
    public final static String HYBRID_PUSH_PULL_ENABLED = "hybrid.push.pull.enabled";
    public final static String HYBRID_PUSH_PULL_TIMEOUT = "hybrid.push.pull.timeout.ms";
    public final static String HYBRID_PUSH_PULL_BUFFER_STATUS_UPDATES = "hybrid.push.pull.buffer.status.updates";
    public final static String DBF_ROUTER_VALIDATE_HEADER = "dbf.router.validate.header";
    public final static String OUTGOING_BATCH_UPDATE_STATUS_MILLIS = "outgoing.batches.update.status.millis";
    public final static String OUTGOING_BATCH_UPDATE_STATUS_DATA_COUNT = "outgoing.batches.update.status.data.count";
    public final static String FIREBIRD_EXTRACT_VARCHAR_ROW_OLD_PK_DATA = "firebird.extract.varchar.row.old.pk.data";
    public final static String GROUPLET_ENABLE = "grouplet.enable";
    public final static String CHECK_SOFTWARE_UPDATES = "check.software.updates";
    public final static String SEND_USAGE_STATS = "send.usage.stats";
    public final static String LOG_CONFLICT_RESOLUTION = "log.conflict.resolution";
    public final static String CONFLICT_DEFAULT_PK_WITH_FALLBACK = "conflict.default.pk.with.fallback";
    public final static String UPDATE_SERVICE_CLASS = "update.service.class";
    public final static String STAGING_MANAGER_CLASS = "staging.manager.class";
    public final static String STAGING_DIR = "staging.dir";
    public final static String STAGING_LOW_SPACE_THRESHOLD_MEGABYTES = "staging.low.space.threshold.megabytes";
    public final static String STATISTIC_MANAGER_CLASS = "statistic.manager.class";
    public final static String DB2_CAPTURE_TRANSACTION_ID = "db2.capture.transaction.id";
    public final static String TREAT_BINARY_AS_LOB_ENABLED = "treat.binary.as.lob.enabled";
    public final static String RIGHT_TRIM_CHAR_VALUES = "right.trim.char.values";
    public final static String ALLOW_UPDATES_WITH_RESULTS = "allow.updates.with.results";
    public final static String ALLOW_TRIGGER_CREATE_OR_REPLACE = "trigger.allow.create.or.replace";
    public final static String NODE_LOAD_ONLY = "load.only";
    public final static String MYSQL_TINYINT_DDL_TO_BOOLEAN = "mysql.tinyint.ddl.to.boolean";
    public static final String LOAD_ONLY_PROPERTY_PREFIX = "target.";
    public final static String KAFKA_PRODUCER = "kafka.producer";
    public final static String KAFKA_FORMAT = "kafka.format";
    public final static String KAFKA_MESSAGE_BY = "kafka.message.by";
    public final static String KAFKA_TOPIC_BY = "kafka.topic.by";
    public final static String KAFKA_CONFLUENT_REGISTRY_URL = "kafka.confluent.registry.url";
    public final static String KAFKA_AVRO_JAVA_PACKAGE = "kafka.avro.java.package";
    public final static String KAFKACLIENT_SECURITY_PROTOCOL = "kafkaclient.security.protocol";
    public final static String KAFKACLIENT_SSL_KEYSTORE_LOCATION = "kafkaclient.ssl.keystore.location";
    public final static String KAFKACLIENT_SSL_KEYSTORE_PASSWORD = "kafkaclient.ssl.keystore.password";
    public final static String KAFKACLIENT_SSL_TRUSTSTORE_LOCATION = "kafkaclient.ssl.truststore.location";
    public final static String KAFKACLIENT_SSL_KEYSTORE_TYPE = "kafkaclient.ssl.keystore.type";
    public final static String[] ALL_KAFKA_PARAMS = new String[] { KAFKA_PRODUCER, KAFKA_FORMAT, KAFKA_MESSAGE_BY,
            KAFKA_TOPIC_BY, KAFKA_CONFLUENT_REGISTRY_URL, KAFKA_AVRO_JAVA_PACKAGE, KAFKACLIENT_SECURITY_PROTOCOL,
            KAFKACLIENT_SSL_KEYSTORE_LOCATION, KAFKACLIENT_SSL_KEYSTORE_PASSWORD, KAFKACLIENT_SSL_TRUSTSTORE_LOCATION,
            KAFKACLIENT_SSL_KEYSTORE_TYPE };
    public final static String RABBITMQ_FORMAT = "rabbitmq.format";
    public final static String RABBITMQ_QUEUE_NAME = "rabbitmq.queue.name";
    public final static String RABBITMQ_MESSAGE_BY = "rabbitmq.message.by";
    public final static String RABBITMQ_QUEUE_BY = "rabbitmq.queue.by";
    public final static String RABBITMQ_USE_SSL = "rabbitmq.use.ssl";
    public final static String SNOWFLAKE_STAGING_TYPE = "snowflake.staging.type";
    public final static String SNOWFLAKE_INTERNAL_STAGE_NAME = "snowflake.internal.stage.name";
    public final static String SNAPSHOT_FILE_INCLUDE_HOSTNAME = "snapshot.file.include.hostname";
    public final static String SNAPSHOT_MAX_FILES = "snapshot.max.files";
    public final static String SNAPSHOT_MAX_BATCHES = "snapshot.max.batches";
    public final static String SNAPSHOT_MAX_NODE_CHANNELS = "snapshot.max.node.channels";
    public final static String SNAPSHOT_OPERATION_TIMEOUT_MS = "snapshot.operation.timeout.ms";
    public final static String POSTGRES_TRIGGER_CAPTURE_TRUNCATE = "postgres.trigger.capture.truncate.event";
    public final static String POSTGRES_SECURITY_DEFINER = "postgres.security.definer";
    public final static String POSTGRES_CONVERT_INFINITY_DATE_TO_NULL = "postgres.convert.infinity.date.to.null";
    public final static String[] STARTUP_DB_OBJECTS_SETUP_PARAMS = new String[] { TRIGGER_CAPTURE_DDL_CHANGES,
            POSTGRES_TRIGGER_CAPTURE_TRUNCATE, TRIGGER_CAPTURE_DDL_CHECK_TRIGGER_HIST, TRIGGER_CAPTURE_DDL_DELIMITER,
            DB_USER, "db.url", "target.db.url" };
    public final static String[] ALL_JDBC_PARAMS = new String[] { DB_FETCH_SIZE, DB_QUERY_TIMEOUT_SECS, JDBC_EXECUTE_BATCH_SIZE, JDBC_ISOLATION_LEVEL,
            JDBC_READ_STRINGS_AS_BYTES, TREAT_BINARY_AS_LOB_ENABLED, LOG_SLOW_SQL_THRESHOLD_MILLIS, LOG_SQL_PARAMETERS_INLINE };
    public final static String GOOGLE_BIG_QUERY_MAX_ROWS_PER_RPC = "google.bigquery.max.rows.per.rpc";
    public final static String GOOGLE_BIG_QUERY_LOCATION = "google.bigquery.location";
    public final static String GOOGLE_BIG_QUERY_PROJECT_ID = "google.bigquery.project.id";
    public final static String GOOGLE_BIG_QUERY_SECURITY_CREDENTIALS_PATH = "google.bigquery.security.credentials.path";
    public final static String[] ALL_GOOGLE_BIG_QUERY_PARAMS = new String[] { GOOGLE_BIG_QUERY_MAX_ROWS_PER_RPC, GOOGLE_BIG_QUERY_LOCATION,
            GOOGLE_BIG_QUERY_PROJECT_ID, GOOGLE_BIG_QUERY_SECURITY_CREDENTIALS_PATH };
    public final static String HBASE_SITE_XML_PATH = "hbase.site.xml.path";
    public final static String MONGO_DEFAULT_DATABASE = "mongodb.default.database";
    public final static String MONGO_USE_MONGO_IDS = "mongodb.use.mongo.ids";
    public final static String[] ALL_MONGODB_PARAMS = new String[] { MONGO_DEFAULT_DATABASE, MONGO_USE_MONGO_IDS };
    public final static String COSMOS_DEFAULT_DATABASE = "cosmosdb.default.database";
    public final static String[] ALL_COSMOS_PARAMS = new String[] { COSMOS_DEFAULT_DATABASE };
    public final static String OPENSEARCH_LOAD_AWS_ACCESS_KEY = "opensearch.load.aws.access.key";
    public final static String OPENSEARCH_LOAD_AWS_SECRET_KEY = "opensearch.load.aws.secret.key";
    public final static String S3_LOAD_AWS_ACCESS_KEY = "s3.load.aws.access.key";
    public final static String S3_LOAD_AWS_SECRET_KEY = "s3.load.aws.secret.key";
    public final static String S3_LOAD_FORMAT = "s3.load.format";
    public final static String SINGLESTORE_AUDIT_LOG_DIR = "single.store.audit.log.dir";
    public final static String SPATIAL_TYPES_ENABLED = "spatial.data.types.enabled";
    public final static String DEFAULT_VALUES_TO_LEAVE_UNQUOTED = "default.values.to.leave.unquoted";
    public final static String DEFAULT_VALUES_TO_TRANSLATE = "default.values.to.translate";
    public final static String INCLUDE_ROWIDENTIFIER_AS_COLUMN = "include.rowidentifier.as.column";
    public final static String COMPARE_QUEUE_PER_REQUEST_COUNT = "compare.queue.per.request.count";
    public final static String COMPARE_THREAD_PER_SERVER_COUNT = "compare.thread.per.server.count";
    public final static String COMPARE_LOCK_TIMEOUT_MS = "compare.lock.timeout.ms";
    public final static String CAPTURE_TYPE_TIME_BASED = "time.based.capture";
    public final static String FILESYNCTRACKER_MAX_ROWS_BEFORE_COMMIT = "filesynctracker.max.rows.before.commit";
    public final static String SYNC_USE_READY_QUEUES = "sync.use.ready.queues";
    public final static String KEEP_BULK_STAGING_FILES = "keep.bulk.staging.files";
    public final static String MSSQL_BULK_LOAD_BCP_CODE_PAGE = "mssql.bulk.load.bcp.code.page";

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