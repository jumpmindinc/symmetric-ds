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
package org.jumpmind.db.platform.sqlanywhere;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;

/*
 * The platform implementation for Sybase.
 */
public class SqlAnywhereDatabasePlatform extends AbstractJdbcDatabasePlatform {
    /* The standard Sybase jdbc driver. */
    public static final String JDBC_DRIVER = "com.sybase.jdbc4.jdbc.SybDriver";
    /* The old Sybase jdbc driver. */
    public static final String JDBC_DRIVER_OLD = "com.sybase.jdbc4.jdbc.SybDriver";
    /* The subprotocol used by the standard Sybase driver. */
    public static final String JDBC_SUBPROTOCOL = "sybase:Tds";
    public static final String JDBC_SUBPROTOCOL_SHORT = "sybase";
    /* The maximum size that text and binary columns can have. */
    public static final long MAX_TEXT_SIZE = 2147483647;
    private Map<String, String> sqlScriptReplacementTokens;

    public SqlAnywhereDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        sqlScriptReplacementTokens = super.getSqlScriptReplacementTokens();
        if (sqlScriptReplacementTokens == null) {
            sqlScriptReplacementTokens = new HashMap<String, String>();
        }
        sqlScriptReplacementTokens.put("current_timestamp", "getdate()");
    }

    @Override
    protected SqlAnywhereDdlBuilder createDdlBuilder() {
        return new SqlAnywhereDdlBuilder();
    }

    @Override
    protected SqlAnywhereDdlReader createDdlReader() {
        return new SqlAnywhereDdlReader(this);
    }

    @Override
    protected SqlAnywhereJdbcSqlTemplate createSqlTemplate() {
        return new SqlAnywhereJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    public String getName() {
        return DatabaseNamesConstants.SQLANYWHERE;
    }

    @Override
    public String getClassName() {
        return SqlAnywhereDatabasePlatform.class.getName();
    }

    public String getDefaultCatalog() {
        if (StringUtils.isBlank(defaultCatalog)) {
            defaultCatalog = getSqlTemplate().queryForObject("select DB_NAME()", String.class);
        }
        return defaultCatalog;
    }

    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = (String) getSqlTemplate().queryForObject("select USER_NAME()",
                    String.class);
        }
        return defaultSchema;
    }

    @Override
    public Map<String, String> getSqlScriptReplacementTokens() {
        return sqlScriptReplacementTokens;
    }

    @Override
    public PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        String triggerSql = "CREATE OR REPLACE TRIGGER TEST_TRIGGER AFTER UPDATE ON " + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter
                + " BEGIN SELECT 1 END";
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
    public boolean supportsLimitOffset() {
        return true;
    }

    @Override
    public String massageForLimitOffset(String sql, int limit, int offset) {
        return Strings.CI.replace(sql, "select", "select top " + limit + " start at " + (offset + 1));
    }
}
