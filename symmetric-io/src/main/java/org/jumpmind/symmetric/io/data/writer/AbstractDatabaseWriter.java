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

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TableColumnSourceReferences;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.TableNotFoundException;
import org.jumpmind.exception.ParseException;
import org.jumpmind.symmetric.io.IoConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.Conflict.DetectConflict;
import org.jumpmind.symmetric.io.data.writer.Conflict.ResolveConflict;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bsh.EvalError;
import bsh.Interpreter;

abstract public class AbstractDatabaseWriter implements IDataWriter {
    private final static Logger log = LoggerFactory.getLogger(AbstractDatabaseWriter.class);
    public static final String CONFLICT_ERROR = "DatabaseWriter.ConflictError";
    public static final String TRANSACTION_ABORTED = "DatabaseWriter.TransactionAborted";
    public static final String CONFLICT_IGNORE = "DatabaseWriter.ConflictIgnore";

    public static enum LoadStatus {
        SUCCESS, CONFLICT
    };

    protected boolean lastUseConflictDetection = true;
    protected boolean lastApplyChangesOnly = false;
    protected boolean isRequiresSavePointsInTransaction = false;
    protected Table sourceTable;
    protected Table targetTable;
    protected Map<String, Table> targetTables = new HashMap<String, Table>();
    protected CsvData lastData;
    protected Batch batch;
    protected DataContext context;
    protected long uncommittedCount = 0;
    protected DatabaseWriterSettings writerSettings;
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();
    protected IDatabaseWriterConflictResolver conflictResolver;
    protected Set<String> missingTables = new HashSet<String>();
    protected Map<String, TableColumnSourceReferences> targetColumnSourceReferencesMap = new HashMap<String, TableColumnSourceReferences>();

    public AbstractDatabaseWriter() {
        this(null, null);
    }

    public AbstractDatabaseWriter(DatabaseWriterSettings settings) {
        this(null, settings);
    }

    public AbstractDatabaseWriter(
            IDatabaseWriterConflictResolver conflictResolver, DatabaseWriterSettings settings) {
        this.conflictResolver = conflictResolver == null ? new DefaultDatabaseWriterConflictResolver()
                : conflictResolver;
        this.writerSettings = settings == null ? new DatabaseWriterSettings() : settings;
    }

    @Override
    public void open(DataContext context) {
        this.context = context;
    }

    @Override
    public void start(Batch batch) {
        this.batch = batch;
        this.batch.setBulkLoaderFlag(false);
        this.statistics.put(batch, new Statistics());
        this.statistics.get(batch).set(DataWriterStatisticConstants.STARTTIME, new Date().getTime());
    }

    @Override
    public boolean start(Table table) {
        if (table == null) {
            throw new NullPointerException("Cannot load a null table");
        }
        this.lastData = null;
        this.sourceTable = table;
        this.targetTable = lookupTableAtTarget(this.sourceTable);
        this.sourceTable.copyColumnTypesFrom(this.targetTable);
        if (this.targetTable == null && hasFilterThatHandlesMissingTable(table)) {
            this.targetTable = table;
        }
        refreshTargetColumnReferencesMap();
        /* The first data that requires a target table should fail because the table will not be found */
        return true;
    }

    @Override
    public void write(CsvData data) {
        context.remove(AbstractDatabaseWriter.CONFLICT_ERROR);
        /*
         * If the startTable has been called and the targetTable is required then check to see if the writer has been configured to ignore this data event
         */
        if (sourceTable != null && targetTable == null && data.requiresTable() && (writerSettings.isIgnoreMissingTables()
                || batch.getBatchId() == IoConstants.IGNORE_TABLES_BATCH || IoConstants.CHANNEL_CONFIG.equals(batch.getChannelId()))) {
            String qualifiedName = sourceTable.getFullyQualifiedName();
            if (missingTables.add(qualifiedName)) {
                log.info("Did not find the {} table in the target database", qualifiedName);
            }
        } else {
            context.put(CONFLICT_ERROR, null);
            if (data.requiresTable() && sourceTable != null
                    && targetTable == null && data.getDataEventType() != DataEventType.SQL) {
                Table lastTable = (Table) context.getLastParsedRelation();
                if (lastTable != null
                        && lastTable.getFullyQualifiedNameLowerCase().equals(sourceTable.getFullyQualifiedNameLowerCase())) {
                    // if we cross batches and the table isn't specified, then
                    // use the last table we used
                    start(lastTable);
                }
            }
            if (targetTable != null || !data.requiresTable()
                    || (targetTable == null && data.getDataEventType() == DataEventType.SQL)) {
                try {
                    statistics.get(batch).increment(DataWriterStatisticConstants.ROWCOUNT);
                    statistics.get(batch).increment(DataWriterStatisticConstants.LINENUMBER);
                    if (filterBefore(data)) {
                        switch (data.getDataEventType()) {
                            case UPDATE:
                            case INSERT:
                                if (sourceTable.getColumnCount() != data.getParsedData(CsvData.ROW_DATA).length) {
                                    throw new ParseException(String.format("The (%s) table's column count (%d) does not match the data's column count (%d)",
                                            sourceTable.getName(), sourceTable.getColumnCount(), data.getParsedData(CsvData.ROW_DATA).length));
                                }
                                break;
                            case DELETE:
                                if (sourceTable.getPrimaryKeyColumnCount() != data.getParsedData(CsvData.PK_DATA).length) {
                                    throw new ParseException(String.format(
                                            "The (%s) table's pk column count (%d) does not match the data's pk column count (%d)", sourceTable.getName(),
                                            sourceTable.getPrimaryKeyColumnCount(), data.getParsedData(CsvData.PK_DATA).length));
                                }
                                break;
                            default:
                                break;
                        }
                        if (isRequiresSavePointsInTransaction && conflictResolver != null) {
                            Statistics batchStatistics = getStatistics().get(getBatch());
                            long statementCount = batchStatistics.get(DataWriterStatisticConstants.ROWCOUNT);
                            ResolvedData resolvedData = getWriterSettings().getResolvedData(statementCount);
                            if (resolvedData != null) {
                                if (resolvedData.isIgnoreRow()) {
                                    statistics.get(batch).increment(DataWriterStatisticConstants.IGNOREROWCOUNT);
                                } else {
                                    Conflict conflict = new Conflict();
                                    conflict.setDetectType(DetectConflict.USE_PK_DATA);
                                    conflict.setResolveType(ResolveConflict.FALLBACK);
                                    conflictResolver.attemptToResolve(resolvedData, data, this, conflict);
                                }
                                return;
                            }
                        }
                        LoadStatus loadStatus = LoadStatus.SUCCESS;
                        switch (data.getDataEventType()) {
                            case UPDATE:
                                loadStatus = update(data, writerSettings.isApplyChangesOnly(), true);
                                break;
                            case INSERT:
                                loadStatus = insert(data);
                                break;
                            case DELETE:
                                loadStatus = delete(data, true);
                                break;
                            case BSH:
                                script(data);
                                break;
                            case SQL:
                                sql(data);
                                break;
                            case CREATE:
                                create(data);
                                break;
                            default:
                                break;
                        }
                        if (loadStatus == LoadStatus.CONFLICT) {
                            if (conflictResolver != null) {
                                conflictResolver.needsResolved(this, data, loadStatus);
                            } else {
                                throw new ConflictException(data, targetTable, false,
                                        writerSettings.pickConflict(targetTable, batch),
                                        (Exception) context.get(AbstractDatabaseWriter.CONFLICT_ERROR));
                            }
                        }
                        uncommittedCount++;
                        lastData = data;
                        filterAfter(data);
                        checkForEarlyCommit();
                    }
                } catch (IgnoreBatchException ex) {
                    rollback();
                    throw ex;
                } catch (RuntimeException ex) {
                    Statistics batchStatistics = getStatistics().get(getBatch());
                    long statementCount = batchStatistics.get(DataWriterStatisticConstants.ROWCOUNT);
                    ResolvedData resolvedData = getWriterSettings().getResolvedData(statementCount);
                    if (conflictResolver != null && conflictResolver.isIgnoreRow(this, data)) {
                        statistics.get(batch).increment(DataWriterStatisticConstants.IGNOREROWCOUNT);
                    } else if (conflictResolver != null && resolvedData != null) {
                        Conflict conflict = new Conflict();
                        conflict.setDetectType(DetectConflict.USE_PK_DATA);
                        conflict.setResolveType(ResolveConflict.FALLBACK);
                        conflictResolver.attemptToResolve(resolvedData, data, this, conflict);
                    } else {
                        if (filterError(data, ex)) {
                            if (!(ex instanceof SqlException)) {
                                /*
                                 * SQL exceptions should have already been logged
                                 */
                                logFailureDetails(ex, data, false);
                            }
                            throw ex;
                        } else {
                            uncommittedCount++;
                            statistics.get(batch).increment(DataWriterStatisticConstants.IGNOREROWCOUNT);
                            checkForEarlyCommit();
                            if (Boolean.TRUE.equals(context.get(AbstractDatabaseWriter.TRANSACTION_ABORTED))) {
                                context.put(CONFLICT_IGNORE, true);
                                throw ex;
                            }
                        }
                    }
                }
            } else {
                if (sourceTable != null) {
                    // If the source table was found but the target table is
                    // still unknown throw an exception
                    statistics.get(batch).increment(DataWriterStatisticConstants.ROWCOUNT);
                    statistics.get(batch).increment(DataWriterStatisticConstants.LINENUMBER);
                    throw new TableNotFoundException(String.format("Could not find the target table '%s'",
                            sourceTable.getFullyQualifiedName()));
                } else {
                    throw new SqlException("The target table was not specified");
                }
            }
        }
    }

    protected void checkForEarlyCommit() {
        if (uncommittedCount >= writerSettings.getMaxRowsBeforeCommit()) {
            commit(true);
            long sleep = writerSettings.getCommitSleepInterval();
            if (sleep > 0) {
                /*
                 * Chances are if SymmetricDS is configured to commit early in a batch we want to give other threads a chance to do work and access the
                 * database. This was added to support H2 clients that are loading big batches while an application is doing work.
                 */
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    log.warn("{}", e.getMessage());
                }
            }
        }
    }

    protected void commit(boolean earlyCommit) {
        uncommittedCount = 0;
    }

    protected void rollback() {
        uncommittedCount = 0;
    }

    protected boolean filterError(CsvData data, Exception ex) {
        boolean process = true;
        List<IDatabaseWriterErrorHandler> filters = this.writerSettings
                .getDatabaseWriterErrorHandlers();
        if (filters != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.FILTERMILLIS);
                for (IDatabaseWriterErrorHandler filter : filters) {
                    process &= filter.handleError(context, targetTable, data, ex);
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.FILTERMILLIS);
            }
        }
        return process;
    }

    protected boolean filterBefore(CsvData data) {
        boolean process = true;
        List<IDatabaseWriterFilter> filters = this.writerSettings.getDatabaseWriterFilters();
        if (filters != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.FILTERMILLIS);
                for (IDatabaseWriterFilter filter : filters) {
                    process &= filter.beforeWrite(this.context, this.sourceTable, data);
                }
                // re-lookup target table in case the source table has changed
                Table oldTargetTable = targetTable;
                if (this.sourceTable != null) {
                    targetTable = lookupTableAtTarget(this.sourceTable);
                }
                if (targetTable != null && !targetTable.equals(oldTargetTable)) {
                    targetTableWasChangedByFilter(oldTargetTable);
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.FILTERMILLIS);
            }
        }
        return process;
    }

    protected void targetTableWasChangedByFilter(Table oldTargetTable) {
    }

    protected void notifyFiltersEarlyCommit() {
        List<IDatabaseWriterFilter> filters = this.writerSettings.getDatabaseWriterFilters();
        if (filters != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.FILTERMILLIS);
                for (IDatabaseWriterFilter filter : filters) {
                    filter.earlyCommit(context);
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.FILTERMILLIS);
            }
        }
    }

    protected void notifyFiltersBatchComplete() {
        List<IDatabaseWriterFilter> filters = this.writerSettings.getDatabaseWriterFilters();
        if (filters != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.FILTERMILLIS);
                for (IDatabaseWriterFilter filter : filters) {
                    filter.batchComplete(context);
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.FILTERMILLIS);
            }
        }
    }

    protected void notifyFiltersBatchCommitted() {
        List<IDatabaseWriterFilter> filters = this.writerSettings.getDatabaseWriterFilters();
        if (filters != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.FILTERMILLIS);
                for (IDatabaseWriterFilter filter : filters) {
                    filter.batchCommitted(context);
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.FILTERMILLIS);
            }
        }
    }

    protected void notifyFiltersBatchRolledback() {
        List<IDatabaseWriterFilter> filters = this.writerSettings.getDatabaseWriterFilters();
        if (filters != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.FILTERMILLIS);
                for (IDatabaseWriterFilter filter : filters) {
                    filter.batchRolledback(context);
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.FILTERMILLIS);
            }
        }
    }

    protected void filterAfter(CsvData data) {
        List<IDatabaseWriterFilter> filters = this.writerSettings.getDatabaseWriterFilters();
        if (filters != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.FILTERMILLIS);
                for (IDatabaseWriterFilter filter : filters) {
                    filter.afterWrite(this.context, this.sourceTable, data);
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.FILTERMILLIS);
            }
        }
    }

    protected abstract LoadStatus insert(CsvData data);

    protected abstract LoadStatus delete(CsvData data, boolean useConflictDetection);

    protected abstract LoadStatus update(CsvData data, boolean applyChangesOnly, boolean useConflictDetection);

    protected abstract boolean create(CsvData data);

    protected abstract boolean sql(CsvData data);

    protected abstract void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails);

    protected abstract void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails, Object[] values);

    /***
     * Parses source data and returns string array with values in same positions as target columns.
     * 
     * @param data
     *            CsvData from the source
     * @param dataType
     *            CsvData type constant. Example: CsvData.ROW_DATA, CsvData.OLD_DATA
     * @return Values for target columns (as strings)
     */
    protected String[] getRowData(CsvData data, String dataType) {
        String[] targetValues = new String[targetTable.getColumnCount()];
        String[] originalValues = data.getParsedData(dataType);
        if (originalValues == null) {
            return null;
        }
        TableColumnSourceReferences columnReferences = this.getTargetColumnReferencesMap();
        for (TableColumnSourceReferences.ColumnSourceReferenceEntry referenceEntry : columnReferences) {
            if (referenceEntry.sourceColumnNo() < originalValues.length) {
                targetValues[referenceEntry.targetColumnNo()] = originalValues[referenceEntry.sourceColumnNo()];
            }
        }
        return targetValues;
    }

    /**
     * Looks up column mappings in the cache (or generates them). Used to quickly copy data from source columns into target columns.
     */
    protected TableColumnSourceReferences getTargetColumnReferencesMap() {
        String key = TableColumnSourceReferences.generateSearchKey(this.sourceTable, this.targetTable);
        TableColumnSourceReferences columnReferences = this.targetColumnSourceReferencesMap.get(key);
        if (columnReferences == null) {
            return buildTargetColumnReferencesMap(key);
        }
        return columnReferences;
    }

    /**
     * Builds new column mappings and saves it to the cache.
     */
    protected TableColumnSourceReferences buildTargetColumnReferencesMap(String key) {
        TableColumnSourceReferences columnReferences = new TableColumnSourceReferences(this.sourceTable, this.targetTable);
        this.targetColumnSourceReferencesMap.put(key, columnReferences);
        return columnReferences;
    }

    /**
     * Verifies that column mapping cache is up-to date. Rebuilds column mappings, if mismatch is detected.
     */
    protected void refreshTargetColumnReferencesMap() {
        if (this.targetTable == null) {
            return;
        }
        String key = TableColumnSourceReferences.generateSearchKey(this.sourceTable, this.targetTable);
        TableColumnSourceReferences columnReferences = this.targetColumnSourceReferencesMap.get(key);
        if (columnReferences == null || !columnReferences.isMatchingTables(this.sourceTable, this.targetTable)) {
            buildTargetColumnReferencesMap(key);
            return;
        }
    }

    /**
     * Clears column mapping cache. Used when DDL changes impact column definitions.
     */
    protected void clearTargetColumnReferencesMap() {
        this.targetColumnSourceReferencesMap.clear();
    }

    protected void bindVariables(Map<String, Object> variables) {
        variables.put("SOURCE_NODE_ID", batch.getSourceNodeId());
        variables.put("TARGET_NODE_ID", batch.getTargetNodeId());
        variables.put("log", log);
        variables.put("context", context);
        variables.putAll(context.getContext());
    }

    protected boolean script(CsvData data) {
        try {
            statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
            String script = data.getParsedData(CsvData.ROW_DATA)[0];
            Map<String, Object> variables = new HashMap<String, Object>();
            bindVariables(variables);
            Interpreter interpreter = new Interpreter();
            if (variables != null) {
                for (String variableName : variables.keySet()) {
                    interpreter.set(variableName, variables.get(variableName));
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("About to run: {}", script);
            }
            interpreter.eval(script);
            statistics.get(batch).increment(DataWriterStatisticConstants.SCRIPTCOUNT);
        } catch (EvalError e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    protected Map<String, String> getLookupDataMap(CsvData data, Conflict conflict) {
        Map<String, String> keyData = null;
        if (data.getDataEventType() == DataEventType.INSERT) {
            keyData = data.toColumnNameValuePairs(sourceTable.getColumnNames(), CsvData.ROW_DATA);
        } else if (conflict.getDetectType() != DetectConflict.USE_PK_DATA) {
            keyData = data.toColumnNameValuePairs(sourceTable.getColumnNames(), CsvData.OLD_DATA);
        }
        if (keyData == null || keyData.size() == 0) {
            keyData = data.toKeyColumnValuePairs(sourceTable);
        }
        return keyData;
    }

    protected String getPkDataFor(CsvData data, Column column) {
        String[] values = data.getParsedData(CsvData.PK_DATA);
        if (values != null) {
            Column[] columns = targetTable.getColumns();
            int index = -1;
            for (Column column2 : columns) {
                if (column2.isPrimaryKey()) {
                    index++;
                }
                if (column2.equals(column)) {
                    if (index < values.length) {
                        return values[index];
                    }
                    return null;
                }
            }
        } else {
            return data.getParsedData(CsvData.ROW_DATA)[targetTable.getColumnIndex(column)];
        }
        return null;
    }

    @Override
    public void end(Table table) {
    }

    @Override
    public void end(Batch batch, boolean inError) {
        this.lastData = null;
        if (batch.isIgnored()) {
            getStatistics().get(batch).increment(DataWriterStatisticConstants.IGNORECOUNT);
        }
        if (!inError) {
            notifyFiltersBatchComplete();
            commit(false);
        } else {
            rollback();
        }
    }

    @Override
    public void close() {
        this.targetColumnSourceReferencesMap.clear();
    }

    protected boolean hasFilterThatHandlesMissingTable(Table table) {
        if (writerSettings.getDatabaseWriterFilters() != null) {
            for (IDatabaseWriterFilter filter : writerSettings.getDatabaseWriterFilters()) {
                try {
                    if (filter.handlesMissingTable(context, table)) {
                        return true;
                    }
                } catch (NullPointerException ex) {
                    /*
                     * Script-based filters (e.g. BeanShell) that do not implement handlesMissingTable() will cause the JDK proxy's InvocationHandler to return
                     * null, which the JVM then fails to auto-unbox to boolean. Treat this as false — the filter does not handle missing tables.
                     */
                    log.debug("Filter {} returned null from handlesMissingTable(), treating as false", filter.getClass().getName());
                }
            }
        }
        return false;
    }

    protected void allowInsertIntoAutoIncrementColumns(boolean value, Table table) {
    }

    protected Table lookupTableAtTarget(Table sourceTable) {
        return sourceTable;
    }

    public Batch getBatch() {
        return batch;
    }

    public DataContext getContext() {
        return context;
    }

    public IDatabaseWriterConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    public void setConflictResolver(IDatabaseWriterConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }

    public Table getTargetTable() {
        return targetTable;
    }

    public Table getSourceTable() {
        return sourceTable;
    }

    @Override
    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }

    public DatabaseWriterSettings getWriterSettings() {
        return writerSettings;
    }
}
