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
package org.jumpmind.db.platform.sybase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.springframework.jdbc.core.SqlTypeValue;

public class SybaseJdbcSqlTemplate extends JdbcSqlTemplate implements ISqlTemplate {
    protected int jdbcMajorVersion;
    protected boolean isUsingJtds;

    public SybaseJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings,
            SymmetricLobHandler lobHandler, DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        primaryKeyViolationCodes = new int[] { 423, 511, 515, 530, 547, 2601, 2615, 2714 };
        uniqueKeyViolationNameRegex = new String[] { "unique index '(.*)'" };
        foreignKeyViolationCodes = new int[] { 546 };
        foreignKeyChildExistsViolationCodes = new int[] { 547 };
        Connection c = null;
        try {
            c = dataSource.getConnection();
            jdbcMajorVersion = c.getMetaData().getJDBCMajorVersion();
            if (dataSource.getConnection().getMetaData().getURL().contains("jtds")) {
                isUsingJtds = true;
            }
        } catch (SQLException ex) {
            jdbcMajorVersion = -1;
        } finally {
            close(c);
        }
    }

    @Override
    protected boolean allowsNullForIdentityColumn() {
        return false;
    }

    @Override
    protected int getUpdateCount(Statement stmt) throws SQLException {
        int updateCount;
        do {
            updateCount = stmt.getUpdateCount();
        } while (stmt.getMoreResults());
        return updateCount;
    }

    @Override
    public void setValues(PreparedStatement ps, Object[] args)
            throws SQLException {
        super.setValues(ps, args);
        if (args != null && args.length > 0) {
            int[] argTypes = new int[args.length];
            for (int i = 0; i < argTypes.length; i++) {
                argTypes[i] = SqlTypeValue.TYPE_UNKNOWN;
            }
            setValues(ps, args, argTypes, getLobHandler());
        }
    }

    public boolean supportsGetGeneratedKeys() {
        // needs to return true for jtds
        if (jdbcMajorVersion >= 4 || isUsingJtds) {
            return true;
        } else {
            return false;
        }
        // return jdbcMajorVersion >= 4;
    }

    protected String getSelectLastInsertIdSql(String sequenceName) {
        return "select @@identity";
    }
}
