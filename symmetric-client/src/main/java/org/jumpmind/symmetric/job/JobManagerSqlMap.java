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
package org.jumpmind.symmetric.job;

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.service.impl.AbstractSqlMap;

public class JobManagerSqlMap extends AbstractSqlMap {
    public JobManagerSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        putSql("loadCustomJobs",
                "select * from $(job) order by job_type, job_name");
        putSql("insertJobSql", "insert into $(job) (description, job_type, job_expression, implementation, "
                + "default_auto_start, default_schedule, node_group_id, is_clustered, "
                + "create_by, create_time, last_update_by, last_update_time, job_name) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        putSql("updateJobSql", "update $(job) set description = ?, job_type = ?, job_expression = ?, implementation = ?, "
                + "default_auto_start = ?, default_schedule = ?, node_group_id = ?, is_clustered = ?, "
                + "create_by = ?, last_update_by = ?, last_update_time = ? "
                + "where job_name = ?");
        putSql("deleteJobSql", "delete from $(job) where job_name = ? and job_type <> 'BUILT_IN'");
        putSql("deleteAllJobsSql", "delete from $(job)");
    }
}
