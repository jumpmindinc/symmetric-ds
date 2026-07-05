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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.ext.IReloadQueueThreadAssigner;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.DatabaseParameter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.RandomTimeSlot;
import org.slf4j.MDC;

public class NodeCommunicationService extends AbstractService implements INodeCommunicationService {
    private Map<CommunicationType, ThreadPoolExecutor> executors = new HashMap<NodeCommunication.CommunicationType, ThreadPoolExecutor>();
    private INodeService nodeService;
    private IClusterService clusterService;
    private IConfigurationService configurationService;
    private IExtensionService extensionService;
    private boolean initialized = false;
    private Map<CommunicationType, Set<String>> currentlyExecuting;
    private Map<CommunicationType, Map<String, NodeCommunication>> lockCache;
    private volatile long lastHeartbeatTouchMs;

    public NodeCommunicationService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        setSqlMap(new NodeCommunicationServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
        clusterService = engine.getClusterService();
        nodeService = engine.getNodeService();
        configurationService = engine.getConfigurationService();
        extensionService = engine.getExtensionService();
        currentlyExecuting = new HashMap<NodeCommunication.CommunicationType, Set<String>>();
        CommunicationType[] types = CommunicationType.values();
        for (CommunicationType communicationType : types) {
            currentlyExecuting.put(communicationType, Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>()));
        }
        lockCache = new HashMap<CommunicationType, Map<String, NodeCommunication>>();
        for (CommunicationType type : types) {
            lockCache.put(type, new HashMap<String, NodeCommunication>());
        }
    }

    private synchronized final void initialize() {
        if (!initialized) {
            if (clusterService.isClusteringEnabled()) {
                try {
                    int locksCleared = sqlTemplate.update(getSql("clearLocksOnRestartSql"),
                            clusterService.getServerId());
                    if (locksCleared > 0) {
                        log.info("Cleared {} node communication locks for {}", locksCleared,
                                clusterService.getServerId());
                    }
                } finally {
                    initialized = true;
                }
            } else {
                initialized = true;
            }
        }
    }

    @Override
    public synchronized void persistToTableForSnapshot() {
        sqlTemplate.update(getSql("deleteSql"));
        Collection<Map<String, NodeCommunication>> values = lockCache.values();
        for (Map<String, NodeCommunication> map : values) {
            Collection<NodeCommunication> nodeCommies = map.values();
            for (NodeCommunication nodeCommunication : nodeCommies) {
                save(nodeCommunication, true);
            }
        }
    }

    public NodeCommunication find(String nodeId, String queue, CommunicationType communicationType) {
        NodeCommunication lock = null;
        if (clusterService.isClusteringEnabled()) {
            lock = sqlTemplate.queryForObject(
                    getSql("selectNodeCommunicationByNodeAndChannelSql"), new NodeCommunicationMapper(),
                    nodeId, queue, communicationType.name());
        } else {
            Map<String, NodeCommunication> locks = lockCache.get(communicationType);
            lock = locks.get(nodeId + "-" + queue);
        }
        if (lock == null) {
            lock = new NodeCommunication();
            lock.setNodeId(nodeId);
            lock.setCommunicationType(communicationType);
            lock.setQueue(queue);
            save(lock, false);
        }
        return lock;
    }

    protected List<NodeCommunication> find(CommunicationType communicationType) {
        if (clusterService.isClusteringEnabled()) {
            String SQL_KEY = CommunicationType.isPullType(communicationType) ? "selectNodeCommunicationPullSql" : "selectNodeCommunicationSql";
            return new ArrayList<NodeCommunication>(sqlTemplate.query(getSql(SQL_KEY),
                    new NodeCommunicationMapper(), communicationType.name()));
        } else {
            Map<String, NodeCommunication> locks = lockCache.get(communicationType);
            List<NodeCommunication> list = new ArrayList<NodeCommunication>(locks.values());
            sortNodeCommunications(list, communicationType);
            return list;
        }
    }

    public List<NodeCommunication> list(CommunicationType communicationType) {
        return list(communicationType, true);
    }

    public List<NodeCommunication> listAll(CommunicationType communicationType) {
        return list(communicationType, false);
    }

    protected List<NodeCommunication> list(CommunicationType communicationType, boolean onlyNodesWithChanges) {
        initialize();
        long ts = System.currentTimeMillis();
        List<NodeCommunication> communicationRows = find(communicationType);
        log.debug("Found {} node communication locks to push to in {}ms", communicationRows.size(), System.currentTimeMillis() - ts);
        List<Node> nodesToCommunicateWith = null;
        switch (communicationType) {
            case PULL:
            case FILE_PULL:
                List<Node> nodes = nodeService.findNodesToPull();
                nodesToCommunicateWith = removeOfflineNodes(nodes);
                break;
            case FILE_PUSH:
            case PUSH:
                ts = System.currentTimeMillis();
                nodes = nodeService.findNodesToPushTo();
                nodesToCommunicateWith = removeOfflineNodes(nodes);
                log.debug("Found {} nodes to push to in {}ms", nodesToCommunicateWith.size(), System.currentTimeMillis() - ts);
                break;
            case OFFLN_PUSH:
            case OFF_FSPUSH:
                nodesToCommunicateWith = getNodesToCommunicateWithOffline(CommunicationType.PUSH);
                break;
            case OFFLN_PULL:
            case OFF_FSPULL:
                nodesToCommunicateWith = getNodesToCommunicateWithOffline(CommunicationType.PULL);
                break;
            default:
                nodesToCommunicateWith = new ArrayList<Node>(0);
                break;
        }
        Map<String, NodeCommunication> communicationRowsMap = new HashMap<String, NodeCommunication>(communicationRows.size());
        for (NodeCommunication nodeCommunication : communicationRows) {
            communicationRowsMap.put(nodeCommunication.getIdentifier(), nodeCommunication);
        }
        IReloadQueueThreadAssigner extension = extensionService.getExtensionPoint(IReloadQueueThreadAssigner.class);
        List<NodeCommunication> nodesToCommunicateWithList = filterForChannelThreading(nodesToCommunicateWith, communicationType, extension);
        Map<String, NodeCommunication> nodesToCommunicateWithListMap = new HashMap<String, NodeCommunication>(nodesToCommunicateWithList.size());
        for (NodeCommunication nodeToCommunicateWith : nodesToCommunicateWithList) {
            NodeCommunication comm = communicationRowsMap.get(nodeToCommunicateWith.getIdentifier());
            if (comm == null) {
                comm = new NodeCommunication();
                comm.setNodeId(nodeToCommunicateWith.getNodeId());
                comm.setQueue(nodeToCommunicateWith.getQueue());
                comm.setCommunicationType(communicationType);
                save(comm, false);
                communicationRows.add(comm);
            }
            comm.setNode(nodeToCommunicateWith.getNode());
            nodesToCommunicateWithListMap.put(nodeToCommunicateWith.getNodeId(), nodeToCommunicateWith);
        }
        removeInvalidQueues(communicationType, communicationRows, nodesToCommunicateWithListMap, extension);
        if (communicationType == CommunicationType.PUSH && onlyNodesWithChanges &&
                parameterService.getInt(ParameterConstants.PUSH_THREAD_COUNT_PER_SERVER) < communicationRows.size()) {
            ts = System.currentTimeMillis();
            List<String> nodeIds = getNodeIdsWithUnsentCount();
            List<NodeCommunication> filteredNodes = new ArrayList<NodeCommunication>(nodeIds.size());
            for (NodeCommunication nodeCommunication : communicationRows) {
                if (nodeIds.contains(nodeCommunication.getNodeId())) {
                    filteredNodes.add(nodeCommunication);
                }
            }
            log.debug("Filtered down to {} nodes to push to in {}ms", filteredNodes.size(), System.currentTimeMillis() - ts);
            communicationRows = filteredNodes;
        }
        if (communicationType == CommunicationType.PULL || communicationType == CommunicationType.FILE_PULL) {
            communicationRows = removeNodesWithNoBatchesToSend(communicationRows);
        }
        return communicationRows;
    }

    protected void removeInvalidQueues(CommunicationType communicationType, List<NodeCommunication> communicationRows,
            Map<String, NodeCommunication> nodesToCommunicateWithListMap, IReloadQueueThreadAssigner extension) {
        Map<String, Channel> channels = configurationService.getChannels(false);
        HashSet<String> queues = new HashSet<String>();
        for (Channel channel : channels.values()) {
            queues.add(channel.getQueue());
        }
        if (extension != null) {
            extension.addDynamicQueues(queues, communicationType);
        }
        Iterator<NodeCommunication> it = communicationRows.iterator();
        while (it.hasNext()) {
            NodeCommunication nodeCommunication = it.next();
            NodeCommunication nodeToCommunicateWith = nodesToCommunicateWithListMap.get(nodeCommunication.getNodeId());
            Node node = nodeToCommunicateWith != null ? nodeToCommunicateWith.getNode() : null;
            if (node == null || (!queues.contains(nodeCommunication.getQueue()))) {
                delete(nodeCommunication);
                it.remove();
            }
        }
    }

    protected List<String> getNodeIdsWithUnsentCount() {
        return sqlTemplate.query(getSql("selectNodeIdsWithUnsentBatchsSql"),
                new StringMapper(), OutgoingBatch.Status.ER.name(), OutgoingBatch.Status.NE.name(), OutgoingBatch.Status.QY.name(),
                OutgoingBatch.Status.SE.name(), OutgoingBatch.Status.LD.name(), OutgoingBatch.Status.IG.name(), OutgoingBatch.Status.RS.name());
    }

    protected List<NodeCommunication> filterForChannelThreading(List<Node> nodesToCommunicateWith, CommunicationType communicationType,
            IReloadQueueThreadAssigner extension) {
        List<NodeCommunication> nodeCommunications = new ArrayList<NodeCommunication>();
        Collection<Channel> channels = configurationService.getChannels(false).values();
        HashSet<String> queues = new HashSet<String>();
        for (Channel channel : channels) {
            queues.add(channel.getQueue());
        }
        if (extension != null) {
            extension.addDynamicQueues(queues, communicationType);
        }
        for (Node node : nodesToCommunicateWith) {
            if (node.isVersionGreaterThanOrEqualTo(3, 8, 0)) {
                multiplyNodeCommunicationByQueues(node, nodeCommunications, queues);
            } else {
                NodeCommunication nodeCommunication = new NodeCommunication();
                nodeCommunication.setNodeId(node.getNodeId());
                nodeCommunication.setNode(node);
                nodeCommunications.add(nodeCommunication);
            }
        }
        return nodeCommunications;
    }

    protected void multiplyNodeCommunicationByQueues(Node node, List<NodeCommunication> nodeCommunications, HashSet<String> queues) {
        for (String queue : queues) {
            NodeCommunication nodeCommunication = new NodeCommunication();
            nodeCommunication.setNodeId(node.getNodeId());
            nodeCommunication.setQueue(queue);
            nodeCommunication.setNode(node);
            nodeCommunications.add(nodeCommunication);
        }
    }

    protected List<Node> removeOfflineNodes(List<Node> nodes) {
        if (parameterService.is(ParameterConstants.NODE_OFFLINE)) {
            nodes.clear();
        } else {
            List<DatabaseParameter> parms = parameterService.getOfflineNodeParameters();
            for (DatabaseParameter parm : parms) {
                Iterator<Node> iter = nodes.iterator();
                while (iter.hasNext()) {
                    Node node = iter.next();
                    if ((parm.getNodeGroupId().equals(ParameterConstants.ALL) || parm.getNodeGroupId().equals(node.getNodeGroupId()) &&
                            (parm.getExternalId().equals(ParameterConstants.ALL) || parm.getExternalId().equals(node.getExternalId())))) {
                        if (parm.getValue() != null &&
                                Boolean.valueOf(parm.getValue())) {
                            iter.remove();
                        }
                    }
                }
            }
        }
        return nodes;
    }

    protected List<Node> getNodesToCommunicateWithOffline(CommunicationType communicationType) {
        List<Node> nodesToCommunicateWith = new ArrayList<Node>();
        if (parameterService.is(ParameterConstants.NODE_OFFLINE) ||
                (communicationType.equals(CommunicationType.PULL) && parameterService.is(ParameterConstants.NODE_OFFLINE_INCOMING_ACCEPT_ALL))) {
            if (communicationType.equals(CommunicationType.PUSH)) {
                nodesToCommunicateWith.addAll(nodeService.findTargetNodesFor(NodeGroupLinkAction.W));
                nodesToCommunicateWith.addAll(nodeService.findNodesToPushTo());
            } else if (communicationType.equals(CommunicationType.PULL)) {
                nodesToCommunicateWith.addAll(nodeService.findSourceNodesFor(NodeGroupLinkAction.P));
                nodesToCommunicateWith.addAll(nodeService.findNodesToPull());
            }
        } else {
            List<DatabaseParameter> parms = parameterService.getOfflineNodeParameters();
            if (parms.size() > 0) {
                List<Node> sourceNodes = new ArrayList<Node>();
                if (communicationType.equals(CommunicationType.PUSH)) {
                    sourceNodes.addAll(nodeService.findTargetNodesFor(NodeGroupLinkAction.W));
                    sourceNodes.addAll(nodeService.findNodesToPushTo());
                } else if (communicationType.equals(CommunicationType.PULL)) {
                    sourceNodes.addAll(nodeService.findSourceNodesFor(NodeGroupLinkAction.P));
                    sourceNodes.addAll(nodeService.findNodesToPull());
                }
                if (sourceNodes != null && sourceNodes.size() > 0) {
                    for (DatabaseParameter parm : parms) {
                        for (Node node : sourceNodes) {
                            if ((parm.getNodeGroupId().equals(ParameterConstants.ALL) || parm.getNodeGroupId().equals(node.getNodeGroupId()) &&
                                    (parm.getExternalId().equals(ParameterConstants.ALL) || parm.getExternalId().equals(node.getExternalId())))) {
                                nodesToCommunicateWith.add(node);
                            }
                        }
                    }
                }
            }
        }
        return nodesToCommunicateWith;
    }

    public boolean delete(NodeCommunication nodeCommunication) {
        if (clusterService.isClusteringEnabled()) {
            return 1 == sqlTemplate.update(getSql("deleteNodeCommunicationSql"),
                    nodeCommunication.getNodeId(), nodeCommunication.getQueue(), nodeCommunication.getCommunicationType().name());
        } else {
            Map<String, NodeCommunication> locks = lockCache.get(nodeCommunication.getCommunicationType());
            return locks.remove(nodeCommunication.getIdentifier()) != null;
        }
    }

    protected void save(NodeCommunication nodeCommunication, boolean force) {
        if (clusterService.isClusteringEnabled() || force) {
            if (0 >= sqlTemplate.update(getSql("updateNodeCommunicationSql"),
                    nodeCommunication.getLockTime(), nodeCommunication.getLockingServerId(),
                    nodeCommunication.getLastLockMillis(), nodeCommunication.getSuccessCount(),
                    nodeCommunication.getFailCount(), nodeCommunication.getTotalSuccessCount(),
                    nodeCommunication.getTotalFailCount(), nodeCommunication.getTotalSuccessMillis(),
                    nodeCommunication.getTotalFailMillis(), nodeCommunication.getLastLockTime(),
                    nodeCommunication.getBatchToSendCount(), nodeCommunication.getNodePriority(),
                    nodeCommunication.getNodeId(), nodeCommunication.getQueue(),
                    nodeCommunication.getCommunicationType().name())) {
                sqlTemplate.update(getSql("insertNodeCommunicationSql"),
                        nodeCommunication.getLockTime(), nodeCommunication.getLockingServerId(),
                        nodeCommunication.getLastLockMillis(), nodeCommunication.getSuccessCount(),
                        nodeCommunication.getFailCount(), nodeCommunication.getTotalSuccessCount(),
                        nodeCommunication.getTotalFailCount(),
                        nodeCommunication.getTotalSuccessMillis(),
                        nodeCommunication.getTotalFailMillis(), nodeCommunication.getLastLockTime(),
                        nodeCommunication.getBatchToSendCount(), nodeCommunication.getNodePriority(),
                        nodeCommunication.getNodeId(), nodeCommunication.getQueue(),
                        nodeCommunication.getCommunicationType().name());
            }
        } else {
            Map<String, NodeCommunication> locks = lockCache.get(nodeCommunication.getCommunicationType());
            locks.put(nodeCommunication.getIdentifier(), nodeCommunication);
        }
    }

    protected List<NodeCommunication> removeNodesWithNoBatchesToSend(List<NodeCommunication> nodeCommunications) {
        if (!this.parameterService.is(ParameterConstants.HYBRID_PUSH_PULL_ENABLED)) {
            return nodeCommunications;
        }
        List<NodeCommunication> filteredNodes = new ArrayList<NodeCommunication>(nodeCommunications);
        for (NodeCommunication nodeCommunication : nodeCommunications) {
            long elapsedLock = System.currentTimeMillis() - nodeCommunication.getLastLockMillis();
            if (nodeCommunication.getBatchToSendCount() == 0 && elapsedLock < this.parameterService.getLong(ParameterConstants.HYBRID_PUSH_PULL_TIMEOUT)) {
                filteredNodes.remove(nodeCommunication);
            }
        }
        return filteredNodes;
    }

    protected ThreadPoolExecutor getExecutor(final CommunicationType communicationType) {
        return getExecutor(communicationType, null);
    }

    protected ThreadPoolExecutor getExecutor(final CommunicationType communicationType, final String threadChannelId) {
        ThreadPoolExecutor service = executors.get(communicationType);
        String threadCountParameter = "";
        switch (communicationType) {
            case PULL:
                threadCountParameter = ParameterConstants.PULL_THREAD_COUNT_PER_SERVER;
                break;
            case PUSH:
                threadCountParameter = ParameterConstants.PUSH_THREAD_COUNT_PER_SERVER;
                break;
            case ROUTE:
                threadCountParameter = ParameterConstants.ROUTING_THREAD_COUNT_PER_SERVER;
                break;
            case OFFLN_PULL:
                threadCountParameter = ParameterConstants.OFFLINE_PULL_THREAD_COUNT_PER_SERVER;
                break;
            case OFFLN_PUSH:
                threadCountParameter = ParameterConstants.OFFLINE_PUSH_THREAD_COUNT_PER_SERVER;
                break;
            case FILE_PULL:
            case OFF_FSPULL:
                threadCountParameter = ParameterConstants.FILE_PUSH_THREAD_COUNT_PER_SERVER;
                break;
            case FILE_PUSH:
            case OFF_FSPUSH:
                threadCountParameter = ParameterConstants.FILE_PUSH_THREAD_COUNT_PER_SERVER;
                break;
            case FILE_XTRCT:
            case EXTRACT:
                threadCountParameter = ParameterConstants.INITIAL_LOAD_EXTRACT_THREAD_COUNT_PER_SERVER;
                break;
            case COMPARE:
                threadCountParameter = ParameterConstants.COMPARE_THREAD_PER_SERVER_COUNT;
                break;
            default:
                break;
        }
        int threadCount = parameterService.getInt(threadCountParameter, 1);
        if (service != null && service.getCorePoolSize() != threadCount) {
            synchronized (this) {
                if (service != null) {
                    log.info("{} has changed from {} to {}.  Restarting thread pool for {}", threadCountParameter, service.getCorePoolSize(), threadCount,
                            communicationType.name());
                    service.shutdown();
                    executors.remove(communicationType);
                    service = null;
                }
            }
        }
        if (service == null) {
            synchronized (this) {
                service = executors.get(communicationType);
                if (service == null) {
                    if (threadCount <= 0) {
                        log.warn("{}={} is not a valid value. Defaulting to 1",
                                threadCountParameter, threadCount);
                        threadCount = 1;
                    } else if (threadCount > 1) {
                        log.info("{} will use {} threads", communicationType.name().toLowerCase(),
                                threadCount);
                    }
                    service = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount,
                            new ChannelThreadFactory(parameterService.getEngineName(), communicationType.name()));
                    service.setKeepAliveTime(1, TimeUnit.MINUTES);
                    service.allowCoreThreadTimeOut(true);
                    executors.put(communicationType, service);
                }
            }
        }
        return service;
    }

    public int getAvailableThreads(CommunicationType communicationType) {
        ThreadPoolExecutor service = getExecutor(communicationType);
        return service.getMaximumPoolSize() - service.getActiveCount();
    }

    protected Date getLockTimeoutDate(CommunicationType communicationType) {
        String parameter = "";
        switch (communicationType) {
            case PULL:
                parameter = ParameterConstants.PULL_LOCK_TIMEOUT_MS;
                break;
            case PUSH:
                parameter = ParameterConstants.PUSH_LOCK_TIMEOUT_MS;
                break;
            case ROUTE:
                parameter = ParameterConstants.ROUTING_LOCK_TIMEOUT_MS;
                break;
            case OFFLN_PULL:
                parameter = ParameterConstants.OFFLINE_PULL_LOCK_TIMEOUT_MS;
                break;
            case OFFLN_PUSH:
                parameter = ParameterConstants.OFFLINE_PUSH_LOCK_TIMEOUT_MS;
                break;
            case FILE_PULL:
            case OFF_FSPULL:
                parameter = ParameterConstants.FILE_PULL_LOCK_TIMEOUT_MS;
                break;
            case FILE_PUSH:
            case OFF_FSPUSH:
                parameter = ParameterConstants.FILE_PUSH_LOCK_TIMEOUT_MS;
                break;
            case FILE_XTRCT:
            case EXTRACT:
                parameter = ParameterConstants.INITIAL_LOAD_EXTRACT_TIMEOUT_MS;
                break;
            case COMPARE:
                parameter = ParameterConstants.COMPARE_LOCK_TIMEOUT_MS;
                break;
            default:
                break;
        }
        return DateUtils.addMilliseconds(new Date(),
                -parameterService.getInt(parameter, 7200000));
    }

    public boolean execute(final NodeCommunication nodeCommunication, RemoteNodeStatuses statuses,
            final INodeCommunicationExecutor executor) {
        Date now = new Date();
        final Set<String> executing = this.currentlyExecuting.get(nodeCommunication.getCommunicationType());
        try {
            boolean locked = !executing.contains(nodeCommunication.getIdentifier()) && lock(nodeCommunication, now);
            if (locked) {
                executing.add(nodeCommunication.getIdentifier());
                nodeCommunication.setLastLockTime(now);
                nodeCommunication.setLockingServerId(clusterService.getServerId());
                final RemoteNodeStatus status = statuses.add(nodeCommunication.getNodeId(), nodeCommunication.getQueue());
                Runnable r = new Runnable() {
                    public void run() {
                        long ts = System.currentTimeMillis();
                        boolean failed = false;
                        try {
                            MDC.put("engineName", parameterService.getEngineName());
                            String name = parameterService.getEngineName().toLowerCase() + "-" + nodeCommunication.getCommunicationType().name().toLowerCase() +
                                    "-" + nodeCommunication.getQueue().toLowerCase();
                            Thread thread = Thread.currentThread();
                            thread.setName(thread.getName().replaceFirst(".*(-\\d+)", name + "$1"));
                            executor.execute(nodeCommunication, status);
                            failed = status.failed();
                        } catch (Throwable ex) {
                            failed = true;
                            log.error(String.format("Failed to execute %s for node %s and channel %s", nodeCommunication.getCommunicationType().name(),
                                    nodeCommunication.getNodeId(), nodeCommunication.getQueue()), ex);
                        } finally {
                            status.setComplete(true);
                            executing.remove(nodeCommunication.getIdentifier());
                            unlock(nodeCommunication, failed, ts);
                        }
                    }
                };
                if (parameterService.is(ParameterConstants.SYNCHRONIZE_ALL_JOBS)) {
                    r.run();
                } else {
                    ThreadPoolExecutor service = getExecutor(nodeCommunication.getCommunicationType(),
                            nodeCommunication.getQueue());
                    service.execute(r);
                }
            }
            return locked;
        } catch (RuntimeException ex) {
            log.error(String.format("Failed to execute %s for node %s and channel thread %s", nodeCommunication.getCommunicationType().name(),
                    nodeCommunication.getNodeId(), nodeCommunication.getQueue()), ex);
            executing.remove(nodeCommunication.getIdentifier());
            unlock(nodeCommunication, true, System.currentTimeMillis());
            return false;
        }
    }

    protected boolean lock(NodeCommunication nodeCommunication, Date lockTime) {
        Date lockTimeout = getLockTimeoutDate(nodeCommunication.getCommunicationType());
        String currentOwner = nodeCommunication.getLockingServerId();
        if (clusterService.isClusteringEnabled()) {
            boolean locked = sqlTemplate.update(getSql("aquireLockSql"), clusterService.getServerId(), lockTime, lockTime,
                    nodeCommunication.getNodeId(), nodeCommunication.getQueue(),
                    nodeCommunication.getCommunicationType().name(), lockTimeout) == 1;
            if (!locked && clusterService.isStaleServer(currentOwner)) {
                log.warn("Breaking node communication lock for node '{}' queue '{}' from stale cluster peer '{}', lockTime={}",
                        nodeCommunication.getNodeId(), nodeCommunication.getQueue(), currentOwner, nodeCommunication.getLockTime());
                locked = sqlTemplate.update(getSql("acquireLockFromStaleSql"), clusterService.getServerId(), lockTime, lockTime,
                        nodeCommunication.getNodeId(), nodeCommunication.getQueue(),
                        nodeCommunication.getCommunicationType().name(), currentOwner) == 1;
            }
            if (locked) {
                touchNodeHostHeartbeat();
            }
            return locked;
        } else {
            Date lockTimeVal = nodeCommunication.getLockTime();
            if (lockTimeVal == null || lockTimeVal.before(lockTimeout)) {
                if (lockTimeVal != null && currentOwner != null) {
                    log.warn("Breaking node communication lock for node '{}' queue '{}' from '{}' due to expiry, lockTime={}",
                            nodeCommunication.getNodeId(), nodeCommunication.getQueue(), currentOwner, lockTimeVal);
                }
                nodeCommunication.setLockingServerId(clusterService.getServerId());
                nodeCommunication.setLockTime(lockTime);
                nodeCommunication.setLastLockTime(lockTime);
                return true;
            }
            if (clusterService.isStaleServer(currentOwner)) {
                log.warn("Breaking node communication lock for node '{}' queue '{}' from stale cluster peer '{}', lockTime={}",
                        nodeCommunication.getNodeId(), nodeCommunication.getQueue(), currentOwner, nodeCommunication.getLockTime());
                nodeCommunication.setLockingServerId(clusterService.getServerId());
                nodeCommunication.setLockTime(lockTime);
                nodeCommunication.setLastLockTime(lockTime);
                return true;
            }
            return false;
        }
    }

    /**
     * Keeps this node's SYM_NODE_HOST heartbeat fresh whenever it successfully acquires a node communication lock, so a node that's busy doing push/pull/ route
     * work doesn't go heartbeat-stale and get mistaken for crashed if the heartbeat job is disabled. Throttled to cluster.lock.refresh.ms per service instance
     * rather than per lock, since locks here are keyed by node+queue (potentially many distinct entries) rather than the small fixed set of actions
     * ClusterService's own lock refresh is keyed by.
     */
    private void touchNodeHostHeartbeat() {
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatTouchMs >= parameterService.getLong(ParameterConstants.CLUSTER_LOCK_REFRESH_MS)) {
            lastHeartbeatTouchMs = now;
            nodeService.updateNodeHostForCurrentNode(false);
        }
    }

    protected void unlock(NodeCommunication nodeCommunication,
            boolean failed, long ts) {
        boolean unlocked = false;
        int attempts = 1;
        do {
            try {
                if (nodeCommunication.isDeleteOnUnlock()) {
                    delete(nodeCommunication);
                } else {
                    long millis = System.currentTimeMillis() - ts;
                    nodeCommunication.setLockTime(null);
                    nodeCommunication.setLastLockMillis(millis);
                    if (failed) {
                        nodeCommunication.setFailCount(nodeCommunication.getFailCount() + 1);
                        nodeCommunication.setTotalFailCount(nodeCommunication.getTotalFailCount() + 1);
                        nodeCommunication.setTotalFailMillis(nodeCommunication.getTotalFailMillis() + millis);
                    } else {
                        nodeCommunication.setSuccessCount(nodeCommunication.getSuccessCount() + 1);
                        nodeCommunication.setTotalSuccessCount(nodeCommunication.getTotalSuccessCount() + 1);
                        nodeCommunication.setTotalSuccessMillis(nodeCommunication.getTotalSuccessMillis() + millis);
                        nodeCommunication.setFailCount(0);
                    }
                    if (clusterService.isClusteringEnabled()) {
                        save(nodeCommunication, false);
                    }
                }
                unlocked = true;
                if (attempts > 1) {
                    log.info("Successfully unlocked {} node communication record for {} and channel {} after {} attempts",
                            nodeCommunication.getCommunicationType().name(),
                            nodeCommunication.getNodeId(), nodeCommunication.getQueue(), attempts);
                }
            } catch (Throwable e) {
                log.error(String.format(
                        "Failed to unlock %s node communication record for %s and channel %s",
                        nodeCommunication.getCommunicationType().name(),
                        nodeCommunication.getNodeId(), nodeCommunication.getQueue()), e);
                long sleepTime = DateUtils.MILLIS_PER_SECOND
                        * new RandomTimeSlot(nodeCommunication.getNodeId(), 30).getRandomValueSeededByExternalId();
                log.warn("Sleeping for {} ms before attempting to unlock the node communication record again", sleepTime);
                AppUtils.sleep(sleepTime);
                attempts++;
            }
        } while (!unlocked);
    }

    @Override
    public void clearLocksForServer(String serverId) {
        try {
            int count = sqlTemplate.update(getSql("clearLocksForServerSql"), serverId);
            if (count > 0) {
                log.info("Cleared {} node communication lock(s) held by server '{}'", count, serverId);
            }
        } catch (Exception e) {
            log.debug("Could not clear node communication locks for server '{}': {}", serverId, e.getMessage());
        }
    }

    public void stop() {
        Collection<CommunicationType> services = new HashSet<NodeCommunication.CommunicationType>(
                executors.keySet());
        for (CommunicationType communicationType : services) {
            try {
                ExecutorService service = executors.get(communicationType);
                service.shutdown();
            } finally {
                executors.remove(communicationType);
            }
        }
    }

    static class NodeCommunicationMapper implements ISqlRowMapper<NodeCommunication> {
        public NodeCommunication mapRow(Row rs) {
            NodeCommunication nodeCommunication = new NodeCommunication();
            nodeCommunication.setCommunicationType(CommunicationType.valueOf(rs.getString(
                    "communication_type").toUpperCase()));
            nodeCommunication.setNodeId(rs.getString("node_id"));
            nodeCommunication.setLockTime(rs.getDateTime("lock_time"));
            nodeCommunication.setLastLockMillis(rs.getLong("last_lock_millis"));
            nodeCommunication.setLockingServerId(rs.getString("locking_server_id"));
            nodeCommunication.setSuccessCount(rs.getLong("success_count"));
            nodeCommunication.setTotalSuccessCount(rs.getLong("total_success_count"));
            nodeCommunication.setTotalSuccessMillis(rs.getLong("total_success_millis"));
            nodeCommunication.setFailCount(rs.getLong("fail_count"));
            nodeCommunication.setTotalFailCount(rs.getLong("total_fail_count"));
            nodeCommunication.setTotalFailMillis(rs.getLong("total_fail_millis"));
            nodeCommunication.setLastLockTime(rs.getDateTime("last_lock_time"));
            nodeCommunication.setQueue(rs.getString("queue"));
            nodeCommunication.setBatchToSendCount(rs.getLong("batch_to_send_count"));
            nodeCommunication.setNodePriority(rs.getInt("node_priority"));
            return nodeCommunication;
        }
    }

    static class ChannelThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private String engineName;
        private String communicationType;

        public ChannelThreadFactory(String engineName, String communicationType) {
            this.engineName = engineName;
            this.communicationType = communicationType;
        }

        public String getThreadPrefix() {
            return new StringBuffer(engineName.toLowerCase()).append("-").append(communicationType.toLowerCase()).append("-default-").toString();
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(getThreadPrefix() + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    protected void sortNodeCommunications(List<NodeCommunication> list, final CommunicationType communicationType) {
        final Date FAR_PAST_DATE = new Date(0);
        final Date FAR_FUTURE_DATE = new Date(Long.MAX_VALUE);
        Collections.sort(list, new Comparator<NodeCommunication>() {
            public int compare(NodeCommunication o1, NodeCommunication o2) {
                // 1. Node priority
                int compareTo = Integer.compare(o1.getNodePriority(), o2.getNodePriority());
                if (compareTo != 0) {
                    return compareTo;
                }
                // 2. If it's a pull, look at batch_to_send_count.
                if (CommunicationType.isPullType(communicationType)) {
                    compareTo = Long.compare(o1.getBatchToSendCount(), o2.getBatchToSendCount());
                    if (compareTo != 0) {
                        return compareTo;
                    }
                }
                // 3. last_lock_time.
                Date o1LockTime = o1.getLastLockTime() != null ? o1.getLastLockTime() : FAR_PAST_DATE;
                Date o2LockTime = o2.getLastLockTime() != null ? o2.getLastLockTime() : FAR_FUTURE_DATE;
                compareTo = o1LockTime.compareTo(o2LockTime);
                if (compareTo != 0) {
                    return compareTo;
                }
                return compareTo;
            }
        });
    }

    @Override
    public void updateBatchToSendCounts(String nodeId, Map<String, Integer> batchesCountToQueues) {
        List<NodeCommunication> nodeCommunications = this.find(CommunicationType.PULL);
        List<NodeCommunication> updatedNodeCommunications = new ArrayList<NodeCommunication>();
        for (String queue : batchesCountToQueues.keySet()) {
            NodeCommunication match = null;
            for (int i = 0; i < nodeCommunications.size(); i++) {
                NodeCommunication nodeCommunication = nodeCommunications.get(i);
                if (nodeCommunication.getNodeId().equals(nodeId)
                        && nodeCommunication.getQueue().equals(queue)) {
                    match = nodeCommunication;
                    break;
                }
            }
            if (match == null) {
                NodeCommunication newNodeCommunication = new NodeCommunication();
                newNodeCommunication.setCommunicationType(CommunicationType.PULL);
                newNodeCommunication.setNodeId(nodeId);
                newNodeCommunication.setQueue(queue);
                newNodeCommunication.setBatchToSendCount(batchesCountToQueues.get(queue));
                updatedNodeCommunications.add(newNodeCommunication);
            } else {
                match.setBatchToSendCount(batchesCountToQueues.get(queue));
                updatedNodeCommunications.add(match);
            }
        }
        for (NodeCommunication nodeCommunication : updatedNodeCommunications) {
            save(nodeCommunication, false);
        }
    }

    @Override
    public Map<String, Integer> parseQueueToBatchCounts(String channelToBatchCountsString) {
        Map<String, Integer> channelsToBatchCount = new HashMap<String, Integer>();
        // åchannelName:4,anotherChannelName:6
        String[] channelToBatchCounts = channelToBatchCountsString.split(",");
        for (String channelToBatchCount : channelToBatchCounts) {
            // anotherQueueName:6
            String[] queueToBatchCountSplit = channelToBatchCount.split(":");
            String queueName = queueToBatchCountSplit[0];
            int batchCount = Integer.parseInt(queueToBatchCountSplit[1].trim());
            channelsToBatchCount.put(queueName, batchCount);
        }
        // Convert channels to queues
        Map<String, Channel> channels = configurationService.getChannels(false);
        Map<String, Integer> queuesToBatchCount = new HashMap<String, Integer>();
        for (String channelId : channelsToBatchCount.keySet()) {
            Channel channel = channels.get(channelId);
            if (channel == null) {
                log.warn("Unknown channel: '" + channelId + "'");
                continue;
            }
            String queue = channel.getQueue();
            if (!queuesToBatchCount.containsKey(queue)) {
                queuesToBatchCount.put(queue, channelsToBatchCount.get(channelId));
            } else {
                queuesToBatchCount.put(queue, queuesToBatchCount.get(queue) + channelsToBatchCount.get(channelId));
            }
        }
        return queuesToBatchCount;
    }
}
