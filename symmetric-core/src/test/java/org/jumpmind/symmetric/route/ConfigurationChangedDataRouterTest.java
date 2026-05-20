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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.NetworkedNode;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.Test;

public class ConfigurationChangedDataRouterTest {
    private static List<NodeGroupLink> THREE_TIER_LINKS;
    private static NetworkedNode THREE_TIER_NETWORKED_ROOT;
    private static List<NodeGroupLink> MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS;
    private static NetworkedNode MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT;
    static {
        THREE_TIER_LINKS = new ArrayList<NodeGroupLink>();
        THREE_TIER_LINKS.add(new NodeGroupLink("corp", "region"));
        THREE_TIER_LINKS.add(new NodeGroupLink("region", "corp"));
        THREE_TIER_LINKS.add(new NodeGroupLink("region", "laptop"));
        THREE_TIER_LINKS.add(new NodeGroupLink("laptop", "region"));
        THREE_TIER_NETWORKED_ROOT = new NetworkedNode(new Node("corp", "corp"));
        Node rgn1 = new Node("rgn1", "region");
        rgn1.setCreatedAtNodeId("corp");
        THREE_TIER_NETWORKED_ROOT.addChild(new NetworkedNode(rgn1));
        Node rgn2 = new Node("rgn2", "region");
        rgn2.setCreatedAtNodeId("corp");
        THREE_TIER_NETWORKED_ROOT.addChild(new NetworkedNode(rgn2));
        Node laptop1 = new Node("laptop1", "laptop");
        laptop1.setCreatedAtNodeId("rgn1");
        THREE_TIER_NETWORKED_ROOT.findNetworkedNode(laptop1.getCreatedAtNodeId()).addChild(
                new NetworkedNode(laptop1));
        Node laptop2 = new Node("laptop2", "laptop");
        laptop2.setCreatedAtNodeId("rgn2");
        THREE_TIER_NETWORKED_ROOT.findNetworkedNode(laptop2.getCreatedAtNodeId()).addChild(
                new NetworkedNode(laptop2));
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS = new ArrayList<NodeGroupLink>();
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS.add(new NodeGroupLink("regsvr", "s1"));
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS.add(new NodeGroupLink("regsvr", "s2"));
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS.add(new NodeGroupLink("regsvr", "dw"));
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS.add(new NodeGroupLink("s1", "regsvr"));
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS.add(new NodeGroupLink("s2", "regsvr"));
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS.add(new NodeGroupLink("dw", "regsvr"));
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS.add(new NodeGroupLink("s1", "dw"));
        MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS.add(new NodeGroupLink("s2", "dw"));
        MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT = new NetworkedNode(new Node("regsvr", "regsvr"));
        Node node = null;
        node = new Node("s1", "s1");
        node.setCreatedAtNodeId("regsvr");
        MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.addChild(new NetworkedNode(node));
        node = new Node("s2", "s2");
        node.setCreatedAtNodeId("regsvr");
        MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.addChild(new NetworkedNode(node));
        node = new Node("dw", "dw");
        node.setCreatedAtNodeId("regsvr");
        MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.addChild(new NetworkedNode(node));
    }

    @Test
    public void testRouteHeartbeatToParent() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("laptop1").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn1").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "laptop1"), nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertEquals("rgn1", nodeIds.iterator().next());
    }

    @Test
    public void testRouteLaptop1FromRgn1() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("laptop1").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "laptop1"), nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertEquals("laptop1", nodeIds.iterator().next());
    }

    @Test
    public void testRouteRgn2FromCorp() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn1").getNode());
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn2").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "rgn2"), nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertEquals("rgn2", nodeIds.iterator().next());
    }

    @Test
    public void testConfigurationExtract() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "corp"), nodes, true, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertEquals("corp", nodeIds.iterator().next());
    }

    @Test
    public void testRouteRgn1FromCorp() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn1").getNode());
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn2").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "rgn1"), nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertEquals("rgn1", nodeIds.iterator().next());
    }

    @Test
    public void testRouteLaptop1FromCorp() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn1").getNode());
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn2").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "laptop1"), nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertEquals("rgn1", nodeIds.iterator().next());
    }

    @Test
    public void testRouteS1ToDWFromRegsvr() {
        IDataRouter router = buildTestableRouter(
                MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("regsvr").getNode(), MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS,
                MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("s1").getNode());
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("s2").getNode());
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("dw").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "s1"), nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(2, nodeIds.size());
        assertTrue(nodeIds.contains("s1"));
        assertTrue(nodeIds.contains("dw"));
    }

    @Test
    public void testRouteDWToS1andS2FromRegsvr() {
        IDataRouter router = buildTestableRouter(
                MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("regsvr").getNode(), MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS,
                MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("s1").getNode());
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("s2").getNode());
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("dw").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "dw"), nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(3, nodeIds.size());
        assertTrue(nodeIds.contains("s1"));
        assertTrue(nodeIds.contains("s2"));
        assertTrue(nodeIds.contains("dw"));
    }

    @Test
    public void testRouteS1toRegsvrFromS1() {
        IDataRouter router = buildTestableRouter(
                MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("s1").getNode(), MULTIPLE_GROUPS_PLUS_REG_SVR_LINKS,
                MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("s1").getNode());
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("dw").getNode());
        nodes.add(MULTIPLE_GROUPS_PLUS_REG_SVR_NETWORKED_ROOT.findNetworkedNode("regsvr").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(), buildDataMetaData("SYM_NODE", "s1"), nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertTrue(nodeIds.contains("regsvr"));
    }

    @Test
    void testRouteTableReloadRequest_routesToSourceAndTargetNodes() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn1").getNode());
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn2").getNode());
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("laptop1").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildReloadRequestDataMetaData(
                        TableConstants.getTableName("sym", TableConstants.SYM_TABLE_RELOAD_REQUEST), "rgn1", "laptop1"),
                nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(2, nodeIds.size());
        assertTrue(nodeIds.contains("rgn1"));
        assertTrue(nodeIds.contains("laptop1"));
    }

    @Test
    void testRouteTableReloadRequest_emptyPossibleNodes_routesToNone() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildReloadRequestDataMetaData(
                        TableConstants.getTableName("sym", TableConstants.SYM_TABLE_RELOAD_REQUEST), "rgn1", "laptop1"),
                nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(0, nodeIds.size());
    }

    @Test
    void testRouteTableReloadRequest_routesToSourceNodeOnly() {
        IDataRouter router = buildTestableRouter(
                THREE_TIER_NETWORKED_ROOT.findNetworkedNode("corp").getNode(), THREE_TIER_LINKS,
                THREE_TIER_NETWORKED_ROOT);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn1").getNode());
        nodes.add(THREE_TIER_NETWORKED_ROOT.findNetworkedNode("rgn2").getNode());
        Collection<String> nodeIds = router.routeToNodes(new SimpleRouterContext(),
                buildReloadRequestDataMetaData(
                        TableConstants.getTableName("sym", TableConstants.SYM_TABLE_RELOAD_REQUEST), "rgn1", null),
                nodes, false, false, null);
        assertNotNull(nodeIds);
        assertEquals(1, nodeIds.size());
        assertTrue(nodeIds.contains("rgn1"));
    }

    protected DataMetaData buildDataMetaData(String tableName, String nodeId) {
        Data data = new Data();
        data.setTableName(tableName);
        data.setDataEventType(DataEventType.UPDATE);
        data.setTriggerHistory(new TriggerHistory(tableName, "NODE_ID", "NODE_ID"));
        data.setPkData(nodeId);
        data.setRowData(nodeId);
        return new DataMetaData(data, new Table(tableName), null, null);
    }

    protected DataMetaData buildReloadRequestDataMetaData(String tableName, String sourceNodeId, String targetNodeId) {
        Data data = new Data();
        data.setTableName(tableName);
        data.setDataEventType(DataEventType.UPDATE);
        data.setTriggerHistory(new TriggerHistory(tableName, "SOURCE_NODE_ID", "SOURCE_NODE_ID,TARGET_NODE_ID"));
        String targetId = targetNodeId != null ? targetNodeId : "";
        data.setPkData(sourceNodeId);
        data.setRowData(sourceNodeId + "," + targetId);
        return new DataMetaData(data, new Table(tableName), null, null);
    }

    protected IDataRouter buildTestableRouter(final Node nodeThatIsRouting,
            final List<NodeGroupLink> links, final NetworkedNode root) {
        IConfigurationService configService = mock(IConfigurationService.class);
        IParameterService paramService = mock(IParameterService.class);
        when(configService.isMasterToMaster()).thenReturn(false);
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getConfigurationService()).thenReturn(configService);
        when(engine.getParameterService()).thenReturn(paramService);
        when(engine.getTablePrefix()).thenReturn("sym");
        when(paramService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)).thenReturn(true);
        when(paramService.is(ParameterConstants.AUTO_SYNC_TRIGGERS_AFTER_CONFIG_CHANGED)).thenReturn(true);
        ConfigurationChangedDataRouter router = new ConfigurationChangedDataRouter(engine) {
            @Override
            protected Node findIdentity() {
                return nodeThatIsRouting;
            }

            @Override
            protected List<NodeGroupLink> getNodeGroupLinksFromContext(SimpleRouterContext routingContext) {
                return links;
            }

            @Override
            protected NetworkedNode getRootNetworkNodeFromContext(SimpleRouterContext routingContext) {
                return root;
            }
        };
        return router;
    }
}
