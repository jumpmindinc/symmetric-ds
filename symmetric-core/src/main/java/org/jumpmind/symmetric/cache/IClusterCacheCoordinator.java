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

    ClusterPeerSecureMessage getMessage(String peerId);

    Set<String> getPeerIds();
}
