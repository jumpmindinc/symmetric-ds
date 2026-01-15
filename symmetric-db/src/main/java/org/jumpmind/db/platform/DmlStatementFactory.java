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
package org.jumpmind.db.platform;

import org.jumpmind.db.platform.ase.AseDmlStatement;
import org.jumpmind.db.platform.hbase.HbaseDmlStatement;
import org.jumpmind.db.platform.mysql.MySqlDmlStatement;
import org.jumpmind.db.platform.oracle.OracleDmlStatement;
import org.jumpmind.db.platform.postgresql.PostgreSqlDmlStatement;
import org.jumpmind.db.platform.postgresql.PostgreSqlDmlStatement95;
import org.jumpmind.db.platform.redshift.RedshiftDmlStatement;
import org.jumpmind.db.platform.sqlanywhere.SqlAnywhereDmlStatement;
import org.jumpmind.db.platform.sqlite.SqliteDmlStatement;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.jumpmind.util.AppUtils;

public class DmlStatementFactory implements IDmlStatementFactory {
    protected static IDmlStatementFactory instance;

    protected DmlStatementFactory() {
    }

    public static synchronized IDmlStatementFactory getInstance() {
        if (instance == null) {
            return AppUtils.newInstance(IDmlStatementFactory.class, DmlStatementFactory.class);
        }
        return instance;
    }

    @Override
    public DmlStatement create(String databaseName, DmlStatementOptions options) {
        if (DatabaseNamesConstants.ORACLE.equals(databaseName) || DatabaseNamesConstants.ORACLE122.equals(databaseName) || DatabaseNamesConstants.ORACLE23
                .equals(databaseName)) {
            return new OracleDmlStatement(options);
        } else if (DatabaseNamesConstants.POSTGRESQL.equals(databaseName) || (DatabaseNamesConstants.POSTGRESQL95.equals(databaseName) &&
                System.getProperty("postgres.use.on.conflict", "").equalsIgnoreCase("false"))) {
            return new PostgreSqlDmlStatement(options);
        } else if (DatabaseNamesConstants.POSTGRESQL95.equals(databaseName)) {
            return new PostgreSqlDmlStatement95(options);
        } else if (DatabaseNamesConstants.REDSHIFT.equals(databaseName)) {
            return new RedshiftDmlStatement(options);
        } else if (DatabaseNamesConstants.MYSQL.equals(databaseName)) {
            return new MySqlDmlStatement(options);
        } else if (DatabaseNamesConstants.SQLITE.equals(databaseName)) {
            return new SqliteDmlStatement(options);
        } else if (DatabaseNamesConstants.SQLANYWHERE.equals(databaseName)) {
            return new SqlAnywhereDmlStatement(options);
        } else if (DatabaseNamesConstants.HBASE.equals(databaseName)) {
            return new HbaseDmlStatement(options);
        } else if (DatabaseNamesConstants.ASE.equals(databaseName)) {
            return new AseDmlStatement(options);
        } else {
            return new DmlStatement(options);
        }
    }
}
