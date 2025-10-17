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
package org.jumpmind.symmetric.cache;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Grouplet;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.LoadFilter.LoadFilterType;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroupChannelWindow;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.impl.DataLoaderService.ConflictNodeGroupLink;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;

public interface ICacheManager {
    public List<TriggerRouter> getTriggerRouters(boolean refreshCache);

    public Map<String, List<TriggerRouter>> getTriggerRoutersByChannel(String nodeGroupId, boolean refreshCache);

    public Map<String, Map<Integer, TriggerRouter>> getTriggerRoutersByTriggerHist(boolean refreshCache);

    public Map<String, TriggerRouterRoutersCache> getTriggerRoutersByNodeGroupId(boolean refreshCache);

    public boolean isUsingTargetExternalId(boolean refreshCache);

    public Map<String, Trigger> getTriggers(boolean refreshCache);

    public Map<String, Router> getRouters(boolean refreshCache);

    public Map<String, TriggerRouter> getTriggerRoutersById(boolean refreshCache);

    public void flushTriggerRoutersByNodeGroupId();

    public void flushTriggerRoutersByChannel();

    public void flushTriggerRouters();

    public void flushTriggerRoutersByTriggerHist();

    public void flushTriggerRoutersById();

    public void flushTriggers();

    public void flushRouters();

    public void flushAllWithRouters();

    public List<Node> getSourceNodesCache(NodeGroupLinkAction eventAction, Node node);

    public List<Node> getTargetNodesCache(NodeGroupLinkAction eventAction, Node node);

    public Collection<Node> getNodesByGroup(String nodeGroupId);

    public void flushSourceNodesCache();

    public void flushTargetNodesCache();

    public List<NodeChannel> getAllNodeChannels();

    public List<NodeChannel> getNodeChannels(String nodeId);

    public long getNodeChannelCacheTime();

    public Map<String, Channel> getChannels(boolean refreshCache);

    public Collection<String> getQueues(boolean refreshCache);

    public List<NodeGroupLink> getNodeGroupLinks(boolean refreshCache);

    public Map<String, List<NodeGroupChannelWindow>> getNodeGroupChannelWindows();

    public void flushNodeChannels();

    public void flushChannels();

    public void flushNodeGroupLinks();

    public void flushNodeGroupChannelWindows();

    public List<ConflictNodeGroupLink> getConflictSettingsNodeGroupLinks(NodeGroupLink link, boolean refreshCache);

    public void flushConflictSettingsNodeGroupLinks();

    public List<FileTriggerRouter> getFileTriggerRouters(boolean refreshCache);

    public void flushFileTriggerRouters();

    public List<Grouplet> getGrouplets(boolean refreshCache);

    public void flushGrouplets();

    public Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> findLoadFilters(NodeGroupLink nodeGroupLink,
            boolean useCache);

    public void flushLoadFilters();

    public Map<NodeGroupLink, Map<TransformPoint, List<TransformTableNodeGroupLink>>> getTransformCache();

    public void flushTransformCache();

    public Map<String, Collection<String>> getReadyQueues(boolean refreshCache);

    public void flushReadyQueuesCache();
}
