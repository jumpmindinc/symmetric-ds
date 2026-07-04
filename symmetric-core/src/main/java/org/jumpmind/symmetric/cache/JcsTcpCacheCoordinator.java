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
 * software distributed under the LICENSE is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.cache;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ServerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IClusterCacheCoordinator implementation that uses Apache JCS lateral TCP cache for peer-to-peer communication. Peers find each other through JCS's built-in
 * UDP multicast discovery, so the JCS CompositeCacheManager is configured once in {@link #start(InitialSettings, Set)} and is never torn down or reconfigured
 * as peers come and go. Reconfiguring it on every new peer previously caused JCS to silently keep returning its already-shutdown TCP listener for the port (JCS
 * caches listeners in a static, port-keyed registry that a shutdown never clears), leaving the node unreachable for the rest of its life.
 */
public class JcsTcpCacheCoordinator implements IClusterCacheCoordinator {
    private static final Logger log = LoggerFactory.getLogger(JcsTcpCacheCoordinator.class);
    static final int DEFAULT_PORT = 1101;
    private static final String JCS_PEER_REGION = "SYM_CLUSTER_PEERS";
    private static final String JCS_ENGINE_REGION = "SYM_CLUSTER_ENGINES";
    private static final String JCS_REGION_SYNC_LATERAL_TCP = "LATERAL_TCP"; // Sync mode specific to this class
    private static final int DEFAULT_MAX_OBJECTS = 1000;
    private static final int DEFAULT_MAX_LIFE_SECONDS = -1;
    private static final boolean DEFAULT_USE_MEMORY_SHRINKER = false;
    private static final int DEFAULT_SHRINKER_INTERVAL_SECONDS = 30;
    private static final RemovalType DEFAULT_REMOVAL_TYPE = RemovalType.LRU;
    private static final String JCS_CONFIG_GLOBAL_PREFIX = "jcs.default";
    private static final String JCS_CONFIG_REGION_PREFIX = "jcs.region";
    private static final String JCS_CONFIG_AUX_PREFIX = "jcs.auxiliary";
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private volatile CompositeCacheManager jcsCacheManager;
    private volatile CacheAccess<String, ClusterPeerSecureMessage> peerHeartbeatCache;
    private volatile CacheAccess<String, ClusterEngineStateMessage> engineStateCache;
    private int port;
    private String serverId;
    private String clusterPartitionId;

    @Override
    public void start(ISymmetricEngine engine) {
        InitialSettings initialSettings = new InitialSettings(
                engine.getClusterService().getServerId(),
                engine.getClusterService().getClusterPartitionId(),
                engine.getParameterService().getInt(ServerConstants.CLUSTER_JCS_PORT, DEFAULT_PORT));
        start(initialSettings, Collections.emptySet());
    }

    @Override
    public synchronized void start(InitialSettings initialSettings, Set<RegionSettings> regionSettings) {
        this.serverId = initialSettings.serverId();
        this.clusterPartitionId = initialSettings.clusterPartitionId();
        this.port = initialSettings.port();
        Set<RegionSettings> allRegionSettings = withMandatoryRegions(regionSettings);
        try {
            jcsCacheManager = CompositeCacheManager.getUnconfiguredInstance();
            Properties props = buildJcsCoreProperties(initialSettings);
            props.putAll(buildRegionalProperties(allRegionSettings));
            jcsCacheManager.configure(props);
            peerHeartbeatCache = new CacheAccess<>(jcsCacheManager.getCache(JCS_PEER_REGION));
            engineStateCache = new CacheAccess<>(jcsCacheManager.getCache(JCS_ENGINE_REGION));
            log.info("Started JCS cluster cache. Port={}, ServerId={}, ClusterPartitionId={}", port, serverId, clusterPartitionId);
        } catch (Exception e) {
            log.error("Failed to initialize JCS cluster cache on port {}: {}", port, e.getMessage());
            throw new RuntimeException("Failed to initialize JCS cluster cache on port " + port, e);
        }
    }

    /**
     * Merges the caller-supplied regions with the mandatory JCS_PEER_REGION/JCS_ENGINE_REGION, which are always configured with default settings. Rejects
     * duplicate region names, including caller-supplied regions that reuse a mandatory region name, since those are not caller-configurable.
     */
    private Set<RegionSettings> withMandatoryRegions(Set<RegionSettings> regionSettings) {
        Set<String> regionNames = new HashSet<>(Set.of(JCS_PEER_REGION, JCS_ENGINE_REGION));
        for (RegionSettings settings : regionSettings) {
            if (!regionNames.add(settings.regionName())) {
                throw new IllegalArgumentException("Duplicate region name: " + settings.regionName());
            }
        }
        Set<RegionSettings> allRegionSettings = new LinkedHashSet<>();
        allRegionSettings.add(defaultRegionSettings(JCS_PEER_REGION));
        allRegionSettings.add(defaultRegionSettings(JCS_ENGINE_REGION));
        allRegionSettings.addAll(regionSettings);
        return allRegionSettings;
    }

    private RegionSettings defaultRegionSettings(String regionName) {
        return new RegionSettings(regionName, DEFAULT_MAX_OBJECTS, DEFAULT_MAX_LIFE_SECONDS, DEFAULT_USE_MEMORY_SHRINKER,
                DEFAULT_SHRINKER_INTERVAL_SECONDS, DEFAULT_REMOVAL_TYPE);
    }

    @Override
    public synchronized void stop() {
        if (jcsCacheManager != null) {
            jcsCacheManager.shutDown();
            jcsCacheManager = null;
            peerHeartbeatCache = null;
            engineStateCache = null;
        }
    }

    @Override
    public synchronized boolean addPeer(String serverId) {
        if (knownPeers.add(serverId)) {
            log.info("Added new peer to cluster. serverId={}, ClusterPartitionId={}, knownPeers.size={}", serverId, clusterPartitionId, knownPeers.size());
            return true;
        } else {
            log.debug("Peer already known to cluster. serverId={}, ClusterPartitionId={}", serverId, clusterPartitionId);
            return false;
        }
    }

    @Override
    public void sendMessageToPeers(ClusterPeerSecureMessage message) {
        if (knownPeers.isEmpty()) {
            log.debug("Skipping cluster-wide message — no peers in cluster. serverId={}", serverId);
            return;
        }
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        if (cache == null) {
            log.debug("Skipping send to cluster peers because JCS is not initialized. serverId={}", serverId);
            return;
        }
        try {
            cache.put(message.getServerId(), message);
            log.debug("Sent cluster-wide message. eventType={}, serverId={}, knownPeers.size={}",
                    message.getEventType(), message.getServerId(), knownPeers.size());
        } catch (Exception ex) {
            String msg = String.format("Failed to send cluster-wide message. eventType=%s, serverId=%s",
                    message.getEventType(), message.getServerId());
            log.warn(msg, ex);
        }
    }

    @Override
    public void sendEngineStateMessage(ClusterEngineStateMessage message) {
        if (knownPeers.isEmpty()) {
            log.debug("Skipping engine state message — no peers in cluster. serverId={}", serverId);
            return;
        }
        CacheAccess<String, ClusterEngineStateMessage> cache = engineStateCache;
        if (cache == null) {
            log.debug("Skipping engine state message — JCS not initialized. serverId={}", serverId);
            return;
        }
        String key = IClusterCacheCoordinator.generateEngineClusterPeerKey(message.getServerId(), message.getEngineName());
        try {
            cache.put(key, message);
            log.debug("Sent engine state message. engineState={}, engineName={}, serverId={}",
                    message.getEngineState(), message.getEngineName(), message.getServerId());
        } catch (Exception ex) {
            String msg = String.format("Failed to send engine state message. engineState=%s, engineName=%s, serverId=%s",
                    message.getEngineState(), message.getEngineName(), message.getServerId());
            log.warn(msg, ex);
        }
    }

    @Override
    public ClusterPeerStatusMessage getPeerStatusMessage(String peerId) {
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        if (cache == null) {
            return null;
        }
        ClusterPeerSecureMessage msg = cache.get(peerId);
        return msg instanceof ClusterPeerStatusMessage ? (ClusterPeerStatusMessage) msg : null;
    }

    @Override
    public ClusterEngineStateMessage getEngineStateMessage(String peerId, String engineName) {
        CacheAccess<String, ClusterEngineStateMessage> cache = engineStateCache;
        if (cache == null) {
            return null;
        }
        return cache.get(IClusterCacheCoordinator.generateEngineClusterPeerKey(peerId, engineName));
    }

    @Override
    public ClusterPeerSecureMessage getMessage(String region, String key) {
        if (JCS_PEER_REGION.equals(region)) {
            return getPeerStatusMessage(key);
        }
        return null;
    }

    @Override
    public Set<String> getPeerIds() {
        return knownPeers;
    }

    @Override
    public boolean detectIfPeerIsStale(String peerId, long staleThresholdMs) {
        ClusterPeerStatusMessage peerStatusMessage = getPeerStatusMessage(peerId);
        return peerStatusMessage == null || peerStatusMessage.isStale(System.currentTimeMillis(), staleThresholdMs);
    }

    @Override
    public Set<ClusterPeerStatusMessage> getObservedPeers() {
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        if (cache == null) {
            return Collections.emptySet();
        }
        Set<ClusterPeerStatusMessage> result = new HashSet<>();
        for (String key : cache.getCacheControl().getKeySet(true)) {
            ClusterPeerSecureMessage msg = cache.get(key);
            if (msg instanceof ClusterPeerStatusMessage) {
                result.add((ClusterPeerStatusMessage) msg);
            }
        }
        return result;
    }

    /**
     * Prepares the core (non-region) configuration properties for Apache JCS's CompositeCacheManager: the lateral TCP auxiliary cache and its UDP discovery
     * settings.
     *
     * JCS's own UDP discovery timers (UDPDiscoveryAttributes.sendDelaySec/maxIdleTimeSec) are not exposed through TCPLateralCacheAttributes and so cannot be
     * set here; JCS 3.2.1 also never reads sendDelaySec (its passive broadcast runs on a hardcoded 15s interval) and always constructs UDPDiscoveryAttributes
     * with its own defaults. This is not load-bearing for SymmetricDS: peer liveness is decided by our own heartbeat cadence and staleness threshold (see
     * ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS, CLUSTER_DB_OWNERSHIP_STALE_MS, and detectIfPeerIsStale), which run independently of JCS's UDP discovery of
     * lateral TCP peers.
     */
    private Properties buildJcsCoreProperties(InitialSettings initialSettings) {
        Properties props = new Properties();
        props.setProperty(JCS_CONFIG_GLOBAL_PREFIX, "");
        String auxPrefix = JCS_CONFIG_AUX_PREFIX + ".LATERAL_TCP";
        props.setProperty(auxPrefix, "org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.LateralTCPCacheFactory");
        props.setProperty(auxPrefix + ".attributes", "org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.TCPLateralCacheAttributes");
        props.setProperty(auxPrefix + ".attributes.TcpListenerPort", String.valueOf(initialSettings.port()));
        props.setProperty(auxPrefix + ".attributes.UdpDiscoveryEnabled", "true");
        props.setProperty(auxPrefix + ".attributes.AllowGet", "false");
        props.setProperty(auxPrefix + ".attributes.Receive", "true");
        return props;
    }

    /**
     * Prepares the per-region configuration properties, enriching each region with its lateral TCP mode and sizing/expiration/eviction settings. A negative
     * maxLifeSeconds leaves the region's elements eternal (no age-based expiration), matching JCS's own default. Disk and remote auxiliaries are explicitly
     * disabled at both the region and element level since only the LATERAL_TCP auxiliary is ever configured; lateral is explicitly enabled to match it.
     */
    private Properties buildRegionalProperties(Set<RegionSettings> regionSettings) {
        Properties props = new Properties();
        for (RegionSettings settings : regionSettings) {
            String regionPrefix = JCS_CONFIG_REGION_PREFIX + "." + settings.regionName();
            props.setProperty(regionPrefix, JCS_REGION_SYNC_LATERAL_TCP);
            props.setProperty(regionPrefix + ".cacheattributes.MaxObjects", String.valueOf(settings.maxObjects()));
            props.setProperty(regionPrefix + ".cacheattributes.UseLateral", "true");
            props.setProperty(regionPrefix + ".cacheattributes.UseRemote", "false");
            props.setProperty(regionPrefix + ".cacheattributes.UseDisk", "false");
            props.setProperty(regionPrefix + ".cacheattributes.UseMemoryShrinker", String.valueOf(settings.useMemoryShrinker()));
            props.setProperty(regionPrefix + ".cacheattributes.ShrinkerIntervalSeconds", String.valueOf(settings.shrinkerIntervalSeconds()));
            props.setProperty(regionPrefix + ".cacheattributes.MemoryCacheName", settings.removalType().toMemoryCacheName());
            props.setProperty(regionPrefix + ".elementattributes.IsEternal", String.valueOf(settings.maxLifeSeconds() < 0));
            props.setProperty(regionPrefix + ".elementattributes.MaxLife", String.valueOf(settings.maxLifeSeconds()));
            props.setProperty(regionPrefix + ".elementattributes.IsLateral", "true");
            props.setProperty(regionPrefix + ".elementattributes.IsRemote", "false");
            props.setProperty(regionPrefix + ".elementattributes.IsSpool", "false");
        }
        return props;
    }
}
