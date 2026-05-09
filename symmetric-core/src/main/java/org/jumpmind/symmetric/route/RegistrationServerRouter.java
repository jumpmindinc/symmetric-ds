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
import java.util.Set;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TriggerRouter;

/**
 * Routes data only to the registration server. It returns no targets when called on the registration node itself.
 */
public class RegistrationServerRouter extends AbstractDataRouter implements IBuiltInExtensionPoint {
    public static final String ROUTER_TYPE = "registration_server";
    private static final Set<String> NO_NODES_TO_ROUTE = Collections.emptySet();
    protected ISymmetricEngine engine;

    public RegistrationServerRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @Override
    public Set<String> routeToNodes(SimpleRouterContext routingContext, DataMetaData dataMetaData,
            Set<Node> possibleTargetNodes, boolean initialLoad, boolean initialLoadSelectUsed,
            TriggerRouter triggerRouter) {
        Node identity = findIdentity();
        return hasSomewhereToRoute(identity)
                ? findRegistrationNode(identity, possibleTargetNodes)
                : NO_NODES_TO_ROUTE;
    }

    protected Set<String> findRegistrationNode(Node identity, Set<Node> possibleTargetNodes) {
        String registrationNodeId = identity.getCreatedAtNodeId();
        for (Node node : possibleTargetNodes) {
            if (registrationNodeId.equals(node.getNodeId())) {
                return Collections.singleton(registrationNodeId);
            }
        }
        return NO_NODES_TO_ROUTE;
    }

    protected boolean hasSomewhereToRoute(Node identity) {
        return !isRegistrationServer()
                && identity != null
                && identity.getCreatedAtNodeId() != null;
    }

    protected boolean isRegistrationServer() {
        return engine.getParameterService().isRegistrationServer();
    }

    protected Node findIdentity() {
        return engine.getNodeService().findIdentity();
    }

    @Override
    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch) {
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
