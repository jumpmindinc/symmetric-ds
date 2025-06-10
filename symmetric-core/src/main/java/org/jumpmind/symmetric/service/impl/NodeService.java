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

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.config.INodeIdCreator;
import org.jumpmind.symmetric.ext.IOfflineServerListener;
import org.jumpmind.symmetric.model.NetworkedNode;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.NodeHost;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.NodeStatus;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.security.INodePasswordFilter;
import org.jumpmind.symmetric.service.FilterCriterion;
import org.jumpmind.symmetric.service.FilterCriterion.FilterOption;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.util.DefaultNodeIdCreator;
import org.jumpmind.util.AppUtils;

/**
 * @see INodeService
 */
public class NodeService extends AbstractService implements INodeService {
    private ISymmetricEngine engine;
    private IExtensionService extensionService;
    private Node cachedNodeIdentity;
    private Map<String, NodeSecurity> securityCache;
    private long securityCacheTime;
    private Map<String, Node> nodeCache = new HashMap<String, Node>();
    private long nodeCacheTime;
    private INodePasswordFilter nodePasswordFilter;
    private NodeHost nodeHostForCurrentNode = null;
    private ICacheManager cacheManager;

    public NodeService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        this.cacheManager = engine.getCacheManager();
        extensionService = engine.getExtensionService();
        extensionService.addExtensionPoint(new DefaultNodeIdCreator(parameterService, this, engine.getSecurityService()));
        setSqlMap(new NodeServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    public String findSymmetricVersion() {
        return (String) sqlTemplate.queryForObject(getSql("findSymmetricVersionSql"), String.class);
    }

    public String findIdentityNodeId() {
        Node node = findIdentity();
        return node != null ? node.getNodeId() : null;
    }

    @Override
    public String getExternalId(String nodeId) {
        String externalId = null;
        Node node = findNode(nodeId, true);
        if (node != null) {
            externalId = node.getExternalId();
        }
        return externalId;
    }

    public Collection<Node> findEnabledNodesFromNodeGroup(String nodeGroupId) {
        return cacheManager.getNodesByGroup(nodeGroupId);
    }

    public Collection<Node> getEnabledNodesFromDatabase() {
        return sqlTemplate.query(getSql("selectNodePrefixSql", "findEnabledNodes"), new NodeRowMapper());
    }

    public Set<Node> findNodesThatOriginatedFromNodeId(String originalNodeId) {
        return findNodesThatOriginatedFromNodeId(originalNodeId, true);
    }

    public Collection<Node> findNodesWithOpenRegistration() {
        return sqlTemplate.query(getSql("selectNodePrefixSql", "findNodesWithOpenRegistrationSql"), new NodeRowMapper());
    }

    public Set<Node> findNodesThatOriginatedFromNodeId(String originalNodeId, boolean recursive) {
        Set<Node> all = new HashSet<Node>();
        List<Node> list = sqlTemplate.query(getSql("selectNodePrefixSql", "findNodesCreatedByMeSql"), new NodeRowMapper(), originalNodeId);
        if (list.size() > 0) {
            all.addAll(list);
            if (recursive) {
                for (Node node : list) {
                    all.addAll(findNodesThatOriginatedFromNodeId(node.getNodeId()));
                }
            }
        }
        return all;
    }

    /**
     * Lookup a node in the database, which contains information for syncing with it.
     */
    public Node findNode(String id) {
        List<Node> list = sqlTemplate.query(getSql("selectNodePrefixSql", "findNodeSql"), new NodeRowMapper(), id);
        return (Node) getFirstEntry(list);
    }

    public Node findNodeInCacheOnly(String id) {
        return nodeCache.get(id);
    }

    public Node findNode(String id, boolean useCache) {
        if (useCache) {
            long cacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_IN_MS);
            if ((System.currentTimeMillis() - nodeCacheTime) >= cacheTimeoutInMs) {
                nodeCache = findAllNodesAsMap();
                nodeCacheTime = System.currentTimeMillis();
            }
            return nodeCache.get(id);
        } else {
            return findNode(id);
        }
    }

    public void flushNodeCache() {
        nodeCacheTime = 0;
    }

    public Node findNodeByExternalId(String nodeGroupId, String externalId) {
        List<Node> list = sqlTemplate.query(getSql("selectNodePrefixSql", "findNodeByExternalIdSql"), new NodeRowMapper(), nodeGroupId,
                externalId);
        return (Node) getFirstEntry(list);
    }

    public void ignoreNodeChannelForExternalId(boolean enabled, String channelId, String nodeGroupId, String externalId) {
        Node node = findNodeByExternalId(nodeGroupId, externalId);
        if (sqlTemplate.update(getSql("nodeChannelControlIgnoreSql"), new Object[] { enabled ? 1 : 0, node.getNodeId(), channelId }) <= 0) {
            sqlTemplate.update(getSql("insertNodeChannelControlSql"), new Object[] { node.getNodeId(), channelId, enabled ? 1 : 0, 0 });
        }
    }

    public List<NodeHost> findNodeHosts(String nodeId) {
        return sqlTemplate.query(getSql("selectNodeHostPrefixSql", "selectNodeHostByNodeIdSql"), new NodeHostRowMapper(), nodeId);
    }

    public void deleteNodeHost(String nodeId) {
        platform.getSqlTemplate().update(getSql("deleteNodeHostSql"), new Object[] { nodeId });
    }

    public void deleteNodeHostInstance(String nodeId, String instanceId) {
        platform.getSqlTemplate().update(getSql("deleteNodeHostInstanceSql"), new Object[] { nodeId, instanceId });
    }

    public void updateNodeHost(NodeHost nodeHost) {
        if (sqlTemplate.update(getSql("updateNodeHostSql"),
                new Object[] { nodeHost.getIpAddress(), nodeHost.getInstanceId(), nodeHost.getOsUser(),
                        nodeHost.getOsName(), nodeHost.getOsArch(), nodeHost.getOsVersion(),
                        nodeHost.getAvailableProcessors(), nodeHost.getFreeMemoryBytes(),
                        nodeHost.getTotalMemoryBytes(), nodeHost.getMaxMemoryBytes(), nodeHost.getJavaVersion(),
                        nodeHost.getJavaVendor(), nodeHost.getJdbcVersion(), nodeHost.getSymmetricVersion(),
                        nodeHost.getTimezoneOffset(), nodeHost.getHeartbeatTime(), nodeHost.getLastRestartTime(),
                        nodeHost.getNodeId(), nodeHost.getHostName() }) <= 0) {
            sqlTemplate.update(getSql("insertNodeHostSql"),
                    new Object[] { nodeHost.getIpAddress(), nodeHost.getInstanceId(), nodeHost.getOsUser(),
                            nodeHost.getOsName(), nodeHost.getOsArch(), nodeHost.getOsVersion(),
                            nodeHost.getAvailableProcessors(), nodeHost.getFreeMemoryBytes(),
                            nodeHost.getTotalMemoryBytes(), nodeHost.getMaxMemoryBytes(), nodeHost.getJavaVersion(),
                            nodeHost.getJavaVendor(), nodeHost.getJdbcVersion(), nodeHost.getSymmetricVersion(),
                            nodeHost.getTimezoneOffset(), nodeHost.getHeartbeatTime(), nodeHost.getLastRestartTime(),
                            new Date(), nodeHost.getNodeId(), nodeHost.getHostName() });
        }
    }

    public void updateNodeHostForCurrentNode() {
        if (nodeHostForCurrentNode == null) {
            nodeHostForCurrentNode = new NodeHost(findIdentityNodeId(), engine.getClusterService().getInstanceId());
        }
        nodeHostForCurrentNode.refresh(platform, engine.getClusterService().getInstanceId());
        updateNodeHost(nodeHostForCurrentNode);
    }

    @Override
    public void deleteNode(String nodeId, boolean syncChange) {
        deleteNode(nodeId, null, syncChange);
    }

    @Override
    public synchronized void deleteNode(String nodeId, String targetNodeId, boolean syncChange) {
        log.info("Unregistering node {} and removing it from database", nodeId);
        if (StringUtils.isNotBlank(nodeId)) {
            for (ProcessInfo info : engine.getStatisticManager().getProcessInfos()) {
                if ((info.getTargetNodeId() != null && info.getTargetNodeId().equals(nodeId)) ||
                        (info.getSourceNodeId() != null && info.getSourceNodeId().equals(nodeId))) {
                    log.info("Sending interrupt to " + info.getKey() + ",batchId=" + info.getCurrentBatchId());
                    info.getThread().interrupt();
                }
            }
            ISqlTransaction transaction = null;
            try {
                transaction = sqlTemplate.startSqlTransaction();
                if (!syncChange) {
                    symmetricDialect.disableSyncTriggers(transaction, nodeId);
                }
                String myNode = findIdentityNodeId();
                if (StringUtils.isNotBlank(myNode) && myNode.equals(nodeId)) {
                    transaction.prepareAndExecute(getSql("deleteNodeIdentitySql"));
                    cachedNodeIdentity = null;
                }
                transaction.prepareAndExecute(getSql("deleteNodeSecuritySql"), new Object[] { nodeId });
                transaction.prepareAndExecute(getSql("deleteNodeHostSql"), new Object[] { nodeId });
                transaction.prepareAndExecute(getSql("deleteNodeSql"), new Object[] { nodeId });
                transaction.prepareAndExecute(getSql("deleteNodeChannelCtlSql"), new Object[] { nodeId });
                transaction.prepareAndExecute(getSql("deleteIncomingErrorSql"), new Object[] { StringUtils.isNotBlank(targetNodeId) ? targetNodeId : nodeId });
                transaction.prepareAndExecute(getSql("deleteExtractRequestSql"), new Object[] { nodeId, nodeId });
                transaction.prepareAndExecute(getSql("deleteNodeCommunicationSql"), new Object[] { StringUtils.isNotBlank(targetNodeId) ? targetNodeId
                        : nodeId });
                transaction.prepareAndExecute(getSql("deleteTableReloadRequestSql"), new Object[] { nodeId, nodeId });
                transaction.prepareAndExecute(getSql("cancelTableReloadStatusSql"), new Object[] { new Date(), new Date(), nodeId, nodeId });
                transaction.prepareAndExecute(getSql("setOutgoingBatchOkSql"), new Object[] { StringUtils.isNotBlank(targetNodeId) ? targetNodeId : nodeId });
                transaction.prepareAndExecute(getSql("deleteIncomingBatchSql"), new Object[] { StringUtils.isNotBlank(targetNodeId) ? targetNodeId : nodeId });
                transaction.commit();
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                if (!syncChange) {
                    symmetricDialect.enableSyncTriggers(transaction);
                }
                close(transaction);
            }
        }
    }

    public void insertNodeIdentity(String nodeId) {
        sqlTemplate.update(getSql("insertNodeIdentitySql"), nodeId);
    }

    public boolean deleteIdentity() {
        boolean successful = false;
        try {
            sqlTemplate.update(getSql("deleteNodeIdentitySql"));
            successful = true;
        } catch (SqlException ex) {
            log.debug(ex.getMessage());
        } finally {
            cachedNodeIdentity = null;
        }
        return successful;
    }

    public void insertNodeGroup(String groupId, String description) {
        if (sqlTemplate.queryForInt(getSql("doesNodeGroupExistSql"), groupId) == 0) {
            sqlTemplate.update(getSql("insertNodeGroupSql"), description, groupId);
        }
    }

    public void save(Node node) {
        if (!updateNode(node)) {
            sqlTemplate.update(
                    getSql("insertNodeSql"),
                    new Object[] { node.getNodeGroupId(), node.getExternalId(), node.getDatabaseType(),
                            node.getDatabaseVersion(), node.getDatabaseName(), node.getSchemaVersion(),
                            node.getSymmetricVersion(), node.getSyncUrl(),
                            node.isSyncEnabled() ? 1 : 0,
                            node.getBatchToSendCount(), node.getBatchInErrorCount(),
                            node.getLastSuccessfulSyncDate(), node.getMostRecentActiveTableSynced(),
                            node.getPurgeOutgoingAverageMs(), node.getPurgeOutgoingLastMs(), node.getPurgeOutgoingLastRun(), node.getRoutingAverageMs(),
                            node.getRoutingLastMs(), node.getRoutingLastRun(), node.getSymDataSize(),
                            node.getCreatedAtNodeId(), node.getDeploymentType(),
                            node.getDeploymentSubType(), node.getConfigVersion(),
                            node.getDataRowsToSendCount(), node.getDataRowsLoadedCount(), node.getOldestLoadTime(),
                            node.getNodeId() },
                    new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                            Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.TIMESTAMP,
                            Types.VARCHAR, Types.BIGINT, Types.BIGINT, Types.TIMESTAMP,
                            Types.BIGINT, Types.BIGINT,
                            Types.TIMESTAMP, Types.BIGINT, Types.VARCHAR, Types.VARCHAR,
                            Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.INTEGER,
                            Types.TIMESTAMP, Types.VARCHAR });
            flushNodeGroupCache();
        }
        flushNodeCache();
    }

    public boolean updateNode(Node node) {
        boolean updated = sqlTemplate.update(
                getSql("updateNodeSql"),
                new Object[] { node.getNodeGroupId(), node.getExternalId(), node.getDatabaseType(),
                        node.getDatabaseVersion(), node.getDatabaseName(), node.getSchemaVersion(),
                        node.getSymmetricVersion(), node.getSyncUrl(),
                        node.isSyncEnabled() ? 1 : 0,
                        node.getBatchToSendCount(), node.getBatchInErrorCount(), node.getLastSuccessfulSyncDate(),
                        node.getMostRecentActiveTableSynced(), node.getPurgeOutgoingAverageMs(),
                        node.getPurgeOutgoingLastMs(), node.getPurgeOutgoingLastRun(), node.getRoutingAverageMs(), node.getRoutingLastMs(),
                        node.getRoutingLastRun(), node.getSymDataSize(),
                        node.getCreatedAtNodeId(), node.getDeploymentType(), node.getDeploymentSubType(),
                        node.getConfigVersion(),
                        node.getDataRowsToSendCount(), node.getDataRowsLoadedCount(), node.getOldestLoadTime(),
                        node.getNodeId() },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                        Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                        Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.TIMESTAMP,
                        Types.VARCHAR, Types.BIGINT, Types.BIGINT, Types.TIMESTAMP,
                        Types.BIGINT, Types.BIGINT,
                        Types.TIMESTAMP, Types.BIGINT, Types.VARCHAR, Types.VARCHAR,
                        Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.INTEGER,
                        Types.TIMESTAMP, Types.VARCHAR }) == 1;
        return updated;
    }

    protected <T> T getFirstEntry(List<T> list) {
        if (list != null && list.size() > 0) {
            return list.get(0);
        }
        return null;
    }

    public Node getCachedIdentity() {
        return cachedNodeIdentity;
    }

    public Node findIdentity() {
        return findIdentity(true);
    }

    public Node findIdentity(boolean useCache) {
        return findIdentity(useCache, true);
    }

    public Node findIdentity(boolean useCache, boolean logSqlError) {
        if (cachedNodeIdentity == null || useCache == false) {
            try {
                List<Node> list = sqlTemplate.query(getSql("selectNodePrefixSql", "findNodeIdentitySql"), new NodeRowMapper());
                cachedNodeIdentity = (Node) getFirstEntry(list);
            } catch (SqlException ex) {
                if (logSqlError) {
                    // This is at debug level because it gets called pre-registration
                    log.debug("Failed to load the node identity. Returning " + cachedNodeIdentity, ex);
                }
            }
        }
        return cachedNodeIdentity;
    }

    public List<Node> findNodesToPull() {
        return findSourceNodesFor(NodeGroupLinkAction.W);
    }

    public List<Node> findNodesWhoPushToMe() {
        return findSourceNodesFor(NodeGroupLinkAction.P);
    }

    public List<Node> findNodesToPushTo() {
        return findTargetNodesFor(NodeGroupLinkAction.P);
    }

    public List<Node> findNodesWhoPullFromMe() {
        return findTargetNodesFor(NodeGroupLinkAction.W);
    }

    public List<Node> findSourceNodesFor(NodeGroupLinkAction eventAction) {
        Node node = findIdentity();
        if (node != null) {
            return cacheManager.getSourceNodesCache(eventAction, node);
        } else {
            return Collections.emptyList();
        }
    }

    public List<Node> getSourceNodesFromDatabase(NodeGroupLinkAction eventAction, Node node) {
        if (node != null) {
            return sqlTemplate.query(getSql("selectNodePrefixSql", "findNodesWhoTargetMeSql"),
                    new NodeRowMapper(), node.getNodeGroupId(), eventAction.name());
        } else {
            return Collections.emptyList();
        }
    }

    public List<Node> findTargetNodesFor(NodeGroupLinkAction eventAction) {
        Node node = findIdentity();
        if (node != null) {
            return cacheManager.getTargetNodesCache(eventAction, node);
        } else {
            return Collections.emptyList();
        }
    }

    public List<Node> getTargetNodesFromDatabase(NodeGroupLinkAction eventAction, Node node) {
        if (node != null) {
            return sqlTemplate.query(getSql("selectNodePrefixSql", "findNodesWhoITargetSql"),
                    new NodeRowMapper(), node.getNodeGroupId(), eventAction.name());
        } else {
            return Collections.emptyList();
        }
    }

    public void flushNodeGroupCache() {
        cacheManager.flushSourceNodesCache();
        cacheManager.flushTargetNodesCache();
    }

    public List<String> findAllExternalIds() {
        return sqlTemplate.query(getSql("selectExternalIdsSql"), new StringMapper());
    }

    public List<Node> findAllNodes() {
        List<Node> nodeList = sqlTemplate.query(getSql("selectNodePrefixSql"), new NodeRowMapper());
        return nodeList;
    }

    public List<Node> findAllNodes(boolean useCache) {
        if (useCache) {
            findNode(findIdentityNodeId(), true);
            return new ArrayList<Node>(nodeCache.values());
        } else {
            return findAllNodes();
        }
    }

    public Map<String, Node> findAllNodesAsMap() {
        List<Node> nodes = findAllNodes();
        Map<String, Node> nodeMap = new HashMap<String, Node>(nodes.size());
        for (Node node : nodes) {
            nodeMap.put(node.getNodeId(), node);
        }
        return nodeMap;
    }

    public List<Node> findFilteredNodesWithLimit(int offset, int limit, List<FilterCriterion> filter,
            String orderColumn, String orderDirection) {
        String where = filter != null ? buildWhere(filter) : null;
        Map<String, Object> params = filter != null ? buildParams(filter) : new HashMap<String, Object>();
        String orderBy = buildOrderBy(orderColumn, orderDirection);
        String sql = getSql("selectNodePrefixSql", where, orderBy);
        List<Node> nodeList;
        if (platform.supportsLimitOffset()) {
            sql = platform.massageForLimitOffset(sql, limit, offset);
            nodeList = sqlTemplateDirty.query(sql, new NodeRowMapper(), params);
        } else {
            ISqlReadCursor<Node> cursor = sqlTemplateDirty.queryForCursor(sql, new NodeRowMapper(), params);
            try {
                Node next = null;
                nodeList = new ArrayList<Node>();
                int rowCount = 0;
                do {
                    next = cursor.next();
                    if (next != null) {
                        if (offset <= rowCount && rowCount < limit + offset) {
                            nodeList.add(next);
                        }
                        rowCount++;
                    }
                    if (rowCount >= limit + offset) {
                        break;
                    }
                } while (next != null);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return nodeList;
    }

    public int countFilteredNodes(List<FilterCriterion> filter) {
        String where = filter != null ? buildWhere(filter) : null;
        Map<String, Object> params = filter != null ? buildParams(filter) : new HashMap<String, Object>();
        String sql = "select count(*) from " + TableConstants.getTableName(parameterService.getTablePrefix(), TableConstants.SYM_NODE) + where;
        int size = sqlTemplate.queryForInt(sql, params);
        return size;
    }

    protected String buildWhere(List<FilterCriterion> filter) {
        StringBuilder where = new StringBuilder();
        boolean needsAnd = false;
        int id = 0;
        for (FilterCriterion criterion : filter) {
            if (needsAnd) {
                where.append(" and ");
            } else {
                needsAnd = true;
            }
            FilterOption option = criterion.getOption();
            String optionSql = option.toSql();
            String prefix = criterion.getPropertyId().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase() + " " + optionSql;
            where.append(prefix + " :" + id++);
        }
        if (where.length() > 0) {
            where.insert(0, " where ");
        }
        return where.toString();
    }

    protected Map<String, Object> buildParams(List<FilterCriterion> filter) {
        Map<String, Object> params = new HashMap<String, Object>();
        int id = 0;
        for (FilterCriterion criterion : filter) {
            Object value = criterion.getValues().get(0);
            FilterOption option = criterion.getOption();
            if (option.equals(FilterOption.CONTAINS)) {
                value = "%" + value + "%";
            } else if (option.equals(FilterOption.STARTS_WITH)) {
                value += "%";
            }
            params.put(String.valueOf(id++), value);
        }
        return params;
    }

    protected String buildOrderBy(String orderColumn, String orderDirection) {
        String orderBy = " order by ";
        if (orderColumn == null) {
            orderBy += "node_id desc";
        } else {
            orderBy += orderColumn.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
            if (orderDirection.equals("DESCENDING")) {
                orderBy += " desc";
            }
        }
        return orderBy;
    }

    public NetworkedNode getRootNetworkedNode() {
        Map<String, Node> nodes = findAllNodesAsMap();
        Map<String, NetworkedNode> leaves = new HashMap<String, NetworkedNode>(nodes.size());
        NetworkedNode nodeLeaf = null;
        for (Node node : nodes.values()) {
            nodeLeaf = leaves.get(node.getNodeId());
            if (nodeLeaf == null) {
                nodeLeaf = new NetworkedNode(node);
                nodeLeaf.addParents(nodes, leaves);
                leaves.put(node.getNodeId(), nodeLeaf);
            }
        }
        nodeLeaf = leaves.get(findIdentityNodeId());
        if (nodeLeaf != null) {
            NetworkedNode root = nodeLeaf.getRoot();
            root.setAllNetworkedNodes(leaves);
            return root;
        } else {
            return null;
        }
    }

    public Node findRootNode() {
        List<Node> nodeList = sqlTemplate.query(getSql("selectNodePrefixSql", "findRootNodeSql"), new NodeRowMapper());
        if (nodeList.size() > 0) {
            return nodeList.get(0);
        }
        return null;
    }

    /**
     * Lookup a node_security in the database, which contains private information used to authenticate.
     */
    public NodeSecurity findNodeSecurity(String id) {
        return findNodeSecurity(id, false);
    }

    public NodeSecurity findNodeSecurity(String nodeId, boolean useCache) {
        if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) && useCache) {
            Map<String, NodeSecurity> nodeSecurities = findAllNodeSecurity(true);
            return nodeSecurities.get(nodeId);
        } else {
            List<NodeSecurity> list = sqlTemplate.query(getSql("selectNodeSecuritySql", "findNodeSecurityByNodeIdSql"),
                    new NodeSecurityRowMapper(), new Object[] { nodeId }, new int[] { Types.VARCHAR });
            return getFirstEntry(list);
        }
    }

    public NodeSecurity findOrCreateNodeSecurity(String nodeId) {
        try {
            if (nodeId != null) {
                NodeSecurity security = findNodeSecurity(nodeId, false);
                if (security == null) {
                    insertNodeSecurity(nodeId);
                    security = findNodeSecurity(nodeId, true);
                }
                return security;
            } else {
                log.debug("A 'null' node id was passed into findNodeSecurity");
                return null;
            }
        } catch (UniqueKeyException ex) {
            log.error("Could not find a node security row for '{}'", nodeId);
            throw ex;
        }
    }

    public boolean isRegistrationEnabled(String nodeId) {
        NodeSecurity nodeSecurity = findNodeSecurity(nodeId);
        if (nodeSecurity != null) {
            return nodeSecurity.isRegistrationEnabled();
        }
        return false;
    }

    public void insertNodeSecurity(String id) {
        String password = extensionService.getExtensionPoint(INodeIdCreator.class).generatePassword(new Node(id, null, null));
        password = filterPasswordOnSaveIfNeeded(password, id);
        sqlTemplate.update(getSql("insertNodeSecuritySql"), new Object[] { id, password, null });
        flushNodeAuthorizedCache();
    }

    public void deleteNodeSecurity(String nodeId) {
        sqlTemplate.update(getSql("deleteNodeSecuritySql"), new Object[] { nodeId });
        flushNodeAuthorizedCache();
    }

    public List<NodeSecurity> findNodeSecurityWithLoadEnabled() {
        if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            return sqlTemplate.query(getSql("selectNodeSecuritySql", "findNodeSecurityWithLoadEnabledSql"), new NodeSecurityRowMapper());
        } else {
            List<NodeSecurity> list = new ArrayList<NodeSecurity>();
            for (NodeSecurity nodeSecurity : findAllNodeSecurity(true).values()) {
                if (nodeSecurity.isInitialLoadEnabled() || nodeSecurity.isRevInitialLoadEnabled()) {
                    list.add(nodeSecurity);
                }
            }
            return list;
        }
    }

    public synchronized Map<String, NodeSecurity> findAllNodeSecurity(boolean useCache) {
        long maxSecurityCacheTime = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_SECURITY_IN_MS);
        Map<String, NodeSecurity> all = securityCache;
        if (all == null || System.currentTimeMillis() - securityCacheTime >= maxSecurityCacheTime || securityCacheTime == 0 || !useCache) {
            all = (Map<String, NodeSecurity>) sqlTemplate.queryForMap(getSql("selectNodeSecuritySql"), new NodeSecurityRowMapper(),
                    "node_id");
            securityCache = all;
            securityCacheTime = System.currentTimeMillis();
        }
        return all;
    }

    /**
     * Check that the given node and password match in the node_security table. A node must authenticate before it's allowed to sync data.
     */
    public boolean isNodeAuthorized(String nodeId, String password) {
        int maxFailedLogins = parameterService.getInt(ParameterConstants.NODE_PASSWORD_FAILED_ATTEMPTS);
        Map<String, NodeSecurity> nodeSecurities = findAllNodeSecurity(true);
        NodeSecurity nodeSecurity = nodeSecurities.get(nodeId);
        if (nodeSecurity != null) {
            if (!nodeId.equals(findIdentityNodeId()) && StringUtils.isNotBlank(nodeSecurity.getNodePassword())
                    && nodeSecurity.getNodePassword().equals(password)
                    && (maxFailedLogins <= 0 || nodeSecurity.getFailedLogins() < maxFailedLogins)
                    || nodeSecurity.isRegistrationEnabled()) {
                return true;
            }
        }
        return false;
    }

    protected boolean isNodeAuthorizationLocked(String nodeId) {
        int maxFailedLogins = parameterService.getInt(ParameterConstants.NODE_PASSWORD_FAILED_ATTEMPTS);
        if (maxFailedLogins > 0) {
            Map<String, NodeSecurity> nodeSecurities = findAllNodeSecurity(true);
            NodeSecurity nodeSecurity = nodeSecurities.get(nodeId);
            return nodeSecurity != null && nodeSecurity.getFailedLogins() >= maxFailedLogins;
        }
        return false;
    }

    protected boolean isNodePasswordFailedDecrypt(String nodeId) {
        Map<String, NodeSecurity> nodeSecurities = findAllNodeSecurity(true);
        NodeSecurity nodeSecurity = nodeSecurities.get(nodeId);
        return nodeSecurity != null && nodeSecurity.getNodePassword() == null;
    }

    public void flushNodeAuthorizedCache() {
        securityCacheTime = 0;
    }

    public boolean updateNodeSecurity(NodeSecurity security) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            boolean updated = updateNodeSecurity(transaction, security);
            transaction.commit();
            return updated;
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    public boolean updateNodeSecurity(ISqlTransaction transaction, NodeSecurity security) {
        security.setNodePassword(filterPasswordOnSaveIfNeeded(security.getNodePassword(), security.getNodeId()));
        String sql = getSql("updateNodeSecuritySql");
        Object[] values = new Object[] { security.getNodePassword(), security.isRegistrationEnabled() ? 1 : 0, security.getRegistrationTime(),
                security.getRegistrationNotBefore(), security.getRegistrationNotAfter(), security.isInitialLoadEnabled() ? 1 : 0,
                security.getInitialLoadTime(), security.getInitialLoadEndTime(), security.getCreatedAtNodeId(),
                security.isRevInitialLoadEnabled() ? 1 : 0, security.getRevInitialLoadTime(), security.getInitialLoadId(),
                security.getInitialLoadCreateBy(), security.getRevInitialLoadId(), security.getRevInitialLoadCreateBy(),
                security.getFailedLogins(), security.getPartialLoadTime(), security.getPartialLoadEndTime(),
                security.getPartialLoadId(), security.getPartialLoadCreateBy(), security.getNodeId() };
        int[] types = new int[] { Types.VARCHAR, Types.INTEGER, Types.TIMESTAMP,
                Types.TIMESTAMP, Types.TIMESTAMP, Types.INTEGER,
                Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR,
                Types.INTEGER, Types.TIMESTAMP, Types.BIGINT,
                Types.VARCHAR, Types.BIGINT, Types.VARCHAR,
                Types.INTEGER, Types.TIMESTAMP, Types.TIMESTAMP, symmetricDialect.getSqlTypeForIds(), Types.VARCHAR, Types.VARCHAR };
        if (StringUtils.isBlank(security.getNodePassword())) {
            sql = sql.replace("node_password = ?,", "");
            values = ArrayUtils.subarray(values, 1, values.length);
            types = ArrayUtils.subarray(types, 1, types.length);
        }
        int updateCount = transaction.prepareAndExecute(sql, values, types);
        flushNodeAuthorizedCache();
        return (updateCount == 1);
    }

    public boolean setInitialLoadEnabled(ISqlTransaction transaction, String nodeId, boolean initialLoadEnabled, boolean syncChange,
            long loadId, String createBy) {
        try {
            if (!syncChange) {
                symmetricDialect.disableSyncTriggers(transaction, nodeId);
            }
            NodeSecurity nodeSecurity = findOrCreateNodeSecurity(nodeId);
            if (nodeSecurity != null) {
                nodeSecurity.setInitialLoadEnabled(initialLoadEnabled);
                nodeSecurity.setInitialLoadId(loadId);
                nodeSecurity.setInitialLoadEndTime(null);
                nodeSecurity.setInitialLoadCreateBy(createBy);
                if (initialLoadEnabled) {
                    nodeSecurity.setInitialLoadTime(null);
                } else {
                    nodeSecurity.setInitialLoadTime(new Date());
                }
                return updateNodeSecurity(transaction, nodeSecurity);
            }
            return false;
        } finally {
            if (!syncChange) {
                symmetricDialect.enableSyncTriggers(transaction);
            }
        }
    }

    public boolean setInitialLoadEnabled(String nodeId, boolean initialLoadEnabled, boolean syncChange, long loadId, String createBy) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            boolean updated = setInitialLoadEnabled(transaction, nodeId, initialLoadEnabled, syncChange, loadId, createBy);
            transaction.commit();
            return updated;
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    public boolean setInitialLoadEnded(ISqlTransaction transaction, String nodeId) {
        boolean isAutoCommit = false;
        try {
            if (transaction == null) {
                transaction = sqlTemplate.startSqlTransaction();
                isAutoCommit = true;
            }
            NodeSecurity nodeSecurity = findOrCreateNodeSecurity(nodeId);
            boolean isUpdated = false;
            if (nodeSecurity != null) {
                nodeSecurity.setInitialLoadEndTime(new Date());
                isUpdated = updateNodeSecurity(transaction, nodeSecurity);
            }
            if (isAutoCommit) {
                transaction.commit();
            }
            return isUpdated;
        } catch (Error ex) {
            if (isAutoCommit && transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (isAutoCommit && transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            if (isAutoCommit) {
                close(transaction);
            }
        }
    }

    public boolean setPartialLoadStarted(ISqlTransaction transaction, String nodeId, long loadId, String createBy) {
        NodeSecurity nodeSecurity = findOrCreateNodeSecurity(nodeId);
        if (nodeSecurity != null) {
            nodeSecurity.setPartialLoadId(loadId);
            nodeSecurity.setPartialLoadTime(new Date());
            nodeSecurity.setPartialLoadEndTime(null);
            nodeSecurity.setPartialLoadCreateBy(createBy);
            return updateNodeSecurity(transaction, nodeSecurity);
        }
        return false;
    }

    public boolean setPartialLoadEnded(ISqlTransaction transaction, String nodeId) {
        boolean isAutoCommit = false;
        try {
            if (transaction == null) {
                transaction = sqlTemplate.startSqlTransaction();
                isAutoCommit = true;
            }
            NodeSecurity nodeSecurity = findOrCreateNodeSecurity(nodeId);
            boolean isUpdated = false;
            if (nodeSecurity != null) {
                nodeSecurity.setPartialLoadEndTime(new Date());
                isUpdated = updateNodeSecurity(transaction, nodeSecurity);
            }
            if (isAutoCommit) {
                transaction.commit();
            }
            return isUpdated;
        } catch (Error ex) {
            if (isAutoCommit && transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (isAutoCommit && transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            if (isAutoCommit) {
                close(transaction);
            }
        }
    }

    public boolean setReverseInitialLoadEnabled(ISqlTransaction transaction, String nodeId, boolean initialLoadEnabled, boolean syncChange,
            long loadId, String createBy) {
        try {
            if (!syncChange) {
                symmetricDialect.disableSyncTriggers(transaction, nodeId);
            }
            NodeSecurity nodeSecurity = findOrCreateNodeSecurity(nodeId);
            if (nodeSecurity != null) {
                nodeSecurity.setRevInitialLoadEnabled(initialLoadEnabled);
                nodeSecurity.setRevInitialLoadId(loadId);
                if (initialLoadEnabled) {
                    nodeSecurity.setRevInitialLoadTime(null);
                    nodeSecurity.setRevInitialLoadCreateBy(createBy);
                } else {
                    nodeSecurity.setRevInitialLoadTime(new Date());
                }
                return updateNodeSecurity(transaction, nodeSecurity);
            }
            return false;
        } finally {
            if (!syncChange) {
                symmetricDialect.enableSyncTriggers(transaction);
            }
        }
    }

    public boolean setReverseInitialLoadEnabled(String nodeId, boolean initialLoadEnabled, boolean syncChange, long loadId, String createBy) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            boolean updated = setReverseInitialLoadEnabled(transaction, nodeId, initialLoadEnabled, syncChange, loadId, createBy);
            transaction.commit();
            return updated;
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    public boolean isExternalIdRegistered(String nodeGroupId, String externalId) {
        return sqlTemplate.queryForInt(getSql("isNodeRegisteredSql"), new Object[] { nodeGroupId, externalId }) > 0;
    }

    public boolean isDataLoadCompleted() {
        return getNodeStatus() == NodeStatus.DATA_LOAD_COMPLETED;
    }

    public boolean isDataLoadCompleted(String nodeId) {
        return getNodeStatus(nodeId) == NodeStatus.DATA_LOAD_COMPLETED;
    }

    public boolean isDataLoadStarted() {
        return getNodeStatus() == NodeStatus.DATA_LOAD_STARTED;
    }

    public boolean isDataLoadStarted(String nodeId) {
        return getNodeStatus(nodeId) == NodeStatus.DATA_LOAD_STARTED;
    }

    public boolean isRegistrationServer() {
        return parameterService.isRegistrationServer();
    }

    public NodeStatus getNodeStatus() {
        return getNodeStatus(findIdentityNodeId());
    }

    public NodeStatus getNodeStatus(String nodeId) {
        long ts = System.currentTimeMillis();
        try {
            NodeSecurity nodeSecurity = findNodeSecurity(nodeId, true);
            if (nodeSecurity != null) {
                if (nodeSecurity.isInitialLoadEnabled() || (nodeSecurity.getInitialLoadTime() != null && nodeSecurity.getInitialLoadEndTime() == null)) {
                    return NodeStatus.DATA_LOAD_STARTED;
                } else if (nodeSecurity.getInitialLoadEndTime() != null) {
                    return NodeStatus.DATA_LOAD_COMPLETED;
                }
            }
            return NodeStatus.DATA_LOAD_NOT_STARTED;
        } catch (SqlException ex) {
            log.error("Could not query table after {} ms.  The status is unknown.", (System.currentTimeMillis() - ts), ex);
            return NodeStatus.STATUS_UNKNOWN;
        }
    }

    public void setNodePasswordFilter(INodePasswordFilter nodePasswordFilter) {
        this.nodePasswordFilter = nodePasswordFilter;
    }

    private String filterPasswordOnSaveIfNeeded(String password, String nodeId) {
        String s = password;
        if (nodePasswordFilter != null) {
            s = nodePasswordFilter.onNodeSecuritySave(password, nodeId);
        }
        return s;
    }

    private String filterPasswordOnRenderIfNeeded(String password, String nodeId) {
        String s = password;
        if (nodePasswordFilter != null) {
            s = nodePasswordFilter.onNodeSecurityRender(password, nodeId);
        }
        return s;
    }

    public void checkForOfflineNodes() {
        long offlineNodeDetectionMinutes = parameterService.getLong(ParameterConstants.OFFLINE_NODE_DETECTION_PERIOD_MINUTES);
        List<IOfflineServerListener> offlineServerListeners = extensionService.getExtensionPointList(IOfflineServerListener.class);
        // Only check for offline nodes if there is a listener and the
        // offline detection period is a positive value. A negative value
        // disables the feature.
        if (offlineServerListeners != null && offlineNodeDetectionMinutes > 0) {
            List<Node> list = findOfflineNodes();
            if (list.size() > 0) {
                fireOffline(list);
            }
        }
    }

    public List<Node> findOfflineNodes() {
        return findOfflineNodes(parameterService.getLong(ParameterConstants.OFFLINE_NODE_DETECTION_PERIOD_MINUTES));
    }

    public List<Node> findOfflineNodes(long minutesOffline) {
        List<Node> offlineNodeList = new ArrayList<Node>();
        Node myNode = findIdentity();
        long restartDelayMinutes = parameterService.getLong(ParameterConstants.OFFLINE_NODE_DETECTION_RESTART_MINUTES);
        Date lastRestartTime = engine.getLastRestartTime() != null ? engine.getLastRestartTime() : new Date();
        if (myNode != null && System.currentTimeMillis() - lastRestartTime.getTime() > restartDelayMinutes * 60000) {
            long offlineNodeDetectionMillis = minutesOffline * 60 * 1000;
            List<Row> list = sqlTemplateDirty.query(getSql("findNodeHeartbeatsSql"));
            for (Row node : list) {
                String nodeId = node.getString("node_id");
                Date time = node.getDateTime("heartbeat_time");
                String offset = node.getString("timezone_offset");
                // Take the timezone of the client node into account when
                // checking the hearbeat time.
                Date clientNodeCurrentTime = null;
                if (offset != null) {
                    clientNodeCurrentTime = AppUtils.getLocalDateForOffset(offset);
                } else {
                    clientNodeCurrentTime = new Date();
                }
                long cutOffTimeMillis = clientNodeCurrentTime.getTime() - offlineNodeDetectionMillis;
                if (time == null || time.getTime() < cutOffTimeMillis) {
                    offlineNodeList.add(findNode(nodeId, true));
                }
            }
        }
        return offlineNodeList;
    }

    public Map<String, Date> findLastHeartbeats() {
        Map<String, Date> dates = new HashMap<String, Date>();
        Node myNode = findIdentity();
        if (myNode != null) {
            List<Row> list = sqlTemplateDirty.query(getSql("findNodeHeartbeatsSql"));
            for (Row node : list) {
                String nodeId = node.getString("node_id");
                Date time = node.getDateTime("heartbeat_time");
                dates.put(nodeId, time);
            }
        }
        return dates;
    }

    public List<String> findOfflineNodeIds(long minutesOffline) {
        List<String> offlineNodeList = new ArrayList<String>();
        Node myNode = findIdentity();
        if (myNode != null) {
            long offlineNodeDetectionMillis = minutesOffline * 60 * 1000;
            List<Row> list = sqlTemplateDirty.query(getSql("findNodeHeartbeatsSql"));
            for (Row node : list) {
                String nodeId = node.getString("node_id");
                Date time = node.getDateTime("heartbeat_time");
                String offset = node.getString("timezone_offset");
                // Take the timezone of the client node into account when
                // checking the hearbeat time.
                Date clientNodeCurrentTime = null;
                if (offset != null) {
                    clientNodeCurrentTime = AppUtils.getLocalDateForOffset(offset);
                } else {
                    clientNodeCurrentTime = new Date();
                }
                long cutOffTimeMillis = clientNodeCurrentTime.getTime() - offlineNodeDetectionMillis;
                if (time == null || time.getTime() < cutOffTimeMillis) {
                    offlineNodeList.add(nodeId);
                }
            }
        }
        return offlineNodeList;
    }

    protected void fireOffline(List<Node> offlineClientNodeList) {
        Node myNode = findIdentity();
        for (IOfflineServerListener listener : extensionService.getExtensionPointList(IOfflineServerListener.class)) {
            for (Node node : offlineClientNodeList) {
                if (myNode != null && !myNode.equals(node)) {
                    String myNodeId = myNode.getNodeId();
                    if (myNodeId.equals(node.getCreatedAtNodeId())) {
                        listener.clientNodeOffline(node);
                    } else {
                        NodeSecurity security = findNodeSecurity(node.getNodeId());
                        if (security != null && myNodeId.equals(security.getCreatedAtNodeId())) {
                            listener.clientNodeOffline(node);
                        }
                    }
                }
            }
        }
    }

    public static class NodeRowMapper implements ISqlRowMapper<Node> {
        public Node mapRow(Row rs) {
            Node node = new Node();
            node.setNodeId(rs.getString("node_id"));
            node.setNodeGroupId(rs.getString("node_group_id"));
            node.setExternalId(rs.getString("external_id"));
            node.setSyncEnabled(rs.getBoolean("sync_enabled"));
            node.setSyncUrl(rs.getString("sync_url"));
            node.setSchemaVersion(rs.getString("schema_version"));
            node.setDatabaseType(rs.getString("database_type"));
            node.setDatabaseVersion(rs.getString("database_version"));
            node.setDatabaseName(rs.getString("database_name"));
            node.setSymmetricVersion(rs.getString("symmetric_version"));
            node.setCreatedAtNodeId(rs.getString("created_at_node_id"));
            node.setBatchToSendCount(rs.getInt("batch_to_send_count"));
            node.setBatchInErrorCount(rs.getInt("batch_in_error_count"));
            node.setDeploymentType(rs.getString("deployment_type"));
            node.setDeploymentSubType(rs.getString("deployment_sub_type"));
            node.setConfigVersion(rs.getString("config_version"));
            node.setPurgeOutgoingAverageMs(rs.getLong("purge_outgoing_average_ms"));
            node.setPurgeOutgoingLastMs(rs.getLong("purge_outgoing_last_run_ms"));
            node.setPurgeOutgoingLastRun(rs.getDateTime("purge_outgoing_last_finish"));
            node.setRoutingAverageMs(rs.getLong("routing_average_run_ms"));
            node.setRoutingLastMs(rs.getLong("routing_last_run_ms"));
            node.setRoutingLastRun(rs.getDateTime("routing_last_finish"));
            node.setSymDataSize(rs.getLong("sym_data_size"));
            return node;
        }
    }

    class NodeSecurityRowMapper implements ISqlRowMapper<NodeSecurity> {
        public NodeSecurity mapRow(Row rs) {
            NodeSecurity nodeSecurity = new NodeSecurity();
            nodeSecurity.setNodeId(rs.getString("node_id"));
            nodeSecurity.setNodePassword(filterPasswordOnRenderIfNeeded(rs.getString("node_password"), nodeSecurity.getNodeId()));
            nodeSecurity.setRegistrationEnabled(rs.getBoolean("registration_enabled"));
            nodeSecurity.setRegistrationTime(rs.getDateTime("registration_time"));
            nodeSecurity.setRegistrationNotBefore(rs.getDateTime("registration_not_before"));
            nodeSecurity.setRegistrationNotAfter(rs.getDateTime("registration_not_after"));
            nodeSecurity.setInitialLoadEnabled(rs.getBoolean("initial_load_enabled"));
            nodeSecurity.setInitialLoadTime(rs.getDateTime("initial_load_time"));
            nodeSecurity.setInitialLoadEndTime(rs.getDateTime("initial_load_end_time"));
            nodeSecurity.setCreatedAtNodeId(rs.getString("created_at_node_id"));
            nodeSecurity.setRevInitialLoadEnabled(rs.getBoolean("rev_initial_load_enabled"));
            nodeSecurity.setRevInitialLoadTime(rs.getDateTime("rev_initial_load_time"));
            nodeSecurity.setInitialLoadId(rs.getLong("initial_load_id"));
            nodeSecurity.setInitialLoadCreateBy(rs.getString("initial_load_create_by"));
            nodeSecurity.setRevInitialLoadId(rs.getLong("rev_initial_load_id"));
            nodeSecurity.setRevInitialLoadCreateBy(rs.getString("rev_initial_load_create_by"));
            nodeSecurity.setFailedLogins(rs.getInt("failed_logins"));
            if (rs.containsKey("partial_load_id")) {
                nodeSecurity.setPartialLoadId(rs.getLong("partial_load_id"));
                nodeSecurity.setPartialLoadTime(rs.getDateTime("partial_load_time"));
                nodeSecurity.setPartialLoadEndTime(rs.getDateTime("partial_load_end_time"));
                nodeSecurity.setPartialLoadCreateBy(rs.getString("partial_load_create_by"));
            }
            return nodeSecurity;
        }
    }

    static class NodeHostRowMapper implements ISqlRowMapper<NodeHost> {
        public NodeHost mapRow(Row rs) {
            NodeHost nodeHost = new NodeHost();
            nodeHost.setNodeId(rs.getString("node_id"));
            nodeHost.setHostName(rs.getString("host_name"));
            nodeHost.setInstanceId(rs.getString("instance_id"));
            nodeHost.setIpAddress(rs.getString("ip_address"));
            nodeHost.setOsUser(rs.getString("os_user"));
            nodeHost.setOsName(rs.getString("os_name"));
            nodeHost.setOsArch(rs.getString("os_arch"));
            nodeHost.setOsVersion(rs.getString("os_version"));
            nodeHost.setAvailableProcessors(rs.getInt("available_processors"));
            nodeHost.setFreeMemoryBytes(rs.getLong("free_memory_bytes"));
            nodeHost.setTotalMemoryBytes(rs.getLong("total_memory_bytes"));
            nodeHost.setMaxMemoryBytes(rs.getLong("max_memory_bytes"));
            nodeHost.setJavaVersion(rs.getString("java_version"));
            nodeHost.setJavaVendor(rs.getString("java_vendor"));
            nodeHost.setJdbcVersion(rs.getString("jdbc_version"));
            nodeHost.setSymmetricVersion(rs.getString("symmetric_version"));
            nodeHost.setTimezoneOffset(rs.getString("timezone_offset"));
            nodeHost.setHeartbeatTime(rs.getDateTime("heartbeat_time"));
            nodeHost.setLastRestartTime(rs.getDateTime("last_restart_time"));
            nodeHost.setCreateTime(rs.getDateTime("create_time"));
            return nodeHost;
        }
    }

    public AuthenticationStatus getAuthenticationStatus(String nodeId, String securityToken) {
        AuthenticationStatus retVal = AuthenticationStatus.ACCEPTED;
        Node node = findNode(nodeId, true);
        if (node == null) {
            node = findNode(nodeId, false);
        }
        if (node == null) {
            retVal = AuthenticationStatus.REGISTRATION_REQUIRED;
        } else if (!syncEnabled(node)) {
            if (registrationOpen(node)) {
                retVal = AuthenticationStatus.REGISTRATION_REQUIRED;
            } else {
                retVal = AuthenticationStatus.SYNC_DISABLED;
            }
        } else if (!isNodeAuthorized(nodeId, securityToken)) {
            if (isNodePasswordFailedDecrypt(nodeId)) {
                retVal = AuthenticationStatus.FAILED_DECRYPT;
            } else if (isNodeAuthorizationLocked(nodeId)) {
                retVal = AuthenticationStatus.LOCKED;
            } else {
                retVal = AuthenticationStatus.FORBIDDEN;
            }
        }
        return retVal;
    }

    public void resetNodeFailedLogins(String nodeId) {
        if (parameterService.getInt(ParameterConstants.NODE_PASSWORD_FAILED_ATTEMPTS) >= 0) {
            Map<String, NodeSecurity> nodeSecurities = findAllNodeSecurity(true);
            NodeSecurity nodeSecurity = nodeSecurities.get(nodeId);
            if (nodeSecurity != null && nodeSecurity.getFailedLogins() > 0) {
                nodeSecurity.setFailedLogins(0);
                nodeSecurity = findNodeSecurity(nodeId);
                if (nodeSecurity != null && nodeSecurity.getFailedLogins() > 0) {
                    nodeSecurity.setFailedLogins(0);
                    updateNodeSecurity(nodeSecurity);
                }
            }
        }
    }

    public void incrementNodeFailedLogins(String nodeId) {
        int maxFailedAttempts = parameterService.getInt(ParameterConstants.NODE_PASSWORD_FAILED_ATTEMPTS);
        if (maxFailedAttempts >= 0) {
            NodeSecurity nodeSecurity = findNodeSecurity(nodeId);
            if (nodeSecurity != null) {
                if (nodeSecurity.getFailedLogins() < maxFailedAttempts) {
                    nodeSecurity.setFailedLogins(nodeSecurity.getFailedLogins() + 1);
                    updateNodeSecurity(nodeSecurity);
                    Map<String, NodeSecurity> cache = findAllNodeSecurity(true);
                    NodeSecurity cacheSecurity = cache.get(nodeId);
                    if (cacheSecurity != null) {
                        cacheSecurity.setFailedLogins(nodeSecurity.getFailedLogins());
                    }
                }
            }
        }
    }

    protected boolean syncEnabled(Node node) {
        boolean syncEnabled = false;
        if (node != null) {
            syncEnabled = node.isSyncEnabled();
        }
        return syncEnabled;
    }

    protected boolean registrationOpen(Node node) {
        NodeSecurity security = findNodeSecurity(node.getNodeId());
        if (security != null) {
            return security.isRegistrationEnabled();
        }
        return false;
    }
}
