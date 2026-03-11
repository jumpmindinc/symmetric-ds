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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.TokenConstants;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.ColumnMatchExpression;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerRouter;

/**
 * This data router is invoked when the router_type='column'. The router_expression is always a name value pair of a column on the table that is being
 * synchronized to the value it should be matched with.
 * <P>
 * The value can be a constant. In the data router the value of the new data is always represented by a string so all comparisons are done in the format that
 * SymmetricDS transmits.
 * <P>
 * The column name used for the match is the upper case column name if the current value is being compared. The upper case column name prefixed by OLD_ can be
 * used if the comparison is being done of the old data. The virtual column names :SOURCE_SCHEMA and :SOURCE_CATALOG can also be used to match against the
 * source schema or catalog from the trigger history (e.g. ':SOURCE_SCHEMA=:EXTERNAL_ID').
 * <P>
 * For example, if the column on a table is named STATUS you can specify that you want to router when STATUS=OK by specifying such for the router_expression. If
 * you wanted to route when only the old value for STATUS=OK you would specify OLD_STATUS=OK.
 * <P>
 * The value can also be one of the following expressions:
 * <ol>
 * <li>:NODE_ID</li>
 * <li>:EXTERNAL_ID</li>
 * <li>:NODE_GROUP_ID</li>
 * <li>:REDIRECT_NODE</li>
 * <li>:{column name}</li>
 * </ol>
 * NODE_ID, EXTERNAL_ID, and NODE_GROUP_ID are instructions for the column matcher to select nodes that have a NODE_ID, EXTERNAL_ID or NODE_GROUP_ID that are
 * equal to the value on the column.
 * <P>
 * REDIRECT_NODE is an instruction to match the specified column to a registrant_external_id on registration_redirect and return the associated
 * registration_node_id in the list of node id to route to. For example, if the 'price' table was being routed to to a region 1 node based on the store_id, the
 * store_id would be the external_id of a node in the registration_redirect table and the router_expression for trigger entry for the 'price' table would be
 * 'store_id=:REDIRECT_NODE' and the router_type would be 'column'.
 */
public class ColumnMatchDataRouter extends AbstractDataRouter implements IDataRouter, IBuiltInExtensionPoint {
    private ISymmetricEngine engine;
    final static String EXPRESSION_KEY = String.format("%s.Expression.", ColumnMatchDataRouter.class
            .getName());

    public ColumnMatchDataRouter() {
    }

    public ColumnMatchDataRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    public Set<String> routeToNodes(SimpleRouterContext routingContext,
            DataMetaData dataMetaData, Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        Set<String> nodeIds = null;
        if (initialLoadSelectUsed && initialLoad) {
            nodeIds = toNodeIds(nodes, null);
        } else {
            List<ColumnMatchExpression> expressions = getExpressions(dataMetaData.getRouter(), routingContext);
            Map<String, String> columnValues = getDataMap(dataMetaData, engine.getSymmetricDialect());
            if (columnValues != null) {
                Node identity = engine.getNodeService().findIdentity();
                for (ColumnMatchExpression e : expressions) {
                    String column = e.getTokens()[0].trim();
                    String value = e.getTokens()[1];
                    String columnValue = columnValues.get(column);
                    if (columnValue == null && TokenConstants.SOURCE_SCHEMA.equalsIgnoreCase(column)) {
                        columnValue = dataMetaData.getTriggerHistory() != null
                                ? dataMetaData.getTriggerHistory().getSourceSchemaName()
                                : null;
                    } else if (columnValue == null && TokenConstants.SOURCE_CATALOG.equalsIgnoreCase(column)) {
                        columnValue = dataMetaData.getTriggerHistory() != null
                                ? dataMetaData.getTriggerHistory().getSourceCatalogName()
                                : null;
                    }
                    if (value.equalsIgnoreCase(TokenConstants.NODE_ID)) {
                        for (Node node : nodes) {
                            nodeIds = runExpression(e, columnValue, node.getNodeId(), nodes,
                                    nodeIds, node);
                        }
                    } else if (value.equalsIgnoreCase(TokenConstants.SOURCE_NODE_ID)) {
                        String sourceNodeId = identity.getNodeId();
                        for (Node node : nodes) {
                            nodeIds = runExpression(e, columnValue, sourceNodeId, nodes,
                                    nodeIds, node);
                        }
                    } else if (value.equalsIgnoreCase(TokenConstants.EXTERNAL_ID)) {
                        for (Node node : nodes) {
                            nodeIds = runExpression(e, columnValue, node.getExternalId(), nodes,
                                    nodeIds, node);
                        }
                    } else if (value.equalsIgnoreCase(TokenConstants.SOURCE_EXTERNAL_ID)) {
                        String sourceExternalId = identity.getExternalId();
                        for (Node node : nodes) {
                            nodeIds = runExpression(e, columnValue, sourceExternalId, nodes,
                                    nodeIds, node);
                        }
                    } else if (value.equalsIgnoreCase(TokenConstants.NODE_GROUP_ID)) {
                        for (Node node : nodes) {
                            nodeIds = runExpression(e, columnValue, node.getNodeGroupId(), nodes,
                                    nodeIds, node);
                        }
                    } else if (value.equalsIgnoreCase(TokenConstants.SOURCE_NODE_GROUP_ID)) {
                        String sourceNodeGroupId = identity.getNodeGroupId();
                        for (Node node : nodes) {
                            nodeIds = runExpression(e, columnValue, sourceNodeGroupId, nodes,
                                    nodeIds, node);
                        }
                    } else if (e.hasEquals() && value.equalsIgnoreCase(TokenConstants.REDIRECT_NODE)) {
                        Map<String, String> redirectMap = getRedirectMap(routingContext);
                        String nodeId = redirectMap.get(columnValue);
                        if (nodeId != null) {
                            nodeIds = addNodeId(nodeId, nodeIds, nodes);
                        }
                    } else {
                        String compareValue = value;
                        if (value.equalsIgnoreCase(TokenConstants.EXTERNAL_DATA)) {
                            compareValue = dataMetaData.getData().getExternalData();
                        } else if (value.startsWith(":")) {
                            compareValue = columnValues.get(value.substring(1));
                        } else if (value.equals(ColumnMatchExpression.NULL_VALUE)) {
                            compareValue = null;
                        }
                        nodeIds = runExpression(e, columnValue, compareValue, nodes, nodeIds, null);
                    }
                }
            } else {
                log.warn("There were no columns to match for the data_id of {}", dataMetaData
                        .getData().getDataId());
            }
        }
        if (nodeIds != null) {
            nodeIds.remove(null);
        } else {
            nodeIds = Collections.emptySet();
        }
        return nodeIds;
    }

    protected Set<String> runExpression(ColumnMatchExpression e, String columnValue, String compareValue,
            Set<Node> nodes, Set<String> nodeIds, Node node) {
        if (e.run(columnValue, compareValue)) {
            if (node != null) {
                nodeIds = addNodeId(node.getNodeId(), nodeIds, nodes);
            } else {
                nodeIds = toNodeIds(nodes, nodeIds);
            }
        }
        return nodeIds;
    }

    /**
     * Cache parsed expressions in the context to minimize the amount of parsing we have to do when we have lots of throughput.
     */
    @SuppressWarnings("unchecked")
    protected List<ColumnMatchExpression> getExpressions(Router router, SimpleRouterContext context) {
        final String KEY = EXPRESSION_KEY + router.getRouterId();
        List<ColumnMatchExpression> expressions = (List<ColumnMatchExpression>) context.getContextCache().get(
                KEY);
        if (expressions == null) {
            expressions = ColumnMatchExpression.parse(router.getRouterExpression());
            context.getContextCache().put(KEY, expressions);
        }
        return expressions;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, String> getRedirectMap(SimpleRouterContext ctx) {
        final String CTX_CACHE_KEY = ColumnMatchDataRouter.class.getSimpleName() + "RouterMap";
        Map<String, String> redirectMap = (Map<String, String>) ctx.getContextCache().get(
                CTX_CACHE_KEY);
        if (redirectMap == null) {
            redirectMap = engine.getConfigurationService().getRegistrationRedirectMap();
            ctx.getContextCache().put(CTX_CACHE_KEY, redirectMap);
        }
        return redirectMap;
    }
}
