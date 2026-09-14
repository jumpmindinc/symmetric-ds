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
package org.jumpmind.db.platform.db2;

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

import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.AddPrimaryKeyChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.PrimaryKeyChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.RemovePrimaryKeyChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PlatformUtils;

/*
 * The DDL Builder for DB2.
 */
public class Db2DdlBuilder extends AbstractDdlBuilder {
    protected boolean migrateDataToRemoveColumn = false;

    public Db2DdlBuilder() {
        super(DatabaseNamesConstants.DB2);
        databaseInfo.addNativeTypeMapping(Types.ARRAY, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.BINARY, "CHAR {0} FOR BIT DATA");
        databaseInfo.addNativeTypeMapping(Types.BIT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "DOUBLE", Types.DOUBLE);
        databaseInfo.addNativeTypeMapping(Types.JAVA_OBJECT, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "LONG VARCHAR FOR BIT DATA");
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "LONG VARCHAR");
        databaseInfo.addNativeTypeMapping(Types.NULL, "LONG VARCHAR FOR BIT DATA",
                Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.NUMERIC, "DECIMAL", Types.DECIMAL);
        databaseInfo.addNativeTypeMapping(Types.OTHER, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.STRUCT, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping(Types.VARBINARY, "VARCHAR {0} FOR BIT DATA");
        databaseInfo.addNativeTypeMapping("BOOLEAN", "SMALLINT", "SMALLINT");
        databaseInfo.setHasSize(Types.TIMESTAMP, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPTZ, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPLTZ, true);
        databaseInfo.setDefaultSize(Types.CHAR, 254);
        databaseInfo.setDefaultSize(Types.VARCHAR, 254);
        databaseInfo.setDefaultSize(Types.BINARY, 254);
        databaseInfo.setDefaultSize(Types.VARBINARY, 254);
        databaseInfo.setDefaultSize(Types.TIMESTAMP, 6);
        databaseInfo.setDefaultSize(Types.TIME, 0);
        databaseInfo.setMaxSize("TIMESTAMP", 12);
        databaseInfo.setMaxIdentifierLength(128);
        databaseInfo.setMaxColumnNameLength(128);
        databaseInfo.setMaxConstraintNameLength(128);
        databaseInfo.setMaxForeignKeyNameLength(128);
        databaseInfo.setNonBlankCharColumnSpacePadded(true);
        databaseInfo.setBlankCharColumnSpacePadded(true);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
        databaseInfo.setBinaryQuoteStart("blob(X'");
        databaseInfo.setBinaryQuoteEnd("')");
        databaseInfo.setCanTriggerExistWithoutTable(true);
    }

    @Override
    protected String getNativeDefaultValue(Column column) {
        if ((column.getMappedTypeCode() == Types.BIT)
                || (PlatformUtils.supportsJava14JdbcTypes() && (column.getMappedTypeCode() == PlatformUtils
                        .determineBooleanTypeCode()))) {
            return getDefaultValueHelper().convert(column.getDefaultValue(),
                    column.getMappedTypeCode(), Types.SMALLINT);
        } else {
            return super.getNativeDefaultValue(column);
        }
    }

    @Override
    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
        ddl.append("GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1, NO CACHE, ORDER)");
    }

    @Override
    public String getSelectLastIdentityValues(Table table) {
        return "VALUES IDENTITY_VAL_LOCAL()";
    }

    @Override
    public void writeExternalIndexDropStmt(Table table, IIndex index, StringBuilder ddl) {
        // Index names in DB2 are unique to a schema and hence does not
        // use the ON <tablename> clause
        ddl.append("DROP INDEX ");
        printIdentifier(getIndexName(index), ddl);
        printEndOfStatement(ddl);
    }

    @Override
    protected void writeCastExpression(Column sourceColumn, Column targetColumn, StringBuilder ddl) {
        String sourceNativeType = getBareNativeType(sourceColumn);
        String targetNativeType = getBareNativeType(targetColumn);
        if (sourceNativeType.equals(targetNativeType)) {
            printIdentifier(getColumnName(sourceColumn), ddl);
        } else {
            String type = getSqlType(targetColumn);
            // DB2 has the limitation that it cannot convert numeric values
            // to VARCHAR, though it can convert them to CHAR
            if (TypeMap.isNumericType(sourceColumn.getMappedTypeCode())
                    && "VARCHAR".equalsIgnoreCase(targetNativeType)) {
                Object sizeSpec = targetColumn.getSize();
                if (sizeSpec == null) {
                    sizeSpec = databaseInfo.getDefaultSize(targetColumn.getMappedTypeCode());
                }
                type = "CHAR(" + sizeSpec + ")";
            }
            ddl.append("CAST(");
            printIdentifier(getColumnName(sourceColumn), ddl);
            ddl.append(" AS ");
            ddl.append(type);
            ddl.append(")");
        }
    }

    @Override
    protected void processTableStructureChanges(Database currentModel, Database desiredModel,
            Table sourceTable, Table targetTable, List<TableChange> changes, StringBuilder ddl) {
        // DB2 provides only limited ways to alter a column, so we don't use
        // them
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof AddColumnChange) {
                AddColumnChange addColumnChange = (AddColumnChange) change;
                /*
                 * DB2 does not allow the GENERATED BY DEFAULT AS IDENTITY clause in the ALTER TABLE ADD COLUMN statement, so we have to rebuild the table
                 * instead
                 */
                if (!addColumnChange.getNewColumn().isAutoIncrement()) {
                    processChange(currentModel, desiredModel, addColumnChange, ddl);
                    changeIt.remove();
                } else {
                    return;
                }
            }
        }
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof PrimaryKeyChange) {
                processChange(currentModel, desiredModel, (PrimaryKeyChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof RemovePrimaryKeyChange) {
                processChange(currentModel, desiredModel, (RemovePrimaryKeyChange) change, ddl);
                changeIt.remove();
            }
        }
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof RemoveColumnChange && !migrateDataToRemoveColumn) {
                processChange(currentModel, desiredModel, (RemoveColumnChange) change, ddl);
                changeIt.remove();
            } else if (change instanceof CopyColumnValueChange) {
                CopyColumnValueChange copyColumnChange = (CopyColumnValueChange) change;
                processChange(currentModel, desiredModel, copyColumnChange, ddl);
                changeIt.remove();
            }
        }
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof AddPrimaryKeyChange) {
                processChange(currentModel, desiredModel, (AddPrimaryKeyChange) change, ddl);
                changeIt.remove();
            }
        }
        super.processTableStructureChanges(currentModel, desiredModel, sourceTable, targetTable,
                changes, ddl);
    }

    @Override
    protected boolean writeAlterColumnDataTypeToBigInt(ColumnDataTypeChange change, StringBuilder ddl) {
        if (!change.getChangedColumn().isPrimaryKey()) {
            writeTableAlterStmt(change.getChangedTable(), ddl);
            ddl.append(" ALTER COLUMN ");
            Column column = change.getChangedColumn();
            column.setTypeCode(change.getNewTypeCode());
            printIdentifier(getColumnName(column), ddl);
            ddl.append(" SET DATA TYPE ");
            ddl.append(getSqlType(column));
            printEndOfStatement(ddl);
            return true;
        } else {
            return false;
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
        writeReorgStmt(change.getChangedTable(), ddl);
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

    @Override
    protected void writeExternalPrimaryKeysCreateStmt(Table table, Column[] primaryKeyColumns,
            StringBuilder ddl) {
        super.writeExternalPrimaryKeysCreateStmt(table, primaryKeyColumns, ddl);
        writeReorgStmt(table, ddl);
        printEndOfStatement(ddl);
    }

    protected void writeReorgStmt(Table table, StringBuilder ddl) {
        ddl.append("CALL SYSPROC.ADMIN_CMD('REORG TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        ddl.append("')");
    }

    @Override
    protected void writeCascadeAttributesForForeignKeyUpdate(ForeignKey key, StringBuilder ddl) {
        // DB2 only supports RESTRICT and NO ACTION for ON UPDATE
        if (key.getOnUpdateAction() == ForeignKeyAction.RESTRICT || key.getOnUpdateAction() == ForeignKeyAction.NOACTION) {
            super.writeCascadeAttributesForForeignKeyUpdate(key, ddl);
        }
    }

    @Override
    public String mapDefaultValue(Object defaultValue, Column column) {
        String defaultValueStr = super.mapDefaultValue(defaultValue, column);
        if (defaultValueStr.toLowerCase().equals("current_timestamp()")) {
            defaultValueStr = "current_timestamp(6)";
        }
        return defaultValueStr;
    }

    @Override
    protected void writeColumnType(Table table, Column column, StringBuilder ddl) {
        boolean isTimestampAutoUpdate = column.getMappedTypeCode() == Types.TIMESTAMP && column.isAutoUpdate();
        if (isTimestampAutoUpdate) {
            // DB2 allows for only one timestamp per table to be auto update
            for (Column col : table.getColumns()) {
                if (col.getMappedTypeCode() == Types.TIMESTAMP && col.isAutoUpdate()) {
                    isTimestampAutoUpdate = col.equals(column);
                    break;
                }
            }
        }
        if (isTimestampAutoUpdate) {
            ddl.append("generated by default for each row on update as row change timestamp not null");
        } else {
            super.writeColumnType(table, column, ddl);
        }
    }

    @Override
    protected void writeColumnDefaultValue(Table table, Column column, StringBuilder ddl) {
        if (!(column.getMappedTypeCode() == Types.TIMESTAMP && column.isAutoUpdate())) {
            printDefaultValue(getNativeDefaultValue(column), column, ddl);
        }
    }
}
