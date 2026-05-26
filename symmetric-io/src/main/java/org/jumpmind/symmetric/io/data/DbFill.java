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
package org.jumpmind.symmetric.io.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.db.util.TableRow;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate data for populating databases.
 */
public class DbFill {
    final Logger log = LoggerFactory.getLogger(getClass());
    private String catalog;
    private String schema;
    private IDatabasePlatform platform;
    private boolean ignoreMissingTables;
    private boolean cascading = false;
    private boolean cascadingSelect = false;
    private boolean truncate = false;
    private String ignore[] = null;
    private String prefixed[] = null;
    private int inputLength = 1;
    private int repeat = 1;
    private int maxRowsCommit = 1;
    private int commitDelay = 0;
    private int percentRollback = 0;
    private SecureRandom rand = null;
    private int interval = 0;
    private boolean debug = false;
    private boolean verbose = false;
    private boolean continueOnError = false;
    private boolean print = false;
    private boolean useRandomCount = false;
    private int maxByteSize = 32;
    private int maxTextSize = 32;
    private String textColumnExpression;
    // Weights given to insert, update, and delete commands when
    // randomly selecting a command for any given table.
    private DmlWeight dmlWeight = new DmlWeight(1, 0, 0);
    // All tables from database [Table name -> Table]
    private Map<String, Table> allDbTablesCache = null;
    // Current row being worked on for all tables [Table name -> [column name -> value]]
    private Map<String, Map<String, Object>> currentRowValues = new HashMap<String, Map<String, Object>>();
    // Foreign keys by their local column name [Table name.column name -> ForeignKeyReference].
    // Used to look up the current row value of foreign keys for an insert.
    private Map<String, List<ForeignKeyReference>> foreignKeyReferences = new TreeMap<String, List<ForeignKeyReference>>();
    // For each table, the ordered list of tables it depends on (for inserts cascading)
    private Map<Table, List<Table>> foreignTables = new HashMap<Table, List<Table>>();
    // For each table, the ordered list of tables that depend on it (for deletes cascading)
    private Map<Table, List<Table>> foreignTablesReversed = new HashMap<Table, List<Table>>();
    // For cascading-select, when the same local column has multiple foreign dependencies, they must
    // share a common value [foreign table name.column name -> [value]]
    private Map<String, List<Object>> commonDependencyValues = new TreeMap<String, List<Object>>();
    // For cascading-select, quick check by table if it shares a common value with other tables
    private Set<Table> commonDependencyTables = new HashSet<Table>();
    // For cascading-select, to ensure composite keys contain proper values for all columns in the key
    private Map<Table, HashSet<ForeignKey>> compositeForeignKeys = new HashMap<Table, HashSet<ForeignKey>>();
    // Minimum column size for all columns with foreign key references
    // For example, if pid varchar(10) references id varchar(5), then both id and pid will return min column size as 5
    private Map<String, Integer> minColumnSizes = new HashMap<String, Integer>();
    // Cache of SET column values: key = "tableName.columnName", value = valid SET values
    private Map<String, String[]> setColumnValues = new HashMap<>();
    // -1 for no limit
    private static final int RANDOM_SELECT_SIZE = 100;
    // Must remain 0-2 to choose randomly.
    public final static int INSERT = 0;
    public final static int UPDATE = 1;
    public final static int DELETE = 2;

    public DbFill() {
    }

    public DbFill(IDatabasePlatform platform) {
        this.platform = platform;
    }

    public void fillTables(String... tableNames) {
        fillTables(tableNames, null);
    }

    public void fillTables(String[] tableNames, Map<String, DmlWeight> tableProperties) {
        List<Table> tablesToFill = new ArrayList<Table>();
        if (verbose) {
            log.info("Looking up table definitions from database");
        }
        if (tableNames.length == 0) {
            // If no tableNames are provided look up all tables.
            Map<String, Table> allTables = getAllDbTables();
            if (ignore != null) {
                // Ignore any tables matching an ignorePrefix. (e.g., "sym_")
                table_loop: for (Table table : allTables.values()) {
                    for (String ignoreName : ignore) {
                        if (table.getName().startsWith(ignoreName)) {
                            if (verbose) {
                                log.info("Ignore table " + table.getName());
                            }
                            continue table_loop;
                        }
                    }
                    if (prefixed != null) {
                        for (String prefixedName : prefixed) {
                            if (!table.getName().startsWith(prefixedName)) {
                                if (verbose) {
                                    log.info("Non prefixed table (" + prefixedName + ")" + table.getName());
                                }
                                continue table_loop;
                            }
                        }
                    }
                    tablesToFill.add(table);
                }
            }
        } else {
            for (String tableName : tableNames) {
                Table table = (Table) platform.readRelationFromDatabase(getCatalogToUse(), getSchemaToUse(),
                        tableName);
                if (table != null) {
                    table = filterColumns(table);
                    tablesToFill.add(table);
                } else if (!ignoreMissingTables) {
                    throw new RuntimeException("Cannot find table " + tableName + " in catalog "
                            + getCatalogToUse() + " and schema " + getSchemaToUse());
                }
            }
        }
        if (cascading || cascadingSelect) {
            log.info("Resolving foreign key references");
            buildForeignTables(tablesToFill);
        } else {
            log.info("Checking for foreign key constraints");
            List<Table> missingTables = getForeignKeyTables(tablesToFill, new HashSet<Table>());
            if (missingTables.size() > 0) {
                List<String> missingTableNames = new ArrayList<String>();
                for (Table missingTable : missingTables) {
                    missingTableNames.add(missingTable.getName());
                }
                log.warn("Foreign tables are missing from the list (see --select or --cascade options): " +
                        missingTableNames);
            }
        }
        log.info("TABLES TO FILL (" + tablesToFill.size() + "): " + toString(tablesToFill));
        List<Table> orderedTables = Database.sortByForeignKeys(
                new ArrayList<Relation>(tablesToFill),
                new HashMap<String, Relation>(getAllDbTables()),
                null, null)
                .stream().map(r -> (Table) r).collect(Collectors.toList());
        orderedTables = removeSymTables(orderedTables);
        List<Table> dependencyTables = new ArrayList<Table>();
        for (Table table : orderedTables) {
            if (!tablesToFill.contains(table)) {
                dependencyTables.add(table);
            }
        }
        log.info("DEPENDENCIES (" + dependencyTables.size() + " tables): " + toString(dependencyTables));
        buildForeignKeyReferences(orderedTables);
        buildDependentColumnValues(orderedTables);
        buildMinColumnSizes(orderedTables);
        fillTables(tablesToFill, orderedTables, tableProperties);
    }

    protected List<Table> removeSymTables(List<Table> tables) {
        List<Table> filteredTables = new ArrayList<Table>();
        for (Table table : tables) {
            if (!table.getNameLowerCase().startsWith("sym_")) {
                filteredTables.add(table);
            }
        }
        return filteredTables;
    }

    protected String toString(List<Table> tables) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Table table : tables) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(", ");
            }
            sb.append(table.getName());
        }
        return sb.toString();
    }

    protected void buildForeignTables(List<Table> tables) {
        for (Table table : tables) {
            ArrayList<Table> tableList = new ArrayList<Table>();
            tableList.add(table);
            List<Table> list = getForeignKeyTables(tableList, new HashSet<Table>());
            foreignTables.put(table, list);
            List<Table> reversedList = getForeignKeyTablesReversed(tableList, new HashSet<Table>());
            foreignTablesReversed.put(table, reversedList);
        }
    }

    protected void buildForeignKeyReferences(List<Table> tables) {
        for (Table table : tables) {
            for (ForeignKey fk : table.getForeignKeys()) {
                for (Reference ref : fk.getReferences()) {
                    String key = table.getQualifiedName() + "." + ref.getLocalColumnName();
                    List<ForeignKeyReference> fkrs = foreignKeyReferences.get(key);
                    if (fkrs == null) {
                        fkrs = new ArrayList<ForeignKeyReference>();
                        foreignKeyReferences.put(key, fkrs);
                    }
                    fkrs.add(new ForeignKeyReference(fk, ref));
                }
                if (fk.getReferences().length > 1) {
                    if (compositeForeignKeys.get(table) == null) {
                        compositeForeignKeys.put(table, new HashSet<ForeignKey>());
                    }
                    compositeForeignKeys.get(table).add(fk);
                }
            }
        }
    }

    protected void buildDependentColumnValues(List<Table> tables) {
        for (Table table : tables) {
            Map<String, List<ForeignKeyReference>> columnReferences = new HashMap<String, List<ForeignKeyReference>>();
            for (ForeignKey fk : table.getForeignKeys()) {
                for (Reference ref : fk.getReferences()) {
                    List<ForeignKeyReference> references = columnReferences.get(ref.getLocalColumnName());
                    if (references == null) {
                        references = new ArrayList<ForeignKeyReference>();
                        columnReferences.put(ref.getLocalColumnName(), references);
                    }
                    references.add(new ForeignKeyReference(fk, ref));
                }
            }
            for (String columnName : columnReferences.keySet()) {
                List<ForeignKeyReference> references = columnReferences.get(columnName);
                if (references.size() > 1) {
                    List<Object> commonValue = new ArrayList<Object>();
                    StringBuilder sb = null;
                    for (ForeignKeyReference fkr : references) {
                        ForeignKey fk = fkr.getForeignKey();
                        String key = table.getFullyQualifiedName() + "." + fkr.getReference().getForeignColumnName();
                        commonDependencyValues.put(key, commonValue);
                        commonDependencyTables.add(getDbTable(fk.getForeignTableCatalog(), fk.getForeignTableSchema(), fk.getForeignTableName()));
                        if (verbose) {
                            sb = (sb == null) ? new StringBuilder() : sb.append(", ");
                            sb.append(fkr.getReference().getLocalColumnName() + " -> " + fkr.getForeignKey().getForeignTableName() + "." + fkr.getReference()
                                    .getForeignColumnName());
                        }
                    }
                    if (verbose) {
                        log.info("Common dependency for table {}: {}", table.getName(), sb);
                    }
                }
            }
        }
    }

    protected void buildMinColumnSizes(List<Table> tables) {
        for (Table table : tables) {
            Set<String> columnNames = new HashSet<String>();
            for (ForeignKey fk : table.getForeignKeys()) {
                for (Reference ref : fk.getReferences()) {
                    columnNames.add(ref.getLocalColumnName());
                }
            }
            for (String columnName : columnNames) {
                Column column = table.findColumn(columnName);
                buildMinColumnSize(table, column, new HashSet<String>(), null);
            }
        }
    }

    /**
     * For the table and column passed in, traverse all the foreign key references to find the smallest size defined for the column.
     * 
     */
    protected int buildMinColumnSize(Table table, Column column, Set<String> relatedTableColumns, Integer minSize) {
        if (relatedTableColumns.add(table.getQualifiedName() + "." + column.getName())) {
            Integer size = column.getSizeAsInt();
            if (minSize != null && minSize < size) {
                size = minSize;
            }
            for (ForeignKey fk : table.getForeignKeys()) {
                for (Reference ref : fk.getReferences()) {
                    if (ref.getLocalColumnName().equals(column.getName())) {
                        Table foreignTable = getDbTable(fk.getForeignTableCatalog(), fk.getForeignTableSchema(),
                                fk.getForeignTableName());
                        Column foreignColumn = foreignTable.findColumn(ref.getForeignColumnName());
                        size = buildMinColumnSize(foreignTable, foreignColumn, relatedTableColumns, size);
                    }
                }
            }
            if (minSize == null) {
                for (String relatedTableColumn : relatedTableColumns) {
                    minColumnSizes.put(relatedTableColumn, size);
                }
            }
            minSize = size;
        }
        return minSize;
    }

    /**
     * Identify the tables not included in the given list that the initial tables have FK relationships to.
     *
     * @param tables
     *
     * @return The table array argument and the tables that the initial table array argument depend on.
     */
    protected List<Table> getForeignKeyTables(List<Table> tables, Set<Table> visited) {
        Set<Table> fkDepSet = new HashSet<Table>(tables);
        List<Table> fkDepList = new ArrayList<Table>();
        for (Table table : tables) {
            if (table != null && visited.add(table)) {
                for (ForeignKey fk : table.getForeignKeys()) {
                    Table foreignTable = getDbTable(fk.getForeignTableCatalog(), fk.getForeignTableSchema(),
                            fk.getForeignTableName());
                    if (fkDepSet.add(foreignTable)) {
                        fkDepList.add(foreignTable);
                    }
                }
            }
        }
        if (fkDepList.size() > 0) {
            fkDepList.addAll(getForeignKeyTables(fkDepList, visited));
        }
        Collections.reverse(fkDepList);
        return fkDepList;
    }

    protected List<Table> getForeignKeyTablesReversed(List<Table> tables, Set<Table> visited) {
        List<Table> fkDepList = new ArrayList<Table>();
        for (Table table : tables) {
            if (table != null && visited.add(table)) {
                String parentTableName = table.getName();
                for (Table child : getAllDbTables().values()) {
                    for (ForeignKey fk : child.getForeignKeys()) {
                        if (parentTableName.equalsIgnoreCase(fk.getForeignTableName())) {
                            fkDepList.add(child);
                            break;
                        }
                    }
                }
            }
        }
        if (fkDepList.size() > 0) {
            fkDepList.addAll(getForeignKeyTablesReversed(fkDepList, visited));
        }
        return fkDepList;
    }

    /**
     * Perform an INSERT, UPDATE, or DELETE on every table in tables.
     *
     * @param tables
     *            Array of tables to perform statement on. Tables must be in insert order.
     * @param tableProperties
     *            Map indicating IUD weights for each table name provided in the properties file.
     */
    private void fillTables(List<Table> tablesToFill, List<Table> orderedTables, Map<String, DmlWeight> tableProperties) {
        if (truncate) {
            ListIterator<Table> iterator = tablesToFill.listIterator(tablesToFill.size());
            while (iterator.hasPrevious()) {
                Table table = iterator.previous();
                truncateTable(table);
            }
        }
        ISqlTransaction tran = platform.getSqlTemplate().startSqlTransaction();
        try {
            DatabaseInfo dbInfo = platform.getDatabaseInfo();
            String quote = dbInfo.getDelimiterToken();
            String catalogSeparator = dbInfo.getCatalogSeparator();
            String schemaSeparator = dbInfo.getSchemaSeparator();
            int rowsInTransaction = 0;
            int rowsTotal = 0;
            for (int x = 0; x < repeat; x++) {
                int numRowsToGenerate = inputLength;
                int numRowsToCommit = maxRowsCommit;
                if (useRandomCount) {
                    numRowsToGenerate = getRand().nextInt(inputLength);
                    numRowsToGenerate = numRowsToGenerate > 0 ? numRowsToGenerate : 1;
                    numRowsToCommit = getRand().nextInt(maxRowsCommit);
                    numRowsToCommit = numRowsToCommit > 0 ? numRowsToCommit : 1;
                }
                for (int i = 0; i < numRowsToGenerate; i++) {
                    for (Table table : orderedTables) {
                        if (table.hasAutoIncrementColumn()) {
                            log.debug("Turning on identity insert for table " + table.getName());
                            tran.allowInsertIntoAutoIncrementColumns(true, table, quote, catalogSeparator, schemaSeparator);
                        }
                        int dmlType = INSERT;
                        if (tableProperties != null && tableProperties.containsKey(table.getName())) {
                            dmlType = randomIUD(tableProperties.get(table.getName()));
                        } else if (dmlWeight != null) {
                            dmlType = randomIUD(dmlWeight);
                        }
                        if (cascadingSelect && dmlType == INSERT && !tablesToFill.contains(table)) {
                            if (currentRowValues.get(table.getName()) == null) {
                                selectRandomRecord(tran, table);
                            }
                            continue;
                        }
                        switch (dmlType) {
                            case INSERT:
                                if (verbose) {
                                    log.info("Inserting into table " + table.getName());
                                }
                                insertRandomRecord(tran, table);
                                break;
                            case UPDATE:
                                if (verbose) {
                                    log.info("Updating record in table " + table.getName());
                                }
                                updateRandomRecord(tran, table);
                                break;
                            case DELETE:
                                if (verbose) {
                                    log.info("Deleting record in table " + table.getName());
                                }
                                deleteRandomRecord(tran, table);
                                selectRandomRecord(tran, table);
                                break;
                        }
                        if (++rowsInTransaction >= numRowsToCommit) {
                            if (commitDelay > 0) {
                                AppUtils.sleep(commitDelay);
                            }
                            if (percentRollback > 0 && getRand().nextInt(100) <= percentRollback) {
                                log.info("Rollback " + rowsInTransaction + " rows");
                                tran.rollback();
                            } else {
                                rowsTotal += rowsInTransaction;
                                log.info("Commit " + rowsInTransaction + " rows, total " + rowsTotal + " rows");
                                tran.commit();
                            }
                            rowsInTransaction = 0;
                            AppUtils.sleep(interval);
                        }
                        if (table.hasAutoIncrementColumn()) {
                            log.info("Turning off identity insert for table " + table.getName());
                            tran.allowInsertIntoAutoIncrementColumns(false, table, quote, catalogSeparator, schemaSeparator);
                        }
                    }
                }
                clearDependentColumnValues();
                currentRowValues.clear();
            }
            if (rowsInTransaction > 0) {
                if (commitDelay > 0) {
                    AppUtils.sleep(commitDelay);
                }
                log.info("Commit " + rowsInTransaction + " rows, total " + rowsTotal + " rows");
                tran.commit();
            }
        } finally {
            tran.close();
        }
    }

    private void truncateTable(Table table) {
        if (verbose) {
            log.info("Truncating table " + table.getFullyQualifiedName());
        }
        String options = "";
        if (platform.getName().startsWith(DatabaseNamesConstants.POSTGRESQL)) {
            options = " cascade";
        }
        platform.getSqlTemplate().update("truncate table " + table.getFullyQualifiedName() + options);
    }

    /**
     * Given a table's IUD weights a random DML statement type is chosen.
     *
     * @param iudWeight
     * @return
     */
    private int randomIUD(DmlWeight iudWeight) {
        int total = iudWeight.getInsertWeight() + iudWeight.getUpdateWeight() + iudWeight.getDeleteWeight();
        if (total == 0) {
            return INSERT;
        }
        int rVal = getRand().nextInt(total);
        if (rVal < iudWeight.getInsertWeight()) {
            return INSERT;
        } else if (rVal < iudWeight.getInsertWeight() + iudWeight.getUpdateWeight()) {
            return UPDATE;
        }
        return DELETE;
    }

    /**
     * Select a random row from the table in the connected database. Return null if there are no rows.
     *
     * @param sqlTemplate
     * @param table
     *            The table to select a row from.
     * @return A random row from the table. Null if there are no rows.
     */
    private Row selectRandomRow(ISqlTransaction tran, Table table) {
        Row row = null;
        // Select all rows and return the primary key columns.
        String sql = platform.createDmlStatement(DmlType.SELECT_ALL, table.getCatalog(), table.getSchema(), table.getName(),
                table.getPrimaryKeyColumns(), table.getColumns(), null, textColumnExpression).getSql();
        if (verbose) {
            log.info("Selecting from " + table.getName());
        }
        List<Row> rows = queryForRows(tran, sql, null, null);
        if (rows.size() != 0) {
            int rowNum = getRand().nextInt(rows.size());
            row = rows.get(rowNum);
            if (verbose) {
                log.info("Row from " + table.getName() + ": " + row);
            }
        } else {
            if (cascading) {
                insertRandomRecord(tran, table);
                return selectRandomRow(tran, table);
            } else {
                log.warn("Unable to find a row in table " + table.getName());
            }
        }
        return row;
    }

    private Row selectSpecificRow(ISqlTransaction tran, Table table, Column[] keyColumns, Object[] values) {
        Row row = null;
        DmlStatement stmt = platform.createDmlStatement(DmlType.SELECT, table.getCatalog(), table.getSchema(), table.getName(),
                keyColumns, table.getColumns(), null, textColumnExpression);
        if (verbose) {
            StringBuilder sb = null;
            for (int i = 0; i < keyColumns.length; i++) {
                sb = (sb == null) ? new StringBuilder() : sb.append(", ");
                sb.append(keyColumns[i].getName()).append("=").append(values[i]);
            }
            log.info("Selecting from {} where {}", table.getName(), sb);
        }
        List<Row> rows = queryForRows(tran, stmt.getSql(), values, stmt.getTypes());
        if (rows.size() != 0) {
            int rowNum = getRand().nextInt(rows.size());
            row = rows.get(rowNum);
        } else {
            StringBuilder sb = null;
            for (int i = 0; i < keyColumns.length; i++) {
                sb = (sb == null) ? new StringBuilder() : sb.append(", ");
                sb.append(keyColumns[i].getName()).append("=").append(values[i]);
            }
            log.warn("Unable to find row from {} where {}", table.getName(), sb);
        }
        return row;
    }

    private List<Row> queryForRows(ISqlTransaction tran, String sql, Object[] values, int[] types) {
        final List<Row> rows = new ArrayList<Row>();
        if (tran != null) {
            try {
                tran.query(sql, new ISqlRowMapper<Object>() {
                    int count = 1;

                    public Object mapRow(Row row) {
                        rows.add(row);
                        if (count++ >= RANDOM_SELECT_SIZE) {
                            throw new RuntimeException("MAX");
                        }
                        return Boolean.TRUE;
                    }
                }, values, types);
            } catch (RuntimeException e) {
                if (!e.getMessage().equals("MAX")) {
                    throw e;
                }
            }
        } else {
            platform.getSqlTemplate().query(sql, RANDOM_SELECT_SIZE, new ISqlRowMapper<Object>() {
                public Object mapRow(Row row) {
                    rows.add(row);
                    return Boolean.TRUE;
                }
            }, values, types);
        }
        return rows;
    }

    /**
     * TODO: Add updates to primary key, avoid updates to foreign keys
     */
    private void updateRandomRecord(ISqlTransaction tran, Table table) {
        DmlStatement updStatement = createUpdateDmlStatement(table);
        Row row = createRandomUpdateValues(tran, updStatement, table);
        Object[] values = new Object[table.getColumnCount()];
        int i = 0;
        for (Column column : table.getColumns()) {
            if (!column.isPrimaryKey()) {
                values[i++] = row.get(column.getName());
            }
        }
        if (i > 0) {
            for (Column column : table.getPrimaryKeyColumns()) {
                values[i++] = row.get(column.getName());
            }
            try {
                tran.prepareAndExecute(updStatement.getSql(), values);
            } catch (SqlException ex) {
                log.info("Failed to update {}: {}", table.getName(), ex.getMessage());
                if (debug) {
                    logRow(row);
                    log.info("Failed SQL: " + updStatement.getSql(), ex);
                }
                if (!continueOnError) {
                    throw ex;
                }
            }
        }
    }

    /**
     * Select a random row from the table and update all columns except for primary and foreign keys.
     *
     * @param sqlTemplate
     * @param table
     */
    private void insertRandomRecord(ISqlTransaction tran, Table table) {
        DmlStatement insertStatement = createInsertDmlStatement(table);
        Row row = null;
        try {
            int count = 0;
            for (int i = 0; i < 100 && count == 0; i++) {
                row = createRandomInsertValues(insertStatement, table);
                try {
                    count = tran.prepareAndExecute(insertStatement.getSql(), insertStatement.getValueArray(row.toArray(table.getColumnNames()),
                            row.toArray(table.getPrimaryKeyColumnNames())));
                } catch (SqlException e) {
                    log.warn(e.getMessage());
                    if (platform.getDatabaseInfo().isRequiresSavePointsInTransaction()) {
                        tran.rollback();
                    }
                    count = 0;
                }
            }
            if (count == 0 && cascading) {
                log.info("Failed to insert non-conflicting row into {}", table.getName());
                if (debug) {
                    logRow(row);
                    log.info("Failed SQL: " + insertStatement.getSql());
                }
                if (continueOnError) {
                    selectRandomRecord(tran, table);
                }
            }
        } catch (SqlException ex) {
            log.info("Failed to insert into {}: {}", table.getName(), ex.getMessage());
            if (debug) {
                logRow(row);
                log.info("Failed SQL: " + insertStatement.getSql(), ex);
            }
            if (continueOnError) {
                selectRandomRecord(tran, table);
            } else {
                throw ex;
            }
        }
    }

    public String createDynamicRandomInsertSql(Table table) {
        DmlStatement insertStatement = createInsertDmlStatement(table);
        Row row = createRandomInsertValues(insertStatement, table);
        return insertStatement.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
    }

    public String createDynamicRandomUpdateSql(Table table) {
        DmlStatement updStatement = createUpdateDmlStatement(table);
        Row row = createRandomUpdateValues(null, updStatement, table);
        return updStatement.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
    }

    public String createDynamicRandomDeleteSql(Table table) {
        DmlStatement deleteStatement = createDeleteDmlStatement(table);
        Row row = selectRandomRow(null, table);
        return deleteStatement.buildDynamicDeleteSql(BinaryEncoding.HEX, row, false, true);
    }

    /**
     * Delete a random row in the given table or delete all rows matching selectColumns in the given table.
     * 
     * @param table
     *            Table to delete from.
     * @param selectColumns
     *            If provided, the rows that match this criteria are deleted.
     */
    private void deleteRandomRecord(ISqlTransaction tran, Table table) {
        DmlStatement deleteStatement = createDeleteDmlStatement(table);
        Row row = selectRandomRow(tran, table);
        try {
            tran.prepareAndExecute(deleteStatement.getSql(), row.toArray(table.getPrimaryKeyColumnNames()));
        } catch (SqlException ex) {
            log.info("Failed to delete from {}: {}", table.getName(), ex.getMessage());
            if (debug) {
                logRow(row);
                log.info("Failed SQL: " + deleteStatement.getSql(), ex);
            }
            if (platform.getSqlTemplate().isForeignKeyChildExistsViolation(ex)) {
                try {
                    deleteForeignKeyChildren(tran, table, row);
                } catch (SqlException e) {
                    if (!continueOnError) {
                        throw e;
                    }
                }
            } else if (!continueOnError) {
                throw ex;
            }
        }
    }

    private void deleteForeignKeyChildren(ISqlTransaction tran, Table table, Row row) {
        List<TableRow> tableRows = new ArrayList<TableRow>();
        tableRows.add(new TableRow(table, row, null, null, null));
        tableRows = platform.getDdlReader().getExportedForeignTableRows(tran, tableRows, new HashSet<TableRow>(), BinaryEncoding.HEX);
        if (!tableRows.isEmpty()) {
            Collections.reverse(tableRows);
            Set<TableRow> visited = new HashSet<TableRow>();
            for (TableRow foreignTableRow : tableRows) {
                if (visited.add(foreignTableRow)) {
                    Table foreignTable = foreignTableRow.getTable();
                    log.info("Remove foreign row "
                            + "catalog '{}' schema '{}' foreign table name '{}' fk name '{}' where sql '{}' "
                            + "to correct table '{}' for column '{}'",
                            foreignTable.getCatalog(), foreignTable.getSchema(), foreignTable.getName(), foreignTableRow.getFkName(), foreignTableRow
                                    .getWhereSql(),
                            table.getName(), foreignTableRow.getReferenceColumnName());
                    DatabaseInfo info = platform.getDatabaseInfo();
                    String tableName = Table.getFullyQualifiedName(foreignTable.getCatalog(), foreignTable.getSchema(), foreignTable.getName(),
                            info.getDelimiterToken(), info.getCatalogSeparator(), info.getSchemaSeparator());
                    String sql = "DELETE FROM " + tableName + " WHERE " + foreignTableRow.getWhereSql();
                    tran.prepareAndExecute(sql);
                }
            }
        }
    }

    private void selectRandomRecord(ISqlTransaction tran, Table table) {
        Row row = null;
        if (hasDependentColumns(table)) {
            Map<Column, Object> dependent = getDependentColumnValues(table);
            if (dependent.size() > 0) {
                row = selectSpecificRow(tran, table, dependent.keySet().toArray(new Column[dependent.size()]),
                        dependent.values().toArray(new Object[dependent.size()]));
                saveDependentColumnValues(table, row);
            } else {
                row = selectRandomRow(tran, table);
                saveDependentColumnValues(table, row);
            }
        } else {
            row = selectRandomRow(tran, table);
        }
        currentRowValues.put(table.getName(), row);
    }

    private boolean hasDependentColumns(Table table) {
        return commonDependencyTables.contains(table);
    }

    private Map<Column, Object> getDependentColumnValues(Table table) {
        Map<Column, Object> columnValues = new HashMap<Column, Object>();
        for (Column column : table.getColumns()) {
            String key = table.getQualifiedColumnName(column);
            List<Object> commonValue = commonDependencyValues.get(key);
            if (commonValue != null && commonValue.size() != 0) {
                columnValues.put(column, commonValue.get(0));
            }
        }
        return columnValues;
    }

    private void saveDependentColumnValues(Table table, Row row) {
        for (String columnName : row.keySet()) {
            String key = table.getFullyQualifiedName() + "." + columnName;
            List<Object> commonValue = commonDependencyValues.get(key);
            if (commonValue != null) {
                commonValue.clear();
                Object value = row.get(columnName);
                commonValue.add(value);
                if (verbose) {
                    log.info("Setting common value for {}={}", key, value);
                }
            }
        }
    }

    private void clearDependentColumnValues() {
        for (List<Object> commonValue : commonDependencyValues.values()) {
            commonValue.clear();
        }
    }

    private void loadSetColumnValues(Table table) {
        if (!platform.getName().equals(DatabaseNamesConstants.MYSQL)) {
            return;
        }
        String tableKey = table.getName() + ".";
        for (String key : setColumnValues.keySet()) {
            if (key.startsWith(tableKey)) {
                return;
            }
        }
        try {
            String sql = "SELECT column_name, column_type FROM information_schema.columns " +
                    "WHERE table_schema = ? AND table_name = ? AND column_type LIKE 'set(%'";
            List<Row> rows = platform.getSqlTemplate().query(sql, new Object[] { getCatalogToUse(), table.getName() });
            for (Row row : rows) {
                String columnName = row.getString("column_name");
                String columnType = row.getString("column_type");
                String valuesStr = columnType.substring(4, columnType.length() - 1);
                String[] values = valuesStr.split(",");
                for (int i = 0; i < values.length; i++) {
                    values[i] = StringUtils.unwrap(values[i].trim(), "'");
                }
                setColumnValues.put(table.getName() + "." + columnName, values);
            }
        } catch (Exception e) {
            log.debug("Could not load SET column values for table {}", table.getName(), e);
        }
    }

    private Object generateRandomValueForColumn(Table table, Column column) {
        Object objectValue = null;
        int type = column.getMappedTypeCode();
        loadSetColumnValues(table);
        if (column.getPlatformColumns() != null && column.getPlatformColumns().get(platform.getName()) != null && column.getPlatformColumns().get(platform
                .getName()).isEnum()) {
            objectValue = column.getPlatformColumns().get(platform.getName()).getEnumValues()[getRand().nextInt(column.getPlatformColumns().get(platform
                    .getName()).getEnumValues().length)];
        } else if (setColumnValues.containsKey(table.getName() + "." + column.getName())) {
            String[] setValues = setColumnValues.get(table.getName() + "." + column.getName());
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String val : setValues) {
                if (getRand().nextBoolean()) {
                    if (!first)
                        sb.append(",");
                    sb.append(val);
                    first = false;
                }
            }
            if (sb.length() == 0) {
                sb.append(setValues[getRand().nextInt(setValues.length)]);
            }
            objectValue = sb.toString();
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().equals("uniqueidentifier")) {
            objectValue = randomUUID();
        } else if (column.isTimestampWithTimezone()) {
            objectValue = String.format("%s %s",
                    FormatUtils.TIMESTAMP_FORMATTER.format(randomDate()),
                    AppUtils.getTimezoneOffset());
        } else if (type == Types.DATE) {
            objectValue = DateUtils.truncate(randomDate(), Calendar.DATE);
        } else if (type == Types.TIMESTAMP || type == Types.TIME) {
            objectValue = randomTimestamp();
        } else if (type == Types.INTEGER || type == Types.BIGINT) {
            objectValue = randomInt();
        } else if (type == Types.SMALLINT) {
            objectValue = randomSmallInt(column.getJdbcTypeName().toLowerCase().contains("unsigned"));
        } else if (type == Types.FLOAT) {
            objectValue = randomFloat();
        } else if (type == Types.DOUBLE) {
            objectValue = checkForMoney(column, randomDouble());
        } else if (type == Types.TINYINT) {
            objectValue = randomTinyInt();
        } else if (type == Types.NUMERIC || type == Types.DECIMAL
                || type == Types.REAL) {
            // big decimal is very slow if too big
            int size = column.getSizeAsInt() > 32 ? 32 : column.getSizeAsInt();
            objectValue = checkForMoney(column, randomBigDecimal(size, column.getScale()));
        } else if (type == Types.BOOLEAN || type == Types.BIT) {
            objectValue = randomBoolean();
        } else if (type == Types.BLOB || type == Types.LONGVARBINARY || type == Types.BINARY
                || type == Types.VARBINARY ||
                // SQLServer text type
                type == -10) {
            int size = maxByteSize;
            if ((type == Types.BINARY || type == Types.VARBINARY) && size > column.getSizeAsInt()) {
                size = column.getSizeAsInt();
            }
            objectValue = randomBytes(randomSize(column, size));
        } else if (type == Types.ARRAY) {
            objectValue = null;
        } else if (type == Types.VARCHAR || type == Types.NVARCHAR || type == Types.NCHAR || type == Types.LONGVARCHAR || type == Types.CHAR
                || type == Types.CLOB) {
            if (column.getJdbcTypeName() != null
                    && (column.getJdbcTypeName().equals("JSON") || column.getJdbcTypeName().equals("jsonb"))) {
                objectValue = "{\"jumpmind\":\"symmetricds\"}";
            } else if ("UUID".equalsIgnoreCase(column.getJdbcTypeName())) {
                objectValue = randomUUID();
            } else if ("TIME".equalsIgnoreCase(column.getJdbcTypeName())) {
                objectValue = randomTimestamp();
            } else if ("BIT".equalsIgnoreCase(column.getJdbcTypeName())) {
                objectValue = randomBoolean() ? "1" : "0";
            } else if ("timestamp_timezone".equalsIgnoreCase(column.getJdbcTypeName())) {
                objectValue = randomTimestamp();
            } else {
                int size = maxTextSize;
                // Assume if the size is 0 there is no max size configured.
                if (column.getSizeAsInt() != 0) {
                    Integer minSize = minColumnSizes.get(table.getQualifiedColumnName(column));
                    if (minSize != null) {
                        // use smallest size of related foreign key columns
                        size = minSize;
                    } else if (size > column.getSizeAsInt()) {
                        size = column.getSizeAsInt();
                    }
                }
                objectValue = randomString(randomSize(column, size));
            }
        } else if (type == Types.OTHER) {
            if ("UUID".equalsIgnoreCase(column.getJdbcTypeName())) {
                objectValue = randomUUID();
            }
        }
        return objectValue;
    }

    private Object checkForMoney(Column column, Object objectValue) {
        if (Strings.CI.contains(column.getJdbcTypeName(), "money")) {
            BigDecimal bd;
            if (objectValue instanceof BigDecimal) {
                bd = ((BigDecimal) objectValue).abs();
            } else {
                bd = BigDecimal.valueOf((Double) objectValue);
            }
            int wholeDigits = bd.precision() - bd.scale();
            if ((Strings.CI.contains(column.getJdbcTypeName(), "smallmoney") && wholeDigits > 5) || wholeDigits > 14) {
                bd = bd.divide(BigDecimal.TEN);
            }
            bd = bd.setScale(2, RoundingMode.HALF_UP);
            objectValue = bd.toString();
        }
        return objectValue;
    }

    private int randomSize(Column column, int size) {
        if (!column.isPrimaryKey()) {
            size = getRand().nextInt(size);
            if (size == 0) {
                size = 1;
            }
        }
        return size;
    }

    private Object randomSmallInt(boolean unsigned) {
        if (unsigned) {
            return Integer.valueOf(getRand().nextInt(32768));
        } else {
            // TINYINT (-32768 32767)
            return Integer.valueOf(getRand().nextInt(65535) - 32768);
        }
    }

    private Object randomFloat() {
        return getRand().nextFloat();
    }

    private Object randomDouble() {
        final long places = 1000000000l;
        double d = getRand().nextDouble() * places;
        long l = Math.round(d);
        return ((double) l) / (double) places + 2 + (double) randomInt();
    }

    private Object randomTinyInt() {
        // TINYINT (-128 to 127) or (0 to 255) depending on database platform
        return Integer.valueOf(getRand().nextInt(127));
    }

    private String randomString(int maxLength) {
        StringBuilder str = new StringBuilder(maxLength);
        for (int i = 0; i < maxLength; i++) {
            str.append(randomChar());
        }
        return str.toString();
    }

    private byte[] randomBytes(int length) {
        byte array[] = new byte[length];
        for (int i = 0; i < length; i++) {
            array[i] = (byte) getRand().nextInt(256);
        }
        return array;
    }

    private boolean randomBoolean() {
        return getRand().nextBoolean();
    }

    private BigDecimal randomBigDecimal(int size, int digits) {
        if (size <= 0 && (digits <= 0)) {
            // set the values to something reasonable
            size = 10;
            digits = 0;
        }
        Random rnd = getRand();
        StringBuilder str = new StringBuilder();
        if (size > 0 && rnd.nextBoolean()) {
            str.append("-");
        }
        for (int i = 0; i < size; i++) {
            if (i == size - digits)
                str.append(".");
            str.append(rnd.nextInt(10));
        }
        return new BigDecimal(str.toString());
    }

    private Character randomChar() {
        int rnd = getRand().nextInt(52);
        char base = (rnd < 26) ? 'A' : 'a';
        return (char) (base + rnd % 26);
    }

    private Date randomDate() {
        // Random date between 1970 and 2020
        long l = getRand().nextLong();
        if (l < 0) {
            l *= -1l;
        }
        long ms = (50L * 365 * 24 * 60 * 60 * 1000);
        return new Date(l % ms);
    }

    private Timestamp randomTimestamp() {
        return Timestamp.valueOf(FormatUtils.TIMESTAMP_FORMATTER.format(randomDate()));
    }

    private Integer randomInt() {
        return Integer.valueOf(getRand().nextInt(1000000));
    }

    private String randomUUID() {
        return UUID.randomUUID().toString();
    }

    public String getSchemaToUse() {
        if (StringUtils.isBlank(schema)) {
            return platform.getDefaultSchema();
        } else {
            return schema;
        }
    }

    public String getCatalogToUse() {
        if (StringUtils.isBlank(catalog)) {
            return platform.getDefaultCatalog();
        } else {
            return catalog;
        }
    }

    protected List<String> getLocalFkRefColumns(Table table) {
        List<String> columns = new ArrayList<String>();
        for (ForeignKey fk : table.getForeignKeys()) {
            for (Reference ref : fk.getReferences()) {
                columns.add(ref.getLocalColumnName());
            }
        }
        return columns;
    }

    protected Map<String, Table> getAllDbTables() {
        if (allDbTablesCache == null) {
            allDbTablesCache = new TreeMap<String, Table>(String.CASE_INSENSITIVE_ORDER);
            Table[] allTables = platform.readDatabase(getCatalogToUse(), getSchemaToUse(), null).getTables();
            for (Table table : allTables) {
                table = filterColumns(table);
                allDbTablesCache.put(table.getName(), table);
            }
        }
        return allDbTablesCache;
    }

    protected Table getDbTable(String catalogName, String schemaName, String tableName) {
        Table table = getAllDbTables().get(tableName);
        if (table == null) {
            table = (Table) platform.readRelationFromDatabase(catalogName, schemaName, tableName);
            table = filterColumns(table);
        }
        return table;
    }

    protected Table filterColumns(Table table) {
        if (platform.getName().startsWith("mssql")) {
            List<Column> columnsToRemove = new ArrayList<Column>();
            for (Column column : table.getColumns()) {
                if (column.getJdbcTypeName().equalsIgnoreCase("timestamp") || column.getJdbcTypeName().equalsIgnoreCase("rowversion")) {
                    columnsToRemove.add(column);
                }
            }
            if (columnsToRemove.size() > 0) {
                table = table.copy();
                for (Column column : columnsToRemove) {
                    table.removeColumn(column);
                }
            }
        }
        return table;
    }

    public DmlStatement createInsertDmlStatement(Table table) {
        return platform.createDmlStatement(DmlType.INSERT, table.getCatalog(), table.getSchema(), table.getName(),
                table.getPrimaryKeyColumns(), table.getColumns(), null, textColumnExpression);
    }

    public DmlStatement createUpdateDmlStatement(Table table) {
        return platform.createDmlStatement(DmlType.UPDATE, table.getCatalog(), table.getSchema(), table.getName(),
                table.getPrimaryKeyColumns(), table.getNonPrimaryKeyColumns(), null, textColumnExpression);
    }

    public DmlStatement createDeleteDmlStatement(Table table) {
        return platform.createDmlStatement(DmlType.DELETE, table.getCatalog(), table.getSchema(), table.getName(),
                table.getPrimaryKeyColumns(), table.getNonPrimaryKeyColumns(), null, textColumnExpression);
    }

    private Row createRandomInsertValues(DmlStatement insertStatement, Table table) {
        Column[] columns = insertStatement.getMetaData();
        Row row = new Row(columns.length);
        for (Column column : columns) {
            Object value = null;
            List<ForeignKeyReference> fkrs = foreignKeyReferences.get(table.getQualifiedColumnName(column));
            if (fkrs != null) {
                for (ForeignKeyReference fkr : fkrs) {
                    if (fkr != null && !table.getName().equals(fkr.getForeignKey().getForeignTableName())) {
                        Map<String, Object> foreignRowValues = currentRowValues.get(fkr.getForeignKey().getForeignTableName());
                        if (foreignRowValues != null) {
                            value = foreignRowValues.get(fkr.getReference().getForeignColumnName());
                            break;
                        }
                    }
                }
            }
            if (value == null) {
                value = generateRandomValueForColumn(table, column);
            }
            row.put(column.getName(), value);
        }
        Map<Column, Object> dependentValues = getDependentColumnValues(table);
        if (dependentValues != null) {
            for (Map.Entry<Column, Object> entry : dependentValues.entrySet()) {
                row.put(entry.getKey().getName(), entry.getValue());
            }
        }
        Set<ForeignKey> listCompositeForeignKeys = compositeForeignKeys.get(table);
        if (listCompositeForeignKeys != null) {
            for (ForeignKey fk : listCompositeForeignKeys) {
                if (!table.getName().equals(fk.getForeignTableName())) {
                    Map<String, Object> foreignRowValues = currentRowValues.get(fk.getForeignTableName());
                    if (foreignRowValues != null) {
                        for (Reference ref : fk.getReferences()) {
                            row.put(ref.getLocalColumnName(), foreignRowValues.get(ref.getForeignColumnName()));
                        }
                    }
                }
            }
        }
        // for a self-referencing foreign key, the row will just refer to itself to satisfy
        for (ForeignKey fk : table.getForeignKeys()) {
            if (table.getName().equals(fk.getForeignTableName())) {
                for (Reference ref : fk.getReferences()) {
                    row.put(ref.getLocalColumnName(), row.get(ref.getForeignColumnName()));
                }
            }
        }
        if (verbose) {
            log.info("Generated row for " + table.getName() + " " + row);
        }
        currentRowValues.put(table.getName(), row);
        return row;
    }

    private Row createRandomUpdateValues(ISqlTransaction tran, DmlStatement updStatement, Table table) {
        Row row = selectRandomRow(tran, table);
        if (row == null) {
            log.warn("Unable to update a random record in empty table '" + table.getName() + "'.");
            return null;
        }
        Column[] columns = updStatement.getMetaData();
        // Get list of local fk reference columns
        List<String> localFkRefColumns = getLocalFkRefColumns(table);
        int numToUpdate = getRand().nextInt(columns.length);
        if (numToUpdate == 0) {
            numToUpdate = 1;
        }
        for (int i = 0; i < columns.length; i++) {
            if (!(columns[i].isPrimaryKey() || localFkRefColumns.contains(columns[i].getName()))) {
                if (numToUpdate-- > 0) {
                    row.put(columns[i].getName(), generateRandomValueForColumn(table, columns[i]));
                }
            }
        }
        currentRowValues.put(table.getName(), row);
        return row;
    }

    private void logRow(Row row) {
        StringBuilder sb = null;
        for (String name : row.keySet()) {
            sb = (sb == null) ? new StringBuilder() : sb.append(",");
            sb.append(name).append("=").append(row.get(name));
        }
        log.info("The row data was: {} ", sb);
    }

    public void setPlatform(IDatabasePlatform platform) {
        this.platform = platform;
    }

    public int getRecordCount() {
        return inputLength;
    }

    public void setRecordCount(int recordCount) {
        this.inputLength = recordCount;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public boolean isCascading() {
        return cascading;
    }

    public void setCascading(boolean cascading) {
        this.cascading = cascading;
    }

    public boolean isCascadingSelect() {
        return cascadingSelect;
    }

    public void setCascadingSelect(boolean cascadingSelect) {
        this.cascadingSelect = cascadingSelect;
    }

    public boolean isTruncate() {
        return truncate;
    }

    public void setTruncate(boolean truncate) {
        this.truncate = truncate;
    }

    public String[] getIgnore() {
        return ignore;
    }

    public void setIgnore(String[] ignore) {
        this.ignore = ignore;
    }

    public String[] getPrefixed() {
        return prefixed;
    }

    public void setPrefixed(String[] prefixed) {
        this.prefixed = prefixed;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public SecureRandom getRand() {
        if (rand == null) {
            rand = new SecureRandom();
        }
        return rand;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setDmlWeight(DmlWeight dmlWeight) {
        this.dmlWeight = dmlWeight;
    }

    public void setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }

    public void setPrint(boolean print) {
        this.print = print;
    }

    public boolean getPrint() {
        return print;
    }

    public void setUseRandomCount(boolean useRandomCount) {
        this.useRandomCount = useRandomCount;
    }

    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    public void setMaxRowsCommit(int maxRowsCommit) {
        this.maxRowsCommit = maxRowsCommit;
    }

    public void setCommitDelay(int commitDelay) {
        this.commitDelay = commitDelay;
    }

    public void setPercentRollback(int percentRollback) {
        this.percentRollback = percentRollback;
    }

    public int getInsertWeight() {
        return dmlWeight.getInsertWeight();
    }

    public int getUpdateWeight() {
        return dmlWeight.getUpdateWeight();
    }

    public int getDeleteWeight() {
        return dmlWeight.getDeleteWeight();
    }

    public void setTextColumnExpression(String textColumnExpression) {
        this.textColumnExpression = textColumnExpression;
    }

    public String getTextColumnExpression() {
        return textColumnExpression;
    }

    public long getMaxByteSize() {
        return maxByteSize;
    }

    public void setMaxByteSize(int maxByteSize) {
        this.maxByteSize = maxByteSize;
    }

    public int getMaxTextSize() {
        return maxTextSize;
    }

    public void setMaxTextSize(int maxTextSize) {
        this.maxTextSize = maxTextSize;
    }

    static class ForeignKeyReference {
        ForeignKey fk;
        Reference ref;

        public ForeignKeyReference(ForeignKey fk, Reference ref) {
            this.fk = fk;
            this.ref = ref;
        }

        public ForeignKey getForeignKey() {
            return fk;
        }

        public Reference getReference() {
            return ref;
        }

        public String toString() {
            return fk.getForeignTableName() + "." + ref.getForeignColumnName() + "->" + ref.getLocalColumnName();
        }

        public int hashCode() {
            return fk.hashCode() + ref.hashCode();
        }

        public boolean equals(Object o) {
            return o != null && o.hashCode() == hashCode();
        }
    }
}
