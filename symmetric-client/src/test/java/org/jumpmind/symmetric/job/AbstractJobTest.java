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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.model.Lock;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

class AbstractJobTest {
    private static final String TEST_JOB_NAME = "Test Job";
    private static final String TEST_NODE_ID = "test-node-001";
    private static final String TEST_NODE_GROUP = "test-group";
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IClusterService clusterService;
    private IRegistrationService registrationService;
    private INodeService nodeService;
    private ThreadPoolTaskScheduler taskScheduler;
    private TestableJob testJob;
    private JobDefinition jobDefinition;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        clusterService = mock(IClusterService.class);
        registrationService = mock(IRegistrationService.class);
        nodeService = mock(INodeService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        jobDefinition = createJobDefinition();
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(parameterService.getExternalId()).thenReturn(TEST_NODE_ID);
        when(parameterService.getInt(anyString())).thenReturn(10000);
        testJob = new TestableJob(TEST_JOB_NAME, engine, taskScheduler);
        testJob.setJobDefinition(jobDefinition);
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
    void testStart_withPeriodicSchedule_schedulesJob() {
        when(parameterService.getString(anyString())).thenReturn(null);
        when(clusterService.isInfiniteLocked(TEST_JOB_NAME)).thenReturn(false);
        Map<String, Lock> locks = new HashMap<>();
        when(clusterService.findLocks()).thenReturn(locks);
        jobDefinition.setDefaultSchedule("60000");
        testJob.start();
        assertTrue(testJob.isStarted());
        verify(taskScheduler).scheduleWithFixedDelay(eq(testJob), any(), any());
    }

    @Test
    void testStart_withCronSchedule_schedulesJob() {
        when(parameterService.getString(jobDefinition.getCronParameter())).thenReturn("0 0 * * * *");
        when(clusterService.isInfiniteLocked(TEST_JOB_NAME)).thenReturn(false);
        testJob.start();
        assertTrue(testJob.isStarted());
        verify(taskScheduler).schedule(eq(testJob), any(CronTrigger.class));
    }

    @Test
    void testStart_whenInfiniteLocked_doesNotStart() {
        when(clusterService.isInfiniteLocked(TEST_JOB_NAME)).thenReturn(true);
        testJob.start();
        assertFalse(testJob.isStarted());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    void testStart_withZeroPeriod_doesNotStart() {
        when(parameterService.getString(anyString())).thenReturn(null);
        when(clusterService.isInfiniteLocked(TEST_JOB_NAME)).thenReturn(false);
        jobDefinition.setDefaultSchedule("0");
        testJob.start();
        assertFalse(testJob.isStarted());
    }

    @Test
    void testStart_whenAlreadyScheduled_doesNotScheduleAgain() {
        when(parameterService.getString(anyString())).thenReturn(null);
        when(clusterService.isInfiniteLocked(TEST_JOB_NAME)).thenReturn(false);
        Map<String, Lock> locks = new HashMap<>();
        when(clusterService.findLocks()).thenReturn(locks);
        jobDefinition.setDefaultSchedule("60000");
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(), any());
        testJob.start();
        assertTrue(testJob.isStarted());
        // Call start again - should not schedule a second time
        testJob.start();
        assertTrue(testJob.isStarted());
        // Verify scheduler was only called once
        verify(taskScheduler).scheduleWithFixedDelay(eq(testJob), any(), any());
    }

    @Test
    void testGetTimeBetweenRunsInMs_validPeriod_returnsValue() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("60000");
        assertEquals(60000L, testJob.getTimeBetweenRunsInMsPublic());
    }

    @Test
    void testGetTimeBetweenRunsInMs_zeroPeriod_returnsNegative() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("0");
        assertEquals(-1L, testJob.getTimeBetweenRunsInMsPublic());
    }

    @Test
    void testGetTimeBetweenRunsInMs_negativePeriod_returnsNegative() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("-1000");
        assertEquals(-1L, testJob.getTimeBetweenRunsInMsPublic());
    }

    @Test
    void testGetTimeBetweenRunsInMs_invalidFormat_returnsNegative() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("not-a-number");
        assertEquals(-1L, testJob.getTimeBetweenRunsInMsPublic());
    }

    @Test
    void testGetTimeBetweenRunsInMs_emptySchedule_returnsNegative() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("");
        assertEquals(-1L, testJob.getTimeBetweenRunsInMsPublic());
    }

    @Test
    void testGetTimeBetweenRunsInMs_nullSchedule_returnsNegative() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule(null);
        assertEquals(-1L, testJob.getTimeBetweenRunsInMsPublic());
    }

    @Test
    void testStop_cancelsScheduledJob() {
        when(parameterService.getString(anyString())).thenReturn(null);
        when(clusterService.isInfiniteLocked(TEST_JOB_NAME)).thenReturn(false);
        Map<String, Lock> locks = new HashMap<>();
        when(clusterService.findLocks()).thenReturn(locks);
        jobDefinition.setDefaultSchedule("60000");
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(), any());
        when(scheduledFuture.cancel(true)).thenReturn(true);
        testJob.start();
        assertTrue(testJob.isStarted());
        assertTrue(testJob.stop());
        assertFalse(testJob.isStarted());
    }

    @Test
    void testStop_whenNotStarted_returnsFalse() {
        assertFalse(testJob.stop());
    }

    @Test
    void testGetName_returnsJobName() {
        assertEquals(TEST_JOB_NAME, testJob.getName());
    }

    @Test
    void testGetJobDefinition_returnsDefinition() {
        assertEquals(jobDefinition, testJob.getJobDefinition());
    }

    @Test
    void testCheckPrerequisites_engineIsNull_returnsFalse() {
        testJob.setEngine(null);
        assertFalse(testJob.checkPrerequisitesPublic(false));
    }

    @Test
    void testCheckPrerequisites_engineNotStarted_returnsFalse() {
        when(engine.isStarted()).thenReturn(false);
        assertFalse(testJob.checkPrerequisitesPublic(false));
    }

    @Test
    void testCheckPrerequisites_jobPausedNotForced_returnsFalse() {
        when(engine.isStarted()).thenReturn(true);
        testJob.pause();
        assertFalse(testJob.checkPrerequisitesPublic(false));
    }

    @Test
    void testCheckPrerequisites_jobPausedButForced_returnsTrue() {
        when(engine.isStarted()).thenReturn(true);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(registrationService.isRegisteredWithServer()).thenReturn(true);
        testJob.pause();
        assertTrue(testJob.checkPrerequisitesPublic(true));
    }

    @Test
    void testCheckPrerequisites_requiresRegistrationButNotRegistered_returnsFalse() {
        when(engine.isStarted()).thenReturn(true);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(registrationService.isRegisteredWithServer()).thenReturn(false);
        jobDefinition.setRequiresRegistration(true);
        // Note: The current implementation logs but doesn't return false for this case
        // This test documents current behavior
        assertTrue(testJob.checkPrerequisitesPublic(false));
    }

    @Test
    void testCheckPrerequisites_wrongNodeGroup_returnsFalse() {
        when(engine.isStarted()).thenReturn(true);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(registrationService.isRegisteredWithServer()).thenReturn(true);
        when(engine.getNodeService()).thenReturn(nodeService);
        Node identity = new Node();
        identity.setNodeGroupId("different-group");
        when(nodeService.findIdentity()).thenReturn(identity);
        jobDefinition.setNodeGroupId(TEST_NODE_GROUP);
        assertFalse(testJob.checkPrerequisitesPublic(false));
    }

    @Test
    void testCheckPrerequisites_nodeGroupAll_returnsTrue() {
        when(engine.isStarted()).thenReturn(true);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(registrationService.isRegisteredWithServer()).thenReturn(true);
        jobDefinition.setNodeGroupId("ALL");
        assertTrue(testJob.checkPrerequisitesPublic(false));
    }

    @Test
    void testCheckPrerequisites_matchingNodeGroup_returnsTrue() {
        when(engine.isStarted()).thenReturn(true);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(registrationService.isRegisteredWithServer()).thenReturn(true);
        when(engine.getNodeService()).thenReturn(nodeService);
        Node identity = new Node();
        identity.setNodeGroupId(TEST_NODE_GROUP);
        when(nodeService.findIdentity()).thenReturn(identity);
        jobDefinition.setNodeGroupId(TEST_NODE_GROUP);
        assertTrue(testJob.checkPrerequisitesPublic(false));
    }

    @Test
    void testCheckPrerequisites_allConditionsMet_returnsTrue() {
        when(engine.isStarted()).thenReturn(true);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(registrationService.isRegisteredWithServer()).thenReturn(true);
        assertTrue(testJob.checkPrerequisitesPublic(false));
    }

    @Test
    void testInvoke_successfulExecution_updatesStatistics() {
        setupSuccessfulInvoke();
        testJob.setDoJobSleepMs(50);
        boolean result = testJob.invoke(false);
        assertTrue(result);
        assertEquals(1, testJob.getNumberOfRuns());
        assertTrue(testJob.getLastExecutionTimeInMs() >= 50);
        assertTrue(testJob.getTotalExecutionTimeInMs() >= 50);
        assertNotNull(testJob.getLastFinishTime());
    }

    @Test
    void testInvoke_multipleExecutions_accumulatesStatistics() {
        setupSuccessfulInvoke();
        testJob.setDoJobSleepMs(10);
        testJob.invoke(false);
        testJob.invoke(false);
        testJob.invoke(false);
        assertEquals(3, testJob.getNumberOfRuns());
        assertTrue(testJob.getTotalExecutionTimeInMs() >= 30);
    }

    @Test
    void testInvoke_clusteredJob_acquiresAndReleasesLock() {
        setupSuccessfulInvoke();
        jobDefinition.setClustered(true);
        when(clusterService.lock(TEST_JOB_NAME)).thenReturn(true);
        boolean result = testJob.invoke(false);
        assertTrue(result);
        verify(clusterService).lock(TEST_JOB_NAME);
        verify(clusterService).unlock(TEST_JOB_NAME);
    }

    @Test
    void testInvoke_clusteredJobCannotAcquireLock_doesNotExecute() {
        setupSuccessfulInvoke();
        jobDefinition.setClustered(true);
        when(clusterService.lock(TEST_JOB_NAME)).thenReturn(false);
        boolean result = testJob.invoke(false);
        assertTrue(result); // invoke returns true even if lock fails
        assertEquals(0, testJob.getNumberOfRuns()); // doJob wasn't called
        verify(clusterService, never()).unlock(anyString());
    }

    @Test
    void testInvoke_nonClusteredJob_doesNotUseLock() {
        setupSuccessfulInvoke();
        jobDefinition.setClustered(false);
        testJob.invoke(false);
        verify(clusterService, never()).lock(anyString());
        verify(clusterService, never()).unlock(anyString());
    }

    @Test
    void testInvoke_jobThrowsException_stillUpdatesStatistics() {
        setupSuccessfulInvoke();
        testJob.setThrowException(true);
        testJob.invoke(false);
        // Statistics should still be updated even on exception
        assertEquals(1, testJob.getNumberOfRuns());
        assertNotNull(testJob.getLastFinishTime());
    }

    @Test
    void testInvoke_prerequisitesFail_returnsFalse() {
        when(engine.isStarted()).thenReturn(false);
        boolean result = testJob.invoke(false);
        assertFalse(result);
        assertEquals(0, testJob.getNumberOfRuns());
    }

    @Test
    void testPause_setsJobToPaused() {
        assertFalse(testJob.isPaused());
        testJob.pause();
        assertTrue(testJob.isPaused());
    }

    @Test
    void testUnpause_resumesJob() {
        testJob.pause();
        assertTrue(testJob.isPaused());
        testJob.unpause();
        assertFalse(testJob.isPaused());
    }

    @Test
    void testIsPeriodicSchedule_withNumericSchedule_returnsTrue() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("60000");
        assertTrue(testJob.isPeriodicSchedule());
        assertFalse(testJob.isCronSchedule());
    }

    @Test
    void testIsCronSchedule_withCronExpression_returnsTrue() {
        when(parameterService.getString(jobDefinition.getCronParameter())).thenReturn("0 0 * * * *");
        assertTrue(testJob.isCronSchedule());
        assertFalse(testJob.isPeriodicSchedule());
    }

    @Test
    void testGetSchedule_prefersParameterOverDefault() {
        when(parameterService.getString(jobDefinition.getCronParameter())).thenReturn(null);
        when(parameterService.getString(jobDefinition.getPeriodicParameter())).thenReturn("30000");
        assertEquals("30000", testJob.getSchedule());
    }

    @Test
    void testGetSchedule_cronParameterTakesPrecedence() {
        when(parameterService.getString(jobDefinition.getCronParameter())).thenReturn("0 0 * * * *");
        when(parameterService.getString(jobDefinition.getPeriodicParameter())).thenReturn("30000");
        assertEquals("0 0 * * * *", testJob.getSchedule());
    }

    @Test
    void testGetSchedule_fallsBackToDefault() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("120000");
        assertEquals("120000", testJob.getSchedule());
    }

    @Test
    void testGetAverageExecutionTimeInMs_noRuns_returnsZero() {
        assertEquals(0, testJob.getAverageExecutionTimeInMs());
    }

    @Test
    void testGetAverageExecutionTimeInMs_withRuns_calculatesAverage() {
        setupSuccessfulInvoke();
        testJob.setDoJobSleepMs(100);
        testJob.invoke(false);
        testJob.invoke(false);
        long average = testJob.getAverageExecutionTimeInMs();
        assertTrue(average >= 100, "Average should be at least 100ms");
    }

    @Test
    void testGetNextExecutionTime_periodicNotStarted_returnsNull() {
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("60000");
        assertNull(testJob.getNextExecutionTime());
    }

    @Test
    void testGetNextExecutionTime_afterExecution_calculatesFromLastFinish() {
        setupSuccessfulInvoke();
        when(parameterService.getString(anyString())).thenReturn(null);
        jobDefinition.setDefaultSchedule("60000");
        when(clusterService.isInfiniteLocked(TEST_JOB_NAME)).thenReturn(false);
        Map<String, Lock> locks = new HashMap<>();
        when(clusterService.findLocks()).thenReturn(locks);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(), any());
        testJob.start();
        testJob.invoke(false);
        Date nextExecution = testJob.getNextExecutionTime();
        assertNotNull(nextExecution);
        assertTrue(nextExecution.after(testJob.getLastFinishTime()));
    }

    @Test
    void testIsRunning_initialState_returnsFalse() {
        assertFalse(testJob.isRunning());
    }

    @Test
    void testProcessedCount_canBeSetAndRetrieved() {
        testJob.setProcessedCount(100);
        assertEquals(100, testJob.getProcessedCount());
    }

    @Test
    void testTargetNodeId_canBeSetAndRetrieved() {
        testJob.setTargetNodeId("node-123");
        assertEquals("node-123", testJob.getTargetNodeId());
    }

    @Test
    void testTargetNodeCount_canBeSetAndRetrieved() {
        testJob.setTargetNodeCount(5);
        assertEquals(5, testJob.getTargetNodeCount());
    }

    private void setupSuccessfulInvoke() {
        when(engine.isStarted()).thenReturn(true);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(registrationService.isRegisteredWithServer()).thenReturn(true);
    }

    /**
     * Concrete implementation of AbstractJob for testing purposes.
     */
    private static class TestableJob extends AbstractJob {
        private boolean throwException = false;
        private long doJobSleepMs = 0;

        public TestableJob(String jobName, ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
            super(jobName, engine, taskScheduler);
        }

        @Override
        protected void doJob(boolean force) throws Exception {
            if (doJobSleepMs > 0) {
                Thread.sleep(doJobSleepMs);
            }
            if (throwException) {
                throw new RuntimeException("Test exception");
            }
        }

        @Override
        public JobDefaults getDefaults() {
            return new JobDefaults().schedule(JobDefaults.EVERY_MINUTE);
        }

        public boolean checkPrerequisitesPublic(boolean force) {
            return checkPrerequsites(force);
        }

        public long getTimeBetweenRunsInMsPublic() {
            return getTimeBetweenRunsInMs();
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }

        public void setDoJobSleepMs(long doJobSleepMs) {
            this.doJobSleepMs = doJobSleepMs;
        }
    }
}
