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
package org.jumpmind.symmetric.cache;

import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;

/**
 * Transport abstraction for cluster peer communication. Implementations handle the mechanics of sending and receiving cluster peer messages, allowing
 * ClusteredCacheManager to remain agnostic of the underlying transport (JCS lateral TCP, UDP multicast, etc.).
 */
public interface IClusterCacheCoordinator {
    void start(ISymmetricEngine engine);

    void stop();

    void addPeer(String serverId);

    void sendMessageToPeers(ClusterPeerSecureMessage message);

    /** Returns the latest peer status message (heartbeat/join/leave) for the given peer server ID. */
    ClusterPeerStatusMessage getPeerStatusMessage(String peerId);

    /**
     * Returns the latest message stored in the given cache region for the given key. Supports future message types beyond peer status (e.g. cache invalidation
     * notifications keyed by cache variable name). Returns null if the region or key is not found.
     */
    ClusterPeerSecureMessage getMessage(String region, String key);

    Set<String> getPeerIds();
}
