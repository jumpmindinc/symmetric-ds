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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.engine.control.CompositeCache;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.CacheCoordinatorNetworkSettings;
import org.jumpmind.symmetric.common.ServerConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class JcsTcpCacheCoordinatorTest {
    private JcsTcpCacheCoordinator coordinator;
    private ClusterMessageConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        coordinator = new JcsTcpCacheCoordinator();
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.nextSecureLong()).thenReturn(12345L);
        converter = new ClusterMessageConverter(mockSecurityService, "inst1");
        setField("converter", converter);
        setField("myPartitionId", "inst1");
        setField("networkSettings", new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, ServerConstants.CLUSTER_PEER_DISCOVERY_DB, 3000L));
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @SuppressWarnings("unchecked")
    private CacheAccess<String, ClusterPeerSecureMessage> mockCacheAccess() {
        return mock(CacheAccess.class);
    }

    @Test
    void getPeerIds_emptyByDefault() {
        assertTrue(coordinator.getPeerIds().isEmpty());
    }

    @Test
    void addPeer_addsToPeerIds() {
        assertTrue(coordinator.addPeer("server1"));
        assertTrue(coordinator.getPeerIds().contains("server1"));
        assertEquals(1, coordinator.getPeerIds().size());
    }

    @Test
    void addPeer_duplicate_notAddedTwice() {
        coordinator.addPeer("server1");
        assertFalse(coordinator.addPeer("server1"));
        assertEquals(1, coordinator.getPeerIds().size());
    }

    @Test
    void addPeer_multiplePeers_allTracked() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server2");
        coordinator.addPeer("server3");
        assertEquals(3, coordinator.getPeerIds().size());
        assertTrue(coordinator.getPeerIds().contains("server1"));
        assertTrue(coordinator.getPeerIds().contains("server2"));
        assertTrue(coordinator.getPeerIds().contains("server3"));
    }

    @Test
    void removePeer_knownPeer_removesFromPeerIds() {
        coordinator.addPeer("server1");
        assertTrue(coordinator.removePeer("server1"));
        assertTrue(coordinator.getPeerIds().isEmpty());
    }

    @Test
    void removePeer_unknownPeer_returnsFalse() {
        assertFalse(coordinator.removePeer("server1"));
    }

    @Test
    void removePeer_onlyRemovesSpecifiedPeer() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server2");
        coordinator.removePeer("server1");
        assertEquals(1, coordinator.getPeerIds().size());
        assertTrue(coordinator.getPeerIds().contains("server2"));
        assertFalse(coordinator.getPeerIds().contains("server1"));
    }

    @Test
    void getPeerStatusMessage_notStarted_returnsNull() {
        assertNull(coordinator.getPeerStatusMessage("server1"));
    }

    @Test
    void getMessage_knownRegion_notStarted_returnsNull() {
        assertNull(coordinator.getMessage("SYM_CLUSTER_PEERS", "server1"));
    }

    @Test
    void getMessage_unknownRegion_returnsNull() {
        assertNull(coordinator.getMessage("OTHER_REGION", "server1"));
    }

    @Test
    void isInitialized_beforeStart_returnsFalse() {
        assertFalse(coordinator.isInitialized());
    }

    @Test
    void getConverter_returnsNonNullConverter() {
        assertNotNull(coordinator.getConverter());
    }

    @Test
    void detectIfPeerIsStale_noHeartbeat_returnsTrue() {
        long now = System.currentTimeMillis();
        assertTrue(coordinator.detectIfPeerIsStale("server1", now));
    }

    @Test
    void getEngineStateMessage_notStarted_returnsNull() {
        assertNull(coordinator.getEngineStateMessage("server1"));
    }

    @Test
    void getEngineState_bothArgumentsNull_returnsNull() {
        assertNull(coordinator.getEngineState("server1", "engine1"));
    }

    @Test
    void addPeer_thenRemove_checksAreCorrect() {
        assertEquals(0, coordinator.getPeerIds().size());
        coordinator.addPeer("peer1");
        assertEquals(1, coordinator.getPeerIds().size());
        coordinator.removePeer("peer1");
        assertEquals(0, coordinator.getPeerIds().size());
    }

    @Test
    void announceDiscoveredPeer_udpDiscoveryDisabled_isNoOpAndReturnsFalse() throws Exception {
        setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, ServerConstants.CLUSTER_PEER_DISCOVERY_DB, 3000L));
        assertFalse(coordinator.announceDiscoveredPeer("peer1", "172.21.0.4"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void deliverWithTimeout_blockedDelivery_returnsWithinTimeoutAndDoesNotWaitForCompletion() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, "udp", 400L));
            setField("messageDeliveryExecutor", executor);
            setField("deliveryTimeoutMs", 200L);
            CountDownLatch releaseBlockedTask = new CountDownLatch(1);
            long start = System.currentTimeMillis();
            invokeDeliverWithTimeout("blocked", () -> awaitQuietly(releaseBlockedTask));
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 2000L, "delivery should return near the 200ms timeout, took " + elapsed + "ms");
            assertTrue(elapsed >= 200L, "delivery should wait at least the timeout, took " + elapsed + "ms");
            releaseBlockedTask.countDown();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void deliverWithTimeout_priorDeliveryStillRunning_skipsWithoutBlocking() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, "udp", 400L));
            setField("messageDeliveryExecutor", executor);
            setField("deliveryTimeoutMs", 200L);
            CountDownLatch releaseFirstTask = new CountDownLatch(1);
            invokeDeliverWithTimeout("first", () -> awaitQuietly(releaseFirstTask));
            AtomicBoolean secondTaskRan = new AtomicBoolean(false);
            long start = System.currentTimeMillis();
            invokeDeliverWithTimeout("second", () -> secondTaskRan.set(true));
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 100L, "skipped delivery should return immediately, took " + elapsed + "ms");
            assertFalse(secondTaskRan.get(), "second delivery must be skipped while the first is still in flight");
            releaseFirstTask.countDown();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void deliverWithTimeout_fastDelivery_runsToCompletion() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, "udp", 400L));
            setField("messageDeliveryExecutor", executor);
            setField("deliveryTimeoutMs", 2000L);
            AtomicBoolean taskRan = new AtomicBoolean(false);
            invokeDeliverWithTimeout("fast", () -> taskRan.set(true));
            assertTrue(taskRan.get(), "a fast delivery should complete within the timeout");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void setNetworkSettings(CacheCoordinatorNetworkSettings settings) throws Exception {
        setField("networkSettings", settings);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = JcsTcpCacheCoordinator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(coordinator, value);
    }

    private void invokeDeliverWithTimeout(String description, Runnable task) throws Exception {
        Method method = JcsTcpCacheCoordinator.class.getDeclaredMethod("deliverWithTimeout", String.class, Runnable.class);
        method.setAccessible(true);
        method.invoke(coordinator, description, task);
    }

    private void invokePurgePeerMessages(String serverId) throws Exception {
        Method method = JcsTcpCacheCoordinator.class.getDeclaredMethod("purgePeerMessages", String.class);
        method.setAccessible(true);
        method.invoke(coordinator, serverId);
    }

    private void invokeShutdownMessageDeliveryExecutor() throws Exception {
        Method method = JcsTcpCacheCoordinator.class.getDeclaredMethod("shutdownMessageDeliveryExecutor");
        method.setAccessible(true);
        method.invoke(coordinator);
    }

    @Test
    void start_dbDiscoveryMode_initializesJcsAndIsUsable() throws Exception {
        int port = findFreePort();
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings("server1", "inst1", port,
                ServerConstants.CLUSTER_PEER_DISCOVERY_DB, 3000L);
        try {
            coordinator.start(settings, Collections.emptySet(), converter, new NodeHostCachePeerServerDiscovery());
            assertTrue(coordinator.isInitialized());
            assertNull(coordinator.getPeerStatusMessage("nonexistent"));
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void start_discoveryStartThrows_wrapsInRuntimeExceptionAndLeavesUninitialized() throws Exception {
        int port = findFreePort();
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings("server1", "inst1", port,
                ServerConstants.CLUSTER_PEER_DISCOVERY_DB, 3000L);
        ICachePeerServerDiscovery brokenDiscovery = mock(ICachePeerServerDiscovery.class);
        doThrow(new RuntimeException("boom")).when(brokenDiscovery).start(any());
        assertThrows(RuntimeException.class, () -> coordinator.start(settings, Collections.emptySet(), converter, brokenDiscovery));
        assertFalse(coordinator.isInitialized());
    }

    @Test
    void stop_beforeStart_isSafeNoOp() {
        assertDoesNotThrow(() -> coordinator.stop());
        assertFalse(coordinator.isInitialized());
    }

    @Test
    void stop_afterStart_tearsDownAndMarksUninitialized() throws Exception {
        int port = findFreePort();
        CacheCoordinatorNetworkSettings settings = new CacheCoordinatorNetworkSettings("server1", "inst1", port,
                ServerConstants.CLUSTER_PEER_DISCOVERY_DB, 3000L);
        coordinator.start(settings, Collections.emptySet(), converter, new NodeHostCachePeerServerDiscovery());
        coordinator.stop();
        assertFalse(coordinator.isInitialized());
        assertNull(coordinator.getPeerStatusMessage("server1"));
    }

    @Test
    void announceDiscoveredPeer_blankAddress_returnsFalse() {
        assertFalse(coordinator.announceDiscoveredPeer("peer1", "  "));
    }

    @Test
    void announceDiscoveredPeer_blacklistedServer_returnsFalseWithoutCallingDiscovery() throws Exception {
        ClusterMessageConverter mockConverter = mock(ClusterMessageConverter.class);
        ClusterMessageConverter.RejectionInfo rejectionInfo = mock(ClusterMessageConverter.RejectionInfo.class);
        Map<String, ClusterMessageConverter.RejectionInfo> rejected = new HashMap<>();
        rejected.put("peer1", rejectionInfo);
        when(mockConverter.getRejectedServers()).thenReturn(rejected);
        setField("converter", mockConverter);
        ICachePeerServerDiscovery mockDiscovery = mock(ICachePeerServerDiscovery.class);
        setField("discovery", mockDiscovery);
        assertFalse(coordinator.announceDiscoveredPeer("peer1", "172.21.0.4"));
        verify(mockDiscovery, never()).announcePeer(anyString(), anyString());
    }

    @Test
    void announceDiscoveredPeer_discoveryNull_returnsFalse() {
        assertFalse(coordinator.announceDiscoveredPeer("peer1", "172.21.0.4"));
    }

    @Test
    void announceDiscoveredPeer_discoveryPresent_delegatesAndReturnsResult() throws Exception {
        ICachePeerServerDiscovery mockDiscovery = mock(ICachePeerServerDiscovery.class);
        when(mockDiscovery.announcePeer("peer1", "172.21.0.4")).thenReturn(true);
        setField("discovery", mockDiscovery);
        assertTrue(coordinator.announceDiscoveredPeer("peer1", "172.21.0.4"));
        verify(mockDiscovery).announcePeer("peer1", "172.21.0.4");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void announceDiscoveredPeer_realCrossInstanceDelivery_messageBecomesVisibleToOtherCoordinator() throws Exception {
        int portA = findFreePort();
        int portB = findFreePort();
        JcsTcpCacheCoordinator coordinatorA = coordinator;
        JcsTcpCacheCoordinator coordinatorB = new JcsTcpCacheCoordinator();
        CacheCoordinatorNetworkSettings settingsA = new CacheCoordinatorNetworkSettings("serverA", "inst1", portA, ServerConstants.CLUSTER_PEER_DISCOVERY_DB,
                3000L);
        CacheCoordinatorNetworkSettings settingsB = new CacheCoordinatorNetworkSettings("serverB", "inst1", portB, ServerConstants.CLUSTER_PEER_DISCOVERY_DB,
                3000L);
        try {
            coordinatorA.start(settingsA, Collections.emptySet(), converter, new NodeHostCachePeerServerDiscovery());
            coordinatorB.start(settingsB, Collections.emptySet(), converter, new NodeHostCachePeerServerDiscovery());
            assertTrue(coordinatorA.announceDiscoveredPeer("serverB", "127.0.0.1:" + portB));
            ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "serverA", "inst1", 1000L);
            coordinatorA.sendServerStatus(msg);
            ClusterServerStatusMessage received = null;
            long deadline = System.currentTimeMillis() + 10_000L;
            while (System.currentTimeMillis() < deadline && received == null) {
                received = coordinatorB.getPeerStatusMessage("serverA");
                if (received == null) {
                    Thread.sleep(200L);
                }
            }
            assertNotNull(received, "coordinatorB should eventually receive coordinatorA's server status message over the real lateral TCP connection");
            assertEquals("serverA", received.getServerId());
        } finally {
            coordinatorA.stop();
            coordinatorB.stop();
        }
    }

    @Test
    void addPeer_blacklistedNewPeer_returnsFalseAndDoesNotAdd() throws Exception {
        ClusterMessageConverter mockConverter = mock(ClusterMessageConverter.class);
        ClusterMessageConverter.RejectionInfo rejectionInfo = mock(ClusterMessageConverter.RejectionInfo.class);
        Map<String, ClusterMessageConverter.RejectionInfo> rejected = new HashMap<>();
        rejected.put("peer1", rejectionInfo);
        when(mockConverter.getRejectedServers()).thenReturn(rejected);
        setField("converter", mockConverter);
        assertFalse(coordinator.addPeer("peer1"));
        assertFalse(coordinator.getPeerIds().contains("peer1"));
    }

    @Test
    void purgePeerMessages_viaRemovePeer_removesFromBothCaches() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockPeerCache = mockCacheAccess();
        CacheAccess<String, ClusterPeerSecureMessage> mockEngineCache = mockCacheAccess();
        setField("peerHeartbeatCache", mockPeerCache);
        setField("engineStateCache", mockEngineCache);
        coordinator.addPeer("peer1");
        coordinator.removePeer("peer1");
        verify(mockPeerCache).remove("peer1");
        verify(mockEngineCache).remove("peer1");
    }

    @Test
    void purgePeerMessages_cachesNull_doesNotThrow() {
        assertDoesNotThrow(() -> invokePurgePeerMessages("peer1"));
    }

    @Test
    void purgePeerMessages_cacheThrows_isCaughtAndLogged() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockPeerCache = mockCacheAccess();
        doThrow(new RuntimeException("boom")).when(mockPeerCache).remove(anyString());
        setField("peerHeartbeatCache", mockPeerCache);
        assertDoesNotThrow(() -> invokePurgePeerMessages("peer1"));
    }

    @Test
    void sendServerStatus_cacheNull_doesNotThrow() {
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 0L);
        assertDoesNotThrow(() -> coordinator.sendServerStatus(msg));
    }

    @Test
    void sendServerStatus_cachePresent_putsEncryptedMessage() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockPeerCache = mockCacheAccess();
        setField("peerHeartbeatCache", mockPeerCache);
        setField("messageDeliveryExecutor", Executors.newSingleThreadExecutor());
        setField("deliveryTimeoutMs", 2000L);
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 0L);
        coordinator.sendServerStatus(msg);
        verify(mockPeerCache).put(eq("server1"), any());
    }

    @Test
    void sendServerStatus_cachePutThrows_doesNotPropagate() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockPeerCache = mockCacheAccess();
        doThrow(new RuntimeException("boom")).when(mockPeerCache).put(anyString(), any());
        setField("peerHeartbeatCache", mockPeerCache);
        setField("messageDeliveryExecutor", Executors.newSingleThreadExecutor());
        setField("deliveryTimeoutMs", 2000L);
        ClusterServerStatusMessage msg = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 0L);
        assertDoesNotThrow(() -> coordinator.sendServerStatus(msg));
    }

    @Test
    void sendEngineStates_cacheNull_doesNotThrow() {
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, "engine1", "server1", "inst1");
        assertDoesNotThrow(() -> coordinator.sendEngineStates(msg));
    }

    @Test
    void sendEngineStates_cachePresent_putsEncryptedMessage() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockEngineCache = mockCacheAccess();
        setField("engineStateCache", mockEngineCache);
        setField("messageDeliveryExecutor", Executors.newSingleThreadExecutor());
        setField("deliveryTimeoutMs", 2000L);
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, "engine1", "server1", "inst1");
        coordinator.sendEngineStates(msg);
        verify(mockEngineCache).put(eq("server1"), any());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void deliverWithTimeout_taskThrows_isCaughtAsExecutionExceptionAndDoesNotPropagate() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, "db", 400L));
            setField("messageDeliveryExecutor", executor);
            setField("deliveryTimeoutMs", 2000L);
            assertDoesNotThrow(() -> invokeDeliverWithTimeout("throwing", () -> {
                throw new RuntimeException("boom");
            }));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shutdownMessageDeliveryExecutor_shutsDownExecutorAndClearsState() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        setField("messageDeliveryExecutor", executor);
        invokeShutdownMessageDeliveryExecutor();
        assertTrue(executor.isShutdown());
        Field executorField = JcsTcpCacheCoordinator.class.getDeclaredField("messageDeliveryExecutor");
        executorField.setAccessible(true);
        assertNull(executorField.get(coordinator));
    }

    @Test
    void shutdownMessageDeliveryExecutor_alreadyNull_isSafeNoOp() {
        assertDoesNotThrow(this::invokeShutdownMessageDeliveryExecutor);
    }

    @Test
    void getPeerStatusMessage_cachePresentButNoMessage_returnsNull() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockPeerCache = mockCacheAccess();
        when(mockPeerCache.get("server1")).thenReturn(null);
        setField("peerHeartbeatCache", mockPeerCache);
        assertNull(coordinator.getPeerStatusMessage("server1"));
    }

    @Test
    void getPeerStatusMessage_cachePresentWithMessage_returnsConvertedMessage() throws Exception {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage secure = converter.toEncryptedMessage(plain);
        CacheAccess<String, ClusterPeerSecureMessage> mockPeerCache = mockCacheAccess();
        when(mockPeerCache.get("server1")).thenReturn(secure);
        setField("peerHeartbeatCache", mockPeerCache);
        ClusterServerStatusMessage result = coordinator.getPeerStatusMessage("server1");
        assertNotNull(result);
        assertEquals("server1", result.getServerId());
        assertEquals(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, result.getEventType());
    }

    @Test
    void getEngineStateMessage_cachePresentButNoMessage_returnsNull() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockEngineCache = mockCacheAccess();
        when(mockEngineCache.get("server1")).thenReturn(null);
        setField("engineStateCache", mockEngineCache);
        assertNull(coordinator.getEngineStateMessage("server1"));
    }

    @Test
    void getEngineStateMessage_cachePresentWithMessage_returnsConvertedMessage() throws Exception {
        ClusterEngineStateMessage plain = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, "engine1", "server1", "inst1");
        ClusterPeerSecureMessage secure = converter.toEncryptedMessage(plain);
        CacheAccess<String, ClusterPeerSecureMessage> mockEngineCache = mockCacheAccess();
        when(mockEngineCache.get("server1")).thenReturn(secure);
        setField("engineStateCache", mockEngineCache);
        ClusterEngineStateMessage result = coordinator.getEngineStateMessage("server1");
        assertNotNull(result);
        assertEquals(ClusteredEngineState.RUNNING.getValue(), result.getEngineState("engine1"));
    }

    @Test
    void getEngineState_messagePresentWithState_returnsState() throws Exception {
        ClusterEngineStateMessage plain = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, "engine1", "server1", "inst1");
        ClusterPeerSecureMessage secure = converter.toEncryptedMessage(plain);
        CacheAccess<String, ClusterPeerSecureMessage> mockEngineCache = mockCacheAccess();
        when(mockEngineCache.get("server1")).thenReturn(secure);
        setField("engineStateCache", mockEngineCache);
        assertEquals(ClusteredEngineState.RUNNING.getValue(), coordinator.getEngineState("server1", "engine1"));
    }

    @Test
    void getEngineState_requestedEngineNotInMessage_returnsNull() throws Exception {
        ClusterEngineStateMessage plain = new ClusterEngineStateMessage(ClusteredEngineState.RUNNING, "engine1", "server1", "inst1");
        ClusterPeerSecureMessage secure = converter.toEncryptedMessage(plain);
        CacheAccess<String, ClusterPeerSecureMessage> mockEngineCache = mockCacheAccess();
        when(mockEngineCache.get("server1")).thenReturn(secure);
        setField("engineStateCache", mockEngineCache);
        assertNull(coordinator.getEngineState("server1", "engineNotPresent"));
    }

    @Test
    void getObservedPeers_cacheNull_returnsEmptySet() {
        assertTrue(coordinator.getObservedPeers().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getObservedPeers_cacheWithValidMessage_returnsConvertedSet() throws Exception {
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterPeerSecureMessage secure = converter.toEncryptedMessage(plain);
        CacheAccess<String, ClusterPeerSecureMessage> mockPeerCache = mockCacheAccess();
        CompositeCache<String, ClusterPeerSecureMessage> mockCacheControl = mock(CompositeCache.class);
        when(mockCacheControl.getKeySet(true)).thenReturn(new HashSet<>(Arrays.asList("server1")));
        when(mockPeerCache.getCacheControl()).thenReturn(mockCacheControl);
        when(mockPeerCache.get("server1")).thenReturn(secure);
        setField("peerHeartbeatCache", mockPeerCache);
        Set<ClusterServerStatusMessage> observed = coordinator.getObservedPeers();
        assertEquals(1, observed.size());
        assertEquals("server1", observed.iterator().next().getServerId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getObservedPeers_cacheWithRejectedMessage_excludedFromResult() throws Exception {
        // Message's clusterPartitionId ("inst1") matches, but it was encrypted by a converter seeded with a different keystore
        // fingerprint ("differentSeed"), so fingerprint validation fails and the message converts to null.
        setField("myPartitionId", "inst1");
        ClusterServerStatusMessage plain = new ClusterServerStatusMessage(ClusterServerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", 1000L);
        ClusterMessageConverter wrongKeystoreConverter = new ClusterMessageConverter(realSecurityService(), "differentSeed");
        ClusterPeerSecureMessage secure = wrongKeystoreConverter.toEncryptedMessage(plain);
        CacheAccess<String, ClusterPeerSecureMessage> mockPeerCache = mockCacheAccess();
        CompositeCache<String, ClusterPeerSecureMessage> mockCacheControl = mock(CompositeCache.class);
        when(mockCacheControl.getKeySet(true)).thenReturn(new HashSet<>(Arrays.asList("server1")));
        when(mockPeerCache.getCacheControl()).thenReturn(mockCacheControl);
        when(mockPeerCache.get("server1")).thenReturn(secure);
        setField("peerHeartbeatCache", mockPeerCache);
        assertTrue(coordinator.getObservedPeers().isEmpty());
    }

    private ISecurityService realSecurityService() {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.nextSecureLong()).thenReturn(54321L);
        return mockSecurityService;
    }
}
