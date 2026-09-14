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

public class DataLoaderServiceSqlMap extends AbstractSqlMap {
    DataLoaderServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        // @formatter:off
        
        putSql("updateConflictSettingsSql", 
          "update $(conflict) set                                                                        " +
          "source_node_group_id=?, target_node_group_id=?,                                               " +
          "target_channel_id=?, target_catalog_name=?, target_schema_name=?, target_table_name=?,        " +
          "detect_type=?, resolve_type=?, ping_back=?, resolve_changes_only=?,                           " +
          "resolve_row_only=?, detect_expression=?,                                                      " +
          "last_update_by=?, last_update_time=? where conflict_id=?                                      ");
        
        putSql("insertConflictSettingsSql", 
          "insert into $(conflict) (                                                                      " +
          "source_node_group_id, target_node_group_id,                                                    " +
          "target_channel_id, target_catalog_name, target_schema_name, target_table_name,                 " +
          "detect_type, resolve_type, ping_back,                                                          " +
          "resolve_changes_only, resolve_row_only, detect_expression,                                     " +
          "create_time, last_update_by, last_update_time, conflict_id)                                    " +
          "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        
        putSql("deleteConflictSettingsSql", "delete from $(conflict) where conflict_id=?");
        
        putSql("deleteAllConflictSettingsSql", "delete from $(conflict)");
        
        putSql("selectConflictSettingsSql",
                "select " +
                "source_node_group_id, target_node_group_id,                                              " +
                "target_channel_id, target_catalog_name, target_schema_name, target_table_name,           " +
                "detect_type, resolve_type, ping_back,                                                    " +
                "resolve_changes_only, resolve_row_only, detect_expression,                               " +
                "create_time, last_update_by, last_update_time, conflict_id from $(conflict)              ");

        putSql("selectIncomingErrorSql",
                "select batch_id, node_id, failed_row_number, failed_line_number, target_catalog_name, target_schema_name, " +
                "target_table_name, event_type, binary_encoding, column_names, pk_column_names, row_data, " +
                "old_data, cur_data, resolve_data, resolve_ignore, conflict_id, " +
                "create_time, last_update_by, last_update_time from $(incoming_error) where batch_id = ? and node_id = ?");

        putSql("selectCurrentIncomingErrorSql",
                "select e.batch_id, e.node_id, e.failed_row_number, e.failed_line_number, e.target_catalog_name, e.target_schema_name, " +
                "e.target_table_name, e.event_type, e.binary_encoding, e.column_names, e.pk_column_names, e.row_data, " +
                "e.old_data, e.cur_data, e.resolve_data, e.resolve_ignore, e.conflict_id, " +
                "e.create_time, e.last_update_by, e.last_update_time " +
                "from $(incoming_error) e inner join $(incoming_batch) b on b.batch_id = e.batch_id " +
                "and b.node_id = e.node_id and b.failed_row_number = e.failed_row_number " +
                "where b.batch_id = ? and b.node_id = ?");
        
        putSql("selectMaxLastUpdateTime" ,"" + 
"select max(last_update_time) from $(conflict) where last_update_time is not null" );       

        putSql("insertIncomingErrorSql", 
                "insert into $(incoming_error) " +
                "(batch_id, node_id, failed_row_number, failed_line_number, target_catalog_name, target_schema_name, " +
                "target_table_name, event_type, binary_encoding, column_names, pk_column_names, row_data, old_data, cur_data, resolve_data, resolve_ignore, conflict_id, " +
                "create_time, last_update_by, last_update_time) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        putSql("updateIncomingErrorSql",
                "update $(incoming_error) set resolve_data = ?, resolve_ignore = ? " +
                "where batch_id = ? and node_id = ? and failed_row_number = ?");
        
        putSql("whereConflictIdLike", "where conflict_id like ?");

        // @formatter:on
    }
}
