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

import org.jumpmind.symmetric.ISymmetricEngine;
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

public class CacheManager implements ICacheManager {
    private ISymmetricEngine engine;
    volatile private Object constructorCreator = new Object();
    volatile private TriggerRouterCache triggerRouterCache;
    volatile private NodeCache nodeCache;
    volatile private ConfigurationCache configurationCache;
    volatile private DataLoaderCache dataLoaderCache;
    volatile private FileSyncCache fileSyncCache;
    volatile private GroupletCache groupletCache;
    volatile private LoadFilterCache loadFilterCache;
    volatile private TransformCache transformCache;
    volatile private OutgoingBatchCache outgoingBatchCache;

    public CacheManager(ISymmetricEngine engine) {
        this.engine = engine;
    }

    private void initializeTriggerRouterCache() {
        if (triggerRouterCache == null) {
            synchronized (constructorCreator) {
                if (triggerRouterCache == null) {
                    triggerRouterCache = new TriggerRouterCache(engine);
                }
            }
        }
    }

    private void initializeNodeCache() {
        if (nodeCache == null) {
            synchronized (constructorCreator) {
                if (nodeCache == null) {
                    nodeCache = new NodeCache(engine);
                }
            }
        }
    }

    private void initializeConfigurationCache() {
        if (configurationCache == null) {
            synchronized (constructorCreator) {
                if (configurationCache == null) {
                    configurationCache = new ConfigurationCache(engine);
                }
            }
        }
    }

    private void initializeDataLoaderCache() {
        if (dataLoaderCache == null) {
            synchronized (constructorCreator) {
                if (dataLoaderCache == null) {
                    dataLoaderCache = new DataLoaderCache(engine);
                }
            }
        }
    }

    private void initializeFileSyncCache() {
        if (fileSyncCache == null) {
            synchronized (constructorCreator) {
                if (fileSyncCache == null) {
                    fileSyncCache = new FileSyncCache(engine);
                }
            }
        }
    }

    private void initializeGroupletCache() {
        if (groupletCache == null) {
            synchronized (constructorCreator) {
                if (groupletCache == null) {
                    groupletCache = new GroupletCache(engine);
                }
            }
        }
    }

    private void initializeLoadFilterCache() {
        if (loadFilterCache == null) {
            synchronized (constructorCreator) {
                if (loadFilterCache == null) {
                    loadFilterCache = new LoadFilterCache(engine);
                }
            }
        }
    }

    private void initializeTransformCache() {
        if (transformCache == null) {
            synchronized (constructorCreator) {
                if (transformCache == null) {
                    transformCache = new TransformCache(engine);
                }
            }
        }
    }

    private void initializeOutgoingBatchCache() {
        if (outgoingBatchCache == null) {
            synchronized (constructorCreator) {
                if (outgoingBatchCache == null) {
                    outgoingBatchCache = new OutgoingBatchCache(engine);
                }
            }
        }
    }

    @Override
    public List<TriggerRouter> getTriggerRouters(boolean refreshCache) {
        initializeTriggerRouterCache();
        return triggerRouterCache.getTriggerRouters(refreshCache);
    }

    @Override
    public Map<String, List<TriggerRouter>> getTriggerRoutersByChannel(String nodeGroupId, boolean refreshCache) {
        initializeTriggerRouterCache();
        return triggerRouterCache.getTriggerRoutersByChannel(nodeGroupId, refreshCache);
    }

    @Override
    public Map<String, Map<Integer, TriggerRouter>> getTriggerRoutersByTriggerHist(boolean refreshCache) {
        initializeTriggerRouterCache();
        return triggerRouterCache.getTriggerRoutersByTriggerHist(refreshCache);
    }

    @Override
    public Map<String, Trigger> getTriggers(boolean refreshCache) {
        initializeTriggerRouterCache();
        return triggerRouterCache.getTriggers(refreshCache);
    }

    @Override
    public Map<String, Router> getRouters(boolean refreshCache) {
        initializeTriggerRouterCache();
        return triggerRouterCache.getRouters(refreshCache);
    }

    @Override
    public Map<String, TriggerRouter> getTriggerRoutersById(boolean refreshCache) {
        initializeTriggerRouterCache();
        return triggerRouterCache.getTriggerRoutersById(refreshCache);
    }

    @Override
    public Map<String, TriggerRouterRoutersCache> getTriggerRoutersByNodeGroupId(boolean refreshCache) {
        initializeTriggerRouterCache();
        return triggerRouterCache.getTriggerRoutersByNodeGroupId(refreshCache);
    }

    @Override
    public boolean isUsingTargetExternalId(boolean refreshCache) {
        initializeTriggerRouterCache();
        return triggerRouterCache.isUsingTargetExternalId(refreshCache);
    }

    @Override
    public void flushTriggerRoutersByNodeGroupId() {
        initializeTriggerRouterCache();
        triggerRouterCache.flushTriggerRoutersByNodeGroupId();
    }

    @Override
    public void flushTriggerRoutersByChannel() {
        initializeTriggerRouterCache();
        triggerRouterCache.flushTriggerRoutersByChannel();
    }

    @Override
    public void flushTriggerRouters() {
        initializeTriggerRouterCache();
        triggerRouterCache.flushTriggerRouters();
    }

    @Override
    public void flushTriggerRoutersByTriggerHist() {
        initializeTriggerRouterCache();
        triggerRouterCache.flushTriggerRoutersByTriggerHist();
    }

    @Override
    public void flushTriggerRoutersById() {
        initializeTriggerRouterCache();
        triggerRouterCache.flushTriggerRoutersById();
    }

    @Override
    public void flushTriggers() {
        initializeTriggerRouterCache();
        triggerRouterCache.flushTriggers();
    }

    @Override
    public void flushRouters() {
        initializeTriggerRouterCache();
        triggerRouterCache.flushRouters();
    }

    @Override
    public void flushAllWithRouters() {
        flushTriggerRoutersByNodeGroupId();
        flushTriggerRoutersByChannel();
        flushTriggerRouters();
        flushTriggerRoutersByTriggerHist();
        flushTriggerRoutersById();
    }

    @Override
    public List<Node> getSourceNodesCache(NodeGroupLinkAction eventAction, Node node) {
        initializeNodeCache();
        return nodeCache.getSourceNodesCache(eventAction, node);
    }

    @Override
    public List<Node> getTargetNodesCache(NodeGroupLinkAction eventAction, Node node) {
        initializeNodeCache();
        return nodeCache.getTargetNodesCache(eventAction, node);
    }

    @Override
    public Collection<Node> getNodesByGroup(String nodeGroupId) {
        initializeNodeCache();
        return nodeCache.getNodesByGroup(nodeGroupId);
    }

    @Override
    public void flushSourceNodesCache() {
        initializeNodeCache();
        nodeCache.flushSourceNodesCache();
    }

    @Override
    public void flushTargetNodesCache() {
        initializeNodeCache();
        nodeCache.flushTargetNodesCache();
    }

    @Override
    public List<NodeChannel> getNodeChannels(String nodeId) {
        initializeConfigurationCache();
        return configurationCache.getNodeChannels(nodeId);
    }

    @Override
    public long getNodeChannelCacheTime() {
        initializeConfigurationCache();
        return configurationCache.getNodeChannelCacheTime();
    }

    @Override
    public Map<String, Channel> getChannels(boolean refreshCache) {
        initializeConfigurationCache();
        return configurationCache.getChannels(refreshCache);
    }

    @Override
    public Collection<String> getQueues(boolean refreshCache) {
        initializeConfigurationCache();
        return configurationCache.getQueues(refreshCache);
    }

    @Override
    public List<NodeGroupLink> getNodeGroupLinks(boolean refreshCache) {
        initializeConfigurationCache();
        return configurationCache.getNodeGroupLinks(refreshCache);
    }

    @Override
    public Map<String, List<NodeGroupChannelWindow>> getNodeGroupChannelWindows() {
        initializeConfigurationCache();
        return configurationCache.getNodeGroupChannelWindows();
    }

    @Override
    public void flushNodeChannels() {
        initializeConfigurationCache();
        configurationCache.flushNodeChannels();
    }

    @Override
    public void flushChannels() {
        initializeConfigurationCache();
        configurationCache.flushChannels();
    }

    @Override
    public void flushNodeGroupLinks() {
        initializeConfigurationCache();
        configurationCache.flushNodeGroupLinks();
    }

    @Override
    public void flushNodeGroupChannelWindows() {
        initializeConfigurationCache();
        configurationCache.flushNodeGroupChannelWindows();
    }

    @Override
    public List<ConflictNodeGroupLink> getConflictSettingsNodeGroupLinks(NodeGroupLink link, boolean refreshCache) {
        initializeDataLoaderCache();
        return dataLoaderCache.getConflictSettingsNodeGroupLinks(link, refreshCache);
    }

    @Override
    public void flushConflictSettingsNodeGroupLinks() {
        initializeDataLoaderCache();
        dataLoaderCache.clearDataLoaderCache();
    }

    @Override
    public List<FileTriggerRouter> getFileTriggerRouters(boolean refreshCache) {
        initializeFileSyncCache();
        return fileSyncCache.getFileTriggerRouters(refreshCache);
    }

    @Override
    public void flushFileTriggerRouters() {
        initializeFileSyncCache();
        fileSyncCache.flushFileTriggerRouters();
    }

    @Override
    public List<Grouplet> getGrouplets(boolean refreshCache) {
        initializeGroupletCache();
        return groupletCache.getGrouplets(refreshCache);
    }

    @Override
    public void flushGrouplets() {
        initializeGroupletCache();
        groupletCache.flushGrouplets();
    }

    @Override
    public Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> findLoadFilters(NodeGroupLink nodeGroupLink,
            boolean useCache) {
        initializeLoadFilterCache();
        return loadFilterCache.findLoadFilters(nodeGroupLink, useCache);
    }

    @Override
    public void flushLoadFilters() {
        initializeLoadFilterCache();
        loadFilterCache.flushLoadFilterCache();
    }

    @Override
    public Map<NodeGroupLink, Map<TransformPoint, List<TransformTableNodeGroupLink>>> getTransformCache() {
        initializeTransformCache();
        return transformCache.getTransformsCacheByNodeGroupLinkByTransformPoint();
    }

    @Override
    public void flushTransformCache() {
        initializeTransformCache();
        transformCache.flushTransformCache();
    }

    @Override
    public Map<String, Collection<String>> getReadyQueues(boolean refreshCache) {
        initializeOutgoingBatchCache();
        return outgoingBatchCache.getReadyQueues(refreshCache);
    }

    @Override
    public void flushReadyQueuesCache() {
        initializeOutgoingBatchCache();
        outgoingBatchCache.flushReadyQueuesCache();
    }
}
