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
package org.jumpmind.db.platform.db2;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;

public class Db2JdbcSqlTemplate extends JdbcSqlTemplate {
    public Db2JdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings,
            SymmetricLobHandler lobHandler, DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        primaryKeyViolationCodes = new int[] { -803 };
        foreignKeyViolationCodes = new int[] { -530 };
        foreignKeyChildExistsViolationCodes = new int[] { -531, -532 };
        deadlockCodes = new int[] { -911 };
    }

    @Override
    public String getSelectLastInsertIdSql(String sequenceName) {
        return "SELECT IDENTITY_VAL_LOCAL() FROM SYSIBM.SYSDUMMY1";
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
    public String getUniqueKeyViolationIndexName(Throwable ex) {
        String indexName = null;
        SQLException sqlEx = findSQLException(ex);
        if (sqlEx != null && sqlEx.getMessage().contains("SQLSTATE=23505, SQLERRMC=2;")) {
            // when SQLERRMC is 1, it seems to mean PK, while 2 means unique index
            // we don't know which unique key was violated, so we assume any/all of them
            indexName = "%";
        }
        return indexName;
    }
}
