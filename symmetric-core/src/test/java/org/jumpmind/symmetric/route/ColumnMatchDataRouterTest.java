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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.INodeService;
import org.junit.jupiter.api.Test;

public class ColumnMatchDataRouterTest {
    //
    @Test
    public void testExpressionEqualsNodeId() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("100", "client"));
        nodes.add(new Node("200", "client"));
        nodes.add(new Node("300", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("100", "client")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,NODE_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,100,Super Dooper");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("NODE_ID = :NODE_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(1, result.size());
        assertEquals(true, result.contains("100"));
    }

    @Test
    public void testExpressionNotEqualsNodeId() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("100", "client"));
        nodes.add(new Node("200", "client"));
        nodes.add(new Node("300", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("100", "client")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,NODE_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,100,Super Dooper");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("NODE_ID != :NODE_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(2, result.size());
        assertEquals(true, result.contains("200"));
        assertEquals(true, result.contains("300"));
    }

    @Test
    public void testExpressionEqualsExternalId() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("1000", "client"));
        nodes.add(new Node("100", "client"));
        nodes.add(new Node("10", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("100", "client")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,STORE_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,100,Super Dooper");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("STORE_ID = :EXTERNAL_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(1, result.size());
        assertEquals(true, result.contains("100"));
    }

    @Test
    public void testExpressionEqualsGroupId() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("10", "server"));
        nodes.add(new Node("20", "server"));
        nodes.add(new Node("3", "client"));
        nodes.add(new Node("2", "client"));
        nodes.add(new Node("1", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("10", "server")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,GROUP_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,client,Super Dooper");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("GROUP_ID = :NODE_GROUP_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(3, result.size());
        assertEquals(true, result.contains("1"));
        assertEquals(true, result.contains("2"));
        assertEquals(true, result.contains("3"));
    }

    @Test
    public void testExpressionEqualsNull() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("100", "client"));
        nodes.add(new Node("200", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("100", "client")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,NODE_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,100,");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("COLUMN2 = NULL");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(2, result.size());
        assertEquals(true, result.contains("100"));
        assertEquals(true, result.contains("200"));
    }

    @Test
    public void testExpressionNotEqualsNull() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("100", "client"));
        nodes.add(new Node("200", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("100", "client")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,NODE_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,100,");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("COLUMN2 != NULL");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(0, result.size());
    }

    @Test
    public void testExpressionExternalData() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("100", "client"));
        nodes.add(new Node("200", "client"));
        nodes.add(new Node("300", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("100", "client")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,NODE_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,100,Super Dooper");
        data.setExternalData("100");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("EXTERNAL_DATA = :NODE_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(1, result.size());
        assertEquals(true, result.contains("100"));
    }

    @Test
    public void testExpressionContains() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("100", "client"));
        nodes.add(new Node("200", "client"));
        nodes.add(new Node("300", "client"));
        nodes.add(new Node("1000", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("100", "client")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,NODE_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,100,Super Dooper");
        data.setExternalData("1000,200");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("EXTERNAL_DATA contains :NODE_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(2, result.size());
        assertEquals(true, result.contains("1000"));
        assertEquals(true, result.contains("200"));
    }

    @Test
    public void testExpressionNotContains() {
        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(new Node("100", "client"));
        nodes.add(new Node("200", "client"));
        nodes.add(new Node("300", "client"));
        nodes.add(new Node("1000", "client"));
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("100", "client")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,NODE_ID,COLUMN2");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,100,Super Dooper");
        data.setExternalData("1000,200");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression("EXTERNAL_DATA not contains :NODE_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(2, result.size());
        assertEquals(true, result.contains("100"));
        assertEquals(true, result.contains("300"));
    }

    @Test
    void testExpressionSourceSchemaEqualsExternalId() {
        HashSet<Node> nodes = new HashSet<Node>();
        Node node1 = new Node("1", "client");
        node1.setExternalId("warehouse");
        nodes.add(node1);
        Node node2 = new Node("2", "client");
        node2.setExternalId("retail");
        nodes.add(node2);
        Node node3 = new Node("3", "client");
        node3.setExternalId("office");
        nodes.add(node3);
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("0", "server")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,COLUMN2");
        triggerHist.setSourceSchemaName("warehouse");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,Super Dooper");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression(":SOURCE_SCHEMA = :EXTERNAL_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(1, result.size());
        assertEquals(true, result.contains("1"));
    }

    @Test
    void testExpressionSourceSchemaNotEquals() {
        HashSet<Node> nodes = new HashSet<Node>();
        Node node1 = new Node("1", "client");
        node1.setExternalId("warehouse");
        nodes.add(node1);
        Node node2 = new Node("2", "client");
        node2.setExternalId("retail");
        nodes.add(node2);
        Node node3 = new Node("3", "client");
        node3.setExternalId("office");
        nodes.add(node3);
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("0", "server")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,COLUMN2");
        triggerHist.setSourceSchemaName("warehouse");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,Super Dooper");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression(":SOURCE_SCHEMA != :EXTERNAL_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(2, result.size());
        assertEquals(true, result.contains("2"));
        assertEquals(true, result.contains("3"));
    }

    @Test
    void testExpressionSourceCatalogEqualsExternalId() {
        HashSet<Node> nodes = new HashSet<Node>();
        Node node1 = new Node("1", "client");
        node1.setExternalId("proddb");
        nodes.add(node1);
        Node node2 = new Node("2", "client");
        node2.setExternalId("devdb");
        nodes.add(node2);
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        doReturn(new Node("0", "server")).when(nodeService).findIdentity(true, true);
        ColumnMatchDataRouter router = new ColumnMatchDataRouter(engine);
        SimpleRouterContext routingContext = new SimpleRouterContext();
        TriggerHistory triggerHist = new TriggerHistory("mytable", "ID", "ID,COLUMN2");
        triggerHist.setSourceCatalogName("proddb");
        Data data = new Data();
        data.setDataId(1);
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,Super Dooper");
        data.setTriggerHistory(triggerHist);
        Table table = new Table();
        NodeChannel nodeChannel = new NodeChannel();
        Router route = new Router();
        route.setRouterExpression(":SOURCE_CATALOG = :EXTERNAL_ID");
        route.setRouterId("route1");
        DataMetaData dataMetaData = new DataMetaData(data, table, route, nodeChannel);
        Set<String> result = router.routeToNodes(routingContext, dataMetaData, nodes, false, false, null);
        assertEquals(1, result.size());
        assertEquals(true, result.contains("1"));
    }
}
