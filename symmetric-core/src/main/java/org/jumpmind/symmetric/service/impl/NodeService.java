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
import org.jumpmind.symmetric.cache.ClusteredCacheManager;
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
    private Map<String, Node> nodeCache = new HashMap<>();
    private long nodeCacheTime;
    private INodePasswordFilter nodePasswordFilter;
    private NodeHost nodeHostForCurrentNode = null;
    private ICacheManager cacheManager;
    private static final String SELECT_NODE_PREFIX_SQL = "selectNodePrefixSql";
    private static final String SELECT_NODE_SECURITY_PREFIX_SQL = "selectNodeSecuritySql";
    private static final String FIND_NODE_HEARTBEATS_SQL = "findNodeHeartbeatsSql";
    private static final String NODE_ID = "node_id";
    private static final String TIMEZONE_OFFSET = "timezone_offset";
    private static final String HEARTBEAT_TIME = "heartbeat_time";

    public NodeService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        this.cacheManager = engine.getCacheManager();
        extensionService = engine.getExtensionService();
        extensionService.addExtensionPoint(new DefaultNodeIdCreator(parameterService, this, engine.getSecurityService()));
        setSqlMap(new NodeServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    @Override
    public String findSymmetricVersion() {
        return (String) sqlTemplate.queryForObject(getSql("findSymmetricVersionSql"), String.class);
    }

    @Override
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

    @Override
    public Collection<Node> findEnabledNodesFromNodeGroup(String nodeGroupId) {
        return cacheManager.getNodesByGroup(nodeGroupId);
    }

    @Override
    public Collection<Node> getEnabledNodesFromDatabase() {
        return sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findEnabledNodes"), new NodeRowMapper());
    }

    @Override
    public Set<Node> findNodesThatOriginatedFromNodeId(String originalNodeId) {
        return findNodesThatOriginatedFromNodeId(originalNodeId, true);
    }

    @Override
    public Collection<Node> findNodesWithOpenRegistration() {
        return sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findNodesWithOpenRegistrationSql"), new NodeRowMapper());
    }

    @Override
    public Set<Node> findNodesThatOriginatedFromNodeId(String originalNodeId, boolean recursive) {
        Set<Node> all = new HashSet<>();
        List<Node> list = sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findNodesCreatedByMeSql"), new NodeRowMapper(), originalNodeId);
        if (!list.isEmpty()) {
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
    @Override
    public Node findNode(String id) {
        List<Node> list = sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findNodeSql"), new NodeRowMapper(), id);
        return (Node) getFirstEntry(list);
    }

    @Override
    public Node findNodeInCacheOnly(String id) {
        return nodeCache.get(id);
    }

    @Override
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

    @Override
    public void flushNodeCache() {
        nodeCacheTime = 0;
    }

    @Override
    public Node findNodeByExternalId(String nodeGroupId, String externalId) {
        List<Node> list = sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findNodeByExternalIdSql"), new NodeRowMapper(), nodeGroupId,
                externalId);
        return (Node) getFirstEntry(list);
    }

    @Override
    public void ignoreNodeChannelForExternalId(boolean enabled, String channelId, String nodeGroupId, String externalId,
            String targetNodeGroupId, String targetExternalId) {
        Node node = findNodeByExternalId(nodeGroupId, externalId);
        Node targetNode = findNodeByExternalId(targetNodeGroupId, targetExternalId);
        if (sqlTemplate.update(getSql("nodeChannelControlIgnoreSql"),
                enabled ? 1 : 0, node.getNodeId(), targetNode.getNodeId(), channelId) <= 0) {
            sqlTemplate.update(getSql("insertNodeChannelControlSql"),
                    node.getNodeId(), targetNode.getNodeId(), channelId, enabled ? 1 : 0, 0);
        }
    }

    @Override
    public List<NodeHost> findNodeHosts(String nodeId) {
        return sqlTemplate.query(getSql("selectNodeHostPrefixSql", "selectNodeHostByNodeIdSql"), new NodeHostRowMapper(), nodeId);
    }

    @Override
    public void deleteNodeHost(String nodeId) {
        platform.getSqlTemplate().update(getSql("deleteNodeHostSql"), nodeId);
    }

    @Override
    public void deleteNodeHostInstance(String nodeId, String instanceId) {
        platform.getSqlTemplate().update(getSql("deleteNodeHostInstanceSql"), nodeId, instanceId);
    }

    @Override
    public void updateNodeHost(NodeHost nodeHost) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            updateNodeHost(transaction, nodeHost);
            transaction.commit();
        } finally {
            close(transaction);
        }
    }

    @Override
    public void updateNodeHost(ISqlTransaction transaction, NodeHost nodeHost) {
        String hostName = StringUtils.left(nodeHost.getHostName(), 60);
        int updateCount = transaction.prepareAndExecute(getSql("updateNodeHostSql"),
                nodeHost.getIpAddress(), nodeHost.getInstanceId(), nodeHost.getClusterPartitionId(), nodeHost.getOsUser(),
                nodeHost.getOsName(), nodeHost.getOsArch(), nodeHost.getOsVersion(),
                nodeHost.getAvailableProcessors(), nodeHost.getFreeMemoryBytes(),
                nodeHost.getTotalMemoryBytes(), nodeHost.getMaxMemoryBytes(), nodeHost.getJavaVersion(),
                nodeHost.getJavaVendor(), nodeHost.getSecurityMode(), nodeHost.getJdbcVersion(), nodeHost.getSymmetricVersion(),
                nodeHost.getTimezoneOffset(), nodeHost.getHeartbeatTime(), nodeHost.getLastRestartTime(),
                nodeHost.getNodeId(), hostName);
        if (updateCount <= 0) {
            log.debug("NodeHost update returned {} rows, inserting new record. nodeId={}, hostname={}, ip={}",
                    updateCount, nodeHost.getNodeId(), hostName, nodeHost.getIpAddress());
            transaction.prepareAndExecute(getSql("insertNodeHostSql"),
                    nodeHost.getIpAddress(), nodeHost.getInstanceId(), nodeHost.getClusterPartitionId(), nodeHost.getOsUser(),
                    nodeHost.getOsName(), nodeHost.getOsArch(), nodeHost.getOsVersion(),
                    nodeHost.getAvailableProcessors(), nodeHost.getFreeMemoryBytes(),
                    nodeHost.getTotalMemoryBytes(), nodeHost.getMaxMemoryBytes(), nodeHost.getJavaVersion(),
                    nodeHost.getJavaVendor(), nodeHost.getSecurityMode(), nodeHost.getJdbcVersion(), nodeHost.getSymmetricVersion(),
                    nodeHost.getTimezoneOffset(), nodeHost.getHeartbeatTime(), nodeHost.getLastRestartTime(),
                    new Date(), nodeHost.getNodeId(), hostName);
            log.info("Inserted new NodeHost record. nodeId={}, hostname={}, ip={}, heartbeat={}",
                    nodeHost.getNodeId(), hostName, nodeHost.getIpAddress(), nodeHost.getHeartbeatTime());
        } else {
            log.debug("Updated existing NodeHost record. nodeId={}, hostname={}, ip={}, heartbeat={}",
                    nodeHost.getNodeId(), hostName, nodeHost.getIpAddress(), nodeHost.getHeartbeatTime());
        }
    }

    @Override
    public void updateNodeHostForCurrentNode() {
        if (nodeHostForCurrentNode == null) {
            nodeHostForCurrentNode = new NodeHost(findIdentityNodeId(), engine.getClusterService().getInstanceId());
        }
        nodeHostForCurrentNode.refresh(platform, engine.getClusterService().getInstanceId(), engine.getClusterService().getServerId(),
                ClusteredCacheManager.getInstance().getClusterPartitionId());
        log.debug("Updating NodeHost for current node: nodeId={}, hostname={}, ip={}, partition={}",
                nodeHostForCurrentNode.getNodeId(), nodeHostForCurrentNode.getHostName(),
                nodeHostForCurrentNode.getIpAddress(), nodeHostForCurrentNode.getClusterPartitionId());
        updateNodeHost(nodeHostForCurrentNode);
    }

    @Override
    public void updateNodeHostForCurrentNode(ISqlTransaction transaction) {
        if (nodeHostForCurrentNode == null) {
            nodeHostForCurrentNode = new NodeHost(findIdentityNodeId(), engine.getClusterService().getInstanceId());
        }
        nodeHostForCurrentNode.refresh(platform, engine.getClusterService().getInstanceId(), engine.getClusterService().getServerId(),
                ClusteredCacheManager.getInstance().getClusterPartitionId());
        updateNodeHost(transaction, nodeHostForCurrentNode);
    }

    @Override
    public void updateNodeHostForCurrentNode(boolean bypassTrigger) {
        if (!bypassTrigger) {
            updateNodeHostForCurrentNode();
            return;
        }
        String nodeId = findIdentityNodeId();
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            symmetricDialect.disableSyncTriggers(transaction, nodeId);
            updateNodeHostForCurrentNode(transaction);
            symmetricDialect.enableSyncTriggers(transaction);
            transaction.commit();
        } catch (Exception ex) {
            log.warn("Failed to update node host heartbeat for current node, but will try again next time.", ex);
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public void deleteNode(String nodeId, boolean syncChange) {
        deleteNode(nodeId, null, syncChange);
    }

    @Override
    public synchronized void deleteNode(String nodeId, String targetNodeId, boolean syncChange) {
        log.info("Unregistering node {} and removing it from database", nodeId);
        if (StringUtils.isNotBlank(nodeId)) {
            interruptThreads(nodeId);
            ISqlTransaction transaction = null;
            try {
                transaction = sqlTemplate.startSqlTransaction();
                enableDisableSync(syncChange, false, nodeId, transaction);
                deleteNodeIdentity(nodeId, transaction);
                transaction.prepareAndExecute(getSql("deleteNodeSecuritySql"), nodeId);
                transaction.prepareAndExecute(getSql("deleteNodeHostSql"), nodeId);
                transaction.prepareAndExecute(getSql("deleteNodeSql"), nodeId);
                transaction.prepareAndExecute(getSql("deleteNodeChannelCtlSql"), nodeId, nodeId);
                transaction.prepareAndExecute(getSql("deleteIncomingErrorSql"), StringUtils.isNotBlank(targetNodeId) ? targetNodeId : nodeId);
                transaction.prepareAndExecute(getSql("deleteExtractRequestSql"), nodeId, nodeId);
                transaction.prepareAndExecute(getSql("deleteNodeCommunicationSql"), StringUtils.isNotBlank(targetNodeId) ? targetNodeId : nodeId);
                transaction.prepareAndExecute(getSql("deleteTableReloadRequestSql"), nodeId, nodeId);
                transaction.prepareAndExecute(getSql("cancelTableReloadStatusSql"), new Date(), new Date(), nodeId, nodeId);
                transaction.prepareAndExecute(getSql("setOutgoingBatchOkSql"), StringUtils.isNotBlank(targetNodeId) ? targetNodeId : nodeId);
                transaction.prepareAndExecute(getSql("deleteIncomingBatchSql"), StringUtils.isNotBlank(targetNodeId) ? targetNodeId : nodeId);
                transaction.commit();
            } catch (Error | RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                enableDisableSync(syncChange, true, nodeId, transaction);
                close(transaction);
            }
        }
    }

    private void deleteNodeIdentity(String nodeId, ISqlTransaction transaction) {
        String myNode = findIdentityNodeId();
        if (StringUtils.isNotBlank(myNode) && myNode.equals(nodeId)) {
            transaction.prepareAndExecute(getSql("deleteNodeIdentitySql"));
            cachedNodeIdentity = null;
        }
    }

    private void enableDisableSync(boolean syncChange, boolean enable, String nodeId, ISqlTransaction transaction) {
        if (!syncChange) {
            if (enable) {
                symmetricDialect.enableSyncTriggers(transaction);
            } else {
                symmetricDialect.disableSyncTriggers(transaction, nodeId);
            }
        }
    }

    private void interruptThreads(String nodeId) {
        for (ProcessInfo info : engine.getStatisticManager().getProcessInfos()) {
            if ((info.getTargetNodeId() != null && info.getTargetNodeId().equals(nodeId)) ||
                    (info.getSourceNodeId() != null && info.getSourceNodeId().equals(nodeId))) {
                log.info("Sending interrupt to {},batchId={}", info.getKey(), info.getCurrentBatchId());
                info.getThread().interrupt();
            }
        }
    }

    @Override
    public void insertNodeIdentity(String nodeId) {
        sqlTemplate.update(getSql("insertNodeIdentitySql"), nodeId);
    }

    @Override
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

    @Override
    public void insertNodeGroup(String groupId, String description) {
        if (sqlTemplate.queryForInt(getSql("doesNodeGroupExistSql"), groupId) == 0) {
            sqlTemplate.update(getSql("insertNodeGroupSql"), description, groupId);
        }
    }

    @Override
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
        }
        flushNodeGroupCache();
        flushNodeCache();
        cacheManager.flushTargetNodesCache();
        cacheManager.flushSourceNodesCache();
    }

    public boolean updateNode(Node node) {
        return sqlTemplate.update(
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
    }

    protected <T> T getFirstEntry(List<T> list) {
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    @Override
    public Node getCachedIdentity() {
        return cachedNodeIdentity;
    }

    @Override
    public Node findIdentity() {
        return findIdentity(true);
    }

    @Override
    public Node findIdentity(boolean useCache) {
        return findIdentity(useCache, true);
    }

    @Override
    public Node findIdentity(boolean useCache, boolean logSqlError) {
        if (cachedNodeIdentity == null || !useCache) {
            try {
                List<Node> list = sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findNodeIdentitySql"), new NodeRowMapper());
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

    @Override
    public List<Node> findNodesToPull() {
        return findSourceNodesFor(NodeGroupLinkAction.W);
    }

    @Override
    public List<Node> findNodesWhoPushToMe() {
        return findSourceNodesFor(NodeGroupLinkAction.P);
    }

    @Override
    public List<Node> findNodesToPushTo() {
        return findTargetNodesFor(NodeGroupLinkAction.P);
    }

    @Override
    public List<Node> findNodesWhoPullFromMe() {
        return findTargetNodesFor(NodeGroupLinkAction.W);
    }

    @Override
    public List<Node> findSourceNodesFor(NodeGroupLinkAction eventAction) {
        Node node = findIdentity();
        if (node != null) {
            return cacheManager.getSourceNodesCache(eventAction, node);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Node> getSourceNodesFromDatabase(NodeGroupLinkAction eventAction, Node node) {
        if (node != null) {
            return sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findNodesWhoTargetMeSql"),
                    new NodeRowMapper(), node.getNodeGroupId(), eventAction.name());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Node> findTargetNodesFor(NodeGroupLinkAction eventAction) {
        Node node = findIdentity();
        if (node != null) {
            return cacheManager.getTargetNodesCache(eventAction, node);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Node> getTargetNodesFromDatabase(NodeGroupLinkAction eventAction, Node node) {
        if (node != null) {
            return sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findNodesWhoITargetSql"),
                    new NodeRowMapper(), node.getNodeGroupId(), eventAction.name());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void flushNodeGroupCache() {
        cacheManager.flushSourceNodesCache();
        cacheManager.flushTargetNodesCache();
    }

    @Override
    public List<String> findAllExternalIds() {
        return sqlTemplate.query(getSql("selectExternalIdsSql"), new StringMapper());
    }

    @Override
    public List<Node> findAllNodes() {
        return sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL), new NodeRowMapper());
    }

    @Override
    public List<Node> findAllNodes(boolean useCache) {
        if (useCache) {
            findNode(findIdentityNodeId(), true);
            return new ArrayList<>(nodeCache.values());
        } else {
            return findAllNodes();
        }
    }

    @Override
    public Map<String, Node> findAllNodesAsMap() {
        List<Node> nodes = findAllNodes();
        Map<String, Node> nodeMap = new HashMap<>(nodes.size());
        for (Node node : nodes) {
            nodeMap.put(node.getNodeId(), node);
        }
        return nodeMap;
    }

    @Override
    public List<Node> findFilteredNodesWithLimit(int offset, int limit, List<FilterCriterion> filter,
            String orderColumn, String orderDirection) {
        String where = filter != null ? buildWhere(filter) : null;
        Map<String, Object> params = filter != null ? buildParams(filter) : new HashMap<>();
        String orderBy = buildOrderBy(orderColumn, orderDirection);
        String sql = getSql(SELECT_NODE_PREFIX_SQL, where, orderBy);
        List<Node> nodeList;
        if (platform.supportsLimitOffset()) {
            sql = platform.massageForLimitOffset(sql, limit, offset);
            nodeList = sqlTemplateDirty.query(sql, new NodeRowMapper(), params);
        } else {
            nodeList = getNodeListUsingLimitAndOffset(sql, params, offset, limit);
        }
        return nodeList;
    }

    private List<Node> getNodeListUsingLimitAndOffset(String sql, Map<String, Object> params, int offset, int limit) {
        List<Node> nodeList = new ArrayList<>();
        ISqlReadCursor<Node> cursor = sqlTemplateDirty.queryForCursor(sql, new NodeRowMapper(), params);
        try {
            Node next = null;
            nodeList = new ArrayList<>();
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
        return nodeList;
    }

    @Override
    public int countFilteredNodes(List<FilterCriterion> filter) {
        String where = filter != null ? buildWhere(filter) : null;
        Map<String, Object> params = filter != null ? buildParams(filter) : new HashMap<>();
        String sql = "select count(*) from " + TableConstants.getTableName(parameterService.getTablePrefix(), TableConstants.SYM_NODE) + where;
        return sqlTemplate.queryForInt(sql, params);
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
            where.append(prefix).append(" :").append(id++);
        }
        if (!where.isEmpty()) {
            where.insert(0, " where ");
        }
        return where.toString();
    }

    protected Map<String, Object> buildParams(List<FilterCriterion> filter) {
        Map<String, Object> params = new HashMap<>();
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

    @Override
    public NetworkedNode getRootNetworkedNode() {
        Map<String, Node> nodes = findAllNodesAsMap();
        Map<String, NetworkedNode> leaves = new HashMap<>(nodes.size());
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

    @Override
    public Node findRootNode() {
        List<Node> nodeList = sqlTemplate.query(getSql(SELECT_NODE_PREFIX_SQL, "findRootNodeSql"), new NodeRowMapper());
        if (!nodeList.isEmpty()) {
            return nodeList.get(0);
        }
        return null;
    }

    /**
     * Lookup a node_security in the database, which contains private information used to authenticate.
     */
    @Override
    public NodeSecurity findNodeSecurity(String id) {
        return findNodeSecurity(id, false);
    }

    @Override
    public NodeSecurity findNodeSecurity(String nodeId, boolean useCache) {
        if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) && useCache) {
            Map<String, NodeSecurity> nodeSecurities = findAllNodeSecurity(true);
            return nodeSecurities.get(nodeId);
        } else {
            List<NodeSecurity> list = sqlTemplate.query(getSql(SELECT_NODE_SECURITY_PREFIX_SQL, "findNodeSecurityByNodeIdSql"),
                    new NodeSecurityRowMapper(), new Object[] { nodeId }, new int[] { Types.VARCHAR });
            return getFirstEntry(list);
        }
    }

    @Override
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

    @Override
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
        sqlTemplate.update(getSql("insertNodeSecuritySql"), id, password, null);
        flushNodeAuthorizedCache();
    }

    @Override
    public void deleteNodeSecurity(String nodeId) {
        sqlTemplate.update(getSql("deleteNodeSecuritySql"), nodeId);
        flushNodeAuthorizedCache();
    }

    @Override
    public List<NodeSecurity> findNodeSecurityWithLoadEnabled() {
        if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            return sqlTemplate.query(getSql(SELECT_NODE_SECURITY_PREFIX_SQL, "findNodeSecurityWithLoadEnabledSql"), new NodeSecurityRowMapper());
        } else {
            List<NodeSecurity> list = new ArrayList<>();
            for (NodeSecurity nodeSecurity : findAllNodeSecurity(true).values()) {
                if (nodeSecurity.isInitialLoadEnabled() || nodeSecurity.isRevInitialLoadEnabled()) {
                    list.add(nodeSecurity);
                }
            }
            return list;
        }
    }

    @Override
    public synchronized Map<String, NodeSecurity> findAllNodeSecurity(boolean useCache) {
        long maxSecurityCacheTime = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_SECURITY_IN_MS);
        Map<String, NodeSecurity> all = securityCache;
        if (all == null || System.currentTimeMillis() - securityCacheTime >= maxSecurityCacheTime || securityCacheTime == 0 || !useCache) {
            all = (Map<String, NodeSecurity>) sqlTemplate.queryForMap(getSql(SELECT_NODE_SECURITY_PREFIX_SQL), new NodeSecurityRowMapper(),
                    NODE_ID);
            securityCache = all;
            securityCacheTime = System.currentTimeMillis();
        }
        return all;
    }

    /**
     * Check that the given node and password match in the node_security table. A node must authenticate before it's allowed to sync data.
     */
    @Override
    public boolean isNodeAuthorized(String nodeId, String password) {
        int maxFailedLogins = parameterService.getInt(ParameterConstants.NODE_PASSWORD_FAILED_ATTEMPTS);
        Map<String, NodeSecurity> nodeSecurities = findAllNodeSecurity(true);
        NodeSecurity nodeSecurity = nodeSecurities.get(nodeId);
        return nodeSecurity != null && !nodeId.equals(findIdentityNodeId()) && StringUtils.isNotBlank(nodeSecurity.getNodePassword())
                && nodeSecurity.getNodePassword().equals(password)
                && (maxFailedLogins <= 0 || nodeSecurity.getFailedLogins() < maxFailedLogins)
                || nodeSecurity.isRegistrationEnabled();
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

    @Override
    public void flushNodeAuthorizedCache() {
        securityCacheTime = 0;
    }

    @Override
    public boolean updateNodeSecurity(NodeSecurity security) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            boolean updated = updateNodeSecurity(transaction, security);
            transaction.commit();
            return updated;
        } catch (Error | RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
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

    @Override
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

    @Override
    public boolean setInitialLoadEnabled(String nodeId, boolean initialLoadEnabled, boolean syncChange, long loadId, String createBy) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            boolean updated = setInitialLoadEnabled(transaction, nodeId, initialLoadEnabled, syncChange, loadId, createBy);
            transaction.commit();
            return updated;
        } catch (Error | RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
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
        } catch (Error | RuntimeException ex) {
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

    @Override
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

    @Override
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
        } catch (Error | RuntimeException ex) {
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

    @Override
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

    @Override
    public boolean setReverseInitialLoadEnabled(String nodeId, boolean initialLoadEnabled, boolean syncChange, long loadId, String createBy) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            boolean updated = setReverseInitialLoadEnabled(transaction, nodeId, initialLoadEnabled, syncChange, loadId, createBy);
            transaction.commit();
            return updated;
        } catch (Error | RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public boolean isExternalIdRegistered(String nodeGroupId, String externalId) {
        return sqlTemplate.queryForInt(getSql("isNodeRegisteredSql"), nodeGroupId, externalId) > 0;
    }

    @Override
    public boolean isDataLoadCompleted() {
        return getNodeStatus() == NodeStatus.DATA_LOAD_COMPLETED;
    }

    @Override
    public boolean isDataLoadCompleted(String nodeId) {
        return getNodeStatus(nodeId) == NodeStatus.DATA_LOAD_COMPLETED;
    }

    @Override
    public boolean isDataLoadStarted() {
        return getNodeStatus() == NodeStatus.DATA_LOAD_STARTED;
    }

    @Override
    public boolean isDataLoadStarted(String nodeId) {
        return getNodeStatus(nodeId) == NodeStatus.DATA_LOAD_STARTED;
    }

    @Override
    public boolean isRegistrationServer() {
        return parameterService.isRegistrationServer();
    }

    @Override
    public NodeStatus getNodeStatus() {
        return getNodeStatus(findIdentityNodeId());
    }

    @Override
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

    @Override
    public void checkForOfflineNodes() {
        long offlineNodeDetectionMinutes = parameterService.getLong(ParameterConstants.OFFLINE_NODE_DETECTION_PERIOD_MINUTES);
        List<IOfflineServerListener> offlineServerListeners = extensionService.getExtensionPointList(IOfflineServerListener.class);
        // Only check for offline nodes if there is a listener and the
        // offline detection period is a positive value. A negative value
        // disables the feature.
        if (offlineServerListeners != null && offlineNodeDetectionMinutes > 0) {
            List<Node> list = findOfflineNodes();
            if (!list.isEmpty()) {
                fireOffline(list);
            }
        }
    }

    @Override
    public List<Node> findOfflineNodes() {
        return findOfflineNodes(parameterService.getLong(ParameterConstants.OFFLINE_NODE_DETECTION_PERIOD_MINUTES));
    }

    @Override
    public List<Node> findOfflineNodes(long minutesOffline) {
        List<Node> offlineNodeList = new ArrayList<>();
        Node myNode = findIdentity();
        long restartDelayMinutes = parameterService.getLong(ParameterConstants.OFFLINE_NODE_DETECTION_RESTART_MINUTES);
        Date lastRestartTime = engine.getLastRestartTime() != null ? engine.getLastRestartTime() : new Date();
        if (myNode != null && System.currentTimeMillis() - lastRestartTime.getTime() > restartDelayMinutes * 60000) {
            long offlineNodeDetectionMillis = minutesOffline * 60 * 1000;
            List<Row> list = sqlTemplateDirty.query(getSql(FIND_NODE_HEARTBEATS_SQL));
            for (Row node : list) {
                String nodeId = node.getString(NODE_ID);
                Date time = node.getDateTime(HEARTBEAT_TIME);
                String offset = node.getString(TIMEZONE_OFFSET);
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

    @Override
    public Map<String, Date> findLastHeartbeats() {
        Map<String, Date> dates = new HashMap<>();
        Node myNode = findIdentity();
        if (myNode != null) {
            List<Row> list = sqlTemplateDirty.query(getSql(FIND_NODE_HEARTBEATS_SQL));
            for (Row node : list) {
                String nodeId = node.getString(NODE_ID);
                Date time = node.getDateTime(HEARTBEAT_TIME);
                dates.put(nodeId, time);
            }
        }
        return dates;
    }

    @Override
    public List<String> findOfflineNodeIds(long minutesOffline) {
        List<String> offlineNodeList = new ArrayList<>();
        Node myNode = findIdentity();
        if (myNode != null) {
            long offlineNodeDetectionMillis = minutesOffline * 60 * 1000;
            List<Row> list = sqlTemplateDirty.query(getSql(FIND_NODE_HEARTBEATS_SQL));
            for (Row node : list) {
                String nodeId = node.getString(NODE_ID);
                Date time = node.getDateTime(HEARTBEAT_TIME);
                String offset = node.getString(TIMEZONE_OFFSET);
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
        Map<String, Node> offlineNodesNotUnregistered = new HashMap<>();
        for (IOfflineServerListener listener : extensionService.getExtensionPointList(IOfflineServerListener.class)) {
            for (Node node : offlineClientNodeList) {
                processOfflineNode(myNode, node, offlineNodesNotUnregistered, listener);
            }
        }
        if (!offlineNodesNotUnregistered.isEmpty()) {
            log.info("Watchdog could not disable {} nodes because the nodes do not initiate synchronization with me or the auto.reload parameter is false",
                    offlineNodesNotUnregistered.size());
        }
    }

    private void processOfflineNode(Node myNode, Node node, Map<String, Node> offlineNodesNotUnregistered, IOfflineServerListener listener) {
        if (myNode != null && !myNode.equals(node)) {
            String myNodeId = myNode.getNodeId();
            if (myNodeId.equals(node.getCreatedAtNodeId())) {
                processUnregisterRequest(node, listener, offlineNodesNotUnregistered);
            } else {
                NodeSecurity security = findNodeSecurity(node.getNodeId());
                if (security != null && myNodeId.equals(security.getCreatedAtNodeId())) {
                    processUnregisterRequest(node, listener, offlineNodesNotUnregistered);
                }
            }
        }
    }

    private void processUnregisterRequest(Node node, IOfflineServerListener listener, Map<String, Node> offlineNodesNotUnregistered) {
        if (okToUnregisterNode(node)) {
            listener.clientNodeOffline(node);
        } else {
            offlineNodesNotUnregistered.put(node.getNodeId(), node);
        }
    }

    private boolean okToUnregisterNode(Node node) {
        return nodeInitiatesCommunicationWithMe(node) && engine.getParameterService().is(ParameterConstants.AUTO_RELOAD_ENABLED);
    }

    private boolean nodeInitiatesCommunicationWithMe(Node node) {
        boolean ret = false;
        List<Node> pushToMe = findNodesWhoPushToMe();
        for (Node nd : pushToMe) {
            if (nd.getNodeId().equals(node.getNodeId())) {
                ret = true;
            }
        }
        if (!ret) {
            List<Node> pullFromMe = findNodesWhoPullFromMe();
            for (Node nd : pullFromMe) {
                if (nd.getNodeId().equals(node.getNodeId())) {
                    ret = true;
                }
            }
        }
        return ret;
    }

    public static class NodeRowMapper implements ISqlRowMapper<Node> {
        @Override
        public Node mapRow(Row rs) {
            Node node = new Node();
            node.setNodeId(rs.getString(NODE_ID));
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
        @Override
        public NodeSecurity mapRow(Row rs) {
            NodeSecurity nodeSecurity = new NodeSecurity();
            nodeSecurity.setNodeId(rs.getString(NODE_ID));
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

        private String filterPasswordOnRenderIfNeeded(String password, String nodeId) {
            String s = password;
            if (nodePasswordFilter != null) {
                s = nodePasswordFilter.onNodeSecurityRender(password, nodeId);
            }
            return s;
        }
    }

    static class NodeHostRowMapper implements ISqlRowMapper<NodeHost> {
        @Override
        public NodeHost mapRow(Row rs) {
            NodeHost nodeHost = new NodeHost();
            nodeHost.setNodeId(rs.getString(NODE_ID));
            nodeHost.setHostName(rs.getString("host_name"));
            nodeHost.setInstanceId(rs.getString("instance_id"));
            nodeHost.setClusterPartitionId(rs.getString("cluster_partition_id"));
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
            nodeHost.setSecurityMode(rs.getString("security_mode"));
            nodeHost.setJdbcVersion(rs.getString("jdbc_version"));
            nodeHost.setSymmetricVersion(rs.getString("symmetric_version"));
            nodeHost.setTimezoneOffset(rs.getString(TIMEZONE_OFFSET));
            nodeHost.setHeartbeatTime(rs.getDateTime(HEARTBEAT_TIME));
            nodeHost.setLastRestartTime(rs.getDateTime("last_restart_time"));
            nodeHost.setCreateTime(rs.getDateTime("create_time"));
            return nodeHost;
        }
    }

    @Override
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

    @Override
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

    @Override
    public void incrementNodeFailedLogins(String nodeId) {
        int maxFailedAttempts = parameterService.getInt(ParameterConstants.NODE_PASSWORD_FAILED_ATTEMPTS);
        if (maxFailedAttempts >= 0) {
            NodeSecurity nodeSecurity = findNodeSecurity(nodeId);
            if (nodeSecurity != null && nodeSecurity.getFailedLogins() < maxFailedAttempts) {
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
