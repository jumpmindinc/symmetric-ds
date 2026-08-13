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
package org.jumpmind.symmetric.service.impl;

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;

public class DataExtractorServiceSqlMap extends AbstractSqlMap {
    public DataExtractorServiceSqlMap(IDatabasePlatform platform,
            Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        // @formatter:off
        putSql("selectNodeIdsForExtractSql", "select node_id, queue, extract_thread_id from $(extract_request) where status=? and parent_request_id=0 and source_node_id = ? group by node_id, queue, extract_thread_id");
        
        putSql("selectExtractRequestForNodeSql", "select * from $(extract_request) where node_id=? and queue=? and status=? and parent_request_id=0 and source_node_id = ? order by request_id");

        putSql("selectExtractRequestForNodeThreadSql", "select * from $(extract_request) where node_id=? and queue=? and extract_thread_id = ? and status=? and parent_request_id=0 and source_node_id = ? order by load_id, request_id");

        putSql("selectExtractRequestForBatchSql", "select * from $(extract_request) where start_batch_id <= ? and end_batch_id >= ? and node_id = ? and load_id = ? and source_node_id = ?");

        putSql("selectExtractChildRequestForNodeSql", "select c.* from $(extract_request) c " +
                "inner join $(extract_request) p on p.request_id = c.parent_request_id where p.node_id=? and p.queue=? and p.status=? and p.parent_request_id=0 and p.source_node_id = ?");

        putSql("selectExtractChildRequestForNodeThreadSql", "select c.* from $(extract_request) c " +
                "inner join $(extract_request) p on p.request_id = c.parent_request_id where p.node_id=? and p.queue=? and p.extract_thread_id=? and p.status=? and p.parent_request_id=0 and p.source_node_id = ?");

        putSql("selectExtractChildRequestsByParentSql", "select * from $(extract_request) where parent_request_id = ? and source_node_id = ?");

        putSql("selectExtractChildRequestIdsMissed",
                "select request_id from $(extract_request) where status = ? and parent_request_id > 0 " 
                + "and parent_request_id in (select request_id from $(extract_request) where parent_request_id = 0 and status = ? and source_node_id = ?) and source_node_id = ?");

        putSql("selectExtractRequestsForThreadingSql", "select * from $(extract_request) where source_node_id = ? and queue = ? and " +
                "(status = ? or (status = ? and loaded_time is null)) " +
                "order by load_id asc, request_id asc");

        putSql("releaseExtractChildRequestFromParent",
                "update $(extract_request) set parent_request_id = 0 where request_id = ?");
        
        putSql("insertExtractRequestSql", "insert into $(extract_request) "
                + "(request_id, source_node_id, node_id, queue, status, start_batch_id, end_batch_id, trigger_id, router_id, load_id, table_name, total_rows, parent_request_id, last_update_time, create_time) "
                + " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        
        putSql("updateExtractRequestStatus", "update $(extract_request) set status=?, last_update_time=?, extracted_rows=?, extracted_millis=? where request_id=?");
        
        putSql("updateExtractRequestLoadTime", "update $(extract_request) set loaded_time = (case when end_batch_id = ? then ? when 1 = 0 then last_update_time else null end), "
                + " loaded_rows = loaded_rows + ?, loaded_millis = loaded_millis + ?, last_loaded_batch_id = ?, "
                + " conflicted_rows = conflicted_rows + ?, "
                + " last_update_time=?, "
                + " bulk_rows_loaded = bulk_rows_loaded + ?"
                + " where start_batch_id <= ? and end_batch_id >= ? and node_id=? and load_id=? and source_node_id = ? and loaded_time is null");
        
        putSql("updateExtractRequestLoadTimeNoParamsInSelect", "update $(extract_request) set loaded_time = (case when end_batch_id = $(batchId) then current_timestamp when 1 = 0 then last_update_time else null end), "
                + " loaded_rows = loaded_rows + $(rowCount), loaded_millis = loaded_millis + $(loadMillis), last_loaded_batch_id = ?, "
                + " conflicted_rows = conflicted_rows + $(conflictedRows), "
                + " last_update_time=?, "
                + " bulk_rows_loaded = bulk_rows_loaded + $(bulkRowsLoaded) "
                + " where start_batch_id <= ? and end_batch_id >= ? and node_id=? and load_id=? and source_node_id = ? and loaded_time is null");
        
        putSql("updateExtractRequestTransferred", "update $(extract_request) set byte_count = byte_count + ?, last_transferred_batch_id=?, transferred_rows = transferred_rows + ?, transferred_millis = ?"
                + " where start_batch_id <= ? and end_batch_id >= ? and node_id=? and load_id=? and (last_transferred_batch_id is null or last_transferred_batch_id < ?) and source_node_id = ?");

        putSql("updateExtractRequestTransferredNoParamsInSelect", "update $(extract_request) set byte_count = byte_count + $(byteCount), last_transferred_batch_id=?, transferred_rows = transferred_rows + $(rowCount), transferred_millis = ?"
                + " where start_batch_id <= ? and end_batch_id >= ? and node_id=? and load_id=? and (last_transferred_batch_id is null or last_transferred_batch_id < ?) and source_node_id = ?");

        putSql("updateExtractRequestsThreadsSql", "update $(extract_request) set extract_thread_id = ?, load_thread_id = ? where request_id = ?");

        putSql("updateOutgoingBatchesForThreadSql", "update $(outgoing_batch) set thread_id = ? where load_id = ?" +
                " and (batch_id between ? and ? or (batch_id < ? and summary = ?))");

        putSql("updateOutgoingBatchesForSetupThreadSql", "update $(outgoing_batch) set thread_id = ? where load_id = ? and summary like ? and thread_id is null");

        // extracted_rows / extracted_millis are reset alongside the transfer and load statistics. Leaving them behind makes a
        // restarted request report extraction counters from the run that was interrupted, which is the misleading state this
        // recovery path exists to clear.
        putSql("restartExtractRequest", "update $(extract_request) set last_transferred_batch_id = null, transferred_rows = 0, transferred_millis = 0, "
                + "last_loaded_batch_id = null, loaded_rows = 0, loaded_millis = 0, extracted_rows = 0, extracted_millis = 0, "
                + "parent_request_id = 0, status = ? "
                + "where request_id = ? and node_id = ?");

        putSql("updateExtractRequestExtractedStats", "update $(extract_request) set extracted_rows = extracted_rows + ?, "
                + "extracted_millis = extracted_millis + ?, last_update_time = ? where request_id = ?");

        putSql("countRequestedBatchesForExtractRequestSql",
                "select count(*) from $(outgoing_batch) where node_id = ? and batch_id between ? and ? and status = 'RQ'");

        putSql("countDeliveredBatchesForExtractRequestSql",
                "select count(*) from $(outgoing_batch) where node_id = ? and batch_id between ? and ? and status in ('OK','IG')");

        /*
         * A request marked OK whose range still contains RQ batches cannot have completed: MultiBatchStagingWriter.close() advances every remaining batch, so a
         * finished extract leaves none at RQ. loaded_time is null excludes requests that legitimately finished and were loaded, and the last_update_time bound
         * avoids racing a status write that is still in flight.
         */
        putSql("selectStuckExtractRequestsSql", "select * from $(extract_request) r where r.source_node_id = ? and r.status = ? "
                + "and r.loaded_time is null and r.parent_request_id = 0 and r.last_update_time < ? "
                + "and exists (select 1 from $(outgoing_batch) b where b.node_id = r.node_id "
                + "and b.batch_id between r.start_batch_id and r.end_batch_id and b.status = 'RQ') "
                + "order by r.load_id asc, r.request_id asc");

        putSql("cancelExtractRequests", "update $(extract_request) set status=?, last_update_time=?, loaded_time=? where load_id = ? and source_node_id = ? and (status != ? or loaded_time is null)");

        putSql("countIncompleteExtractRequestsByLoadId", "select count(*) from $(extract_request) where load_id = ? and source_node_id = ? and parent_request_id = 0 and status != 'OK'");

        putSql("selectTablesForExtractByLoadId", "select * from $(extract_request) where load_id = ? and source_node_id = ? order by request_id");
    
        putSql("selectTablesForExtractByLoadIdAndNodeId", "select * from $(extract_request) where load_id = ? and node_id = ? order by request_id");
        
        putSql("updateExtractRequestStatuses", "update $(extract_request) set status=?, last_update_time=? "
                + "where load_id=? and source_node_id=? and status=?");
    }

}
