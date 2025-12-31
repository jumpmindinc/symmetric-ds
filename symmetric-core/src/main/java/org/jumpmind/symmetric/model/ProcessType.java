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

import org.jumpmind.symmetric.common.ProcessTypeMessages;

public enum ProcessType {
    ANY, PUSH_JOB_EXTRACT, PUSH_JOB_TRANSFER, PULL_JOB_TRANSFER, PULL_JOB_LOAD, PUSH_HANDLER_TRANSFER, PUSH_HANDLER_LOAD, PULL_HANDLER_TRANSFER, PULL_HANDLER_EXTRACT, REST_PULL_HANLDER, OFFLINE_PUSH, OFFLINE_PULL, ROUTER_JOB, INSERT_LOAD_EVENTS, GAP_DETECT, ROUTER_READER, MANUAL_LOAD, FILE_SYNC_PULL_JOB, FILE_SYNC_PUSH_JOB, FILE_SYNC_PULL_HANDLER, FILE_SYNC_PUSH_HANDLER, FILE_SYNC_TRACKER, INITIAL_LOAD_EXTRACT_JOB, FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB, PULL_CONFIG_JOB, LOG_MINER_JOB, COMPARE_PUT_HANDLE, COMPARE_GET_HANDLE, COMPARE_START_HANDLE, COMPARE_EXECUTE, UNINSTALL;

    public static final ProcessType[] dataSyncProcessTypes = new ProcessType[] { PUSH_JOB_EXTRACT, PUSH_JOB_TRANSFER, PULL_JOB_TRANSFER, PULL_JOB_LOAD,
            PUSH_HANDLER_TRANSFER, PUSH_HANDLER_LOAD, PULL_HANDLER_TRANSFER, PULL_HANDLER_EXTRACT, INSERT_LOAD_EVENTS, INITIAL_LOAD_EXTRACT_JOB };

    @Override
    public String toString() {
        switch (this) {
            case ANY:
                return ProcessTypeMessages.ANY;
            case MANUAL_LOAD:
                return ProcessTypeMessages.MANUAL_LOAD;
            case PUSH_JOB_EXTRACT:
                return ProcessTypeMessages.DATABASE_PUSH_EXTRACT;
            case PUSH_JOB_TRANSFER:
                return ProcessTypeMessages.DATABASE_PUSH_TRANSFER;
            case PULL_JOB_TRANSFER:
                return ProcessTypeMessages.DATABASE_PULL_TRANSFER;
            case PULL_JOB_LOAD:
                return ProcessTypeMessages.DATABASE_PULL_LOAD;
            case PULL_CONFIG_JOB:
                return ProcessTypeMessages.CONFIG_PULL;
            case PUSH_HANDLER_TRANSFER:
                return ProcessTypeMessages.SERVICE_DATABASE_PUSH_TRANSFER;
            case PULL_HANDLER_TRANSFER:
                return ProcessTypeMessages.SERVICE_DATABASE_PULL_TRANSFER;
            case PUSH_HANDLER_LOAD:
                return ProcessTypeMessages.SERVICE_DATABASE_PUSH_LOAD;
            case PULL_HANDLER_EXTRACT:
                return ProcessTypeMessages.SERVICE_DATABASE_PULL_EXTRACT;
            case OFFLINE_PUSH:
                return ProcessTypeMessages.OFFLINE_PUSH;
            case OFFLINE_PULL:
                return ProcessTypeMessages.OFFLINE_PULL;
            case ROUTER_JOB:
                return ProcessTypeMessages.ROUTING;
            case ROUTER_READER:
                return ProcessTypeMessages.ROUTING_READER;
            case GAP_DETECT:
                return ProcessTypeMessages.GAP_DETECTION;
            case FILE_SYNC_PULL_JOB:
                return ProcessTypeMessages.FILE_SYNC_PULL;
            case FILE_SYNC_PUSH_JOB:
                return ProcessTypeMessages.FILE_SYNC_PUSH;
            case FILE_SYNC_PULL_HANDLER:
                return ProcessTypeMessages.SERVICE_FILE_SYNC_PULL;
            case FILE_SYNC_PUSH_HANDLER:
                return ProcessTypeMessages.SERVICE_FILE_SYNC_PUSH;
            case FILE_SYNC_TRACKER:
                return ProcessTypeMessages.FILE_SYNC_TRACKER;
            case REST_PULL_HANLDER:
                return ProcessTypeMessages.REST_PULL;
            case INSERT_LOAD_EVENTS:
                return ProcessTypeMessages.INSERTING_LOAD_EVENTS;
            case INITIAL_LOAD_EXTRACT_JOB:
                return ProcessTypeMessages.INITIAL_LOAD_EXTRACTOR;
            case FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB:
                return ProcessTypeMessages.FILE_SYNC_INITIAL_LOAD_EXTRACTOR;
            case LOG_MINER_JOB:
                return ProcessTypeMessages.LOG_MINER;
            default:
                return name();
        }
    }

    public String toStringShort() {
        switch (this) {
            case ANY:
                return ProcessTypeMessages.ANY;
            case MANUAL_LOAD:
                return ProcessTypeMessages.MANUAL_LOAD;
            case PUSH_JOB_EXTRACT:
                return ProcessTypeMessages.EXTRACTING;
            case PUSH_JOB_TRANSFER:
                return ProcessTypeMessages.TRANSFERRING;
            case PULL_JOB_TRANSFER:
                return ProcessTypeMessages.TRANSFERRING;
            case PULL_JOB_LOAD:
                return ProcessTypeMessages.LOADING;
            case PULL_CONFIG_JOB:
                return ProcessTypeMessages.CONFIG_PULL;
            case PUSH_HANDLER_TRANSFER:
                return ProcessTypeMessages.TRANSFERRING;
            case PULL_HANDLER_TRANSFER:
                return ProcessTypeMessages.TRANSFERRING;
            case PUSH_HANDLER_LOAD:
                return ProcessTypeMessages.LOADING;
            case PULL_HANDLER_EXTRACT:
                return ProcessTypeMessages.EXTRACTING;
            case OFFLINE_PUSH:
                return ProcessTypeMessages.OFFLINE_PUSH;
            case OFFLINE_PULL:
                return ProcessTypeMessages.OFFLINE_PULL;
            case ROUTER_JOB:
                return ProcessTypeMessages.ROUTING;
            case ROUTER_READER:
                return ProcessTypeMessages.ROUTING;
            case GAP_DETECT:
                return ProcessTypeMessages.ROUTING;
            case FILE_SYNC_PULL_JOB:
                return ProcessTypeMessages.FILE_TRANSFER;
            case FILE_SYNC_PUSH_JOB:
                return ProcessTypeMessages.FILE_TRANSFER;
            case FILE_SYNC_PULL_HANDLER:
                return ProcessTypeMessages.FILE_WRITE;
            case FILE_SYNC_PUSH_HANDLER:
                return ProcessTypeMessages.FILE_WRITE;
            case FILE_SYNC_TRACKER:
                return ProcessTypeMessages.FILE_TRACKER;
            case REST_PULL_HANLDER:
                return ProcessTypeMessages.REST_PULL;
            case INSERT_LOAD_EVENTS:
                return ProcessTypeMessages.LOAD_SETUP;
            case INITIAL_LOAD_EXTRACT_JOB:
                return ProcessTypeMessages.LOAD_EXTRACTING;
            case FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB:
                return ProcessTypeMessages.FILE_LOAD_EXTRACTING;
            case LOG_MINER_JOB:
                return ProcessTypeMessages.LOG_MINER;
            default:
                return name();
        }
    }

    public String toStringShortPastTense() {
        switch (this) {
            case ANY:
                return ProcessTypeMessages.ANY;
            case MANUAL_LOAD:
                return ProcessTypeMessages.MANUAL_LOAD;
            case PUSH_JOB_EXTRACT:
                return ProcessTypeMessages.EXTRACTED;
            case PUSH_JOB_TRANSFER:
                return ProcessTypeMessages.TRANSFERRED;
            case PULL_JOB_TRANSFER:
                return ProcessTypeMessages.TRANSFERRED;
            case PULL_JOB_LOAD:
                return ProcessTypeMessages.LOADED;
            case PULL_CONFIG_JOB:
                return ProcessTypeMessages.CONFIG_PULL;
            case PUSH_HANDLER_TRANSFER:
                return ProcessTypeMessages.TRANSFERRED;
            case PULL_HANDLER_TRANSFER:
                return ProcessTypeMessages.TRANSFERRED;
            case PUSH_HANDLER_LOAD:
                return ProcessTypeMessages.LOADED;
            case PULL_HANDLER_EXTRACT:
                return ProcessTypeMessages.EXTRACTED;
            case OFFLINE_PUSH:
                return ProcessTypeMessages.OFFLINE_PUSH;
            case OFFLINE_PULL:
                return ProcessTypeMessages.OFFLINE_PULL;
            case ROUTER_JOB:
                return ProcessTypeMessages.ROUTED;
            case ROUTER_READER:
                return ProcessTypeMessages.ROUTED;
            case GAP_DETECT:
                return ProcessTypeMessages.ROUTED;
            case FILE_SYNC_PULL_JOB:
                return ProcessTypeMessages.FILE_TRANSFER;
            case FILE_SYNC_PUSH_JOB:
                return ProcessTypeMessages.FILE_TRANSFER;
            case FILE_SYNC_PULL_HANDLER:
                return ProcessTypeMessages.FILE_WRITE;
            case FILE_SYNC_PUSH_HANDLER:
                return ProcessTypeMessages.FILE_WRITE;
            case FILE_SYNC_TRACKER:
                return ProcessTypeMessages.FILE_TRACKER;
            case REST_PULL_HANLDER:
                return ProcessTypeMessages.REST_PULL;
            case INSERT_LOAD_EVENTS:
                return ProcessTypeMessages.LOAD_SETUP;
            case INITIAL_LOAD_EXTRACT_JOB:
                return ProcessTypeMessages.LOAD_EXTRACTED;
            case FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB:
                return ProcessTypeMessages.FILE_LOAD_EXTRACTED;
            case LOG_MINER_JOB:
                return ProcessTypeMessages.LOG_MINER;
            default:
                return name();
        }
    }
};