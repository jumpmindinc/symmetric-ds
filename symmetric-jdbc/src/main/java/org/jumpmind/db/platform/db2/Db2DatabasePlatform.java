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
package org.jumpmind.db.platform.db2;

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

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;

/*
 * The DB2 platform implementation.
 */
public class Db2DatabasePlatform extends AbstractJdbcDatabasePlatform {
    /* The standard DB2 jdbc driver. */
    public static final String JDBC_DRIVER = "com.ibm.db2.jcc.DB2Driver";
    /* The subprotocol used by the standard DB2 driver. */
    public static final String JDBC_SUBPROTOCOL = "db2";
    protected int majorVersion;
    protected int minorVersion;

    /*
     * Creates a new platform instance.
     */
    public Db2DatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        majorVersion = sqlTemplate.getDatabaseMajorVersion();
        minorVersion = sqlTemplate.getDatabaseMinorVersion();
        if (majorVersion < 9 || (majorVersion == 9 && minorVersion < 7)) {
            supportsTruncate = false;
        }
    }

    @Override
    protected Db2DdlBuilder createDdlBuilder() {
        return new Db2DdlBuilder();
    }

    @Override
    protected Db2DdlReader createDdlReader() {
        return new Db2DdlReader(this);
    }

    @Override
    protected Db2JdbcSqlTemplate createSqlTemplate() {
        return new Db2JdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    @Override
    public boolean allowsUniqueIndexDuplicatesWithNulls() {
        return false;
    }

    public String getName() {
        return DatabaseNamesConstants.DB2;
    }

    @Override
    public String getClassName() {
        return Db2DatabasePlatform.class.getName();
    }

    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = (String) getSqlTemplate().queryForObject("select CURRENT SCHEMA from sysibm.sysdummy1", String.class);
        }
        return defaultSchema;
    }

    public String getDefaultCatalog() {
        return "";
    }

    @Override
    public boolean canColumnBeUsedInWhereClause(Column column) {
        return !column.isOfBinaryType() && super.canColumnBeUsedInWhereClause(column);
    }

    @Override
    public PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        String triggerSql = "CREATE TRIGGER TEST_TRIGGER AFTER UPDATE ON " + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter
                + " FOR EACH ROW BEGIN ATOMIC END";
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
    public String getTruncateSql(Table table) {
        String sql = super.getTruncateSql(table);
        sql += " reuse storage immediate";
        return sql;
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
        if (majorVersion >= 12 || (majorVersion == 11 && minorVersion >= 1)) {
            return sql + " limit " + limit + " offset " + offset;
        }
        int orderIndex = Strings.CI.lastIndexOf(sql, "order by");
        String order = sql.substring(orderIndex);
        String innerSql = sql.substring(0, orderIndex - 1);
        innerSql = Strings.CI.replace(innerSql, " from", ", ROW_NUMBER() over (" + order + ") as RowNum from");
        return "select * from (" + innerSql + ") " + "where RowNum between " + (offset + 1) + " and " + (offset + limit);
    }
}
