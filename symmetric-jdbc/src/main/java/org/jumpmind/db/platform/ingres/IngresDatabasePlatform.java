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
package org.jumpmind.db.platform.ingres;

import java.sql.Connection;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;

public class IngresDatabasePlatform extends AbstractJdbcDatabasePlatform {
    public static final String JDBC_DRIVER = "com.ingres.jdbc.IngresDriver";
    public static final String JDBC_SUBPROTOCOL = "ingres";

    public IngresDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.INGRES;
    }

    @Override
    public String getClassName() {
        return IngresDatabasePlatform.class.getName();
    }

    @Override
    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = (String) getSqlTemplate().queryForObject("SELECT DBMSINFO('SESSION_SCHEMA')", String.class);
        }
        return defaultSchema;
    }

    @Override
    public String getDefaultCatalog() {
        return null;
    }

    public SqlTemplateSettings getSettings() {
        return settings;
    }

    @Override
    protected IDdlBuilder createDdlBuilder() {
        return new IngresDdlBuilder();
    }

    @Override
    protected IDdlReader createDdlReader() {
        return new IngresDdlReader(this);
    }

    @Override
    protected IngresJdbcSqlTemplate createSqlTemplate() {
        SymmetricLobHandler lobHandler = new SymmetricLobHandler();
        return new IngresJdbcSqlTemplate(dataSource, settings, lobHandler, getDatabaseInfo());
    }

    @Override
    protected ISqlTemplate createSqlTemplateDirty() {
        IngresJdbcSqlTemplate sqlTemplateDirty = new IngresJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
        sqlTemplateDirty.setIsolationLevel(Connection.TRANSACTION_READ_UNCOMMITTED);
        return sqlTemplateDirty;
    }

    @Override
    protected PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        // Need the procedure in place in order for trigger to be successfully created
        getCreateSymRoutinePermission();
        String triggerSql = "CREATE TRIGGER TEST_TRIGGER after insert of " + PERMISSION_TEST_TABLE_NAME + " for each row execute procedure SYM_PROCEDURE_TEST";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TRIGGER, triggerSql);
        try {
            getSqlTemplate().update(triggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE TRIGGER permission and/or DROP TRIGGER permission");
        }
        return result;
    }

    @Override
    protected PermissionResult getDropSymTriggerPermission() {
        String dropTriggerSql = "DROP TRIGGER TEST_TRIGGER";
        PermissionResult result = new PermissionResult(PermissionType.DROP_TRIGGER, dropTriggerSql);
        try {
            getSqlTemplate().update(dropTriggerSql);
            result.setStatus(PermissionResult.Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
        }
        return result;
    }

    @Override
    protected PermissionResult getCreateSymRoutinePermission() {
        String procedureSql = "CREATE OR REPLACE PROCEDURE SYM_PROCEDURE_TEST AS DECLARE err INT; BEGIN err = 1; END";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_ROUTINE, procedureSql);
        ISqlTransaction transaction = null;
        try {
            transaction = getSqlTemplate().startSqlTransaction();
            transaction.execute(procedureSql);
            transaction.commit();
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            if (transaction != null) {
                transaction.rollback();
            }
            result.setException(e);
            result.setSolution("Grant CREATE PROCEDURE permission and/or DROP PROCEDURE permission");
        } finally {
            if (transaction != null) {
                transaction.close();
            }
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
        return sql + " offset " + offset + " fetch first " + limit + " rows only";
    }
}
