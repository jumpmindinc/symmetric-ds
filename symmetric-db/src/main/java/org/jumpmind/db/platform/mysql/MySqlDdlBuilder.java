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
package org.jumpmind.db.platform.mysql;

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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.AddPrimaryKeyChange;
import org.jumpmind.db.alter.ColumnAutoIncrementChange;
import org.jumpmind.db.alter.ColumnAutoUpdateChange;
import org.jumpmind.db.alter.ColumnChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.PrimaryKeyChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.RemovePrimaryKeyChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

/*
 * The SQL Builder for MySQL.
 */
public class MySqlDdlBuilder extends AbstractDdlBuilder {
    public MySqlDdlBuilder(String databaseName) {
        super(databaseName);
        init();
    }

    public MySqlDdlBuilder() {
        super(DatabaseNamesConstants.MYSQL);
        init();
    }

    protected void init() {
        databaseInfo.setSystemForeignKeyIndicesAlwaysNonUnique(true);
        databaseInfo.setMaxIdentifierLength(64);
        databaseInfo.setNullAsDefaultValueRequired(true);
        databaseInfo.setDefaultValuesForLongTypesSupported(false);
        // see
        // http://dev.mysql.com/doc/refman/4.1/en/example-auto-increment.html
        databaseInfo.setNonPKIdentityColumnsSupported(false);
        // MySql returns synthetic default values for pk columns
        databaseInfo.setSyntheticDefaultValueForRequiredReturned(true);
        databaseInfo.setCommentPrefix("#");
        // Double quotes are only allowed for delimiting identifiers if the
        // server SQL mode includes ANSI_QUOTES
        databaseInfo.setDelimiterToken("`");
        databaseInfo.setZeroDateAllowed(true);
        databaseInfo.addNativeTypeMapping(Types.ARRAY, "LONGBLOB", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.BIT, "BIT");
        databaseInfo.addNativeTypeMapping(Types.BLOB, "LONGBLOB", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.CLOB, "LONGTEXT", Types.LONGVARCHAR);
        databaseInfo.addNativeTypeMapping(Types.DISTINCT, "LONGBLOB", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "DOUBLE", Types.DOUBLE);
        databaseInfo.addNativeTypeMapping(Types.JAVA_OBJECT, "LONGBLOB", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "MEDIUMBLOB");
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "MEDIUMTEXT", Types.LONGVARCHAR);
        databaseInfo.addNativeTypeMapping(Types.NULL, "MEDIUMBLOB", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.NUMERIC, "DECIMAL", Types.DECIMAL);
        databaseInfo.addNativeTypeMapping(Types.OTHER, "LONGBLOB", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.REAL, "FLOAT");
        databaseInfo.addNativeTypeMapping(Types.REF, "MEDIUMBLOB", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.STRUCT, "LONGBLOB", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.NCLOB, "LONGTEXT(MAX)");
        // Since TIMESTAMP is not a stable datatype yet, and does not support a
        // higher precision
        // than DATETIME (year to seconds) as of MySQL 5, we map the JDBC type
        // here to DATETIME
        // TODO: Make this configurable
        databaseInfo.addNativeTypeMapping(Types.TIMESTAMP, "DATETIME");
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPTZ, "DATETIME", Types.TIMESTAMP);
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPLTZ, "DATETIME", Types.TIMESTAMP);
        // In MySql, TINYINT has only a range of -128 to 127
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping("BOOLEAN", "BIT", "BIT");
        databaseInfo.addNativeTypeMapping("DATALINK", "MEDIUMBLOB", "LONGVARBINARY");
        databaseInfo.addNativeTypeMapping(ColumnTypes.NVARCHAR, "NVARCHAR", Types.NVARCHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.LONGNVARCHAR, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.NCHAR, "NCHAR", Types.NCHAR);
        databaseInfo.setDefaultSize(Types.CHAR, 254);
        databaseInfo.setDefaultSize(Types.VARCHAR, 254);
        databaseInfo.setDefaultSize(Types.BINARY, 254);
        databaseInfo.setDefaultSize(Types.VARBINARY, 254);
        databaseInfo.setHasSize(Types.TIMESTAMP, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPTZ, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPLTZ, true);
        databaseInfo.setHasSize(Types.TIME, true);
        databaseInfo.setHasSize(ColumnTypes.TIMETZ, true);
        databaseInfo.setMaxSize("DATETIME", 6);
        databaseInfo.setMaxSize("TIMESTAMP", 6);
        databaseInfo.setMaxSize("TIME", 6);
        databaseInfo.setNonBlankCharColumnSpacePadded(false);
        databaseInfo.setBlankCharColumnSpacePadded(false);
        databaseInfo.setCharColumnSpaceTrimmed(true);
        databaseInfo.setEmptyStringNulled(false);
        databaseInfo.setBinaryQuoteStart("0x");
        databaseInfo.setBinaryQuoteEnd("");
        // MySql 5.0 returns an empty string for default values for pk columns
        // which is different from the MySql 4 behavior
        databaseInfo.setSyntheticDefaultValueForRequiredReturned(false);
        // we need to handle the backslash first otherwise the other
        // already escaped sequences would be affected
        addEscapedCharSequence("\\", "\\\\");
        addEscapedCharSequence("\0", "\\0");
        addEscapedCharSequence("\"", "\\\"");
        addEscapedCharSequence("\b", "\\b");
        addEscapedCharSequence("\n", "\\n");
        addEscapedCharSequence("\r", "\\r");
        addEscapedCharSequence("\t", "\\t");
        addEscapedCharSequence("\u001A", "\\Z");
    }

    @Override
    public String mapDefaultValue(Object defaultValue, Column column) {
        String newValue = super.mapDefaultValue(defaultValue, column);
        if (databaseInfo.getDefaultValuesToTranslate().containsKey(defaultValue.toString())) {
            return newValue;
        }
        int typeCode = column.getMappedTypeCode();
        if ((typeCode == Types.TIMESTAMP || typeCode == ColumnTypes.TIMESTAMPTZ || typeCode == ColumnTypes.TIMESTAMPLTZ)
                && (!column.allPlatformColumnNamesContain("mysql") && !column.allPlatformColumnNamesContain("maria"))) {
            String uppercaseValue = defaultValue.toString().trim().toUpperCase();
            if (uppercaseValue.startsWith("SYSDATE")) {
                newValue = "SYSDATE()";
            } else if (uppercaseValue.startsWith("CURRENT_DATE") || uppercaseValue.startsWith("CURRENT DATE")) {
                newValue = "NOW()";
            } else if (column.anyPlatformColumnNameContains("mssql")
                    && (uppercaseValue.startsWith("GETDATE(") || uppercaseValue.startsWith("CURRENT_TIMESTAMP"))) {
                newValue = "NOW(3)";
            } else if (uppercaseValue.startsWith("SYSTIMESTAMP") || uppercaseValue.startsWith("SYSDATETIME")
                    || uppercaseValue.startsWith("TRANSACTION_TIMESTAMP(")
                    || uppercaseValue.startsWith("STATEMENT_TIMESTAMP(") || uppercaseValue.startsWith("CLOCK_TIMESTAMP(")
                    || (column.anyPlatformColumnNameContains("oracle") && uppercaseValue.startsWith("CURRENT_TIMESTAMP")
                            || uppercaseValue.startsWith("LOCALTIMESTAMP"))
                    || (column.anyPlatformColumnNameContains("postgres") && !uppercaseValue.matches(".*\\d.*")
                            && (uppercaseValue.startsWith("NOW") || uppercaseValue.startsWith("CURRENT_TIMESTAMP")
                                    || uppercaseValue.startsWith("LOCALTIMESTAMP")))) {
                newValue = "NOW(6)";
            } else if (uppercaseValue.startsWith("GETUTCDATE(")) {
                newValue = "UTC_TIMESTAMP(3)";
            } else if (uppercaseValue.startsWith("SYSUTCDATETIME(")) {
                newValue = "UTC_TIMESTAMP(6)";
            }
            if (newValue.matches(".*[A-Za-z].*") && !(newValue.startsWith("(") && newValue.endsWith(")"))) {
                newValue = "(" + newValue + ")";
            }
        }
        return newValue;
    }

    @Override
    protected void dropTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        ddl.append("DROP TABLE IF EXISTS ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        printEndOfStatement(ddl);
    }

    @Override
    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
        ddl.append("AUTO_INCREMENT");
    }

    @Override
    protected void writeColumnDefaultValueStmt(Table table, Column column, StringBuilder ddl) {
        super.writeColumnDefaultValueStmt(table, column, ddl);
        if (column.getParsedDefaultValue() == null
                && !(databaseInfo.isDefaultValueUsedForIdentitySpec() && column.isAutoIncrement())
                && StringUtils.isBlank(column.getDefaultValue()) && column.findPlatformColumn(databaseName) != null
                && "0000-00-00".equals(column.findPlatformColumn(databaseName).getDefaultValue())) {
            ddl.append(" DEFAULT ");
            writeColumnDefaultValue(table, column, ddl);
        }
    }

    @Override
    protected void writeColumnDefaultValue(Table table, Column column, StringBuilder ddl) {
        int typeCode = column.getMappedTypeCode();
        String defaultValue = getNativeDefaultValue(column);
        String defaultValueStr = mapDefaultValue(defaultValue, column);
        PlatformColumn platformColumn = column.findPlatformColumn(databaseName);
        if (TypeMap.isDateTimeType(typeCode) && defaultValueStr.toUpperCase().equals("CURRENT_TIMESTAMP") && hasSize(column)) {
            String nativeType = getNativeType(column);
            if (platformColumn != null) {
                nativeType = platformColumn.getType();
            }
            if (nativeType.startsWith("DATETIME") || nativeType.startsWith("TIMESTAMP")) {
                Integer size = column.getSizeAsInt();
                if (platformColumn != null) {
                    size = platformColumn.getSize();
                } else if (column.getSize() == null) {
                    size = databaseInfo.getDefaultSize(column.getMappedTypeCode());
                }
                if (size != null && size >= 0) {
                    int maxSize = databaseInfo.getMaxSize(nativeType);
                    if (maxSize > 0 && size > maxSize) {
                        size = maxSize;
                    }
                    ddl.append(defaultValueStr).append("(").append(size).append(")");
                    return;
                }
            }
        } else if (databaseInfo.isExpressionsAsDefaultValuesSupported() && platformColumn != null
                && column.isExpressionAsDefaultValue()) {
            if (defaultValue.startsWith("(") && defaultValue.endsWith(")")) {
                ddl.append(defaultValueStr);
            } else {
                ddl.append("(").append(defaultValueStr).append(")");
            }
            return;
        }
        printDefaultValue(defaultValue, column, ddl);
    }

    @Override
    protected void writeColumnAutoUpdateStmt(Table table, Column column, StringBuilder ddl) {
        if (column.getMappedTypeCode() == Types.TIMESTAMP && column.isAutoUpdate()) {
            ddl.append("on update current_timestamp()");
        }
    }

    @Override
    protected boolean shouldUseQuotes(String defaultValue, Column column) {
        String defaultValueStr = mapDefaultValue(defaultValue, column);
        while (defaultValueStr != null && defaultValueStr.startsWith("(") && defaultValueStr.endsWith(")")) {
            defaultValueStr = defaultValueStr.substring(1, defaultValueStr.length() - 1);
        }
        return super.shouldUseQuotes(defaultValue, column) && !defaultValueStr.trim().toUpperCase().startsWith("UTC_TIMESTAMP");
    }

    @Override
    protected boolean shouldGeneratePrimaryKeys(Column[] primaryKeyColumns) {
        // mySQL requires primary key indication for auto increment key columns
        // I'm not sure why the default skips the pk statement if all are
        // identity
        return true;
    }

    @Override
    protected void writeEmbeddedPrimaryKeysStmt(Table table, StringBuilder ddl) {
        super.writeEmbeddedPrimaryKeysStmt(table, ddl);
        Column[] primaryKeyColumns = table.getPrimaryKeyColumnsInIndexOrder();
        if (primaryKeyColumns.length > 1) {
            for (int i = 1; i < primaryKeyColumns.length; i++) {
                Column col = primaryKeyColumns[i];
                if (col.isAutoIncrement()) {
                    printStartOfEmbeddedStatement(ddl);
                    ddl.append("KEY ");
                    printIdentifier(getColumnName(col), ddl);
                    ddl.append(" (");
                    printIdentifier(getColumnName(col), ddl);
                    ddl.append(")");
                }
            }
        }
    }

    /*
     * Normally mysql will return the LAST_INSERT_ID as the column name for the inserted id. Since ddlutils expects the real column name of the field that is
     * autoincrementing, the column has an alias of that column name.
     */
    @Override
    public String getSelectLastIdentityValues(Table table) {
        String autoIncrementKeyName = "";
        if (table.getAutoIncrementColumns().length > 0) {
            autoIncrementKeyName = table.getAutoIncrementColumns()[0].getName();
        }
        return "SELECT LAST_INSERT_ID() " + autoIncrementKeyName;
    }

    @Override
    protected void writeExternalForeignKeyDropStmt(Table table, ForeignKey foreignKey,
            StringBuilder ddl) {
        writeTableAlterStmt(table, ddl);
        ddl.append("DROP FOREIGN KEY ");
        printIdentifier(getForeignKeyName(table, foreignKey), ddl);
        printEndOfStatement(ddl);
        if (foreignKey.isAutoIndexPresent()) {
            writeTableAlterStmt(table, ddl);
            ddl.append("DROP INDEX ");
            printIdentifier(getForeignKeyName(table, foreignKey), ddl);
            printEndOfStatement(ddl);
        }
    }

    @Override
    protected boolean isFullTextIndex(IIndex index) {
        for (int idx = 0; idx < index.getColumnCount(); idx++) {
            IndexColumn indexColumn = index.getColumn(idx);
            if (indexColumn != null) {
                Column column = indexColumn.getColumn();
                if (column != null && column.getMappedTypeCode() == Types.LONGVARCHAR) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void processTableStructureChanges(Database currentModel, Database desiredModel,
            Table sourceTable, Table targetTable, List<TableChange> changes, StringBuilder ddl) {
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof AddColumnChange) {
                processChange(currentModel, desiredModel, (AddColumnChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof ColumnAutoIncrementChange) {
                /**
                 * This has to happen before any primary key changes because if a column is bring dropped as auto increment and being dropped from the primary
                 * key, an auto increment column can't be a non primary key column on mysql.
                 */
                try {
                    Column sourceColumn = ((ColumnAutoIncrementChange) change).getColumn();
                    Column targetColumn = (Column) sourceColumn.clone();
                    targetColumn.setAutoIncrement(!sourceColumn.isAutoIncrement());
                    processColumnChange(sourceTable, targetTable, sourceColumn, targetColumn, ddl);
                    changeIt.remove();
                } catch (CloneNotSupportedException e) {
                    log.error("", e);
                }
            }
        }
        List<Column> changedColumns = new ArrayList<Column>();
        // we don't have to care about the order because the comparator will
        // have ensured that a add primary key change comes after all necessary
        // columns are present
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof RemoveColumnChange) {
                processChange(currentModel, desiredModel, (RemoveColumnChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof CopyColumnValueChange) {
                CopyColumnValueChange copyColumnChange = (CopyColumnValueChange) change;
                processChange(currentModel, desiredModel, copyColumnChange, ddl);
                changeIt.remove();
            } else if (change instanceof AddPrimaryKeyChange) {
                processChange(currentModel, desiredModel, (AddPrimaryKeyChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof PrimaryKeyChange) {
                processChange(currentModel, desiredModel, (PrimaryKeyChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof RemovePrimaryKeyChange) {
                processChange(currentModel, desiredModel, (RemovePrimaryKeyChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof ColumnChange) {
                /*
                 * we gather all changed columns because we can use the ALTER TABLE MODIFY COLUMN statement for them
                 */
                Column column = ((ColumnChange) change).getChangedColumn();
                if (!changedColumns.contains(column)) {
                    changedColumns.add(column);
                }
                changeIt.remove();
            } else if (change instanceof ColumnAutoUpdateChange) {
                try {
                    Column sourceColumn = ((ColumnAutoUpdateChange) change).getColumn();
                    Column targetColumn = (Column) sourceColumn.clone();
                    targetColumn.setAutoUpdate(!sourceColumn.isAutoUpdate());
                    processColumnChange(sourceTable, targetTable, sourceColumn, targetColumn, ddl);
                    changeIt.remove();
                } catch (CloneNotSupportedException e) {
                    log.error("Failed to clone column", e);
                }
            }
        }
        for (Iterator<Column> columnIt = changedColumns.iterator(); columnIt.hasNext();) {
            Column sourceColumn = columnIt.next();
            Column targetColumn = targetTable.findColumn(sourceColumn.getName(),
                    false);
            processColumnChange(sourceTable, targetTable, sourceColumn, targetColumn, ddl);
        }
    }

    /*
     * Processes the addition of a column to a table.
     */
    protected void processChange(Database currentModel, Database desiredModel,
            AddColumnChange change, StringBuilder ddl) {
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(change.getChangedTable()));
        printIndent(ddl);
        ddl.append("ADD COLUMN ");
        writeColumn(change.getChangedTable(), change.getNewColumn(), ddl);
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    /*
     * Processes the removal of a column from a table.
     */
    protected void processChange(Database currentModel, Database desiredModel,
            RemoveColumnChange change, StringBuilder ddl) {
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(change.getChangedTable()));
        printIndent(ddl);
        ddl.append("DROP COLUMN ");
        printIdentifier(getColumnName(change.getColumn()), ddl);
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    /*
     * Processes the removal of a primary key from a table.
     */
    protected void processChange(Database currentModel, Database desiredModel,
            RemovePrimaryKeyChange change, StringBuilder ddl) {
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(change.getChangedTable()));
        printIndent(ddl);
        ddl.append("DROP PRIMARY KEY");
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    /*
     * Processes the change of the primary key of a table.
     */
    protected void processChange(Database currentModel, Database desiredModel,
            PrimaryKeyChange change, StringBuilder ddl) {
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(change.getChangedTable()));
        printIndent(ddl);
        ddl.append("DROP PRIMARY KEY");
        printEndOfStatement(ddl);
        writeExternalPrimaryKeysCreateStmt(change.getChangedTable(),
                change.getNewPrimaryKeyColumns(), ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    /*
     * Processes a change to a column.
     */
    protected void processColumnChange(Table sourceTable, Table targetTable, Column sourceColumn,
            Column targetColumn, StringBuilder ddl) {
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(sourceTable));
        printIndent(ddl);
        ddl.append("MODIFY COLUMN ");
        writeColumn(targetTable, targetColumn, ddl);
        printEndOfStatement(ddl);
    }

    @Override
    public String getSqlType(Column column) {
        String sqlType = super.getSqlType(column);
        if (column.isAutoIncrement()
                && (column.getMappedTypeCode() == Types.DECIMAL || column.getMappedTypeCode() == Types.NUMERIC)) {
            sqlType = "BIGINT";
        }
        if (column.getMappedTypeCode() == Types.TIMESTAMP && column.getScale() > 0) {
            sqlType = "DATETIME(" + column.getScale() + ")";
        }
        PlatformColumn pc = column.getPlatformColumns() == null ? null : column.findPlatformColumn(DatabaseNamesConstants.MYSQL);
        if (pc != null && ("ENUM".equalsIgnoreCase(column.getJdbcTypeName()) || "ENUM".equalsIgnoreCase(pc.getType())
                || "SET".equalsIgnoreCase(column.getJdbcTypeName()) || "SET".equalsIgnoreCase(pc.getType()))) {
            String[] enumValues = pc.getEnumValues();
            if (enumValues != null && enumValues.length > 0) {
                // Redo the enum/set, specifying the values returned from the database in the enumValues field
                // instead of the size of the column
                String typeName = "SET".equalsIgnoreCase(column.getJdbcTypeName()) || "SET".equalsIgnoreCase(pc.getType()) ? "SET" : "ENUM";
                StringBuilder tmpSqlType = new StringBuilder();
                tmpSqlType.append(typeName);
                tmpSqlType.append("(");
                boolean appendComma = false;
                for (String s : enumValues) {
                    if (appendComma) {
                        tmpSqlType.append(",");
                    }
                    tmpSqlType.append("'").append(s).append("'");
                    appendComma = true;
                }
                tmpSqlType.append(")");
                sqlType = tmpSqlType.toString();
            }
        }
        if ("TINYBLOB".equalsIgnoreCase(column.getJdbcTypeName()) || (pc != null && "TINYBLOB".equalsIgnoreCase(pc.getType()))) {
            // For some reason, MySql driver returns BINARY type for TINYBLOB instead of BLOB type
            sqlType = "TINYBLOB";
        } else if (pc == null && (column.getMappedTypeCode() == Types.CHAR || column.getMappedTypeCode() == Types.NCHAR) && column.getSizeAsInt() > 255) {
            if (column.getSizeAsInt() <= 8000) {
                // max row size and column size is 65,535, but calculating row size would be tough
                sqlType = "VARCHAR(" + column.getSizeAsInt() + ")";
            } else if (column.getSizeAsInt() <= 16777216) {
                sqlType = "MEDIUMTEXT";
            } else {
                sqlType = "LONGTEXT";
            }
        }
        pc = column.getPlatformColumns() == null ? null : column.getPlatformColumns().get(DatabaseNamesConstants.ORACLE);
        if (pc == null) {
            pc = column.getPlatformColumns() == null ? null : column.getPlatformColumns().get(DatabaseNamesConstants.ORACLE122);
        }
        if (pc == null) {
            pc = column.getPlatformColumns() == null ? null : column.getPlatformColumns().get(DatabaseNamesConstants.ORACLE23);
        }
        if (pc != null) {
            if ("NVARCHAR2".equals(pc.getType())) {
                sqlType = "NVARCHAR(" + pc.getSize() + ")";
            } else if ("LONG".equals(pc.getType()) || "CLOB".equals(pc.getType()) || "NCLOB".equals(pc.getType()) || "XMLTYPE".equals(pc.getType())) {
                sqlType = "LONGTEXT";
            } else if ("FLOAT".equals(pc.getType()) && pc.getSize() >= 63) {
                sqlType = "DOUBLE";
            }
        }
        if (sqlType.contains("UNSIGNED")) {
            sqlType = sqlType.replaceAll(" UNSIGNED", "");
            sqlType = sqlType + " UNSIGNED";
        }
        return sqlType;
    }
}
