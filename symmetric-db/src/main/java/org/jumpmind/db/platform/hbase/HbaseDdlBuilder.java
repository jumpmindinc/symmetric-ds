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
package org.jumpmind.db.platform.hbase;

import java.sql.Types;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

public class HbaseDdlBuilder extends AbstractDdlBuilder {
    public HbaseDdlBuilder() {
        super(DatabaseNamesConstants.HBASE);
        databaseInfo.setHasSize(Types.TIMESTAMP, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPTZ, true);
        databaseInfo.setHasSize(ColumnTypes.TIMESTAMPLTZ, true);
        databaseInfo.setMaxSize("TIMESTAMP", 9);
    }

    @Override
    protected void writePrimaryKeyStmt(Table table, Column[] primaryKeyColumns, StringBuilder ddl) {
        ddl.append("CONSTRAINT NAME PRIMARY KEY (");
        for (int idx = 0; idx < primaryKeyColumns.length; idx++) {
            printIdentifier(getColumnName(primaryKeyColumns[idx]), ddl);
            if (idx < primaryKeyColumns.length - 1) {
                ddl.append(", ");
            }
        }
        ddl.append(")");
    }

    @Override
    public void writeCopyDataStatement(Table sourceTable, Table targetTable, LinkedHashMap<Column, Column> columnMap, StringBuilder ddl) {
        ddl.append("UPSERT INTO ");
        ddl.append(getFullyQualifiedTableNameShorten(targetTable));
        ddl.append(" (");
        for (Iterator<Column> columnIt = columnMap.values().iterator(); columnIt.hasNext();) {
            printIdentifier(getColumnName((Column) columnIt.next()), ddl);
            if (columnIt.hasNext()) {
                ddl.append(",");
            }
        }
        ddl.append(") SELECT ");
        for (Iterator<Map.Entry<Column, Column>> columnsIt = columnMap.entrySet().iterator(); columnsIt.hasNext();) {
            Map.Entry<Column, Column> entry = columnsIt.next();
            writeCastExpression((Column) entry.getKey(), (Column) entry.getValue(), ddl);
            if (columnsIt.hasNext()) {
                ddl.append(",");
            }
        }
        ddl.append(" FROM ");
        ddl.append(getFullyQualifiedTableNameShorten(sourceTable));
        printEndOfStatement(ddl);
    }
}
