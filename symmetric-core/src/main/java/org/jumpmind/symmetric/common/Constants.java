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
package org.jumpmind.symmetric.common;

import org.jumpmind.symmetric.io.IoConstants;
import org.jumpmind.symmetric.io.data.reader.ExtractDataReader;
import org.jumpmind.symmetric.model.DataEvent;

/**
 * General purpose constants used by SymmetricDS
 */
final public class Constants {
    private Constants() {
    }

    public static final String SYSTEM_USER = "system";
    public static final long VIRTUAL_BATCH_FOR_REGISTRATION = IoConstants.IGNORE_TABLES_BATCH;
    public static final long BATCH_ID_MISSING = -1;
    public static final String NONE_TOKEN = "$(none)";
    public static final String NULL_TOKEN = "$(null)";
    public static final String DATA_CONTEXT_ENGINE = "engine";
    public static final String DATA_CONTEXT_TARGET_NODE = "targetNode";
    public static final String DATA_CONTEXT_TARGET_NODE_ID = "targetNodeId";
    public static final String DATA_CONTEXT_TARGET_NODE_GROUP_ID = "targetNodeGroupId";
    public static final String DATA_CONTEXT_TARGET_NODE_EXTERNAL_ID = "targetNodeExternalId";
    public static final String DATA_CONTEXT_CURRENT_CSV_DATA = ExtractDataReader.DATA_CONTEXT_CURRENT_CSV_DATA;
    public static final String DATA_CONTEXT_SOURCE_NODE = "sourceNode";
    public static final String DATA_CONTEXT_SOURCE_NODE_ID = "sourceNodeId";
    public static final String DATA_CONTEXT_SOURCE_NODE_GROUP_ID = "sourceNodeGroupId";
    public static final String DATA_CONTEXT_SOURCE_NODE_EXTERNAL_ID = "sourceNodeExternalId";
    public static final String STAGING_CATEGORY_OUTGOING = "outgoing";
    public static final String STAGING_CATEGORY_INCOMING = "incoming";
    public static final String STAGING_CATEGORY_LOG_MINER = "logminer";
    public static final String STAGING_CATEGORY_BULK_LOAD = "bulkloaddir";
    public static final String STAGING_CATEGORY_BULK_EXTRACT = "bulkextract";
    public static final String STAGING_CATEGORY_COMPARE = "compare";
    public static final String CLIENT_SPRING_XML = "classpath:/symmetric-client.xml";
    public static final String SERVER_SPRING_XML = "classpath:/symmetric-server.xml";
    public static final String PLEASE_SET_ME = "please set me";
    public static final String DEPLOYMENT_TYPE_SERVER = "server";
    public static final String DEPLOYMENT_TYPE_PROFESSIONAL = "professional";
    public static final String DEPLOYMENT_TYPE_CCLIENT = "cclient";
    public static final String DEPLOYMENT_TYPE_REST = "rest";
    public static final String DEPLOYMENT_SUB_TYPE_LOAD_ONLY = "load-only";
    public static final String DEPLOYMENT_SUB_TYPE_EXTRACT_ONLY = "extract-only";
    public static final String DEPLOYMENT_SUB_TYPE_LOG_BASED = "log-based";
    public static final String DEPLOYMENT_SUB_TYPE_TRIGGER_BASED = "trigger-based";
    public static final String DEPLOYMENT_SUB_TYPE_TIME_BASED = "time-based";
    /**
     * Use this value for the router_id in {@link DataEvent} if the router is unknown.
     */
    public static final String UNKNOWN_ROUTER_ID = "?";
    public static final String UNKNOWN_STRING = "unknown";
    public static final String NO_GROUP = "nogroup";
    public static final String ALWAYS_TRUE_CONDITION = "1=1";
    public static final String UNROUTED_NODE_ID = "-1";
    public static final long LONG_OPERATION_THRESHOLD = 30000;
    public static final long LOG_PROCESS_SUMMARY_THRESHOLD = 60000;
    public static final String SYMMETRIC_ENGINE = "symmetricEngine";
    public static final String MBEAN_SERVER = "mbeanserver";
    public static final String CHANNEL_HEARTBEAT = "heartbeat";
    public static final String CHANNEL_MONITOR = "monitor";
    public static final String CHANNEL_CONFIG = "config";
    public static final String CHANNEL_SYSTEM = "system";
    public static final String CHANNEL_RELOAD = "reload";
    public static final String CHANNEL_DEFAULT = "default";
    public static final String CHANNEL_FILESYNC = "filesync";
    public static final String CHANNEL_FILESYNC_RELOAD = "filesync_reload";
    public static final String CHANNEL_DYNAMIC = "dynamic";
    public static final String QUEUE_DEFAULT = "default";
    public static final String QUEUE_SYSTEM = "system";
    public static final String QUEUE_RELOAD = "reload";
    public static final String DELIMITER_QUEUE_THREAD = "!";
    public static final String PUSH_JOB_TIMER = "job.push";
    public static final String PULL_JOB_TIMER = "job.pull";
    public static final String ROUTE_JOB_TIMER = "job.routing";
    public static final String PURGE_JOB_TIMER = "job.purge";
    public static final String HEARTBEAT_JOB_TIMER = "job.heartbeat";
    public static final String SYNC_TRIGGERS_JOB_TIMER = "job.synctriggers";
    public static final String STATISTIC_FLUSH_JOB_TIMER = "job.stat.flush";
    public static final String WATCHDOG_JOB_TIMER = "job.watchdog";
    public static final String DATA_EXTRACTOR = "dataExtractor";
    public static final String OUTGOING_BATCH_SERVICE = "outgoingBatchService";
    public static final String TRANSACTION_TEMPLATE = "transactionTemplate";
    public static final String PURGE_SERVICE = "purgeService";
    public static final String JDBC_TEMPLATE = "jdbcTemplate";
    public static final String PARENT_PROPERTY_PREFIX = "parent.";
    public static final String DOWNLOAD_RATE = "downloadRateKb";
    public static final String MAX_CONCURRENT_WORKERS = "maxConcurrentWorkers";
    public static final String PROTOCOL_NONE = "nop";
    public static final String PROTOCOL_HTTP = "http";
    public static final String PROTOCOL_INTERNAL = "internal";
    public static final String PROTOCOL_FILE = "file";
    public static final String PROTOCOL_EXT = "ext";
    public static final String PROTOCOL_HYBRID = "hybrid";
    public static final String TRANSPORT_HTTPS_VERIFIED_SERVERS_ALL = "all";
    public static final String TRANSFORM_SERVICE = "transformService";
    public static final String SEQUENCE_TRIGGER_HIST = TableConstants.SYM_TRIGGER_HIST;
    public static final String SEQUENCE_EXTRACT_REQ = TableConstants.SYM_EXTRACT_REQUEST;
    public static final String SEQUENCE_OUTGOING_BATCH = TableConstants.SYM_OUTGOING_BATCH;
    public static final String SEQUENCE_OUTGOING_BATCH_LOAD_ID = "outgoing_batch_load_id";
    public static final String SEQUENCE_COMPARE_ID = "compare_id";
    public static final String SEND_SCHEMA_EXCLUDE_INDICES = "excludeIndices";
    public static final String SEND_SCHEMA_EXCLUDE_FOREIGN_KEYS = "excludeForeignKeys";
    public static final String SEND_SCHEMA_EXCLUDE_DEFAULTS = "excludeDefaults";
}