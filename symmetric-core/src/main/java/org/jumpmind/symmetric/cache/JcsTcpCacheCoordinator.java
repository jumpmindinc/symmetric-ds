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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.TCPLateralCacheAttributes;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.apache.commons.jcs3.utils.discovery.DiscoveredService;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryManager;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryService;
import org.apache.commons.jcs3.utils.discovery.behavior.IDiscoveryListener;
import org.apache.commons.jcs3.utils.serialization.StandardSerializer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IClusterCacheCoordinator implementation that uses Apache JCS lateral TCP cache for peer-to-peer communication. Peers can find each other through JCS's
 * built-in UDP multicast discovery, or through {@link #announceDiscoveredPeer(String, String)} feeding in addresses learned from SYM_NODE_HOST — needed because
 * UDP multicast is unavailable on most cloud VPCs and managed Kubernetes networks. Either way, the JCS CompositeCacheManager is configured once in
 * {@link #start(InitialSettings, Set)} and is never torn down or reconfigured as peers come and go. Reconfiguring it on every new peer previously caused JCS to
 * silently keep returning its already-shutdown TCP listener for the port (JCS caches listeners in a static, port-keyed registry that a shutdown never clears),
 * leaving the node unreachable for the rest of its life.
 */
public class JcsTcpCacheCoordinator implements IClusterCacheCoordinator {
    private static final Logger log = LoggerFactory.getLogger(JcsTcpCacheCoordinator.class);
    static final int JCS_TCP_PORT_DEFAULT = 1101;
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet(); // Used for actual network communication
    private final Map<String, String> knownPeerAddresses = new ConcurrentHashMap<>(); // serverId -> last address announced for JCS discovery
    private final ClusterMessageConverter converter = new ClusterMessageConverter();
    private volatile CompositeCacheManager jcsManager;
    private volatile CacheAccess<String, ClusterPeerSecureMessage> peerHeartbeatCache;
    private volatile CacheAccess<String, ClusterEngineStateMessage> engineStateCache;
    private Set<String> discoveryRegionNames = Collections.emptySet();
    private UDPDiscoveryService discoveryService; // Resolved lazily; only accessed from methods synchronized on this
    private int port;
    private String serverId;
    private String clusterPartitionId;

    @Override
    public synchronized void start(InitialSettings initialSettings, Set<RegionSettings> regionSettings) {
        this.serverId = initialSettings.serverId();
        this.clusterPartitionId = initialSettings.clusterPartitionId();
        this.port = initialSettings.port();
        Properties jcsProperties = JcsPropertiesBuilder.build(initialSettings, regionSettings);
        Set<String> regionNames = new HashSet<>(Set.of(JcsPropertiesBuilder.PEER_REGION, JcsPropertiesBuilder.ENGINE_REGION));
        for (RegionSettings settings : regionSettings) {
            regionNames.add(settings.regionName());
        }
        this.discoveryRegionNames = regionNames;
        try {
            jcsManager = CompositeCacheManager.getUnconfiguredInstance();
            jcsManager.configure(jcsProperties);
            peerHeartbeatCache = new CacheAccess<>(jcsManager.getCache(JcsPropertiesBuilder.PEER_REGION));
            engineStateCache = new CacheAccess<>(jcsManager.getCache(JcsPropertiesBuilder.ENGINE_REGION));
            log.info("Started JCS cluster communication. Port={}, ServerId={}, ClusterPartitionId={}", port, serverId, clusterPartitionId);
        } catch (Exception ex) {
            String msg = String.format("Failed to initialize JCS cluster communication on port %d", port);
            log.error(msg, ex);
            stop();
            throw new RuntimeException(msg, ex);
        }
    }

    @Override
    public boolean isInitialized() {
        return jcsManager != null;
    }

    public ClusterMessageConverter getConverter() {
        return converter;
    }

    @Override
    public synchronized void stop() {
        if (jcsManager != null) {
            log.debug("Stopping JCS cluster communication. ServerId={}, ClusterPartitionId={}", serverId, clusterPartitionId);
            try {
                jcsManager.shutDown();
            } catch (Exception ex) {
                log.warn("Problem while stopping JCS cluster communication", ex);
            }
            jcsManager = null;
            peerHeartbeatCache = null;
            engineStateCache = null;
            discoveryService = null;
            log.debug("JCS cluster cache shutdown complete. ServerId={}, ClusterPartitionId={}", serverId, clusterPartitionId);
        } else {
            log.debug("JCS cluster cache was not running, so no shutdown was performed. ServerId={}, ClusterPartitionId={}", serverId, clusterPartitionId);
        }
    }

    @Override
    public synchronized boolean addPeer(String serverId) {
        boolean isNewPeer = !knownPeers.contains(serverId);
        if (isNewPeer && converter.getRejectedServers().containsKey(serverId)) {
            log.debug("Rejecting new peer due to blacklist. serverId={}, ClusterPartitionId={}, rejectionReason={}",
                    serverId, clusterPartitionId, converter.getRejectedServers().get(serverId).getReason());
            return false;
        }
        if (knownPeers.add(serverId)) {
            log.info("Added new peer to cluster. serverId={}, ClusterPartitionId={}, knownPeers.size={}", serverId, clusterPartitionId, knownPeers.size());
            return true;
        } else {
            log.debug("Peer already known to cluster. serverId={}, ClusterPartitionId={}", serverId, clusterPartitionId);
            return false;
        }
    }

    @Override
    public synchronized boolean removePeer(String serverId) {
        String address = knownPeerAddresses.remove(serverId);
        if (address != null) {
            UDPDiscoveryService service = getUdpDiscoveryService();
            if (service != null) {
                retractDiscoveredAddress(service, address);
            }
        }
        if (knownPeers.remove(serverId)) {
            purgePeerMessages(serverId);
            log.info("Removed obsolete peer from cluster. serverId={}, ClusterPartitionId={}, knownPeers.size={}", serverId, clusterPartitionId,
                    knownPeers.size());
            return true;
        } else {
            log.debug("Peer not known to cluster, nothing to remove. serverId={}, ClusterPartitionId={}", serverId, clusterPartitionId);
            return false;
        }
    }

    /**
     * Registers a peer's address so JCS's lateral cache can reach it directly, without depending on JCS's own UDP multicast discovery — which is unavailable on
     * most cloud VPCs and managed Kubernetes networks. Reuses the same {@link IDiscoveryListener} extension point JCS's UDP layer calls when it receives a
     * multicast announcement, so this never touches the CompositeCacheManager configuration and cannot hit the reconfigure landmine described in the class
     * comment.
     */
    @Override
    public synchronized boolean announceDiscoveredPeer(String serverId, String address) {
        if (StringUtils.isBlank(address)) {
            return false;
        }
        if (converter.getRejectedServers().containsKey(serverId)) {
            log.debug("Rejecting UDP-discovered peer due to blacklist. serverId={}, address={}, ClusterPartitionId={}, rejectionReason={}",
                    serverId, address, clusterPartitionId, converter.getRejectedServers().get(serverId).getReason());
            return false;
        }
        UDPDiscoveryService service = getUdpDiscoveryService();
        if (service == null) {
            log.debug("Skipping peer discovery announcement because JCS discovery is unavailable. serverId={}, address={}", serverId, address);
            return false;
        }
        String previousAddress = knownPeerAddresses.put(serverId, address);
        if (address.equals(previousAddress)) {
            return false;
        }
        if (previousAddress != null) {
            retractDiscoveredAddress(service, previousAddress);
        }
        announceDiscoveredAddress(service, address);
        log.info("Announced discovered peer for JCS lateral cache discovery. serverId={}, address={}, previousAddress={}, ClusterPartitionId={}",
                serverId, address, previousAddress, clusterPartitionId);
        return true;
    }

    /**
     * Obtains handle to the UDPDiscoveryService JCS already created while configuring the lateral TCP cache (lazy resolve to avoid zombie UDPDiscoveryService
     * problem). UDPDiscoveryManager's internal service map is keyed only by discoveryAddress:discoveryPort:servicePort, and since buildJcsCoreProperties()
     * never overrides the UDP discovery address/port, a fresh TCPLateralCacheAttributes' defaults are guaranteed to match what JCS is actually using. This
     * approach returns the existing UDP instance rather than creating a new one!
     */
    private UDPDiscoveryService getUdpDiscoveryService() {
        if (this.discoveryService == null && jcsManager != null) {
            this.discoveryService = obtainUdpDiscoveryService();
        }
        return this.discoveryService;
    }

    private UDPDiscoveryService obtainUdpDiscoveryService() {
        UDPDiscoveryService currentDiscoveryService = null;
        try {
            TCPLateralCacheAttributes discoveryDefaults = new TCPLateralCacheAttributes();
            currentDiscoveryService = UDPDiscoveryManager.getInstance().getService(
                    discoveryDefaults.getUdpDiscoveryAddr(), discoveryDefaults.getUdpDiscoveryPort(),
                    null, port, 0, jcsManager, new StandardSerializer());
            log.debug("Resolved JCS UDP discovery service. ServerId={}", serverId);
        } catch (Exception ex) {
            log.warn("Unable to resolve JCS UDP discovery service! ServerId={}", serverId, ex);
        }
        return currentDiscoveryService;
    }

    private void announceDiscoveredAddress(UDPDiscoveryService service, String address) {
        DiscoveredService discovered = buildDiscoveredService(address);
        for (IDiscoveryListener listener : service.getCopyOfDiscoveryListeners()) {
            listener.addDiscoveredService(discovered);
        }
    }

    private void retractDiscoveredAddress(UDPDiscoveryService service, String address) {
        DiscoveredService discovered = buildDiscoveredService(address);
        for (IDiscoveryListener listener : service.getCopyOfDiscoveryListeners()) {
            listener.removeDiscoveredService(discovered);
        }
    }

    private DiscoveredService buildDiscoveredService(String address) {
        DiscoveredService discovered = new DiscoveredService();
        discovered.setServiceAddress(address);
        discovered.setServicePort(port);
        discovered.setCacheNames(new ArrayList<>(discoveryRegionNames));
        return discovered;
    }

    /**
     * Purges every cached message for a removed peer: its heartbeat/status message (keyed directly by serverId) and its engine-state message (also keyed by
     * serverId), since those would otherwise sit in the JCS cache indefinitely (the mandatory regions never expire elements by age). Now that all engine states
     * for a peer are consolidated into a single message, purging is simplified to two single-key removals.
     */
    private void purgePeerMessages(String serverId) {
        CacheAccess<String, ClusterPeerSecureMessage> heartbeatCache = peerHeartbeatCache;
        if (heartbeatCache != null) {
            heartbeatCache.remove(serverId);
        }
        CacheAccess<String, ClusterEngineStateMessage> engineCache = engineStateCache;
        if (engineCache != null) {
            engineCache.remove(serverId);
        }
    }

    @Override
    public void sendServerStatus(ClusterServerStatusMessage message) {
        // Always publish, even with zero known peers: this is how a peer with no known peers of its own gets discovered
        // by others in the first place (see getObservedPeers()). Skipping the put here would deadlock discovery — every
        // node starts with an empty knownPeers set, so nobody would ever announce itself for anyone else to find.
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        if (cache == null) {
            log.debug("Skipping send to cluster peers because JCS is not initialized. serverId={}", serverId);
            return;
        }
        try {
            ClusterPeerSecureMessage secureMsg = wrapServerStatus(message);
            cache.put(message.getServerId(), secureMsg);
            log.debug("Sent server status. eventType={}, serverId={}, knownPeers.size={}",
                    message.getEventType(), message.getServerId(), knownPeers.size());
        } catch (Exception ex) {
            String msg = String.format("Failed to send server status. eventType=%s, serverId=%s",
                    message.getEventType(), message.getServerId());
            log.warn(msg, ex);
        }
    }

    private ClusterPeerSecureMessage wrapServerStatus(ClusterServerStatusMessage plainMsg) {
        String payload = plainMsg.getEventType() + "|" + plainMsg.getStartTimeMs();
        return new SecureServerStatusMessage(plainMsg.getServerId(), plainMsg.getClusterPartitionId(),
                plainMsg.getVersion(), plainMsg.getTimestamp(), payload, plainMsg);
    }

    @Override
    public void sendEngineStates(ClusterEngineStateMessage message) {
        // Same reasoning as sendServerStatus(): must publish even with zero known peers, or discovery can never bootstrap.
        // Now consolidates all engine states for a peer into a single message stored by peerId (not per-engine key).
        CacheAccess<String, ClusterEngineStateMessage> cache = engineStateCache;
        if (cache == null) {
            log.debug("Skipping engine state message — JCS not initialized. serverId={}", serverId);
            return;
        }
        try {
            cache.put(message.getServerId(), message);
            log.debug("Sent consolidated engine states. engineStatesCount={}, serverId={}",
                    message.getEngineStates().size(), message.getServerId());
        } catch (Exception ex) {
            String msg = String.format("Failed to send consolidated engine states. serverId=%s, engineStatesCount=%d",
                    message.getServerId(), message.getEngineStates().size());
            log.warn(msg, ex);
        }
    }

    @Override
    public ClusterServerStatusMessage getPeerStatusMessage(String peerId) {
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        if (cache == null) {
            return null;
        }
        ClusterPeerSecureMessage secureMsg = cache.get(peerId);
        if (secureMsg == null) {
            return null;
        }
        ClusterPlainMessage plainMsg = converter.toPlainMessage(secureMsg, clusterPartitionId);
        if (plainMsg instanceof ClusterServerStatusMessage) {
            ClusterServerStatusMessage statusMsg = (ClusterServerStatusMessage) plainMsg;
            log.debug("Received cluster-wide message. eventType={}, peerId={}", statusMsg.getEventType(), peerId);
            return statusMsg;
        }
        return null;
    }

    @Override
    public ClusterEngineStateMessage getEngineStateMessage(String peerId) {
        CacheAccess<String, ClusterEngineStateMessage> cache = engineStateCache;
        if (cache == null) {
            return null;
        }
        ClusterEngineStateMessage msg = cache.get(peerId);
        if (msg != null) {
            log.debug("Received consolidated engine states message. engineStatesCount={}, peerId={}", msg.getEngineStates().size(), peerId);
        }
        return msg;
    }

    @Override
    public String getEngineState(String peerId, String engineName) {
        ClusterEngineStateMessage msg = getEngineStateMessage(peerId);
        if (msg != null) {
            String state = msg.getEngineState(engineName);
            if (state != null) {
                log.debug("Received engine state. engineState={}, engineName={}, peerId={}", state, engineName, peerId);
            }
            return state;
        }
        return null;
    }

    @Override
    public ClusterPeerSecureMessage getMessage(String region, String key) {
        if (JcsPropertiesBuilder.PEER_REGION.equals(region)) {
            CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
            if (cache != null) {
                return cache.get(key);
            }
        }
        return null;
    }

    @Override
    public Set<String> getPeerIds() {
        return knownPeers;
    }

    @Override
    public boolean detectIfPeerIsStale(String peerId, long staleThresholdMs) {
        ClusterServerStatusMessage peerStatusMessage = getPeerStatusMessage(peerId);
        return peerStatusMessage == null || peerStatusMessage.isStale(System.currentTimeMillis(), staleThresholdMs);
    }

    @Override
    public Set<ClusterServerStatusMessage> getObservedPeers() {
        CacheAccess<String, ClusterServerStatusMessage> cache = (CacheAccess<String, ClusterServerStatusMessage>) (CacheAccess<?, ?>) peerHeartbeatCache;
        if (cache == null) {
            log.debug("Skipping getObservedPeers() because JCS is not initialized. serverId={}", serverId);
            return Collections.emptySet();
        }
        Set<ClusterServerStatusMessage> result = new HashSet<>();
        for (String peerId : cache.getCacheControl().getKeySet(true)) {
            ClusterServerStatusMessage msg = cache.get(peerId);
            if (msg != null) {
                log.debug("Using observed peer message to compile list of peers. eventType={}, peerId={}, timestamp={}",
                        msg.getEventType(), peerId, msg.getTimestamp());
                result.add(msg);
            }
        }
        log.debug("Compiled list of observed peers. All known peers={}, Observed={}", knownPeers.size(), result.size());
        return result;
    }

    private static class SecureServerStatusMessage extends ClusterPeerSecureMessage {
        private static final long serialVersionUID = 1L;
        private transient String eventType;
        private transient long startTimeMs;

        SecureServerStatusMessage(String serverId, String clusterPartitionId, String version, long timestamp,
                String payload, ClusterServerStatusMessage plainMsg) {
            super(serverId, clusterPartitionId, version, timestamp, payload);
            this.eventType = plainMsg.getEventType();
            this.startTimeMs = plainMsg.getStartTimeMs();
            markDecrypted();
        }

        @Override
        protected void parsePayload(String plainPayload) {
            String[] parts = plainPayload.split("\\|");
            if (parts.length >= 2) {
                this.eventType = parts[0];
                this.startTimeMs = Long.parseLong(parts[1]);
            }
        }

        @Override
        public String getEventType() {
            ensureDecrypted();
            return eventType;
        }

        public long getStartTimeMs() {
            ensureDecrypted();
            return startTimeMs;
        }
    }

    private static class SecureEngineStateMessage extends ClusterPeerSecureMessage {
        private static final long serialVersionUID = 1L;
        private transient Map<String, String> engineStates;

        SecureEngineStateMessage(String serverId, String clusterPartitionId, String version, long timestamp,
                String payload, ClusterEngineStateMessage plainMsg) {
            super(serverId, clusterPartitionId, version, timestamp, payload);
            this.engineStates = plainMsg.getEngineStates();
            markDecrypted();
        }

        @Override
        protected void parsePayload(String plainPayload) {
            // Deserialize engineStates from payload if needed
            this.engineStates = new ConcurrentHashMap<>();
        }

        @Override
        public String getEventType() {
            return ClusterEngineStateMessage.MSG_TYPE_ENGINE_STATES;
        }

        public Map<String, String> getEngineStates() {
            ensureDecrypted();
            return engineStates;
        }
    }
}
