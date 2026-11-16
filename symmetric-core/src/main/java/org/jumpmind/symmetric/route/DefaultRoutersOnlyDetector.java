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
package org.jumpmind.symmetric.route;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRoutersOnlyDetector {
    private static final Logger log = LoggerFactory.getLogger(DefaultRoutersOnlyDetector.class);
    private final Map<String, DefaultRoutersCheck> defaultRoutersCheckByChannel = new ConcurrentHashMap<String, DefaultRoutersCheck>();

    public boolean hasOnlyDefaultRouters(Channel channel, String nodeGroupId, List<TriggerRouter> triggerRouters, long cacheTime) {
        String channelId = channel.getChannelId();
        DefaultRoutersCheck lastCheck = defaultRoutersCheckByChannel.get(channelId);
        if (lastCheck != null && lastCheck.isCurrentlyValid(triggerRouters, cacheTime)) {
            return lastCheck.hasOnlyDefaultRouters();
        } else {
            boolean hasOnlyDefaultRouters = computeHasOnlyDefaultRouters(channel, nodeGroupId, triggerRouters);
            if (hasOnlyDefaultRouters && (lastCheck == null || !lastCheck.hasOnlyDefaultRouters())) {
                log.debug("The '{}' channel for the '{}' node group has only default routers assigned to it.  Change data won't be selected during routing",
                        channelId, nodeGroupId);
            }
            defaultRoutersCheckByChannel.put(channelId, new DefaultRoutersCheck(hasOnlyDefaultRouters, triggerRouters, System.currentTimeMillis()));
            return hasOnlyDefaultRouters;
        }
    }

    private boolean computeHasOnlyDefaultRouters(Channel channel, String nodeGroupId, List<TriggerRouter> triggerRouters) {
        String channelId = channel.getChannelId();
        boolean hasOnlyDefaultRouters = initialScanHasOnlyDefaultRouters(channel);
        if (hasOnlyDefaultRouters && triggerRouters != null) {
            for (TriggerRouter triggerRouter : triggerRouters) {
                if (triggerRouter.getTrigger().getChannelId().equals(channelId) &&
                        triggerRouter.getRouter().getNodeGroupLink().getSourceNodeGroupId()
                                .equals(nodeGroupId) && !DefaultDataRouter.ROUTER_TYPE.equals(triggerRouter.getRouter().getRouterType())) {
                    hasOnlyDefaultRouters = false;
                }
            }
        }
        return hasOnlyDefaultRouters;
    }

    private boolean initialScanHasOnlyDefaultRouters(Channel channel) {
        String channelId = channel.getChannelId();
        return !Constants.CHANNEL_CONFIG.equals(channelId) && !Constants.CHANNEL_SYSTEM.equals(channelId)
                && !Constants.CHANNEL_HEARTBEAT.equals(channelId)
                && !Constants.CHANNEL_MONITOR.equals(channelId)
                && !channel.isFileSyncFlag()
                && !channel.isReloadFlag();
    }

    public void clear() {
        defaultRoutersCheckByChannel.clear();
    }

    private record DefaultRoutersCheck(boolean hasOnlyDefaultRouters, List<TriggerRouter> triggerRouters, long checkTime) {
        boolean isCurrentlyValid(List<TriggerRouter> currentTriggerRouters, long cacheTime) {
            return triggerRouters == currentTriggerRouters && System.currentTimeMillis() - checkTime <= cacheTime;
        }
    }
}
