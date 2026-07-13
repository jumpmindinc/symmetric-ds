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

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.CacheCoordinatorNetworkSettings;
import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.LoggingConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * JVM-level singleton that coordinates cluster peer communication and state tracking. Network transport is delegated to an IClusterCacheCoordinator (
 * JcsTcpCacheCoordinator ).
 */
public class ClusteredCacheManager implements IClusteredCacheManager {
    private static final ClusteredCacheManager GLOBAL_INSTANCE = new ClusteredCacheManager();
    private static final Logger log = LoggerFactory.getLogger(ClusteredCacheManager.class);
    private static final String CLUSTER_HEARTBEAT_THREAD_NAME = "sym-cluster-heartbeat";
    private static final String CLUSTERED_CACHE_LOG_CONTEXT = "sym_clustered_cache";
    private static final long HEARTBEAT_MIN_SLEEP_DELAY_MS = 20L; // Thread.Sleep(x) for X than this value is irrelevant
    private volatile IClusterCacheCoordinator peerNetworkCoordinator;
    private volatile ClusterMessageConverter converter;
    private volatile ICachePeerServerDiscovery peerDiscovery;
    private final Map<String, ISymmetricEngine> registeredEngines = new ConcurrentHashMap<>();
    private final Map<String, Boolean> peerWasPreviouslyAlive = new ConcurrentHashMap<>();
    private final Map<String, Boolean> engineStateMap = new ConcurrentHashMap<>();
    private final Map<String, IClusteredCacheManager.PeerState> peerStates = new ConcurrentHashMap<>();
    private Thread heartbeatThread;
    private volatile boolean isInitializationComplete = false;
    private volatile boolean isHeartbeatLoopRunning = false;
    private volatile long currentHeartbeatMs = ServerConstants.CLUSTER_PEER_HEARTBEAT_DEFAULT_MS;
    private volatile long currentStaleThresholdMs = ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS;
    private volatile long lastHeartbeatSummaryLogMs;
    private volatile boolean isClusterPeerListenerStarted;
    private volatile boolean isClusterLockingEnabled;
    private volatile String lastBroadcastEventType = ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT;
    private final Map<String, String> lastEngineStates = new ConcurrentHashMap<>();
    private String myServerId;
    private String myClusterPartitionId;
    private long myStartTimeMs;
    private volatile Object symmetricEngineHolder;
    // Runs exitProcess() on its own thread to prevent deadlocks in synchronized methods inside AbstractSymmetricEngine.
    Runnable exitProcessAction = () -> new Thread(this::exitProcess, "sym-cluster-exit").start();

    private ClusteredCacheManager() {
    }

    @Override
    public boolean isInitialized() {
        return isInitializationComplete;
    }

    public static IClusteredCacheManager getInstance() {
        return GLOBAL_INSTANCE;
    }

    @Override
    public synchronized void registerEngine(ISymmetricEngine engine) {
        registeredEngines.put(engine.getEngineName(), engine);
    }

    @Override
    public synchronized void registerEngine(ISymmetricEngine engine, ClusteredEngineState initialEngineState) {
        registerEngine(engine);
        broadcastEngineState(engine.getEngineName(), initialEngineState);
    }

    @Override
    public synchronized void unregisterEngine(ISymmetricEngine engine) {
        String engineName = engine.getEngineName();
        registeredEngines.remove(engineName);
        this.engineStateMap.put(engineName, false);
        broadcastEngineState(engineName, ClusteredEngineState.OFFLINE);
    }

    @Override
    public synchronized boolean addPeer(String serverId, Date historicalHeartbeat, String peerClusterPartitionId) {
        if (!isInitialized() || serverId == null || isOwnServerId(serverId)) {
            return false;
        }
        if (peerClusterPartitionId != null && !peerClusterPartitionId.equals(myClusterPartitionId)) {
            log.warn("Rejecting cluster peer due to partition ID mismatch! ServerId={}, peerClusterPartitionId={}, myClusterPartitionId={}",
                    serverId, peerClusterPartitionId, myClusterPartitionId);
            return false;
        }
        boolean peerIsRejected = converter.getRejectedServers().containsKey(serverId);
        if (peerIsRejected) {
            log.debug("Rejecting new peer due to blacklist. ServerId={}, ClusterPartitionId={}, rejectionReason={}",
                    serverId, myClusterPartitionId, converter.getRejectedServers().get(serverId).getReason());
            return false;
        }
        boolean isNewPeer = peerNetworkCoordinator.addPeer(serverId);
        if (!isNewPeer) {
            ClusterServerStatusMessage peerStatusMessage = peerNetworkCoordinator.getPeerStatusMessage(serverId);
            if (peerStatusMessage == null) {
                log.info(
                        "This cluster peer is not new and was never detected by JCS (likely offline). ServerId={}, Last known heartbeat={}, ClusterPartitionId={}",
                        serverId, historicalHeartbeat, myClusterPartitionId);
            } else {
                log.debug("This cluster peer is not new. ServerId={}, Last known heartbeat={}, JCS heartbeat={}, ClusterPartitionId={}",
                        serverId, historicalHeartbeat, peerStatusMessage.getTimestampAsString(), myClusterPartitionId);
            }
            return false;
        }
        boolean isHeartbeatStale = historicalHeartbeat != null
                ? (System.currentTimeMillis() - historicalHeartbeat.getTime() > this.currentStaleThresholdMs)
                : peerNetworkCoordinator.detectIfPeerIsStale(serverId, this.currentStaleThresholdMs);
        if (!isHeartbeatStale) {
            log.debug("Added cluster peer. ServerId={}, Last known heartbeat={}, ClusterPartitionId={}",
                    serverId, historicalHeartbeat, myClusterPartitionId);
            historicalHeartbeat = historicalHeartbeat != null ? historicalHeartbeat : new Date();
            long peerStartTimeMs = historicalHeartbeat.getTime();
            for (ISymmetricEngine engine : registeredEngines.values()) {
                if (!engine.getClusterService().isClusteringEnabled()) {
                    enforceClusterLockingOrExit(serverId, peerStartTimeMs);
                    break;
                }
            }
        } else {
            log.debug("Added cluster peer as stale. ServerId={}, Last known heartbeat={}, ClusterPartitionId={}",
                    serverId, historicalHeartbeat, myClusterPartitionId);
        }
        return isNewPeer;
    }

    @Override
    public synchronized boolean removePeer(String serverId) {
        if (serverId == null || isOwnServerId(serverId)) {
            return false;
        }
        return peerNetworkCoordinator != null && peerNetworkCoordinator.removePeer(serverId);
    }

    @Override
    public synchronized boolean announceDiscoveredPeer(String serverId, String address) {
        if (serverId == null || isOwnServerId(serverId)) {
            return false;
        }
        return peerNetworkCoordinator.announceDiscoveredPeer(serverId, address);
    }

    @Override
    public boolean recordPeerOffline(String serverId) {
        if (serverId == null || isOwnServerId(serverId)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isPeerOfflineLongEnough(String serverId, long staleThresholdMs) {
        long now = System.currentTimeMillis();
        ClusterServerStatusMessage msg = peerNetworkCoordinator.getPeerStatusMessage(serverId);
        if (msg != null && !isPeerAlive(serverId, msg, now, staleThresholdMs)) {
            return msg.isStale(now, staleThresholdMs);
        }
        return false;
    }

    @Override
    public Set<String> getActiveServerIds() {
        if (!isInitialized()) {
            throw new RuntimeException("Service was not yet initialized!");
        }
        Set<String> active = new HashSet<>();
        long staleThresholdMs = ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS;
        long now = System.currentTimeMillis();
        for (String peerId : peerNetworkCoordinator.getPeerIds()) {
            ClusterServerStatusMessage msg = peerNetworkCoordinator.getPeerStatusMessage(peerId);
            if (isPeerAlive(peerId, msg, now, staleThresholdMs)) {
                active.add(peerId);
            }
        }
        return active;
    }

    /**
     * Checks {@code myServerId} (this JVM's own announced identity, valid for its whole lifetime) before falling back to scanning registered engines. Relying
     * on registered engines alone is unsafe: once the last engine unregisters (e.g. after a crash), this JVM's own lingering heartbeat message could be
     * misclassified as belonging to a genuinely new external peer, since there would be no registered engine left to recognize it as "self".
     */
    private boolean isOwnServerId(String serverId) {
        if (serverId.equals(myServerId)) {
            return true;
        }
        for (ISymmetricEngine engine : registeredEngines.values()) {
            if (serverId.equals(engine.getClusterService().getServerId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isClusterPeerListenerStarted() {
        return isClusterPeerListenerStarted;
    }

    /**
     * Initial entry point (without nodes started yet): brings up JCS peer announcement/discovery with no database dependency, so this node is visible to peers
     * quickly. The cluster partition ID and server ID should be resolved as start up parameters.
     */
    @Override
    public synchronized void initialize(ISecurityService securityService, String clusterPartitionId, String serverId, boolean isClusterLockingEnabled,
            Object engineHolder) {
        this.symmetricEngineHolder = engineHolder;
        this.isClusterLockingEnabled = isClusterLockingEnabled;
        myClusterPartitionId = clusterPartitionId;
        myServerId = serverId;
        if (this.isClusterLockingEnabled) {
            initializeClusterCommunicationAndDiscovery(securityService);
            startClusterHeartbeatThread();
        } else {
            log.debug("Skipped cluster cache and lock initialization, because parameter is turned off");
        }
        this.isInitializationComplete = true;
    }

    private void initializeClusterCommunicationAndDiscovery(ISecurityService securityService) {
        if (!securityService.isInitialized()) {
            securityService.init();
        }
        if (converter == null) {
            converter = new ClusterMessageConverter(securityService, myClusterPartitionId);
        }
        if (peerNetworkCoordinator == null) {
            peerNetworkCoordinator = AppUtils.newInstance(IClusterCacheCoordinator.class, JcsTcpCacheCoordinator.class);
        }
        int jcsPort = Integer.parseInt(System.getProperty(ServerConstants.CLUSTER_JCS_PORT, String.valueOf(1101)));
        String discoveryMode = System.getProperty(ServerConstants.CLUSTER_PEER_DISCOVERY, ServerConstants.CLUSTER_PEER_DISCOVERY_DB);
        if (peerDiscovery == null) {
            ICachePeerServerDiscoveryFactory discoveryFactory = AppUtils.newInstance(ICachePeerServerDiscoveryFactory.class,
                    CachePeerServerDiscoveryFactory.class);
            peerDiscovery = discoveryFactory.create(discoveryMode);
        }
        if (isClusterLockingEnabled) {
            myStartTimeMs = System.currentTimeMillis();
            CacheCoordinatorNetworkSettings networkSettings = new CacheCoordinatorNetworkSettings(myServerId,
                    myClusterPartitionId, jcsPort, discoveryMode, currentHeartbeatMs);
            startClusterPeerListener(networkSettings);
        }
    }

    private synchronized void startClusterPeerListener(CacheCoordinatorNetworkSettings networkSettings) {
        if (isClusterPeerListenerStarted) {
            log.debug("Skipping redundant JCS cluster peer listener start on {}", myServerId);
            return;
        }
        String serverInfo = String.format("serverId=%s, clusterPartitionId=%s, port=%d, discoveryMode=%s",
                networkSettings.serverId(), networkSettings.clusterPartitionId(), networkSettings.port(), networkSettings.discoveryMode());
        try {
            log.debug("Starting JCS cluster peer listener on {}", serverInfo);
            peerNetworkCoordinator.start(networkSettings, Collections.emptySet(), converter, peerDiscovery);
            isClusterPeerListenerStarted = true;
            log.info("Started JCS cluster peer listener on {}", serverInfo);
        } catch (Exception ex) {
            log.debug("Failed to start JCS cluster peer listener on " + serverInfo, ex);
            throw new RuntimeException("Failed to start JCS cluster peer listener on " + serverInfo, ex);
        }
    }

    @Override
    public boolean isClusterLockingEnabled() {
        return isClusterLockingEnabled;
    }

    @Override
    public String getClusterPartitionId() {
        return myClusterPartitionId;
    }

    @Override
    public String getServerId() {
        return myServerId;
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return currentHeartbeatMs;
    }

    @Override
    public long getStaleIntervalMs() {
        return currentStaleThresholdMs;
    }

    @Override
    public boolean isAnyPeerInState(String eventType) {
        if (!isInitialized()) {
            throw new RuntimeException("Service was not yet initialized!");
        }
        for (String peerId : peerNetworkCoordinator.getPeerIds()) {
            ClusterServerStatusMessage msg = peerNetworkCoordinator.getPeerStatusMessage(peerId);
            if (msg != null && eventType.equals(msg.getEventType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAnyPeerOnline() {
        if (!isInitialized()) {
            throw new RuntimeException("Service was not yet initialized!");
        }
        long staleThresholdMs = ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS;
        long now = System.currentTimeMillis();
        for (String peerId : peerNetworkCoordinator.getPeerIds()) {
            if (isPeerAlive(peerId, peerNetworkCoordinator.getPeerStatusMessage(peerId), now, staleThresholdMs)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void broadcastStateToPeers(ClusterPeerServerState state) {
        if (isClusterPeerListenerStarted) {
            sendMessageToPeers(state.getValue());
        }
    }

    @Override
    public void broadcastEngineState(String engineName, ClusteredEngineState engineState) {
        if (!isInitialized()) {
            throw new RuntimeException("Service was not yet initialized!");
        }
        lastEngineStates.put(engineName, engineState.getValue());
        if (isClusterPeerListenerStarted) {
            Map<String, String> currentStatesOfEngines = new HashMap<>(lastEngineStates);
            ClusterEngineStateMessage msg = new ClusterEngineStateMessage(
                    currentStatesOfEngines, myServerId, myClusterPartitionId);
            peerNetworkCoordinator.sendEngineStates(msg);
        }
    }

    @Override
    public void rebroadcastCurrentState() {
        if (!isInitialized()) {
            throw new RuntimeException("Service was not yet initialized!");
        }
        if (isClusterPeerListenerStarted) {
            broadcastCurrentStateAndEngines();
        }
    }

    @Override
    public boolean isAnyPeerWithEngineInState(String engineName, ClusteredEngineState engineState) {
        if (!isInitialized()) {
            throw new RuntimeException("Service was not yet initialized!");
        }
        long staleThresholdMs = ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS;
        long now = System.currentTimeMillis();
        String stateValue = engineState.getValue();
        for (String peerId : peerNetworkCoordinator.getPeerIds()) {
            ClusterServerStatusMessage peerStatusMsg = peerNetworkCoordinator.getPeerStatusMessage(peerId);
            if (peerStatusMsg == null || peerStatusMsg.isStale(now, staleThresholdMs)) {
                continue;
            }
            ClusterEngineStateMessage engineStateMsg = peerNetworkCoordinator.getEngineStateMessage(peerId);
            if (engineStateMsg != null && !engineStateMsg.isStale(now, staleThresholdMs)) {
                String engineStateValue = engineStateMsg.getEngineState(engineName);
                if (stateValue.equals(engineStateValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected synchronized void startClusterHeartbeatThread() {
        if (this.isHeartbeatLoopRunning || !isClusterPeerListenerStarted) {
            log.debug("Skipping start of cluster peer heartbeat thread because isHeartbeatLoopRunning={} or isClusterPeerListenerStarted={}",
                    this.isHeartbeatLoopRunning, isClusterPeerListenerStarted);
            return;
        }
        this.isHeartbeatLoopRunning = true;
        heartbeatThread = new Thread(() -> runClusterHeartbeatLoop(), CLUSTER_HEARTBEAT_THREAD_NAME);
        heartbeatThread.setDaemon(true);
        log.debug("Starting cluster peer heartbeat thread = {}", CLUSTER_HEARTBEAT_THREAD_NAME);
        heartbeatThread.start();
    }

    @Override
    public synchronized void shutdown() {
        this.isInitializationComplete = false;
        this.isHeartbeatLoopRunning = false;
        if (heartbeatThread != null) {
            try {
                heartbeatThread.interrupt();
            } catch (Exception ex) {
                log.warn("Problem interrupting cluster network heartbeat thread! ", ex);
            }
        }
        for (String engineName : lastEngineStates.keySet()) {
            lastEngineStates.put(engineName, ClusteredEngineState.OFFLINE.getValue());
        }
        if (peerDiscovery != null) {
            peerDiscovery.stop();
            peerDiscovery = null;
        }
        if (peerNetworkCoordinator != null && peerNetworkCoordinator.isInitialized()) {
            try {
                if (isClusterPeerListenerStarted) {
                    sendMessageToPeers(ClusterServerStatusMessage.EVENT_PEER_LEAVING);
                    broadcastCurrentEngineStates();
                }
                peerNetworkCoordinator.stop();
            } catch (Exception ex) {
                log.warn("Problem stopping network peer coordinator! ", ex);
            }
        }
        peerNetworkCoordinator = null;
        heartbeatThread = null;
        isClusterPeerListenerStarted = false;
    }

    private void broadcastCurrentStateAndEngines() {
        sendMessageToPeers(lastBroadcastEventType);
        broadcastCurrentEngineStates();
    }

    private void broadcastCurrentEngineStates() {
        Map<String, ClusteredEngineState> currentEnginePeerStates = getCurrentEngineStateSnapshot();
        Map<String, String> stateStrings = convertEngineStatesToStrings(currentEnginePeerStates);
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(
                stateStrings, myServerId, myClusterPartitionId);
        peerNetworkCoordinator.sendEngineStates(msg);
    }

    private Map<String, ClusteredEngineState> getCurrentEngineStateSnapshot() {
        if (symmetricEngineHolder != null) {
            try {
                return invokeSymmetricEngineHolderMethod("buildCurrentEngineStateSnapshot");
            } catch (Exception ex) {
                log.warn("Failed to get engine state snapshot from SymmetricEngineHolder, falling back to registered engines", ex);
            }
        }
        return buildCurrentEngineStateSnapshotFromRegistered();
    }

    private Map<String, ClusteredEngineState> buildCurrentEngineStateSnapshotFromRegistered() {
        Map<String, ClusteredEngineState> snapshot = new HashMap<>();
        for (String engineName : lastEngineStates.keySet()) {
            String stateStr = lastEngineStates.get(engineName);
            try {
                snapshot.put(engineName, ClusteredEngineState.valueOf(stateStr.toUpperCase()));
            } catch (Exception ex) {
                log.warn("Failed to parse engine state for engine={}. stateStr={}", engineName, stateStr);
                snapshot.put(engineName, ClusteredEngineState.OFFLINE);
            }
        }
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ClusteredEngineState> invokeSymmetricEngineHolderMethod(String methodName) throws Exception {
        return (Map<String, ClusteredEngineState>) symmetricEngineHolder.getClass()
                .getDeclaredMethod(methodName)
                .invoke(symmetricEngineHolder);
    }

    private Map<String, String> convertEngineStatesToStrings(Map<String, ClusteredEngineState> engineStates) {
        Map<String, String> stringStates = new HashMap<>();
        for (Map.Entry<String, ClusteredEngineState> entry : engineStates.entrySet()) {
            stringStates.put(entry.getKey(), entry.getValue().getValue());
        }
        return stringStates;
    }

    private void sendMessageToPeers(String eventType) {
        lastBroadcastEventType = eventType;
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(eventType, myServerId, myClusterPartitionId, myStartTimeMs);
        peerNetworkCoordinator.sendServerStatus(msg);
    }

    private void runClusterHeartbeatLoop() {
        log.debug("Started cluster peer heartbeat thread={}", CLUSTER_HEARTBEAT_THREAD_NAME);
        MDC.put(LoggingConstants.CONTEXT_ENGINE, CLUSTERED_CACHE_LOG_CONTEXT);
        while (this.isHeartbeatLoopRunning) {
            try {
                executeClusterHeartbeatAndDiscoveryTick();
            } catch (InterruptedException ex) {
                log.info("Cluster peer heartbeat thread interrupted, shutting down. " + ex.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in cluster peer heartbeat", e);
            }
        }
        log.debug("Ended cluster peer heartbeat thread={}", CLUSTER_HEARTBEAT_THREAD_NAME);
    }

    private void executeClusterHeartbeatAndDiscoveryTick() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long staleThresholdMs = refreshStaleThreshold();
        long sleepBetweenHeartbeatsMs = refreshSleepBetweenHeartbeats();
        discoverPeersIncomingHeartbeats();
        discoverPeersFromNodeHostTable();
        broadcastCurrentStateAndEngines();
        updateOwnNodeHostHeartbeat();
        if (log.isDebugEnabled()) {
            logEngineStates();
            logPeerStates();
        }
        purgeObsoletePeers(startTime, getObsoleteMs(getAnyEngine()));
        if (this.isHeartbeatLoopRunning) {
            sleepUntilNextHeartbeat(startTime, sleepBetweenHeartbeatsMs, staleThresholdMs);
        } else {
            log.debug("Cluster peer heartbeat loop is no longer active, skipping sleep.");
        }
    }

    private void sleepUntilNextHeartbeat(long startTime, long sleepBetweenHeartbeatsMs, long staleThresholdMs) throws InterruptedException {
        // These counts only track remote peers; the current server is also an active member of the cluster, noted separately in the log message below.
        int activeMembersCount = countActivePeers(staleThresholdMs);
        int knownPeersCount = peerNetworkCoordinator.getPeerIds().size();
        long now = System.currentTimeMillis();
        long durationMs = now - startTime;
        long adjustedSleepMs = sleepBetweenHeartbeatsMs - durationMs;
        if (adjustedSleepMs < HEARTBEAT_MIN_SLEEP_DELAY_MS) {
            if (adjustedSleepMs < 0) {
                log.warn(
                        "Cluster peer heartbeat completed, but system is slow - processing took so long that the next cycle is overdue! Active peers={} (plus myself), Known peers={}, myServerId={}, ClusterPartitionId={}, staleThresholdMs={}, Tick duration={} ms, sleepMs={}",
                        activeMembersCount, knownPeersCount, myServerId, myClusterPartitionId, staleThresholdMs, durationMs, adjustedSleepMs);
            } else {
                log.info(
                        "Cluster peer heartbeat completed, but processing took so long that the next cycle is due. Active peers={} (plus myself), Known peers={}, myServerId={}, ClusterPartitionId={}, staleThresholdMs={}, Tick duration={} ms, sleepMs={}",
                        activeMembersCount, knownPeersCount, myServerId, myClusterPartitionId, staleThresholdMs, durationMs, adjustedSleepMs);
            }
            return; // No sleep!
        }
        if (staleThresholdMs > 0 && now - lastHeartbeatSummaryLogMs >= staleThresholdMs) {
            lastHeartbeatSummaryLogMs = now;
            log.info(
                    "Cluster peer heartbeat completed: Active peers={} (plus myself), Known peers={}, myServerId={}, ClusterPartitionId={}, staleThresholdMs={}, Tick duration={} ms, sleepMs={}",
                    activeMembersCount, knownPeersCount, myServerId, myClusterPartitionId, staleThresholdMs, durationMs, adjustedSleepMs);
        } else {
            log.debug(
                    "Cluster peer heartbeat completed: Active peers={} (plus myself), Known peers={}, myServerId={}, ClusterPartitionId={}, staleThresholdMs={}, Tick duration={} ms, sleepMs={}",
                    activeMembersCount, knownPeersCount, myServerId, myClusterPartitionId, staleThresholdMs, durationMs, adjustedSleepMs);
        }
        Thread.sleep(adjustedSleepMs);
    }

    private long refreshSleepBetweenHeartbeats() {
        long sleepBetweenHeartbeatsMs = this.currentHeartbeatMs;
        ISymmetricEngine engine = getAnyEngine();
        if (engine != null) {
            sleepBetweenHeartbeatsMs = engine.getParameterService().getLong(ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS,
                    sleepBetweenHeartbeatsMs);
            if (this.currentHeartbeatMs != sleepBetweenHeartbeatsMs) {
                log.info("Cluster heartbeat interval changed from {} ms to {} ms based on parameters for engine={}",
                        this.currentHeartbeatMs, sleepBetweenHeartbeatsMs, engine.getEngineName());
                this.currentHeartbeatMs = sleepBetweenHeartbeatsMs;
            }
        }
        return sleepBetweenHeartbeatsMs;
    }

    private long refreshStaleThreshold() {
        long staleThresholdMs = this.currentStaleThresholdMs;
        ISymmetricEngine engine = getAnyEngine();
        if (engine != null) {
            staleThresholdMs = engine.getParameterService().getLong(ParameterConstants.CLUSTER_PEER_STALE_MS, staleThresholdMs);
            if (this.currentStaleThresholdMs != staleThresholdMs) {
                log.info("Cluster stale threshold changed from {} ms to {} ms based on parameters for engine={}",
                        this.currentStaleThresholdMs, staleThresholdMs, engine.getEngineName());
                this.currentStaleThresholdMs = staleThresholdMs;
            }
        }
        return staleThresholdMs;
    }

    private long getObsoleteMs(ISymmetricEngine engine) {
        long defaultMs = ServerConstants.CLUSTER_PEER_OBSOLETE_DEFAULT_MS;
        if (engine == null) {
            return defaultMs;
        }
        return engine.getParameterService().getLong(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS, defaultMs);
    }

    /**
     * Stops tracking a peer that has been offline for so long that even staleness check is irrelevant.
     */
    private void purgeObsoletePeers(long now, long obsoleteThresholdMs) {
        for (String peerId : new HashSet<>(peerNetworkCoordinator.getPeerIds())) {
            ClusterServerStatusMessage msg = peerNetworkCoordinator.getPeerStatusMessage(peerId);
            if (msg != null && msg.isStale(now, obsoleteThresholdMs)) {
                peerNetworkCoordinator.removePeer(peerId);
                log.debug("Purged obsolete peer. ServerId={}, LastHeartbeat={}, ObsoleteThresholdMs={}",
                        peerId, msg.getTimestampAsString(), obsoleteThresholdMs);
            }
        }
    }

    /**
     * Refreshes SYM_NODE_HOST.heartbeat_time for every registered engine, local-only (does not sync to other nodes). Skips engines that aren't started yet so
     * avoid race conditions with DB upgrade or startup-related SYM_NODE_HOST updates.
     */
    void updateOwnNodeHostHeartbeat() {
        String engineName = "";
        for (ISymmetricEngine engine : registeredEngines.values()) {
            try {
                engineName = engine.getEngineName();
                if (!engine.isStarted()) {
                    log.debug("Skipped heartbeat_time update for (not started) engine={}", engineName);
                    continue;
                }
                engine.getDataService().updateNodeHostForCurrentNode(true);
            } catch (Exception ex) {
                log.warn("Failed to refresh heartbeat_time for engine=" + engineName, ex);
            }
        }
    }

    /**
     * Adds recently observed server ID (sent us a heartbeat message) to the set of known peers. Rejects peers from different cluster partitions for security.
     */
    private int discoverPeersIncomingHeartbeats() {
        int newPeersCount = 0;
        for (ClusterServerStatusMessage msg : peerNetworkCoordinator.getObservedPeers()) {
            if (addPeer(msg.getServerId(), msg.getTimestampAsDate(), msg.getClusterPartitionId())) {
                newPeersCount++;
            }
        }
        log.debug("Discovered {} new peers from incoming cluster heartbeat messages. serverId={}, ClusterPartitionId={}",
                newPeersCount, myServerId, myClusterPartitionId);
        return newPeersCount;
    }

    private int discoverPeersFromNodeHostTable() {
        String engineName = "";
        int newPeersCount = 0;
        for (ISymmetricEngine engine : registeredEngines.values()) {
            try {
                engineName = engine.getEngineName();
                if (!engine.isInitialized()) {
                    log.debug("Skipped peer discovery via NODE_HOST for engine={}", engineName);
                    continue;
                }
                newPeersCount += engine.refreshClusterPeersFromNodeHost();
            } catch (Exception ex) {
                log.warn("Failed to complete peer discovery via NODE_HOST for engine=" + engineName, ex);
            }
        }
        log.debug("Completed peer discovery via NODE_HOST. New peers found={}", newPeersCount);
        return newPeersCount;
    }

    private int countActivePeers(long staleThresholdMs) {
        long now = System.currentTimeMillis();
        int activeMembersCount = 0;
        for (String peerId : peerNetworkCoordinator.getPeerIds()) {
            ClusterServerStatusMessage messageFromPeer = peerNetworkCoordinator.getPeerStatusMessage(peerId);
            if (isMessageSourceServerActive(peerId, messageFromPeer, now, staleThresholdMs)) {
                activeMembersCount++;
            }
            ClusterEngineStateMessage engineStateMsg = peerNetworkCoordinator.getEngineStateMessage(peerId);
            for (String engineName : registeredEngines.keySet()) {
                detectEngineStateAndFireEvents(peerId, engineName, engineStateMsg, now, staleThresholdMs);
            }
        }
        return activeMembersCount;
    }

    private void detectEngineStateAndFireEvents(String peerId, String engineName,
            ClusterEngineStateMessage msg, long now, long staleThresholdMs) {
        String key = IClusterCacheCoordinator.generateEngineClusterPeerKey(peerId, engineName);
        String engineState = msg != null ? msg.getEngineState(engineName) : null;
        boolean isActive = engineState != null
                && !ClusteredEngineState.OFFLINE.getValue().equals(engineState)
                && !msg.isStale(now, staleThresholdMs);
        boolean wasActive = Boolean.TRUE.equals(engineStateMap.get(key));
        if (isActive) {
            engineStateMap.put(key, Boolean.TRUE);
        } else if (wasActive) {
            engineStateMap.put(key, Boolean.FALSE);
            onPeerEngineCrashed(peerId, engineName);
        }
    }

    private boolean isPeerAlive(String peerId, ClusterServerStatusMessage messageFromPeer, long now, long staleThresholdMs) {
        if (messageFromPeer == null) {
            log.debug("Skipping null message from cluster peer={}", peerId);
            return false;
        }
        String eventType = messageFromPeer.getEventType();
        String lastHeartbeatInfo = messageFromPeer.getTimestampAsString();
        if (ClusterServerStatusMessage.EVENT_PEER_LEAVING.equals(eventType)) {
            log.info("Cluster peer sent message about leaving the cluster. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, lastHeartbeatInfo, eventType);
            return false;
        }
        if (messageFromPeer.isStale(now, staleThresholdMs)) {
            if (messageFromPeer.isStale(now, 2 * staleThresholdMs)) {
                log.debug("Last message from cluster peer is very stale! Considering peer inactive. Peer={}, Last heartbeat={}, EventType={}",
                        peerId, lastHeartbeatInfo, eventType);
            } else {
                log.warn("Last message from cluster peer is stale! Considering peer inactive. Peer={}, Last heartbeat={}, EventType={}",
                        peerId, lastHeartbeatInfo, eventType);
            }
            return false;
        }
        if (ClusterServerStatusMessage.EVENT_PEER_JOINING.equals(eventType)
                || ClusterServerStatusMessage.EVENT_PEER_INITIALIZING.equals(eventType)) {
            log.info("Cluster peer is starting up. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, lastHeartbeatInfo, eventType);
            return true;
        }
        if (ClusterServerStatusMessage.EVENT_PEER_DISCOVERY.equals(eventType)) {
            log.info("Cluster peer was discovered via database table and is presumed alive - for now. Peer={}, Last DB heartbeat={}, EventType={}",
                    peerId, lastHeartbeatInfo, eventType);
            return true;
        }
        if (log.isDebugEnabled()) {
            logDebugPeerHeartbeat(peerId, messageFromPeer, now, staleThresholdMs);
        }
        return true;
    }

    private void logDebugPeerHeartbeat(String peerId, ClusterServerStatusMessage messageFromPeer, long now, long staleThresholdMs) {
        String eventType = messageFromPeer.getEventType();
        String lastHeartbeatInfo = messageFromPeer.getTimestampAsString();
        long ageMs = messageFromPeer.getAgeMs(now);
        long heartbeatMs = currentHeartbeatMs;
        if (heartbeatMs > 0) {
            long ageIntervals = ageMs / heartbeatMs;
            if (ageIntervals > 0 && ageIntervals % 20 == 0) {
                log.debug("Cluster peer heartbeat appears to be delayed. Heartbeat.age={} ms, Peer={}, Heartbeat={}",
                        ageMs, peerId, lastHeartbeatInfo);
            } else {
                log.debug("Cluster peer heartbeat. Peer={}, Last heartbeat={}, EventType={}, now={}, staleThresholdMs={}",
                        peerId, lastHeartbeatInfo, eventType, now, staleThresholdMs);
            }
        } else {
            log.debug("Cluster peer heartbeat. Peer={}, Last heartbeat={}, EventType={}, now={}, staleThresholdMs={}",
                    peerId, lastHeartbeatInfo, eventType, now, staleThresholdMs);
        }
    }

    private boolean isMessageSourceServerActive(String peerId, ClusterServerStatusMessage message, long now, long staleThresholdMs) {
        // Future: route to type-specific handlers based on message.getEventType() or the cache region it arrived from.
        // Additional coordinator regions (e.g. cache invalidation) would add branches here without changing the state machine.
        return detectPeerStateAndFireEvents(peerId, message, now, staleThresholdMs);
    }

    private boolean detectPeerStateAndFireEvents(String peerId, ClusterServerStatusMessage messageFromPeer, long now, long staleThresholdMs) {
        boolean peerIsActive = isPeerAlive(peerId, messageFromPeer, now, staleThresholdMs);
        boolean wasAlive = Boolean.TRUE.equals(peerWasPreviouslyAlive.get(peerId));
        if (peerIsActive) {
            peerWasPreviouslyAlive.put(peerId, Boolean.TRUE);
            if (!wasAlive) {
                onPeerJoined(messageFromPeer);
            }
        } else if (wasAlive) {
            peerWasPreviouslyAlive.put(peerId, Boolean.FALSE);
            if (messageFromPeer == null || messageFromPeer.isStale(now, staleThresholdMs)) {
                onPeerCrashed(peerId);
            } else {
                onPeerLeft(peerId);
            }
        }
        return peerIsActive;
    }

    protected void onPeerJoined(ClusterServerStatusMessage msg) {
        log.debug("Processing peer joined notification. Peer={}, version={}", msg.getServerId(), msg.getVersion());
        long peerStartTimeMs = msg.getStartTimeMs();
        for (ISymmetricEngine engine : registeredEngines.values()) {
            MDC.put(LoggingConstants.CONTEXT_ENGINE, engine.getParameterService().getEngineName());
            if (!authenticateAndJoinClusterPartition(engine, msg)) {
                log.error("Aborting peer joined processing for peer={} because cluster partition authentication failed", msg.getServerId());
                return;
            }
            if (!engine.getClusterService().isClusteringEnabled()) {
                enforceClusterLockingOrExit(msg.getServerId(), peerStartTimeMs);
                return;
            }
        }
        log.info("Cluster peer joined: serverId={} version={}", msg.getServerId(), msg.getVersion());
    }

    /**
     * Decides which side is the "duplicate" instance when a peer is detected but clustering isn't supported.
     */
    private void enforceClusterLockingOrExit(String peerServerId, long peerStartTimeMs) {
        boolean amINewer = myStartTimeMs > peerStartTimeMs
                || (myStartTimeMs == peerStartTimeMs && myServerId.compareTo(peerServerId) > 0);
        if (amINewer) {
            log.error("Detected an existing cluster peer {} while cluster locking is not enforced "
                    + "({}=false, and/or this edition does not support clustering). "
                    + "This node started later, so it is shutting down to avoid data corruption from "
                    + "multiple unclustered instances sharing the same database.",
                    peerServerId, ParameterConstants.CLUSTER_LOCKING_ENABLED);
            exitProcessAction.run();
        } else {
            log.warn("Detected a newer cluster peer {} while cluster locking is not enforced "
                    + "({}=false, and/or this edition does not support clustering). "
                    + "This node started first and will continue running; the newer peer is expected to shut itself down.",
                    peerServerId, ParameterConstants.CLUSTER_LOCKING_ENABLED);
        }
    }

    /**
     * Verifies that a peer claiming to share this engine's cluster partition ID also shares the same keystore contents.
     *
     * @return false if this engine detected the mismatch and initiated shutdown; true to continue processing other engines
     */
    protected boolean authenticateAndJoinClusterPartition(ISymmetricEngine engine, ClusterServerStatusMessage msg) {
        String peerClusterPartitionId = msg.getClusterPartitionId();
        log.debug("Authenticating cluster peer={} for engine={}. peerClusterPartitionId={}, ClusterPartitionId={}",
                msg.getServerId(), engine.getEngineName(), peerClusterPartitionId, myClusterPartitionId);
        if (peerClusterPartitionId == null) {
            log.debug("Peer={} sent no clusterPartitionId — skipping keystore authentication", msg.getServerId());
            return true;
        }
        if (!peerClusterPartitionId.equals(myClusterPartitionId)) {
            log.warn("Rejecting cluster peer={} — clusterPartitionId mismatch indicates different cluster or environment. "
                    + "peerClusterPartitionId={}, ClusterPartitionId={}",
                    msg.getServerId(), peerClusterPartitionId, myClusterPartitionId);
            return true;
        }
        log.debug("Cluster peer shares same clusterPartitionId={}. Peer={}", myClusterPartitionId, msg.getServerId());
        if (Version.isOlderThanVersion(Version.version(), msg.getVersion())) {
            log.debug("Skipping cluster keystore authentication for peer={} — peer version {} is newer than this version {}",
                    msg.getServerId(), msg.getVersion(), Version.version());
            return true;
        }
        if (!isClusterLockingEnabled) {
            log.debug("Skipping cluster keystore authentication for peer={} — {}=false",
                    msg.getServerId(), ParameterConstants.CLUSTER_LOCKING_ENABLED);
            return true;
        }
        log.debug("Cluster keystore authentication succeeded for peer={}", msg.getServerId());
        return true;
    }

    /**
     * Announces departure to cluster peers, stops all registered engines, and reports this JVM as shutting down to the application health tracker, before
     * terminating. Announcing the departure here (rather than relying solely on the JVM shutdown hook that normally drives this) means peers clear this node's
     * locks immediately via {@code onPeerLeft} instead of waiting out the full stale-peer timeout and treating it as a crash.
     */
    private void exitProcess() {
        ApplicationHealthTracker.getTracker().onShutdown();
        shutdown();
        stopRegisteredEngines();
        ApplicationHealthTracker.getTracker().setAlive(false);
        System.exit(1);
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
        if (registeredEngines.isEmpty()) {
            log.debug("No registered engines available yet.");
            return null;
        }
        return registeredEngines.values().stream().findFirst().orElse(null);
    }

    @Override
    public long generatePeerCoordinationDelay() {
        return pickDelay(this.currentHeartbeatMs, this.currentStaleThresholdMs);
    }

    /**
     * Non-cryptographic jitter to minimize race conditions during simultaneous startups, not security-sensitive.
     */
    public static long pickDelay(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("Min must be less than or equal to max");
        }
        if (min == max) {
            log.warn("Min and max are equal, defeating the purpose of minimizing race conditions. Delay={} ms", min);
            return min;
        }
        return ThreadLocalRandom.current().nextLong(max - min + 1) + min;
    }

    private void logEngineStates() {
        if (lastEngineStates.isEmpty()) {
            log.debug("No engine state information available");
        } else {
            lastEngineStates.forEach((engineName, state) -> log.debug("Engine state: engineName={}, state={}", engineName, state));
        }
    }

    private void logPeerStates() {
        Set<String> peerIds = peerNetworkCoordinator.getPeerIds();
        if (peerIds.isEmpty()) {
            log.debug("No peer information available");
        } else {
            peerIds.forEach(peerId -> {
                IClusteredCacheManager.PeerState peerState = peerStates.get(peerId);
                if (peerState != null) {
                    long ageMs = System.currentTimeMillis() - peerState.lastAliveMs();
                    String state = peerState.alive() ? "ALIVE" : "OFFLINE";
                    log.debug("Peer state: peerId={}, state={}, lastAliveMs={}, ageMs={}", peerId, state, peerState.lastAliveMs(), ageMs);
                } else {
                    log.debug("Peer state: peerId={}, state=UNKNOWN", peerId);
                }
            });
        }
    }
}
