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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.cache.ClusteredCacheManager;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Lock;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Tests for ClusterService.
 */
class ClusterServiceTest {
    private static final String EXISTING_CLUSTER_PARTITION_ID = "existing-cluster-partition-id";
    private IParameterService parameterService;
    private ISymmetricDialect dialect;
    private INodeService nodeService;
    private IExtensionService extensionService;
    private ISqlTemplate sqlTemplate;
    private IDatabasePlatform platform;
    private ClusterService clusterService;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        dialect = mock(ISymmetricDialect.class);
        nodeService = mock(INodeService.class);
        extensionService = mock(IExtensionService.class);
        sqlTemplate = mock(ISqlTemplate.class);
        platform = mock(IDatabasePlatform.class);
        when(dialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(parameterService.getLong(ParameterConstants.CLUSTER_LOCK_TIMEOUT_MS)).thenReturn(60000L);
        when(parameterService.getLong(ParameterConstants.LOCK_TIMEOUT_MS)).thenReturn(60000L);
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(false);
        when(nodeService.findIdentityNodeId()).thenReturn("test-node");
        when(nodeService.findNodeHosts(anyString())).thenReturn(new ArrayList<>());
        clusterService = new ClusterService(parameterService, dialect, nodeService, extensionService);
    }

    @Test
    void testLoadLocksFromDatabase_mergesLastLockTimeIntoCache() {
        Date lastLockTime = new Date(System.currentTimeMillis() - 60000);
        String lastLockingServerId = "server-1";
        List<Lock> dbLocks = new ArrayList<>();
        Lock dbLock = new Lock();
        dbLock.setLockAction(ClusterConstants.PUSH);
        dbLock.setLockType(ClusterConstants.TYPE_CLUSTER);
        dbLock.setLastLockTime(lastLockTime);
        dbLock.setLastLockingServerId(lastLockingServerId);
        dbLocks.add(dbLock);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(dbLocks);
        clusterService.loadLocksFromDatabase();
        Map<String, Lock> locks = clusterService.findLocks();
        Lock cachedLock = locks.get(ClusterConstants.PUSH);
        assertNotNull(cachedLock);
        assertEquals(lastLockTime, cachedLock.getLastLockTime());
        assertEquals(lastLockingServerId, cachedLock.getLastLockingServerId());
    }

    @Test
    void testLoadLocksFromDatabase_ignoresUnknownActions() {
        List<Lock> dbLocks = new ArrayList<>();
        Lock dbLock = new Lock();
        dbLock.setLockAction("UNKNOWN_ACTION");
        dbLock.setLockType(ClusterConstants.TYPE_CLUSTER);
        dbLock.setLastLockTime(new Date());
        dbLock.setLastLockingServerId("server-1");
        dbLocks.add(dbLock);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(dbLocks);
        clusterService.loadLocksFromDatabase();
        Map<String, Lock> locks = clusterService.findLocks();
        assertNull(locks.get("UNKNOWN_ACTION"));
    }

    @Test
    void testLoadLocksFromDatabase_handlesEmptyResult() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(new ArrayList<>());
        clusterService.loadLocksFromDatabase();
        Map<String, Lock> locks = clusterService.findLocks();
        Lock cachedLock = locks.get(ClusterConstants.PUSH);
        assertNotNull(cachedLock);
        assertNull(cachedLock.getLastLockTime());
    }

    @Test
    void testLoadLocksFromDatabase_handlesException() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenThrow(new RuntimeException("DB error"));
        clusterService.loadLocksFromDatabase();
        Map<String, Lock> locks = clusterService.findLocks();
        assertNotNull(locks.get(ClusterConstants.PUSH));
    }

    @Test
    void testPersistLastLockTime_updateSucceeds() {
        Date lastLockTime = new Date();
        String lastLockingServerId = "server-1";
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        clusterService.persistLastLockTime(ClusterConstants.PUSH, lastLockTime, lastLockingServerId);
        // Only update called, no insert needed
        verify(sqlTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void testPersistLastLockTime_updateReturnsZero_insertSucceeds() {
        Date lastLockTime = new Date();
        String lastLockingServerId = "server-1";
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0).thenReturn(1);
        clusterService.persistLastLockTime(ClusterConstants.PUSH, lastLockTime, lastLockingServerId);
        // Update returns 0, then insert called
        verify(sqlTemplate, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void testPersistLastLockTime_updateReturnsZero_insertThrowsUniqueKey() {
        Date lastLockTime = new Date();
        String lastLockingServerId = "server-1";
        when(sqlTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(0)
                .thenThrow(new UniqueKeyException());
        clusterService.persistLastLockTime(ClusterConstants.PUSH, lastLockTime, lastLockingServerId);
        // Update returns 0, insert throws UniqueKeyException - handled silently
        verify(sqlTemplate, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void testPersistLastLockTime_handlesException() {
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("DB error"));
        clusterService.persistLastLockTime(ClusterConstants.PUSH, new Date(), "server-1");
    }

    @Test
    void testUnlockCluster_persistsLastLockTime() {
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        clusterService.lock(ClusterConstants.PUSH);
        clusterService.unlock(ClusterConstants.PUSH);
        Map<String, Lock> locks = clusterService.findLocks();
        Lock lock = locks.get(ClusterConstants.PUSH);
        assertNotNull(lock.getLastLockTime());
        assertNotNull(lock.getLastLockingServerId());
        assertNull(lock.getLockTime());
        assertNull(lock.getLockingServerId());
    }

    @Test
    void testInitCallsLoadLocksFromDatabase() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class))).thenReturn(new ArrayList<>());
        clusterService.init();
        verify(sqlTemplate).query(anyString(), any(ISqlRowMapper.class));
    }

    @Test
    void testGenerateClusterPartitionId_lockingDisabled_stillGeneratesAndSavesToContext() {
        when(sqlTemplate.queryForString(anyString(), any(Object[].class))).thenReturn(null);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0).thenReturn(1);
        clusterService.generateClusterPartitionId();
        assertNotNull(clusterService.getClusterPartitionId());
        verify(sqlTemplate, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void testGenerateClusterPartitionId_lockingEnabled_noExistingContextValue_generatesAndSaves() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        when(sqlTemplate.queryForString(anyString(), any(Object[].class))).thenReturn(null);
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(0).thenReturn(1);
        clusterService.generateClusterPartitionId();
        assertNotNull(clusterService.getClusterPartitionId());
        verify(sqlTemplate, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void testGenerateClusterPartitionId_lockingEnabled_existingContextValue_reusesIt() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        when(sqlTemplate.queryForString(anyString(), any(Object[].class))).thenReturn(EXISTING_CLUSTER_PARTITION_ID);
        clusterService.generateClusterPartitionId();
        assertEquals(EXISTING_CLUSTER_PARTITION_ID, clusterService.getClusterPartitionId());
        verify(sqlTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void testGenerateClusterPartitionId_configuredProperty_takesPriorityOverContext() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        when(parameterService.getString(ServerConstants.CLUSTER_PARTITION_ID)).thenReturn("configured-partition-id");
        when(sqlTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        clusterService.generateClusterPartitionId();
        assertEquals("configured-partition-id", clusterService.getClusterPartitionId());
        verify(sqlTemplate, never()).queryForString(anyString(), any(Object[].class));
    }

    @Test
    void testGenerateClusterPartitionId_calledTwice_onlyResolvesOnce() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        when(sqlTemplate.queryForString(anyString(), any(Object[].class))).thenReturn(EXISTING_CLUSTER_PARTITION_ID);
        clusterService.generateClusterPartitionId();
        clusterService.generateClusterPartitionId();
        verify(sqlTemplate, times(1)).queryForString(anyString(), any(Object[].class));
    }

    @Test
    void testWriteAndReadClusterPartitionId_roundTripsThroughFile(@TempDir File tempDir) throws Exception {
        File clusterPartitionIdFile = new File(tempDir, "cluster-partition.uuid");
        Method write = ClusterService.class.getDeclaredMethod("writeClusterPartitionId", File.class, String.class);
        write.setAccessible(true);
        write.invoke(clusterService, clusterPartitionIdFile, "file-cluster-partition-id");
        Method read = ClusterService.class.getDeclaredMethod("readClusterPartitionId", File.class);
        read.setAccessible(true);
        assertEquals("file-cluster-partition-id", read.invoke(clusterService, clusterPartitionIdFile));
    }

    @Test
    void testReadClusterPartitionId_missingFile_returnsNull(@TempDir File tempDir) throws Exception {
        File clusterPartitionIdFile = new File(tempDir, "does-not-exist.uuid");
        Method read = ClusterService.class.getDeclaredMethod("readClusterPartitionId", File.class);
        read.setAccessible(true);
        assertNull(read.invoke(clusterService, clusterPartitionIdFile));
    }

    @Test
    void testApplyUuidMarker_embeddingAutoMarker() {
        UUID original = UUID.fromString("12345678-9abc-def0-1234-567890abcdef");
        UUID marked = ClusterService.applyUuidMarker(original, ServerConstants.INSTANCE_UUID_MARKER_AUTO);
        assertEquals("12345678-aaaa-def0-1234-567890abcdef", marked.toString());
    }

    @Test
    void testApplyUuidMarker_embeddingHardwareMarker() {
        UUID original = UUID.fromString("12345678-9abc-def0-1234-567890abcdef");
        UUID marked = ClusterService.applyUuidMarker(original, ServerConstants.INSTANCE_UUID_MARKER_HARDWARE);
        assertEquals("12345678-bbbb-def0-1234-567890abcdef", marked.toString());
    }

    @Test
    void testApplyUuidMarker_embeddingConfiguredMarker() {
        UUID original = UUID.fromString("12345678-9abc-def0-1234-567890abcdef");
        UUID marked = ClusterService.applyUuidMarker(original, ServerConstants.INSTANCE_UUID_MARKER_CONFIGURED);
        assertEquals("12345678-cccc-def0-1234-567890abcdef", marked.toString());
    }

    @Test
    void testApplyUuidMarker_preservesAllOtherBytes() {
        UUID original = UUID.fromString("aabbccdd-eeff-1122-3344-556677889900");
        UUID marked = ClusterService.applyUuidMarker(original, ServerConstants.INSTANCE_UUID_MARKER_AUTO);
        String s = marked.toString();
        assertEquals("aabbccdd", s.substring(0, 8));
        assertEquals("1122", s.substring(14, 18));
        assertEquals("3344-556677889900", s.substring(19));
    }

    @Test
    void testApplyUuidMarkerToId_hostnamePrefix() {
        String input = "myhost-12345678-9abc-def0-1234-567890abcdef";
        String result = ClusterService.applyUuidMarkerToId(input, ServerConstants.INSTANCE_UUID_MARKER_AUTO);
        assertEquals("myhost-12345678-aaaa-def0-1234-567890abcdef", result);
    }

    @Test
    void testApplyUuidMarkerToId_plainUuid() {
        String input = "12345678-9abc-def0-1234-567890abcdef";
        String result = ClusterService.applyUuidMarkerToId(input, ServerConstants.INSTANCE_UUID_MARKER_CONFIGURED);
        assertEquals("12345678-cccc-def0-1234-567890abcdef", result);
    }

    @Test
    void testApplyUuidMarkerToId_nullUsesZeroUuidWithMarker() {
        String result = ClusterService.applyUuidMarkerToId(null, ServerConstants.INSTANCE_UUID_MARKER_AUTO);
        assertEquals(36, result.length());
        int byte4 = Integer.parseInt(result.substring(9, 11), 16);
        int byte5 = Integer.parseInt(result.substring(11, 13), 16);
        assertEquals(0xaa, byte4);
        assertEquals(0xaa, byte5);
    }

    @Test
    void testApplyUuidMarkerToId_shortStringPrefixedBeforeZeroUuid() {
        String result = ClusterService.applyUuidMarkerToId("abc", ServerConstants.INSTANCE_UUID_MARKER_AUTO);
        assertEquals(39, result.length());
        assertTrue(result.startsWith("abc"));
        int uuidStart = result.length() - 36;
        int byte4 = Integer.parseInt(result.substring(uuidStart + 9, uuidStart + 11), 16);
        int byte5 = Integer.parseInt(result.substring(uuidStart + 11, uuidStart + 13), 16);
        assertEquals(0xaa, byte4);
        assertEquals(0xaa, byte5);
    }

    @Test
    void testApplyUuidMarkerToId_nonUuidSuffixReturnedUnchanged() {
        String input = "prefix-GGGGGGGG-GGGG-GGGG-GGGG-GGGGGGGGGGGG";
        assertEquals(input, ClusterService.applyUuidMarkerToId(input, ServerConstants.INSTANCE_UUID_MARKER_AUTO));
    }

    @Test
    void testGenerateInstanceId_hasAutoMarker() {
        String instanceId = ClusterService.generateInstanceId("testhost");
        String uuidPart = instanceId.substring(instanceId.length() - 36);
        int byte4 = Integer.parseInt(uuidPart.substring(9, 11), 16);
        int byte5 = Integer.parseInt(uuidPart.substring(11, 13), 16);
        assertEquals(0xaa, byte4);
        assertEquals(0xaa, byte5);
    }

    @Test
    void testGenerateInstanceId_hostnameIsTruncatedTo23Chars() {
        String longHost = "this-hostname-is-definitely-longer-than-23-characters";
        String instanceId = ClusterService.generateInstanceId(longHost);
        String prefix = instanceId.substring(0, instanceId.length() - 37);
        assertEquals(23, prefix.length());
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void clearActivePeers() throws Exception {
        Field peerState = ClusteredCacheManager.class.getDeclaredField("peerStateMap");
        peerState.setAccessible(true);
        ((Map<String, Boolean>) peerState.get(ClusteredCacheManager.getInstance())).clear();
        Field peerOffline = ClusteredCacheManager.class.getDeclaredField("peerOfflineTimestampMs");
        peerOffline.setAccessible(true);
        ((Map<String, Long>) peerOffline.get(ClusteredCacheManager.getInstance())).clear();
    }

    @SuppressWarnings("unchecked")
    private void injectActivePeer(String peerId) throws Exception {
        Field f = ClusteredCacheManager.class.getDeclaredField("peerStateMap");
        f.setAccessible(true);
        ((Map<String, Boolean>) f.get(ClusteredCacheManager.getInstance())).put(peerId, Boolean.TRUE);
    }

    @Test
    void testIsStaleServer_nullOwner_returnsFalse() {
        assertFalse(clusterService.isStaleServer(null));
    }

    @Test
    void testIsStaleServer_emptyActivePeers_returnsFalse() {
        assertFalse(clusterService.isStaleServer("other-server"));
    }

    @Test
    void testIsStaleServer_ownerInActivePeers_returnsFalse() throws Exception {
        injectActivePeer("other-server");
        assertFalse(clusterService.isStaleServer("other-server"));
    }

    @Test
    void testIsStaleServer_ownerAbsentFromActivePeers_returnsTrue() throws Exception {
        injectActivePeer("some-active-peer");
        assertTrue(clusterService.isStaleServer("absent-server"));
    }

    @Test
    void testIsStaleServer_ownServerId_returnsFalse() throws Exception {
        injectActivePeer("some-active-peer");
        assertFalse(clusterService.isStaleServer(clusterService.getServerId()));
    }

    @Test
    void testIsLockExpiredOrServerStale_nullLockTime_returnsTrue() {
        assertTrue(clusterService.isLockExpiredOrServerStale(null, null, new Date()));
    }

    @Test
    void testIsLockExpiredOrServerStale_expiredLock_returnsTrue() {
        Date lockTime = new Date(System.currentTimeMillis() - 120_000);
        Date lockTimeout = new Date(System.currentTimeMillis() - 60_000);
        assertTrue(clusterService.isLockExpiredOrServerStale("other-server", lockTime, lockTimeout));
    }

    @Test
    void testIsLockExpiredOrServerStale_freshLock_notStale_returnsFalse() {
        Date lockTime = new Date();
        Date lockTimeout = new Date(System.currentTimeMillis() - 60_000);
        assertFalse(clusterService.isLockExpiredOrServerStale("other-server", lockTime, lockTimeout));
    }

    @Test
    void testIsLockExpiredOrServerStale_freshLock_ownerStale_returnsTrue() throws Exception {
        injectActivePeer("some-active-peer");
        Date lockTime = new Date();
        Date lockTimeout = new Date(System.currentTimeMillis() - 60_000);
        assertTrue(clusterService.isLockExpiredOrServerStale("absent-server", lockTime, lockTimeout));
    }

    @Test
    void testLockCluster_staleOwner_breaksLockBeforeTimeout() throws Exception {
        injectActivePeer("some-active-peer");
        Lock lock = clusterService.lockCache.get(ClusterConstants.PUSH);
        lock.setLockingServerId("absent-server");
        lock.setLockTime(new Date());
        Date timeToBreakLock = new Date(System.currentTimeMillis() - 1);
        assertTrue(clusterService.lockCluster(ClusterConstants.PUSH, timeToBreakLock, new Date(), clusterService.getServerId()));
    }

    @Test
    void testLockCluster_freshLockNotStale_doesNotBreak() {
        Lock lock = clusterService.lockCache.get(ClusterConstants.PUSH);
        lock.setLockingServerId("active-server");
        lock.setLockTime(new Date());
        Date timeToBreakLock = new Date(System.currentTimeMillis() - 1);
        assertFalse(clusterService.lockCluster(ClusterConstants.PUSH, timeToBreakLock, new Date(), clusterService.getServerId()));
    }
}
