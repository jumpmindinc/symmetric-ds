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
package org.jumpmind.db.platform.derby;

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

import java.sql.Types;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;

/*
 * The platform implementation for Derby.
 */
public class DerbyDatabasePlatform extends AbstractJdbcDatabasePlatform {
    /* The derby jdbc driver for use as a client for a normal server. */
    public static final String JDBC_DRIVER = "org.apache.derby.jdbc.ClientDriver";
    /* The derby jdbc driver for use as an embedded database. */
    public static final String JDBC_DRIVER_EMBEDDED = "org.apache.derby.jdbc.EmbeddedDriver";
    /* The subprotocol used by the derby drivers. */
    public static final String JDBC_SUBPROTOCOL = "derby";

    /*
     * Creates a new Derby platform instance.
     */
    public DerbyDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    protected DerbyDdlBuilder createDdlBuilder() {
        return new DerbyDdlBuilder();
    }

    @Override
    protected DerbyDdlReader createDdlReader() {
        return new DerbyDdlReader(this);
    }

    @Override
    protected DerbyJdbcSqlTemplate createSqlTemplate() {
        return new DerbyJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    @Override
    public boolean allowsUniqueIndexDuplicatesWithNulls() {
        return false;
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.DERBY;
    }

    @Override
    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = getSqlTemplate().queryForObject("values CURRENT SCHEMA", String.class);
        }
        return defaultSchema;
    }

    @Override
    public String getDefaultCatalog() {
        return "";
    }

    @Override
    public boolean isClob(Column column) {
        int type = column.getMappedTypeCode();
        return type == Types.CLOB;
    }

    @Override
    public boolean canColumnBeUsedInWhereClause(Column column) {
        return (!column.isOfBinaryType()) &&
                column.getJdbcTypeCode() != Types.CLOB &&
                column.getJdbcTypeCode() != Types.LONGVARCHAR &&
                column.getJdbcTypeCode() != Types.LONGNVARCHAR &&
                super.canColumnBeUsedInWhereClause(column);
    }

    @Override
    public PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        String triggerSql = "CREATE TRIGGER TEST_TRIGGER AFTER UPDATE ON " + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter
                + " FOR EACH ROW MODE DB2SQL INSERT INTO " + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter + " VALUES(NULL,NULL)";
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
        return sqlTemplate.getDatabaseMinorVersion() >= 4;
    }

    @Override
    public String massageForLimitOffset(String sql, int limit, int offset) {
        if (supportsLimitOffset()) {
            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1);
            }
            int minorVersion = sqlTemplate.getDatabaseMinorVersion();
            if (minorVersion >= 7) {
                if (minorVersion >= 9) {
                    return sql + " { limit " + limit + " offset " + offset + " }";
                }
                return sql + " offset " + offset + " rows fetch next " + limit + " rows only;";
            }
            int orderIndex = Strings.CI.lastIndexOf(sql, "order by");
            String order = sql.substring(orderIndex);
            String innerSql = sql.substring(0, orderIndex - 1);
            innerSql = Strings.CI.replace(innerSql, " from", ", ROW_NUMBER() over (" + order + ") as RowNum from");
            return "select * from (" + innerSql + ") " +
                    "where RowNum between " + (offset + 1) + " and " + (offset + limit);
        }
        return sql;
    }
}
