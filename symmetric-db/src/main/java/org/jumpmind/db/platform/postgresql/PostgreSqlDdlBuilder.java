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
package org.jumpmind.db.platform.postgresql;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.sql.Types;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.AddTableLoggingChange;
import org.jumpmind.db.alter.ColumnAutoIncrementChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.ColumnDefaultValueChange;
import org.jumpmind.db.alter.ColumnRequiredChange;
import org.jumpmind.db.alter.ColumnSizeChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.PrimaryKeyChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.RemoveFunctionChange;
import org.jumpmind.db.alter.RemoveTableLoggingChange;
import org.jumpmind.db.alter.RemoveTriggerChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

/*
 * The SQL Builder for PostgresSql.
 */
public class PostgreSqlDdlBuilder extends AbstractDdlBuilder {
    public PostgreSqlDdlBuilder() {
        super(DatabaseNamesConstants.POSTGRESQL);
        // this is the default length though it might be changed when building
        // PostgreSQL
        // in file src/include/postgres_ext.h
        databaseInfo.setMaxIdentifierLength(63);
        databaseInfo.setRequiresSavePointsInTransaction(true);
        databaseInfo.setRequiresAutoCommitForDdl(false);
        databaseInfo.addNativeTypeMapping(Types.ARRAY, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.BINARY, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.BIT, "BOOLEAN");
        databaseInfo.addNativeTypeMapping(Types.BLOB, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.CLOB, "TEXT", Types.LONGVARCHAR);
        databaseInfo.addNativeTypeMapping(Types.NCLOB, "TEXT", Types.LONGVARCHAR);
        databaseInfo.addNativeTypeMapping(Types.DECIMAL, "NUMERIC", Types.NUMERIC);
        databaseInfo.addNativeTypeMapping(Types.DISTINCT, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.DOUBLE, "DOUBLE PRECISION");
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "DOUBLE PRECISION", Types.DOUBLE);
        databaseInfo.addNativeTypeMapping(Types.JAVA_OBJECT, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "BYTEA");
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "TEXT", Types.LONGVARCHAR);
        databaseInfo.addNativeTypeMapping(Types.NULL, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.OTHER, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.REF, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.STRUCT, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping(Types.VARBINARY, "BYTEA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping("BOOLEAN", "BOOLEAN", "BIT");
        databaseInfo.addNativeTypeMapping("DATALINK", "BYTEA", "LONGVARBINARY");
        databaseInfo.addNativeTypeMapping(ColumnTypes.NVARCHAR, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.LONGNVARCHAR, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.NCHAR, "CHAR", Types.CHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPTZ, "TIMESTAMPTZ");
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPLTZ, "TIMESTAMPTZ", ColumnTypes.TIMESTAMPTZ);
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMETZ, "TIMETZ");
        databaseInfo.setHasSize(Types.TIMESTAMP, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPTZ, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPLTZ, true);
        databaseInfo.setHasSize(Types.TIME, true);
        databaseInfo.setHasSize(ColumnTypes.TIMETZ, true);
        databaseInfo.setDefaultSize(Types.CHAR, 254);
        databaseInfo.setDefaultSize(Types.VARCHAR, 254);
        databaseInfo.setDefaultSize(Types.TIMESTAMP, 6);
        databaseInfo.setDefaultSize(ColumnTypes.TIMESTAMPTZ, 6);
        databaseInfo.setDefaultSize(ColumnTypes.TIMESTAMPLTZ, 6);
        databaseInfo.setDefaultSize(Types.TIME, 6);
        databaseInfo.setDefaultSize(ColumnTypes.TIMETZ, 6);
        databaseInfo.setMaxSize("TIMESTAMP", 6);
        databaseInfo.setMaxSize("TIMESTAMPTZ", 6);
        databaseInfo.setMaxSize("TIME", 6);
        databaseInfo.setMaxSize("TIMETZ", 6);
        // no support for specifying the size for these types (because they are
        // mapped to BYTEA which back-maps to BLOB)
        databaseInfo.setHasSize(Types.BINARY, false);
        databaseInfo.setHasSize(Types.VARBINARY, false);
        databaseInfo.setNonBlankCharColumnSpacePadded(true);
        databaseInfo.setBlankCharColumnSpacePadded(true);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
        databaseInfo.setBinaryQuoteStart("0x");
        databaseInfo.setBinaryQuoteEnd("");
        // we need to handle the backslash first otherwise the other
        // already escaped sequences would be affected
        addEscapedCharSequence("\\", "\\\\");
        addEscapedCharSequence("\b", "\\b");
        addEscapedCharSequence("\f", "\\f");
        addEscapedCharSequence("\n", "\\n");
        addEscapedCharSequence("\r", "\\r");
        addEscapedCharSequence("\t", "\\t");
    }

    @Override
    public String mapDefaultValue(Object defaultValue, Column column) {
        String newValue = super.mapDefaultValue(defaultValue, column);
        if (databaseInfo.getDefaultValuesToTranslate().containsKey(defaultValue.toString())) {
            return newValue;
        }
        int typeCode = column.getMappedTypeCode();
        if ((typeCode == Types.TIMESTAMP || typeCode == ColumnTypes.TIMESTAMPTZ || typeCode == ColumnTypes.TIMESTAMPLTZ)
                && !column.allPlatformColumnNamesContain("postgres")) {
            String uppercaseValue = newValue.trim().toUpperCase();
            if (uppercaseValue.startsWith("CURRENT_DATE") || uppercaseValue.startsWith("CURRENT DATE")
                    || (column.anyPlatformColumnNameContains("oracle") && uppercaseValue.startsWith("SYSDATE"))
                    || (column.anyPlatformColumnNameContains("mysql") && !uppercaseValue.matches(".*\\d.*")
                            && (uppercaseValue.startsWith("NOW") || uppercaseValue.startsWith("CURRENT_TIMESTAMP")
                                    || uppercaseValue.startsWith("LOCALTIME") || uppercaseValue.startsWith("SYSDATE")))) {
                newValue = "CURRENT_TIMESTAMP(0)";
            } else if (column.anyPlatformColumnNameContains("mssql")
                    && (uppercaseValue.startsWith("GETDATE(") || uppercaseValue.startsWith("CURRENT_TIMESTAMP"))) {
                newValue = "CURRENT_TIMESTAMP(3)";
            } else if (uppercaseValue.startsWith("SYSTIMESTAMP") || uppercaseValue.startsWith("SYSDATETIME(")) {
                newValue = "CURRENT_TIMESTAMP";
            } else if (column.anyPlatformColumnNameContains("mysql") && uppercaseValue.startsWith("SYSDATE(")) {
                newValue = Strings.CI.replaceOnce(newValue, "sysdate", "CURRENT_TIMESTAMP");
            } else if (uppercaseValue.startsWith("SYSDATETIMEOFFSET(")) {
                newValue = "LOCALTIMESTAMP";
            } else if (uppercaseValue.startsWith("GETUTCDATE(")) {
                newValue = "TIMEZONE('utc', CURRENT_TIMESTAMP(3))";
            } else if (uppercaseValue.startsWith("SYSUTCDATETIME(")) {
                newValue = "TIMEZONE('utc', CURRENT_TIMESTAMP)";
            } else if (uppercaseValue.startsWith("UTC_TIMESTAMP")) {
                if (uppercaseValue.matches(".*\\d.*")) {
                    Matcher matcher = Pattern.compile("\\d").matcher(uppercaseValue);
                    matcher.find();
                    int precision = Integer.valueOf(matcher.group());
                    if (precision > 6) {
                        precision = 6;
                    }
                    newValue = "TIMEZONE('utc', CURRENT_TIMESTAMP(" + precision + "))";
                } else {
                    newValue = "TIMEZONE('utc', CURRENT_TIMESTAMP(0))";
                }
            }
        } else if (newValue != null && newValue.equals("newid()")) {
            newValue = "gen_random_uuid()";
        }
        return newValue;
    }

    public static boolean isUsePseudoSequence() {
        return "true".equalsIgnoreCase(System.getProperty(
                "org.jumpmind.symmetric.ddl.use.table.seq", "false"));
    }

    public static boolean isMapCharToJson() {
        return "true".equalsIgnoreCase(System.getProperty(
                "org.jumpmind.symmetric.ddl.use.postgres.map.json", "true"));
    }

    @Override
    protected void dropTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        ddl.append("DROP TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        ddl.append(" CASCADE");
        printEndOfStatement(ddl);
        if (!temporary && !recreate) {
            Column[] columns = table.getAutoIncrementColumns();
            for (int idx = 0; idx < columns.length; idx++) {
                dropAutoIncrementSequence(table, columns[idx], ddl);
            }
        }
    }

    @Override
    protected boolean writeAlterColumnDataTypeToBigInt(ColumnDataTypeChange change, StringBuilder ddl) {
        writeTableAlterStmt(change.getChangedTable(), ddl);
        ddl.append(" ALTER COLUMN ");
        Column column = change.getChangedColumn();
        column.setTypeCode(change.getNewTypeCode());
        printIdentifier(getColumnName(column), ddl);
        ddl.append(" TYPE ");
        ddl.append(getSqlType(column));
        printEndOfStatement(ddl);
        return true;
    }

    @Override
    public void writeExternalIndexDropStmt(Table table, IIndex index, StringBuilder ddl) {
        ddl.append("DROP INDEX ");
        if (StringUtils.isNotBlank(table.getSchema())) {
            ddl.append(getDelimitedIdentifier(table.getSchema())).append(".");
        }
        printIdentifier(getIndexName(index), ddl);
        printEndOfStatement(ddl);
    }

    @Override
    protected void createTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        if (!temporary && !recreate) {
            for (int idx = 0; idx < table.getColumnCount(); idx++) {
                Column column = table.getColumn(idx);
                if (column.isAutoIncrement()) {
                    createAutoIncrementSequence(table, column, ddl);
                }
            }
        }
        super.createTable(table, ddl, temporary, recreate);
    }

    /**
     * Supports an optional UNLOGGED trait when building new create table statement.
     */
    @Override
    protected void writeTableCreateOpeningStmt(Table table, StringBuilder ddl) {
        ddl.append("CREATE");
        if (!table.getLogging() && this.databaseInfo.isTableLevelLoggingSupported()) {
            ddl.append(" UNLOGGED");
        }
        ddl.append(" TABLE ");
    }

    /*
     * Creates the auto-increment sequence that is then used in the column.
     * 
     * @param table The table
     * 
     * @param column The column
     */
    private void createAutoIncrementSequence(Table table, Column column, StringBuilder ddl) {
        if (isUsePseudoSequence()) {
            ddl.append("CREATE TABLE ");
            ddl.append(getConstraintName(null, table, column.getName(), "tbl"));
            ddl.append("(SEQ_ID int8)");
            printEndOfStatement(ddl);
            ddl.append("CREATE FUNCTION ");
            ddl.append(getConstraintName(null, table, column.getName(), "seq"));
            ddl.append("() ");
            ddl.append("RETURNS INT8 AS $$ ");
            ddl.append("DECLARE curVal int8; ");
            ddl.append("BEGIN ");
            ddl.append("  select seq_id into curVal from ");
            ddl.append(getConstraintName(null, table, column.getName(), "tbl"));
            ddl.append(" for update;");
            ddl.append("  if curVal is null then ");
            ddl.append("      insert into ");
            ddl.append(getConstraintName(null, table, column.getName(), "tbl"));
            ddl.append(" values(1); ");
            ddl.append("      curVal = 0; ");
            ddl.append("  else ");
            ddl.append("      update ");
            ddl.append(getConstraintName(null, table, column.getName(), "tbl"));
            ddl.append(" set seq_id=curVal+1; ");
            ddl.append("  end if; ");
            ddl.append("  return curVal+1; ");
            ddl.append("END; ");
            println("$$ LANGUAGE plpgsql; ", ddl);
        } else {
            ddl.append("CREATE SEQUENCE ");
            if (StringUtils.isNotBlank(table.getSchema())) {
                printIdentifier(table.getSchema(), ddl);
                ddl.append(".");
            }
            printIdentifier(getConstraintName(null, table, column.getName(), "seq"), ddl);
            printEndOfStatement(ddl);
        }
    }

    /*
     * Creates the auto-increment sequence that is then used in the column.
     * 
     * @param table The table
     * 
     * @param column The column
     */
    private void dropAutoIncrementSequence(Table table, Column column, StringBuilder ddl) {
        if (isUsePseudoSequence()) {
            ddl.append("DROP TABLE ");
            ddl.append(getConstraintName(null, table, column.getName(), "tbl"));
            printEndOfStatement(ddl);
            ddl.append("DROP FUNCTION ");
            ddl.append(getConstraintName(null, table, column.getName(), "seq"));
            ddl.append("()");
            printEndOfStatement(ddl);
        } else {
            ddl.append("DROP SEQUENCE ");
            if (StringUtils.isNotBlank(table.getSchema())) {
                printIdentifier(table.getSchema(), ddl);
                ddl.append(".");
            }
            printIdentifier(getConstraintName(null, table, column.getName(), "seq"), ddl);
            printEndOfStatement(ddl);
        }
    }

    @Override
    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
        if (isUsePseudoSequence()) {
            ddl.append(" DEFAULT ");
            ddl.append(getConstraintName(null, table, column.getName(), "seq"));
            ddl.append("()");
        } else {
            ddl.append(" DEFAULT nextval('");
            if (StringUtils.isNotBlank(table.getSchema())) {
                printIdentifier(table.getSchema(), ddl);
                ddl.append(".");
            }
            printIdentifier(getConstraintName(null, table, column.getName(), "seq"), ddl);
            ddl.append("')");
        }
    }

    @Override
    protected void writeGeneratedColumn(Table table, Column column, StringBuilder ddl) {
        writeColumnTypeDefaultRequired(table, column, ddl);
        String definition = getDefinitionForGeneratedColumn(table, column);
        if (!StringUtils.isBlank(definition)) {
            if (!(definition.startsWith("(") && definition.endsWith(")"))) {
                ddl.append(" GENERATED ALWAYS AS ").append("(").append(definition).append(") STORED");
            } else {
                ddl.append(" GENERATED ALWAYS AS ").append(definition).append(" STORED");
            }
        }
    }

    @Override
    public String getSelectLastIdentityValues(Table table) {
        Column[] columns = table.getAutoIncrementColumns();
        if (columns.length == 0) {
            return null;
        } else {
            StringBuilder result = new StringBuilder();
            result.append("SELECT ");
            for (int idx = 0; idx < columns.length; idx++) {
                if (idx > 0) {
                    result.append(", ");
                }
                result.append("currval('");
                result.append(getDelimitedIdentifier(getConstraintName(null, table,
                        columns[idx].getName(), "seq")));
                result.append("') AS ");
                result.append(getDelimitedIdentifier(columns[idx].getName()));
            }
            return result.toString();
        }
    }

    @Override
    protected void writeCastExpression(Column sourceColumn, Column targetColumn, StringBuilder ddl) {
        if (sourceColumn != null && targetColumn != null) {
            if (sourceColumn.getMappedTypeCode() == Types.TIME && targetColumn.getMappedTypeCode() == Types.TIMESTAMP) {
                ddl.append("date_trunc('DAY', localtimestamp) + ");
                printIdentifier(getColumnName(sourceColumn), ddl);
            } else {
                super.writeCastExpression(sourceColumn, targetColumn, ddl);
            }
        }
    }

    @Override
    protected void processTableStructureChanges(Database currentModel, Database desiredModel,
            Table sourceTable, Table targetTable, List<TableChange> changes, StringBuilder ddl) {
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof AddTableLoggingChange) {
                processChange(currentModel, desiredModel, (AddTableLoggingChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof RemoveTableLoggingChange) {
                processChange(currentModel, desiredModel, (RemoveTableLoggingChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof AddColumnChange) {
                AddColumnChange addColumnChange = (AddColumnChange) change;
                processChange(currentModel, desiredModel, addColumnChange, ddl);
                changeIt.remove();
            } else if (change instanceof RemoveColumnChange) {
                processChange(currentModel, desiredModel, (RemoveColumnChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof CopyColumnValueChange) {
                CopyColumnValueChange copyColumnChange = (CopyColumnValueChange) change;
                processChange(currentModel, desiredModel, copyColumnChange, ddl);
                changeIt.remove();
            } else if (change instanceof ColumnDefaultValueChange) {
                processChange(currentModel, desiredModel, (ColumnDefaultValueChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof ColumnRequiredChange) {
                processChange(currentModel, desiredModel, (ColumnRequiredChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof ColumnSizeChange) {
                processChange(currentModel, desiredModel, (ColumnSizeChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof PrimaryKeyChange) {
                processChange(currentModel, desiredModel, (PrimaryKeyChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof ColumnAutoIncrementChange) {
                if (processChange(currentModel, desiredModel, (ColumnAutoIncrementChange) change,
                        ddl)) {
                    changeIt.remove();
                }
            }
        }
        super.processTableStructureChanges(currentModel, desiredModel, sourceTable, targetTable,
                changes, ddl);
    }

    protected void processChange(Database currentModel, Database desiredModel,
            PrimaryKeyChange change, StringBuilder ddl) {
        writeTableAlterStmt(change.getChangedTable(), ddl);
        printIndent(ddl);
        ddl.append(" DROP CONSTRAINT ");
        printIdentifier(change.getChangedTable().getPrimaryKeyConstraintName(), ddl);
        printEndOfStatement(ddl);
        writeTableAlterStmt(change.getChangedTable(), ddl);
        printIndent(ddl);
        ddl.append(" ADD ");
        writePrimaryKeyStmt(change.getChangedTable(), change.getNewPrimaryKeyColumns(), ddl);
        printEndOfStatement(ddl);
    }

    /*
     * Processes the addition of a column to a table.
     */
    protected void processChange(Database currentModel, Database desiredModel,
            AddColumnChange change, StringBuilder ddl) {
        writeTableAlterStmt(change.getChangedTable(), ddl);
        printIndent(ddl);
        ddl.append(" ADD COLUMN ");
        writeColumn(change.getChangedTable(), change.getNewColumn(), ddl);
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    /*
     * Processes the removal of a column from a table.
     */
    protected void processChange(Database currentModel, Database desiredModel,
            RemoveColumnChange change, StringBuilder ddl) {
        writeTableAlterStmt(change.getChangedTable(), ddl);
        printIndent(ddl);
        ddl.append("DROP COLUMN ");
        printIdentifier(getColumnName(change.getColumn()), ddl);
        printEndOfStatement(ddl);
        if (change.getColumn().isAutoIncrement()) {
            dropAutoIncrementSequence(change.getChangedTable(), change.getColumn(), ddl);
        }
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    /*
     * Processes the addition of table-level transaction logging.
     */
    @Override
    protected void processChange(Database currentModel, Database desiredModel,
            AddTableLoggingChange change, StringBuilder ddl) {
        writeTableAlterStmt(change.getChangedTable(), ddl);
        printIndent(ddl);
        ddl.append(" SET LOGGED");
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    /*
     * Processes the removal of table-level transaction logging.
     */
    @Override
    protected void processChange(Database currentModel, Database desiredModel,
            RemoveTableLoggingChange change, StringBuilder ddl) {
        writeTableAlterStmt(change.getChangedTable(), ddl);
        printIndent(ddl);
        ddl.append(" SET UNLOGGED");
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    protected void processChange(Database currentModel, Database desiredModel,
            ColumnDefaultValueChange change, StringBuilder ddl) {
        if (change.getNewDefaultValue() == null
                && change.getChangedColumn().getDefaultValue() != null) {
            writeTableAlterStmt(change.getChangedTable(), ddl);
            ddl.append(" ALTER COLUMN ");
            Column column = change.getChangedColumn();
            printIdentifier(getColumnName(column), ddl);
            ddl.append(" DROP DEFAULT ");
            printEndOfStatement(ddl);
        } else {
            writeTableAlterStmt(change.getChangedTable(), ddl);
            ddl.append(" ALTER COLUMN ");
            Column column = change.getChangedColumn();
            printIdentifier(getColumnName(column), ddl);
            ddl.append(" SET DEFAULT ");
            printDefaultValue(change.getNewDefaultValue(), column, ddl);
            printEndOfStatement(ddl);
        }
    }

    protected void processChange(Database currentModel, Database desiredModel,
            ColumnRequiredChange change, StringBuilder ddl) {
        boolean required = !change.getChangedColumn().isRequired();
        writeTableAlterStmt(change.getChangedTable(), ddl);
        ddl.append(" ALTER COLUMN ");
        Column column = change.getChangedColumn();
        printIdentifier(getColumnName(column), ddl);
        if (required) {
            ddl.append(" SET NOT NULL ");
        } else {
            ddl.append(" DROP NOT NULL ");
        }
        printEndOfStatement(ddl);
    }

    protected void processChange(Database currentModel, Database desiredModel,
            ColumnSizeChange change, StringBuilder ddl) {
        writeTableAlterStmt(change.getChangedTable(), ddl);
        ddl.append(" ALTER COLUMN ");
        Column column = change.getChangedColumn();
        column.setSizeAndScale(change.getNewSize(), change.getNewScale());
        printIdentifier(getColumnName(column), ddl);
        ddl.append(" TYPE ");
        ddl.append(getSqlType(column));
        printEndOfStatement(ddl);
    }

    protected boolean processChange(Database currentModel, Database desiredModel,
            ColumnAutoIncrementChange change, StringBuilder ddl) {
        boolean autoIncrement = !change.getColumn().isAutoIncrement();
        if (!autoIncrement) {
            writeTableAlterStmt(change.getChangedTable(), ddl);
            ddl.append(" ALTER COLUMN ");
            Column column = change.getColumn();
            printIdentifier(getColumnName(column), ddl);
            ddl.append(" DROP DEFAULT ");
            printEndOfStatement(ddl);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void processChange(Database currentModel, Database desiredModel, RemoveTriggerChange change,
            StringBuilder ddl) {
        ddl.append("DROP TRIGGER ").append(change.getTrigger().getName());
        ddl.append(" ON ").append(change.getChangedTable().getFullyQualifiedTableName());
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    @Override
    protected void processChange(Database currentModel, Database desiredModel, RemoveFunctionChange change,
            StringBuilder ddl) {
        ddl.append("DROP FUNCTION IF EXISTS ");
        if (change.getFunction().getSchemaName() != null && change.getFunction().getSchemaName().length() > 0) {
            ddl.append(change.getFunction().getSchemaName()).append(".");
        }
        ddl.append(change.getFunction().getFunctionName());
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    @Override
    protected void printDefaultValue(String defaultValue, Column column, StringBuilder ddl) {
        int typeCode = column.getMappedTypeCode();
        if (defaultValue != null &&
                ((defaultValue.endsWith("::uuid") && Types.OTHER == typeCode) ||
                        (defaultValue.contains("::") && Types.ARRAY == typeCode))) {
            ddl.append(defaultValue);
        } else if (Types.BOOLEAN == typeCode || Types.BIT == typeCode) {
            boolean isNull = false;
            if (defaultValue == null || defaultValue.equalsIgnoreCase("null")) {
                isNull = true;
            }
            if (!isNull) {
                ddl.append(databaseInfo.getValueQuoteToken());
                ddl.append(escapeStringValue(defaultValue));
                ddl.append(databaseInfo.getValueQuoteToken());
            } else {
                ddl.append(defaultValue);
            }
        } else {
            super.printDefaultValue(defaultValue, column, ddl);
        }
    }

    @Override
    protected boolean shouldUseQuotes(String defaultValue, Column column) {
        String defaultValueStr = mapDefaultValue(defaultValue, column);
        while (defaultValueStr != null && defaultValueStr.startsWith("(") && defaultValueStr.endsWith(")")) {
            defaultValueStr = defaultValueStr.substring(1, defaultValueStr.length() - 1);
        }
        return super.shouldUseQuotes(defaultValue, column) && !defaultValueStr.trim().toUpperCase().startsWith("TIMEZONE(");
    }

    @Override
    public String getSqlType(Column column) {
        String type = super.getSqlType(column);
        if (type.startsWith("CHAR") && column.getPlatformColumns() != null) {
            if (isMapCharToJson()) {
                for (Map.Entry<String, PlatformColumn> platformColumn : column.getPlatformColumns().entrySet()) {
                    if (platformColumn.getValue() != null && platformColumn.getValue().getType() != null &&
                            platformColumn.getValue().getType().equals("JSON")) {
                        type = "JSONB";
                        return type;
                    }
                }
            }
        }
        if (column.getMappedTypeCode() == Types.CHAR || column.getMappedTypeCode() == Types.VARCHAR) {
            int size = column.getSizeAsInt();
            if (size > 10485760) {
                type = "TEXT";
            }
        }
        return type;
    }

    @Override
    protected void writeExternalIndexCreate(Table table, IIndex index, StringBuilder ddl) {
        super.writeExternalIndexCreate(table, index, ddl);
        if (index.getIncludedColumns() != null && index.getIncludedColumnCount() > 0) {
            ddl.append(" INCLUDE (");
            for (int i = 0; i < index.getIncludedColumnCount(); i++) {
                if (i > 0) {
                    ddl.append(", ");
                }
                printIdentifier(index.getIncludedColumns()[i].getName(), ddl);
            }
            ddl.append(")");
        }
    }
}
