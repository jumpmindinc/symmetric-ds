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

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TriggerRouter;

/**
 * This data router will route data to all of the nodes that are passed to it.
 */
public class DefaultDataRouter extends AbstractDataRouter implements IBuiltInExtensionPoint {
    public Set<String> routeToNodes(SimpleRouterContext routingContext, DataMetaData dataMetaData, Set<Node> nodes,
            boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        return toNodeIds(nodes, null);
    }

    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch) {
    }

    @Override
    public boolean isDmlOnly() {
        return false;
    }
}