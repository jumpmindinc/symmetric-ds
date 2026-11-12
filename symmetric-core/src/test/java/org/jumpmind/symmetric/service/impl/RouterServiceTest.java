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
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.ChannelRouterContext;
import org.jumpmind.symmetric.route.IDataRouter;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
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
    private static final Channel OTHER_CHANNEL = new Channel("other", 2);
    private static final long DEFAULT_ROUTER_CACHE_TIME_MS = 600000;
    private static final long DATA_ID = 7;
    private static final String PK_DATA = "\"7\"";
    private static final String ROW_DATA = "\"7\",\"SHIV\"";
    private static final String OLD_DATA = "\"7\",\"OLD\"";
    RouterService routerService;
    IConfigurationService configurationService;
    IExtensionService extensionService;
    INodeService nodeService;
    IGroupletService groupletService;
    private IParameterService parameterService;
    private IDataService dataService;

    @BeforeEach
    public void setup() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        dataService = mock(IDataService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        extensionService = mock(IExtensionService.class);
        configurationService = mock(IConfigurationService.class);
        nodeService = mock(INodeService.class);
        groupletService = mock(IGroupletService.class);
        when(databasePlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getLong(ParameterConstants.CACHE_CHANNEL_DEFAULT_ROUTER_IN_MS)).thenReturn(DEFAULT_ROUTER_CACHE_TIME_MS);
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        when(engine.getDatabasePlatform()).thenReturn(databasePlatform);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getDataService()).thenReturn(dataService);
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false))
                .thenReturn(new NodeGroupLink(SOURCE_NODE_GROUP, TARGET_NODE_GROUP));
        when(groupletService.getTargetEnabled(any(TriggerRouter.class), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(groupletService.isTargetEnabled(any(TriggerRouter.class), any(Node.class))).thenReturn(true);
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
    void testOnlyDefaultRoutersAssigned_whenCacheTimeExpired() {
        when(parameterService.getLong(ParameterConstants.CACHE_CHANNEL_DEFAULT_ROUTER_IN_MS)).thenReturn(-1L);
        List<TriggerRouter> triggerRouters = newTriggerRouters(newTriggerRouter(CHANNEL_2_TEST, "default"));
        routerService.onlyDefaultRoutersAssigned(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters);
        triggerRouters.add(newTriggerRouter(CHANNEL_2_TEST, "column"));
        assertFalse(routerService.onlyDefaultRoutersAssigned(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    void testFlushCache_recalculatesEveryChannel() {
        List<TriggerRouter> triggerRouters = newTriggerRouters(newTriggerRouter(CHANNEL_2_TEST, "default"), newTriggerRouter(OTHER_CHANNEL, "default"));
        routerService.onlyDefaultRoutersAssigned(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters);
        routerService.onlyDefaultRoutersAssigned(OTHER_CHANNEL, SOURCE_NODE_GROUP, triggerRouters);
        triggerRouters.add(newTriggerRouter(CHANNEL_2_TEST, "column"));
        routerService.flushCache();
        routerService.onlyDefaultRoutersAssigned(OTHER_CHANNEL, SOURCE_NODE_GROUP, triggerRouters);
        assertFalse(routerService.onlyDefaultRoutersAssigned(CHANNEL_2_TEST, SOURCE_NODE_GROUP, triggerRouters));
    }

    @Test
    void testReloadRoutingColumnsIfSkipped_withColumnRouter() {
        Data data = newDataWithoutValues();
        when(dataService.findData(DATA_ID)).thenReturn(newData(PK_DATA, ROW_DATA, OLD_DATA));
        routerService.reloadRoutingColumnsIfSkipped(data, newTriggerRouters(newTriggerRouter(CHANNEL_2_TEST, "column")), newDefaultRoutersOnlyContext());
        assertEquals(ROW_DATA, data.getRowData());
        assertEquals(OLD_DATA, data.getOldData());
        assertEquals(PK_DATA, data.getPkData());
    }

    @Test
    void testReloadRoutingColumnsIfSkipped_keepsDataTheChannelDoesNotRouteOn() {
        Data data = newDataWithoutValues();
        when(dataService.findData(DATA_ID)).thenReturn(newData(PK_DATA, ROW_DATA, OLD_DATA));
        NodeChannel channel = new NodeChannel(new Channel(CHANNEL_2_TEST.getChannelId(), 1));
        channel.getChannel().setUseOldDataToRoute(false);
        ChannelRouterContext context = newContext(channel);
        context.setOnlyDefaultRoutersAssigned(true);
        routerService.reloadRoutingColumnsIfSkipped(data, newTriggerRouters(newTriggerRouter(CHANNEL_2_TEST, "column")), context);
        assertEquals(ROW_DATA, data.getRowData());
        assertEquals("", data.getOldData());
    }

    @Test
    void testReloadRoutingColumnsIfSkipped_whenChannelRoutesOnNoData() {
        Data data = newDataWithoutValues();
        NodeChannel channel = new NodeChannel(new Channel(CHANNEL_2_TEST.getChannelId(), 1));
        channel.getChannel().setUseRowDataToRoute(false);
        channel.getChannel().setUseOldDataToRoute(false);
        channel.getChannel().setUsePkDataToRoute(false);
        ChannelRouterContext context = newContext(channel);
        context.setOnlyDefaultRoutersAssigned(true);
        routerService.reloadRoutingColumnsIfSkipped(data, newTriggerRouters(newTriggerRouter(CHANNEL_2_TEST, "column")), context);
        verify(dataService, never()).findData(DATA_ID);
    }

    @Test
    void testReloadRoutingColumnsIfSkipped_withNodeList() {
        Data data = newDataWithoutValues();
        data.setNodeList(TARGET_NODE_ID);
        routerService.reloadRoutingColumnsIfSkipped(data, newTriggerRouters(newTriggerRouter(CHANNEL_2_TEST, "column")), newDefaultRoutersOnlyContext());
        verify(dataService, never()).findData(DATA_ID);
    }

    @Test
    void testReloadRoutingColumnsIfSkipped_withMissingRouter() {
        Data data = newDataWithoutValues();
        TriggerRouter triggerRouterWithoutRouter = new TriggerRouter(new Trigger("a", CHANNEL_2_TEST.getChannelId()), null);
        routerService.reloadRoutingColumnsIfSkipped(data, newTriggerRouters(triggerRouterWithoutRouter), newDefaultRoutersOnlyContext());
        verify(dataService, never()).findData(DATA_ID);
    }

    @Test
    void testReloadRoutingColumnsIfSkipped_withDefaultRouter() {
        Data data = newDataWithoutValues();
        routerService.reloadRoutingColumnsIfSkipped(data, newTriggerRouters(newTriggerRouter(CHANNEL_2_TEST, "default")), newDefaultRoutersOnlyContext());
        verify(dataService, never()).findData(DATA_ID);
    }

    @Test
    void testReloadRoutingColumnsIfSkipped_whenDataWasSelected() {
        Data data = newDataWithoutValues();
        routerService.reloadRoutingColumnsIfSkipped(data, newTriggerRouters(newTriggerRouter(CHANNEL_2_TEST, "column")), newContext());
        verify(dataService, never()).findData(DATA_ID);
    }

    @Test
    void testReloadRoutingColumnsIfSkipped_withNoTriggerRouters() {
        Data data = newDataWithoutValues();
        routerService.reloadRoutingColumnsIfSkipped(data, null, newDefaultRoutersOnlyContext());
        verify(dataService, never()).findData(DATA_ID);
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
        assertTrue(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter()));
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeBelongsToAnotherGroup() {
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, OTHER_NODE_GROUP));
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter()));
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeDoesNotExist() {
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(null);
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter()));
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeIsSyncDisabled() {
        Node disabledNode = new Node(TARGET_NODE_ID, TARGET_NODE_GROUP);
        disabledNode.setSyncEnabled(false);
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(disabledNode);
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter()));
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenRouterHasNoNodeGroupLink() {
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false)).thenReturn(null);
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, TARGET_NODE_GROUP));
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter()));
        verify(nodeService, never()).findNode(TARGET_NODE_ID);
    }

    @Test
    public void testIsNodeCacheRefreshNotNeededWhenMissingNodeIsExcludedByGrouplet() {
        when(nodeService.findNode(TARGET_NODE_ID)).thenReturn(new Node(TARGET_NODE_ID, TARGET_NODE_GROUP));
        when(groupletService.isTargetEnabled(any(TriggerRouter.class), any(Node.class))).thenReturn(false);
        assertFalse(routerService.isNodeCacheRefreshNeeded(Arrays.asList(TARGET_NODE_ID), newTriggerRouter()));
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

    private TriggerRouter newTriggerRouter(Channel channel, String routerType) {
        return new TriggerRouter(new Trigger("a", channel.getChannelId()), new Router(routerType, SOURCE_NODE_GROUP, TARGET_NODE_GROUP, routerType));
    }

    private List<TriggerRouter> newTriggerRouters(TriggerRouter... triggerRouters) {
        return new ArrayList<TriggerRouter>(Arrays.asList(triggerRouters));
    }

    private Data newDataWithoutValues() {
        return newData("", "", "");
    }

    private Data newData(String pkData, String rowData, String oldData) {
        Data data = new Data();
        data.setDataId(DATA_ID);
        data.setPkData(pkData);
        data.setRowData(rowData);
        data.setOldData(oldData);
        return data;
    }

    private ChannelRouterContext newDefaultRoutersOnlyContext() {
        ChannelRouterContext context = newContext();
        context.setOnlyDefaultRoutersAssigned(true);
        return context;
    }

    private Data newDataForNodeList() {
        Data data = new Data();
        data.setTableName("a");
        data.setNodeList(TARGET_NODE_ID);
        return data;
    }

    private ChannelRouterContext newContext() {
        return newContext(new NodeChannel(CHANNEL_2_TEST));
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
