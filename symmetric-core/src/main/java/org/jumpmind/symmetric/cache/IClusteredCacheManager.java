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

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.ISymmetricEngine;

public interface IClusteredCacheManager {
    /**
     * Subscribe a SymmetricDS engine to this manager. JCS is started when the first engine registers. Call {@link #addPeer} for each remote host before
     * registering to ensure JCS starts with a complete peer list.
     */
    void registerEngine(ISymmetricEngine engine);

    /**
     * Remove a SymmetricDS engine from this manager. JCS is stopped when the last engine unregisters.
     */
    void unregisterEngine(ISymmetricEngine engine);

    /**
     * Add a remote server hostname to the JCS lateral peer list. Safe to call before {@link #registerEngine}; peers accumulate and are applied when JCS
     * initializes.
     */
    void addPeer(String serverId);

    Set<String> getActiveServerIds();

    boolean recordPeerOffline(String serverId);

    boolean isPeerOfflineLongEnough(String serverId, long staleThresholdMs);

    void startClusterPeerListener(ISecurityService securityService);

    void startClusterHeartbeat();

    void stopClusterCommunication();

    boolean isAnyPeerInState(String eventType);

    boolean isAnyPeerOnline();

    void broadcastPeerState(String eventType);

    void broadcastEngineState(String engineName, String engineState);

    boolean isAnyPeerWithEngineInState(String engineName, String engineState);
}
