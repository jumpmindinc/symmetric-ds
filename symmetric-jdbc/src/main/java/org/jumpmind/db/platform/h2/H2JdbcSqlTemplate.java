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
package org.jumpmind.db.platform.h2;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;

public class H2JdbcSqlTemplate extends JdbcSqlTemplate {
    public H2JdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings,
            SymmetricLobHandler lobHandler, DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        primaryKeyViolationSqlStates = new String[] { "23001", "23505" };
        uniqueKeyViolationNameRegex = new String[] { "key violation: \"(.*) ON .* VALUES .*" };
        foreignKeyViolationCodes = new int[] { 23506 };
        foreignKeyChildExistsViolationCodes = new int[] { 23503 };
    }

    @Override
    public boolean supportsGetGeneratedKeys() {
        return false;
    }

    @Override
    protected boolean allowsNullForIdentityColumn() {
        return false;
    }

    @Override
    public String getSelectLastInsertIdSql(String sequenceName) {
        if (this.getDatabaseMajorVersion() == 2) {
            return "call CURRVAL('" + sequenceName + "_seq')";
        }
        return "call IDENTITY()";
    }

    @Override
    public ISqlTransaction startSqlTransaction(boolean autoCommit) {
        return new H2JdbcSqlTransaction(this, autoCommit);
    }

    @Override
    public ISqlTransaction startSqlTransaction() {
        return new H2JdbcSqlTransaction(this);
    }
}
