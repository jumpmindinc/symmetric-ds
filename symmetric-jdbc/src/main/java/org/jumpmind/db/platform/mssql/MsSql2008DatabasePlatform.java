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
package org.jumpmind.db.platform.mssql;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.SqlTemplateSettings;

/*
 * The platform implementation for the Microsoft SQL Server 2008 database.
 */
public class MsSql2008DatabasePlatform extends MsSql2005DatabasePlatform {
    /*
     * Creates a new platform instance.
     */
    public MsSql2008DatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        supportsTruncate = true;
    }

    @Override
    protected IDdlBuilder createDdlBuilder() {
        return new MsSql2008DdlBuilder();
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.MSSQL2008;
    }

    @Override
    public long getEstimatedRowCount(Table table) {
        DatabaseInfo dbInfo = getDatabaseInfo();
        String quote = getDdlBuilder().isDelimitedIdentifierModeOn() ? dbInfo.getDelimiterToken() : "";
        String qualifiedName = table.getQualifiedTableName(quote, dbInfo.getCatalogSeparator(), dbInfo.getSchemaSeparator());
        String catalogPrefix = "";
        if (StringUtils.isNotBlank(table.getCatalog())) {
            catalogPrefix = (quote.length() > 0 ? quote + table.getCatalog() + quote : table.getCatalog()) + ".";
        }
        return getSqlTemplateDirty().queryForLong(
                "SELECT ISNULL(SUM(rows), -1) FROM " + catalogPrefix + "sys.partitions " +
                        "WHERE object_id = OBJECT_ID(?) AND index_id IN (0,1)",
                qualifiedName);
    }

    @Override
    public PermissionResult getLogMinePermission() {
        final PermissionResult result = new PermissionResult(PermissionType.LOG_MINE, "");
        try {
            if (getSqlTemplate().queryForInt("SELECT COUNT(*) FROM fn_my_permissions(NULL, 'SERVER') WHERE permission_name='ALTER ANY DATABASE'") > 0) {
                result.setStatus(Status.PASS);
            } else if (getSqlTemplate().queryForInt("SELECT COUNT(*) FROM sys.change_tracking_databases WHERE database_id=DB_ID()") > 0) {
                result.setStatus(Status.PASS);
            } else {
                result.setStatus(Status.FAIL);
                result.setSolution("Grant alter any database to this user. Or, enable change tracking for this database.");
            }
        } catch (Exception e) {
            result.setSolution("Error occurred checking user permissions");
            result.setException(e);
        }
        return result;
    }
}
