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
package org.jumpmind.db.util;

import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.Row;

public class TableRow {
    Table table;
    Row row;
    String whereSql;
    String referenceColumnName;
    String fkName;
    String fkColumnValues = null;

    public TableRow(Table table, Row row, String whereSql, String referenceColumnName, String fkName) {
        this.table = table;
        this.row = row;
        this.whereSql = whereSql;
        this.referenceColumnName = referenceColumnName;
        this.fkName = fkName;
    }

    protected String getFkColumnValues() {
        if (fkColumnValues == null) {
            StringBuilder builder = new StringBuilder();
            ForeignKey[] keys = table.getForeignKeys();
            for (ForeignKey foreignKey : keys) {
                if (foreignKey.getName().equals(fkName)) {
                    Reference[] refs = foreignKey.getReferences();
                    for (Reference ref : refs) {
                        Object value = row.get(ref.getLocalColumnName());
                        if (value != null) {
                            builder.append("\"").append(value).append("\",");
                        } else {
                            builder.append("null,");
                        }
                    }
                }
            }
            fkColumnValues = builder.toString();
        }
        return fkColumnValues;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((table == null) ? 0 : table.hashCode());
        result = prime * result + ((whereSql == null) ? 0 : whereSql.hashCode());
        result = prime * result + ((getFkColumnValues() == null) ? 0 : getFkColumnValues().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TableRow) {
            TableRow tr = (TableRow) o;
            return tr.table.equals(table) && tr.whereSql.equals(whereSql)
                    && tr.getFkColumnValues().equals(getFkColumnValues());
        }
        return false;
    }

    @Override
    public String toString() {
        return table.getFullyQualifiedName() + ":" + whereSql + ":" + getFkColumnValues();
    }

    public Table getTable() {
        return table;
    }

    public Row getRow() {
        return row;
    }

    public String getWhereSql() {
        return whereSql;
    }

    public String getReferenceColumnName() {
        return referenceColumnName;
    }

    public String getFkName() {
        return fkName;
    }
}
