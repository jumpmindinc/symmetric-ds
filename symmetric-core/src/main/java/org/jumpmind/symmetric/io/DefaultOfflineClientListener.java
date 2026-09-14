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
package org.jumpmind.symmetric.io;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.INodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of an {@link IOfflineClientListener}. When the listener detects that sync has been disabled or registration is required, the local
 * node identity is removed.
 */
public class DefaultOfflineClientListener implements IOfflineClientListener, IBuiltInExtensionPoint {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ISymmetricEngine engine;

    public DefaultOfflineClientListener(ISymmetricEngine engine) {
        this.engine = engine;
    }

    public void busy(Node remoteNode) {
    }

    public void notAuthenticated(Node remoteNode) {
    }

    public void unknownError(Node remoteNode, Exception ex) {
    }

    public void offline(Node remoteNode) {
    }

    public void syncDisabled(Node remoteNode) {
        INodeService nodeService = engine.getNodeService();
        Node identity = nodeService.findIdentity();
        if (identity != null && ((identity.getCreatedAtNodeId() != null
                && identity.getCreatedAtNodeId().equals(remoteNode.getNodeId()))
                || engine.getConfigurationService().isMasterToMaster())) {
            log.warn("Uninstalling node because sync has been disabled");
            engine.uninstall();
        }
    }

    public void registrationRequired(Node remoteNode) {
    }

    public void online(Node remoteNode) {
    }
}
