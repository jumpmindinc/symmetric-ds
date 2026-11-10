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
package org.jumpmind.db.platform.clickhouse;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.SqlTemplateSettings;

/**
 * ClickHouse as a load-only target. The JDBC driver reports the database as the schema and has no catalog.
 */
public class ClickHouseDatabasePlatform extends AbstractJdbcDatabasePlatform {
    public static final String JDBC_DRIVER = "com.clickhouse.jdbc.ClickHouseDriver";
    public static final String JDBC_SUBPROTOCOL = "clickhouse";
    public static final String JDBC_SUBPROTOCOL_SHORT = "ch";

    public ClickHouseDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    protected ClickHouseDdlBuilder createDdlBuilder() {
        return new ClickHouseDdlBuilder();
    }

    @Override
    protected ClickHouseDdlReader createDdlReader() {
        return new ClickHouseDdlReader(this);
    }

    @Override
    protected ClickHouseJdbcSqlTemplate createSqlTemplate() {
        return new ClickHouseJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    @Override
    protected ClickHouseJdbcSqlTemplate createSqlTemplateDirty() {
        // There are no isolation levels, so a dirty read is the same as a normal read
        return new ClickHouseJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    public String getName() {
        return DatabaseNamesConstants.CLICKHOUSE;
    }

    @Override
    public String getClassName() {
        return ClickHouseDatabasePlatform.class.getName();
    }

    public String getDefaultCatalog() {
        return null;
    }

    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = (String) getSqlTemplate().queryForObject("select currentDatabase()", String.class);
        }
        return defaultSchema;
    }

    @Override
    public PermissionResult getCreateSymTablePermission(Database database) {
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TABLE, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    public PermissionResult getDropSymTablePermission() {
        PermissionResult result = new PermissionResult(PermissionType.DROP_TABLE, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    public PermissionResult getAlterSymTablePermission(Database database) {
        PermissionResult result = new PermissionResult(PermissionType.ALTER_TABLE, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    public PermissionResult getDropSymTriggerPermission() {
        PermissionResult result = new PermissionResult(PermissionType.DROP_TRIGGER, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    public boolean supportsLimitOffset() {
        return true;
    }

    @Override
    public String massageForLimitOffset(String sql, int limit, int offset) {
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql + " limit " + limit + " offset " + offset;
    }
}
