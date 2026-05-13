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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.Test;

class RegistrationServerRouterTest {
    @Test
    void testRouteToNodes_serverSourceReturnsEmpty() {
        Node identity = node("server", "server");
        IDataRouter router = buildRouter(identity, true);
        Set<Node> targets = new HashSet<Node>();
        targets.add(node("client1", "client"));
        targets.add(node("client2", "client"));
        Set<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildDataMetaData(), targets, false, false, null);
        assertNotNull(nodeIds);
        assertTrue(nodeIds.isEmpty(), "registration server should not route anywhere");
    }

    @Test
    void testRouteToNodes_clientRoutesToRegistrationServerOnly() {
        Node identity = node("client1", "client");
        identity.setCreatedAtNodeId("server");
        IDataRouter router = buildRouter(identity, false);
        Set<Node> targets = new HashSet<Node>();
        targets.add(node("server", "server"));
        targets.add(node("client2", "client"));
        Set<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildDataMetaData(), targets, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertEquals("server", nodeIds.iterator().next());
    }

    @Test
    void testRouteToNodes_clientWhenRegistrationServerNotInTargetsReturnsEmpty() {
        Node identity = node("laptop1", "laptop");
        identity.setCreatedAtNodeId("server");
        IDataRouter router = buildRouter(identity, false);
        Set<Node> targets = new HashSet<Node>();
        targets.add(node("rgn1", "region"));
        targets.add(node("rgn2", "region"));
        Set<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildDataMetaData(), targets, false, false, null);
        assertNotNull(nodeIds);
        assertTrue(nodeIds.isEmpty());
    }

    @Test
    void testRouteToNodes_nullIdentityReturnsEmpty() {
        IDataRouter router = buildRouter(null, false);
        Set<Node> targets = new HashSet<Node>();
        targets.add(node("server", "server"));
        Set<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildDataMetaData(), targets, false, false, null);
        assertNotNull(nodeIds);
        assertTrue(nodeIds.isEmpty());
    }

    @Test
    void testRouteToNodes_nullCreatedAtNodeIdReturnsEmpty() {
        Node identity = node("client1", "client");
        // createdAtNodeId left null
        IDataRouter router = buildRouter(identity, false);
        Set<Node> targets = new HashSet<Node>();
        targets.add(node("server", "server"));
        Set<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildDataMetaData(), targets, false, false, null);
        assertNotNull(nodeIds);
        assertTrue(nodeIds.isEmpty());
    }

    @Test
    void testRouteToNodes_selfReferentialCreatedAtNodeIdReturnsEmpty() {
        Node identity = node("server", "server");
        identity.setCreatedAtNodeId("server");
        IDataRouter router = buildRouter(identity, false);
        Set<Node> targets = new HashSet<Node>();
        targets.add(node("client1", "client"));
        Set<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildDataMetaData(), targets, false, false, null);
        assertNotNull(nodeIds);
        assertTrue(nodeIds.isEmpty());
    }

    @Test
    void testHasSomewhereToRoute_falseWhenRegistrationServer() {
        RegistrationServerRouter router = (RegistrationServerRouter) buildRouter(node("server", "server"), true);
        Node identity = node("server", "server");
        identity.setCreatedAtNodeId(null);
        assertFalse(router.hasSomewhereToRoute(identity));
    }

    @Test
    void testHasSomewhereToRoute_falseWhenIdentityNull() {
        RegistrationServerRouter router = (RegistrationServerRouter) buildRouter(null, false);
        assertFalse(router.hasSomewhereToRoute(null));
    }

    @Test
    void testHasSomewhereToRoute_falseWhenCreatedAtNodeIdNull() {
        RegistrationServerRouter router = (RegistrationServerRouter) buildRouter(null, false);
        Node identity = node("client1", "client");
        // createdAtNodeId left null
        assertFalse(router.hasSomewhereToRoute(identity));
    }

    @Test
    void testHasSomewhereToRoute_trueWhenClientWithRegistrationParent() {
        RegistrationServerRouter router = (RegistrationServerRouter) buildRouter(null, false);
        Node identity = node("client1", "client");
        identity.setCreatedAtNodeId("server");
        assertTrue(router.hasSomewhereToRoute(identity));
    }

    @Test
    void testHasSomewhereToRoute_falseWhenCreatedAtNodeIdEqualsNodeId() {
        RegistrationServerRouter router = (RegistrationServerRouter) buildRouter(null, false);
        Node identity = node("server", "server");
        identity.setCreatedAtNodeId("server");
        assertFalse(router.hasSomewhereToRoute(identity));
    }

    @Test
    void testFindRegistrationNode_returnsSingletonWhenFound() {
        RegistrationServerRouter router = (RegistrationServerRouter) buildRouter(null, false);
        Node identity = node("client1", "client");
        identity.setCreatedAtNodeId("server");
        Set<Node> targets = new HashSet<Node>();
        targets.add(node("server", "server"));
        targets.add(node("client2", "client"));
        Set<String> result = router.findRegistrationNode(identity, targets);
        assertEquals(1, result.size());
        assertEquals("server", result.iterator().next());
    }

    @Test
    void testFindRegistrationNode_returnsEmptyWhenNotInTargets() {
        RegistrationServerRouter router = (RegistrationServerRouter) buildRouter(null, false);
        Node identity = node("client1", "client");
        identity.setCreatedAtNodeId("server");
        Set<Node> targets = new HashSet<Node>();
        targets.add(node("client2", "client"));
        targets.add(node("client3", "client"));
        Set<String> result = router.findRegistrationNode(identity, targets);
        assertTrue(result.isEmpty());
    }

    @Test
    void testIsRegistrationServer_delegatesToParameterService() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService paramService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(paramService);
        RegistrationServerRouter router = new RegistrationServerRouter(engine);
        when(paramService.isRegistrationServer()).thenReturn(true);
        assertTrue(router.isRegistrationServer());
        when(paramService.isRegistrationServer()).thenReturn(false);
        assertFalse(router.isRegistrationServer());
    }

    @Test
    void testFindIdentity_delegatesToNodeService() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        Node expected = node("client1", "client");
        when(engine.getNodeService()).thenReturn(nodeService);
        when(nodeService.findIdentity()).thenReturn(expected);
        RegistrationServerRouter router = new RegistrationServerRouter(engine);
        assertSame(expected, router.findIdentity());
    }

    @Test
    void testIsConfigurable() {
        assertFalse(new RegistrationServerRouter(mock(ISymmetricEngine.class)).isConfigurable());
    }

    @Test
    void testIsDmlOnly() {
        assertFalse(new RegistrationServerRouter(mock(ISymmetricEngine.class)).isDmlOnly());
    }

    private Node node(String nodeId, String groupId) {
        return new Node(nodeId, groupId);
    }

    private DataMetaData buildDataMetaData() {
        Data data = new Data();
        data.setTableName("sym_monitor_event");
        data.setDataEventType(DataEventType.INSERT);
        data.setTriggerHistory(new TriggerHistory("sym_monitor_event", "MONITOR_ID,NODE_ID,EVENT_TIME",
                "MONITOR_ID,NODE_ID,EVENT_TIME"));
        return new DataMetaData(data, new Table("sym_monitor_event"), null, null);
    }

    private IDataRouter buildRouter(final Node identity, final boolean isRegistrationServer) {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        return new RegistrationServerRouter(engine) {
            @Override
            protected Node findIdentity() {
                return identity;
            }

            @Override
            protected boolean isRegistrationServer() {
                return isRegistrationServer;
            }
        };
    }
}
