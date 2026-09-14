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
package org.jumpmind.symmetric.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.MockNodeService;
import org.jumpmind.symmetric.service.impl.MockParameterService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DefaultNodeIdCreatorTest {
    @Test
    public void testSelectNodeId() throws Exception {
        final String EXPECTED_NODE_ID = "100-2";
        DefaultNodeIdCreator generator = new DefaultNodeIdCreator(new MockParameterService(), new MockNodeService() {
            @Override
            public NodeSecurity findNodeSecurity(String nodeId) {
                if (nodeId.equals(EXPECTED_NODE_ID)) {
                    NodeSecurity security = new NodeSecurity();
                    security.setNodeId(EXPECTED_NODE_ID);
                    security.setRegistrationEnabled(true);
                    return security;
                } else {
                    return null;
                }
            }
        }, SecurityServiceFactory.create());
        Node node = new Node();
        node.setExternalId("100");
        String selectedNodeId = generator.selectNodeId(node, null, null);
        assertEquals(EXPECTED_NODE_ID, selectedNodeId);
    }

    @Test
    public void testSelectNodeIdWithNodeIdSet() throws Exception {
        DefaultNodeIdCreator generator = new DefaultNodeIdCreator(new MockParameterService(), new MockNodeService(), SecurityServiceFactory.create());
        Node node = new Node();
        final String EXPECTED_NODE_ID = "10001";
        node.setExternalId(EXPECTED_NODE_ID);
        node.setNodeId(EXPECTED_NODE_ID);
        String selectedNodeId = generator.selectNodeId(node, null, null);
        assertEquals(EXPECTED_NODE_ID, selectedNodeId);
    }

    @Test
    public void testGenerateNodeIdNoExisting() throws Exception {
        final String EXPECTED_NODE_ID = "100";
        DefaultNodeIdCreator generator = new DefaultNodeIdCreator(new MockParameterService(), new MockNodeService(), SecurityServiceFactory.create());
        Node node = new Node();
        node.setExternalId("100");
        String selectedNodeId = generator.generateNodeId(node, null, null);
        assertEquals(EXPECTED_NODE_ID, selectedNodeId);
    }

    @Test
    public void testGenerateNodeIdExisting() throws Exception {
        final String EXPECTED_NODE_ID = "100";
        DefaultNodeIdCreator generator = new DefaultNodeIdCreator(new MockParameterService(
                ParameterConstants.EXTERNAL_ID_IS_UNIQUE, "true"), new MockNodeService() {
                    @Override
                    public Node findNode(String nodeId) {
                        if (nodeId.equals("100")) {
                            Node node = new Node();
                            node.setNodeId("100");
                            return node;
                        } else {
                            return null;
                        }
                    }
                }, SecurityServiceFactory.create());
        Node node = new Node();
        node.setExternalId("100");
        String selectedNodeId = generator.generateNodeId(node, null, null);
        assertEquals(EXPECTED_NODE_ID, selectedNodeId);
    }

    @Test
    public void testGenerateNodeIdExistingWithUniqueParam() throws Exception {
        final String EXPECTED_NODE_ID = "100-0";
        IParameterService paramService = mock(IParameterService.class);
        Mockito.when(paramService.is(ParameterConstants.EXTERNAL_ID_IS_UNIQUE)).thenReturn(false);
        Mockito.when(paramService.getInt(ParameterConstants.NODE_ID_CREATOR_MAX_NODES, 100)).thenReturn(100);
        DefaultNodeIdCreator generator = new DefaultNodeIdCreator(paramService, new MockNodeService() {
            @Override
            public Node findNode(String nodeId) {
                if (nodeId.equals("100")) {
                    Node node = new Node();
                    node.setNodeId(nodeId);
                    return node;
                } else {
                    return null;
                }
            }

            public NodeSecurity findNodeSecurity(String nodeId) {
                if (nodeId.equals("100")) {
                    NodeSecurity nodeSecurity = new NodeSecurity();
                    nodeSecurity.setNodeId(nodeId);
                    nodeSecurity.setRegistrationEnabled(false);
                    return nodeSecurity;
                } else {
                    return null;
                }
            }
        }, SecurityServiceFactory.create());
        Node node = new Node();
        node.setExternalId("100");
        String selectedNodeId = generator.generateNodeId(node, null, null);
        assertEquals(EXPECTED_NODE_ID, selectedNodeId);
    }

    @Test
    public void testGenerateNodeIdWithMultipleExistingWithUniqueParam() throws Exception {
        final String EXPECTED_NODE_ID = "100-5";
        IParameterService paramService = mock(IParameterService.class);
        Mockito.when(paramService.is(ParameterConstants.EXTERNAL_ID_IS_UNIQUE)).thenReturn(false);
        Mockito.when(paramService.getInt(ParameterConstants.NODE_ID_CREATOR_MAX_NODES, 100)).thenReturn(100);
        DefaultNodeIdCreator generator = new DefaultNodeIdCreator(paramService, new MockNodeService() {
            @Override
            public Node findNode(String nodeId) {
                if (nodeId != null && !nodeId.equals("100-5")) {
                    Node node = new Node();
                    node.setNodeId(nodeId);
                    return node;
                } else {
                    return null;
                }
            }

            public NodeSecurity findNodeSecurity(String nodeId) {
                if (nodeId != null && !nodeId.equals("100-5")) {
                    NodeSecurity nodeSecurity = new NodeSecurity();
                    nodeSecurity.setNodeId(nodeId);
                    nodeSecurity.setRegistrationEnabled(false);
                    return nodeSecurity;
                } else {
                    return null;
                }
            }
        }, SecurityServiceFactory.create());
        Node node = new Node();
        node.setExternalId("100");
        String selectedNodeId = generator.generateNodeId(node, null, null);
        assertEquals(EXPECTED_NODE_ID, selectedNodeId);
    }

    @Test
    public void testGenerateNodeIdExistingAll() throws Exception {
        IParameterService paramService = mock(IParameterService.class);
        Mockito.when(paramService.is(ParameterConstants.EXTERNAL_ID_IS_UNIQUE)).thenReturn(false);
        Mockito.when(paramService.getInt(ParameterConstants.NODE_ID_CREATOR_MAX_NODES, 100)).thenReturn(100);
        DefaultNodeIdCreator generator = new DefaultNodeIdCreator(paramService, new MockNodeService() {
            @Override
            public Node findNode(String nodeId) {
                Node node = new Node();
                node.setNodeId(nodeId);
                return node;
            }

            public NodeSecurity findNodeSecurity(String nodeId) {
                NodeSecurity nodeSecurity = new NodeSecurity();
                nodeSecurity.setNodeId(nodeId);
                nodeSecurity.setRegistrationEnabled(false);
                return nodeSecurity;
            }
        }, SecurityServiceFactory.create());
        Node node = new Node();
        node.setExternalId("100");
        try {
            generator.generateNodeId(node, null, null);
            fail("Should have received exception");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testGenerateNodeIdWithNodeIdSet() throws Exception {
        DefaultNodeIdCreator generator = new DefaultNodeIdCreator(new MockParameterService(), new MockNodeService(), SecurityServiceFactory.create());
        Node node = new Node();
        final String EXPECTED_NODE_ID = "10001";
        node.setExternalId(EXPECTED_NODE_ID);
        node.setNodeId(EXPECTED_NODE_ID);
        String selectedNodeId = generator.generateNodeId(node, null, null);
        assertEquals(EXPECTED_NODE_ID, selectedNodeId);
    }
}