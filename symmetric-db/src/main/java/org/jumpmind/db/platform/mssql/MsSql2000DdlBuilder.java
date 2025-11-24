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
package org.jumpmind.db.platform.mssql;

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

import java.rmi.server.UID;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.AddForeignKeyChange;
import org.jumpmind.db.alter.AddIndexChange;
import org.jumpmind.db.alter.AddPrimaryKeyChange;
import org.jumpmind.db.alter.ColumnAutoIncrementChange;
import org.jumpmind.db.alter.ColumnChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.ColumnSizeChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.IModelChange;
import org.jumpmind.db.alter.PrimaryKeyChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.RemoveForeignKeyChange;
import org.jumpmind.db.alter.RemoveIndexChange;
import org.jumpmind.db.alter.RemovePrimaryKeyChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PlatformUtils;

/*
 * The SQL Builder for the Microsoft SQL Server.
 */
public class MsSql2000DdlBuilder extends AbstractDdlBuilder {
    /* We use a generic date format. */
    private DateFormat _genericDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    /* We use a generic date format. */
    private DateFormat _genericTimeFormat = new SimpleDateFormat("HH:mm:ss");

    public MsSql2000DdlBuilder() {
        super(DatabaseNamesConstants.MSSQL2000);
        databaseInfo.setMaxIdentifierLength(128);
        databaseInfo.addNativeTypeMapping(Types.ARRAY, "IMAGE", Types.LONGVARBINARY);
        // BIGINT will be mapped back to BIGINT by the model reader
        // databaseInfo.addNativeTypeMapping(Types.BIGINT, "DECIMAL(19,0)");
        databaseInfo.addNativeTypeMapping(Types.BIGINT, "BIGINT", Types.BIGINT);
        databaseInfo.addNativeTypeMapping(Types.DATE, "DATETIME", Types.TIMESTAMP);
        databaseInfo.addNativeTypeMapping(Types.DISTINCT, "IMAGE", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.DOUBLE, "FLOAT", Types.FLOAT);
        databaseInfo.addNativeTypeMapping(Types.INTEGER, "INT");
        databaseInfo.addNativeTypeMapping(Types.JAVA_OBJECT, "IMAGE", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "IMAGE");
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "TEXT", Types.LONGVARCHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.LONGNVARCHAR, "NTEXT", ColumnTypes.LONGNVARCHAR);
        databaseInfo.addNativeTypeMapping(Types.NULL, "IMAGE", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.OTHER, "IMAGE", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.REF, "IMAGE", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.STRUCT, "IMAGE", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.TIME, "DATETIME", Types.TIMESTAMP);
        databaseInfo.addNativeTypeMapping(Types.TIMESTAMP, "DATETIME");
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPTZ, "DATETIME");
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPLTZ, "DATETIME");
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping("BOOLEAN", "BIT", "BIT");
        databaseInfo.addNativeTypeMapping("DATALINK", "IMAGE", "LONGVARBINARY");
        databaseInfo.setDefaultSize(Types.CHAR, 254);
        databaseInfo.setDefaultSize(Types.VARCHAR, 254);
        databaseInfo.setDefaultSize(Types.BINARY, 254);
        databaseInfo.setDefaultSize(Types.VARBINARY, 254);
        databaseInfo.setDefaultSize(Types.TIMESTAMP, 3);
        databaseInfo.setMaxSize("DATETIME", 3);
        databaseInfo.setDateOverridesToTimestamp(true);
        databaseInfo.setNonBlankCharColumnSpacePadded(true);
        databaseInfo.setBlankCharColumnSpacePadded(true);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
        databaseInfo.setAutoIncrementUpdateAllowed(false);
        databaseInfo.setGeneratedColumnsSupported(true);
        databaseInfo.setBinaryQuoteStart("0x");
        databaseInfo.setBinaryQuoteEnd("");
        addLobMapping();
    }

    protected void addLobMapping() {
        databaseInfo.addNativeTypeMapping(Types.BLOB, "IMAGE", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.CLOB, "TEXT", Types.LONGVARCHAR);
    }

    @Override
    public String mapDefaultValue(Object defaultValue, Column column) {
        String newValue = super.mapDefaultValue(defaultValue, column);
        if (databaseInfo.getDefaultValuesToTranslate().containsKey(defaultValue.toString())) {
            return newValue;
        }
        int typeCode = column.getMappedTypeCode();
        if ((typeCode == Types.TIMESTAMP || typeCode == ColumnTypes.TIMESTAMPTZ || typeCode == ColumnTypes.TIMESTAMPLTZ)
                && !column.allPlatformColumnNamesContain("mssql")) {
            String uppercaseValue = newValue.trim().toUpperCase();
            if (uppercaseValue.startsWith("SYSDATE") || uppercaseValue.startsWith("SYSTIMESTAMP")
                    || uppercaseValue.startsWith("CURRENT_DATE") || uppercaseValue.startsWith("CURRENT_TIMESTAMP")
                    || uppercaseValue.startsWith("CURRENT DATE") || uppercaseValue.startsWith("CURRENT TIMESTAMP")
                    || uppercaseValue.startsWith("LOCALTIME") || uppercaseValue.startsWith("NOW(")
                    || uppercaseValue.startsWith("TRANSACTION_TIMESTAMP(") || uppercaseValue.startsWith("STATEMENT_TIMESTAMP(")
                    || uppercaseValue.startsWith("CLOCK_TIMESTAMP(")) {
                newValue = "GETDATE()";
            } else if (uppercaseValue.startsWith("UTC_TIMESTAMP")) {
                newValue = "GETUTCDATE()";
            }
        }
        return newValue;
    }

    @Override
    protected void createTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        writeQuotationOnStatement(ddl);
        super.createTable(table, ddl, temporary, recreate);
    }

    @Override
    protected void dropTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        String tableName = getTableName(table.getName());
        String catalog = table.getCatalog();
        String schema = table.getSchema();
        String tableNameVar = "tn" + createUniqueIdentifier();
        String constraintNameVar = "cn" + createUniqueIdentifier();
        writeQuotationOnStatement(ddl);
        ddl.append("IF EXISTS (SELECT 1 FROM ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        ddl.append("dbo.sysobjects WHERE type = 'U' AND name = ");
        printAlwaysSingleQuotedIdentifier(tableName, ddl);
        println(")", ddl);
        println("BEGIN", ddl);
        println("  DECLARE @" + tableNameVar + " nvarchar(256), @" + constraintNameVar
                + " nvarchar(256)", ddl);
        println("  DECLARE refcursor CURSOR FOR", ddl);
        println("  SELECT object_name(objs.parent_obj) tablename, objs.name constraintname", ddl);
        ddl.append("    FROM ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        ddl.append("dbo.sysobjects objs");
        ddl.append("    WHERE objs.xtype in ('C','D','F','UQ') AND object_name(objs.parent_obj) = ");
        printAlwaysSingleQuotedIdentifier(tableName, ddl);
        println("", ddl);
        println("  OPEN refcursor", ddl);
        println("  FETCH NEXT FROM refcursor INTO @" + tableNameVar + ", @" + constraintNameVar,
                ddl);
        println("  WHILE @@FETCH_STATUS = 0", ddl);
        println("    BEGIN", ddl);
        ddl.append("      EXEC ('ALTER TABLE ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        if (StringUtils.isNotBlank(schema)) {
            printIdentifier(schema, ddl);
            ddl.append(".");
        }
        ddl.append("'+@" + tableNameVar + "+' DROP CONSTRAINT '+@" + constraintNameVar + ")");
        println("", ddl);
        println("      FETCH NEXT FROM refcursor INTO @" + tableNameVar + ", @" + constraintNameVar,
                ddl);
        println("    END", ddl);
        println("  CLOSE refcursor", ddl);
        println("  DEALLOCATE refcursor", ddl);
        ddl.append("  DROP TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        println("", ddl);
        println("END", ddl);
        printEndOfStatement(ddl);
    }

    @Override
    public void dropExternalForeignKeys(Table table, StringBuilder ddl) {
        writeQuotationOnStatement(ddl);
        super.dropExternalForeignKeys(table, ddl);
    }

    @Override
    protected DateFormat getValueDateFormat() {
        return _genericDateFormat;
    }

    @Override
    protected DateFormat getValueTimeFormat() {
        return _genericTimeFormat;
    }

    @Override
    protected String getValueAsString(Column column, Object value) {
        if (value == null) {
            return "NULL";
        }
        StringBuilder result = new StringBuilder();
        switch (column.getMappedTypeCode()) {
            case Types.REAL:
            case Types.NUMERIC:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.DECIMAL:
                // SQL Server does not want quotes around the value
                if (!(value instanceof String) && (getValueNumberFormat() != null)) {
                    result.append(getValueNumberFormat().format(value));
                } else {
                    result.append(value);
                }
                break;
            case Types.DATE:
                result.append("CAST(");
                result.append(databaseInfo.getValueQuoteToken());
                result.append(value instanceof String ? (String) value
                        : getValueDateFormat()
                                .format(value));
                result.append(databaseInfo.getValueQuoteToken());
                result.append(" AS datetime)");
                break;
            case Types.TIME:
                result.append("CAST(");
                result.append(databaseInfo.getValueQuoteToken());
                result.append(value instanceof String ? (String) value
                        : getValueTimeFormat()
                                .format(value));
                result.append(databaseInfo.getValueQuoteToken());
                result.append(" AS datetime)");
                break;
            case Types.TIMESTAMP:
                result.append("CAST(");
                result.append(databaseInfo.getValueQuoteToken());
                result.append(value);
                result.append(databaseInfo.getValueQuoteToken());
                result.append(" AS datetime)");
                break;
        }
        return super.getValueAsString(column, value);
    }

    @Override
    protected String getNativeDefaultValue(Column column) {
        // Sql Server wants BIT default values as 0 or 1
        if ((column.getMappedTypeCode() == Types.BIT)
                || (PlatformUtils.supportsJava14JdbcTypes() && (column
                        .getMappedTypeCode() == PlatformUtils
                                .determineBooleanTypeCode()))) {
            String defaultValue = column.getDefaultValue();
            if ("NULL".equalsIgnoreCase(defaultValue)) {
                return defaultValue;
            }
            if ("'True'".equalsIgnoreCase(defaultValue)) {
                return "1";
            }
            return getDefaultValueHelper().convert(defaultValue, column.getMappedTypeCode(), Types.SMALLINT);
        }
        if ((column.getMappedTypeCode() == Types.TIMESTAMP) || (column.getMappedTypeCode() == Types.TIME) || (column.getMappedTypeCode() == Types.DATE)) {
            String defaultValue = super.getNativeDefaultValue(column);
            if (defaultValue != null && (defaultValue.equalsIgnoreCase("CURRENT_DATE") || defaultValue.equalsIgnoreCase("CURRENT DATE"))) {
                return "CURRENT_TIMESTAMP";
            }
        }
        return super.getNativeDefaultValue(column);
    }

    @Override
    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
        ddl.append("IDENTITY (1,1) ");
    }

    @Override
    protected void writeExternalForeignKeyDropStmt(Table table, ForeignKey foreignKey,
            StringBuilder ddl) {
        String constraintName = getForeignKeyName(table, foreignKey);
        String catalog = table.getCatalog();
        ddl.append("IF EXISTS (SELECT 1 FROM ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        ddl.append("sys.sysobjects WHERE type = 'F' AND name = ");
        printAlwaysSingleQuotedIdentifier(constraintName, ddl);
        println(")", ddl);
        printIndent(ddl);
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        ddl.append(" DROP CONSTRAINT ");
        printIdentifier(constraintName, ddl);
        printEndOfStatement(ddl);
    }

    /*
     * Returns the statement that turns on the ability to write delimited identifiers.
     * 
     * @return The quotation-on statement
     */
    private String getQuotationOnStatement() {
        if (delimitedIdentifierModeOn) {
            return "SET quoted_identifier on" + databaseInfo.getSqlCommandDelimiter() + "\n";
        } else {
            return "";
        }
    }

    /*
     * Writes the statement that turns on the ability to write delimited identifiers.
     */
    private void writeQuotationOnStatement(StringBuilder ddl) {
        ddl.append(getQuotationOnStatement());
    }

    @Override
    public String getSelectLastIdentityValues(Table table) {
        return "SELECT @@IDENTITY";
    }

    @Override
    public String getDeleteSql(Table table, Map<String, Object> pkValues, boolean genPlaceholders) {
        return getQuotationOnStatement() + super.getDeleteSql(table, pkValues, genPlaceholders);
    }

    @Override
    public String getInsertSql(Table table, Map<String, Object> columnValues,
            boolean genPlaceholders) {
        return getQuotationOnStatement() + super.getInsertSql(table, columnValues, genPlaceholders);
    }

    @Override
    public String getUpdateSql(Table table, Map<String, Object> columnValues,
            boolean genPlaceholders) {
        return getQuotationOnStatement() + super.getUpdateSql(table, columnValues, genPlaceholders);
    }

    /*
     * Prints the given identifier with enforced single quotes around it regardless of whether delimited identifiers are turned on or not.
     * 
     * @param identifier The identifier
     */
    protected void printAlwaysSingleQuotedIdentifier(String identifier, StringBuilder ddl) {
        ddl.append("'");
        ddl.append(identifier);
        ddl.append("'");
    }

    @Override
    public void writeCopyDataStatement(Table sourceTable, Table targetTable, StringBuilder ddl) {
        // Sql Server per default does not allow us to insert values explicitly
        // into
        // identity columns. However, we can change this behavior
        boolean hasIdentityColumns = targetTable.getAutoIncrementColumns().length > 0;
        if (hasIdentityColumns) {
            ddl.append("SET IDENTITY_INSERT ");
            ddl.append(getFullyQualifiedTableNameShorten(targetTable));
            ddl.append(" ON");
            printEndOfStatement(ddl);
        }
        super.writeCopyDataStatement(sourceTable, targetTable, ddl);
        // We have to turn it off ASAP because it can be on only for one table
        // per session
        if (hasIdentityColumns) {
            ddl.append("SET IDENTITY_INSERT ");
            ddl.append(getFullyQualifiedTableNameShorten(targetTable));
            ddl.append(" OFF");
            printEndOfStatement(ddl);
        }
    }

    @Override
    protected void processChanges(Database currentModel, Database desiredModel,
            List<IModelChange> changes, StringBuilder ddl) {
        if (!changes.isEmpty()) {
            writeQuotationOnStatement(ddl);
        }
        /*
         * For column data type and size changes, we need to drop and then re-create indexes and foreign keys using the column, as well as any primary keys
         * containg these columns However, if the index/foreign key/primary key is already slated for removal or change, then we don't want to generate change
         * duplication
         */
        HashSet<IIndex> removedIndexes = new HashSet<IIndex>();
        HashSet<ForeignKey> removedForeignKeys = new HashSet<ForeignKey>();
        HashSet<Table> removedPKs = new HashSet<Table>();
        for (Iterator<IModelChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            IModelChange change = changeIt.next();
            if (change instanceof RemoveIndexChange) {
                removedIndexes.add(((RemoveIndexChange) change).getIndex());
            } else if (change instanceof RemoveForeignKeyChange) {
                removedForeignKeys.add(((RemoveForeignKeyChange) change).getForeignKey());
            } else if (change instanceof RemovePrimaryKeyChange) {
                removedPKs.add(((RemovePrimaryKeyChange) change).getChangedTable());
            }
        }
        ArrayList<TableChange> additionalChanges = new ArrayList<TableChange>();
        for (Iterator<IModelChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            IModelChange change = changeIt.next();
            if ((change instanceof ColumnDataTypeChange) || (change instanceof ColumnSizeChange)) {
                Column column = ((ColumnChange) change).getChangedColumn();
                Table table = ((ColumnChange) change).getChangedTable();
                if (column.isPrimaryKey() && !removedPKs.contains(table)) {
                    Column[] pk = table.getPrimaryKeyColumnsInIndexOrder();
                    additionalChanges.add(new RemovePrimaryKeyChange(table, pk));
                    additionalChanges.add(new AddPrimaryKeyChange(table, pk));
                    removedPKs.add(table);
                }
                for (int idx = 0; idx < table.getIndexCount(); idx++) {
                    IIndex index = table.getIndex(idx);
                    if (index.hasColumn(column) && !removedIndexes.contains(index)) {
                        additionalChanges.add(new RemoveIndexChange(table, index));
                        additionalChanges.add(new AddIndexChange(table, index));
                        removedIndexes.add(index);
                    }
                }
                for (int tableIdx = 0; tableIdx < currentModel.getTableCount(); tableIdx++) {
                    Table curTable = currentModel.getTable(tableIdx);
                    for (int fkIdx = 0; fkIdx < curTable.getForeignKeyCount(); fkIdx++) {
                        ForeignKey curFk = curTable.getForeignKey(fkIdx);
                        if ((curFk.hasLocalColumn(column) || curFk.hasForeignColumn(column))
                                && !removedForeignKeys.contains(curFk)) {
                            additionalChanges.add(new RemoveForeignKeyChange(curTable, curFk));
                            additionalChanges.add(new AddForeignKeyChange(curTable, curFk));
                            removedForeignKeys.add(curFk);
                        }
                    }
                }
            }
        }
        changes.addAll(additionalChanges);
        super.processChanges(currentModel, desiredModel, changes, ddl);
    }

    @Override
    protected void processTableStructureChanges(Database currentModel, Database desiredModel,
            Table sourceTable, Table targetTable, List<TableChange> changes, StringBuilder ddl) {
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof ColumnAutoIncrementChange) {
                /*
                 * Sql Server has no way of adding or removing an IDENTITY constraint thus we have to rebuild the table anyway and can ignore all the other
                 * column changes
                 */
                return;
            }
        }
        // First we drop primary keys as necessary
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof RemovePrimaryKeyChange) {
                processChange(currentModel, desiredModel, (RemovePrimaryKeyChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof PrimaryKeyChange) {
                PrimaryKeyChange pkChange = (PrimaryKeyChange) change;
                RemovePrimaryKeyChange removePkChange = new RemovePrimaryKeyChange(
                        pkChange.getChangedTable(), pkChange.getOldPrimaryKeyColumns());
                processChange(currentModel, desiredModel, removePkChange, ddl);
            }
        }
        ArrayList<ColumnChange> columnChanges = new ArrayList<ColumnChange>();
        // Next we add/remove columns
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof AddColumnChange) {
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
            } else if ((change instanceof ColumnChange) && (columnChanges != null)) {
                /*
                 * We gather all changed columns because we can use the ALTER TABLE ALTER COLUMN statement for them
                 */
                columnChanges.add((ColumnChange) change);
            }
        }
        if (columnChanges != null) {
            HashSet<Column> processedColumns = new HashSet<Column>();
            for (Iterator<ColumnChange> changeIt = columnChanges.iterator(); changeIt.hasNext();) {
                ColumnChange change = changeIt.next();
                Column sourceColumn = change.getChangedColumn();
                if (!sourceColumn.isPrimaryKey()) {
                    Column targetColumn = targetTable.findColumn(sourceColumn.getName(),
                            delimitedIdentifierModeOn);
                    if (!processedColumns.contains(targetColumn)) {
                        processColumnChange(sourceTable, targetTable, sourceColumn, targetColumn,
                                (change instanceof ColumnDataTypeChange)
                                        || (change instanceof ColumnSizeChange), ddl);
                        processedColumns.add(targetColumn);
                    }
                    changes.remove(change);
                    change.apply(currentModel, delimitedIdentifierModeOn);
                } else {
                    log.debug("Cannot alter a primay key column on sql server (azure).  Just let the table rebuild.");
                }
            }
        }
        // Finally we add primary keys
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof AddPrimaryKeyChange) {
                processChange(currentModel, desiredModel, (AddPrimaryKeyChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof PrimaryKeyChange) {
                PrimaryKeyChange pkChange = (PrimaryKeyChange) change;
                AddPrimaryKeyChange addPkChange = new AddPrimaryKeyChange(
                        pkChange.getChangedTable(), pkChange.getNewPrimaryKeyColumns());
                processChange(currentModel, desiredModel, addPkChange, ddl);
                changeIt.remove();
            }
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
        ddl.append("ADD ");
        writeColumn(change.getChangedTable(), change.getNewColumn(), ddl);
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    /*
     * Processes the removal of a column from a table.
     */
    protected void processChange(Database currentModel, Database desiredModel,
            RemoveColumnChange change, StringBuilder ddl) {
        boolean hasDefault = change.getColumn().getParsedDefaultValue() != null;
        if (hasDefault) {
            dropDefaultConstraint(change.getChangedTable(), change.getColumn().getName(), ddl);
        }
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
        // TODO: this would be easier when named primary keys are supported
        // because then we can use ALTER TABLE DROP
        String catalog = change.getChangedTable().getCatalog();
        String schema = change.getChangedTable().getSchema();
        String tableName = getTableName(change.getChangedTable().getName());
        String tableNameVar = "tn" + createUniqueIdentifier();
        String constraintNameVar = "cn" + createUniqueIdentifier();
        println("BEGIN", ddl);
        println("  DECLARE @" + tableNameVar + " nvarchar(256), @" + constraintNameVar
                + " nvarchar(256)", ddl);
        println("  DECLARE refcursor CURSOR FOR", ddl);
        println("  SELECT parentobjs.name tablename, objs.name constraintname", ddl);
        ddl.append("    FROM ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("dbo.sysobjects objs", ddl);
        ddl.append("    JOIN ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("dbo.sysobjects parentobjs on objs.parent_obj=parentobjs.id", ddl);
        ddl.append("    WHERE objs.xtype = 'PK' AND parentobjs.name = ");
        printAlwaysSingleQuotedIdentifier(tableName, ddl);
        println("", ddl);
        println("  OPEN refcursor", ddl);
        println("  FETCH NEXT FROM refcursor INTO @" + tableNameVar + ", @" + constraintNameVar,
                ddl);
        println("  WHILE @@FETCH_STATUS = 0", ddl);
        println("    BEGIN", ddl);
        ddl.append("      EXEC ('ALTER TABLE ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        if (StringUtils.isNotBlank(schema)) {
            printIdentifier(schema, ddl);
            ddl.append(".");
        }
        ddl.append("'+@" + tableNameVar + "+' DROP CONSTRAINT '+@" + constraintNameVar + ")");
        println("", ddl);
        println("      FETCH NEXT FROM refcursor INTO @" + tableNameVar + ", @" + constraintNameVar,
                ddl);
        println("    END", ddl);
        println("  CLOSE refcursor", ddl);
        println("  DEALLOCATE refcursor", ddl);
        ddl.append("END");
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    protected void dropDefaultConstraint(Table table, String columnName, StringBuilder ddl) {
        println("BEGIN                                                                                        ", ddl);
        println("DECLARE @sql NVARCHAR(2000)                                                                  ", ddl);
        println(String.format("SELECT TOP 1 @sql = N'alter table \"%s\" drop constraint ['+objs.NAME+N']'                     ", table.getName()), ddl);
        println("FROM dbo.sysconstraints dc                                                              ", ddl);
        println("JOIN dbo.sysobjects objs                                                                          ", ddl);
        println("    ON objs.id = dc.constid                                                     ", ddl);
        println("JOIN sys.columns c                                                                           ", ddl);
        println("    ON c.default_object_id = dc.colid                                                    ", ddl);
        println("WHERE                                                                                        ", ddl);
        println(String.format("    dc.id = OBJECT_ID('%s')                                                    ", table.getName()), ddl);
        println(String.format("AND c.name = N'%s'                                                                           ", columnName), ddl);
        println("IF @@ROWCOUNT > 0                                                                            ", ddl);
        println("  EXEC (@sql)                                                                                ", ddl);
        println("END                                                                                          ", ddl);
        printEndOfStatement(ddl);
    }

    /*
     * Processes a change to a column.
     */
    protected void processColumnChange(Table sourceTable, Table targetTable, Column sourceColumn,
            Column targetColumn, boolean typeChange, StringBuilder ddl) {
        boolean hasDefault = sourceColumn.getParsedDefaultValue() != null;
        boolean shallHaveDefault = targetColumn.getParsedDefaultValue() != null;
        String newDefault = targetColumn.getDefaultValue();
        /*
         * Sql Server does not like it if there is a default spec in the ALTER TABLE ALTER COLUMN statement; thus we have to change the default manually
         */
        if (newDefault != null) {
            targetColumn.setDefaultValue(null);
        }
        if (hasDefault) {
            dropColumnChangeDefaults(sourceTable, sourceColumn, ddl);
        }
        /*
         * Cannot alter text to ntext or ntext to text directly. Have to alter to varchar(max) first.
         */
        if ((targetColumn.getMappedType().equalsIgnoreCase(TypeMap.LONGNVARCHAR) && sourceColumn.getJdbcTypeName().equalsIgnoreCase("text")) ||
                (targetColumn.getMappedType().equalsIgnoreCase(TypeMap.LONGVARCHAR) && sourceColumn.getJdbcTypeName().equalsIgnoreCase("ntext"))) {
            ddl.append("ALTER TABLE ");
            ddl.append(getFullyQualifiedTableNameShorten(sourceTable));
            printIndent(ddl);
            ddl.append("ALTER COLUMN ");
            printIdentifier(getColumnName(targetColumn), ddl);
            ddl.append(" varchar(max)");
            printEndOfStatement(ddl);
        }
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(sourceTable));
        printIndent(ddl);
        ddl.append("ALTER COLUMN ");
        writeColumnTypeDefaultRequired(sourceTable, targetColumn, ddl);
        printEndOfStatement(ddl);
        if (shallHaveDefault) {
            targetColumn.setDefaultValue(newDefault);
            /*
             * if the column shall have a default, then we have to add it as a constraint
             */
            ddl.append("ALTER TABLE ");
            ddl.append(getFullyQualifiedTableNameShorten(sourceTable));
            printIndent(ddl);
            ddl.append("ADD CONSTRAINT ");
            printIdentifier(getConstraintName("DF", sourceTable, sourceColumn.getName(), null), ddl);
            writeColumnDefaultValueStmt(sourceTable, targetColumn, ddl);
            ddl.append(" FOR ");
            printIdentifier(getColumnName(sourceColumn), ddl);
            printEndOfStatement(ddl);
        }
    }

    protected void dropColumnChangeDefaults(Table sourceTable, Column sourceColumn, StringBuilder ddl) {
        // we're dropping the old default
        String tableName = getTableName(sourceTable.getName());
        String columnName = getColumnName(sourceColumn);
        String tableNameVar = "tn" + createUniqueIdentifier();
        String constraintNameVar = "cn" + createUniqueIdentifier();
        println("BEGIN", ddl);
        println("  DECLARE @" + tableNameVar + " nvarchar(256), @" + constraintNameVar
                + " nvarchar(256)", ddl);
        println("  DECLARE refcursor CURSOR FOR", ddl);
        println("  SELECT object_name(cons.id) tablename, object_name(cons.constid) constraintname FROM dbo.sysconstraints cons ",
                ddl);
        println("    WHERE  cons.colid = (SELECT colid FROM syscolumns WHERE id = object_id(", ddl);
        printAlwaysSingleQuotedIdentifier(tableName, ddl);
        ddl.append(") AND name = ");
        printAlwaysSingleQuotedIdentifier(columnName, ddl);
        println(") AND", ddl);
        ddl.append("          object_name(cons.id) = ");
        printAlwaysSingleQuotedIdentifier(tableName, ddl);
        println("  OPEN refcursor", ddl);
        println("  FETCH NEXT FROM refcursor INTO @" + tableNameVar + ", @" + constraintNameVar,
                ddl);
        println("  WHILE @@FETCH_STATUS = 0", ddl);
        println("    BEGIN", ddl);
        println("      EXEC ('ALTER TABLE '+@" + tableNameVar + "+' DROP CONSTRAINT '+@"
                + constraintNameVar + ")", ddl);
        println("      FETCH NEXT FROM refcursor INTO @" + tableNameVar + ", @"
                + constraintNameVar, ddl);
        println("    END", ddl);
        println("  CLOSE refcursor", ddl);
        println("  DEALLOCATE refcursor", ddl);
        ddl.append("END");
        printEndOfStatement(ddl);
    }

    /**
     * Creates a reasonably unique identifier only consisting of hexadecimal characters and underscores. It looks like
     * <code>d578271282b42fce__2955b56e_107df3fbc96__8000</code> and is 48 characters long.
     * 
     * @return The identifier
     */
    protected String createUniqueIdentifier() {
        return new UID().toString().replace(':', '_').replace('-', '_');
    }

    @Override
    protected void filterColumnSqlType(StringBuilder sqlType) {
        int identityIndex = sqlType.indexOf("identity");
        if (identityIndex > 0) {
            sqlType.replace(identityIndex, sqlType.length(), "");
            int parensIndex = sqlType.indexOf("()");
            if (parensIndex > 0) {
                sqlType.replace(parensIndex, sqlType.length(), "");
            }
        }
        if (sqlType.toString().equalsIgnoreCase("varchar")) {
            sqlType.setLength(0);
            sqlType.append("varchar(max)");
        } else if (sqlType.toString().equalsIgnoreCase("varbinary")) {
            sqlType.setLength(0);
            sqlType.append("varbinary(max)");
        } else if (sqlType.toString().equalsIgnoreCase("nvarchar")) {
            sqlType.setLength(0);
            sqlType.append("nvarchar(max)");
        } else if (sqlType.toString().equalsIgnoreCase("nvarbinary")) {
            sqlType.setLength(0);
            sqlType.append("nvarbinary(max)");
        } else if (Strings.CI.contains(sqlType, "uniqueidentifier(")) {
            sqlType.setLength(0);
            sqlType.append("uniqueidentifier");
        } else if (Strings.CI.contains(sqlType, "sysname(")) {
            sqlType.setLength(0);
            sqlType.append("sysname");
        }
    }

    @Override
    protected void writeColumnDefaultValue(Table table, Column column, StringBuilder ddl) {
        String defaultValue = getNativeDefaultValue(column);
        PlatformColumn platformColumn = column.findPlatformColumn(databaseName);
        if (platformColumn != null && Strings.CI.contains(platformColumn.getType(), "uniqueidentifier")) {
            String defaultValueStr = mapDefaultValue(defaultValue, column);
            if (Strings.CI.contains(defaultValueStr, "NEWID()") || Strings.CI.contains(defaultValueStr, "NEWSEQUENTIALID()")) {
                ddl.append(defaultValueStr);
                return;
            }
        }
        printDefaultValue(defaultValue, column, ddl);
    }

    @Override
    protected boolean shouldUseQuotes(String defaultValue, Column column) {
        String defaultValueStr = mapDefaultValue(defaultValue, column);
        while (defaultValueStr != null && defaultValueStr.startsWith("(") && defaultValueStr.endsWith(")")) {
            defaultValueStr = defaultValueStr.substring(1, defaultValueStr.length() - 1);
        }
        return super.shouldUseQuotes(defaultValue, column) && !(defaultValueStr.trim().toUpperCase().startsWith("GETDATE(")
                || defaultValueStr.trim().toUpperCase().startsWith("GETUTCDATE("));
    }

    @Override
    protected void writeGeneratedColumn(Table table, Column column, StringBuilder ddl) {
        String definition = getDefinitionForGeneratedColumn(table, column);
        if (!StringUtils.isBlank(definition)) {
            printIdentifier(getColumnName(column), ddl);
            ddl.append(" AS ").append(definition);
        } else {
            writeColumnTypeDefaultRequired(table, column, ddl);
        }
    }

    @Override
    protected void writeCascadeAttributesForForeignKeyUpdate(ForeignKey key, StringBuilder ddl) {
        // MSSQL does not support ON UPDATE RESTRICT, but RESTRICT is just like NOACTION
        ForeignKeyAction original = key.getOnUpdateAction();
        if (key.getOnUpdateAction() == ForeignKeyAction.RESTRICT) {
            key.setOnUpdateAction(ForeignKeyAction.NOACTION);
        }
        super.writeCascadeAttributesForForeignKeyUpdate(key, ddl);
        key.setOnUpdateAction(original);
    }

    @Override
    protected void writeCascadeAttributesForForeignKeyDelete(ForeignKey key, StringBuilder ddl) {
        // MSSQL does not support ON DELETE RESTRICT, but RESTRICT is just like NOACTION
        ForeignKeyAction original = key.getOnDeleteAction();
        if (key.getOnDeleteAction() == ForeignKeyAction.RESTRICT) {
            key.setOnDeleteAction(ForeignKeyAction.NOACTION);
        }
        super.writeCascadeAttributesForForeignKeyDelete(key, ddl);
        key.setOnDeleteAction(original);
    }
}