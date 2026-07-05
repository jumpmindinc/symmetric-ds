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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.security.ISecurityService;
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
 * JVM-level singleton that coordinates cluster peer communication and state tracking. Multiple SymmetricDS engines co-hosted on the same JVM share one instance
 * and one heartbeat thread. Transport is delegated to an IClusterCacheCoordinator, resolved the same way as other pluggable services (see
 * {@link AppUtils#newInstance(Class, Class)}) so a different implementation can be substituted via {@code symmetric-impl.properties} without changing this
 * class; it defaults to JcsTcpCacheCoordinator when no override is present. When a remote peer is detected as crashed, locks are cleared across all registered
 * engines.
 */
public class ClusteredCacheManager implements IClusteredCacheManager {
    private static final ClusteredCacheManager GLOBAL_INSTANCE = new ClusteredCacheManager();
    private static final Logger log = LoggerFactory.getLogger(ClusteredCacheManager.class);
    private static final String CLUSTER_HEARTBEAT_THREAD_NAME = "sym-cluster-heartbeat";
    private static final String CLUSTERED_CACHE_LOG_CONTEXT = "sym_clustered_cache";
    private final IClusterCacheCoordinator coordinator = AppUtils.newInstance(IClusterCacheCoordinator.class, JcsTcpCacheCoordinator.class);
    private final Map<String, ISymmetricEngine> registeredEngines = new ConcurrentHashMap<>();
    private final Map<String, PeerState> peerStates = new ConcurrentHashMap<>(); // Tracks both historical (last 24 hours) and currently active peers.
    private final Map<String, Boolean> engineStateMap = new ConcurrentHashMap<>();
    private Thread heartbeatThread;
    private volatile boolean isHeartbeatLoopRunning = false;
    private volatile long currentHeartbeatMs = ServerConstants.CLUSTER_PEER_HEARTBEAT_DEFAULT_MS;
    private volatile long currentStaleThresholdMs = ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS;
    private volatile long lastHeartbeatSummaryLogMs;
    private volatile boolean isClusterPeerListenerStarted;
    private volatile String lastBroadcastEventType = ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT;
    private final Map<String, String> lastEngineStates = new ConcurrentHashMap<>();
    private String myServerId;
    private String myClusterPartitionId;
    private long myStartTimeMs;
    Runnable exitProcessAction = this::exitProcess;

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
    public synchronized boolean addPeer(String serverId, Date historicalHeartbeat) {
        if (serverId == null || isOwnServerId(serverId)) {
            return false;
        }
        boolean isNewPeer = coordinator.addPeer(serverId);
        if (!isNewPeer) {
            log.debug("Recorded cluster peer, but it is not new. ServerId={}, Last known heartbeat={}, ClusterPartitionId={}",
                    serverId, historicalHeartbeat, myClusterPartitionId);
            return false;
        }
        boolean isHeartbeatStale = historicalHeartbeat != null
                ? (System.currentTimeMillis() - historicalHeartbeat.getTime() > this.currentStaleThresholdMs)
                : coordinator.detectIfPeerIsStale(serverId, this.currentStaleThresholdMs);
        if (!isHeartbeatStale) {
            peerStates.put(serverId, new PeerState(true, System.currentTimeMillis()));
            log.debug("Added cluster peer. ServerId={}, Last known heartbeat={}, ClusterPartitionId={}",
                    serverId, historicalHeartbeat, myClusterPartitionId);
            // historicalHeartbeat approximates when the peer started, since this discovery path (SYM_NODE_HOST) carries no true start time.
            long peerStartTimeMs = historicalHeartbeat != null ? historicalHeartbeat.getTime() : System.currentTimeMillis();
            for (ISymmetricEngine engine : registeredEngines.values()) {
                if (!engine.getClusterService().isClusteringEnabled()) {
                    enforceClusterLockingOrExit(serverId, peerStartTimeMs);
                    break;
                }
            }
        } else {
            recordPeerOffline(serverId);
            log.debug("Added cluster peer as stale. ServerId={}, Last known heartbeat={}, ClusterPartitionId={}",
                    serverId, historicalHeartbeat, myClusterPartitionId);
        }
        return isNewPeer;
    }

    @Override
    public synchronized boolean announceDiscoveredPeer(String serverId, String address) {
        if (serverId == null || isOwnServerId(serverId)) {
            return false;
        }
        return coordinator.announceDiscoveredPeer(serverId, address);
    }

    @Override
    public boolean recordPeerOffline(String serverId) {
        if (serverId == null || isOwnServerId(serverId)) {
            return false;
        }
        return peerStates.putIfAbsent(serverId, new PeerState(false, System.currentTimeMillis())) == null;
    }

    @Override
    public boolean isPeerOfflineLongEnough(String serverId, long staleThresholdMs) {
        PeerState state = peerStates.get(serverId);
        return state != null && state.isOfflineLongerThan(System.currentTimeMillis(), staleThresholdMs);
    }

    @Override
    public Set<String> getActiveServerIds() {
        Set<String> active = new HashSet<>();
        for (Map.Entry<String, PeerState> entry : peerStates.entrySet()) {
            if (entry.getValue().alive()) {
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

    @Override
    public boolean isClusterPeerListenerStarted() {
        return isClusterPeerListenerStarted;
    }

    public synchronized void startClusterPeerListener(ISecurityService securityService, String clusterPartitionId, String serverId, boolean isJcsEnabled) {
        ClusterPeerSecureMessage.setSecurityService(securityService);
        myClusterPartitionId = clusterPartitionId;
        myServerId = serverId;
        int port = Integer.parseInt(System.getProperty(
                ServerConstants.CLUSTER_JCS_PORT, String.valueOf(1101)));
        if (isJcsEnabled) {
            myStartTimeMs = System.currentTimeMillis();
            ensurePeerListenerStarted(myServerId, myClusterPartitionId, port);
        }
    }

    private synchronized void ensurePeerListenerStarted(String serverId, String clusterPartitionId, int port) {
        String serverInfo = String.format("serverId=%s, clusterPartitionId=%s, port=%d", serverId, clusterPartitionId, port);
        if (isClusterPeerListenerStarted) {
            log.debug("Skipping redundant JCS cluster peer listener start on {}", serverInfo);
            return;
        }
        try {
            log.debug("Starting JCS cluster peer listener on {}", serverInfo);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings(serverId, clusterPartitionId, port), Collections.emptySet());
            isClusterPeerListenerStarted = true;
            log.info("Started JCS cluster peer listener on {}", serverInfo);
        } catch (Exception ex) {
            log.debug("Failed to start JCS cluster peer listener on " + serverInfo, ex);
            throw new RuntimeException("Failed to start JCS cluster peer listener on " + serverInfo, ex);
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
        long staleThresholdMs = ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS;
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
        lastEngineStates.put(engineName, engineState);
        if (isClusterPeerListenerStarted) {
            ClusterEngineStateMessage msg = new ClusterEngineStateMessage(
                    engineState, engineName, myServerId, myClusterPartitionId, Version.version());
            coordinator.sendEngineStateMessage(msg);
        }
    }

    public void rebroadcastCurrentState() {
        if (isClusterPeerListenerStarted) {
            broadcastStateAndEngines();
        }
    }

    public boolean isAnyPeerWithEngineInState(String engineName, String engineState) {
        long staleThresholdMs = ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS;
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

    public synchronized void stopClusterCommunication() {
        this.isHeartbeatLoopRunning = false;
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

    private void broadcastStateAndEngines() {
        sendMessageToPeers(lastBroadcastEventType);
        broadcastLastKnownEngineStates();
    }

    private void broadcastLastKnownEngineStates() {
        new java.util.HashMap<>(lastEngineStates).forEach((name, state) -> coordinator.sendEngineStateMessage(new ClusterEngineStateMessage(
                state, name, myServerId, myClusterPartitionId, Version.version())));
    }

    private void sendMessageToPeers(String eventType) {
        lastBroadcastEventType = eventType;
        ClusterPeerStatusMessage msg = new ClusterPeerStatusMessage(eventType, myServerId, myClusterPartitionId, Version.version(), myStartTimeMs);
        coordinator.sendMessageToPeers(msg);
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
        broadcastStateAndEngines();
        purgeObsoletePeers(startTime, getObsoleteMs(getAnyEngine()));
        if (this.isHeartbeatLoopRunning) {
            sleepUntilNextHeartbeat(startTime, sleepBetweenHeartbeatsMs, staleThresholdMs);
        } else {
            log.debug("Cluster peer heartbeat loop is no longer active, skipping sleep.");
        }
    }

    private void sleepUntilNextHeartbeat(long startTime, long sleepBetweenHeartbeatsMs, long staleThresholdMs) throws InterruptedException {
        // These counts only track remote peers; the current server is also an active member of the cluster, noted separately in the log message below.
        int activeMembers = countActivePeers(staleThresholdMs);
        int knownPeers = coordinator.getPeerIds().size();
        long now = System.currentTimeMillis();
        long durationMs = now - startTime;
        long adjustedSleepMs = Math.max(0, sleepBetweenHeartbeatsMs - durationMs);
        if (staleThresholdMs > 0 && now - lastHeartbeatSummaryLogMs >= staleThresholdMs) {
            lastHeartbeatSummaryLogMs = now;
            log.info(
                    "Cluster peer heartbeat completed: Active peers={} (plus myself), Known peers={}, myServerId={}, myClusterPartitionId={}, staleThresholdMs={}, Tick duration={} ms, sleepMs={}",
                    activeMembers, knownPeers, myServerId, myClusterPartitionId, staleThresholdMs, durationMs, adjustedSleepMs);
        } else {
            log.debug(
                    "Cluster peer heartbeat completed: Active peers={} (plus myself), Known peers={}, myServerId={}, myClusterPartitionId={}, staleThresholdMs={}, Tick duration={} ms, sleepMs={}",
                    activeMembers, knownPeers, myServerId, myClusterPartitionId, staleThresholdMs, durationMs, adjustedSleepMs);
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
        peerStates.entrySet().removeIf(entry -> {
            boolean isObsolete = entry.getValue().isOfflineLongerThan(now, obsoleteThresholdMs);
            if (isObsolete) {
                coordinator.removePeer(entry.getKey());
            }
            return isObsolete;
        });
    }

    /**
     * Adds recently observed server ID (sent us a heartbeat message) to the set of known peers.
     */
    private int discoverPeersIncomingHeartbeats() {
        int newPeersCount = 0;
        for (ClusterPeerStatusMessage msg : coordinator.getObservedPeers()) {
            if (addPeer(msg.getServerId(), msg.getTimestampAsDate())) {
                newPeersCount++;
            }
        }
        log.debug("Discovered {} new peers from incoming heartbeat messages. serverId={}, ClusterPartitionId={}",
                newPeersCount, myServerId, myClusterPartitionId);
        return newPeersCount;
    }

    private int countActivePeers(long staleThresholdMs) {
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
            log.debug("Rejecting message from cluster peer={} — checksum invalid, message may be corrupt or from an unauthorized host. myClusterPartitionId={}",
                    peerId, myClusterPartitionId);
            return false;
        }
        String eventType = messageFromPeer.getEventType();
        String lastHeartbeatInfo = messageFromPeer.getTimestampAsString();
        if (ClusterPeerStatusMessage.EVENT_PEER_LEAVING.equals(eventType)) {
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
        if (ClusterPeerStatusMessage.EVENT_PEER_JOINING.equals(eventType)
                || ClusterPeerStatusMessage.EVENT_PEER_INITIALIZING.equals(eventType)) {
            log.info("Cluster peer is starting up. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, lastHeartbeatInfo, eventType);
            return true;
        }
        if (ClusterPeerStatusMessage.EVENT_PEER_UPGRADING_DB.equals(eventType)) {
            log.info("Cluster peer is upgrading database. Peer={}, Last heartbeat={}, EventType={}",
                    peerId, lastHeartbeatInfo, eventType);
            return true;
        }
        if (log.isDebugEnabled()) {
            logDebugPeerHeartbeat(peerId, messageFromPeer, now, staleThresholdMs);
        }
        return true;
    }

    private void logDebugPeerHeartbeat(String peerId, ClusterPeerSecureMessage messageFromPeer, long now, long staleThresholdMs) {
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

    private boolean dispatchMessage(String peerId, ClusterPeerSecureMessage message, long now, long staleThresholdMs) {
        // Future: route to type-specific handlers based on message.getEventType() or the cache region it arrived from.
        // Additional coordinator regions (e.g. cache invalidation) would add branches here without changing the state machine.
        return detectPeerStateAndFireEvents(peerId, message, now, staleThresholdMs);
    }

    private boolean detectPeerStateAndFireEvents(String peerId, ClusterPeerSecureMessage messageFromPeer, long now, long staleThresholdMs) {
        boolean peerIsActive = isPeerAlive(peerId, messageFromPeer, now, staleThresholdMs);
        long lastHeartbeatTime = messageFromPeer != null ? messageFromPeer.getTimestamp() : 0;
        PeerState previous = peerStates.get(peerId);
        boolean wasAlive = previous != null && previous.alive();
        if (previous != null) {
            lastHeartbeatTime = Math.max(previous.lastAliveMs(), lastHeartbeatTime);
        }
        if (peerIsActive) {
            peerStates.put(peerId, new PeerState(true, lastHeartbeatTime));
            if (!wasAlive) {
                onPeerJoined(messageFromPeer);
            }
        } else if (wasAlive) {
            if (messageFromPeer == null || messageFromPeer.isStale(now, staleThresholdMs)) {
                peerStates.put(peerId, new PeerState(false, previous.lastAliveMs()));
                onPeerCrashed(peerId);
            } else {
                peerStates.put(peerId, new PeerState(false, lastHeartbeatTime));
                onPeerLeft(peerId);
            }
        }
        return peerIsActive;
    }

    protected void onPeerJoined(ClusterPeerSecureMessage msg) {
        log.debug("Processing peer joined notification. Peer={}, version={}", msg.getServerId(), msg.getVersion());
        long peerStartTimeMs = msg instanceof ClusterPeerStatusMessage ? ((ClusterPeerStatusMessage) msg).getStartTimeMs() : 0L;
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
                    + "(cluster.lock.enabled=false, and/or this edition does not support clustering). "
                    + "This node started later, so it is shutting down to avoid data corruption from "
                    + "multiple unclustered instances sharing the same database.", peerServerId);
            exitProcessAction.run();
        } else {
            log.warn("Detected a newer cluster peer {} while cluster locking is not enforced "
                    + "(cluster.lock.enabled=false, and/or this edition does not support clustering). "
                    + "This node started first and will continue running; the newer peer is expected to shut itself down.",
                    peerServerId);
        }
    }

    /**
     * Verifies that a peer claiming to share this engine's cluster partition ID also shares the same keystore contents.
     *
     * @return false if this engine detected the mismatch and initiated shutdown; true to continue processing other engines
     */
    protected boolean authenticateAndJoinClusterPartition(ISymmetricEngine engine, ClusterPeerSecureMessage msg) {
        String peerClusterPartitionId = msg.getClusterPartitionId();
        String myClusterPartitionId = engine.getClusterService().getClusterPartitionId();
        log.debug("Authenticating cluster peer={} for engine={}. peerClusterPartitionId={}, myClusterPartitionId={}",
                msg.getServerId(), engine.getEngineName(), peerClusterPartitionId, myClusterPartitionId);
        if (peerClusterPartitionId == null) {
            log.debug("Peer={} sent no clusterPartitionId — skipping keystore authentication", msg.getServerId());
            return true;
        }
        if (!peerClusterPartitionId.equals(myClusterPartitionId)) {
            log.warn("Rejecting cluster peer={} — clusterPartitionId mismatch indicates different cluster or environment. "
                    + "peerClusterPartitionId={}, myClusterPartitionId={}",
                    msg.getServerId(), peerClusterPartitionId, myClusterPartitionId);
            return true;
        }
        log.debug("Cluster peer shares same clusterPartitionId={}. Peer={}", myClusterPartitionId, msg.getServerId());
        if (Version.isOlderThanVersion(Version.version(), msg.getVersion())) {
            log.debug("Skipping cluster keystore authentication for peer={} — peer version {} is newer than this version {}",
                    msg.getServerId(), msg.getVersion(), Version.version());
            return true;
        }
        if (!engine.getParameterService().is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            log.debug("Skipping cluster keystore authentication for peer={} — cluster.lock.enabled=false", msg.getServerId());
            return true;
        }
        if (!msg.isKeystoreFingerprintValid()) {
            log.error("Cluster keystore is not identical across all cluster peers - see User guide for details. Peer={}", msg.getServerId());
            exitProcessAction.run();
            return false;
        }
        log.debug("Cluster keystore authentication succeeded for peer={}", msg.getServerId());
        return true;
    }

    /**
     * Stops all registered engines and reports this JVM as shutting down to the application health tracker.
     */
    private void exitProcess() {
        ApplicationHealthTracker.getTracker().onShutdown();
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
        return registeredEngines.values().stream().findFirst().orElse(null);
    }
}
