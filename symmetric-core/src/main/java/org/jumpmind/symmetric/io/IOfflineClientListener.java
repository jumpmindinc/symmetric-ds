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

import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.model.Node;

/**
 * This is an extension point that is called when the current instance has detected it cannot sync with another {@link Node}.
 */
public interface IOfflineClientListener extends IExtensionPoint {
    /**
     * Called when the remote node is unreachable.
     */
    public void offline(Node remoteNode);

    /**
     * Called when this node is rejected because of a password mismatch.
     */
    public void notAuthenticated(Node remoteNode);

    /**
     * Called when this node has been rejected because the remote node is currently too busy to handle the sync request.
     */
    public void busy(Node remoteNode);

    /**
     * Called when this node is rejected because synchronization is disabled on the remote node.
     * 
     * @param remoteNode
     */
    public void syncDisabled(Node remoteNode);

    /**
     * Called when this node is rejected because the node has not been registered with the remote node.
     * 
     * @param remoteNode
     */
    public void registrationRequired(Node remoteNode);

    public void unknownError(Node remoteNode, Exception ex);

    /**
     * Called when the remote node is reachable.
     */
    public void online(Node remoteNode);
}