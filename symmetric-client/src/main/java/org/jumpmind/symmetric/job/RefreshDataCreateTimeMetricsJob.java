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

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.ChannelDataCreateTimeRange;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class RefreshDataCreateTimeMetricsJob extends AbstractJob {
    public RefreshDataCreateTimeMetricsJob(ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(ClusterConstants.REFRESH_DATA_CREATE_TIME_METRICS, engine, taskScheduler);
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults()
                .schedule(EVERY_FIFTEEN_MINUTES)
                .description("Refresh unrouted data create time metrics");
    }

    @Override
    protected long getMinSchedulePeriodMs() {
        return Long.parseLong(EVERY_FIFTEEN_MINUTES);
    }

    @Override
    public boolean isRateLimited() {
        return true;
    }

    @Override
    public void doJob(boolean force) throws Exception {
        IStatisticManager statisticManager = engine.getStatisticManager();
        for (ChannelDataCreateTimeRange range : engine.getRouterService().findUnroutedDataCreateTimeRangeByChannel()) {
            statisticManager.setDataUnroutedMinCreateTime(range.channelId(), range.minCreateTime());
            statisticManager.setDataUnroutedMaxCreateTime(range.channelId(), range.maxCreateTime());
        }
    }
}
