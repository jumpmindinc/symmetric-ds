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
package org.jumpmind.db.platform.voltdb;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;

public class VoltDbJdbcSqlTemplate extends JdbcSqlTemplate {
    public VoltDbJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings, SymmetricLobHandler lobHandler,
            DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        settings.setResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE);
        settings.setOverrideIsolationLevel(Connection.TRANSACTION_SERIALIZABLE);
    }

    @Override
    public boolean isUniqueKeyViolation(Throwable ex) {
        SQLException sqlEx = findSQLException(ex);
        return (sqlEx != null && sqlEx.getMessage() != null && sqlEx.getMessage().contains("Constraint Type UNIQUE"));
    }

    public int getIsolationLevel() {
        return Connection.TRANSACTION_SERIALIZABLE; // All that VoltDB supports.
    }

    @Override
    public ISqlTransaction startSqlTransaction() {
        return new JdbcSqlTransaction(this, true); // VoltDB only supports auto-commit.
    }

    @Override
    protected void setTinyIntValue(PreparedStatement ps, int i, Object arg, int argType)
            throws SQLException {
        if (arg instanceof Integer) {
            Integer integer = (Integer) arg;
            super.setTinyIntValue(ps, i, integer.byteValue(), argType); // VoltDB wants a byte.
        } else {
            super.setTinyIntValue(ps, i, arg, argType);
        }
    }

    @Override
    protected int verifyArgType(Object arg, int argType) {
        if (argType == Types.CHAR) {
            return Types.VARCHAR;
        }
        if (arg instanceof Byte) {
            return Types.TINYINT;
        } else if (arg instanceof Short) {
            return Types.SMALLINT;
        } else if (arg instanceof Integer) {
            return Types.INTEGER;
        } else if (arg instanceof Long) {
            return Types.BIGINT;
        } else if (arg instanceof Double) {
            return Types.DOUBLE;
        } else if (arg instanceof BigDecimal) {
            return Types.DECIMAL;
        } else if (arg instanceof Timestamp) {
            return Types.TIMESTAMP;
        } else {
            return super.verifyArgType(arg, argType);
        }
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
                rs = st.executeQuery("select max(data_id)+1 from sym_data");
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
            setValues(ps, args, types, lobHandler);
            ps.executeUpdate();
        } finally {
            close(ps);
        }
        return key;
    }
}