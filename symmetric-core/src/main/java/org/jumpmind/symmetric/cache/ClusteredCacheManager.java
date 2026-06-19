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

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.LoggingConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.service.impl.ClusterService;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * JVM-level singleton that coordinates cluster peer communication and state tracking. Multiple SymmetricDS engines co-hosted on the same JVM share one instance
 * and one heartbeat thread. Transport is delegated to an IClusterCacheCoordinator. When a remote peer is detected as crashed, locks are cleared across all
 * registered engines.
 */
public class ClusteredCacheManager implements IClusteredCacheManager {
    public static final long UPGRADE_WAIT_MS = 60_000L;
    public static final long DEFAULT_HEARTBEAT_MS = 3_000L;
    private static final ClusteredCacheManager GLOBAL_INSTANCE = new ClusteredCacheManager();
    private static final Logger log = LoggerFactory.getLogger(ClusteredCacheManager.class);
    private static final String CLUSTER_HEARTBEAT_THREAD_NAME = "sym-cluster-heartbeat";
    private final IClusterCacheCoordinator coordinator = new JcsTcpCacheCoordinator();
    private final Map<String, ISymmetricEngine> registeredEngines = new ConcurrentHashMap<>();
    private final Map<String, Boolean> peerStateMap = new ConcurrentHashMap<>();
    private final Map<String, Long> peerOfflineTimestampMs = new ConcurrentHashMap<>();
    private final Map<String, Boolean> engineStateMap = new ConcurrentHashMap<>();
    private Thread heartbeatThread;
    private volatile boolean running;
    private volatile long currentHeartbeatMs = DEFAULT_HEARTBEAT_MS;
    private volatile boolean isClusterPeerListenerStarted;
    private volatile String lastBroadcastEventType = ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT;
    private final Map<String, String> lastEngineStates = new ConcurrentHashMap<>();
    private String myServerId;
    private String myInstanceId;
    Runnable exitAction = () -> System.exit(1);

    private ClusteredCacheManager() {
    }

    public static IClusteredCacheManager getInstance() {
        return GLOBAL_INSTANCE;
    }

    @Override
    public synchronized void registerEngine(ISymmetricEngine engine) {
        registeredEngines.put(engine.getEngineName(), engine);
    }

    @Override
    public synchronized void unregisterEngine(ISymmetricEngine engine) {
        registeredEngines.remove(engine.getEngineName());
    }

    @Override
    public synchronized void addPeer(String serverId, Date heartbeatTime) {
        if (serverId == null || isOwnServerId(serverId)) {
            return;
        }
        coordinator.addPeer(serverId);
        ClusterPeerStatusMessage existingMsg = coordinator.getPeerStatusMessage(serverId);
        if (existingMsg != null && (heartbeatTime == null || existingMsg.getTimestamp() >= heartbeatTime.getTime())) {
            log.debug("Skipping peer state seed for {} — JCS message is more recent than database heartbeat", serverId);
            return;
        }
        long staleThresholdMs = getStaleMs(getAnyEngine());
        if (heartbeatTime != null && System.currentTimeMillis() - heartbeatTime.getTime() <= staleThresholdMs) {
            peerStateMap.put(serverId, Boolean.TRUE);
            log.debug("Seeded peer {} as online from database (heartbeat: {})", serverId, heartbeatTime);
        } else {
            recordPeerOffline(serverId);
            log.debug("Seeded peer {} as stale from database (heartbeat: {})", serverId, heartbeatTime);
        }
    }

    @Override
    public boolean recordPeerOffline(String serverId) {
        if (serverId == null || isOwnServerId(serverId)) {
            return false;
        }
        boolean isNew = peerOfflineTimestampMs.putIfAbsent(serverId, System.currentTimeMillis()) == null;
        peerStateMap.putIfAbsent(serverId, Boolean.FALSE);
        return isNew;
    }

    @Override
    public boolean isPeerOfflineLongEnough(String serverId, long staleThresholdMs) {
        Long since = peerOfflineTimestampMs.get(serverId);
        return since != null && System.currentTimeMillis() - since > staleThresholdMs;
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

    public synchronized void startClusterPeerListener(ISecurityService securityService) {
        ClusterPeerSecureMessage.setSecurityService(securityService);
        myInstanceId = ClusterService.initInstanceId(null);
        myServerId = StringUtils.defaultIfBlank(
                System.getProperty(SystemConstants.SYSPROP_CLUSTER_SERVER_ID), AppUtils.getHostName());
        int port = Integer.parseInt(System.getProperty(
                ServerConstants.CLUSTER_JCS_PORT, String.valueOf(1101)));
        ensurePeerListenerStarted(myServerId, myInstanceId, port);
    }

    private synchronized void ensurePeerListenerStarted(String serverId, String instanceId, int port) {
        if (isClusterPeerListenerStarted) {
            return;
        }
        try {
            coordinator.start(serverId, instanceId, port);
            isClusterPeerListenerStarted = true;
        } catch (Exception e) {
            log.error("Failed to start cluster peer listener: {}", e.getMessage());
            throw new RuntimeException("Failed to start cluster peer listener on port " + port, e);
        }
    }

    public boolean isAnyPeerInState(String eventType) {
        for (String peerId : coordinator.getPeerIds()) {
            ClusterPeerStatusMessage msg = coordinator.getPeerStatusMessage(peerId);
            if (msg != null && eventType.equals(msg.getEventType())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAnyPeerOnline() {
        long staleThresholdMs = 3 * DEFAULT_HEARTBEAT_MS;
        long now = System.currentTimeMillis();
        for (String peerId : coordinator.getPeerIds()) {
            if (isPeerAlive(peerId, coordinator.getPeerStatusMessage(peerId), now, staleThresholdMs)) {
                return true;
            }
        }
        return false;
    }

    public void broadcastPeerState(String eventType) {
        if (isClusterPeerListenerStarted) {
            sendMessageToPeers(eventType);
        }
    }

    public void broadcastEngineState(String engineName, String engineState) {
        if (isClusterPeerListenerStarted) {
            lastEngineStates.put(engineName, engineState);
            ClusterEngineStateMessage msg = new ClusterEngineStateMessage(
                    engineState, engineName, myServerId, myInstanceId, Version.version());
            coordinator.sendEngineStateMessage(msg);
        }
    }

    public boolean isAnyPeerWithEngineInState(String engineName, String engineState) {
        long staleThresholdMs = 3 * DEFAULT_HEARTBEAT_MS;
        long now = System.currentTimeMillis();
        for (String peerId : coordinator.getPeerIds()) {
            ClusterEngineStateMessage msg = coordinator.getEngineStateMessage(peerId, engineName);
            if (msg != null && engineState.equals(msg.getEngineState())
                    && !msg.isStale(now, staleThresholdMs)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void startClusterHeartbeat() {
        if (running) {
            return;
        }
        running = true;
        startHeartbeatThread(null);
    }

    public synchronized void stopClusterCommunication() {
        running = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        if (isClusterPeerListenerStarted) {
            sendMessageToPeers(ClusterPeerStatusMessage.EVENT_PEER_LEAVING);
        }
        stopPeerListener();
    }

    private synchronized void stopPeerListener() {
        if (!isClusterPeerListenerStarted) {
            return;
        }
        coordinator.stop();
        isClusterPeerListenerStarted = false;
    }

    private void rebroadcastEngineStates() {
        new java.util.HashMap<>(lastEngineStates).forEach((name, state) -> coordinator.sendEngineStateMessage(new ClusterEngineStateMessage(
                state, name, myServerId, myInstanceId, Version.version())));
    }

    private void sendMessageToPeers(String eventType) {
        lastBroadcastEventType = eventType;
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
                    MDC.put(LoggingConstants.CONTEXT_ENGINE, engine.getParameterService().getEngineName());
                    sleepHeartbeatMs = getHeartbeatMs(engine);
                    currentHeartbeatMs = sleepHeartbeatMs;
                    staleThresholdMs = getStaleMs(engine);
                }
                sendMessageToPeers(lastBroadcastEventType);
                rebroadcastEngineStates();
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
        long defaultMs = DEFAULT_HEARTBEAT_MS;
        if (engine == null) {
            return defaultMs;
        }
        return engine.getParameterService().getLong(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS, defaultMs);
    }

    private long getStaleMs(ISymmetricEngine engine) {
        long defaultMs = 100 * getHeartbeatMs(engine);
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
            for (String engineName : registeredEngines.keySet()) {
                ClusterEngineStateMessage engineMsg = coordinator.getEngineStateMessage(peerId, engineName);
                detectEngineStateAndFireEvents(peerId, engineName, engineMsg, now, staleThresholdMs);
            }
        }
        return activeMembers;
    }

    private void detectEngineStateAndFireEvents(String peerId, String engineName,
            ClusterEngineStateMessage msg, long now, long staleThresholdMs) {
        String key = IClusterCacheCoordinator.generateEngineClusterPeerKey(peerId, engineName);
        boolean isActive = msg != null
                && !ClusterEngineStateMessage.ENGINE_OFFLINE.equals(msg.getEngineState())
                && !msg.isStale(now, staleThresholdMs);
        boolean wasActive = Boolean.TRUE.equals(engineStateMap.get(key));
        if (isActive) {
            engineStateMap.put(key, Boolean.TRUE);
        } else if (wasActive) {
            engineStateMap.put(key, Boolean.FALSE);
            onPeerEngineCrashed(peerId, engineName);
        }
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
        if (ClusterPeerStatusMessage.EVENT_PEER_JOINING.equals(eventType)
                || ClusterPeerStatusMessage.EVENT_PEER_INITIALIZING.equals(eventType)) {
            log.info("Cluster peer is starting up. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, messageFromPeer.getTimestampAsDate(), eventType);
        } else if (ClusterPeerStatusMessage.EVENT_PEER_UPGRADING_DB.equals(eventType)) {
            log.info("Cluster peer is upgrading database. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, messageFromPeer.getTimestampAsDate(), eventType);
        } else {
            long ageMs = now - messageFromPeer.getTimestamp();
            long heartbeatMs = currentHeartbeatMs;
            if (heartbeatMs > 0) {
                long ageIntervals = ageMs / heartbeatMs;
                if (ageIntervals > 0 && ageIntervals % 20 == 0) {
                    log.warn("Cluster peer heartbeat delayed by {} heartbeat interval(s) ({} ms). Peer={}, Last heartbeat={}",
                            ageIntervals, ageMs, peerId, messageFromPeer.getTimestampAsDate());
                } else {
                    log.debug("Cluster peer heartbeat. Peer={}, Last heartbeat={}, EventType={}, now={}, staleThresholdMs={}",
                            peerId, messageFromPeer.getTimestampAsDate(), eventType, now, staleThresholdMs);
                }
            } else {
                log.debug("Cluster peer heartbeat. Peer={}, Last heartbeat={}, EventType={}, now={}, staleThresholdMs={}",
                        peerId, messageFromPeer.getTimestampAsDate(), eventType, now, staleThresholdMs);
            }
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
            MDC.put(LoggingConstants.CONTEXT_ENGINE, engine.getParameterService().getEngineName());
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
                exitAction.run();
                return;
            }
        }
        peerOfflineTimestampMs.remove(msg.getServerId());
        log.info("Cluster peer joined: serverId={} version={}", msg.getServerId(), msg.getVersion());
    }

    private void stopRegisteredEngines() {
        for (ISymmetricEngine engine : registeredEngines.values()) {
            MDC.put(LoggingConstants.CONTEXT_ENGINE, engine.getParameterService().getEngineName());
            engine.stop();
        }
    }

    protected void onPeerCrashed(String serverId) {
        log.warn("Cluster peer JVM {} stopped sending heartbeats. Clearing its orphaned locks.", serverId);
        clearLocksForPeer(serverId);
    }

    protected void onPeerEngineCrashed(String peerId, String engineName) {
        log.warn("Engine {} on peer {} stopped sending heartbeats. Clearing its orphaned locks.", engineName, peerId);
        ISymmetricEngine localEngine = registeredEngines.get(engineName);
        if (localEngine != null) {
            MDC.put(LoggingConstants.CONTEXT_ENGINE, engineName);
            localEngine.getClusterService().clearLocksForServer(peerId);
            localEngine.getNodeCommunicationService().clearLocksForServer(peerId);
        }
    }

    protected void onPeerLeft(String serverId) {
        peerOfflineTimestampMs.remove(serverId);
        log.info("Cluster peer {} left the cluster. Clearing its locks.", serverId);
        clearLocksForPeer(serverId);
    }

    private void clearLocksForPeer(String serverId) {
        for (ISymmetricEngine engine : registeredEngines.values()) {
            MDC.put(LoggingConstants.CONTEXT_ENGINE, engine.getParameterService().getEngineName());
            engine.getClusterService().clearLocksForServer(serverId);
            engine.getNodeCommunicationService().clearLocksForServer(serverId);
            engineStateMap.put(IClusterCacheCoordinator.generateEngineClusterPeerKey(serverId, engine.getEngineName()),
                    Boolean.FALSE);
        }
    }

    private ISymmetricEngine getAnyEngine() {
        return registeredEngines.values().stream().findFirst().orElse(null);
    }
}
