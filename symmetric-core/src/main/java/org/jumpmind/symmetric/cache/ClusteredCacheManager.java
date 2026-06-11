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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.access.exception.CacheException;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JVM-level singleton that uses Apache JCS lateral TCP cache for cluster peer communication. Multiple SymmetricDS engines co-hosted on the same JVM share one
 * instance, one TCP port, and one heartbeat thread. When a remote peer is detected as crashed, locks are cleared across all registered engines.
 */
public class ClusteredCacheManager implements IClusteredCacheManager {
    private static final String THREAD_NAME_HEARTBEAT= "sym-cluster-heartbeat";
    private static final ClusteredCacheManager INSTANCE = new ClusteredCacheManager();
    private static final String REGION = "CLUSTER_PEERS";
    private static final Logger log = LoggerFactory.getLogger(ClusteredCacheManager.class);
    private final Map<String, ISymmetricEngine> registeredEngines = new ConcurrentHashMap<>();
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> peerStateMap = new ConcurrentHashMap<>();
    private volatile CompositeCacheManager jcsCacheManager;
    private volatile CacheAccess<String, ClusterMessage> peerCache;
    private Thread heartbeatThread;
    private volatile boolean running;
    private String myServerId;
    private String myInstanceId;

    private ClusteredCacheManager() {
    }

    public static IClusteredCacheManager getInstance() {
        return INSTANCE;
    }

    @Override
    public synchronized void registerEngine(ISymmetricEngine engine) {
        registeredEngines.put(engine.getEngineName(), engine);
        if (registeredEngines.size() == 1) {
            startInternal(engine);
        }
    }

    @Override
    public synchronized void unregisterEngine(ISymmetricEngine engine) {
        if (registeredEngines.size() == 1 && registeredEngines.containsKey(engine.getEngineName())) {
            stopInternal(engine);
        }
        registeredEngines.remove(engine.getEngineName());
    }

    @Override
    public synchronized void addPeer(String serverId) {
        if (serverId == null || isOwnServerId(serverId)) {
            return;
        }
        if (knownPeers.add(serverId) && jcsCacheManager != null) {
            reinitJcs();
        }
    }

    @Override
    public Set<String> getActiveServerIds() {
        Set<String> active = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : peerStateMap.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                active.add(entry.getKey());
            }
        }
        return active;
    }

    private boolean isOwnServerId(String serverId) {
        for (ISymmetricEngine engine : registeredEngines.values()) {
            if (serverId.equals(engine.getClusterService().getServerId())) {
                return true;
            }
        }
        return false;
    }

    private void startInternal(ISymmetricEngine engine) {
        myServerId = engine.getClusterService().getServerId();
        myInstanceId = engine.getClusterService().getInstanceId();
        running = true;
        initJcs(engine);
        if (running) {
            sendMessageToPeers(ClusterMessage.Type.PEER_JOINING, engine);
            startHeartbeatThread(engine);
        }
    }

    private void stopInternal(ISymmetricEngine lastEngine) {
        running = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        sendMessageToPeers(ClusterMessage.Type.PEER_LEAVING, lastEngine);
        if (jcsCacheManager != null) {
            jcsCacheManager = null;
            peerCache = null;
            jcsCacheManager.shutDown();
        }
    }

    private void initJcs(ISymmetricEngine engine) {
        int port = engine.getParameterService().getInt(ServerConstants.JCS_PORT, 1101);
        String peerList = buildPeerList(port);
        try {
            jcsCacheManager = CompositeCacheManager.getUnconfiguredInstance();
            jcsCacheManager.configure(buildJcsProperties(port, peerList));
            peerCache = new CacheAccess<>(jcsCacheManager.getCache(REGION));
            log.info("Started JCS cluster cache. Port={}, ServerId={}, InstanceId={}, Peers=[{}]", 
                port, myServerId, myInstanceId, peerList);
        } catch (Exception e) {
            log.error("Failed to initialize JCS cluster cache on port {}: {}", port, e.getMessage());
            running = false;
        }
    }

    private void reinitJcs() {
        ISymmetricEngine engine = getAnyEngine();
        if (engine == null) {
            return;
        }
        int port = engine.getParameterService().getInt(ServerConstants.JCS_PORT, 1101);
        String peerList = buildPeerList(port);
        try {
            jcsCacheManager.shutDown();
            jcsCacheManager = CompositeCacheManager.getUnconfiguredInstance();
            jcsCacheManager.configure(buildJcsProperties(port, peerList));
            peerCache = new CacheAccess<>(jcsCacheManager.getCache(REGION));
            log.info("Reinitialized JCS cluster cache. Port={}, ServerId={}, InstanceId={}, Peers=[{}]", 
                port, myServerId, myInstanceId, peerList);
        } catch (Exception e) {
            log.error("Failed to reinitialize JCS cluster cache: {}", e.getMessage());
        }
    }

    private Properties buildJcsProperties(int port, String peerList) {
        Properties props = new Properties();
        props.setProperty("jcs.default", "");
        props.setProperty("jcs.region." + REGION, "LATERAL_TCP");
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

    private String buildPeerList(int port) {
        StringBuilder result = new StringBuilder();
        for (String peerId : knownPeers) {
            if (result.isEmpty()) {
                result.append(",");
            }
            result.append(peerId).append(":").append(port);
        }
        return result.toString();
    }

    /**
     * Broadcasts a message to all cluster peers by putting it in the JCS cache. Peers will receive the message when they do their next heartbeat check.
     * Does not need to be synchronized, because peerCache is read once, atomically.
     */
    private void sendMessageToPeers(ClusterMessage.Type type, ISymmetricEngine engine) {
        CacheAccess<String, ClusterMessage> cache = peerCache;
        if (cache == null ) {
            log.debug("Skipping messaging cluster peers because JCS is not initialized! myServerId={}", myServerId);
            return;
        }        
        if ( engine == null) {
            log.warn("Skipping messaging cluster peers because engine is null! myServerId={}", myServerId);
            return;
        }
        ClusterMessage msg = new ClusterMessage(type, myServerId, myInstanceId, Version.version());
        String details = String.format("Type={}, myServerId={}", type, myServerId);
        try {
            cache.put(myServerId, msg); // Propagates to all peers via JCS
            log.debug("Sent cluster-wide message " + details);
        } catch (Exception ex) {
            log.warn("Failed to send cluster-wide message! " + details, ex);
        }
    }

    private void startHeartbeatThread(ISymmetricEngine orignalEngine) {
        long sleepBetweenHeartbeatsMs = getHeartbeatMs(orignalEngine);
        heartbeatThread = new Thread(() -> monitorClusterPeers(orignalEngine, sleepBetweenHeartbeatsMs), THREAD_NAME_HEARTBEAT);
        heartbeatThread.setDaemon(true);
        log.debug("Initializing cluster peer heartbeat thread = {}", THREAD_NAME_HEARTBEAT);
        heartbeatThread.start();
    }

    private void monitorClusterPeers(ISymmetricEngine orignalEngine, long sleepMs) {
        log.debug("Started cluster peer heartbeat thread = {}", THREAD_NAME_HEARTBEAT);
        while (running) {
            try {
                int activeMembers = 0;
                ISymmetricEngine engine = getAnyEngine();
                if (engine != null) {
                    sleepMs = getHeartbeatMs(engine);
                    sendMessageToPeers(ClusterMessage.Type.PEER_HEARTBEAT, engine);
                    long staleThresholdMs = 3 * engine.getParameterService().getLong(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS, 3000L);
                    activeMembers = checkAllClusterPeers(engine, staleThresholdMs);
                } 
                log.debug("Cluster peer heartbeat completed: activeMembers={}, knownPeers={}, myServerId={}, staleThresholdMs={}, sleepMs={}", 
                    activeMembers, knownPeers.size(), myServerId, staleThresholdMs, sleepMs);
                Thread.sleep(sleepMs);
            } catch (InterruptedException ex) {
                if(log.isDebugEnabled()){
                    log.debug("Cluster peer heartbeat thread interrupted, shutting down.", ex);
                } else {
                    log.info("Cluster peer heartbeat thread interrupted, shutting down. "+ ex.getMessage());
                }
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in cluster peer heartbeat", e);
            }
        }
    }

    private long getHeartbeatMs(ISymmetricEngine engine) {
        ISymmetricEngine e = getAnyEngine();
        if (e == null) {
            e = engine;
        }
        return e.getParameterService().getLong(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS, 3000L);
    }

    private int checkAllClusterPeers(ISymmetricEngine engine, long staleThresholdMs) {
        CacheAccess<String, ClusterMessage> cache = peerCache;
        if (cache == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int activeMembers = 0;
        for (String peerId : knownPeers) {
            ClusterMessage messageFromPeer = cache.get(peerId);
            boolean wasAlive = Boolean.TRUE.equals(peerStateMap.get(peerId));
            if (messageFromPeer != null && messageFromPeer.getType() == ClusterMessage.Type.PEER_LEAVING) {
                if (wasAlive) {
                    onPeerLeft(peerId);
                }
                peerStateMap.remove(peerId);
            } else if (messageFromPeer == null || now - messageFromPeer.getTimestamp() > staleThresholdMs) {
                if (wasAlive) {
                    onPeerCrashed(peerId);
                    peerStateMap.put(peerId, false);
                }
            } else {
                if (!wasAlive) {
                    onPeerJoined(messageFromPeer);
                }
                peerStateMap.put(peerId, true);
            }
        }
        return activeMembers;
    }

    protected void onPeerJoined(ClusterMessage msg) {
        for (ISymmetricEngine engine : registeredEngines.values()) {
            String myInstanceId = engine.getClusterService().getInstanceId();
            if (myInstanceId != null && myInstanceId.equals(msg.getInstanceId())
                    && !engine.getClusterService().getServerId().equals(msg.getServerId())) {
                log.error("Detected another host is already running for the same instance of SymmetricDS. Shutting down.");
                Collection<ISymmetricEngine> engines = registeredEngines.values();
                new Thread(() -> engines.forEach(ISymmetricEngine::stop), "sym-cluster-shutdown").start();
                return;
            }
        }
        log.info("Cluster peer joined: serverId={} version={}", msg.getServerId(), msg.getVersion());
    }

    protected void onPeerCrashed(String serverId) {
        log.warn("Cluster peer {} stopped sending heartbeats. Clearing its orphaned locks.", serverId);
        for (ISymmetricEngine engine : registeredEngines.values()) {
            engine.getClusterService().clearLocksForServer(serverId);
            engine.getNodeCommunicationService().clearLocksForServer(serverId);
        }
    }

    protected void onPeerLeft(String serverId) {
        log.info("Cluster peer {} shut down gracefully.", serverId);
    }

    private ISymmetricEngine getAnyEngine() {
        return registeredEngines.values().stream().findFirst().orElse(null);
    }
}
