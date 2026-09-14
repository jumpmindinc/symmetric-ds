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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.springframework.jdbc.core.StatementCreatorUtils;

public class PostgreSqlJdbcSqlTemplate extends JdbcSqlTemplate {
    public PostgreSqlJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings, SymmetricLobHandler lobHandler,
            DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        this.requiresAutoCommitFalseToSetFetchSize = true;
        primaryKeyViolationSqlStates = new String[] { "23000", "23505" };
        primaryKeyViolationMessageParts = new String[] { "duplicate key value violates", "duplicar valor da chave viola a restrição de unicidade" };
        uniqueKeyViolationNameRegex = new String[] { "violates unique constraint \"(.*)\"" };
        foreignKeyViolationMessageParts = new String[] { "violates foreign key constraint" };
        foreignKeyViolationSqlStates = new String[] { "23503" };
        foreignKeyChildExistsViolationMessageParts = new String[] { "is still referenced from table" };
        dataTruncationStates = new String[] { "22001" };
        deadlockSqlStates = new String[] { "40P01" };
        objectAlreadyExistsStates = new String[] { "42723", "42P05", "42P06", "42P07", "42712", "42710" };
        objectDoesNotExistStates = new String[] { "42883", "42P01", "42704", "42703" };
    }

    @Override
    public String getSelectLastInsertIdSql(String sequenceName) {
        if (PostgreSqlDdlBuilder.isUsePseudoSequence()) {
            return "select seq_id from " + sequenceName + "_tbl";
        } else {
            return "select currval('" + sequenceName + "_seq')";
        }
    }

    @Override
    protected boolean allowsNullForIdentityColumn() {
        return false;
    }

    @Override
    protected void setNanOrNull(PreparedStatement ps, int i, Object arg, int argType) throws SQLException {
        StatementCreatorUtils.setParameterValue(ps, i, Types.FLOAT, Float.NaN);
    }
}
