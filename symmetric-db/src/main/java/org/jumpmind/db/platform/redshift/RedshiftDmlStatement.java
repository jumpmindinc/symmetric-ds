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
package org.jumpmind.db.platform.redshift;

import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;

public class RedshiftDmlStatement extends DmlStatement {
    public RedshiftDmlStatement(DmlStatementOptions options) {
        super(options);
    }

    @Override
    public String buildInsertSql(String tableName, Column[] keyColumns, Column[] columns) {
        if (keyColumns != null && keyColumns.length > 0 && keyColumns[0] != null) {
            StringBuilder sql = new StringBuilder("insert into ");
            sql.append(tableName);
            sql.append("(");
            appendColumns(sql, columns, false);
            sql.append(") (select ");
            appendColumnParameters(sql, columns);
            sql.append(" where (select count(*) from ");
            sql.append(tableName);
            sql.append(" where  ");
            if (keyColumns == null || keyColumns.length == 0) {
                sql.append("1 != 1");
            } else {
                appendColumnsEquals(sql, keyColumns, " and ");
            }
            sql.append(") = 0)");
            return sql.toString();
        } else {
            return super.buildInsertSql(tableName, keyColumns, columns);
        }
    }

    @Override
    public Column[] getMetaData() {
        if (dmlType == DmlType.INSERT) {
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
}
