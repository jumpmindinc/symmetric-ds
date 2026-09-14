/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.io.data.writer;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.AbstractDatabaseWriter.LoadStatus;
import org.jumpmind.symmetric.io.data.writer.Conflict.DetectConflict;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractDatabaseWriterConflictResolver implements IDatabaseWriterConflictResolver {
    protected Logger log = LoggerFactory.getLogger(AbstractDatabaseWriterConflictResolver.class);

    public void needsResolved(AbstractDatabaseWriter writer, CsvData data, LoadStatus loadStatus) {
        DataEventType originalEventType = data.getDataEventType();
        DatabaseWriterSettings writerSettings = writer.getWriterSettings();
        Conflict conflict = writerSettings.pickConflict(writer.getTargetTable(), writer.getBatch());
        Statistics statistics = writer.getStatistics().get(writer.getBatch());
        long statementCount = statistics.get(DataWriterStatisticConstants.ROWCOUNT);
        long lineNumber = statistics.get(DataWriterStatisticConstants.LINENUMBER);
        ResolvedData resolvedData = writerSettings.getResolvedData(statementCount);
        logConflictHappened(conflict, data, writer, resolvedData, lineNumber);
        switch (originalEventType) {
            case INSERT:
                if (resolvedData != null) {
                    attemptToResolve(resolvedData, data, writer, conflict);
                } else {
                    switch (conflict.getResolveType()) {
                        case FALLBACK:
                            performChainedFallbackForInsert(writer, data, conflict);
                            break;
                        case NEWER_WINS:
                            boolean isWinner = false;
                            if (conflict.getDetectType() == DetectConflict.USE_TIMESTAMP) {
                                isWinner = isTimestampNewer(conflict, writer, data);
                            } else if (conflict.getDetectType() == DetectConflict.USE_VERSION) {
                                isWinner = isVersionNewer(conflict, writer, data);
                            } else {
                                isWinner = isCaptureTimeNewer(conflict, writer, data, null);
                            }
                            if (isWinner) {
                                performChainedFallbackForInsert(writer, data, conflict);
                            } else {
                                if (!conflict.isResolveRowOnly()) {
                                    writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTLOSECOUNT);
                                    throw new IgnoreBatchException();
                                } else {
                                    ignoreRow(writer);
                                    writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTLOSECOUNT);
                                }
                            }
                            break;
                        case IGNORE:
                            writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTLOSECOUNT);
                            ignore(writer, conflict);
                            break;
                        case MANUAL:
                        default:
                            attemptToResolve(resolvedData, data, writer, conflict);
                            break;
                    }
                }
                break;
            case UPDATE:
                if (resolvedData != null) {
                    attemptToResolve(resolvedData, data, writer, conflict);
                } else {
                    switch (conflict.getResolveType()) {
                        case FALLBACK:
                            performChainedFallbackForUpdate(writer, data, conflict);
                            break;
                        case NEWER_WINS:
                            boolean isWinner = false;
                            if (conflict.getDetectType() == DetectConflict.USE_TIMESTAMP) {
                                isWinner = isTimestampNewer(conflict, writer, data);
                            } else if (conflict.getDetectType() == DetectConflict.USE_VERSION) {
                                isWinner = isVersionNewer(conflict, writer, data);
                            } else {
                                isWinner = isCaptureTimeNewer(conflict, writer, data, null);
                            }
                            if (isWinner) {
                                performChainedFallbackForUpdate(writer, data, conflict);
                            } else {
                                if (!conflict.isResolveRowOnly()) {
                                    writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTLOSECOUNT);
                                    throw new IgnoreBatchException();
                                } else {
                                    ignoreRow(writer);
                                    writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTLOSECOUNT);
                                }
                            }
                            break;
                        case IGNORE:
                            writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTLOSECOUNT);
                            ignore(writer, conflict);
                            break;
                        case MANUAL:
                        default:
                            attemptToResolve(resolvedData, data, writer, conflict);
                            break;
                    }
                }
                break;
            case DELETE:
                switch (conflict.getResolveType()) {
                    case FALLBACK:
                        LoadStatus status = LoadStatus.CONFLICT;
                        if (conflict.getDetectType() == DetectConflict.USE_PK_DATA) {
                            status = performChainedFallbackForDelete(writer, data, conflict);
                        } else {
                            status = writer.delete(data, false);
                            if (status == LoadStatus.CONFLICT) {
                                status = performChainedFallbackForDelete(writer, data, conflict);
                            }
                        }
                        if (status == LoadStatus.CONFLICT) {
                            writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.MISSINGDELETECOUNT);
                        }
                        writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTWINCOUNT);
                        break;
                    case IGNORE:
                        writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTWINCOUNT);
                        ignore(writer, conflict);
                        break;
                    case NEWER_WINS:
                        status = LoadStatus.CONFLICT;
                        boolean isWinner = false;
                        if (conflict.getDetectType() == DetectConflict.USE_TIMESTAMP || conflict.getDetectType() == DetectConflict.USE_VERSION) {
                            isWinner = true;
                        } else {
                            isWinner = isCaptureTimeNewer(conflict, writer, data, null);
                        }
                        if (isWinner) {
                            if (writer.getContext().getLastError() == null) {
                                status = writer.delete(data, false);
                            }
                            if (status == LoadStatus.CONFLICT) {
                                status = performChainedFallbackForDelete(writer, data, conflict);
                            }
                        } else {
                            ignoreRow(writer);
                        }
                        if (status == LoadStatus.CONFLICT) {
                            writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.MISSINGDELETECOUNT);
                        }
                        writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTWINCOUNT);
                        break;
                    case MANUAL:
                    default:
                        if (resolvedData != null) {
                            writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTWINCOUNT);
                            if (!resolvedData.isIgnoreRow()) {
                                writer.delete(data, false);
                            } else {
                                if (!conflict.isResolveRowOnly()) {
                                    throw new IgnoreBatchException();
                                }
                            }
                        } else {
                            throw new ConflictException(data, writer.getTargetTable(), false, conflict,
                                    (Exception) writer.getContext().get(AbstractDatabaseWriter.CONFLICT_ERROR));
                        }
                        break;
                }
                break;
            default:
                break;
        }
        writer.getContext().setLastError(null);
        logConflictResolution(conflict, data, writer, resolvedData, lineNumber);
        checkIfTransactionAborted(writer, data, conflict);
    }

    protected void performChainedFallbackForInsert(AbstractDatabaseWriter writer, CsvData data, Conflict conflict) {
        if (checkForUniqueKeyViolation(writer, data, conflict, writer.getContext().getLastError(), false)) {
            // unique index violation, we remove blocking rows, and try again
            try {
                performFallbackToInsert(writer, data, conflict, true);
            } catch (ConflictException e) {
                // standard fallback to update when insert gets primary key violation
                performFallbackToUpdate(writer, data, conflict, true);
            }
        } else {
            try {
                // standard fallback to update when insert gets primary key violation
                performFallbackToUpdate(writer, data, conflict, true);
            } catch (ConflictException e) {
                if (checkForUniqueKeyViolation(writer, data, conflict, writer.getContext().getLastError(), true)) {
                    // unique index violation, we remove blocking rows, and try again
                    performFallbackToUpdate(writer, data, conflict, true);
                } else {
                    throw e;
                }
            }
        }
    }

    protected void performChainedFallbackForUpdate(AbstractDatabaseWriter writer, CsvData data, Conflict conflict) {
        if (writer.getContext().getLastError() == null && conflict.getDetectType() != DetectConflict.USE_PK_DATA) {
            try {
                // original update was 0 rows, so we'll try to update without conflict detection
                performFallbackToUpdate(writer, data, conflict, true);
            } catch (ConflictException e) {
                if (writer.getContext().getLastError() == null) {
                    performChainedFallbackForUpdateNoException(writer, data, conflict);
                } else {
                    performChainedFallbackForUpdateWithException(writer, data, conflict);
                }
            }
        } else if (writer.getContext().getLastError() == null) {
            performChainedFallbackForUpdateNoException(writer, data, conflict);
        } else {
            performChainedFallbackForUpdateWithException(writer, data, conflict);
        }
    }

    protected void performChainedFallbackForUpdateWithException(AbstractDatabaseWriter writer, CsvData data, Conflict conflict) {
        if (checkForUniqueKeyViolation(writer, data, conflict, writer.getContext().getLastError(), false) ||
                checkForForeignKeyChildExistsViolation(writer, data, conflict, writer.getContext().getLastError())) {
            try {
                performFallbackToUpdate(writer, data, conflict, true);
            } catch (ConflictException e) {
                performFallbackToInsert(writer, data, conflict, true);
            }
        } else {
            if (checkForUniqueKeyViolation(writer, data, conflict, writer.getContext().getLastError(), true)) {
                performFallbackToUpdate(writer, data, conflict, true);
            } else {
                throw new ConflictException(data, writer.getTargetTable(), false, conflict,
                        (Exception) writer.getContext().get(AbstractDatabaseWriter.CONFLICT_ERROR));
            }
        }
    }

    protected void performChainedFallbackForUpdateNoException(AbstractDatabaseWriter writer, CsvData data, Conflict conflict) {
        try {
            performFallbackToInsert(writer, data, conflict, true);
        } catch (ConflictException e) {
            if (checkForUniqueKeyViolation(writer, data, conflict, writer.getContext().getLastError(), true)) {
                performFallbackToInsert(writer, data, conflict, true);
            } else {
                checkIfMismatchedPrimaryKey(writer);
                throw e;
            }
        }
    }

    protected LoadStatus performChainedFallbackForDelete(AbstractDatabaseWriter writer, CsvData data, Conflict conflict) {
        LoadStatus status = LoadStatus.CONFLICT;
        if (checkForForeignKeyChildExistsViolation(writer, data, conflict, writer.getContext().getLastError())) {
            // foreign key child exists violation, we remove blocking rows, and try again
            checkIfTransactionAborted(writer, data, conflict);
            status = writer.delete(data, false);
        }
        if (status == LoadStatus.CONFLICT && !checkIfMismatchedPrimaryKey(writer) && writer.getWriterSettings().isAutoResolveCaptureDeleteMissingRows()) {
            captureMissingDelete(conflict, writer, data);
        }
        return status;
    }

    protected void beforeResolutionAttempt(CsvData data, Conflict conflict) {
    }

    protected void afterResolutionAttempt(CsvData data, Conflict conflict) {
    }

    protected void logConflictHappened(Conflict conflict, CsvData data, AbstractDatabaseWriter writer, ResolvedData resolvedData,
            long lineNumber) {
        if (log.isDebugEnabled()) {
            log.debug("Conflict detected: {} in batch {} at line {} for table {}",
                    new Object[] { conflict.getConflictId() == null ? "default" : conflict.getConflictId(), writer.getBatch().getNodeBatchId(),
                            lineNumber, writer.getTargetTable().getFullyQualifiedName() });
            String csvData = data.getCsvData(CsvData.ROW_DATA);
            if (StringUtils.isNotBlank(csvData)) {
                log.debug("Row data: {}", csvData);
            }
            csvData = data.getCsvData(CsvData.OLD_DATA);
            if (StringUtils.isNotBlank(csvData)) {
                log.debug("Old data: {}", csvData);
            }
            csvData = resolvedData != null ? resolvedData.getResolvedData() : null;
            if (StringUtils.isNotBlank(csvData)) {
                log.debug("Resolve data: {}", csvData);
            }
        }
    }

    protected void logConflictResolution(Conflict conflict, CsvData data, AbstractDatabaseWriter writer, ResolvedData resolvedData,
            long lineNumber) {
        if (writer.getWriterSettings().isLogConflictResolution()) {
            log.info("Conflict detected: {} in batch {} at line {} for table {}",
                    new Object[] { conflict.getConflictId() == null ? "default" : conflict.getConflictId(), writer.getBatch().getBatchId(),
                            lineNumber, writer.getTargetTable().getFullyQualifiedName() });
            String csvData = data.getCsvData(CsvData.ROW_DATA);
            if (StringUtils.isNotBlank(csvData)) {
                log.info("Row data: {}", csvData);
            }
            csvData = data.getCsvData(CsvData.OLD_DATA);
            if (StringUtils.isNotBlank(csvData)) {
                log.info("Old data: {}", csvData);
            }
            csvData = resolvedData != null ? resolvedData.getResolvedData() : null;
            if (StringUtils.isNotBlank(csvData)) {
                log.info("Resolve data: {}", csvData);
            }
            log.info("Resolve Type: {}", conflict.getResolveType());
        }
    }

    protected void ignore(AbstractDatabaseWriter writer, Conflict conflict) {
        if (conflict.isResolveRowOnly()) {
            writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.IGNOREROWCOUNT);
        } else {
            throw new IgnoreBatchException();
        }
    }

    public void attemptToResolve(ResolvedData resolvedData, CsvData data, AbstractDatabaseWriter writer, Conflict conflict) {
        if (resolvedData != null) {
            if (!resolvedData.isIgnoreRow()) {
                data.putCsvData(CsvData.ROW_DATA, resolvedData.getResolvedData());
                try {
                    performFallbackToUpdate(writer, data, conflict, true);
                } catch (ConflictException ex) {
                    performFallbackToInsert(writer, data, conflict, true);
                }
            } else {
                writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.IGNOREROWCOUNT);
            }
        } else {
            throw new ConflictException(data, writer.getTargetTable(), false, conflict,
                    (Exception) writer.getContext().get(AbstractDatabaseWriter.CONFLICT_ERROR));
        }
    }

    abstract protected boolean isTimestampNewer(Conflict conflict, AbstractDatabaseWriter writer, CsvData data);

    abstract protected boolean isCaptureTimeNewer(Conflict conflict, AbstractDatabaseWriter writer, CsvData data, String tableName);

    abstract protected boolean isVersionNewer(Conflict conflict, AbstractDatabaseWriter writer, CsvData data);

    abstract protected void captureMissingDelete(Conflict conflict, AbstractDatabaseWriter writer, CsvData data);

    protected void performFallbackToUpdate(AbstractDatabaseWriter writer, CsvData data, Conflict conflict, boolean retransform) {
        try {
            beforeResolutionAttempt(data, conflict);
            checkIfTransactionAborted(writer, data, conflict);
            LoadStatus loadStatus = writer.update(data, conflict.isResolveChangesOnly(), false);
            if (loadStatus != LoadStatus.SUCCESS) {
                throw new ConflictException(data, writer.getTargetTable(), true, conflict,
                        (Exception) writer.getContext().get(AbstractDatabaseWriter.CONFLICT_ERROR));
            } else {
                writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTWINCOUNT);
                writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.FALLBACKUPDATECOUNT);
            }
        } finally {
            afterResolutionAttempt(data, conflict);
        }
    }

    protected void performFallbackToInsert(AbstractDatabaseWriter writer, CsvData csvData, Conflict conflict, boolean retransform) {
        try {
            beforeResolutionAttempt(csvData, conflict);
            checkIfTransactionAborted(writer, csvData, conflict);
            LoadStatus loadStatus = writer.insert(csvData);
            if (loadStatus != LoadStatus.SUCCESS) {
                throw new ConflictException(csvData, writer.getTargetTable(), true, conflict,
                        (Exception) writer.getContext().get(AbstractDatabaseWriter.CONFLICT_ERROR));
            } else {
                writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.CONFLICTWINCOUNT);
                writer.getStatistics().get(writer.getBatch()).increment(DataWriterStatisticConstants.FALLBACKINSERTCOUNT);
            }
        } finally {
            afterResolutionAttempt(csvData, conflict);
        }
    }

    protected void ignoreRow(AbstractDatabaseWriter writer) {
        if (Boolean.TRUE.equals(writer.getContext().get(AbstractDatabaseWriter.TRANSACTION_ABORTED))) {
            writer.getContext().put(AbstractDatabaseWriter.CONFLICT_IGNORE, true);
        }
    }

    protected void checkIfTransactionAborted(AbstractDatabaseWriter writer, CsvData csvData, Conflict conflict) {
        if (Boolean.TRUE.equals(writer.getContext().get(AbstractDatabaseWriter.TRANSACTION_ABORTED))) {
            throw new ConflictException(csvData, writer.getTargetTable(), false, conflict,
                    (Exception) writer.getContext().get(AbstractDatabaseWriter.CONFLICT_ERROR));
        }
    }

    abstract protected boolean checkForUniqueKeyViolation(AbstractDatabaseWriter writer, CsvData csvData, Conflict conflict, Throwable ex, boolean isFallback);

    abstract protected boolean checkForForeignKeyChildExistsViolation(AbstractDatabaseWriter writer, CsvData data, Conflict conflict, Throwable ex);

    abstract protected boolean isConflictingLosingParentRow(AbstractDatabaseWriter writer, CsvData data);

    abstract protected boolean checkIfMismatchedPrimaryKey(AbstractDatabaseWriter writer);

    @Override
    public boolean isIgnoreRow(AbstractDatabaseWriter writer, CsvData data) {
        DatabaseWriterSettings writerSettings = writer.getWriterSettings();
        Statistics statistics = writer.getStatistics().get(writer.getBatch());
        long statementCount = statistics.get(DataWriterStatisticConstants.ROWCOUNT);
        ResolvedData resolvedData = writerSettings.getResolvedData(statementCount);
        if (resolvedData != null) {
            return resolvedData.isIgnoreRow();
        }
        return isConflictingLosingParentRow(writer, data);
    }
}
