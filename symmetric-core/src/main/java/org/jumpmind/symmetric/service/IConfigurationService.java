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
package org.jumpmind.symmetric.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ChannelMap;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeChannelControl;
import org.jumpmind.symmetric.model.NodeGroup;
import org.jumpmind.symmetric.model.NodeGroupChannelWindow;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;

/**
 * Provides an API to configure data synchronizations.
 */
public interface IConfigurationService {
    public boolean refreshFromDatabase();

    public List<NodeGroup> getNodeGroups();

    public void saveNodeGroup(NodeGroup group);

    public void saveNodeGroupLink(NodeGroupLink link);

    public void renameNodeGroupLink(String oldSourceId, String oldTargetId, NodeGroupLink link);

    public void deleteNodeGroup(String nodeGroupId);

    public void deleteNodeGroupLink(NodeGroupLink link);

    public void deleteAllNodeGroupLinks();

    public List<NodeGroupLink> getNodeGroupLinks(boolean refreshCache);

    public List<NodeGroupLink> getNodeGroupLinksFromDb();

    public List<NodeGroupLink> getNodeGroupLinksFor(String sourceGroupId, boolean refreshCache);

    public NodeGroupLink getNodeGroupLinkFor(String sourceNodeGroupId, String targetNodeGroupId, boolean refreshCache);

    /**
     * Check to see if the channel is currently being used in the system.
     */
    public boolean isChannelInUse(String channelId);

    public void saveChannel(Channel channel, boolean reloadChannels);

    public void saveChannel(NodeChannel channel, boolean reloadChannels);

    public void saveChannelAsCopy(Channel channel, boolean reloadChannels);

    public void renameChannel(String oldId, Channel channel);

    public void saveNodeChannel(NodeChannel channel, boolean reloadChannels);

    public void saveNodeChannelControl(NodeChannel channel, boolean reloadChannels);

    public void saveNodeChannelControl(NodeChannelControl nodeChannelControl);

    public void updateLastExtractTime(NodeChannel channel);

    public void deleteChannel(Channel channel);

    public void deleteAllChannels();

    public void deleteNodeChannelControl(String nodeId, String targetNodeId, String channelId);

    public List<NodeGroupChannelWindow> getNodeGroupChannelWindows(String nodeGroupId, String channelId);

    public Map<String, List<NodeGroupChannelWindow>> getNodeGroupChannelWindowsFromDb();

    public NodeGroupLinkAction getDataEventActionByGroupLinkId(String sourceGroupId, String targetGroupId);

    public List<NodeChannel> getNodeChannels(boolean refreshExtractMillis);

    public List<NodeChannel> getNodeChannels(String nodeId, boolean refreshExtractMillis);

    public List<NodeChannel> getNodeChannelsFromDb(String nodeId);

    public List<NodeChannel> getAllNodeChannelsFromCache(boolean suspendedOnly);

    public NodeChannel getNodeChannel(String channelId, boolean refreshExtractMillis);

    public Channel getChannel(String channelId);

    public List<Channel> getFileSyncChannels();

    public Map<String, Channel> getChannels(boolean refreshCache);

    public Collection<String> getQueues(boolean refreshCache);

    public Map<String, Channel> getChannelsFromDb();

    public NodeChannel getNodeChannel(String channelId, String nodeId, boolean refreshExtractMillis);

    public void clearCache();

    public void initDefaultChannels();

    /**
     * Returns four sets of channel names for suspended, unsuspended, ignored, and unignored channels
     * 
     * @param nodeId
     * @return A Map with four entries, the sets of which will always be defined but may be empty.
     */
    public ChannelMap getSuspendIgnoreChannelLists(String nodeId);

    public ChannelMap getSuspendIgnoreChannelLists();

    /**
     * @return a map of nodes to redirect to that is keyed by a list of external_ids that should be redirected.
     */
    public Map<String, String> getRegistrationRedirectMap();

    public boolean isBulkLoaderEnabled();

    /**
     * Indicates that this node participates in a master to master link
     * 
     * @return
     */
    public boolean isMasterToMaster();

    public boolean containsMasterToMaster();

    public boolean isMasterToMasterOnly();

    public boolean isUseSourceStagingEnabled(String nodeId);
}