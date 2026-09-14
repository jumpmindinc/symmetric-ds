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

public class ClusterServiceSqlMap extends AbstractSqlMap {
    public ClusterServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        putSql("deleteSql", "delete from $(lock)");
        putSql("insertCompleteLockSql",
                "insert into $(lock) (lock_action, lock_type, locking_server_id, lock_time, shared_count, shared_enable, last_lock_time, last_locking_server_id) values(?,?,?,?,?,?,?,?)");
        putSql("selectLocksSql",
                "select lock_action, lock_type, locking_server_id, lock_time, shared_count, shared_enable, last_lock_time, last_locking_server_id from $(lock)");
        putSql("updateLastLockTimeSql",
                "update $(lock) set last_lock_time = ?, last_locking_server_id = ? where lock_action = ?");
        putSql("clearLocksForServerSql",
                "update $(lock) set locking_server_id = null, lock_time = null, shared_count = 0, shared_enable = 0 where locking_server_id = ?");
    }
}