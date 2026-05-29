package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
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
}
