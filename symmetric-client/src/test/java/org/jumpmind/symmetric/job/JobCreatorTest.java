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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.model.JobDefinition.JobType;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class JobCreatorTest {
    private JobCreator creator;
    private ISymmetricEngine engine;
    private ThreadPoolTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        creator = new JobCreator();
        engine = mock(ISymmetricEngine.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(0);
    }

    @Test
    void testCreateJob_bshType() {
        JobDefinition def = jobDefinition("BshJob", JobType.BSH);
        IJob job = creator.createJob(def, engine, taskScheduler);
        assertNotNull(job);
        assertInstanceOf(BshJob.class, job);
    }

    @Test
    void testCreateJob_sqlType() {
        JobDefinition def = jobDefinition("SqlJob", JobType.SQL);
        IJob job = creator.createJob(def, engine, taskScheduler);
        assertNotNull(job);
        assertInstanceOf(SqlJob.class, job);
    }

    @Test
    void testCreateJob_javaType() {
        JobDefinition def = jobDefinition("JavaJob", JobType.JAVA);
        IJob job = creator.createJob(def, engine, taskScheduler);
        assertNotNull(job);
        assertInstanceOf(JavaJob.class, job);
    }

    @Test
    void testCreateJob_unknownType_throwsSymmetricException() {
        JobDefinition def = new JobDefinition();
        def.setJobName("UnknownJob");
        assertThrows(SymmetricException.class, () -> creator.createJob(def, engine, taskScheduler));
    }

    @Test
    void testCreateJob_builtInType_instantiatesViaReflection() {
        JobDefinition def = jobDefinition(ClusterConstants.HEARTBEAT, JobType.BUILT_IN);
        def.setJobExpression(HeartbeatJob.class.getName());
        IJob job = creator.createJob(def, engine, taskScheduler);
        assertNotNull(job);
        assertInstanceOf(HeartbeatJob.class, job);
    }

    private JobDefinition jobDefinition(String name, JobType type) {
        JobDefinition def = new JobDefinition();
        def.setJobName(name);
        def.setJobType(type);
        return def;
    }
}
