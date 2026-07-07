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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.CacheCoordinatorNetworkSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class JcsTcpCacheCoordinatorTest {
    private JcsTcpCacheCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new JcsTcpCacheCoordinator();
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.nextSecureLong()).thenReturn(12345L);
        coordinator.getConverter().setSecurityService(mockSecurityService);
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
        setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, false, 3000L));
        assertFalse(coordinator.announceDiscoveredPeer("peer1", "172.21.0.4"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void deliverWithTimeout_blockedDelivery_returnsWithinTimeoutAndDoesNotWaitForCompletion() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, true, 400L));
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
            setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, true, 400L));
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
            setNetworkSettings(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, true, 400L));
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
}
