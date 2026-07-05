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

public class NodeServiceSqlMap extends AbstractSqlMap {
    public NodeServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        putSql("findSymmetricVersionSql",
                "select symmetric_version from $(node)                      "
                        + "  where node_id in (select node_id from $(node_identity))  ");
        putSql("insertNodeIdentitySql", "" + "insert into $(node_identity) values(?)   ");
        putSql("doesNodeGroupExistSql", ""
                + "select count(*) from $(node_group) where node_group_id=?   ");
        putSql("insertNodeGroupSql", ""
                + "insert into $(node_group) (description, node_group_id) values(?, ?)   ");
        putSql("nodeChannelControlIgnoreSql", ""
                + "update $(node_channel_ctl) set ignore_enabled=? where node_id=? and target_node_id=? and   "
                + "  channel_id=?                                                              ");
        putSql("insertNodeChannelControlSql", ""
                + "insert into $(node_channel_ctl)                                   "
                + "  (node_id,target_node_id,channel_id,ignore_enabled,suspend_enabled) values(?,?,?,?,?)   ");
        putSql("insertNodeSql",
                "insert into $(node) (node_group_id, external_id, database_type, database_version, database_name, " +
                        "schema_version, symmetric_version, sync_url," +
                        "sync_enabled, batch_to_send_count, batch_in_error_count, batch_last_successful, most_recent_active_table, " +
                        "purge_outgoing_average_ms, purge_outgoing_last_run_ms, purge_outgoing_last_finish, routing_average_run_ms, routing_last_run_ms, routing_last_finish, sym_data_size, "
                        +
                        "created_at_node_id, deployment_type, deployment_sub_type, config_version, data_rows_to_send_count, " +
                        " data_rows_loaded_count,oldest_load_time, node_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        putSql("updateNodeSql",
                "update $(node) set node_group_id=?, external_id=?, database_type=?,                                                                       "
                        + "  database_version=?, database_name=?, schema_version=?, symmetric_version=?, sync_url=?, "
                        + "  sync_enabled=?, batch_to_send_count=?, batch_in_error_count=?, batch_last_successful=?, most_recent_active_table=?, "
                        + "  purge_outgoing_average_ms = ? , purge_outgoing_last_run_ms = ?, purge_outgoing_last_finish = ?, routing_average_run_ms = ?, routing_last_run_ms = ?, routing_last_finish = ?, sym_data_size = ?, "
                        + "  created_at_node_id=?, deployment_type=?, deployment_sub_type=?, config_version = ?, "
                        + "  data_rows_to_send_count = ?, data_rows_loaded_count = ?, oldest_load_time = ? where node_id = ?");
        putSql("findNodeSql", "where node_id = ?   ");
        putSql("findNodeByExternalIdSql", ""
                + "where node_group_id = ? and external_id = ? order by node_id   ");
        putSql("findEnabledNodes", "where sync_enabled = 1 order by node_id");
        putSql("findNodesWithOpenRegistrationSql",
                "where node_id in (select node_id from $(node_security) where registration_enabled=1)   ");
        putSql("findNodesCreatedByMeSql", ""
                + "where created_at_node_id=? and created_at_node_id != node_id   ");
        putSql("selectNodeSecuritySql",
                "select node_id, node_password, registration_enabled, registration_time, "
                        + "registration_not_before, registration_not_after, "
                        + "initial_load_enabled, initial_load_time, initial_load_end_time, created_at_node_id, "
                        + "rev_initial_load_enabled, rev_initial_load_time, initial_load_id, "
                        + "initial_load_create_by, rev_initial_load_id, rev_initial_load_create_by, failed_logins, "
                        + "partial_load_time, partial_load_end_time, partial_load_id, partial_load_create_by "
                        + "from $(node_security) ");
        putSql("findNodeSecurityByNodeIdSql", "where node_id = ?");
        putSql("selectExternalIdsSql",
                "select distinct(external_id) from $(node) where sync_enabled=1 order by external_id asc   ");
        putSql("findNodeSecurityWithLoadEnabledSql", "where initial_load_enabled=1 or rev_initial_load_enabled=1");
        putSql("deleteNodeSecuritySql", "delete from $(node_security) where node_id = ?");
        putSql("deleteNodeSql", "delete from $(node) where node_id = ?");
        putSql("deleteNodeHostSql", "delete from $(node_host) where node_id = ?");
        putSql("deleteNodeHostInstanceSql", "delete from $(node_host) where node_id = ? and instance_id = ?");
        putSql("deleteObsoleteNodeHostsSql", "delete from $(node_host) where node_id = ? and (heartbeat_time is null or heartbeat_time < ?)");
        putSql("deleteNodeChannelCtlSql", "delete from $(node_channel_ctl) where node_id = ? or target_node_id = ?");
        putSql("deleteIncomingErrorSql", "delete from $(incoming_error) where node_id = ?");
        putSql("deleteTableReloadRequestSql", "delete from $(table_reload_request) where source_node_id = ? or target_node_id=?");
        putSql("cancelTableReloadStatusSql", "update $(table_reload_status) set cancelled = 1, end_time = ?, "
                + "last_update_time = ? where (source_node_id = ? or target_node_id = ?) and completed = 0 and cancelled = 0");
        putSql("deleteExtractRequestSql", "delete from $(extract_request) where node_id = ? or source_node_id = ?");
        putSql("deleteNodeCommunicationSql", "delete from $(node_communication) where node_id = ?");
        putSql("setOutgoingBatchOkSql", "update $(outgoing_batch) set status='OK', error_flag=0 where node_id = ?");
        putSql("deleteIncomingBatchSql", "delete from $(incoming_batch) where node_id = ?");
        putSql("findNodeIdentitySql", "inner join $(node_identity) i on c.node_id =   "
                + "  i.node_id                                          ");
        putSql("deleteNodeIdentitySql", "delete from $(node_identity)   ");
        putSql("isNodeRegisteredSql",
                "select count(*) from $(node_security) s inner join                             "
                        + "  $(node) n on n.node_id=s.node_id where n.node_group_id=? and                 "
                        + "  n.external_id=? and s.registration_time is not null and s.registration_enabled=0   ");
        putSql("findNodesWhoTargetMeSql",
                ""
                        + "inner join $(node_group_link) d on                                          "
                        + "  c.node_group_id = d.source_node_group_id where d.target_node_group_id = ? and   "
                        + "  (d.data_event_action = ? or d.is_reversible = 1) and c.node_id not in (select node_id from $(node_identity))          "
                        + "and (c.sync_enabled = 1 or c.node_id in (select node_id from $(node_security) where registration_enabled = 1 "
                        + "and created_at_node_id = (select node_id from $(node_identity))))");
        putSql("findNodesWhoITargetSql",
                ""
                        + "inner join $(node_group_link) d on                                          "
                        + "  c.node_group_id = d.target_node_group_id where d.source_node_group_id = ? and   "
                        + "  (d.data_event_action = ? or d.is_reversible = 1) and c.node_id not in (select node_id from $(node_identity))   "
                        + "and (c.sync_enabled = 1 or c.node_id in (select node_id from $(node_security) where registration_enabled = 1 "
                        + "and created_at_node_id = (select node_id from $(node_identity))))");
        putSql("selectNodeHostPrefixSql",
                ""
                        + "select node_id, host_name, instance_id, ip_address, os_user, os_name, os_arch, os_version, available_processors,        "
                        + "  free_memory_bytes, total_memory_bytes, max_memory_bytes, java_version, java_vendor, security_mode, jdbc_version, symmetric_version,   "
                        + "  timezone_offset, heartbeat_time, last_restart_time, create_time from $(node_host) h");
        putSql("selectNodeHostByNodeIdSql", "where node_id=? order by heartbeat_time desc");
        putSql("selectNodePrefixSql",
                "select c.node_id, c.node_group_id, c.external_id, c.sync_enabled, c.sync_url,                                                                                                                                    "
                        + " c.schema_version, c.database_type, c.database_version, c.database_name, "
                        + " c.symmetric_version, c.created_at_node_id, c.batch_to_send_count, c.batch_in_error_count, "
                        + " c.deployment_type, c.deployment_sub_type, c.config_version, "
                        + " c.purge_outgoing_last_run_ms, c.purge_outgoing_average_ms, c.purge_outgoing_last_finish, c.routing_average_run_ms, c.routing_last_run_ms, c.routing_last_finish, c.sym_data_size "
                        + " from   "
                        + "  $(node) c                                                                                                                                                                                                ");
        putSql("updateNodeSecuritySql", "update $(node_security) set node_password = ?, registration_enabled = ?, "
                + "registration_time = ?, registration_not_before = ?, registration_not_after = ?, "
                + "initial_load_enabled = ?, initial_load_time = ?, initial_load_end_time = ?, created_at_node_id = ?, "
                + "rev_initial_load_enabled=?, rev_initial_load_time=?, initial_load_id=?, "
                + " initial_load_create_by=?, rev_initial_load_id=?, rev_initial_load_create_by=?, failed_logins=?, "
                + "partial_load_time=?, partial_load_end_time=?, partial_load_id=?, partial_load_create_by=? "
                + " where node_id = ?");
        putSql("insertNodeSecuritySql",
                ""
                        + "insert into $(node_security) (node_id, node_password, created_at_node_id) values (?, ?, ?)   ");
        putSql("insertNodeHostSql",
                "insert into $(node_host)                                                                                                                                                                                                                                            "
                        + "  (ip_address, instance_id, cluster_partition_id, os_user, os_name, os_arch, os_version, available_processors, free_memory_bytes, total_memory_bytes, max_memory_bytes, java_version, java_vendor, security_mode, jdbc_version, symmetric_version, timezone_offset, heartbeat_time, last_restart_time, create_time, node_id, host_name)"
                        + "  values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        putSql("updateNodeHostSql",
                ""
                        + "update $(node_host) set                                                                                                          "
                        + "  ip_address=?, instance_id=?, cluster_partition_id=?, os_user=?, os_name=?, os_arch=?, os_version=?, available_processors=?, free_memory_bytes=?,                            "
                        + "  total_memory_bytes=?, max_memory_bytes=?, java_version=?, java_vendor=?, security_mode=?, jdbc_version=?, symmetric_version=?, timezone_offset=?, heartbeat_time=?,   "
                        + "  last_restart_time=? where node_id=? and host_name=?                                                                                  ");
        putSql("findNodeHeartbeatsSql",
                "select h.node_id, h.heartbeat_time, h.timezone_offset from $(node_host) h inner join $(node) n on h.node_id=n.node_id"
                        + " where n.sync_enabled = 1 and h.heartbeat_time = (select max(hh.heartbeat_time) from $(node_host) hh where hh.node_id = h.node_id)"
                        + " and (exists (select * from $(node) nn where nn.node_id=n.created_at_node_id) OR n.created_at_node_id is null)");
        putSql("findRootNodeSql",
                "where created_at_node_id = node_id or created_at_node_id is null order by created_at_node_id desc");
    }
}