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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Date;
import java.util.List;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ChannelDataCreateTimeRange;
import org.jumpmind.symmetric.model.ChannelDataUnroutedCount;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.ChannelRouterContext;
import org.jumpmind.symmetric.route.DataGapDetector;
import org.jumpmind.symmetric.service.IConfigurationService;
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
    IConfigurationService configurationService;
    INodeService nodeService;
    IGroupletService groupletService;

    @BeforeEach
    public void setup() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        configurationService = mock(IConfigurationService.class);
        nodeService = mock(INodeService.class);
        groupletService = mock(IGroupletService.class);
        when(databasePlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        when(engine.getDatabasePlatform()).thenReturn(databasePlatform);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getGroupletService()).thenReturn(groupletService);
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
    public void testShouldSkipSqlEventWhenSyncSqlDisabled() {
        NodeGroupLink link = new NodeGroupLink(SOURCE_NODE_GROUP, TARGET_NODE_GROUP);
        link.setSyncSqlEnabled(false);
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false)).thenReturn(link);
        Data data = new Data();
        data.setDataEventType(DataEventType.SQL);
        Router router = new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default");
        assertTrue(routerService.shouldSkipSqlEvent(data, router));
    }

    @Test
    public void testShouldNotSkipSqlEventWhenSyncSqlEnabled() {
        NodeGroupLink link = new NodeGroupLink(SOURCE_NODE_GROUP, TARGET_NODE_GROUP);
        link.setSyncSqlEnabled(true);
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false)).thenReturn(link);
        Data data = new Data();
        data.setDataEventType(DataEventType.SQL);
        Router router = new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default");
        assertFalse(routerService.shouldSkipSqlEvent(data, router));
    }

    @Test
    public void testShouldNotSkipNonSqlEventWhenSyncSqlDisabled() {
        NodeGroupLink link = new NodeGroupLink(SOURCE_NODE_GROUP, TARGET_NODE_GROUP);
        link.setSyncSqlEnabled(false);
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false)).thenReturn(link);
        Data data = new Data();
        data.setDataEventType(DataEventType.INSERT);
        Router router = new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default");
        assertFalse(routerService.shouldSkipSqlEvent(data, router));
    }

    @Test
    public void testShouldNotSkipSqlEventWhenLinkNotFound() {
        when(configurationService.getNodeGroupLinkFor(SOURCE_NODE_GROUP, TARGET_NODE_GROUP, false)).thenReturn(null);
        Data data = new Data();
        data.setDataEventType(DataEventType.SQL);
        Router router = new Router("test", SOURCE_NODE_GROUP, TARGET_NODE_GROUP, "default");
        assertFalse(routerService.shouldSkipSqlEvent(data, router));
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

    private RouterService newRouterServiceForGapQuery(IParameterService parameterService, int maxGapsToQualify, int maxGapsBeforeGreaterThanQuery) {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        when(databasePlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(databasePlatform.scrubSql(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(databasePlatform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getInt(ParameterConstants.ROUTING_MAX_GAPS_TO_QUALIFY_IN_SQL, 100)).thenReturn(maxGapsToQualify);
        when(parameterService.getInt(ParameterConstants.ROUTING_DATA_READER_THRESHOLD_GAPS_TO_USE_GREATER_QUERY, 100))
                .thenReturn(maxGapsBeforeGreaterThanQuery);
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        when(engine.getDatabasePlatform()).thenReturn(databasePlatform);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        RouterService testRouterService = new RouterService(engine);
        testRouterService.setSqlMap(new RouterServiceSqlMap(databasePlatform,
                Collections.singletonMap("data", TableConstants.getTableName("sym", TableConstants.SYM_DATA))));
        return testRouterService;
    }

    @Test
    void testBuildGapQualifiedQueryUsesGapRangeSqlWhenGapCountAtOrBelowThreshold() {
        RouterService testRouterService = newRouterServiceForGapQuery(mock(IParameterService.class), 100, 100);
        List<DataGap> gaps = Arrays.asList(new DataGap(1, 10), new DataGap(20, 30));
        RouterService.GapQualifiedQuery query = testRouterService.buildGapQualifiedQuery(gaps,
                "selectChannelDataCreateTimeRangeUsingGapsSql", "selectChannelDataCreateTimeRangeUsingStartDataId");
        assertTrue(query.sql().contains("group by channel_id"));
        assertTrue(query.sql().contains("data_id between ? and ?"));
        assertEquals(4, query.args().length);
        assertEquals(1L, query.args()[0]);
        assertEquals(10L, query.args()[1]);
        assertEquals(20L, query.args()[2]);
        assertEquals(30L, query.args()[3]);
    }

    @Test
    void testBuildGapQualifiedQueryUsesStartIdSqlWhenGapCountExceedsThreshold() {
        RouterService testRouterService = newRouterServiceForGapQuery(mock(IParameterService.class), 100, 1);
        List<DataGap> gaps = Arrays.asList(new DataGap(1, 10), new DataGap(20, 30));
        RouterService.GapQualifiedQuery query = testRouterService.buildGapQualifiedQuery(gaps,
                "selectChannelDataCreateTimeRangeUsingGapsSql", "selectChannelDataCreateTimeRangeUsingStartDataId");
        assertTrue(query.sql().contains("data_id >= ?"));
        assertEquals(1, query.args().length);
        assertEquals(1L, query.args()[0]);
    }

    @SuppressWarnings("unchecked")
    private static void stubQueryToInvokeMapper(ISqlTemplate sqlTemplate, List<Row> rows) {
        when(sqlTemplate.query(any(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class))).thenAnswer(invocation -> {
            ISqlRowMapper<Object> mapper = invocation.getArgument(1);
            List<Object> results = new ArrayList<>();
            for (Row row : rows) {
                results.add(mapper.mapRow(row));
            }
            return results;
        });
    }

    @Test
    void testGetReadyChannelsMapsChannelIdsFromQueryResults() {
        RouterService testRouterService = newRouterServiceForGapQuery(mock(IParameterService.class), 100, 100);
        testRouterService.gapDetector = mock(DataGapDetector.class);
        when(testRouterService.gapDetector.getDataGaps()).thenReturn(Arrays.asList(new DataGap(1, 10)));
        stubQueryToInvokeMapper(testRouterService.sqlTemplateDirty, Arrays.asList(
                new Row("channel_id", "chan1"),
                new Row("channel_id", "chan2")));
        Collection<String> readyChannels = testRouterService.getReadyChannels();
        assertTrue(readyChannels.contains("chan1"));
        assertTrue(readyChannels.contains("chan2"));
    }

    @Test
    void testFindUnroutedDataCreateTimeRangeByChannelWithGapsMapsChannelDataCreateTimeRanges() {
        RouterService testRouterService = newRouterServiceForGapQuery(mock(IParameterService.class), 100, 100);
        testRouterService.gapDetector = mock(DataGapDetector.class);
        when(testRouterService.gapDetector.getDataGaps()).thenReturn(Arrays.asList(new DataGap(1, 10)));
        Date minTime = new Date(1000L);
        Date maxTime = new Date(2000L);
        Row row = new Row(3);
        row.put("channel_id", "chan1");
        row.put("min_create_time", minTime);
        row.put("max_create_time", maxTime);
        stubQueryToInvokeMapper(testRouterService.sqlTemplateDirty, Arrays.asList(row));
        List<ChannelDataCreateTimeRange> ranges = testRouterService.findUnroutedDataCreateTimeRangeByChannel();
        assertEquals(1, ranges.size());
        assertEquals("chan1", ranges.get(0).channelId());
        assertEquals(minTime, ranges.get(0).minCreateTime());
        assertEquals(maxTime, ranges.get(0).maxCreateTime());
    }

    @Test
    void testFindUnroutedDataCreateTimeRangeByChannelReturnsEmptyListWhenGapsNotYetDetected() {
        assertEquals(Collections.emptyList(), routerService.findUnroutedDataCreateTimeRangeByChannel());
    }

    @SuppressWarnings("unchecked")
    private static void stubNoArgQueryToInvokeMapper(ISqlTemplate sqlTemplate, List<Row> rows) {
        when(sqlTemplate.query(any(), any(ISqlRowMapper.class))).thenAnswer(invocation -> {
            ISqlRowMapper<Object> mapper = invocation.getArgument(1);
            List<Object> results = new ArrayList<>();
            for (Row row : rows) {
                results.add(mapper.mapRow(row));
            }
            return results;
        });
    }

    @Test
    void testFindUnroutedDataCountByChannelMapsChannelDataUnroutedCounts() {
        RouterService testRouterService = newRouterServiceForGapQuery(mock(IParameterService.class), 100, 100);
        Row row = new Row(2);
        row.put("channel_id", "chan1");
        row.put("unrouted_count", 5L);
        stubNoArgQueryToInvokeMapper(testRouterService.sqlTemplateDirty, Arrays.asList(row));
        List<ChannelDataUnroutedCount> counts = testRouterService.findUnroutedDataCountByChannel();
        assertEquals(1, counts.size());
        assertEquals("chan1", counts.get(0).channelId());
        assertEquals(5L, counts.get(0).count());
    }

    @Test
    void testFindUnroutedDataCountByChannelIgnoresGapDetectorState() {
        RouterService testRouterService = newRouterServiceForGapQuery(mock(IParameterService.class), 100, 100);
        testRouterService.gapDetector = mock(DataGapDetector.class);
        when(testRouterService.gapDetector.getDataGaps()).thenReturn(Collections.emptyList());
        Row row = new Row(2);
        row.put("channel_id", "chan1");
        row.put("unrouted_count", 5L);
        stubNoArgQueryToInvokeMapper(testRouterService.sqlTemplateDirty, Arrays.asList(row));
        List<ChannelDataUnroutedCount> counts = testRouterService.findUnroutedDataCountByChannel();
        assertEquals(1, counts.size());
        assertEquals("chan1", counts.get(0).channelId());
    }
}
