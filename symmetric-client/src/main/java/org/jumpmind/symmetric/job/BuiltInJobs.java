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

import java.util.ArrayList;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.model.JobDefinition.JobType;
import org.jumpmind.util.AppUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class BuiltInJobs {
    public List<JobDefinition> syncBuiltInJobs(List<JobDefinition> existingJobs, ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        List<IJob> builtInJobs = getBuiltInJobs(engine, taskScheduler);
        for (IJob job : builtInJobs) {
            existingJobs.add(job.getJobDefinition());
        }
        return existingJobs;
    }

    public List<IJob> getBuiltInJobs(ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        List<IJob> builtInJobs = new ArrayList<IJob>(20);
        builtInJobs.add(new RouterJob(engine, taskScheduler));
        builtInJobs.add(createJob(PushJob.class, engine, taskScheduler));
        builtInJobs.add(createJob(PullJob.class, engine, taskScheduler));
        builtInJobs.add(new OfflinePushJob(engine, taskScheduler));
        builtInJobs.add(new OfflinePullJob(engine, taskScheduler));
        builtInJobs.add(new OutgoingPurgeJob(engine, taskScheduler));
        builtInJobs.add(new IncomingPurgeJob(engine, taskScheduler));
        builtInJobs.add(new StatisticFlushJob(engine, taskScheduler));
        builtInJobs.add(new PurgeMetricStatsJob(engine, taskScheduler));
        builtInJobs.add(createJob(RefreshBacklogReportJob.class, engine, taskScheduler));
        builtInJobs.add(new SyncTriggersJob(engine, taskScheduler));
        builtInJobs.add(new HeartbeatJob(engine, taskScheduler));
        builtInJobs.add(new WatchdogJob(engine, taskScheduler));
        builtInJobs.add(new StageManagementJob(engine, taskScheduler));
        builtInJobs.add(new RefreshCacheJob(engine, taskScheduler));
        builtInJobs.add(new FileSyncTrackerJob(engine, taskScheduler));
        builtInJobs.add(new FileSyncPullJob(engine, taskScheduler));
        builtInJobs.add(new FileSyncPushJob(engine, taskScheduler));
        builtInJobs.add(new InitialLoadExtractorJob(engine, taskScheduler));
        builtInJobs.add(new ReportStatusJob(engine, taskScheduler));
        builtInJobs.add(new LogMinerJob(engine, taskScheduler));
        builtInJobs.add(new InitialLoadJob(engine, taskScheduler));
        List<IJob> extendedJobs = engine.getExtensionService().getExtensionPointList(IJob.class);
        for (IJob extendedJob : extendedJobs) {
            if (extendedJob instanceof AbstractJob) {
                ((AbstractJob) extendedJob).setTaskScheduler(taskScheduler);
            }
            builtInJobs.add(extendedJob);
        }
        for (IJob builtInJob : builtInJobs) {
            setBuiltInDefaults(builtInJob);
        }
        return builtInJobs;
    }

    protected <T extends IJob> T createJob(Class<T> jobClass, ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        return AppUtils.newInstance(jobClass, jobClass,
                new Object[] { engine, taskScheduler },
                new Class<?>[] { ISymmetricEngine.class, ThreadPoolTaskScheduler.class });
    }

    protected void setBuiltInDefaults(IJob argBuiltInJob) {
        AbstractJob builtInJob = (AbstractJob) argBuiltInJob;
        JobDefinition jobDefinition = new JobDefinition();
        jobDefinition.setJobName(builtInJob.getName());
        jobDefinition.setDescription(builtInJob.getDefaults().getDescription());
        jobDefinition.setRequiresRegistration(builtInJob.getDefaults().isRequiresRegisteration());
        jobDefinition.setJobType(JobType.BUILT_IN);
        jobDefinition.setJobExpression(argBuiltInJob.getClass().getName());
        jobDefinition.setNodeGroupId("ALL");
        jobDefinition.setCreateBy("SymmetricDS");
        jobDefinition.setDefaultAutomaticStartup(builtInJob.getDefaults().isEnabled());
        builtInJob.setJobDefinition(jobDefinition);
    }
}
