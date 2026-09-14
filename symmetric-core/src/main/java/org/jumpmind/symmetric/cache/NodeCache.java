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
package org.jumpmind.symmetric.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;

public class NodeCache {
    private IParameterService parameterService;
    private INodeService nodeService;
    private Object nodeCacheLock = new Object();
    volatile private Map<String, List<Node>> sourceNodesCache = new HashMap<String, List<Node>>();
    volatile private Map<String, List<Node>> targetNodesCache = new HashMap<String, List<Node>>();
    volatile private Map<String, Long> sourceNodeLinkCacheTime = new HashMap<String, Long>();
    volatile private Map<String, Long> targetNodeLinkCacheTime = new HashMap<String, Long>();
    volatile private Map<String, List<Node>> nodesByGroupCache;
    private long nodesByGroupCacheTime;

    public NodeCache(ISymmetricEngine engine) {
        this.parameterService = engine.getParameterService();
        this.nodeService = engine.getNodeService();
    }

    public List<Node> getNodesByGroup(String nodeGroupId) {
        long cacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS);
        synchronized (nodeCacheLock) {
            if (nodesByGroupCache == null || System.currentTimeMillis() - nodesByGroupCacheTime >= cacheTimeoutInMs) {
                nodesByGroupCache = new HashMap<String, List<Node>>();
                Collection<Node> nodes = nodeService.getEnabledNodesFromDatabase();
                for (Node node : nodes) {
                    List<Node> list = nodesByGroupCache.get(node.getNodeGroupId());
                    if (list == null) {
                        list = new ArrayList<Node>();
                        nodesByGroupCache.put(node.getNodeGroupId(), list);
                    }
                    list.add(node);
                }
                nodesByGroupCacheTime = System.currentTimeMillis();
            }
            List<Node> nodes = nodesByGroupCache.get(nodeGroupId);
            if (nodes == null) {
                nodes = new ArrayList<Node>();
                nodesByGroupCache.put(nodeGroupId, nodes);
            }
            return nodes;
        }
    }

    public List<Node> getSourceNodesCache(NodeGroupLinkAction eventAction, Node node) {
        long cacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS);
        if (node != null) {
            List<Node> list;
            synchronized (nodeCacheLock) {
                list = sourceNodesCache.get(eventAction.name());
                if (list == null || (System.currentTimeMillis() - sourceNodeLinkCacheTime.get(eventAction.toString())) >= cacheTimeoutInMs) {
                    list = nodeService.getSourceNodesFromDatabase(eventAction, node);
                    sourceNodesCache.put(eventAction.name(), list);
                    sourceNodeLinkCacheTime.put(eventAction.toString(), System.currentTimeMillis());
                }
            }
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    public void flushSourceNodesCache() {
        synchronized (nodeCacheLock) {
            sourceNodeLinkCacheTime.entrySet().stream().forEach(e -> e.setValue(0l));
        }
    }

    public void flushTargetNodesCache() {
        synchronized (nodeCacheLock) {
            targetNodeLinkCacheTime.entrySet().stream().forEach(e -> e.setValue(0l));
            nodesByGroupCacheTime = 0;
        }
    }

    public List<Node> getTargetNodesCache(NodeGroupLinkAction eventAction, Node node) {
        long cacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_NODE_GROUP_LINK_IN_MS);
        if (node != null) {
            List<Node> list;
            synchronized (nodeCacheLock) {
                list = targetNodesCache.get(eventAction.name());
                if (list == null || (System.currentTimeMillis() - targetNodeLinkCacheTime.get(eventAction.toString())) >= cacheTimeoutInMs) {
                    list = nodeService.getTargetNodesFromDatabase(eventAction, node);
                    targetNodesCache.put(eventAction.name(), list);
                    targetNodeLinkCacheTime.put(eventAction.toString(), System.currentTimeMillis());
                }
            }
            return list;
        } else {
            return Collections.emptyList();
        }
    }
}
