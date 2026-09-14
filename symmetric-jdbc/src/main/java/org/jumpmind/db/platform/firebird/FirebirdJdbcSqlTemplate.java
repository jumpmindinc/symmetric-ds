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
package org.jumpmind.db.platform.firebird;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.ConcurrencySqlException;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;

public class FirebirdJdbcSqlTemplate extends JdbcSqlTemplate {
    public FirebirdJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings,
            SymmetricLobHandler lobHandler, DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        primaryKeyViolationCodes = new int[] { 335544665, 335544349 };
        uniqueKeyViolationNameRegex = new String[] { "unique index \"(.*?)\"" };
        foreignKeyViolationCodes = new int[] { 335544466 };
        foreignKeyChildExistsViolationMessageParts = new String[] { "Foreign key references are present for the record" };
        deadlockSqlStates = new String[] { "40001" };
    }

    @Override
    public boolean supportsReturningKeys() {
        return true;
    }

    @Override
    protected boolean allowsNullForIdentityColumn() {
        return true;
    }

    @Override
    public boolean isDataTruncationViolation(Throwable ex) {
        SQLException sqlEx = findSQLException(ex);
        return sqlEx != null && sqlEx.getMessage() != null && sqlEx.getMessage().contains("State:22001");
    }

    @Override
    public SqlException translate(String message, Throwable ex) {
        if (ex instanceof SQLException) {
            if (((SQLException) ex).getErrorCode() == 335544336) {
                throw new ConcurrencySqlException(message, ex);
            }
        }
        return super.translate(message, ex);
    }
}
