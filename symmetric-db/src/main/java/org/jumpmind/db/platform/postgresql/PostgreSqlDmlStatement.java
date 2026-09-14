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
package org.jumpmind.db.platform.postgresql;

import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;

public class PostgreSqlDmlStatement extends DmlStatement {
    public PostgreSqlDmlStatement(DmlStatementOptions options) {
        super(options);
    }

    protected boolean allowIgnoreOnConflict = true;

    @Override
    public String buildInsertSql(String tableName, Column[] keyColumns, Column[] columns) {
        if (keyColumns != null && keyColumns.length > 0 && keyColumns[0] != null) {
            StringBuilder sql = new StringBuilder("insert into ");
            sql.append(tableName);
            sql.append("(");
            appendColumns(sql, columns, false);
            sql.append(") (select ");
            appendColumnParameters(sql, columns);
            sql.append(" where (select distinct 1 from ");
            sql.append(tableName);
            sql.append(" where  ");
            if (keyColumns == null || keyColumns.length == 0) {
                sql.append("1 != 1");
            } else {
                appendColumnsEquals(sql, keyColumns, " and ");
            }
            sql.append(") is null)");
            return sql.toString();
        } else {
            return super.buildInsertSql(tableName, keyColumns, columns);
        }
    }

    @Override
    public String getSql(boolean allowIgnoreOnConflict) {
        this.allowIgnoreOnConflict = allowIgnoreOnConflict;
        if (allowIgnoreOnConflict) {
            return sql;
        } else {
            return super.buildInsertSql(tableName, keys, columns);
        }
    }

    @Override
    public Column[] getMetaData() {
        if (dmlType == DmlType.INSERT && allowIgnoreOnConflict) {
            return getColumnKeyMetaData();
        } else {
            return super.getMetaData();
        }
    }

    @Override
    public <T> T[] getValueArray(T[] columnValues, T[] keyValues) {
        if (dmlType == DmlType.INSERT) {
            return (T[]) ArrayUtils.addAll(columnValues, keyValues);
        } else {
            return super.getValueArray(columnValues, keyValues);
        }
    }

    @Override
    public Object[] getValueArray(Map<String, Object> params) {
        Object[] args = null;
        int index = 0;
        if (params != null) {
            if (dmlType == DmlType.INSERT) {
                args = new Object[columns.length + keys.length];
                for (Column column : columns) {
                    args[index++] = params.get(column.getName());
                }
                for (Column column : keys) {
                    args[index++] = params.get(column.getName());
                }
            } else {
                args = super.getValueArray(params);
            }
        }
        return args;
    }

    @Override
    protected int[] buildTypes(Column[] keys, Column[] columns, boolean isDateOverrideToTimestamp) {
        if (dmlType == DmlType.INSERT) {
            int[] columnTypes = buildTypes(columns, isDateOverrideToTimestamp);
            int[] keyTypes = buildTypes(keys, isDateOverrideToTimestamp);
            return ArrayUtils.addAll(columnTypes, keyTypes);
        } else {
            return super.buildTypes(keys, columns, isDateOverrideToTimestamp);
        }
    }

    @Override
    protected void appendColumnParameter(StringBuilder sql, Column column) {
        String typeToCast = getTypeToCast(column);
        if (typeToCast != null) {
            sql.append("cast(? as ").append(typeToCast).append(")").append(",");
        } else if (column.getJdbcTypeName() != null && (column.getJdbcTypeName().toUpperCase().contains(TypeMap.GEOMETRY) ||
                column.getJdbcTypeName().toUpperCase().contains(TypeMap.GEOGRAPHY))) {
            sql.append("ST_GEOMFROMTEXT(?)").append(",");
        } else {
            super.appendColumnParameter(sql, column);
        }
    }

    @Override
    protected void appendColumnEquals(StringBuilder sql, Column column) {
        String typeToCast = getTypeToCast(column);
        if (typeToCast != null) {
            sql.append(quote).append(column.getName()).append(quote)
                    .append(" = cast(? as ").append(typeToCast).append(")");
        } else if (column.getJdbcTypeName() != null && (column.getJdbcTypeName().toUpperCase().contains(TypeMap.GEOMETRY) ||
                column.getJdbcTypeName().toUpperCase().contains(TypeMap.GEOGRAPHY))) {
            sql.append(quote).append(column.getName()).append(quote).append(" = ST_GEOMFROMTEXT(?)");
        } else {
            super.appendColumnEquals(sql, column);
        }
    }

    private String getTypeToCast(Column column) {
        String typeToCast = null;
        if (column.isTimestampWithTimezone()) {
            typeToCast = "timestamp with time zone";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.UUID)) {
            typeToCast = "uuid";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.VARBIT)) {
            typeToCast = "bit varying";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.INTERVAL)) {
            typeToCast = "interval";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.TSVECTOR)) {
            typeToCast = "tsvector";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.JSONB)) {
            typeToCast = "json";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.JSON)) {
            typeToCast = "json";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.INET)) {
            typeToCast = "inet";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.CIDR)) {
            typeToCast = "cidr";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.MACADDR8)) {
            typeToCast = "macaddr8";
        } else if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.MACADDR)) {
            typeToCast = "macaddr";
        }
        if (typeToCast != null && column.getMappedType() != null && column.getMappedType().equals(TypeMap.ARRAY)) {
            typeToCast = typeToCast + "[]";
        }
        return typeToCast;
    }

    @Override
    protected void appendColumnNameForSql(StringBuilder sql, Column column, boolean select) {
        String columnName = column.getName();
        if (select && column.isTimestampWithTimezone()) {
            sql.append(
                    "   case                                                                                                                                 " +
                            "   when extract(timezone_hour from ").append(quote).append(columnName).append(quote).append(
                                    ") < 0 then                                 " +
                                            "     to_char(").append(quote).append(columnName).append(quote).append(
                                                    ", 'YYYY-MM-DD HH24:MI:SS.US ')||'-'||                            " +
                                                            "     lpad(cast(abs(extract(timezone_hour from ").append(quote).append(columnName).append(quote)
                    .append(")) as varchar),2,'0')||':'||    " +
                            "     lpad(cast(extract(timezone_minute from ").append(quote).append(columnName).append(quote).append(
                                    ") as varchar), 2, '0')            " +
                                            "   else                                                                                                                                 "
                                            +
                                            "     to_char(").append(quote).append(columnName).append(quote).append(
                                                    ", 'YYYY-MM-DD HH24:MI:SS.US ')||'+'||                            " +
                                                            "     lpad(cast(extract(timezone_hour from ").append(quote).append(columnName).append(quote).append(
                                                                    ") as varchar),2,'0')||':'||         " +
                                                                            "     lpad(cast(extract(timezone_minute from ").append(quote).append(columnName)
                    .append(quote).append(") as varchar), 2, '0')            " +
                            "   end as ").append(columnName);
        } else {
            super.appendColumnNameForSql(sql, column, select);
        }
    }
}
