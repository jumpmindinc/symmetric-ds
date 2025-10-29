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
package org.jumpmind.symmetric.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

/**
 * 
 */
public class NodeChannels {
    private String channelQueue;
    private final ChannelNodesMap suspendMap = new ChannelNodesMap();
    private final ChannelNodesMap ignoreMap = new ChannelNodesMap();

    public NodeChannels() {
    }

    public void addSuspendChannels(ChannelNodesMap suspends) {
        addChannels(suspendMap, suspends);
    }

    public void addIgnoreChannels(ChannelNodesMap ignores) {
        addChannels(ignoreMap, ignores);
    }

    private void addChannels(ChannelNodesMap map, ChannelNodesMap channels) {
        if (channels != null) {
            map.putAll(channels);
        }
    }

    public void addSuspendChannels(String targetNodeId, String suspends) {
        addChannels(suspendMap, targetNodeId, suspends);
    }

    public void addIgnoreChannels(String targetNodeId, String ignores) {
        addChannels(ignoreMap, targetNodeId, ignores);
    }

    private void addChannels(ChannelNodesMap map, String targetNodeId, String channels) {
        if (channels != null) {
            for (String channel : channels.split(",")) {
                map.put(channel, Collections.singleton(targetNodeId));
            }
        }
    }

    public String getSuspendChannelsAsString(String targetNodeId) {
        return getChannelsAsString(suspendMap, targetNodeId);
    }

    public String getIgnoreChannelsAsString(String targetNodeId) {
        return getChannelsAsString(ignoreMap, targetNodeId);
    }

    /**
     * Returns a comma-separated list of channel IDs that are suspended or ignored.
     * 
     * @param map
     *            Either suspendMap or ignoreMap
     * @param targetNodeId
     *            Node ID of the target node that the list of suspended or ignored channel IDs applies to
     */
    private String getChannelsAsString(ChannelNodesMap map, String targetNodeId) {
        Validate.isTrue(Arrays.asList(suspendMap, ignoreMap).contains(map), "Key must be suspendMap or ignoreMap");
        Set<String> channelIdSet = new HashSet<String>();
        for (Entry<String, Set<String>> channelEntry : map.entrySet()) {
            Set<String> targetNodeIdSet = channelEntry.getValue();
            if (targetNodeIdSet.contains(targetNodeId)) {
                String channelId = channelEntry.getKey();
                channelIdSet.add(channelId);
            }
        }
        return StringUtils.join(channelIdSet, ",");
    }

    public ChannelNodesMap getSuspendChannels() {
        return suspendMap;
    }

    public ChannelNodesMap getIgnoreChannels() {
        return ignoreMap;
    }

    public boolean isBatchSuspended(OutgoingBatch batch) {
        return isBatchSuspendedOrIgnored(suspendMap, batch);
    }

    public boolean isBatchIgnored(OutgoingBatch batch) {
        return isBatchSuspendedOrIgnored(ignoreMap, batch);
    }

    private boolean isBatchSuspendedOrIgnored(ChannelNodesMap map, OutgoingBatch batch) {
        Set<String> targetNodeIdSet = map.getByChannelId(batch.getChannelId());
        return targetNodeIdSet != null && targetNodeIdSet.contains(batch.getNodeId());
    }

    public String getChannelQueue() {
        return channelQueue;
    }

    public void setChannelQueue(String threadChannel) {
        this.channelQueue = threadChannel;
    }
}