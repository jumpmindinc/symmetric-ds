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

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_5_MINUTES;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.util.LogSummaryAppenderUtils;
import org.jumpmind.util.LogSummaryAppender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/*
 * Background job that is responsible for writing statistics to database tables.
 */
public class StatisticFlushJob extends AbstractJob {
    public StatisticFlushJob(ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(ClusterConstants.STATISTICS, engine, taskScheduler);
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults()
                .schedule(EVERY_5_MINUTES)
                .description("Write statistics out to the database");
    }

    @Override
    public void doJob(boolean force) throws Exception {
        engine.getStatisticManager().flush();
        engine.getPurgeService().purgeStats(force);
        purgeLogSummaryAppender();
    }

    protected void purgeLogSummaryAppender() {
        LogSummaryAppender appender = LogSummaryAppenderUtils.getLogSummaryAppender();
        if (appender != null) {
            appender.purgeOlderThan(System.currentTimeMillis()
                    - engine.getParameterService().getLong(ParameterConstants.PURGE_LOG_SUMMARY_MINUTES, 60)
                            * 60000);
        }
    }
}