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

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jumpmind.symmetric.common.Constants.LOG_PROCESS_SUMMARY_THRESHOLD;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DdlBuilderFactory;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.mapper.LongMapper;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ConfigurationVersionHelper;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ErrorConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.ext.IReloadQueueThreadAssigner;
import org.jumpmind.symmetric.extract.ExtractDataReaderFactory;
import org.jumpmind.symmetric.extract.IExtractDataReaderFactory;
import org.jumpmind.symmetric.extract.MultiBatchStagingWriter;
import org.jumpmind.symmetric.extract.SelectFromSymDataSource;
import org.jumpmind.symmetric.extract.SelectFromTableEvent;
import org.jumpmind.symmetric.extract.SelectFromTableSource;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.DataProcessor;
import org.jumpmind.symmetric.io.data.IDataProcessorListener;
import org.jumpmind.symmetric.io.data.IDataReader;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.io.data.reader.DataReaderStatistics;
import org.jumpmind.symmetric.io.data.reader.ExtractDataReader;
import org.jumpmind.symmetric.io.data.reader.IExtractDataReaderSource;
import org.jumpmind.symmetric.io.data.reader.ProtocolDataReader;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformTable;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.symmetric.io.data.writer.ProtocolDataWriter;
import org.jumpmind.symmetric.io.data.writer.StagingDataWriter;
import org.jumpmind.symmetric.io.data.writer.StructureDataWriter;
import org.jumpmind.symmetric.io.data.writer.StructureDataWriter.PayloadType;
import org.jumpmind.symmetric.io.data.writer.TransformWriter;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.io.stage.StagingFileLock;
import org.jumpmind.symmetric.io.stage.StagingLowFreeSpace;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ChannelMap;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.ExtractRequest.ExtractStatus;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.OutgoingBatchWithPayload;
import org.jumpmind.symmetric.model.OutgoingBatches;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoDataWriter;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IInitialLoadService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeCommunicationService.INodeCommunicationExecutor;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.BatchBufferedWriter;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.jumpmind.symmetric.util.QueueThread;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.CustomizableThreadFactory;
import org.jumpmind.util.ExceptionUtils;
import org.jumpmind.util.FormatUtils;
import org.jumpmind.util.FutureImpl;
import org.jumpmind.util.Statistics;
import org.slf4j.MDC;

/**
 * @see IDataExtractorService
 */
public class DataExtractorService extends AbstractService implements IDataExtractorService,
        INodeCommunicationExecutor {
    final static long MS_PASSED_BEFORE_BATCH_REQUERIED = 5000;

    protected enum ExtractMode {
        FOR_SYM_CLIENT, FOR_PAYLOAD_CLIENT, EXTRACT_ONLY
    };

    protected ISymmetricEngine engine;
    IOutgoingBatchService outgoingBatchService;
    private IRouterService routerService;
    private IInitialLoadService initialLoadService;
    private IConfigurationService configurationService;
    private ITriggerRouterService triggerRouterService;
    private ITransformService transformService;
    private ISequenceService sequenceService;
    private IDataService dataService;
    private INodeService nodeService;
    IStatisticManager statisticManager;
    private IStagingManager stagingManager;
    private INodeCommunicationService nodeCommunicationService;
    private IClusterService clusterService;
    private Map<String, BatchLock> locks = new ConcurrentHashMap<String, BatchLock>();
    private CustomizableThreadFactory threadPoolFactory;

    public DataExtractorService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        this.outgoingBatchService = engine.getOutgoingBatchService();
        this.routerService = engine.getRouterService();
        this.dataService = engine.getDataService();
        this.configurationService = engine.getConfigurationService();
        this.triggerRouterService = engine.getTriggerRouterService();
        this.nodeService = engine.getNodeService();
        this.transformService = engine.getTransformService();
        this.statisticManager = engine.getStatisticManager();
        this.stagingManager = engine.getStagingManager();
        this.nodeCommunicationService = engine.getNodeCommunicationService();
        this.clusterService = engine.getClusterService();
        this.sequenceService = engine.getSequenceService();
        this.initialLoadService = engine.getInitialLoadService();
        setSqlMap(new DataExtractorServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
    }

    /**
     * Extract the SymmetricDS configuration for the passed in {@link Node}.
     */
    @Override
    public void extractConfigurationStandalone(Node targetNode, Writer writer, String... tablesToExclude) {
        Node sourceNode = nodeService.findIdentity();
        if (targetNode != null && sourceNode != null) {
            Batch batch = new Batch(BatchType.EXTRACT, Constants.VIRTUAL_BATCH_FOR_REGISTRATION, Constants.CHANNEL_CONFIG, symmetricDialect.getBinaryEncoding(),
                    sourceNode.getNodeId(), targetNode.getNodeId(), false);
            NodeGroupLink nodeGroupLink = new NodeGroupLink(parameterService.getNodeGroupId(), targetNode.getNodeGroupId());
            List<TriggerRouter> configTriggerRouters = triggerRouterService.buildTriggerRoutersForSymmetricTables(StringUtils.isBlank(targetNode
                    .getSymmetricVersion()) ? Version.version() : targetNode.getSymmetricVersion(), nodeGroupLink, tablesToExclude);
            List<SelectFromTableEvent> initialLoadEvents = new ArrayList<SelectFromTableEvent>(configTriggerRouters.size() * 2);
            ConfigurationVersionHelper helper = new ConfigurationVersionHelper(symmetricDialect.getTablePrefix(), targetNode);
            List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
            List<TriggerHistory> triggerHistories = new ArrayList<TriggerHistory>();
            for (int i = 0; i < configTriggerRouters.size(); i++) {
                TriggerRouter triggerRouter = configTriggerRouters.get(i);
                Trigger trigger = triggerRouter.getTrigger();
                String channelId = trigger.getChannelId();
                String tableName = trigger.getSourceTableName();
                if ((Constants.CHANNEL_CONFIG.equals(channelId) || Constants.CHANNEL_SYSTEM.equals(channelId) || Constants.CHANNEL_HEARTBEAT.equals(channelId))
                        && helper.shouldSendTable(tableName)) {
                    TriggerHistory triggerHistory = triggerRouterService.getNewestTriggerHistoryForTrigger(trigger.getTriggerId(), null, null, tableName);
                    if (triggerHistory == null) {
                        Table table = platform.getTableFromCache(trigger.getSourceCatalogName(), trigger.getSourceSchemaName(), tableName, false);
                        if (table == null) {
                            throw new IllegalStateException("Could not find a required table: " + tableName);
                        }
                        triggerHistory = new TriggerHistory(table, trigger, symmetricDialect.getTriggerTemplate());
                        triggerHistory.setTriggerHistoryId(Integer.MAX_VALUE - i);
                    }
                    triggerRouters.add(triggerRouter);
                    triggerHistories.add(triggerHistory);
                }
            }
            for (int i = triggerRouters.size() - 1; i >= 0; i--) {
                TriggerRouter triggerRouter = triggerRouters.get(i);
                TriggerHistory triggerHistory = triggerHistories.get(i);
                StringBuilder sql = new StringBuilder(symmetricDialect.createPurgeSqlFor(targetNode, triggerRouter, triggerHistory));
                addPurgeCriteriaToConfigurationTables(targetNode, triggerRouter.getTrigger().getSourceTableName(), sql);
                Data data = new Data(1, null, sql.toString(), DataEventType.SQL, triggerHistory.getSourceTableName(), null, triggerHistory, triggerRouter
                        .getTrigger().getChannelId(), null, null);
                initialLoadEvents.add(new SelectFromTableEvent(data, triggerRouter));
            }
            for (int i = 0; i < triggerRouters.size(); i++) {
                TriggerRouter triggerRouter = triggerRouters.get(i);
                TriggerHistory triggerHistory = triggerHistories.get(i);
                Table table = symmetricDialect.getPlatform().getTableFromCache(triggerHistory.getSourceCatalogName(), triggerHistory.getSourceSchemaName(),
                        triggerHistory.getSourceTableName(), false);
                String initialLoadSql = "1=1 order by ";
                String quote = platform.getDdlBuilder().getDatabaseInfo().getDelimiterToken();
                Column[] pkColumns = table.getPrimaryKeyColumns();
                for (int j = 0; j < pkColumns.length; j++) {
                    if (j > 0) {
                        initialLoadSql += ", ";
                    }
                    initialLoadSql += quote + pkColumns[j].getName() + quote;
                }
                if (!triggerRouter.getTrigger().getSourceTableName().endsWith(TableConstants.SYM_NODE_IDENTITY)) {
                    initialLoadEvents.add(new SelectFromTableEvent(targetNode, triggerRouter, triggerHistory, initialLoadSql));
                } else {
                    Data data = new Data(1, null, targetNode.getNodeId(), DataEventType.INSERT, triggerHistory.getSourceTableName(), null, triggerHistory,
                            triggerRouter.getTrigger().getChannelId(), null, null);
                    initialLoadEvents.add(new SelectFromTableEvent(data, triggerRouter));
                }
            }
            SelectFromTableSource source = new SelectFromTableSource(engine, batch, initialLoadEvents);
            source.setConfiguration(true);
            ExtractDataReader dataReader = new ExtractDataReader(symmetricDialect.getPlatform(), source, symmetricDialect.getTargetPlatform());
            ProtocolDataWriter dataWriter = new ProtocolDataWriter(nodeService.findIdentityNodeId(), writer, targetNode.requires13Compatiblity(), false, false);
            List<TransformTableNodeGroupLink> transformsList = transformService.getConfigExtractTransforms(nodeGroupLink);
            TransformTable[] transforms = transformsList.toArray(new TransformTable[transformsList.size()]);
            TransformWriter transformWriter = new TransformWriter(symmetricDialect.getTargetPlatform(), TransformPoint.EXTRACT, dataWriter, transformService
                    .getColumnTransforms(), transforms);
            DataContext ctx = new DataContext();
            DataProcessor processor = new DataProcessor(dataReader, transformWriter, "configuration extract");
            ctx.put(Constants.DATA_CONTEXT_TARGET_NODE, targetNode);
            ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE, sourceNode);
            ctx.put(Constants.DATA_CONTEXT_ENGINE, engine);
            processor.process(ctx);
            if (triggerRouters.size() == 0) {
                log.error("{} attempted registration, but was sent an empty configuration", targetNode);
            }
        }
    }

    private void addPurgeCriteriaToConfigurationTables(Node targetNode, String sourceTableName, StringBuilder sql) {
        if ((TableConstants
                .getTableName(parameterService.getTablePrefix(), TableConstants.SYM_NODE)
                .equalsIgnoreCase(sourceTableName))
                || TableConstants.getTableName(parameterService.getTablePrefix(),
                        TableConstants.SYM_NODE_SECURITY).equalsIgnoreCase(sourceTableName)) {
            sql.append(String.format(" where created_at_node_id != '%s' or created_at_node_id is null", targetNode.getNodeId()));
        }
    }

    private List<OutgoingBatch> filterBatchesForExtraction(OutgoingBatches batches,
            ChannelMap suspendIgnoreChannelsList, String queue, Node targetNode) {
        if (parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)) {
            List<Channel> fileSyncChannels = configurationService.getFileSyncChannels();
            for (Channel channel : fileSyncChannels) {
                batches.filterBatchesForChannel(channel);
            }
        }
        // We now have either our local suspend/ignore list, or the combined
        // remote send/ignore list and our local list (along with a
        // reservation, if we go this far...)
        // Now, we need to skip the suspended channels and ignore the
        // ignored ones by ultimately setting the status to ignored and
        // updating them.
        List<OutgoingBatch> ignoredBatches = batches
                .filterBatchesForChannels(suspendIgnoreChannelsList.getIgnoreChannels());
        // Finally, update the ignored outgoing batches such that they
        // will be skipped in the future.
        for (OutgoingBatch batch : ignoredBatches) {
            batch.setStatus(OutgoingBatch.Status.OK);
            batch.incrementIgnoreCount();
            if (log.isDebugEnabled()) {
                log.debug("Batch {} is being ignored", batch.getBatchId());
            }
        }
        outgoingBatchService.updateOutgoingBatches(ignoredBatches);
        batches.filterBatchesForChannels(suspendIgnoreChannelsList.getSuspendChannels());
        // Remove non-load batches so that an initial load finishes before
        // any other batches are loaded.
        if (parameterService.is(ParameterConstants.INITIAL_LOAD_BLOCK_CHANNELS, true) && !Constants.QUEUE_RELOAD.equals(QueueThread.getQueueName(queue))) {
            if (batches.containsLoadBatches()) {
                if (!(parameterService.is(ParameterConstants.INITIAL_LOAD_UNBLOCK_CHANNELS_ON_ERROR, true) && batches.containsBatchesInError())) {
                    batches.removeNonLoadBatches();
                    log.info("Removing non-load batches from queue {} for target node", queue, targetNode);
                }
            } else {
                NodeSecurity nodeSecurity = nodeService.findNodeSecurity(targetNode.getNodeId(), true);
                if (nodeSecurity != null) {
                    long loadId = 0;
                    if (nodeSecurity.getInitialLoadTime() != null && nodeSecurity.getInitialLoadEndTime() == null) {
                        loadId = nodeSecurity.getInitialLoadId();
                    } else if (nodeSecurity.getPartialLoadTime() != null && nodeSecurity.getPartialLoadEndTime() == null) {
                        loadId = nodeSecurity.getPartialLoadId();
                    }
                    if (loadId != 0) {
                        TableReloadStatus status = dataService.getTableReloadStatusByLoadIdAndSourceNodeId(loadId, engine.getNodeId());
                        if (status != null && status.getDataBatchLoaded() < status.getDataBatchCount()) {
                            batches.removeNonLoadBatches();
                            log.info("Removing non-load batches from queue {} for target node {} because load ID {} was found", queue, targetNode,
                                    status.getLoadId());
                        }
                    }
                }
            }
        }
        return batches.getBatches();
    }

    @Override
    public List<OutgoingBatchWithPayload> extractToPayload(ProcessInfo processInfo,
            Node targetNode, PayloadType payloadType, boolean useJdbcTimestampFormat,
            boolean useUpsertStatements, boolean useDelimiterIdentifiers) {
        OutgoingBatches batches = outgoingBatchService.getOutgoingBatches(targetNode.getNodeId(),
                false);
        if (batches.containsBatches()) {
            ChannelMap channelMap = configurationService.getSuspendIgnoreChannelLists(targetNode
                    .getNodeId());
            List<OutgoingBatch> activeBatches = filterBatchesForExtraction(batches, channelMap, null, targetNode);
            if (activeBatches.size() > 0) {
                IDdlBuilder builder = DdlBuilderFactory.getInstance().create(targetNode.getDatabaseType());
                if (builder == null) {
                    throw new IllegalStateException(
                            "Could not find a ddl builder registered for the database type of "
                                    + targetNode.getDatabaseType()
                                    + ".  Please check the database type setting for node '"
                                    + targetNode.getNodeId() + "'");
                }
                StructureDataWriter writer = new StructureDataWriter(
                        symmetricDialect.getPlatform(), targetNode.getDatabaseType(), payloadType,
                        useDelimiterIdentifiers, symmetricDialect.getBinaryEncoding(),
                        useJdbcTimestampFormat, useUpsertStatements);
                List<OutgoingBatch> extractedBatches = extract(processInfo, targetNode,
                        activeBatches, writer, null, ExtractMode.FOR_PAYLOAD_CLIENT);
                List<OutgoingBatchWithPayload> batchesWithPayload = new ArrayList<OutgoingBatchWithPayload>();
                for (OutgoingBatch batch : extractedBatches) {
                    OutgoingBatchWithPayload batchWithPayload = new OutgoingBatchWithPayload(batch,
                            payloadType);
                    batchWithPayload.setPayload(writer.getPayloadMap().get(batch.getBatchId()));
                    batchWithPayload.setPayloadType(payloadType);
                    batchesWithPayload.add(batchWithPayload);
                }
                return batchesWithPayload;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public List<OutgoingBatch> extract(ProcessInfo extractInfo, Node targetNode,
            IOutgoingTransport transport) {
        return extract(extractInfo, targetNode, null, transport);
    }

    @Override
    public List<OutgoingBatch> extract(ProcessInfo extractInfo, Node targetNode, String queue,
            IOutgoingTransport transport) {
        /*
         * make sure that data is routed before extracting if the route job is not configured to start automatically
         */
        String startRouteJob = parameterService.getString(ParameterConstants.START_ROUTE_JOB_38);
        boolean startRoutingJob = false;
        if (StringUtils.isBlank(startRouteJob)) {
            startRoutingJob = parameterService.is(ParameterConstants.START_ROUTE_JOB);
        } else {
            startRoutingJob = parameterService.is(ParameterConstants.START_ROUTE_JOB_38);
        }
        if (!startRoutingJob && parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)) {
            initialLoadService.queueLoads(true);
            routerService.routeData(true);
        }
        OutgoingBatches batches = loadPendingBatches(extractInfo, targetNode, queue, transport);
        if (batches != null && batches.containsBatches()) {
            ChannelMap channelMap = transport.getSuspendIgnoreChannelLists(configurationService, queue,
                    targetNode);
            List<OutgoingBatch> activeBatches = filterBatchesForExtraction(batches, channelMap, queue, targetNode);
            if (activeBatches.size() > 0) {
                BufferedWriter writer = transport.openWriter();
                IDataWriter dataWriter = new ProtocolDataWriter(nodeService.findIdentityNodeId(),
                        writer, targetNode.requires13Compatiblity(), targetNode.allowCaptureTimeInProtocol(),
                        parameterService.is(ParameterConstants.EXTRACT_ROW_CAPTURE_TIME, true));
                return extract(extractInfo, targetNode, activeBatches, dataWriter, writer, ExtractMode.FOR_SYM_CLIENT);
            }
        }
        return Collections.emptyList();
    }

    protected OutgoingBatches loadPendingBatches(ProcessInfo extractInfo, Node targetNode, String queue, IOutgoingTransport transport) {
        BufferedWriter writer = transport.getWriter();
        extractInfo.setStatus(ProcessStatus.QUERYING);
        Callable<OutgoingBatches> getOutgoingBatches = () -> {
            MDC.put("engineName", engine.getParameterService().getEngineName());
            OutgoingBatches batches = null;
            if (queue != null) {
                NodeGroupLink link = configurationService.getNodeGroupLinkFor(nodeService.findIdentity().getNodeGroupId(),
                        targetNode.getNodeGroupId(), false);
                if (link != null) {
                    NodeGroupLinkAction defaultAction = configurationService.getNodeGroupLinkFor(nodeService.findIdentity().getNodeGroupId(),
                            targetNode.getNodeGroupId(), false).getDataEventAction();
                    ProcessType processType = extractInfo.getKey().getProcessType();
                    NodeGroupLinkAction action = null;
                    if (processType.equals(ProcessType.PUSH_JOB_EXTRACT)) {
                        action = NodeGroupLinkAction.P;
                    } else if (processType.equals(ProcessType.PULL_HANDLER_EXTRACT)) {
                        action = NodeGroupLinkAction.W;
                    }
                    batches = outgoingBatchService.getOutgoingBatches(targetNode.getNodeId(), queue, action, defaultAction, false);
                } else {
                    log.error("Group link not found for " + nodeService.findIdentity().getNodeGroupId() +
                            " to " + targetNode.getNodeGroupId() + ".  Check that configuration matches on both nodes.");
                }
            } else {
                batches = outgoingBatchService.getOutgoingBatches(targetNode.getNodeId(), false);
            }
            return batches;
        };
        if (writer != null) {
            final boolean streamToFileEnabled = parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED);
            long keepAliveMillis = parameterService.getLong(ParameterConstants.DATA_LOADER_SEND_ACK_KEEPALIVE);
            Node sourceNode = nodeService.findIdentity();
            FutureTask<OutgoingBatches> getOutgoingBatchesTask = new FutureTask<OutgoingBatches>(getOutgoingBatches);
            ExecutorService executor = Executors.newFixedThreadPool(1);
            executor.execute(getOutgoingBatchesTask);
            try {
                while (true) {
                    try {
                        return getOutgoingBatchesTask.get(keepAliveMillis, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException ex) {
                        writeKeepAliveAck(writer, sourceNode, streamToFileEnabled);
                    } catch (Exception ex) {
                        throw new SymmetricException("Failed to execute getOutgoingBatchesTask ", ex);
                    }
                }
            } finally {
                executor.shutdown();
            }
        } else {
            try {
                return getOutgoingBatches.call();
            } catch (Exception ex) {
                throw new SymmetricException("Failed to execute getOutgoingBatchesTask ", ex);
            }
        }
    }

    /**
     * This method will extract an outgoing batch, but will not update the outgoing batch status
     */
    @Override
    public boolean extractOnlyOutgoingBatch(String nodeId, long batchId, Writer writer) {
        boolean extracted = false;
        Node targetNode = null;
        if (Constants.UNROUTED_NODE_ID.equals(nodeId)) {
            targetNode = new Node(nodeId, parameterService.getNodeGroupId());
        } else {
            targetNode = nodeService.findNode(nodeId, true);
        }
        if (targetNode != null) {
            OutgoingBatch batch = outgoingBatchService.findOutgoingBatch(batchId, nodeId);
            if (batch != null) {
                IDataWriter dataWriter = new ProtocolDataWriter(nodeService.findIdentityNodeId(),
                        writer, targetNode.requires13Compatiblity(), targetNode.allowCaptureTimeInProtocol(),
                        parameterService.is(ParameterConstants.EXTRACT_ROW_CAPTURE_TIME, true));
                List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>(1);
                batches.add(batch);
                batches = extract(new ProcessInfo(), targetNode, batches, dataWriter, null,
                        ExtractMode.EXTRACT_ONLY);
                extracted = batches.size() > 0;
            }
        }
        return extracted;
    }

    protected List<OutgoingBatch> extract(final ProcessInfo extractInfo, final Node targetNode,
            final List<OutgoingBatch> activeBatches, final IDataWriter dataWriter, final BufferedWriter writer, final ExtractMode mode) {
        if (activeBatches.size() > 0) {
            final List<OutgoingBatch> processedBatches = new ArrayList<OutgoingBatch>(activeBatches.size());
            Set<String> channelsProcessed = new HashSet<String>();
            long batchesSelectedAtMs = System.currentTimeMillis();
            OutgoingBatch currentBatch = null;
            ExecutorService executor = null;
            try {
                final boolean streamToFileEnabled = parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED);
                long keepAliveMillis = parameterService.getLong(ParameterConstants.DATA_LOADER_SEND_ACK_KEEPALIVE);
                Node sourceNode = nodeService.findIdentity();
                final FutureExtractStatus status = new FutureExtractStatus();
                if (this.threadPoolFactory == null) {
                    this.threadPoolFactory = new CustomizableThreadFactory(String.format("%s-dataextractor", parameterService.getEngineName().toLowerCase()));
                }
                executor = streamToFileEnabled ? Executors.newFixedThreadPool(1, this.threadPoolFactory) : null;
                List<Future<FutureOutgoingBatch>> futures = new ArrayList<Future<FutureOutgoingBatch>>();
                extractInfo.setTotalBatchCount(activeBatches.size());
                for (int i = 0; i < activeBatches.size(); i++) {
                    currentBatch = activeBatches.get(i);
                    channelsProcessed.add(currentBatch.getChannelId());
                    final OutgoingBatch extractBatch = currentBatch;
                    Callable<FutureOutgoingBatch> callable = () -> {
                        MDC.put("engineName", engine.getParameterService().getEngineName());
                        OutgoingBatch refreshedBatch = requeryIfEnoughTimeHasPassed(batchesSelectedAtMs, extractBatch);
                        return extractBatch(refreshedBatch, status, extractInfo, targetNode, dataWriter, mode, activeBatches);
                    };
                    if (status.shouldExtractSkip) {
                        break;
                    }
                    if (executor != null) {
                        futures.add(executor.submit(callable));
                    } else {
                        try {
                            FutureOutgoingBatch batch = callable.call();
                            futures.add(new FutureImpl<>(batch));
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                if (parameterService.is(ParameterConstants.SYNCHRONIZE_ALL_JOBS) && executor != null) {
                    executor.shutdown();
                    boolean isProcessed = false;
                    while (!isProcessed) {
                        try {
                            isProcessed = executor.awaitTermination(keepAliveMillis, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        if (!isProcessed) {
                            writeKeepAliveAck(writer, sourceNode, streamToFileEnabled);
                        }
                    }
                }
                final long initialLoadMaxBytesToSync = parameterService.getLong(ParameterConstants.INITIAL_LOAD_TRANSPORT_MAX_BYTES_TO_SYNC);
                long totalBytesSend = 0;
                boolean logMaxBytesReached = false;
                Iterator<OutgoingBatch> activeBatchIter = activeBatches.iterator();
                for (int i = 0; i < futures.size(); i++) {
                    Future<FutureOutgoingBatch> future = futures.get(i);
                    currentBatch = activeBatchIter.next();
                    boolean isProcessed = false;
                    ProcessInfo transferInfo = null;
                    while (!isProcessed) {
                        try {
                            FutureOutgoingBatch extractBatch = future.get(keepAliveMillis, TimeUnit.MILLISECONDS);
                            transferInfo = statisticManager.newProcessInfo(new ProcessInfoKey(nodeService.findIdentityNodeId(),
                                    extractInfo.getQueue(), targetNode.getNodeId(), extractInfo.getProcessType() == ProcessType.PUSH_JOB_EXTRACT
                                            ? ProcessType.PUSH_JOB_TRANSFER
                                            : ProcessType.PULL_HANDLER_TRANSFER));
                            transferInfo.setCurrentBatchId(currentBatch.getBatchId());
                            transferInfo.incrementBatchCount();
                            transferInfo.setTotalDataCount(currentBatch.getExtractRowCount());
                            currentBatch = extractBatch.getOutgoingBatch();
                            if (i == futures.size() - 1) {
                                extractInfo.setStatus(ProcessStatus.OK);
                            }
                            if (extractBatch.isExtractSkipped) {
                                transferInfo.setStatus(ProcessStatus.OK);
                                break;
                            }
                            if (streamToFileEnabled || mode == ExtractMode.FOR_PAYLOAD_CLIENT || (currentBatch.isExtractJobFlag() && parameterService.is(
                                    ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB))) {
                                if (totalBytesSend > initialLoadMaxBytesToSync) {
                                    if (!logMaxBytesReached) {
                                        logMaxBytesReached = true;
                                        log.info(
                                                "Reached the total byte threshold for initial load after {} of {} batches were sent for node '{}' (sent {} bytes, the max is {}).  "
                                                        + "The remaining batches will be send on a subsequent sync.",
                                                new Object[] { i, futures.size(), targetNode.getNodeId(), totalBytesSend, initialLoadMaxBytesToSync });
                                    }
                                    transferInfo.setStatus(ProcessStatus.OK);
                                    break;
                                }
                                transferInfo.setStatus(ProcessInfo.ProcessStatus.TRANSFERRING);
                                transferInfo.setCurrentLoadId(currentBatch.getLoadId());
                                boolean isRetry = extractBatch.isRetry() && extractBatch.getOutgoingBatch().getStatus() != OutgoingBatch.Status.IG;
                                currentBatch = sendOutgoingBatch(transferInfo, targetNode, currentBatch, isRetry,
                                        dataWriter, writer, mode);
                                totalBytesSend += currentBatch.getByteCount();
                            }
                            processedBatches.add(currentBatch);
                            if (currentBatch.getStatus() != Status.OK) {
                                currentBatch.setLoadCount(currentBatch.getLoadCount() + 1);
                                changeBatchStatus(Status.LD, currentBatch, mode);
                            }
                            if (currentBatch.getLoadId() > 0) {
                                long transferMillis = transferInfo.getEndTime() == null ? new Date().getTime() - transferInfo.getStartTime().getTime()
                                        : transferInfo.getEndTime().getTime() - transferInfo.getStartTime().getTime();
                                updateExtractRequestTransferred(currentBatch, transferMillis);
                            }
                            transferInfo.setCurrentTableName(currentBatch.getSummary());
                            transferInfo.setStatus(ProcessStatus.OK);
                            isProcessed = true;
                        } catch (TimeoutException e) {
                            writeKeepAliveAck(writer, sourceNode, streamToFileEnabled);
                        } catch (Exception e) {
                            if (transferInfo != null && transferInfo.getStatus() != ProcessStatus.OK) {
                                transferInfo.setStatus(ProcessStatus.ERROR);
                            }
                            if (e instanceof ExecutionException) {
                                if (isNotBlank(e.getMessage()) && e.getMessage().contains("string truncation")) {
                                    throw new RuntimeException(
                                            "There is a good chance that the truncation error you are receiving is because contains_big_lobs on the '"
                                                    + currentBatch.getChannelId() + "' channel needs to be turned on.",
                                            e.getCause() != null ? e.getCause() : e);
                                }
                                if (e.getCause() instanceof RuntimeException) {
                                    throw (RuntimeException) e.getCause();
                                }
                                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
                            } else if (!(e instanceof RuntimeException)) {
                                throw new RuntimeException(e);
                            } else if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            }
                        }
                    }
                }
            } catch (RuntimeException e) {
                if (currentBatch != null) {
                    boolean isNewErrorStaging = false;
                    if (!isStreamClosedByClient(e)) {
                        if (e.getCause() instanceof InterruptedException || e.getCause() instanceof CancellationException) {
                            log.info("Extract of batch {} was interrupted", currentBatch);
                        } else if (e instanceof StagingLowFreeSpace) {
                            log.error("Extract is disabled because disk is almost full: {}", e.getMessage());
                        } else if (e.getCause() instanceof ZipException || e instanceof ProtocolException || e instanceof IllegalStateException) {
                            if (currentBatch.getSqlCode() != ErrorConstants.STAGE_ERROR_CODE) {
                                isNewErrorStaging = true;
                            }
                            log.warn("The batch {} appears corrupt in staging, so removing it. ({})", currentBatch.getNodeBatchId(), e.getMessage());
                            IStagedResource resource = getStagedResource(currentBatch);
                            if (resource != null) {
                                resource.delete();
                            }
                        } else {
                            log.error("Failed to extract batch " + currentBatch + " on channel '" + currentBatch.getChannelId() + "'", e);
                        }
                    }
                    try {
                        /* Reread batch in case the ignore flag has been set */
                        currentBatch = outgoingBatchService.findOutgoingBatch(currentBatch.getBatchId(), currentBatch.getNodeId());
                        SQLException se = ExceptionUtils.unwrapSqlException(e);
                        if (se != null) {
                            currentBatch.setSqlState(se.getSQLState());
                            currentBatch.setSqlCode(se.getErrorCode());
                            currentBatch.setSqlMessage(se.getMessage());
                        } else if (isNewErrorStaging) {
                            currentBatch.setSqlState(ErrorConstants.STAGE_ERROR_STATE);
                            currentBatch.setSqlCode(ErrorConstants.STAGE_ERROR_CODE);
                            currentBatch.setSqlMessage(ExceptionUtils.getRootMessage(e));
                        } else {
                            currentBatch.setSqlMessage(ExceptionUtils.getRootMessage(e));
                        }
                        currentBatch.revertStatsOnError();
                        if (currentBatch.getStatus() != Status.IG && currentBatch.getStatus() != Status.OK) {
                            currentBatch.setStatus(Status.ER);
                            currentBatch.setErrorFlag(isNewErrorStaging ? false : true);
                            statisticManager.incrementDataExtractedErrors(currentBatch.getChannelId(), 1);
                            extractInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                        }
                        outgoingBatchService.updateOutgoingBatch(currentBatch);
                    } catch (Exception ex) {
                        log.error("Failed to update the outgoing batch status for failed batch {}", currentBatch, ex);
                        extractInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                    }
                } else {
                    log.error("Could not log the outgoing batch status because the batch was null", e);
                }
            } finally {
                if (executor != null) {
                    executor.shutdown();
                }
            }
            // Next, we update the node channel controls to the
            // current timestamp
            Calendar now = Calendar.getInstance();
            for (String channelProcessed : channelsProcessed) {
                NodeChannel nodeChannel = configurationService.getNodeChannel(channelProcessed,
                        targetNode.getNodeId(), false);
                if (nodeChannel != null && nodeChannel.getExtractPeriodMillis() > 0) {
                    nodeChannel.setLastExtractTime(now.getTime());
                    configurationService.updateLastExtractTime(nodeChannel);
                }
            }
            return processedBatches;
        } else {
            return Collections.emptyList();
        }
    }

    protected FutureOutgoingBatch extractBatch(OutgoingBatch extractBatch, FutureExtractStatus status, ProcessInfo extractInfo,
            Node targetNode, IDataWriter dataWriter, ExtractMode mode, List<OutgoingBatch> activeBatches) throws Exception {
        extractInfo.setThread(Thread.currentThread());
        extractInfo.setCurrentLoadId(extractBatch.getLoadId());
        extractInfo.setTotalDataCount(extractBatch.getDataRowCount());
        FutureOutgoingBatch outgoingBatch = new FutureOutgoingBatch(extractBatch, false);
        final long maxBytesToSync = parameterService.getLong(ParameterConstants.TRANSPORT_MAX_BYTES_TO_SYNC);
        final boolean streamToFileEnabled = parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED);
        if (!status.shouldExtractSkip) {
            if (extractBatch.isExtractJobFlag() && extractBatch.getStatus() != Status.IG) {
                if (parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)) {
                    if (extractBatch.getStatus() != Status.RQ && extractBatch.getStatus() != Status.IG
                            && !isPreviouslyExtracted(extractBatch, false)) {
                        /*
                         * the batch must have been purged. it needs to be re-extracted
                         */
                        log.info("Batch {} is marked as ready but it is missing in staging.  Rescheduling it for extraction.",
                                extractBatch.getNodeBatchId());
                        if (mode != ExtractMode.EXTRACT_ONLY) {
                            resetExtractRequest(extractBatch);
                        }
                        status.shouldExtractSkip = outgoingBatch.isExtractSkipped = true;
                    } else if (extractBatch.getStatus() == Status.RQ) {
                        log.info("Batch {} is not ready for delivery.  It is currently scheduled for extraction.",
                                extractBatch.getNodeBatchId());
                        status.shouldExtractSkip = outgoingBatch.isExtractSkipped = true;
                    }
                } else {
                    extractBatch.setStatus(Status.NE);
                    extractBatch.setExtractJobFlag(false);
                }
            } else {
                try {
                    boolean isRetry = isRetry(extractBatch, targetNode);
                    outgoingBatch = new FutureOutgoingBatch(
                            extractOutgoingBatch(extractInfo, targetNode, dataWriter, extractBatch, streamToFileEnabled, true, mode, null),
                            isRetry);
                    status.batchExtractCount++;
                    status.byteExtractCount += extractBatch.getByteCount();
                    if (status.byteExtractCount >= maxBytesToSync && status.batchExtractCount < activeBatches.size()) {
                        log.info(
                                "Reached the total byte threshold after {} of {} batches were extracted for node '{}' (extracted {} bytes, the max is {}).  "
                                        + "The remaining batches will be extracted on a subsequent sync.",
                                new Object[] { status.batchExtractCount, activeBatches.size(), targetNode.getNodeId(), status.byteExtractCount,
                                        maxBytesToSync });
                        status.shouldExtractSkip = true;
                    }
                } catch (Exception e) {
                    status.shouldExtractSkip = outgoingBatch.isExtractSkipped = true;
                    throw e;
                }
            }
        } else {
            outgoingBatch.isExtractSkipped = true;
        }
        return outgoingBatch;
    }

    protected void writeKeepAliveAck(BufferedWriter writer, Node sourceNode, boolean streamToFileEnabled) {
        try {
            if (writer != null && streamToFileEnabled) {
                writer.write(CsvConstants.NODEID + "," + sourceNode.getNodeId());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ex) {
        }
    }

    final protected boolean changeBatchStatus(Status status, OutgoingBatch currentBatch, ExtractMode mode) {
        if (currentBatch.getStatus() != Status.IG) {
            currentBatch.setStatus(status);
        }
        if (mode != ExtractMode.EXTRACT_ONLY) {
            long batchStatusUpdateMillis = parameterService.getLong(ParameterConstants.OUTGOING_BATCH_UPDATE_STATUS_MILLIS);
            int batchStatusUpdateDataCount = parameterService.getInt(ParameterConstants.OUTGOING_BATCH_UPDATE_STATUS_DATA_COUNT);
            Channel channel = configurationService.getChannel(currentBatch.getChannelId());
            if (currentBatch.getStatus() == Status.RQ ||
                    currentBatch.getStatus() == Status.LD ||
                    currentBatch.getLastUpdatedTime() == null ||
                    System.currentTimeMillis() - batchStatusUpdateMillis >= currentBatch.getLastUpdatedTime().getTime() ||
                    channel.isReloadFlag() ||
                    currentBatch.getDataRowCount() > batchStatusUpdateDataCount) {
                outgoingBatchService.updateOutgoingBatch(currentBatch);
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    /**
     * If time has passed, then re-query the batch to double check that the status has not changed
     */
    final protected OutgoingBatch requeryIfEnoughTimeHasPassed(long ts, OutgoingBatch currentBatch) {
        if (System.currentTimeMillis() - ts > MS_PASSED_BEFORE_BATCH_REQUERIED) {
            OutgoingBatch batch = outgoingBatchService.findOutgoingBatch(currentBatch.getBatchId(),
                    currentBatch.getNodeId());
            if (batch != null && !batch.getStatus().equals(currentBatch.getStatus())) {
                currentBatch.setStatus(batch.getStatus());
            }
        }
        return currentBatch;
    }

    protected OutgoingBatch extractOutgoingBatch(ProcessInfo extractInfo, Node targetNode,
            IDataWriter dataWriter, OutgoingBatch currentBatch, boolean useStagingDataWriter,
            boolean updateBatchStatistics, ExtractMode mode, IDataProcessorListener listener) {
        if (currentBatch.getStatus() != Status.OK || ExtractMode.EXTRACT_ONLY == mode || ExtractMode.FOR_SYM_CLIENT == mode) {
            Node sourceNode = nodeService.findIdentity();
            IDataWriter writer = wrapWithTransformWriter(sourceNode, targetNode, extractInfo, dataWriter, useStagingDataWriter);
            long ts = System.currentTimeMillis();
            long extractTimeInMs = 0l;
            long byteCount = 0l;
            long transformTimeInMs = 0l;
            if (currentBatch.getStatus() == Status.IG) {
                cleanupIgnoredBatch(sourceNode, targetNode, currentBatch, writer);
            } else if (currentBatch.getStatus() == Status.RQ || !isPreviouslyExtracted(currentBatch, false)) {
                BatchLock lock = null;
                try {
                    log.debug("{} attempting to acquire lock for batch {}", targetNode.getNodeId(), currentBatch.getBatchId());
                    lock = acquireLock(currentBatch, useStagingDataWriter);
                    log.debug("{} acquired lock for batch {}", targetNode.getNodeId(), currentBatch.getBatchId());
                    if (currentBatch.getStatus() == Status.RQ || !isPreviouslyExtracted(currentBatch, true)) {
                        log.debug("{} extracting batch {}", targetNode.getNodeId(), currentBatch.getBatchId());
                        currentBatch.setExtractCount(currentBatch.getExtractCount() + 1);
                        if (currentBatch.getExtractStartTime() == null) {
                            currentBatch.setExtractStartTime(new Date());
                        }
                        if (updateBatchStatistics) {
                            changeBatchStatus(Status.QY, currentBatch, mode);
                        }
                        DataContext ctx = new DataContext();
                        ctx.put(Constants.DATA_CONTEXT_TARGET_NODE_ID, targetNode.getNodeId());
                        ctx.put(Constants.DATA_CONTEXT_TARGET_NODE_EXTERNAL_ID, targetNode.getExternalId());
                        ctx.put(Constants.DATA_CONTEXT_TARGET_NODE_GROUP_ID, targetNode.getNodeGroupId());
                        ctx.put(Constants.DATA_CONTEXT_TARGET_NODE, targetNode);
                        ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE, sourceNode);
                        ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE_ID, sourceNode.getNodeId());
                        ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE_EXTERNAL_ID, sourceNode.getExternalId());
                        ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE_GROUP_ID, sourceNode.getNodeGroupId());
                        ctx.put(Constants.DATA_CONTEXT_ENGINE, engine);
                        extractInfo.setTotalDataCount(currentBatch.getDataRowCount());
                        currentBatch.resetStats();
                        IDataReader dataReader = buildExtractDataReader(sourceNode, targetNode, currentBatch, extractInfo);
                        try {
                            new DataProcessor(dataReader, writer, listener, "extract").process(ctx);
                        } catch (Exception e) {
                            if ((e instanceof ProtocolException || sqlTemplate.isDataTruncationViolation(e)) &&
                                    !configurationService.getNodeChannel(currentBatch.getChannelId(), false).getChannel().isContainsBigLob()) {
                                log.warn(e.getMessage());
                                log.info("Re-attempting extraction for batch {} with contains_big_lobs temporarily enabled for channel {}",
                                        currentBatch.getBatchId(), currentBatch.getChannelId());
                                extractInfo.setTotalDataCount(currentBatch.getDataRowCount());
                                currentBatch.resetStats();
                                IStagedResource resource = getStagedResource(currentBatch);
                                if (resource != null) {
                                    resource.delete();
                                }
                                dataReader = buildExtractDataReader(sourceNode, targetNode, currentBatch, extractInfo, true);
                                writer = wrapWithTransformWriter(sourceNode, targetNode, extractInfo, dataWriter, useStagingDataWriter);
                                new DataProcessor(dataReader, writer, listener, "extract").process(ctx);
                            } else {
                                throw e;
                            }
                        }
                        extractTimeInMs = System.currentTimeMillis() - ts;
                        Statistics stats = getExtractStats(writer, currentBatch);
                        if (stats != null) {
                            transformTimeInMs = stats.get(DataWriterStatisticConstants.TRANSFORMMILLIS);
                            currentBatch.setDataRowCount(stats.get(DataWriterStatisticConstants.ROWCOUNT));
                            currentBatch.setDataInsertRowCount(stats.get(DataWriterStatisticConstants.INSERTCOUNT));
                            currentBatch.setDataUpdateRowCount(stats.get(DataWriterStatisticConstants.UPDATECOUNT));
                            currentBatch.setDataDeleteRowCount(stats.get(DataWriterStatisticConstants.DELETECOUNT));
                            currentBatch.setTableExtractedCount(stats.getTableStats());
                            currentBatch.setTransformExtractMillis(transformTimeInMs);
                            extractTimeInMs = extractTimeInMs - transformTimeInMs;
                            byteCount = stats.get(DataWriterStatisticConstants.BYTECOUNT);
                            statisticManager.incrementDataBytesExtracted(currentBatch.getChannelId(), byteCount);
                            statisticManager.incrementDataExtracted(currentBatch.getChannelId(),
                                    stats.get(DataWriterStatisticConstants.ROWCOUNT));
                            statisticManager.incrementTableRows(currentBatch.getTableExtractedCount(), false);
                            currentBatch.setByteCount(byteCount);
                            if (!useStagingDataWriter) {
                                statisticManager.incrementDataBytesSent(currentBatch.getChannelId(), byteCount);
                                statisticManager.incrementDataSent(currentBatch.getChannelId(), stats.get(DataWriterStatisticConstants.ROWCOUNT));
                            }
                            if (currentBatch.isCommonFlag()) {
                                outgoingBatchService.updateCommonBatchExtractStatistics(currentBatch);
                            }
                        }
                    }
                } catch (RuntimeException ex) {
                    IStagedResource resource = getStagedResource(currentBatch);
                    if (resource != null) {
                        resource.close();
                        resource.delete();
                    }
                    throw ex;
                } finally {
                    try {
                        IStagedResource resource = getStagedResource(currentBatch);
                        if (resource != null) {
                            resource.setState(State.DONE);
                        }
                    } finally {
                        releaseLock(lock, currentBatch, useStagingDataWriter);
                        log.debug("{} released lock for batch {}", targetNode.getNodeId(), currentBatch.getBatchId());
                    }
                }
            }
            if (updateBatchStatistics) {
                currentBatch = requeryIfEnoughTimeHasPassed(ts, currentBatch);
                if (extractTimeInMs > 0) {
                    currentBatch.setExtractMillis(extractTimeInMs);
                }
                if (byteCount > 0) {
                    currentBatch.setByteCount(byteCount);
                }
                if (transformTimeInMs > 0) {
                    currentBatch.setTransformExtractMillis(transformTimeInMs);
                }
                if (currentBatch.getLoadId() > 0 && (currentBatch.getSummary() == null || !currentBatch.getSummary().startsWith(symmetricDialect
                        .getTablePrefix()))) {
                    if (currentBatch.getExtractRowCount() != currentBatch.getDataRowCount()) {
                        currentBatch.setDataRowCount(currentBatch.getExtractRowCount());
                        currentBatch.setDataInsertRowCount(currentBatch.getExtractInsertRowCount());
                    }
                    ExtractRequest extractRequest = getExtractRequestForBatch(currentBatch);
                    if (extractRequest != null && extractRequest.getStatus() != ExtractStatus.OK) {
                        sqlTemplate.update(getSql("updateExtractRequestStatus"), ExtractStatus.OK.name(), new Date(),
                                currentBatch.getExtractRowCount(), currentBatch.getExtractMillis(), extractRequest.getRequestId());
                        checkSendDeferredConstraints(extractRequest, null, targetNode);
                    }
                }
            }
        }
        return currentBatch;
    }

    protected String getSemaphoreKey(OutgoingBatch batch, boolean useStagingDataWriter) {
        return useStagingDataWriter ? Long.toString(batch.getBatchId()) : batch.getNodeBatchId();
    }

    private BatchLock acquireLock(OutgoingBatch batch, boolean useStagingDataWriter) {
        String semaphoreKey = getSemaphoreKey(batch, useStagingDataWriter);
        BatchLock lock = null;
        synchronized (DataExtractorService.this) {
            lock = locks.get(semaphoreKey);
            if (lock == null) {
                lock = new BatchLock(semaphoreKey);
                locks.put(semaphoreKey, lock);
            }
            lock.referenceCount++;
        }
        try {
            lock.acquire(); // In-memory, intra-process lock.
            if (isStagingFileLockRequired(batch)) { // File-system, inter-process lock for clustering.
                StagingFileLock fileLock = acquireStagingFileLock(batch);
                if (fileLock.isAcquired()) {
                    lock.fileLock = fileLock;
                } else {
                    // Didn't get the fileLock, ditch the in-memory lock as well.
                    releaseLock(lock, batch, useStagingDataWriter);
                    // So the next releaseLock() does not do anything with the lock
                    lock = null;
                    throw new SymmetricException("Failed to get extract lock on batch " + batch.getNodeBatchId());
                }
            }
        } catch (InterruptedException e) {
            releaseLock(lock, batch, useStagingDataWriter);
            throw new org.jumpmind.exception.InterruptedException(e);
        } catch (Throwable e) {
            releaseLock(lock, batch, useStagingDataWriter);
            if (e instanceof SymmetricException) {
                throw (SymmetricException) e;
            } else {
                throw new SymmetricException(e);
            }
        }
        return lock;
    }

    @Override
    public StagingFileLock acquireStagingFileLock(OutgoingBatch batch) {
        boolean stagingFileAcquired = false;
        StagingFileLock fileLock = null;
        int iterations = 0;
        while (!stagingFileAcquired) {
            fileLock = stagingManager.acquireFileLock(getLockingServerInfo(), Constants.STAGING_CATEGORY_OUTGOING,
                    batch.getStagedLocation(), batch.getBatchId());
            stagingFileAcquired = fileLock.isAcquired();
            if (!stagingFileAcquired) {
                if (fileLock.getLockFile() == null) {
                    log.warn("Staging lock file not acquired " + fileLock.getLockFailureMessage());
                    return fileLock;
                }
                long lockAge = fileLock.getLockAge();
                if (lockAge >= parameterService.getLong(ParameterConstants.LOCK_TIMEOUT_MS)) {
                    log.warn("Lock {} in place for {} > about to BREAK the lock.", fileLock.getLockFile(), DurationFormatUtils.formatDurationWords(lockAge,
                            true, true));
                    fileLock.breakLock();
                } else {
                    if ((iterations % 10) == 0) {
                        log.info("Lock {} in place for {}, waiting...", fileLock.getLockFile(), DurationFormatUtils.formatDurationWords(lockAge, true, true));
                    } else {
                        log.debug("Lock {} in place for {}, waiting...", fileLock.getLockFile(), DurationFormatUtils.formatDurationWords(lockAge, true, true));
                    }
                    try {
                        Thread.sleep(parameterService.getLong(ParameterConstants.LOCK_WAIT_RETRY_MILLIS));
                    } catch (InterruptedException ex) {
                        log.debug("Interrupted.", ex);
                    }
                }
            }
            iterations++;
        }
        return fileLock;
    }

    private String getLockingServerInfo() {
        return String.format("Server: '%s' Host: '%s' IP: '%s'", clusterService.getServerId(), AppUtils.getHostName(), AppUtils.getIpAddress());
    }

    protected void releaseLock(BatchLock lock, OutgoingBatch batch, boolean useStagingDataWriter) {
        if (lock != null) {
            synchronized (DataExtractorService.this) {
                lock.referenceCount--;
                if (lock.referenceCount == 0) {
                    locks.remove(lock.semaphoreKey);
                }
                lock.release();
            }
            if (lock.fileLock != null) {
                lock.fileLock.releaseLock();
            }
        }
    }

    protected boolean isStagingFileLockRequired(OutgoingBatch batch) {
        return batch.isCommonFlag()
                && parameterService.is(ParameterConstants.CLUSTER_STAGING_ENABLED);
    }

    protected void triggerReExtraction(OutgoingBatch currentBatch) {
        // Allow user to reset batch status to NE in the DB to trigger a batch re-extract
        IStagedResource resource = getStagedResource(currentBatch);
        if (resource != null) {
            resource.delete();
        }
    }

    protected ExtractDataReader buildExtractDataReader(Node sourceNode, Node targetNode, OutgoingBatch currentBatch, ProcessInfo processInfo) {
        boolean containsBigLob = engine.getConfigurationService().getNodeChannel(currentBatch.getChannelId(), false).getChannel().isContainsBigLob();
        return buildExtractDataReader(sourceNode, targetNode, currentBatch, processInfo, containsBigLob);
    }

    protected ExtractDataReader buildExtractDataReader(Node sourceNode, Node targetNode, OutgoingBatch currentBatch, ProcessInfo processInfo,
            boolean containsBigLob) {
        IExtractDataReaderSource source = AppUtils.newInstance(IExtractDataReaderSource.class, SelectFromSymDataSource.class,
                new Object[] { engine, currentBatch, sourceNode, targetNode, processInfo, containsBigLob },
                new Class[] { ISymmetricEngine.class, OutgoingBatch.class, Node.class, Node.class, ProcessInfo.class, boolean.class });
        IExtractDataReaderFactory factory = AppUtils.newInstance(IExtractDataReaderFactory.class, ExtractDataReaderFactory.class,
                new Object[] { engine }, new Class[] { ISymmetricEngine.class });
        return factory.getReader(platform, source, sourceNode, targetNode, engine.getSymmetricDialect().getTargetPlatform());
    }

    protected Statistics getExtractStats(IDataWriter writer, OutgoingBatch currentBatch) {
        Map<Batch, Statistics> statisticsMap = null;
        if (writer instanceof TransformWriter) {
            statisticsMap = ((TransformWriter) writer).getNestedWriter().getStatistics();
        } else {
            statisticsMap = writer.getStatistics();
        }
        if (statisticsMap.size() > 0) {
            for (Entry<Batch, Statistics> entry : statisticsMap.entrySet()) {
                if (entry.getKey().getBatchId() == currentBatch.getBatchId()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    protected IDataWriter wrapWithTransformWriter(Node sourceNode, Node targetNode, ProcessInfo processInfo, IDataWriter dataWriter,
            boolean useStagingDataWriter) {
        TransformWriter transformExtractWriter = null;
        if (useStagingDataWriter) {
            long memoryThresholdInBytes = parameterService
                    .getLong(ParameterConstants.STREAM_TO_FILE_THRESHOLD);
            transformExtractWriter = createTransformDataWriter(
                    sourceNode,
                    targetNode,
                    new ProcessInfoDataWriter(new StagingDataWriter(memoryThresholdInBytes, true, nodeService
                            .findIdentityNodeId(), Constants.STAGING_CATEGORY_OUTGOING,
                            stagingManager, targetNode.allowCaptureTimeInProtocol(),
                            parameterService.is(ParameterConstants.EXTRACT_ROW_CAPTURE_TIME, true)), processInfo));
        } else {
            transformExtractWriter = createTransformDataWriter(sourceNode, targetNode,
                    new ProcessInfoDataWriter(dataWriter, processInfo));
        }
        return transformExtractWriter;
    }

    protected void cleanupIgnoredBatch(Node sourceNode, Node targetNode, OutgoingBatch currentBatch, IDataWriter writer) {
        Batch batch = new Batch(BatchType.EXTRACT, currentBatch.getBatchId(),
                currentBatch.getChannelId(), symmetricDialect.getBinaryEncoding(),
                sourceNode.getNodeId(), currentBatch.getNodeId(),
                currentBatch.isCommonFlag());
        batch.setIgnored(true);
        try {
            IStagedResource resource = getStagedResource(currentBatch);
            if (resource != null) {
                resource.delete();
            }
            DataContext ctx = new DataContext(batch);
            ctx.put("targetNode", targetNode);
            ctx.put("sourceNode", sourceNode);
            writer.open(ctx);
            writer.start(batch);
            writer.end(batch, false);
        } finally {
            writer.close();
            IStagedResource resource = getStagedResource(currentBatch);
            if (resource != null) {
                resource.setState(State.DONE);
            }
        }
    }

    protected IStagedResource getStagedResource(OutgoingBatch currentBatch) {
        return stagingManager.find(Constants.STAGING_CATEGORY_OUTGOING,
                currentBatch.getStagedLocation(), currentBatch.getBatchId());
    }

    protected boolean isPreviouslyExtracted(OutgoingBatch currentBatch, boolean acquireReference) {
        IStagedResource previouslyExtracted = getStagedResource(currentBatch);
        if (previouslyExtracted != null && previouslyExtracted.getState() == State.DONE) {
            if (log.isDebugEnabled()) {
                log.debug("We have already extracted batch {}.  Using the existing extraction: {}",
                        currentBatch.getBatchId(), previouslyExtracted);
            }
            if (acquireReference) {
                previouslyExtracted.reference();
            }
            return true;
        }
        return false;
    }

    protected boolean isRetry(OutgoingBatch currentBatch, Node remoteNode) {
        if (currentBatch.getSentCount() > 0 && currentBatch.getStatus() != OutgoingBatch.Status.RS && currentBatch.getStatus() != OutgoingBatch.Status.IG) {
            boolean offline = parameterService.is(ParameterConstants.NODE_OFFLINE, false);
            boolean cclient = StringUtils.equals(remoteNode.getDeploymentType(), Constants.DEPLOYMENT_TYPE_CCLIENT);
            if (remoteNode.isVersionGreaterThanOrEqualTo(3, 8, 0) && !offline && !cclient) {
                IStagedResource previouslyExtracted = getStagedResource(currentBatch);
                return previouslyExtracted != null && previouslyExtracted.getState() == State.DONE;
            }
        }
        return false;
    }

    protected OutgoingBatch sendOutgoingBatch(ProcessInfo processInfo, Node targetNode,
            OutgoingBatch currentBatch, boolean isRetry, IDataWriter dataWriter, BufferedWriter writer, ExtractMode mode) {
        if (currentBatch.getStatus() != Status.OK || ExtractMode.EXTRACT_ONLY == mode) {
            currentBatch.setSentCount(currentBatch.getSentCount() + 1);
            if (currentBatch.getStatus() != Status.RS) {
                currentBatch.setTransferStartTime(new Date());
            }
            long ts = System.currentTimeMillis();
            IStagedResource extractedBatch = getStagedResource(currentBatch);
            if (extractedBatch != null) {
                processInfo.setTotalDataCount(currentBatch.getDataRowCount());
                if (currentBatch.getLoadId() > 0) {
                    processInfo.setCurrentTableName(currentBatch.getSummary());
                }
                if (mode == ExtractMode.FOR_SYM_CLIENT && writer != null) {
                    if (!isRetry && parameterService.is(ParameterConstants.OUTGOING_BATCH_COPY_TO_INCOMING_STAGING) &&
                            !parameterService.is(ParameterConstants.NODE_OFFLINE, false)) {
                        ISymmetricEngine targetEngine = AbstractSymmetricEngine.findEngineByUrl(targetNode.getSyncUrl());
                        if (targetEngine != null) {
                            IParameterService targetParam = targetEngine.getParameterService();
                            if (extractedBatch.isFileResource() && targetParam.is(ParameterConstants.STREAM_TO_FILE_ENABLED)
                                    && (!targetParam.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) || targetParam.is(
                                            ParameterConstants.CLUSTER_STAGING_ENABLED))) {
                                Node sourceNode = nodeService.findIdentity();
                                Node targetNodeByEngine = targetEngine.getNodeService().findIdentity();
                                if ((sourceNode != null && sourceNode.equals(targetNodeByEngine)) || (targetNodeByEngine != null && !targetNodeByEngine.equals(
                                        targetNode))) {
                                    log.warn(
                                            "Target engine (NodeId {}) is the same engine as the current one and differs from the correct target (NodeId {}). This looks like a mis-configuration of the sync urls '{}'",
                                            targetNodeByEngine.getNodeId(), targetNode.getNodeId(), targetNode.getSyncUrl());
                                } else {
                                    IStagedResource targetResource = targetEngine.getStagingManager().create(
                                            Constants.STAGING_CATEGORY_INCOMING, Batch.getStagedLocation(false, sourceNode.getNodeId(), currentBatch
                                                    .getBatchId()),
                                            currentBatch.getBatchId());
                                    try {
                                        Files.copy(extractedBatch.getFile().toPath(), targetResource.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
                                        processInfo.setCurrentDataCount(currentBatch.getDataRowCount());
                                        if (log.isDebugEnabled()) {
                                            log.debug("Copied file to incoming staging of remote engine {}", targetResource.getFile().getAbsolutePath());
                                        }
                                        targetResource.setState(State.DONE);
                                        isRetry = true;
                                        if (currentBatch.getSentCount() == 1) {
                                            statisticManager.incrementDataSent(currentBatch.getChannelId(), currentBatch.getDataRowCount());
                                            statisticManager.incrementDataBytesSent(currentBatch.getChannelId(), extractedBatch.getFile().length());
                                        }
                                    } catch (Exception e) {
                                        FileUtils.deleteQuietly(targetResource.getFile());
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                        }
                    } else if (!isRetry && engine.getConfigurationService().isUseSourceStagingEnabled(targetNode.getNodeId()) &&
                            currentBatch.getStatus() != OutgoingBatch.Status.IG) {
                        ISymmetricEngine targetEngine = AbstractSymmetricEngine.findEngineByNodeId(targetNode.getNodeId());
                        if (targetEngine != null) {
                            isRetry = true;
                            if (currentBatch.getSentCount() == 1) {
                                statisticManager.incrementDataSent(currentBatch.getChannelId(), currentBatch.getDataRowCount());
                                statisticManager.incrementDataBytesSent(currentBatch.getChannelId(), extractedBatch.getFile().length());
                            }
                        }
                    }
                    Channel channel = configurationService.getChannel(currentBatch.getChannelId());
                    DataContext ctx = new DataContext();
                    transferFromStaging(mode, BatchType.EXTRACT, currentBatch, isRetry, extractedBatch, writer, ctx,
                            channel.getMaxKBytesPerSecond(), processInfo);
                } else {
                    IDataReader dataReader = new ProtocolDataReader(BatchType.EXTRACT,
                            currentBatch.getNodeId(), extractedBatch);
                    DataContext ctx = new DataContext();
                    ctx.put(Constants.DATA_CONTEXT_TARGET_NODE, targetNode);
                    ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE, nodeService.findIdentity());
                    new DataProcessor(dataReader, new ProcessInfoDataWriter(dataWriter, processInfo), "send from stage")
                            .process(ctx);
                    if (dataReader.getStatistics().size() > 0) {
                        if (!isRetry) {
                            Statistics stats = dataReader.getStatistics().values().iterator().next();
                            statisticManager.incrementDataSent(currentBatch.getChannelId(),
                                    stats.get(DataReaderStatistics.READ_RECORD_COUNT));
                            long byteCount = stats.get(DataReaderStatistics.READ_BYTE_COUNT);
                            statisticManager.incrementDataBytesSent(currentBatch.getChannelId(), byteCount);
                        }
                    } else {
                        log.warn("Could not find recorded statistics for batch {}",
                                currentBatch.getNodeBatchId());
                    }
                }
            } else {
                throw new IllegalStateException(String.format(
                        "Could not find the staged resource for batch %s",
                        currentBatch.getNodeBatchId()));
            }
            currentBatch = requeryIfEnoughTimeHasPassed(ts, currentBatch);
        }
        return currentBatch;
    }

    protected void transferFromStaging(ExtractMode mode, BatchType batchType, OutgoingBatch batch, boolean isRetry, IStagedResource stagedResource,
            BufferedWriter writer, DataContext context, BigDecimal maxKBytesPerSec, ProcessInfo processInfo) {
        final int MAX_WRITE_LENGTH = 32768;
        BufferedReader reader = stagedResource.getReader();
        try {
            // Retry means we've sent this batch before, so let's ask to
            // retry the batch from the target's staging
            if (isRetry) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(CsvConstants.BATCH)) {
                        if (nodeService.findNode(batch.getNodeId(), true).isVersionGreaterThanOrEqualTo(3, 9, 0)) {
                            writer.write(getBatchStatsColumns());
                            writer.newLine();
                            writer.write(getBatchStats(batch));
                            writer.newLine();
                        }
                        writer.write(CsvConstants.RETRY + "," + batch.getBatchId());
                        writer.newLine();
                        writer.write(CsvConstants.COMMIT + "," + batch.getBatchId());
                        writer.newLine();
                        break;
                    } else {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                writer.flush();
                processInfo.setCurrentDataCount(batch.getDataRowCount());
            } else {
                long totalBytes = stagedResource.getSize();
                long totalCharsRead = 0, totalBytesRead = 0;
                int numCharsRead = 0, numBytesRead = 0;
                long startTime = System.currentTimeMillis(), ts = startTime, bts = startTime;
                boolean isThrottled = maxKBytesPerSec != null && maxKBytesPerSec.compareTo(BigDecimal.ZERO) > 0;
                long totalThrottleTime = 0;
                int bufferSize = MAX_WRITE_LENGTH;
                if (isThrottled) {
                    bufferSize = maxKBytesPerSec.multiply(new BigDecimal(1024)).intValue();
                }
                char[] buffer = new char[bufferSize];
                boolean batchStatsWritten = false;
                String prevBuffer = "";
                long batchStatusUpdateMillis = parameterService.getLong(ParameterConstants.OUTGOING_BATCH_UPDATE_STATUS_MILLIS);
                boolean is39orNewer = nodeService.findNode(batch.getNodeId(), true).isVersionGreaterThanOrEqualTo(3, 9, 0);
                while ((numCharsRead = reader.read(buffer)) != -1) {
                    if (!batchStatsWritten && is39orNewer) {
                        batchStatsWritten = writeBatchStats(writer, buffer, numCharsRead, prevBuffer, batch);
                        prevBuffer = new String(buffer);
                    } else {
                        writer.write(buffer, 0, numCharsRead);
                    }
                    totalCharsRead += numCharsRead;
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IoException("This thread was interrupted");
                    }
                    if (System.currentTimeMillis() - ts > batchStatusUpdateMillis && batch.getStatus() != Status.SE && batch.getStatus() != Status.RS) {
                        changeBatchStatus(Status.SE, batch, mode);
                    }
                    if (System.currentTimeMillis() - ts > LOG_PROCESS_SUMMARY_THRESHOLD) {
                        log.info(
                                "Batch '{}', for node '{}', for process 'send from stage' has been processing for {} seconds.  "
                                        + "The following stats have been gathered: {}",
                                new Object[] { batch.getBatchId(), batch.getNodeId(), (System.currentTimeMillis() - startTime) / 1000,
                                        "CHARS=" + totalCharsRead });
                        ts = System.currentTimeMillis();
                    }
                    if (isThrottled) {
                        numBytesRead += new String(buffer, 0, numCharsRead).getBytes().length;
                        totalBytesRead += numBytesRead;
                        if (numBytesRead >= bufferSize) {
                            long expectedMillis = (long) (((numBytesRead / 1024f) / maxKBytesPerSec.floatValue()) * 1000);
                            long actualMillis = System.currentTimeMillis() - bts;
                            if (actualMillis < expectedMillis) {
                                totalThrottleTime += expectedMillis - actualMillis;
                                Thread.sleep(expectedMillis - actualMillis);
                            }
                            numBytesRead = 0;
                            bts = System.currentTimeMillis();
                        }
                    } else {
                        totalBytesRead += new String(buffer, 0, numCharsRead).getBytes().length;
                    }
                    processInfo.setCurrentDataCount((long) ((totalBytesRead / (double) totalBytes) * batch.getDataRowCount()));
                }
                if (batch.getSentCount() == 1) {
                    statisticManager.incrementDataSent(batch.getChannelId(), batch.getDataRowCount());
                    statisticManager.incrementDataBytesSent(batch.getChannelId(), totalBytesRead);
                }
                if (log.isDebugEnabled() && totalThrottleTime > 0) {
                    log.debug("Batch '{}' for node '{}' took {}ms for {} bytes and was throttled for {}ms because limit is set to {} KB/s",
                            batch.getBatchId(), batch.getNodeId(), (System.currentTimeMillis() - startTime), totalBytesRead,
                            totalThrottleTime, maxKBytesPerSec);
                }
            }
            if (writer instanceof BatchBufferedWriter) {
                ((BatchBufferedWriter) writer).getBatchIds().add(batch.getBatchId());
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            stagedResource.close();
            stagedResource.dereference();
        }
    }

    protected int findStatsIndex(String bufferString, String prevBuffer) {
        int index = -1;
        String fullBuffer = prevBuffer + bufferString;
        String pattern = "\n" + CsvConstants.BATCH + "\\s*,\\s*\\d*\r*\n";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(fullBuffer);
        if (m.find()) {
            String group = m.group(0);
            int start = fullBuffer.indexOf(group);
            if (start + group.length() > prevBuffer.length()) {
                index = start + group.length() - prevBuffer.length();
            } else {
                index = start + group.length();
            }
        }
        return index;
    }

    @Override
    public List<ExtractRequest> getPendingTablesForExtractByLoadId(long loadId) {
        return sqlTemplate.query(getSql("selectIncompleteTablesForExtractByLoadId"), new ExtractRequestMapper(), loadId, engine.getNodeId());
    }

    @Override
    public List<ExtractRequest> getCompletedTablesForExtractByLoadId(long loadId) {
        return sqlTemplate.query(getSql("selectCompletedTablesForExtractByLoadId"), new ExtractRequestMapper(), loadId, engine.getNodeId());
    }

    @Override
    public List<ExtractRequest> getPendingTablesForExtractByLoadIdAndNodeId(long loadId, String nodeId) {
        return sqlTemplate.query(getSql("selectIncompleteTablesForExtractByLoadIdAndNodeId"), new ExtractRequestMapper(), loadId, nodeId);
    }

    @Override
    public List<ExtractRequest> getCompletedTablesForExtractByLoadIdAndNodeId(long loadId, String nodeId) {
        return sqlTemplate.query(getSql("selectCompletedTablesForExtractByLoadIdAndNodeId"), new ExtractRequestMapper(), loadId, nodeId);
    }

    @Override
    public void updateExtractRequestLoadTime(ISqlTransaction transaction, Date loadTime, OutgoingBatch outgoingBatch) {
        if (platform.supportsParametersInSelect()) {
            transaction.prepareAndExecute(getSql("updateExtractRequestLoadTime"), outgoingBatch.getBatchId(), new Date(),
                    outgoingBatch.getReloadRowCount() > 0 ? outgoingBatch.getDataRowCount() : 0,
                    outgoingBatch.getLoadMillis(), outgoingBatch.getBatchId(), new Date(), outgoingBatch.isBulkLoaderFlag() == true ? outgoingBatch
                            .getLoadRowCount() : 0, outgoingBatch.getBatchId(),
                    outgoingBatch.getBatchId(), outgoingBatch.getNodeId(), outgoingBatch.getLoadId(), engine.getNodeId());
        } else {
            String sql = getSql("updateExtractRequestLoadTimeNoParamsInSelect");
            sql = FormatUtils.replace("batchId", String.valueOf(outgoingBatch.getBatchId()), sql);
            sql = FormatUtils.replace("rowCount",
                    String.valueOf(outgoingBatch.getReloadRowCount() > 0 ? outgoingBatch.getDataRowCount() : 0), sql);
            sql = FormatUtils.replace("loadMillis", String.valueOf(outgoingBatch.getLoadMillis()), sql);
            sql = FormatUtils.replace("bulkRowsLoaded",
                    String.valueOf(outgoingBatch.isBulkLoaderFlag() == true ? (outgoingBatch.getReloadRowCount() > 0 ? outgoingBatch.getDataRowCount() : 0)
                            : 0), sql);
            transaction.prepareAndExecute(sql, outgoingBatch.getBatchId(), new Date(), outgoingBatch.getBatchId(),
                    outgoingBatch.getBatchId(), outgoingBatch.getNodeId(), outgoingBatch.getLoadId(), engine.getNodeId());
        }
        TableReloadStatus status = dataService.updateTableReloadStatusDataLoaded(transaction,
                outgoingBatch.getLoadId(), engine.getNodeId(), outgoingBatch.getBatchId(), 1, outgoingBatch.isBulkLoaderFlag());
        if (status != null && (status.isCancelled() || status.isCompleted())) {
            if (status.isFullLoad()) {
                log.info("Initial load ended for node {}, load ID {}", outgoingBatch.getNodeId(), status.getLoadId());
                nodeService.setInitialLoadEnded(transaction, outgoingBatch.getNodeId());
            } else {
                log.info("Partial load ended for node {}, load ID {}", outgoingBatch.getNodeId(), status.getLoadId());
                nodeService.setPartialLoadEnded(transaction, outgoingBatch.getNodeId());
            }
        }
    }

    @Override
    public void updateExtractRequestTransferred(OutgoingBatch batch, long transferMillis) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            if (platform.supportsParametersInSelect()) {
                transaction.prepareAndExecute(getSql("updateExtractRequestTransferred"), batch.getBatchId(), batch.getDataRowCount(), transferMillis,
                        batch.getBatchId(), batch.getBatchId(), batch.getNodeId(), batch.getLoadId(), batch.getBatchId(), engine.getNodeId());
            } else {
                String sql = getSql("updateExtractRequestTransferredNoParamsInSelect");
                sql = FormatUtils.replace("rowCount", String.valueOf(batch.getDataRowCount()), sql);
                transaction.prepareAndExecute(sql, batch.getBatchId(), transferMillis, batch.getBatchId(),
                        batch.getBatchId(), batch.getNodeId(), batch.getLoadId(), batch.getBatchId(), engine.getNodeId());
            }
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

    @Override
    public int cancelExtractRequests(long loadId) {
        Date date = new Date();
        return sqlTemplate.update(getSql("cancelExtractRequests"), ExtractStatus.OK.name(), date, date, loadId, engine.getNodeId(), ExtractStatus.OK.name());
    }

    protected boolean writeBatchStats(BufferedWriter writer, char[] buffer, int bufferSize, String prevBuffer, OutgoingBatch batch)
            throws IOException {
        String bufferString = new String(buffer);
        int index = findStatsIndex(bufferString, prevBuffer);
        if (index > 0) {
            char prefix[] = Arrays.copyOf(buffer, index);
            writer.write(prefix, 0, index);
        }
        if (index > -1) {
            String stats = getBatchStatsColumns() + System.lineSeparator() + getBatchStats(batch) + System.lineSeparator();
            char statsBuffer[] = stats.toCharArray();
            writer.write(statsBuffer, 0, statsBuffer.length);
            char suffix[] = Arrays.copyOfRange(buffer, index, buffer.length);
            writer.write(suffix, 0, bufferSize - index);
        } else {
            writer.write(buffer, 0, bufferSize);
        }
        return index > -1;
    }

    protected String getBatchStatsColumns() {
        return StringUtils.join(new String[] { CsvConstants.STATS_COLUMNS, DataReaderStatistics.LOAD_FLAG, DataReaderStatistics.EXTRACT_COUNT,
                DataReaderStatistics.SENT_COUNT, DataReaderStatistics.LOAD_COUNT, DataReaderStatistics.LOAD_ID,
                DataReaderStatistics.COMMON_FLAG, DataReaderStatistics.ROUTER_MILLIS, DataReaderStatistics.EXTRACT_MILLIS,
                DataReaderStatistics.TRANSFORM_EXTRACT_MILLIS, DataReaderStatistics.TRANSFORM_LOAD_MILLIS,
                DataReaderStatistics.RELOAD_ROW_COUNT, DataReaderStatistics.OTHER_ROW_COUNT, DataReaderStatistics.DATA_ROW_COUNT,
                DataReaderStatistics.DATA_INSERT_ROW_COUNT, DataReaderStatistics.DATA_UPDATE_ROW_COUNT,
                DataReaderStatistics.DATA_DELETE_ROW_COUNT, DataReaderStatistics.EXTRACT_ROW_COUNT,
                DataReaderStatistics.EXTRACT_INSERT_ROW_COUNT, DataReaderStatistics.EXTRACT_UPDATE_ROW_COUNT,
                DataReaderStatistics.EXTRACT_DELETE_ROW_COUNT, DataReaderStatistics.FAILED_DATA_ID }, ',');
    }

    protected String getBatchStats(OutgoingBatch batch) {
        return StringUtils.join(new String[] { CsvConstants.STATS, String.valueOf(batch.isLoadFlag() ? 1 : 0),
                String.valueOf(batch.getExtractCount()), String.valueOf(batch.getSentCount()), String.valueOf(batch.getLoadCount()),
                String.valueOf(batch.getLoadId()), String.valueOf(batch.isCommonFlag() ? 1 : 0), String.valueOf(batch.getRouterMillis()),
                String.valueOf(batch.getExtractMillis()), String.valueOf(batch.getTransformExtractMillis()),
                String.valueOf(batch.getTransformLoadMillis()), String.valueOf(batch.getReloadRowCount()),
                String.valueOf(batch.getOtherRowCount()), String.valueOf(batch.getDataRowCount()),
                String.valueOf(batch.getDataInsertRowCount()), String.valueOf(batch.getDataUpdateRowCount()),
                String.valueOf(batch.getDataDeleteRowCount()), String.valueOf(batch.getExtractRowCount()),
                String.valueOf(batch.getExtractInsertRowCount()), String.valueOf(batch.getExtractUpdateRowCount()),
                String.valueOf(batch.getExtractDeleteRowCount()), String.valueOf(batch.getFailedDataId()) }, ',');
    }

    @Override
    public boolean extractBatchRange(Writer writer, String nodeId, long startBatchId,
            long endBatchId) {
        boolean foundBatch = false;
        Node sourceNode = nodeService.findIdentity();
        for (long batchId = startBatchId; batchId <= endBatchId; batchId++) {
            OutgoingBatch batch = outgoingBatchService.findOutgoingBatch(batchId, nodeId);
            if (batch != null) {
                Node targetNode = nodeService.findNode(nodeId, true);
                if (targetNode == null && Constants.UNROUTED_NODE_ID.equals(nodeId)) {
                    targetNode = new Node();
                    targetNode.setNodeId("-1");
                }
                if (targetNode != null) {
                    IDataReader dataReader = buildExtractDataReader(sourceNode, targetNode, batch, new ProcessInfo());
                    DataContext ctx = new DataContext();
                    ctx.put(Constants.DATA_CONTEXT_TARGET_NODE, targetNode);
                    ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE, nodeService.findIdentity());
                    new DataProcessor(dataReader, createTransformDataWriter(
                            nodeService.findIdentity(), targetNode,
                            new ProtocolDataWriter(nodeService.findIdentityNodeId(), writer,
                                    targetNode.requires13Compatiblity(), false, false)), "extract range").process(ctx);
                    foundBatch = true;
                }
            }
        }
        return foundBatch;
    }

    @Override
    public boolean extractBatchRange(Writer writer, String nodeId, Date startBatchTime,
            Date endBatchTime, String... channelIds) {
        boolean foundBatch = false;
        Node sourceNode = nodeService.findIdentity();
        OutgoingBatches batches = outgoingBatchService.getOutgoingBatchRange(nodeId,
                startBatchTime, endBatchTime, channelIds);
        List<OutgoingBatch> list = batches.getBatches();
        for (OutgoingBatch outgoingBatch : list) {
            Node targetNode = nodeService.findNode(nodeId, true);
            if (targetNode == null && Constants.UNROUTED_NODE_ID.equals(nodeId)) {
                targetNode = new Node();
                targetNode.setNodeId("-1");
            }
            if (targetNode != null) {
                IDataReader dataReader = buildExtractDataReader(sourceNode, targetNode, outgoingBatch, new ProcessInfo());
                DataContext ctx = new DataContext();
                ctx.put(Constants.DATA_CONTEXT_TARGET_NODE, targetNode);
                ctx.put(Constants.DATA_CONTEXT_SOURCE_NODE, nodeService.findIdentity());
                new DataProcessor(dataReader, createTransformDataWriter(nodeService.findIdentity(),
                        targetNode, new ProtocolDataWriter(nodeService.findIdentityNodeId(),
                                writer, targetNode.requires13Compatiblity(), false, false)), "extract range").process(ctx);
                foundBatch = true;
            }
        }
        return foundBatch;
    }

    protected TransformWriter createTransformDataWriter(Node identity, Node targetNode,
            IDataWriter extractWriter) {
        List<TransformTableNodeGroupLink> transformsList = null;
        if (targetNode != null) {
            transformsList = transformService.findTransformsFor(
                    new NodeGroupLink(identity.getNodeGroupId(), targetNode.getNodeGroupId()),
                    TransformPoint.EXTRACT);
        }
        TransformTable[] transforms = transformsList != null ? transformsList
                .toArray(new TransformTable[transformsList.size()]) : null;
        TransformWriter transformExtractWriter = new TransformWriter(symmetricDialect.getTargetPlatform(), TransformPoint.EXTRACT, extractWriter,
                transformService.getColumnTransforms(), transforms);
        return transformExtractWriter;
    }

    @Override
    public RemoteNodeStatuses queueWork(boolean force) {
        final RemoteNodeStatuses statuses = new RemoteNodeStatuses(configurationService.getChannels(false));
        Node identity = nodeService.findIdentity();
        if (identity != null) {
            if (force || clusterService.lock(ClusterConstants.INITIAL_LOAD_EXTRACT)) {
                try {
                    updateExtractRequestsForThreading();
                    List<NodeQueuePair> nodes = getExtractRequestNodes();
                    for (NodeQueuePair pair : nodes) {
                        if (pair.getQueue().equals(Constants.QUEUE_RELOAD)) {
                            if (pair.getExtractThreadId() != null) {
                                queue(pair.getNodeId(), pair.getQueue() + Constants.DELIMITER_QUEUE_THREAD + pair.getExtractThreadId(), statuses);
                            }
                        } else {
                            queue(pair.getNodeId(), pair.getQueue(), statuses);
                        }
                    }
                } finally {
                    if (!force) {
                        clusterService.unlock(ClusterConstants.INITIAL_LOAD_EXTRACT);
                    }
                }
            }
        } else {
            log.debug("Not running initial load extract service because this node does not have an identity");
        }
        return statuses;
    }

    protected void queue(String nodeId, String queue, RemoteNodeStatuses statuses) {
        final NodeCommunication.CommunicationType TYPE = NodeCommunication.CommunicationType.EXTRACT;
        int availableThreads = nodeCommunicationService.getAvailableThreads(TYPE);
        NodeCommunication lock = nodeCommunicationService.find(nodeId, queue, TYPE);
        if (availableThreads > 0) {
            nodeCommunicationService.execute(lock, statuses, this);
        }
    }

    public List<NodeQueuePair> getExtractRequestNodes() {
        return sqlTemplate.query(getSql("selectNodeIdsForExtractSql"), new NodeQueuePairMapper(),
                ExtractStatus.NE.name(), engine.getNodeId());
    }

    protected void updateExtractRequestsForThreading() {
        Map<String, Channel> channelMap = configurationService.getChannels(false);
        Channel reloadChannel = channelMap != null ? channelMap.get(Constants.CHANNEL_RELOAD) : null;
        if (reloadChannel != null && reloadChannel.getQueue().equals(Constants.QUEUE_RELOAD)) {
            List<ExtractRequest> extractRequests = getExtractRequestsForThreading();
            IReloadQueueThreadAssigner assigner = engine.getExtensionService().getExtensionPoint(IReloadQueueThreadAssigner.class);
            if (assigner != null) {
                Collection<ExtractRequest> requestsToUpdate = assigner.assignThreadsToExtractRequests(extractRequests, reloadChannel);
                updateExtractRequestsForThreading(requestsToUpdate);
            } else if (!extractRequests.isEmpty()) {
                log.info("Found {} extract requests to assign threads for extract and load", extractRequests.size());
                for (ExtractRequest extractRequest : extractRequests) {
                    extractRequest.setExtractThreadId(0);
                    extractRequest.setLoadThreadId(0);
                }
                updateExtractRequestsForThreading(extractRequests);
            }
        }
    }

    protected List<ExtractRequest> getExtractRequestsForThreading() {
        return sqlTemplate.query(getSql("selectExtractRequestsForThreadingSql"), new ExtractRequestMapper(), engine.getNodeId(), Constants.QUEUE_RELOAD,
                ExtractStatus.NE.name(), ExtractStatus.OK.name());
    }

    protected void updateExtractRequestsForThreading(Collection<ExtractRequest> extractRequests) {
        String sourceNodeId = engine.getNodeId();
        TableReloadStatus reloadStatus = null;
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            for (ExtractRequest request : extractRequests) {
                if (request.getStatus() == ExtractStatus.NE && (reloadStatus == null || reloadStatus.getLoadId() != request.getLoadId())) {
                    reloadStatus = dataService.getTableReloadStatusByLoadIdAndSourceNodeId(request.getLoadId(), sourceNodeId);
                    if (reloadStatus.getRowsLoaded() == 0) {
                        log.info("Assigning load ID {} system {} tables to thread 0", request.getLoadId(), engine.getParameterService().getTablePrefix());
                        transaction.prepareAndExecute(getSql("updateOutgoingBatchesForSetupThreadSql"), new Object[] { 0, request.getLoadId(),
                                engine.getParameterService().getTablePrefix().toLowerCase() + "%" }, new int[] { Types.INTEGER,
                                        symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
                    }
                }
                transaction.prepareAndExecute(getSql("updateExtractRequestsThreadsSql"), new Object[] { request.getExtractThreadId(), request.getLoadThreadId(),
                        request.getRequestId() }, new int[] { Types.INTEGER, Types.INTEGER, symmetricDialect.getSqlTypeForIds() });
                if (request.getLoadThreadId() != null) {
                    transaction.prepareAndExecute(getSql("updateOutgoingBatchesForThreadSql"), new Object[] { request.getLoadThreadId(), request.getLoadId(),
                            request.getStartBatchId(), request.getEndBatchId(), request.getStartBatchId(), request.getTableName().toLowerCase() },
                            new int[] { Types.INTEGER, symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds(),
                                    symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
                }
            }
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

    private static class NodeQueuePair {
        private String nodeId;
        private String queue;
        private Integer extractThreadId;

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getQueue() {
            return queue;
        }

        public void setQueue(String queue) {
            this.queue = queue;
        }

        public Integer getExtractThreadId() {
            return extractThreadId;
        }

        public void setExtractThreadId(Integer extractThreadId) {
            this.extractThreadId = extractThreadId;
        }
    }

    static class NodeQueuePairMapper implements ISqlRowMapper<NodeQueuePair> {
        @Override
        public NodeQueuePair mapRow(Row row) {
            NodeQueuePair pair = new NodeQueuePair();
            pair.setNodeId(row.getString("node_id"));
            pair.setQueue(row.getString("queue"));
            pair.setExtractThreadId(row.getInteger("extract_thread_id"));
            return pair;
        }
    }

    protected List<ExtractRequest> getExtractRequestsForNode(NodeCommunication nodeCommunication, String queue, QueueThread queueThread) {
        if (queueThread.isUsingThreading()) {
            return sqlTemplate.query(getSql("selectExtractRequestForNodeThreadSql"), new ExtractRequestMapper(), nodeCommunication.getNodeId(), queue,
                    queueThread.getThreadId(), ExtractRequest.ExtractStatus.NE.name(), engine.getNodeId());
        }
        return sqlTemplate.query(getSql("selectExtractRequestForNodeSql"), new ExtractRequestMapper(), nodeCommunication.getNodeId(), queue,
                ExtractRequest.ExtractStatus.NE.name(), engine.getNodeId());
    }

    protected ExtractRequest getExtractRequestForBatch(OutgoingBatch batch) {
        return sqlTemplate.queryForObject(getSql("selectExtractRequestForBatchSql"),
                new ExtractRequestMapper(), batch.getBatchId(), batch.getBatchId(), batch.getNodeId(), batch.getLoadId(), engine.getNodeId());
    }

    protected Map<Long, List<ExtractRequest>> getExtractChildRequestsForNode(NodeCommunication nodeCommunication, String queue, QueueThread queueThread) {
        Map<Long, List<ExtractRequest>> requests = new HashMap<Long, List<ExtractRequest>>();
        List<ExtractRequest> childRequests = null;
        if (queueThread.isUsingThreading()) {
            childRequests = sqlTemplate.query(getSql("selectExtractChildRequestForNodeThreadSql"), new ExtractRequestMapper(), nodeCommunication.getNodeId(),
                    queue, queueThread.getThreadId(), ExtractRequest.ExtractStatus.NE.name(), engine.getNodeId());
        } else {
            childRequests = sqlTemplate.query(getSql("selectExtractChildRequestForNodeSql"), new ExtractRequestMapper(), nodeCommunication.getNodeId(),
                    queue, ExtractRequest.ExtractStatus.NE.name(), engine.getNodeId());
        }
        for (ExtractRequest childRequest : childRequests) {
            List<ExtractRequest> childList = requests.get(childRequest.getParentRequestId());
            if (childList == null) {
                childList = new ArrayList<ExtractRequest>();
                requests.put(childRequest.getParentRequestId(), childList);
            }
            childList.add(childRequest);
        }
        return requests;
    }

    protected List<ExtractRequest> getExtractChildRequestsForNode(ExtractRequest parentRequest) {
        return sqlTemplate.query(getSql("selectExtractChildRequestsByParentSql"), new ExtractRequestMapper(), parentRequest.getRequestId(), engine.getNodeId());
    }

    @Override
    public void resetExtractRequest(OutgoingBatch batch) {
        ExtractRequest request = getExtractRequestForBatch(batch);
        if (request != null) {
            List<ProcessInfo> infos = statisticManager.getProcessInfos();
            for (ProcessInfo info : infos) {
                if (info.getProcessType().equals(ProcessType.INITIAL_LOAD_EXTRACT_JOB) &&
                        request.getNodeId().equals(info.getTargetNodeId()) &&
                        info.getCurrentBatchId() >= request.getStartBatchId() &&
                        info.getCurrentBatchId() <= request.getEndBatchId()) {
                    log.info("Sending interrupt to " + info.getKey());
                    info.getThread().interrupt();
                }
            }
            List<OutgoingBatch> batches = outgoingBatchService.getOutgoingBatchRange(request.getStartBatchId(), request.getEndBatchId()).getBatches();
            List<ExtractRequest> childRequests = null;
            if (request.getParentRequestId() == 0) {
                childRequests = getExtractChildRequestsForNode(request);
            }
            restartExtractRequest(batches, request, childRequests);
        } else {
            log.warn("Unable to find extract request for node {} batch {} load {}", batch.getNodeId(), batch.getBatchId(), batch.getLoadId());
        }
    }

    @Override
    public ExtractRequest requestExtractRequest(ISqlTransaction transaction, String nodeId, String queue,
            TriggerRouter triggerRouter, long startBatchId, long endBatchId, long loadId, String table, long rows, long parentRequestId) {
        long requestId = 0;
        if (platform.supportsMultiThreadedTransactions()) {
            requestId = sequenceService.nextVal(Constants.SEQUENCE_EXTRACT_REQ);
        } else {
            requestId = sequenceService.nextVal(transaction, Constants.SEQUENCE_EXTRACT_REQ);
        }
        transaction.prepareAndExecute(getSql("insertExtractRequestSql"),
                new Object[] { requestId, engine.getNodeId(), nodeId, queue, ExtractStatus.LS.name(), startBatchId, endBatchId,
                        triggerRouter.getTrigger().getTriggerId(), triggerRouter.getRouter().getRouterId(), loadId,
                        table, rows, parentRequestId, new Date(), new Date() },
                new int[] { Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.BIGINT,
                        Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.BIGINT, Types.BIGINT,
                        Types.TIMESTAMP, Types.TIMESTAMP });
        ExtractRequest request = new ExtractRequest();
        request.setRequestId(requestId);
        request.setNodeId(nodeId);
        request.setQueue(queue);
        request.setStatus(ExtractStatus.LS);
        request.setStartBatchId(startBatchId);
        request.setEndBatchId(endBatchId);
        request.setRouterId(triggerRouter.getRouterId());
        request.setLoadId(loadId);
        request.setTableName(table);
        request.setRows(rows);
        request.setParentRequestId(parentRequestId);
        return request;
    }

    protected void updateExtractRequestStatus(ISqlTransaction transaction, long extractId,
            ExtractStatus status, long extractedRows, long extractedMillis) {
        transaction.prepareAndExecute(getSql("updateExtractRequestStatus"), status.name(), new Date(), extractedRows, extractedMillis, extractId);
    }

    protected boolean canProcessExtractRequest(ExtractRequest request, CommunicationType communicationType) {
        return !request.getTableName().equalsIgnoreCase(TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_SNAPSHOT));
    }

    /**
     * This is a callback method used by the NodeCommunicationService that extracts an initial load in the background.
     */
    @Override
    public void execute(NodeCommunication nodeCommunication, RemoteNodeStatus status) {
        if (!isApplicable(nodeCommunication)) {
            log.debug("{} failed isApplicable check and will not run.", this);
            return;
        }
        QueueThread queueThread = new QueueThread(nodeCommunication.getQueue());
        String queue = queueThread.getQueueName();
        List<ExtractRequest> requests = getExtractRequestsForNode(nodeCommunication, queue, queueThread);
        Map<Long, List<ExtractRequest>> allChildRequests = null;
        long ts = System.currentTimeMillis();
        if (requests.size() > 0) {
            allChildRequests = getExtractChildRequestsForNode(nodeCommunication, queue, queueThread);
        }
        long maxProcessTime = parameterService.getLong(ParameterConstants.INITIAL_LOAD_EXTRACT_MAX_PROCESS_TIME_MS, 3600000);
        for (int i = 0; i < requests.size() && (System.currentTimeMillis() - ts) <= maxProcessTime; i++) {
            ExtractRequest request = requests.get(i);
            if (!canProcessExtractRequest(request, nodeCommunication.getCommunicationType())) {
                continue;
            }
            Node identity = nodeService.findIdentity();
            Node targetNode = nodeService.findNode(nodeCommunication.getNodeId(), true);
            log.info("Starting request {} to extract table {} into batches {} through {} for node {} on queue {}.", request.getRequestId(),
                    request.getTableName(), request.getStartBatchId(), request.getEndBatchId(), request.getNodeId(), queue);
            List<OutgoingBatch> batches = outgoingBatchService.getOutgoingBatchRange(request.getStartBatchId(), request.getEndBatchId()).getBatches();
            ProcessInfo processInfo = statisticManager.newProcessInfo(new ProcessInfoKey(identity
                    .getNodeId(), nodeCommunication.getQueue(), nodeCommunication.getNodeId(),
                    getProcessType()));
            processInfo.setTotalBatchCount(batches.size());
            List<ExtractRequest> childRequests = allChildRequests.get(request.getRequestId());
            try {
                boolean isCanceled = true;
                boolean isRestarted = false;
                for (OutgoingBatch outgoingBatch : batches) {
                    if (outgoingBatch.getStatus() != Status.OK && outgoingBatch.getStatus() != Status.IG) {
                        isCanceled = false;
                    }
                    if (outgoingBatch.getStatus() != Status.RQ) {
                        isRestarted = true;
                    }
                }
                if (!isCanceled) {
                    Channel channel = configurationService.getChannel(batches.get(0).getChannelId());
                    /*
                     * "Trick" the extractor to extract one reload batch, but we will split it across the N batches when writing it
                     */
                    OutgoingBatch firstBatch = batches.get(0);
                    processInfo.setCurrentLoadId(firstBatch.getLoadId());
                    processInfo.setStatus(ProcessStatus.QUERYING);
                    if (isRestarted) {
                        restartExtractRequest(batches, request, childRequests);
                    }
                    MultiBatchStagingWriter multiBatchStagingWriter = buildMultiBatchStagingWriter(request, childRequests, identity, targetNode, batches,
                            processInfo, channel, isRestarted);
                    extractOutgoingBatch(processInfo, targetNode, multiBatchStagingWriter,
                            firstBatch, false, false, ExtractMode.FOR_SYM_CLIENT, new ClusterLockRefreshListener(clusterService));
                    checkSendDeferredConstraints(request, childRequests, targetNode);
                } else {
                    log.info(
                            "Batches already had an OK status for request {} to extract table {} for batches {} through {} for node {} on queue {}.  Not extracting.",
                            request.getRequestId(), request.getTableName(), request.getStartBatchId(), request.getEndBatchId(), request.getNodeId(), queue);
                }
                ISqlTransaction transaction = null;
                try {
                    transaction = sqlTemplate.startSqlTransaction();
                    long extractMillis = new Date().getTime() - processInfo.getStartTime().getTime();
                    updateExtractRequestStatus(transaction, request.getRequestId(), ExtractStatus.OK, processInfo.getCurrentDataCount(), extractMillis);
                    if (childRequests != null) {
                        for (ExtractRequest childRequest : childRequests) {
                            updateExtractRequestStatus(transaction, childRequest.getRequestId(), ExtractStatus.OK, processInfo.getCurrentDataCount(),
                                    extractMillis);
                        }
                    }
                    transaction.commit();
                    log.info("Done with request {} to extract table {} into batches {} through {} for node {} on queue {}", request.getRequestId(),
                            request.getTableName(), request.getStartBatchId(), request.getEndBatchId(), request.getNodeId(), queue);
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
                releaseMissedExtractRequests();
                processInfo.setStatus(ProcessInfo.ProcessStatus.OK);
            } catch (CancellationException ex) {
                log.info("Interrupted on request {} to extract table {} for batches {} through {} for node {} on queue {}", request.getRequestId(),
                        request.getTableName(), request.getStartBatchId(), request.getEndBatchId(), request.getNodeId(), queue);
                processInfo.setStatus(ProcessInfo.ProcessStatus.OK);
            } catch (RuntimeException ex) {
                log.warn("Failed on request {} to extract table {} into batches {} through {} for node {} on queue {}", request.getRequestId(),
                        request.getTableName(), request.getStartBatchId(), request.getEndBatchId(), request.getNodeId(), queue);
                processInfo.setStatus(ProcessInfo.ProcessStatus.ERROR);
                if (ex instanceof StagingLowFreeSpace) {
                    log.error("Extract load is disabled because disk is almost full: {}", ex.getMessage());
                    break;
                } else {
                    throw ex;
                }
            }
        }
    }

    protected void restartExtractRequest(List<OutgoingBatch> batches, ExtractRequest request, List<ExtractRequest> childRequests) {
        /*
         * This extract request was interrupted and must start over
         */
        log.info("Resetting status of request {} to extract table {} into batches {} through {} for node {}",
                request.getRequestId(), request.getTableName(), request.getStartBatchId(), request.getEndBatchId(), request.getNodeId());
        long batchLoadedCount = 0;
        if (request.getLastLoadedBatchId() > 0) {
            batchLoadedCount = request.getLastLoadedBatchId() - request.getStartBatchId() + 1;
        }
        long rowLoadedCount = request.getLoadedRows();
        List<ExtractRequest> allRequests = new ArrayList<ExtractRequest>();
        allRequests.add(request);
        if (childRequests != null) {
            allRequests.addAll(childRequests);
        }
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            for (ExtractRequest extractRequest : allRequests) {
                // reset statistics for extract request
                transaction.prepareAndExecute(getSql("restartExtractRequest"), ExtractStatus.NE.name(), extractRequest.getRequestId(), extractRequest
                        .getNodeId());
                // back out statistics from table reload request
                if (batchLoadedCount > 0 || rowLoadedCount > 0) {
                    dataService.updateTableReloadStatusDataLoaded(transaction, extractRequest.getLoadId(), engine.getNodeId(),
                            extractRequest.getStartBatchId(), (int) batchLoadedCount * -1, false);
                }
                // set status of batches back to requested
                outgoingBatchService.updateOutgoingBatchStatus(transaction, Status.RQ, extractRequest.getNodeId(), extractRequest.getStartBatchId(),
                        extractRequest.getEndBatchId());
            }
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
        // remove the batches from staging
        for (OutgoingBatch outgoingBatch : batches) {
            IStagedResource resource = getStagedResource(outgoingBatch);
            if (resource != null) {
                resource.delete();
            }
            if (childRequests != null) {
                long batchIndex = outgoingBatch.getBatchId() - request.getStartBatchId();
                for (ExtractRequest extractRequest : childRequests) {
                    OutgoingBatch childBatch = new OutgoingBatch(extractRequest.getNodeId(), outgoingBatch.getChannelId(), outgoingBatch.getStatus());
                    childBatch.setBatchId(outgoingBatch.getBatchId() + batchIndex);
                    resource = getStagedResource(childBatch);
                    if (resource != null) {
                        resource.delete();
                    }
                }
            }
        }
        // clear the incoming batch table for the batches at the target node, so the batches won't be skipped
        String symIncomingBatch = TableConstants.getTableName(parameterService.getTablePrefix(), TableConstants.SYM_INCOMING_BATCH);
        String nodeIdentityId = nodeService.findIdentityNodeId();
        for (ExtractRequest extractRequest : allRequests) {
            String sql = "delete from " + symIncomingBatch + " where node_id = '" + nodeIdentityId +
                    "' and batch_id between " + extractRequest.getStartBatchId() + " and " + extractRequest.getEndBatchId();
            dataService.sendSQL(extractRequest.getNodeId(), sql);
        }
        for (ExtractRequest extractRequest : allRequests) {
            TableReloadStatus reloadStatus = dataService.getTableReloadStatusByLoadIdAndSourceNodeId(extractRequest.getLoadId(), nodeIdentityId);
            OutgoingBatches setupBatches = outgoingBatchService.getOutgoingBatchByLoadRangeAndTable(extractRequest.getLoadId(), 1,
                    reloadStatus.getStartDataBatchId() - 1, extractRequest.getTableName().toLowerCase());
            // clear incoming batch table for all batches at the target node that were used to setup this load for a specific table (delete, truncate, etc)
            for (OutgoingBatch batch : setupBatches.getBatches()) {
                String sql = "delete from " + symIncomingBatch + " where node_id = '" + nodeIdentityId +
                        "' and batch_id = " + batch.getBatchId();
                dataService.sendSQL(batch.getNodeId(), sql);
                // set status of these batches back to new so they are resent
                batch.setStatus(Status.NE);
                outgoingBatchService.updateOutgoingBatch(batch);
            }
        }
    }

    @Override
    public void releaseMissedExtractRequests() {
        List<Long> requestIds = sqlTemplateDirty.query(getSql("selectExtractChildRequestIdsMissed"), new LongMapper(), Status.NE.name(), Status.OK.name(),
                engine.getNodeId(), engine.getNodeId());
        if (requestIds != null && requestIds.size() > 0) {
            log.info("Releasing {} child extract requests that missed processing by parent node", requestIds.size());
            for (Long requestId : requestIds) {
                sqlTemplate.update(getSql("releaseExtractChildRequestFromParent"), requestId);
            }
        }
    }

    protected void checkSendDeferredConstraints(ExtractRequest request, List<ExtractRequest> childRequests, Node targetNode) {
        if (parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)) {
            TableReloadRequest reloadRequest = dataService.getTableReloadRequest(request.getLoadId(), request.getTriggerId(), request.getRouterId());
            if ((reloadRequest != null && reloadRequest.isCreateTable()) ||
                    (reloadRequest == null && parameterService.is(ParameterConstants.INITIAL_LOAD_CREATE_SCHEMA_BEFORE_RELOAD))) {
                boolean success = false;
                Trigger trigger = triggerRouterService.getTriggerById(request.getTriggerId());
                if (trigger != null) {
                    List<TriggerHistory> histories = triggerRouterService.getActiveTriggerHistories(triggerRouterService.getTriggerById(request
                            .getTriggerId()));
                    if (histories != null && histories.size() > 0) {
                        for (TriggerHistory history : histories) {
                            Channel channel = configurationService.getChannel(trigger.getReloadChannelId());
                            if (!channel.isFileSyncFlag() && history.getSourceTableName().equalsIgnoreCase(request.getTableName())) {
                                Data data = new Data(history.getSourceTableName(), DataEventType.CREATE, null, null,
                                        history, trigger.getChannelId(), String.valueOf(request.getLoadId()), null);
                                data.setNodeList(targetNode.getNodeId());
                                log.info("Deferred create event for load {} table {} on channel {}", request.getLoadId(), history.getSourceTableName(),
                                        trigger.getChannelId());
                                dataService.insertData(data);
                                if (childRequests != null) {
                                    for (ExtractRequest childRequest : childRequests) {
                                        data = new Data(history.getSourceTableName(), DataEventType.CREATE, null, null,
                                                history, trigger.getChannelId(), String.valueOf(childRequest.getLoadId()), null);
                                        data.setNodeList(childRequest.getNodeId());
                                        dataService.insertData(data);
                                    }
                                }
                            }
                        }
                        success = true;
                    }
                }
                if (!success) {
                    log.warn("Unable to send deferred constraints for trigger '{}' router '{}' in load {}",
                            reloadRequest.getTriggerId(), reloadRequest.getRouterId(), reloadRequest.getLoadId());
                }
            }
        }
    }

    protected boolean isApplicable(NodeCommunication nodeCommunication) {
        return nodeCommunication.getCommunicationType() != CommunicationType.FILE_XTRCT;
    }

    protected MultiBatchStagingWriter buildMultiBatchStagingWriter(ExtractRequest request, List<ExtractRequest> childRequests, Node sourceNode,
            Node targetNode, List<OutgoingBatch> batches, ProcessInfo processInfo, Channel channel, boolean isRestarted) {
        boolean isSingleLocalTarget = false;
        if (childRequests == null || childRequests.size() == 0) {
            ISymmetricEngine targetEngine = AbstractSymmetricEngine.findEngineByNodeId(request.getNodeId());
            isSingleLocalTarget = targetEngine != null;
        }
        MultiBatchStagingWriter multiBatchStatingWriter = new MultiBatchStagingWriter(engine, request, childRequests, sourceNode.getNodeId(),
                batches, channel.getMaxBatchSize(), processInfo, isRestarted, isSingleLocalTarget);
        return multiBatchStatingWriter;
    }

    protected ProcessType getProcessType() {
        return ProcessType.INITIAL_LOAD_EXTRACT_JOB;
    }

    class ExtractRequestMapper implements ISqlRowMapper<ExtractRequest> {
        @Override
        public ExtractRequest mapRow(Row row) {
            ExtractRequest request = new ExtractRequest();
            request.setNodeId(row.getString("node_id"));
            request.setRequestId(row.getLong("request_id"));
            request.setStartBatchId(row.getLong("start_batch_id"));
            request.setEndBatchId(row.getLong("end_batch_id"));
            request.setStatus(ExtractStatus.valueOf(row.getString("status").toUpperCase()));
            request.setCreateTime(row.getDateTime("create_time"));
            request.setLastUpdateTime(row.getDateTime("last_update_time"));
            request.setTriggerId(row.getString("trigger_id"));
            request.setRouterId(row.getString("router_id"));
            request.setTriggerRouter(triggerRouterService.findTriggerRouterById(
                    row.getString("trigger_id"), row.getString("router_id"), false));
            request.setQueue(row.getString("queue"));
            request.setLoadId(row.getLong("load_id"));
            request.setTableName(row.getString("table_name"));
            request.setRows(row.getLong("total_rows"));
            request.setTransferredRows(row.getLong("transferred_rows"));
            request.setLastTransferredBatchId(row.getLong("last_transferred_batch_id"));
            request.setLoadedRows(row.getLong("loaded_rows"));
            request.setLastLoadedBatchId(row.getLong("last_loaded_batch_id"));
            request.setTransferredMillis(row.getLong("transferred_millis"));
            request.setLoadedMillis(row.getLong("loaded_millis"));
            request.setParentRequestId(row.getLong("parent_request_id"));
            request.setExtractedRows(row.getLong("extracted_rows"));
            request.setExtractedMillis(row.getLong("extracted_millis"));
            request.setExtractThreadId(row.getInteger("extract_thread_id"));
            request.setLoadThreadId(row.getInteger("load_thread_id"));
            request.setNumRowsBulkLoaded(row.getLong("bulk_rows_loaded"));
            return request;
        }
    }

    @Override
    public void removeBatchFromStaging(OutgoingBatch batch) {
        IStagedResource resource = getStagedResource(batch);
        if (resource != null) {
            resource.delete();
        } else {
            log.info("Could not remove batch {} from staging because it did not exist", batch.getNodeBatchId());
        }
    }

    @Override
    public void updateExtractRequestStatuses(ISqlTransaction transaction, long loadId, String sourceNodeId,
            String fromStatus, String toStatus) {
        transaction.prepareAndExecute(getSql("updateExtractRequestStatuses"),
                toStatus, new Date(), loadId, sourceNodeId, fromStatus);
    }

    static class FutureExtractStatus {
        boolean shouldExtractSkip;
        int batchExtractCount;
        int byteExtractCount;
    }

    static class FutureOutgoingBatch {
        OutgoingBatch outgoingBatch;
        boolean isRetry;
        boolean isExtractSkipped;

        public FutureOutgoingBatch(OutgoingBatch outgoingBatch, boolean isRetry) {
            this.outgoingBatch = outgoingBatch;
            this.isRetry = isRetry;
        }

        public OutgoingBatch getOutgoingBatch() {
            return outgoingBatch;
        }

        public boolean isRetry() {
            return isRetry;
        }
    }

    static class BatchLock {
        public BatchLock(String semaphoreKey) {
            this.semaphoreKey = semaphoreKey;
        }

        public void acquire() throws InterruptedException {
            inMemoryLock.acquire();
        }

        public void release() {
            inMemoryLock.release();
        }

        String semaphoreKey;
        private Semaphore inMemoryLock = new Semaphore(1);
        StagingFileLock fileLock;
        int referenceCount = 0;
    }
}
