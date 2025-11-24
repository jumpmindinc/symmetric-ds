/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.db.platform.mariadb;

import javax.sql.DataSource;

import org.apache.commons.lang3.Strings;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.mysql.MySqlDatabasePlatform;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlTemplateSettings;

public class MariaDBDatabasePlatform extends MySqlDatabasePlatform {
    public static final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
    public static final String JDBC_SUBPROTOCOL = "mariadb";
    public static final String SQL_GET_MARIADB_NAME = "select variable_value from information_schema.global_variables where variable_name='VERSION'";
    private static int originalFetchSize;

    public MariaDBDatabasePlatform(DataSource dataSource,
            SqlTemplateSettings settings) {
        super(dataSource, overrideSettings(settings));
        settings.setFetchSize(originalFetchSize);
    }

    @Override
    protected MariaDBDdlReader createDdlReader() {
        return new MariaDBDdlReader(this);
    }

    @Override
    public PermissionResult getLogMinePermission() {
        final PermissionResult result = new PermissionResult(PermissionType.LOG_MINE, "Use LogMiner");
        StringBuilder solution = new StringBuilder();
        Row row = getSqlTemplate().queryForRow("show variables like 'log_bin'");
        if (row == null || !Strings.CI.equals(row.getString("Value"), "ON")) {
            solution.append("Use the --log-bin option at startup. ");
        }
        row = getSqlTemplate().queryForRow("show variables like 'binlog_format'");
        if (row == null || !Strings.CI.equals(row.getString("Value"), "ROW")) {
            solution.append("Set the binlog_format system variable to \"ROW\". ");
        }
        if (solution.length() > 0) {
            result.setStatus(Status.FAIL);
            result.setSolution(solution.toString());
        } else {
            result.setStatus(Status.PASS);
        }
        return result;
    }

    @Override
    public String getCharSetName() {
        return (String) getSqlTemplate().queryForObject("SELECT CHARSET('a'), @@character_set_connection;", String.class);
    }

    protected static SqlTemplateSettings overrideSettings(SqlTemplateSettings settings) {
        if (settings == null) {
            settings = new SqlTemplateSettings();
        }
        originalFetchSize = settings.getFetchSize();
        return settings;
    }
}
