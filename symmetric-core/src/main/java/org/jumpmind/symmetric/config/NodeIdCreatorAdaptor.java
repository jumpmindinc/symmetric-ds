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
package org.jumpmind.symmetric.config;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.INodeService;

@SuppressWarnings("deprecation")
public class NodeIdCreatorAdaptor implements INodeIdCreator {
    private INodeService nodeService;
    private INodeIdGenerator nodeIdGenerator;

    public NodeIdCreatorAdaptor(INodeIdGenerator generator, INodeService nodeService) {
        this.nodeIdGenerator = generator;
        this.nodeService = nodeService;
    }

    public String selectNodeId(Node node, String remoteHost, String remoteAddress) {
        return nodeIdGenerator.selectNodeId(nodeService, node);
    }

    public String generateNodeId(Node node, String remoteHost, String remoteAddress) {
        return nodeIdGenerator.generateNodeId(nodeService, node);
    }

    public String generatePassword(Node node) {
        return nodeIdGenerator.generatePassword(nodeService, node);
    }
}
