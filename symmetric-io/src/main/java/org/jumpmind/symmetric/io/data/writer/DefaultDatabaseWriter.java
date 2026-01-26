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
package org.jumpmind.symmetric.io.data.writer;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.io.DatabaseXmlUtil;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DataTruncationException;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.LogSqlBuilder;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlScriptReader;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.io.IoConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.Conflict.DetectConflict;
import org.jumpmind.symmetric.io.data.writer.Conflict.DetectExpressionKey;
import org.jumpmind.util.CollectionUtils;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDatabaseWriter extends AbstractDatabaseWriter {
    private final static Logger log = LoggerFactory.getLogger(DefaultDatabaseWriter.class);
    public static final String CUR_DATA = "DatabaseWriter.CurData";
    private final String ATTRIBUTE_CHANNEL_ID_RELOAD = "reload";
    private final String TRUNCATE_PATTERN = "^(truncate)( table)?.*";
    private final String DELETE_PATTERN = "^(delete from).*";
    private final String INSERT_PATTERN = "^(insert into).*";
    private final String UPDATE_PATTERN = "^(update ).*";
    private final String ALTER_DEF_CONSTRAINT_PATTERN = " *alter +table +[\\[\\\"]{0,1}(.*?)[\\]\\\"]{0,1} +drop +constraint +[\\[\\\"]{0,1}(df__.*?)[\\]\\\"]{0,1} *";
    private final String ALTER_TABLE_PATTERN = " *(alter|create) +.*";
    protected IDatabasePlatform platform;
    protected ISqlTransaction transaction;
    protected DmlStatement currentDmlStatement;
    protected Object[] currentDmlValues;
    protected LogSqlBuilder logSqlBuilder = new LogSqlBuilder();
    protected Boolean isCteExpression;
    protected boolean hasUncommittedDdl;

    public DefaultDatabaseWriter(IDatabasePlatform platform) {
        this(platform, null, null);
    }

    public DefaultDatabaseWriter(IDatabasePlatform platform, DatabaseWriterSettings settings) {
        this(platform, null, settings);
    }

    public DefaultDatabaseWriter(IDatabasePlatform platform,
            IDatabaseWriterConflictResolver conflictResolver, DatabaseWriterSettings settings) {
        super(conflictResolver, settings);
        this.platform = platform;
        isRequiresSavePointsInTransaction = platform.getDatabaseInfo().isRequiresSavePointsInTransaction();
    }

    public IDatabasePlatform getPlatform() {
        return platform;
    }

    public IDatabasePlatform getPlatform(Table table) {
        return platform;
    }

    public IDatabasePlatform getPlatform(String table) {
        return platform;
    }

    public IDatabasePlatform getTargetPlatform() {
        return platform;
    }

    public ISqlTransaction getTransaction() {
        return transaction;
    }

    public ISqlTransaction getTransaction(Table table) {
        return transaction;
    }

    public ISqlTransaction getTransaction(String table) {
        return transaction;
    }

    public ISqlTransaction getTargetTransaction() {
        return transaction;
    }

    @Override
    public void open(DataContext context) {
        super.open(context);
        transaction = platform.getSqlTemplate().startSqlTransaction();
    }

    @Override
    public boolean start(Table table) {
        currentDmlStatement = null;
        boolean process = super.start(table);
        if (process && targetTable != null) {
            allowInsertIntoAutoIncrementColumns(true, targetTable);
        }
        return process;
    }

    @Override
    public void end(Table table) {
        super.end(table);
        if (transaction.isAllowInsertIntoAutoIncrement()) {
            // SQL Server using JDBC Batch loading requires a flush before turning off the identity insert.
            transaction.flush();
        }
        allowInsertIntoAutoIncrementColumns(false, targetTable);
    }

    @Override
    public void end(Batch batch, boolean inError) {
        currentDmlStatement = null;
        if (inError) {
            allowInsertIntoAutoIncrementColumns(false, targetTable);
        }
        super.end(batch, inError);
    }

    @Override
    public void close() {
        super.close();
        if (transaction != null) {
            transaction.close();
        }
    }

    @Override
    protected void commit(boolean earlyCommit) {
        if (transaction != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
                transaction.commit();
                if (!earlyCommit) {
                    notifyFiltersBatchCommitted();
                } else {
                    notifyFiltersEarlyCommit();
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
            }
        }
        super.commit(earlyCommit);
        hasUncommittedDdl = false;
    }

    protected void commit(boolean earlyCommit, ISqlTransaction newTransaction) {
        if (transaction != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
                newTransaction.commit();
                if (!earlyCommit) {
                    notifyFiltersBatchCommitted();
                } else {
                    notifyFiltersEarlyCommit();
                }
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
            }
        }
        super.commit(earlyCommit);
        hasUncommittedDdl = false;
    }

    @Override
    protected void rollback() {
        if (transaction != null) {
            try {
                statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
                transaction.rollback();
                notifyFiltersBatchRolledback();
            } finally {
                statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
            }
        }
        super.rollback();
        hasUncommittedDdl = false;
    }

    protected boolean isCteExpression() {
        if (isCteExpression == null) {
            isCteExpression = getPlatform().getDdlBuilder().getDatabaseInfo().getCteExpression() != null;
        }
        return isCteExpression;
    }

    protected void replaceCteExpression() {
        if (isCteExpression()) {
            currentDmlStatement.updateCteExpression(batch.getSourceNodeId(), getPlatform().getDdlBuilder().getCteExpressionPrefix());
        }
    }

    protected String replaceCteExpression(String sql) {
        String retSql = sql;
        if (isCteExpression()) {
            retSql = getPlatform().getDdlBuilder().getDatabaseInfo().getCteExpression() + " " + sql;
            retSql = DmlStatement.updateCteExpression(retSql, batch.getSourceNodeId(), getPlatform().getDdlBuilder().getCteExpressionPrefix());
        }
        return retSql;
    }

    protected boolean isDml(String sql) {
        return sql.matches(INSERT_PATTERN) || sql.matches(UPDATE_PATTERN) || sql.matches(DELETE_PATTERN);
    }

    @Override
    protected LoadStatus insert(CsvData data) {
        String[] values = null;
        try {
            if (isRequiresSavePointsInTransaction && conflictResolver != null && conflictResolver.isIgnoreRow(this, data)) {
                statistics.get(batch).increment(DataWriterStatisticConstants.IGNOREROWCOUNT);
                currentDmlStatement = null;
                return LoadStatus.SUCCESS;
            }
            statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
            if (requireNewStatement(DmlType.INSERT, data, false, true, null)) {
                checkTargetTableHasColumns();
                lastUseConflictDetection = true;
                currentDmlStatement = getPlatform().createDmlStatement(DmlType.INSERT, targetTable, writerSettings.getTextColumnExpression());
                replaceCteExpression();
                if (log.isDebugEnabled()) {
                    log.debug("Preparing dml: " + currentDmlStatement.getSql());
                }
                prepare();
            }
            boolean isFindAndThrowException = false;
            try {
                Conflict conflict = writerSettings.pickConflict(targetTable, batch);
                values = ArrayUtils.addAll(getRowData(data, CsvData.ROW_DATA),
                        currentDmlStatement.getLookupKeyData(getLookupDataMap(data, conflict)));
                long count = execute(data, values);
                statistics.get(batch).increment(DataWriterStatisticConstants.INSERTCOUNT, count);
                statistics.get(batch).incrementTableStats(targetTable.getName(), DataEventType.INSERT.getCode(), count);
                if (count > 0) {
                    return LoadStatus.SUCCESS;
                } else {
                    isFindAndThrowException = true;
                    findAndThrowInsertException(data, values);
                    return LoadStatus.CONFLICT;
                }
            } catch (SqlException ex) {
                if (isRequiresSavePointsInTransaction && !isFindAndThrowException) {
                    context.put(TRANSACTION_ABORTED, true);
                }
                if (getPlatform().getSqlTemplate().isUniqueKeyViolation(ex)) {
                    context.put(CONFLICT_ERROR, ex);
                    context.put(CUR_DATA, getCurData(getTransaction()));
                    context.setLastError(ex);
                    return LoadStatus.CONFLICT;
                } else {
                    throw ex;
                }
            }
        } catch (RuntimeException ex) {
            logFailureDetails(ex, data, true, values);
            throw ex;
        } finally {
            statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        }
    }

    private void findAndThrowInsertException(CsvData data, String[] values) throws SqlException {
        if (isRequiresSavePointsInTransaction && !getTransaction().isInBatchMode()) {
            try {
                getTransaction().execute("savepoint sym");
                getTransaction().prepare(currentDmlStatement.getSql(false));
                currentDmlValues = getPlatform().getObjectValues(batch.getBinaryEncoding(), values,
                        currentDmlStatement.getMetaData(), false, writerSettings.isFitToColumn());
                getTransaction().addRow(data, currentDmlValues, currentDmlStatement.getTypes());
            } catch (SqlException e) {
                getTransaction().execute("rollback to savepoint sym");
                throw e;
            } finally {
                getTransaction().execute("release savepoint sym");
                getTransaction().prepare(currentDmlStatement.getSql(true));
            }
        }
    }

    @Override
    protected LoadStatus delete(CsvData data, boolean useConflictDetection) {
        try {
            if (isRequiresSavePointsInTransaction && conflictResolver != null && conflictResolver.isIgnoreRow(this, data)) {
                statistics.get(batch).increment(DataWriterStatisticConstants.IGNOREROWCOUNT);
                currentDmlStatement = null;
                return LoadStatus.SUCCESS;
            }
            statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
            Conflict conflict = writerSettings.pickConflict(targetTable, batch);
            Map<String, String> lookupDataMap = null;
            if (requireNewStatement(DmlType.DELETE, data, useConflictDetection, useConflictDetection,
                    conflict.getDetectType())) {
                lastUseConflictDetection = useConflictDetection;
                List<Column> lookupKeys = null;
                if (!useConflictDetection) {
                    lookupKeys = targetTable.getPrimaryKeyColumnsAsList();
                } else {
                    switch (conflict.getDetectType()) {
                        case USE_OLD_DATA:
                        case USE_CHANGED_DATA:
                            if (data.contains(CsvData.OLD_DATA)) {
                                lookupKeys = targetTable.getColumnsAsList();
                            } else {
                                lookupKeys = targetTable.getPrimaryKeyColumnsAsList();
                            }
                            break;
                        case USE_VERSION:
                        case USE_TIMESTAMP:
                            List<Column> lookupColumns = new ArrayList<Column>();
                            Column versionColumn = targetTable.getColumnWithName(conflict
                                    .getDetectExpression());
                            if (versionColumn != null) {
                                lookupColumns.add(versionColumn);
                            } else {
                                log.error(
                                        "Could not find the timestamp/version column with the name {} on table {}.  Defaulting to using primary keys for the lookup.",
                                        conflict.getDetectExpression(), targetTable.getName());
                            }
                            Column[] pks = targetTable.getPrimaryKeyColumns();
                            for (Column column : pks) {
                                // make sure all of the PK keys are in the list
                                // only once and are always at the end of the
                                // list
                                lookupColumns.remove(column);
                                lookupColumns.add(column);
                            }
                            lookupKeys = lookupColumns;
                            break;
                        case USE_PK_DATA:
                        default:
                            lookupKeys = targetTable.getPrimaryKeyColumnsAsList();
                            break;
                    }
                }
                if (lookupKeys == null || lookupKeys.size() == 0) {
                    lookupKeys = targetTable.getColumnsAsList();
                }
                int lookupKeyCountBeforeColumnRemoval = lookupKeys.size();
                Iterator<Column> it = lookupKeys.iterator();
                while (it.hasNext()) {
                    Column col = it.next();
                    if ((getPlatform().isLob(col) && data.isNoBinaryOldData())
                            || !getPlatform().canColumnBeUsedInWhereClause(col)) {
                        it.remove();
                    }
                }
                if (lookupKeys.size() == 0) {
                    String msg = "There are no keys defined for "
                            + targetTable.getFullyQualifiedTableName()
                            + ".  Cannot build a delete statement.  ";
                    if (lookupKeyCountBeforeColumnRemoval > 0) {
                        msg += "The only keys defined are binary and they have been removed.";
                    }
                    throw new IllegalStateException(msg);
                }
                lookupDataMap = getLookupDataMap(data, conflict);
                boolean[] nullKeyValues = new boolean[lookupKeys.size()];
                for (int i = 0; i < lookupKeys.size(); i++) {
                    Column column = lookupKeys.get(i);
                    nullKeyValues[i] = !column.isRequired()
                            && lookupDataMap.get(column.getName()) == null;
                }
                currentDmlStatement = getPlatform().createDmlStatement(DmlType.DELETE,
                        targetTable.getCatalog(), targetTable.getSchema(), targetTable.getName(),
                        lookupKeys.toArray(new Column[lookupKeys.size()]), null, nullKeyValues, writerSettings.getTextColumnExpression());
                replaceCteExpression();
                if (log.isDebugEnabled()) {
                    log.debug("Preparing dml: " + currentDmlStatement.getSql());
                }
                prepare();
            }
            try {
                lookupDataMap = lookupDataMap == null ? getLookupDataMap(data, conflict) : lookupDataMap;
                long count = execute(data, currentDmlStatement.getLookupKeyData(lookupDataMap));
                statistics.get(batch).increment(DataWriterStatisticConstants.DELETECOUNT, count);
                statistics.get(batch).incrementTableStats(targetTable.getName(), DataEventType.DELETE.getCode(), count);
                if (count > 0) {
                    return LoadStatus.SUCCESS;
                } else {
                    context.put(CUR_DATA, null); // since a delete conflicted, there's no row to delete, so no cur data.
                    return LoadStatus.CONFLICT;
                }
            } catch (RuntimeException ex) {
                if (isRequiresSavePointsInTransaction && ex instanceof SqlException) {
                    context.put(TRANSACTION_ABORTED, true);
                }
                if (getPlatform().getSqlTemplate().isForeignKeyChildExistsViolation(ex)) {
                    context.put(CONFLICT_ERROR, ex);
                    context.put(CUR_DATA, null); // since a delete conflicted, there's no row to delete, so no cur data.
                    context.setLastError(ex);
                    return LoadStatus.CONFLICT;
                } else {
                    throw ex;
                }
            }
        } catch (SqlException ex) {
            logFailureDetails(ex, data, true);
            throw ex;
        } finally {
            statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        }
    }

    @Override
    protected LoadStatus update(CsvData data, boolean applyChangesOnly, boolean useConflictDetection) {
        try {
            if (isRequiresSavePointsInTransaction && conflictResolver != null && conflictResolver.isIgnoreRow(this, data)) {
                statistics.get(batch).increment(DataWriterStatisticConstants.IGNOREROWCOUNT);
                currentDmlStatement = null;
                return LoadStatus.SUCCESS;
            }
            statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
            String[] rowData = getRowData(data, CsvData.ROW_DATA);
            String[] oldData = getRowData(data, CsvData.OLD_DATA);
            ArrayList<String> changedColumnValueList = new ArrayList<>();
            ArrayList<Column> changedColumnsList = new ArrayList<>();
            for (int i = 0; i < targetTable.getColumnCount(); i++) {
                Column column = targetTable.getColumn(i);
                if (column != null) {
                    if (doesColumnNeedUpdated(i, column, data, rowData, oldData, applyChangesOnly)) {
                        changedColumnsList.add(column);
                        changedColumnValueList.add(rowData[i]);
                    }
                }
            }
            if (changedColumnsList.size() > 0) {
                Map<String, String> lookupDataMap = null;
                Conflict conflict = writerSettings.pickConflict(targetTable, batch);
                if (requireNewStatement(DmlType.UPDATE, data, applyChangesOnly,
                        useConflictDetection, conflict.getDetectType())) {
                    lastApplyChangesOnly = applyChangesOnly;
                    lastUseConflictDetection = useConflictDetection;
                    List<Column> lookupKeys = null;
                    if (!useConflictDetection) {
                        lookupKeys = targetTable.getPrimaryKeyColumnsAsList();
                    } else {
                        switch (conflict.getDetectType()) {
                            case USE_CHANGED_DATA:
                                if (data.contains(CsvData.OLD_DATA)) {
                                    ArrayList<Column> lookupColumns = new ArrayList<Column>(
                                            changedColumnsList);
                                    Column[] pks = targetTable.getPrimaryKeyColumns();
                                    for (Column column : pks) {
                                        // make sure all of the PK keys are in the
                                        // list only once and are always at the end
                                        // of the list
                                        lookupColumns.remove(column);
                                        lookupColumns.add(column);
                                    }
                                    removeExcludedColumns(conflict, lookupColumns);
                                    lookupKeys = lookupColumns;
                                } else {
                                    lookupKeys = targetTable.getPrimaryKeyColumnsAsList();
                                }
                                break;
                            case USE_OLD_DATA:
                                if (data.contains(CsvData.OLD_DATA)) {
                                    lookupKeys = targetTable.getColumnsAsList();
                                    removeExcludedColumns(conflict, lookupKeys);
                                } else {
                                    lookupKeys = targetTable.getPrimaryKeyColumnsAsList();
                                }
                                break;
                            case USE_VERSION:
                            case USE_TIMESTAMP:
                                ArrayList<Column> lookupColumns = new ArrayList<Column>();
                                Column versionColumn = targetTable.getColumnWithName(conflict
                                        .getDetectExpression());
                                if (versionColumn != null) {
                                    lookupColumns.add(versionColumn);
                                } else {
                                    log.error(
                                            "Could not find the timestamp/version column with the name {} on table {}.  Defaulting to using primary keys for the lookup.",
                                            conflict.getDetectExpression(), targetTable.getName());
                                }
                                Column[] pks = targetTable.getPrimaryKeyColumns();
                                for (Column column : pks) {
                                    // make sure all of the PK keys are in the
                                    // list only once and are always at the end
                                    // of the list
                                    lookupColumns.remove(column);
                                    lookupColumns.add(column);
                                }
                                lookupKeys = lookupColumns;
                                break;
                            case USE_PK_DATA:
                            default:
                                lookupKeys = targetTable.getPrimaryKeyColumnsAsList();
                                break;
                        }
                    }
                    if (lookupKeys == null || lookupKeys.size() == 0) {
                        lookupKeys = targetTable.getColumnsAsList();
                    }
                    int lookupKeyCountBeforeColumnRemoval = lookupKeys.size();
                    Iterator<Column> it = lookupKeys.iterator();
                    while (it.hasNext()) {
                        Column col = it.next();
                        if ((getPlatform().isLob(col) && data.isNoBinaryOldData())
                                || !getPlatform().canColumnBeUsedInWhereClause(col)) {
                            it.remove();
                        }
                    }
                    if (lookupKeys.size() == 0) {
                        String msg = "There are no keys defined for "
                                + targetTable.getFullyQualifiedTableName()
                                + ".  Cannot build an update statement.  ";
                        if (lookupKeyCountBeforeColumnRemoval > 0) {
                            msg += "The only keys defined are binary and they have been removed.";
                        }
                        throw new IllegalStateException(msg);
                    }
                    lookupDataMap = getLookupDataMap(data, conflict);
                    boolean[] nullKeyValues = new boolean[lookupKeys.size()];
                    for (int i = 0; i < lookupKeys.size(); i++) {
                        Column column = lookupKeys.get(i);
                        // the isRequired is a bit of a hack. This nullKeyValues
                        // should really be checking against the object values
                        // because some null values get translated into empty
                        // strings
                        nullKeyValues[i] = !column.isRequired()
                                && lookupDataMap.get(column.getName()) == null;
                    }
                    currentDmlStatement = getPlatform().createDmlStatement(DmlType.UPDATE,
                            targetTable.getCatalog(), targetTable.getSchema(),
                            targetTable.getName(),
                            lookupKeys.toArray(new Column[lookupKeys.size()]),
                            changedColumnsList.toArray(new Column[changedColumnsList.size()]),
                            nullKeyValues, writerSettings.getTextColumnExpression());
                    replaceCteExpression();
                    if (log.isDebugEnabled()) {
                        log.debug("Preparing dml: " + currentDmlStatement.getSql());
                    }
                    prepare();
                }
                rowData = changedColumnValueList
                        .toArray(new String[changedColumnValueList.size()]);
                lookupDataMap = lookupDataMap == null ? getLookupDataMap(data, conflict) : lookupDataMap;
                String[] values = ArrayUtils.addAll(rowData,
                        currentDmlStatement.getLookupKeyData(lookupDataMap));
                try {
                    long count = execute(data, values);
                    statistics.get(batch).increment(DataWriterStatisticConstants.UPDATECOUNT, count);
                    statistics.get(batch).incrementTableStats(targetTable.getName(), DataEventType.UPDATE.getCode(), count);
                    if (count > 0) {
                        return LoadStatus.SUCCESS;
                    } else {
                        context.put(CUR_DATA, getCurData(getTransaction()));
                        return LoadStatus.CONFLICT;
                    }
                } catch (SqlException ex) {
                    if (isRequiresSavePointsInTransaction) {
                        context.put(TRANSACTION_ABORTED, true);
                    }
                    if ((getPlatform().getSqlTemplate().isUniqueKeyViolation(ex) || getPlatform().getSqlTemplate().isForeignKeyChildExistsViolation(ex))) {
                        context.put(CONFLICT_ERROR, ex);
                        context.put(CUR_DATA, getCurData(getTransaction()));
                        context.setLastError(ex);
                        return LoadStatus.CONFLICT;
                    } else {
                        throw ex;
                    }
                }
            } else {
                checkTargetTableHasColumns();
                if (log.isDebugEnabled()) {
                    log.debug("Not running update for table {} with pk of {}.  There was no change to apply",
                            targetTable.getFullyQualifiedTableName(), data.getCsvData(CsvData.PK_DATA));
                }
                // There was no change to apply
                return LoadStatus.SUCCESS;
            }
        } catch (RuntimeException ex) {
            logFailureDetails(ex, data, true);
            throw ex;
        } finally {
            statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        }
    }

    protected void checkTargetTableHasColumns() {
        if (targetTable.getColumnCount() == 0) {
            throw new IllegalStateException("There are no columns defined for table " + targetTable.getFullyQualifiedTableName() +
                    " that match with " + sourceTable.getColumnCount() + " columns in the batch");
        }
    }

    @Override
    protected boolean create(CsvData data) {
        return create(data, false);
    }

    protected boolean create(CsvData data, boolean withoutDefaults) {
        String xml = null;
        Database db = null;
        boolean hasMatchingPlatform = false;
        try {
            getTargetTransaction().commit();
            statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
            xml = data.getParsedData(CsvData.ROW_DATA)[0];
            log.info("Incoming batch {} on channel {} contains the following table definition: {}", batch.getNodeBatchId(), batch.getChannelId(), xml);
            StringReader reader = new StringReader(xml);
            db = DatabaseXmlUtil.read(reader, false);
            hasMatchingPlatform = getTargetPlatform().hasMatchingPlatform(db);
            if (writerSettings.isCreateTableAlterCaseToMatchDatabaseDefault() && !hasMatchingPlatform) {
                getTargetPlatform().alterCaseToMatchDatabaseDefaultCase(db);
            }
            getTargetPlatform().makePlatformSpecific(db);
            if (withoutDefaults) {
                for (Table table : db.getTables()) {
                    table.removeAllColumnDefaults();
                }
            }
            if (writerSettings.isCreateIndexConvertUniqueToNonuniqueWhenColumnsNotRequired()) {
                if (!(getTargetPlatform().allowsUniqueIndexDuplicatesWithNulls())) {
                    for (Table table : db.getTables()) {
                        for (IIndex index : table.getUniqueIndices()) {
                            boolean needsFixed = false;
                            for (IndexColumn indexColumn : index.getColumns()) {
                                Column column = indexColumn.getColumn();
                                if (column != null && !column.isRequired()) {
                                    needsFixed = true;
                                    log.warn(
                                            "Detected Unique Index: {} with potential for multiple null values in table: {} on column: {}. Adjusting index to be NonUnique.",
                                            index.getName(), table.getName(), column.getName());
                                    break;
                                }
                            }
                            if (needsFixed) {
                                table.removeIndex(index);
                                IIndex newIndex = new NonUniqueIndex(index.getName());
                                for (IndexColumn indexColumn : index.getColumns()) {
                                    newIndex.addColumn(indexColumn);
                                }
                                // Make sure to add the platform index info to the new non-unique index
                                if (index.getPlatformIndexes() != null) {
                                    for (PlatformIndex platformIndex : index.getPlatformIndexes().values()) {
                                        newIndex.addPlatformIndex(platformIndex);
                                    }
                                }
                                table.addIndex(newIndex);
                            }
                        }
                    }
                }
            }
            if (writerSettings.isAlterTable()) {
                getTargetPlatform().alterTables(!writerSettings.isCreateTableFailOnError(), writerSettings.isCreateTableIncludeApplicationTriggers(),
                        writerSettings.getRuntimeConfigTriggerPrefix(), writerSettings.getAlterDatabaseInterceptors(), db.getTables());
            } else {
                getTargetPlatform().createDatabase(db, writerSettings.isCreateTableDropFirst(), !writerSettings.isCreateTableFailOnError());
            }
            getTargetPlatform().resetCachedTableModel();
            statistics.get(batch).increment(DataWriterStatisticConstants.CREATECOUNT);
            return true;
        } catch (RuntimeException ex) {
            if (!withoutDefaults && writerSettings.isCreateTableWithoutDefaultsOnError() && !hasMatchingPlatform) {
                log.info("Attempting to create table again without defaults");
                return create(data, true);
            } else {
                throw ex;
            }
        } finally {
            statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        }
    }

    @Override
    protected boolean sql(CsvData data) {
        try {
            statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
            String[] parsedData = data.getParsedData(CsvData.ROW_DATA);
            String script = parsedData[0];
            boolean captureChanges = parsedData.length > 1 && parsedData[1].equals("1");
            List<String> sqlStatements = getSqlStatements(script);
            long count = 0;
            Pattern defConsPattern = Pattern.compile(ALTER_DEF_CONSTRAINT_PATTERN, Pattern.CASE_INSENSITIVE);
            Pattern alterPattern = Pattern.compile(ALTER_TABLE_PATTERN, Pattern.CASE_INSENSITIVE);
            for (String sql : sqlStatements) {
                ISqlTransaction newTransaction = null;
                try {
                    sql = preprocessSqlStatement(sql);
                    Matcher defConsMatcher = defConsPattern.matcher(sql);
                    if (getPlatform().getName().startsWith(DatabaseNamesConstants.MSSQL) && defConsMatcher.matches()) {
                        String tableName = defConsMatcher.group(1);
                        String constraintName = defConsMatcher.group(2);
                        String constraintPrefix = constraintName.substring(0, constraintName.lastIndexOf("__"));
                        String querySql = "select c.name from sys.default_constraints c inner join sys.objects o on o.object_id = c.parent_object_id "
                                + "where c.name like ? and o.name = ?";
                        List<String> names = getTransaction().query(querySql, new StringMapper(), new Object[] { constraintPrefix + "%", tableName },
                                new int[] { Types.VARCHAR, Types.VARCHAR });
                        if (names.size() > 0) {
                            sql = sql.replace(constraintName, names.get(0));
                        }
                    }
                    if (captureChanges) {
                        newTransaction = getPlatform().getSqlTemplate().startSqlTransaction();
                        if (sql.matches(TRUNCATE_PATTERN) && getPlatform().getName().equals(DatabaseNamesConstants.DB2)) {
                            commit(true, newTransaction);
                        }
                        newTransaction.prepare(sql);
                        log.info("Running SQL event: {}", sql);
                        count += newTransaction.prepareAndExecute(sql);
                        if (log.isDebugEnabled()) {
                            log.debug("{} rows updated when running: {}", count, sql);
                        }
                    } else {
                        if (sql.matches(TRUNCATE_PATTERN) && getPlatform().getName().equals(DatabaseNamesConstants.DB2)) {
                            commit(true);
                        }
                        if (isDml(sql)) {
                            sql = replaceCteExpression(sql);
                        }
                        prepare(sql, data);
                        log.info("Running SQL event: {}", sql);
                        count += prepareAndExecute(sql, data);
                        if (log.isDebugEnabled()) {
                            log.debug("{} rows updated when running: {}", count, sql);
                        }
                    }
                    Matcher alterMatcher = alterPattern.matcher(sql);
                    hasUncommittedDdl |= alterMatcher.matches();
                } catch (Error ex) {
                    log.error("Failed to run the following sql: {}", sql);
                    if (newTransaction != null) {
                        newTransaction.rollback();
                    }
                    if (!writerSettings.isIgnoreSqlDataEventFailures()) {
                        throw ex;
                    }
                } catch (SqlException ex) {
                    log.info("Attempting to correct SQL statement failure", ex);
                    if (platform.getSqlTemplate().doesObjectAlreadyExist(ex)) {
                        String massagedSql = platform.massageForObjectAlreadyExists(sql);
                        if (!sql.equals(massagedSql)) {
                            if (massagedSql.contains("alter")) {
                                log.info("Changing the following sql to an alter because the created object already exists: {}", sql);
                            } else if (massagedSql.contains("create or replace") || massagedSql.contains("create or alter")) {
                                log.info("Changing the following sql to a create or replace because the created object already exists: {}", sql);
                            } else if (massagedSql.startsWith("drop")) {
                                log.info("Dropping the object before running the following sql because the created object already exists: {}", sql);
                            }
                            count = retryWithMassagedSql(massagedSql, newTransaction, data, captureChanges, count);
                        } else {
                            handleRuntimeException(ex, sql, newTransaction);
                        }
                    } else if (platform.getSqlTemplate().doesObjectNotExist(ex)) {
                        if (sql.trim().toUpperCase().startsWith("DROP")) {
                            log.info("Skipping the following sql because the dropped object does not exist: {}", sql);
                            if (newTransaction != null) {
                                newTransaction.rollback();
                            } else if (transaction != null) {
                                transaction.rollback();
                            }
                        } else {
                            String massagedSql = platform.massageForObjectDoesNotExist(sql);
                            if (!sql.equals(massagedSql)) {
                                log.info("Changing the following sql to a create because the altered object does not exist: {}", sql);
                                count = retryWithMassagedSql(massagedSql, newTransaction, data, captureChanges, count);
                            } else {
                                handleRuntimeException(ex, sql, newTransaction);
                            }
                        }
                    } else {
                        handleRuntimeException(ex, sql, newTransaction);
                    }
                } catch (RuntimeException ex) {
                    handleRuntimeException(ex, sql, newTransaction);
                } finally {
                    if (newTransaction != null) {
                        newTransaction.close();
                    }
                }
            }
            statistics.get(batch).increment(DataWriterStatisticConstants.SQLCOUNT);
            statistics.get(batch).increment(DataWriterStatisticConstants.SQLROWSAFFECTEDCOUNT,
                    count);
            return true;
        } finally {
            statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        }
    }

    private long retryWithMassagedSql(String sql, ISqlTransaction transaction, CsvData data, boolean captureChanges, long count) {
        try {
            if (captureChanges) {
                if (transaction == null) {
                    transaction = getPlatform().getSqlTemplate().startSqlTransaction();
                } else {
                    transaction.rollback();
                }
                transaction.prepare(sql);
                if (log.isDebugEnabled()) {
                    log.debug("About to run: {}", sql);
                }
                count += transaction.prepareAndExecute(sql);
                if (log.isDebugEnabled()) {
                    log.debug("{} rows updated when running: {}", count, sql);
                }
            } else {
                if (this.transaction != null) {
                    this.transaction.rollback();
                }
                prepare(sql, data);
                if (log.isDebugEnabled()) {
                    log.debug("About to run: {}", sql);
                }
                count += prepareAndExecute(sql, data);
                if (log.isDebugEnabled()) {
                    log.debug("{} rows updated when running: {}", count, sql);
                }
            }
        } catch (Error ex) {
            log.error("Failed to run the following sql after changing it: {}", sql);
            if (transaction != null) {
                transaction.rollback();
            }
            if (!writerSettings.isIgnoreSqlDataEventFailures()) {
                throw ex;
            }
        } catch (RuntimeException ex) {
            handleRuntimeException(ex, sql, transaction);
        }
        return count;
    }

    private void handleRuntimeException(RuntimeException ex, String sql, ISqlTransaction transaction) throws RuntimeException {
        log.error("Failed to run the following sql: {}", sql);
        if (transaction != null) {
            transaction.rollback();
        }
        if (!writerSettings.isIgnoreSqlDataEventFailures()) {
            throw ex;
        }
    }

    protected boolean requireNewStatement(DmlType currentType, CsvData data,
            boolean applyChangesOnly, boolean useConflictDetection,
            Conflict.DetectConflict detectType) {
        boolean requiresNew = currentDmlStatement == null || lastData == null
                || currentDmlStatement.getDmlType() != currentType
                || lastData.getDataEventType() != data.getDataEventType()
                || lastApplyChangesOnly != applyChangesOnly
                || lastUseConflictDetection != useConflictDetection;
        if (!requiresNew && currentType == DmlType.UPDATE) {
            String currentChanges = Arrays.toString(data.getChangedDataIndicators());
            String lastChanges = Arrays.toString(lastData.getChangedDataIndicators());
            requiresNew = !currentChanges.equals(lastChanges);
        }
        if (!requiresNew) {
            requiresNew |= containsNullLookupKeyDataSinceLastStatement(currentType, data,
                    detectType);
        }
        return requiresNew;
    }

    protected boolean containsNullLookupKeyDataSinceLastStatement(DmlType currentType,
            CsvData data, DetectConflict detectType) {
        boolean foundNullValueChange = false;
        if (currentType == DmlType.UPDATE || currentType == DmlType.DELETE) {
            if (detectType != null
                    && (detectType == DetectConflict.USE_CHANGED_DATA || detectType == DetectConflict.USE_OLD_DATA)) {
                String[] lastOldData = lastData.getParsedData(CsvData.OLD_DATA);
                String[] newOldData = data.getParsedData(CsvData.OLD_DATA);
                if (lastOldData != null && newOldData != null) {
                    for (int i = 0; i < lastOldData.length && i < newOldData.length; i++) {
                        String lastValue = lastOldData[i];
                        String value = newOldData[i];
                        if ((lastValue != null && value == null)
                                || (value != null && lastValue == null)) {
                            foundNullValueChange = true;
                        }
                    }
                }
            } else {
                String[] lastpkData = lastData.getParsedData(CsvData.PK_DATA);
                String[] newpkData = data.getParsedData(CsvData.PK_DATA);
                if (lastpkData != null && newpkData != null) {
                    for (int i = 0; i < lastpkData.length && i < newpkData.length; i++) {
                        String lastValue = lastpkData[i];
                        String value = newpkData[i];
                        if ((lastValue != null && value == null)
                                || (value != null && lastValue == null)) {
                            foundNullValueChange = true;
                        }
                    }
                }
            }
        }
        return foundNullValueChange;
    }

    @Override
    protected void targetTableWasChangedByFilter(Table oldTargetTable) {
        // allow for auto increment columns to be inserted into if appropriate
        if (oldTargetTable != null) {
            allowInsertIntoAutoIncrementColumns(false, oldTargetTable);
        }
        allowInsertIntoAutoIncrementColumns(true, targetTable);
        this.currentDmlStatement = null;
    }

    private void removeExcludedColumns(Conflict conflict,
            List<Column> lookupColumns) {
        String excludedString = conflict.getDetectExpressionValue(
                DetectExpressionKey.EXCLUDED_COLUMN_NAMES);
        if (!StringUtils.isBlank(excludedString)) {
            String excludedColumns[] = excludedString.split(",");
            if (excludedColumns.length > 0) {
                Iterator<Column> iter = lookupColumns.iterator();
                while (iter.hasNext()) {
                    Column column = iter.next();
                    for (String excludedColumn : excludedColumns) {
                        if (excludedColumn.trim().equalsIgnoreCase(column.getName())) {
                            iter.remove();
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails) {
        logFailureDetails(e, data, logLastDmlDetails, currentDmlValues);
    }

    @Override
    protected void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails, Object[] values) {
        StringBuilder failureMessage = new StringBuilder();
        failureMessage.append("Failed to process ");
        failureMessage.append(data.getDataEventType().toString().toLowerCase());
        failureMessage.append(" event in batch ");
        failureMessage.append(batch.getNodeBatchId());
        failureMessage.append(" at line ").append(batch.getLineCount());
        failureMessage.append(" on channel '");
        failureMessage.append(batch.getChannelId());
        failureMessage.append("'.\n");
        if (logLastDmlDetails && currentDmlStatement != null) {
            boolean shouldLogRawSql = true;
            if (writerSettings.isLogSqlParamsOnError()) {
                failureMessage.append("Failed sql was: ");
                String dynamicSQL = logSqlBuilder.buildDynamicSqlForLog(currentDmlStatement.getSql(), values, currentDmlStatement.getTypes());
                failureMessage.append(dynamicSQL);
                failureMessage.append("\n");
                shouldLogRawSql = !dynamicSQL.equals(currentDmlStatement.getSql());
            }
            if (shouldLogRawSql) {
                failureMessage.append("Failed raw sql was: ");
                failureMessage.append(currentDmlStatement.getSql());
            }
            failureMessage.append("\n");
        }
        if (logLastDmlDetails && values != null && currentDmlStatement != null) {
            if (writerSettings.isLogSqlParamsOnError()) {
                failureMessage.append("Failed sql parameters: ");
                failureMessage.append(StringUtils.abbreviate("[" + dmlValuesToString(values, currentDmlStatement.getTypes()) + "]",
                        CsvData.MAX_DATA_SIZE_TO_PRINT_TO_LOG));
                failureMessage.append("\n");
            }
            failureMessage.append("Failed sql parameters types: ");
            failureMessage.append("[" + TypeMap.getJdbcTypeDescriptions(currentDmlStatement.getTypes()) + "]");
            failureMessage.append("\n");
        }
        if (logLastDmlDetails && e instanceof SqlException && e.getCause() instanceof SQLException) {
            SQLException se = (SQLException) e.getCause();
            failureMessage.append("Failed sql state and code: ").append(se.getSQLState());
            failureMessage.append(" (").append(se.getErrorCode()).append(")");
            failureMessage.append("\n");
        }
        if (e instanceof DataTruncationException) {
            logDataTruncation(data, failureMessage);
        }
        if (writerSettings.isLogSqlParamsOnError()) {
            data.writeCsvDataDetails(failureMessage);
        }
        log.warn(failureMessage.toString(), e);
    }

    protected void logDataTruncation(CsvData data, StringBuilder failureMessage) {
        String[] rowData = data.getParsedData(CsvData.ROW_DATA);
        int rowIndex = 0;
        for (Column col : targetTable.getColumns()) {
            if (col.getJdbcTypeCode() == Types.VARCHAR) { // CHAR
                if (rowData[rowIndex] != null && rowData[rowIndex].length() > Integer.parseInt(col.getSize())) {
                    failureMessage.append("Failed truncation column: ");
                    failureMessage.append(col.getName());
                    failureMessage.append(" with size of: ");
                    failureMessage.append(Integer.parseInt(col.getSize()));
                    failureMessage.append(" failed to load data: ");
                    failureMessage.append(rowData[rowIndex]);
                    failureMessage.append("\n");
                }
            }
            rowIndex++;
        }
    }

    protected String dmlValuesToString(Object[] dmlValues, int[] types) {
        StringBuilder buff = new StringBuilder();
        if (dmlValues == null || dmlValues.length == 0) {
            return "";
        }
        LogSqlBuilder logSqlBuilder = new LogSqlBuilder();
        for (int i = 0; i < dmlValues.length; i++) {
            int type = i < types.length ? types[i] : -9999;
            buff.append(logSqlBuilder.formatValue(dmlValues[i], type));
            if (i < dmlValues.length - 1) {
                buff.append(", ");
            }
        }
        return buff.toString();
    }

    @Override
    protected void bindVariables(Map<String, Object> variables) {
        super.bindVariables(variables);
        ISqlTemplate template = getPlatform().getSqlTemplate();
        Class<?> templateClass = template.getClass();
        if (templateClass.getSimpleName().equals("JdbcSqlTemplate")) {
            try {
                Method method = templateClass.getMethod("getDataSource");
                variables.put("DATASOURCE", method.invoke(template));
            } catch (Exception e) {
                log.warn("Had trouble looking up the datasource used by the sql template", e);
            }
        }
    }

    protected List<String> getSqlStatements(String script) {
        List<String> sqlStatements = new ArrayList<String>();
        SqlScriptReader scriptReader = new SqlScriptReader(new StringReader(script));
        scriptReader.setStripOutComments(writerSettings.isStripOutCommentsInScripts());
        try {
            String sql = scriptReader.readSqlStatement();
            while (sql != null) {
                if (Strings.CI.startsWith(sql, "delimiter")) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found delimiter line: " + sql);
                    }
                    String delimiter = StringUtils.trimToNull(sql.substring("delimiter".length()));
                    if (delimiter != null) {
                        scriptReader.setDelimiter(delimiter);
                    }
                } else {
                    sqlStatements.add(sql);
                }
                sql = scriptReader.readSqlStatement();
            }
            return sqlStatements;
        } finally {
            try {
                if (scriptReader != null) {
                    scriptReader.close();
                }
            } catch (IOException e) {
            }
        }
    }

    protected String preprocessSqlStatement(String sql) {
        Table table = targetTable != null ? targetTable : sourceTable;
        sql = FormatUtils.replace("nodeId", batch.getTargetNodeId(), sql);
        if (table != null) {
            sql = FormatUtils.replace("catalogName", quoteString(table.getCatalog()), sql);
            sql = FormatUtils.replace("schemaName", quoteString(table.getSchema()), sql);
            sql = FormatUtils.replace("tableName", quoteString(table.getName()), sql);
            DatabaseInfo info = getPlatform().getDatabaseInfo();
            String quote = getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? info.getDelimiterToken() : "";
            sql = FormatUtils.replace("fullTableName", table.getQualifiedTableName(quote, info.getCatalogSeparator(), info.getSchemaSeparator()),
                    sql);
            final String old38CompatibilityTable = "sym_node";
            if (ATTRIBUTE_CHANNEL_ID_RELOAD.equals(batch.getChannelId()) && sql.matches(TRUNCATE_PATTERN)
                    && !table.getNameLowerCase().equals(old38CompatibilityTable)) {
                sql = getPlatform().getTruncateSql(table);
            } else if (ATTRIBUTE_CHANNEL_ID_RELOAD.equals(batch.getChannelId()) && sql.matches(DELETE_PATTERN)
                    && !sql.toUpperCase().contains("WHERE") && !table.getNameLowerCase().equals(old38CompatibilityTable)) {
                sql = getPlatform().getDeleteSql(table);
            }
        }
        sql = getPlatform().scrubSql(sql);
        sql = FormatUtils.replace("sourceNodeId", (String) context.get("sourceNodeId"), sql);
        sql = FormatUtils.replace("sourceNodeExternalId",
                (String) context.get("sourceNodeExternalId"), sql);
        sql = FormatUtils.replace("sourceNodeGroupId", (String) context.get("sourceNodeGroupId"),
                sql);
        sql = FormatUtils.replace("targetNodeId", (String) context.get("targetNodeId"), sql);
        sql = FormatUtils.replace("targetNodeExternalId",
                (String) context.get("targetNodeExternalId"), sql);
        sql = FormatUtils.replace("targetNodeGroupId", (String) context.get("targetNodeGroupId"),
                sql);
        return sql;
    }

    protected String quoteString(String string) {
        if (!StringUtils.isEmpty(string)) {
            String quote = getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn()
                    ? getPlatform()
                            .getDatabaseInfo().getDelimiterToken()
                    : "";
            return String.format("%s%s%s", quote, string, quote);
        } else {
            return string;
        }
    }

    protected boolean doesColumnNeedUpdated(int targetColumnIndex, Column column, CsvData data, String[] rowData, String[] oldData,
            boolean applyChangesOnly) {
        boolean needsUpdated = true;
        if (!getPlatform().getDatabaseInfo().isAutoIncrementUpdateAllowed() && column.isAutoIncrement()) {
            needsUpdated = false;
        } else if (oldData != null && applyChangesOnly) {
            /*
             * Old data isn't captured for some lob fields. When both values are null, then we always have to update because we don't know if the lob field was
             * previously null.
             */
            boolean containsEmptyLobColumn = getPlatform().isLob(column)
                    && StringUtils.isBlank(oldData[targetColumnIndex]);
            needsUpdated = !Strings.CS.equals(rowData[targetColumnIndex], oldData[targetColumnIndex])
                    || data.getParsedData(CsvData.OLD_DATA) == null
                    || containsEmptyLobColumn;
            if (containsEmptyLobColumn) {
                // indicate that we are considering the column to be changed
                updateChangedDataIndicator(data, column, true);
            }
        } else {
            /*
             * This is in support of creating update statements that don't use the keys in the set portion of the update statement. </p> In oracle (and maybe
             * not only in oracle) if there is no index on child table on FK column and update is performing on PK on master table, table lock is acquired on
             * child table. Table lock is taken not in exclusive mode, but lock contentions is possible.
             */
            needsUpdated = !column.isPrimaryKey()
                    || !Strings.CS.equals(rowData[targetColumnIndex], getPkDataFor(data, column));
            /*
             * A primary key change isn't indicated in the change data indicators when there is no old data. Need to update it manually in that case.
             */
            updateChangedDataIndicator(data, column, needsUpdated);
        }
        return needsUpdated;
    }

    protected void updateChangedDataIndicator(CsvData data, Column column, boolean needsUpdated) {
        boolean[] changeIndicators = data.getChangedDataIndicators();
        int index = sourceTable.getColumnIndex(column.getName());
        if (index != -1 && index < changeIndicators.length) {
            changeIndicators[index] = needsUpdated;
        } else if (index == -1) {
            log.warn("Unable to set change indicator because column {} not found on source table {}", column.getName(),
                    sourceTable.getFullyQualifiedTableName());
        } else {
            log.warn("Unable to set change indicator because column {} is index {} on source table {}, but row data has only {} values",
                    column.getName(), index, sourceTable.getFullyQualifiedTableName(), changeIndicators.length);
        }
    }

    protected void prepare() {
        getTransaction().prepare(currentDmlStatement.getSql());
    }

    protected void prepare(String sql, CsvData data) {
        getTransaction().prepare(sql);
    }

    protected int execute(CsvData data, String[] values) {
        currentDmlValues = getPlatform().getObjectValues(batch.getBinaryEncoding(), values,
                currentDmlStatement.getMetaData(), false, writerSettings.isFitToColumn());
        if (log.isDebugEnabled()) {
            log.debug("Submitting data [{}] with types [{}]",
                    dmlValuesToString(currentDmlValues, currentDmlStatement.getTypes()),
                    TypeMap.getJdbcTypeDescriptions(currentDmlStatement.getTypes()));
        }
        return getTransaction().addRow(data, currentDmlValues, currentDmlStatement.getTypes());
    }

    @Override
    protected Table lookupTableAtTarget(Table sourceTable) {
        String tableNameKey = getTableKey(sourceTable);
        Table table = lookupTableFromCache(sourceTable, tableNameKey);
        if (table == null) {
            try {
                if (hasUncommittedDdl) {
                    table = getPlatform(sourceTable).readTableFromDatabase(getTransaction(sourceTable), sourceTable.getCatalog(), sourceTable.getSchema(),
                            sourceTable.getName());
                } else {
                    table = getPlatform(sourceTable).getTableFromCache(sourceTable.getCatalog(), sourceTable.getSchema(),
                            sourceTable.getName(), false);
                }
                if (table != null) {
                    table = table.copyAndFilterColumns(sourceTable.getColumnNames(),
                            sourceTable.getPrimaryKeyColumnNames(), writerSettings.isUsePrimaryKeysFromSource(), false);
                    if (table.getPrimaryKeyColumnCount() == 0) {
                        table = getPlatform(table).makeAllColumnsPrimaryKeys(table);
                    }
                    if (writerSettings.isTreatDateTimeFieldsAsVarchar() && (batch.getChannelId() != null
                            && !batch.getChannelId().equals(IoConstants.CHANNEL_CONFIG)
                            && !batch.getChannelId().equals(IoConstants.CHANNEL_MONITOR))) {
                        Column[] columns = table.getColumns();
                        for (Column column : columns) {
                            if (column != null) {
                                int typeCode = column.getMappedTypeCode();
                                if (typeCode == Types.DATE || typeCode == Types.TIME || typeCode == Types.TIMESTAMP) {
                                    column.setMappedTypeCode(Types.VARCHAR);
                                }
                            }
                        }
                    }
                    if (writerSettings.isTreatBitFieldsAsInteger() && (batch.getChannelId() != null
                            && !batch.getChannelId().equals(IoConstants.CHANNEL_CONFIG)
                            && !batch.getChannelId().equals(IoConstants.CHANNEL_MONITOR))) {
                        Column[] columns = table.getColumns();
                        for (Column column : columns) {
                            if (column != null) {
                                if (column.getJdbcTypeName().equals("BIT") && column.getSizeAsInt() > 1) {
                                    column.setMappedTypeCode(Types.INTEGER);
                                }
                            }
                        }
                    }
                    if (table.hasGeneratedColumns()) {
                        removeGeneratedColumns(table);
                    }
                    putTableInCache(tableNameKey, table);
                }
            } catch (SqlException sqle) {
                // TODO: is there really a "does not exist" exception or should this be removed? copied from AbstractJdbcDdlReader.readTable()
                if (sqle.getMessage() == null || !Strings.CI.contains(sqle.getMessage(), "does not exist")) {
                    throw sqle;
                }
            }
        }
        return table;
    }

    protected void removeGeneratedColumns(Table table) {
        List<Column> adjustedColumns = new ArrayList<Column>();
        for (int i = 0; i < table.getColumnCount(); i++) {
            Column col = table.getColumn(i);
            if (!col.isGenerated()) {
                adjustedColumns.add(col);
            }
        }
        table.removeAllColumns();
        table.addColumns(adjustedColumns);
    }

    protected String getTableKey(Table table) {
        return table.getTableKey();
    }

    protected Table lookupTableFromCache(Table sourceTable, String tableKey) {
        return targetTables.get(tableKey);
    }

    protected void putTableInCache(String tableKey, Table table) {
        targetTables.put(tableKey, table);
    }

    public DmlStatement getCurrentDmlStatement() {
        return currentDmlStatement;
    }

    @Override
    public DatabaseWriterSettings getWriterSettings() {
        return writerSettings;
    }

    public int prepareAndExecute(String sql, CsvData data) {
        return getTransaction().prepareAndExecute(sql);
    }

    protected String getCurData(ISqlTransaction transaction) {
        String curVal = null;
        if (writerSettings.isSaveCurrentValueOnError()) {
            String[] keyNames = Table.getArrayColumns(context.getTable().getPrimaryKeyColumns());
            String[] columnNames = Table.getArrayColumns(context.getTable().getColumns());
            org.jumpmind.db.model.Table targetTable = getPlatform().getTableFromCache(
                    context.getTable().getCatalog(), context.getTable().getSchema(),
                    context.getTable().getName(), false);
            targetTable = targetTable.copyAndFilterColumns(columnNames, keyNames, true, false);
            String[] data = context.getData().getParsedData(CsvData.OLD_DATA);
            if (data == null) {
                data = context.getData().getParsedData(CsvData.ROW_DATA);
            }
            Column[] columns = targetTable.getColumns();
            Object[] objectValues = getPlatform()
                    .getObjectValues(context.getBatch().getBinaryEncoding(), data,
                            columns);
            Map<String, Object> columnDataMap = CollectionUtils
                    .toMap(columnNames, objectValues);
            Column[] pkColumns = targetTable.getPrimaryKeyColumns();
            Object[] args = new Object[pkColumns.length];
            for (int i = 0; i < pkColumns.length; i++) {
                args[i] = columnDataMap.get(pkColumns[i].getName());
            }
            DmlStatement sqlStatement = getPlatform()
                    .createDmlStatement(DmlType.SELECT, targetTable, writerSettings.getTextColumnExpression());
            Row row = null;
            List<Row> list = transaction.query(sqlStatement.getSql(),
                    new ISqlRowMapper<Row>() {
                        @Override
                        public Row mapRow(Row row) {
                            return row;
                        }
                    }, args, null);
            if (list != null && list.size() > 0) {
                row = list.get(0);
            }
            if (row != null) {
                String[] existData = getPlatform().getStringValues(context.getBatch().getBinaryEncoding(),
                        columns, row, false, false);
                if (existData != null) {
                    curVal = CsvUtils.escapeCsvData(existData);
                }
            }
        }
        return curVal;
    }

    @Override
    protected void allowInsertIntoAutoIncrementColumns(boolean value, Table table) {
        DatabaseInfo dbInfo = getPlatform(table).getDatabaseInfo();
        String quote = dbInfo.getDelimiterToken();
        String catalogSeparator = dbInfo.getCatalogSeparator();
        String schemaSeparator = dbInfo.getSchemaSeparator();
        getTransaction(table).allowInsertIntoAutoIncrementColumns(value, table, quote, catalogSeparator, schemaSeparator);
    }
}
