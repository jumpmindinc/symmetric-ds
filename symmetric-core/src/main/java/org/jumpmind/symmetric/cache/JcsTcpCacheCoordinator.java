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
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IClusterCacheCoordinator implementation that uses Apache JCS lateral TCP cache for peer-to-peer communication. Peers can find each other through JCS's
 * built-in UDP multicast discovery, or through {@link #announceDiscoveredPeer(String, String)} feeding in addresses learned from SYM_NODE_HOST — needed because
 * UDP multicast is unavailable on most cloud VPCs and managed Kubernetes networks. Either way, the JCS CompositeCacheManager is configured once in
 * {@link #start(CacheCoordinatorNetworkSettings, Set)} and is never torn down or reconfigured as peers come and go. Reconfiguring it on every new peer
 * previously caused JCS to silently keep returning its already-shutdown TCP listener for the port (JCS caches listeners in a static, port-keyed registry that a
 * shutdown never clears), leaving the node unreachable for the rest of its life.
 */
public class JcsTcpCacheCoordinator implements IClusterCacheCoordinator {
    private static final Logger log = LoggerFactory.getLogger(JcsTcpCacheCoordinator.class);
    static final int JCS_TCP_PORT_DEFAULT = 1101;
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet(); // Used for actual network communication
    private volatile ClusterMessageConverter converter;
    private volatile CompositeCacheManager jcsManager;
    private volatile CacheAccess<String, ClusterPeerSecureMessage> peerHeartbeatCache; // JCS-managed cache for peer server status
    private volatile CacheAccess<String, ClusterPeerSecureMessage> engineStateCache; // JCS-managed cache for engine states
    private volatile ICachePeerServerDiscovery discovery;
    private CacheCoordinatorNetworkSettings networkSettings;
    private String myPartitionId;
    private volatile ExecutorService messageDeliveryExecutor; // Actual network cache "puts" run on this single background thread to prevent blocking
    private final AtomicReference<Future<?>> deliveryInFlight = new AtomicReference<>();
    private volatile long deliveryTimeoutMs; // Derived from the heartbeat interval in start(); only read after start() via the executor guard.

    @Override
    public synchronized void start(CacheCoordinatorNetworkSettings networkSettings, Set<RegionSettings> regionSettings, ClusterMessageConverter converter,
            ICachePeerServerDiscovery discovery) {
        this.networkSettings = networkSettings;
        this.converter = converter;
        this.discovery = discovery;
        this.myPartitionId = networkSettings.clusterPartitionId();
        this.deliveryTimeoutMs = networkSettings.deliveryTimeoutMs();
        this.messageDeliveryExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sym-cluster-msg-delivery-" + networkSettings.serverId());
            thread.setDaemon(true);
            return thread;
        });
        Properties jcsProperties = JcsPropertiesBuilder.build(networkSettings, regionSettings);
        discovery.enrichJcsProperties(jcsProperties, JcsPropertiesBuilder.lateralAuxAttributesPrefix());
        Set<String> regionNames = new HashSet<>(Set.of(JcsPropertiesBuilder.PEER_REGION, JcsPropertiesBuilder.ENGINE_REGION));
        for (RegionSettings settings : regionSettings) {
            regionNames.add(settings.regionName());
        }
        try {
            jcsManager = CompositeCacheManager.getUnconfiguredInstance();
            jcsManager.configure(jcsProperties);
            peerHeartbeatCache = new CacheAccess<>(jcsManager.getCache(JcsPropertiesBuilder.PEER_REGION));
            engineStateCache = new CacheAccess<>(jcsManager.getCache(JcsPropertiesBuilder.ENGINE_REGION));
            discovery.start(new DiscoveryContext(jcsManager, networkSettings.port(), regionNames, networkSettings.serverId()));
            log.info("Started JCS cluster communication. Port={}, ServerId={}, ClusterPartitionId={}", networkSettings.port(), networkSettings.serverId(),
                    myPartitionId);
        } catch (Exception ex) {
            String msg = String.format("Failed to initialize JCS cluster communication on port %d", networkSettings.port());
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
        if (!isInitialized()) {
            log.debug("JCS cluster communication was not running, so no shutdown was performed. ServerId={}, ClusterPartitionId={}", networkSettings.serverId(),
                    myPartitionId);
        }
        log.debug("Stopping JCS cluster communication... ServerId={}, ClusterPartitionId={}", networkSettings.serverId(), myPartitionId);
        shutdownMessageDeliveryExecutor();
        if (discovery != null) {
            discovery.stop();
            discovery = null;
        }
        try {
            jcsManager.shutDown();
        } catch (Exception ex) {
            log.warn("Problem while stopping JCS cluster communication", ex);
        }
        jcsManager = null;
        peerHeartbeatCache = null;
        engineStateCache = null;
        log.info("JCS cluster communication stopped. ServerId={}, ClusterPartitionId={}", networkSettings.serverId(), myPartitionId);
    }

    /**
     * Registers a peer's address so JCS's lateral cache can reach it directly, without depending on JCS's own UDP multicast discovery — which is unavailable on
     * most cloud VPCs and managed Kubernetes networks. Uses the {@link IDiscoveryListener} extension point -same as in JCS's own UDP layer.
     */
    @Override
    public synchronized boolean announceDiscoveredPeer(String serverId, String address) {
        if (StringUtils.isBlank(address)) {
            return false;
        }
        if (converter.getRejectedServers().containsKey(serverId)) {
            log.debug("Rejecting discovered peer due to blacklist. serverId={}, address={}, reason={}", serverId, address,
                    converter.getRejectedServers().get(serverId).getReason());
            return false;
        }
        return discovery != null && discovery.announcePeer(serverId, address);
    }

    @Override
    public synchronized boolean addPeer(String serverId) {
        boolean isNewPeer = !knownPeers.contains(serverId);
        if (isNewPeer && converter.getRejectedServers().containsKey(serverId)) {
            log.debug("Rejecting new peer due to blacklist. serverId={}, ClusterPartitionId={}, rejectionReason={}",
                    serverId, myPartitionId, converter.getRejectedServers().get(serverId).getReason());
            return false;
        }
        if (knownPeers.add(serverId)) {
            log.info("Added new peer to cluster. serverId={}, ClusterPartitionId={}, knownPeers.size={}", serverId, myPartitionId, knownPeers.size());
            return true;
        } else {
            log.debug("Peer already known to cluster. serverId={}, ClusterPartitionId={}", serverId, myPartitionId);
            return false;
        }
    }

    @Override
    public synchronized boolean removePeer(String serverId) {
        if (discovery != null) {
            discovery.retractPeer(serverId);
        }
        if (knownPeers.remove(serverId)) {
            purgePeerMessages(serverId);
            log.info("Removed obsolete peer from cluster. serverId={}, ClusterPartitionId={}, knownPeers.size={}", serverId, myPartitionId,
                    knownPeers.size());
            return true;
        } else {
            log.debug("Peer not known to cluster, nothing to remove. serverId={}, ClusterPartitionId={}", serverId, myPartitionId);
            return false;
        }
    }

    /**
     * Purges every cached message for a removed peer: its heartbeat/status message (keyed directly by serverId) and its engine-state message (also keyed by
     * serverId), since those would otherwise sit in the JCS cache indefinitely (the mandatory regions never expire elements by age). Now that all engine states
     * for a peer are consolidated into a single message, purging is simplified to two single-key removals.
     */
    private void purgePeerMessages(String serverId) {
        try {
            CacheAccess<String, ClusterPeerSecureMessage> heartbeatCache = peerHeartbeatCache;
            if (heartbeatCache != null) {
                heartbeatCache.remove(serverId);
            }
            CacheAccess<String, ClusterPeerSecureMessage> engineCache = engineStateCache;
            if (engineCache != null) {
                engineCache.remove(serverId);
            }
        } catch (Exception ex) {
            log.debug("Failed to purge cached messages for removed peer. serverId=" + serverId, ex);
        }
    }

    @Override
    public void sendServerStatus(ClusterServerStatusMessage message) {
        // Always publish, even with zero known peers: this is how a peer with no known peers of its own gets discovered
        // by others in the first place (see getObservedPeers()). Skipping the put here would deadlock discovery — every
        // node starts with an empty knownPeers set, so nobody would ever announce itself for anyone else to find.
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        if (cache == null) {
            log.debug("Skipping send to cluster peers because JCS is not initialized. serverId={}", networkSettings.serverId());
            return;
        }
        deliverWithTimeout("server status", () -> {
            try {
                ClusterPeerSecureMessage secureMsg = converter.toEncryptedMessage(message);
                cache.put(message.getServerId(), secureMsg);
                log.debug("Sent server status. eventType={}, serverId={}, knownPeers.size={}",
                        message.getEventType(), message.getServerId(), knownPeers.size());
            } catch (Exception ex) {
                String msg = String.format("Failed to send server status. eventType=%s, serverId=%s",
                        message.getEventType(), message.getServerId());
                log.warn(msg, ex);
            }
        });
    }

    @Override
    public void sendEngineStates(ClusterEngineStateMessage message) {
        // Same reasoning as sendServerStatus(): must publish even with zero known peers, or discovery can never bootstrap.
        // Now consolidates all engine states for a peer into a single message stored by peerId (not per-engine key).
        CacheAccess<String, ClusterPeerSecureMessage> cache = engineStateCache;
        if (cache == null) {
            log.debug("Skipping engine state message — JCS not initialized. serverId={}", networkSettings.serverId());
            return;
        }
        deliverWithTimeout("engine states", () -> {
            try {
                ClusterPeerSecureMessage secureMsg = converter.toEncryptedMessage(message);
                cache.put(message.getServerId(), secureMsg);
                log.debug("Sent consolidated engine states. engineStatesCount={}, serverId={}",
                        message.getEngineStates().size(), message.getServerId());
            } catch (Exception ex) {
                String msg = String.format("Failed to send consolidated engine states. serverId=%s, engineStatesCount=%d",
                        message.getServerId(), message.getEngineStates().size());
                log.warn(msg, ex);
            }
        });
    }

    /**
     * Runs a lateral-cache delivery on the dedicated background thread and waits at most {@link #deliveryTimeoutMs} (half the heartbeat interval) for it to
     * finish. If a prior delivery is still running — the signature of a blocked transport — this delivery is skipped rather than queued, so the caller's
     * heartbeat loop keeps ticking (discovery, staleness checks, purge) instead of deadlocking on a single unreachable peer. The delivery itself is not
     * cancelled on timeout: interrupting a socket write mid-flight can corrupt the lateral connection, so the stuck task is left to finish or die with the JVM
     * while newer ticks simply skip.
     */
    private void deliverWithTimeout(String description, Runnable deliveryTask) {
        ExecutorService executor = messageDeliveryExecutor;
        if (executor == null) {
            log.debug("Skipping cluster {} delivery because the delivery executor is not running. serverId={}", description, networkSettings.serverId());
            return;
        }
        Future<?> previous = deliveryInFlight.get();
        if (previous != null && !previous.isDone()) {
            log.warn("Skipping cluster {} delivery; the previous delivery has not completed within {} ms and the transport may be blocked. serverId={}",
                    description, deliveryTimeoutMs, networkSettings.serverId());
            return;
        }
        Future<?> future;
        try {
            future = executor.submit(deliveryTask);
        } catch (RejectedExecutionException ex) {
            log.debug("Cluster {} delivery was rejected because the delivery executor is shutting down. serverId={}", description,
                    networkSettings.serverId());
            return;
        }
        deliveryInFlight.set(future);
        try {
            future.get(deliveryTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            log.warn("Cluster {} delivery did not complete within {} ms; continuing without blocking the heartbeat loop. serverId={}",
                    description, deliveryTimeoutMs, networkSettings.serverId());
        } catch (ExecutionException ex) {
            log.warn("Cluster {} delivery failed. serverId={}", description, networkSettings.serverId(), ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void shutdownMessageDeliveryExecutor() {
        ExecutorService executor = messageDeliveryExecutor;
        if (executor != null) {
            executor.shutdownNow();
            messageDeliveryExecutor = null;
        }
        deliveryInFlight.set(null);
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
        return converter.toServerStatusMessage(secureMsg, myPartitionId);
    }

    @Override
    public ClusterEngineStateMessage getEngineStateMessage(String peerId) {
        CacheAccess<String, ClusterPeerSecureMessage> cache = engineStateCache;
        if (cache == null) {
            return null;
        }
        ClusterPeerSecureMessage secureMsg = cache.get(peerId);
        if (secureMsg == null) {
            return null;
        }
        return converter.toEngineStateMessage(secureMsg, myPartitionId);
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
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        if (cache == null) {
            log.debug("Skipping getObservedPeers() because JCS is not initialized. serverId={}", networkSettings.serverId());
            return Collections.emptySet();
        }
        Set<ClusterServerStatusMessage> result = new HashSet<>();
        for (String peerId : cache.getCacheControl().getKeySet(true)) {
            ClusterPeerSecureMessage secureMsg = cache.get(peerId);
            if (secureMsg != null) {
                ClusterServerStatusMessage msg = converter.toServerStatusMessage(secureMsg, myPartitionId);
                if (msg != null) {
                    log.debug("Using observed peer message to compile list of peers. eventType={}, peerId={}, timestamp={}",
                            msg.getEventType(), peerId, msg.getTimestamp());
                    result.add(msg);
                }
            }
        }
        log.debug("Compiled list of observed peers. All known peers={}, Observed={}", knownPeers.size(), result.size());
        return result;
    }
}
