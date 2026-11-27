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
package org.jumpmind.symmetric.model;

import org.apache.commons.lang3.StringUtils;

/**
 * The channels suspended and ignored per target node, as merged from control rows, remote headers and the local node.
 */
public class NodeChannels {
    private String channelQueue;
    private final ChannelNodesMap suspendMap = new ChannelNodesMap();
    private final ChannelNodesMap ignoreMap = new ChannelNodesMap();

    public void addSuspendChannels(ChannelNodesMap suspends) {
        addChannels(suspendMap, suspends);
    }

    public void addIgnoreChannels(ChannelNodesMap ignores) {
        addChannels(ignoreMap, ignores);
    }

    public void addSuspendChannels(String targetNodeId, String suspends) {
        addChannels(suspendMap, targetNodeId, suspends);
    }

    public void addIgnoreChannels(String targetNodeId, String ignores) {
        addChannels(ignoreMap, targetNodeId, ignores);
    }

    public void addSuspendChannel(String channelId, String targetNodeId) {
        suspendMap.add(channelId, targetNodeId);
    }

    public void addIgnoreChannel(String channelId, String targetNodeId) {
        ignoreMap.add(channelId, targetNodeId);
    }

    private static void addChannels(ChannelNodesMap map, ChannelNodesMap channels) {
        if (channels != null) {
            map.addAll(channels);
        }
    }

    private static void addChannels(ChannelNodesMap map, String targetNodeId, String commaSeparatedChannelIds) {
        for (String channelId : StringUtils.split(StringUtils.defaultString(commaSeparatedChannelIds), ',')) {
            map.add(channelId, targetNodeId);
        }
    }

    public String getSuspendChannelsAsString(String targetNodeId) {
        return StringUtils.join(suspendMap.getChannelIds(targetNodeId), ",");
    }

    public String getIgnoreChannelsAsString(String targetNodeId) {
        return StringUtils.join(ignoreMap.getChannelIds(targetNodeId), ",");
    }

    public ChannelNodesMap getSuspendChannels() {
        return suspendMap;
    }

    public ChannelNodesMap getIgnoreChannels() {
        return ignoreMap;
    }

    public boolean isBatchSuspended(OutgoingBatch batch) {
        return suspendMap.contains(batch.getChannelId(), batch.getNodeId());
    }

    public boolean isBatchIgnored(OutgoingBatch batch) {
        return ignoreMap.contains(batch.getChannelId(), batch.getNodeId());
    }

    public String getChannelQueue() {
        return channelQueue;
    }

    public void setChannelQueue(String threadChannel) {
        this.channelQueue = threadChannel;
    }
}
