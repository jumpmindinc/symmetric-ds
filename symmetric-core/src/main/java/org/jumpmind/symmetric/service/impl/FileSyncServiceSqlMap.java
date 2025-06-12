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

public class FileSyncServiceSqlMap extends AbstractSqlMap {
    public FileSyncServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        // @formatter:off

        putSql("selectFileTriggersSql",
                " select trigger_id, base_dir, recurse,                                       " +
                "        includes_files, excludes_files,                                      " +
                "        sync_on_create, sync_on_modified, sync_on_delete,                    " +
                "        sync_on_ctl_file, delete_after_sync,                                 " +
                "        before_copy_script,                                                  " +
                "        after_copy_script,                                                   " +
                "        create_time, last_update_by,                                         " +
                "        last_update_time, channel_id, reload_channel_id                      " +
                " from $(file_trigger)                                                        ");

        putSql("triggerIdWhere", "where trigger_id=?");
        
        putSql("triggerIdWhereLike", "where trigger_id like ?");

        putSql("updateFileTriggerSql",
                " update $(file_trigger) set base_dir=?, recurse=?, includes_files=?,         " +
                "  excludes_files=?, sync_on_create=?, sync_on_modified=?, sync_on_delete=?,  " +
                "  sync_on_ctl_file=?, delete_after_sync=?,                                   " +
                "  before_copy_script=?, after_copy_script=?,                                 " +
                "  last_update_by=?, last_update_time=?, channel_id=?, reload_channel_id=?    " +
                "where trigger_id=?                                                           ");

        putSql("insertFileTriggerSql",
                " insert into $(file_trigger) (base_dir, recurse, includes_files,             " +
                "  excludes_files, sync_on_create, sync_on_modified, sync_on_delete,          " +
                "  sync_on_ctl_file, delete_after_sync,                                       " +
                "  before_copy_script, after_copy_script,                                     " +
                "  last_update_by, last_update_time, trigger_id, create_time,                 " +
                "channel_id, reload_channel_id)                                               " +
                " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)                                   ");

        putSql("selectFileSnapshotSql",
                " select trigger_id, router_id, channel_id, reload_channel_id, relative_dir, file_name, " +
                " last_event_type, crc32_checksum, " +
                "  file_size, file_modified_time, create_time, last_update_time, last_update_by        " +
                " from $(file_snapshot) where trigger_id=? and router_id=?                             ");
        
        putSql("relativeDirWhere", "and relative_dir=?");

        putSql("updateFileSnapshotSql",
                " update $(file_snapshot) set   " +
                        "  last_event_type=?, crc32_checksum=?,                                 " +
                        "  file_size=?, file_modified_time=?, last_update_time=?,               " +
                        "  last_update_by=?, channel_id=?, reload_channel_id=?                  " +
                        " where                                                                 " +
                        "  trigger_id=? and router_id=? and relative_dir=? and file_name=?         ");

        putSql("updateFileIncoming",
                " update $(file_incoming) set                                                   " +
                        "  node_id=?,                                                           " +
                        "  file_modified_time=?,                                                " +
                        "  last_event_type=?                                                    " +
                        " where                                                                 " +
                        "  relative_dir=? and file_name=?                                          ");

        putSql("insertFileIncoming",
                " insert into $(file_incoming) (node_id, file_modified_time, last_event_type, relative_dir, file_name) " +
                "   values(?,?,?,?,?)                                                                             ");

        putSql("deleteFileIncoming",
                " delete from $(file_incoming)");

        putSql("findNodeIdFromFileIncoming",
                " select node_id from $(file_incoming) where relative_dir=? and file_name=? and file_modified_time=?");

        putSql("deleteFileSnapshotSql",
                " delete from $(file_snapshot)                                                  " +
                        " where                                                                 " +
                        "  trigger_id=? and router_id=? and relative_dir=? and file_name=?         ");

        putSql("insertFileSnapshotSql",
                " insert into $(file_snapshot) (                                                " +
                "  last_event_type, crc32_checksum,                                             " +
                "  file_size, file_modified_time, create_time, last_update_time,                " +
                "  last_update_by, channel_id, reload_channel_id, trigger_id, router_id, relative_dir, file_name   " +
                " ) values(?,?,?,?,?,?,?,?,?,?,?,?,?)                                                 ");

        putSql("selectFileTriggerRoutersSql",
                " select                                                                        " +
                "  tr.trigger_id as trigger_id, tr.router_id as router_id, enabled,             " +
                "  initial_load_enabled, target_base_dir,                                       " +
                "  conflict_strategy, tr.create_time as create_time,                            " +
                "  tr.last_update_by as last_update_by, tr.last_update_time as last_update_time " +
                " from $(file_trigger_router) tr                                                ");

        putSql("whereTriggerRouterId", "where trigger_id=? and router_id=?");
        
        putSql("whereTriggerId", "where trigger_id=?");

        putSql("fileTriggerRoutersForCurrentNodeWhere", " " +
                " inner join $(router) r on " +
                "  tr.router_id=r.router_id " +
                " where r.source_node_group_id=?");

        putSql("updateFileTriggerRouterSql",
                " update $(file_trigger_router) set                                             " +
                        "  enabled=?, initial_load_enabled=?, target_base_dir=?,                " +
                        "  conflict_strategy=?, last_update_by=?, last_update_time=?            " +
                " where trigger_id=? and router_id=?                                            ");

        putSql("insertFileTriggerRouterSql",
                " insert into $(file_trigger_router) (                                          " +
                        "  enabled, initial_load_enabled, target_base_dir,                      " +
                        "  conflict_strategy, create_time, last_update_by,                      " +
                        "  last_update_time, trigger_id, router_id                              " +
                " ) values(?,?,?,?,?,?,?,?,?)                                                   ");

        putSql("deleteFileTriggerRouterSql", ""
                + "delete from $(file_trigger_router) where trigger_id=? and router_id=? ");

        putSql("deleteAllFileTriggerRoutersSql", "delete from $(file_trigger_router)");

        putSql("deleteFileTriggerSql", "" + "delete from $(file_trigger) where trigger_id=?   ");

        putSql("deleteAllFileTriggersSql", "" + "delete from $(file_trigger)");

        putSql("selectMaxFileTriggerLastUpdateTime" ,"select max(last_update_time) from $(file_trigger) where last_update_time is not null" );
        putSql("selectMaxRouterLastUpdateTime" ,"select max(last_update_time) from $(router) where last_update_time is not null" );
        putSql("selectMaxFileTriggerRouterLastUpdateTime" ,"select max(last_update_time) from $(file_trigger_router) where last_update_time is not null" );
        
        putSql("updateFileTriggerIdSql", "update $(file_trigger_router) set trigger_id=? where trigger_id=?");
        
        putSql("countEntriesInFileSnapshot", "select count(*) from $(file_snapshot)");
    }


}
