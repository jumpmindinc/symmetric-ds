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

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ConfigurationChangedHelper;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.NetworkedNode;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.ITriggerRouterService;

public class ConfigurationChangedDataRouter extends AbstractDataRouter implements IDataRouter, IBuiltInExtensionPoint {
    public static final String ROUTER_TYPE = "configurationChanged";
    protected ISymmetricEngine engine;
    private ConfigurationChangedHelper helper;

    public ConfigurationChangedDataRouter(ISymmetricEngine engine) {
        this.engine = engine;
        helper = new ConfigurationChangedHelper(engine);
    }

    @Override
    public Set<String> routeToNodes(SimpleRouterContext routingContext, DataMetaData dataMetaData,
            Set<Node> possibleTargetNodes, boolean initialLoad, boolean initialLoadSelectUsed,
            TriggerRouter triggerRouter) {
        if (helper.isNewContext(routingContext)) {
            helper.setSyncTriggersAllowed(routingContext, engine.getParameterService().is(ParameterConstants.AUTO_SYNC_TRIGGERS) &&
                    engine.getParameterService().is(ParameterConstants.AUTO_SYNC_TRIGGERS_AFTER_CONFIG_CHANGED));
        }
        helper.handleChange(routingContext, dataMetaData.getTable(), dataMetaData.getData());
        // the list of nodeIds that we will return
        Set<String> nodeIds = new HashSet<String>();
        // the inbound data
        Map<String, String> columnValues = getDataMap(dataMetaData, engine != null ? engine.getSymmetricDialect() : null);
        possibleTargetNodes = helper.filterNodes(possibleTargetNodes, dataMetaData.getTable().getNameLowerCase(), columnValues);
        Node me = findIdentity();
        if (me != null) {
            NetworkedNode rootNetworkedNode = getRootNetworkNodeFromContext(routingContext);
            if (tableMatches(dataMetaData, TableConstants.SYM_NODE) && dataMetaData.getData().getDataEventType() == DataEventType.SQL) {
                if (dataMetaData.getData().getParsedData(CsvData.ROW_DATA).length > 1 && dataMetaData.getData().getParsedData(CsvData.ROW_DATA)[0]
                        .toUpperCase().contains("TABLE")) {
                    helper.setSyncTriggersNeeded(routingContext);
                }
                IConfigurationService configurationService = engine.getConfigurationService();
                for (Node routeToNode : possibleTargetNodes) {
                    if (notRestClient(routeToNode)) {
                        NodeGroupLink link = configurationService.getNodeGroupLinkFor(me.getNodeGroupId(), routeToNode.getNodeGroupId(), false);
                        if (initialLoad || (link != null && link.isSyncSqlEnabled())) {
                            nodeIds.add(routeToNode.getNodeId());
                        }
                    }
                }
            } else if (tableMatches(dataMetaData, TableConstants.SYM_NODE)
                    || tableMatches(dataMetaData, TableConstants.SYM_NODE_SECURITY)
                    || tableMatches(dataMetaData, TableConstants.SYM_NODE_HOST)
                    || tableMatches(dataMetaData, TableConstants.SYM_MONITOR_EVENT)) {
                /*
                 * If this is sym_node or sym_node_security determine which nodes it goes to.
                 */
                routeNodeTables(nodeIds, columnValues, rootNetworkedNode, me, routingContext,
                        dataMetaData, possibleTargetNodes, initialLoad);
            } else if (tableMatches(dataMetaData, TableConstants.SYM_TABLE_RELOAD_REQUEST)
                    || tableMatches(dataMetaData, TableConstants.SYM_TABLE_RELOAD_STATUS)
                    || tableMatches(dataMetaData, TableConstants.SYM_COMPARE_REQUEST)
                    || tableMatches(dataMetaData, TableConstants.SYM_COMPARE_STATUS)
                    || tableMatches(dataMetaData, TableConstants.SYM_COMPARE_TABLE_STATUS)) {
                String sourceNodeId = columnValues.get("SOURCE_NODE_ID");
                String targetNodeId = columnValues.get("TARGET_NODE_ID");
                for (Node nodeThatMayBeRoutedTo : possibleTargetNodes) {
                    if (notRestClient(nodeThatMayBeRoutedTo)
                            && (nodeThatMayBeRoutedTo.getNodeId().equals(sourceNodeId) ||
                                    nodeThatMayBeRoutedTo.getNodeId().equals(targetNodeId))) {
                        nodeIds.add(nodeThatMayBeRoutedTo.getNodeId());
                    }
                }
            } else if (tableMatches(dataMetaData, TableConstants.SYM_EXTRACT_REQUEST)
                    || tableMatches(dataMetaData, TableConstants.SYM_OUTGOING_ERROR)
                    || tableMatches(dataMetaData, TableConstants.SYM_INCOMING_ERROR)) {
                String targetNodeId = columnValues.get("NODE_ID");
                for (Node nodeThatMayBeRoutedTo : possibleTargetNodes) {
                    if (notRestClient(nodeThatMayBeRoutedTo) && nodeThatMayBeRoutedTo.getNodeId().equals(targetNodeId)) {
                        nodeIds.add(nodeThatMayBeRoutedTo.getNodeId());
                    }
                }
            } else {
                IConfigurationService configurationService = engine.getConfigurationService();
                for (Node nodeThatMayBeRoutedTo : possibleTargetNodes) {
                    if (notRestClient(nodeThatMayBeRoutedTo)
                            && (initialLoad || !isSameNumberOfLinksAwayFromRoot(nodeThatMayBeRoutedTo,
                                    rootNetworkedNode, me))) {
                        NodeGroupLink link = configurationService.getNodeGroupLinkFor(
                                me.getNodeGroupId(), nodeThatMayBeRoutedTo.getNodeGroupId(), false);
                        if (initialLoad || (link != null && link.isSyncConfigEnabled())) {
                            nodeIds.add(nodeThatMayBeRoutedTo.getNodeId());
                        }
                    }
                }
                if (tableMatches(dataMetaData, TableConstants.SYM_NODE_GROUP_LINK)) {
                    if (dataMetaData.getData().getDataEventType() == DataEventType.INSERT) {
                        if (!initialLoad) {
                            if (!isConfigDataMetaDataAlreadyHandled(dataMetaData, routingContext)) {
                                buildReloadEvents(dataMetaData, columnValues);
                                addConfigDataMetaData(dataMetaData, routingContext);
                            }
                        }
                    }
                }
            }
        }
        return nodeIds;
    }

    private boolean isConfigDataMetaDataAlreadyHandled(DataMetaData dataMetaData, SimpleRouterContext routingContext) {
        boolean ret = false;
        if (routingContext instanceof ChannelRouterContext) {
            ChannelRouterContext channelRoutingContext = (ChannelRouterContext) routingContext;
            if (channelRoutingContext.getConfigDataMetaData(dataMetaData.getData().getDataId()) != null) {
                ret = true;
            }
        }
        return ret;
    }

    private void addConfigDataMetaData(DataMetaData dataMetaData, SimpleRouterContext routingContext) {
        if (routingContext instanceof ChannelRouterContext) {
            ChannelRouterContext channelRoutingContext = (ChannelRouterContext) routingContext;
            channelRoutingContext.addConfigDataMetaData(dataMetaData);
        }
    }

    private void buildReloadEvents(DataMetaData dataMetaData, Map<String, String> columnValues) {
        String symTablePrefix = engine.getTablePrefix();
        String tableName = dataMetaData.getTable().getName();
        if (TableConstants.getTableName(symTablePrefix, TableConstants.SYM_NODE_GROUP_LINK).equalsIgnoreCase(tableName)) {
            if (engine.getParameterService().isRegistrationServer()) {
                if (dataMetaData.getData().getDataEventType() == DataEventType.INSERT) {
                    Node me = engine.getNodeService().findIdentity();
                    String targetNodeGroupId = columnValues.get("TARGET_NODE_GROUP_ID");
                    String sourceNodeGroupId = columnValues.get("SOURCE_NODE_GROUP_ID");
                    log.info("Inserting reload events for sym_node and sym_node_security for source_node_group_id=" + sourceNodeGroupId
                            + " and target_node_group_id=" + targetNodeGroupId);
                    Collection<Node> targetNodes = engine.getNodeService().findEnabledNodesFromNodeGroup(targetNodeGroupId);
                    Collection<Node> sourceNodes = engine.getNodeService().findEnabledNodesFromNodeGroup(sourceNodeGroupId);
                    NodeGroupLink nodeGroupLink = new NodeGroupLink(sourceNodeGroupId, targetNodeGroupId);
                    Date createTime = new Date();
                    List<TriggerRouter> triggerRouterList = engine.getTriggerRouterService().buildTriggerRoutersForSymmetricTables(
                            Version.version(), nodeGroupLink);
                    // send sym_node
                    TriggerRouter triggerRouter = findTriggerRouter(triggerRouterList, TableConstants.SYM_NODE, symTablePrefix);
                    if (triggerRouter != null) {
                        // send nodes in sourcenodegroupid to target nodes
                        String initialLoadSelect = String.format(engine.getDataService().findNodeIdsByNodeGroupId(), "'" + sourceNodeGroupId + "'");
                        insertReloadEvents(triggerRouter, initialLoadSelect, sourceNodeGroupId, targetNodeGroupId,
                                createTime, me, targetNodes);
                        // send nodes in targetnodegroupid to source nodes
                        initialLoadSelect = String.format(engine.getDataService().findNodeIdsByNodeGroupId(), "'" + targetNodeGroupId + "'");
                        insertReloadEvents(triggerRouter, initialLoadSelect, sourceNodeGroupId, targetNodeGroupId,
                                createTime, me, sourceNodes);
                    }
                    // send sym_node_security
                    triggerRouter = findTriggerRouter(triggerRouterList, TableConstants.SYM_NODE_SECURITY, symTablePrefix);
                    if (triggerRouter != null) {
                        // send source nodes in sourcenodegroupid to target nodes
                        String initialLoadSelect = String.format(engine.getDataService().findNodeIdsByNodeGroupId(), "'" + sourceNodeGroupId + "'");
                        insertReloadEvents(triggerRouter, initialLoadSelect, sourceNodeGroupId, targetNodeGroupId,
                                createTime, me, targetNodes);
                        // send target nodes in targetnodegroupid to source nodes
                        initialLoadSelect = String.format(engine.getDataService().findNodeIdsByNodeGroupId(), "'" + targetNodeGroupId + "'");
                        insertReloadEvents(triggerRouter, initialLoadSelect, sourceNodeGroupId, targetNodeGroupId,
                                createTime, me, sourceNodes);
                    }
                }
            }
        }
    }

    private void insertReloadEvents(TriggerRouter triggerRouter, String initialLoadSelect, String sourceNodeGroupId,
            String targetNodeGroupId, Date createTime, Node me, Collection<Node> targetNodes) {
        IDataService dataService = engine.getDataService();
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        List<TriggerHistory> triggerHistories = triggerRouterService.getActiveTriggerHistories(triggerRouter.getTrigger());
        if (triggerHistories.size() > 0) {
            TriggerHistory triggerHistory = triggerHistories.get(0);
            ISqlTransaction transaction = null;
            try {
                transaction = engine.getDatabasePlatform().getSqlTemplate().startSqlTransaction();
                for (Node targetNode : targetNodes) {
                    if (!me.getNodeId().equalsIgnoreCase(targetNode.getNodeId()) && notRestClient(targetNode)) {
                        dataService.insertReloadEvent(transaction, targetNode, triggerRouter, triggerHistory, initialLoadSelect, false, -1l, "configRouter",
                                Status.NE, 0l);
                    }
                }
                transaction.commit();
            } catch (Exception e) {
                log.error("Failed to insert reload events for table " + triggerRouter.getTrigger().getSourceTableName(), e);
                if (transaction != null) {
                    transaction.rollback();
                }
            } finally {
                if (transaction != null) {
                    transaction.close();
                }
            }
        }
    }

    private TriggerRouter findTriggerRouter(List<TriggerRouter> triggerRouters, String tableName, String symTablePrefix) {
        for (TriggerRouter triggerRouter : triggerRouters) {
            if (TableConstants.getTableName(symTablePrefix, tableName).equalsIgnoreCase(triggerRouter.getTrigger().getSourceTableName())) {
                return triggerRouter;
            }
        }
        return null;
    }

    protected void routeNodeTables(Set<String> nodeIds, Map<String, String> columnValues,
            NetworkedNode rootNetworkedNode, Node me, SimpleRouterContext routingContext,
            DataMetaData dataMetaData, Set<Node> possibleTargetNodes, boolean initialLoad) {
        String nodeIdForRecordBeingRouted = columnValues.get("NODE_ID");
        if (dataMetaData.getData().getDataEventType() == DataEventType.DELETE) {
            String createAtNodeId = columnValues.get("CREATED_AT_NODE_ID");
            for (Node nodeThatMayBeRoutedTo : possibleTargetNodes) {
                if (notRestClient(nodeThatMayBeRoutedTo)
                        && !nodeIdForRecordBeingRouted.equals(nodeThatMayBeRoutedTo.getNodeId())
                        && !nodeThatMayBeRoutedTo.getNodeId().equals(createAtNodeId)
                        && !nodeIdForRecordBeingRouted.equals(me.getNodeId())
                        && (nodeThatMayBeRoutedTo.getCreatedAtNodeId() == null || !nodeThatMayBeRoutedTo
                                .getCreatedAtNodeId().equals(nodeIdForRecordBeingRouted))) {
                    nodeIds.add(nodeThatMayBeRoutedTo.getNodeId());
                }
            }
        } else {
            IConfigurationService configurationService = engine.getConfigurationService();
            List<NodeGroupLink> nodeGroupLinks = getNodeGroupLinksFromContext(routingContext);
            for (Node nodeThatMayBeRoutedTo : possibleTargetNodes) {
                if (notRestClient(nodeThatMayBeRoutedTo)
                        && isLinked(nodeIdForRecordBeingRouted, nodeThatMayBeRoutedTo, rootNetworkedNode, me, nodeGroupLinks)
                        && (!isSameNumberOfLinksAwayFromRoot(nodeThatMayBeRoutedTo, rootNetworkedNode, me)
                                || configurationService.isMasterToMaster())
                        || (nodeThatMayBeRoutedTo.getNodeId().equals(me.getNodeId()) && initialLoad)) {
                    nodeIds.add(nodeThatMayBeRoutedTo.getNodeId());
                }
            }
            if (!initialLoad && nodeIds != null) {
                /*
                 * Don't route insert events for a node to itself. They will be loaded during registration. If we route them, then an old state can override the
                 * correct state
                 * 
                 * Don't send deletes to a node. A node should be responsible for deleting itself.
                 */
                if (dataMetaData.getData().getDataEventType() == DataEventType.INSERT) {
                    nodeIds.remove(nodeIdForRecordBeingRouted);
                }
            }
        }
    }

    protected Node findIdentity() {
        return engine.getNodeService().findIdentity();
    }

    @SuppressWarnings("unchecked")
    protected List<NodeGroupLink> getNodeGroupLinksFromContext(SimpleRouterContext routingContext) {
        List<NodeGroupLink> list = (List<NodeGroupLink>) routingContext.get(NodeGroupLink.class
                .getName());
        if (list == null) {
            list = engine.getConfigurationService().getNodeGroupLinks(false);
            routingContext.put(NodeGroupLink.class.getName(), list);
        }
        return list;
    }

    protected NetworkedNode getRootNetworkNodeFromContext(SimpleRouterContext routingContext) {
        NetworkedNode root = (NetworkedNode) routingContext.get(NetworkedNode.class.getName());
        if (root == null) {
            root = engine.getNodeService().getRootNetworkedNode();
            routingContext.put(NetworkedNode.class.getName(), root);
        }
        return root;
    }

    private boolean isSameNumberOfLinksAwayFromRoot(Node nodeThatCouldBeRoutedTo,
            NetworkedNode root, Node me) {
        return me != null
                && root != null
                && root.getNumberOfLinksAwayFromRoot(nodeThatCouldBeRoutedTo.getNodeId()) == root
                        .getNumberOfLinksAwayFromRoot(me.getNodeId());
    }

    private boolean isLinked(String nodeIdInQuestion, Node nodeThatCouldBeRoutedTo,
            NetworkedNode root, Node me, List<NodeGroupLink> allLinks) {
        if (root != null) {
            if (nodeIdInQuestion != null && nodeThatCouldBeRoutedTo != null
                    && !nodeIdInQuestion.equals(nodeThatCouldBeRoutedTo.getNodeId())) {
                NetworkedNode networkedNodeInQuestion = root.findNetworkedNode(nodeIdInQuestion);
                NetworkedNode networkedNodeThatCouldBeRoutedTo = root
                        .findNetworkedNode(nodeThatCouldBeRoutedTo.getNodeId());
                if (networkedNodeInQuestion != null) {
                    if (networkedNodeInQuestion.isInParentHierarchy(nodeThatCouldBeRoutedTo
                            .getNodeId())) {
                        // always route changes to parent nodes
                        return true;
                    } else if (networkedNodeInQuestion.isInChildHierarchy(nodeThatCouldBeRoutedTo.getNodeId())) {
                        return true;
                    }
                    String createdAtNodeId = networkedNodeInQuestion.getNode().getCreatedAtNodeId();
                    if (createdAtNodeId != null
                            && !createdAtNodeId.equals(me.getNodeId())
                            && !networkedNodeInQuestion.getNode().getNodeId()
                                    .equals(me.getNodeId())) {
                        if (createdAtNodeId.equals(nodeThatCouldBeRoutedTo.getNodeId())) {
                            return true;
                        } else if (networkedNodeThatCouldBeRoutedTo != null) {
                            // the node was created at some other node. lets
                            // attempt
                            // to get that update back to that node
                            return networkedNodeThatCouldBeRoutedTo
                                    .isInChildHierarchy(createdAtNodeId);
                        }
                    }
                    // if we haven't found a place to route by now, then we need
                    // to
                    // send the row to all nodes that have links to the node's
                    // group
                    String groupId = networkedNodeInQuestion.getNode().getNodeGroupId();
                    Set<String> groupsThatWillBeInterested = new HashSet<String>();
                    for (NodeGroupLink nodeGroupLink : allLinks) {
                        if (nodeGroupLink.getTargetNodeGroupId().equals(groupId)) {
                            groupsThatWillBeInterested.add(nodeGroupLink.getSourceNodeGroupId());
                        } else if (nodeGroupLink.getSourceNodeGroupId().equals(groupId)) {
                            groupsThatWillBeInterested.add(nodeGroupLink.getTargetNodeGroupId());
                        }
                    }
                    if (groupsThatWillBeInterested.contains(nodeThatCouldBeRoutedTo
                            .getNodeGroupId())) {
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public void contextCommitted(SimpleRouterContext routingContext) {
        helper.contextCommittedAndComplete(routingContext);
    }

    private boolean notRestClient(Node node) {
        return !Constants.DEPLOYMENT_TYPE_REST.equals(node.getDeploymentType());
    }

    private String tableName(String tableName) {
        return TableConstants.getTableName(engine != null ? engine.getTablePrefix() : "sym", tableName);
    }

    private boolean tableMatches(DataMetaData dataMetaData, String tableName) {
        boolean matches = false;
        if (dataMetaData.getTable().getName().equalsIgnoreCase(tableName(tableName))) {
            matches = true;
        }
        return matches;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean isDmlOnly() {
        return false;
    }
}
