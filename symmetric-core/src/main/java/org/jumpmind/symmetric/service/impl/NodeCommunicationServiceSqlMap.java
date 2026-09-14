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

public class NodeCommunicationServiceSqlMap extends AbstractSqlMap {
    public NodeCommunicationServiceSqlMap(IDatabasePlatform platform,
            Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        putSql("deleteSql", "delete from $(node_communication)");
        putSql("clearLocksOnRestartSql", "update $(node_communication) set lock_time=null where locking_server_id=? and lock_time is not null");
        putSql("selectNodeIdsWithUnsentBatchsSql", "select distinct(node_id) from $(outgoing_batch) where status in (?,?,?,?,?,?,?)");
        putSql("selectNodeCommunicationSql",
                "select * from $(node_communication) where communication_type=? order by node_priority DESC,last_lock_time");
        putSql("selectNodeCommunicationPullSql",
                "select * from $(node_communication) where communication_type=? order by node_priority DESC,batch_to_send_count DESC,last_lock_time");
        putSql("selectNodeCommunicationByNodeAndChannelSql",
                "select * from $(node_communication) where node_id=? and queue=? and communication_type=?");
        putSql("insertNodeCommunicationSql", "insert into $(node_communication) ("
                + "lock_time,locking_server_id,last_lock_millis,success_count,fail_count,"
                + "total_success_count,total_fail_count,total_success_millis,total_fail_millis,last_lock_time,batch_to_send_count,node_priority,"
                + "node_id,queue,communication_type) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        putSql("updateNodeCommunicationSql",
                "update $(node_communication) set lock_time=?,locking_server_id=?,last_lock_millis=?,"
                        + "success_count=?,fail_count=?,total_success_count=?,total_fail_count=?,"
                        + "total_success_millis=?,total_fail_millis=?, last_lock_time=?, batch_to_send_count=?, node_priority=? "
                        + "where node_id=? and queue=? and communication_type=?");
        putSql("deleteNodeCommunicationSql",
                "delete from $(node_communication) where node_id=? and queue=? and communication_type=?");
        putSql("aquireLockSql",
                "update $(node_communication) set locking_server_id=?, lock_time=?, last_lock_time=? where "
                        + "  node_id=? and queue=? and communication_type=? and "
                        + " (lock_time is null or lock_time < ?)   ");
        putSql("acquireLockFromStaleSql",
                "update $(node_communication) set locking_server_id=?, lock_time=?, last_lock_time=? where "
                        + "  node_id=? and queue=? and communication_type=? and locking_server_id=?   ");
        putSql("clearLocksForServerSql",
                "update $(node_communication) set locking_server_id = null, lock_time = null where locking_server_id = ?");
    }
}