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

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_FIFTEEN_MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.TokenConstants;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SqlJobTest {
    private static final String TEST_NODE_ID = "test-node-001";
    private static final String TEST_JOB_NAME = "TestSqlJob";
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IClusterService clusterService;
    private IExtensionService extensionService;
    private ThreadPoolTaskScheduler taskScheduler;
    private JobDefinition jobDefinition;
    private SqlJob sqlJob;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        clusterService = mock(IClusterService.class);
        extensionService = mock(IExtensionService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        jobDefinition = createJobDefinition();
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(parameterService.getExternalId()).thenReturn(TEST_NODE_ID);
        when(parameterService.getInt(anyString())).thenReturn(10000);
        sqlJob = new SqlJob(TEST_JOB_NAME, engine, taskScheduler);
        sqlJob.setJobDefinition(jobDefinition);
    }

    private JobDefinition createJobDefinition() {
        JobDefinition jobDef = new JobDefinition();
        jobDef.setJobName(TEST_JOB_NAME);
        jobDef.setDefaultSchedule("60000");
        jobDef.setRequiresRegistration(false);
        jobDef.setClustered(false);
        jobDef.setNodeGroupId(null);
        return jobDef;
    }

    @Test
    void testGetMinSchedulePeriodMs() {
        assertEquals(Long.parseLong(EVERY_FIFTEEN_MINUTES), sqlJob.getMinSchedulePeriodMs());
    }

    @Test
    void testIsRateLimited() {
        assertTrue(sqlJob.isRateLimited());
    }

    @Test
    void testGetTimeBetweenRunsInMs_belowMinimum() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("10000");
        assertEquals(Long.parseLong(EVERY_FIFTEEN_MINUTES), sqlJob.getTimeBetweenRunsInMs());
    }

    @Test
    void testGetTimeBetweenRunsInMs_aboveMinimum() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("7200000");
        assertEquals(7200000L, sqlJob.getTimeBetweenRunsInMs());
    }

    @Test
    void testGetTimeBetweenRunsInMs_exactlyMinimum() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule(EVERY_FIFTEEN_MINUTES);
        assertEquals(Long.parseLong(EVERY_FIFTEEN_MINUTES), sqlJob.getTimeBetweenRunsInMs());
    }

    @Test
    void getReplacementTokens_withIdentity_includesNodeGroupId() {
        INodeService nodeService = mock(INodeService.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        Node identity = new Node();
        identity.setNodeGroupId("client");
        when(engine.getNodeId()).thenReturn(TEST_NODE_ID);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentity()).thenReturn(identity);
        when(platform.getSqlScriptReplacementTokens()).thenReturn(null);
        Map<String, String> tokens = sqlJob.getReplacementTokens(engine, null);
        assertEquals("client", tokens.get(TokenConstants.NODE_GROUP_ID));
        assertEquals(TEST_NODE_ID, tokens.get(TokenConstants.NODE_ID));
    }

    @Test
    void getReplacementTokens_withNullIdentity_omitsNodeGroupId() {
        INodeService nodeService = mock(INodeService.class);
        when(engine.getNodeId()).thenReturn(TEST_NODE_ID);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentity()).thenReturn(null);
        Map<String, String> tokens = sqlJob.getReplacementTokens(engine, null);
        assertFalse(tokens.containsKey(TokenConstants.NODE_GROUP_ID));
        assertEquals(TEST_NODE_ID, tokens.get(TokenConstants.NODE_ID));
    }
}
