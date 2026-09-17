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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractDataRouterTest {
    private PassThroughRouter router;
    private Set<Node> nodes;

    @BeforeEach
    void setUp() {
        router = new PassThroughRouter();
        nodes = new LinkedHashSet<>(Arrays.asList(newNode("store-1", "001"), newNode("store-2", "002")));
    }

    @Test
    void testToNodeIds_collectsEveryNodeId() {
        assertEquals(new HashSet<>(Arrays.asList("store-1", "store-2")), router.toNodeIds(nodes, null));
    }

    @Test
    void testToNodeIds_addsToExistingSet() {
        Set<String> existing = new HashSet<>(Arrays.asList("corp"));
        assertEquals(new HashSet<>(Arrays.asList("corp", "store-1", "store-2")), router.toNodeIds(nodes, existing));
    }

    @Test
    void testToNodeIds_withNoNodes() {
        assertTrue(router.toNodeIds(new HashSet<Node>(), null).isEmpty());
    }

    @Test
    void testToExternalIds_collectsEveryExternalId() {
        assertEquals(new HashSet<>(Arrays.asList("001", "002")), router.toExternalIds(nodes));
    }

    @Test
    void testToExternalIds_withNoNodes() {
        assertTrue(router.toExternalIds(new HashSet<Node>()).isEmpty());
    }

    @Test
    void testAddNodeId_addsKnownNode() {
        assertEquals(new HashSet<>(Arrays.asList("store-1")), router.addNodeId("store-1", null, nodes));
    }

    @Test
    void testAddNodeId_ignoresUnknownNode() {
        assertTrue(router.addNodeId("store-99", null, nodes).isEmpty());
    }

    @Test
    void testAddNodeId_keepsExistingEntries() {
        Set<String> existing = new HashSet<>(Arrays.asList("corp"));
        assertEquals(new HashSet<>(Arrays.asList("corp", "store-2")), router.addNodeId("store-2", existing, nodes));
    }

    @Test
    void testIsConfigurable_isTrueByDefault() {
        assertTrue(router.isConfigurable());
    }

    @Test
    void testIsDmlOnly_isTrueByDefault() {
        assertTrue(router.isDmlOnly());
    }

    @Test
    void testDefaultDataRouter_routesToEveryNode() {
        DefaultDataRouter defaultRouter = new DefaultDataRouter();
        assertEquals(new HashSet<>(Arrays.asList("store-1", "store-2")),
                defaultRouter.routeToNodes(new SimpleRouterContext(), null, nodes, false, false, null));
    }

    @Test
    void testDefaultDataRouter_isNotDmlOnly() {
        assertFalse(new DefaultDataRouter().isDmlOnly());
    }

    @Test
    void testDefaultDataRouter_withNoNodes() {
        assertTrue(new DefaultDataRouter().routeToNodes(new SimpleRouterContext(), null, new HashSet<Node>(), false, false, null).isEmpty());
    }

    private Node newNode(String nodeId, String externalId) {
        Node node = new Node(nodeId, "store");
        node.setExternalId(externalId);
        return node;
    }

    private static class PassThroughRouter extends AbstractDataRouter {
        @Override
        public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData, Set<Node> nodes,
                boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
            return toNodeIds(nodes, null);
        }
    }
}
