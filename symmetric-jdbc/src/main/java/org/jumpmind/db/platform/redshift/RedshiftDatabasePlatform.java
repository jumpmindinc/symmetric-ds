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
package org.jumpmind.db.platform.redshift;

import java.sql.Connection;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.SqlTemplateSettings;

public class RedshiftDatabasePlatform extends AbstractJdbcDatabasePlatform {
    public static final String JDBC_DRIVER = "com.amazon.redshift.jdbc42.Driver";
    public static final String JDBC_SUBPROTOCOL = "redshift";
    private Map<String, String> sqlScriptReplacementTokens;

    public RedshiftDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, overrideSettings(settings));
        sqlScriptReplacementTokens = super.getSqlScriptReplacementTokens();
        if (sqlScriptReplacementTokens == null) {
            sqlScriptReplacementTokens = new HashMap<String, String>();
        }
        sqlScriptReplacementTokens.put("current_timestamp", "sysdate");
    }

    protected static SqlTemplateSettings overrideSettings(SqlTemplateSettings settings) {
        if (settings == null) {
            settings = new SqlTemplateSettings();
        }
        settings.setQueryTimeout(0);
        return settings;
    }

    @Override
    protected RedshiftDdlBuilder createDdlBuilder() {
        return new RedshiftDdlBuilder();
    }

    @Override
    protected RedshiftDdlReader createDdlReader() {
        return new RedshiftDdlReader(this);
    }

    @Override
    protected RedshiftJdbcSqlTemplate createSqlTemplate() {
        return new RedshiftJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    @Override
    protected RedshiftJdbcSqlTemplate createSqlTemplateDirty() {
        RedshiftJdbcSqlTemplate sqlTemplateDirty = new RedshiftJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
        sqlTemplateDirty.setIsolationLevel(Connection.TRANSACTION_READ_UNCOMMITTED);
        return sqlTemplateDirty;
    }

    public String getName() {
        return DatabaseNamesConstants.REDSHIFT;
    }

    @Override
    public String getClassName() {
        return RedshiftDatabasePlatform.class.getName();
    }

    public String getDefaultCatalog() {
        return null;
    }

    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = (String) getSqlTemplate().queryForObject("select current_schema()", String.class);
        }
        return defaultSchema;
    }

    @Override
    public Map<String, String> getSqlScriptReplacementTokens() {
        return sqlScriptReplacementTokens;
    }

    public boolean isClob(int type) {
        return type == Types.CLOB;
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
