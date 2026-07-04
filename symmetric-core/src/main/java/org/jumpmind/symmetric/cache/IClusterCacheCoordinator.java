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
    /** Identity and network settings needed to join the cluster. */
    record InitialSettings(String serverId, String clusterPartitionId, int port) {
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

    void start(ISymmetricEngine engine);

    /**
     * Starts the coordinator. The implementation's own mandatory regions (e.g. peer heartbeat, engine state) are always configured; regionSettings adds
     * additional named regions on top of those. Region names must be unique, including against the mandatory region names, which are not caller-configurable.
     */
    void start(InitialSettings initialSettings, Set<RegionSettings> regionSettings);

    void stop();

    void sendEngineStateMessage(ClusterEngineStateMessage message);

    ClusterEngineStateMessage getEngineStateMessage(String serverId, String engineName);

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

    void sendMessageToPeers(ClusterPeerSecureMessage message);

    /** Returns the latest peer status message (heartbeat/join/leave) for the given peer server ID. */
    ClusterPeerStatusMessage getPeerStatusMessage(String peerId);

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
    Set<ClusterPeerStatusMessage> getObservedPeers();
}
