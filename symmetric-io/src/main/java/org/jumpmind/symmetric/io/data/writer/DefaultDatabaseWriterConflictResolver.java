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

import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.SchemaObject;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.mapper.NumberMapper;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.db.util.DatabaseConstants;
import org.jumpmind.db.util.TableRow;
import org.jumpmind.exception.ParseException;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDatabaseWriterConflictResolver extends AbstractDatabaseWriterConflictResolver {
    private static final Logger log = LoggerFactory.getLogger(DefaultDatabaseWriterConflictResolver.class);

    @Override
    protected boolean isTimestampNewer(Conflict conflict, AbstractDatabaseWriter writer, CsvData data) {
        DynamicDefaultDatabaseWriter databaseWriter = (DynamicDefaultDatabaseWriter) writer;
        IDatabasePlatform platform = databaseWriter.getPlatform();
        String columnName = conflict.getDetectExpression();
        Table targetTable = writer.getTargetTable();
        Table sourceTable = writer.getSourceTable();
        String[] pkData = data.getPkData(targetTable);
        Object[] objectValues = databaseWriter.getPlatform().getObjectValues(
                writer.getBatch().getBinaryEncoding(), pkData, targetTable.getPrimaryKeyColumns());
        DmlStatement stmt = databaseWriter.getPlatform().createDmlStatement(DmlType.FROM, targetTable, writer.getWriterSettings().getTextColumnExpression());
        Column column = targetTable.getColumnWithName(columnName);
        if (column == null) {
            throw new RuntimeException(String.format("Could not find a timestamp column with a name of %s on the table %s.  "
                    + "Please check your conflict resolution configuration", columnName, targetTable.getQualifiedName()));
        }
        String sql = stmt.getColumnsSql(new Column[] { column });
        Map<String, String> newData = data.toColumnNameValuePairs(sourceTable.getColumnNames(),
                CsvData.ROW_DATA);
        String loadingStr = newData.get(columnName);
        Date loadingTs = null;
        Date existingTs = null;
        if (column.isTimestampWithTimezone()) {
            String existingStr = databaseWriter.getTransaction().queryForObject(sql, String.class,
                    objectValues);
            if (existingStr != null) {
                existingTs = FormatUtils.parseTimestampWithTimezone(existingStr, FormatUtils.TIMESTAMP_WITH_TIMEZONE_PATTERNS);
            }
            if (loadingStr != null) {
                loadingTs = FormatUtils.parseTimestampWithTimezone(loadingStr, FormatUtils.TIMESTAMP_WITH_TIMEZONE_PATTERNS);
            }
        } else {
            existingTs = databaseWriter.getTransaction().queryForObject(sql, Timestamp.class,
                    objectValues);
            Object[] values = platform.getObjectValues(writer.getBatch().getBinaryEncoding(),
                    new String[] { loadingStr }, new Column[] { column });
            if (values[0] instanceof Date) {
                loadingTs = (Date) values[0];
            } else if (values[0] instanceof String &&
                    column.getJdbcTypeName().equalsIgnoreCase(TypeMap.DATETIME2)) {
                // SQL Server DateTime2 type is treated as a string internally.
                loadingTs = databaseWriter.getPlatform().parseDate(Types.VARCHAR, (String) values[0], false);
            } else {
                throw new ParseException("Could not parse " + columnName + " with a value of "
                        + loadingStr + " for purposes of conflict detection");
            }
        }
        return existingTs == null || (loadingTs != null && loadingTs.compareTo(existingTs) > 0);
    }

    @Override
    protected boolean isCaptureTimeNewer(Conflict conflict, AbstractDatabaseWriter writer, CsvData data, String tableName) {
        DynamicDefaultDatabaseWriter databaseWriter = (DynamicDefaultDatabaseWriter) writer;
        Table targetTable = writer.getTargetTable();
        Map<String, String> keyData = getLookupDataMap(data, writer.getSourceTable());
        DmlStatement st = databaseWriter.getPlatform().createDmlStatement(DmlType.UPDATE, targetTable.getCatalog(), targetTable.getSchema(),
                targetTable.getName(), targetTable.getPrimaryKeyColumns(), targetTable.getPrimaryKeyColumns(),
                new boolean[targetTable.getPrimaryKeyColumnCount()], databaseWriter.getWriterSettings().getTextColumnExpression());
        String[] pkData = st.getLookupKeyData(keyData);
        Timestamp loadingTs = data.getAttribute(CsvData.ATTRIBUTE_CREATE_TIME);
        Date existingTs = null;
        String existingNodeId = null;
        boolean isWinnerByUk = true;
        if (loadingTs != null) {
            if (log.isDebugEnabled()) {
                log.debug("Finding last capture time for table {} with pk of {}", targetTable.getName(), ArrayUtils.toString(pkData));
            }
            if (databaseWriter.getPlatform(targetTable).supportsMultiThreadedTransactions() &&
                    (!databaseWriter.getPlatform().getDatabaseInfo().isRequiresSavePointsInTransaction() ||
                            !Boolean.TRUE.equals(databaseWriter.getContext().get(AbstractDatabaseWriter.TRANSACTION_ABORTED)))) {
                // make sure we lock the row that is in conflict to prevent a race with other data loading
                if (primaryKeyUpdateAllowed(databaseWriter, targetTable)) {
                    st.updateCteExpression(writer.getBatch().getSourceNodeId(), databaseWriter.getPlatform().getDdlBuilder().getCteExpressionPrefix());
                    Object[] values = databaseWriter.getPlatform().getObjectValues(writer.getBatch().getBinaryEncoding(),
                            pkData, targetTable.getPrimaryKeyColumns());
                    databaseWriter.getTransaction().prepareAndExecute(st.getSql(), ArrayUtils.addAll(values, values), st.getTypes());
                } else {
                    Column[] columns = targetTable.getNonPrimaryKeyColumns();
                    if (columns != null && columns.length > 0) {
                        Map<String, String> rowDataMap = data.toColumnNameValuePairs(targetTable.getColumnNames(), CsvData.ROW_DATA);
                        st = databaseWriter.getPlatform().createDmlStatement(DmlType.UPDATE, targetTable.getCatalog(), targetTable.getSchema(),
                                targetTable.getName(), targetTable.getPrimaryKeyColumns(), new Column[] { columns[0] },
                                new boolean[targetTable.getPrimaryKeyColumnCount()], databaseWriter.getWriterSettings().getTextColumnExpression());
                        st.updateCteExpression(writer.getBatch().getSourceNodeId(), databaseWriter.getPlatform().getDdlBuilder().getCteExpressionPrefix());
                        Object[] values = databaseWriter.getPlatform().getObjectValues(writer.getBatch().getBinaryEncoding(),
                                ArrayUtils.addAll(new String[] { rowDataMap.get(columns[0].getName()) }, pkData),
                                ArrayUtils.addAll(new Column[] { columns[0] }, targetTable.getPrimaryKeyColumns()));
                        databaseWriter.getTransaction().prepareAndExecute(st.getSql(), values, st.getTypes());
                    }
                }
            }
            modifyTimestampsForPrecision(databaseWriter.getPlatform(targetTable), targetTable, pkData);
            String pkCsv = CsvUtils.escapeCsvData(pkData);
            String sql = "select source_node_id, create_time from " + databaseWriter.getTablePrefix() +
                    "_data where table_name = ? and ((event_type = 'I' and row_data like ?) or " +
                    "(event_type in ('U', 'D') and pk_data like ?)) and create_time >= ? " +
                    "and (source_node_id is null or source_node_id != ?) order by create_time desc";
            Object[] args = new Object[] { tableName != null ? tableName : targetTable.getName(), pkCsv + "%", pkCsv, loadingTs,
                    writer.getBatch().getSourceNodeId() };
            log.debug("Querying capture time for CSV {}", pkCsv);
            Row row = null;
            if (databaseWriter.isLoadOnly() || databaseWriter.getPlatform(databaseWriter.getTablePrefix()).supportsMultiThreadedTransactions()) {
                // we may have waited for another transaction to commit, so query with a new transaction
                row = databaseWriter.getPlatform(databaseWriter.getTablePrefix()).getSqlTemplateDirty().queryForRow(sql, args);
            } else {
                row = writer.getContext().findTransaction().queryForRow(sql, args);
            }
            if (row != null) {
                existingTs = row.getDateTime("create_time");
                existingNodeId = row.getString("source_node_id");
                if (existingNodeId == null || existingNodeId.equals("")) {
                    existingNodeId = writer.getContext().getBatch().getTargetNodeId();
                }
            }
            if (data.getDataEventType() != DataEventType.DELETE) {
                isWinnerByUk = isCaptureTimeNewerForUk(writer, data);
            }
        }
        boolean isWinner = (existingTs == null && isWinnerByUk) || (isWinnerByUk && loadingTs != null && (loadingTs.getTime() > existingTs.getTime()
                || (loadingTs.getTime() == existingTs.getTime() && writer.getContext().getBatch().getSourceNodeId().hashCode() > existingNodeId.hashCode())));
        writer.getContext().put(DatabaseConstants.IS_CONFLICT_WINNER, isWinner);
        if (!isWinner && !isWinnerByUk) {
            Set<String> conflictLosingParentRows = writer.getWriterSettings().getConflictLosingParentRows();
            if (conflictLosingParentRows != null) {
                Map<String, String> rowDataMap = data.toColumnNameValuePairs(targetTable.getColumnNames(), CsvData.ROW_DATA);
                conflictLosingParentRows.add(getConflictRowKey(targetTable, rowDataMap));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("{} row from batch {} with local time of {} and remote time of {} for table {} and pk of {}",
                    isWinner ? "Winning" : "Losing", writer.getContext().getBatch().getNodeBatchId(),
                    existingTs, loadingTs, targetTable.getName(), ArrayUtils.toString(pkData));
        }
        return isWinner;
    }

    protected void modifyTimestampsForPrecision(IDatabasePlatform platform, Table table, String[] pkData) {
        boolean checkDatetime = platform.getName().startsWith(DatabaseNamesConstants.MSSQL) || platform.getName().startsWith(DatabaseNamesConstants.ASE);
        Column[] pkColumns = table.getPrimaryKeyColumns();
        for (int i = 0; i < pkColumns.length && i < pkData.length; i++) {
            if (pkData[i] == null) {
                continue;
            }
            int type = pkColumns[i].getMappedTypeCode();
            if (type == Types.TIMESTAMP || type == Types.TIME || type == ColumnTypes.TIMESTAMPTZ || type == ColumnTypes.TIMESTAMPLTZ
                    || type == ColumnTypes.TIMETZ) {
                int startIndex = pkData[i].indexOf(".") + 1;
                int endIndex = pkData[i].indexOf(" ", startIndex);
                if (endIndex == -1) {
                    endIndex = pkData[i].length();
                }
                if (startIndex != 0 && startIndex < pkData[i].length()) {
                    String fractional = pkData[i].substring(startIndex, endIndex);
                    boolean modified = false;
                    int fractionalPrecision = pkColumns[i].getSizeAsInt();
                    int fractionalAccuracy = fractionalPrecision;
                    if (checkDatetime) {
                        PlatformColumn platformColumn = pkColumns[i].findPlatformColumn(platform.getName());
                        if (platformColumn != null && platformColumn.getType().equalsIgnoreCase("datetime")) {
                            fractionalAccuracy = 2;
                        }
                    }
                    if (fractional.length() > fractionalAccuracy) {
                        log.debug("Reducing fsp from {} to {}", fractional.length(), fractionalAccuracy);
                        fractional = fractional.substring(0, fractionalAccuracy);
                        modified = true;
                    }
                    if (fractional.length() < fractionalPrecision) {
                        log.debug("Padding fsp of {} to match {}", fractional.length(), fractionalPrecision);
                        fractional += "%";
                        modified = true;
                    }
                    if (modified) {
                        pkData[i] = pkData[i].substring(0, startIndex) + fractional + pkData[i].substring(endIndex);
                    }
                }
            }
        }
    }

    protected boolean primaryKeyUpdateAllowed(DynamicDefaultDatabaseWriter databaseWriter, Table targetTable) {
        if (!databaseWriter.getPlatform(targetTable).getDatabaseInfo().isAutoIncrementUpdateAllowed()) {
            for (Column column : targetTable.getPrimaryKeyColumns()) {
                if (column.isAutoIncrement()) {
                    return false;
                }
            }
        }
        return true;
    }

    protected boolean uniqueKeyUpdateAllowed(DynamicDefaultDatabaseWriter databaseWriter, Table targetTable, Column[] uniqueColumns) {
        if (!databaseWriter.getPlatform(targetTable).getDatabaseInfo().isAutoIncrementUpdateAllowed()) {
            for (Column column : uniqueColumns) {
                if (column.isAutoIncrement()) {
                    return false;
                }
            }
        }
        return true;
    }

    protected Map<String, String> getLookupDataMap(CsvData data, Table table) {
        Map<String, String> keyData = null;
        if (data.getDataEventType() == DataEventType.INSERT) {
            keyData = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
        } else {
            keyData = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.OLD_DATA);
        }
        if (keyData == null || keyData.size() == 0) {
            keyData = data.toKeyColumnValuePairs(table);
        }
        return keyData;
    }

    protected boolean isCaptureTimeNewerForUk(AbstractDatabaseWriter writer, CsvData data) {
        DynamicDefaultDatabaseWriter databaseWriter = (DynamicDefaultDatabaseWriter) writer;
        Table targetTable = writer.getTargetTable();
        String[] ukData = new String[0];
        Timestamp loadingTs = data.getAttribute(CsvData.ATTRIBUTE_CREATE_TIME);
        Date existingTs = null;
        String existingNodeId = null;
        boolean isWinner = true;
        for (IIndex index : targetTable.getIndices()) {
            if (index.isUnique()) {
                boolean containsPk = true;
                for (Column column : targetTable.getPrimaryKeyColumns()) {
                    containsPk &= index.hasColumn(column);
                }
                if (containsPk) {
                    break;
                }
                Map<String, String> rowDataMap = data.toColumnNameValuePairs(targetTable.getColumnNames(), CsvData.ROW_DATA);
                Column[] uniqueKeyColumns = new Column[index.getColumnCount()];
                ukData = new String[index.getColumnCount()];
                boolean[] nullKeyValues = new boolean[index.getColumnCount()];
                StringBuilder ukCsv = new StringBuilder();
                int i = 0;
                for (Column column : targetTable.getColumns()) {
                    if (ukCsv.length() > 0) {
                        ukCsv.append(",");
                    }
                    if (index.hasColumn(column)) {
                        uniqueKeyColumns[i] = column;
                        ukData[i] = rowDataMap.get(column.getName());
                        nullKeyValues[i] = ukData[i] == null;
                        ukCsv.append(CsvUtils.escapeCsvData(new String[] { ukData[i++] }));
                    } else {
                        ukCsv.append("%");
                    }
                }
                int count = 0;
                Object[] values = databaseWriter.getPlatform().getObjectValues(writer.getBatch().getBinaryEncoding(), ukData, uniqueKeyColumns);
                if (values == null || values.length == 0) {
                    break;
                }
                if (!databaseWriter.getPlatform(targetTable).supportsMultiThreadedTransactions() ||
                        (databaseWriter.getPlatform().getDatabaseInfo().isRequiresSavePointsInTransaction() &&
                                Boolean.TRUE.equals(databaseWriter.getContext().get(AbstractDatabaseWriter.TRANSACTION_ABORTED)))) {
                    DmlStatement st = databaseWriter.getPlatform().createDmlStatement(DmlType.COUNT, targetTable.getCatalog(), targetTable.getSchema(),
                            targetTable.getName(), uniqueKeyColumns, uniqueKeyColumns, nullKeyValues,
                            databaseWriter.getWriterSettings().getTextColumnExpression());
                    count = databaseWriter.getPlatform(targetTable).getSqlTemplateDirty().queryForInt(st.getSql(), addKeyArgs(null, values));
                } else if (uniqueKeyUpdateAllowed(databaseWriter, targetTable, uniqueKeyColumns)) {
                    // make sure we lock the row that is in conflict to prevent a race with other data loading
                    DmlStatement st = databaseWriter.getPlatform().createDmlStatement(DmlType.UPDATE, targetTable.getCatalog(), targetTable.getSchema(),
                            targetTable.getName(), uniqueKeyColumns, uniqueKeyColumns, nullKeyValues,
                            databaseWriter.getWriterSettings().getTextColumnExpression());
                    st.updateCteExpression(databaseWriter.getBatch().getSourceNodeId(), databaseWriter.getPlatform().getDdlBuilder().getCteExpressionPrefix());
                    count = databaseWriter.getTransaction().prepareAndExecute(st.getSql(), addKeyArgs(values, values));
                }
                if (count > 0) {
                    String sql = "select source_node_id, create_time from " + databaseWriter.getTablePrefix() +
                            "_data where table_name = ? and event_type in ('I', 'U') and row_data like ? and " +
                            "create_time >= ? and (source_node_id is null or source_node_id != ?) order by create_time desc";
                    Object[] args = new Object[] { targetTable.getName(), ukCsv.toString(), loadingTs, writer.getBatch().getSourceNodeId() };
                    Row row = null;
                    if (databaseWriter.isLoadOnly() || databaseWriter.getPlatform(databaseWriter.getTablePrefix()).supportsMultiThreadedTransactions()) {
                        // we may have waited for another transaction to commit, so query with a new transaction
                        row = databaseWriter.getPlatform(databaseWriter.getTablePrefix()).getSqlTemplateDirty().queryForRow(sql, args);
                    } else {
                        row = writer.getContext().findTransaction().queryForRow(sql, args);
                    }
                    if (row != null) {
                        existingTs = row.getDateTime("create_time");
                        existingNodeId = row.getString("source_node_id");
                        if (existingNodeId == null || existingNodeId.equals("")) {
                            existingNodeId = writer.getContext().getBatch().getTargetNodeId();
                        }
                    }
                }
                isWinner = existingTs == null || (loadingTs != null && (loadingTs.getTime() > existingTs.getTime()
                        || (loadingTs.getTime() == existingTs.getTime() && writer.getContext().getBatch().getSourceNodeId().hashCode() > existingNodeId
                                .hashCode())));
                if (log.isDebugEnabled()) {
                    log.debug("{} row from batch {} with local time of {} and remote time of {} for table {} and uk of {}",
                            isWinner ? "Winning" : "Losing", writer.getContext().getBatch().getNodeBatchId(),
                            existingTs, loadingTs, targetTable.getName(), ArrayUtils.toString(ukData));
                }
                break;
            }
        }
        return isWinner;
    }

    protected Object[] addKeyArgs(Object[] currentArgs, Object[] additionalArgs) {
        Object[] args = currentArgs;
        if (additionalArgs != null) {
            for (Object arg : additionalArgs) {
                if (arg != null) {
                    args = ArrayUtils.addAll(args, arg);
                }
            }
        }
        return args;
    }

    @Override
    protected boolean isVersionNewer(Conflict conflict, AbstractDatabaseWriter writer, CsvData data) {
        DefaultDatabaseWriter databaseWriter = (DefaultDatabaseWriter) writer;
        String columnName = conflict.getDetectExpression();
        Table targetTable = writer.getTargetTable();
        Table sourceTable = writer.getSourceTable();
        String[] pkData = data.getPkData(targetTable);
        Object[] objectValues = databaseWriter.getPlatform().getObjectValues(
                writer.getBatch().getBinaryEncoding(), pkData, targetTable.getPrimaryKeyColumns());
        DmlStatement stmt = databaseWriter.getPlatform().createDmlStatement(DmlType.FROM, targetTable, writer.getWriterSettings().getTextColumnExpression());
        String sql = stmt.getColumnsSql(new Column[] { targetTable.getColumnWithName(columnName) });
        Long existingVersion = null;
        try {
            existingVersion = databaseWriter.getTransaction().queryForObject(sql, Long.class, objectValues);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to execute conflict resolution SQL: \"" +
                    sql + "\" values: " + Arrays.toString(objectValues), ex);
        }
        if (existingVersion == null) {
            return true;
        } else {
            Map<String, String> newData = data.toColumnNameValuePairs(sourceTable.getColumnNames(),
                    CsvData.ROW_DATA);
            Long loadingVersion = Long.valueOf(newData.get(columnName));
            return loadingVersion > existingVersion;
        }
    }

    @Override
    protected boolean checkForUniqueKeyViolation(AbstractDatabaseWriter writer, CsvData data, Conflict conflict, Throwable e, boolean isFallback) {
        DefaultDatabaseWriter databaseWriter = (DefaultDatabaseWriter) writer;
        IDatabasePlatform platform = databaseWriter.getPlatform();
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        int count = 0;
        if (e != null && sqlTemplate.isUniqueKeyViolation(e)) {
            Table targetTable = writer.getTargetTable();
            Map<String, String> values = data.toColumnNameValuePairs(writer.getSourceTable().getColumnNames(), CsvData.ROW_DATA);
            List<Column> whereColumns = targetTable.getPrimaryKeyColumnsAsList();
            List<String> whereValues = new ArrayList<String>();
            for (Column column : whereColumns) {
                whereValues.add(values.get(column.getName()));
            }
            boolean[] nullKeyValues = new boolean[whereColumns.size()];
            DmlStatement countStmt = platform.createDmlStatement(DmlType.COUNT, targetTable.getCatalog(), targetTable.getSchema(),
                    targetTable.getName(), whereColumns.toArray(new Column[0]), targetTable.getPrimaryKeyColumns(), nullKeyValues,
                    databaseWriter.getWriterSettings().getTextColumnExpression());
            Object[] objectValues = platform.getObjectValues(databaseWriter.getBatch().getBinaryEncoding(),
                    whereValues.toArray(new String[0]), whereColumns.toArray(new Column[0]));
            int pkCount = queryForInt(platform, databaseWriter, countStmt.getSql(), objectValues, countStmt.getTypes());
            boolean isUniqueKeyBlocking = false;
            boolean isPrimaryKeyBlocking = false;
            if ((!isFallback && data.getDataEventType() == DataEventType.UPDATE) ||
                    (isFallback && data.getDataEventType() == DataEventType.INSERT)) {
                Map<String, String> pkValues = data.toColumnNameValuePairs(writer.getSourceTable().getPrimaryKeyColumnNames(), CsvData.PK_DATA);
                boolean isPkChanged = false;
                if (pkValues.size() > 0) {
                    for (String name : targetTable.getPrimaryKeyColumnNames()) {
                        if (!Strings.CS.equals(values.get(name), pkValues.get(name))) {
                            isPkChanged = true;
                            break;
                        }
                    }
                }
                if (isPkChanged && pkCount > 0) {
                    isPrimaryKeyBlocking = true;
                } else {
                    isUniqueKeyBlocking = true;
                }
            } else if (isFallback && data.getDataEventType() == DataEventType.UPDATE) {
                if (pkCount > 0) {
                    isPrimaryKeyBlocking = true;
                } else {
                    isUniqueKeyBlocking = true;
                }
            } else if (!isFallback && data.getDataEventType() == DataEventType.INSERT && pkCount == 0) {
                isUniqueKeyBlocking = true;
            }
            long line = writer.getStatistics().get(writer.getBatch()).get(DataWriterStatisticConstants.LINENUMBER);
            if (isUniqueKeyBlocking) {
                if (writer.getWriterSettings().isAutoResolveUniqueIndexViolation()) {
                    if (targetTable.getIndices().length > 0) {
                        log.info("Unique key violation on table {} during {} with batch {} line {}.  Attempting to correct.",
                                targetTable.getName(), data.getDataEventType().toString(), writer.getContext().getBatch().getNodeBatchId(), line);
                    } else {
                        log.info("No primary key row or unique index found after unique key violation on table {} during {} with batch {} line {}.",
                                targetTable.getName(), data.getDataEventType().toString(), writer.getContext().getBatch().getNodeBatchId(), line);
                    }
                    for (IIndex index : targetTable.getIndices()) {
                        if (index.isUnique()) {
                            log.info("Correcting for possible violation of unique index {} on table {} during {} with batch {} line {}", index.getName(),
                                    targetTable.getName(), data.getDataEventType().toString(), writer.getContext().getBatch().getNodeBatchId(), line);
                            count += deleteUniqueConstraintRow(platform, sqlTemplate, databaseWriter, targetTable, index, data);
                        }
                    }
                    if (count == 0) {
                        checkIfMismatchedPrimaryKey(writer);
                    }
                } else if (log.isDebugEnabled()) {
                    log.debug("Did not issue correction for unique key violation on table {} during {} with batch {} line {}.",
                            targetTable.getName(), data.getDataEventType().toString(), writer.getContext().getBatch().getNodeBatchId(), line);
                }
            } else if (isPrimaryKeyBlocking) {
                if (writer.getWriterSettings().isAutoResolvePrimaryKeyViolation()) {
                    log.info("Correcting for update violation of primary key on table {} during {} with batch {} line {}", targetTable.getName(),
                            data.getDataEventType().toString(), writer.getContext().getBatch().getNodeBatchId(), line);
                    count += deleteRow(platform, sqlTemplate, databaseWriter, targetTable, whereColumns, whereValues, false);
                } else if (log.isDebugEnabled()) {
                    log.debug("Did not issue correction for update violation of primary key on table {} during {} with batch {} line {}", targetTable.getName(),
                            data.getDataEventType().toString(), writer.getContext().getBatch().getNodeBatchId(), line);
                }
            }
        }
        return count != 0;
    }

    @Override
    protected boolean isConflictingLosingParentRow(AbstractDatabaseWriter writer, CsvData data) {
        DefaultDatabaseWriter databaseWriter = (DefaultDatabaseWriter) writer;
        IDatabasePlatform platform = databaseWriter.getPlatform();
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        Set<String> conflictLosingParentRows = writer.getWriterSettings().getConflictLosingParentRows();
        if (conflictLosingParentRows != null && conflictLosingParentRows.size() > 0 && !writer.getBatch().isInitialLoad() &&
                (platform.getDatabaseInfo().isRequiresSavePointsInTransaction() || (writer.getContext().getLastError() != null &&
                        sqlTemplate.isForeignKeyViolation(writer.getContext().getLastError())))) {
            Table targetTable = writer.getTargetTable();
            Map<String, String> values = data.toColumnNameValuePairs(targetTable.getColumnNames(), CsvData.ROW_DATA);
            for (ForeignKey fk : targetTable.getForeignKeys()) {
                StringBuilder parentRowKey = new StringBuilder();
                parentRowKey.append(fk.getForeignTableName()).append(":");
                int i = 0;
                for (Reference ref : fk.getReferences()) {
                    if (i++ > 0) {
                        parentRowKey.append(":");
                    }
                    parentRowKey.append(values.get(ref.getLocalColumnName()));
                }
                if (conflictLosingParentRows.contains(parentRowKey.toString())) {
                    conflictLosingParentRows.add(getConflictRowKey(targetTable, values));
                    log.info("Detected losing row for batch {} for missing foreign key parent {}", writer.getBatch().getNodeBatchId(), parentRowKey);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean checkIfMismatchedPrimaryKey(AbstractDatabaseWriter writer) {
        boolean mismatched = false;
        DefaultDatabaseWriter databaseWriter = (DefaultDatabaseWriter) writer;
        Table targetTable = writer.getTargetTable();
        Table databaseTable = (Table) databaseWriter.getPlatform(writer.getTargetTable()).getRelationFromCache(targetTable.getCatalog(), targetTable
                .getSchema(),
                targetTable.getName(), false);
        if (databaseTable != null) {
            String[] names = targetTable.getPrimaryKeyColumnNames();
            String[] databaseNames = databaseTable.getPrimaryKeyColumnNames();
            if (names.length != databaseNames.length) {
                mismatched = true;
            } else {
                for (String name : names) {
                    boolean found = false;
                    for (String databaseName : databaseNames) {
                        if (databaseName.equalsIgnoreCase(name)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        mismatched = true;
                        break;
                    }
                }
            }
        }
        if (mismatched) {
            long line = databaseWriter.getStatistics().get(databaseWriter.getBatch()).get(DataWriterStatisticConstants.LINENUMBER);
            log.warn("Mismatch primary key for table {} in batch {} line {}.  Using {} but target database has {}", targetTable.getName(),
                    writer.getContext().getBatch().getNodeBatchId(), line, targetTable.getPrimaryKeyColumnNames(), databaseTable.getPrimaryKeyColumnNames());
        }
        return mismatched;
    }

    protected String getConflictRowKey(Table table, Map<String, String> values) {
        StringBuilder rowKey = new StringBuilder();
        rowKey.append(table.getName()).append(":");
        int j = 0;
        for (Column pk : table.getPrimaryKeyColumns()) {
            if (j++ > 0) {
                rowKey.append(":");
            }
            rowKey.append(values.get(pk.getName()));
        }
        return rowKey.toString();
    }

    protected int deleteUniqueConstraintRow(IDatabasePlatform platform, ISqlTemplate sqlTemplate, DefaultDatabaseWriter databaseWriter, Table targetTable,
            IIndex uniqueIndex, CsvData data) {
        Map<String, String> values = data.toColumnNameValuePairs(databaseWriter.getSourceTable().getColumnNames(), CsvData.ROW_DATA);
        List<Column> whereColumns = new ArrayList<Column>();
        List<String> whereValues = new ArrayList<String>();
        boolean hasNotNullValue = false;
        for (IndexColumn indexColumn : uniqueIndex.getColumns()) {
            whereColumns.add(targetTable.getColumnWithName(indexColumn.getName()));
            String value = values.get(indexColumn.getName());
            whereValues.add(value);
            hasNotNullValue = hasNotNullValue || (value != null);
        }
        if (!hasNotNullValue && databaseWriter.getWriterSettings().isAutoResolveUniqueIndexIgnoreNullValues()) {
            long line = databaseWriter.getStatistics().get(databaseWriter.getBatch()).get(DataWriterStatisticConstants.LINENUMBER);
            log.info("Did not correct for possible violation of unique index {} on table {} during {} with batch {} line {} because null values are ignored",
                    uniqueIndex.getName(), targetTable.getName(), data.getDataEventType().toString(), databaseWriter.getContext().getBatch().getNodeBatchId(),
                    line);
            return 0;
        }
        return deleteRow(platform, sqlTemplate, databaseWriter, targetTable, whereColumns, whereValues, true);
    }

    protected int deleteRow(IDatabasePlatform platform, ISqlTemplate sqlTemplate, DefaultDatabaseWriter databaseWriter, Table targetTable,
            List<Column> whereColumns, List<String> whereValues, boolean isUniqueKey) {
        Object[] values = platform.getObjectValues(databaseWriter.getBatch().getBinaryEncoding(),
                whereValues.toArray(new String[0]), whereColumns.toArray(new Column[0]));
        boolean[] nullKeyValues = new boolean[values.length];
        List<Object> valuesList = new ArrayList<Object>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                nullKeyValues[i] = true;
            } else {
                valuesList.add(values[i]);
            }
        }
        Object[] objectValues = valuesList.toArray(new Object[0]);
        DmlStatement fromStmt = platform.createDmlStatement(DmlType.FROM, targetTable.getCatalog(), targetTable.getSchema(),
                targetTable.getName(), whereColumns.toArray(new Column[0]), targetTable.getColumns(), nullKeyValues,
                databaseWriter.getWriterSettings().getTextColumnExpression());
        long line = databaseWriter.getStatistics().get(databaseWriter.getBatch()).get(DataWriterStatisticConstants.LINENUMBER);
        String cte = updateCteExpression(platform.getDatabaseInfo().getCteExpression(), databaseWriter.getBatch().getSourceNodeId(), platform.getDdlBuilder()
                .getCteExpressionPrefix());
        String sql = cte + " DELETE " + fromStmt.getSql();
        int count = 0;
        try {
            count = prepareAndExecute(platform, databaseWriter, sql, objectValues);
            if (count == 0) {
                log.info("Could not find and delete the blocking row by {} for batch {} line {}: {} {}",
                        isUniqueKey ? "unique constraint" : "primary key", databaseWriter.getBatch().getNodeBatchId(), line, sql,
                        ArrayUtils.toString(objectValues));
            }
        } catch (SqlException ex) {
            if (sqlTemplate.isForeignKeyChildExistsViolation(ex)) {
                log.info("Child exists foreign key violation while correcting {} violation for batch {} line {}.  Attempting further corrections.",
                        isUniqueKey ? "unique constraint" : "primary key", databaseWriter.getBatch().getNodeBatchId(), line);
                // Failed to delete the row because another row is referencing it
                DmlStatement selectStmt = platform.createDmlStatement(DmlType.SELECT, targetTable.getCatalog(), targetTable.getSchema(),
                        targetTable.getName(), whereColumns.toArray(new Column[0]), targetTable.getColumns(), nullKeyValues,
                        databaseWriter.getWriterSettings().getTextColumnExpression());
                // Query the row that we need to delete because it is blocking us
                Row uniqueRow = queryForRow(platform, databaseWriter, selectStmt.getSql(), objectValues);
                if (deleteForeignKeyChildren(platform, sqlTemplate, databaseWriter, targetTable, null, uniqueRow)) {
                    count = prepareAndExecute(platform, databaseWriter, sql, objectValues);
                }
            } else {
                throw ex;
            }
        }
        return count;
    }

    @Override
    protected boolean checkForForeignKeyChildExistsViolation(AbstractDatabaseWriter writer, CsvData data, Conflict conflict, Throwable e) {
        DefaultDatabaseWriter databaseWriter = (DefaultDatabaseWriter) writer;
        IDatabasePlatform platform = databaseWriter.getPlatform();
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        if (e != null && sqlTemplate.isForeignKeyChildExistsViolation(e)) {
            Table targetTable = writer.getTargetTable();
            long line = databaseWriter.getStatistics().get(databaseWriter.getBatch()).get(DataWriterStatisticConstants.LINENUMBER);
            if (!writer.getWriterSettings().isAutoResolveForeignKeyViolationDelete()) {
                if (log.isDebugEnabled()) {
                    log.debug("Did not issue correction for child exists foreign key violation on table {} during {} with batch {} line {}.",
                            targetTable.getName(), data.getDataEventType().toString(), writer.getContext().getBatch().getNodeBatchId(), line);
                }
                throw new RuntimeException(e);
            }
            log.info("Child exists foreign key violation on table {} during {} with batch {} line {}.  Attempting to correct.",
                    targetTable.getName(), data.getDataEventType().toString(), writer.getContext().getBatch().getNodeBatchId(), line);
            if (deleteForeignKeyChildren(platform, sqlTemplate, databaseWriter, writer.getTargetTable(), data, null)) {
                return true;
            } else {
                throw new RuntimeException("Failed to delete foreign table rows to fix foreign key violation for table '"
                        + writer.getTargetTable().getFullyQualifiedName() + "'");
            }
        }
        return false;
    }

    protected boolean deleteForeignKeyChildren(IDatabasePlatform platform, ISqlTemplate sqlTemplate, DefaultDatabaseWriter databaseWriter, Table targetTable,
            CsvData data, Row row) {
        List<TableRow> tableRows = new ArrayList<TableRow>();
        BinaryEncoding encoding = databaseWriter.getBatch().getBinaryEncoding();
        Table sourceTable = databaseWriter.getSourceTable();
        if (row != null) {
            tableRows.add(new TableRow(targetTable, row, null, null, null));
        } else if (data.getDataEventType() == DataEventType.INSERT) {
            Object[] objectValues = platform.getObjectValues(encoding, data.getParsedData(CsvData.ROW_DATA), sourceTable.getColumns());
            tableRows.add(new TableRow(targetTable, new Row(sourceTable.getColumnNames(), objectValues), null, null, null));
        } else {
            String[] oldData = data.getParsedData(CsvData.OLD_DATA);
            String[] pkData = data.getParsedData(CsvData.PK_DATA);
            if (oldData != null && oldData.length > 0) {
                Object[] objectValues = platform.getObjectValues(encoding, oldData, sourceTable.getColumns());
                tableRows.add(new TableRow(targetTable, new Row(sourceTable.getColumnNames(), objectValues), null, null, null));
            } else if (pkData != null && pkData.length > 0) {
                Object[] keys = platform.getObjectValues(encoding, pkData, sourceTable.getColumns());
                DmlStatement selectSt = platform.createDmlStatement(DmlType.SELECT, targetTable, null);
                Row targetRow = doInTransaction(platform, databaseWriter, new ITransactionCallback<Row>() {
                    @Override
                    public Row execute(ISqlTransaction transaction) {
                        return transaction.queryForRow(selectSt.getSql(), keys);
                    }
                });
                if (targetRow != null) {
                    tableRows.add(new TableRow(targetTable, targetRow, null, null, null));
                }
            }
        }
        if (tableRows.isEmpty()) {
            Object[] objectValues = platform.getObjectValues(encoding, data.getParsedData(CsvData.ROW_DATA), sourceTable.getColumns());
            tableRows.add(new TableRow(targetTable, new Row(sourceTable.getColumnNames(), objectValues), null, null, null));
        }
        List<TableRow> foreignTableRows = doInTransaction(platform, databaseWriter, new ITransactionCallback<List<TableRow>>() {
            @Override
            public List<TableRow> execute(ISqlTransaction transaction) {
                return platform.getDdlReader().getExportedForeignTableRows(transaction, tableRows, new HashSet<TableRow>(), databaseWriter.getBatch()
                        .getBinaryEncoding());
            }
        });
        if (foreignTableRows.isEmpty()) {
            log.info("Could not determine foreign table rows to fix foreign key violation for table '{}'", targetTable.getFullyQualifiedName());
            return false;
        }
        Collections.reverse(foreignTableRows);
        Set<TableRow> visited = new HashSet<TableRow>();
        for (TableRow foreignTableRow : foreignTableRows) {
            if (visited.add(foreignTableRow)) {
                Table foreignTable = foreignTableRow.getTable();
                log.info("Remove foreign row from table '{}' fk name '{}' where sql '{}' to correct table '{}' for column '{}'",
                        foreignTable.getFullyQualifiedName(), foreignTableRow.getFkName(), foreignTableRow.getWhereSql(),
                        targetTable.getName(), foreignTableRow.getReferenceColumnName());
                DatabaseInfo info = platform.getDatabaseInfo();
                String tableName = SchemaObject.getFullyQualifiedName(foreignTable.getCatalog(), foreignTable.getSchema(), foreignTable.getName(),
                        info.getDelimiterToken(), info.getCatalogSeparator(), info.getSchemaSeparator());
                String cte = updateCteExpression(info.getCteExpression(), databaseWriter.getBatch().getSourceNodeId(), platform.getDdlBuilder()
                        .getCteExpressionPrefix());
                String sql = cte + " DELETE FROM " + tableName + " WHERE " + foreignTableRow.getWhereSql();
                prepareAndExecute(platform, databaseWriter, sql);
            }
        }
        return true;
    }

    @Override
    protected void captureMissingDelete(Conflict conflict, AbstractDatabaseWriter writer, CsvData data) {
    }

    protected String updateCteExpression(String sql, String nodeId, String prefix) {
        if (sql == null) {
            return "";
        }
        if (!StringUtils.isBlank(prefix)) {
            sql = sql.replaceAll(prefix + ":",
                    prefix + ":" + nodeId);
        }
        return sql;
    }

    protected int prepareAndExecute(IDatabasePlatform platform, DefaultDatabaseWriter databaseWriter, String sql, Object... values) {
        return doInTransaction(platform, databaseWriter, new ITransactionCallback<Integer>() {
            @Override
            public Integer execute(ISqlTransaction transaction) {
                return transaction.prepareAndExecute(sql, values);
            }
        });
    }

    protected Row queryForRow(IDatabasePlatform platform, DefaultDatabaseWriter databaseWriter, String sql, Object... values) {
        return doInTransaction(platform, databaseWriter, new ITransactionCallback<Row>() {
            @Override
            public Row execute(ISqlTransaction transaction) {
                return transaction.queryForRow(sql, values);
            }
        });
    }

    protected int queryForInt(IDatabasePlatform platform, DefaultDatabaseWriter databaseWriter, String sql, Object[] values, int[] types) {
        return doInTransaction(platform, databaseWriter, new ITransactionCallback<Integer>() {
            @Override
            public Integer execute(ISqlTransaction transaction) {
                List<Number> list = transaction.query(sql, new NumberMapper(), values, types);
                if (list.size() > 0) {
                    return list.get(0).intValue();
                }
                return 0;
            }
        });
    }

    private <T> T doInTransaction(IDatabasePlatform platform, DefaultDatabaseWriter databaseWriter, ITransactionCallback<T> callback) {
        ISqlTransaction transaction = null;
        boolean useSavepoints = platform.getDatabaseInfo().isRequiresSavePointsInTransaction();
        boolean isAborted = Boolean.TRUE.equals(databaseWriter.getContext().get(AbstractDatabaseWriter.TRANSACTION_ABORTED));
        try {
            if (useSavepoints && isAborted) {
                transaction = platform.getSqlTemplate().startSqlTransaction(true);
                transaction.prepareAndExecute("select set_config('symmetric.triggers_disabled', '1', false)");
                String sourceNodeId = (String) databaseWriter.getContext().get("sourceNodeId");
                transaction.prepareAndExecute("select set_config('symmetric.node_disabled', '" +
                        (sourceNodeId == null ? "" : sourceNodeId) + "', false)");
            } else {
                transaction = databaseWriter.getTransaction();
                if (useSavepoints) {
                    transaction.execute("savepoint sym");
                }
            }
            return callback.execute(transaction);
        } catch (SqlException e) {
            if (useSavepoints && !isAborted) {
                transaction.execute("rollback to savepoint sym");
            }
            throw e;
        } finally {
            if (useSavepoints) {
                if (isAborted) {
                    transaction.prepareAndExecute("select set_config('symmetric.triggers_disabled', '', false)");
                    transaction.prepareAndExecute("select set_config('symmetric.node_disabled', '', false)");
                    transaction.close();
                } else {
                    transaction.execute("release savepoint sym");
                }
            }
        }
    }

    private interface ITransactionCallback<T> {
        public T execute(ISqlTransaction transaction);
    }
}
