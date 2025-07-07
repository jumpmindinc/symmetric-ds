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

import static org.jumpmind.symmetric.model.ProcessType.MANUAL_LOAD;
import static org.jumpmind.symmetric.model.ProcessType.OFFLINE_PULL;
import static org.jumpmind.symmetric.model.ProcessType.PULL_CONFIG_JOB;
import static org.jumpmind.symmetric.model.ProcessType.PULL_JOB_LOAD;
import static org.jumpmind.symmetric.model.ProcessType.PULL_JOB_TRANSFER;
import static org.jumpmind.symmetric.model.ProcessType.PUSH_HANDLER_LOAD;
import static org.jumpmind.symmetric.model.ProcessType.PUSH_HANDLER_TRANSFER;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.HttpException;
import org.jumpmind.exception.InvalidRetryException;
import org.jumpmind.exception.IoException;
import org.jumpmind.extension.IProcessInfoListener;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ErrorConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.ext.INodeRegistrationListener;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.DataProcessor;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.io.data.reader.DataReaderStatistics;
import org.jumpmind.symmetric.io.data.reader.ProtocolDataReader;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformTable;
import org.jumpmind.symmetric.io.data.writer.Conflict;
import org.jumpmind.symmetric.io.data.writer.Conflict.DetectConflict;
import org.jumpmind.symmetric.io.data.writer.Conflict.PingBack;
import org.jumpmind.symmetric.io.data.writer.Conflict.ResolveConflict;
import org.jumpmind.symmetric.io.data.writer.ConflictException;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterErrorHandler;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;
import org.jumpmind.symmetric.io.data.writer.IProtocolDataWriterListener;
import org.jumpmind.symmetric.io.data.writer.ResolvedData;
import org.jumpmind.symmetric.io.data.writer.TransformWriter;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.SimpleStagingDataWriter;
import org.jumpmind.symmetric.io.stage.StagingLowFreeSpace;
import org.jumpmind.symmetric.load.ConfigurationChangedDatabaseWriterFilter;
import org.jumpmind.symmetric.load.DefaultDataLoaderFactory;
import org.jumpmind.symmetric.load.DynamicDatabaseWriterFilter;
import org.jumpmind.symmetric.load.IDataLoaderFactory;
import org.jumpmind.symmetric.load.ILoadSyncLifecycleListener;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ChannelMap;
import org.jumpmind.symmetric.model.IModelObject;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingError;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.LoadFilter.LoadFilterType;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoDataWriter;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.ILoadFilterService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.service.RegistrationRequiredException;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.AuthenticationException;
import org.jumpmind.symmetric.transport.AuthenticationExpiredException;
import org.jumpmind.symmetric.transport.ConnectionDuplicateException;
import org.jumpmind.symmetric.transport.ConnectionRejectedException;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.ServiceUnavailableException;
import org.jumpmind.symmetric.transport.SyncDisabledException;
import org.jumpmind.symmetric.transport.internal.InternalIncomingTransport;
import org.jumpmind.symmetric.web.WebConstants;
import org.jumpmind.util.CustomizableThreadFactory;
import org.slf4j.MDC;

/**
 * Responsible for writing batch data to the database
 * 
 * @see IDataLoaderService
 */
public class DataLoaderService extends AbstractService implements IDataLoaderService {
    private IIncomingBatchService incomingBatchService;
    private IConfigurationService configurationService;
    private ITransportManager transportManager;
    private IStatisticManager statisticManager;
    private INodeService nodeService;
    private ITransformService transformService;
    private ILoadFilterService loadFilterService;
    private IExtensionService extensionService;
    private INodeCommunicationService nodeCommunicationService;
    private ISymmetricEngine engine = null;
    private Date lastUpdateTime;
    private CustomizableThreadFactory threadFactory;
    private ICacheManager cacheManager;

    public DataLoaderService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.incomingBatchService = engine.getIncomingBatchService();
        this.configurationService = engine.getConfigurationService();
        this.transportManager = engine.getTransportManager();
        this.statisticManager = engine.getStatisticManager();
        this.nodeService = engine.getNodeService();
        this.transformService = engine.getTransformService();
        this.loadFilterService = engine.getLoadFilterService();
        this.setSqlMap(new DataLoaderServiceSqlMap(platform, createSqlReplacementTokens()));
        extensionService = engine.getExtensionService();
        extensionService.addExtensionPoint(new DefaultDataLoaderFactory(engine));
        extensionService.addExtensionPoint(new ConfigurationChangedDatabaseWriterFilter(engine));
        this.nodeCommunicationService = engine.getNodeCommunicationService();
        this.cacheManager = engine.getCacheManager();
        this.engine = engine;
    }

    public boolean refreshFromDatabase() {
        Date date = sqlTemplate.queryForObject(getSql("selectMaxLastUpdateTime"), Date.class);
        if (date != null) {
            if (lastUpdateTime == null || lastUpdateTime.before(date)) {
                if (lastUpdateTime != null) {
                    log.info("Newer conflict settings were detected");
                }
                lastUpdateTime = date;
                clearCache();
                return true;
            }
        }
        return false;
    }

    public List<String> getAvailableDataLoaderFactories() {
        return new ArrayList<String>(getDataLoaderFactories().keySet());
    }

    protected Map<String, IDataLoaderFactory> getDataLoaderFactories() {
        Map<String, IDataLoaderFactory> dataLoaderFactories = new HashMap<String, IDataLoaderFactory>();
        for (IDataLoaderFactory factory : engine.getExtensionService().getExtensionPointList(
                IDataLoaderFactory.class)) {
            dataLoaderFactories.put(factory.getTypeName(), factory);
        }
        return dataLoaderFactories;
    }

    public List<IncomingBatch> loadDataBatch(String batchData) {
        return loadDataBatch(batchData, null);
    }

    public List<IncomingBatch> loadDataBatch(String batchData, IProcessInfoListener listener) {
        String nodeId = nodeService.findIdentityNodeId();
        if (StringUtils.isNotBlank(nodeId)) {
            ProcessInfo processInfo = statisticManager.newProcessInfo(new ProcessInfoKey(nodeId,
                    nodeId, MANUAL_LOAD));
            processInfo.setListener(listener);
            try {
                InternalIncomingTransport transport = new InternalIncomingTransport(
                        new BufferedReader(new StringReader(batchData)));
                List<IncomingBatch> list = loadDataFromTransport(processInfo,
                        nodeService.findIdentity(), transport, null, null);
                processInfo.setStatus(ProcessInfo.ProcessStatus.OK);
                return list;
            } catch (IOException ex) {
                processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                throw new IoException();
            } catch (RuntimeException ex) {
                processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                throw ex;
            }
        } else {
            return new ArrayList<IncomingBatch>(0);
        }
    }

    /**
     * Connect to the remote node and pull data. The acknowledgment of commit/error status is sent separately after the data is processed.
     */
    public RemoteNodeStatus loadDataFromPull(Node remote, String queue) throws IOException {
        RemoteNodeStatus status = new RemoteNodeStatus(remote != null ? remote.getNodeId() : null,
                queue, configurationService.getChannels(false));
        loadDataFromPull(remote, status);
        return status;
    }

    public void loadDataFromPull(Node remote, RemoteNodeStatus status) throws IOException {
        Node local = nodeService.findIdentity();
        if (local == null) {
            local = new Node(this.parameterService, symmetricDialect, this.engine.getDatabasePlatform().getName());
            local.setDeploymentType(engine.getDeploymentType());
        }
        try {
            NodeSecurity localSecurity = nodeService.findNodeSecurity(local.getNodeId(), true);
            IIncomingTransport transport = null;
            boolean isRegisterTransport = false;
            if (remote != null && localSecurity != null) {
                Map<String, String> requestProperties = new HashMap<String, String>();
                ChannelMap suspendIgnoreChannels = configurationService
                        .getSuspendIgnoreChannelLists();
                requestProperties.put(WebConstants.SUSPENDED_CHANNELS,
                        suspendIgnoreChannels.getSuspendChannelsAsString());
                requestProperties.put(WebConstants.IGNORED_CHANNELS,
                        suspendIgnoreChannels.getIgnoreChannelsAsString());
                requestProperties.put(WebConstants.CHANNEL_QUEUE, status.getQueue());
                transport = transportManager.getPullTransport(remote, local,
                        localSecurity.getNodePassword(), requestProperties,
                        parameterService.getRegistrationUrl());
            } else {
                List<INodeRegistrationListener> registrationListeners = extensionService.getExtensionPointList(INodeRegistrationListener.class);
                Map<String, String> requestProps = new HashMap<String, String>();
                for (INodeRegistrationListener l : registrationListeners) {
                    Map<String, String> props = l.getRequestProperties();
                    if (props != null) {
                        requestProps.putAll(props);
                    }
                }
                transport = transportManager.getRegisterTransport(local,
                        parameterService.getRegistrationUrl(), requestProps);
                log.info("Using registration URL of {}", transport.getUrl());
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationUrlUpdated(transport.getUrl());
                }
                remote = new Node();
                remote.setSyncUrl(parameterService.getRegistrationUrl());
                isRegisterTransport = true;
            }
            ProcessInfo transferInfo = statisticManager.newProcessInfo(new ProcessInfoKey(remote
                    .getNodeId(), status.getQueue(), local.getNodeId(), PULL_JOB_TRANSFER));
            try {
                List<IncomingBatch> list = loadDataFromTransport(transferInfo, remote, transport, null, status);
                if (list.size() > 0) {
                    transferInfo.setStatus(ProcessInfo.ProcessStatus.ACKING);
                    status.updateIncomingStatus(list);
                    local = nodeService.findIdentity();
                    if (local != null) {
                        localSecurity = nodeService.findNodeSecurity(local.getNodeId(), !isRegisterTransport);
                        if (StringUtils.isNotBlank(transport.getRedirectionUrl())) {
                            /*
                             * We were redirected for the pull, we need to redirect for the ack
                             */
                            String url = transport.getRedirectionUrl();
                            int index = url.indexOf("/registration?");
                            if (index >= 0) {
                                url = url.substring(0, index);
                            }
                            log.info("Setting the sync url for ack to: {}", url);
                            remote.setSyncUrl(url);
                        }
                        if (!sendAck(remote, local, localSecurity, list, transportManager, status.getQueue())) {
                            status.setStatus(RemoteNodeStatus.Status.UNKNOWN_ERROR);
                        }
                    }
                }
                if (containsError(list)) {
                    transferInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                } else {
                    transferInfo.setStatus(ProcessInfo.ProcessStatus.OK);
                }
                updateBatchToSendCount(remote, transport);
                purgeLoadBatchesFromStaging(list);
            } catch (RuntimeException e) {
                transferInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                throw e;
            } catch (IOException e) {
                transferInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                throw e;
            }
        } catch (RegistrationRequiredException e) {
            if (StringUtils.isBlank(remote.getSyncUrl())
                    || remote.getSyncUrl().equalsIgnoreCase(parameterService.getRegistrationUrl())) {
                log.warn("Node information missing on the server.  Attempting to re-register remote.getSyncUrl()={}", remote.getSyncUrl());
                loadDataFromPull(null, status);
                nodeService.findIdentity(false);
            } else {
                log.warn(
                        "Failed to pull data from node '{}'. It probably is missing a node security record for '{}'.",
                        remote.getNodeId(), local.getNodeId());
            }
        } catch (MalformedURLException e) {
            if (remote != null) {
                log.error("Could not connect to the {} node's transport because of a bad URL: '{}' {}",
                        remote.getNodeId(), remote.getSyncUrl(), e);
            } else {
                log.error("", e);
            }
            throw e;
        }
    }

    protected void updateBatchToSendCount(Node remote, IIncomingTransport transport) {
        Map<String, String> headers = transport.getHeaders();
        if (headers != null && headers.containsKey(WebConstants.BATCH_TO_SEND_COUNT)) {
            Map<String, Integer> queuesToBatchCounts = nodeCommunicationService.parseQueueToBatchCounts(headers.get(WebConstants.BATCH_TO_SEND_COUNT));
            nodeCommunicationService.updateBatchToSendCounts(remote.getNodeId(), queuesToBatchCounts);
        }
    }

    private boolean containsError(List<IncomingBatch> list) {
        for (IncomingBatch incomingBatch : list) {
            if (incomingBatch.getStatus() == Status.ER) {
                return true;
            }
        }
        return false;
    }

    /**
     * Load database from input stream and write acknowledgment to output stream. This is used for a "push" request with a response of an acknowledgment.
     */
    public void loadDataFromPush(Node sourceNode, InputStream in, OutputStream out)
            throws IOException {
        loadDataFromPush(sourceNode, null, in, out);
    }

    /**
     * Load database from input stream and write acknowledgment to output stream. This is used for a "push" request with a response of an acknowledgment.
     */
    public void loadDataFromPush(Node sourceNode, String queue, InputStream in, OutputStream out)
            throws IOException {
        Node local = nodeService.findIdentity();
        if (sourceNode != null && sourceNode.getNodeId() != null) {
            ProcessInfo transferInfo = statisticManager.newProcessInfo(new ProcessInfoKey(sourceNode
                    .getNodeId(), queue, local != null ? local.getNodeId() : null, PUSH_HANDLER_TRANSFER));
            try {
                List<IncomingBatch> batchList = loadDataFromTransport(transferInfo, sourceNode,
                        new InternalIncomingTransport(in), out, null);
                logDataReceivedFromPush(sourceNode, batchList, transferInfo);
                if (local == null) {
                    local = nodeService.findIdentity(false);
                }
                if (local != null && local.getNodeId() != null) {
                    NodeSecurity security = nodeService.findNodeSecurity(local.getNodeId());
                    transferInfo.setStatus(ProcessInfo.ProcessStatus.ACKING);
                    transportManager.writeAcknowledgement(out, sourceNode, batchList, local,
                            security != null ? security.getNodePassword() : null);
                } else {
                    log.info("Could not load data because the node is not registered");
                    throw new RegistrationRequiredException();
                }
                transferInfo.setStatus(ProcessInfo.ProcessStatus.OK);
                purgeLoadBatchesFromStaging(batchList);
            } catch (Exception e) {
                transferInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new RuntimeException(e);
            }
        }
    }

    private void logDataReceivedFromPush(Node sourceNode, List<IncomingBatch> batchList, ProcessInfo processInfo) {
        int okBatchesCount = 0;
        int errorBatchesCount = 0;
        int okDataCount = 0;
        for (IncomingBatch incomingBatch : batchList) {
            if (incomingBatch.getStatus() == Status.OK) {
                okBatchesCount++;
                okDataCount += incomingBatch.getLoadRowCount();
            } else if (incomingBatch.getStatus() == Status.ER) {
                errorBatchesCount++;
            }
        }
        if (okBatchesCount > 0) {
            if (errorBatchesCount > 0) {
                log.info("{} data and {} batches loaded during push request from {}.  There were {} batches in error",
                        new Object[] { okDataCount, okBatchesCount, sourceNode.toString(), errorBatchesCount });
            } else {
                log.info("{} data and {} batches loaded during push request from {}",
                        new Object[] { okDataCount, okBatchesCount, sourceNode.toString() });
            }
        }
    }

    protected void purgeLoadBatchesFromStaging(List<IncomingBatch> batchList) {
        long threshold = parameterService.getLong(ParameterConstants.INITIAL_LOAD_PURGE_STAGE_IMMEDIATE_THRESHOLD_ROWS);
        if (threshold >= 0 && batchList != null) {
            for (IncomingBatch batch : batchList) {
                if (batch.isLoadFlag() && !batch.isCommonFlag() && batch.getDataRowCount() >= threshold) {
                    IStagedResource resource = engine.getStagingManager().find(Constants.STAGING_CATEGORY_INCOMING,
                            batch.getStagedLocation(), batch.getBatchId());
                    if (resource != null) {
                        resource.delete();
                    }
                }
            }
        }
    }

    public List<IncomingBatch> loadDataFromOfflineTransport(Node remote, RemoteNodeStatus status, IIncomingTransport transport) throws IOException {
        Node local = nodeService.findIdentity();
        ProcessInfo processInfo = statisticManager.newProcessInfo(new ProcessInfoKey(remote
                .getNodeId(), local.getNodeId(), OFFLINE_PULL));
        List<IncomingBatch> list = null;
        try {
            list = loadDataFromTransport(processInfo, remote, transport, null, null);
            if (list.size() > 0) {
                processInfo.setStatus(ProcessInfo.ProcessStatus.ACKING);
                status.updateIncomingStatus(list);
            }
            if (containsError(list)) {
                processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
            } else {
                processInfo.setStatus(ProcessInfo.ProcessStatus.OK);
            }
        } catch (RuntimeException e) {
            processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
            throw e;
        } catch (IOException e) {
            processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
            throw e;
        }
        return list;
    }

    public void loadDataFromConfig(Node remote, RemoteNodeStatus status, boolean force) throws IOException {
        if (engine.getParameterService().isRegistrationServer() || remote == null || Version.isOlderThanVersion(remote.getSymmetricVersion(), "3.8.22")) {
            return;
        }
        Node local = nodeService.findIdentity();
        try {
            NodeSecurity localSecurity = nodeService.findNodeSecurity(local.getNodeId(), true);
            String configVersion = force ? "" : local.getConfigVersion();
            IIncomingTransport transport = engine.getTransportManager().getConfigTransport(remote, local,
                    localSecurity.getNodePassword(), Version.version(), configVersion, remote.getSyncUrl());
            ProcessInfo processInfo = statisticManager.newProcessInfo(new ProcessInfoKey(remote
                    .getNodeId(), Constants.CHANNEL_CONFIG, local.getNodeId(), PULL_CONFIG_JOB));
            try {
                log.info("Requesting current configuration {symmetricVersion={}, configVersion={}}",
                        Version.version(), local.getConfigVersion());
                List<IncomingBatch> list = loadDataFromTransport(processInfo, remote, transport, null, null);
                if (containsError(list)) {
                    processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                } else {
                    if (list.size() > 0) {
                        status.updateIncomingStatus(list);
                        local.setConfigVersion(Version.version());
                        nodeService.save(local);
                    }
                    processInfo.setStatus(ProcessInfo.ProcessStatus.OK);
                }
            } catch (RuntimeException e) {
                processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                throw e;
            } catch (IOException e) {
                processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                throw e;
            }
        } catch (RegistrationRequiredException e) {
            log.warn("Failed to pull configuration from node '{}'. It probably is missing a node security record for '{}'.",
                    remote.getNodeId(), local.getNodeId());
        } catch (MalformedURLException e) {
            log.error("Could not connect to the {} node's transport because of a bad URL: '{}' {}",
                    remote.getNodeId(), remote.getSyncUrl(), e);
            throw e;
        }
    }

    public List<IncomingBatch> loadDataFromTransport(ProcessInfo processInfo, Node sourceNode, IIncomingTransport transport) throws IOException {
        return loadDataFromTransport(processInfo, sourceNode, transport, null, null);
    }

    /**
     * Load database from input stream and return a list of batch statuses. This is used for a pull request that responds with data, and the acknowledgment is
     * sent later.
     */
    protected List<IncomingBatch> loadDataFromTransport(final ProcessInfo transferInfo,
            final Node sourceNode, IIncomingTransport transport, OutputStream out, RemoteNodeStatus status) throws IOException {
        final ManageIncomingBatchListener listener = new ManageIncomingBatchListener(transferInfo, engine);
        final DataContext ctx = new DataContext();
        Throwable error = null;
        try {
            Node targetNode = nodeService.findIdentity();
            ctx.put(Constants.DATA_CONTEXT_ENGINE, engine);
            if (targetNode != null) {
                ctx.put(Constants.DATA_CONTEXT_TARGET_NODE, targetNode);
                ctx.put(Constants.DATA_CONTEXT_TARGET_NODE_ID, targetNode.getNodeId());
                ctx.put(Constants.DATA_CONTEXT_TARGET_NODE_GROUP_ID, targetNode.getNodeGroupId());
                ctx.put(Constants.DATA_CONTEXT_TARGET_NODE_EXTERNAL_ID, targetNode.getExternalId());
            }
            ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE, sourceNode);
            ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE_ID, sourceNode.getNodeId());
            ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE_GROUP_ID, sourceNode.getNodeGroupId());
            ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE_EXTERNAL_ID, sourceNode.getExternalId());
            for (ILoadSyncLifecycleListener l : extensionService
                    .getExtensionPointList(ILoadSyncLifecycleListener.class)) {
                l.syncStarted(ctx);
            }
            long memoryThresholdInBytes = parameterService.getLong(ParameterConstants.STREAM_TO_FILE_THRESHOLD);
            String targetNodeId = nodeService.findIdentityNodeId();
            boolean streamToFile = parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED);
            if (streamToFile) {
                transferInfo.setStatus(ProcessStatus.TRANSFERRING);
                if (threadFactory == null) {
                    threadFactory = new CustomizableThreadFactory(parameterService.getEngineName().toLowerCase() + "-dataloader");
                }
                ExecutorService executor = Executors.newFixedThreadPool(1, threadFactory);
                LoadIntoDatabaseOnArrivalListener loadListener = new LoadIntoDatabaseOnArrivalListener(transferInfo,
                        sourceNode.getNodeId(), listener, executor);
                SimpleStagingDataWriter stageWriter = null;
                try {
                    stageWriter = new SimpleStagingDataWriter(transferInfo, transport.openReader(), engine, Constants.STAGING_CATEGORY_INCOMING,
                            memoryThresholdInBytes, BatchType.LOAD, sourceNode.getNodeId(), targetNodeId, ctx, loadListener);
                    notifyQueuesReady(status, transport);
                    stageWriter.process();
                } finally {
                    /* Previously submitted tasks will still be executed */
                    executor.shutdown();
                }
                OutputStreamWriter outWriter = null;
                if (out != null) {
                    try {
                        outWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                        long keepAliveMillis = parameterService.getLong(ParameterConstants.DATA_LOADER_SEND_ACK_KEEPALIVE);
                        while (!executor.awaitTermination(keepAliveMillis, TimeUnit.MILLISECONDS)) {
                            outWriter.write("1=1&");
                            outWriter.flush();
                        }
                    } catch (Exception ex) {
                        log.info("Could not send keep alives to " + sourceNode + " " + ex);
                        awaitTermination(executor);
                    }
                } else {
                    transport.close();
                    awaitTermination(executor);
                }
                loadListener.isDone();
                if (stageWriter.getException() != null) {
                    throw stageWriter.getException();
                }
            } else {
                transferInfo.setStatus(ProcessStatus.OK);
                ProcessInfo loadInfo = statisticManager.newProcessInfo(new ProcessInfoKey(sourceNode.getNodeId(), transferInfo.getQueue(), nodeService
                        .findIdentityNodeId(), PULL_JOB_LOAD));
                try {
                    DataProcessor processor = new DataProcessor(new ProtocolDataReader(BatchType.LOAD,
                            targetNodeId, transport.openReader(), streamToFile), null, listener, "data load") {
                        @Override
                        protected IDataWriter chooseDataWriter(Batch batch) {
                            return buildDataWriter(loadInfo, sourceNode.getNodeId(),
                                    batch.getChannelId(), batch.getBatchId(),
                                    ((ManageIncomingBatchListener) listener).getCurrentBatch().isRetry());
                        }
                    };
                    notifyQueuesReady(status, transport);
                    processor.process(ctx);
                    loadInfo.setStatus(ProcessStatus.OK);
                } catch (Throwable e) {
                    loadInfo.setStatus(ProcessStatus.ERROR);
                    throw e;
                }
            }
        } catch (Throwable ex) {
            error = ex;
            if (status != null) {
                status.setStatus(RemoteNodeStatus.Status.DATA_ERROR);
            }
            if (parameterService.is(ParameterConstants.AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE_RELOAD)
                    && listener.getCurrentBatch() != null && listener.isNewErrorForCurrentBatch()
                    && listener.getCurrentBatch().isLoadFlag()
                    && listener.getCurrentBatch().getSqlCode() == ErrorConstants.FK_VIOLATION_CODE
                    && !Version.isOlderThanVersion(sourceNode.getSymmetricVersion(), "3.12.6")) {
                engine.getDataService().reloadMissingForeignKeyRowsForLoad(sourceNode.getNodeId(), ctx.getBatch().getBatchId(),
                        listener.getCurrentBatch().getFailedLineNumber(), ctx.getTable(), ctx.getData(), Constants.CHANNEL_CONFIG);
            }
            if (parameterService.is(ParameterConstants.AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE)
                    && listener.getCurrentBatch() != null && listener.isNewErrorForCurrentBatch()
                    && !listener.getCurrentBatch().isLoadFlag()
                    && listener.getCurrentBatch().getSqlCode() == ErrorConstants.FK_VIOLATION_CODE) {
                engine.getDataService().reloadMissingForeignKeyRowsReverse(sourceNode.getNodeId(), ctx.getTable(), ctx.getData(), null,
                        parameterService.is(ParameterConstants.AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE_PEERS));
            }
            logOrRethrow(ex, sourceNode.getNodeId(), transport.getUrl() == null || transport.getUrl().endsWith(WebConstants.URL_REGISTRATION));
        } finally {
            transport.close();
            for (ILoadSyncLifecycleListener l : extensionService
                    .getExtensionPointList(ILoadSyncLifecycleListener.class)) {
                l.syncEnded(ctx, listener.getBatchesProcessed(), error);
            }
        }
        List<IncomingBatch> batchesProcessed = listener.getBatchesProcessed();
        if (error != null && error instanceof ProtocolException) {
            batchesProcessed.add(new IncomingBatch());
        }
        return batchesProcessed;
    }

    private void notifyQueuesReady(RemoteNodeStatus status, IIncomingTransport transport) {
        if (status != null && Constants.QUEUE_DEFAULT.equals(status.getQueue())) {
            Map<String, String> headers = transport.getHeaders();
            if (headers != null) {
                String queues = headers.get(WebConstants.HEADER_READY_QUEUES);
                if (queues != null) {
                    log.debug("Received ready queues from node {}: {}", status.getNodeId(), queues);
                    incomingBatchService.setReadyQueues(status.getNodeId(), Arrays.asList(queues.split("\\s*,\\s*")));
                }
            }
        }
    }

    private void awaitTermination(ExecutorService executor) throws InterruptedException {
        long hours = 1;
        while (!executor.awaitTermination(1, TimeUnit.HOURS)) {
            log.info("Executor has been awaiting loader termination for {} hour(s).", hours);
            hours++;
        }
    }

    protected void logOrRethrow(Throwable ex, String sourceNodeId, boolean isRegistration) throws IOException {
        // Throwing exception will mean acks are not sent, so only certain exceptions should be thrown
        if (ex instanceof RegistrationRequiredException) {
            throw (RegistrationRequiredException) ex;
        } else if (ex instanceof ConnectException) {
            throw (ConnectException) ex;
        } else if (ex instanceof UnknownHostException) {
            throw (UnknownHostException) ex;
        } else if (ex instanceof RegistrationNotOpenException) {
            throw (RegistrationNotOpenException) ex;
        } else if (ex instanceof ConnectionRejectedException) {
            throw (ConnectionRejectedException) ex;
        } else if (ex instanceof ConnectionDuplicateException) {
            throw (ConnectionDuplicateException) ex;
        } else if (ex instanceof ServiceUnavailableException) {
            throw (ServiceUnavailableException) ex;
        } else if (ex instanceof SyncDisabledException) {
            throw (SyncDisabledException) ex;
        } else if (ex instanceof HttpException) {
            throw (HttpException) ex;
        } else if (ex instanceof InvalidRetryException) {
            throw (InvalidRetryException) ex;
        } else if (ex instanceof AuthenticationException) {
            throw (AuthenticationException) ex;
        } else if (ex instanceof AuthenticationExpiredException) {
            throw (AuthenticationExpiredException) ex;
        } else if (isRegistration) {
            if (ex instanceof IOException) {
                throw (IOException) ex;
            } else {
                throw new SymmetricException(ex);
            }
        } else if (ex instanceof ProtocolException) {
            log.error("Failed to process incoming batch from node '{}': {}{}", sourceNodeId, ex.getClass().getSimpleName(),
                    StringUtils.isNotBlank(ex.getMessage()) ? ": " + ex.getMessage() : "");
        } else if (ex instanceof StagingLowFreeSpace) {
            log.error("Loading is disabled because disk is almost full: {}", ex.getMessage());
        } else if (ex instanceof FileNotFoundException && parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED)) {
            log.error("Cannot write to staging directory, check file permissions.", ex);
        } else if (!(ex instanceof ConflictException) && !(ex instanceof SqlException) && !(ex instanceof CancellationException)) {
            log.error("Failed to process incoming batch from node '" + sourceNodeId + "'", ex);
        } else {
            log.debug("Failed to process incoming batch from node '" + sourceNodeId + "'", ex);
        }
    }

    protected IDataWriter buildDataWriter(ProcessInfo processInfo, String sourceNodeId,
            String channelId, long batchId, boolean isRetry) {
        TransformTable[] transforms = null;
        NodeGroupLink link = null;
        List<ResolvedData> resolvedDatas = new ArrayList<ResolvedData>();
        List<IDatabaseWriterFilter> filters = extensionService
                .getExtensionPointList(IDatabaseWriterFilter.class);
        List<IDatabaseWriterFilter> dynamicFilters = filters;
        List<IDatabaseWriterErrorHandler> errorHandlers = extensionService
                .getExtensionPointList(IDatabaseWriterErrorHandler.class);
        List<IDatabaseWriterErrorHandler> dynamicErrorHandlers = errorHandlers;
        if (sourceNodeId != null) {
            Node sourceNode = nodeService.findNode(sourceNodeId, true);
            if (sourceNode != null) {
                link = new NodeGroupLink(sourceNode.getNodeGroupId(),
                        parameterService.getNodeGroupId());
            }
            Map<LoadFilterType, Map<String, List<LoadFilter>>> loadFilters = loadFilterService
                    .findLoadFiltersFor(link, true);
            List<DynamicDatabaseWriterFilter> databaseWriterFilters = DynamicDatabaseWriterFilter
                    .getDatabaseWriterFilters(engine, loadFilters);
            if (loadFilters != null && loadFilters.size() > 0) {
                dynamicFilters = new ArrayList<IDatabaseWriterFilter>(filters.size() + 1);
                dynamicFilters.addAll(filters);
                dynamicFilters.addAll(databaseWriterFilters);
                dynamicErrorHandlers = new ArrayList<IDatabaseWriterErrorHandler>(
                        errorHandlers.size() + 1);
                dynamicErrorHandlers.addAll(errorHandlers);
                dynamicErrorHandlers.addAll(databaseWriterFilters);
            }
            if (isRetry) {
                List<IncomingError> incomingErrors = getIncomingErrors(batchId, sourceNodeId);
                for (IncomingError incomingError : incomingErrors) {
                    if (incomingError.isResolveIgnore()
                            || StringUtils.isNotBlank(incomingError.getResolveData())) {
                        resolvedDatas.add(new ResolvedData(incomingError.getFailedRowNumber(),
                                incomingError.getResolveData(), incomingError.isResolveIgnore()));
                    }
                }
            }
        }
        Channel channel = configurationService.getChannel(channelId);
        List<TransformTableNodeGroupLink> transformsList = transformService.findTransformsFor(link, TransformPoint.LOAD);
        transforms = transformsList != null ? transformsList.toArray(new TransformTable[transformsList.size()]) : null;
        TransformWriter transformWriter = new TransformWriter(this.engine.getSymmetricDialect().getTargetPlatform(), TransformPoint.LOAD, null,
                transformService.getColumnTransforms(), transforms);
        IDataWriter targetWriter = getFactory(channel).getDataWriter(sourceNodeId, channel,
                this.engine.getSymmetricDialect(), transformWriter, dynamicFilters, dynamicErrorHandlers,
                getConflictSettingsNodeGroupLinks(link, false), resolvedDatas);
        transformWriter.setNestedWriter(new ProcessInfoDataWriter(targetWriter, processInfo));
        return transformWriter;
    }

    protected IDataLoaderFactory getFactory(Channel channel) {
        String dataLoaderType = "default";
        IDataLoaderFactory factory = null;
        if (channel != null) {
            dataLoaderType = filterDataLoaderType(channel.getDataLoaderType());
        }
        Map<String, IDataLoaderFactory> dataLoaderFactories = getDataLoaderFactories();
        factory = dataLoaderFactories.get(dataLoaderType);
        if (factory == null) {
            log.warn(
                    "Could not find a data loader factory of type '{}'.  Using the 'default' data loader.",
                    dataLoaderType);
            factory = dataLoaderFactories.get("default");
        }
        if (!factory.isPlatformSupported(symmetricDialect.getTargetPlatform())) {
            log.warn(
                    "The current platform does not support a data loader type of '{}'.  Using the 'default' data loader.",
                    dataLoaderType);
            factory = dataLoaderFactories.get("default");
        }
        return factory;
    }

    public List<ConflictNodeGroupLink> getConflictSettingsNodeGroupLinks() {
        List<ConflictNodeGroupLink> list = new ArrayList<DataLoaderService.ConflictNodeGroupLink>();
        list = sqlTemplate.query(getSql("selectConflictSettingsSql"),
                new ConflictSettingsNodeGroupLinkMapper());
        return list;
    }

    public void clearCache() {
        synchronized (this) {
            cacheManager.flushConflictSettingsNodeGroupLinks();
        }
    }

    @Override
    public List<ConflictNodeGroupLink> getConflictSettingsNodeGroupLinks(NodeGroupLink link, boolean refreshCache) {
        if (link != null) {
            return cacheManager.getConflictSettingsNodeGroupLinks(link, refreshCache);
        } else {
            return new ArrayList<DataLoaderService.ConflictNodeGroupLink>(0);
        }
    }

    @Override
    public List<ConflictNodeGroupLink> getConflictSettinsNodeGroupLinksFromDb(NodeGroupLink link) {
        return sqlTemplate.query(
                getSql("selectConflictSettingsSql",
                        " where source_node_group_id=? and target_node_group_id=?"),
                new ConflictSettingsNodeGroupLinkMapper(), link.getSourceNodeGroupId(),
                link.getTargetNodeGroupId());
    }

    public void delete(ConflictNodeGroupLink settings) {
        delete(settings.getConflictId());
    }

    private void delete(String id) {
        sqlTemplate.update(getSql("deleteConflictSettingsSql"), id);
    }

    public void deleteAllConflicts() {
        sqlTemplate.update(getSql("deleteAllConflictSettingsSql"));
    }

    public void save(ConflictNodeGroupLink setting) {
        cacheManager.flushConflictSettingsNodeGroupLinks();
        if (sqlTemplate.update(
                getSql("updateConflictSettingsSql"),
                new Object[] { setting.getNodeGroupLink().getSourceNodeGroupId(),
                        setting.getNodeGroupLink().getTargetNodeGroupId(),
                        setting.getTargetChannelId(), setting.getTargetCatalogName(),
                        setting.getTargetSchemaName(), setting.getTargetTableName(),
                        setting.getDetectType().name(), setting.getResolveType().name(),
                        setting.getPingBack().name(), setting.isResolveChangesOnly() ? 1 : 0,
                        setting.isResolveRowOnly() ? 1 : 0, setting.getDetectExpression(),
                        setting.getLastUpdateBy(), new Date(), setting.getConflictId() }, new int[] {
                                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER,
                                Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR }) <= 0) {
            sqlTemplate.update(
                    getSql("insertConflictSettingsSql"),
                    new Object[] { setting.getNodeGroupLink().getSourceNodeGroupId(),
                            setting.getNodeGroupLink().getTargetNodeGroupId(),
                            setting.getTargetChannelId(), setting.getTargetCatalogName(),
                            setting.getTargetSchemaName(), setting.getTargetTableName(),
                            setting.getDetectType().name(), setting.getResolveType().name(),
                            setting.getPingBack().name(), setting.isResolveChangesOnly() ? 1 : 0,
                            setting.isResolveRowOnly() ? 1 : 0, setting.getDetectExpression(), new Date(),
                            setting.getLastUpdateBy(), new Date(), setting.getConflictId() }, new int[] {
                                    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                                    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                                    Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.VARCHAR,
                                    Types.TIMESTAMP, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR });
        }
    }

    public void saveAsCopy(ConflictNodeGroupLink settings) {
        String newId = settings.getConflictId();
        List<ConflictNodeGroupLink> conflicts = sqlTemplate.query(
                getSql("selectConflictSettingsSql", "whereConflictIdLike"), new ConflictSettingsNodeGroupLinkMapper(), newId + "%");
        List<String> ids = conflicts.stream().map(ConflictNodeGroupLink::getConflictId).collect(Collectors.toList());
        String suffix = "";
        for (int i = 2; ids.contains(newId + suffix); i++) {
            suffix = "_" + i;
        }
        settings.setConflictId(newId + suffix);
        save(settings);
    }

    public void rename(String oldId, ConflictNodeGroupLink setting) {
        delete(oldId);
        save(setting);
    }

    public List<IncomingError> getIncomingErrors(long batchId, String nodeId) {
        return sqlTemplate.query(getSql("selectIncomingErrorSql"), new IncomingErrorMapper(),
                batchId, nodeId);
    }

    public IncomingError getIncomingError(long batchId, String nodeId, long rowNumber) {
        return sqlTemplate.queryForObject(getSql("selectIncomingErrorSql") + " and failed_row_number = ?",
                new IncomingErrorMapper(), batchId, nodeId, rowNumber);
    }

    public IncomingError getCurrentIncomingError(long batchId, String nodeId) {
        return sqlTemplate.queryForObject(getSql("selectCurrentIncomingErrorSql"),
                new IncomingErrorMapper(), batchId, nodeId);
    }

    public void insertIncomingError(IncomingError incomingError) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            insertIncomingError(transaction, incomingError);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    public void insertIncomingError(ISqlTransaction transaction, IncomingError incomingError) {
        if (StringUtils.isNotBlank(incomingError.getNodeId()) && incomingError.getBatchId() >= 0) {
            boolean alreadyExists = false;
            if (symmetricDialect.getDriverName() != null
                    && symmetricDialect.getDriverName().equalsIgnoreCase(DatabaseNamesConstants.OPENEDGE)) {
                if (getIncomingError(incomingError.getBatchId(), incomingError.getNodeId(),
                        incomingError.getFailedRowNumber()) != null) {
                    alreadyExists = true;
                }
            }
            if (!alreadyExists) {
                transaction.prepareAndExecute(
                        getSql("insertIncomingErrorSql"),
                        new Object[] { incomingError.getBatchId(), incomingError.getNodeId(),
                                incomingError.getFailedRowNumber(),
                                incomingError.getFailedLineNumber(),
                                incomingError.getTargetCatalogName(),
                                incomingError.getTargetSchemaName(),
                                incomingError.getTargetTableName(),
                                incomingError.getEventType().getCode(),
                                incomingError.getBinaryEncoding().name(),
                                incomingError.getColumnNames(),
                                incomingError.getPrimaryKeyColumnNames(), incomingError.getRowData(),
                                incomingError.getOldData(), incomingError.getCurData(),
                                incomingError.getResolveData(),
                                incomingError.isResolveIgnore() ? 1 : 0, incomingError.getConflictId(),
                                incomingError.getCreateTime(), incomingError.getLastUpdateBy(),
                                incomingError.getLastUpdateTime() }, new int[] { Types.BIGINT,
                                        Types.VARCHAR, Types.BIGINT, Types.BIGINT, Types.VARCHAR,
                                        Types.VARCHAR, Types.VARCHAR, Types.CHAR, Types.VARCHAR, Types.VARCHAR,
                                        Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                                        Types.VARCHAR, Types.SMALLINT, Types.VARCHAR, Types.TIMESTAMP,
                                        Types.VARCHAR, Types.TIMESTAMP });
            }
        }
    }

    public void updateIncomingError(IncomingError incomingError) {
        sqlTemplate.update(getSql("updateIncomingErrorSql"), incomingError.getResolveData(),
                incomingError.isResolveIgnore() ? 1 : 0, incomingError.getBatchId(),
                incomingError.getNodeId(), incomingError.getFailedRowNumber());
    }

    /**
     * Used for unit tests
     */
    protected void setTransportManager(ITransportManager transportManager) {
        this.transportManager = transportManager;
    }

    class ConflictSettingsNodeGroupLinkMapper implements ISqlRowMapper<ConflictNodeGroupLink> {
        public ConflictNodeGroupLink mapRow(Row rs) {
            ConflictNodeGroupLink setting = new ConflictNodeGroupLink();
            setting.setNodeGroupLink(configurationService.getNodeGroupLinkFor(
                    rs.getString("source_node_group_id"), rs.getString("target_node_group_id"),
                    false));
            setting.setTargetChannelId(StringUtils.trimToNull(rs.getString("target_channel_id")));
            setting.setTargetCatalogName(StringUtils.trimToNull(rs.getString("target_catalog_name")));
            setting.setTargetSchemaName(StringUtils.trimToNull(rs.getString("target_schema_name")));
            setting.setTargetTableName(StringUtils.trimToNull(rs.getString("target_table_name")));
            setting.setDetectType(DetectConflict.valueOf(rs.getString("detect_type").toUpperCase()));
            setting.setResolveType(ResolveConflict.valueOf(rs.getString("resolve_type")
                    .toUpperCase()));
            setting.setPingBack(PingBack.valueOf(rs.getString("ping_back")));
            setting.setResolveChangesOnly(rs.getBoolean("resolve_changes_only"));
            setting.setResolveRowOnly(rs.getBoolean("resolve_row_only"));
            setting.setDetectExpression(StringUtils.trimToNull(rs.getString("detect_expression")));
            setting.setLastUpdateBy(rs.getString("last_update_by"));
            setting.setConflictId(rs.getString("conflict_id"));
            setting.setCreateTime(rs.getDateTime("create_time"));
            setting.setLastUpdateTime(rs.getDateTime("last_update_time"));
            return setting;
        }
    }

    static class IncomingErrorMapper implements ISqlRowMapper<IncomingError> {
        public IncomingError mapRow(Row rs) {
            IncomingError incomingError = new IncomingError();
            incomingError.setBatchId(rs.getLong("batch_id"));
            incomingError.setNodeId(rs.getString("node_id"));
            incomingError.setFailedRowNumber(rs.getLong("failed_row_number"));
            incomingError.setFailedLineNumber(rs.getLong("failed_line_number"));
            incomingError.setTargetCatalogName(rs.getString("target_catalog_name"));
            incomingError.setTargetSchemaName(rs.getString("target_schema_name"));
            incomingError.setTargetTableName(rs.getString("target_table_name"));
            incomingError.setEventType(DataEventType.getEventType(rs.getString("event_type")));
            incomingError
                    .setBinaryEncoding(BinaryEncoding.valueOf(rs.getString("binary_encoding")));
            incomingError.setColumnNames(rs.getString("column_names"));
            incomingError.setPrimaryKeyColumnNames(rs.getString("pk_column_names"));
            incomingError.setRowData(rs.getString("row_data"));
            incomingError.setOldData(rs.getString("old_data"));
            incomingError.setCurData(rs.getString("cur_data"));
            incomingError.setResolveData(rs.getString("resolve_data"));
            incomingError.setResolveIgnore(rs.getBoolean("resolve_ignore"));
            incomingError.setConflictId(rs.getString("conflict_id"));
            incomingError.setCreateTime(rs.getDateTime("create_time"));
            incomingError.setLastUpdateBy(rs.getString("last_update_by"));
            incomingError.setLastUpdateTime(rs.getDateTime("last_update_time"));
            return incomingError;
        }
    }

    class LoadIntoDatabaseOnArrivalListener implements IProtocolDataWriterListener {
        private ManageIncomingBatchListener listener;
        private long batchStartsToArriveTimeInMs;
        private String sourceNodeId;
        private ProcessInfo transferInfo;
        private ProcessInfo loadInfo;
        private ExecutorService executor;
        private List<Future<IncomingBatch>> futures = new ArrayList<Future<IncomingBatch>>();
        private boolean isError;

        public LoadIntoDatabaseOnArrivalListener(ProcessInfo transferInfo, String sourceNodeId,
                ManageIncomingBatchListener listener, ExecutorService executor) {
            this.sourceNodeId = sourceNodeId;
            this.listener = listener;
            this.executor = executor;
            this.transferInfo = transferInfo;
        }

        public void start(DataContext ctx, Batch batch) {
            batchStartsToArriveTimeInMs = System.currentTimeMillis();
        }

        protected ProtocolDataReader buildDataReader(final Batch batchInStaging, final IStagedResource resource) {
            return new ProtocolDataReader(BatchType.LOAD, batchInStaging.getTargetNodeId(), resource) {
                @Override
                public Table nextTable() {
                    Table table = super.nextTable();
                    if (table != null && listener.currentBatch != null) {
                        listener.currentBatch.incrementTableCount(table.getNameLowerCase());
                    }
                    return table;
                }

                public Batch nextBatch() {
                    Batch nextBatch = super.nextBatch();
                    if (nextBatch != null) {
                        nextBatch.setStatistics(batchInStaging.getStatistics());
                    }
                    return nextBatch;
                }
            };
        }

        public void end(final DataContext ctx, final Batch batchInStaging, final IStagedResource resource) {
            final long networkMillis = System.currentTimeMillis() - batchStartsToArriveTimeInMs;
            Callable<IncomingBatch> loadBatchFromStage = new Callable<IncomingBatch>() {
                public IncomingBatch call() throws Exception {
                    IncomingBatch incomingBatch = null;
                    DataProcessor processor = null;
                    MDC.put("engineName", engine.getParameterService().getEngineName());
                    if (!isError && resource != null && resource.exists()) {
                        try {
                            loadInfo = statisticManager.newProcessInfo(new ProcessInfoKey(transferInfo.getSourceNodeId(),
                                    transferInfo.getQueue(), transferInfo.getTargetNodeId(), transferInfo.getProcessType() == PULL_JOB_TRANSFER ? PULL_JOB_LOAD
                                            : PUSH_HANDLER_LOAD));
                            loadInfo.setCurrentLoadId(transferInfo.getCurrentLoadId());
                            if (batchInStaging.getStatistics() != null) {
                                loadInfo.setTotalDataCount(batchInStaging.getStatistics().get(DataReaderStatistics.DATA_ROW_COUNT));
                                loadInfo.setCurrentLoadId(batchInStaging.getStatistics().get(DataReaderStatistics.LOAD_ID));
                            }
                            loadInfo.setStatus(ProcessInfo.ProcessStatus.LOADING);
                            ProtocolDataReader reader = buildDataReader(batchInStaging, resource);
                            processor = new DataProcessor(reader, null, listener, "data load from stage") {
                                @Override
                                protected IDataWriter chooseDataWriter(Batch batch) {
                                    boolean isRetry = ((ManageIncomingBatchListener) listener).getCurrentBatch().isRetry();
                                    return buildDataWriter(loadInfo, sourceNodeId, batch.getChannelId(), batch.getBatchId(), isRetry);
                                }
                            };
                            processor.process(ctx);
                            if (loadInfo.getCurrentBatchCount() == 0) {
                                loadInfo.setStatus(ProcessStatus.OK);
                            }
                        } catch (Exception e) {
                            if (ctx.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE) != null && ctx.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE).equals(
                                    "bulk")) {
                                log.debug("Bulk loader failed : ", e);
                                log.info("Bulk loader failed in class {} with message: {}", e.getClass().getName(), e.getMessage());
                                ctx.put(ContextConstants.CONTEXT_BULK_WRITER_TO_USE, "default");
                                ctx.setLastError(null);
                                if (listener.currentBatch != null) {
                                    listener.currentBatch.setStatus(Status.OK);
                                }
                                processor.setDataReader(buildDataReader(batchInStaging, resource));
                                try {
                                    listener.getBatchesProcessed().remove(listener.currentBatch);
                                    processor.process(ctx);
                                } catch (Exception retryException) {
                                    isError = true;
                                    incomingBatch = listener.currentBatch;
                                    incomingBatch.setStatus(Status.ER);
                                    incomingBatch.setErrorFlag(true);
                                    incomingBatchService.updateIncomingBatch(incomingBatch);
                                    throw e;
                                }
                            } else {
                                isError = true;
                                if (listener.isErrorSuppressed()) {
                                    loadInfo.setStatus(ProcessStatus.OK);
                                    loadInfo.setCurrentBatchId(0);
                                }
                                if (listener.currentBatch != null && listener.currentBatch.getSqlCode() == ErrorConstants.PROTOCOL_VIOLATION_CODE) {
                                    log.info("The batch {} may be corrupt in staging, so removing it.", batchInStaging.getNodeBatchId());
                                    resource.delete();
                                    incomingBatch = listener.currentBatch;
                                } else {
                                    throw e;
                                }
                            }
                        } finally {
                            incomingBatch = listener.currentBatch;
                            if (incomingBatch != null) {
                                incomingBatch.setNetworkMillis(networkMillis);
                                if (batchInStaging.isIgnored()) {
                                    incomingBatch.incrementIgnoreCount();
                                }
                            }
                            resource.setState(State.DONE);
                            if (!resource.isFileResource()) {
                                resource.delete();
                            }
                        }
                    } else if (resource == null || !resource.exists()) {
                        log.info("The batch {} was missing in staging.  Setting status to resend.", batchInStaging.getNodeBatchId());
                        incomingBatch = new IncomingBatch(batchInStaging);
                        incomingBatch.setStatus(Status.RS);
                        incomingBatchService.updateIncomingBatch(incomingBatch);
                    }
                    return incomingBatch;
                }
            };
            if (resource == null) {
                IncomingBatch incomingBatch = new IncomingBatch(batchInStaging);
                listener.getBatchesProcessed().add(incomingBatch);
                if (incomingBatchService.acquireIncomingBatch(incomingBatch)) {
                    log.info("Unable to retry batch {} because it's not in staging.  Setting status to resend.", batchInStaging.getNodeBatchId());
                    incomingBatch.setStatus(Status.RS);
                    incomingBatchService.updateIncomingBatch(incomingBatch);
                }
                isError = true;
            } else {
                futures.add(executor.submit(loadBatchFromStage));
            }
        }

        public boolean isDone() throws Throwable {
            boolean isDone = true;
            for (Future<IncomingBatch> future : futures) {
                if (future.isDone()) {
                    try {
                        future.get();
                    } catch (ExecutionException e) {
                        throw e.getCause() != null ? e.getCause() : e;
                    }
                } else {
                    isDone = false;
                }
            }
            return isDone;
        }
    }

    public static class ConflictNodeGroupLink extends Conflict implements IModelObject {
        private static final long serialVersionUID = 1L;
        protected NodeGroupLink nodeGroupLink;

        public void setNodeGroupLink(NodeGroupLink nodeGroupLink) {
            this.nodeGroupLink = nodeGroupLink;
        }

        public NodeGroupLink getNodeGroupLink() {
            return nodeGroupLink;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((getConflictId() == null) ? 0 : getConflictId().hashCode());
            result = prime * result + ((nodeGroupLink == null) ? 0 : nodeGroupLink.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ConflictNodeGroupLink other = (ConflictNodeGroupLink) obj;
            if (getConflictId() == null) {
                if (other.getConflictId() != null) {
                    return false;
                }
            } else if (!getConflictId().equals(other.getConflictId())) {
                return false;
            }
            if (nodeGroupLink == null) {
                if (other.nodeGroupLink != null) {
                    return false;
                }
            } else if (!nodeGroupLink.equals(other.nodeGroupLink)) {
                return false;
            }
            return true;
        }
    }

    public static String filterDataLoaderType(String dataLoaderType) {
        if (dataLoaderType == null) {
            return dataLoaderType;
        }
        // Check for deprecated data loaders and reset to just "bulk".
        if (dataLoaderType.equals("mysql_bulk")
                || dataLoaderType.equals("mssql_bulk")
                || dataLoaderType.equals("oracle_bulk")
                || dataLoaderType.equals("postgres_bulk")
                || dataLoaderType.equals("redshift_bulk")) {
            dataLoaderType = "bulk";
        }
        return dataLoaderType;
    }
}
