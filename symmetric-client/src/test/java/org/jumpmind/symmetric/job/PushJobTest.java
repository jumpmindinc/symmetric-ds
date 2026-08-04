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

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_FIFTEEN_MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class PushJobTest {
    private static final String TEST_NODE_ID = "test-node-001";
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IClusterService clusterService;
    private IRegistrationService registrationService;
    private IExtensionService extensionService;
    private ThreadPoolTaskScheduler taskScheduler;
    private JobDefinition jobDefinition;
    private PushJob pushJob;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        clusterService = mock(IClusterService.class);
        registrationService = mock(IRegistrationService.class);
        extensionService = mock(IExtensionService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        jobDefinition = createJobDefinition();
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.isRuntimeDbHealthy()).thenReturn(true);
        when(parameterService.getExternalId()).thenReturn(TEST_NODE_ID);
        when(parameterService.getInt(anyString())).thenReturn(10000);
        pushJob = new PushJob(engine, taskScheduler);
        pushJob.setJobDefinition(jobDefinition);
    }

    private JobDefinition createJobDefinition() {
        JobDefinition jobDef = new JobDefinition();
        jobDef.setJobName("Push");
        jobDef.setDefaultSchedule("60000");
        jobDef.setRequiresRegistration(false);
        jobDef.setClustered(false);
        jobDef.setNodeGroupId(null);
        return jobDef;
    }

    private void setupSuccessfulInvoke() {
        when(engine.isStarted()).thenReturn(true);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(registrationService.isRegisteredWithServer()).thenReturn(true);
    }

    @Test
    void testGetMinSchedulePeriodMs() {
        assertEquals(Long.parseLong(EVERY_FIFTEEN_MINUTES), pushJob.getMinSchedulePeriodMs());
    }

    @Test
    void testIsRateLimited() {
        assertTrue(pushJob.isRateLimited());
    }

    @Test
    void testGetTimeBetweenRunsInMs_belowMinimum() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("10000"); // 10 seconds - below minimum
        // Should return minimum of 15 minutes (900000ms) instead of configured 10000ms
        assertEquals(Long.parseLong(EVERY_FIFTEEN_MINUTES), pushJob.getTimeBetweenRunsInMs());
    }

    @Test
    void testGetTimeBetweenRunsInMs_aboveMinimum() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("7200000"); // 2 hours - above minimum
        // Should keep the configured 2 hour schedule
        assertEquals(7200000L, pushJob.getTimeBetweenRunsInMs());
    }

    @Test
    void testGetSchedule_belowMinimum_returnsEnforcedMinimum() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("10000"); // 10 seconds - below minimum
        // getSchedule() should return the enforced minimum (15 minutes)
        assertEquals(EVERY_FIFTEEN_MINUTES, pushJob.getSchedule());
    }

    @Test
    void testGetSchedule_aboveMinimum_returnsConfigured() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("7200000"); // 2 hours - above minimum
        // getSchedule() should return the configured 2 hour schedule
        assertEquals("7200000", pushJob.getSchedule());
    }

    @Test
    void testGetSchedule_cronBelowMinimum_returnsEnforcedMinimum() {
        when(parameterService.getString(jobDefinition.getCronParameter())).thenReturn("0/10 * * * * *");
        when(parameterService.getString(jobDefinition.getPeriodicParameter())).thenReturn(null);
        // getSchedule() should return the enforced minimum (15 minutes) instead of the cron schedule
        assertEquals(EVERY_FIFTEEN_MINUTES, pushJob.getSchedule());
    }

    @Test
    void testGetSchedule_cronAboveMinimum_returnsCronSchedule() {
        when(parameterService.getString(jobDefinition.getCronParameter())).thenReturn("0 0 0/2 * * *");
        when(parameterService.getString(jobDefinition.getPeriodicParameter())).thenReturn(null);
        // getSchedule() should return the cron schedule since it's slower than minimum
        assertEquals("0 0 0/2 * * *", pushJob.getSchedule());
    }

    @Test
    void testInvoke_nonClusteredJob_acquiresLock() {
        jobDefinition.setClustered(false);
        setupSuccessfulInvoke();
        when(clusterService.lock("Push")).thenReturn(true);
        boolean result = pushJob.invoke(false);
        assertTrue(result);
        verify(clusterService).lock("Push");
        verify(clusterService).unlock("Push");
    }
}
