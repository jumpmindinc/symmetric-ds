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
package org.jumpmind.symmetric.service.impl;

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;

public class IncomingBatchServiceSqlMap extends AbstractSqlMap {
    public IncomingBatchServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        // @formatter:off
        
        putSql("selectNodesInErrorSql", "select distinct node_id from $(incoming_batch) where error_flag=1");

        putSql("selectIncomingBatchPrefixSql" ,""  
                    + "select batch_id, node_id, channel_id, status, network_millis, filter_millis, load_millis, "
                    + "  failed_row_number, failed_line_number, byte_count, load_row_count, fallback_insert_count, "
                    + "  fallback_update_count, conflict_win_count, conflict_lose_count, ignore_count, "
                    + "  ignore_row_count, missing_delete_count, skip_count, sql_state, sql_code, sql_message, "
                    + "  last_update_hostname, last_update_time, create_time, error_flag, summary, "
                    + "  load_insert_row_count, load_update_row_count, load_delete_row_count, "
                    + "  data_insert_row_count, data_update_row_count, data_delete_row_count, "
                    + "  data_row_count, extract_insert_row_count, extract_update_row_count, "
                    + "  extract_delete_row_count, extract_row_count, reload_row_count, other_row_count, "
                    + "  load_flag, extract_count, load_count, router_millis, extract_millis, sent_count, "
                    + "  transform_extract_millis, transform_load_millis, load_id, common_flag, failed_data_id, bulk_loader_flag from "
                    + "  $(incoming_batch)                                          " );

        putSql("selectCreateTimePrefixSql" ,"" + 
"select create_time from $(incoming_batch)   " );

        putSql("findIncomingBatchSql" ,"" + 
"where batch_id = ? and node_id = ?   " );

        putSql("findIncomingBatchByBatchIdSql", "where batch_id = ? " );

        putSql("listIncomingBatchesInErrorForNodeSql" ,"" + 
"where node_id=? and error_flag=1   " );

        putSql("findIncomingBatchErrorsSql" ,"" + 
"where status = 'ER' order by batch_id   " );

        putSql("countIncomingBatchesErrorsSql" ,"" + 
"select count(*) from $(incoming_batch) where error_flag=1   " );

        putSql("countIncomingBatchesErrorsOnChannelSql" ,"" + 
"select count(*) from $(incoming_batch) where error_flag=1 and channel_id=?");

        putSql("getIncomingBatchesLatestUpdateSql",
                "select max(last_update_time) from $(incoming_batch) where status = 'OK' and channel_id not in ('heartbeat', 'monitor', 'config', 'reload')");
        
        putSql("selectCountBatchesPrefixSql", "select count(*) from $(incoming_batch) ");

        putSql("insertIncomingBatchSql" ,"" + 
"insert into $(incoming_batch) (batch_id, node_id, channel_id, status, network_millis, filter_millis, load_millis, failed_row_number, failed_line_number, byte_count, " + 
"  load_row_count, fallback_insert_count, fallback_update_count, conflict_win_count, conflict_lose_count, ignore_count, ignore_row_count, missing_delete_count, " +
"  skip_count, sql_state, sql_code, sql_message, last_update_hostname, last_update_time, summary, create_time, load_flag, extract_count, sent_count, load_count, " +
"  load_id, common_flag, router_millis, extract_millis, transform_extract_millis, transform_load_millis, reload_row_count, other_row_count, data_row_count, " +
"  data_insert_row_count, data_update_row_count, data_delete_row_count, extract_row_count, extract_insert_row_count, extract_update_row_count, " +
"  extract_delete_row_count, load_insert_row_count, load_update_row_count, load_delete_row_count, failed_data_id) " + 
"  values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
"  ?, ?, ?, ?, ?, ?, ?, ?)");

        putSql("updateIncomingBatchSql" ,"" + 
"update $(incoming_batch) set status = ?, error_flag=?, network_millis = ?, filter_millis = ?, load_millis = ?, failed_row_number = ?, failed_line_number = ?, byte_count = ?, " + 
"  load_row_count = ?, fallback_insert_count = ?, fallback_update_count = ?, conflict_win_count = ?, conflict_lose_count = ?, ignore_count = ?, ignore_row_count = ?, " +
"  missing_delete_count = ?, skip_count = ?,  sql_state = ?, sql_code = ?, sql_message = ?, last_update_hostname = ?, last_update_time = ?, summary = ?, load_flag = ?, " +
"  extract_count = ?, sent_count = ?, load_count = ?, load_id = ?, common_flag = ?, router_millis = ?, extract_millis = ?, transform_extract_millis = ?, " +
"  transform_load_millis = ?, reload_row_count = ?, other_row_count = ?, data_row_count = ?, data_insert_row_count = ?, data_update_row_count = ?, data_delete_row_count = ?, " +
"  extract_row_count = ?, extract_insert_row_count = ?, extract_update_row_count = ?, extract_delete_row_count = ?, load_insert_row_count = ?, load_update_row_count = ?, " +
"  load_delete_row_count = ?, failed_data_id = ?, bulk_loader_flag=? where batch_id = ? and node_id = ? " );
        
        putSql("statusNotOk", " and status not in ('OK', 'IG')");

        putSql("deleteIncomingBatchSql" ,"" + 
"delete from $(incoming_batch) where batch_id = ? and node_id = ? " );

        putSql("deleteIncomingBatchByNodeSql" ,"delete from $(incoming_batch) where node_id = ?");
        
        putSql("maxBatchIdsSql", "select max(batch_id) as batch_id, node_id, channel_id from $(incoming_batch) where status = ? group by node_id, channel_id");
        
        putSql("selectIncomingBatchSummaryByStatusAndChannelSql",
                "select count(*) as batches, s.status, sum(s.load_row_count) as data, s.node_id, min(s.create_time) as oldest_batch_time, s.channel_id,      "
                        + " max(s.last_update_time) as last_update_time, min(s.batch_id) as batch_id, max(s.error_flag) as error_flag "
                        + "  from $(incoming_batch) s "
                        + "  join $(node) n on n.node_id=s.node_id and n.sync_enabled=1 where s.status in (:STATUS_LIST) group by s.status, s.node_id, s.channel_id order by oldest_batch_time asc");

        putSql("selectIncomingBatchSummaryByStatusSql",
                "select count(*) as batches, status, sum(load_row_count) as data, node_id, min(create_time) as oldest_batch_time,      "
                        + " max(last_update_time) as last_update_time"
                        + "  from $(incoming_batch) where status in (:STATUS_LIST) group by status, node_id order by oldest_batch_time asc   ");

        putSql("lastUpdateByChannelSql", "select max(last_update_time) as last_update_time, channel_id from $(incoming_batch) group by channel_id");
        
        putSql("getAllBatchesSql", "select batch_id, node_id from $(incoming_batch)");
    
        putSql("selectIncomingBatchSummaryPrefixSql",
                "select b.status ");
        
        putSql("selectIncomingBatchSummaryByNodePrefixSql",
                "select b.status, b.node_id ");
        
        putSql("selectIncomingBatchSummaryByNodeAndChannelPrefixSql",
                "select b.status, b.node_id, b.channel_id ");
        
        putSql("selectIncomingBatchSummaryStatsPrefixSql",
                ", count(*) as batches, sum(b.load_row_count) as data, min(b.create_time) as oldest_batch_time, "
                        + " max(b.last_update_time) as last_update_time, "
                        + " min(b.batch_id) as batch_id, "
                        + " max(b.error_flag) as error_flag, "
                        + " sum(b.byte_count) as total_bytes, "
                        + " sum(b.router_millis + b.extract_millis + b.network_millis + b.filter_millis + b.load_millis) as total_millis, "
                        + " sum(b.router_millis) as total_router_millis, "
                        + " sum(b.extract_millis) as total_extract_millis, "
                        + " sum(b.network_millis) as total_network_millis, "
                        + " sum(b.filter_millis) as total_filter_millis, "
                        + " sum(b.load_millis) as total_load_millis, "
                        + " sum(b.load_insert_row_count) as insert_event_count, "  
                        + " sum(b.load_update_row_count) as update_event_count, " 
                        + " sum(b.load_delete_row_count) as delete_event_count, "  
                        + " sum(b.other_row_count) as other_event_count, "
                        + " sum(b.reload_row_count) as reload_event_count "
                        + " from $(incoming_batch) b ");
        
        putSql("whereStatusAndNodeGroupByStatusSql",
                " where b.status in (:STATUS_LIST) and b.node_id = ? group by b.status order by oldest_batch_time asc   ");
        
        putSql("whereStatusGroupByStatusAndNodeSql",
                " where b.status in (:STATUS_LIST) group by b.status, b.node_id order by oldest_batch_time asc   ");

        putSql("whereStatusGroupByStatusAndNodeAndChannelSql",
                " where b.status in (:STATUS_LIST) group by b.status, b.node_id, b.channel_id order by b.node_id, oldest_batch_time asc   ");

        putSql("whereStatusAndNodeAndChannelGroupByStatusSql",
                " where b.status in (:STATUS_LIST) and b.node_id = ? and b.channel_id = ? group by b.status order by oldest_batch_time asc   ");

        putSql("selectIncomingBatchSummaryByNodeBriefStatsSql",
                "select b.node_id, b.status, CAST(b.create_time AS DATE) as batch_date, count(*) as batches, sum(b.data_row_count) as data_rows "
                        + " from $(incoming_batch) b "
                        + " GROUP BY b.node_id, b.status, CAST(b.create_time AS DATE)"
                        + " ORDER BY b.node_id, b.status, CAST(b.create_time AS DATE)");
    }

}