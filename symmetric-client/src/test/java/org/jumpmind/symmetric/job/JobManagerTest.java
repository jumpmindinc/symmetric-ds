package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.model.JobDefinition.JobType;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for JobManager.
 */
class JobManagerTest {
    private JobManager jobManager;
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IClusterService clusterService;
    private ISymmetricDialect dialect;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        clusterService = mock(IClusterService.class);
        dialect = mock(ISymmetricDialect.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        when(dialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(parameterService.getEngineName()).thenReturn("test-engine");
        when(parameterService.getNodeGroupId()).thenReturn("test-group");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        jobManager = new JobManager(engine);
    }

    @Test
    void testGetJobCreator() {
        JobCreator creator = jobManager.getJobCreator();
        assertNotNull(creator, "JobCreator should not be null");
    }

    @Test
    void testSaveJob_withExistingJob() {
        JobDefinition job = new JobDefinition();
        job.setJobName("ExistingJob");
        job.setJobType(JobType.BSH);
        job.setJobExpression("println('updated')");
        job.setDefaultSchedule("600000");
        job.setDescription("Updated description");
        job.setDefaultAutomaticStartup(true);
        job.setNodeGroupId("test-group");
        job.setClustered(true);
        job.setCreateBy("user1");
        job.setLastUpdateBy("user2");
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        jobManager.saveJob(job);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(sqlTemplate, times(1)).update(sqlCaptor.capture(), argsCaptor.capture());
        verify(clusterService, times(1)).addLock(eq("ExistingJob"), anyString());
    }

    @Test
    void testSaveJob_withNewJob() {
        JobDefinition job = new JobDefinition();
        job.setJobName("NewJob");
        job.setJobType(JobType.SQL);
        job.setJobExpression("SELECT * FROM test");
        job.setDefaultSchedule("300000");
        job.setDescription("New job");
        job.setDefaultAutomaticStartup(false);
        job.setNodeGroupId("test-group");
        job.setClustered(false);
        job.setCreateBy("user1");
        job.setLastUpdateBy("user1");
        when(sqlTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(0) // update returns 0
                .thenReturn(1); // insert returns 1
        jobManager.saveJob(job);
        verify(sqlTemplate, times(2)).update(anyString(), any(Object[].class));
        verify(clusterService, times(1)).addLock(eq("NewJob"), anyString());
    }

    @Test
    void testSaveJob_withBshJob() {
        JobDefinition job = new JobDefinition();
        job.setJobName("BshJob");
        job.setJobType(JobType.BSH);
        job.setJobExpression("println('test')");
        job.setDefaultSchedule("300000");
        job.setClustered(false);
        job.setCreateBy("user1");
        job.setLastUpdateBy("user1");
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        jobManager.saveJob(job);
        verify(clusterService, times(1)).addLock(eq("BshJob"), anyString());
    }

    @Test
    void testSaveJob_withJavaJob() {
        JobDefinition job = new JobDefinition();
        job.setJobName("JavaJob");
        job.setJobType(JobType.JAVA);
        job.setJobExpression("org.jumpmind.symmetric.job.PushJob");
        job.setDefaultSchedule("300000");
        job.setClustered(false);
        job.setCreateBy("user1");
        job.setLastUpdateBy("user1");
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        jobManager.saveJob(job);
        verify(clusterService, times(1)).addLock(eq("JavaJob"), anyString());
    }

    @Test
    void testSaveJob_withBuiltInJob() {
        JobDefinition job = new JobDefinition();
        job.setJobName("BuiltInJob");
        job.setJobType(JobType.BUILT_IN);
        job.setJobExpression("org.jumpmind.symmetric.job.PushJob");
        job.setDefaultSchedule("300000");
        job.setClustered(false);
        job.setCreateBy("user1");
        job.setLastUpdateBy("user1");
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        jobManager.saveJob(job);
        verify(clusterService, times(0)).addLock(anyString(), anyString());
    }

    @Test
    void testSaveJob_withDataRefreshJob_addsClusterLock() {
        JobDefinition job = new JobDefinition();
        job.setJobName(ClusterConstants.DATA_REFRESH_DAILY_MIDNIGHT);
        job.setJobType(JobType.JAVA);
        job.setImplementation("com.jumpmind.symmetric.job.DataRefreshJob");
        job.setDefaultSchedule("0 0 0 * * *");
        job.setClustered(false);
        job.setCreateBy("SymmetricDS");
        job.setLastUpdateBy("SymmetricDS");
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        jobManager.saveJob(job);
        verify(clusterService, times(1)).addLock(eq(ClusterConstants.DATA_REFRESH_DAILY_MIDNIGHT), anyString());
    }

    @Test
    void testIsAutoStartConfigured_trueViaDeprecatedParameter() {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setJobName(ClusterConstants.ROUTE);
        when(job.getDeprecatedStartParameter()).thenReturn("start.route.job.38");
        when(job.getJobDefinition()).thenReturn(def);
        when(parameterService.getString("start.route.job.38")).thenReturn("true");
        assertTrue(jobManager.isAutoStartConfigured(job));
    }

    @Test
    void testIsAutoStartConfigured_trueViaNamedStartParameter() {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setJobName(ClusterConstants.PUSH);
        when(job.getDeprecatedStartParameter()).thenReturn(null);
        when(job.getJobDefinition()).thenReturn(def);
        when(parameterService.getString(ParameterConstants.START_PUSH_JOB)).thenReturn("1");
        assertTrue(jobManager.isAutoStartConfigured(job));
    }

    @Test
    void testIsAutoStartConfigured_trueViaDefaultAutomaticStartup() {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setJobName(ClusterConstants.HEARTBEAT);
        def.setDefaultAutomaticStartup(true);
        when(job.getDeprecatedStartParameter()).thenReturn(null);
        when(job.getJobDefinition()).thenReturn(def);
        assertTrue(jobManager.isAutoStartConfigured(job));
    }

    @Test
    void testIsAutoStartConfigured_falseViaDefaultAutomaticStartup() {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setJobName(ClusterConstants.DATA_REFRESH_DAILY_MIDNIGHT);
        def.setDefaultAutomaticStartup(false);
        when(job.getDeprecatedStartParameter()).thenReturn(null);
        when(job.getJobDefinition()).thenReturn(def);
        assertFalse(jobManager.isAutoStartConfigured(job));
    }

    @Test
    void testGetCustomJobDefinitions_filtersNullEntries() {
        JobDefinition validJob = new JobDefinition();
        validJob.setJobName("ValidJob");
        List<JobDefinition> rows = Arrays.asList(validJob, null);
        when(sqlTemplate.query(anyString(), any(JobMapper.class))).thenReturn(rows);
        List<JobDefinition> result = jobManager.getCustomJobDefinitions();
        assertEquals(1, result.size());
        assertEquals("ValidJob", result.get(0).getJobName());
    }

    @Test
    void testIsStarted_initiallyFalse() {
        assertFalse(jobManager.isStarted());
    }

    @Test
    void testGetJob_returnsMatchingJobIgnoringCase() throws Exception {
        IJob job = mock(IJob.class);
        when(job.getName()).thenReturn("PushJob");
        setJobs(Arrays.asList(job));
        assertEquals(job, jobManager.getJob("pushjob"));
    }

    @Test
    void testGetJob_returnsNullWhenNotFound() throws Exception {
        setJobs(Collections.emptyList());
        assertNull(jobManager.getJob("missing"));
    }

    @Test
    void testIsJobApplicableToNodeGroup_emptyNodeGroupId_returnsTrue() {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setNodeGroupId("");
        when(job.getJobDefinition()).thenReturn(def);
        assertTrue(jobManager.isJobApplicableToNodeGroup(job));
    }

    @Test
    void testIsJobApplicableToNodeGroup_allNodeGroupId_returnsTrue() {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setNodeGroupId("ALL");
        when(job.getJobDefinition()).thenReturn(def);
        assertTrue(jobManager.isJobApplicableToNodeGroup(job));
    }

    @Test
    void testIsJobApplicableToNodeGroup_matchingNodeGroupId_returnsTrue() {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setNodeGroupId("test-group");
        when(job.getJobDefinition()).thenReturn(def);
        assertTrue(jobManager.isJobApplicableToNodeGroup(job));
    }

    @Test
    void testIsJobApplicableToNodeGroup_nonMatchingNodeGroupId_returnsFalse() {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setNodeGroupId("other-group");
        when(job.getJobDefinition()).thenReturn(def);
        assertFalse(jobManager.isJobApplicableToNodeGroup(job));
    }

    @Test
    void testStartJobs_startsAutoStartApplicableJobs() throws Exception {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setJobName(ClusterConstants.PUSH);
        def.setNodeGroupId("test-group");
        def.setDefaultAutomaticStartup(true);
        when(job.getJobDefinition()).thenReturn(def);
        when(job.getDeprecatedStartParameter()).thenReturn(null);
        setJobs(Arrays.asList(job));
        jobManager.startJobs();
        verify(job, times(1)).start();
        assertTrue(jobManager.isStarted());
    }

    @Test
    void testStartJobs_skipsJobsNotConfiguredForAutoStart() throws Exception {
        IJob job = mock(IJob.class);
        JobDefinition def = new JobDefinition();
        def.setJobName(ClusterConstants.PUSH);
        def.setNodeGroupId("test-group");
        def.setDefaultAutomaticStartup(false);
        when(job.getJobDefinition()).thenReturn(def);
        when(job.getDeprecatedStartParameter()).thenReturn(null);
        setJobs(Arrays.asList(job));
        jobManager.startJobs();
        verify(job, never()).start();
        assertTrue(jobManager.isStarted());
    }

    @Test
    void testStopJobs_stopsAllJobsAndSetsStartedFalse() throws Exception {
        IJob job1 = mock(IJob.class);
        IJob job2 = mock(IJob.class);
        setJobs(Arrays.asList(job1, job2));
        jobManager.stopJobs();
        verify(job1, times(1)).stop();
        verify(job2, times(1)).stop();
        assertFalse(jobManager.isStarted());
    }

    @Test
    void testStopJobs_withNullJobs_doesNotThrow() {
        jobManager.stopJobs();
        assertFalse(jobManager.isStarted());
    }

    @Test
    void testDestroy_stopsJobsAndShutsDownScheduler() throws Exception {
        IJob job = mock(IJob.class);
        setJobs(Arrays.asList(job));
        jobManager.destroy();
        verify(job, times(1)).stop();
        assertFalse(jobManager.isStarted());
    }

    @Test
    void testGetJobs_withNullJobs_returnsEmptyList() {
        assertTrue(jobManager.getJobs().isEmpty());
    }

    @Test
    void testGetJobs_sortsStartedJobsFirstThenByJobTypeDescending() throws Exception {
        IJob jobA = mock(IJob.class);
        JobDefinition defA = new JobDefinition();
        defA.setJobType(JobType.BSH);
        when(jobA.isStarted()).thenReturn(false);
        when(jobA.getJobDefinition()).thenReturn(defA);
        IJob jobB = mock(IJob.class);
        JobDefinition defB = new JobDefinition();
        defB.setJobType(JobType.SQL);
        when(jobB.isStarted()).thenReturn(true);
        when(jobB.getJobDefinition()).thenReturn(defB);
        IJob jobC = mock(IJob.class);
        JobDefinition defC = new JobDefinition();
        defC.setJobType(JobType.JAVA);
        when(jobC.isStarted()).thenReturn(false);
        when(jobC.getJobDefinition()).thenReturn(defC);
        setJobs(Arrays.asList(jobA, jobB, jobC));
        List<IJob> sorted = jobManager.getJobs();
        assertEquals(Arrays.asList(jobB, jobC, jobA), sorted);
    }

    @Test
    void testRestartJob_notFound_doesNothing() throws Exception {
        setJobs(Collections.emptyList());
        jobManager.restartJob("missing");
        assertNull(jobManager.getJob("missing"));
    }

    @Test
    void testRestartJob_existingJob_stopsAndRestartsWhenApplicable() throws Exception {
        IJob job = mock(IJob.class);
        when(job.getName()).thenReturn("PushJob");
        JobDefinition def = new JobDefinition();
        def.setJobName(ClusterConstants.PUSH);
        def.setNodeGroupId("test-group");
        def.setDefaultAutomaticStartup(true);
        when(job.getJobDefinition()).thenReturn(def);
        when(job.getDeprecatedStartParameter()).thenReturn(null);
        setJobs(Arrays.asList(job));
        jobManager.restartJob("PushJob");
        verify(job, times(1)).stop();
        verify(job, times(1)).start();
    }

    @Test
    void testRestartJob_withAbstractJobInstance_appliesDefaultsBeforeRestart() throws Exception {
        AbstractJob job = mock(AbstractJob.class);
        when(job.getName()).thenReturn("BshJob");
        JobDefinition def = new JobDefinition();
        def.setJobName("BshJob");
        def.setNodeGroupId("test-group");
        when(job.getJobDefinition()).thenReturn(def);
        when(job.getDeprecatedStartParameter()).thenReturn(null);
        when(job.getDefaults()).thenReturn(new JobDefaults().enabled(true));
        setJobs(Arrays.asList((IJob) job));
        jobManager.restartJob("BshJob");
        assertTrue(def.isDefaultAutomaticStartup());
        verify(job, times(1)).stop();
        verify(job, times(1)).start();
    }

    @Test
    void testSaveJobAsCopy_appendsSuffixWhenNameExists() throws Exception {
        IJob existingJob = mock(IJob.class);
        when(existingJob.getName()).thenReturn("MyJob");
        setJobs(Arrays.asList(existingJob));
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        JobDefinition job = new JobDefinition();
        job.setJobName("MyJob");
        job.setJobType(JobType.SQL);
        job.setJobExpression("SELECT 1");
        job.setDefaultSchedule("1000");
        job.setNodeGroupId("test-group");
        jobManager.saveJobAsCopy(job);
        assertEquals("MyJob_2", job.getJobName());
    }

    @Test
    void testSaveJobAsCopy_keepsNameWhenNoConflict() throws Exception {
        setJobs(Collections.emptyList());
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        JobDefinition job = new JobDefinition();
        job.setJobName("UniqueJob");
        job.setJobType(JobType.SQL);
        jobManager.saveJobAsCopy(job);
        assertEquals("UniqueJob", job.getJobName());
    }

    @Test
    void testRenameJob_removesOldJobAndSavesNewDefinition() throws Exception {
        IJob oldJob = mock(IJob.class);
        when(oldJob.getName()).thenReturn("OldJob");
        JobDefinition oldDef = new JobDefinition();
        oldDef.setClustered(false);
        when(oldJob.getJobDefinition()).thenReturn(oldDef);
        setJobs(Arrays.asList(oldJob));
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        JobDefinition newJob = new JobDefinition();
        newJob.setJobName("NewJob");
        newJob.setJobType(JobType.SQL);
        newJob.setNodeGroupId("test-group");
        jobManager.renameJob("OldJob", newJob);
        verify(sqlTemplate, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void testRemoveJob_removesClusterLockWhenJobIsClustered() throws Exception {
        IJob job = mock(IJob.class);
        when(job.getName()).thenReturn("ClusteredJob");
        JobDefinition def = new JobDefinition();
        def.setClustered(true);
        when(job.getJobDefinition()).thenReturn(def);
        setJobs(Arrays.asList(job));
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        jobManager.removeJob("ClusteredJob");
        verify(clusterService, times(1)).removeLock("ClusteredJob");
    }

    @Test
    void testRemoveJob_doesNotRemoveLockWhenJobIsNotClustered() throws Exception {
        IJob job = mock(IJob.class);
        when(job.getName()).thenReturn("SimpleJob");
        JobDefinition def = new JobDefinition();
        def.setClustered(false);
        when(job.getJobDefinition()).thenReturn(def);
        setJobs(Arrays.asList(job));
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        jobManager.removeJob("SimpleJob");
        verify(clusterService, never()).removeLock(anyString());
    }

    @Test
    void testRemoveJob_throwsExceptionWhenDeleteFails() throws Exception {
        setJobs(Collections.emptyList());
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        assertThrows(SymmetricException.class, () -> jobManager.removeJob("NoSuchJob"));
    }

    @Test
    void testRemoveAllJobs_executesDeleteAllJobsSql() {
        jobManager.removeAllJobs();
        verify(sqlTemplate, times(1)).update(anyString());
    }

    private void setJobs(List<IJob> jobs) throws Exception {
        Field field = JobManager.class.getDeclaredField("jobs");
        field.setAccessible(true);
        field.set(jobManager, jobs);
    }
}
