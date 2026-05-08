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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class BuiltInJobsTest {
    private static final String TEST_NODE_ID = "test-node-001";
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IExtensionService extensionService;
    private ThreadPoolTaskScheduler taskScheduler;
    private BuiltInJobs builtInJobs;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        extensionService = mock(IExtensionService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(parameterService.getExternalId()).thenReturn(TEST_NODE_ID);
        when(parameterService.getInt(anyString())).thenReturn(10000);
        when(extensionService.getExtensionPointList(IJob.class)).thenReturn(Collections.emptyList());
        builtInJobs = new BuiltInJobs();
    }

    @Test
    void testCreateJob() {
        IJob job = builtInJobs.createJob(PullJob.class, engine, taskScheduler);
        assertNotNull(job);
        assertInstanceOf(PullJob.class, job);
        assertEquals("Pull", job.getName());
    }

    @Test
    void testGetBuiltInJobs() {
        List<IJob> jobs = builtInJobs.getBuiltInJobs(engine, taskScheduler);
        assertNotNull(jobs);
        assertTrue(jobs.size() > 0);
    }

    @Test
    void testGetBuiltInJobs_setsJobDefinitions() {
        List<IJob> jobs = builtInJobs.getBuiltInJobs(engine, taskScheduler);
        for (IJob job : jobs) {
            JobDefinition def = job.getJobDefinition();
            assertNotNull(def, "Job " + job.getName() + " should have a JobDefinition");
            assertNotNull(def.getJobName(), "JobDefinition should have a name");
            assertEquals(job.getName(), def.getJobName());
        }
    }

    @Test
    void testSyncBuiltInJobs_addsJobDefinitionsToExistingList() {
        List<JobDefinition> existingJobs = new ArrayList<>();
        List<JobDefinition> result = builtInJobs.syncBuiltInJobs(existingJobs, engine, taskScheduler);
        assertNotNull(result);
        assertTrue(result.size() > 0);
    }

    @Test
    void testSyncBuiltInJobs_preservesExistingJobs() {
        List<JobDefinition> existingJobs = new ArrayList<>();
        JobDefinition customJob = new JobDefinition();
        customJob.setJobName("CustomJob");
        existingJobs.add(customJob);
        List<JobDefinition> result = builtInJobs.syncBuiltInJobs(existingJobs, engine, taskScheduler);
        assertTrue(result.contains(customJob), "Should preserve existing custom job");
    }

    @Test
    void testSyncBuiltInJobs_returnsSameListInstance() {
        List<JobDefinition> existingJobs = new ArrayList<>();
        List<JobDefinition> result = builtInJobs.syncBuiltInJobs(existingJobs, engine, taskScheduler);
        assertSame(existingJobs, result, "Should return the same list instance");
    }
}
