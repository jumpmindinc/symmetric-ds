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

import org.jumpmind.db.platform.IDatabasePlatform;

public class ParameterServiceSqlMap extends AbstractSqlMap {
    public ParameterServiceSqlMap(IDatabasePlatform platform, String tablePrefix) {
        super(platform, tablePrefix);
        putSql("updateParameterSql", "" +
                "update $(parameter) set param_value=?, last_update_by=?, last_update_time=? " +
                " where external_id=? and node_group_id=? and param_key=?");
        putSql("insertParameterSql", "" +
                "insert into $(parameter) (external_id, node_group_id, param_key, param_value, last_update_by, create_time, last_update_time)   " +
                "  values(?, ?, ?, ?, ?, ?, ?)                                                                  ");
        putSql("selectParametersByNodeGroupAndExternalIdSql", "" +
                "select param_key, param_value from $(parameter) where external_id=? and   " +
                "  node_group_id=?                                                                    ");
        putSql("selectParametersByNodeGroupIdSql", "" +
                "select param_key, param_value from $(parameter) where                                " +
                "  node_group_id=?                                                                    ");
        putSql("selectParametersByExternalIdSql", "" +
                "select param_key, param_value from $(parameter) where external_id=?                  ");
        putSql("selectParametersSql", "" +
                "select param_key, param_value, external_id, node_group_id from $(parameter)   " +
                "  order by node_group_id, external_id                                                                      ");
        putSql("selectParametersByKeySql", "" +
                "select param_key, param_value, external_id, node_group_id from $(parameter) where param_key=?   " +
                "  order by node_group_id, external_id                                                                      ");
        putSql("deleteParameterSql", "" +
                "delete from $(parameter) where external_id=? and   " +
                "  node_group_id=? and param_key=?                             ");
        putSql("deleteParameterByKeySql", "delete from $(parameter) where param_key=?");
        putSql("deleteAllParametersSql", "delete from $(parameter)");
        putSql("selectMaxLastUpdateTime", "" +
                "select max(last_update_time) from $(parameter) where last_update_time is not null");
    }
}