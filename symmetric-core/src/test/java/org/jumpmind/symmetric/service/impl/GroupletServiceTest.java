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
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Grouplet;
import org.jumpmind.symmetric.model.Grouplet.GroupletLinkPolicy;
import org.jumpmind.symmetric.model.GroupletLink;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.model.TriggerRouterGrouplet;
import org.jumpmind.symmetric.model.TriggerRouterGrouplet.AppliesWhen;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupletServiceTest {
    private static final String TRIGGER_ID = "item_trigger";
    private static final String ROUTER_ID = "corp_2_store";
    private IParameterService parameterService;
    private INodeService nodeService;
    private ICacheManager cacheManager;
    private ISqlTemplate sqlTemplate;
    private TriggerRouter triggerRouter;
    private GroupletService groupletService;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        nodeService = mock(INodeService.class);
        cacheManager = mock(ICacheManager.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        triggerRouter = newTriggerRouter();
        groupletService = new GroupletService(newMockedEngine());
    }

    @Test
    void testGetGrouplets_withGroupletsDisabled() {
        assertTrue(groupletService.getGrouplets(false).isEmpty());
        verify(cacheManager, never()).getGrouplets(false);
    }

    @Test
    void testGetGrouplets_readsThroughTheCache() {
        enableGrouplets();
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B, "store-001");
        when(cacheManager.getGrouplets(false)).thenReturn(Collections.singletonList(grouplet));
        assertEquals(Collections.singletonList(grouplet), groupletService.getGrouplets(false));
    }

    @Test
    void testGetGroupletsFor_matchesOnTriggerRouterAndAppliesWhen() {
        enableGrouplets();
        stubGrouplets(newGrouplet(GroupletLinkPolicy.I, AppliesWhen.S, "store-001"));
        assertEquals(1, groupletService.getGroupletsFor(triggerRouter, AppliesWhen.S, false).size());
        assertTrue(groupletService.getGroupletsFor(triggerRouter, AppliesWhen.T, false).isEmpty());
    }

    @Test
    void testGetGroupletsFor_appliesWhenBothMatchesEitherDirection() {
        enableGrouplets();
        stubGrouplets(newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B, "store-001"));
        assertEquals(1, groupletService.getGroupletsFor(triggerRouter, AppliesWhen.S, false).size());
        assertEquals(1, groupletService.getGroupletsFor(triggerRouter, AppliesWhen.T, false).size());
    }

    @Test
    void testGetGroupletsFor_skipsOtherTriggerRouters() {
        enableGrouplets();
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.S, "store-001");
        grouplet.getTriggerRouterGrouplets().get(0).setTriggerId("other_trigger");
        stubGrouplets(grouplet);
        assertTrue(groupletService.getGroupletsFor(triggerRouter, AppliesWhen.S, false).isEmpty());
    }

    @Test
    void testIsSourceEnabled_withGroupletsDisabled() {
        assertTrue(groupletService.isSourceEnabled(triggerRouter));
    }

    @Test
    void testIsSourceEnabled_withoutANodeIdentity() {
        enableGrouplets();
        assertFalse(groupletService.isSourceEnabled(triggerRouter));
    }

    @Test
    void testIsSourceEnabled_withoutAnyMatchingGrouplet() {
        enableGrouplets();
        stubIdentity("store-001");
        stubGrouplets();
        assertTrue(groupletService.isSourceEnabled(triggerRouter));
    }

    @Test
    void testIsSourceEnabled_withAnIncludePolicyThatMatchesTheNode() {
        enableGrouplets();
        stubIdentity("store-001");
        stubGrouplets(newGrouplet(GroupletLinkPolicy.I, AppliesWhen.S, "store-001"));
        assertTrue(groupletService.isSourceEnabled(triggerRouter));
    }

    @Test
    void testIsSourceEnabled_withAnIncludePolicyThatDoesNotMatchTheNode() {
        enableGrouplets();
        stubIdentity("store-002");
        stubGrouplets(newGrouplet(GroupletLinkPolicy.I, AppliesWhen.S, "store-001"));
        assertFalse(groupletService.isSourceEnabled(triggerRouter));
    }

    @Test
    void testIsSourceEnabled_withAnExcludePolicyThatMatchesTheNode() {
        enableGrouplets();
        stubIdentity("store-001");
        stubGrouplets(newGrouplet(GroupletLinkPolicy.E, AppliesWhen.S, "store-001"));
        assertFalse(groupletService.isSourceEnabled(triggerRouter));
    }

    @Test
    void testIsSourceEnabled_withAnExcludePolicyThatDoesNotMatchTheNode() {
        enableGrouplets();
        stubIdentity("store-002");
        stubGrouplets(newGrouplet(GroupletLinkPolicy.E, AppliesWhen.S, "store-001"));
        assertTrue(groupletService.isSourceEnabled(triggerRouter));
    }

    @Test
    void testIsTargetEnabled_withGroupletsDisabled() {
        assertTrue(groupletService.isTargetEnabled(triggerRouter, newNode("store-001")));
    }

    @Test
    void testIsTargetEnabled_withAnIncludePolicyThatMatchesTheNode() {
        enableGrouplets();
        stubGrouplets(newGrouplet(GroupletLinkPolicy.I, AppliesWhen.T, "store-001"));
        assertTrue(groupletService.isTargetEnabled(triggerRouter, newNode("store-001")));
        assertFalse(groupletService.isTargetEnabled(triggerRouter, newNode("store-002")));
    }

    @Test
    void testGetTargetEnabled_withoutAnyGroupletsReturnsEveryNode() {
        enableGrouplets();
        stubGrouplets();
        Set<Node> nodes = newNodes("store-001", "store-002");
        assertEquals(nodes, groupletService.getTargetEnabled(triggerRouter, nodes));
    }

    @Test
    void testGetTargetEnabled_withAnIncludePolicyKeepsOnlyTheLinkedNodes() {
        enableGrouplets();
        stubGrouplets(newGrouplet(GroupletLinkPolicy.I, AppliesWhen.T, "store-001"));
        assertEquals(newNodes("store-001"), groupletService.getTargetEnabled(triggerRouter, newNodes("store-001", "store-002")));
    }

    @Test
    void testGetTargetEnabled_withAnExcludePolicyDropsOnlyTheLinkedNodes() {
        enableGrouplets();
        stubGrouplets(newGrouplet(GroupletLinkPolicy.E, AppliesWhen.T, "store-001"));
        assertEquals(newNodes("store-002"), groupletService.getTargetEnabled(triggerRouter, newNodes("store-001", "store-002")));
    }

    @Test
    void testGetTargetEnabled_whenANodeIsBothIncludedAndExcludedTheIncludeWins() {
        enableGrouplets();
        stubGrouplets(newGrouplet(GroupletLinkPolicy.I, AppliesWhen.T, "store-001"), newGrouplet(GroupletLinkPolicy.E, AppliesWhen.T, "store-001"));
        assertEquals(newNodes("store-001"), groupletService.getTargetEnabled(triggerRouter, newNodes("store-001", "store-002")));
    }

    @Test
    void testRefreshFromDatabase_withGroupletsDisabled() {
        assertFalse(groupletService.refreshFromDatabase());
    }

    @Test
    void testRefreshFromDatabase_withNoRowsInAnyGroupletTable() {
        enableGrouplets();
        assertFalse(groupletService.refreshFromDatabase());
    }

    @Test
    void testRefreshFromDatabase_takesTheLatestOfTheThreeUpdateTimes() {
        enableGrouplets();
        when(sqlTemplate.queryForObject(sqlFor("selectMaxGroupletLastUpdateTime"), Date.class)).thenReturn(Timestamp.valueOf("2023-11-14 17:13:20"));
        when(sqlTemplate.queryForObject(sqlFor("selectMaxGroupletLinkLastUpdateTime"), Date.class)).thenReturn(Timestamp.valueOf("2024-01-02 03:04:05"));
        assertTrue(groupletService.refreshFromDatabase());
        verify(cacheManager).flushGrouplets();
    }

    @Test
    void testRefreshFromDatabase_withAnUnchangedUpdateTime() {
        enableGrouplets();
        when(sqlTemplate.queryForObject(sqlFor("selectMaxGroupletLastUpdateTime"), Date.class)).thenReturn(Timestamp.valueOf("2024-01-02 03:04:05"));
        groupletService.refreshFromDatabase();
        assertFalse(groupletService.refreshFromDatabase());
    }

    @Test
    void testClearCache() {
        groupletService.clearCache();
        verify(cacheManager).flushGrouplets();
    }

    @Test
    void testDeleteAllGrouplets_clearsAllThreeTables() {
        groupletService.deleteAllGrouplets();
        verify(sqlTemplate).update(sqlFor("deleteAllGroupletLinksSql"));
        verify(sqlTemplate).update(sqlFor("deleteAllTriggerRouterGroupletsSql"));
        verify(sqlTemplate).update(sqlFor("deleteAllGroupletsSql"));
    }

    @Test
    void testDeleteGrouplet_cascadesToLinksAndTriggerRouters() {
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B, "store-001");
        groupletService.deleteGrouplet(grouplet);
        verify(sqlTemplate).update(eqSql("deleteGroupletLinkSql"), any(Object[].class), any(int[].class));
        verify(sqlTemplate).update(eqSql("deleteTriggerRouterGroupletSql"), any(Object[].class), any(int[].class));
        verify(sqlTemplate).update(eqSql("deleteGroupletSql"), any(Object[].class), any(int[].class));
    }

    @Test
    void testDeleteTriggerRouterGroupletsFor_aRouter() {
        Router router = new Router();
        router.setRouterId(ROUTER_ID);
        groupletService.deleteTriggerRouterGroupletsFor(router);
        verify(sqlTemplate).update(eqSql("deleteTriggerRouterGroupletsForRouterSql"), any(Object[].class), any(int[].class));
    }

    @Test
    void testDeleteTriggerRouterGroupletsFor_aTriggerRouter() {
        groupletService.deleteTriggerRouterGroupletsFor(triggerRouter);
        verify(sqlTemplate).update(eqSql("deleteTriggerRouterGroupletForSql"), any(Object[].class), any(int[].class));
    }

    @Test
    void testGroupletMapper_mapsEveryColumnAndIndexesTheGrouplet() {
        Map<String, Grouplet> groupletsById = new HashMap<String, Grouplet>();
        Row row = new Row(6);
        row.put("grouplet_id", "east-stores");
        row.put("description", "stores in the east");
        row.put("grouplet_link_policy", "I");
        row.put("create_time", Timestamp.valueOf("2023-11-14 17:13:20"));
        row.put("last_update_by", "system");
        row.put("last_update_time", Timestamp.valueOf("2024-01-02 03:04:05"));
        Grouplet grouplet = new GroupletService.GroupletMapper(groupletsById).mapRow(row);
        assertEquals("east-stores", grouplet.getGroupletId());
        assertEquals("stores in the east", grouplet.getDescription());
        assertEquals(GroupletLinkPolicy.I, grouplet.getGroupletLinkPolicy());
        assertEquals("system", grouplet.getLastUpdateBy());
        assertEquals(grouplet, groupletsById.get("east-stores"));
    }

    @Test
    void testGroupletMapper_withoutAnIndexToPopulate() {
        Row row = new Row(6);
        row.put("grouplet_id", "east-stores");
        row.put("description", null);
        row.put("grouplet_link_policy", "E");
        row.put("create_time", Timestamp.valueOf("2023-11-14 17:13:20"));
        row.put("last_update_by", "system");
        row.put("last_update_time", Timestamp.valueOf("2024-01-02 03:04:05"));
        assertEquals(GroupletLinkPolicy.E, new GroupletService.GroupletMapper(null).mapRow(row).getGroupletLinkPolicy());
    }

    private ISymmetricEngine newMockedEngine() {
        ISymmetricDialect symmetricDialect = newSymmetricDialect();
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        return engine;
    }

    private ISymmetricDialect newSymmetricDialect() {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(returnsFirstArg());
        return symmetricDialect;
    }

    private void enableGrouplets() {
        when(parameterService.is(ParameterConstants.GROUPLET_ENABLE)).thenReturn(true);
    }

    private void stubIdentity(String externalId) {
        when(nodeService.findIdentity()).thenReturn(newNode(externalId));
    }

    private void stubGrouplets(Grouplet... grouplets) {
        when(cacheManager.getGrouplets(false)).thenReturn(Arrays.asList(grouplets));
    }

    private Grouplet newGrouplet(GroupletLinkPolicy policy, AppliesWhen appliesWhen, String... externalIds) {
        Grouplet grouplet = new Grouplet();
        grouplet.setGroupletId("east-stores");
        grouplet.setGroupletLinkPolicy(policy);
        for (String externalId : externalIds) {
            GroupletLink link = new GroupletLink();
            link.setExternalId(externalId);
            grouplet.getGroupletLinks().add(link);
        }
        TriggerRouterGrouplet triggerRouterGrouplet = new TriggerRouterGrouplet();
        triggerRouterGrouplet.setTriggerId(TRIGGER_ID);
        triggerRouterGrouplet.setRouterId(ROUTER_ID);
        triggerRouterGrouplet.setAppliesWhen(appliesWhen);
        grouplet.getTriggerRouterGrouplets().add(triggerRouterGrouplet);
        return grouplet;
    }

    private TriggerRouter newTriggerRouter() {
        Trigger trigger = new Trigger();
        trigger.setTriggerId(TRIGGER_ID);
        Router router = new Router();
        router.setRouterId(ROUTER_ID);
        TriggerRouter newTriggerRouter = new TriggerRouter();
        newTriggerRouter.setTrigger(trigger);
        newTriggerRouter.setRouter(router);
        return newTriggerRouter;
    }

    private Node newNode(String externalId) {
        Node node = new Node(externalId, "store");
        node.setExternalId(externalId);
        return node;
    }

    private Set<Node> newNodes(String... externalIds) {
        Set<Node> nodes = new HashSet<Node>();
        for (String externalId : externalIds) {
            nodes.add(newNode(externalId));
        }
        return nodes;
    }

    private String eqSql(String key) {
        return eq(groupletService.getSql(key));
    }

    private String sqlFor(String key) {
        return groupletService.getSql(key);
    }
}
