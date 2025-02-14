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

import java.sql.Types;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.DatabaseNamesConstants;

public class MsSql2005DdlBuilder extends MsSql2000DdlBuilder {
    public static final int NVARCHARMAX_LIMIT = 4000;
    public static final int VARCHARMAX_LIMIT = 8000;

    public MsSql2005DdlBuilder() {
        super();
        this.databaseName = DatabaseNamesConstants.MSSQL2005;
        databaseInfo.addNativeTypeMapping(Types.SQLXML, "XML", Types.SQLXML);
    }

    @Override
    protected void addLobMapping() {
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "VARBINARY(MAX)", Types.LONGVARBINARY);
        databaseInfo.addNativeTypeMapping(Types.BLOB, "VARBINARY(MAX)", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.NCLOB, "NVARCHAR(MAX)", Types.NCLOB);
        databaseInfo.addNativeTypeMapping(Types.CLOB, "VARCHAR(MAX)", Types.CLOB);
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "VARCHAR(MAX)", Types.LONGVARCHAR);
    }

    @Override
    protected void dropDefaultConstraint(Table table, String columnName, StringBuilder ddl) {
        String catalog = table.getCatalog();
        String schema = table.getSchema();
        println("BEGIN", ddl);
        println("DECLARE @sql NVARCHAR(2000)", ddl);
        ddl.append("SELECT @sql = N'alter table ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        if (StringUtils.isNotBlank(schema)) {
            printIdentifier(schema, ddl);
            ddl.append(".");
        }
        printIdentifier(table.getName(), ddl);
        println(" drop constraint ['+cons.NAME+N']'", ddl);
        ddl.append("from ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.default_constraints cons", ddl);
        ddl.append("join ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.syscolumns cols on cons.parent_object_id = cols.id and cons.parent_column_id = cols.colid", ddl);
        ddl.append("join ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.sysobjects objs on objs.id=cons.parent_object_id", ddl);
        ddl.append("join ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.schemas sch on sch.schema_id = objs.uid", ddl);
        println("WHERE cols.name='" + columnName + "' and objs.name='" + table.getName() + "' and sch.name='" + schema + "'", ddl);
        println("IF @@ROWCOUNT > 0", ddl);
        println("  EXEC (@sql)", ddl);
        println("END", ddl);
        printEndOfStatement(ddl);
    }

    @Override
    protected void dropColumnChangeDefaults(Table sourceTable, Column sourceColumn, StringBuilder ddl) {
        // we're dropping the old default
        String tableName = getTableName(sourceTable.getName());
        String columnName = getColumnName(sourceColumn);
        String tableNameVar = "tn" + createUniqueIdentifier();
        String constraintNameVar = "cn" + createUniqueIdentifier();
        String catalog = sourceTable.getCatalog();
        String schema = sourceTable.getSchema();
        println("BEGIN", ddl);
        println("  DECLARE @" + tableNameVar + " nvarchar(256), @" + constraintNameVar
                + " nvarchar(256)", ddl);
        println("  DECLARE refcursor CURSOR FOR", ddl);
        println("  select objs.name tablename, cons.name constraintname ", ddl);
        ddl.append("   from ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.default_constraints cons", ddl);
        ddl.append("join ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.sysobjects objs on cons.parent_object_id=objs.id", ddl);
        ddl.append("join ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.schemas sch on objs.uid=sch.schema_id", ddl);
        println("    where cons.parent_column_id=(", ddl);
        println("    SELECT colid", ddl);
        ddl.append("    FROM ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.syscolumns cols", ddl);
        ddl.append(" JOIN ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.sysobjects objs on objs.id=cols.id", ddl);
        ddl.append(" JOIN ");
        if (StringUtils.isNotBlank(catalog)) {
            printIdentifier(catalog, ddl);
            ddl.append(".");
        }
        println("sys.schemas sch on sch.schema_id=objs.uid", ddl);
        ddl.append("WHERE objs.name = ");
        printAlwaysSingleQuotedIdentifier(tableName, ddl);
        ddl.append(" and cols.name = ");
        printAlwaysSingleQuotedIdentifier(columnName, ddl);
        if (StringUtils.isNotBlank(schema)) {
            ddl.append(" AND sch.name=");
            printAlwaysSingleQuotedIdentifier(schema, ddl);
        }
        println(")", ddl);
        ddl.append(" AND objs.name=");
        printAlwaysSingleQuotedIdentifier(tableName, ddl);
        ddl.append(" AND sch.name=");
        printAlwaysSingleQuotedIdentifier(schema, ddl);
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
        println("      FETCH NEXT FROM refcursor INTO @" + tableNameVar + ", @"
                + constraintNameVar, ddl);
        println("    END", ddl);
        println("  CLOSE refcursor", ddl);
        println("  DEALLOCATE refcursor", ddl);
        ddl.append("END");
        printEndOfStatement(ddl);
    }

    @Override
    public String getSqlType(Column column) {
        String sqlType = super.getSqlType(column);
        boolean useVarcharForText = System.getProperty("mssql.use.varchar.for.lob", "false").equalsIgnoreCase("true");
        boolean useNvarChar = System.getProperty("mssql.use.ntypes.for.chars", "false").equalsIgnoreCase("true");
        if (column.getMappedTypeCode() == Types.VARBINARY && column.anyPlatformColumnTypeContains("hierarchyid")) {
            sqlType = "HIERARCHYID";
        } else if (column.getMappedTypeCode() == Types.VARBINARY && column.getSizeAsInt() > 8000) {
            sqlType = "VARBINARY(MAX)";
        } else if (column.getMappedTypeCode() == Types.VARCHAR && column.getSizeAsInt() > VARCHARMAX_LIMIT) {
            sqlType = "VARCHAR(MAX)";
        } else if (column.getMappedTypeCode() == Types.NVARCHAR && column.getSizeAsInt() > NVARCHARMAX_LIMIT) {
            sqlType = "NVARCHAR(MAX)";
        } else if (column.getMappedTypeCode() == Types.DECIMAL && column.getSizeAsInt() > 38) {
            sqlType = String.format("DECIMAL(38,%d)", column.getScale());
        } else if (useVarcharForText && (column.getMappedTypeCode() == Types.LONGVARCHAR || column.getMappedTypeCode() == Types.LONGNVARCHAR || column
                .getMappedTypeCode() == Types.CLOB)) {
            column.setMappedType(TypeMap.VARCHAR);
            column.setSize("10000"); // Ensure the size is set to max
            sqlType = (column.getMappedTypeCode() == Types.LONGNVARCHAR) ? "N" : "";
            sqlType += "VARCHAR(MAX)";
        }
        if (useNvarChar && column.getMappedTypeCode() == Types.VARCHAR) {
            int intColumnSize = 2 * column.getSizeAsInt(); // As every character in MSSQL takes at least 2 bytes in N-types, we have to double the size.
            String strColumnSize = String.valueOf(intColumnSize);
            if (intColumnSize > NVARCHARMAX_LIMIT) {
                strColumnSize = "MAX";
            }
            sqlType = String.format("NVARCHAR(%s)", strColumnSize);
        }
        return sqlType;
    }

    @Override
    protected void writeColumnType(Table table, Column column, StringBuilder ddl) {
        super.writeColumnType(table, column, ddl);
        PlatformColumn platformColumn = column.findPlatformColumn(databaseName);
        if (!column.isRequired() && platformColumn != null && platformColumn.isUserDefinedType() &&
                !(databaseInfo.isNullAsDefaultValueRequired() && databaseInfo.hasNullDefault(column.getMappedTypeCode()))) {
            ddl.append(" ");
            writeColumnNullableStmt(ddl);
        }
    }

    @Override
    protected void writeExternalIndexCreate(Table table, IIndex index, StringBuilder ddl) {
        super.writeExternalIndexCreate(table, index, ddl);
        if (index.getIncludedColumns() != null && index.getIncludedColumns().length > 0) {
            ddl.append(" INCLUDE (");
            IndexColumn[] includedColumns = index.getIncludedColumns();
            for (int i = 0; i < includedColumns.length; i++) {
                IndexColumn includedColumn = includedColumns[i];
                if (i > 0) {
                    ddl.append(", ");
                }
                ddl.append(includedColumn.getName());
            }
            ddl.append(")");
        }
    }
}
