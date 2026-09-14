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
package org.jumpmind.db.platform.sqlite;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;

public class SqliteDmlStatement extends DmlStatement {
    public SqliteDmlStatement(DmlStatementOptions options) {
        super(options);
    }

    @Override
    protected String buildUpsertSql(String tableName, Column[] keyColumns, Column[] columns) {
        StringBuilder sql = new StringBuilder("insert or replace into " + tableName + " (");
        appendColumns(sql, columns, false);
        sql.append(") values (");
        appendColumnParameters(sql, columns);
        sql.append(")");
        return sql.toString();
    }

    @Override
    public boolean isUpsertSupported() {
        return true;
    }
}
