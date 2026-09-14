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
package org.jumpmind.symmetric.db.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.IConnectionCallback;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.symmetric.service.IParameterService;
import org.sqlite.Function;
import org.sqlite.SQLiteConnection;

public class SqliteJdbcSymmetricDialect extends SqliteSymmetricDialect {
    public SqliteJdbcSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
    }

    @Override
    protected void setSqliteFunctionResult(ISqlTransaction transaction, final String name, final String result) {
        JdbcSqlTransaction trans = (JdbcSqlTransaction) transaction;
        trans.executeCallback(new IConnectionCallback<Object>() {
            @Override
            public Object execute(Connection con) throws SQLException {
                SQLiteConnection unwrapped = con.unwrap(SQLiteConnection.class);
                Function.create(unwrapped, name, new Function() {
                    @Override
                    protected void xFunc() throws SQLException {
                        this.result(result);
                    }
                });
                return null;
            }
        });
    }
}
