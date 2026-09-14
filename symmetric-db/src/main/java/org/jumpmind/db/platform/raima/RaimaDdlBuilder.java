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
package org.jumpmind.db.platform.raima;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.AddPrimaryKeyChange;
import org.jumpmind.db.alter.ColumnChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

public class RaimaDdlBuilder extends AbstractDdlBuilder {
    public RaimaDdlBuilder() {
        super(DatabaseNamesConstants.RAIMA);
        databaseInfo.setSystemForeignKeyIndicesAlwaysNonUnique(true);
        databaseInfo.setMaxIdentifierLength(128);
        databaseInfo.setNullAsDefaultValueRequired(true);
        databaseInfo.setDefaultValuesForLongTypesSupported(false);
        databaseInfo.setNonPKIdentityColumnsSupported(true);
        databaseInfo.setSyntheticDefaultValueForRequiredReturned(true);
        databaseInfo.setAlterTableForDropUsed(true);
        databaseInfo.setDelimiterToken("");
        databaseInfo.addNativeTypeMapping(Types.CHAR, "CHAR");
        databaseInfo.addNativeTypeMapping(Types.VARCHAR, "VARCHAR");
        databaseInfo.addNativeTypeMapping(Types.NCHAR, "WCHAR");
        databaseInfo.addNativeTypeMapping(Types.NVARCHAR, "WVARCHAR");
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "LONG VARCHAR");
        databaseInfo.addNativeTypeMapping(Types.CLOB, "CLOB");
        databaseInfo.addNativeTypeMapping(Types.LONGNVARCHAR, "LONG NVARCHAR");
        databaseInfo.addNativeTypeMapping(Types.NCLOB, "WCLOB");
        databaseInfo.addNativeTypeMapping(Types.BINARY, "BINARY");
        databaseInfo.addNativeTypeMapping(Types.VARBINARY, "VARBINARY");
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "LONG VARBINARY");
        databaseInfo.addNativeTypeMapping(Types.BLOB, "BLOB");
        databaseInfo.addNativeTypeMapping(Types.BIT, "BOOLEAN");
        databaseInfo.addNativeTypeMapping(Types.BOOLEAN, "BOOLEAN");
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "TINYINT");
        databaseInfo.addNativeTypeMapping(Types.SMALLINT, "SMALLINT");
        databaseInfo.addNativeTypeMapping(Types.INTEGER, "INTEGER");
        databaseInfo.addNativeTypeMapping(Types.BIGINT, "BIGINT");
        databaseInfo.addNativeTypeMapping(Types.REAL, "FLOAT");
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "FLOAT");
        databaseInfo.addNativeTypeMapping(Types.DOUBLE, "DOUBLE");
        databaseInfo.addNativeTypeMapping(Types.NUMERIC, "DECIMAL");
        databaseInfo.addNativeTypeMapping(Types.DECIMAL, "DECIMAL");
        databaseInfo.addNativeTypeMapping(Types.DATE, "DATE");
        databaseInfo.addNativeTypeMapping(Types.TIME, "TIME");
        databaseInfo.addNativeTypeMapping(Types.TIMESTAMP, "TIMESTAMP");
        databaseInfo.setDefaultSize(Types.CHAR, 254);
        databaseInfo.setDefaultSize(Types.VARCHAR, 254);
        databaseInfo.setDefaultSize(Types.BINARY, 254);
        databaseInfo.setDefaultSize(Types.VARBINARY, 254);
        databaseInfo.setMaxSize("TIMESTAMP", 4);
        databaseInfo.setNonBlankCharColumnSpacePadded(false);
        databaseInfo.setBlankCharColumnSpacePadded(false);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
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
    protected String getFullyQualifiedTableNameShorten(Table table) {
        String result = "";
        result += getDelimitedIdentifier(getTableName(table.getName()));
        return result;
    }

    @Override
    public boolean areColumnSizesTheSame(Column sourceColumn, Column targetColumn) {
        if (sourceColumn.getMappedType().equals("DECIMAL") && targetColumn.getMappedType().equals("DECIMAL")) {
            int targetSize = targetColumn.getSizeAsInt();
            int sourceSize = sourceColumn.getSizeAsInt();
            if (targetSize > 8 && sourceSize > 8 &&
                    targetColumn.getScale() == sourceColumn.getScale()) {
                return true;
            } else {
                return false;
            }
        } else {
            return super.areColumnSizesTheSame(sourceColumn, targetColumn);
        }
    }

    @Override
    protected void dropTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        ddl.append("DROP TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        printEndOfStatement(ddl);
    }

    protected void writeColumnType(Table table, Column column, StringBuilder ddl) {
        if (column.isAutoIncrement()) {
            ddl.append("ROWID NOT NULL");
        } else {
            super.writeColumnType(table, column, ddl);
        }
    }

    @Override
    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
    }

    @Override
    public String getSelectLastIdentityValues(Table table) {
        return "SELECT LAST_INSERT_ID()";
    }

    @Override
    protected void processTableStructureChanges(Database currentModel, Database desiredModel,
            Table sourceTable, Table targetTable, List<TableChange> changes, StringBuilder ddl) {
        List<Column> changedColumns = new ArrayList<Column>();
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
            } else if (change instanceof AddColumnChange) {
                processChange(currentModel, desiredModel, (AddColumnChange) change, ddl);
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
            }
        }
        for (Iterator<Column> columnIt = changedColumns.iterator(); columnIt.hasNext();) {
            Column sourceColumn = columnIt.next();
            Column targetColumn = targetTable.findColumn(sourceColumn.getName(),
                    delimitedIdentifierModeOn);
            processColumnChange(sourceTable, targetTable, sourceColumn, targetColumn, ddl);
        }
        super.processTableStructureChanges(currentModel, desiredModel, sourceTable, targetTable,
                changes, ddl);
    }

    @Override
    protected void writeExternalForeignKeyCreateStmt(Database database, Table table,
            ForeignKey key, StringBuilder ddl) {
        if (key.getForeignTableName() == null) {
            log.warn("Foreign key table is null for key " + key);
        } else {
            writeTableAlterStmt(table, ddl);
            ddl.append("ADD CONSTRAINT ");
            printIdentifier(getForeignKeyName(table, key), ddl);
            ddl.append(" FOREIGN KEY (");
            writeLocalReferences(key, ddl);
            ddl.append(") REFERENCES ");
            printIdentifier(getTableName(key.getForeignTableName()), ddl);
            ddl.append(" (");
            writeForeignReferences(key, ddl);
            ddl.append(")");
            writeCascadeAttributesForForeignKey(key, ddl);
            printEndOfStatement(ddl);
        }
    }

    @Override
    protected void writeExternalForeignKeyDropStmt(Table table, ForeignKey foreignKey,
            StringBuilder ddl) {
        writeTableAlterStmt(table, ddl);
        ddl.append("DROP CONSTRAINT ");
        printIdentifier(getForeignKeyName(table, foreignKey), ddl);
        printEndOfStatement(ddl);
    }

    @Override
    public void writeExternalIndexDropStmt(Table table, IIndex index, StringBuilder ddl) {
        writeTableAlterStmt(table, ddl);
        ddl.append("DROP INDEX ");
        printIdentifier(getIndexName(index), ddl);
        printEndOfStatement(ddl);
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
     * Processes the change of datatype to column.
     */
    protected void processChange(Database currentModel, Database desiredModel,
            ColumnDataTypeChange change, StringBuilder ddl) {
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(change.getChangedTable()));
        printIndent(ddl);
        ddl.append("ALTER COLUMN ");
        printIdentifier(getColumnName(change.getChangedColumn()), ddl);
        ddl.append("TYPE ");
        ddl.append(change.getChangedColumn().getMappedType());
        printEndOfStatement(ddl);
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
        boolean autoInc = targetColumn.isAutoIncrement();
        if (autoInc) {
            targetColumn.setAutoIncrement(false);
        }
        writeColumn(targetTable, targetColumn, ddl);
        if (autoInc) {
            targetColumn.setAutoIncrement(true);
        }
        printEndOfStatement(ddl);
    }

    @Override
    protected void writeCascadeAttributesForForeignKeyUpdate(ForeignKey key, StringBuilder ddl) {
        // Raima does not support ON UPDATE SET DEFAULT
        if (key.getOnUpdateAction() != ForeignKeyAction.SETDEFAULT) {
            super.writeCascadeAttributesForForeignKeyUpdate(key, ddl);
        }
    }

    @Override
    protected void writeCascadeAttributesForForeignKeyDelete(ForeignKey key, StringBuilder ddl) {
        // Raima does not support ON DELETE SET DEFAULT
        if (key.getOnDeleteAction() != ForeignKeyAction.SETDEFAULT) {
            super.writeCascadeAttributesForForeignKeyDelete(key, ddl);
        }
    }
}
