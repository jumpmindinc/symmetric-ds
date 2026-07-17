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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.jcs3.auxiliary.AuxiliaryCache;
import org.apache.commons.jcs3.auxiliary.lateral.LateralCacheNoWaitFacade;
import org.apache.commons.jcs3.engine.control.CompositeCache;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryManager;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryService;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.CacheCoordinatorNetworkSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BaseCachePeerServerDiscoveryTest {
    private final CompositeCacheManager mockJcsManager = mock(CompositeCacheManager.class);
    private CompositeCacheManager realJcsManager;

    @AfterEach
    void tearDown() {
        if (realJcsManager != null) {
            realJcsManager.shutDown();
            realJcsManager = null;
        }
    }

    private DiscoveryContext contextWithJcsManager() {
        return new DiscoveryContext(mockJcsManager, 4001, List.of("region1", "region2"), "server1");
    }

    private DiscoveryContext contextWithoutJcsManager() {
        return new DiscoveryContext(null, 4001, List.of("region1"), "server1");
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private DiscoveryContext realContext() throws Exception {
        int port = findFreePort();
        CacheCoordinatorNetworkSettings networkSettings = new CacheCoordinatorNetworkSettings("server1", "inst1", port, "db", 3000L);
        Properties jcsProperties = JcsPropertiesBuilder.build(networkSettings, Collections.emptySet());
        realJcsManager = CompositeCacheManager.getUnconfiguredInstance();
        realJcsManager.configure(jcsProperties);
        return new DiscoveryContext(realJcsManager, port, List.of(JcsPropertiesBuilder.PEER_REGION, JcsPropertiesBuilder.ENGINE_REGION), "server1");
    }

    @SuppressWarnings("unchecked")
    private <K, V> LateralCacheNoWaitFacade<K, V> getLateralFacade(String regionName) {
        CompositeCache<K, V> cache = realJcsManager.getCache(regionName);
        for (AuxiliaryCache<K, V> aux : cache.getAuxCacheList()) {
            if (aux instanceof LateralCacheNoWaitFacade) {
                return (LateralCacheNoWaitFacade<K, V>) aux;
            }
        }
        return null;
    }

    @Test
    void enrichJcsProperties_doesNotModifyProperties() {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        Properties props = new Properties();
        props.setProperty("existing", "value");
        discovery.enrichJcsProperties(props, "jcs.auxiliary.LATERAL_TCP.attributes");
        assertEquals(1, props.size());
        assertEquals("value", props.getProperty("existing"));
    }

    @Test
    void start_setsContext() {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        DiscoveryContext ctx = contextWithJcsManager();
        discovery.start(ctx);
        assertSame(ctx, discovery.context);
    }

    @Test
    void announcePeer_blankAddress_returnsFalse() {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(contextWithJcsManager());
        assertFalse(discovery.announcePeer("server2", "   "));
        assertFalse(discovery.announcePeer("server2", null));
        assertFalse(discovery.announcePeer("server2", ""));
    }

    @Test
    void announcePeer_notStarted_returnsFalse() {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        assertFalse(discovery.announcePeer("server2", "10.0.0.2:4001"));
    }

    @Test
    void announcePeer_contextWithoutJcsManager_returnsFalse() {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(contextWithoutJcsManager());
        assertFalse(discovery.announcePeer("server2", "10.0.0.2:4001"));
    }

    @Test
    void announcePeer_newPeer_wiresLateralConnectionIntoEveryRegion() throws Exception {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(realContext());
        String address = "127.0.0.1:" + findFreePort();
        assertTrue(discovery.announcePeer("server2", address));
        assertTrue(getLateralFacade(JcsPropertiesBuilder.PEER_REGION).containsNoWait(address));
        assertTrue(getLateralFacade(JcsPropertiesBuilder.ENGINE_REGION).containsNoWait(address));
    }

    @Test
    void announcePeer_bareAddressWithoutPort_appendsContextPortToBuildTcpServerKey() throws Exception {
        // AbstractSymmetricEngine.refreshClusterPeers announces bare SYM_NODE_HOST IP addresses with no port; all cluster nodes share one configured port.
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        DiscoveryContext ctx = realContext();
        discovery.start(ctx);
        String bareAddress = "127.0.0.1";
        assertTrue(discovery.announcePeer("server2", bareAddress));
        assertTrue(getLateralFacade(JcsPropertiesBuilder.PEER_REGION).containsNoWait(bareAddress + ":" + ctx.port()));
    }

    @Test
    void announcePeer_sameAddressAnnouncedTwice_secondCallReturnsFalseAndDoesNotDuplicate() throws Exception {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(realContext());
        String address = "127.0.0.1:" + findFreePort();
        assertTrue(discovery.announcePeer("server2", address));
        assertFalse(discovery.announcePeer("server2", address));
        assertTrue(getLateralFacade(JcsPropertiesBuilder.PEER_REGION).containsNoWait(address));
    }

    @Test
    void announcePeer_addressChanged_removesOldConnectionAndAddsNew() throws Exception {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(realContext());
        String oldAddress = "127.0.0.1:" + findFreePort();
        String newAddress = "127.0.0.1:" + findFreePort();
        assertTrue(discovery.announcePeer("server2", oldAddress));
        assertTrue(discovery.announcePeer("server2", newAddress));
        assertFalse(getLateralFacade(JcsPropertiesBuilder.PEER_REGION).containsNoWait(oldAddress));
        assertTrue(getLateralFacade(JcsPropertiesBuilder.PEER_REGION).containsNoWait(newAddress));
    }

    @Test
    @SuppressWarnings("unchecked")
    void announcePeer_regionHasNoLateralFacade_skipsWithoutThrowing() {
        CompositeCache<Object, Object> mockCache = mock(CompositeCache.class);
        when(mockCache.getAuxCacheList()).thenReturn(Collections.emptyList());
        when(mockJcsManager.getCache(anyString())).thenReturn(mockCache);
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(contextWithJcsManager());
        assertTrue(discovery.announcePeer("server2", "10.0.0.2:4001"));
    }

    @Test
    void announcePeer_jcsManagerGetCacheThrows_isCaughtForBothAddAndRemove() {
        when(mockJcsManager.getCache(anyString())).thenThrow(new RuntimeException("boom"));
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(contextWithJcsManager());
        assertTrue(discovery.announcePeer("server2", "10.0.0.2:4001"));
        assertTrue(discovery.announcePeer("server2", "10.0.0.3:4001"));
    }

    @Test
    void retractPeer_unknownServerId_returnsFalse() {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(contextWithJcsManager());
        assertFalse(discovery.retractPeer("unknown"));
    }

    @Test
    void retractPeer_knownServerId_removesLateralConnectionAndReturnsTrue() throws Exception {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(realContext());
        String address = "127.0.0.1:" + findFreePort();
        discovery.announcePeer("server2", address);
        assertTrue(discovery.retractPeer("server2"));
        assertFalse(getLateralFacade(JcsPropertiesBuilder.PEER_REGION).containsNoWait(address));
        assertFalse(discovery.retractPeer("server2"));
    }

    @Test
    void retractPeer_knownServerIdWithUnavailableContext_returnsTrueWithoutThrowing() throws Exception {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(realContext());
        String address = "127.0.0.1:" + findFreePort();
        discovery.announcePeer("server2", address);
        discovery.start(contextWithoutJcsManager());
        assertTrue(discovery.retractPeer("server2"));
    }

    @Test
    void stop_clearsContextAndKnownPeers() throws Exception {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(realContext());
        discovery.announcePeer("server2", "127.0.0.1:" + findFreePort());
        discovery.stop();
        assertNull(discovery.context);
        assertNull(discovery.getUdpDiscoveryService());
        assertFalse(discovery.retractPeer("server2"));
    }

    @Test
    void getUdpDiscoveryService_contextNotStarted_returnsNull() {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        assertNull(discovery.getUdpDiscoveryService());
    }

    @Test
    void getUdpDiscoveryService_contextWithoutJcsManager_returnsNull() {
        BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
        discovery.start(contextWithoutJcsManager());
        assertNull(discovery.getUdpDiscoveryService());
    }

    @Test
    void getUdpDiscoveryService_createsAndCachesServiceOnSubsequentCalls() {
        UDPDiscoveryService mockService = mock(UDPDiscoveryService.class);
        try (MockedStatic<UDPDiscoveryManager> mockedManagerStatic = mockStatic(UDPDiscoveryManager.class)) {
            UDPDiscoveryManager mockManager = mock(UDPDiscoveryManager.class);
            mockedManagerStatic.when(UDPDiscoveryManager::getInstance).thenReturn(mockManager);
            when(mockManager.getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any())).thenReturn(mockService);
            BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            assertSame(mockService, discovery.getUdpDiscoveryService());
            assertSame(mockService, discovery.getUdpDiscoveryService());
            verify(mockManager, times(1)).getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any());
        }
    }

    @Test
    void getUdpDiscoveryService_managerThrowsException_returnsNull() {
        try (MockedStatic<UDPDiscoveryManager> mockedManagerStatic = mockStatic(UDPDiscoveryManager.class)) {
            UDPDiscoveryManager mockManager = mock(UDPDiscoveryManager.class);
            mockedManagerStatic.when(UDPDiscoveryManager::getInstance).thenReturn(mockManager);
            when(mockManager.getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any()))
                    .thenThrow(new RuntimeException("boom"));
            BaseCachePeerServerDiscovery discovery = new BaseCachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            assertNull(discovery.getUdpDiscoveryService());
        }
    }
}
