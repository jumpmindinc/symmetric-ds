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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.model.JobDefinition.JobType;
import org.junit.jupiter.api.Test;

class JobMapperTest {
    private JobMapper mapper = new JobMapper();

    @Test
    void testMapRow_withValidRow_mapsAllFields() {
        Date createTime = new Date(1000L);
        Date lastUpdateTime = new Date(2000L);
        Row row = new Row(14);
        row.put("job_name", "Push");
        row.put("job_type", "JAVA");
        row.put("requires_registration", true);
        row.put("job_expression", "com.foo.Bar");
        row.put("description", "Push job");
        row.put("default_auto_start", true);
        row.put("default_schedule", "60000");
        row.put("node_group_id", "corp");
        row.put("is_clustered", false);
        row.put("implementation", "impl");
        row.put("create_by", "admin");
        row.put("create_time", createTime);
        row.put("last_update_by", "admin2");
        row.put("last_update_time", lastUpdateTime);
        JobDefinition result = mapper.mapRow(row);
        assertNotNull(result);
        assertEquals("Push", result.getJobName());
        assertEquals(JobType.JAVA, result.getJobType());
        assertTrue(result.isRequiresRegistration());
        assertEquals("com.foo.Bar", result.getJobExpression());
        assertEquals("Push job", result.getDescription());
        assertTrue(result.isDefaultAutomaticStartup());
        assertEquals("60000", result.getDefaultSchedule());
        assertEquals("corp", result.getNodeGroupId());
        assertFalse(result.isClustered());
        assertEquals("impl", result.getImplementation());
        assertEquals("admin", result.getCreateBy());
        assertEquals(createTime, result.getCreateTime());
        assertEquals("admin2", result.getLastUpdateBy());
        assertEquals(lastUpdateTime, result.getLastUpdateTime());
    }

    @Test
    void testMapRow_withUnrecognizedJobType_returnsNull() {
        Row row = new Row(2);
        row.put("job_name", "Bogus");
        row.put("job_type", "NOT_A_TYPE");
        assertNull(mapper.mapRow(row));
    }
}
