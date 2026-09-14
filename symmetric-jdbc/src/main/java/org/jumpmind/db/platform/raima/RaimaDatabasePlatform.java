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
package org.jumpmind.db.platform.raima;

import javax.sql.DataSource;

import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;

public class RaimaDatabasePlatform extends AbstractJdbcDatabasePlatform {
    public static final String JDBC_DRIVER = "com.raima.rdm.jdbc.RDMDriverr";
    public static final String JDBC_SUBPROTOCOL = "raima";

    public RaimaDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        supportsTruncate = false;
        getDatabaseInfo().setRequiresAutoCommitForDdl(true);
    }

    @Override
    protected RaimaDdlBuilder createDdlBuilder() {
        return new RaimaDdlBuilder();
    }

    @Override
    protected RaimaDdlReader createDdlReader() {
        return new RaimaDdlReader(this);
    }

    @Override
    protected RaimaJdbcSqlTemplate createSqlTemplate() {
        return new RaimaJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    @Override
    protected ISqlTemplate createSqlTemplateDirty() {
        return sqlTemplate;
    }

    public String getName() {
        return DatabaseNamesConstants.RAIMA;
    }

    @Override
    public String getClassName() {
        return RaimaDatabasePlatform.class.getName();
    }

    public String getDefaultSchema() {
        return null;
    }

    public String getDefaultCatalog() {
        return null;
    }

    @Override
    public PermissionResult getCreateSymTriggerPermission() {
        String createTriggerSql = "create trigger test_trigger after insert on " + PERMISSION_TEST_TABLE_NAME + " for each row begin atomic end";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TRIGGER, createTriggerSql);
        try {
            getSqlTemplate().update(createTriggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE DATABASE permission and TRIGGER permission");
        }
        return result;
    }

    @Override
    protected PermissionResult getDropSymTriggerPermission() {
        String dropTriggerSql = "drop trigger test_trigger";
        PermissionResult result = new PermissionResult(PermissionType.DROP_TRIGGER, dropTriggerSql);
        try {
            getSqlTemplate().update(dropTriggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE DATABASE permission and TRIGGER permission");
        }
        return result;
    }
}
