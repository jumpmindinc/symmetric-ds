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

public class TriggerRouterServiceSqlMap extends AbstractSqlMap {
    public TriggerRouterServiceSqlMap(IDatabasePlatform platform,
            Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        // @formatter:off

        putSql("countTriggerRoutersByRouterIdSql",
           "select count(*) from $(trigger_router) where router_id=?   ");

        putSql("countTriggerRoutersByTriggerIdSql", ""
                + "select count(*) from $(trigger_router) where trigger_id=?   ");

        putSql("countTriggerByTriggerIdSql", ""
                + "select count(*) from $(trigger) where trigger_id=?   ");

        putSql("countTriggerByTableNameSql", ""
                + "select count(*) from $(trigger) where source_table_name=? OR source_table_name=? OR source_table_name=? ");
        
        putSql("countTriggerByTableNameFromTriggerHistSql", ""
                + "select count(*) from $(trigger_hist) where (source_table_name=? OR source_table_name=? OR source_table_name=?) and inactive_time is null ");

        putSql("deleteRouterSql", "" + "delete from $(router) where router_id=?   ");
        
        putSql("deleteAllRoutersSql", "" + "delete from $(router)");

        putSql("inactivateTriggerHistorySql", "update $(trigger_hist) set inactive_time = ?, error_message = ? where trigger_hist_id = ?");

        putSql("selectTriggersSql", "select * from $(trigger) order by trigger_id asc");
        
        putSql("selectTriggersWhereTriggerIdLikeSql", "select * from $(trigger) where trigger_id like ?");

        putSql("selectTriggerRoutersSql", ""
                + "from $(trigger_router) tr                                 "
                + "  inner join $(trigger) t on tr.trigger_id=t.trigger_id   "
                + "  inner join $(router) r on tr.router_id=r.router_id      ");

        putSql("selectTriggerRoutersColumnList", " tr.* ");

        putSql("selectRoutersColumnList",
                ""
                        + "  r.sync_on_insert as r_sync_on_insert,r.sync_on_update as r_sync_on_update,r.sync_on_delete as r_sync_on_delete,                            "
                        + "  r.target_catalog_name,r.source_node_group_id,r.target_schema_name,r.target_table_name,r.target_node_group_id,r.router_expression,        "
                        + "  r.router_type,r.router_id,r.create_time as r_create_time,r.last_update_time as r_last_update_time,r.last_update_by as r_last_update_by, "
                        + "  r.use_source_catalog_schema ");
        
        putSql("selectTriggerHistIdSql", "select trigger_hist_id from $(trigger_hist) ");

        putSql("selectGroupTriggersSql", ""
                + "where r.source_node_group_id = ? or r.target_node_group_id = ? order by t.channel_id   ");

        putSql("activeTriggersForSourceNodeGroupSql", "" + "where r.source_node_group_id = ?   ");

        putSql("activeTriggersForTargetNodeGroupSql", "where r.target_node_group_id = ?");
        
        putSql("activeTriggersForSourceAndTargetNodeGroupsSql", "where r.source_node_group_id = ? and r.target_node_group_id = ?");

        putSql("activeTriggersForReloadSql", ""
                + "where r.source_node_group_id = ? and                          "
                + "  r.target_node_group_id = ? and t.channel_id not in (?, ?) and tr.enabled=1 order by   "
                + "  tr.initial_load_order                                       ");

        putSql("activeTriggerHistSql", "where inactive_time is null");

        putSql("errorTriggerHistSql", "where inactive_time is not null and error_message is not null order by trigger_hist_id");

        putSql("multipleActiveTriggerHistSql", ""
                + "select trigger_id, source_table_name, source_catalog_name, source_schema_name "
                + "from $(trigger_hist) "
                + "where inactive_time is null "
                + "group by trigger_id, source_table_name, source_catalog_name, source_schema_name "
                + "having count(*) > 1");

        putSql("activeTriggerHistSqlByTriggerId", ""
                + "where trigger_id=? and inactive_time is null   ");
             
        putSql("allTriggerHistSql",
                ""
                        + "select trigger_hist_id,trigger_id,source_table_name,table_hash,create_time,pk_column_names,column_names,is_missing_pk,last_trigger_build_reason,name_for_delete_trigger,name_for_insert_trigger,name_for_update_trigger,source_schema_name,source_catalog_name,trigger_row_hash,trigger_template_hash,error_message   "
                        + "  from $(trigger_hist)                                                                                                                                                                                                                                                      ");
        putSql("allTriggerHistBackwardsCompatibleSql",
                        "select trigger_hist_id,trigger_id,source_table_name,table_hash,create_time,pk_column_names,column_names,last_trigger_build_reason,name_for_delete_trigger,name_for_insert_trigger,name_for_update_trigger,source_schema_name,source_catalog_name,trigger_row_hash,trigger_template_hash,error_message " +
                        "from $(trigger_hist) ");
        
        putSql("triggerHistBySourceTableWhereSql", ""
                + "where (source_table_name=? OR source_table_name=? OR source_table_name=?) and inactive_time is null   ");

        putSql("latestTriggerHistSql",
                ""
                        + "select                                                                                                                                                                                                                                                                                             "
                        + "  trigger_hist_id,trigger_id,source_table_name,table_hash,create_time,pk_column_names,column_names,is_missing_pk,last_trigger_build_reason,name_for_delete_trigger,name_for_insert_trigger,name_for_update_trigger,source_schema_name,source_catalog_name,trigger_row_hash,trigger_template_hash,error_message   "
                        + "  from $(trigger_hist) where trigger_id=? and source_table_name=? and inactive_time is null order by trigger_hist_id desc                                                                                                                                                                          ");

        putSql("triggerHistSql",
                ""
                        + "select                                                                                                                                                                                                                                                                       "
                        + "  trigger_hist_id,trigger_id,source_table_name,table_hash,create_time,pk_column_names,column_names,is_missing_pk,last_trigger_build_reason,name_for_delete_trigger,name_for_insert_trigger,name_for_update_trigger,source_schema_name,source_catalog_name,trigger_row_hash,trigger_template_hash,error_message   "
                        + "  from $(trigger_hist) where trigger_hist_id = ?                                                                                                                                                                                                                       ");

        putSql("insertTriggerHistorySql",
                ""
                        + "insert into $(trigger_hist)                                                                                                                                                                                                                              "
                        + "  (trigger_hist_id, trigger_id,source_table_name,table_hash,create_time,column_names,pk_column_names,is_missing_pk,last_trigger_build_reason,name_for_delete_trigger,name_for_insert_trigger,name_for_update_trigger,source_schema_name,source_catalog_name,trigger_row_hash,trigger_template_hash,error_message,inactive_time)   "
                        + "  values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

        putSql("deleteTriggerSql", "" + "delete from $(trigger) where trigger_id=?   ");

        putSql("deleteAllTriggersSql", "" + "delete from $(trigger)");

        putSql("deleteTriggerHistorySql", ""
                + "delete from $(trigger_hist) where trigger_hist_id=?   ");

        putSql("insertTriggerSql",
                ""
                        + "insert into $(trigger)                                                                                                         "
                        + "  (source_catalog_name,source_schema_name,source_table_name,channel_id,reload_channel_id,sync_on_update,sync_on_insert,sync_on_delete,                 "
                        + "  sync_on_incoming_batch,use_stream_lobs,use_capture_lobs,use_capture_old_data,use_handle_key_updates,name_for_update_trigger,name_for_insert_trigger,name_for_delete_trigger,   "
                        + "  sync_on_update_condition,sync_on_insert_condition,sync_on_delete_condition,custom_before_update_text,custom_before_insert_text,custom_before_delete_text,custom_on_update_text,custom_on_insert_text,custom_on_delete_text,tx_id_expression,excluded_column_names,included_column_names,sync_key_names,            "
                        + "  create_time,last_update_by,last_update_time,external_select,channel_expression,stream_row,capture_changes_only,time_based_column_name,trigger_id)                                                            "
                        + "  values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)                                                                             ");

        putSql("updateTriggerSql",
                ""
                        + "update $(trigger)                                                                                                                                "
                        + "  set source_catalog_name=?,source_schema_name=?,source_table_name=?,                                                                            "
                        + "  channel_id=?,reload_channel_id=?,sync_on_update=?,sync_on_insert=?,sync_on_delete=?,                                                                               "
                        + "  sync_on_incoming_batch=?,use_stream_lobs=?,use_capture_lobs=?,use_capture_old_data=?,use_handle_key_updates=?,name_for_update_trigger=?,name_for_insert_trigger=?,        "
                        + "  name_for_delete_trigger=?,sync_on_update_condition=?,sync_on_insert_condition=?,sync_on_delete_condition=?,custom_before_update_text=?,custom_before_insert_text=?,custom_before_delete_text=?,custom_on_update_text=?,custom_on_insert_text=?,custom_on_delete_text=?,                                             "
                        + "  tx_id_expression=?,excluded_column_names=?,included_column_names=?,sync_key_names=?,create_time=?,last_update_by=?,last_update_time=?,external_select=?,channel_expression=?,stream_row=?, capture_changes_only=?,time_based_column_name=?  "
                        + "  where trigger_id=?                                                                                                            ");

        putSql("insertRouterSql",
                ""
                        + "insert into $(router)                                                                                                           "
                        + "  (target_catalog_name,target_schema_name,target_table_name,source_node_group_id,target_node_group_id,                                "
                        + "  router_type,router_expression,sync_on_update,sync_on_insert,sync_on_delete,use_source_catalog_schema, "
                        + "  create_time,last_update_by,last_update_time,router_id)   "
                        + "  values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

        putSql("updateRouterSql",
                ""
                        + "update $(router)                                                                                           "
                        + "  set target_catalog_name=?,target_schema_name=?,target_table_name=?,source_node_group_id=?,                     "
                        + "  target_node_group_id=?,router_type=?,router_expression=?,sync_on_update=?,sync_on_insert=?,sync_on_delete=?,   "
                        + "  use_source_catalog_schema=?, last_update_by=?,last_update_time=? "
                        + "  where router_id=?                                                                                              ");

        putSql("deleteTriggerRouterSql", ""
                + "delete from $(trigger_router) where trigger_id=? and router_id=?   ");
        
        putSql("deleteTriggerRoutersByTriggerIdSql", ""
                + "delete from $(trigger_router) where trigger_id=?   ");
        
        putSql("deleteTriggerRoutersByRouterIdSql", ""
                + "delete from $(trigger_router) where router_id=?   ");

        putSql("deleteTriggerRoutersByRouterSql", "delete from $(trigger_router) where router_id = ?");

        putSql("deleteAllTriggerRoutersSql", "delete from $(trigger_router)");

        putSql("insertTriggerRouterSql",
                ""
                        + "insert into $(trigger_router)                                                                                             "
                        + "  (initial_load_order,initial_load_select,initial_load_delete_stmt,ping_back_enabled,create_time,last_update_by,last_update_time,enabled,description,data_refresh_type,data_refresh_job_name,trigger_id,router_id)   "
                        + "  values(?,?,?,?,?,?,?,?,?,?,?,?,?)                                                                                                       ");

        putSql("updateTriggerRouterSql",
                ""
                        + "update $(trigger_router)                                                                             "
                        + "  set initial_load_order=?,initial_load_select=?,initial_load_delete_stmt=?,ping_back_enabled=?,create_time=?,last_update_by=?,last_update_time=?,enabled=?,description=?,data_refresh_type=?,data_refresh_job_name=?   "
                        + "  where trigger_id=? and router_id=?                                                                       ");

        putSql("updateDataRefreshJobNameSql",
                "update $(trigger_router) set data_refresh_job_name=? where data_refresh_job_name=?");

        putSql("selectTriggerTargetSql",
                ""
                        + "where t.source_table_name = ? and r.target_node_group_id = ? and t.channel_id = ? and r.source_node_group_id = ?   ");

        putSql("selectTriggerRouterSql", "" + "where t.trigger_id=? and r.router_id=?   ");
        
        putSql("selectTriggerRoutersByTriggerIdSql", "" + "where t.trigger_id=?   ");
        
        putSql("selectTriggerRoutersByRouterIdSql", "" + "where r.router_id=?   ");

        putSql("selectRouterSql", "" + "from $(router) r where r.router_id=?   ");

        putSql("selectRoutersSql", "" + "from $(router) r order by r.router_id   ");
        
        putSql("selectRoutersWhereRouterIdLikeSql", "" + "from $(router) r where r.router_id like ?   ");

        putSql("selectRouterByNodeGroupLinkWhereSql",
                "from $(router) r where r.source_node_group_id=? and r.target_node_group_id=? order by r.router_id   ");

        putSql("selectTriggerByIdSql", "" + "where t.trigger_id = ?   ");

        putSql("selectMaxTriggerLastUpdateTime" ,"select max(last_update_time) from $(trigger) where last_update_time is not null" );
        putSql("selectMaxRouterLastUpdateTime" ,"select max(last_update_time) from $(router) where last_update_time is not null" );
        putSql("selectMaxTriggerRouterLastUpdateTime" ,"select max(last_update_time) from $(trigger_router) where last_update_time is not null" );
        
        putSql("updateTriggerRouterIdSql0", "update $(trigger_router_grouplet) set trigger_id=? where trigger_id=?");
        putSql("updateTriggerRouterIdSql1", "update $(trigger_router_grouplet) set router_id=? where router_id=?");
        putSql("deleteTriggerRouterGroupletByRouterSql", "delete from $(trigger_router_grouplet) where router_id=?");

        putSql("updateFileTriggerRouterSql", "update $(file_trigger_router) set router_id=? where router_id=?");
        putSql("deleteFileTriggerRoutersByRouterSql", "delete from $(file_trigger_router) where router_id=?");
    }

}
