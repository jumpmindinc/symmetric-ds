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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.engine.control.CompositeCache;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.apache.commons.jcs3.utils.discovery.DiscoveredService;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryService;
import org.apache.commons.jcs3.utils.discovery.behavior.IDiscoveryListener;
import org.jumpmind.security.ISecurityService;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JcsTcpCacheCoordinatorTest {
    private JcsTcpCacheCoordinator coordinator;

    @BeforeEach
    void setUp() {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(mockSecurityService);
        coordinator = new JcsTcpCacheCoordinator();
    }

    @Test
    void getPeerIds_emptyByDefault() {
        assertTrue(coordinator.getPeerIds().isEmpty());
    }

    @Test
    void addPeer_addsToPeerIds() {
        coordinator.addPeer("server1");
        assertTrue(coordinator.getPeerIds().contains("server1"));
        assertEquals(1, coordinator.getPeerIds().size());
    }

    @Test
    void addPeer_duplicate_notAddedTwice() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server1");
        assertEquals(1, coordinator.getPeerIds().size());
    }

    @Test
    void addPeer_multiplePeers_allTracked() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server2");
        coordinator.addPeer("server3");
        assertEquals(3, coordinator.getPeerIds().size());
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
    void removePeer_knownPeer_purgesHeartbeatMessage() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        setPeerHeartbeatCache(mockCache);
        coordinator.addPeer("server1");
        coordinator.removePeer("server1");
        verify(mockCache).remove("server1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void removePeer_knownPeer_purgesOnlyThatPeersEngineStateMessages() throws Exception {
        CacheAccess<String, ClusterEngineStateMessage> mockCache = mock(CacheAccess.class);
        CompositeCache<String, ClusterEngineStateMessage> mockCacheControl = mock(CompositeCache.class);
        when(mockCache.getCacheControl()).thenReturn(mockCacheControl);
        when(mockCacheControl.getKeySet(true)).thenReturn(Set.of("server1|engine1", "server1|engine2", "server2|engine1"));
        setEngineStateCache(mockCache);
        coordinator.addPeer("server1");
        coordinator.removePeer("server1");
        verify(mockCache).remove("server1|engine1");
        verify(mockCache).remove("server1|engine2");
        verify(mockCache, never()).remove("server2|engine1");
    }

    @Test
    void removePeer_unknownPeer_doesNotPurgeHeartbeatMessage() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        setPeerHeartbeatCache(mockCache);
        coordinator.removePeer("server1");
        verify(mockCache, never()).remove(anyString());
    }

    @Test
    void removePeer_onlyRemovesSpecifiedPeer() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server2");
        coordinator.removePeer("server1");
        assertEquals(1, coordinator.getPeerIds().size());
        assertTrue(coordinator.getPeerIds().contains("server2"));
    }

    @Test
    void getMessage_notStarted_returnsNull() {
        assertNull(coordinator.getPeerStatusMessage("server1"));
    }

    @Test
    void sendMessageToPeers_notStarted_doesNotThrow() {
        ClusterPeerStatusMessage msg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        coordinator.sendMessageToPeers(msg);
        assertNull(coordinator.getPeerStatusMessage("server1"));
    }

    @Test
    void stop_notStarted_doesNotThrow() {
        coordinator.stop();
        assertTrue(coordinator.getPeerIds().isEmpty());
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
    void stop_calledTwice_doesNotThrow() {
        coordinator.stop();
        coordinator.stop();
    }

    @Test
    void start_alwaysConfiguresMandatoryRegions() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of());
            verify(mockManager).configure(captor.capture());
            Properties props = captor.getValue();
            assertTrue(props.containsKey("jcs.region.SYM_CLUSTER_PEERS"));
            assertTrue(props.containsKey("jcs.region.SYM_CLUSTER_ENGINES"));
        }
    }

    @Test
    void start_customRegion_isConfiguredAlongsideMandatoryRegions() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101),
                    Set.of(new IClusterCacheCoordinator.RegionSettings(
                            "CUSTOM_REGION", 50, 30, false, 30, IClusterCacheCoordinator.RemovalType.LRU)));
            verify(mockManager).configure(captor.capture());
            Properties props = captor.getValue();
            assertTrue(props.containsKey("jcs.region.CUSTOM_REGION"));
            assertTrue(props.containsKey("jcs.region.SYM_CLUSTER_PEERS"));
        }
    }

    @Test
    void start_regionNameDuplicatesMandatoryRegion_throws() {
        assertThrows(IllegalArgumentException.class, () -> coordinator.start(
                new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101),
                Set.of(new IClusterCacheCoordinator.RegionSettings(
                        "SYM_CLUSTER_PEERS", 50, 30, false, 30, IClusterCacheCoordinator.RemovalType.LRU))));
    }

    @Test
    void start_duplicateCustomRegionNames_throws() {
        Set<IClusterCacheCoordinator.RegionSettings> duplicateRegionSettings = new HashSet<>(List.of(
                new IClusterCacheCoordinator.RegionSettings("CUSTOM_REGION", 50, 30, false, 30, IClusterCacheCoordinator.RemovalType.LRU),
                new IClusterCacheCoordinator.RegionSettings("CUSTOM_REGION", 100, 60, false, 30, IClusterCacheCoordinator.RemovalType.LRU)));
        assertThrows(IllegalArgumentException.class, () -> coordinator.start(
                new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), duplicateRegionSettings));
    }

    @Test
    void announceDiscoveredPeer_notStarted_returnsFalse() {
        assertFalse(coordinator.announceDiscoveredPeer("server1", "10.0.0.5"));
    }

    @Test
    void announceDiscoveredPeer_blankAddress_returnsFalse() {
        assertFalse(coordinator.announceDiscoveredPeer("server1", null));
        assertFalse(coordinator.announceDiscoveredPeer("server1", " "));
    }

    @Test
    @SuppressWarnings("unchecked")
    void announceDiscoveredPeer_newPeer_registersWithDiscoveryListeners() throws Exception {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of());
            UDPDiscoveryService mockDiscoveryService = mock(UDPDiscoveryService.class);
            IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
            when(mockDiscoveryService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
            setDiscoveryService(mockDiscoveryService);
            assertTrue(coordinator.announceDiscoveredPeer("server1", "10.0.0.5"));
            ArgumentCaptor<DiscoveredService> captor = ArgumentCaptor.forClass(DiscoveredService.class);
            verify(mockListener).addDiscoveredService(captor.capture());
            assertEquals("10.0.0.5", captor.getValue().getServiceAddress());
            assertEquals(1101, captor.getValue().getServicePort());
            assertTrue(captor.getValue().getCacheNames().contains("SYM_CLUSTER_PEERS"));
            assertTrue(captor.getValue().getCacheNames().contains("SYM_CLUSTER_ENGINES"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void announceDiscoveredPeer_sameAddressAgain_returnsFalseAndDoesNotReannounce() throws Exception {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of());
            UDPDiscoveryService mockDiscoveryService = mock(UDPDiscoveryService.class);
            IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
            when(mockDiscoveryService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
            setDiscoveryService(mockDiscoveryService);
            assertTrue(coordinator.announceDiscoveredPeer("server1", "10.0.0.5"));
            assertFalse(coordinator.announceDiscoveredPeer("server1", "10.0.0.5"));
            verify(mockListener, times(1)).addDiscoveredService(any());
            verify(mockListener, never()).removeDiscoveredService(any());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void announceDiscoveredPeer_addressChanged_retractsOldAndAnnouncesNew() throws Exception {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of());
            UDPDiscoveryService mockDiscoveryService = mock(UDPDiscoveryService.class);
            IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
            when(mockDiscoveryService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
            setDiscoveryService(mockDiscoveryService);
            assertTrue(coordinator.announceDiscoveredPeer("server1", "10.0.0.5"));
            assertTrue(coordinator.announceDiscoveredPeer("server1", "10.0.0.6"));
            ArgumentCaptor<DiscoveredService> removeCaptor = ArgumentCaptor.forClass(DiscoveredService.class);
            verify(mockListener).removeDiscoveredService(removeCaptor.capture());
            assertEquals("10.0.0.5", removeCaptor.getValue().getServiceAddress());
            ArgumentCaptor<DiscoveredService> addCaptor = ArgumentCaptor.forClass(DiscoveredService.class);
            verify(mockListener, times(2)).addDiscoveredService(addCaptor.capture());
            assertEquals("10.0.0.6", addCaptor.getValue().getServiceAddress());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void removePeer_afterAnnounce_retractsDiscoveredService() throws Exception {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of());
            setPeerHeartbeatCache(mock(CacheAccess.class));
            CacheAccess<String, ClusterEngineStateMessage> mockEngineCache = mock(CacheAccess.class);
            CompositeCache<String, ClusterEngineStateMessage> mockEngineCacheControl = mock(CompositeCache.class);
            when(mockEngineCache.getCacheControl()).thenReturn(mockEngineCacheControl);
            when(mockEngineCacheControl.getKeySet(true)).thenReturn(Set.of());
            setEngineStateCache(mockEngineCache);
            UDPDiscoveryService mockDiscoveryService = mock(UDPDiscoveryService.class);
            IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
            when(mockDiscoveryService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
            setDiscoveryService(mockDiscoveryService);
            coordinator.addPeer("server2");
            coordinator.announceDiscoveredPeer("server2", "10.0.0.7");
            coordinator.removePeer("server2");
            ArgumentCaptor<DiscoveredService> captor = ArgumentCaptor.forClass(DiscoveredService.class);
            verify(mockListener).removeDiscoveredService(captor.capture());
            assertEquals("10.0.0.7", captor.getValue().getServiceAddress());
        }
    }

    @Test
    void removePeer_neverAnnounced_doesNotInteractWithDiscoveryService() throws Exception {
        UDPDiscoveryService mockDiscoveryService = mock(UDPDiscoveryService.class);
        setDiscoveryService(mockDiscoveryService);
        coordinator.addPeer("server1");
        coordinator.removePeer("server1");
        verify(mockDiscoveryService, never()).getCopyOfDiscoveryListeners();
    }

    @Test
    void getPeerStatusMessage_cacheHasStatusMessage_returnsIt() throws Exception {
        ClusterPeerStatusMessage expected = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1", "inst1", "1.0");
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        when(mockCache.get("peer1")).thenReturn(expected);
        setPeerHeartbeatCache(mockCache);
        assertEquals(expected, coordinator.getPeerStatusMessage("peer1"));
    }

    @Test
    void getPeerStatusMessage_keyNotInCache_returnsNull() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        when(mockCache.get("peer1")).thenReturn(null);
        setPeerHeartbeatCache(mockCache);
        assertNull(coordinator.getPeerStatusMessage("peer1"));
    }

    @Test
    void getObservedPeers_notStarted_returnsEmptySet() {
        assertTrue(coordinator.getObservedPeers().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getObservedPeers_cacheHasKeys_returnsMessages() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        CompositeCache<String, ClusterPeerSecureMessage> mockCacheControl = mock(CompositeCache.class);
        when(mockCache.getCacheControl()).thenReturn(mockCacheControl);
        when(mockCacheControl.getKeySet(true)).thenReturn(Set.of("peer1", "peer2"));
        ClusterPeerStatusMessage peer1Msg = new ClusterPeerStatusMessage(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1", "inst1", "1.0");
        ClusterPeerStatusMessage peer2Msg = new ClusterPeerStatusMessage(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer2", "inst1", "1.0");
        when(mockCache.get("peer1")).thenReturn(peer1Msg);
        when(mockCache.get("peer2")).thenReturn(peer2Msg);
        setPeerHeartbeatCache(mockCache);
        Set<ClusterPeerStatusMessage> observed = coordinator.getObservedPeers();
        assertEquals(2, observed.size());
        Set<String> observedIds = new java.util.HashSet<>();
        for (ClusterPeerStatusMessage msg : observed) {
            observedIds.add(msg.getServerId());
        }
        assertEquals(Set.of("peer1", "peer2"), observedIds);
    }

    @Test
    void sendMessageToPeers_noPeers_doesNotSendToCache() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        setPeerHeartbeatCache(mockCache);
        ClusterPeerStatusMessage msg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        coordinator.sendMessageToPeers(msg);
        verify(mockCache, never()).put(anyString(), any());
    }

    @Test
    void sendEngineStateMessage_noPeers_doesNotSendToCache() throws Exception {
        CacheAccess<String, ClusterEngineStateMessage> mockCache = mock(CacheAccess.class);
        setEngineStateCache(mockCache);
        ClusterEngineStateMessage msg = new ClusterEngineStateMessage("ENGINE_ONLINE", "engine1", "server1", "inst1", "1.0");
        coordinator.sendEngineStateMessage(msg);
        verify(mockCache, never()).put(anyString(), any());
    }

    @Test
    void sendMessageToPeers_cachePutThrows_doesNotThrow() throws Exception {
        coordinator.addPeer("server1");
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        doThrow(new RuntimeException("JCS failure")).when(mockCache).put(anyString(), any());
        setPeerHeartbeatCache(mockCache);
        ClusterPeerStatusMessage msg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        coordinator.sendMessageToPeers(msg);
        assertNull(coordinator.getPeerStatusMessage("server1"));
    }

    @Test
    void addPeer_doesNotShutDownExistingManager() throws Exception {
        CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
        setJcsCacheManager(mockManager);
        coordinator.addPeer("server1");
        assertTrue(coordinator.getPeerIds().contains("server1"));
        verify(mockManager, never()).shutDown();
        verify(mockManager, never()).configure(any(Properties.class));
    }

    @Test
    void start_regularPath_initializesManager() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of());
            verify(mockManager).configure(any(Properties.class));
        }
    }

    @Test
    void start_exceptionDuringConfigure_throwsRuntimeException() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            doThrow(new RuntimeException("configure failed")).when(mockManager).configure(any(Properties.class));
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            assertThrows(RuntimeException.class, () -> coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of()));
        }
    }

    @Test
    void stop_afterStarted_shutsDownManager() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of());
            coordinator.stop();
            verify(mockManager).shutDown();
            assertNull(coordinator.getPeerStatusMessage("server1"));
        }
    }

    @Test
    void addPeer_afterStart_doesNotReconfigureManager() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(new IClusterCacheCoordinator.InitialSettings("server1", "inst1", 1101), Set.of());
            coordinator.addPeer("newPeer");
            verify(mockManager, never()).shutDown();
            verify(mockManager).configure(any(Properties.class));
        }
    }

    private void setPeerHeartbeatCache(CacheAccess<String, ClusterPeerSecureMessage> cache) throws Exception {
        Field f = JcsTcpCacheCoordinator.class.getDeclaredField("peerHeartbeatCache");
        f.setAccessible(true);
        f.set(coordinator, cache);
    }

    private void setEngineStateCache(CacheAccess<String, ClusterEngineStateMessage> cache) throws Exception {
        Field f = JcsTcpCacheCoordinator.class.getDeclaredField("engineStateCache");
        f.setAccessible(true);
        f.set(coordinator, cache);
    }

    private void setJcsCacheManager(CompositeCacheManager manager) throws Exception {
        Field f = JcsTcpCacheCoordinator.class.getDeclaredField("jcsManager");
        f.setAccessible(true);
        f.set(coordinator, manager);
    }

    private void setDiscoveryService(UDPDiscoveryService service) throws Exception {
        Field f = JcsTcpCacheCoordinator.class.getDeclaredField("discoveryService");
        f.setAccessible(true);
        f.set(coordinator, service);
    }
}
