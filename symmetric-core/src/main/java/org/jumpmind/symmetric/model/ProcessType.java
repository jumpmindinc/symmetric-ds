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
package org.jumpmind.symmetric.model;

public enum ProcessType {
    ANY, PUSH_JOB_EXTRACT, PUSH_JOB_TRANSFER, PULL_JOB_TRANSFER, PULL_JOB_LOAD, PUSH_HANDLER_TRANSFER, PUSH_HANDLER_LOAD, PULL_HANDLER_TRANSFER, PULL_HANDLER_EXTRACT, REST_PULL_HANLDER, OFFLINE_PUSH, OFFLINE_PULL, ROUTER_JOB, INSERT_LOAD_EVENTS, GAP_DETECT, ROUTER_READER, MANUAL_LOAD, FILE_SYNC_PULL_JOB, FILE_SYNC_PUSH_JOB, FILE_SYNC_PULL_HANDLER, FILE_SYNC_PUSH_HANDLER, FILE_SYNC_TRACKER, INITIAL_LOAD_EXTRACT_JOB, FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB, PULL_CONFIG_JOB, LOG_MINER_JOB, COMPARE_PUT_HANDLE, COMPARE_GET_HANDLE, COMPARE_START_HANDLE, COMPARE_EXECUTE, UNINSTALL;

    public static final ProcessType[] dataSyncProcessTypes = new ProcessType[] { PUSH_JOB_EXTRACT, PUSH_JOB_TRANSFER, PULL_JOB_TRANSFER, PULL_JOB_LOAD,
            PUSH_HANDLER_TRANSFER, PUSH_HANDLER_LOAD, PULL_HANDLER_TRANSFER, PULL_HANDLER_EXTRACT, INSERT_LOAD_EVENTS, INITIAL_LOAD_EXTRACT_JOB };

    @Override
    public String toString() {
        switch (this) {
            case ANY:
                return "<Any>";
            case MANUAL_LOAD:
                return "Manual Load";
            case PUSH_JOB_EXTRACT:
                return "Database Push Extract";
            case PUSH_JOB_TRANSFER:
                return "Database Push Transfer";
            case PULL_JOB_TRANSFER:
                return "Database Pull Transfer";
            case PULL_JOB_LOAD:
                return "Database Pull Load";
            case PULL_CONFIG_JOB:
                return "Config Pull";
            case PUSH_HANDLER_TRANSFER:
                return "Service Database Push Transfer";
            case PULL_HANDLER_TRANSFER:
                return "Service Database Pull Transfer";
            case PUSH_HANDLER_LOAD:
                return "Service Database Push Load";
            case PULL_HANDLER_EXTRACT:
                return "Service Database Pull Extract";
            case OFFLINE_PUSH:
                return "Offline Push";
            case OFFLINE_PULL:
                return "Offline Pull";
            case ROUTER_JOB:
                return "Routing";
            case ROUTER_READER:
                return "Routing Reader";
            case GAP_DETECT:
                return "Gap Detection";
            case FILE_SYNC_PULL_JOB:
                return "File Sync Pull";
            case FILE_SYNC_PUSH_JOB:
                return "File Sync Push";
            case FILE_SYNC_PULL_HANDLER:
                return "Service File Sync Pull";
            case FILE_SYNC_PUSH_HANDLER:
                return "Service File Sync Push";
            case FILE_SYNC_TRACKER:
                return "File Sync Tracker";
            case REST_PULL_HANLDER:
                return "REST Pull";
            case INSERT_LOAD_EVENTS:
                return "Inserting Load Events";
            case INITIAL_LOAD_EXTRACT_JOB:
                return "Initial Load Extractor";
            case FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB:
                return "File Sync Initial Load Extractor";
            case LOG_MINER_JOB:
                return "Log Miner";
            default:
                return name();
        }
    }

    public String toStringShort() {
        switch (this) {
            case ANY:
                return "<Any>";
            case MANUAL_LOAD:
                return "Manual Load";
            case PUSH_JOB_EXTRACT:
                return "Extracting";
            case PUSH_JOB_TRANSFER:
                return "Transferring";
            case PULL_JOB_TRANSFER:
                return "Transferring";
            case PULL_JOB_LOAD:
                return "Loading";
            case PULL_CONFIG_JOB:
                return "Config Pull";
            case PUSH_HANDLER_TRANSFER:
                return "Transferring";
            case PULL_HANDLER_TRANSFER:
                return "Transferring";
            case PUSH_HANDLER_LOAD:
                return "Loading";
            case PULL_HANDLER_EXTRACT:
                return "Extracting";
            case OFFLINE_PUSH:
                return "Offline Push";
            case OFFLINE_PULL:
                return "Offline Pull";
            case ROUTER_JOB:
                return "Routing";
            case ROUTER_READER:
                return "Routing";
            case GAP_DETECT:
                return "Routing";
            case FILE_SYNC_PULL_JOB:
                return "File Transfer";
            case FILE_SYNC_PUSH_JOB:
                return "File Transfer";
            case FILE_SYNC_PULL_HANDLER:
                return "File Write";
            case FILE_SYNC_PUSH_HANDLER:
                return "File Write";
            case FILE_SYNC_TRACKER:
                return "File Tracker";
            case REST_PULL_HANLDER:
                return "REST Pull";
            case INSERT_LOAD_EVENTS:
                return "Load Setup";
            case INITIAL_LOAD_EXTRACT_JOB:
                return "Load Extracting";
            case FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB:
                return "File Load Extracting";
            case LOG_MINER_JOB:
                return "Log Miner";
            default:
                return name();
        }
    }

    public String toStringShortPastTense() {
        switch (this) {
            case ANY:
                return "<Any>";
            case MANUAL_LOAD:
                return "Manual Load";
            case PUSH_JOB_EXTRACT:
                return "Extracted";
            case PUSH_JOB_TRANSFER:
                return "Transferred";
            case PULL_JOB_TRANSFER:
                return "Transferred";
            case PULL_JOB_LOAD:
                return "Loaded";
            case PULL_CONFIG_JOB:
                return "Config Pull";
            case PUSH_HANDLER_TRANSFER:
                return "Transferred";
            case PULL_HANDLER_TRANSFER:
                return "Transferred";
            case PUSH_HANDLER_LOAD:
                return "Loaded";
            case PULL_HANDLER_EXTRACT:
                return "Extracted";
            case OFFLINE_PUSH:
                return "Offline Push";
            case OFFLINE_PULL:
                return "Offline Pull";
            case ROUTER_JOB:
                return "Routed";
            case ROUTER_READER:
                return "Routed";
            case GAP_DETECT:
                return "Routed";
            case FILE_SYNC_PULL_JOB:
                return "File Transfer";
            case FILE_SYNC_PUSH_JOB:
                return "File Transfer";
            case FILE_SYNC_PULL_HANDLER:
                return "File Write";
            case FILE_SYNC_PUSH_HANDLER:
                return "File Write";
            case FILE_SYNC_TRACKER:
                return "File Tracker";
            case REST_PULL_HANLDER:
                return "REST Pull";
            case INSERT_LOAD_EVENTS:
                return "Load Setup";
            case INITIAL_LOAD_EXTRACT_JOB:
                return "Load Extracted";
            case FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB:
                return "File Load Extracted";
            case LOG_MINER_JOB:
                return "Log Miner";
            default:
                return name();
        }
    }
};