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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

import org.apache.commons.jcs3.access.CacheAccess;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IParameterService;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JcsTcpCacheCoordinatorTest {
    private JcsTcpCacheCoordinator coordinator;

    @BeforeEach
    public void setUp() {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(mockSecurityService);
        coordinator = new JcsTcpCacheCoordinator();
    }

    @Test
    public void getPeerIds_emptyByDefault() {
        assertTrue(coordinator.getPeerIds().isEmpty());
    }

    @Test
    public void addPeer_addsToPeerIds() {
        coordinator.addPeer("server1");
        assertTrue(coordinator.getPeerIds().contains("server1"));
        assertEquals(1, coordinator.getPeerIds().size());
    }

    @Test
    public void addPeer_duplicate_notAddedTwice() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server1");
        assertEquals(1, coordinator.getPeerIds().size());
    }

    @Test
    public void addPeer_multiplePeers_allTracked() {
        coordinator.addPeer("server1");
        coordinator.addPeer("server2");
        coordinator.addPeer("server3");
        assertEquals(3, coordinator.getPeerIds().size());
    }

    @Test
    public void getMessage_notStarted_returnsNull() {
        assertNull(coordinator.getPeerStatusMessage("server1"));
    }

    @Test
    public void sendMessageToPeers_notStarted_doesNotThrow() {
        ClusterPeerStatusMessage msg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        coordinator.sendMessageToPeers(msg);
    }

    @Test
    public void stop_notStarted_doesNotThrow() {
        coordinator.stop();
    }

    @Test
    public void buildPeerList_noPeers_returnsEmptyString() throws Exception {
        setPort(1101);
        assertEquals("", invokeBuildPeerList());
    }

    @Test
    public void buildPeerList_onePeer_returnsHostColonPort() throws Exception {
        setPort(1101);
        coordinator.addPeer("host1");
        assertEquals("host1:1101", invokeBuildPeerList());
    }

    @Test
    public void buildPeerList_multiplePeers_returnsCommaSeparated() throws Exception {
        setPort(2200);
        coordinator.addPeer("host1");
        coordinator.addPeer("host2");
        String result = invokeBuildPeerList();
        assertTrue(result.contains("host1:2200"));
        assertTrue(result.contains("host2:2200"));
        assertTrue(result.contains(","));
    }

    @Test
    public void getMessage_knownRegion_notStarted_returnsNull() {
        assertNull(coordinator.getMessage("SYM_CLUSTER_PEERS", "server1"));
    }

    @Test
    public void getMessage_unknownRegion_returnsNull() {
        assertNull(coordinator.getMessage("OTHER_REGION", "server1"));
    }

    @Test
    public void stop_calledTwice_doesNotThrow() {
        coordinator.stop();
        coordinator.stop();
    }

    @Test
    public void buildJcsProperties_containsRequiredKeys() throws Exception {
        setPort(1101);
        Method m = JcsTcpCacheCoordinator.class.getDeclaredMethod("buildJcsProperties", String.class);
        m.setAccessible(true);
        Properties props = (Properties) m.invoke(coordinator, "host1:1101");
        assertTrue(props.containsKey("jcs.region.SYM_CLUSTER_PEERS"));
        assertTrue(props.containsKey("jcs.auxiliary.LATERAL_TCP"));
        assertTrue(props.containsKey("jcs.auxiliary.LATERAL_TCP.attributes.TcpListenerPort"));
        assertEquals("1101", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.TcpListenerPort"));
        assertEquals("host1:1101", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.TcpServers"));
    }

    @Test
    public void buildJcsProperties_allowGetIsFalse() throws Exception {
        setPort(1101);
        Method m = JcsTcpCacheCoordinator.class.getDeclaredMethod("buildJcsProperties", String.class);
        m.setAccessible(true);
        Properties props = (Properties) m.invoke(coordinator, "");
        assertEquals("false", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.AllowGet"));
    }

    @Test
    public void getPeerStatusMessage_cacheHasStatusMessage_returnsIt() throws Exception {
        ClusterPeerStatusMessage expected = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "peer1", "inst1", "1.0");
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        when(mockCache.get("peer1")).thenReturn(expected);
        setPeerHeartbeatCache(mockCache);
        assertEquals(expected, coordinator.getPeerStatusMessage("peer1"));
    }

    @Test
    public void getPeerStatusMessage_keyNotInCache_returnsNull() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        when(mockCache.get("peer1")).thenReturn(null);
        setPeerHeartbeatCache(mockCache);
        assertNull(coordinator.getPeerStatusMessage("peer1"));
    }

    @Test
    public void sendMessageToPeers_cachePutThrows_doesNotThrow() throws Exception {
        CacheAccess<String, ClusterPeerSecureMessage> mockCache = mock(CacheAccess.class);
        doThrow(new RuntimeException("JCS failure")).when(mockCache).put(anyString(), any());
        setPeerHeartbeatCache(mockCache);
        ClusterPeerStatusMessage msg = new ClusterPeerStatusMessage(
                ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
        coordinator.sendMessageToPeers(msg);
    }

    @Test
    public void reinitJcs_shutdownThrows_doesNotThrow() throws Exception {
        CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
        doThrow(new RuntimeException("shutdown failed")).when(mockManager).shutDown();
        setJcsCacheManager(mockManager);
        coordinator.addPeer("server1");
    }

    @Test
    public void start_regularPath_initializesManager() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(buildMockEngine(1101));
            verify(mockManager).configure(any(Properties.class));
        }
    }

    @Test
    public void start_exceptionDuringConfigure_throwsRuntimeException() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            doThrow(new RuntimeException("configure failed")).when(mockManager).configure(any(Properties.class));
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            assertThrows(RuntimeException.class, () -> coordinator.start(buildMockEngine(1101)));
        }
    }

    @Test
    public void stop_afterStarted_shutsDownManager() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager mockManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance()).thenReturn(mockManager);
            coordinator.start(buildMockEngine(1101));
            coordinator.stop();
            verify(mockManager).shutDown();
            assertNull(coordinator.getPeerStatusMessage("server1"));
        }
    }

    @Test
    public void reinitJcs_regularPath_shutsDownAndReinitsManager() {
        try (MockedStatic<CompositeCacheManager> mocked = mockStatic(CompositeCacheManager.class)) {
            CompositeCacheManager firstManager = mock(CompositeCacheManager.class);
            CompositeCacheManager secondManager = mock(CompositeCacheManager.class);
            mocked.when(() -> CompositeCacheManager.getUnconfiguredInstance())
                    .thenReturn(firstManager, secondManager);
            coordinator.start(buildMockEngine(1101));
            coordinator.addPeer("newPeer");
            verify(firstManager).shutDown();
            verify(secondManager).configure(any(Properties.class));
        }
    }

    private ISymmetricEngine buildMockEngine(int port) {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IClusterService clusterService = mock(IClusterService.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(clusterService.getServerId()).thenReturn("server1");
        when(clusterService.getInstanceId()).thenReturn("inst1");
        when(parameterService.getInt(ServerConstants.CLUSTER_JCS_PORT, 1101)).thenReturn(port);
        return engine;
    }

    private String invokeBuildPeerList() throws Exception {
        Method m = JcsTcpCacheCoordinator.class.getDeclaredMethod("buildPeerList");
        m.setAccessible(true);
        return (String) m.invoke(coordinator);
    }

    private void setPort(int port) throws Exception {
        Field f = JcsTcpCacheCoordinator.class.getDeclaredField("port");
        f.setAccessible(true);
        f.set(coordinator, port);
    }

    private void setPeerHeartbeatCache(CacheAccess<String, ClusterPeerSecureMessage> cache) throws Exception {
        Field f = JcsTcpCacheCoordinator.class.getDeclaredField("peerHeartbeatCache");
        f.setAccessible(true);
        f.set(coordinator, cache);
    }

    private void setJcsCacheManager(CompositeCacheManager manager) throws Exception {
        Field f = JcsTcpCacheCoordinator.class.getDeclaredField("jcsCacheManager");
        f.setAccessible(true);
        f.set(coordinator, manager);
    }
}
