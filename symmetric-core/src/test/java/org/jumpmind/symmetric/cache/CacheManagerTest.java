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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Grouplet;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroupChannelWindow;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.model.LoadFilter.LoadFilterType;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.IGroupletService;
import org.jumpmind.symmetric.service.ILoadFilterService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.impl.DataLoaderService.ConflictNodeGroupLink;
import org.jumpmind.symmetric.service.impl.LoadFilterService.LoadFilterNodeGroupLink;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CacheManagerTest {
    private ISymmetricEngine engine;
    private ITriggerRouterService triggerRouterService;
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private IParameterService parameterService;
    private IDataLoaderService dataLoaderService;
    private IFileSyncService fileSyncService;
    private IGroupletService groupletService;
    private ILoadFilterService loadFilterService;
    private ITransformService transformService;

    @BeforeEach
    public void setup() {
        parameterService = mock(IParameterService.class);
        engine = mock(AbstractSymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        configurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        dataLoaderService = mock(IDataLoaderService.class);
        when(engine.getDataLoaderService()).thenReturn(dataLoaderService);
        fileSyncService = mock(IFileSyncService.class);
        when(engine.getFileSyncService()).thenReturn(fileSyncService);
        groupletService = mock(IGroupletService.class);
        when(engine.getGroupletService()).thenReturn(groupletService);
        loadFilterService = mock(ILoadFilterService.class);
        when(engine.getLoadFilterService()).thenReturn(loadFilterService);
        transformService = mock(ITransformService.class);
        when(engine.getTransformService()).thenReturn(transformService);
    }

    @Test
    public void triggerRoutersCacheTest() {
        List<TriggerRouter> triggerRoutersList = Arrays.asList(new TriggerRouter());
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(600000l);
        when(triggerRouterService.getTriggerRoutersFromDatabase()).thenReturn(triggerRoutersList);
        CacheManager cacheManager = new CacheManager(engine);
        List<TriggerRouter> l = cacheManager.getTriggerRouters(false);
        assertEquals(1, l.size());
        triggerRoutersList = Arrays.asList(new TriggerRouter(), new TriggerRouter());
        when(triggerRouterService.getTriggerRoutersFromDatabase()).thenReturn(triggerRoutersList);
        cacheManager.flushTriggerRouters();
        l = cacheManager.getTriggerRouters(false);
        assertEquals(2, l.size());
        triggerRoutersList = Arrays.asList(new TriggerRouter());
        when(triggerRouterService.getTriggerRoutersFromDatabase()).thenReturn(triggerRoutersList);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        l = cacheManager.getTriggerRouters(false);
        assertEquals(1, l.size());
    }

    @Test
    public void triggerRoutersByChannelCacheTest() {
        Trigger t = new Trigger("t1", "channel1");
        Router r = new Router();
        TriggerRouter tr = new TriggerRouter(t, r);
        List<TriggerRouter> triggerRoutersList = Arrays.asList(tr);
        Map<String, List<TriggerRouter>> m = new HashMap<String, List<TriggerRouter>>();
        m.put("channel1", triggerRoutersList);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(600000l);
        when(triggerRouterService.getTriggerRoutersByChannelFromDatabase("channel1")).thenReturn(m);
        CacheManager cacheManager = new CacheManager(engine);
        assertEquals(1, cacheManager.getTriggerRoutersByChannel("channel1", false).size());
        assertEquals("channel1", cacheManager.getTriggerRoutersByChannel("nodeGroup1", false).get("channel1").get(0).getTrigger().getChannelId());
        Trigger t2 = new Trigger("t2", "channel2");
        Router r2 = new Router();
        TriggerRouter tr2 = new TriggerRouter(t2, r2);
        triggerRoutersList = Arrays.asList(tr);
        m.clear();
        m.put("channel1", triggerRoutersList);
        triggerRoutersList = Arrays.asList(tr2);
        m.put("channel2", triggerRoutersList);
        when(triggerRouterService.getTriggerRoutersByChannelFromDatabase("nodeGroup1")).thenReturn(m);
        cacheManager.flushTriggerRoutersByChannel();
        assertEquals(2, cacheManager.getTriggerRoutersByChannel("nodeGroup1", false).size());
        assertEquals("channel1", cacheManager.getTriggerRoutersByChannel("nodeGroup1", false).get("channel1").get(0).getTrigger().getChannelId());
        assertEquals("channel2", cacheManager.getTriggerRoutersByChannel("nodeGroup1", false).get("channel2").get(0).getTrigger().getChannelId());
        m.remove("channel2");
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(5l);
        when(triggerRouterService.getTriggerRoutersByChannelFromDatabase("nodeGroup1")).thenReturn(m);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        assertEquals(1, cacheManager.getTriggerRoutersByChannel("nodeGroup1", false).size());
        assertEquals("channel1", cacheManager.getTriggerRoutersByChannel("nodeGroup1", false).get("channel1").get(0).getTrigger().getChannelId());
        assertNull(cacheManager.getTriggerRoutersByChannel("nodeGroup1", false).get("channel2"));
    }

    @Test
    public void triggerRoutersByTriggerHistCacheTest() {
        Trigger t1 = new Trigger("t1", "channel1");
        t1.setTriggerId("trigger1");
        TriggerHistory th1 = new TriggerHistory(t1);
        th1.setTriggerHistoryId(10);
        NodeGroupLink ng1 = new NodeGroupLink("source", "target");
        Router r1 = new Router("r1", ng1);
        TriggerRouter tr1 = new TriggerRouter(t1, r1);
        Map<String, List<TriggerRouter>> m = new HashMap<String, List<TriggerRouter>>();
        m.put(t1.getTriggerId(), Arrays.asList(tr1));
        when(triggerRouterService.getTriggerRoutersForCurrentNode(true)).thenReturn(m);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(600000l);
        when(triggerRouterService.getActiveTriggerHistories()).thenReturn(Arrays.asList(th1));
        CacheManager cacheManager = new CacheManager(engine);
        assertEquals(1, cacheManager.getTriggerRoutersByTriggerHist(false).size());
        assertEquals("trigger1", cacheManager.getTriggerRoutersByTriggerHist(false).get("target").get(10).getTriggerId());
        Trigger t2 = new Trigger("t2", "channel2");
        t2.setTriggerId("trigger2");
        TriggerHistory th2 = new TriggerHistory(t2);
        th2.setTriggerHistoryId(11);
        TriggerRouter tr2 = new TriggerRouter(t2, r1);
        m.put(t2.getTriggerId(), Arrays.asList(tr2));
        when(triggerRouterService.getTriggerRoutersForCurrentNode(true)).thenReturn(m);
        when(triggerRouterService.getActiveTriggerHistories()).thenReturn(Arrays.asList(th1, th2));
        cacheManager.flushTriggerRoutersByTriggerHist();
        assertEquals(1, cacheManager.getTriggerRoutersByTriggerHist(false).size());
        assertEquals(2, cacheManager.getTriggerRoutersByTriggerHist(false).get("target").size());
        assertEquals("trigger1", cacheManager.getTriggerRoutersByTriggerHist(false).get("target").get(10).getTriggerId());
        assertEquals("trigger2", cacheManager.getTriggerRoutersByTriggerHist(false).get("target").get(11).getTriggerId());
        m.remove(t1.getTriggerId());
        when(triggerRouterService.getTriggerRoutersForCurrentNode(true)).thenReturn(m);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        assertEquals(1, cacheManager.getTriggerRoutersByTriggerHist(false).size());
        assertEquals(1, cacheManager.getTriggerRoutersByTriggerHist(false).get("target").size());
        assertEquals("trigger2", cacheManager.getTriggerRoutersByTriggerHist(false).get("target").get(11).getTriggerId());
    }

    @Test
    public void triggersCacheTest() {
        // triggerRouterService.getTriggers()
        // triggerRouterService.buildTriggersForSymmetricTables(Version.version())
        Trigger t1 = new Trigger("t1", "channel1");
        t1.setTriggerId("trigger1");
        when(triggerRouterService.getTriggers()).thenReturn(Arrays.asList(t1));
        when(triggerRouterService.buildTriggersForSymmetricTables(Version.version())).thenReturn(new ArrayList<Trigger>());
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        assertEquals(1, cacheManager.getTriggers(false).size());
        assertEquals("trigger1", cacheManager.getTriggers(false).get(t1.getTriggerId()).getTriggerId());
        Trigger t2 = new Trigger("t2", "channel2");
        t2.setTriggerId("trigger2");
        when(triggerRouterService.getTriggers()).thenReturn(Arrays.asList(t1, t2));
        cacheManager.flushTriggers();
        assertEquals(2, cacheManager.getTriggers(false).size());
        assertEquals("trigger1", cacheManager.getTriggers(false).get(t1.getTriggerId()).getTriggerId());
        assertEquals("trigger2", cacheManager.getTriggers(false).get(t2.getTriggerId()).getTriggerId());
        when(triggerRouterService.getTriggers()).thenReturn(Arrays.asList(t2));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        assertEquals(1, cacheManager.getTriggers(false).size());
        assertEquals("trigger2", cacheManager.getTriggers(false).get(t2.getTriggerId()).getTriggerId());
    }

    @Test
    public void routersCacheTest() {
        NodeGroupLink link = new NodeGroupLink("source", "target");
        Router r1 = new Router("r1", link);
        when(triggerRouterService.getRouters()).thenReturn(Arrays.asList(r1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        assertEquals(1, cacheManager.getRouters(false).size());
        assertEquals("r1", cacheManager.getRouters(false).get("r1").getRouterId());
        Router r2 = new Router("r2", link);
        when(triggerRouterService.getRouters()).thenReturn(Arrays.asList(r1, r2));
        cacheManager.flushRouters();
        assertEquals(2, cacheManager.getRouters(false).size());
        assertEquals("r1", cacheManager.getRouters(false).get("r1").getRouterId());
        assertEquals("r2", cacheManager.getRouters(false).get("r2").getRouterId());
        when(triggerRouterService.getRouters()).thenReturn(Arrays.asList(r2));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        assertEquals(1, cacheManager.getRouters(false).size());
        assertEquals("r2", cacheManager.getRouters(false).get("r2").getRouterId());
    }

    @Test
    public void triggerRoutersByIdCacheTest() {
        Trigger t1 = new Trigger();
        t1.setTriggerId("t1");
        Router r1 = new Router("r1", new NodeGroupLink("sourde", "target"));
        TriggerRouter tr1 = new TriggerRouter(t1, r1);
        Trigger t2 = new Trigger();
        t2.setTriggerId("t2");
        TriggerRouter tr2 = new TriggerRouter(t2, r1);
        tr2.setTriggerId("tr2");
        List<TriggerRouter> triggerRoutersList = Arrays.asList(tr1);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(600000l);
        when(triggerRouterService.getTriggerRoutersFromDatabase()).thenReturn(triggerRoutersList);
        CacheManager cacheManager = new CacheManager(engine);
        Map<String, TriggerRouter> m = cacheManager.getTriggerRoutersById(false);
        assertEquals(1, m.size());
        triggerRoutersList = Arrays.asList(tr1, tr2);
        when(triggerRouterService.getTriggerRoutersFromDatabase()).thenReturn(triggerRoutersList);
        cacheManager.flushTriggerRoutersById();
        m = cacheManager.getTriggerRoutersById(false);
        assertEquals(2, m.size());
        triggerRoutersList = Arrays.asList(tr2);
        when(triggerRouterService.getTriggerRoutersFromDatabase()).thenReturn(triggerRoutersList);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        m = cacheManager.getTriggerRoutersById(false);
        assertEquals(1, m.size());
    }

    @Test
    public void triggerRoutersByNodeGroupIdCacheTest() {
        Trigger t1 = new Trigger();
        t1.setTriggerId("t1");
        Router r1 = new Router();
        r1.setRouterId("r1");
        TriggerRouter tr1 = new TriggerRouter(t1, r1);
        Map<String, List<TriggerRouter>> triggerRoutersByTriggerId = new HashMap<String, List<TriggerRouter>>();
        triggerRoutersByTriggerId.put(tr1.getTrigger().getTriggerId(), Arrays.asList(tr1));
        Map<String, Router> routers = new HashMap<String, Router>();
        routers.put(r1.getRouterId(), r1);
        TriggerRouterRoutersCache triggerRoutersCache = new TriggerRouterRoutersCache(triggerRoutersByTriggerId, routers);
        Map<String, TriggerRouterRoutersCache> m = new HashMap<String, TriggerRouterRoutersCache>();
        m.put("mynode", triggerRoutersCache);
        when(triggerRouterService.getTriggerRoutersCacheByNodeGroupIdFromDatabase()).thenReturn(m);
        CacheManager cacheManager = new CacheManager(engine);
        Map<String, TriggerRouterRoutersCache> ret = cacheManager.getTriggerRoutersByNodeGroupId(false);
        assertEquals(1, ret.size());
        TriggerRouterRoutersCache c = ret.get("mynode");
        assertEquals(1, c.triggerRoutersByTriggerId.size());
        assertEquals(1, c.routersByRouterId.size());
        Trigger t2 = new Trigger();
        t2.setTriggerId("t2");
        TriggerRouter tr2 = new TriggerRouter(t2, r1);
        triggerRoutersByTriggerId.put(tr2.getTrigger().getTriggerId(), Arrays.asList(tr2));
        Router r2 = new Router();
        r2.setRouterId("r2");
        routers.put(r2.getRouterId(), r2);
        triggerRoutersCache = new TriggerRouterRoutersCache(triggerRoutersByTriggerId, routers);
        m.put("mynode", triggerRoutersCache);
        when(triggerRouterService.getTriggerRoutersCacheByNodeGroupIdFromDatabase()).thenReturn(m);
        cacheManager.flushTriggerRoutersByNodeGroupId();
        ret = cacheManager.getTriggerRoutersByNodeGroupId(false);
        assertEquals(1, ret.size());
        c = ret.get("mynode");
        assertEquals(2, c.triggerRoutersByTriggerId.size());
        assertEquals(2, c.routersByRouterId.size());
        triggerRoutersByTriggerId.remove(tr1.getTrigger().getTriggerId());
        routers.remove(r1.getRouterId());
        triggerRoutersCache = new TriggerRouterRoutersCache(triggerRoutersByTriggerId, routers);
        m.put("mynode", triggerRoutersCache);
        when(triggerRouterService.getTriggerRoutersCacheByNodeGroupIdFromDatabase()).thenReturn(m);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        ret = cacheManager.getTriggerRoutersByNodeGroupId(false);
        assertEquals(1, ret.size());
        c = ret.get("mynode");
        assertEquals(1, c.triggerRoutersByTriggerId.size());
        assertEquals(1, c.routersByRouterId.size());
    }

    @Test
    public void sourceNodesCacheTest() {
        NodeGroupLinkAction eventAction = NodeGroupLinkAction.P;
        Node node = new Node("mynode", "mygroup");
        Node t1 = new Node("t1", "group1");
        when(nodeService.getSourceNodesFromDatabase(eventAction, node)).thenReturn(Arrays.asList(t1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        List<Node> sourceNodes = cacheManager.getSourceNodesCache(eventAction, node);
        assertEquals(1, sourceNodes.size());
        Node t2 = new Node("t2", "group1");
        when(nodeService.getSourceNodesFromDatabase(eventAction, node)).thenReturn(Arrays.asList(t1, t2));
        cacheManager.flushSourceNodesCache();
        sourceNodes = cacheManager.getSourceNodesCache(eventAction, node);
        assertEquals(2, sourceNodes.size());
        when(nodeService.getSourceNodesFromDatabase(eventAction, node)).thenReturn(Arrays.asList(t1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        sourceNodes = cacheManager.getSourceNodesCache(eventAction, node);
        assertEquals(1, sourceNodes.size());
    }

    @Test
    public void targetNodesCacheTest() {
        NodeGroupLinkAction eventAction = NodeGroupLinkAction.P;
        Node node = new Node("mynode", "mygroup");
        Node t1 = new Node("t1", "group1");
        when(nodeService.getTargetNodesFromDatabase(eventAction, node)).thenReturn(Arrays.asList(t1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        List<Node> targetNodes = cacheManager.getTargetNodesCache(eventAction, node);
        assertEquals(1, targetNodes.size());
        Node t2 = new Node("t2", "group1");
        when(nodeService.getTargetNodesFromDatabase(eventAction, node)).thenReturn(Arrays.asList(t1, t2));
        cacheManager.flushTargetNodesCache();
        targetNodes = cacheManager.getTargetNodesCache(eventAction, node);
        assertEquals(2, targetNodes.size());
        when(nodeService.getTargetNodesFromDatabase(eventAction, node)).thenReturn(Arrays.asList(t1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        targetNodes = cacheManager.getTargetNodesCache(eventAction, node);
        assertEquals(1, targetNodes.size());
    }

    @Test
    public void nodeChannelsCacheTest() {
        NodeChannel n1 = new NodeChannel("channel1");
        when(configurationService.getNodeChannelsFromDb("mynode")).thenReturn(Arrays.asList(n1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        List<NodeChannel> nodeChannelCache = cacheManager.getNodeChannels("mynode");
        assertEquals(1, nodeChannelCache.size());
        NodeChannel n2 = new NodeChannel("channel2");
        when(configurationService.getNodeChannelsFromDb("mynode")).thenReturn(Arrays.asList(n1, n2));
        cacheManager.flushNodeChannels();
        nodeChannelCache = cacheManager.getNodeChannels("mynode");
        assertEquals(2, nodeChannelCache.size());
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS)).thenReturn(5l);
        when(configurationService.getNodeChannelsFromDb("mynode")).thenReturn(Arrays.asList(n2));
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        nodeChannelCache = cacheManager.getNodeChannels("mynode");
        assertEquals(1, nodeChannelCache.size());
        assertEquals("channel2", nodeChannelCache.get(0).getChannelId());
    }

    @Test
    public void channelsCacheTest() {
        Map<String, Channel> channels = new HashMap<String, Channel>();
        Channel channel1 = new Channel("channel1", 50);
        channels.put(channel1.getChannelId(), channel1);
        when(configurationService.getChannelsFromDb()).thenReturn(channels);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        Map<String, Channel> channelCache = cacheManager.getChannels(false);
        assertEquals(1, channelCache.size());
        Channel channel2 = new Channel("channel2", 50);
        channels.put(channel2.getChannelId(), channel2);
        when(configurationService.getChannelsFromDb()).thenReturn(channels);
        cacheManager.flushChannels();
        channelCache = cacheManager.getChannels(false);
        assertEquals(2, channelCache.size());
        channels.remove(channel1.getChannelId());
        when(configurationService.getChannelsFromDb()).thenReturn(channels);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        channelCache = cacheManager.getChannels(false);
        assertEquals(1, channelCache.size());
        assertEquals(channel2.getChannelId(), channelCache.get(channel2.getChannelId()).getChannelId());
    }

    @Test
    public void nodeGroupLinksCacheTest() {
        List<NodeGroupLink> nodeGroupLinks = new ArrayList<NodeGroupLink>();
        NodeGroupLink nodeGroupLink1 = new NodeGroupLink("source1", "target");
        nodeGroupLinks.add(nodeGroupLink1);
        when(configurationService.getNodeGroupLinksFromDb()).thenReturn(nodeGroupLinks);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        List<NodeGroupLink> nodeGroupLinkCache = cacheManager.getNodeGroupLinks(false);
        assertEquals(1, nodeGroupLinkCache.size());
        NodeGroupLink nodegroupLink2 = new NodeGroupLink("source2", "target");
        nodeGroupLinks.add(nodegroupLink2);
        when(configurationService.getNodeGroupLinksFromDb()).thenReturn(nodeGroupLinks);
        cacheManager.flushNodeGroupLinks();
        nodeGroupLinkCache = cacheManager.getNodeGroupLinks(false);
        assertEquals(2, nodeGroupLinkCache.size());
        nodeGroupLinks.remove(nodeGroupLink1);
        when(configurationService.getNodeGroupLinksFromDb()).thenReturn(nodeGroupLinks);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        nodeGroupLinkCache = cacheManager.getNodeGroupLinks(false);
        assertEquals(1, nodeGroupLinkCache.size());
        assertEquals("source2", nodeGroupLinkCache.get(0).getSourceNodeGroupId());
    }

    @Test
    public void nodeGroupChannelWindowsCacheTest() {
        Map<String, List<NodeGroupChannelWindow>> nodeGroupChannelWindows = new HashMap<String, List<NodeGroupChannelWindow>>();
        NodeGroupChannelWindow n1 = new NodeGroupChannelWindow();
        n1.setChannelId("channel1");
        nodeGroupChannelWindows.put(n1.getChannelId(), Arrays.asList(n1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS, 60000)).thenReturn(600000l);
        when(configurationService.getNodeGroupChannelWindowsFromDb()).thenReturn(nodeGroupChannelWindows);
        CacheManager cacheManager = new CacheManager(engine);
        Map<String, List<NodeGroupChannelWindow>> nodeGroupChannelWindowsCache = cacheManager.getNodeGroupChannelWindows();
        assertEquals(1, nodeGroupChannelWindowsCache.size());
        NodeGroupChannelWindow n2 = new NodeGroupChannelWindow();
        n2.setChannelId("channel2");
        nodeGroupChannelWindows.put(n2.getChannelId(), Arrays.asList(n2));
        when(configurationService.getNodeGroupChannelWindowsFromDb()).thenReturn(nodeGroupChannelWindows);
        cacheManager.flushNodeGroupChannelWindows();
        nodeGroupChannelWindowsCache = cacheManager.getNodeGroupChannelWindows();
        assertEquals(2, nodeGroupChannelWindowsCache.size());
        nodeGroupChannelWindows.remove(n1.getChannelId());
        when(configurationService.getNodeGroupChannelWindowsFromDb()).thenReturn(nodeGroupChannelWindows);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CHANNEL_IN_MS, 60000)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        nodeGroupChannelWindowsCache = cacheManager.getNodeGroupChannelWindows();
        assertEquals(1, nodeGroupChannelWindowsCache.size());
        assertEquals(n2.getChannelId(), nodeGroupChannelWindowsCache.get(n2.getChannelId()).get(0).getChannelId());
    }

    @Test
    public void conflictSettingsNodeGroupLinksCacheTest() {
        NodeGroupLink n1 = new NodeGroupLink("source1", "target");
        ConflictNodeGroupLink c1 = new ConflictNodeGroupLink();
        c1.setConflictId("conflict1");
        when(dataLoaderService.getConflictSettinsNodeGroupLinksFromDb(n1)).thenReturn(Arrays.asList(c1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CONFLICT_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        List<ConflictNodeGroupLink> conflictNodeGroupLinkCache = cacheManager.getConflictSettingsNodeGroupLinks(n1, false);
        assertEquals(1, conflictNodeGroupLinkCache.size());
        ConflictNodeGroupLink c2 = new ConflictNodeGroupLink();
        c2.setConflictId("conflict2");
        when(dataLoaderService.getConflictSettinsNodeGroupLinksFromDb(n1)).thenReturn(Arrays.asList(c1, c2));
        cacheManager.flushConflictSettingsNodeGroupLinks();
        conflictNodeGroupLinkCache = cacheManager.getConflictSettingsNodeGroupLinks(n1, false);
        assertEquals(2, conflictNodeGroupLinkCache.size());
        when(dataLoaderService.getConflictSettinsNodeGroupLinksFromDb(n1)).thenReturn(Arrays.asList(c2));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_CONFLICT_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        conflictNodeGroupLinkCache = cacheManager.getConflictSettingsNodeGroupLinks(n1, false);
        assertEquals(1, conflictNodeGroupLinkCache.size());
        assertEquals(c2.getConflictId(), conflictNodeGroupLinkCache.get(0).getConflictId());
    }

    @Test
    public void fileTriggerRoutersCacheTest() {
        FileTriggerRouter fileTriggerRouter1 = new FileTriggerRouter();
        FileTrigger fileTrigger1 = new FileTrigger();
        fileTrigger1.setTriggerId("trigger1");
        Router router = new Router();
        fileTriggerRouter1.setFileTrigger(fileTrigger1);
        fileTriggerRouter1.setRouter(router);
        when(fileSyncService.getFileTriggerRoutersFromDb()).thenReturn(Arrays.asList(fileTriggerRouter1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        List<FileTriggerRouter> fileTriggerRouterCache = cacheManager.getFileTriggerRouters(false);
        assertEquals(1, fileTriggerRouterCache.size());
        FileTrigger fileTrigger2 = new FileTrigger();
        fileTrigger2.setTriggerId("trigger2");
        FileTriggerRouter fileTriggerRouter2 = new FileTriggerRouter();
        fileTriggerRouter2.setFileTrigger(fileTrigger2);
        fileTriggerRouter2.setRouter(router);
        when(fileSyncService.getFileTriggerRoutersFromDb()).thenReturn(Arrays.asList(fileTriggerRouter1, fileTriggerRouter2));
        cacheManager.flushFileTriggerRouters();
        fileTriggerRouterCache = cacheManager.getFileTriggerRouters(false);
        assertEquals(2, fileTriggerRouterCache.size());
        when(fileSyncService.getFileTriggerRoutersFromDb()).thenReturn(Arrays.asList(fileTriggerRouter2));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRIGGER_ROUTER_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        fileTriggerRouterCache = cacheManager.getFileTriggerRouters(false);
        assertEquals(1, fileTriggerRouterCache.size());
        assertEquals(fileTrigger2.getTriggerId(), fileTriggerRouterCache.get(0).getTriggerId());
    }

    @Test
    public void groupletsCacheTest() {
        Grouplet grouplet1 = new Grouplet();
        grouplet1.setGroupletId("grouplet1");
        when(groupletService.getGroupletsFromDb()).thenReturn(Arrays.asList(grouplet1));
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_GROUPLETS_IN_MS)).thenReturn(600000l);
        when(engine.getParameterService().is(ParameterConstants.GROUPLET_ENABLE)).thenReturn(false);
        CacheManager cacheManager = new CacheManager(engine);
        List<Grouplet> groupletCache = cacheManager.getGrouplets(false);
        assertEquals(0, groupletCache.size());
        when(engine.getParameterService().is(ParameterConstants.GROUPLET_ENABLE)).thenReturn(true);
        groupletCache = cacheManager.getGrouplets(false);
        assertEquals(1, groupletCache.size());
        Grouplet grouplet2 = new Grouplet();
        grouplet2.setGroupletId("grouplet2");
        when(groupletService.getGroupletsFromDb()).thenReturn(Arrays.asList(grouplet1, grouplet2));
        cacheManager.flushGrouplets();
        groupletCache = cacheManager.getGrouplets(false);
        assertEquals(2, groupletCache.size());
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_GROUPLETS_IN_MS)).thenReturn(5l);
        when(groupletService.getGroupletsFromDb()).thenReturn(Arrays.asList(grouplet2));
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        groupletCache = cacheManager.getGrouplets(false);
        assertEquals(1, groupletCache.size());
        assertEquals(grouplet2.getGroupletId(), groupletCache.get(0).getGroupletId());
    }

    @Test
    public void loadFiltersCacheTest() {
        LoadFilter loadFilter = new LoadFilter();
        loadFilter.setLoadFilterId("loadFilter1");
        loadFilter.setLoadFilterType(LoadFilterType.BSH);
        loadFilter.setTargetTableName("t1");
        List<LoadFilter> loadFiltersForTable = new ArrayList<LoadFilter>();
        loadFiltersForTable.add(loadFilter);
        Map<String, List<LoadFilter>> loadFiltersByTable = new HashMap<String, List<LoadFilter>>();
        loadFiltersByTable.put(loadFilter.getTargetTableName(), loadFiltersForTable);
        Map<LoadFilterType, Map<String, List<LoadFilter>>> loadFiltersByType = new HashMap<LoadFilterType, Map<String, List<LoadFilter>>>();
        loadFiltersByType.put(loadFilter.getLoadFilterType(), loadFiltersByTable);
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> loadFilters = new HashMap<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>>();
        LoadFilterNodeGroupLink loadFilterNodeGroupLink = new LoadFilterNodeGroupLink();
        NodeGroupLink nodeGroupLink = new NodeGroupLink("source1", "target");
        loadFilterNodeGroupLink.setNodeGroupLink(nodeGroupLink);
        loadFilters.put(nodeGroupLink, loadFiltersByType);
        when(loadFilterService.findLoadFiltersFromDb()).thenReturn(loadFilters);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_LOAD_FILTER_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> loadFiltersCache = cacheManager.findLoadFilters(nodeGroupLink, false);
        assertEquals(1, loadFiltersCache.size());
        assertEquals(1, loadFiltersCache.get(nodeGroupLink).size());
        assertEquals(1, loadFiltersCache.get(nodeGroupLink).get(loadFilter.getLoadFilterType()).get(loadFilter.getTargetTableName()).size());
        assertEquals(loadFilter.getTargetTableName(), loadFiltersCache.get(nodeGroupLink).get(loadFilter.getLoadFilterType()).get(loadFilter
                .getTargetTableName()).get(0).getTargetTableName());
        LoadFilter loadFilter2 = new LoadFilter();
        loadFilter2.setLoadFilterId("loadFilter2");
        loadFilter2.setLoadFilterType(LoadFilterType.BSH);
        loadFilter2.setTargetTableName("t1");
        loadFiltersForTable.add(loadFilter2);
        when(loadFilterService.findLoadFiltersFromDb()).thenReturn(loadFilters);
        cacheManager.flushLoadFilters();
        loadFiltersCache = cacheManager.findLoadFilters(nodeGroupLink, false);
        assertEquals(1, loadFiltersCache.size());
        assertEquals(1, loadFiltersCache.get(nodeGroupLink).size());
        assertEquals(2, loadFiltersCache.get(nodeGroupLink).get(loadFilter.getLoadFilterType()).get(loadFilter.getTargetTableName()).size());
        assertEquals(loadFilter.getTargetTableName(), loadFiltersCache.get(nodeGroupLink).get(loadFilter.getLoadFilterType()).get(loadFilter
                .getTargetTableName()).get(0).getTargetTableName());
        assertEquals(loadFilter2.getTargetTableName(), loadFiltersCache.get(nodeGroupLink).get(loadFilter2.getLoadFilterType()).get(loadFilter2
                .getTargetTableName()).get(1).getTargetTableName());
        loadFiltersForTable.remove(loadFilter);
        when(loadFilterService.findLoadFiltersFromDb()).thenReturn(loadFilters);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_LOAD_FILTER_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        loadFiltersCache = cacheManager.findLoadFilters(nodeGroupLink, false);
        assertEquals(1, loadFiltersCache.get(nodeGroupLink).get(loadFilter2.getLoadFilterType()).get(loadFilter2.getTargetTableName()).size());
        assertEquals(loadFilter2.getTargetTableName(), loadFiltersCache.get(nodeGroupLink).get(loadFilter2.getLoadFilterType()).get(loadFilter2
                .getTargetTableName()).get(0).getTargetTableName());
    }

    @Test
    public void transformCacheTest() {
        NodeGroupLink nodeGroupLink1 = new NodeGroupLink("source1", "target");
        TransformTableNodeGroupLink transformTable1 = new TransformTableNodeGroupLink();
        transformTable1.setTransformId("transform1");
        transformTable1.setSourceTableName("t1");
        transformTable1.setTransformPoint(TransformPoint.EXTRACT);
        transformTable1.setNodeGroupLink(nodeGroupLink1);
        List<TransformTableNodeGroupLink> byTableName = new ArrayList<TransformTableNodeGroupLink>();
        byTableName.add(transformTable1);
        Map<TransformPoint, List<TransformTableNodeGroupLink>> byTransformPoint = new HashMap<TransformPoint, List<TransformTableNodeGroupLink>>();
        byTransformPoint.put(transformTable1.getTransformPoint(), byTableName);
        Map<NodeGroupLink, Map<TransformPoint, List<TransformTableNodeGroupLink>>> transformsCacheByNodeGroupLinkByTransformPoint = new HashMap<NodeGroupLink, Map<TransformPoint, List<TransformTableNodeGroupLink>>>();
        transformsCacheByNodeGroupLinkByTransformPoint.put(nodeGroupLink1, byTransformPoint);
        when(transformService.readInCacheIfExpiredFromDb()).thenReturn(transformsCacheByNodeGroupLinkByTransformPoint);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRANSFORM_IN_MS)).thenReturn(600000l);
        CacheManager cacheManager = new CacheManager(engine);
        Map<NodeGroupLink, Map<TransformPoint, List<TransformTableNodeGroupLink>>> cache = cacheManager.getTransformCache();
        assertEquals(1, cache.get(nodeGroupLink1).get(transformTable1.getTransformPoint()).size());
        TransformTableNodeGroupLink transformTable2 = new TransformTableNodeGroupLink();
        transformTable2.setTransformId("transform2");
        transformTable2.setSourceTableName("t1");
        transformTable2.setTransformPoint(TransformPoint.EXTRACT);
        transformTable2.setNodeGroupLink(nodeGroupLink1);
        byTableName.add(transformTable2);
        when(transformService.readInCacheIfExpiredFromDb()).thenReturn(transformsCacheByNodeGroupLinkByTransformPoint);
        cacheManager.flushTransformCache();
        cache = cacheManager.getTransformCache();
        assertEquals(2, cache.get(nodeGroupLink1).get(transformTable1.getTransformPoint()).size());
        byTableName.remove(transformTable1);
        when(transformService.readInCacheIfExpiredFromDb()).thenReturn(transformsCacheByNodeGroupLinkByTransformPoint);
        when(parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_TRANSFORM_IN_MS)).thenReturn(5l);
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        cache = cacheManager.getTransformCache();
        assertEquals(1, cache.get(nodeGroupLink1).get(transformTable1.getTransformPoint()).size());
        assertEquals(transformTable2.getTransformId(), cache.get(nodeGroupLink1).get(transformTable1.getTransformPoint()).get(0).getTransformId());
    }
}
