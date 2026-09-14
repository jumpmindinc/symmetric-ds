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
package org.jumpmind.db.platform.greenplum;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.postgresql.PostgreSqlDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgresLobHandler;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;

public class GreenplumPlatform extends PostgreSqlDatabasePlatform {
    /* PostgreSql can be either PostgreSql or Greenplum. Metadata queries to determine which one */
    public static final String SQL_GET_GREENPLUM_COUNT = "select count(*) from information_schema.tables where table_name = 'gp_id'";
    public static final String SQL_GET_GREENPLUM_VERSION = "select productversion from gp_version_at_initdb";

    public GreenplumPlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    protected GreenplumDdlBuilder createDdlBuilder() {
        return new GreenplumDdlBuilder();
    }

    @Override
    protected GreenplumDdlReader createDdlReader() {
        return new GreenplumDdlReader(this);
    }

    @Override
    protected GreenplumJdbcSqlTemplate createSqlTemplate() {
        SymmetricLobHandler lobHandler = new PostgresLobHandler();
        return new GreenplumJdbcSqlTemplate(dataSource, settings, lobHandler, getDatabaseInfo());
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.GREENPLUM;
    }

    @Override
    public String getClassName() {
        return GreenplumPlatform.class.getName();
    }
}
