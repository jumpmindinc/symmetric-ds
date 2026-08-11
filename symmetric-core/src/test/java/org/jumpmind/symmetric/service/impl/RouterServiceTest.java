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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
<<<<<<< HEAD
=======
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroupLink;
>>>>>>> 890b59bc80 (SYM-7891: Fix slow Routing job when node cache misses, nodes list is empty (#988))
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.ChannelRouterContext;
import org.jumpmind.symmetric.route.IDataRouter;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IGroupletService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RouterServiceTest {
    final static Channel CHANNEL_2_TEST = new Channel("test", 1);
    final static String SOURCE_NODE_GROUP = "source";
    final static String TARGET_NODE_GROUP = "target";
    final static String OTHER_NODE_GROUP = "other";
    final static String TARGET_NODE_ID = "node1";
    RouterService routerService;
    IExtensionService extensionService;
    INodeService nodeService;
    IGroupletService groupletService;

    @BeforeEach
    public void setup() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        extensionService = mock(IExtensionService.class);
<<<<<<< HEAD
=======
        configurationService = mock(IConfigurationService.class);
        nodeService = mock(INodeService.class);
        groupletService = mock(IGroupletService.class);
>>>>>>> 890b59bc80 (SYM-7891: Fix slow Routing job when node cache misses, nodes list is empty (#988))
        when(databasePlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        when(engine.getDatabasePlatform()).thenReturn(databasePlatform);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
<<<<<<< HEAD
=======
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false))
                .thenReturn(new NodeGroupLink(SOURCE_NODE_GROUP, TARGET_NODE_GROUP));
        when(groupletService.getTargetEnabled(any(TriggerRouter.class), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(groupletService.isTargetEnabled(any(TriggerRouter.class), any(Node.class))).thenReturn(true);
>>>>>>> 890b59bc80 (SYM-7891: Fix slow Routing job when node cache misses, nodes list is empty (#988))
        routerService = new RouterService(engine);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testProducesCommonBatchesOneTableOneChannelDefaultRouter() {
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        triggerRouters.add(new TriggerRouter(new Trigger("a", CHANNEL_2_TEST.getChannelId()), new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP,
                "default")));
        assertTrue(routerService.producesCommonBatches(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNotProducesCommonBatchesOneTableOneChannelNonDefaultRouter() {
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        triggerRouters.add(new TriggerRouter(new Trigger("a", CHANNEL_2_TEST.getChannelId()), new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP,
                "column")));
        assertTrue(!routerService.producesCommonBatches(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testProducesCommonBatchesMultipleTablesTwoChannelsMultipleRouters() {
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        triggerRouters.add(new TriggerRouter(new Trigger("a", CHANNEL_2_TEST.getChannelId()), new Router("test1", SOURCE_NODE_GROUP, TARGET_NODE_GROUP,
                "default")));
        triggerRouters.add(new TriggerRouter(new Trigger("b", "anotherchannel"), new Router("test2", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "column")));
        assertTrue(routerService.producesCommonBatches(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testProducesCommonBatchesMultipleTablesTwoChannelsMultipleRoutersBidirectional() {
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        triggerRouters.add(new TriggerRouter(new Trigger("a", CHANNEL_2_TEST.getChannelId()), new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP,
                "default")));
        triggerRouters.add(new TriggerRouter(new Trigger("a", CHANNEL_2_TEST.getChannelId()), new Router("test", TARGET_NODE_GROUP, SOURCE_NODE_GROUP,
                "default")));
        assertTrue(routerService.producesCommonBatches(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNotProducesCommonBatchesMultipleTablesTwoChannelsMultipleRoutersSyncOnIncoming() {
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        Trigger tableTrigger = new Trigger("a", CHANNEL_2_TEST.getChannelId(), true);
        triggerRouters.add(new TriggerRouter(tableTrigger, new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default")));
        triggerRouters.add(new TriggerRouter(tableTrigger, new Router("test", TARGET_NODE_GROUP, SOURCE_NODE_GROUP, "default")));
        assertTrue(!routerService.producesCommonBatches(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNotProducesCommonBatchesSameTablesTwoChannelsMultipleRoutersSameTableIncomingOnAnotherChannel() {
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        Trigger tableTrigger1 = new Trigger("a", CHANNEL_2_TEST.getChannelId(), true);
        Trigger tableTrigger2 = new Trigger("a", "anotherchannel");
        triggerRouters.add(new TriggerRouter(tableTrigger1, new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default")));
        triggerRouters.add(new TriggerRouter(tableTrigger2, new Router("test", TARGET_NODE_GROUP, SOURCE_NODE_GROUP, "default")));
        assertTrue(!routerService.producesCommonBatches(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testProducesCommonBatchesSameTablesTwoChannelsMultipleRoutersDifferentTableIncomingOnAnotherChannel() {
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        Trigger tableTrigger1 = new Trigger("a", CHANNEL_2_TEST.getChannelId(), true);
        Trigger tableTrigger2 = new Trigger("b", "anotherchannel");
        Trigger tableTrigger3 = new Trigger("c", CHANNEL_2_TEST.getChannelId());
        triggerRouters.add(new TriggerRouter(tableTrigger1, new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default")));
        triggerRouters.add(new TriggerRouter(tableTrigger2, new Router("test", TARGET_NODE_GROUP, SOURCE_NODE_GROUP, "default")));
        triggerRouters.add(new TriggerRouter(tableTrigger3, new Router("test", TARGET_NODE_GROUP, SOURCE_NODE_GROUP, "default")));
        assertTrue(routerService.producesCommonBatches(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    public void testGetDataRouterSkipsRoutingForUnsupportedColumnSegmentRouterType() {
        IDataRouter defaultRouter = mock(IDataRouter.class);
        Map<String, IDataRouter> routers = new HashMap<String, IDataRouter>();
        routers.put("default", defaultRouter);
        when(extensionService.getExtensionPointMap(IDataRouter.class)).thenReturn(routers);
        Router router = new Router("segmentRouter", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, RouterService.COLUMN_SEGMENT_ROUTER_TYPE);
        IDataRouter result = routerService.getDataRouter(router, null);
        assertNotEquals(defaultRouter, result);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("node1", SOURCE_NODE_GROUP));
        assertTrue(result.routeToNodes(null, null, nodes, false, false, null).isEmpty());
        assertEquals(1, routerService.unsupportedColumnSegmentRouterType.get("segmentRouter").getCount());
        assertFalse(routerService.invalidRouterType.containsKey("segmentRouter"));
    }

    @Test
    public void testGetDataRouterFallsBackToDefaultForOtherInvalidRouterType() {
        IDataRouter defaultRouter = mock(IDataRouter.class);
        Map<String, IDataRouter> routers = new HashMap<String, IDataRouter>();
        routers.put("default", defaultRouter);
        when(extensionService.getExtensionPointMap(IDataRouter.class)).thenReturn(routers);
        Router router = new Router("bogusRouter", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "bogus");
        IDataRouter result = routerService.getDataRouter(router, null);
        assertEquals(defaultRouter, result);
        assertEquals(1, routerService.invalidRouterType.get("bogusRouter").getCount());
        assertFalse(routerService.unsupportedColumnSegmentRouterType.containsKey("bogusRouter"));
    }

    @Test
    public void testIsNodeCacheRefreshNeededWhenMissingNodeIsEnabledMemberOfTargetGroup() {
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, TARGET_NODE_GROUP));
        assertTrue(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter(), newChannel()));
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeBelongsToAnotherGroup() {
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, OTHER_NODE_GROUP));
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter(), newChannel()));
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeDoesNotExist() {
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(null);
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter(), newChannel()));
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeIsSyncDisabled() {
        Node disabledNode = new Node(TARGET_NODE_ID, TARGET_NODE_GROUP);
        disabledNode.setSyncEnabled(false);
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(disabledNode);
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter(), newChannel()));
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeIsIgnoredOnChannel() {
        NodeChannel channel = newChannel();
        channel.setIgnoreEnabled(TARGET_NODE_ID, true);
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, TARGET_NODE_GROUP));
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter(), channel));
        verify(nodeService, never()).findNode(TARGET_NODE_ID);
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenRouterHasNoNodeGroupLink() {
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false)).thenReturn(null);
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, TARGET_NODE_GROUP));
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter(), newChannel()));
        verify(nodeService, never()).findNode(TARGET_NODE_ID);
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeIsExcludedByGrouplet() {
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, TARGET_NODE_GROUP));
        when(groupletService.isTargetEnabled(any(TriggerRouter.class), any(Node.class))).thenReturn(false);
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter(), newChannel()));
    }

    @Test
    public void testFindNodeIdsFromNodeListSkipsCacheRefreshWhenTargetNodeBelongsToAnotherGroup() {
        when(nodeService.findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP)).thenReturn(Collections.emptyList());
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, OTHER_NODE_GROUP));
        Collection<String> nodeIds = routerService.findNodeIdsFromNodeList(newDataForNodeList(), newTriggerRouter(), newContext());
        assertTrue(nodeIds.isEmpty());
        verify(nodeService, never()).flushNodeGroupCache();
        verify(nodeService, times(1)).findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP);
    }

    @Test
    public void testFindNodeIdsFromNodeListRefreshesCacheWhenTargetNodeIsMissingFromStaleCache() {
        Node targetNode = new Node(TARGET_NODE_ID, TARGET_NODE_GROUP);
        when(nodeService.findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP)).thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(targetNode));
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(targetNode);
        Collection<String> nodeIds = routerService.findNodeIdsFromNodeList(newDataForNodeList(), newTriggerRouter(), newContext());
        assertEquals(Arrays.asList(TARGET_NODE_ID), new ArrayList<String>(nodeIds));
        verify(nodeService, times(1)).flushNodeGroupCache();
        verify(nodeService, times(2)).findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP);
    }

    @Test
    public void testFindNodeIdsFromNodeListSkipsCacheRefreshWhenAllTargetNodesResolve() {
        when(nodeService.findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP))
                .thenReturn(Collections.singletonList(new Node(TARGET_NODE_ID, TARGET_NODE_GROUP)));
        Collection<String> nodeIds = routerService.findNodeIdsFromNodeList(newDataForNodeList(), newTriggerRouter(), newContext());
        assertEquals(Arrays.asList(TARGET_NODE_ID), new ArrayList<String>(nodeIds));
        verify(nodeService, never()).flushNodeGroupCache();
        verify(nodeService, never()).findNode(TARGET_NODE_ID);
    }

    @Test
    public void testFindNodeIdsFromNodeListSkipsCacheRefreshWhenTargetNodeIsIgnoredOnChannel() {
        Node targetNode = new Node(TARGET_NODE_ID, TARGET_NODE_GROUP);
        NodeChannel channel = newChannel();
        channel.setIgnoreEnabled(TARGET_NODE_ID, true);
        when(nodeService.findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP)).thenReturn(Collections.singletonList(targetNode));
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(targetNode);
        Collection<String> nodeIds = routerService.findNodeIdsFromNodeList(newDataForNodeList(), newTriggerRouter(), newContext(channel));
        assertTrue(nodeIds.isEmpty());
        verify(nodeService, never()).flushNodeGroupCache();
        verify(nodeService, times(1)).findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP);
    }

    @Test
    public void testFindNodeIdsFromNodeListSkipsCacheRefreshWhenRouterHasNoNodeGroupLink() {
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false)).thenReturn(null);
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, TARGET_NODE_GROUP));
        Collection<String> nodeIds = routerService.findNodeIdsFromNodeList(newDataForNodeList(), newTriggerRouter(), newContext());
        assertTrue(nodeIds.isEmpty());
        verify(nodeService, never()).flushNodeGroupCache();
        verify(nodeService, never()).findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP);
    }

    @Test
    public void testFindNodeIdsFromNodeListSkipsCacheRefreshWhenTargetNodeIsExcludedByGrouplet() {
        Node targetNode = new Node(TARGET_NODE_ID, TARGET_NODE_GROUP);
        when(groupletService.getTargetEnabled(any(TriggerRouter.class), any())).thenReturn(Collections.emptySet());
        when(groupletService.isTargetEnabled(any(TriggerRouter.class), any(Node.class))).thenReturn(false);
        when(nodeService.findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP)).thenReturn(Collections.singletonList(targetNode));
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(targetNode);
        Collection<String> nodeIds = routerService.findNodeIdsFromNodeList(newDataForNodeList(), newTriggerRouter(), newContext());
        assertTrue(nodeIds.isEmpty());
        verify(nodeService, never()).flushNodeGroupCache();
        verify(nodeService, times(1)).findEnabledNodesFromNodeGroup(TARGET_NODE_GROUP);
    }

    @SuppressWarnings("deprecation")
    private TriggerRouter newTriggerRouter() {
        return new TriggerRouter(new Trigger("a", CHANNEL_2_TEST.getChannelId()),
                new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default"));
    }

    private Data newDataForNodeList() {
        Data data = new Data();
        data.setTableName("a");
        data.setNodeList(TARGET_NODE_ID);
        return data;
    }

    private NodeChannel newChannel() {
        return new NodeChannel(CHANNEL_2_TEST);
    }

    private ChannelRouterContext newContext() {
        return newContext(newChannel());
    }

    private ChannelRouterContext newContext(NodeChannel channel) {
        return new ChannelRouterContext(SOURCE_NODE_GROUP, channel, mock(ISqlTransaction.class), null);
    }

    @Test
    public void testGetDataRouterUsesRegisteredRouterForKnownType() {
        IDataRouter defaultRouter = mock(IDataRouter.class);
        Map<String, IDataRouter> routers = new HashMap<String, IDataRouter>();
        routers.put("default", defaultRouter);
        when(extensionService.getExtensionPointMap(IDataRouter.class)).thenReturn(routers);
        Router router = new Router("defaultRouter", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default");
        IDataRouter result = routerService.getDataRouter(router, null);
        assertEquals(defaultRouter, result);
        assertTrue(routerService.invalidRouterType.isEmpty());
        assertTrue(routerService.unsupportedColumnSegmentRouterType.isEmpty());
    }
}
