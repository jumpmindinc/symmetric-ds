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

import static org.jumpmind.symmetric.service.ClusterConstants.COMPARE;
import static org.jumpmind.symmetric.service.ClusterConstants.DATA_REFRESH;
import static org.jumpmind.symmetric.service.ClusterConstants.FILE_SYNC_PULL;
import static org.jumpmind.symmetric.service.ClusterConstants.FILE_SYNC_PUSH;
import static org.jumpmind.symmetric.service.ClusterConstants.FILE_SYNC_SHARED;
import static org.jumpmind.symmetric.service.ClusterConstants.FILE_SYNC_TRACKER;
import static org.jumpmind.symmetric.service.ClusterConstants.HEARTBEAT;
import static org.jumpmind.symmetric.service.ClusterConstants.INITIAL_LOAD_EXTRACT;
import static org.jumpmind.symmetric.service.ClusterConstants.INITIAL_LOAD_QUEUE;
import static org.jumpmind.symmetric.service.ClusterConstants.LOG_MINER;
import static org.jumpmind.symmetric.service.ClusterConstants.MONITOR;
import static org.jumpmind.symmetric.service.ClusterConstants.OFFLINE_PULL;
import static org.jumpmind.symmetric.service.ClusterConstants.OFFLINE_PUSH;
import static org.jumpmind.symmetric.service.ClusterConstants.PULL;
import static org.jumpmind.symmetric.service.ClusterConstants.PURGE_DATA_GAPS;
import static org.jumpmind.symmetric.service.ClusterConstants.PURGE_INCOMING;
import static org.jumpmind.symmetric.service.ClusterConstants.PURGE_OUTGOING;
import static org.jumpmind.symmetric.service.ClusterConstants.PURGE_STATISTICS;
import static org.jumpmind.symmetric.service.ClusterConstants.PUSH;
import static org.jumpmind.symmetric.service.ClusterConstants.ROUTE;
import static org.jumpmind.symmetric.service.ClusterConstants.STAGE_MANAGEMENT;
import static org.jumpmind.symmetric.service.ClusterConstants.STATISTICS;
import static org.jumpmind.symmetric.service.ClusterConstants.SYNC_CONFIG;
import static org.jumpmind.symmetric.service.ClusterConstants.SYNC_TRIGGERS;
import static org.jumpmind.symmetric.service.ClusterConstants.TYPE_CLUSTER;
import static org.jumpmind.symmetric.service.ClusterConstants.TYPE_EXCLUSIVE;
import static org.jumpmind.symmetric.service.ClusterConstants.TYPE_SHARED;
import static org.jumpmind.symmetric.service.ClusterConstants.WATCHDOG;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Lock;
import org.jumpmind.symmetric.model.NodeHost;
import org.jumpmind.symmetric.cache.ClusteredCacheManager;
import org.jumpmind.symmetric.cache.IClusteredCacheManager;
import org.jumpmind.symmetric.service.IClusterInstanceGenerator;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @see IClusterService
 */
public class ClusterService extends AbstractService implements IClusterService {
    protected static final String[] actions = new String[] { ROUTE, PULL, PUSH, HEARTBEAT, PURGE_INCOMING, PURGE_OUTGOING,
            PURGE_STATISTICS, SYNC_TRIGGERS, PURGE_DATA_GAPS, STAGE_MANAGEMENT, WATCHDOG, STATISTICS, FILE_SYNC_PULL,
            FILE_SYNC_PUSH, FILE_SYNC_TRACKER, INITIAL_LOAD_EXTRACT, INITIAL_LOAD_QUEUE, OFFLINE_PUSH, OFFLINE_PULL,
            MONITOR, SYNC_CONFIG, LOG_MINER, COMPARE, DATA_REFRESH };
    protected static final String[] sharedActions = new String[] { FILE_SYNC_SHARED };
    protected static final Logger log = LoggerFactory.getLogger(ClusterService.class);
    protected String serverId = null;
    protected static String instanceId = null;
    protected INodeService nodeService;
    protected IExtensionService extensionService;
    protected Map<String, Lock> lockCache = new ConcurrentHashMap<String, Lock>();

    public ClusterService(IParameterService parameterService, ISymmetricDialect dialect, INodeService nodeService,
            IExtensionService extensionService) {
        super(parameterService, dialect);
        this.nodeService = nodeService;
        this.extensionService = extensionService;
        setSqlMap(new ClusterServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
        initCache();
    }

    @Override
    public void init() {
        if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) && !isClusteringEnabled()) {
            log.warn("Cluster lock is only available in SymmetricDS Pro.  Remove {} from engine properties or install SymmetricDS PRO license.",
                    ParameterConstants.CLUSTER_LOCKING_ENABLED);
        }
        initInstanceId();
        checkSymDbOwnership();
        initializeAllLocks();
        loadLocksFromDatabase();
    }

    @Override
    public void refreshLockEntries() {
    }

    protected void initInstanceId() {
        if (instanceId == null) {
            synchronized (ClusterService.class) {
                IClusterInstanceGenerator generator = extensionService != null
                        ? extensionService.getExtensionPoint(IClusterInstanceGenerator.class)
                        : null;
                initInstanceId(generator);
            }
        }
    }

    public static String initInstanceId(IClusterInstanceGenerator generator) {
        if (instanceId != null) {
            return instanceId;
        }
        File defaultFile = new File(AppUtils.getSymHome() + "/conf/instance.uuid");
        File instanceIdFile = null;
        URL instanceIdURL = null;
        if ("true".equals(System.getProperty(SystemConstants.SYSPROP_LAUNCHER))) {
            instanceIdFile = defaultFile;
        } else {
            instanceIdURL = ClusterService.class.getClassLoader().getResource("/instance.uuid");
        }
        if (instanceIdFile != null) {
            try {
                instanceId = IOUtils.toString(new FileInputStream(instanceIdFile), Charset.defaultCharset()).trim();
            } catch (Exception ex) {
                log.debug("Failed to load instance id from file '" + instanceIdFile + "'", ex);
            }
        } else if (instanceIdURL != null) {
            try {
                instanceId = IOUtils.toString(instanceIdURL.openStream(), Charset.defaultCharset()).trim();
            } catch (Exception ex) {
                log.debug("Failed to load instance id from classpath '" + instanceIdURL + "'", ex);
            }
        }
        boolean isInstanceIdBlank = StringUtils.isBlank(instanceId);
        if (isInstanceIdBlank || (generator != null && !generator.isValid(instanceId))) {
            String newInstanceId = null;
            if (generator != null) {
                newInstanceId = generator.generateInstanceId();
            }
            if (newInstanceId == null) {
                newInstanceId = generateInstanceId(AppUtils.getHostName());
            }
            if (isInstanceIdBlank) {
                log.info("Generated a new instance ID ({}) because the current instance ID is missing or blank", newInstanceId);
            } else {
                log.info("Generated a new instance ID ({}) because the current instance ID ({}) is invalid", newInstanceId, instanceId);
            }
            instanceId = newInstanceId;
            if (instanceIdFile != null) {
                try {
                    instanceIdFile.getParentFile().mkdirs();
                    IOUtils.write(newInstanceId, new FileOutputStream(instanceIdFile), Charset.defaultCharset());
                } catch (Exception ex) {
                    throw new SymmetricException("Failed to save file '" + instanceIdFile + "' Please correct and restart this node.", ex);
                }
            }
        }
        return instanceId;
    }

    protected void checkSymDbOwnership() {
        List<NodeHost> nodeHosts = nodeService.findNodeHosts(nodeService.findIdentityNodeId());
        long staleThresholdMs = parameterService.getLong(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS);
        for (NodeHost nodeHost : nodeHosts) {
            if (nodeHost.getInstanceId() != null && !Strings.CS.equals(instanceId, nodeHost.getInstanceId())) {
                if (isOwnerStale(nodeHost, staleThresholdMs)) {
                    log.warn("Reclaiming ownership of node '{}' from instance id '{}' on host '{}' because its last heartbeat ({}) is older than "
                            + "cluster.peer.obsolete.ms={}, indicating that instance has crashed or been replaced.",
                            nodeService.findIdentityNodeId(), nodeHost.getInstanceId(), nodeHost.getHostName(), nodeHost.getHeartbeatTime(),
                            staleThresholdMs);
                    continue;
                }
                String msg = String.format("*** Node '%s' failed to claim exclusive ownership of the SymmetricDS database. *** "
                        + "This is instance id '%s' on host '%s' but instance id '%s' on host '%s' is already present in sym_node_host.  This is caused "
                        + "when 2 copies of SymmetricDS are pointed at the same database, but not clustered.  If you are configuring a cluster, set "
                        + "%s=true and restart.  If you moved your installation or re-installed, run 'delete from sym_node_host where "
                        + "node_id = '%s' and restart SymmetricDS.",
                        nodeService.findIdentityNodeId(), instanceId, getServerId(), nodeHost.getInstanceId(), nodeHost.getHostName(),
                        ParameterConstants.CLUSTER_LOCKING_ENABLED, nodeService.findIdentityNodeId());
                throw new SymmetricException(msg);
            }
        }
    }

    /**
     * A prior owner is presumed crashed rather than merely slow once its sym_node_host row hasn't been refreshed for longer than the heartbeat job could
     * plausibly take, since heartbeat_time is only ever written by that job (see job.heartbeat.period.time.ms).
     */
    private boolean isOwnerStale(NodeHost nodeHost, long staleThresholdMs) {
        return nodeHost.getHeartbeatTime() == null
                || System.currentTimeMillis() - nodeHost.getHeartbeatTime().getTime() > staleThresholdMs;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public synchronized void persistToTableForSnapshot() {
        sqlTemplate.update(getSql("deleteSql"));
        Collection<Lock> values = lockCache.values();
        for (Lock lock : values) {
            insertLock(lock);
        }
    }

    protected void insertLock(Lock lock) {
        sqlTemplate.update(getSql("insertCompleteLockSql"), lock.getLockAction(), lock.getLockType(), lock.getLockingServerId(), lock.getLockTime(), lock
                .getSharedCount(), lock.isSharedEnable() ? 1 : 0, lock.getLastLockTime(), lock.getLastLockingServerId());
    }

    protected final void initCache() {
        lockCache.clear();
        for (String action : actions) {
            Lock lock = new Lock();
            lock.setLockAction(action);
            lock.setLockType(TYPE_CLUSTER);
            lockCache.put(action, lock);
        }
        for (String action : sharedActions) {
            Lock lock = new Lock();
            lock.setLockAction(action);
            lock.setLockType(TYPE_SHARED);
            lockCache.put(action, lock);
        }
    }

    /**
     * Loads locks from the database and merges lastLockTime values into the cache. This loads persisted lock info after restarts.
     */
    protected void loadLocksFromDatabase() {
        for (Lock dbLock : selectLocksFromDatabase()) {
            Lock cachedLock = lockCache.get(dbLock.getLockAction());
            if (cachedLock != null) {
                cachedLock.setLastLockTime(dbLock.getLastLockTime());
                cachedLock.setLastLockingServerId(dbLock.getLastLockingServerId());
            }
        }
    }

    protected List<Lock> selectLocksFromDatabase() {
        try {
            return sqlTemplate.query(getSql("selectLocksSql"), new ISqlRowMapper<Lock>() {
                @Override
                public Lock mapRow(Row row) {
                    Lock lock = new Lock();
                    lock.setLockAction(row.getString("lock_action"));
                    lock.setLockType(row.getString("lock_type"));
                    lock.setLockingServerId(row.getString("locking_server_id"));
                    lock.setLockTime(row.getDateTime("lock_time"));
                    lock.setSharedCount(row.getInt("shared_count"));
                    lock.setSharedEnable(row.getInt("shared_enable") == 1);
                    lock.setLastLockTime(row.getDateTime("last_lock_time"));
                    lock.setLastLockingServerId(row.getString("last_locking_server_id"));
                    return lock;
                }
            });
        } catch (Exception e) {
            log.debug("Could not load locks from database (table may not exist yet): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Persists the lastLockTime for a lock to the database. This is called after unlock to ensure lock info persists across restarts.
     */
    protected void persistLastLockTime(String action, Date lastLockTime, String lastLockingServerId) {
        try {
            // Attempt updating the lock first, since it is the more common case.
            int updated = sqlTemplate.update(getSql("updateLastLockTimeSql"), lastLockTime, lastLockingServerId, action);
            if (updated <= 0) {
                // Row may not exist, or DB didn't return row count - try insert
                try {
                    sqlTemplate.update(getSql("insertCompleteLockSql"), action, TYPE_CLUSTER, null, null, 0, 0, lastLockTime, lastLockingServerId);
                } catch (UniqueKeyException e) {
                    // Row already existed, UPDATE already updated it
                }
            }
        } catch (Exception e) {
            log.debug("Could not persist lastLockTime for action '{}': {}", action, e.getMessage());
        }
    }

    @Override
    public synchronized void addLock(String action, String lockType) {
        Lock lock = new Lock();
        lock.setLockAction(action);
        lock.setLockType(lockType);
        lockCache.put(action, lock);
    }

    @Override
    public synchronized void removeLock(String action) {
        lockCache.remove(action);
    }

    @Override
    public void clearAllLocks() {
        initCache();
    }

    protected void initializeAllLocks() {
        for (Lock lock : lockCache.values()) {
            lock.setLastLockingServerId(lock.getLockingServerId());
            lock.setLockingServerId(null);
            lock.setLastLockTime(lock.getLockTime());
            lock.setLockTime(null);
            lock.setSharedCount(0);
            lock.setSharedEnable(false);
        }
    }

    @Override
    public boolean lock(final String action, final String lockType) {
        if (lockType.equals(TYPE_CLUSTER)) {
            return lock(action);
        } else if (lockType.equals(TYPE_SHARED)) {
            return lockShared(action);
        } else if (lockType.equals(TYPE_EXCLUSIVE)) {
            return lockExclusive(action);
        } else {
            throw new UnsupportedOperationException("Lock type of " + lockType + " is not supported");
        }
    }

    @Override
    public boolean lock(final String action, final String lockType, long waitMillis) {
        if (lockType.equals(TYPE_SHARED) || lockType.equals(TYPE_EXCLUSIVE)) {
            return lockWait(action, lockType, waitMillis);
        } else {
            throw new UnsupportedOperationException("Lock type of " + lockType + " is not supported");
        }
    }

    @Override
    public boolean lock(final String action) {
        final Date timeout = DateUtils.addMilliseconds(new Date(),
                (int) -parameterService.getLong(ParameterConstants.CLUSTER_LOCK_TIMEOUT_MS));
        return lockCluster(action, timeout, new Date(), getServerId());
    }

    protected boolean lockCluster(String action, Date timeToBreakLock, Date timeLockAcquired,
            String argServerId) {
        Lock lock = lockCache.get(action);
        if (lock != null) {
            synchronized (lock) {
                boolean isClusterLock = lock.getLockType().equals(TYPE_CLUSTER);
                boolean isOwnLock = argServerId.equals(lock.getLockingServerId());
                boolean isStealable = isLockExpiredOrServerStale(lock.getLockingServerId(), lock.getLockTime(), timeToBreakLock);
                if (isClusterLock && (isOwnLock || isStealable)) {
                    lock.setLockingServerId(argServerId);
                    lock.setLockTime(timeLockAcquired);
                    return true;
                }
            }
        }
        return false;
    }

    protected void updateCacheLockTime(String action, Date timeLockAcquired) {
        Lock lock = lockCache.get(action);
        if (lock != null) {
            synchronized (lock) {
                lock.setLockTime(timeLockAcquired);
            }
        }
    }

    protected boolean lockShared(final String action) {
        Lock lock = lockCache.get(action);
        if (lock != null) {
            synchronized (lock) {
                final Date timeout = DateUtils.addMilliseconds(new Date(),
                        (int) -parameterService.getLong(ParameterConstants.LOCK_TIMEOUT_MS));
                if ((lock.getLockType().equals(TYPE_SHARED) || lock.getLockTime() == null || lock.getLockTime().before(timeout))
                        && (lock.isSharedEnable() || lock.getSharedCount() == 0)) {
                    lock.setLockType(TYPE_SHARED);
                    lock.setLockingServerId(getServerId());
                    lock.setLockTime(new Date());
                    if (lock.getSharedCount() == 0) {
                        lock.setSharedEnable(true);
                    }
                    lock.setSharedCount(lock.getSharedCount() + 1);
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean lockExclusive(final String action) {
        Lock lock = lockCache.get(action);
        if (lock != null) {
            synchronized (lock) {
                final Date timeout = DateUtils.addMilliseconds(new Date(),
                        (int) -parameterService.getLong(ParameterConstants.LOCK_TIMEOUT_MS));
                if ((lock.getLockType().equals(TYPE_SHARED) && lock.getSharedCount() == 0) ||
                        lock.getLockTime() == null || lock.getLockTime().before(timeout)) {
                    lock.setLockType(TYPE_EXCLUSIVE);
                    lock.setLockingServerId(getServerId());
                    lock.setLockTime(new Date());
                    lock.setSharedCount(0);
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean lockWait(final String action, final String lockType, long waitMillis) {
        boolean isLocked = false;
        long endTime = System.currentTimeMillis() + waitMillis;
        long sleepMillis = parameterService.getLong(ParameterConstants.LOCK_WAIT_RETRY_MILLIS);
        do {
            if (lockType.equals(TYPE_SHARED)) {
                isLocked = lockShared(action);
            } else if (lockType.equals(TYPE_EXCLUSIVE)) {
                isLocked = lockExclusive(action);
                if (!isLocked) {
                    disableSharedLock(action);
                }
            }
            if (isLocked) {
                break;
            }
            AppUtils.sleep(sleepMillis);
        } while (waitMillis == 0 || System.currentTimeMillis() < endTime);
        return isLocked;
    }

    protected void disableSharedLock(final String action) {
        Lock lock = lockCache.get(action);
        if (lock != null) {
            synchronized (lock) {
                lock.setSharedEnable(false);
            }
        }
    }

    @Override
    public Map<String, Lock> findLocks() {
        return lockCache;
    }

    /**
     * The instance id is similar in intent to the serverId, but it is generated by the system on initial startup, and semi-permanently cached as a file on the
     * local file system. The intension is to uniquely identity SymmetricDS installations, and protect against situations where things are misconfigured and
     * potentially pointed at the wrong databases, or pointed at the same database without the cluster.lock.enabled parameter turned on. Also used to discover
     * peers in the JCS cluster, since the instance id is shared across a cluster. The serverId is a runtime identifier of the JVM instance, and is used for
     * locking and monitoring purposes.
     */
    protected static String generateInstanceId(String hostName) {
        final int MAX_HOST_LENGTH = 23;
        StringBuilder buff = new StringBuilder();
        buff.append(hostName);
        if (buff.length() > MAX_HOST_LENGTH) {
            buff.setLength(MAX_HOST_LENGTH);
        }
        buff.append("-");
        buff.append(UUID.randomUUID().toString());
        return buff.toString();
    }

    /**
     * Get a unique identifier that represents the JVM instance this server is currently running in.
     */
    @Override
    public String getServerId() {
        if (StringUtils.isBlank(serverId)) {
            serverId = parameterService.getString(ServerConstants.CLUSTER_SERVER_ID);
            if (StringUtils.isBlank(serverId)) {
                // JBoss uses this system property to identify a server in a
                // cluster
                serverId = System.getProperty("bind.address", null);
            }
            if (StringUtils.isBlank(serverId)) {
                // JBoss uses this system property to identify a server in a
                // cluster
                serverId = System.getProperty("jboss.bind.address", null);
            }
            if (StringUtils.isBlank(serverId)) {
                try {
                    serverId = AppUtils.getHostName();
                } catch (Exception ex) {
                    serverId = "unknown";
                }
            }
            serverId = StringUtils.left(serverId, 255);
            log.info("This node picked a server id of {}", serverId);
        }
        return serverId;
    }

    @Override
    public void unlock(final String action, final String lockType) {
        if (lockType.equals(TYPE_CLUSTER)) {
            unlock(action);
        } else if (lockType.equals(TYPE_SHARED)) {
            unlockShared(action);
        } else if (lockType.equals(TYPE_EXCLUSIVE)) {
            unlockExclusive(action);
        } else {
            throw new UnsupportedOperationException("Lock type of " + lockType + " is not supported");
        }
    }

    @Override
    public void unlock(final String action) {
        if (!unlockCluster(action, getServerId())) {
            log.warn("Failed to release lock for action:{} server:{}", action, getServerId());
        }
    }

    protected boolean unlockCluster(String action, String argServerId) {
        Lock lock = lockCache.get(action);
        if (lock != null) {
            synchronized (lock) {
                if (lock.getLockType().equals(TYPE_CLUSTER) && argServerId.equals(lock.getLockingServerId())) {
                    lock.setLastLockingServerId(lock.getLockingServerId());
                    lock.setLockingServerId(null);
                    lock.setLastLockTime(lock.getLockTime());
                    lock.setLockTime(null);
                    // Persist lastLockTime to database so it survives restarts
                    persistLastLockTime(action, lock.getLastLockTime(), lock.getLastLockingServerId());
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean unlockShared(final String action) {
        Lock lock = lockCache.get(action);
        if (lock != null) {
            synchronized (lock) {
                if (lock.getLockType().equals(TYPE_SHARED)) {
                    lock.setLastLockTime(lock.getLockTime());
                    lock.setLastLockingServerId(lock.getLockingServerId());
                    if (lock.getSharedCount() == 1) {
                        lock.setSharedEnable(false);
                        lock.setLockingServerId(null);
                        lock.setLockTime(null);
                    }
                    if (lock.getSharedCount() > 1) {
                        lock.setSharedCount(lock.getSharedCount() - 1);
                    } else {
                        lock.setSharedCount(0);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean unlockExclusive(final String action) {
        Lock lock = lockCache.get(action);
        if (lock != null) {
            synchronized (lock) {
                if (lock.getLockType().equals(TYPE_EXCLUSIVE)) {
                    lock.setLastLockingServerId(lock.getLockingServerId());
                    lock.setLockingServerId(null);
                    lock.setLastLockTime(lock.getLockTime());
                    lock.setLockTime(null);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isLocked(String action) {
        Map<String, Lock> locks = findLocks();
        Lock lock = locks.get(action);
        return lock != null && lock.getLockTime() != null;
    }

    @Override
    public boolean isInfiniteLocked(String action) {
        Map<String, Lock> locks = findLocks();
        Lock lock = locks.get(action);
        if (lock != null && lock.getLockTime() != null && new Date().before(lock.getLockTime())
                && Lock.STOPPED.equals(lock.getLockingServerId())) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void aquireInfiniteLock(String action) {
    }

    @Override
    public void clearInfiniteLock(String action) {
        Map<String, Lock> all = findLocks();
        Lock lock = all.get(action);
        if (lock != null && Lock.STOPPED.equals(lock.getLockingServerId())) {
            if (lock.getLockType().equals(TYPE_CLUSTER) && Lock.STOPPED.equals(lock.getLockingServerId())) {
                lock.setLastLockingServerId(null);
                lock.setLockingServerId(null);
                lock.setLastLockTime(null);
                lock.setLockTime(null);
            }
        }
    }

    @Override
    public boolean refreshLock(String action) {
        return true;
    }

    @Override
    public boolean isClusteringEnabled() {
        return false;
    }

    @Override
    public void clearLocksForServer(String serverId) {
        for (Lock lock : lockCache.values()) {
            synchronized (lock) {
                if (serverId.equals(lock.getLockingServerId())) {
                    lock.setLockingServerId(null);
                    lock.setLockTime(null);
                    if (TYPE_SHARED.equals(lock.getLockType())) {
                        lock.setSharedCount(0);
                        lock.setSharedEnable(false);
                    }
                }
            }
        }
        try {
            sqlTemplate.update(getSql("clearLocksForServerSql"), serverId);
        } catch (Exception e) {
            log.debug("Could not clear sym_lock rows for server '{}': {}", serverId, e.getMessage());
        }
        log.info("Cleared cluster locks held by server '{}'", serverId);
    }

    @Override
    public boolean isStaleServer(String lockingServerId) {
        if (lockingServerId == null || getServerId().equals(lockingServerId)) {
            return false;
        }
        IClusteredCacheManager cacheManager = ClusteredCacheManager.getInstance();
        Set<String> active = cacheManager.getActiveServerIds();
        if (!active.isEmpty()) {
            return !active.contains(lockingServerId);
        }
        long staleThresholdMs = parameterService.getLong(ParameterConstants.CLUSTER_PEER_STALE_MS,
                ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS);
        return cacheManager.isPeerOfflineLongEnough(lockingServerId, staleThresholdMs);
    }

    @Override
    public void removeObsoleteNodeHosts() {
        String nodeHostTableName = TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST);
        String nodeId = nodeService.findIdentityNodeId();
        if (nodeId == null) {
            return;
        }
        long obsoleteThresholdMs = parameterService.getLong(ParameterConstants.CLUSTER_PEER_OBSOLETE_MS);
        List<NodeHost> nodeHosts = nodeService.findNodeHosts(nodeId);
        if (nodeHosts != null) {
            IClusteredCacheManager cacheManager = ClusteredCacheManager.getInstance();
            for (NodeHost nodeHost : nodeHosts) {
                if (nodeHost != null && isOwnerStale(nodeHost, obsoleteThresholdMs)) {
                    String hostName = nodeHost.getHostName();
                    log.info("Purging obsolete {} row for node '{}' host '{}' and clearing any locks it still holds",
                            nodeHostTableName, nodeId, hostName);
                    clearLocksForServer(hostName);
                    if (cacheManager != null && hostName != null) {
                        cacheManager.removePeer(hostName);
                    }
                }
            }
        }
    }

    protected boolean isLockExpiredOrServerStale(String lockingServerId, Date lockTime, Date lockTimeout) {
        if (lockTime == null || lockTime.before(lockTimeout)) {
            if (lockingServerId != null) {
                if (getServerId().equals(lockingServerId)) {
                    log.debug("Resetting own cluster lock due to expiry, serverId='{}', lockTime={}", lockingServerId, lockTime);
                } else {
                    log.warn("Breaking cluster lock from '{}' due to expiry, lockTime={}", lockingServerId, lockTime);
                }
            }
            return true;
        }
        if (lockingServerId != null && !getServerId().equals(lockingServerId)) {
            boolean isNew = ClusteredCacheManager.getInstance().recordPeerOffline(lockingServerId);
            if (isNew) {
                long staleThresholdMs = parameterService.getLong(ParameterConstants.CLUSTER_PEER_STALE_MS,
                        ServerConstants.CLUSTER_PEER_STALE_DEFAULT_MS);
                log.warn("Cluster lock owner '{}' was not detected via JCS peer heartbeat. Starting offline timer — "
                        + "lock will be eligible for breaking in {} ms if peer does not reconnect, lockTime={}",
                        lockingServerId, staleThresholdMs, lockTime);
            }
        }
        if (isStaleServer(lockingServerId)) {
            log.warn("Breaking cluster lock from stale cluster peer '{}', lockTime={}", lockingServerId, lockTime);
            return true;
        }
        return false;
    }
}
