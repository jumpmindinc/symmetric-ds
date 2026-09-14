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
package org.jumpmind.symmetric.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITriggerRouterService;

public class TriggerRouterCache {
    private IParameterService parameterService;
    private ITriggerRouterService triggerRouterService;
    private Object triggerRouterCacheLock = new Object();
    private long triggerRoutersCacheTime;
    private List<TriggerRouter> triggerRoutersCache = new ArrayList<TriggerRouter>();
    private long triggerRouterPerChannelCacheTime;
    private Map<String, List<TriggerRouter>> triggerRouterCacheByChannel = new HashMap<String, List<TriggerRouter>>();
    private long triggerRoutersByTriggerHistCacheTime;
    private Map<String, Map<Integer, TriggerRouter>> triggerRoutersByTriggerHist;
    private long triggerRoutersByNodeGroupIdCacheTime;
    private Map<String, TriggerRouterRoutersCache> triggerRoutersByNodeGroupId;
    private Map<String, TriggerRouter> triggerRoutersByIdCache;
    private long triggerRoutersByIdCacheTime;
    private Map<String, Trigger> triggersCache;
    private long triggersCacheTime;
    private Map<String, Router> routersCache;
    private long routersCacheTime;
    private Boolean usingTargetExternalId;
    private long targetExternalIdCacheTime;

    public TriggerRouterCache(ISymmetricEngine engine) {
        this.parameterService = engine.getParameterService();
        this.triggerRouterService = engine.getTriggerRouterService();
    }

    public List<TriggerRouter> getTriggerRouters(boolean refreshCache) {
        long triggerRouterCacheTimeoutInMs = parameterService
                .getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS);
        if (triggerRoutersCache == null
                || refreshCache
                || System.currentTimeMillis() - this.triggerRoutersCacheTime > triggerRouterCacheTimeoutInMs) {
            synchronized (triggerRouterCacheLock) {
                if (triggerRoutersCache == null
                        || refreshCache
                        || System.currentTimeMillis() - this.triggerRoutersCacheTime > triggerRouterCacheTimeoutInMs) {
                    triggerRoutersCache = triggerRouterService.getTriggerRoutersFromDatabase();
                    triggerRoutersCacheTime = System.currentTimeMillis();
                }
            }
        }
        return triggerRoutersCache;
    }

    public void flushTriggerRouters() {
        synchronized (triggerRouterCacheLock) {
            triggerRoutersCacheTime = 0l;
        }
    }

    public Map<String, List<TriggerRouter>> getTriggerRoutersByChannel(String nodeGroupId, boolean refreshCache) {
        long triggerRouterCacheTimeout = parameterService
                .getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS);
        if (triggerRouterCacheByChannel == null
                || refreshCache
                || System.currentTimeMillis() - this.triggerRouterPerChannelCacheTime > triggerRouterCacheTimeout) {
            synchronized (triggerRouterCacheLock) {
                if (triggerRouterCacheByChannel == null || refreshCache
                        || System.currentTimeMillis() - this.triggerRouterPerChannelCacheTime > triggerRouterCacheTimeout) {
                    this.triggerRouterPerChannelCacheTime = System.currentTimeMillis();
                    triggerRouterCacheByChannel = triggerRouterService.getTriggerRoutersByChannelFromDatabase(nodeGroupId);
                }
            }
        }
        return triggerRouterCacheByChannel;
    }

    public void flushTriggerRoutersByChannel() {
        synchronized (triggerRouterCacheLock) {
            triggerRouterPerChannelCacheTime = 0l;
        }
    }

    public Map<String, Map<Integer, TriggerRouter>> getTriggerRoutersByTriggerHist(boolean refreshCache) {
        long cacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS);
        if (triggerRoutersByTriggerHist == null || refreshCache || System.currentTimeMillis() - triggerRoutersByTriggerHistCacheTime > cacheTimeoutInMs) {
            synchronized (triggerRouterCacheLock) {
                if (triggerRoutersByTriggerHist == null || refreshCache || System.currentTimeMillis()
                        - triggerRoutersByTriggerHistCacheTime > cacheTimeoutInMs) {
                    triggerRoutersByTriggerHistCacheTime = System.currentTimeMillis();
                    Map<String, Map<Integer, TriggerRouter>> cache = new HashMap<String, Map<Integer, TriggerRouter>>();
                    Map<String, List<TriggerRouter>> triggerRouters = triggerRouterService.getTriggerRoutersForCurrentNode(true);
                    Map<String, TriggerHistory> triggerHistoryByTrigger = new HashMap<String, TriggerHistory>();
                    for (TriggerHistory hist : triggerRouterService.getActiveTriggerHistories()) {
                        triggerHistoryByTrigger.put(hist.getTriggerId(), hist);
                    }
                    for (List<TriggerRouter> list : triggerRouters.values()) {
                        for (TriggerRouter triggerRouter : list) {
                            String groupId = triggerRouter.getRouter().getNodeGroupLink().getTargetNodeGroupId();
                            Map<Integer, TriggerRouter> map = cache.get(groupId);
                            if (map == null) {
                                map = new HashMap<Integer, TriggerRouter>();
                                cache.put(groupId, map);
                            }
                            TriggerHistory hist = triggerHistoryByTrigger.get(triggerRouter.getTriggerId());
                            if (hist != null) {
                                map.put(hist.getTriggerHistoryId(), triggerRouter);
                            }
                        }
                    }
                    triggerRoutersByTriggerHist = cache;
                }
            }
        }
        return triggerRoutersByTriggerHist;
    }

    public void flushTriggerRoutersByTriggerHist() {
        synchronized (triggerRouterCacheLock) {
            triggerRoutersByTriggerHistCacheTime = 0l;
        }
    }

    public Map<String, TriggerRouterRoutersCache> getTriggerRoutersByNodeGroupId(boolean refreshCache) {
        long cacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS);
        if (triggerRoutersByNodeGroupId == null || refreshCache || System.currentTimeMillis() - triggerRoutersByNodeGroupIdCacheTime > cacheTimeoutInMs) {
            synchronized (triggerRouterCacheLock) {
                if (triggerRoutersByNodeGroupId == null || refreshCache || System.currentTimeMillis()
                        - triggerRoutersByTriggerHistCacheTime > cacheTimeoutInMs) {
                    triggerRoutersByNodeGroupIdCacheTime = System.currentTimeMillis();
                    triggerRoutersByNodeGroupId = triggerRouterService.getTriggerRoutersCacheByNodeGroupIdFromDatabase();
                }
            }
        }
        return triggerRoutersByNodeGroupId;
    }

    public void flushTriggerRoutersByNodeGroupId() {
        synchronized (triggerRouterCacheLock) {
            triggerRoutersByNodeGroupIdCacheTime = 0l;
            usingTargetExternalId = null;
        }
    }

    public boolean isUsingTargetExternalId(boolean refreshCache) {
        long cacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS);
        if (usingTargetExternalId == null || refreshCache || System.currentTimeMillis() - targetExternalIdCacheTime > cacheTimeoutInMs) {
            synchronized (triggerRouterCacheLock) {
                usingTargetExternalId = false;
                TriggerRouterRoutersCache cache = getTriggerRoutersByNodeGroupId(false).get(parameterService.getNodeGroupId());
                if (cache != null) {
                    for (List<TriggerRouter> list : cache.triggerRoutersByTriggerId.values()) {
                        for (TriggerRouter triggerRouter : list) {
                            if (triggerRouter.getTrigger().getSourceTableName().contains("$(targetExternalId)")) {
                                usingTargetExternalId = true;
                                break;
                            }
                        }
                    }
                }
                targetExternalIdCacheTime = System.currentTimeMillis();
            }
        }
        return usingTargetExternalId;
    }

    public Map<String, TriggerRouter> getTriggerRoutersById(boolean refreshCache) {
        long cacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS);
        if (triggerRoutersByIdCache == null || refreshCache || System.currentTimeMillis() - triggerRoutersByIdCacheTime > cacheTimeoutInMs) {
            synchronized (triggerRouterCacheLock) {
                if (triggerRoutersByIdCache == null || refreshCache || System.currentTimeMillis() - triggerRoutersByIdCacheTime > cacheTimeoutInMs) {
                    Map<String, TriggerRouter> map = new HashMap<String, TriggerRouter>();
                    for (TriggerRouter triggerRouter : triggerRouterService.getTriggerRoutersFromDatabase()) {
                        map.put(triggerRouter.getIdentifier(), triggerRouter);
                    }
                    triggerRoutersByIdCache = map;
                    triggerRoutersByIdCacheTime = System.currentTimeMillis();
                }
            }
        }
        return triggerRoutersByIdCache;
    }

    public void flushTriggerRoutersById() {
        synchronized (triggerRouterCacheLock) {
            triggerRoutersByIdCacheTime = 0l;
        }
    }

    public Map<String, Trigger> getTriggers(boolean refreshCache) {
        final long triggerCacheTimeoutInMs = parameterService
                .getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS);
        if (triggersCache == null || refreshCache
                || (System.currentTimeMillis() - this.triggersCacheTime) > triggerCacheTimeoutInMs) {
            synchronized (triggerRouterCacheLock) {
                if (triggersCache == null || refreshCache
                        || (System.currentTimeMillis() - this.triggersCacheTime) > triggerCacheTimeoutInMs) {
                    this.triggersCacheTime = System.currentTimeMillis();
                    List<Trigger> triggers = new ArrayList<Trigger>(triggerRouterService.getTriggers());
                    triggers.addAll(triggerRouterService.buildTriggersForSymmetricTables(Version.version()));
                    Map<String, Trigger> cache = new HashMap<String, Trigger>(triggers.size());
                    for (Trigger t : triggers) {
                        cache.put(t.getTriggerId(), t);
                    }
                    this.triggersCache = cache;
                }
            }
        }
        return triggersCache;
    }

    public void flushTriggers() {
        synchronized (triggerRouterCacheLock) {
            triggersCacheTime = 0l;
        }
    }

    public Map<String, Router> getRouters(boolean refreshCache) {
        final long routerCacheTimeoutInMs = parameterService
                .getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS);
        if (routersCache == null || refreshCache
                || System.currentTimeMillis() - this.routersCacheTime > routerCacheTimeoutInMs) {
            synchronized (triggerRouterCacheLock) {
                if (routersCache == null || refreshCache
                        || System.currentTimeMillis() - this.routersCacheTime > routerCacheTimeoutInMs) {
                    this.routersCacheTime = System.currentTimeMillis();
                    List<Router> routers = triggerRouterService.getRouters();
                    Map<String, Router> cache = new HashMap<String, Router>(routers.size());
                    for (Router router : routers) {
                        cache.put(router.getRouterId(), router);
                    }
                    this.routersCache = cache;
                }
            }
        }
        return routersCache;
    }

    public void flushRouters() {
        synchronized (triggerRouterCacheLock) {
            routersCacheTime = 0l;
        }
    }
}
