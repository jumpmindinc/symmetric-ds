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

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import org.jumpmind.db.model.Relation;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.TableNotFoundException;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.exception.IoException;
import org.jumpmind.exception.ParseException;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ErrorConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.IDataProcessorListener;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.io.data.writer.AbstractDatabaseWriter;
import org.jumpmind.symmetric.io.data.writer.Conflict;
import org.jumpmind.symmetric.io.data.writer.ConflictException;
import org.jumpmind.symmetric.io.data.writer.DefaultDatabaseWriter;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingError;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IIncomingBatchListener;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.TransportException;
import org.jumpmind.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ManageIncomingBatchListener implements IDataProcessorListener {
    private static final Logger log = LoggerFactory.getLogger(ManageIncomingBatchListener.class);
    protected List<IncomingBatch> batchesProcessed = new ArrayList<IncomingBatch>();
    protected IncomingBatch currentBatch;
    protected boolean isNewErrorForCurrentBatch;
    protected boolean isNewErrorSuppressed;
    protected ProcessInfo processInfo;
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IIncomingBatchService incomingBatchService;
    private IStatisticManager statisticManager;
    private ISymmetricDialect symmetricDialect;
    private IDataLoaderService dataLoaderService;
    private IIncomingBatchListener incomingBatchListener;

    public ManageIncomingBatchListener(ProcessInfo processInfo, ISymmetricEngine engine) {
        this.processInfo = processInfo;
        this.engine = engine;
        this.parameterService = engine.getParameterService();
        this.symmetricDialect = engine.getSymmetricDialect();
        this.dataLoaderService = engine.getDataLoaderService();
        this.incomingBatchService = engine.getIncomingBatchService();
        this.statisticManager = engine.getStatisticManager();
        this.incomingBatchListener = engine.getExtensionService().getExtensionPoint(IIncomingBatchListener.class);
        if (this.incomingBatchListener != null && !this.incomingBatchListener.isInterestedInBatches()) {
            this.incomingBatchListener = null;
        }
    }

    public void beforeBatchEnd(DataContext context) {
        if (incomingBatchListener != null) {
            incomingBatchListener.batchStarted(context, symmetricDialect);
        }
        enableSyncTriggers(context);
    }

    public boolean beforeBatchStarted(DataContext context) {
        this.currentBatch = null;
        Batch batch = context.getBatch();
        this.currentBatch = null;
        context.remove("currentBatch");
        if (parameterService.is(ParameterConstants.DATA_LOADER_ENABLED) || (batch.getChannelId() != null && (batch.getChannelId().equals(
                Constants.CHANNEL_CONFIG) || batch.getChannelId().equals(Constants.CHANNEL_SYSTEM)))) {
            if (batch.getBatchId() == Constants.VIRTUAL_BATCH_FOR_REGISTRATION && batch.getSourceNodeId() != null) {
                log.info("Preparing to receive registration from node {} by clearing its outgoing config batches", batch.getSourceNodeId());
                IOutgoingBatchService outgoingBatchService = engine.getOutgoingBatchService();
                IDataService dataService = engine.getDataService();
                dataService.deleteCapturedConfigChannelData();
                outgoingBatchService.markAllConfigAsSentForNode(batch.getSourceNodeId());
            }
            IncomingBatch incomingBatch = new IncomingBatch(batch);
            this.batchesProcessed.add(incomingBatch);
            if (batch.getStatistics() != null) {
                incomingBatch.mergeInjectedBatchStatistics(batch.getStatistics());
                processInfo.setTotalDataCount(incomingBatch.getExtractRowCount());
                processInfo.setCurrentLoadId(incomingBatch.getLoadId());
            }
            this.currentBatch = incomingBatch;
            context.put("currentBatch", this.currentBatch);
            if (incomingBatchService.acquireIncomingBatch(incomingBatch)) {
                if (batch.getBatchId() == Constants.VIRTUAL_BATCH_FOR_REGISTRATION) {
                    log.info("Loading registration batch from {}", batch.getSourceNodeId());
                }
                return true;
            }
        }
        return false;
    }

    public void afterBatchStarted(DataContext context) {
        Batch batch = context.getBatch();
        ISqlTransaction transaction = context.findSymmetricTransaction(engine.getTablePrefix());
        if (transaction != null) {
            symmetricDialect.disableSyncTriggers(transaction, batch.getSourceNodeId());
        }
    }

    public void batchSuccessful(DataContext context) {
        Batch batch = context.getBatch();
        this.currentBatch.setValues(context.getReader().getStatistics().get(batch), context
                .getWriter().getStatistics().get(batch), true);
        this.currentBatch.setBulkLoaderFlag(batch.isBulkLoaderFlag());
        statisticManager.incrementDataLoaded(this.currentBatch.getChannelId(),
                this.currentBatch.getLoadRowCount());
        statisticManager.incrementDataBytesLoaded(this.currentBatch.getChannelId(),
                this.currentBatch.getByteCount());
        statisticManager.incrementTableRows(this.currentBatch.getTableLoadedCount(), true);
        if (incomingBatchListener != null) {
            incomingBatchListener.batchCommitted(context, symmetricDialect);
        }
        Status oldStatus = this.currentBatch.getStatus();
        try {
            this.currentBatch.setStatus(Status.OK);
            if (incomingBatchService.isRecordOkBatchesEnabled()) {
                if (this.currentBatch.getIgnoreCount() > 0) {
                    log.info("Ignoring batch {}", this.currentBatch.getNodeBatchId());
                }
                incomingBatchService.updateIncomingBatch(this.currentBatch);
            } else if (this.currentBatch.isRetry()) {
                incomingBatchService.deleteIncomingBatch(this.currentBatch);
            }
        } catch (RuntimeException ex) {
            this.currentBatch.setStatus(oldStatus);
            throw ex;
        }
    }

    protected void enableSyncTriggers(DataContext context) {
        try {
            ISqlTransaction transaction = context.findSymmetricTransaction(engine.getTablePrefix());
            if (transaction != null) {
                if (!Boolean.TRUE.equals(context.get(AbstractDatabaseWriter.TRANSACTION_ABORTED))) {
                    symmetricDialect.enableSyncTriggers(transaction);
                }
            }
        } catch (Exception ex) {
            log.error("", ex);
        }
    }

    public void batchInError(DataContext context, Throwable ex) {
        try {
            if (this.currentBatch == null) {
                /*
                 * if the current batch is null, there isn't anything we can do other than log the error
                 */
                throw ex;
            }
            if (context.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE) != null && context.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE).equals("bulk")) {
                currentBatch.setBulkLoaderFlag(false);
                log.info("Bulk loading failed for this batch " + context.getBatch().getBatchId() + ", falling back to default loading. (" + ex + ")");
                log.debug("Bulk loading error.", ex);
            } else {
                /*
                 * Reread batch to make sure it wasn't set to IG or OK
                 */
                engine.getIncomingBatchService().refreshIncomingBatch(currentBatch);
                if (currentBatch.getStatus() != Status.OK && currentBatch.getStatus() != Status.IG) {
                    currentBatch.setStatus(IncomingBatch.Status.ER);
                    currentBatch.setErrorFlag(true);
                }
                Batch batch = context.getBatch();
                isNewErrorForCurrentBatch = batch != null && batch.getLineCount() != currentBatch.getFailedLineNumber();
                if (context.getWriter() != null
                        && context.getReader().getStatistics().get(batch) != null
                        && context.getWriter().getStatistics().get(batch) != null) {
                    this.currentBatch.setValues(context.getReader().getStatistics().get(batch),
                            context.getWriter().getStatistics().get(batch), false);
                    statisticManager.incrementDataLoadedErrors(this.currentBatch.getChannelId(), 1);
                } else {
                    log.error("An error caused a batch to fail without attempting to load data for batch " +
                            (batch != null ? batch.getNodeBatchId() : "?"), ex);
                }
                enableSyncTriggers(context);
                if (ex instanceof CancellationException) {
                    log.info("Cancelling batch " + this.currentBatch.getNodeBatchId());
                } else if (ex instanceof ParseException || ExceptionUtils.is(ex, ProtocolException.class, ZipException.class, BadPaddingException.class,
                        IllegalBlockSizeException.class)) {
                    this.currentBatch.setSqlCode(ErrorConstants.PROTOCOL_VIOLATION_CODE);
                    this.currentBatch.setSqlState(ErrorConstants.PROTOCOL_VIOLATION_STATE);
                    if (isNewErrorForCurrentBatch) {
                        suppressError();
                    } else {
                        log.error(String.format("Failed to parse batch %s", this.currentBatch.getNodeBatchId()), ex);
                    }
                } else if (ex instanceof IOException || ex instanceof TransportException
                        || ex instanceof IoException) {
                    log.warn("Failed to load batch " + this.currentBatch.getNodeBatchId(), ex);
                    this.currentBatch.setSqlMessage(ex);
                } else {
                    SQLException se = ExceptionUtils.unwrapSqlException(ex);
                    if (ex instanceof ConflictException) {
                        this.currentBatch.setSqlMessage(ex);
                        if (se != null && isNotBlank(se.getMessage())) {
                            this.currentBatch.setSqlMessage(this.currentBatch.getSqlMessage() + " " + se.getMessage());
                        }
                        this.currentBatch.setSqlState(ErrorConstants.CONFLICT_STATE);
                        this.currentBatch.setSqlCode(ErrorConstants.CONFLICT_CODE);
                    } else if (se != null) {
                        String sqlState = se.getSQLState();
                        if (sqlState != null && sqlState.length() > 10) {
                            sqlState = sqlState.replace("JDBC-", "");
                            if (sqlState.length() > 10) {
                                sqlState = sqlState.substring(0, 10);
                            }
                        }
                        this.currentBatch.setSqlState(sqlState);
                        this.currentBatch.setSqlCode(se.getErrorCode());
                        this.currentBatch.setSqlMessage(se);
                        ISqlTemplate sqlTemplate = null;
                        if (context.getRelation() != null) {
                            sqlTemplate = symmetricDialect.getTargetPlatform(context.getRelation().getName()).getSqlTemplate();
                        } else {
                            sqlTemplate = symmetricDialect.getTargetPlatform().getSqlTemplate();
                        }
                        if (sqlTemplate.isForeignKeyViolation(se)) {
                            this.currentBatch.setSqlState(ErrorConstants.FK_VIOLATION_STATE);
                            this.currentBatch.setSqlCode(ErrorConstants.FK_VIOLATION_CODE);
                        } else if (sqlTemplate.isDeadlock(se)) {
                            this.currentBatch.setSqlState(ErrorConstants.DEADLOCK_STATE);
                            this.currentBatch.setSqlCode(ErrorConstants.DEADLOCK_CODE);
                        }
                    } else {
                        this.currentBatch.setSqlMessage(ExceptionUtils.getRootCause(ex));
                    }
                    if (ex instanceof TableNotFoundException) {
                        log.error("The incoming batch {} failed: {}", this.currentBatch.getNodeBatchId(), ex.getMessage());
                    } else if (isNewErrorForCurrentBatch && (this.currentBatch.getSqlCode() == ErrorConstants.FK_VIOLATION_CODE
                            || this.currentBatch.getSqlCode() == ErrorConstants.DEADLOCK_CODE
                            || (Boolean.TRUE.equals(context.get(AbstractDatabaseWriter.TRANSACTION_ABORTED)) &&
                                    this.currentBatch.getSqlCode() == ErrorConstants.CONFLICT_CODE))) {
                        suppressError();
                    } else {
                        log.error(String.format("Failed to load batch %s", this.currentBatch.getNodeBatchId()), ex);
                    }
                }
                ISqlTransaction transaction = context.findSymmetricTransaction(engine.getTablePrefix());
                if (Boolean.TRUE.equals(context.get(AbstractDatabaseWriter.TRANSACTION_ABORTED))) {
                    transaction = null;
                }
                if (currentBatch.getStatus() == Status.ER) {
                    if (context.getRelation() != null && context.getData() != null) {
                        try {
                            IncomingError error = new IncomingError();
                            error.setBatchId(this.currentBatch.getBatchId());
                            error.setNodeId(this.currentBatch.getNodeId());
                            error.setTargetCatalogName(context.getRelation().getCatalog());
                            error.setTargetSchemaName(context.getRelation().getSchema());
                            error.setTargetTableName(context.getRelation().getName());
                            error.setColumnNames(Relation.getCommaDeliminatedColumns(context
                                    .getRelation().getColumns()));
                            error.setPrimaryKeyColumnNames(Relation.getCommaDeliminatedColumns(context
                                    .getRelation().getPrimaryKeyColumns()));
                            error.setCsvData(context.getData());
                            error.setCurData((String) context.get(DefaultDatabaseWriter.CUR_DATA));
                            error.setBinaryEncoding(context.getBatch().getBinaryEncoding());
                            error.setEventType(context.getData().getDataEventType());
                            error.setFailedLineNumber(this.currentBatch.getFailedLineNumber());
                            error.setFailedRowNumber(this.currentBatch.getFailedRowNumber());
                            if (ex instanceof ConflictException) {
                                ConflictException conflictEx = (ConflictException) ex;
                                Conflict conflict = conflictEx.getConflict();
                                if (conflict != null) {
                                    error.setConflictId(conflict.getConflictId());
                                }
                            }
                            if (context.get(AbstractDatabaseWriter.CONFLICT_IGNORE) != null) {
                                error.setResolveIgnore(true);
                            }
                            if (transaction != null) {
                                dataLoaderService.insertIncomingError(transaction, error);
                            } else {
                                dataLoaderService.insertIncomingError(error);
                            }
                        } catch (UniqueKeyException e) {
                            // ignore. we already inserted an error for this row
                            if (transaction != null) {
                                transaction.rollback();
                            }
                            if (context.get(AbstractDatabaseWriter.CONFLICT_IGNORE) != null) {
                                IncomingError error = dataLoaderService.getIncomingError(currentBatch.getBatchId(), currentBatch.getNodeId(),
                                        currentBatch.getFailedRowNumber());
                                if (error != null) {
                                    error.setResolveIgnore(true);
                                    dataLoaderService.updateIncomingError(error);
                                }
                            }
                        }
                    }
                }
                if (transaction != null) {
                    if (incomingBatchService.isRecordOkBatchesEnabled()
                            || this.currentBatch.isRetry()) {
                        incomingBatchService.updateIncomingBatch(transaction, this.currentBatch);
                    } else {
                        incomingBatchService.insertIncomingBatch(transaction, this.currentBatch);
                    }
                } else {
                    if (incomingBatchService.isRecordOkBatchesEnabled()
                            || this.currentBatch.isRetry()) {
                        incomingBatchService.updateIncomingBatch(this.currentBatch);
                    } else {
                        incomingBatchService.insertIncomingBatch(this.currentBatch);
                    }
                }
            }
        } catch (Throwable e) {
            log.error("Failed to record status of batch " + (this.currentBatch != null ? this.currentBatch.getNodeBatchId()
                    : context.getBatch().getNodeBatchId()), e);
        }
    }

    protected void suppressError() {
        currentBatch.setErrorFlag(false);
        currentBatch.setStatus(Status.LD);
        isNewErrorSuppressed = true;
    }

    public void batchProgressUpdate(DataContext context) {
    }

    public void dataRowProcessed() {
    }

    public List<IncomingBatch> getBatchesProcessed() {
        return batchesProcessed;
    }

    public IncomingBatch getCurrentBatch() {
        return currentBatch;
    }

    public boolean isNewErrorForCurrentBatch() {
        return isNewErrorForCurrentBatch;
    }

    public boolean isErrorSuppressed() {
        return isNewErrorSuppressed;
    }
}