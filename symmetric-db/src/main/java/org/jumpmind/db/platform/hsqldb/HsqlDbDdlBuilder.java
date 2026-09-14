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
package org.jumpmind.db.platform.hsqldb;

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
import java.util.ListIterator;

import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

/*
 * The SQL Builder for the HsqlDb database.
 */
public class HsqlDbDdlBuilder extends AbstractDdlBuilder {
    public HsqlDbDdlBuilder() {
        super(DatabaseNamesConstants.HSQLDB);
        databaseInfo.setNonPKIdentityColumnsSupported(false);
        databaseInfo.setIdentityOverrideAllowed(false);
        databaseInfo.setSystemForeignKeyIndicesAlwaysNonUnique(true);
        databaseInfo.addNativeTypeMapping(Types.ARRAY, "LONGVARBINARY", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.BLOB, "LONGVARBINARY", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.CLOB, "LONGVARCHAR", Types.LONGVARCHAR);
        databaseInfo.addNativeTypeMapping(Types.DISTINCT, "LONGVARBINARY", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "DOUBLE", Types.DOUBLE);
        databaseInfo.addNativeTypeMapping(Types.JAVA_OBJECT, "OTHER");
        databaseInfo.addNativeTypeMapping(Types.NULL, "LONGVARBINARY", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.REF, "LONGVARBINARY", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.STRUCT, "LONGVARBINARY", Types.LONGVARBINARY);
        // JDBC's TINYINT requires a value range of -255 to 255, but HsqlDb's is
        // only -128 to 127
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping("BIT", "BOOLEAN", "BOOLEAN");
        databaseInfo.addNativeTypeMapping("DATALINK", "LONGVARBINARY", "LONGVARBINARY");
        databaseInfo.setDefaultSize(Types.CHAR, Integer.MAX_VALUE);
        databaseInfo.setDefaultSize(Types.VARCHAR, Integer.MAX_VALUE);
        databaseInfo.setDefaultSize(Types.BINARY, Integer.MAX_VALUE);
        databaseInfo.setDefaultSize(Types.VARBINARY, Integer.MAX_VALUE);
        databaseInfo.setDefaultSize(Types.TIMESTAMP, 3);
        databaseInfo.setDefaultSize(Types.TIME, 0);
        databaseInfo.setHasSize(Types.TIMESTAMP, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPTZ, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPLTZ, true);
        databaseInfo.setHasSize(Types.TIME, true);
        databaseInfo.setMaxSize("TIMESTAMP", 3);
        databaseInfo.setMaxSize("TIME", 3);
        databaseInfo.setNonBlankCharColumnSpacePadded(true);
        databaseInfo.setBlankCharColumnSpacePadded(true);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
    }

    @Override
    protected void dropTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        ddl.append("DROP TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        ddl.append(" IF EXISTS");
        printEndOfStatement(ddl);
    }

    @Override
    public String getSelectLastIdentityValues(Table table) {
        return "CALL IDENTITY()";
    }

    @Override
    protected void processTableStructureChanges(Database currentModel, Database desiredModel,
            Table sourceTable, Table targetTable, List<TableChange> changes, StringBuilder ddl) {
        // HsqlDb can only drop columns that are not part of a primary key
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if ((change instanceof RemoveColumnChange)
                    && ((RemoveColumnChange) change).getColumn().isPrimaryKey()) {
                return;
            }
        }
        // in order to utilize the ALTER TABLE ADD COLUMN BEFORE statement
        // we have to apply the add column changes in the correct order
        // thus we first gather all add column changes and then execute them
        // Since we get them in target table column order, we can simply
        // iterate backwards
        ArrayList<AddColumnChange> addColumnChanges = new ArrayList<AddColumnChange>();
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof AddColumnChange) {
                addColumnChanges.add((AddColumnChange) change);
                changeIt.remove();
            }
        }
        for (ListIterator<AddColumnChange> changeIt = addColumnChanges
                .listIterator(addColumnChanges.size()); changeIt.hasPrevious();) {
            AddColumnChange addColumnChange = (AddColumnChange) changeIt.previous();
            processChange(currentModel, desiredModel, addColumnChange, ddl);
            changeIt.remove();
        }
        for (Iterator<TableChange> changeIt = changes.iterator(); changeIt.hasNext();) {
            TableChange change = changeIt.next();
            if (change instanceof RemoveColumnChange) {
                RemoveColumnChange removeColumnChange = (RemoveColumnChange) change;
                processChange(currentModel, desiredModel, removeColumnChange, ddl);
                changeIt.remove();
            } else if (change instanceof CopyColumnValueChange) {
                CopyColumnValueChange copyColumnChange = (CopyColumnValueChange) change;
                processChange(currentModel, desiredModel, copyColumnChange, ddl);
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
        ddl.append("ADD COLUMN ");
        writeColumn(change.getChangedTable(), change.getNewColumn(), ddl);
        if (change.getNextColumn() != null) {
            ddl.append(" BEFORE ");
            printIdentifier(getColumnName(change.getNextColumn()), ddl);
        }
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
}
