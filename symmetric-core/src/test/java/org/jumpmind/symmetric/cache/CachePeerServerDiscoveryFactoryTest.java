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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jumpmind.symmetric.common.ServerConstants;
import org.junit.jupiter.api.Test;

class CachePeerServerDiscoveryFactoryTest {
    private final CachePeerServerDiscoveryFactory factory = new CachePeerServerDiscoveryFactory();

    @Test
    void create_nullMode_returnsNodeHostCachePeerServerDiscovery() {
        assertInstanceOf(NodeHostCachePeerServerDiscovery.class, factory.create(null));
    }

    @Test
    void create_blankMode_returnsNodeHostCachePeerServerDiscovery() {
        assertInstanceOf(NodeHostCachePeerServerDiscovery.class, factory.create("   "));
    }

    @Test
    void create_dbMode_returnsNodeHostCachePeerServerDiscovery() {
        assertInstanceOf(NodeHostCachePeerServerDiscovery.class, factory.create(ServerConstants.CLUSTER_PEER_DISCOVERY_DB));
    }

    @Test
    void create_dbModeDifferentCase_returnsNodeHostCachePeerServerDiscovery() {
        assertInstanceOf(NodeHostCachePeerServerDiscovery.class, factory.create("DB"));
    }

    @Test
    void create_unsupportedMode_throwsIllegalArgumentExceptionWithDetails() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> factory.create("multicast"));
        assertEquals("Unsupported value for parameter cluster.peer.discovery='multicast'; Note, this edition supports: db", ex.getMessage());
    }
}
