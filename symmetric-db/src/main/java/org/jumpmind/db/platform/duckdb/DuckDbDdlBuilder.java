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
package org.jumpmind.db.platform.duckdb;

import java.sql.Types;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

public class DuckDbDdlBuilder extends AbstractDdlBuilder {
    private static final String VARCHAR = "VARCHAR";

    public DuckDbDdlBuilder() {
        super(DatabaseNamesConstants.DUCKDB);
        databaseInfo.setTriggersSupported(false);
        databaseInfo.setForeignKeysSupported(false);
        databaseInfo.addNativeTypeMapping(Types.BIT, "BOOLEAN", Types.BOOLEAN);
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, VARCHAR, Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.LONGNVARCHAR, VARCHAR, Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.CLOB, VARCHAR, Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.CHAR, VARCHAR, Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.BINARY, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.VARBINARY, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.LONGVARBINARY, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "DOUBLE", Types.DOUBLE);
        databaseInfo.addNativeTypeMapping(Types.NUMERIC, "DECIMAL", Types.DECIMAL);
        databaseInfo.addNativeTypeMapping(Types.NCHAR, VARCHAR, Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.NVARCHAR, VARCHAR, Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(ColumnTypes.TIMESTAMPTZ, "TIMESTAMPTZ", ColumnTypes.TIMESTAMPTZ);
        databaseInfo.addNativeTypeMapping(Types.TIMESTAMP_WITH_TIMEZONE, "TIMESTAMPTZ", ColumnTypes.TIMESTAMPTZ);
        databaseInfo.setMaxSize("DECIMAL", 38);
        databaseInfo.setMaxSize("NUMERIC", 38);
        databaseInfo.setHasSize(Types.VARCHAR, false);
        databaseInfo.setHasSize(Types.CHAR, false);
        databaseInfo.setHasSize(Types.BINARY, false);
        databaseInfo.setHasSize(Types.VARBINARY, false);
        databaseInfo.setHasSize(Types.NCHAR, false);
        databaseInfo.setHasSize(Types.NVARCHAR, false);
        databaseInfo.setNonBlankCharColumnSpacePadded(false);
        databaseInfo.setBlankCharColumnSpacePadded(false);
    }

    @Override
    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
        ddl.append(" DEFAULT nextval('");
        if (StringUtils.isNotBlank(table.getSchema())) {
            printIdentifier(table.getSchema(), ddl);
            ddl.append(".");
        }
        printIdentifier(getConstraintName(null, table, column.getName(), "seq"), ddl);
        ddl.append("')");
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

    @Override
    protected void dropTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        ddl.append("DROP TABLE IF EXISTS ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        printEndOfStatement(ddl);
        if (!temporary && !recreate) {
            Column[] columns = table.getAutoIncrementColumns();
            for (int idx = 0; idx < columns.length; idx++) {
                dropAutoIncrementSequence(table, columns[idx], ddl);
            }
        }
    }

    private void createAutoIncrementSequence(Table table, Column column, StringBuilder ddl) {
        ddl.append("CREATE SEQUENCE ");
        if (StringUtils.isNotBlank(table.getSchema())) {
            printIdentifier(table.getSchema(), ddl);
            ddl.append(".");
        }
        printIdentifier(getConstraintName(null, table, column.getName(), "seq"), ddl);
        printEndOfStatement(ddl);
    }

    private void dropAutoIncrementSequence(Table table, Column column, StringBuilder ddl) {
        ddl.append("DROP SEQUENCE ");
        if (StringUtils.isNotBlank(table.getSchema())) {
            printIdentifier(table.getSchema(), ddl);
            ddl.append(".");
        }
        printIdentifier(getConstraintName(null, table, column.getName(), "seq"), ddl);
        printEndOfStatement(ddl);
    }
}
