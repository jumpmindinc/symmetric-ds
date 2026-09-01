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
package org.jumpmind.symmetric;

import java.util.List;

import org.jumpmind.db.platform.AbstractDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.DatabaseVersion;
import org.jumpmind.db.platform.IDatabasePlatform;

/*
 * Validates that a detected database platform which requires SymmetricDS Pro is not being
 * run against SymmetricDS open source.
 */
public class ProOnlyDatabaseValidator {
    private static final List<CloudDatabase> CLOUD_DATABASES = List.of(
            new CloudDatabase(DatabaseNamesConstants.AURORA_POSTGRESQL, "AWS Aurora PostgreSQL"),
            new CloudDatabase(DatabaseNamesConstants.AZURE_POSTGRESQL, "Azure Database for PostgreSQL"),
            new CloudDatabase(DatabaseNamesConstants.CLOUDSQL_POSTGRESQL, "Google Cloud SQL for PostgreSQL"),
            new CloudDatabase(DatabaseNamesConstants.AURORA_MYSQL, "AWS Aurora MySQL"),
            new CloudDatabase(DatabaseNamesConstants.CLOUDSQL_MYSQL, "Google Cloud SQL for MySQL"));
    private final IDatabasePlatform platform;

    public ProOnlyDatabaseValidator(IDatabasePlatform platform) {
        this.platform = platform;
    }

    public void validate() {
        DatabaseVersion dbVersion = platform.getDatabaseVersion();
        String dbVersionName = dbVersion != null ? dbVersion.getName() : null;
        for (CloudDatabase cloudDatabase : CLOUD_DATABASES) {
            cloudDatabase.checkIfFellBackToOpenSource(dbVersionName, platform.getName());
        }
        if (platform instanceof AbstractDatabasePlatform abstractDbPlatform && abstractDbPlatform.isDedicatedPlatform()) {
            return;
        }
        if (dbVersionName != null) {
            String nameLower = dbVersionName.toLowerCase();
            if (nameLower.startsWith(DatabaseNamesConstants.ORACLE) || nameLower.contains("sql server")) {
                throw new SymmetricException(
                        "The detected database platform '%s' is not supported in SymmetricDS open source. "
                                + "Some DB platforms, including Oracle and Microsoft SQL Server, require SymmetricDS Pro. "
                                + "Contact the SymmetricDS sales team for more information.",
                        dbVersionName);
            }
        }
    }

    private record CloudDatabase(String name, String vendorDescription) {
        void checkIfFellBackToOpenSource(String dbVersionName, String platformName) {
            if (name.equalsIgnoreCase(dbVersionName) && !name.equalsIgnoreCase(platformName)) {
                throw new SymmetricException(
                        "The detected database platform '%s' is not supported in SymmetricDS open source. "
                                + "%s requires SymmetricDS Pro. "
                                + "Contact the SymmetricDS sales team for more information.",
                        dbVersionName, vendorDescription);
            }
        }
    }
}
