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
package org.jumpmind.db.platform.ingres;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;

public class IngresJdbcSqlTemplate extends JdbcSqlTemplate {
    public IngresJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings, SymmetricLobHandler lobHandler,
            DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        this.requiresAutoCommitFalseToSetFetchSize = true;
        primaryKeyViolationSqlStates = new String[] { "23501" };
        primaryKeyViolationMessageParts = new String[] { "Duplicate key on INSERT detected" };
        uniqueKeyViolationNameRegex = new String[] { "violates unique constraint \"(.*)\"" };
        foreignKeyViolationMessageParts = new String[] { "violates foreign key constraint" };
        foreignKeyViolationSqlStates = new String[] { "23503" };
        foreignKeyChildExistsViolationSqlStates = new String[] { "23504" };
        foreignKeyChildExistsViolationMessageParts = new String[] { "is still referenced from table" };
    }

    @Override
    public String getSelectLastInsertIdSql(String sequenceName) {
        if (IngresDdlBuilder.isUsePseudoSequence()) {
            return "select seq_id from " + sequenceName + "_tbl";
        } else {
            return "select getGeneratedKeys()";
        }
    }

    @Override
    public boolean isDataTruncationViolation(Throwable ex) {
        boolean dataTruncationViolation = false;
        SQLException sqlEx = findSQLException(ex);
        if (sqlEx != null) {
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null && sqlState.equals("22001")) {
                dataTruncationViolation = true;
            }
        }
        return dataTruncationViolation;
    }

    @Override
    protected boolean allowsNullForIdentityColumn() {
        return false;
    }
}
