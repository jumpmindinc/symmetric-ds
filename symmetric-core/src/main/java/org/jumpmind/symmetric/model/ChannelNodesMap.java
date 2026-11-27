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

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Channel IDs mapped to the target node IDs for which each channel is suspended or ignored. Adding only ever unions node IDs into a channel, so merging lists
 * from several sources (control rows, remote headers, the local node) keeps every node.
 */
public class ChannelNodesMap {
    private final Map<String, Set<String>> map = new TreeMap<String, Set<String>>();

    public void add(String channelId, String targetNodeId) {
        getOrCreateTargetNodeIds(channelId).add(targetNodeId);
    }

    public void addAll(ChannelNodesMap other) {
        for (Entry<String, Set<String>> entry : other.map.entrySet()) {
            getOrCreateTargetNodeIds(entry.getKey()).addAll(entry.getValue());
        }
    }

    public boolean contains(String channelId, String targetNodeId) {
        Set<String> targetNodeIds = map.get(channelId);
        return targetNodeIds != null && targetNodeIds.contains(targetNodeId);
    }

    public Set<String> getChannelIds(String targetNodeId) {
        Set<String> channelIds = new TreeSet<String>();
        for (Entry<String, Set<String>> entry : map.entrySet()) {
            if (entry.getValue().contains(targetNodeId)) {
                channelIds.add(entry.getKey());
            }
        }
        return channelIds;
    }

    /**
     * @return a read-only view of the target node IDs for the channel, or null when the channel has none
     */
    public Set<String> getByChannelId(String channelId) {
        Set<String> targetNodeIds = map.get(channelId);
        return targetNodeIds == null ? null : Collections.unmodifiableSet(targetNodeIds);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    private Set<String> getOrCreateTargetNodeIds(String channelId) {
        return map.computeIfAbsent(channelId, id -> new TreeSet<String>());
    }
}
