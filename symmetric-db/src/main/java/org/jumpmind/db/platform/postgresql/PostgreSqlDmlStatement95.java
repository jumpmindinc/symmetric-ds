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

import org.jumpmind.db.model.Column;
import org.jumpmind.db.sql.DmlStatementOptions;

public class PostgreSqlDmlStatement95 extends PostgreSqlDmlStatement {
    public static final String ON_CONFLICT_DO_NOTHING = "on conflict do nothing";

    public PostgreSqlDmlStatement95(DmlStatementOptions options) {
        super(options);
    }

    @Override
    public String buildInsertSql(String tableName, Column[] keys, Column[] columns) {
        StringBuilder sql = new StringBuilder("insert into " + tableName + " (");
        appendColumns(sql, columns, false);
        sql.append(") values (");
        appendColumnParameters(sql, columns);
        if (databaseInfo.isRequiresSavePointsInTransaction()) {
            sql.append(") ").append(ON_CONFLICT_DO_NOTHING);
        } else {
            sql.append(")");
        }
        return sql.toString();
    }

    @Override
    public String getSql(boolean allowIgnoreOnConflict) {
        if (allowIgnoreOnConflict) {
            return sql;
        } else {
            return sql.replace(ON_CONFLICT_DO_NOTHING, "");
        }
    }

    @Override
    public Column[] getMetaData() {
        if (dmlType == DmlType.INSERT) {
            return getColumns();
        } else {
            return super.getMetaData();
        }
    }

    @Override
    public <T> T[] getValueArray(T[] columnValues, T[] keyValues) {
        if (dmlType == DmlType.INSERT) {
            return columnValues;
        } else {
            return super.getValueArray(columnValues, keyValues);
        }
    }

    @Override
    public Object[] getValueArray(Map<String, Object> params) {
        if (dmlType == DmlType.INSERT) {
            int index = 0;
            Object[] args = new Object[columns.length];
            for (Column column : columns) {
                args[index++] = params.get(column.getName());
            }
            return args;
        }
        return super.getValueArray(params);
    }

    @Override
    protected int[] buildTypes(Column[] keys, Column[] columns, boolean isDateOverrideToTimestamp) {
        if (dmlType == DmlType.INSERT) {
            return buildTypes(columns, isDateOverrideToTimestamp);
        } else {
            return super.buildTypes(keys, columns, isDateOverrideToTimestamp);
        }
    }
}
