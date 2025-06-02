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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.InvalidSqlException;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.SimpleRouterContext.RouterTimer;
import org.jumpmind.util.FormatUtils;

/**
 * This data router is invoked when the router_type is 'subselect'. The router_expression is always a SQL expression that is used to find the list of nodes a
 * row of data will be routed to. This router should never be used for high throughput tables because it makes a call back to the database for each row that is
 * routed.
 * <P/>
 * The query that is used to select the nodes is as follows:
 * <P/>
 * <code>
 * select c.node_id from $[sym.sync.table.prefix]_node c where c.node_group_id=:NODE_GROUP_ID and c.sync_enabled=1 and ...
 * </code>
 * <P/>
 * The SQL expression designated in the router_expression is appended to the above SQL statement. Current and old column values can be passed into the sub
 * select expression. For example, say you had an EMPLOYEE table and a PASSWORD table. When the password changes you want to route the password to the
 * HOME_STORE that is stored on the EMPLOYEE table. The sub select expression might look like:
 * <P/>
 * <code>
 * c.external_id in (select home_store from employee where employee_id in (:EMPLOYEE_ID, :OLD_EMPLOYEE_ID))
 * </code>
 */
public class SubSelectDataRouter extends AbstractDataRouter implements IBuiltInExtensionPoint {
    private static final String SQL = "select c.node_id from $(prefixName)_node c where c.node_group_id=:NODE_GROUP_ID and c.sync_enabled=1 and ";
    private ISymmetricDialect symmetricDialect;

    public SubSelectDataRouter(ISymmetricDialect symmetricDialect) {
        this.symmetricDialect = symmetricDialect;
    }

    public Set<String> routeToNodes(SimpleRouterContext routingContext, DataMetaData dataMetaData,
            Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        String sql = FormatUtils.replaceToken(SQL, "prefixName", symmetricDialect.getTablePrefix(),
                true);
        String subSelect = dataMetaData.getRouter().getRouterExpression();
        Set<String> nodeIds = null;
        if (!StringUtils.isBlank(subSelect) && !initialLoadSelectUsed) {
            try {
                Map<String, Object> sqlParams = getDataObjectMap(dataMetaData, symmetricDialect, true);
                sqlParams.put("NODE_GROUP_ID", dataMetaData.getRouter().getNodeGroupLink()
                        .getTargetNodeGroupId());
                sqlParams.put("EXTERNAL_DATA", dataMetaData.getData().getExternalData());
                sqlParams.put("DATA_EVENT_TYPE", dataMetaData.getData().getDataEventType().name());
                sqlParams.put("TABLE_NAME", dataMetaData.getData().getTableName());
                sqlParams.put("SOURCE_NODE_ID", dataMetaData.getData().getSourceNodeId());
                ISqlTemplate template = symmetricDialect.getPlatform().getSqlTemplate();
                long queryStartTime = System.currentTimeMillis();
                List<String> ids = template.query(String.format("%s(%s)", sql, subSelect),
                        new StringMapper(), sqlParams);
                long queryEndTime = System.currentTimeMillis();
                String routerId = dataMetaData.getRouter().getRouterId();
                RouterTimer rt = routingContext.addQueryTime(routerId, queryEndTime - queryStartTime);
                if (rt.getQueryTime() > 60000) {
                    rt.resetQueryTime();
                    log.info("Subselect data router with id: {} has total query time of {} seconds.", routerId, rt.getTotalQueryTime() / 1000);
                }
                if (ids != null) {
                    nodeIds = new HashSet<String>(ids);
                }
            } catch (InvalidSqlException ex) {
                log.error("The subselect expression was invalid for the {} subselect router for the '{}' event for table '{}'",
                        new Object[] { dataMetaData.getRouter().getRouterId(),
                                dataMetaData.getData().getDataEventType().name(),
                                dataMetaData.getData().getTableName() });
                throw ex;
            }
        } else if (initialLoadSelectUsed) {
            nodeIds = toNodeIds(nodes, null);
        } else {
            throw new InvalidSqlException("The subselect expression is missing for the %s router", dataMetaData.getRouter().getRouterId());
        }
        return nodeIds;
    }
}
