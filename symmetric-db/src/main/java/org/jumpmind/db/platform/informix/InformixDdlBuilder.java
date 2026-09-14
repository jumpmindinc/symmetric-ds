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
package org.jumpmind.db.platform.informix;

import java.sql.Types;
import java.util.Map;

import org.jumpmind.db.alter.PrimaryKeyChange;
import org.jumpmind.db.alter.RemovePrimaryKeyChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

public class InformixDdlBuilder extends AbstractDdlBuilder {
    public InformixDdlBuilder() {
        super(DatabaseNamesConstants.INFORMIX);
        databaseInfo.addNativeTypeMapping(Types.VARCHAR, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "LVARCHAR", Types.LONGVARCHAR);
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.TIMESTAMP, "DATETIME YEAR TO FRACTION", Types.TIMESTAMP);
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPTZ, "DATETIME YEAR TO FRACTION");
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPLTZ, "DATETIME YEAR TO FRACTION");
        databaseInfo.addNativeTypeMapping(Types.TIME, "DATETIME YEAR TO FRACTION", Types.TIMESTAMP);
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMETZ, "DATETIME YEAR TO FRACTION", Types.TIMESTAMP);
        databaseInfo.addNativeTypeMapping(Types.BINARY, "BYTE", Types.BINARY);
        databaseInfo.addNativeTypeMapping(Types.VARBINARY, "BYTE", Types.BINARY);
        databaseInfo.addNativeTypeMapping(Types.BIT, "BOOLEAN", Types.BOOLEAN);
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping(Types.DOUBLE, "FLOAT", Types.DOUBLE);
        databaseInfo.setDefaultSize(Types.VARCHAR, 255);
        databaseInfo.setDefaultSize(Types.CHAR, 255);
        databaseInfo.setDefaultSize(Types.TIMESTAMP, 5);
        databaseInfo.setHasSize(Types.TIMESTAMP, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPTZ, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPLTZ, true);
        databaseInfo.setHasSize(Types.TIME, true);
        databaseInfo.setHasSize(ColumnTypes.TIMETZ, true);
        databaseInfo.setMaxSize("DATETIME YEAR TO FRACTION", 5);
        databaseInfo.setMaxSize("INTERVAL", 5);
        databaseInfo.setAlterTableForDropUsed(true);
        databaseInfo.setSystemIndicesReturned(true);
        databaseInfo.setCatalogSeparator(":");
        databaseInfo.setNonBlankCharColumnSpacePadded(true);
        databaseInfo.setBlankCharColumnSpacePadded(true);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
        databaseInfo.setAutoIncrementUpdateAllowed(false);
        databaseInfo.setRequiresAutoCommitForDdl(true);
        Map<String, String> env = System.getenv();
        String clientIdentifierMode = env.get("DELIMIDENT");
        if (clientIdentifierMode != null && clientIdentifierMode.equalsIgnoreCase("y")) {
            databaseInfo.setDelimiterToken("\"");
            databaseInfo.setDelimitedIdentifiersSupported(true);
        } else {
            databaseInfo.setDelimiterToken("");
            databaseInfo.setDelimitedIdentifiersSupported(false);
            delimitedIdentifierModeOn = false;
        }
    }

    @Override
    public String getSqlType(Column column) {
        String sqlType = super.getSqlType(column);
        int typeCode = column.getMappedTypeCode();
        if (typeCode == Types.TIMESTAMP || typeCode == ColumnTypes.TIMESTAMPTZ || typeCode == ColumnTypes.TIMESTAMPLTZ || typeCode == Types.TIME) {
            sqlType = sqlType.replace("DATETIME YEAR TO FRACTION(0)", "DATETIME YEAR TO SECOND");
        }
        return sqlType;
    }

    @Override
    protected void writeColumn(Table table, Column column, StringBuilder ddl) {
        if (column.isAutoIncrement()) {
            printIdentifier(getColumnName(column), ddl);
            if (column.getMappedTypeCode() == Types.BIGINT) {
                ddl.append(" BIGSERIAL");
            } else {
                ddl.append(" SERIAL");
            }
        } else {
            super.writeColumn(table, column, ddl);
        }
    }

    @Override
    public String getSelectLastIdentityValues(Table table) {
        return "select dbinfo('sqlca.sqlerrd1') from sysmaster:sysdual";
    }

    @Override
    protected void writeExternalPrimaryKeysCreateStmt(Table table, Column primaryKeyColumns[],
            StringBuilder ddl) {
        if (primaryKeyColumns.length > 0 && shouldGeneratePrimaryKeys(primaryKeyColumns)) {
            ddl.append("ALTER TABLE ");
            ddl.append(getFullyQualifiedTableNameShorten(table));
            printIndent(ddl);
            ddl.append("ADD CONSTRAINT ");
            writePrimaryKeyStmt(table, primaryKeyColumns, ddl);
            ddl.append(" CONSTRAINT ");
            printIdentifier(getConstraintName(null, table, "PK", null), ddl);
            printEndOfStatement(ddl);
        }
    }

    protected void writeExternalForeignKeyCreateStmt(Database database, Table table,
            ForeignKey key, StringBuilder ddl) {
        if (key.getForeignTableName() == null) {
            log.warn("Foreign key table is null for key " + key);
        } else {
            writeTableAlterStmt(table, ddl);
            ddl.append("ADD CONSTRAINT FOREIGN KEY (");
            writeLocalReferences(key, ddl);
            ddl.append(") REFERENCES ");
            printIdentifier(getTableName(key.getForeignTableName()), ddl);
            ddl.append(" (");
            writeForeignReferences(key, ddl);
            ddl.append(")");
            writeCascadeAttributesForForeignKey(key, ddl);
            ddl.append(" CONSTRAINT ");
            printIdentifier(getForeignKeyName(table, key), ddl);
            printEndOfStatement(ddl);
        }
    }

    @Override
    protected void writeCascadeAttributesForForeignKeyUpdate(ForeignKey key, StringBuilder ddl) {
        // Informix does not support ON UPDATE
        return;
    }

    @Override
    protected void writeCascadeAttributesForForeignKeyDelete(ForeignKey key, StringBuilder ddl) {
        // Informix only supports ON DELETE CASCADE
        if (key.getOnDeleteAction() == ForeignKeyAction.CASCADE) {
            super.writeCascadeAttributesForForeignKeyDelete(key, ddl);
        }
    }

    protected void processChange(Database currentModel, Database desiredModel,
            RemovePrimaryKeyChange change, StringBuilder ddl) {
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(change.getChangedTable()));
        printIndent(ddl);
        ddl.append("DROP CONSTRAINT ");
        printIdentifier(getConstraintName(null, change.getChangedTable(), "PK", null), ddl);
        printEndOfStatement(ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }

    protected void processChange(Database currentModel, Database desiredModel,
            PrimaryKeyChange change, StringBuilder ddl) {
        ddl.append("ALTER TABLE ");
        ddl.append(getFullyQualifiedTableNameShorten(change.getChangedTable()));
        printIndent(ddl);
        ddl.append("DROP CONSTRAINT ");
        printIdentifier(getConstraintName(null, change.getChangedTable(), "PK", null), ddl);
        printEndOfStatement(ddl);
        writeExternalPrimaryKeysCreateStmt(change.getChangedTable(),
                change.getNewPrimaryKeyColumns(), ddl);
        change.apply(currentModel, delimitedIdentifierModeOn);
    }
}
