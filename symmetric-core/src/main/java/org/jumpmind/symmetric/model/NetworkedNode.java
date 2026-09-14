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
package org.jumpmind.symmetric.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

public class NetworkedNode implements Comparable<NetworkedNode> {
    private Node node;
    private NetworkedNode parent;
    private TreeMap<String, NetworkedNode> children;
    private Map<String, NetworkedNode> allNetworkedNodes;

    public NetworkedNode(Node node) {
        this.node = node;
    }

    public Set<NetworkedNode> getChildren() {
        if (children != null) {
            return new TreeSet<NetworkedNode>(children.values());
        } else {
            return null;
        }
    }

    public void addChild(NetworkedNode node) {
        if (children == null) {
            children = new TreeMap<String, NetworkedNode>();
        }
        node.parent = this;
        children.put(node.getNode().getNodeId(), node);
    }

    public Node getNode() {
        return node;
    }

    public NetworkedNode getParent() {
        return parent;
    }

    public int getNumberOfLinksAwayFromRoot(String nodeId) {
        int numberOfLinksAwayFromRoot = getRoot().getNumberOfLinksAwayFromMe(nodeId, 0);
        if (numberOfLinksAwayFromRoot == 0 && !node.getNodeId().equals(nodeId)) {
            return -1;
        } else {
            return numberOfLinksAwayFromRoot;
        }
    }

    protected int getNumberOfLinksAwayFromMe(String nodeId, int numberOfLinksIAmFromRoot) {
        if (!node.getNodeId().equals(nodeId)) {
            if (children != null) {
                NetworkedNode node = children.get(nodeId);
                if (node != null) {
                    return numberOfLinksIAmFromRoot + 1;
                } else {
                    for (NetworkedNode child : children.values()) {
                        int numberOfLinksAwayFromMe = child.getNumberOfLinksAwayFromMe(nodeId,
                                numberOfLinksIAmFromRoot + 1);
                        if (numberOfLinksAwayFromMe > (numberOfLinksIAmFromRoot + 1)) {
                            return numberOfLinksAwayFromMe;
                        }
                    }
                }
            }
        }
        return numberOfLinksIAmFromRoot;
    }

    public NetworkedNode findNetworkedNode(String nodeId) {
        if (this.node.getNodeId().equals(nodeId)) {
            return this;
        } else {
            if (allNetworkedNodes != null) {
                return allNetworkedNodes.get(nodeId);
            } else if (children != null) {
                NetworkedNode node = children.get(nodeId);
                if (node != null) {
                    return node;
                } else {
                    for (NetworkedNode child : children.values()) {
                        NetworkedNode foundIt = child.findNetworkedNode(nodeId);
                        if (foundIt != null) {
                            return foundIt;
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean isInParentHierarchy(String nodeId) {
        if (parent != null) {
            if (parent.getNode().getNodeId().equals(nodeId)) {
                return true;
            } else {
                return parent.isInParentHierarchy(nodeId);
            }
        } else {
            return false;
        }
    }

    public boolean hasChildrenThatBelongToGroups(Set<String> groupIds) {
        if (children != null) {
            for (NetworkedNode child : children.values()) {
                if (groupIds.contains(child.getNode().getNodeGroupId())) {
                    return true;
                } else {
                    if (child.hasChildrenThatBelongToGroups(groupIds)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isInChildHierarchy(String nodeId) {
        if (children != null) {
            for (NetworkedNode child : children.values()) {
                if (child.getNode().getNodeId().equals(nodeId)) {
                    return true;
                } else {
                    if (child.isInChildHierarchy(nodeId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void addParents(Map<String, Node> nodes, Map<String, NetworkedNode> leaves) {
        String parentNodeId = node.getCreatedAtNodeId();
        if (parentNodeId != null && !parentNodeId.equals(node.getNodeId())) {
            NetworkedNode parentNetworkedNode = leaves.get(parentNodeId);
            if (parentNetworkedNode == null) {
                Node parentNode = nodes.get(parentNodeId);
                if (parentNode != null) {
                    parentNetworkedNode = new NetworkedNode(parentNode);
                    parentNetworkedNode.addParents(nodes, leaves);
                    leaves.put(parentNodeId, parentNetworkedNode);
                }
            }
            if (parentNetworkedNode != null) {
                parentNetworkedNode.addChild(this);
            }
            this.parent = parentNetworkedNode;
        }
    }

    public NetworkedNode getRoot() {
        if (parent != null) {
            return parent.getRoot();
        } else {
            return this;
        }
    }

    public int compareTo(NetworkedNode o) {
        return node.compareTo(o.getNode());
    }

    public void setAllNetworkedNodes(Map<String, NetworkedNode> allNetworkedNodes) {
        this.allNetworkedNodes = allNetworkedNodes;
    }

    public int getNumberOfLinksFromRoot() {
        int count = 0;
        NetworkedNode root = this.parent;
        while (root != null) {
            root = root.getParent();
            count++;
        }
        return count;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        string.append(StringUtils.repeat("-", getNumberOfLinksFromRoot())).append(node.getNodeId());
        if (children != null) {
            Collection<NetworkedNode> set = children.values();
            for (NetworkedNode networkedNode : set) {
                string.append("\n").append(networkedNode);
            }
        }
        return string.toString();
    }
}
