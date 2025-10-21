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
public class ChannelMapWrapper {
    public static final String CHANNELS_SUSPEND = "Suspended-Channels";
    public static final String CHANNELS_UNSUSPEND = "Unsuspended-Channels";
    public static final String CHANNELS_IGNORE = "Ignored-Channels";
    public static final String CHANNELS_UNIGNORE = "Unignored-Channels";
    private String channelQueue;
    private final ChannelMap map = new ChannelMap();

    public ChannelMapWrapper() {
        map.put(CHANNELS_SUSPEND, new TargetNodeMap());
        map.put(CHANNELS_UNSUSPEND, new TargetNodeMap());
        map.put(CHANNELS_IGNORE, new TargetNodeMap());
        map.put(CHANNELS_UNIGNORE, new TargetNodeMap());
    }

    public void addSuspendChannels(TargetNodeMap suspends) {
        addChannels(CHANNELS_SUSPEND, suspends);
    }

    public void addUnsuspendChannels(TargetNodeMap unsuspends) {
        addChannels(CHANNELS_UNSUSPEND, unsuspends);
    }

    public void addIgnoreChannels(TargetNodeMap ignores) {
        addChannels(CHANNELS_IGNORE, ignores);
    }

    public void addUnignoreChannels(TargetNodeMap unignores) {
        addChannels(CHANNELS_UNIGNORE, unignores);
    }

    private void addChannels(String key, TargetNodeMap channels) {
        if (channels != null) {
            map.get(key).putAll(channels);
        }
    }

    public void addSuspendChannels(String targetNodeId, String suspends) {
        addChannels(CHANNELS_SUSPEND, targetNodeId, suspends);
    }

    public void addIgnoreChannels(String targetNodeId, String ignores) {
        addChannels(CHANNELS_IGNORE, targetNodeId, ignores);
    }

    private void addChannels(String key, String targetNodeId, String channels) {
        if (channels != null) {
            TargetNodeMap targetNodeMap = map.get(key);
            for (String channel : channels.split(",")) {
                targetNodeMap.put(channel, Collections.singleton(targetNodeId));
            }
        }
    }

    public String getSuspendChannelsAsString(String targetNodeId) {
        return getChannelsAsString(CHANNELS_SUSPEND, targetNodeId);
    }

    public String getIgnoreChannelsAsString(String targetNodeId) {
        return getChannelsAsString(CHANNELS_IGNORE, targetNodeId);
    }

    /**
     * Returns a comma-separated list of channel IDs that are suspended or ignored.
     * 
     * @param key
     *            Either {@value #CHANNELS_SUSPEND} or {@value #CHANNELS_IGNORE}
     * @param targetNodeId
     *            Node ID of the target node that the list of suspended or ignored channel IDs applies to
     */
    private String getChannelsAsString(String key, String targetNodeId) {
        Validate.isTrue(Arrays.asList(CHANNELS_SUSPEND, CHANNELS_IGNORE).contains(key),
                "Key must be " + CHANNELS_SUSPEND + " or " + CHANNELS_IGNORE);
        Set<String> channelIdSet = new HashSet<String>();
        TargetNodeMap includedTargetNodeMap = map.get(key);
        TargetNodeMap excludedTargetNodeMap = map.get(key.equals(CHANNELS_SUSPEND) ? CHANNELS_UNSUSPEND : CHANNELS_UNIGNORE);
        for (Entry<String, Set<String>> channelEntry : includedTargetNodeMap.entrySet()) {
            String channelId = channelEntry.getKey();
            Set<String> includedTargetNodeIdSet = channelEntry.getValue();
            if (includedTargetNodeIdSet.contains(targetNodeId)) {
                channelIdSet.add(channelId);
            } else if (includedTargetNodeIdSet.contains(NodeChannelControl.ALL)) {
                Set<String> excludedTargetNodeIdSet = excludedTargetNodeMap.get(channelId);
                if (excludedTargetNodeIdSet == null || !excludedTargetNodeIdSet.contains(targetNodeId)) {
                    channelIdSet.add(channelId);
                }
            }
        }
        return StringUtils.join(channelIdSet, ",");
    }

    public TargetNodeMap getSuspendChannels() {
        return map.get(CHANNELS_SUSPEND);
    }

    public TargetNodeMap getUnsuspendChannels() {
        return map.get(CHANNELS_UNSUSPEND);
    }

    public TargetNodeMap getIgnoreChannels() {
        return map.get(CHANNELS_IGNORE);
    }

    public TargetNodeMap getUnignoreChannels() {
        return map.get(CHANNELS_UNIGNORE);
    }

    public String getChannelQueue() {
        return channelQueue;
    }

    public void setChannelQueue(String threadChannel) {
        this.channelQueue = threadChannel;
    }
}