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

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.config.INodeIdCreator;
import org.jumpmind.symmetric.ext.IOfflineServerListener;
import org.jumpmind.symmetric.model.NetworkedNode;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.NodeHost;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.NodeStatus;
import org.jumpmind.symmetric.security.INodePasswordFilter;
import org.jumpmind.symmetric.service.FilterCriterion;
import org.jumpmind.symmetric.service.INodeService;

public class MockNodeService implements INodeService {
    public Node getCachedIdentity() {
        return null;
    }

    public List<Node> findAllNodes() {
        return null;
    }

    public List<Node> findAllNodes(boolean useCache) {
        return null;
    }

    @Override
    public List<Node> findNodesWhoPushToMe() {
        return null;
    }

    @Override
    public List<Node> findNodesWhoPullFromMe() {
        return null;
    }

    public Map<String, Node> findAllNodesAsMap() {
        List<Node> nodes = findAllNodes();
        Map<String, Node> nodeMap = new HashMap<String, Node>();
        if (nodes == null) {
            return nodeMap;
        }
        for (Node node : nodes) {
            nodeMap.put(node.getNodeId(), node);
        }
        return nodeMap;
    }

    public void deleteNodeHost(String nodeId) {
    }

    public void deleteNode(String nodeId, boolean syncChange) {
    }

    @Override
    public void deleteNode(String myNodeId, String targetNodeIId, boolean syncChange) {
    }

    public List<String> findAllExternalIds() {
        return null;
    }

    public NetworkedNode getRootNetworkedNode() {
        return null;
    }

    public List<String> findOfflineNodeIds(long minutesOffline) {
        return null;
    }

    public boolean isRegistrationServer() {
        return false;
    }

    public Set<Node> findNodesThatOriginatedFromNodeId(String originalNodeId) {
        return null;
    }

    public Set<Node> findNodesThatOriginatedFromNodeId(String originalNodeId, boolean recursive) {
        return null;
    }

    public Collection<Node> findNodesWithOpenRegistration() {
        return null;
    }

    public List<NodeHost> findNodeHosts(String nodeId) {
        return null;
    }

    public Node findIdentity() {
        return null;
    }

    public Collection<Node> findEnabledNodesFromNodeGroup(String nodeGroupId) {
        return null;
    }

    public Collection<Node> getEnabledNodesFromDatabase() {
        return null;
    }

    public Map<String, NodeSecurity> findAllNodeSecurity(boolean useCache) {
        return null;
    }

    public String findSymmetricVersion() {
        return null;
    }

    public NodeSecurity findNodeSecurity(String nodeId, boolean createIfNotFound) {
        return null;
    }

    public void save(Node node) {
    }

    public void updateNodeHostForCurrentNode() {
    }

    public void insertNodeGroup(String groupId, String description) {
    }

    public void insertNodeIdentity(String nodeId) {
    }

    public String findIdentityNodeId() {
        return null;
    }

    public Node findNode(String nodeId) {
        return null;
    }

    @Override
    public Node findNodeInCacheOnly(String id) {
        return null;
    }

    @Override
    public List<Node> findFilteredNodesWithLimit(int offset, int limit, List<FilterCriterion> filter,
            String orderColumn, String orderDirection) {
        return null;
    }

    @Override
    public int countFilteredNodes(List<FilterCriterion> filter) {
        return 0;
    }

    public Node findNodeByExternalId(String nodeGroupId, String externalId) {
        return null;
    }

    public NodeSecurity findNodeSecurity(String nodeId) {
        return null;
    }

    public List<Node> findNodesToPull() {
        return null;
    }

    public List<Node> findNodesToPushTo() {
        return null;
    }

    public List<Node> findSourceNodesFor(NodeGroupLinkAction eventAction) {
        return null;
    }

    public List<Node> findTargetNodesFor(NodeGroupLinkAction eventAction) {
        return null;
    }

    public List<Node> getSourceNodesFromDatabase(NodeGroupLinkAction eventAction, Node node) {
        return null;
    }

    public List<Node> getTargetNodesFromDatabase(NodeGroupLinkAction eventAction, Node node) {
        return null;
    }

    public void ignoreNodeChannelForExternalId(boolean ignore, String channelId, String nodeGroupId, String externalId,
            String targetNodeGroupId, String targetExternalId) {
    }

    public boolean isExternalIdRegistered(String nodeGroupId, String externalId) {
        return false;
    }

    public boolean isNodeAuthorized(String nodeId, String password) {
        return false;
    }

    public boolean isRegistrationEnabled(String nodeId) {
        return false;
    }

    public boolean setInitialLoadEnabled(String nodeId, boolean initialLoadEnabled, boolean syncChange, long loadId, String createBy) {
        return false;
    }

    public boolean setInitialLoadEnabled(ISqlTransaction transaction, String nodeId,
            boolean initialLoadEnabled, boolean syncChange, long loadId, String createBy) {
        return false;
    }

    public boolean updateNode(Node node) {
        return false;
    }

    public boolean updateNodeSecurity(ISqlTransaction transaction, NodeSecurity security) {
        return false;
    }

    public boolean updateNodeSecurity(NodeSecurity security) {
        return false;
    }

    public Node findIdentity(boolean useCache) {
        return null;
    }

    public String generateNodeId(String nodeGroupId, String externalId) {
        return null;
    }

    public String generatePassword() {
        return null;
    }

    public void flushNodeAuthorizedCache() {
    }

    public INodeIdCreator getNodeIdCreator() {
        return null;
    }

    public void setNodeIdCreator(INodeIdCreator nodeIdGenerator) {
    }

    public boolean isDataLoadCompleted() {
        return false;
    }

    public boolean isDataLoadStarted() {
        return false;
    }

    public NodeStatus getNodeStatus() {
        return null;
    }

    public void setNodePasswordFilter(INodePasswordFilter nodePasswordFilter) {
    }

    public void checkForOfflineNodes() {
    }

    public List<Node> findOfflineNodes() {
        return null;
    }

    public List<Node> findOfflineNodes(long minutesOffline) {
        return null;
    }

    public boolean deleteIdentity() {
        return false;
    }

    public void deleteNodeSecurity(String nodeId) {
    }

    public void addOfflineServerListener(IOfflineServerListener listener) {
    }

    public boolean removeOfflineServerListener(IOfflineServerListener listener) {
        return false;
    }

    public boolean setReverseInitialLoadEnabled(ISqlTransaction transaction, String nodeId,
            boolean initialLoadEnabled, boolean syncChange, long loadId, String createBy) {
        return true;
    }

    public boolean setReverseInitialLoadEnabled(String nodeId, boolean initialLoadEnabled, boolean syncChange, long loadId, String createBy) {
        return true;
    }

    public List<NodeSecurity> findNodeSecurityWithLoadEnabled() {
        return null;
    }

    public NodeSecurity findOrCreateNodeSecurity(String nodeId) {
        return null;
    }

    public Node findIdentity(boolean useCache, boolean logSqlError) {
        return null;
    }

    public void updateNodeHost(NodeHost nodeHost) {
    }

    public void updateNodeHost(ISqlTransaction transaction, NodeHost nodeHost) {
    }

    public void updateNodeHostForCurrentNode(ISqlTransaction transaction) {
    }

    public Map<String, Date> findLastHeartbeats() {
        return null;
    }

    public void clearCache() {
    }

    public void flushNodeGroupCache() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jumpmind.symmetric.service.INodeService#getExternalId(java.lang.String)
     */
    @Override
    public String getExternalId(String nodeId) {
        return nodeId;
    }

    @Override
    public void flushNodeCache() {
    }

    @Override
    public Node findNode(String id, boolean useCache) {
        return null;
    }

    @Override
    public AuthenticationStatus getAuthenticationStatus(String nodeId, String securityToken) {
        return null;
    }

    @Override
    public Node findRootNode() {
        return null;
    }

    @Override
    public void resetNodeFailedLogins(String nodeId) {
    }

    @Override
    public void incrementNodeFailedLogins(String nodeId) {
    }

    @Override
    public boolean isDataLoadCompleted(String nodeId) {
        return false;
    }

    @Override
    public boolean isDataLoadStarted(String nodeId) {
        return false;
    }

    @Override
    public NodeStatus getNodeStatus(String nodeId) {
        return null;
    }

    @Override
    public boolean setInitialLoadEnded(ISqlTransaction transaction, String nodeId) {
        return false;
    }

    @Override
    public boolean setPartialLoadStarted(ISqlTransaction transaction, String nodeId, long loadId, String createBy) {
        return false;
    }

    @Override
    public boolean setPartialLoadEnded(ISqlTransaction transaction, String nodeId) {
        return false;
    }

    @Override
    public void deleteNodeHostInstance(String nodeId, String instanceId) {
    }

    @Override
    public void deleteStaleNodeHosts(String nodeId, Date staleBeforeTime) {
    }
}