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

/**
 * Transport abstraction for cluster peer communication. Implementations handle the mechanics of sending and receiving cluster peer messages, allowing
 * ClusteredCacheManager to remain agnostic of the underlying transport (JCS lateral TCP, UDP multicast, etc.).
 */
public interface IClusterCacheCoordinator {
    /**
     * Identity and network settings needed to join the cluster. {@code heartbeatMs} is the cluster peer heartbeat interval; both the transport's
     * message-delivery timeout and the underlying JCS socket timeouts are derived from it so they always track the (operator-tunable) heartbeat cadence and can
     * never exceed it. {@code discoveryMode} selects the peer-discovery mechanism (e.g. "none", "udp", "mdns").
     */
    record CacheCoordinatorNetworkSettings(String serverId, String clusterPartitionId, int port, String discoveryMode, long heartbeatMs) {

        private static final long DELIVERY_TIMEOUT_FLOOR_MS = 250L;
        private static final long SOCKET_TIMEOUT_CEILING_MS = 2000L;
        /**
         * Time budget for delivering a single lateral message: half the heartbeat interval (never below {@value #DELIVERY_TIMEOUT_FLOOR_MS} ms), so a blocked
         * put can never stall the heartbeat loop for longer than the loop's own cadence.
         */
        public long deliveryTimeoutMs() {
            return Math.max(DELIVERY_TIMEOUT_FLOOR_MS, heartbeatMs / 2);
        }

        /**
         * JCS lateral socket open/read timeout. Capped at the delivery budget so a dead-peer connection fails inside the budget rather than being abandoned
         * mid-flight by the delivery executor, and ceilinged at {@value #SOCKET_TIMEOUT_CEILING_MS} ms so it never waits pointlessly long when the heartbeat
         * interval is large.
         */
        public long socketTimeoutMs() {
            return Math.min(SOCKET_TIMEOUT_CEILING_MS, deliveryTimeoutMs());
        }
    }

    /** Sizing/expiration settings for a single cache region. A negative maxLifeSeconds means entries never expire by age. */
    record RegionSettings(String regionName, int maxObjects, int maxLifeSeconds, boolean useMemoryShrinker, int shrinkerIntervalSeconds,
            RemovalType removalType) {
    }

    /** Memory cache eviction policy for a region. Converts to the Apache Commons JCS MemoryCacheName class that implements it. */
    enum RemovalType {
        LRU, LFU, ARC;

        /**
         * Returns the fully-qualified JCS MemoryCacheName class implementing this eviction policy. Apache Commons JCS 3.2.1 ships only an LRU implementation
         * (plus FIFO/MRU/soft-reference, which aren't exposed here) — LFU and ARC have no real implementation to map to.
         */
        String toMemoryCacheName() {
            if (this != LRU) {
                throw new UnsupportedOperationException(
                        "Apache Commons JCS 3.2.1 does not provide a " + this + " memory cache implementation; only LRU is available");
            }
            return "org.apache.commons.jcs3.engine.memory.lru.LRUMemoryCache";
        }
    }

    static String generateEngineClusterPeerKey(String serverId, String engineName) {
        return serverId + "|" + engineName;
    }

    /**
     * Starts the coordinator. The implementation's own mandatory regions (e.g. peer heartbeat, engine state) are always configured; regionSettings adds
     * additional named regions on top of those. Region names must be unique, including against the mandatory region names, which are not caller-configurable.
     */
    void start(CacheCoordinatorNetworkSettings networkSettings, Set<RegionSettings> regionSettings);

    boolean isInitialized();

    void stop();

    void sendEngineStates(ClusterEngineStateMessage message);

    /** Returns the engine state message containing states for ALL engines on the given peer. */
    ClusterEngineStateMessage getEngineStateMessage(String peerId);

    /** Returns the engine state for a specific engine on a peer. Extracts from the full message. */
    String getEngineState(String peerId, String engineName);

    /** Adds a new peer to the cluster. Returns true if the peer was not already known. */
    boolean addPeer(String serverId);

    /**
     * Removes a peer that is no longer relevant (e.g. purged as obsolete). Returns true if the peer was known and has been removed. Also retracts any discovery
     * registration previously made via {@link #announceDiscoveredPeer(String, String)} for this peer.
     */
    boolean removePeer(String serverId);

    /**
     * Registers a peer's network address for transport-level discovery, so the underlying transport can reach the peer without depending on its own
     * broadcast/multicast discovery mechanism. Safe to call repeatedly as a peer's address changes (e.g. a restarted container with a new IP): the prior
     * registration for this serverId is retracted before the new one is added. Returns true if this changed the registration (new peer or changed address).
     */
    boolean announceDiscoveredPeer(String serverId, String address);

    void sendServerStatus(ClusterServerStatusMessage message);

    /** Returns the latest peer status message (heartbeat/join/leave) for the given peer server ID. */
    ClusterServerStatusMessage getPeerStatusMessage(String peerId);

    /**
     * Returns the latest message stored in the given cache region for the given key. Supports future message types beyond peer status (e.g. cache invalidation
     * notifications keyed by cache variable name). Returns null if the region or key is not found.
     */
    ClusterPeerSecureMessage getMessage(String region, String key);

    Set<String> getPeerIds();

    /**
     * Checks for last peer message stored in cache and determines if message timestamp is not stale. Returns false if peer is considered stale.
     */
    boolean detectIfPeerIsStale(String peerId, long staleThresholdMs);

    /**
     * Returns the peer status messages observed in the local peer-status cache region, including peers who have already pushed lateral cache messages to us
     * (e.g. because they have us in their own TcpServers list) but that we have not yet added as a known peer ourselves. Returning the full message (rather
     * than just the server ID) lets callers seed a peer's initial state from its real timestamp instead of re-reading the cache a second time.
     */
    Set<ClusterServerStatusMessage> getObservedPeers();

    ClusterMessageConverter getConverter();
}
