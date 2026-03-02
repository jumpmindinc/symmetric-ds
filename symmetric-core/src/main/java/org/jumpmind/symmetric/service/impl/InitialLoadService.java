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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.load.IReloadGenerator;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.Lock;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IInitialLoadService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitialLoadService extends AbstractService implements IInitialLoadService {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ISymmetricEngine engine;
    protected IExtensionService extensionService;
    protected boolean syncTriggersBeforeInitialLoadAttempted = false;
    protected int lastLoadCountToProcess;

    public InitialLoadService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        this.extensionService = engine.getExtensionService();
    }

    @Override
    public void queueLoads(boolean force) {
        if (engine.getClusterService().isLocked(ClusterConstants.SYNC_TRIGGERS)) {
            log.info("Not queuing new loads because Sync Triggers is currently running");
            return;
        }
        Node identity = engine.getNodeService().findIdentity();
        if (identity == null) {
            log.warn("Not queuing new loads because node identity is not yet intialized!");
            return;
        }
        String nodeInfo = String.format("NodeId=%s, ExternalId=%s, Group=%s",
                identity.getNodeId(), identity.getExternalId(), identity.getNodeGroupId());
        if (!identity.isSyncEnabled()) {
            log.warn("Not queuing new loads because sync is disabled for this node! " + nodeInfo);
            return;
        }
        if (!force && !engine.getClusterService().lock(ClusterConstants.INITIAL_LOAD_QUEUE)) {
            log.debug("Not queuing new loads because cluster lock was not acquired. " + nodeInfo);
            return;
        }
        ProcessInfo processInfo = null;
        try {
            processInfo = engine.getStatisticManager().newProcessInfo(
                    new ProcessInfoKey(identity.getNodeId(), null, ProcessType.INSERT_LOAD_EVENTS));
            log.debug("Queuing initial loads for " + nodeInfo);
            processInfo.setStatus(ProcessInfo.ProcessStatus.PROCESSING);
            queueLoadsSynchronized(identity, processInfo);
            if (!processInfo.isStatusFinal()) {
                processInfo.setStatus(ProcessInfo.ProcessStatus.OK);
            }
            log.debug("Done queuing new loads for " + nodeInfo);
        } catch (Exception ex) {
            log.error("Error while queuing new loads! " + nodeInfo, ex);
            if (processInfo != null) {
                processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
            }
        } finally {
            if (!force) {
                engine.getClusterService().unlock(ClusterConstants.INITIAL_LOAD_QUEUE);
            }
        }
    }

    private synchronized void queueLoadsSynchronized(Node identity, ProcessInfo processInfo) {
        processInitialLoadEnabledFlag(identity, processInfo);
        processTableRequestLoads(identity, processInfo);
    }

    @Override
    public void cancelLoad(TableReloadStatus status) {
        Node identity = engine.getNodeService().findIdentity();
        boolean isSourceNode = identity != null && identity.getNodeId().equals(status.getSourceNodeId());
        log.info("Cancelling {} load {} {} node {}", isSourceNode ? "outgoing" : "incoming", status.getLoadId(),
                isSourceNode ? "for" : "from", isSourceNode ? status.getTargetNodeId() : status.getSourceNodeId());
        List<ProcessInfo> infos = engine.getStatisticManager().getProcessInfos();
        List<ProcessInfo> infosToWaitFor = new ArrayList<ProcessInfo>();
        for (ProcessInfo info : infos) {
            if (info.getCurrentLoadId() == status.getLoadId()) {
                log.info("Sending interrupt to " + info.getKey() + ",batchId=" + info.getCurrentBatchId());
                info.getThread().interrupt();
                infosToWaitFor.add(info);
            }
        }
        if (infosToWaitFor.size() > 0) {
            for (int i = 0; i < 10; i++) {
                AppUtils.sleep(500l);
                ListIterator<ProcessInfo> iterator = infosToWaitFor.listIterator();
                while (iterator.hasNext()) {
                    ProcessInfo p = iterator.next();
                    if (p.getEndTime() != null) {
                        iterator.remove();
                    } else {
                        log.info("Still waiting for process {}, load {} to finish", p.getKey(), p.getCurrentLoadId());
                    }
                }
                if (infosToWaitFor.size() == 0) {
                    break;
                }
            }
        }
        if (isSourceNode) {
            IOutgoingBatchService outgoingBatchService = engine.getOutgoingBatchService();
            int count = engine.getDataService().updateTableReloadRequestsCancelled(status.getLoadId(), status.getSourceNodeId());
            log.info("Marked {} load requests as OK for node {}", count, status.getTargetNodeId());
            count = engine.getDataExtractorService().cancelExtractRequests(status.getLoadId());
            log.info("Marked {} extract requests as OK for node {}", count, status.getTargetNodeId());
            count = outgoingBatchService.cancelLoadBatches(status.getLoadId());
            log.info("Marked {} batches as OK or IG for node {}", count, status.getTargetNodeId());
            engine.getDataExtractorService().releaseMissedExtractRequests();
            if (status.isFullLoad()) {
                engine.getNodeService().setInitialLoadEnded(null, status.getTargetNodeId());
            } else {
                engine.getNodeService().setPartialLoadEnded(null, status.getTargetNodeId());
            }
        } else {
            engine.getDataService().updateTableReloadRequestsCancelled(status.getLoadId(), status.getSourceNodeId());
        }
    }

    @Override
    public void cancelAllLoadsForTarget(String targetNodeId) {
        int requestCount = 0;
        List<TableReloadRequest> requests = engine.getDataService().getTableReloadRequestToProcessByTarget(targetNodeId);
        for (TableReloadRequest request : requests) {
            if (StringUtils.isBlank(request.getReloadSelect())) {
                engine.getDataService().cancelTableReloadRequest(request);
                requestCount++;
            }
        }
        if (requestCount > 0) {
            log.info("Cancelled {} outstanding load requests for target node {}", requestCount, targetNodeId);
        }
        List<TableReloadStatus> statuses = engine.getDataService().getTableReloadStatusByTarget(targetNodeId);
        for (TableReloadStatus status : statuses) {
            if (!status.isCompleted() && !status.isCancelled()) {
                TableReloadRequest request = engine.getDataService().getTableReloadRequest(status.getLoadId());
                if (request != null && StringUtils.isBlank(request.getReloadSelect())) {
                    cancelLoad(status);
                }
            }
        }
    }

    /**
     * If a load has been queued up by setting the initial load enabled or reverse initial load enabled flags, then the router service will insert the reload
     * events. This process will not run at the same time sync triggers is running.
     */
    public void processInitialLoadEnabledFlag(Node identity, ProcessInfo processInfo) {
        try {
            List<NodeSecurity> nodeSecurities = findNodesThatAreReadyForInitialLoad();
            if (nodeSecurities != null && nodeSecurities.size() > 0) {
                boolean reverseLoadFirst = parameterService.is(ParameterConstants.INITIAL_LOAD_REVERSE_FIRST);
                List<TriggerHistory> activeHistories = null;
                IReloadGenerator reloadGenerator = extensionService.getExtensionPoint(IReloadGenerator.class);
                if (reloadGenerator == null) {
                    activeHistories = engine.getTriggerRouterService().getActiveTriggerHistories();
                }
                for (NodeSecurity security : nodeSecurities) {
                    if (reloadGenerator != null) {
                        Node targetNode = engine.getNodeService().findNode(security.getNodeId());
                        activeHistories = reloadGenerator.getActiveTriggerHistories(targetNode);
                    }
                    if (activeHistories.size() > 0) {
                        boolean thisMySecurityRecord = security.getNodeId().equals(identity.getNodeId());
                        boolean reverseLoadEnabled = security.isRevInitialLoadEnabled();
                        boolean initialLoadEnabled = security.isInitialLoadEnabled();
                        boolean registered = security.hasRegistered();
                        if (!thisMySecurityRecord && registered && reverseLoadEnabled && (reverseLoadFirst || !initialLoadEnabled)) {
                            // If node is created by me then set up reverse initial load
                            if (Strings.CS.equals(security.getCreatedAtNodeId(), identity.getNodeId())) {
                                if (parameterService.is(ParameterConstants.AUTO_RELOAD_USE_CONFIG, false)) {
                                    sendLoadBasedOnConfig(security, true, processInfo);
                                } else {
                                    TableReloadRequest request = new TableReloadRequest();
                                    request.setTriggerId(ParameterConstants.ALL);
                                    request.setRouterId(ParameterConstants.ALL);
                                    request.setSourceNodeId(security.getNodeId());
                                    request.setTargetNodeId(identity.getNodeId());
                                    request.setCreateTable(parameterService.is(ParameterConstants.INITIAL_LOAD_CREATE_SCHEMA_BEFORE_RELOAD));
                                    request.setDeleteFirst(parameterService.is(ParameterConstants.INITIAL_LOAD_DELETE_BEFORE_RELOAD));
                                    request.setCreateTime(new Date());
                                    request.setLastUpdateBy(security.getRevInitialLoadCreateBy());
                                    log.info("Creating load request from node " + security.getNodeId() + " to node " + identity.getNodeId());
                                    engine.getDataService().insertTableReloadRequest(request);
                                    processInfo.incrementCurrentDataCount();
                                }
                                // Reset reverse initial load flag to off
                                engine.getNodeService().setReverseInitialLoadEnabled(security.getNodeId(), false, true, 0l, "initialLoadService");
                            }
                        } else if (!thisMySecurityRecord && registered && initialLoadEnabled && (!reverseLoadFirst || !reverseLoadEnabled)) {
                            // If node is created by me then set up initial load
                            if (Strings.CS.equals(security.getCreatedAtNodeId(), identity.getNodeId())) {
                                if (parameterService.is(ParameterConstants.AUTO_RELOAD_USE_CONFIG, false)) {
                                    sendLoadBasedOnConfig(security, false, processInfo);
                                } else {
                                    TableReloadRequest reloadRequest = new TableReloadRequest();
                                    reloadRequest.setTriggerId(ParameterConstants.ALL);
                                    reloadRequest.setRouterId(ParameterConstants.ALL);
                                    reloadRequest.setSourceNodeId(identity.getNodeId());
                                    reloadRequest.setTargetNodeId(security.getNodeId());
                                    reloadRequest.setCreateTable(parameterService.is(ParameterConstants.INITIAL_LOAD_CREATE_SCHEMA_BEFORE_RELOAD));
                                    reloadRequest.setDeleteFirst(parameterService.is(ParameterConstants.INITIAL_LOAD_DELETE_BEFORE_RELOAD));
                                    reloadRequest.setCreateTime(new Date());
                                    reloadRequest.setLastUpdateBy(security.getInitialLoadCreateBy());
                                    cancelAllLoadsForTarget(security.getNodeId());
                                    log.info("Creating load request from node " + identity.getNodeId() + " to node " + security.getNodeId());
                                    engine.getDataService().insertTableReloadRequest(reloadRequest);
                                    processInfo.incrementCurrentDataCount();
                                }
                                // Reset initial load flag to off
                                engine.getNodeService().setInitialLoadEnabled(security.getNodeId(), false, false, 0l, "initialLoadService");
                            }
                        }
                    } else {
                        List<NodeGroupLink> links = engine.getConfigurationService().getNodeGroupLinksFor(parameterService.getNodeGroupId(),
                                false);
                        if (links == null || links.size() == 0) {
                            log.warn(
                                    "Could not queue up a load for {} because a node group link is NOT configured over which a load could be delivered",
                                    security.getNodeId());
                        } else {
                            log.warn("Could not queue up a load for {} because sync triggers has not yet run", security.getNodeId());
                            if (!syncTriggersBeforeInitialLoadAttempted) {
                                syncTriggersBeforeInitialLoadAttempted = true;
                                engine.getTriggerRouterService().syncTriggers();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error while processing initial loads using node security", e);
        }
    }

    protected void sendLoadBasedOnConfig(NodeSecurity security, boolean isReverse, ProcessInfo processInfo) {
        List<Node> nodes = new ArrayList<Node>();
        List<NodeGroupLink> groupLinks = engine.getConfigurationService().getNodeGroupLinks(false);
        Node currentNode = engine.getNodeService().findNode(security.getNodeId());
        if (isReverse) {
            Set<String> nodeGroups = new HashSet<String>();
            for (NodeGroupLink link : groupLinks) {
                if (link.getSourceNodeGroupId().equals(currentNode.getNodeGroupId())) {
                    nodeGroups.add(link.getTargetNodeGroupId());
                }
            }
            Iterator<String> itr = nodeGroups.iterator();
            while (itr.hasNext()) {
                nodes.addAll(engine.getNodeService().findEnabledNodesFromNodeGroup(itr.next()));
            }
        } else {
            Set<String> nodeGroups = new HashSet<String>();
            for (NodeGroupLink link : groupLinks) {
                if (link.getTargetNodeGroupId().equals(currentNode.getNodeGroupId())) {
                    nodeGroups.add(link.getSourceNodeGroupId());
                }
            }
            Iterator<String> itr = nodeGroups.iterator();
            while (itr.hasNext()) {
                nodes.addAll(engine.getNodeService().findEnabledNodesFromNodeGroup(itr.next()));
            }
        }
        for (Node node : nodes) {
            TableReloadRequest request = new TableReloadRequest();
            request.setTriggerId(ParameterConstants.ALL);
            request.setRouterId(ParameterConstants.ALL);
            if (isReverse) {
                request.setSourceNodeId(security.getNodeId());
                request.setTargetNodeId(node.getNodeId());
                log.info("Creating config based reverse load request from node " + security.getNodeId() + " to node " + node.getNodeId());
            } else {
                request.setSourceNodeId(node.getNodeId());
                request.setTargetNodeId(security.getNodeId());
                log.info("Creating config based load request from node " + node.getNodeId() + " to node " + security.getNodeId());
            }
            request.setCreateTable(parameterService.is(ParameterConstants.INITIAL_LOAD_CREATE_SCHEMA_BEFORE_RELOAD));
            request.setDeleteFirst(parameterService.is(ParameterConstants.INITIAL_LOAD_DELETE_BEFORE_RELOAD));
            request.setCreateTime(new Date());
            engine.getDataService().insertTableReloadRequest(request);
            processInfo.incrementCurrentDataCount();
        }
    }

    protected void processTableRequestLoads(Node source, ProcessInfo processInfo) {
        List<TableReloadRequest> loadsToProcess = engine.getDataService().getTableReloadRequestToProcess(source.getNodeId());
        if (loadsToProcess.size() > 0) {
            processInfo.setStatus(ProcessInfo.ProcessStatus.CREATING);
            int maxLoadCount = parameterService.getInt(ParameterConstants.INITIAL_LOAD_EXTRACT_THREAD_COUNT_PER_SERVER, 20);
            int activeLoadCount = engine.getDataService().getActiveTableReloadStatus().size();
            int loadCountToProcess = loadsToProcess.size();
            if (activeLoadCount >= maxLoadCount) {
                logActiveLoadCount(activeLoadCount, loadCountToProcess);
                return;
            }
            boolean useExtractJob = parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB, true);
            if (useExtractJob) {
                Map<String, Channel> channels = engine.getConfigurationService().getChannels(false);
                boolean isError = false;
                for (Channel channel : channels.values()) {
                    if (channel.isReloadFlag() && channel.getMaxBatchSize() == 1) {
                        log.error("Max batch size must be greater than 1 for '{}' channel", channel.getChannelId());
                        isError = true;
                    }
                }
                if (isError) {
                    log.error("Initial loads are disabled until max batch size is corrected or {} is set to false",
                            ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB);
                    return;
                }
            }
            log.info("Found {} table reload requests to process.", loadCountToProcess);
            boolean streamToFile = parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED, false);
            Map<String, List<TableReloadRequest>> requestsSplitByLoad = new HashMap<String, List<TableReloadRequest>>();
            Map<String, List<TriggerRouter>> triggerRoutersByNodeGroup = new HashMap<String, List<TriggerRouter>>();
            Map<Integer, ExtractRequest> extractRequests = null;
            IReloadGenerator reloadGenerator = extensionService.getExtensionPoint(IReloadGenerator.class);
            for (TableReloadRequest load : loadsToProcess) {
                Node targetNode = engine.getNodeService().findNode(load.getTargetNodeId(), true);
                NodeSecurity targetNodeSecurity = engine.getNodeService().findNodeSecurity(load.getTargetNodeId(), true);
                if (useExtractJob && !streamToFile) {
                    throw new SymmetricException(
                            String.format("Node '%s' can't process load for '%s' because of conflicting parameters: %s=%s and %s=%s",
                                    source.getNodeId(), load.getTargetNodeId(), ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB, useExtractJob,
                                    ParameterConstants.STREAM_TO_FILE_ENABLED, streamToFile));
                } else if (isOkayToQueueLoad(targetNodeSecurity)) {
                    if (load.isFullLoadRequest() && isValidLoadTarget(load.getTargetNodeId())) {
                        List<TableReloadRequest> fullLoad = new ArrayList<TableReloadRequest>();
                        fullLoad.add(load);
                        List<TriggerRouter> triggerRouters = getTriggerRoutersForNodeGroup(triggerRoutersByNodeGroup, targetNode.getNodeGroupId());
                        boolean reverse = targetNode.getNodeId().equals(source.getCreatedAtNodeId());
                        extractRequests = engine.getDataService().insertReloadEvents(targetNode, reverse, fullLoad, processInfo,
                                triggerRouters, extractRequests, reloadGenerator);
                        loadCountToProcess--;
                        if (++activeLoadCount >= maxLoadCount) {
                            logActiveLoadCount(activeLoadCount, loadCountToProcess);
                            return;
                        }
                    } else {
                        boolean registered = targetNodeSecurity != null && (targetNodeSecurity.hasRegistered()
                                || targetNodeSecurity.getNodeId().equals(targetNodeSecurity.getCreatedAtNodeId()));
                        if (registered) {
                            // Make loads unique to the target and create time
                            String key = load.getTargetNodeId() + "::" + load.getCreateTime();
                            if (!requestsSplitByLoad.containsKey(key)) {
                                requestsSplitByLoad.put(key, new ArrayList<TableReloadRequest>());
                            }
                            requestsSplitByLoad.get(key).add(load);
                        } else {
                            log.warn("There was a load queued up for '{}', but the node is not registered.  It is being ignored",
                                    load.getTargetNodeId());
                        }
                    }
                }
            }
            Map<String, List<TriggerRouter>> triggerRoutersByTargetNodeGroupId = new HashMap<String, List<TriggerRouter>>();
            for (Map.Entry<String, List<TableReloadRequest>> entry : requestsSplitByLoad.entrySet()) {
                Node targetNode = engine.getNodeService().findNode(entry.getKey().split("::")[0], true);
                if (targetNode == null) {
                    targetNode = engine.getNodeService().findNode(entry.getKey().split("::")[0], false);
                }
                ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
                List<TriggerRouter> triggerRouters = triggerRoutersByTargetNodeGroupId.get(targetNode.getNodeGroupId());
                if (triggerRouters == null) {
                    triggerRouters = triggerRouterService.getAllTriggerRoutersForReloadForCurrentNode(parameterService.getNodeGroupId(),
                            targetNode.getNodeGroupId());
                    triggerRoutersByTargetNodeGroupId.put(targetNode.getNodeGroupId(), triggerRouters);
                }
                boolean reverse = targetNode.getNodeId().equals(source.getCreatedAtNodeId());
                extractRequests = engine.getDataService().insertReloadEvents(targetNode, reverse, entry.getValue(), processInfo,
                        triggerRouters, extractRequests, reloadGenerator);
                loadCountToProcess--;
                if (++activeLoadCount >= maxLoadCount) {
                    logActiveLoadCount(activeLoadCount, loadCountToProcess);
                    return;
                }
            }
        }
    }

    protected void logActiveLoadCount(int activeLoadCount, int loadCountToProcess) {
        String message = "Max outgoing loads of {} are active, while {} outgoing loads are pending";
        if (loadCountToProcess != lastLoadCountToProcess) {
            log.warn(message, activeLoadCount, loadCountToProcess);
        } else {
            log.debug(message, activeLoadCount, loadCountToProcess);
        }
        lastLoadCountToProcess = loadCountToProcess;
    }

    protected List<TriggerRouter> getTriggerRoutersForNodeGroup(Map<String, List<TriggerRouter>> triggerRoutersByNodeGroup, String nodeGroupId) {
        List<TriggerRouter> list = triggerRoutersByNodeGroup.get(nodeGroupId);
        if (list == null) {
            list = engine.getTriggerRouterService().getAllTriggerRoutersForReloadForCurrentNode(parameterService.getNodeGroupId(), nodeGroupId);
            triggerRoutersByNodeGroup.put(nodeGroupId, list);
        }
        return list;
    }

    protected List<NodeSecurity> findNodesThatAreReadyForInitialLoad() {
        INodeService nodeService = engine.getNodeService();
        String me = nodeService.findIdentityNodeId();
        List<NodeSecurity> toReturn = new ArrayList<NodeSecurity>();
        List<NodeSecurity> securities = nodeService.findNodeSecurityWithLoadEnabled();
        for (NodeSecurity nodeSecurity : securities) {
            if (Strings.CS.equals(nodeSecurity.getCreatedAtNodeId(), me) && nodeSecurity.hasRegistered() &&
                    (nodeSecurity.isInitialLoadEnabled() || nodeSecurity.isRevInitialLoadEnabled()) && isOkayToQueueLoad(nodeSecurity)) {
                toReturn.add(nodeSecurity);
            }
        }
        return toReturn;
    }

    protected boolean isOkayToQueueLoad(NodeSecurity nodeSecurity) {
        boolean okayToQueueLoad = true;
        if (engine.getConfigurationService().isMasterToMaster() && nodeSecurity != null) {
            Lock routingLock = engine.getClusterService().findLocks().get(ClusterConstants.ROUTE);
            if (routingLock.getLastLockTime() == null || (nodeSecurity.getRegistrationTime() != null &&
                    routingLock.getLastLockTime().compareTo(nodeSecurity.getRegistrationTime()) <= 0)) {
                okayToQueueLoad = false;
                log.info("Delaying initial load request for node {} until the last routing run is after {}",
                        nodeSecurity.getNodeId(), nodeSecurity.getRegistrationTime());
            } else {
                int count = engine.getOutgoingBatchService().countOutgoingBatchesUnsent(Constants.CHANNEL_SYSTEM);
                if (count > 0) {
                    okayToQueueLoad = false;
                    log.info("Delaying initial load request for node {} until {} system batches are complete",
                            nodeSecurity.getNodeId(), count);
                }
            }
        }
        return okayToQueueLoad;
    }

    protected boolean isValidLoadTarget(String targetNodeId) {
        boolean result = false;
        NodeSecurity targetNodeSecurity = engine.getNodeService().findNodeSecurity(targetNodeId);
        if (targetNodeSecurity != null) {
            boolean reverseLoadFirst = parameterService.is(ParameterConstants.INITIAL_LOAD_REVERSE_FIRST);
            boolean registered = targetNodeSecurity.hasRegistered();
            boolean reverseLoadQueued = targetNodeSecurity.isRevInitialLoadEnabled();
            if (registered && (!reverseLoadFirst || !reverseLoadQueued)) {
                result = true;
            } else {
                log.info("Unable to process load for target node id " + targetNodeId + " [registered: " + registered + ", reverse load first: "
                        + reverseLoadFirst + ", reverse load queued: " + reverseLoadQueued + "]");
            }
        }
        return result;
    }
}
