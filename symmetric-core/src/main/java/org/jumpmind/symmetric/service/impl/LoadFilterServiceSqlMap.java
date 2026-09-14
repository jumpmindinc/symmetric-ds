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

public class LoadFilterServiceSqlMap extends AbstractSqlMap {
    public LoadFilterServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        // @formatter:off
        putSql("selectLoadFilterTable" ,"" + 
"select                                                               " + 
"  load_filter_id, load_filter_type,                                  " +
"  source_node_group_id, target_node_group_id,                        " + 
"  target_catalog_name, target_schema_name,                           " + 
"  target_table_name,                                                 " +
"  filter_on_insert, filter_on_update, filter_on_delete,              " +
"  before_write_script, after_write_script,                           " +
"  batch_complete_script, batch_commit_script, batch_rollback_script, " +
"  handle_error_script,                                               " +
"  create_time, last_update_by, last_update_time,                     " +
"  load_filter_order, fail_on_error                                   " +
"  from $(load_filter)                                                "); 
        
        putSql("orderByLoadFilterOrderSql", "order by load_filter_order");
        
        putSql("whereLoadFilterIdLikeSql", "where load_filter_id like ?");

        putSql("updateLoadFilterSql" ,"" + 
"update                       " + 
"  $(load_filter)             " + 
"  set                        " + 
"  after_write_script=?,      " + 
"  batch_commit_script=?,     " + 
"  batch_complete_script=?,   " + 
"  batch_rollback_script=?,   " + 
"  before_write_script=?,     " + 
"  handle_error_script=?,     " +
"  load_filter_order=?,       " + 
"  load_filter_type=?,        " + 
"  source_node_group_id=?,    " + 
"  target_node_group_id=?,    " + 
"  target_catalog_name=?,     " + 
"  target_schema_name=?,      " + 
"  target_table_name=?,       " + 
"  filter_on_insert=?,        " +
"  filter_on_update=?,        " +
"  filter_on_delete=?,        " +
"  fail_on_error=?,           " +
"  last_update_by=?,          " +
"  last_update_time=?        " +
"  where                      " + 
"  load_filter_id=?           " );

        putSql("insertLoadFilterSql" ,"" + 
"insert into $(load_filter) (" + 
"  after_write_script,      " + 
"  batch_commit_script,     " + 
"  batch_complete_script,   " + 
"  batch_rollback_script,   " + 
"  before_write_script,     " +
"  handle_error_script,    " +
"  load_filter_order,       " + 
"  load_filter_type,        " + 
"  source_node_group_id,    " + 
"  target_node_group_id,    " + 
"  target_catalog_name,     " + 
"  target_schema_name,      " + 
"  target_table_name,       " + 
"  filter_on_insert,        " +
"  filter_on_update,        " +
"  filter_on_delete,        " +
"  fail_on_error,           " +
"  last_update_by,          " +
"  last_update_time,        " +
"  create_time,             " +
"  load_filter_id           " +
"  ) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)  " );

        putSql("deleteLoadFilterSql" ,"" + 
"delete from $(load_filter) where   " + 
"  load_filter_id=? " );

        putSql("deleteAllLoadFiltersSql", "delete from $(load_filter)");

        putSql("selectMaxLastUpdateTime" ,"" + 
"select max(last_update_time) from $(load_filter) where last_update_time is not null" );     
        
        // @formatter:on
    }
}