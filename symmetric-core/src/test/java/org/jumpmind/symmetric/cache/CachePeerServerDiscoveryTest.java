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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.apache.commons.jcs3.utils.discovery.DiscoveredService;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryManager;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryService;
import org.apache.commons.jcs3.utils.discovery.behavior.IDiscoveryListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class CachePeerServerDiscoveryTest {
    private final CompositeCacheManager jcsManager = mock(CompositeCacheManager.class);

    private DiscoveryContext contextWithJcsManager() {
        return new DiscoveryContext(jcsManager, 4001, List.of("region1", "region2"), "server1");
    }

    private DiscoveryContext contextWithoutJcsManager() {
        return new DiscoveryContext(null, 4001, List.of("region1"), "server1");
    }

    @Test
    void enrichJcsProperties_doesNotModifyProperties() {
        CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
        Properties props = new Properties();
        props.setProperty("existing", "value");
        discovery.enrichJcsProperties(props, "jcs.auxiliary.LATERAL_TCP.attributes");
        assertEquals(1, props.size());
        assertEquals("value", props.getProperty("existing"));
    }

    @Test
    void start_setsContext() {
        CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
        DiscoveryContext ctx = contextWithJcsManager();
        discovery.start(ctx);
        assertSame(ctx, discovery.context);
    }

    @Test
    void announcePeer_blankAddress_returnsFalse() {
        CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
        discovery.start(contextWithJcsManager());
        assertFalse(discovery.announcePeer("server2", "   "));
        assertFalse(discovery.announcePeer("server2", null));
        assertFalse(discovery.announcePeer("server2", ""));
    }

    @Test
    void announcePeer_notStarted_returnsFalse() {
        CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
        assertFalse(discovery.announcePeer("server2", "10.0.0.2:4001"));
    }

    @Test
    void announcePeer_contextWithoutJcsManager_returnsFalse() {
        CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
        discovery.start(contextWithoutJcsManager());
        assertFalse(discovery.announcePeer("server2", "10.0.0.2:4001"));
    }

    @Test
    void announcePeer_newPeer_returnsTrueAndAnnouncesToListeners() {
        UDPDiscoveryService mockService = mock(UDPDiscoveryService.class);
        IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
        when(mockService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
        try (MockedStatic<UDPDiscoveryManager> mockedManagerStatic = mockStatic(UDPDiscoveryManager.class)) {
            UDPDiscoveryManager mockManager = mock(UDPDiscoveryManager.class);
            mockedManagerStatic.when(UDPDiscoveryManager::getInstance).thenReturn(mockManager);
            when(mockManager.getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any())).thenReturn(mockService);
            CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            assertTrue(discovery.announcePeer("server2", "10.0.0.2:4001"));
            ArgumentCaptor<DiscoveredService> captor = ArgumentCaptor.forClass(DiscoveredService.class);
            verify(mockListener).addDiscoveredService(captor.capture());
            assertEquals("10.0.0.2:4001", captor.getValue().getServiceAddress());
            assertEquals(4001, captor.getValue().getServicePort());
            assertEquals(List.of("region1", "region2"), captor.getValue().getCacheNames());
        }
    }

    @Test
    void announcePeer_sameAddressAnnouncedTwice_secondCallReturnsFalseAndDoesNotReannounce() {
        UDPDiscoveryService mockService = mock(UDPDiscoveryService.class);
        IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
        when(mockService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
        try (MockedStatic<UDPDiscoveryManager> mockedManagerStatic = mockStatic(UDPDiscoveryManager.class)) {
            UDPDiscoveryManager mockManager = mock(UDPDiscoveryManager.class);
            mockedManagerStatic.when(UDPDiscoveryManager::getInstance).thenReturn(mockManager);
            when(mockManager.getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any())).thenReturn(mockService);
            CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            assertTrue(discovery.announcePeer("server2", "10.0.0.2:4001"));
            assertFalse(discovery.announcePeer("server2", "10.0.0.2:4001"));
            verify(mockListener, times(1)).addDiscoveredService(any());
            verify(mockListener, never()).removeDiscoveredService(any());
        }
    }

    @Test
    void announcePeer_addressChanged_retractsOldAnnouncesNewAndReturnsTrue() {
        UDPDiscoveryService mockService = mock(UDPDiscoveryService.class);
        IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
        when(mockService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
        try (MockedStatic<UDPDiscoveryManager> mockedManagerStatic = mockStatic(UDPDiscoveryManager.class)) {
            UDPDiscoveryManager mockManager = mock(UDPDiscoveryManager.class);
            mockedManagerStatic.when(UDPDiscoveryManager::getInstance).thenReturn(mockManager);
            when(mockManager.getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any())).thenReturn(mockService);
            CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            assertTrue(discovery.announcePeer("server2", "10.0.0.2:4001"));
            assertTrue(discovery.announcePeer("server2", "10.0.0.3:4001"));
            ArgumentCaptor<DiscoveredService> removeCaptor = ArgumentCaptor.forClass(DiscoveredService.class);
            verify(mockListener).removeDiscoveredService(removeCaptor.capture());
            assertEquals("10.0.0.2:4001", removeCaptor.getValue().getServiceAddress());
            ArgumentCaptor<DiscoveredService> addCaptor = ArgumentCaptor.forClass(DiscoveredService.class);
            verify(mockListener, times(2)).addDiscoveredService(addCaptor.capture());
            assertEquals("10.0.0.3:4001", addCaptor.getValue().getServiceAddress());
        }
    }

    @Test
    void retractPeer_unknownServerId_returnsFalse() {
        CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
        discovery.start(contextWithJcsManager());
        assertFalse(discovery.retractPeer("unknown"));
    }

    @Test
    void retractPeer_knownServerIdWithAvailableService_returnsTrueAndRetracts() {
        UDPDiscoveryService mockService = mock(UDPDiscoveryService.class);
        IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
        when(mockService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
        try (MockedStatic<UDPDiscoveryManager> mockedManagerStatic = mockStatic(UDPDiscoveryManager.class)) {
            UDPDiscoveryManager mockManager = mock(UDPDiscoveryManager.class);
            mockedManagerStatic.when(UDPDiscoveryManager::getInstance).thenReturn(mockManager);
            when(mockManager.getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any())).thenReturn(mockService);
            CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            discovery.announcePeer("server2", "10.0.0.2:4001");
            assertTrue(discovery.retractPeer("server2"));
            verify(mockListener).removeDiscoveredService(any());
            assertFalse(discovery.retractPeer("server2"));
        }
    }

    @Test
    void retractPeer_knownServerIdWithUnavailableService_returnsTrueWithoutRetracting() {
        UDPDiscoveryService mockService = mock(UDPDiscoveryService.class);
        IDiscoveryListener mockListener = mock(IDiscoveryListener.class);
        when(mockService.getCopyOfDiscoveryListeners()).thenReturn(Set.of(mockListener));
        try (MockedStatic<UDPDiscoveryManager> mockedManagerStatic = mockStatic(UDPDiscoveryManager.class)) {
            UDPDiscoveryManager mockManager = mock(UDPDiscoveryManager.class);
            mockedManagerStatic.when(UDPDiscoveryManager::getInstance).thenReturn(mockManager);
            when(mockManager.getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any())).thenReturn(mockService);
            CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            discovery.announcePeer("server2", "10.0.0.2:4001");
            discovery.start(contextWithoutJcsManager());
            assertTrue(discovery.retractPeer("server2"));
            verify(mockListener, never()).removeDiscoveredService(any());
        }
    }

    @Test
    void stop_clearsContextDiscoveryServiceAndKnownPeers() {
        UDPDiscoveryService mockService = mock(UDPDiscoveryService.class);
        when(mockService.getCopyOfDiscoveryListeners()).thenReturn(Set.of());
        try (MockedStatic<UDPDiscoveryManager> mockedManagerStatic = mockStatic(UDPDiscoveryManager.class)) {
            UDPDiscoveryManager mockManager = mock(UDPDiscoveryManager.class);
            mockedManagerStatic.when(UDPDiscoveryManager::getInstance).thenReturn(mockManager);
            when(mockManager.getService(anyString(), anyInt(), any(), anyInt(), anyInt(), any(), any())).thenReturn(mockService);
            CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            discovery.announcePeer("server2", "10.0.0.2:4001");
            discovery.stop();
            assertNull(discovery.context);
            assertNull(discovery.getUdpDiscoveryService());
            assertFalse(discovery.retractPeer("server2"));
        }
    }

    @Test
    void getUdpDiscoveryService_contextNotStarted_returnsNull() {
        CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
        assertNull(discovery.getUdpDiscoveryService());
    }

    @Test
    void getUdpDiscoveryService_contextWithoutJcsManager_returnsNull() {
        CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
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
            CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
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
            CachePeerServerDiscovery discovery = new CachePeerServerDiscovery();
            discovery.start(contextWithJcsManager());
            assertNull(discovery.getUdpDiscoveryService());
        }
    }
}
