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
package org.jumpmind.symmetric.web;

/**
 * Constants that are related to the HTTP transport
 */
public class WebConstants {
    public static final String HEADER_ACCEPT_CHARSET = "Accept-Charset";
    public static final String HEADER_READY_QUEUES = "Ready-Queues";
    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_HEAD = "HEAD";
    public static final String INIT_PARAM_AUTO_START = "autoStart";
    public static final String INIT_PARAM_AUTO_CREATE = "autoCreate";
    public static final String INIT_PARAM_MULTI_SERVER_MODE = "multiServerMode";
    public static final String INIT_PARAM_STATIC_ENGINES_MODE = "staticEnginesMode";
    public static final String INIT_PARAM_DEPLOYMENT_TYPE = "deploymentType";
    public static final String INIT_SINGLE_SERVER_PROPERTIES_FILE = "singleServerPropertiesFile";
    public static final String INIT_SINGLE_USE_WEBAPP_CONTEXT = "useWebApplicationContext";
    public static final String ATTR_ENGINE_HOLDER = "symmetricEngineHolder";
    public static final int REGISTRATION_NOT_OPEN = 656;
    public static final int REGISTRATION_REQUIRED = 657;
    public static final int REGISTRATION_PENDING = 667;
    public static final int INITIAL_LOAD_PENDING = 668;
    public static final int SYNC_DISABLED = 658;
    public static final int SC_FORBIDDEN = 659;
    public static final int SC_AUTH_EXPIRED = 669;
    public static final int SC_SERVICE_UNAVAILABLE = 660;
    public static final int SC_SERVICE_BUSY = 670;
    public static final int SC_SERVICE_NOT_READY = 671;
    public static final int SC_SERVICE_ERROR = 601;
    public static final int SC_NO_RESERVATION = 604;
    public static final int SC_ALREADY_CONNECTED = 605;
    public static final int SC_NO_ENGINE = 602;
    public static final int SC_BAD_REQUEST = 603;
    public static final int SC_INTERNAL_ERROR = 600;
    public static final int SC_NO_CONTENT = 204;
    public static final int SC_OK = 200;
    public static final String ACK_BATCH_NAME = "batch-";
    public static final String ACK_BATCH_OK = "ok";
    public static final String ACK_BATCH_RESEND = "resend";
    public static final String ACK_BULK_LOADER_FLAG = "bulkLoader-";
    public static final String ACK_NODE_ID = "nodeId-";
    public static final String ACK_NETWORK_MILLIS = "network-";
    public static final String ACK_FILTER_MILLIS = "filter-";
    public static final String ACK_DATABASE_MILLIS = "database-";
    public static final String ACK_START_TIME = "startTime-";
    public static final String ACK_BYTE_COUNT = "byteCount-";
    public static final String ACK_IGNORE_COUNT = "ignoreCount-";
    public static final String ACK_SQL_STATE = "sqlState-";
    public static final String ACK_SQL_CODE = "sqlCode-";
    public static final String ACK_SQL_MESSAGE = "sqlMessage-";
    public static final String ACK_LOAD_ROW_COUNT = "loadRowCount-";
    public static final String TRANSFORM_TIME = "transformTime-";
    public static final String ACK_LOAD_INSERT_ROW_COUNT = "loadInsertRowCount-";
    public static final String ACK_LOAD_UPDATE_ROW_COUNT = "loadUpdateRowCount-";
    public static final String ACK_LOAD_DELETE_ROW_COUNT = "loadDeleteRowCount-";
    public static final String ACK_FALLBACK_INSERT_COUNT = "fallbackInsertCount-";
    public static final String ACK_FALLBACK_UPDATE_COUNT = "fallbackUpdateCount-";
    public static final String ACK_CONFLICT_WIN_COUNT = "conflictWinCount-";
    public static final String ACK_CONFLICT_LOSE_COUNT = "conflictLoseCount-";
    public static final String ACK_IGNORE_ROW_COUNT = "ignoreRowCount-";
    public static final String ACK_MISSING_DELETE_COUNT = "missingDeleteCount-";
    public static final String ACK_SKIP_COUNT = "skipCount-";
    public static final String NODE_ID = "nodeId";
    public static final String NODE_GROUP_ID = "nodeGroupId";
    public static final String EXTERNAL_ID = "externalId";
    public static final String SYMMETRIC_VERSION = "symmetricVersion";
    public static final String HOST_NAME = "hostName";
    public static final String IP_ADDRESS = "ipAddress";
    public static final String SYNC_URL = "syncURL";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String DATABASE_TYPE = "databaseType";
    public static final String DATABASE_VERSION = "databaseVersion";
    public static final String DATABASE_NAME = "databaseName";
    public static final String DEPLOYMENT_TYPE = "deploymentType";
    public static final String SECURITY_TOKEN = "securityToken";
    public static final String SUSPENDED_CHANNELS = "Suspended-Channels";
    public static final String IGNORED_CHANNELS = "Ignored-Channels";
    public static final String BATCH_TO_SEND_COUNT = "Batch-To-Send-Count";
    public static final String CHANNEL_QUEUE = "threadChannel";
    public static final String CONFIG_VERSION = "configVersion";
    public static final String SESSION_PREFIX = "JSESSIONID_";
    public static final String HEADER_SECURITY_TOKEN = "Security-Token";
    public static final String HEADER_SESSION_ID = "Session-ID";
    public static final String HEADER_SET_SESSION_ID = "Set-Session-ID";
    public static final String REG_USER_ID = "regUserId";
    public static final String REG_PASSWORD = "regPassword";
    public static final String PUSH_REGISTRATION = "pushRegistration";
    public static final String API_KEY_HEADER = "X-REST-API-KEY";
    public static final String DIRECTION = "direction";
    public static final String SAMPLE_SIZE = "sampleSize";
    public static final String HEADER_DIRECTION = "Direction";
    public static final String HEADER_SAMPLE_SIZE = "Sample-Size";
    public static final String URL_PUSH = "push";
    public static final String URL_PULL = "pull";
    public static final String URL_PING = "ping";
    public static final String URL_REGISTRATION = "registration";
    public static final String URL_COPY = "copy";
    public static final String URL_CONFIG = "config";
    public static final String URL_PUSHSTATUS = "pushstatus";
    public static final String URL_BANDWIDTH = "bandwidth";
    public static final String URL_FILESYNC_PUSH = "filesync/push";
    public static final String URL_FILESYNC_PULL = "filesync/pull";
    public static final String URL_COMPARE_PUSH = "compare/push";
    public static final String URL_COMPARE_PULL = "compare/pull";

    public static String getHttpMessage(int httpCode) {
        String httpMessage = null;
        if (httpCode == REGISTRATION_NOT_OPEN) {
            httpMessage = "Registration is not open";
        } else if (httpCode == REGISTRATION_REQUIRED) {
            httpMessage = "Registration is required";
        } else if (httpCode == REGISTRATION_PENDING) {
            httpMessage = "Registration is pending";
        } else if (httpCode == INITIAL_LOAD_PENDING) {
            httpMessage = "Initial load is pending";
        } else if (httpCode == SYNC_DISABLED) {
            httpMessage = "Sync is disabled";
        } else if (httpCode == SC_FORBIDDEN) {
            httpMessage = "Bad node password";
        } else if (httpCode == SC_AUTH_EXPIRED) {
            httpMessage = "Session expired";
        } else if (httpCode == SC_SERVICE_UNAVAILABLE) {
            httpMessage = "Service is unavailable";
        } else if (httpCode == SC_SERVICE_BUSY) {
            httpMessage = "Service is busy";
        } else if (httpCode == SC_SERVICE_NOT_READY) {
            httpMessage = "Service is not ready";
        } else if (httpCode == SC_SERVICE_ERROR) {
            httpMessage = "Service internal error";
        } else if (httpCode == SC_NO_RESERVATION) {
            httpMessage = "Missing reservation";
        } else if (httpCode == SC_ALREADY_CONNECTED) {
            httpMessage = "Duplicate connection";
        } else if (httpCode == SC_NO_ENGINE) {
            httpMessage = "No engine found";
        } else if (httpCode == SC_BAD_REQUEST) {
            httpMessage = "URI handler not found";
        } else if (httpCode == SC_INTERNAL_ERROR) {
            httpMessage = "Server internal error";
        } else if (httpCode == SC_NO_CONTENT) {
            httpMessage = "No content";
        }
        return httpMessage;
    }
}