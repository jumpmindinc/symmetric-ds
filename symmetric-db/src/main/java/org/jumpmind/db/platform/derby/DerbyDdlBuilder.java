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
package org.jumpmind.db.platform.derby;

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

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.alter.AddColumnChange;
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
 * The SQL Builder for Derby.
 */
public class DerbyDdlBuilder extends AbstractDdlBuilder {
    public DerbyDdlBuilder() {
        super(DatabaseNamesConstants.DERBY);
        databaseInfo.setMaxIdentifierLength(128);
        databaseInfo.setSystemForeignKeyIndicesAlwaysNonUnique(true);
        databaseInfo.addNativeTypeMapping(Types.ARRAY, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.BINARY, "CHAR {0} FOR BIT DATA");
        databaseInfo.addNativeTypeMapping(Types.BIT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping(Types.DISTINCT, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.JAVA_OBJECT, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "LONG VARCHAR FOR BIT DATA");
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "CLOB", Types.CLOB);
        databaseInfo.addNativeTypeMapping(Types.NULL, "LONG VARCHAR FOR BIT DATA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.OTHER, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.REF, "LONG VARCHAR FOR BIT DATA", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.STRUCT, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping(Types.VARBINARY, "VARCHAR {0} FOR BIT DATA");
        databaseInfo.addNativeTypeMapping("BOOLEAN", "SMALLINT", "SMALLINT");
        databaseInfo.addNativeTypeMapping("DATALINK", "LONG VARCHAR FOR BIT DATA", "LONGVARBINARY");
        databaseInfo.addNativeTypeMapping(ColumnTypes.NVARCHAR, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.LONGNVARCHAR, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.NCHAR, "CHAR", Types.CHAR);
        databaseInfo.setDefaultSize(Types.BINARY, 254);
        databaseInfo.setDefaultSize(Types.CHAR, 254);
        databaseInfo.setDefaultSize(Types.VARBINARY, 254);
        databaseInfo.setDefaultSize(Types.VARCHAR, 254);
        databaseInfo.addNativeTypeMapping(Types.DOUBLE, "DOUBLE");
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "DOUBLE", Types.DOUBLE);
        databaseInfo.setMaxSize("TIMESTAMP", 6);
        databaseInfo.setNonBlankCharColumnSpacePadded(true);
        databaseInfo.setBlankCharColumnSpacePadded(true);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
    }

    @Override
    protected String getNativeDefaultValue(Column column) {
        if ((column.getMappedTypeCode() == Types.BIT)
                || (PlatformUtils.supportsJava14JdbcTypes() && (column.getMappedTypeCode() == PlatformUtils
                        .determineBooleanTypeCode()))) {
            return getDefaultValueHelper().convert(column.getDefaultValue(), column.getMappedTypeCode(),
                    Types.SMALLINT);
        } else {
            return super.getNativeDefaultValue(column);
        }
    }

    @Override
    public String getSelectLastIdentityValues(Table table) {
        return "VALUES IDENTITY_VAL_LOCAL()";
    }

    @Override
    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
        ddl.append("GENERATED BY DEFAULT AS IDENTITY");
    }

    @Override
    protected void writeGeneratedColumn(Table table, Column column, StringBuilder ddl) {
        String definition = getDefinitionForGeneratedColumn(table, column);
        if (!StringUtils.isBlank(definition)) {
            printIdentifier(getColumnName(column), ddl);
            if (!(definition.startsWith("(") && definition.endsWith(")"))) {
                ddl.append(" GENERATED ALWAYS AS ").append("(").append(definition).append(")");
            } else {
                ddl.append(" GENERATED ALWAYS AS ").append(definition);
            }
        } else {
            writeColumnTypeDefaultRequired(table, column, ddl);
        }
    }

    @Override
    public void writeExternalIndexDropStmt(Table table, IIndex index, StringBuilder ddl) {
        // Index names in Derby are unique to a schema and hence Derby does not
        // use the ON <tablename> clause
        ddl.append("DROP INDEX ");
        printIdentifier(getIndexName(index), ddl);
        printEndOfStatement(ddl);
    }

    @Override
    protected void writeCastExpression(Column sourceColumn, Column targetColumn, StringBuilder ddl) {
        String sourceNativeType = getBareNativeType(sourceColumn);
        String targetNativeType = getBareNativeType(targetColumn);
        if (sourceNativeType.equals(targetNativeType)
                || ((sourceNativeType.equalsIgnoreCase("CLOB") || sourceNativeType.equalsIgnoreCase("LONG VARCHAR"))
                        && (targetNativeType.equalsIgnoreCase("CHAR") || targetNativeType.equalsIgnoreCase("VARCHAR")
                                || targetNativeType.equalsIgnoreCase("LONG VARCHAR")))) {
            printIdentifier(getColumnName(sourceColumn), ddl);
        } else {
            // Derby currently has the limitation that it cannot convert numeric
            // values
            // to VARCHAR, though it can convert them to CHAR
            if (TypeMap.isNumericType(sourceColumn.getMappedTypeCode())
                    && "VARCHAR".equalsIgnoreCase(targetNativeType)) {
                targetNativeType = "CHAR";
            }
            ddl.append(targetNativeType);
            ddl.append("(");
            printIdentifier(getColumnName(sourceColumn), ddl);
            ddl.append(")");
        }
    }

    @Override
    protected void processTableStructureChanges(Database currentModel, Database desiredModel,
            Table sourceTable, Table targetTable, List<TableChange> changes, StringBuilder ddl) {
        // Derby provides a way to alter the size of a column but it is limited
        // (no pk or fk columns, only for VARCHAR columns), so we don't use it
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof AddColumnChange) {
                AddColumnChange addColumnChange = (AddColumnChange) change;
                // Derby can only add not insert columns, and the columns
                // cannot be identity columns
                if (addColumnChange.isAtEnd() && !addColumnChange.getNewColumn().isAutoIncrement()) {
                    processChange(currentModel, desiredModel, addColumnChange, ddl);
                    changeIt.remove();
                }
            }
        }
        super.processTableStructureChanges(currentModel, desiredModel, sourceTable, targetTable,
                changes, ddl);
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

    @Override
    protected void writeCascadeAttributesForForeignKeyUpdate(ForeignKey key, StringBuilder ddl) {
        // Derby does not support ON UPDATE SET DEFAULT
        if (key.getOnUpdateAction() != ForeignKeyAction.SETDEFAULT) {
            super.writeCascadeAttributesForForeignKeyUpdate(key, ddl);
        }
    }

    @Override
    protected void writeCascadeAttributesForForeignKeyDelete(ForeignKey key, StringBuilder ddl) {
        // Derby does not support ON DELETE SET DEFAULT
        if (key.getOnDeleteAction() != ForeignKeyAction.SETDEFAULT) {
            super.writeCascadeAttributesForForeignKeyDelete(key, ddl);
        }
    }
}
