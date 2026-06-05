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

public class OutgoingBatchServiceSqlMap extends AbstractSqlMap {
    public OutgoingBatchServiceSqlMap(IDatabasePlatform platform,
            Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        putSql("selectNodesInErrorSql", "select distinct node_id from $(outgoing_batch) where error_flag=1");
        putSql("initialLoadStatusSql",
                "select status from $(outgoing_batch) where node_id=? and load_flag=?   ");
        putSql("unsentBatchesForNodeIdChannelIdSql",
                "select count(*) from $(outgoing_batch) where node_id=? and channel_id=? and status != 'OK'   ");
        putSql("selectCountBatchesPrefixSql", "select count(*) from $(outgoing_batch)   ");
        putSql("cancelLoadBatchesSql",
                "update $(outgoing_batch) set ignore_count=1, status=case when sent_count > 0 then 'IG' else 'OK' end, " +
                        "error_flag=0, last_update_time=? where load_id=? and status not in ('OK','IG')");
        putSql("insertOutgoingBatchSql",
                "insert into $(outgoing_batch)                                                                                                                "
                        + "  (batch_id, node_id, channel_id, status, load_id, extract_job_flag, load_flag, common_flag, reload_row_count, other_row_count, "
                        + "  data_update_row_count, data_insert_row_count, data_delete_row_count, last_update_hostname, last_update_time, create_time, create_by, summary, data_row_count)   "
                        + "  values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)                                                                         ");
        putSql("updateOutgoingBatchSql",
                "update $(outgoing_batch) set status=?, load_id=?, extract_job_flag=?, load_flag=?, error_flag=?,                                          "
                        + "  byte_count=?, extract_count=?, sent_count=?, load_count=?, data_row_count=?,                                 "
                        + "  reload_row_count=?, data_insert_row_count=?, data_update_row_count=?, data_delete_row_count=?, other_row_count=?,   "
                        + "  ignore_count=?, router_millis=?, network_millis=?, filter_millis=?,                                                            "
                        + "  load_millis=?, extract_millis=?, extract_start_time=?, transfer_start_time=?, load_start_time=?, "
                        + "  sql_state=?, sql_code=?, sql_message=?,                                       "
                        + "  failed_data_id=?, failed_line_number=?, last_update_hostname=?, last_update_time=?, summary=?, "
                        + "  load_row_count=?, load_insert_row_count=?, load_update_row_count=?, load_delete_row_count=?, "
                        + "  fallback_insert_count=?, fallback_update_count=?, conflict_win_count=?, conflict_lose_count=?, ignore_row_count=?, "
                        + "  missing_delete_count=?, skip_count=?, extract_row_count=?, extract_insert_row_count=?, extract_update_row_count=?, "
                        + "  extract_delete_row_count=?, transform_extract_millis=?, transform_load_millis=?, bulk_loader_flag=? "
                        + "  where batch_id=? and node_id=?");
        putSql("statusNotOk", " and status not in ('OK', 'IG')");
        putSql("updateOutgoingBatchStatusSql",
                "update $(outgoing_batch) set status=?, last_update_time=?, last_update_hostname=? " +
                        "where node_id = ? and batch_id between ? and ?");
        putSql("updateCommonBatchExtractStatsSql",
                "update $(outgoing_batch) set byte_count=?, data_row_count=?,                                 "
                        + "  data_insert_row_count=?, data_update_row_count=?, data_delete_row_count=?, other_row_count=?,   "
                        + "  extract_row_count=?, extract_insert_row_count=?, extract_update_row_count=?, extract_delete_row_count=? "
                        + "  where batch_id=? and node_id != ?                    ");
        putSql("findOutgoingBatchSql", "where batch_id=? and node_id=?  ");
        putSql("findOutgoingBatchByIdOnlySql", "where batch_id=? ");
        putSql("findOutgoingBatchFirstCommonSql", "where batch_id=? and common_flag=1 and extract_start_time is not null");
        putSql("selectOutgoingBatchSql",
                "where node_id = ? and status in (?, ?, ?, ?, ?, ?, ?, ?) order by batch_id asc   ");
        putSql("selectOutgoingBatchChannelSql",
                " join $(channel) c on c.channel_id = b.channel_id where node_id = ? and c.queue = ? and status in (?, ?, ?, ?, ?, ?, ?, ?) order by batch_id asc   ");
        putSql("selectOutgoingBatchChannelActionSql",
                " join $(channel) c on c.channel_id = b.channel_id" +
                        " where c.data_event_action = ?" +
                        " and b.node_id = ? and c.queue = ? and b.status in (?, ?, ?, ?, ?, ?, ?, ?) order by b.batch_id asc   ");
        putSql("selectOutgoingBatchChannelActionNullSql",
                " join $(channel) c on c.channel_id = b.channel_id" +
                        " where (c.data_event_action is null or c.data_event_action = ?)" +
                        " and b.node_id = ? and c.queue = ? and b.status in (?, ?, ?, ?, ?, ?, ?, ?) order by b.batch_id asc   ");
        putSql("selectOutgoingBatchByThreadSql",
                "where b.node_id = ? and b.channel_id = ? and b.status in (?, ?, ?, ?, ?, ?, ?, ?) and b.thread_id = ? order by b.batch_id asc");
        putSql("selectOutgoingBatchRangeSql",
                "where batch_id between ? and ? order by batch_id   ");
        putSql("selectOutgoingBatchLoadSql",
                "where load_id = ? order by batch_id   ");
        putSql("selectOutgoingBatchLoadByBatchRangeByTableNameSql",
                "where load_id = ? and batch_id between ? and ? and summary = ? order by batch_id   ");
        putSql("selectOutgoingBatchTimeRangeSql",
                "where node_id=? and channel_id=? and create_time >= ? and create_time <= ? ");
        putSql("selectOutgoingBatchPrefixSql",
                "select b.node_id, b.channel_id, b.status, "
                        + "  b.byte_count, b.extract_count, b.sent_count, b.load_count, b.data_row_count, "
                        + "  b.reload_row_count, b.data_insert_row_count, b.data_update_row_count, b.data_delete_row_count, b.other_row_count, "
                        + "  b.ignore_count, b.router_millis, b.network_millis, b.filter_millis, b.load_millis, b.extract_millis, "
                        + "  b.extract_start_time, b.transfer_start_time, b.load_start_time, b.sql_state, b.sql_code, "
                        + "  b.sql_message, b.load_insert_row_count, b.load_update_row_count, b.load_delete_row_count, b.load_row_count, "
                        + "  b.extract_insert_row_count, b.extract_update_row_count, b.extract_delete_row_count, b.extract_row_count, "
                        + "  b.transform_extract_millis, b.transform_load_millis, b.fallback_insert_count, b.fallback_update_count, "
                        + "  b.conflict_win_count, b.conflict_lose_count, b.ignore_row_count, b.missing_delete_count, b.skip_count, "
                        + "  b.failed_data_id, b.failed_line_number, b.last_update_hostname, b.last_update_time, b.create_time, b.batch_id, "
                        + "  b.extract_job_flag, b.load_flag, b.error_flag, b.common_flag, b.load_id, b.create_by, b.summary, b.bulk_loader_flag, "
                        + "  b.thread_id from $(outgoing_batch) b ");
        putSql("selectOutgoingBatchErrorsSql", " where error_flag=1 order by batch_id   ");
        putSql("countOutgoingBatchesErrorsOnChannelSql",
                "select count(*) from $(outgoing_batch) where error_flag=1 and channel_id=?");
        putSql("countOutgoingBatchesByChannelSql",
                "select count(*) as batch_count, channel_id from $(outgoing_batch) where node_id = ? and channel_id <> 'heartbeat' and status in ('ER','RQ','NE','QY','RT') group by channel_id order by batch_count desc, channel_id");
        putSql("countOutgoingRowsByTargetNodeSql",
                "select sum(data_row_count) as rows_count from $(outgoing_batch) where node_id = ? and channel_id <> 'heartbeat' and status in ('ER','RQ','NE','QY','RT')");
        putSql("countOutgoingBatchesByTargetNodeSql",
                "select count(*) as rows_count from $(outgoing_batch) where node_id = ? and status != 'OK'");
        putSql("countOutgoingBatchesByTargetNodeExcludingHeartbeatsSql",
                "select count(*) as rows_count from $(outgoing_batch) where node_id = ? and channel_id <> 'heartbeat' and status in ('ER','RQ','NE','QY','RT')");
        putSql("countOutgoingBatchesErrorsSql",
                "select count(*) from $(outgoing_batch) where error_flag=1");
        putSql("countOutgoingBatchesUnsentSql",
                "select count(*) from $(outgoing_batch) where status != 'OK'");
        putSql("countOutgoingNonSystemBatchesUnsentSql",
                "select count(batch_id) as batch_count, sum(data_row_count) as rows_count from $(outgoing_batch) where status != 'OK' and channel_id not in ('heartbeat', 'monitor', 'config')");
        putSql("getOutgoingBatchesLatestUpdateSql",
                "select max(last_update_time) from $(outgoing_batch) where status = 'OK' and channel_id not in ('heartbeat', 'monitor', 'config')");
        putSql("countOutgoingBatchesWithStatusSql",
                "select count(*) from $(outgoing_batch) where status = ? ");
        putSql("countOutgoingBatchesUnsentOnChannelSql",
                "select count(*) from $(outgoing_batch) where status != 'OK' and channel_id=?");
        putSql("countOutgoingBatchesUnsentHeartbeat",
                "select count(distinct b.node_id) from $(outgoing_batch) b inner join $(data_event) e on e.batch_id = b.batch_id " +
                        "inner join $(data) d on d.data_id = e.data_id " +
                        "where b.channel_id = 'heartbeat' and b.status != 'OK' and d.source_node_id is null");
        putSql("cancelStaleHeartbeatBatchesSql",
                "update $(outgoing_batch) " +
                        "set status = 'OK', ignore_count = 1, last_update_time = current_timestamp " +
                        "where channel_id = 'heartbeat' " +
                        "  and status in ('NE', 'RT', 'RQ') " +
                        "  and batch_id not in ( " +
                        "      select batch_id from ( " +
                        "          select max(batch_id) as batch_id from $(outgoing_batch) " +
                        "           where channel_id = 'heartbeat' " +
                        "             and status in ('NE', 'RT', 'RQ') " +
                        "           group by node_id " +
                        "      ) as latest_batches " +
                        "  )");
        putSql("countUnsentBatchesBlocked", "select count(*) as batch_count, count(distinct b1.node_id) as node_count from sym_outgoing_batch b1 "
                + "inner join sym_outgoing_batch b2 "
                + "on b1.batch_id >= b2.batch_id and b1.node_id = b2.node_id and b1.channel_id = b2.channel_id "
                + "where b2.error_flag = 1 "
                + "and b1.status != 'OK'");
        putSql("selectOutgoingBatchSummaryPrefixSql",
                "select b.status ");
        putSql("selectOutgoingBatchSummaryByNodePrefixSql",
                "select b.status, b.node_id ");
        putSql("selectOutgoingBatchSummaryByNodeAndChannelPrefixSql",
                "select b.status, b.node_id, b.channel_id ");
        putSql("selectOutgoingBatchSummaryStatsPrefixSql",
                ", count(*) as batches, sum(b.data_row_count) as data, min(b.create_time) as oldest_batch_time, "
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
                        + " sum(b.data_insert_row_count) as insert_event_count, "
                        + " sum(b.data_update_row_count) as update_event_count, "
                        + " sum(b.data_delete_row_count) as delete_event_count, "
                        + " sum(b.other_row_count) as other_event_count, "
                        + " sum(b.reload_row_count) as reload_event_count "
                        + " from $(outgoing_batch) b ");
        putSql("whereStatusGroupByStatusAndNodeSql",
                " where b.status in (:STATUS_LIST) group by b.status, b.node_id order by oldest_batch_time asc   ");
        putSql("whereStatusGroupByStatusAndNodeAndChannelSql",
                " where b.status in (:STATUS_LIST) group by b.status, b.node_id, b.channel_id order by b.node_id, oldest_batch_time asc   ");
        putSql("whereStatusAndNodeGroupByStatusSql",
                " where b.status in (:STATUS_LIST) and b.node_id = ? group by b.status, b.node_id order by b.node_id, oldest_batch_time asc   ");
        putSql("whereStatusAndNodeAndChannelGroupByStatusSql",
                " where b.status in (:STATUS_LIST) and b.node_id = ? and b.channel_id = ? group by b.status, b.node_id order by oldest_batch_time asc   ");
        putSql("updateOutgoingBatchesStatusSql",
                "update $(outgoing_batch) set status = ?, ignore_count = 1 where status = ?");
        putSql("deleteOutgoingBatchesForNodeSql",
                "delete from $(outgoing_batch) where node_id=? and channel_id=? and batch_id < "
                        + "(select max(batch_id) from $(outgoing_batch) where node_id=? and channel_id=?) ");
        putSql("copyOutgoingBatchesSql",
                "insert into $(outgoing_batch)                                                                                                                 "
                        + "  (batch_id, node_id, channel_id, status, load_id, extract_job_flag, load_flag, common_flag, reload_row_count, other_row_count,           "
                        + "  last_update_hostname, last_update_time, create_time, create_by)                                                                             "
                        + "  (select batch_id, ?, channel_id, 'NE', load_id, extract_job_flag, load_flag, common_flag, reload_row_count, other_row_count,          "
                        + "   last_update_hostname, ?, create_time, 'copy' from $(outgoing_batch) where node_id=? and channel_id=? and batch_id > ?)     ");
        putSql("getAllBatchesSql", "select batch_id from $(outgoing_batch)");
        putSql("whereInProgressStatusSql", "where status in (?, ?, ?, ?, ?) ");
        putSql("updateOutgoingError",
                "update $(outgoing_error) set resolve_ignore = 1 where batch_id = ? and node_id = ?");
        putSql("updateOutgoingSetupBatchStatusByStatus",
                "update $(outgoing_batch) set status=?, last_update_time=?, last_update_hostname=? where node_id=? and load_id=? and status=? and batch_id < ?");
        putSql("updateOutgoingLoadBatchStatusByStatus",
                "update $(outgoing_batch) set status=?, last_update_time=?, last_update_hostname=? where node_id=? and load_id=? and status=? and batch_id between ? and ?");
        putSql("updateOutgoingFinalizeBatchStatusByStatus",
                "update $(outgoing_batch) set status=?, last_update_time=?, last_update_hostname=? where node_id=? and load_id=? and status=? and batch_id > ?");
        putSql("selectReadyChannels", "select distinct node_id, channel_id, thread_id from $(outgoing_batch) where status in (?, ?, ?, ?, ?, ?, ?)");
    }
}