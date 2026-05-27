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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.DbCompareReport.TableReport;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DbCompare has the ability to compare two SQL-based datasources and output a report of of differences, and optionally SQL to bring the target into sync with
 * the source.
 */
public class DbCompare {
    private static final String TABLE_NAME_PART_CATALOG = "catalog";
    private static final String TABLE_NAME_PART_SCHEMA = "schema";
    private static final String TABLE_NAME_PART_TABLE = "table";
    final Logger log = LoggerFactory.getLogger(getClass());
    ISqlRowMapper<Row> defaultRowMapper = new ISqlRowMapper<Row>() {
        @Override
        public Row mapRow(Row row) {
            return row;
        }
    };
    private ISymmetricEngine sourceEngine;
    private ISymmetricEngine targetEngine;
    private DbCompareConfig config;
    private DbValueComparator dbValueComparator;

    public DbCompare(ISymmetricEngine sourceEngine, ISymmetricEngine targetEngine, DbCompareConfig config) {
        this.config = config;
        this.sourceEngine = sourceEngine;
        this.targetEngine = targetEngine;
        dbValueComparator = new DbValueComparator(sourceEngine, targetEngine);
    }

    public DbCompareReport compare() {
        dbValueComparator.setNumericScale(config.getNumericScale());
        dbValueComparator.setDateTimeFormat(config.getDateTimeFormat());
        log.info("Starting DBCompare with config:\n{}", config.report());
        OutputStream sqlDiffOutput = getSqlDiffOutputStream();
        DbCompareReport report = new DbCompareReport();
        long start = System.currentTimeMillis();
        List<DbCompareTables> tablesToCompare = getTablesToCompare();
        report.printReportHeader(System.out);
        for (DbCompareTables tables : tablesToCompare) {
            try {
                TableReport tableReport = compareTables(tables, sqlDiffOutput);
                report.addTableReport(tableReport);
                long elapsed = System.currentTimeMillis() - start;
                log.info("Completed table {}.  Elapsed time: {}", tableReport,
                        DurationFormatUtils.formatDurationWords((elapsed), true, true));
                report.printTableReport(tableReport, System.out);
            } catch (Exception e) {
                log.error("Exception while comparing " + tables.getSourceTable() +
                        " to " + tables.getTargetTable(), e);
            }
        }
        report.printReportFooter(System.out);
        long totalTime = System.currentTimeMillis() - start;
        log.info("dbcompare complete.  Total Time: {}",
                DurationFormatUtils.formatDurationWords((totalTime), true, true));
        return report;
    }

    protected OutputStream getSqlDiffOutputStream() {
        String outputSqlDiffFileName = config.getOutputSql();
        if (!StringUtils.isEmpty(outputSqlDiffFileName) && !outputSqlDiffFileName.contains("%t")) {
            try {
                return new FirstUseFileOutputStream(outputSqlDiffFileName);
            } catch (Exception e) {
                throw new RuntimeException("Failed to open stream to file '" + outputSqlDiffFileName + "'", e);
            }
        } else {
            return null;
        }
    }

    protected OutputStream getSqlDiffOutputStream(DbCompareTables tables) {
        String outputSqlDiffFileName = config.getOutputSql();
        if (!StringUtils.isEmpty(outputSqlDiffFileName)) {
            // allow file per table.
            String fileNameFormatted = outputSqlDiffFileName.replace("%t", "%s");
            fileNameFormatted = String.format(fileNameFormatted, tables.getSourceTable().getName());
            fileNameFormatted = fileNameFormatted.replaceAll("\"", "").replaceAll("\\]", "").replaceAll("\\[", "");
            try {
                return new FirstUseFileOutputStream(fileNameFormatted);
            } catch (Exception e) {
                throw new RuntimeException("Failed to open stream to file '" + fileNameFormatted + "'", e);
            }
        } else {
            return null;
        }
    }

    protected TableReport compareTables(DbCompareTables tables, OutputStream sqlDiffOutput) {
        String sourceSelect = getSourceComparisonSQL(tables, sourceEngine.getTargetDialect().getTargetPlatform());
        String targetSelect = getTargetComparisonSQL(tables, targetEngine.getTargetDialect().getTargetPlatform());
        ISymmetricDialect sourceDialect = sourceEngine.getSymmetricDialect();
        ISymmetricDialect targetDialect = targetEngine.getSymmetricDialect();
        boolean isUsingUnitypes = targetDialect.getParameterService().is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC) ||
                sourceDialect.getParameterService().is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC);
        boolean sourceTableContainUnitypes = false;
        boolean targetTableContainUnitypes = false;
        List<Column> sourceColumns = tables.getSourceTable().getColumnsAsList();
        for (Column column : sourceColumns) {
            if (isUniType(column.getJdbcTypeName())) {
                sourceTableContainUnitypes = true;
            }
        }
        List<Column> targetColumns = tables.getTargetTable().getColumnsAsList();
        for (Column column : targetColumns) {
            if (isUniType(column.getJdbcTypeName())) {
                targetTableContainUnitypes = true;
            }
        }
        CountingSqlReadCursor sourceCursor = new CountingSqlReadCursor(sourceEngine.getTargetDialect().getTargetPlatform().getSqlTemplateDirty().queryForCursor(
                sourceSelect,
                defaultRowMapper));
        CountingSqlReadCursor targetCursor = new CountingSqlReadCursor(targetEngine.getTargetDialect().getTargetPlatform().getSqlTemplateDirty().queryForCursor(
                targetSelect,
                defaultRowMapper));
        TableReport tableReport = new TableReport();
        tableReport.setSourceTable(tables.getSourceTable().getName());
        tableReport.setTargetTable(tables.getTargetTable().getName());
        long time = System.currentTimeMillis();
        Row sourceRow = sourceCursor.next();
        Row targetRow = targetCursor.next();
        time = System.currentTimeMillis() - time;
        log.debug("Took " + time + " milliseconds to get the starting source and target rows.");
        int counter = 0;
        long startTime = System.currentTimeMillis();
        DbCompareDiffWriter diffWriter = null;
        OutputStream stream = null;
        if (sqlDiffOutput != null) {
            diffWriter = new DbCompareDiffWriter(targetEngine, tables, sqlDiffOutput);
        } else {
            stream = getSqlDiffOutputStream(tables);
            diffWriter = new DbCompareDiffWriter(targetEngine, tables, stream);
        }
        diffWriter.setContinueAfterError(config.isContinueAfterError());
        try {
            while (true) {
                if (sourceRow == null && targetRow == null) {
                    break;
                }
                if (isUsingUnitypes && (sourceTableContainUnitypes || targetTableContainUnitypes)) {
                    long keyCheck = System.currentTimeMillis();
                    Map<String, Object> recordToAdd = new HashMap<String, Object>();
                    Map<String, Object> recordToDelete = new HashMap<String, Object>();
                    // need to find a way to determine which row, source or target, has the uni key
                    if (targetTableContainUnitypes) {
                        if (targetRow != null) {
                            for (String key : targetRow.keySet()) {
                                if (key.startsWith("_uni_")) {
                                    recordToAdd.put(key.substring(5), targetRow.get(key));
                                    recordToDelete.put(key, targetRow.get(key));
                                }
                            }
                            modifyRowForUnitypes(targetRow, recordToAdd, true);
                            modifyRowForUnitypes(targetRow, recordToDelete, false);
                        }
                    } else if (sourceTableContainUnitypes) {
                        if (sourceRow != null) {
                            for (String key : sourceRow.keySet()) {
                                if (key.startsWith("_uni_")) {
                                    recordToAdd.put(key.substring(5), sourceRow.get(key));
                                    recordToDelete.put(key, sourceRow.get(key));
                                }
                            }
                            modifyRowForUnitypes(sourceRow, recordToAdd, true);
                            modifyRowForUnitypes(sourceRow, recordToDelete, false);
                        }
                    }
                    keyCheck = System.currentTimeMillis() - keyCheck;
                    log.debug("Took " + keyCheck + " milliseconds to check for unitype keys and adjust.");
                }
                counter++;
                if ((counter % 50000) == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("{} rows processed for table {}. Elapsed time {}. ({} ms.) Current report status {}",
                            counter, tables.getSourceTable().getName(),
                            DurationFormatUtils.formatDurationWords((elapsed), true, true), elapsed,
                            tableReport);
                }
                DbCompareRow sourceCompareRow = sourceRow != null ? new DbCompareRow(sourceEngine, dbValueComparator, tables.getSourceTable(), sourceRow)
                        : null;
                DbCompareRow targetCompareRow = targetRow != null ? new DbCompareRow(targetEngine, dbValueComparator, tables.getTargetTable(), targetRow)
                        : null;
                diffWriter.setError(false);
                diffWriter.setThrowable(null);
                DbCompareRow targetCompareRowCopy = null;
                DbCompareRow sourceCompareRowCopy = null;
                if (isUsingUnitypes) {
                    targetCompareRowCopy = targetRow != null ? new DbCompareRow(targetEngine, dbValueComparator, tables.getTargetTable().copy(), (Row) targetRow
                            .clone())
                            : null;
                    sourceCompareRowCopy = sourceRow != null ? new DbCompareRow(sourceEngine, dbValueComparator, tables.getSourceTable().copy(), (Row) sourceRow
                            .clone())
                            : null;
                }
                long ts1 = System.currentTimeMillis();
                int comparePk = comparePk(tables, sourceCompareRow, targetCompareRow);
                ts1 = System.currentTimeMillis() - ts1;
                if (ts1 > 2) {
                    log.debug("Took " + ts1 + " milliseconds to run comparePKs.");
                }
                if (comparePk == 0) {
                    long ts = System.currentTimeMillis();
                    Map<Column, String> deltas = sourceCompareRow.compareTo(tables, targetCompareRow);
                    ts = System.currentTimeMillis() - ts;
                    if (ts > 2) {
                        log.debug("Took " + ts + " milliseconds to run compareTo.");
                    }
                    Map<Column, String> deltasCopy = new LinkedHashMap<Column, String>();
                    if (isUsingUnitypes) {
                        for (Column column : deltas.keySet()) {
                            deltasCopy.put((Column) column.clone(), deltas.get(column));
                        }
                    }
                    if (targetCompareRowCopy != null || sourceCompareRowCopy != null) {
                        for (String pkColumnName : targetCompareRowCopy.getTable().getPrimaryKeyColumnNames()) {
                            if (isUniType(targetCompareRowCopy.getTable().getColumnWithName(pkColumnName).getJdbcTypeName())) {
                                targetCompareRowCopy.getTable().getColumnWithName(pkColumnName).setMappedType("VARCHAR");
                            }
                        }
                        for (String pkColumnName : sourceCompareRowCopy.getTable().getPrimaryKeyColumnNames()) {
                            if (isUniType(sourceCompareRowCopy.getTable().getColumnWithName(pkColumnName).getJdbcTypeName())) {
                                sourceCompareRowCopy.getTable().getColumnWithName(pkColumnName).setMappedType("VARCHAR");
                            }
                        }
                    }
                    if (deltas.isEmpty()) {
                        tableReport.countMatchedRow();
                    } else {
                        if (isUsingUnitypes) {
                            long ts2 = System.currentTimeMillis();
                            diffWriter.writeUpdate(targetCompareRowCopy, deltasCopy);
                            ts2 = System.currentTimeMillis() - ts2;
                            log.debug("Took " + ts2 + " milliseconds to run writeUpdate for Unitypes.");
                        } else {
                            long ts3 = System.currentTimeMillis();
                            diffWriter.writeUpdate(targetCompareRow, deltas);
                            ts3 = System.currentTimeMillis() - ts3;
                            log.debug("Took " + ts3 + " milliseconds to run writeUpdate.");
                        }
                        if (!diffWriter.isError()) {
                            tableReport.countDifferentRow();
                        } else {
                            tableReport.countErrorRows();
                            diffWriter.setThrowable(tableReport.getThrowable());
                        }
                    }
                    long nextRow = System.currentTimeMillis();
                    sourceRow = sourceCursor.next();
                    targetRow = targetCursor.next();
                    nextRow = System.currentTimeMillis() - nextRow;
                    if (nextRow > 2) {
                        log.debug("Took " + nextRow + " milliseconds to get the next source and target rows.");
                    }
                } else if (comparePk < 0) {
                    if (isUsingUnitypes) {
                        long ts4 = System.currentTimeMillis();
                        diffWriter.writeInsert(sourceCompareRowCopy);
                        ts4 = System.currentTimeMillis() - ts4;
                        log.debug("Took " + ts4 + " milliseconds to run writeInsert with Unitypes.");
                    } else {
                        long ts5 = System.currentTimeMillis();
                        diffWriter.writeInsert(sourceCompareRow);
                        ts5 = System.currentTimeMillis() - ts5;
                        log.debug("Took " + ts5 + " milliseconds to run writeInsert.");
                    }
                    if (!diffWriter.isError()) {
                        tableReport.countMissingRow();
                    } else {
                        tableReport.countErrorRows();
                        diffWriter.setThrowable(tableReport.getThrowable());
                    }
                    sourceRow = sourceCursor.next();
                } else {
                    if (isUsingUnitypes) {
                        long ts6 = System.currentTimeMillis();
                        diffWriter.writeDelete(targetCompareRowCopy);
                        ts6 = System.currentTimeMillis() - ts6;
                        log.debug("Took " + ts6 + " milliseconds to run writeDelete with Unitypes.");
                    } else {
                        long ts7 = System.currentTimeMillis();
                        diffWriter.writeDelete(targetCompareRow);
                        ts7 = System.currentTimeMillis() - ts7;
                        log.debug("Took " + ts7 + " milliseconds to run writeDelete.");
                    }
                    if (!diffWriter.isError()) {
                        tableReport.countExtraRow();
                    } else {
                        tableReport.countErrorRows();
                        diffWriter.setThrowable(tableReport.getThrowable());
                    }
                    targetRow = targetCursor.next();
                }
                long tableReportTime = System.currentTimeMillis();
                tableReport.setSourceRows(sourceCursor.count);
                tableReport.setTargetRows(targetCursor.count);
                tableReportTime = System.currentTimeMillis() - tableReportTime;
                if (tableReportTime > 2) {
                    log.debug("Took " + tableReportTime + " milliseconds to update the table report.");
                }
            }
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
            if (sourceCursor != null) {
                sourceCursor.close();
            }
            if (targetCursor != null) {
                targetCursor.close();
            }
        }
        return tableReport;
    }

    protected void modifyRowForUnitypes(Row originalRow, Map<String, Object> records, Boolean isInserting) {
        for (String key : records.keySet()) {
            if (isInserting) {
                originalRow.put(key, records.get(key));
            } else {
                originalRow.remove(key);
            }
        }
    }

    protected int comparePk(DbCompareTables tables, DbCompareRow sourceCompareRow, DbCompareRow targetCompareRow) {
        if (sourceCompareRow != null && targetCompareRow == null) {
            return -1;
        }
        if (sourceCompareRow == null && targetCompareRow != null) {
            return 1;
        }
        if (sourceCompareRow == null) {
            return 0;
        }
        return sourceCompareRow.comparePks(tables, targetCompareRow);
    }

    protected String getSourceComparisonSQL(DbCompareTables tables, IDatabasePlatform platform) {
        String whereClause = config.getSourceWhereClause(tables.getSourceTable().getName());
        String sql = getComparisonSQL(tables.getSourceTable(),
                tables.getSourceTable().getPrimaryKeyColumns(), platform, whereClause, true);
        log.info("Source comparison SQL: {}", sql);
        return sql;
    }

    protected String getTargetComparisonSQL(DbCompareTables tables, IDatabasePlatform platform) {
        List<Column> mappedPkColumns = new ArrayList<Column>();
        for (Column sourcePkColumn : tables.getSourceTable().getPrimaryKeyColumns()) {
            Column targetColumn = tables.getColumnMapping().get(sourcePkColumn);
            if (targetColumn == null) {
                log.warn("No target column mapped to source PK column {}.  Dbcompare may be inaccurate for this table.", sourcePkColumn);
            } else {
                mappedPkColumns.add(targetColumn);
            }
        }
        String whereClause = config.getTargetWhereClause(tables.getTargetTable().getName());
        String sql = getComparisonSQL(tables.getTargetTable(), tables.getTargetTable().getPrimaryKeyColumns(), platform,
                whereClause, false);
        log.info("Target comparison SQL: {}", sql);
        return sql;
    }

    protected String getComparisonSQL(Table table, Column[] sortByColumns, IDatabasePlatform platform,
            String whereClause, boolean isSource) {
        DmlStatement statement = platform.createDmlStatement(DmlType.SELECT, table.getCatalog(), table.getSchema(),
                table.getName(), null, table.getColumns(), null, null);
        StringBuilder sql = new StringBuilder(statement.getSql());
        String sybaseUnitypeConversions = statement.getSql();
        boolean isUsingUnitypes = false;
        if (platform.getName().equals(DatabaseNamesConstants.ASE)) {
            if (isSource) {
                ISymmetricDialect symmetricDialect = sourceEngine.getSymmetricDialect();
                isUsingUnitypes = symmetricDialect.getParameterService()
                        .is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC);
            } else {
                ISymmetricDialect symmetricDialect = targetEngine.getSymmetricDialect();
                isUsingUnitypes = symmetricDialect.getParameterService()
                        .is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC);
            }
            if (isUsingUnitypes) {
                for (Column column : statement.getColumns()) {
                    if (isUniType(column.getJdbcTypeName()) && !column.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                        if (sybaseUnitypeConversions.contains(" " + column.getName() + ",")) {
                            sybaseUnitypeConversions = sybaseUnitypeConversions.replace(" " + column.getName() + ",", "case when " + column.getName()
                                    + " is null then null else '\"' +\n"
                                    + " bintostr(convert(varbinary(16384)," + column.getName() + ")) + '\"' end as _uni_" + column.getName() + " ,");
                        } else {
                            sybaseUnitypeConversions = sybaseUnitypeConversions.replace(" " + column.getName() + " from", "case when " + column.getName()
                                    + " is null then null else '\"' +\n"
                                    + " bintostr(convert(varbinary(16384)," + column.getName() + ")) + '\"' end as _uni_" + column.getName() + " from");
                        }
                    }
                }
            }
        }
        StringBuilder finalSql = null;
        if (isUsingUnitypes) {
            finalSql = new StringBuilder(sybaseUnitypeConversions.toString());
        } else {
            finalSql = new StringBuilder(sql.toString());
        }
        finalSql.setLength(finalSql.length() - "where ".length()); // remove the trailing where so we can insert a table alias.
        finalSql.append(" t where "); // main table alias.
        finalSql.append(whereClause).append(" ");
        finalSql.append(buildOrderBy(table, sortByColumns, platform, isSource));
        return finalSql.toString();
    }

    protected String buildOrderBy(Table table, Column[] sortByColumns, IDatabasePlatform platform, boolean isSource) {
        DatabaseInfo databaseInfo = platform.getDatabaseInfo();
        String quote = databaseInfo.getDelimiterToken() == null ? "" : databaseInfo.getDelimiterToken();
        StringBuilder orderByClause = new StringBuilder("ORDER BY ");
        for (Column sortByColumn : sortByColumns) {
            String columnName = sortByColumn.getName();
            String quotedColumnName = new StringBuilder(quote).append(columnName).append(quote).toString();
            orderByClause.append(quotedColumnName);
            String suffix;
            if (isSource) {
                suffix = config.getSourceOrderBySuffix(table.getName(), columnName);
            } else {
                suffix = config.getTargetOrderBySuffix(table.getName(), columnName);
            }
            if (StringUtils.isNotBlank(suffix)) {
                orderByClause.append(" ").append(suffix);
            }
            orderByClause.append(",");
        }
        orderByClause.setLength(orderByClause.length() - 1);
        return orderByClause.toString();
    }

    protected List<DbCompareTables> getTablesToCompare() {
        List<DbCompareTables> tablesToCompare;
        if (config.isUseSymmetricConfig()) {
            tablesToCompare = loadTablesFromConfig();
        } else {
            tablesToCompare = loadTablesFromArguments();
        }
        return tablesToCompare;
    }

    protected List<DbCompareTables> loadTablesFromConfig() {
        String sourceNodeGroupId = sourceEngine.getNodeService().findIdentity(true, true).getNodeGroupId();
        String targetNodeGroupId = targetEngine.getNodeService().findIdentity(true, true).getNodeGroupId();
        List<TriggerRouter> triggerRouters = sourceEngine.getTriggerRouterService()
                .getTriggerRoutersForSourceAndTargetNodes(sourceNodeGroupId, targetNodeGroupId);
        Set<String> configTables = TableConstants.getTables(sourceEngine.getTablePrefix());
        List<String> tableNames = new ArrayList<String>();
        for (TriggerRouter triggerRouter : triggerRouters) {
            String tableName = triggerRouter.getTrigger().getFullyQualifiedSourceTableName();
            if (!tableNames.contains(tableName) && !configTables.contains(tableName)
                    && ((!CollectionUtils.isEmpty(config.getSourceTableNames())
                            && config.getSourceTableNames().contains(tableName))
                            || CollectionUtils.isEmpty(config.getSourceTableNames()))) {
                tableNames.add(tableName);
            }
        }
        return loadTables(tableNames, config.getTargetTableNames());
    }

    protected List<DbCompareTables> loadTables(List<String> tableNames, List<String> targetTableNames) {
        List<DbCompareTables> compareTables = new ArrayList<DbCompareTables>(1);
        List<String> filteredTablesNames = filterTables(tableNames);
        if (!CollectionUtils.isEmpty(targetTableNames) && filteredTablesNames.size() != targetTableNames.size()) {
            StringBuilder sb = new StringBuilder("Source and target table lists do not match.");
            sb.append(System.lineSeparator()).append("SourceTable:TargetTable").append(System.lineSeparator());
            Iterator<String> ftn = filteredTablesNames.iterator();
            Iterator<String> ttn = targetTableNames.iterator();
            while (true) {
                if (ftn.hasNext() || ttn.hasNext()) {
                    String filteredTableName = ftn.hasNext() ? ftn.next() : "<undefined>";
                    String targetTableName = ttn.hasNext() ? ttn.next() : "<undefined>";
                    sb.append(filteredTableName + ":" + targetTableName).append(System.lineSeparator());
                } else {
                    break;
                }
            }
            log.error(sb.toString());
            throw new SymmetricException("Source table names must be the same length as the list of target "
                    + "table names.  Check your arguments. source table names = "
                    + filteredTablesNames + " target table names = " + targetTableNames);
        }
        for (int i = 0; i < filteredTablesNames.size(); i++) {
            String tableName = filteredTablesNames.get(i);
            Table sourceTable = null;
            Map<String, String> tableNameParts = sourceEngine.getTargetDialect().getTargetPlatform().parseQualifiedTableName(tableName);
            if (tableNameParts.size() == 1) {
                sourceTable = (Table) sourceEngine.getTargetDialect().getTargetPlatform().getRelationFromCache(tableName, true);
            } else {
                sourceTable = (Table) sourceEngine.getTargetDialect().getTargetPlatform().getRelationFromCache(tableNameParts.get(TABLE_NAME_PART_CATALOG),
                        tableNameParts.get(TABLE_NAME_PART_SCHEMA),
                        tableNameParts.get(TABLE_NAME_PART_TABLE), true);
                if (sourceTable == null) {
                    sourceTable = (Table) sourceEngine.getTargetDialect().getTargetPlatform().getRelationFromCache(tableNameParts.get(TABLE_NAME_PART_SCHEMA),
                            tableNameParts.get(TABLE_NAME_PART_CATALOG),
                            tableNameParts.get(TABLE_NAME_PART_TABLE), true);
                }
            }
            if (sourceTable == null) {
                log.warn("No source table found for name {}", tableName);
                continue;
            }
            if (config.isUseSymmetricConfig()) {
                String catalog = null;
                String schema = null;
                if (!Strings.CS.equals(sourceEngine.getTargetDialect().getTargetPlatform().getDefaultCatalog(), sourceTable.getCatalog())) {
                    catalog = sourceTable.getCatalog();
                }
                if (!Strings.CS.equals(sourceEngine.getTargetDialect().getTargetPlatform().getDefaultSchema(), sourceTable.getSchema())) {
                    schema = sourceTable.getSchema();
                }
                TriggerHistory hist = sourceEngine.getTriggerRouterService().findTriggerHistory(catalog, schema,
                        sourceTable.getName());
                if (hist != null) {
                    sourceTable = sourceTable.copyAndFilterColumns(hist.getParsedColumnNames(), hist.getParsedPkColumnNames(), true, false);
                } else {
                    log.warn("No trigger history found for {}", sourceTable.getFullyQualifiedName());
                }
            }
            // could put a check here before copying table?
            Table sourceTableCopy = sourceTable.copy();
            DbCompareTables tables = new DbCompareTables(sourceTableCopy, null);
            String targetTableName = tableName;
            if (!CollectionUtils.isEmpty(targetTableNames)) {
                targetTableName = targetTableNames.get(i);
            }
            Table targetTable = loadTargetTable(tables, targetTableName);
            if (targetTable == null) {
                log.warn("No target table found for name {}", tableName);
                continue;
            }
            tables.applyColumnMappings();
            tables.filterExcludedColumns(config);
            if (tables.getSourceTable().getPrimaryKeyColumnCount() == 0) {
                log.warn("Source table {} doesn't have any primary key columns and will not be considered in the comparison.", sourceTable);
                continue;
            }
            boolean success = mapPrimaryKey(tables);
            if (success) {
                compareTables.add(tables);
            }
        }
        return compareTables;
    }

    protected boolean mapPrimaryKey(DbCompareTables tables) {
        List<Column> mappedPkColumns = new ArrayList<Column>();
        for (Column sourcePkColumn : tables.getSourceTable().getPrimaryKeyColumns()) {
            Column targetColumn = tables.getColumnMapping().get(sourcePkColumn);
            if (targetColumn == null) {
                log.warn("No target column mapped to source PK column {}. Unable to perform dbcompare on table {}",
                        sourcePkColumn, tables.getSourceTable());
                return false;
            } else {
                mappedPkColumns.add(targetColumn);
            }
        }
        Column[] targetColumns = tables.getTargetTable().getColumns();
        for (Column column : targetColumns) {
            column.setPrimaryKey(false);
        }
        List<Column> reorderedColumns = new ArrayList<Column>();
        for (Column mappedPkColumn : mappedPkColumns) {
            mappedPkColumn.setPrimaryKey(true);
            reorderedColumns.add(mappedPkColumn);
        }
        for (Column column : targetColumns) {
            if (!reorderedColumns.contains(column)) {
                reorderedColumns.add(column);
            }
        }
        tables.getTargetTable().removeAllColumns();
        tables.getTargetTable().addColumns(reorderedColumns);
        return true;
    }

    protected Table loadTargetTable(DbCompareTables tables, String targetTableName) {
        Table targetTable = null;
        if (config.isUseSymmetricConfig()) {
            TransformTableNodeGroupLink transform = getTransformFor(tables.getSourceTable());
            if (transform != null) {
                targetTable = loadTargetTableUsingTransform(transform);
                tables.setTransform(transform);
            } else {
                String catalog = targetEngine.getTargetDialect().getTargetPlatform().getDefaultCatalog();
                String schema = targetEngine.getTargetDialect().getTargetPlatform().getDefaultSchema();
                String tableName = tables.getSourceTable().getName();
                TriggerRouter triggerRouter = getTriggerRouterFor(tables.getSourceTable());
                if (triggerRouter != null) {
                    catalog = triggerRouter.getTargetCatalog(catalog, null);
                    schema = triggerRouter.getTargetSchema(schema, null);
                    if (StringUtils.isNotEmpty(triggerRouter.getTargetTable(null))) {
                        tableName = triggerRouter.getTargetTable(null);
                    }
                }
                targetTable = (Table) targetEngine.getTargetDialect().getTargetPlatform().getRelationFromCache(catalog, schema, tableName, true);
            }
        } else {
            Map<String, String> tableNameParts = targetEngine.getTargetDialect().getTargetPlatform().parseQualifiedTableName(targetTableName);
            if (tableNameParts.size() == 1) {
                targetTable = (Table) targetEngine.getTargetDialect().getTargetPlatform().getRelationFromCache(targetTableName, true);
            } else {
                targetTable = (Table) targetEngine.getTargetDialect().getTargetPlatform().getRelationFromCache(tableNameParts.get(TABLE_NAME_PART_CATALOG),
                        tableNameParts
                                .get("schema"),
                        tableNameParts
                                .get("table"), true);
                if (targetTable == null) {
                    targetTable = (Table) targetEngine.getTargetDialect().getTargetPlatform().getRelationFromCache(tableNameParts.get(TABLE_NAME_PART_SCHEMA),
                            tableNameParts
                                    .get(
                                            "catalog"),
                            tableNameParts.get(TABLE_NAME_PART_TABLE), true);
                }
            }
        }
        // could put a check here before copying table
        long targetTableCopyTime = System.currentTimeMillis();
        Table targetTableCopy = targetTable.copy();
        targetTableCopyTime = System.currentTimeMillis() - targetTableCopyTime;
        log.debug("Took " + targetTableCopyTime + " milliseconds to copy target table.");
        tables.setTargetTable(targetTableCopy);
        return targetTableCopy;
    }

    protected TriggerRouter getTriggerRouterFor(Table sourceTable) {
        // TODO get routers.
        Set<TriggerRouter> triggerRouters = sourceEngine.getTriggerRouterService().getTriggerRouterForTableForCurrentNode(
                sourceTable.getCatalog(), sourceTable.getSchema(), sourceTable.getName(), false);
        for (TriggerRouter triggerRouter : triggerRouters) {
            String routerTargetNodeGroupId = triggerRouter.getRouter().getNodeGroupLink().getTargetNodeGroupId();
            String compareTargetNodeGroupId = targetEngine.getNodeService().getCachedIdentity().getNodeGroupId();
            if (Strings.CS.equals(compareTargetNodeGroupId, routerTargetNodeGroupId)) {
                return triggerRouter;
            }
        }
        return null;
    }

    protected TransformTableNodeGroupLink getTransformFor(Table sourceTable) {
        String sourceNodeGroupId = null;
        if (sourceEngine.getNodeService().findIdentity() != null) {
            sourceNodeGroupId = sourceEngine.getNodeService().findIdentity().getNodeGroupId();
        }
        String targetNodeGroupId = null;
        if (targetEngine.getNodeService().findIdentity() != null) {
            targetNodeGroupId = targetEngine.getNodeService().findIdentity().getNodeGroupId();
        }
        List<TransformTableNodeGroupLink> transforms = sourceEngine.getTransformService().findTransformsFor(
                sourceNodeGroupId, targetNodeGroupId, sourceTable.getName(),
                sourceTable.getSchema(), sourceTable.getCatalog());
        if (!CollectionUtils.isEmpty(transforms)) {
            TransformTableNodeGroupLink transform = transforms.get(0); // Only can operate on a single table transform for now.
            if (!StringUtils.isEmpty(transform.getFullyQualifiedTargetTableName())) {
                return transform;
            }
        }
        return null;
    }

    protected Table loadTargetTableUsingTransform(TransformTableNodeGroupLink transform) {
        return (Table) targetEngine.getTargetDialect().getTargetPlatform().getRelationFromCache(transform.getTargetCatalogName(),
                transform.getTargetSchemaName(), transform.getTargetTableName(), true);
    }

    protected Table cloneTable(Table table) {
        return table.copy();
    }

    protected List<DbCompareTables> loadTablesFromArguments() {
        if (CollectionUtils.isEmpty(config.getSourceTableNames())) {
            throw new RuntimeException("sourceTableNames not provided, sourceTableNames must be provided "
                    + "when not comparing using SymmetricDS config.");
        }
        return loadTables(config.getSourceTableNames(), config.getTargetTableNames());
    }

    protected List<String> filterTables(List<String> tables) {
        List<String> filteredTables = new ArrayList<String>(tables.size());
        filteredTables.addAll(tables);
        if (!CollectionUtils.isEmpty(config.getExcludedTableNames())) {
            List<String> excludedTables = new ArrayList<String>(filteredTables);
            for (String excludedTableName : config.getExcludedTableNames()) {
                for (String tableName : filteredTables) {
                    if (compareTableNames(tableName, excludedTableName)) {
                        excludedTables.remove(tableName);
                    }
                }
            }
            return excludedTables;
        }
        return filteredTables;
    }

    protected boolean compareTableNames(String sourceTableName, String targetTableName) {
        sourceTableName = sourceTableName.trim();
        targetTableName = targetTableName.trim();
        if (Strings.CI.equals(sourceTableName, targetTableName)) {
            return true;
        } else {
            Map<String, String> sourceTableNameParts = sourceEngine.getTargetDialect().getTargetPlatform().parseQualifiedTableName(sourceTableName);
            Map<String, String> targetTableNameParts = targetEngine.getTargetDialect().getTargetPlatform().parseQualifiedTableName(targetTableName);
            return Strings.CI.equals(sourceTableNameParts.get("table"), targetTableNameParts.get("table"));
        }
    }

    public boolean isUniType(String type) {
        return type.equalsIgnoreCase("UNITEXT") || type.equalsIgnoreCase("UNICHAR") || type.equalsIgnoreCase("UNIVARCHAR");
    }

    static class CountingSqlReadCursor implements ISqlReadCursor<Row>, Closeable {
        ISqlReadCursor<Row> wrapped;
        int count = 0;

        CountingSqlReadCursor(ISqlReadCursor<Row> wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public Row next() {
            Row row = wrapped.next();
            if (row != null) {
                count++;
            }
            return row;
        }

        @Override
        public void close() {
            wrapped.close();
        }
    }

    public DbCompareConfig getConfig() {
        return config;
    }
}
