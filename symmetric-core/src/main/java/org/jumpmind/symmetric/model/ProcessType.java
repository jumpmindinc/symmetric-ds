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
    //@formatter:off
    ANY(ProcessTypeMessages.ANY, ProcessTypeMessages.ANY, ProcessTypeMessages.ANY),
    PUSH_JOB_EXTRACT(ProcessTypeMessages.DATABASE_PUSH_EXTRACT, ProcessTypeMessages.EXTRACTING, ProcessTypeMessages.EXTRACTED),
    PUSH_JOB_TRANSFER(ProcessTypeMessages.DATABASE_PUSH_TRANSFER, ProcessTypeMessages.TRANSFERRING, ProcessTypeMessages.TRANSFERRED),
    PULL_JOB_TRANSFER(ProcessTypeMessages.DATABASE_PULL_TRANSFER, ProcessTypeMessages.TRANSFERRING, ProcessTypeMessages.TRANSFERRED),
    PULL_JOB_LOAD(ProcessTypeMessages.DATABASE_PULL_LOAD, ProcessTypeMessages.LOADING, ProcessTypeMessages.LOADED),
    PUSH_HANDLER_TRANSFER(ProcessTypeMessages.SERVICE_DATABASE_PUSH_TRANSFER, ProcessTypeMessages.TRANSFERRING, ProcessTypeMessages.TRANSFERRED),
    PUSH_HANDLER_LOAD(ProcessTypeMessages.SERVICE_DATABASE_PUSH_LOAD, ProcessTypeMessages.LOADING, ProcessTypeMessages.LOADED),
    PULL_HANDLER_TRANSFER(ProcessTypeMessages.SERVICE_DATABASE_PULL_TRANSFER, ProcessTypeMessages.TRANSFERRING, ProcessTypeMessages.TRANSFERRED),
    PULL_HANDLER_EXTRACT(ProcessTypeMessages.SERVICE_DATABASE_PULL_EXTRACT, ProcessTypeMessages.EXTRACTING, ProcessTypeMessages.EXTRACTED),
    REST_PULL_HANLDER(ProcessTypeMessages.REST_PULL, ProcessTypeMessages.REST_PULL, ProcessTypeMessages.REST_PULL),
    OFFLINE_PUSH(ProcessTypeMessages.OFFLINE_PUSH, ProcessTypeMessages.OFFLINE_PUSH, ProcessTypeMessages.OFFLINE_PUSH),
    OFFLINE_PULL(ProcessTypeMessages.OFFLINE_PULL, ProcessTypeMessages.OFFLINE_PULL, ProcessTypeMessages.OFFLINE_PULL),
    ROUTER_JOB(ProcessTypeMessages.ROUTING, ProcessTypeMessages.ROUTING, ProcessTypeMessages.ROUTED),
    INSERT_LOAD_EVENTS(ProcessTypeMessages.INSERTING_LOAD_EVENTS, ProcessTypeMessages.LOAD_SETUP, ProcessTypeMessages.LOAD_SETUP),
    GAP_DETECT(ProcessTypeMessages.GAP_DETECTION, ProcessTypeMessages.ROUTING, ProcessTypeMessages.ROUTED),
    ROUTER_READER(ProcessTypeMessages.ROUTING_READER, ProcessTypeMessages.ROUTING, ProcessTypeMessages.ROUTED),
    MANUAL_LOAD(ProcessTypeMessages.MANUAL_LOAD, ProcessTypeMessages.MANUAL_LOAD, ProcessTypeMessages.MANUAL_LOAD),
    FILE_SYNC_PULL_JOB(ProcessTypeMessages.FILE_SYNC_PULL, ProcessTypeMessages.FILE_TRANSFER, ProcessTypeMessages.FILE_TRANSFER),
    FILE_SYNC_PUSH_JOB(ProcessTypeMessages.FILE_SYNC_PUSH, ProcessTypeMessages.FILE_TRANSFER, ProcessTypeMessages.FILE_TRANSFER),
    FILE_SYNC_PULL_HANDLER(ProcessTypeMessages.SERVICE_FILE_SYNC_PULL, ProcessTypeMessages.FILE_WRITE, ProcessTypeMessages.FILE_WRITE),
    FILE_SYNC_PUSH_HANDLER(ProcessTypeMessages.SERVICE_FILE_SYNC_PUSH, ProcessTypeMessages.FILE_WRITE, ProcessTypeMessages.FILE_WRITE),
    FILE_SYNC_TRACKER(ProcessTypeMessages.FILE_SYNC_TRACKER, ProcessTypeMessages.FILE_TRACKER, ProcessTypeMessages.FILE_TRACKER),
    INITIAL_LOAD_EXTRACT_JOB(ProcessTypeMessages.INITIAL_LOAD_EXTRACTOR, ProcessTypeMessages.LOAD_EXTRACTING, ProcessTypeMessages.LOAD_EXTRACTED),
    FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB(ProcessTypeMessages.FILE_SYNC_INITIAL_LOAD_EXTRACTOR,
            ProcessTypeMessages.FILE_LOAD_EXTRACTING, ProcessTypeMessages.FILE_LOAD_EXTRACTED),
    PULL_CONFIG_JOB(ProcessTypeMessages.CONFIG_PULL, ProcessTypeMessages.CONFIG_PULL, ProcessTypeMessages.CONFIG_PULL),
    LOG_MINER_JOB(ProcessTypeMessages.LOG_MINER, ProcessTypeMessages.LOG_MINER, ProcessTypeMessages.LOG_MINER),
    COMPARE_PUT_HANDLE(null, null, null), COMPARE_GET_HANDLE(null, null, null), COMPARE_START_HANDLE(null, null, null),
    COMPARE_EXECUTE(null, null, null), UNINSTALL(null, null, null);
    //@formatter:on

    public static final ProcessType[] dataSyncProcessTypes = new ProcessType[] { PUSH_JOB_EXTRACT, PUSH_JOB_TRANSFER, PULL_JOB_TRANSFER, PULL_JOB_LOAD,
            PUSH_HANDLER_TRANSFER, PUSH_HANDLER_LOAD, PULL_HANDLER_TRANSFER, PULL_HANDLER_EXTRACT, INSERT_LOAD_EVENTS, INITIAL_LOAD_EXTRACT_JOB };
    private final String displayName;
    private final String shortName;
    private final String shortPastTense;

    ProcessType(String displayName, String shortName, String shortPastTense) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.shortPastTense = shortPastTense;
    }

    @Override
    public String toString() {
        return displayName != null ? displayName : name();
    }

    public String toStringShort() {
        return shortName != null ? shortName : name();
    }

    public String toStringShortPastTense() {
        return shortPastTense != null ? shortPastTense : name();
    }
};