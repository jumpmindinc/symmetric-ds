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

import java.util.Collection;
import java.util.Map;
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
 * IClusterCacheCoordinator implementation that uses Apache JCS lateral TCP cache for peer-to-peer communication. Each known peer requires an explicit server
 * address; adding a new peer triggers a full reinit of the JCS CompositeCacheManager to update the TcpServers list.
 */
public class JcsTcpCacheCoordinator implements IClusterCacheCoordinator {
    private static final Logger log = LoggerFactory.getLogger(JcsTcpCacheCoordinator.class);
    static final int DEFAULT_PORT = 1101;
    private static final String JCS_PEER_REGION = "SYM_CLUSTER_PEERS";
    private static final String JCS_ENGINE_REGION = "SYM_CLUSTER_ENGINES";
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private volatile CompositeCacheManager jcsCacheManager;
    private volatile CacheAccess<String, ClusterPeerSecureMessage> peerHeartbeatCache;
    private volatile CacheAccess<String, ClusterEngineStateMessage> engineStateCache;
    private int port;
    private String serverId;
    private String clusterPartitionId;

    @Override
    public void start(ISymmetricEngine engine) {
        start(engine.getClusterService().getServerId(),
                engine.getClusterService().getClusterPartitionId(),
                engine.getParameterService().getInt(ServerConstants.CLUSTER_JCS_PORT, DEFAULT_PORT));
    }

    @Override
    public synchronized void start(String serverId, String clusterPartitionId, int port) {
        this.serverId = serverId;
        this.clusterPartitionId = clusterPartitionId;
        this.port = port;
        String peerList = buildPeerList();
        try {
            jcsCacheManager = CompositeCacheManager.getUnconfiguredInstance();
            jcsCacheManager.configure(buildJcsProperties(peerList));
            peerHeartbeatCache = new CacheAccess<>(jcsCacheManager.getCache(JCS_PEER_REGION));
            engineStateCache = new CacheAccess<>(jcsCacheManager.getCache(JCS_ENGINE_REGION));
            log.info("Started JCS cluster cache. Port={}, ServerId={}, ClusterPartitionId={}, Peers=[{}]", port, serverId, clusterPartitionId, peerList);
        } catch (Exception e) {
            log.error("Failed to initialize JCS cluster cache on port {}: {}", port, e.getMessage());
            throw new RuntimeException("Failed to initialize JCS cluster cache on port " + port, e);
        }
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
            log.info("Added new peer to cluster. serverId={}, ClusterPartitionId={}", serverId, clusterPartitionId);
            if (jcsCacheManager == null) {
                log.debug("Skipping JCS re-initialization because it was not initialized yet. Peer.serverId={}, ClusterPartitionId={}", serverId,
                        clusterPartitionId);
            } else {
                reinitJcs();
            }
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

    private synchronized void reinitJcs() {
        String peerList = buildPeerList();
        try {
            if (jcsCacheManager != null) {
                jcsCacheManager.shutDown();
            }
            jcsCacheManager = CompositeCacheManager.getUnconfiguredInstance();
            jcsCacheManager.configure(buildJcsProperties(peerList));
            peerHeartbeatCache = new CacheAccess<>(jcsCacheManager.getCache(JCS_PEER_REGION));
            engineStateCache = new CacheAccess<>(jcsCacheManager.getCache(JCS_ENGINE_REGION));
            log.info("Reinitialized JCS cluster cache. Port={}, ServerId={}, ClusterPartitionId={}, Peers=[{}]", port, serverId, clusterPartitionId, peerList);
        } catch (Exception e) {
            log.error("Failed to reinitialize JCS cluster cache: {}", e.getMessage());
        }
    }

    private Properties buildJcsProperties(String peerList) {
        Properties props = new Properties();
        props.setProperty("jcs.default", "");
        props.setProperty("jcs.region." + JCS_PEER_REGION, "LATERAL_TCP");
        props.setProperty("jcs.region." + JCS_ENGINE_REGION, "LATERAL_TCP");
        props.setProperty("jcs.auxiliary.LATERAL_TCP",
                "org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.LateralTCPCacheFactory");
        props.setProperty("jcs.auxiliary.LATERAL_TCP.attributes",
                "org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.TCPLateralCacheAttributes");
        props.setProperty("jcs.auxiliary.LATERAL_TCP.attributes.TcpListenerPort", String.valueOf(port));
        props.setProperty("jcs.auxiliary.LATERAL_TCP.attributes.TcpServers", peerList);
        props.setProperty("jcs.auxiliary.LATERAL_TCP.attributes.AllowGet", "false");
        props.setProperty("jcs.auxiliary.LATERAL_TCP.attributes.Receive", "true");
        return props;
    }

    private String buildPeerList() {
        StringBuilder result = new StringBuilder();
        for (String peerId : knownPeers) {
            if (result.length() > 0) {
                result.append(",");
            }
            result.append(peerId).append(":").append(port);
        }
        return result.toString();
    }
}
