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
package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobManagerSqlMapTest {
    private JobManagerSqlMap sqlMap;

    @BeforeEach
    void setUp() {
        sqlMap = new JobManagerSqlMap(null, Map.of("job", "sym_job"));
    }

    @Test
    void testGetSql_loadCustomJobs() {
        assertEquals("select * from sym_job order by job_type, job_name", sqlMap.getSql("loadCustomJobs"));
    }

    @Test
    void testGetSql_insertJobSql() {
        assertEquals("insert into sym_job (description, job_type, job_expression, implementation, "
                + "default_auto_start, default_schedule, node_group_id, is_clustered, "
                + "create_by, create_time, last_update_by, last_update_time, job_name) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", sqlMap.getSql("insertJobSql"));
    }

    @Test
    void testGetSql_updateJobSql() {
        assertEquals("update sym_job set description = ?, job_type = ?, job_expression = ?, implementation = ?, "
                + "default_auto_start = ?, default_schedule = ?, node_group_id = ?, is_clustered = ?, "
                + "create_by = ?, last_update_by = ?, last_update_time = ? "
                + "where job_name = ?", sqlMap.getSql("updateJobSql"));
    }

    @Test
    void testGetSql_deleteJobSql() {
        assertEquals("delete from sym_job where job_name = ? and job_type <> 'BUILT_IN'", sqlMap.getSql("deleteJobSql"));
    }

    @Test
    void testGetSql_deleteAllJobsSql() {
        assertEquals("delete from sym_job", sqlMap.getSql("deleteAllJobsSql"));
    }
}
