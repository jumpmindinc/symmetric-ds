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
import java.util.Set;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A common superclass for data routers
 */
public abstract class AbstractDataRouter implements IDataRouter {
    protected Logger log = LoggerFactory.getLogger(getClass());

    public void contextCommitted(SimpleRouterContext context) {
    }

    protected Set<String> addNodeId(String nodeId, Set<String> nodeIds, Set<Node> nodes) {
        nodeIds = nodeIds == null ? new HashSet<String>(1) : nodeIds;
        for (Node node : nodes) {
            if (node.getNodeId().equals(nodeId)) {
                nodeIds.add(nodeId);
                break;
            }
        }
        return nodeIds;
    }

    protected Set<String> toNodeIds(Set<Node> nodes, Set<String> nodeIds) {
        nodeIds = nodeIds == null ? new HashSet<String>(nodes.size()) : nodeIds;
        for (Node node : nodes) {
            nodeIds.add(node.getNodeId());
        }
        return nodeIds;
    }

    protected Set<String> toExternalIds(Set<Node> nodes) {
        Set<String> externalIds = new HashSet<String>();
        for (Node node : nodes) {
            externalIds.add(node.getExternalId());
        }
        return externalIds;
    }

    /**
     * Override if needed.
     */
    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch) {
        log.debug("Completing batch {}", batch.getBatchId());
    }

    /**
     * Override if a router is not configurable.
     */
    public boolean isConfigurable() {
        return true;
    }

    /**
     * Override if a router is able to route non-DML DataEventTypes.
     */
    public boolean isDmlOnly() {
        return true;
    }
}
