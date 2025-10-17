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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

/**
 * 
 */
public class ChannelMap {
    public static final String CHANNELS_SUSPEND = "Suspended-Channels";
    public static final String CHANNELS_UNSUSPEND = "Unsuspended-Channels";
    public static final String CHANNELS_IGNORE = "Ignored-Channels";
    public static final String CHANNELS_UNIGNORE = "Unignored-Channels";
    private String channelQueue;
    private final Map<String, Map<String, Set<String>>> map = new HashMap<String, Map<String, Set<String>>>();

    public ChannelMap() {
        map.put(CHANNELS_SUSPEND, new TreeMap<String, Set<String>>());
        map.put(CHANNELS_UNSUSPEND, new TreeMap<String, Set<String>>());
        map.put(CHANNELS_IGNORE, new TreeMap<String, Set<String>>());
        map.put(CHANNELS_UNIGNORE, new TreeMap<String, Set<String>>());
    }

    public void addSuspendChannels(Map<String, Set<String>> suspends) {
        addChannels(CHANNELS_SUSPEND, suspends);
    }

    public void addUnsuspendChannels(Map<String, Set<String>> unsuspends) {
        addChannels(CHANNELS_UNSUSPEND, unsuspends);
    }

    public void addIgnoreChannels(Map<String, Set<String>> ignores) {
        addChannels(CHANNELS_IGNORE, ignores);
    }

    public void addUnignoreChannels(Map<String, Set<String>> unignores) {
        addChannels(CHANNELS_UNIGNORE, unignores);
    }

    private void addChannels(String key, Map<String, Set<String>> channels) {
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
            Map<String, Set<String>> channelMap = map.get(key);
            for (String channel : channels.split(",")) {
                channelMap.put(channel, Collections.singleton(targetNodeId));
            }
        }
    }

    public String getSuspendChannelsAsString(String targetNodeId) {
        return getChannelsAsString(CHANNELS_SUSPEND, targetNodeId);
    }

    public String getIgnoreChannelsAsString(String targetNodeId) {
        return getChannelsAsString(CHANNELS_IGNORE, targetNodeId);
    }

    private String getChannelsAsString(String key, String targetNodeId) {
        Set<String> channelIdSet = new HashSet<String>();
        Map<String, Set<String>> includedChannelMap = map.get(key);
        Map<String, Set<String>> excludedChannelMap = map.get(key.equals(CHANNELS_SUSPEND) ? CHANNELS_UNSUSPEND : CHANNELS_UNIGNORE);
        for (Entry<String, Set<String>> channelEntry : includedChannelMap.entrySet()) {
            String channelId = channelEntry.getKey();
            Set<String> includedTargetNodeIdSet = channelEntry.getValue();
            if (includedTargetNodeIdSet.contains(targetNodeId)) {
                channelIdSet.add(channelId);
            } else if (includedTargetNodeIdSet.contains(NodeChannelControl.ALL)) {
                Set<String> excludedTargetNodeIdSet = excludedChannelMap.get(channelId);
                if (excludedTargetNodeIdSet == null || !excludedTargetNodeIdSet.contains(targetNodeId)) {
                    channelIdSet.add(channelId);
                }
            }
        }
        return StringUtils.join(channelIdSet, ",");
    }

    public Map<String, Set<String>> getSuspendChannels() {
        return map.get(CHANNELS_SUSPEND);
    }

    public Map<String, Set<String>> getUnsuspendChannels() {
        return map.get(CHANNELS_UNSUSPEND);
    }

    public Map<String, Set<String>> getIgnoreChannels() {
        return map.get(CHANNELS_IGNORE);
    }

    public Map<String, Set<String>> getUnignoreChannels() {
        return map.get(CHANNELS_UNIGNORE);
    }

    public String getChannelQueue() {
        return channelQueue;
    }

    public void setChannelQueue(String threadChannel) {
        this.channelQueue = threadChannel;
    }
}