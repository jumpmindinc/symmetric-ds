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
package org.jumpmind.symmetric.io;

import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.reader.DataReaderStatistics;
import org.jumpmind.symmetric.io.data.writer.Conflict;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterSettings;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcBatchBulkDatabaseWriter extends AbstractBulkDatabaseWriter {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private int lastRowCount = 0;
    private int expectedRowCount = 0; // Helps detect conflicts at the target
    private int lastUncommittedRows = 0; // Helps detect commits
    private int totalCommittedRows = 0;
    private long batchStartTimeMillis;
    private long batchLastLogUpdateMillis = 0;
    private long commitLogUpdateMillis = DateUtils.MILLIS_PER_MINUTE;

    public JdbcBatchBulkDatabaseWriter(IDatabasePlatform symmetricPlatform, IDatabasePlatform targetPlatform,
            String tablePrefix, DatabaseWriterSettings writerSettings) {
        super(symmetricPlatform, targetPlatform, tablePrefix, writerSettings);
    }

    @Override
    public void start(Batch batch) {
        super.start(batch);
        String batchInfo = "batch=" + batch.getBatchId();
        batchStartTimeMillis = System.currentTimeMillis();
        batchLastLogUpdateMillis = batchStartTimeMillis;
        long jdbcMaxRowsBeforeCommit = writerSettings.getMaxRowsBeforeCommit();
        if (context.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE) == null || !context.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE).equals("default")) {
            int bulkLoaderCommitLimit = ((JdbcSqlTemplate) getPlatform().getSqlTemplate()).getSettings().getBatchBulkLoaderSize();
            if (jdbcMaxRowsBeforeCommit > 0 && jdbcMaxRowsBeforeCommit < bulkLoaderCommitLimit) {
                log.info(
                        "Expect more frequent commits, because the dataloader.max.rows.before.commit limit ({}) overrides the db.jdbc.bulk.execute.batch.size limit ({}), {}",
                        jdbcMaxRowsBeforeCommit, bulkLoaderCommitLimit, batchInfo);
                bulkLoaderCommitLimit = (int) jdbcMaxRowsBeforeCommit;
            }
            if (log.isDebugEnabled()) {
                Statistics batchStats = batch.getStatistics();
                if (batchStats != null) {
                    long totalBatchRowCount = batch.getStatistics().get(DataReaderStatistics.DATA_ROW_COUNT);
                    log.debug("Starting batch that contains {} data rows. Commit limit={}, {}", totalBatchRowCount, bulkLoaderCommitLimit, batchInfo);
                } else {
                    log.debug("Starting batch. Commit limit={}, {}", bulkLoaderCommitLimit, batchInfo);
                }
            }
            getTransaction().setInBatchMode(true);
            ((JdbcSqlTransaction) getTransaction()).setBatchSize(bulkLoaderCommitLimit);
        }
        if (log.isDebugEnabled()) {
            log.debug("Initial DML row count: Actual={}, Expected={}, Uncommitted={}", lastRowCount, expectedRowCount, lastUncommittedRows);
        }
    }

    @Override
    protected void bulkWrite(CsvData data) {
        writeDefault(data);
    }

    @Override
    protected LoadStatus delete(CsvData data, boolean useConflictDetection) {
        LoadStatus loadStatus = super.delete(data, useConflictDetection);
        if (!getTransaction().isInBatchMode()) {
            return loadStatus;
        }
        throwForConflict(loadStatus, true);
        return LoadStatus.SUCCESS;
    }

    @Override
    protected LoadStatus insert(CsvData data) {
        LoadStatus loadStatus = super.insert(data);
        if (!getTransaction().isInBatchMode()) {
            return loadStatus;
        }
        throwForConflict(loadStatus, true);
        return LoadStatus.SUCCESS;
    }

    @Override
    protected LoadStatus update(CsvData data, boolean applyChangesOnly, boolean useConflictDetection) {
        LoadStatus loadStatus = super.update(data, applyChangesOnly, useConflictDetection);
        if (!getTransaction().isInBatchMode()) {
            return loadStatus;
        }
        throwForConflict(loadStatus, true);
        return LoadStatus.SUCCESS;
    }

    protected void throwForConflict(boolean isDml) {
        throwForConflict(LoadStatus.SUCCESS, isDml);
    }

    protected void throwForConflict(LoadStatus loadStatus, boolean isDml) {
        if (isDml) {
            expectedRowCount++;
        }
        int pendingRows = getTransaction().getUnflushedMarkers(false).size();
        if (loadStatus != LoadStatus.SUCCESS) {
            log.warn("There was a row conflict at target! Actual={}, Expected={}, Pending={}, (Previous) Uncommitted={}, Total committed={}",
                    lastRowCount, expectedRowCount, pendingRows, lastUncommittedRows, totalCommittedRows);
            throw new SymmetricException("JdbcBatchBulkDataWriter was in conflict, will attempt to fallback using default writer.");
        }
        if (pendingRows == 0) {
            if (expectedRowCount != totalCommittedRows) {
                log.warn(
                        "Number of committed rows does not match expected, which indicates a conflict at target! Actual={}, Expected={}, Pending={}, Prev.Uncommitted={}, Total committed={}",
                        lastRowCount, expectedRowCount, totalCommittedRows, pendingRows, lastUncommittedRows, totalCommittedRows);
                throw new SymmetricException("JdbcBatchBulkDataWriter was in conflict, will attempt to fallback using default writer.");
            } else if (log.isDebugEnabled()) {
                log.debug("No conflict. DML row count update: Actual={}, Expected={}, Pending={}, Prev.Uncommitted={}, Total committed={}", lastRowCount,
                        expectedRowCount, pendingRows, lastUncommittedRows, totalCommittedRows);
            }
        }
    }

    @Override
    protected void prepare() {
        if (getTransaction().isInBatchMode()) {
            int pendingRows = getTransaction().getUnflushedMarkers(false).size();
            if (pendingRows > 0) {
                lastRowCount = getTransaction().flush();
                totalCommittedRows += lastRowCount;
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Committed existing rows to prepare for a new set. Row count update: Actual={}, Expected={}, Pending-before flush={}, Total committed={}",
                            lastRowCount, expectedRowCount, pendingRows, totalCommittedRows);
                }
                lastUncommittedRows = 0;
                throwForConflict(false);
            }
        }
        super.prepare();
    }

    @Override
    protected int execute(CsvData data, String[] values) {
        int pendingRowsBefore = getTransaction().getUnflushedMarkers(false).size();
        if (pendingRowsBefore == 0 && lastUncommittedRows > 0) {
            totalCommittedRows += lastUncommittedRows;
            if (log.isDebugEnabled()) {
                log.debug("Detected an out-of-band commit. Rows update: Expected={}, Last uncommitted={}, Total committed={}", expectedRowCount,
                        lastUncommittedRows, totalCommittedRows);
            }
        }
        lastUncommittedRows = pendingRowsBefore;
        lastRowCount = super.execute(data, values);
        int pendingRowsAfter = getTransaction().getUnflushedMarkers(false).size();
        long currentTimeMillis = System.currentTimeMillis();
        if (pendingRowsAfter == 0) {
            totalCommittedRows += lastRowCount;
            if (currentTimeMillis - batchLastLogUpdateMillis >= commitLogUpdateMillis) {
                String batchInfo = String.format("Committed %d rows for batch=%d, Table=%s, Rows=%d, Rows per second=%.1f", lastRowCount,
                        batch.getBatchId(), targetTable.getName(), totalCommittedRows, getRowsPerSecond());
                log.info("Commit point reached. " + batchInfo);
                batchLastLogUpdateMillis = currentTimeMillis;
            } else if (log.isDebugEnabled()) {
                log.debug("Committed rows={}, Expected={}, Pending after={}, before={}, Total committed={}", lastRowCount,
                        expectedRowCount, pendingRowsAfter, pendingRowsBefore, totalCommittedRows);
            }
            lastUncommittedRows = 0;
            return lastRowCount;
        } else {
            lastUncommittedRows = pendingRowsAfter;
            if (log.isDebugEnabled()) {
                log.debug("Executed, actually just buffered rows={}, Expected={}, Pending after={}, before={}, Total committed={}", lastRowCount,
                        expectedRowCount, pendingRowsAfter, pendingRowsBefore, totalCommittedRows);
            }
            return pendingRowsAfter - pendingRowsBefore;
        }
    }

    @Override
    public void end(Batch batch, boolean inError) {
        if (getTransaction().isInBatchMode()) {
            int pendingRows = getTransaction().getUnflushedMarkers(false).size();
            if (pendingRows > 0) {
                lastRowCount = getTransaction().flush();
                totalCommittedRows += lastRowCount;
                if (log.isDebugEnabled()) {
                    log.debug("Committed at the end of batch. Rows={}, Expected={}, Pending-before flush={}, Total committed={}",
                            lastRowCount, expectedRowCount, pendingRows, totalCommittedRows);
                }
                lastUncommittedRows = 0;
                throwForConflict(false);
            }
        }
        super.end(batch, inError);
        String batchInfo;
        if (targetTable != null) {
            batchInfo = String.format("batch=%d, Table=%s, Rows=%d, Rows per second=%.1f",
                    batch.getBatchId(), targetTable.getName(), totalCommittedRows, getRowsPerSecond());
        } else {
            batchInfo = String.format("batch=%d, Rows=%d, Rows per second=%.1f",
                    batch.getBatchId(), totalCommittedRows, getRowsPerSecond());
        }
        log.info("Batch committed. " + batchInfo);
    }

    @Override
    protected boolean requireNewStatement(DmlType currentType, CsvData data,
            boolean applyChangesOnly, boolean useConflictDetection,
            Conflict.DetectConflict detectType) {
        if (currentType == DmlType.DELETE) {
            applyChangesOnly = false;
        }
        return super.requireNewStatement(currentType, data, applyChangesOnly, useConflictDetection, detectType);
    }

    public double getRowsPerSecond() {
        long durationMillis = System.currentTimeMillis() - batchStartTimeMillis;
        double rowsPerSecond = totalCommittedRows; // Just in case durationMillis is zero
        if (durationMillis > 0) {
            rowsPerSecond = Math.round(totalCommittedRows * 10000.0 / durationMillis) / 10.0;
        }
        return rowsPerSecond;
    }
}
