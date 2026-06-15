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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * JVM-level singleton that coordinates cluster peer communication and state tracking. Multiple SymmetricDS engines co-hosted on the same JVM share one instance
 * and one heartbeat thread. Transport is delegated to an IClusterCacheCoordinator. When a remote peer is detected as crashed, locks are cleared across all
 * registered engines.
 */
public class ClusteredCacheManager implements IClusteredCacheManager {
    private static final ClusteredCacheManager GLOBAL_INSTANCE = new ClusteredCacheManager();
    private static final Logger log = LoggerFactory.getLogger(ClusteredCacheManager.class);
    private static final String CLUSTER_HEARTBEAT_THREAD_NAME = "sym-cluster-heartbeat";
    private final IClusterCacheCoordinator coordinator = new JcsTcpCacheCoordinator();
    private final Map<String, ISymmetricEngine> registeredEngines = new ConcurrentHashMap<>();
    private final Map<String, Boolean> peerStateMap = new ConcurrentHashMap<>();
    private Thread heartbeatThread;
    private volatile boolean running;
    private String myServerId;
    private String myInstanceId;

    private ClusteredCacheManager() {
    }

    public static IClusteredCacheManager getInstance() {
        return GLOBAL_INSTANCE;
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
            stopInternal();
        }
        registeredEngines.remove(engine.getEngineName());
    }

    @Override
    public synchronized void addPeer(String serverId) {
        if (serverId == null || isOwnServerId(serverId)) {
            return;
        }
        coordinator.addPeer(serverId);
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
        ClusterPeerSecureMessage.setSecurityService(engine.getSecurityService());
        myServerId = engine.getClusterService().getServerId();
        myInstanceId = engine.getClusterService().getInstanceId();
        running = true;
        try {
            coordinator.start(engine);
        } catch (Exception e) {
            log.error("Failed to start cluster coordinator: {}", e.getMessage());
            running = false;
            return;
        }
        sendMessageToPeers(ClusterPeerStatusMessage.EVENT_PEER_JOINING);
        startHeartbeatThread(engine);
    }

    private void stopInternal() {
        running = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        sendMessageToPeers(ClusterPeerStatusMessage.EVENT_PEER_LEAVING);
        coordinator.stop();
    }

    private void sendMessageToPeers(String eventType) {
        ClusterPeerStatusMessage msg = new ClusterPeerStatusMessage(eventType, myServerId, myInstanceId, Version.version());
        coordinator.sendMessageToPeers(msg);
    }

    private void startHeartbeatThread(ISymmetricEngine originalEngine) {
        long sleepBetweenHeartbeatsMs = getHeartbeatMs(originalEngine);
        heartbeatThread = new Thread(() -> monitorClusterPeers(sleepBetweenHeartbeatsMs), CLUSTER_HEARTBEAT_THREAD_NAME);
        heartbeatThread.setDaemon(true);
        log.debug("Initializing cluster peer heartbeat thread = {}", CLUSTER_HEARTBEAT_THREAD_NAME);
        heartbeatThread.start();
    }

    private void monitorClusterPeers(long sleepHeartbeatMs) {
        log.debug("Started cluster peer heartbeat thread = {}", CLUSTER_HEARTBEAT_THREAD_NAME);
        while (running) {
            try {
                long startTime = System.currentTimeMillis();
                int activeMembers = 0;
                long staleThresholdMs = 0;
                ISymmetricEngine engine = getAnyEngine();
                if (engine != null) {
                    MDC.put("engineName", engine.getParameterService().getEngineName());
                    sleepHeartbeatMs = getHeartbeatMs(engine);
                    staleThresholdMs = getStaleMs(engine);
                }
                sendMessageToPeers(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT);
                activeMembers = checkAllClusterPeers(staleThresholdMs);
                long durationMs = System.currentTimeMillis() - startTime;
                long adjustedSleepMs = Math.max(0, sleepHeartbeatMs - durationMs);
                log.debug("Cluster peer heartbeat completed: activeMembers={}, knownPeers={}, myServerId={}, staleThresholdMs={}, durationMs={}, sleepMs={}",
                        activeMembers, coordinator.getPeerIds().size(), myServerId, staleThresholdMs, durationMs, adjustedSleepMs);
                Thread.sleep(adjustedSleepMs);
            } catch (InterruptedException ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Cluster peer heartbeat thread interrupted, shutting down.", ex);
                } else {
                    log.info("Cluster peer heartbeat thread interrupted, shutting down. " + ex.getMessage());
                }
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in cluster peer heartbeat", e);
            }
        }
    }

    private long getHeartbeatMs(ISymmetricEngine engine) {
        long defaultMs = 3000L;
        if (engine == null) {
            return defaultMs;
        }
        return engine.getParameterService().getLong(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS, defaultMs);
    }

    private long getStaleMs(ISymmetricEngine engine) {
        long defaultMs = 3 * getHeartbeatMs(engine);
        if (engine == null) {
            return defaultMs;
        }
        return engine.getParameterService().getLong(ParameterConstants.CLUSTER_PEER_STALE_MS, defaultMs);
    }

    private int checkAllClusterPeers(long staleThresholdMs) {
        long now = System.currentTimeMillis();
        int activeMembers = 0;
        for (String peerId : coordinator.getPeerIds()) {
            ClusterPeerSecureMessage messageFromPeer = coordinator.getPeerStatusMessage(peerId);
            if (dispatchMessage(peerId, messageFromPeer, now, staleThresholdMs)) {
                activeMembers++;
            }
        }
        return activeMembers;
    }

    private boolean isPeerAlive(String peerId, ClusterPeerSecureMessage messageFromPeer, long now, long staleThresholdMs) {
        if (messageFromPeer == null) {
            log.debug("Skipping null message from cluster peer={}", peerId);
            return false;
        }
        if (!messageFromPeer.isHeaderChecksumValid()) {
            log.warn("Rejecting message from cluster peer={} — checksum invalid, message may be corrupt or from an unauthorized host", peerId);
            return false;
        }
        String eventType = messageFromPeer.getEventType();
        if (ClusterPeerStatusMessage.EVENT_PEER_LEAVING.equals(eventType)) {
            log.info("Cluster peer sent message about leaving the cluster. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, messageFromPeer.getTimestampAsDate(), eventType);
            return false;
        }
        if (messageFromPeer.isStale(now, staleThresholdMs)) {
            log.warn("Last message from cluster peer is stale! Considering peer inactive. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, messageFromPeer.getTimestampAsDate(), eventType);
            return false;
        }
        if (ClusterPeerStatusMessage.EVENT_PEER_JOINING.equals(eventType)) {
            log.info("Cluster peer sent message about joining this cluster. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, messageFromPeer.getTimestampAsDate(), eventType);
        } else {
            log.debug("Processing heartbeat message from cluster peer={}, Last heartbeat={}, EventType={}, now={}, staleThresholdMs={}",
                    peerId, messageFromPeer.getTimestampAsDate(), eventType, now, staleThresholdMs);
        }
        return true;
    }

    private boolean dispatchMessage(String peerId, ClusterPeerSecureMessage message, long now, long staleThresholdMs) {
        // Future: route to type-specific handlers based on message.getEventType() or the cache region it arrived from.
        // Additional coordinator regions (e.g. cache invalidation) would add branches here without changing the state machine.
        return detectPeerStateAndFireEvents(peerId, message, now, staleThresholdMs);
    }

    private boolean detectPeerStateAndFireEvents(String peerId, ClusterPeerSecureMessage messageFromPeer, long now, long staleThresholdMs) {
        boolean peerIsActive = isPeerAlive(peerId, messageFromPeer, now, staleThresholdMs);
        boolean wasAlive = Boolean.TRUE.equals(peerStateMap.get(peerId));
        if (peerIsActive) {
            peerStateMap.put(peerId, true);
            if (!wasAlive) {
                onPeerJoined(messageFromPeer);
            }
        } else if (wasAlive) {
            if (messageFromPeer == null || messageFromPeer.isStale(now, staleThresholdMs)) {
                peerStateMap.put(peerId, false);
                onPeerCrashed(peerId);
            } else {
                peerStateMap.remove(peerId);
                onPeerLeft(peerId);
            }
        }
        return peerIsActive;
    }

    protected void onPeerJoined(ClusterPeerSecureMessage msg) {
        String peerInstanceId = msg instanceof ClusterPeerStatusMessage ? ((ClusterPeerStatusMessage) msg).getInstanceId() : null;
        for (ISymmetricEngine engine : registeredEngines.values()) {
            MDC.put("engineName", engine.getParameterService().getEngineName());
            String myInstanceId = engine.getClusterService().getInstanceId();
            if (peerInstanceId != null) {
                if (myInstanceId.equals(peerInstanceId)) {
                    log.info("Detected another host is already running for the same instance of SymmetricDS.");
                } else {
                    log.warn("Detected another host is already running with a different instance of SymmetricDS.");
                }
            }
            if (!engine.getParameterService().is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
                log.error("Detected another cluster peer {} but cluster.lock.enabled=false. "
                        + "Multiple SymmetricDS instances cannot share a database without cluster locking! Shutting down.",
                        msg.getServerId());
                stopRegisteredEngines();
                System.exit(1);
                return;
            }
        }
        log.info("Cluster peer joined: serverId={} version={}", msg.getServerId(), msg.getVersion());
    }

    private void stopRegisteredEngines() {
        for (ISymmetricEngine engine : registeredEngines.values()) {
            MDC.put("engineName", engine.getParameterService().getEngineName());
            engine.stop();
        }
    }

    protected void onPeerCrashed(String serverId) {
        log.warn("Cluster peer {} stopped sending heartbeats. Clearing its orphaned locks.", serverId);
        for (ISymmetricEngine engine : registeredEngines.values()) {
            MDC.put("engineName", engine.getParameterService().getEngineName());
            engine.getClusterService().clearLocksForServer(serverId);
            engine.getNodeCommunicationService().clearLocksForServer(serverId);
        }
    }

    protected void onPeerLeft(String serverId) {
        log.info("Cluster peer {} was removed from rotation.", serverId);
    }

    private ISymmetricEngine getAnyEngine() {
        return registeredEngines.values().stream().findFirst().orElse(null);
    }
}
