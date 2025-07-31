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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroupChannelWindow;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationCache {
    private final Logger log = LoggerFactory.getLogger(ConfigurationCache.class);
    private IParameterService parameterService;
    private IConfigurationService configurationService;
    private Semaphore configurationCacheLock = new Semaphore(1);
    volatile private Map<String, List<NodeChannel>> nodeChannelCache;
    volatile private Map<String, Channel> channelsCache;
    volatile private Collection<String> queuesCache;
    volatile private List<NodeGroupLink> nodeGroupLinksCache;
    volatile private Map<String, List<NodeGroupChannelWindow>> channelWindowsByChannelCache;
    volatile private long channelCacheTime;
    volatile private long nodeChannelCacheTime;
    volatile private long nodeGroupLinkCacheTime;
    volatile private long channelWindowsByChannelCacheTime;

    public ConfigurationCache(ISymmetricEngine engine) {
        this.parameterService = engine.getParameterService();
        this.configurationService = engine.getConfigurationService();
    }

    public List<NodeChannel> getNodeChannels(String nodeId) {
        if (nodeId == null) {
            return new ArrayList<NodeChannel>(0);
        }
        checkNodeChannelCache(nodeId);
        if (nodeChannelCache != null) {
            return nodeChannelCache.getOrDefault(nodeId, new ArrayList<NodeChannel>(0));
        }
        return new ArrayList<NodeChannel>(0);
    }

    protected void checkNodeChannelCache(String nodeId) {
        long channelCacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS);
        List<NodeChannel> nodeChannels = nodeChannelCache != null ? nodeChannelCache.get(nodeId) : null;
        if (System.currentTimeMillis() - nodeChannelCacheTime >= channelCacheTimeoutInMs || nodeChannelCache == null || nodeChannels == null) {
            if (nodeChannelCache == null || nodeChannels == null) {
                try {
                    configurationCacheLock.acquire();
                    try {
                        boolean refreshCache = System.currentTimeMillis() - nodeChannelCacheTime >= channelCacheTimeoutInMs
                                || nodeChannelCache == null;
                        nodeChannels = nodeChannelCache != null ? nodeChannelCache.get(nodeId) : null;
                        if (refreshCache || nodeChannels == null) {
                            populateNodeChannelCache(nodeId, refreshCache, channelCacheTimeoutInMs);
                        }
                    } finally {
                        configurationCacheLock.release();
                    }
                } catch (InterruptedException e) {
                    log.error("Failed to retrieve node channels", e);
                }
            } else if (configurationCacheLock.tryAcquire()) {
                try {
                    populateNodeChannelCache(nodeId, true, channelCacheTimeoutInMs);
                } finally {
                    configurationCacheLock.release();
                }
            }
        }
    }

    protected void populateNodeChannelCache(String nodeId, boolean refreshCache, long channelCacheTimeoutInMs) {
        long ts = System.currentTimeMillis();
        List<NodeChannel> nodeChannels = configurationService.getNodeChannelsFromDb(nodeId);
        if (refreshCache) {
            nodeChannelCache = new HashMap<String, List<NodeChannel>>();
            nodeChannelCacheTime = System.currentTimeMillis();
        }
        nodeChannelCache.put(nodeId, nodeChannels);
        long queryTime = System.currentTimeMillis() - ts;
        if (queryTime > channelCacheTimeoutInMs) {
            log.warn("Query time of {} ms exceeded cache time of {} ms for node channels. "
                    + " This means the query may run on the database constantly.", queryTime, channelCacheTimeoutInMs);
        }
    }

    public long getNodeChannelCacheTime() {
        return nodeChannelCacheTime;
    }

    public Map<String, Channel> getChannels(boolean refreshCache) {
        checkChannelCache(refreshCache);
        if (channelsCache != null) {
            return channelsCache;
        }
        return new HashMap<String, Channel>(0);
    }

    public Collection<String> getQueues(boolean refreshCache) {
        checkChannelCache(refreshCache);
        if (queuesCache != null) {
            return queuesCache;
        }
        return new ArrayList<String>(0);
    }

    protected void checkChannelCache(boolean refreshCache) {
        long channelCacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS, 60000);
        if (System.currentTimeMillis() - channelCacheTime >= channelCacheTimeoutInMs || channelsCache == null || refreshCache) {
            if (channelsCache == null) {
                try {
                    configurationCacheLock.acquire();
                    try {
                        if (System.currentTimeMillis() - channelCacheTime >= channelCacheTimeoutInMs || channelsCache == null || refreshCache) {
                            populateChannelCache(channelCacheTimeoutInMs);
                        }
                    } finally {
                        configurationCacheLock.release();
                    }
                } catch (InterruptedException e) {
                    log.error("Failed to retrieve channels", e);
                }
            } else if (configurationCacheLock.tryAcquire()) {
                try {
                    populateChannelCache(channelCacheTimeoutInMs);
                } finally {
                    configurationCacheLock.release();
                }
            }
        }
    }

    protected void populateChannelCache(long channelCacheTimeoutInMs) {
        long ts = System.currentTimeMillis();
        channelsCache = configurationService.getChannelsFromDb();
        Collection<String> queues = new HashSet<String>();
        for (Channel channel : channelsCache.values()) {
            queues.add(channel.getQueue());
        }
        queuesCache = queues;
        channelCacheTime = System.currentTimeMillis();
        long queryTime = channelCacheTime - ts;
        if (queryTime > channelCacheTimeoutInMs) {
            log.warn("Query time of {} ms exceeded cache time of {} ms for channels. "
                    + " This means the query may run on the database constantly.", queryTime, channelCacheTimeoutInMs);
        }
    }

    public List<NodeGroupLink> getNodeGroupLinks(boolean refreshCache) {
        checkNodeGroupLinkCache(refreshCache);
        if (nodeGroupLinksCache != null) {
            return nodeGroupLinksCache;
        }
        return new ArrayList<NodeGroupLink>(0);
    }

    protected void checkNodeGroupLinkCache(boolean refreshCache) {
        long cacheTimeoutInMs = parameterService
                .getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS);
        if (System.currentTimeMillis() - nodeGroupLinkCacheTime >= cacheTimeoutInMs || nodeGroupLinksCache == null || refreshCache) {
            if (nodeGroupLinksCache == null) {
                try {
                    configurationCacheLock.acquire();
                    try {
                        if (System.currentTimeMillis() - nodeGroupLinkCacheTime >= cacheTimeoutInMs
                                || nodeGroupLinksCache == null || refreshCache) {
                            populateNodeGroupLinkCache(cacheTimeoutInMs);
                        }
                    } finally {
                        configurationCacheLock.release();
                    }
                } catch (InterruptedException e) {
                    log.error("Failed to retrieve node group links", e);
                }
            } else if (configurationCacheLock.tryAcquire()) {
                try {
                    populateNodeGroupLinkCache(cacheTimeoutInMs);
                } finally {
                    configurationCacheLock.release();
                }
            }
        }
    }

    protected void populateNodeGroupLinkCache(long cacheTimeoutInMs) {
        long ts = System.currentTimeMillis();
        nodeGroupLinksCache = configurationService.getNodeGroupLinksFromDb();
        nodeGroupLinkCacheTime = System.currentTimeMillis();
        long queryTime = nodeGroupLinkCacheTime - ts;
        if (queryTime > cacheTimeoutInMs) {
            log.warn("Query time of {} ms exceeded cache time of {} ms for node group links. "
                    + " This means the query may run on the database constantly.", queryTime, cacheTimeoutInMs);
        }
    }

    public Map<String, List<NodeGroupChannelWindow>> getNodeGroupChannelWindows() {
        checkNodeGroupChannelWindowCache();
        if (channelWindowsByChannelCache != null) {
            return channelWindowsByChannelCache;
        }
        return new HashMap<String, List<NodeGroupChannelWindow>>(0);
    }

    protected void checkNodeGroupChannelWindowCache() {
        long channelCacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS, 60000);
        if (System.currentTimeMillis() - channelWindowsByChannelCacheTime >= channelCacheTimeoutInMs || channelWindowsByChannelCache == null) {
            if (channelWindowsByChannelCache == null) {
                try {
                    configurationCacheLock.acquire();
                    try {
                        if (System.currentTimeMillis() - channelWindowsByChannelCacheTime >= channelCacheTimeoutInMs
                                || channelWindowsByChannelCache == null) {
                            populateNodeGroupChannelWindowCache(channelCacheTimeoutInMs);
                        }
                    } finally {
                        configurationCacheLock.release();
                    }
                } catch (InterruptedException e) {
                    log.error("Failed to retrieve node group channel windows", e);
                }
            } else if (configurationCacheLock.tryAcquire()) {
                try {
                    populateNodeGroupChannelWindowCache(channelCacheTimeoutInMs);
                } finally {
                    configurationCacheLock.release();
                }
            }
        }
    }

    protected void populateNodeGroupChannelWindowCache(long channelCacheTimeoutInMs) {
        long ts = System.currentTimeMillis();
        channelWindowsByChannelCache = configurationService.getNodeGroupChannelWindowsFromDb();
        channelWindowsByChannelCacheTime = System.currentTimeMillis();
        long queryTime = channelWindowsByChannelCacheTime - ts;
        if (queryTime > channelCacheTimeoutInMs) {
            log.warn("Query time of {} ms exceeded cache time of {} ms for node group channel windows. "
                    + " This means the query may run on the database constantly.", queryTime, channelCacheTimeoutInMs);
        }
    }

    public void flushNodeChannels() {
        if (configurationCacheLock.tryAcquire()) {
            nodeChannelCacheTime = 0l;
            configurationCacheLock.release();
        }
    }

    public void flushChannels() {
        if (configurationCacheLock.tryAcquire()) {
            channelCacheTime = 0l;
            configurationCacheLock.release();
        }
    }

    public void flushNodeGroupLinks() {
        if (configurationCacheLock.tryAcquire()) {
            nodeGroupLinkCacheTime = 0l;
            configurationCacheLock.release();
        }
    }

    public void flushNodeGroupChannelWindows() {
        if (configurationCacheLock.tryAcquire()) {
            channelWindowsByChannelCacheTime = 0l;
            configurationCacheLock.release();
        }
    }
}
