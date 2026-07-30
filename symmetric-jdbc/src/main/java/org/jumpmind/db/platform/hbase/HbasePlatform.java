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
package org.jumpmind.db.platform.hbase;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.platform.generic.GenericJdbcDatabasePlatform;
import org.jumpmind.db.sql.SqlTemplateSettings;

public class HbasePlatform extends GenericJdbcDatabasePlatform {
    public static final String JDBC_DRIVER = "com.sap.db.jdbc.Driver";
    public static final String JDBC_SUBPROTOCOL = "phoenix";

    public HbasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.HBASE;
    }

    @Override
    public String getClassName() {
        return HbasePlatform.class.getName();
    }

    @Override
    protected IDdlBuilder createDdlBuilder() {
        return new HbaseDdlBuilder();
    }

    @Override
    public IDdlBuilder getDdlBuilder() {
        return new HbaseDdlBuilder();
    }
}
