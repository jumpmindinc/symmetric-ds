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
package org.jumpmind.db.platform.greenplum;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.postgresql.PostgreSqlJdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SqlUtils;
import org.jumpmind.db.sql.SymmetricLobHandler;

public class GreenplumJdbcSqlTemplate extends PostgreSqlJdbcSqlTemplate {
    public GreenplumJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings,
            SymmetricLobHandler lobHandler, DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
    }

    @Override
    public boolean supportsGetGeneratedKeys() {
        return false;
    }

    @Override
    protected long insertWithGeneratedKey(Connection conn, String sql, String column,
            String sequenceName, Object[] args, int[] types) throws SQLException {
        long key = 0;
        PreparedStatement ps = null;
        try {
            Statement st = null;
            ResultSet rs = null;
            try {
                st = conn.createStatement();
                rs = st.executeQuery("select nextval('" + SqlUtils.sanitizeIdentifier(sequenceName) + "_seq')");
                if (rs.next()) {
                    key = rs.getLong(1);
                }
            } finally {
                close(rs);
                close(st);
            }
            String replaceSql = sql.replaceFirst("\\(null,", "(" + key + ",");
            ps = conn.prepareStatement(replaceSql);
            ps.setQueryTimeout(settings.getQueryTimeout());
            setValues(ps, args, types, lobHandler.getDefaultHandler());
            ps.executeUpdate();
        } finally {
            close(ps);
        }
        return key;
    }
}
