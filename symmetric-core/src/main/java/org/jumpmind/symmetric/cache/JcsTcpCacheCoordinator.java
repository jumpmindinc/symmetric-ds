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
    private static final int DEFAULT_PORT = 1101;
    private static final String JCS_REGION = "SYM_CLUSTER_PEERS";
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private volatile CompositeCacheManager jcsCacheManager;
    private volatile CacheAccess<String, ClusterPeerSecureMessage> peerHeartbeatCache;
    private int port;
    private String serverId;
    private String instanceId;

    @Override
    public void start(ISymmetricEngine engine) {
        this.serverId = engine.getClusterService().getServerId();
        this.instanceId = engine.getClusterService().getInstanceId();
        this.port = engine.getParameterService().getInt(ServerConstants.CLUSTER_JCS_PORT, DEFAULT_PORT);
        String peerList = buildPeerList();
        try {
            jcsCacheManager = CompositeCacheManager.getUnconfiguredInstance();
            jcsCacheManager.configure(buildJcsProperties(peerList));
            peerHeartbeatCache = new CacheAccess<>(jcsCacheManager.getCache(JCS_REGION));
            log.info("Started JCS cluster cache. Port={}, ServerId={}, InstanceId={}, Peers=[{}]", port, serverId, instanceId, peerList);
        } catch (Exception e) {
            log.error("Failed to initialize JCS cluster cache on port {}: {}", port, e.getMessage());
            throw new RuntimeException("Failed to initialize JCS cluster cache on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (jcsCacheManager != null) {
            jcsCacheManager.shutDown();
            jcsCacheManager = null;
            peerHeartbeatCache = null;
        }
    }

    @Override
    public synchronized void addPeer(String serverId) {
        if (knownPeers.add(serverId) && jcsCacheManager != null) {
            reinitJcs();
        }
    }

    @Override
    public void sendMessageToPeers(ClusterPeerSecureMessage message) {
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        if (cache == null) {
            log.debug("Skipping send to cluster peers because JCS is not initialized. serverId={}", serverId);
            return;
        }
        try {
            cache.put(message.getServerId(), message);
            log.debug("Sent cluster-wide message. eventType={}, serverId={}", message.getEventType(), message.getServerId());
        } catch (Exception ex) {
            log.warn("Failed to send cluster-wide message. eventType={}, serverId={}", message.getEventType(), message.getServerId(), ex);
        }
    }

    @Override
    public ClusterPeerSecureMessage getMessage(String peerId) {
        CacheAccess<String, ClusterPeerSecureMessage> cache = peerHeartbeatCache;
        return cache != null ? cache.get(peerId) : null;
    }

    @Override
    public Set<String> getPeerIds() {
        return knownPeers;
    }

    private synchronized void reinitJcs() {
        String peerList = buildPeerList();
        try {
            jcsCacheManager.shutDown();
            jcsCacheManager = CompositeCacheManager.getUnconfiguredInstance();
            jcsCacheManager.configure(buildJcsProperties(peerList));
            peerHeartbeatCache = new CacheAccess<>(jcsCacheManager.getCache(JCS_REGION));
            log.info("Reinitialized JCS cluster cache. Port={}, ServerId={}, InstanceId={}, Peers=[{}]", port, serverId, instanceId, peerList);
        } catch (Exception e) {
            log.error("Failed to reinitialize JCS cluster cache: {}", e.getMessage());
        }
    }

    private Properties buildJcsProperties(String peerList) {
        Properties props = new Properties();
        props.setProperty("jcs.default", "");
        props.setProperty("jcs.region." + JCS_REGION, "LATERAL_TCP");
        props.setProperty("jcs.auxiliary.LATERAL_TCP",
                "org.apache.commons.jcs3.auxiliary.lateral.tcp.LateralTCPCacheFactory");
        props.setProperty("jcs.auxiliary.LATERAL_TCP.attributes",
                "org.apache.commons.jcs3.auxiliary.lateral.tcp.LateralTCPCacheAttributes");
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
