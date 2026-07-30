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
package org.jumpmind.db.platform.nuodb;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;

/*
 * The platform implementation for MySQL.
 */
public class NuoDbDatabasePlatform extends AbstractJdbcDatabasePlatform {
    /* The standard NuoDB jdbc driver. */
    public static final String JDBC_DRIVER = "com.nuodb.jdbc.Driver";
    /* The subprotocol used by the standard MySQL driver. */
    public static final String JDBC_SUBPROTOCOL = "nuodb";

    /*
     * Creates a new platform instance.
     */
    public NuoDbDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    protected NuoDbDdlBuilder createDdlBuilder() {
        return new NuoDbDdlBuilder();
    }

    @Override
    protected NuoDbDdlReader createDdlReader() {
        return new NuoDbDdlReader(this);
    }

    @Override
    protected NuoDbJdbcSqlTemplate createSqlTemplate() {
        return new NuoDbJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    @Override
    protected ISqlTemplate createSqlTemplateDirty() {
        return sqlTemplate;
    }

    public String getName() {
        return DatabaseNamesConstants.NUODB;
    }

    @Override
    public String getClassName() {
        return NuoDbDatabasePlatform.class.getName();
    }

    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = getSqlTemplate().queryForObject("select current_schema from system.dual", String.class);
        }
        return defaultSchema;
    }

    public String getDefaultCatalog() {
        return null;
    }

    @Override
    public PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        String triggerSql = "CREATE TRIGGER TEST_TRIGGER FOR" + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter
                + " AFTER UPDATE FOR EACH ROW AS INSERT INTO " + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter + " VALUES(NULL,NULL); END_TRIGGER";
        String dropTriggerSql = "DROP TRIGGER IF EXISTS TEST_TRIGGER";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TRIGGER,
                dropTriggerSql + "\r\n" + triggerSql + "\r\n" + dropTriggerSql);
        try {
            getSqlTemplate().update(dropTriggerSql);
            getSqlTemplate().update(triggerSql);
            getSqlTemplate().update(dropTriggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE TRIGGER permission or TRIGGER permission");
        }
        return result;
    }

    @Override
    public PermissionResult getCreateSymRoutinePermission() {
        String routineSql = "CREATE PROCEDURE TEST_PROC() AS VAR myVar = 1; END_PROCEDURE";
        String dropSql = "DROP PROCEDURE IF EXISTS TEST_PROC";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_ROUTINE,
                dropSql + "\r\n" + routineSql + "\r\n" + dropSql);
        try {
            getSqlTemplate().update(dropSql);
            getSqlTemplate().update(routineSql);
            getSqlTemplate().update(dropSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE ROUTINE Privilege");
        }
        return result;
    }

    @Override
    protected PermissionResult getDropSymTriggerPermission() {
        String dropTriggerSql = "DROP TRIGGER IF EXISTS TEST_TRIGGER";
        PermissionResult result = new PermissionResult(PermissionType.DROP_TRIGGER, dropTriggerSql);
        try {
            getSqlTemplate().update(dropTriggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant DROP TRIGGER permission or TRIGGER permission");
        }
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
