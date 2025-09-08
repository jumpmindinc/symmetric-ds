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

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;

public class MsSqlDmlStatement extends DmlStatement {
    public MsSqlDmlStatement(DmlStatementOptions options) {
        super(options);
    }

    @Override
    protected String buildInsertSql(String tableName, Column[] keys, Column[] columns) {
        return databaseInfo.getCteExpression() != null
                ? databaseInfo.getCteExpression() + super.buildInsertSql(tableName, keys, columns)
                : super.buildInsertSql(tableName, keys, columns);
    }

    @Override
    protected String buildUpdateSql(String tableName, Column[] keyColumns, Column[] columns) {
        return databaseInfo.getCteExpression() != null
                ? databaseInfo.getCteExpression() + super.buildUpdateSql(tableName, keyColumns, columns)
                : super.buildUpdateSql(tableName, keyColumns, columns);
    }

    @Override
    protected String buildDeleteSql(String tableName, Column[] keyColumns) {
        return databaseInfo.getCteExpression() != null
                ? databaseInfo.getCteExpression() + super.buildDeleteSql(tableName, keyColumns)
                : super.buildDeleteSql(tableName, keyColumns);
    }

    @Override
    protected int getTypeCode(Column column, boolean isDateOverrideToTimestamp) {
        int type = column.getMappedTypeCode();
        if (type == Types.FLOAT || type == Types.REAL) {
            return Types.VARCHAR;
        } else if (type == ColumnTypes.MSSQL_SQL_VARIANT) {
            return Types.OTHER;
        } else {
            return super.getTypeCode(column, isDateOverrideToTimestamp);
        }
    }

    @Override
    protected void appendColumnParameter(StringBuilder sql, Column column) {
        if (column.getJdbcTypeName() != null && column.getJdbcTypeName().equals("datetime2") && column.getMappedTypeCode() == Types.VARCHAR) {
            sql.append("cast(? AS datetime2(7))").append(",");
        } else if ("datetimeoffset".equalsIgnoreCase(column.getJdbcTypeName())) {
            sql.append("cast(? AS datetimeoffset(7))").append(",");
        } else {
            super.appendColumnParameter(sql, column);
        }
    }

    @Override
    protected void appendColumnEquals(StringBuilder sql, Column column) {
        if (column.getJdbcTypeName() != null && column.getJdbcTypeName().equals("datetime2") && column.getMappedTypeCode() == Types.VARCHAR) {
            sql.append(quote).append(column.getName()).append(quote).append(" = cast(? AS datetime2(7))");
        } else if ("datetimeoffset".equalsIgnoreCase(column.getJdbcTypeName())) {
            sql.append(quote).append(column.getName()).append(quote).append(" = cast(? AS datetimeoffset(7))");
        } else {
            super.appendColumnEquals(sql, column);
        }
    }
}
