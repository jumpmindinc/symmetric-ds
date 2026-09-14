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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.jumpmind.symmetric.common.ServerConstants;
import org.junit.jupiter.api.Test;

class NodeHostCachePeerServerDiscoveryTest {
    @Test
    void factory_dbMode_returnsNodeHostCachePeerServerDiscovery() {
        ICachePeerServerDiscovery discovery = new CachePeerServerDiscoveryFactory().create(ServerConstants.CLUSTER_PEER_DISCOVERY_DB);
        assertInstanceOf(NodeHostCachePeerServerDiscovery.class, discovery);
    }

    @Test
    void announcePeer_behavesIdenticallyToInheritedBaseClass() {
        NodeHostCachePeerServerDiscovery discovery = new NodeHostCachePeerServerDiscovery();
        assertFalse(discovery.announcePeer("server2", "10.0.0.2:4001"));
        CompositeCacheManager jcsManager = mock(CompositeCacheManager.class);
        discovery.start(new DiscoveryContext(jcsManager, 4001, List.of("region1"), "server1"));
        assertTrue(discovery.announcePeer("server2", "10.0.0.2:4001"));
    }
}
