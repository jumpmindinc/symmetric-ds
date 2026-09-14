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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.ChannelDataCreateTimeRange;
import org.jumpmind.symmetric.model.ChannelDataUnroutedCount;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class RefreshUnroutedDataMetricsJob extends AbstractJob {
    public RefreshUnroutedDataMetricsJob(ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(ClusterConstants.REFRESH_UNROUTED_DATA_METRICS, engine, taskScheduler);
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults()
                .schedule(EVERY_FIFTEEN_MINUTES)
                .description("Refresh unrouted data create time and row count metrics");
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
        List<NodeChannel> nodeChannels = engine.getConfigurationService().getNodeChannels(false);
        refreshUnroutedDataCreateTimeRange(statisticManager, nodeChannels);
        if (engine.getParameterService().is(ParameterConstants.ROUTING_COLLECT_STATS_UNROUTED)) {
            refreshUnroutedDataCounts(statisticManager, nodeChannels);
        }
    }

    private void refreshUnroutedDataCreateTimeRange(IStatisticManager statisticManager, List<NodeChannel> nodeChannels) {
        Map<String, ChannelDataCreateTimeRange> rangesByChannel = engine.getRouterService().findUnroutedDataCreateTimeRangeByChannel()
                .stream().collect(Collectors.toMap(ChannelDataCreateTimeRange::channelId, range -> range));
        Date now = new Date();
        for (NodeChannel channel : nodeChannels) {
            ChannelDataCreateTimeRange range = rangesByChannel.get(channel.getChannelId());
            if (range != null) {
                statisticManager.setDataUnroutedMinCreateTime(range.channelId(), range.minCreateTime());
                statisticManager.setDataUnroutedMaxCreateTime(range.channelId(), range.maxCreateTime());
            } else {
                statisticManager.setDataUnroutedMinCreateTime(channel.getChannelId(), now);
                statisticManager.setDataUnroutedMaxCreateTime(channel.getChannelId(), now);
            }
        }
    }

    private void refreshUnroutedDataCounts(IStatisticManager statisticManager, List<NodeChannel> nodeChannels) {
        Map<String, Long> countsByChannel = engine.getRouterService().findUnroutedDataCountByChannel().stream()
                .collect(Collectors.toMap(ChannelDataUnroutedCount::channelId, ChannelDataUnroutedCount::count));
        for (NodeChannel channel : nodeChannels) {
            statisticManager.setDataUnRouted(channel.getChannelId(), countsByChannel.getOrDefault(channel.getChannelId(), 0L));
        }
    }
}
