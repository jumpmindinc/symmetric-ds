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
package org.jumpmind.db.platform.sqlite;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;

public class SqliteDatabasePlatform extends AbstractJdbcDatabasePlatform implements
        IDatabasePlatform {
    public static final String JDBC_DRIVER = "org.sqlite.JDBC";
    public static final String JDBC_SUBPROTOCOL = "sqlite";
    private Map<String, String> sqlScriptReplacementTokens;

    public SqliteDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        sqlScriptReplacementTokens = super.getSqlScriptReplacementTokens();
        if (sqlScriptReplacementTokens == null) {
            sqlScriptReplacementTokens = new HashMap<String, String>();
        }
        sqlScriptReplacementTokens.put("current_timestamp",
                "strftime('%Y-%m-%d %H:%M:%f','now','localtime')");
        sqlScriptReplacementTokens.put("\\{ts([^<]*?)\\}", "$1");
        sqlScriptReplacementTokens.put("\\{d([^<]*?)\\}", "$1");
        supportsTruncate = false;
        getDatabaseInfo().setJdbcTimestampAllowed(false);
    }

    @Override
    public Map<String, String> getSqlScriptReplacementTokens() {
        return sqlScriptReplacementTokens;
    }

    public String getName() {
        return DatabaseNamesConstants.SQLITE;
    }

    @Override
    public String getClassName() {
        return SqliteDatabasePlatform.class.getName();
    }

    public String getDefaultSchema() {
        return null;
    }

    public String getDefaultCatalog() {
        return null;
    }

    @Override
    protected SqliteDdlBuilder createDdlBuilder() {
        return new SqliteDdlBuilder();
    }

    @Override
    protected SqliteDdlReader createDdlReader() {
        return new SqliteDdlReader(this);
    }

    protected ISqlTemplate createSqlTemplate() {
        return new SqliteJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    protected ISqlTemplate createSqlTemplateDirty() {
        JdbcSqlTemplate sqlTemplateDirty = new SqliteJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
        sqlTemplateDirty.setIsolationLevel(Connection.TRANSACTION_READ_UNCOMMITTED);
        return sqlTemplateDirty;
    }

    @Override
    public DmlStatement createDmlStatement(DmlStatementOptions options) {
        return new SqliteDmlStatement(options.databaseInfo(getDatabaseInfo()));
    }

    @Override
    protected String getDateTimeStringValue(String name, int type, Row row, boolean useVariableDates) {
        return row.getString(name);
    }

    @Override
    protected Object parseBigDecimal(String value) {
        /* sqlite allows blank data in integer fields */
        if ("".equals(value)) {
            return value;
        } else {
            return super.parseBigDecimal(value);
        }
    }

    @Override
    protected Object parseBigInteger(String value) {
        /* sqlite allows blank data in integer fields */
        if ("".equals(value)) {
            return value;
        } else {
            return super.parseBigInteger(value);
        }
    }

    @Override
    protected Object parseInteger(String value) {
        /* sqlite allows blank data in integer fields */
        if ("".equals(value)) {
            return value;
        } else {
            return super.parseInteger(value);
        }
    }

    @Override
    protected PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        String triggerSql = "CREATE TRIGGER TEST_TRIGGER AFTER UPDATE ON " + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter
                + "FOR EACH ROW BEGIN SELECT 1; END";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TRIGGER, triggerSql);
        try {
            getSqlTemplate().update(triggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE TRIGGER permission or TRIGGER permission");
        }
        return result;
    }

    @Override
    public boolean supportsMultiThreadedTransactions() {
        return false;
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
