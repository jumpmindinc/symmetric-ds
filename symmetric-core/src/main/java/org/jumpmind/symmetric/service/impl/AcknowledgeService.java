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
import java.util.Iterator;
import java.util.List;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.NumberMapper;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ErrorConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.RegistrationRequest.RegistrationStatus;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.BatchAckResult;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.OutgoingBatches;
import org.jumpmind.symmetric.model.RegistrationRequest;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.statistic.RouterStats;
import org.jumpmind.symmetric.transport.IAcknowledgeEventListener;

/**
 * @see IAcknowledgeService
 */
public class AcknowledgeService extends AbstractService implements IAcknowledgeService {
    private ISymmetricEngine engine;

    public AcknowledgeService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        setSqlMap(new AcknowledgeServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    public BatchAckResult ack(final BatchAck batch) {
        IRegistrationService registrationService = engine.getRegistrationService();
        IOutgoingBatchService outgoingBatchService = engine.getOutgoingBatchService();
        BatchAckResult result = new BatchAckResult(batch);
        for (IAcknowledgeEventListener listener : engine.getExtensionService().getExtensionPointList(IAcknowledgeEventListener.class)) {
            listener.onAcknowledgeEvent(batch);
        }
        if (batch.getBatchId() == Constants.VIRTUAL_BATCH_FOR_REGISTRATION) {
            if (batch.isOk()) {
                registrationService.markNodeAsRegistered(batch.getNodeId());
            } else if (batch.getSqlCode() != 0 || batch.getSqlMessage() != null) {
                Node requestingNode = engine.getNodeService().findNode(batch.getNodeId());
                if (requestingNode != null) {
                    RegistrationRequest request = registrationService.getLatestRegistrationRequest(
                            requestingNode.getNodeGroupId(), requestingNode.getExternalId());
                    if (request != null) {
                        request.setStatus(RegistrationStatus.ER);
                        request.setErrorMessage(getErrorMessage(batch));
                        request.setAttemptCount(request.getAttemptCount() + 1);
                        registrationService.updateRegistrationRequest(request);
                    }
                }
            }
        } else if (batch.getBatchId() != Constants.BATCH_ID_MISSING) {
            OutgoingBatch outgoingBatch = outgoingBatchService.findOutgoingBatch(batch.getBatchId(), batch.getNodeId());
            Status status = batch.isResend() ? Status.RS : batch.isOk() ? Status.OK : Status.ER;
            if (outgoingBatch != null && outgoingBatch.getStatus() != Status.RQ) {
                // Allow an outside system/user to indicate that a batch is OK
                if (outgoingBatch.getStatus() == Status.IG && status == Status.OK) {
                    log.info("Ignoring batch {}", outgoingBatch.getNodeBatchId());
                } else if (outgoingBatch.getStatus() == Status.OK && status != Status.OK) {
                    log.info("Setting status to ignore for batch {} because status was set to OK by user", outgoingBatch.getNodeBatchId());
                    status = Status.IG;
                }
                boolean isFirstTimeAsOkStatus = outgoingBatch.getStatus() != Status.OK && status == Status.OK;
                boolean isFirstTimeAsErStatus = !outgoingBatch.isErrorFlag() && status == Status.ER;
                outgoingBatch.setStatus(status);
                outgoingBatch.setBulkLoaderFlag(batch.isBulkLoaderFlag());
                outgoingBatch.setErrorFlag(status == Status.ER);
                outgoingBatch.setNetworkMillis(batch.getNetworkMillis());
                outgoingBatch.setFilterMillis(batch.getFilterMillis());
                outgoingBatch.setLoadMillis(batch.getLoadMillis());
                outgoingBatch.setLoadStartTime(new Date(batch.getStartTime()));
                outgoingBatch.setSqlCode(batch.getSqlCode());
                outgoingBatch.setSqlState(batch.getSqlState());
                outgoingBatch.setSqlMessage(batch.getSqlMessage());
                outgoingBatch.setLoadRowCount(batch.getLoadRowCount());
                outgoingBatch.setLoadInsertRowCount(batch.getLoadInsertRowCount());
                outgoingBatch.setLoadUpdateRowCount(batch.getLoadUpdateRowCount());
                outgoingBatch.setTransformLoadMillis(batch.getTransformLoadMillis());
                outgoingBatch.setLoadDeleteRowCount(batch.getLoadDeleteRowCount());
                outgoingBatch.setFallbackInsertCount(batch.getFallbackInsertCount());
                outgoingBatch.setFallbackUpdateCount(batch.getFallbackUpdateCount());
                outgoingBatch.setConflictWinCount(batch.getConflictWinCount());
                outgoingBatch.setConflictLoseCount(batch.getConflictLoseCount());
                outgoingBatch.setIgnoreRowCount(batch.getIgnoreRowCount());
                outgoingBatch.setMissingDeleteCount(batch.getMissingDeleteCount());
                outgoingBatch.setSkipCount(batch.getSkipCount());
                if (batch.isIgnored()) {
                    outgoingBatch.incrementIgnoreCount();
                }
                if (status == Status.OK) {
                    outgoingBatch.setFailedDataId(0);
                    outgoingBatch.setFailedLineNumber(0);
                }
                boolean isNewError = false;
                if (status == Status.ER && batch.getErrorLine() != 0) {
                    if (outgoingBatch.isLoadFlag()) {
                        isNewError = outgoingBatch.getSentCount() == 1;
                    } else if (batch.getErrorLine() != outgoingBatch.getFailedLineNumber()) {
                        String sql = getSql("selectDataIdSql");
                        if (parameterService.is(ParameterConstants.DBDIALECT_ORACLE_SEQUENCE_NOORDER, false)) {
                            sql = getSql("selectDataIdByCreateTimeSql");
                        } else if (parameterService.is(ParameterConstants.ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED, true)) {
                            sql += getSql("orderByDataId");
                        }
                        List<Number> ids = sqlTemplateDirty.query(sql, new NumberMapper(), outgoingBatch.getBatchId());
                        if (ids.size() >= batch.getErrorLine()) {
                            long failedDataId = ids.get((int) batch.getErrorLine() - 1).longValue();
                            isNewError = outgoingBatch.getFailedDataId() == 0 || outgoingBatch.getFailedDataId() != failedDataId;
                            outgoingBatch.setFailedDataId(failedDataId);
                        }
                    }
                    outgoingBatch.setFailedLineNumber(batch.getErrorLine());
                }
                if (status == Status.ER) {
                    boolean suppressError = false;
                    if (isNewError) {
                        engine.getStatisticManager().incrementDataLoadedOutgoingErrors(outgoingBatch.getChannelId(), 1);
                    }
                    if (isNewError && outgoingBatch.getSqlCode() == ErrorConstants.FK_VIOLATION_CODE) {
                        if (!outgoingBatch.isLoadFlag() && outgoingBatch.getReloadRowCount() == 0 &&
                                parameterService.is(ParameterConstants.AUTO_RESOLVE_FOREIGN_KEY_VIOLATION)) {
                            try {
                                engine.getDataService().reloadMissingForeignKeyRows(outgoingBatch.getBatchId(), outgoingBatch.getNodeId(),
                                        outgoingBatch.getFailedDataId(), outgoingBatch.getFailedLineNumber());
                            } catch (Exception e) {
                                log.error("Failed to request a reload of missing foreign key rows for batch " + outgoingBatch.getNodeBatchId() +
                                        " data ID " + outgoingBatch.getFailedDataId(), e);
                                outgoingBatch.setFailedLineNumber(0);
                                outgoingBatch.setFailedDataId(0);
                            }
                        }
                        if (outgoingBatch.isLoadFlag() && parameterService.is(ParameterConstants.AUTO_RESOLVE_FOREIGN_KEY_VIOLATION_REVERSE_RELOAD)) {
                            suppressError = true;
                        }
                    }
                    if (outgoingBatch.getSqlCode() == ErrorConstants.PROTOCOL_VIOLATION_CODE
                            && ErrorConstants.PROTOCOL_VIOLATION_STATE.equals(outgoingBatch.getSqlState())) {
                        if (outgoingBatch.isLoadFlag()) {
                            log.info(
                                    "The batch {} may be corrupt in staging. Not removing the batch because it was a load batch, but you may need to clear the batch from staging manually.",
                                    outgoingBatch.getNodeBatchId());
                        } else {
                            IStagedResource resource = engine.getStagingManager().find(Constants.STAGING_CATEGORY_OUTGOING,
                                    outgoingBatch.getStagedLocation(), outgoingBatch.getBatchId());
                            if (resource != null) {
                                log.info("The batch {} may be corrupt in staging, so removing it.", outgoingBatch.getNodeBatchId());
                                resource.delete();
                                suppressError = isNewError;
                            }
                        }
                    }
                    if (isNewError && (outgoingBatch.getSqlCode() == ErrorConstants.DEADLOCK_CODE ||
                            outgoingBatch.getSqlCode() == ErrorConstants.CONFLICT_CODE)) {
                        suppressError = true;
                    }
                    if (suppressError) {
                        outgoingBatch.setErrorFlag(false);
                    } else {
                        log.error("The outgoing batch {} failed: {}", outgoingBatch.getNodeBatchId(), getErrorMessage(batch));
                        RouterStats routerStats = engine.getStatisticManager().getRouterStatsByBatch(batch.getBatchId());
                        if (routerStats != null) {
                            log.info("Router stats for batch " + outgoingBatch.getBatchId() + ": " + routerStats);
                        }
                    }
                } else if (status == Status.RS) {
                    log.info("The outgoing batch {} received resend request", outgoingBatch.getNodeBatchId());
                }
                ISqlTransaction transaction = null;
                try {
                    transaction = sqlTemplate.startSqlTransaction();
                    outgoingBatchService.updateOutgoingBatch(transaction, outgoingBatch);
                    if (status == Status.OK && outgoingBatch.getLoadId() > 0) {
                        if (isFirstTimeAsOkStatus) {
                            engine.getDataExtractorService().updateExtractRequestLoadTime(transaction, new Date(), outgoingBatch);
                        } else {
                            log.info("Ignoring duplicate load status update for load ID {} with batch {}", outgoingBatch.getLoadId(),
                                    outgoingBatch.getNodeBatchId());
                        }
                    } else if (status == Status.ER && isFirstTimeAsErStatus && outgoingBatch.getLoadId() > 0) {
                        engine.getDataService().updateTableReloadStatusFailed(transaction, outgoingBatch.getLoadId(),
                                engine.getNodeId(), outgoingBatch.getBatchId());
                    }
                    transaction.commit();
                    if (status == Status.OK) {
                        if (isFirstTimeAsOkStatus) {
                            engine.getStatisticManager().incrementDataLoadedOutgoing(outgoingBatch.getChannelId(), outgoingBatch.getLoadRowCount());
                            engine.getStatisticManager().incrementDataBytesLoadedOutgoing(outgoingBatch.getChannelId(), outgoingBatch.getByteCount());
                        }
                        if (parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED)) {
                            purgeBatchesFromStaging(outgoingBatch);
                        }
                        Channel channel = engine.getConfigurationService().getChannel(outgoingBatch.getChannelId());
                        if (channel != null && channel.isFileSyncFlag()) {
                            /* Acknowledge the file_sync in case the file needs deleted. */
                            engine.getFileSyncService().acknowledgeFiles(outgoingBatch);
                        }
                        engine.getStatisticManager().removeRouterStatsByBatch(batch.getBatchId());
                    }
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
            } else if (outgoingBatch == null) {
                log.error("Could not find batch {}-{} to acknowledge as {}", new Object[] { batch.getNodeId(), batch.getBatchId(),
                        status.name() });
                result.setOk(false);
            }
        }
        return result;
    }

    protected String getErrorMessage(BatchAck batch) {
        return (batch.getSqlCode() != 0 ? "[" + batch.getSqlState() + "," + batch.getSqlCode() + "] " : "")
                + (batch.getSqlMessage() != null ? batch.getSqlMessage() : "(no message)");
    }

    protected void purgeBatchesFromStaging(OutgoingBatch outgoingBatch) {
        long threshold = parameterService.getLong(ParameterConstants.INITIAL_LOAD_PURGE_STAGE_IMMEDIATE_THRESHOLD_ROWS);
        if (threshold >= 0 && outgoingBatch.isLoadFlag() && !outgoingBatch.isCommonFlag()) {
            if (outgoingBatch.getDataRowCount() > threshold) {
                IStagedResource resource = engine.getStagingManager().find(Constants.STAGING_CATEGORY_OUTGOING,
                        outgoingBatch.getStagedLocation(), outgoingBatch.getBatchId());
                if (resource != null) {
                    resource.delete();
                }
            }
        }
        long streamToFileThreshold = parameterService.getLong(ParameterConstants.STREAM_TO_FILE_THRESHOLD);
        if (streamToFileThreshold > 0 && !outgoingBatch.isCommonFlag() && outgoingBatch.getByteCount() <= streamToFileThreshold) {
            IStagedResource resource = engine.getStagingManager().find(Constants.STAGING_CATEGORY_OUTGOING,
                    outgoingBatch.getStagedLocation(), outgoingBatch.getBatchId());
            if (resource != null && resource.isMemoryResource() && !resource.isInUse()) {
                resource.delete();
            }
        }
    }

    public List<BatchAckResult> ack(List<BatchAck> batches) {
        List<BatchAckResult> results = new ArrayList<BatchAckResult>();
        for (BatchAck batch : batches) {
            results.add(ack(batch));
        }
        return results;
    }

    public void checkMissingAck(List<BatchAck> acks, String queue) {
        boolean hasCorruptBatch = false;
        String nodeId = null;
        for (BatchAck batch : acks) {
            if (batch.getBatchId() == Constants.BATCH_ID_MISSING) {
                hasCorruptBatch = true;
                nodeId = batch.getNodeId();
            }
        }
        if (hasCorruptBatch && parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED)) {
            OutgoingBatches batches = engine.getOutgoingBatchService().getOutgoingBatches(nodeId, queue, false);
            Iterator<OutgoingBatch> iter = batches.getBatches().iterator();
            while (iter.hasNext()) {
                OutgoingBatch batch = iter.next();
                if (batch.getStatus() == Status.RQ || batch.getStatus() == Status.NE) {
                    iter.remove();
                }
            }
            if (batches.containsBatches()) {
                OutgoingBatch batch = batches.getBatches().get(0);
                StringBuilder message = new StringBuilder(128);
                message.append("Expected but did not receive an ack for batch ");
                message.append(batch.getNodeBatchId()).append(". ");
                if (!batch.isLoadFlag()) {
                    message.append("This could be because the batch is corrupt - removing the batch from staging.");
                    log.warn(message.toString());
                    IStagedResource resource = engine.getStagingManager().find(Constants.STAGING_CATEGORY_OUTGOING,
                            batch.getStagedLocation(), batch.getBatchId());
                    if (resource != null) {
                        resource.delete();
                    } else {
                        log.warn("Unable to find outgoing staging file for {} that has corrupt batch data", batch.getNodeBatchId());
                    }
                } else {
                    message.append(
                            "This could be because the batch is corrupt. Not removing the batch because it was a load batch, but you may need to clear the batch from staging manually.");
                    log.warn(message.toString());
                }
            } else {
                log.warn("Unable to find outgoing batch for node {} that has corrupt batch data", nodeId);
            }
        }
    }
}
