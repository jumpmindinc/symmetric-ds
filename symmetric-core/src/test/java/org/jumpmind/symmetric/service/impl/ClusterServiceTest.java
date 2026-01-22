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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Lock;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for ClusterService.
 */
class ClusterServiceTest {
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
}
