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

import java.util.Date;
import java.util.Set;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.ISymmetricEngine;

public interface IClusteredCacheManager {
    record PeerState(boolean alive, long lastAliveMs) {
    }

    /**
     * Subscribe a SymmetricDS engine (endpoint, node) to broadcast engine state to cluster peer servers.
     */
    void registerEngine(ISymmetricEngine engine, ClusteredEngineState initialEngineState);

    /**
     * Remove a SymmetricDS engine, typically on stop/shutdown.
     */
    void unregisterEngine(ISymmetricEngine engine, ClusteredEngineState finalEngineState);

    /**
     * Add a remote server hostname to the list of cluster peer servers. Return true if the peer was not already known to the cluster.
     */
    boolean addPeer(String serverId, Date heartbeatTime, String peerClusterPartitionId);

    /**
     * Removes a peer that is no longer relevant (e.g. its SYM_NODE_HOST row was purged as obsolete). Returns true if the peer was known and has been removed.
     */
    boolean removePeer(String serverId);

    /**
     * Registers a peer's network address for transport-level discovery, so the underlying transport can reach the peer without depending on its own
     * broadcast/multicast discovery mechanism. Safe to call repeatedly as a peer's address changes. Returns true if this changed the registration.
     */
    boolean announceDiscoveredPeer(String serverId, String address);

    Set<String> getActiveServerIds();

    boolean recordPeerOffline(String serverId);

    boolean isPeerOfflineLongEnough(String serverId, long staleThresholdMs);

    boolean isClusterPeerListenerStarted();

    boolean isInitialized();

    /**
     * Start network communication with peers in cluster (if configured) and begin heartbeat message broadcasts + discovery without database dependency.
     */
    void initialize(ISecurityService securityService, String clusterPartitionId, String serverId, boolean isClusterLockingEnabled, Object engineHolder);

    /**
     * The cluster.lock.enabled value this node actually started JCS peer-awareness with, resolved once from file/environment configuration before any engine
     * existed. This parameter is not database-overridable, so this is the authoritative value — a live {@code IParameterService} read may disagree if a stale
     * or mismatched database override exists.
     */
    boolean isClusterLockingEnabled();

    /**
     * Announces departure to cluster peers immediately, before tearing down network resources (in shutdown method).
     */
    void announceLeaving();

    /** Stop network communication and release JCS cluster resources. Call announceLeaving first so peer servers are notified of the departure. */
    void shutdown();

    /** The JCS cluster partition ID this node resolved and is currently announcing under. */
    String getClusterPartitionId();

    /** The server ID this node resolved and is currently announcing under. */
    String getServerId();

    /** The current interval, in milliseconds, between cluster peer heartbeat broadcasts. */
    long getHeartbeatIntervalMs();

    /** The current age, in milliseconds, after which a peer's last heartbeat is considered stale. */
    long getStaleIntervalMs();

    /**
     * Generates a random delay, bounded by the current heartbeat interval and stale threshold, used to jitter cluster peer coordination waits so simultaneous
     * engine startups don't retry in lockstep. Not security-sensitive.
     */
    long generatePeerCoordinationDelay();

    boolean isAnyPeerInState(String eventType);

    boolean isAnyPeerOnline();

    void broadcastStateToPeers(ClusterPeerServerState state);

    void broadcastEngineState(String engineName, ClusteredEngineState engineState);

    boolean isAnyPeerWithEngineInState(String engineName, ClusteredEngineState engineState);

    /**
     * Re-broadcasts this node's last known peer status and engine states. Callers that add one or more peers outside the regular heartbeat cycle (e.g. a
     * DB-driven scan) should call this once afterward — only if {@link #addPeer} returned {@code true} for at least one of them — so those peers learn our
     * current state without waiting for the next heartbeat tick. No-op if the cluster peer listener has not started.
     */
    void rebroadcastCurrentState();
}
