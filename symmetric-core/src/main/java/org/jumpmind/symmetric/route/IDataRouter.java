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
package org.jumpmind.symmetric.route;

import java.util.Set;

import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TriggerRouter;

/**
 * The data router is an extension point that allows the end user to target certain nodes with data changes. SymmetricDS comes with a build-in data routers like
 * {@link DefaultDataRouter}.
 * <p>
 * In order to configure a data router you use the router_type and router_expression column on the trigger table. The given Spring bean name of the
 * {@link IDataRouter} is the router_type and each data router is configured using the routing_expression according to its implementation.
 *
 * @since 2.0
 * @see DefaultDataRouter
 *
 *
 */
public interface IDataRouter extends IExtensionPoint {
    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData, Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed,
            TriggerRouter triggerRouter);

    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch);

    public void contextCommitted(SimpleRouterContext context);

    public boolean isConfigurable();

    public boolean isDmlOnly();
}